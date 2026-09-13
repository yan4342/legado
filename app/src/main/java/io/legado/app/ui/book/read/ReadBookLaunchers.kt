package io.legado.app.ui.book.read

import android.content.Intent
import io.legado.app.ui.file.HandleFileContract

/** HandleFileContract 的配置 lambda（可空形式经 (HandleFileConfig?) -> Unit 使用） */
typealias ReadFileConfig = HandleFileContract.HandleFileParam.() -> Unit

/**
 * 阶段 4 阅读页路由化：ActivityResult 桥。
 * ReadBookRouteScreen 用 rememberLauncherForActivityResult 创建后填入各回调，
 * ReadBookController 经此触发（替代原 Activity 的 registerForActivityResult 字段）。
 */
class ReadBookLaunchers {

    /** TocActivityResult：入参 bookUrl */
    var toc: ((String) -> Unit)? = null

    /** BookSourceEditActivity：入参 intent 配置 lambda，结果 OK 时回调 sourceEditResult */
    var sourceEdit: ((Intent.() -> Unit) -> Unit)? = null
    var sourceEditResult: (() -> Unit)? = null

    /** ReplaceRuleActivity：入参 Intent，结果 OK 时回调 replaceResult */
    var replace: ((Intent) -> Unit)? = null
    var replaceResult: (() -> Unit)? = null

    /** SearchContentActivity：入参为 intent 配置 lambda，结果回调 (key, index) */
    var searchContent: ((Intent.() -> Unit) -> Unit)? = null
    var searchContentResult: ((key: Long, index: Int) -> Unit)? = null

    /** BookInfoComposeActivity（阅读器内打开详情）：退出时按 bookDeleted/addedToShelf 分发 */
    var bookInfoDone: (() -> Unit)? = null
    var bookInfoDeleted: (() -> Unit)? = null

    /** HandleFileContract：null 表示仅选目录 */
    var selectImageDir: ((ReadFileConfig?) -> Unit)? = null

    /** HandleFileContract DIR_SYS：权限拒绝重选书籍文件夹 */
    var selectBookFolder: ((ReadFileConfig) -> Unit)? = null
}
