package com.brenninhotools.vconverter.json

class JSONException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private object JsonNull {
    override fun toString(): String = "null"
    override fun equals(other: Any?): Boolean = other == null || other === this
    override fun hashCode(): Int = 0
}

internal object JsonText {
    fun quote(text: String?): String {
        if (text == null) return "\"\""
        val sb = StringBuilder(text.length + 2)
        sb.append('"')
        var previous = ' '
        for (c in text) {
            when {
                c == '\\' || c == '"' -> sb.append('\\').append(c)
                c == '/' && previous == '<' -> sb.append("\\/")
                c == '\b' -> sb.append("\\b")
                c == '\t' -> sb.append("\\t")
                c == '\n' -> sb.append("\\n")
                c == '\u000C' -> sb.append("\\f")
                c == '\r' -> sb.append("\\r")
                c < ' ' || (c in '\u0080'..'\u009f') || (c in ' '..'⃿') -> {
                    val hex = c.code.toString(16)
                    sb.append("\\u").append("0000".substring(hex.length)).append(hex)
                }
                else -> sb.append(c)
            }
            previous = c
        }
        return sb.append('"').toString()
    }

    fun number(value: Number): String {
        when (value) {
            is Int, is Long, is Short, is Byte -> return value.toString()
            is Float -> {
                if (value.isNaN() || value.isInfinite()) throw JSONException("JSON does not allow non-finite numbers.")
                return trim(value.toString())
            }
            else -> {
                val d = value.toDouble()
                if (d.isNaN() || d.isInfinite()) throw JSONException("JSON does not allow non-finite numbers.")
                if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) {
                    val l = d.toLong()
                    return if (l == 0L && 1.0 / d < 0) "-0" else l.toString()
                }
                return trim(d.toString())
            }
        }
    }

    private fun trim(text: String): String {
        var s = text
        if (s.indexOf('.') > 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0) {
            while (s.endsWith("0")) s = s.dropLast(1)
            if (s.endsWith(".")) s = s.dropLast(1)
        }
        return s
    }

    fun write(value: Any?, indent: Int, level: Int, sb: StringBuilder) {
        when (value) {
            null -> sb.append("null")
            is JSONObject -> value.write(sb, indent, level)
            is JSONArray -> value.write(sb, indent, level)
            is String -> sb.append(quote(value))
            is Number -> sb.append(number(value))
            is Boolean -> sb.append(value.toString())
            is Map<*, *> -> JSONObject(value).write(sb, indent, level)
            is Collection<*> -> JSONArray(value).write(sb, indent, level)
            is Array<*> -> JSONArray(value.toList()).write(sb, indent, level)
            is Enum<*> -> sb.append(quote(value.name))
            else -> if (value == JSONObject.NULL) sb.append("null") else sb.append(quote(value.toString()))
        }
    }

    fun pad(sb: StringBuilder, count: Int) {
        repeat(count) { sb.append(' ') }
    }

    fun toNumber(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }

    fun toBoolean(value: Any?): Boolean? = when {
        value is Boolean -> value
        value is String && value.equals("true", ignoreCase = true) -> true
        value is String && value.equals("false", ignoreCase = true) -> false
        else -> null
    }

    fun stringToValue(text: String): Any? {
        if (text.isEmpty()) return text
        if (text.equals("true", ignoreCase = true)) return true
        if (text.equals("false", ignoreCase = true)) return false
        if (text.equals("null", ignoreCase = true)) return JSONObject.NULL
        val first = text[0]
        if (first in '0'..'9' || first == '-' || first == '.' || first == '+') {
            val decimal = text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0 || text == "-0"
            if (decimal) {
                text.toDoubleOrNull()?.let { if (!it.isNaN() && !it.isInfinite()) return it }
            } else {
                text.toLongOrNull()?.let { return if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
                text.toDoubleOrNull()?.let { if (!it.isNaN() && !it.isInfinite()) return it }
            }
        }
        return text
    }

    fun wrap(value: Any?): Any? = when (value) {
        null -> null
        is JSONObject, is JSONArray, is String, is Number, is Boolean -> value
        is Map<*, *> -> JSONObject(value)
        is Collection<*> -> JSONArray(value)
        is Array<*> -> JSONArray(value.toList())
        is IntArray -> JSONArray(value.toList())
        is DoubleArray -> JSONArray(value.toList())
        is FloatArray -> JSONArray(value.toList())
        is LongArray -> JSONArray(value.toList())
        is BooleanArray -> JSONArray(value.toList())
        is Enum<*> -> value.name
        else -> if (value == JSONObject.NULL) JSONObject.NULL else value
    }

    fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.toMap()
        is JSONArray -> value.toList()
        else -> if (value == JSONObject.NULL) null else value
    }
}

