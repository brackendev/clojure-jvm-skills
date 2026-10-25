;; failure-hints.bb - After a Clojure CLI or Leiningen command prints a Clojure
;; error, adds a short hint that names the next check for that error. Started
;; by failure-hints.sh, which reports the hook as inactive when Babashka is
;; missing.
;;
;; Shipped as an APM hook primitive. APM registers it for PostToolUse and
;; PostToolUseFailure with the matcher "*". Claude Code sends a command that
;; exits with a nonzero status to PostToolUseFailure, with its output in
;; `error`. A command whose pipeline exits 0, such as `clojure ... 2>&1 | tee
;; log`, goes to PostToolUse with its output in tool_response.stdout and
;; tool_response.stderr, so the script reads both events.
;;
;; The hook acts only on Claude Code input. It has not been verified on other
;; runtimes, so it stays silent for Codex input (turn_id) and for Copilot
;; input (toolName or tool_result).
;;
;; A hint fires only when the command runs clj, clojure, or lein and the output
;; matches a known error shape. Each hint is given once per session for the
;; same command and error. When the same command fails with the same error
;; again, the hook says so instead of repeating the hint.
(ns failure-hints
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def clojure-commands #{"clj" "clojure" "lein"})

;; Words that run the command after them, such as `time clojure ...`.
(def wrappers #{"time" "env" "nice" "command" "exec" "timeout" "nohup"})

;; Input

(defn claude-input?
  "Claude Code input. Codex adds turn_id, and Copilot sends toolName or tool_result."
  [input]
  (not (some #(contains? input %) [:turn_id :toolName :tool_result])))

(defn command-words
  "The first words of each command in a shell line, after variable assignments
  and wrapper words. Splits on &&, ||, ;, |, and newlines. Text inside quotes is
  not inspected, so a quoted mention of clj is not a command."
  [line]
  (let [unquoted (-> line
                     ;; A quoted word without spaces, such as "/opt/homebrew/bin/clj", keeps its text.
                     (str/replace #"\"([^\"\s]+)\"|'([^'\s]+)'" (fn [[_ a b]] (or a b)))
                     ;; Other quoted text is data, so a quoted mention of clj is not a command.
                     (str/replace #"\"(?:[^\"\\]|\\.)*\"|'[^']*'" "\"\""))]
    (for [segment (str/split unquoted #"&&|\|\||[;|\n]")
          :let [words (remove #(re-matches #"[A-Za-z_][A-Za-z0-9_]*=.*" %)
                              (str/split (str/trim segment) #"\s+"))
                words (drop-while #(or (wrappers %) (re-matches #"-.*|\d+[smh]?" %)) words)]
          :when (seq words)]
      (fs/file-name (first words)))))

(defn runs-clojure? [command]
  (boolean (some clojure-commands (command-words (or command "")))))

(defn command-output [input]
  (if (= "PostToolUseFailure" (:hook_event_name input))
    (str (:error input))
    (str (get-in input [:tool_response :stdout]) "\n" (get-in input [:tool_response :stderr]))))

;; Hints

(defn reader-hint [output]
  (when-let [[_ file line] (re-find #"Syntax error reading source at \((.+?):(\d+):\d+\)" output)]
    (str "The reader could not parse " file " near line " line ". "
         "\"EOF while reading, starting at line N\" means a form that opens on line N never closes. "
         "Run `clj-kondo --lint` on the source file to find the unbalanced delimiter before editing.")))

(defn macroexpand-hint
  "A spec failure means the call has the wrong shape. Without one, the macro
  itself threw while expanding."
  [output]
  (when-let [[_ macro location] (re-find #"Syntax error macroexpanding (\S+) at \((.+?)\)" output)]
    (if (re-find #"did not conform to spec| - failed: " output)
      (str "A call to " macro " at " location " has the wrong shape. "
           "The spec message on the next line names the part that failed, such as an odd number of let bindings.")
      (str macro " threw while expanding at " location ". "
           "The next line is the macro's own error, so check the macro's code and the values it uses at expansion time."))))

(defn unresolved-hint [output]
  (when-let [[_ symbol] (re-find #"Unable to resolve symbol: (\S+) in this context" output)]
    (str symbol " is not defined or referred in that namespace. "
         "Check the ns form's :require and :refer, the spelling, and whether it is defined later in the file.")))

(defn classpath-hint [output]
  (when-let [[_ path] (re-find #"Could not locate (\S+?)__init\.class, \S+ or \S+ on classpath" output)]
    (str "The namespace file " path ".clj or " path ".cljc is not on this command's classpath. "
         "Check that the ns name matches the file path (hyphens in the namespace become underscores in the file name), "
         "that the command's classpath includes the directory that holds it (:paths and the command's aliases in deps.edn, or :source-paths and the active profiles in project.clj), "
         "and the dependency coordinate if it comes from a library.")))

(defn report-hint [output]
  (when-let [[_ path] (re-find #"Full report at:\s*\n\s*(\S+\.edn)" output)]
    (str "The full report at " path " holds :clojure.main/triage (phase, file, line) and the :via chain with each exception's :data.")))

(def hint-fns [reader-hint macroexpand-hint unresolved-hint classpath-hint])

(defn error-identity
  "The summary line and the line after it, which holds the cause, so a different
  cause at the same location is a different failure."
  [output]
  (let [lines (vec (remove str/blank? (map str/trim (str/split-lines output))))
        i (first (keep-indexed (fn [i l] (when (re-find #"^(Syntax error|Execution error|Could not locate|Unable to resolve)" l) i))
                               lines))]
    (if i (str/join "\n" (subvec lines i (min (count lines) (+ i 2)))) "")))

;; Session state

(defn sha256 [^String s]
  (let [digest (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" %) digest))))

(defn seen-before!
  "True when this command and error were already seen in the session. Records them otherwise."
  [input command error-line]
  (let [dir (fs/path (fs/temp-dir) "clojure-jvm-skills-failure-hints"
                     (str/replace (str (or (:session_id input) "no-session")) #"[^A-Za-z0-9_-]" "_"))
        _ (fs/create-dirs dir)
        marker (fs/file dir (sha256 (str (str/trim command) "\n" error-line)))]
    (not (.createNewFile marker))))

(defn hint-text [input]
  (let [command (str (get-in input [:tool_input :command]))
        output (command-output input)
        hints (seq (keep #(% output) hint-fns))]
    (when hints
      (if (seen-before! input command (error-identity output))
        (str "This command failed with the same error earlier in the session. "
             "Change the diagnostic approach before running it again, and keep the original error output.")
        (str/join "\n"
                  (cond-> (vec (take 2 hints))
                    (report-hint output) (conj (report-hint output))
                    true (conj "The clojure-debug skill from clojure-skills covers the procedure for finding the cause.")))))))

(defn -main []
  (let [input (json/parse-string (slurp *in*) true)
        event (:hook_event_name input)]
    (when (and (claude-input? input)
               (#{"PostToolUse" "PostToolUseFailure"} event)
               (= "Bash" (:tool_name input))
               (runs-clojure? (get-in input [:tool_input :command])))
      (when-let [text (hint-text input)]
        (println (json/generate-string {:hookSpecificOutput {:hookEventName event :additionalContext text}}))))))

(try
  (-main)
  (catch Exception e
    ;; A hook failure must not interrupt the agent. Report it on standard error.
    (binding [*out* *err*]
      (println "failure-hints failed:" (ex-message e)))))
