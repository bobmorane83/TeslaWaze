package com.wazaa.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Autorisations **spéciales** nécessaires au pilotage de Waze depuis l'arrière-plan.
 *
 * Depuis Android 10, une app en arrière-plan ne peut pas démarrer d'activité — et lancer
 * Waze *est* un démarrage d'activité. La seule exemption accessible sans root est
 * « Afficher par-dessus les autres applications » (`SYSTEM_ALERT_WINDOW`), que l'utilisateur
 * accorde une fois dans les réglages système.
 */
object Permissions {

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun overlayIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    /**
     * « Accès aux notifications » : indispensable pour déclencher l'action « Éteindre » de
     * Waze (seul moyen de le fermer — il tourne en service de premier plan).
     *
     * Lu dans le réglage `enabled_notification_listeners` (même approche que
     * `NotificationManagerCompat.getEnabledListenerPackages`), qui n'existe pas côté framework.
     */
    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        if (enabled.isEmpty()) return false
        return enabled.split(':').any { flat ->
            ComponentName.unflattenFromString(flat)?.packageName == context.packageName
        }
    }

    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
