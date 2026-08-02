# Changelog

## [Unreleased]

### Added

- **Per-folder overrides: forced Auto-Update Interval + Proxy bypass**
  Two folder-level override features, built on one shared, generic
  inheritance mechanism (`CascadingScope`) instead of duplicating cascade
  logic per feature. Precedence is **topmost-enforced-wins, root-first**:
  walking from the root folder down to a bookmark, the first folder in
  that path with its override enabled wins the whole subtree below it -
  not the nearest one. A folder's own configured value is never deleted
  or overwritten when it's outranked by an ancestor; it stays dormant and
  reactivates automatically once the ancestor's override is disabled.

  - **Forced Auto-Update Interval**: a folder can force a specific
    refresh interval (e.g. every 5 minutes for a "Breaking News" folder,
    once a day for "Archives") for every feed nested inside it,
    overriding the global interval and any per-feed setting.
    `FeedReloadService` now resolves each bookmark's interval through the
    cascade and reacts to folder changes by recursively re-syncing every
    bookmark nested underneath. The old `updateChildPreferences()`
    one-shot copy-down in `GeneralPropertyPage` (which wrote a folder's
    setting onto every *current* child's own keys at save time, and
    silently missed children added later) has been removed - resolution
    is now live.
  - **Proxy bypass (v1, boolean only)**: a folder can force "use proxy" /
    "connect directly" for every feed nested inside it, overriding the
    global proxy configuration. Per-protocol (HTTP/HTTPS/SOCKS) config,
    host/port fields, and credential storage are intentionally out of
    scope for this pass and left for a v2 follow-up.
    `Controller#reload()` resolves the cascade and injects the
    (previously defined but unused) `USE_PROXY` connection property;
    `DefaultProtocolHandler` now honors it, skipping proxy resolution
    entirely when a folder ancestor has bypass enabled.

  New preference keys (`FOLDER_UPDATE_INTERVAL[_STATE]`,
  `FOLDER_PROXY_OVERRIDE_STATE`, `FOLDER_PROXY_BYPASS`) are deliberately
  separate from the existing per-bookmark keys, so the property copy that
  happens during reparenting can never freeze a stale folder value onto a
  bookmark that never reads it - covered by a dedicated regression test.
  `GeneralPropertyPage` gets new folder-only controls for both features,
  greying out and showing "Managed by ancestor folder '…'" when a single
  selected folder is shadowed by an enforcing ancestor.

  `org.rssowl.core/src/org/rssowl/core/internal/persist/pref/CascadingScope.java`
  `org.rssowl.core/src/org/rssowl/core/persist/pref/Preference.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/pref/DefaultPreferences.java`
  `org.rssowl.core/src/org/rssowl/core/persist/service/IPreferenceService.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/service/PreferenceServiceImpl.java`
  `org.rssowl.core/src/org/rssowl/core/internal/connection/DefaultProtocolHandler.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/services/FeedReloadService.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/Controller.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/PreferencesInitializer.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/properties/GeneralPropertyPage.java`

- **Added modern replacements for the dead `KeywordFeed` sources in `org.rssowl.ui/plugin.xml` and `plugin.eclipse.xml`**
  The previous pass dropped Google News, Delicious, Digg, Twitter, Google
  Blog Search, and YouTube (GData) because their RSS output was gone. This
  pass researches and adds working replacements, verified live as of
  July 2026. Both plugin manifests (`plugin.xml` and `plugin.eclipse.xml`)
  and both properties files (`plugin.properties` and
  `plugin.eclipse.properties`) were updated together, matching the
  existing Flickr/Vimeo pattern.

  Added:
  - `KeywordFeed`: **Reddit** (`org.rssowl.ui.RedditKeywordFeed`), using
    Reddit's still-live `https://www.reddit.com/search.rss?q=[:]` search
    endpoint. Reuses the existing `icons/obj16/share_reddit.gif` icon
    (already shipped for the share-to-Reddit action). Caveat: Reddit
    began rate-limiting unauthenticated RSS access in mid-2026 (as low as
    1 request/minute per a widely-reported case) and has publicly flagged
    RSS as a candidate for future restriction alongside its now-closed
    unauthenticated `.json` endpoints. The feed works today but should be
    considered at risk; if Reddit closes it, this entry will need to be
    removed or replaced.
  - `KeywordFeed`: **Hacker News** (`org.rssowl.ui.HackerNewsKeywordFeed`),
    using `https://hnrss.org/newest?q=[:]`. HN's own search (Algolia,
    `hn.algolia.com/api/v1/search`) only returns JSON, and the
    `KeywordFeed` extension point has no transform/parse hook - it treats
    `url` as a literal feed location - so raw Algolia JSON is not usable
    here without new Java code (a custom interpreter or reusing the
    `LinkTransformer` machinery, neither of which exists for JSON today).
    Used `hnrss.org` instead: a long-running (10+ years), community-run
    proxy that queries the same Algolia index server-side and returns
    real RSS/XML, confirmed live. No icon asset exists for
    Hacker News in `icons/obj16/`; a new one should be added (entry
    currently ships without an `icon` attribute, which is valid per
    `KeywordFeed.exsd`).
  - `KeywordFeed`: **Mastodon** (`org.rssowl.ui.MastodonKeywordFeed`),
    using `https://mastodon.social/tags/[:].rss`, Mastodon's built-in
    per-instance hashtag RSS. Pinned to the flagship `mastodon.social`
    instance since the extension point only supports one fixed URL
    pattern per entry and hashtag results are local to whichever
    instance is queried (results will differ from other instances, and
    posts from users who aren't on/known to mastodon.social may be
    under-represented). No icon asset exists for Mastodon either; a new
    one should be added.

  Deliberately not added, with reasoning:
  - **YouTube**: channel RSS (`youtube.com/feeds/videos.xml?channel_id=`)
    still works, but there is no keyword-search RSS - YouTube retired
    that in the GData v2 shutdown, and the current Data API v3 search
    endpoint requires an API key, which `KeywordFeed` has no facility to
    supply. Pointing the entry at an HTML search-results page wouldn't
    work either, since RSSOwl expects `url` to resolve directly to a
    feed. Recommendation: leave YouTube out of `KeywordFeed`; users who
    know a channel's ID can already add it as a normal bookmark via the
    existing `videos.xml?channel_id=` URL.
  - **Lemmy**: RSS exists per-community per-instance
    (`https://instance.tld/feeds/c/community.xml`), but there is no
    cross-instance keyword search endpoint - `[:]` would have to be a
    known community name on a specific instance, not a search term,
    which doesn't match how every other `KeywordFeed` entry behaves.
    Better suited to being added as a handful of ordinary bookmarks
    (e.g. for specific communities on lemmy.world) than as a
    `KeywordFeed` search provider.
  - **Substack**: only per-publication feeds exist
    (`https://name.substack.com/feed`); there is no site-wide search-by-
    keyword RSS endpoint to substitute `[:]` into.

