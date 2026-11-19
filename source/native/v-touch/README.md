# V-Touch

The touch input engine of V-Converter, written in C++17 with a C API (`include/vtouch/VTouch.h`). It uses no exceptions, no RTTI and no standard library containers, so the compiled code only needs the C library: it links the same way on Android, Windows, Linux, macOS and iOS.

The app feeds it raw pointer events (`vtouch_pointer` with down, move, up or cancel, a position and a time) and polls the gestures it recognized (`vtouch_poll`). One engine serves one surface and only runs the recognizers turned on in `VTouchConfig.features`:

| Feature | Events |
| --- | --- |
| `LANES` | `LANE_PRESS` / `LANE_RELEASE` for 1 to 9 lanes, as a hitbox (equal columns) or a virtual pad (the FNF arrow diamond for 4 lanes, an arc of buttons for other counts). Fingers can slide between lanes and several fingers can hold the same lane |
| `TAP` | `TAP` and `DOUBLE_TAP` |
| `LONG_PRESS` | `LONG_PRESS` after `long_press_ms` without moving; call `vtouch_tick` while a finger is down |
| `STEPS` | `DRAG_START`, `STEP` (+1 / -1 every `step_px` of vertical drag) and `DRAG_END` |
| `FLICK` | extra `STEP`s after a fast vertical swipe, up to `max_flick_steps` |
| `SWIPE_BACK` | `SWIPE_BACK` after a mostly horizontal swipe to the right longer than `swipe_back_dp` |
| `PINCH` | `ZOOM` (scale factor around the fingers' centroid) and `PAN` with two or more fingers |

`vtouch_lane_shape` returns where each lane is so the UI draws exactly what the engine hit-tests.

On Android and desktop V-Converter loads it through JNI (`libvconverter_touch`), on iOS it is compiled into a static library and bound with cinterop, and the web build runs `KotlinTouchEngine`, a line-by-line Kotlin port that the tests compare against the C++ engine.

```
cmake -S . -B build -DVTOUCH_BUILD_TESTS=ON
cmake --build build
ctest --test-dir build
```
