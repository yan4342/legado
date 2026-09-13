---
name: html-chat-theme
description: Design and hot-patch app HTML AI-chat theme packs via path-based file edits. Use when the user asks to restyle the HTML chat UI or create a custom theme.
mode: chat
resources: htmlChatThemeHelp, htmlChatUiHelp
---

# HTML chat theme

## Goal

Ship a **user** theme under `files/aichat-themes/{id}/` and activate it. Built-in `default` / `ink` are read-only; tools only write user overlays (same id is fine).

## What you own vs what the app owns

| You invent freely | App keeps fixed |
|-------------------|-----------------|
| Colors, type, spacing, motion, chrome layout | Path whitelist & zip/media rules |
| Message / sidebar / composer **look** | `LegadoBridge` + whitelisted `postIntent` |
| Custom `data-slot` regions & fragments | Compose sheets (outline, workspace, …) |
| Optional full `index.html` / `app.js` rewrite | Slim `applyState` / `applyTheme` push |
| Visual metaphors, density, information hierarchy | No remote CDN / network theme fetches |

Do **not** flatten every theme into the default shell clone. Match the user’s ask: a color restyle ≠ a new information architecture.

## Intensity ladder (pick the shallowest that fits)

1. **Tokens / CSS** — `:root` vars, component classes in `styles.css`. Prefer `edits`.
2. **Fragments** — swap chrome HTML for known or custom slots; keep shared `app.js`.
3. **Shell** — user `index.html` (+ optional `app.js`) when layout or behavior must diverge. Seed with `seedFrom=blank` (or copy builtin → user in Settings).
4. Only then deep-edit `app.js`. Keep the capability contract unless the user explicitly wants a radical fork (`skipShellValidation`).

## Tools

Theme files are edited through the generic file tools with `theme://{themeId}/{path}` URIs. Enable the `file` group (auto-enabled with `html_theme`); theme:// paths also require the `html_theme` group.

1. `list_html_chat_themes`
2. `read_file` — path `theme://{id}` → manifest + paths; with a file path → body (`offset` / `limit`); `grep`=<regex> → search within a file
3. `edit_file` — path `theme://{id}/{file}`; StrReplace on one file. **Takes effect immediately (working tree = live).** Requires confirmation.
4. `write_file` — path `theme://{id}/{file}`; create/overwrite one file. `seedFrom=blank` (or a builtin/user id) seeds a new pack. **Takes effect immediately.** Requires confirmation.
5. `delete_file` — path `theme://{id}/{file}`; delete one overlay file. styles.css is protected. **Takes effect immediately.** Requires confirmation.
6. `diff_html_chat_themes` — compare a file between two themes, or between two versions (`versionA`/`versionB`)
7. `commit_html_chat_theme` — snapshot working tree, bump version, clear dirty. **Does NOT affect runtime (already live). Only when user asks to save/version.** Requires confirmation.
8. `list_html_chat_theme_versions` — commit history: versions, messages, timestamps, dirty flag
9. `rollback_html_chat_theme` — restore working tree from a snapshot, auto-commit. Requires confirmation.
10. `set_html_chat_theme`
11. `download_image` — download an image from URL → local file. Supports PNG/JPEG/WebP/GIF/SVG (sanitized). Requires confirmation.
12. `search_rule_help` — pull pack or UI docs on demand

## Workflow

Code-style: **read → edit/write/delete → read(verify) → optionally commit**.

1. `list_html_chat_themes` → choose target / template (`dirty` = working tree differs from snapshot, but **runtime always reflects working tree**).
2. `diff_html_chat_themes` between themes to understand differences.
3. `read_file` with concrete `theme://{id}/{path}`; use `grep` to pinpoint; raise `limit` / page with `offset` if truncated.
4. Unsure about Bridge / `applyState` / sheets → `doc=htmlChatUiHelp`. Pack paths / slots / media → `doc=htmlChatThemeHelp`.
5. **One `edit_file` per change.** Make old_string long enough to be unique. Retry on 0/N matches — widen context, not the whole file.
6. Change appears immediately in the WebView. No commit needed.
7. `write_file` for new files. `delete_file` for removals.
8. `read_file` (with `grep` on the changed area) to verify each edit.
9. If user explicitly asks to save/version, call `commit_html_chat_theme`. If wrong, `rollback_html_chat_theme`.
10. Summarize what changed; do not dump full files into chat.

## Hard constraints (short)

- Paths only: `styles.css`, `theme.json`, `index.html`, `app.js`, `fragments/{slot}.html`, `media/{file}`.
- Binary media (non-svg) → zip import, not AI `writes`.
- Fragments: no `<script>` / `on*=` / `javascript:` (sanitized). Known-slot ids → **warnings**, not blockers.
- `app.js` must mention `LegadoBridge`, `postIntent`, `LegadoAiChat`, `applyState` (unless skipping validation).
- Never invent `postIntent` types or skip compress confirm (`openSheet("compress")` first).
- On edit miss: use returned `suggestedOffset` / `snippet` / `hint` — re-read, then retry. Prefer `dryRun` before large multi-file patches.
- Prefer progressive docs; don’t paste entire references into replies.

## Creative license

