package com.wazaa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie le filtrage des destinations factices renvoyées lorsque la navigation est inactive.
 */
class TeslaApiTest {

    @Test
    fun `destination Tesla sans ETA et sur place - factice`() {
        assertTrue(isPlaceholderDestination("Tesla", 43.0, 5.0, null, null, 43.0, 5.0))
    }

    @Test
    fun `destination Tesla avec ETA mais sur place - factice`() {
        assertTrue(isPlaceholderDestination("Tesla", 43.0, 5.0, 12.0, 3.0, 43.0, 5.0))
    }

    @Test
    fun `destination Tesla avec ETA ailleurs - reelle`() {
        assertFalse(isPlaceholderDestination("Tesla", 43.1, 5.1, 12.0, 3.0, 43.0, 5.0))
    }

    @Test
    fun `coordonnees nulles - factice`() {
        assertTrue(isPlaceholderDestination(null, 0.0, 0.0, null, null, 43.0, 5.0))
    }

    @Test
    fun `coordonnees egales au vehicule sans ETA - factice`() {
        assertTrue(isPlaceholderDestination(null, 43.0, 5.0, null, null, 43.0, 5.0))
    }

    @Test
    fun `destination normale - reelle`() {
        assertFalse(isPlaceholderDestination("Aix", 43.5297, 5.4474, 20.0, 10.0, 43.0, 5.0))
    }

    @Test
    fun `destination sans nom mais avec ETA - reelle`() {
        assertFalse(isPlaceholderDestination(null, 43.1, 5.1, 20.0, 10.0, 43.0, 5.0))
    }

    @Test
    fun `destination sans nom et sans ETA - factice meme sans position vehicule`() {
        // Valeurs observées après l'arrêt de la navigation (2026-10-20).
        assertTrue(isPlaceholderDestination(null, 43.614675, 7.07595, null, null, null, null))
    }

    @Test
    fun `coordonnees non finies - factice`() {
        assertTrue(isPlaceholderDestination("Aix", Double.NaN, 5.0, null, null, 43.0, 5.0))
    }
}
