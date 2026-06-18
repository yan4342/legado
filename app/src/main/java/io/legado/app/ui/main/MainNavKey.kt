package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 路由定义（覆盖层，用于全屏二级页面如书籍详情）。
 * 底层 ViewPager Tab 架构不变。
 */
@Serializable
sealed interface MainRoute : NavKey

@Serializable
data object MainRouteBookshelf : MainRoute

@Serializable
data class MainRouteBookInfo(
    val name: String?,
    val author: String?,
    val bookUrl: String,
    val origin: String? = null,
    val coverPath: String? = null,
    val sharedCoverKey: String? = null,
) : MainRoute
