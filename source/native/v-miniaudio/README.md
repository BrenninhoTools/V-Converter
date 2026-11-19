# V-MiniAudio

A lightweight audio framework developed from scratch in C11, focused on audio processing and control.

How It Works

V-MiniAudio uses an audio engine to process PCM audio data through buffers.

The audio system is divided into three main components:

- VAudio: Manages audio initialization, configuration, volume and errors.
- VAudioEngine: Handles audio buffers, mixing, gain and audio processing.
- VAudioPlayer: Will manage audio playback, pausing, stopping and looping.

Audio System

The audio processing system follows a simple structure:

Audio Source
     |
     v
Audio Player
     |
     v
Audio Engine
     |
     v
PCM Processing
     |
     v
Audio Output

The engine currently supports PCM buffer processing using 32-bit floating-point samples.

V-MiniAudio does not open audio devices itself: the host calls `vma_render` from its own output callback or thread (AudioTrack, AAudio, WASAPI, CoreAudio, a SourceDataLine…) and V-MiniAudio fills the buffer with every playing audio mixed together.

Audio sources

- `vma_audio_create_pcm`: interleaved PCM in U8, S16, S24, S32, F32 or F64
- `vma_audio_load` / `vma_audio_load_memory`: WAV (PCM 8/16/24/32-bit and float 32/64-bit) and Ogg Vorbis (through the bundled stb_vorbis, turned off with `-DVMA_ENABLE_VORBIS=OFF`). MP3 and FLAC are detected and reported as `VMA_ERROR_UNSUPPORTED_FORMAT`
- `vma_decode_memory`: decodes a WAV or Ogg file to float PCM without a context; free the result with `vma_free`

Playback

- Per audio: play, pause, resume, stop, restart, seek, volume, pan, pitch (playback rate), looping and a finished callback
- `vma_audio_fade(audio, target, seconds, end)`: smooth volume ramps that can pause or stop the audio when they finish
- `vma_audio_set_loop_points(audio, start, end)`: loop only a part of the audio, like a music loop after its intro
- `vma_audio_play_delayed(audio, seconds)`: sample-accurate scheduled start
- `vma_audio_get_peak(audio)`: loudest sample of the last block
- `vma_set_interpolation`: nearest, linear or 4-point cubic (Hermite) resampling; cubic is the default
- `vma_set_pan_law`: linear or equal-power panning

Mixing

- 8 buses. `vma_audio_set_bus` sends an audio to a bus; every bus has volume, mute, a low-pass and a high-pass filter (biquad, 12 dB/octave), an echo (delay up to 2 seconds with feedback and mix) and a peak meter
- Master: volume, mute, a peak limiter (`vma_set_limiter`, on by default with a ceiling of 1.0) that turns loud mixes down smoothly instead of clipping, then a final clamp to [-1, 1]
- `vma_get_meter`: peak and RMS of the left and right output and how much the limiter turned the mix down
- `vma_get_time`: seconds rendered since the context was created, for syncing
- `vma_render(context, output, frames)`: resamples each audio to the context rate, mixes the buses, applies the master and fills the buffer
- The context is thread safe, so `vma_render` can run on an audio thread while another thread controls playback

Tests

```
cmake -S . -B build -DVMA_BUILD_TESTS=ON
cmake --build build
ctest --test-dir build
```

Features

- Written entirely in C11 (stb_vorbis is bundled for Ogg)
- Lightweight audio processing
- PCM audio buffers
- Audio mixing
- Volume control
- Audio callbacks
- Cross-platform architecture
- No external audio libraries

Platforms

V-MiniAudio is designed for Windows, Linux, macOS and Android.

Platform-specific audio backends are planned.

License

See the LICENSE file for more information.
