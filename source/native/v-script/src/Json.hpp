#pragma once

#include <map>
#include <memory>
#include <string>
#include <vector>

namespace vscript {

class Json {
public:
    enum class Kind { Null, Bool, Number, String, Array, Object };

    Json() = default;
    static Json boolean(bool value);
    static Json number(double value);
    static Json string(std::string value);
    static Json array();
    static Json object();
    static Json parse(const std::string& text, bool& ok);

    Kind kind() const { return kind_; }
    bool isNull() const { return kind_ == Kind::Null; }
    bool asBool(bool fallback = false) const { return kind_ == Kind::Bool ? bool_ : fallback; }
    double asNumber(double fallback = 0) const { return kind_ == Kind::Number ? number_ : fallback; }
    const std::string& asString() const { return string_; }
    std::string text(const std::string& fallback = "") const { return kind_ == Kind::String ? string_ : fallback; }
    const std::vector<Json>& items() const { return items_; }
    const std::vector<std::pair<std::string, Json>>& fields() const { return fields_; }
    const Json& operator[](const std::string& key) const;

    void push(Json value);
    void set(const std::string& key, Json value);
    std::string dump() const;

private:
    Kind kind_ = Kind::Null;
    bool bool_ = false;
    double number_ = 0;
    std::string string_;
    std::vector<Json> items_;
    std::vector<std::pair<std::string, Json>> fields_;
};

std::string jsonQuote(const std::string& text);

}
