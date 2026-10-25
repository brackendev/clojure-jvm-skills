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

## Key Rules

1. **Use `with-open` for resource cleanup.** Not `try`/`finally`. Anything implementing `java.lang.AutoCloseable` or `java.io.Closeable` qualifies.
2. **Never catch `Throwable`.** Catch specific exception types so `OutOfMemoryError`, `StackOverflowError`, and assertion failures keep propagating.
3. **Use the auto-generated record factories, not the JVM interop form.** The baseline rule "use `->Foo` constructors" applies; this skill adds the JVM-specific reason: `->Foo` is an ordinary first-class function that callers reach through `:require`, while the `(Foo. ...)` interop form is a constructor call on the generated class and needs an `:import`.
4. **Prefer `swap!` and atom-only patterns when possible; reach for refs and agents only when their coordination semantics are required.** STM is for coordinated multi-identity updates; agents are for asynchronous single-identity updates that can be batched.
5. **Reuse standard Java exception types when `ex-info` is not the right fit.** `IllegalArgumentException`, `IllegalStateException`, and `UnsupportedOperationException` carry meaning across the JVM ecosystem.

## References

The Key Rules are the short form. Each reference file below holds the detailed guidance and the examples for one topic. Before writing, reviewing, or refactoring JVM Clojure code, read every reference whose topic the task touches, and read the next one when the work moves to a new topic.

| When the task involves | Read |
|------------------------|------|
| Calling Java: methods, constructors, static members, qualified methods and method values, `:param-tags`, array class symbols, functional interfaces, Java streams, or external processes | `references/interop.md` |
| Catching or throwing JVM exceptions, or closing resources with `with-open` | `references/errors-and-resources.md` |
| Reading a Clojure CLI error, a stack trace, the "Full report at" file, `*e`, a classpath error, or a JVM exception such as `NullPointerException` or `ArityException` | `references/debugging.md` |
| `alter-var-root`, root bindings, refs and STM, agents, or `io!` | `references/state.md` |
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
