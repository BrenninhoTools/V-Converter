#include "vscript/VScript.h"

#include "Converter.hpp"
#include "Json.hpp"
#include "Lua.hpp"

#include <cstdio>
#include <cstdlib>
#include <string>

static int failures = 0;

#define CHECK(condition)                                                        \
    do {                                                                        \
        if (!(condition)) {                                                     \
            std::fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #condition); \
            failures++;                                                         \
        }                                                                       \
    } while (0)

static bool has(const std::string& text, const std::string& part) {
    bool found = text.find(part) != std::string::npos;
    if (!found) std::fprintf(stderr, "missing: %s\n", part.c_str());
    return found;
}

static bool lacks(const std::string& text, const std::string& part) {
    bool found = text.find(part) != std::string::npos;
    if (found) std::fprintf(stderr, "unexpected: %s\n", part.c_str());
    return !found;
}

static int braceBalance(const std::string& text) {
    int depth = 0;
    bool quoted = false;
    for (size_t i = 0; i < text.size(); i++) {
        char c = text[i];
        if (c == '"' && (i == 0 || text[i - 1] != '\\')) quoted = !quoted;
        if (quoted) continue;
        if (c == '{') depth++;
        if (c == '}') depth--;
        if (depth < 0) return -1;
    }
    return depth;
}

static vscript::ConvertResult convert(const std::string& lua, vscript::Base base, const std::string& className = "TestScript", const std::string& id = "test") {
    vscript::ConvertOptions o;
    o.base = base;
    o.className = className;
    o.id = id;
    o.noteKinds["Hurt Note"] = "hurt";
    return vscript::convertLua(lua, o);
}

static void testParser() {
    vscript::Block block = vscript::parseLua(
        "local a, b = 1, 'two'\n"
        "local s = [[long\nstring]] .. \"esc\\n\\65\\u{48}\"\n"
        "t = {1, 2, x = 3, ['y z'] = 4; 5}\n"
        "for i = 10, 1, -1 do if i % 2 == 0 then goto continue end ::continue:: end\n"
        "repeat a = a + 1 until a > 3\n"
        "function obj:method(...) return self, ... end\n"
        "local f = function(x) return x ^ 2 ^ 3 end\n"
        "print(#t, not a, -b, a ~= b, a != b)\n"
        "--[==[ comment ]==]\n"
        "call 'literal'\n"
        "call {1}\n");
    CHECK(block.size() == 10);
    bool threw = false;
    try {
        vscript::parseLua("function x( end");
    } catch (const vscript::LuaError& e) {
        threw = e.line == 1;
    }
    CHECK(threw);
}

