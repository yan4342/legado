package io.legado.app.help.storage

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.BookCover
import io.legado.app.ui.ai.chat.html.AiChatHtmlThemeStore
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.getSharedPreferences
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 备份
 */
object Backup {

    val backupPath: String by lazy {
        appCtx.filesDir.getFile("backup").createFolderIfNotExist().absolutePath
    }
    val zipFilePath = "${appCtx.externalFiles.absolutePath}${File.separator}tmp_backup.zip"

    private const val TAG = "Backup"

    private val mutex = Mutex()

    private val backupFileNames by lazy {
        arrayOf(
            "bookshelf.json",
            "bookmark.json",
            "bookGroup.json",
            "bookSource.json",
            "rssSources.json",
            "rssStar.json",
            "replaceRule.json",
            "readRecord.json",
            "dailyReadRecord.json",
            "hourlyReadRecord.json",
            "searchHistory.json",
            "sourceSub.json",
            "txtTocRule.json",
            "httpTTS.json",
            "keyboardAssists.json",
            "dictRule.json",
            "aiDictRule.json",
            "aiChatConversation.json",
            "aiChatMessage.json",
            "aiCharacterCard.json",
            "aiWritingPrompt.json",
            "aiProviderProfile.json",
            "aiModelProfile.json",
            "aiTaskPreset.json",
            "aiArtifact.json",
            "aiMemory.json",
            "aiWorldBook.json",
            "aiMemoryTable.json",
            "aiMemoryTableRow.json",
            "aiOutline.json",
            "aiPromptTemplate.json",
            "aiToolConfig.json",
            "aiSkill.json",
            "aiUsageRecord.json",
            "aiWorkspace.json",
            "aiStructuredDataSnapshot.json",
            "aiPromptPipelinePreset.json",
            "aiWorldBookEntry.json",
            "bookSourceVersion.json",
            "bookCharacterCast.json",
            "readAloudVoices.json",
            "bookVoiceBindings.json",
            "chapterSpeechAnalysis.json",
            "chapterSpeechSegments.json",
            "cloudTtsEngines.json",
            "servers.json",
            DirectLinkUpload.ruleFileName,
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            ThemeConfig.configFileName,
            BookCover.configFileName,
            "config.xml"
        )
    }

    private fun getNowZipFileName(): String {
        val backupDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val deviceName = AppConfig.webDavDeviceName
        return if (deviceName?.isNotBlank() == true) {
            "backup${backupDate}-${deviceName}.zip"
        } else {
            "backup${backupDate}.zip"
        }.normalizeFileName()
    }

    private fun shouldBackup(): Boolean {
        val lastBackup = LocalConfig.lastBackup
        return lastBackup + TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis()
    }

    fun autoBack(context: Context) {
        if (shouldBackup()) {
            Coroutine.async {
                mutex.withLock {
                    if (shouldBackup()) {
                        val backupZipFileName = getNowZipFileName()
                        if (!AppWebDav.hasBackUp(backupZipFileName)) {
                            val backupPath = AppConfig.backupPath
                            backup(
                                context,
                                backupPath,
                                toLocal = !backupPath.isNullOrBlank(),
                                toWebDav = true,
                            )
                        } else {
                            LocalConfig.lastBackup = System.currentTimeMillis()
                        }
                    }
                }
            }.onError {
                AppLog.put("自动备份失败: ${it.localizedMessage}", it)
            }
        }
    }

    suspend fun backupLocked(context: Context, path: String?) {
        mutex.withLock {
            withContext(IO) {
                backup(context, path, toLocal = true, toWebDav = true)
            }
        }
    }

    suspend fun backupLocalLocked(context: Context, path: String?) {
        if (path.isNullOrBlank()) {
            throw NoStackTraceException(appCtx.getString(R.string.select_backup_path))
        }
        mutex.withLock {
            withContext(IO) {
                backup(context, path, toLocal = true, toWebDav = false)
            }
        }
    }

