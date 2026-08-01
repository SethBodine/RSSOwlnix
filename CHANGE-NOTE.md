# Fix (attempt 2): flatten translations/ — previous fix didn't work

## Why the last fix failed

Same error persisted after adding `tycho.pomless.parent = ../../pom.xml`
to each `build.properties`. I checked the actual pinned version rather
than assume, and found the real problem: `.mvn/extensions.xml` pins
`org.eclipse.tycho.extras:tycho-pomless` at **1.1.0**, while
`releng/configuration/pom.xml` pins Tycho core (`tycho.version`) at
**4.0.13** — decoupled, and 1.1.0 predates Tycho's own "1.5" release
notes, which is where pomless *update-site* and *product* support was
even added. The `tycho.pomless.parent` override I used is documented on
the current Tycho Pomless wiki, but that page describes the modern
`tycho-build` extension — nothing confirms 1.1.0 supports it, and the
build proved it doesn't.

I should have checked the pinned version before proposing a
version-dependent property, not after a second failure.

## The actual fix this time

Flattened `translations/` so all 30 modules
(`org.rssowl.{core,ui,feature}.nls.<code>/`) sit **directly** under
`translations/`, with no `<lang>/` grouping folder — removing any
dependency on `tycho.pomless.parent` working at all. This is not a
guess: it's the same shape as `bundles/` and `features/`, which build
successfully today at this exact 1.1.0 pin. Confidence here comes from
your own working build, not from documentation of a version you're not
running.

```
translations/org.rssowl.core.nls.de/
translations/org.rssowl.ui.nls.de/
translations/org.rssowl.feature.nls.de/
translations/org.rssowl.core.nls.fr/
... (30 total, flat)
```

The `tycho.pomless.parent` lines added last time are removed (no longer
needed).

## Files in this fix

- `translations/` — full tree, restructured flat (417 files)
- `translations/pom.xml` — module paths updated to flat (e.g.
  `<module>org.rssowl.core.nls.da</module>`, not
  `<module>da/org.rssowl.core.nls.da</module>`)
- `tools/generate-nls-status.sh`, `tools/find-missing-translations.sh`,
  `tools/prune-dead-translation-keys.sh` — all three assumed the nested
  `translations/<lang>/` layout for locating fragment files; rewritten
  for the flat layout and re-tested against it
- `TRANSLATING.md` — folder-structure instructions updated to match
- `CHANGELOG.md` — corrected to describe the real fix, not the
  non-working one

## Important: this replaces the old translations/ tree, not merges with it

Because paths changed (not just content), **delete the existing
`translations/` directory in your repo before unzipping this**, or
you'll end up with both the old nested layout and the new flat one side
by side:

```
rm -rf translations
unzip -o rssowlnix-nls-flatten-fix.zip -d /path/to/RSSOwlnix
```

`TRANSLATING.md` and `CHANGELOG.md` overwrite the versions from the
previous fix; `tools/*.sh` overwrite their previous versions too.

## Verification performed

- All 310 `messages_*.properties` files re-verified with a real
  `java.util.Properties` load after the move — zero failures.
- `generate-nls-status.sh` re-run against a fresh mirror of your actual
  `org.rssowl.core`/`org.rssowl.ui` source with the flat tree — all 10
  languages still 100%, confirming the move didn't lose content.
- `find-missing-translations.sh` tested against three cases: a complete
  language (0 missing), a Chinese-variant language where the folder
  suffix and properties suffix differ (0 missing), and a
  deliberately-invalid language code (correct error message).
- `prune-dead-translation-keys.sh` tested with a positive control:
  injected a fake dead key, confirmed the script actually caught and
  removed it (not just reported 0 and trusted that blindly, given the
  false-negative bug this exact script had earlier in the project).
- Still not verified: an actual Tycho/Maven build. No network access to
  Maven Central in this environment. This fix is now structurally
  identical to your already-working `bundles/`/`features/` pattern,
  which is the strongest confidence available without running the real
  build — but it's still the next CI run that's the first true
  end-to-end confirmation.
