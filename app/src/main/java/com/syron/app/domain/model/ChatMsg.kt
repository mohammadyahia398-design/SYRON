package com.syron.app.domain.model

data class ChatMsg(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val isError: Boolean = false
)