- Re-skin, rearrange chrome, add custom slots, invent density / reading-mode / editorial looks.
- Free shells bind **any** DOM; default ids (`#input`, `#message-list`, …) are a **map for the stock app.js**, not a cage.
- Ignore unknown `applyState` fields; use only what your UI needs.
- Leave heavy editors (outline body, world book, …) to `openSheet` — theme owns presentation chrome, not duplicate product surfaces.

## Images

The app supports AI-generated images, downloaded images, and SVG code rendered as images in messages.

### Image sources

- **Stream images** — when model outputs inline images (content_part / inlineData), the app saves them as `AiMessagePart.Image` with `localPath` + `mimeType`.
- **SVG in text** — when model outputs SVG code in markdown code blocks (` ```svg `) or raw `<svg>` tags, the app detects and renders them as inline images.
- **`download_image`** tool — fetch URL → stored in `app-private ai_images/`. Returns local path, dimensions, mime, size.
- **User-uploaded images** — separate `Attachment` part, not `Image`.

### Data flow to HTML

`AiChatHtmlStateMapper.projectPart()` projects image parts into the state JSON:

| Field | Type | Description |
|-------|------|-------------|
| `kind` | `"image"` | Always for image parts |
| `localPath` | string | Absolute file path on device |
| `mimeType` | string | e.g. `"image/svg+xml"`, `"image/png"` |
| `svgText` | string? | For SVG only — sanitized inline SVG source. Read from file with `sanitizeSvg()`. |
| `remoteUrl` | string? | Original URL if downloaded |
| `width` / `height` | int? | Pixel dimensions if known |

### HTML rendering (`app.js`)

**`formatBody()`** handles image parts and SVG-in-text:

- **`part.kind === "image"`** → raster: `<img src="file://{localPath}">` in `.msg-image` div. SVG: calls `renderSvgCard(part.svgText, part.localPath)` which inlines the sanitized `<svg>` element.
- **`part.kind === "text"`** → `extractSvgs(text)` scans for SVG code blocks (` ```svg `) and raw `<svg>…</svg>` tags, splits into text+SVG segments. SVGs render via `renderSvgCard()`.
- `renderSvgCard()` writes the SVG source into `svgCodeStore` keyed by numeric ID. Buttons reference only the ID (`data-svg-copy-id`, `data-svg-save-id` + `data-svg-save-type`), avoiding HTML-attribute encoding issues with newlines/angle-brackets in raw SVG.

**Image part sample JSON:**

```json
{
  "kind": "image",
  "localPath": "/data/data/io.legado.app/files/ai_images/uuid.svg",
  "mimeType": "image/svg+xml",
  "svgText": "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">...</svg>"
}
```

### Action buttons

Every rendered SVG image gets two buttons under `.svg-actions`:

| Button | Class | Behavior |
|--------|-------|----------|
| 复制代码 (Copy) | `.svg-copy-btn` | Copies SVG source to clipboard via `LegadoBridge.copyText()` |
| 保存图片 (Save) | `.svg-save-btn` | Saves as PNG to `Pictures/legado/`. Type `"path"` → `saveImage(path)`. Type `"code"` → `saveSvgCode(code)`. |

Bridge methods involved:
- `LegadoBridge.copyText(text)` — clipboard
- `LegadoBridge.saveImage(localPath)` — save file by path → PNG
- `LegadoBridge.saveSvgCode(svgCode)` — save SVG source → render to PNG → save

All save paths render SVG to PNG via `androidsvg` at ~320–480dp density before writing — gallery apps don't support raw SVG.


### Storage & sanitization

- `AiChatAttachmentStore.writeImageBytes()` — SVGs saved as `.svg` (preserves source for copy), raster saved as-is.
- `AiChatAttachmentStore.sanitizeSvg()` — strips `<script>`, `<foreignObject>`, `on*=` handlers, `javascript:` before inline rendering.
- Save-to-gallery always converts to PNG (490–90 quality) in `Pictures/legado/`.

### CSS hooks

| Selector | Purpose |
|----------|---------|
| `.msg-body .msg-image` | Image wrapper margin |
| `.msg-body .msg-image img` | Raster image styling (max-width, max-height, border-radius) |
| `.msg-body .svg-container svg` | Inline SVG styling (same dims as img) |
| `.svg-container` | SVG card container (inline-block) |
| `.svg-actions` | Button row below SVG (flex, gap) |
| `.svg-actions .chip-btn` | Action button sizing (0.78rem, 3px×10px padding) |

Images are **not** theme pack files. Theme packs control chrome/layout/CSS only.

## Docs

| Doc | When |
|-----|------|
| this SKILL | Always (already loaded) |
| `htmlChatThemeHelp` | Pack layout, tools args, slots, media, CSS hooks |
| `htmlChatUiHelp` | Bridge, intents, `applyState`, sheets, free-shell binding |

## Tips

- `md.js` is a shared utility: `<script src="md.js"></script>` exposes `window.formatMarkdown()`. All themes can use it.
- Rename a theme: edit `theme.json` → change `"name"`.
- **Edits take effect immediately. `commit` is only for version snapshots.**
- Theme files can also be viewed/edited in the app's HTML theme config screen (Settings → AI → HTML Chat Theme).
