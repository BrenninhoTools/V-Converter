#include "Converter.hpp"

#include "Lua.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <functional>
#include <sstream>

namespace vscript {

namespace {

const std::set<std::string> kHaxeReserved = {"abstract", "break", "case", "cast", "catch", "class", "continue", "default", "do", "dynamic", "else",
    "enum", "extends", "extern", "false", "final", "for", "function", "if", "implements", "import", "in", "inline", "interface", "macro", "new",
    "null", "operator", "overload", "override", "package", "private", "public", "return", "static", "super", "switch", "this", "throw", "true",
    "try", "typedef", "untyped", "using", "var", "while", "event", "trace", "activeGame", "closed", "sprites", "create"};

const std::set<std::string> kTypeNames = {"Math", "Std", "String", "Array", "Reflect", "Type", "FlxG", "Paths", "Conductor", "PlayState", "Preferences",
    "Highscore", "FlxTween", "FlxEase", "FlxTimer", "FlxColor", "FlxText", "FlxSprite", "FunkinSprite", "FunkinSound", "StringTools", "Date",
    "StringMap", "EReg", "Stage", "Song", "Module", "NoteKind", "SongEvent", "Assets", "Constants", "BlendMode", "FlxAxes", "FlxMath", "Lambda"};

const std::map<std::string, std::string> kImports = {{"Reflect", "Reflect"}, {"Type", "Type"}, {"StringTools", "StringTools"}, {"Lambda", "Lambda"},
    {"EReg", "EReg"}, {"Date", "Date"}, {"StringMap", "haxe.ds.StringMap"}, {"FlxCamera", "flixel.FlxCamera"}, {"FlxSprite", "flixel.FlxSprite"},
    {"FlxRuntimeShader", "flixel.addons.display.FlxRuntimeShader"}, {"FlxMath", "flixel.math.FlxMath"}, {"FlxPoint", "flixel.math.FlxPoint"},
    {"FlxText", "flixel.text.FlxText"}, {"FlxTextBorderStyle", "flixel.text.FlxTextBorderStyle"}, {"FlxEase", "flixel.tweens.FlxEase"},
    {"FlxTween", "flixel.tweens.FlxTween"}, {"FlxAxes", "flixel.util.FlxAxes"}, {"FlxColor", "flixel.util.FlxColor"}, {"FlxTimer", "flixel.util.FlxTimer"},
    {"BlendMode", "openfl.display.BlendMode"}, {"Conductor", "funkin.Conductor"}, {"Highscore", "funkin.Highscore"},
    {"PlayerSettings", "funkin.PlayerSettings"}, {"FunkinSound", "funkin.audio.FunkinSound"}, {"FunkinSprite", "funkin.graphics.FunkinSprite"},
    {"Module", "funkin.modding.module.Module"}, {"PlayState", "funkin.play.PlayState"}, {"PlayStatePlaylist", "funkin.play.PlayStatePlaylist"},
    {"SongEvent", "funkin.play.event.SongEvent"}, {"SongEventSchema", "funkin.data.event.SongEventSchema"},
    {"SongEventRegistry", "funkin.data.event.SongEventRegistry"}, {"SongEventData", "funkin.data.song.SongEventData"}, {"CharacterDataParser", "funkin.data.character.CharacterDataParser"},
    {"BaseCharacter", "funkin.play.character.BaseCharacter"}, {"CharacterType", "funkin.play.character.CharacterType"},
    {"Song", "funkin.play.song.Song"}, {"Stage", "funkin.play.stage.Stage"}, {"NoteKind", "funkin.play.notes.notekind.NoteKind"},
    {"VideoCutscene", "funkin.play.cutscene.VideoCutscene"}, {"Assets", "openfl.utils.Assets"}, {"FlxKey", "flixel.input.keyboard.FlxKey"}};

const std::set<std::string> kEaseNames = {"linear", "quadIn", "quadOut", "quadInOut", "cubeIn", "cubeOut", "cubeInOut", "quartIn", "quartOut",
    "quartInOut", "quintIn", "quintOut", "quintInOut", "sineIn", "sineOut", "sineInOut", "bounceIn", "bounceOut", "bounceInOut", "circIn", "circOut",
    "circInOut", "expoIn", "expoOut", "expoInOut", "backIn", "backOut", "backInOut", "elasticIn", "elasticOut", "elasticInOut", "smoothStepIn",
    "smoothStepOut", "smoothStepInOut", "smootherStepIn", "smootherStepOut", "smootherStepInOut"};

const std::set<std::string> kVSliceEvents = {"FocusCamera", "PlayAnimation", "ScrollSpeed", "SetCameraBop", "SetHealthIcon", "SetTargetBopSpeed", "ZoomCamera"};

std::string lower(std::string text) {
    for (char& c : text) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return text;
}

std::string trim(const std::string& text) {
    size_t a = text.find_first_not_of(" \t\r\n");
    if (a == std::string::npos) return "";
    size_t b = text.find_last_not_of(" \t\r\n");
    return text.substr(a, b - a + 1);
}

bool isIdentifier(const std::string& text) {
    if (text.empty() || std::isdigit(static_cast<unsigned char>(text[0]))) return false;
    for (char c : text) {
        if (!std::isalnum(static_cast<unsigned char>(c)) && c != '_') return false;
    }
    return true;
}

std::string pascal(const std::string& text) {
    std::string out;
    bool up = true;
    for (char c : text) {
        if (std::isalnum(static_cast<unsigned char>(c))) {
            out += up ? static_cast<char>(std::toupper(static_cast<unsigned char>(c))) : c;
            up = false;
        } else {
            up = true;
        }
    }
    return out;
}

std::string slug(const std::string& text) {
    std::string out;
    bool dash = false;
    for (char c : lower(trim(text))) {
        if (std::isalnum(static_cast<unsigned char>(c))) {
            if (dash && !out.empty()) out += '-';
            out += c;
            dash = false;
        } else {
            dash = true;
        }
    }
    return out;
}

bool isNumberText(const std::string& text, double& value) {
    if (text.empty()) return false;
    char* end = nullptr;
    value = std::strtod(text.c_str(), &end);
    return end != text.c_str() && *end == '\0';
}

std::string numberText(double value) {
    if (std::floor(value) == value && std::fabs(value) < 1e15) return std::to_string(static_cast<long long>(value));
    std::ostringstream out;
    out.precision(15);
    out << value;
    return out.str();
}

}

std::string hxString(const std::string& text) {
    std::string out = "\"";
    for (unsigned char c : text) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buffer[8];
                    std::snprintf(buffer, sizeof buffer, "\\x%02X", c);
                    out += buffer;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out + "\"";
}

std::string eventIdFor(const std::string& name) {
    std::string key = lower(trim(name));
    if (key == "hey!" || key == "play animation") return "PlayAnimation";
    if (key == "camera follow pos") return "FocusCamera";
    if (key == "change scroll speed") return "ScrollSpeed";
    std::string id = pascal(name);
    if (id.empty()) id = "PsychEvent";
    if (std::isdigit(static_cast<unsigned char>(id[0]))) id = "Event" + id;
    return kVSliceEvents.count(id) ? "Psych" + id : id;
}

std::string completeImports(const std::string& code, const std::set<std::string>& extra) {
    std::set<std::string> needed = extra;
    std::string blanked = code;
    for (size_t i = 0; i < blanked.size(); i++) {
        if (blanked[i] == '"') {
            size_t j = i + 1;
            while (j < blanked.size() && blanked[j] != '"') {
                if (blanked[j] == '\\') j++;
                j++;
            }
            for (size_t k = i; k < std::min(j + 1, blanked.size()); k++) blanked[k] = ' ';
            i = j;
        }
    }
    for (const auto& entry : kImports) {
        const std::string& name = entry.first;
        size_t at = 0;
        while ((at = blanked.find(name, at)) != std::string::npos) {
            bool startOk = at == 0 || (!std::isalnum(static_cast<unsigned char>(blanked[at - 1])) && blanked[at - 1] != '_' && blanked[at - 1] != '.');
            size_t end = at + name.size();
            bool endOk = end >= blanked.size() || (!std::isalnum(static_cast<unsigned char>(blanked[end])) && blanked[end] != '_');
            if (startOk && endOk) {
                needed.insert(entry.second);
                break;
            }
            at = end;
        }
    }
    std::vector<std::string> sorted;
    for (const std::string& path : needed) {
        if (path.find('.') != std::string::npos || path == "Reflect" || path == "Type" || path == "StringTools" || path == "Lambda" || path == "EReg" || path == "Date") {
            sorted.push_back(path);
        }
    }
    std::sort(sorted.begin(), sorted.end(), [](const std::string& a, const std::string& b) { return lower(a) < lower(b); });
    std::string out;
    for (const std::string& path : sorted) out += "import " + path + ";\n";
    if (!out.empty()) out += "\n";
    return out + code;
}

namespace {

class Code {
public:
    void line(const std::string& text) {
        size_t start = 0;
        while (true) {
            size_t end = text.find('\n', start);
            std::string piece = text.substr(start, end == std::string::npos ? std::string::npos : end - start);
            lines_.push_back(piece.empty() ? piece : std::string(static_cast<size_t>(depth_) * 2, ' ') + piece);
            if (end == std::string::npos) break;
            start = end + 1;
        }
    }
    void open(const std::string& head) {
        line(head);
        line("{");
        depth_++;
    }
    void close(const std::string& tail = "") {
        depth_--;
        line("}" + tail);
    }
    void blank() {
        if (!lines_.empty() && !lines_.back().empty()) lines_.push_back("");
    }
    void append(const Code& other) {
        for (const std::string& l : other.lines_) lines_.push_back(l.empty() ? l : std::string(static_cast<size_t>(depth_) * 2, ' ') + l);
    }
    bool empty() const { return lines_.empty(); }
    int depth() const { return depth_; }
    void setDepth(int depth) { depth_ = depth; }
    std::string text() const {
        std::string out;
        for (const std::string& l : lines_) out += l + "\n";
        return out;
    }
    const std::vector<std::string>& lines() const { return lines_; }

private:
    std::vector<std::string> lines_;
    int depth_ = 0;
};

enum class TagKind { Sprite, Text };

struct Tag {
    std::string field;
    TagKind kind = TagKind::Sprite;
};

enum class TableKind { Unknown, Array, Object, Map };

struct HookPart {
    std::string psych;
    const FuncBody* func = nullptr;
};

const std::set<std::string> kStringMethods = {"upper", "lower", "len", "sub", "find", "gsub", "rep", "format", "byte", "char", "reverse", "match", "gmatch", "split"};

const std::set<std::string> kKnownCallbacks = {"onCreate", "onCreatePost", "onUpdate", "onUpdatePost", "onBeatHit", "onStepHit", "onSectionHit",
    "onEvent", "goodNoteHit", "goodNoteHitPost", "opponentNoteHit", "opponentNoteHitPost", "noteMiss", "noteMissPress", "onSpawnNote", "onSongStart",
    "onEndSong", "onPause", "onResume", "onGameOver", "onGameOverStart", "onDestroy", "onStartCountdown", "onCountdownStarted", "onCountdownTick",
    "onTimerCompleted", "onTweenCompleted", "onSoundFinished", "onGameOverConfirm", "onMoveCamera", "onKeyPress", "onKeyRelease", "onGhostTap",
    "onRecalculateRating", "onUpdateScore", "onEventPushed", "onCheckForResult", "onNextDialogue", "onSkipDialogue", "onCustomSubstateCreate",
    "onCustomSubstateCreatePost", "onCustomSubstateUpdate", "onCustomSubstateUpdatePost", "onCustomSubstateDestroy", "onCloseScript",
    "onKeyPressPre", "onKeyReleasePre", "onSpawnNotePost", "noteMissPost"};

const std::set<std::string> kLuaGlobals = {"print", "tostring", "tonumber", "type", "pairs", "ipairs", "math", "string", "table", "os", "pcall", "error",
    "assert", "select", "unpack", "next", "require", "setmetatable", "getmetatable", "rawget", "rawset", "xpcall", "io", "debug", "_G", "bit"};

class Emitter {
public:
    Emitter(const ConvertOptions& options) : opt_(options) {}

    ConvertResult run(const std::string& source) {
        ConvertResult result;
        Block chunk;
        try {
            chunk = parseLua(source);
        } catch (const LuaError& e) {
            result.issues.push_back({e.line, std::string("Lua syntax error: ") + e.what()});
            return result;
        }
        analyze(chunk);
        build(chunk);
        result.ok = true;
        result.source = completeImports(classText(), imports_);
        result.callbacks = callbacks_;
        result.issues = issues_;
        result.apiCalls = api_;
        return result;
    }

private:
    const ConvertOptions& opt_;
    std::vector<Issue> issues_;
    std::set<std::string> imports_;
    std::set<std::string> api_;
    std::vector<std::string> callbacks_;

    std::map<std::string, const FuncBody*> functions_;
    std::vector<std::string> functionOrder_;
    std::map<std::string, std::string> methodNames_;
    std::map<std::string, std::string> fieldNames_;
    std::vector<std::string> fieldOrder_;
    std::map<std::string, std::string> fieldTypes_;
    std::map<std::string, std::string> fieldInits_;
    std::set<std::string> referencedFunctions_;
    std::map<std::string, Tag> tags_;
    std::vector<std::string> tagOrder_;
    std::set<std::string> cancelledTweens_;
    std::set<std::string> cancelledTimers_;
    std::set<std::string> soundTags_;
    std::map<std::string, TableKind> tables_;
    std::set<std::string> boolFields_;
    bool dynamicTags_ = false;
    bool usesClose_ = false;
    bool usesTweenCallback_ = false;
    bool usesTimerCallback_ = false;
    bool hasCreate_ = false;
    int behindCount_ = 0;
    int frontCount_ = 0;
    int temp_ = 0;

    std::vector<std::map<std::string, std::string>> scopes_;
    std::set<std::string> usedLocals_;
    std::string eventParam_;
    std::string noteTypeParam_;
    bool inOverride_ = false;
    bool cancelable_ = false;
    std::vector<std::string> loopContinue_;

    Code fields_;
    Code body_;
    std::vector<std::string> createParts_;
    std::vector<std::string> methodsText_;

    void issue(int line, const std::string& message) {
        for (const Issue& i : issues_) {
            if (i.line == line && i.message == message) return;
        }
        issues_.push_back({line, message});
    }

    void use(const std::string&) {}

    static const Expr* strip(const Expr* e) {
        while (e && e->kind == ExprKind::Paren) e = e->a.get();
        return e;
    }

    static bool isString(const Expr* e) { return strip(e) && strip(e)->kind == ExprKind::String; }
    static const std::string& stringOf(const Expr* e) { return strip(e)->text; }

    static const Expr* argAt(const std::vector<ExprPtr>& args, size_t i) { return i < args.size() ? args[i].get() : nullptr; }

    static std::string calleeName(const Expr& call) {
        const Expr* f = strip(call.a.get());
        if (f && f->kind == ExprKind::Name) return f->text;
        if (f && f->kind == ExprKind::Index && f->a && f->a->kind == ExprKind::Name && isString(f->b.get())) return f->a->text + "." + f->b->text;
        return "";
    }

    std::string safeName(const std::string& name) {
        std::string out;
        for (char c : name) out += std::isalnum(static_cast<unsigned char>(c)) || c == '_' ? c : '_';
        if (out.empty()) out = "value";
        if (std::isdigit(static_cast<unsigned char>(out[0]))) out = "_" + out;
        if (kHaxeReserved.count(out) || kTypeNames.count(out)) out += "_";
        return out;
    }

    std::string uniqueMember(const std::string& base) {
        std::string name = safeName(base);
        std::string candidate = name;
        int n = 2;
        auto taken = [&](const std::string& c) {
            for (const auto& f : fieldNames_) if (f.second == c) return true;
            for (const auto& m : methodNames_) if (m.second == c) return true;
            for (const auto& t : tags_) if (t.second.field == c) return true;
            return false;
        };
        while (taken(candidate)) candidate = name + std::to_string(n++);
        return candidate;
    }

    void walkExpr(const Expr* e, const std::function<void(const Expr&)>& visit) {
        if (!e) return;
        visit(*e);
        walkExpr(e->a.get(), visit);
        walkExpr(e->b.get(), visit);
        for (const auto& arg : e->args) walkExpr(arg.get(), visit);
        for (const auto& field : e->fields) {
            walkExpr(field.key.get(), visit);
            walkExpr(field.value.get(), visit);
        }
        if (e->func) walkBlock(e->func->body, visit);
    }

    void walkBlock(const Block& block, const std::function<void(const Expr&)>& visit) {
        for (const auto& s : block) {
            for (const auto& t : s->targets) walkExpr(t.get(), visit);
            for (const auto& v : s->values) walkExpr(v.get(), visit);
            walkExpr(s->cond.get(), visit);
            walkBlock(s->body, visit);
            for (const auto& c : s->clauses) {
                walkExpr(c.cond.get(), visit);
                walkBlock(c.body, visit);
            }
            walkBlock(s->elseBody, visit);
            if (s->func) walkBlock(s->func->body, visit);
        }
    }

    void collectFree(const Block& block, std::vector<std::set<std::string>>& scope, std::set<std::string>& assigned, std::set<std::string>& read) {
        scope.emplace_back();
        auto bound = [&](const std::string& n) {
            for (auto it = scope.rbegin(); it != scope.rend(); ++it) if (it->count(n)) return true;
            return false;
        };
        std::function<void(const Expr*)> expr = [&](const Expr* e) {
            if (!e) return;
            if (e->kind == ExprKind::Name) {
                if (!bound(e->text)) read.insert(e->text);
                return;
            }
            if (e->kind == ExprKind::Function) {
                scope.emplace_back(e->func->params.begin(), e->func->params.end());
                collectFree(e->func->body, scope, assigned, read);
                scope.pop_back();
                return;
            }
            expr(e->a.get());
            expr(e->b.get());
            for (const auto& a : e->args) expr(a.get());
            for (const auto& f : e->fields) {
                expr(f.key.get());
                expr(f.value.get());
            }
        };
        for (const auto& s : block) {
            switch (s->kind) {
                case StatKind::Local:
                    for (const auto& v : s->values) expr(v.get());
                    for (const auto& n : s->names) scope.back().insert(n);
                    break;
                case StatKind::LocalFunction:
                    scope.back().insert(s->names[0]);
                    scope.emplace_back(s->func->params.begin(), s->func->params.end());
                    collectFree(s->func->body, scope, assigned, read);
                    scope.pop_back();
                    break;
                case StatKind::Function:
                    if (s->path.size() == 1 && !bound(s->path[0])) assigned.insert(s->path[0]);
                    else if (!bound(s->path[0])) read.insert(s->path[0]);
                    scope.emplace_back(s->func->params.begin(), s->func->params.end());
                    collectFree(s->func->body, scope, assigned, read);
                    scope.pop_back();
                    break;
                case StatKind::Assign:
                    for (const auto& t : s->targets) {
                        if (t->kind == ExprKind::Name) {
                            if (!bound(t->text)) assigned.insert(t->text);
                        } else {
                            expr(t.get());
                        }
                    }
                    for (const auto& v : s->values) expr(v.get());
                    break;
                case StatKind::NumericFor:
                case StatKind::GenericFor:
                    for (const auto& v : s->values) expr(v.get());
                    scope.emplace_back(s->names.begin(), s->names.end());
                    collectFree(s->body, scope, assigned, read);
                    scope.pop_back();
                    break;
                case StatKind::Repeat:
                    collectFree(s->body, scope, assigned, read);
                    expr(s->cond.get());
                    break;
                default:
                    for (const auto& v : s->values) expr(v.get());
                    expr(s->cond.get());
                    if (!s->body.empty()) collectFree(s->body, scope, assigned, read);
                    for (const auto& c : s->clauses) {
                        expr(c.cond.get());
                        collectFree(c.body, scope, assigned, read);
                    }
                    if (!s->elseBody.empty()) collectFree(s->elseBody, scope, assigned, read);
            }
        }
        scope.pop_back();
    }