static void testStage() {
    const char* lua =
        "local sway = 0\n"
        "function onCreate()\n"
        "  makeLuaSprite('bg', 'stageback', -600, -200)\n"
        "  setScrollFactor('bg', 0.9, 0.9)\n"
        "  addLuaSprite('bg', false)\n"
        "  makeAnimatedLuaSprite('crowd', 'crowd', 0, 300)\n"
        "  addAnimationByPrefix('crowd', 'bop', 'crowd bop', 24, false)\n"
        "  addAnimationByIndices('crowd', 'left', 'crowd bop', '0, 1, 2', 24)\n"
        "  addLuaSprite('crowd', true)\n"
        "end\n"
        "function onBeatHit()\n"
        "  if curBeat % 2 == 0 then playAnim('crowd', 'bop', true) end\n"
        "end\n"
        "function onUpdate(elapsed)\n"
        "  sway = sway + elapsed\n"
        "  setProperty('bg.angle', math.sin(sway) * 5)\n"
        "end\n";
    vscript::ConvertResult r = convert(lua, vscript::Base::Stage, "SpookyStage", "spooky");
    CHECK(r.ok);
    const std::string& s = r.source;
    CHECK(has(s, "class SpookyStage extends Stage"));
    CHECK(has(s, "import funkin.play.stage.Stage;"));
    CHECK(has(s, "import funkin.graphics.FunkinSprite;"));
    CHECK(has(s, "super(\"spooky\");"));
    CHECK(has(s, "var bg:FunkinSprite;"));
    CHECK(has(s, "public override function onCreate(event:ScriptEvent):Void"));
    CHECK(has(s, "bg = FunkinSprite.create(-600, -200, \"stageback\");"));
    CHECK(has(s, "behindSprites.push(bg);"));
    CHECK(has(s, "function layerSprites():Void"));
    CHECK(has(s, "var stage:Stage = this;"));
    CHECK(has(s, "public override function onCountdownStart(event:CountdownScriptEvent):Void"));
    CHECK(has(s, "layerSprites();"));
    CHECK(has(s, "bg.scrollFactor.set(0.9, 0.9);"));
    CHECK(has(s, "crowd = FunkinSprite.createSparrow(0, 300, \"crowd\");"));
    CHECK(has(s, "crowd.animation.addByIndices(\"left\", \"crowd bop\", [0, 1, 2], \"\", 24, false);"));
    CHECK(has(s, "frontSprites.push(crowd);"));
    CHECK(has(s, "add(crowd);"));
    CHECK(has(s, "stage.refresh();"));
    CHECK(has(s, "Conductor.instance.currentBeat % 2 == 0"));
    CHECK(has(s, "crowd.animation.play(\"bop\", true, false, 0);"));
    CHECK(has(s, "var elapsed:Float = event.elapsed;"));
    CHECK(has(s, "bg.angle = Math.sin(sway) * 5;"));
    CHECK(has(s, "var sway:Dynamic = 0;"));
    CHECK(lacks(s, "psychCallback"));
    CHECK(lacks(s, "setProperty("));
    CHECK(lacks(s, "makeLuaSprite"));
    CHECK(braceBalance(s) == 0);
}

static void testSongEvents() {
    const char* lua =
        "function onEvent(name, v1, v2)\n"
        "  if name == 'Flash Camera' then\n"
        "    cameraFlash('hud', 'FF0000', tonumber(v1))\n"
        "  elseif name == 'Play Animation' then\n"
        "    debugPrint('anim', v1)\n"
        "  end\n"
        "end\n"
        "function goodNoteHit(id, dir, noteType, sustain)\n"
        "  if noteType == 'Hurt Note' then addHealth(-0.2) end\n"
        "end\n"
        "function opponentNoteHit(id, dir, noteType, sustain)\n"
        "  cameraShake('game', 0.01, 0.1)\n"
        "end\n"
        "function onEndSong()\n"
        "  if not seen then seen = true; return Function_Stop end\n"
        "  return Function_Continue\n"
        "end\n"
        "function onSectionHit()\n"
        "  triggerEvent('Hey!', 'bf', '')\n"
        "end\n";
    vscript::ConvertResult r = convert(lua, vscript::Base::Song, "BopeeboSong", "bopeebo");
    const std::string& s = r.source;
    CHECK(has(s, "class BopeeboSong extends Song"));
    CHECK(has(s, "public override function onSongEvent(event:SongEventScriptEvent):Void"));
    CHECK(has(s, "var name:String = event.eventData.eventKind;"));
    CHECK(has(s, "if (name == \"FlashCamera\")"));
    CHECK(has(s, "else if (name == \"PlayAnimation\")"));
    CHECK(has(s, "v1 = event.eventData.value.anim;"));
    CHECK(has(s, "PlayState.instance.camHUD.flash(0xFFFF0000, Std.parseFloat(v1), null, false);"));
    CHECK(has(s, "if (event.note.noteData.getMustHitNote())"));
    CHECK(has(s, "if (noteType == \"hurt\")"));
    CHECK(has(s, "PlayState.instance.health += -0.2;"));
    CHECK(has(s, "PlayState.instance.camGame.shake(0.01, 0.1);"));
    CHECK(has(s, "public override function onSongEnd(event:ScriptEvent):Void"));
    CHECK(has(s, "event.cancel();"));
    CHECK(has(s, "event.step % Conductor.instance.stepsPerMeasure == 0"));
    CHECK(has(s, "SongEventRegistry.handleEvent(new SongEventData(Conductor.instance.songPosition, \"PlayAnimation\", {anim: \"hey\", target: \"bf\", force: true}));"));
    CHECK(has(s, "import funkin.data.song.SongEventData;"));
    CHECK(braceBalance(s) == 0);
}

