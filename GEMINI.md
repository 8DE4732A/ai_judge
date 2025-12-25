# AI Judge Project Context

## Project Overview

**AI Judge** is an Android application designed to act as an unbiased, conversational observer. It listens to user speech, transcribes it, consults an AI "Judge" (LLM) for an opinion or verdict, and speaks the response back to the user.

**Key Features:**
*   **Real-time STT:** Uses [Sherpa-onnx](https://github.com/k2fsa/sherpa-onnx) for on-device, streaming speech recognition.
*   **AI Integration:** Connects to OpenAI-compatible LLM APIs (e.g., GPT-3.5/4, DeepSeek) to process text and generate responses.
*   **TTS Output:** Uses Sherpa-onnx VITS for on-device text-to-speech synthesis.
*   **Modern UI:** Built with Jetpack Compose and Material 3.

**Architecture:**
*   **Pattern:** MVVM (Model-View-ViewModel).
*   **Language:** Kotlin.
*   **UI Toolkit:** Jetpack Compose.
*   **Data Layer:** Retrofit for network calls, internal storage for models.

## Building and Running

### Prerequisites
*   **JDK:** Version 1.8 or higher (Java 17 recommended for recent Android builds).
*   **Android SDK:** Compile SDK 34, Min SDK 24.
*   **Hardware:** Android device or emulator with microphone support.

### Build Commands
Use the Gradle wrapper included in the project:

```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease

# Run Unit Tests
./gradlew test

# Run Android Tests
./gradlew connectedAndroidTest
```

### Model Setup
The application requires pre-trained ONNX models for STT and TTS.

1.  **Automatic Download:** The app includes a `ModelDownloadManager` that attempts to download models from Hugging Face to the app's internal storage (`filesDir/stt-model` and `filesDir/tts-model`) upon initialization or user trigger.
2.  **Manual Setup:** If automatic download fails, you can manually push models to the device:
    *   **STT Path:** `/data/data/win.liuping.aijudge/files/stt-model/` containing `encoder-*.onnx`, `decoder-*.onnx`, `joiner-*.onnx`, `tokens.txt`.
    *   **TTS Path:** `/data/data/win.liuping.aijudge/files/tts-model/` containing `vits-*.onnx`, `tokens.txt`, `lexicon.txt`, `rule.fst`.

### Configuration
1.  Open the app and navigate to **Settings**.
2.  Enter your **API Key** for the LLM provider.
3.  (Optional) Customize the **System Prompt** and **Endpoint URL**.

## Key Files and Components

*   **`app/src/main/java/win/liuping/aijudge/MainViewModel.kt`**: The core orchestrator. Manages application state (`_messages`, `_isListening`), handles audio recording toggles, triggers model downloads, and coordinates the flow between STT -> LLM -> TTS.
*   **`app/src/main/java/win/liuping/aijudge/speech/SttManager.kt`**: Wraps `Sherpa-onnx`'s `OnlineRecognizer`. Handles microphone input and streaming transcription.
*   **`app/src/main/java/win/liuping/aijudge/speech/TtsManager.kt`**: Wraps `Sherpa-onnx`'s TTS engine. Converts LLM text responses to audio.
*   **`app/src/main/java/win/liuping/aijudge/data/network/LlmApi.kt`**: Retrofit interface for the Chat Completion API.
*   **`app/src/main/java/win/liuping/aijudge/util/ModelDownloadManager.kt`**: Handles downloading model files from Hugging Face URLs defined in constants.

## Development Conventions

*   **Code Style:** Standard Kotlin conventions.
*   **Asynchrony:** Heavy use of Kotlin Coroutines (`viewModelScope`, `suspend` functions, `Flow`) for non-blocking operations.
*   **State:** UI state is exposed via `StateFlow` from the ViewModel.
*   **Dependency Injection:** Currently manual instantiation in `MainViewModel`, but prepared for DI (e.g., Hilt) if needed.