    bool isBoolLiteralExpr(const Expr* e) {
        e = strip(e);
        if (!e) return false;
        if (e->kind == ExprKind::True || e->kind == ExprKind::False) return true;
        if (e->kind == ExprKind::Unary && e->text == "not") return true;
        if (e->kind == ExprKind::Binary) {
            const std::string& op = e->text;
            if (op == "==" || op == "~=" || op == "<" || op == ">" || op == "<=" || op == ">=") return true;
            if (op == "and" || op == "or") return isBoolLiteralExpr(e->a.get()) && isBoolLiteralExpr(e->b.get());
        }
        return false;
    }

    void analyze(const Block& chunk) {
        for (const auto& s : chunk) {
            if (s->kind == StatKind::Function && s->path.size() == 1 && !opt_.skipLines.count(s->line)) {
                if (!functions_.count(s->path[0])) functionOrder_.push_back(s->path[0]);
                functions_[s->path[0]] = s->func.get();
            } else if (s->kind == StatKind::LocalFunction && !opt_.skipLines.count(s->line)) {
                if (!functions_.count(s->names[0])) functionOrder_.push_back(s->names[0]);
                functions_[s->names[0]] = s->func.get();
            }
        }
        for (const std::string& name : functionOrder_) {
            if (!kKnownCallbacks.count(name)) methodNames_[name] = uniqueMember(name);
        }

        std::set<std::string> called;
        walkBlock(chunk, [&](const Expr& e) {
            if (e.kind == ExprKind::Name && functions_.count(e.text) && kKnownCallbacks.count(e.text)) referencedFunctions_.insert(e.text);
            if (e.kind != ExprKind::Call) return;
            if (strip(e.a.get())->kind == ExprKind::Name) called.insert(strip(e.a.get())->text);
            std::string name = calleeName(e);
            const Expr* first = argAt(e.args, 0);
            if ((name == "makeLuaSprite" || name == "makeAnimatedLuaSprite" || name == "makeLuaText") && !opt_.skipLines.count(e.line)) {
                if (isString(first)) {
                    const std::string& tag = stringOf(first);
                    if (!tags_.count(tag)) {
                        Tag t;
                        t.kind = name == "makeLuaText" ? TagKind::Text : TagKind::Sprite;
                        t.field = uniqueMember(tag);
                        tags_[tag] = t;
                        tagOrder_.push_back(tag);
                    }
                } else {
                    dynamicTags_ = true;
                }
            }
            if (name == "cancelTween" && isString(first)) cancelledTweens_.insert(stringOf(first));
            if (name == "cancelTimer" && isString(first)) cancelledTimers_.insert(stringOf(first));
            if ((name == "stopSound" || name == "pauseSound" || name == "resumeSound" || name == "soundFadeIn" || name == "soundFadeOut" ||
                    name == "setSoundVolume" || name == "getSoundVolume" || name == "getSoundTime" || name == "setSoundTime") && isString(first) && !stringOf(first).empty()) {
                soundTags_.insert(stringOf(first));
            }
            if (name == "close") usesClose_ = true;
        });
        usesTweenCallback_ = functions_.count("onTweenCompleted") > 0;
        usesTimerCallback_ = functions_.count("onTimerCompleted") > 0;

        std::vector<std::set<std::string>> scope;
        std::set<std::string> assigned;
        std::set<std::string> read;
        Block topLocals;
        collectFree(chunk, scope, assigned, read);
        for (const auto& s : chunk) {
            if (s->kind == StatKind::Local && !opt_.skipLines.count(s->line)) {
                for (const auto& n : s->names) assigned.insert(n);
            }
        }
        for (const std::string& name : assigned) {
            if (functions_.count(name)) continue;
            fieldNames_[name] = uniqueMember(name);
            fieldOrder_.push_back(name);
        }
        for (const std::string& name : read) {
            if (fieldNames_.count(name) || functions_.count(name) || called.count(name) || psychGlobal(name, 0).size() || kLuaGlobals.count(name) || isPsychFunctionName(name)) continue;
            if (name == "Function_Stop" || name == "Function_Continue" || name == "Function_StopLua" || name == "Function_StopHScript" || name == "Function_StopAll") continue;
            fieldNames_[name] = uniqueMember(name);
            fieldOrder_.push_back(name);
        }
        classifyTables(chunk);
        findBoolFields(chunk);
    }

    bool isPsychFunctionName(const std::string& name) const {
        static const std::set<std::string> names = {"makeLuaSprite", "makeAnimatedLuaSprite", "addLuaSprite", "removeLuaSprite", "makeLuaText", "addLuaText",
            "removeLuaText", "setProperty", "getProperty", "debugPrint", "close", "runTimer", "doTweenX", "doTweenY", "doTweenAlpha", "playAnim"};
        return names.count(name) > 0;
    }

    void classifyTables(const Block& chunk) {
        auto mark = [&](const std::string& name, TableKind kind) {
            TableKind& current = tables_[name];
            if (current == TableKind::Unknown || kind == TableKind::Array || (kind == TableKind::Map && current == TableKind::Object)) current = kind;
        };
        std::function<void(const Block&)> visitBlock = [&](const Block& block) {
            for (const auto& s : block) {
                auto bindTable = [&](const std::string& name, const Expr* value) {
                    value = strip(value);
                    if (!value || value->kind != ExprKind::Table) return;
                    bool positional = false;
                    bool named = false;
                    bool keyed = false;
                    for (const auto& f : value->fields) {
                        if (f.kind == TableField::Kind::Positional) positional = true;
                        else if (f.kind == TableField::Kind::Named) named = true;
                        else if (isString(f.key.get())) named = true;
                        else keyed = true;
                    }
                    if (positional && !named) mark(name, TableKind::Array);
                    else if (keyed) mark(name, TableKind::Map);
                    else if (named) mark(name, TableKind::Object);
                    else tables_.emplace(name, TableKind::Unknown);
                };
                if (s->kind == StatKind::Local) {
                    for (size_t i = 0; i < s->names.size() && i < s->values.size(); i++) bindTable(s->names[i], s->values[i].get());
                }
                if (s->kind == StatKind::Assign) {
                    for (size_t i = 0; i < s->targets.size() && i < s->values.size(); i++) {
                        if (s->targets[i]->kind == ExprKind::Name) bindTable(s->targets[i]->text, s->values[i].get());
                    }
                }
                if (s->kind == StatKind::GenericFor && !s->values.empty()) {
                    const Expr* it = strip(s->values[0].get());
                    if (it && it->kind == ExprKind::Call && !it->args.empty() && strip(it->args[0].get())->kind == ExprKind::Name) {
                        std::string fn = calleeName(*it);
                        if (fn == "ipairs") mark(strip(it->args[0].get())->text, TableKind::Array);
                    }
                }
                visitBlock(s->body);
                for (const auto& c : s->clauses) visitBlock(c.body);
                visitBlock(s->elseBody);
                if (s->func) visitBlock(s->func->body);
            }
        };
        visitBlock(chunk);
        walkBlock(chunk, [&](const Expr& e) {
            if (e.kind == ExprKind::Unary && e.text == "#" && strip(e.a.get())->kind == ExprKind::Name) mark(strip(e.a.get())->text, TableKind::Array);
            if (e.kind == ExprKind::Call) {
                std::string fn = calleeName(e);
                if ((fn == "table.insert" || fn == "table.remove" || fn == "table.concat" || fn == "table.sort") && !e.args.empty() && strip(e.args[0].get())->kind == ExprKind::Name) {
                    mark(strip(e.args[0].get())->text, TableKind::Array);
                }
            }
            if (e.kind == ExprKind::Index && strip(e.a.get())->kind == ExprKind::Name) {
                const std::string& name = strip(e.a.get())->text;
                const Expr* key = strip(e.b.get());
                if (key->kind == ExprKind::Number) mark(name, TableKind::Array);
                else if (key->kind != ExprKind::String && tables_.count(name) && tables_[name] != TableKind::Array) mark(name, TableKind::Map);
            }
        });
    }

    void findBoolFields(const Block& chunk) {
        std::map<std::string, bool> allBool;
        std::function<void(const Block&)> visit = [&](const Block& block) {
            for (const auto& s : block) {
                if (s->kind == StatKind::Assign || s->kind == StatKind::Local) {
                    for (size_t i = 0; i < s->values.size(); i++) {
                        std::string name = s->kind == StatKind::Local ? (i < s->names.size() ? s->names[i] : "") :
                            (i < s->targets.size() && s->targets[i]->kind == ExprKind::Name ? s->targets[i]->text : "");
                        if (name.empty()) continue;
                        bool b = isBoolLiteralExpr(s->values[i].get());
                        auto it = allBool.find(name);
                        allBool[name] = (it == allBool.end() ? true : it->second) && b;
                    }
                }
                visit(s->body);
                for (const auto& c : s->clauses) visit(c.body);
                visit(s->elseBody);
                if (s->func) visit(s->func->body);
            }
        };
        visit(chunk);
        for (const auto& entry : allBool) {
            if (entry.second) boolFields_.insert(entry.first);
        }
    }

    std::string tmp() { return "tmp" + std::to_string(++temp_); }

    void pushScope() { scopes_.emplace_back(); }
    void popScope() { scopes_.pop_back(); }

    std::string declare(const std::string& lua) {
        std::string name = lua == "..." ? "args" : safeName(lua);
        bool clash = fieldNames_.count(lua) && fieldNames_[lua] == name;
        for (const auto& t : tags_) if (t.second.field == name) clash = true;
        if (clash) name += "Local";
        scopes_.back()[lua] = name;
        return name;
    }

    const std::string* local(const std::string& lua) const {
        for (auto it = scopes_.rbegin(); it != scopes_.rend(); ++it) {
            auto found = it->find(lua);
            if (found != it->end()) return &found->second;
        }
        return nullptr;
    }

    std::string psychGlobal(const std::string& name, int line) {
        static const std::map<std::string, std::string> globals = {
            {"curBeat", "Conductor.instance.currentBeat"}, {"curStep", "Conductor.instance.currentStep"},
            {"curSection", "Conductor.instance.currentMeasure"}, {"curDecBeat", "Conductor.instance.currentBeatTime"},
            {"curDecStep", "Conductor.instance.currentStepTime"}, {"songPosition", "Conductor.instance.songPosition"},
            {"bpm", "Conductor.instance.bpm"}, {"curBpm", "Conductor.instance.bpm"}, {"crochet", "Conductor.instance.beatLengthMs"},
            {"stepCrochet", "Conductor.instance.stepLengthMs"}, {"songLength", "FlxG.sound.music.length"},
            {"songName", "PlayState.instance.currentSong.songName"}, {"songPath", "PlayState.instance.currentSong.id"},
            {"loadedSongName", "PlayState.instance.currentSong.id"}, {"curStage", "PlayState.instance.currentStageId"},
            {"screenWidth", "FlxG.width"}, {"screenHeight", "FlxG.height"}, {"downscroll", "Preferences.downscroll"},
            {"middlescroll", "false"}, {"flashingLights", "Preferences.flashingLights"}, {"framerate", "Preferences.framerate"},
            {"botPlay", "PlayState.instance.isBotPlayMode"}, {"difficultyName", "PlayState.instance.currentDifficulty"},
            {"isStoryMode", "PlayStatePlaylist.isStoryMode"}, {"health", "PlayState.instance.health"}, {"score", "PlayState.instance.songScore"},
            {"misses", "Highscore.tallies.missed"}, {"hits", "Highscore.tallies.totalNotesHit"}, {"combo", "Highscore.tallies.combo"},
            {"scrollSpeed", "PlayState.instance.currentChart.scrollSpeed"}, {"playbackRate", "PlayState.instance.playbackRate"},
            {"cameraX", "PlayState.instance.cameraFollowPoint.x"}, {"cameraY", "PlayState.instance.cameraFollowPoint.y"},
            {"inGameOver", "PlayState.instance.isGameOverState"}, {"startedCountdown", "!PlayState.instance.isInCountdown"},
            {"boyfriendName", "PlayState.instance.currentStage.getBoyfriend().characterId"},
            {"dadName", "PlayState.instance.currentStage.getDad().characterId"},
            {"gfName", "PlayState.instance.currentStage.getGirlfriend().characterId"},
            {"lowQuality", "false"}, {"shadersEnabled", "true"}, {"ghostTapping", "true"}, {"hideHud", "false"}, {"practice", "false"},
            {"instakillOnMiss", "false"}, {"luaDebugMode", "false"}, {"seenCutscene", "false"}, {"version", "\"1.0.4\""},
            {"defaultBoyfriendX", "PlayState.instance.currentStage.getBoyfriend().x"}, {"defaultBoyfriendY", "PlayState.instance.currentStage.getBoyfriend().y"},
            {"defaultOpponentX", "PlayState.instance.currentStage.getDad().x"}, {"defaultOpponentY", "PlayState.instance.currentStage.getDad().y"},
            {"defaultGirlfriendX", "PlayState.instance.currentStage.getGirlfriend().x"}, {"defaultGirlfriendY", "PlayState.instance.currentStage.getGirlfriend().y"}};
        auto it = globals.find(name);
        if (it != globals.end()) {
            use("Conductor");
            use("PlayState");
            use("Highscore");
            use("PlayStatePlaylist");
            return it->second;
        }
        static const char* const strums[] = {"defaultPlayerStrumX", "defaultPlayerStrumY", "defaultOpponentStrumX", "defaultOpponentStrumY"};
        for (const char* prefix : strums) {
            std::string p = prefix;
            if (name.size() == p.size() + 1 && name.compare(0, p.size(), p) == 0 && std::isdigit(static_cast<unsigned char>(name.back()))) {
                bool player = p.find("Player") != std::string::npos;
                char axis = std::tolower(static_cast<unsigned char>(p[p.size() - 1]));
                return std::string("PlayState.instance.") + (player ? "playerStrumline" : "opponentStrumline") + ".strumlineNotes.members[" + name.back() + "]." + axis;
            }
        }
        if (name == "mustHitSection" || name == "gfSection" || name == "altAnim") {
            issue(line, name + " has no V-Slice match: charts have no sections; camera focus comes from FocusCamera events.");
            return "false";
        }
        return "";
    }

    std::string nameRef(const std::string& lua, int line) {
        if (const std::string* l = local(lua)) {
            usedLocals_.insert(*l);
            return *l;
        }
        auto f = fieldNames_.find(lua);
        if (f != fieldNames_.end()) return f->second;
        auto m = methodNames_.find(lua);
        if (m != methodNames_.end()) return m->second;
        if (functions_.count(lua)) return lua;
        if (lua == "Function_Stop" || lua == "Function_StopLua" || lua == "Function_StopHScript" || lua == "Function_StopAll") return "true";
        if (lua == "Function_Continue") return "false";
        std::string g = psychGlobal(lua, line);
        if (!g.empty()) return g;
        if (lua == "_G") {
            issue(line, "_G has no V-Slice match.");
            return "null";
        }
        return safeName(lua);
    }

    bool isBoolExpr(const Expr* e) {
        e = strip(e);
        if (!e) return false;
        switch (e->kind) {
            case ExprKind::True:
            case ExprKind::False: return true;
            case ExprKind::Unary: return e->text == "not";
            case ExprKind::Binary: {
                const std::string& op = e->text;
                if (op == "==" || op == "~=" || op == "<" || op == ">" || op == "<=" || op == ">=") return true;
                if (op == "and" || op == "or") return isBoolExpr(e->a.get()) && isBoolExpr(e->b.get());
                return false;
            }
            case ExprKind::Name: {
                if (local(e->text)) return boolFields_.count(e->text) > 0;
                if (boolFields_.count(e->text)) return true;
                static const std::set<std::string> bools = {"downscroll", "middlescroll", "botPlay", "isStoryMode", "inGameOver", "startedCountdown",
                    "flashingLights", "lowQuality", "shadersEnabled", "ghostTapping", "hideHud", "practice", "mustHitSection", "gfSection"};
                return bools.count(e->text) > 0;
            }
            case ExprKind::Call: {
                static const std::set<std::string> bools = {"luaSpriteExists", "luaTextExists", "luaSoundExists", "keyJustPressed", "keyPressed",
                    "keyReleased", "keyboardJustPressed", "keyboardPressed", "keyboardReleased", "mouseClicked", "mousePressed", "mouseReleased",
                    "getRandomBool", "stringStartsWith", "stringEndsWith", "objectsOverlap", "checkFileExists", "isRunning"};
                std::string name = calleeName(*e);
                if (bools.count(name)) return true;
                if (name == "getProperty" || name == "getPropertyFromClass") {
                    const Expr* path = argAt(e->args, name == "getProperty" ? 0 : 1);
                    if (isString(path)) {
                        std::string last = stringOf(path).substr(stringOf(path).find_last_of('.') == std::string::npos ? 0 : stringOf(path).find_last_of('.') + 1);
                        static const std::set<std::string> props = {"visible", "active", "flipX", "flipY", "antialiasing", "exists", "alive", "downScroll",
                            "middleScroll", "ghostTapping", "inCutscene", "paused", "camZooming", "startingSong", "endingSong", "cpuControlled"};
                        return props.count(last) > 0;
                    }
                }
                auto fn = functions_.find(name);
                if (fn != functions_.end()) {
                    bool any = false;
                    bool all = true;
                    std::function<void(const Block&)> scan = [&](const Block& block) {
                        for (const auto& s : block) {
                            if (s->kind == StatKind::Return && !s->values.empty()) {
                                any = true;
                                if (!isBoolLiteralExpr(s->values[0].get())) all = false;
                            }
                            scan(s->body);
                            for (const auto& c : s->clauses) scan(c.body);
                            scan(s->elseBody);
                        }
                    };
                    scan(fn->second->body);
                    return any && all;
                }
                return false;
            }
            default: return false;
        }
    }

    std::string cond(const Expr* e) {
        e = strip(e);
        if (!e) return "false";
        if (e->kind == ExprKind::Call) {
            std::string name = calleeName(*e);
            if (name == "string.find" && e->args.size() >= 2) return findCond(expr(e->args[0].get()), e->args[1].get(), argAt(e->args, 3));
            if (name == "luaSpriteExists" || name == "luaTextExists") return expr(e) + "";
        }
        if (e->kind == ExprKind::Method && e->text == "find" && !e->args.empty()) return findCond(expr(e->a.get()), e->args[0].get(), argAt(e->args, 2));
        if (e->kind == ExprKind::Binary && (e->text == "and" || e->text == "or")) {
            return wrapLogic(e->a.get(), e->text) + (e->text == "and" ? " && " : " || ") + wrapLogic(e->b.get(), e->text);
        }
        if (e->kind == ExprKind::Unary && e->text == "not") return "!" + wrapCond(e->a.get());
        if (isBoolExpr(e)) return expr(e);
        if (e->kind == ExprKind::Nil) return "false";
        if (e->kind == ExprKind::Number || e->kind == ExprKind::String || e->kind == ExprKind::Table || e->kind == ExprKind::Function) return "true";
        std::string value = expr(e);
        if (e->kind == ExprKind::Call || e->kind == ExprKind::Method) return value + " == true";
        return value + " != null && " + value + " != false";
    }

    std::string wrapLogic(const Expr* e, const std::string& parent) {
        const Expr* s = strip(e);
        std::string c = cond(s);
        bool simpleAnd = s->kind == ExprKind::Binary && s->text == parent;
        bool compound = c.find(" && ") != std::string::npos || c.find(" || ") != std::string::npos;
        return compound && !simpleAnd ? "(" + c + ")" : c;
    }

    std::string wrapCond(const Expr* e) {
        std::string c = cond(e);
        const Expr* s = strip(e);
        bool atom = s->kind == ExprKind::Name || s->kind == ExprKind::True || s->kind == ExprKind::False ||
            ((s->kind == ExprKind::Call || s->kind == ExprKind::Method || s->kind == ExprKind::Index) && c.find(' ') == std::string::npos);
        return atom ? c : "(" + c + ")";
    }

    std::string findCond(const std::string& subject, const Expr* pattern, const Expr* plain) {
        std::string pat;
        bool literal = isString(pattern) && (plain || !hasMagic(stringOf(pattern)));
        if (literal) return subject + ".indexOf(" + hxString(stringOf(pattern)) + ") >= 0";
        if (isString(pattern)) {
            use("EReg");
            return "new EReg(" + hxString(luaPatternToRegex(stringOf(pattern))) + ", \"\").match(" + subject + ")";
        }
        return subject + ".indexOf(" + expr(pattern) + ") >= 0";
    }

