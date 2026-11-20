package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.generator.Callback
import com.vconvertercrew.vconverter.generator.ScriptGenerator
import com.vconvertercrew.vconverter.generator.ScriptKind
import com.vconvertercrew.vconverter.generator.ScriptSpec
import com.vconvertercrew.vconverter.script.NativeScripts
import com.vconvertercrew.vconverter.script.ScriptConverter
import com.vconvertercrew.vconverter.script.ScriptSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeScriptTest {
    @Test
    fun luaBecomesDirectVSliceCode() {
        if (System.getProperty("vconverter.script.library") == null) return
        assertTrue(NativeScripts.available)
        val lua = """
            function onCreate()
              makeLuaSprite('bg', 'stageback', -600, -200)
              addLuaSprite('bg', false)
            end
            function onBeatHit()
              if curBeat % 4 == 0 then triggerEvent('Add Camera Zoom', '0.03', '0.03') end
            end
        """.trimIndent()
        val bundle = ScriptConverter.convert("Spooky", listOf(ScriptSource("stages/spooky.lua", lua)), emptyMap())
        val code = bundle.files.single().content
        assertEquals("scripts/stages/SpookyStage.hxc", bundle.files.single().path)
        assertTrue(code.contains("class SpookyStage extends Stage"))
        assertTrue(code.contains("var bg:FunkinSprite;"))
        assertTrue(code.contains("bg.loadGraphic(Paths.image(\"stageback\"));"))
        assertTrue(code.contains("SongEventRegistry.handleEvent(new SongEventData(Conductor.instance.songPosition, \"AddCameraZoom\""))
        assertFalse(code.contains("psychCallback") || code.contains("function makeSprite"))
        assertTrue(bundle.report.sections.any { section -> section.lines.any { it.contains("V-Script (C++)") } })

        val events = ScriptConverter.eventFiles(listOf("Screen Shake"))
        assertTrue(events.single().content.contains("cameras[i].shake(intensity, duration);"))

        val generated = ScriptGenerator.generate(ScriptSpec(ScriptKind.MODULE, "HudModule", "hud", setOf(Callback.UPDATE, Callback.NOTE_HIT)))
        val text = generated.files.single().text.orEmpty()
        assertTrue(text.contains("super(\"hud\", 1000);"))
        assertTrue(text.contains("if (PlayState.instance == null) return;"))
        assertTrue(text.contains("import funkin.play.PlayState;"))
    }
}
