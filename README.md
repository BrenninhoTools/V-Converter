# V-Converter

A remake of the V-Slice converter as a native Android app written in Kotlin. It converts Friday Night Funkin' content from **Psych Engine** to **V-Slice** (the current base game).

## Status

| Module | Status |
| --- | --- |
| Charts | ✅ Base converter |
| Characters | ⏳ Planned |
| Stages | ⏳ Planned |
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

## Building

```
./gradlew assembleDebug
```

GitHub Actions builds debug and release APKs on every push.

## References

- [FunkinCrew/Funkin](https://github.com/FunkinCrew/Funkin)
- [ShadowMario/FNF-PsychEngine](https://github.com/ShadowMario/FNF-PsychEngine)
