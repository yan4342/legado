package io.legado.app.data.repository

import io.legado.app.data.dao.RssSourceDao
import io.legado.app.data.entities.RssSource
import io.legado.app.help.source.SourceHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RssSourceRepository(private val dao: RssSourceDao) {

    fun flowAll(): Flow<List<RssSource>> = dao.flowAll()

    fun flowGroups(): Flow<List<String>> = dao.flowGroups()

    suspend fun getByKey(sourceUrl: String): RssSource? = withContext(Dispatchers.IO) {
        dao.getByKey(sourceUrl)
    }

    suspend fun insertSources(vararg sources: RssSource) = withContext(Dispatchers.IO) {
        dao.insert(*sources)
    }

    suspend fun updateSources(vararg sources: RssSource) = withContext(Dispatchers.IO) {
        dao.update(*sources)
    }

    suspend fun topSources(sources: List<RssSource>) = withContext(Dispatchers.IO) {
        val minOrder = dao.minOrder - 1
        val sorted = sources.sortedBy { it.customOrder }
        val updates = Array(sorted.size) { index ->
            sorted[index].copy(customOrder = minOrder - index)
        }
        dao.update(*updates)
    }

    suspend fun bottomSources(sources: List<RssSource>) = withContext(Dispatchers.IO) {
        val maxOrder = dao.maxOrder + 1
        val sorted = sources.sortedBy { it.customOrder }
        val updates = Array(sorted.size) { index ->
            sorted[index].copy(customOrder = maxOrder + index)
        }
        dao.update(*updates)
    }

    suspend fun deleteSources(sources: List<RssSource>) {
        withContext(Dispatchers.IO) {
            SourceHelp.deleteRssSources(sources)
        }
    }

    suspend fun deleteByIds(ids: Set<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        SourceHelp.deleteRssSources(dao.getRssSources(*ids.toTypedArray()))
    }

    suspend fun saveOrder(sources: List<RssSource>) = withContext(Dispatchers.IO) {
        val updated = sources.mapIndexed { index, source ->
            source.copy(customOrder = index + 1)
        }
        dao.update(*updated.toTypedArray())
    }

    suspend fun normalizeOrder() = withContext(Dispatchers.IO) {
        val updated = dao.all.mapIndexed { index, source ->
            source.copy(customOrder = index + 1)
        }
        dao.update(*updated.toTypedArray())
    }

    suspend fun setEnabled(ids: Set<String>, enabled: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val updated = dao.getRssSources(*ids.toTypedArray()).map { it.copy(enabled = enabled) }
        dao.update(*updated.toTypedArray())
    }

    suspend fun addGroup(ids: Set<String>, group: String) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val updated = dao.getRssSources(*ids.toTypedArray()).map { it.copy().addGroup(group) }
        dao.update(*updated.toTypedArray())
    }

    suspend fun removeGroup(ids: Set<String>, group: String) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val updated = dao.getRssSources(*ids.toTypedArray()).map { it.copy().removeGroup(group) }
        dao.update(*updated.toTypedArray())
    }

    suspend fun renameGroup(oldGroup: String, newGroup: String) = withContext(Dispatchers.IO) {
        val sources = dao.getByGroup(oldGroup)
        sources.forEach { source ->
            source.sourceGroup?.split(",")?.toHashSet()?.let { groups ->
                groups.remove(oldGroup)
                if (newGroup.isNotEmpty()) groups.add(newGroup)
                source.sourceGroup = groups.joinToString(",")
            }
        }
        dao.update(*sources.toTypedArray())
    }

    suspend fun deleteGroup(group: String) = withContext(Dispatchers.IO) {
        val sources = dao.getByGroup(group)
        sources.forEach { source ->
            source.sourceGroup?.split(",")?.toHashSet()?.let { groups ->
                groups.remove(group)
                source.sourceGroup = groups.joinToString(",")
            }
        }
        dao.update(*sources.toTypedArray())
    }

    suspend fun addGroupForNoGroup(group: String) = withContext(Dispatchers.IO) {
        val sources = dao.noGroup
        sources.forEach { source -> source.sourceGroup = group }
        dao.update(*sources.toTypedArray())
    }

    suspend fun getAll(): List<RssSource> = withContext(Dispatchers.IO) { dao.all }

    suspend fun getAllEnabled(): List<RssSource> = withContext(Dispatchers.IO) {
        dao.all.filter { it.enabled }
    }

    suspend fun getAllDisabled(): List<RssSource> = withContext(Dispatchers.IO) {
        dao.all.filter { !it.enabled }
    }

    suspend fun getAllLogin(): List<RssSource> = withContext(Dispatchers.IO) {
        dao.all.filter { !it.loginUrl.isNullOrBlank() }
    }

    suspend fun getAllNoGroup(): List<RssSource> = withContext(Dispatchers.IO) { dao.noGroup }

    suspend fun getByGroup(group: String): List<RssSource> = withContext(Dispatchers.IO) {
        dao.getByGroup(group)
    }

    suspend fun search(query: String): List<RssSource> = withContext(Dispatchers.IO) {
        dao.all.filter {
            it.sourceName.contains(query, true) ||
                it.sourceUrl.contains(query, true) ||
                it.sourceGroup?.contains(query, true) == true ||
                it.sourceComment?.contains(query, true) == true
        }
    }
}
