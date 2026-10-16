package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.ConversionOptions
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.script.LuaPattern
import com.brenninhotools.vconverter.script.LuaToHScript
import com.brenninhotools.vconverter.script.PsychHScriptWrapper
import com.brenninhotools.vconverter.script.ScriptConverter
import com.brenninhotools.vconverter.script.ScriptScope
import com.brenninhotools.vconverter.script.ScriptSource
import com.brenninhotools.vconverter.script.ScriptTarget
import com.brenninhotools.vconverter.script.lua.LuaParser
import com.brenninhotools.vconverter.script.lua.LuaSyntaxException
import com.brenninhotools.vconverter.script.lua.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScriptConversionTest {
    private val lua = """
        local count = 0
        local names = {'a', 'b', 'c'}

        function onCreate()
            makeLuaSprite('bg', 'stageback', -600, -200)
            setScrollFactor('bg', 0.9, 0.9)
            addLuaSprite('bg', false)
        end

        function onBeatHit()
            count = count + 1
            if curBeat % 4 == 0 and not lowQuality then
                doTweenAlpha('fade', 'bg', 0.5, crochet / 1000, 'quadOut')
            end
            for i = 1, #names do
                setProperty('bg.x', i * 10)
            end
        end

        function goodNoteHit(id, direction, noteType, isSustainNote)
            if noteType == 'Ghost Note' then return Function_Stop end
        end
    """.trimIndent()

    @Test
    fun detectsScopeFromPsychFolders() {
        assertEquals(ScriptTarget(ScriptScope.SONG, "bopeebo"), ScriptConverter.detectTarget("data/Bopeebo/script.lua"))
        assertEquals(ScriptTarget(ScriptScope.STAGE, "club"), ScriptConverter.detectTarget("mods/x/stages/club.lua"))
        assertEquals(ScriptTarget(ScriptScope.EVENT, "Flash Camera"), ScriptConverter.detectTarget("custom_events/Flash Camera.lua"))
        assertEquals(ScriptTarget(ScriptScope.NOTE_TYPE, "Ghost Note"), ScriptConverter.detectTarget("custom_notetypes/Ghost Note.lua"))
        assertEquals(ScriptScope.GLOBAL, ScriptConverter.detectTarget("scripts/hud.hx").scope)
    }

    @Test
    fun parsesLuaAndReportsSyntaxErrors() {
        val chunk = LuaParser.parse(lua)
        assertEquals(5, chunk.size)
        assertTrue(chunk[2] is Stat.FunctionDecl)
        try {
            LuaParser.parse("function broken(\n  x = \nend")
            fail("expected a syntax error")
        } catch (e: LuaSyntaxException) {
            assertTrue(e.line >= 1)
        }
    }

    @Test
    fun luaBecomesScopedModule() {
        val result = LuaToHScript("TestClubStage", "test-club-stage", ScriptTarget(ScriptScope.STAGE, "club")).convert(lua)
        val code = result.source
        assertTrue(code.contains("class TestClubStage extends Module"))
        assertTrue(code.contains("super(\"test-club-stage\""))
        assertTrue(code.contains("makeSprite(\"bg\", \"stageback\", -600, -200)"))
        assertTrue(code.contains("function makeSprite(?a0:Dynamic, ?a1:Dynamic, ?a2:Dynamic, ?a3:Dynamic)"))
        assertTrue(code.contains("var count:Dynamic"))
        assertTrue(code.contains("function goodNoteHit("))
        assertTrue(code.contains("function onBeatHitScript("))
        assertTrue(code.contains("return CANCEL;"))
        assertTrue(code.contains("var CANCEL:String = \"cancel\";"))
        assertNoLua(code)
        assertTrue(code.contains("currentStageId"))
        assertTrue(code.contains("override public function onBeatHit"))
        assertTrue(code.contains("override public function onNoteHit"))
        assertFalse(code.contains("trace("))
        assertFalse(code.contains("StringTools"))
        assertEquals(setOf("onCreate", "onBeatHit", "goodNoteHit"), result.callbacks.toSet())
        assertTrue("doTweenAlpha" in result.apiCalls)
        assertEquals(code.count { it == '{' }, code.count { it == '}' })
    }

    @Test
    fun unsupportedLuaIsReportedNotDropped() {
        val result = LuaToHScript("A", "a", ScriptTarget(ScriptScope.GLOBAL)).convert(
            "function onCreate()\n  setmetatable({}, {})\n  runHaxeCode('trace(1)')\nend"
        )
        assertTrue(result.source.isNotEmpty())
        assertTrue(result.issues.isNotEmpty())
        assertTrue("runHaxeCode" in result.apiCalls)
    }

    @Test
    fun luaPatternsBecomeRegex() {
        val trim = Regex(LuaPattern.toRegex("^%s*(.-)%s*$")!!)
        assertEquals("hi there", trim.find("  hi there \n")!!.groupValues[1])
        assertEquals(listOf("12", "7"), Regex(LuaPattern.toRegex("%d+")!!).findAll("a12b7").map { it.value }.toList())
        assertNull(LuaPattern.toRegex("%b()"))
    }

    @Test
    fun psychHScriptIsWrapped() {
        val source = """
            import backend.ClientPrefs;
            import flixel.text.FlxText;
            var label:FlxText;
            function onCreatePost() {
                label = new FlxText(0, 0, 0, "hi", 16);
                label.cameras = [game.camHUD];
                add(label);
                game.boyfriend.alpha = 0.5;
            }
            function onBeatHit() {
                if (curBeat % 2 == 0) game.defaultCamZoom += 0.01;
            }
        """.trimIndent()
        val result = PsychHScriptWrapper("THudModule", "t-hud-module", ScriptTarget(ScriptScope.GLOBAL)).convert(source)
        val code = result.source
        assertNoLua(code)
        assertTrue(code.contains("label = new FlxText("))
        assertTrue(code.contains("function onCreatePostScript()") || code.contains("function onCreatePost()"))
        assertTrue(code.contains("import flixel.text.FlxText;"))
        assertFalse(code.contains("backend.ClientPrefs"))
        assertTrue(code.contains("currentStage.getBoyfriend().alpha = 0.5"))
        assertTrue(code.contains("currentCameraZoom += 0.01"))
        assertTrue(code.contains("Conductor.instance.currentBeat"))
        assertTrue(result.issues.any { it.message.contains("ClientPrefs") })
    }

    @Test
    fun bundleIncludesRuntimeAndUniqueClasses() {
        val bundle = ScriptConverter.convert(
            "Test",
            listOf(
                ScriptSource("data/bopeebo/script.lua", "function onCreate() end"),
                ScriptSource("data/fresh/script.lua", "function onCreate() end"),
                ScriptSource("custom_notetypes/Ghost Note.lua", "function goodNoteHit() end")
            ),
            ScriptConverter.noteTypeMap(listOf("Ghost Note"))
        )
        assertEquals(3, bundle.converted)
        val names = bundle.files.map { it.path.substringAfterLast('/') }
        assertEquals(listOf("TestBopeeboScript.hxc", "TestFreshScript.hxc", "TestGhostNoteNoteType.hxc"), names)
        assertTrue(bundle.files.all { it.path.startsWith("scripts/modules/") })
        val noteType = bundle.files.first { it.path.endsWith("TestGhostNoteNoteType.hxc") }.content
        assertTrue(noteType.contains("\"Ghost Note\""))
        assertTrue(noteType.contains("function songUsesNoteType"))
        assertTrue(noteType.contains("super(\"test-ghost-note-note-type\""))
        bundle.files.forEach { assertNoLua(it.content) }
    }

    @Test
    fun unknownChartEventsAreKeptAsPsychEvents() {
        val chart = """{"song":{"song":"T","bpm":120,"speed":1,"needsVoices":false,"player1":"bf","player2":"dad","format":"psych_v1",
            "notes":[{"sectionBeats":4,"mustHitSection":true,"sectionNotes":[]}],
            "events":[[1000,[["Flash Camera","0.5",""],["Add Camera Zoom","0.1","0.05"]]]]}}"""
        val conversion = PsychToVSliceChart.convert(listOf(ChartSource("normal", PsychChartParser.parse(chart))), ConversionOptions())
        val kept = conversion.chart.events.filter { it.kind == "FlashCamera" || it.kind == "AddCameraZoom" }
        assertEquals(2, kept.size)
        assertEquals("0.5", kept.first { it.kind == "FlashCamera" }.value["value1"])
        val events = ScriptConverter.eventFiles(conversion.report.unsupportedEvents.keys)
        assertEquals(listOf("scripts/events/AddCameraZoomEvent.hxc"), events.map { it.path })
        assertTrue(events.single().content.contains("extends SongEvent"))
        assertTrue(events.single().content.contains("super(\"AddCameraZoom\")"))
        assertTrue(events.single().content.contains("return \"Add Camera Zoom\";"))
        assertEquals(kept.size, conversion.report.keptPsychEvents)
        assertEquals(1, conversion.report.unsupportedEvents["Add Camera Zoom"])
    }

    @Test
    fun customEventScriptGoesToScriptsEventsWithRenamedId() {
        val bundle = ScriptConverter.convert(
            "Test",
            listOf(ScriptSource("custom_events/Flash Camera.lua", "function onEvent(name, v1, v2)\n  if name == 'Flash Camera' then cameraFlash('game', 'FFFFFF', tonumber(v1)) end\nend")),
            emptyMap(),
            listOf("Flash Camera")
        )
        val file = bundle.files.single()
        assertEquals("scripts/events/FlashCameraEvent.hxc", file.path)
        val text = file.content
        assertTrue(text.contains("class FlashCameraEvent extends SongEvent"))
        assertTrue(text.contains("super(\"FlashCamera\")"))
        assertTrue(text.contains("return \"Flash Camera\";"))
        assertTrue(text.contains("class FlashCameraEventHandler extends Module"))
        assertTrue(text.contains("if (data.eventKind == \"FlashCamera\") return;"))
        assertTrue(text.contains("[\"FlashCamera\", \"Flash Camera\"]"))
        assertEquals(1, Regex("^import funkin.play.event.SongEvent;", RegexOption.MULTILINE).findAll(text).count())
    }

    @Test
    fun eventIdsAvoidVSliceBuiltIns() {
        assertEquals("FlashCamera", com.brenninhotools.vconverter.chart.PsychEventIds.idFor("Flash Camera"))
        assertEquals("PsychFocusCamera", com.brenninhotools.vconverter.chart.PsychEventIds.idFor("Focus Camera"))
        assertEquals("Hey", com.brenninhotools.vconverter.chart.PsychEventIds.idFor("Hey!"))
    }

    private fun assertNoLua(code: String) {
        val blanked = code.replace(Regex("\"(?:[^\"\\\\]|\\\\.)*\""), "\"\"")
        listOf("\\blua[A-Z]\\w*", "\\bLua\\w*", "\\bLUA_\\w*", "\\bFunction_\\w+", "\\bpsych\\w*\\(", "\\b[glh]_[A-Za-z]\\w*", "PSYCHLUA").forEach { pattern ->
            val hit = Regex(pattern).find(blanked)
            assertTrue("Found Lua leftover '${hit?.value}'", hit == null)
        }
    }
}
