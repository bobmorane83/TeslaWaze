package com.wazaa.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Éteint l'écran après le lancement de Waze (économie de batterie, §2.2 du PRD).
 *
 * Sur un téléphone **non rooté**, `lockNow()` est la seule extinction d'écran disponible :
 * `PowerManager.goToSleep()` exige la permission système `DEVICE_POWER`. L'administrateur
 * de périphérique est donc réduit au strict minimum (politique `force-lock`).
 *
 * Waze n'est pas interrompu : il tourne en service de premier plan et continue ses
 * annonces vocales (mesuré : 3,5–7,6 % de CPU écran éteint contre 86–112 % écran allumé).
 */
class ScreenOffAdmin : DeviceAdminReceiver()

object ScreenOff {

    private fun component(context: Context) = ComponentName(context, ScreenOffAdmin::class.java)

    private fun dpm(context: Context): DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    fun isActive(context: Context): Boolean =
        dpm(context)?.isAdminActive(component(context)) == true

    /** Intent système d'activation (affiché une seule fois, sur action de l'utilisateur). */
    fun requestIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Wazaa éteint l'écran après le lancement de Waze (économie de batterie). " +
                        "Waze continue d'annoncer les alertes."
            )
        }

    /** Éteint (verrouille) l'écran. `false` si l'administrateur n'est pas activé. */
    fun turnOff(context: Context): Boolean {
        val d = dpm(context) ?: return false
        val cn = component(context)
        if (!d.isAdminActive(cn)) return false
        return try {
            d.lockNow()
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
