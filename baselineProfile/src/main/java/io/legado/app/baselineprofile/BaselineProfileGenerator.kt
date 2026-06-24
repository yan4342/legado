package io.legado.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: "io.legado.app.release",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            val device = androidx.test.uiautomator.UiDevice.getInstance(
                InstrumentationRegistry.getInstrumentation()
            )
            val w = device.displayWidth
            val h = device.displayHeight

            // WelcomeActivity 立即跳转 MainActivity，多等一下确保 Main 渲染完
            Thread.sleep(2000)

            val swStartX = w / 2
            val swStartY = (h * 0.75).toInt()
            val swEndY = (h * 0.25).toInt()

            // ====== 1. 判断首页状态：书架 or 阅读页 ======
            if (device.wait(Until.hasObject(By.desc("bookshelf_list")), 3_000)) {
                // 主界面 — 书架
                device.waitForIdle()
                Thread.sleep(500)

                // 书架滚动
                repeat(2) {
                    device.swipe(swStartX, swStartY, swStartX, swEndY, 25)
                    Thread.sleep(600)
                }
                repeat(2) {
                    device.swipe(swStartX, swEndY, swStartX, swStartY, 10)
                    Thread.sleep(600)
                }

                // 切换底部 Tab
                trySwitchTabs(device, w, h)

            } else if (device.wait(Until.hasObject(By.pkg(device.currentPackageName).clazz("android.webkit.WebView")), 1_000)
                || device.currentPackageName.contains("legado")
            ) {
                // 可能在阅读页（开了"默认阅读"直接进阅读）
                readAndGoBack(device, w, h, swStartX, swStartY, swEndY)
                // 返回后可能在书架页
                if (device.wait(Until.hasObject(By.desc("bookshelf_list")), 3_000)) {
                    device.waitForIdle()
                    Thread.sleep(500)
                    repeat(2) {
                        device.swipe(swStartX, swStartY, swStartX, swEndY, 25)
                        Thread.sleep(600)
                    }
                    trySwitchTabs(device, w, h)
                }
            } else {
                // 其他情况 — 可能在主界面但书架还没渲染
                Thread.sleep(1000)
                if (device.wait(Until.hasObject(By.desc("bookshelf_list")), 5_000)) {
                    device.waitForIdle()
                    Thread.sleep(500)
                    repeat(2) {
                        device.swipe(swStartX, swStartY, swStartX, swEndY, 25)
                        Thread.sleep(600)
                    }
                    trySwitchTabs(device, w, h)
                }
            }

            // ====== 2. 进入书籍详情和阅读 ======
            if (device.wait(Until.hasObject(By.desc("bookshelf_list")), 2_000)) {
                // 回到书架首页（可能在阅读页按了返回）
                device.swipe(swStartX, swEndY, swStartX, swStartY, 10)
                Thread.sleep(300)

                // 点第一本书
                try {
                    device.click(w / 2, (h * 0.28).toInt())
                    Thread.sleep(1500)

                    // 等详情页"开始阅读"按钮
                    val readBtn = By.textContains("Read")
                    if (device.wait(Until.hasObject(readBtn), 5_000)) {
                        device.waitForIdle()
                        Thread.sleep(300)

                        // 滚动详情页
                        device.swipe(swStartX, swStartY, swStartX, swEndY, 15)
                        Thread.sleep(300)
                        device.swipe(swStartX, swStartY, swStartX, swEndY, 15)
                        Thread.sleep(300)

                        // 点阅读按钮 → 进入阅读页
                        try {
                            device.findObject(readBtn).click()
                            Thread.sleep(2000)
                            readAndGoBack(device, w, h, swStartX, swStartY, swEndY)
                        } catch (_: Exception) { }

                        // 返回书架
                        device.pressBack()
                        Thread.sleep(600)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    // ---- 阅读页操作 ----
    private fun readAndGoBack(
        device: androidx.test.uiautomator.UiDevice,
        w: Int, h: Int,
        swX: Int, swSY: Int, swEY: Int,
    ) {
        Thread.sleep(1000)
        device.swipe(swX, swSY, swX, swEY, 20)
        Thread.sleep(300)
        device.swipe(swX, swSY, swX, swEY, 20)
        Thread.sleep(300)
        device.swipe(swX, swSY, swX, swEY, 20)
        Thread.sleep(300)
        device.swipe(swX, swEY, swX, swSY, 10)
        Thread.sleep(300)
        device.swipe(swX, swEY, swX, swSY, 10)
        Thread.sleep(300)
        // 点击中央唤出菜单
        device.click(w / 2, h / 2)
        Thread.sleep(500)
        device.click(w / 2, h / 2)
        Thread.sleep(500)
        device.pressBack()
        Thread.sleep(800)
    }

    // ---- 切换底部 Tab ----
    private fun trySwitchTabs(
        device: androidx.test.uiautomator.UiDevice,
        w: Int, h: Int,
    ) {
        val tabs = listOf("Me", "Discovery", "RSS feeds", "Bookshelf")
        for (desc in tabs) {
            try {
                val tab = device.wait(Until.findObject(By.desc(desc)), 2_000)
                tab?.let {
                    val b = it.visibleBounds
                    device.click(b.centerX(), b.centerY())
                    Thread.sleep(600)
                }
            } catch (_: Exception) { }
        }
    }
}
