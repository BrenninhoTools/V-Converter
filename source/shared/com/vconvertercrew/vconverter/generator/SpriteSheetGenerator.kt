package com.vconvertercrew.vconverter.generator

import com.vconvertercrew.vconverter.core.PngWriter
import com.vconvertercrew.vconverter.core.RgbaImage
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

data class PixelBox(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val area: Int get() = width * height

    fun intersects(other: PixelBox, gap: Int = 0): Boolean =
        x - gap < other.right && other.x - gap < right && y - gap < other.bottom && other.y - gap < bottom

    fun union(other: PixelBox): PixelBox {
        val left = minOf(x, other.x)
        val top = minOf(y, other.y)
        return PixelBox(left, top, max(right, other.right) - left, max(bottom, other.bottom) - top)
    }
}

class SheetSource(val id: Int, val name: String, val image: RgbaImage)

data class SheetFrame(
    val source: SheetSource,
    val region: PixelBox,
    val canvasWidth: Int = region.width,
    val canvasHeight: Int = region.height,
    val canvasX: Int = 0,
    val canvasY: Int = 0
)

class SheetAnimation(val name: String, val frames: List<SheetFrame>)

data class PackOptions(
    val imagePath: String = "sheet.png",
    val trim: Boolean = true,
    val padding: Int = 2,
    val powerOfTwo: Boolean = false,
    val dedupe: Boolean = true,
    val maxSize: Int = 8192
)

data class PackedFrame(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val frameX: Int,
    val frameY: Int,
    val frameWidth: Int,
    val frameHeight: Int
) {
    val trimmed: Boolean get() = frameX != 0 || frameY != 0 || frameWidth != width || frameHeight != height
}

class PackResult(val image: RgbaImage?, val frames: List<PackedFrame>, val xml: String, val reused: Int) {
    fun png(): ByteArray? = image?.let(PngWriter::encode)
}

class PackException(message: String) : Exception(message)

object SpriteSheetGenerator {
    private val numbered = Regex("^(.*?)[\\s_.-]*(\\d+)$")

    fun frameName(fileName: String): Pair<String, Int?> {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val match = numbered.matchEntire(base) ?: return base.trim() to null
        val prefix = match.groupValues[1].trim()
        return (prefix.ifEmpty { base.trim() }) to match.groupValues[2].toIntOrNull()
    }

    fun group(files: List<Pair<String, SheetFrame>>): List<SheetAnimation> {
        val named = files.map { (fileName, frame) -> Triple(frameName(fileName), fileName, frame) }
        val order = LinkedHashMap<String, MutableList<Triple<Pair<String, Int?>, String, SheetFrame>>>()
        named.forEach { order.getOrPut(it.first.first) { ArrayList() } += it }
        return order.map { (name, list) ->
            SheetAnimation(name, list.sortedWith(compareBy({ it.first.second ?: Int.MAX_VALUE }, { it.second })).map { it.third })
        }
    }

    fun opaqueBounds(image: RgbaImage, area: PixelBox = PixelBox(0, 0, image.width, image.height)): PixelBox? {
        var left = area.right
        var top = area.bottom
        var right = area.x - 1
        var bottom = area.y - 1
        for (y in area.y until area.bottom) {
            val row = y * image.width
            for (x in area.x until area.right) {
                if (image.pixels[(row + x) * 4 + 3].toInt() != 0) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left) return null
        return PixelBox(left, top, right - left + 1, bottom - top + 1)
    }

    fun islands(image: RgbaImage, gap: Int = 2, minPixels: Int = 4): List<PixelBox> {
        val w = image.width
        val h = image.height
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val boxes = ArrayList<PixelBox>()
        for (start in 0 until w * h) {
            if (seen[start] || image.pixels[start * 4 + 3].toInt() == 0) continue
            var size = 0
            stack[size++] = start
            seen[start] = true
            var left = w
            var top = h
            var right = -1
            var bottom = -1
            var count = 0
            while (size > 0) {
                val p = stack[--size]
                val px = p % w
                val py = p / w
                count++
                if (px < left) left = px
                if (px > right) right = px
                if (py < top) top = py
                if (py > bottom) bottom = py
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx
                    val ny = py + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (!seen[q] && image.pixels[q * 4 + 3].toInt() != 0) {
                        seen[q] = true
                        stack[size++] = q
                    }
                }
            }
            if (count >= minPixels) boxes += PixelBox(left, top, right - left + 1, bottom - top + 1)
        }
        val merged = ArrayList(boxes)
        var changed = true
        while (changed) {
            changed = false
            loop@ for (i in merged.indices) for (j in i + 1 until merged.size) {
                if (merged[i].intersects(merged[j], gap)) {
                    merged[i] = merged[i].union(merged[j])
                    merged.removeAt(j)
                    changed = true
                    break@loop
                }
            }
        }
        return readingOrder(merged)
    }