internal class JsonParser(private val text: String) {
    private var i = 0

    fun error(message: String): JSONException = JSONException("$message at character ${i + 1}")

    private fun skip() {
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() || c == '﻿' -> i++
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    val end = text.indexOf("*/", i + 2)
                    if (end < 0) throw error("Unclosed comment")
                    i = end + 2
                }
                c == '#' -> {
                    while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
                }
                else -> return
            }
        }
    }

    private fun peek(): Char {
        skip()
        return if (i < text.length) text[i] else '\u0000'
    }

    fun end() {
        skip()
        if (i < text.length) throw error("Unexpected text after the JSON value")
    }

    fun value(): Any? {
        return when (val c = peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"', '\'' -> string(c)
            '\u0000' -> throw error("Unexpected end of text")
            else -> {
                val start = i
                while (i < text.length && text[i] !in ",:]}/\\\"[{;=#" && !text[i].isWhitespace()) i++
                val raw = text.substring(start, i).trim()
                if (raw.isEmpty()) throw error("Missing value")
                JsonText.stringToValue(raw)
            }
        }
    }

    private fun string(quote: Char): String {
        i++
        val sb = StringBuilder()
        while (true) {
            if (i >= text.length) throw error("Unterminated string")
            val c = text[i++]
            when (c) {
                quote -> return sb.toString()
                '\\' -> {
                    if (i >= text.length) throw error("Unterminated string")
                    when (val e = text[i++]) {
                        'b' -> sb.append('\b')
                        't' -> sb.append('\t')
                        'n' -> sb.append('\n')
                        'f' -> sb.append('\u000C')
                        'r' -> sb.append('\r')
                        'u' -> {
                            if (i + 4 > text.length) throw error("Illegal escape")
                            val code = text.substring(i, i + 4).toIntOrNull(16) ?: throw error("Illegal escape")
                            sb.append(code.toChar())
                            i += 4
                        }
                        else -> sb.append(e)
                    }
                }
                '\n', '\r' -> if (quote == '\'') throw error("Unterminated string") else sb.append(c)
                else -> sb.append(c)
            }
        }
    }

    private fun parseObject(): JSONObject {
        val obj = JSONObject()
        i++
        if (peek() == '}') {
            i++
            return obj
        }
        while (true) {
            val keyChar = peek()
            val key = when (keyChar) {
                '"', '\'' -> string(keyChar)
                '}' -> {
                    i++
                    return obj
                }
                '\u0000' -> throw error("A JSONObject text must end with '}'")
                else -> value().toString()
            }
            when (peek()) {
                ':' -> {
                    i++
                    if (i < text.length && text[i] == '>') i++
                }
                '=' -> {
                    i++
                    if (i < text.length && text[i] == '>') i++
                }
                else -> throw error("Expected a ':' after a key")
            }
            obj.map[key] = value()
            when (peek()) {
                ',', ';' -> {
                    i++
                    if (peek() == '}') {
                        i++
                        return obj
                    }
                }
                '}' -> {
                    i++
                    return obj
                }
                else -> throw error("Expected a ',' or '}'")
            }
        }
    }

    private fun parseArray(): JSONArray {
        val arr = JSONArray()
        i++
        if (peek() == ']') {
            i++
            return arr
        }
        while (true) {
            when (peek()) {
                ',' -> {
                    i++
                    arr.list.add(JSONObject.NULL)
                    continue
                }
                ']' -> {
                    i++
                    return arr
                }
                '\u0000' -> throw error("A JSONArray text must end with ']'")
            }
            arr.list.add(value())
            when (peek()) {
                ',', ';' -> {
                    i++
                    if (peek() == ']') {
                        i++
                        return arr
                    }
                }
                ']' -> {
                    i++
                    return arr
                }
                else -> throw error("Expected a ',' or ']'")
            }
        }
    }
}