    static bool hasMagic(const std::string& p) {
        return p.find_first_of("^$()%.[]*+-?") != std::string::npos;
    }

    static std::string luaPatternToRegex(const std::string& p) {
        std::string out;
        for (size_t i = 0; i < p.size(); i++) {
            char c = p[i];
            if (c == '%' && i + 1 < p.size()) {
                char n = p[++i];
                switch (n) {
                    case 'd': out += "\\d"; break;
                    case 'D': out += "\\D"; break;
                    case 'a': out += "[A-Za-z]"; break;
                    case 'A': out += "[^A-Za-z]"; break;
                    case 's': out += "\\s"; break;
                    case 'S': out += "\\S"; break;
                    case 'w': out += "[A-Za-z0-9]"; break;
                    case 'W': out += "[^A-Za-z0-9]"; break;
                    case 'l': out += "[a-z]"; break;
                    case 'u': out += "[A-Z]"; break;
                    case 'p': out += "[!-/:-@\\[-`{-~]"; break;
                    case 'x': out += "[0-9A-Fa-f]"; break;
                    default: out += std::string("\\") + n;
                }
            } else if (c == '-') {
                out += "*?";
            } else {
                out += c;
            }
        }
        return out;
    }

    static int precedence(const std::string& op) {
        if (op == "or") return 1;
        if (op == "and") return 2;
        if (op == "==" || op == "~=" || op == "<" || op == ">" || op == "<=" || op == ">=") return 3;
        if (op == "..") return 4;
        if (op == "+" || op == "-") return 5;
        if (op == "*" || op == "/") return 6;
        if (op == "%") return 7;
        return 8;
    }

    std::string operand(const Expr* e, const std::string& parent, bool right) {
        const Expr* s = strip(e);
        std::string text = expr(s);
        if (s->kind == ExprKind::Binary && s->text != "and" && s->text != "or") {
            int mine = precedence(s->text);
            int theirs = precedence(parent);
            bool same = s->text == parent && (parent == "+" || parent == "*") && !right;
            if (!(mine > theirs && parent != "%") && !same) return "(" + text + ")";
        }
        if (s->kind == ExprKind::Binary && (s->text == "and" || s->text == "or")) return "(" + text + ")";
        if (s->kind == ExprKind::Unary && s->text == "-" && parent == "-" && right) return "(" + text + ")";
        return text;
    }

    void concatParts(const Expr* e, std::vector<const Expr*>& parts) {
        const Expr* s = strip(e);
        if (s->kind == ExprKind::Binary && s->text == "..") {
            concatParts(s->a.get(), parts);
            concatParts(s->b.get(), parts);
        } else {
            parts.push_back(s);
        }
    }

    std::string number(const std::string& text) {
        if (text.size() > 2 && (text[1] == 'x' || text[1] == 'X')) return text;
        std::string out = text;
        if (out[0] == '.') out = "0" + out;
        size_t e = out.find_first_of("eE");
        std::string mantissa = e == std::string::npos ? out : out.substr(0, e);
        if (!mantissa.empty() && mantissa.back() == '.') mantissa += "0";
        return e == std::string::npos ? mantissa : mantissa + out.substr(e);
    }

    TableKind tableKindOf(const Expr* e) {
        e = strip(e);
        if (e && e->kind == ExprKind::Name) {
            auto it = tables_.find(e->text);
            if (it != tables_.end()) return it->second;
        }
        return TableKind::Unknown;
    }

    std::string offsetIndex(const Expr* key) {
        key = strip(key);
        double n;
        if (key->kind == ExprKind::Number && isNumberText(key->text, n)) return numberText(n - 1);
        if (key->kind == ExprKind::Binary && key->text == "+" && strip(key->b.get())->kind == ExprKind::Number) {
            double m;
            if (isNumberText(strip(key->b.get())->text, m)) {
                if (m == 1) return expr(key->a.get());
                return operand(key->a.get(), "+", false) + " + " + numberText(m - 1);
            }
        }
        return operand(key, "-", false) + " - 1";
    }

    std::string index(const Expr& e) {
        const Expr* target = strip(e.a.get());
        const Expr* key = strip(e.b.get());
        std::string base = expr(target);
        if (target->kind != ExprKind::Name && target->kind != ExprKind::Index && target->kind != ExprKind::Call && target->kind != ExprKind::Method) base = "(" + base + ")";
        if (target->kind == ExprKind::Name && !local(target->text) && !fieldNames_.count(target->text)) {
            if (target->text == "math" && key->kind == ExprKind::String) {
                if (key->text == "pi") return "Math.PI";
                if (key->text == "huge") return "Math.POSITIVE_INFINITY";
            }
        }
        TableKind kind = tableKindOf(target);
        if (key->kind == ExprKind::String && isIdentifier(key->text) && !kHaxeReserved.count(key->text)) {
            if (kind == TableKind::Map) return base + ".get(" + hxString(key->text) + ")";
            return base + "." + key->text;
        }
        if (kind == TableKind::Map) return base + ".get(" + expr(key) + ")";
        if (key->kind == ExprKind::String) {
            use("Reflect");
            return "Reflect.field(" + base + ", " + hxString(key->text) + ")";
        }
        if (kind == TableKind::Array || key->kind == ExprKind::Number) return base + "[" + offsetIndex(key) + "]";
        return base + "[" + expr(key) + "]";
    }

    std::string table(const Expr& e) {
        bool positional = false;
        bool named = false;
        bool keyed = false;
        for (const auto& f : e.fields) {
            if (f.kind == TableField::Kind::Positional) positional = true;
            else if (f.kind == TableField::Kind::Named || (f.kind == TableField::Kind::Keyed && isString(f.key.get()) && isIdentifier(stringOf(f.key.get())))) named = true;
            else keyed = true;
        }
        if (e.fields.empty()) return "{}";
        if (positional && !named && !keyed) {
            std::string out = "[";
            for (size_t i = 0; i < e.fields.size(); i++) {
                if (i) out += ", ";
                out += expr(e.fields[i].value.get());
            }
            return out + "]";
        }
        if (keyed) {
            issue(e.line, "a table with computed keys became a StringMap; read it with get() and set().");
            use("StringMap");
            return "new StringMap()";
        }
        if (positional) issue(e.line, "a table mixing list items and named fields kept only the named fields.");
        std::string out = "{";
        bool first = true;
        for (const auto& f : e.fields) {
            if (f.kind == TableField::Kind::Positional) continue;
            std::string key = f.kind == TableField::Kind::Named ? f.name : stringOf(f.key.get());
            if (!first) out += ", ";
            first = false;
            out += key + ": " + expr(f.value.get());
        }
        return out + "}";
    }

    std::string valueFor(const std::string& lua, const Expr* value) {
        const Expr* v = strip(value);
        if (v && v->kind == ExprKind::Table && v->fields.empty()) {
            auto it = tables_.find(lua);
            if (it != tables_.end() && it->second == TableKind::Array) return "[]";
            if (it != tables_.end() && it->second == TableKind::Map) return "new StringMap()";
        }
        return expr(value);
    }

    std::string function(const FuncBody& func) {
        pushScope();
        std::string params;
        for (size_t i = 0; i < func.params.size(); i++) {
            if (i) params += ", ";
            params += declare(func.params[i]);
        }
        if (func.vararg) {
            if (!params.empty()) params += ", ";
            params += declare("...");
            issue(func.line, "varargs (...) became one array argument.");
        }
        Code code;
        code.setDepth(0);
        bool savedOverride = inOverride_;
        inOverride_ = false;
        block(func.body, code);
        inOverride_ = savedOverride;
        popScope();
        std::string out = "function(" + params + ") {";
        if (code.empty()) return out + "}";
        out += "\n";
        for (const std::string& l : code.lines()) out += (l.empty() ? l : "  " + l) + "\n";
        return out + "}";
    }

    std::string expr(const Expr* e) {
        e = strip(e);
        if (!e) return "null";
        switch (e->kind) {
            case ExprKind::Nil: return "null";
            case ExprKind::True: return "true";
            case ExprKind::False: return "false";
            case ExprKind::Number: return number(e->text);
            case ExprKind::String: return hxString(e->text);
            case ExprKind::Vararg: return nameRef("...", e->line);
            case ExprKind::Function: return function(*e->func);
            case ExprKind::Table: return table(*e);
            case ExprKind::Name: return nameRef(e->text, e->line);
            case ExprKind::Index: return index(*e);
            case ExprKind::Call: return call(*e, nullptr);
            case ExprKind::Method: return method(*e);
            case ExprKind::Paren: return expr(e->a.get());
            case ExprKind::Unary: {
                if (e->text == "not") return "!" + wrapCond(e->a.get());
                if (e->text == "#") {
                    const Expr* a = strip(e->a.get());
                    std::string inner = expr(a);
                    if (a->kind == ExprKind::Binary) inner = "(" + inner + ")";
                    return inner + ".length";
                }
                const Expr* a = strip(e->a.get());
                std::string inner = expr(a);
                return a->kind == ExprKind::Binary ? "-(" + inner + ")" : "-" + inner;
            }
            case ExprKind::Binary: return binary(*e);
        }
        return "null";
    }

    std::string rewriteLiteral(const Expr* side, const Expr* other) {
        const Expr* s = strip(side);
        const Expr* o = strip(other);
        if (!isString(o) || s->kind != ExprKind::Name) return "";
        const std::string* l = local(s->text);
        if (!l) return "";
        if (!eventParam_.empty() && *l == eventParam_) return hxString(eventIdFor(stringOf(o)));
        if (!noteTypeParam_.empty() && *l == noteTypeParam_) return hxString(noteKindFor(stringOf(o)));
        return "";
    }

    std::string noteKindFor(const std::string& name) const {
        auto it = opt_.noteKinds.find(name);
        if (it != opt_.noteKinds.end()) return it->second;
        for (const auto& entry : opt_.noteKinds) {
            if (lower(entry.first) == lower(trim(name))) return entry.second;
        }
        return slug(name);
    }

    std::string typeCheck(const Expr* call, const Expr* literal, bool equal) {
        const Expr* c = strip(call);
        if (!c || c->kind != ExprKind::Call || calleeName(*c) != "type" || c->args.empty() || !isString(literal)) return "";
        std::string value = expr(c->args[0].get());
        const std::string& kind = stringOf(literal);
        std::string test;
        if (kind == "string") test = "Std.isOfType(" + value + ", String)";
        else if (kind == "number") test = "(" + value + " != null && !Std.isOfType(" + value + ", String) && !Math.isNaN(Std.parseFloat(Std.string(" + value + "))))";
        else if (kind == "boolean") test = "(" + value + " == true || " + value + " == false)";
        else if (kind == "nil") return value + (equal ? " == null" : " != null");
        else if (kind == "table") {
            use("Reflect");
            test = "(Reflect.isObject(" + value + ") && !Std.isOfType(" + value + ", String))";
        } else if (kind == "function") {
            use("Reflect");
            test = "Reflect.isFunction(" + value + ")";
        } else {
            return "";
        }
        return equal ? test : "!" + test;
    }

    std::string binary(const Expr& e) {
        const std::string& op = e.text;
        if (op == "and" || op == "or") {
            if (isBoolExpr(e.a.get()) && isBoolExpr(e.b.get())) return cond(&e);
            const Expr* left = strip(e.a.get());
            if (op == "or" && left->kind == ExprKind::Binary && left->text == "and") {
                return cond(left->a.get()) + " ? " + operand(left->b.get(), "?", false) + " : " + operand(e.b.get(), "?", true);
            }
            if (op == "or") {
                std::string a = expr(left);
                if (left->kind == ExprKind::Name || left->kind == ExprKind::Index) return a + " != null ? " + a + " : " + operand(e.b.get(), "?", true);
                return wrapCond(left) + " ? " + a + " : " + operand(e.b.get(), "?", true);
            }
            return wrapCond(left) + " ? " + operand(e.b.get(), "?", true) + " : " + (isBoolExpr(left) ? "false" : "null");
        }
        if (op == "..") {
            std::vector<const Expr*> parts;
            concatParts(&e, parts);
            std::string out;
            bool stringFirst = isString(parts[0]);
            if (!stringFirst) out = "\"\" + ";
            for (size_t i = 0; i < parts.size(); i++) {
                if (i) out += " + ";
                std::string piece = expr(parts[i]);
                if (parts[i]->kind == ExprKind::Binary || (parts[i]->kind == ExprKind::Unary && parts[i]->text != "#")) piece = "(" + piece + ")";
                out += piece;
            }
            return out;
        }
        if (op == "^") return "Math.pow(" + expr(e.a.get()) + ", " + expr(e.b.get()) + ")";
        if (op == "==" || op == "~=") {
            std::string check = typeCheck(e.a.get(), e.b.get(), op == "==");
            if (check.empty()) check = typeCheck(e.b.get(), e.a.get(), op == "==");
            if (!check.empty()) return check;
            std::string hop = op == "==" ? "==" : "!=";
            std::string right = rewriteLiteral(e.a.get(), e.b.get());
            std::string left = rewriteLiteral(e.b.get(), e.a.get());
            return (left.empty() ? operand(e.a.get(), op, false) : left) + " " + hop + " " + (right.empty() ? operand(e.b.get(), op, true) : right);
        }
        return operand(e.a.get(), op, false) + " " + op + " " + operand(e.b.get(), op, true);
    }

    std::string args(const std::vector<ExprPtr>& list, size_t from = 0) {
        std::string out;
        for (size_t i = from; i < list.size(); i++) {
            if (i > from) out += ", ";
            out += expr(list[i].get());
        }
        return out;
    }

    std::string arg(const std::vector<ExprPtr>& list, size_t i, const std::string& fallback) {
        const Expr* e = argAt(list, i);
        if (!e || strip(e)->kind == ExprKind::Nil) return fallback;
        return expr(e);
    }

    std::string method(const Expr& e) {
        const Expr* target = strip(e.a.get());
        std::string self = expr(target);
        if (target->kind == ExprKind::Binary || target->kind == ExprKind::Unary) self = "(" + self + ")";
        const std::string& name = e.text;
        if (name == "format") return format(target, e.args, 0, e.line);
        if (kStringMethods.count(name)) return stringFunction(name, self, e.args, 0, e.line);
        return self + "." + name + "(" + args(e.args) + ")";
    }

    std::string stringFunction(const std::string& name, const std::string& self, const std::vector<ExprPtr>& a, size_t from, int line) {
        const Expr* p1 = argAt(a, from);
        const Expr* p2 = argAt(a, from + 1);
        if (name == "upper") return self + ".toUpperCase()";
        if (name == "lower") return self + ".toLowerCase()";
        if (name == "len") return self + ".length";
        if (name == "reverse") return self + ".split(\"\").reverse().join(\"\")";
        if (name == "split") return self + ".split(" + arg(a, from, "\",\"") + ")";
        if (name == "sub") {
            double i = 1;
            double j = 0;
            bool iLit = p1 && strip(p1)->kind == ExprKind::Number && isNumberText(strip(p1)->text, i);
            bool jNeg = p2 && strip(p2)->kind == ExprKind::Unary && strip(p2)->text == "-" && strip(strip(p2)->a.get())->kind == ExprKind::Number;
            bool jLit = p2 && strip(p2)->kind == ExprKind::Number && isNumberText(strip(p2)->text, j);
            if (jNeg) {
                isNumberText(strip(strip(p2)->a.get())->text, j);
                j = -j;
                jLit = true;
            }
            bool iNeg = p1 && strip(p1)->kind == ExprKind::Unary && strip(p1)->text == "-" && strip(strip(p1)->a.get())->kind == ExprKind::Number;
            if (iNeg && !p2) return self + ".substr(" + expr(p1) + ")";
            std::string start = iLit ? numberText(std::max(0.0, i - 1)) : (p1 ? offsetIndex(p1) : "0");
            if (!p2) return self + ".substr(" + start + ")";
            if (jLit && j < 0) return self + ".substring(" + start + ", " + self + ".length" + (j == -1 ? "" : " - " + numberText(-j - 1)) + ")";
            return self + ".substring(" + start + ", " + expr(p2) + ")";
        }
        if (name == "find") {
            std::string subject = self;
            if (p1 && isString(p1) && (argAt(a, from + 2) || !hasMagic(stringOf(p1)))) return "(" + subject + ".indexOf(" + expr(p1) + ") + 1)";
            issue(line, "string.find with a pattern returns a position only through cond checks; it was turned into indexOf.");
            return "(" + subject + ".indexOf(" + expr(p1) + ") + 1)";
        }
        if (name == "gsub") {
            if (p1 && isString(p1) && !hasMagic(stringOf(p1))) {
                use("StringTools");
                return "StringTools.replace(" + self + ", " + expr(p1) + ", " + arg(a, from + 1, "\"\"") + ")";
            }
            if (p1 && isString(p1)) {
                use("EReg");
                return "new EReg(" + hxString(luaPatternToRegex(stringOf(p1))) + ", \"g\").replace(" + self + ", " + arg(a, from + 1, "\"\"") + ")";
            }
            use("StringTools");
            return "StringTools.replace(" + self + ", " + expr(p1) + ", " + arg(a, from + 1, "\"\"") + ")";
        }
        if (name == "rep") {
            use("StringTools");
            return "StringTools.lpad(\"\", " + self + ", " + self + ".length * " + operand(p1, "*", true) + ")";
        }
        if (name == "byte") return self + ".charCodeAt(" + (p1 ? offsetIndex(p1) : "0") + ")";
        if (name == "char") return "String.fromCharCode(" + self + ")";
        if (name == "match") {
            if (p1 && isString(p1)) {
                use("EReg");
                std::string re = "new EReg(" + hxString(luaPatternToRegex(stringOf(p1))) + ", \"\")";
                issue(line, "string.match became an EReg; it returns the whole match.");
                return "(" + re + ".match(" + self + ") ? " + re + ".matched(0) : null)";
            }
        }
        issue(line, "string." + name + " has no direct Haxe match.");
        return self + "." + name + "(" + args(a, from) + ")";
    }

    std::string format(const Expr* patternExpr, const std::vector<ExprPtr>& a, size_t from, int line) {
        if (!patternExpr || !isString(patternExpr)) {
            issue(line, "string.format with a pattern made at runtime was not converted.");
            return "Std.string(" + expr(patternExpr) + ")";
        }
        const std::string& pattern = stringOf(patternExpr);
        std::vector<std::string> pieces;
        std::string literal;
        size_t next = from;
        for (size_t i = 0; i < pattern.size(); i++) {
            if (pattern[i] != '%' || i + 1 >= pattern.size()) {
                literal += pattern[i];
                continue;
            }
            size_t j = i + 1;
            if (pattern[j] == '%') {
                literal += '%';
                i = j;
                continue;
            }
            std::string spec;
            while (j < pattern.size() && std::string("-+ #0123456789.").find(pattern[j]) != std::string::npos) spec += pattern[j++];
            if (j >= pattern.size()) break;
            char type = pattern[j];
            i = j;
            if (!literal.empty()) {
                pieces.push_back(hxString(literal));
                literal.clear();
            }
            std::string value = arg(a, next++, "null");
            if (type == 'd' || type == 'i') {
                pieces.push_back("Std.int(" + value + ")");
            } else if (type == 'f' || type == 'g') {
                size_t dot = spec.find('.');
                if (dot != std::string::npos) {
                    use("FlxMath");
                    pieces.push_back("FlxMath.roundDecimal(" + value + ", " + spec.substr(dot + 1) + ")");
                } else {
                    pieces.push_back(value);
                }
            } else if (type == 'x' || type == 'X') {
                use("StringTools");
                pieces.push_back("StringTools.hex(" + value + ")");
            } else {
                pieces.push_back(value);
            }
        }
        if (!literal.empty()) pieces.push_back(hxString(literal));
        if (pieces.empty()) return "\"\"";
        std::string out = pieces[0][0] == '"' ? "" : "\"\" + ";
        for (size_t i = 0; i < pieces.size(); i++) {
            if (i) out += " + ";
            out += pieces[i];
        }
        return out;
    }

    std::string tagField(const std::string& tag) const {
        auto it = tags_.find(tag);
        return it == tags_.end() ? "" : it->second.field;
    }

