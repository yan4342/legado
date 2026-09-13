package io.legado.app.domain.usecase

import android.util.Log
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiToolCall

data class ToolConfirmPolicy(
    val requireMutationApproval: Boolean,
    val conversationType: String,
    /** AI 输出审批模式："auto" | "ask" | "plan"。plan 下写工具跳过、只读工具可执行。 */
    val outputMode: String = "ask",
)

/** Result of the user confirming/rejecting a mutation tool batch. */
data class ToolApprovalDecision(
    val approved: List<AiToolCall> = emptyList(),
    /**
     * Tools from the pending batch that were not executed.
     * Key = tool call id, value = reason for the model (and UI).
     */
    val skippedFeedback: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = approved.isEmpty()
}

interface ToolConfirmHandler {
    suspend fun awaitApproval(
        toolCalls: List<AiToolCall>,
        round: Int,
    ): ToolApprovalDecision
}

interface InteractiveToolHandler {
    suspend fun awaitResponse(call: AiToolCall): String
}

class ToolAgentLoop {
    data class LoopResult(
        val request: AiGenerateRequest,
        val fullText: String,
        val fullReasoning: String,
        val toolTrace: ToolTraceBuilder,
        val userRejected: Boolean = false,
    )

    suspend fun run(
        initialRequest: AiGenerateRequest,
        initialText: String,
        initialReasoning: String,
        toolTrace: ToolTraceBuilder,
        policy: ToolConfirmPolicy,
        confirmHandler: ToolConfirmHandler?,
        maxRounds: Int,
        onStreamRound: suspend (
            request: AiGenerateRequest,
            fullText: StringBuilder,
            fullReasoning: StringBuilder,
            toolTrace: ToolTraceBuilder,
        ) -> Unit,
        executeBatch: suspend (
            convId: String?,
            request: AiGenerateRequest,
            fullText: StringBuilder,
            fullReasoning: StringBuilder,
            toolTrace: ToolTraceBuilder,
            calls: List<AiToolCall>,
            round: Int,
            /** Full tool_calls list for the assistant message (includes skipped). Defaults to [calls]. */
            roundToolCalls: List<AiToolCall>?,
            /** Length of [fullText] already committed to the request history. The assistant message
             *  committed by this batch must carry only the not-yet-committed text delta, otherwise the
             *  model sees its own previous output again and re-states it (duplicated content). */
            committedTextLen: Int,
            /** Length of [fullReasoning] already committed. The assistant message committed by this
             *  batch carries only the reasoning delta since the last round (for pass-back in thinking mode). */
            committedReasoningLen: Int,
        ) -> AiGenerateRequest,
        onMaxRoundsReached: suspend (
            convId: String?,
            request: AiGenerateRequest,
            fullText: StringBuilder,
            fullReasoning: StringBuilder,
            toolTrace: ToolTraceBuilder,
            remaining: List<AiToolCall>,
            committedTextLen: Int,
            committedReasoningLen: Int,
        ) -> Unit = { _, _, _, _, _, _, _, _ -> },
        appendRejectedRound: suspend (
            convId: String?,
            request: AiGenerateRequest,
            fullText: StringBuilder,
            fullReasoning: StringBuilder,
            toolTrace: ToolTraceBuilder,
            rejectedCalls: List<AiToolCall>,
            committedTextLen: Int,
            committedReasoningLen: Int,
        ) -> AiGenerateRequest = { _, req, _, _, _, _, _, _ -> req },
        convId: String? = null,
        needsBookshelfApproval: suspend (AiToolCall) -> Boolean = { false },
    ): LoopResult {
        var currentRequest = initialRequest
        val fullText = StringBuilder(initialText)
        val fullReasoning = StringBuilder(initialReasoning)
        // The initial round's text has not been committed to the request history yet:
        // the first batch must carry the whole accumulated text, later batches only deltas.
        var committedTextLen = 0
        var committedReasoningLen = 0
        var round = 0
        while (round < maxRounds) {
            val allCalls = toolTrace.pendingToolCalls()
            if (allCalls.isEmpty()) break

            val approvalCalls = mutableListOf<AiToolCall>()
            val autoCalls = mutableListOf<AiToolCall>()
            val isPlan = policy.outputMode == "plan"
            for (call in allCalls) {
                // Plan mode：写操作直接跳过（记入 trace，不执行），只读工具可执行。
                // 例外：write_file/edit_file/delete_file 指向 plan:// 时是计划工件本身的操作，放行且不审批。
                val isPlanFileOp = isPlan &&
                    call.name in setOf(
                        AiToolRepository.TOOL_WRITE_FILE,
                        AiToolRepository.TOOL_EDIT_FILE,
                        AiToolRepository.TOOL_DELETE_FILE,
                    ) &&
                    call.arguments.contains("plan://")
                if (isPlan && AiToolRepository.isPlanModeMutation(call.name, call.arguments)) {
                    toolTrace.appendResult(
                        call.id,
                        ToolTraceBuilder.formatSkippedResult("计划模式：写操作留待批准后执行"),
                    )
                    continue
                }
                val needsMutation = if (isPlanFileOp) {
                    false
                } else {
                    AiToolRepository.needsMutationConfirm(
                        call.name,
                        policy.requireMutationApproval,
                        policy.conversationType,
                    )
                }
                val needsBookshelf = policy.conversationType == "chat" &&
                    needsBookshelfApproval(call)
                val needsWebSearch = policy.conversationType == "chat" &&
                    AiToolRepository.isWebSearchConfirmTool(call.name)
                val needsHabitMemory = AiToolRepository.needsHabitMemoryConfirm(
                    call.name,
                    policy.conversationType,
                )
                if (call.name == AiToolRepository.TOOL_SEARCH_BOOK_SOURCES ||
                    call.name == AiToolRepository.TOOL_ADD_BOOK_TO_BOOKSHELF ||
                    call.name == AiToolRepository.TOOL_WEB_SEARCH ||
                    call.name == AiToolRepository.TOOL_READ_WEB_PAGE
                ) {
                    Log.i(
                        AiToolRepository.SHELF_LOG_TAG,
                        "gate round=$round name=${call.name} id=${call.id} " +
                            "needsBookshelf=$needsBookshelf needsWebSearch=$needsWebSearch " +
                            "needsMutation=$needsMutation mode=${policy.conversationType}",
                    )
                }
                if (needsMutation || needsBookshelf || needsWebSearch || needsHabitMemory) {
                    approvalCalls.add(call)
                } else {
                    autoCalls.add(call)
                }
            }

            var batchCalls: List<AiToolCall>? = null
            var batchToolCalls: List<AiToolCall>? = null

            if (approvalCalls.isNotEmpty()) {
                val decision = when {
                    confirmHandler != null -> confirmHandler.awaitApproval(approvalCalls, round)
                    else -> ToolApprovalDecision()
                }
                val approved = decision.approved
                if (approved.isEmpty()) {
                    val hadMutation = approvalCalls.any {
                        AiToolRepository.needsMutationConfirm(
                            it.name,
                            policy.requireMutationApproval,
                            policy.conversationType,
                        ) || AiToolRepository.isMutationTool(it.name)
                    }
                    val hadBookshelf = approvalCalls.any {
                        AiToolRepository.isBookshelfAccessConfirmTool(it.name)
                    }
                    val hadWebSearch = approvalCalls.any {
                        AiToolRepository.isWebSearchConfirmTool(it.name)
                    }
                    if (hadBookshelf || hadWebSearch) {
                        Log.w(
                            AiToolRepository.SHELF_LOG_TAG,
                            "gate REJECTED round=$round tools=${approvalCalls.joinToString { it.name }}",
                        )
                    }
                    val shouldHandleRejection = policy.conversationType != "writing" &&
                        (hadBookshelf || hadWebSearch || hadMutation)
                    if (shouldHandleRejection) {
                        val allCancelled = approvalCalls.all {
                            toolTrace.resultOf(it.id) == ToolTraceBuilder.RESULT_CANCELLED
                        }
                        if (allCancelled) {
                            return LoopResult(
                                request = currentRequest,
                                fullText = fullText.toString(),
                                fullReasoning = fullReasoning.toString(),
                                toolTrace = toolTrace,
                                userRejected = true,
                            )
                        }
                        currentRequest = appendRejectedRound(
                            convId,
                            currentRequest,
                            fullText,
                            fullReasoning,
                            toolTrace,
                            approvalCalls,
                            committedTextLen,
                            committedReasoningLen,
                        )
                        committedTextLen = fullText.length
                        committedReasoningLen = fullReasoning.length
                        round++
                        onStreamRound(currentRequest, fullText, fullReasoning, toolTrace)
                        continue
                    }
                    // Rejected but not handled (e.g. writing mode): only auto tools run this round.
                    batchCalls = autoCalls
                    batchToolCalls = autoCalls
                } else {
                    // Partial or full approve: record skipped tools, execute approved + auto in one
                    // batch, keep the full assistant tool_calls list so the model sees every outcome.
                    for (call in approvalCalls) {
                        val skipReason = decision.skippedFeedback[call.id]
                        if (skipReason != null && toolTrace.resultOf(call.id).isNullOrBlank()) {
                            toolTrace.appendResult(
                                call.id,
                                ToolTraceBuilder.formatSkippedResult(skipReason),
                            )
                        }
                    }
                    val shelfApproved = approved.filter {
                        it.name == AiToolRepository.TOOL_SEARCH_BOOK_SOURCES ||
                            it.name == AiToolRepository.TOOL_ADD_BOOK_TO_BOOKSHELF
                    }
                    if (shelfApproved.isNotEmpty()) {
                        Log.i(
                            AiToolRepository.SHELF_LOG_TAG,
                            "gate APPROVED round=$round tools=${shelfApproved.joinToString {
                                "${it.name}(approved=${it.bookshelfAccessApproved})"
                            }}",
                        )
                    }
                    if (decision.skippedFeedback.isNotEmpty()) {
                        Log.i(
                            "AiTool",
                            "gate PARTIAL round=$round approved=${approved.size} " +
                                "skipped=${decision.skippedFeedback.size}",
                        )
                    }
                    batchCalls = approved + autoCalls
                    batchToolCalls = approvalCalls + autoCalls
                }
            } else if (autoCalls.isNotEmpty()) {
                val shelfAuto = autoCalls.filter {
                    it.name == AiToolRepository.TOOL_SEARCH_BOOK_SOURCES ||
                        it.name == AiToolRepository.TOOL_ADD_BOOK_TO_BOOKSHELF
                }
                if (shelfAuto.isNotEmpty()) {
                    Log.w(
                        AiToolRepository.SHELF_LOG_TAG,
                        "gate AUTO round=$round tools=${shelfAuto.joinToString { it.name }} " +
                            "(no approval flag — expect BLOCKED if fetch enabled)",
                    )
                }
                batchCalls = autoCalls
                batchToolCalls = autoCalls
            }

            if (!batchCalls.isNullOrEmpty()) {
                currentRequest = executeBatch(
                    convId,
                    currentRequest,
                    fullText,
                    fullReasoning,
                    toolTrace,
                    batchCalls,
                    round,
                    batchToolCalls,
                    committedTextLen,
                    committedReasoningLen,
                )
                committedTextLen = fullText.length
                committedReasoningLen = fullReasoning.length
            }

            round++
            onStreamRound(currentRequest, fullText, fullReasoning, toolTrace)
        }

        val remaining = toolTrace.pendingToolCalls()
        if (remaining.isNotEmpty()) {
            onMaxRoundsReached(convId, currentRequest, fullText, fullReasoning, toolTrace, remaining, committedTextLen, committedReasoningLen)
        } else {
            toolTrace.finalizeIncomplete()
        }

        return LoopResult(
            request = currentRequest,
            fullText = fullText.toString(),
            fullReasoning = fullReasoning.toString(),
            toolTrace = toolTrace,
        )
    }
}
