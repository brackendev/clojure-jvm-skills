---
name: clojure-jvm
description: >-
  Use when writing, editing, reviewing, or discussing JVM Clojure code that
  touches the JVM host. Triggers: .clj files, deps.edn projects, project.clj,
  build.clj, clojure.test, REPL usage, Java interop (`java.*`, `import`,
  `Throwable`, `Exception`, `AutoCloseable`), `with-open`, refs / agents / STM
  (`dosync`, `alter`, `ref`, `agent`, `send`, `send-off`, `io!`),
  `alter-var-root`, `gen-class`, `proxy`, qualified methods (`Class/method`,
  `Class/.method`, `Class/new`), `:param-tags` (`^[long]`), array class
  symbols (`String/1`), functional interfaces, Java streams (`stream-into!`,
  `stream-reduce!`), `clojure.tools.logging`, `clojure.java.io`,
  `clojure.java.process`, `clojure.repl.deps` (`add-lib`, `sync-deps`),
  `clojure.math`, `clojure.core.async` on the JVM, the
  Clojure CLI (`clj`, `clojure`), `tools.build`, `clj-kondo`, `cljfmt`,
  Cognitect `test-runner`, nREPL, or `clojure-mcp`. Covers Java interop,
  JVM-typed exception handling, JVM resource cleanup, the reference primitives
  (refs, agents, STM), `alter-var-root` workflow, and the JVM project layout
  and tooling.
user-invocable: false
---

# JVM Clojure

