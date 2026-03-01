# Jarvis — Local Android AI Voice Assistant

A fully offline Android voice assistant built with Kotlin + Jetpack Compose. All AI inference runs on-device with no internet required after initial model setup. Supports continuous voice chat with long-term memory stored locally.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin + Jetpack Compose |
| LLM | MediaPipe LLM Inference API + Gemma 2B CPU INT4 |
| STT | Whisper.cpp via Android NDK/JNI (tiny model) |
| VAD | Silero VAD v5 via ONNX Runtime |
| TTS | Android TextToSpeech (offline) |
| Storage | Room/SQLite |
| DI | Hilt |

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Android App (Kotlin)                │
│                                                     │
│  ┌──────────┐   ┌──────────┐   ┌─────────────────┐ │
│  │   UI     │   │  Voice   │   │  Memory/Context │ │
│  │ (Compose)│   │  Layer   │   │  (Room/SQLite)  │ │
│  └────┬─────┘   └────┬─────┘   └────────┬────────┘ │
│       │              │                   │           │
│       └──────────────┼───────────────────┘           │
│                      ▼                               │
│            ┌─────────────────┐                      │
│            │  Assistant Core │                      │
│            │  (ViewModel)    │                      │
│            └────────┬────────┘                      │
│                     │                               │
│       ┌─────────────┼─────────────┐                 │
│       ▼             ▼             ▼                 │
│  ┌─────────┐  ┌──────────┐  ┌─────────┐            │
│  │ Whisper │  │MediaPipe │  │ Android │            │
│  │  (NDK)  │  │  Gemma   │  │   TTS   │            │
│  │  (STT)  │  │  (LLM)   │  │         │            │
│  └─────────┘  └──────────┘  └─────────┘            │
└─────────────────────────────────────────────────────┘
```

---

## Voice Chat Loop

```
LISTENING (VAD active)
    │
    ├─ VAD detects speech start → begin AudioRecord capture
    ├─ VAD detects silence → stop recording
    ▼
TRANSCRIBING
    └─ Whisper.cpp → transcript text
    ▼
THINKING
    └─ Build prompt (memories + summary + history + query)
       → MediaPipe Gemma streams tokens
    ▼
SPEAKING
    └─ Buffer tokens to sentence boundary → TTS speaks sentence
       Next sentence generating in parallel
       VAD still active for barge-in
    │
    ├─ User speaks during TTS → BARGE-IN: stop TTS + cancel LLM → LISTENING
    └─ Response complete → LISTENING
```

---

## Components

### Voice Layer
- **`AudioRecorder`** — streams 32ms PCM chunks via Flow from `AudioRecord`
- **`VADEngine`** — Silero VAD v5 (ONNX Runtime); runs during TTS for barge-in detection
- **`WhisperEngine`** — JNI wrapper around whisper.cpp; suspend fun on IO dispatcher
- **`TTSEngine`** — wraps Android TTS; sentence-by-sentence queuing; immediate stop for barge-in

### Assistant Core
- **`ConversationOrchestrator`** — state machine `IDLE → LISTENING → TRANSCRIBING → THINKING → SPEAKING`
- **`ContextBuilder`** — assembles Gemma prompt: system + memories + summaries + recent history
- **`MemoryExtractor`** — after each exchange, runs background LLM pass to extract and persist user facts
- **`SummarizationWorker`** — at 30-message threshold, summarises oldest 20 messages via Gemma

### Memory Layer (Room)
| Table | Purpose |
|---|---|
| `conversations` | Conversation sessions |
| `messages` | Per-message role/content, `is_summarized` flag |
| `memories` | Key/value user facts (e.g. `user_name: Nikhil`) |
| `summaries` | Compressed older message ranges |

### Prompt Assembly

```
<start_of_turn>system
You are Jarvis, a local AI assistant.
Known facts: - user_name: Nikhil
<end_of_turn>
<start_of_turn>model
[Earlier conversation summary: ...]
<end_of_turn>
[last 10 messages]
<start_of_turn>user
[new message]
<end_of_turn>
<start_of_turn>model
```

---

## Setup

### Required model files

Push via `adb` to `/sdcard/Android/data/com.jarvis.android/files/`:

| File | Source | Size |
|---|---|---|
| `gemma-1.1-2b-it-cpu-int4.bin` | [Kaggle — google/gemma/mediapipe](https://www.kaggle.com/models/google/gemma/mediapipe) | ~1.3 GB |
| `ggml-tiny.en.bin` | [HuggingFace — ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp) | ~75 MB |

`silero_vad.onnx` (~2 MB) is bundled in the app's assets — no manual download needed.

```bash
adb push gemma-1.1-2b-it-cpu-int4.bin /sdcard/Android/data/com.jarvis.android/files/
adb push ggml-tiny.en.bin             /sdcard/Android/data/com.jarvis.android/files/
```

### Build

```bash
git clone https://github.com/nikhilanandms/jarvis-android.git
cd jarvis-android
git submodule update --init --recursive   # pulls whisper.cpp
./gradlew installDebug
```

> **Note:** First build compiles whisper.cpp via NDK — takes ~3–5 minutes.

---

## Project Structure

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt          # NDK build for whisper.cpp
│   ├── whisper_jni.cpp         # JNI bridge
│   └── whisper.cpp/            # git submodule
├── assets/
│   └── silero_vad.onnx         # bundled VAD model
└── java/com/jarvis/android/
    ├── assistant/              # Orchestrator, ContextBuilder, MemoryExtractor
    ├── data/db/                # Room entities and DAOs
    ├── data/repository/        # ConversationRepository, MemoryRepository, SummaryRepository
    ├── di/                     # Hilt modules (EngineModule, DatabaseModule)
    ├── engine/                 # WhisperEngine, LLMEngine, VADEngine, TTSEngine, AudioRecorder
    ├── ui/chat/                # ChatScreen + ChatViewModel
    ├── ui/download/            # DownloadScreen + DownloadViewModel
    └── worker/                 # SummarizationWorker
```

---

## Tests

- **18 unit tests** — ContextBuilder, MemoryExtractor, SummarizationWorker, ConversationOrchestrator, ConversationRepository
- **6 instrumented integration tests** — Room DB pipeline, memory persistence across sessions, summarization threshold

---

## Out of Scope (v1)

- Wake word detection ("Hey Jarvis")
- Device control (timers, notifications, app launching)
- Multi-language support
- In-app model download (currently requires adb push)
- Cloud sync or backup
