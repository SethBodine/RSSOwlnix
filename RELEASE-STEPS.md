# Cutting a release

Exact commands to go from a pushed release branch to a published GitHub Release. See `RELEASE.md` in the repo for the full explanation of what each workflow job does and why; this file is just the checklist to follow each time.

Examples below use `1.2.3-beta` as a placeholder version and `1.2.3` as the placeholder branch name - substitute your actual version/branch throughout (e.g. `2.10.3-beta` / `2.10.3`, or drop `-beta` entirely for a stable release).

## 1. Bump the version

```bash
bash set-version.sh 1.2.3 beta
```

Updates the version string across `MANIFEST.MF`, `feature.xml`, `*.product`, `category.xml`, `config.ini`, `plugin.xml`, and the splash screen string in `Owl.java`. Omit `beta` (i.e. `bash set-version.sh 1.2.3`) for a stable release.

The script has a bash shebang and needs LF line endings to run. If it fails with something like `/usr/bin/env: 'bash\r': No such file or directory`, it's picked up CRLF endings (common if checked out on Windows) - fix with:
```bash
sed -i 's/\r$//' set-version.sh
```

Commit the result:
```bash
git add -A
git commit -m "chore: bump version to 1.2.3-beta"
```

## 2. Cut the changelog

In `CHANGELOG.md`, rename the `## [Unreleased]` heading to `## [1.2.3-beta] - YYYY-MM-DD` (today's date). `generate-updates-rss.sh` parses this file directly for the in-app update feed, so the heading format matters.

```bash
git add CHANGELOG.md
git commit -m "chore: updating changelog"
```

## 3. Open the PR

```bash
gh pr create \
  --base main \
  --head 1.2.3 \
  --title "1.2.3-beta: <short summary>" \
  --body-file PR-1.2.3-beta.md
```

No `gh` CLI? Open the compare view in the browser instead:
```
https://github.com/<your-username>/RSSOwlnix/compare/main...1.2.3
```
and paste in your PR description.

Opening the PR triggers CodeQL automatically (`codeql.yml`) - informational only, not a merge gate.

## 4. Review and merge

- Wait for CodeQL to finish.
- Merge the PR into `main` (merge commit or squash, your call; squashing loses the individual commit messages, so a regular merge keeps the branch's history intact if you want it).

Merging to `main` triggers `build` + `publish-p2` automatically. This updates the P2 site and in-app update feed, but does **not** create a GitHub Release yet.

## 5. Tag the release commit

```bash
git checkout main
git pull
git tag v1.2.3-beta
git push origin v1.2.3-beta
```

This is the step that actually creates the GitHub Release. The `release` job only runs on a `v*` tag push, and marks itself prerelease automatically because the tag contains `-beta` (or `-rc`). Omit `-beta`/`-rc` in the tag for a stable release, and it publishes as a full release instead.

## 6. Watch it build

GitHub → Actions → "Build All Platforms", triggered by the tag push. Three jobs: `build`, `release`, `publish-p2`. Takes a few minutes for all three platforms.

## 7. Verify

- **Releases page**: `v1.2.3-beta` present, marked "Pre-release" if applicable, Windows/Linux/macOS archives attached, auto-generated notes look reasonable.
- **Update feed**: check `updates.rss` on GitHub Pages picked up the new version (this is what existing installs use for "Check for Updates").
- Optional: install/run a build from one platform archive as a smoke test.

## If something's wrong after tagging

```bash
git tag -d v1.2.3-beta
git push origin :refs/tags/v1.2.3-beta
gh release delete v1.2.3-beta   # or delete via the web UI
```
Fix the issue, then repeat step 5.
