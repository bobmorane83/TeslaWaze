package com.wazaa.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Wazaa — écran de contrôle.
 *
 * La surveillance vit dans `DestinationService` (service de premier plan) : cet écran ne fait
 * que l'afficher et la configurer. Un appui sur « retour » **n'arrête donc rien** ; seul
 * « Quitter » (ici ou dans la notification) arrête le service et retire la notification.
 */

internal val ColBg = Color(0xFF050A0F)
internal val ColTeal = Color(0xFF00E5C8)
internal val ColTealDim = Color(0xFF005A50)
internal val ColWhite = Color(0xFFFFFFFF)
internal val ColWazeText = Color(0xFFE8F4F2)

/** Orange de la charte, utilisé quand un prérequis manque. */
internal val ColOrange = Color(0xFFFFB74D)

/** Fond des boîtes de dialogue : sans lui, le thème clair de Material rend les textes blancs illisibles. */
internal val ColDialog = Color(0xFF0C1A1F)

private const val TAG = "Wazaa"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ColTeal,
                    onPrimary = ColBg,
                    secondary = ColTealDim,
                    background = ColBg,
                    onBackground = ColWhite,
                    surface = ColDialog,
                    onSurface = ColWhite,
                    surfaceContainerHigh = ColDialog,
                    onSurfaceVariant = ColWazeText
                )
            ) {
                WazeScreen()
            }
        }
    }
}

