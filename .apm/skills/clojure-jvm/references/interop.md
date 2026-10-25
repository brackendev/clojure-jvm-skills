# Java Interop

Part of the `clojure-jvm` skill. It applies to JVM Clojure and overrides the host-neutral `clojure` skill in this domain.

## Java Interop

Use sugared syntax:

```clojure
;; good
(java.util.ArrayList. 100)
(System/getProperty "user.home")
(.substring "hello" 1 3)
Integer/MAX_VALUE

;; bad
(new java.util.ArrayList 100)
(. System getProperty "user.home")
(. "hello" substring 1 3)
(. Integer MAX_VALUE)
```

For `java.lang.Math` functions, prefer `clojure.math` (alias `math`), as in `(math/pow 2 10)`. Its functions wrap `java.lang.Math` with only `long` and `double` overloads, so calls compile without reflection.

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

### Qualified methods and method values

Qualified method symbols name Java members as values. `Classname/method` names a static method, `Classname/.method` names an instance method, and `Classname/new` names a constructor. Use them to pass a Java method to a higher-order function instead of wrapping it in an anonymous function:

```clojure
;; good
(map String/.toUpperCase names)
(map Instant/ofEpochMilli timestamps)

;; bad: hand-written wrappers around Java methods
(map #(.toUpperCase ^String %) names)
(map #(Instant/ofEpochMilli %) timestamps)
```

To parse numbers from text, use `parse-long` and `parse-double`, which return `nil` on bad input, rather than `Long/parseLong`, which throws. `references/numbers.md` covers parsing at a boundary.

The qualifying class also acts as the type hint, so `(String/.length s)` compiles without reflection even when `s` has no hint. The dot forms in the previous section are also correct in invocation position. Reach for qualified methods when the method is used as a value or when the class is otherwise unknown to the compiler.

A method value cannot choose between overloads by itself, and the compiler falls back to reflection. Add `:param-tags` with the `^[...]` reader syntax to select one signature. Use `_` for a parameter whose type does not need to be specified. The compiler ignores `:param-tags` on unqualified `.method` forms, so put them on the qualified symbol:

```clojure
;; good: resolves Math/abs(double) at compile time
(map ^[double] Math/abs readings)

;; bad: Math/abs is overloaded, so this call reflects
(map Math/abs readings)
```

Array classes have symbol syntax: `String/1` is a one-dimensional `String` array and `long/2` is a two-dimensional `long` array. Use it for type hints and class values instead of the JVM descriptor string form:

```clojure
;; good
(defn join-all [^String/1 parts] (String/join "," parts))

;; bad
(defn join-all [^"[Ljava.lang.String;" parts] (String/join "," parts))
```

### Functional interfaces, suppliers, and streams

A Clojure function can be passed directly to a Java method that expects a functional interface (`Predicate`, `Function`, `Runnable`, and so on). The compiler builds the adapter. Do not write a `reify` for it:

```clojure
;; good
(.removeIf items even?)

;; bad
(.removeIf items (reify java.util.function.Predicate
                   (test [_ x] (even? x))))
```

To reuse one adapter, for example inside a loop, hint a `let` binding with the interface type: `(let [^java.util.function.Predicate keep? even?] ...)`.

Every `IDeref` (`delay`, `future`, `promise`, `atom`, `ref`, `agent`, `var`) implements `java.util.function.Supplier`, so pass one directly where a `Supplier` is expected.

Consume a `java.util.stream.Stream` with `stream-seq!`, `stream-reduce!`, `stream-transduce!`, or `stream-into!`. Each of them is a terminal operation that consumes the stream:

```clojure
(stream-into! [] (.stream java-list))
(stream-transduce! (map inc) + (.stream java-list))
```

### External processes

Use `clojure.java.process` to run external commands. `exec` returns the captured standard output, and throws `RuntimeException` ("Process failed with exit=N") when the process exits with a nonzero status. By default `exec` sends the child's standard error to the parent's standard error, so pass `{:err :stdout}` as the first argument to capture both streams. The exception carries only the exit status, so the output of a failing command is lost, even with `{:err :stdout}`. When the caller needs that output, use `start`, which returns the `java.lang.Process`, and read its streams. Prefer `clojure.java.process` over `clojure.java.shell` in new code. The Clojure 1.12 release notes call it "easier to use."

```clojure
(require '[clojure.java.process :as process])

(process/exec "git" "rev-parse" "HEAD")

;; keep the output and the exit status of a command that can fail
(let [proc (process/start {:err :stdout} "ls" "no-such-file")
      output (slurp (process/stdout proc))
      exit @(process/exit-ref proc)]
  {:exit exit :output output})
;; => {:exit 1, :output "ls: no-such-file: No such file or directory\n"}
```

Pass each argument as a separate string, never a command joined from input. `references/untrusted-input.md` covers command injection, `sh -c`, and `babashka.process`.
