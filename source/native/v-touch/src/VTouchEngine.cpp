#include "vtouch/VTouch.h"

#include <cmath>
#include <cstdlib>
#include <cstring>
#include <new>

namespace vtouch {

constexpr float kPi = 3.14159265358979323846f;
constexpr double kVelocityWindowMs = 100.0;
constexpr int kVelocitySamples = 8;

struct Vec2 {
    float x = 0.0f;
    float y = 0.0f;

    Vec2 operator+(const Vec2& o) const { return {x + o.x, y + o.y}; }
    Vec2 operator-(const Vec2& o) const { return {x - o.x, y - o.y}; }
    Vec2 operator*(float k) const { return {x * k, y * k}; }
    float length() const { return std::sqrt(x * x + y * y); }
};

struct Sample {
    Vec2 position;
    double time = 0.0;
};

struct Pointer {
    bool active = false;
    int64_t id = 0;
    Vec2 start;
    Vec2 position;
    double down_time = 0.0;
    int32_t lane = -1;
    Sample samples[kVelocitySamples];
    int sample_count = 0;
    int sample_head = 0;

    void record(Vec2 at, double time) {
        samples[sample_head] = {at, time};
        sample_head = (sample_head + 1) % kVelocitySamples;
        if (sample_count < kVelocitySamples)
            sample_count++;
    }

    Vec2 velocity(double now) const {
        const Sample* oldest = nullptr;
        const Sample* newest = nullptr;
        for (int i = 0; i < sample_count; ++i) {
            const Sample& s = samples[(sample_head - 1 - i + kVelocitySamples) % kVelocitySamples];
            if (now - s.time > kVelocityWindowMs)
                break;
            if (newest == nullptr)
                newest = &s;
            oldest = &s;
        }
        if (oldest == nullptr || newest == nullptr || newest->time - oldest->time < 1.0)
            return {};
        float seconds = static_cast<float>((newest->time - oldest->time) / 1000.0);
        return (newest->position - oldest->position) * (1.0f / seconds);
    }
};

class EventQueue {
public:
    bool push(const VTouchEvent& event) {
        if (count_ == VTOUCH_QUEUE_SIZE)
            return false;
        items_[(head_ + count_) % VTOUCH_QUEUE_SIZE] = event;
        count_++;
        return true;
    }

    bool pop(VTouchEvent* out) {
        if (count_ == 0)
            return false;
        *out = items_[head_];
        head_ = (head_ + 1) % VTOUCH_QUEUE_SIZE;
        count_--;
        return true;
    }

    int32_t size() const { return count_; }

    void clear() {
        head_ = 0;
        count_ = 0;
    }

private:
    VTouchEvent items_[VTOUCH_QUEUE_SIZE] = {};
    int32_t head_ = 0;
    int32_t count_ = 0;
};

struct Circle {
    Vec2 center;
    float radius = 0.0f;
    float rotation = 0.0f;
};

class Engine {
public:
    explicit Engine(const VTouchConfig& config) { configure(config); }

    void configure(const VTouchConfig& config) {
        VTouchConfig next = config;
        if (!(next.density > 0.0f))
            next.density = 1.0f;
        if (next.lanes < 1)
            next.lanes = 1;
        if (next.lanes > VTOUCH_MAX_LANES)
            next.lanes = VTOUCH_MAX_LANES;
        if (next.layout < VTOUCH_LAYOUT_HITBOX || next.layout > VTOUCH_LAYOUT_OFF)
            next.layout = VTOUCH_LAYOUT_HITBOX;
        bool lanes_changed = next.lanes != config_.lanes || next.layout != config_.layout;
        if (lanes_changed && configured_)
            releaseAllLanes();
        config_ = next;
        configured_ = true;
    }

    const VTouchConfig& config() const { return config_; }

    void setViewport(float width, float height) {
        width_ = width > 1.0f ? width : 1.0f;
        height_ = height > 1.0f ? height : 1.0f;
    }

    void setStep(float pixels) { config_.step_px = pixels; }

    void setEnabled(bool enabled) {
        if (enabled_ && !enabled)
            reset();
        enabled_ = enabled;
    }

    void pointer(int64_t id, int32_t action, Vec2 at, double time) {
        if (!enabled_)
            return;
        now_ = time;
        switch (action) {
            case VTOUCH_ACTION_DOWN:
                down(id, at, time);
                break;
            case VTOUCH_ACTION_MOVE:
                move(id, at, time);
                break;
            case VTOUCH_ACTION_UP:
                up(id, at, time, false);
                break;
            case VTOUCH_ACTION_CANCEL:
                up(id, at, time, true);
                break;
            default:
                break;
        }
    }

