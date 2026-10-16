# V-Converter

A remake of the V-Slice converter as a native Android app written in Kotlin. It converts Friday Night Funkin' content from **Psych Engine** to **V-Slice** (the current base game).

## Status

| Module | Psych → V-Slice | V-Slice → Psych |
| --- | --- | --- |
| Whole mod (folder / .zip / .rar / .7z) | ✅ | ✅ |
| Charts | ✅ | ✅ |
| Characters | ✅ | ✅ |
| Stages | ✅ | ✅ |
| Weeks ↔ Levels | ✅ | ✅ |
| Scripts | ✅ Lua / HScript → `.hxc` | ✅ `.hxc` → Psych HScript |

The app has two sections, switched at the top: **Converter** and **Generator** (new V-Slice content from scratch). The Converter works both ways: pick **Psych → V-Slice** or **V-Slice → Psych** under the section switch.

## Generator

| Tab | Creates |
| --- | --- |
| Mod | `_polymod_meta.json`, `_polymod_icon.png` and an optional starter module script |
| Song | `<song>-metadata.json` and empty `<song>-chart.json` (BPM, time signature, difficulties incl. an `erect` variation, characters, stage, note style), plus `Inst.ogg` / `Voices*.ogg` |
| Character | `data/characters/<id>.json` from a Sparrow XML, guessing `idle`, `singLEFT`…, misses and `danceLeft`/`danceRight` from frame prefixes |
| Stage | `data/stages/<id>.json` from your images (positions, scroll, scale, in front/behind), with the base game character positions |
| Level | `data/levels/<id>.json` with songs, background color, title image and base or custom menu characters |
| Script | `.hxc` classes: Module, Song, Stage, Character, Song event (with Chart Editor fields) and Note kind, with the callbacks you pick |

Every tab can download the result as a `.zip` or write it into the mod folder.

## Whole mod

The **Mod** tab converts an entire Psych Engine mod in one go, from its folder or from a `.zip`, `.rar` (RAR4 and RAR5) or `.7z` archive. The mod root is found automatically even when the archive wraps it in extra folders.

