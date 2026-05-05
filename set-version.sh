#!/usr/bin/env bash
# set-version.sh
# Updates all version references in RSSOwlnix to the supplied version.
# Requires nothing beyond a standard bash shell and sed.
#
# Usage:
#   ./set-version.sh 2.10.1
#   ./set-version.sh 2.11.0 beta     # adds -beta suffix to user-visible strings only

VERSION="${1:-}"
LABEL="${2:-}"

if [ -z "$VERSION" ]; then
  echo "Usage: $0 <version> [label]"
  echo "  e.g. $0 2.10.1"
  echo "  e.g. $0 2.11.0 beta"
  exit 1
fi

case "$VERSION" in
  [0-9]*.[0-9]*.[0-9]*) ;;
  *)
    echo "Error: version must be in x.y.z format (e.g. 2.10.1)"
    exit 1
    ;;
esac

if [ -n "$LABEL" ]; then
  DISPLAY_VERSION="${VERSION}-${LABEL}"
else
  DISPLAY_VERSION="${VERSION}"
fi

BUILD_DATE=$(date +%Y-%m-%d)
ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "Setting version : $VERSION"
echo "Display version : $DISPLAY_VERSION"
echo "Build date      : $BUILD_DATE"
echo ""

# MANIFEST.MF files
echo "[1/5] Updating MANIFEST.MF files..."
for f in \
  "$ROOT/org.rssowl.core/META-INF/MANIFEST.MF" \
  "$ROOT/org.rssowl.ui/META-INF/MANIFEST.MF" \
  "$ROOT/org.rssowl.core.tests/META-INF/MANIFEST.MF" \
  "$ROOT/org.rssowl.lib.httpclient/META-INF/MANIFEST.MF"
do
  sed -i "s/Bundle-Version: [0-9]*\.[0-9]*\.[0-9]*\.qualifier/Bundle-Version: ${VERSION}.qualifier/" "$f"
  echo "  Updated $f"
done

# feature.xml and category.xml files
echo "[2/5] Updating feature.xml files..."
for f in \
  "$ROOT/org.rssowl.feature/feature.xml" \
  "$ROOT/org.rssowl.feature.eclipse/feature.xml" \
  "$ROOT/org.rssowl.feature.dependencies/feature.xml" \
  "$ROOT/org.rssowl.feature.dependencies.updater/feature.xml" \
  "$ROOT/org.rssowl.feature.tests/feature.xml" \
  "$ROOT/releng/update/category.xml"
do
  sed -i "s/[0-9]*\.[0-9]*\.[0-9]*\.qualifier/${VERSION}.qualifier/g" "$f"
  echo "  Updated $f"
done

# product files
echo "[3/5] Updating product files..."
for f in \
  "$ROOT/releng/product/rssowlnix.product" \
  "$ROOT/releng/product_manual_export/rssowlnix.product"
do
  sed -i "s/version=\"[0-9]*\.[0-9]*\.[0-9]*\"/version=\"${VERSION}\"/" "$f"
  sed -i "s/Version: [0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*[-a-zA-Z0-9]*/Version: ${DISPLAY_VERSION}/" "$f"
  sed -i "s/Build Id: [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/Build Id: ${BUILD_DATE}/" "$f"
  echo "  Updated $f"
done

# config.ini
echo "[4/6] Updating config.ini..."
sed -i "s/rssowl\.buildId=[0-9]*\.[0-9]*\.[0-9]*/rssowl.buildId=${VERSION}/" \
  "$ROOT/org.rssowl.ui/config.ini"
echo "  Updated org.rssowl.ui/config.ini"

# Owl.java splash screen version constant
echo "[5/6] Updating Owl.java..."
sed -i "s/SPLASH_VERSION = \"[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*[-a-zA-Z0-9]*\"/SPLASH_VERSION = \"${DISPLAY_VERSION}\"/" \
  "$ROOT/org.rssowl.core/src/org/rssowl/core/Owl.java"
echo "  Updated org.rssowl.core/src/org/rssowl/core/Owl.java"

# plugin.xml About dialog
echo "[6/6] Updating plugin.xml..."
sed -i "s/Version: [0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*[-a-zA-Z0-9]*/Version: ${DISPLAY_VERSION}/g" \
  "$ROOT/org.rssowl.ui/plugin.xml"
sed -i "s/Build Id: [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/Build Id: ${BUILD_DATE}/g" \
  "$ROOT/org.rssowl.ui/plugin.xml"
echo "  Updated org.rssowl.ui/plugin.xml"

echo ""
echo "Done. All files updated to ${DISPLAY_VERSION} (build: ${BUILD_DATE})"
echo ""
echo "Suggested next steps:"
echo "  git add -A"
echo "  git commit -m \"chore: bump version to ${DISPLAY_VERSION}\""
echo "  git tag -a v${DISPLAY_VERSION} -m \"Release v${DISPLAY_VERSION}\""
echo "  git push origin main --follow-tags"

