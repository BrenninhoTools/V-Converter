#pragma once

#include "Json.hpp"

#include <string>

namespace vscript {

std::string generateScript(const Json& spec, std::string& error);

}