class JSONObject() {
    internal val map: LinkedHashMap<String, Any?> = LinkedHashMap()

    constructor(source: String) : this() {
        val parser = JsonParser(source)
        val value = parser.value()
        if (value !is JSONObject) throw parser.error("A JSONObject text must begin with '{'")
        parser.end()
        map.putAll(value.map)
    }

    constructor(source: Map<*, *>?) : this() {
        source?.forEach { (k, v) -> if (k != null && v != null) map[k.toString()] = JsonText.wrap(v) }
    }

    constructor(source: JSONObject, names: Array<String>) : this() {
        names.forEach { name -> source.opt(name)?.let { map[name] = it } }
    }

    fun length(): Int = map.size

    fun isEmpty(): Boolean = map.isEmpty()

    fun has(key: String): Boolean = map.containsKey(key)

    fun isNull(key: String): Boolean = map[key].let { it == null || it == NULL }

    fun keys(): Iterator<String> = map.keys.toList().iterator()

    fun keySet(): MutableSet<String> = map.keys

    fun names(): JSONArray? = if (map.isEmpty()) null else JSONArray(map.keys.toList())

    fun remove(key: String): Any? = map.remove(key)

    fun put(key: String, value: Any?): JSONObject {
        if (value == null) map.remove(key) else {
            if (value is Double && (value.isNaN() || value.isInfinite())) throw JSONException("JSON does not allow non-finite numbers.")
            if (value is Float && (value.isNaN() || value.isInfinite())) throw JSONException("JSON does not allow non-finite numbers.")
            map[key] = JsonText.wrap(value)
        }
        return this
    }

    fun put(key: String, value: Boolean): JSONObject = put(key, value as Any?)

    fun put(key: String, value: Int): JSONObject = put(key, value as Any?)

    fun put(key: String, value: Long): JSONObject = put(key, value as Any?)

    fun put(key: String, value: Double): JSONObject = put(key, value as Any?)

    fun put(key: String, value: Float): JSONObject = put(key, value as Any?)

    fun putOpt(key: String?, value: Any?): JSONObject {
        if (key != null && value != null) put(key, value)
        return this
    }

    fun putOnce(key: String?, value: Any?): JSONObject {
        if (key != null && value != null) {
            if (has(key)) throw JSONException("Duplicate key \"$key\"")
            put(key, value)
        }
        return this
    }

    fun accumulate(key: String, value: Any?): JSONObject {
        val existing = map[key]
        when {
            existing == null -> put(key, if (value is JSONArray) JSONArray().put(value) else value)
            existing is JSONArray -> existing.put(value)
            else -> map[key] = JSONArray().put(existing).put(value)
        }
        return this
    }

    fun append(key: String, value: Any?): JSONObject {
        when (val existing = map[key]) {
            null -> put(key, JSONArray().put(value))
            is JSONArray -> existing.put(value)
            else -> throw JSONException("JSONObject[${JsonText.quote(key)}] is not a JSONArray.")
        }
        return this
    }

    fun opt(key: String?): Any? = if (key == null) null else map[key]

    fun get(key: String): Any = map[key] ?: throw JSONException("JSONObject[${JsonText.quote(key)}] not found.")

    private fun wrong(key: String, type: String): JSONException = JSONException("JSONObject[${JsonText.quote(key)}] is not a $type.")

