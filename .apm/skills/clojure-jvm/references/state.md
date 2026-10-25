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
