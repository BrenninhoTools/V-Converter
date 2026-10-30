package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.reverse.HxcToPsych
import com.brenninhotools.vconverter.script.LuaToHScript
import com.brenninhotools.vconverter.script.ScriptConverter
import com.brenninhotools.vconverter.script.ScriptScope
import com.brenninhotools.vconverter.script.ScriptTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptUpgradeTest {
    private val hxc = """
import funkin.graphics.FunkinSprite;
import funkin.play.PlayState;
import funkin.save.Save;
import funkin.util.HapticUtil;
import funkin.FunkinMemory;
import funkin.play.song.Song;

class TestSong extends Song
{
  var CACHE_PATHS(get, never):Array<String>;
  var level(default, set):Int = 0;

  function get_CACHE_PATHS():Array<String>
  {
    return ["a", "b"];
  }

  function set_level(value:Int):Int
  {
    level = value;
    return value;
  }

  public function new()
  {
    super('test');
  }

  function onCreate(event:ScriptEvent):Void
  {
    super.onCreate(event);
    for (snd in CACHE_PATHS)
      FunkinMemory.cacheSound(Paths.sound(snd));
    level = 3;
    var bg:FunkinSprite = FunkinSprite.createSparrow(0, 0, 'shared:stage/bg');
    trace('Level ${'$'}{level}');
    HapticUtil.vibrate(0, 0.1);
    if (!Save.instance.hasBeatenSong('test')) trace('first time');
    PlayState.instance.currentStage.add(bg);
  }

  function onSongRetry(event:ScriptEvent):Void
  {
    trace(event.retryCount);
  }
}
""".trimStart()

    @Test
    fun vsliceScriptsBecomeValidPsychHScript() {
        val result = HxcToPsych.convert("test.hxc", hxc)
        val text = result.files.first().content
        assertTrue(text, text.contains("for (snd in get_CACHE_PATHS())"))
        assertTrue(text, text.contains("null;"))
        assertFalse(text, text.contains("FunkinMemory"))
        assertFalse(text, text.contains("HapticUtil"))
        assertTrue(text, text.contains("set_level(3);"))
        assertTrue(text, text.contains("  level = value;"))
        assertFalse(text, text.contains("(get, never)"))
        assertFalse(text, text.contains("(default, set)"))
        assertTrue(text, text.contains("'Level ${'$'}{level}'"))
        assertFalse(text, text.contains("\\${'$'}"))
        assertTrue(text, text.contains("funkinSparrow(0, 0, 'shared:stage/bg')"))
        assertTrue(text, text.contains("function funkinSparrow("))
        assertTrue(text, text.contains("hasBeatenSong('test')"))
        assertTrue(text, text.contains("function hasBeatenSong("))
        assertTrue(text, text.contains("import backend.Highscore;"))
        assertTrue(result.notes.toString(), result.notes.any { it.startsWith("Line 42: V-Slice callback onSongRetry") })
        assertTrue(result.notes.toString(), result.notes.any { it.startsWith("Line 39:") && it.contains("currentStage") })
        assertTrue(result.notes.toString(), result.notes.any { it.startsWith("Line 10: property CACHE_PATHS") })
    }

    private val lua = """
function onCreate()
    runHaxeCode([[
        import objects.HealthIcon;
        var extraIcon = null;
        function tint(color:Int) {
            game.dad.color = color;
            return color;
        }
        extraIcon = game.iconP2;
    ]])
    runHaxeCode("game.camGame.zoom = \"x\" == \"x\" ? 1.2 : 1;", {target = 3})
    _G.counter = 0
    _G['label'] = 'hi'
    local key = 'dyn'
    _G[key] = 5
    setProperty('bg.color', FlxColor('#FF0000'))
end

function onBeatHit()
    counter = counter + 1
    runHaxeFunction('tint', {0xFF00FF})
    debugPrint(_G[key2], label)
end

function onSectionHit() end
function onKeyPress(key) end
function onGhostTap(key) end
function noteMissPress(key) end
function onGameOverConfirm(retry) end
function onEventPushed(name, v1, v2, time) end
function onNextDialogue(line) end
""".trimStart()

    @Test
    fun luaHaxeCodeGlobalsAndCallbacksConvert() {
        val out = LuaToHScript("TestScript", "test-script", ScriptTarget(ScriptScope.GLOBAL)).convert(lua)
        val text = out.source
        assertTrue(text, text.contains("function hx_tint(color:Int)"))
        assertTrue(text, text.contains("PlayState.instance.currentStage.getDad().color = color;"))
        assertTrue(text, text.contains("hx_extraIcon = PlayState.instance.iconP2;"))
        assertTrue(text, text.contains("var hx_extraIcon:Dynamic = null;"))
        assertTrue(text, text.contains("hx_target = 3;"))
        assertTrue(text, text.contains("PlayState.instance.camGame.zoom = \"x\" == \"x\" ? 1.2 : 1;"))
        assertTrue(text, text.contains("hx_tint(0xFF00FF)") || text.contains("hx_tint(16711935)"))
        assertTrue(text, Regex("\\bcounter = 0;").containsMatchIn(text))
        assertTrue(text, Regex("\\blabel = \"hi\";").containsMatchIn(text))
        assertTrue(text, text.contains("setGlobal(key, 5);"))
        assertTrue(text, text.contains("function global(name:Dynamic):Dynamic"))
        assertTrue(text, text.contains("case \"counter\": return counter;"))
        assertTrue(text, text.contains("var globals:Dynamic = null;"))
        assertTrue(text, text.contains("import haxe.ds.StringMap;"))
        assertTrue(text, text.contains("controls.NOTE_LEFT_P"))
        assertTrue(text, text.contains("if (event.hasMiss) noteMissPress(event.dir); else onGhostTap(event.dir);"))
        assertTrue(text, text.contains("override public function onSongRetry(event:ScriptEvent):Void"))
        assertTrue(text, text.contains("for (data in game.songEvents)"))
        assertTrue(text, text.contains("Conductor.instance.currentMeasure"))
        assertFalse(text, text.contains("event.beat % 4"))
        assertTrue(out.issues.toString(), out.issues.any { it.line == 31 && it.message.contains("onNextDialogue") })
        assertFalse(out.issues.toString(), out.issues.any { it.message.contains("_G is not supported") || it.message.contains("runHaxeCode") && it.message.contains("no V-Slice") })
        assertTrue(out.apiCalls.toString(), "FlxColor" in out.apiCalls)
        assertEquals(ScriptScope.SONG, ScriptConverter.detectTarget("data/songs/my-song/script.lua").scope)
        assertEquals("my-song", ScriptConverter.detectTarget("data/songs/my-song/script.lua").key)
        assertEquals("my-song", ScriptConverter.detectTarget("data/my-song/script.lua").key)
    }
}
