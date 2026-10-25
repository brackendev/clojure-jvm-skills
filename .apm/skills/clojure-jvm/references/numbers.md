# Numbers and Money on the JVM

Part of the `clojure-jvm` skill. The `clojure` skill's Iron Law CLJ-9 forbids floating point for money on every host. This file covers the JVM number types: `BigDecimal` and the `M` literal, rounding, money from parsing to storage, parsing numbers at a boundary, and long overflow. The examples ran on Clojure 1.12.6.

## Number types

| Literal | Type |
|---------|------|
| `42` | `java.lang.Long` |
| `42N` | `clojure.lang.BigInt` |
| `1/3` | `clojure.lang.Ratio` |
| `0.1` | `java.lang.Double` |
| `0.1M` | `java.math.BigDecimal` |

Dividing two integers gives an exact result: `(/ 6 3)` is the long `2`, and `(/ 1 3)` is the ratio `1/3`. Use `quot` for an integer quotient, `(quot 7 2)` is `3`.

## BigDecimal and double contagion

`BigDecimal` arithmetic is exact. One double operand turns the whole result into a double, silently:

```clojure
(+ 0.1 0.2)    ; => 0.30000000000000004
(+ 0.1M 0.2M)  ; => 0.3M
(+ 0.1M 0.2)   ; => 0.30000000000000004, a Double
(+ 1M 1)       ; => 2M, a long converts to BigDecimal without loss
```

Keep every operand of a money calculation a `BigDecimal`, including constants (`0.075M`, not `0.075`).

`=` is true for numbers only within one category, and the clojure.org equality guide names three: "integer or ratio", "floating point (float or double)", and `BigDecimal`. So `(= 1 1M)` and `(= 0.1M 0.1)` are false. Use `==` to compare numbers of different types: `(== 1 1M)` is true. Within `BigDecimal`, `=` ignores scale, so `(= 1.0M 1.00M)` is true and `#{1.0M}` contains `1.00M`, while Java's `(.equals 1.0M 1.00M)` is false.

## Division and rounding

`BigDecimal` division throws when the exact quotient has no finite decimal form:

```clojure
(/ 1M 3M)  ; throws ArithmeticException: Non-terminating decimal expansion; no exact representable decimal result.
(/ 1M 4M)  ; => 0.25M
```

`with-precision` sets the number of significant digits and the rounding mode for the `BigDecimal` operations in its body. The docstring: "The rounding mode is one of CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UP, DOWN and UNNECESSARY; it defaults to HALF_UP."

```clojure
(with-precision 10 (/ 1M 3M))                      ; => 0.3333333333M
(with-precision 10 :rounding HALF_EVEN (/ 2M 3M))  ; => 0.6666666667M
```

Precision counts significant digits, not decimal places: `(with-precision 2 (/ 1000M 3M))` is `3.3E+2M`. `with-precision` is a dynamic binding of `*math-context*`, so a lazy sequence realized after the form returns has lost it. `(vec (with-precision 10 (map #(/ % 3M) xs)))` throws the non-terminating error. Realize the result inside, for example with `mapv`.

For money, fix the scale (the number of decimal places) with `.setScale` or `.divide` and a `java.math.RoundingMode`:

```clojure
(import '[java.math RoundingMode])

(.setScale 2.345M 2 RoundingMode/HALF_EVEN)               ; => 2.34M
(.setScale 2.345M 2 RoundingMode/HALF_UP)                 ; => 2.35M
(.setScale (* 19.99M 0.075M) 2 RoundingMode/HALF_EVEN)    ; => 1.50M, rounded from 1.49925M
(.divide 10M 3M 2 RoundingMode/HALF_EVEN)                 ; => 3.33M
(.setScale 2.345M 2)                                      ; throws ArithmeticException: Rounding necessary
```

