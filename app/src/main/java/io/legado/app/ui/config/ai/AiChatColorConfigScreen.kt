package io.legado.app.ui.config.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AiChatColorConfig
import io.legado.app.lib.theme.AiChatColorManager
import io.legado.app.lib.theme.LocalAiChatSemanticColors
import io.legado.app.ui.ai.chat.ToolApprovalBadge
import io.legado.app.ui.ai.chat.ToolVisualFamily
import io.legado.app.ui.ai.chat.resolveToolApprovalBadges
import io.legado.app.ui.ai.chat.toolApprovalColors
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.ui.common.compose.SimpleColorPickerDialog
import io.legado.app.ui.common.compose.TransparentTopAppBarStatusBar
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem

private data class AiChatColorItem(
    val prefKey: String,
    val titleRes: Int,
    val descRes: Int,
    val fallback: @Composable () -> Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatColorConfigScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val semantic = LocalAiChatSemanticColors.current
    var refreshTick by remember { mutableIntStateOf(0) }

    TransparentTopAppBarStatusBar(barColor = colorScheme.surface)

    var pickerTitle by remember { mutableStateOf("") }
    var pickerColor by remember { mutableStateOf(Color.White) }
    var pickerOnConfirm by remember { mutableStateOf<(Color) -> Unit>({}) }
    var showPicker by remember { mutableStateOf(false) }

    fun reload() {
        AiChatColorManager.refresh(context.applicationContext)
        refreshTick++
    }

    val messageItems = remember(colorScheme) {
        listOf(
            AiChatColorItem(
                PreferKey.aiChatUserBubbleBg,
                R.string.ai_chat_color_user_bubble_bg,
                R.string.ai_chat_color_user_bubble_bg_desc,
            ) { colorScheme.primaryContainer },
            AiChatColorItem(
                PreferKey.aiChatUserBubbleText,
                R.string.ai_chat_color_user_bubble_text,
                R.string.ai_chat_color_user_bubble_text_desc,
            ) { colorScheme.onPrimaryContainer },
            AiChatColorItem(
                PreferKey.aiChatUserDialogue,
                R.string.ai_chat_color_user_dialogue,
                R.string.ai_chat_color_user_dialogue_desc,
            ) { colorScheme.tertiary },
        )
    }
    val toolItems = remember(colorScheme) {
        listOf(
            AiChatColorItem(
                PreferKey.aiChatToolSearch,
                R.string.ai_chat_color_tool_search,
                R.string.ai_chat_color_tool_search_desc,
            ) { colorScheme.primary },
            AiChatColorItem(
                PreferKey.aiChatToolRead,
                R.string.ai_chat_color_tool_read,
                R.string.ai_chat_color_tool_read_desc,
            ) { colorScheme.secondary },
            AiChatColorItem(
                PreferKey.aiChatToolExtract,
                R.string.ai_chat_color_tool_extract,
                R.string.ai_chat_color_tool_extract_desc,
            ) { colorScheme.tertiary },
            AiChatColorItem(
                PreferKey.aiChatToolMemory,
                R.string.ai_chat_color_tool_memory,
                R.string.ai_chat_color_tool_memory_desc,
            ) { Color(0xFF7E57C2) },
            AiChatColorItem(
                PreferKey.aiChatToolMutation,
                R.string.ai_chat_color_tool_mutation,
                R.string.ai_chat_color_tool_mutation_desc,
            ) { colorScheme.primary },
            AiChatColorItem(
                PreferKey.aiChatToolDestructive,
                R.string.ai_chat_color_tool_destructive,
                R.string.ai_chat_color_tool_destructive_desc,
            ) { colorScheme.error },
        )
    }

    if (showPicker) {
        SimpleColorPickerDialog(
            title = pickerTitle,
            currentColor = pickerColor,
            onDismiss = { showPicker = false },
            onConfirm = { color ->
                pickerOnConfirm(color)
                reload()
                showPicker = false
            },
        )
    }

    Scaffold(
        containerColor = colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_chat_color_config)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            AiChatColorConfig.resetAll(context)
                            reload()
                        },
                    ) {
                        Text(stringResource(R.string.ai_chat_color_reset_all))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        @Suppress("UNUSED_VARIABLE")
        val tick = refreshTick
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AiChatColorPreviewSection(
                    userBubbleBg = semantic.userBubbleBg ?: colorScheme.primaryContainer,
                    userBubbleText = semantic.userBubbleText ?: colorScheme.onPrimaryContainer,
                    speakerColors = semantic.speakerColors,
                )
            }
            item {
                ColorSection(
                    title = stringResource(R.string.ai_chat_color_section_messages),
                    items = messageItems,
                    context = context,
                    onPick = { title, color, onConfirm ->
                        pickerTitle = title
                        pickerColor = color
                        pickerOnConfirm = onConfirm
                        showPicker = true
                    },
                    onReset = { key ->
                        AiChatColorConfig.resetKey(context, key)
                        reload()
                    },
                    onReload = ::reload,
                )
            }
            item {
                ColorSection(
                    title = stringResource(R.string.ai_chat_color_section_tools),
                    items = toolItems,
                    context = context,
                    onPick = { title, color, onConfirm ->
                        pickerTitle = title
                        pickerColor = color
                        pickerOnConfirm = onConfirm
                        showPicker = true
                    },
                    onReset = { key ->
                        AiChatColorConfig.resetKey(context, key)
                        reload()
                    },
                    onReload = ::reload,
                )
            }
            item {
                SpeakerColorSection(
                    context = context,
                    onPick = { title, color, onConfirm ->
                        pickerTitle = title
                        pickerColor = color
                        pickerOnConfirm = onConfirm
                        showPicker = true
                    },
                    onReset = { key ->
                        AiChatColorConfig.resetKey(context, key)
                        reload()
                    },
                    onReload = ::reload,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AiChatColorPreviewSection(
    userBubbleBg: Color,
    userBubbleText: Color,
    speakerColors: List<Color>,
) {
    val colorScheme = MaterialTheme.colorScheme
    val searchBadge = remember { resolveToolApprovalBadges(io.legado.app.data.repository.AiToolRepository.TOOL_SEARCH_BOOKS) }
    val readBadge = remember { resolveToolApprovalBadges(io.legado.app.data.repository.AiToolRepository.TOOL_GET_CHAPTER_CONTENT) }
    val memoryBadge = remember { resolveToolApprovalBadges(io.legado.app.data.repository.AiToolRepository.TOOL_PATCH_HISTORY_MEMORY) }
    val readColors = toolApprovalColors(ToolVisualFamily.Read)
    val memoryColors = toolApprovalColors(ToolVisualFamily.Memory)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.ai_chat_color_preview),
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = userBubbleBg,
                    modifier = Modifier.fillMaxWidth(0.72f),
                ) {
                    Text(
                        "Hello",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = userBubbleText,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    searchBadge.forEach { ToolApprovalBadge(it) }
                    readBadge.forEach { ToolApprovalBadge(it) }
                    memoryBadge.forEach { ToolApprovalBadge(it) }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(readColors.panelContainer)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 3.dp, height = 28.dp)
                            .background(readColors.accentStripe),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.ai_tool_badge_read),
                        style = MaterialTheme.typography.labelMedium,
                        color = readColors.accent,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(memoryColors.panelContainer)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 3.dp, height = 28.dp)
                            .background(memoryColors.accentStripe),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.ai_tool_badge_memory),
                        style = MaterialTheme.typography.labelMedium,
                        color = memoryColors.accent,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    speakerColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSection(
    title: String,
    items: List<AiChatColorItem>,
    context: android.content.Context,
    onPick: (String, Color, (Color) -> Unit) -> Unit,
    onReset: (String) -> Unit,
    onReload: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
        SectionCard {
            items.forEach { item ->
                val stored = AiChatColorConfig.readColor(context, item.prefKey)
                val display = stored ?: item.fallback()
                val titleText = stringResource(item.titleRes)
                val descText = if (stored == null) {
                    stringResource(item.descRes) + " · " + stringResource(R.string.ai_chat_color_unset)
                } else {
                    stringResource(item.descRes)
                }
                ColorPreferenceRow(
                    title = titleText,
                    description = descText,
                    color = display,
                    hasOverride = stored != null,
                    onClick = {
                        onPick(titleText, display) { newColor ->
                            AiChatColorConfig.writeColor(context, item.prefKey, newColor)
                            onReload()
                        }
                    },
                    onReset = { onReset(item.prefKey) },
                )
            }
        }
    }
}

