#include <jni.h>
#include <string>
#include <android/log.h>
#include <thread>
#include <atomic>
#include <vector>
#include <dlfcn.h>

#define LOG_TAG "RyzixVM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef void (*qemu_init_func)(int argc, char **argv, char **envp);
typedef void (*qemu_main_loop_func)(void);

static std::atomic<bool> vm_running{false};
static std::thread vm_thread;
static void *qemu_handle = nullptr;

extern "C" JNIEXPORT jint JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_nativeStartQEMU(
        JNIEnv *env,
        jobject,
        jstring libPath,
        jobjectArray args) {

    if (vm_running.load()) {
        LOGE("VM already running");
        return -1;
    }

    const char *lib_path_str = env->GetStringUTFChars(libPath, nullptr);
    std::string qemu_lib(lib_path_str);
    env->ReleaseStringUTFChars(libPath, lib_path_str);

    int argc = env->GetArrayLength(args);
    std::vector<std::string> arg_strings;
    for (int i = 0; i < argc; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(args, i);
        const char *cstr = env->GetStringUTFChars(jstr, nullptr);
        arg_strings.emplace_back(cstr);
        LOGI("QEMU arg[%d]: %s", i, cstr);
        env->ReleaseStringUTFChars(jstr, cstr);
        env->DeleteLocalRef(jstr);
    }

    LOGI("Loading QEMU library: %s", qemu_lib.c_str());
    qemu_handle = dlopen(qemu_lib.c_str(), RTLD_LAZY | RTLD_GLOBAL);
    if (!qemu_handle) {
        LOGE("dlopen(%s) failed: %s — trying by name", qemu_lib.c_str(), dlerror());
        qemu_handle = dlopen("libqemu-system-x86_64.so", RTLD_LAZY | RTLD_GLOBAL);
    }
    if (!qemu_handle) {
        LOGE("Could not load QEMU library: %s", dlerror());
        return -2;
    }

    auto qemu_init_fn = (qemu_init_func) dlsym(qemu_handle, "qemu_init");
    auto qemu_main_loop_fn = (qemu_main_loop_func) dlsym(qemu_handle, "qemu_main_loop");

    if (!qemu_init_fn || !qemu_main_loop_fn) {
        LOGE("Symbol resolution failed: qemu_init=%p qemu_main_loop=%p",
             qemu_init_fn, qemu_main_loop_fn);
        dlclose(qemu_handle);
        qemu_handle = nullptr;
        return -3;
    }

    LOGI("QEMU symbols resolved. Launching VM thread...");
    vm_running.store(true);

    vm_thread = std::thread([qemu_init_fn, qemu_main_loop_fn, arg_strings]() mutable {
        std::vector<char *> argv_ptrs;
        for (auto &s : arg_strings) argv_ptrs.push_back(const_cast<char *>(s.c_str()));

        LOGI("qemu_init starting with %zu args", argv_ptrs.size());
        qemu_init_fn((int) argv_ptrs.size(), argv_ptrs.data(), nullptr);
        LOGI("qemu_init done, entering main loop");
        qemu_main_loop_fn();
        LOGI("QEMU main loop exited");
        vm_running.store(false);
    });
    vm_thread.detach();
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_stopQEMU(
        JNIEnv *,
        jobject) {
    LOGI("stopQEMU called");
    vm_running.store(false);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_isRunning(
        JNIEnv *,
        jobject) {
    return (jboolean) vm_running.load();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_getVersion(
        JNIEnv *env,
        jobject) {
    if (qemu_handle) {
        return env->NewStringUTF("QEMU 5.1.0 (Limbo/Android, x86_64 guest)");
    }
    return env->NewStringUTF("QEMU not loaded yet");
}
