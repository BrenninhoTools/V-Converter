# V-Converter

A remake of the V-Slice converter as a native Android app written in Kotlin. It converts Friday Night Funkin' content from **Psych Engine** and **Codename Engine** to **V-Slice** (the current base game), from V-Slice back to Psych, and between Psych and Codename in both directions.

## Status

| Module | Psych → V-Slice | V-Slice → Psych | Codename → V-Slice | Codename → Psych | Psych → Codename |
| --- | --- | --- | --- | --- | --- |
| Whole mod (folder / .zip / .rar / .7z) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Charts | ✅ | ✅ | ✅ | ✅ | ✅ |
| Characters | ✅ | ✅ | ✅ | ✅ | ✅ |
| Stages | ✅ | ✅ | ✅ | ✅ | ✅ |
| Weeks ↔ Levels | ✅ | ✅ | ✅ | ✅ | ✅ |
| Scripts | ✅ Lua / HScript → `.hxc` | ✅ `.hxc` → Psych HScript | ✅ Codename HScript → `.hxc` | ✅ Codename HScript → Psych HScript | ✅ Lua / HScript → Codename HScript |

The app has two sections, switched at the top: **Converter** and **Generator** (new V-Slice content from scratch). In the Converter, pick the source engine (**Psych**, **Codename** or **V-Slice**) and the target engine in the bridge at the top of each tab; tapping the **To** card or the swap button switches the target.

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
- Placeholder folders that hold nothing but a single text file (like a template's `readme.txt`) are left out automatically, in both directions
- `pack.json` / `pack.png` → `_polymod_meta.json` / `_polymod_icon.png`
- Lua and HScript scripts (`scripts/`, `data/<song>/`, `stages/`, `custom_events/`, `custom_notetypes/`) → `scripts/modules/*.hxc`

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

Other Psych events get a V-Slice style id (`Flash Camera` → `FlashCamera`, never clashing with built-in ids like `FocusCamera`), applied the same way in charts and scripts, while converted scripts still receive the original name in `onEvent`. `custom_events/*.lua`/`.hx` become `scripts/events/<Name>Event.hxc` holding a `SongEvent` (Chart Editor entry with Value 1/Value 2) and a `Module` with the converted code. Built-in Psych events without a V-Slice event (`Add Camera Zoom`, `Screen Shake`, `Set Property`, `Play Sound`, `Set GF Speed`, `Alt Idle Animation`) get a `SongEvent` script there too. Anything that can't be converted yet is listed in the in-app report.

## Saving

Pick your V-Slice mod folder once (it is remembered). Converted files go straight to the right place:

```
<mod>/data/songs/<song-id>/<song-id>-metadata.json
<mod>/data/songs/<song-id>/<song-id>-chart.json
<mod>/data/characters/<id>.json
<mod>/data/stages/<id>.json
<mod>/data/levels/<id>.json
<mod>/scripts/modules/<Class>.hxc
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
- The output is plain V-Slice HScript: no Lua-style leftovers (no `Function_*` constants, no `psych("…")` dispatch, no `g_`/`l_` prefixes). Script names keep their own names (`onUpdate` becomes `onUpdateScript` where it would clash with a `Module` method), and Psych calls become real functions such as `makeSprite(...)`. No runtime or helper classes are added: each module carries native V-Slice/Flixel code for exactly the Psych functions it uses (Lua sprites and texts, `getProperty`/`setProperty` paths, tweens, timers, sounds, camera, health, score, strum notes, shaders, saves, shared variables). Scripts of the same mod can still reach each other's Lua sprites
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

## Codename → V-Slice

| Input | Output |
| --- | --- |
| `songs/<song>/charts/<diff>.json` + `meta.json` + `events.json` | `<song>-metadata.json` + `<song>-chart.json`: player/opponent strumlines → lanes 0-3/4-7, note types → note kinds (`Alt Anim Note` → `alt` with a generated `NoteKind`), `BPM Change`/`Time Signature Change` → time changes, `Camera Movement`/`Camera Position` → `FocusCamera`, `Camera Zoom` → `ZoomCamera`, `Scroll Speed Change` → `ScrollSpeed`, `Camera Modulo Change` → `SetCameraBop`, `Play Animation` → `PlayAnimation` |
| Variants (`charts/<variant>/`, `meta-<variant>.json`) | V-Slice variations with their own `Inst-<variant>` audio |
| Other events (`Camera Flash`, `Add Camera Zoom`, `Camera Bop`, `HScript Call`, custom `data/events/*.json`) | Kept in the chart with named values, plus a `SongEvent` in `scripts/events/` (with a working implementation for the built-in ones) |
| `data/characters/<id>.xml` (+ spritesheet XML) | `data/characters/<id>.json`: offsets multiplied by the scale (Codename scales them, V-Slice does not), characters used on the other side mirrored like Codename does at runtime, `<id>-dead`/`gameOverChar` animations merged in for game over |
| `data/stages/<id>.xml` | `data/stages/<id>.json`: sprites, boxes, `high-memory` content, beat sprites (`danceEvery`), layer order and character spots; scaled sprites without `updateHitbox` are moved to where Codename draws them |
| `data/weeks/weeks/*.xml` + `data/weeks/characters/*.xml` | `data/levels/*.json` with menu props |
| `.hx` scripts | V-Slice modules scoped like Codename runs them (song, stage, stage extension, character, event, note type, global). `create`/`update`/`beatHit`/`onPlayerHit`/`onEvent`… become module events with Codename-style event objects; `boyfriend`, `camHUD`, `curBeat`, `Conductor.crochet`, stage sprites by name and so on are mapped to the V-Slice API |
| Mod | `_polymod_meta.json` from `data/config/modpack.ini`, `song/Inst.ogg`/`Voices*.ogg` moved to `songs/<song>/`, assets copied |

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

## Psych → Codename

| Input | Output |
| --- | --- |
| `data/<song>/<song>-<diff>.json` + `events.json` | `songs/<song>/charts/<diff>.json` + `meta.json`: opponent, player and (hidden) girlfriend strumlines, per-character vocals (`Voices-Player`/`Voices-Opponent`), section focus → `Camera Movement` (kept under forced camera positions), `Camera Follow Pos` → `Camera Position`, `Add Camera Zoom`, `Play Animation`, `Change Scroll Speed`, `Alt Idle Animation`, BPM and time signature changes |
| `Hey!`, `Set GF Speed`, `Screen Shake`, `Change Character`, `Set Property`, `Play Sound`, `Alt Idle Animation` | `data/events/<name>.hx` + `.json` reimplementing Psych's built-in events in Codename |
| `Hurt Note`, `Hey!`, `GF Sing` note types | `data/notes/<name>.hx` (avoided by the CPU, health loss on hit, no miss when skipped; hey/cheer animations; sung by the girlfriend) |
| `characters/<id>.json` | `data/characters/<id>.xml`: offsets divided by the scale, `sing_duration` → `holdTime`, camera offsets per side, spritesheets outside `images/characters/` copied there |
| `stages/<id>.json` (+ `stages/<id>.lua`) | `data/stages/<id>.xml` with sprites, boxes, quality filters (`low-memory`/`high-memory`), character spots and the pixel extension; sprites made in the stage Lua move into the XML and the rest of the Lua becomes `data/stages/<id>.hx` |
| `weeks/*.json` + `images/menucharacters/*.json` | `data/weeks/weeks/*.xml` (song folders with display names), `data/weeks/weeks.txt`, `data/weeks/characters/*.xml`, `data/config/freeplaySonglist.txt` |
| Lua and HScript | Codename HScript: Psych callbacks are bridged from Codename's (`goodNoteHitPre` → `onPlayerHit` with `return Function_Stop` cancelling), Lua sprites become shared `public var`s, `getProperty`/`setProperty`/tweens/timers/cameras/text/shaders/sounds/saves come with small Psych helper functions, `runHaxeCode` blocks are converted inline, `mustHitSection` reads the chart's camera events. Song scripts go to `songs/<song>/scripts/`, global ones to `songs/`, custom events to `data/events/`, note types to `data/notes/` |
| Mod | `data/config/modpack.ini` from `pack.json`, audio moved to `songs/<song>/song/`, `pack.png` → `icon.png`, assets copied |

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
- [CodenameCrew/CodenameEngine](https://github.com/CodenameCrew/CodenameEngine)
