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