    void tick(double time) {
        now_ = time;
        if (!has(VTOUCH_FEATURE_LONG_PRESS) || long_fired_ || multi_ || dragging_)
            return;
        Pointer* only = single();
        if (only != nullptr && time - only->down_time >= config_.long_press_ms && (only->position - only->start).length() <= slop()) {
            long_fired_ = true;
            emit(VTOUCH_EVENT_LONG_PRESS, -1, only->position.x, only->position.y, static_cast<float>(time - only->down_time));
        }
    }

    void reset() {
        releaseAllLanes();
        for (Pointer& p : pointers_)
            p = Pointer();
        endGesture();
    }

    bool poll(VTouchEvent* out) { return queue_.pop(out); }
    int32_t pending() const { return queue_.size(); }

    int32_t held(int32_t lane) const {
        if (lane < 0 || lane >= VTOUCH_MAX_LANES)
            return 0;
        return held_[lane];
    }

    int32_t pointerCount() const {
        int32_t n = 0;
        for (const Pointer& p : pointers_)
            if (p.active)
                n++;
        return n;
    }

    bool dragging() const { return dragging_; }
    bool pinching() const { return pinching_; }

    int32_t laneAt(Vec2 at) const {
        switch (config_.layout) {
            case VTOUCH_LAYOUT_HITBOX: {
                if (at.x < 0.0f || at.y < 0.0f || at.x > width_ || at.y > height_)
                    return -1;
                int32_t lane = static_cast<int32_t>(at.x / width_ * static_cast<float>(config_.lanes));
                return lane < 0 ? 0 : (lane >= config_.lanes ? config_.lanes - 1 : lane);
            }
            case VTOUCH_LAYOUT_PAD: {
                int32_t best = -1;
                float best_distance = 0.0f;
                for (int32_t lane = 0; lane < config_.lanes; ++lane) {
                    Circle c = pad(lane);
                    float distance = (c.center - at).length();
                    if (distance <= c.radius * config_.pad_hit_scale && (best < 0 || distance < best_distance)) {
                        best = lane;
                        best_distance = distance;
                    }
                }
                return best;
            }
            default:
                return -1;
        }
    }

    bool shape(int32_t lane, float* out) const {
        if (lane < 0 || lane >= config_.lanes || out == nullptr || config_.layout == VTOUCH_LAYOUT_OFF)
            return false;
        if (config_.layout == VTOUCH_LAYOUT_HITBOX) {
            float w = width_ / static_cast<float>(config_.lanes);
            out[0] = w * static_cast<float>(lane);
            out[1] = 0.0f;
            out[2] = w;
            out[3] = height_;
            return true;
        }
        Circle c = pad(lane);
        out[0] = c.center.x;
        out[1] = c.center.y;
        out[2] = c.radius;
        out[3] = c.rotation;
        return true;
    }

private:
    bool has(uint32_t feature) const { return (config_.features & feature) != 0; }
    float slop() const { return config_.tap_slop_dp * config_.density; }

    Circle pad(int32_t lane) const {
        float base = (width_ < height_ ? width_ : height_) * 0.085f;
        if (config_.lanes == 4) {
            float gap = base * 2.25f;
            Vec2 middle{width_ - gap * 1.6f, height_ - gap * 1.4f};
            static const float rotations[4] = {0.0f, 270.0f, 90.0f, 180.0f};
            Vec2 offsets[4] = {{-gap, 0.0f}, {0.0f, gap * 0.62f}, {0.0f, -gap * 0.62f}, {gap, 0.0f}};
            return {middle + offsets[lane], base, rotations[lane]};
        }
        float radius = base * (config_.lanes > 6 ? 0.8f : 1.0f);
        float gap = radius * 2.3f;
        float total = gap * static_cast<float>(config_.lanes - 1);
        float right = width_ - radius * 1.8f;
        float left = right - total;
        if (left < radius * 1.8f)
            left = radius * 1.8f;
        float step = config_.lanes > 1 ? (right - left) / static_cast<float>(config_.lanes - 1) : 0.0f;
        float arc = height_ - radius * 2.2f;
        float t = config_.lanes > 1 ? static_cast<float>(lane) / static_cast<float>(config_.lanes - 1) : 0.5f;
        float lift = std::sin(t * kPi) * radius * 0.9f;
        return {{left + step * static_cast<float>(lane), arc - lift}, radius, 0.0f};
    }

