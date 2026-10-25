# Debugging JVM Clojure Errors

Part of the `clojure-jvm` skill. It adds the JVM error formats and exception tools to the host-neutral procedure in the `clojure-debug` skill.

## Reading Clojure CLI errors

The JVM Clojure CLI prints a short summary and writes the full report to a temporary file:

```
Syntax error macroexpanding clojure.core/let at (app/bad.clj:3:3).
[y] - failed: even-number-of-forms? at: [:bindings] spec: :clojure.core.specs.alpha/bindings

Full report at:
/var/folders/.../clojure-4925883397276477484.edn
```

| First line begins with | Phase | Usual causes |
|------------------------|-------|--------------|
| `Syntax error reading source` | Read | Unbalanced delimiters or an invalid literal. "EOF while reading, starting at line N" means a form that opens on line N never closes. |
| `Syntax error macroexpanding` | Macroexpansion | A macro call has the wrong shape, and "did not conform to spec" names the failing part, for example an odd number of `let` bindings. Without a spec message, the macro itself threw while expanding, and the next line is its error. |
| `Syntax error compiling` | Compile | "Unable to resolve symbol" means a missing `:require` or `:refer`, a typo, or a use before definition. |
| `Execution error (Type) at ns/fn (file:line)` | Runtime | The named exception type was thrown while the code ran. The location is where it surfaced. |

Open the "Full report at" file when the summary is not enough. It is EDN with `:clojure.main/triage` (phase, file, line, and cause) and `:clojure.main/trace`, whose `:via` vector lists each exception in the chain from outermost to innermost, with its type, message, `:data`, and location.

## The last exception at the REPL

At a REPL, `*e` holds the last exception. `(clojure.repl/pst *e 30)` prints its trace, and `(Throwable->map *e)` returns `:via`, `:trace`, `:cause`, and `:data`. `:via` lists the chain from outermost to innermost, and `:cause` and `:data` belong to the innermost exception.

## Classpath errors

"Could not locate app/nope__init.class, app/nope.clj or app/nope.cljc on classpath" means the namespace is not on the classpath that this command uses. Check the `ns` name against the file path (hyphens in the namespace become underscores in the file name), the `:paths` and aliases in `deps.edn` for this command, and the dependency coordinate.

## Common runtime errors

| Error | What it usually means |
|-------|-----------------------|
| `NullPointerException` from a core function | A `nil` reached code that needs a value, often from a missing map key or an empty lookup. Find where the value was first `nil`, not only where it was used. |
| `ClassCastException`: "cannot be cast to class clojure.lang.IFn" | A value that is not a function, such as a number or a string, was called as one. An extra pair of parentheses around a value is the usual cause. |
| `ArityException`: "Wrong number of args (N) passed to" | A call site and a definition disagree, or a keyword was called with no map, as in `(:k)`. Check the definition that is loaded, which may differ from the file if the REPL holds an old one. |
| `IllegalArgumentException`: "Don't know how to create ISeq from" | A non-collection was passed where a sequence was expected. |
