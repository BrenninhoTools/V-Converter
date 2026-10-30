package com.brenninhotools.vconverter.editorview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.editor.AtlasFrame
import com.brenninhotools.vconverter.editor.CharacterAnim
import com.brenninhotools.vconverter.editor.CharacterDocument
import com.brenninhotools.vconverter.editor.SpriteAtlas
import androidx.compose.ui.graphics.ImageBitmap

class EditHistory<T>(initial: T, private val limit: Int = 200) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    var current: T = initial
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(next: T): T {
        if (next == current) return current
        checkpoint()
        current = next
        return current
    }

    fun checkpoint() {
        undoStack.addLast(current)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    fun replace(next: T): T {
        current = next
        return current
    }

    fun undo(): T {
        val previous = undoStack.removeLastOrNull() ?: return current
        redoStack.addLast(current)
        current = previous
        return current
    }

    fun redo(): T {
        val next = redoStack.removeLastOrNull() ?: return current
        undoStack.addLast(current)
        current = next
        return current
    }

    fun reset(value: T): T {
        undoStack.clear()
        redoStack.clear()
        current = value
        return current
    }
}

class CharacterSprite(val image: ImageBitmap, val atlas: SpriteAtlas, val sheetScale: Float = 1f)

enum class DragMode(val label: String) { OFFSET("Offset"), CAMERA("Camera"), VIEW("View") }

class CharacterEditorState(initial: CharacterDocument) {
    private val history = EditHistory(initial)

