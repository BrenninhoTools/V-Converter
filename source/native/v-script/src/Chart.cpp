#include "Funkin.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <set>
#include <stdexcept>

namespace vscript {

namespace {

struct PsychNote {
    double time;
    int lane;
    double length;
    std::string type;
    bool isPlayer() const { return lane < 4; }
};

struct PsychSection {
    double beats = 4;
    bool mustHit = true;
    bool gfSection = false;
    bool altAnim = false;
    bool changeBpm = false;
    double bpm = 0;
    std::vector<PsychNote> notes;
};

struct PsychEvent {
    double time;
    std::string name;
    std::string value1;
    std::string value2;
    bool operator==(const PsychEvent& o) const { return time == o.time && name == o.name && value1 == o.value1 && value2 == o.value2; }
    bool operator<(const PsychEvent& o) const {
        if (time != o.time) return time < o.time;
        if (name != o.name) return name < o.name;
        if (value1 != o.value1) return value1 < o.value1;
        return value2 < o.value2;
    }
};

struct PsychSong {
    std::string name;
    std::string format;
    double bpm = 100;
    double speed = 1;
    double offset = 0;
    bool needsVoices = true;
    std::string player1;
    std::string player2;
    std::string girlfriend;
    std::string stage;
    std::string arrowSkin;
    std::vector<PsychSection> sections;
    std::vector<PsychEvent> events;
    int invalidNotes = 0;
    int wrappedLanes = 0;
};

struct TimeChange {
    double time;
    double beat;
    double bpm;
    int numerator;
    bool operator==(const TimeChange& o) const { return time == o.time && beat == o.beat && bpm == o.bpm && numerator == o.numerator; }
};

struct Timing {
    std::vector<double> sectionStarts;
    std::vector<TimeChange> changes;
    int irregular = 0;
    int fractional = 0;
    double endTime = 0;

    explicit Timing(const PsychSong& song) {
        changes.push_back({0, 0, song.bpm, 4});
        double bpm = song.bpm;
        int numerator = 4;
        double time = 0;
        double beat = 0;
        for (const PsychSection& section : song.sections) {
            double newBpm = section.changeBpm && section.bpm > 0 ? section.bpm : bpm;
            long long whole = std::llround(section.beats);
            bool integral = std::fabs(section.beats - static_cast<double>(whole)) < 1e-6 && whole >= 1 && whole <= 64;
            int newNumerator = integral ? static_cast<int>(whole) : numerator;
            if (section.beats != 4.0) irregular++;
            if (!integral) fractional++;
            if (newBpm != bpm || newNumerator != numerator) {
                bpm = newBpm;
                numerator = newNumerator;
                TimeChange change{time, beat, bpm, numerator};
                if (time == 0) changes[0] = change;
                else changes.push_back(change);
            }
            sectionStarts.push_back(time);
            time += section.beats * 60000.0 / bpm;
            beat += section.beats;
        }
        endTime = time;
    }

    double bpmAt(double time) const {
        const TimeChange* found = &changes.front();
        for (const TimeChange& c : changes) {
            if (c.time <= time + 0.001) found = &c;
        }
        return found->bpm;
    }

    double stepMsAt(double time) const { return 15000.0 / bpmAt(time); }

