package com.chatapp.data

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body body: AuthBody): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: AuthBody): Response<AuthResponse>

    @GET("api/auth/users")
    suspend fun getUsers(
        @Header("Authorization") auth: String,
        @Query("q") query: String? = null
    ): Response<UserListResponse>

    @GET("api/auth/messages/{userId}")
    suspend fun getMessages(
        @Header("Authorization") auth: String,
        @Path("userId") userId: String,
        @Query("q") query: String? = null,
        @Query("limit") limit: Int = 200
    ): Response<MessageListResponse>

    @Multipart
    @POST("api/media/upload")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): Response<UploadResponse>
}

data class AuthBody(
    val name: String? = null,
    val email: String,
    val password: String
)
