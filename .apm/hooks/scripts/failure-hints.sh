#!/bin/bash
# failure-hints.sh - Starts failure-hints.bb with Babashka, passing the hook
# input through on standard input. APM registers this launcher as the hook
# command.
#
# The hook runs after every tool call, so the launcher first checks the input
# for a Clojure CLI or Leiningen error and exits at once when there is none.
# That keeps the common case from starting Babashka.
#
# Without Babashka the hook cannot run. The launcher then says so on standard
# error and exits 0, so the session continues without the hints.
input=$(cat)
case "$input" in
  *"Syntax error"* | *"Could not locate"* | *"Unable to resolve symbol"*) ;;
  *) exit 0 ;;
esac
if ! command -v bb >/dev/null 2>&1; then
  echo "failure-hints is inactive: Babashka (bb) is not installed. Install it from https://babashka.org to enable this hook." >&2
  exit 0
fi
printf '%s' "$input" | exec bb "$(dirname "${BASH_SOURCE[0]}")/failure-hints.bb" "$@"
