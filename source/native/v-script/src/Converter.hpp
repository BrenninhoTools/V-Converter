#pragma once

#include <map>
#include <set>
#include <string>
#include <utility>
#include <vector>

namespace vscript {

enum class Base { Module, Song, Stage, NoteKind, SongEvent };

struct ConvertOptions {
    Base base = Base::Module;
    std::string className = "Script";
    std::string id = "script";
    std::string scopeKind;
    std::string scopeId;
    int priority = 1000;
    std::string eventName;
    std::string title;
    std::string noteStyle;
    std::map<std::string, std::string> events;
    std::map<std::string, std::string> noteKinds;
    std::set<std::string> stageProps;
    std::set<int> skipLines;
    int behindLayer = 99;
    int frontLayer = 1000;
};

struct Issue {
    int line;
    std::string message;
};

struct ConvertResult {
    bool ok = false;
    std::string source;
    std::vector<std::string> callbacks;
    std::vector<Issue> issues;
    std::set<std::string> apiCalls;
};

ConvertResult convertLua(const std::string& source, const ConvertOptions& options);

std::string eventIdFor(const std::string& name);

std::string builtinEvent(const std::string& name, const std::string& className, const std::string& id, bool& known);

std::string hxString(const std::string& text);

std::string completeImports(const std::string& code, const std::set<std::string>& extra);

}
