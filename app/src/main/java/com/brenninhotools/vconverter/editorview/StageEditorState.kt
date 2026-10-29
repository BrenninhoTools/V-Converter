package com.brenninhotools.vconverter.editorview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.brenninhotools.vconverter.editor.AtlasFrame
import com.brenninhotools.vconverter.editor.SpriteAtlas
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editor.StageAssets
import com.brenninhotools.vconverter.editor.StageDocument
import com.brenninhotools.vconverter.editor.StageProp
import com.brenninhotools.vconverter.editor.StageSlot

class StageImage(val image: ImageBitmap, val width: Int, val height: Int, val atlas: SpriteAtlas? = null, val sheetScale: Float = 1f)

sealed interface StageSelection {
    data class Prop(val index: Int) : StageSelection
    data class Spot(val slot: StageSlot) : StageSelection
}

class StageRect(val left: Double, val top: Double, val width: Double, val height: Double, val scroll: Double, val scrollY: Double) {
    fun contains(x: Double, y: Double) = x >= left && x <= left + width && y >= top && y <= top + height
}

class StageEditorState(initial: StageDocument) {
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
    var selection by mutableStateOf<StageSelection?>(null)
    var zoom by mutableFloatStateOf(0.45f)
    var camX by mutableFloatStateOf(640f)
    var camY by mutableFloatStateOf(400f)
    var parallax by mutableStateOf(true)
    var showCamera by mutableStateOf(true)

    private var draggingItem = false

    private fun refresh() {
        canUndo = history.canUndo
        canRedo = history.canRedo
        val sel = selection
        if (sel is StageSelection.Prop && sel.index !in document.props.indices) selection = null
        revision++
    }

    fun edit(change: (StageDocument) -> StageDocument) {
        val next = change(document)
        if (next == document) return
        document = history.push(next)
        refresh()
    }

    private fun liveEdit(change: (StageDocument) -> StageDocument) {
        document = history.replace(change(document))
        refresh()
    }

    fun load(next: StageDocument, newAssets: Map<String, StageAsset>, decoded: Map<String, StageImage>) {
        document = history.reset(next)
        assets = newAssets
        images = decoded
        selection = null
        resetView()
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
        assets = assets + (key to asset)
        if (image != null) images = images + (key to image)
        revision++
    }

    fun imageFor(prop: StageProp): StageImage? {
        if (prop.isSolid) return null
        val k = StageAssets.key(prop.assetPath)
        return images[k] ?: images.entries.firstOrNull { it.key.substringAfterLast('/') == k.substringAfterLast('/') }?.value
    }

    fun frameFor(prop: StageProp, image: StageImage?): AtlasFrame? =
        if (prop.prefix.isEmpty()) null else image?.atlas?.framesFor(prop.prefix)?.firstOrNull()

    fun rectOf(prop: StageProp): StageRect {
        val image = imageFor(prop)
        val frame = frameFor(prop, image)
        val (w, h) = when {
            prop.isSolid -> 1.0 to 1.0
            frame != null -> frame.frameWidth.toDouble() to frame.frameHeight.toDouble()
            image != null -> image.width.toDouble() to image.height.toDouble()
            else -> 200.0 to 200.0
        }
        return StageRect(prop.x, prop.y, w * prop.scaleX, h * prop.scaleY, prop.scrollX, prop.scrollY)
    }

    fun rectOf(slot: StageSlot): StageRect {
        val spot = document.spot(slot)
        val (w, h) = if (slot == StageSlot.GIRLFRIEND) 380.0 to 440.0 else 360.0 to 400.0
        val sw = w * spot.scale
        val sh = h * spot.scale
        return StageRect(spot.x - sw / 2, spot.y - sh, sw, sh, spot.scroll, spot.scroll)
    }

    fun drawOrder(): List<StageSelection> {
        val items = document.props.mapIndexed { i, p -> Triple(p.zIndex, i, StageSelection.Prop(i) as StageSelection) } +
            StageSlot.entries.mapIndexed { i, s -> Triple(document.spot(s).zIndex, 10_000 + i, StageSelection.Spot(s) as StageSelection) }
        return items.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
    }

    fun rectOf(item: StageSelection): StageRect = when (item) {
        is StageSelection.Prop -> rectOf(document.props[item.index])
        is StageSelection.Spot -> rectOf(item.slot)
    }

    fun toWorld(screenX: Float, screenY: Float, width: Float, height: Float, scroll: Double, scrollY: Double): Pair<Double, Double> {
        val sx = if (parallax) scroll else 1.0
        val sy = if (parallax) scrollY else 1.0
        return ((screenX - width / 2f) / zoom + camX * sx) to ((screenY - height / 2f) / zoom + camY * sy)
    }

    fun hitTest(screenX: Float, screenY: Float, width: Float, height: Float): StageSelection? =
        drawOrder().asReversed().firstOrNull { item ->
            val rect = rectOf(item)
            val (wx, wy) = toWorld(screenX, screenY, width, height, rect.scroll, rect.scrollY)
            rect.contains(wx, wy)
        }