    suspend fun backupWebDavLocked(context: Context) {
        mutex.withLock {
            withContext(IO) {
                backup(context, null, toLocal = false, toWebDav = true)
            }
        }
    }

    private suspend fun backup(
        context: Context,
        path: String?,
        toLocal: Boolean = true,
        toWebDav: Boolean = true
    ) {
        LogUtils.d(TAG, "开始备份 path:$path")
        val aes = BackupAES()
        FileUtils.delete(backupPath)
        writeListToJson(appDb.bookDao.all, "bookshelf.json", backupPath)
        writeListToJson(appDb.bookmarkDao.all, "bookmark.json", backupPath)
        writeListToJson(appDb.bookGroupDao.all, "bookGroup.json", backupPath)
        writeListToJson(appDb.bookSourceDao.all, "bookSource.json", backupPath)
        writeListToJson(appDb.rssSourceDao.all, "rssSources.json", backupPath)
        writeListToJson(appDb.rssStarDao.all, "rssStar.json", backupPath)
        writeListToJson(appDb.replaceRuleDao.all, "replaceRule.json", backupPath)
        writeListToJson(appDb.readRecordDao.all, "readRecord.json", backupPath)
        writeListToJson(appDb.dailyReadRecordDao.all, "dailyReadRecord.json", backupPath)
        writeListToJson(appDb.hourlyReadRecordDao.all, "hourlyReadRecord.json", backupPath)
        writeListToJson(appDb.searchKeywordDao.all, "searchHistory.json", backupPath)
        writeListToJson(appDb.ruleSubDao.all, "sourceSub.json", backupPath)
        writeListToJson(appDb.txtTocRuleDao.all, "txtTocRule.json", backupPath)
        writeListToJson(appDb.httpTTSDao.all, "httpTTS.json", backupPath)
        writeListToJson(appDb.keyboardAssistsDao.all, "keyboardAssists.json", backupPath)
        writeListToJson(appDb.dictRuleDao.all, "dictRule.json", backupPath)
        writeListToJson(appDb.aiDictRuleDao.all, "aiDictRule.json", backupPath)
        writeListToJson(appDb.aiChatDao.getAllConversations(), "aiChatConversation.json", backupPath)
        writeListToJson(appDb.aiChatDao.getAllMessages(), "aiChatMessage.json", backupPath)
        writeListToJson(appDb.aiCharacterCardDao.all, "aiCharacterCard.json", backupPath)
        writeListToJson(appDb.aiWritingPromptDao.all, "aiWritingPrompt.json", backupPath)
        writeListToJson(appDb.aiProfileDao.getAllProviders(), "aiProviderProfile.json", backupPath)
        writeListToJson(appDb.aiProfileDao.getAllModels(), "aiModelProfile.json", backupPath)
        writeListToJson(appDb.aiProfileDao.getAllPresets(), "aiTaskPreset.json", backupPath)
        writeListToJson(appDb.aiArtifactDao.all, "aiArtifact.json", backupPath)
        writeListToJson(appDb.aiMemoryDao.all, "aiMemory.json", backupPath)
        writeListToJson(appDb.aiWorldBookDao.all, "aiWorldBook.json", backupPath)
        writeListToJson(appDb.aiMemoryTableDao.getAllTables(), "aiMemoryTable.json", backupPath)
        writeListToJson(appDb.aiMemoryTableDao.getAllRows(), "aiMemoryTableRow.json", backupPath)
        writeListToJson(appDb.aiOutlineDao.getAll(), "aiOutline.json", backupPath)
        writeListToJson(appDb.aiPromptTemplateDao.getAll(), "aiPromptTemplate.json", backupPath)
        writeListToJson(appDb.aiToolConfigDao.getAll(), "aiToolConfig.json", backupPath)
        writeListToJson(appDb.aiSkillDao.getAll(), "aiSkill.json", backupPath)
        writeListToJson(appDb.aiUsageRecordDao.all, "aiUsageRecord.json", backupPath)
        writeListToJson(appDb.aiWorkspaceDao.getAll(), "aiWorkspace.json", backupPath)
        writeListToJson(appDb.aiStructuredDataSnapshotDao.getAll(), "aiStructuredDataSnapshot.json", backupPath)
        writeListToJson(appDb.aiPromptPipelinePresetDao.getAll(), "aiPromptPipelinePreset.json", backupPath)
        writeListToJson(appDb.aiWorldBookEntryDao.getAll(), "aiWorldBookEntry.json", backupPath)
        writeListToJson(appDb.bookSourceVersionDao.getAll(), "bookSourceVersion.json", backupPath)
        writeListToJson(appDb.bookCharacterCastDao.getAll(), "bookCharacterCast.json", backupPath)
        writeListToJson(appDb.readAloudVoiceDao.getAllVoices(), "readAloudVoices.json", backupPath)
        writeListToJson(appDb.readAloudVoiceDao.getAllBindings(), "bookVoiceBindings.json", backupPath)
        writeListToJson(appDb.chapterSpeechDao.getAllAnalyses(), "chapterSpeechAnalysis.json", backupPath)
        writeListToJson(appDb.chapterSpeechDao.getAllSegments(), "chapterSpeechSegments.json", backupPath)
        writeListToJson(appDb.cloudTtsEngineDao.getAll(), "cloudTtsEngines.json", backupPath)
        GSON.toJson(appDb.serverDao.all).let { json ->
            aes.runCatching {
                encryptBase64(json)
            }.getOrDefault(json).let {
                FileUtils.createFileIfNotExist(backupPath + File.separator + "servers.json")
                    .writeText(it)
            }
        }
        currentCoroutineContext().ensureActive()
        GSON.toJson(ReadBookConfig.configList).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ReadBookConfig.configFileName)
                .writeText(it)
        }
        GSON.toJson(ReadBookConfig.shareConfig).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ReadBookConfig.shareConfigFileName)
                .writeText(it)
        }
        GSON.toJson(ThemeConfig.configList).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ThemeConfig.configFileName)
                .writeText(it)
        }
        DirectLinkUpload.getConfig()?.let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + DirectLinkUpload.ruleFileName)
                .writeText(GSON.toJson(it))
        }
        BookCover.getConfig()?.let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + BookCover.configFileName)
                .writeText(GSON.toJson(it))
        }
        currentCoroutineContext().ensureActive()
        appCtx.getSharedPreferences(backupPath, "config")?.let { sp ->
            val edit = sp.edit()
            appCtx.defaultSharedPreferences.all.forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key)) {
                    when (key) {
                        PreferKey.webDavPassword -> {
                            edit.putString(key, aes.runCatching {
                                encryptBase64(value.toString())
                            }.getOrDefault(value.toString()))
                        }

                        else -> when (value) {
                            is Int -> edit.putInt(key, value)
                            is Boolean -> edit.putBoolean(key, value)
                            is Long -> edit.putLong(key, value)
                            is Float -> edit.putFloat(key, value)
                            is String -> edit.putString(key, value)
                        }
                    }
                }
            }
            edit.commit()
        }
        currentCoroutineContext().ensureActive()
        //备份AI聊天HTML主题（用户包目录）
        runCatching {
            val themesDir = File(appCtx.filesDir, AiChatHtmlThemeStore.USER_THEMES_DIR)
            if (themesDir.isDirectory) {
                FileUtils.copy(themesDir, File(backupPath, AiChatHtmlThemeStore.USER_THEMES_DIR))
            }
        }.onFailure {
            AppLog.put("备份AI聊天HTML主题失败: ${it.localizedMessage}", it)
        }
        val zipFileName = getNowZipFileName()
        val paths = arrayListOf(*backupFileNames)
        paths.add(AiChatHtmlThemeStore.USER_THEMES_DIR)
        for (i in 0 until paths.size) {
            paths[i] = backupPath + File.separator + paths[i]
        }
        FileUtils.delete(zipFilePath)
        FileUtils.delete(zipFilePath.replace("tmp_", ""))
        val backupFileName = if (AppConfig.onlyLatestBackup) {
            "backup.zip"
        } else {
            zipFileName
        }
        if (!ZipUtils.zipFiles(paths, zipFilePath)) {
            throw NoStackTraceException("压缩备份文件失败")
        }
        if (toLocal && !path.isNullOrBlank()) {
            when {
                path.isContentScheme() -> {
                    copyBackup(context, path.toUri(), backupFileName)
                }

                else -> {
                    copyBackup(File(path), backupFileName)
                }
            }
        }
        if (toWebDav) {
            try {
                AppWebDav.backUpWebDav(zipFileName)
            } catch (e: Exception) {
                AppLog.put("上传备份文件到WebDav失败: $zipFileName\n${e.localizedMessage}", e)
                if (!toLocal) {
                    throw e
                }
            }
        }
        LocalConfig.lastBackup = System.currentTimeMillis()
        FileUtils.delete(backupPath)
        FileUtils.delete(zipFilePath)
        currentCoroutineContext().ensureActive()
        if (toWebDav) {
            ReadBookConfig.getAllPicBgStr().map {
                if (it.contains(File.separator)) {
                    File(it)
                } else {
                    appCtx.externalFiles.getFile("bg", it)
                }
            }.let {
                AppWebDav.upBgs(it.toTypedArray())
            }
        }
    }

    private suspend fun writeListToJson(list: List<Any>, fileName: String, path: String) {
        currentCoroutineContext().ensureActive()
        withContext(IO) {
            if (list.isNotEmpty()) {
                LogUtils.d(TAG, "阅读备份 $fileName 列表大小 ${list.size}")
                val file = FileUtils.createFileIfNotExist(path + File.separator + fileName)
                file.outputStream().buffered().use {
                    GSON.writeToOutputStream(it, list)
                }
                LogUtils.d(TAG, "阅读备份 $fileName 写入大小 ${file.length()}")
            } else {
                LogUtils.d(TAG, "阅读备份 $fileName 列表为空")
            }
        }
    }

    @Throws(Exception::class)
    @Suppress("SameParameterValue")
    private fun copyBackup(context: Context, uri: Uri, fileName: String) {
        val treeDoc = DocumentFile.fromTreeUri(context, uri)
            ?: throw NoStackTraceException("无法访问备份目录，请重新选择备份路径")
        if (!treeDoc.canWrite()) {
            throw NoStackTraceException("备份目录没有写入权限，请重新选择备份路径")
        }
        treeDoc.findFile(fileName)?.delete()
        val fileDoc = treeDoc.createFile("", fileName)
            ?: throw NoStackTraceException("创建备份文件失败")
        val outputS = fileDoc.openOutputStream()
            ?: throw NoStackTraceException("打开备份文件失败")
        outputS.use {
            FileInputStream(zipFilePath).use { inputS ->
                inputS.copyTo(outputS)
            }
        }
    }

    @Throws(Exception::class)
    @Suppress("SameParameterValue")
    private fun copyBackup(rootFile: File, fileName: String) {
        val dir = when {
            rootFile.isDirectory -> rootFile
            rootFile.isFile -> throw NoStackTraceException("备份路径不能是文件: ${rootFile.path}")
            rootFile.mkdirs() || rootFile.isDirectory -> rootFile
            else -> throw NoStackTraceException("无法创建备份目录: ${rootFile.path}")
        }
        if (!dir.canWrite()) {
            throw NoStackTraceException("备份目录没有写入权限: ${dir.path}")
        }
        FileInputStream(File(zipFilePath)).use { inputS ->
            val file = FileUtils.createFileIfNotExist(dir, fileName)
            if (!file.exists()) {
                throw NoStackTraceException("创建备份文件失败: ${file.path}")
            }
            FileOutputStream(file).use { outputS ->
                inputS.copyTo(outputS)
            }
        }
    }

    fun clearCache() {
        FileUtils.delete(backupPath)
        FileUtils.delete(zipFilePath)
    }
}
