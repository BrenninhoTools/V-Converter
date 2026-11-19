package com.vconvertercrew.vconverter.editorview

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.vconvertercrew.vconverter.core.RgbaImage
import com.vconvertercrew.vconverter.generator.PackOptions
import com.vconvertercrew.vconverter.generator.PixelBox
import com.vconvertercrew.vconverter.generator.SheetAnimation
import com.vconvertercrew.vconverter.generator.SheetFrame
import com.vconvertercrew.vconverter.generator.SheetSource
import com.vconvertercrew.vconverter.generator.SpriteSheetGenerator
import com.vconvertercrew.vconverter.onlineview.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
class XmlAnimationEntry(name: String, frames: List<SheetFrame>) {
    var name by mutableStateOf(name)
    val frames = mutableStateListOf<SheetFrame>().apply { addAll(frames) }
}

class XmlExport(val name: String, val png: ByteArray, val xml: String, val summary: String) {
    val pngName: String get() = "$name.png"
    val xmlName: String get() = "$name.xml"
}

enum class SliceMode(val label: String) { AUTO("Auto Detect"), GRID("Grid"), WHOLE("Whole Image") }

private class LoadedImage(val name: String, val bytes: ByteArray, val bitmap: ImageBitmap, val pixels: RgbaImage)

@Stable
class XmlEditorState(private val scope: CoroutineScope, private val decode: (ByteArray) -> ImageBitmap?) {
    private val bitmaps = HashMap<Int, ImageBitmap>()
    private val originals = HashMap<Int, ByteArray>()
    private var nextSource = 0

    val animations = mutableStateListOf<XmlAnimationEntry>()
    var selected by mutableIntStateOf(0)
    var frame by mutableIntStateOf(0)
    var tab by mutableIntStateOf(0)
    var sheetName by mutableStateOf("spritesheet")
    var trim by mutableStateOf(true)
    var padding by mutableIntStateOf(2)
    var powerOfTwo by mutableStateOf(false)
    var dedupe by mutableStateOf(true)
    var keepSheet by mutableStateOf(false)
    var sliceMode by mutableStateOf(SliceMode.AUTO)
    var columns by mutableIntStateOf(4)
    var rows by mutableIntStateOf(1)
    var align by mutableStateOf(true)
    var fps by mutableIntStateOf(24)
    var playing by mutableStateOf(true)
    var onion by mutableStateOf(false)
    var showBounds by mutableStateOf(true)
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var busy by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var result by mutableStateOf<XmlExport?>(null)
    private var clock = 0.0
    private var last = 0L

    val current: XmlAnimationEntry? get() = animations.getOrNull(selected)
    val currentFrame: SheetFrame? get() = current?.frames?.getOrNull(frame)
    val frameCount: Int get() = animations.sumOf { it.frames.size }
    val singleSource: Boolean get() = animations.flatMap { it.frames }.map { it.source.id }.distinct().size == 1

    fun bitmap(frame: SheetFrame): ImageBitmap? = bitmaps[frame.source.id]

    fun tick(nanos: Long) {
        val entry = current
        if (last == 0L || entry == null || entry.frames.isEmpty()) {
            last = nanos
            return
        }
        clock += (nanos - last) / 1_000_000_000.0
        last = nanos
        val step = 1.0 / fps.coerceIn(1, 120)
        while (clock >= step) {
            clock -= step
            frame = (frame + 1) % entry.frames.size
        }
    }

    fun stopClock() {
        last = 0L
        clock = 0.0
    }

    fun selectAnimation(index: Int) {
        if (animations.isEmpty()) return
        selected = ((index % animations.size) + animations.size) % animations.size
        frame = 0
        stopClock()
    }

    fun step(by: Int) {
        val count = current?.frames?.size ?: return
        if (count == 0) return
        playing = false
        frame = ((frame + by) % count + count) % count
    }

    private fun load(name: String, bytes: ByteArray): LoadedImage? {
        val bitmap = runCatching { decode(bytes) }.getOrNull() ?: return null
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(argb)
        return LoadedImage(name, bytes, bitmap, RgbaImage.fromArgb(bitmap.width, bitmap.height, argb))
    }

    private fun register(image: LoadedImage): SheetSource {
        val source = SheetSource(nextSource++, image.name, image.pixels)
        bitmaps[source.id] = image.bitmap
        originals[source.id] = image.bytes
        return source
    }

    private fun merge(groups: List<SheetAnimation>) {
        groups.forEach { group ->
            val existing = animations.firstOrNull { it.name == group.name }
            if (existing != null) existing.frames.addAll(group.frames) else animations += XmlAnimationEntry(group.name, group.frames)
        }
        if (animations.isNotEmpty() && selected >= animations.size) selected = 0
        result = null
    }

    private fun imageFiles(files: List<Pair<String, ByteArray>>) = files.filter { (name, _) ->
        val ext = name.substringAfterLast('.', "").lowercase()
        ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
    }

