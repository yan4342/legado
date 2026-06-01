package io.legado.app.ui.common.compose

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全局动画开关。
 * E-Ink 模式下自动禁用所有 Compose 动画。
 * 可通过 CompositionLocal 在任何组件中读取：
 * ```
 * val animationsEnabled = LocalAnimationsEnabled.current
 * Modifier.then(if (animationsEnabled) Modifier.animateContentSize() else Modifier)
 * ```
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }
