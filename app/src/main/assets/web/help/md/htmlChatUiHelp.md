# HTML AI chat — UI contract

Load: `search_rule_help` → `scope=skill:html-chat-theme` → `doc=htmlChatUiHelp`.

Pack paths / slots / media → `doc=htmlChatThemeHelp`.

This doc is the **platform contract**. Visual layout and chrome are open; these wires are not.

## Architecture

```text
AiChatHtmlScreen
  ├── WebView  index.html + app.js  (user pack if present, else assets)
  │     ├── LegadoBridge.*         JS → Native
  │     └── window.LegadoAiChat.*  Native → JS
  └── Compose overlays             Sheets / dialogs via openSheet
```

Theme owns presentation. Product surfaces that already exist as Compose sheets stay there.

## Capability contract (hard)

User-pack `app.js` must reference (string presence):

- `LegadoBridge`
- `postIntent`
- `LegadoAiChat` (or `window.LegadoAiChat`)
- `applyState`

Stock DOM ids are a **binding map for the shared `app.js`**, not a requirement for free shells. If you replace `app.js`, you choose elements and wire them.

## Native → JS (`window.LegadoAiChat`)

| Method | Payload | Role |
|--------|---------|------|
| `applyState(state)` | Slim UI JSON | Conversations, messages, toggles, panels |
| `applyTheme(theme)` | `{ themeId, themes, cssUrl, fragments }` | Hot CSS + inject every `fragments` key into `[data-slot]` |
| `applyEffect(effect)` | e.g. `{ type:"SetInputText", text, forConversationId }` | One-shots |

After theme/fragment remount, call your own refresh (stock shell: `renderAll()`). **Ignore unknown `applyState` fields.**

## JS → Native (`LegadoBridge`)

### Helpers (not AiChatIntent)

| Method | Notes |
|--------|--------|
| `ready()` | Page ready → Native starts pushing |
| `goBack()` / `openSettings()` | Leave chat / AI settings |
| `listThemes()` / `getThemeId()` / `setThemeId(id)` | Theme list / switch |
| `getFragments()` | Active slot→html map |
| `openSheet(json)` | `{"sheet":"…","id":"…?"}` → Compose |
| `copyText(text)` | Clipboard |
| `pickAttachments()` | System picker |
| `matchSlashCommands(partial)` | Slash suggestion JSON |
| `normalizeWritingInput(text, mode)` | `DIALOGUE` \| `ACTION` |
| `switchWritingInputMode(json)` | Mode switch + text rewrite |
| `postIntent(json)` | Whitelisted intents only |

### `openSheet` ids

| `sheet` | Opens |
|---------|--------|
| `outline` | Outline |
| `workspace` | Workspace |
| `characters` / `character` | Character multi-select |
| `character_list` | Character list |
| `prompts` / `prompt` | Writing prompts |
| `skills` / `skill` | Conversation skills |
| `userCard` / `user_card` / `user` | User card |
| `worldBook` / `world_book` | World book |
| `memory` / `memory_table` | Memory tables |
| `history` / `execution` | Execution history |
| `compress` / `compress_confirm` | Compress **confirm** (required step) |
| `context` / `context_usage` | Context usage |
| `delete_conversation` | Delete confirm; pass `"id"` |

Example: `LegadoBridge.openSheet(JSON.stringify({ sheet: "compress" }))`.

## `postIntent` whitelist

Unknown `type` → ignored. Do not invent types.

### Session / messages

| type | Fields |
|------|--------|
| `NewConversation` | — |
| `SelectConversation` | `id` |
| `RenameConversation` | `id`, `title` |
| `DeleteConversation` | `id` (prefer `openSheet` confirm) |
| `SendMessage` | `content` |
| `StopGenerating` | — |
| `ContinueWriting` | — |
| `LoadMoreMessages` | — |
| `UpdateDraftInput` | `text` |
| `RegenerateMessage` | `messageId` |
| `RegenerateFromUserMessage` | `userMessageId` or `messageId` |
| `SwitchBranch` | `messageId`, `direction` (±1) |
| `EditMessage` | `messageId`, `newContent` |
| `DeleteMessage` | `messageId` |
| `ForkConversation` | `messageId` |
| `RestoreSnapshot` | `snapshotId` |

### Mode / toggles

| type | Fields |
|------|--------|
| `SwitchMode` | `mode`: `chat` \| `writing` |
| `SetWritingSubMode` | `subMode`: `roleplay` \| `author` |
| `UpdateReasoningLevel` | `level`: `OFF`\|`AUTO`\|`LOW`\|`MEDIUM`\|`HIGH`\|`MAX` |
| `SetOutputMode` | `mode`: `AUTO` \| `ASK` \| `PLAN`（自动/询问/计划模式） |
| `AcceptPlan` | —（计划模式批准） |
| `RejectPlan` | —（计划模式拒绝） |
| `EditPlan` | `content`（计划编辑） |
| `CancelPlanEdit` | —（取消计划编辑） |
| `ToggleWebSearch` | — |
| `ToggleGalgame` | — |
| `ToggleInterCharacterChat` | — |
| `ToggleDialogueHighlight` | — |
| `ToggleRoleplayDialogueBubble` | — |
| `TogglePostEdit` | — |
| `ToggleOutlineEnabled` | — |
| `ToggleUserCardEnabled` | — |
| `SetStructuredAutoMaintain` | `enabled` |
| `CompressContext` | — (after `openSheet("compress")`) |

### Tools / suggestions / HUD / helpers

| type | Fields |
|------|--------|
| `ToggleToolApproval` | `callId` |
| `ToggleToolSubItemApproval` | `callId`, `subItemId` |
| `UpdateToolFeedback` | `callId`, `feedback` |
| `UpdateToolBatchFeedback` | `feedback` |
| `ConfirmPendingTools` / `RejectPendingTools` | — |
| `SelectSuggestion` | `text` |
| `DismissSuggestions` | — |
| `RemovePendingAttachment` | `id` |
| `RegenerateGalgameHud` / `RecreateGalgameHud` | — |
| `SelectOutlineBranch` | `optionId` |
| `ToggleQuestionOption` | `callId`, `questionId`, `optionId` |
| `UpdateQuestionCustomText` | `callId`, `questionId`, `text` |
| `SubmitUserQuestions` | `callId` |
| `DismissUserQuestions` | — |
| `ConfirmHabitMemory` / `RejectHabitMemory` | — |
| `UpdateTtsSecretFill` | `callId`, optional `apiKey`, `secretKey` |
| `AiHelpReply` | `draftText`, optional `inputMode` |
| `ShowWorkspaceSheet` | — |

## `applyState` fields

Slim projection. Free shells use what they need; ignore the rest.

### Session

`currentConversationId`, `conversationType`, `isSending`, `messagesReady`, `hasMoreHistory`, `isLoadingHistory`

`conversations[]`: `id`, `title`, `updatedAt`, `isSelected`, `providerName`, `modelName`, `type`, `characterCardId`, `promptIds`

### Messages

`messages[]`, `streamingMessage`, `regeneratingMessageId`

Message: `id`, `role`, `content`, `reasoning`, `createdAt`, `thinkingDuration`, `branchIndex`, `totalBranches`, `parentMessageId`, `speakerName`, `speakerColorIndex`, `speakerCardId`, `excludeFromContext`, `parts[]`, `bookResults[]`

`parts[].kind`: `text` \| `reasoning` \| `tool` \| `attachment` \| `book_result` (plus kind-specific fields). Chat UI may render thinking vs tools as separate folds; writing may group them — presentation choice.

### Writing / user

`writingSubMode`, `userName`, `userDescription`, `userCardEnabled`, `continueActionPrompt`, `writingRoundCount`

`selectedCharacterName`, `selectedCharacterNames[]`, `selectedCharacterCount`

`selectedCharacterCards[]`: `{id,name,avatarPath,dramaticRole}`

### Toggles / flags

`outputMode`, `pendingPlan`, `webSearchArmed`, `galgameEnabled`, `interCharacterChatEnabled`, `dialogueHighlightEnabled`, `roleplayDialogueBubbleEnabled`, `structuredAutoMaintainEnabled`, `outlineEnabled`, `postEditEnabled`, `isPostEditing`, `multiBubbleProtocolEnabled`, `isMaintainingStructuredData`, `habitReplyStyle`, `workspaceId`, `workspaceName`

### Context

`contextTokensUsed`, `contextInputBudget`, `contextWindow`, `isCompressing`, `cacheHitRatio`, `contextTokensSource`, `contextCalibrationScale`, `contextCacheHitTokens`, `contextUsageSlices[]`, `compressedSummary`

### Panels / composer

`pendingToolConfs[]`, `pendingToolBatchFeedback`, `outlineBranchChoice`, `pendingUserQuestions`, `pendingHabitMemoryConfirm`, `pendingTtsSecretFills[]`, `pendingSnapshotUndo`, `galgameHudHtml`, `galgameHudLoading`, `suggestions[]`, `suggestionsLoading`, `pendingAttachments[]`, `isProcessingAttachments`, `reasoningLevel`

### Skills / theme chrome

`availableSkills[]`, `conversationSkillIds`

`themeId`, `themeVersion`, `themeDirty` (also via `applyTheme`)

### Not projected

Outline/workspace full JSON, memory/world-book bodies, full catalogs, `toolTrace`, attachment local paths → sheets / native only.

## Free-shell binding notes

- `applyTheme` replaces **innerHTML** of each `[data-slot]` present in the map. Empty/missing keys can restore slot defaults (stock behavior).
- Keep `<dialog>` (or equivalent) if you still use stock edit/theme flows — or reimplement those flows in your `app.js`.
- Escape untrusted text when building HTML from `applyState` (message `content` / tool IO). `galgameHudHtml` is HTML by design — treat carefully.
- Minimal viable loop: `ready()` → handle `applyState` → `postIntent(SendMessage|StopGenerating|…)` → show streaming text. Everything else is progressive enhancement.

## Forbidden

- Writing APK built-ins (tools never do)
- Skipping compress confirm
- Inventing `postIntent` types
- Remote script/font CDNs inside the theme
- Dropping the capability contract unless `skipShellValidation` is intentional
