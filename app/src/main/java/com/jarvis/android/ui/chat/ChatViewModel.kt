package com.jarvis.android.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.android.assistant.ConversationOrchestrator
import com.jarvis.android.assistant.OrchestratorState
import com.jarvis.android.data.db.entities.MessageEntity
import com.jarvis.android.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val orchestrator: ConversationOrchestrator,
    private val conversationRepo: ConversationRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val orchestratorState: StateFlow<OrchestratorState> = orchestrator.state
    val streamingResponse = orchestrator.streamingResponse

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    // True only when VOICE is actively listening — mic button uses this, not orchestratorState
    private val _voiceActive = MutableStateFlow(false)
    val voiceActive: StateFlow<Boolean> = _voiceActive.asStateFlow()

    private var convId: Long = -1L

    init {
        viewModelScope.launch {
            convId = conversationRepo.createConversation()
            conversationRepo.getMessages(convId).collect { _messages.value = it }
        }
        viewModelScope.launch {
            streamingResponse.collect { token ->
                _currentResponse.value += token
            }
        }
        viewModelScope.launch {
            orchestratorState.collect { state ->
                when (state) {
                    // New request starting — clear old streaming bubble
                    OrchestratorState.THINKING -> _currentResponse.value = ""
                    // Generation complete — clear streaming bubble so DB message is sole source
                    OrchestratorState.IDLE     -> _currentResponse.value = ""
                    else -> Unit
                }
                if (state != OrchestratorState.LISTENING) _voiceActive.value = false
            }
        }
    }

    fun startVoiceChat() {
        if (convId == -1L) return
        _voiceActive.value = true
        orchestrator.startListening(convId, viewModelScope)
    }

    /** Only stops VOICE — does not cancel in-progress LLM inference. */
    fun stopVoiceChat() {
        _voiceActive.value = false
        orchestrator.stopListening()
    }

    fun sendTextMessage(text: String) {
        if (convId == -1L || text.isBlank()) return
        // orchestrator.submitText handles stopping voice if needed
        orchestrator.submitText(text, convId, viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        orchestrator.stop()
    }
}
