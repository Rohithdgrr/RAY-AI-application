#include <jni.h>
#include <string>
#include <android/log.h>
#include <thread>
#include <vector>
#include <atomic>
#include <mutex>

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
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
    cparams.n_threads = std::thread::hardware_concurrency();

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

    LOGD("nativeInit: Model loaded successfully");
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
    jmethodID onCompleteID = env->GetMethodID(cbClass, "onComplete", "()V");
    jmethodID onErrorID = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    wrapper->stop_requested = false;

    // Tokenize
    std::vector<llama_token> tokens = common_tokenize(wrapper->vocab, prompt_std, true, true);
    
    int n_past = 0;
    int n_remain = 512;

    auto sparams = common_params_sampling();
    auto * sampler = common_sampler_init(wrapper->model, sparams);

    while (n_remain > 0 && !wrapper->stop_requested) {
        // Decode
        llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
        if (llama_decode(wrapper->ctx, batch)) {
            env->CallVoidMethod(cb, onErrorID, env->NewStringUTF("Failed to decode"));
            common_sampler_free(sampler);
            return;
        }

        n_past += tokens.size();
        tokens.clear();

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

        tokens.push_back(id);
        n_remain--;
    }

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
