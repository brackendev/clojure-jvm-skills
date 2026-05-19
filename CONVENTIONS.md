# Skill Conventions

This document defines the argument grammar, scope vocabulary, and mutation defaults that every user-invocable skill in `clojure-jvm-skills` follows. A reader who learns one skill should be able to predict every other skill. New skills follow this document.

These conventions apply to user-invocable skills (`user-invocable: true` in frontmatter). Model-invocable reference skills with no argument surface (currently the only skill in this plugin, `clojure-jvm`) carry no argument grammar and are exempt from rules 1 and 2.

This plugin ships no user-invocable skill today. The rules below define the contract that any future user-invocable JVM Clojure skill will honor, and the contract that a reader who learned the standard from a companion package ([clojure-skills](https://github.com/brackendev/clojure-skills), [clojurescript-skills](https://github.com/brackendev/clojurescript-skills), [clojuredart-skills](https://github.com/brackendev/clojuredart-skills), [biff-skills](https://github.com/brackendev/biff-skills), [fulcro-skills](https://github.com/brackendev/fulcro-skills)) can expect to apply here.

## The three rules

### Rule 1: argument grammar

User-invocable skills accept natural-language keywords and bare paths. The single sanctioned flag is `--report`. No other `--name` flags exist.

Skill-specific modifiers are bare phrases, not flags. Step keywords (for example, `lint`, `format`, `test`, `dry` on a hypothetical pipeline skill) and scope keywords (`all`) are documented in each skill's `## Arguments` table.

Exemptions are listed in the [Exemptions](#exemptions) section with a reason. The standard exemption pattern is a scaffolding or prompt-template skill that needs a required positional argument because no useful default exists.

### Rule 2: scope vocabulary

Skills that operate on files, diffs, pull requests, or commit messages share these core rows in their `## Arguments` table:

| Input             | Target                                                          |
|-------------------|-----------------------------------------------------------------|
| (no argument)     | The skill's narrowest useful default                            |
| `all`             | Widen the selected scope to its maximum                         |
| `<path>` `<glob>` | Operate on those files or directories                           |

Each skill states what `all` resolves to in concrete terms (the whole project, the full codebase, every detected step). The keyword has one uniform meaning: widen the selected scope to the maximum. The unit varies per skill.

A skill whose narrowest useful default is already maximum scope still accepts `all` for family consistency. The table row reads "Same as (no argument); accepted for family consistency."

Opt-in rows (`#N` or PR URL, `pr`, `commit`) appear only when the skill genuinely supports them. None of the current `clojure-jvm-skills` skills operate on pull requests or commit messages.

### Rule 3: mutation is the default

Skills that can mutate the workspace apply changes when invoked. The operator passes `--report` to receive a description of what the skill would do, without modifying any files.

Only the literal token `--report` enables report-only mode. Natural-language phrases ("preview", "dry run", "rehearse") are scope input or step keywords, not mode triggers. A skill that conflates them is wrong.

Command verbs reinforce the default. Skills named `/fix-*`, `/sync-*`, `/commit`, `/prune-*`, `/rebuild-*`, `/tidy`, `/new`, and similar action verbs mutate by default. Skills named `/review-*`, `/audit-*`, `/check-*` are pure-report.

## Classification

Every user-invocable skill falls into one of two classes.

**Mutating skill** -- default behavior. May carry `--report` when preview is useful. May omit `--report` when preview is meaningless (the operator inspects `git diff` after the fact) or the action is small and reversible.

**Pure report** -- never mutates. No `--report` flag because there is nothing to invert.

A skill is classified by its actual behavior, not by its name. If the name and behavior disagree, the rename is the fix. The classification appears in the skill's own description so the operator knows what to expect.

### Skills in this plugin

| Skill          | Class             | `--report` available? | Notes |
|----------------|-------------------|------------------------|-------|
| `clojure-jvm`  | Model-invoked     | Not applicable          | No argument surface. Triggers on JVM Clojure usage in conversation context. Out of scope for rules 1 and 2. |

No user-invocable skill ships in this plugin today.

## Section structure

User-invocable skills with an argument surface use these section conventions:

- `## Arguments` -- required. Contains the canonical scope table.
- `## Scope` -- optional. Add only when detection order, fallback, or base-branch resolution exceeds what the table can express.
- `## Mutation` -- optional. Add only when mutation behavior needs clarification beyond a single table row (for example, when only one of several steps writes, or when reflection-warning analysis writes a generated report while the main path only reads source).

The section name `## Customization` is retired.

## Worked examples

This plugin has no user-invocable skills yet, so the examples below illustrate how the rules apply to JVM Clojure workflows. The named commands (`/clj-tidy`, `/clj-new`, `/clj-smells-review`) live today in the companion [clojure-skills](https://github.com/brackendev/clojure-skills) plugin and operate across `.clj`, `.cljs`, `.cljc`, and `.cljd`. If a JVM-only variant of any such workflow lands here in the future, it follows the same shape.

### Mutating pipeline skill with `--report` (model: `/clj-tidy`)

```
/clj-tidy                    # all four steps; format writes
/clj-tidy lint               # lint only (pure-read; no writes anywhere)
/clj-tidy format             # format step; writes via cljfmt fix
/clj-tidy lint test          # combined step keywords
/clj-tidy --report           # all four steps; format reads via cljfmt check
/clj-tidy format --report    # format step; no writes
/clj-tidy all                # synonym for (no argument)
```

The skill writes when the `format` step runs without `--report`. With `--report`, the format step runs `cljfmt check`, which reports diffs without writing. The other three steps (`lint`, `test`, `dry`) are pure-read regardless. The `## Mutation` section in the skill body documents this asymmetry.

### Mutating scaffolding skill, exemption from `all`/path rows (model: `/clj-new`)

```
/clj-new my-service          # creates project tree at ./my-service
/clj-new                     # prompts the operator for a project name
```

`<project-name>` is a required positional argument. The skill omits the `all` and `<path>` rows because they would not be meaningful for scaffolding. It also omits `--report` because preview is meaningless: the operator reads `SKILL.md` to see what files will be written. The exemption is listed below.

### Pure-report skill (model: `/clj-smells-review`)

```
/clj-smells-review                       # review changed files
/clj-smells-review src/api               # review files under directory
/clj-smells-review src/api/auth.clj      # review specific file
/clj-smells-review all                   # review the full codebase
```

No `--report` flag, because the skill never writes. Operators apply suggestions themselves.

## Exemptions

No skill in this plugin claims an exemption today. The standard exemption pattern, for reference, is a scaffolding skill (`/clj-new` in the companion `clojure-skills` package) that accepts a required positional `<project-name>` because no useful default exists. Such a skill omits the `all` and `<path>` scope rows and the `--report` flag for the same reason. The `## Arguments` table in its `SKILL.md` documents the positional grammar.

## Ambiguity notes

**`(no argument)` outside a git worktree.** A skill whose narrowest useful default depends on git state (for example, "review changed files") must define the fallback when no git worktree is present. The expected fallback is to ask the operator what to review rather than to widen silently to `all`.

**`commit` versus staged-and-unstaged state.** When a future skill accepts `commit` as a scope keyword, it must state whether `commit` means the most recent commit, the staged tree, or the staged-plus-unstaged working tree. The expected default is the most recent commit. None of the current skills carry this scope.

**`--report` versus natural-language synonyms.** "Preview", "dry run", "rehearse", and similar phrases are scope input or step keywords (or operator chatter), never mode triggers. Only the literal `--report` token disables writes. A skill that accepts both is wrong; the natural-language synonym must mean something else or be rejected. As a JVM-specific note: a future pipeline skill that exposes a `dry` step (for example, a dry4clj duplicate-form scan) must not conflate that step keyword with a dry-run mode.

**Step keywords versus scope keywords.** A pipeline skill that accepts step keywords (`lint`, `format`, `test`, `dry`) selects which work to run; scope keywords (`all`) widen scope. Step keywords are skill-specific and listed in the skill's own table. Scope keywords are shared and listed here. When a skill has both, the `## Arguments` table lists both with clearly distinct rows.

**Tool-level flags the skill calls internally.** A skill may invoke a tool that itself uses POSIX flags (for example, `clj-kondo --lint`, `cljfmt fix`, `cljfmt check`, `clojure -T:build`, `clojure -M:test`). Those are tool-level flags, not skill flags, and do not count against rule 1. The skill body should disambiguate when a tool flag could be mistaken for a skill flag.

**JVM-specific reflection and AOT writes.** Skills that analyze reflection warnings or AOT-compile classes may produce artifacts under `target/`, `classes/`, or `out/`. Build-artifact writes are not source mutation; the `## Mutation` section in the skill body must distinguish them from source writes. `--report` suppresses source writes; it does not necessarily suppress build artifacts unless the skill body says so.

## Author checklist

When adding or modifying a user-invocable skill, confirm each item before committing.

- [ ] Skill has a `## Arguments` section (or is listed under [Exemptions](#exemptions)).
- [ ] Scope rows match the canonical table; opt-in rows appear only where the skill genuinely supports them.
- [ ] If the skill mutates, the command verb signals it (`/fix-*`, `/sync-*`, `/commit`, `/prune-*`, `/rebuild-*`, `/tidy`, `/new`).
- [ ] If the skill mutates and preview is useful, `--report` is documented.
- [ ] If the skill is pure-report, the verb signals it (`/review-*`, `/audit-*`, `/check-*`) and the skill has no `--report` flag.
- [ ] No `## Customization` section.
- [ ] No `--name` flags other than `--report`. Tool-level flags the skill calls internally (for example, `cljfmt check`, `clj-kondo --lint`, `clojure -T:build`) are not skill flags and do not count.
- [ ] Frontmatter `name` matches the skill's directory name.
- [ ] OpenCode mirror under `.opencode/skills/<name>/SKILL.md` is byte-identical to the canonical source.
- [ ] `agents/openai.yaml` `default_prompt` references the current command name.
- [ ] `CHANGELOG.md` records the change under `[Unreleased]` when the change is user-facing.