    fun addFrames(files: List<Pair<String, ByteArray>>) {
        val images = imageFiles(files)
        if (images.isEmpty()) {
            status = "Pick .png frames, like idle0000.png, idle0001.png..."
            return
        }
        busy = true
        scope.launch {
            try {
                val loaded = withContext(ioDispatcher) { images.mapNotNull { (name, bytes) -> load(name, bytes) } }
                val frames = loaded.map { image ->
                    val source = register(image)
                    image.name to SheetFrame(source, PixelBox(0, 0, image.pixels.width, image.pixels.height))
                }
                val groups = SpriteSheetGenerator.group(frames)
                merge(groups)
                if (animations.size == groups.size) sheetName = SpriteSheetGenerator.frameName(images.first().first).first.ifBlank { sheetName }
                val skipped = images.size - loaded.size
                status = "Added ${loaded.size} frame(s) in ${groups.size} animation(s)." + if (skipped > 0) " $skipped image(s) could not be read." else ""
            } catch (e: Exception) {
                status = "Could not read the images: ${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun addSheet(name: String, bytes: ByteArray) {
        busy = true
        scope.launch {
            try {
                val image = withContext(ioDispatcher) { load(name, bytes) }
                if (image == null) {
                    status = "Could not read $name."
                    return@launch
                }
                val boxes = withContext(ioDispatcher) {
                    when (sliceMode) {
                        SliceMode.AUTO -> SpriteSheetGenerator.islands(image.pixels)
                        SliceMode.GRID -> SpriteSheetGenerator.grid(image.pixels, columns, rows)
                        SliceMode.WHOLE -> listOf(PixelBox(0, 0, image.pixels.width, image.pixels.height))
                    }
                }
                if (boxes.isEmpty()) {
                    status = "No frames were found in $name. Try the Grid mode."
                    return@launch
                }
                val source = register(image)
                val frames = if (align && sliceMode == SliceMode.AUTO) SpriteSheetGenerator.aligned(source, boxes) else boxes.map { SheetFrame(source, it) }
                val animation = SpriteSheetGenerator.frameName(name).first.ifBlank { "frames" }
                merge(listOf(SheetAnimation(animation, frames)))
                if (animations.size == 1) sheetName = animation
                status = "Found ${frames.size} frame(s) in $name."
            } catch (e: Exception) {
                status = "Could not read $name: ${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun renameAnimation(name: String) {
        val entry = current ?: return
        val clean = name.replace(Regex("[\\r\\n\"<>]"), "")
        val other = animations.firstOrNull { it !== entry && it.name == clean }
        if (other != null && clean.isNotEmpty()) {
            status = "Another animation is already called \"$clean\"."
            return
        }
        entry.name = clean
        result = null
    }

    fun moveAnimation(by: Int) {
        val target = selected + by
        if (target !in animations.indices || current == null) return
        val entry = animations.removeAt(selected)
        animations.add(target, entry)
        selected = target
        result = null
    }

    fun removeAnimation() {
        if (current == null) return
        animations.removeAt(selected)
        selected = selected.coerceAtMost(animations.size - 1).coerceAtLeast(0)
        frame = 0
        result = null
    }

    fun moveFrame(by: Int) {
        val entry = current ?: return
        val target = frame + by
        if (target !in entry.frames.indices) return
        val moved = entry.frames.removeAt(frame)
        entry.frames.add(target, moved)
        frame = target
        playing = false
        result = null
    }

    fun removeFrame() {
        val entry = current ?: return
        if (frame !in entry.frames.indices) return
        entry.frames.removeAt(frame)
        if (entry.frames.isEmpty()) removeAnimation() else frame = frame.coerceAtMost(entry.frames.size - 1)
        result = null
    }

    fun splitFrame() {
        val entry = current ?: return
        if (frame <= 0 || frame >= entry.frames.size) return
        val tail = entry.frames.subList(frame, entry.frames.size).toList()
        repeat(tail.size) { entry.frames.removeAt(entry.frames.size - 1) }
        var name = "${entry.name} 2"
        var n = 2
        while (animations.any { it.name == name }) name = "${entry.name} ${++n}"
        animations.add(selected + 1, XmlAnimationEntry(name, tail))
        selected += 1
        frame = 0
        result = null
    }

    fun clear() {
        animations.clear()
        bitmaps.clear()
        originals.clear()
        selected = 0
        frame = 0
        result = null
        status = null
    }

    fun generate(onDone: (XmlExport) -> Unit = {}) {
        if (busy) return
        val empty = animations.firstOrNull { it.name.isBlank() }
        if (empty != null) {
            status = "Every animation needs a name."
            return
        }
        val groups = animations.filter { it.frames.isNotEmpty() }.map { SheetAnimation(it.name, it.frames.toList()) }
        if (groups.isEmpty()) {
            status = "Add some frames first."
            return
        }
        val name = sheetName.trim().ifEmpty { "spritesheet" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val keep = keepSheet && singleSource
        val options = PackOptions("$name.png", trim, padding, powerOfTwo, dedupe)
        busy = true
        status = "Generating..."
        scope.launch {
            try {
                val export = withContext(ioDispatcher) {
                    if (keep) {
                        val packed = SpriteSheetGenerator.describe(groups, "$name.png", trim)
                        val source = groups.first().frames.first().source
                        XmlExport(name, originals[source.id] ?: ByteArray(0), packed.xml, "${packed.frames.size} frames on the original ${source.image.width}x${source.image.height} image")
                    } else {
                        val packed = SpriteSheetGenerator.pack(groups, options)
                        val sheet = packed.image!!
                        val png = packed.png()!!
                        val reused = if (packed.reused > 0) ", ${packed.reused} repeated frame(s) reused" else ""
                        XmlExport(name, png, packed.xml, "${packed.frames.size} frames on a ${sheet.width}x${sheet.height} sheet$reused")
                    }
                }
                result = export
                status = "Done: ${export.summary}."
                onDone(export)
            } catch (e: Exception) {
                status = e.message ?: "Could not generate the sheet."
            } finally {
                busy = false
            }
        }
    }
}
