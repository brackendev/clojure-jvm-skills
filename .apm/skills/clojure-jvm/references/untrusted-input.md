# Untrusted Input on the JVM

Part of the `clojure-jvm` skill. The host-neutral rules are in `clojure/references/untrusted-input.md` in the `clojure` skill (Iron Laws CLJ-10 and CLJ-11). This file gives the JVM APIs and examples for them: SQL through next.jdbc and HoneySQL, external commands through `clojure.java.process`, `clojure.java.shell`, and `babashka.process`, and data through `clojure.edn`.

## SQL with next.jdbc

Put a `?` in the SQL string for each value, and pass the values after the string in the same vector. The next.jdbc guide: "with `?` in the SQL and then the corresponding parameter values in the vector after the SQL string." next.jdbc builds a `java.sql.PreparedStatement` from them, and the driver sends each value as data:

```clojure
;; bad: id "1 or 1=1" returns every row
(jdbc/execute! ds [(str "select * from users where id = " id)])

;; good: id is sent as a parameter
(jdbc/execute! ds ["select * from users where id = ?" id])
```

Against an H2 in-memory database, the joined query with `id` set to `"1 or 1=1"` returned every row, and the parameterized query threw `JdbcSQLDataException` ("Data conversion error converting "1 or 1=1"") without running. A name such as `"x' or '1'='1"` joined into `where name = '...'` also returned every row, and as a parameter it matched none.

A parameter is always a value, never part of the statement. H2 rejected `order by ?` with the same "Data conversion error", because it received the column name as a value. Map a user-chosen column or sort direction through an allowlist of fixed SQL fragments, and join only those fragments:

```clojure
(def sort-columns {"name" "name", "created" "created_at"})
(def directions {"asc" "asc", "desc" "desc"})

(let [column (get sort-columns (:sort params) "name")
      direction (get directions (:dir params) "asc")]
  (jdbc/execute! ds [(str "select * from users order by " column " " direction)]))
```

An unknown value falls back to the default, so `{:sort "password_hash; drop table users"}` sorts by `name`.

### HoneySQL

HoneySQL turns strings, numbers, and other values into parameters, and keywords and symbols into identifiers. The HoneySQL README: column names "can be provided as keywords or symbols (but not strings -- HoneySQL treats strings as values that should be lifted out of the SQL as parameters)."

```clojure
(sql/format {:select [:id] :from [:users] :where [:= :name name]})
;; => ["SELECT id FROM users WHERE name = ?" "x' or '1'='1"]
```

Never build a keyword from input. HoneySQL rejects an identifier that contains `;` or `,` ("suspicious character found in entity") and quotes one that contains other special characters, with or without `{:quoted true}`. The input still chooses which column or table the query names, though: `(keyword "password_hash")` in `:order-by` produces `ORDER BY password_hash ASC`, and an unknown direction keyword goes into the SQL unchanged (`ORDER BY name SIDEWAYS`). In a value position a keyword is a column, not a value: `[:= :status (keyword "active")]` produces `status = active`, which compares two columns. Pass a value as a string, or wrap it in `[:lift ...]`, and map identifiers through an allowlist of keywords:

```clojure
(def sort-keys {"name" :name, "created" :created_at})
(def direction-keys {"asc" :asc, "desc" :desc})

(sql/format {:select [:id] :from [:users]
             :order-by [[(get sort-keys (:sort params) :name)
                         (get direction-keys (:dir params) :asc)]]})
;; {:sort "created" :dir "desc"} => ["SELECT id FROM users ORDER BY created_at DESC"]
```

## External commands

Pass the program and each argument as separate strings. `clojure.java.process` and `clojure.java.shell/sh` hand them to the operating system as a list of arguments, without a shell, so each value stays one argument whatever characters it holds:

```clojure
(require '[clojure.java.process :as process])

;; good: file is one argument, even "my file.txt" or "x; echo INJECTED"
(process/exec "wc" "-l" file)

;; bad: the shell parses the joined string, so input "x; echo INJECTED" runs a second command
(process/exec "sh" "-c" (str "echo " input))
```

With `input` set to `"x; echo INJECTED"`, the bad form printed `x` and then `INJECTED`, and `(process/exec "echo" input)` printed the input unchanged.

