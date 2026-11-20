#include "Converter.hpp"

#include <cctype>

namespace vscript {

namespace {

std::string lowerText(std::string text) {
    for (char& c : text) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return text;
}

const char* const kCharacterLookup =
    "    var key:String = %VALUE%.toLowerCase();\n"
    "    var stage = PlayState.instance.currentStage;\n"
    "    var character = key == \"dad\" || key == \"opponent\" || key == \"1\" ? stage.getDad() : (key == \"gf\" || key == \"girlfriend\" || key == \"2\" ? stage.getGirlfriend() : stage.getBoyfriend());\n";

std::string characterLookup(const std::string& value) {
    std::string text = kCharacterLookup;
    size_t at = text.find("%VALUE%");
    text.replace(at, 7, value);
    return text;
}

}

std::string builtinEvent(const std::string& name, const std::string& className, const std::string& id, bool& known) {
    known = true;
    std::string key = lowerText(name);
    std::string body;
    if (key == "add camera zoom") {
        body =
            "    var gameZoom:Float = Std.parseFloat(value1);\n"
            "    var hudZoom:Float = Std.parseFloat(value2);\n"
            "    if (Math.isNaN(gameZoom)) gameZoom = 0.015;\n"
            "    if (Math.isNaN(hudZoom)) hudZoom = 0.03;\n"
            "    if (PlayState.instance.cameraBopMultiplier < 1.35)\n"
            "    {\n"
            "      PlayState.instance.cameraBopMultiplier += gameZoom;\n"
            "      PlayState.instance.camHUD.zoom += hudZoom;\n"
            "    }\n";
    } else if (key == "screen shake") {
        body =
            "    var cameras:Array<Dynamic> = [PlayState.instance.camGame, PlayState.instance.camHUD];\n"
            "    var values:Array<String> = [value1, value2];\n"
            "    for (i in 0...2)\n"
            "    {\n"
            "      var parts:Array<String> = values[i].split(\",\");\n"
            "      var duration:Float = Std.parseFloat(parts[0]);\n"
            "      var intensity:Float = parts.length > 1 ? Std.parseFloat(parts[1]) : 0;\n"
            "      if (!Math.isNaN(duration) && !Math.isNaN(intensity) && duration > 0 && intensity != 0) cameras[i].shake(intensity, duration);\n"
            "    }\n";
    } else if (key == "set property") {
        body =
            "    var value:Dynamic = value2 == \"true\" ? true : (value2 == \"false\" ? false : (Math.isNaN(Std.parseFloat(value2)) ? value2 : Std.parseFloat(value2)));\n"
            "    var parts:Array<String> = value1.split(\".\");\n"
            "    var field:String = parts.pop();\n"
            "    var target:Dynamic = PlayState.instance;\n"
            "    if (parts.length > 0)\n"
            "    {\n"
            "      var root:String = parts.shift();\n"
            "      var stage = PlayState.instance.currentStage;\n"
            "      if (root == \"boyfriend\" || root == \"bf\") target = stage.getBoyfriend();\n"
            "      else if (root == \"dad\") target = stage.getDad();\n"
            "      else if (root == \"gf\" || root == \"girlfriend\") target = stage.getGirlfriend();\n"
            "      else if (stage.getNamedProp(root) != null) target = stage.getNamedProp(root);\n"
            "      else target = Reflect.getProperty(PlayState.instance, root);\n"
            "    }\n"
            "    for (part in parts) if (target != null) target = Reflect.getProperty(target, part);\n"
            "    if (target != null) Reflect.setProperty(target, field, value);\n";
    } else if (key == "play sound") {
        body =
            "    var volume:Float = Std.parseFloat(value2);\n"
            "    FunkinSound.playOnce(Paths.sound(value1), Math.isNaN(volume) ? 1 : volume);\n";
    } else if (key == "set gf speed") {
        body =
            "    var speed:Null<Int> = Std.parseInt(value1);\n"
            "    var gf = PlayState.instance.currentStage.getGirlfriend();\n"
            "    if (gf != null) gf.danceEvery = speed == null || speed < 1 ? 1 : speed;\n";
    } else if (key == "alt idle animation") {
        body = characterLookup("value1") + "    if (character != null) character.idleSuffix = value2;\n";
    } else if (key == "hey!") {
        body =
            "    var key:String = value1.toLowerCase();\n"
            "    var stage = PlayState.instance.currentStage;\n"
            "    if (key != \"gf\" && key != \"girlfriend\" && key != \"1\" && stage.getBoyfriend() != null) stage.getBoyfriend().playAnimation(\"hey\", true, true);\n"
            "    if (key != \"bf\" && key != \"boyfriend\" && key != \"0\" && stage.getGirlfriend() != null) stage.getGirlfriend().playAnimation(\"cheer\", true, true);\n";
    } else if (key == "play animation") {
        body = characterLookup("value2") + "    if (character != null) character.playAnimation(value1, true, true);\n";
    } else {
        known = false;
        return "";
    }
    std::string code =
        "class " + className + " extends SongEvent\n"
        "{\n"
        "  public function new()\n"
        "  {\n"
        "    super(" + hxString(id) + ");\n"
        "  }\n"
        "\n"
        "  override public function handleEvent(data:Dynamic):Void\n"
        "  {\n"
        "    if (PlayState.instance == null || PlayState.instance.currentStage == null || data == null) return;\n"
        "    var raw1:Dynamic = Reflect.field(data.value, \"value1\");\n"
        "    var raw2:Dynamic = Reflect.field(data.value, \"value2\");\n"
        "    var value1:String = raw1 == null ? \"\" : Std.string(raw1);\n"
        "    var value2:String = raw2 == null ? \"\" : Std.string(raw2);\n" +
        body +
        "  }\n"
        "\n"
        "  override public function getTitle():String\n"
        "  {\n"
        "    return " + hxString(name) + ";\n"
        "  }\n"
        "\n"
        "  override public function getEventSchema():Dynamic\n"
        "  {\n"
        "    return new SongEventSchema([\n"
        "      {name: \"value1\", title: \"Value 1\", type: \"string\", defaultValue: \"\"},\n"
        "      {name: \"value2\", title: \"Value 2\", type: \"string\", defaultValue: \"\"}\n"
        "    ]);\n"
        "  }\n"
        "}\n";
    return completeImports(code, {});
}

}