    std::string gameObject(const std::string& key) {
        use("PlayState");
        static const std::map<std::string, std::string> objects = {{"boyfriend", "PlayState.instance.currentStage.getBoyfriend()"},
            {"bf", "PlayState.instance.currentStage.getBoyfriend()"}, {"boyfriendGroup", "PlayState.instance.currentStage.getBoyfriend()"},
            {"dad", "PlayState.instance.currentStage.getDad()"}, {"dadGroup", "PlayState.instance.currentStage.getDad()"},
            {"opponent", "PlayState.instance.currentStage.getDad()"}, {"gf", "PlayState.instance.currentStage.getGirlfriend()"},
            {"girlfriend", "PlayState.instance.currentStage.getGirlfriend()"}, {"gfGroup", "PlayState.instance.currentStage.getGirlfriend()"},
            {"camGame", "PlayState.instance.camGame"}, {"camHUD", "PlayState.instance.camHUD"}, {"camOther", "PlayState.instance.camCutscene"},
            {"camFollow", "PlayState.instance.cameraFollowPoint"}, {"camFollowPos", "PlayState.instance.cameraFollowPoint"},
            {"playerStrums", "PlayState.instance.playerStrumline.strumlineNotes"}, {"opponentStrums", "PlayState.instance.opponentStrumline.strumlineNotes"},
            {"iconP1", "PlayState.instance.iconP1"}, {"iconP2", "PlayState.instance.iconP2"}, {"healthBar", "PlayState.instance.healthBar"},
            {"healthBarBG", "PlayState.instance.healthBarBG"}, {"scoreTxt", "PlayState.instance.scoreText"}, {"inst", "FlxG.sound.music"},
            {"vocals", "PlayState.instance.vocals"}, {"game", "PlayState.instance"}, {"instance", "PlayState.instance"}, {"this", "PlayState.instance"},
            {"stage", "PlayState.instance.currentStage"}, {"notes", "PlayState.instance.playerStrumline.notes"}};
        auto it = objects.find(key);
        return it == objects.end() ? "" : it->second;
    }

    std::string playStateField(const std::string& key) {
        static const std::map<std::string, std::string> fields = {{"health", "PlayState.instance.health"},
            {"songScore", "PlayState.instance.songScore"}, {"songMisses", "Highscore.tallies.missed"}, {"combo", "Highscore.tallies.combo"},
            {"songHits", "Highscore.tallies.totalNotesHit"}, {"defaultCamZoom", "PlayState.instance.currentCameraZoom"},
            {"inCutscene", "PlayState.instance.isInCutscene"}, {"cpuControlled", "PlayState.instance.isBotPlayMode"},
            {"paused", "PlayState.instance.isGamePaused"}, {"startingSong", "PlayState.instance.startingSong"},
            {"songSpeed", "PlayState.instance.playerStrumline.scrollSpeed"}, {"camZooming", "PlayState.instance.cameraZoomRate > 0"},
            {"playbackRate", "PlayState.instance.playbackRate"}};
        auto it = fields.find(key);
        if (it == fields.end()) return "";
        use("PlayState");
        use("Highscore");
        return it->second;
    }

    std::string pathTail(const std::vector<std::string>& parts, size_t from) {
        std::string out;
        if (parts.size() >= from + 3 && parts[parts.size() - 3] == "animation" && parts[parts.size() - 2] == "curAnim") {
            std::vector<std::string> head(parts.begin(), parts.end() - 3);
            const std::string& last = parts.back();
            std::string tail = pathTail(head, from);
            if (last == "name") return tail + ".getCurrentAnimation()";
            if (last == "finished") return tail + ".isAnimationFinished()";
            if (last == "curFrame") return tail + ".animation.curAnim.curFrame";
        }
        for (size_t i = from; i < parts.size(); i++) {
            const std::string& part = parts[i];
            double n;
            if (isNumberText(part, n)) out += "[" + part + "]";
            else if (isIdentifier(part)) out += "." + part;
            else out += "." + safeName(part);
        }
        return out;
    }

    std::vector<std::string> split(const std::string& path) {
        std::vector<std::string> parts;
        std::string current;
        for (char c : path) {
            if (c == '.') {
                parts.push_back(current);
                current.clear();
            } else if (c == '[') {
                parts.push_back(current);
                current.clear();
            } else if (c != ']') {
                current += c;
            }
        }
        parts.push_back(current);
        std::vector<std::string> clean;
        for (const std::string& p : parts) if (!p.empty()) clean.push_back(p);
        return clean;
    }

    std::string rootObject(const std::string& name, int line) {
        std::string field = tagField(name);
        if (!field.empty()) return field;
        auto f = fieldNames_.find(name);
        if (f != fieldNames_.end()) return f->second;
        std::string g = gameObject(name);
        if (!g.empty()) return g;
        if (opt_.stageProps.count(name) || opt_.base == Base::Stage) {
            if (opt_.base == Base::Stage) return "getNamedProp(" + hxString(name) + ")";
            use("PlayState");
            return "PlayState.instance.currentStage.getNamedProp(" + hxString(name) + ")";
        }
        if (dynamicTags_) return "sprites.get(" + hxString(name) + ")";
        use("PlayState");
        issue(line, "\"" + name + "\" is not made by this script; it is read from the stage's props.");
        return "PlayState.instance.currentStage.getNamedProp(" + hxString(name) + ")";
    }

    std::string object(const Expr* e, int line) {
        e = strip(e);
        if (!e) return "null";
        if (isString(e)) {
            std::string path = stringOf(e);
            if (path.compare(0, 5, "game.") == 0) path = path.substr(5);
            std::vector<std::string> parts = split(path);
            if (parts.empty()) return "null";
            return rootObject(parts[0], line) + pathTail(parts, 1);
        }
        if (dynamicTags_) return "sprites.get(" + expr(e) + ")";
        issue(line, "the object name is computed at runtime; it is looked up in the stage's props.");
        use("PlayState");
        return "PlayState.instance.currentStage.getNamedProp(" + expr(e) + ")";
    }

    std::string property(const Expr* e, int line) {
        e = strip(e);
        if (isString(e)) {
            std::string path = stringOf(e);
            if (path.compare(0, 5, "game.") == 0) path = path.substr(5);
            std::vector<std::string> parts = split(path);
            if (parts.size() == 1) {
                std::string ps = playStateField(parts[0]);
                if (!ps.empty()) return ps;
                if (!tagField(parts[0]).empty() || fieldNames_.count(parts[0])) return rootObject(parts[0], line);
                std::string g = gameObject(parts[0]);
                if (!g.empty()) return g;
                use("PlayState");
                return "PlayState.instance." + safeName(parts[0]);
            }
            if (!parts.empty()) {
                std::string root = parts[0];
                if (tagField(root).empty() && !fieldNames_.count(root) && gameObject(root).empty() && !opt_.stageProps.count(root) && opt_.base != Base::Stage) {
                    std::string ps = playStateField(root);
                    use("PlayState");
                    if (!ps.empty()) return ps + pathTail(parts, 1);
                    static const std::set<std::string> playState = {"strumLineNotes", "unspawnNotes", "eventNotes", "comboGroup", "uiGroup",
                        "noteGroup", "timeBar", "timeTxt", "botplayTxt", "boyfriendCameraOffset", "opponentCameraOffset", "camHUD", "camGame"};
                    if (playState.count(root)) {
                        issue(line, "\"" + root + "\" is a Psych PlayState object; V-Slice names it differently or has none.");
                        return "PlayState.instance." + root + pathTail(parts, 1);
                    }
                }
            }
            return object(e, line);
        }
        use("Reflect");
        use("PlayState");
        issue(line, "the property path is computed at runtime; it is read from PlayState with Reflect.");
        return "";
    }

    std::string camera(const Expr* e) {
        use("PlayState");
        e = strip(e);
        if (!e || e->kind == ExprKind::Nil) return "PlayState.instance.camGame";
        if (isString(e)) {
            std::string key = lower(stringOf(e));
            if (key == "hud" || key == "camhud") return "PlayState.instance.camHUD";
            if (key == "other" || key == "camother") return "PlayState.instance.camCutscene";
            return "PlayState.instance.camGame";
        }
        std::string v = expr(e);
        return "(" + v + " == \"hud\" || " + v + " == \"camHUD\" ? PlayState.instance.camHUD : (" + v + " == \"other\" || " + v + " == \"camOther\" ? PlayState.instance.camCutscene : PlayState.instance.camGame))";
    }

    std::string color(const Expr* e) {
        e = strip(e);
        if (!e || e->kind == ExprKind::Nil) return "0xFFFFFFFF";
        if (isString(e)) {
            std::string text = trim(stringOf(e));
            if (text.compare(0, 2, "0x") == 0 || text.compare(0, 2, "0X") == 0) text = text.substr(2);
            if (!text.empty() && text[0] == '#') text = text.substr(1);
            bool hex = !text.empty() && std::all_of(text.begin(), text.end(), [](char c) { return std::isxdigit(static_cast<unsigned char>(c)); });
            if (hex && text.size() == 6) {
                std::string up = text;
                for (char& c : up) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
                return "0xFF" + up;
            }
            if (hex && text.size() == 8) {
                std::string up = text;
                for (char& c : up) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
                return "0x" + up;
            }
            use("FlxColor");
            return "FlxColor.fromString(" + hxString(stringOf(e)) + ")";
        }
        if (e->kind == ExprKind::Number) return expr(e);
        use("FlxColor");
        use("StringTools");
        std::string v = expr(e);
        return "FlxColor.fromString(StringTools.startsWith(" + v + ", \"0x\") || StringTools.startsWith(" + v + ", \"#\") ? " + v + " : \"#\" + " + v + ")";
    }

    std::string ease(const Expr* e) {
        use("FlxEase");
        e = strip(e);
        if (!e || e->kind == ExprKind::Nil) return "FlxEase.linear";
        if (isString(e)) {
            std::string key = lower(stringOf(e));
            for (const std::string& name : kEaseNames) {
                if (lower(name) == key) return "FlxEase." + name;
            }
            return "FlxEase.linear";
        }
        use("Reflect");
        return "Reflect.field(FlxEase, " + expr(e) + ")";
    }

    std::string character(const Expr* e) {
        use("PlayState");
        e = strip(e);
        if (isString(e)) {
            std::string key = lower(stringOf(e));
            if (key == "dad" || key == "opponent" || key == "1") return "PlayState.instance.currentStage.getDad()";
            if (key == "gf" || key == "girlfriend" || key == "2") return "PlayState.instance.currentStage.getGirlfriend()";
            if (key == "bf" || key == "boyfriend" || key == "0") return "PlayState.instance.currentStage.getBoyfriend()";
            return object(e, e->line);
        }
        if (e && e->kind == ExprKind::Number) {
            if (e->text == "1") return "PlayState.instance.currentStage.getDad()";
            if (e->text == "2") return "PlayState.instance.currentStage.getGirlfriend()";
            return "PlayState.instance.currentStage.getBoyfriend()";
        }
        std::string v = expr(e);
        return "(" + v + " == \"dad\" ? PlayState.instance.currentStage.getDad() : (" + v + " == \"gf\" ? PlayState.instance.currentStage.getGirlfriend() : PlayState.instance.currentStage.getBoyfriend()))";
    }

    bool isCharacterTag(const Expr* e) {
        e = strip(e);
        if (!isString(e)) return false;
        std::string key = lower(stringOf(e));
        return key == "dad" || key == "boyfriend" || key == "bf" || key == "gf" || key == "girlfriend";
    }

    std::string strum(const Expr* e) {
        use("PlayState");
        e = strip(e);
        double n;
        if (e && e->kind == ExprKind::Number && isNumberText(e->text, n)) {
            int i = static_cast<int>(n);
            return std::string("PlayState.instance.") + (i < 4 ? "opponentStrumline" : "playerStrumline") + ".strumlineNotes.members[" + std::to_string(i % 4) + "]";
        }
        std::string v = operand(e, "%", false);
        return "(" + v + " < 4 ? PlayState.instance.opponentStrumline : PlayState.instance.playerStrumline).strumlineNotes.members[" + v + " % 4]";
    }

    std::string container() {
        if (opt_.base == Base::Stage) return "";
        use("PlayState");
        return "PlayState.instance.currentStage.";
    }

    std::string tweenOptions(const Expr* easeArg, const std::string& tag, const Expr* tagExpr) {
        std::string out = "{ease: " + ease(easeArg);
        if (usesTweenCallback_) {
            std::string tagValue = tagExpr ? expr(tagExpr) : hxString(tag);
            out += ", onComplete: function(tween:FlxTween) { onTweenCompleted(" + tagValue + "); }";
        }
        return out + "}";
    }

    std::string storeTween(const Expr* tagExpr, const std::string& tween) {
        if (isString(tagExpr) && cancelledTweens_.count(stringOf(tagExpr))) return tweenField(stringOf(tagExpr)) + " = " + tween;
        return tween;
    }

    std::string tweenField(const std::string& tag) {
        std::string name = safeName(tag) + "Tween";
        if (!fieldTypes_.count(name)) {
            fieldTypes_[name] = "FlxTween";
            use("FlxTween");
        }
        return name;
    }

    std::string timerField(const std::string& tag) {
        std::string name = safeName(tag) + "Timer";
        if (!fieldTypes_.count(name)) {
            fieldTypes_[name] = "FlxTimer";
            use("FlxTimer");
        }
        return name;
    }

    std::string soundField(const std::string& tag) {
        std::string name = safeName(tag) + "Sound";
        if (!fieldTypes_.count(name)) {
            fieldTypes_[name] = "FunkinSound";
            use("FunkinSound");
        }
        return name;
    }

    std::string sound(const Expr* tag) {
        tag = strip(tag);
        if (!tag || (isString(tag) && stringOf(tag).empty()) || tag->kind == ExprKind::Nil) return "FlxG.sound.music";
        if (isString(tag)) return soundField(stringOf(tag));
        return "FlxG.sound.music";
    }

    bool usesLayers_ = false;
    bool removesSprites_ = false;

    std::string addSprite(const std::string& sprite, const Expr* front) {
        front = strip(front);
        usesLayers_ = true;
        bool inFront = front && front->kind == ExprKind::True;
        bool dynamic = front && front->kind != ExprKind::True && front->kind != ExprKind::False && front->kind != ExprKind::Nil;
        std::string list = dynamic ? "(" + cond(front) + " ? frontSprites : behindSprites)" : (inFront ? "frontSprites" : "behindSprites");
        std::string out = container() + "add(" + sprite + ");\n" + list + ".push(" + sprite + ");";
        if (inCreate_) {
            addedInCreate_ = true;
            return out;
        }
        return out + "\nlayerSprites();";
    }

    std::string layerHelper() const {
        std::string stage = opt_.base == Base::Stage ? "this" : "PlayState.instance.currentStage";
        return
            "function layerSprites():Void\n"
            "{\n"
            "  var stage:Stage = " + stage + ";\n"
            "  if (stage == null) return;\n"
            "  var low:Int = 0;\n"
            "  var high:Int = 0;\n"
            "  var found:Bool = false;\n"
            "  for (character in [stage.getGirlfriend(), stage.getDad(), stage.getBoyfriend()])\n"
            "  {\n"
            "    if (character == null) continue;\n"
            "    if (!found || character.zIndex < low) low = character.zIndex;\n"
            "    if (!found || character.zIndex > high) high = character.zIndex;\n"
            "    found = true;\n"
            "  }\n"
            "  if (stage.getGirlfriend() != null) low = stage.getGirlfriend().zIndex;\n"
            "  for (i in 0...behindSprites.length) behindSprites[i].zIndex = low - behindSprites.length + i;\n"
            "  for (i in 0...frontSprites.length) frontSprites[i].zIndex = high + 1 + i;\n"
            "  stage.refresh();\n"
            "}";
    }

    void lines(Code& code, const std::string& text) {
        std::istringstream in(text);
        std::string l;
        while (std::getline(in, l)) code.line(l);
    }

    std::string call(const Expr& e, Code* code) {
        std::string name = calleeName(e);
        const auto& a = e.args;
        int line = e.line;
        if (!name.empty() && (local(name) || fieldNames_.count(name))) return expr(e.a.get()) + "(" + args(a) + ")";
        const Expr* callee = strip(e.a.get());
        if (callee->kind != ExprKind::Name) {
            static const std::set<std::string> libs = {"math", "string", "table", "os", "io", "debug", "bit", "coroutine"};
            const Expr* root = callee;
            while (root->kind == ExprKind::Index) root = strip(root->a.get());
            if (!(root->kind == ExprKind::Name && libs.count(root->text) && !local(root->text) && !fieldNames_.count(root->text))) {
                return expr(callee) + "(" + args(a) + ")";
            }
        }
        auto fn = functions_.find(name);
        if (fn != functions_.end()) {
            std::string target = methodNames_.count(name) ? methodNames_[name] : name;
            return target + "(" + args(a) + ")";
        }
        std::string mapped = library(name, e, code);
        if (mapped != "\x01") return mapped;
        mapped = psychApi(name, e, code);
        if (mapped != "\x01") {
            api_.insert(name);
            return mapped;
        }
        if (!name.empty()) issue(line, name + "() is not a Psych or Lua function V-Converter knows; it was kept as a call.");
        return expr(e.a.get()) + "(" + args(a) + ")";
    }

    std::string library(const std::string& name, const Expr& e, Code* code) {
        const auto& a = e.args;
        int line = e.line;
        if (name == "print") {
            std::string out = "trace(";
            for (size_t i = 0; i < a.size(); i++) {
                if (i) out += " + \" \" + ";
                out += expr(a[i].get());
            }
            return out + ")";
        }
        if (name == "tostring") return "Std.string(" + arg(a, 0, "null") + ")";
        if (name == "tonumber") {
            const Expr* base = argAt(a, 1);
            if (base && strip(base)->kind == ExprKind::Number && strip(base)->text == "16") return "Std.parseInt(\"0x\" + " + arg(a, 0, "\"\"") + ")";
            return "Std.parseFloat(" + arg(a, 0, "\"\"") + ")";
        }
        if (name == "type") {
            use("Type");
            issue(line, "type() returns a Haxe ValueType; comparisons with Lua type names were converted where found.");
            return "Type.typeof(" + arg(a, 0, "null") + ")";
        }
        if (name == "error") return "throw " + arg(a, 0, "\"error\"");
        if (name == "assert") return arg(a, 0, "null");
        if (name == "unpack" || name == "table.unpack" || name == "select" || name == "next" || name == "require" || name == "setmetatable" ||
            name == "getmetatable" || name == "rawget" || name == "rawset" || name == "loadstring" || name == "dofile") {
            issue(line, name + "() has no Haxe match.");
            return "null";
        }
        if (name == "pcall") {
            if (code && !a.empty()) {
                code->open("try");
                code->line(expr(a[0].get()) + "(" + args(a, 1) + ");");
                code->close();
                code->line("catch (e:Dynamic) {}");
                return "";
            }
            issue(line, "pcall in an expression became a direct call.");
            return a.empty() ? "null" : expr(a[0].get()) + "(" + args(a, 1) + ")";
        }
        if (name.compare(0, 5, "math.") == 0) {
            std::string fn = name.substr(5);
            static const std::set<std::string> direct = {"floor", "ceil", "abs", "sqrt", "sin", "cos", "tan", "asin", "acos", "atan", "exp", "log", "pow"};
            if (direct.count(fn)) {
                if (fn == "log" && a.size() > 1) return "(Math.log(" + arg(a, 0, "0") + ") / Math.log(" + arg(a, 1, "10") + "))";
                return "Math." + fn + "(" + args(a) + ")";
            }
            if (fn == "atan2") return "Math.atan2(" + args(a) + ")";
            if (fn == "min" || fn == "max") {
                if (a.empty()) return "0";
                std::string out = expr(a[0].get());
                for (size_t i = 1; i < a.size(); i++) out = "Math." + fn + "(" + out + ", " + expr(a[i].get()) + ")";
                return out;
            }
            if (fn == "random") {
                if (a.empty()) return "FlxG.random.float()";
                if (a.size() == 1) return "FlxG.random.int(1, " + expr(a[0].get()) + ")";
                return "FlxG.random.int(" + expr(a[0].get()) + ", " + expr(a[1].get()) + ")";
            }
            if (fn == "fmod") return operand(a[0].get(), "%", false) + " % " + operand(a[1].get(), "%", true);
            if (fn == "rad") return "(" + arg(a, 0, "0") + " * Math.PI / 180)";
            if (fn == "deg") return "(" + arg(a, 0, "0") + " * 180 / Math.PI)";
            if (fn == "randomseed") return code ? "" : "null";
            issue(line, name + "() has no Haxe match.");
            return "null";
        }
        if (name.compare(0, 7, "string.") == 0) {
            if (a.empty()) return "\"\"";
            std::string fn = name.substr(7);
            std::string self = expr(a[0].get());
            if (strip(a[0].get())->kind == ExprKind::Binary) self = "(" + self + ")";
            if (fn == "format") return format(a[0].get(), a, 1, line);
            if (fn == "char") {
                std::string out;
                for (size_t i = 0; i < a.size(); i++) {
                    if (i) out += " + ";
                    out += "String.fromCharCode(" + expr(a[i].get()) + ")";
                }
                return out;
            }
            return stringFunction(fn, self, a, 1, line);
        }
        if (name.compare(0, 6, "table.") == 0) {
            std::string fn = name.substr(6);
            if (a.empty()) return "null";
            std::string t = expr(a[0].get());
            if (fn == "insert") {
                if (a.size() >= 3) return t + ".insert(" + offsetIndex(a[1].get()) + ", " + expr(a[2].get()) + ")";
                return t + ".push(" + arg(a, 1, "null") + ")";
            }
            if (fn == "remove") {
                if (a.size() >= 2) return t + ".splice(" + offsetIndex(a[1].get()) + ", 1)[0]";
                return t + ".pop()";
            }
            if (fn == "concat") return t + ".join(" + arg(a, 1, "\"\"") + ")";
            if (fn == "sort") {
                if (a.size() >= 2) return t + ".sort(function(x, y) return " + expr(a[1].get()) + "(x, y) ? -1 : 1)";
                use("Reflect");
                return t + ".sort(Reflect.compare)";
            }
            if (fn == "getn") return t + ".length";
            issue(line, name + "() has no Haxe match.");
            return "null";
        }
        if (name == "os.time" || name == "os.clock") {
            use("Date");
            return "Date.now().getTime() / 1000";
        }
        if (name == "os.date") {
            use("Date");
            issue(line, "os.date became Date.now().toString().");
            return "Date.now().toString()";
        }
        if (name.compare(0, 3, "io.") == 0 || name.compare(0, 3, "os.") == 0 || name.compare(0, 6, "debug.") == 0) {
            issue(line, name + "() is not available in V-Slice scripts.");
            return code ? "" : "null";
        }
        if (name == "ipairs" || name == "pairs") {
            issue(line, name + "() outside a for loop has no Haxe match.");
            return arg(a, 0, "null");
        }
        return "\x01";
    }

