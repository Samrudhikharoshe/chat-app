package com.chatapp.data

import com.google.gson.annotations.SerializedName

data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatar: String? = null,
    val online: Boolean = false,
    val lastSeen: String? = null,
    val createdAt: String? = null
)

data class Message(
    val id: String,
    @SerializedName("from") val fromId: String,
    @SerializedName("to") val toId: String,
    val type: String = "text",
    val content: String? = null,
    val mediaUrl: String? = null,
    val createdAt: String = "",
    val read: Boolean = false,
    val readAt: String? = null
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class UserListResponse(
    val users: List<User>
)

data class MessageListResponse(
    val messages: List<Message>
)

data class UploadResponse(
    val url: String,
    val filename: String,
    val size: Long,
    val mimetype: String
)

data class ApiError(
    val error: String? = null
)
