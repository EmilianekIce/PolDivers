package com.poldivers.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poldivers.app.R
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.core.prefs.ApiLanguage
import com.poldivers.app.ui.update.UpdateSettingsSection

@Composable
fun SettingsDialog(container: AppContainer, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    val language by container.preferences.language.collectAsStateWithLifecycle()
    val hapticsEnabled by container.preferences.hapticsEnabled.collectAsStateWithLifecycle()
    var languageMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                haptics.tap()
                onDismiss()
            }) { Text("Gotowe") }
        },
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.settings_language_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = {
                            haptics.tap()
                            languageMenuExpanded = true
                        }) {
                            Text("${language.label} ▾")
                        }
                        DropdownMenu(expanded = languageMenuExpanded, onDismissRequest = { languageMenuExpanded = false }) {
                            ApiLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(if (lang == language) "✓ ${lang.label}" else lang.label) },
                                    onClick = {
                                        haptics.tap()
                                        container.preferences.setLanguage(lang)
                                        languageMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_haptics), style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = {
                            container.preferences.setHapticsEnabled(it)
                            if (it) haptics.confirm()
                        },
                    )
                }

                UpdateSettingsSection(container.updates)

                Text(
                    stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
