package com.wazaa.app

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * État partagé entre le service permanent et l'écran.
 *
 * Le service tourne indépendamment de l'activité : c'est lui qui publie l'état courant,
 * y compris quand l'écran est fermé (l'utilisateur a appuyé sur « retour »).
 */
object WatcherState {

    /** Texte affiché dans l'app **et** dans la notification de barre système. */
    val text = MutableStateFlow("Démarrage…")

    /** `true` tant que le service de surveillance tourne. */
    val running = MutableStateFlow(false)
}
