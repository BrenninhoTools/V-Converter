#include "Funkin.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <map>
#include <stdexcept>

namespace vscript {

namespace {

struct Slot {
    std::string label;
    double anchorX;
    double anchorY;
    double cameraBaseX;
    double cameraBaseY;
    double cameraSign;
};

Slot slotOf(const std::string& key) {
    if (key == "player") return {"Player", 219.5, 785.0, -100.0, -100.0, -1.0};
    if (key == "girlfriend") return {"Girlfriend", 351.5, 657.0, 0.0, 0.0, 1.0};
    return {"Opponent", 235.0, 785.0, 150.0, -100.0, 1.0};
}

struct Anim {
    std::string anim;
    std::string prefix;
    int fps = 24;
    bool loop = false;
    std::vector<int> indices;
    double offsetX = 0;
    double offsetY = 0;
};

struct Character {
    std::string image;
    double scale = 1;
    double positionX = 0;
    double positionY = 0;
    double cameraX = 0;
    double cameraY = 0;
    bool flipX = false;
    bool noAntialiasing = false;
    std::string healthIcon;
    int color[3] = {161, 161, 161};
    double singDuration = 4;
    std::string vocalsFile;
    int editorIsPlayer = -1;
    std::vector<Anim> animations;

    std::vector<std::string> atlasImages() const {
        std::vector<std::string> out;
        size_t start = 0;
        while (start <= image.size()) {
            size_t comma = image.find(',', start);
            std::string part = trimText(image.substr(start, comma == std::string::npos ? std::string::npos : comma - start));
            if (!part.empty()) out.push_back(part);
            if (comma == std::string::npos) break;
            start = comma + 1;
        }
        return out;
    }

    bool hasDanceLeftRight() const {
        bool left = false;
        bool right = false;
        for (const Anim& a : animations) {
            if (a.anim == "danceLeft") left = true;
            if (a.anim == "danceRight") right = true;
        }
        return left && right;
    }

