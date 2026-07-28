# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.9] - 2026-07-29

### Changed

- The README's runtime sentence now scopes its list to APM's default target set rather than claiming every runtime APM supports. APM 0.26.0 also supports Antigravity, IntelliJ, and several experimental runtimes, none of which `apm install --target all` includes. The eight runtimes named are unchanged, and the sentence adds that Antigravity works when named explicitly with `--target antigravity`.

## [0.1.8] - 2026-07-29

### Removed

- The package manifest no longer declares the top-level `target: all` field. The APM manifest schema deprecates the `all` value: a parser treats the field as though it were absent and falls through to the `--target` flag or filesystem auto-detection, and the value is scheduled to become a hard parse error in a future APM release. Removing the field makes that fall-through behavior permanent. Installation behavior is unchanged, because APM already resolved targets by auto-detection rather than from this field. The separate `compilation.target` setting is not affected.

## [0.1.7] - 2026-06-15

### Changed

- Add Kiro to the README's runtime list. APM 0.20.0 added Kiro as a first-class install target included in `apm install --target all`, so the README now lists it alongside Claude Code, Codex, OpenCode, Cursor, Copilot, Gemini, and Windsurf.

## [0.1.6] - 2026-05-28

### Added

- `CONVENTIONS.md` gains Rule 4: vendored and generated paths are excluded by default from mutating skills that walk the workspace. The rule sits alongside the existing three rules (renamed from "The three rules" to "The four rules"). Two filters apply together (`.gitignore` matches plus a hardcoded floor of dependency directories, build outputs, and lock files). The override rides on Rule 1's existing `<path>` `<glob>` grammar; no new flag is introduced. This plugin ships no user-invocable mutating skill today, so no SKILL.md changes accompany the rule. The contract is recorded so any future skill that walks the workspace matches its companion packages.

## [0.1.5] - 2026-05-20

### Changed

- `CONTRIBUTING.md` Layout table now includes a row for `CONVENTIONS.md`, aligning the package with the family-wide structural template.

## [0.1.4] - 2026-05-20

### Changed

- Cross-references to `clojure-skills` updated for the `clj-tidy` → `clj-fix` rename in that companion package. The worked examples in `CONVENTIONS.md` now show `/clj-fix` rather than `/clj-tidy`.
- The `CONVENTIONS.md` command-verb section now lists noun-first command suffixes (`-fix`, `-sync`, `-review`, and the rest) rather than verb-first patterns, reflecting the family's noun-first canonical naming.

## [0.1.3] - 2026-05-19

### Added

- A repo-root `CONVENTIONS.md` that defines the argument grammar, scope vocabulary, and mutation defaults any user-invocable skill in this package will follow. Three rules cover argument grammar (one sanctioned flag, `--report`), scope vocabulary (`(no argument)`, `all`, `<path>`), and mutation-as-default. The document includes worked examples drawn from the companion [clojure-skills](https://github.com/brackendev/clojure-skills) package because this plugin ships no user-invocable skill today, plus an author checklist for any future addition.

### Changed

- `CONTRIBUTING.md` now links to `CONVENTIONS.md` from the Skill conventions section.

## [0.1.2] - 2026-05-17

### Changed

- The `clojure-jvm` `SKILL.md` now references [clojurescript-skills](https://github.com/brackendev/clojurescript-skills) as a published companion. The previous "when published" qualifier has been removed.

## [0.1.1] - 2026-05-17

### Changed

- Merged the `MCP Integration` section in `references/project-workflows.md` into the `REPL` section. The recovery step (start nREPL with `clj -M:nrepl &` when none is running) now leads the section and is unconditional, so it applies whether or not `clojure-mcp` is in use. The `clojure-mcp` paragraph follows as an enhancement that connects over the running nREPL.

## [0.1.0] - 2026-05-17

### Added

- Initial release. JVM-specific Clojure skill extracted from [clojure-skills](https://github.com/brackendev/clojure-skills) so the baseline skill can stay host-neutral across `.clj`, `.cljs`, `.cljc`, and `.cljd`.
- **clojure-jvm** (model-invoked): Java interop, JVM-typed exception handling, JVM resource cleanup (`with-open`), reference primitives (`ref`, `agent`, STM, `io!`), `alter-var-root`, `with-redefs` semantics, JVM aliases, and reflection-warning guidance. Defers to the [clojure](https://github.com/brackendev/clojure-skills) baseline for host-neutral style. Pulls deeper detail from `references/project-workflows.md` on demand (Clojure CLI, `deps.edn`, `tools.build`, `clj-kondo`, `cljfmt`, Cognitect `test-runner`, nREPL, `clojure-mcp`).
