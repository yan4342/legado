package io.legado.app.ui.common.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Lift sheet content above the gesture nav bar and software keyboard (no double-counting). */
@Composable
fun Modifier.legadoSheetInsets(): Modifier =
    windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
