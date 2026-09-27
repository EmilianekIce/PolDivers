package com.poldivers.app.ui.settings

import com.poldivers.app.core.i18n.tr
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
import com.poldivers.app.core.prefs.HapticStrength
import androidx.compose.material3.FilterChip
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
            }) { Text(tr("Gotowe", "Done")) }
        },
        title = { Text(tr("Ustawienia", "Settings")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text(tr("Język", "Language"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        tr("Interfejs, nazwy planet, rozkazy, newsy i opisy DSS przełączają się na wybrany język. Otwarte zakładki odświeżą się same.", "The interface, planet names, orders, news and DSS descriptions switch to the chosen language. Open tabs refresh by themselves."),
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

                val animations by container.preferences.animations.collectAsStateWithLifecycle()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Animacje", "Animations"), style = MaterialTheme.typography.labelLarge)
                        Text(
                            tr("Przejścia, odbicia przycisków, fale na mapie. Wyłącz na słabszym telefonie.", "Transitions, button bounces, map waves. Turn off on a slower phone."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = animations,
                        onCheckedChange = {
                            haptics.tap()
                            container.preferences.setAnimations(it)
                        },
                    )
                }

                val alwaysTranslate by container.preferences.alwaysTranslate.collectAsStateWithLifecycle()
                val language by container.preferences.language.collectAsStateWithLifecycle()
                if (language != com.poldivers.app.core.prefs.ApiLanguage.ENGLISH) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Zawsze tłumacz", "Always translate"), style = MaterialTheme.typography.labelLarge)
                            Text(
                                tr("Teksty z wiki i kampanii od razu po polsku (tłumaczenie w telefonie)", "Wiki and campaign texts translated right away (on-device translation)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = alwaysTranslate,
                            onCheckedChange = {
                                haptics.tap()
                                container.preferences.setAlwaysTranslate(it)
                            },
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("Wibracje przy dotyku", "Haptic feedback"), style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = {
                            container.preferences.setHapticsEnabled(it)
                            if (it) haptics.confirm()
                        },
                    )
                }

                if (hapticsEnabled) {
                    val strength by container.preferences.hapticStrength.collectAsStateWithLifecycle()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("Siła wibracji", "Vibration strength"), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HapticStrength.entries.forEach { option ->
                                FilterChip(
                                    selected = strength == option,
                                    onClick = {
                                        container.preferences.setHapticStrength(option)
                                        container.haptics.preview(option)
                                    },
                                    label = { Text(option.label) },
                                )
                            }
                        }
                    }
                }

                UpdateSettingsSection(container.updates)

                Text(
                    tr("Nieoficjalna aplikacja fanowska, niezwiązana z Arrowhead Game Studios ani Sony. Dane: api.helldivers2.dev (community API). Archiwum i kampanie wojenne: helldivers.wiki.gg (CC BY-SA 4.0). Nazwy efektów planet: helldivers-2/json (MIT). Mapa sektorów i herby frakcji: helldivers-2/companion (MIT). Ikony i grafiki z gry (© Arrowhead Game Studios) pochodzą z helldivers.wiki.gg. Czcionki: Chakra Petch, Russo One (SIL OFL).", "Unofficial fan app, not affiliated with Arrowhead Game Studios or Sony. Data: api.helldivers2.dev (community API). Archive and war campaigns: helldivers.wiki.gg (CC BY-SA 4.0). Planet effect names: helldivers-2/json (MIT). Sector map and faction emblems: helldivers-2/companion (MIT). Game icons and artwork (© Arrowhead Game Studios) come from helldivers.wiki.gg. Fonts: Chakra Petch, Russo One (SIL OFL)."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
