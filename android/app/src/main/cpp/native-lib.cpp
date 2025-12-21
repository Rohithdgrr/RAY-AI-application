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

struct LlamaContextWrapper {
    llama_model * model;
    llama_context * ctx;
    llama_sampler * smpl;
    uint32_t n_past = 0;
};

JNIEXPORT jlong JNICALL
Java_com_example_offlinellm_LlamaInference_nativeInit(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Initializing model from: %s", path);

    // CRITICAL: Load backends
    // In newer llama.cpp, backend initialization might be handled differently, 
    // but ensured through common initialization patterns.
    
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    
    llama_model * model = llama_model_load_from_file(path, model_params);
    if (!model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }

    const int n_ctx = 2048;
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = std::thread::hardware_concurrency();
    
    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    env->ReleaseStringUTFChars(model_path, path);
    
    LlamaContextWrapper * wrapper = new LlamaContextWrapper();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->smpl = smpl;
    wrapper->n_past = 0;
    
    return (jlong)wrapper;
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeGenerate(JNIEnv *env, jobject thiz, jlong ptr, jstring prompt, jobject cb) {
    const char *p = env->GetStringUTFChars(prompt, nullptr);
    LlamaContextWrapper * wrapper = (LlamaContextWrapper *)ptr;
    
    if (!wrapper || !wrapper->ctx) {
        if (p) env->ReleaseStringUTFChars(prompt, p);
        return;
    }

    jclass cbClass = env->GetObjectClass(cb);
    jmethodID onTokenID = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteID = env->GetMethodID(cbClass, "onComplete", "()V");

    const llama_vocab * vocab = llama_model_get_vocab(wrapper->model);
    
    // Tokenize
    int32_t n_tokens_max = strlen(p) + 4;
    std::vector<llama_token> tokens(n_tokens_max);
    int32_t n_tokens = llama_tokenize(vocab, p, strlen(p), tokens.data(), tokens.size(), wrapper->n_past == 0, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, p, strlen(p), tokens.data(), tokens.size(), wrapper->n_past == 0, true);
    }
    tokens.resize(n_tokens);

    // Initial batch
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    
    int n_predict = 512;
    int n_gen = 0;

    while (n_gen < n_predict) {
        if (llama_decode(wrapper->ctx, batch)) {
            LOGE("Failed to decode");
            break;
        }

        wrapper->n_past += batch.n_tokens;

        llama_token new_token_id = llama_sampler_sample(wrapper->smpl, wrapper->ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            jstring jtoken = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(cb, onTokenID, jtoken); // This is safe because onTokenID is cached and cb is valid
            env->DeleteLocalRef(jtoken);
        } else if (n < 0) {
           // Piece too small, or other error. For now, just skip.
        }

        batch = llama_batch_get_one(&new_token_id, 1);
        n_gen++;
    }

    env->CallVoidMethod(cb, onCompleteID);
    env->ReleaseStringUTFChars(prompt, p);
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeFree(JNIEnv *env, jobject thiz, jlong ptr) {
    LlamaContextWrapper * wrapper = (LlamaContextWrapper *)ptr;
    if (wrapper) {
        if (wrapper->ctx) llama_free(wrapper->ctx);
        if (wrapper->model) llama_model_free(wrapper->model);
        if (wrapper->smpl) llama_sampler_free(wrapper->smpl);
        delete wrapper;
    }
}

}
