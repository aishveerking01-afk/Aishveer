package com.rui.app.data

import java.util.UUID

enum class Role { User, Assistant }

data class Message(
    val id: Long,
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
)

data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<Message> = emptyList(),
)
