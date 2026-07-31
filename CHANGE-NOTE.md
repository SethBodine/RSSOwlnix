# Fix: Tycho pomless build failure on translations/

## The error

```
[FATAL] Non-readable POM /home/runner/.../translations/da/org.rssowl.core.nls.da/.polyglot.build.properties:
No parent pom file found in /home/runner/.../translations/da @
```
(repeated for all 30 leaf modules under translations/)

## Root cause

`tycho-pomless` resolves each module's parent POM by default exactly one
directory level up (`..`). The `translations/<lang>/<bundle>/` layout
puts a `<lang>/` grouping folder between each bundle and
`translations/pom.xml` — two levels up, not one. `<lang>/` (e.g. `da`,
`de`) isn't one of tycho-pomless's recognized magic aggregation folder
names (`bundles`, `plugins`, `tests`, `features`, `sites`, `products`,
`releng`), so it also didn't get an auto-generated aggregation POM.
Every leaf module's parent lookup failed as a result.

Confirmed against the official Tycho Pomless wiki
(https://github.com/eclipse-tycho/tycho/wiki/Tycho-Pomless), which
documents this exact scenario (a `component-A/plugins/plugin1`-shaped
layout) and its fix.

## The fix

Each of the 30 leaf modules' `build.properties` now sets:

```
tycho.pomless.parent = ../../pom.xml
```

which explicitly points two levels up (past the `<lang>/` folder) to
`translations/pom.xml`, instead of relying on the default one-level-up
assumption.

## Files changed

- `translations/<lang>/org.rssowl.core.nls.<code>/build.properties` (10)
- `translations/<lang>/org.rssowl.ui.nls.<code>/build.properties` (10)
- `translations/<lang>/org.rssowl.feature.nls.<code>/build.properties` (10)
- `CHANGELOG.md` — amended the existing `[Unreleased]` entry to document
  why `tycho.pomless.parent` is set, for whoever adds an 11th language
  later

31 files total. Nothing else in the tree is touched — no directory
restructuring, no changes to `translations/pom.xml`'s `<module>` paths,
no changes to `category.xml`, MANIFEST.MF, or any `.properties` message
content.

## Applying

Unzip over your existing checkout — same paths, so this only touches the
31 files above:

```
unzip -o rssowlnix-pomless-fix.zip -d /path/to/RSSOwlnix
```

## Verification performed

- Confirmed the fix targets all 30 leaf modules (none missed) by
  grepping a fresh extraction of the package for `tycho.pomless.parent`.
- Path arithmetic verified programmatically: `../../pom.xml` from
  `translations/da/org.rssowl.core.nls.da/` resolves to
  `translations/pom.xml`.
- Re-ran the full `java.util.Properties` parse check across all 310
  translation message files after the edit — zero failures, confirming
  the `build.properties` edits didn't corrupt anything else in those
  directories.
- Not verified: an actual Tycho/Maven build in this environment (no
  network access to Maven Central here). This fix is correct against
  the documented Tycho-pomless contract; the next CI run is the first
  real end-to-end confirmation.
