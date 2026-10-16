package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.script.ScriptConverter
import com.brenninhotools.vconverter.script.ScriptScope
import com.brenninhotools.vconverter.script.ScriptSource
import com.brenninhotools.vconverter.script.ScriptTarget
import com.brenninhotools.vconverter.stage.LuaStageExtractor
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import com.brenninhotools.vconverter.stage.StageSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaStageTest {
    private val lua = LuaStageTest::class.java.getResourceAsStream("/tank-stage.lua")!!.bufferedReader().readText()
    private val stageJson = """{"directory":"","defaultZoom":0.8,"boyfriend":[810,100],"girlfriend":[200,85],"opponent":[20,100],"camera_boyfriend":[0,0],"camera_opponent":[0,0],"camera_girlfriend":[0,0]}"""

    @Test
    fun luaSpritesBecomeStageProps() {
        val props = LuaStageExtractor.extract(lua)
        assertEquals(listOf("bar", "sniper", "tankguy"), props.behind.map { it.name })
        assertTrue(props.front.isEmpty())
        val stage = LuaStageExtractor.merge(PsychStageParser.parse(stageJson), props)
        val json = JSONObject(PsychToVSliceStage.convert(StageSource("tank", stage)).json)
        val list = json.getJSONArray("props")
        assertEquals(3, list.length())
        val bar = list.getJSONObject(0)
        assertEquals("tankmanBattlefield/erect/bg", bar.getString("assetPath"))
        assertEquals(-985.0, bar.getJSONArray("position").getDouble(0), 0.001)
        assertEquals(1.15, bar.getJSONArray("scale").getDouble(0), 0.001)
        val sniper = list.getJSONObject(1)
        assertEquals("idle", sniper.getString("startingAnimation"))
        assertEquals(listOf("idle", "sip"), (0 until sniper.getJSONArray("animations").length()).map { sniper.getJSONArray("animations").getJSONObject(it).getString("name") })
        assertEquals("Tankmanidlebaked instance 1", sniper.getJSONArray("animations").getJSONObject(0).getString("prefix"))
        val chars = json.getJSONObject("characters")
        assertTrue(chars.getJSONObject("gf").getInt("zIndex") > bar.getInt("zIndex"))
        assertTrue(props.removedLines.containsAll(listOf(3, 4, 5, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18)))
    }

    @Test
    fun stageModuleKeepsOnlyTheLogic() {
        val props = LuaStageExtractor.extract(lua)
        val bundle = ScriptConverter.convert("Tank", listOf(ScriptSource("stages/tank.lua", lua, skipLines = props.removedLines)), emptyMap())
        val code = bundle.files.single().content
        assertFalse(code.contains("makeSprite(\"bar\""))
        assertFalse(code.contains("makeAnimatedSprite(\"sniper\""))
        assertTrue(code.contains("playAnim(\"sniper\", \"sip\", true)"))
        assertFalse(code.contains("Reflect.callMethod(null"))
    }

    @Test
    fun scriptsFolderKeepsItsName() {
        val bundle = ScriptConverter.convert(
            "My Mod",
            listOf(ScriptSource("scripts/ExtraEventsSaveHandler.lua", "function onCreate() debugPrint('hi') end"), ScriptSource("scripts/my hud.hx", "function onCreatePost() {}")),
            emptyMap()
        )
        assertEquals(listOf("scripts/modules/ExtraEventsSaveHandler.hxc", "scripts/modules/my_hud.hxc"), bundle.files.map { it.path })
        val code = bundle.files[0].content
        assertTrue(code.contains("class ExtraEventsSaveHandler extends Module"))
        assertTrue(code.contains("super(\"ExtraEventsSaveHandler\", 1100);"))
        assertTrue(bundle.files[1].content.contains("class My_hud extends Module"))
        assertTrue(bundle.files[1].content.contains("super(\"my hud\", 1100);"))
    }
}
