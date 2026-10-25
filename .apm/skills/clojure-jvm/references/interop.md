# Java Interop

Part of the `clojure-jvm` skill. It applies to JVM Clojure and overrides the host-neutral `clojure` skill in this domain.

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

### Qualified methods and method values

Clojure 1.12 added qualified method symbols. `Classname/method` names a static method, `Classname/.method` names an instance method, and `Classname/new` names a constructor. Use them to pass a Java method to a higher-order function instead of wrapping it in an anonymous function:

```clojure
;; good
(map String/.toUpperCase names)
(map Long/parseLong ids)

;; bad: hand-written wrappers around Java methods
(map #(.toUpperCase ^String %) names)
(map #(Long/parseLong %) ids)
```

The qualifying class also acts as the type hint, so `(String/.length s)` compiles without reflection even when `s` has no hint. The dot forms in the previous section remain correct in invocation position. Reach for qualified methods when the method is used as a value or when the class is otherwise unknown to the compiler.

A method value cannot choose between overloads by itself, and the compiler falls back to reflection. Add `:param-tags` with the `^[...]` reader syntax to select one signature. Use `_` for a parameter whose type does not need to be specified:

```clojure
;; good: resolves Math/abs(double) at compile time
(map ^[double] Math/abs readings)

;; bad: Math/abs is overloaded, so this call reflects
(map Math/abs readings)
```

Array classes have symbol syntax: `String/1` is a one-dimensional `String` array and `long/2` is a two-dimensional `long` array. Use it for type hints and class values instead of the older string form:

```clojure
;; good
(defn join-all [^String/1 parts] (String/join "," parts))

;; bad
(defn join-all [^"[Ljava.lang.String;" parts] (String/join "," parts))
```

### Functional interfaces, suppliers, and streams

Since Clojure 1.12, a Clojure function can be passed directly to a Java method that expects a functional interface (`Predicate`, `Function`, `Runnable`, and so on). The compiler builds the adapter. Do not write a `reify` for it:

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

Use `clojure.java.process` (Clojure 1.12+) to run external commands. `exec` returns the captured standard output, and throws when the process exits with a nonzero status. `start` returns the `java.lang.Process` for full control over its streams. Prefer it over `clojure.java.shell` in new code.

```clojure
(require '[clojure.java.process :as process])

(process/exec "git" "rev-parse" "HEAD")
```
