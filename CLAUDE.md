# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests on device
./gradlew clean                  # Clean build outputs
```

Requires JDK 17+ and Android SDK API 34.

## Architecture

This is an Android MVVM application that acts as an AI "judge" - it listens to conversations, transcribes speech, sends to an LLM for judgment, and speaks the response.

### Data Flow

```
Microphone → SttManager (Sherpa-onnx) → MainViewModel → LlmRepository → LLM API
                                              ↓
                                         TtsManager → Speaker
```

### Key Components

**MainViewModel** (`viewmodel/MainViewModel.kt`)
- Central orchestrator managing all state via StateFlow
- Coordinates STT recording, LLM calls, and TTS playback
- Handles model download status and settings persistence

**SttManager** (`speech/SttManager.kt`)
- Wraps Sherpa-onnx OnlineRecognizer for streaming speech-to-text
- Implements speaker diarization using SpeakerEmbeddingExtractor
- Maintains speaker registry with cosine similarity matching (threshold: 0.45)

**TtsManager** (`speech/TtsManager.kt`)
- Wraps Sherpa-onnx OfflineTts (VITS model) for text-to-speech
- Handles audio normalization and playback via AudioTrack

**LlmRepository** (`data/repository/LlmRepository.kt`)
- Retrofit-based network layer for LLM API calls
- Uses OpenAI-compatible `/v1/chat/completions` format
- Supports 7 providers: OpenAI, Claude, Gemini, DeepSeek, Doubao, Xiaomi, Custom

**ModelDownloadManager** (`util/ModelDownloadManager.kt`)
- Downloads ONNX models from Hugging Face
- Reports progress via Kotlin Flow

### UI Layer

- **MainActivity**: Single Activity with Jetpack Compose, handles navigation and permissions
- **ChatScreen**: Displays message history with speaker-colored bubbles
- **SettingsScreen**: LLM provider config, API keys, model downloads

## Models (ONNX)

Models are stored in app internal storage (`filesDir`):

- **STT**: Streaming Zipformer Bilingual (encoder/decoder/joiner + tokens)
- **TTS**: VITS Chinese (model + tokens + lexicon + rule.fst)
- **Diarization**: 3DSpeaker embedding extractor

Models are downloaded on-demand via Settings screen.

## Supported LLM Providers

Defined in `data/model/AppSettings.kt`. All use OpenAI-compatible API format:
- OpenAI, Claude (proxy), Gemini (proxy), DeepSeek, Doubao, Xiaomi, Custom

## Localization

Supports English (`values/strings.xml`) and Chinese (`values-zh/strings.xml`).

## Dependencies

- Sherpa-onnx AAR in `app/libs/` for speech processing
- Retrofit + OkHttp for networking
- Jetpack Compose with Material 3 for UI
- Navigation Compose for screen navigation