@Composable
private fun WazeScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    val session = remember(settings.teslaApiBase, settings.teslaAuthBase) {
        TeslaSession(context) { settings }
    }

    val status by WatcherState.text.collectAsState()
    val running by WatcherState.running.collectAsState()
    var teslaStatus by remember { mutableStateOf(session.statusLabel()) }
    var showSettings by remember { mutableStateOf(false) }
    var showPrereqs by remember { mutableStateOf(false) }
    var authDialog by remember { mutableStateOf<String?>(null) }
    var overlayNeeded by remember { mutableStateOf(false) }
    var notifAccessNeeded by remember { mutableStateOf(false) }
    var notifGranted by remember { mutableStateOf(StatusNotification.hasPermission(context)) }
    var prereqRefresh by remember { mutableStateOf(0) }

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        teslaStatus = session.statusLabel()
        prereqRefresh++
        if (res.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "auth: connexion réussie")
        } else {
            authDialog = res.data?.getStringExtra("error") ?: "Connexion Tesla annulée"
        }
    }

    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        prereqRefresh++
        Log.d(TAG, "admin: actif=${ScreenOff.isActive(context)}")
        // On revient au premier plan : (re)garantit que la surveillance tourne.
        DestinationService.start(context)
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayNeeded = !Permissions.canDrawOverlays(context)
        prereqRefresh++
        Log.d(TAG, "overlay: accordée=${!overlayNeeded}")
        DestinationService.start(context)
        if (!overlayNeeded && settings.screenOff && !ScreenOff.isActive(context)) {
            adminLauncher.launch(ScreenOff.requestIntent(context))
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
        prereqRefresh++
        Log.d(TAG, "notif: permission=$granted")
    }

    val notifAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        notifAccessNeeded = !Permissions.hasNotificationAccess(context)
        prereqRefresh++
        Log.d(TAG, "notif-listener: accordé=${!notifAccessNeeded}")
        DestinationService.start(context)
    }

    fun connectTesla() {
        // Avec un client_id Fleet API enregistré, on l'utilise ; sinon on retombe sur le flux
        // historique `ownerapi` + `tesla://auth/callback`, qui fonctionne (tesla_auth 0.13+).
        val fleetClient = settings.teslaClientId.trim()
        val clientId = fleetClient.ifBlank { TeslaAuth.LEGACY_CLIENT_ID }
        val redirect = if (fleetClient.isBlank()) TeslaAuth.LEGACY_REDIRECT_URI
        else settings.teslaRedirectUri.trim()
        val scope = if (clientId == TeslaAuth.LEGACY_CLIENT_ID) TeslaAuth.SCOPE_LEGACY
        else TeslaAuth.SCOPE_FLEET

        loginLauncher.launch(
            Intent(context, TeslaLoginActivity::class.java)
                .putExtra(TeslaLoginActivity.EXTRA_AUTH_BASE, settings.teslaAuthBase)
                .putExtra(TeslaLoginActivity.EXTRA_CLIENT_ID, clientId)
                .putExtra(TeslaLoginActivity.EXTRA_REDIRECT_URI, redirect)
                .putExtra(TeslaLoginActivity.EXTRA_SCOPE, scope)
        )
    }

    fun saveToken(access: String, refresh: String) {
        if (access.isBlank()) return
        val expiry = TeslaAuth.jwtExpiryMs(access) ?: (System.currentTimeMillis() + 3_600_000L)
        session.save(
            TeslaToken(
                access = access,
                refresh = refresh.takeIf { it.isNotBlank() },
                expiresAtMs = expiry,
                // Jeton collé = celui de tesla_auth / TeslaMate ⇒ client public historique.
                clientId = TeslaAuth.LEGACY_CLIENT_ID
            )
        )
        teslaStatus = session.statusLabel()
        prereqRefresh++
        Log.d(TAG, "auth: jeton collé (expire à $expiry)")
        DestinationService.start(context)
    }

    fun disconnectTesla() {
        session.clear()
        teslaStatus = session.statusLabel()
        prereqRefresh++
        DestinationService.stop(context)
    }

    fun quit() {
        store.setServiceEnabled(false)
        DestinationService.requestQuit(context)
        (context as? Activity)?.finish()
    }

    LaunchedEffect(Unit) {
        // Ouvrir l'app = (re)démarrer la surveillance.
        Log.d(TAG, "ui: ouverture — service demandé")
        store.setServiceEnabled(true)
        DestinationService.start(context)

        if (!notifGranted) {
            // Android 13+ : sans elle, aucun logo dans la barre système.
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!Permissions.canDrawOverlays(context)) {
            overlayNeeded = true
        } else if (!Permissions.hasNotificationAccess(context)) {
            notifAccessNeeded = true
        } else if (settings.screenOff && !ScreenOff.isActive(context)) {
            adminLauncher.launch(ScreenOff.requestIntent(context))
        }
        if (session.token() == null) {
            authDialog = "Wazaa a besoin d'un jeton Tesla pour lire la destination du véhicule. " +
                    "La connexion ne se fait qu'une fois."
        }
    }

    val token = session.token()
    val prerequisites = remember(
        prereqRefresh,
        settings,
        running,
        status,
        teslaStatus,
        overlayNeeded,
        notifAccessNeeded,
        notifGranted
    ) {
        evaluatePrerequisites(
            teslaConnected = token != null && (!token.isExpiring() || token.refresh != null),
            vehicleIdKnown = session.vehicleId() != null,
            overlayGranted = Permissions.canDrawOverlays(context),
            notificationsGranted = notifGranted,
            notificationAccessGranted = Permissions.hasNotificationAccess(context),
            wazeInstalled = WazeLauncher.isInstalled(context),
            serviceRunning = running,
            screenOffEnabled = settings.screenOff,
            screenOffAdminGranted = ScreenOff.isActive(context)
        )
    }
    val allSatisfied = prerequisites.allSatisfied()
    val missing = prerequisites.missingCount()

    Box(modifier = Modifier.fillMaxSize().background(ColBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Wazaa", color = ColTeal, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Text(status, color = ColWazeText, fontSize = 14.sp, textAlign = TextAlign.Center)
        }

        // État des prérequis : discret, en haut à gauche. Turquoise = tout est bon.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp, start = 8.dp)
                .size(48.dp)
                .clickable { showPrereqs = true }
                .semantics {
                    contentDescription = if (allSatisfied) "Prérequis remplis"
                    else "$missing prérequis manquant${if (missing > 1) "s" else ""}"
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (allSatisfied) ColTeal else ColOrange, CircleShape)
            )
        }

        // Réglages : accessibles uniquement par l'icône engrenage en haut à droite.
        TextButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Réglages",
                tint = ColTeal,
                modifier = Modifier.size(28.dp)
            )
        }

        // Bouton « Quitter », en bas de l'écran.
        TextButton(
            onClick = { quit() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
        ) {
            Text("Quitter", color = ColTeal, fontSize = 15.sp)
        }
    }

    authDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { authDialog = null },
            containerColor = ColDialog,
            titleContentColor = ColTeal,
            textContentColor = ColWazeText,
            title = { Text("Connexion Tesla requise", fontWeight = FontWeight.SemiBold) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    authDialog = null
                    connectTesla()
                }) { Text("Se connecter", color = ColTeal) }
            },
            dismissButton = {
                TextButton(onClick = { authDialog = null }) { Text("Plus tard", color = ColWazeText) }
            }
        )
    }

    if (overlayNeeded) {
        AlertDialog(
            onDismissRequest = { overlayNeeded = false },
            containerColor = ColDialog,
            titleContentColor = ColTeal,
            textContentColor = ColWazeText,
            title = { Text("Autorisation requise", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Pour lancer et arrêter Waze alors que l'écran est éteint, Wazaa a besoin " +
                            "de l'autorisation « Afficher par-dessus les autres applications » " +
                            "(Android interdit sinon de démarrer une app depuis l'arrière-plan)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    overlayNeeded = false
                    overlayLauncher.launch(Permissions.overlayIntent(context))
                }) { Text("Autoriser", color = ColTeal) }
            },
            dismissButton = {
                TextButton(onClick = { overlayNeeded = false }) {
                    Text("Plus tard", color = ColWazeText)
                }
            }
        )
    }

    if (notifAccessNeeded) {
        AlertDialog(
            onDismissRequest = { notifAccessNeeded = false },
            containerColor = ColDialog,
            titleContentColor = ColTeal,
            textContentColor = ColWazeText,
            title = { Text("Accès aux notifications", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Il sert uniquement à déclencher l'action « Éteindre » de la notification " +
                            "de Waze, seule façon de fermer Waze automatiquement (il tourne en " +
                            "service de premier plan). Aucun contenu n'est lu ni conservé."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    notifAccessNeeded = false
                    notifAccessLauncher.launch(Permissions.notificationAccessIntent())
                }) { Text("Autoriser", color = ColTeal) }
            },
            dismissButton = {
                TextButton(onClick = { notifAccessNeeded = false }) {
                    Text("Plus tard", color = ColWazeText)
                }
            }
        )
    }

    if (showPrereqs) {
        AlertDialog(
            onDismissRequest = { showPrereqs = false },
            containerColor = ColDialog,
            titleContentColor = ColTeal,
            textContentColor = ColWazeText,
            title = { Text("Prérequis", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (allSatisfied) "Toutes les conditions sont remplies."
                        else "$missing prérequis manquant${if (missing > 1) "s" else ""}.",
                        color = if (allSatisfied) ColTeal else ColOrange,
                        fontSize = 12.sp
                    )
                    prerequisites.forEach { prereq ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        when {
                                            !prereq.required -> ColTealDim
                                            prereq.ok -> ColTeal
                                            else -> ColOrange
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    prereq.label,
                                    color = if (prereq.required) ColWhite else ColWazeText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    prereq.detail,
                                    color = when {
                                        !prereq.required -> ColWazeText
                                        prereq.ok -> ColTeal
                                        else -> ColOrange
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (!allSatisfied) {
                        Text(
                            "Les réglages permettent d'accorder les autorisations manquantes.",
                            color = ColWazeText,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrereqs = false }) {
                    Text("Fermer", color = ColTeal)
                }
            }
        )
    }

    if (showSettings) {
        SettingsScreen(
            settings = settings,
            teslaStatus = teslaStatus,
            overlayGranted = Permissions.canDrawOverlays(context),
            notificationAccessGranted = Permissions.hasNotificationAccess(context),
            onChange = { s ->
                settings = s
                store.save(s)
            },
            onConnect = { connectTesla() },
            onPasteToken = { access, refresh -> saveToken(access, refresh) },
            onDisconnect = { disconnectTesla() },
            onRequestOverlay = { overlayLauncher.launch(Permissions.overlayIntent(context)) },
            onRequestNotificationAccess = {
                notifAccessLauncher.launch(Permissions.notificationAccessIntent())
            },
            onClose = { showSettings = false }
        )
    }
}
