package com.rui.app.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed interface EngineState {
    data object Idle : EngineState
    data object Loading : EngineState
    data class Ready(val modelName: String, val backend: String) : EngineState
    data object Generating : EngineState
    data class Error(val message: String) : EngineState
}

/** Swap this implementation to move to llama.cpp (GGUF) or ONNX Runtime without touching the UI. */
interface LlmEngine {
    val state: StateFlow<EngineState>
    suspend fun load(modelPath: String)
    /** Emits partial text pieces as they are produced. Cancelling the collector stops generation. */
    fun generate(prompt: String): Flow<String>
    fun resetSession()
    fun unload()
}

/** Gemma-style chat template. Phi-3 and Llama 3 use different markers: change them here only. */
object ChatTemplate {
    fun turn(role: String, text: String) = "<start_of_turn>$role\n$text<end_of_turn>\n"
    const val MODEL_PREFIX = "<start_of_turn>model\n"
}

class MediaPipeLlmEngine(private val context: Context) : LlmEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val lock = Any()
    private var llm: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private var backendName = "CPU"
    private var lastModelName = ""

    override suspend fun load(modelPath: String) = withContext(Dispatchers.IO) {
        synchronized(lock) { releaseLocked() }
        _state.value = EngineState.Loading
        try {
            // Try the GPU delegate first, fall back to CPU if the device/driver rejects it.
            val inference = try {
                backendName = "GPU"
                create(modelPath, LlmInference.Backend.GPU)
            } catch (t: Throwable) {
                backendName = "CPU"
                create(modelPath, LlmInference.Backend.CPU)
            }
            synchronized(lock) {
                llm = inference
                session = newSession(inference)
            }
            lastModelName = File(modelPath).name
            _state.value = EngineState.Ready(lastModelName, backendName)
        } catch (oom: OutOfMemoryError) {
            synchronized(lock) { releaseLocked() }
            _state.value = EngineState.Error("Not enough memory for this model. Close other apps or pick a smaller model.")
        } catch (t: Throwable) {
            synchronized(lock) { releaseLocked() }
            _state.value = EngineState.Error(t.message ?: "Could not load the model")
        }
    }

    private fun create(path: String, backend: LlmInference.Backend): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(2048)
            .setPreferredBackend(backend)
            .build()
        return LlmInference.createFromOptions(context, options)
    }

    private fun newSession(inference: LlmInference): LlmInferenceSession {
        val opts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTopP(0.95f)
            .setTemperature(0.8f)
            .build()
        return LlmInferenceSession.createFromOptions(inference, opts)
    }

    override fun generate(prompt: String): Flow<String> = callbackFlow {
        val s = synchronized(lock) { session }
        if (s == null) {
            close(IllegalStateException("No model is loaded"))
            return@callbackFlow
        }
        _state.value = EngineState.Generating
        try {
            s.addQueryChunk(prompt)
            // Partial results arrive on MediaPipe's own thread, never on the main thread.
            s.generateResponseAsync { partial, done ->
                trySend(partial)
                if (done) close()
            }
        } catch (t: Throwable) {
            close(t)
        }
        awaitClose {
            // Runs on normal completion AND when the collector is cancelled (Stop button).
            runCatching { s.cancelGenerateResponseAsync() }
            _state.value = if (synchronized(lock) { llm } != null) {
                EngineState.Ready(lastModelName, backendName)
            } else EngineState.Idle
        }
    }

    override fun resetSession() = synchronized(lock) {
        val inference = llm ?: return@synchronized
        runCatching { session?.close() }
        session = newSession(inference)
    }

    override fun unload() {
        synchronized(lock) { releaseLocked() }
        _state.value = EngineState.Idle
    }

    private fun releaseLocked() {
        runCatching { session?.close() }
        runCatching { llm?.close() }
        session = null
        llm = null
    }
}
