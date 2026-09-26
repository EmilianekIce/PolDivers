package com.poldivers.app.data.wiki

/**
 * Galactic War campaigns as documented on helldivers.wiki.gg/wiki/Campaigns, where each campaign
 * is a `{{Campaign ...}}` template holding up to three `{{Campaign MO ...}}` phases. The game API
 * has no campaign data at all; the wiki is the community's record (the same data campaign
 * trackers show).
 */
data class WikiCampaign(
    val name: String,
    val description: String,
    val bannerImage: String?,
    val rewardType: String,
    val rewardAmount: Int,
    val rewardText: String,
    /** API faction key: Terminids / Automaton / Illuminate / Humans, or "" when unknown. */
    val faction: String,
    val phases: List<WikiCampaignPhase>,
) {
    val isActive: Boolean get() = phases.any { it.outcome == Outcome.IN_PROGRESS }
    val successes: Int get() = phases.count { it.outcome == Outcome.SUCCESS }
    /** Campaigns succeed when the majority of their Major Orders do. */
    val succeeded: Boolean get() = successes * 2 > phases.size
}

enum class Outcome { SUCCESS, FAILURE, IN_PROGRESS, UNKNOWN }

data class WikiCampaignPhase(
    val number: Int,
    val name: String,
    val outcome: Outcome,
    val image: String?,
    val dateStart: String,
    val dateEnd: String,
    val briefing: String,
    val debrief: String,
    val objective: String,
    val rewardType: String,
    val rewardAmount: Int,
)

fun parseCampaigns(wikitext: String): List<WikiCampaign> =
    topLevelTemplates(wikitext)
        .filter { it.first.equals("Campaign", ignoreCase = true) }
        .mapNotNull { (_, params) -> toCampaign(params) }
        // The page lists some campaigns twice (overview + per-campaign section): keep one each.
        .distinctBy { it.name.lowercase() }

private fun toCampaign(p: Map<String, String>): WikiCampaign? {
    val name = cleanWikitext(p["name"].orEmpty()).trim()
    if (name.isBlank()) return null
    val phases = (1..6).mapNotNull { n ->
        val raw = p["${n}_mo"] ?: return@mapNotNull null
        val mo = topLevelTemplates(raw).firstOrNull { it.first.equals("Campaign MO", ignoreCase = true) }?.second
            ?: return@mapNotNull null
        WikiCampaignPhase(
            number = n,
            name = cleanWikitext(mo["name"].orEmpty()).trim(),
            outcome = when (mo["outcome"].orEmpty().trim().lowercase()) {
                "success", "successful", "victory" -> Outcome.SUCCESS
                "failure", "failed", "fail", "defeat" -> Outcome.FAILURE
                "in-progress", "in-proggress", "in progress", "ongoing", "active" -> Outcome.IN_PROGRESS
                else -> if (mo["date_end"].isNullOrBlank()) Outcome.IN_PROGRESS else Outcome.UNKNOWN
            },
            image = mo["image"]?.trim()?.takeIf { it.isNotBlank() },
            dateStart = cleanWikitext(mo["date_start"].orEmpty()).trim(),
            dateEnd = cleanWikitext(mo["date_end"].orEmpty()).trim(),
            briefing = toGameMarkup(mo["briefing"].orEmpty()),
            debrief = toGameMarkup(mo["debrief"].orEmpty()),
            objective = toGameMarkup(mo["objective"].orEmpty()),
            rewardType = mo["reward_type"].orEmpty().trim().lowercase(),
            rewardAmount = mo["reward_amount"]?.trim()?.toIntOrNull() ?: 0,
        )
    }
    return WikiCampaign(
        name = name,
        description = toGameMarkup(p["description"].orEmpty()),
        bannerImage = p["banner_image"]?.trim()?.takeIf { it.isNotBlank() },
        rewardType = p["reward_type"].orEmpty().trim().lowercase(),
        rewardAmount = p["reward_amount"]?.trim()?.toIntOrNull() ?: 0,
        rewardText = cleanWikitext(p["reward_text"].orEmpty()).trim(),
        faction = when (p["faction"].orEmpty().trim().lowercase()) {
            "terminid", "terminids" -> "Terminids"
            "automaton", "automatons" -> "Automaton"
            "illuminate" -> "Illuminate"
            "human", "humans", "super earth" -> "Humans"
            else -> ""
        },
        phases = phases,
    )
}

