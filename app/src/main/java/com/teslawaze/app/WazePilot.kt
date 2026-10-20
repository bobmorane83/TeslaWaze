package com.teslawaze.app

import java.util.Locale

/**
 * Machine à états du pilotage de Waze — **sans aucune dépendance Android**, donc testable.
 *
 * Règles (§4.7 du PRD) :
 *  - une destination apparaît et le véhicule n'est pas garé après avoir roulé ⇒ **lancer Waze** ;
 *  - la destination change ⇒ **relancer Waze** sur la nouvelle destination ;
 *  - destination retirée, véhicule garé après avoir roulé, ou véhicule hors ligne ⇒ **arrêter Waze** ;
 *  - toute décision d'arrêt doit être confirmée par [stopConfirmations] lectures concordantes :
 *    Waze rend `active_route_*` nul un instant pendant un recalcul de route, et un véhicule peut
 *    passer `offline` quelques secondes ;
 *  - une destination déjà arrêtée (garé/hors ligne) n'est pas relancée tant que le véhicule n'a
 *    pas roulé de nouveau ⇒ pas de va-et-vient lancement/arrêt.
 */
class WazePilot(private val stopConfirmations: Int = 2) {

    sealed interface Action {
        /** Rien à faire. */
        data object None : Action

        /** Rien à faire, mais l'état mérite d'être affiché. */
        data class Idle(val text: String) : Action

        /** Lancer (ou relancer) Waze sur cette destination. */
        data class Launch(val destination: TeslaApi.Destination) : Action

        /** Arrêter Waze (destination retirée, véhicule garé ou hors ligne). */
        data class Stop(val reason: String) : Action
    }

    /** Destination actuellement confiée à Waze (`null` = Waze non lancé par nous). */
    var launchedKey: String? = null
        private set

    /** Destination déjà arrêtée : pas de relance tant que le véhicule n'a pas roulé. */
    private var stoppedKey: String? = null
    private var sawDriving = false
    private var stopStreak = 0

    val wazeLaunched: Boolean get() = launchedKey != null

    /**
     * @param online      véhicule `online` (les données sont alors valides) ;
     * @param destination destination de navigation active, `null` si aucune ;
     * @param shiftState  `"P"` garé, `"D"`/`"R"`/`"N"` en mouvement, `null` inconnu ;
     * @param speedMph    vitesse instantanée (0 au repos).
     */
    fun next(
        online: Boolean,
        destination: TeslaApi.Destination?,
        shiftState: String?,
        speedMph: Double?
    ): Action {
        if (!online) return confirmStop("véhicule hors ligne")

        // Un véhicule qui roule réarme l'arrêt et autorise à nouveau une lancement.
        val moving = (shiftState != null && shiftState != "P") || (speedMph ?: 0.0) > 0.5
        if (moving) {
            sawDriving = true
            stoppedKey = null
        }

        if (destination == null) {
            // À l'arrêt, une destination nulle est définitive : inutile d'attendre une
            // seconde lecture. En roulant, elle peut être un trou de recalcul.
            return confirmStop("destination retirée", immediate = !moving)
        }

        // Garé **après** avoir roulé : la navigation est terminée.
        if (shiftState == "P" && sawDriving) {
            return confirmStop("véhicule garé", immediate = true)
        }

        stopStreak = 0
        val key = keyOf(destination)
        return when (key) {
            launchedKey -> Action.Idle(
                "Navigation vers ${destination.name ?: "destination Tesla"}"
            )
            stoppedKey -> Action.Idle("Véhicule arrêté — en attente d'un nouveau départ")
            else -> {
                launchedKey = key
                Action.Launch(destination)
            }
        }
    }

    /** À appeler si le lancement a échoué (Waze absent, intent refusé). */
    fun launchFailed() {
        launchedKey = null
    }

    private fun confirmStop(reason: String, immediate: Boolean = false): Action {
        if (launchedKey == null) {
            // Rien à arrêter : l'état affiché reste celui du service (véhicule endormi, pas de
            // destination…).
            stopStreak = 0
            return Action.None
        }
        if (!immediate) {
            stopStreak++
            if (stopStreak < stopConfirmations) return Action.Idle("Arrêt en cours — $reason")
        }
        stopStreak = 0
        stoppedKey = launchedKey
        launchedKey = null
        sawDriving = false
        return Action.Stop(reason)
    }

    companion object {
        /** Clé stable d'une destination (arrondi à ~1 m). */
        fun keyOf(dest: TeslaApi.Destination): String =
            "%.5f,%.5f".format(Locale.US, dest.lat, dest.lon)
    }
}
