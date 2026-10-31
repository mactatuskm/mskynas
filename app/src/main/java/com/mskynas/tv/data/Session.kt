package com.mskynas.tv.data

import android.content.Context
import java.util.UUID

/** Guarda en el dispositivo la cuenta, el perfil activo y el servidor elegido. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("mskynas", Context.MODE_PRIVATE)

    val clientId: String = prefs.getString("clientId", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("clientId", it).apply() }

    /** Token del perfil activo (cambia al elegir otro perfil). */
    var userToken: String?
        get() = prefs.getString("userToken", null)
        set(value) = prefs.edit().putString("userToken", value).apply()

    /** Token de la cuenta principal, con el que se listan y cambian los perfiles. */
    val accountToken: String? get() = prefs.getString("accountToken", null) ?: userToken

    val serverUri: String? get() = prefs.getString("serverUri", null)
    val serverToken: String? get() = prefs.getString("serverToken", null) ?: userToken
    val serverName: String? get() = prefs.getString("serverName", null)
    val serverId: String? get() = prefs.getString("serverId", null)

    /** Perfil activo de Plex Home. Si es null, es el de la cuenta principal. */
    val profileId: Long? get() = if (prefs.contains("profileId")) prefs.getLong("profileId", 0) else null
    val profileTitle: String? get() = prefs.getString("profileTitle", null)
    val profileThumb: String? get() = prefs.getString("profileThumb", null)

    /** Recién vinculada la TV en plex.tv/link: ese token es el de la cuenta principal. */
    fun login(token: String) {
        prefs.edit()
            .putString("userToken", token)
            .putString("accountToken", token)
            .remove("profileId").remove("profileTitle").remove("profileThumb")
            .apply()
    }

    fun setServer(resource: Resource, uri: String) {
        prefs.edit()
            .putString("serverUri", uri)
            .putString("serverToken", resource.accessToken ?: userToken)
            .putString("serverName", resource.name)
            .putString("serverId", resource.clientIdentifier)
            .apply()
    }

    fun setProfile(user: HomeUser, token: String, serverToken: String) {
        val edit = prefs.edit()
        // Instalaciones anteriores solo guardaban userToken: antes de reemplazarlo se conserva
        // como token de la cuenta principal.
        if (!prefs.contains("accountToken")) edit.putString("accountToken", userToken)
        edit.putString("userToken", token)
            .putString("serverToken", serverToken)
            .putLong("profileId", user.id)
            .putString("profileTitle", user.title)
            .putString("profileThumb", user.thumb)
            .apply()
    }

    fun clear() {
        val id = clientId
        prefs.edit().clear().putString("clientId", id).apply()
    }
}
