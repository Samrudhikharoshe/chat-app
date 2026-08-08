package com.chatapp.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    val gson = Gson()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val service: ApiService = Retrofit.Builder()
        .baseUrl(Config.BASE_URL)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ApiService::class.java)

    fun bearer(): String = "Bearer ${Session.current.token ?: ""}"

    inline fun <reified T> gsonList(value: String?): List<T> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            gson.fromJson(value, object : TypeToken<List<T>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun mediaTypeFor(filename: String): okhttp3.MediaType {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png".toMediaType()
            "gif" -> "image/gif".toMediaType()
            "webp" -> "image/webp".toMediaType()
            else -> "image/jpeg".toMediaType()
        }
    }
}
