#include "Json.hpp"

#include <cmath>
#include <cstdio>
#include <cstdlib>

namespace vscript {

namespace {

const Json kNull;

struct Reader {
    const std::string& text;
    size_t at = 0;
    bool ok = true;

    void space() {
        while (at < text.size() && (text[at] == ' ' || text[at] == '\n' || text[at] == '\r' || text[at] == '\t')) at++;
    }

    bool take(char c) {
        space();
        if (at < text.size() && text[at] == c) {
            at++;
            return true;
        }
        return false;
    }

    void utf8(std::string& out, unsigned code) {
        if (code < 0x80) {
            out += static_cast<char>(code);
        } else if (code < 0x800) {
            out += static_cast<char>(0xC0 | (code >> 6));
            out += static_cast<char>(0x80 | (code & 0x3F));
        } else if (code < 0x10000) {
            out += static_cast<char>(0xE0 | (code >> 12));
            out += static_cast<char>(0x80 | ((code >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (code & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (code >> 18));
            out += static_cast<char>(0x80 | ((code >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((code >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (code & 0x3F));
        }
    }

    unsigned hex4() {
        if (at + 4 > text.size()) {
            ok = false;
            return 0;
        }
        unsigned value = static_cast<unsigned>(std::strtoul(text.substr(at, 4).c_str(), nullptr, 16));
        at += 4;
        return value;
    }

    std::string str() {
        std::string out;
        at++;
        while (at < text.size() && text[at] != '"') {
            char c = text[at++];
            if (c != '\\') {
                out += c;
                continue;
            }
            if (at >= text.size()) break;
            char e = text[at++];
            switch (e) {
                case 'n': out += '\n'; break;
                case 't': out += '\t'; break;
                case 'r': out += '\r'; break;
                case 'b': out += '\b'; break;
                case 'f': out += '\f'; break;
                case 'u': {
                    unsigned code = hex4();
                    if (code >= 0xD800 && code < 0xDC00 && at + 1 < text.size() && text[at] == '\\' && text[at + 1] == 'u') {
                        at += 2;
                        unsigned low = hex4();
                        code = 0x10000 + ((code - 0xD800) << 10) + (low - 0xDC00);
                    }
                    utf8(out, code);
                    break;
                }
                default: out += e;
            }
        }
        if (at >= text.size()) ok = false;
        at++;
        return out;
    }

    Json value() {
        space();
        if (at >= text.size()) {
            ok = false;
            return Json();
        }
        char c = text[at];
        if (c == '{') {
            at++;
            Json obj = Json::object();
            if (take('}')) return obj;
            do {
                space();
                if (at >= text.size() || text[at] != '"') {
                    ok = false;
                    return obj;
                }
                std::string key = str();
                if (!take(':')) {
                    ok = false;
                    return obj;
                }
                obj.set(key, value());
            } while (ok && take(','));
            if (!take('}')) ok = false;
            return obj;
        }
        if (c == '[') {
            at++;
            Json arr = Json::array();
            if (take(']')) return arr;
            do {
                arr.push(value());
            } while (ok && take(','));
            if (!take(']')) ok = false;
            return arr;
        }
        if (c == '"') return Json::string(str());
        if (text.compare(at, 4, "true") == 0) {
            at += 4;
            return Json::boolean(true);
        }
        if (text.compare(at, 5, "false") == 0) {
            at += 5;
            return Json::boolean(false);
        }
        if (text.compare(at, 4, "null") == 0) {
            at += 4;
            return Json();
        }
        const char* start = text.c_str() + at;
        char* end = nullptr;
        double number = std::strtod(start, &end);
        if (end == start) {
            ok = false;
            return Json();
        }
        at += static_cast<size_t>(end - start);
        return Json::number(number);
    }
};

}

Json Json::boolean(bool value) {
    Json j;
    j.kind_ = Kind::Bool;
    j.bool_ = value;
    return j;
}

Json Json::number(double value) {
    Json j;
    j.kind_ = Kind::Number;
    j.number_ = value;
    return j;
}

Json Json::string(std::string value) {
    Json j;
    j.kind_ = Kind::String;
    j.string_ = std::move(value);
    return j;
}

Json Json::array() {
    Json j;
    j.kind_ = Kind::Array;
    return j;
}

Json Json::object() {
    Json j;
    j.kind_ = Kind::Object;
    return j;
}

Json Json::parse(const std::string& text, bool& ok) {
    Reader reader{text};
    Json result = reader.value();
    reader.space();
    ok = reader.ok && reader.at == text.size();
    return ok ? result : Json();
}

const Json& Json::operator[](const std::string& key) const {
    for (const auto& field : fields_) {
        if (field.first == key) return field.second;
    }
    return kNull;
}

void Json::push(Json value) {
    items_.push_back(std::move(value));
}

void Json::set(const std::string& key, Json value) {
    for (auto& field : fields_) {
        if (field.first == key) {
            field.second = std::move(value);
            return;
        }
    }
    fields_.emplace_back(key, std::move(value));
}

std::string jsonQuote(const std::string& text) {
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
                    std::snprintf(buffer, sizeof buffer, "\\u%04x", c);
                    out += buffer;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out + "\"";
}

std::string Json::dump() const {
    switch (kind_) {
        case Kind::Null: return "null";
        case Kind::Bool: return bool_ ? "true" : "false";
        case Kind::Number: {
            if (std::floor(number_) == number_ && std::fabs(number_) < 1e15) return std::to_string(static_cast<long long>(number_));
            char buffer[32];
            std::snprintf(buffer, sizeof buffer, "%.17g", number_);
            return buffer;
        }
        case Kind::String: return jsonQuote(string_);
        case Kind::Array: {
            std::string out = "[";
            for (size_t i = 0; i < items_.size(); i++) {
                if (i > 0) out += ",";
                out += items_[i].dump();
            }
            return out + "]";
        }
        case Kind::Object: {
            std::string out = "{";
            for (size_t i = 0; i < fields_.size(); i++) {
                if (i > 0) out += ",";
                out += jsonQuote(fields_[i].first) + ":" + fields_[i].second.dump();
            }
            return out + "}";
        }
    }
    return "null";
}

}
