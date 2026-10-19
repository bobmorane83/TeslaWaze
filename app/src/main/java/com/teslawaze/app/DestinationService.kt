package com.teslawaze.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service **permanent** : c'est lui qui surveille la voiture et pilote Waze, indépendamment
 * de l'écran de l'app (un appui sur « retour » ne l'arrête pas ; seul « Quitter » l'arrête).
 *
 * Politique :
 *  - destination présente (route active, véhicule non garé) → **lance Waze** (deep link),
 *    et le relance si la destination change ;
 *  - destination retirée, véhicule garé (passage en `P` après avoir roulé) ou véhicule
 *    endormi/hors ligne → **arrête Waze** (`WazeStopper`) ;
 *  - deux lectures concordantes sont exigées avant d'arrêter quoi que ce soit : Waze rend
 *    `active_route_*` nul un instant pendant un recalcul de route.
 *
 * Cadences identiques à TeslaMate (30 s véhicule endormi, 60 s en ligne) : la sonde d'état
 * `GET /api/1/vehicles/{id}` **ne réveille pas** la voiture, seul `vehicle_data` le fait —
 * et il n'est appelé que si le véhicule est déjà `online`.
 */
class DestinationService : Service() {

    companion object {
        private const val TAG = "TeslaWaze"
        const val ACTION_STOP = "com.teslawaze.app.action.STOP"

        const val POLL_ASLEEP_MS = 30_000L
        const val POLL_ONLINE_MS = 60_000L
        const val POLL_ERROR_MS = 30_000L

        /** Lectures concordantes avant d'arrêter Waze (un recalcul de route rend la valeur nulle). */
        private const val STOP_CONFIRMATIONS = 2

        fun start(context: Context) {
            val i = Intent(context, DestinationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
                Log.d(TAG, "service: démarrage demandé")
            } catch (e: Exception) {
                // Android 12+ : « ForegroundServiceStartNotAllowedException » si l'app est en fond.
                Log.w(TAG, "service: démarrage refusé: ${e.javaClass.simpleName} ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DestinationService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private lateinit var store: SettingsStore
    private lateinit var session: TeslaSession

    /** Destination actuellement confiée à Waze (`null` = Waze non lancé par nous). */
    private val pilot = WazePilot(STOP_CONFIRMATIONS)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "service: onCreate")
        store = SettingsStore(this)
        session = TeslaSession(this) { store.load() }
        WatcherState.running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // « Quitter » est définitif : une demande de démarrage arrivée en retard (course avec
        // l'activité, relance START_STICKY, boot) ne doit pas ressusciter la surveillance.
        if (intent?.action == ACTION_STOP || !store.serviceEnabled()) {
            Log.d(TAG, "service: démarrage ignoré (quitté)")
            quit()
            return START_NOT_STICKY
        }

        // Notification de service : à poster avant toute autre chose (5 s max).
        val notif = StatusNotification.build(this, WatcherState.text.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                StatusNotification.ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(StatusNotification.ID, notif)
        }

        if (loopJob?.isActive != true) loopJob = scope.launch { loop() }
        return START_STICKY // le système relance le service s'il est tué sous pression mémoire
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        WatcherState.running.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun quit() {
        store.setServiceEnabled(false)
        Log.d(TAG, "service: quit demandé")
        WatcherState.text.value = "Arrêté"
        stopSelf()
    }

    private fun publish(text: String, log: Boolean = true) {
        if (log) Log.d(TAG, "état: $text")
        WatcherState.text.value = text
        StatusNotification.update(this, text)
    }

    // ------------------------------------------------------------------ boucle

    private suspend fun loop() {
        var vehicleId = session.vehicleId()

        while (currentCoroutineContext().isActive) {
            val token = session.validToken()
            if (token == null) {
                publish("Connexion Tesla requise", log = false)
                delay(POLL_ERROR_MS)
                continue
            }
            val api = session.api(token)

            try {
                if (vehicleId == null) {
                    // `/api/1/vehicles` est réservé à Fleet API : un jeton legacy doit fournir l'ID.
                    val list = try {
                        api.vehicles(token.access)
                    } catch (e: TeslaApiException) {
                        emptyList()
                    }
                    vehicleId = list.firstOrNull()?.id
                    if (vehicleId == null) {
                        publish("ID véhicule manquant (Réglages → Avancé)")
                        delay(POLL_ONLINE_MS)
                        continue
                    }
                    session.saveVehicleId(vehicleId)
                    Log.d(TAG, "tesla: véhicule=${list.first().displayName ?: vehicleId}")
                }

                // 1) Sonde d'état — ne réveille PAS la voiture.
                val state = api.state(token.access, vehicleId)
                if (state != "online") {
                    publish("Véhicule $state — en attente")
                    Log.d(TAG, "tesla: state=$state -> attente ${POLL_ASLEEP_MS / 1000}s")
                    apply(pilot.next(false, null, null, null))
                    delay(POLL_ASLEEP_MS)
                    continue
                }

                // 2) Un seul appel qui réveille (véhicule déjà en ligne).
                val drive = api.driveState(token.access, vehicleId)
                if (drive.destination == null) {
                    publish("En ligne — pas de destination de navigation")
                }
                apply(
                    pilot.next(
                        online = true,
                        destination = drive.destination,
                        shiftState = drive.shiftState,
                        speedMph = drive.speedMph
                    )
                )
                delay(POLL_ONLINE_MS)
            } catch (e: TeslaAuthException) {
                publish("Connexion Tesla expirée — rouvrez l'app")
                delay(POLL_ERROR_MS)
            } catch (e: TeslaVehicleUnavailable) {
                publish("Véhicule endormi — en attente")
                apply(pilot.next(false, null, null, null))
                delay(POLL_ASLEEP_MS)
            } catch (e: CancellationException) {
                throw e // arrêt du service : ce n'est pas une erreur
            } catch (e: Exception) {
                publish("Tesla : ${e.javaClass.simpleName}")
                Log.w(TAG, "tesla: erreur ${e.javaClass.simpleName}: ${e.message}")
                delay(POLL_ERROR_MS)
            }
        }
    }

    /** Applique la décision de la machine à états. */
    private fun apply(action: WazePilot.Action) {
        when (action) {
            is WazePilot.Action.None -> Unit

            is WazePilot.Action.Idle -> publish(action.text, log = false)

            is WazePilot.Action.Launch -> launch(action.destination)

            is WazePilot.Action.Stop -> {
                val ok = WazeStopper.stop(this)
                Log.d(TAG, "waze: arrêt (${action.reason}) demandé=$ok")
                publish(
                    if (ok) "Waze arrêté (${action.reason})"
                    else "Arrêt de Waze impossible — autorisations manquantes"
                )
            }
        }
    }

    /** Lance Waze sur la destination, puis éteint l'écran (deep link renvoyé une fois). */
    private fun launch(dest: TeslaApi.Destination) {
        val key = pilot.launchedKey
        val ok = WazeLauncher.launch(this, dest.lat, dest.lon)
        Log.d(
            TAG,
            "waze: lancement=%s dest=%.6f,%.6f source=tesla".format(
                Locale.US, ok, dest.lat, dest.lon
            )
        )
        if (!ok) {
            pilot.launchFailed()
            publish("Waze introuvable")
            return
        }
        publish("Waze lancé — ${dest.name ?: "destination Tesla"}")

        val screenOff = store.load().screenOff
        scope.launch {
            // Waze ignore le deep link reçu pendant son démarrage à froid : on le renvoie une fois.
            delay(4_000)
            if (pilot.launchedKey != key) return@launch
            WazeLauncher.launch(this@DestinationService, dest.lat, dest.lon)
            if (!screenOff) return@launch
            delay(1_000)
            val slept = ScreenOff.turnOff(this@DestinationService)
            Log.d(TAG, "waze: ecran eteint=$slept")
        }
    }
}