    int sectionIndexAt(double time) const {
        if (sectionStarts.empty()) return -1;
        int low = 0;
        int high = static_cast<int>(sectionStarts.size()) - 1;
        int found = 0;
        while (low <= high) {
            int mid = (low + high) / 2;
            if (sectionStarts[static_cast<size_t>(mid)] <= time + 0.001) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }
};

const Json& at(const Json& array, size_t i) {
    static const Json none;
    return i < array.items().size() ? array.items()[i] : none;
}

bool parseNumber(const std::string& raw, double& out) {
    std::string t = trimText(raw);
    if (t.empty()) return false;
    char* end = nullptr;
    out = std::strtod(t.c_str(), &end);
    if (end == t.c_str() || *end != '\0') return false;
    return std::isfinite(out);
}

double asDouble(const Json& v, double fallback) {
    if (v.kind() == Json::Kind::Number) return std::isfinite(v.asNumber()) ? v.asNumber() : fallback;
    if (v.kind() == Json::Kind::String) {
        double out;
        return parseNumber(v.asString(), out) ? out : fallback;
    }
    return fallback;
}

bool asBool(const Json& v, bool fallback) {
    if (v.kind() == Json::Kind::Bool) return v.asBool();
    if (v.kind() == Json::Kind::Number) return v.asNumber() != 0.0;
    if (v.kind() == Json::Kind::String) {
        std::string t = lowerText(trimText(v.asString()));
        if (t == "true" || t == "1") return true;
        if (t == "false" || t == "0") return false;
    }
    return fallback;
}

std::string asText(const Json& v) {
    switch (v.kind()) {
        case Json::Kind::Null: return "";
        case Json::Kind::String: return v.asString();
        case Json::Kind::Bool: return v.asBool() ? "true" : "false";
        case Json::Kind::Number: return numberToText(v.asNumber());
        default: return v.dump();
    }
}

std::string noteTypeOf(const Json& raw) {
    static const char* const legacy[] = {"", "Alt Animation", "Hey!", "Hurt Note", "GF Sing", "No Animation"};
    switch (raw.kind()) {
        case Json::Kind::String: return raw.asString();
        case Json::Kind::Bool: return raw.asBool() ? "Alt Animation" : "";
        case Json::Kind::Number: {
            long long i = static_cast<long long>(raw.asNumber());
            return i >= 0 && i < 6 ? legacy[i] : "";
        }
        default: return "";
    }
}

double sectionBeats(const Json& section) {
    double beats = asDouble(section["sectionBeats"], NAN);
    if (!std::isnan(beats) && beats > 0) return beats;
    double steps = asDouble(section["lengthInSteps"], NAN);
    if (!std::isnan(steps) && steps > 0) return steps / 4.0;
    return 4.0;
}

PsychSong parseSong(const std::string& raw) {
    std::string text = trimText(raw);
    if (text.compare(0, 3, "\xEF\xBB\xBF") == 0) text = text.substr(3);
    bool ok = false;
    Json root = Json::parse(text, ok);
    if (!ok) throw std::runtime_error("The file is not valid JSON.");
    const Json& song = root["song"].kind() == Json::Kind::Object ? root["song"] : root;
    if (song["notes"].kind() != Json::Kind::Array && song["events"].kind() != Json::Kind::Array) {
        throw std::runtime_error("This JSON does not look like a Psych Engine chart (no \"notes\" or \"events\" found).");
    }
    PsychSong out;
    out.format = asText(song["format"]);
    if (trimText(out.format).empty()) out.format = "unknown";
    bool absolute = out.format.compare(0, 8, "psych_v1") == 0;
    std::vector<PsychEvent> events;
    for (const Json& section : song["notes"].items()) {
        if (section.kind() != Json::Kind::Object) continue;
        PsychSection s;
        s.mustHit = asBool(section["mustHitSection"], true);
        for (const Json& note : section["sectionNotes"].items()) {
            if (note.kind() != Json::Kind::Array) {
                out.invalidNotes++;
                continue;
            }
            double time = asDouble(at(note, 0), NAN);
            double data = asDouble(at(note, 1), NAN);
            if (std::isnan(time) || std::isnan(data)) {
                out.invalidNotes++;
                continue;
            }
            int lane = static_cast<int>(data);
            if (lane < 0) {
                events.push_back({time, asText(at(note, 2)), asText(at(note, 3)), asText(at(note, 4))});
                continue;
            }
            int resolved = lane;
            if (resolved > 7) {
                resolved %= 8;
                out.wrappedLanes++;
            }
            if (!absolute) {
                bool playerHits = resolved < 4 ? s.mustHit : !s.mustHit;
                resolved = resolved % 4 + (playerHits ? 0 : 4);
            }
            double length = std::max(0.0, asDouble(at(note, 2), 0.0));
            s.notes.push_back({time, resolved, length, noteTypeOf(at(note, 3))});
        }
        s.beats = sectionBeats(section);
        s.gfSection = asBool(section["gfSection"], false);
        s.altAnim = asBool(section["altAnim"], false);
        s.changeBpm = asBool(section["changeBPM"], false);
        s.bpm = asDouble(section["bpm"], 0.0);
        out.sections.push_back(std::move(s));
    }
    for (const Json& entry : song["events"].items()) {
        if (entry.kind() != Json::Kind::Array) continue;
        double time = asDouble(at(entry, 0), NAN);
        if (std::isnan(time)) continue;
        const Json& payload = at(entry, 1);
        if (payload.kind() == Json::Kind::Array) {
            for (const Json& sub : payload.items()) {
                if (sub.kind() != Json::Kind::Array) continue;
                std::string name = asText(at(sub, 0));
                if (!trimText(name).empty()) events.push_back({time, name, asText(at(sub, 1)), asText(at(sub, 2))});
            }
        } else if (payload.kind() == Json::Kind::String && !trimText(payload.asString()).empty()) {
            events.push_back({time, payload.asString(), asText(at(entry, 2)), asText(at(entry, 3))});
        }
    }
    double bpm = asDouble(song["bpm"], 100.0);
    out.bpm = bpm > 0 ? bpm : 100.0;
    double speed = asDouble(song["speed"], 1.0);
    out.speed = speed > 0 ? speed : 1.0;
    out.offset = asDouble(song["offset"], 0.0);
    out.needsVoices = asBool(song["needsVoices"], true);
    out.name = asText(song["song"]);
    auto orDefault = [](const std::string& v, const std::string& d) { return trimText(v).empty() ? d : v; };
    out.player1 = orDefault(asText(song["player1"]), "bf");
    out.player2 = orDefault(asText(song["player2"]), "dad");
    std::string gf = asText(song["gfVersion"]);
    if (trimText(gf).empty()) gf = asText(song["player3"]);
    out.girlfriend = orDefault(gf, "gf");
    out.stage = orDefault(asText(song["stage"]), "stage");
    out.arrowSkin = asText(song["arrowSkin"]);
    std::stable_sort(events.begin(), events.end(), [](const PsychEvent& a, const PsychEvent& b) { return a.time < b.time; });
    out.events = events;
    return out;
}

struct Source {
    std::string difficulty;
    PsychSong song;
};

struct VEvent {
    double time;
    std::string kind;
    Json value;
};

struct Report {
    std::vector<std::pair<std::string, int>> noteCounts;
    std::vector<std::pair<std::string, int>> duplicates;
    std::vector<std::pair<std::string, int>> unsupported;
    std::set<std::string> noteKinds;
    std::vector<std::string> warnings;
    int cameraEvents = 0;
    int convertedEvents = 0;
    int keptEvents = 0;
    double offset = 0;
    std::vector<std::pair<std::string, std::vector<std::string>>> variations;

