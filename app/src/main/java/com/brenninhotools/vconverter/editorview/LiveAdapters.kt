package com.brenninhotools.vconverter.editorview

import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.editor.CharacterDocument
import com.brenninhotools.vconverter.editor.ChartChange
import com.brenninhotools.vconverter.editor.ChartSync
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editor.StageDocument
import com.brenninhotools.vconverter.editor.WeekDocument
import org.json.JSONObject

sealed interface LiveChange {
    data class Ops(val ops: List<JSONObject>) : LiveChange
    data class Snapshot(val snapshot: JSONObject) : LiveChange
}

enum class LiveKind(val key: String, val label: String) {
    CHART("chart", "Chart Editor"),
    CHARACTER("character", "Character Editor"),
    STAGE("stage", "Stage Editor"),
    WEEK("week", "Week Editor");

    companion object {
        fun of(key: String): LiveKind = entries.firstOrNull { it.key == key } ?: CHART
    }
}

interface LiveAdapter {
    val kind: LiveKind
    fun title(): String
    fun snapshot(): JSONObject
    fun load(snapshot: JSONObject, ops: List<JSONObject>)
    fun applyOp(op: JSONObject) {}
    fun watch(onChange: (LiveChange) -> Unit)
    fun sharedFiles(): Map<String, () -> ByteArray?> = emptyMap()
    fun receiveFile(name: String, bytes: ByteArray): (() -> Unit)? = null
}

class ChartLiveAdapter(
    private val editor: ChartEditorState,
    private val audio: () -> List<EditorAudio> = { emptyList() },
    private val onAudio: (EditorAudio) -> Unit = {}
) : LiveAdapter {
    override val kind = LiveKind.CHART

    override fun title(): String = editor.document.meta.songName.ifBlank { editor.document.id }

    override fun snapshot(): JSONObject = ChartSync.snapshot(editor.document)

    override fun load(snapshot: JSONObject, ops: List<JSONObject>) {
        editor.loadRemote(ops.fold(ChartSync.fromSnapshot(snapshot)) { doc, op -> ChartSync.apply(doc, op) })
    }

    override fun applyOp(op: JSONObject) = editor.applyRemote { ChartSync.apply(it, op) }

    override fun watch(onChange: (LiveChange) -> Unit) {
        editor.onLocalChange = { old, new ->
            when (val change = ChartSync.diff(old, new)) {
                is ChartChange.Ops -> onChange(LiveChange.Ops(change.ops))
                is ChartChange.Snapshot -> onChange(LiveChange.Snapshot(change.snapshot))
                null -> Unit
            }
        }
    }

    override fun sharedFiles(): Map<String, () -> ByteArray?> = audio().associate { a -> "audio/${a.name}" to { a.bytes } }

    override fun receiveFile(name: String, bytes: ByteArray): (() -> Unit)? {
        if (!name.startsWith("audio/")) return null
        val item = EditorAudio(name.removePrefix("audio/"), bytes)
        return { onAudio(item) }
    }
}

class CharacterLiveAdapter(
    private val editor: CharacterEditorState,
    private val sheet: () -> Pair<ByteArray?, String?>,
    private val onSheet: (ByteArray?, String?) -> Unit
) : LiveAdapter {
    override val kind = LiveKind.CHARACTER
    private var png: ByteArray? = null
    private var xml: String? = null

    override fun title(): String = editor.document.name.ifBlank { editor.document.id }

    override fun snapshot(): JSONObject = JSONObject()
        .put("id", editor.document.id)
        .put("slot", editor.document.slot.name)
        .put("json", editor.document.toJson())

    override fun load(snapshot: JSONObject, ops: List<JSONObject>) {
        val slot = runCatching { CharacterSlot.valueOf(snapshot.optString("slot")) }.getOrNull()
        editor.loadRemote(CharacterDocument.fromVSlice(snapshot.optString("id", "character"), snapshot.getString("json"), slot))
    }

    override fun watch(onChange: (LiveChange) -> Unit) {
        editor.onLocalChange = { onChange(LiveChange.Snapshot(snapshot())) }
    }

    override fun sharedFiles(): Map<String, () -> ByteArray?> {
        val (image, text) = sheet()
        return buildMap {
            if (image != null) put("sprite/sheet-${image.size}.png") { image }
            if (text != null) put("sprite/sheet-${text.length}.xml") { text.toByteArray() }
        }
    }

    override fun receiveFile(name: String, bytes: ByteArray): (() -> Unit)? {
        if (!name.startsWith("sprite/")) return null
        if (name.endsWith(".png")) png = bytes else if (name.endsWith(".xml")) xml = bytes.toString(Charsets.UTF_8)
        val image = png
        val text = xml
        return { onSheet(image, text) }
    }
}

