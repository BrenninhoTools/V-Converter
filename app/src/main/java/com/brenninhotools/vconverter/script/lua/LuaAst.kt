package com.brenninhotools.vconverter.script.lua

sealed class Expr {
    abstract val line: Int

    data class Nil(override val line: Int) : Expr()
    data class Bool(val value: Boolean, override val line: Int) : Expr()
    data class Num(val text: String, override val line: Int) : Expr()
    data class Str(val value: String, override val line: Int) : Expr()
    data class Vararg(override val line: Int) : Expr()
    data class Func(val params: List<String>, val isVararg: Boolean, val body: List<Stat>, override val line: Int) : Expr()
    data class Table(val fields: List<TableField>, override val line: Int) : Expr()
    data class Binary(val op: String, val left: Expr, val right: Expr, override val line: Int) : Expr()
    data class Unary(val op: String, val operand: Expr, override val line: Int) : Expr()
    data class Name(val name: String, override val line: Int) : Expr()
    data class Index(val target: Expr, val key: Expr, override val line: Int) : Expr()
    data class Field(val target: Expr, val name: String, override val line: Int) : Expr()
    data class Call(val function: Expr, val args: List<Expr>, override val line: Int) : Expr()
    data class Method(val target: Expr, val name: String, val args: List<Expr>, override val line: Int) : Expr()
    data class Paren(val inner: Expr, override val line: Int) : Expr()
}

sealed class TableField {
    data class Positional(val value: Expr) : TableField()
    data class Named(val name: String, val value: Expr) : TableField()
    data class Keyed(val key: Expr, val value: Expr) : TableField()
}

sealed class Stat {
    abstract val line: Int

    data class Local(val names: List<String>, val values: List<Expr>, override val line: Int) : Stat()
    data class LocalFunction(val name: String, val function: Expr.Func, override val line: Int) : Stat()
    data class FunctionDecl(val path: List<String>, val method: String?, val function: Expr.Func, override val line: Int) : Stat()
    data class Assign(val targets: List<Expr>, val values: List<Expr>, override val line: Int) : Stat()
    data class CallStat(val call: Expr, override val line: Int) : Stat()
    data class Do(val body: List<Stat>, override val line: Int) : Stat()
    data class While(val condition: Expr, val body: List<Stat>, override val line: Int) : Stat()
    data class Repeat(val body: List<Stat>, val condition: Expr, override val line: Int) : Stat()
    data class If(val branches: List<Pair<Expr, List<Stat>>>, val otherwise: List<Stat>?, override val line: Int) : Stat()
    data class NumericFor(val variable: String, val start: Expr, val limit: Expr, val step: Expr?, val body: List<Stat>, override val line: Int) : Stat()
    data class GenericFor(val names: List<String>, val iterators: List<Expr>, val body: List<Stat>, override val line: Int) : Stat()
    data class Return(val values: List<Expr>, override val line: Int) : Stat()
    data class Break(override val line: Int) : Stat()
    data class Goto(val label: String, override val line: Int) : Stat()
    data class Label(val name: String, override val line: Int) : Stat()
}
