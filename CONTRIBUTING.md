# Contributing

For people working on the plugin source. End-user install instructions live in [README.md](README.md).

## Layout

| Path | Purpose |
|------|---------|
| `apm.yml` | Package manifest (`type: skill`, no MCP dependencies). |
| `.apm/skills/<name>/` | Canonical skill source. APM deploys from here. Each directory holds `SKILL.md` plus optional `agents/openai.yaml`, `references/`, `scripts/`, `examples/`. |
| `.opencode/skills/<name>/SKILL.md` | Byte-identical mirror of the canonical `SKILL.md`. Enables local OpenCode validation (`opencode --pure debug skill`). Supporting files are not mirrored. |
| `opencode.jsonc`, `.opencode/package.json` | Local OpenCode configuration. |
| `README.md` | End-user documentation. |
| `CHANGELOG.md` | User-facing changes per version. |
| `CLAUDE.md`, `TODO.md` | Local working notes. Gitignored globally; never committed. |

## APM lockfile rule

Do not add `.claude-plugin/`, `.codex-plugin/`, `.agents/plugins/marketplace.json`, or a root `plugin.json`. Any of those change the APM lockfile classification from `apm_package` to `marketplace_plugin` and suppress skill deployment to every runtime.

## Adding or modifying a skill

1. Edit `.apm/skills/<name>/SKILL.md`.
2. Mirror the change to `.opencode/skills/<name>/SKILL.md` (byte-identical).
3. Update `README.md` if the change is user-facing.
4. Add a `CHANGELOG.md` entry under `[Unreleased]` for user-facing changes.
5. Increment the `version` field in `apm.yml`.

Verify the mirror is in sync:

```bash
for f in .apm/skills/*/SKILL.md; do
  diff "$f" ".opencode/skills/$(basename "$(dirname "$f")")/SKILL.md" || echo "DIFF: $f"
done
```

## Validation

Configuration files:

```bash
python3 -m json.tool opencode.jsonc >/dev/null
python3 -m json.tool .opencode/package.json >/dev/null
python3 -c "import yaml, pathlib; yaml.safe_load(pathlib.Path('apm.yml').read_text())"
python3 -c "import yaml, pathlib; [yaml.safe_load(p.read_text()) for p in pathlib.Path('.apm/skills').glob('*/agents/openai.yaml')]"
```

Runtime install (requires `apm` and the runtime CLIs you want to verify: `claude`, `codex`, `copilot`, `gemini`, `opencode`):

- Run `apm install`, `apm update`, and `apm uninstall` in a clean temporary project. Pre-create the runtime roots: `.agents/`, `.claude/`, `.cursor/`, `.opencode/`, `.gemini/`, `.github/`, `.windsurf/`.
- Exercise user scope with `apm install brackendev/clojure-jvm-skills -g [--target ...]` and `apm uninstall brackendev/clojure-jvm-skills -g`. A local filesystem path (`apm install /absolute/path -g`) is also accepted.
- Confirm OpenCode deployment with `opencode --pure debug skill`, which lists deployed skills and their source paths.

## Skill conventions

| Setting | When to use |
|---------|-------------|
| `user-invocable: true`, `disable-model-invocation: true` | User-only slash command. |
| `user-invocable: false` (or omitted) | Model-invoked from conversation context (for example `clojure-jvm`). |

Every skill carries `agents/openai.yaml` whose `policy.allow_implicit_invocation` matches the table above (`true` for model-invoked, `false` for user-only). Skills in this package use the Java orange brand color, `#E76F00`, so the runtimes can distinguish JVM-specific guidance from the host-neutral `clojure` baseline (Clojure logo blue, `#5881D8`), the ClojureScript-specific `clojurescript` skill (JavaScript yellow, `#F7DF1E`), the Biff framework skill (Biff indigo, `#4338CA`), the Fulcro framework skill (Fulcro teal, `#009E9E`), and the ClojureDart skill (Flutter blue, `#02569B`).
