package com.poldivers.app.feature.campaigns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.hd2.CampaignHistoryStore.PhaseRecord
import com.poldivers.app.data.hd2.CampaignPhase
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.wiki.WikiPage
import com.poldivers.app.data.wiki.WikiRepository
import com.poldivers.app.feature.archive.ArchiveViewModel
import com.poldivers.app.feature.planets.PlanetDetailSheet
import com.poldivers.app.ui.common.LoadableContent
import com.poldivers.app.ui.common.UiState
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.common.factionLabel
import com.poldivers.app.ui.common.gameText
import com.poldivers.app.ui.theme.StatusGreen
import com.poldivers.app.ui.theme.StatusRed
import com.poldivers.app.ui.theme.SuperEarthYellow
import com.poldivers.app.ui.wiki.WikiReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Campaign artwork + lead text from the wiki, fetched when the user opens this tab. */
class CampaignArtViewModel(private val wiki: WikiRepository) : ViewModel() {
    private val _art = MutableStateFlow<Map<String, WikiPage?>>(emptyMap())
    val art: StateFlow<Map<String, WikiPage?>> = _art.asStateFlow()

    fun load(titles: Collection<String>) {
        val missing = titles.filter { it !in _art.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            runCatching { wiki.getSummaries(missing) }.onSuccess { _art.value = _art.value + it }
        }
    }
}

private enum class PhaseState { ACTIVE, SUCCESS, FAILED, UNKNOWN }

private fun PhaseRecord.Outcome?.toState() = when (this) {
    PhaseRecord.Outcome.ACTIVE -> PhaseState.ACTIVE
    PhaseRecord.Outcome.SUCCESS -> PhaseState.SUCCESS
    PhaseRecord.Outcome.FAILED -> PhaseState.FAILED
    null -> PhaseState.UNKNOWN
}

private fun PhaseState.color(muted: Color) = when (this) {
    PhaseState.ACTIVE -> SuperEarthYellow
    PhaseState.SUCCESS -> StatusGreen
    PhaseState.FAILED -> StatusRed
    PhaseState.UNKNOWN -> muted
}

private fun PhaseState.label() = when (this) {
    PhaseState.ACTIVE -> "W TOKU"
    PhaseState.SUCCESS -> "SUKCES"
    PhaseState.FAILED -> "PORAŻKA"
    PhaseState.UNKNOWN -> "BRAK DANYCH"
}

/** Campaigns seem to run 3 phases; show at least that many slots on the timeline. */
private const val TYPICAL_PHASES = 3

/**
 * Galactic War campaigns in the layout of truthenforcers.com/campaigns: headline, front, artwork,
 * description, phase timeline, one section per phase (briefing with highlighted planets, outcome,
 * reward) and an archive of past campaigns.
 *
 * Sources: phase text/reward/progress from the game API (the MO); campaign artwork and
 * description from the campaign's wiki page; outcomes of past phases from what this phone has
 * recorded (the API forgets finished orders).
 */
