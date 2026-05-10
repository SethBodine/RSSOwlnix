#!/usr/bin/env bash
# generate-updates-rss.sh
#
# Parses CHANGELOG.md and writes updates.rss (RSS 2.0) into the repo root.
#
# Each versioned heading  ## [x.y.z] - YYYY-MM-DD  becomes one <item>.
# [Unreleased] is included when it contains content (non-empty lines),
# using today's date and the current git commit SHA as its guid so it
# updates in the feed on every push without creating duplicate entries.
# Undated baseline entries (e.g. ## [2.10.0] - upstream baseline) are skipped.
#
# guid strategy:
#   - Tagged release  (x.y.z with no pre-release suffix like -beta/-rc):
#       guid = GitHub releases tag URL  (perma-link, stable)
#   - Pre-release     (x.y.z-beta, x.y.z-rc):
#       guid = GitHub releases tag URL  (perma-link, stable)
#   - Unreleased:
#       guid = repo URL + /unreleased/ + git-SHA  (changes each push, intentional)
#
# This means readers see a new Unreleased item on every push that touches
# CHANGELOG.md, but versioned items never re-appear once read.
#
# Usage: bash generate-updates-rss.sh [CHANGELOG.md] [updates.rss]
# Defaults: CHANGELOG.md  updates.rss  (run from repo root)
#
# Dependencies: bash, sed, date, git  (all standard on Linux / macOS / Git Bash)

set -euo pipefail

CHANGELOG="${1:-CHANGELOG.md}"
OUTPUT="${2:-updates.rss}"

FEED_TITLE="RSSOwlnix Updates"
FEED_LINK="https://sethbodine.github.io/RSSOwlnix"
FEED_DESC="Release notes and in-progress changes for RSSOwlnix."
REPO_BASE="https://github.com/SethBodine/RSSOwlnix"
REPO_RELEASES="$REPO_BASE/releases"

if [[ ! -f "$CHANGELOG" ]]; then
  echo "ERROR: $CHANGELOG not found." >&2
  exit 1
fi

# Current git SHA for the Unreleased guid (falls back gracefully outside a repo)
GIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
TODAY=$(date -u +"%Y-%m-%d")

# ---------------------------------------------------------------------------
# xml_escape    escape the five XML special characters in a string
# ---------------------------------------------------------------------------
xml_escape() {
  local s="$1"
  s="${s//&/&amp;}"
  s="${s//</&lt;}"
  s="${s//>/&gt;}"
  s="${s//\"/&quot;}"
  s="${s//\'/&apos;}"
  printf '%s' "$s"
}

# ---------------------------------------------------------------------------
# Parse the changelog into parallel arrays:
#   versions[]  e.g. "2.10.2-beta"  or  "Unreleased"
#   dates[]     e.g. "2026-05-08"   or  today's date for Unreleased
#   bodies[]    raw markdown content between this heading and the next
# ---------------------------------------------------------------------------
declare -a versions dates bodies

current_version=""
current_date=""
current_body=""
in_section=false

flush_section() {
  # Only keep sections that have real content (not just whitespace/placeholders)
  local content_check
  content_check=$(printf '%s' "$current_body" | sed '/^[[:space:]]*$/d' | grep -v "^Nothing currently pending" || true)
  if [[ -n "$current_version" && -n "$content_check" ]]; then
    versions+=("$current_version")
    dates+=("$current_date")
    bodies+=("$current_body")
  fi
}

while IFS= read -r line || [[ -n "$line" ]]; do

  # Match dated release:  ## [2.10.2-beta] - 2026-05-08
  if [[ "$line" =~ ^##[[:space:]]\[([^]]+)\][[:space:]]-[[:space:]]([0-9]{4}-[0-9]{2}-[0-9]{2}) ]]; then
    flush_section
    current_version="${BASH_REMATCH[1]}"
    current_date="${BASH_REMATCH[2]}"
    current_body=""
    in_section=true
    continue
  fi

  # Match [Unreleased]  (no date)
  if [[ "$line" =~ ^##[[:space:]]\[Unreleased\] ]]; then
    flush_section
    current_version="Unreleased"
    current_date="$TODAY"
    current_body=""
    in_section=true
    continue
  fi

  # Any other undated ## heading (e.g. upstream baseline)  skip its content
  if [[ "$line" =~ ^##[[:space:]] ]]; then
    flush_section
    in_section=false
    current_version=""
    current_date=""
    current_body=""
    continue
  fi

  if [[ "$in_section" == true ]]; then
    current_body+="$line"$'\n'
  fi

done < "$CHANGELOG"

flush_section  # handle the final section

if [[ ${#versions[@]} -eq 0 ]]; then
  echo "WARNING: No sections with content found in $CHANGELOG  writing empty feed." >&2
fi

# ---------------------------------------------------------------------------
# Build the RSS
# ---------------------------------------------------------------------------
BUILD_DATE=$(date -u +"%a, %d %b %Y %H:%M:%S GMT")

{
  cat <<HEADER
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>$(xml_escape "$FEED_TITLE")</title>
    <link>$FEED_LINK</link>
    <description>$(xml_escape "$FEED_DESC")</description>
    <language>en</language>
    <lastBuildDate>$BUILD_DATE</lastBuildDate>
    <atom:link href="$FEED_LINK/updates.rss" rel="self" type="application/rss+xml"/>
HEADER

  for i in "${!versions[@]}"; do
    version="${versions[$i]}"
    date_raw="${dates[$i]}"
    body="${bodies[$i]}"

    # Convert YYYY-MM-DD to RFC 2822 for pubDate
    pub_date=$(date -u -d "$date_raw" +"%a, %d %b %Y 00:00:00 GMT" 2>/dev/null \
               || date -u -j -f "%Y-%m-%d" "$date_raw" +"%a, %d %b %Y 00:00:00 GMT")

    # Strip horizontal rules and trailing whitespace; condense consecutive blank lines
    clean_body=$(printf '%s' "$body" \
      | sed '/^---*$/d' \
      | sed 's/[[:space:]]*$//' \
      | cat -s)

    # Determine item link and guid
    if [[ "$version" == "Unreleased" ]]; then
      item_title="RSSOwlnix  Unreleased changes (${date_raw})"
      item_link="$REPO_BASE/blob/main/CHANGELOG.md"
      # guid includes the SHA so each push produces a distinct item in the feed
      item_guid="$REPO_BASE/unreleased/$GIT_SHA"
      guid_permalink="false"
    else
      item_title="RSSOwlnix $version"
      item_link="$REPO_RELEASES/tag/v$version"
      # Version + date is stable  readers won't re-show already-read items
      item_guid="$item_link"
      guid_permalink="true"
    fi

    cat <<ITEM
    <item>
      <title>$(xml_escape "$item_title")</title>
      <link>$item_link</link>
      <guid isPermaLink="$guid_permalink">$item_guid</guid>
      <pubDate>$pub_date</pubDate>
      <description><![CDATA[$clean_body]]></description>
    </item>
ITEM
  done

  echo "  </channel>"
  echo "</rss>"

} > "$OUTPUT"

echo "Written $OUTPUT  (${#versions[@]} section(s): ${versions[*]})"

