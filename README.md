# RSSOwlnix

A powerful RSS/Atom/RDF feed reader based on Eclipse RCP, forked from [Xyrio/RSSOwlnix](https://github.com/Xyrio/RSSOwlnix).

> **Latest release: [2.10.2-beta](https://github.com/SethBodine/RSSOwlnix/releases/latest)**
> Includes security fixes (CWE-78, CWE-22, CWE-327), bug fixes for toolbar separators, tray startup and Lucene startup errors, and a live P2 update site.

> **In development (unreleased):** Stale-feed filters  surface feeds with no successful fetch or no new post in a configurable number of days, directly from the feed explorer toolbar.

##  Upgrade note for 2.10.2-beta

If you use a **master password** to protect feed credentials, your saved passwords will not be readable after upgrading. This is a side effect of replacing the broken MD5 digest with SHA-512 for key derivation. Before upgrading, go to **Preferences  Credentials  Reset** to clear stored credentials  you will be prompted to re-enter them once after the update. Users relying on Windows DPAPI or macOS Keychain are unaffected.

## Links

releases: https://github.com/SethBodine/RSSOwlnix/releases

changelog: https://github.com/SethBodine/RSSOwlnix/blob/main/CHANGELOG.md

bug reports: https://github.com/SethBodine/RSSOwlnix/issues

upstream wiki (build instructions etc.): https://github.com/Xyrio/RSSOwlnix/wiki

## Requirements

- Java 17 or 21

## Building

The project builds via Maven/Tycho on Linux. A GitHub Actions workflow handles CI and produces Windows, Linux and macOS ZIPs automatically on push to `main` and on version tags.

```bash
mvn -B clean verify --no-transfer-progress
```

## In-app Updates

The P2 update site is hosted on GitHub Pages and rebuilt automatically by CI on every push to `main`. To check for updates inside the application: **Help  Check for Updates**

| Channel | URL |
|---------|-----|
| Program updates | `https://SethBodine.github.io/RSSOwlnix/update/program` |
| Language packs  | `https://SethBodine.github.io/RSSOwlnix/update/nls`     |
| Add-ons         | `https://SethBodine.github.io/RSSOwlnix/update/addons`  |

## Security

Security vulnerabilities should be reported via [GitHub Security Advisories](https://github.com/SethBodine/RSSOwlnix/security/advisories/new) rather than as public issues.

## Upstream

This fork is based on [Xyrio/RSSOwlnix](https://github.com/Xyrio/RSSOwlnix) which is itself based on RSSOwl 2.2.1.


