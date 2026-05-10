# Changelog

## [Unreleased]

### Bug Fixes

- **GDI handle exhaustion after prolonged use** (#94/#131)
  `NewsTableLabelProvider` had two `dispose()` methods  the original at the
  bottom of the class (no null guard, no `super.dispose()`) and a corrected
  version added near the top (null-guards `fResources`, calls `super.dispose()`).
  The duplicate caused a compile failure. The old version has been removed;
  the corrected version is retained.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsTableLabelProvider.java`

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

## [2.10.2-beta] - 2026-05-08

### Bug Fixes

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
  segments  created when a separator appeared at the start of the list or
  immediately after another separator  were being added to the coolbar and
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
  live  program updates, language packs (empty, ready for future use) and
  add-ons (empty, ready for future use)  eliminating the missing repository
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

- **TOCTOU race in notification popup checks** (CodeQL #19/#20/#21  High, CWE-367)  accepted risk
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
  NIO) which sets restrictive permissions by default. Test code only  no
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
  triggers a reverse DNS lookup  blocking for up to 2 minutes on domain-joined
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
  validates and surgically repairs the file at startup  trimming back to the
  last well-formed closing tag and re-closing the root element  preserving as
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



