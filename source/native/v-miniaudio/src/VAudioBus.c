#include "VAudioInternal.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

static float vma_bus_clampf(float value, float minimum, float maximum) {
    if (!isfinite(value) || value < minimum)
        return minimum;
    if (value > maximum)
        return maximum;
    return value;
}

static void vma_biquad_free(VMABiquad* filter) {
    free(filter->state);
    filter->state = NULL;
    filter->enabled = false;
    filter->cutoff = 0.0f;
}

static VMAResult vma_biquad_set(VMABiquad* filter, bool highpass, float cutoff, uint32_t rate, uint32_t channels) {
    if (!isfinite(cutoff) || cutoff < 0.0f)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (cutoff == 0.0f) {
        vma_biquad_free(filter);
        return VMA_SUCCESS;
    }

    float nyquist = (float)rate * 0.49f;
    float frequency = vma_bus_clampf(cutoff, 10.0f, nyquist);

    if (filter->state == NULL) {
        filter->state = (float*)calloc((size_t)channels * 4, sizeof(float));
        if (filter->state == NULL)
            return VMA_ERROR_OUT_OF_MEMORY;
    }

    double w0 = 2.0 * 3.14159265358979323846 * frequency / (double)rate;
    double cosw = cos(w0);
    double alpha = sin(w0) / (2.0 * 0.70710678118654752);
    double a0 = 1.0 + alpha;
    double b0;
    double b1;

    if (highpass) {
        b0 = (1.0 + cosw) / 2.0;
        b1 = -(1.0 + cosw);
    } else {
        b0 = (1.0 - cosw) / 2.0;
        b1 = 1.0 - cosw;
    }

    filter->b0 = (float)(b0 / a0);
    filter->b1 = (float)(b1 / a0);
    filter->b2 = (float)(b0 / a0);
    filter->a1 = (float)(-2.0 * cosw / a0);
    filter->a2 = (float)((1.0 - alpha) / a0);
    filter->cutoff = frequency;
    filter->enabled = true;

    return VMA_SUCCESS;
}

static void vma_biquad_process(VMABiquad* filter, float* buffer, uint32_t frames, uint32_t channels) {
    if (!filter->enabled || filter->state == NULL)
        return;

    for (uint32_t c = 0; c < channels; ++c) {
        float* s = filter->state + (size_t)c * 4;
        float x1 = s[0], x2 = s[1], y1 = s[2], y2 = s[3];

        for (uint32_t i = 0; i < frames; ++i) {
            size_t at = (size_t)i * channels + c;
            float x = buffer[at];
            float y = filter->b0 * x + filter->b1 * x1 + filter->b2 * x2 - filter->a1 * y1 - filter->a2 * y2;

            if (!isfinite(y) || fabsf(y) < 1e-20f)
                y = 0.0f;

            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            buffer[at] = y;
        }

        s[0] = x1;
        s[1] = x2;
        s[2] = y1;
        s[3] = y2;
    }
}

void vma_buses_init(VMAContext* context) {
    for (uint32_t b = 0; b < VMA_MAX_BUSES; ++b) {
        memset(&context->buses[b], 0, sizeof(VMABus));
        context->buses[b].volume = 1.0f;
    }
}

void vma_buses_free(VMAContext* context) {
    for (uint32_t b = 0; b < VMA_MAX_BUSES; ++b) {
        VMABus* bus = &context->buses[b];
        vma_biquad_free(&bus->lowpass);
        vma_biquad_free(&bus->highpass);
        free(bus->echo_buffer);
        free(bus->mix);
        memset(bus, 0, sizeof(VMABus));
    }
}

bool vma_bus_needs_processing(const VMABus* bus) {
    return bus->lowpass.enabled || bus->highpass.enabled || bus->echo_buffer != NULL;
}

VMAResult vma_bus_reserve(VMABus* bus, size_t count) {
    if (bus->mix_size >= count)
        return VMA_SUCCESS;

    float* grown = (float*)realloc(bus->mix, count * sizeof(float));

    if (grown == NULL)
        return VMA_ERROR_OUT_OF_MEMORY;

    bus->mix = grown;
    bus->mix_size = count;

    return VMA_SUCCESS;
}

