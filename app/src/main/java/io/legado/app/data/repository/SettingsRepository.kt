package io.legado.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.legado.app.constant.PreferKey
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.localDataStore
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsValue
import io.legado.app.help.config.rawPrefValue
import io.legado.app.help.config.setPrefValue
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            // 不要用 androidx.datastore 的 SharedPreferencesMigration：它在每次冷启动
            // （SP 非空）时都会把默认 SharedPreferences 整体搬进 DataStore，随后 cleanUp()
            // 无条件 clear() 并删除 SP 文件；而本应用绝大多数设置（AppConfig/ThemeConfig/
            // ReadBookConfig、getPref*/putPref*、PreferenceFragment、Backup/Restore）仍以
            // 默认 SharedPreferences 为唯一读写源，结果就是每次重启后主题、阅读界面预设等
            // 全部恢复默认。这里换成非破坏性播种 + 一次性回填，SP 仍是活存储。
            SpToDsSeedMigration(context),
            DsToSpRepairMigration(context),
            LocalUiStatusMigration(context),
            ShowBrightnessViewMigration,
        )
    }
)

/** 旧代码读写设置的默认 SharedPreferences 文件名（与 PreferenceManager 默认名一致）。 */
private fun Context.legacySp(): SharedPreferences =
    getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)

/**
 * 非破坏性 SP → DataStore 播种迁移。
 *
 * 只把默认 SharedPreferences 中 DataStore 缺失（按 key 名判等）的条目复制进 settings
 * DataStore，**绝不删除/清空 SharedPreferences**。SP 仍是旧代码路径的活存储，DataStore
 * 仅为新仓库做镜像/播种；两侧同名键以先写者为准，互不覆盖。
 *
 * shouldMigrate 只在"SP 中存在 DS 没有的 key"时为 true，播种完成后即收敛，幂等。
 */
internal class SpToDsSeedMigration(
    private val context: Context,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        context.legacySp().all.keys.any { key -> currentData.rawPrefValue(key) == null }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            context.legacySp().all.forEach { (key, value) ->
                if (currentData.rawPrefValue(key) == null) {
                    setPrefValue(key, value)
                }
            }
        }

    override suspend fun cleanUp() = Unit
}

/** 一次性 DS → SP 回填修复的完成标记（存于 settings DataStore，不下发到 SP）。 */
private val SpDsRepairDoneKey = booleanPreferencesKey("__sp_ds_repair_done")

/**
 * 一次性 DS → SP 回填修复迁移。
 *
 * 目的：修复被旧版 SharedPreferencesMigration 清空 SP 的存量安装——它们的旧设置只残留在
 * settings DataStore 里，若不回填，升级后主题/阅读设置依旧读默认值。
 * 只填充 SP 缺失的 key（不覆盖 SP 已有值），内部标记（"__" 前缀）不回填进 SP；
 * 用 DS 内标记位保证整个流程只执行一次，之后不再把 DS 反向写回 SP（避免 removePref
 * 删除的键被"复活"）。
 */
internal class DsToSpRepairMigration(
    private val context: Context,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[SpDsRepairDoneKey] != true && currentData.asMap().isNotEmpty()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val sp = context.legacySp()
        val edit = sp.edit()
        currentData.asMap().forEach { (key, value) ->
            if (!key.name.startsWith(INTERNAL_PREFIX) && !sp.contains(key.name)) {
                edit.putLegacyValue(key.name, value)
            }
        }
        edit.commit()
        return currentData.toMutablePreferences().apply {
            this[SpDsRepairDoneKey] = true
        }
    }

    override suspend fun cleanUp() = Unit

    private companion object {
        const val INTERNAL_PREFIX = "__"
    }
}

/**
 * 把 DataStore 里的值按"SP 消费者期望的类型"写回 SP。
 * 已知的类型漂移：showBrightnessView 在 DataStore 侧被 [ShowBrightnessViewMigration] 存成
 * "1"/"0" 字符串，而旧代码用 getPrefBoolean 读取，直接写字符串会导致 ClassCastException。
 */
private fun SharedPreferences.Editor.putLegacyValue(key: String, value: Any?) {
    when (value) {
        is Boolean -> putBoolean(key, value)
        is String -> {
            if (key == PreferKey.showBrightnessView) {
                putBoolean(key, value == "1" || value.equals("true", ignoreCase = true))
            } else {
                putString(key, value)
            }
        }
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
        else -> Unit
    }
}