    fun readingOrder(boxes: List<PixelBox>): List<PixelBox> {
        val rows = ArrayList<MutableList<PixelBox>>()
        boxes.sortedBy { it.y }.forEach { box ->
            val row = rows.firstOrNull { line -> line.any { box.y < it.bottom && it.y < box.bottom && (box.y + box.height / 2) in it.y..it.bottom } }
            if (row != null) row += box else rows += mutableListOf(box)
        }
        return rows.sortedBy { line -> line.minOf { it.y } }.flatMap { line -> line.sortedBy { it.x } }
    }

    fun grid(image: RgbaImage, columns: Int, rows: Int, skipEmpty: Boolean = true): List<PixelBox> {
        if (columns <= 0 || rows <= 0) return emptyList()
        val cellW = image.width / columns
        val cellH = image.height / rows
        if (cellW <= 0 || cellH <= 0) return emptyList()
        val cells = ArrayList<PixelBox>()
        for (row in 0 until rows) for (column in 0 until columns) {
            val cell = PixelBox(column * cellW, row * cellH, cellW, cellH)
            if (!skipEmpty || opaqueBounds(image, cell) != null) cells += cell
        }
        return cells
    }

    fun aligned(source: SheetSource, boxes: List<PixelBox>): List<SheetFrame> {
        if (boxes.isEmpty()) return emptyList()
        val width = boxes.maxOf { it.width }
        val height = boxes.maxOf { it.height }
        return boxes.map { box -> SheetFrame(source, box, width, height, (width - box.width) / 2, height - box.height) }
    }

    fun frameNames(animations: List<SheetAnimation>): List<Pair<String, SheetFrame>> =
        animations.flatMap { animation -> animation.frames.mapIndexed { index, frame -> "${animation.name}${index.toString().padStart(4, '0')}" to frame } }

    private class Piece(val key: Int, val source: SheetSource, val box: PixelBox) {
        var x = 0
        var y = 0
    }

    private fun content(frame: SheetFrame, trim: Boolean): PixelBox {
        if (!trim) return frame.region
        return opaqueBounds(frame.source.image, frame.region) ?: PixelBox(frame.region.x, frame.region.y, 1, 1)
    }

    private fun samePixels(a: Piece, b: Piece): Boolean {
        if (a.box.width != b.box.width || a.box.height != b.box.height) return false
        val ia = a.source.image
        val ib = b.source.image
        for (row in 0 until a.box.height) {
            val oa = ((a.box.y + row) * ia.width + a.box.x) * 4
            val ob = ((b.box.y + row) * ib.width + b.box.x) * 4
            for (k in 0 until a.box.width * 4) if (ia.pixels[oa + k] != ib.pixels[ob + k]) return false
        }
        return true
    }

    private fun hash(source: SheetSource, box: PixelBox): Int {
        var h = box.width * 31 + box.height
        val image = source.image
        val stepY = max(1, box.height / 16)
        val stepX = max(1, box.width / 16)
        var y = box.y
        while (y < box.bottom) {
            var x = box.x
            while (x < box.right) {
                h = h * 31 + image.argb(x, y)
                x += stepX
            }
            y += stepY
        }
        return h
    }

    private fun nextPow2(v: Int): Int {
        var p = 1
        while (p < v) p = p shl 1
        return p
    }

    private class MaxRects(val width: Int, val height: Int) {
        val free = ArrayList<PixelBox>().apply { add(PixelBox(0, 0, width, height)) }

        fun insert(w: Int, h: Int): PixelBox? {
            var best: PixelBox? = null
            var bestShort = Int.MAX_VALUE
            var bestLong = Int.MAX_VALUE
            for (rect in free) {
                if (rect.width >= w && rect.height >= h) {
                    val leftW = rect.width - w
                    val leftH = rect.height - h
                    val short = minOf(leftW, leftH)
                    val long = max(leftW, leftH)
                    if (short < bestShort || (short == bestShort && long < bestLong)) {
                        best = PixelBox(rect.x, rect.y, w, h)
                        bestShort = short
                        bestLong = long
                    }
                }
            }
            val placed = best ?: return null
            val next = ArrayList<PixelBox>(free.size + 4)
            for (rect in free) {
                if (!rect.intersects(placed)) {
                    next += rect
                    continue
                }
                if (placed.x > rect.x) next += PixelBox(rect.x, rect.y, placed.x - rect.x, rect.height)
                if (placed.right < rect.right) next += PixelBox(placed.right, rect.y, rect.right - placed.right, rect.height)
                if (placed.y > rect.y) next += PixelBox(rect.x, rect.y, rect.width, placed.y - rect.y)
                if (placed.bottom < rect.bottom) next += PixelBox(rect.x, placed.bottom, rect.width, rect.bottom - placed.bottom)
            }
            free.clear()
            for (i in next.indices) {
                val a = next[i]
                var contained = false
                for (j in next.indices) {
                    if (i == j) continue
                    val b = next[j]
                    if (a.x >= b.x && a.y >= b.y && a.right <= b.right && a.bottom <= b.bottom && (a != b || j < i)) {
                        contained = true
                        break
                    }
                }
                if (!contained) free += a
            }
            return placed
        }
    }