    void emit(int32_t type, int32_t lane, float x, float y, float value = 0.0f, float dx = 0.0f, float dy = 0.0f) {
        queue_.push(VTouchEvent{type, lane, x, y, value, dx, dy});
    }

    Pointer* find(int64_t id) {
        for (Pointer& p : pointers_)
            if (p.active && p.id == id)
                return &p;
        return nullptr;
    }

    Pointer* single() {
        Pointer* found = nullptr;
        for (Pointer& p : pointers_) {
            if (!p.active)
                continue;
            if (found != nullptr)
                return nullptr;
            found = &p;
        }
        return found;
    }

    void press(int32_t lane, Vec2 at) {
        if (lane < 0 || lane >= VTOUCH_MAX_LANES)
            return;
        if (++held_[lane] == 1)
            emit(VTOUCH_EVENT_LANE_PRESS, lane, at.x, at.y);
    }

    void release(int32_t lane, Vec2 at) {
        if (lane < 0 || lane >= VTOUCH_MAX_LANES || held_[lane] == 0)
            return;
        if (--held_[lane] == 0)
            emit(VTOUCH_EVENT_LANE_RELEASE, lane, at.x, at.y);
    }

    void releaseAllLanes() {
        for (Pointer& p : pointers_) {
            if (p.active && p.lane >= 0) {
                release(p.lane, p.position);
                p.lane = -1;
            }
        }
        for (int32_t lane = 0; lane < VTOUCH_MAX_LANES; ++lane) {
            if (held_[lane] > 0) {
                held_[lane] = 0;
                emit(VTOUCH_EVENT_LANE_RELEASE, lane, 0.0f, 0.0f);
            }
        }
    }

    void updateLane(Pointer& p) {
        if (!has(VTOUCH_FEATURE_LANES))
            return;
        int32_t lane = laneAt(p.position);
        if (lane == p.lane)
            return;
        if (p.lane >= 0)
            release(p.lane, p.position);
        p.lane = lane;
        if (lane >= 0)
            press(lane, p.position);
    }

    void pinchSnapshot(Vec2* centroid, float* span) {
        Vec2 sum;
        int n = 0;
        for (const Pointer& p : pointers_) {
            if (!p.active)
                continue;
            sum = sum + p.position;
            n++;
        }
        *centroid = n > 0 ? sum * (1.0f / static_cast<float>(n)) : Vec2{};
        float total = 0.0f;
        for (const Pointer& p : pointers_)
            if (p.active)
                total += (p.position - *centroid).length();
        *span = n > 0 ? total / static_cast<float>(n) : 0.0f;
    }

    void down(int64_t id, Vec2 at, double time) {
        Pointer* p = find(id);
        if (p == nullptr) {
            for (Pointer& slot : pointers_) {
                if (!slot.active) {
                    p = &slot;
                    break;
                }
            }
        }
        if (p == nullptr)
            return;
        bool first = pointerCount() == 0;
        *p = Pointer();
        p->active = true;
        p->id = id;
        p->start = at;
        p->position = at;
        p->down_time = time;
        p->record(at, time);
        if (first) {
            gesture_start_ = time;
            total_ = {};
            vertical_ = 0.0f;
            long_fired_ = false;
            multi_ = false;
        } else {
            multi_ = true;
            if (dragging_) {
                dragging_ = false;
                emit(VTOUCH_EVENT_DRAG_END, -1, at.x, at.y);
            }
        }
        updateLane(*p);
        if (has(VTOUCH_FEATURE_PINCH) && pointerCount() >= 2) {
            pinching_ = true;
            pinchSnapshot(&pinch_centroid_, &pinch_span_);
        }
    }

