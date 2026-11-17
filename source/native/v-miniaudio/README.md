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
- `vma_audio_load` / `vma_audio_load_memory`: WAV files (PCM 8/16/24/32-bit and float 32/64-bit); OGG, MP3 and FLAC are detected and reported as `VMA_ERROR_UNSUPPORTED_FORMAT`, so decode them first and pass PCM
- Per audio: play, pause, resume, stop, restart, seek, volume, pan, pitch (playback rate), looping and a finished callback
- `vma_render(context, output, frames)`: resamples each audio to the context rate, mixes, applies the master volume or mute and clamps the result to [-1, 1]
- The context is thread safe, so `vma_render` can run on an audio thread while another thread controls playback

Tests

```
cmake -S . -B build -DVMA_BUILD_TESTS=ON
cmake --build build
ctest --test-dir build
```

Features

- Written entirely in C11
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
