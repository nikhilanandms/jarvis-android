package com.jarvis.android.ui.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ModelStatus(
    val name: String,
    val description: String,
    val path: String,
    val exists: Boolean
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _models = MutableStateFlow<List<ModelStatus>>(emptyList())
    val models: StateFlow<List<ModelStatus>> = _models.asStateFlow()

    val allReady: Boolean get() = _models.value.isNotEmpty() && _models.value.all { it.exists }

    init {
        checkModels()
    }

    fun checkModels() {
        viewModelScope.launch {
            val base = context.getExternalFilesDir(null)?.absolutePath ?: return@launch
            _models.value = listOf(
                ModelStatus(
                    name = "Gemma 1.1 2B CPU INT4 (LLM)",
                    description = "~1.3 GB · Place at path below",
                    path = "$base/gemma-1.1-2b-it-cpu-int4.bin",
                    exists = File("$base/gemma-1.1-2b-it-cpu-int4.bin").exists()
                ),
                ModelStatus(
                    name = "Whisper Tiny (STT)",
                    description = "~75 MB · Place at path below",
                    path = "$base/ggml-tiny.en.bin",
                    exists = File("$base/ggml-tiny.en.bin").exists()
                )
            )
        }
    }
}
