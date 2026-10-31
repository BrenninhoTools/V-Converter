package com.brenninhotools.vconverter.editorview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.editor.AtlasFrame
import com.brenninhotools.vconverter.editor.MenuAnim
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editor.StageAssets
import com.brenninhotools.vconverter.editor.WeekDocument
import com.brenninhotools.vconverter.editor.WeekProp
import com.brenninhotools.vconverter.editor.WeekSlot
import com.brenninhotools.vconverter.editor.WeekSong
import com.brenninhotools.vconverter.core.roundLong

class WeekRect(val left: Double, val top: Double, val width: Double, val height: Double) {
    fun contains(x: Double, y: Double) = x >= left && x <= left + width && y >= top && y <= top + height
}

class WeekFrame(val image: StageImage, val frame: AtlasFrame?, val anim: MenuAnim?)

class WeekEditorState(initial: WeekDocument) {
    private val history = EditHistory(initial)

    var document by mutableStateOf(initial)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var revision by mutableIntStateOf(0)
        private set
    var assets by mutableStateOf<Map<String, StageAsset>>(emptyMap())
        private set
    var images by mutableStateOf<Map<String, StageImage>>(emptyMap())
        private set
    var selected by mutableStateOf<WeekSlot?>(null)
    var selectedSong by mutableIntStateOf(-1)
    var difficulty by mutableIntStateOf(1)
    var playing by mutableStateOf(true)
    var time by mutableDoubleStateOf(0.0)
        private set
    private var confirmAt = -1.0
    private var lastTick = -1L
    private var dragging = false

    var onLocalChange: (() -> Unit)? = null
    private var announced: WeekDocument? = null
    private var remote = false

    fun loadRemote(next: WeekDocument) {
        remote = true
        try {
            document = history.replace(next)
            refresh()
        } finally {
            remote = false
        }
    }

    private fun refresh() {
        canUndo = history.canUndo
        canRedo = history.canRedo
        if (selectedSong !in document.songs.indices) selectedSong = -1
        difficulty = difficulty.coerceIn(0, (document.difficulties.size - 1).coerceAtLeast(0))
        revision++
        if (document !== announced) {
            announced = document
            if (!remote) onLocalChange?.invoke()
        }
    }

    fun edit(change: (WeekDocument) -> WeekDocument) {
        val next = change(document)
        if (next == document) return
        document = history.push(next)
        refresh()
    }

    private fun liveEdit(change: (WeekDocument) -> WeekDocument) {
        document = history.replace(change(document))
        refresh()
    }

    fun load(next: WeekDocument, newAssets: Map<String, StageAsset>, decoded: Map<String, StageImage>) {
        document = history.reset(next)
        assets = newAssets
        images = decoded
        selected = null
        selectedSong = -1
        difficulty = next.difficulties.indexOf("normal").takeIf { it >= 0 } ?: 0
        refresh()
    }

    fun undo() {
        document = history.undo()
        refresh()
    }

    fun redo() {
        document = history.redo()
        refresh()
    }

    fun addAsset(key: String, asset: StageAsset, image: StageImage?) {
        assets = assets + (key.lowercase() to asset)
        if (image != null) images = images + (key.lowercase() to image)
        revision++
    }

    fun imageFor(path: String): StageImage? {
        if (path.isBlank()) return null
        val k = StageAssets.key(path)
        return images[k] ?: images.entries.firstOrNull { it.key.substringAfterLast('/') == k.substringAfterLast('/') }?.value
    }

    fun tick(frameNanos: Long) {
        val delta = if (lastTick < 0) 0.0 else (frameNanos - lastTick) / 1_000_000_000.0
        lastTick = frameNanos
        time += delta.coerceAtMost(0.25)
        if (confirmAt >= 0 && time - confirmAt > 2.0) confirmAt = -1.0
    }

    fun stopTicking() {
        lastTick = -1L
    }

    fun confirm() {
        confirmAt = time
        playing = true
    }

    private fun framesOf(image: StageImage, anim: MenuAnim): List<AtlasFrame> = image.atlas?.framesFor(anim.prefix, anim.indices) ?: emptyList()

