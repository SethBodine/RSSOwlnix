#!/usr/bin/env bash
# generate-nls-status.sh
#
# Walks translations/<lang>/org.rssowl.{core,ui}.nls.<code>/ against the
# current English org.rssowl.core / org.rssowl.ui messages.properties
# files and computes per-language coverage.
#
# Usage:
#   ./generate-nls-status.sh                 # writes status.json + status.md to cwd
#   ./generate-nls-status.sh --fail-under 90  # exit 1 if any *fully-supported*
#                                              # language (see SUPPORTED below)
#                                              # drops below 90% coverage
#
# Output:
#   status.json  - machine-readable, meant to be published alongside the
#                   NLS p2 site (e.g. update/nls/status.json) so coverage
#                   is checkable without cloning the repo.
#   status.md    - human-readable table, used for PR comments / job summaries.
#
# This script has no dependency on the earlier find-missing-translations.sh
# (kept separate: that one is for a contributor working on one language
# locally, this one is for CI reporting on all languages at once).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TRANSLATIONS_DIR="${REPO_ROOT}/translations"
FAIL_UNDER=""

# Languages considered "fully supported" — i.e. the ones the project
# promises to keep at high coverage. Add a language here once a maintainer
# or regular contributor has committed to keeping it current. Everything
# else in translations/ is still built and shipped, just not gated in CI.
SUPPORTED=(de fr es)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fail-under) FAIL_UNDER="$2"; shift 2 ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

extract_keys() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  grep -aE '^[A-Za-z0-9_.]+[[:space:]]*=' "$file" \
    | sed -E 's/^([A-Za-z0-9_.]+)[[:space:]]*=.*$/\1/' \
    | tr -d '\r'
}

is_supported() {
  local lang="$1"
  for s in "${SUPPORTED[@]}"; do [[ "$s" == "$lang" ]] && return 0; done
  return 1
}

# total English key count (denominator, same for every language)
total_en_keys=0
declare -A en_keys_by_relpath
while IFS= read -r -d '' en_file; do
  rel="${en_file#${REPO_ROOT}/}"
  keys="$(extract_keys "$en_file")"
  count="$(echo "$keys" | grep -c . || true)"
  total_en_keys=$((total_en_keys + count))
done < <(find "${REPO_ROOT}/org.rssowl.core" "${REPO_ROOT}/org.rssowl.ui" \
              -type f -name 'messages.properties' -print0 2>/dev/null)

json_entries=()
md_rows=()
worst_supported_pct=100
any_fail=0

if [[ -d "${TRANSLATIONS_DIR}" ]]; then
  for lang_dir in "${TRANSLATIONS_DIR}"/*/; do
    lang="$(basename "${lang_dir}")"
    [[ "$lang" == "pom.xml" ]] && continue

    missing=0
    while IFS= read -r -d '' en_file; do
      rel_from_src="${en_file#*/src/}"
      pkg_dir="$(dirname "${rel_from_src}")"
      suffix="${lang}"
      translated_file="$(find "${lang_dir}" -type f \
          -path "*/${pkg_dir}/messages_${suffix}.properties" 2>/dev/null | head -n1 || true)"
      en_keys="$(extract_keys "${en_file}" | sort -u)"
      tr_keys="$(extract_keys "${translated_file:-/dev/null}" | sort -u)"
      m="$(comm -23 <(echo "${en_keys}") <(echo "${tr_keys}") | grep -c . || true)"
      missing=$((missing + m))
    done < <(find "${REPO_ROOT}/org.rssowl.core" "${REPO_ROOT}/org.rssowl.ui" \
                  -type f -name 'messages.properties' -print0 2>/dev/null)

    translated=$((total_en_keys - missing))
    pct=$(( total_en_keys > 0 ? translated * 10000 / total_en_keys : 0 ))  # 2 decimal places, integer math
    pct_whole=$((pct / 100))
    pct_frac=$((pct % 100))

    supported="false"
    if is_supported "$lang"; then
      supported="true"
      if (( pct_whole < worst_supported_pct )); then worst_supported_pct=$pct_whole; fi
    fi

    json_entries+=("    {\"language\": \"${lang}\", \"translated\": ${translated}, \"total\": ${total_en_keys}, \"missing\": ${missing}, \"coverage_pct\": ${pct_whole}.${pct_frac}, \"fully_supported\": ${supported}}")
    md_rows+=("| ${lang} | ${translated}/${total_en_keys} | ${pct_whole}.${pct_frac}% | ${supported} |")
  done
fi

{
  echo "{"
  echo "  \"total_keys\": ${total_en_keys},"
  echo "  \"generated_at\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
  echo "  \"languages\": ["
  ( IFS=,; echo "${json_entries[*]}" )
  echo "  ]"
  echo "}"
} > status.json

{
  echo "### Translation coverage"
  echo ""
  echo "Total keys: ${total_en_keys}"
  echo ""
  echo "| Language | Translated | Coverage | Fully supported |"
  echo "|---|---|---|---|"
  printf '%s\n' "${md_rows[@]}"
} > status.md

cat status.md

if [[ -n "${FAIL_UNDER}" ]] && (( worst_supported_pct < FAIL_UNDER )); then
  echo ""
  echo "FAIL: a fully-supported language dropped below ${FAIL_UNDER}% coverage (worst: ${worst_supported_pct}%)" >&2
  exit 1
fi
