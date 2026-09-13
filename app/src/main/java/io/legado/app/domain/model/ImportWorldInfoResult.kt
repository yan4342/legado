package io.legado.app.domain.model

data class ImportWorldInfoResult(
    val worldBookId: String,
    val worldBookName: String,
    val entryCount: Int,
    val skippedEmpty: Int = 0,
    val ignoredAdvanced: Boolean = false,
    /** Set when a PNG/JSON character card was imported together with the lorebook. */
    val characterCardId: String? = null,
    val characterCardName: String? = null,
)
