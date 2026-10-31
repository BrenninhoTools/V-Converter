package com.brenninhotools.vconverter.playview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.brenninhotools.vconverter.editorview.NoteSkin
import com.brenninhotools.vconverter.editorview.StageSelection
import com.brenninhotools.vconverter.editorview.drawFrame
import com.brenninhotools.vconverter.editorview.drawProp
import com.brenninhotools.vconverter.editorview.sprite
import com.brenninhotools.vconverter.playtest.CharacterRole
import com.brenninhotools.vconverter.playtest.PlayNote
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.brenninhotools.vconverter.core.fmt

private const val GAME_W = 1280f
private const val GAME_H = 720f
private const val NOTE_SIZE = 112f
private const val SPACING = 112f
private const val OPPONENT_X = 92f
private const val PLAYER_X = 732f
private val laneColors = listOf(Color(0xFFC24B99), Color(0xFF00FFFF), Color(0xFF12FA05), Color(0xFFF9393F))
private val roleColors = mapOf(CharacterRole.PLAYER to Color(0xFF31B0D1), CharacterRole.OPPONENT to Color(0xFFAF66CE), CharacterRole.GIRLFRIEND to Color(0xFFA5004D))

private class Screen(val k: Float, val ox: Float, val oy: Float) {
    fun x(v: Float) = ox + v * k
    fun y(v: Float) = oy + v * k
}

@Composable
fun PlayTestView(state: PlayTestState, modifier: Modifier = Modifier, skin: NoteSkin? = null, touchLanes: Boolean = true) {
    val measurer = rememberTextMeasurer()
    LaunchedEffect(state.phase) {
        while (state.phase == PlayPhase.PLAYING) withFrameNanos { state.tick(it) }
    }
    Box(
        modifier.pointerInput(touchLanes) {
            if (!touchLanes) return@pointerInput
            val lanes = HashMap<PointerId, Int>()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        val lane = ((change.position.x / size.width.coerceAtLeast(1)) * 4).toInt().coerceIn(0, 3)
                        if (change.pressed && !change.previousPressed) {
                            lanes[change.id] = lane
                            state.press(lane)
                        } else if (!change.pressed && change.previousPressed) {
                            lanes.remove(change.id)?.let { state.release(it) }
                        }
                        change.consume()
                    }
                }
            }
        }
    ) {
        Canvas(Modifier.matchParentSize()) {
            state.frames
            state.phase
            val k = min(size.width / GAME_W, size.height / GAME_H)
            val screen = Screen(k, (size.width - GAME_W * k) / 2f, (size.height - GAME_H * k) / 2f)
            drawRect(Color.Black)
            clipRect(screen.ox, screen.oy, screen.ox + GAME_W * k, screen.oy + GAME_H * k) {
                drawScene(state, screen)
                drawHud(state, screen, skin, measurer)
            }
        }
    }
}

private fun DrawScope.drawScene(state: PlayTestState, screen: Screen) {
    if (!state.showStage) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1B1530), Color(0xFF0B0A10))), Offset(screen.ox, screen.oy), Size(GAME_W * screen.k, GAME_H * screen.k))
        return
    }
    drawRect(Color(0xFF15131F), Offset(screen.ox, screen.oy), Size(GAME_W * screen.k, GAME_H * screen.k))
    val stage = state.stage
    val zoom = (stage.document.cameraZoom.takeIf { it > 0 } ?: 1.0).toFloat()
    val camX = state.camX.toFloat()
    val camY = state.camY.toFloat()
    fun sx(x: Double, scroll: Double): Float = screen.x(GAME_W / 2f + ((x.toFloat() - camX * scroll.toFloat() + GAME_W / 2f * scroll.toFloat() - GAME_W / 2f)) * zoom)
    fun sy(y: Double, scroll: Double): Float = screen.y(GAME_H / 2f + ((y.toFloat() - camY * scroll.toFloat() + GAME_H / 2f * scroll.toFloat() - GAME_H / 2f)) * zoom)
    stage.drawOrder().forEach { item ->
        when (item) {
            is StageSelection.Prop -> {
                val prop = stage.document.props[item.index]
                val rect = stage.rectOf(prop)
                drawProp(stage, prop, sx(rect.left, rect.scroll), sy(rect.top, rect.scrollY), (rect.width * zoom * screen.k).toFloat(), (rect.height * zoom * screen.k).toFloat())
            }
            is StageSelection.Spot -> {
                val role = state.roleOf(item.slot)
                val actor = state.actors[role] ?: return@forEach
                val spot = stage.document.spot(item.slot)
                if (spot.alpha <= 0.0) return@forEach
                val origin = Offset(sx(spot.x, spot.scroll), sy(spot.y, spot.scroll))
                val scale = zoom * screen.k * spot.scale.toFloat()
                val doc = actor.document
                val sprite = actor.sprite
                val frame = actor.frame(state.time)
                val anim = actor.current()
                if (doc != null && sprite != null && frame != null && anim != null) {
                    drawFrame(sprite, doc, anim, frame, actor.reference, origin, scale, spot.alpha.toFloat(), null)
                } else {
                    val color = roleColors.getValue(role)
                    val w = 300f * scale
                    val h = (actor.height.toFloat()) * scale
                    drawRect(color.copy(alpha = 0.3f), Offset(origin.x - w / 2f, origin.y - h), Size(w, h))
                    drawRect(color, Offset(origin.x - w / 2f, origin.y - h), Size(w, h), style = Stroke(2f))
                }
            }
        }
    }
}

