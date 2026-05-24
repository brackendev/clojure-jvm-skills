# clojure-jvm-skills

JVM-specific Clojure skills packaged as an [APM](https://github.com/microsoft/apm) plugin. One install deploys the package to every runtime APM supports: Claude Code, Codex, OpenCode, Cursor, Copilot, Gemini, and Windsurf.

Skills follow the [Agent Skills](https://agentskills.io) open standard. The `clojure-jvm` skill auto-triggers from conversation context when JVM-specific Clojure usage appears (Java interop, refs / agents / STM, `with-open`, JVM-typed exceptions, the Clojure CLI, `tools.build`, `clj-kondo`, `cljfmt`, `test-runner`, nREPL, `clojure-mcp`).

## Companion packages

Five sibling APM packages. This package layers on top of [clojure-skills](https://github.com/brackendev/clojure-skills) and covers only what is JVM-specific. Install `clojure-skills` alongside it for JVM Clojure work; install `biff-skills` as well for Biff projects; install `fulcro-skills` as well for Fulcro projects; install `clojuredart-skills` instead for ClojureDart / Flutter work.

| Package | Focus | Layers on |
|---------|-------|-----------|
| [clojure-skills](https://github.com/brackendev/clojure-skills) | Host-neutral [Clojure](https://clojure.org/) family baseline: style, naming, threading, collections, atoms, dispatch, formatting, namespaces, and testing. Triggers on `.clj`, `.cljs`, `.cljc`, `.cljd`. | — |
| [clojure-jvm-skills](https://github.com/brackendev/clojure-jvm-skills) (this package) | [Clojure](https://clojure.org/) on the JVM: Java interop, refs / agents / STM, `with-open`, JVM-typed exceptions, `alter-var-root`, and the Clojure CLI / `tools.build` / `clj-kondo` / `cljfmt` / `test-runner` / nREPL workflow. | `clojure-skills` |
| [clojurescript-skills](https://github.com/brackendev/clojurescript-skills) | [ClojureScript](https://clojurescript.org/) style and tooling: JavaScript interop, externs inference, macro stage separation, `catch :default`, JS-flavored numbers and truthiness, and the `cljs.main` workflow. Triggers on `.cljs`, `.cljc` compiled to JS, `shadow-cljs.edn`, `figwheel-main.edn`. | `clojure-skills` |
| [biff-skills](https://github.com/brackendev/biff-skills) | [Biff](https://biffweb.com/) web framework on the JVM: scaffolding, conventions, deployment. | `clojure-skills` + `clojure-jvm-skills` |
| [fulcro-skills](https://github.com/brackendev/fulcro-skills) | [Fulcro](https://github.com/fulcrologic/fulcro) full-stack framework: `defsc` components, idents and the normalized client database, mutations, `df/load!`, dynamic routing, forms, UI state machines, Fulcro Inspect, and the Pathom 3 server. Triggers on `com.fulcrologic.fulcro.*`, `com.fulcrologic.rad.*`, `com.wsscode.pathom3.*`, `defsc`, `defmutation`, `defrouter`, `df/load!`, and ident vectors. | `clojure-skills` + `clojurescript-skills` + `clojure-jvm-skills` |
| [clojuredart-skills](https://github.com/brackendev/clojuredart-skills) | [ClojureDart](https://github.com/Tensegritics/ClojureDart) on [Flutter](https://flutter.dev/): Dart interop, type hints, `cljd.flutter` directives, async, FFI, REPL, and the Flutter project workflow. Triggers on `.cljd`, `cljd.flutter`. | `clojure-skills` |

## Install

Install [APM](https://github.com/microsoft/apm) first if you don't already have it. Then, in a project:

```bash
apm install brackendev/clojure-jvm-skills --target all
apm install brackendev/clojure-skills --target all
```

Globally for your user account:

```bash
apm install brackendev/clojure-jvm-skills -g --target all
apm install brackendev/clojure-skills -g --target all
```

Update later with `apm update [-g]`. Remove with `apm uninstall brackendev/clojure-jvm-skills [-g]`. A local filesystem path can replace the shorthand at either scope.

## Requirements

- [Clojure CLI](https://clojure.org/guides/install_clojure) and Java 17 or higher.
- [clojure-skills](https://github.com/brackendev/clojure-skills) installed alongside, for the host-neutral baseline.

## Skills

### Auto-triggered

| Skill | Triggers |
|-------|----------|
| **clojure-jvm** | `.clj` files, `deps.edn` projects, `project.clj`, `build.clj`, Java interop (`java.*`, `import`, `Throwable`, `Exception`, `AutoCloseable`), `with-open`, refs / agents / STM (`dosync`, `alter`, `ref`, `agent`, `send`, `send-off`, `io!`), `alter-var-root`, `gen-class`, `proxy`, `clojure.tools.logging`, `clojure.java.io`, `clojure.math`, `clojure.core.async` on the JVM, the Clojure CLI (`clj`, `clojure`), `tools.build`, `clj-kondo`, `cljfmt`, Cognitect `test-runner`, nREPL, or `clojure-mcp`. Covers Java interop, JVM-typed exception handling, JVM resource cleanup, the reference primitives, `alter-var-root` workflow, and the JVM project layout and tooling. Defers to the [clojure](https://github.com/brackendev/clojure-skills) skill for host-neutral style. |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT. See [LICENSE](LICENSE).
