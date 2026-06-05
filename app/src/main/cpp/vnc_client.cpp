#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <string.h>
#include <stdint.h>
#include <thread>
#include <atomic>

#define LOG_TAG "RyzixVNC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<int> vnc_socket{-1};
static std::atomic<bool> vnc_connected{false};

static bool read_exact(int fd, uint8_t* buf, size_t len) {
    size_t total = 0;
    while (total < len) {
        ssize_t n = recv(fd, buf + total, len - total, 0);
        if (n <= 0) return false;
        total += n;
    }
    return true;
}

static bool write_exact(int fd, const uint8_t* buf, size_t len) {
    size_t total = 0;
    while (total < len) {
        ssize_t n = send(fd, buf + total, len - total, 0);
        if (n <= 0) return false;
        total += n;
    }
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ryzix_vm_vnc_VNCClient_nativeConnect(
        JNIEnv *env,
        jobject /* this */,
        jstring host,
        jint port) {

    const char* host_str = env->GetStringUTFChars(host, nullptr);
    LOGI("Connecting to VNC %s:%d", host_str, port);

    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        LOGE("Failed to create socket");
        env->ReleaseStringUTFChars(host, host_str);
        return JNI_FALSE;
    }

    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    inet_pton(AF_INET, host_str, &addr.sin_addr);
    env->ReleaseStringUTFChars(host, host_str);

    if (connect(sock, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOGE("Connection failed");
        close(sock);
        return JNI_FALSE;
    }

    // RFB Protocol Handshake
    uint8_t version[12];
    if (!read_exact(sock, version, 12)) {
        LOGE("Failed to read server version");
        close(sock);
        return JNI_FALSE;
    }
    version[11] = 0;
    LOGI("Server version: %s", version);

    // Send our version
    const uint8_t client_version[] = "RFB 003.008\n";
    write_exact(sock, client_version, 12);

    // Security types
    uint8_t num_security;
    if (!read_exact(sock, &num_security, 1)) {
        close(sock);
        return JNI_FALSE;
    }

    std::vector<uint8_t> security_types(num_security);
    if (!read_exact(sock, security_types.data(), num_security)) {
        close(sock);
        return JNI_FALSE;
    }

    // Choose None (1) security if available
    uint8_t chosen = 1;
    write_exact(sock, &chosen, 1);

    // Security result
    uint32_t result;
    if (!read_exact(sock, (uint8_t*)&result, 4)) {
        close(sock);
        return JNI_FALSE;
    }

    if (ntohl(result) != 0) {
        LOGE("Security handshake failed");
        close(sock);
        return JNI_FALSE;
    }

    // ClientInit — shared desktop
    uint8_t shared = 1;
    write_exact(sock, &shared, 1);

    vnc_socket.store(sock);
    vnc_connected.store(true);
    LOGI("VNC connected successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ryzix_vm_vnc_VNCClient_nativeDisconnect(
        JNIEnv *env,
        jobject /* this */) {
    int sock = vnc_socket.exchange(-1);
    if (sock >= 0) {
        close(sock);
        LOGI("VNC disconnected");
    }
    vnc_connected.store(false);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ryzix_vm_vnc_VNCClient_nativeIsConnected(
        JNIEnv *env,
        jobject /* this */) {
    return (jboolean)vnc_connected.load();
}

extern "C" JNIEXPORT void JNICALL
Java_com_ryzix_vm_vnc_VNCClient_nativeSendPointerEvent(
        JNIEnv *env,
        jobject /* this */,
        jint x,
        jint y,
        jint buttonMask) {
    int sock = vnc_socket.load();
    if (sock < 0) return;

    uint8_t msg[6];
    msg[0] = 5; // PointerEvent message type
    msg[1] = (uint8_t)buttonMask;
    msg[2] = (x >> 8) & 0xFF;
    msg[3] = x & 0xFF;
    msg[4] = (y >> 8) & 0xFF;
    msg[5] = y & 0xFF;
    write_exact(sock, msg, 6);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ryzix_vm_vnc_VNCClient_nativeSendKeyEvent(
        JNIEnv *env,
        jobject /* this */,
        jint keysym,
        jboolean down) {
    int sock = vnc_socket.load();
    if (sock < 0) return;

    uint8_t msg[8];
    msg[0] = 4; // KeyEvent message type
    msg[1] = down ? 1 : 0;
    msg[2] = 0;
    msg[3] = 0;
    msg[4] = (keysym >> 24) & 0xFF;
    msg[5] = (keysym >> 16) & 0xFF;
    msg[6] = (keysym >> 8) & 0xFF;
    msg[7] = keysym & 0xFF;
    write_exact(sock, msg, 8);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ryzix_vm_vnc_VNCClient_nativeSendFramebufferUpdateRequest(
        JNIEnv *env,
        jobject /* this */,
        jint x,
        jint y,
        jint width,
        jint height,
        jboolean incremental) {
    int sock = vnc_socket.load();
    if (sock < 0) return;

    uint8_t msg[10];
    msg[0] = 3;
    msg[1] = incremental ? 1 : 0;
    msg[2] = (x >> 8) & 0xFF;
    msg[3] = x & 0xFF;
    msg[4] = (y >> 8) & 0xFF;
    msg[5] = y & 0xFF;
    msg[6] = (width >> 8) & 0xFF;
    msg[7] = width & 0xFF;
    msg[8] = (height >> 8) & 0xFF;
    msg[9] = height & 0xFF;
    write_exact(sock, msg, 10);
}
