package io.legado.app.ui.book.manga

internal fun mangaClickRegionIndex(
    x: Float,
    y: Float,
    width: Int,
    height: Int,
): Int {
    val column = (x / (width.coerceAtLeast(1) / 3f)).toInt().coerceIn(0, 2)
    val row = (y / (height.coerceAtLeast(1) / 3f)).toInt().coerceIn(0, 2)
    return row * 3 + column
}

/**
 * 找 [direction] 方向上的下一个「真实页」：跳过 ChapterTransition/ChapterEdge 等非页项。
 *
 * 直接以相邻下标做 PageStep 时，目标落在过渡页上既无法推进，scrollRequest 也因过渡页非
 * Page 而永远不清除（Pager 卡在章节边界）。保证步进永远落在实际页面。
 */
internal fun nextPageItemIndex(
    items: List<MangaReaderItemUi>,
    currentIndex: Int,
    direction: Int,
): Int? {
    var index = currentIndex + direction
    while (index in items.indices) {
        if (items[index] is MangaReaderItemUi.Page) return index
        index += direction
    }
    return null
}

/**
 * Chooses the page that represents a Webtoon viewport.
 *
 * Normally the last visible page is a useful reading-progress anchor. At a chapter boundary it
 * is not: a number of short pages from the adjacent chapter can be visible at once, so using the
 * last one promotes the session directly to that chapter's final visible page. Once the current
 * chapter has left the viewport, use the first visible page when entering a later chapter and
 * the last visible page when entering an earlier one. Those are the pages adjacent to the
 * boundary in reading order.
 */
internal fun mangaWebtoonFocusedPageIndex(
    items: List<MangaReaderItemUi>,
    visibleItemIndices: List<Int>,
    currentChapterIndex: Int,
): Int? {
    val visiblePages = visibleItemIndices.mapNotNull { index ->
        (items.getOrNull(index) as? MangaReaderItemUi.Page)?.let { index to it }
    }
    if (visiblePages.isEmpty()) return null
    if (visiblePages.any { (_, page) -> page.chapterIndex == currentChapterIndex }) {
        return visiblePages.last().first
    }
    return when {
        visiblePages.first().second.chapterIndex > currentChapterIndex -> visiblePages.first().first
        visiblePages.last().second.chapterIndex < currentChapterIndex -> visiblePages.last().first
        else -> visiblePages.last().first
    }
}

/** A programmatic position restore must not be overwritten by the old viewport's first callback. */
internal fun acceptsMangaVisibleItem(
    requestedItemIndex: Int?,
    reportedItemIndex: Int,
): Boolean = requestedItemIndex == null || requestedItemIndex == reportedItemIndex

internal fun mangaClickActionAt(
    clickActions: List<Int>,
    x: Float,
    y: Float,
    width: Int,
    height: Int,
): Int = clickActions.getOrNull(mangaClickRegionIndex(x, y, width, height)) ?: 0

internal fun shouldExposeMangaPages(currentChapterFinished: Boolean): Boolean =
    currentChapterFinished

enum class MangaChapterSwitch { NONE, NEXT, PREVIOUS }

/**
 * 决定可见页是否触发章节切换：只有当前章节已完全滑出视口时，
 * 才允许沿可见页的章节方向自动切章；否则只是停留在当前章记录进度。
 */
internal fun mangaChapterSwitchDecision(
    currentChapterIndex: Int,
    visibleChapterIndex: Int,
    currentChapterVisible: Boolean,
): MangaChapterSwitch = when {
    currentChapterIndex < visibleChapterIndex ->
        if (currentChapterVisible) MangaChapterSwitch.NONE else MangaChapterSwitch.NEXT

    currentChapterIndex > visibleChapterIndex ->
        if (currentChapterVisible) MangaChapterSwitch.NONE else MangaChapterSwitch.PREVIOUS

    else -> MangaChapterSwitch.NONE
}

/**
 * 是否强制重定位：无页面 / 全局加载中 / 换书 / 显式跳章目标 —— 否则按 key 锚定保持位置。
 */
internal fun shouldForceMangaChapterPosition(
    hasPages: Boolean,
    isLoading: Boolean,
    currentBookUrl: String,
    targetBookUrl: String,
    pendingExplicitChapterIndex: Int?,
    targetChapterIndex: Int,
): Boolean =
    !hasPages || isLoading || currentBookUrl != targetBookUrl ||
        pendingExplicitChapterIndex == targetChapterIndex
