package com.wazaa.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Redémarre la surveillance après un redémarrage du téléphone (« l'app reste en permanence »),
 * sauf si l'utilisateur a quitté explicitement.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = SettingsStore(context)
        val settings = store.load()
        if (!store.serviceEnabled()) {
            Log.d("Wazaa", "boot: service désactivé (quitté)")
            return
        }
        if (TeslaSession(context) { settings }.token() == null) {
            Log.d("Wazaa", "boot: aucun jeton")
            return
        }
        Log.d("Wazaa", "boot: démarrage du service")
        DestinationService.start(context)
    }
}
