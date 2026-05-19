# Changelog

## [Unreleased]

## 0.1.3

### Added

- A repo-root `CONVENTIONS.md` that defines the argument grammar, scope vocabulary, and mutation defaults any user-invocable skill in this package will follow. Three rules cover argument grammar (one sanctioned flag, `--report`), scope vocabulary (`(no argument)`, `all`, `<path>`), and mutation-as-default. The document includes worked examples drawn from the companion [clojure-skills](https://github.com/brackendev/clojure-skills) package because this plugin ships no user-invocable skill today, plus an author checklist for any future addition.

### Changed

- `CONTRIBUTING.md` now links to `CONVENTIONS.md` from the Skill conventions section.

## 0.1.2

### Changed

- The `clojure-jvm` `SKILL.md` now references [clojurescript-skills](https://github.com/brackendev/clojurescript-skills) as a published companion. The previous "when published" qualifier has been removed.

## 0.1.1

### Changed

- Merged the `MCP Integration` section in `references/project-workflows.md` into the `REPL` section. The recovery step (start nREPL with `clj -M:nrepl &` when none is running) now leads the section and is unconditional, so it applies whether or not `clojure-mcp` is in use. The `clojure-mcp` paragraph follows as an enhancement that connects over the running nREPL.

## 0.1.0

### Added

- Initial release. JVM-specific Clojure skill extracted from [clojure-skills](https://github.com/brackendev/clojure-skills) so the baseline skill can stay host-neutral across `.clj`, `.cljs`, `.cljc`, and `.cljd`.
- **clojure-jvm** (model-invoked): Java interop, JVM-typed exception handling, JVM resource cleanup (`with-open`), reference primitives (`ref`, `agent`, STM, `io!`), `alter-var-root`, `with-redefs` semantics, JVM aliases, and reflection-warning guidance. Defers to the [clojure](https://github.com/brackendev/clojure-skills) baseline for host-neutral style. Pulls deeper detail from `references/project-workflows.md` on demand (Clojure CLI, `deps.edn`, `tools.build`, `clj-kondo`, `cljfmt`, Cognitect `test-runner`, nREPL, `clojure-mcp`).
