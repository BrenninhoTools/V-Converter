#include "vtouch/VTouch.h"

#include <cmath>
#include <cstdio>

static int failures = 0;

#define CHECK(condition)                                                                       \
    do {                                                                                       \
        if (!(condition)) {                                                                    \
            std::fprintf(stderr, "%s:%d: check failed: %s\n", __FILE__, __LINE__, #condition); \
            failures++;                                                                        \
        }                                                                                      \
    } while (0)

static int drain(VTouchEngine* engine, int type, VTouchEvent* last = nullptr) {
    VTouchEvent event;
    int found = 0;
    while (vtouch_poll(engine, &event)) {
        if (event.type == type) {
            found++;
            if (last != nullptr)
                *last = event;
        }
    }
    return found;
}

static VTouchEngine* make(unsigned features, int layout = VTOUCH_LAYOUT_HITBOX, int lanes = 4) {
    VTouchConfig config;
    vtouch_config_init(&config);
    config.features = features;
    config.layout = layout;
    config.lanes = lanes;
    VTouchEngine* engine = vtouch_create(&config);
    vtouch_set_viewport(engine, 1000.0f, 500.0f);
    return engine;
}

static void lanes() {
    VTouchEngine* e = make(VTOUCH_FEATURE_LANES);
    vtouch_pointer(e, 1, VTOUCH_ACTION_DOWN, 100, 100, 0);
    VTouchEvent event;
    CHECK(vtouch_poll(e, &event) && event.type == VTOUCH_EVENT_LANE_PRESS && event.lane == 0);
    vtouch_pointer(e, 2, VTOUCH_ACTION_DOWN, 120, 300, 5);
    CHECK(vtouch_pending(e) == 0);
    CHECK(vtouch_lane_held(e, 0) == 2);
    vtouch_pointer(e, 1, VTOUCH_ACTION_MOVE, 600, 100, 10);
    CHECK(vtouch_poll(e, &event) && event.type == VTOUCH_EVENT_LANE_PRESS && event.lane == 2);
    CHECK(vtouch_lane_held(e, 0) == 1);
    vtouch_pointer(e, 2, VTOUCH_ACTION_UP, 120, 300, 20);
    CHECK(vtouch_poll(e, &event) && event.type == VTOUCH_EVENT_LANE_RELEASE && event.lane == 0);
    vtouch_reset(e);
    CHECK(vtouch_poll(e, &event) && event.type == VTOUCH_EVENT_LANE_RELEASE && event.lane == 2);
    CHECK(vtouch_lane_held(e, 2) == 0);

    float shape[4];
    CHECK(vtouch_lane_shape(e, 3, shape) && std::fabs(shape[0] - 750.0f) < 0.01f && std::fabs(shape[2] - 250.0f) < 0.01f);
    CHECK(vtouch_lane_at(e, 999, 10) == 3);
    CHECK(vtouch_lane_at(e, -5, 10) == -1);
    vtouch_destroy(e);

    VTouchEngine* pad = make(VTOUCH_FEATURE_LANES, VTOUCH_LAYOUT_PAD);
    for (int lane = 0; lane < 4; ++lane) {
        CHECK(vtouch_lane_shape(pad, lane, shape));
        CHECK(vtouch_lane_at(pad, shape[0], shape[1]) == lane);
    }
    CHECK(vtouch_lane_at(pad, 10, 10) == -1);
    vtouch_destroy(pad);

    VTouchEngine* seven = make(VTOUCH_FEATURE_LANES, VTOUCH_LAYOUT_PAD, 7);
    for (int lane = 0; lane < 7; ++lane) {
        CHECK(vtouch_lane_shape(seven, lane, shape));
        CHECK(vtouch_lane_at(seven, shape[0], shape[1]) == lane);
    }
    CHECK(!vtouch_lane_shape(seven, 7, shape));
    vtouch_destroy(seven);
}

static void taps() {
    VTouchEngine* e = make(VTOUCH_FEATURE_TAP | VTOUCH_FEATURE_LONG_PRESS | VTOUCH_FEATURE_STEPS);
    vtouch_pointer(e, 1, VTOUCH_ACTION_DOWN, 300, 200, 0);
    vtouch_pointer(e, 1, VTOUCH_ACTION_MOVE, 303, 202, 30);
    vtouch_pointer(e, 1, VTOUCH_ACTION_UP, 303, 202, 80);
    VTouchEvent tap;
    CHECK(drain(e, VTOUCH_EVENT_TAP, &tap) == 1 && tap.x == 303.0f);
    vtouch_pointer(e, 1, VTOUCH_ACTION_DOWN, 305, 205, 200);
    vtouch_pointer(e, 1, VTOUCH_ACTION_UP, 305, 205, 250);
    VTouchEvent event;
    int taps = 0, doubles = 0;
    while (vtouch_poll(e, &event)) {
        taps += event.type == VTOUCH_EVENT_TAP;
        doubles += event.type == VTOUCH_EVENT_DOUBLE_TAP;
    }
    CHECK(taps == 1 && doubles == 1);

    vtouch_pointer(e, 1, VTOUCH_ACTION_DOWN, 300, 200, 1000);
    vtouch_tick(e, 1300);
    CHECK(vtouch_pending(e) == 0);
    vtouch_tick(e, 1600);
    CHECK(drain(e, VTOUCH_EVENT_LONG_PRESS) == 1);
    vtouch_pointer(e, 1, VTOUCH_ACTION_UP, 300, 200, 1700);
    CHECK(drain(e, VTOUCH_EVENT_TAP) == 0);

    vtouch_pointer(e, 1, VTOUCH_ACTION_DOWN, 300, 200, 2000);
    vtouch_pointer(e, 1, VTOUCH_ACTION_MOVE, 300, 260, 2050);
    vtouch_pointer(e, 1, VTOUCH_ACTION_UP, 300, 260, 2100);
    CHECK(drain(e, VTOUCH_EVENT_TAP) == 0);
    vtouch_destroy(e);
}

static void steps() {
    VTouchEngine* e = make(VTOUCH_FEATURE_TAP | VTOUCH_FEATURE_STEPS | VTOUCH_FEATURE_SWIPE_BACK | VTOUCH_FEATURE_FLICK);
    vtouch_set_step(e, 50.0f);
    vtouch_pointer(e, 1, VTOUCH_ACTION_DOWN, 300, 100, 0);
    for (int i = 1; i <= 20; ++i)
        vtouch_pointer(e, 1, VTOUCH_ACTION_MOVE, 300, 100.0f + i * 10.0f, i * 50.0);
    CHECK(vtouch_is_dragging(e));
    VTouchEvent event;
    int up = 0, down = 0, starts = 0;
    while (vtouch_poll(e, &event)) {
        if (event.type == VTOUCH_EVENT_STEP) {
            if (event.value < 0) up++; else down++;
        }
        starts += event.type == VTOUCH_EVENT_DRAG_START;
    }
    CHECK(starts == 1 && up == 3 && down == 0);
    vtouch_pointer(e, 1, VTOUCH_ACTION_UP, 300, 300, 1050);
    CHECK(drain(e, VTOUCH_EVENT_SWIPE_BACK) == 0);

    vtouch_pointer(e, 1, VTOUCH_ACTION_DOWN, 100, 200, 2000);
    vtouch_pointer(e, 1, VTOUCH_ACTION_MOVE, 200, 205, 2050);
    vtouch_pointer(e, 1, VTOUCH_ACTION_MOVE, 260, 210, 2100);
    vtouch_pointer(e, 1, VTOUCH_ACTION_UP, 260, 210, 2120);
    VTouchEvent swipe;
    CHECK(drain(e, VTOUCH_EVENT_SWIPE_BACK, &swipe) == 1 && swipe.value > 150.0f);

    vtouch_pointer(e, 1, VTOUCH_ACTION_DOWN, 300, 400, 3000);
    vtouch_pointer(e, 1, VTOUCH_ACTION_MOVE, 300, 380, 3010);
    vtouch_pointer(e, 1, VTOUCH_ACTION_MOVE, 300, 330, 3030);
    vtouch_pointer(e, 1, VTOUCH_ACTION_UP, 300, 290, 3050);
    int flicked = 0;
    while (vtouch_poll(e, &event))
        if (event.type == VTOUCH_EVENT_STEP && event.dy != 0.0f)
            flicked++;
    CHECK(flicked >= 1 && flicked <= 4);
    vtouch_destroy(e);
}

static void pinch() {
    VTouchEngine* e = make(VTOUCH_FEATURE_PINCH | VTOUCH_FEATURE_TAP | VTOUCH_FEATURE_STEPS);
    vtouch_pointer(e, 1, VTOUCH_ACTION_DOWN, 400, 250, 0);
    vtouch_pointer(e, 2, VTOUCH_ACTION_DOWN, 600, 250, 10);
    CHECK(vtouch_is_pinching(e));
    vtouch_pointer(e, 1, VTOUCH_ACTION_MOVE, 300, 250, 20);
    vtouch_pointer(e, 2, VTOUCH_ACTION_MOVE, 700, 250, 20);
    VTouchEvent event;
    float zoom = 1.0f;
    float pan = 0.0f;
    while (vtouch_poll(e, &event)) {
        if (event.type == VTOUCH_EVENT_ZOOM)
            zoom *= event.value;
        if (event.type == VTOUCH_EVENT_PAN)
            pan += event.dx;
        CHECK(event.type != VTOUCH_EVENT_STEP && event.type != VTOUCH_EVENT_TAP);
    }
    CHECK(std::fabs(zoom - 2.0f) < 0.01f);
    CHECK(std::fabs(pan) < 0.01f);
    vtouch_pointer(e, 1, VTOUCH_ACTION_UP, 300, 250, 40);
    vtouch_pointer(e, 2, VTOUCH_ACTION_UP, 700, 250, 50);
    CHECK(drain(e, VTOUCH_EVENT_TAP) == 0);
    CHECK(!vtouch_is_pinching(e) && vtouch_pointer_count(e) == 0);
    vtouch_destroy(e);
}

int main() {
    CHECK(vtouch_version()[0] == '1');
    lanes();
    taps();
    steps();
    pinch();
    if (failures == 0)
        std::printf("V-Touch tests passed\n");
    return failures == 0 ? 0 : 1;
}
