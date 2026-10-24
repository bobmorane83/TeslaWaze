package com.wazaa.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build

/**
 * Notification **du service de premier plan** : c'est elle qui affiche la boussole dans la
 * barre système tant que la surveillance tourne (§4.8 du PRD).
 *
 * Elle est silencieuse (`IMPORTANCE_LOW` : pas de son, pas de heads-up), non balayable
 * (service au premier plan) et propose deux actions : ouvrir l'app, quitter.
 */
object StatusNotification {

    private const val CHANNEL_ID = "wazaa_status"
    const val ID = 42

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = manager(context) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Surveillance de la destination",
                NotificationManager.IMPORTANCE_LOW // silencieux, pas de heads-up
            ).apply {
                description = "Boussole tant que Wazaa surveille la destination du véhicule"
                setShowBadge(false)
            }
        )
    }

    fun build(context: Context, text: String): Notification {
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val quit = PendingIntent.getService(
            context,
            2,
            Intent(context, DestinationService::class.java).setAction(DestinationService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wazaa)
            .setContentTitle("Wazaa")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_wazaa), "Ouvrir", open
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_wazaa), "Quitter", quit
                ).build()
            )
            .build()
    }

    /** Met à jour le texte sans recréer le service. */
    fun update(context: Context, text: String) {
        manager(context)?.notify(ID, build(context, text))
    }

    fun cancel(context: Context) {
        manager(context)?.cancel(ID)
    }
}
