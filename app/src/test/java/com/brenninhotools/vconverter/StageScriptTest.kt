package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.reverse.HxcToPsych
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.reverse.VSliceToPsychStage
import com.brenninhotools.vconverter.script.PsychNativeLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageScriptTest {
    private val stage = """
import flixel.FlxSprite;
import funkin.play.stage.Stage;
import funkin.play.character.BaseCharacter;
import funkin.play.character.CharacterType;
import funkin.graphics.shaders.AdjustColorShader;
import funkin.util.HapticUtil;
import funkin.util.Sequence;
import funkin.FunkinMemory;

class CarStage extends Stage
{
  function new()
  {
    super('carStage');
  }

  var colorShader:AdjustColorShader;
  var carSequence:Sequence;
  var carData:Array<SequenceData>;
  var mist:FlxSprite;

  function onCreate(event:ScriptEvent)
  {
    super.onCreate(event);
    for (snd in ['carPass0', 'carPass1'])
      FunkinMemory.cacheSound(Paths.sound(snd, "week4"));
    FunkinMemory.purgeCache();
  }

  override function buildStage()
  {
    super.buildStage();
    colorShader = new AdjustColorShader();
    colorShader.hue = -30;
    colorShader.brightness = -30;
    mist = new FlxSprite().loadGraphic(Paths.image('limo/mist', 'week4'));
    mist.zIndex = 400;
    add(mist);
    this.refresh();
    trace(' WARNING '.bg_yellow().bold() + ' ready');
    carData = [{time: 1, callback: () -> trace('car')}];
  }

  override function addCharacter(character:BaseCharacter, charType:CharacterType):Void
  {
    super.addCharacter(character, charType);
    switch (charType)
    {
      case CharacterType.BF:
        character.shader = colorShader;
      default:
    }
  }

  override function onBeatHit(event:SongTimeScriptEvent)
  {
    super.onBeatHit(event);
    carSequence = new Sequence(carData);
    if (HapticUtil.hapticsAvailable) HapticUtil.vibrate(0, 0.1);
  }

  override function onPause(event:PauseScriptEvent)
  {
    super.onPause(event);
    if (carSequence != null) carSequence.running = false;
  }

  override function onCountdownEnd(event:CountdownScriptEvent)
  {
    super.onCountdownEnd(event);
    getBoyfriend().zIndex += 10;
    refresh();
  }

  override function fetchAssetPaths():Array<String>
  {
    var results:Array<String> = super.fetchAssetPaths();
    results.push(Paths.image('limo/mist'));
    return results;
  }
}
""".trimStart()

    private val stageJson = """
{"version": "1.0.0", "name": "Car", "cameraZoom": 0.9, "props": [
  {"name": "sky", "assetPath": "limo/sky", "position": [0, 0], "zIndex": 10},
  {"name": "car", "assetPath": "limo/car", "position": [0, 0], "zIndex": 350}
], "characters": {"bf": {"zIndex": 300, "position": [0, 0]}, "dad": {"zIndex": 200, "position": [0, 0]}, "gf": {"zIndex": 100, "position": [0, 0]}}}
""".trimStart()

    private fun convert(): String {
        val layers = VSliceToPsychStage.layers(VSliceParser.stage(stageJson))
        return HxcToPsych.convert("CarStage.hxc", stage) { if (it == "carStage") layers else null }.files.first().content
    }

    @Test
    fun stageLayersFollowTheStageJson() {
        val layers = VSliceToPsychStage.layers(VSliceParser.stage(stageJson))
        assertEquals(listOf("sky", "gfGroup", "dadGroup", "boyfriendGroup", "car"), layers.keys.toList())
    }

    @Test
    fun buildStageRunsInOnCreate() {
        val text = convert()
        assertEquals(1, Regex("function onCreate\\(").findAll(text).count())
        assertFalse(text, text.contains("function buildStage"))
        val create = text.substringAfter("function onCreate()").substringBefore("\n}\n")
        assertTrue(create, create.indexOf("newAdjustColorShader()") < create.indexOf("Paths.sound(snd);"))
        assertTrue(create, create.contains("precacheAssets();"))
        assertTrue(create, create.contains("addCharacter(boyfriend, 'bf');"))
        assertTrue(create, create.contains("if (gf != null) addCharacter(gf, 'gf');"))
        assertTrue(text, text.contains("function addCharacter(character:Character, charType:String)"))
        assertTrue(text, text.contains("case 'bf':"))
    }

    @Test
    fun memoryHapticsAndLibrariesBecomePsych() {
        val text = convert()
        assertFalse(text, text.contains("FunkinMemory"))
        assertFalse(text, text.contains("HapticUtil"))
        assertFalse(text, text.contains("purgeCache"))
        assertTrue(text, text.contains("Paths.sound(snd);"))
        assertTrue(text, text.contains("Paths.image('limo/mist')"))
        assertFalse(text, text.contains("'week4'"))
        assertFalse(text, text.contains("\"week4\""))
        assertTrue(text, text.contains("trace(' WARNING ' + ' ready');"))
        assertTrue(text, text.contains("if (false) null;"))
    }

    @Test
    fun zIndexSequenceAndShadersGetHelpers() {
        val text = convert()
        assertTrue(text, text.contains("setZIndex(mist, 400);"))
        assertTrue(text, text.contains("game.add(mist);"))
        assertTrue(text, text.contains("setZIndex(boyfriend, getZIndex(boyfriend) + (10));"))
        assertTrue(text, text.contains("refreshZ();"))
        assertTrue(text, text.contains("{name: \"car\", z: 350}"))
        assertTrue(text, text.contains("function refreshZ()"))
        assertTrue(text, text.contains("carSequence = startSequence(carData);"))
        assertTrue(text, text.contains("setSequenceRunning(carSequence, false);"))
        assertTrue(text, text.contains("var carSequence:Array<FlxTimer>;"))
        assertTrue(text, text.contains("import flixel.util.FlxTimer;"))
        assertTrue(text, text.contains("setAdjustColor(colorShader, 'hue', -30);"))
        assertTrue(text, text.contains("game.createRuntimeShader('adjustColor')"))
        assertFalse(text, Regex("new AdjustColorShader|:AdjustColorShader").containsMatchIn(text))
        val start = text.substringAfter("function onSongStart()").substringBefore("\n}\n")
        assertTrue(start, start.contains("refreshZ();"))
    }

    @Test
    fun psychPrecacheUsesFunkinMemory() {
        val cases = PsychNativeLibrary.library.cases
        val image = cases.first { "precacheImage" in it.first }.second
        assertTrue(image, image.contains("FunkinMemory.cacheTexture(Paths.image("))
        val sound = cases.first { "precacheSound" in it.first }.second
        assertTrue(sound, sound.contains("FunkinMemory.cacheSound(Paths.sound("))
        val character = cases.first { "addCharacterToList" in it.first }.second
        assertTrue(character, character.contains("CharacterDataParser.fetchCharacterData"))
    }
}
