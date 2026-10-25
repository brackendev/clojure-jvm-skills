# Exceptions and Resource Cleanup

Part of the `clojure-jvm` skill. It applies to JVM Clojure and overrides the host-neutral `clojure` skill in this domain.

## Exception Handling

`ex-info` for data-carrying exceptions remains the baseline. On the JVM, prefer standard Java exception types when the failure mode already has a named exception in the platform:

```clojure
;; good: domain failure with structured data
(throw (ex-info "Invalid input" {:value x :reason :negative}))

;; good: parameter contract failure that callers may already match against
(throw (IllegalArgumentException. "x must be positive"))
```

Never swallow a `Throwable` (Iron Law JVM-2). Java documents `Error` (the superclass of `OutOfMemoryError`, `StackOverflowError`, and `AssertionError`) as indicating "serious problems that a reasonable application should not try to catch":

```clojure
;; good: catch what the code can handle
(try (foo)
  (catch clojure.lang.ExceptionInfo ex ...)
  (catch IllegalArgumentException ex ...))

;; bad: swallows OutOfMemoryError, StackOverflowError, and assertion failures
(try (foo)
  (catch Throwable t (log/warn t "ignored")))

;; acceptable: the top level of a thread or process reports, then exits or rethrows
(defn -main [& args]
  (try (run args)
       (catch Throwable t
         (log/error t "fatal")
         (System/exit 1))))
```

`clojure.main` follows the same pattern: it catches `Throwable` around the program, reports it, and exits.

## Resource Cleanup

Bind a resource whose lifetime ends with the block in `with-open` rather than writing `try`/`finally` (Iron Law JVM-1). It calls `.close` on each bound value in a `finally` clause, in reverse declaration order:

```clojure
;; good: slurp reads the whole stream before the body returns
(with-open [rdr (clojure.java.io/reader "file.txt")]
  (slurp rdr))

;; bad: verbose and easy to get wrong
(let [rdr (clojure.java.io/reader "file.txt")]
  (try
    (slurp rdr)
    (finally
      (.close rdr))))
```

`with-open` calls `.close` by name, so it works on any object with a no-argument `.close` method, not only `AutoCloseable` and `Closeable` values. Use `try`/`finally` when cleanup is a different call, such as `(.unlock lock)` or `(.shutdown executor)`. Do not bind a resource in `with-open` when it must outlive the block, such as a stream the function returns or a pool a component holds. Its owner closes it. `ExecutorService` has a `close` method on JDK 19 and later, which runs the tasks already submitted and waits until they finish. JDK 17 has no such method, and `with-open` on an executor there throws "No matching field found: close", so call `.shutdown` in `try`/`finally` when the project supports a JDK older than 19.

### Lazy results inside `with-open`

`with-open` closes the resource as soon as its body returns, so the body must consume every lazy sequence that reads from the resource (Iron Law CLJ-2 in the `clojure` skill). Returning the sequence unread is the common mistake. `line-seq` reads the first line before it returns and the rest on demand, so the bug hides until a caller reads past the first line:

```clojure
;; bad: returns a lazy sequence over a reader that is already closed
(defn read-lines [f]
  (with-open [r (io/reader f)]
    (line-seq r)))

(first (read-lines "app.log"))  ; => the first line, read before the reader closed
(count (read-lines "app.log"))  ; throws java.io.IOException: Stream closed
```

The Clojure CLI reports it as `Execution error (IOException) at java.io.BufferedReader/ensureOpen`, followed by `Stream closed`. A test that reads only the first line, or reads an empty file, passes. Wrapping the sequence in `map` or `filter` does not help, because they are lazy too.

Realize the result, or reduce it, before the body returns:

```clojure
;; good: every line, realized while the reader is open
(with-open [r (io/reader f)]
  (into [] (line-seq r)))  ; or (doall (line-seq r)), or (mapv parse-line (line-seq r))

;; good: keep only what is needed, without holding every line in memory
(with-open [r (io/reader f)]
  (into [] (filter #(str/starts-with? % "ERROR")) (line-seq r)))

(with-open [r (io/reader f)]
  (reduce (fn [n _] (inc n)) 0 (line-seq r)))  ; counts the lines

;; good: a side effect for each line
(with-open [r (io/reader f)]
  (run! println (line-seq r)))
```

`reduce`, `transduce`, `run!`, and `into` with a transducer read the file one line at a time, so they also suit files too large to hold in memory. `doall`, `vec`, and `into []` without a transducer keep every line.