static void testModuleScope() {
    vscript::ConvertOptions o;
    o.base = vscript::Base::Module;
    o.className = "SpookyModule";
    o.id = "spooky-module";
    o.scopeKind = "song";
    o.scopeId = "spookeez";
    vscript::ConvertResult r = vscript::convertLua("function onUpdate(e) health = 1 end\nfunction onStepHit() if curStep == 4 then close() end end", o);
    const std::string& s = r.source;
    CHECK(has(s, "class SpookyModule extends Module"));
    CHECK(has(s, "super(\"spooky-module\", 1000);"));
    CHECK(has(s, "PlayState.instance.currentSong.id != \"spookeez\""));
    CHECK(has(s, "var closed:Bool = false;"));
    CHECK(has(s, "closed = true;"));
    CHECK(braceBalance(s) == 0);
}

static void testLuaSemantics() {
    const char* lua =
        "local list = {}\n"
        "local names = {'a', 'b'}\n"
        "local cfg = {speed = 2, name = 'x'}\n"
        "function onCreate()\n"
        "  table.insert(list, 5)\n"
        "  for i, v in ipairs(names) do debugPrint(i .. v) end\n"
        "  for k, v in pairs(cfg) do debugPrint(k) end\n"
        "  for i = 1, #list do list[i] = list[i] * 2 end\n"
        "  for i = 0, 3 do local n = i end\n"
        "  local x = cfg.speed or 1\n"
        "  local y = x > 1 and 'fast' or 'slow'\n"
        "  local s = string.format('%d hits, %.2f%%', 10, 99.5)\n"
        "  local up = ('abc'):upper() .. string.sub('hello', 2, 3)\n"
        "  if string.find(up, 'B') then x = x ^ 2 end\n"
        "  if type(x) == 'number' and x then x = -x end\n"
        "  while x < 10 do x = x + 1 if x == 5 then break end end\n"
        "  repeat x = x - 1 until x <= 0\n"
        "  for i = 1, 10 do if i == 2 then goto continue end debugPrint(i) ::continue:: end\n"
        "  local a, b = 1, 2\n"
        "  a, b = b, a\n"
        "end\n";
    vscript::ConvertResult r = convert(lua, vscript::Base::Song);
    const std::string& s = r.source;
    CHECK(has(s, "list.push(5);"));
    CHECK(has(s, "var names:Dynamic = [\"a\", \"b\"];"));
    CHECK(has(s, "var cfg:Dynamic = {speed: 2, name: \"x\"};"));
    CHECK(has(s, "for (i in 1...list.length + 1)"));
    CHECK(has(s, "list[i - 1] = list[i - 1] * 2;"));
    CHECK(has(s, "for (i in 0...4)"));
    CHECK(has(s, "for (k in Reflect.fields(cfg))"));
    CHECK(has(s, "cfg.speed != null ? cfg.speed : 1"));
    CHECK(has(s, "x > 1 ? \"fast\" : \"slow\""));
    CHECK(has(s, "Std.int(10) + \" hits, \" + FlxMath.roundDecimal(99.5, 2) + \"%\""));
    CHECK(has(s, "\"abc\".toUpperCase() + \"hello\".substring(1, 3)"));
    CHECK(has(s, "up.indexOf(\"B\") >= 0"));
    CHECK(has(s, "Math.pow(x, 2)"));
    CHECK(has(s, "!Math.isNaN(Std.parseFloat(Std.string(x)))"));
    CHECK(has(s, "do"));
    CHECK(has(s, "while (!(x <= 0));"));
    CHECK(has(s, "continue;"));
    CHECK(has(s, "var tmp"));
    CHECK(braceBalance(s) == 0);
}

