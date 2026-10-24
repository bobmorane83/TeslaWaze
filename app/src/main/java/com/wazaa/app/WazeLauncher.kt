package com.wazaa.app

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.util.Locale

/**
 * Lance Waze avec une destination injectée, via le **deep link officiel** Waze
 * (https://developers.google.com/waze/deeplinks) :
 *
 *     waze://?ll=<lat>,<lon>&navigate=yes&utm_source=wazaa
 *
 * Aucun patch de l'application Waze : l'API de liens profonds est publique et couvre
 * exactement le besoin (§2.2 du PRD). Le calcul d'itinéraire reste fait par Waze.
 *
 * ⚠️ Coordonnées formatées en `Locale.US` : avec une locale française, `%.5f` produirait
 * « 48,85660 » et le lien serait ignoré — même piège que `GeoBox` / Overpass.
 * ⚠️ Android 10+ interdit le démarrage d'activité depuis l'arrière-plan : l'appel doit
 * partir d'une activité visible (au démarrage, le dashboard est affiché).
 */
object WazeLauncher {

    const val PACKAGE = "com.waze"

    /**
     * Activité qui traite les deep links. **Vérifié sur l'émulateur** : avec un intent
     * implicite (`ACTION_VIEW` seul), Waze déjà lancé journalise « deep link data: null » et
     * n'applique pas la destination ; en ciblant explicitement cette activité, l'itinéraire
     * est bien (re)calculé (`Proceed to highlighted route`, 471 mi / 7 h 11 vers Aix).
     */
    private const val DEEP_LINK_ACTIVITY = "com.waze.FreeMapAppActivity"

    /** `true` si Waze est installé (ne lève jamais d'exception). */
    @Suppress("DEPRECATION")
    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * URL `waze://` officielle. `navigation = true` lance le calcul d'itinéraire
     * (`navigate=yes`) ; sinon Waze ne fait que centrer la carte sur le point.
     */
    fun uri(lat: Double, lon: Double, navigation: Boolean = true): Uri = Uri.parse(
        "waze://?ll=%s&navigate=%s&utm_source=wazaa".format(
            Locale.US,
            "%.6f,%.6f".format(Locale.US, lat, lon),
            if (navigation) "yes" else "no"
        )
    )

    /** Ouvre Waze sur la destination. `false` si Waze est absent ou refuse l'intent. */
    fun launch(context: Context, lat: Double, lon: Double, navigation: Boolean = true): Boolean {
        if (!isInstalled(context)) return false
        val uri = uri(lat, lon, navigation)

        // 1) Ciblage explicite : seul moyen fiable quand Waze tourne déjà (voir DEEP_LINK_ACTIVITY).
        val explicit = Intent(Intent.ACTION_VIEW, uri)
            .setComponent(ComponentName(PACKAGE, DEEP_LINK_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(explicit)
            return true
        } catch (e: ActivityNotFoundException) {
            // 2) Repli : intent implicite (autre version de Waze, activité renommée).
        } catch (e: SecurityException) {
            return false
        }

        val implicit = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(implicit)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