    std::string psychApi(const std::string& name, const Expr& e, Code* code) {
        const auto& a = e.args;
        int line = e.line;
        const Expr* a0 = argAt(a, 0);
        const Expr* a1 = argAt(a, 1);
        const Expr* a2 = argAt(a, 2);
        const Expr* a3 = argAt(a, 3);
        const Expr* a4 = argAt(a, 4);
        auto none = [&]() { return code ? std::string() : std::string("null"); };
        auto unsupported = [&](const std::string& why) {
            issue(line, name + "() " + why);
            return none();
        };
        auto spriteRef = [&](const Expr* tag) -> std::string {
            if (isString(tag)) {
                std::string field = tagField(stringOf(tag));
                if (!field.empty()) return field;
            }
            return object(tag, line);
        };
        auto isText = [&](const Expr* tag) {
            if (!isString(tag)) return false;
            auto it = tags_.find(stringOf(tag));
            return it != tags_.end() && it->second.kind == TagKind::Text;
        };
        auto created = [&](const Expr* tag, const std::string& value) -> std::string {
            if (isString(tag)) {
                std::string field = tagField(stringOf(tag));
                std::string out = field + " = " + value + ";";
                if (dynamicTags_) out += "\nsprites.set(" + hxString(stringOf(tag)) + ", " + field + ");";
                return out;
            }
            return "sprites.set(" + expr(tag) + ", " + value + ");";
        };
        auto createdRef = [&](const Expr* tag) -> std::string {
            if (isString(tag)) return tagField(stringOf(tag));
            return "sprites.get(" + expr(tag) + ")";
        };

        if (name == "makeLuaSprite") {
            use("FunkinSprite");
            use("Paths");
            const Expr* image = strip(a1);
            if (image && image->kind != ExprKind::Nil && !(isString(image) && stringOf(image).empty())) {
                return created(a0, "FunkinSprite.create(" + arg(a, 2, "0") + ", " + arg(a, 3, "0") + ", " + expr(image) + ")");
            }
            return created(a0, "new FunkinSprite(" + arg(a, 2, "0") + ", " + arg(a, 3, "0") + ")");
        }
        if (name == "makeAnimatedLuaSprite") {
            use("FunkinSprite");
            const Expr* kind = strip(a4);
            std::string factory = kind && isString(kind) && lower(stringOf(kind)) == "packer" ? "createPacker" : "createSparrow";
            return created(a0, "FunkinSprite." + factory + "(" + arg(a, 2, "0") + ", " + arg(a, 3, "0") + ", " + arg(a, 1, "\"\"") + ")");
        }
        if (name == "makeGraphic") return spriteRef(a0) + ".makeGraphic(" + arg(a, 1, "1") + ", " + arg(a, 2, "1") + ", " + color(a3) + ")";
        if (name == "makeLuaText") {
            use("FlxText");
            use("FlxTextBorderStyle");
            use("Paths");
            use("PlayState");
            std::string field = createdRef(a0);
            std::string out = created(a0, "new FlxText(" + arg(a, 3, "0") + ", " + arg(a, 4, "0") + ", " + arg(a, 2, "0") + ", " + arg(a, 1, "\"\"") + ", 16)");
            out += "\n" + field + ".setFormat(Paths.font(\"vcr.ttf\"), 16, 0xFFFFFFFF, \"left\", FlxTextBorderStyle.OUTLINE, 0xFF000000);";
            out += "\n" + field + ".borderSize = 2;";
            out += "\n" + field + ".cameras = [PlayState.instance.camHUD];";
            return out;
        }
        if (name == "addLuaSprite") return addSprite(spriteRef(a0), a1);
        if (name == "addLuaText") {
            use("PlayState");
            return "PlayState.instance.add(" + spriteRef(a0) + ")";
        }
        if (name == "removeLuaSprite" || name == "removeLuaText") {
            if (name == "removeLuaSprite") removesSprites_ = true;
            std::string obj = spriteRef(a0);
            std::string holder = name == "removeLuaText" || isText(a0) ? "PlayState.instance." : container();
            use("PlayState");
            const Expr* destroy = strip(a1);
            bool keep = destroy && destroy->kind == ExprKind::False;
            std::string out = holder + "remove(" + obj + ");";
            if (usesLayers_ || name == "removeLuaSprite") out += "\nbehindSprites.remove(" + obj + ");\nfrontSprites.remove(" + obj + ");";
            if (!keep) out += "\n" + obj + ".destroy();";
            if (!code) {
                issue(line, name + " inside an expression only removes the object.");
                return holder + "remove(" + obj + ")";
            }
            return out;
        }
        if (name == "luaSpriteExists" || name == "luaTextExists") return spriteRef(a0) + " != null";
        if (name == "addAnimationByPrefix") return spriteRef(a0) + ".animation.addByPrefix(" + arg(a, 1, "\"\"") + ", " + arg(a, 2, "\"\"") + ", " + arg(a, 3, "24") + ", " + (a4 ? cond(a4) : "true") + ")";
        if (name == "addAnimation") return spriteRef(a0) + ".animation.add(" + arg(a, 1, "\"\"") + ", " + arg(a, 2, "[]") + ", " + arg(a, 3, "24") + ", " + (a4 ? cond(a4) : "true") + ")";
        if (name == "addAnimationByIndices" || name == "addAnimationByIndicesLoop") {
            std::string indices;
            if (isString(a3)) {
                std::string list = stringOf(a3);
                std::string cleaned;
                for (char c : list) if (std::isdigit(static_cast<unsigned char>(c)) || c == ',' || c == '-') cleaned += c;
                std::string out = "[";
                std::string item;
                bool first = true;
                for (size_t i = 0; i <= cleaned.size(); i++) {
                    if (i == cleaned.size() || cleaned[i] == ',') {
                        if (!item.empty()) {
                            out += (first ? "" : ", ") + item;
                            first = false;
                        }
                        item.clear();
                    } else {
                        item += cleaned[i];
                    }
                }
                indices = out + "]";
            } else {
                indices = expr(a3) + ".split(\",\").map(function(i) return Std.parseInt(i))";
            }
            const Expr* loopArg = name == "addAnimationByIndicesLoop" ? nullptr : argAt(a, 5);
            std::string loop = name == "addAnimationByIndicesLoop" ? "true" : (loopArg ? cond(loopArg) : "false");
            return spriteRef(a0) + ".animation.addByIndices(" + arg(a, 1, "\"\"") + ", " + arg(a, 2, "\"\"") + ", " + indices + ", \"\", " + arg(a, 4, "24") + ", " + loop + ")";
        }
        if (name == "addOffset") {
            issue(line, "addOffset has no FunkinSprite match; set offset.x/offset.y after playing the animation instead.");
            return spriteRef(a0) + ".offset.set(" + arg(a, 2, "0") + ", " + arg(a, 3, "0") + ")";
        }
        if (name == "playAnim" || name == "objectPlayAnimation") {
            if (isCharacterTag(a0)) return character(a0) + ".playAnimation(" + arg(a, 1, "\"\"") + ", " + (a2 ? cond(a2) : "false") + ")";
            if (isString(a0) && tagField(stringOf(a0)).empty() && !dynamicTags_) {
                return object(a0, line) + ".playAnimation(" + arg(a, 1, "\"\"") + ", " + (a2 ? cond(a2) : "false") + ")";
            }
            std::string frame = name == "playAnim" ? arg(a, 4, "0") : arg(a, 3, "0");
            std::string reversed = name == "playAnim" && a3 ? cond(a3) : "false";
            return spriteRef(a0) + ".animation.play(" + arg(a, 1, "\"\"") + ", " + (a2 ? cond(a2) : "false") + ", " + reversed + ", " + frame + ")";
        }
        if (name == "characterPlayAnim" || name == "playCharacterAnim") return character(a0) + ".playAnimation(" + arg(a, 1, "\"\"") + ", " + (a2 ? cond(a2) : "false") + ")";
        if (name == "characterDance") return character(a0) + ".dance(true)";
        if (name == "getCharacterX") return character(a0) + ".x";
        if (name == "getCharacterY") return character(a0) + ".y";
        if (name == "setCharacterX") return character(a0) + ".x = " + arg(a, 1, "0");
        if (name == "setCharacterY") return character(a0) + ".y = " + arg(a, 1, "0");
        if (name == "setScrollFactor") return spriteRef(a0) + ".scrollFactor.set(" + arg(a, 1, "1") + ", " + arg(a, 2, "1") + ")";
        if (name == "scaleObject" || name == "scaleLuaSprite") {
            std::string obj = spriteRef(a0);
            bool update = !(a3 && strip(a3)->kind == ExprKind::False);
            if (code && update) return obj + ".scale.set(" + arg(a, 1, "1") + ", " + arg(a, 2, "1") + ");\n" + obj + ".updateHitbox();";
            return obj + ".scale.set(" + arg(a, 1, "1") + ", " + arg(a, 2, "1") + ")";
        }
        if (name == "setGraphicSize") {
            std::string obj = spriteRef(a0);
            bool update = !(a3 && strip(a3)->kind == ExprKind::False);
            if (code && update) return obj + ".setGraphicSize(" + arg(a, 1, "0") + ", " + arg(a, 2, "0") + ");\n" + obj + ".updateHitbox();";
            return obj + ".setGraphicSize(" + arg(a, 1, "0") + ", " + arg(a, 2, "0") + ")";
        }
        if (name == "updateHitbox") return spriteRef(a0) + ".updateHitbox()";
        if (name == "screenCenter") {
            use("FlxAxes");
            std::string axis = "XY";
            if (isString(a1)) {
                std::string k = lower(stringOf(a1));
                if (k == "x") axis = "X";
                else if (k == "y") axis = "Y";
            }
            return spriteRef(a0) + ".screenCenter(FlxAxes." + axis + ")";
        }
        if (name == "setObjectCamera") return spriteRef(a0) + ".cameras = [" + camera(a1) + "]";
        if (name == "setBlendMode") {
            use("BlendMode");
            if (isString(a1)) {
                std::string mode = stringOf(a1);
                for (char& c : mode) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
                return spriteRef(a0) + ".blend = BlendMode." + mode;
            }
            return spriteRef(a0) + ".blend = " + expr(a1) + ".toLowerCase()";
        }
        if (name == "setObjectOrder") {
            std::string obj = spriteRef(a0);
            if (code) return obj + ".zIndex = " + arg(a, 1, "0") + ";\n" + container() + "refresh();";
            return obj + ".zIndex = " + arg(a, 1, "0");
        }
        if (name == "getObjectOrder") return spriteRef(a0) + ".zIndex";
        if (name == "loadGraphic") {
            use("Paths");
            std::string grid = arg(a, 2, "0");
            if (a2) return spriteRef(a0) + ".loadGraphic(Paths.image(" + arg(a, 1, "\"\"") + "), true, " + grid + ", " + arg(a, 3, "0") + ")";
            return spriteRef(a0) + ".loadTexture(" + arg(a, 1, "\"\"") + ")";
        }
        if (name == "loadFrames") {
            use("Paths");
            return spriteRef(a0) + ".frames = Paths.getSparrowAtlas(" + arg(a, 1, "\"\"") + ")";
        }
        if (name == "getMidpointX") return spriteRef(a0) + ".getMidpoint().x";
        if (name == "getMidpointY") return spriteRef(a0) + ".getMidpoint().y";
        if (name == "getGraphicMidpointX") return spriteRef(a0) + ".getGraphicMidpoint().x";
        if (name == "getGraphicMidpointY") return spriteRef(a0) + ".getGraphicMidpoint().y";
        if (name == "getScreenPositionX") return spriteRef(a0) + ".getScreenPosition().x";
        if (name == "getScreenPositionY") return spriteRef(a0) + ".getScreenPosition().y";
        if (name == "objectsOverlap") return "FlxG.overlap(" + spriteRef(a0) + ", " + spriteRef(a1) + ")";
        if (name == "getPixelColor") return spriteRef(a0) + ".pixels.getPixel32(" + arg(a, 1, "0") + ", " + arg(a, 2, "0") + ")";

        if (name == "setTextString") return spriteRef(a0) + ".text = " + arg(a, 1, "\"\"");
        if (name == "getTextString") return spriteRef(a0) + ".text";
        if (name == "setTextSize") return spriteRef(a0) + ".size = " + arg(a, 1, "16");
        if (name == "getTextSize") return spriteRef(a0) + ".size";
        if (name == "setTextWidth") return spriteRef(a0) + ".fieldWidth = " + arg(a, 1, "0");
        if (name == "setTextColor") return spriteRef(a0) + ".color = " + color(a1);
        if (name == "setTextFont") {
            use("Paths");
            return spriteRef(a0) + ".font = Paths.font(" + arg(a, 1, "\"vcr.ttf\"") + ")";
        }
        if (name == "getTextFont") return spriteRef(a0) + ".font";
        if (name == "setTextItalic") return spriteRef(a0) + ".italic = " + (a1 ? cond(a1) : "true");
        if (name == "setTextAlignment") return spriteRef(a0) + ".alignment = " + arg(a, 1, "\"left\"") + ".toLowerCase()";
        if (name == "setTextAutoSize") return spriteRef(a0) + ".autoSize = " + (a1 ? cond(a1) : "true");
        if (name == "setTextBorder") {
            use("FlxTextBorderStyle");
            return spriteRef(a0) + ".setBorderStyle(FlxTextBorderStyle.OUTLINE, " + color(a2) + ", " + arg(a, 1, "1") + ")";
        }

        if (name == "getProperty" || name == "setProperty") {
            std::string target = property(a0, line);
            if (target.empty()) {
                if (name == "getProperty") return "Reflect.getProperty(PlayState.instance, " + expr(a0) + ")";
                return "Reflect.setProperty(PlayState.instance, " + expr(a0) + ", " + arg(a, 1, "null") + ")";
            }
            if (name == "getProperty") return target;
            if (isString(a0) && stringOf(a0) == "songSpeed" && code) {
                use("PlayState");
                return "PlayState.instance.playerStrumline.scrollSpeed = " + arg(a, 1, "1") + ";\nPlayState.instance.opponentStrumline.scrollSpeed = PlayState.instance.playerStrumline.scrollSpeed;";
            }
            if (isString(a0) && (stringOf(a0) == "camZooming" || stringOf(a0) == "cameraSpeed" || stringOf(a0) == "isCameraOnForcedPos")) {
                return unsupported("on \"" + stringOf(a0) + "\" has no V-Slice match; V-Slice zooms and moves the camera through song events.");
            }
            return target + " = " + arg(a, 1, "null");
        }
        if (name == "getPropertyFromGroup" || name == "setPropertyFromGroup") {
            std::string group;
            if (isString(a0)) {
                std::string g = stringOf(a0);
                if (g == "playerStrums") group = "PlayState.instance.playerStrumline.strumlineNotes.members[" + expr(a1) + "]";
                else if (g == "opponentStrums") group = "PlayState.instance.opponentStrumline.strumlineNotes.members[" + expr(a1) + "]";
                else if (g == "strumLineNotes") group = strum(a1);
                else if (g == "notes") group = "PlayState.instance.playerStrumline.notes.members[" + expr(a1) + "]";
            }
            use("PlayState");
            if (group.empty()) {
                return unsupported("on \"" + (isString(a0) ? stringOf(a0) : std::string("?")) + "\" has no V-Slice match; V-Slice keeps notes per strumline.");
            }
            std::string field = isString(a2) ? pathTail(split(stringOf(a2)), 0) : "";
            if (field.empty()) {
                use("Reflect");
                if (name == "getPropertyFromGroup") return "Reflect.getProperty(" + group + ", " + expr(a2) + ")";
                return "Reflect.setProperty(" + group + ", " + expr(a2) + ", " + arg(a, 3, "null") + ")";
            }
            if (name == "getPropertyFromGroup") return group + field;
            return group + field + " = " + arg(a, 3, "null");
        }
        if (name == "getPropertyFromClass" || name == "setPropertyFromClass") {
            std::string cls = isString(a0) ? stringOf(a0) : "";
            std::string field = isString(a1) ? stringOf(a1) : "";
            std::string shortCls = cls.substr(cls.find_last_of('.') == std::string::npos ? 0 : cls.find_last_of('.') + 1);
            std::string target;
            if (shortCls == "ClientPrefs") {
                std::string key = field.compare(0, 5, "data.") == 0 ? field.substr(5) : field;
                std::string k = lower(key);
                if (k == "downscroll") target = "Preferences.downscroll";
                else if (k == "flashing") target = "Preferences.flashingLights";
                else if (k == "framerate") target = "Preferences.framerate";
                else if (k == "middlescroll" || k == "lowquality") target = "false";
                else if (k == "ghosttapping" || k == "shaders" || k == "globalantialiasing" || k == "antialiasing") target = "true";
            } else if (shortCls == "FlxG") {
                target = "FlxG" + pathTail(split(field), 0);
            } else if (shortCls == "PlayState") {
                std::string f = field.compare(0, 9, "instance.") == 0 ? field.substr(9) : field;
                use("PlayState");
                std::string ps = playStateField(f);
                target = ps.empty() ? "PlayState.instance" + pathTail(split(f), 0) : ps;
            } else if (shortCls == "Conductor") {
                use("Conductor");
                std::string k = field;
                if (k == "songPosition") target = "Conductor.instance.songPosition";
                else if (k == "bpm") target = "Conductor.instance.bpm";
                else if (k == "crochet") target = "Conductor.instance.beatLengthMs";
                else if (k == "stepCrochet") target = "Conductor.instance.stepLengthMs";
            } else if (shortCls == "GameOverSubstate") {
                return unsupported("on GameOverSubstate has no V-Slice match; the game over uses the character's data instead.");
            }
            if (target.empty()) return unsupported("on " + (cls.empty() ? std::string("a class made at runtime") : cls + "." + field) + " has no V-Slice match.");
            if (target == "false" || target == "true") {
                if (name == "setPropertyFromClass") return unsupported("can't change " + field + "; V-Slice has no such option.");
                return target;
            }
            if (name == "getPropertyFromClass") return target;
            if (target.compare(0, 12, "Preferences.") == 0) issue(line, "this changes the player's saved " + target.substr(12) + " setting in V-Slice.");
            return target + " = " + arg(a, 2, "null");
        }
        if (name == "setVar" || name == "getVar") {
            if (!isString(a0)) return unsupported("with a name made at runtime has no V-Slice match.");
            std::string key = stringOf(a0);
            if (!fieldNames_.count(key)) {
                fieldNames_[key] = uniqueMember(key);
                fieldOrder_.push_back(key);
            }
            if (name == "getVar") return fieldNames_[key];
            return fieldNames_[key] + " = " + arg(a, 1, "null");
        }

        if (name == "doTweenX" || name == "doTweenY" || name == "doTweenAlpha" || name == "doTweenAngle") {
            use("FlxTween");
            std::string prop = name == "doTweenX" ? "x" : name == "doTweenY" ? "y" : name == "doTweenAlpha" ? "alpha" : "angle";
            std::string tag = isString(a0) ? stringOf(a0) : "";
            return storeTween(a0, "FlxTween.tween(" + spriteRef(a1) + ", {" + prop + ": " + arg(a, 2, "0") + "}, " + arg(a, 3, "1") + ", " + tweenOptions(a4, tag, a0) + ")");
        }
        if (name == "doTweenColor") {
            use("FlxTween");
            std::string obj = spriteRef(a1);
            std::string tag = isString(a0) ? stringOf(a0) : "";
            return storeTween(a0, "FlxTween.color(" + obj + ", " + arg(a, 3, "1") + ", " + obj + ".color, " + color(a2) + ", " + tweenOptions(a4, tag, a0) + ")");
        }
        if (name == "doTweenZoom") {
            use("FlxTween");
            use("PlayState");
            std::string tag = isString(a0) ? stringOf(a0) : "";
            std::string target = camera(a1) == "PlayState.instance.camGame" ? "PlayState.instance, {currentCameraZoom: " : camera(a1) + ", {zoom: ";
            return storeTween(a0, "FlxTween.tween(" + target + arg(a, 2, "1") + "}, " + arg(a, 3, "1") + ", " + tweenOptions(a4, tag, a0) + ")");
        }
        if (name == "noteTweenX" || name == "noteTweenY" || name == "noteTweenAlpha" || name == "noteTweenAngle" || name == "noteTweenDirection") {
            use("FlxTween");
            std::string prop = name == "noteTweenX" ? "x" : name == "noteTweenY" ? "y" : name == "noteTweenAlpha" ? "alpha" : name == "noteTweenAngle" ? "angle" : "direction";
            std::string tag = isString(a0) ? stringOf(a0) : "";
            if (prop == "direction") issue(line, "noteTweenDirection tweens the strum's direction field, which V-Slice strums don't use.");
            return storeTween(a0, "FlxTween.tween(" + strum(a1) + ", {" + prop + ": " + arg(a, 2, "0") + "}, " + arg(a, 3, "1") + ", " + tweenOptions(a4, tag, a0) + ")");
        }
        if (name == "startTween") {
            use("FlxTween");
            std::string values = a2 ? expr(a2) : "{}";
            std::string options = "{";
            const Expr* opts = strip(a4);
            std::string tag = isString(a0) ? stringOf(a0) : "";
            bool first = true;
            if (opts && opts->kind == ExprKind::Table) {
                for (const auto& f : opts->fields) {
                    if (f.kind != TableField::Kind::Named) continue;
                    if (!first) options += ", ";
                    first = false;
                    if (f.name == "ease") options += "ease: " + ease(f.value.get());
                    else if (f.name == "onComplete" && isString(f.value.get()) && functions_.count(stringOf(f.value.get()))) {
                        std::string fn = methodNames_.count(stringOf(f.value.get())) ? methodNames_[stringOf(f.value.get())] : stringOf(f.value.get());
                        options += "onComplete: function(tween:FlxTween) { " + fn + "(" + hxString(tag) + "); }";
                    } else if (f.name == "type") {
                        options += "type: " + (isString(f.value.get()) ? "FlxTweenType." + std::string(stringOf(f.value.get())) : expr(f.value.get()));
                        imports_.insert("flixel.tweens.FlxTween.FlxTweenType");
                    } else {
                        options += f.name + ": " + expr(f.value.get());
                    }
                }
            }
            options += "}";
            return storeTween(a0, "FlxTween.tween(" + spriteRef(a1) + ", " + values + ", " + arg(a, 3, "1") + ", " + options + ")");
        }
        if (name == "cancelTween") {
            if (isString(a0)) {
                std::string field = tweenField(stringOf(a0));
                if (code) return "if (" + field + " != null) " + field + ".cancel();";
                return field + ".cancel()";
            }
            return unsupported("with a tag made at runtime can't find its tween.");
        }
        if (name == "runTimer") {
            use("FlxTimer");
            std::string tag = isString(a0) ? hxString(stringOf(a0)) : expr(a0);
            std::string body = usesTimerCallback_ ? "onTimerCompleted(" + tag + ", timer.loops, timer.loopsLeft);" : "";
            std::string timer = "new FlxTimer().start(" + arg(a, 1, "1") + ", function(timer:FlxTimer) {" + (body.empty() ? "" : " " + body + " ") + "}, " + arg(a, 2, "1") + ")";
            if (isString(a0) && cancelledTimers_.count(stringOf(a0))) return timerField(stringOf(a0)) + " = " + timer;
            return timer;
        }
        if (name == "cancelTimer") {
            if (isString(a0)) {
                std::string field = timerField(stringOf(a0));
                if (code) return "if (" + field + " != null) " + field + ".cancel();";
                return field + ".cancel()";
            }
            return unsupported("with a tag made at runtime can't find its timer.");
        }

        if (name == "getRandomInt") return "FlxG.random.int(" + arg(a, 0, "0") + ", " + arg(a, 1, "100") + ")";
        if (name == "getRandomFloat") return "FlxG.random.float(" + arg(a, 0, "0") + ", " + arg(a, 1, "1") + ")";
        if (name == "getRandomBool") return "FlxG.random.bool(" + arg(a, 0, "50") + ")";
        if (name == "getSongPosition") {
            use("Conductor");
            return "Conductor.instance.songPosition";
        }
        if (name == "getColorFromHex" || name == "FlxColor" || name == "getColorFromString" || name == "getColorFromName") return color(a0);
        if (name == "stringStartsWith" || name == "stringEndsWith") {
            use("StringTools");
            return std::string("StringTools.") + (name == "stringStartsWith" ? "startsWith(" : "endsWith(") + arg(a, 0, "\"\"") + ", " + arg(a, 1, "\"\"") + ")";
        }
        if (name == "stringSplit") return arg(a, 0, "\"\"") + ".split(" + arg(a, 1, "\",\"") + ")";
        if (name == "stringTrim") {
            use("StringTools");
            return "StringTools.trim(" + arg(a, 0, "\"\"") + ")";
        }

        if (name == "setHealth") return "PlayState.instance.health = " + arg(a, 0, "1");
        if (name == "addHealth") return "PlayState.instance.health += " + arg(a, 0, "0");
        if (name == "getHealth") return "PlayState.instance.health";
        if (name == "setScore") return "PlayState.instance.songScore = " + arg(a, 0, "0");
        if (name == "addScore") return "PlayState.instance.songScore += " + arg(a, 0, "0");
        if (name == "getScore") return "PlayState.instance.songScore";
        if (name == "setMisses" || name == "addMisses" || name == "getMisses" || name == "setHits" || name == "addHits" || name == "getHits") {
            use("Highscore");
            std::string tally = name.find("Misses") != std::string::npos ? "Highscore.tallies.missed" : "Highscore.tallies.totalNotesHit";
            if (name.compare(0, 3, "get") == 0) return tally;
            return tally + (name.compare(0, 3, "add") == 0 ? " += " : " = ") + arg(a, 0, "0");
        }
        if (name == "setHealthBarColors") {
            use("PlayState");
            if (!code) return unsupported("inside an expression was skipped.");
            return "PlayState.instance.healthBar.createFilledBar(" + color(a0) + ", " + color(a1) + ");\nPlayState.instance.healthBar.updateBar();";
        }
        if (name == "setTimeBarColors" || name == "setRatingPercent" || name == "setRatingName" || name == "setRatingFC") return unsupported("has no V-Slice match.");

        if (name == "cameraShake") return camera(a0) + ".shake(" + arg(a, 1, "0.05") + ", " + arg(a, 2, "0.5") + ")";
        if (name == "cameraFlash") return camera(a0) + ".flash(" + color(a1) + ", " + arg(a, 2, "1") + ", null, " + (a3 ? cond(a3) : "false") + ")";
        if (name == "cameraFade") return camera(a0) + ".fade(" + color(a1) + ", " + arg(a, 2, "1") + ", false, null, " + (a3 ? cond(a3) : "false") + ")";
        if (name == "cameraSetTarget") {
            use("PlayState");
            std::string ch = character(a0);
            return "PlayState.instance.cameraFollowPoint.setPosition(" + ch + ".cameraFocusPoint.x, " + ch + ".cameraFocusPoint.y)";
        }
        if (name == "getCameraFollowX" || name == "getCameraFollowY") return "PlayState.instance.cameraFollowPoint." + std::string(name == "getCameraFollowX" ? "x" : "y");
        if (name == "setCameraFollowPoint" || name == "setCameraPosition") return "PlayState.instance.cameraFollowPoint.setPosition(" + arg(a, 0, "0") + ", " + arg(a, 1, "0") + ")";

        if (name == "playSound") {
            use("FunkinSound");
            use("Paths");
            std::string sfx = "FunkinSound.playOnce(Paths.sound(" + arg(a, 0, "\"\"") + "), " + arg(a, 1, "1") + ")";
            if (isString(a2) && soundTags_.count(stringOf(a2))) return soundField(stringOf(a2)) + " = " + sfx;
            return sfx;
        }
        if (name == "playMusic") {
            use("Paths");
            return "FlxG.sound.playMusic(Paths.music(" + arg(a, 0, "\"\"") + "), " + arg(a, 1, "1") + ", " + (a2 ? cond(a2) : "false") + ")";
        }
        if (name == "stopSound") {
            std::string s = sound(a0);
            return code ? "if (" + s + " != null) " + s + ".stop();" : s + ".stop()";
        }
        if (name == "pauseSound") return sound(a0) + ".pause()";
        if (name == "resumeSound") return sound(a0) + ".resume()";
        if (name == "soundFadeIn") return sound(a0) + ".fadeIn(" + arg(a, 1, "1") + ", " + arg(a, 2, "0") + ", " + arg(a, 3, "1") + ")";
        if (name == "soundFadeOut") return sound(a0) + ".fadeOut(" + arg(a, 1, "1") + ", " + arg(a, 2, "0") + ")";
        if (name == "setSoundVolume") return sound(a0) + ".volume = " + arg(a, 1, "1");
        if (name == "getSoundVolume") return sound(a0) + ".volume";
        if (name == "getSoundTime") return sound(a0) + ".time";
        if (name == "setSoundTime") return sound(a0) + ".time = " + arg(a, 1, "0");
        if (name == "luaSoundExists") return sound(a0) + " != null";
        if (name == "precacheImage") {
            use("Paths");
            return "FlxG.bitmap.add(Paths.image(" + arg(a, 0, "\"\"") + "))";
        }
        if (name == "precacheSound" || name == "precacheMusic" || name == "initLuaShader" || name == "addLuaScript" || name == "addHScript" ||
            name == "luaDebugMode" || name == "setOnLuas" || name == "setOnScripts" || name == "setOnHScript") {
            return code ? "" : "null";
        }

        if (name == "triggerEvent") {
            use("SongEventRegistry");
            use("SongEventData");
            use("Conductor");
            std::string id = isString(a0) ? hxString(eventIdFor(stringOf(a0))) : expr(a0);
            std::string value = "{value1: " + arg(a, 1, "\"\"") + ", value2: " + arg(a, 2, "\"\"") + "}";
            if (isString(a0)) {
                std::string key = lower(stringOf(a0));
                std::string compact;
                for (char c : key) if (c != ' ' && c != '_' && c != '-') compact += c;
                std::string time = "Conductor.instance.songPosition";
                auto fire = [&](const std::string& kind, const std::string& data) {
                    return "SongEventRegistry.handleEvent(new SongEventData(" + time + ", " + hxString(kind) + ", " + data + "))";
                };
                if (key == "play animation") {
                    std::string target = "\"dad\"";
                    if (a2 && isString(a2)) {
                        std::string t = lower(trim(stringOf(a2)));
                        target = t == "bf" || t == "boyfriend" || t == "1" ? "\"bf\"" : (t == "gf" || t == "girlfriend" || t == "2" ? "\"gf\"" : "\"dad\"");
                    } else if (a2) {
                        use("StringTools");
                        std::string v = "StringTools.trim(Std.string(" + expr(a2) + ")).toLowerCase()";
                        target = "[\"bf\", \"boyfriend\", \"1\"].indexOf(" + v + ") >= 0 ? \"bf\" : ([\"gf\", \"girlfriend\", \"2\"].indexOf(" + v + ") >= 0 ? \"gf\" : \"dad\")";
                    }
                    value = "{anim: " + arg(a, 1, "\"\"") + ", target: " + target + ", force: true}";
                    return fire("PlayAnimation", value);
                }
                if (key == "hey!") {
                    std::string t = a1 && isString(a1) ? lower(trim(stringOf(a1))) : "";
                    std::string bf = fire("PlayAnimation", "{anim: \"hey\", target: \"bf\", force: true}");
                    std::string gf = fire("PlayAnimation", "{anim: \"cheer\", target: \"gf\", force: true}");
                    if (t == "bf" || t == "boyfriend" || t == "0") return bf;
                    if (t == "gf" || t == "girlfriend" || t == "1") return gf;
                    std::string first = bf;
                    std::string second = gf;
                    if (a1 && !isString(a1)) {
                        std::string v = "Std.string(" + expr(a1) + ").toLowerCase()";
                        first = "if ([\"gf\", \"girlfriend\", \"1\"].indexOf(" + v + ") < 0) " + bf;
                        second = "if ([\"bf\", \"boyfriend\", \"0\"].indexOf(" + v + ") < 0) " + gf;
                    }
                    if (code) {
                        code->line(first + ";");
                        code->line(second + ";");
                        return "";
                    }
                    return "{ " + first + "; " + second + "; }";
                }
                if (key == "set gf speed") {
                    return fire("SetTargetBopSpeed", "{target: \"girlfriend\", rate: Std.parseInt(Std.string(" + arg(a, 1, "\"1\"") + "))}");
                }
                if (compact == "changecharacter") id = hxString("ChangeCharacter");
                else if (key == "change scroll speed") value = "{scroll: Std.parseFloat(" + arg(a, 1, "\"1\"") + "), ease: \"INSTANT\", absolute: false}";
                else if (key == "camera follow pos") value = "{char: -1, x: Std.parseFloat(" + arg(a, 1, "\"0\"") + "), y: Std.parseFloat(" + arg(a, 2, "\"0\"") + ")}";
            }
            return "SongEventRegistry.handleEvent(new SongEventData(Conductor.instance.songPosition, " + id + ", " + value + "))";
        }
        if (name == "debugPrint") {
            if (a.size() == 1) return "trace(" + expr(a[0].get()) + ")";
            std::string out = "trace(";
            for (size_t i = 0; i < a.size(); i++) {
                if (i) out += " + \" \" + ";
                std::string piece = expr(a[i].get());
                if (i == 0 && !isString(a[i].get())) piece = "Std.string(" + piece + ")";
                out += piece;
            }
            return out + ")";
        }
        if (name == "close") return code ? "closed = true;\nreturn;" : "closed = true";
        if (name == "endSong") return "PlayState.instance.endSong(true)";
        if (name == "startCountdown") return "PlayState.instance.startCountdown()";
        if (name == "restartSong") return "PlayState.instance.needsReset = true";
        if (name == "exitSong") return unsupported("has no V-Slice match; end the song with endSong() instead.");
        if (name == "startVideo") {
            use("VideoCutscene");
            use("Paths");
            return "VideoCutscene.play(Paths.videos(" + arg(a, 0, "\"\"") + "))";
        }
        if (name == "startDialogue" || name == "callOnLuas" || name == "callScript" || name == "callOnScripts" || name == "callOnHScript" ||
            name == "getRunningScripts" || name == "isRunning" || name == "removeLuaScript" || name == "removeHScript" || name == "runHaxeFunction" ||
            name == "openCustomSubstate" || name == "closeCustomSubstate" || name == "insertToCustomSubstate") {
            return unsupported("has no V-Slice match; scripts can't call each other in V-Slice.");
        }
        if (name == "setSpriteShader") {
            use("FlxRuntimeShader");
            use("Assets");
            use("Paths");
            return spriteRef(a0) + ".shader = new FlxRuntimeShader(Assets.getText(Paths.frag(" + arg(a, 1, "\"\"") + ")), null)";
        }
        if (name == "removeSpriteShader") return spriteRef(a0) + ".shader = null";
        if (name == "setShaderFloat") return spriteRef(a0) + ".shader.setFloat(" + arg(a, 1, "\"\"") + ", " + arg(a, 2, "0") + ")";
        if (name == "setShaderInt") return spriteRef(a0) + ".shader.setInt(" + arg(a, 1, "\"\"") + ", " + arg(a, 2, "0") + ")";
        if (name == "setShaderBool") return spriteRef(a0) + ".shader.setBool(" + arg(a, 1, "\"\"") + ", " + (a2 ? cond(a2) : "false") + ")";
        if (name == "setShaderFloatArray") return spriteRef(a0) + ".shader.setFloatArray(" + arg(a, 1, "\"\"") + ", " + arg(a, 2, "[]") + ")";
        if (name == "getShaderFloat") return spriteRef(a0) + ".shader.getFloat(" + arg(a, 1, "\"\"") + ")";

        if (name == "keyJustPressed" || name == "keyPressed" || name == "keyReleased") {
            use("PlayerSettings");
            std::string suffix = name == "keyJustPressed" ? "_P" : name == "keyReleased" ? "_R" : "";
            std::string key = isString(a0) ? lower(stringOf(a0)) : "";
            std::string control;
            if (key == "left" || key == "down" || key == "up" || key == "right") {
                std::string up = key;
                for (char& c : up) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
                control = "NOTE_" + up + suffix;
            } else if (key == "accept" || key == "back" || key == "pause" || key == "reset") {
                std::string up = key;
                for (char& c : up) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
                control = up + suffix;
            } else if (key == "space") {
                return std::string("FlxG.keys.") + (name == "keyJustPressed" ? "justPressed" : name == "keyReleased" ? "justReleased" : "pressed") + ".SPACE";
            }
            if (control.empty()) return unsupported("with a key made at runtime has no V-Slice match.");
            return "PlayerSettings.player1.controls." + control;
        }
        if (name == "keyboardJustPressed" || name == "keyboardPressed" || name == "keyboardReleased") {
            std::string state = name == "keyboardJustPressed" ? "justPressed" : name == "keyboardReleased" ? "justReleased" : "pressed";
            if (isString(a0) && isIdentifier(stringOf(a0))) {
                std::string key = stringOf(a0);
                for (char& c : key) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
                return "FlxG.keys." + state + "." + key;
            }
            use("FlxKey");
            std::string any = name == "keyboardJustPressed" ? "anyJustPressed" : name == "keyboardReleased" ? "anyJustReleased" : "anyPressed";
            return "FlxG.keys." + any + "([FlxKey.fromString(" + expr(a0) + ")])";
        }
        if (name == "mouseClicked" || name == "mousePressed" || name == "mouseReleased") {
            std::string button = isString(a0) ? lower(stringOf(a0)) : "left";
            std::string prefix = button == "right" ? "justPressedRight" : button == "middle" ? "justPressedMiddle" : "justPressed";
            if (name == "mousePressed") prefix = button == "right" ? "pressedRight" : button == "middle" ? "pressedMiddle" : "pressed";
            if (name == "mouseReleased") prefix = button == "right" ? "justReleasedRight" : button == "middle" ? "justReleasedMiddle" : "justReleased";
            return "FlxG.mouse." + prefix;
        }
        if (name == "getMouseX" || name == "getMouseY") return "FlxG.mouse.getScreenPosition(" + camera(a0) + ")." + (name == "getMouseX" ? "x" : "y");

        if (name == "runHaxeCode") {
            if (!isString(a0)) return unsupported("with code made at runtime can't be converted.");
            if (!code) {
                issue(line, "runHaxeCode inside an expression was moved out of it.");
                return "null";
            }
            std::string source = stringOf(a0);
            std::string out;
            std::istringstream in(source);
            std::string l;
            while (std::getline(in, l)) {
                std::string t = trim(l);
                if (t.compare(0, 7, "import ") == 0) {
                    std::string path = trim(t.substr(7));
                    if (!path.empty() && path.back() == ';') path.pop_back();
                    imports_.insert(path);
                    continue;
                }
                size_t at = 0;
                while ((at = l.find("game.", at)) != std::string::npos) {
                    bool start = at == 0 || (!std::isalnum(static_cast<unsigned char>(l[at - 1])) && l[at - 1] != '_' && l[at - 1] != '.');
                    if (start) {
                        l.replace(at, 5, "PlayState.instance.");
                        at += 19;
                    } else {
                        at += 5;
                    }
                }
                if (!t.empty()) out += "  " + trim(l) + "\n";
            }
            use("PlayState");
            issue(line, "runHaxeCode was inlined as Haxe; Psych-only names in it (setVar, game.*) may need a look.");
            if (out.empty()) return "";
            return "{\n" + out + "}";
        }
        if (name == "addHaxeLibrary") {
            if (isString(a0)) {
                std::string lib = stringOf(a0);
                std::string pkg = isString(a1) ? stringOf(a1) : "";
                imports_.insert(pkg.empty() ? lib : pkg + "." + lib);
            }
            return code ? "" : "null";
        }
        return "\x01";
    }

