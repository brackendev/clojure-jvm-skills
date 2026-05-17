# Changelog

## [Unreleased]

## 0.1.0

### Added

- Initial release. JVM-specific Clojure skill extracted from [clojure-skills](https://github.com/brackendev/clojure-skills) so the baseline skill can stay host-neutral across `.clj`, `.cljs`, `.cljc`, and `.cljd`.
- **clojure-jvm** (model-invoked): Java interop, JVM-typed exception handling, JVM resource cleanup (`with-open`), reference primitives (`ref`, `agent`, STM, `io!`), `alter-var-root`, `with-redefs` semantics, JVM aliases, and reflection-warning guidance. Defers to the [clojure](https://github.com/brackendev/clojure-skills) baseline for host-neutral style. Pulls deeper detail from `references/project-workflows.md` on demand (Clojure CLI, `deps.edn`, `tools.build`, `clj-kondo`, `cljfmt`, Cognitect `test-runner`, nREPL, `clojure-mcp`).
