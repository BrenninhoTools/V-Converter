#pragma once

#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace vscript {

struct LuaError : std::runtime_error {
    int line;
    LuaError(int line, const std::string& message) : std::runtime_error(message), line(line) {}
};

struct Stat;
struct Expr;
struct FuncBody;

using ExprPtr = std::unique_ptr<Expr>;
using StatPtr = std::unique_ptr<Stat>;
using Block = std::vector<StatPtr>;

enum class ExprKind { Nil, True, False, Number, String, Vararg, Function, Table, Binary, Unary, Name, Index, Call, Method, Paren };

struct TableField {
    enum class Kind { Positional, Named, Keyed } kind = Kind::Positional;
    std::string name;
    ExprPtr key;
    ExprPtr value;
};

struct Expr {
    ExprKind kind;
    int line = 0;
    std::string text;
    ExprPtr a;
    ExprPtr b;
    std::vector<ExprPtr> args;
    std::vector<TableField> fields;
    std::shared_ptr<FuncBody> func;

    Expr(ExprKind kind, int line) : kind(kind), line(line) {}
};

struct FuncBody {
    std::vector<std::string> params;
    bool vararg = false;
    Block body;
    int line = 0;
    int endLine = 0;
};

enum class StatKind { Local, Assign, Call, Do, While, Repeat, If, NumericFor, GenericFor, Function, LocalFunction, Return, Break, Goto, Label };

struct IfClause {
    ExprPtr cond;
    Block body;
};

struct Stat {
    StatKind kind;
    int line = 0;
    std::vector<std::string> names;
    std::vector<ExprPtr> targets;
    std::vector<ExprPtr> values;
    ExprPtr cond;
    Block body;
    std::vector<IfClause> clauses;
    Block elseBody;
    bool hasElse = false;
    std::shared_ptr<FuncBody> func;
    std::vector<std::string> path;
    bool method = false;

    Stat(StatKind kind, int line) : kind(kind), line(line) {}
};

Block parseLua(const std::string& source);

}
