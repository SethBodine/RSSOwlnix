# Translating RSSOwlnix

RSSOwlnix's UI text lives in Eclipse `messages.properties` files inside
`org.rssowl.core` and `org.rssowl.ui`. Translations are shipped as separate
**NLS fragment** bundles under `translations/`, so you never need to
touch the main source code — you're only ever adding or editing
`.properties` files.

If a translated string is missing, Eclipse silently falls back to English,
so an incomplete translation is still safe to ship — it just means some
strings won't be in your language yet.

## Before you start

You need:
- A GitHub account
- A text editor that saves files as UTF-8

You do **not** need to build the project, install Eclipse, or know Java.

## 1. Find your language's fragment folders

Check `translations/` in this repo for folders ending in your language
code (e.g. `org.rssowl.core.nls.de` and `org.rssowl.ui.nls.de` for
German; `org.rssowl.core.nls.zhcn` and `org.rssowl.ui.nls.zhcn` for
Simplified Chinese — the two Chinese variants use `zhcn`/`zhtw` in
folder names, but the actual `.properties` files inside still use the
real locale suffix `zh_CN`/`zh_TW`). If your language doesn't have
folders yet, see
[Starting a new language](#starting-a-new-language-not-listed-here) below.

Each fragment mirrors the package structure of the main source, e.g.:

```
translations/org.rssowl.ui.nls.de/org/rssowl/ui/internal/dialogs/welcome/messages_de.properties
```

corresponds to:

```
org.rssowl.ui/src/org/rssowl/ui/internal/dialogs/welcome/messages.properties
```

Same package path, same filename, just `messages_de.properties` instead of
`messages.properties`, and only the translated key/value pairs (no need to
copy keys you haven't translated yet).

## 2. Find keys that need translating

Run the helper script from the repo root:

```bash
./tools/find-missing-translations.sh de
```

This prints every key that exists in the English source but is missing (or
identical to English, which usually means "not yet translated") from your
language's fragment files. Pipe it to a file to work through it:

```bash
./tools/find-missing-translations.sh de > de-todo.txt
```

If you don't want to run a script, you can also just open the English
`messages.properties` file for the screen you want to translate (e.g.
`org.rssowl.ui/src/.../dialogs/welcome/messages.properties`) and compare it
key-by-key against the matching `messages_XX.properties` in your language
folder.

## 3. Translate

- Keep the key name on the left of `=` exactly as-is. Only change the value.
- Preserve any `{0}`, `{1}`, etc. placeholders — these get filled in with
  dynamic values (feed names, counts, dates) at runtime, in the same order.
- Preserve `&` mnemonic markers (e.g. `&File`) where present — these define
  Alt-key shortcuts. Pick a different letter in your language if the
  English one doesn't make sense, but keep exactly one `&`.
- `.properties` files must be Latin-1/ISO-8859-1 encoded per the Java
  properties spec. Non-Latin characters (e.g. Chinese, Korean, Cyrillic)
  must be escaped as `\uXXXX`. Most editors and IDEs do this for you
  automatically when you save a `.properties` file — if yours doesn't, use
  the `native2ascii` tool (bundled with any JDK) to convert before
  committing.
- Keep line endings and trailing whitespace consistent with the rest of the
  file.

## 4. Submit a pull request

1. Fork this repo and create a branch, e.g. `translate/de-filter-dialog`.
2. Commit only the `.properties` file(s) you changed.
3. Open a PR against `main` with a short description of what you translated
   (e.g. "German: translate the day-threshold filter dialog strings added
   in 2.10.4").
4. CI will build the `update-nls` P2 site automatically; no manual build
   needed for review.

Small, focused PRs (one screen or one feature at a time) are easier to
review than one giant PR translating everything at once — feel free to
submit as many small PRs as you like.

## Starting a new language not listed here

1. Copy the three existing German fragment/feature folders under
   `translations/` (`org.rssowl.core.nls.de`, `org.rssowl.ui.nls.de`,
   `org.rssowl.feature.nls.de`) to new folders using your language's
   [ISO 639-1](https://en.wikipedia.org/wiki/List_of_ISO_639_language_codes)
   code, e.g. `org.rssowl.core.nls.sv` for Swedish (or a short lowercase
   form without the underscore for regional variants, matching the
   existing `zhcn`/`zhtw` pattern, e.g. `ptbr` for `pt_BR` — the folder
   name and the `messages_XX.properties` locale suffix don't have to
   match exactly, but keep them close). These stay directly under
   `translations/` — no per-language subfolder, matching how `bundles/`
   and `features/` are laid out (tycho-pomless 1.1.0, as pinned in
   `.mvn/extensions.xml`, only resolves a module's parent one directory
   level up with no override available).
2. Rename each fragment's `messages_de.properties` files to your
   language's real locale suffix (e.g. `messages_sv.properties`), and
   delete the translated values inside (leave the English fallback out —
   untranslated keys should simply not appear in the file).
3. Update `Bundle-SymbolicName` and `Fragment-Host` in each
   `META-INF/MANIFEST.MF`, and `id`/`label` in `feature.xml`, to match
   your language code.
4. Add your three new modules to `translations/pom.xml` and a
   `<feature>` entry to `releng/update-nls/category.xml` (see the
   existing entries for the exact format, or ask in your PR and a
   maintainer will help wire it up).
5. Open a PR with just the scaffolding — you don't need to translate
   everything before your first PR.

## Questions

Open an issue or start a discussion in this repo — no translation
experience required, just native or fluent familiarity with your language
and RSS readers in general.