    void statementCall(const Expr& e, Code& code, int line) {
        std::string text;
        if (e.kind == ExprKind::Call) text = call(e, &code);
        else text = method(e);
        (void)line;
        if (text.empty()) return;
        if (text.find('\n') != std::string::npos) {
            lines(code, text);
            return;
        }
        if (text.back() == '}' || text.back() == ';') code.line(text);
        else code.line(text + ";");
    }

    bool hasReturn(const Block& block) {
        for (const auto& s : block) {
            if (s->kind == StatKind::Return) return true;
            if (hasReturn(s->body) || hasReturn(s->elseBody)) return true;
            for (const auto& c : s->clauses) if (hasReturn(c.body)) return true;
        }
        return false;
    }

    void block(const Block& stats, Code& code) {
        pushScope();
        for (size_t i = 0; i < stats.size(); i++) {
            const Stat& s = *stats[i];
            if (opt_.skipLines.count(s.line)) continue;
            if (s.kind == StatKind::Label && i + 1 == stats.size()) continue;
            statement(s, code);
        }
        popScope();
    }

    void assign(const Expr* target, const std::string& value, Code& code) {
        target = strip(target);
        if (target->kind == ExprKind::Name) {
            std::string name = nameRef(target->text, target->line);
            code.line(name + " = " + value + ";");
            return;
        }
        if (target->kind == ExprKind::Index) {
            TableKind kind = tableKindOf(target->a.get());
            const Expr* key = strip(target->b.get());
            if (kind == TableKind::Map) {
                code.line(expr(target->a.get()) + ".set(" + expr(key) + ", " + value + ");");
                return;
            }
            if (key->kind == ExprKind::String && !isIdentifier(key->text)) {
                use("Reflect");
                code.line("Reflect.setField(" + expr(target->a.get()) + ", " + hxString(key->text) + ", " + value + ");");
                return;
            }
        }
        code.line(expr(target) + " = " + value + ";");
    }

