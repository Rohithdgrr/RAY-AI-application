#include <jni.h>
#include <string>
#include <android/log.h>
#include <thread>
#include <vector>
#include <atomic>
#include <mutex>
#include <chrono>

#include "llama.cpp/include/llama.h"
#include "llama.cpp/common/common.h"
#include "llama.cpp/common/sampling.h"

#define TAG "LLAMA_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct llama_context_wrapper {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const struct llama_vocab * vocab = nullptr;
    std::atomic<bool> stop_requested{false};
    int n_batch = 512;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_offlinellm_LlamaInference_nativeInit(JNIEnv *env, jobject thiz, jstring model_path) {
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("nativeInit: Loading model from %s", path);

    llama_backend_init();

    auto mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    llama_model * model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (!model) {
        LOGE("nativeInit: Failed to load model");
        return 0;
    }

    auto cparams = llama_context_default_params();
    cparams.n_ctx = 4096;
    cparams.n_batch = 512;  
    cparams.n_ubatch = 512; 
    
    uint32_t n_threads = std::thread::hardware_concurrency();
    if (n_threads >= 8) n_threads = 4; 
    else if (n_threads > 4) n_threads = 4;
    else if (n_threads < 1) n_threads = 1;
    
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;

    llama_context * ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("nativeInit: Failed to create context");
        llama_model_free(model);
        return 0;
    }

    auto * wrapper = new llama_context_wrapper();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->vocab = llama_model_get_vocab(model);
    wrapper->n_batch = cparams.n_batch;

    LOGD("nativeInit: Model loaded successfully with %u threads", n_threads);
    return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeGenerate(JNIEnv *env, jobject thiz, jlong ptr, jstring prompt, jobject cb) {
    auto * wrapper = reinterpret_cast<llama_context_wrapper *>(ptr);
    if (!wrapper) return;

    const char * prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_std(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    jclass cbClass = env->GetObjectClass(cb);
    jmethodID onTokenID = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onStatusID = env->GetMethodID(cbClass, "onStatus", "(Ljava/lang/String;)V");
    jmethodID onCompleteID = env->GetMethodID(cbClass, "onComplete", "()V");
    jmethodID onErrorID = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    wrapper->stop_requested = false;

    // Tokenize
    std::vector<llama_token> tokens = common_tokenize(wrapper->vocab, prompt_std, true, true);
    
    int n_past = 0;
    int n_remain = 1024; // Increased from 512
    int n_generated = 0;

    auto sparams = common_params_sampling();
    auto * sampler = common_sampler_init(wrapper->model, sparams);

    // Initial batch for prompt
    int initial_batch_size = tokens.size() > (size_t)wrapper->n_batch ? wrapper->n_batch : (int)tokens.size();
    llama_batch batch = llama_batch_init(initial_batch_size, 0, 1);
    
    // Report prompt processing status
    {
        char status[128];
        snprintf(status, sizeof(status), "Processing prompt (%zu tokens)...", tokens.size());
        jstring jstatus = env->NewStringUTF(status);
        env->CallVoidMethod(cb, onStatusID, jstatus);
        env->DeleteLocalRef(jstatus);
    }

    // Decode prompt in chunks
    for (size_t i = 0; i < tokens.size(); i += wrapper->n_batch) {
        if (wrapper->stop_requested) break;
        
        size_t n_eval = tokens.size() - i;
        if (n_eval > (size_t)wrapper->n_batch) n_eval = wrapper->n_batch;
        
        batch.n_tokens = 0;
        for (size_t j = 0; j < n_eval; j++) {
            common_batch_add(batch, tokens[i + j], n_past + j, { 0 }, (i + j) == (tokens.size() - 1));
        }
        
        if (llama_decode(wrapper->ctx, batch)) {
            LOGE("Failed to decode prompt chunk");
            env->CallVoidMethod(cb, onErrorID, env->NewStringUTF("Failed to decode prompt"));
            llama_batch_free(batch);
            common_sampler_free(sampler);
            return;
        }
        n_past += n_eval;
    }

    auto start_time = std::chrono::high_resolution_clock::now();

    while (n_remain > 0 && !wrapper->stop_requested) {
        // Report status every 5 tokens
        if (n_generated % 5 == 0) {
            auto now = std::chrono::high_resolution_clock::now();
            double duration = std::chrono::duration_cast<std::chrono::milliseconds>(now - start_time).count() / 1000.0;
            double tps = duration > 0 ? n_generated / duration : 0;
            
            char status[128];
            snprintf(status, sizeof(status), "Context: %d | Speed: %.1f t/s", n_past, tps);
            jstring jstatus = env->NewStringUTF(status);
            env->CallVoidMethod(cb, onStatusID, jstatus);
            env->DeleteLocalRef(jstatus);
        }

        // Sample
        llama_token id = common_sampler_sample(sampler, wrapper->ctx, -1);
        common_sampler_accept(sampler, id, true);

        if (llama_vocab_is_eog(wrapper->vocab, id)) {
            break;
        }

        // Token to piece
        char buf[256];
        int n = llama_token_to_piece(wrapper->vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            jstring jpiece = env->NewStringUTF(std::string(buf, n).c_str());
            env->CallVoidMethod(cb, onTokenID, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        // Prepare next batch (single token)
        batch.n_tokens = 0;
        common_batch_add(batch, id, n_past, { 0 }, true);
        
        // Decode next token
        if (llama_decode(wrapper->ctx, batch)) {
            LOGE("Failed to decode token");
            break;
        }

        n_past += 1;
        n_remain--;
        n_generated++;
    }

    llama_batch_free(batch);

    common_sampler_free(sampler);
    if (!wrapper->stop_requested) {
        env->CallVoidMethod(cb, onCompleteID);
    }
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeClearKV(JNIEnv *env, jobject thiz, jlong ptr) {
    auto * wrapper = reinterpret_cast<llama_context_wrapper *>(ptr);
    if (wrapper && wrapper->ctx) {
        llama_memory_seq_rm(llama_get_memory(wrapper->ctx), -1, -1, -1);
    }
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeStop(JNIEnv *env, jobject thiz, jlong ptr) {
    auto * wrapper = reinterpret_cast<llama_context_wrapper *>(ptr);
    if (wrapper) {
        wrapper->stop_requested = true;
    }
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeFree(JNIEnv *env, jobject thiz, jlong ptr) {
    auto * wrapper = reinterpret_cast<llama_context_wrapper *>(ptr);
    if (wrapper) {
        if (wrapper->ctx) llama_free(wrapper->ctx);
        if (wrapper->model) llama_model_free(wrapper->model);
        delete wrapper;
    }
}

}