private fun DrawScope.drawHud(state: PlayTestState, screen: Screen, skin: NoteSkin?, measurer: TextMeasurer) {
    val game = state.engine
    val doc = state.document
    val time = state.time
    val down = state.downscroll
    val strumY = if (down) GAME_H - 150f else 50f
    val speed = (doc?.difficulties?.get(state.difficulty)?.speed ?: 1.0).toFloat().coerceAtLeast(0.1f)
    val pxPerMs = 0.45f * speed
    fun laneX(lane: Int, player: Boolean) = (if (player) PLAYER_X else OPPONENT_X) + lane * SPACING + NOTE_SIZE / 2f
    fun noteY(t: Double) = strumY + NOTE_SIZE / 2f + ((t - time).toFloat() * pxPerMs) * (if (down) -1f else 1f)

    for (side in 0..1) {
        val player = side == 1
        for (lane in 0..3) {
            val center = Offset(screen.x(laneX(lane, player)), screen.y(strumY + NOTE_SIZE / 2f))
            val size = NOTE_SIZE * screen.k
            val confirmed = time - state.lastConfirm[lane + if (player) 4 else 0] < 140
            val held = player && game?.isHeld(lane) == true
            val receptor = skin?.receptors?.getOrNull(lane)
            if (skin != null && receptor != null) {
                sprite(skin, receptor, center, size * (if (held && !confirmed) 0.9f else 1f), alpha = if (held && !confirmed) 0.7f else 1f)
                if (confirmed) skin.notes.getOrNull(lane)?.let { sprite(skin, it, center, size * 1.05f) }
            } else {
                drawCircle(if (confirmed) laneColors[lane] else Color.Gray.copy(alpha = 0.5f), radius = size * 0.42f, center = center, style = Stroke(4f * screen.k))
            }
        }
    }

    if (game != null) {
        val visibleMs = (GAME_H / pxPerMs + 400).toDouble()
        game.notes.forEach { note ->
            if (note.time - time > visibleMs || note.end - time < -400) return@forEach
            drawNote(note, state, screen, skin, ::laneX, ::noteY, down)
        }
    }

    val text = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = (20f * screen.k).toSp(), textAlign = TextAlign.Center)
    val barY = if (down) 72f else GAME_H * 0.89f
    val barW = 600f
    val barX = (GAME_W - barW) / 2f
    val health = ((game?.health ?: 1.0) / 2.0).toFloat().coerceIn(0f, 1f)
    drawRoundRect(Color.Black, Offset(screen.x(barX - 4), screen.y(barY - 4)), Size((barW + 8) * screen.k, 26f * screen.k), CornerRadius(6f * screen.k))
    drawRect(Color(0xFFFF0000), Offset(screen.x(barX), screen.y(barY)), Size(barW * (1 - health) * screen.k, 18f * screen.k))
    drawRect(Color(0xFF66FF33), Offset(screen.x(barX + barW * (1 - health)), screen.y(barY)), Size(barW * health * screen.k, 18f * screen.k))
    if (game != null) {
        val accuracy = fmt("%.2f", game.accuracy * 100)
        val line = "Score: ${game.score}  |  Misses: ${game.misses}  |  Accuracy: $accuracy%  |  ${game.rating}"
        val layout = measurer.measure(line, text)
        drawText(layout, topLeft = Offset(screen.x(GAME_W / 2f) - layout.size.width / 2f, screen.y(barY + 30f)))
    }
    val total = state.duration
    if (total > 0) {
        val progress = (time / total).toFloat().coerceIn(0f, 1f)
        val y = if (down) GAME_H - 26f else 12f
        drawRect(Color.Black.copy(alpha = 0.6f), Offset(screen.x(440f), screen.y(y)), Size(400f * screen.k, 10f * screen.k))
        drawRect(Color.White, Offset(screen.x(440f), screen.y(y)), Size(400f * progress * screen.k, 10f * screen.k))
        val clock = measurer.measure("${PlayTestState.formatMs(time)} / ${PlayTestState.formatMs(total)}", text.copy(fontSize = (14f * screen.k).toSp()))
        drawText(clock, topLeft = Offset(screen.x(GAME_W / 2f) - clock.size.width / 2f, screen.y(y + 12f)))
    }
    state.popups.lastOrNull()?.let { popup ->
        val age = (time - popup.time).toFloat()
        val alpha = (1f - age / 700f).coerceIn(0f, 1f)
        val color = when (popup.text) {
            "MISS", "DROP" -> Color(0xFFFF4F4F)
            "SICK!!" -> Color(0xFF7CF7FF)
            else -> Color.White
        }
        val style = text.copy(color = color.copy(alpha = alpha), fontSize = (44f * screen.k).toSp(), fontWeight = FontWeight.Bold)
        val layout = measurer.measure(popup.text, style)
        val y = GAME_H * 0.42f - age * 0.03f
        drawText(layout, topLeft = Offset(screen.x(PLAYER_X - 160f) - layout.size.width / 2f, screen.y(y)))
        val combo = game?.combo ?: 0
        if (combo >= 2 && popup.text != "MISS") {
            val c = measurer.measure("$combo", style.copy(fontSize = (32f * screen.k).toSp()))
            drawText(c, topLeft = Offset(screen.x(PLAYER_X - 160f) - c.size.width / 2f, screen.y(y + 54f)))
        }
    }
    if (state.botplay) {
        val layout = measurer.measure("BOTPLAY", text.copy(fontSize = (32f * screen.k).toSp(), fontWeight = FontWeight.Bold))
        drawText(layout, topLeft = Offset(screen.x(GAME_W / 2f) - layout.size.width / 2f, screen.y(if (down) GAME_H - 110f else 90f)))
    }
    if (state.phase == PlayPhase.PAUSED || state.phase == PlayPhase.LOADING) {
        drawRect(Color.Black.copy(alpha = 0.55f), Offset(screen.ox, screen.oy), Size(GAME_W * screen.k, GAME_H * screen.k))
        val layout = measurer.measure(if (state.phase == PlayPhase.PAUSED) "PAUSED" else "LOADING…", text.copy(fontSize = (56f * screen.k).toSp(), fontWeight = FontWeight.Bold))
        drawText(layout, topLeft = Offset(screen.x(GAME_W / 2f) - layout.size.width / 2f, screen.y(GAME_H / 2f) - layout.size.height / 2f))
    }
}

