package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.online.DiscordProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscordProfileTest {
    @Test
    fun profileSurvivesSaving() {
        val profile = DiscordProfile("80351110224678912", "brenninho", "Brenninho", "https://cdn.discordapp.com/avatars/1/a.png?size=128")
        assertEquals(profile, DiscordProfile.parse(profile.toJson()))
        assertEquals("Brenninho", profile.name)
        assertEquals("brenninho", profile.copy(displayName = "").name)
    }

    @Test
    fun brokenOrEmptyProfilesAreIgnored() {
        assertNull(DiscordProfile.parse(""))
        assertNull(DiscordProfile.parse(null))
        assertNull(DiscordProfile.parse("{not json"))
        assertNull(DiscordProfile.parse("""{"username":"nobody"}"""))
    }
}
