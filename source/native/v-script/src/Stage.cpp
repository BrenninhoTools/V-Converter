#include "Funkin.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <map>
#include <set>

namespace vscript {

namespace {

const int kLowQuality = 1;
const int kHighQuality = 2;
const int kStoryMode = 4;
const int kFreeplay = 8;
const double kGirlfriendScroll = 0.95;

struct Point {
    double x;
    double y;
};

struct StageAnim {
    std::string anim;
    std::string prefix;
    int fps = 24;
    bool loop = false;
    std::vector<int> indices;
    double offsetX = 0;
    double offsetY = 0;
};

struct StageObject {
    std::string type;
    std::string name;
    std::string image;
    double x = 0;
    double y = 0;
    Point scale{1, 1};
    Point scroll{1, 1};
    double alpha = 1;
    double angle = 0;
    std::string color = "FFFFFF";
    bool antialiasing = true;
    bool flipX = false;
    bool flipY = false;
    int filters = kLowQuality | kHighQuality;
    std::vector<StageAnim> animations;
    std::string firstAnimation;
    std::string blend;
    double danceEvery = 0;
    bool rawOffsets = true;
    bool startReplayed = false;
    bool paused = false;
};

Point point(const Json& v, double dx, double dy) {
    if (v.kind() != Json::Kind::Array) return {dx, dy};
    const auto& items = v.items();
    double x = items.size() > 0 && items[0].kind() == Json::Kind::Number ? items[0].asNumber() : dx;
    double y = items.size() > 1 && items[1].kind() == Json::Kind::Number ? items[1].asNumber() : dy;
    return {x, y};
}

StageObject parseObject(const Json& o) {
    StageObject s;
    s.type = o["type"].text();
    s.name = o["name"].text();
    s.image = o["image"].text();
    s.x = o["x"].asNumber(0);
    s.y = o["y"].asNumber(0);
    s.scale = point(o["scale"], 1, 1);
    s.scroll = point(o["scroll"], 1, 1);
    s.alpha = o["alpha"].asNumber(1);
    s.angle = o["angle"].asNumber(0);
    s.color = o["color"].text("FFFFFF");
    s.antialiasing = o["antialiasing"].asBool(true);
    s.flipX = o["flipX"].asBool(false);
    s.flipY = o["flipY"].asBool(false);
    s.filters = static_cast<int>(o["filters"].asNumber(kLowQuality | kHighQuality));
    for (const Json& a : o["animations"].items()) {
        StageAnim anim;
        anim.anim = a["anim"].text();
        anim.prefix = a["prefix"].text();
        anim.fps = static_cast<int>(a["fps"].asNumber(24));
        anim.loop = a["loop"].asBool(false);
        for (const Json& i : a["indices"].items()) anim.indices.push_back(static_cast<int>(i.asNumber()));
        anim.offsetX = a["offsetX"].asNumber(0);
        anim.offsetY = a["offsetY"].asNumber(0);
        s.animations.push_back(anim);
    }
    s.firstAnimation = o["firstAnimation"].text();
    s.blend = o["blend"].text();
    s.danceEvery = o["danceEvery"].asNumber(0);
    s.rawOffsets = o["rawOffsets"].asBool(true);
    s.startReplayed = o["startReplayed"].asBool(false);
    s.paused = o["paused"].asBool(false);
    return s;
}

const std::set<std::string> kLibraries = {"shared", "week1", "week2", "week3", "week4", "week5", "week6", "week7", "weekend1", "tutorial"};

const std::map<std::string, std::string> kBaseStages = {{"stage", "mainStage"}, {"spooky", "spookyMansion"}, {"philly", "phillyTrain"},
    {"limo", "limoRide"}, {"mall", "mallXmas"}, {"mallevil", "mallEvil"}, {"school", "school"}, {"schoolevil", "schoolEvil"},
    {"tank", "tankmanBattlefield"}, {"phillystreets", "phillyStreets"}, {"phillyblazin", "phillyBlazin"}};

const std::map<std::string, std::string> kNamedColors = {{"white", "FFFFFF"}, {"black", "000000"}, {"gray", "808080"}, {"grey", "808080"},
    {"red", "FF0000"}, {"green", "008000"}, {"lime", "00FF00"}, {"blue", "0000FF"}, {"yellow", "FFFF00"}, {"cyan", "00FFFF"}, {"magenta", "FF00FF"},
    {"pink", "FFC0CB"}, {"purple", "800080"}, {"orange", "FFA500"}, {"brown", "8B4513"}, {"transparent", "000000"}};

const std::set<std::string> kBlends = {"add", "alpha", "darken", "difference", "erase", "hardlight", "invert", "layer", "lighten", "multiply", "normal",
    "overlay", "screen", "shader", "subtract"};

struct Notes {
    std::vector<std::string> lines;
    int exact = 0;
    int approximate = 0;
    int renamed = 0;
};

std::string library(const std::string& directory) {
    std::string clean = trimText(directory);
    while (!clean.empty() && clean.front() == '/') clean.erase(clean.begin());
    while (!clean.empty() && clean.back() == '/') clean.pop_back();
    clean = lowerText(clean);
    return kLibraries.count(clean) ? clean : "shared";
}

std::string upper(std::string t) {
    for (char& c : t) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
    return t;
}

bool isHex(const std::string& t) {
    if (t.empty()) return false;
    for (char c : t) if (!std::isxdigit(static_cast<unsigned char>(c))) return false;
    return true;
}

std::string hexColor(const std::string& raw, const std::string& label, ReportOut& report) {
    std::string t = trimText(raw);
    if (!t.empty() && t[0] == '#') t = t.substr(1);
    if (t.compare(0, 2, "0x") == 0 || t.compare(0, 2, "0X") == 0) t = t.substr(2);
    if (t.empty()) return "FFFFFF";
    if (t.size() == 6 && isHex(t)) return upper(t);
    if (t.size() == 8 && isHex(t)) return upper(t.substr(2));
    if (t.size() == 3 && isHex(t)) {
        std::string out;
        for (char c : t) {
            out += c;
            out += c;
        }
        return upper(out);
    }
    auto named = kNamedColors.find(lowerText(t));
    if (named != kNamedColors.end()) return named->second;
    report.warn("\"" + label + "\" uses the color \"" + raw + "\", which could not be read; white was used instead.");
    return "FFFFFF";
}

Json pair(double a, double b) {
    Json arr = Json::array();
    arr.push(Json::number(a));
    arr.push(Json::number(b));
    return arr;
}

double round3(double v) { return std::floor(v * 1000.0 + 0.5) / 1000.0; }

std::string uniqueName(const std::string& name, std::set<std::string>& used) {
    std::string candidate = name;
    int n = 2;
    while (!used.insert(candidate).second) candidate = name + std::to_string(n++);
    return candidate;
}

std::string assetPathOf(const std::string& image) {
    std::string t = trimText(image);
    std::replace(t.begin(), t.end(), '\\', '/');
    if (t.compare(0, 7, "images/") == 0) t = t.substr(7);
    if (t.size() >= 4 && t.compare(t.size() - 4, 4, ".png") == 0) t = t.substr(0, t.size() - 4);
    return t;
}

void animate(StageObject obj, const std::string& label, Json& entry, const std::map<std::string, Atlas>& atlases, ReportOut& report, Notes& notes) {
    std::string path = entry["assetPath"].text();
    auto found = atlases.find(path);
    const Atlas* atlas = found == atlases.end() ? nullptr : &found->second;
    std::vector<StageAnim> animations = obj.animations;
    if (animations.empty()) {
        const AtlasFrame* first = atlas ? atlas->first() : nullptr;
        if (!first) {
            report.warn("\"" + label + "\" is an animated sprite without animations; add its spritesheet .xml so the first frame can be shown. It was kept as a plain image for now.");
            return;
        }
        std::string prefix = first->name;
        while (!prefix.empty() && std::isdigit(static_cast<unsigned char>(prefix.back()))) prefix.pop_back();
        StageAnim idle;
        idle.anim = "idle";
        idle.prefix = prefix;
        idle.indices = {0};
        animations.push_back(idle);
        notes.lines.push_back("\"" + label + "\" had no animations; it now shows the first frame of its spritesheet like Psych does.");
    }
    std::string start = animations.front().anim;
    for (const StageAnim& a : animations) {
        if (a.anim == obj.firstAnimation) {
            start = obj.firstAnimation;
            break;
        }
    }
    bool bops = obj.danceEvery > 0;
    bool hasIdle = false;
    for (const StageAnim& a : animations) if (a.anim == "idle" || a.anim == "danceLeft") hasIdle = true;
    if (bops && !hasIdle) {
        std::string renamed = start;
        for (StageAnim& a : animations) if (a.anim == renamed) a.anim = "idle";
        start = "idle";
        notes.renamed++;
    }
    double sx = obj.scale.x;
    double sy = obj.scale.y;
    bool scaled = sx != 1.0 || sy != 1.0;
    bool hasOffsets = false;
    for (const StageAnim& a : animations) if (a.offsetX != 0 || a.offsetY != 0) hasOffsets = true;
    const AtlasFrame* first = atlas ? atlas->first() : nullptr;
    const StageAnim* startAnim = nullptr;
    for (const StageAnim& a : animations) {
        if (a.anim == start) {
            startAnim = &a;
            break;
        }
    }
    const AtlasFrame* startFrame = atlas && startAnim ? atlas->firstFrameOf(startAnim->prefix, startAnim->indices) : nullptr;
    if (!startFrame) startFrame = first;
    bool exact = obj.rawOffsets && scaled && first && startFrame;
    if (obj.rawOffsets && scaled && (hasOffsets || (startFrame && first && startFrame->width != first->width))) {
        if (exact) notes.exact++;
        else if (hasOffsets) notes.approximate++;
    }
    double hitboxX = exact ? 0.5 * first->width * (1 - sx) : 0;
    double hitboxY = exact ? 0.5 * first->height * (1 - sy) : 0;
    double startHitboxX = exact ? 0.5 * startFrame->width * (1 - sx) : 0;
    double startHitboxY = exact ? 0.5 * startFrame->height * (1 - sy) : 0;
    bool startShowsHitbox = obj.rawOffsets && scaled && !bops && !obj.startReplayed;

    if (atlas) {
        std::vector<std::string> missing;
        for (const StageAnim& a : animations) if (!atlas->hasPrefix(a.prefix)) missing.push_back(a.anim + " (\"" + a.prefix + "\")");
        if (!missing.empty()) {
            std::string list;
            for (size_t i = 0; i < missing.size(); i++) list += (i ? ", " : "") + missing[i];
            report.warn("\"" + label + "\": the spritesheet has no frames for " + list + ".");
        }
    }
    Json list = Json::array();
    bool allPacker = true;
    for (const StageAnim& anim : animations) {
        double ox;
        double oy;
        if (!obj.rawOffsets) {
            ox = anim.offsetX / sx;
            oy = anim.offsetY / sy;
        } else if (anim.anim == start && startShowsHitbox) {
            ox = (startHitboxX - hitboxX) / sx;
            oy = (startHitboxY - hitboxY) / sy;
        } else {
            ox = (anim.offsetX - hitboxX) / sx;
            oy = (anim.offsetY - hitboxY) / sy;
        }
        Json a = Json::object();
        a.set("name", Json::string(anim.anim));
        a.set("frameRate", Json::number(anim.fps));
        a.set("looped", Json::boolean(anim.loop));
        if (!anim.prefix.empty() || anim.indices.empty()) a.set("prefix", Json::string(anim.prefix));
        if (!anim.indices.empty()) {
            Json idx = Json::array();
            for (int i : anim.indices) idx.push(Json::number(i));
            a.set("frameIndices", idx);
        }
        if (round3(ox) != 0 || round3(oy) != 0) a.set("offsets", pair(jsonNumber(round3(ox)), jsonNumber(round3(oy))));
        list.push(a);
        if (!(anim.prefix.empty() && !anim.indices.empty())) allPacker = false;
    }
    entry.set("animType", Json::string(allPacker ? "packer" : "sparrow"));
    entry.set("danceEvery", Json::number(jsonNumber(obj.danceEvery)));
    entry.set("animations", list);
    if (!obj.paused) entry.set("startingAnimation", Json::string(start));
}

Json prop(const StageObject& obj, const std::string& label, int z, const std::map<std::string, Atlas>& atlases, ReportOut& report, Notes& notes) {
    Json entry = Json::object();
    entry.set("name", Json::string(label));
    entry.set("zIndex", Json::number(z));
    entry.set("position", pair(jsonNumber(obj.x), jsonNumber(obj.y)));
    if (obj.type == "square") {
        entry.set("assetPath", Json::string("#" + hexColor(obj.color, label, report)));
        entry.set("scale", pair(std::max(1.0, std::floor(obj.scale.x + 0.5)), std::max(1.0, std::floor(obj.scale.y + 0.5))));
    } else {
        entry.set("assetPath", Json::string(assetPathOf(obj.image)));
        entry.set("scale", pair(jsonNumber(obj.scale.x), jsonNumber(obj.scale.y)));
        std::string color = hexColor(obj.color, label, report);
        if (color != "FFFFFF") entry.set("color", Json::string("#" + color));
        if (!obj.antialiasing) entry.set("isPixel", Json::boolean(true));
        if (obj.flipX) entry.set("flipX", Json::boolean(true));
        if (obj.flipY) entry.set("flipY", Json::boolean(true));
    }
    if (obj.scroll.x != 1.0 || obj.scroll.y != 1.0) entry.set("scroll", pair(jsonNumber(obj.scroll.x), jsonNumber(obj.scroll.y)));
    if (obj.alpha != 1.0) entry.set("alpha", Json::number(jsonNumber(std::max(0.0, std::min(1.0, obj.alpha)))));
    std::string blend = lowerText(trimText(obj.blend));
    if (!blend.empty() && blend != "normal") {
        if (kBlends.count(blend)) entry.set("blend", Json::string(blend));
        else report.warn("\"" + label + "\" uses the blend mode \"" + obj.blend + "\", which V-Slice does not know; it draws normally.");
    }
    if (obj.angle != 0) entry.set("angle", Json::number(jsonNumber(obj.angle)));
    if (obj.type == "animatedSprite") animate(obj, label, entry, atlases, report, notes);
    return entry;
}

struct Slot {
    const char* label;
    double anchorX;
    double anchorY;
    double cameraBaseX;
    double cameraBaseY;
};

const Slot kPlayer = {"Player", 219.5, 785.0, -100.0, -100.0};
const Slot kOpponent = {"Opponent", 235.0, 785.0, 150.0, -100.0};
const Slot kGirlfriend = {"Girlfriend", 351.5, 657.0, 0.0, 0.0};

Json character(const Slot& slot, Point position, Point camera, int z, double scroll, double alpha) {
    Json entry = Json::object();
    entry.set("zIndex", Json::number(z));
    entry.set("position", pair(jsonNumber(position.x + slot.anchorX), jsonNumber(position.y + slot.anchorY)));
    entry.set("cameraOffsets", pair(jsonNumber(camera.x + slot.cameraBaseX), jsonNumber(camera.y + slot.cameraBaseY)));
    if (scroll != 1.0) entry.set("scroll", pair(scroll, scroll));
    if (alpha != 1.0) entry.set("alpha", Json::number(jsonNumber(alpha)));
    return entry;
}

std::string prettyName(const std::string& id) {
    std::string out;
    std::string part;
    auto flush = [&]() {
        if (trimText(part).empty()) {
            part.clear();
            return;
        }
        if (!out.empty()) out += " ";
        part[0] = static_cast<char>(std::toupper(static_cast<unsigned char>(part[0])));
        out += part;
        part.clear();
    };
    for (char c : id) {
        if (c == '-' || c == '_' || c == ' ') flush();
        else part += c;
    }
    flush();
    return out.empty() ? id : out;
}

}

Json convertPsychStage(const Json& input, std::string& error) {
    const Json& psych = input["stage"];
    if (psych.kind() != Json::Kind::Object) {
        error = "The stage is missing.";
        return Json();
    }
    std::string id = input["id"].text();
    std::map<std::string, Atlas> atlases;
    for (const auto& entry : input["atlases"].fields()) atlases[entry.first] = Atlas::fromJson(entry.second);
    ReportOut report;
    Notes notes;
    Json props = Json::array();
    std::vector<std::string> skipped;
    int converted = 0;
    std::set<std::string> used;
    bool hasObjects = psych["objects"].kind() == Json::Kind::Array;
    std::vector<StageObject> objects;
    for (const Json& o : psych["objects"].items()) objects.push_back(parseObject(o));
    int gfZ = -1;
    int dadZ = -1;
    int bfZ = -1;
    int top = 0;
    std::vector<std::string> preload;
    for (const Json& p : psych["preload"].items()) preload.push_back(p.text());
    if (objects.empty()) {
        if (!hasObjects) {
            auto base = kBaseStages.find(lowerText(id));
            report.warn("This stage has no \"objects\" list, so its sprites are built in code (a hardcoded Psych stage or stages/" + id +
                ".lua/.hx). Character positions, cameras and zoom were converted; the props must be recreated in V-Slice." +
                (base != kBaseStages.end() ? " V-Slice already has this stage as \"" + base->second + "\"." : std::string()));
            if (!preload.empty()) {
                std::vector<std::string> images;
                for (const std::string& p : preload) images.push_back(p.compare(0, 7, "images/") == 0 ? p.substr(7) : p);
                report.section("Images the stage preloads", images);
            }
        }
    } else {
        for (size_t index = 0; index < objects.size(); index++) {
            const StageObject& obj = objects[index];
            int z = static_cast<int>(index + 1) * 10;
            top = z;
            std::string fallback = "object" + std::to_string(index);
            if (obj.type == "gf" || obj.type == "gfGroup") {
                if (gfZ < 0) gfZ = z;
            } else if (obj.type == "dad" || obj.type == "dadGroup") {
                if (dadZ < 0) dadZ = z;
            } else if (obj.type == "boyfriend" || obj.type == "boyfriendGroup") {
                if (bfZ < 0) bfZ = z;
            } else if (obj.type == "square" || obj.type == "sprite" || obj.type == "animatedSprite") {
                std::string label = uniqueName(trimText(obj.name).empty() ? fallback : obj.name, used);
                if ((obj.filters & (kLowQuality | kHighQuality)) == 0) {
                    skipped.push_back(label + " (hidden in every quality setting)");
                } else if ((obj.filters & kHighQuality) == 0) {
                    skipped.push_back(label + " (low quality only)");
                } else {
                    if (obj.filters & (kStoryMode | kFreeplay)) report.warn("\"" + label + "\" only shows in story mode or freeplay in Psych; V-Slice always shows it.");
                    props.push(prop(obj, label, z, atlases, report, notes));
                    converted++;
                }
            } else {
                skipped.push_back((trimText(obj.name).empty() ? fallback : obj.name) + " (unknown type \"" + obj.type + "\")");
            }
        }
    }
    std::vector<std::string> missing;
    int step = 0;
    if (gfZ < 0) {
        gfZ = top + 10 * ++step;
        missing.push_back("girlfriend");
    }
    if (dadZ < 0) {
        dadZ = top + 10 * ++step;
        missing.push_back("opponent");
    }
    if (bfZ < 0) {
        bfZ = top + 10 * ++step;
        missing.push_back("player");
    }
    if (!objects.empty() && missing.size() == 3) {
        notes.lines.push_back("The objects list has no character layers, so the characters were placed above every prop (Psych would not show them at all).");
    } else if (!objects.empty() && !missing.empty()) {
        std::string list;
        for (size_t i = 0; i < missing.size(); i++) list += (i ? ", " : "") + missing[i];
        bool one = missing.size() == 1;
        report.warn("The objects list has no layer for " + list + "; Psych would not show " + (one ? "that character" : "those characters") + ", V-Slice puts " + (one ? "it" : "them") + " on top.");
    }
    bool hideGirlfriend = psych["hideGirlfriend"].asBool(false);
    Json characters = Json::object();
    characters.set("bf", character(kPlayer, point(psych["boyfriend"], 770, 100), point(psych["cameraBoyfriend"], 0, 0), bfZ, 1.0, 1.0));
    characters.set("dad", character(kOpponent, point(psych["opponent"], 100, 100), point(psych["cameraOpponent"], 0, 0), dadZ, 1.0, 1.0));
    characters.set("gf", character(kGirlfriend, point(psych["girlfriend"], 400, 130), point(psych["cameraGirlfriend"], 0, 0), gfZ, kGirlfriendScroll, hideGirlfriend ? 0.0 : 1.0));

    std::string directoryRaw = psych["directory"].text();
    std::string directory = library(directoryRaw);
    double zoom = psych["defaultZoom"].asNumber(0.9);
    bool pixel = psych["isPixelStage"].asBool(false);
    std::string displayName = trimText(input["displayName"].text());
    Json root = Json::object();
    root.set("version", Json::string("1.0.2"));
    root.set("name", Json::string(displayName.empty() ? prettyName(id) : input["displayName"].text()));
    root.set("cameraZoom", Json::number(jsonNumber(zoom)));
    root.set("directory", Json::string(directory));
    root.set("props", props);
    root.set("characters", characters);

    report.stat("Props", Json::number(converted));
    report.stat("Zoom", Json::number(jsonNumber(zoom)));
    report.stat("Pixel", Json::string(pixel ? "Yes" : "No"));
    report.section("Skipped objects", skipped);
    if (!trimText(directoryRaw).empty() && directory != lowerText(trimText(directoryRaw))) {
        notes.lines.push_back("The folder \"" + directoryRaw + "\" is not a V-Slice asset library (V-Slice stops with \"Missing library\" on unknown ones), so the stage uses \"shared\". Images in the mod's images/ folder load from there.");
    }
    if (hideGirlfriend) notes.lines.push_back("hide_girlfriend became an invisible girlfriend (alpha 0); you can also set the song's girlfriend to none in its metadata.");
    double cameraSpeed = psych["cameraSpeed"].asNumber(1.0);
    if (cameraSpeed != 1.0) report.warn("Camera speed " + doubleText(cameraSpeed) + " has no V-Slice stage setting; use a FocusCamera event with a duration, or a stage script, to change it.");
    if (pixel) notes.lines.push_back("Pixel stage: give the songs that use it the \"pixel\" note style in their metadata.");
    if (notes.exact > 0) notes.lines.push_back(std::to_string(notes.exact) + " animated prop(s) are scaled; their animation offsets were recalculated from the spritesheet frames so they draw exactly where Psych draws them.");
    if (notes.approximate > 0) report.warn(std::to_string(notes.approximate) + " scaled animated prop(s) have animation offsets but no spritesheet XML was given; offsets were divided by the scale and may be a few pixels off. Add the images' .xml files for an exact result.");
    if (notes.renamed > 0) notes.lines.push_back(std::to_string(notes.renamed) + " beat-bopping prop(s) had their bop animation renamed to \"idle\", the name V-Slice props dance with.");
    notes.lines.push_back("Character positions use V-Slice feet anchors. Characters converted with V-Converter land on the exact Psych spot; native V-Slice characters land close to it.");
    notes.lines.push_back("Layers keep Psych's order through zIndex (props and characters every 10).");
    report.section("Notes", notes.lines);

    Json out = Json::object();
    out.set("json", root);
    out.set("report", report.toJson());
    return out;
}

}
