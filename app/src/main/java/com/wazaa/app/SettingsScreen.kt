package com.wazaa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Couleur d'avertissement (délais trop courts, etc.). */
private val ColWarn = Color(0xFFFFB74D)

/**
 * Réglages : comportement Waze et compte Tesla (connexion à la demande).
 * Ouvert par l'**engrenage** en haut à droite.
 */
@Composable
fun SettingsScreen(
    settings: WazeSettings,
    teslaStatus: String,
    overlayGranted: Boolean,
    notificationAccessGranted: Boolean,
    onChange: (WazeSettings) -> Unit,
    onConnect: () -> Unit,
    onPasteToken: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onClose: () -> Unit
) {
    var pasteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Réglages", color = ColTeal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Waze est lancé dès qu'une destination apparaît sur le compte Tesla, et arrêté quand " +
                    "elle disparaît, quand le véhicule est garé ou hors ligne.",
            color = ColWazeText,
            fontSize = 12.sp
        )

        Text(
            if (overlayGranted) "Autorisation « Superposer » : accordée"
            else "Autorisation « Superposer » : manquante — sans elle, Waze ne peut pas être " +
                    "lancé écran éteint.",
            color = if (overlayGranted) ColTeal else ColWazeText,
            fontSize = 12.sp
        )
        if (!overlayGranted) {
            TextButton(onClick = onRequestOverlay) {
                Text("Autoriser « Superposer »", color = ColTeal, fontSize = 14.sp)
            }
        }
        Text(
            if (notificationAccessGranted) "Accès aux notifications : accordé"
            else "Accès aux notifications : manquant — sans lui, Waze ne peut pas être fermé " +
                    "quand la destination disparaît.",
            color = if (notificationAccessGranted) ColTeal else ColWazeText,
            fontSize = 12.sp
        )
        if (!notificationAccessGranted) {
            TextButton(onClick = onRequestNotificationAccess) {
                Text("Autoriser l'accès aux notifications", color = ColTeal, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        SwitchRow(
            label = "Éteindre l'écran après le lancement",
            checked = settings.screenOff,
            onCheckedChange = { onChange(settings.copy(screenOff = it)) }
        )

        Spacer(Modifier.height(8.dp))

        SwitchRow(
            label = "Relancer Waze toutes les 15 s s'il n'est plus actif",
            checked = settings.wazeWatchdog,
            onCheckedChange = { onChange(settings.copy(wazeWatchdog = it)) }
        )
        Text(
            "Actif uniquement lorsqu'une destination Tesla est en cours. Waze ne permet pas " +
                    "de lire sa destination interne : le contrôle vérifie que sa notification " +
                    "de navigation est toujours présente, et renvoie la destination Tesla sinon.",
            color = ColWazeText,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(16.dp))

        Text("Délais", color = ColTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Intervalles de lecture de l'API Tesla. Valeurs : 10–120 s (en ligne), " +
                    "10–60 s (endormi/erreur). Modifications appliquées au cycle suivant.",
            color = ColWazeText,
            fontSize = 12.sp
        )
        Text(
            "⚠️ Réduire fortement ces délais multiplie les appels à l'API Tesla " +
                    "(risque de limitation) et peut provoquer des arrêts ou relances " +
                    "intempestifs. Conservez les valeurs par défaut sauf besoin précis.",
            color = ColWarn,
            fontSize = 12.sp
        )
        DelayRow(
            label = "Véhicule en ligne",
            value = settings.pollOnlineSec,
            max = WazeSettings.MAX_POLL_ONLINE_SEC,
            onChange = { onChange(settings.copy(pollOnlineSec = it)) }
        )
        DelayRow(
            label = "Véhicule endormi / hors ligne",
            value = settings.pollAsleepSec,
            max = WazeSettings.MAX_POLL_ASLEEP_SEC,
            onChange = { onChange(settings.copy(pollAsleepSec = it)) }
        )
        DelayRow(
            label = "Après une erreur",
            value = settings.pollErrorSec,
            max = WazeSettings.MAX_POLL_ERROR_SEC,
            onChange = { onChange(settings.copy(pollErrorSec = it)) }
        )
        Text(
            "Le délai de confirmation d'arrêt suit l'intervalle « en ligne ».",
            color = ColWazeText,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(16.dp))

        Text("Compte Tesla", color = ColTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(teslaStatus, color = ColWhite, fontSize = 13.sp)
        Text(
            "Le jeton se demande une seule fois : il est chiffré sur l'appareil (Android Keystore) " +
                    "et renouvelé automatiquement. Reconnexion nécessaire seulement s'il est révoqué.",
            color = ColWazeText,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onConnect) { Text("Se connecter", color = ColTeal, fontSize = 14.sp) }
            TextButton(onClick = { pasteDialog = true }) {
                Text("Coller un token", color = ColTeal, fontSize = 14.sp)
            }
        }
        TextButton(onClick = onDisconnect) {
            Text("Se déconnecter", color = ColWazeText, fontSize = 14.sp)
        }

        // La section « Avancé » est volontairement masquée. Les champs restent dans
        // WazeSettings et UrlField, mais ne sont plus accessibles depuis l'interface.

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose) { Text("Fermer", color = ColTeal, fontSize = 15.sp) }
        }
    }

    if (pasteDialog) {
        PasteTokenDialog(
            onDismiss = { pasteDialog = false },
            onConfirm = { access, refresh ->
                pasteDialog = false
                onPasteToken(access, refresh)
            }
        )
    }
}

/** Reprend la saisie de la page de connexion de TeslaMate (`token` + `refresh_token`). */
@Composable
private fun PasteTokenDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var access by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColDialog,
        titleContentColor = ColTeal,
        textContentColor = ColWazeText,
        title = { Text("Coller un jeton Tesla", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Jetons obtenus par la procédure TeslaMate (tesla_auth / fournisseur de token).",
                    color = ColWazeText,
                    fontSize = 12.sp
                )
                TokenField("access_token", access) { access = it }
                TokenField("refresh_token", refresh) { refresh = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(access.trim(), refresh.trim()) },
                enabled = access.isNotBlank()
            ) { Text("Enregistrer", color = ColTeal) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = ColWazeText) } }
    )
}

@Composable
private fun TokenField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = ColWhite, fontSize = 12.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ColWhite,
            unfocusedTextColor = ColWhite,
            cursorColor = ColTeal,
            focusedBorderColor = ColTeal,
            unfocusedBorderColor = ColTealDim,
            focusedLabelColor = ColTeal,
            unfocusedLabelColor = ColWazeText
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun UrlField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        textStyle = androidx.compose.ui.text.TextStyle(color = ColWhite, fontSize = 12.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ColWhite,
            unfocusedTextColor = ColWhite,
            cursorColor = ColTeal,
            focusedBorderColor = ColTeal,
            unfocusedBorderColor = ColTealDim,
            focusedLabelColor = ColTeal,
            unfocusedLabelColor = ColWazeText
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DelayRow(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = ColWazeText, fontSize = 12.sp)
            Text("$value s", color = ColTeal, fontSize = 12.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = WazeSettings.MIN_POLL_SEC.toFloat()..max.toFloat(),
            steps = ((max - WazeSettings.MIN_POLL_SEC) / WazeSettings.POLL_STEP_SEC) - 1
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ColWhite, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(0.72f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ColBg,
                checkedTrackColor = ColTeal,
                uncheckedThumbColor = ColWazeText,
                uncheckedTrackColor = ColTealDim
            )
        )
    }
}
