#include "Generator.hpp"

#include "Converter.hpp"

#include <cctype>
#include <map>
#include <set>
#include <vector>

namespace vscript {

namespace {

struct Callback {
    const char* method;
    const char* type;
    bool gameplay;
};

const Callback kCallbacks[] = {
    {"onCreate", "ScriptEvent", false}, {"onUpdate", "UpdateScriptEvent", false}, {"onBeatHit", "SongTimeScriptEvent", true},
    {"onStepHit", "SongTimeScriptEvent", true}, {"onCountdownStart", "CountdownScriptEvent", true}, {"onSongStart", "ScriptEvent", true},
    {"onSongEnd", "ScriptEvent", true}, {"onNoteIncoming", "NoteScriptEvent", true}, {"onNoteHit", "HitNoteScriptEvent", true},
    {"onNoteMiss", "NoteScriptEvent", true}, {"onSongEvent", "SongEventScriptEvent", true}, {"onPause", "PauseScriptEvent", true},
    {"onGameOver", "ScriptEvent", true}, {"onDestroy", "ScriptEvent", false}};

const std::set<std::string> kNoteKindCallbacks = {"onCreate", "onUpdate", "onNoteIncoming", "onNoteHit", "onNoteMiss", "onDestroy"};

std::string pretty(const std::string& id) {
    std::string out;
    bool up = true;
    for (size_t i = 0; i < id.size(); i++) {
        char c = id[i];
        if (c == '-' || c == '_' || c == ' ') {
            if (!out.empty() && out.back() != ' ') out += ' ';
            up = true;
            continue;
        }
        if (i > 0 && std::isupper(static_cast<unsigned char>(c)) && std::islower(static_cast<unsigned char>(id[i - 1])) && out.back() != ' ') out += ' ';
        out += up ? static_cast<char>(std::toupper(static_cast<unsigned char>(c))) : c;
        up = false;
    }
    return out;
}

std::string defaultFor(const std::string& type) {
    if (type == "integer") return "0";
    if (type == "float") return "0.0";
    if (type == "bool") return "false";
    return "\"\"";
}

std::string readField(const std::string& name, const std::string& type) {
    std::string raw = "Reflect.field(value, " + hxString(name) + ")";
    std::string fallback = defaultFor(type);
    std::string hx = type == "integer" ? "Int" : type == "float" ? "Float" : type == "bool" ? "Bool" : "String";
    return "    var " + name + ":" + hx + " = value == null || " + raw + " == null ? " + fallback + " : " + raw + ";\n";
}

}

std::string generateScript(const Json& spec, std::string& error) {
    std::string kind = spec["kind"].text("module");
    std::string className = spec["className"].text();
    std::string target = spec["targetId"].text();
    if (className.empty() || target.empty()) {
        error = "The class name and the id are required.";
        return "";
    }
    std::set<std::string> wanted;
    for (const Json& c : spec["callbacks"].items()) wanted.insert(c.text());
    std::string title = spec["title"].text();
    if (title.empty()) title = pretty(target);
    int priority = static_cast<int>(spec["priority"].asNumber(1000));

    std::string out;
    if (kind == "event") {
        out += "class " + className + " extends SongEvent\n{\n";
        out += "  public function new()\n  {\n    super(" + hxString(target) + ");\n  }\n\n";
        out += "  override public function handleEvent(data:Dynamic):Void\n  {\n";
        out += "    if (PlayState.instance == null || data == null) return;\n";
        out += "    var value:Dynamic = data.value;\n";
        for (const Json& f : spec["fields"].items()) out += readField(f["name"].text(), f["type"].text("string"));
        out += "  }\n";
        for (const Callback& c : kCallbacks) {
            if (!wanted.count(c.method)) continue;
            out += "\n  override public function " + std::string(c.method) + "(event:" + c.type + "):Void\n  {\n";
            out += "    super." + std::string(c.method) + "(event);\n  }\n";
        }
        out += "\n  override public function getTitle():String\n  {\n    return " + hxString(title) + ";\n  }\n";
        out += "\n  override public function getEventSchema():Dynamic\n  {\n";
        const auto& fields = spec["fields"].items();
        if (fields.empty()) {
            out += "    return new SongEventSchema([]);\n";
        } else {
            out += "    return new SongEventSchema([\n";
            for (size_t i = 0; i < fields.size(); i++) {
                std::string name = fields[i]["name"].text();
                std::string type = fields[i]["type"].text("string");
                out += "      {name: " + hxString(name) + ", title: " + hxString(pretty(name)) + ", type: " + hxString(type) + ", defaultValue: " + defaultFor(type) + "}";
                out += i + 1 < fields.size() ? ",\n" : "\n";
            }
            out += "    ]);\n";
        }
        out += "  }\n}\n";
        return completeImports(out, {});
    }

    std::string parent;
    std::string superCall;
    if (kind == "module") {
        parent = "Module";
        superCall = "super(" + hxString(target) + ", " + std::to_string(priority) + ");";
    } else if (kind == "song") {
        parent = "Song";
        superCall = "super(" + hxString(target) + ");";
    } else if (kind == "stage") {
        parent = "Stage";
        superCall = "super(" + hxString(target) + ");";
    } else if (kind == "character") {
        parent = spec["render"].text("SparrowCharacter");
        superCall = "super(" + hxString(target) + ");";
    } else if (kind == "notekind") {
        parent = "NoteKind";
        std::string style = spec["noteStyle"].text();
        superCall = "super(" + hxString(target) + ", " + hxString(title) + (style.empty() ? "" : ", " + hxString(style)) + ");";
    } else {
        error = "Unknown script kind \"" + kind + "\".";
        return "";
    }
    std::set<std::string> imports;
    if (kind == "character") imports.insert("funkin.play.character." + parent);
    out += "class " + className + " extends " + parent + "\n{\n";
    out += "  public function new()\n  {\n    " + superCall + "\n  }\n";
    for (const Callback& c : kCallbacks) {
        if (!wanted.count(c.method)) continue;
        if (kind == "notekind" && !kNoteKindCallbacks.count(c.method)) continue;
        out += "\n  override public function " + std::string(c.method) + "(event:" + c.type + "):Void\n  {\n";
        out += "    super." + std::string(c.method) + "(event);\n";
        if (kind == "module" && c.gameplay) out += "    if (PlayState.instance == null) return;\n";
        if (kind == "module" && std::string(c.method) == "onUpdate") out += "    if (PlayState.instance == null) return;\n";
        if (std::string(c.method) == "onNoteHit") out += "    var isPlayer:Bool = event.note.noteData.getMustHitNote();\n";
        out += "  }\n";
    }
    out += "}\n";
    return completeImports(out, imports);
}

}
