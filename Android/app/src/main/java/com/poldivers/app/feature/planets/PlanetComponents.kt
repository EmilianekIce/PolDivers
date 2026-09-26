package com.poldivers.app.feature.planets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.common.factionLabel
import com.poldivers.app.ui.common.formatNumber
import com.poldivers.app.ui.common.formatPercent
import com.poldivers.app.ui.common.formatRemaining
import com.poldivers.app.ui.common.rememberNow
import com.poldivers.app.ui.theme.FactionHuman
import com.poldivers.app.ui.theme.StatusRed
import com.poldivers.app.ui.theme.SuperEarthYellow

/** Small colored pill, e.g. "OBRONA", "ROZKAZ", "DSS". */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun FactionDot(owner: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(factionColor(owner)),
    )
}

/**
 * Liberation bar, or -- while the planet is under attack -- the defense bar with a countdown,
 * which is what actually matters to players at that moment.
 */
@Composable
fun PlanetProgress(planet: Planet, modifier: Modifier = Modifier) {
    val event = planet.event
    val now by rememberNow()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (event != null) {
            LinearProgressIndicator(
                progress = { (event.defensePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                color = FactionHuman,
                trackColor = factionColor(event.faction).copy(alpha = 0.45f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Obrona: ${formatPercent(event.defensePercent)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Pozostało: ${formatRemaining(event.endTime, now)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusRed,
                )
            }
        } else {
            LinearProgressIndicator(
                progress = { (planet.liberationPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                color = FactionHuman,
                trackColor = factionColor(planet.currentOwner).copy(alpha = 0.45f),
            )
            Text(
                "Wyzwolenie: ${formatPercent(planet.liberationPercent)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanetDetailSheet(
    planet: Planet,
    onDismiss: () -> Unit,
    isMajorOrderTarget: Boolean = false,
    hasDss: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FactionDot(planet.currentOwner)
                Column(Modifier.weight(1f)) {
                    Text(planet.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Sektor ${planet.sector} · ${factionLabel(planet.currentOwner)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                planet.event?.let { Tag("OBRONA przed: ${factionLabel(it.faction)}", StatusRed) }
                if (isMajorOrderTarget) Tag("CEL ROZKAZU", SuperEarthYellow)
                if (hasDss) Tag("DSS NA ORBICIE", SuperEarthYellow)
                if (planet.disabled) Tag("NIEDOSTĘPNA", MaterialTheme.colorScheme.onSurfaceVariant)
            }

            StatLine("Helldiverów na planecie", formatNumber(planet.playerCount))
            PlanetProgress(planet)
            if (planet.event == null && planet.regenPerSecond > 0 && planet.maxHealth > 0 && planet.currentOwner != "Humans") {
                val regenPerHour = planet.regenPerSecond * 3600 / planet.maxHealth * 100
                StatLine("Regeneracja wroga", "${formatPercent(regenPerHour)}% / h")
            }

            planet.biome?.takeIf { it.name.isNotBlank() }?.let { biome ->
                Section("BIOM")
                Text(biome.name, style = MaterialTheme.typography.bodyLarge)
                if (biome.description.isNotBlank()) {
                    Text(biome.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (planet.hazards.isNotEmpty()) {
                Section("WARUNKI ŚRODOWISKOWE")
                planet.hazards.forEach { hazard ->
                    Column {
                        Text(hazard.name, style = MaterialTheme.typography.bodyLarge)
                        if (hazard.description.isNotBlank()) {
                            Text(hazard.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            val regions = planet.regions.filter { !it.name.isNullOrBlank() }
            if (regions.isNotEmpty()) {
                Section("REGIONY")
                regions.forEach { region ->
                    val health = region.health
                    val percent = if (health != null && region.maxHealth > 0) {
                        (1.0 - health.toDouble() / region.maxHealth) * 100
                    } else {
                        null
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(region.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            buildString {
                                percent?.let { append("${formatPercent(it, 1)}%") }
                                if (region.players > 0) append(" · ${formatNumber(region.players)} 👤")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            planet.statistics?.let { stats ->
                Section("STATYSTYKI")
                StatLine("Misje wygrane / przegrane", "${formatNumber(stats.missionsWon)} / ${formatNumber(stats.missionsLost)}")
                StatLine("Skuteczność misji", "${stats.missionSuccessRate}%")
                StatLine("Zabici wrogowie", formatNumber(stats.terminidKills + stats.automatonKills + stats.illuminateKills))
                StatLine("Poległi Helldiverzy", formatNumber(stats.deaths))
                StatLine("Ogień bratobójczy", formatNumber(stats.friendlies))
                StatLine("Celność", "${stats.accuracy}%")
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
