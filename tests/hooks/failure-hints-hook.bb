;; Runs failure-hints.sh end to end with Clojure CLI error output recorded from
;; Clojure 1.12 and checks which hint the agent receives. Covers both Claude
;; Code events, piped commands, wrappers, commands that only quote an error,
;; repeated failures, other runtimes' input, and a missing Babashka.
;;
;; Usage: bb failure-hints-hook.bb <hook scripts directory>
(require '[babashka.fs :as fs]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(load-file (str (fs/path here "support.bb")))
(alias 'support 'hook-test-support)

(def scripts (or (first *command-line-args*)
                 (do (println "Usage: bb failure-hints-hook.bb <hook scripts directory>")
                     (System/exit 1))))
(def hook (str (fs/path scripts "failure-hints.sh")))

(defn session [] (str "test-" (random-uuid)))

;; Output recorded from the Clojure 1.12 CLI.
(def macroexpand-error
  (str "Syntax error macroexpanding clojure.core/let at (app/bad.clj:3:3).\n"
       "[y] - failed: even-number-of-forms? at: [:bindings] spec: :clojure.core.specs.alpha/bindings\n\n"
       "Full report at:\n/var/folders/xx/T/clojure-4925883397276477484.edn\n"))
(def reader-error "Syntax error reading source at (app/reader.clj:4:1).\nEOF while reading, starting at line 2\n")
(def unresolved-error "Syntax error compiling at (app/unres.clj:2:12).\nUnable to resolve symbol: frob in this context\n")
(def classpath-error
  (str "Execution error (FileNotFoundException) at user/eval1 (REPL:1).\n"
       "Could not locate app/nope__init.class, app/nope.clj or app/nope.cljc on classpath.\n"))
(def test-failure "FAIL in (adds-test) (core_test.clj:7)\nexpected: (= 4 (add 2 3))\n  actual: (not (= 4 5))\n")

;; Output recorded from `lein run -m app.nope` with Leiningen 2.13.0.
(def lein-classpath-error
  (str "Can't find 'app.nope' as .class or .clj for lein run: please check the spelling.\n"
       "Execution error (FileNotFoundException) at user/eval138$fn (form-init8640735091224983763.clj:1).\n"
       "Could not locate app/nope__init.class, app/nope.clj or app/nope.cljc on classpath.\n"))

(defn run
  "Runs the hook. Returns {:exit :event :context :raw} from its output."
  [input & opts]
  (let [{:keys [exit out err]} (apply support/run-hook hook input opts)
        out (str/trim (or out ""))
        parsed (when-not (str/blank? out) (try (json/parse-string out true) (catch Exception _ ::invalid)))]
    {:exit exit
     :err err
     :raw out
     :event (get-in parsed [:hookSpecificOutput :hookEventName])
     :context (get-in parsed [:hookSpecificOutput :additionalContext])}))

(defn context [event command output]
  (:context (run (support/claude-input (session) event command output))))

;; A failed command (PostToolUseFailure) gets the hint, the report path, and the skill pointer.
(let [{:keys [exit event context]} (run (support/claude-input (session) "PostToolUseFailure"
                                                              "clj -M -e \"(require 'app.bad)\"" macroexpand-error))]
  (support/result 0 exit "macroexpansion failure: exit status")
  (support/result "PostToolUseFailure" event "macroexpansion failure: event name in output")
  (support/check (and context (str/includes? context "clojure.core/let at app/bad.clj:3:3 has the wrong shape")
                      (str/includes? context "clojure-4925883397276477484.edn")
                      (str/includes? context "clojure-debug skill"))
                 (str "macroexpansion failure: hint, report path, and skill pointer: " context)))

;; A piped command exits 0, so its output arrives through PostToolUse.
(let [{:keys [event context]} (run (support/claude-input (session) "PostToolUse"
                                                         "clj -M -e \"(require 'app.bad)\" 2>&1 | tee /tmp/err.log"
                                                         macroexpand-error))]
  (support/result "PostToolUse" event "piped command: event name in output")
  (support/check (some-> context (str/includes? "wrong shape")) (str "piped command: hint: " context)))

;; Each error shape gets its own hint.
(support/check (some-> (context "PostToolUseFailure" "clojure -M -m app.reader" reader-error)
                       (str/includes? "could not parse app/reader.clj near line 4"))
               "reader error: hint names the file and line")
(support/check (some-> (context "PostToolUseFailure" "clj -M -m app.unres" unresolved-error)
                       (str/includes? "frob is not defined or referred"))
               "unresolved symbol: hint names the symbol")
(support/check (some-> (context "PostToolUseFailure" "clj -M -e \"(require 'app.nope)\"" classpath-error)
                       (str/includes? "app/nope.clj or app/nope.cljc is not on this command's classpath"))
               "classpath error: hint names the file")
(support/check (some-> (context "PostToolUseFailure" "lein run -m app.nope" lein-classpath-error)
                       (str/includes? ":source-paths and the active profiles in project.clj"))
               "classpath error from Leiningen: hint names the project.clj source paths")

;; Wrappers and command lists still count as running the Clojure CLI.
(doseq [command ["timeout 60 clj -M:test" "cd app && clojure -M -m app.main" "lein test"
                 "JAVA_OPTS=-Xmx1g clj -M:run" "/usr/local/bin/clj -M:test" "\"/opt/homebrew/bin/clj\" -M:test"]]
  (support/check (some? (context "PostToolUseFailure" command unresolved-error))
                 (str "command runs the Clojure CLI: " command)))

;; A command that only shows or searches error text gets no hint.
(doseq [command ["cat /tmp/err.log" "grep -n \"Syntax error\" /tmp/err.log" "echo \"clj failed\""
                 "bb -e '(println 1)'"]]
  (support/result nil (context "PostToolUse" command unresolved-error)
                  (str "command does not run the Clojure CLI: " command)))

;; A macro that throws while expanding, without a spec failure, is not called a wrong shape.
(let [c (context "PostToolUseFailure" "clj -M -m app.db"
                 "Syntax error macroexpanding app.db/defquery at (app/db.clj:5:1).\nConnection refused\n")]
  (support/check (and c (str/includes? c "threw while expanding") (not (str/includes? c "wrong shape")))
                 (str "macro that throws: hint says the macro threw: " c)))

;; A different cause at the same location is a new failure, not a repeat.
(let [s (session)
      command "clj -M -m app.unres"
      _ (run (support/claude-input s "PostToolUseFailure" command unresolved-error))
      c (:context (run (support/claude-input s "PostToolUseFailure" command
                                            (str/replace unresolved-error "frob" "blarg"))))]
  (support/check (some-> c (str/includes? "blarg is not defined"))
                 (str "different cause: new hint, not the repeat note: " c)))

;; Output with no known error shape gets no hint.
(support/result nil (context "PostToolUseFailure" "clj -X:test" test-failure) "test failure output: no hint")

;; The same failure twice in a session gets the repeated-failure note.
(let [s (session)
      command "clj -M -m app.unres"
      first-run (:context (run (support/claude-input s "PostToolUseFailure" command unresolved-error)))
      second-run (:context (run (support/claude-input s "PostToolUseFailure" command unresolved-error)))]
  (support/check (some-> first-run (str/includes? "frob")) "repeated failure: first run gets the hint")
  (support/check (some-> second-run (str/includes? "failed with the same error earlier"))
                 (str "repeated failure: second run gets the repeated-failure note: " second-run)))

;; Other runtimes' input gets no hint.
(support/result "" (:raw (run (json/generate-string {"session_id" (session) "turn_id" "t" "hook_event_name" "PostToolUse"
                                                     "tool_name" "Bash" "tool_input" {"command" "clj -M:test"}
                                                     "tool_response" {"stdout" unresolved-error}})))
                "codex input: no output")
(support/result "" (:raw (run (json/generate-string {"sessionId" (session) "toolName" "bash"
                                                     "toolArgs" {"command" "clj -M:test"}
                                                     "toolResult" {"textResultForLlm" unresolved-error}})))
                "copilot input: no output")

;; Without Babashka the launcher exits 0 and reports the hook as inactive.
(let [bin (support/temp-directory)
      _ (doseq [tool ["cat" "dirname"]] (fs/create-sym-link (fs/path bin tool) (fs/which tool)))
      {:keys [exit raw err]} (run (support/claude-input (session) "PostToolUseFailure" "clj -M:test" unresolved-error)
                                  :path (str bin))]
  (support/result 0 exit "missing bb: exit status")
  (support/result "" raw "missing bb: no standard output")
  (support/check (str/includes? (or err "") "Babashka (bb) is not installed") "missing bb: inactive notice"))

(support/report "failure-hints hook")
