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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.poldivers.app.core.art.GameIcon
import com.poldivers.app.core.art.rememberGameArt
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.wiki.Outcome
import com.poldivers.app.data.wiki.WikiCampaign
import com.poldivers.app.data.wiki.WikiCampaignPhase
import com.poldivers.app.data.wiki.WikiRepository
import com.poldivers.app.feature.archive.ArchiveViewModel
import com.poldivers.app.feature.planets.PlanetDetailSheet
import com.poldivers.app.ui.common.Loadable
import com.poldivers.app.ui.common.LoadableContent
import com.poldivers.app.ui.common.TranslatableText
import com.poldivers.app.ui.common.UiState
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.common.factionLabel
import com.poldivers.app.ui.common.gameText
import com.poldivers.app.ui.theme.StatusGreen
import com.poldivers.app.ui.theme.StatusRed
import com.poldivers.app.ui.theme.SuperEarthYellow
import com.poldivers.app.ui.theme.glow
import com.poldivers.app.ui.theme.hudPanel
import com.poldivers.app.ui.wiki.WikiReader
import kotlinx.coroutines.flow.flowOf

/** Campaigns from the wiki's Campaigns page -- loaded when this tab is opened. */
class WarCampaignsViewModel(wiki: WikiRepository) : ViewModel() {
    val data = Loadable(viewModelScope, flowOf(Unit)) { wiki.getCampaigns().asReversed() }
}

private fun Outcome.color(muted: Color) = when (this) {
    Outcome.IN_PROGRESS -> SuperEarthYellow
    Outcome.SUCCESS -> StatusGreen
    Outcome.FAILURE -> StatusRed
    Outcome.UNKNOWN -> muted
}

private fun Outcome.label() = when (this) {
    Outcome.IN_PROGRESS -> "W TOKU"
    Outcome.SUCCESS -> "SUKCES"
    Outcome.FAILURE -> "PORAŻKA"
    Outcome.UNKNOWN -> "BRAK DANYCH"
}

/** Campaigns run three phases; the timeline always shows at least that many slots. */
private const val TYPICAL_PHASES = 3

/**
 * Galactic War campaigns laid out like truthenforcers.com/campaigns -- data entirely from
 * helldivers.wiki.gg/wiki/Campaigns (the community record: names, descriptions, phases, outcomes,
 * rewards, briefings/debriefs). For the phase running right now, the live Major Order objectives
 * and prognosis from the game API are shown underneath.
 */
