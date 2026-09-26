package com.poldivers.app.ui.common

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
