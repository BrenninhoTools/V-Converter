#include "Funkin.hpp"

#include <cctype>
#include <climits>
#include <cmath>
#include <cstdio>
#include <cstdlib>

namespace vscript {

std::string trimText(const std::string& text) {
    size_t a = 0;
    while (a < text.size() && std::isspace(static_cast<unsigned char>(text[a]))) a++;
    size_t b = text.size();
    while (b > a && std::isspace(static_cast<unsigned char>(text[b - 1]))) b--;
    return text.substr(a, b - a);
}

std::string lowerText(std::string text) {
    for (char& c : text) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return text;
}

std::string numberToText(double value) {
    if (std::fmod(value, 1.0) == 0.0 && std::fabs(value) < 1e15) return std::to_string(static_cast<long long>(value));
    char buffer[40];
    for (int precision = 1; precision <= 17; precision++) {
        std::snprintf(buffer, sizeof buffer, "%.*g", precision, value);
        if (std::strtod(buffer, nullptr) == value) break;
    }
    std::string text = buffer;
    size_t e = text.find('e');
    if (e == std::string::npos) {
        if (text.find('.') == std::string::npos) text += ".0";
        return text;
    }
    std::string mantissa = text.substr(0, e);
    int exponent = std::atoi(text.c_str() + e + 1);
    if (mantissa.find('.') == std::string::npos) mantissa += ".0";
    return mantissa + "E" + std::to_string(exponent);
}

std::string doubleText(double value) {
    if (std::fmod(value, 1.0) == 0.0 && std::fabs(value) < 1e7) return std::to_string(static_cast<long long>(value)) + ".0";
    return numberToText(value);
}

double jsonNumber(double value) {
    double scaled = value * 1000.0;
    return std::floor(scaled + 0.5) / 1000.0;
}

Json ReportOut::toJson() const {
    Json out = Json::object();
    Json s = Json::array();
    for (const auto& stat : stats) {
        Json pair = Json::array();
        pair.push(Json::string(stat.first));
        pair.push(stat.second);
        s.push(pair);
    }
    out.set("stats", s);
    Json sec = Json::array();
    for (const auto& section : sections) {
        Json pair = Json::array();
        pair.push(Json::string(section.first));
        Json lines = Json::array();
        for (const std::string& l : section.second) lines.push(Json::string(l));
        pair.push(lines);
        sec.push(pair);
    }
    out.set("sections", sec);
    Json w = Json::array();
    for (const std::string& warning : warnings) w.push(Json::string(warning));
    out.set("warnings", w);
    return out;
}

bool Atlas::hasPrefix(const std::string& prefix) const {
    for (const AtlasFrame& f : frames) if (f.name.compare(0, prefix.size(), prefix) == 0) return true;
    return false;
}

namespace {

bool frameNumber(const std::string& name, const std::string& prefix, int& out) {
    std::string rest = name.compare(0, prefix.size(), prefix) == 0 ? name.substr(prefix.size()) : name;
    size_t start = rest.size();
    while (start > 0 && std::isdigit(static_cast<unsigned char>(rest[start - 1]))) start--;
    std::string digits = rest.substr(start);
    if (digits.empty() || digits.size() > 9) return false;
    out = std::atoi(digits.c_str());
    return true;
}

}

const AtlasFrame* Atlas::firstFrameOf(const std::string& prefix, const std::vector<int>& indices) const {
    std::vector<const AtlasFrame*> matching;
    for (const AtlasFrame& f : frames) if (f.name.compare(0, prefix.size(), prefix) == 0) matching.push_back(&f);
    if (matching.empty()) return nullptr;
    if (!indices.empty()) {
        for (const AtlasFrame* f : matching) {
            int n;
            if (frameNumber(f->name, prefix, n) && n == indices.front()) return f;
        }
    }
    const AtlasFrame* best = nullptr;
    long long bestNumber = LLONG_MAX;
    for (const AtlasFrame* f : matching) {
        int n;
        long long value = frameNumber(f->name, prefix, n) ? n : static_cast<long long>(INT_MAX);
        if (best == nullptr || value < bestNumber) {
            best = f;
            bestNumber = value;
        }
    }
    return best;
}

Atlas Atlas::fromJson(const Json& frames) {
    Atlas atlas;
    for (const Json& f : frames.items()) {
        if (f.items().size() < 3) continue;
        atlas.frames.push_back({f.items()[0].text(), f.items()[1].asNumber(), f.items()[2].asNumber()});
    }
    return atlas;
}

}
