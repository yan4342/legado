package io.legado.app.ui.welcome

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseActivity
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityWelcomeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.fullScreen
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import io.legado.app.utils.windowSize
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    override val binding by viewBinding(ActivityWelcomeBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.ivBook.setColorFilter(accentColor)
        binding.vwTitleLine.setBackgroundColor(accentColor)
        // 避免从桌面启动程序后，会重新实例化入口类的activity
        if (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) {
            // Deep-link / MainIntent while app is alive: forward extras to MainActivity.
            if (intent.hasExtra(io.legado.app.ui.main.MainIntent.EXTRA_START_ROUTE)) {
                startMainActivity(forwardExtras = true)
            }
            finish()
        } else {
            startMainActivity(forwardExtras = true)
        }
    }

    override fun setupSystemBar() {
        fullScreen()
        setStatusBarColorAuto(backgroundColor, true, fullScreen)
        upNavigationBarColor()
    }

    override fun upBackgroundImage() {
        if (AppConfig.customWelcome) {
            kotlin.runCatching {
                when (ThemeConfig.getTheme()) {
                    Theme.Dark -> AppConfig.welcomeImageDark?.let { path ->
                        val size = windowManager.windowSize
                        lifecycleScope.launch {
                            val bitmap = withContext(IO) {
                                BitmapUtils.decodeBitmap(path, size.widthPixels, size.heightPixels)
                            }
                            bitmap?.let {
                                binding.tvLegado.visible(AppConfig.welcomeShowTextDark)
                                binding.ivBook.visible(AppConfig.welcomeShowIconDark)
                                binding.tvGzh.visible(AppConfig.welcomeShowTextDark)
                                window.decorView.background = BitmapDrawable(resources, it)
                            }
                        }
                        return
                    }

                    else -> AppConfig.welcomeImage?.let { path ->
                        val size = windowManager.windowSize
                        lifecycleScope.launch {
                            val bitmap = withContext(IO) {
                                BitmapUtils.decodeBitmap(path, size.widthPixels, size.heightPixels)
                            }
                            bitmap?.let {
                                binding.tvLegado.visible(AppConfig.welcomeShowText)
                                binding.ivBook.visible(AppConfig.welcomeShowIcon)
                                binding.tvGzh.visible(AppConfig.welcomeShowText)
                                window.decorView.background = BitmapDrawable(resources, it)
                            }
                        }
                        return
                    }
                }
            }
        }
        super.upBackgroundImage()
    }

    private fun startMainActivity(forwardExtras: Boolean = false) {
        val source = intent
        startActivity<MainActivity> {
            if (forwardExtras) {
                source.extras?.let { putExtras(it) }
                source.data?.let { data = it }
                source.action?.let { action = it }
            }
        }
        if (AppConfig.defaultToRead) {
            lifecycleScope.launch {
                val lastBook = withContext(IO) {
                    appDb.bookDao.lastReadBook
                }
                if (lastBook != null) {
                    startActivity<ReadBookActivity>()
                }
                finish()
            }
        } else {
            finish()
        }
    }

}

class Launcher1 : WelcomeActivity()
class Launcher2 : WelcomeActivity()
class Launcher3 : WelcomeActivity()
class Launcher4 : WelcomeActivity()
class Launcher5 : WelcomeActivity()
class Launcher6 : WelcomeActivity()
