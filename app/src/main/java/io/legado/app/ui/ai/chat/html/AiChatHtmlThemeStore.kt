package io.legado.app.ui.ai.chat.html

import android.content.Context
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * HTML chat theme packs — no Room/DB.
 *
 * Built-in: `assets/web/aichat/themes/{id}/`
 * User: `{filesDir}/aichat-themes/{id}/`
 */
class AiChatHtmlThemeStore(
    private val context: Context,
) {
    init {
        // Backfill md.js for existing user packs before WebView loads any page.
        val root = userThemesDir()
        if (root.isDirectory) {
            root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                ensureSharedMdJs(dir)
            }
        }
    }

    data class ThemeInfo(
        val id: String,
        val name: String,
        val version: Int = 1,
        val builtin: Boolean = false,
        val selected: Boolean = false,
        val dirty: Boolean = false,
    )

    data class ThemeManifest(
        val id: String? = null,
        val name: String? = null,
        val version: Int = 1,
        /** Working-tree has uncommitted edits (git-like). */
        val dirty: Boolean = false,
        val lastCommitMessage: String? = null,
        val lastCommitAt: Long? = null,
        /**
         * Shell provenance for the upgrade mechanism: `"builtin"` = snapshot of the shared
         * shell (safe to overwrite), `"custom"` = user wrote their own shell (runtime
         * enhancement only, never overwrite). Null on legacy packs → guessed on demand.
         */
        val shellKind: String? = null,
    )

    fun listThemes(): List<ThemeInfo> {
        val selected = AppConfig.aiChatHtmlThemeId
        val byId = LinkedHashMap<String, ThemeInfo>()
        for (info in listBuiltin()) {
            byId[info.id] = info.copy(selected = info.id == selected)
        }
        for (info in listUser()) {
            byId[info.id] = info.copy(selected = info.id == selected)
        }
        if (byId.isEmpty()) {
            byId[DEFAULT_THEME_ID] = ThemeInfo(
                id = DEFAULT_THEME_ID,
                name = "Default",
                builtin = true,
                selected = true,
            )
        } else if (byId.values.none { it.selected }) {
            val first = byId.values.first()
            byId[first.id] = first.copy(selected = true)
            AppConfig.aiChatHtmlThemeId = first.id
        }
        return byId.values.toList()
    }

    fun setTheme(id: String): Boolean {
        val normalized = id.trim()
        if (normalized.isEmpty() || !exists(normalized)) return false
        AppConfig.aiChatHtmlThemeId = normalized
        notifyChanged()
        return true
    }

    fun exists(id: String): Boolean {
        val normalized = id.trim()
        if (normalized.isEmpty()) return false
        if (File(userThemesDir(), "$normalized/styles.css").isFile) return true
        if (!readAsset("web/aichat/themes/$normalized/styles.css").isNullOrBlank()) return true
        if (normalized == DEFAULT_THEME_ID && !readAsset("web/aichat/styles.css").isNullOrBlank()) return true
        return false
    }

    fun isUserTheme(id: String): Boolean =
        File(userThemesDir(), "${id.trim()}/styles.css").isFile

    fun readActiveCss(): String {
        val id = AppConfig.aiChatHtmlThemeId.ifBlank { DEFAULT_THEME_ID }
        return readCss(id) ?: readCss(DEFAULT_THEME_ID) ?: FALLBACK_CSS
    }

    fun readCss(id: String): String? {
        val normalized = id.trim()
        val userFile = File(userThemesDir(), "$normalized/styles.css")
        if (userFile.isFile) {
            return runCatching { userFile.readText(Charsets.UTF_8) }.getOrNull()
        }
        val assetPaths = listOf(
            "web/aichat/themes/$normalized/styles.css",
            if (normalized == DEFAULT_THEME_ID) "web/aichat/styles.css" else null,
        ).filterNotNull()
        for (path in assetPaths) {
            val text = readAsset(path)
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    fun readManifest(id: String): ThemeManifest? {
        val normalized = id.trim()
        val userFile = File(userThemesDir(), "$normalized/theme.json")
        if (userFile.isFile) {
            return runCatching { userFile.readText(Charsets.UTF_8) }
                .getOrNull()
                ?.let { GSON.fromJsonObject<ThemeManifest>(it).getOrNull() }
        }
        return readAsset("web/aichat/themes/$normalized/theme.json")
            ?.let { GSON.fromJsonObject<ThemeManifest>(it).getOrNull() }
    }

    fun listFragmentSlots(id: String): List<String> {
        val normalized = id.trim()
        val userDir = File(userThemesDir(), "$normalized/fragments")
        if (userDir.isDirectory) {
            val userSlots = userDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("html", ignoreCase = true) }
                ?.map { it.nameWithoutExtension }
                ?.filter { isValidSlotName(it) }
                ?.toMutableSet()
                .orEmpty()
                .toMutableSet()
            // Merge known builtin slots that exist as assets when overlaying a named builtin id.
            for (slot in KNOWN_SLOTS) {
                if (!readAsset("web/aichat/themes/$normalized/fragments/$slot.html").isNullOrBlank()) {
                    userSlots += slot
                }
            }
            return userSlots.sorted()
        }
        val assetRoot = "web/aichat/themes/$normalized/fragments"
        return KNOWN_SLOTS.filter { slot ->
            !readAsset("$assetRoot/$slot.html").isNullOrBlank()
        }
    }

    fun readFragment(id: String, slot: String): String? {
        val normalized = id.trim()
        val slotId = slot.trim()
        if (!isValidSlotName(slotId)) return null
        val userFile = File(userThemesDir(), "$normalized/fragments/$slotId.html")
        if (userFile.isFile) {
            return runCatching { userFile.readText(Charsets.UTF_8) }.getOrNull()
        }
        if (slotId !in KNOWN_SLOTS) return null
        return readAsset("web/aichat/themes/$normalized/fragments/$slotId.html")
    }

    data class MediaMeta(
        val path: String,
        val extension: String,
        val mime: String,
        val size: Long,
    ) {
        val isImage: Boolean
            get() = extension in IMAGE_MEDIA_EXTENSIONS
        val isFont: Boolean
            get() = extension in FONT_MEDIA_EXTENSIONS
    }

    fun listMediaPaths(id: String): List<String> {
        val dir = File(userThemesDir(), "${id.trim()}/media")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && isAllowedMediaFileName(it.name) }
            ?.map { "media/${it.name}" }
            ?.sorted()
            .orEmpty()
    }

    fun mediaMeta(id: String, path: String): MediaMeta? {
        val rel = normalizePackPath(path).getOrNull() ?: return null
        if (!rel.startsWith("media/")) return null
        val file = userFileForPath(id.trim(), rel)
        if (!file.isFile) return null
        return MediaMeta(
            path = rel,
            extension = rel.substringAfterLast('.', "").lowercase(),
            mime = mimeForMedia(rel),
            size = file.length(),
        )
    }

    /**
     * True when any text file of the pack (styles.css / index.html / app.js / fragments)
     * references this media path (by full path, media/name, bare name, or name stem).
     */
    fun isMediaReferenced(id: String, path: String): Boolean {
        val rel = normalizePackPath(path).getOrNull() ?: return false
        if (!rel.startsWith("media/")) return false
        val name = rel.removePrefix("media/")
        val stem = name.substringBeforeLast('.')
        val needles = listOf(rel, "media/$name", name, stem)
        val candidates = buildList {
            add("styles.css")
            add(SHELL_INDEX)
            add(SHELL_APP_JS)
            addAll(listFragmentSlots(id).map { "fragments/$it.html" })
        }
        return candidates.any { packPath ->
            readPackFile(id, packPath)?.let { text -> needles.any { text.contains(it) } } == true
        }
    }

    fun readMediaBytes(id: String, path: String): ByteArray? {
        val rel = normalizePackPath(path).getOrNull() ?: return null
        if (!rel.startsWith("media/")) return null
        val file = userFileForPath(id.trim(), rel)
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    /** Actual on-disk file for a media path (null when not present). Used for Typeface previews. */
    fun mediaFile(id: String, path: String): File? {
        val rel = normalizePackPath(path).getOrNull() ?: return null
        if (!rel.startsWith("media/")) return null
        val file = userFileForPath(id.trim(), rel)
        return file.takeIf { it.isFile }
    }

    fun writeMediaBytes(
        id: String,
        path: String,
        bytes: ByteArray,
        notify: Boolean = true,
    ): Result<AppliedOp> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        if (!rel.startsWith("media/")) {
            return Result.failure(IllegalArgumentException("Not a media path: $path"))
        }
        if (bytes.size > MAX_MEDIA_BYTES) {
            return Result.failure(IllegalArgumentException("Media exceeds $MAX_MEDIA_BYTES bytes: $rel"))
        }
        ensureUserPack(destId).getOrElse { return Result.failure(it) }
        val file = userFileForPath(destId, rel)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        markDirty(destId)
        if (notify) notifyChanged()
        return Result.success(AppliedOp(op = "write", path = rel))
    }

    fun readActiveFragments(): Map<String, String> {
        val id = AppConfig.aiChatHtmlThemeId.ifBlank { DEFAULT_THEME_ID }
        return listFragmentSlots(id).mapNotNull { slot ->
            readFragment(id, slot)?.let { slot to it }
        }.toMap()
    }

    /** User-pack override only (null if not present). Built-ins never store shell in theme folder. */
    fun hasUserIndexHtml(id: String): Boolean =
        File(userThemesDir(), "${id.trim()}/index.html").isFile

    fun hasUserAppJs(id: String): Boolean =
        File(userThemesDir(), "${id.trim()}/app.js").isFile

    fun readUserIndexHtml(id: String): String? = readUserShellFile(id, SHELL_INDEX)

    fun readUserAppJs(id: String): String? = readUserShellFile(id, SHELL_APP_JS)

    /** Effective shell for read/seed: user override → builtin assets. */
    fun readEffectiveIndexHtml(id: String): String? =
        readUserIndexHtml(id) ?: readAsset("web/aichat/$SHELL_INDEX")

    fun readEffectiveAppJs(id: String): String? =
        readUserAppJs(id) ?: readAsset("web/aichat/$SHELL_APP_JS")

    fun activeEntryUrl(): String {
        val id = AppConfig.aiChatHtmlThemeId.ifBlank { DEFAULT_THEME_ID }
        val index = File(userThemesDir(), "$id/$SHELL_INDEX")
        return if (index.isFile) {
            "file://${index.absolutePath}"
        } else {
            ASSET_ENTRY_URL
        }
    }

    /** Changes when user shell files change → WebView must full-reload. */
    fun activeShellRevision(): String {
        val id = AppConfig.aiChatHtmlThemeId.ifBlank { DEFAULT_THEME_ID }
        val index = File(userThemesDir(), "$id/$SHELL_INDEX")
        val app = File(userThemesDir(), "$id/$SHELL_APP_JS")
        return listOf(
            id,
            if (index.isFile) index.lastModified().toString() else "0",
            if (app.isFile) app.lastModified().toString() else "0",
        ).joinToString("|")
    }

    /** Prefer user app.js; else null so caller can fall back to asset. */
    fun readActiveAppJsOrNull(): String? {
        val id = AppConfig.aiChatHtmlThemeId.ifBlank { DEFAULT_THEME_ID }
        return readUserAppJs(id)
    }

    /**
     * Shell provenance for a pack:
     * - `"builtin"` — no user shell, or shell matches the current built-in (snapshot).
     * - `"custom"` — user wrote/edited their own shell; do not overwrite.
     */
    fun shellKindOf(id: String): String {
        val normalized = id.trim()
        if (!isUserTheme(normalized)) return "builtin"
        if (!hasUserIndexHtml(normalized) && !hasUserAppJs(normalized)) return "builtin"
        val stored = readManifest(normalized)?.shellKind
        if (stored != null) return stored
        // Legacy pack without a marker: guess by comparing against the current built-in shell.
        val index = readUserIndexHtml(normalized)
        val appJs = readUserAppJs(normalized)
        val builtinIndex = readAsset("web/aichat/$SHELL_INDEX")
        val builtinApp = readAsset("web/aichat/$SHELL_APP_JS")
        val same = (index == null || index == builtinIndex) &&
            (appJs == null || appJs == builtinApp)
        return if (same) "builtin" else "custom"
    }

    /**
     * Upgrade the shared shell for a user pack.
     *
     * A-type (shellKind `"builtin"`): overwrites index.html / app.js with the latest
     * built-in shell and ensures md.js — a full, safe upgrade.
     * B-type (shellKind `"custom"`): by default never touches user files; only tags the manifest
     * so the config UI can offer the explicit replace action. New features arrive at runtime via
     * the injected shared-enhance.js. When [force] is `true` the custom shell is overwritten too
     * (the user explicitly confirmed replacing their own files).
     *
     * @return the shell kind the pack was left in ("builtin" or "custom").
     */
    fun upgradeSharedShell(id: String, force: Boolean = false): Result<String> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        if (!isUserTheme(destId)) {
            return Result.failure(IllegalArgumentException("Only user packs can be upgraded"))
        }
        val kind = shellKindOf(destId)
        if (kind == "custom" && !force) {
            writeShellKindMarker(destId, "custom")
            return Result.success("custom")
        }
        val dir = File(userThemesDir(), destId).also { it.mkdirs() }
        readAsset("web/aichat/$SHELL_INDEX")?.let {
            File(dir, SHELL_INDEX).writeText(it, Charsets.UTF_8)
        }
        readAsset("web/aichat/$SHELL_APP_JS")?.let {
            File(dir, SHELL_APP_JS).writeText(it, Charsets.UTF_8)
        }
        readAsset("web/aichat/md.js")?.let {
            File(dir, "md.js").writeText(it, Charsets.UTF_8)
        }
        bumpManifest(
            destId,
            bumpVersion = true,
            dirty = false,
            shellKind = "builtin",
        )
        notifyChanged(setOf(SHELL_INDEX, SHELL_APP_JS, "md.js"))
        return Result.success("builtin")
    }

    private fun writeShellKindMarker(id: String, kind: String) {
        val prev = readManifest(id)
        val manifest = ThemeManifest(
            id = id,
            name = prev?.name ?: id,
            version = prev?.version ?: 1,
            dirty = prev?.dirty ?: false,
            lastCommitMessage = prev?.lastCommitMessage,
            lastCommitAt = prev?.lastCommitAt,
            shellKind = kind,
        )
        val destDir = File(userThemesDir(), id).also { it.mkdirs() }
        File(destDir, "theme.json").writeText(GSON.toJson(manifest), Charsets.UTF_8)
    }

    /** Recompute and persist shellKind for packs imported/edited without a marker. */
    private fun reclassifyShellKind(id: String) {
        val normalized = id.trim()
        if (!hasUserIndexHtml(normalized) && !hasUserAppJs(normalized)) return
        val index = readUserIndexHtml(normalized)
        val appJs = readUserAppJs(normalized)
        val builtinIndex = readAsset("web/aichat/$SHELL_INDEX")
        val builtinApp = readAsset("web/aichat/$SHELL_APP_JS")
        val same = (index == null || index == builtinIndex) &&
            (appJs == null || appJs == builtinApp)
        writeShellKindMarker(normalized, if (same) "builtin" else "custom")
    }

    private fun readUserShellFile(id: String, fileName: String): String? {
        val file = File(userThemesDir(), "${id.trim()}/$fileName")
        if (!file.isFile) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    /** Write shared md.js from assets into a user pack dir (public utility, theme-agnostic). */
    private fun ensureSharedMdJs(destDir: File) {
        val mdFile = File(destDir, "md.js")
        if (mdFile.isFile) return
        val content = readAsset("web/aichat/md.js") ?: return
        mdFile.writeText(content, Charsets.UTF_8)
    }

    fun copyBuiltinToUser(sourceId: String, targetId: String? = null, name: String? = null): Result<ThemeInfo> {
        val src = sourceId.trim()
        if (!exists(src)) return Result.failure(IllegalArgumentException("Theme not found: $src"))
        val destId = (targetId?.trim()?.takeIf { it.isNotBlank() } ?: "${src}-copy")
            .replace(Regex("[^a-zA-Z0-9_-]"), "-")
            .lowercase()
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid target id"))
        val css = readCss(src) ?: return Result.failure(IllegalArgumentException("Missing styles.css"))
        val manifest = readManifest(src)
        val destDir = File(userThemesDir(), destId).also { it.mkdirs() }
        File(destDir, "styles.css").writeText(css, Charsets.UTF_8)
        // Seed editable shell from effective sources (user overlay of src, else assets).
        readEffectiveIndexHtml(src)?.let { File(destDir, SHELL_INDEX).writeText(it, Charsets.UTF_8) }
        readEffectiveAppJs(src)?.let { File(destDir, SHELL_APP_JS).writeText(it, Charsets.UTF_8) }
        ensureSharedMdJs(destDir)
        val newManifest = ThemeManifest(
            id = destId,
            name = name?.takeIf { it.isNotBlank() } ?: "${manifest?.name ?: src} (copy)",
            version = (manifest?.version ?: 1),
            shellKind = "builtin",
        )
        File(destDir, "theme.json").writeText(GSON.toJson(newManifest), Charsets.UTF_8)
        val fragDir = File(destDir, "fragments").also { it.mkdirs() }
        for (slot in listFragmentSlots(src)) {
            val html = readFragment(src, slot) ?: continue
            File(fragDir, "$slot.html").writeText(sanitizeHtmlFragment(html), Charsets.UTF_8)
        }
        notifyChanged()
        return Result.success(
            ThemeInfo(id = destId, name = newManifest.name ?: destId, version = newManifest.version, builtin = false),
        )
    }

    /**
     * Import a single local file into a user pack by display name / extension.
     * Maps: styles.css|theme.json|index.html|app.js → root;
     * `{slot}.html` (valid slot) → fragments/; media extensions → media/.
     */
    fun importLocalFile(
        id: String,
        displayName: String,
        bytes: ByteArray,
        notify: Boolean = true,
    ): Result<AppliedOp> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        if (!isUserTheme(destId)) {
            return Result.failure(IllegalArgumentException("Import into a user pack only (copy builtin first)"))
        }
        val rawName = displayName.trim().substringAfterLast('/').substringAfterLast('\\')
        if (rawName.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing file name"))
        }
        val lower = rawName.lowercase()
        val rel = when {
            lower in setOf("styles.css", "theme.json", SHELL_INDEX, SHELL_APP_JS) -> lower
            lower.endsWith(".html") -> {
                val slot = lower.removeSuffix(".html")
                if (!isValidSlotName(slot)) {
                    return Result.failure(
                        IllegalArgumentException(
                            "Invalid fragment name: $rawName (use lowercase slot like composer.html)",
                        ),
                    )
                }
                "fragments/$slot.html"
            }
            isAllowedMediaFileName(rawName) || isAllowedMediaFileName(sanitizeMediaFileName(rawName)) -> {
                val safe = if (isAllowedMediaFileName(rawName)) rawName else sanitizeMediaFileName(rawName)
                "media/$safe"
            }
            else -> return Result.failure(
                IllegalArgumentException(
                    "Unsupported file: $rawName. Allowed: styles.css, theme.json, index.html, app.js, " +
                        "{slot}.html, or media types (png/jpg/webp/gif/svg/woff/woff2/ttf/otf/ico)",
                ),
            )
        }
        return if (rel.startsWith("media/") && !isTextMediaPath(rel)) {
            writeMediaBytes(destId, rel, bytes, notify = notify)
        } else {
            val text = bytes.toString(Charsets.UTF_8)
            writePackFile(
                destId,
                rel,
                text,
                skipShellValidation = false,
                bumpVersion = false,
                notify = notify,
            )
        }
    }

    fun deleteUser(id: String): Boolean {
        val normalized = id.trim()
        if (normalized.isEmpty() || !isUserTheme(normalized)) return false
        val dir = File(userThemesDir(), normalized)
        val deleted = dir.deleteRecursively()
        if (AppConfig.aiChatHtmlThemeId == normalized) {
            AppConfig.aiChatHtmlThemeId = DEFAULT_THEME_ID
        }
        if (deleted) notifyChanged()
        return deleted
    }

    data class FileSlice(
        val path: String,
        val content: String,
        val totalLines: Int,
        val offset: Int,
        val limit: Int,
        val truncated: Boolean,
    )

    data class AppliedOp(
        val op: String,
        val path: String,
        val replacements: Int = 0,
        val warnings: List<String> = emptyList(),
    )

    /** Structured patch failure for AI-retry (path, matchCount, snippet, offset). */
    class PatchOpException(
        message: String,
        val path: String? = null,
        val matchCount: Int? = null,
        val suggestedOffset: Int? = null,
        val snippet: String? = null,
        val hint: String? = null,
    ) : IllegalArgumentException(message) {
        fun toErrorMap(): Map<String, Any?> = buildMap {
            put("success", false)
            put("error", message ?: "patch failed")
            path?.let { put("path", it) }
            matchCount?.let { put("matchCount", it) }
            suggestedOffset?.let { put("suggestedOffset", it) }
            snippet?.takeIf { it.isNotBlank() }?.let { put("snippet", it) }
            hint?.takeIf { it.isNotBlank() }?.let { put("hint", it) }
        }
    }

    data class EditApplyResult(
        val content: String,
        val replacements: Int,
    )

    fun normalizeThemeId(id: String): String =
        id.trim().replace(Regex("[^a-zA-Z0-9_-]"), "-").lowercase()

    /** Effective pack paths that currently resolve to content. */
    fun listPackPaths(id: String): List<String> {
        val normalized = id.trim()
        if (!exists(normalized) && !isUserTheme(normalized)) return emptyList()
        val paths = mutableListOf<String>()
        if (!readPackFile(normalized, "styles.css").isNullOrBlank()) paths += "styles.css"
        if (readManifest(normalized) != null ||
            File(userThemesDir(), "$normalized/theme.json").isFile
        ) {
            paths += "theme.json"
        }
        if (!readPackFile(normalized, SHELL_INDEX).isNullOrBlank()) paths += SHELL_INDEX
        if (!readPackFile(normalized, SHELL_APP_JS).isNullOrBlank()) paths += SHELL_APP_JS
        for (slot in listFragmentSlots(normalized)) {
            paths += "fragments/$slot.html"
        }
        val userFrag = File(userThemesDir(), "$normalized/fragments")
        if (userFrag.isDirectory) {
            userFrag.listFiles()
                ?.filter { it.isFile && it.extension.equals("html", true) }
                ?.map { "fragments/${it.nameWithoutExtension}.html" }
                ?.filter { path ->
                    path !in paths && fragmentSlotFromPath(path)?.let { isValidSlotName(it) } == true
                }
                ?.let { paths.addAll(it) }
        }
        paths.addAll(listMediaPaths(normalized))
        return paths.distinct()
    }

    fun readPackFile(id: String, path: String): String? {
        val normalized = id.trim()
        val rel = normalizePackPath(path).getOrElse { return null }
        return when {
            rel == "styles.css" -> readCss(normalized)
            rel == "theme.json" -> {
                val user = File(userThemesDir(), "$normalized/theme.json")
                when {
                    user.isFile -> runCatching { user.readText(Charsets.UTF_8) }.getOrNull()
                    else -> readAsset("web/aichat/themes/$normalized/theme.json")
                }
            }
            rel == SHELL_INDEX -> readEffectiveIndexHtml(normalized)
            rel == SHELL_APP_JS -> readEffectiveAppJs(normalized)
            rel.startsWith("fragments/") -> {
                val slot = fragmentSlotFromPath(rel) ?: return null
                readFragment(normalized, slot)
            }
            rel.startsWith("media/") -> {
                if (!isTextMediaPath(rel)) return null
                val file = userFileForPath(normalized, rel)
                if (!file.isFile) return null
                runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            }
            else -> null
        }
    }

    private fun readVersionFile(
        id: String,
        path: String,
        version: Int,
    ): String? {
        val base = File(userThemesDir(), "$id/.snapshots/v$version")
        val file = File(base, path)
        if (file.isFile) return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
        // Fallback: fragments may be stored under fragments/ dir
        if (path.startsWith("fragments/")) {
            val fragFile = File(base, path)
            if (fragFile.isFile) return runCatching { fragFile.readText(Charsets.UTF_8) }.getOrNull()
        }
        return null
    }

    fun readPackFileLines(
        id: String,
        path: String,
        offset: Int = 1,
        limit: Int = 400,
    ): Result<FileSlice> {
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        if (rel.startsWith("media/") && !isTextMediaPath(rel)) {
            val bytes = readMediaBytes(id, rel)
                ?: return Result.failure(IllegalArgumentException("Media not found: $rel"))
            return Result.failure(
                IllegalArgumentException(
                    "Binary media $rel (${bytes.size} bytes, ${mimeForMedia(rel)}). Import/export via zip; AI writes only svg text.",
                ),
            )
        }
        val text = readPackFile(id, rel)
            ?: return Result.failure(IllegalArgumentException("File not found: $rel"))
        val lines = text.split('\n')
        val total = lines.size
        val start = offset.coerceAtLeast(1)
        val lim = limit.coerceIn(1, 2000)
        val fromIdx = (start - 1).coerceAtMost(total)
        val slice = lines.drop(fromIdx).take(lim)
        val more = fromIdx + slice.size < total
        return Result.success(
            FileSlice(
                path = rel,
                content = slice.joinToString("\n"),
                totalLines = total,
                offset = start,
                limit = lim,
                truncated = more,
            ),
        )
    }

    /**
     * Grep a pack file for lines matching [pattern] (case-insensitive regex).
     * Returns matched lines with 1-based line numbers.
     */
    fun grepPackFile(
        id: String,
        path: String,
        pattern: String,
        maxResults: Int = 80,
    ): Result<Map<String, Any?>> {
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        val text = readPackFile(id, rel)
            ?: return Result.failure(IllegalArgumentException("File not found: $rel"))
        val lines = text.split('\n')
        val regex = runCatching { Regex(pattern, setOf(RegexOption.IGNORE_CASE)) }
            .getOrElse { return Result.failure(IllegalArgumentException("Invalid regex: ${it.message}")) }
        val matches = lines.mapIndexedNotNull { idx, line ->
            if (regex.containsMatchIn(line)) {
                mapOf("line" to (idx + 1), "text" to line)
            } else null
        }
        val limited = matches.take(maxResults)
        return Result.success(
            mapOf(
                "path" to rel,
                "pattern" to pattern,
                "totalLines" to lines.size,
                "matchCount" to matches.size,
                "truncated" to (matches.size > maxResults),
                "matches" to limited,
            ),
        )
    }

    /**
     * Line-by-line diff of [path] between two themes (or two versions of the same theme).
     * Pass [versionA] / [versionB] to read from snapshots instead of working tree.
     */
    fun diffPackFile(
        idA: String,
        idB: String,
        path: String,
        versionA: Int? = null,
        versionB: Int? = null,
    ): Result<Map<String, Any?>> {
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        val textA = if (versionA != null) readVersionFile(idA, rel, versionA)
            else readPackFile(idA, rel)
        val textB = if (versionB != null) readVersionFile(idB, rel, versionB)
            else readPackFile(idB, rel)
        if (textA == null) return Result.failure(IllegalArgumentException("$rel not found in $idA${versionA?.let { " v$it" } ?: ""}"))
        if (textB == null) return Result.failure(IllegalArgumentException("$rel not found in $idB${versionB?.let { " v$it" } ?: ""}"))
        val linesA = textA.split('\n')
        val linesB = textB.split('\n')
        val setA = linesA.mapIndexedNotNull { idx, line ->
            if (line.isNotBlank()) (idx + 1) to line else null
        }
        val setB = linesB.mapIndexedNotNull { idx, line ->
            if (line.isNotBlank()) (idx + 1) to line else null
        }
        val textSetA = setA.map { it.second }.toSet()
        val textSetB = setB.map { it.second }.toSet()
        val onlyInA = setA.filter { it.second !in textSetB }
            .take(200).map { mapOf("line" to it.first, "text" to it.second) }
        val onlyInB = setB.filter { it.second !in textSetA }
            .take(200).map { mapOf("line" to it.first, "text" to it.second) }
        val aTruncated = setA.count { it.second !in textSetB } > 200
        val bTruncated = setB.count { it.second !in textSetA } > 200
        return Result.success(
            mapOf(
                "path" to rel,
                "themeA" to mapOf("id" to idA, "totalLines" to linesA.size) +
                    if (versionA != null) mapOf("version" to versionA) else emptyMap(),
                "themeB" to mapOf("id" to idB, "totalLines" to linesB.size) +
                    if (versionB != null) mapOf("version" to versionB) else emptyMap(),
                "onlyInA" to onlyInA,
                "onlyInB" to onlyInB,
                "truncatedA" to aTruncated,
                "truncatedB" to bTruncated,
            ),
        )
    }

    /**
     * Ensure a user overlay pack exists (seed CSS + fragments from [baseThemeId] / self / default).
     * Does not seed shell unless [seedShell] is true.
     * [baseThemeId] = [STARTER_BLANK] seeds the official blank starter.
     */
    fun ensureUserPack(
        id: String,
        baseThemeId: String? = null,
        name: String? = null,
        seedShell: Boolean = false,
    ): Result<ThemeInfo> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        val base = baseThemeId?.trim()?.takeIf { it.isNotBlank() }
        if (base == STARTER_BLANK) {
            val destDir = File(userThemesDir(), destId)
            val isNew = !destDir.exists() || !File(destDir, "styles.css").isFile
            if (isNew) {
                return createFromStarter(STARTER_BLANK, destId, name)
            }
            if (!name.isNullOrBlank()) {
                bumpManifest(destId, name = name, bumpVersion = false, dirty = readManifest(destId)?.dirty == true)
            }
            val manifest = readManifest(destId)
            return Result.success(
                ThemeInfo(
                    id = destId,
                    name = manifest?.name ?: destId,
                    version = manifest?.version ?: 1,
                    builtin = false,
                    selected = AppConfig.aiChatHtmlThemeId == destId,
                    dirty = manifest?.dirty == true,
                ),
            )
        }
        val seedId = base
            ?: destId.takeIf { exists(it) }
            ?: DEFAULT_THEME_ID
        if (base != null && !exists(seedId)) {
            return Result.failure(IllegalArgumentException("baseThemeId not found: $seedId"))
        }
        val destDir = File(userThemesDir(), destId)
        val isNew = !destDir.exists() || !File(destDir, "styles.css").isFile
        if (isNew) {
            destDir.mkdirs()
            readCss(seedId)?.let { File(destDir, "styles.css").writeText(it, Charsets.UTF_8) }
                ?: File(destDir, "styles.css").writeText(FALLBACK_CSS, Charsets.UTF_8)
            val fragDir = File(destDir, "fragments").also { it.mkdirs() }
            for (slot in listFragmentSlots(seedId)) {
                val html = readFragment(seedId, slot) ?: continue
                File(fragDir, "$slot.html").writeText(sanitizeHtmlFragment(html), Charsets.UTF_8)
            }
            if (seedShell || base != null) {
                readEffectiveIndexHtml(seedId)?.let {
                    File(destDir, SHELL_INDEX).writeText(it, Charsets.UTF_8)
                }
                readEffectiveAppJs(seedId)?.let {
                    File(destDir, SHELL_APP_JS).writeText(it, Charsets.UTF_8)
                }
                ensureSharedMdJs(destDir)
            }
            val baseManifest = readManifest(seedId)
            File(destDir, "theme.json").writeText(
                GSON.toJson(
                    ThemeManifest(
                        id = destId,
                        name = name?.takeIf { it.isNotBlank() }
                            ?: "${baseManifest?.name ?: seedId} (new)",
                        version = 1,
                        shellKind = if (seedShell || base != null) "builtin" else null,
                    ),
                ),
                Charsets.UTF_8,
            )
            notifyChanged()
        } else if (!name.isNullOrBlank()) {
            bumpManifest(destId, name = name, bumpVersion = false)
        }
        val manifest = readManifest(destId)
        return Result.success(
            ThemeInfo(
                id = destId,
                name = manifest?.name ?: destId,
                version = manifest?.version ?: 1,
                builtin = false,
                selected = AppConfig.aiChatHtmlThemeId == destId,
            ),
        )
    }

    /**
     * Create a user pack from an official starter (not listed as built-in theme).
     * Copies starter files and shared [SHELL_APP_JS] from assets.
     */
    fun createFromStarter(
        starterId: String,
        targetId: String? = null,
        name: String? = null,
    ): Result<ThemeInfo> {
        val starter = starterId.trim().lowercase()
        if (starter != STARTER_BLANK) {
            return Result.failure(IllegalArgumentException("Unknown starter: $starterId (only blank)"))
        }
        val destId = normalizeThemeId(
            targetId?.takeIf { it.isNotBlank() }
                ?: "blank-${System.currentTimeMillis() % 100000}",
        )
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        val destDir = File(userThemesDir(), destId).also { it.mkdirs() }
        val starterRoot = "web/aichat/starters/$starter"
        val css = readAsset("$starterRoot/styles.css") ?: FALLBACK_CSS
        File(destDir, "styles.css").writeText(css, Charsets.UTF_8)
        val index = readAsset("$starterRoot/index.html")
            ?: return Result.failure(IllegalArgumentException("Starter missing index.html"))
        File(destDir, SHELL_INDEX).writeText(index, Charsets.UTF_8)
        val appJs = readAsset("web/aichat/$SHELL_APP_JS")
            ?: return Result.failure(IllegalArgumentException("Shared app.js missing"))
        File(destDir, SHELL_APP_JS).writeText(appJs, Charsets.UTF_8)
        ensureSharedMdJs(destDir)
        val starterManifest = readAsset("$starterRoot/theme.json")
            ?.let { GSON.fromJsonObject<ThemeManifest>(it).getOrNull() }
        val manifest = ThemeManifest(
            id = destId,
            name = name?.takeIf { it.isNotBlank() }
                ?: starterManifest?.name
                ?: "Blank shell",
            version = 1,
            dirty = false,
            shellKind = "builtin",
        )
        File(destDir, "theme.json").writeText(GSON.toJson(manifest), Charsets.UTF_8)
        // Optional starter fragments
        val fragNames = runCatching {
            context.assets.list("$starterRoot/fragments")?.toList().orEmpty()
        }.getOrDefault(emptyList())
        if (fragNames.isNotEmpty()) {
            val fragDir = File(destDir, "fragments").also { it.mkdirs() }
            for (nameFile in fragNames) {
                if (!nameFile.endsWith(".html")) continue
                val slot = nameFile.removeSuffix(".html")
                if (!isValidSlotName(slot)) continue
                val html = readAsset("$starterRoot/fragments/$nameFile") ?: continue
                File(fragDir, nameFile).writeText(sanitizeHtmlFragment(html), Charsets.UTF_8)
            }
        }
        notifyChanged()
        return Result.success(
            ThemeInfo(
                id = destId,
                name = manifest.name ?: destId,
                version = 1,
                builtin = false,
                selected = AppConfig.aiChatHtmlThemeId == destId,
            ),
        )
    }

    fun writePackFile(
        id: String,
        path: String,
        contents: String,
        skipShellValidation: Boolean = false,
        bumpVersion: Boolean = false,
        notify: Boolean = true,
    ): Result<AppliedOp> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        ensureUserPack(destId).getOrElse { return Result.failure(it) }
        val prepared = prepareContentForPath(rel, contents, skipShellValidation)
            .getOrElse { return Result.failure(it) }
        val file = userFileForPath(destId, rel)
        file.parentFile?.mkdirs()
        file.writeText(prepared.content, Charsets.UTF_8)
        // Working-tree edit only — version bumps on commitTheme / patchPack commit.
        markDirty(destId)
        if (bumpVersion) {
            // Legacy: explicit bump still allowed but prefer commitTheme.
            bumpManifest(destId, bumpVersion = true, dirty = false)
        }
        if (isShellPath(rel)) reclassifyShellKind(destId)
        if (notify) notifyChanged()
        return Result.success(AppliedOp(op = "write", path = rel, warnings = prepared.warnings))
    }

    fun applyStrReplace(
        id: String,
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean = false,
        skipShellValidation: Boolean = false,
        bumpVersion: Boolean = false,
        notify: Boolean = true,
    ): Result<AppliedOp> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        if (oldString.isEmpty()) {
            return Result.failure(IllegalArgumentException("old_string must not be empty"))
        }
        ensureUserPack(
            destId,
            seedShell = isShellPath(rel),
        ).getOrElse { return Result.failure(it) }
        // Seed specific file into user pack if missing so we edit a real overlay.
        val userFile = userFileForPath(destId, rel)
        if (!userFile.isFile) {
            val effective = readPackFile(destId, rel)
                ?: return Result.failure(IllegalArgumentException("File not found: $rel"))
            userFile.parentFile?.mkdirs()
            userFile.writeText(effective, Charsets.UTF_8)
        }
        val current = runCatching { userFile.readText(Charsets.UTF_8) }.getOrElse {
            return Result.failure(it)
        }
        val edited = applyEditToContent(current, oldString, newString, replaceAll)
            .getOrElse { return Result.failure(diagnoseEditFailure(current, rel, oldString, it)) }
        val prepared = prepareContentForPath(rel, edited.content, skipShellValidation)
            .getOrElse { return Result.failure(it) }
        userFile.writeText(prepared.content, Charsets.UTF_8)
        markDirty(destId)
        if (bumpVersion) bumpManifest(destId, bumpVersion = true, dirty = false)
        if (isShellPath(rel)) reclassifyShellKind(destId)
        if (notify) notifyChanged()
        return Result.success(
            AppliedOp(
                op = "edit",
                path = rel,
                replacements = edited.replacements,
                warnings = prepared.warnings,
            ),
        )
    }

    fun deletePackPath(
        id: String,
        path: String,
        bumpVersion: Boolean = false,
        notify: Boolean = true,
    ): Result<AppliedOp> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        if (rel == "styles.css") {
            return Result.failure(IllegalArgumentException("Cannot delete styles.css (required)"))
        }
        if (!isUserTheme(destId) && !File(userThemesDir(), destId).isDirectory) {
            return Result.failure(IllegalArgumentException("No user overlay to delete from: $destId"))
        }
        val file = userFileForPath(destId, rel)
        if (!file.isFile) {
            return Result.failure(IllegalArgumentException("User overlay file not found: $rel"))
        }
        if (!file.delete()) {
            return Result.failure(IllegalArgumentException("Failed to delete $rel"))
        }
        markDirty(destId)
        if (bumpVersion) bumpManifest(destId, bumpVersion = true, dirty = false)
        if (isShellPath(rel)) reclassifyShellKind(destId)
        if (notify) notifyChanged()
        return Result.success(AppliedOp(op = "delete", path = rel))
    }

    /**
     * Pack patch: plan all ops in memory → write once → single commit.
     * [dryRun] validates and returns the plan without touching disk (no seed/commit).
     * On apply failure after writes begin, restores a per-path snapshot.
     */
    fun patchPack(
        id: String,
        name: String? = null,
        baseThemeId: String? = null,
        activate: Boolean = false,
        skipShellValidation: Boolean = false,
        writes: List<Pair<String, String>> = emptyList(),
        edits: List<EditOp> = emptyList(),
        deletePaths: List<String> = emptyList(),
        dryRun: Boolean = false,
        message: String? = null,
    ): Result<PatchResult> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        if (writes.isEmpty() && edits.isEmpty() && deletePaths.isEmpty() &&
            name.isNullOrBlank() && baseThemeId.isNullOrBlank() && !activate
        ) {
            return Result.failure(
                IllegalArgumentException("Provide writes and/or edits and/or deletePaths (or name/baseThemeId/activate)"),
            )
        }

        if (dryRun) {
            val plan = planPatch(
                destId = destId,
                baseThemeId = baseThemeId,
                skipShellValidation = skipShellValidation,
                writes = writes,
                edits = edits,
                deletePaths = deletePaths,
                allowMissingPack = true,
            ).getOrElse { return Result.failure(it) }
            val manifestName = name?.takeIf { it.isNotBlank() }
                ?: readManifest(destId)?.name
                ?: destId
            return Result.success(
                PatchResult(
                    theme = ThemeInfo(
                        id = destId,
                        name = manifestName,
                        version = readManifest(destId)?.version ?: 1,
                        builtin = false,
                        selected = AppConfig.aiChatHtmlThemeId == destId,
                        dirty = readManifest(destId)?.dirty == true,
                    ),
                    applied = plan.applied,
                    reload = plan.reload,
                    warnings = plan.warnings,
                    dryRun = true,
                    diffs = plan.diffs,
                ),
            )
        }

        val needsShellSeed = writes.any { isShellPath(it.first) } ||
            edits.any { isShellPath(it.path) } ||
            baseThemeId != null
        ensureUserPack(
            destId,
            baseThemeId = baseThemeId,
            name = name,
            seedShell = needsShellSeed,
        ).getOrElse { return Result.failure(it) }

        val plan = planPatch(
            destId = destId,
            baseThemeId = null,
            skipShellValidation = skipShellValidation,
            writes = writes,
            edits = edits,
            deletePaths = deletePaths,
            allowMissingPack = false,
        ).getOrElse { return Result.failure(it) }

        if (plan.applied.isEmpty()) {
            if (!name.isNullOrBlank()) {
                bumpManifest(destId, name = name, bumpVersion = false, dirty = readManifest(destId)?.dirty == true)
            }
            if (activate) AppConfig.aiChatHtmlThemeId = destId
            notifyChanged()
            val manifest = readManifest(destId)
            return Result.success(
                PatchResult(
                    theme = ThemeInfo(
                        id = destId,
                        name = manifest?.name ?: destId,
                        version = manifest?.version ?: 1,
                        builtin = false,
                        selected = AppConfig.aiChatHtmlThemeId == destId,
                        dirty = manifest?.dirty == true,
                    ),
                    applied = emptyList(),
                    reload = "hot",
                    warnings = emptyList(),
                ),
            )
        }

        val snapshot = snapshotUserPaths(destId, plan.touchedPaths)
        try {
            for (path in plan.deletes) {
                val file = userFileForPath(destId, path)
                if (file.isFile && !file.delete()) {
                    error("Failed to delete $path")
                }
            }
            for ((path, content) in plan.writes) {
                val file = userFileForPath(destId, path)
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            }
            markDirty(destId)
            if (!name.isNullOrBlank()) {
                bumpManifest(destId, name = name, bumpVersion = false, dirty = false)
            }
            if (activate) AppConfig.aiChatHtmlThemeId = destId
            notifyChanged()
        } catch (e: Exception) {
            restoreUserPaths(destId, snapshot)
            notifyChanged()
            return Result.failure(
                PatchOpException(
                    message = "Patch rolled back: ${e.message ?: "write failed"}",
                    hint = "No partial writes kept. Re-read paths and retry.",
                ),
            )
        }

        val manifest = readManifest(destId)
        return Result.success(
            PatchResult(
                theme = ThemeInfo(
                    id = destId,
                    name = manifest?.name ?: destId,
                    version = manifest?.version ?: 1,
                    builtin = false,
                    selected = AppConfig.aiChatHtmlThemeId == destId,
                    dirty = manifest?.dirty == true,
                ),
                applied = plan.applied,
                reload = plan.reload,
                warnings = plan.warnings,
                diffs = plan.diffs,
            ),
        )
    }

    /**
     * Dry-run / approval preview: field-style diffs without writing.
     */
    fun previewPatchChanges(
        id: String,
        baseThemeId: String? = null,
        skipShellValidation: Boolean = false,
        writes: List<Pair<String, String>> = emptyList(),
        edits: List<EditOp> = emptyList(),
        deletePaths: List<String> = emptyList(),
    ): List<io.legado.app.domain.usecase.structured.FieldChange> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return emptyList()
        val plan = planPatch(
            destId = destId,
            baseThemeId = baseThemeId,
            skipShellValidation = skipShellValidation,
            writes = writes,
            edits = edits,
            deletePaths = deletePaths,
            allowMissingPack = true,
        ).getOrElse { err ->
            val pe = err as? PatchOpException
            return listOf(
                io.legado.app.domain.usecase.structured.FieldChange(
                    path = pe?.path ?: "_error",
                    oldValue = "",
                    newValue = pe?.message ?: err.message ?: "patch preview failed",
                ),
            )
        }
        return plan.diffs
    }

    private data class PlannedPatch(
        val applied: List<AppliedOp>,
        val writes: Map<String, String>,
        val deletes: Set<String>,
        val touchedPaths: Set<String>,
        val reload: String,
        val warnings: List<String>,
        val diffs: List<io.legado.app.domain.usecase.structured.FieldChange>,
    )

    private data class PathSnapshot(
        val path: String,
        val existed: Boolean,
        val content: String?,
    )

    private fun snapshotUserPaths(id: String, paths: Set<String>): List<PathSnapshot> =
        paths.map { path ->
            val file = userFileForPath(id, path)
            PathSnapshot(
                path = path,
                existed = file.isFile,
                content = if (file.isFile) runCatching { file.readText(Charsets.UTF_8) }.getOrNull() else null,
            )
        }

    private fun restoreUserPaths(id: String, snapshots: List<PathSnapshot>) {
        for (snap in snapshots) {
            val file = userFileForPath(id, snap.path)
            if (!snap.existed) {
                if (file.isFile) file.delete()
                continue
            }
            val text = snap.content ?: continue
            file.parentFile?.mkdirs()
            runCatching { file.writeText(text, Charsets.UTF_8) }
        }
    }

    /**
     * Resolve effective file text for planning. [allowMissingPack] uses [baseThemeId] / assets
     * without requiring a user overlay (dry-run of new themes).
     */
    private fun resolvePlanSource(
        destId: String,
        path: String,
        baseThemeId: String?,
        buffers: Map<String, String>,
        deleted: Set<String>,
    ): String? {
        if (path in deleted) return null
        buffers[path]?.let { return it }
        readPackFile(destId, path)?.let { return it }
        val seed = baseThemeId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            seed == STARTER_BLANK -> when (path) {
                "styles.css" -> readAsset("web/aichat/starters/blank/styles.css")
                SHELL_INDEX -> readAsset("web/aichat/starters/blank/index.html")
                    ?: readAsset("web/aichat/$SHELL_INDEX")
                SHELL_APP_JS -> readAsset("web/aichat/$SHELL_APP_JS")
                else -> if (path.startsWith("fragments/")) {
                    val slot = fragmentSlotFromPath(path) ?: return null
                    readAsset("web/aichat/starters/blank/fragments/$slot.html")
                } else null
            }
            else -> readPackFile(seed, path)
        }
    }

    private fun planPatch(
        destId: String,
        baseThemeId: String?,
        skipShellValidation: Boolean,
        writes: List<Pair<String, String>>,
        edits: List<EditOp>,
        deletePaths: List<String>,
        allowMissingPack: Boolean,
    ): Result<PlannedPatch> {
        if (!allowMissingPack && !isUserTheme(destId) && !exists(destId)) {
            return Result.failure(IllegalArgumentException("Theme not found: $destId"))
        }
        val buffers = linkedMapOf<String, String>()
        val deleted = linkedSetOf<String>()
        val before = linkedMapOf<String, String>()
        val applied = mutableListOf<AppliedOp>()
        var shellTouched = false

        fun touchBefore(path: String) {
            if (path in before) return
            val src = resolvePlanSource(destId, path, baseThemeId, buffers, deleted)
            if (src != null) before[path] = src
        }

        for ((rawPath, contents) in writes) {
            val rel = normalizePackPath(rawPath).getOrElse { return Result.failure(it) }
            touchBefore(rel)
            val prepared = prepareContentForPath(rel, contents, skipShellValidation)
                .getOrElse { return Result.failure(it) }
            buffers[rel] = prepared.content
            deleted.remove(rel)
            applied += AppliedOp("write", rel, warnings = prepared.warnings)
            if (isShellPath(rel)) shellTouched = true
        }

        for (edit in edits) {
            val rel = normalizePackPath(edit.path).getOrElse { return Result.failure(it) }
            if (edit.oldString.isEmpty()) {
                return Result.failure(
                    PatchOpException("old_string must not be empty", path = rel, hint = "Provide a non-empty old_string"),
                )
            }
            touchBefore(rel)
            val current = resolvePlanSource(destId, rel, baseThemeId, buffers, deleted)
                ?: return Result.failure(
                    PatchOpException(
                        message = "File not found: $rel",
                        path = rel,
                        hint = "write the file first, or seed with baseThemeId",
                    ),
                )
            val edited = applyEditToContent(current, edit.oldString, edit.newString, edit.replaceAll)
                .getOrElse { return Result.failure(diagnoseEditFailure(current, rel, edit.oldString, it)) }
            val prepared = prepareContentForPath(rel, edited.content, skipShellValidation)
                .getOrElse { return Result.failure(it) }
            buffers[rel] = prepared.content
            deleted.remove(rel)
            applied += AppliedOp(
                op = "edit",
                path = rel,
                replacements = edited.replacements,
                warnings = prepared.warnings,
            )
            if (isShellPath(rel)) shellTouched = true
        }

        for (rawPath in deletePaths) {
            val rel = normalizePackPath(rawPath).getOrElse { return Result.failure(it) }
            if (rel == "styles.css") {
                return Result.failure(
                    PatchOpException("Cannot delete styles.css (required)", path = rel),
                )
            }
            touchBefore(rel)
            val existsNow = resolvePlanSource(destId, rel, baseThemeId, buffers, deleted) != null ||
                userFileForPath(destId, rel).isFile
            if (!existsNow && rel !in buffers) {
                return Result.failure(
                    PatchOpException("User overlay file not found: $rel", path = rel),
                )
            }
            buffers.remove(rel)
            deleted += rel
            applied += AppliedOp("delete", rel)
            if (isShellPath(rel)) shellTouched = true
        }

        val diffs = mutableListOf<io.legado.app.domain.usecase.structured.FieldChange>()
        for (path in (before.keys + buffers.keys + deleted)) {
            val oldVal = before[path].orEmpty()
            val newVal = when {
                path in deleted -> ""
                path in buffers -> buffers.getValue(path)
                else -> oldVal
            }
            if (oldVal == newVal && path !in deleted) continue
            diffs += io.legado.app.domain.usecase.structured.FieldChange(
                path = path,
                oldValue = truncateDiff(oldVal),
                newValue = if (path in deleted) "(deleted)" else truncateDiff(newVal),
            )
        }

        return Result.success(
            PlannedPatch(
                applied = applied,
                writes = buffers.filterKeys { it !in deleted },
                deletes = deleted,
                touchedPaths = (buffers.keys + deleted + before.keys).toSet(),
                reload = if (shellTouched) "full" else "hot",
                warnings = applied.flatMap { it.warnings }.distinct(),
                diffs = diffs,
            ),
        )
    }

    /**
     * Git-like commit: bump [ThemeManifest.version] once and clear dirty.
     * No-op failure if nothing to commit (not dirty and [force] is false).
     */
    fun commitTheme(
        id: String,
        message: String? = null,
        force: Boolean = false,
        notify: Boolean = true,
    ): Result<ThemeInfo> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        if (!isUserTheme(destId)) {
            return Result.failure(IllegalArgumentException("Only user packs can be committed"))
        }
        val prev = readManifest(destId)
        if (!force && prev?.dirty != true) {
            return Result.failure(IllegalArgumentException("Nothing to commit (working tree clean)"))
        }
        val nextVersion = (prev?.version ?: 1) + 1
        val msg = message?.trim()?.takeIf { it.isNotBlank() } ?: "commit"
        val manifest = ThemeManifest(
            id = destId,
            name = prev?.name ?: destId,
            version = nextVersion,
            dirty = false,
            lastCommitMessage = msg,
            lastCommitAt = System.currentTimeMillis(),
            shellKind = prev?.shellKind,
        )
        val destDir = File(userThemesDir(), destId).also { it.mkdirs() }
        File(destDir, "theme.json").writeText(GSON.toJson(manifest), Charsets.UTF_8)
        snapshotVersion(destId, nextVersion)
        appendCommitLog(destId, nextVersion, msg)
        if (notify) notifyChanged()
        return Result.success(
            ThemeInfo(
                id = destId,
                name = manifest.name ?: destId,
                version = nextVersion,
                builtin = false,
                selected = AppConfig.aiChatHtmlThemeId == destId,
                dirty = false,
            ),
        )
    }

    private fun snapshotVersion(id: String, version: Int) {
        val srcDir = File(userThemesDir(), id)
        val snapDir = File(srcDir, ".snapshots/v$version")
        if (snapDir.exists()) snapDir.deleteRecursively()
        snapDir.mkdirs()
        val userFiles = srcDir.listFiles()?.filter { it.isFile } ?: return
        for (file in userFiles) {
            file.copyTo(File(snapDir, file.name), overwrite = true)
        }
        val fragDir = File(srcDir, "fragments")
        if (fragDir.isDirectory) {
            val snapFrag = File(snapDir, "fragments")
            snapFrag.mkdirs()
            fragDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                file.copyTo(File(snapFrag, file.name), overwrite = true)
            }
        }
    }

    data class CommitEntry(
        val version: Int,
        val message: String,
        val timestamp: Long,
    )

    fun listCommits(id: String): List<CommitEntry> {
        val log = File(userThemesDir(), "$id/commits.log")
        if (!log.isFile) return emptyList()
        return runCatching {
            log.readLines().mapNotNull { line ->
                val parts = line.split("\t", limit = 3)
                if (parts.size < 3) return@mapNotNull null
                val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
                val vPart = parts[1]
                if (!vPart.startsWith("v")) return@mapNotNull null
                val v = vPart.removePrefix("v").toIntOrNull() ?: return@mapNotNull null
                CommitEntry(version = v, timestamp = ts, message = parts[2])
            }.sortedByDescending { it.timestamp }
        }.getOrElse { emptyList() }
    }

    fun rollbackToVersion(
        id: String,
        targetVersion: Int,
        message: String? = null,
    ): Result<ThemeInfo> {
        val destId = normalizeThemeId(id)
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))
        if (!isUserTheme(destId)) {
            return Result.failure(IllegalArgumentException("Only user packs can be rolled back"))
        }
        val snapDir = File(userThemesDir(), "$destId/.snapshots/v$targetVersion")
        if (!snapDir.isDirectory) {
            return Result.failure(IllegalArgumentException("Snapshot v$targetVersion not found for '$destId'"))
        }
        val currentVersion = readManifest(destId)?.version ?: 1
        val destDir = File(userThemesDir(), destId).also { it.mkdirs() }
        // Copy snapshot files to working tree (theme.json written below with a fresh version).
        snapDir.listFiles()?.filter { it.isFile && it.name != "theme.json" }?.forEach { file ->
            file.copyTo(File(destDir, file.name), overwrite = true)
        }
        val snapFrag = File(snapDir, "fragments")
        val destFrag = File(destDir, "fragments")
        if (snapFrag.isDirectory) {
            destFrag.mkdirs()
            snapFrag.listFiles()?.filter { it.isFile }?.forEach { file ->
                file.copyTo(File(destFrag, file.name), overwrite = true)
            }
        }
        // Version only ever increases: the rollback commit must not collide with
        // an existing history entry (e.g. rolling v14 back to v12 must not re-create v13).
        val nextVersion = maxOf(currentVersion, targetVersion) + 1
        val msg = message?.trim()?.takeIf { it.isNotBlank() } ?: "Rollback to v$targetVersion"
        val manifest = ThemeManifest(
            id = destId,
            name = readManifest(destId)?.name ?: destId,
            version = nextVersion,
            dirty = false,
            lastCommitMessage = msg,
            lastCommitAt = System.currentTimeMillis(),
            shellKind = readManifest(destId)?.shellKind,
        )
        File(destDir, "theme.json").writeText(GSON.toJson(manifest), Charsets.UTF_8)
        snapshotVersion(destId, nextVersion)
        appendCommitLog(destId, nextVersion, msg)
        notifyChanged()
        return Result.success(
            ThemeInfo(
                id = destId,
                name = manifest.name ?: destId,
                version = nextVersion,
                builtin = false,
                selected = AppConfig.aiChatHtmlThemeId == destId,
                dirty = false,
            ),
        )
    }

    /** Apply a single StrReplace edit to one pack file. Marks dirty on success. */
    fun editFile(
        id: String,
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean = false,
        skipShellValidation: Boolean = false,
    ): Result<Map<String, Any?>> {
        val destId = normalizeThemeId(id)
            .takeIf { it.isNotBlank() } ?: return Result.failure(IllegalArgumentException("Invalid theme id"))
        if (!isUserTheme(destId)) {
            return Result.failure(IllegalArgumentException("Only user packs can be edited"))
        }
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        val current = readPackFile(destId, rel)
            ?: return Result.failure(IllegalArgumentException("File not found: $rel. write it first."))
        val edited = applyEditToContent(current, oldString, newString, replaceAll)
            .getOrElse { return Result.failure(diagnoseEditFailure(current, rel, oldString, it)) }
        val prepared = prepareContentForPath(rel, edited.content, skipShellValidation)
            .getOrElse { return Result.failure(it) }
        val file = userFileForPath(destId, rel)
        file.parentFile?.mkdirs()
        file.writeText(prepared.content, Charsets.UTF_8)
        markDirty(destId)
        if (isShellPath(rel)) reclassifyShellKind(destId)
        notifyChanged(setOf(rel))
        return Result.success(
            mapOf(
                "path" to rel,
                "replacements" to edited.replacements,
                "preview" to buildChangePreview(current, edited.content, oldString, newString),
                "note" to if (replaceAll && edited.replacements > 1) {
                    "${edited.replacements} replacements applied."
                } else {
                    "1 replacement applied."
                },
            ),
        )
    }

    /** Write/create a single pack file. Seeds a new pack from [baseThemeId] if needed. Marks dirty. */
    fun writeUserFile(
        id: String,
        path: String,
        contents: String,
        baseThemeId: String? = null,
        skipShellValidation: Boolean = false,
    ): Result<Map<String, Any?>> {
        val destId = normalizeThemeId(id)
            .takeIf { it.isNotBlank() } ?: return Result.failure(IllegalArgumentException("Invalid theme id"))
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        // Seed pack if needed
        if (!isUserTheme(destId)) {
            val base = baseThemeId?.trim()?.takeIf { it.isNotBlank() }
            ensureUserPack(destId, baseThemeId = base, name = null, seedShell = isShellPath(rel))
                .getOrElse { return Result.failure(it) }
        }
        val prepared = prepareContentForPath(rel, contents, skipShellValidation)
            .getOrElse { return Result.failure(it) }
        val file = userFileForPath(destId, rel)
        file.parentFile?.mkdirs()
        file.writeText(prepared.content, Charsets.UTF_8)
        markDirty(destId)
        if (isShellPath(rel)) reclassifyShellKind(destId)
        notifyChanged(setOf(rel))
        return Result.success(
            mapOf(
                "path" to rel,
                "size" to prepared.content.length,
                "note" to "Written ${prepared.content.length} chars to $rel.",
            ),
        )
    }

    /** Delete a single user overlay file. Marks dirty. styles.css is protected. */
    fun deleteUserFile(id: String, path: String): Result<Map<String, Any?>> {
        val destId = normalizeThemeId(id)
            .takeIf { it.isNotBlank() } ?: return Result.failure(IllegalArgumentException("Invalid theme id"))
        if (!isUserTheme(destId)) {
            return Result.failure(IllegalArgumentException("Only user packs can be modified"))
        }
        val rel = normalizePackPath(path).getOrElse { return Result.failure(it) }
        if (rel == "styles.css") {
            return Result.failure(IllegalArgumentException("Cannot delete styles.css (required)"))
        }
        val file = userFileForPath(destId, rel)
        if (!file.isFile) {
            return Result.failure(IllegalArgumentException("User overlay file not found: $rel"))
        }
        file.delete()
        markDirty(destId)
        return Result.success(
            mapOf(
                "path" to rel,
                "note" to "Deleted $rel.",
            ),
        )
    }

    private fun buildChangePreview(
        before: String,
        after: String,
        oldString: String,
        newString: String,
    ): Map<String, String> {
        val idx = before.indexOf(oldString)
        if (idx < 0) return mapOf("old" to oldString.take(200), "new" to newString.take(200))
        val beforeStart = before.lastIndexOf('\n', (idx - 1).coerceAtLeast(0)).coerceAtLeast(0)
        val beforeEnd = before.indexOf('\n', (idx + oldString.length).coerceAtMost(before.length))
            .takeIf { it >= 0 } ?: before.length
        val afterStart = after.lastIndexOf('\n', (idx - 1).coerceAtLeast(0)).coerceAtLeast(0)
        val afterEnd = after.indexOf('\n', (idx + newString.length).coerceAtMost(after.length))
            .takeIf { it >= 0 } ?: after.length
        return mapOf(
            "old" to before.substring(beforeStart, beforeEnd).trimEnd(),
            "new" to after.substring(afterStart, afterEnd).trimEnd(),
        )
    }

    fun isDirty(id: String): Boolean = readManifest(id.trim())?.dirty == true

    private fun markDirty(id: String) {
        val prev = readManifest(id)
        val manifest = ThemeManifest(
            id = id,
            name = prev?.name ?: id,
            version = prev?.version ?: 1,
            dirty = true,
            lastCommitMessage = prev?.lastCommitMessage,
            lastCommitAt = prev?.lastCommitAt,
            shellKind = prev?.shellKind,
        )
        val destDir = File(userThemesDir(), id).also { it.mkdirs() }
        File(destDir, "theme.json").writeText(GSON.toJson(manifest), Charsets.UTF_8)
    }

    private fun appendCommitLog(id: String, version: Int, message: String) {
        val log = File(userThemesDir(), "$id/commits.log")
        runCatching {
            log.parentFile?.mkdirs()
            log.appendText(
                "${System.currentTimeMillis()}\tv$version\t${message.replace('\t', ' ').replace('\n', ' ')}\n",
                Charsets.UTF_8,
            )
        }
    }

    data class EditOp(
        val path: String,
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean = false,
    )

    data class PatchResult(
        val theme: ThemeInfo,
        val applied: List<AppliedOp>,
        val reload: String,
        val warnings: List<String> = emptyList(),
        val dryRun: Boolean = false,
        val diffs: List<io.legado.app.domain.usecase.structured.FieldChange> = emptyList(),
    )

    private fun userFileForPath(id: String, rel: String): File =
        File(userThemesDir(), "$id/$rel")

    private data class PreparedContent(
        val content: String,
        val warnings: List<String> = emptyList(),
    )

    private fun prepareContentForPath(
        rel: String,
        contents: String,
        skipShellValidation: Boolean,
    ): Result<PreparedContent> {
        return when {
            rel == "styles.css" -> Result.success(PreparedContent(contents))
            rel == "theme.json" -> Result.success(PreparedContent(contents.trim()))
            rel == SHELL_INDEX -> {
                val cleaned = prepareShellHtml(contents)
                if (cleaned.length > MAX_SHELL_CHARS) {
                    return Result.failure(IllegalArgumentException("index.html exceeds $MAX_SHELL_CHARS chars"))
                }
                if (!skipShellValidation) {
                    runCatching { validateIndexHtmlOrThrow(cleaned) }.getOrElse {
                        return Result.failure(it)
                    }
                }
                Result.success(PreparedContent(cleaned))
            }
            rel == SHELL_APP_JS -> {
                val cleaned = prepareShellJs(contents)
                if (cleaned.length > MAX_SHELL_CHARS) {
                    return Result.failure(IllegalArgumentException("app.js exceeds $MAX_SHELL_CHARS chars"))
                }
                if (!skipShellValidation) {
                    runCatching { validateAppJsOrThrow(cleaned) }.getOrElse {
                        return Result.failure(it)
                    }
                }
                Result.success(PreparedContent(cleaned))
            }
            rel.startsWith("fragments/") -> {
                val slot = fragmentSlotFromPath(rel)
                    ?: return Result.failure(IllegalArgumentException("Invalid fragment path: $rel"))
                val cleaned = sanitizeHtmlFragment(contents)
                Result.success(PreparedContent(cleaned, fragmentIdWarnings(slot, cleaned)))
            }
            rel.startsWith("media/") -> {
                if (!isTextMediaPath(rel)) {
                    return Result.failure(
                        IllegalArgumentException(
                            "Binary media $rel: import via zip (AI writes allow svg only)",
                        ),
                    )
                }
                val cleaned = contents.trim()
                val bytes = cleaned.toByteArray(Charsets.UTF_8)
                if (bytes.size > MAX_MEDIA_BYTES) {
                    return Result.failure(IllegalArgumentException("Media exceeds $MAX_MEDIA_BYTES bytes: $rel"))
                }
                Result.success(PreparedContent(cleaned))
            }
            else -> Result.failure(IllegalArgumentException("Unsupported path: $rel"))
        }
    }

    private fun bumpManifest(
        id: String,
        name: String? = null,
        bumpVersion: Boolean,
        dirty: Boolean? = null,
        shellKind: String? = null,
    ) {
        val prev = readManifest(id)
        val manifest = ThemeManifest(
            id = id,
            name = name?.takeIf { it.isNotBlank() } ?: prev?.name ?: id,
            version = (prev?.version ?: 1) + if (bumpVersion) 1 else 0,
            dirty = dirty ?: (prev?.dirty == true),
            lastCommitMessage = prev?.lastCommitMessage,
            lastCommitAt = prev?.lastCommitAt,
            shellKind = shellKind ?: prev?.shellKind,
        )
        val destDir = File(userThemesDir(), id).also { it.mkdirs() }
        File(destDir, "theme.json").writeText(GSON.toJson(manifest), Charsets.UTF_8)
    }

    fun exportZip(id: String): ByteArray? {
        val normalized = id.trim()
        if (!exists(normalized)) return null
        val css = readCss(normalized) ?: return null
        val baos = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(baos)).use { zos ->
            fun putText(path: String, content: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            fun putBytes(path: String, bytes: ByteArray) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
            val manifest = readManifest(normalized) ?: ThemeManifest(id = normalized, name = normalized)
            putText("theme.json", GSON.toJson(manifest.copy(id = normalized)))
            putText("styles.css", css)
            readEffectiveIndexHtml(normalized)?.let { putText(SHELL_INDEX, it) }
            readEffectiveAppJs(normalized)?.let { putText(SHELL_APP_JS, it) }
            for (slot in listFragmentSlots(normalized)) {
                val html = readFragment(normalized, slot) ?: continue
                putText("fragments/$slot.html", html)
            }
            for (mediaPath in listMediaPaths(normalized)) {
                val bytes = readMediaBytes(normalized, mediaPath) ?: continue
                putBytes(mediaPath, bytes)
            }
        }
        return baos.toByteArray()
    }

    fun importZip(bytes: ByteArray, preferredId: String? = null): Result<ThemeInfo> {
        val textByRel = linkedMapOf<String, String>()
        val binaryMedia = linkedMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val rawPath = entry.name.replace('\\', '/').removePrefix("./")
                    val raw = zis.readBytes()
                    val rel = resolveZipEntryPath(rawPath)
                    if (rel != null) {
                        if (rel.startsWith("media/") && !isTextMediaPath(rel)) {
                            binaryMedia[rel] = raw
                        } else {
                            textByRel[rel] = raw.toString(Charsets.UTF_8)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        val css = textByRel["styles.css"]
            ?: return Result.failure(IllegalArgumentException("Zip missing styles.css"))
        val manifestJson = textByRel["theme.json"]
        val manifest = manifestJson?.let { GSON.fromJsonObject<ThemeManifest>(it).getOrNull() }
        val destId = normalizeThemeId(
            preferredId?.trim()?.takeIf { it.isNotBlank() }
                ?: manifest?.id
                ?: "imported-${System.currentTimeMillis() % 100000}",
        )
        if (destId.isBlank()) return Result.failure(IllegalArgumentException("Invalid theme id"))

        val existed = isUserTheme(destId)
        // Seed pack without auto-commit; write all entries then commit once.
        ensureUserPack(
            destId,
            name = manifest?.name,
            seedShell = textByRel.containsKey(SHELL_INDEX) || textByRel.containsKey(SHELL_APP_JS),
        ).getOrElse { return Result.failure(it) }

        writePackFile(destId, "styles.css", css, bumpVersion = false, notify = false)
            .getOrElse { return Result.failure(it) }
        for ((rel, content) in textByRel) {
            // theme.json is rewritten by commitTheme / bumpManifest below.
            if (rel == "styles.css" || rel == "theme.json") continue
            writePackFile(
                destId, rel, content,
                skipShellValidation = false,
                bumpVersion = false,
                notify = false,
            ).getOrElse { return Result.failure(it) }
        }
        for ((mediaPath, mediaBytes) in binaryMedia) {
            writeMediaBytes(destId, mediaPath, mediaBytes, notify = false)
                .getOrElse { return Result.failure(it) }
        }
        val info = if (existed) {
            commitTheme(destId, message = "import zip", force = true, notify = false)
                .getOrElse { return Result.failure(it) }
        } else {
            // Fresh pack: keep version 1, clear dirty after import.
            bumpManifest(
                destId,
                name = manifest?.name,
                bumpVersion = false,
                dirty = false,
            )
            appendCommitLog(destId, readManifest(destId)?.version ?: 1, "import zip")
            val m = readManifest(destId)
            ThemeInfo(
                id = destId,
                name = m?.name ?: destId,
                version = m?.version ?: 1,
                builtin = false,
                selected = AppConfig.aiChatHtmlThemeId == destId,
                dirty = false,
            )
        }
        if (existed && !manifest?.name.isNullOrBlank()) {
            bumpManifest(destId, name = manifest?.name, bumpVersion = false, dirty = false)
        }
        reclassifyShellKind(destId)
        notifyChanged()
        val finalManifest = readManifest(destId)
        return Result.success(
            info.copy(
                name = finalManifest?.name ?: info.name,
                version = finalManifest?.version ?: info.version,
                dirty = finalManifest?.dirty == true,
            ),
        )
    }

    private fun listBuiltin(): List<ThemeInfo> {
        val root = "web/aichat/themes"
        val names = runCatching { context.assets.list(root)?.toList().orEmpty() }.getOrDefault(emptyList())
        if (names.isEmpty()) {
            if (!readAsset("web/aichat/styles.css").isNullOrBlank()) {
                return listOf(ThemeInfo(id = DEFAULT_THEME_ID, name = "Default", builtin = true))
            }
            return emptyList()
        }
        return names.mapNotNull { id ->
            val manifest = readAsset("$root/$id/theme.json")
                ?.let { GSON.fromJsonObject<ThemeManifest>(it).getOrNull() }
            val hasCss = !readAsset("$root/$id/styles.css").isNullOrBlank()
            if (!hasCss && manifest == null) return@mapNotNull null
            ThemeInfo(
                id = manifest?.id?.takeIf { it.isNotBlank() } ?: id,
                name = manifest?.name?.takeIf { it.isNotBlank() } ?: id.replaceFirstChar { it.uppercase() },
                version = manifest?.version ?: 1,
                builtin = true,
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun listUser(): List<ThemeInfo> {
        val root = userThemesDir()
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.onEach { dir -> ensureSharedMdJs(dir) }
            ?.mapNotNull { dir ->
                val css = File(dir, "styles.css")
                if (!css.isFile) return@mapNotNull null
                val manifest = File(dir, "theme.json")
                    .takeIf { it.isFile }
                    ?.readText(Charsets.UTF_8)
                    ?.let { GSON.fromJsonObject<ThemeManifest>(it).getOrNull() }
                ThemeInfo(
                    id = manifest?.id?.takeIf { it.isNotBlank() } ?: dir.name,
                    name = manifest?.name?.takeIf { it.isNotBlank() } ?: dir.name,
                    version = manifest?.version ?: 1,
                    builtin = false,
                    dirty = manifest?.dirty == true,
                )
            }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    private fun userThemesDir(): File = File(context.filesDir, USER_THEMES_DIR).also { it.mkdirs() }

    private fun readAsset(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    companion object {
        const val DEFAULT_THEME_ID = "default"
        const val STARTER_BLANK = "blank"
        const val USER_THEMES_DIR = "aichat-themes"
        const val SHELL_INDEX = "index.html"
        const val SHELL_APP_JS = "app.js"
        const val ASSET_ENTRY_URL = "file:///android_asset/web/aichat/index.html"
        const val ASSET_PREFIX = "file:///android_asset/web/aichat/"
        const val MAX_SHELL_CHARS = 400_000
        const val MAX_MEDIA_BYTES = 2_000_000

        private val ALLOWED_ROOT_FILES = setOf(
            "styles.css",
            "theme.json",
            SHELL_INDEX,
            SHELL_APP_JS,
        )

        private val MEDIA_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "webp", "gif", "svg",
            "woff", "woff2", "ttf", "otf", "ico",
        )

        val IMAGE_MEDIA_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "webp", "gif", "svg", "ico",
        )

        val FONT_MEDIA_EXTENSIONS = setOf(
            "woff", "woff2", "ttf", "otf",
        )

        private val SLOT_NAME_REGEX = Regex("^[a-z][a-z0-9_]{0,31}$")

        /** Known default-shell slots (assets enumeration + advisory ids). */
        val KNOWN_SLOTS = linkedSetOf(
            "sidebar",
            "right_drawer",
            "composer",
            "topbar",
            "message_area",
            "galgame_hud",
            "suggestions",
            "tool_panel",
            "interactive_panels",
            "message_item",
        )

        /** Alias of [KNOWN_SLOTS] for older callers/tests. */
        val ALLOWED_SLOTS: Set<String> get() = KNOWN_SLOTS

        /**
         * Expected ids for default shell (advisory). Missing → warnings, not hard fail.
         */
        val REQUIRED_IDS_BY_SLOT: Map<String, Set<String>> = mapOf(
            "sidebar" to setOf(
                "conversation-list",
                "btn-new-chat",
                "btn-back",
                "btn-settings",
            ),
            "right_drawer" to setOf(
                "right-drawer-body",
            ),
            "composer" to setOf(
                "input",
                "btn-send",
                "btn-stop",
            ),
            "topbar" to setOf(
                "btn-toggle-sidebar",
                "current-title",
                "current-meta",
                "btn-toggle-right",
            ),
            "message_area" to setOf(
                "message-list",
            ),
            "galgame_hud" to setOf(
                "galgame-hud-body",
                "btn-galgame-toggle",
            ),
            "suggestions" to emptySet(),
            "tool_panel" to emptySet(),
            "interactive_panels" to emptySet(),
            "message_item" to emptySet(),
        )

        private val FALLBACK_CSS = """
            body { margin: 0; font-family: sans-serif; }
            .message-list { overflow-y: auto; min-height: 0; flex: 1; }
        """.trimIndent()

        private val _changes = MutableSharedFlow<Set<String>>(extraBufferCapacity = 8)
        val changes: SharedFlow<Set<String>> = _changes.asSharedFlow()

        fun notifyChanged(paths: Set<String> = emptySet()) {
            _changes.tryEmit(paths)
        }

        fun isValidSlotName(slot: String): Boolean = SLOT_NAME_REGEX.matches(slot.trim())

        fun isAllowedMediaFileName(name: String): Boolean {
            val n = name.trim()
            if (n.isBlank() || n.contains('/') || n.contains('\\') || n.contains("..")) return false
            val ext = n.substringAfterLast('.', "").lowercase()
            return ext in MEDIA_EXTENSIONS && n != ext
        }

        /** ASCII-safe media file name for awkward display names. */
        fun sanitizeMediaFileName(name: String): String {
            val trimmed = name.trim().substringAfterLast('/').substringAfterLast('\\')
            val ext = trimmed.substringAfterLast('.', "").lowercase()
            val base = trimmed.substringBeforeLast('.', trimmed)
                .replace(Regex("[^a-zA-Z0-9_-]+"), "-")
                .trim('-')
                .ifBlank { "asset" }
            val safeExt = if (ext in MEDIA_EXTENSIONS) ext else "bin"
            return "$base.$safeExt".take(120)
        }

        fun isTextMediaPath(path: String): Boolean =
            path.startsWith("media/") && path.substringAfterLast('.').equals("svg", ignoreCase = true)

        fun mimeForMedia(path: String): String {
            return when (path.substringAfterLast('.').lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "svg" -> "image/svg+xml"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                "ttf" -> "font/ttf"
                "otf" -> "font/otf"
                "ico" -> "image/x-icon"
                else -> "application/octet-stream"
            }
        }

        fun normalizePackPath(path: String): Result<String> {
            val raw = path.trim().replace('\\', '/').removePrefix("./")
            if (raw.isBlank() || raw.contains("..") || raw.startsWith("/")) {
                return Result.failure(IllegalArgumentException("Invalid path: $path"))
            }
            if (raw in ALLOWED_ROOT_FILES) return Result.success(raw)
            if (raw.startsWith("fragments/") && raw.count { it == '/' } == 1 && raw.endsWith(".html")) {
                val slot = raw.removePrefix("fragments/").removeSuffix(".html")
                if (isValidSlotName(slot)) return Result.success(raw)
                return Result.failure(IllegalArgumentException("Invalid fragment slot: $slot"))
            }
            if (raw.startsWith("media/") && raw.count { it == '/' } == 1) {
                val name = raw.removePrefix("media/")
                if (isAllowedMediaFileName(name)) return Result.success(raw)
                return Result.failure(IllegalArgumentException("Invalid media file: $name"))
            }
            return Result.failure(
                IllegalArgumentException(
                    "Path not allowed: $path. Use styles.css, theme.json, index.html, app.js, " +
                        "fragments/{slot}.html, or media/{file}",
                ),
            )
        }

        /**
         * Map zip entry names to pack-relative paths.
         * Accepts flat packs and one-folder wrappers (`my-theme/styles.css`, `pkg/media/a.png`).
         */
        fun resolveZipEntryPath(entryName: String): String? {
            val path = entryName.trim().replace('\\', '/').removePrefix("./")
            if (path.isBlank() || path.contains("..")) return null
            normalizePackPath(path).getOrNull()?.let { return it }
            val parts = path.split('/').filter { it.isNotEmpty() }
            for (i in 1 until parts.size) {
                val candidate = parts.drop(i).joinToString("/")
                normalizePackPath(candidate).getOrNull()?.let { return it }
            }
            return null
        }

        fun fragmentSlotFromPath(path: String): String? {
            val rel = normalizePackPath(path).getOrNull() ?: return null
            if (!rel.startsWith("fragments/") || !rel.endsWith(".html")) return null
            return rel.removePrefix("fragments/").removeSuffix(".html")
        }

        fun isShellPath(path: String): Boolean {
            val rel = normalizePackPath(path).getOrNull() ?: return false
            return rel == SHELL_INDEX || rel == SHELL_APP_JS
        }

        fun applyEditToContent(
            content: String,
            oldString: String,
            newString: String,
            replaceAll: Boolean,
        ): Result<EditApplyResult> {
            return io.legado.app.domain.usecase.FileToolsStrReplace.applyEdit(
                content, oldString, newString, replaceAll,
            ).map { EditApplyResult(it.content, it.replacements) }
        }

        fun diagnoseEditFailure(
            content: String,
            path: String,
            oldString: String,
            @Suppress("UNUSED_PARAMETER") cause: Throwable? = null,
        ): PatchOpException {
            val count = io.legado.app.domain.usecase.FileToolsStrReplace.countOccurrences(content, oldString)
            return when {
                count == 0 -> {
                    val probe = oldString.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                        .take(64)
                    val hit = if (probe.length >= 4) content.indexOf(probe) else -1
                    val (offset, snip) = if (hit >= 0) {
                        snippetAroundIndex(content, hit)
                    } else {
                        snippetAroundIndex(content, 0)
                    }
                    PatchOpException(
                        message = "old_string not found in $path (0 matches)",
                        path = path,
                        matchCount = 0,
                        suggestedOffset = offset,
                        snippet = snip,
                        hint = "Re-read $path at offset=$offset (limit ~40) and widen old_string context",
                    )
                }
                else -> {
                    val indices = mutableListOf<Int>()
                    var idx = 0
                    while (indices.size < 3) {
                        val found = content.indexOf(oldString, idx)
                        if (found < 0) break
                        indices += found
                        idx = found + oldString.length
                    }
                    val parts = indices.map { at ->
                        val (line, snip) = snippetAroundIndex(content, at)
                        "L$line:\n$snip"
                    }
                    val firstOffset = indices.firstOrNull()?.let { snippetAroundIndex(content, it).first } ?: 1
                    PatchOpException(
                        message = "old_string found $count times in $path; pass replace_all=true or include more context",
                        path = path,
                        matchCount = count,
                        suggestedOffset = firstOffset,
                        snippet = parts.joinToString("\n---\n"),
                        hint = "Narrow old_string so it matches once, or set replace_all=true",
                    )
                }
            }
        }

        /** 1-based line + short snippet around [index]. */
        fun snippetAroundIndex(content: String, index: Int, radius: Int = 2): Pair<Int, String> {
            if (content.isEmpty()) return 1 to ""
            val safeIdx = index.coerceIn(0, content.lastIndex.coerceAtLeast(0))
            var lineStart = 1
            var i = 0
            while (i < safeIdx) {
                if (content[i] == '\n') lineStart++
                i++
            }
            val lines = content.split('\n')
            val from = (lineStart - 1 - radius).coerceAtLeast(0)
            val to = (lineStart - 1 + radius).coerceAtMost(lines.lastIndex)
            val snip = (from..to).joinToString("\n") { li ->
                val n = li + 1
                val mark = if (n == lineStart) ">" else " "
                "$mark$n|${lines[li].take(160)}"
            }
            return lineStart to snip
        }

        fun truncateDiff(text: String, maxChars: Int = 240): String {
            if (text.length <= maxChars) return text
            return text.take(maxChars) + "…(${text.length} chars)"
        }

        fun prepareShellHtml(raw: String): String =
            raw.trim()
                .removeSurrounding("```html", "```")
                .removeSurrounding("```", "```")
                .trim()

        fun prepareShellJs(raw: String): String =
            raw.trim()
                .removeSurrounding("```javascript", "```")
                .removeSurrounding("```js", "```")
                .removeSurrounding("```", "```")
                .trim()

        /**
         * Soft bootstrap check: theme.css + app.js references.
         * DOM ids are not hard-required (free shells bind in app.js).
         */
        fun validateIndexHtmlOrThrow(html: String) {
            val missing = mutableListOf<String>()
            if (!html.contains("theme.css", ignoreCase = true)) {
                missing += "link[href*=theme.css]"
            }
            if (!html.contains("app.js", ignoreCase = true)) {
                missing += "script[src*=app.js]"
            }
            if (missing.isNotEmpty()) {
                throw IllegalArgumentException(
                    "index.html missing bootstrap reference(s): ${missing.joinToString(", ")}. " +
                        "Pass skipShellValidation=true to bypass.",
                )
            }
        }

        fun validateAppJsOrThrow(js: String) {
            val missing = mutableListOf<String>()
            if (!js.contains("LegadoBridge")) missing += "LegadoBridge"
            if (!js.contains("postIntent")) missing += "postIntent"
            if (!js.contains("LegadoAiChat") && !js.contains("window.LegadoAiChat")) {
                missing += "LegadoAiChat"
            }
            if (!js.contains("applyState")) missing += "applyState"
            if (missing.isNotEmpty()) {
                throw IllegalArgumentException(
                    "app.js missing required contract(s): ${missing.joinToString(", ")}. " +
                        "Pass skipShellValidation=true to bypass.",
                )
            }
        }

        fun sanitizeHtmlFragment(raw: String): String {
            var cleaned = raw.trim()
                .removeSurrounding("```html", "```")
                .removeSurrounding("```", "```")
                .trim()
            cleaned = cleaned
                .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                .replace(Regex("<script[^>]*/>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("javascript:", RegexOption.IGNORE_CASE), "")
            return cleaned.trim()
        }

        /** Returns missing required element ids for [slot], empty if valid / unknown slot. */
        fun missingRequiredIds(slot: String, html: String): List<String> {
            val required = REQUIRED_IDS_BY_SLOT[slot.trim()] ?: return emptyList()
            if (required.isEmpty()) return emptyList()
            return required.filter { id -> !containsHtmlId(html, id) }.sorted()
        }

        fun fragmentIdWarnings(slot: String, html: String): List<String> {
            val missing = missingRequiredIds(slot, html)
            if (missing.isEmpty()) return emptyList()
            return listOf(
                "Fragment `$slot` missing expected id(s) for default shell: ${missing.joinToString(", ")}",
            )
        }

        /** Advisory only; patches use [fragmentIdWarnings] instead of failing. */
        fun validateFragmentOrThrow(slot: String, html: String) {
            val warnings = fragmentIdWarnings(slot, html)
            if (warnings.isNotEmpty()) {
                throw IllegalArgumentException(warnings.first())
            }
        }

        fun containsHtmlId(html: String, id: String): Boolean {
            if (id.isBlank()) return true
            val quoted = Regex(
                """\bid\s*=\s*(["'])${Regex.escape(id)}\1""",
                RegexOption.IGNORE_CASE,
            )
            val unquoted = Regex(
                """\bid\s*=\s*${Regex.escape(id)}(?=[\s>])""",
                RegexOption.IGNORE_CASE,
            )
            return quoted.containsMatchIn(html) || unquoted.containsMatchIn(html)
        }
    }
}