    fun getString(key: String): String {
        val v = get(key)
        if (v == NULL) throw wrong(key, "string")
        return v as? String ?: v.toString()
    }

    fun getBoolean(key: String): Boolean = JsonText.toBoolean(get(key)) ?: throw wrong(key, "Boolean")

    fun getDouble(key: String): Double = JsonText.toNumber(get(key)) ?: throw wrong(key, "number")

    fun getFloat(key: String): Float = getDouble(key).toFloat()

    fun getInt(key: String): Int = (get(key) as? Number)?.toInt() ?: JsonText.toNumber(map[key])?.toInt() ?: throw wrong(key, "int")

    fun getLong(key: String): Long = (get(key) as? Number)?.toLong() ?: JsonText.toNumber(map[key])?.toLong() ?: throw wrong(key, "long")

    fun getNumber(key: String): Number = (get(key) as? Number) ?: JsonText.toNumber(map[key]) ?: throw wrong(key, "number")

    fun getJSONObject(key: String): JSONObject = get(key) as? JSONObject ?: throw wrong(key, "JSONObject")

    fun getJSONArray(key: String): JSONArray = get(key) as? JSONArray ?: throw wrong(key, "JSONArray")

    fun optString(key: String): String = optString(key, "")

    fun optString(key: String, fallback: String?): String {
        val v = map[key]
        return if (v == null || v == NULL) fallback ?: "" else v as? String ?: v.toString()
    }

    fun optBoolean(key: String): Boolean = optBoolean(key, false)

    fun optBoolean(key: String, fallback: Boolean): Boolean = JsonText.toBoolean(map[key]) ?: fallback

    fun optDouble(key: String): Double = optDouble(key, Double.NaN)

    fun optDouble(key: String, fallback: Double): Double = JsonText.toNumber(map[key]) ?: fallback

    fun optFloat(key: String): Float = optFloat(key, Float.NaN)

    fun optFloat(key: String, fallback: Float): Float = JsonText.toNumber(map[key])?.toFloat() ?: fallback

    fun optInt(key: String): Int = optInt(key, 0)

    fun optInt(key: String, fallback: Int): Int {
        val v = map[key]
        return (v as? Number)?.toInt() ?: JsonText.toNumber(v)?.toInt() ?: fallback
    }

    fun optLong(key: String): Long = optLong(key, 0L)

    fun optLong(key: String, fallback: Long): Long {
        val v = map[key]
        return (v as? Number)?.toLong() ?: JsonText.toNumber(v)?.toLong() ?: fallback
    }

    fun optNumber(key: String): Number? = (map[key] as? Number) ?: JsonText.toNumber(map[key])

    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject

    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray

    fun toMap(): MutableMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        map.forEach { (k, v) -> out[k] = JsonText.plain(v) }
        return out
    }

    fun similar(other: Any?): Boolean = other is JSONObject && toString() == other.toString()

    internal fun write(sb: StringBuilder, indent: Int, level: Int) {
        if (map.isEmpty()) {
            sb.append("{}")
            return
        }
        if (map.size == 1) {
            val (k, v) = map.entries.first()
            sb.append('{').append(JsonText.quote(k)).append(':')
            if (indent > 0) sb.append(' ')
            JsonText.write(v, indent, level, sb)
            sb.append('}')
            return
        }
        val inner = level + indent
        sb.append('{')
        var first = true
        map.forEach { (k, v) ->
            if (!first) sb.append(',')
            first = false
            if (indent > 0) sb.append('\n')
            JsonText.pad(sb, inner)
            sb.append(JsonText.quote(k)).append(':')
            if (indent > 0) sb.append(' ')
            JsonText.write(v, indent, inner, sb)
        }
        if (indent > 0) sb.append('\n')
        JsonText.pad(sb, level)
        sb.append('}')
    }

    override fun toString(): String = toString(0)

    fun toString(indentFactor: Int): String = StringBuilder().also { write(it, indentFactor, 0) }.toString()

    companion object {
        val NULL: Any = JsonNull

        fun quote(text: String?): String = JsonText.quote(text)

        fun wrap(value: Any?): Any? = JsonText.wrap(value)

        fun numberToString(value: Number): String = JsonText.number(value)

        fun stringToValue(text: String): Any? = JsonText.stringToValue(text)

        fun getNames(obj: JSONObject): Array<String>? = if (obj.map.isEmpty()) null else obj.map.keys.toTypedArray()
    }
}

