# Contributing

For people working on the plugin source. End-user install instructions live in [README.md](README.md).

## Layout

| Path | Purpose |
|------|---------|
| `apm.yml` | Package manifest (`type: skill`, no MCP dependencies). |
| `.apm/skills/<name>/` | Canonical skill source. APM deploys from here. Each directory holds `SKILL.md` plus optional `agents/openai.yaml`, `references/`, `scripts/`, `examples/`. |
| `.apm/hooks/` | Hook definitions. `failure-hints.json` and `jvm-rules.json` hold the generic copies, and `copilot-hooks.json` and `cursor-hooks.json` are empty, so Copilot CLI and Cursor receive no native copy. `scripts/` holds each hook's bash launcher and Babashka script, and `jvm-rules.md`, the condensed Iron Laws that `jvm-rules` delivers. |
| `tests/hooks/` | Hook test suites. They are repository files, not package content, and APM does not deploy them. |
| `.opencode/skills` | Symlink to `../.apm/skills`. OpenCode finds it from any directory in the repository, so an OpenCode session loads the canonical skills and their supporting files without an APM install. |
| `opencode.jsonc` | Local OpenCode configuration. |
| `README.md` | End-user documentation. |
| `CHANGELOG.md` | User-facing changes per version. |
| `CONVENTIONS.md` | Canonical argument grammar, scope vocabulary, and mutation defaults for every user-invocable skill. |
| `AGENTS.md`, `GEMINI.md`, `TODO.md` | Local agent instructions (`GEMINI.md` is a symlink to `AGENTS.md`) and working notes. Gitignored globally and never committed. |

## APM lockfile rule

Do not add `.claude-plugin/`, `.codex-plugin/`, `.agents/plugins/marketplace.json`, or a root `plugin.json`. Any of those change the APM lockfile classification from `apm_package` to `marketplace_plugin` and suppress skill deployment to every runtime.

## Adding or modifying a skill

1. Edit `.apm/skills/<name>/SKILL.md`.
2. Update `README.md` if the change is user-facing.
3. Add a `CHANGELOG.md` entry for every change to a published file, including documentation-only changes, under a `## [x.y.z] - YYYY-MM-DD` heading for the new `apm.yml` version. Leave `## [Unreleased]` empty above it.
4. Increment the `version` field in `apm.yml`.

## Adding or modifying a hook

1. Edit the hook JSON in `.apm/hooks/<name>.json`, keyed by lifecycle event (`PostToolUse`, `PostToolUseFailure`). Use the matcher `"*"` and choose the tool inside the script, because APM passes matchers through unchanged.
2. Keep `.apm/hooks/cursor-hooks.json` empty: an install fails when a hook reaches Cursor both natively and through its import of Claude Code settings, and APM has no Cursor mapping for `PostToolUseFailure`. Keep `.apm/hooks/copilot-hooks.json` empty until the hook is verified under Copilot CLI. A non-empty file there must use Copilot's flat camelCase format.
3. Write the hook as a Babashka script, `.apm/hooks/scripts/<name>.bb`, started by a bash launcher, `.apm/hooks/scripts/<name>.sh`. When `bb` is missing, the launcher prints that the hook is inactive to standard error and exits 0. Reference the launcher as `${PLUGIN_ROOT}/.apm/hooks/scripts/<name>.sh`. The `.apm/` segment is required, or APM logs "Hook script not found". Keep the launcher executable.
4. Claude Code sends a command that exits with a nonzero status to `PostToolUseFailure` with its output in `error`, and a command whose pipeline exits 0 to `PostToolUse` with its output in `tool_response.stdout` and `tool_response.stderr`. Read both.
5. Add cases to the suites in `tests/hooks/` and run them (see "Hook tests" below).
6. Update `README.md`, increment the `version` field in `apm.yml`, and add a `CHANGELOG.md` entry under a `## [x.y.z] - YYYY-MM-DD` heading for that version.

## Changing an Iron Law

The rules hook delivers `.apm/hooks/scripts/jvm-rules.md`, which condenses each Iron Law in `.apm/skills/clojure-jvm/SKILL.md` into one `- JVM-<n>: ...` line. When a law changes, update its line in the same change, keeping the law's exceptions. The coordinator's drift check compares the law IDs in both files but cannot compare their meaning.

## Validation

Configuration files:

```bash
python3 -m json.tool opencode.jsonc >/dev/null
python3 -c "import yaml, pathlib; yaml.safe_load(pathlib.Path('apm.yml').read_text())"
python3 -c "import yaml, pathlib; [yaml.safe_load(p.read_text()) for p in pathlib.Path('.apm/skills').glob('*/agents/openai.yaml')]"
```

Runtime install (requires `apm` and the runtime CLIs you want to verify: `claude`, `codex`, `copilot`, `gemini`, `opencode`):

- Run `apm install --target all`, `apm update`, and `apm uninstall` in a clean temporary project. Pre-create the runtime roots: `.agents/`, `.claude/`, `.cursor/`, `.opencode/`, `.gemini/`, `.github/`, `.windsurf/`. With several runtime roots present, APM stops with "Multiple harnesses detected" unless the install names a target. `apm update` reuses the targets that the install records in the project's `apm.yml`.
- Exercise user scope with `apm install brackendev/clojure-jvm-skills -g [--target ...]` and `apm uninstall brackendev/clojure-jvm-skills -g`. A local filesystem path (`apm install /absolute/path -g`) is also accepted.
- Confirm OpenCode deployment from the temporary project with `opencode api GET "/api/skill?location[directory]=$PWD"`, which lists the skills OpenCode discovers and their source paths. The first request for a directory can return an empty list while OpenCode loads the skills, so repeat it before reading the result.

### Hook tests

Run every hook suite with `bb tests/hooks/run.bb`. It needs `bb`, `git`, and `apm`, and a missing requirement fails the run. `failure-hints-hook.bb` runs the hook with Clojure CLI error output recorded from Clojure 1.12, and `install-check.bb` installs the package with APM at project and user scope, with `--target claude` and with `--target all`, and checks the deployed hook. `jvm-rules-hook.bb` checks when `jvm-rules` delivers the rules: at session start, on the first matching edit, after a clear or compaction, and never twice or outside a JVM Clojure project.

## Skill conventions

For the argument grammar, scope vocabulary, and mutation defaults that any user-invocable skill in this plugin will follow, see [CONVENTIONS.md](CONVENTIONS.md).

| Setting | When to use |
|---------|-------------|
| `user-invocable: true`, `disable-model-invocation: true` | User-only slash command. |
| `user-invocable: false` | Model-invoked from conversation context (for example `clojure-jvm`). |

Every skill carries `agents/openai.yaml` whose `policy.allow_implicit_invocation` matches the table above (`true` for model-invoked, `false` for user-only). Skills in this package use the Java orange brand color, `#E76F00`, so the runtimes can distinguish JVM-specific guidance from the host-neutral `clojure` baseline (Clojure logo blue, `#5881D8`), the ClojureScript-specific `clojurescript` skill (JavaScript yellow, `#F7DF1E`), the Biff framework skill (Biff indigo, `#4338CA`), the Electric framework skill (Hyperfiddle lavender, `#B7A4C2`), the Fulcro framework skill (Fulcro teal, `#009E9E`), and the ClojureDart skill (Flutter blue, `#02569B`).
