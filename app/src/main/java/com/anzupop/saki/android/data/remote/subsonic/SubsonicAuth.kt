package com.anzupop.saki.android.data.remote.subsonic

import com.anzupop.saki.android.domain.model.ServerConfig
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

internal object SubsonicAuth {
    fun baseQuery(server: ServerConfig): Map<String, String> {
        val salt = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .take(8)

        return linkedMapOf(
            "u" to server.username,
            "t" to md5("${server.password}$salt"),
            "s" to salt,
            "v" to server.apiVersion,
            "c" to server.clientName,
            "f" to "json",
        )
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())

        return digest.joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte)
        }
    }
}