    void move(int64_t id, Vec2 at, double time) {
        Pointer* p = find(id);
        if (p == nullptr)
            return;
        Vec2 delta = at - p->position;
        p->position = at;
        p->record(at, time);
        updateLane(*p);

        if (pinching_ && pointerCount() >= 2) {
            Vec2 centroid;
            float span = 0.0f;
            pinchSnapshot(&centroid, &span);
            if (pinch_span_ > 0.5f && span > 0.5f) {
                float zoom = span / pinch_span_;
                if (std::fabs(zoom - 1.0f) > 1e-4f)
                    emit(VTOUCH_EVENT_ZOOM, -1, centroid.x, centroid.y, zoom);
            }
            Vec2 pan = centroid - pinch_centroid_;
            if (pan.x != 0.0f || pan.y != 0.0f)
                emit(VTOUCH_EVENT_PAN, -1, centroid.x, centroid.y, 0.0f, pan.x, pan.y);
            pinch_centroid_ = centroid;
            pinch_span_ = span;
            return;
        }

        if (multi_ || single() != p)
            return;

        if (!dragging_ && (at - p->start).length() > slop() && (has(VTOUCH_FEATURE_STEPS) || has(VTOUCH_FEATURE_SWIPE_BACK))) {
            dragging_ = true;
            emit(VTOUCH_EVENT_DRAG_START, -1, p->start.x, p->start.y);
            delta = at - p->start;
        }

        if (!dragging_)
            return;

        total_ = total_ + delta;
        if (!has(VTOUCH_FEATURE_STEPS))
            return;
        if (std::fabs(total_.x) > std::fabs(total_.y) * 1.5f)
            return;
        vertical_ += delta.y;
        float size = config_.step_px < 24.0f ? 24.0f : config_.step_px;
        while (std::fabs(vertical_) > size) {
            emit(VTOUCH_EVENT_STEP, -1, at.x, at.y, vertical_ > 0.0f ? -1.0f : 1.0f);
            vertical_ -= vertical_ > 0.0f ? size : -size;
        }
    }

    void up(int64_t id, Vec2 at, double time, bool cancelled) {
        Pointer* p = find(id);
        if (p == nullptr)
            return;
        p->position = at;
        p->record(at, time);
        bool last = pointerCount() == 1;

        if (p->lane >= 0) {
            release(p->lane, at);
            p->lane = -1;
        }

        if (last && !cancelled && !multi_) {
            if (dragging_) {
                finishDrag(*p, time);
            } else if (has(VTOUCH_FEATURE_TAP) && !long_fired_ && (at - p->start).length() <= slop() &&
                       (!has(VTOUCH_FEATURE_LONG_PRESS) || time - p->down_time < config_.long_press_ms)) {
                emit(VTOUCH_EVENT_TAP, -1, at.x, at.y);
                if (last_tap_time_ >= 0.0 && time - last_tap_time_ <= config_.double_tap_ms &&
                    (at - last_tap_).length() <= slop() * 4.0f) {
                    emit(VTOUCH_EVENT_DOUBLE_TAP, -1, at.x, at.y);
                    last_tap_time_ = -1.0;
                } else {
                    last_tap_time_ = time;
                    last_tap_ = at;
                }
            }
        } else if (last && dragging_) {
            emit(VTOUCH_EVENT_DRAG_END, -1, at.x, at.y);
        }

        *p = Pointer();
        if (pointerCount() < 2 && pinching_) {
            pinching_ = false;
        } else if (pinching_) {
            pinchSnapshot(&pinch_centroid_, &pinch_span_);
        }
        if (pointerCount() == 0)
            endGesture();
    }

    void finishDrag(Pointer& p, double time) {
        float swipe = config_.swipe_back_dp * config_.density;
        if (has(VTOUCH_FEATURE_SWIPE_BACK) && total_.x > swipe && std::fabs(total_.x) > std::fabs(total_.y) * 2.0f)
            emit(VTOUCH_EVENT_SWIPE_BACK, -1, p.position.x, p.position.y, total_.x);

        if (has(VTOUCH_FEATURE_STEPS) && has(VTOUCH_FEATURE_FLICK) && std::fabs(total_.x) <= std::fabs(total_.y) * 1.5f) {
            Vec2 velocity = p.velocity(time);
            float threshold = config_.flick_dp_per_second * config_.density;
            if (threshold > 0.0f && std::fabs(velocity.y) > threshold) {
                int32_t steps = static_cast<int32_t>(std::fabs(velocity.y) / threshold);
                if (steps > config_.max_flick_steps)
                    steps = config_.max_flick_steps;
                float direction = velocity.y > 0.0f ? -1.0f : 1.0f;
                for (int32_t i = 0; i < steps; ++i)
                    emit(VTOUCH_EVENT_STEP, -1, p.position.x, p.position.y, direction, 0.0f, velocity.y);
            }
        }
        emit(VTOUCH_EVENT_DRAG_END, -1, p.position.x, p.position.y);
    }

    void endGesture() {
        dragging_ = false;
        pinching_ = false;
        multi_ = false;
        long_fired_ = false;
        total_ = {};
        vertical_ = 0.0f;
    }

    VTouchConfig config_{};
    bool configured_ = false;
    bool enabled_ = true;
    float width_ = 1.0f;
    float height_ = 1.0f;
    double now_ = 0.0;
    Pointer pointers_[VTOUCH_MAX_POINTERS];
    int32_t held_[VTOUCH_MAX_LANES] = {};
    EventQueue queue_;
    double gesture_start_ = 0.0;
    bool dragging_ = false;
    bool pinching_ = false;
    bool multi_ = false;
    bool long_fired_ = false;
    Vec2 total_;
    float vertical_ = 0.0f;
    Vec2 pinch_centroid_;
    float pinch_span_ = 0.0f;
    double last_tap_time_ = -1.0;
    Vec2 last_tap_;
};

}