    void warn(const std::string& message) {
        if (std::find(warnings.begin(), warnings.end(), message) == warnings.end()) warnings.push_back(message);
    }

    bool hasUnsupported(const std::string& name) const {
        std::string key = lowerText(name);
        for (const auto& u : unsupported) if (lowerText(u.first) == key) return true;
        return false;
    }

    void skip(const std::string& name) {
        std::string key = lowerText(name);
        for (auto& u : unsupported) {
            if (lowerText(u.first) == key) {
                u.second++;
                return;
            }
        }
        unsupported.emplace_back(name, 1);
    }
};

struct Options {
    std::string songName;
    std::string artist;
    std::string charter;
    std::vector<PsychEvent> extraEvents;
    std::set<std::string> customStages;
    bool keepPsychEvents = true;
    Json playerVocals;
    Json opponentVocals;
    bool splitVariations = true;
    std::string noteStyle;
    std::set<std::string> pixelStages;
    std::string generatedBy = "V-Converter (Psych Engine to V-Slice)";
};

const std::vector<std::string> kOrder = {"easy", "normal", "hard", "erect", "nightmare"};

int rank(const std::string& d) {
    auto it = std::find(kOrder.begin(), kOrder.end(), d);
    return it == kOrder.end() ? static_cast<int>(kOrder.size()) : static_cast<int>(it - kOrder.begin());
}

std::string normalizeDifficulty(const std::string& raw) {
    std::string t = lowerText(trimText(raw));
    std::string out;
    bool space = false;
    for (char c : t) {
        if (std::isspace(static_cast<unsigned char>(c))) {
            if (!space) out += '-';
            space = true;
        } else {
            out += c;
            space = false;
        }
    }
    return out;
}

std::string slugText(const std::string& text) {
    std::string out;
    bool dash = false;
    for (char c : lowerText(trimText(text))) {
        if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
            out += c;
            dash = false;
        } else if (!dash) {
            out += '-';
            dash = true;
        }
    }
    while (!out.empty() && out.front() == '-') out.erase(out.begin());
    while (!out.empty() && out.back() == '-') out.pop_back();
    return out;
}

std::string pascalText(const std::string& text) {
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

std::string psychEventId(const std::string& name) {
    static const std::set<std::string> native = {"FocusCamera", "PlayAnimation", "ScrollSpeed", "SetCameraBop", "SetHealthIcon", "SetTargetBopSpeed", "ZoomCamera"};
    std::string id = pascalText(name);
    if (id.empty()) id = "PsychEvent";
    if (std::isdigit(static_cast<unsigned char>(id[0]))) id = "Event" + id;
    return native.count(id) ? "Psych" + id : id;
}

const std::map<std::string, std::string> kStages = {{"stage", "mainStage"}, {"spooky", "spookyMansion"}, {"philly", "phillyTrain"},
    {"limo", "limoRide"}, {"mall", "mallXmas"}, {"mallevil", "mallEvil"}, {"school", "school"}, {"schoolevil", "schoolEvil"}, {"tank", "tankmanBattlefield"}};

const std::map<std::string, std::string> kNoteKinds = {{"alt animation", "alt"}, {"hey!", "hey"}, {"hurt note", "hurt"}, {"no animation", "noanim"}, {"gf sing", "gf-sing"}};

std::string noteKindFor(const std::string& type) {
    auto it = kNoteKinds.find(lowerText(trimText(type)));
    return it != kNoteKinds.end() ? it->second : slugText(type);
}

struct Signature {
    std::vector<TimeChange> changes;
    std::set<PsychEvent> events;
    std::string player;
    std::string opponent;
    std::string girlfriend;
    std::string stage;
    bool operator==(const Signature& o) const {
        return changes == o.changes && events == o.events && player == o.player && opponent == o.opponent && girlfriend == o.girlfriend && stage == o.stage;
    }
};

Signature signatureOf(const PsychSong& song) {
    return {Timing(song).changes, std::set<PsychEvent>(song.events.begin(), song.events.end()), song.player1, song.player2, song.girlfriend, song.stage};
}

int sectionFocus(const PsychSong& song, int index) {
    if (index < 0 || index >= static_cast<int>(song.sections.size())) return 1;
    const PsychSection& s = song.sections[static_cast<size_t>(index)];
    if (s.gfSection) return 2;
    if (s.mustHit) return 0;
    return 1;
}

Json valueObject(std::initializer_list<std::pair<const char*, Json>> fields) {
    Json o = Json::object();
    for (const auto& f : fields) o.set(f.first, f.second);
    return o;
}

VEvent playAnimation(double time, const std::string& target, const std::string& anim) {
    return {time, "PlayAnimation", valueObject({{"target", Json::string(target)}, {"anim", Json::string(anim)}, {"force", Json::boolean(true)}})};
}

std::string animationTarget(const std::string& raw) {
    std::string k = lowerText(raw);
    if (k == "bf" || k == "boyfriend") return "bf";
    if (k == "gf" || k == "girlfriend") return "gf";
    double n;
    if (parseNumber(raw, n)) {
        long long r = std::llround(n);
        if (r == 1) return "bf";
        if (r == 2) return "gf";
    }
    return "dad";
}

bool convertEvent(const PsychEvent& event, const PsychSong& song, const Timing& timing, std::vector<VEvent>& out) {
    std::string v1 = trimText(event.value1);
    std::string v2 = trimText(event.value2);
    std::string name = lowerText(trimText(event.name));
    if (name == "hey!") {
        std::string k = lowerText(v1);
        std::vector<std::string> targets;
        if (k == "bf" || k == "boyfriend" || k == "0") targets = {"bf"};
        else if (k == "gf" || k == "girlfriend" || k == "1") targets = {"gf"};
        else targets = {"bf", "gf"};
        for (const std::string& t : targets) out.push_back(playAnimation(event.time, t, "hey"));
        return true;
    }
    if (name == "play animation") {
        if (v1.empty()) return false;
        out.push_back(playAnimation(event.time, animationTarget(v2), v1));
        return true;
    }
    if (name == "camera follow pos") {
        double x;
        double y;
        bool hx = parseNumber(v1, x);
        bool hy = parseNumber(v2, y);
        if (!hx && !hy) {
            out.push_back({event.time, "FocusCamera", valueObject({{"char", Json::number(sectionFocus(song, timing.sectionIndexAt(event.time)))}})});
        } else {
            out.push_back({event.time, "FocusCamera", valueObject({{"char", Json::number(-1)}, {"x", Json::number(hx ? x : 0.0)}, {"y", Json::number(hy ? y : 0.0)}})});
        }
        return true;
    }
    if (name == "change scroll speed") {
        double multiplier;
        double seconds;
        if (!parseNumber(v1, multiplier)) multiplier = 1.0;
        if (!parseNumber(v2, seconds)) seconds = 0.0;
        Json value = Json::object();
        value.set("scroll", Json::number(multiplier));
        if (seconds <= 0) {
            value.set("ease", Json::string("INSTANT"));
        } else {
            value.set("duration", Json::number(seconds * 1000.0 / timing.stepMsAt(event.time)));
            value.set("ease", Json::string("linear"));
            value.set("easeDir", Json::string(""));
        }
        value.set("absolute", Json::boolean(false));
        out.push_back({event.time, "ScrollSpeed", value});
        return true;
    }
    if (name == "set gf speed") {
        double rate;
        if (!parseNumber(v1, rate) || !(rate >= 1)) rate = 1;
        out.push_back({event.time, "SetTargetBopSpeed", valueObject({{"target", Json::string("girlfriend")}, {"rate", Json::number(std::floor(rate + 0.5))}})});
        return true;
    }
    return false;
}

std::vector<VEvent> cameraEvents(const PsychSong& song, const Timing& timing, Report& report, bool count) {
    std::vector<VEvent> result;
    int last = -100;
    for (size_t i = 0; i < song.sections.size(); i++) {
        int focus = sectionFocus(song, static_cast<int>(i));
        if (focus != last) {
            last = focus;
            result.push_back({timing.sectionStarts[i], "FocusCamera", valueObject({{"char", Json::number(focus)}})});
        }
    }
    if (count) report.cameraEvents = static_cast<int>(result.size());
    return result;
}

std::vector<VEvent> psychEvents(const std::vector<PsychEvent>& all, const PsychSong& song, const Timing& timing, Report& report, bool keep, bool count) {
    std::vector<PsychEvent> events;
    for (const PsychEvent& e : all) {
        if (std::find(events.begin(), events.end(), e) == events.end()) events.push_back(e);
    }
    std::stable_sort(events.begin(), events.end(), [](const PsychEvent& a, const PsychEvent& b) { return a.time < b.time; });
    std::vector<VEvent> result;
    for (const PsychEvent& event : events) {
        std::vector<VEvent> converted;
        if (!convertEvent(event, song, timing, converted)) {
            if (count || !report.hasUnsupported(event.name)) report.skip(trimText(event.name).empty() ? "(unnamed)" : event.name);
            if (keep && !trimText(event.name).empty()) {
                if (count) report.keptEvents++;
                result.push_back({event.time, psychEventId(event.name), valueObject({{"value1", Json::string(event.value1)}, {"value2", Json::string(event.value2)}})});
            }
        } else {
            if (count) report.convertedEvents++;
            for (VEvent& v : converted) result.push_back(std::move(v));
        }
    }
    return result;
}

Json convertNotes(const Source& source, Report& report) {
    std::set<std::pair<long long, int>> seen;
    struct N {
        double time;
        int lane;
        double length;
        std::string kind;
    };
    std::vector<N> notes;
    int duplicates = 0;
    for (const PsychSection& section : source.song.sections) {
        for (const PsychNote& note : section.notes) {
            std::pair<long long, int> key{static_cast<long long>(std::floor(note.time * 1000.0 + 0.5)), note.lane};
            if (!seen.insert(key).second) {
                duplicates++;
                continue;
            }
            std::string kind;
            if (trimText(note.type).empty()) kind = section.altAnim && !note.isPlayer() ? "alt" : "";
            else kind = noteKindFor(note.type);
            if (!kind.empty()) report.noteKinds.insert(kind);
            notes.push_back({note.time, note.lane, note.length, kind});
        }
    }
    std::stable_sort(notes.begin(), notes.end(), [](const N& a, const N& b) { return a.time != b.time ? a.time < b.time : a.lane < b.lane; });
    report.noteCounts.emplace_back(source.difficulty, static_cast<int>(notes.size()));
    if (duplicates > 0) report.duplicates.emplace_back(source.difficulty, duplicates);
    Json list = Json::array();
    for (const N& n : notes) {
        Json o = Json::object();
        o.set("time", Json::number(n.time));
        o.set("direction", Json::number(n.lane));
        o.set("length", Json::number(n.length));
        o.set("kind", Json::string(n.kind));
        list.push(o);
    }
    return list;
}

std::string convertStage(const std::string& stage, const Options& options, Report& report) {
    if (options.customStages.count(stage)) return stage;
    auto it = kStages.find(lowerText(stage));
    if (it != kStages.end()) return it->second;
    report.warn("Stage \"" + stage + "\" has no known V-Slice equivalent and was kept as-is. Make sure your mod has a V-Slice stage with this id.");
    return stage;
}

std::string noteStyleOf(const PsychSong& song, const Options& options) {
    std::string stage = lowerText(song.stage);
    bool pixelStage = stage == "school" || stage == "schoolevil";
    for (const std::string& p : options.pixelStages) if (lowerText(p) == stage) pixelStage = true;
    bool pixelSkin = lowerText(song.arrowSkin).find("pixel") != std::string::npos;
    return pixelStage || pixelSkin ? "pixel" : "funkin";
}

Json buildGroup(const Source& lead, const std::vector<const Source*>& group, const Options& options, Report& report, const std::string& variation) {
    const PsychSong& song = lead.song;
    Timing timing(song);
    Json notes = Json::object();
    Json speeds = Json::object();
    for (const Source* s : group) {
        notes.set(s->difficulty, convertNotes(*s, report));
        speeds.set(s->difficulty, Json::number(s->song.speed));
    }
    bool count = variation.empty();
    std::vector<VEvent> events = cameraEvents(song, timing, report, count);
    std::vector<PsychEvent> all = song.events;
    all.insert(all.end(), options.extraEvents.begin(), options.extraEvents.end());
    std::vector<VEvent> converted = psychEvents(all, song, timing, report, options.keepPsychEvents, count);
    for (VEvent& v : converted) events.push_back(std::move(v));
    std::stable_sort(events.begin(), events.end(), [](const VEvent& a, const VEvent& b) { return a.time < b.time; });

    Json metadata = Json::object();
    std::string songName = trimText(options.songName).empty() ? song.name : options.songName;
    metadata.set("songName", Json::string(trimText(songName).empty() ? "Unknown" : songName));
    metadata.set("artist", Json::string(trimText(options.artist).empty() ? "Unknown" : options.artist));
    metadata.set("charter", Json::string(trimText(options.charter).empty() ? "Unknown" : options.charter));
    Json diffs = Json::array();
    for (const Source* s : group) diffs.push(Json::string(s->difficulty));
    metadata.set("difficulties", diffs);
    Json characters = Json::object();
    characters.set("player", Json::string(song.player1));
    characters.set("girlfriend", Json::string(song.girlfriend));
    characters.set("opponent", Json::string(song.player2));
    if (!options.playerVocals.isNull()) characters.set("playerVocals", options.playerVocals);
    if (!options.opponentVocals.isNull()) characters.set("opponentVocals", options.opponentVocals);
    metadata.set("characters", characters);
    metadata.set("stage", Json::string(convertStage(song.stage, options, report)));
    metadata.set("noteStyle", Json::string(options.noteStyle.empty() ? noteStyleOf(song, options) : options.noteStyle));
    Json changes = Json::array();
    for (const TimeChange& c : timing.changes) {
        Json o = Json::object();
        o.set("time", Json::number(c.time));
        o.set("beat", Json::number(c.beat));
        o.set("bpm", Json::number(c.bpm));
        o.set("numerator", Json::number(c.numerator));
        changes.push(o);
    }
    metadata.set("timeChanges", changes);
    metadata.set("offset", Json::number(song.offset));
    metadata.set("generatedBy", Json::string(options.generatedBy));

    Json eventList = Json::array();
    for (const VEvent& e : events) {
        Json o = Json::object();
        o.set("time", Json::number(e.time));
        o.set("kind", Json::string(e.kind));
        o.set("value", e.value);
        eventList.push(o);
    }
    Json chart = Json::object();
    chart.set("scrollSpeed", speeds);
    chart.set("events", eventList);
    chart.set("notes", notes);

    if (timing.fractional > 0) {
        report.warn(std::to_string(timing.fractional) + " sections" + (variation.empty() ? "" : " in the " + variation + " variation") +
            " have a fractional length (like 3.5 beats). Note timing is kept, but V-Slice measures stay in whole beats, so the camera bop can drift after them.");
    }
    if (song.offset != 0) report.offset = song.offset;
    Json out = Json::object();
    out.set("metadata", metadata);
    out.set("chart", chart);
    return out;
}

std::string joinList(const std::vector<std::string>& items) {
    std::string out;
    for (size_t i = 0; i < items.size(); i++) {
        if (i) out += ", ";
        out += items[i];
    }
    return out;
}

}

Json convertPsychCharts(const Json& input, std::string& error) {
    std::vector<Source> sources;
    for (const Json& chart : input["charts"].items()) {
        Source s;
        s.difficulty = normalizeDifficulty(chart["difficulty"].text());
        try {
            s.song = parseSong(chart["text"].text());
        } catch (const std::exception& e) {
            error = e.what();
            return Json();
        }
        sources.push_back(std::move(s));
    }
    if (sources.empty()) {
        error = "Select at least one Psych Engine chart.";
        return Json();
    }
    for (size_t i = 0; i < sources.size(); i++) {
        if (sources[i].difficulty.empty()) {
            error = "Every chart needs a difficulty name.";
            return Json();
        }
        for (size_t j = i + 1; j < sources.size(); j++) {
            if (sources[i].difficulty == sources[j].difficulty) {
                error = "The difficulty \"" + sources[i].difficulty + "\" is assigned to more than one chart.";
                return Json();
            }
        }
    }
    const Json& o = input["options"];
    Options options;
    options.songName = o["songName"].text();
    options.artist = o["artist"].text();
    options.charter = o["charter"].text();
    for (const Json& e : o["extraEvents"].items()) options.extraEvents.push_back({e["time"].asNumber(), e["name"].text(), e["value1"].text(), e["value2"].text()});
    for (const Json& s : o["customStageIds"].items()) options.customStages.insert(s.text());
    options.keepPsychEvents = o["keepPsychEvents"].asBool(true);
    options.playerVocals = o["playerVocals"];
    options.opponentVocals = o["opponentVocals"];
    options.splitVariations = o["splitVariations"].asBool(true);
    options.noteStyle = o["noteStyle"].text();
    for (const Json& s : o["pixelStages"].items()) options.pixelStages.insert(s.text());
    if (!o["generatedBy"].text().empty()) options.generatedBy = o["generatedBy"].text();

    std::vector<Source*> ordered;
    for (Source& s : sources) ordered.push_back(&s);
    std::stable_sort(ordered.begin(), ordered.end(), [](const Source* a, const Source* b) {
        int ra = rank(a->difficulty);
        int rb = rank(b->difficulty);
        return ra != rb ? ra < rb : a->difficulty < b->difficulty;
    });
    Source* main = ordered.front();
    for (Source* s : ordered) {
        if (s->difficulty == "normal") {
            main = s;
            break;
        }
    }
    Report report;
    std::vector<std::vector<const Source*>> groups;
    if (options.splitVariations) {
        std::vector<Signature> keys;
        for (Source* s : ordered) {
            Signature sig = signatureOf(s->song);
            size_t k = 0;
            for (; k < keys.size(); k++) if (keys[k] == sig) break;
            if (k == keys.size()) {
                keys.push_back(sig);
                groups.emplace_back();
            }
            groups[k].push_back(s);
        }
        std::stable_sort(groups.begin(), groups.end(), [&](const std::vector<const Source*>& a, const std::vector<const Source*>& b) {
            int ka = std::find(a.begin(), a.end(), main) != a.end() ? 0 : 1;
            int kb = std::find(b.begin(), b.end(), main) != b.end() ? 0 : 1;
            return ka < kb;
        });
    } else {
        groups.emplace_back(ordered.begin(), ordered.end());
        Timing timing(main->song);
        for (Source* other : ordered) {
            if (other == main) continue;
            if (!(Timing(other->song).changes == timing.changes)) {
                report.warn("\"" + other->difficulty + "\" has different BPM changes than \"" + main->difficulty + "\". V-Slice shares one timing map per song, so the \"" + main->difficulty + "\" timing was used.");
            }
            std::set<PsychEvent> a(other->song.events.begin(), other->song.events.end());
            std::set<PsychEvent> b(main->song.events.begin(), main->song.events.end());
            if (a != b) {
                report.warn("\"" + other->difficulty + "\" has different events than \"" + main->difficulty + "\". V-Slice shares events between difficulties, so only the \"" + main->difficulty + "\" events were kept.");
            }
            const PsychSong& x = other->song;
            const PsychSong& y = main->song;
            if (x.player1 != y.player1 || x.player2 != y.player2 || x.girlfriend != y.girlfriend || x.stage != y.stage) {
                report.warn("\"" + other->difficulty + "\" uses different characters or stage than \"" + main->difficulty + "\". The \"" + main->difficulty + "\" setup was used.");
            }
        }
    }
    std::vector<std::string> names;
    std::set<std::string> used = {"default", ""};
    for (size_t g = 1; g < groups.size(); g++) {
        std::string base;
        bool erect = false;
        for (const Source* s : groups[g]) if (s->difficulty == "erect" || s->difficulty == "nightmare") erect = true;
        base = erect ? "erect" : slugText(groups[g].front()->difficulty);
        if (base.empty()) base = "alt";
        std::string name = base;
        int n = 2;
        while (!used.insert(name).second) name = base + std::to_string(n++);
        names.push_back(name);
    }
    for (Source* s : ordered) {
        if (s->song.invalidNotes > 0) report.warn("\"" + s->difficulty + "\": " + std::to_string(s->song.invalidNotes) + " malformed notes were ignored.");
        if (s->song.wrappedLanes > 0) report.warn("\"" + s->difficulty + "\": " + std::to_string(s->song.wrappedLanes) + " notes used lanes above 7 and were wrapped into the 8 standard lanes.");
    }
    std::vector<Json> built;
    for (size_t g = 0; g < groups.size(); g++) {
        const Source& lead = g == 0 ? *main : *groups[g].front();
        built.push_back(buildGroup(lead, groups[g], options, report, g == 0 ? "" : names[g - 1]));
    }
    Json result = Json::object();
    Json mainJson = built.front();
    Json mainMeta = mainJson["metadata"];
    Json variationIds = Json::array();
    for (const std::string& n : names) variationIds.push(Json::string(n));
    mainMeta.set("songVariations", variationIds);
    mainJson.set("metadata", mainMeta);
    result.set("main", mainJson);
    Json variations = Json::array();
    for (size_t g = 1; g < built.size(); g++) {
        Json v = built[g];
        v.set("variation", Json::string(names[g - 1]));
        variations.push(v);
        std::vector<std::string> diffs;
        for (const Source* s : groups[g]) diffs.push_back(s->difficulty);
        bool single = diffs.size() == 1;
        report.warn(joinList(diffs) + " " + (single ? "has" : "have") + " different BPM, time signatures, events, characters or stage than \"" + main->difficulty + "\", so " +
            (single ? "it was" : "they were") + " kept as the \"" + names[g - 1] + "\" variation. V-Slice plays a variation with Voices files ending in -" + names[g - 1] +
            " (Voices-<character>-" + names[g - 1] + ".ogg).");
        report.variations.emplace_back(names[g - 1], diffs);
    }
    result.set("variations", variations);

    Json r = Json::object();
    auto pairs = [](const std::vector<std::pair<std::string, int>>& list) {
        Json arr = Json::array();
        for (const auto& p : list) {
            Json o = Json::object();
            o.set("name", Json::string(p.first));
            o.set("count", Json::number(p.second));
            arr.push(o);
        }
        return arr;
    };
    r.set("noteCounts", pairs(report.noteCounts));
    r.set("duplicateNotes", pairs(report.duplicates));
    r.set("unsupportedEvents", pairs(report.unsupported));
    Json kinds = Json::array();
    for (const std::string& k : report.noteKinds) kinds.push(Json::string(k));
    r.set("noteKinds", kinds);
    Json warnings = Json::array();
    for (const std::string& w : report.warnings) warnings.push(Json::string(w));
    r.set("warnings", warnings);
    r.set("cameraEvents", Json::number(report.cameraEvents));
    r.set("convertedEvents", Json::number(report.convertedEvents));
    r.set("keptPsychEvents", Json::number(report.keptEvents));
    r.set("offsetApplied", Json::number(report.offset));
    Json vars = Json::array();
    for (const auto& v : report.variations) {
        Json o = Json::object();
        o.set("name", Json::string(v.first));
        Json d = Json::array();
        for (const std::string& s : v.second) d.push(Json::string(s));
        o.set("difficulties", d);
        vars.push(o);
    }
    r.set("variations", vars);
    result.set("report", r);
    return result;
}

}