@Composable
private fun SpeakerColorSection(
    context: android.content.Context,
    onPick: (String, Color, (Color) -> Unit) -> Unit,
    onReset: (String) -> Unit,
    onReload: () -> Unit,
) {
    val defaults = remember { AiChatColorConfig.defaultSpeakerColors() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.ai_chat_color_section_speakers),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
        SectionCard {
            AiChatColorConfig.speakerColorKeys.forEachIndexed { index, key ->
                val stored = AiChatColorConfig.readColor(context, key)
                val display = stored ?: defaults[index]
                val titleText = stringResource(R.string.ai_chat_color_speaker_n, index + 1)
                val descText = if (stored == null) {
                    stringResource(R.string.ai_chat_color_speaker_desc, index + 1) +
                        " · " + stringResource(R.string.ai_chat_color_unset)
                } else {
                    stringResource(R.string.ai_chat_color_speaker_desc, index + 1)
                }
                ColorPreferenceRow(
                    title = titleText,
                    description = descText,
                    color = display,
                    hasOverride = stored != null,
                    onClick = {
                        onPick(titleText, display) { newColor ->
                            AiChatColorConfig.writeColor(context, key, newColor)
                            onReload()
                        }
                    },
                    onReset = { onReset(key) },
                )
            }
        }
    }
}

@Composable
private fun ColorPreferenceRow(
    title: String,
    description: String,
    color: Color,
    hasOverride: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
) {
    ClickableSettingItem(
        title = title,
        description = description,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasOverride) {
                    TextButton(onClick = onReset) {
                        Text(
                            stringResource(R.string.ai_chat_color_reset),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        },
        onClick = onClick,
    )
}
