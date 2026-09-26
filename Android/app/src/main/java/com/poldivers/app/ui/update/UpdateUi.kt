package com.poldivers.app.ui.update

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
 * that switch and they tap "Aktualizuj" again.
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
                        is State.Downloading -> "Pobieranie aktualizacji… ${(s.progress * 100).toInt()}%"
                        is State.ReadyToInstall -> "Aktualizacja pobrana"
                        is State.Available -> "Dostępna nowa wersja PolDivers"
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
                    ) { Text(if (state is State.ReadyToInstall) "Zainstaluj" else "Aktualizuj") }
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
                    "Za pierwszym razem Android poprosi o zgodę na instalowanie aplikacji z PolDivers — włącz ją i wróć.",
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
        Text("Aktualizacje", style = MaterialTheme.typography.labelLarge)
        Text(
            "Zainstalowana wersja: ${updates.currentVersion}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when (val s = state) {
                State.Idle -> "Nowe wersje pobierane są z GitHuba (EmilianekIce/PolDivers)."
                State.Checking -> "Sprawdzanie…"
                State.UpToDate -> "Masz najnowszą wersję."
                is State.Available -> "Dostępna: ${s.release.name ?: s.release.tag}"
                is State.Downloading -> "Pobieranie… ${(s.progress * 100).toInt()}%"
                is State.ReadyToInstall -> "Pobrano ${s.release.name ?: s.release.tag} — gotowe do instalacji."
                is State.Failed -> "Błąd: ${s.message}"
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
                    is State.Available -> "Aktualizuj"
                    is State.ReadyToInstall -> "Zainstaluj"
                    else -> "Sprawdź aktualizacje"
                },
            )
        }
    }
}
