package com.jarvis.android.di

import android.content.Context
import com.jarvis.android.engine.AudioRecorder
import com.jarvis.android.engine.LLMEngine
import com.jarvis.android.engine.TTSEngine
import com.jarvis.android.engine.VADEngine
import com.jarvis.android.engine.WhisperEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideWhisperEngine(@ApplicationContext context: Context): WhisperEngine {
        val path = context.getExternalFilesDir(null)!!.absolutePath + "/ggml-tiny.en.bin"
        return WhisperEngine(path)
    }

    @Provides
    @Singleton
    fun provideLLMEngine(@ApplicationContext context: Context): LLMEngine {
        val path = context.getExternalFilesDir(null)!!.absolutePath + "/gemma-1.1-2b-it-cpu-int4.bin"
        return LLMEngine(context, path)
    }

    @Provides
    @Singleton
    fun provideVADEngine(@ApplicationContext context: Context): VADEngine = VADEngine(context)

    @Provides
    @Singleton
    fun provideTTSEngine(@ApplicationContext context: Context): TTSEngine = TTSEngine(context)

    @Provides
    @Singleton
    fun provideAudioRecorder(): AudioRecorder = AudioRecorder()
}
