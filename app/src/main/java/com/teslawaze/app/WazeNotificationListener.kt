package com.teslawaze.app

import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Accès aux notifications, utilisé pour **arrêter Waze proprement**.
 *
 * Waze publie une notification de service (`CLOSE_WAZE_CHANNEL`) dont l'unique action est
 * **« Éteindre »** : c'est la seule façon, sans root, de fermer Waze depuis une autre app
 * (`killBackgroundProcesses` est sans effet : Waze tourne en service de premier plan).
 *
 * Le service ne lit rien d'autre : il se contente de déclencher cette action de Waze.
 * L'utilisateur l'active une fois dans « Accès aux notifications ».
 */
class WazeNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        Log.d(TAG, "notif-listener: connecté")
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        Log.d(TAG, "notif-listener: déconnecté")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TeslaWaze"

        /** Titres « fermer » rencontrés selon la langue de Waze. */
        private val CLOSE_TITLES = setOf(
            "éteindre", "eteindre", "turn off", "turnoff", "fermer", "close", "arrêter",
            "arreter", "stop", "quitter", "quit"
        )

        @Volatile
        private var instance: WazeNotificationListener? = null

        val isConnected: Boolean get() = instance != null

        /**
         * Déclenche l'action « Éteindre » de la notification de Waze.
         * `false` si l'accès aux notifications n'est pas accordé ou si Waze ne publie rien.
         */
        fun closeWaze(): Boolean {
            val svc = instance ?: return false
            val posted = try {
                svc.activeNotifications
            } catch (e: Exception) {
                Log.w(TAG, "notif-listener: activeNotifications KO: ${e.message}")
                null
            } ?: return false

            for (sbn in posted) {
                if (sbn.packageName != WazeLauncher.PACKAGE) continue
                val actions = sbn.notification.actions ?: continue
                val close = actions.firstOrNull { a ->
                    a.title?.toString()?.trim()?.lowercase() in CLOSE_TITLES
                } ?: actions.firstOrNull()
                val intent = close?.actionIntent ?: continue
                return try {
                    intent.send()
                    Log.d(TAG, "waze: action « ${close.title} » déclenchée")
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "waze: action impossible: ${e.message}")
                    false
                }
            }
            Log.d(TAG, "waze: aucune notification à fermer")
            return false
        }
    }
}
