package com.poldivers.app

import com.poldivers.app.data.hd2.parseCampaignTitle
import com.poldivers.app.data.hd2.titleCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampaignTitleTest {

    @Test
    fun parsesCommaPhaseColon() {
        assertEquals(
            Triple("COUNTERDISSIDENT HAMMER", 1, "DISSIDENT MANHUNT"),
            parseCampaignTitle("COUNTERDISSIDENT HAMMER, PHASE 1: DISSIDENT MANHUNT"),
        )
    }

    @Test
    fun parsesDashAndPolish() {
        assertEquals(Triple("Census Thunder", 2, ""), parseCampaignTitle("Census Thunder - Phase 2"))
        assertEquals(Triple("MŁOT", 3, "BARIERA"), parseCampaignTitle("MŁOT, FAZA 3: BARIERA"))
    }

    @Test
    fun ignoresPlainOrders() {
        assertNull(parseCampaignTitle("MAJOR ORDER"))
        assertNull(parseCampaignTitle(""))
    }

    @Test
    fun titleCasesAllCaps() {
        assertEquals("Counterdissident Hammer", titleCase("COUNTERDISSIDENT HAMMER"))
        assertEquals("Census Thunder", titleCase("Census Thunder"))
    }
}
