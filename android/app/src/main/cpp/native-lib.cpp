#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <android/log.h>

#include "llama.h"

#define TAG "LLAMA_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_offlinellm_LlamaInference_nativeInit(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Initializing model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    
    llama_model * model = llama_load_model_from_file(path, model_params);
    if (!model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = 4;
    
    llama_context * ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_free_model(model);
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }

    env->ReleaseStringUTFChars(model_path, path);
    return (jlong)ctx;
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeGenerate(JNIEnv *env, jobject thiz, jlong ptr, jstring prompt, jobject cb) {
    const char *p = env->GetStringUTFChars(prompt, nullptr);
    
    jclass cbClass = env->GetObjectClass(cb);
    jmethodID onTokenID = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteID = env->GetMethodID(cbClass, "onComplete", "()V");

    llama_context * ctx = (llama_context *)ptr;
    if (!ctx) {
        LOGE("Invalid context pointer");
        env->CallVoidMethod(cb, onCompleteID);
        env->ReleaseStringUTFChars(prompt, p);
        return;
    }

    std::vector<llama_token> tokens;
    tokens = llama_tokenize(ctx, p, true);
    
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    
    for (size_t i = 0; i < tokens.size(); i++) {
        llama_batch_add(&batch, tokens[i], i, {0}, false);
    }
    
    if (llama_decode(ctx, batch) != 0) {
        LOGE("Failed to decode prompt");
        llama_batch_free(batch);
        env->CallVoidMethod(cb, onCompleteID);
        env->ReleaseStringUTFChars(prompt, p);
        return;
    }

    for (int i = 0; i < 100; i++) {
        llama_token new_token = llama_sample_token(ctx, NULL);
        
        if (new_token == llama_token_eos(ctx)) {
            break;
        }
        
        std::string token_str = llama_token_to_piece(ctx, new_token);
        jstring jtoken = env->NewStringUTF(token_str.c_str());
        env->CallVoidMethod(cb, onTokenID, jtoken);
        env->DeleteLocalRef(jtoken);
        
        llama_batch_clear(batch);
        llama_batch_add(&batch, new_token, tokens.size() + i, {0}, false);
        
        if (llama_decode(ctx, batch) != 0) {
            LOGE("Failed to decode token");
            break;
        }
        
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }

    llama_batch_free(batch);
    env->CallVoidMethod(cb, onCompleteID);
    env->ReleaseStringUTFChars(prompt, p);
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeFree(JNIEnv *env, jobject thiz, jlong ptr) {
    LOGD("Freeing native context");
    llama_context * ctx = (llama_context *)ptr;
    if (ctx) {
        llama_model * model = llama_get_model(ctx);
        llama_free(ctx);
        llama_free_model(model);
    }
}

}
