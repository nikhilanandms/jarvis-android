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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    // Accumulates streaming tokens for live display
    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private var convId: Long = -1L

    init {
        viewModelScope.launch {
            convId = conversationRepo.createConversation()
            conversationRepo.getMessages(convId).collect { _messages.value = it }
        }
        viewModelScope.launch {
            streamingResponse.collect { token ->
                if (orchestratorState.value == OrchestratorState.THINKING ||
                    orchestratorState.value == OrchestratorState.SPEAKING) {
                    _currentResponse.value += token
                }
            }
        }
        viewModelScope.launch {
            orchestratorState.collect { state ->
                if (state == OrchestratorState.LISTENING || state == OrchestratorState.IDLE) {
                    _currentResponse.value = ""
                }
            }
        }
    }

    fun startVoiceChat() {
        if (convId == -1L) return
        _currentResponse.value = ""
        orchestrator.startListening(convId, viewModelScope)
    }

    fun stopVoiceChat() = orchestrator.stop()

    fun sendTextMessage(text: String) {
        if (convId == -1L || text.isBlank()) return
        viewModelScope.launch {
            conversationRepo.addMessage(convId, "user", text)
            // TODO: trigger LLM response for text-only input (Task 16 / future)
        }
    }

    override fun onCleared() {
        super.onCleared()
        orchestrator.stop()
    }
}
