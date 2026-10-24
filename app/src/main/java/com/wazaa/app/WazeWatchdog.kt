package com.wazaa.app

/**
 * Décision du watchdog Waze, sans dépendance Android pour rester testable.
 *
 * Waze n'expose pas sa destination interne. Le seul signal fiable dont dispose l'app est sa
 * notification de navigation (`WazeNotificationListener.isWazeRunning()`) : elle apparaît
 * quand Waze navigue et disparaît quand il est fermé. Le watchdog ne peut donc pas comparer
 * l'itinéraire Waze à celui de la Tesla ; il vérifie que **la navigation Waze est toujours
 * active** et renvoie la dernière destination Tesla si elle ne l'est plus.
 */
object WazeWatchdog {

    /** Période de contrôle demandée. */
    const val INTERVAL_MS = 15_000L

    /**
     * Après un lancement, Waze met quelques secondes à publier sa notification. On ne le
     * relance pas pendant ce délai pour éviter une boucle de deep links.
     */
    const val GRACE_MS = 20_000L

    /**
     * @param enabled       option activée dans les réglages ;
     * @param shouldBeRunning la destination a été confiée à Waze et n'a pas été arrêtée ;
     * @param wazeRunning   `true` si Waze publie sa notification, `false` s'il ne tourne plus,
     *                      `null` si l'accès aux notifications n'est pas accordé ;
     * @param sinceLaunchMs temps écoulé depuis le dernier deep link.
     */
    fun shouldRelaunch(
        enabled: Boolean,
        shouldBeRunning: Boolean,
        wazeRunning: Boolean?,
        sinceLaunchMs: Long
    ): Boolean = enabled &&
            shouldBeRunning &&
            wazeRunning == false &&
            sinceLaunchMs >= GRACE_MS
}