/** Templates at nesting depth 0 of [text]: name + named parameters. */
internal fun topLevelTemplates(text: String): List<Pair<String, Map<String, String>>> {
    val out = mutableListOf<Pair<String, Map<String, String>>>()
    var i = 0
    while (true) {
        val start = text.indexOf("{{", i)
        if (start < 0) break
        val end = matchingClose(text, start)
        if (end < 0) break
        val body = text.substring(start + 2, end)
        val parts = splitTopLevel(body)
        val name = parts.first().trim()
        val params = parts.drop(1).mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).trim().lowercase() to part.substring(eq + 1).trim()
        }.toMap()
        out += name to params
        i = end + 2
    }
    return out
}

/** Index of the "}}" closing the "{{" at [start], honouring nesting. */
private fun matchingClose(text: String, start: Int): Int {
    var depth = 0
    var i = start
    while (i < text.length - 1) {
        when {
            text.startsWith("{{", i) -> { depth++; i += 2 }
            text.startsWith("}}", i) -> {
                depth--
                if (depth == 0) return i
                i += 2
            }
            else -> i++
        }
    }
    return -1
}

/** Splits a template body on '|' that are not inside nested {{ }} or [[ ]]. */
private fun splitTopLevel(body: String): List<String> {
    val parts = mutableListOf<String>()
    var braces = 0
    var brackets = 0
    var last = 0
    var i = 0
    while (i < body.length) {
        when {
            body.startsWith("{{", i) -> { braces++; i += 2; continue }
            body.startsWith("}}", i) -> { braces--; i += 2; continue }
            body.startsWith("[[", i) -> { brackets++; i += 2; continue }
            body.startsWith("]]", i) -> { brackets--; i += 2; continue }
            body[i] == '|' && braces == 0 && brackets == 0 -> {
                parts += body.substring(last, i)
                last = i + 1
            }
        }
        i++
    }
    parts += body.substring(last)
    return parts
}

/** Wikitext -> plain text (links keep their label). */
fun cleanWikitext(text: String): String = toGameMarkup(text).replace(Regex("""</?i(=\d)?>"""), "")

/**
 * Wikitext -> the game's own light markup, rendered by the app like MO briefings: links and bold
 * become highlighted runs (<i=1>..</i>), <br> becomes a line break, refs/files/templates vanish.
 */
fun toGameMarkup(text: String): String {
    var s = text
    s = s.replace(Regex("""(?s)<ref[^>]*/>"""), "")
    s = s.replace(Regex("""(?s)<ref[^>]*>.*?</ref>"""), "")
    s = s.replace(Regex("""(?i)<br\s*/?>"""), "\n")
    s = s.replace(Regex("""\[\[(?:File|Image|Plik):[^\]]*]]""", RegexOption.IGNORE_CASE), "")
    // {{Currency|Medals}} / {{Icon|X}} style templates -> their last argument.
    repeat(3) {
        s = s.replace(Regex("""\{\{[^{}|]*\|([^{}]*?)}}""")) { m -> m.groupValues[1].substringAfterLast('|') }
        s = s.replace(Regex("""\{\{[^{}]*}}"""), "")
    }
    s = s.replace(Regex("""\[\[[^\]|]*\|([^\]]*)]]""")) { "<i=1>${it.groupValues[1]}</i>" }
    s = s.replace(Regex("""\[\[([^\]]*)]]""")) { "<i=1>${it.groupValues[1]}</i>" }
    s = s.replace(Regex("""\[https?://\S+ ([^\]]*)]"""), "$1")
    s = s.replace(Regex("""'''(.*?)'''""")) { "<i=1>${it.groupValues[1]}</i>" }
    s = s.replace("''", "")
    s = s.replace(Regex("""<(?!/?i(=\d)?>)[^>]+>"""), "")
    // A bold run wrapping links produces nested highlight tags; flatten them.
    s = s.replace(Regex("""(<i=1>)+"""), "<i=1>").replace(Regex("""(</i>)+"""), "</i>")
    return s.lines().joinToString("\n") { it.trim() }.replace(Regex("""\n{3,}"""), "\n\n").trim()
}
