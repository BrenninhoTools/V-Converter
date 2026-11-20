#include "Lua.hpp"

#include <cctype>
#include <cstdlib>

namespace vscript {

namespace {

enum class Tok { Name, Number, String, Keyword, Symbol, End };

struct Token {
    Tok type;
    std::string text;
    int line;
};

bool isKeyword(const std::string& word) {
    static const char* const words[] = {"and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in",
        "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"};
    for (const char* w : words) {
        if (word == w) return true;
    }
    return false;
}

void appendUtf8(std::string& out, unsigned long code) {
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

class Lexer {
public:
    explicit Lexer(const std::string& source) : src_(source) {}

    std::vector<Token> run() {
        std::vector<Token> out;
        if (src_.compare(0, 3, "\xEF\xBB\xBF") == 0) pos_ = 3;
        if (src_.compare(pos_, 1, "#") == 0) {
            while (pos_ < src_.size() && src_[pos_] != '\n') pos_++;
        }
        while (true) {
            skip();
            if (pos_ >= src_.size()) {
                out.push_back({Tok::End, "", line_});
                return out;
            }
            out.push_back(next());
        }
    }

private:
    const std::string& src_;
    size_t pos_ = 0;
    int line_ = 1;

    char at(size_t offset = 0) const { return pos_ + offset < src_.size() ? src_[pos_ + offset] : '\0'; }

    int longBracket(size_t start) const {
        if (start >= src_.size() || src_[start] != '[') return -1;
        size_t i = start + 1;
        int level = 0;
        while (i < src_.size() && src_[i] == '=') {
            level++;
            i++;
        }
        return i < src_.size() && src_[i] == '[' ? level : -1;
    }

    std::string readLong(int level) {
        pos_ += static_cast<size_t>(level) + 2;
        if (at() == '\r') pos_++;
        if (at() == '\n') {
            line_++;
            pos_++;
        }
        std::string close = "]" + std::string(static_cast<size_t>(level), '=') + "]";
        size_t end = src_.find(close, pos_);
        if (end == std::string::npos) throw LuaError(line_, "unfinished long string or comment");
        std::string body = src_.substr(pos_, end - pos_);
        for (char c : body) {
            if (c == '\n') line_++;
        }
        pos_ = end + close.size();
        return body;
    }

    void skip() {
        while (pos_ < src_.size()) {
            char c = at();
            if (c == '\n') {
                line_++;
                pos_++;
            } else if (std::isspace(static_cast<unsigned char>(c))) {
                pos_++;
            } else if (c == '-' && at(1) == '-') {
                pos_ += 2;
                int level = longBracket(pos_);
                if (level >= 0) {
                    readLong(level);
                } else {
                    while (pos_ < src_.size() && at() != '\n') pos_++;
                }
            } else {
                break;
            }
        }
    }

    Token next() {
        int line = line_;
        char c = at();
        if (std::isalpha(static_cast<unsigned char>(c)) || c == '_') {
            size_t start = pos_;
            while (std::isalnum(static_cast<unsigned char>(at())) || at() == '_') pos_++;
            std::string word = src_.substr(start, pos_ - start);
            return {isKeyword(word) ? Tok::Keyword : Tok::Name, word, line};
        }
        if (std::isdigit(static_cast<unsigned char>(c)) || (c == '.' && std::isdigit(static_cast<unsigned char>(at(1))))) return number(line);
        if (c == '"' || c == '\'') return quoted(line);
        if (c == '[') {
            int level = longBracket(pos_);
            if (level >= 0) return {Tok::String, readLong(level), line};
        }
        static const char* const symbols[] = {"...", "..", "==", "~=", "<=", ">=", "::", "!=", "+", "-", "*", "/", "%", "^", "#", "<", ">", "=",
            "(", ")", "{", "}", "[", "]", ";", ":", ",", "."};
        for (const char* s : symbols) {
            size_t n = std::char_traits<char>::length(s);
            if (src_.compare(pos_, n, s) == 0) {
                pos_ += n;
                std::string text = s;
                if (text == "!=") text = "~=";
                return {Tok::Symbol, text, line};
            }
        }
        throw LuaError(line, std::string("unexpected symbol '") + c + "'");
    }

    Token number(int line) {
        size_t start = pos_;
        if (at() == '0' && (at(1) == 'x' || at(1) == 'X')) {
            pos_ += 2;
            while (std::isxdigit(static_cast<unsigned char>(at())) || at() == '.') pos_++;
            if (at() == 'p' || at() == 'P') {
                pos_++;
                if (at() == '+' || at() == '-') pos_++;
                while (std::isdigit(static_cast<unsigned char>(at()))) pos_++;
            }
        } else {
            while (std::isdigit(static_cast<unsigned char>(at())) || at() == '.') pos_++;
            if (at() == 'e' || at() == 'E') {
                pos_++;
                if (at() == '+' || at() == '-') pos_++;
                while (std::isdigit(static_cast<unsigned char>(at()))) pos_++;
            }
        }
        while (std::isalpha(static_cast<unsigned char>(at()))) pos_++;
        return {Tok::Number, src_.substr(start, pos_ - start), line};
    }

    Token quoted(int line) {
        char quote = at();
        pos_++;
        std::string out;
        while (true) {
            if (pos_ >= src_.size() || at() == '\n') throw LuaError(line, "unfinished string");
            char c = at();
            pos_++;
            if (c == quote) break;
            if (c != '\\') {
                out += c;
                continue;
            }
            char e = at();
            pos_++;
            switch (e) {
                case 'n': out += '\n'; break;
                case 't': out += '\t'; break;
                case 'r': out += '\r'; break;
                case 'a': out += '\a'; break;
                case 'b': out += '\b'; break;
                case 'f': out += '\f'; break;
                case 'v': out += '\v'; break;
                case '\n':
                    out += '\n';
                    line_++;
                    break;
                case 'x': {
                    std::string hex = src_.substr(pos_, 2);
                    pos_ += 2;
                    out += static_cast<char>(std::strtol(hex.c_str(), nullptr, 16));
                    break;
                }
                case 'z':
                    while (pos_ < src_.size() && std::isspace(static_cast<unsigned char>(at()))) {
                        if (at() == '\n') line_++;
                        pos_++;
                    }
                    break;
                case 'u': {
                    if (at() == '{') {
                        size_t close = src_.find('}', pos_);
                        if (close == std::string::npos) throw LuaError(line, "bad \\u escape");
                        appendUtf8(out, std::strtoul(src_.substr(pos_ + 1, close - pos_ - 1).c_str(), nullptr, 16));
                        pos_ = close + 1;
                    } else {
                        out += 'u';
                    }
                    break;
                }
                default:
                    if (std::isdigit(static_cast<unsigned char>(e))) {
                        std::string digits(1, e);
                        while (digits.size() < 3 && std::isdigit(static_cast<unsigned char>(at()))) {
                            digits += at();
                            pos_++;
                        }
                        out += static_cast<char>(std::atoi(digits.c_str()));
                    } else {
                        out += e;
                    }
            }
        }
        return {Tok::String, out, line};
    }
};

class Parser {
public:
    explicit Parser(std::vector<Token> tokens) : toks_(std::move(tokens)) {}

    Block chunk() {
        Block block = blockUntil();
        if (peek().type != Tok::End) throw LuaError(peek().line, "'" + peek().text + "' was not expected here");
        return block;
    }

private:
    std::vector<Token> toks_;
    size_t i_ = 0;

    const Token& peek(size_t ahead = 0) const { return toks_[std::min(i_ + ahead, toks_.size() - 1)]; }
    bool check(const char* text) const { return (peek().type == Tok::Symbol || peek().type == Tok::Keyword) && peek().text == text; }

    bool accept(const char* text) {
        if (check(text)) {
            i_++;
            return true;
        }
        return false;
    }

    void expect(const char* text, const char* what = nullptr) {
        if (!accept(text)) {
            std::string got = peek().type == Tok::End ? "end of file" : "'" + peek().text + "'";
            throw LuaError(peek().line, std::string("expected '") + text + "'" + (what ? std::string(" ") + what : "") + " near " + got);
        }
    }

    std::string name() {
        if (peek().type != Tok::Name) throw LuaError(peek().line, "expected a name near '" + peek().text + "'");
        return toks_[i_++].text;
    }

    bool blockEnds() const {
        if (peek().type == Tok::End) return true;
        if (peek().type != Tok::Keyword) return false;
        const std::string& t = peek().text;
        return t == "end" || t == "else" || t == "elseif" || t == "until";
    }

    Block blockUntil() {
        Block block;
        while (!blockEnds()) {
            if (accept(";")) continue;
            if (check("return")) {
                block.push_back(returnStat());
                break;
            }
            block.push_back(statement());
        }
        return block;
    }

    StatPtr returnStat() {
        int line = peek().line;
        i_++;
        auto stat = std::make_unique<Stat>(StatKind::Return, line);
        if (!blockEnds() && !check(";")) stat->values = exprList();
        accept(";");
        return stat;
    }

    StatPtr statement() {
        int line = peek().line;
        if (peek().type == Tok::Keyword) {
            const std::string t = peek().text;
            if (t == "if") return ifStat();
            if (t == "while") {
                i_++;
                auto stat = std::make_unique<Stat>(StatKind::While, line);
                stat->cond = expr();
                expect("do");
                stat->body = blockUntil();
                expect("end", "to close 'while'");
                return stat;
            }
            if (t == "do") {
                i_++;
                auto stat = std::make_unique<Stat>(StatKind::Do, line);
                stat->body = blockUntil();
                expect("end", "to close 'do'");
                return stat;
            }
            if (t == "for") return forStat();
            if (t == "repeat") {
                i_++;
                auto stat = std::make_unique<Stat>(StatKind::Repeat, line);
                stat->body = blockUntil();
                expect("until");
                stat->cond = expr();
                return stat;
            }
            if (t == "function") {
                i_++;
                auto stat = std::make_unique<Stat>(StatKind::Function, line);
                stat->path.push_back(name());
                while (accept(".")) stat->path.push_back(name());
                if (accept(":")) {
                    stat->path.push_back(name());
                    stat->method = true;
                }
                stat->func = body(line, stat->method);
                return stat;
            }
            if (t == "local") {
                i_++;
                if (accept("function")) {
                    auto stat = std::make_unique<Stat>(StatKind::LocalFunction, line);
                    stat->names.push_back(name());
                    stat->func = body(line, false);
                    return stat;
                }
                auto stat = std::make_unique<Stat>(StatKind::Local, line);
                do {
                    stat->names.push_back(name());
                    if (accept("<")) {
                        name();
                        expect(">");
                    }
                } while (accept(","));
                if (accept("=")) stat->values = exprList();
                return stat;
            }
            if (t == "break") {
                i_++;
                return std::make_unique<Stat>(StatKind::Break, line);
            }
            if (t == "goto") {
                i_++;
                auto stat = std::make_unique<Stat>(StatKind::Goto, line);
                stat->names.push_back(name());
                return stat;
            }
        }
        if (accept("::")) {
            auto stat = std::make_unique<Stat>(StatKind::Label, line);
            stat->names.push_back(name());
            expect("::");
            return stat;
        }
        ExprPtr first = suffixed();
        if (check("=") || check(",")) {
            auto stat = std::make_unique<Stat>(StatKind::Assign, line);
            stat->targets.push_back(std::move(first));
            while (accept(",")) stat->targets.push_back(suffixed());
            expect("=");
            stat->values = exprList();
            for (const auto& target : stat->targets) {
                if (target->kind != ExprKind::Name && target->kind != ExprKind::Index) throw LuaError(line, "cannot assign to this expression");
            }
            return stat;
        }
        if (first->kind != ExprKind::Call && first->kind != ExprKind::Method) throw LuaError(line, "syntax error near '" + peek().text + "'");
        auto stat = std::make_unique<Stat>(StatKind::Call, line);
        stat->values.push_back(std::move(first));
        return stat;
    }

    StatPtr ifStat() {
        int line = peek().line;
        i_++;
        auto stat = std::make_unique<Stat>(StatKind::If, line);
        IfClause first;
        first.cond = expr();
        expect("then");
        first.body = blockUntil();
        stat->clauses.push_back(std::move(first));
        while (accept("elseif")) {
            IfClause clause;
            clause.cond = expr();
            expect("then");
            clause.body = blockUntil();
            stat->clauses.push_back(std::move(clause));
        }
        if (accept("else")) {
            stat->hasElse = true;
            stat->elseBody = blockUntil();
        }
        expect("end", "to close 'if'");
        return stat;
    }

    StatPtr forStat() {
        int line = peek().line;
        i_++;
        std::string first = name();
        if (accept("=")) {
            auto stat = std::make_unique<Stat>(StatKind::NumericFor, line);
            stat->names.push_back(first);
            stat->values.push_back(expr());
            expect(",");
            stat->values.push_back(expr());
            if (accept(",")) stat->values.push_back(expr());
            expect("do");
            stat->body = blockUntil();
            expect("end", "to close 'for'");
            return stat;
        }
        auto stat = std::make_unique<Stat>(StatKind::GenericFor, line);
        stat->names.push_back(first);
        while (accept(",")) stat->names.push_back(name());
        expect("in");
        stat->values = exprList();
        expect("do");
        stat->body = blockUntil();
        expect("end", "to close 'for'");
        return stat;
    }

    std::shared_ptr<FuncBody> body(int line, bool method) {
        auto func = std::make_shared<FuncBody>();
        func->line = line;
        if (method) func->params.push_back("self");
        expect("(");
        if (!check(")")) {
            do {
                if (accept("...")) {
                    func->vararg = true;
                    break;
                }
                func->params.push_back(name());
            } while (accept(","));
        }
        expect(")");
        func->body = blockUntil();
        func->endLine = peek().line;
        expect("end", "to close 'function'");
        return func;
    }

    std::vector<ExprPtr> exprList() {
        std::vector<ExprPtr> list;
        list.push_back(expr());
        while (accept(",")) list.push_back(expr());
        return list;
    }

    static int leftPriority(const std::string& op) {
        if (op == "or") return 1;
        if (op == "and") return 2;
        if (op == "<" || op == ">" || op == "<=" || op == ">=" || op == "~=" || op == "==") return 3;
        if (op == "..") return 9;
        if (op == "+" || op == "-") return 10;
        if (op == "*" || op == "/" || op == "%") return 11;
        if (op == "^") return 14;
        return -1;
    }

    static int rightPriority(const std::string& op) {
        if (op == "..") return 8;
        if (op == "^") return 13;
        return leftPriority(op);
    }

    std::string binaryOp() const {
        const Token& t = peek();
        if (t.type == Tok::Symbol || (t.type == Tok::Keyword && (t.text == "and" || t.text == "or"))) {
            if (leftPriority(t.text) > 0) return t.text;
        }
        return "";
    }

    ExprPtr expr(int limit = 0) {
        ExprPtr left;
        int line = peek().line;
        if (check("not") || check("-") || check("#")) {
            std::string op = toks_[i_++].text;
            auto unary = std::make_unique<Expr>(ExprKind::Unary, line);
            unary->text = op;
            unary->a = expr(12);
            left = std::move(unary);
        } else {
            left = simple();
        }
        while (true) {
            std::string op = binaryOp();
            if (op.empty() || leftPriority(op) <= limit) break;
            int opLine = peek().line;
            i_++;
            auto binary = std::make_unique<Expr>(ExprKind::Binary, opLine);
            binary->text = op;
            binary->a = std::move(left);
            binary->b = expr(rightPriority(op));
            left = std::move(binary);
        }
        return left;
    }

    ExprPtr simple() {
        const Token& t = peek();
        int line = t.line;
        if (t.type == Tok::Number) {
            auto e = std::make_unique<Expr>(ExprKind::Number, line);
            e->text = t.text;
            i_++;
            return e;
        }
        if (t.type == Tok::String) {
            auto e = std::make_unique<Expr>(ExprKind::String, line);
            e->text = t.text;
            i_++;
            return e;
        }
        if (accept("nil")) return std::make_unique<Expr>(ExprKind::Nil, line);
        if (accept("true")) return std::make_unique<Expr>(ExprKind::True, line);
        if (accept("false")) return std::make_unique<Expr>(ExprKind::False, line);
        if (accept("...")) return std::make_unique<Expr>(ExprKind::Vararg, line);
        if (check("{")) return table();
        if (accept("function")) {
            auto e = std::make_unique<Expr>(ExprKind::Function, line);
            e->func = body(line, false);
            return e;
        }
        return suffixed();
    }

    ExprPtr primary() {
        int line = peek().line;
        if (peek().type == Tok::Name) {
            auto e = std::make_unique<Expr>(ExprKind::Name, line);
            e->text = toks_[i_++].text;
            return e;
        }
        if (accept("(")) {
            auto e = std::make_unique<Expr>(ExprKind::Paren, line);
            e->a = expr();
            expect(")");
            return e;
        }
        throw LuaError(line, "unexpected '" + (peek().type == Tok::End ? std::string("end of file") : peek().text) + "'");
    }

    std::vector<ExprPtr> callArgs() {
        std::vector<ExprPtr> args;
        if (peek().type == Tok::String) {
            auto e = std::make_unique<Expr>(ExprKind::String, peek().line);
            e->text = toks_[i_++].text;
            args.push_back(std::move(e));
            return args;
        }
        if (check("{")) {
            args.push_back(table());
            return args;
        }
        expect("(");
        if (!check(")")) args = exprList();
        expect(")");
        return args;
    }

    ExprPtr suffixed() {
        ExprPtr e = primary();
        while (true) {
            int line = peek().line;
            if (accept(".")) {
                auto index = std::make_unique<Expr>(ExprKind::Index, line);
                auto key = std::make_unique<Expr>(ExprKind::String, line);
                key->text = name();
                index->a = std::move(e);
                index->b = std::move(key);
                e = std::move(index);
            } else if (accept("[")) {
                auto index = std::make_unique<Expr>(ExprKind::Index, line);
                index->a = std::move(e);
                index->b = expr();
                expect("]");
                e = std::move(index);
            } else if (accept(":")) {
                auto call = std::make_unique<Expr>(ExprKind::Method, line);
                call->text = name();
                call->a = std::move(e);
                call->args = callArgs();
                e = std::move(call);
            } else if (check("(") || check("{") || peek().type == Tok::String) {
                auto call = std::make_unique<Expr>(ExprKind::Call, line);
                call->a = std::move(e);
                call->args = callArgs();
                e = std::move(call);
            } else {
                return e;
            }
        }
    }

    ExprPtr table() {
        int line = peek().line;
        expect("{");
        auto e = std::make_unique<Expr>(ExprKind::Table, line);
        while (!check("}")) {
            TableField field;
            if (accept("[")) {
                field.kind = TableField::Kind::Keyed;
                field.key = expr();
                expect("]");
                expect("=");
                field.value = expr();
            } else if (peek().type == Tok::Name && peek(1).type == Tok::Symbol && peek(1).text == "=") {
                field.kind = TableField::Kind::Named;
                field.name = name();
                expect("=");
                field.value = expr();
            } else {
                field.value = expr();
            }
            e->fields.push_back(std::move(field));
            if (!accept(",") && !accept(";")) break;
        }
        expect("}", "to close the table");
        return e;
    }
};

}

Block parseLua(const std::string& source) {
    Lexer lexer(source);
    Parser parser(lexer.run());
    return parser.chunk();
}

}
