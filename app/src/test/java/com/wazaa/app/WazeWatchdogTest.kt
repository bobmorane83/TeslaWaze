package com.wazaa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie la décision du watchdog sans Android : quand relancer Waze, et quand ne rien faire. */
class WazeWatchdogTest {

    private fun should(
        enabled: Boolean = true,
        shouldBeRunning: Boolean = true,
        running: Boolean? = false,
        sinceLaunchMs: Long = WazeWatchdog.GRACE_MS
    ) = WazeWatchdog.shouldRelaunch(enabled, shouldBeRunning, running, sinceLaunchMs)

    @Test
    fun `option desactivee - ne relance pas`() {
        assertFalse(should(enabled = false))
    }

    @Test
    fun `aucune destination Tesla - ne relance pas`() {
        assertFalse(should(shouldBeRunning = false))
    }

    @Test
    fun `waze navigue encore - ne relance pas`() {
        assertFalse(should(running = true))
    }

    @Test
    fun `acces notifications absent - ne relance pas`() {
        assertFalse(should(running = null))
    }

    @Test
    fun `waze inactif mais delai de grace - ne relance pas`() {
        assertFalse(should(sinceLaunchMs = WazeWatchdog.GRACE_MS - 1))
    }

    @Test
    fun `waze inactif apres le delai de grace - relance`() {
        assertTrue(should(sinceLaunchMs = WazeWatchdog.GRACE_MS))
    }

    @Test
    fun `periode de controle de 15 secondes`() {
        assertTrue(WazeWatchdog.INTERVAL_MS == 15_000L)
    }
}