@Composable
fun WarCampaignsTab() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val campaignsVm: WarCampaignsViewModel = viewModel(
        factory = viewModelFactory { initializer { WarCampaignsViewModel(container.wikiRepository) } },
    )
    val ordersVm: CampaignsViewModel = viewModel(
        factory = viewModelFactory { initializer { CampaignsViewModel(container.hd2Repository, container.preferences.language) } },
    )
    val wiki: ArchiveViewModel = viewModel(
        key = "war-campaigns-wiki",
        factory = viewModelFactory { initializer { ArchiveViewModel(container.wikiRepository) } },
    )
    val haptics = LocalHaptics.current
    val article by wiki.article.collectAsStateWithLifecycle()
    val selectedPlanet by ordersVm.selectedPlanet.collectAsStateWithLifecycle()
    val orders by ordersVm.data.state.collectAsStateWithLifecycle()
    var opened by rememberSaveable { mutableStateOf<String?>(null) }

    selectedPlanet?.let { planet ->
        val data = (orders as? UiState.Success)?.data
        PlanetDetailSheet(
            planet = planet,
            onDismiss = { ordersVm.selectPlanet(null) },
            isMajorOrderTarget = data?.majorOrderPlanets?.contains(planet.index) == true,
            effects = data?.effects?.get(planet.index).orEmpty(),
        )
    }

    article?.let { state ->
        BackHandler { wiki.back() }
        WikiReader(state = state, onBack = { wiki.back() }, onOpenArticle = wiki::openArticle, onRetry = wiki::retry)
        return
    }

    val liveOrder = (orders as? UiState.Success)?.data
    // Campaign texts are English (wiki): one switch translates the whole page.
    val alwaysTranslate = com.poldivers.app.ui.common.LocalAutoTranslate.current
    var translateAll by rememberSaveable(alwaysTranslate) { mutableStateOf(alwaysTranslate) }
    val canTranslate = container.translator.canTranslate
    LoadableContent(campaignsVm.data) { campaigns ->
      androidx.compose.runtime.CompositionLocalProvider(com.poldivers.app.ui.common.LocalAutoTranslate provides translateAll) {
        val active = campaigns.firstOrNull { it.isActive }
        val detail = opened?.let { name -> campaigns.firstOrNull { it.name == name } }
        if (detail != null) BackHandler { opened = null }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (canTranslate) {
                item(key = "translate") {
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            translateAll = !translateAll
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Translate, contentDescription = null)
                        Text(if (translateAll) "  POKAŻ ORYGINAŁ (EN)" else "  PRZETŁUMACZ KAMPANIE")
                    }
                }
            }
            when {
                detail != null -> {
                    item(key = "back") {
                        TextButton(onClick = {
                            haptics.tap()
                            opened = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Text("  Wszystkie kampanie")
                        }
                    }
                    campaignDetail(detail, liveOrder?.assignments?.firstOrNull(), liveOrder?.planets.orEmpty()) { ordersVm.selectPlanet(it) }
                }
                active != null -> campaignDetail(active, liveOrder?.assignments?.firstOrNull(), liveOrder?.planets.orEmpty()) { ordersVm.selectPlanet(it) }
                else -> item(key = "none") {
                    Text(
                        "Wiki nie odnotowała jeszcze trwającej kampanii. Aktualne rozkazy są w zakładce Rozkazy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (detail == null) {
                item(key = "archive-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Text(
                            "ARCHIWUM KAMPANII",
                            style = MaterialTheme.typography.headlineLarge.glow(Color.White, 10f),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                campaigns.filter { it !== active }.forEach { c ->
                    item(key = "archive-${c.name}") {
                        ArchiveRow(c) {
                            haptics.tap()
                            opened = c.name
                        }
                    }
                }
            }
            item(key = "source") {
                OutlinedButton(onClick = { haptics.tap(); wiki.openArticle("Campaigns") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Text("  Źródło: Helldivers Wiki (CC BY-SA)")
                }
            }
        }
      }
    }
}

private fun LazyListScope.campaignDetail(
    campaign: WikiCampaign,
    liveAssignment: Assignment?,
    planets: Map<Int, Planet>,
    onPlanetClick: (Planet) -> Unit,
) {
    item(key = "head-${campaign.name}") { CampaignHeader(campaign) }
    item(key = "timeline-${campaign.name}") { PhaseTimeline(campaign) }
    campaign.phases.sortedByDescending { it.number }.forEach { phase ->
        item(key = "phase-${campaign.name}-${phase.number}") {
            PhaseSection(
                campaign = campaign,
                phase = phase,
                liveAssignment = liveAssignment.takeIf { phase.outcome == Outcome.IN_PROGRESS },
                planets = planets,
                onPlanetClick = onPlanetClick,
            )
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, bar: Boolean = false) {
    Row(Modifier.background(color.copy(alpha = 0.10f)).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (bar) Box(Modifier.width(3.dp).height(18.dp).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp))
    }
}

@Composable
private fun CampaignHeader(campaign: WikiCampaign) {
    val art = rememberGameArt()
    val accent = campaign.faction.takeIf { it.isNotBlank() }?.let(::factionColor) ?: SuperEarthYellow
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(campaign.name.uppercase(), style = MaterialTheme.typography.displaySmall.glow(accent, 28f), color = accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (campaign.faction.isBlank()) "FRONT" else "FRONT: ${factionLabel(campaign.faction).uppercase()}", accent, bar = true)
            when {
                campaign.isActive -> Chip("AKTYWNA KAMPANIA", SuperEarthYellow)
                campaign.succeeded -> Chip("UDANA KAMPANIA", StatusGreen)
                else -> Chip("NIEUDANA KAMPANIA", StatusRed)
            }
        }
        art.wikiImage(campaign.bannerImage)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = campaign.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).border(BorderStroke(1.dp, accent.copy(alpha = 0.6f))),
            )
        }
        if (campaign.description.isNotBlank()) {
            TranslatableText(campaign.description.replace(Regex("</?i(=\\d)?>"), ""), MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurface)
        }
        RewardBox(
            caption = "Wykonaj większość rozkazów tej kampanii, by zdobyć nagrodę kampanii",
            icon = art.wikiRewardIcon(campaign.rewardType),
            title = campaign.rewardText.ifBlank { campaign.rewardType.uppercase() },
            amount = campaign.rewardAmount,
        )
    }
}

