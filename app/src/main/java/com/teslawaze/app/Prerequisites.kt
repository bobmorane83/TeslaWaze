package com.teslawaze.app

/**
 * Une condition nécessaire au fonctionnement de TeslaWaze.
 *
 * `required = false` sert aux options désactivées : elles restent visibles dans la liste,
 * mais ne rendent pas l'indicateur orange.
 */
internal data class Prerequisite(
    val label: String,
    val detail: String,
    val ok: Boolean,
    val required: Boolean = true
)

/** Construit la liste affichée par l'indicateur d'état (sans dépendance Android, donc testable). */
internal fun evaluatePrerequisites(
    teslaConnected: Boolean,
    overlayGranted: Boolean,
    notificationsGranted: Boolean,
    notificationAccessGranted: Boolean,
    wazeInstalled: Boolean,
    serviceRunning: Boolean,
    screenOffEnabled: Boolean,
    screenOffAdminGranted: Boolean
): List<Prerequisite> = buildList {
    add(
        Prerequisite(
            label = "Compte Tesla",
            detail = if (teslaConnected) "Connecté" else "Non connecté — reconnecte-toi",
            ok = teslaConnected
        )
    )
    add(
        Prerequisite(
            label = "Autorisation « Superposer »",
            detail = if (overlayGranted) "Accordée"
            else "Manquante — nécessaire pour lancer Waze écran éteint",
            ok = overlayGranted
        )
    )
    add(
        Prerequisite(
            label = "Notifications TeslaWaze",
            detail = if (notificationsGranted) "Accordées"
            else "Refusées — la surveillance n'apparaîtra pas dans la barre",
            ok = notificationsGranted
        )
    )
    add(
        Prerequisite(
            label = "Accès aux notifications",
            detail = if (notificationAccessGranted) "Accordé"
            else "Manquant — nécessaire pour fermer Waze",
            ok = notificationAccessGranted
        )
    )
    add(
        Prerequisite(
            label = "Waze",
            detail = if (wazeInstalled) "Installé" else "Non installé",
            ok = wazeInstalled
        )
    )
    add(
        Prerequisite(
            label = "Surveillance",
            detail = if (serviceRunning) "Active" else "Arrêtée",
            ok = serviceRunning
        )
    )
    add(
        if (screenOffEnabled) {
            Prerequisite(
                label = "Extinction d'écran",
                detail = if (screenOffAdminGranted) "Accordée"
                else "Manquante — extinction automatique demandée",
                ok = screenOffAdminGranted
            )
        } else {
            Prerequisite(
                label = "Extinction d'écran",
                detail = "Option désactivée",
                ok = true,
                required = false
            )
        }
    )
}

/** Vrai si toutes les conditions obligatoires sont remplies. */
internal fun List<Prerequisite>.allSatisfied(): Boolean =
    filter { it.required }.all { it.ok }

/** Nombre de conditions obligatoires manquantes. */
internal fun List<Prerequisite>.missingCount(): Int =
    count { it.required && !it.ok }