    std::string returnValue(const Stat& s) {
        if (s.values.empty()) return "";
        if (s.values.size() > 1) issue(s.line, "only the first of several return values is kept.");
        return expr(s.values[0].get());
    }

    void statement(const Stat& s, Code& code) {
        switch (s.kind) {
            case StatKind::Local: {
                if (s.values.size() == 1 && s.names.size() > 1) issue(s.line, "multiple values from one call are not supported; only the first variable gets it.");
                std::vector<std::string> values;
                for (size_t i = 0; i < s.names.size(); i++) values.push_back(i < s.values.size() ? valueFor(s.names[i], s.values[i].get()) : "null");
                for (size_t i = 0; i < s.names.size(); i++) {
                    std::string name = declare(s.names[i]);
                    code.line("var " + name + (i >= s.values.size() ? ";" : " = " + values[i] + ";"));
                }
                for (size_t i = s.names.size(); i < s.values.size(); i++) {
                    const Expr* v = strip(s.values[i].get());
                    if (v->kind == ExprKind::Call || v->kind == ExprKind::Method) code.line(expr(v) + ";");
                }
                break;
            }
            case StatKind::Assign: {
                if (s.targets.size() == 1 && s.values.size() >= 1) {
                    const Expr* target = strip(s.targets[0].get());
                    assign(target, target->kind == ExprKind::Name ? valueFor(target->text, s.values[0].get()) : expr(s.values[0].get()), code);
                    break;
                }
                std::vector<std::string> temps;
                for (size_t i = 0; i < s.targets.size(); i++) {
                    std::string t = tmp();
                    code.line("var " + t + " = " + (i < s.values.size() ? expr(s.values[i].get()) : "null") + ";");
                    temps.push_back(t);
                }
                for (size_t i = 0; i < s.targets.size(); i++) assign(s.targets[i].get(), temps[i], code);
                break;
            }
            case StatKind::Call:
                statementCall(*s.values[0], code, s.line);
                break;
            case StatKind::Do:
                code.open("");
                block(s.body, code);
                code.close();
                break;
            case StatKind::While:
                code.open("while (" + cond(s.cond.get()) + ")");
                loopBody(s.body, code);
                code.close();
                break;
            case StatKind::Repeat:
                code.open("do");
                loopBody(s.body, code);
                code.close(" while (!(" + cond(s.cond.get()) + "));");
                break;
            case StatKind::If: {
                for (size_t i = 0; i < s.clauses.size(); i++) {
                    std::string head = (i == 0 ? "if (" : "else if (") + cond(s.clauses[i].cond.get()) + ")";
                    code.open(head);
                    block(s.clauses[i].body, code);
                    code.close();
                }
                if (s.hasElse && !s.elseBody.empty()) {
                    code.open("else");
                    block(s.elseBody, code);
                    code.close();
                }
                break;
            }
            case StatKind::NumericFor: numericFor(s, code); break;
            case StatKind::GenericFor: genericFor(s, code); break;
            case StatKind::Function: {
                if (s.path.size() == 1) {
                    if (scopes_.size() > 1) {
                        std::string target = nameRef(s.path[0], s.line);
                        code.line(target + " = " + function(*s.func) + ";");
                    }
                    break;
                }
                std::string target = nameRef(s.path[0], s.line);
                for (size_t i = 1; i < s.path.size(); i++) target += "." + s.path[i];
                if (s.method) issue(s.line, "a method declared with ':' became a plain function field; self is its first argument.");
                code.line(target + " = " + function(*s.func) + ";");
                break;
            }
            case StatKind::LocalFunction: {
                std::string name = declare(s.names[0]);
                std::string fn = function(*s.func);
                code.line("function " + name + fn.substr(8));
                break;
            }
            case StatKind::Return: {
                std::string value = returnValue(s);
                const Expr* v = s.values.empty() ? nullptr : strip(s.values[0].get());
                bool stop = v && v->kind == ExprKind::Name && (v->text == "Function_Stop" || v->text == "Function_StopLua" || v->text == "Function_StopAll" || v->text == "Function_StopHScript");
                if (inOverride_) {
                    if (stop && cancelable_) code.line("event.cancel();");
                    else if (stop) issue(s.line, "returning Function_Stop here can't cancel anything in V-Slice.");
                    code.line("return;");
                } else {
                    code.line(value.empty() ? "return;" : "return " + value + ";");
                }
                break;
            }
            case StatKind::Break: code.line("break;"); break;
            case StatKind::Goto:
                if (!loopContinue_.empty() && loopContinue_.back() == s.names[0]) code.line("continue;");
                else issue(s.line, "goto " + s.names[0] + " has no Haxe match.");
                break;
            case StatKind::Label: break;
        }
    }

    void loopBody(const Block& body, Code& code) {
        std::string label;
        if (!body.empty() && body.back()->kind == StatKind::Label) label = body.back()->names[0];
        loopContinue_.push_back(label);
        block(body, code);
        loopContinue_.pop_back();
    }

    void numericFor(const Stat& s, Code& code) {
        const Expr* start = s.values[0].get();
        const Expr* end = s.values[1].get();
        const Expr* step = s.values.size() > 2 ? s.values[2].get() : nullptr;
        pushScope();
        std::string var = declare(s.names[0]);
        double stepValue = 1;
        bool literalStep = !step || (strip(step)->kind == ExprKind::Number && isNumberText(strip(step)->text, stepValue));
        if (step && strip(step)->kind == ExprKind::Unary && strip(step)->text == "-" && strip(strip(step)->a.get())->kind == ExprKind::Number) {
            isNumberText(strip(strip(step)->a.get())->text, stepValue);
            stepValue = -stepValue;
            literalStep = true;
        }
        if (literalStep && stepValue == 1) {
            const Expr* stop = strip(end);
            std::string limit;
            double n;
            if (stop->kind == ExprKind::Number && isNumberText(stop->text, n)) limit = numberText(n + 1);
            else if (stop->kind == ExprKind::Binary && stop->text == "-" && strip(stop->b.get())->kind == ExprKind::Number && strip(stop->b.get())->text == "1") limit = expr(stop->a.get());
            else limit = operand(stop, "+", false) + " + 1";
            code.open("for (" + var + " in " + operand(start, "...", false) + "..." + limit + ")");
        } else {
            std::string count = tmp();
            std::string by = step ? operand(step, "*", true) : "1";
            std::string from = operand(start, "+", false);
            code.open("for (" + count + " in 0...Std.int(Math.max(0, Math.floor((" + expr(end) + " - " + from + ") / " + by + ") + 1)))");
            code.line("var " + var + " = " + from + " + " + count + " * " + by + ";");
        }
        loopBody(s.body, code);
        code.close();
        popScope();
    }

    bool usesName(const Block& body, const std::string& lua) {
        bool found = false;
        walkBlock(body, [&](const Expr& e) {
            if (e.kind == ExprKind::Name && e.text == lua) found = true;
        });
        return found;
    }

    void genericFor(const Stat& s, Code& code) {
        const Expr* it = strip(s.values[0].get());
        pushScope();
        std::string iterator = it->kind == ExprKind::Call ? calleeName(*it) : "";
        const Expr* subject = it->kind == ExprKind::Call && !it->args.empty() ? it->args[0].get() : nullptr;
        TableKind kind = tableKindOf(subject);
        bool array = iterator == "ipairs" || (iterator == "pairs" && kind == TableKind::Array);
        if ((iterator == "ipairs" || iterator == "pairs") && subject) {
            std::string table = expr(subject);
            std::string keyLua = s.names[0];
            std::string valueLua = s.names.size() > 1 ? s.names[1] : "";
            bool keyUsed = keyLua != "_" && usesName(s.body, keyLua);
            if (array) {
                if (!keyUsed && !valueLua.empty()) {
                    code.open("for (" + declare(valueLua) + " in " + table + ")");
                } else {
                    std::string index = tmp();
                    code.open("for (" + index + " in 0..." + table + ".length)");
                    code.line("var " + declare(keyLua) + " = " + index + " + 1;");
                    if (!valueLua.empty()) code.line("var " + declare(valueLua) + " = " + table + "[" + index + "];");
                }
            } else if (kind == TableKind::Map) {
                std::string key = declare(keyLua);
                code.open("for (" + key + " in " + table + ".keys())");
                if (!valueLua.empty()) code.line("var " + declare(valueLua) + " = " + table + ".get(" + key + ");");
            } else {
                use("Reflect");
                std::string key = declare(keyLua);
                code.open("for (" + key + " in Reflect.fields(" + table + "))");
                if (!valueLua.empty()) code.line("var " + declare(valueLua) + " = Reflect.field(" + table + ", " + key + ");");
            }
        } else if ((iterator == "string.gmatch" || (it->kind == ExprKind::Method && it->text == "gmatch")) ) {
            const Expr* str = it->kind == ExprKind::Method ? it->a.get() : subject;
            const Expr* pattern = it->kind == ExprKind::Method ? argAt(it->args, 0) : argAt(it->args, 1);
            use("EReg");
            std::string re = tmp();
            std::string rest = tmp();
            code.line("var " + re + " = new EReg(" + (pattern && isString(pattern) ? hxString(luaPatternToRegex(stringOf(pattern))) : expr(pattern)) + ", \"\");");
            code.line("var " + rest + ":String = " + expr(str) + ";");
            code.open("while (" + re + ".match(" + rest + "))");
            code.line("var " + declare(s.names[0]) + " = " + re + ".matched(" + (re.empty() ? "0" : "0") + ");");
            code.line(rest + " = " + re + ".matchedRight();");
        } else {
            issue(s.line, "this for-in loop uses a custom iterator; it was turned into a Haxe for over the value.");
            code.open("for (" + declare(s.names[0]) + " in " + expr(it) + ")");
        }
        loopBody(s.body, code);
        code.close();
        popScope();
    }

    struct HookParam {
        std::string type;
        std::string value;
    };

    std::string bind(const std::string& lua, const HookParam& param, const Block& body, Code& code) {
        std::string name = declare(lua);
        if (usesName(body, lua)) code.line("var " + name + ":" + param.type + " = " + param.value + ";");
        return name;
    }

    std::vector<HookParam> paramsFor(const std::string& psych) {
        use("PlayState");
        if (psych == "onUpdate" || psych == "onUpdatePost") return {{"Float", "event.elapsed"}};
        if (psych == "onBeatHit") return {};
        if (psych == "onEvent") return {{"String", "event.eventData.eventKind"}, {"Dynamic", "Reflect.field(event.eventData.value, \"value1\")"},
            {"Dynamic", "Reflect.field(event.eventData.value, \"value2\")"}, {"Float", "event.eventData.time"}};
        if (psych == "goodNoteHit" || psych == "opponentNoteHit" || psych == "goodNoteHitPost" || psych == "opponentNoteHitPost" ||
            psych == "noteMiss" || psych == "noteMissPost" || psych == "onSpawnNote" || psych == "onSpawnNotePost") {
            return {{"Int", "0"}, {"Int", "event.note.noteData.getDirection()"}, {"String", "event.note.kind"}, {"Bool", "false"},
                {"Float", "event.note.strumTime"}};
        }
        if (psych == "noteMissPress") return {{"Int", "event.dir"}};
        if (psych == "onCountdownTick") return {{"Int", "[\"THREE\", \"TWO\", \"ONE\", \"GO\", \"AFTER\"].indexOf(Std.string(event.step))"}};
        return {};
    }

    std::string hookBody(const std::string& psych, Code& code, const std::string& extraCheck = "") {
        const FuncBody* func = functions_.at(psych);
        if (!extraCheck.empty()) code.open("if (" + extraCheck + ")");
        if (referencedFunctions_.count(psych) || (methodForm_.count(psych))) {
            std::string callArgs;
            std::vector<HookParam> params = paramsFor(psych);
            for (size_t i = 0; i < func->params.size(); i++) {
                if (i) callArgs += ", ";
                callArgs += i < params.size() ? params[i].value : "null";
            }
            if (psych == "onEvent" || psych == "onCountdownTick") use("Reflect");
            bool cancel = cancelable_;
            if (cancel) code.line("if (" + psych + "(" + callArgs + ") == true) event.cancel();");
            else code.line(psych + "(" + callArgs + ");");
            if (!extraCheck.empty()) code.close();
            hookMethods_.insert(psych);
            return psych;
        }
        pushScope();
        std::vector<HookParam> params = paramsFor(psych);
        std::string savedEvent = eventParam_;
        std::string savedNote = noteTypeParam_;
        for (size_t i = 0; i < func->params.size(); i++) {
            if (i < params.size()) {
                std::string bound = bind(func->params[i], params[i], func->body, code);
                if (params[i].value.find("eventKind") != std::string::npos) eventParam_ = bound;
                if (params[i].value.find("note.kind") != std::string::npos) noteTypeParam_ = bound;
            } else {
                declare(func->params[i]);
            }
        }
        if (psych == "onEvent" && !func->params.empty()) {
            nativeEventBindings(func, code, func->params.size() > 1 ? *local(func->params[1]) : "", func->params.size() > 2 ? *local(func->params[2]) : "", *local(func->params[0]));
        }
        bool savedOverride = inOverride_;
        inOverride_ = true;
        block(func->body, code);
        inOverride_ = savedOverride;
        eventParam_ = savedEvent;
        noteTypeParam_ = savedNote;
        popScope();
        if (!extraCheck.empty()) code.close();
        return psych;
    }

    void nativeEventBindings(const FuncBody* func, Code& code, const std::string& v1, const std::string& v2, const std::string& nameVar) {
        bool anim = false;
        bool focus = false;
        bool scroll = false;
        walkBlock(func->body, [&](const Expr& e) {
            if (e.kind != ExprKind::String) return;
            std::string id = eventIdFor(e.text);
            if (id == "PlayAnimation") anim = true;
            if (id == "FocusCamera") focus = true;
            if (id == "ScrollSpeed") scroll = true;
        });
        bool hasValues = (!v1.empty() && usesName(func->body, func->params[1])) || (!v2.empty() && func->params.size() > 2 && usesName(func->body, func->params[2]));
        if (!hasValues) return;
        auto set = [&](const std::string& var, const std::string& value) {
            if (!var.empty()) code.line(var + " = " + value + ";");
        };
        if (anim) {
            code.open("if (" + nameVar + " == \"PlayAnimation\")");
            set(v1, "event.eventData.value.anim");
            set(v2, "event.eventData.value.target");
            code.close();
        }
        if (focus) {
            code.open("if (" + nameVar + " == \"FocusCamera\")");
            set(v1, "Std.string(event.eventData.value.x)");
            set(v2, "Std.string(event.eventData.value.y)");
            code.close();
        }
        if (scroll) {
            code.open("if (" + nameVar + " == \"ScrollSpeed\")");
            set(v1, "Std.string(event.eventData.value.scroll)");
            code.close();
        }
    }

    std::set<std::string> methodForm_;
    std::set<std::string> hookMethods_;

    bool has(const std::string& psych) const { return functions_.count(psych) > 0; }

    std::string guard(bool needsCreate) {
        std::vector<std::string> checks;
        if (usesClose_) checks.push_back("closed");
        if (opt_.base == Base::Module) {
            use("PlayState");
            checks.push_back("PlayState.instance == null");
            if (opt_.scopeKind == "song") checks.push_back("PlayState.instance.currentSong == null || PlayState.instance.currentSong.id != " + hxString(opt_.scopeId));
            if (opt_.scopeKind == "stage") checks.push_back("PlayState.instance.currentStageId != " + hxString(opt_.scopeId));
        }
        if (needsCreate && hasCreate_ && opt_.base != Base::NoteKind && opt_.base != Base::SongEvent) {
            use("PlayState");
            checks.push_back("activeGame != PlayState.instance");
        }
        if (checks.empty()) return "";
        std::string out = "if (";
        for (size_t i = 0; i < checks.size(); i++) {
            if (i) out += " || ";
            out += checks[i];
        }
        return out + ") return;";
    }

    bool allowed(const std::string& method) const {
        if (opt_.base == Base::SongEvent) return false;
        if (opt_.base == Base::NoteKind) {
            return method == "onCreate" || method == "onDestroy" || method == "onUpdate" || method == "onNoteIncoming" || method == "onNoteHit" || method == "onNoteMiss";
        }
        return true;
    }

