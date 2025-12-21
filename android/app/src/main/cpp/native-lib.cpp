#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <android/log.h>

// Note: These headers come from llama.cpp
// In a real build, we'd include the actual llama.cpp headers
// #include "llama.h"

#define TAG "LLAMA_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_offlinellm_LlamaInference_nativeInit(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Initializing model from: %s", path);

    // Placeholder for actual llama_init calls
    // llama_model * model = llama_load_model_from_file(path, params);
    // llama_context * ctx = llama_new_context_with_model(model, params);
    
    // For this example, we return a dummy pointer
    long dummy_ctx = 12345;

    env->ReleaseStringUTFChars(model_path, path);
    return (jlong)dummy_ctx;
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeGenerate(JNIEnv *env, jobject thiz, jlong ptr, jstring prompt, jobject cb) {
    const char *p = env->GetStringUTFChars(prompt, nullptr);
    
    jclass cbClass = env->GetObjectClass(cb);
    jmethodID onTokenID = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteID = env->GetMethodID(cbClass, "onComplete", "()V");

    // Simulation of token generation
    std::string response = "This is a simulated response generated fully offline using llama.cpp JNI for the prompt: ";
    response += p;

    // Split into tokens (words) to simulate streaming
    std::string word;
    size_t pos = 0;
    while ((pos = response.find(' ')) != std::string::npos) {
        word = response.substr(0, pos + 1);
        jstring jword = env->NewStringUTF(word.c_str());
        env->CallVoidMethod(cb, onTokenID, jword);
        env->DeleteLocalRef(jword);
        response.erase(0, pos + 1);
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }

    if (!response.empty()) {
        jstring jword = env->NewStringUTF(response.c_str());
        env->CallVoidMethod(cb, onTokenID, jword);
        env->DeleteLocalRef(jword);
    }

    env->CallVoidMethod(cb, onCompleteID);
    env->ReleaseStringUTFChars(prompt, p);
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeFree(JNIEnv *env, jobject thiz, jlong ptr) {
    LOGD("Freeing native context");
    // llama_free((llama_context *)ptr);
}

}