- `data/<song>/` charts (+ `events.json`) → `data/songs/<song>/`
- `characters/`, `stages/`, `weeks/` → `data/characters/`, `data/stages/`, `data/levels/`
- `images/`, `songs/`, `music/`, `sounds/`, `fonts/`, `videos/`, `shaders/` copied as-is
- Split vocals (`Voices-Player.ogg`, `Voices-Opponent.ogg` or a character's `vocals_file`) are wired through `playerVocals` / `opponentVocals`, so audio files keep their names
- Character slots are inferred from how the charts use each character
- `pack.json` / `pack.png` → `_polymod_meta.json` / `_polymod_icon.png`
- Lua and HScript scripts (`scripts/`, `data/<song>/`, `stages/`, `custom_events/`, `custom_notetypes/`) → `scripts/psych/*.hxc`

Every other tab also accepts archives and picks the files it needs from them.

## Charts

Pick one or more Psych Engine charts of the same song (`song-easy.json`, `song.json`, `song-hard.json`, …) and optionally its `events.json`. The app writes a V-Slice song folder:

```
<song-id>/
├── <song-id>-metadata.json
└── <song-id>-chart.json
```

Drop that folder inside `data/songs/` of your V-Slice mod.

### What gets converted

- Both the legacy format (pre-1.0, `mustHitSection` relative lanes) and `psych_v1` (absolute lanes)
- Every difficulty merged into one chart, each with its own scroll speed
- BPM changes rebuilt from section lengths into `timeChanges`
- Camera focus from `mustHitSection` / `gfSection` → `FocusCamera`
- Psych events:
  - `Hey!` → `PlayAnimation` (`hey`)
  - `Play Animation` → `PlayAnimation`
  - `Camera Follow Pos` → `FocusCamera` (position or back to section focus)
  - `Change Scroll Speed` → `ScrollSpeed` (relative, duration converted to steps)
- Note types → note kinds (`Alt Animation` → `alt`, `Hurt Note` → `hurt`, `Hey!` → `hey`, `No Animation` → `noanim`, custom types → slugged id), and `altAnim` sections → `alt` on opponent notes
- Stacked duplicate notes are removed
- Stage ids mapped to the V-Slice ones (`stage` → `mainStage`, `spooky` → `spookyMansion`, …); `school`/`schoolEvil` or pixel arrow skins → `pixel` note style

Other Psych events keep their own names in the chart (with `value1`/`value2`), so converted scripts still get them in `onEvent`. Built-in ones without a V-Slice event (`Add Camera Zoom`, `Screen Shake`, `Set Property`, `Play Sound`, `Set GF Speed`, `Alt Idle Animation`) also get a real `SongEvent` script in `scripts/events/`, which adds them to the Chart Editor. Anything that can't be converted yet is listed in the in-app report.

## Saving

Pick your V-Slice mod folder once (it is remembered). Converted files go straight to the right place:

```
<mod>/data/songs/<song-id>/<song-id>-metadata.json
<mod>/data/songs/<song-id>/<song-id>-chart.json
<mod>/data/characters/<id>.json
<mod>/data/stages/<id>.json
<mod>/data/levels/<id>.json
<mod>/scripts/psych/<Class>.hxc
```

## Characters

Pick Psych character `.json` files together with their spritesheet `.xml` files. With the XML the conversion is pixel-exact: V-Slice anchors characters at their feet (idle frame bottom-center) and scales animation offsets, while Psych anchors at the top-left of the first atlas frame, so offsets and camera are rebased using the real frame sizes.

- Animations, prefixes, indices, frame rate and loop
- Sparrow and multi-atlas (`multisparrow`) sheets
- Scale, flip, pixel (no antialiasing), health icon, sing duration, dance speed (`danceLeft`/`danceRight`)
- Camera position folded into `cameraOffsets`
- Slot (Player / Opponent / Girlfriend) decides how offsets are rebased

Not carried over: health bar colors and per-character vocal suffixes (V-Slice has no equivalent).

## Stages

- Character positions → V-Slice feet anchors, camera offsets (including Psych's built-in ±100/150 camera shifts), zoom and directory
- Psych 1.0 stage editor `objects` → props (sprites, animated sprites, solid squares), keeping layer order, scroll, scale, alpha, angle, color, flip and pixel mode
- Girlfriend scroll factor (0.95) preserved

Stages built only in Lua/HScript have no object list; their character layout converts and the report lists the images to recreate as props.

## Weeks

- Songs (ids in Psych's song path format), story name, background (`menubackgrounds/menu_<name>` or the default yellow) and title image (`storymenu/<week>`)
- Menu characters from `images/menucharacters/*.json` → level props, placed where Psych draws them; base `dad`/`bf`/`gf` map to V-Slice's own props
- Hidden story weeks → `visible: false`

Not carried over: lock chains (`weekBefore`), freeplay hiding, per-song freeplay icons/colors (listed in the report).

## Scripts

The **Scripts** tab (and the Mod tab) turns Psych Engine scripts into V-Slice scripted classes (`.hxc`).

- **Lua** is fully parsed (Lua 5.1 / LuaJIT syntax) and transpiled to HScript: locals, globals, closures, varargs, tables (arrays and maps), `pairs`/`ipairs`, numeric loops, `goto continue`, `and`/`or` with Lua truthiness, and the `string`, `table`, `math`, `os` and `bit` libraries, including Lua patterns (`find`, `match`, `gmatch`, `gsub`, `format`)
- **Psych HScript** (`.hx`) is wrapped into a module: imports are mapped (`states.PlayState` → `funkin.play.PlayState`, …), and `game.boyfriend`, `camFollow`, `defaultCamZoom`, `Conductor.songPosition`, `curBeat`, `ClientPrefs`… are rewritten to their V-Slice equivalents
- Every script becomes a `Module` that only runs where Psych would run it: its song (`data/<song>/`), its stage (`stages/`), charts that use its event (`custom_events/`) or note type (`custom_notetypes/`), or everywhere (`scripts/`)
- Psych callbacks are hooked to V-Slice events: `onCreate`, `onCreatePost`, `onUpdate`, `onBeatHit`, `onStepHit`, `onSectionHit`, `goodNoteHit`, `opponentNoteHit`, `noteMiss`, `noteMissPress`, `onEvent`, `onMoveCamera`, `onSpawnNote`, `onCountdownTick`, `onStartCountdown`, `onEndSong`, `onSongStart`, `onPause`, `onResume`, `onGameOver`, `onDestroy`, `onTimerCompleted`, `onTweenCompleted`, `onSoundFinished`; returning `Function_Stop` cancels the countdown and the song end like in Psych
- No runtime or helper classes are added: each module carries native V-Slice/Flixel code for exactly the Psych functions it uses (Lua sprites and texts, `getProperty`/`setProperty` paths, tweens, timers, sounds, camera, health, score, strum notes, shaders, saves, shared variables). Scripts of the same mod can still reach each other's Lua sprites
- Every class, field and event the generated code touches was checked against the FunkinCrew/Funkin source

The report lists, line by line, anything a script uses that has no V-Slice equivalent (`runHaxeCode`, videos, custom substates, metatables, `unspawnNotes` tricks) so those parts can be finished by hand.

## V-Slice → Psych

| Input | Output |
| --- | --- |
| `<song>-metadata.json` + `<song>-chart.json` | `data/<song>/<song>[-difficulty].json` (psych_v1): sections rebuilt from time changes (exact BPM changes), camera focus → `mustHitSection`/`gfSection`, note kinds → note types, `PlayAnimation`/`ScrollSpeed`/`ZoomCamera`/`FocusCamera` → Psych events |
| Variations (`-erect`…) | Their own Psych song (`<song>-erect`) with their own audio, in a freeplay-only week |
| `data/characters/<id>.json` (+ XML) | `characters/<id>.json`, positions and camera rebased exactly with the spritesheet |
| `data/stages/<id>.json` | `stages/<id>.json` with stage-editor objects, layer order and character markers |
| `data/levels/<id>.json` | `weeks/<id>.json` + `images/menucharacters/*.json` for custom props |
| `.hxc` scripts | Psych HScript: modules → `scripts/`, songs → `data/<song>/`, stages → `stages/`, characters → `characters/`, song events → `custom_events/` (+ `.txt`), note kinds → `custom_notetypes/` |
| Mod | `pack.json`/`pack.png`, `Voices-<char>.ogg` → `Voices-Player.ogg`/`Voices-Opponent.ogg`, assets copied |

Round trips (Psych → V-Slice → Psych) keep notes, sections, events, stage positions and the on-screen character placement.

## Building

```
./gradlew assembleDebug
```

GitHub Actions builds a debug APK on every push to `main`.

Archives are extracted with `java.util.zip` (ZIP) and [7-Zip-JBinding-4Android](https://github.com/omicronapps/7-Zip-JBinding-4Android) (RAR, RAR5, 7z).

The UI uses [Lilita One](https://fonts.google.com/specimen/Lilita+One) (SIL Open Font License, see `licenses/`).

## References

- [FunkinCrew/Funkin](https://github.com/FunkinCrew/Funkin)
- [ShadowMario/FNF-PsychEngine](https://github.com/ShadowMario/FNF-PsychEngine)
