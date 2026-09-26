package com.poldivers.app.data.hd2

import com.poldivers.app.data.hd2.model.Assignment
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Galactic War campaigns ("Counterdissident Hammer", ...) are a narrative layer over Major
 * Orders: each phase is one MO whose title reads like "CAMPAIGN NAME, PHASE 2: PHASE NAME".
 * The API has no campaign object, so we recognise it from the title (English text for matching,
 * localized text for display).
 */
data class CampaignPhase(
    /** Campaign name as shown to the user (localized when the localized title has the same shape). */
    val campaign: String,
    /** Stable English name, used to group phases and to find the wiki page. */
    val campaignKey: String,
    val phase: Int,
    val phaseName: String,
)

private val PHASE_REGEX = Regex(
    """^\s*(.+?)\s*[,:\-–—|]?\s*(?:PHASE|FAZA|PHASE|FASE|PHASE|ФАЗА|阶段|階段)\s*(\d+)\s*(?:[:\-–—|.]\s*(.*))?$""",
    RegexOption.IGNORE_CASE,
)

/** Parses "NAME, PHASE 2: SUBTITLE" (also "NAME - Phase 2", "NAME | PHASE 2 - SUBTITLE"). */
fun parseCampaignTitle(title: String): Triple<String, Int, String>? {
    val line = title.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
    val match = PHASE_REGEX.matchEntire(line) ?: return null
    val name = match.groupValues[1].trim().trimEnd(',', ':', '-', '–', '—', '|').trim()
    val phase = match.groupValues[2].toIntOrNull() ?: return null
    if (name.isBlank()) return null
    return Triple(name, phase, match.groupValues[3].trim())
}

fun campaignPhaseOf(localized: Assignment, english: Assignment?): CampaignPhase? {
    val en = english?.let { parseCampaignTitle(it.title) ?: parseCampaignTitle(it.briefing) }
    val local = parseCampaignTitle(localized.title) ?: parseCampaignTitle(localized.briefing)
    val key = en ?: local ?: return null
    return CampaignPhase(
        campaign = titleCase((local ?: key).first),
        campaignKey = titleCase(key.first),
        phase = key.second,
        phaseName = titleCase((local ?: key).third.ifBlank { key.third }),
    )
}

/** "COUNTERDISSIDENT HAMMER" -> "Counterdissident Hammer" (wiki page titles use this form). */
fun titleCase(text: String): String =
    if (text != text.uppercase()) text else text.lowercase().split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase() }
    }

/**
 * Remembers every campaign phase the app has seen, so the timeline can show earlier phases and
 * how they ended after their Major Order has disappeared from the API. Stored as a small JSON file.
 */
class CampaignHistoryStore(private val file: File?) {

    @Serializable
    data class PhaseRecord(
        val assignmentId: Long,
        val campaign: String,
        val campaignKey: String,
        val phase: Int,
        val phaseName: String,
        val briefing: String = "",
        val reward: String = "",
        val rewardType: Int = 0,
        val rewardAmount: Long = 0,
        val expiration: String = "",
        val firstSeenMs: Long = 0,
        val lastSeenMs: Long = 0,
        val tasksDone: Int = 0,
        val tasksTotal: Int = 0,
        val faction: String = "",
    ) {
        enum class Outcome { ACTIVE, SUCCESS, FAILED }

        /**
         * An order that vanished well before its deadline was completed (the game ends MOs early
         * on success); one still unfinished at its deadline failed.
         */
        fun outcome(nowMs: Long, stillActive: Boolean): Outcome {
            if (stillActive) return Outcome.ACTIVE
            if (tasksTotal > 0 && tasksDone >= tasksTotal) return Outcome.SUCCESS
            val deadline = runCatching { java.time.Instant.parse(expiration).toEpochMilli() }.getOrNull() ?: return Outcome.SUCCESS
            return if (lastSeenMs < deadline - 5 * 60_000) Outcome.SUCCESS else Outcome.FAILED
        }
    }

    private val lock = Any()
    private val records: MutableMap<Long, PhaseRecord> = load()

    fun update(record: PhaseRecord) = synchronized(lock) {
        val previous = records[record.assignmentId]
        records[record.assignmentId] = record.copy(firstSeenMs = previous?.firstSeenMs ?: record.firstSeenMs)
    }

    fun all(): List<PhaseRecord> = synchronized(lock) { records.values.toList() }

    fun phasesOf(campaignKey: String): List<PhaseRecord> = synchronized(lock) {
        records.values.filter { it.campaignKey.equals(campaignKey, ignoreCase = true) }.sortedBy { it.phase }
    }

    fun save() {
        val target = file ?: return
        val snapshot = synchronized(lock) { records.values.toList() }
        runCatching {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(json.encodeToString(snapshot))
            tmp.renameTo(target)
        }
    }

    private fun load(): MutableMap<Long, PhaseRecord> {
        val source = file?.takeIf { it.exists() } ?: return mutableMapOf()
        return runCatching {
            json.decodeFromString<List<PhaseRecord>>(source.readText()).associateBy { it.assignmentId }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