    var document by mutableStateOf(initial)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var revision by mutableIntStateOf(0)
        private set
    var sprite by mutableStateOf<CharacterSprite?>(null)
    var selected by mutableIntStateOf(0)
        private set
    var frame by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var ghost by mutableStateOf(true)
    var ghostAnim by mutableStateOf<String?>(null)
    var zoom by mutableFloatStateOf(0.5f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var dragMode by mutableStateOf(DragMode.OFFSET)
    var showCamera by mutableStateOf(true)
    var showBackground by mutableStateOf(true)
    var stageZoom by mutableFloatStateOf(1f)

    fun cameraFocus(): Pair<Float, Float> {
        val s = (document.scale.takeIf { it > 0 } ?: 1.0).toFloat()
        val height = (referenceFrame?.frameHeight ?: 400) * s
        return document.cameraX.toFloat() to (-height / 2f + document.cameraY.toFloat())
    }

    fun gameCamera(): Pair<Float, Float> {
        val focus = cameraFocus()
        val slot = document.slot
        return (focus.first + slot.cameraBaseX.toFloat()) to (focus.second + slot.cameraBaseY.toFloat())
    }

    private var elapsed = 0.0
    private var lastTick = -1L

    val animation: CharacterAnim? get() = document.animations.getOrNull(selected)

    fun framesOf(anim: CharacterAnim?): List<AtlasFrame> = anim?.let { sprite?.atlas?.framesFor(it.prefix, it.indices) } ?: emptyList()

    val referenceFrame: AtlasFrame?
        get() {
            val doc = document
            val reference = doc.anim(doc.startingAnimation) ?: doc.anim("idle") ?: doc.anim("danceRight") ?: doc.animations.firstOrNull()
            return framesOf(reference).firstOrNull()
        }

    val ghostAnimation: CharacterAnim?
        get() = ghostAnim?.let { document.anim(it) } ?: document.anim(document.startingAnimation) ?: document.anim("idle") ?: document.anim("danceRight")

    private fun refresh() {
        canUndo = history.canUndo
        canRedo = history.canRedo
        if (selected >= document.animations.size) selected = (document.animations.size - 1).coerceAtLeast(0)
        val count = framesOf(animation).size
        if (frame >= count) frame = (count - 1).coerceAtLeast(0)
        revision++
    }

    fun edit(change: (CharacterDocument) -> CharacterDocument) {
        val next = change(document)
        if (next == document) return
        document = history.push(next)
        refresh()
    }

    fun beginLiveEdit() {
        history.checkpoint()
    }

    fun liveEdit(change: (CharacterDocument) -> CharacterDocument) {
        document = history.replace(change(document))
        refresh()
    }

    fun load(next: CharacterDocument) {
        document = history.reset(next)
        selected = next.animations.indexOfFirst { it.name == next.startingAnimation }.coerceAtLeast(0)
        frame = 0
        playing = false
        ghostAnim = null
        panX = 0f
        panY = 0f
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

    fun select(index: Int) {
        if (index !in document.animations.indices) return
        selected = index
        frame = 0
        elapsed = 0.0
        lastTick = -1L
        playing = true
    }

    fun togglePlay() {
        if (playing) {
            playing = false
        } else {
            if (frame >= framesOf(animation).size - 1) frame = 0
            elapsed = frame.toDouble()
            lastTick = -1L
            playing = true
        }
    }

    fun step(by: Int) {
        val count = framesOf(animation).size
        if (count == 0) return
        playing = false
        frame = (frame + by).mod(count)
    }

    fun tick(frameNanos: Long) {
        if (!playing) return
        val anim = animation
        val count = framesOf(anim).size
        if (anim == null || count == 0) {
            playing = false
            return
        }
        val delta = if (lastTick < 0) 0.0 else (frameNanos - lastTick) / 1_000_000_000.0
        lastTick = frameNanos
        elapsed += delta * anim.frameRate.coerceAtLeast(1)
        val next = elapsed.toInt()
        frame = when {
            next < count -> next
            anim.looped -> next % count
            else -> {
                playing = false
                count - 1
            }
        }
    }

    fun nudgeOffset(dx: Double, dy: Double) {
        val anim = animation ?: return
        edit { it.replaceAnim(selected, anim.copy(offsetX = anim.offsetX + dx, offsetY = anim.offsetY + dy)) }
    }

    fun dragBy(dxScreen: Float, dyScreen: Float) {
        when (dragMode) {
            DragMode.VIEW -> {
                panX += dxScreen
                panY += dyScreen
            }
            DragMode.OFFSET -> {
                val anim = animation ?: return
                val s = document.scale.takeIf { it > 0 } ?: 1.0
                liveEdit { it.replaceAnim(selected, anim.copy(offsetX = anim.offsetX - dxScreen / (zoom * s), offsetY = anim.offsetY - dyScreen / (zoom * s))) }
            }
            DragMode.CAMERA -> liveEdit { it.copy(cameraX = it.cameraX + dxScreen / zoom, cameraY = it.cameraY + dyScreen / zoom) }
        }
    }

    fun endDrag() {
        if (dragMode == DragMode.VIEW) return
        val anim = animation
        if (anim != null && dragMode == DragMode.OFFSET) {
            liveEdit { it.replaceAnim(selected, anim.copy(offsetX = Math.round(anim.offsetX).toDouble(), offsetY = Math.round(anim.offsetY).toDouble())) }
        } else if (dragMode == DragMode.CAMERA) {
            liveEdit { it.copy(cameraX = Math.round(it.cameraX).toDouble(), cameraY = Math.round(it.cameraY).toDouble()) }
        }
    }

    fun addAnimation(prefix: String) {
        val base = com.brenninhotools.vconverter.generator.CharacterGenerator.guess(prefix) ?: "anim"
        var name = base
        var n = 2
        while (document.anim(name) != null) name = base + n++
        edit { it.addAnim(CharacterAnim(name, prefix, 24, name == "idle" || name.startsWith("dance"))) }
        select(document.animations.size - 1)
    }

    fun removeSelected() {
        if (document.animations.isEmpty()) return
        edit { it.removeAnim(selected) }
        frame = 0
    }

    fun moveSelected(by: Int) {
        val target = selected + by
        if (target !in document.animations.indices) return
        edit { it.moveAnim(selected, by) }
        selected = target
    }

    fun resetView() {
        panX = 0f
        panY = 0f
        zoom = 0.5f
    }
}
