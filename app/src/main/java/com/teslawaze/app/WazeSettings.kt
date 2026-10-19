package com.teslawaze.app

import android.content.Context

/**
 * Réglages de l'app.
 *
 * La destination n'est **plus** une coordonnée factice : elle vient du compte Tesla
 * (`drive_state.active_route_*`). Ne restent ici que le comportement Waze et les URL
 * (surchargeables pour les tests / un proxy local).
 */
data class WazeSettings(
    val screenOff: Boolean = DEFAULT_SCREEN_OFF,
    val teslaAuthBase: String = TeslaAuth.DEFAULT_AUTH_BASE,
    val teslaApiBase: String = DEFAULT_TESLA_API_BASE,
    /** `client_id` de l'application Fleet API (developer.tesla.com) — vide si non enregistrée. */
    val teslaClientId: String = BuildConfig.TESLA_CLIENT_ID,
    /** URI de redirection **déclarée pour ce client_id**. */
    val teslaRedirectUri: String = BuildConfig.TESLA_REDIRECT_URI,
    /**
     * Identifiant API du véhicule (`eid`, ex. 929725129941616).
     * Indispensable depuis que `GET /api/1/vehicles` (la liste) est réservé à Fleet API :
     * les endpoints par identifiant (`/api/1/vehicles/{id}` et `vehicle_data`) répondent
     * encore à un jeton legacy.
     */
    val teslaVehicleId: Long = 0L
) {
    companion object {
        const val DEFAULT_SCREEN_OFF = true

        /** Hôte utilisé par TeslaMate (`get/2`, `get_with_state/2`). */
        const val DEFAULT_TESLA_API_BASE = "https://owner-api.teslamotors.com"
    }
}

/** Lecture / écriture des réglages (mêmes clés qu'avant pour ne rien perdre). */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("teslawaze_settings", Context.MODE_PRIVATE)

    fun load(): WazeSettings = WazeSettings(
        screenOff = prefs.getBoolean(KEY_SCREEN_OFF, WazeSettings.DEFAULT_SCREEN_OFF),
        teslaAuthBase = prefs.getString(KEY_AUTH_BASE, null)
            ?.takeIf { it.isNotBlank() } ?: TeslaAuth.DEFAULT_AUTH_BASE,
        teslaApiBase = prefs.getString(KEY_API_BASE, null)
            ?.takeIf { it.isNotBlank() } ?: WazeSettings.DEFAULT_TESLA_API_BASE,
        teslaClientId = prefs.getString(KEY_CLIENT_ID, null) ?: BuildConfig.TESLA_CLIENT_ID,
        teslaRedirectUri = prefs.getString(KEY_REDIRECT_URI, null)
            ?: BuildConfig.TESLA_REDIRECT_URI,
        teslaVehicleId = prefs.getString(KEY_VEHICLE_ID, null)?.toLongOrNull() ?: 0L
    )

    fun save(s: WazeSettings) {
        prefs.edit()
            .putBoolean(KEY_SCREEN_OFF, s.screenOff)
            .putString(KEY_AUTH_BASE, s.teslaAuthBase)
            .putString(KEY_API_BASE, s.teslaApiBase)
            .putString(KEY_CLIENT_ID, s.teslaClientId)
            .putString(KEY_REDIRECT_URI, s.teslaRedirectUri)
            .putString(KEY_VEHICLE_ID, s.teslaVehicleId.toString())
            .apply()
    }

    /**
     * « Quitter » : le service ne doit pas revenir au prochain démarrage du téléphone.
     * Réarmé dès que l'app est rouverte.
     */
    fun serviceEnabled(): Boolean = prefs.getBoolean(KEY_SERVICE, true)

    fun setServiceEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_SERVICE, enabled).apply()

    private companion object {
        const val KEY_SCREEN_OFF = "waze_screen_off"
        const val KEY_AUTH_BASE = "tesla_auth_base"
        const val KEY_API_BASE = "tesla_api_base"
        const val KEY_CLIENT_ID = "tesla_client_id"
        const val KEY_REDIRECT_URI = "tesla_redirect_uri"
        const val KEY_VEHICLE_ID = "tesla_vehicle_id"
        const val KEY_SERVICE = "service_enabled"
    }
}