private fun DrawScope.drawNote(
    note: PlayNote,
    state: PlayTestState,
    screen: Screen,
    skin: NoteSkin?,
    laneX: (Int, Boolean) -> Float,
    noteY: (Double) -> Float,
    down: Boolean
) {
    val time = state.time
    val x = screen.x(laneX(note.lane, note.player))
    val size = NOTE_SIZE * screen.k
    val alpha = if (note.missed) 0.35f else 1f
    if (note.length > 0.0 && !note.dropped && !(note.hit && note.holdDone)) {
        val from = if (note.hit) max(note.time, time) else note.time
        val y1 = screen.y(noteY(from))
        val y2 = screen.y(noteY(note.end))
        val top = min(y1, y2)
        val bottom = max(y1, y2)
        val piece = skin?.holdPieces?.getOrNull(note.lane)
        val end = skin?.holdEnds?.getOrNull(note.lane)
        val head = skin?.notes?.getOrNull(note.lane)
        if (skin != null && piece != null && end != null && head != null && bottom - top > 1f) {
            val scale = size / max(head.size.width, head.size.height)
            val width = (piece.size.width * scale).roundToInt().coerceAtLeast(1)
            val endHeight = (end.size.height * scale).coerceAtMost(bottom - top)
            val bodyTop = if (down) top + endHeight else top
            val bodyBottom = if (down) bottom else bottom - endHeight
            if (bodyBottom > bodyTop) {
                drawImage(
                    skin.image,
                    srcOffset = IntOffset(piece.offset.x, piece.offset.y + 1),
                    srcSize = IntSize(piece.size.width, (piece.size.height - 2).coerceAtLeast(1)),
                    dstOffset = IntOffset((x - width / 2f).roundToInt(), bodyTop.roundToInt()),
                    dstSize = IntSize(width, (bodyBottom - bodyTop).roundToInt().coerceAtLeast(1)),
                    alpha = 0.85f * alpha
                )
            }
            drawImage(
                skin.image,
                srcOffset = end.offset,
                srcSize = end.size,
                dstOffset = IntOffset((x - width / 2f).roundToInt(), (if (down) top else bottom - endHeight).roundToInt()),
                dstSize = IntSize(width, endHeight.roundToInt().coerceAtLeast(1)),
                alpha = 0.85f * alpha
            )
        } else {
            drawRect(laneColors[note.lane].copy(alpha = 0.7f * alpha), Offset(x - size * 0.12f, top), Size(size * 0.24f, bottom - top))
        }
    }
    if (note.hit) return
    val center = Offset(x, screen.y(noteY(note.time)))
    val head = skin?.notes?.getOrNull(note.lane)
    if (skin != null && head != null) sprite(skin, head, center, size, alpha = alpha)
    else drawCircle(laneColors[note.lane].copy(alpha = alpha), radius = size * 0.4f, center = center)
}
