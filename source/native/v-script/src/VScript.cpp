#include "vscript/VScript.h"

#include "Converter.hpp"
#include "Funkin.hpp"
#include "Generator.hpp"
#include "Json.hpp"

#include <cstdlib>
#include <cstring>

namespace {

char* copy(const std::string& text) {
    char* out = static_cast<char*>(std::malloc(text.size() + 1));
    if (out == nullptr) return nullptr;
    std::memcpy(out, text.c_str(), text.size() + 1);
    return out;
}

vscript::Base baseOf(const std::string& text) {
    if (text == "song") return vscript::Base::Song;
    if (text == "stage") return vscript::Base::Stage;
    if (text == "notekind") return vscript::Base::NoteKind;
    if (text == "event") return vscript::Base::SongEvent;
    return vscript::Base::Module;
}

}

extern "C" {

const char* vscript_version(void) {
    return VSCRIPT_VERSION_STRING;
}

char* vscript_convert_lua(const char* source, const char* options_json) {
    using namespace vscript;
    bool ok = false;
    Json options = Json::parse(options_json ? options_json : "{}", ok);
    if (!ok) options = Json::object();
    ConvertOptions o;
    o.base = baseOf(options["base"].text("module"));
    o.className = options["className"].text("Script");
    o.id = options["id"].text("script");
    o.scopeKind = options["scopeKind"].text();
    o.scopeId = options["scopeId"].text();
    o.priority = static_cast<int>(options["priority"].asNumber(1000));
    o.eventName = options["eventName"].text();
    o.title = options["title"].text();
    o.noteStyle = options["noteStyle"].text();
    o.behindLayer = static_cast<int>(options["behindLayer"].asNumber(99));
    o.frontLayer = static_cast<int>(options["frontLayer"].asNumber(1000));
    for (const auto& field : options["events"].fields()) o.events[field.first] = field.second.text();
    for (const auto& field : options["noteKinds"].fields()) o.noteKinds[field.first] = field.second.text();
    for (const Json& prop : options["stageProps"].items()) o.stageProps.insert(prop.text());
    for (const Json& line : options["skipLines"].items()) o.skipLines.insert(static_cast<int>(line.asNumber()));
    ConvertResult result = convertLua(source ? source : "", o);
    Json out = Json::object();
    out.set("ok", Json::boolean(result.ok));
    out.set("source", Json::string(result.source));
    Json callbacks = Json::array();
    for (const std::string& c : result.callbacks) callbacks.push(Json::string(c));
    out.set("callbacks", callbacks);
    Json issues = Json::array();
    for (const Issue& i : result.issues) {
        Json item = Json::object();
        item.set("line", Json::number(i.line));
        item.set("message", Json::string(i.message));
        issues.push(item);
    }
    out.set("issues", issues);
    Json api = Json::array();
    for (const std::string& a : result.apiCalls) api.push(Json::string(a));
    out.set("api", api);
    return copy(out.dump());
}

char* vscript_generate(const char* spec_json) {
    using namespace vscript;
    bool ok = false;
    Json spec = Json::parse(spec_json ? spec_json : "{}", ok);
    Json out = Json::object();
    if (!ok) {
        out.set("ok", Json::boolean(false));
        out.set("error", Json::string("The script spec is not valid JSON."));
        return copy(out.dump());
    }
    std::string error;
    std::string source = generateScript(spec, error);
    out.set("ok", Json::boolean(error.empty()));
    out.set("source", Json::string(source));
    if (!error.empty()) out.set("error", Json::string(error));
    return copy(out.dump());
}

char* vscript_builtin_event(const char* name, const char* class_name, const char* id) {
    using namespace vscript;
    bool known = false;
    std::string source = builtinEvent(name ? name : "", class_name ? class_name : "Event", id ? id : "event", known);
    Json out = Json::object();
    out.set("known", Json::boolean(known));
    out.set("source", Json::string(source));
    return copy(out.dump());
}

char* vscript_convert_data(const char* kind, const char* input_json) {
    using namespace vscript;
    bool ok = false;
    Json input = Json::parse(input_json ? input_json : "", ok);
    Json out = Json::object();
    if (!ok) {
        out.set("ok", Json::boolean(false));
        out.set("error", Json::string("The input could not be read."));
        return copy(out.dump());
    }
    std::string type = kind ? kind : "";
    std::string error;
    Json result;
    if (type == "chart") result = convertPsychCharts(input, error);
    else if (type == "character") result = convertPsychCharacter(input, error);
    else if (type == "stage") result = convertPsychStage(input, error);
    else error = "Unknown conversion \"" + type + "\".";
    out.set("ok", Json::boolean(error.empty()));
    if (error.empty()) out.set("result", result);
    else out.set("error", Json::string(error));
    return copy(out.dump());
}

void vscript_free(char* text) {
    std::free(text);
}

}
