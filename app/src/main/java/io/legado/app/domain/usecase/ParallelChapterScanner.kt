package io.legado.app.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 并行扫描共享骨架：将 [items] 按章序分片并行扫描，结果严格按原顺序逐片回调。
 *
 * 实现原理：分片保持原顺序；`async` 列表按序 [kotlinx.coroutines.Deferred.await]，
 * 前面片完成即回调，已完成的后片立即返回，保证「并行执行、按序消费」——
 * 总耗时接近最慢分片，而回调顺序与输入顺序完全一致。
 *
 * 适合已缓存章节的 CPU 密集扫描（简繁转换 + 关键词匹配）。网络 fetch 等 IO 密集
 * 工作请勿使用本骨架并行（会打爆上游），保持调用方串行。
 */
object ParallelChapterScanner {

    /** 并行分片数上限（CPU 密集扫描，避免过度调度）。 */
    const val MAX_PARALLEL_CHUNKS = 4

    /**
     * 并行扫描并严格按序回调。
     * @param items 保持顺序的待扫描集合（通常为章节列表）
     * @param scanChunk 每片的扫描函数，返回该片的结果对象（可为 `List<RESULT>`）
     * @param onChunkResult 按 [items] 顺序逐片回调结果，运行在调用方协程
     * @param partitions 分片数，默认按可用核数（上限 [MAX_PARALLEL_CHUNKS]）
     */
    suspend fun <ITEM, RESULT> scanInOrder(
        items: List<ITEM>,
        scanChunk: suspend (List<ITEM>) -> RESULT,
        onChunkResult: suspend (RESULT) -> Unit,
        partitions: Int = (Runtime.getRuntime().availableProcessors() - 1)
            .coerceIn(1, MAX_PARALLEL_CHUNKS),
    ) {
        if (items.isEmpty()) return
        val chunkSize = (items.size + partitions - 1) / partitions
        coroutineScope {
            val jobs = items.chunked(chunkSize.coerceAtLeast(1)).map { chunk ->
                async(Dispatchers.Default) { scanChunk(chunk) }
            }
            for (job in jobs) {
                onChunkResult(job.await())
            }
        }
    }
}
