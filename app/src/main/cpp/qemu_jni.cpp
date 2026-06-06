#include <jni.h>
#include <string>
#include <android/log.h>
#include <thread>
#include <atomic>
#include <vector>
#include <dlfcn.h>
#include <sys/stat.h>

#define LOG_TAG "RyzixVM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef void (*qemu_init_func)(int argc, char **argv, char **envp);
typedef void (*qemu_main_loop_func)(void);
typedef void (*qemu_cleanup_func)(void);
// Limbo compat layer initialiser — sets limbo_base_dir / storage_base_dir
// so android_fopen() doesn't crash on null ptr inside qemu_init.
typedef void (*set_jni_func)(JNIEnv*, jobject, jclass, const char*, const char*);

static std::atomic<bool> vm_running{false};
static std::thread vm_thread;
static void *qemu_handle = nullptr;

extern "C" JNIEXPORT jint JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_nativeStartQEMU(
        JNIEnv *env,
        jobject thiz,
        jstring libPath,
        jstring biosDir,
        jstring storageDir,
        jobjectArray args) {

    if (vm_running.load()) {
        LOGE("VM already running");
        return -1;
    }

    const char *lib_path_str  = env->GetStringUTFChars(libPath,    nullptr);
    const char *bios_dir_str  = env->GetStringUTFChars(biosDir,    nullptr);
    const char *stor_dir_str  = env->GetStringUTFChars(storageDir, nullptr);

    std::string qemu_lib(lib_path_str);
    std::string bios_dir(bios_dir_str);
    std::string stor_dir(stor_dir_str);

    env->ReleaseStringUTFChars(libPath,    lib_path_str);
    env->ReleaseStringUTFChars(biosDir,    bios_dir_str);
    env->ReleaseStringUTFChars(storageDir, stor_dir_str);

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
    qemu_handle = dlopen(qemu_lib.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!qemu_handle) {
        LOGE("dlopen(%s) failed: %s — trying short name", qemu_lib.c_str(), dlerror());
        qemu_handle = dlopen("libqemu-system-x86_64.so", RTLD_NOW | RTLD_GLOBAL);
    }
    if (!qemu_handle) {
        LOGE("Could not load QEMU library: %s", dlerror());
        return -2;
    }

    // ── Initialise Limbo's compat layer ──────────────────────────────────
    // Limbo's patched QEMU replaces fopen with android_fopen which uses
    // limbo_base_dir (set by set_jni).  Without this call limbo_base_dir
    // is NULL and qemu_init crashes with SEGV_ACCERR the moment it tries
    // any file I/O (config, BIOS, ROM, etc.).
    auto set_jni_fn = (set_jni_func) dlsym(qemu_handle, "set_jni");
    if (set_jni_fn) {
        jclass thiz_class = env->GetObjectClass(thiz);
        set_jni_fn(env, thiz, thiz_class, stor_dir.c_str(), bios_dir.c_str());
        env->DeleteLocalRef(thiz_class);
        LOGI("set_jni called: storage=%s bios=%s", stor_dir.c_str(), bios_dir.c_str());
    } else {
        LOGE("set_jni symbol not found — QEMU may crash in android_fopen");
    }

    auto qemu_init_fn      = (qemu_init_func)      dlsym(qemu_handle, "qemu_init");
    auto qemu_main_loop_fn = (qemu_main_loop_func) dlsym(qemu_handle, "qemu_main_loop");
    auto qemu_cleanup_fn   = (qemu_cleanup_func)   dlsym(qemu_handle, "qemu_cleanup");

    if (!qemu_init_fn) {
        LOGE("qemu_init symbol not found");
        dlclose(qemu_handle);
        qemu_handle = nullptr;
        return -3;
    }
    if (!qemu_main_loop_fn) {
        LOGE("qemu_main_loop symbol not found");
        dlclose(qemu_handle);
        qemu_handle = nullptr;
        return -4;
    }

    LOGI("QEMU symbols resolved. Launching VM thread...");
    vm_running.store(true);

    vm_thread = std::thread([qemu_init_fn, qemu_main_loop_fn, qemu_cleanup_fn,
                             arg_strings]() mutable {
        std::vector<char *> argv_ptrs;
        for (auto &s : arg_strings) argv_ptrs.push_back(const_cast<char *>(s.c_str()));

        LOGI("qemu_init starting with %zu args", argv_ptrs.size());
        qemu_init_fn((int) argv_ptrs.size(), argv_ptrs.data(), nullptr);

        LOGI("qemu_init done, entering main loop");
        qemu_main_loop_fn();

        if (qemu_cleanup_fn) {
            LOGI("qemu_cleanup");
            qemu_cleanup_fn();
        }

        LOGI("QEMU main loop exited");
        vm_running.store(false);
    });
    vm_thread.detach();
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_stopQEMU(JNIEnv *, jobject) {
    LOGI("stopQEMU called");
    vm_running.store(false);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_isRunning(JNIEnv *, jobject) {
    return (jboolean) vm_running.load();
}

// Safe version check — stat() only, zero dlopen, zero constructor risk.
extern "C" JNIEXPORT jstring JNICALL
Java_com_ryzix_vm_qemu_QEMUBridge_getVersion_1native(
        JNIEnv *env,
        jobject,
        jstring nativeLibDirJ) {

    if (qemu_handle) {
        return env->NewStringUTF("QEMU 5.1.0 (Limbo/Android, x86_64 guest)");
    }
    const char *dir = env->GetStringUTFChars(nativeLibDirJ, nullptr);
    std::string libPath = std::string(dir) + "/libqemu-system-x86_64.so";
    env->ReleaseStringUTFChars(nativeLibDirJ, dir);

    struct stat st{};
    if (stat(libPath.c_str(), &st) == 0 && st.st_size > 0) {
        LOGI("QEMU library found: %s (%lld bytes)", libPath.c_str(), (long long) st.st_size);
        return env->NewStringUTF("QEMU 5.1.0 (Limbo/Android, x86_64 guest)");
    }
    LOGE("QEMU library not found at %s", libPath.c_str());
    return env->NewStringUTF("QEMU library not found");
}
