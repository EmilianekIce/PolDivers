package com.poldivers.app

import com.poldivers.app.feature.news.parseDispatchMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DispatchTextTest {

    @Test
    fun splitsMarkedUpHeadlineFromBody() {
        val content = parseDispatchMessage("<i=3>ALARM STRATEGICZNY</i>\nAutomatony atakują <i=1>Malevelon Creek</i>.")
        assertEquals("ALARM STRATEGICZNY", content.headline)
        assertEquals("Automatony atakują Malevelon Creek.", content.plainBody)
        assertEquals(1, content.body.single { it.text == "Malevelon Creek" }.style)
    }

    @Test
    fun keepsPlainMessageWithoutHeadline() {
        val content = parseDispatchMessage("Helldiverzy, dobra robota na froncie.\nDalej tak.")
        assertNull(content.headline)
        assertEquals("Helldiverzy, dobra robota na froncie.\nDalej tak.", content.plainBody)
    }

    @Test
    fun neverShowsRawTags() {
        val content = parseDispatchMessage("<i=1>A</i> <i=3>B</i> C")
        assertEquals("A B C", content.plainBody)
    }
}