void vma_bus_process(VMABus* bus, float* buffer, uint32_t frames, uint32_t channels) {
    vma_biquad_process(&bus->highpass, buffer, frames, channels);
    vma_biquad_process(&bus->lowpass, buffer, frames, channels);

    if (bus->echo_buffer != NULL && bus->echo_frames > 0) {
        for (uint32_t i = 0; i < frames; ++i) {
            float* slot = bus->echo_buffer + bus->echo_cursor * channels;

            for (uint32_t c = 0; c < channels; ++c) {
                size_t at = (size_t)i * channels + c;
                float dry = buffer[at];
                float delayed = slot[c];
                buffer[at] = dry + delayed * bus->echo_mix;
                float feed = dry + delayed * bus->echo_feedback;
                slot[c] = isfinite(feed) && fabsf(feed) > 1e-20f ? feed : 0.0f;
            }

            bus->echo_cursor = (bus->echo_cursor + 1) % bus->echo_frames;
        }
    }

    float gain = bus->muted ? 0.0f : bus->volume;
    float peak = 0.0f;
    size_t count = (size_t)frames * channels;

    for (size_t i = 0; i < count; ++i) {
        buffer[i] *= gain;
        float magnitude = fabsf(buffer[i]);
        if (magnitude > peak)
            peak = magnitude;
    }

    bus->peak = peak;
}

void vma_master_process(VMAContext* context, float* output, uint32_t frames) {
    uint32_t channels = context->config.channels;
    float gain = context->muted ? 0.0f : context->master_volume;
    float lowest = 1.0f;
    float peak_left = 0.0f;
    float peak_right = 0.0f;
    double sum_left = 0.0;
    double sum_right = 0.0;

    for (uint32_t i = 0; i < frames; ++i) {
        float* frame = output + (size_t)i * channels;
        float loudest = 0.0f;

        for (uint32_t c = 0; c < channels; ++c) {
            float sample = frame[c] * gain;
            if (!isfinite(sample))
                sample = 0.0f;
            frame[c] = sample;
            float magnitude = fabsf(sample);
            if (magnitude > loudest)
                loudest = magnitude;
        }

        if (context->limiter_enabled) {
            float ceiling = context->limiter_ceiling;
            float target = loudest > ceiling ? ceiling / loudest : 1.0f;

            if (target < context->limiter_gain)
                context->limiter_gain = target;
            else
                context->limiter_gain = target + (context->limiter_gain - target) * context->limiter_release;

            if (context->limiter_gain < lowest)
                lowest = context->limiter_gain;
        }

        float applied = context->limiter_enabled ? context->limiter_gain : 1.0f;

        for (uint32_t c = 0; c < channels; ++c) {
            float sample = frame[c] * applied;

            if (sample > 1.0f)
                sample = 1.0f;
            else if (sample < -1.0f)
                sample = -1.0f;

            frame[c] = sample;
        }

        float left = fabsf(frame[0]);
        float right = channels > 1 ? fabsf(frame[1]) : left;

        if (left > peak_left)
            peak_left = left;
        if (right > peak_right)
            peak_right = right;

        sum_left += (double)frame[0] * frame[0];
        sum_right += channels > 1 ? (double)frame[1] * frame[1] : (double)frame[0] * frame[0];
    }

    if (frames > 0) {
        context->meter.peak_left = peak_left;
        context->meter.peak_right = peak_right;
        context->meter.rms_left = (float)sqrt(sum_left / frames);
        context->meter.rms_right = (float)sqrt(sum_right / frames);
        context->meter.gain_reduction = 1.0f - lowest;
    }

    context->rendered_frames += frames;
}

#define VMA_BUS_LOCKED(context, bus, body)                                  \
    do {                                                                    \
        if ((context) == NULL || !(context)->initialized || (bus) >= VMA_MAX_BUSES) \
            return VMA_ERROR_INVALID_ARGUMENT;                              \
        vma_mutex_lock(&(context)->lock);                                   \
        body                                                                \
        vma_mutex_unlock(&(context)->lock);                                 \
    } while (0)