When the command needs a shell feature such as a pipe or a redirect, keep the script a fixed string and pass the input after it. POSIX `sh -c` sets `$0` from the first argument after the script, "and the positional parameters ($1, $2, and so on) in sequence from the remaining argument operands." Quote each parameter inside the script:

```clojure
;; good: the script is fixed, and file reaches it as "$1"
(process/exec "sh" "-c" "wc -l < \"$1\"" "sh" file)
```

`process/exec` throws `RuntimeException` ("Process failed with exit=N") when the command exits with a nonzero status. `references/interop.md` covers its options and how to keep the output of a failing command. `clojure.java.shell/sh` also takes each argument as a separate string. It returns `{:exit :out :err}` and does not throw on a nonzero exit, so check `:exit`.

A separate argument can still reach the program as an option, such as a file name that starts with `-`. The `clojure` skill covers validating arguments and putting `--` before positional arguments.

### Babashka

`babashka.process`, which Babashka includes, splits the first string argument of `shell`, `process`, and `sh` on spaces. Its README: "All of `shell`, `process` and `sh` support tokenization on the first string argument using `tokenize`." No shell runs, so `;` and `|` have no effect, and the `shell` docstring says it "does not support bash syntax like `ls *.clj`." Input joined into that first string is still split into several arguments, and quote characters inside it change the split: `(p/tokenize "echo it's")` returns `["echo" "its"]`.

```clojure
(require '[babashka.process :as p])

;; bad: "my dir" becomes the arguments "my" and "dir", and "-n hello" becomes an echo option
(p/shell (str "ls " folder))
(p/sh (str "echo " input))

;; good: each value is one argument
(p/shell "ls" folder)
(p/sh ["echo" input])
```

Keep the first string to a fixed program name or fixed command words, and pass every value as its own argument or inside a vector. `shell` throws on a nonzero exit unless the options map has `:continue true`. `process` and `sh` do not check the exit code, so call `p/check` or read `:exit`.

## Reading EDN

Read data from outside the program with `clojure.edn/read-string` or `clojure.edn/read`. Never use `clojure.core/read-string` or `read` on it. The `read-string` docstring: "Note that read-string can execute code (controlled by *read-eval*), and as such should be used only with trusted sources. For data structure interop use clojure.edn/read-string."

```clojure
(read-string "#=(+ 1 2)")      ; => 3, because the reader evaluated the form
(edn/read-string "#=(+ 1 2)")  ; throws RuntimeException: No dispatch macro for: =
```

Binding `*read-eval*` to false does not make the core reader safe. It disables `#=` ("EvalReader not allowed when *read-eval* is false.") and record literals, but the reader still calls every tag reader in `*data-readers*`, which includes the readers that libraries register in `data_readers.clj` files on the classpath, and it still calls `*default-data-reader-fn*`. The `*read-eval*` docstring says the full reader "should never be used with untrusted sources. See also: clojure.edn/read."

`clojure.edn` reads only the built-in `#inst` and `#uuid` tags, and throws for any other tag unless the options name a reader:

```clojure
(edn/read-string "#money/usd \"1.50\"")
;; throws RuntimeException: No reader function for tag money/usd

(edn/read-string {:readers {'money/usd (fn [s] (->Money (bigdec s) :usd))}}
                 "#money/usd \"1.50\"")
;; => #my.app.Money{:amount 1.50M, :currency :usd}

(edn/read-string {:default tagged-literal} "#money/usd \"1.50\"")
;; => #money/usd "1.50", a clojure.lang.TaggedLiteral that keeps the tag and value as data
```

The reader calls the `:readers` functions and the `:default` function with values from the input, so each must be safe for any value. Parse and validate in them. Never `eval`, `resolve`, or load a class that the input names.

`edn/read-string` reads the first form and ignores the rest of the string, so `(edn/read-string "{:a 1} {:b 2}")` returns `{:a 1}`. It returns `nil` for an empty string. Validate the value it returns before using it. For a file or stream, read with `edn/read` from a `java.io.PushbackReader` inside `with-open`:

```clojure
(with-open [r (java.io.PushbackReader. (io/reader "config.edn"))]
  (edn/read {:eof nil} r))
```
