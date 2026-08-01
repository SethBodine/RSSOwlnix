#!/usr/bin/env bash
#
# find-missing-translations.sh
#
# Two modes:
#
#   1) Language mode — find keys missing from a language's translation
#      fragments compared to the current English source:
#
#        ./find-missing-translations.sh de
#
#      Searches translations/ (flat: org.rssowl.{core,ui,feature}.nls.<code>/
#      directly under translations/, no per-language grouping folder —
#      tycho-pomless 1.1.0, as pinned in .mvn/extensions.xml, only resolves
#      a pomless module's parent one directory level up with no override,
#      so translations/ has to be flat like bundles/ and features/) for
#      messages_de.properties files and reports every key present in an
#      English messages.properties file that has no corresponding key
#      there.
#
#   2) Baseline-diff mode — find keys added to English source since a
#      given git ref (e.g. the upstream 2.1.2 tag), useful for figuring
#      out what *no* translation has caught up to yet:
#
#        ./find-missing-translations.sh --since <git-ref>
#
#      Must be run from inside a git checkout that has <git-ref> available
#      (fetch it first if needed, e.g.
#        git fetch https://github.com/rssowl/RSSOwl.git 2.1.2 --tags
#      or point at whatever ref/commit your fork's history has for the
#      2.1.2 release).
#
# Both modes only look at Eclipse-style *.properties files matching
# messages*.properties, which is what this codebase uses for NLS.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TRANSLATIONS_DIR="${REPO_ROOT}/translations"

extract_keys() {
  # Prints "relative/path/messages.properties::key" for every key= line,
  # skipping comments and blank lines. Handles trailing \r from CRLF files.
  local file="$1"
  local label="$2"
  grep -nE '^[A-Za-z0-9_.]+[[:space:]]*=' "$file" 2>/dev/null \
    | sed -E 's/^[0-9]+:([A-Za-z0-9_.]+)[[:space:]]*=.*$/\1/' \
    | tr -d '\r' \
    | sed "s#^#${label}::#"
}

mode_since() {
  local ref="$1"
  echo "Comparing current English messages.properties keys against ${ref}..." >&2

  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "${tmp}"' EXIT

  # Export the tree at $ref into $tmp without touching the working copy.
  git -C "${REPO_ROOT}" archive "${ref}" | tar -x -C "${tmp}"

  local old_keys new_keys
  old_keys="$(mktemp)"
  new_keys="$(mktemp)"

  find "${tmp}" -type f -name 'messages.properties' -print0 \
    | while IFS= read -r -d '' f; do
        rel="${f#${tmp}/}"
        extract_keys "$f" "$rel"
      done | sort -u > "${old_keys}"

  find "${REPO_ROOT}" -type f -name 'messages.properties' \
       -not -path '*/target/*' -print0 \
    | while IFS= read -r -d '' f; do
        rel="${f#${REPO_ROOT}/}"
        extract_keys "$f" "$rel"
      done | sort -u > "${new_keys}"

  echo "# Keys present now but not at ${ref}:"
  comm -13 "${old_keys}" "${new_keys}" | sed 's/::/  ->  /'

  rm -f "${old_keys}" "${new_keys}"
}

mode_lang() {
  local lang="$1"

  if [[ ! -d "${TRANSLATIONS_DIR}" ]]; then
    echo "No translations/ directory found. Does TRANSLATIONS_DIR point at" >&2
    echo "the right place in this repo?" >&2
    exit 1
  fi

  if ! find "${TRANSLATIONS_DIR}" -type f -name "messages_${lang}.properties" -print -quit 2>/dev/null | grep -q .; then
    echo "No messages_${lang}.properties files found anywhere under translations/." >&2
    echo "Is '${lang}' the right properties-file suffix? (e.g. zh_CN, not zhcn)" >&2
    exit 1
  fi

  local total_missing=0

  # Every English messages.properties under org.rssowl.core and org.rssowl.ui
  while IFS= read -r -d '' en_file; do
    # Derive the matching translated file path: same package path, but
    # inside a translations/org.rssowl.*.nls.<code>/ module and with the
    # language suffix.
    rel_from_src="${en_file#*/src/}"          # e.g. org/rssowl/ui/internal/messages.properties
    pkg_dir="$(dirname "${rel_from_src}")"

    # Search the whole flat translations/ tree for the matching fragment
    # file — the messages_<lang>.properties filename itself disambiguates
    # language, so no per-language directory lookup is needed.
    translated_file="$(find "${TRANSLATIONS_DIR}" -type f \
        -path "*/${pkg_dir}/messages_${lang}.properties" 2>/dev/null | head -n1 || true)"

    en_keys="$(extract_keys "${en_file}" "${en_file}" | sed 's/^[^:]*:://')"

    if [[ -z "${translated_file}" ]]; then
      echo "## ${rel_from_src}  (entire file missing for '${lang}')"
      echo "${en_keys}" | sed 's/^/  - /'
      total_missing=$((total_missing + $(echo "${en_keys}" | grep -c .)))
      continue
    fi

    tr_keys="$(extract_keys "${translated_file}" "${translated_file}" | sed 's/^[^:]*:://')"
    missing="$(comm -23 <(echo "${en_keys}" | sort) <(echo "${tr_keys}" | sort))"

    if [[ -n "${missing}" ]]; then
      echo "## ${rel_from_src}"
      echo "   -> ${translated_file#${REPO_ROOT}/}"
      echo "${missing}" | sed 's/^/  - /'
      total_missing=$((total_missing + $(echo "${missing}" | grep -c .)))
    fi
  done < <(find "${REPO_ROOT}/org.rssowl.core" "${REPO_ROOT}/org.rssowl.ui" \
                -type f -name 'messages.properties' -print0 2>/dev/null)

  echo ""
  echo "Total missing keys for '${lang}': ${total_missing}"
}

usage() {
  echo "Usage:"
  echo "  $0 <lang-code>          # e.g. $0 de"
  echo "  $0 --since <git-ref>    # e.g. $0 --since 2.1.2"
  exit 1
}

[[ $# -ge 1 ]] || usage

if [[ "$1" == "--since" ]]; then
  [[ $# -eq 2 ]] || usage
  mode_since "$2"
else
  mode_lang "$1"
fi
