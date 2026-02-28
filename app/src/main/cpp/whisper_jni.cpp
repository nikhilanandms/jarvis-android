#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_jarvis_android_engine_WhisperEngine_nativeInit(
        JNIEnv *env, jobject /* this */, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading Whisper model from: %s", path);

    struct whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;

    struct whisper_context *ctx = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!ctx) {
        LOGE("Failed to load Whisper model");
        return 0L;
    }
    LOGI("Whisper model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_jarvis_android_engine_WhisperEngine_nativeTranscribe(
        JNIEnv *env, jobject /* this */, jlong ctxPtr, jfloatArray pcmData) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctxPtr);
    if (!ctx) return env->NewStringUTF("");

    jsize len = env->GetArrayLength(pcmData);
    jfloat *pcm = env->GetFloatArrayElements(pcmData, nullptr);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads        = 4;
    params.language         = "en";
    params.translate        = false;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = false;
    params.single_segment   = false;

    int result = whisper_full(ctx, params, pcm, static_cast<int>(len));
    env->ReleaseFloatArrayElements(pcmData, pcm, JNI_ABORT);

    if (result != 0) {
        LOGE("whisper_full failed with code: %d", result);
        return env->NewStringUTF("");
    }

    std::string transcript;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) transcript += text;
    }

    // Trim leading/trailing whitespace
    size_t start = transcript.find_first_not_of(" \t\n\r");
    if (start == std::string::npos) return env->NewStringUTF("");
    size_t end = transcript.find_last_not_of(" \t\n\r");
    transcript = transcript.substr(start, end - start + 1);

    LOGI("Transcribed: %s", transcript.c_str());
    return env->NewStringUTF(transcript.c_str());
}

JNIEXPORT void JNICALL
Java_com_jarvis_android_engine_WhisperEngine_nativeRelease(
        JNIEnv * /* env */, jobject /* this */, jlong ctxPtr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctxPtr);
    if (ctx) {
        whisper_free(ctx);
        LOGI("Whisper context released");
    }
}

} // extern "C"