    void override(Code& out, const std::string& method, const std::string& type, const std::function<void(Code&)>& fill, bool needsCreate = true) {
        Code body;
        body.setDepth(out.depth() + 1);
        fill(body);
        if (body.empty()) return;
        if (!allowed(method)) {
            issue(0, method + " never reaches a " + baseClass() + " script; that code was left out. Move it to a song or stage script.");
            return;
        }
        out.blank();
        out.open("public override function " + method + "(event:" + type + "):Void");
        out.line("super." + method + "(event);");
        std::string g = guard(needsCreate);
        if (!g.empty()) out.line(g);
        if (lazyCreate()) out.line("if (activeGame != PlayState.instance) create();");
        for (const std::string& l : body.lines()) out.line(l.empty() ? l : l.substr(static_cast<size_t>(out.depth()) * 2));
        out.close();
    }

    bool lazyCreate() const { return hasCreate_ && (opt_.base == Base::NoteKind || opt_.base == Base::SongEvent); }

    bool inCreate_ = false;
    bool addedInCreate_ = false;

    void createCode(const Block& chunk, Code& code, Code& post) {
        inCreate_ = true;
        pushScope();
        for (const auto& s : chunk) {
            if (opt_.skipLines.count(s->line)) continue;
            if (s->kind == StatKind::Function || s->kind == StatKind::LocalFunction) continue;
            if (s->kind == StatKind::Local) {
                for (size_t i = 0; i < s->names.size(); i++) {
                    const Expr* v = i < s->values.size() ? s->values[i].get() : nullptr;
                    std::string field = fieldNames_[s->names[i]];
                    if (v && isConstant(v)) fieldInits_[field] = valueFor(s->names[i], v);
                    else if (v) code.line(field + " = " + valueFor(s->names[i], v) + ";");
                }
                continue;
            }
            if (s->kind == StatKind::Assign && s->targets.size() == 1 && s->values.size() == 1 && strip(s->targets[0].get())->kind == ExprKind::Name &&
                isConstant(s->values[0].get()) && fieldNames_.count(strip(s->targets[0].get())->text) && !fieldInits_.count(fieldNames_[strip(s->targets[0].get())->text])) {
                fieldInits_[fieldNames_[strip(s->targets[0].get())->text]] = valueFor(strip(s->targets[0].get())->text, s->values[0].get());
                continue;
            }
            statement(*s, code);
        }
        popScope();
        if (has("onCreate")) {
            callbacks_.push_back("onCreate");
            hookBody("onCreate", code);
        }
        if (has("onCreatePost")) {
            callbacks_.push_back("onCreatePost");
            hookBody("onCreatePost", opt_.base == Base::Stage ? post : code);
        }
        if (addedInCreate_ && opt_.base != Base::Stage) code.line("layerSprites();");
        inCreate_ = false;
    }

    bool isConstant(const Expr* e) {
        e = strip(e);
        switch (e->kind) {
            case ExprKind::Nil:
            case ExprKind::True:
            case ExprKind::False:
            case ExprKind::Number:
            case ExprKind::String: return true;
            case ExprKind::Unary: return e->text == "-" && strip(e->a.get())->kind == ExprKind::Number;
            case ExprKind::Table: {
                for (const auto& f : e->fields) {
                    if (f.key && !isConstant(f.key.get())) return false;
                    if (!isConstant(f.value.get())) return false;
                }
                return true;
            }
            default: return false;
        }
    }

    void build(const Block& chunk) {
        for (const char* psych : {"onCreate", "onCreatePost", "onUpdate", "onUpdatePost", "goodNoteHit", "goodNoteHitPost", "opponentNoteHit",
                 "opponentNoteHitPost", "onStepHit", "onSectionHit", "onStartCountdown", "onCountdownStarted"}) {
            if (has(psych) && hasReturn(functions_.at(psych)->body)) methodForm_.insert(psych);
        }
        bool chunkCode = false;
        for (const auto& s : chunk) {
            if (opt_.skipLines.count(s->line)) continue;
            if (s->kind != StatKind::Function && s->kind != StatKind::LocalFunction) chunkCode = true;
        }
        hasCreate_ = chunkCode || has("onCreate") || has("onCreatePost") || !tags_.empty();
        if (hasCreate_ && methodForm_.count("onCreate") == 0 && has("onCreatePost") && has("onCreate") && hasReturn(functions_.at("onCreate")->body)) methodForm_.insert("onCreate");

        Code create;
        create.setDepth(0);
        Code post;
        post.setDepth(0);
        pushScope();
        if (dynamicTags_) {
            use("StringMap");
            fieldTypes_["sprites"] = "StringMap<Dynamic>";
            fieldInits_["sprites"] = "new StringMap()";
        }
        createCode(chunk, create, post);
        popScope();

        Code hooks;
        hooks.setDepth(1);
        emitHooks(hooks, create, post);

        Code methods;
        methods.setDepth(1);
        for (const std::string& name : functionOrder_) {
            const FuncBody* func = functions_.at(name);
            bool callback = kKnownCallbacks.count(name) > 0;
            bool asMethod = !callback || hookMethods_.count(name) || referencedFunctions_.count(name) || name == "onTimerCompleted" ||
                name == "onTweenCompleted" || name == "onSoundFinished";
            if (callback && !asMethod && handled_.count(name)) continue;
            if (callback && !handled_.count(name) && !hookMethods_.count(name) && name != "onTimerCompleted" && name != "onTweenCompleted" && name != "onSoundFinished") {
                issue(func->line, name + " is never called by V-Slice; it was kept as a method.");
            }
            if (callback && std::find(callbacks_.begin(), callbacks_.end(), name) == callbacks_.end()) callbacks_.push_back(name);
            std::string target = methodNames_.count(name) ? methodNames_[name] : name;
            pushScope();
            std::string params;
            std::vector<HookParam> hookParams = paramsFor(name);
            for (size_t i = 0; i < func->params.size(); i++) {
                if (i) params += ", ";
                std::string p = declare(func->params[i]);
                params += p;
                if (i < hookParams.size() && hookMethods_.count(name)) params += ":" + hookParams[i].type;
            }
            if (func->vararg) {
                if (!params.empty()) params += ", ";
                params += declare("...");
                issue(func->line, "varargs (...) became one array argument.");
            }
            methods.blank();
            methods.open("function " + target + "(" + params + ")");
            std::string savedEvent = eventParam_;
            std::string savedNote = noteTypeParam_;
            if (name == "onEvent" && !func->params.empty()) eventParam_ = safeName(func->params[0]);
            if ((name == "goodNoteHit" || name == "opponentNoteHit" || name == "noteMiss" || name == "onSpawnNote") && func->params.size() > 2) noteTypeParam_ = safeName(func->params[2]);
            bool savedOverride = inOverride_;
            inOverride_ = false;
            block(func->body, methods);
            inOverride_ = savedOverride;
            eventParam_ = savedEvent;
            noteTypeParam_ = savedNote;
            methods.close();
            popScope();
        }

        if (lazyCreate()) {
            Code lazy;
            lazy.setDepth(1);
            lazy.blank();
            lazy.open("function create():Void");
            use("PlayState");
            lazy.line("activeGame = PlayState.instance;");
            lazy.append(create);
            lazy.close();
            for (const std::string& l : lazy.lines()) methodsText_.push_back(l);
        }
        if (usesLayers_) {
            Code helper;
            helper.setDepth(1);
            helper.line("");
            helper.line(layerHelper());
            for (const std::string& l : helper.lines()) methodsText_.push_back(l);
            imports_.insert("funkin.play.stage.Stage");
        }
        for (const std::string& l : hooks.lines()) hooksText_.push_back(l);
        for (const std::string& l : methods.lines()) methodsText_.push_back(l);
    }

    std::set<std::string> handled_;
    std::vector<std::string> hooksText_;

    void markHandled(const std::string& psych) {
        handled_.insert(psych);
        if (has(psych) && std::find(callbacks_.begin(), callbacks_.end(), psych) == callbacks_.end()) callbacks_.push_back(psych);
    }

    void emitCreateInto(Code& body, const Code& create) {
        if (create.empty() && tags_.empty()) return;
        use("PlayState");
        body.line("activeGame = PlayState.instance;");
        body.append(create);
    }

    void countdownParts(Code& c) {
        cancelable_ = true;
        if (has("onStartCountdown")) {
            markHandled("onStartCountdown");
            hookBody("onStartCountdown", c);
        }
        cancelable_ = false;
        if (has("onCountdownStarted")) {
            markHandled("onCountdownStarted");
            hookBody("onCountdownStarted", c);
        }
    }

    void emitHooks(Code& out, const Code& create, const Code& post) {
        markHandled("onCreate");
        markHandled("onCreatePost");
        bool stage = opt_.base == Base::Stage;
        bool song = opt_.base == Base::Song;
        bool lazy = lazyCreate();
        if ((stage || song) && hasCreate_) {
            out.blank();
            out.open("public override function onCreate(event:ScriptEvent):Void");
            out.line("super.onCreate(event);");
            if (usesClose_) out.line("closed = false;");
            emitCreateInto(out, create);
            out.close();
        }
        bool stagePost = stage && (!post.empty() || usesLayers_);
        if (stagePost) {
            fieldTypes_["postGame"] = "PlayState";
            out.blank();
            out.open("public override function onCountdownStart(event:CountdownScriptEvent):Void");
            out.line("super.onCountdownStart(event);");
            std::string g = guard(true);
            if (!g.empty()) out.line(g);
            out.open("if (postGame != PlayState.instance)");
            out.line("postGame = PlayState.instance;");
            if (usesLayers_) out.line("layerSprites();");
            out.append(post);
            out.close();
            countdownParts(out);
            out.close();
        } else if (!stage && !song && !lazy && (hasCreate_ || has("onStartCountdown") || has("onCountdownStarted"))) {
            out.blank();
            out.open("public override function onCountdownStart(event:CountdownScriptEvent):Void");
            out.line("super.onCountdownStart(event);");
            std::string g = guard(false);
            if (!g.empty()) out.line(g);
            if (hasCreate_) {
                use("PlayState");
                out.open("if (activeGame != PlayState.instance)");
                if (usesClose_) out.line("closed = false;");
                emitCreateInto(out, create);
                out.close();
            }
            countdownParts(out);
            out.close();
        } else if (has("onStartCountdown") || has("onCountdownStarted")) {
            override(out, "onCountdownStart", "CountdownScriptEvent", [&](Code& c) { countdownParts(c); });
        }
        if (opt_.base == Base::SongEvent) {
            emitEventHook(out);
            return;
        }
        simple(out, "onCountdownTick", "onCountdownStep", "CountdownScriptEvent");
        override(out, "onUpdate", "UpdateScriptEvent", [&](Code& c) {
            for (const char* psych : {"onUpdate", "onUpdatePost"}) {
                if (!has(psych)) continue;
                markHandled(psych);
                hookBody(psych, c);
            }
        });
        simple(out, "onBeatHit", "onBeatHit", "SongTimeScriptEvent");
        override(out, "onStepHit", "SongTimeScriptEvent", [&](Code& c) {
            if (has("onStepHit")) {
                markHandled("onStepHit");
                hookBody("onStepHit", c);
            }
            if (has("onSectionHit")) {
                markHandled("onSectionHit");
                use("Conductor");
                hookBody("onSectionHit", c, "event.step % Conductor.instance.stepsPerMeasure == 0");
            }
        });
        if (opt_.base != Base::SongEvent) {
            override(out, "onSongEvent", "SongEventScriptEvent", [&](Code& c) {
                if (has("onEvent")) {
                    markHandled("onEvent");
                    hookBody("onEvent", c);
                }
            });
        }
        override(out, "onNoteIncoming", "NoteScriptEvent", [&](Code& c) {
            for (const char* psych : {"onSpawnNote", "onSpawnNotePost"}) {
                if (!has(psych)) continue;
                markHandled(psych);
                hookBody(psych, c);
            }
        });
        override(out, "onNoteHit", "HitNoteScriptEvent", [&](Code& c) {
            bool good = has("goodNoteHit") || has("goodNoteHitPost");
            bool opponent = has("opponentNoteHit") || has("opponentNoteHitPost");
            if (!good && !opponent) return;
            if (opt_.base == Base::NoteKind && good && !opponent) {
                for (const char* psych : {"goodNoteHit", "goodNoteHitPost"}) {
                    if (!has(psych)) continue;
                    markHandled(psych);
                    hookBody(psych, c, "event.note.noteData.getMustHitNote()");
                }
                return;
            }
            if (good) {
                c.open("if (event.note.noteData.getMustHitNote())");
                for (const char* psych : {"goodNoteHit", "goodNoteHitPost"}) {
                    if (!has(psych)) continue;
                    markHandled(psych);
                    hookBody(psych, c);
                }
                c.close();
            }
            if (opponent) {
                c.open(good ? "else" : "if (!event.note.noteData.getMustHitNote())");
                for (const char* psych : {"opponentNoteHit", "opponentNoteHitPost"}) {
                    if (!has(psych)) continue;
                    markHandled(psych);
                    hookBody(psych, c);
                }
                c.close();
            }
        });
        override(out, "onNoteMiss", "NoteScriptEvent", [&](Code& c) {
            for (const char* psych : {"noteMiss", "noteMissPost"}) {
                if (!has(psych)) continue;
                markHandled(psych);
                hookBody(psych, c);
            }
        });
        simple(out, "noteMissPress", "onNoteGhostMiss", "GhostMissNoteScriptEvent");
        simple(out, "onSongStart", "onSongStart", "ScriptEvent");
        cancelable_ = true;
        simple(out, "onEndSong", "onSongEnd", "ScriptEvent");
        cancelable_ = false;
        simple(out, "onPause", "onPause", "PauseScriptEvent");
        simple(out, "onResume", "onResume", "ScriptEvent");
        override(out, "onGameOver", "ScriptEvent", [&](Code& c) {
            for (const char* psych : {"onGameOver", "onGameOverStart"}) {
                if (!has(psych)) continue;
                markHandled(psych);
                hookBody(psych, c);
            }
        });
        bool destroy = has("onDestroy") || hasCreate_;
        if (destroy && opt_.base != Base::SongEvent) {
            out.blank();
            out.open("public override function onDestroy(event:ScriptEvent):Void");
            out.line("super.onDestroy(event);");
            if (has("onDestroy")) {
                markHandled("onDestroy");
                if (hasCreate_) out.open("if (activeGame != null)");
                hookBody("onDestroy", out);
                if (hasCreate_) out.close();
            }
            if (hasCreate_) out.line("activeGame = null;");
            out.close();
        }
    }

    void simple(Code& out, const std::string& psych, const std::string& method, const std::string& type) {
        if (!has(psych)) return;
        markHandled(psych);
        override(out, method, type, [&](Code& c) { hookBody(psych, c); });
    }

    void emitEventHook(Code& out) {
        use("Reflect");
        out.blank();
        out.open("public override function handleEvent(data:SongEventData):Void");
        out.line("if (PlayState.instance == null || data == null) return;");
        use("PlayState");
        if (usesClose_) out.line("if (closed) return;");
        if (lazyCreate()) out.line("if (activeGame != PlayState.instance) create();");
        if (has("onEvent")) {
            markHandled("onEvent");
            const FuncBody* func = functions_.at("onEvent");
            pushScope();
            std::vector<std::string> values = {hxString(opt_.id), "Reflect.field(data.value, \"value1\")", "Reflect.field(data.value, \"value2\")", "data.time"};
            std::vector<std::string> types = {"String", "Dynamic", "Dynamic", "Float"};
            std::string savedEvent = eventParam_;
            for (size_t i = 0; i < func->params.size(); i++) {
                std::string name = declare(func->params[i]);
                if (i < values.size() && usesName(func->body, func->params[i])) out.line("var " + name + ":" + types[i] + " = " + values[i] + ";");
                if (i == 0) eventParam_ = name;
            }
            bool saved = inOverride_;
            inOverride_ = true;
            block(func->body, out);
            inOverride_ = saved;
            eventParam_ = savedEvent;
            popScope();
        }
        out.close();
        out.blank();
        out.open("public override function getTitle():String");
        out.line("return " + hxString(opt_.title.empty() ? opt_.eventName : opt_.title) + ";");
        out.close();
        out.blank();
        use("SongEventSchema");
        out.open("public override function getEventSchema():SongEventSchema");
        out.line("return new SongEventSchema([");
        out.line("  {name: \"value1\", title: \"Value 1\", type: \"string\", defaultValue: \"\"},");
        out.line("  {name: \"value2\", title: \"Value 2\", type: \"string\", defaultValue: \"\"}");
        out.line("]);");
        out.close();
        for (const auto& entry : functions_) {
            if (entry.first != "onEvent" && entry.first != "onCreate" && entry.first != "onCreatePost" && kKnownCallbacks.count(entry.first) &&
                entry.first != "onTimerCompleted" && entry.first != "onTweenCompleted") {
                issue(entry.second->line, entry.first + " never reaches a SongEvent; move that code to a song or stage script.");
            }
        }
    }

    std::string baseClass() const {
        switch (opt_.base) {
            case Base::Module: return "Module";
            case Base::Song: return "Song";
            case Base::Stage: return "Stage";
            case Base::NoteKind: return "NoteKind";
            case Base::SongEvent: return "SongEvent";
        }
        return "Module";
    }

    std::string superCall() const {
        switch (opt_.base) {
            case Base::Module: return "super(" + hxString(opt_.id) + ", " + std::to_string(opt_.priority) + ");";
            case Base::NoteKind:
                return "super(" + hxString(opt_.id) + ", " + hxString(opt_.title.empty() ? opt_.id : opt_.title) + (opt_.noteStyle.empty() ? "" : ", " + hxString(opt_.noteStyle)) + ");";
            default: return "super(" + hxString(opt_.id) + ");";
        }
    }

    std::string classText() {
        use(baseClass());
        Code out;
        out.open("class " + opt_.className + " extends " + baseClass());
        bool any = false;
        if (hasCreate_) {
            use("PlayState");
            out.line("var activeGame:PlayState = null;");
            any = true;
        }
        if (usesClose_) {
            out.line("var closed:Bool = false;");
            any = true;
        }
        for (const std::string& tag : tagOrder_) {
            const Tag& t = tags_.at(tag);
            std::string type = t.kind == TagKind::Text ? "FlxText" : "FunkinSprite";
            use(type);
            out.line("var " + t.field + ":" + type + ";");
            any = true;
        }
        if (usesLayers_ || removesSprites_) {
            out.line("var behindSprites:Array<FlxSprite> = [];");
            out.line("var frontSprites:Array<FlxSprite> = [];");
            imports_.insert("flixel.FlxSprite");
            any = true;
        }
        for (const auto& entry : fieldTypes_) {
            auto init = fieldInits_.find(entry.first);
            out.line("var " + entry.first + ":" + entry.second + (init != fieldInits_.end() ? " = " + init->second : "") + ";");
            any = true;
        }
        for (const std::string& lua : fieldOrder_) {
            const std::string& name = fieldNames_.at(lua);
            auto init = fieldInits_.find(name);
            std::string type = boolFields_.count(lua) ? "Bool" : "Dynamic";
            out.line("var " + name + ":" + type + (init != fieldInits_.end() ? " = " + init->second : "") + ";");
            any = true;
        }
        if (any) out.blank();
        out.open("public function new()");
        out.line(superCall());
        out.close();
        Code rest;
        if (!hooksText_.empty()) out.blank();
        for (const std::string& l : hooksText_) {
            if (!l.empty() || !out.lines().back().empty()) out.line(l.empty() ? "" : l.substr(2));
        }
        if (!methodsText_.empty()) out.blank();
        for (const std::string& l : methodsText_) {
            if (!l.empty() || !out.lines().back().empty()) out.line(l.empty() ? "" : l.substr(2));
        }
        out.close();
        std::string text;
        for (const std::string& l : out.lines()) text += (trim(l).empty() ? "" : l) + "\n";
        return text;
    }
};

}

ConvertResult convertLua(const std::string& source, const ConvertOptions& options) {
    Emitter emitter(options);
    return emitter.run(source);
}

}
