package io.legado.app.help.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppConst.androidId
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiDictRule
import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessage
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.data.entities.AiWritingPrompt
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.data.entities.AiOutline
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.entities.AiPromptPipelinePreset
import io.legado.app.data.entities.AiStructuredDataSnapshot
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.data.entities.AiSkill
import io.legado.app.data.entities.AiUsageRecord
import io.legado.app.data.entities.AiWorkspace
import io.legado.app.data.entities.AiWorldBookEntry
import io.legado.app.data.entities.BookSourceVersion
import io.legado.app.data.entities.BookCharacterCast
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.ChapterSpeechAnalysisEntity
import io.legado.app.data.entities.ChapterSpeechSegmentEntity
import io.legado.app.data.entities.CloudTtsEngineEntity
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.DailyReadRecord
import io.legado.app.data.entities.HourlyReadRecord
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReadRecord
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.upType
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.BookCover
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.ai.chat.html.AiChatHtmlThemeStore
import io.legado.app.utils.ACache
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getSharedPreferences
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.openInputStream
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream

/**
 * 恢复
 */
object Restore {

    private val mutex = Mutex()

    private const val TAG = "Restore"

    suspend fun restore(context: Context, uri: Uri) {
        LogUtils.d(TAG, "开始恢复备份 uri:$uri")
        kotlin.runCatching {
            FileUtils.delete(Backup.backupPath)
            if (uri.isContentScheme()) {
                DocumentFile.fromSingleUri(context, uri)!!.openInputStream()!!.use {
                    ZipUtils.unZipToPath(it, Backup.backupPath)
                }
            } else {
                ZipUtils.unZipToPath(File(uri.path!!), Backup.backupPath)
            }
        }.onFailure {
            AppLog.put("解压备份文件失败: $uri\n${it.localizedMessage}", it)
            appCtx.toastOnUi("解压备份文件失败: ${it.localizedMessage}")
            return
        }
        kotlin.runCatching {
            restoreLocked(Backup.backupPath)
            LocalConfig.lastBackup = System.currentTimeMillis()
        }.onFailure {
            appCtx.toastOnUi("恢复备份数据失败: ${it.localizedMessage}")
            AppLog.put("恢复备份数据失败: ${it.localizedMessage}", it)
        }
    }

    suspend fun restoreLocked(path: String) {
        mutex.withLock {
            restore(path)
        }
    }

