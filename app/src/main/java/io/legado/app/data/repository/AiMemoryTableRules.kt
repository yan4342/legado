package io.legado.app.data.repository

import io.legado.app.data.entities.AiMemoryTable

internal object AiMemoryTableRules {
    fun requireConversationId(conversationId: String) {
        require(conversationId.isNotBlank()) { "Memory table must be bound to a conversation" }
    }

    fun requireTableBound(table: AiMemoryTable) {
        requireConversationId(table.conversationId)
    }
}
