# V-Converter

A remake of the V-Slice converter as a native Android app written in Kotlin. It converts Friday Night Funkin' content from **Psych Engine** to **V-Slice** (the current base game).

## Status

| Module | Status |
| --- | --- |
| Charts | ✅ Base converter |
| Characters | ✅ Converter |
| Stages | ✅ Converter |
| Week / Level data | ⏳ Planned |
| Audio layout | ⏳ Planned |

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

Anything that can't be converted yet is listed in the in-app report.

## Saving

Pick your V-Slice mod folder once (it is remembered). Converted files go straight to the right place:

```
<mod>/data/songs/<song-id>/<song-id>-metadata.json
<mod>/data/songs/<song-id>/<song-id>-chart.json
<mod>/data/characters/<id>.json
<mod>/data/stages/<id>.json
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

## Building

```
./gradlew assembleDebug
```

GitHub Actions builds a debug APK on every push to `main`.

The UI uses [Lilita One](https://fonts.google.com/specimen/Lilita+One) (SIL Open Font License, see `licenses/`).

## References

- [FunkinCrew/Funkin](https://github.com/FunkinCrew/Funkin)
- [ShadowMario/FNF-PsychEngine](https://github.com/ShadowMario/FNF-PsychEngine)