    const Anim* find(const std::string& name) const {
        for (const Anim& a : animations) if (a.anim == name) return &a;
        return nullptr;
    }
};

double num(const Json& v, double fallback) {
    if (v.kind() == Json::Kind::Number) return std::isfinite(v.asNumber()) ? v.asNumber() : fallback;
    if (v.kind() == Json::Kind::String) {
        std::string t = trimText(v.asString());
        char* end = nullptr;
        double d = std::strtod(t.c_str(), &end);
        if (!t.empty() && end && *end == '\0' && std::isfinite(d)) return d;
    }
    return fallback;
}

bool flag(const Json& v, bool fallback) {
    if (v.kind() == Json::Kind::Bool) return v.asBool();
    if (v.kind() == Json::Kind::Number) return v.asNumber() != 0;
    if (v.kind() == Json::Kind::String) {
        std::string t = lowerText(trimText(v.asString()));
        if (t == "true" || t == "1") return true;
        if (t == "false" || t == "0") return false;
    }
    return fallback;
}

std::string text(const Json& v) {
    if (v.kind() == Json::Kind::String) return v.asString();
    if (v.kind() == Json::Kind::Number) return numberToText(v.asNumber());
    if (v.kind() == Json::Kind::Bool) return v.asBool() ? "true" : "false";
    return "";
}

const Json& item(const Json& array, size_t i) {
    static const Json none;
    return i < array.items().size() ? array.items()[i] : none;
}

Character parseCharacter(const std::string& raw) {
    std::string t = trimText(raw);
    if (t.compare(0, 3, "\xEF\xBB\xBF") == 0) t = t.substr(3);
    bool ok = false;
    Json root = Json::parse(t, ok);
    if (!ok) throw std::runtime_error("The file is not valid JSON.");
    if (root["animations"].kind() != Json::Kind::Array || root["image"].isNull()) {
        throw std::runtime_error("This JSON does not look like a Psych Engine character (missing \"animations\" or \"image\").");
    }
    Character c;
    for (const Json& a : root["animations"].items()) {
        if (a.kind() != Json::Kind::Object) continue;
        Anim anim;
        anim.anim = text(a["anim"]);
        if (trimText(anim.anim).empty()) continue;
        anim.prefix = text(a["name"]);
        anim.fps = static_cast<int>(num(a["fps"], 24.0));
        anim.loop = flag(a["loop"], false);
        for (const Json& i : a["indices"].items()) {
            double v = num(i, NAN);
            if (!std::isnan(v)) anim.indices.push_back(static_cast<int>(v));
        }
        anim.offsetX = num(item(a["offsets"], 0), 0.0);
        anim.offsetY = num(item(a["offsets"], 1), 0.0);
        c.animations.push_back(anim);
    }
    c.image = text(root["image"]);
    double scale = num(root["scale"], 1.0);
    c.scale = scale > 0 ? scale : 1.0;
    c.positionX = num(item(root["position"], 0), 0.0);
    c.positionY = num(item(root["position"], 1), 0.0);
    c.cameraX = num(item(root["camera_position"], 0), 0.0);
    c.cameraY = num(item(root["camera_position"], 1), 0.0);
    c.flipX = flag(root["flip_x"], false);
    c.noAntialiasing = flag(root["no_antialiasing"], false);
    c.healthIcon = text(root["healthicon"]);
    if (trimText(c.healthIcon).empty()) c.healthIcon = "face";
    const Json& colors = root["healthbar_colors"];
    if (colors.items().size() >= 3) {
        for (int i = 0; i < 3; i++) c.color[i] = std::max(0, std::min(255, static_cast<int>(num(colors.items()[static_cast<size_t>(i)], 0.0))));
    }
    c.singDuration = num(root["sing_duration"], 4.0);
    c.vocalsFile = text(root["vocals_file"]);
    if (!root["_editor_isPlayer"].isNull()) c.editorIsPlayer = flag(root["_editor_isPlayer"], false) ? 1 : 0;
    return c;
}

std::string atlasKey(const std::string& image) {
    std::string t = trimText(image);
    std::replace(t.begin(), t.end(), '\\', '/');
    size_t slash = t.rfind('/');
    if (slash != std::string::npos) t = t.substr(slash + 1);
    size_t png = t.rfind(".png");
    if (png != std::string::npos) t = t.substr(0, png);
    return lowerText(t);
}

std::string vsliceName(const std::string& name) {
    const std::string loop = "-loop";
    if (name.size() >= loop.size() && name.compare(name.size() - loop.size(), loop.size(), loop) == 0) return name.substr(0, name.size() - loop.size()) + "-hold";
    return name;
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

Json numberPair(double a, double b) {
    Json arr = Json::array();
    arr.push(Json::number(jsonNumber(a)));
    arr.push(Json::number(jsonNumber(b)));
    return arr;
}

Json intArray(const std::vector<int>& values) {
    Json arr = Json::array();
    for (int v : values) arr.push(Json::number(v));
    return arr;
}

std::string hexColor(const int color[3]) {
    char buffer[16];
    std::snprintf(buffer, sizeof buffer, "#%02X%02X%02X", color[0], color[1], color[2]);
    return buffer;
}

std::string join(const std::vector<std::string>& items) {
    std::string out;
    for (size_t i = 0; i < items.size(); i++) {
        if (i) out += ", ";
        out += items[i];
    }
    return out;
}

}

Json convertPsychCharacter(const Json& input, std::string& error) {
    Character psych;
    try {
        psych = parseCharacter(input["text"].text());
    } catch (const std::exception& e) {
        error = e.what();
        return Json();
    }
    std::string id = input["id"].text();
    std::string slotKey = input["slot"].text();
    Slot slot = slotOf(slotKey);
    std::map<std::string, Atlas> atlases;
    for (const auto& entry : input["atlases"].fields()) atlases[entry.first] = Atlas::fromJson(entry.second);
    auto atlasFor = [&](const std::string& image) -> const Atlas* {
        auto it = atlases.find(atlasKey(image));
        return it == atlases.end() ? nullptr : &it->second;
    };

    ReportOut report;
    double scale = psych.scale;
    std::vector<std::string> images = psych.atlasImages();
    if (images.empty()) images.push_back(psych.image);
    const std::string mainImage = images.front();
    std::vector<std::string> missingAtlases;
    for (const std::string& image : images) if (!atlasFor(image)) missingAtlases.push_back(image);

    const Anim* idleAnim = psych.hasDanceLeftRight() ? psych.find("danceLeft") : psych.find("idle");
    if (!idleAnim && !psych.animations.empty()) idleAnim = &psych.animations.front();

    const AtlasFrame* firstFrame = atlasFor(mainImage) ? atlasFor(mainImage)->first() : nullptr;
    const AtlasFrame* idleFrame = nullptr;
    if (idleAnim) {
        for (const std::string& image : images) {
            const Atlas* atlas = atlasFor(image);
            if (atlas) idleFrame = atlas->firstFrameOf(idleAnim->prefix, idleAnim->indices);
            if (idleFrame) break;
        }
    }
    bool exact = firstFrame && idleFrame;
    double fallbackWidth = slot.anchorX * 2.0;
    double fallbackHeight = slot.anchorY * 2.0 / (1.0 + scale);
    double fw0 = firstFrame ? firstFrame->width : idleFrame ? idleFrame->width : fallbackWidth;
    double fh0 = firstFrame ? firstFrame->height : idleFrame ? idleFrame->height : fallbackHeight;
    double fwi = idleFrame ? idleFrame->width : fw0;
    double fhi = idleFrame ? idleFrame->height : fh0;

    double baseX = slot.anchorX - psych.positionX - scale * fwi / 2.0 - (1.0 - scale) * fw0 / 2.0;
    double baseY = slot.anchorY - psych.positionY - scale * fhi - (1.0 - scale) * fh0 / 2.0;
    double cameraX = psych.positionX - slot.anchorX + scale * fw0 / 2.0 + slot.cameraSign * psych.cameraX;
    double cameraY = psych.positionY - slot.anchorY + scale * fh0 / 2.0 + scale * fhi / 2.0 + psych.cameraY;

    bool multiAtlas = images.size() > 1;
    std::vector<std::string> missingPrefixes;
    Json animations = Json::array();
    for (const Anim& anim : psych.animations) {
        Json entry = Json::object();
        entry.set("name", Json::string(vsliceName(anim.anim)));
        entry.set("prefix", Json::string(anim.prefix));
        if (multiAtlas) {
            for (const std::string& image : images) {
                const Atlas* atlas = atlasFor(image);
                if (atlas && atlas->hasPrefix(anim.prefix)) {
                    if (image != mainImage) entry.set("assetPath", Json::string(image));
                    break;
                }
            }
        }
        if (missingAtlases.empty()) {
            bool found = false;
            for (const std::string& image : images) if (atlasFor(image) && atlasFor(image)->hasPrefix(anim.prefix)) found = true;
            if (!found) missingPrefixes.push_back(anim.anim + " (\"" + anim.prefix + "\")");
        }
        if (!anim.indices.empty()) entry.set("frameIndices", intArray(anim.indices));
        entry.set("frameRate", Json::number(anim.fps));
        entry.set("looped", Json::boolean(anim.loop));
        entry.set("offsets", numberPair((anim.offsetX + baseX) / scale, (anim.offsetY + baseY) / scale));
        animations.push(entry);
    }

    bool merged = false;
    std::string deadImage;
    const Json& gameOverInput = input["gameOver"];
    if (gameOverInput.kind() == Json::Kind::Object && slotKey == "player" && !psych.find("firstDeath") && !psych.find("deathLoop") && !psych.find("deathConfirm")) {
        Character gameOver;
        bool parsed = true;
        try {
            gameOver = parseCharacter(gameOverInput["text"].text());
        } catch (const std::exception&) {
            parsed = false;
        }
        if (parsed) {
            std::string gameOverId = gameOverInput["id"].text();
            std::vector<std::string> deadImages = gameOver.atlasImages();
            if (deadImages.empty()) deadImages.push_back(gameOver.image);
            deadImage = deadImages.front();
            const Atlas* deadAtlas = atlasFor(deadImage);
            const AtlasFrame* deadFirst = deadAtlas ? deadAtlas->first() : nullptr;
            double fw0d = deadFirst ? deadFirst->width : fw0;
            double fh0d = deadFirst ? deadFirst->height : fh0;
            double baseDX = slot.anchorX - gameOver.positionX - scale * fwi / 2.0 - (1.0 - scale) * fw0d / 2.0;
            double baseDY = slot.anchorY - gameOver.positionY - scale * fhi - (1.0 - scale) * fh0d / 2.0;
            for (const Anim& anim : gameOver.animations) {
                if (anim.anim != "firstDeath" && anim.anim != "deathLoop" && anim.anim != "deathConfirm") continue;
                Json entry = Json::object();
                entry.set("name", Json::string(anim.anim));
                entry.set("prefix", Json::string(anim.prefix));
                if (deadImage != mainImage) entry.set("assetPath", Json::string(deadImage));
                if (!anim.indices.empty()) entry.set("frameIndices", intArray(anim.indices));
                entry.set("frameRate", Json::number(anim.fps));
                entry.set("looped", Json::boolean(anim.loop));
                entry.set("offsets", numberPair((anim.offsetX + baseDX) / scale, (anim.offsetY + baseDY) / scale));
                animations.push(entry);
                merged = true;
            }
            if (merged) {
                report.section("Game over", {"firstDeath, deathLoop and deathConfirm come from \"" + gameOverId + "\" (" + deadImage + "), placed where Psych's game over screen draws them."});
                if (!deadFirst) report.warn("Add the game over spritesheet XML (" + deadImage + ") for an exact game over position.");
                if (gameOver.scale != scale) {
                    report.warn("\"" + gameOverId + "\" has scale " + doubleText(gameOver.scale) + " but V-Slice plays game over animations at the character's scale (" + doubleText(scale) + ").");
                }
            }
        }
    }
    bool renderMulti = multiAtlas || (merged && deadImage != mainImage);

    Json healthIcon = Json::object();
    healthIcon.set("id", Json::string(psych.healthIcon));
    healthIcon.set("isPixel", Json::boolean(false));

    std::string displayName = trimText(input["displayName"].text());
    Json root = Json::object();
    root.set("version", Json::string("1.0.2"));
    root.set("name", Json::string(displayName.empty() ? prettyName(id) : input["displayName"].text()));
    root.set("renderType", Json::string(renderMulti ? "multisparrow" : "sparrow"));
    root.set("assetPath", Json::string(mainImage));
    root.set("scale", Json::number(jsonNumber(scale)));
    root.set("isPixel", Json::boolean(psych.noAntialiasing));
    root.set("healthIcon", healthIcon);
    Json zero = Json::array();
    zero.push(Json::number(0));
    zero.push(Json::number(0));
    root.set("offsets", zero);
    root.set("cameraOffsets", numberPair(cameraX, cameraY));
    root.set("singTime", Json::number(jsonNumber(psych.singDuration * 1.1)));
    root.set("danceEvery", Json::number(psych.hasDanceLeftRight() ? 1 : 2));
    root.set("flipX", Json::boolean(psych.flipX));
    root.set("startingAnimation", Json::string(psych.hasDanceLeftRight() ? "danceRight" : (idleAnim ? idleAnim->anim : "idle")));
    root.set("animations", animations);

    report.stat("Animations", Json::number(static_cast<double>(psych.animations.size())));
    report.stat("Render", Json::string(renderMulti ? "Multi-Sparrow" : "Sparrow"));
    report.stat("Slot", Json::string(slot.label));
    report.stat("Accuracy", Json::string(exact ? "Exact" : "Approximate"));

    if (!missingAtlases.empty()) {
        std::vector<std::string> quoted;
        for (const std::string& m : missingAtlases) quoted.push_back("\"" + m + "\"");
        report.warn("Spritesheet XML not provided for " + join(quoted) + ". Positions and camera are approximate; add the XML for an exact conversion.");
    } else if (!idleFrame) {
        report.warn("The idle animation (\"" + (idleAnim ? idleAnim->prefix : std::string("idle")) + "\") was not found in the spritesheet, so positions are approximate.");
    }
    if (!missingPrefixes.empty()) report.warn("These animations have no matching frames in the spritesheet and will not play: " + join(missingPrefixes) + ".");
    if (!psych.find("idle") && !psych.hasDanceLeftRight()) report.warn("No \"idle\" or \"danceLeft\"/\"danceRight\" animation found; V-Slice needs one to idle.");
    std::vector<std::string> loopAnims;
    for (const Anim& a : psych.animations) {
        if (a.anim.size() >= 5 && a.anim.compare(a.anim.size() - 5, 5, "-loop") == 0) loopAnims.push_back(a.anim);
    }
    if (!loopAnims.empty()) {
        report.section("Loop animations", {"Psych's \"<anim>-loop\" animations became V-Slice's \"<anim>-hold\", which V-Slice plays the same way after the animation ends: " + join(loopAnims) + "."});
    }
    std::vector<std::string> notCarried = {"Health bar color " + hexColor(psych.color) + " (V-Slice uses fixed health bar colors)"};
    if (!trimText(psych.vocalsFile).empty()) notCarried.push_back("Vocals suffix \"" + psych.vocalsFile + "\" (V-Slice names vocals after the character id)");
    if (slotKey != "player" && psych.editorIsPlayer == 1) notCarried.push_back("Psych editor marked this as a player character");
    report.section("Not carried over", notCarried);
    std::string slotLower = lowerText(slot.label);
    report.section("Notes", {"Offsets were rebased for the " + slotLower + " slot of a converted stage. Using the character in another slot can shift it by a few pixels."});

    Json out = Json::object();
    out.set("json", root);
    out.set("report", report.toJson());
    return out;
}

}
