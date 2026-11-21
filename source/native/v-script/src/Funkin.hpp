#pragma once

#include "Json.hpp"

#include <string>
#include <utility>
#include <vector>

namespace vscript {

std::string trimText(const std::string& text);

std::string lowerText(std::string text);

std::string numberToText(double value);

double jsonNumber(double value);

std::string doubleText(double value);

struct ReportOut {
    std::vector<std::pair<std::string, Json>> stats;
    std::vector<std::pair<std::string, std::vector<std::string>>> sections;
    std::vector<std::string> warnings;

    void stat(const std::string& label, Json value) { stats.emplace_back(label, std::move(value)); }
    void section(const std::string& title, const std::vector<std::string>& lines) {
        if (!lines.empty()) sections.emplace_back(title, lines);
    }
    void warn(const std::string& message) {
        for (const std::string& w : warnings) if (w == message) return;
        warnings.push_back(message);
    }
    Json toJson() const;
};

struct AtlasFrame {
    std::string name;
    double width;
    double height;
};

struct Atlas {
    std::vector<AtlasFrame> frames;
    const AtlasFrame* first() const { return frames.empty() ? nullptr : &frames.front(); }
    bool hasPrefix(const std::string& prefix) const;
    const AtlasFrame* firstFrameOf(const std::string& prefix, const std::vector<int>& indices) const;
    static Atlas fromJson(const Json& frames);
};

Json convertPsychCharts(const Json& input, std::string& error);

Json convertPsychCharacter(const Json& input, std::string& error);

Json convertPsychStage(const Json& input, std::string& error);

}
