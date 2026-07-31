#!/usr/bin/env bash
# prune-dead-translation-keys.sh
#
# Removes key=value lines from translations/**/messages_*.properties that
# have no matching key anywhere in the current English source
# (org.rssowl.core, org.rssowl.ui). These are almost always leftovers from
# removed features (e.g. the old Google Reader integration, which no
# longer exists in this fork) rather than typos — run this once after
# bundling upstream translations, then rely on CI (translations-check.yml)
# to catch anything new going forward.
#
# Prints a summary of what was removed per file. Safe to re-run; it's a
# no-op once a file is clean.

set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

total_removed=0

while IFS= read -r -d '' tf; do
  rel="$(dirname "$tf")"
  rel="${rel#${REPO_ROOT}/}"
  pkg_path="${rel#translations/*/org.rssowl.*.nls.*/}"
  en_file=""
  for host in org.rssowl.core org.rssowl.ui; do
    candidate="${REPO_ROOT}/${host}/src/${pkg_path}/messages.properties"
    [[ -f "$candidate" ]] && en_file="$candidate"
  done
  if [[ -z "$en_file" ]]; then
    continue
  fi

  en_keys="$(grep -aE '^[A-Za-z0-9_.]+[[:space:]]*=' "$en_file" | sed -E 's/^([A-Za-z0-9_.]+).*/\1/')"

  removed_this_file=0
  tmp="$(mktemp)"
  # `|| [[ -n "$line" ]]` is required: several upstream files have no
  # trailing newline, and a plain `while read` silently drops that last
  # line (read exits non-zero with no newline, ending the loop before the
  # body runs) — that dropped a genuine, non-dead key on the first pass.
  while IFS= read -r line || [[ -n "$line" ]]; do
    key="$(sed -E 's/^([A-Za-z0-9_.]+)[[:space:]]*=.*/\1/' <<< "$line")"
    if [[ "$line" =~ ^[A-Za-z0-9_.]+[[:space:]]*= ]] && ! grep -qxF "$key" <<< "$en_keys"; then
      removed_this_file=$((removed_this_file + 1))
      continue
    fi
    echo "$line" >> "$tmp"
  done < "$tf"

  if (( removed_this_file > 0 )); then
    mv "$tmp" "$tf"
    echo "$tf: removed ${removed_this_file} dead key(s)"
    total_removed=$((total_removed + removed_this_file))
  else
    rm -f "$tmp"
  fi
done < <(find "${REPO_ROOT}/translations" -name 'messages_*.properties' -print0)

echo ""
echo "Total dead keys removed: ${total_removed}"
