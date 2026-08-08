package com.chatapp.data

import android.content.Context
import android.content.SharedPreferences

class Session private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chat_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var avatarUrl: String?
        get() = prefs.getString(KEY_AVATAR, null)
        set(value) = prefs.edit().putString(KEY_AVATAR, value).apply()

    fun saveAuth(user: User, token: String) {
        this.token = token
        userId = user.id
        userName = user.name
        userEmail = user.email
        avatarUrl = user.avatar
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = !token.isNullOrEmpty()

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_AVATAR = "avatar"

        @Volatile
        private var instance: Session? = null

        fun init(context: Context) {
            if (instance == null) {
                instance = Session(context.applicationContext)
            }
        }

        val current: Session
            get() = checkNotNull(instance) { "Session.init() must be called first" }
    }
}
