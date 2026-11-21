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

std::string javaDoubleText(double value) {
    if (value == 0) return std::signbit(value) ? "-0.0" : "0.0";
    if (std::isnan(value)) return "NaN";
    if (std::isinf(value)) return value > 0 ? "Infinity" : "-Infinity";
    char buffer[48];
    for (int precision = 1; precision <= 17; precision++) {
        std::snprintf(buffer, sizeof buffer, "%.*e", precision - 1, value);
        if (std::strtod(buffer, nullptr) == value) break;
    }
    std::string text = buffer;
    bool negative = !text.empty() && text[0] == '-';
    if (negative) text.erase(0, 1);
    size_t e = text.find('e');
    int exponent = std::atoi(text.c_str() + e + 1);
    std::string digits;
    for (size_t i = 0; i < e; i++) if (std::isdigit(static_cast<unsigned char>(text[i]))) digits += text[i];
    while (digits.size() > 1 && digits.back() == '0') digits.pop_back();
    double magnitude = std::fabs(value);
    std::string out;
    if (magnitude >= 1e-3 && magnitude < 1e7) {
        int point = exponent + 1;
        if (point <= 0) {
            out = "0." + std::string(static_cast<size_t>(-point), '0') + digits;
        } else if (point >= static_cast<int>(digits.size())) {
            out = digits + std::string(static_cast<size_t>(point) - digits.size(), '0') + ".0";
        } else {
            out = digits.substr(0, static_cast<size_t>(point)) + "." + digits.substr(static_cast<size_t>(point));
        }
    } else {
        out = digits.substr(0, 1) + "." + (digits.size() > 1 ? digits.substr(1) : std::string("0")) + "E" + std::to_string(exponent);
    }
    return negative ? "-" + out : out;
}

std::string numberToText(double value) {
    if (std::fmod(value, 1.0) == 0.0 && std::fabs(value) < 1e15) return std::to_string(static_cast<long long>(value));
    return javaDoubleText(value);
}

std::string doubleText(double value) {
    if (std::fmod(value, 1.0) == 0.0 && std::fabs(value) < 1e7) return std::to_string(static_cast<long long>(value)) + ".0";
    return javaDoubleText(value);
}

namespace {

std::string kotlinNumber(double value) {
    if (std::isnan(value) || std::isinf(value)) return "null";
    if (value == std::floor(value) && std::fabs(value) < 1e15) {
        long long whole = static_cast<long long>(value);
        if (whole == 0 && std::signbit(value)) return "-0";
        return std::to_string(whole);
    }
    double magnitude = std::fabs(value);
    if (magnitude >= 1e-3 && magnitude < 1e7) {
        std::string fast;
        if (appendMillis(fast, value)) return fast;
    }
    std::string text = javaDoubleText(value);
    if (text.find('.') != std::string::npos && text.find('E') == std::string::npos) {
        while (!text.empty() && text.back() == '0') text.pop_back();
        if (!text.empty() && text.back() == '.') text.pop_back();
    }
    return text;
}

void kotlinQuote(const std::string& text, std::string& out) {
    out += '"';
    unsigned previous = ' ';
    size_t i = 0;
    while (i < text.size()) {
        unsigned char c = static_cast<unsigned char>(text[i]);
        unsigned cp = c;
        size_t length = 1;
        if (c >= 0xF0 && i + 3 < text.size()) {
            cp = ((c & 0x07u) << 18) | ((static_cast<unsigned char>(text[i + 1]) & 0x3Fu) << 12) | ((static_cast<unsigned char>(text[i + 2]) & 0x3Fu) << 6) | (static_cast<unsigned char>(text[i + 3]) & 0x3Fu);
            length = 4;
        } else if (c >= 0xE0 && i + 2 < text.size()) {
            cp = ((c & 0x0Fu) << 12) | ((static_cast<unsigned char>(text[i + 1]) & 0x3Fu) << 6) | (static_cast<unsigned char>(text[i + 2]) & 0x3Fu);
            length = 3;
        } else if (c >= 0xC0 && i + 1 < text.size()) {
            cp = ((c & 0x1Fu) << 6) | (static_cast<unsigned char>(text[i + 1]) & 0x3Fu);
            length = 2;
        }
        if (cp == '\\' || cp == '"') {
            out += '\\';
            out += static_cast<char>(cp);
        } else if (cp == '/' && previous == '<') {
            out += "\\/";
        } else if (cp == '\b') {
            out += "\\b";
        } else if (cp == '\t') {
            out += "\\t";
        } else if (cp == '\n') {
            out += "\\n";
        } else if (cp == '\f') {
            out += "\\f";
        } else if (cp == '\r') {
            out += "\\r";
        } else if (cp < 0x20 || (cp >= 0x80 && cp <= 0x9F) || (cp >= 0x2000 && cp <= 0x20FF)) {
            char hex[8];
            std::snprintf(hex, sizeof hex, "\\u%04x", cp);
            out += hex;
        } else {
            out.append(text, i, length);
        }
        previous = cp;
        i += length;
    }
    out += '"';
}

void kotlinWrite(const Json& value, int indent, int level, std::string& out) {
    switch (value.kind()) {
        case Json::Kind::Null: out += "null"; return;
        case Json::Kind::Bool: out += value.asBool() ? "true" : "false"; return;
        case Json::Kind::Number: out += kotlinNumber(value.asNumber()); return;
        case Json::Kind::String: kotlinQuote(value.asString(), out); return;
        case Json::Kind::Array: {
            const auto& items = value.items();
            if (items.empty()) {
                out += "[]";
                return;
            }
            if (items.size() == 1) {
                out += '[';
                kotlinWrite(items[0], indent, level, out);
                out += ']';
                return;
            }
            int inner = level + indent;
            out += '[';
            bool first = true;
            for (const Json& item : items) {
                if (!first) out += ',';
                first = false;
                if (indent > 0) out += '\n';
                out.append(static_cast<size_t>(inner), ' ');
                kotlinWrite(item, indent, inner, out);
            }
            if (indent > 0) out += '\n';
            out.append(static_cast<size_t>(level), ' ');
            out += ']';
            return;
        }
        case Json::Kind::Object: {
            const auto& fields = value.fields();
            if (fields.empty()) {
                out += "{}";
                return;
            }
            if (fields.size() == 1) {
                out += '{';
                kotlinQuote(fields[0].first, out);
                out += ':';
                if (indent > 0) out += ' ';
                kotlinWrite(fields[0].second, indent, level, out);
                out += '}';
                return;
            }
            int inner = level + indent;
            out += '{';
            bool first = true;
            for (const auto& field : fields) {
                if (!first) out += ',';
                first = false;
                if (indent > 0) out += '\n';
                out.append(static_cast<size_t>(inner), ' ');
                kotlinQuote(field.first, out);
                out += ':';
                if (indent > 0) out += ' ';
                kotlinWrite(field.second, indent, inner, out);
            }
            if (indent > 0) out += '\n';
            out.append(static_cast<size_t>(level), ' ');
            out += '}';
            return;
        }
    }
}

}

std::string kotlinJson(const Json& value, int indent) {
    std::string out;
    kotlinWrite(value, indent, 0, out);
    return out;
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