    fun tap(screenX: Float, screenY: Float, width: Float, height: Float) {
        selection = hitTest(screenX, screenY, width, height)
    }

    fun beginDrag(screenX: Float, screenY: Float, width: Float, height: Float) {
        val hit = hitTest(screenX, screenY, width, height)
        draggingItem = hit != null && hit == selection
        if (draggingItem) history.checkpoint()
    }

    fun dragBy(dx: Float, dy: Float) {
        if (!draggingItem) {
            camX -= dx / zoom
            camY -= dy / zoom
            return
        }
        val wx = dx / zoom
        val wy = dy / zoom
        when (val sel = selection) {
            is StageSelection.Prop -> liveEdit { doc -> doc.replaceProp(sel.index, doc.props[sel.index].let { it.copy(x = it.x + wx, y = it.y + wy) }) }
            is StageSelection.Spot -> liveEdit { doc -> doc.withSpot(sel.slot, doc.spot(sel.slot).let { it.copy(x = it.x + wx, y = it.y + wy) }) }
            null -> Unit
        }
    }

    fun endDrag() {
        if (!draggingItem) return
        draggingItem = false
        when (val sel = selection) {
            is StageSelection.Prop -> liveEdit { doc -> doc.replaceProp(sel.index, doc.props[sel.index].let { it.copy(x = Math.round(it.x).toDouble(), y = Math.round(it.y).toDouble()) }) }
            is StageSelection.Spot -> liveEdit { doc -> doc.withSpot(sel.slot, doc.spot(sel.slot).let { it.copy(x = Math.round(it.x * 2) / 2.0, y = Math.round(it.y * 2) / 2.0) }) }
            null -> Unit
        }
    }

    fun nudge(dx: Double, dy: Double) {
        when (val sel = selection) {
            is StageSelection.Prop -> edit { doc -> doc.replaceProp(sel.index, doc.props[sel.index].let { it.copy(x = it.x + dx, y = it.y + dy) }) }
            is StageSelection.Spot -> edit { doc -> doc.withSpot(sel.slot, doc.spot(sel.slot).let { it.copy(x = it.x + dx, y = it.y + dy) }) }
            null -> Unit
        }
    }

    fun addProp(prop: StageProp, autoLayer: Boolean = true) {
        val top = document.props.maxOfOrNull { it.zIndex } ?: 0
        val z = if (!autoLayer) prop.zIndex else minOf(top + 10, document.spot(StageSlot.GIRLFRIEND).zIndex - 10).coerceAtLeast(0)
        val placed = prop.copy(name = document.uniqueName(prop.name), zIndex = z)
        edit { it.addProp(placed) }
        selection = StageSelection.Prop(document.props.size - 1)
    }

    fun duplicateSelected() {
        val sel = selection as? StageSelection.Prop ?: return
        val prop = document.props[sel.index]
        edit { it.addProp(prop.copy(name = it.uniqueName(prop.name), x = prop.x + 40, y = prop.y + 40, zIndex = prop.zIndex + 1)) }
        selection = StageSelection.Prop(document.props.size - 1)
    }

    fun deleteSelected() {
        val sel = selection as? StageSelection.Prop ?: return
        edit { it.removeProp(sel.index) }
        selection = null
    }

    fun moveLayer(direction: Int) {
        val sel = selection ?: return
        val order = drawOrder()
        val index = order.indexOf(sel)
        val other = order.getOrNull(index + direction) ?: return
        fun z(item: StageSelection) = when (item) {
            is StageSelection.Prop -> document.props[item.index].zIndex
            is StageSelection.Spot -> document.spot(item.slot).zIndex
        }
        val mine = z(sel)
        val theirs = z(other)
        val newMine = if (mine == theirs) theirs + direction else theirs
        val newTheirs = mine
        fun set(doc: StageDocument, item: StageSelection, value: Int): StageDocument = when (item) {
            is StageSelection.Prop -> doc.replaceProp(item.index, doc.props[item.index].copy(zIndex = value))
            is StageSelection.Spot -> doc.withSpot(item.slot, doc.spot(item.slot).copy(zIndex = value))
        }
        edit { set(set(it, sel, newMine), other, newTheirs) }
    }

    fun focus(item: StageSelection) {
        selection = item
        val rect = rectOf(item)
        camX = (rect.left + rect.width / 2).toFloat()
        camY = (rect.top + rect.height / 2).toFloat()
    }

    fun resetView() {
        val bf = document.spot(StageSlot.BOYFRIEND)
        val dad = document.spot(StageSlot.DAD)
        camX = ((bf.x + dad.x) / 2).toFloat()
        camY = ((bf.y + dad.y) / 2 - 250).toFloat()
        zoom = 0.45f
    }
}
