package com.wazaa.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.app.ActivityManager

/**
 * Arrête Waze quand la navigation n'a plus lieu d'être (destination retirée, véhicule garé
 * ou hors ligne).
 *
 * Waze n'expose **aucun** moyen officiel d'arrêter une navigation en cours, et une app
 * ordinaire ne peut pas `force-stop` une autre app. Deux leviers restent, sans root :
 *
 *  1. `killBackgroundProcesses("com.waze")` — permission normale `KILL_BACKGROUND_PROCESSES`,
 *     mais inopérant si Waze est au premier plan (son adj est protégé) ;
 *  2. **prendre le premier plan** avec une activité à nous ([StopWazeActivity]) : Waze passe
 *     en arrière-plan, puis (1) devient effectif.
 *
 * L'étape 2 exige l'autorisation « Superposer » (démarrage d'activité depuis l'arrière-plan).
 */
object WazeStopper {

    private const val TAG = "Wazaa"

    /** Délai avant de vérifier si Waze a réellement disparu après « Éteindre ». */
    private const val FOLLOW_UP_MS = 1_500L

    /** Tue le processus Waze s'il est déjà en arrière-plan. `false` si le système refuse. */
    fun killNow(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        return try {
            am.killBackgroundProcesses(WazeLauncher.PACKAGE)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "waze: killBackgroundProcesses refusé: ${e.message}")
            false
        }
    }

    /**
     * Arrête Waze. `true` si la demande est partie.
     *
     * 1. **Action « Éteindre »** de la notification de Waze, si elle est publiée (accès aux
     *    notifications) — seule voie qui fonctionne quand Waze navigue : il tourne en service
     *    de premier plan, donc `killBackgroundProcesses` ne peut rien contre lui ;
     * 2. sinon, on prend le premier plan ([StopWazeActivity]) : Waze passe en arrière-plan et
     *    publie sa notification, que l'on peut alors éteindre ;
     * 3. dernier repli : `killBackgroundProcesses` (suffit si Waze n'a pas de FGS).
     */
    fun stop(context: Context): Boolean {
        if (!WazeLauncher.isInstalled(context)) return true

        if (WazeNotificationListener.closeWaze()) {
            Log.d(TAG, "waze: action « Éteindre » envoyée")
            // Si Waze met du temps à se fermer, on force la fermeture peu après.
            killNow(context)
            Handler(Looper.getMainLooper()).postDelayed({
                val running = WazeNotificationListener.isWazeRunning()
                Log.d(TAG, "waze: état après « Éteindre » = $running")
                if (running == true) {
                    Log.d(TAG, "waze: encore actif — fermeture forcée")
                    forceForeground(context)
                }
            }, FOLLOW_UP_MS)
            return true
        }

        if (!Permissions.canDrawOverlays(context)) {
            Log.w(TAG, "waze: arrêt incertain — autorisation « Superposer » absente")
            return killNow(context)
        }
        return try {
            context.startActivity(
                Intent(context, StopWazeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "waze: lancement StopWazeActivity impossible: ${e.message}")
            killNow(context)
            false
        }
    }

    /** Prend brièvement le premier plan pour rendre `killBackgroundProcesses` effectif. */
    private fun forceForeground(context: Context) {
        if (!Permissions.canDrawOverlays(context)) {
            killNow(context)
            return
        }
        try {
            context.startActivity(
                Intent(context, StopWazeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            )
        } catch (e: Exception) {
            Log.w(TAG, "waze: lancement StopWazeActivity impossible: ${e.message}")
            killNow(context)
        }
    }
}
