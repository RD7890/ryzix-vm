#include <jni.h>
#include <string>
#include <android/log.h>
#include <thread>
#include <atomic>
#include <vector>
#include <sstream>

#define LOG_TAG "RyzixVM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if QEMU_AVAILABLE
extern "C" int qemu_main(int argc, char **argv, char **envp);
#endif

static std::atomic<bool> vm_running{false};
static std::thread vm_thread;

extern "C" JNIEXPORT jint JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_startQEMU(
        JNIEnv *env,
        jobject /* this */,
        jobjectArray args) {

    if (vm_running.load()) {
        LOGE("VM already running");
        return -1;
    }

    int argc = env->GetArrayLength(args);
    std::vector<std::string> arg_strings;
    std::vector<char*> argv_ptrs;

    for (int i = 0; i < argc; i++) {
        jstring jstr = (jstring)env->GetObjectArrayElement(args, i);
        const char* cstr = env->GetStringUTFChars(jstr, nullptr);
        arg_strings.push_back(std::string(cstr));
        env->ReleaseStringUTFChars(jstr, cstr);
        env->DeleteLocalRef(jstr);
    }

    for (auto& s : arg_strings) {
        argv_ptrs.push_back(const_cast<char*>(s.c_str()));
        LOGI("QEMU arg: %s", s.c_str());
    }

#if QEMU_AVAILABLE
    vm_running.store(true);
    vm_thread = std::thread([argv_ptrs, arg_strings]() mutable {
        LOGI("Starting QEMU thread with %zu args", argv_ptrs.size());
        int ret = qemu_main((int)argv_ptrs.size(), argv_ptrs.data(), nullptr);
        LOGI("QEMU exited with code %d", ret);
        vm_running.store(false);
    });
    vm_thread.detach();
    return 0;
#else
    LOGI("STUB MODE: QEMU not compiled in. Args received: %d", argc);
    LOGI("To build with full QEMU, run GitHub Actions workflow.");
    vm_running.store(true);
    vm_thread = std::thread([]() {
        LOGI("Stub VM running... (no actual QEMU)");
        std::this_thread::sleep_for(std::chrono::seconds(2));
        vm_running.store(false);
        LOGI("Stub VM stopped");
    });
    vm_thread.detach();
    return 0;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_stopQEMU(
        JNIEnv *env,
        jobject /* this */) {
    LOGI("Stopping QEMU");
    vm_running.store(false);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_isRunning(
        JNIEnv *env,
        jobject /* this */) {
    return (jboolean)vm_running.load();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_getVersion(
        JNIEnv *env,
        jobject /* this */) {
#if QEMU_AVAILABLE
    return env->NewStringUTF("QEMU 8.x (Full Build)");
#else
    return env->NewStringUTF("QEMU Stub (Build via GitHub Actions for full QEMU)");
#endif
}
