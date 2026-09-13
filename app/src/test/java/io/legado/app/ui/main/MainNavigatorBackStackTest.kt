package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MainNavigator 入栈策略表守护测试。
 * 入栈规则（navigateToRoute）是纯函数行为，改动白名单/清栈逻辑必须同步更新本测试。
 */
class MainNavigatorBackStackTest {

    private fun bookInfo() = MainRouteBookInfo("Book", "Author", "book-url")

    @Test
    fun `cache page opening book info keeps cache in back stack`() {
        // 返回栈白名单：缓存页(MD3 6dc297221)→书籍详情，返回时应回到缓存页
        val cache = MainRouteCache(-1L)
        val backStack = mutableListOf<NavKey>(MainRouteHome, cache)

        MainNavigator.navigateToRoute(backStack, bookInfo())

        assertEquals(listOf<NavKey>(MainRouteHome, cache, bookInfo()), backStack)
    }

    @Test
    fun `home route resets back stack to home only`() {
        val backStack = mutableListOf<NavKey>(
            MainRouteHome, MainRouteSearch(null), bookInfo()
        )

        MainNavigator.navigateToRoute(backStack, MainRouteHome)

        assertEquals(listOf<NavKey>(MainRouteHome), backStack)
    }

    @Test
    fun `search from home pushes search`() {
        val backStack = mutableListOf<NavKey>(MainRouteHome)
        val search = MainRouteSearch(key = null, scopeRaw = "scope")

        MainNavigator.navigateToRoute(backStack, search)

        assertEquals(listOf<NavKey>(MainRouteHome, search), backStack)
    }

    @Test
    fun `search from book info keeps book info in back stack`() {
        // 本项目白名单包含 BookInfo（详情页→搜索，返回回详情），与 MD3 不同，系有意设计
        val info = bookInfo()
        val backStack = mutableListOf<NavKey>(MainRouteHome, info)
        val search = MainRouteSearch(null)

        MainNavigator.navigateToRoute(backStack, search)

        assertEquals(listOf<NavKey>(MainRouteHome, info, search), backStack)
    }

    @Test
    fun `book info from search keeps search in back stack`() {
        val search = MainRouteSearch(null)
        val backStack = mutableListOf<NavKey>(MainRouteHome, search)

        MainNavigator.navigateToRoute(backStack, bookInfo())

        assertEquals(listOf<NavKey>(MainRouteHome, search, bookInfo()), backStack)
    }

    @Test
    fun `same route on top is a no-op`() {
        val info = bookInfo()
        val backStack = mutableListOf<NavKey>(MainRouteHome, info)

        MainNavigator.navigateToRoute(backStack, info)

        assertEquals(listOf<NavKey>(MainRouteHome, info), backStack)
    }

    @Test
    fun `settings sub pages rebuild drill-down path`() {
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteSearch(null))

        MainNavigator.navigateToRoute(backStack, MainRouteSettings)
        MainNavigator.navigateToRoute(backStack, MainRouteSettingsBackup)

        assertEquals(
            listOf<NavKey>(MainRouteHome, MainRouteSettings, MainRouteSettingsBackup),
            backStack,
        )
    }

    @Test
    fun `replace rule from home pushes list`() {
        val backStack = mutableListOf<NavKey>(MainRouteHome)

        MainNavigator.navigateToRoute(backStack, MainRouteReplaceRule)

        assertEquals(listOf<NavKey>(MainRouteHome, MainRouteReplaceRule), backStack)
    }

    @Test
    fun `replace edit pushes over replace rule list`() {
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteReplaceRule)
        val edit = MainRouteReplaceEdit(id = 3L)

        MainNavigator.navigateToRoute(backStack, edit)

        assertEquals(listOf<NavKey>(MainRouteHome, MainRouteReplaceRule, edit), backStack)
    }

    @Test
    fun `book source manage from home pushes page`() {
        val backStack = mutableListOf<NavKey>(MainRouteHome)

        MainNavigator.navigateToRoute(backStack, MainRouteBookSourceManage)

        assertEquals(listOf<NavKey>(MainRouteHome, MainRouteBookSourceManage), backStack)
    }

    @Test
    fun `search from book source manage keeps manage in back stack`() {
        val manage = MainRouteBookSourceManage
        val backStack = mutableListOf<NavKey>(MainRouteHome, manage)
        val search = MainRouteSearch(null)

        MainNavigator.navigateToRoute(backStack, search)

        assertEquals(listOf<NavKey>(MainRouteHome, manage, search), backStack)
    }

    @Test
    fun `toc pushes over book info`() {
        val info = bookInfo()
        val backStack = mutableListOf<NavKey>(MainRouteHome, info)
        val toc = MainRouteToc("book-url")

        MainNavigator.navigateToRoute(backStack, toc)

        assertEquals(listOf<NavKey>(MainRouteHome, info, toc), backStack)
    }

    @Test
    fun `read book pushes over home`() {
        val backStack = mutableListOf<NavKey>(MainRouteHome)

        MainNavigator.navigateToRoute(backStack, MainRouteReadBook("book-url"))

        assertEquals(listOf<NavKey>(MainRouteHome, MainRouteReadBook("book-url")), backStack)
    }

    @Test
    fun `read book pushes over book info keeping source`() {
        val info = bookInfo()
        val backStack = mutableListOf<NavKey>(MainRouteHome, info)

        MainNavigator.navigateToRoute(backStack, MainRouteReadBook("book-url"))

        assertEquals(
            listOf<NavKey>(MainRouteHome, info, MainRouteReadBook("book-url")),
            backStack,
        )
    }

    @Test
    fun `read book replaces existing reader in stack`() {
        // 阅读器全局唯一：reader→详情→再开另一本书，旧 reader 必须移除
        val backStack = mutableListOf<NavKey>(
            MainRouteHome,
            MainRouteReadBook("book-a"),
            bookInfo(),
        )

        MainNavigator.navigateToRoute(backStack, MainRouteReadBook("book-b"))

        assertEquals(
            listOf<NavKey>(MainRouteHome, bookInfo(), MainRouteReadBook("book-b")),
            backStack,
        )
    }

    @Test
    fun `read book from unrelated route resets to home plus reader`() {
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)

        MainNavigator.navigateToRoute(backStack, MainRouteReadBook("book-url"))

        assertEquals(
            listOf<NavKey>(MainRouteHome, MainRouteReadBook("book-url")),
            backStack,
        )
    }
}
