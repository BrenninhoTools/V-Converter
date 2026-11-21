package com.vconvertercrew.vconverter.script

object ChangeCharacterEvent {
    private val template = """
import funkin.data.character.CharacterDataParser;
import funkin.data.event.SongEventSchema;
import funkin.data.song.SongEventData;
import funkin.play.character.BaseCharacter;
import funkin.play.character.CharacterType;
import funkin.play.event.SongEvent;
import funkin.play.PlayState;
import funkin.play.stage.Stage;
import Reflect;
import StringTools;

class %CLASS% extends SongEvent
{
  var game:PlayState = null;
  var pool:Array<BaseCharacter> = [];
  var originals:Array<String> = [];

  public function new()
  {
    super(%ID%, {processOldEvents: true});
  }

  function slotOf(value:String):Int
  {
    var key:String = StringTools.trim(value).toLowerCase();
    if (key == "gf" || key == "girlfriend") return 2;
    if (key == "dad" || key == "opponent") return 1;
    var index:Null<Int> = Std.parseInt(key);
    if (index == null) return 0;
    return index >= 0 && index <= 2 ? index : -1;
  }

  function characterAt(stage:Stage, slot:Int, pop:Bool):BaseCharacter
  {
    if (slot == 1) return stage.getDad(pop);
    if (slot == 2) return stage.getGirlfriend(pop);
    return stage.getBoyfriend(pop);
  }

  function pooled(id:String):BaseCharacter
  {
    for (character in pool) if (character != null && character.characterId == id) return character;
    return null;
  }

  function take(id:String):BaseCharacter
  {
    var character:BaseCharacter = pooled(id);
    if (character == null) return CharacterDataParser.fetchCharacter(id);
    pool.remove(character);
    return character;
  }

  function swap(slot:Int, id:String):Void
  {
    var stage:Stage = PlayState.instance.currentStage;
    if (stage == null || id == null || id == "") return;
    var old:BaseCharacter = characterAt(stage, slot, false);
    if (old != null && old.characterId == id) return;
    var next:BaseCharacter = take(id);
    if (next == null) return;
    if (old != null) pool.push(characterAt(stage, slot, true));
    stage.addCharacter(next, slot == 1 ? CharacterType.DAD : (slot == 2 ? CharacterType.GF : CharacterType.BF));
    stage.refresh();
  }

  function clearPool():Void
  {
    for (character in pool) if (character != null) character.destroy();
    pool = [];
  }

  function preload(play:PlayState):Void
  {
    var events:Array<SongEventData> = play.currentChart == null ? null : play.currentChart.events;
    if (events == null) return;
    for (songEvent in events)
    {
      if (songEvent == null || songEvent.eventKind != %ID%) continue;
      var raw:Dynamic = Reflect.field(songEvent.value, "value2");
      var id:String = raw == null ? "" : StringTools.trim(Std.string(raw));
      if (id == "" || pooled(id) != null) continue;
      var character:BaseCharacter = CharacterDataParser.fetchCharacter(id);
      if (character != null) pool.push(character);
    }
  }

  function idOf(character:BaseCharacter):String
  {
    return character == null ? null : character.characterId;
  }

  public override function onCountdownStart(event:CountdownScriptEvent):Void
  {
    var play:PlayState = PlayState.instance;
    if (play == null || play.currentStage == null) return;
    var stage:Stage = play.currentStage;
    if (game != play)
    {
      game = play;
      clearPool();
      originals = [idOf(stage.getBoyfriend()), idOf(stage.getDad()), idOf(stage.getGirlfriend())];
      preload(play);
      return;
    }
    for (slot in 0...3) if (originals[slot] != null) swap(slot, originals[slot]);
  }

  public override function onSongEnd(event:ScriptEvent):Void
  {
    clearPool();
    game = null;
  }

  public override function handleEvent(data:SongEventData):Void
  {
    if (PlayState.instance == null || PlayState.instance.currentStage == null || data == null) return;
    var raw1:Dynamic = Reflect.field(data.value, "value1");
    var raw2:Dynamic = Reflect.field(data.value, "value2");
    var slot:Int = slotOf(raw1 == null ? "" : Std.string(raw1));
    if (slot < 0) return;
    swap(slot, raw2 == null ? "" : StringTools.trim(Std.string(raw2)));
  }

  public override function getTitle():String
  {
    return %TITLE%;
  }

  public override function getEventSchema():SongEventSchema
  {
    return new SongEventSchema([
      {name: "value1", title: "Target (bf, dad or gf)", type: "string", defaultValue: "bf"},
      {name: "value2", title: "New character", type: "string", defaultValue: ""}
    ]);
  }
}
""".trimStart('\n')

    fun source(className: String, id: String, title: String): String = template
        .replace("%CLASS%", className)
        .replace("%ID%", LuaToHScript.hxString(id))
        .replace("%TITLE%", LuaToHScript.hxString(title))
}
