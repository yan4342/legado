package io.legado.app.domain.model.settings

data class MangaSettings(
    val showMangaUi: Boolean = true,
    val disableMangaScale: Boolean = true,
    val disableMangaDoubleTapZoom: Boolean = true,
    val disableMangaScrollAnimation: Boolean = false,
    val disableMangaCrossFade: Boolean = false,
    val scrollMode: Int = 4,
    val preDownloadNum: Int = 10,
    // Manga (MD3 port)：自动离线缓存（开启后才缓存后续章节）
    val chapterPrefetchCount: Int = 0,
    val autoOfflineCache: Boolean = false,
    val autoPageSpeed: Int = 3,
    val footerConfig: String = "",
    val disableClickScroll: Boolean = false,
    val longClick: Boolean = true,
    val background: Int = 0xFF000000.toInt(),
    val autoBackground: Boolean = false,
    val pageScaleType: Int = 0,
    val widePageMode: Int = 0,
    val doublePageMode: Int = 0,
    // Manga (MD3 port)：双页封面对齐（封面单页/顺序反转/配对错位）
    val doublePageCoverSingle: Boolean = true,
    val doublePageInvert: Boolean = false,
    val doublePageShift: Boolean = false,
    val colorFilter: String = "",
    val hideTitle: Boolean = false,
    val enableEInk: Boolean = false,
    val eInkThreshold: Int = 150,
    val enableGray: Boolean = false,
    val webtoonSidePaddingDp: Int = 0,
    val volumeKeyPage: Boolean = false,
    val reverseVolumeKeyPage: Boolean = false,
    val clickActionTL: Int = -1,
    val clickActionTC: Int = -1,
    val clickActionTR: Int = 1,
    val clickActionML: Int = 2,
    val clickActionMC: Int = 0,
    val clickActionMR: Int = 1,
    val clickActionBL: Int = 2,
    val clickActionBC: Int = 1,
    val clickActionBR: Int = 1,
) {
    fun hasMenuClickArea(): Boolean =
        clickActionTL * clickActionTC * clickActionTR *
            clickActionML * clickActionMC * clickActionMR *
            clickActionBL * clickActionBC * clickActionBR == 0
}
