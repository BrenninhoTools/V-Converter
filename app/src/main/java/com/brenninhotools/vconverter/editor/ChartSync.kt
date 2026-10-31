package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject
import kotlin.math.abs

sealed interface ChartChange {
    data class Ops(val ops: List<JSONObject>) : ChartChange
    data class Snapshot(val snapshot: JSONObject) : ChartChange
}

object ChartSync {
    fun snapshot(document: ChartDocument): JSONObject = JSONObject()
        .put("id", document.id)
        .put("variation", document.variation)
        .put("metadata", document.metadataJson())
        .put("chart", document.chartJson())

    fun fromSnapshot(snapshot: JSONObject): ChartDocument =
        ChartDocument.load(snapshot.optString("id", "song"), snapshot.optString("variation"), snapshot.getString("metadata"), snapshot.getString("chart"))

    private fun noteArray(note: EditorNote): JSONArray = JSONArray().put(note.time.toJsonNumber()).put(note.data).put(note.length.toJsonNumber()).put(note.kind)

    private fun note(array: JSONArray): EditorNote = EditorNote(array.optDouble(0, 0.0), array.optInt(1, 0), array.optDouble(2, 0.0).coerceAtLeast(0.0), array.optString(3, ""))

    private fun eventsArray(events: List<EditorEvent>): JSONArray = JSONArray().also { array ->
        events.forEach { e -> array.put(JSONArray().put(e.time.toJsonNumber()).put(e.kind).put(e.value ?: JSONObject.NULL)) }
    }

    private fun sameEvents(a: List<EditorEvent>, b: List<EditorEvent>): Boolean =
        a.size == b.size && a.indices.all { i -> a[i] === b[i] || (a[i].time == b[i].time && a[i].kind == b[i].kind && a[i].value?.toString() == b[i].value?.toString()) }

    fun diff(old: ChartDocument, new: ChartDocument): ChartChange? {
        if (old === new) return null
        val notesOnly = old.difficulties.keys.toList() == new.difficulties.keys.toList() &&
            old.copy(
                difficulties = LinkedHashMap(old.difficulties.mapValues { (name, chart) -> chart.copy(notes = new.difficulties.getValue(name).notes) }),
                events = new.events
            ) == new
        if (!notesOnly) return ChartChange.Snapshot(snapshot(new))
        val ops = ArrayList<JSONObject>()
        new.difficulties.forEach { (name, chart) ->
            val before = old.difficulties.getValue(name).notes
            if (before === chart.notes || before == chart.notes) return@forEach
            val previous = before.groupingBy { it }.eachCount().toMutableMap()
            val added = ArrayList<EditorNote>()
            chart.notes.forEach { n ->
                val left = previous[n] ?: 0
                if (left > 0) previous[n] = left - 1 else added += n
            }
            val removed = previous.flatMap { (n, count) -> List(count) { n } }
            if (added.isEmpty() && removed.isEmpty()) return@forEach
            ops += JSONObject().put("t", "notes").put("d", name)
                .put("add", JSONArray().also { a -> added.forEach { a.put(noteArray(it)) } })
                .put("remove", JSONArray().also { a -> removed.forEach { a.put(noteArray(it)) } })
        }
        if (!sameEvents(old.events, new.events)) ops += JSONObject().put("t", "events").put("events", eventsArray(new.events))
        return if (ops.isEmpty()) null else ChartChange.Ops(ops)
    }

    private fun matches(a: EditorNote, b: EditorNote) = a.data == b.data && abs(a.time - b.time) < 0.5

    fun apply(document: ChartDocument, op: JSONObject): ChartDocument = when (op.optString("t")) {
        "notes" -> {
            val name = op.optString("d")
            val chart = document.difficulties[name]
            if (chart == null) document else {
                val notes = chart.notes.toMutableList()
                op.optJSONArray("remove")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optJSONArray(it) }.map(::note).forEach { gone ->
                        val index = notes.indexOfFirst { it == gone }.takeIf { it >= 0 } ?: notes.indexOfFirst { matches(it, gone) }
                        if (index >= 0) notes.removeAt(index)
                    }
                }
                op.optJSONArray("add")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optJSONArray(it) }.map(::note).forEach { added ->
                        notes.removeAll { matches(it, added) }
                        notes += added
                    }
                }
                val sorted = notes.sortedWith(compareBy({ it.time }, { it.data }))
                document.copy(difficulties = LinkedHashMap(document.difficulties).also { it[name] = chart.copy(notes = sorted) })
            }
        }
        "events" -> {
            val array = op.optJSONArray("events") ?: JSONArray()
            val events = (0 until array.length()).mapNotNull { array.optJSONArray(it) }.map { e ->
                EditorEvent(e.optDouble(0, 0.0), e.optString(1), e.opt(2).takeIf { it != JSONObject.NULL })
            }.sortedBy { it.time }
            document.copy(events = events)
        }
        else -> document
    }
}
