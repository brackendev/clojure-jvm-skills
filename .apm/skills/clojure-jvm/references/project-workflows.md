# JVM Clojure Project Workflows

Reference for the Clojure CLI, project structure, `deps.edn` configuration, lint and format tooling, the test-runner workflow, and the JVM REPL workflow (nREPL, `clojure-mcp`).

## CLI

```bash
clj                          # Start a REPL
clj -M:alias                 # Run with alias
clj -X:alias fn-name         # Execute a function
clj -T:build task            # Run a tools.build task
clj -Sdeps '{:deps {...}}'   # Add inline dependencies
```

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
 {:dev     {:extra-paths ["dev"]
            :extra-deps  {}}
  :test    {:extra-paths ["test"]
            :extra-deps  {io.github.cognitect-labs/test-runner
                          {:git/tag "v0.5.1" :git/sha "dfb30dd"}}
            :main-opts   ["-m" "cognitect.test-runner"]
            :exec-fn     cognitect.test-runner.api/test}
  :build   {:deps        {io.github.clojure/tools.build
                          {:git/tag "v0.10.14" :git/sha "1176afd"}}
            :ns-default  build}
  :cljfmt  {:extra-deps  {dev.weavejester/cljfmt {:mvn/version "0.16.6"}}
            :main-opts   ["-m" "cljfmt.main"]}}}
```

## Tooling

| Tool | Command | Purpose |
|------|---------|---------|
| Clojure CLI | `clj` | REPL, run, execute |
| tools.build | `clj -T:build task` | Build tasks (uberjar, etc.) |
| clj-kondo | `clj-kondo --lint src` | Lint `.clj` files |
| cljfmt | `clj -M:cljfmt fix` | Format `.clj` files |
| test-runner | `clj -X:test` | Run tests |

## REPL

The host-neutral REPL conventions (`(comment ...)` scratchpads, `in-ns`, fully-qualified references across namespaces) and the REPL-driven development principle live in the [clojure](https://github.com/brackendev/clojure-skills) baseline skill. The JVM-specific additions are below.

Connect to a running nREPL when available. If none is running and the project defines an `:nrepl` alias, start one in the background with `clj -M:nrepl &`. An nREPL started through `nrepl.cmdline` prints its port and writes it to `.nrepl-port` in the project directory. Connect the editor or MCP server to that port. An agent cannot restart its own session, so when an MCP server must be restarted to pick up the new port, ask the user to restart it.

When [clojure-mcp](https://github.com/bhauman/clojure-mcp) is available, prefer its tools (REPL evaluation, namespace reload, file operations) over shell commands; it connects over the running nREPL.

Clojure 1.12 can add a library to a running REPL without restarting the JVM. In a `clojure.main` REPL these functions are referred into `user` automatically. In other REPLs, `(require '[clojure.repl.deps :refer [add-lib add-libs sync-deps]])` first. Call them from a REPL, not from a script or `-e` expression: they check that `*repl*` is bound to true and need the REPL's dynamic class loader. They call the Clojure CLI to resolve dependencies:

```clojure
(add-lib 'org.clojure/data.json)                          ; newest release
(add-lib 'org.clojure/data.json {:mvn/version "2.5.1"})   ; specific version
(sync-deps)                                               ; load deps.edn libs not yet on the classpath
```

Use these for exploration only. Record every dependency the project keeps in `deps.edn`.

Reload namespaces with the `:reload` flag to work with the latest code:

```clojure
(require '[my-app.core] :reload)
```

Reload namespaces before running tests:

```clojure
(require '[my-app.core-test] :reload)
(clojure.test/run-tests 'my-app.core-test)
```

For full test suite runs, prefer the CLI:

```bash
clj -X:test
```

### Reflection warnings

Set `*warn-on-reflection*` at the top of a namespace to surface untyped Java method calls during development:

```clojure
(set! *warn-on-reflection* true)
```

Combine with `^Type` hints on parameters and return values to eliminate the warnings.
