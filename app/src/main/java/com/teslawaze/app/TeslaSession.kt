package com.teslawaze.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Session Tesla : jeton (rafraîchi au besoin, comme `TeslaApi.Auth.Refresh` de TeslaMate)
 * et identifiant du véhicule retenu.
 */
class TeslaSession(
    context: Context,
    private val settings: () -> WazeSettings
) {

    private val store = TeslaTokenStore(context)
    private val prefs = context.applicationContext
        .getSharedPreferences("tesla_auth", Context.MODE_PRIVATE)

    fun token(): TeslaToken? = store.load()

    fun save(token: TeslaToken) = store.save(token)

    fun clear() {
        store.clear()
        prefs.edit().remove(KEY_VEHICLE_ID).apply()
    }

    fun vehicleId(): Long? =
        prefs.getLong(KEY_VEHICLE_ID, 0L).takeIf { it > 0L }
            ?: settings().teslaVehicleId.takeIf { it > 0L }

    fun saveVehicleId(id: Long) = prefs.edit().putLong(KEY_VEHICLE_ID, id).apply()

    /**
     * Jeton utilisable : rafraîchi s'il expire dans moins de 2 minutes (appel réseau → IO).
     * `null` ⇒ connexion Tesla requise (pas de jeton, ou refresh révoqué).
     */
    suspend fun validToken(): TeslaToken? {
        val t = store.load() ?: return null
        if (!t.isExpiring()) return t
        val refresh = t.refresh
        if (refresh == null) {
            store.clear()
            return null
        }
        val fresh = withContext(Dispatchers.IO) {
            TeslaAuth.refresh(
                settings().teslaAuthBase,
                t.clientId.ifBlank { TeslaAuth.LEGACY_CLIENT_ID },
                refresh
            )
        }
        if (fresh == null) {
            store.clear()
            return null
        }
        store.save(fresh)
        return fresh
    }

    /**
     * Client API : si le jeton porte un hôte Fleet (`aud`), on l'utilise directement ;
     * sinon on retombe sur l'hôte configuré (jeton factice / proxy local).
     */
    fun api(token: TeslaToken): TeslaApi {
        val fleet = TeslaAuth.fleetBaseFromJwt(token.access)
        return if (fleet != null) TeslaApi(fleet) else TeslaApi(settings().teslaApiBase)
    }

    /** Libellé d'état pour l'UI. */
    fun statusLabel(): String {
        val t = store.load() ?: return "Non connecté"
        return if (t.isExpiring()) {
            if (t.refresh == null) "Expiré — reconnexion requise"
            else "À rafraîchir (${t.expiresInMinutes()} min)"
        } else {
            "Connecté — expire dans ${"%.0f".format(Locale.US, t.expiresInMinutes() / 60.0)} h"
        }
    }

    private companion object {
        const val KEY_VEHICLE_ID = "vehicle_id"
    }
}
