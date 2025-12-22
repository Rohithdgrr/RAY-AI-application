#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define TAG "LLAMA_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

struct LlamaContextWrapper {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    llama_sampler * smpl = nullptr;
    uint32_t n_past = 0;
    bool should_abort = false;
    bool is_generating = false;
    std::mutex mtx;
};

static std::once_flag g_llama_init;

JNIEXPORT jlong JNICALL
Java_com_example_offlinellm_LlamaInference_nativeInit(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Initializing model from: %s", path);

    std::call_once(g_llama_init, []() {
        llama_backend_init();
    });
    
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    
    llama_model * model = llama_model_load_from_file(path, model_params);
    if (!model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }

    const int n_ctx_val = 4096; // Increased context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx_val;
    ctx_params.n_batch = 2048; // Double logical batch for faster ingestion
    ctx_params.n_ubatch = 1024; // Physical batch
    ctx_params.n_threads = std::max(1u, std::thread::hardware_concurrency());
    ctx_params.n_threads_batch = std::max(1u, std::thread::hardware_concurrency());
    ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED; 
    ctx_params.type_k = GGML_TYPE_F16; // Faster precision
    ctx_params.type_v = GGML_TYPE_F16;
    
    LlamaContextWrapper * wrapper = new LlamaContextWrapper();
    wrapper->model = model;
    
    ctx_params.abort_callback = [](void * data) {
        LlamaContextWrapper * w = (LlamaContextWrapper *)data;
        if (w) {
            std::lock_guard<std::mutex> lock(w->mtx);
            return w->should_abort;
        }
        return false;
    };
    ctx_params.abort_callback_data = wrapper;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        delete wrapper;
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    env->ReleaseStringUTFChars(model_path, path);
    
    wrapper->ctx = ctx;
    wrapper->smpl = smpl;
    wrapper->n_past = 0;
    
    return (jlong)wrapper;
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeGenerate(JNIEnv *env, jobject thiz, jlong ptr, jstring prompt, jobject cb) {
    LlamaContextWrapper * wrapper = (LlamaContextWrapper *)ptr;
    if (!wrapper) return;

    const char *p = env->GetStringUTFChars(prompt, nullptr);
    if (!p) return;

    jclass cbClass = env->GetObjectClass(cb);
    jmethodID onTokenID = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteID = env->GetMethodID(cbClass, "onComplete", "()V");

    {
        std::lock_guard<std::mutex> lock(wrapper->mtx);
        wrapper->is_generating = true;
        wrapper->should_abort = false;
    }

    const llama_vocab * vocab = llama_model_get_vocab(wrapper->model);
    uint32_t n_ctx = llama_n_ctx(wrapper->ctx);
    
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
    llama_token new_token_id = 0;

    while (n_gen < n_predict) {
        {
            std::lock_guard<std::mutex> lock(wrapper->mtx);
            if (wrapper->should_abort) break;
        }

        // Context shift if full
        if (wrapper->n_past + batch.n_tokens > n_ctx) {
            LOGD("Context full, resetting n_past");
            wrapper->n_past = 0;
            // NOTE: In a real app we'd want to keep some context, but for a fix we just reset
        }

        if (llama_decode(wrapper->ctx, batch)) {
            LOGE("Failed to decode");
            break;
        }

        wrapper->n_past += batch.n_tokens;

        new_token_id = llama_sampler_sample(wrapper->smpl, wrapper->ctx, -1);
        llama_sampler_accept(wrapper->smpl, new_token_id);

        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            jstring jtoken = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(cb, onTokenID, jtoken);
            env->DeleteLocalRef(jtoken);
        }

        batch = llama_batch_get_one(&new_token_id, 1);
        n_gen++;
    }

    bool was_aborted = false;
    {
        std::lock_guard<std::mutex> lock(wrapper->mtx);
        was_aborted = wrapper->should_abort;
        wrapper->is_generating = false;
    }

    if (!was_aborted) {
        env->CallVoidMethod(cb, onCompleteID);
    }
    
    env->ReleaseStringUTFChars(prompt, p);
}

JNIEXPORT void JNICALL
Java_com_example_offlinellm_LlamaInference_nativeFree(JNIEnv *env, jobject thiz, jlong ptr) {
    LlamaContextWrapper * wrapper = (LlamaContextWrapper *)ptr;
    if (wrapper) {
        {
            std::lock_guard<std::mutex> lock(wrapper->mtx);
            wrapper->should_abort = true;
        }
        
        // Wait for generation to stop
        int timeout = 500; // 5 seconds max
        while (true) {
            {
                std::lock_guard<std::mutex> lock(wrapper->mtx);
                if (!wrapper->is_generating) break;
            }
            if (timeout-- <= 0) {
                LOGE("nativeFree: Timeout waiting for generation to stop! Potential crash incoming...");
                break;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }

        if (wrapper->ctx) {
            llama_free(wrapper->ctx);
            wrapper->ctx = nullptr;
        }
        if (wrapper->model) {
            llama_model_free(wrapper->model);
            wrapper->model = nullptr;
        }
        if (wrapper->smpl) {
            llama_sampler_free(wrapper->smpl);
            wrapper->smpl = nullptr;
        }
        delete wrapper;
    }
}

}
