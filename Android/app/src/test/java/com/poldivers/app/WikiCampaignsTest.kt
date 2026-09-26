package com.poldivers.app

import com.poldivers.app.data.wiki.Outcome
import com.poldivers.app.data.wiki.parseCampaigns
import com.poldivers.app.data.wiki.toGameMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiCampaignsTest {

    /** Snapshot of helldivers.wiki.gg/wiki/Campaigns (wiki dump 2026-09-09). */
    private val wikitext = javaClass.classLoader!!.getResource("campaigns_wikitext.txt")!!.readText()

    @Test
    fun parsesAllCampaignsFromTheRealPage() {
        val campaigns = parseCampaigns(wikitext)
        val names = campaigns.map { it.name }
        assertTrue(names.containsAll(listOf("Census Thunder", "Counterdissident Hammer", "Blazing Electorate")))
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun campaignFieldsAndPhases() {
        val census = parseCampaigns(wikitext).first { it.name == "Census Thunder" }
        assertEquals("Illuminate", census.faction)
        assertEquals("stratagem", census.rewardType)
        assertTrue(census.rewardText.contains("M-103 Supply FRV"))
        assertTrue(census.phases.size >= 2)
        val first = census.phases.first()
        assertEquals("Containment", first.name)
        assertEquals(Outcome.FAILURE, first.outcome)
        assertEquals("medal", first.rewardType)
        assertEquals(30, first.rewardAmount)
        assertTrue(first.briefing.contains("SANGIS"))
        assertFalse(first.briefing.contains("[["))
    }

    @Test
    fun activeCampaignDetected() {
        val blazing = parseCampaigns(wikitext).first { it.name == "Blazing Electorate" }
        assertTrue(blazing.isActive)
        assertEquals(Outcome.IN_PROGRESS, blazing.phases.first().outcome)
    }

    @Test
    fun wikitextToGameMarkup() {
        assertEquals(
            "Liberate <i=1>SANGIS</i> now.\nNext line",
            toGameMarkup("Liberate [[Sangis|SANGIS]] now.<br>Next line<ref>x</ref>"),
        )
    }
}