`HALF_EVEN` rounds a tie to the even digit, and `HALF_UP` rounds a tie away from zero (`-2.345M` becomes `-2.35M`). Use the mode the business rule names. Multiplication adds the scales of its operands, so round at the step the rule specifies, such as once per line item or once per invoice. When the argument has no type hint, write `(BigDecimal/.setScale amount 2 RoundingMode/HALF_EVEN)` or hint `^BigDecimal amount`, so the call compiles without reflection.

## Money from parsing to storage

Keep a monetary amount in `BigDecimal` at every step: parsing, arithmetic, storage, and output.

- **Parse from the string.** `(bigdec "123456789.123456789")` is `123456789.123456789M`. Going through a double first loses digits: `(bigdec (parse-double "123456789.123456789"))` is `123456789.12345679M`. A double that came from arithmetic already holds the error, so `(bigdec (+ 0.1 0.2))` is `0.30000000000000004M`. Never call the `BigDecimal` constructor on a double: `(BigDecimal. 0.1)` is `0.1000000000000000055511151231257827021181583404541015625M`.
- **Validate before `bigdec`.** Clojure has no `parse-bigdec`. `bigdec` throws `NumberFormatException` on text it cannot read (`"abc"`, `"1,000.00"`, `" 1.00"`), and it accepts exponents, so `(bigdec "1e3")` is `1E+3M`. Match the format you accept first: `(when (re-matches #"-?\d+(\.\d{1,2})?" s) (bigdec s))` returns `nil` for `"19.999"` and `"1e3"`.
- **Read JSON numbers as BigDecimal.** `clojure.data.json/read-str` reads decimals as doubles unless the call passes `:bigdec true` ("If true use BigDecimal for decimal numbers instead of Double."). `(json/read-str "{\"amount\": 0.1}" :bigdec true)` is `{"amount" 0.1M}`.
- **Store in a decimal column.** Use `DECIMAL` or `NUMERIC` with an explicit scale, never `FLOAT`, `REAL`, or `DOUBLE`. With next.jdbc and H2, a `decimal(12,2)` column read back as `BigDecimal` and a `double` column as `Double`. Pass `BigDecimal` values as parameters.
- **Write without losing scale.** `(str 1.10M)` is `"1.10"`, and `clojure.data.json/write-str` writes `0.10M` as `0.10`.

## Parsing numbers at a boundary

`parse-long` and `parse-double` return `nil` for a string they cannot parse, so check for `nil` instead of catching an exception. `Long/parseLong` and `Double/parseDouble` throw `NumberFormatException`:

```clojure
(parse-long "42")      ; => 42
(parse-long "abc")     ; => nil
(Long/parseLong "abc") ; throws NumberFormatException: For input string: "abc"
```

- `parse-long` accepts an optional leading `+` or `-` and decimal digits only. It returns `nil` for `" 42"`, `"42.0"`, and a value outside the `long` range such as `"9223372036854775808"`.
- `parse-double` calls `Double/valueOf`, so it accepts surrounding whitespace, exponents, a `d` or `f` suffix (`"1.5d"`), `"NaN"`, and `"Infinity"`. Check `(Double/isFinite x)` when only ordinary numbers are valid.
- Both throw `IllegalArgumentException` ("Expected string, got java.lang.Long") when the argument is not a string.

## Long overflow

`+`, `-`, `*`, `inc`, and `dec` throw on `long` overflow. The primed versions promote to `BigInt`, and the `unchecked-` versions wrap:

```clojure
(+ Long/MAX_VALUE 1)              ; throws ArithmeticException: long overflow
(+' Long/MAX_VALUE 1)             ; => 9223372036854775808N
(unchecked-add Long/MAX_VALUE 1)  ; => -9223372036854775808
```

Use `+'`, `-'`, `*'`, `inc'`, and `dec'` when a result can exceed the `long` range. Use the `unchecked-` functions only where wrapping is intended, such as hash arithmetic.

`(set! *unchecked-math* :warn-on-boxed)`, which reports boxed math, also turns off the overflow checks in the rest of that file. The docstring: "While bound to :warn-on-boxed, same behavior as true." With it set, `(+ x 1)` on `Long/MAX_VALUE` returned `-9223372036854775808` instead of throwing.