class JSONArray() : Iterable<Any?> {
    internal val list: ArrayList<Any?> = ArrayList()

    constructor(source: String) : this() {
        val parser = JsonParser(source)
        val value = parser.value()
        if (value !is JSONArray) throw parser.error("A JSONArray text must start with '['")
        parser.end()
        list.addAll(value.list)
    }

    constructor(source: Collection<*>?) : this() {
        source?.forEach { list.add(JsonText.wrap(it) ?: JSONObject.NULL) }
    }

    constructor(source: Iterable<*>?) : this() {
        source?.forEach { list.add(JsonText.wrap(it) ?: JSONObject.NULL) }
    }

    constructor(source: Array<*>) : this() {
        source.forEach { list.add(JsonText.wrap(it) ?: JSONObject.NULL) }
    }

    constructor(capacity: Int) : this() {
        if (capacity < 0) throw JSONException("JSONArray initial capacity cannot be negative.")
    }

    override fun iterator(): Iterator<Any?> = list.iterator()

    fun length(): Int = list.size

    fun isEmpty(): Boolean = list.isEmpty()

    fun isNull(index: Int): Boolean = opt(index).let { it == null || it == JSONObject.NULL }

    fun put(value: Any?): JSONArray {
        if (value is Double && (value.isNaN() || value.isInfinite())) throw JSONException("JSON does not allow non-finite numbers.")
        list.add(JsonText.wrap(value) ?: JSONObject.NULL)
        return this
    }

    fun put(value: Boolean): JSONArray = put(value as Any?)

    fun put(value: Int): JSONArray = put(value as Any?)

    fun put(value: Long): JSONArray = put(value as Any?)

    fun put(value: Double): JSONArray = put(value as Any?)

    fun put(value: Float): JSONArray = put(value as Any?)

    fun put(index: Int, value: Any?): JSONArray {
        if (index < 0) throw JSONException("JSONArray[$index] not found.")
        val wrapped = JsonText.wrap(value) ?: JSONObject.NULL
        if (index < list.size) list[index] = wrapped else {
            while (list.size < index) list.add(JSONObject.NULL)
            list.add(wrapped)
        }
        return this
    }

    fun put(index: Int, value: Boolean): JSONArray = put(index, value as Any?)

    fun put(index: Int, value: Int): JSONArray = put(index, value as Any?)

    fun put(index: Int, value: Long): JSONArray = put(index, value as Any?)

    fun put(index: Int, value: Double): JSONArray = put(index, value as Any?)

    fun put(index: Int, value: Float): JSONArray = put(index, value as Any?)

    fun putAll(values: Collection<*>): JSONArray {
        values.forEach { put(it) }
        return this
    }

    fun putAll(values: JSONArray): JSONArray {
        list.addAll(values.list)
        return this
    }

    fun remove(index: Int): Any? = if (index in list.indices) list.removeAt(index) else null

    fun clear() = list.clear()

    fun opt(index: Int): Any? = list.getOrNull(index)

    fun get(index: Int): Any = list.getOrNull(index) ?: throw JSONException("JSONArray[$index] not found.")

    private fun wrong(index: Int, type: String): JSONException = JSONException("JSONArray[$index] is not a $type.")

    fun getString(index: Int): String {
        val v = get(index)
        if (v == JSONObject.NULL) throw wrong(index, "String")
        return v as? String ?: v.toString()
    }

    fun getBoolean(index: Int): Boolean = JsonText.toBoolean(get(index)) ?: throw wrong(index, "Boolean")

