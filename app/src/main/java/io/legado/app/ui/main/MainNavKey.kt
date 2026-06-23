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

// “我的”页 NavDisplay 覆盖层路由（返回时由 predictivePopTransitionSpec 驱动 Compose pop 动画）
@Serializable
data object MainRouteMy : MainRoute

@Serializable
data object MainRouteReadRecord : MainRoute

@Serializable
data object MainRouteReadRecordOverview : MainRoute

@Serializable
data object MainRouteAiDictRule : MainRoute

// 第二批：带框架回调的 B 类 Compose 子页
@Serializable
data object MainRouteAbout : MainRoute

@Serializable
data object MainRouteOtherConfig : MainRoute

@Serializable
data object MainRouteThemeConfig : MainRoute

@Serializable
data object MainRouteWelcomeConfig : MainRoute

@Serializable
data object MainRouteCoverConfig : MainRoute

@Serializable
data object MainRouteBackupConfig : MainRoute
