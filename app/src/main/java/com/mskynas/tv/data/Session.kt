package com.mskynas.tv.data

import android.content.Context
import java.util.UUID

/** Guarda en el dispositivo la cuenta y el servidor elegido. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("mskynas", Context.MODE_PRIVATE)

    val clientId: String = prefs.getString("clientId", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("clientId", it).apply() }

    var userToken: String?
        get() = prefs.getString("userToken", null)
        set(value) = prefs.edit().putString("userToken", value).apply()

    val serverUri: String? get() = prefs.getString("serverUri", null)
    val serverToken: String? get() = prefs.getString("serverToken", null) ?: userToken
    val serverName: String? get() = prefs.getString("serverName", null)
    val serverId: String? get() = prefs.getString("serverId", null)

    fun setServer(resource: Resource, uri: String) {
        prefs.edit()
            .putString("serverUri", uri)
            .putString("serverToken", resource.accessToken ?: userToken)
            .putString("serverName", resource.name)
            .putString("serverId", resource.clientIdentifier)
            .apply()
    }

    fun clear() {
        val id = clientId
        prefs.edit().clear().putString("clientId", id).apply()
    }
}
