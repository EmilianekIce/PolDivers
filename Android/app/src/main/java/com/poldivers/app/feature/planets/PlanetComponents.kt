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
import androidx.compose.ui.platform.LocalContext
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.trends.Projection
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.ui.common.formatClockIn
import com.poldivers.app.ui.common.formatSeconds
import com.poldivers.app.ui.theme.StatusGreen
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.common.factionLabel
import com.poldivers.app.ui.common.formatNumber
import com.poldivers.app.ui.common.formatPercent
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

/** Projection of [planet] from the locally observed history (see TrendStore). */
@Composable
fun rememberProjection(planet: Planet): Projection {
    val context = LocalContext.current
    val now by rememberNow()
    return AppContainer.get(context).hd2Repository.projectionFor(planet, now)
}

/**
 * Liberation bar, or -- while the planet is under attack -- the defense bar with a countdown,
 * plus what the community trackers show: pace per hour, time to liberation, and whether a
 * defense will hold before the enemy takes the planet.
 */
@Composable
fun PlanetProgress(planet: Planet, modifier: Modifier = Modifier, showRegion: Boolean = true) {
    val event = planet.event
    val now by rememberNow()
    val projection = rememberProjection(planet)
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
                RateText(projection.ratePerHour)
            }
            DefenseOutlook(projection, now)
        } else {
            LinearProgressIndicator(
                progress = { (planet.liberationPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                color = FactionHuman,
                trackColor = factionColor(planet.currentOwner).copy(alpha = 0.45f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Wyzwolenie: ${formatPercent(planet.liberationPercent)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RateText(projection.ratePerHour)
            }
            LiberationOutlook(projection, now)

            val region = planet.leadingRegion
            val regionPercent = region?.liberationPercent
            if (showRegion && region != null && regionPercent != null && regionPercent > 0.0 && planet.liberationPercent < 0.01) {
                val regionProjection = AppContainer.get(LocalContext.current).hd2Repository.projectionFor(planet, region)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Region ${region.name}: ${formatPercent(regionPercent)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RateText(regionProjection.ratePerHour)
                }
                LiberationOutlook(regionProjection, now, what = "Zdobycie regionu")
            }
        }
    }
}

@Composable
fun RateText(ratePerHour: Double?) {
    val (text, color) = when {
        ratePerHour == null -> "tempo: liczę…" to MaterialTheme.colorScheme.onSurfaceVariant
        ratePerHour > Projection.RATE_EPSILON -> "+${formatPercent(ratePerHour)}%/h" to StatusGreen
        ratePerHour < -Projection.RATE_EPSILON -> "${formatPercent(ratePerHour)}%/h" to StatusRed
        else -> "0%/h" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun LiberationOutlook(projection: Projection, now: java.time.Instant, what: String = "Wyzwolenie") {
    val rate = projection.ratePerHour ?: return
    val eta = projection.etaSeconds
    val (text, color) = when {
        projection.percent >= 100.0 -> return
        eta != null -> "$what za ~${formatSeconds(eta)} (≈ ${formatClockIn(eta, now)})" to StatusGreen
        rate < -Projection.RATE_EPSILON -> "Wróg odbija teren — front się cofa" to StatusRed
        else -> "Front stoi w miejscu" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun DefenseOutlook(projection: Projection, now: java.time.Instant) {
    val left = projection.secondsLeft
    Text(
        if (left != null && left > 0) {
            "Koniec obrony za ${formatSeconds(left)} (≈ ${formatClockIn(left, now)})"
        } else {
            "Obrona dobiega końca"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val eta = projection.etaSeconds
    when (projection.outcome) {
        Projection.Outcome.ON_TRACK -> Text(
            "Obrona utrzymana za ~${eta?.let(::formatSeconds) ?: "?"} — zdążymy",
            style = MaterialTheme.typography.labelSmall,
            color = StatusGreen,
        )
        Projection.Outcome.FAILING -> {
            val atEnd = projection.percentAtDeadline ?: projection.percent
            val required = projection.requiredRatePerHour
            Text(
                "Przy tym tempie: ${formatPercent(atEnd, 1)}% na koniec — planeta padnie" +
                    (if (left != null && left > 0) " za ${formatSeconds(left)}" else "") +
                    (required?.let { ". Potrzeba ≥ ${formatPercent(it)}%/h" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = StatusRed,
            )
        }
        Projection.Outcome.UNKNOWN -> projection.requiredRatePerHour?.let {
            Text(
                "Potrzebne tempo: ${formatPercent(it)}%/h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> Unit
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
