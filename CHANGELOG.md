# Changelog

## [Unreleased]

Nothing currently pending.

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

## [2.10.1] - 2025-05-05

### Bug Fixes

- **Systray icon lost after Windows Explorer crash**
  When Windows Explorer crashed and restarted, the system tray icon would
  disappear with no way to restore it short of killing and relaunching the
  application. The application now detects the external disposal of the tray
  icon and automatically attempts to recover it, retrying every 3 seconds for
  up to 5 attempts to allow Explorer time to fully restart. If the window was
  hidden to the tray at the time of the crash it is automatically restored to
  the foreground so the user is not left with no way to access the application.
  `org.rssowl.ui/src/org/rssowl/ui/internal/ApplicationWorkbenchWindowAdvisor.java`

- **Stale feed content when navigating A  B  A in Reuse Feed View mode**
  Navigating from one feed to another and back again would display the content
  of the intermediate feed rather than refreshing back to the original. The root
  cause was a race condition in the background content-loading job: the job
  captured the content provider as a field reference rather than a snapshot, so
  a rapidly submitted follow-up job could corrupt the cache of the already-active
  provider before the UI update ran. Fixed by capturing the content provider
  instance at job-submission time and adding a generation counter to discard any
  UI updates that belong to a superseded navigation.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/FeedView.java`

### New Features

- **Search article body / description content**
  The quick-search dropdown in the feed filter bar now includes a
  _Find in Article Body_ option, allowing searches against the full text of
  article descriptions rather than only headlines, authors, categories and
  labels.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsFilter.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/FilterBar.java`