VMAResult vma_bus_set_volume(VMAContext* context, uint32_t bus, float volume) {
    if (!isfinite(volume))
        return VMA_ERROR_INVALID_ARGUMENT;
    VMA_BUS_LOCKED(context, bus, {
        context->buses[bus].volume = vma_bus_clampf(volume, 0.0f, 4.0f);
    });
    return VMA_SUCCESS;
}

float vma_bus_get_volume(const VMAContext* context, uint32_t bus) {
    if (context == NULL || bus >= VMA_MAX_BUSES)
        return 0.0f;
    return context->buses[bus].volume;
}

VMAResult vma_bus_set_muted(VMAContext* context, uint32_t bus, bool muted) {
    VMA_BUS_LOCKED(context, bus, {
        context->buses[bus].muted = muted;
    });
    return VMA_SUCCESS;
}

VMAResult vma_bus_set_lowpass(VMAContext* context, uint32_t bus, float cutoff) {
    VMAResult result = VMA_SUCCESS;
    VMA_BUS_LOCKED(context, bus, {
        result = vma_biquad_set(&context->buses[bus].lowpass, false, cutoff, context->config.sample_rate, context->config.channels);
    });
    return result;
}

VMAResult vma_bus_set_highpass(VMAContext* context, uint32_t bus, float cutoff) {
    VMAResult result = VMA_SUCCESS;
    VMA_BUS_LOCKED(context, bus, {
        result = vma_biquad_set(&context->buses[bus].highpass, true, cutoff, context->config.sample_rate, context->config.channels);
    });
    return result;
}

VMAResult vma_bus_set_echo(VMAContext* context, uint32_t bus, float seconds, float feedback, float mix) {
    if (!isfinite(seconds) || !isfinite(feedback) || !isfinite(mix) || seconds < 0.0f)
        return VMA_ERROR_INVALID_ARGUMENT;

    VMAResult result = VMA_SUCCESS;
    VMA_BUS_LOCKED(context, bus, {
        VMABus* target = &context->buses[bus];
        uint32_t channels = context->config.channels;
        size_t frames = (size_t)(vma_bus_clampf(seconds, 0.0f, VMA_MAX_ECHO_SECONDS) * (float)context->config.sample_rate);

        if (frames == 0 || mix <= 0.0f) {
            free(target->echo_buffer);
            target->echo_buffer = NULL;
            target->echo_frames = 0;
            target->echo_cursor = 0;
        } else if (frames != target->echo_frames) {
            float* buffer = (float*)calloc(frames * channels, sizeof(float));
            if (buffer == NULL) {
                result = VMA_ERROR_OUT_OF_MEMORY;
            } else {
                free(target->echo_buffer);
                target->echo_buffer = buffer;
                target->echo_frames = frames;
                target->echo_cursor = 0;
            }
        }

        target->echo_seconds = seconds;
        target->echo_feedback = vma_bus_clampf(feedback, 0.0f, 0.95f);
        target->echo_mix = vma_bus_clampf(mix, 0.0f, 1.0f);
    });
    return result;
}

float vma_bus_get_peak(const VMAContext* context, uint32_t bus) {
    if (context == NULL || bus >= VMA_MAX_BUSES)
        return 0.0f;
    return context->buses[bus].peak;
}

VMAResult vma_set_limiter(VMAContext* context, bool enabled, float ceiling) {
    if (context == NULL || !context->initialized || !isfinite(ceiling))
        return VMA_ERROR_INVALID_ARGUMENT;
    vma_mutex_lock(&context->lock);
    context->limiter_enabled = enabled;
    context->limiter_ceiling = vma_bus_clampf(ceiling, 0.05f, 1.0f);
    context->limiter_gain = 1.0f;
    vma_mutex_unlock(&context->lock);
    return VMA_SUCCESS;
}

VMAResult vma_get_meter(const VMAContext* context, VMAMeter* meter) {
    if (context == NULL || meter == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;
    *meter = context->meter;
    return VMA_SUCCESS;
}

double vma_get_time(const VMAContext* context) {
    if (context == NULL || context->config.sample_rate == 0)
        return 0.0;
    return (double)context->rendered_frames / (double)context->config.sample_rate;
}
