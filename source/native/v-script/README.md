# V-Script

The script converter and generator of V-Converter, written in C++17 with a C API (`include/vscript/VScript.h`). It reads Psych Engine Lua and writes the V-Slice class each script belongs to, with the Psych calls rewritten as the V-Slice code that does the same thing. Nothing from Psych is carried over: no `setProperty`, no tag tables, no callback dispatcher.

| Psych | V-Slice |
| --- | --- |
| `stages/<id>.lua` | `class ...Stage extends Stage`, sprites built in `onCreate` and layered with `zIndex` + `refresh()` |
| `data/<song>/*.lua` | `class ...Song extends Song`, built once per game in `onCountdownStart` |
| `custom_notetypes/<type>.lua` | `class ...NoteKind extends NoteKind`, only the note callbacks it can get |
| `custom_events/<event>.lua` | `class ...Event extends SongEvent` with `handleEvent`, `getTitle` and `getEventSchema` |
| `scripts/*.lua` | `class ... extends Module`, guarded so it only runs in gameplay (or in its song or stage) |
| `makeLuaSprite('bg', ...)` | a typed `var bg:FunkinSprite` field and `new FunkinSprite(...)` |
| `setProperty('dad.x', v)` | `PlayState.instance.currentStage.getDad().x = v` |
| `doTweenAlpha('t', 'bg', 0, 1, 'quadOut')` | `FlxTween.tween(bg, {alpha: 0}, 1, {ease: FlxEase.quadOut})` |
| `onBeatHit()`, `onUpdate(elapsed)` | `override public function onBeatHit(event:SongTimeScriptEvent)`, `onUpdate(event:UpdateScriptEvent)` |
| `goodNoteHit` / `opponentNoteHit` | one `onNoteHit` split on `event.note.noteData.getMustHitNote()` |
| `onEvent(name, v1, v2)` | `onSongEvent`, with event names compared by their V-Slice ids |
| `return Function_Stop` | `event.cancel()` |

Lua itself is parsed into a full syntax tree (Lua 5.1/LuaJIT with `goto`), so tables become arrays, anonymous objects or `StringMap`s from how the script uses them, `ipairs`/`pairs` loops become Haxe loops, 1-based indexes are shifted, `..` becomes string concatenation and `and`/`or` value tricks become ternaries. Anything without a V-Slice match is listed in the report with its line.

`vscript_generate` writes new V-Slice scripts from a JSON spec, and `vscript_builtin_event` writes the SongEvent classes for Psych's built-in events.

```
cmake -S . -B build -DVSCRIPT_BUILD_TESTS=ON
cmake --build build
ctest --test-dir build
```
