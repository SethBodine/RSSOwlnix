<#
.SYNOPSIS
  set-version.ps1
  Updates all version references in RSSOwlnix to the supplied version.
  PowerShell port of set-version.sh - same files, same replacements, same output.

.DESCRIPTION
  Requires nothing beyond PowerShell 5.1+ (Windows PowerShell or PowerShell 7+).
  No external tools (no sed/bash needed).

.EXAMPLE
  ./set-version.ps1 2.10.1

.EXAMPLE
  ./set-version.ps1 2.11.0 beta
  # adds -beta suffix to user-visible strings only
#>

param(
  [Parameter(Position = 0)]
  [string]$Version,

  [Parameter(Position = 1)]
  [string]$Label
)

$ScriptName = $MyInvocation.MyCommand.Name

if ([string]::IsNullOrEmpty($Version)) {
  Write-Host "Usage: $ScriptName <version> [label]"
  Write-Host "  e.g. $ScriptName 2.10.1"
  Write-Host "  e.g. $ScriptName 2.11.0 beta"
  exit 1
}

# Equivalent to the bash case pattern: [0-9]*.[0-9]*.[0-9]*
if ($Version -notmatch '^[0-9].*\.[0-9].*\.[0-9].*$') {
  Write-Host "Error: version must be in x.y.z format (e.g. 2.10.1)"
  exit 1
}

if (-not [string]::IsNullOrEmpty($Label)) {
  $DisplayVersion = "$Version-$Label"
}
else {
  $DisplayVersion = $Version
}

$BuildDate = (Get-Date).ToString('yyyy-MM-dd')
$Root = $PSScriptRoot

Write-Host "Setting version : $Version"
Write-Host "Display version : $DisplayVersion"
Write-Host "Build date      : $BuildDate"
Write-Host ""

# UTF-8 without BOM, to avoid introducing a BOM into ASCII/plain-text project files
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Update-FileContent {
  param(
    [string]$Path,
    [string]$Pattern,
    [string]$Replacement,
    [switch]$Global
  )

  if (-not (Test-Path -LiteralPath $Path)) {
    Write-Warning "File not found, skipping: $Path"
    return
  }

  $content = [System.IO.File]::ReadAllText($Path)
  $regex = [regex]$Pattern

  if ($Global) {
    $newContent = $regex.Replace($content, $Replacement)
  }
  else {
    $newContent = $regex.Replace($content, $Replacement, 1)
  }

  [System.IO.File]::WriteAllText($Path, $newContent, $Utf8NoBom)
  Write-Host "  Updated $Path"
}

# MANIFEST.MF files
Write-Host "[1/5] Updating MANIFEST.MF files..."
$manifestFiles = @(
  "$Root/org.rssowl.core/META-INF/MANIFEST.MF",
  "$Root/org.rssowl.ui/META-INF/MANIFEST.MF",
  "$Root/org.rssowl.core.tests/META-INF/MANIFEST.MF",
  "$Root/org.rssowl.lib.httpclient/META-INF/MANIFEST.MF"
)
foreach ($f in $manifestFiles) {
  Update-FileContent -Path $f `
    -Pattern 'Bundle-Version: \d+\.\d+\.\d+\.qualifier' `
    -Replacement "Bundle-Version: $Version.qualifier"
}

# feature.xml and category.xml files
Write-Host "[2/5] Updating feature.xml files..."
$featureFiles = @(
  "$Root/org.rssowl.feature/feature.xml",
  "$Root/org.rssowl.feature.eclipse/feature.xml",
  "$Root/org.rssowl.feature.dependencies/feature.xml",
  "$Root/org.rssowl.feature.dependencies.updater/feature.xml",
  "$Root/org.rssowl.feature.tests/feature.xml",
  "$Root/releng/update/category.xml"
)
foreach ($f in $featureFiles) {
  Update-FileContent -Path $f `
    -Pattern '\d+\.\d+\.\d+\.qualifier' `
    -Replacement "$Version.qualifier" `
    -Global
}

# product files
Write-Host "[3/5] Updating product files..."
$productFiles = @(
  "$Root/releng/product/rssowlnix.product",
  "$Root/releng/product_manual_export/rssowlnix.product"
)
foreach ($f in $productFiles) {
  if (-not (Test-Path -LiteralPath $f)) {
    Write-Warning "File not found, skipping: $f"
    continue
  }

  $content = [System.IO.File]::ReadAllText($f)
  $content = ([regex]'version="\d+\.\d+\.\d+"').Replace($content, "version=`"$Version`"", 1)
  $content = ([regex]'Version: \d+\.\d+\.\d+[-a-zA-Z0-9]*').Replace($content, "Version: $DisplayVersion", 1)
  $content = ([regex]'Build Id: \d{4}-\d{2}-\d{2}').Replace($content, "Build Id: $BuildDate", 1)
  [System.IO.File]::WriteAllText($f, $content, $Utf8NoBom)
  Write-Host "  Updated $f"
}

# config.ini
Write-Host "[4/6] Updating config.ini..."
Update-FileContent -Path "$Root/org.rssowl.ui/config.ini" `
  -Pattern 'rssowl\.buildId=\d+\.\d+\.\d+' `
  -Replacement "rssowl.buildId=$Version"

# Owl.java splash screen version constant
Write-Host "[5/6] Updating Owl.java..."
Update-FileContent -Path "$Root/org.rssowl.core/src/org/rssowl/core/Owl.java" `
  -Pattern 'SPLASH_VERSION = "\d+\.\d+\.\d+[-a-zA-Z0-9]*"' `
  -Replacement "SPLASH_VERSION = `"$DisplayVersion`""

# plugin.xml About dialog
Write-Host "[6/6] Updating plugin.xml..."
$pluginPath = "$Root/org.rssowl.ui/plugin.xml"
if (Test-Path -LiteralPath $pluginPath) {
  $content = [System.IO.File]::ReadAllText($pluginPath)
  $content = ([regex]'Version: \d+\.\d+\.\d+[-a-zA-Z0-9]*').Replace($content, "Version: $DisplayVersion")
  $content = ([regex]'Build Id: \d{4}-\d{2}-\d{2}').Replace($content, "Build Id: $BuildDate")
  [System.IO.File]::WriteAllText($pluginPath, $content, $Utf8NoBom)
  Write-Host "  Updated org.rssowl.ui/plugin.xml"
}
else {
  Write-Warning "File not found, skipping: $pluginPath"
}

Write-Host ""
Write-Host "Done. All files updated to $DisplayVersion (build: $BuildDate)"
Write-Host ""
Write-Host "Suggested next steps:"
Write-Host "  git add -A"
Write-Host "  git commit -m `"chore: bump version to $DisplayVersion`""
Write-Host "  git tag -a v$DisplayVersion -m `"Release v$DisplayVersion`""
Write-Host "  git push origin main --follow-tags"
