# JVM Clojure Project Workflows

Reference for the Clojure CLI, project structure, `deps.edn` configuration, lint and format tooling, the test-runner workflow, and the JVM REPL workflow (starting nREPL, `add-lib`, and running tests from the REPL).

## CLI

```bash
clojure -M:alias                 # Run with an alias
clojure -X:alias fn-name         # Execute a function
clojure -T:build task            # Run a tools.build task
clj                              # Start an interactive REPL
clj -Sdeps '{:deps {...}}'       # Start an interactive REPL with inline dependencies
```

### `clojure` and `clj`

Run every command the agent issues with `clojure`, including tests, builds, formatters, and background servers. `clj` runs `clojure` through `rlwrap`, which adds line editing for a person typing at an interactive REPL, and the two accept the same arguments. When `rlwrap` is missing, `clj` prints "Please install rlwrap for command editing or use "clojure" instead." and exits with status 1. Keep `clj` for instructions in which a person starts an interactive REPL at a terminal.

## Project Structure

```
my-project/
  deps.edn              # Dependencies and aliases
  build.clj             # tools.build tasks (optional)
  .cljfmt.edn           # cljfmt formatting rules (optional)
  src/
    my_app/
      core.clj           # Entry point or main namespace
      db.clj             # Feature modules
  test/
    my_app/
      core_test.clj      # Tests mirror src structure
  resources/             # Non-code resources
```

## deps.edn Configuration

```clojure
{:paths ["src" "resources"]
 :deps  {org.clojure/clojure {:mvn/version "1.12.6"}}
 :aliases
 {:dev     {:extra-paths ["dev"]}
  :test    {:extra-paths ["test"]
            :extra-deps  {io.github.cognitect-labs/test-runner
                          {:git/tag "v0.5.1" :git/sha "dfb30dd"}}
            :main-opts   ["-m" "cognitect.test-runner"]
            :exec-fn     cognitect.test-runner.api/test}
  :build   {:deps        {io.github.clojure/tools.build
                          {:git/tag "v0.10.14" :git/sha "1176afd"}}
            :ns-default  build}
  :cljfmt  {:replace-deps {dev.weavejester/cljfmt {:mvn/version "0.16.6"}}
            :main-opts    ["-m" "cljfmt.main"]}
  :dry4clj {:extra-deps  {io.github.unclebob/dry4clj
                          {:git/url "https://github.com/unclebob/dry4clj.git"
                           :git/sha "5994ef2bcaaae8d12bcd0afb4f091ae09083228c"}}
            :main-opts   ["-m" "dry4clj.core"]}}}
```

These aliases match the steps `/clj-fix` in clojure-skills runs. `dry4clj` is not published to Clojars, so its alias uses a Git coordinate pinned to a commit, and `/clj-fix` uses it for its duplicate-form step.

## Tooling

| Tool | Command | Purpose |
|------|---------|---------|
| Clojure CLI | `clojure` (`clj` when a person starts an interactive REPL) | Run, execute, REPL |
| tools.build | `clojure -T:build task` | Build tasks (uberjar, etc.) |
| clj-kondo | `clj-kondo --lint src` | Lint `.clj` files |
| cljfmt | `clojure -M:cljfmt fix` | Format `.clj` files |
| test-runner | `clojure -X:test` | Run tests |

Run the test command the project defines. Kaocha's documented setup (a `tests.edn` file and a `bin/kaocha` script) defines `:test` with `:main-opts ["-m" "kaocha.runner"]` and no `:exec-fn`, so run `bin/kaocha` or `clojure -M:test` in such a project. `clojure -X:test` fails there with "No function found on command line or in :exec-fn".

## REPL

The [clojure](https://github.com/brackendev/clojure-skills) baseline skill holds the host-neutral REPL conventions (`(comment ...)` scratchpads, `in-ns`, fully qualified references across namespaces) and the REPL-driven development principle. The `clojure-repl` skill in clojure-skills covers finding and verifying a running REPL, choosing a client such as clojure-mcp, evaluating with bounded output, and reloading a changed namespace with `:reload`. The JVM-specific additions are below.

### Starting nREPL

Connect to a running nREPL when available. If none is running and the project defines an `:nrepl` alias, start one in the background with `clojure -M:nrepl &` from the project root. A project without one can add an alias built from the nREPL documentation's `deps.edn` example:

```clojure
:nrepl {:extra-deps {nrepl/nrepl {:mvn/version "1.8.0"}}
        :main-opts  ["-m" "nrepl.cmdline"]}
```

In a Leiningen project, `lein repl :headless` starts an nREPL server. Both print the port and write it to `.nrepl-port` in the directory where they start. The `clojure-repl` skill covers how clients such as clojure-mcp find that port.

### Adding a library without a restart

Clojure can add a library to a running REPL without restarting the JVM. In a `clojure.main` REPL these functions are referred into `user` automatically. In other REPLs, `(require '[clojure.repl.deps :refer [add-lib add-libs sync-deps]])` first. Call them from a REPL, not from a script or `-e` expression: they check that `*repl*` is bound to true and need the REPL's dynamic class loader. They call the Clojure CLI to resolve dependencies:

```clojure
(add-lib 'org.clojure/data.json)                          ; newest release
(add-lib 'org.clojure/data.json {:mvn/version "2.5.2"})   ; specific version
(sync-deps)                                               ; load deps.edn libs not yet on the classpath
```

Use these for exploration only. Record every dependency the project keeps in `deps.edn`.

### Running tests from the REPL

After editing source, reload the changed namespace together with its test namespace, then run the tests. Reloading only the test namespace keeps the old definitions of the code under test, so the tests still run against the code from before the edit:

```clojure
(require 'my-app.core 'my-app.core-test :reload)
(clojure.test/run-tests 'my-app.core-test)
```

`:reload-all` reloads the test namespace and every namespace it requires, including libraries such as `clojure.test` and `clojure.string`, so name the changed namespaces with `:reload` instead.

Run the full suite with the project's own test command (see Tooling) in a new process, which also catches definitions that exist only in the REPL.

### Reflection warnings

The Reflection warnings entry under Gotchas in `SKILL.md` covers `*warn-on-reflection*` and the `^Type` hints that clear its warnings.
