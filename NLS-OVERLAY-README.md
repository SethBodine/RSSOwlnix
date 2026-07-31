# RSSOwlnix language packs — overlay

Unzip this over your repo root (paths match exactly, nothing to move
around) and commit. Everything below is additive except the four files
listed under "Code change," which are direct replacements of existing
files.

```
unzip -o rssowlnix-nls-overlay.zip -d /path/to/RSSOwlnix
```

## What's in here

```
pom.xml                                  → adds <module>translations</module>
translations/                            → 10 language packs, 100% coverage
translations/pom.xml                     → new Tycho reactor module
releng/update-nls/category.xml           → was empty, now lists all 10 features
org.rssowl.ui/META-INF/MANIFEST.MF       → +1 optional bundle dependency
org.rssowl.ui/src/.../messages.properties → +1 key
org.rssowl.ui/src/.../Messages.java       → +1 field
org.rssowl.ui/src/.../ApplicationActionBarAdvisor.java → +1 Help menu action
tools/generate-nls-status.sh             → coverage report generator
tools/prune-dead-translation-keys.sh     → one-off dead-key cleanup (already run)
tools/find-missing-translations.sh       → per-language contributor helper
.github/workflows/translations-check.yml → new: PR-only fast validation
.github/workflows/build-all-platforms.yml → existing file, one step added
TRANSLATING.md                           → contributor guide
```

## The three things this gets you

**1. Deployed automatically on release.** No change needed to your deploy
step — `build-all-platforms.yml` already builds `releng/update-nls` and
pushes it to `update/nls` on gh-pages. It was only ever empty because
`category.xml` had nothing in it and nothing built the fragments. Both
fixed here.

**2. Visible in the app's language pack installer.** Your `.product` file
already registers the `update/nls` repository, and the p2 install UI
bundles are already part of the product — but nothing in the running app
opened that UI. `ApplicationActionBarAdvisor.java` now has a
"&Install Language Packs..." entry in the Help menu (after License, before
the separator/About) that calls the real, javadoc-verified
`ProvisioningUI.openInstallWizard(null, null, null)` — passing `null` for
the operation is what tells it to open in "browse all repositories" mode
rather than a fixed install list, per the Eclipse API docs. That's the
standard "Install New Software" wizard, defaulting to "-- All Available
Sites --", where the pre-registered `nls` site and its 10 language
features will appear. I have not run the built product to visually
confirm this — it's correct against the documented API contract, not
something I watched happen.

**3. All 10 languages at 100%.** Verified two ways: `mvn`-independent
check via `tools/generate-nls-status.sh` against your actual
`org.rssowl.core`/`org.rssowl.ui` source, and a real
`java.util.Properties` load of all 310 files (15,257 keys, zero parse
failures).

## What to know before merging

Every line added to a translation file since the original 10 upstream
`translation-*` repos is marked in-file:

```
# --- machine-drafted 2026-07-31, needs native-speaker review before merge ---
```

That's true for all 10 languages now, not just a subset. The rest of each
file — the vast majority — is the original, unaltered, human-written
community translation from `rssowl/translation-*`. Coverage being 100%
means nothing ships in English-fallback; it doesn't mean every line has
had native-speaker eyes on it. `TRANSLATING.md` is what you'd point a
native speaker at to go review/improve the marked lines.

The `prune-dead-translation-keys.sh` cleanup (removing ~700 keys for
functionality no longer in the codebase, mostly the old Google Reader
integration) has already been run — the `translations/` tree here is the
post-cleanup state. Re-running it is safe and a no-op if nothing's
changed since.
