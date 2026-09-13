# HTML chat theme — pack reference

Load: `search_rule_help` → `scope=skill:html-chat-theme` → `doc=htmlChatThemeHelp`.

Companion: shell Bridge / `applyState` → `doc=htmlChatUiHelp`.

## Pack layout

```text
theme.json          # id, name, version, dirty, lastCommit*
styles.css          # required for a listed theme
index.html          # optional user shell (else shared assets shell)
app.js              # optional user logic (else shared assets app.js)
fragments/          # slot HTML (known + custom)
media/              # fonts / images (one level)
commits.log         # append-only
```

| Location | Role |
|----------|------|
| `assets/web/aichat/themes/{id}/` | Built-in CSS (+ fragments); **read-only** |
| `assets/web/aichat/` | Shared default `index.html` / `app.js` / base CSS |
| `assets/web/aichat/starters/blank/` | Blank seed (not a listed built-in) |
| `files/aichat-themes/{id}/` | User overlay — **all tool writes land here** |

Active id: preference `aiChatHtmlThemeId`.

## Versioning

| Action | Effect |
|--------|--------|
| Settings Save / write without commit | `dirty`, version unchanged |
| Settings **Commit** | `dirty=false`, `version + 1`, log line |
| Commit in Settings / **commit_html_chat_theme** | `dirty=false`, `version + 1`, log line |

## Tools

### `list_html_chat_themes`

→ `{ success, count, themes:[{id,name,version,builtin,selected,dirty}] }`

### `read_file` (theme://)

- Path `theme://{id}` (no file): manifest + `paths` + advisory `requiredIdsBySlot`
- Path `theme://{id}/{file}`: text body; `offset` (1-based, default 1), `limit` (default 400)
- Binary `media/*` (non-svg): metadata only

Allowed paths: `styles.css` | `theme.json` | `index.html` | `app.js` | `fragments/{slot}.html` | `media/{file}`

File edits use the same URI scheme: `edit_file` / `write_file` / `delete_file` on `theme://{id}/{file}` (writes seed a new pack from `seedFrom` when the theme is new).


### `set_html_chat_theme`

`themeId` → activate only.

### Settings UI

Copy builtin→user, Create from blank, zip import/export, browse/edit, Commit when dirty.

## Fragments & slots

- File: `fragments/{slot}.html`, slot `^[a-z][a-z0-9_]{0,31}$`
- Mount: any element with `data-slot="{slot}"` in the **active** shell; `applyTheme` injects by key (known + custom)
- Sanitized: strip `<script>`, `on*=`, `javascript:`
- Custom slots are first-class — use them for banners, reading aids, status strips, etc.

### Known slots (stock shell map — advisory)

| Slot | Stock ids (if you keep default `app.js`) |
|------|------------------------------------------|
| `sidebar` | `conversation-list`, `btn-new-chat`, `btn-back`, `btn-settings` |
| `right_drawer` | `right-drawer-body` |
| `composer` | `input`, `btn-send`, `btn-stop`, `btn-attach`, `btn-at-mention`, `btn-thinking`, `btn-composer-more`, `composer-tools-panel` |
| `topbar` | `btn-toggle-sidebar`, `current-title`, `current-meta`, `btn-toggle-right` |
| `message_area` | `message-list` |
| `galgame_hud` | `galgame-hud-body`, `btn-galgame-toggle` |
| other / custom | _(none)_ |

**Overlay panels** (inside `#composer-overlays`, float above input): `tool-panel`, `interactive-panels`, `galgame-hud`, `suggestions`, `slash-menu`, `mention-menu`. These must live in a positioned container inside `<footer class="composer">`, not in the main flex flow, or the `flex:1` message area will push them off-screen.

Missing known ids → **warning only**. Free shells bind whatever DOM they declare in `app.js`.

Useful stock extras (still optional): `btn-theme`, `btn-open-right`, `btn-ai-help`, `btn-continue`, `composer-toolbar`, `attachment-chips`, `btn-galgame-regen`, `btn-galgame-recreate`, `btn-close-right`, `composer-more-menu`, `thinking-menu`, `composer-overlays`.

## Shell validation

Unless `skipShellValidation=true`:

- `index.html`: must reference `theme.css` and `app.js` (string check). DOM ids not required.
- `app.js`: must mention `LegadoBridge`, `postIntent`, `LegadoAiChat`, `applyState`.

## Media

- `media/{filename}` only (one segment)
- Ext: `png|jpg|jpeg|webp|gif|svg|woff|woff2|ttf|otf|ico`
- CSS: `url(media/foo.woff2)` — WebView intercepts
- Binaries: zip import/export; AI writes text/svg only

## CSS hooks (starting points, not a cage)

Stock `:root` tokens you can restyle or replace:

`--bg` `--surface` `--text` `--muted` `--border` `--accent` `--accent-text`
`--user-bg` `--assistant-bg` `--danger` `--shadow` `--radius` `--safe-top` `--safe-bottom`

(`prefers-color-scheme: dark` block exists in stock CSS — keep or redesign.)

If you **keep** the default flex shell, `.message-list` / `.conversation-list` need a scrollport (`min-height: 0; overflow-y: auto`) or the pane collapses. New layouts: own the scroll model yourself.

You may add classes, redefine structure via fragments/shell, or ignore unused stock selectors — dead CSS is fine; conflicting half-overrides are not.

## Design guidance (limits)

**Do**

- Match intensity to the ask (tokens → fragments → shell).
- One clear visual direction; use `media/` for real type/imagery when it matters.
- Preserve send / stop / history / tool-confirm affordances somehow (labels and placement are yours).

**Don’t**

- Reimplement outline / world-book / memory editors in HTML — `openSheet`.
- Depend on remote fonts/scripts.
- Treat advisory ids as hard failures or copy the entire default DOM “just in case” when building a free shell.

## Forbidden

- Writing APK built-in assets
- Network fetches / CDN scripts inside themes
- Scripts or handlers in fragments
- Dropping the `app.js` capability contract without an intentional skip
