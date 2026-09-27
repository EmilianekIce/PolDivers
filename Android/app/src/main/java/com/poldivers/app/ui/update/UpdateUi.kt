package com.poldivers.app.ui.update

import com.poldivers.app.core.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.core.update.UpdateManager
import com.poldivers.app.core.update.UpdateManager.State
import kotlinx.coroutines.launch

/**
 * One tap does everything: download the APK from GitHub Releases and open the system installer.
 * The first time, Android asks to allow installing apps from PolDivers -- we send the user to
 * that switch and they tap tr("Aktualizuj", "Update") again.
 */
private fun startUpdate(
    updates: UpdateManager,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    state: State,
) {
    when (state) {
        is State.Available -> {
            if (!updates.canInstall()) {
                updates.openInstallPermissionSettings(context)
            } else {
                scope.launch { updates.download(state.release) }
            }
        }
        is State.ReadyToInstall -> {
            if (!updates.canInstall()) updates.openInstallPermissionSettings(context) else updates.install(context, state.file)
        }
        else -> scope.launch { updates.check() }
    }
}

@Composable
fun UpdateBanner(updates: UpdateManager) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val state by updates.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { updates.check() }
    // Download finished -> go straight to the installer.
    LaunchedEffect(state) {
        (state as? State.ReadyToInstall)?.let { if (updates.canInstall()) updates.install(context, it.file) }
    }

    // Once per app start: a dialog, so a new version is not missed; the banner stays below.
    var dialogDismissed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val available = state as? State.Available
    if (available != null && !dialogDismissed) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialogDismissed = true },
            title = { Text(tr("Dostępna nowa wersja", "New version available")) },
            text = {
                Text(
                    tr("Jest nowsza wersja PolDivers (${available.release.tag}). Zaktualizować teraz? Pobierze się z GitHuba i otworzy instalator.", "A newer PolDivers version is out (${available.release.tag}). Update now? It downloads from GitHub and opens the installer."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = {
                    haptics.tap()
                    dialogDismissed = true
                    startUpdate(updates, context, scope, available)
                }) { Text(tr("Aktualizuj", "Update")) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    haptics.tap()
                    dialogDismissed = true
                }) { Text(tr("Później", "Later")) }
            },
        )
    }

    val visible = state is State.Available || state is State.Downloading || state is State.ReadyToInstall
    AnimatedVisibility(visible = visible) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (val s = state) {
                        is State.Downloading -> tr("Pobieranie aktualizacji… ${(s.progress * 100).toInt()}%", "Downloading update… ${(s.progress * 100).toInt()}%")
                        is State.ReadyToInstall -> tr("Aktualizacja pobrana", "Update downloaded")
                        is State.Available -> tr("Dostępna nowa wersja PolDivers", "New PolDivers version available")
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (state !is State.Downloading) {
                    Button(
                        onClick = {
                            haptics.tap()
                            startUpdate(updates, context, scope, state)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text(if (state is State.ReadyToInstall) tr("Zainstaluj", "Install") else tr("Aktualizuj", "Update")) }
                }
            }
            (state as? State.Downloading)?.let { s ->
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                )
            }
            if (!updates.canInstall() && state is State.Available) {
                Text(
                    tr("Za pierwszym razem Android poprosi o zgodę na instalowanie aplikacji z PolDivers — włącz ją i wróć.", "The first time, Android asks for permission to install apps from PolDivers — allow it and come back."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
fun UpdateSettingsSection(updates: UpdateManager) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val state by updates.state.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Aktualizacje", "Updates"), style = MaterialTheme.typography.labelLarge)
        Text(
            tr("Zainstalowana wersja: ${updates.currentVersion}", "Installed version: ${updates.currentVersion}"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when (val s = state) {
                State.Idle -> tr("Nowe wersje pobierane są z GitHuba (EmilianekIce/PolDivers).", "New versions are downloaded from GitHub (EmilianekIce/PolDivers).")
                State.Checking -> tr("Sprawdzanie…", "Checking…")
                State.UpToDate -> tr("Masz najnowszą wersję.", "You have the latest version.")
                is State.Available -> tr("Dostępna: ${s.release.name ?: s.release.tag}", "Available: ${s.release.name ?: s.release.tag}")
                is State.Downloading -> tr("Pobieranie… ${(s.progress * 100).toInt()}%", "Downloading… ${(s.progress * 100).toInt()}%")
                is State.ReadyToInstall -> tr("Pobrano ${s.release.name ?: s.release.tag} — gotowe do instalacji.", "Downloaded ${s.release.name ?: s.release.tag} — ready to install.")
                is State.Failed -> tr("Błąd: ${s.message}", "Error: ${s.message}")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        (state as? State.Available)?.release?.body?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                notes.take(400),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = {
                haptics.tap()
                startUpdate(updates, context, scope, state)
            },
            enabled = state !is State.Checking && state !is State.Downloading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (state) {
                    is State.Available -> tr("Aktualizuj", "Update")
                    is State.ReadyToInstall -> tr("Zainstaluj", "Install")
                    else -> tr("Sprawdź aktualizacje", "Check for updates")
                },
            )
        }
    }
}
