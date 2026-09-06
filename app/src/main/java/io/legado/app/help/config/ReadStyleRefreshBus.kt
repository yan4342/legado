package io.legado.app.help.config

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 阅读样式刷新桥（Phase 3：UP_CONFIG → Flow）。
 *
 * 设置面板收编后，"改设置 → 阅读界面局部刷新"从一次性事件（EventBus.UP_CONFIG，
 * 分组 int 列表）升级为可收集的响应式流。旧事件总线**原样保留**——ReadBookActivity 的
 * when 分发、ReadAloudPlayerCoordinator 等既有消费方不受影响；新代码（尤其 Compose
 * 面板）请收集 [refreshFlow]，后续按域逐个切换消费方，全部迁完后再删事件总线。
 * 当前状态：仅收集端就绪（ReadBookActivity），发射端接线在阅读页路由化 b 阶段——此前请勿误判为死代码删除。
 *
 * 分组语义与 UP_CONFIG 完全一致（勿改，迁移期两套并存必须同义）：
 * 0 系统栏可见性 | 1 背景 | 2 样式 | 3 背景透明度 | 4 触摸区 | 5 正文重排 |
 * 6 内容刷新 | 8 章节样式 | 9 重绘 | 10 章节布局 | 11 提交渲染 | 12 翻页动画
 */
object ReadStyleRefreshBus {

    private val _refresh = MutableSharedFlow<List<Int>>(extraBufferCapacity = 16)

    val refreshFlow: SharedFlow<List<Int>> = _refresh

    /** 设置变更后请求阅读界面刷新，groups 同 UP_CONFIG 分组语义 */
    fun refresh(vararg groups: Int) {
        _refresh.tryEmit(groups.toList())
    }
}