- **Added community language packs (10 languages) as a P2 NLS update site**
  Bundles the 10 complete `rssowl/translation-*` community translations
  (Danish, German, Spanish, French, Italian, Polish, Portuguese, Serbian,
  Simplified Chinese, Traditional Chinese) as Tycho-buildable NLS
  fragments under `translations/`, wired into the previously-empty
  `releng/update-nls` P2 site. Publishes to `update/nls` on the existing
  gh-pages workflow; also addable via a new lightweight
  `publish-nls.yml` workflow that rebuilds and republishes just the
  language packs (`mvn -pl translations,releng/update-nls -am`) on any
  push to `main` touching translation content, without running the full
  multi-platform product build.

  - `translations/org.rssowl.{core,ui}.nls.<code>`: NLS fragments
    mirroring the host bundles' package structure, built via
    `tycho-pomless`, flat directly under `translations/` (no
    per-language grouping folder) matching `bundles/`/`features/` -
    `.mvn/extensions.xml` pins `org.eclipse.tycho.extras:tycho-pomless`
    at 1.1.0, which predates Tycho's pomless update-site/product support
    added in 1.5 and does not support the newer `tycho.pomless.parent`
    build.properties override, so any extra directory level between a
    module and its parent POM breaks parent resolution entirely
  - `translations/org.rssowl.feature.nls.<code>`: wraps each
    language's two fragments into one installable P2 feature
  - `releng/update-nls/category.xml`: now lists all 10 language features
    (was an empty placeholder)
  - Translated the 24 keys added since the original 2.1.2 translations
    (regex search conditions, the day-threshold feed filter, grouping by
    recency, page size, the new "Install Language Packs..." menu item)
    plus remaining pre-existing gaps in `es`/`fr`/`it`/`zh_TW`, bringing
    all 10 languages to 100% key coverage. These added lines are marked
    inline (`# machine-drafted, needs native-speaker review`) and are
    not a substitute for native-speaker review - see `TRANSLATING.md`.
  - `tools/generate-nls-status.sh`, `tools/prune-dead-translation-keys.sh`,
    `tools/find-missing-translations.sh`: ongoing maintenance scripts
  - `.github/workflows/translations-check.yml`: PR-scoped encoding/key
    validation and coverage comment, without running the full product
    build
  - `TRANSLATING.md`: contributor guide for adding or updating a
    language

