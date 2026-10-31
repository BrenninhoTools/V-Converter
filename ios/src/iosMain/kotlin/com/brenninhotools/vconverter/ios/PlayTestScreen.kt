package com.brenninhotools.vconverter.ios

import com.brenninhotools.vconverter.platform.File
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.SpriteAtlas
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editorview.CharacterSprite
import com.brenninhotools.vconverter.editorview.NoteSkin
import com.brenninhotools.vconverter.editorview.SongPlayer
import com.brenninhotools.vconverter.editorview.StageImage
import com.brenninhotools.vconverter.io.Archives
import com.brenninhotools.vconverter.playview.PlayPhase
import com.brenninhotools.vconverter.playview.PlayTestControls
import com.brenninhotools.vconverter.playview.PlayTestIssuesPanel
import com.brenninhotools.vconverter.playview.PlayTestPlatform
import com.brenninhotools.vconverter.playview.PlayTestResultsPanel
import com.brenninhotools.vconverter.playview.PlayTestSetupPanel
import com.brenninhotools.vconverter.playview.PlayTestState
import com.brenninhotools.vconverter.playview.PlayTestView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object IosPlayTestPlatform : PlayTestPlatform {
    override fun player(audio: List<EditorAudio>): SongPlayer? = if (audio.isEmpty()) null else IosSongPlayer.create(audio).first

    override fun sprite(png: ByteArray, xml: String): CharacterSprite? {
        val atlas = SpriteAtlas.parse(xml).takeIf { it.frames.isNotEmpty() } ?: return null
        val image = org.jetbrains.skia.Image.makeFromEncoded(png)
        return CharacterSprite(image.toComposeImageBitmap(), atlas)
    }

    override fun image(asset: StageAsset): StageImage? {
        val image = org.jetbrains.skia.Image.makeFromEncoded(asset.png)
        return StageImage(image.toComposeImageBitmap(), image.width, image.height, asset.xml?.let { SpriteAtlas.parse(it) })
    }
}

class IosPlayTest(private val scope: CoroutineScope) {
    val state = PlayTestState(scope, IosPlayTestPlatform)
    var reading by androidx.compose.runtime.mutableStateOf(false)
        private set
    private val held = HashSet<Int>()

    private fun readAll(sources: List<File>): List<Pair<String, ByteArray>> {
        val result = ArrayList<Pair<String, ByteArray>>()
        sources.forEach { source ->
            when {
                source.isDirectory -> source.walkTopDown().filter { it.isFile }.forEach { f ->
                    result += (source.name + "/" + f.relativeTo(source).invariantSeparatorsPath) to f.readBytes()
                }
                else -> {
                    val kind = Archives.kind(source)
                    if (kind == null) {
                        result += source.name to source.readBytes()
                    } else {
                        val archive = Archives.extract(source, source.name, kind, Workspace.cache)
                        try {
                            archive.files.paths.forEach { path -> result += path to File(archive.directory, path).readBytes() }
                        } finally {
                            archive.delete()
                        }
                    }
                }
            }
        }
        return result
    }

    fun open(sources: List<File>) {
        if (sources.isEmpty() || reading) return
        reading = true
        scope.launch {
            try {
                val files = withContext(Dispatchers.IO) { readAll(sources) }
                state.open(files, if (sources.size == 1) sources.first().name else "${sources.size} files")
            } finally {
                reading = false
            }
        }
    }

    fun key(event: KeyEvent): Boolean {
        val lane = when (event.key) {
            Key.DirectionLeft, Key.D -> 0
            Key.DirectionDown, Key.F -> 1
            Key.DirectionUp, Key.J -> 2
            Key.DirectionRight, Key.K -> 3
            else -> -1
        }
        val playing = state.phase == PlayPhase.PLAYING || state.phase == PlayPhase.PAUSED
        if (lane >= 0 && playing) {
            if (event.type == KeyEventType.KeyDown && held.add(lane)) state.press(lane)
            if (event.type == KeyEventType.KeyUp && held.remove(lane)) state.release(lane)
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        when (event.key) {
            Key.Spacebar, Key.Enter -> when (state.phase) {
                PlayPhase.PLAYING -> state.pause()
                PlayPhase.PAUSED -> state.resume()
                PlayPhase.SETUP, PlayPhase.RESULTS -> state.start()
                PlayPhase.LOADING -> Unit
            }
            Key.Escape -> if (playing) state.stop() else state.backToSetup()
            Key.B -> state.toggleBotplay()
            else -> return false
        }
        return true
    }
}

@Composable
fun PlayTestScreen(state: AppState) {
    val model = state.playtest
    val play = model.state
    val accent = Palette.Up
    val skin by produceState<NoteSkin?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val image = Resources.image(NoteSkin.IMAGE)
                val xml = Resources.bytes(NoteSkin.ATLAS)?.decodeToString()
                if (image != null && xml != null) NoteSkin.create(image, xml) else null
            }.getOrNull()
        }
    }

    Card {
        SectionTitle("Open", accent)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Primary("Open mod or files…", accent, !model.reading && !play.busy) { Dialogs.openFiles("Open a mod .zip, mod files or chart files") { model.open(it) } }
            Secondary("Open folder…", !model.reading && !play.busy) { Dialogs.chooseFolder("Choose the mod folder") { model.open(listOf(it)) } }
        }
        Text(
            "A V-Slice, Psych or Codename mod (folder or archive), a converter's result or chart files with their audio. Psych and Codename mods are converted to V-Slice first, so this also tests the converter.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted
        )
        if (model.reading) Text("Reading files…", style = MaterialTheme.typography.bodySmall, color = accent)
    }

    AdaptiveRow {
        Column(Modifier.pane(1.6f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                SectionTitle("Game", accent)
                PlayTestControls(play)
                PlayTestView(
                    play,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).border(1.dp, Palette.Outline, RoundedCornerShape(12.dp)),
                    skin,
                    touchLanes = true
                )
                Text(
                    "Arrow keys or D F J K hit the notes, Space or Enter plays and pauses, B toggles botplay, Escape stops.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted
                )
                if (play.phase == PlayPhase.RESULTS) PlayTestResultsPanel(play)
            }
        }
        Column(Modifier.pane(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card { PlayTestSetupPanel(play) }
            Card { PlayTestIssuesPanel(play) }
        }
    }
}
