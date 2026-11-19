# UI skins

The title screen, the main menu and the back button are drawn from these files. Change a value, rebuild, and the app follows it. A missing or broken file falls back to the built-in look, so a typo never breaks the menus.

Positions are in the 1280x720 FNF screen. `image` paths have no extension: `images/ui/title/logoBumpin` loads `logoBumpin.png` and, when it exists, `logoBumpin.xml` (Sparrow). Without an `.xml` the whole `.png` is drawn. Colors are `#RRGGBB` or `#AARRGGBB`.

## Sprites

Used by `sprites` in `title.json` and `mainMenu.json`, and by `intro.sprites` in `title.json`.

| Key | Meaning |
| --- | --- |
| `id` | Name, used by intro `show` / `hide` events |
| `image` | Path without extension |
| `position` | `[x, y]`, measured from the `anchor` edge inward |
| `anchor` | `topLeft`, `top`, `topRight`, `left`, `center`, `right`, `bottomLeft`, `bottom`, `bottomRight` |
| `scale` | Number or `[x, y]` |
| `alpha`, `visible`, `flipX` | Look |
| `color` | Tint multiplied over the image |
| `layer` | Draw order. In the main menu, negative layers go behind the buttons |
| `scrollFactor` | Main menu only: how much it follows the camera |
| `animations` | List of `{ "name", "prefix", "fps", "loop", "indices", "offset": [x, y] }` |
| `play` | Animation that loops all the time |
| `beat` | Animations played in turn on every beat (`["danceLeft", "danceRight"]`) |
| `pressed` | Title only: animation played after Enter |

## title.json

`bpm`, `background`, `confirmDelay` (seconds from Enter to the main menu), `flash` (`color`, `skip` and `confirm` fade seconds) and `atlasScale` (multiplies every sprite).

`intro` holds `textFile` (the jokes, `first--second` per line), `length` (beat that skips to the title), `top`, `spacing` and `scale` of the text, plus `events`:

| `action` | Does |
| --- | --- |
| `set` | Replaces the text with `text` |
| `add` | Adds the lines in `text` |
| `clear` | Removes the text and the intro sprites |
| `show` / `hide` | Shows or hides the `intro.sprites` whose ids are in `text` |

`{wacky0}` and `{wacky1}` are the two halves of the random joke, `{wacky}` the whole joke.

## mainMenu.json

`background` and `flash` take `image`, `scale`, `scrollFactor`, `color` and `alpha`; `flash` also takes `interval` and `duration` in milliseconds, and `"flash": null` turns it off. `camera.stiffness` sets how fast the camera follows.

`items` takes `folder`, `anchor`, `offset`, `spacing`, `scale`, `fps` and the animation names `idle` and `selected`, where `{label}` and `{id}` are replaced. `items.overrides` changes one button by id: `{ "online": { "image": "...", "idle": "...", "selected": "...", "offset": [x, y], "scale": 1.2 } }`.

`confirm` sets `blinkInterval`, `blinkDuration` and `fadeMillis`. `order` lists ids to put first and `hidden` ids to remove (`converter`, `generator`, `online`, `options`). `version` takes `text` (`{version}` is the app version), `anchor`, `x`, `y`, `size`, `color`, `shadow` (`false` turns it off) and `visible`.

## backButton.json

`image`, `animation` (`prefix`, `fps`, `playOnPress`), `anchor`, `position`, `scale`, `alpha`, `hitPadding` (extra touch area) and `visible`.
