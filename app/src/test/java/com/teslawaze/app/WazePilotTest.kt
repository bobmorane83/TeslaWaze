package com.teslawaze.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la politique de pilotage de Waze sans téléphone ni voiture :
 * lancement quand une destination apparaît, arrêt quand elle disparaît, quand le véhicule se
 * gare après avoir roulé, ou quand il passe hors ligne — avec confirmation anti-rebond.
 */
class WazePilotTest {

    private fun dest(lat: Double, lon: Double, name: String? = null) =
        TeslaApi.Destination(lat, lon, name, null)

    private val aix = dest(43.5297, 5.4474, "Aix")
    private val lyon = dest(45.7640, 4.8357, "Lyon")

    @Test
    fun `destination presente vehicule gare avant depart - lance`() {
        val pilot = WazePilot()
        val action = pilot.next(online = true, destination = aix, shiftState = "P", speedMph = 0.0)
        assertTrue(action is WazePilot.Action.Launch)
        assertEquals("43.52970,5.44740", pilot.launchedKey)
    }

    @Test
    fun `meme destination - ne relance pas`() {
        val pilot = WazePilot()
        pilot.next(true, aix, "D", 40.0)
        assertEquals(WazePilot.Action.Idle::class, pilot.next(true, aix, "D", 40.0)::class)
    }

    @Test
    fun `changement de destination - relance`() {
        val pilot = WazePilot()
        pilot.next(true, aix, "D", 40.0)
        val action = pilot.next(true, lyon, "D", 40.0)
        assertTrue(action is WazePilot.Action.Launch)
        assertEquals("45.76400,4.83570", pilot.launchedKey)
    }

    @Test
    fun `vehicule gare apres avoir roule - arrete`() {
        val pilot = WazePilot()
        pilot.next(true, aix, "D", 40.0)
        pilot.next(true, aix, "P", 0.0) // 1re confirmation : pas encore d'arrêt
        assertEquals(WazePilot.Action.Stop::class, pilot.next(true, aix, "P", 0.0)::class)
        assertNull(pilot.launchedKey)
    }

    @Test
    fun `apres arret a l arrivee - ne relance pas la meme destination`() {
        val pilot = WazePilot()
        pilot.next(true, aix, "D", 40.0)
        pilot.next(true, aix, "P", 0.0)
        pilot.next(true, aix, "P", 0.0) // → Stop
        assertEquals(WazePilot.Action.Idle::class, pilot.next(true, aix, "P", 0.0)::class)
    }

    @Test
    fun `nouveau depart apres arret - relance`() {
        val pilot = WazePilot()
        pilot.next(true, aix, "D", 40.0)
        pilot.next(true, aix, "P", 0.0)
        pilot.next(true, aix, "P", 0.0) // → Stop
        val action = pilot.next(true, aix, "D", 20.0) // la voiture repart
        assertTrue(action is WazePilot.Action.Launch)
    }

    @Test
    fun `destination retiree - arret confirme en deux lectures`() {
        val pilot = WazePilot()
        pilot.next(true, aix, "D", 40.0)
        assertEquals(WazePilot.Action.Idle::class, pilot.next(true, null, "D", 40.0)::class)
        assertEquals(WazePilot.Action.Stop::class, pilot.next(true, null, "D", 40.0)::class)
    }

    @Test
    fun `recalcul de route - une seule lecture nulle ne stoppe pas`() {
        val pilot = WazePilot()
        pilot.next(true, aix, "D", 40.0)
        pilot.next(true, null, "D", 40.0) // trou bref (recalcul)
        assertEquals(WazePilot.Action.Idle::class, pilot.next(true, aix, "D", 40.0)::class)
        assertTrue(pilot.wazeLaunched)
    }

    @Test
    fun `vehicule hors ligne - arret apres confirmation`() {
        val pilot = WazePilot()
        pilot.next(true, aix, "D", 40.0)
        assertEquals(WazePilot.Action.Idle::class, pilot.next(false, null, null, null)::class)
        assertEquals(WazePilot.Action.Stop::class, pilot.next(false, null, null, null)::class)
    }

    @Test
    fun `aucune destination et waze non lance - rien a faire`() {
        val pilot = WazePilot()
        assertEquals(WazePilot.Action.None::class, pilot.next(true, null, "P", 0.0)::class)
        assertNull(pilot.launchedKey)
    }

    @Test
    fun `echec de lancement - la destination sera retentee`() {
        val pilot = WazePilot()
        pilot.next(true, aix, "D", 40.0)
        pilot.launchFailed()
        assertNull(pilot.launchedKey)
        assertTrue(pilot.next(true, aix, "D", 40.0) is WazePilot.Action.Launch)
    }
}
