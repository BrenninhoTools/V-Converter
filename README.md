<div align="center">

<img src="desktop/icon.png" width="128" alt="V-Converter icon">

# V-Converter

**Convert Friday Night Funkin' mods between Psych Engine, V-Slice and Codename Engine.**

[![Build](https://github.com/BrenninhoTools/V-Converter/actions/workflows/build.yml/badge.svg)](https://github.com/BrenninhoTools/V-Converter/actions/workflows/build.yml)
![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)
![Windows](https://img.shields.io/badge/Windows-0078D4?logo=windows&logoColor=white)
![Linux](https://img.shields.io/badge/Linux-FCC624?logo=linux&logoColor=black)
![macOS](https://img.shields.io/badge/macOS-000000?logo=apple&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Vibe coded](https://img.shields.io/badge/vibe%20coded-%E2%9C%A8-ff69b4)

</div>

> [!NOTE]
> **This app was vibe coded.** V-Converter was built through vibe coding: the code was written with an AI assistant (Claude) from prompts, then tested and checked against the engines' source code. Conversions are validated, but expect rough edges, and please [open an issue](https://github.com/BrenninhoTools/V-Converter/issues) if something breaks.

A remake of the V-Slice converter as a native app written in Kotlin, for **Android**, **Windows**, **Linux** and **macOS**. It converts content from **Psych Engine** and **Codename Engine** to **V-Slice** (the current base game), from V-Slice back to Psych, and between Psych and Codename in both directions.

## Contents

- [Download](#download)
- [Supported conversions](#supported-conversions)
- [Generator](#generator) · [Chart Editor](#chart-editor) · [Character Editor](#character-editor) · [Stage Editor](#stage-editor) · [Week Editor](#week-editor)
- [Psych → V-Slice](#psych--v-slice) · [V-Slice → Psych](#v-slice--psych) · [Codename → V-Slice](#codename--v-slice) · [Codename → Psych](#codename--psych) · [Psych → Codename](#psych--codename)
- [Multieditor](#multieditor) · [PlayTest](#playtest) · [Online server](#online-server)
- [Building](#building)

## Download

Get the latest version from [Releases](https://github.com/BrenninhoTools/V-Converter/releases): `V Converter-Android.apk` for Android and `V Converter-Windows.exe` (installer) for Windows. Each release's changelog lives in [`docs/releases`](docs/releases). Releases are published by the [Release workflow](https://github.com/BrenninhoTools/V-Converter/actions/workflows/release.yml), started by hand with the version to publish.

Every push to `main` is also built by [GitHub Actions](https://github.com/BrenninhoTools/V-Converter/actions/workflows/build.yml). Open the latest successful run and grab one of its artifacts:

| Artifact | Platform | What it is |
| --- | --- | --- |
| `V Converter-Android` | 📱 Android | `V Converter-Android.apk`, the optimized (R8) signed APK |
| `V Converter-Windows` | 🪟 Windows | `V Converter-Windows.exe`, the installer |
| `V Converter-Linux` | 🐧 Linux | `V Converter-Linux.deb` for Debian/Ubuntu and `V Converter-Linux.tar.gz`, a portable folder for any distro (run `V-Converter/bin/V-Converter`) |
| `V Converter-MacOS` | 🍎 macOS | `V Converter-MacOS.dmg` (drag the app to Applications) and `V Converter-MacOS.zip` with the app itself. It isn't notarized: the first time, right-click the app and choose **Open**, or allow it in **System Settings → Privacy & Security** |

## Supported conversions

| Module | Psych → V-Slice | V-Slice → Psych | Codename → V-Slice | Codename → Psych | Psych → Codename |
| --- | :---: | :---: | :---: | :---: | :---: |
| Whole mod (folder / .zip / .rar / .7z) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Charts | ✅ | ✅ | ✅ | ✅ | ✅ |
| Characters | ✅ | ✅ | ✅ | ✅ | ✅ |
| Stages | ✅ | ✅ | ✅ | ✅ | ✅ |
| Weeks ↔ Levels | ✅ | ✅ | ✅ | ✅ | ✅ |
| Scripts | ✅ | ✅ | ✅ | ✅ | ✅ |

| Direction | Scripts |
| --- | --- |
| Psych → V-Slice | Lua / HScript → `.hxc` |
| V-Slice → Psych | `.hxc` → Psych HScript |
| Codename → V-Slice | Codename HScript → `.hxc` |
| Codename → Psych | Codename HScript → Psych HScript |
| Psych → Codename | Lua / HScript → Codename HScript |

The Android app has two sections, switched at the top: **Converter** and **Generator** (new V-Slice content from scratch). In the Converter, pick the source engine (**Psych**, **Codename** or **V-Slice**) and the target engine in the bridge at the top of each tab; tapping the **To** card or the swap button switches the target. On Windows, Linux and macOS the engines are picked in the sidebar, and files or mods can be dragged into the window. Both apps use the V-Converter icon (Boyfriend in a hard hat, in `desktop/icon.png`; `icon.ico` and `icon.icns` for the Windows and macOS packages) and the custom cursor from `assets/images/cursor/cursor.png` (on Android it shows with a mouse or trackpad, and where you touch the screen unless "Touch Cursor" is turned off in the options) show an `FPS:` counter in the top-right corner and play `assets/music/freakyMenu.ogg` as menu music (paused while the Chart Editor plays a song) and `assets/sounds/scrollMenu.ogg` when tabs and buttons are clicked; the counter, the sounds, the music and its volume are set in the options (the gear in the top-right corner).

## Generator

| Tab | Creates |
| --- | --- |
| Mod | `_polymod_meta.json`, `_polymod_icon.png` and an optional starter module script |
| Chart | The [Chart Editor](#chart-editor): start a song or open any chart and edit it to the music |
| Character | The [Character Editor](#character-editor): open any character or a spritesheet and fix its animations and offsets |
| Stage | The [Stage Editor](#stage-editor): open any stage or start from your images and lay it out with the game camera in view |
| Week | The [Week Editor](#week-editor): open any week or start a new one and lay out its story menu, songs and unlocking |
| Script | `.hxc` classes: Module, Song, Stage, Character (Sparrow, Multi-Sparrow, Animate atlas, Multi-atlas, Packer), Song event (with Chart Editor fields and gameplay callbacks) and Note kind, with the callbacks you pick |

Every tab can download the result as a `.zip` or write it into the mod folder.

## Chart Editor

The **Chart** tab of the Generator on Android, and **Chart Editor** in the sidebar on Windows, Linux and macOS.

- **New or existing charts**: start a song from a name, BPM and difficulties, or open V-Slice `<song>-metadata.json` + `<song>-chart.json`, Psych chart `.json` files or a Codename song (`meta.json` + `charts/*.json`). Folders and `.zip`/`.rar`/`.7z` archives work too, and `Inst`/`Voices` audio found next to the charts is loaded with them.
- **Note grid**: a V-Slice style checkerboard with the strumline on top, notes, holds and receptors drawn with the base game sprites from `assets/images/ui/NOTE_assets.png`, opponent lanes on the left, player lanes on the right and the event lane last, with events shown with `assets/images/ui/charter/event.png`. Tap/click a lane to place a note snapped to the grid (1/4 up to 1/192), tap it again to select it and once more to delete it. Selected notes get a hold length and a note kind (`alt`, `hurt`…). Drag or use the mouse wheel to scroll, zoom in and out, jump by measures, undo and redo from the toolbar, and tap or drag the mini-map next to the grid to jump around the song. On wide screens the grid sits between the side panels; on phones the panels are tabs under it.
- **Music**: plays the instrumental with the voices mixed in (one shared voices file or split player/opponent), with optional hitsounds while the chart scrolls past the strumline. Without audio the chart still plays at its BPM.
- **Events and BPM**: `FocusCamera`, `PlayAnimation`, `ZoomCamera`, `ScrollSpeed` and `SetCameraBop` with their own fields, or any custom event with a JSON value. BPM and time signature changes go on the timing list; the grid follows them.
- **Difficulties**: add empty or copied difficulties, remove them, mirror or clear notes and set the scroll speed of each one. The song info (name, artist, charter, characters, stage, note style, offset, variation) is edited in place.
- **Export**: V-Slice (`data/songs/<id>/` + `songs/<id>/` audio), Psych Engine (`data/<id>/` charts and `events.json`, `songs/<id>/Inst.ogg` and `Voices*.ogg`) or Codename Engine (`songs/<id>/meta.json`, `charts/` and `song/` audio, with the strumlines pointed at split voices), as a `.zip` or straight into a mod folder.

On Windows, Linux and macOS: <kbd>Space</kbd> plays, <kbd>Ctrl</kbd>+<kbd>Z</kbd> / <kbd>Ctrl</kbd>+<kbd>Y</kbd> undo and redo, <kbd>Q</kbd> / <kbd>E</kbd> shorten and lengthen the selected hold, <kbd>Delete</kbd> removes the selection, <kbd>Page Up</kbd> / <kbd>Page Down</kbd> jump a measure.

## Character Editor

The **Char** tab of the Generator on Android, and **Character Editor** in the sidebar on Windows, Linux and macOS.

- **Open anything**: a V-Slice or Psych character `.json` or a Codename character `.xml`, together with its spritesheet `.png` + `.xml` (folders and archives work too). A spritesheet alone starts a new character with the animations guessed from the frame names (`idle`, `singLEFT`…, misses, `danceLeft`/`danceRight`).
- **Animation preview**: plays every animation from the spritesheet with its frame rate, loop and indices, frame by frame if you want, drawn the way V-Slice places characters (feet on the floor line, offsets scaled with the sprite).
- **Offsets**: drag the sprite or use the arrow buttons to move the selected animation, with a ghost of the idle (or any other animation) behind it. Undo and redo cover every change.
- **Stage and game camera**: the character stands on the floor of `assets/images/ui/character/stage.png` (the **Stage** chip hides it). The camera marker sits where V-Slice points the camera: the character's center plus its camera offset plus the stage's default offset for its side, ignoring the global offsets. A dashed frame shows what a 1280×720 game camera sees at the chosen **Preview zoom**.
- **Character settings**: side (player, opponent, girlfriend), scale, position, camera offset (dragged on the preview too), flip X, pixel art, health icon, sing time, dance rate and starting animation.
- **Export**: V-Slice (`data/characters/<id>.json` + `images/…`), Psych Engine (`characters/<id>.json`, offsets and position converted for the chosen side) or Codename Engine (`data/characters/<id>.xml` + `images/characters/…`), always with the spritesheet.

On Windows, Linux and macOS: <kbd>Space</kbd> plays, the arrow keys move the offset (<kbd>Shift</kbd> for 10), <kbd>W</kbd> / <kbd>S</kbd> pick the animation, <kbd>,</kbd> / <kbd>.</kbd> step frames, <kbd>G</kbd> toggles the ghost and the mouse wheel zooms.

## Stage Editor

The **Stage** tab of the Generator on Android, and **Stage Editor** in the sidebar on Windows, Linux and macOS.

- **Open anything**: a V-Slice or Psych stage `.json` (with its `.lua` when it builds sprites there) or a Codename stage `.xml`, together with the stage images; opening a whole mod folder or archive finds every image the stage uses. A new stage starts with the base game character positions.
- **Layout**: every prop drawn with its image (the first frame for animated Sparrow props), scale, scroll factor, alpha, angle and flips, plus boxes for the player, opponent and girlfriend at their feet position with their camera points. A dashed frame shows the game camera at the stage's zoom, and parallax follows the scroll factors as you move the view.
- **Editing**: tap or click to select, drag to move, arrow buttons to nudge, layers sorted by `zIndex` with bring forward / send back, duplicate and delete, solid color props, images added from `.png` (+ `.xml` for animations), stage name, zoom and library. Undo and redo cover every change.
- **Export**: V-Slice (`data/stages/<id>.json`), Psych Engine (`stages/<id>.json` plus a Lua script for what Psych JSON can't hold) or Codename Engine (`data/stages/<id>.xml`), always with the images in `images/`.

On Windows, Linux and macOS: the arrow keys nudge the selection (<kbd>Shift</kbd> for 10), <kbd>Page Up</kbd> / <kbd>Page Down</kbd> change its layer, <kbd>Ctrl</kbd>+<kbd>D</kbd> duplicates, <kbd>Delete</kbd> removes a prop and the mouse wheel zooms.

## Week Editor

The **Week** tab of the Generator on Android, and **Week Editor** in the sidebar on Windows, Linux and macOS.

- **Open anything**: a V-Slice level `.json`, a Psych week `.json` (with its `images/menucharacters/*.json`) or a Codename week `.xml` (with its `data/weeks/characters/*.xml`), together with the title and menu character images. V-Slice song metadata files fill in the song names and difficulties, and a V-Slice level script (`scripts/levels/`) brings back the unlocking settings. A new week starts with the base game's dad, bf and gf.
- **Story menu preview**: the 1280×720 story menu as V-Slice draws it: the background color or image, the menu characters playing their idle (or `danceLeft`/`danceRight`) animations at their offsets, one slot per quarter of the screen, the title image, the tracks list, the score and week name, and the difficulty (switched from the toolbar). **Confirm** plays the confirm animations.
- **Menu characters**: none, base game or custom for the opponent, boyfriend and girlfriend slots; custom ones load a `.png` + `.xml` and pick their idle and confirm animations from the spritesheet, with scale, offsets (dragged on the preview too), pixel art, flip X and dance rate.
- **Songs**: add, remove and reorder the songs, with their id, display name, freeplay icon and color.
- **Unlocking**: the week to beat first, start unlocked, hidden until unlocked, hidden from story mode and songs hidden from freeplay, plus the difficulty list.
- **Export**: V-Slice (`data/levels/<id>.json`, plus a level script in `scripts/levels/` for the unlocking and freeplay settings), Psych Engine (`weeks/<id>.json` and `images/menucharacters/*.json`) or Codename Engine (`data/weeks/weeks/<id>.xml` and `data/weeks/characters/*.xml`), always with the title, background and custom menu character images. Undo and redo cover every change.

On Windows, Linux and macOS: click a menu character and drag it, the arrow keys nudge it (<kbd>Shift</kbd> for 10) or change the difficulty when nothing is selected, <kbd>Enter</kbd> plays the confirm animation and <kbd>Space</kbd> pauses the animations.

---

## Psych → V-Slice

The main converter. Each part below has its own tab in the app.

## Whole mod

The **Mod** tab converts an entire Psych Engine mod in one go, from its folder or from a `.zip`, `.rar` (RAR4 and RAR5) or `.7z` archive. The mod root is found automatically even when the archive wraps it in extra folders. Older mods shipped as a whole engine build work too: `assets/preload/`, `assets/shared/`, `assets/week*/`, `assets/base_game/` and `assets/songs/` are merged as if they were a `mods/` folder.

- `data/<song>/` charts (+ `events.json`) → `data/songs/<song>/`
- `characters/`, `stages/`, `weeks/` → `data/characters/`, `data/stages/`, `data/levels/`
- `images/`, `songs/`, `music/`, `sounds/`, `fonts/`, `videos/`, `shaders/` copied as-is
- Split vocals (`Voices-Player.ogg`, `Voices-Opponent.ogg` or a character's `vocals_file`) are wired through `playerVocals` / `opponentVocals`, so audio files keep their names
- Character slots are inferred from how the charts use each character
- Placeholder folders that hold nothing but a single text file (like a template's `readme.txt`) are left out automatically, in both directions
- `pack.json` / `pack.png` → `_polymod_meta.json` / `_polymod_icon.png`
- Lua and HScript scripts → their V-Slice classes: `data/<song>/` → `scripts/songs/` (`Song`), `stages/` → `scripts/stages/` (`Stage`), `custom_notetypes/` → `scripts/notekinds/` (`NoteKind`), `custom_events/` → `scripts/events/` (`SongEvent`), `scripts/` → `scripts/modules/` (`Module`)
- A chart's custom `arrowSkin` (and `splashSkin`) → a V-Slice note style in `data/notestyles/` built from the same sheet; note types that set their own `texture` get a note style plus a `NoteKind` script that applies it
- Songs on a pixel stage (`isPixelStage` or `stageUI: "pixel"`) use the `pixel` note style

Every other tab also accepts archives and picks the files it needs from them.

## Charts

Pick one or more Psych Engine charts (`song-easy.json`, `song.json`, `song-hard.json`, …) with their `events.json`; several songs at once work too. The app writes a V-Slice song folder:

```
<song-id>/
├── <song-id>-metadata.json
├── <song-id>-chart.json
├── <song-id>-metadata-erect.json   (only when a difficulty needs its own variation)
└── <song-id>-chart-erect.json
```

Drop that folder inside `data/songs/` of your V-Slice mod.

### The Charts tab

The same Charts tab is used for every direction (Psych ↔ V-Slice, Codename → V-Slice, Codename ↔ Psych), on Android, Windows and Linux:

1. **Files**, grouped by song. Psych difficulties can be renamed in place (handy for `song-insane.json` style names).
2. **Preview**: a minimap of the whole chart with the opponent and player lanes (and girlfriend lanes when used), holds, camera focus, BPM and time signature changes and events, with a zoom toggle. After converting, switch between the source and the converted chart: the app checks that every note, hold and BPM change made it across.
3. **Options**: song name, artist, charter, characters, stage, note style and scroll speed per difficulty (empty fields keep what the chart uses), plus a switch to keep or drop Psych-only events. Only the options the target engine has are shown.
4. **Convert** and save, like the other tabs.

### What gets converted

- Both the legacy format (pre-1.0, `mustHitSection` relative lanes) and `psych_v1` (absolute lanes)
- Every difficulty merged into one chart, each with its own scroll speed
- Difficulties with their own BPM, time signatures, events, characters or stage are not flattened: they become a V-Slice variation (`erect` for erect/nightmare charts), and the whole-mod converter copies the `Voices` files with the variation suffix V-Slice looks for
- BPM changes and time signatures (sections of 3, 6, 7… beats) rebuilt from section lengths into `timeChanges`
- The chart's `offset` becomes the metadata's instrumental offset (and back, V-Slice → Psych)
- Camera focus from `mustHitSection` / `gfSection` → `FocusCamera`
- Psych events:
  - `Hey!` → `PlayAnimation` (`hey`)
  - `Play Animation` → `PlayAnimation`
  - `Camera Follow Pos` → `FocusCamera` (position or back to section focus)
  - `Change Scroll Speed` → `ScrollSpeed` (relative, duration converted to steps)
- Note types → note kinds (`Alt Animation` → `alt`, `Hurt Note` → `hurt`, `Hey!` → `hey`, `No Animation` → `noanim`, custom types → slugged id), and `altAnim` sections → `alt` on opponent notes
- Stacked duplicate notes are removed
- Stage ids mapped to the V-Slice ones (`stage` → `mainStage`, `spooky` → `spookyMansion`, …); `school`/`schoolEvil` or pixel arrow skins → `pixel` note style

Other Psych events get a V-Slice style id (`Flash Camera` → `FlashCamera`, never clashing with built-in ids like `FocusCamera`), applied the same way in charts and scripts, while converted scripts still receive the original name in `onEvent`. `custom_events/*.lua`/`.hx` become `scripts/events/<Name>Event.hxc`: a single `SongEvent` (Chart Editor entry with Value 1/Value 2) that holds the converted code and runs it in `handleEvent`. Built-in Psych events without a V-Slice event (`Add Camera Zoom`, `Screen Shake`, `Set Property`, `Play Sound`, `Set GF Speed`, `Alt Idle Animation`) get a `SongEvent` script there too. Anything that can't be converted yet is listed in the in-app report.

## Saving

Pick your V-Slice mod folder once (it is remembered). Converted files go straight to the right place:

```
<mod>/data/songs/<song-id>/<song-id>-metadata.json
<mod>/data/songs/<song-id>/<song-id>-chart.json
<mod>/data/characters/<id>.json
<mod>/data/stages/<id>.json
<mod>/data/levels/<id>.json
<mod>/scripts/songs/<Song>Song.hxc
<mod>/scripts/stages/<Stage>Stage.hxc
<mod>/scripts/notekinds/<Type>NoteKind.hxc
<mod>/scripts/events/<Name>Event.hxc
<mod>/scripts/modules/<Class>.hxc
```

## Characters

Pick Psych character `.json` files together with their spritesheet `.xml` files. With the XML the conversion is pixel-exact: V-Slice anchors characters at their feet (idle frame bottom-center) and scales animation offsets, while Psych anchors at the top-left of the first atlas frame, so offsets and camera are rebased using the real frame sizes.

- Animations, prefixes, indices, frame rate and loop
- Sparrow and multi-atlas (`multisparrow`) sheets
- Scale, flip, pixel (no antialiasing), health icon, sing duration, dance speed (`danceLeft`/`danceRight`)
- Camera position folded into `cameraOffsets`
- Slot (Player / Opponent / Girlfriend) decides how offsets are rebased
- `<anim>-loop` animations (played by Psych after `<anim>` ends) become V-Slice's `<anim>-hold`, which works the same way (and back, V-Slice → Psych)
- **Animate atlas characters** (`images/<image>/Animation.json` + `spritemap1.json`): Psych's symbol names (matched by prefix) become the exact symbol names V-Slice plays (`animType: "symbol"`), and the offsets include each symbol's bounds, read from `Animation.json` and the spritemap, so the atlas lands where Psych draws it. The same works Psych → Codename, V-Slice → Psych (frame label animations become indices of the main symbol, which is all Psych can play) and Codename → Psych

- **Game over characters**: a player's game over character (`<id>-dead`, or Psych's default `bf-dead`) is merged into it, since V-Slice keeps `firstDeath`, `deathLoop` and `deathConfirm` in the player character (from the game over spritesheet, as `multisparrow` or `multianimateatlas`), placed where Psych's game over screen draws them
- Global `offsets` of V-Slice characters are applied the way V-Slice does (scaled with the sprite), in both directions

Checked against the base game: all 44 V-Slice characters that have their spritesheets convert to Psych and back with the same offsets (death animations included), and every Psych 0.6/0.7 base character converts to V-Slice and back exactly.

Not carried over: health bar colors and per-character vocal suffixes (V-Slice has no equivalent).

## Stages

Built against V-Slice's `Stage.hx` / `StageData.hx`, so the JSON loads and draws like Psych drew it:

- Character positions → V-Slice feet anchors, camera offsets (including Psych's built-in ±100/150 camera shifts) and zoom
- `directory` is always a real V-Slice asset library (`shared`, `week1`…): V-Slice stops with "Missing library" on unknown ones, and mod images load from `images/` anyway
- Psych 1.0 stage editor `objects` → props (sprites, animated sprites, solid squares), keeping layer order, scroll, scale, alpha, angle, color, flip and pixel mode; low-quality-only and hidden objects are left out
- Characters missing from the objects list go on top instead of disappearing; `hide_girlfriend` becomes an invisible girlfriend
- Scaled animated sprites: V-Slice multiplies animation offsets by the scale and keeps the hitbox offset, Psych replaces it. With the spritesheet `.xml` the offsets are recalculated so every animation lands on the exact Psych pixel (the starting animation too)
- Animated sprites without animations show the spritesheet's first frame, like Psych
- Stage Lua is read: sprites it creates become props, and beat bops (`if curBeat % N == 0 then playAnim(...)`, danceLeft/danceRight alternation, `onStepHit` bops) become `danceEvery`, with the bop animation renamed to `idle` when needed. Lua `addOffset`, `setBlendMode` and frame-index `addAnimation` (Packer sheets) on stage objects are folded in, and those lines leave the converted script
- Girlfriend scroll factor (0.95) preserved

Stages built only in code have no object list; their character layout converts, base Psych stages point to their V-Slice twin (`stage` → `mainStage`…) and the report lists the images to recreate as props.

## Weeks

- Songs (ids in Psych's song path format), story name, background (`menubackgrounds/menu_<name>` or the default yellow) and title image (`storymenu/<week>`)
- Menu characters from `images/menucharacters/*.json` → level props, placed where Psych draws them; base `dad`/`bf`/`gf` map to V-Slice's own props
- Hidden story weeks → `visible: false`
- Lock chains and freeplay hiding → a level script (`scripts/levels/<Week>Level.hxc`): the level unlocks after beating `weekBefore`, stays hidden until then when `hiddenUntilUnlocked` is set, keeps its songs out of freeplay while locked (like Psych) and hides them for good with `hideFreeplay`

Not carried over: per-song freeplay icons/colors (V-Slice shows the opponent's pixel icon; listed in the report).

## Scripts

The **Scripts** tab (and the Mod tab) turns Psych Engine scripts into V-Slice scripted classes (`.hxc`).

- **Lua** is fully parsed (Lua 5.1 / LuaJIT syntax) and transpiled to HScript: locals, globals, closures, varargs, tables (arrays and maps), `pairs`/`ipairs`, numeric loops, `goto continue`, `and`/`or` with Lua truthiness, and the `string`, `table`, `math`, `os` and `bit` libraries, including Lua patterns (`find`, `match`, `gmatch`, `gsub`, `format`)
- **Psych HScript** (`.hx`) is wrapped into its class: imports are mapped (`states.PlayState` → `funkin.play.PlayState`, …), and `game.boyfriend`, `camFollow`, `defaultCamZoom`, `Conductor.songPosition`, `curBeat`, `ClientPrefs`… are rewritten to their V-Slice equivalents
- Every script becomes the standard V-Slice class for its job, named after its id (no mod prefix, no invented base classes), built against FunkinCrew/Funkin's `Song`, `Stage`, `NoteKind`, `SongEvent` and `Module`:

| Psych | V-Slice | Class |
| --- | --- | --- |
| `data/<song>/*.lua`, `.hx` | `scripts/songs/<Song>Song.hxc` | `extends Song`, `super('<song>')` |
| `stages/<stage>.lua`, `.hx` | `scripts/stages/<Stage>Stage.hxc` | `extends Stage`, `super('<stage>')` |
| `custom_notetypes/<type>` | `scripts/notekinds/<Type>NoteKind.hxc` | `extends NoteKind`, `super('<kind>', '<type>'[, '<note style>'])` |
| `custom_events/<event>` | `scripts/events/<Event>Event.hxc` | `extends SongEvent`, code runs in `handleEvent` |
| `scripts/*` | `scripts/modules/<name>.hxc` | `extends Module` |

- A script only falls back to a scoped `Module` (`<Name>Module`, listed in the report) when its class can't carry it: a second script for the same song or stage (V-Slice allows one `Song`/`Stage` class per id), a note type that uses callbacks a `NoteKind` never receives (a `NoteKind` only gets update, note and create/destroy events), or an id the base game already scripts (`bopeebo`, `phillyTrain`…). Class names that clash with base game classes get the mod name in front
- Members are renamed when they would override something the class inherits (a Lua `update` or `color` in a stage becomes `updateScript`/`colorScript`, so `FlxSpriteGroup.update` keeps working), `Song`/`Stage`/`SongEvent`/`NoteKind` clean up in `onDestroy` (they have no `onStateChangeBegin`), and a note type texture becomes the note style of its own `NoteKind` instead of a second class
- Psych callbacks are hooked to V-Slice events: `onCreate`, `onCreatePost`, `onUpdate`, `onBeatHit`, `onStepHit`, `onSectionHit` (on real measures, so time signatures work), `goodNoteHit`, `opponentNoteHit`, `noteMiss`, `noteMissPress`, `onGhostTap`, `onKeyPress`/`onKeyRelease` (and their `Pre` versions), `onUpdateScore`, `onRecalculateRating`, `onEvent`, `onEventPushed`, `onMoveCamera`, `onSpawnNote`, `onCountdownTick`, `onStartCountdown`, `onEndSong`, `onSongStart`, `onPause`, `onResume`, `onGameOver`, `onGameOverStart`, `onGameOverConfirm`, `onDestroy`, `onTimerCompleted`, `onTweenCompleted`, `onSoundFinished`; returning `Function_Stop` cancels the countdown and the song end like in Psych
- `runHaxeCode` with code written in the script is converted inline (its functions, variables and the variables passed to it), and `runHaxeFunction` calls those functions; `_G.name`, `_G["name"]` and `_G[key]` work, and so does Psych 0.7's `FlxColor("#RRGGBB")`
- The output is plain V-Slice HScript: no Lua-style leftovers (no `Function_*` constants, no `psych("…")` dispatch, no `g_`/`l_` prefixes). Script names keep their own names (`onUpdate` becomes `onUpdateScript` where it would clash with a method of the class), and Psych calls become real functions such as `makeSprite(...)`. No runtime or helper classes are added: each class carries native V-Slice/Flixel code for exactly the Psych functions it uses (Lua sprites and texts, `getProperty`/`setProperty` paths, tweens, timers, sounds, camera, health, score, strum notes, shaders, saves, shared variables). Scripts of the same mod can still reach each other's Lua sprites
- `precacheImage`, `precacheSound`, `precacheMusic` and `addCharacterToList` cache through V-Slice's `FunkinMemory` (`cacheTexture`/`cacheSound`, and the character's spritesheet from its data), so the assets are ready before they show up
- Every class, field and event the generated code touches was checked against the FunkinCrew/Funkin source

Animate atlas sprites (`makeFlxAnimateSprite`, `loadAnimateAtlas`, `addAnimationBySymbol(Indices)`), every shader getter/setter (int/bool arrays, `setShaderSampler2D`), camera scroll and follow-point helpers, gamepads (buttons and analog sticks), `getPixelColor`, `loadMultipleFrames`, `createInstance`/`addInstance`, `callMethodFromClass`, `addToGroup`/`removeFromGroup`, `directoryFileList`, `loadSong` and `startVideo` are converted too, for V-Slice and for Codename.

The report lists, line by line, anything a script uses that has no V-Slice equivalent (custom substates, `addLuaScript`/`addHScript`, `saveFile`/`deleteFile`, dialogue, metatables, `unspawnNotes` tricks, callbacks such as `onNextDialogue` or `eventEarlyTrigger` that never run in V-Slice) so those parts can be finished by hand.

---

## V-Slice → Psych

| Input | Output |
| --- | --- |
| `<song>-metadata.json` + `<song>-chart.json` | `data/<song>/<song>[-difficulty].json` (psych_v1): sections rebuilt from time changes (exact BPM changes), camera focus → `mustHitSection`/`gfSection`, note kinds → note types, `PlayAnimation`/`ScrollSpeed`/`ZoomCamera`/`FocusCamera` → Psych events |
| Variations (`-erect`…) | Their own Psych song (`<song>-erect`) with their own audio, in a freeplay-only week |
| `data/characters/<id>.json` (+ XML) | `characters/<id>.json`, positions and camera rebased exactly with the spritesheet; library prefixes (`shared:`) are removed from image paths, several Sparrow sheets are merged in `image`, and texture atlas characters keep the animations of their main atlas (the report lists those from other atlases) |
| `data/stages/<id>.json` (+ images' `.xml`) | `stages/<id>.json` with stage-editor objects, layer order and character markers, plus `stages/<id>.lua` when needed: props with `danceEvery` bop on the beat (idle or danceLeft/danceRight), scaled animated props get V-Slice's offset math and start on their starting animation, props without one stay still, Packer animations, blend modes, and character scroll/alpha/angle; an invisible girlfriend becomes `hide_girlfriend` |
| `data/levels/<id>.json` | `weeks/<id>.json` + `images/menucharacters/*.json` for custom props |
| `.hxc` scripts | Psych HScript: modules → `scripts/`, songs → `data/<song>/`, stages → `stages/`, characters → `characters/`, song events → `custom_events/` (+ `.txt`), note kinds → `custom_notetypes/`. Properties (`get`/`set`) become getter/setter calls, `FunkinSprite.create`/`createSparrow`, `Save.instance.hasBeatenSong`/`hasBeatenLevel`, `PlayStatePlaylist.isStoryMode`, `GameOverSubState`, `Constants` and `Preferences` values are mapped, haptics are dropped, `FunkinMemory` caching becomes Psych's own caching (`Paths.image`/`Paths.sound`) and V-Slice library names (`"week4"`, `"shared"`) are removed from `Paths` calls. Stage scripts run like in V-Slice: `buildStage()` runs at the start of `onCreate` (after Psych builds the stage objects), `addCharacter()` is called for boyfriend, dad and gf, `fetchAssetPaths()` precaches its assets, `onCountdownEnd` runs at song start, `getNamedProp`/`add`/`addProp`/`refresh()` work, and `zIndex` keeps working through small helpers that reorder the stage objects, characters and new props with the stage JSON's layers. `funkin.util.Sequence` becomes FlxTimers and `AdjustColorShader` becomes Psych's `adjustColor` runtime shader with the same color math. `VideoCutscene.play(Paths.videos(...))` becomes `PlayState.instance.startVideo(...)`. Only classes Psych 1.0 really has are left in the output: types of V-Slice-only classes become `Dynamic`, and statements that need a V-Slice-only class (`StageProp`, `RuntimeRainShader`, `FlxTweenUtil`, other scripted classes…), plus the code that uses values made from them, are left out and listed line by line in the report. All 114 base game scripts convert to HScript that Psych 1.0's parser accepts, with no class Psych lacks |
| Player characters with `firstDeath`/`deathLoop`/`deathConfirm` | `characters/<id>-dead.json` with the game over animations plus `characters/<id>.lua`, which makes Psych use it when that character is the player |
| Mod | `pack.json`/`pack.png`, `Voices-<char>.ogg` → `Voices-Player.ogg`/`Voices-Opponent.ogg`, assets copied |

Round trips (Psych → V-Slice → Psych) keep notes, sections, events, stage positions and the on-screen character placement.

---

## Codename → V-Slice

| Input | Output |
| --- | --- |
| `songs/<song>/charts/<diff>.json` + `meta.json` + `events.json` | `<song>-metadata.json` + `<song>-chart.json`: player/opponent strumlines → lanes 0-3/4-7, note types → note kinds (`Alt Anim Note` → `alt` with a generated `NoteKind`), `BPM Change`/`Time Signature Change` → time changes, `Camera Movement`/`Camera Position` → `FocusCamera`, `Camera Zoom` → `ZoomCamera`, `Scroll Speed Change` → `ScrollSpeed`, `Camera Modulo Change` → `SetCameraBop`, `Play Animation` → `PlayAnimation` |
| Variants (`charts/<variant>/`, `meta-<variant>.json`) | V-Slice variations with their own `Inst-<variant>` audio |
| Other events (`Camera Flash`, `Add Camera Zoom`, `Camera Bop`, `HScript Call`, custom `data/events/*.json`) | Kept in the chart with named values, plus a `SongEvent` in `scripts/events/` (with a working implementation for the built-in ones) |
| `data/characters/<id>.xml` (+ spritesheet XML) | `data/characters/<id>.json`: offsets multiplied by the scale (Codename scales them, V-Slice does not), characters used on the other side mirrored like Codename does at runtime, `<id>-dead`/`gameOverChar` animations merged in for game over |
| `data/stages/<id>.xml` | `data/stages/<id>.json`: sprites, boxes, `high-memory` content, beat sprites (`danceEvery`), layer order and character spots; scaled sprites without `updateHitbox` are moved to where Codename draws them |
| `data/weeks/weeks/*.xml` + `data/weeks/characters/*.xml` | `data/levels/*.json` with menu props |
| `.hx` scripts | The same standard classes: song scripts → `Song`, stage scripts → `Stage`, note type scripts → `NoteKind` (when they only use note, update and create/destroy callbacks), global ones → `Module`; stage extensions, characters and events become `Module`s scoped like Codename runs them, and so does any script whose id already has a class. `create`/`update`/`beatHit`/`onPlayerHit`/`onEvent`… become module events with Codename-style event objects; `boyfriend`, `camHUD`, `curBeat`, `Conductor.crochet`, stage sprites by name and so on are mapped to the V-Slice API |
| Mod | `_polymod_meta.json` from `data/config/modpack.ini`, `song/Inst.ogg`/`Voices*.ogg` moved to `songs/<song>/`, assets copied |

---

## Codename → Psych

| Input | Output |
| --- | --- |
| `songs/<song>/charts/<diff>.json` + `meta.json` + `events.json` | `data/<song>/<song>-<diff>.json` (Psych 1.0 format): strumlines → sections with `mustHitSection`/`gfSection` from `Camera Movement`, girlfriend strumlines → `GF Sing` notes or a hidden note type, note types renamed (`Alt Anim Note` → `Alt Animation`…), `BPM Change`/`Time Signature Change` → section BPM and beats, `Add Camera Zoom`, `Play Animation`, `Scroll Speed Change` → Psych events |
| Other events (`Camera Position`, `Camera Zoom`, `Camera Bop`, `Camera Flash`, `Camera Modulo Change`, `HScript Call`, custom ones) | `custom_events/<name>.hx` + `.txt`, with working Psych implementations for the built-in ones and the parameters packed into the two values |
| Variants | Their own Psych songs (`<song>-<variant>`), audio included |
| `data/characters/<id>.xml` | `characters/<id>.json`: offsets divided back from the scale, `holdTime` → `sing_duration`, camera offsets per side; character scripts converted |
| `data/stages/<id>.xml` | `stages/<id>.json` with objects, layers and character spots, plus `stages/<id>.hx` for beat dancers, blends, scroll/scale tweaks, `high-memory` content and stage extensions |
| `data/weeks/weeks/*.xml` + `data/weeks/characters/*.xml` | `weeks/*.json`, `weeks/weekList.txt`, `images/menucharacters/*.json`; songs outside every week go to a hidden-story freeplay week |
| `.hx` scripts | Psych HScript: `create`/`postCreate`/`update`/`beatHit`/`onPlayerHit`/`onDadHit`/`onEvent`/`onCountdown`… become Psych callbacks with Codename-style event objects (`event.cancel()` still works), `strumLines`, `stage.getSprite`, `FunkinSprite`, `CustomShader`, `Options`, `Conductor` and so on are mapped to Psych; a small `scripts/CodenameCompat.hx` recreates Codename-only camera settings when needed |
| Mod | `pack.json` from `data/config/modpack.ini`, `song/Inst.ogg`/`Voices*.ogg` moved to `songs/<song>/`, assets copied |

---

## Psych → Codename

| Input | Output |
| --- | --- |
| `data/<song>/<song>-<diff>.json` + `events.json` | `songs/<song>/charts/<diff>.json` + `meta.json`: opponent, player and (hidden) girlfriend strumlines, per-character vocals (`Voices-Player`/`Voices-Opponent`), section focus → `Camera Movement` (kept under forced camera positions), `Camera Follow Pos` → `Camera Position`, `Add Camera Zoom`, `Play Animation`, `Change Scroll Speed`, `Alt Idle Animation`, BPM and time signature changes |
| `Hey!`, `Set GF Speed`, `Screen Shake`, `Change Character`, `Set Property`, `Play Sound`, `Alt Idle Animation` | `data/events/<name>.hx` + `.json` reimplementing Psych's built-in events in Codename |
| Custom `arrowSkin`, note types with their own `texture` | A song script that swaps the note and strum sprites, and the note type texture copied to `images/game/notes/<type>`, where Codename looks for it |
| `Hurt Note`, `Hey!`, `GF Sing` note types | `data/notes/<name>.hx` (avoided by the CPU, health loss on hit, no miss when skipped; hey/cheer animations; sung by the girlfriend) |
| `characters/<id>.json` | `data/characters/<id>.xml`: offsets divided by the scale, `sing_duration` → `holdTime`, camera offsets per side, spritesheets outside `images/characters/` copied there |
| `stages/<id>.json` (+ `stages/<id>.lua`) | `data/stages/<id>.xml` with sprites, boxes, quality filters (`low-memory`/`high-memory`), character spots and the pixel extension; sprites made in the stage Lua move into the XML and the rest of the Lua becomes `data/stages/<id>.hx` |
| `weeks/*.json` + `images/menucharacters/*.json` | `data/weeks/weeks/*.xml` (song folders with display names), `data/weeks/weeks.txt`, `data/weeks/characters/*.xml`, `data/config/freeplaySonglist.txt` |
| Lua and HScript | Codename HScript: Psych callbacks are bridged from Codename's (`goodNoteHitPre` → `onPlayerHit` with `return Function_Stop` cancelling), Lua sprites become shared `public var`s, `getProperty`/`setProperty`/tweens/timers/cameras/text/shaders/sounds/saves come with small Psych helper functions, `runHaxeCode` blocks are converted inline, `mustHitSection` reads the chart's camera events. Song scripts go to `songs/<song>/scripts/`, global ones to `songs/`, custom events to `data/events/`, note types to `data/notes/` |
| Mod | `data/config/modpack.ini` from `pack.json`, audio moved to `songs/<song>/song/`, `pack.png` → `icon.png`, assets copied |

---

## Multieditor

Every editor (Chart, Character, Stage and Week) has a **Multieditor** card: two or more people edit the same thing at the same time through the [online server](#online-server).

- **Rooms**: start a room and share its 6-letter code, or tick **List my room publicly** so anyone can find it in **Online → Rooms** (Android) or the **Online** page (Windows, Linux and macOS), filtered by editor. Joining from the list opens the right editor.
- **Live changes**: in the Chart Editor notes and events sync one by one, and anything else sends the whole chart; the other editors send their whole document (a character, a stage or a week) a few times per second while you work. Undo and redo stay local and sync like any other change. People who join later get the current state.
- **Shared files**: images, spritesheets and song audio you load are uploaded to the room and downloaded by everyone in it, so the previews match.
- **Presence**: who is in the room, where each person is in the song (Chart Editor) and a small chat.

## PlayTest

The **Test** section on Android and **PlayTest** in the sidebar on Windows, Linux and macOS play songs the way the game does, to check converted mods and your own projects.

- **What it opens**: a mod folder or archive (V-Slice, Psych or Codename; Psych and Codename mods go through the converter to V-Slice first, so the converter itself is tested), the result of any conversion (**PlayTest the result** under the output), the converted mod (**PlayTest the converted mod**), or the Chart Editor's chart (**PlayTest this chart**).
- **Gameplay**: notes scroll with the chart's speed (downscroll too), with Sick/Good/Bad/Shit timing windows, holds, misses, combo, score, accuracy, rating (PFC, GFC, FC…) and health. Play from any point of the song, or turn on **Botplay** to watch the chart play itself. On phones the screen is split into four lanes to tap; on a computer the arrow keys or D F J K hit the notes.
- **Stage and characters**: the song's stage with its props, the player, opponent and girlfriend placed on it singing (and missing) with their animations and dancing to the beat, and the camera following FocusCamera events.
- **Report**: problems found in the chart (stacked notes, holds running into notes, missing BPM, empty difficulties, events that need scripts), the audio, the characters (missing data, spritesheets or animations, prefixes with no frames), the stage (props without images or frames) and the conversion, plus timing hints after playing.

## Online server

The `server/` folder holds the V-Converter online server, written in JavaScript (Node.js 22.13+, Express, `ws` and the built-in SQLite). The apps talk to it once its address is typed in the options (the gear): **Server address** and **Send Error Reports** (off by default).

| Feature | In the apps | Server API |
| --- | --- | --- |
| Mod gallery | **Online** section on Android (Mods / Share tabs) and **Online** in the sidebar on Windows, Linux and macOS: search, filter by engine, sort, download, and upload a `.zip` with title, description, author, engine and tags. Uploading returns a delete key, the only way to remove the mod later | `GET /api/mods`, `GET /api/mods/:id`, `GET /api/mods/:id/download`, `POST /api/mods` (multipart, field `file`), `DELETE /api/mods/:id` (`X-Delete-Token`) |
| Updates and news | **News** tab: tells you when a newer V-Converter is out (from the latest GitHub release, with the download for your platform) and shows the server's announcements | `GET /api/version?platform=&current=`, `GET /api/announcements?platform=` |
| Error reports | When enabled, failed conversions and crashes send their message, stack trace, app version and platform | `POST /api/reports` |
| Multieditor | **Multieditor** card in every editor (see [Multieditor](#multieditor)): private or public rooms, live changes, shared images, spritesheets and audio, presence and chat | `GET /api/rooms?kind=` (public rooms), `POST /api/rooms`, `GET /api/rooms/:code`, `PUT`/`GET /api/rooms/:code/assets/<path>`, WebSocket `/ws?room=CODE&name=NAME` |

Run it:

```
cd server
npm ci
ADMIN_TOKEN=change-me npm start
```

or with Docker (`docker build -t v-converter-server server` and `docker run -p 3000:3000 -v vconverter-data:/data -e ADMIN_TOKEN=change-me v-converter-server`). Uploaded mods and the SQLite database live in `DATA_DIR`. Put it behind HTTPS (a reverse proxy or a host such as Render, Railway or Fly) and set `TRUST_PROXY=true` there so rate limits see the real client addresses.

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` / `HOST` | `3000` / `0.0.0.0` | Where it listens |
| `DATA_DIR` | `./data` | Uploaded mods and the database |
| `PUBLIC_URL` | (request host) | Base URL used in download links |
| `ADMIN_TOKEN` | (empty) | Enables moderation, announcements and reading reports (`Authorization: Bearer <token>`) |
| `TRUST_PROXY` | `false` | Trust `X-Forwarded-*` from a reverse proxy |
| `MAX_UPLOAD_MB` | `100` | Largest mod upload |
| `UPLOADS_PER_HOUR` / `REPORTS_PER_HOUR` / `ROOMS_PER_HOUR` | `10` / `60` / `30` | Per-address rate limits |
| `GITHUB_REPO` / `GITHUB_TOKEN` | `BrenninhoTools/V-Converter` / (empty) | Where the latest release is read from |
| `LATEST_VERSION` | (empty) | Version to report when GitHub can't be reached |
| `ROOM_IDLE_MINUTES` / `MAX_PEERS_PER_ROOM` / `MAX_MESSAGE_MB` | `120` / `16` / `8` | Live editing rooms |
| `ROOM_ASSETS_PER_HOUR` / `MAX_ROOM_ASSET_MB` / `MAX_ROOM_ASSETS_MB` | `600` / `40` / `200` | Files shared in rooms |

Admin calls: `POST /api/announcements` (`{"title", "body", "level": "info"|"warning"|"update", "url", "platforms": ["android"], "expiresAt"}`), `DELETE /api/announcements/:id`, `PATCH /api/mods/:id` (`{"hidden": true}`), `DELETE /api/mods/:id`, `GET /api/reports` and `DELETE /api/reports/:id`. `npm test` runs the server's tests, and the [Server workflow](https://github.com/BrenninhoTools/V-Converter/actions/workflows/server.yml) runs them on every change to `server/`.

## Building

Android (debug and release APKs):

```
./gradlew assembleDebug assembleRelease
```

Release APKs are signed with the keystore in `keystore/` (`v-converter.jks`, settings in `keystore/keystore.properties`), so every release build has the same signature and installs as an update over the previous one. Builds made before the keystore was added were signed with a debug key: uninstall that version once before installing a new release APK. The keystore is public in this repository, so anyone can sign an APK with it; only install the APKs from this repository's Actions runs.

Windows (installers and portable app, run on Windows with JDK 17):

```
./gradlew :desktop:packageMsi :desktop:packageExe :desktop:createDistributable
```

Linux (`.deb` package and portable app, run on Linux with JDK 17):

```
./gradlew :desktop:packageDeb :desktop:createDistributable
```

macOS (`.dmg` and `.app`, run on a Mac with JDK 17):

```
./gradlew :desktop:packageDmg :desktop:createDistributable
```

The desktop app (Windows, Linux and macOS) is a [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) build that shares every converter with the Android app.

### Credits

- Archives are extracted with `java.util.zip` (ZIP) and [7-Zip-JBinding-4Android](https://github.com/omicronapps/7-Zip-JBinding-4Android) / [7-Zip-JBinding](https://sevenzipjbind.sourceforge.net/) on Windows, Linux and Intel Macs (RAR, RAR5, 7z; on Apple Silicon Macs those archives need to be extracted first)
- The desktop Chart Editor plays `.ogg` and `.mp3` with [VorbisSPI](https://github.com/pdudits/soundlibs) and MP3SPI
- The UI uses the Friday Night Funkin' style font in `assets/fonts/funkin.ttf`

## References

- [FunkinCrew/Funkin](https://github.com/FunkinCrew/Funkin)
- [ShadowMario/FNF-PsychEngine](https://github.com/ShadowMario/FNF-PsychEngine)
- [CodenameCrew/CodenameEngine](https://github.com/CodenameCrew/CodenameEngine)
