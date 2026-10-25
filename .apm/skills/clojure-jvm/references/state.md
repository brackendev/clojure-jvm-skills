# Vars, Refs, and Agents

Part of the `clojure-jvm` skill. It applies to JVM Clojure and overrides the host-neutral `clojure` skill in this domain.

## Vars and Root Bindings

Use `alter-var-root` to rebind a var's root value rather than redefining it:

```clojure
;; good
(def thing 1)
(alter-var-root #'thing (constantly nil))

;; bad: replaces the var's metadata (docstring, :private) along with its value
(def thing 1)
(def thing nil)
```

`with-redefs` temporarily replaces the root values of vars, so the change is visible in every thread while the body runs. Use it sparingly and only for external boundaries (HTTP, database, clock). Prefer passing dependencies as function arguments. Multi-threaded test runners can leave permanent damage if two redefs race.

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

### Exiting after futures and agents (Iron Law JVM-5)

clojure.org: "use of Agents starts a pool of non-daemon background threads that will prevent shutdown of the JVM. Use shutdown-agents to terminate these threads and allow shutdown." `future`, `pmap`, and `send-off` use a cached pool whose idle threads end after 60 seconds, so a `-main` that dereferenced one `future` took about 60 seconds to exit in a test with Clojure 1.12. `send` uses a fixed pool whose threads never end, so after a `send` the JVM does not exit on its own:

```clojure
;; good: the program exits as soon as -main returns
(defn -main [& args]
  (println @(future (compute)))
  (shutdown-agents))
```

Call `shutdown-agents` only when the program is finishing. Running actions complete, but no new actions are accepted: a new `future` throws `RejectedExecutionException`, and a new `send` returns normally while its action never runs. `clj -X` runs `future` and `send-off` work on daemon threads, but a `send` still keeps the JVM running.

### Exceptions in futures (Iron Law JVM-6)

A `future` that throws prints nothing. The exception surfaces only when the future is dereferenced, wrapped in `java.util.concurrent.ExecutionException`:

```clojure
;; bad: a failure here is never seen
(future (send-report! data))

;; good: catch and log inside the body when nothing dereferences the future
(future
  (try (send-report! data)
       (catch Exception e (log/error e "report failed"))))
```

Agents behave similarly: clojure.org says an exception in an agent action "will be cached in the Agent itself," so check `agent-error` or set an error handler.

### core.async go blocks (Iron Law JVM-4)

`go` runs its body on a small fixed pool of threads and parks, rather than blocks, at `<!`, `>!`, and `alt!`. The `go` docstring says go blocks "should not (either directly or indirectly) perform operations that may block indefinitely," which includes the core.async operations ending in `!!` and blocking I/O, because they can deplete the pool and stop every go block:

```clojure
;; bad: blocks a go thread
(async/go (let [v (async/<!! ch)] (save-to-db! v)))

;; good: park in go, and run blocking I/O on its own thread
(async/go (let [v (async/<! ch)]
            (async/<! (async/io-thread (save-to-db! v)))))
```

`io-thread` (core.async 1.9 and later) "may do blocking I/O but must not do extended computation," and it uses a virtual thread when one is available. On earlier core.async versions, or for work that mixes I/O and computation, use `async/thread`.

In development, set the Java system property `clojure.core.async.go-checking` to `true`. core.async's own blocking operations, such as `<!!` and `>!!`, then check whether they run on a go block thread. The check does not cover other blocking I/O.

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