    private fun place(pieces: List<Piece>, width: Int, height: Int, padding: Int): Boolean {
        val bin = MaxRects(width + padding, height + padding)
        for (piece in pieces) {
            val spot = bin.insert(piece.box.width + padding, piece.box.height + padding) ?: return false
            piece.x = spot.x
            piece.y = spot.y
        }
        return true
    }

    fun pack(animations: List<SheetAnimation>, options: PackOptions = PackOptions()): PackResult {
        val named = frameNames(animations)
        if (named.isEmpty()) throw PackException("Add some frames first.")
        val padding = options.padding.coerceIn(0, 64)
        val pieces = ArrayList<Piece>()
        val byHash = HashMap<Int, MutableList<Piece>>()
        val pieceOf = ArrayList<Piece>(named.size)
        val boxes = ArrayList<PixelBox>(named.size)
        var reused = 0
        named.forEach { (_, frame) ->
            val box = content(frame, options.trim)
            boxes += box
            val candidate = Piece(pieces.size, frame.source, box)
            val existing = if (options.dedupe) byHash[hash(frame.source, box)]?.firstOrNull { samePixels(it, candidate) } else null
            if (existing != null) {
                reused++
                pieceOf += existing
            } else {
                pieces += candidate
                if (options.dedupe) byHash.getOrPut(hash(frame.source, box)) { ArrayList() } += candidate
                pieceOf += candidate
            }
        }
        val sorted = pieces.sortedWith(compareByDescending<Piece> { max(it.box.width, it.box.height) }.thenByDescending { it.box.area })
        val widest = pieces.maxOf { it.box.width } + padding
        val tallest = pieces.maxOf { it.box.height } + padding
        val area = pieces.sumOf { (it.box.width + padding).toLong() * (it.box.height + padding) }
        var side = max(max(widest, tallest), ceil(sqrt(area.toDouble())).toInt())
        var width = 0
        var height = 0
        var fitted = false
        while (!fitted) {
            val tries = if (options.powerOfTwo) {
                val p = nextPow2(side)
                listOf(p to p, p * 2 to p, p to p * 2)
            } else listOf(side to side, side * 5 / 4 to side, side to side * 5 / 4)
            for ((w, h) in tries) {
                val tw = max(w, widest)
                val th = max(h, tallest)
                if (tw > options.maxSize || th > options.maxSize) continue
                if (place(sorted, tw, th, padding)) {
                    width = tw
                    height = th
                    fitted = true
                    break
                }
            }
            if (!fitted) {
                if (side >= options.maxSize) throw PackException("The frames don't fit in a ${options.maxSize}x${options.maxSize} sheet. Turn on trimming or split the animations into more sheets.")
                side = minOf(options.maxSize, side + max(16, side / 10))
            }
        }
        if (!options.powerOfTwo) {
            width = pieces.maxOf { it.x + it.box.width }
            height = pieces.maxOf { it.y + it.box.height }
        }
        val sheet = RgbaImage.blank(width, height)
        pieces.forEach { sheet.blit(it.source.image, it.box.x, it.box.y, it.box.width, it.box.height, it.x, it.y) }
        val frames = named.mapIndexed { index, (name, frame) ->
            val piece = pieceOf[index]
            val box = boxes[index]
            PackedFrame(
                name, piece.x, piece.y, box.width, box.height,
                -(frame.canvasX + box.x - frame.region.x), -(frame.canvasY + box.y - frame.region.y),
                frame.canvasWidth, frame.canvasHeight
            )
        }
        return PackResult(sheet, frames, xml(options.imagePath, frames), reused)
    }

    fun describe(animations: List<SheetAnimation>, imagePath: String, trim: Boolean = true): PackResult {
        val named = frameNames(animations)
        if (named.isEmpty()) throw PackException("Add some frames first.")
        if (named.map { it.second.source.id }.distinct().size != 1) throw PackException("Keeping the original sheet works only when every frame comes from the same image.")
        val frames = named.map { (name, frame) ->
            val box = content(frame, trim)
            PackedFrame(
                name, box.x, box.y, box.width, box.height,
                -(frame.canvasX + box.x - frame.region.x), -(frame.canvasY + box.y - frame.region.y),
                frame.canvasWidth, frame.canvasHeight
            )
        }
        return PackResult(null, frames, xml(imagePath, frames), 0)
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun xml(imagePath: String, frames: List<PackedFrame>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<TextureAtlas imagePath=\"").append(escape(imagePath)).append("\">\n")
        frames.forEach { f ->
            append("\t<SubTexture name=\"").append(escape(f.name)).append('"')
            append(" x=\"").append(f.x).append("\" y=\"").append(f.y).append('"')
            append(" width=\"").append(f.width).append("\" height=\"").append(f.height).append('"')
            if (f.trimmed) {
                append(" frameX=\"").append(f.frameX).append("\" frameY=\"").append(f.frameY).append('"')
                append(" frameWidth=\"").append(f.frameWidth).append("\" frameHeight=\"").append(f.frameHeight).append('"')
            }
            append("/>\n")
        }
        append("</TextureAtlas>\n")
    }
}