    fun getDouble(index: Int): Double = JsonText.toNumber(get(index)) ?: throw wrong(index, "number")

    fun getFloat(index: Int): Float = getDouble(index).toFloat()

    fun getInt(index: Int): Int = (get(index) as? Number)?.toInt() ?: JsonText.toNumber(list[index])?.toInt() ?: throw wrong(index, "int")

    fun getLong(index: Int): Long = (get(index) as? Number)?.toLong() ?: JsonText.toNumber(list[index])?.toLong() ?: throw wrong(index, "long")

    fun getNumber(index: Int): Number = (get(index) as? Number) ?: JsonText.toNumber(list[index]) ?: throw wrong(index, "number")

    fun getJSONObject(index: Int): JSONObject = get(index) as? JSONObject ?: throw wrong(index, "JSONObject")

    fun getJSONArray(index: Int): JSONArray = get(index) as? JSONArray ?: throw wrong(index, "JSONArray")

    fun optString(index: Int): String = optString(index, "")

    fun optString(index: Int, fallback: String?): String {
        val v = opt(index)
        return if (v == null || v == JSONObject.NULL) fallback ?: "" else v as? String ?: v.toString()
    }

    fun optBoolean(index: Int): Boolean = optBoolean(index, false)

    fun optBoolean(index: Int, fallback: Boolean): Boolean = JsonText.toBoolean(opt(index)) ?: fallback

    fun optDouble(index: Int): Double = optDouble(index, Double.NaN)

    fun optDouble(index: Int, fallback: Double): Double = JsonText.toNumber(opt(index)) ?: fallback

    fun optFloat(index: Int): Float = optFloat(index, Float.NaN)

    fun optFloat(index: Int, fallback: Float): Float = JsonText.toNumber(opt(index))?.toFloat() ?: fallback

    fun optInt(index: Int): Int = optInt(index, 0)

    fun optInt(index: Int, fallback: Int): Int {
        val v = opt(index)
        return (v as? Number)?.toInt() ?: JsonText.toNumber(v)?.toInt() ?: fallback
    }

    fun optLong(index: Int): Long = optLong(index, 0L)

    fun optLong(index: Int, fallback: Long): Long {
        val v = opt(index)
        return (v as? Number)?.toLong() ?: JsonText.toNumber(v)?.toLong() ?: fallback
    }

    fun optNumber(index: Int): Number? = (opt(index) as? Number) ?: JsonText.toNumber(opt(index))

    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject

    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

    fun join(separator: String): String = list.joinToString(separator) { v ->
        StringBuilder().also { JsonText.write(v, 0, 0, it) }.toString()
    }

    fun toList(): MutableList<Any?> = list.mapTo(ArrayList()) { JsonText.plain(it) }

    fun toJSONObject(names: JSONArray?): JSONObject? {
        if (names == null || names.length() == 0 || list.isEmpty()) return null
        val obj = JSONObject()
        for (i in 0 until names.length()) obj.put(names.getString(i), opt(i))
        return obj
    }

    fun similar(other: Any?): Boolean = other is JSONArray && toString() == other.toString()

    internal fun write(sb: StringBuilder, indent: Int, level: Int) {
        if (list.isEmpty()) {
            sb.append("[]")
            return
        }
        if (list.size == 1) {
            sb.append('[')
            JsonText.write(list[0], indent, level, sb)
            sb.append(']')
            return
        }
        val inner = level + indent
        sb.append('[')
        var first = true
        list.forEach { v ->
            if (!first) sb.append(',')
            first = false
            if (indent > 0) sb.append('\n')
            JsonText.pad(sb, inner)
            JsonText.write(v, indent, inner, sb)
        }
        if (indent > 0) sb.append('\n')
        JsonText.pad(sb, level)
        sb.append(']')
    }

    override fun toString(): String = toString(0)

    fun toString(indentFactor: Int): String = StringBuilder().also { write(it, indentFactor, 0) }.toString()

}
