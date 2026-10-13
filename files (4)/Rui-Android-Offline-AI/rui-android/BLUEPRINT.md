# Rui for Android: offline on-device AI

Native Kotlin + Jetpack Compose + Material 3. No INTERNET permission, no API keys, no cloud.
The UI follows your Rui desktop screenshot; the logo is your neon "R" orbit logo (`res/drawable-nodpi/rui_logo.png`, launcher icon, Play Store icon `play-store-icon-512.png`).

Open this folder in Android Studio (Ladybug or newer), let Gradle sync, run on an arm64 phone (not the x86 emulator: GPU/LLM runtimes need real hardware).
I could not compile it in my sandbox (no Android SDK or network), so expect to fix a small API or version mismatch on first sync.

---------------------------------------------------------------------

## 1. Architecture and tech stack

```
UI (Compose, M3)            RuiApp -> ChatScreen / ModelsScreen / Sidebar
   |  StateFlow
ChatViewModel               chats, prompt building, token throttling, RAM checks
   |
LlmEngine (interface)       MediaPipeLlmEngine today; llama.cpp / ONNX later
ModelStore                  import (SAF picker), list, delete, RAM + storage stats
Speaker                     on-device TextToSpeech
```

| Need | Choice | Why |
|---|---|---|
| Inference runtime | MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`) | Easiest GPU path on Android, streaming API, ready-made Gemma bundles. Check Google's docs: they are moving this toward LiteRT-LM, same idea. |
| Model format | `.task` (MediaPipe bundle, int4/int8) | Loaded directly by the runtime |
| Alternative | llama.cpp via JNI + `.gguf` | Widest model choice (Llama 3, Phi-3, Qwen). Implement `LlmEngine` with it; the UI does not change. |
| Alternative | ONNX Runtime Mobile + `.onnx` | Good for embeddings / small vision models (RAG, image classification) |
| UI | Compose BOM 2025.01, Material 3, own markdown renderer | No third-party UI deps |

Gradle: see `app/build.gradle.kts` (arm64-v8a only, R8 on, model extensions never compressed).

Model sizing (rough, int4): pick by phone RAM.

| Phone RAM | Good choice |
|---|---|
| 4 to 6 GB | Gemma 1B to 2B class, about 0.5 to 2.5 GB |
| 8 GB | Phi-3 mini or Gemma 2B to 3B class, about 2 to 3.5 GB |
| 12 GB+ | Llama 3 8B int4, about 4.5 to 5.5 GB. Realistic only on flagships, and slow. |

## 2. Local engine (`engine/LlmEngine.kt`)

- `load()` runs on `Dispatchers.IO`, tries the **GPU delegate first, then CPU**, and catches `OutOfMemoryError` so a model that is too big shows a message instead of crashing.
- `generate()` is a `callbackFlow`: tokens arrive on MediaPipe's thread; cancelling the collector (Stop button) calls `cancelGenerateResponseAsync()`.
- One persistent `LlmInferenceSession` per chat keeps the KV cache, so follow-ups only send the new turn. Switching chats resets the session and replays the last 8 messages.
- Chat template lives in one place (`ChatTemplate`). Gemma markers are used; change them for Phi-3 or Llama 3.
- The ViewModel throttles UI updates to about 20 per second so streaming never janks.

Memory safety:
- `ModelStore.estimatedRamBytes` = file size x 1.2; `loadModel()` refuses if free RAM is lower.
- `MainActivity.onTrimMemory` unloads the model on critical pressure.
- `largeHeap` is set, but the weights live in native memory, so the real guard is the RAM check above.
- Do not run two models at once.

## 3. UI (Compose)

- `RuiTheme`: your dark navy / blue-purple palette by default. `RuiTheme(dynamicColor = true)` switches to Material You wallpaper colors on Android 12+.
- Welcome screen = your "Good afternoon!" layout: gradient greeting, gradient-bordered composer, quick-action chips, "Suggested for you" cards (2 columns on tablets, 1 on phones).
- Drawer = your sidebar: logo, "New chat", search, Today / Yesterday / Previous 7 days groups.
- Top pills: **Think** (adds a reasoning hint), **Offline** (replaces "Web: Auto", because this build has no network), **Voice** (speaks replies with on-device TTS).
- Shimmer while waiting for the first token, markdown (headings, bold/italic, lists, quotes, code blocks with Copy), animated screen transitions, animated RAM/storage meters.

## 4. Hardware and storage strategy

- Never put the model inside the APK. Users import it with the system file picker (`ModelsScreen`), streamed in 1 MB chunks to `filesDir/models` as `.part`, then renamed (no half-written files). Free space is checked first.
- `filesDir` needs no storage permission, is deleted on uninstall, and is excluded from backup (`data_extraction_rules.xml`).
- For the Play Store: ship the model with **Play Asset Delivery** (install-time or fast-follow pack). Play does the download, so your app still declares no INTERNET permission. Packs are limited in size, so check the current limits.
- Verify a model with SHA-256 after import before loading if you distribute your own models.
- Verify zero network: `aapt dump permissions app-release.apk` must not list INTERNET.

## Next steps (not built yet)

1. Persist chats with Room (they are in memory now).
2. Voice input: `SpeechRecognizer.createOnDeviceSpeechRecognizer` (Android 12+).
3. File and image input: Gemma multimodal bundles accept images; documents go through extraction + embeddings (MediaPipe TextEmbedder or ONNX) + local SQLite for RAG.
4. Foreground service if you want generation to continue with the screen off.
5. Your earlier "go online when needed" idea conflicts with zero permissions. If you still want it, make a separate product flavor with INTERNET and keep this one offline.