static void testNoteKindAndEvent() {
    vscript::ConvertOptions o;
    o.base = vscript::Base::NoteKind;
    o.className = "HurtNoteKind";
    o.id = "hurt";
    o.title = "Hurt Note";
    vscript::ConvertResult r = vscript::convertLua("function goodNoteHit(i, d, t, s) addHealth(-0.3) end\nfunction onBeatHit() end\nfunction onSongStart() debugPrint('x') end", o);
    CHECK(has(r.source, "class HurtNoteKind extends NoteKind"));
    CHECK(has(r.source, "super(\"hurt\", \"Hurt Note\");"));
    CHECK(has(r.source, "public override function onNoteHit(event:HitNoteScriptEvent):Void"));
    CHECK(lacks(r.source, "onSongStart(event"));
    CHECK(!r.issues.empty());

    vscript::ConvertOptions e;
    e.base = vscript::Base::SongEvent;
    e.className = "FlashEvent";
    e.id = "Flash";
    e.eventName = "Flash";
    vscript::ConvertResult ev = vscript::convertLua("function onEvent(n, a, b) if n == 'Flash' then cameraFlash('game', a, 0.3) end end", e);
    CHECK(has(ev.source, "class FlashEvent extends SongEvent"));
    CHECK(has(ev.source, "public override function handleEvent(data:SongEventData):Void"));
    CHECK(has(ev.source, "var a:Dynamic = Reflect.field(data.value, \"value1\");"));
    CHECK(has(ev.source, "getEventSchema"));
    CHECK(braceBalance(ev.source) == 0);
}

static void testErrorsAndApi() {
    char* raw = vscript_convert_lua("function onCreate( end", "{\"base\":\"stage\"}");
    bool ok = false;
    vscript::Json result = vscript::Json::parse(raw, ok);
    vscript_free(raw);
    CHECK(ok);
    CHECK(!result["ok"].asBool(true));
    CHECK(result["issues"].items().size() == 1);
    CHECK(result["issues"].items()[0]["line"].asNumber() == 1);

    raw = vscript_convert_lua("function onCreate() makeLuaSprite('a', 'b', 0, 0) end", "{\"base\":\"stage\",\"className\":\"AStage\",\"id\":\"a\"}");
    result = vscript::Json::parse(raw, ok);
    vscript_free(raw);
    CHECK(ok && result["ok"].asBool());
    CHECK(has(result["source"].text(), "class AStage extends Stage"));
    CHECK(result["callbacks"].items().size() == 1);

    raw = vscript_builtin_event("Add Camera Zoom", "AddCameraZoomEvent", "AddCameraZoom");
    result = vscript::Json::parse(raw, ok);
    vscript_free(raw);
    CHECK(result["known"].asBool());
    CHECK(has(result["source"].text(), "class AddCameraZoomEvent extends SongEvent"));
    CHECK(has(result["source"].text(), "import funkin.play.event.SongEvent;"));
    CHECK(braceBalance(result["source"].text()) == 0);
    raw = vscript_builtin_event("Unknown Thing", "X", "Y");
    result = vscript::Json::parse(raw, ok);
    vscript_free(raw);
    CHECK(!result["known"].asBool(true));

    raw = vscript_generate("{\"kind\":\"module\",\"className\":\"MyModule\",\"targetId\":\"my-module\",\"callbacks\":[\"onUpdate\",\"onNoteHit\"]}");
    result = vscript::Json::parse(raw, ok);
    vscript_free(raw);
    CHECK(result["ok"].asBool());
    CHECK(has(result["source"].text(), "super(\"my-module\", 1000);"));
    CHECK(has(result["source"].text(), "public override function onNoteHit(event:HitNoteScriptEvent):Void"));
    CHECK(has(result["source"].text(), "import funkin.modding.module.Module;"));
    raw = vscript_generate("{\"kind\":\"event\",\"className\":\"BoomEvent\",\"targetId\":\"Boom\",\"fields\":[{\"name\":\"power\",\"type\":\"float\"}]}");
    result = vscript::Json::parse(raw, ok);
    vscript_free(raw);
    CHECK(has(result["source"].text(), "var power:Float = value == null"));
    CHECK(has(result["source"].text(), "{name: \"power\", title: \"Power\", type: \"float\", defaultValue: 0.0}"));
    raw = vscript_generate("{\"kind\":\"weird\",\"className\":\"A\",\"targetId\":\"a\"}");
    result = vscript::Json::parse(raw, ok);
    vscript_free(raw);
    CHECK(!result["ok"].asBool(true));

    vscript::Json j = vscript::Json::parse("{\"a\":[1,2.5,\"\\u00e9\\n\"],\"b\":{\"c\":null,\"d\":true}}", ok);
    CHECK(ok);
    CHECK(j["a"].items()[2].text() == "\xC3\xA9\n");
    CHECK(j.dump() == "{\"a\":[1,2.5,\"\xC3\xA9\\n\"],\"b\":{\"c\":null,\"d\":true}}");
    CHECK(std::string(vscript_version()) == VSCRIPT_VERSION_STRING);
}

