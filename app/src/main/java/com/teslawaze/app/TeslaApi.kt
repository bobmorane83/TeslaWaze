package com.teslawaze.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 401/403 : le jeton est mort ⇒ reconnexion (même logique que TeslaMate). */
class TeslaAuthException(message: String) : Exception(message)

/** 408/503 : véhicule endormi / indisponible (TeslaMate : `vehicle_unavailable`). */
class TeslaVehicleUnavailable(message: String) : Exception(message)

class TeslaApiException(val code: Int, message: String) : Exception(message)

/**
 * Client Fleet API minimal, calqué sur ce que fait TeslaMate :
 *
 *  - `GET /api/1/vehicles`                                     → liste (id, vin, nom)
 *  - `GET /api/1/vehicles/{id}`                                → **sonde d'état, ne réveille pas**
 *  - `GET /api/1/vehicles/{id}/vehicle_data?endpoints=…`        → **réveille** : à n'appeler
 *    que si la sonde dit `online`
 *
 * La destination de navigation est dans `drive_state` :
 * `active_route_latitude`, `active_route_longitude`, `active_route_destination`,
 * `active_route_minutes_to_arrival`, … (champs `nil` si aucune navigation).
 *
 * Tous les appels sont **suspend** et s'exécutent sur `Dispatchers.IO` : jamais de réseau
 * sur le thread principal (`NetworkOnMainThreadException`).
 */
class TeslaApi(
    baseUrl: String,
    /**
     * Hôte **Fleet API** de secours, déduit du JWT (`aud`) : l'API historique répond
     * désormais « Endpoint is only available on fleetapi ».
     */
    private val fleetFallbackUrl: String? = null
) {

    private var root = baseUrl.trimEnd('/')
    private var switchedToFleet = false

    data class Vehicle(val id: Long, val vin: String?, val displayName: String?)

    data class Destination(
        val lat: Double,
        val lon: Double,
        val name: String?,
        val minutesToArrival: Double?
    )

    /**
     * État de conduite utile au pilotage de Waze :
     *  - `destination` : `null` = pas de navigation en cours (route effacée) ;
     *  - `shiftState`  : `"P"` véhicule garé/arrêté, `"D"`/`"R"`/`"N"` en mouvement
     *    (`null` si le champ est absent — véhicule endormi) ;
     *  - `speedMph`    : vitesse instantanée (0 au repos).
     */
    data class DriveState(
        val destination: Destination?,
        val shiftState: String?,
        val speedMph: Double?
    )

    suspend fun vehicles(token: String): List<Vehicle> = withContext(Dispatchers.IO) {
        val o = getJson("/api/1/vehicles", token)
        val arr = o.optJSONArray("response") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val v = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = v.optLong("id", 0L)
            if (id == 0L) null else Vehicle(
                id = id,
                vin = v.optString("vin").takeIf { it.isNotBlank() },
                displayName = v.optString("display_name").takeIf { it.isNotBlank() }
            )
        }
    }

    /** `online` / `asleep` / `offline` — **sans réveiller la voiture**. */
    suspend fun state(token: String, vehicleId: Long): String = withContext(Dispatchers.IO) {
        val o = getJson("/api/1/vehicles/$vehicleId", token)
        o.optJSONObject("response")?.optString("state").orEmpty()
    }

    /**
     * État de conduite + destination. **Cet appel réveille la voiture** : ne l'appeler
     * qu'après une sonde `online`, et une seule fois par cycle.
     */
    suspend fun driveState(token: String, vehicleId: Long): DriveState =
        withContext(Dispatchers.IO) {
            val o = getJson(
                "/api/1/vehicles/$vehicleId/vehicle_data?endpoints=drive_state",
                token
            )
            val drive = o.optJSONObject("response")?.optJSONObject("drive_state")
                ?: return@withContext DriveState(null, null, null)

            val shift = when {
                drive.isNull("shift_state") -> null
                else -> drive.optString("shift_state").takeIf { it.isNotBlank() && it != "null" }
            }
            val speed = if (drive.isNull("speed")) null
            else drive.optDouble("speed", Double.NaN).takeUnless { it.isNaN() }

            // `null` (ou absent) = aucune destination : c'est exactement le cas « ne rien faire ».
            if (drive.isNull("active_route_latitude") || drive.isNull("active_route_longitude")) {
                return@withContext DriveState(null, shift, speed)
            }
            val lat = drive.optDouble("active_route_latitude", Double.NaN)
            val lon = drive.optDouble("active_route_longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return@withContext DriveState(null, shift, speed)

            DriveState(
                destination = Destination(
                    lat = lat,
                    lon = lon,
                    name = drive.optString("active_route_destination")
                        .takeIf { it.isNotBlank() && it != "null" },
                    minutesToArrival = if (drive.isNull("active_route_minutes_to_arrival")) null
                    else drive.optDouble("active_route_minutes_to_arrival", Double.NaN)
                        .takeUnless { it.isNaN() }
                ),
                shiftState = shift,
                speedMph = speed
            )
        }

    private suspend fun getJson(path: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        val (code, body) = Http.get(root + path, token)

        // Bascule unique vers l'hôte Fleet attaché au jeton (owner-api ne sert plus les données).
        val fallback = fleetFallbackUrl?.trimEnd('/')
        if (!switchedToFleet && fallback != null &&
            body.contains("only available on fleetapi", ignoreCase = true)
        ) {
            switchedToFleet = true
            android.util.Log.d("TeslaWaze", "tesla: bascule API -> $fallback")
            root = fallback
            return@withContext getJson(path, token)
        }

        when (code) {
            401, 403 -> throw TeslaAuthException("HTTP $code — jeton refusé")
            408, 503 -> throw TeslaVehicleUnavailable("HTTP $code — véhicule indisponible")
        }
        if (code !in 200..299) throw TeslaApiException(code, body.take(300))
        JSONObject(body)
    }
}