    fun frameFor(slot: WeekSlot): WeekFrame? {
        val prop = document.prop(slot) ?: return null
        val image = imageFor(prop.assetPath) ?: return null
        if (image.atlas == null || prop.animations.isEmpty()) return WeekFrame(image, null, null)
        val confirm = prop.anim("confirm")?.takeIf { confirmAt >= 0 }
        if (confirm != null) {
            val frames = framesOf(image, confirm)
            if (frames.isNotEmpty()) {
                val index = ((time - confirmAt) * confirm.frameRate).toInt()
                return WeekFrame(image, frames[if (confirm.looped) index % frames.size else index.coerceAtMost(frames.size - 1)], confirm)
            }
        }
        val left = prop.anim("danceLeft")
        val right = prop.anim("danceRight")
        if (left != null && right != null) {
            val l = framesOf(image, left)
            val r = framesOf(image, right)
            val all = l + r
            if (all.isNotEmpty()) {
                val index = if (playing) (time * left.frameRate.coerceAtLeast(1)).toInt() % all.size else 0
                return WeekFrame(image, all[index], if (index < l.size) left else right)
            }
        }
        val idle = prop.anim(prop.idleName) ?: prop.animations.first()
        val frames = framesOf(image, idle)
        if (frames.isEmpty()) return WeekFrame(image, image.atlas.frames.firstOrNull(), idle)
        val index = if (playing) (time * idle.frameRate.coerceAtLeast(1)).toInt() % frames.size else 0
        return WeekFrame(image, frames[index], idle)
    }

    fun rectOf(slot: WeekSlot): WeekRect? {
        val prop = document.prop(slot) ?: return null
        val s = prop.scale * (if (prop.isPixel) 6.0 else 1.0)
        val frame = frameFor(slot)
        val (fw, fh) = when {
            frame?.frame != null -> frame.frame.frameWidth.toDouble() to frame.frame.frameHeight.toDouble()
            frame != null -> frame.image.width.toDouble() to frame.image.height.toDouble()
            else -> placeholder(slot)
        }
        val animX = frame?.anim?.offsetX ?: 0.0
        val animY = frame?.anim?.offsetY ?: 0.0
        val x = prop.offsetX + slot.ordinal * WeekDocument.SLOT_WIDTH - animX + fw / 2 * (1 - s)
        val y = prop.offsetY - animY + fh / 2 * (1 - s)
        return WeekRect(x, y, fw * s, fh * s)
    }

    fun placeholder(slot: WeekSlot): Pair<Double, Double> = when (slot) {
        WeekSlot.DAD -> 280.0 to 360.0
        WeekSlot.BF -> 260.0 to 300.0
        WeekSlot.GF -> 300.0 to 340.0
    }

    fun hitTest(x: Double, y: Double): WeekSlot? = WeekSlot.entries.reversed().firstOrNull { rectOf(it)?.contains(x, y) == true }

    fun tap(x: Double, y: Double) {
        selected = hitTest(x, y)
    }

    fun beginDrag(x: Double, y: Double) {
        val hit = hitTest(x, y)
        if (hit != null) selected = hit
        dragging = hit != null
        if (dragging) history.checkpoint()
    }

    fun dragBy(dx: Double, dy: Double) {
        val slot = selected ?: return
        if (!dragging) return
        liveEdit { doc -> doc.prop(slot)?.let { doc.withProp(slot, it.copy(offsetX = it.offsetX + dx, offsetY = it.offsetY + dy)) } ?: doc }
    }

    fun endDrag() {
        if (!dragging) return
        dragging = false
        val slot = selected ?: return
        liveEdit { doc -> doc.prop(slot)?.let { doc.withProp(slot, it.copy(offsetX = (it.offsetX).roundLong().toDouble(), offsetY = (it.offsetY).roundLong().toDouble())) } ?: doc }
    }

    fun nudge(dx: Double, dy: Double) {
        val slot = selected ?: return
        edit { doc -> doc.prop(slot)?.let { doc.withProp(slot, it.copy(offsetX = it.offsetX + dx, offsetY = it.offsetY + dy)) } ?: doc }
    }

    fun updateProp(slot: WeekSlot, change: (WeekProp) -> WeekProp) = edit { doc -> doc.prop(slot)?.let { doc.withProp(slot, change(it)) } ?: doc }

    fun addSong(id: String) {
        val clean = id.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (clean.isEmpty() || document.songs.any { it.id == clean }) return
        edit { it.copy(songs = it.songs + WeekSong(clean, id.trim())) }
        selectedSong = document.songs.size - 1
    }

    fun removeSong(index: Int) {
        if (index !in document.songs.indices) return
        edit { doc -> doc.copy(songs = doc.songs.filterIndexed { i, _ -> i != index }) }
        selectedSong = (index - 1).coerceAtLeast(if (document.songs.isEmpty()) -1 else 0)
    }

    fun moveSong(index: Int, direction: Int) {
        val target = index + direction
        if (target !in document.songs.indices) return
        edit { it.moveSong(index, direction) }
        selectedSong = target
    }

    fun currentDifficulty(): String = document.difficulties.getOrNull(difficulty) ?: "normal"

    fun changeDifficulty(direction: Int) {
        val size = document.difficulties.size
        if (size == 0) return
        difficulty = ((difficulty + direction) % size + size) % size
        revision++
    }
}
