package com.poldivers.app.ui.common

import com.poldivers.app.core.i18n.tr
import androidx.compose.ui.graphics.Color
import com.poldivers.app.ui.theme.FactionAutomaton
import com.poldivers.app.ui.theme.FactionHuman
import com.poldivers.app.ui.theme.FactionIlluminate
import com.poldivers.app.ui.theme.FactionTerminid
import com.poldivers.app.ui.theme.TextSecondary

fun factionColor(owner: String): Color = when (owner) {
    "Terminids" -> FactionTerminid
    "Automaton" -> FactionAutomaton
    "Illuminate" -> FactionIlluminate
    "Humans" -> FactionHuman
    else -> TextSecondary
}

/** Faction ids are fixed English keys in the API (not localized), so we label them ourselves. */
fun factionLabel(owner: String): String = when (owner) {
    "Terminids" -> tr("Terminidzi", "Terminids")
    "Automaton" -> tr("Automatony", "Automatons")
    "Illuminate" -> tr("Iluminaci", "Illuminate")
    "Humans" -> tr("Super Ziemia", "Super Earth")
    else -> owner.ifBlank { "?" }
}