private fun assetFiles(assets: Map<String, StageAsset>): Map<String, () -> ByteArray?> = buildMap {
    assets.forEach { (key, asset) ->
        put("images/$key.png") { asset.png }
        asset.xml?.let { text -> put("images/$key.xml") { text.toByteArray() } }
    }
}

private class AssetInbox {
    private val png = HashMap<String, ByteArray>()
    private val xml = HashMap<String, String>()

    fun accept(name: String, bytes: ByteArray): Pair<String, StageAsset>? {
        if (!name.startsWith("images/")) return null
        val key = name.removePrefix("images/").substringBeforeLast('.')
        when {
            name.endsWith(".png") -> png[key] = bytes
            name.endsWith(".xml") -> xml[key] = bytes.toString(Charsets.UTF_8)
            else -> return null
        }
        val image = png[key] ?: return null
        return key to StageAsset(image, xml[key])
    }
}

class StageLiveAdapter(private val editor: StageEditorState, private val decode: (StageAsset) -> StageImage?) : LiveAdapter {
    override val kind = LiveKind.STAGE
    private val inbox = AssetInbox()

    override fun title(): String = editor.document.name.ifBlank { editor.document.id }

    override fun snapshot(): JSONObject = JSONObject().put("id", editor.document.id).put("json", editor.document.toJson())

    override fun load(snapshot: JSONObject, ops: List<JSONObject>) {
        editor.loadRemote(StageDocument.fromVSlice(snapshot.optString("id", "stage"), snapshot.getString("json")))
    }

    override fun watch(onChange: (LiveChange) -> Unit) {
        editor.onLocalChange = { onChange(LiveChange.Snapshot(snapshot())) }
    }

    override fun sharedFiles(): Map<String, () -> ByteArray?> = assetFiles(editor.assets)

    override fun receiveFile(name: String, bytes: ByteArray): (() -> Unit)? {
        val (key, asset) = inbox.accept(name, bytes) ?: return null
        val image = decode(asset)
        return { editor.addAsset(key, asset, image) }
    }
}

class WeekLiveAdapter(private val editor: WeekEditorState, private val decode: (StageAsset) -> StageImage?) : LiveAdapter {
    override val kind = LiveKind.WEEK
    private val inbox = AssetInbox()

    override fun title(): String = editor.document.storyName

    override fun snapshot(): JSONObject = editor.document.toSnapshot()

    override fun load(snapshot: JSONObject, ops: List<JSONObject>) {
        editor.loadRemote(WeekDocument.fromSnapshot(snapshot))
    }

    override fun watch(onChange: (LiveChange) -> Unit) {
        editor.onLocalChange = { onChange(LiveChange.Snapshot(snapshot())) }
    }

    override fun sharedFiles(): Map<String, () -> ByteArray?> = assetFiles(editor.assets)

    override fun receiveFile(name: String, bytes: ByteArray): (() -> Unit)? {
        val (key, asset) = inbox.accept(name, bytes) ?: return null
        val image = decode(asset)
        return { editor.addAsset(key, asset, image) }
    }
}