internal class LocalUiStatusMigration(
    private val context: Context,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[LocalPreferencesKeys.MIGRATED_TO_SETTINGS] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val localPreferences = context.localDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
        return mergeMissingLocalPreferences(currentData, localPreferences)
    }

    override suspend fun cleanUp() = Unit
}

internal fun mergeMissingLocalPreferences(
    currentData: Preferences,
    localPreferences: Preferences,
): Preferences = currentData.toMutablePreferences().apply {
    localPreferences.asMap().forEach { (key, value) ->
        if (currentData.rawPrefValue(key.name) == null) {
            setPrefValue(key.name, value)
        }
    }
    this[LocalPreferencesKeys.MIGRATED_TO_SETTINGS] = true
}

internal object ShowBrightnessViewMigration : DataMigration<Preferences> {

    private val booleanKey = booleanPreferencesKey(PreferKey.showBrightnessView)
    private val stringKey = stringPreferencesKey(PreferKey.showBrightnessView)

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().entries.any { (key, value) ->
            key.name == PreferKey.showBrightnessView && value is Boolean
        }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val oldValue = currentData.asMap().entries
            .firstOrNull { (key, value) ->
                key.name == PreferKey.showBrightnessView && value is Boolean
            }
            ?.value as? Boolean
            ?: return currentData
        return currentData.toMutablePreferences().apply {
            remove(booleanKey)
            this[stringKey] = if (oldValue) "1" else "0"
        }
    }

    override suspend fun cleanUp() = Unit
}

/**
 * 设置仓储
 * 以 DataStore 为唯一持久化源，通过 [AppConfigStore] 的有效快照统一读写。
 */
class SettingsRepository {

    fun <T : Any> getPreference(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
        AppConfigStore.preferencesFlow.map { it.compatDsValue(key, defaultValue) }

    suspend fun <T : Any> updatePreference(key: Preferences.Key<T>, value: T) {
        when (value) {
            is String -> AppConfigStore.putString(key.name, value)
            is Int -> AppConfigStore.putInt(key.name, value)
            is Boolean -> AppConfigStore.putBoolean(key.name, value)
            is Long -> AppConfigStore.putLong(key.name, value)
            is Float -> AppConfigStore.putFloat(key.name, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") {
                AppConfigStore.putStringSet(key.name, value as Set<String>)
            }
        }
    }

    // String 类型的快捷访问
    fun getString(key: String, defaultValue: String = ""): Flow<String> =
        getPreference(stringPreferencesKey(key), defaultValue)

    suspend fun putString(key: String, value: String) =
        AppConfigStore.putString(key, value)

    suspend fun putStrings(values: Map<String, String>) {
        AppConfigStore.putAll(values)
    }

    // Int 类型的快捷访问
    fun getInt(key: String, defaultValue: Int = 0): Flow<Int> =
        getPreference(intPreferencesKey(key), defaultValue)

    suspend fun putInt(key: String, value: Int) =
        updatePreference(intPreferencesKey(key), value)

    // Boolean 类型的快捷访问
    fun getBoolean(key: String, defaultValue: Boolean = false): Flow<Boolean> =
        getPreference(booleanPreferencesKey(key), defaultValue)

    suspend fun putBoolean(key: String, value: Boolean) =
        updatePreference(booleanPreferencesKey(key), value)

    // Long 类型的快捷访问
    fun getLong(key: String, defaultValue: Long = 0L): Flow<Long> =
        getPreference(longPreferencesKey(key), defaultValue)

    suspend fun putLong(key: String, value: Long) =
        updatePreference(longPreferencesKey(key), value)

    // Float 类型的快捷访问
    fun getFloat(key: String, defaultValue: Float = 0f): Flow<Float> =
        getPreference(floatPreferencesKey(key), defaultValue)

    suspend fun putFloat(key: String, value: Float) =
        updatePreference(floatPreferencesKey(key), value)

    // Set<String> 类型的快捷访问
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Flow<Set<String>> =
        getPreference(stringSetPreferencesKey(key), defaultValue)

    suspend fun putStringSet(key: String, value: Set<String>) =
        updatePreference(stringSetPreferencesKey(key), value)

    // 移除配置
    suspend fun remove(key: String) {
        AppConfigStore.remove(key)
    }
}
