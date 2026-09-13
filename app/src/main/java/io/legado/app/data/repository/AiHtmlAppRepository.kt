package io.legado.app.data.repository

import io.legado.app.data.dao.AiHtmlAppDao
import io.legado.app.data.entities.AiHtmlApp
import io.legado.app.domain.gateway.AiHtmlAppGateway
import io.legado.app.domain.gateway.Either
import io.legado.app.domain.gateway.HtmlAppFileSlice
import io.legado.app.domain.gateway.HtmlAppVersion
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

/**
 * AI HTML App 存取：目录包 + 版本控制。
 *
 * 布局（每 app 一个目录，`{filesDir}/html_apps/{appId}/`）：
 * - index.html            工作树源码（live：玩家经 file:// 加载，改写立即生效）
 * - manifest.json         {id, title, version, dirty, lastCommitMessage, lastCommitAt}
 * - .snapshots/v{n}/index.html  已提交版本快照（供回滚）
 * - commits.log           `<ts>\tv{n}\t<message>` 追加式历史
 *
 * 语义同主题包：writeFile / editFile 只改工作树并标记 dirty；commit 升版本 + 快照 + 清 dirty。
 * publish（新 app 或整体重写）语义 = 覆盖工作树并直接提交一个新版本。
 */
class AiHtmlAppRepository(
    private val dao: AiHtmlAppDao,
) : AiHtmlAppGateway {

    private fun rootDir(): File = File(appCtx.filesDir, "html_apps").apply { mkdirs() }

    private fun appDir(appId: String): File = File(rootDir(), appId)

    private fun indexFile(appId: String): File = File(appDir(appId), "index.html")

    private fun manifestFile(appId: String): File = File(appDir(appId), "manifest.json")

    private fun snapshotFile(appId: String, version: Int): File =
        File(appDir(appId), ".snapshots/v$version/index.html")

    private fun commitLogFile(appId: String): File = File(appDir(appId), "commits.log")

    // ---- manifest ----

    private data class Manifest(
        val id: String = "",
        val title: String = "",
        val version: Int = 1,
        val dirty: Boolean = false,
        val lastCommitMessage: String? = null,
        val lastCommitAt: Long? = null,
    )

    private fun readManifest(appId: String): Manifest? {
        val file = manifestFile(appId)
        if (!file.isFile) return null
        return runCatching { GSON.fromJson(file.readText(), Manifest::class.java) }.getOrNull()
    }

    private fun writeManifest(appId: String, manifest: Manifest) {
        manifestFile(appId).parentFile?.mkdirs()
        manifestFile(appId).writeText(GSON.toJson(manifest), Charsets.UTF_8)
    }

    private fun snapshotVersion(appId: String, version: Int) {
        val src = indexFile(appId)
        if (!src.isFile) return
        val snap = snapshotFile(appId, version)
        snap.parentFile?.mkdirs()
        src.copyTo(snap, overwrite = true)
    }

    private fun appendCommitLog(appId: String, version: Int, message: String) {
        val log = commitLogFile(appId)
        runCatching {
            log.parentFile?.mkdirs()
            log.appendText(
                "${System.currentTimeMillis()}\tv$version\t${message.replace('\t', ' ').replace('\n', ' ')}\n",
                Charsets.UTF_8,
            )
        }
    }

    /** 把工作树提交为新版本：升版本 + 快照 + 日志 + 清 dirty。 */
    private fun commitWorkingTree(appId: String, message: String) {
        val prev = readManifest(appId) ?: Manifest(id = appId, title = appId)
        val next = prev.version + 1
        snapshotVersion(appId, next)
        appendCommitLog(appId, next, message)
        writeManifest(
            appId,
            prev.copy(
                version = next,
                dirty = false,
                lastCommitMessage = message,
                lastCommitAt = System.currentTimeMillis(),
            ),
        )
    }

    // ---- gateway ----

    override fun observeByConversation(conversationId: String): Flow<List<AiHtmlApp>> =
        dao.observeByConversation(conversationId)

    override suspend fun getById(id: String): AiHtmlApp? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    override suspend fun getByMessageId(messageId: String): AiHtmlApp? =
        withContext(Dispatchers.IO) {
            dao.getByMessageId(messageId)
        }

    override suspend fun publish(
        title: String,
        html: String,
        conversationId: String,
        messageId: String,
        appId: String?,
    ): AiHtmlApp = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = appId?.takeIf { it.isNotBlank() }?.let { dao.getById(it) }
        if (existing != null && existing.conversationId == conversationId) {
            // 整体重写既有 app：覆写工作树并直接提交一个新版本。
            indexFile(existing.id).parentFile?.mkdirs()
            indexFile(existing.id).writeText(html, Charsets.UTF_8)
            commitWorkingTree(existing.id, "update: $title")
            val updated = existing.copy(title = title, updatedAt = now)
            dao.upsert(updated)
            updated
        } else {
            // 新建 app：落 v1。
            val id = "htmlapp_${java.util.UUID.randomUUID().toString().replace("-", "")}"
            indexFile(id).parentFile?.mkdirs()
            indexFile(id).writeText(html, Charsets.UTF_8)
            snapshotVersion(id, 1)
            appendCommitLog(id, 1, "create: $title")
            writeManifest(
                id,
                Manifest(
                    id = id,
                    title = title,
                    version = 1,
                    dirty = false,
                    lastCommitMessage = "create",
                    lastCommitAt = now,
                ),
            )
            val app = AiHtmlApp(
                id = id,
                conversationId = conversationId,
                messageId = messageId,
                title = title,
                createdAt = now,
                updatedAt = now,
            )
            dao.upsert(app)
            app
        }
    }

    override suspend fun updateMessageId(appId: String, messageId: String) =
        withContext(Dispatchers.IO) {
            dao.getById(appId)?.let { dao.upsert(it.copy(messageId = messageId)) }
            Unit
        }

    override suspend fun writeFile(appId: String, content: String): String? =
        withContext(Dispatchers.IO) {
            val app = dao.getById(appId) ?: return@withContext "HTML app not found: $appId"
            if (content.isBlank()) return@withContext "content must not be empty"
            indexFile(appId).parentFile?.mkdirs()
            indexFile(appId).writeText(content, Charsets.UTF_8)
            markDirty(appId, app.title)
            null
        }

    override suspend fun editFile(
        appId: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean,
    ): Either<String, Int> = withContext(Dispatchers.IO) {
        val app = dao.getById(appId) ?: return@withContext Either.Left("HTML app not found: $appId")
        if (oldString.isEmpty()) return@withContext Either.Left("old_string must not be empty")
        val file = indexFile(appId)
        if (!file.isFile) return@withContext Either.Left("index.html not found for $appId")
        val current = file.readText(Charsets.UTF_8)
        val edited = applyEditToContent(current, oldString, newString, replaceAll)
            ?: return@withContext Either.Left("old_string not found (0 matches)")
        file.writeText(edited.content, Charsets.UTF_8)
        markDirty(appId, app.title)
        Either.Right(edited.replacements)
    }

    override suspend fun commit(appId: String, message: String?): AiHtmlApp? =
        withContext(Dispatchers.IO) {
            val app = dao.getById(appId) ?: return@withContext null
            val prev = readManifest(appId)
            if (prev?.dirty != true) return@withContext null // nothing to commit
            val msg = message?.trim()?.takeIf { it.isNotBlank() } ?: "commit"
            commitWorkingTree(appId, msg)
            app.copy(updatedAt = System.currentTimeMillis()).also { dao.upsert(it) }
        }

    override suspend fun readHtmlSlice(
        appId: String,
        offset: Int,
        limit: Int,
    ): HtmlAppFileSlice? = withContext(Dispatchers.IO) {
        val file = indexFile(appId)
        if (!file.isFile) return@withContext null
        val lines = file.readText(Charsets.UTF_8).split('\n')
        val total = lines.size
        val start = offset.coerceAtLeast(1)
        val lim = limit.coerceIn(1, 2000)
        val fromIdx = (start - 1).coerceAtMost(total)
        val slice = lines.drop(fromIdx).take(lim)
        val truncated = fromIdx + slice.size < total
        HtmlAppFileSlice(
            path = "index.html",
            content = slice.joinToString("\n"),
            totalLines = total,
            offset = start,
            limit = lim,
            truncated = truncated,
        )
    }

    override suspend fun entryPath(appId: String): String? = withContext(Dispatchers.IO) {
        val file = indexFile(appId)
        if (file.isFile) file.absolutePath else null
    }

    override suspend fun rollback(appId: String, version: Int): AiHtmlApp? =
        withContext(Dispatchers.IO) {
            val app = dao.getById(appId) ?: return@withContext null
            val snap = snapshotFile(appId, version)
            if (!snap.isFile) return@withContext null
            val currentVersion = readManifest(appId)?.version ?: 1
            // 版本单调递增：回滚不覆盖历史。
            val nextVersion = maxOf(currentVersion, version) + 1
            indexFile(appId).parentFile?.mkdirs()
            snap.copyTo(indexFile(appId), overwrite = true)
            snapshotVersion(appId, nextVersion)
            appendCommitLog(appId, nextVersion, "rollback to v$version")
            val prev = readManifest(appId) ?: Manifest(id = appId, title = app.title)
            writeManifest(
                appId,
                prev.copy(
                    version = nextVersion,
                    dirty = false,
                    lastCommitMessage = "rollback to v$version",
                    lastCommitAt = System.currentTimeMillis(),
                ),
            )
            app.copy(updatedAt = System.currentTimeMillis()).also { dao.upsert(it) }
        }

    override suspend fun listVersions(appId: String): List<HtmlAppVersion> =
        withContext(Dispatchers.IO) {
            val log = commitLogFile(appId)
            if (!log.isFile) return@withContext emptyList()
            runCatching {
                log.readLines().mapNotNull { line ->
                    val parts = line.split('\t', limit = 3)
                    if (parts.size < 3) return@mapNotNull null
                    val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
                    val v = parts[1].removePrefix("v").toIntOrNull() ?: return@mapNotNull null
                    HtmlAppVersion(version = v, timestamp = ts, message = parts[2])
                }.sortedByDescending { it.version }
            }.getOrDefault(emptyList())
        }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
        appDir(id).deleteRecursively()
        Unit
    }

    override suspend fun deleteByConversation(conversationId: String) = withContext(Dispatchers.IO) {
        val apps = dao.observeByConversation(conversationId).first()
        apps.forEach { app ->
            appDir(app.id).deleteRecursively()
        }
        dao.deleteByConversation(conversationId)
        Unit
    }

    private fun markDirty(appId: String, title: String) {
        val prev = readManifest(appId) ?: Manifest(id = appId, title = title)
        writeManifest(appId, prev.copy(dirty = true))
    }

    private data class EditResult(val content: String, val replacements: Int)

    private fun applyEditToContent(
        content: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean,
    ): EditResult? {
        if (oldString.isEmpty()) return null
        val idx = content.indexOf(oldString)
        if (idx < 0) return null
        return if (replaceAll) {
            EditResult(content.replace(oldString, newString), content.countSequence(oldString))
        } else {
            EditResult(
                content.substring(0, idx) + newString + content.substring(idx + oldString.length),
                1,
            )
        }
    }

    /** Count non-overlapping occurrences. */
    private fun String.countSequence(seq: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val i = indexOf(seq, from)
            if (i < 0) break
            count++
            from = i + seq.length
        }
        return count
    }
}