@Composable
private fun PhaseTimeline(campaign: WikiCampaign) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val slots = maxOf(TYPICAL_PHASES, campaign.phases.maxOfOrNull { it.number } ?: 0)
    val byNumber = campaign.phases.associateBy { it.number }
    val status = when {
        campaign.isActive -> "KAMPANIA W TOKU" to SuperEarthYellow
        campaign.succeeded -> "KAMPANIA UDANA" to StatusGreen
        else -> "KAMPANIA NIEUDANA" to StatusRed
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, tint = status.second, modifier = Modifier.size(16.dp))
            Text(status.first, style = MaterialTheme.typography.labelLarge.glow(status.second, 8f), color = status.second)
        }
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val y = size.height / 2
            val step = if (slots > 1) size.width / (slots - 1) else 0f
            drawLine(muted.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            val reached = (1..slots).lastOrNull { byNumber[it] != null }?.minus(1) ?: 0
            if (reached > 0) drawLine(SuperEarthYellow, Offset(0f, y), Offset(step * reached, y), strokeWidth = 2.dp.toPx())
            for (i in 0 until slots) {
                val c = byNumber[i + 1]?.outcome?.color(muted) ?: muted.copy(alpha = 0.4f)
                drawCircle(c, radius = 6.dp.toPx(), center = Offset(if (slots > 1) step * i else size.width / 2, y))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until slots) {
                Text(
                    byNumber[i + 1]?.name?.uppercase() ?: "FAZA ${i + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        slots - 1 -> TextAlign.End
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
    campaign: WikiCampaign,
    phase: WikiCampaignPhase,
    liveAssignment: Assignment?,
    planets: Map<Int, Planet>,
    onPlanetClick: (Planet) -> Unit,
) {
    val art = rememberGameArt()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = campaign.faction.takeIf { it.isNotBlank() }?.let(::factionColor) ?: SuperEarthYellow
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Text("${campaign.name.uppercase()} · FAZA ${phase.number}", style = MaterialTheme.typography.labelSmall, color = SuperEarthYellow)
        Text(phase.name.uppercase(), style = MaterialTheme.typography.headlineMedium.glow(accent, 20f), color = accent)
        Row {
            Text("WYNIK – ", style = MaterialTheme.typography.labelLarge)
            Text(phase.outcome.label(), style = MaterialTheme.typography.labelLarge, color = phase.outcome.color(muted))
            if (phase.dateStart.isNotBlank()) {
                Text(
                    "   ${phase.dateStart}${if (phase.dateEnd.isNotBlank()) " – ${phase.dateEnd}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
        art.wikiImage(phase.image ?: campaign.bannerImage)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).border(BorderStroke(1.dp, accent.copy(alpha = 0.5f))),
            )
        }
        // Live text from the game for the running phase (localised), wiki text otherwise.
        val liveBriefing = liveAssignment?.briefing?.takeIf { it.isNotBlank() }
        if (liveBriefing != null) {
            Text(gameText(liveBriefing, SuperEarthYellow, planets.values.map { it.name }), style = MaterialTheme.typography.bodyMedium)
        } else if (phase.briefing.isNotBlank()) {
            WikiText(phase.briefing)
        }
        if (phase.debrief.isNotBlank()) {
            Text("PODSUMOWANIE", style = MaterialTheme.typography.labelLarge, color = muted)
            WikiText(phase.debrief)
        }
        if (phase.rewardAmount > 0) {
            RewardBox(
                caption = "Wykonaj rozkaz, by zdobyć nagrodę rozkazu",
                icon = art.wikiRewardIcon(phase.rewardType),
                title = null,
                amount = phase.rewardAmount,
            )
        }
        if (liveAssignment != null) {
            AssignmentCard(liveAssignment, planets, onPlanetClick = onPlanetClick, showText = false)
        }
    }
}

/** Wiki (English) text with the game's highlight markup and an optional translation. */
@Composable
private fun WikiText(markup: String) {
    val context = LocalContext.current
    val canTranslate = AppContainer.get(context).translator.canTranslate
    if (canTranslate) {
        TranslatableText(markup.replace(Regex("</?i(=\\d)?>"), ""), MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurface)
    } else {
        Text(gameText(markup, SuperEarthYellow), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RewardBox(caption: String, icon: String?, title: String?, amount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.hudPanel(Color(0xFF4FA3E0), glow = true).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameIcon(icon, size = 36.dp)
            Column {
                title?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium) }
                Text("×$amount", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4FA3E0))
            }
        }
    }
}

@Composable
private fun ArchiveRow(campaign: WikiCampaign, onClick: () -> Unit) {
    val art = rememberGameArt()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = campaign.faction.takeIf { it.isNotBlank() }?.let(::factionColor) ?: SuperEarthYellow
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clickable(onClick = onClick)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 110.dp, height = 64.dp).border(BorderStroke(1.dp, accent.copy(alpha = 0.6f)))) {
                art.wikiImage(campaign.bannerImage, width = 400)?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(64.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(campaign.name.uppercase(), style = MaterialTheme.typography.headlineSmall.glow(accent, 14f), color = accent)
                Text(
                    listOfNotNull(
                        campaign.faction.takeIf { it.isNotBlank() }?.let { "FRONT: ${factionLabel(it).uppercase()}" },
                        campaign.phases.firstOrNull()?.dateStart?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
                Text(
                    if (campaign.succeeded) "UDANA KAMPANIA" else "NIEUDANA KAMPANIA",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (campaign.succeeded) StatusGreen else StatusRed,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    campaign.phases.forEach { p -> Box(Modifier.size(8.dp).clip(CircleShape).background(p.outcome.color(muted))) }
                }
            }
        }
    }
}
