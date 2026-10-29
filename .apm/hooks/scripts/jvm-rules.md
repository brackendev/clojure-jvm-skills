# JVM Clojure rules from clojure-jvm-skills

This is a JVM Clojure project. The Iron Laws below come from the `clojure-jvm` skill and add to the `clojure` baseline laws. Never write code that breaks one. Before writing, reviewing, or refactoring code in this project, load the `clojure-jvm` skill along with the `clojure` skill. The skills hold the full laws, their exceptions, and the references.

## Iron Laws

- JVM-1: Close block-scoped resources with `with-open`. A resource that is returned or stored is closed by its owner. Use `try`/`finally` when the cleanup is not `.close`.
- JVM-2: Never swallow a `Throwable`. Catch specific exception types or `Exception`, and catch `Throwable` only at the top of a thread or process to log it and then rethrow or exit.
- JVM-3: Keep side effects out of `dosync`, because transactions retry. Act after the transaction, `send` to an agent from inside it, and wrap I/O functions in `io!`.
- JVM-4: Never block inside a core.async `go` block. Use `<!` and `>!` inside `go`, and run blocking I/O in `io-thread` or `thread`.
- JVM-5: Call `shutdown-agents` before `-main` returns when the program used `future`, `pmap`, `send`, or `send-off`.
- JVM-6: Dereference every `future` whose failure matters, or catch and log inside its body, because an exception in a `future` prints nothing.
