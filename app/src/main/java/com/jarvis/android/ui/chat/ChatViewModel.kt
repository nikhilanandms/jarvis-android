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
                // Clear streaming display when a response is fully saved to DB
                if (state == OrchestratorState.IDLE || state == OrchestratorState.LISTENING) {
                    _currentResponse.value = ""
                }
                // Voice is only active during the listening state
                if (state != OrchestratorState.LISTENING) _voiceActive.value = false
            }
        }
    }

    fun startVoiceChat() {
        if (convId == -1L) return
        _currentResponse.value = ""
        _voiceActive.value = true
        orchestrator.startListening(convId, viewModelScope)
    }

    /** Only stops VOICE — does not interrupt an in-progress text LLM response. */
    fun stopVoiceChat() {
        _voiceActive.value = false
        orchestrator.stop()
    }

    fun sendTextMessage(text: String) {
        if (convId == -1L || text.isBlank()) return
        _currentResponse.value = ""
        orchestrator.submitText(text, convId, viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        orchestrator.stop()
    }
}
