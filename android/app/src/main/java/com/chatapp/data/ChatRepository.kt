package com.chatapp.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository {

    suspend fun register(name: String, email: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = ApiClient.service.register(
                    AuthBody(name = name, email = email, password = password)
                )
                val body = resp.body()
                if (resp.isSuccessful && body != null) {
                    body
                } else {
                    throw ApiException(parseError(resp.errorBody()?.string()))
                }
            }
        }

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = ApiClient.service.login(AuthBody(email = email, password = password))
                val body = resp.body()
                if (resp.isSuccessful && body != null) {
                    body
                } else {
                    throw ApiException(parseError(resp.errorBody()?.string()))
                }
            }
        }

    suspend fun fetchUsers(query: String? = null): Result<List<User>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = ApiClient.service.getUsers(ApiClient.bearer(), query)
                val body = resp.body()
                if (resp.isSuccessful && body != null) {
                    body.users
                } else {
                    throw ApiException("Failed to load contacts.")
                }
            }
        }

    suspend fun fetchMessages(peerId: String, query: String? = null): Result<List<Message>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = ApiClient.service.getMessages(ApiClient.bearer(), peerId, query)
                val body = resp.body()
                if (resp.isSuccessful && body != null) {
                    body.messages
                } else {
                    throw ApiException("Failed to load chat history.")
                }
            }
        }

    suspend fun uploadMedia(context: Context, contentUri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName =
                    contentUri.getDisplayName(context.contentResolver)
                        ?: "media_${System.currentTimeMillis()}"
                val mime = context.uriType(contentUri) ?: "application/octet-stream"
                val bytes = context.contentResolver.openInputStream(contentUri)?.use { it.readBytes() }
                    ?: throw ApiException("Cannot read the selected file.")
                performUpload(bytes, fileName, mime)
            }
        }

    suspend fun uploadBytes(bytes: ByteArray, fileName: String, mime: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching { performUpload(bytes, fileName, mime) }
        }

    private suspend fun performUpload(bytes: ByteArray, fileName: String, mime: String): String {
        val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull(), 0, bytes.size)
        val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
        val resp = ApiClient.service.uploadMedia(part)
        val body = resp.body()
        if (resp.isSuccessful && body != null) {
            return body.url
        }
        throw ApiException("Upload failed.")
    }

    private fun parseError(raw: String?): String {
        return try {
            ApiClient.gson.fromJson(raw, ApiError::class.java)?.error
                ?: "Something went wrong."
        } catch (e: Exception) {
            "Something went wrong."
        }
    }

    class ApiException(message: String) : Exception(message)
}
