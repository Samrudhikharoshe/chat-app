package com.chatapp.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun Uri.getDisplayName(resolver: ContentResolver): String? {
    return try {
        resolver.query(this, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (e: Exception) {
        null
    }
}

fun Context.uriType(uri: Uri): String? {
    return contentResolver.getType(uri)
}