- **Added an in-app entry point for installing language packs**
  The three P2 repositories (program/nls/addons) were already registered
  via `<repositories>` in `releng/product/rssowlnix.product`, and the p2
  install UI bundles (`org.eclipse.equinox.p2.ui.sdk` and friends) were
  already part of the product via the `org.rssowl.dependencies.updater`
  root feature - but nothing in the running app ever opened that UI.
  `org.rssowl.ui.actions.FindUpdates`/`.FindExtensions` were defined in
  `plugin.xml`/`plugin.eclipse.xml` but never wired to a handler, and
  `ApplicationActionBarAdvisor.createHelpMenu()` was fully hand-built
  with no "Install New Software..." equivalent. Added a
  "&Install Language Packs..." Help menu action calling
  `ProvisioningUI.getDefaultUI().openInstallWizard(null, null, null)`,
  which opens the standard p2 install wizard in repository-browse mode
  (per the Eclipse API: passing `null` for the operation means "let the
  user browse the repositories" rather than a fixed install list),
  defaulting to "-- All Available Sites --" where the registered `nls`
  site's language packs now appear. `org.eclipse.equinox.p2.ui`,
  `org.eclipse.equinox.p2.metadata`, and `org.eclipse.equinox.p2.operations`
  added to `org.rssowl.ui`'s `Require-Bundle`, all `resolution:=optional`
  (the latter two needed at compile time because they declare
  `IInstallableUnit` and `InstallOperation`, types referenced in the
  `openInstallWizard` method signature even though not named directly
  in our own code; both are already part of the installed product via
  `org.rssowl.feature.dependencies.updater`, so this only affects the
  compile-time classpath, not what ships).

### Removed

- **Removed ~700 dead keys from the bundled language pack translations**
  The community translations bundled above (see Added) carried forward
  keys for functionality no longer in this fork's English source -
  mostly the old Google Reader integration strings, consistent with the
  Google Reader removal below. Cleaned via the new
  `tools/prune-dead-translation-keys.sh`, re-verified coverage was
  unaffected (same percentages before/after against current English
  source).

- **Finished removing Google Reader / synchronization code from core, UI, and tests**
  Follow-up to the previous pass, which was scoped to `org.rssowl.ui`.
  This pass removes the remaining Google Reader / sync code from
  `org.rssowl.core`, the last few UI call sites that depended on it, and
  the tests that exercised it, so nothing dead is left anywhere in the
  codebase.

  Removed entirely:
  - `SyncUtils`, `SyncItem`, `SyncConnectionException` (core sync
    utilities and data model).
  - `ReaderProtocolHandler`, the `reader://`/`readers://` scheme handler
    (+ its registration in `org.rssowl.core/plugin.xml` and 5 message
    keys).
  - `org.rssowl.core.internal.interpreter.json.JSONInterpreter`, the
    Google Reader JSON-stream parser, and its `interpretJSONObject()`
    wiring in `IInterpreterService`/`InterpreterServiceImpl`. Confirmed
    distinct from and unrelated to `JsonInterpreter` (the general
    JSON Feed format parser), which is untouched, as are the shared
    `JSONObject`/`JSONArray`/`JSONTokener`/`JSONException` library
    classes it still uses.
  - `SyncConnectionTests`, `SyncServiceTest`, `SyncUtilsTest` (test
    classes exercising the above), plus their suite references in
    `UITests`/`LocalTests`, and an orphaned `testGoogleReaderSync`/
    `testGetGoogleReaderAPIToken` pair in `ConnectionTests` that was
    already `@Deprecated`/`@Ignore`d.

  Trimmed from existing files:
  - `DefaultProtocolHandler`: Google ClientLogin-era 403 error parsing
    (`isSyncAuthenticationIssue()`, `handleForbidden()`), 12 constants,
    9 message keys.
  - `IConnectionPropertyConstants`: `ITEM_LIMIT`, `DATE_LIMIT`,
    `UNCOMMITTED_ITEMS` — all exclusively consumed by the sync/reader
    machinery above.
  - `Controller`: sync-derived item/date reload limits, the
    Google-login-cancel timestamp, the synchronized-feed branch of the
    login-dialog locking and `openLoginDialogInternal()`, Google Reader
    favicon special-casing, and `SyncConnectionException`-based
    error-link tracking (which could never be populated by anything
    else).
  - `ApplicationServiceImpl`: dead Google-labels/read-state merge block
    and an unreachable `isSynchronized` skip-guard.
  - `News`/`Feed`/`MergeUtils`: the synchronized-feed fast paths in
    `merge()`/`mergeNews()`/`copyWithoutDuplicates()` (GUID-based
    matching, label/state merge shortcuts) and the `EXCLUDE_PROPERTIES`
    set that shielded Google-only properties from ordinary merging —
    all dead once no feed can be synchronized.
  - `URIUtils`: dead `readers://` scheme check in `toHTTP()`.
  - `ApplicationActionBarAdvisor`, `TutorialWizard`, `TutorialPage`: the
    "Google Reader Synchronization" help-menu item and tutorial chapter,
    already dead-gated behind `SyncUtils.ENABLED`.
  - `OwlUI`: the unreachable `openSyncLogin()`.
  - `LoginDialog`: collapsed the sync-login variant entirely — dropped
    the 4-arg constructor, `fIsSyncLogin` field, forced
    remember-password behavior, and the "create a Google account" link
    injected into the button bar.
  - `AddCredentialsDialog`, `CredentialsPreferencesPage`: dead Google
    Reader autocomplete suggestion, label special-case, and synthetic
    credentials-list entry.
  - `GeneralPropertyPage`, `RetentionPropertyPage`,
    `InformationPropertyPage`: removed `isSynchronized`-based branching
    (read-only feed field, checkbox labels, load-status messages, "find
    out more" link) now that no bookmark can be synchronized; 6 more
    orphaned message keys.
  - `NewsGrouping`: a dead `isSynchronized` guard in group-ID
    determination.
  - `MyCredentialsProvider`, `ConnectionTests`: dropped a test-only
    Google credentials branch and the two dead test methods above.
  - `InterpreterTest`: removed `testJSON()`, which exclusively exercised
    the deleted `JSONInterpreter`, and its now-orphaned
    `data/interpreter/feed_json.txt` fixture.
  - `MergeUtilsTest`: removed `testMergeExcludedProperties()` (tested
    the removed `EXCLUDE_PROPERTIES` mechanism) and simplified
    `testMergeProperties()`, which had incidentally piggybacked a sync
    constant onto an otherwise unrelated property-merge test.

  Also removed: 2 orphaned comments in `Preference`/`DefaultPreferences`
  left behind by the earlier `CLEAN_UP_BM_BY_SYNCHRONIZATION` removal.

  Not deleted: `icons/wizban/reader_wiz.png` is now unreferenced, same
  as the icons noted in the previous entry below — left in place since
  removing binary assets isn't part of this pass.

  `org.rssowl.core/src/org/rssowl/core/util/SyncUtils.java` (deleted)
  `org.rssowl.core/src/org/rssowl/core/util/SyncItem.java` (deleted)
  `org.rssowl.core/src/org/rssowl/core/connection/SyncConnectionException.java` (deleted)
  `org.rssowl.core/src/org/rssowl/core/internal/connection/ReaderProtocolHandler.java` (deleted)
  `org.rssowl.core/src/org/rssowl/core/internal/interpreter/json/JSONInterpreter.java` (deleted)
  `org.rssowl.core.tests/src/org/rssowl/core/tests/connection/SyncConnectionTests.java` (deleted)
  `org.rssowl.core.tests/src/org/rssowl/core/tests/ui/SyncServiceTest.java` (deleted)
  `org.rssowl.core.tests/src/org/rssowl/core/tests/util/SyncUtilsTest.java` (deleted)
  `org.rssowl.core.tests/data/interpreter/feed_json.txt` (deleted)
  `org.rssowl.core/src/org/rssowl/core/internal/connection/DefaultProtocolHandler.java`
  `org.rssowl.core/src/org/rssowl/core/connection/IConnectionPropertyConstants.java`
  `org.rssowl.core/src/org/rssowl/core/interpreter/IInterpreterService.java`
  `org.rssowl.core/src/org/rssowl/core/internal/interpreter/InterpreterServiceImpl.java`
  `org.rssowl.core/src/org/rssowl/core/internal/ApplicationServiceImpl.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/News.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/Feed.java`
  `org.rssowl.core/src/org/rssowl/core/util/MergeUtils.java`
  `org.rssowl.core/src/org/rssowl/core/util/URIUtils.java`
  `org.rssowl.core/src/org/rssowl/core/persist/pref/Preference.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/pref/DefaultPreferences.java`
  `org.rssowl.core/plugin.xml`
  `org.rssowl.ui/src/org/rssowl/ui/internal/Controller.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/ApplicationActionBarAdvisor.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/OwlUI.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/LoginDialog.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/AddCredentialsDialog.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/preferences/CredentialsPreferencesPage.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/properties/GeneralPropertyPage.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/properties/RetentionPropertyPage.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/properties/InformationPropertyPage.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/welcome/TutorialWizard.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/welcome/TutorialPage.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsGrouping.java`
  `org.rssowl.core.tests/src/org/rssowl/core/tests/connection/ConnectionTests.java`
  `org.rssowl.core.tests/src/org/rssowl/core/tests/connection/MyCredentialsProvider.java`
  `org.rssowl.core.tests/src/org/rssowl/core/tests/interpreter/InterpreterTest.java`
  `org.rssowl.core.tests/src/org/rssowl/core/tests/util/MergeUtilsTest.java`
  `org.rssowl.core.tests/src/org/rssowl/core/tests/UITests.java`
  `org.rssowl.core.tests/src/org/rssowl/core/tests/LocalTests.java`
  Several `Messages.java`/`messages.properties` pairs across the touched
  packages above.

- **Removed dead Google Reader / synchronization service code**
  Google Reader shut down in 2013, and the sync feature that depended on
  it had already been fully disabled in code (`SyncUtils.ENABLED =
  false`). This pass removes the dead code paths that referenced it
  throughout the UI plugin, without touching the OPML/keyword/recommended
  import paths or the underlying `org.rssowl.core` connection/sync
  utility classes (`SyncUtils`, `SyncItem`, `ReaderProtocolHandler`,
  `SyncConnectionException`), which are left in place as a follow-up.

  Removed entirely:
  - `SyncService` and `SyncItemsManager` - the background sync engine
    that batched news-state changes and pushed them to the Google
    Reader API.
  - `ShowSynchronizationStatusAction` and its `SynchronizationStatusDialog`
    - showed last-sync status; both were already unwired from the menu
      (commented out in `plugin.xml`).
  - `UnsubscribeGoogleReaderAction` - migrated synchronized bookmarks
    into a "Google Reader Archive" folder; already unwired from the menu.

  Trimmed from existing files:
  - `Controller`: the `SyncService` field, accessor, lifecycle
    (start/stop), and the uncommitted-sync-items connection property.
  - `ApplicationWorkbenchWindowAdvisor`: the on-minimize sync trigger.
  - `ManageLabelsPreferencePage`: the label-rename propagation that
    pushed renamed labels to Google-Reader-synced news items.
  - `CleanUpModel` (+ `CleanUpOperations`, `CleanUpOptionsPage`,
    `CleanUpWizard`): the "delete feeds no longer subscribed to in
    Google Reader" clean-up option, including the OPML-from-Google
    fetch it relied on. This option was already dead-gated behind
    `SyncUtils.ENABLED`.
  - `ImportSourcePage`: the "Synchronize with Google Reader" import
    source (`Source.GOOGLE`), its radio button, and related branches.
    The OPML file/website, keyword search, and recommended-feeds import
    sources are unaffected.
  - `ImportElementsPage`: `importFromGoogleReader()` and the
    `enableSynchronization(...)` / `setSynchronizationProperties(...)`
    helper cluster used only for Google-Reader-synced bookmarks, the
    Google-Reader-specific auth-retry and `SyncConnectionException`
    handling, and the now-unused `authToken` parameter on the shared
    `openStream()` helper (all other import paths always passed `null`).

  Also removed as a result: the orphaned `CLEAN_UP_BM_BY_SYNCHRONIZATION`
  preference key (`Preference`, `DefaultPreferences`,
  `PreferencesInitializer`), the dead commented-out
  `ShowSynchronizationStatusAction`/`UnsubscribeGoogleReaderAction`
  entries and their `action.label.34`/`action.label.40` strings from
  `plugin.xml`/`plugin.properties`, and roughly twenty now-orphaned
  message keys across the `actions`, `dialogs`, `dialogs/preferences`,
  `dialogs/cleanup`, and `dialogs/importer` `Messages.java`/
  `messages.properties` pairs.

  Not touched in this pass (still reference Google Reader/sync, but are
  outside `org.rssowl.ui` or are test-only): `org.rssowl.core`'s
  `SyncUtils`, `SyncItem`, `ReaderProtocolHandler`,
  `SyncConnectionException`, and `org.rssowl.core.tests`'
  `SyncConnectionTests`/`SyncServiceTest`, which currently exercise the
  now-deleted `SyncService`/`SyncItemsManager` and will need updating in
  a follow-up.

  `org.rssowl.ui/src/org/rssowl/ui/internal/services/SyncService.java` (deleted)
  `org.rssowl.ui/src/org/rssowl/ui/internal/services/SyncItemsManager.java` (deleted)
  `org.rssowl.ui/src/org/rssowl/ui/internal/actions/ShowSynchronizationStatusAction.java` (deleted)
  `org.rssowl.ui/src/org/rssowl/ui/internal/actions/UnsubscribeGoogleReaderAction.java` (deleted)
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/SynchronizationStatusDialog.java` (deleted)
  `org.rssowl.ui/src/org/rssowl/ui/internal/Controller.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/ApplicationWorkbenchWindowAdvisor.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/PreferencesInitializer.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/preferences/ManageLabelsPreferencePage.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/cleanup/CleanUpModel.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/cleanup/CleanUpOperations.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/cleanup/CleanUpOptionsPage.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/cleanup/CleanUpWizard.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/importer/ImportSourcePage.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/importer/ImportElementsPage.java`
  `org.rssowl.core/src/org/rssowl/core/persist/pref/Preference.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/pref/DefaultPreferences.java`
  `org.rssowl.ui/plugin.xml`
  `org.rssowl.ui/plugin.properties`
  `Messages.java` / `messages.properties` in `actions`, `dialogs`,
  `dialogs/preferences`, `dialogs/cleanup`, `dialogs/importer`

- **Dropped dead third-party services from `org.rssowl.ui/plugin.xml` and `plugin.eclipse.xml`**
  Several `KeywordFeed`, `LinkTransformer`, and `FeedSearch` contributions
  pointed at services that have been shut down or had their RSS output
  removed for years, so using them silently produced no results (or an
  error). Both plugin manifests carried their own copies of these, so both
  were cleaned up together. Removed:
  - `KeywordFeed`: Google News, Delicious, Bing/Live, Digg,
    Twitter/queryfeed, Google Blog Search, YouTube (GData v2 in
    `plugin.xml`, the equally-dead `youtube.com/rss/tag` in
    `plugin.eclipse.xml`). `plugin.eclipse.xml` additionally had a
    Technorati entry (`feeds.technorati.com`, Technorati shut down
    ~2014) that `plugin.xml` never had - removed too. Flickr is kept in
    both files. `plugin.eclipse.xml` was also missing the Vimeo entry
    that `plugin.xml` has (a pre-existing drift between the two
    manifests, unrelated to any dead service) - added it there to match.
  - `LinkTransformer`: Readability (`mobile.rssowl.org`, and Readability
    itself shut down in 2016) and Google Mobilizer (`google.com/gwt/x`).
    Instapaper is kept in both files.
  - `FeedSearch`: the default `searchUrl` in both files pointed at
    `rssowl.org/rssowl2dg/search/search.php`, which no longer resolves
    (the rssowl.org domain is gone). Replaced with Feedly's public,
    unauthenticated feed-search endpoint
    (`https://cloud.feedly.com/v3/search/feeds?query=[K]`), which was
    verified to return live JSON results for arbitrary keywords. No code
    change was needed: `ImportElementsPage` already treats the response as
    opaque text and regex-extracts feed-looking URLs from it
    (`importFromOnlineResourceBruteforce`), so a JSON body works the same
    way an HTML body did.

  With both manifests cleaned up, the now-truly-orphaned message keys
  were also removed from `plugin.properties`: `keywordFeed.name` (Google
  News) through `keywordFeed.name.6` (YouTube), and
  `newsLinkTransformer.name.0`/`.1` (Readability, Google Mobilizer).
  `keywordFeed.name.7`/`.8` (Flickr/Vimeo) and `newsLinkTransformer.name`
  (Instapaper) are kept.

  Also now unreferenced anywhere in the codebase, but left in place since
  deleting binary assets wasn't part of this pass:
  `org.rssowl.ui/icons/obj16/fav_technorati.gif`, `fav_delicious.gif`,
  `fav_live.gif`, `fav_digg.gif`, `fav_twitter.gif`, `fav_youtube.gif`.

  `org.rssowl.ui/plugin.xml`
  `org.rssowl.ui/plugin.eclipse.xml`
  `org.rssowl.ui/plugin.properties`

### Fixed

- **Folder overrides: three issues found during manual testing of the
  forced Auto-Update Interval / Proxy bypass feature above**
  - A Bookmark nested under a Folder that forces the Auto-Update
    Interval could still have its own "Automatically update" checkbox
    edited in `GeneralPropertyPage`, even though an enforcing ancestor
    Folder meant that setting had no effect. The "Managed by ancestor
    folder" greyed-out state was only being computed for Folder
    selections, not Bookmark selections - it's now computed for either,
    since the cascade resolution itself already worked identically for
    both.
  - The Proxy-bypass sub-control was a single "Connect directly, bypass
    proxy" checkbox, whose *unchecked* state ambiguously meant "use the
    proxy" without saying so. Replaced with an explicit two-option
    dropdown ("Use global proxy setting" / "Connect directly (bypass
    proxy)") so the choice - and its default - is unambiguous.
  - Proxy bypass was not actually forcing a direct connection: setting
    `RequestConfig#setProxy(null)` alone only means "no proxy explicitly
    requested for this call" to some route planners, which then still
    fall back to the JVM-wide proxy (e.g. one installed globally via
    `ProxySelector`/system properties, such as by Eclipse's
    `org.eclipse.core.net` bundle). `DefaultProtocolHandler` now installs
    an explicit `DefaultRoutePlanner` (whose `determineProxy()`
    unconditionally returns `null`) on the `HttpClientBuilder` whenever a
    Folder's bypass override is active, guaranteeing a direct connection
    regardless of any global proxy configuration.

  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/properties/GeneralPropertyPage.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/properties/Messages.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/properties/messages.properties`
  `org.rssowl.core/src/org/rssowl/core/internal/connection/DefaultProtocolHandler.java`

- **Fixed Tycho build failure caused by hardcoded version constraints in the
  bundled language packs**
  Moving the translations into the repo introduced a P2 dependency
  resolution failure: `org.rssowl.core.nls.da` (and the 19 other language
  fragments/features) required `org.rssowl.core`/`org.rssowl.ui` in the
  exact range `[2.10.4,2.10.5)`, hardcoded into `Fragment-Host` and the
  feature `<import>` elements. Since the actual code version tracked by
  `set-version.sh` was `2.10.3`, every CI build failed at
  `org.rssowl.core.nls.da` with "Missing requirement... could not be
  found", skipping the remaining 37 reactor modules.

  Two changes fix this:
  - Removed the exact-range host-version constraint from all 20
    `Fragment-Host` entries and the corresponding 10 feature `<import>`
    elements, so language packs attach to whatever host version is
    actually built instead of requiring an exact match. Translation packs
    don't need to track the app's micro version - Eclipse NLS fallback
    means missing keys just show English regardless of host version.
  - Realigned the language packs' own `Bundle-Version`/feature version
    from the stray `2.10.4.qualifier` back to `2.10.3.qualifier`, matching
    the actual in-progress version, and added the `translations/*` globs
    to `set-version.sh`'s MANIFEST.MF and feature.xml/category.xml loops
    so this stays in sync automatically on every future version bump
    instead of drifting again next release.

  `translations/*/META-INF/MANIFEST.MF` (20 files)
  `translations/org.rssowl.feature.nls.*/feature.xml` (10 files)
  `releng/update-nls/category.xml`
  `set-version.sh`

## [2.10.3-beta] - 2026-07-28

### New Features

- **Regex search conditions**
  Added "matches regex" and "doesn't match regex" specifiers to Search
  News conditions for the Entire News, Title, Description, Author, and
  Attachments fields. Available anywhere the same condition-builder UI is
  used - Search News, Search Filters, and Saved Searches. Invalid patterns
  are flagged with an error decoration in the UI, and the specifier
  dropdown's tooltip explains regex syntax and the "match any" restriction
  (see Bug Fixes below).
  `org.rssowl.core/src/org/rssowl/core/persist/SearchSpecifier.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/search/ModelSearchImpl.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/search/SearchConditionItem.java`

### Bug Fixes

- **Search Filters threw an exception when using a "matches regex" condition**
  The regex-matching search condition ("matches regex" / "doesn't match
  regex", added for Entire News/Title/Description/Author/Attachments) was
  only wired up in the interactive "Search News" engine (`ModelSearchImpl`).
  Search Filters (Tools  News Filters, the rules that run automatically
  against incoming news) use a separate query builder
  (`ModelSearchQueries.createQuery()`, called directly from
  `ApplicationServiceImpl`) that has no concept of regex conditions and
  explicitly throws `UnsupportedOperationException` for any specifier it
  doesn't recognize. Since filter conditions are built with the same UI
  widgets as "Search News", nothing stopped a user from creating a filter
  with a regex condition that would then throw the next time news arrived.
  Fixed by extracting the regex-condition handling into a shared
  `RegexSearchUtils` and using it from both engines, so Search Filters now
  apply regex conditions as a Java post-filter the same way "Search News"
  does.
  `org.rssowl.core/src/org/rssowl/core/internal/persist/search/RegexSearchUtils.java` (new)
  `org.rssowl.core/src/org/rssowl/core/internal/ApplicationServiceImpl.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/search/ModelSearchImpl.java`

- **"Attachments matches regex" silently searched the news title instead**
  The field-text lookup used for the regex post-filter returned
  `news.getTitle()` for the Attachments field instead of the attachment
  link/type text that's actually indexed for it (see
  `SearchDocument#createAttachmentsField`), so a regex condition on
  Attachments never tested attachment data at all. Fixed as part of the
  `RegexSearchUtils` extraction above; "Entire News" regex matching now also
  includes attachment text, matching how the non-regex "Entire News" search
  already behaves.
  `org.rssowl.core/src/org/rssowl/core/internal/persist/search/RegexSearchUtils.java`

- **Mixing regex and non-regex conditions under "match any" gave silently wrong results**
  A regex condition is evaluated as a Java post-filter after the Lucene
  query for the other conditions has already run. Under "match all" that
  combines correctly (both layers narrow the result set further). Under
  "match any" it doesn't: an item that already matched a non-regex OR
  condition could be incorrectly dropped for not also matching the regex,
  and an item that could only ever match through the regex condition was
  never a Lucene candidate to begin with. Rather than trying to make "match
  any" work correctly for a mix of the two (which would need the regex
  filter to know which Lucene conditions each candidate already satisfied),
  this combination is now simplified to a single rule: regex conditions are
  only applied when they are the sole kind of condition in the search (a
  Location/Scope condition is exempt, since it just restricts which
  Bookmark/Folder/Bin is searched). If "match any" is selected and regex
  conditions are mixed with other conditions, the regex conditions are
  ignored (with a log entry explaining why) and the search runs on the
  other conditions only, exactly as if the regex conditions were never
  added.
  `org.rssowl.core/src/org/rssowl/core/internal/persist/search/RegexSearchUtils.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/search/ModelSearchImpl.java`
  `org.rssowl.core/src/org/rssowl/core/internal/ApplicationServiceImpl.java`

  The specifier dropdown's existing tooltip (shown for "matches regex" /
  "doesn't match regex") now also explains this restriction, so it's visible
  while building the search rather than only discoverable via the log.
  `org.rssowl.ui/src/org/rssowl/ui/internal/search/messages.properties`

