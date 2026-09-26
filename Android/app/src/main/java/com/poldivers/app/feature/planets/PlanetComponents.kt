package com.poldivers.app.feature.planets

import com.poldivers.app.ui.anim.zoomIn
import com.poldivers.app.ui.anim.slowSpin
import com.poldivers.app.ui.anim.appear
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.poldivers.app.R
import androidx.compose.ui.platform.LocalContext
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.trends.Projection
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.poldivers.app.core.art.GameArt
import com.poldivers.app.core.art.GameIcon
import com.poldivers.app.core.art.rememberGameArt
import com.poldivers.app.data.hd2.PlanetEffect
import com.poldivers.app.ui.common.TranslatableText
import com.poldivers.app.ui.common.gameText
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
            .zoomIn(0.5f)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

fun effectColor(effect: PlanetEffect): Color = when (effect.kind) {
    PlanetEffect.Kind.ENEMY_VARIANT -> Color(0xFFFF7A45)
    PlanetEffect.Kind.HAZARD -> Color(0xFFB58CFF)
    PlanetEffect.Kind.SUPPORT -> Color(0xFF58C4FF)
    PlanetEffect.Kind.SITE, PlanetEffect.Kind.OTHER -> Color(0xFFB0B6BE)
}

/** Every active effect of the planet, as icon + name chips (enemy variants first). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectTags(effects: List<PlanetEffect>, modifier: Modifier = Modifier) {
    // Cards show what changes how a planet plays; support buffs (arsenal augmentations, SEAF)
    // are listed in the planet's details.
    val shown = effects.filter { it.kind != PlanetEffect.Kind.SUPPORT && it.kind != PlanetEffect.Kind.OTHER }
    if (shown.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val art = rememberGameArt()
        shown.forEach { effect ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                GameIcon(art.effectIcon(effect), size = 18.dp)
                Tag(effect.shortName.uppercase(), effectColor(effect))
            }
        }
    }
}

/** Faction emblem (Super Earth / Terminids / Automatons / Illuminate), or a colored dot if unknown. */
@Composable
fun FactionDot(owner: String, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val icon = when (owner) {
        "Humans" -> R.drawable.faction_humans
        "Terminids" -> R.drawable.faction_terminids
        "Automaton" -> R.drawable.faction_automaton
        "Illuminate" -> R.drawable.faction_illuminate
        else -> null
    }
    if (icon != null) {
        Image(painterResource(icon), contentDescription = factionLabel(owner), modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size / 2)
                .clip(CircleShape)
                .background(factionColor(owner)),
        )
    }
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
            com.poldivers.app.ui.anim.HudProgressBar(
                progress = ((event.defensePercent / 100.0).toFloat().coerceIn(0f, 1f)).toFloat(),
                color = FactionHuman,
                track = factionColor(event.faction).copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
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
            GambitOutlook(planet, projection)
        } else if (planet.currentOwner == "Humans") {
            // Ours and not attacked: nothing to liberate.
            Text(
                "✓ WYZWOLONA — pod kontrolą Super Ziemi",
                style = MaterialTheme.typography.labelLarge,
                color = FactionHuman,
            )
        } else {
            com.poldivers.app.ui.anim.HudProgressBar(
                progress = ((planet.liberationPercent / 100.0).toFloat().coerceIn(0f, 1f)).toFloat(),
                color = FactionHuman,
                track = factionColor(planet.currentOwner).copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
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
            ResistanceLine(planet)

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

/**
 * Enemy resistance = how fast the enemy claws the planet back on its own (health regen), in
 * liberation %/h. Players must out-pace this for the bar to move at all.
 */
fun Planet.resistancePerHour(): Double =
    if (maxHealth <= 0) 0.0 else regenPerSecond * 3600 / maxHealth * 100

@Composable
fun ResistanceLine(planet: Planet) {
    if (planet.currentOwner == "Humans" || planet.maxHealth <= 0) return
    val r = planet.resistancePerHour()
    // Negative regen = the enemy is cut off from supply: the planet liberates itself.
    val (label, color) = when {
        r < 0.0 -> "ODCIĘTY (planeta sama się wyzwala)" to StatusGreen
        r >= 4.0 -> "BARDZO WYSOKI" to StatusRed
        r >= 2.5 -> "WYSOKI" to Color(0xFFFF7A45)
        r >= 1.5 -> "ŚREDNI" to SuperEarthYellow
        r > 0.0 -> "NISKI" to StatusGreen
        else -> "BRAK" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Opór wroga: $label", style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f))
        Text(
            if (r < 0) "+${formatPercent(-r)}%/h" else "−${formatPercent(r)}%/h",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
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

/**
 * Gambit: liberating the planet the attack comes from ends the defense campaign at once. Worth it
 * when that liberation can finish before the defense timer runs out -- and only needed when the
 * defense itself will not hold.
 */
@Composable
private fun GambitOutlook(defended: Planet, defense: Projection) {
    val repository = AppContainer.get(LocalContext.current).hd2Repository
    val attackers = repository.cachedPlanets().filter { defended.index in it.attacking && it.currentOwner != "Humans" }
    if (attackers.isEmpty()) return
    val secondsLeft = defense.secondsLeft ?: return
    attackers.forEach { attacker ->
        val lib = repository.projectionFor(attacker)
        val eta = lib.etaSeconds
        val required = if (secondsLeft > 0) (100.0 - lib.percent) / (secondsLeft / 3600.0) else null
        val (text, color) = when {
            defense.outcome == Projection.Outcome.ON_TRACK ->
                "niepotrzebny — obrona utrzyma się sama" to MaterialTheme.colorScheme.onSurfaceVariant
            eta != null && eta < secondsLeft ->
                "MA SENS — wyzwolenie za ~${formatSeconds(eta)}, przed końcem obrony" to StatusGreen
            lib.ratePerHour == null -> "liczę tempo wyzwolenia ${attacker.name}…" to MaterialTheme.colorScheme.onSurfaceVariant
            else -> "za wolno — potrzeba ≥ ${formatPercent(required ?: 0.0)}%/h na ${attacker.name}" to StatusRed
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "GAMBIT: wyzwól ${attacker.name} (${formatPercent(lib.percent, 1)}%), skąd idzie atak",
                style = MaterialTheme.typography.labelSmall,
                color = SuperEarthYellow,
            )
            Text(text, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
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
    effects: List<PlanetEffect> = emptyList(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val art = rememberGameArt()
    val repository = AppContainer.get(LocalContext.current).hd2Repository
    // Planet artwork lives on the wiki under the English name; fetched only now, on open.
    val englishName by produceState(art.englishPlanetName(planet.index), planet.index) {
        if (value == null) value = runCatching { repository.getPlanetEnglishName(planet.index) }.getOrNull()
    }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The planet zooms in and then slowly turns, with a faction-coloured halo.
                Box(Modifier.size(96.dp).zoomIn(0.3f), contentAlignment = Alignment.Center) {
                    FactionDot(planet.currentOwner, size = 40.dp)
                    englishName?.let { name ->
                        AsyncImage(
                            model = art.planetIcon(planet.index, effects) ?: GameArt.planetImageUrl(name),
                            contentDescription = planet.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(96.dp).slowSpin(90_000),
                        )
                    }
                }
                Column(Modifier.weight(1f).appear(1)) {
                    Text(planet.name.uppercase(), style = MaterialTheme.typography.headlineMedium)
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

            if (effects.isNotEmpty()) {
                Section("MODYFIKATORY PLANETY")
                effects.forEach { effect ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GameIcon(art.effectIcon(effect), size = 32.dp)
                            Tag(
                                when (effect.kind) {
                                    PlanetEffect.Kind.ENEMY_VARIANT -> "WRÓG"
                                    PlanetEffect.Kind.HAZARD -> "ZAGROŻENIE"
                                    PlanetEffect.Kind.SUPPORT -> "WSPARCIE"
                                    PlanetEffect.Kind.SITE -> "OBIEKT"
                                    PlanetEffect.Kind.OTHER -> "EFEKT"
                                },
                                effectColor(effect),
                            )
                            Text(effect.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                        if (effect.name != effect.originalName) {
                            Text(effect.originalName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (effect.description.isNotBlank()) {
                            TranslatableText(effect.description, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Section("STAN")
            }

            StatLine("Helldiverów na planecie", formatNumber(planet.playerCount))
            if (planet.maxHealth > 0) {
                StatLine("HP planety", "${formatNumber(planet.health)} / ${formatNumber(planet.maxHealth)}")
            }
            planet.event?.takeIf { it.maxHealth > 0 }?.let { e ->
                StatLine("HP obrony", "${formatNumber(e.health)} / ${formatNumber(e.maxHealth)}")
            }
            PlanetProgress(planet)


            val container = AppContainer.get(LocalContext.current)
            val lang = container.preferences.language.value.tag
            val biome = container.terms.biome(planet.index, lang)
            if (biome != null || planet.biome?.name?.isNotBlank() == true) {
                Section("BIOM")
                Text(biome?.name ?: planet.biome?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                val description = biome?.description ?: planet.biome?.description
                if (!description.isNullOrBlank()) {
                    if (biome?.official == true) {
                        Text(gameText(description, SuperEarthYellow), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TranslatableText(description, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Bundled per-planet conditions (the API omits some, e.g. Super Earth's rainstorms).
            val conditions = container.terms.conditions(planet.index, lang)
            if (conditions.isNotEmpty() || planet.hazards.isNotEmpty()) {
                Section("WARUNKI ŚRODOWISKOWE")
                if (conditions.isNotEmpty()) {
                    conditions.forEach { c ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GameIcon(art.hazardIcon(c.englishName), size = 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                c.description?.let { d ->
                                    if (c.official) {
                                        Text(gameText(d, SuperEarthYellow), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        TranslatableText(d, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    planet.hazards.forEach { hazard ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GameIcon(art.hazardIcon(hazard.name), size = 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(container.terms.hazardName(hazard.name, lang), style = MaterialTheme.typography.bodyLarge)
                                if (hazard.description.isNotBlank()) {
                                    TranslatableText(hazard.description, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (planet.regions.isNotEmpty()) {
                Section("MIASTA I REGIONY")
                planet.regions.sortedByDescending { it.liberationPercent ?: 0.0 }.forEach { region ->
                    val percent = region.liberationPercent
                    val captured = percent != null && percent >= 99.95
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(region.name?.takeIf { it.isNotBlank() } ?: "Region ${region.id}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(regionSizeLabel(region.size), if (!region.isAvailable && !captured) "zablokowany" else null)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (captured) {
                            Tag("ZDOBYTE", StatusGreen)
                        } else {
                            Text(
                                buildString {
                                    percent?.let { append("${formatPercent(it, 1)}%") }
                                    if (region.players > 0) append(" · ${com.poldivers.app.ui.common.formatCompact(region.players)} graczy")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.appear(2)) {
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

private fun regionSizeLabel(size: String?): String? = when (size?.lowercase()) {
    null, "" -> null
    "settlement" -> "Osada"
    "town" -> "Miasteczko"
    "city" -> "Miasto"
    "megacity" -> "Megamiasto"
    else -> size.takeUnless { s -> s.all { it.isDigit() } }
}
