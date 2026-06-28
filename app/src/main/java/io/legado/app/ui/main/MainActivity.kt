@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout

import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.EventBus
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.alert
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.showCrashLogSheet
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showMarkdownSheet
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 主界面 — Compose NavDisplay 架构
 */
class MainActivity : BaseComposeActivity(), BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener {

    private var exitTime: Long = 0
    private val EXIT_INTERVAL = 2000L
    private var onNavigateToRoute: ((MainRoute) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            upVersion()
            notifyAppCrash()
        }

        observeEvent<Boolean>(EventBus.DISABLE_VIEW_PAGER) {
            // Handled by overlay — no ViewPager to disable in Compose mode
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @androidx.compose.runtime.Composable
    override fun Content() {
        SharedTransitionLayout {
            MainNavHost(
                onNavigateToRouteSetter = { onNavigateToRoute = it },
                onBackAtHome = {
                    if (System.currentTimeMillis() - exitTime > EXIT_INTERVAL) {
                        toastOnUi(R.string.double_click_exit)
                        exitTime = System.currentTimeMillis()
                    } else {
                        finish()
                    }
                },
                sharedTransitionScope = this@SharedTransitionLayout,
            )
        }
    }

    fun navigateToSearch(key: String? = null, scopeRaw: String? = null) {
        onNavigateToRoute?.invoke(MainRouteSearch(key, scopeRaw))
    }

    // --------------- 保留的遗留方法 ---------------

    override fun onNavigationItemSelected(item: MenuItem): Boolean = false

    override fun onNavigationItemReselected(item: MenuItem) {}

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return super.dispatchKeyEvent(event)
    }

    private suspend fun privacyPolicy(): Boolean {
        if (LocalConfig.privacyPolicyOk) return true
        val privacyPolicy = withContext(IO) {
            String(assets.open("privacyPolicy.md").readBytes())
        }
        return suspendCancellableCoroutine { block ->
            alert(getString(R.string.privacy_policy), privacyPolicy) {
                okButton { block.resume(true) }
                noButton {
                    LocalConfig.privacyPolicyOk = false
                    finish()
                }
            }.show()
        }
    }

    private suspend fun upVersion() {
        if (BuildConfig.DEBUG) return
        privacyPolicy()
        if (LocalConfig.versionCode == appInfo.versionCode) return
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = withContext(IO) {
                String(assets.open("web/help/md/appHelp.md").readBytes())
            }
            showMarkdownSheet(getString(R.string.help), help)
        } else {
            val log = withContext(IO) {
                String(assets.open("updateLog.md").readBytes())
            }
            showMarkdownSheet(getString(R.string.update_log), log)
        }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) return
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton { showCrashLogSheet() }
            noButton()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch(IO) {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }
}