- **List/Classic layout incorrectly marked items read on scroll**
  A previous fix ("Items not marked as read when scrolling past them using
  the scrollbar", below) added scroll/wheel/key-driven mark-as-read logic to
  `NewsTableControl`, the table control shared by both the List and Classic
  layouts. That logic fired on any scrollbar movement, mouse wheel, or arrow
  key press, regardless of whether the user had actually selected/read an
  item - so simply scrolling the list marked everything above the fold as
  read. Only Newspaper/Headlines (the browser-rendered view) is meant to
  mark items read on scrolling; List/Classic should only mark read via
  selection (click or arrow-key selection change) after the configured
  delay. Removed the scroll/wheel/key listeners and their associated
  `onScrolled`/`markItemsAboveViewport`/`onNavigateNoScrollbar`/
  `markAllVisibleAsRead` methods from `NewsTableControl`.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsTableControl.java`

- **Refreshing a feed with new items marked the new items as read**
  When new items arrived and the info bar's "refresh" link was clicked (or a
  background refresh occurred while the newspaper view was hidden/minimized),
  the browser view reloaded but kept the scroll bar at its pre-refresh
  position. Since new items are prepended above the previously-read content,
  that old absolute scroll offset no longer corresponded to the same items 
  the next scroll-based mark-read evaluation would then treat everything
  above the (now misaligned) offset, including the brand-new items, as
  already read. Fixed by resetting the scroll position to the top instead of
  trying to preserve it, both for the info bar's "refresh" action and for
  the browser-viewer refresh path used when new news arrives.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserControl.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserViewer.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/FeedView.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsContentProvider.java`

- **Fast wheel/keyboard scrolling through large feeds could skip mark-as-read**
  The debounced mark-read task was guarded with `fUserInteractionTracker.isRunning()`,
  intended to avoid rescheduling work that was already in flight. In practice this
  caused fast-scroll events to be silently dropped: the tracker only fired once at
  the first event's 500ms mark, capturing the scroll position at that instant and
  missing everything scrolled past afterward. The underlying `JobTracker` already
  debounces correctly by cancelling any pending job before rescheduling with the
  latest one, so the extra guard was unnecessary and actively harmful. Removed it -
  every scroll event now reschedules the task, so it always evaluates the final
  scroll position once the user pauses.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserViewer.java`

- **Reaching the bottom of a feed didn't always mark the last item(s) as read**
  The mark-read-on-scroll evaluation only checked whether an item's top edge had
  scrolled above the viewport, or whether the last item's top edge was within the
  viewport. A last item taller than the window, or an item skipped over by a fast
  scroll between debounced evaluations, could end up never satisfying either
  condition and be left permanently unread. Added explicit "at bottom" detection
  (`scrollPosY + windowHeight >= document.body.scrollHeight`) that marks all
  remaining visible unread items as read once the scrollbar reaches the very
  bottom of the page.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserViewer.java`

- **Fast scrollbar-drag in Newspaper mode didn't mark items read in bulk**
  Mark-read-on-scroll in Newspaper mode was driven entirely by SWT-level
  `MouseWheel`/`MouseDown`/`KeyDown` events on the embedded browser control.
  Dragging the native scrollbar thumb directly doesn't generate those
  events - the gesture is handled inside the embedded browser engine's own
  scrollbar chrome and never reaches the host SWT widget - so scrolling
  through hundreds of items this way silently skipped mark-as-read entirely.
  Fixed by additionally binding a real DOM `scroll` listener inside the
  rendered page (re-injected after every page load, since a reload replaces
  the document), debounced client-side so a fast drag only triggers one
  evaluation once the scrollbar actually stops moving. On settle it calls
  back into the same debounced mark-read evaluation already used for
  mouse/keyboard interaction.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserViewer.java`

- **Single-item newspaper view never marked as read without scrollbar**
  In newspaper layout, if a feed contained only one item and the page had no
  scrollbar, the item was never automatically marked as read regardless of how
  long the user viewed it. The `UserInteractionTask` scroll-detection condition
  used `lastNewsPosY > 0` to gate the mark-read logic - which always fails when
  the item sits at the top of the page (`offsetTop == 0`). Changed to
  `lastNewsPosY >= 0` so a single fully-visible item at position zero is
  correctly treated as read after the configured delay.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserViewer.java`

- **Info bar "new items" refresh showed blank screen or stale content**
  Clicking the info bar notification that new items had arrived either produced
  a blank page or showed the same content with no new items included. Two
  separate problems: (1) the previous `refresh(true, moveToTop)` path used
  `fBrowser.refresh()` which reloads from the browser cache rather than asking
  the application server to regenerate the page, so new items were never
  included; (2) the page reload discarded the user's scroll position, jumping
  the view to the top. Fixed by replacing the reload path with `home()` (which
  calls `internalSetInput(force=true)`, forcing the server to rebuild the page
  from current content), and capturing `scrollTop` before the reload and
  restoring it via a one-shot `ProgressListener` after the page finishes
  loading.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserControl.java`

- **Switching feeds A  B  A failed to re-render the newspaper view**
  Navigating from feed A to feed B and back to A sometimes left the newspaper
  view stale or blank. The `internalSetInput` method guarded against redundant
  `setUrl` calls using `!inputUrl.equals(currentUrl)` - but after ABA the
  browser URL already matched A's URL from the first visit, so the guard
  silently skipped the `setUrl` call and the page was not re-rendered. Fixed
  by bypassing the URL equality check when `force=true`, ensuring the page is
  always regenerated when explicitly requested.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserViewer.java`

---

## [2.10.2-beta] - 2026-05-10

### New Features

- **Stale-feed filters in the feed explorer**
  Two new filter options added to the feed explorer toolbar filter menu,
  allowing feeds that have gone quiet to be surfaced quickly for review or
  cleanup.

  - **No Successful Fetch In...**  shows feeds whose last successful HTTP
    fetch is older than a user-specified number of days. Feeds that have never
    been fetched successfully (e.g. added but not yet refreshed) are excluded.
    Uses `getLastUpdateDate()`, which is set by `TrackingBL` on every
    successful reload.

  - **No New Post In...**  shows feeds whose most recent post date is older
    than a user-specified number of days. Feeds that have never had a post are
    excluded. Uses `getLastRecentNewsDate()`, which reflects the `pubDate` of
    the newest item ever seen in that feed.

  Selecting either filter opens a small input dialog where you type the number
  of days (must be a positive integer). The current threshold is shown in the
  menu label, e.g. *"No New Post In... (30 days)"*. The value is remembered
  across restarts via the new `BE_FILTER_DAYS` preference key and shared
  between both filter types.

  Files changed:
  `org.rssowl.ui/src/org/rssowl/ui/internal/views/explorer/BookMarkFilter.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/views/explorer/BookMarkExplorer.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/views/explorer/Messages.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/views/explorer/messages.properties`
  `org.rssowl.core/src/org/rssowl/core/persist/pref/Preference.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/pref/DefaultPreferences.java`

---


### Bug Fixes

- **GDI handle exhaustion after prolonged use** (#94/#131)
  `NewsTableLabelProvider` had two `dispose()` methods - the original at the
  bottom of the class (no null guard, no `super.dispose()`) and a corrected
  version added near the top (null-guards `fResources`, calls `super.dispose()`).
  The duplicate caused a compile failure. The old version has been removed;
  the corrected version is retained.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsTableLabelProvider.java`

- **Lucene `maxClauseCount` error dialog on startup** (#142)
  An error dialog was shown on first launch for users with many feeds because
  `BooleanQuery.setMaxClauseCount()` was only called reactively after a
  `TooManyClauses` exception was thrown during the first search. The limit is
  now set proactively in `ModelSearchImpl.startup()` before the index is opened,
  so the exception never occurs and the dialog is never shown.
  `org.rssowl.core/src/org/rssowl/core/internal/persist/search/ModelSearchImpl.java`

- **Toolbar separator causes buttons to disappear** (#180)
  When separators were present in the main toolbar, all buttons after the first
  separator were hidden after editing. The cause was that empty `ToolBarManager`
  segments - created when a separator appeared at the start of the list or
  immediately after another separator - were being added to the coolbar and
  consuming layout space without rendering, which clipped subsequent segments.
  Empty toolbar segments are now skipped before being committed to the coolbar.
  `org.rssowl.ui/src/org/rssowl/ui/internal/CoolBarAdvisor.java`

- **App not starting minimised to tray on JRE 17/21** (#158)
  The `TRAY_ON_START` preference had no effect on Eclipse 4.x builds because
  it relied on a patched `blockShellOpenOnce()` method on JFace's `Window`
  class that no longer exists. The reflection call silently failed, leaving
  the window fully visible on startup. The fallback now hides the shell
  directly before it becomes visible; `postWindowOpen()` then moves it to the
  tray once the tray icon is ready, with no visible flash.
  `org.rssowl.ui/src/org/rssowl/ui/internal/ApplicationWorkbenchWindowAdvisor.java`

### Security

- **Fix command injection in browser launch calls** (CodeQL #3 #4 #5 #6 #7  Critical, CWE-78)
  All five `Runtime.getRuntime().exec(String)` calls in `BrowserUtils.java` that
  concatenated a URL from an RSS feed directly into a shell command string have
  been replaced with the `exec(String[])` array form. Each array element is
  passed literally to the OS without shell interpretation, preventing a malicious
  URL from injecting shell metacharacters to execute arbitrary commands.
  `org.rssowl.ui/src/org/rssowl/ui/internal/util/BrowserUtils.java`

- **Replace broken MD5 digest with SHA-512 for master password key derivation** (CodeQL #1  High, CWE-327)
  MD5 was used to derive an internal encryption key from the user's master
  password before passing it to Equinox secure storage. MD5 is cryptographically
  broken and trivially brute-forceable. Replaced with SHA-512, which is a
  mandatory JVM algorithm and a drop-in replacement with no API changes required.
  `org.rssowl.ui/src/org/rssowl/ui/internal/DefaultPasswordProvider.java`

  >  **Migration note:** Users who protect feed credentials with a master
  > password will find saved credentials unreadable after this update, as the
  > derived key changes. They will be prompted to re-enter feed passwords once.
  > To avoid disruption, go to **Preferences  Credentials  Reset** before
  > upgrading. Users relying on OS-level storage (Windows DPAPI / macOS
  > Keychain) are unaffected.

- **Prevent path traversal via crafted socket message in ApplicationServer** (CodeQL #2  High, CWE-22)
  The resource path parameter extracted from incoming local socket messages was
  passed directly to `getResourceAsStream()` without validation, allowing a
  crafted message containing `../` sequences to read arbitrary classpath
  resources. A guard now rejects any parameter that contains `..`, contains a
  backslash, or does not begin with `/`.
  `org.rssowl.ui/src/org/rssowl/ui/internal/ApplicationServer.java`

### Build & CI

- **Linux-only unified build workflow**
  The multi-platform matrix build has been consolidated to a single Linux runner
  using Tycho cross-compilation, producing Windows, Linux and macOS ZIPs from
  one job. The now-redundant `build-windows.yml` workflow has been removed.
  `.github/workflows/build-all-platforms.yml`

- **P2 update site on GitHub Pages** (#165)
  The in-app update site (`Help  Check for Updates`) is now built by CI and
  published automatically on every push to `main`. All three update channels are
  live - program updates, language packs (empty, ready for future use) and
  add-ons (empty, ready for future use) - eliminating the missing repository
  errors previously shown on startup. All Xyrio upstream URLs have been replaced
  across `rssowlnix.product`, both `feature.xml` variants and `category.xml`.
  HTTPS is required for all P2 transport connections; redirects are followed
  provided TLS certificate verification passes; HTTP downgrade redirects are
  blocked.

  | Channel | URL |
  |---------|-----|
  | Program updates | `https://SethBodine.github.io/RSSOwlnix/update/program` |
  | Language packs  | `https://SethBodine.github.io/RSSOwlnix/update/nls`     |
  | Add-ons         | `https://SethBodine.github.io/RSSOwlnix/update/addons`  |

- **CodeQL workflow updated to current action versions**
  Upgraded from `codeql-action/init@v2` (Node 16) to `v3` (Node 24), replaced
  the autobuild step with an explicit Maven/Tycho build matching the main CI
  workflow, and enabled the `security-extended` query suite. Node.js 20
  deprecation warnings resolved across all workflows.
  `.github/workflows/codeql.yml`

---

### Security (additional - CodeQL batch 2)

- **Unreleased lock in `DBManager` shutdown hook** (CodeQL #8  Medium, CWE-764)
  The JVM shutdown hook acquired `fLock.writeLock().lock()` inside a `try` block
  but unlocked in an inner `finally`. If `lock()` itself threw, the outer
  `catch(Throwable)` consumed the exception but the `finally` never ran. Fixed
  using a `boolean locked` guard in a single outer `try/finally`.
  `org.rssowl.core/src/org/rssowl/core/internal/persist/service/DBManager.java`

- **Unreleased lock in `Controller` login dialog** (CodeQL #9/#10  Medium, CWE-764)
  `fLoginDialogLock.lock()` was acquired but the `try/finally` block containing
  `unlock()` only began after a cast and conditional check. If an unexpected
  exception fired in that gap the lock would be leaked. The cast at this call
  site cannot throw (the enclosing `catch` block guarantees the type), so a
  clarifying comment and structural annotation have been added. The inner
  `try/finally` guarantees unlock in all paths.
  `org.rssowl.ui/src/org/rssowl/ui/internal/Controller.java`

- **TOCTOU race in notification popup checks** (CodeQL #19/#20/#21  High, CWE-367) - accepted risk
  Three `isPopupVisible()` guard checks before calling `show()` are not atomic.
  A race could cause two notification popups to appear briefly. This is an
  accepted UI-only risk: no data is exposed and no security boundary is crossed.
  The worst outcome is a briefly doubled popup. A structural fix would require
  making `isPopupVisible()` and `show()` atomic in `NotificationService`, deferred
  to a future release. Suppression comments added explaining the rationale.
  `org.rssowl.ui/src/org/rssowl/ui/internal/ApplicationWorkbenchWindowAdvisor.java`

### Tests

- **Local information disclosure via world-readable temp files** (CodeQL #1118  Medium, CWE-732)
  Eight `File.createTempFile()` calls in test files created world-readable
  temporary files on Linux/macOS. Replaced with `Files.createTempFile()` (Java
  NIO) which sets restrictive permissions by default. Test code only - no
  production impact.
  `org.rssowl.core.tests/src/org/rssowl/core/tests/importer/ImportExportOPMLTest.java`
  `org.rssowl.core.tests/src/org/rssowl/core/tests/persist/StartupShutdownTest.java`
  `org.rssowl.core.tests/src/org/rssowl/core/tests/ui/DownloadServiceTests.java`

## [2.10.1] - 2026-05-08

### Bug Fixes

- **Systray icon lost after Windows Explorer crash**
  When Windows Explorer crashed and restarted, the system tray icon would
  disappear with no way to restore it. The application now detects external
  disposal of the tray icon and automatically recovers it, retrying every 3
  seconds for up to 5 attempts. If the window was hidden to tray at the time
  of the crash it is restored automatically.
  `org.rssowl.ui/src/org/rssowl/ui/internal/ApplicationWorkbenchWindowAdvisor.java`

- **Systray icon visible at launch before window opens on Windows 11**
  `new TrayItem()` causes the icon to appear immediately on Windows 11 before
  `setVisible(false)` is processed. Fixed by deferring the hide call with
  `asyncExec` and guarding against the `TRAY_ON_START` scenario.
  `org.rssowl.ui/src/org/rssowl/ui/internal/ApplicationWorkbenchWindowAdvisor.java`

- **Stale feed content when navigating A  B  A in Reuse Feed View mode**
  A race condition in the background content-loading job caused the wrong feed's
  articles to be displayed when navigating quickly between feeds. Fixed by
  capturing the content provider at job-submission time, adding a generation
  counter to discard stale UI updates, and skipping `refreshCache` entirely in
  `runInBackground` when the job is already superseded.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/FeedView.java`

- **Items not marked as read when scrolling past them using the scrollbar**
  Scrolling via the scrollbar did not mark bypassed items as read. A scroll
  listener now marks all items above the current top-visible item as read
  immediately when the user scrolls past them.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsTableControl.java`

- **Items not marked as read when feed fits entirely in the viewport**
  On single-page feeds (no scrollbar visible), mouse wheel scrolling and
  arrow-key navigation now mark all visible items as read, matching the
  behaviour of multi-page feeds that have been scrolled through.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsTableControl.java`

- **Single item in a feed not marked as read**
  When a feed contained only one article with no scrollbar, the mark-as-read
  tracker never fired because no selection event was dispatched. Fixed by
  auto-selecting the first item 300ms after input is set (after the internal
  block flag clears) and by marking single items read via the browser renderer.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsTableControl.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserControl.java`

- **Application takes 2+ minutes to launch on domain-joined Windows machines**
  `doHandshake()` in `Activator.java` used `InetAddress.getByName()` which
  triggers a reverse DNS lookup - blocking for up to 2 minutes on domain-joined
  machines where HKLM registry reads go through Group Policy verification. Fixed
  using `InetAddress.getLoopbackAddress()` with an explicit 5-second connect
  timeout. A db4o JVM shutdown hook was also added to ensure the database file
  lock is released on any JVM exit, preventing orphaned processes from blocking
  subsequent launches.
  `org.rssowl.ui/src/org/rssowl/ui/internal/Activator.java`
  `org.rssowl.core/src/org/rssowl/core/internal/persist/service/DBManager.java`

- **Slow startup caused by corrupt `workbench.xmi`**
  If the JVM was killed mid-write, the `workbench.xmi` UI state file could
  become corrupt, causing Eclipse to hang on startup. The application now
  validates and surgically repairs the file at startup - trimming back to the
  last well-formed closing tag and re-closing the root element - preserving as
  much window/tab/perspective state as possible. A `.corrupt.bak` backup is
  written before any modification.
  `org.rssowl.ui/src/org/rssowl/ui/internal/ApplicationWorkbenchAdvisor.java`

- **Favicon connection timeouts logged as ERROR**
  Network timeouts when fetching feed favicons were incorrectly logged at ERROR
  severity. Downgraded to INFO since timeouts are a normal network condition.
  `org.rssowl.ui/src/org/rssowl/ui/internal/Controller.java`

- **Eclipse 4.30 target platform using stale download URL**
  `download.eclipse.org/eclipse/updates/4.30` was redirecting intermittently
  and failing in CI. Pointed to the stable archive at
  `archive.eclipse.org/eclipse/updates/4.30/R-4.30-202312010110`.
  `releng/target_platform/target_platform.target`

- **`plugin.xml` share provider block had malformed XML**
  A duplicate `FeedSearch` opening tag with no matching close caused `plugin.xml`
  to fail XML validation on startup, preventing the application from registering
  its extension points.
  `org.rssowl.ui/plugin.xml`

- **`ScrollBar` import missing in `NewsTableControl`**  build failure resolved.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsTableControl.java`

- **`setState` called with single `INews` instead of `Collection`**  build failure resolved.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsBrowserControl.java`

### New Features

- **Article body / description search**
  The quick-search dropdown now includes _Find in Article Body_, allowing
  searches against the full text of article descriptions.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsFilter.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/FilterBar.java`

- **Today and This Week filter options**
  Two new time-based filters added to the show-filter dropdown: _Show Today_
  (articles since midnight) and _Show This Week_ (last 7 days). Both can be
  saved as persistent saved searches.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsFilter.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/FilterBar.java`

- **Refreshed social sharing providers**
  Removed dead services (Delicious, Technorati, Digg, StumbleUpon, Google
  Bookmarks, Mixx, FriendFeed, Newsvine, MySpace, Yahoo Buzz, Posterous).
  Renamed Twitter to X with updated share URL. Added Bluesky, Threads,
  WhatsApp, and Substack Notes. Updated Facebook, LinkedIn, and Reddit URLs.
  `org.rssowl.ui/plugin.xml`, `org.rssowl.ui/plugin.properties`

- **Database loading progress messages**
  Added status messages to the startup progress dialog for long-running
  database operations.
  `org.rssowl.core/src/org/rssowl/core/internal/persist/service/DBManager.java`

- **Centralised version management script**
  `set-version.sh` updates all version references across the codebase in a
  single command using only bash and `sed`. No Maven or other tooling required.

### Build & CI

- **GitHub Actions multi-platform build workflow**
  Added `.github/workflows/build-all-platforms.yml` producing Windows ZIP,
  Linux `.tar.gz`, and macOS `.tar.gz` packages. Windows builds run on a native
  `windows-2025` runner. On a version tag push, all three archives are attached
  to a GitHub Release automatically.

- **p2 artifact caching**
  Tycho p2 repository cached between CI runs, keyed on the target platform file.

- **SWT startup flags baked into product**
  `-Dswt.disableTabletSupport=true`, `-Dswt.enableFontHinting=false`, and
  `-Dswt.font=Segoe UI` added to `vmArgs` to reduce registry polling on
  domain-joined Windows machines.

- **SLF4J log noise suppressed**
  Added `-Dslf4j.internal.verbosity=WARN` to suppress the spurious
  `StaticLoggerBinder` warning logged at ERROR severity on startup.

### Performance

- **JVM heap tuned for faster startup**
  Initial heap increased from `-Xms15m` to `-Xms128m`. G1GC and tiered
  compilation flags added to reduce startup latency.

---

## [2.10.0] - upstream baseline

See [Xyrio/RSSOwlnix](https://github.com/Xyrio/RSSOwlnix) for changes prior to this fork.

- runs with Java 17, 21
- updated to Eclipse RCP 4.30
- updated httpclient to 5.5
- removed auto reload of feeds without data on error
- added `RSSOWLNIX_USER_AGENT` system property
- changed dead feedvalidator.org to validator.w3.org
- various bug fixes (see upstream changelog)



