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
    /** Relance Waze avec la destination Tesla s'il n'est plus actif (toutes les 15 s). */
    val wazeWatchdog: Boolean = DEFAULT_WAZE_WATCHDOG,
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
    val teslaVehicleId: Long = 0L,
    /** Intervalle de lecture quand le véhicule est en ligne (secondes). */
    val pollOnlineSec: Int = DEFAULT_POLL_ONLINE_SEC,
    /** Intervalle de lecture quand le véhicule est endormi/hors ligne (secondes). */
    val pollAsleepSec: Int = DEFAULT_POLL_ASLEEP_SEC,
    /** Intervalle de lecture après une erreur (secondes). */
    val pollErrorSec: Int = DEFAULT_POLL_ERROR_SEC
) {
    fun pollOnlineMs(): Long = clampPollOnlineSec(pollOnlineSec) * 1000L
    fun pollAsleepMs(): Long = clampPollAsleepSec(pollAsleepSec) * 1000L
    fun pollErrorMs(): Long = clampPollErrorSec(pollErrorSec) * 1000L

    companion object {
        const val DEFAULT_SCREEN_OFF = true

        /** Désactivé par défaut : le watchdog relance Waze, il ne doit pas surprendre. */
        const val DEFAULT_WAZE_WATCHDOG = false

        /** Hôte utilisé par TeslaMate (`get/2`, `get_with_state/2`). */
        const val DEFAULT_TESLA_API_BASE = "https://owner-api.teslamotors.com"

        const val DEFAULT_POLL_ONLINE_SEC = 60
        const val DEFAULT_POLL_ASLEEP_SEC = 30
        const val DEFAULT_POLL_ERROR_SEC = 30
        const val MIN_POLL_SEC = 10
        /** Plafond : le double de la valeur par défaut. */
        const val MAX_POLL_ONLINE_SEC = DEFAULT_POLL_ONLINE_SEC * 2
        const val MAX_POLL_ASLEEP_SEC = DEFAULT_POLL_ASLEEP_SEC * 2
        const val MAX_POLL_ERROR_SEC = DEFAULT_POLL_ERROR_SEC * 2
        const val POLL_STEP_SEC = 5

        fun clampPollOnlineSec(value: Int): Int =
            value.coerceIn(MIN_POLL_SEC, MAX_POLL_ONLINE_SEC)

        fun clampPollAsleepSec(value: Int): Int =
            value.coerceIn(MIN_POLL_SEC, MAX_POLL_ASLEEP_SEC)

        fun clampPollErrorSec(value: Int): Int =
            value.coerceIn(MIN_POLL_SEC, MAX_POLL_ERROR_SEC)
    }
}

/** Lecture / écriture des réglages (mêmes clés qu'avant pour ne rien perdre). */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("teslawaze_settings", Context.MODE_PRIVATE)

    fun load(): WazeSettings = WazeSettings(
        screenOff = prefs.getBoolean(KEY_SCREEN_OFF, WazeSettings.DEFAULT_SCREEN_OFF),
        wazeWatchdog = prefs.getBoolean(KEY_WAZE_WATCHDOG, WazeSettings.DEFAULT_WAZE_WATCHDOG),
        teslaAuthBase = prefs.getString(KEY_AUTH_BASE, null)
            ?.takeIf { it.isNotBlank() } ?: TeslaAuth.DEFAULT_AUTH_BASE,
        teslaApiBase = prefs.getString(KEY_API_BASE, null)
            ?.takeIf { it.isNotBlank() } ?: WazeSettings.DEFAULT_TESLA_API_BASE,
        teslaClientId = prefs.getString(KEY_CLIENT_ID, null) ?: BuildConfig.TESLA_CLIENT_ID,
        teslaRedirectUri = prefs.getString(KEY_REDIRECT_URI, null)
            ?: BuildConfig.TESLA_REDIRECT_URI,
        teslaVehicleId = prefs.getString(KEY_VEHICLE_ID, null)?.toLongOrNull() ?: 0L,
        pollOnlineSec = WazeSettings.clampPollOnlineSec(
            prefs.getInt(KEY_POLL_ONLINE, WazeSettings.DEFAULT_POLL_ONLINE_SEC)
        ),
        pollAsleepSec = WazeSettings.clampPollAsleepSec(
            prefs.getInt(KEY_POLL_ASLEEP, WazeSettings.DEFAULT_POLL_ASLEEP_SEC)
        ),
        pollErrorSec = WazeSettings.clampPollErrorSec(
            prefs.getInt(KEY_POLL_ERROR, WazeSettings.DEFAULT_POLL_ERROR_SEC)
        )
    )

    fun save(s: WazeSettings) {
        prefs.edit()
            .putBoolean(KEY_SCREEN_OFF, s.screenOff)
            .putBoolean(KEY_WAZE_WATCHDOG, s.wazeWatchdog)
            .putString(KEY_AUTH_BASE, s.teslaAuthBase)
            .putString(KEY_API_BASE, s.teslaApiBase)
            .putString(KEY_CLIENT_ID, s.teslaClientId)
            .putString(KEY_REDIRECT_URI, s.teslaRedirectUri)
            .putString(KEY_VEHICLE_ID, s.teslaVehicleId.toString())
            .putInt(KEY_POLL_ONLINE, WazeSettings.clampPollOnlineSec(s.pollOnlineSec))
            .putInt(KEY_POLL_ASLEEP, WazeSettings.clampPollAsleepSec(s.pollAsleepSec))
            .putInt(KEY_POLL_ERROR, WazeSettings.clampPollErrorSec(s.pollErrorSec))
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
        const val KEY_WAZE_WATCHDOG = "waze_watchdog"
        const val KEY_AUTH_BASE = "tesla_auth_base"
        const val KEY_API_BASE = "tesla_api_base"
        const val KEY_CLIENT_ID = "tesla_client_id"
        const val KEY_REDIRECT_URI = "tesla_redirect_uri"
        const val KEY_VEHICLE_ID = "tesla_vehicle_id"
        const val KEY_POLL_ONLINE = "poll_online_sec"
        const val KEY_POLL_ASLEEP = "poll_asleep_sec"
        const val KEY_POLL_ERROR = "poll_error_sec"
        const val KEY_SERVICE = "service_enabled"
    }
}
