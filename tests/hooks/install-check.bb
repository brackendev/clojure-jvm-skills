;; Installs the package with APM into a temporary project and a temporary HOME,
;; at project scope and at user scope, and checks the deployed failure-hints
;; and jvm-rules hooks. For failure-hints: the scripts are present and identical to the source, the launcher is
;; executable, the deployed copy gives a hint for a recorded Clojure CLI error,
;; and a launcher without Babashka reports the hook as inactive. For jvm-rules:
;; the scripts and the rules file are present and identical to the source, and
;; the deployed copy delivers the rules at session start. It also checks
;; that an install with every target succeeds at both scopes, gives Claude Code
;; the events of both hooks, writes no native Cursor hook, and gives Copilot no
;; copy of either hook.
;;
;; Usage: bb install-check.bb <package source directory>
(require '[babashka.fs :as fs]
         '[babashka.process :as process]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(load-file (str (fs/path here "support.bb")))
(alias 'support 'hook-test-support)

(def package-source
  (str (fs/absolutize
        (or (first *command-line-args*)
            (do (println "Usage: bb install-check.bb <package source directory>")
                (System/exit 1))))))

(def work (support/temp-directory))
(def home (str (fs/path work "home")))
(def project (str (fs/path work "proj")))
(fs/create-dirs home)
(fs/create-dirs project)
(support/git project "init" "-q")

(defn apm-install [dir home-dir flags]
  (let [{:keys [exit out err]} (apply process/sh {:dir dir
                                                  :env (assoc (into {} (System/getenv)) "HOME" home-dir)
                                                  :err :string}
                                      "apm" "install" package-source flags)]
    {:exit exit :log (str out err)}))

(defn same-file? [a b]
  (and (fs/regular-file? a) (= (slurp (str a)) (slurp (str b)))))

(def hook-files ["failure-hints.sh" "failure-hints.bb"])

(def rules-files ["jvm-rules.sh" "jvm-rules.bb" "jvm-rules.md"])

;; A PATH with what the launcher needs before it looks for bb.
(def path-without-bb
  (let [dir (support/temp-directory)]
    (doseq [tool ["cat" "dirname"]] (fs/create-sym-link (fs/path dir tool) (fs/which tool)))
    dir))

(doseq [[label flags] [["project" []] ["user" ["-g"]]]
        :let [{:keys [exit log]} (apm-install project home (concat flags ["--target" "claude"]))]]
  (support/check (zero? exit) (str label ": install failed (exit " exit ")"))
  (support/check (str/includes? log "hook(s) integrated") (str label ": install did not report integrated hooks"))
  (support/check (not (re-find #"(?i)hook script not found" log)) (str label ": install reported a missing hook script")))


(doseq [scope [(fs/path project ".claude") (fs/path home ".claude")]
        :let [label (str (fs/relativize work scope))
              deployed (some-> (first (fs/glob scope "**/failure-hints.sh" {:hidden true})) fs/parent)]]
  (support/check deployed (str label ": failure-hints.sh was not deployed"))
  (when deployed
    (doseq [file hook-files]
      (support/check (and (same-file? (fs/path deployed file) (fs/path package-source ".apm/hooks/scripts" file))
                          (or (not= file "failure-hints.sh") (fs/executable? (fs/path deployed file))))
                     (str label ": " file " is missing, differs from the source, or is not executable")))
    (let [{:keys [exit out]} (support/run-hook (fs/path deployed "failure-hints.sh")
                                               (support/claude-input (str "install-" (random-uuid)) "PostToolUseFailure"
                                                                     "clj -M -m app.unres"
                                                                     "Syntax error compiling at (app/unres.clj:2:12).\nUnable to resolve symbol: frob in this context\n"))]
      (support/check (and (zero? exit)
                          (some-> (not-empty out) json/parse-string
                                  (get-in ["hookSpecificOutput" "additionalContext"])
                                  (str/includes? "frob is not defined")))
                     (str label ": the deployed hook did not give the unresolved-symbol hint")))
    (let [{:keys [exit err]} (process/sh {:in "{\"error\":\"Syntax error compiling\"}" :err :string
                                          :env (assoc (into {} (System/getenv)) "PATH" (str path-without-bb))}
                                         "/bin/bash" (str (fs/path deployed "failure-hints.sh")))]
      (support/check (and (zero? exit) (str/includes? err "is inactive: Babashka (bb) is not installed"))
                     (str label ": the launcher without bb did not report the hook as inactive (exit " exit ")")))))

(support/write-file project "deps.edn" "{:paths [\"src\"]}\n")

(doseq [scope [(fs/path project ".claude") (fs/path home ".claude")]
        :let [label (str (fs/relativize work scope))
              deployed (some-> (first (fs/glob scope "**/jvm-rules.sh" {:hidden true})) fs/parent)]]
  (support/check deployed (str label ": jvm-rules.sh was not deployed"))
  (when deployed
    (doseq [file rules-files]
      (support/check (and (same-file? (fs/path deployed file) (fs/path package-source ".apm/hooks/scripts" file))
                          (or (not= file "jvm-rules.sh") (fs/executable? (fs/path deployed file))))
                     (str label ": " file " is missing, differs from the source, or is not executable")))
    (let [{:keys [exit out]} (support/run-hook (fs/path deployed "jvm-rules.sh")
                                               (json/generate-string {"session_id" (str "install-" (random-uuid))
                                                                      "hook_event_name" "SessionStart"
                                                                      "source" "startup"
                                                                      "cwd" project}))]
      (support/check (and (zero? exit)
                          (some-> (not-empty out) json/parse-string
                                  (get-in ["hookSpecificOutput" "additionalContext"])
                                  (str/includes? "JVM-1:")))
                     (str label ": the deployed jvm-rules hook did not deliver the rules at session start")))))

;; The test suites are repository files, not package content.
(support/check (empty? (for [dir [(fs/path project ".claude") (fs/path home ".claude")]
                             :when (fs/exists? dir)
                             match (fs/glob dir "**tests/hooks**" {:hidden true})]
                         match))
               "the install deployed files from tests/hooks")

(defn hook-scripts
  "The script file names that a hook configuration file runs, by event. Quotes
  around project-scope commands are removed first."
  [config-file]
  (into {}
        (for [[event entries] (get (json/parse-string (slurp (str config-file))) "hooks")]
          [event (set (for [node (tree-seq coll? seq entries)
                            :when (map? node)
                            :let [command (or (get node "command") (get node "bash"))]
                            :when (string? command)]
                        (first (str/split (str (fs/file-name (str/replace command "\"" ""))) #"\s+"))))])))

;; With every target, Cursor gets no native hook, and Copilot gets no copy of
;; the hook, which has been verified only on Claude Code.
(def all-home (str (fs/path work "home-all")))
(def all-project (str (fs/path work "proj-all")))
(fs/create-dirs all-home)
(fs/create-dirs all-project)
(support/git all-project "init" "-q")

(doseq [[label flags root] [["project, all targets" ["--target" "all"] all-project]
                            ["user, all targets" ["-g" "--target" "all"] all-home]]
        :let [{:keys [exit log]} (apm-install all-project all-home flags)
              claude-config (fs/path root ".claude" "settings.json")
              cursor-config (fs/path root ".cursor" "hooks.json")
              copilot-dir (if (= root all-project) (fs/path root ".github" "hooks") (fs/path root ".copilot" "hooks"))
              before (:fail @support/results)]]
  (support/check (zero? exit) (str label ": install failed (exit " exit ")"))
  (support/check (and (fs/exists? claude-config)
                      (every? #(contains? (get (hook-scripts claude-config) %) "failure-hints.sh")
                              ["PostToolUse" "PostToolUseFailure"]))
                 (str label ": Claude Code settings do not register failure-hints for both events"))
  (support/check (and (fs/exists? claude-config)
                      (every? #(contains? (get (hook-scripts claude-config) %) "jvm-rules.sh")
                              ["SessionStart" "PostToolUse"]))
                 (str label ": Claude Code settings do not register jvm-rules for SessionStart and PostToolUse"))
  (support/check (or (not (fs/exists? cursor-config)) (every? empty? (vals (hook-scripts cursor-config))))
                 (str label ": .cursor/hooks.json contains a native hook"))
  (support/check (empty? (for [f (when (fs/exists? copilot-dir) (fs/glob copilot-dir "*.json"))
                               :when (re-find #"failure-hints\.sh|jvm-rules\.sh" (slurp (str f)))]
                           (str (fs/file-name f))))
                 (str label ": Copilot received a hook file that runs failure-hints or jvm-rules"))
  (when (> (:fail @support/results) before)
    (println (str/replace log #"(?m)^" "    "))))

(fs/delete-tree work)
(fs/delete-tree path-without-bb)
(support/report "install check")
