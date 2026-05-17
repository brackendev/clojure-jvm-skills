---
name: clojure-jvm
description: >-
  Use when writing, editing, reviewing, or discussing JVM Clojure code that
  touches the JVM host. Triggers: .clj files, deps.edn projects, project.clj,
  build.clj, clojure.test, REPL usage, Java interop (`java.*`, `import`,
  `Throwable`, `Exception`, `AutoCloseable`), `with-open`, refs / agents / STM
  (`dosync`, `alter`, `ref`, `agent`, `send`, `send-off`, `io!`),
  `alter-var-root`, `gen-class`, `proxy`, `clojure.tools.logging`,
  `clojure.java.io`, `clojure.math`, `clojure.core.async` on the JVM, the
  Clojure CLI (`clj`, `clojure`), `tools.build`, `clj-kondo`, `cljfmt`,
  Cognitect `test-runner`, nREPL, or `clojure-mcp`. Covers Java interop,
  JVM-typed exception handling, JVM resource cleanup, the reference primitives
  (refs, agents, STM), `alter-var-root` workflow, and the JVM project layout
  and tooling.
user-invocable: false
---

# JVM Clojure

Layered on top of the [clojure](https://github.com/brackendev/clojure-skills) skill, which defines host-neutral Clojure family guidance. This skill applies only to JVM Clojure (`.clj` files targeting the JVM, `deps.edn` projects, `project.clj`, `build.clj`). It overrides the baseline for Java interop, JVM-typed exceptions, JVM resource cleanup, the reference primitives (`ref`, `agent`, STM), `alter-var-root`, and the Clojure CLI / `tools.build` / `clj-kondo` / `cljfmt` / test-runner / nREPL workflow.

ClojureScript and ClojureDart deltas live in their own packages ([clojurescript-skills](https://github.com/brackendev/clojurescript-skills) when published, [clojuredart-skills](https://github.com/brackendev/clojuredart-skills)). Do not apply this skill's interop, exception, or resource rules to those dialects.

## Key Rules

1. **Use `with-open` for resource cleanup.** Not `try`/`finally`. Anything implementing `java.lang.AutoCloseable` or `java.io.Closeable` qualifies.
2. **Never catch `Throwable`.** Catch specific exception types so `OutOfMemoryError`, `StackOverflowError`, and assertion failures keep propagating.
3. **Use the auto-generated record factories, not the JVM interop form.** The baseline rule "use `->Foo` constructors" applies; this skill adds the JVM-specific reason: the `(Foo. ...)` interop form bypasses any preprocessing the factory does.
4. **Prefer `swap!` and atom-only patterns when possible; reach for refs and agents only when their coordination semantics are required.** STM is for coordinated multi-identity updates; agents are for asynchronous single-identity updates that can be batched.
5. **Reuse standard Java exception types when `ex-info` is not the right fit.** `IllegalArgumentException`, `IllegalStateException`, and `UnsupportedOperationException` carry meaning across the JVM ecosystem.

## Java Interop

Use sugared syntax:

```clojure
;; good
(java.util.ArrayList. 100)
(Math/pow 2 10)
(.substring "hello" 1 3)
Integer/MAX_VALUE

;; bad
(new java.util.ArrayList 100)
(. Math pow 2 10)
(. "hello" substring 1 3)
(. Integer MAX_VALUE)
```

The `(:import ...)` clause in `ns` is the JVM-only counterpart to `(:require ...)`. Group it after `(:require ...)`:

```clojure
(ns my-app.core
  (:require
   [clojure.string :as str]
   [my-app.db :as db])
  (:import
   [java.time LocalDate Instant]
   [java.util UUID]))
```

## Exception Handling

`ex-info` for data-carrying exceptions remains the baseline. On the JVM, prefer standard Java exception types when the failure mode already has a named exception in the platform:

```clojure
;; good: domain failure with structured data
(throw (ex-info "Invalid input" {:value x :reason :negative}))

;; good: parameter contract failure that callers may already match against
(throw (IllegalArgumentException. "x must be positive"))
```

Never catch `Throwable`:

```clojure
;; good
(try (foo)
  (catch clojure.lang.ExceptionInfo ex ...)
  (catch IllegalArgumentException ex ...)
  (catch AssertionError t ...))

;; bad: swallows OutOfMemoryError, StackOverflowError, and assertion failures
(try (foo)
  (catch Throwable t ...))
```

## Resource Cleanup

Use `with-open` rather than `try`/`finally`. It calls `.close` on each bound value when the body exits, in reverse declaration order:

```clojure
;; good
(with-open [rdr (clojure.java.io/reader "file.txt")]
  (slurp rdr))

;; bad: verbose and easy to get wrong
(let [rdr (clojure.java.io/reader "file.txt")]
  (try
    (slurp rdr)
    (finally
      (.close rdr))))
```

`with-open` requires the bound value to implement `AutoCloseable` or `Closeable`. For resources that do not, fall back to `try`/`finally` and write the cleanup explicitly.

## Vars and Root Bindings

Use `alter-var-root` to rebind a var's root value rather than redefining it:

```clojure
;; good
(def thing 1)
(alter-var-root #'thing (constantly nil))

;; bad: leaves stale metadata, breaks anything holding the original var
(def thing 1)
(def thing nil)
```

`with-redefs` temporarily rebinds vars in the current thread (and is visible across threads despite that). Use it sparingly and only for external boundaries (HTTP, database, clock). Prefer passing dependencies as function arguments. Multi-threaded test runners can leave permanent damage if two redefs race.

## State Management

### Refs and STM

`dosync` coordinates multiple ref updates atomically. Use `alter` over `ref-set`:

```clojure
(def r (ref 0))

;; good
(dosync (alter r + 5))

;; bad
(dosync (ref-set r 5))
```

Never put atom swaps inside an STM transaction body. STM may retry the transaction; the atom swap will run on each retry:

```clojure
;; good: atom update after transaction
(dosync
  (alter account-a - 100)
  (alter account-b + 100))
(swap! transfer-log conj {:from :a :to :b :amount 100})

;; bad: swap! retries on every STM retry
(dosync
  (alter account-a - 100)
  (alter account-b + 100)
  (swap! transfer-log conj {:from :a :to :b :amount 100}))
```

### Agents

Use `send` for CPU-bound actions (fixed thread pool). Use `send-off` for actions that block on I/O (unbounded thread pool):

```clojure
;; good: pure computation
(send agent-a + 42)

;; good: blocking I/O
(send-off agent-b (fn [state] (assoc state :data (slurp url))))

;; bad: blocking I/O via send starves the fixed pool
(send agent-b (fn [state] (assoc state :data (slurp url))))
```

### `io!` macro

Wrap I/O calls with `io!` to prevent accidental use inside STM transactions. Calling an `io!`-wrapped form inside `dosync` throws `IllegalStateException` rather than letting the I/O happen multiple times during a retry:

```clojure
;; good
(defn save-to-file [path content]
  (io! (spit path content)))

;; bad: could silently run multiple times if called inside dosync
(defn save-to-file [path content]
  (spit path content))
```

## Testing

`thrown?` and `thrown-with-msg?` match JVM exception types:

```clojure
(is (thrown? ArithmeticException (/ 1 0)))
(is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid" (validate! nil)))
```

## JVM Aliases

These namespaces ship only on the JVM (or have JVM-specific behavior). Use the conventional alias:

| Namespace | Alias |
|-----------|-------|
| `clojure.java.io` | `io` |
| `clojure.math` | `math` |
| `clojure.tools.logging` | `log` |
| `clojure.spec.alpha` | `s` |
| `clojure.core.async` | `async` |

`clojure.string`, `clojure.set`, `clojure.edn`, `clojure.walk`, and `clojure.pprint` are baseline and live in the [clojure](https://github.com/brackendev/clojure-skills) skill.

## Project Workflow

CLI commands, project layout, `deps.edn` configuration, lint and format tooling, the test-runner workflow, nREPL setup, and REPL conventions live in `references/project-workflows.md`. Load it on demand.

## Gotchas

### Reflection warnings

`*warn-on-reflection*` is the JVM mechanism that flags untyped Java method calls. Set it once per namespace to surface missing type hints:

```clojure
(set! *warn-on-reflection* true)
```

Add `^Type` hints (e.g., `^String`, `^java.util.List`) to parameters and return values that the compiler cannot infer.

### `gen-class` and `:gen-class`

`gen-class` produces a named JVM class at compile time. It is the right tool for AOT-compiled entry points (e.g., a `-main` invoked by `java -jar`) and Java-interop wrappers. Avoid `gen-class` when a regular `defn` or `defrecord`/`reify` would work; the AOT requirement complicates incremental development.

### Single-segment namespaces and AOT

Avoid single-segment namespaces. They generate JVM class names without a package and conflict with imported Java classes that lack a package. Use `my-app.core`, not `my-app`.
