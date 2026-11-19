package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.skin.IntroAction
import com.vconvertercrew.vconverter.skin.SkinAnchor
import com.vconvertercrew.vconverter.skin.SkinJson
import com.vconvertercrew.vconverter.skin.TitleDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSkinTest {
    private fun asset(path: String): String = javaClass.classLoader!!.getResourceAsStream(path)!!.readBytes().decodeToString()

    @Test
    fun bundledTitleMatchesTheBuiltInLook() {
        val skin = SkinJson.title(asset(SkinJson.TITLE))
        assertEquals(102.0, skin.bpm, 0.0)
        assertEquals(TitleDefaults.events.size, skin.events.size)
        assertEquals(listOf("gf", "logo", "enter"), skin.sprites.map { it.id })
        val gf = skin.sprites[0]
        assertEquals(512f, gf.x)
        assertEquals(2f, gf.scaleX)
        assertEquals(listOf("danceLeft", "danceRight"), gf.beat)
        assertNull(gf.play)
        assertEquals(30, gf.animation("danceLeft")!!.indices!!.first())
        assertEquals("idle", skin.sprites[2].play)
        assertEquals("pressed", skin.sprites[2].pressed)
        assertEquals("images/ui/title/logoBumpin.xml", skin.sprites[1].xmlPath)
    }

    @Test
    fun introEventsBuildTheText() {
        val skin = SkinJson.title(asset(SkinJson.TITLE))
        val wacky = listOf("shoutouts to", "tom fulp")
        assertEquals(listOf("brenninho", "vconverter crew", "present"), skin.intro(3, wacky).lines)
        assertTrue(skin.intro(4, wacky).lines.isEmpty())
        assertEquals(listOf("shoutouts to", "tom fulp"), skin.intro(11, wacky).lines)
        assertEquals(listOf("friday", "night", "converter"), skin.intro(15, wacky).lines)
        val custom = SkinJson.title("""{"intro":{"events":[{"beat":0,"action":"set","text":"{wacky}"},{"beat":1,"action":"show","text":["ng"]},{"beat":2,"action":"hide","text":"ng"},{"beat":2,"action":"bogus"}]}}""")
        assertEquals(3, custom.events.size)
        assertEquals(listOf("shoutouts to tom fulp"), custom.intro(0, wacky).lines)
        assertEquals(setOf("ng"), custom.intro(1, wacky).sprites)
        assertTrue(custom.intro(2, wacky).sprites.isEmpty())
        assertEquals(IntroAction.HIDE, custom.events.last().action)
    }

    @Test
    fun mainMenuOverridesAndOrder() {
        val bundled = SkinJson.mainMenu(asset(SkinJson.MAIN_MENU))
        assertEquals(160f, bundled.spacing)
        assertEquals("images/ui/menu/menuBGMagenta.png", bundled.flash!!.pngPath)
        assertEquals("online white", bundled.selectedFor("online", "online"))
        val custom = SkinJson.mainMenu(
            """{"flash":null,"order":["options","online"],"hidden":["converter"],
               "items":{"overrides":{"online":{"image":"images/custom/net","idle":"{id} idle","offset":[10,-4],"scale":1.5}}},
               "version":{"text":"V {version}","anchor":"top-right","shadow":false,"color":"#80FF0000"},
               "sprites":[{"image":"images/x","layer":-1,"scale":[2,3],"animation":{"prefix":"spin","fps":12}}]}"""
        )
        assertNull(custom.flash)
        assertEquals(listOf("options", "online", "generator"), custom.arrange(listOf("converter", "generator", "online", "options")))
        assertEquals("images/custom/net", custom.itemPath("online"))
        assertEquals("images/ui/menu/main/options", custom.itemPath("options"))
        assertEquals("online idle", custom.idleFor("online", "ONLINE"))
        assertEquals(1.5f, custom.style("online").scale)
        assertEquals(SkinAnchor.TOP_RIGHT, custom.version.anchor)
        assertNull(custom.version.shadow)
        assertEquals(0x80FF0000.toInt(), custom.version.color)
        assertEquals("V 0.1.0", custom.version.format(mapOf("version" to "0.1.0")))
        val sprite = custom.sprites.single()
        assertEquals(-1, sprite.layer)
        assertEquals(3f, sprite.scaleY)
        assertEquals("idle", sprite.play)
        assertEquals(12.0, sprite.animations.single().fps, 0.0)
    }

    @Test
    fun backButtonAndFallbacks() {
        val back = SkinJson.backButton(asset(SkinJson.BACK_BUTTON))
        assertEquals(SkinAnchor.BOTTOM_RIGHT, back.anchor)
        assertEquals(0.6f, back.scale)
        assertEquals(41L, back.frameMillis)
        assertEquals("back", SkinJson.backButton("{ broken").prefix)
        assertEquals(3, SkinJson.title(null).sprites.size)
        val (left, top) = SkinAnchor.BOTTOM_RIGHT.place(0f, 0f, 1000f, 500f, 24f, 24f, 100f, 50f, 1f)
        assertEquals(876f, left)
        assertEquals(426f, top)
        assertEquals(0, TitleDefaults.sprites[1].animation("bump")!!.frameIndex(0.0, 10))
        assertEquals(9, TitleDefaults.sprites[1].animation("bump")!!.frameIndex(5.0, 10))
        assertEquals(1, TitleDefaults.sprites[2].animation("idle")!!.frameIndex(11 / 24.0, 10))
    }
}