- **Today and This Week filter options**
  Two new time-based filters have been added to the show-filter dropdown:
  _Show Today_ (articles since midnight) and _Show This Week_ (articles within
  the last 7 days). Both can also be promoted to saved searches via the existing
  Save as Search option.
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/NewsFilter.java`
  `org.rssowl.ui/src/org/rssowl/ui/internal/editors/feed/FilterBar.java`

### Build & CI

- **GitHub Actions workflow for Windows packaging**
  Added `.github/workflows/build-windows.yml` which uses the existing
  Tycho/Maven build cross-compiled on an Ubuntu runner to produce a Windows
  x86_64 ZIP. The ZIP is uploaded as a workflow artifact on every push to
  `main` and automatically attached to a GitHub Release when a version tag
  (e.g. `v2.10.1`) is pushed. Pre-release tags (`-beta`, `-rc`) are marked
  accordingly. No code-signing certificate is required.

- **Eclipse 4.30 target platform pointed to archive**
  The target platform repository URL was updated from `download.eclipse.org`
  to `archive.eclipse.org/eclipse/updates/4.30/R-4.30-202312010110` so that
  the build resolves against the stable archived release rather than the live
  redirecting URL which caused intermittent download failures in CI.
  `releng/target_platform/target_platform.target`

- **p2 artifact caching added to CI workflow**
  The Tycho p2 local repository (`~/.m2/repository/p2`) is now cached between
  workflow runs keyed on the target platform file, preventing repeated downloads
  of Eclipse platform artifacts from the archive on every build.

### Developer Tooling

- **`set-version.sh`  centralised version management script**
  A bash script has been added to the repository root that updates all version
  references across the codebase in a single command. Requires only a standard
  bash shell and `sed`  no Maven or other tooling needed on the editing
  machine. Usage:

  ```bash
  ./set-version.sh 2.10.1           # full release
  ./set-version.sh 2.11.0 beta      # pre-release with label
  ```

  Files updated by the script:
  - `org.rssowl.core/META-INF/MANIFEST.MF`
  - `org.rssowl.ui/META-INF/MANIFEST.MF`
  - `org.rssowl.core.tests/META-INF/MANIFEST.MF`
  - `org.rssowl.lib.httpclient/META-INF/MANIFEST.MF`
  - All `feature.xml` and `category.xml` files
  - `releng/product/rssowlnix.product`
  - `releng/product_manual_export/rssowlnix.product`
  - `org.rssowl.ui/config.ini`
  - `org.rssowl.ui/plugin.xml`

---

# 2.10.0-beta
- runs with java 17,21 [#116](https://github.com/Xyrio/RSSOwlnix/issues/116)
- updated to eclipse rcp 4.30
- updated httpclient to 5.5
- removed auto reload of feeds without any data and error on viewing feed or the folder(s) feed is in [#118](https://github.com/Xyrio/RSSOwlnix/issues/118)
- added RSSOWLNIX_USER_AGENT system property to change the User-Agent used when requesting feeds to match demands of websites for popular browsers [#164](https://github.com/Xyrio/RSSOwlnix/issues/164)
- changed name reverse sorting to reverse only word positions so that numbers are sorted as usual
- change dead feedvalidator.org to validator.w3.org [#124](https://github.com/Xyrio/RSSOwlnix/issues/124)
- fix BookMark.setLastRecentNewsDate(Date) not allowing null [#178](https://github.com/Xyrio/RSSOwlnix/issues/178)

# 2.9.0-beta
- runs also with java 15, **does not run with java 16 [#116](https://github.com/Xyrio/RSSOwlnix/issues/116)** 
- added more internal information to: Feed / Properties / Status
- added group feeds by: Latest Date (Modified,Published,Received), Last Update date of feed
- added JSON Feed 1.1 support as described by [jsonfeed.org](https://jsonfeed.org) [example](https://jsonfeed.org/feed.json) [#68](https://github.com/Xyrio/RSSOwlnix/issues/68) [PR#98](https://github.com/Xyrio/RSSOwlnix/pull/98)
- added feeds:// for https:// (also feed:https:// is still supported)
- improvement to keep http or https accordingly when reconstructing urls (favico,etc)
- support more media tags from youtube, peertube [#74](https://github.com/Xyrio/RSSOwlnix/issues/74)
- fixed missing description when <content:encode> exists but has no text to not overwrite what was already loaded from <description> [#56](https://github.com/Xyrio/RSSOwlnix/issues/56)
- updated share links: removed Mister Wong, Google+ and added BibSonomy [#95](https://github.com/Xyrio/RSSOwlnix/issues/95)
- fixed progress button at bottom right to show new Download & Activities view (eclipse ProgressView) toggleable from Menu/View/Downloads & Activity. Kept old view at Menu/Tools/Downloads & Activity for now. [#45](https://github.com/Xyrio/RSSOwlnix/issues/45)
- changed page size preference for newspaper/headlines view to be setable to any value [#91](https://github.com/Xyrio/RSSOwlnix/issues/91)
- fixed missing Overview/Network Connections in Preferrences [#67](https://github.com/Xyrio/RSSOwlnix/issues/67)

# 2.8.0-beta
- improved favico search
- updated httpclient to 4.5.12
- added shell:// protocol for feed links to retrieve or transform rss using external scripts or programs. example shell://python html2rss.py https://website/

# 2.7.1-beta
- runs also with java 13, 14
- fixed null news in feed when doing cleanup [#64](https://github.com/Xyrio/RSSOwlnix/issues/64)
- fixed different behaviour when adding new feed from toolbar [#42](https://github.com/Xyrio/RSSOwlnix/issues/42)
- updated rssowl news to use RSSOwlnix's update.rss [PR#59](https://github.com/Xyrio/RSSOwlnix/pull/59)

# 2.7.0-beta
- runs also with java 12
- updated eclipse rcp to 4.9.1 (last rcp supporting 32bit) (no babel localization for 4.9+)
- Added Telegram to options for sharing links [#33](https://github.com/Xyrio/RSSOwlnix/issues/33) [PR#38](https://github.com/Xyrio/RSSOwlnix/pull/38)

# 2.6.1-beta
- fixed wrong sticky news counting when doing a cleanup [#22](https://github.com/Xyrio/RSSOwlnix/issues/22)

# 2.6.0-beta
- fixed some website authentication and authentication secure storage related bugs since updating httpclient to 4.x
- added support for feed links using https like `feed:https://host/rss.xml` ( https://en.wikipedia.org/wiki/Feed_URI_scheme )
- added right to left sorting for search dialog title column too

# 2.5.5-beta
- fixed type check for preferences

# 2.5.4-beta
- updated httpclient to 4.5.6

# 2.5.3-beta
- updated eclipse rcp to 4.7.3a

# 2.5.2-beta
- fixed missing 256x256 program logo icon [#14](https://github.com/Xyrio/RSSOwlnix/issues/14)

# 2.5.1-beta
- added right to left sorting for title column of classic view (appends "<-" to Title column)
- linux: do not force xulrunner [PR#21](https://github.com/Xyrio/RSSOwlnix/pull/21)

# 2.5.0-beta
- main program is now updateable (addons and translation available through Help/Install new Software...) * does not work correctly
- renamed to RSSOwlnix
- updated httpclient to 4.5.5
- updated eclipse rcp to 4.7.2

# 2.4.0-beta
- removed old update manager and added new p2 one (addons work so far)

# 2.3.0-beta
- runs also with java 9
- https websites which needed JCE should work without it when jre 9+ is used. https://sourceforge.net/p/rssowl/discussion/296910/thread/6dc4a203/
- updated eclipse rcp to 4.7
- updated httpclient to 4.5.3
- fixed 2 (maybe) memory leaks

based on RSSOwl 2.2.1

