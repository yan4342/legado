package io.legado.app.help.ai

import android.content.Context
import java.io.File

/**
 * 计划文件存储：每个计划一份 plan_<planId>.md，一个会话可有多个计划文件。
 * 文件是 AI 工具（read_file / edit_file，path 为 plan://…）的读写面，内容与 ai_plans 表保持同步。
 */
object PlanFileStore {

    private const val PLANS_ROOT = "ai_plans"
    private const val EXT = ".md"

    private fun dirFor(context: Context, conversationId: String): File =
        File(File(context.filesDir, PLANS_ROOT), conversationId)

    fun fileFor(context: Context, conversationId: String, planId: String): File =
        File(dirFor(context, conversationId), "$planId$EXT")

    /** 写计划 markdown 到该计划自己的文件（创建目录）。 */
    fun write(context: Context, conversationId: String, planId: String, content: String) {
        val dir = dirFor(context, conversationId)
        dir.mkdirs()
        fileFor(context, conversationId, planId).writeText(content, Charsets.UTF_8)
    }

    /** 读该计划文件；文件不存在返回 null。 */
    fun read(context: Context, conversationId: String, planId: String): String? {
        val file = fileFor(context, conversationId, planId)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }
}
