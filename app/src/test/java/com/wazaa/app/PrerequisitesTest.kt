package com.wazaa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie le calcul des prérequis sans Android. */
class PrerequisitesTest {

    private fun evaluate(
        teslaConnected: Boolean = true,
        overlayGranted: Boolean = true,
        notificationsGranted: Boolean = true,
        notificationAccessGranted: Boolean = true,
        wazeInstalled: Boolean = true,
        serviceRunning: Boolean = true,
        screenOffEnabled: Boolean = false,
        screenOffAdminGranted: Boolean = false
    ) = evaluatePrerequisites(
        teslaConnected = teslaConnected,
        overlayGranted = overlayGranted,
        notificationsGranted = notificationsGranted,
        notificationAccessGranted = notificationAccessGranted,
        wazeInstalled = wazeInstalled,
        serviceRunning = serviceRunning,
        screenOffEnabled = screenOffEnabled,
        screenOffAdminGranted = screenOffAdminGranted
    )

    @Test
    fun `tout est accorde - indicateur vert`() {
        val list = evaluate()
        assertTrue(list.allSatisfied())
        assertEquals(0, list.missingCount())
    }

    @Test
    fun `une autorisation manquante - indicateur orange`() {
        val list = evaluate(overlayGranted = false)
        assertFalse(list.allSatisfied())
        assertEquals(1, list.missingCount())
    }

    @Test
    fun `option extinction desactivee - ne bloque pas`() {
        val list = evaluate(screenOffEnabled = false)
        assertTrue(list.allSatisfied())
        assertFalse(list.first { it.label == "Extinction d'écran" }.required)
    }

    @Test
    fun `extinction activee sans admin - bloquant`() {
        val list = evaluate(screenOffEnabled = true, screenOffAdminGranted = false)
        assertFalse(list.allSatisfied())
        assertEquals(1, list.missingCount())
    }
}
