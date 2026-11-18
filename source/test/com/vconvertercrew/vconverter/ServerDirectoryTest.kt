package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.online.Online
import com.vconvertercrew.vconverter.online.ServerDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDirectoryTest {
    @Test
    fun parsesObjectsStringsAndArrays() {
        val list = ServerDirectory.parse("""{"servers":[{"url":"a.example.com/"},"https://b.example.com",{"url":"c.example.com","enabled":false},""]}""")
        assertEquals(listOf("https://a.example.com", "https://b.example.com"), list)
        assertEquals(listOf("http://local:3000"), ServerDirectory.parse("""["http://local:3000","http://local:3000"]"""))
        assertTrue(ServerDirectory.parse("not json").isEmpty())
    }

    @Test
    fun picksTheFastestServerThatAnswers() {
        val times = mapOf("https://a" to 300L, "https://b" to null, "https://c" to 120L)
        assertEquals("https://c", ServerDirectory.pick(times.keys.toList()) { times[it] })
        assertNull(ServerDirectory.pick(listOf("https://b")) { null })
    }

    @Test
    fun typedServerWinsOverTheAutomaticOne() {
        val saved = Online.serverUrl to Online.autoServer
        try {
            Online.serverUrl = ""
            Online.autoServer = "https://auto.example.com"
            assertTrue(Online.isConfigured && Online.isAutomatic)
            assertEquals("https://auto.example.com", Online.activeUrl)
            Online.serverUrl = "mine.example.com"
            assertEquals("https://mine.example.com", Online.activeUrl)
        } finally {
            Online.serverUrl = saved.first
            Online.autoServer = saved.second
        }
    }
}