static void testFuzz() {
    const char* pieces[] = {"function", "end", "if", "then", "(", ")", "'x'", "local", "=", "1", "..", "{", "}", "[", "]", "for", "do", "a", ",", ":", "#", "--", "\n", "[[", "]]", "\"", "\\", "goto", "::"};
    unsigned seed = 1234;
    for (int round = 0; round < 3000; round++) {
        std::string text;
        int n = 1 + static_cast<int>(seed % 30);
        for (int i = 0; i < n; i++) {
            seed = seed * 1103515245u + 12345u;
            text += pieces[(seed >> 8) % (sizeof(pieces) / sizeof(pieces[0]))];
            text += ' ';
        }
        char* raw = vscript_convert_lua(text.c_str(), "{\"base\":\"song\"}");
        CHECK(raw != nullptr);
        vscript_free(raw);
    }
}

static std::string data(const char* kind, const std::string& input) {
    char* raw = vscript_convert_data(kind, input.c_str());
    std::string out = raw ? raw : "";
    vscript_free(raw);
    return out;
}

static void testDataConverters() {
    std::string chart = data("chart", R"({"charts":[{"difficulty":"Hard","text":"{\"song\":{\"song\":\"Test\",\"bpm\":150,\"speed\":2,\"player1\":\"bf\",\"player2\":\"dad\",\"stage\":\"spooky\",\"notes\":[{\"mustHitSection\":true,\"sectionNotes\":[[0,0,0],[0,0,0],[400,5,100,\"Hurt Note\"]]}],\"events\":[[100,[[\"Set GF Speed\",\"2\",\"\"],[\"Hey!\",\"bf\",\"\"]]]]}}"}],"options":{}})");
    CHECK(has(chart, "\"ok\":true"));
    CHECK(has(chart, "\"stage\":\"spookyMansion\""));
    CHECK(has(chart, "\"kind\":\"SetTargetBopSpeed\",\"value\":{\"target\":\"girlfriend\",\"rate\":2}"));
    CHECK(has(chart, "\"kind\":\"hurt\""));
    CHECK(has(chart, "\"duplicateNotes\":[{\"name\":\"hard\",\"count\":1}]"));
    CHECK(has(data("chart", R"({"charts":[{"difficulty":"hard","text":"nope"}]})"), "\"ok\":false"));
    CHECK(has(data("chart", "{}"), "Select at least one Psych Engine chart."));

    std::string character = data("character", R"({"id":"dude","slot":"opponent","atlases":{"dude":[["dude idle0000",410,400]]},"text":"{\"image\":\"characters/Dude\",\"scale\":1.5,\"position\":[20,300],\"animations\":[{\"anim\":\"idle\",\"name\":\"dude idle\",\"fps\":24,\"offsets\":[-5,3]}]}"})");
    CHECK(has(character, "\"ok\":true"));
    CHECK(has(character, "\"version\":\"1.0.2\""));
    CHECK(has(character, "\"name\":\"Dude\""));

    std::string stage = data("stage", R"({"id":"my-stage","atlases":{},"stage":{"directory":"mymod","defaultZoom":0.8,"objects":[{"type":"square","name":"fade","scale":[1280,720],"color":"black","filters":3},{"type":"sprite","name":"bg","image":"images/bg.png","filters":3,"scroll":[0.9,0.9]},{"type":"boyfriend"}]}})");
    CHECK(has(stage, "\"ok\":true"));
    CHECK(has(stage, "\"version\":\"1.0.2\""));
    CHECK(has(stage, "\"name\":\"My Stage\""));
    CHECK(has(stage, "\"assetPath\":\"#000000\""));
    CHECK(has(stage, "\"assetPath\":\"bg\""));
    CHECK(has(stage, "\"directory\":\"shared\""));
    CHECK(has(data("stage", "{}"), "The stage is missing."));
    CHECK(has(data("other", "{}"), "\"ok\":false"));
    CHECK(has(data("stage", "not json"), "\"ok\":false"));
}