    private suspend fun restore(path: String) {
        val aes = BackupAES()
        fileToListT<Book>(path, "bookshelf.json")?.let {
            it.forEach { book ->
                book.upType()
            }
            it.filter { book -> book.isLocal }
                .forEach { book ->
                    book.coverUrl = LocalBook.getCoverPath(book)
                }
            val newBooks = arrayListOf<Book>()
            val ignoreLocalBook = BackupConfig.ignoreLocalBook
            it.forEach { book ->
                if (ignoreLocalBook && book.isLocal) {
                    return@forEach
                }
                if (appDb.bookDao.has(book.bookUrl)) {
                    try {
                        appDb.bookDao.update(book)
                    } catch (_: SQLiteConstraintException) {
                        appDb.bookDao.insert(book)
                    }
                } else {
                    newBooks.add(book)
                }
            }
            appDb.bookDao.insert(*newBooks.toTypedArray())
        }
        fileToListT<Bookmark>(path, "bookmark.json")?.let {
            appDb.bookmarkDao.insert(*it.toTypedArray())
        }
        fileToListT<BookGroup>(path, "bookGroup.json")?.let {
            appDb.bookGroupDao.insert(*it.toTypedArray())
        }
        fileToListT<BookSource>(path, "bookSource.json")?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        } ?: run {
            val bookSourceFile = File(path, "bookSource.json")
            if (bookSourceFile.exists()) {
                val json = bookSourceFile.readText()
                ImportOldData.importOldSource(json)
            }
        }
        fileToListT<RssSource>(path, "rssSources.json")?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }
        fileToListT<RssStar>(path, "rssStar.json")?.let {
            appDb.rssStarDao.insert(*it.toTypedArray())
        }
        fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
            appDb.replaceRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
            appDb.searchKeywordDao.insert(*it.toTypedArray())
        }
        fileToListT<RuleSub>(path, "sourceSub.json")?.let {
            appDb.ruleSubDao.insert(*it.toTypedArray())
        }
        fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
            appDb.txtTocRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
            appDb.httpTTSDao.insert(*it.toTypedArray())
        }
        fileToListT<DictRule>(path, "dictRule.json")?.let {
            appDb.dictRuleDao.insert(*it.toTypedArray())
        }
        restoreAiList<AiDictRule>(path, "aiDictRule.json") {
            appDb.aiDictRuleDao.insert(*it.toTypedArray())
        }
        restoreAiList<AiChatConversation>(path, "aiChatConversation.json") {
            it.forEach { conv -> appDb.aiChatDao.insertConversation(conv.ensurePersistDefaults()) }
        }
        restoreAiList<AiChatMessage>(path, "aiChatMessage.json") {
            it.forEach { msg -> appDb.aiChatDao.insertMessage(msg) }
        }
        restoreAiList<AiCharacterCard>(path, "aiCharacterCard.json") {
            it.forEach { card -> appDb.aiCharacterCardDao.insert(card) }
        }
        restoreAiList<AiWritingPrompt>(path, "aiWritingPrompt.json") {
            it.forEach { prompt -> appDb.aiWritingPromptDao.insert(prompt) }
        }
        restoreAiList<AiProviderProfile>(path, "aiProviderProfile.json") {
            it.forEach { provider -> appDb.aiProfileDao.insertProvider(provider) }
        }
        restoreAiList<AiModelProfile>(path, "aiModelProfile.json") {
            it.forEach { model -> appDb.aiProfileDao.insertModel(model) }
        }
        restoreAiList<AiTaskPreset>(path, "aiTaskPreset.json") {
            it.forEach { preset -> appDb.aiProfileDao.insertPreset(preset) }
        }
        restoreAiList<AiArtifact>(path, "aiArtifact.json") {
            it.forEach { artifact -> appDb.aiArtifactDao.upsert(artifact) }
        }
        restoreAiList<AiMemory>(path, "aiMemory.json") {
            it.forEach { memory -> appDb.aiMemoryDao.upsert(memory) }
        }
        restoreAiList<AiWorldBook>(path, "aiWorldBook.json") {
            it.forEach { wb -> appDb.aiWorldBookDao.insert(wb) }
        }
        restoreAiList<AiMemoryTable>(path, "aiMemoryTable.json") {
            it.forEach { table -> appDb.aiMemoryTableDao.upsertTable(table) }
        }
        restoreAiList<AiMemoryTableRow>(path, "aiMemoryTableRow.json") {
            it.forEach { row -> appDb.aiMemoryTableDao.upsertRow(row) }
        }
        restoreAiList<AiOutline>(path, "aiOutline.json") {
            it.forEach { outline -> appDb.aiOutlineDao.upsert(outline) }
        }
        restoreAiList<AiPromptTemplate>(path, "aiPromptTemplate.json") {
            it.forEach { template -> appDb.aiPromptTemplateDao.upsert(template) }
        }
        restoreAiList<AiToolConfig>(path, "aiToolConfig.json") {
            it.forEach { config -> appDb.aiToolConfigDao.insert(config) }
        }
        restoreAiList<AiSkill>(path, "aiSkill.json") {
            it.forEach { skill -> appDb.aiSkillDao.insert(skill) }
        }
        restoreAiList<AiUsageRecord>(path, "aiUsageRecord.json") {
            it.forEach { record -> appDb.aiUsageRecordDao.insert(record) }
        }
        restoreAiList<AiWorkspace>(path, "aiWorkspace.json") {
            it.forEach { workspace -> appDb.aiWorkspaceDao.upsert(workspace) }
        }
        restoreAiList<AiStructuredDataSnapshot>(path, "aiStructuredDataSnapshot.json") {
            it.forEach { snapshot -> appDb.aiStructuredDataSnapshotDao.insert(snapshot) }
        }
        restoreAiList<AiPromptPipelinePreset>(path, "aiPromptPipelinePreset.json") {
            it.forEach { preset -> appDb.aiPromptPipelinePresetDao.upsert(preset) }
        }
        restoreAiList<AiWorldBookEntry>(path, "aiWorldBookEntry.json") {
            it.forEach { entry -> appDb.aiWorldBookEntryDao.upsert(entry) }
        }
        restoreAiList<BookSourceVersion>(path, "bookSourceVersion.json") {
            it.forEach { version -> appDb.bookSourceVersionDao.insert(version) }
        }
        restoreAiList<BookCharacterCast>(path, "bookCharacterCast.json") {
            it.forEach { cast -> appDb.bookCharacterCastDao.insert(cast) }
        }
        restoreAiList<ReadAloudVoiceEntity>(path, "readAloudVoices.json") {
            it.forEach { voice -> appDb.readAloudVoiceDao.upsertVoice(voice) }
        }
        restoreAiList<BookVoiceBindingEntity>(path, "bookVoiceBindings.json") {
            it.forEach { binding -> appDb.readAloudVoiceDao.upsertBinding(binding) }
        }
        restoreAiList<ChapterSpeechAnalysisEntity>(path, "chapterSpeechAnalysis.json") {
            it.forEach { analysis -> appDb.chapterSpeechDao.upsertAnalysis(analysis) }
        }
        restoreAiList<ChapterSpeechSegmentEntity>(path, "chapterSpeechSegments.json") {
            if (it.isNotEmpty()) appDb.chapterSpeechDao.upsertSegments(it)
        }
        restoreAiList<CloudTtsEngineEntity>(path, "cloudTtsEngines.json") {
            it.forEach { engine -> appDb.cloudTtsEngineDao.upsert(engine) }
        }
        fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
            appDb.keyboardAssistsDao.insert(*it.toTypedArray())
        }
        fileToListT<ReadRecord>(path, "readRecord.json")?.let {
            it.forEach { readRecord ->
                //判断是不是本机记录
                if (readRecord.deviceId != androidId) {
                    appDb.readRecordDao.insert(readRecord)
                } else {
                    val time = appDb.readRecordDao
                        .getReadTime(readRecord.deviceId, readRecord.bookName)
                    if (time == null || time < readRecord.readTime) {
                        appDb.readRecordDao.insert(readRecord)
                    }
                }
            }
        }
        fileToListT<DailyReadRecord>(path, "dailyReadRecord.json")?.let {
            appDb.dailyReadRecordDao.insert(*it.toTypedArray())
        }
        fileToListT<HourlyReadRecord>(path, "hourlyReadRecord.json")?.let {
            appDb.hourlyReadRecordDao.insert(*it.toTypedArray())
        }
        File(path, "servers.json").takeIf {
            it.exists()
        }?.runCatching {
            var json = readText()
            if (!json.isJsonArray()) {
                json = aes.decryptStr(json)
            }
            GSON.fromJsonArray<Server>(json).getOrNull()?.let {
                appDb.serverDao.insert(*it.toTypedArray())
            }
        }?.onFailure {
            AppLog.put("恢复服务器配置失败: ${it.localizedMessage}", it)
        }
        File(path, DirectLinkUpload.ruleFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, json)
        }?.onFailure {
            AppLog.put("恢复直链上传配置失败: ${it.localizedMessage}", it)
        }
        //恢复主题配置
        File(path, ThemeConfig.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            FileUtils.delete(ThemeConfig.configFilePath)
            copyTo(File(ThemeConfig.configFilePath))
            ThemeConfig.upConfig()
        }?.onFailure {
            AppLog.put("恢复主题配置失败: ${it.localizedMessage}", it)
        }
        File(path, BookCover.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            BookCover.saveCoverRule(json)
        }?.onFailure {
            AppLog.put("恢复封面规则配置失败: ${it.localizedMessage}", it)
        }
        if (!BackupConfig.ignoreReadConfig) {
            //恢复阅读界面配置
            File(path, ReadBookConfig.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.configFilePath)
                copyTo(File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
            }?.onFailure {
                AppLog.put("恢复阅读界面配置失败: ${it.localizedMessage}", it)
            }
            File(path, ReadBookConfig.shareConfigFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                copyTo(File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }?.onFailure {
                AppLog.put("恢复阅读界面配置失败: ${it.localizedMessage}", it)
            }
        }
        //恢复AI聊天HTML主题（用户包目录）
        if (!BackupConfig.ignoreAiData) {
            val srcThemesDir = File(path, AiChatHtmlThemeStore.USER_THEMES_DIR)
            if (srcThemesDir.isDirectory) {
                runCatching {
                    val dest = File(appCtx.filesDir, AiChatHtmlThemeStore.USER_THEMES_DIR)
                    // 逐主题覆盖：备份中同名主题覆盖本地，本地多出的主题保留
                    srcThemesDir.listFiles()?.filter { it.isDirectory }?.forEach { themeDir ->
                        val destTheme = File(dest, themeDir.name)
                        FileUtils.delete(destTheme, true)
                        FileUtils.copy(themeDir, destTheme)
                    }
                    AiChatHtmlThemeStore.notifyChanged()
                }.onFailure {
                    AppLog.put("恢复AI聊天HTML主题失败: ${it.localizedMessage}", it)
                }
            }
        }
        //AppWebDav.downBgs()
        appCtx.getSharedPreferences(path, "config")?.all?.let { map ->
            val edit = appCtx.defaultSharedPreferences.edit()
            // Phase 4 收口：恢复值同时写入 settings DataStore（收编 key 的唯一读源），
            // pending overlay 保证恢复后立即生效；SP 写入保留，导出源与未收编 key 不受影响
            val restored = LinkedHashMap<String, Any?>()

            map.forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key)) {
                    when (key) {
                        PreferKey.webDavPassword -> {
                            kotlin.runCatching {
                                aes.decryptStr(value.toString())
                            }.getOrNull()?.let {
                                edit.putString(key, it)
                                restored[key] = it
                            } ?: let {
                                if (appCtx.getPrefString(PreferKey.webDavPassword)
                                        .isNullOrBlank()
                                ) {
                                    edit.putString(key, value.toString())
                                    restored[key] = value.toString()
                                }
                            }
                        }

                        else -> {
                            when (value) {
                                is Int -> edit.putInt(key, value)
                                is Boolean -> edit.putBoolean(key, value)
                                is Long -> edit.putLong(key, value)
                                is Float -> edit.putFloat(key, value)
                                is String -> edit.putString(key, value)
                                else -> return@forEach
                            }
                            restored[key] = value
                        }
                    }
                }
            }
            edit.commit()
            AppConfigStore.putAll(restored)
        }
        ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 46)
        }
        appCtx.toastOnUi(R.string.restore_success)
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    private inline fun <reified T> fileToListT(path: String, fileName: String): List<T>? {
        try {
            val file = File(path, fileName)
            if (file.exists()) {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件大小 ${file.length()}")
                FileInputStream(file).use {
                    return GSON.fromJsonArray<T>(it).getOrThrow().also { list ->
                        LogUtils.d(TAG, "阅读恢复备份 $fileName 列表大小 ${list.size}")
                    }
                }
            } else {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件不存在")
            }
        } catch (e: Exception) {
            AppLog.put("恢复备份时解析 $fileName 失败: ${e.localizedMessage}", e)
            appCtx.toastOnUi("恢复 $fileName 失败: ${e.localizedMessage}")
        }
        return null
    }

    private inline fun <reified T> restoreAiList(
        path: String,
        fileName: String,
        restore: (List<T>) -> Unit,
    ) {
        if (!BackupConfig.aiDataFileShouldRestore(fileName)) {
            LogUtils.d(TAG, "阅读恢复备份 $fileName 已忽略")
            return
        }
        fileToListT<T>(path, fileName)?.let(restore)
    }

}