Layered on top of the [clojure](https://github.com/brackendev/clojure-skills) skill, which defines host-neutral Clojure family guidance. This skill applies only to JVM Clojure (`.clj` files targeting the JVM, `deps.edn` projects, `project.clj`, `build.clj`). It overrides the baseline for Java interop, JVM-typed exceptions, JVM resource cleanup, the reference primitives (`ref`, `agent`, STM), `alter-var-root`, and the Clojure CLI / `tools.build` / `clj-kondo` / `cljfmt` / test-runner / nREPL workflow.

ClojureScript and ClojureDart deltas live in their own packages ([clojurescript-skills](https://github.com/brackendev/clojurescript-skills), [clojuredart-skills](https://github.com/brackendev/clojuredart-skills)). Do not apply this skill's interop, exception, or resource rules to those dialects.

## Iron Laws

These rules prevent bugs, data loss, resource leaks, and hung processes on the JVM. The `clojure` skill's Iron Laws (CLJ-1 through CLJ-12) also apply. Never write code that breaks one. When existing code breaks a law, stop and report the law ID, the location, the offending code, and the correct pattern, then ask before changing code outside the current task. A law overrides any Key Rule or reference that seems to conflict with it.

1. **JVM-1: CLOSE BLOCK-SCOPED RESOURCES WITH `with-open`.** When a resource's lifetime ends with a block, bind it in `with-open`, which closes it in a `finally` clause even when the body throws. A resource that is returned or stored, such as a connection pool held by a component, must be closed by its owner instead. Use `try`/`finally` when the cleanup is not `.close`, such as releasing a lock (`references/errors-and-resources.md`).
2. **JVM-2: NEVER SWALLOW A `Throwable`.** Java documents `Error` as "serious problems that a reasonable application should not try to catch." Catch specific exception types or `Exception`. Catch `Throwable` only at the top level of a thread or process, to log it and then rethrow or exit, as `clojure.main` does (`references/errors-and-resources.md`).
3. **JVM-3: KEEP SIDE EFFECTS OUT OF `dosync`.** clojure.org: "I/O and other activities with side-effects should be avoided in transactions, since transactions will be retried." Act after the transaction, `send` to an agent from inside it (sends are held until commit and discarded on retry), and wrap I/O functions in `io!` so a call inside `dosync` throws (`references/state.md`).
4. **JVM-4: NEVER BLOCK INSIDE A core.async `go` BLOCK.** The `go` docstring warns that blocking operations, including core.async operations ending in `!!` and blocking I/O, risk "depleting the fixed pool of go block threads, causing all go block processing to stop." Use `<!` and `>!` inside `go`, and run blocking I/O in `io-thread` (core.async 1.9 and later) or `thread` (`references/state.md`).
5. **JVM-5: CALL `shutdown-agents` BEFORE `-main` RETURNS.** Clojure's agent pools use non-daemon threads. After `future`, `pmap`, or `send-off`, the JVM waits about 60 seconds to exit, and after `send` it never exits on its own. Call `shutdown-agents` when the program is finishing. After it, a new `future` throws and a new `send` is silently dropped (`references/state.md`).
6. **JVM-6: DEREF EVERY `future`, OR CATCH INSIDE IT.** An exception thrown in a `future` prints nothing. It surfaces only when the future is dereferenced, wrapped in `ExecutionException`. Dereference every future whose failure matters, or catch and log inside its body (`references/state.md`).

## Key Rules

1. **Use the auto-generated record factories, not the JVM interop form.** The baseline rule "use `->Foo` constructors" applies; this skill adds the JVM-specific reason: `->Foo` is an ordinary first-class function that callers reach through `:require`, while the `(Foo. ...)` interop form is a constructor call on the generated class and needs an `:import`.
2. **Prefer `swap!` and atom-only patterns when possible; reach for refs and agents only when their coordination semantics are required.** STM is for coordinated multi-identity updates; agents are for asynchronous single-identity updates that can be batched.
3. **Reuse standard Java exception types when `ex-info` is not the right fit.** `IllegalArgumentException`, `IllegalStateException`, and `UnsupportedOperationException` carry meaning across the JVM ecosystem.

## References

The Iron Laws and Key Rules are the short form. Each reference file below holds the detailed guidance and the examples for one topic. Before writing, reviewing, or refactoring JVM Clojure code, read every reference whose topic the task touches, and read the next one when the work moves to a new topic.

| When the task involves | Read |
|------------------------|------|
| Calling Java: methods, constructors, static members, qualified methods and method values, `:param-tags`, array class symbols, functional interfaces, Java streams, or external processes | `references/interop.md` |
| Catching or throwing JVM exceptions, or closing resources with `with-open` | `references/errors-and-resources.md` |
| Reading a Clojure CLI error, a stack trace, the "Full report at" file, `*e`, a classpath error, or a JVM exception such as `NullPointerException` or `ArityException` | `references/debugging.md` |
| `alter-var-root`, root bindings, refs and STM, agents, `future` and its exceptions, `shutdown-agents`, `io!`, or core.async `go` blocks | `references/state.md` |
| Clojure CLI commands, project layout, `deps.edn`, lint and format tooling, the test runner, nREPL, or REPL conventions | `references/project-workflows.md` |

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

`*warn-on-reflection*` is the JVM mechanism that flags untyped Java method calls. Set it once at the top of each namespace during development to surface missing type hints:

```clojure
(set! *warn-on-reflection* true)
```

Add `^Type` hints (e.g., `^String`, `^java.util.List`) to parameters and return values that the compiler cannot infer.

### Static fields in parentheses

Refer to a static field as a value, without parentheses: `System/out`, not `(System/out)`. Clojure 1.12 still accepts the parenthesized form. Its release notes say a future release will treat the field's value as something to invoke, which would change what `(System/out)` means.

### `gen-class` and `:gen-class`

`gen-class` produces a named JVM class at compile time. It is the right tool for AOT-compiled entry points (e.g., a `-main` invoked by `java -jar`) and Java-interop wrappers. Avoid `gen-class` when a regular `defn` or `defrecord`/`reify` would work; the AOT requirement complicates incremental development.

### Single-segment namespaces and AOT

Avoid single-segment namespaces. They generate JVM class names without a package and conflict with imported Java classes that lack a package. Use `my-app.core`, not `my-app`.