struct VTouchEngine {
    vtouch::Engine engine;
    explicit VTouchEngine(const VTouchConfig& config) : engine(config) {}
};

extern "C" {

const char* vtouch_version(void) {
    return VTOUCH_VERSION_STRING;
}

void vtouch_config_init(VTouchConfig* config) {
    if (config == nullptr)
        return;
    std::memset(config, 0, sizeof(VTouchConfig));
    config->density = 1.0f;
    config->tap_slop_dp = 8.0f;
    config->double_tap_ms = 300.0f;
    config->long_press_ms = 500.0f;
    config->swipe_back_dp = 110.0f;
    config->step_px = 120.0f;
    config->flick_dp_per_second = 1400.0f;
    config->max_flick_steps = 4;
    config->pad_hit_scale = 1.35f;
    config->lanes = 4;
    config->layout = VTOUCH_LAYOUT_HITBOX;
    config->features = VTOUCH_FEATURE_LANES;
}

VTouchEngine* vtouch_create(const VTouchConfig* config) {
    VTouchConfig local;
    if (config == nullptr) {
        vtouch_config_init(&local);
        config = &local;
    }
    void* memory = std::malloc(sizeof(VTouchEngine));
    if (memory == nullptr)
        return nullptr;
    return new (memory) VTouchEngine(*config);
}

void vtouch_destroy(VTouchEngine* engine) {
    if (engine == nullptr)
        return;
    engine->~VTouchEngine();
    std::free(engine);
}

void vtouch_configure(VTouchEngine* engine, const VTouchConfig* config) {
    if (engine != nullptr && config != nullptr)
        engine->engine.configure(*config);
}

void vtouch_get_config(const VTouchEngine* engine, VTouchConfig* config) {
    if (engine != nullptr && config != nullptr)
        *config = engine->engine.config();
}

void vtouch_set_viewport(VTouchEngine* engine, float width, float height) {
    if (engine != nullptr)
        engine->engine.setViewport(width, height);
}

void vtouch_set_step(VTouchEngine* engine, float pixels) {
    if (engine != nullptr)
        engine->engine.setStep(pixels);
}

void vtouch_set_enabled(VTouchEngine* engine, int32_t enabled) {
    if (engine != nullptr)
        engine->engine.setEnabled(enabled != 0);
}

void vtouch_pointer(VTouchEngine* engine, int64_t id, int32_t action, float x, float y, double time_ms) {
    if (engine != nullptr && std::isfinite(x) && std::isfinite(y))
        engine->engine.pointer(id, action, vtouch::Vec2{x, y}, time_ms);
}

void vtouch_tick(VTouchEngine* engine, double time_ms) {
    if (engine != nullptr)
        engine->engine.tick(time_ms);
}

void vtouch_reset(VTouchEngine* engine) {
    if (engine != nullptr)
        engine->engine.reset();
}

int32_t vtouch_poll(VTouchEngine* engine, VTouchEvent* event) {
    if (engine == nullptr || event == nullptr)
        return 0;
    return engine->engine.poll(event) ? 1 : 0;
}

int32_t vtouch_pending(const VTouchEngine* engine) {
    return engine != nullptr ? engine->engine.pending() : 0;
}

int32_t vtouch_lane_held(const VTouchEngine* engine, int32_t lane) {
    return engine != nullptr ? engine->engine.held(lane) : 0;
}

int32_t vtouch_lane_at(const VTouchEngine* engine, float x, float y) {
    return engine != nullptr ? engine->engine.laneAt(vtouch::Vec2{x, y}) : -1;
}

int32_t vtouch_lane_shape(const VTouchEngine* engine, int32_t lane, float* shape) {
    return engine != nullptr && engine->engine.shape(lane, shape) ? 1 : 0;
}

int32_t vtouch_pointer_count(const VTouchEngine* engine) {
    return engine != nullptr ? engine->engine.pointerCount() : 0;
}

int32_t vtouch_is_dragging(const VTouchEngine* engine) {
    return engine != nullptr && engine->engine.dragging() ? 1 : 0;
}

int32_t vtouch_is_pinching(const VTouchEngine* engine) {
    return engine != nullptr && engine->engine.pinching() ? 1 : 0;
}

}
