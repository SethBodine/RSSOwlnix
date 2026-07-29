# RSSOwlnix

A powerful RSS/Atom/RDF feed reader based on Eclipse RCP, forked from [Xyrio/RSSOwlnix](https://github.com/Xyrio/RSSOwlnix).

> **Latest release: [2.10.3-beta](https://github.com/SethBodine/RSSOwlnix/releases/latest)**
> Regex search conditions ("matches regex" / "doesn't match regex") for Search News, Search Filters, and Saved Searches, with an error decoration for invalid patterns. Bug fixes: Search Filters no longer crash on a regex condition; List/Classic layout no longer marks items read on scroll (Newspaper/Headlines only); refreshing a feed no longer marks newly-arrived items as read; fast scrollbar-drag in Newspaper mode now marks items read in bulk once scrolling settles.

## Upgrade note (from before 2.10.2-beta)

If you use a **master password** to protect feed credentials, your saved passwords will not be readable after upgrading. This is a side effect of replacing the broken MD5 digest with SHA-512 for key derivation. Before upgrading, go to **Preferences → Credentials → Reset** to clear stored credentials - you will be prompted to re-enter them once after the update. Users relying on Windows DPAPI or macOS Keychain are unaffected.

## Links

releases: https://github.com/SethBodine/RSSOwlnix/releases

changelog: https://github.com/SethBodine/RSSOwlnix/blob/main/CHANGELOG.md

bug reports: https://github.com/SethBodine/RSSOwlnix/issues

upstream wiki (build instructions etc.): https://github.com/Xyrio/RSSOwlnix/wiki

## Requirements

- Java 17 or 21
- Maven 3.9+

## Building

The project builds via Maven/Tycho. CI (GitHub Actions) runs the build on Linux and produces Windows, Linux and macOS ZIPs automatically on push to `main` and on version tags - the command below is identical on any platform since it only needs Java + Maven.

**Linux / macOS:**
```bash
mvn -B clean verify --no-transfer-progress
```

**Windows (PowerShell or cmd):**
```powershell
mvn -B clean verify --no-transfer-progress
```

## Versioning (for maintainers)

`set-version.sh` (Linux/macOS) and `set-version.ps1` (Windows) update the version string across every `MANIFEST.MF`, `feature.xml`, `*.product`, `category.xml`, `config.ini`, `plugin.xml`, and the splash screen string in `Owl.java` - they do the same thing, so use whichever matches your shell.

**Linux / macOS:**
```bash
./set-version.sh 2.10.3 beta   # omit "beta" for a stable release
```
If the script won't execute (`/usr/bin/env: 'bash\r': No such file or directory`), it's picked up CRLF line endings - fix with `sed -i 's/\r$//' set-version.sh`.

**Windows (PowerShell):**
```powershell
./set-version.ps1 2.10.3 beta   # omit "beta" for a stable release
```

See `RELEASE.md` for the full release process and `RELEASE-STEPS.md` for the exact command sequence to cut a release.

## In-app Updates

The P2 update site is hosted on GitHub Pages and rebuilt automatically by CI on every push to `main`. To check for updates inside the application: **Help → Check for Updates**

| Channel | URL |
|---------|-----|
| Program updates | `https://SethBodine.github.io/RSSOwlnix/update/program` |
| Language packs  | `https://SethBodine.github.io/RSSOwlnix/update/nls`     |
| Add-ons         | `https://SethBodine.github.io/RSSOwlnix/update/addons`  |

## Security

Security vulnerabilities should be reported via [GitHub Security Advisories](https://github.com/SethBodine/RSSOwlnix/security/advisories/new) rather than as public issues.

## Upstream

This fork is based on [Xyrio/RSSOwlnix](https://github.com/Xyrio/RSSOwlnix) which is itself based on RSSOwl 2.2.1.


