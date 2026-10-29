# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.23] - 2026-10-29

### Added

- `jvm-rules` hook: delivers the condensed JVM-1 through JVM-6 Iron Laws to the agent at session start, after `/clear` or a compaction, and on the first matching edit when the session started outside a matching project, with an instruction to load the `clojure-jvm` skill along with the `clojure` skill. Verified only under Claude Code. Copilot and Cursor receive no copy.

## [0.1.22] - 2026-10-29

### Changed

- The Install section requires APM 0.34.0 and installs it from PyPI with `uv tool install apm-cli==0.34.0` after removing any Homebrew copy. APM 0.35.0 is not supported because it can delete installed skill files ([microsoft/apm#3372](https://github.com/microsoft/apm/issues/3372)).

## [0.1.21] - 2026-10-25

### Added

- The `clojure-jvm` skill has a new untrusted input reference. It shows how to pass SQL values as next.jdbc parameters and map a user-chosen column or sort direction through an allowlist, why a HoneySQL keyword must never come from input, and how to run external commands with separate arguments through `clojure.java.process`, `clojure.java.shell`, and `babashka.process`, which splits its first string argument on spaces. It also covers reading untrusted EDN with `clojure.edn` and its `:readers` and `:default` options, and why binding `*read-eval*` to false does not make `clojure.core/read-string` safe.
- The `clojure-jvm` skill has a new numbers reference. It covers `BigDecimal` and the `M` literal, how one double operand turns a result into a double, `with-precision` and the rounding modes, rounding money with `.setScale`, keeping money in `BigDecimal` from parsing to storage, `parse-long` and `parse-double` at a boundary, and long overflow, including the `*unchecked-math*` setting that turns off overflow checks.
- The resource reference shows how a lazy sequence such as `line-seq` that is returned from `with-open` fails with "Stream closed" when it is read, why a test that reads only the first line still passes, and how to realize or reduce the result inside the block.
- The project workflows reference gives an `:nrepl` alias for `deps.edn` with nREPL 1.8.0 and names `lein repl :headless` for Leiningen projects. It also says to run the tests of a project set up as Kaocha documents with `bin/kaocha` or `clojure -M:test`, because `clojure -X:test` fails in such a project.
- The debugging reference shows how to print the full error report to standard error with `--report stderr`, and it covers the `Unexpected error (Type) macroexpanding` and `Syntax error (Type) compiling` first lines.
- The interop reference recommends `clojure.math` for `java.lang.Math` functions and notes that the compiler ignores `:param-tags` on unqualified `.method` forms. It says that `clojure.java.process/exec` sends standard error to the parent process unless the call passes `{:err :stdout}`, and that a failing `exec` call discards the output it captured, so it shows how to keep that output with `start`.
- The state reference explains how to set the `clojure.core.async.go-checking` property and where its exception goes.

### Changed

- The `clojure-jvm` skill describes only the current Clojure and core.async releases. Version labels such as "Clojure 1.12 added" and "core.async 1.9 and later" are gone, along with the guidance for earlier core.async versions.
- The skill runs every command with `clojure` and keeps `clj` only for a person who starts an interactive REPL, because `clj` exits with an error when `rlwrap` is not installed. The project workflows reference explains the difference in its `clojure` and `clj` section.
- The project workflows reference leaves clojure-mcp, finding the nREPL port, and reloading with `:reload` to the `clojure-repl` skill in clojure-skills, and keeps the JVM-specific parts: starting nREPL, adding a library with `add-lib`, and running tests from the REPL.
- The interop reference shows method values with `Instant/ofEpochMilli` instead of `Long/parseLong`, and it points to `parse-long` and `parse-double` for parsing text, because they return `nil` on bad input instead of throwing.
- The `failure-hints` classpath hint names the `project.clj` source paths and profiles as well as the `deps.edn` paths and aliases, so the hint also fits Leiningen commands.
- `CONVENTIONS.md` lists electric-skills among the companion packages, and its worked examples match the current clojure-skills commands: `/clj-smells-fix` replaces `/clj-smells-review`, and the `/clj-fix` lint step writes fixes unless `--report` is given.
- The `:cljfmt` alias in the `deps.edn` example uses `:replace-deps`, as the clojure-skills fix skills do, so cljfmt runs with only its own dependencies and does not fail when the project pins a conflicting library or an unresolvable local dependency.

### Fixed

- The `clojure-jvm` skill description fits within the 1,024-character limit that the Agent Skills specification sets for `description`. It was longer. The trigger terms are unchanged, and only the closing summary of the covered topics is shorter.
- The REPL test example reloads the changed source namespace together with the test namespace. It reloaded only the test namespace, so the tests ran against the code from before the edit. For a full test run, the reference names the project's own test command instead of `clj -X:test`, which fails in a Kaocha project.
- The README states the Java requirement as Java 8 or higher, the minimum that Clojure and the Clojure CLI document, and says that clojure-mcp needs Java 17 or higher. It said Java 17 or higher for the whole package.
- The state reference no longer says that go blocks run on a small fixed pool. In core.async 1.9.865 the default dispatch pool grows without a limit, so a blocking call in a `go` block adds a thread, and it stops other go blocks only when the application supplies a bounded executor. Iron Law JVM-4 quotes the rule from the `go` docstring without the fixed-pool claim.
- The resource reference says that `ExecutorService` has a `close` method only on JDK 19 and later, and that closing it runs the tasks already submitted before it returns. It said that closing an executor waits for its running tasks on any JDK.
- The single-segment namespace gotcha gives the actual JVM problem: AOT compilation puts the classes in the unnamed Java package, which Java code in a named package cannot import.
- Key Rule 2 describes refs and agents with the clojure.org wording. It said that agents batch updates.

## [0.1.20] - 2026-10-25

### Added

- The `clojure-jvm` skill opens with Iron Laws (JVM-1 through JVM-6): numbered rules that prevent bugs, data loss, resource leaks, and hung processes on the JVM. The agent does not write code that breaks a law, and when existing code breaks one, it reports the law ID, the location, and the correct pattern, then asks before changing code outside the task. Key Rules that guarded against bugs or security holes moved into the laws, and the remaining Key Rules are renumbered.
- The `clojure-jvm` state reference covers `shutdown-agents` after `future`, `pmap`, and agents, and blocking calls inside core.async `go` blocks, including the `clojure.core.async.go-checking` property.
- The state reference covers exceptions in futures and agents, and names `io-thread` for blocking I/O started from a `go` block.

### Fixed

- The exception reference no longer says `with-open` requires `AutoCloseable` (it calls `.close` on any value), and its "good" example no longer catches `AssertionError`. The state reference no longer says agent threads always time out: after `send`, the JVM does not exit until `shutdown-agents` runs.

## [0.1.19] - 2026-10-25

### Added

- The `clojure-jvm` skill's `references/debugging.md` covers the Clojure CLI error format and its read, macroexpansion, compile, and execution phases, the "Full report at" file, `*e` and `Throwable->map`, classpath errors, and the common JVM exception types. The `clojure-debug` skill in clojure-skills points to it.
- The `failure-hints` hook adds a short hint when a `clj`, `clojure`, or `lein` command prints a Clojure reader, macroexpansion, unresolved-symbol, or classpath error. The hint names the next check and the full report file, and a repeated failure gets a note to change the approach. It runs under Claude Code and needs Babashka.

### Changed

- The `clojure-jvm` skill loads a short entry file that keeps the Key Rules and routes each task to a reference file: Java interop, exceptions and resource cleanup, and vars, refs, and agents. The detailed guidance moved into those files without changes, so less text loads on every matching task and the agent reads the detail for the topic at hand.

## [0.1.18] - 2026-10-25

### Changed

- The `clojure-jvm` project workflow reference describes its sample `deps.edn` aliases as the ones `/clj-fix` uses, and `CONVENTIONS.md` no longer cites `/clj-new`, because clojure-skills removed that skill.
- The Companion packages table describes biff-skills as covering Biff 2 conventions and deployment, because that package no longer scaffolds projects.

## [0.1.17] - 2026-10-24

### Added

- The Companion packages table lists [electric-skills](https://github.com/brackendev/electric-skills), the new companion package for the [Electric](https://github.com/hyperfiddle/electric) v3 full-stack framework, and `CONTRIBUTING.md` cites its Hyperfiddle lavender brand color (`#B7A4C2`).

### Changed

- The Companion packages table names the major version each framework package targets: Biff 2, Electric v3, and Fulcro 3. The skills in those packages do not apply to other major versions.
- `CONTRIBUTING.md` asks for a changelog entry and a version increment for every change to a published file, including documentation-only changes, which matches the release history. It asked for entries only for user-facing changes.
- The workflows reference's `deps.edn` example matches the layout `/clj-new` writes, including the `:dry4clj` alias that `/clj-fix` uses.
- The `add-lib` example in the `clojure-jvm` project workflows reference pins `org.clojure/data.json` 2.5.2, the current release.

### Fixed

- The Companion packages introduction counts seven packages, matching the table. It said five while the table listed six.
- The `CONTRIBUTING.md` Layout table names the local `AGENTS.md`, `GEMINI.md`, and `TODO.md` files. It named `CLAUDE.md`, which the package does not use.

## [0.1.16] - 2026-10-24

### Changed

- The `clojure-jvm` project-workflows reference points to the Reflection warnings entry in `SKILL.md` instead of repeating it. That entry says to set `*warn-on-reflection*` at the top of each namespace during development.

## [0.1.15] - 2026-10-24

### Removed

- OpenCode 1 is no longer supported. The repository no longer keeps a copy of each `SKILL.md` under `.opencode/skills/` or a per-skill allow list in `opencode.jsonc`. `.opencode/skills` is now a symlink to `.apm/skills/` instead, so an OpenCode 2 session started anywhere in the repository loads the canonical skills together with their supporting files. The `.opencode/package.json` file, which pinned the OpenCode 1 plugin package, is removed as well. APM deploys the same files as before.

### Changed

- The `CONTRIBUTING.md` acceptance steps list the skills OpenCode discovers with `opencode api GET "/api/skill?location[directory]=$PWD"`, because OpenCode 2 has no `--pure` flag or `debug skill` command.

## [0.1.14] - 2026-10-24

### Fixed

- The `CONTRIBUTING.md` acceptance steps install with `apm install --target all` in the clean temporary project. On APM 0.34.0, a bare `apm install` in that project stops with "Multiple harnesses detected" because the steps create several runtime folders first. `apm update` reuses the targets that the install records in the project's `apm.yml`.

## [0.1.13] - 2026-10-17

### Fixed

- `CONTRIBUTING.md` no longer says that omitting `user-invocable` makes a skill model-only. Claude Code defaults `user-invocable` to `true`, so a skill that omits the field also appears as a slash command. A model-only skill sets `user-invocable: false`.

## [0.1.12] - 2026-10-04

### Added

- The `clojure-jvm` skill now covers the Java interop features added in Clojure 1.12. These are qualified methods (`Class/method`, `Class/.method`, `Class/new`) used as values, `:param-tags` (`^[double]`) to resolve overloads without reflection, and array class symbols such as `String/1`. They also include passing Clojure functions where Java expects a functional interface, `IDeref` values as `Supplier`, and the `stream-into!` family for consuming Java streams.
- The `clojure-jvm` skill now recommends `clojure.java.process` over `clojure.java.shell` for running external commands.
- The `clojure-jvm` skill's Gotchas section now says to refer to static fields without parentheses (`System/out`, not `(System/out)`), which a future Clojure release will treat as an invocation.
- The project workflow reference now describes `add-lib`, `add-libs`, and `sync-deps` for loading libraries into a running REPL.

### Changed

- The project workflow reference's `deps.edn` example now uses Clojure 1.12.6, tools.build v0.10.14, and cljfmt 0.16.6.
- The project workflow reference no longer tells the agent to restart its session after starting an nREPL. It now says to connect to the port written to `.nrepl-port`, and to ask the user when an MCP server must be restarted.

## [0.1.11] - 2026-09-26

### Fixed

- The `clojure-jvm` skill now describes `with-redefs` correctly: it replaces the root values of vars, so the change is visible in every thread, not only the current one.
- The `clojure-jvm` skill now gives an accurate reason for preferring `alter-var-root` over redefining a var with `def`. Redefinition replaces the var's metadata (docstring, `:private`), and code holding the var still sees the new value.
- The `clojure-jvm` skill now gives an accurate reason for preferring `->Foo` record factories over the `(Foo. ...)` interop form. The factory is a plain function reached through `:require` and does no extra preprocessing, while the interop form needs an `:import` of the generated class.

## [0.1.10] - 2026-09-16

### Changed

- The README's runtime sentence now names Grok Build after Kiro. APM 0.28.0 added `grok-build` as a canonical target that `apm install --target all` includes and that APM auto-detects from a `.grok/` directory, so the default target set now has nine runtimes. Grok Build keeps a target-native skill directory (`.grok/skills/` at project scope, `~/.grok/skills/` at user scope), like Claude Code and Kiro, rather than the shared `.agents/skills/` directory.

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
