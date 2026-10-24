package com.wazaa.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Activité **invisible et minuscule** (fond noir, aucune vue) qui existe uniquement pour
 * prendre le premier plan une fraction de seconde : c'est ce qui fait passer Waze en
 * arrière-plan et rend `killBackgroundProcesses("com.waze")` effectif.
 *
 * Elle se termine aussitôt (`onResume` → 500 ms → kill → `finish()`), l'utilisateur ne voit
 * qu'un écran noir très bref — et rien du tout si le téléphone est écran éteint.
 */
class StopWazeActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
    }

    override fun onResume() {
        super.onResume()
        // Waze vient de passer en arrière-plan : il publie alors sa notification, dont
        // l'action « Éteindre » le ferme proprement (`killBackgroundProcesses` est sans effet
        // sur un service de premier plan). On retente tant que la notification n'est pas là.
        schedule()
    }

    private fun schedule() {
        handler.postDelayed({
            val closed = WazeNotificationListener.closeWaze()
            attempts++
            if (closed || attempts >= MAX_ATTEMPTS) {
                val killed = closed || WazeStopper.killNow(this)
                Log.d(TAG, "waze: arrêt (premier plan) closed=$closed kill=$killed essais=$attempts")
                finish()
            } else {
                schedule()
            }
        }, ATTEMPT_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Wazaa"
        const val ATTEMPT_DELAY_MS = 500L
        const val MAX_ATTEMPTS = 8 // ≈ 4 s
    }
}