@Composable
fun WarCampaignsTab() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: CampaignsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CampaignsViewModel(container.hd2Repository, container.preferences.language, container.campaignHistory) }
        },
    )
    val wiki: ArchiveViewModel = viewModel(
        key = "war-campaigns-wiki",
        factory = viewModelFactory { initializer { ArchiveViewModel(container.wikiRepository) } },
    )
    val artModel: CampaignArtViewModel = viewModel(
        factory = viewModelFactory { initializer { CampaignArtViewModel(container.wikiRepository) } },
    )
    val haptics = LocalHaptics.current
    val article by wiki.article.collectAsStateWithLifecycle()
    val art by artModel.art.collectAsStateWithLifecycle()
    val selected by viewModel.selectedPlanet.collectAsStateWithLifecycle()
    val state by viewModel.data.state.collectAsStateWithLifecycle()

    selected?.let { planet ->
        val data = (state as? UiState.Success)?.data
        PlanetDetailSheet(
            planet = planet,
            onDismiss = { viewModel.selectPlanet(null) },
            isMajorOrderTarget = data?.majorOrderPlanets?.contains(planet.index) == true,
            effects = data?.effects?.get(planet.index).orEmpty(),
        )
    }

    article?.let { articleState ->
        BackHandler { wiki.back() }
        WikiReader(state = articleState, onBack = { wiki.back() }, onOpenArticle = wiki::openArticle, onRetry = wiki::retry)
        return
    }

    val openWiki: (String) -> Unit = {
        haptics.tap()
        wiki.openArticle(it)
    }

    LoadableContent(viewModel.data) { data ->
        val activeIds = data.assignments.map { it.id }.toSet()
        val active = data.assignments
            .mapNotNull { a -> data.phases[a.id]?.let { it to a } }
            .groupBy { it.first.campaignKey }
        val archive = container.campaignHistory.all()
            .groupBy { it.campaignKey }
            .filterKeys { it !in active.keys }
            .toList()
            .sortedByDescending { (_, records) -> records.maxOf { it.lastSeenMs } }
        val now = System.currentTimeMillis()
        val planetNames = data.planets.values.map { it.name }

        LaunchedEffect(active.keys, archive.map { it.first }) {
            artModel.load(active.keys + archive.map { it.first })
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (active.isEmpty()) {
                item(key = "none") {
                    Text(
                        if (data.assignments.isEmpty()) {
                            "Brak aktywnej kampanii — Dowództwo nie wydało rozkazu."
                        } else {
                            "Obecny rozkaz nie należy do nazwanej kampanii wojennej. Jego cele są w zakładce Front."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            active.forEach { (key, entries) ->
                val (currentPhase, currentAssignment) = entries.maxBy { it.first.phase }
                val faction = campaignFaction(currentAssignment, data.planets)
                val accent = faction?.let(::factionColor) ?: SuperEarthYellow
                val page = art[key]
                val records = container.campaignHistory.phasesOf(key).associateBy { it.phase }.toMutableMap()
                entries.forEach { (phase, a) ->
                    records.putIfAbsent(phase.phase, PhaseRecord(a.id, phase.campaign, phase.campaignKey, phase.phase, phase.phaseName))
                }
                val states = records.mapValues { (_, r) -> r.outcome(now, stillActive = r.assignmentId in activeIds).toState() }

                item(key = "head-$key") {
                    CampaignHeader(currentPhase, faction, accent, page, active = true)
                }
                item(key = "timeline-$key") {
                    PhaseTimeline(
                        phases = (1..maxOf(TYPICAL_PHASES, records.keys.maxOrNull() ?: 1)).map { n ->
                            Triple(n, records[n]?.phaseName.orEmpty(), states[n] ?: PhaseState.UNKNOWN)
                        },
                    )
                }
                // Newest phase first, like the site.
                records.values.sortedByDescending { it.phase }.forEach { record ->
                    item(key = "phase-$key-${record.phase}") {
                        val assignment = entries.firstOrNull { it.first.phase == record.phase }?.second
                        PhaseSection(
                            campaign = currentPhase.campaign,
                            record = record,
                            state = states[record.phase] ?: PhaseState.UNKNOWN,
                            assignment = assignment,
                            imageUrl = page?.thumbnail?.source,
                            accent = accent,
                            planets = data.planets,
                            planetNames = planetNames,
                            onPlanetClick = { viewModel.selectPlanet(it) },
                        )
                    }
                }
                item(key = "wiki-$key") {
                    OutlinedButton(onClick = { openWiki(key) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        Text("  ${currentPhase.campaign} na Helldivers Wiki")
                    }
                }
            }

            item(key = "archive-title") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text("ARCHIWUM KAMPANII", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 10.dp))
                }
            }
            if (archive.isEmpty()) {
                item(key = "archive-empty") {
                    Text(
                        "Apka zapisuje zakończone kampanie od chwili instalacji. Wcześniejsze znajdziesz w pełnym archiwum na wiki.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            archive.forEach { (key, recordsList) ->
                item(key = "archive-$key") {
                    val phaseStates = recordsList.sortedBy { it.phase }.map { it.outcome(now, stillActive = false).toState() }
                    ArchiveRow(
                        name = recordsList.first().campaign,
                        faction = recordsList.firstNotNullOfOrNull { it.faction.takeIf { f -> f.isNotBlank() } },
                        lastSeenMs = recordsList.maxOf { it.lastSeenMs },
                        phaseStates = phaseStates,
                        imageUrl = art[key]?.thumbnail?.source,
                        onClick = { openWiki(key) },
                    )
                }
            }
            item(key = "archive-wiki") {
                OutlinedButton(onClick = { openWiki("Campaigns") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Text("  Pełne archiwum kampanii (Helldivers Wiki)")
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, bar: Boolean = false) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.10f))
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bar) Box(Modifier.width(3.dp).height(18.dp).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp))
    }
}

@Composable
private fun CampaignHeader(phase: CampaignPhase, faction: String?, accent: Color, page: WikiPage?, active: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(phase.campaign.uppercase(), style = MaterialTheme.typography.displaySmall, color = accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(faction?.let { "FRONT: ${factionLabel(it).uppercase()}" } ?: "FRONT NIEZNANY", accent, bar = true)
            if (active) Chip("AKTYWNA KAMPANIA", SuperEarthYellow)
        }
        page?.thumbnail?.source?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = phase.campaign,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .border(BorderStroke(1.dp, accent.copy(alpha = 0.6f)))
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
        page?.extract?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text.split("\n").firstOrNull { it.length > 40 } ?: text.take(600),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "Wykonaj większość rozkazów tej kampanii, by zdobyć nagrodę kampanii.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhaseTimeline(phases: List<Triple<Int, String, PhaseState>>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val current = phases.lastOrNull { it.third != PhaseState.UNKNOWN }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        current?.let { (_, _, s) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, tint = s.color(muted), modifier = Modifier.size(16.dp))
                Text(if (s == PhaseState.ACTIVE) "KAMPANIA W TOKU" else s.label(), style = MaterialTheme.typography.labelLarge, color = s.color(muted))
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp),
        ) {
            val y = size.height / 2
            val step = if (phases.size > 1) size.width / (phases.size - 1) else 0f
            drawLine(muted.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            val reached = phases.indexOfLast { it.third != PhaseState.UNKNOWN }
            if (reached > 0) drawLine(SuperEarthYellow, Offset(0f, y), Offset(step * reached, y), strokeWidth = 2.dp.toPx())
            phases.forEachIndexed { i, (_, _, s) ->
                val c = Offset(if (phases.size > 1) step * i else size.width / 2, y)
                drawCircle(s.color(muted.copy(alpha = 0.5f)), radius = 6.dp.toPx(), center = c)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            phases.forEachIndexed { i, (n, name, _) ->
                Text(
                    name.ifBlank { "Faza $n" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        phases.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PhaseSection(
    campaign: String,
    record: PhaseRecord,
    state: PhaseState,
    assignment: Assignment?,
    imageUrl: String?,
    accent: Color,
    planets: Map<Int, Planet>,
    planetNames: List<String>,
    onPlanetClick: (Planet) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Text("${campaign.uppercase()} · FAZA ${record.phase}", style = MaterialTheme.typography.labelSmall, color = SuperEarthYellow)
        Text(record.phaseName.ifBlank { "Faza ${record.phase}" }.uppercase(), style = MaterialTheme.typography.headlineMedium, color = accent)
        Row {
            Text("WYNIK – ", style = MaterialTheme.typography.labelLarge)
            Text(state.label(), style = MaterialTheme.typography.labelLarge, color = state.color(muted))
        }
        imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .border(BorderStroke(1.dp, accent.copy(alpha = 0.5f))),
            )
        }
        val briefing = assignment?.briefing ?: record.briefing
        if (briefing.isNotBlank()) {
            Text(gameText(briefing, SuperEarthYellow, planetNames), style = MaterialTheme.typography.bodyMedium)
        }
        val rewardType = assignment?.let { (it.rewards.firstOrNull() ?: it.reward)?.type } ?: record.rewardType
        val rewardAmount = assignment?.let { (it.rewards.firstOrNull() ?: it.reward)?.amount } ?: record.rewardAmount
        if (rewardAmount > 0) RewardBox(rewardType, rewardAmount)
        if (assignment != null) {
            AssignmentCard(assignment, planets, onPlanetClick = onPlanetClick, showText = false)
        }
    }
}

@Composable
private fun RewardBox(type: Int, amount: Long) {
    val name = when (type) {
        1 -> "MEDALE WOJENNE"
        2 -> "SUPER KREDYTY"
        3 -> "PRÓBKI"
        4 -> "ZAPOTRZEBOWANIE"
        else -> "NAGRODA"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Wykonaj rozkaz, by zdobyć nagrodę rozkazu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, Color(0xFF3B6FA8)))
                .background(Color(0xFF0F1A26))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SuperEarthYellow) }
            Column {
                Text(name, style = MaterialTheme.typography.labelMedium)
                Text("×$amount", style = MaterialTheme.typography.labelLarge, color = Color(0xFF4FA3E0))
            }
        }
    }
}

@Composable
private fun ArchiveRow(
    name: String,
    faction: String?,
    lastSeenMs: Long,
    phaseStates: List<PhaseState>,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = faction?.let(::factionColor) ?: SuperEarthYellow
    val successes = phaseStates.count { it == PhaseState.SUCCESS }
    val success = successes * 2 > phaseStates.size
    val days = ((System.currentTimeMillis() - lastSeenMs) / 86_400_000L).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clickable(onClick = onClick)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (days == 0L) "DZIŚ" else "$days DNI TEMU",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier.width(56.dp),
            )
            Box(
                Modifier
                    .size(width = 96.dp, height = 60.dp)
                    .border(BorderStroke(1.dp, accent.copy(alpha = 0.6f)))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                imageUrl?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(60.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name.uppercase(), style = MaterialTheme.typography.headlineSmall, color = accent)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(faction?.let { "FRONT: ${factionLabel(it).uppercase()}" } ?: "FRONT", accent, bar = true)
                }
                Text(
                    if (success) "UDANA KAMPANIA" else "NIEUDANA KAMPANIA",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (success) StatusGreen else StatusRed,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    phaseStates.forEach { s ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(s.color(muted)))
                    }
                }
            }
        }
    }
}
