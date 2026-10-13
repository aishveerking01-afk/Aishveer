package com.rui.app

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rui.app.data.Chat
import com.rui.app.data.Message
import com.rui.app.data.Role
import com.rui.app.engine.ChatTemplate
import com.rui.app.engine.DeviceStats
import com.rui.app.engine.EngineState
import com.rui.app.engine.LlmEngine
import com.rui.app.engine.LocalModel
import com.rui.app.engine.MediaPipeLlmEngine
import com.rui.app.engine.ModelStore
import com.rui.app.engine.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val engine: LlmEngine = MediaPipeLlmEngine(app)
    private val store = ModelStore(app)
    private val speaker = Speaker(app)

    val engineState: StateFlow<EngineState> = engine.state

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()
    private val _currentId = MutableStateFlow<String?>(null)

    val messages: StateFlow<List<Message>> =
        combine(_chats, _currentId) { chats, id -> chats.firstOrNull { it.id == id }?.messages ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val input = MutableStateFlow("")
    val thinkOn = MutableStateFlow(true)
    val voiceOn = MutableStateFlow(false)

    private val _models = MutableStateFlow(store.list())
    val models = _models.asStateFlow()
    private val _stats = MutableStateFlow(store.deviceStats())
    val stats: StateFlow<DeviceStats> = _stats.asStateFlow()
    val importProgress = MutableStateFlow<Float?>(null)
    val loadedModel = MutableStateFlow<String?>(null)

    private val _notice = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notice = _notice.asSharedFlow()

    private var genJob: Job? = null
    private var importJob: Job? = null
    private var sessionChatId: String? = null
    private var nextId = 1L

    val isGenerating: Boolean get() = genJob?.isActive == true

    // ---- Chats ---------------------------------------------------------------------------

    fun newChat() {
        stopGenerating()
        _currentId.value = null
        input.value = ""
    }

    fun openChat(id: String) {
        stopGenerating()
        _currentId.value = id
    }

    fun deleteChat(id: String) {
        _chats.update { list -> list.filterNot { it.id == id } }
        if (_currentId.value == id) _currentId.value = null
    }

    // ---- Sending -------------------------------------------------------------------------

    fun send(text: String = input.value) {
        val q = text.trim()
        if (q.isEmpty() || isGenerating) return
        if (engine.state.value !is EngineState.Ready) {
            _notice.tryEmit("Load a model first (Models & storage).")
            return
        }
        speaker.stop()

        val chatId = _currentId.value ?: Chat(title = q.take(40)).also { c ->
            _chats.update { it + c }
            _currentId.value = c.id
        }.id
        val history = _chats.value.first { it.id == chatId }.messages
        val replay = sessionChatId != chatId
        if (replay) {
            engine.resetSession()
            sessionChatId = chatId
        }
        val prompt = buildPrompt(history, q, replay)

        append(chatId, Message(nextId++, Role.User, q))
        val replyId = nextId++
        append(chatId, Message(replyId, Role.Assistant, "", streaming = true))
        input.value = ""

        genJob = viewModelScope.launch {
            val sb = StringBuilder()
            var last = 0L
            try {
                engine.generate(prompt).collect { piece ->
                    sb.append(piece)
                    // Throttle UI updates to ~20/s so streaming never janks the frame.
                    val now = SystemClock.uptimeMillis()
                    if (now - last >= 50) { update(chatId, replyId, sb.toString(), true); last = now }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                sb.append("\n\n⚠️ ").append(t.message ?: "Generation failed")
                // Context window full → start a fresh session next time.
                sessionChatId = null
            } finally {
                update(chatId, replyId, sb.toString(), false)
            }
            if (voiceOn.value) speaker.speak(sb.toString())
        }
    }

    fun stopGenerating() {
        genJob?.cancel()
        speaker.stop()
    }

    private fun buildPrompt(history: List<Message>, q: String, replay: Boolean): String {
        val think = if (thinkOn.value) "\n\n(Reason step by step before answering.)" else ""
        val sys = "You are Rui, a friendly offline assistant and expert coder. " +
            "Answer clearly in markdown and use a few fitting emojis.\n\n"
        val turns = buildList {
            if (replay) history.takeLast(8).filter { it.text.isNotBlank() }.forEach {
                add((if (it.role == Role.User) "user" else "model") to it.text)
            }
            add("user" to q + think)
        }
        val sb = StringBuilder()
        turns.forEachIndexed { i, (role, text) ->
            sb.append(ChatTemplate.turn(role, if (replay && i == 0 && role == "user") sys + text else text))
        }
        return sb.append(ChatTemplate.MODEL_PREFIX).toString()
    }

    private fun append(chatId: String, m: Message) = _chats.update { list ->
        list.map { if (it.id == chatId) it.copy(messages = it.messages + m, updatedAt = System.currentTimeMillis()) else it }
    }

    private fun update(chatId: String, msgId: Long, text: String, streaming: Boolean) = _chats.update { list ->
        list.map { c ->
            if (c.id != chatId) c
            else c.copy(messages = c.messages.map { if (it.id == msgId) it.copy(text = text, streaming = streaming) else it })
        }
    }

    // ---- Models --------------------------------------------------------------------------

    fun refreshStats() { _stats.value = store.deviceStats() }

    fun importModel(uri: Uri) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            importProgress.value = 0f
            val result = store.import(uri) { importProgress.value = it }
            importProgress.value = null
            result.onSuccess { _notice.tryEmit("Imported ${it.name}") }
                .onFailure { _notice.tryEmit(it.message ?: "Import failed") }
            _models.value = store.list()
            refreshStats()
        }
    }

    fun cancelImport() { importJob?.cancel() }

    fun loadModel(m: LocalModel) {
        refreshStats()
        if (m.estimatedRamBytes > _stats.value.availRam) {
            _notice.tryEmit("Not enough free RAM for ${m.name}. Close other apps or choose a smaller model.")
            return
        }
        viewModelScope.launch {
            engine.load(m.file.absolutePath)
            if (engine.state.value is EngineState.Ready) {
                loadedModel.value = m.name
                sessionChatId = null
            } else (engine.state.value as? EngineState.Error)?.let { _notice.tryEmit(it.message) }
            refreshStats()
        }
    }

    fun unloadModel() {
        stopGenerating()
        engine.unload()
        loadedModel.value = null
        refreshStats()
    }

    fun deleteModel(m: LocalModel) {
        if (loadedModel.value == m.name) unloadModel()
        store.delete(m)
        _models.value = store.list()
        refreshStats()
    }

    override fun onCleared() {
        speaker.shutdown()
        engine.unload()
    }
}
