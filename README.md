# AI Judge (AI 判官)

AI Judge is an Android application that acts as an unbiased observer in your environment. It listens to conversations around it, transcribes them using on-device Speech-to-Text (STT), sends the context to an AI "Judge" (LLM), and speaks out the verdict using on-device Text-to-Speech (TTS).

## Features

- **On-device Speech Recognition**: Powered by [Sherpa-onnx](https://github.com/k2fsa/sherpa-onnx) for real-time, privacy-focused transcription.
- **AI Judgment**: Integrates with OpenAI-compatible LLM APIs (e.g., GPT-3.5/4) to analyze conversations and provide witty or serious judgments.
- **Voice Output**: The Judge speaks its verdict aloud using Sherpa-onnx VITS TTS.
- **Configurable**: reliable Settings screen to customize LLM endpoints, API keys, and system prompts.

## Prerequisites

### 1. Model Files
This app requires pre-trained ONNX models for speech processing. You must manually download them and place them in the app's assets.

**Location:** `app/src/main/assets/`

**Recommended Models:**
- **STT**: `sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20` (or similar streaming transducer)
- **TTS**: `vits-vctk` or other VITS models compatible with Sherpa-onnx.

Ensure the file structure in `assets` matches what is expected by `SttManager` and `TtsManager`.

### 2. LLM API Key
You need an API Key from an OpenAI-compatible provider (OpenAI, DeepSeek, etc.).
- Go to App **Settings**.
- Enter your **API Key**.
- (Optional) Configure **Endpoint** and **System Prompt**.

## Getting Started

1. Clone this repository.
2. Open in **Android Studio** (Hedgehog or later recommended).
3. Download and place the model files in `app/src/main/assets/`.
4. Sync Gradle project.
5. Run on an Android device or Emulator (ensure Microphone permission is granted).
6. Tap **LISTEN** to start the session.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material3)
- **Architecture**: MVVM
- **Speech**: Sherpa-onnx (JNI bindings)
- **Network**: Retrofit