static void testChangeCharacter() {
    bool known = false;
    std::string code = vscript::builtinEvent("ChangeCharacter", "ChangeCharacterEvent", "ChangeCharacter", known);
    CHECK(known);
    CHECK(has(code, "import funkin.data.character.CharacterDataParser;"));
    CHECK(has(code, "import funkin.play.character.CharacterType;"));
    CHECK(has(code, "import funkin.data.song.SongEventData;"));
    CHECK(has(code, "super(\"ChangeCharacter\", {processOldEvents: true});"));
    CHECK(has(code, "stage.addCharacter(next, slot == 1 ? CharacterType.DAD : (slot == 2 ? CharacterType.GF : CharacterType.BF));"));
    CHECK(has(code, "pool.push(characterAt(stage, slot, true));"));
    CHECK(has(code, "songEvent.eventKind != \"ChangeCharacter\""));
    CHECK(has(code, "return \"ChangeCharacter\";"));
    CHECK(braceBalance(code) == 0);
    CHECK(lacks(code, "trace("));

    std::string alt = vscript::builtinEvent("Alt Idle Animation", "AltIdleAnimationEvent", "AltIdleAnimation", known);
    CHECK(has(alt, "index == 1 ? stage.getBoyfriend()"));
    CHECK(has(alt, ": stage.getDad());"));

    auto r = convert(R"(function onBeatHit()
    triggerEvent('Change Character', 'dad', 'pico')
    triggerEvent('Hey!', 'gf', '')
    triggerEvent('Hey!', '', '')
    triggerEvent('Set GF Speed', '2', '')
    triggerEvent('Play Animation', 'shoot', '')
end)", vscript::Base::Song);
    CHECK(has(r.source, "new SongEventData(Conductor.instance.songPosition, \"ChangeCharacter\", {value1: \"dad\", value2: \"pico\"})"));
    CHECK(has(r.source, "{anim: \"cheer\", target: \"gf\", force: true}"));
    CHECK(has(r.source, "{anim: \"hey\", target: \"bf\", force: true}"));
    CHECK(has(r.source, "\"SetTargetBopSpeed\", {target: \"girlfriend\", rate: Std.parseInt(Std.string(\"2\"))}"));
    CHECK(has(r.source, "{anim: \"shoot\", target: \"dad\", force: true}"));
    CHECK(braceBalance(r.source) == 0);

    std::string chart = data("chart", R"({"charts":[{"difficulty":"normal","text":"{\"song\":{\"song\":\"T\",\"bpm\":120,\"notes\":[],\"events\":[[0,[[\"ChangeCharacter\",\"bf\",\"pico\"],[\"Change Character\",\"dad\",\"x\"],[\"Screen Shake\",\"1,0.1\",\"\"]]]]}}"}],"options":{"keepPsychEvents":false}})");
    CHECK(has(chart, "\"kind\":\"ChangeCharacter\",\"value\":{\"value1\":\"bf\",\"value2\":\"pico\"}"));
    CHECK(has(chart, "\"scriptedEvents\":[{\"name\":\"Change Character\",\"count\":2},{\"name\":\"Screen Shake\",\"count\":1}]"));
    CHECK(has(chart, "\"unsupportedEvents\":[]"));
}

int main() {
    testParser();
    testStage();
    testSongEvents();
    testModuleScope();
    testLuaSemantics();
    testNoteKindAndEvent();
    testErrorsAndApi();
    testFuzz();
    testDataConverters();
    testChangeCharacter();
    if (failures == 0) std::printf("v-script: all tests passed\n");
    return failures == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}
