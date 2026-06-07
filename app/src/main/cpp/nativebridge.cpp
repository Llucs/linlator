#include <jni.h>
#include <android/log.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <cstdlib>
#include <cstring>
#include <cerrno>

#define LOG_TAG "LinlatorNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MAX_EVENTS 64
#define XSOCKET_PATH "/data/data/com.linlator/files/xserver/X0"

struct XServerContext {
    int epoll_fd;
    int server_fd;
    int width;
    int height;
    volatile int running;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_linlator_renderer_XServerView_00024XServerRenderer_nativeInit(JNIEnv *env, jobject thiz) {
    XServerContext *ctx = new XServerContext();
    ctx->epoll_fd = -1;
    ctx->server_fd = -1;
    ctx->width = 800;
    ctx->height = 600;
    ctx->running = 0;
    LOGI("XServerContext allocated at %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_linlator_renderer_XServerView_00024XServerRenderer_nativeDestroy(JNIEnv *env, jobject thiz,
                                                                          jlong native_ctx) {
    XServerContext *ctx = reinterpret_cast<XServerContext *>(native_ctx);
    if (!ctx) return;
    ctx->running = 0;
    if (ctx->epoll_fd >= 0) close(ctx->epoll_fd);
    if (ctx->server_fd >= 0) close(ctx->server_fd);
    delete ctx;
    LOGI("XServerContext destroyed");
}

JNIEXPORT void JNICALL
Java_com_linlator_renderer_XServerView_00024XServerRenderer_nativeResize(JNIEnv *env, jobject thiz,
                                                                          jlong native_ctx,
                                                                          jint width, jint height) {
    XServerContext *ctx = reinterpret_cast<XServerContext *>(native_ctx);
    if (!ctx) return;
    ctx->width = width;
    ctx->height = height;
    LOGI("Resized to %dx%d", width, height);
}

JNIEXPORT void JNICALL
Java_com_linlator_renderer_XServerView_00024XServerRenderer_nativeRender(JNIEnv *env, jobject thiz,
                                                                          jlong native_ctx) {
    XServerContext *ctx = reinterpret_cast<XServerContext *>(native_ctx);
    if (!ctx || ctx->epoll_fd < 0) return;

    struct epoll_event events[MAX_EVENTS];
    int nfds = epoll_wait(ctx->epoll_fd, events, MAX_EVENTS, 0);
    if (nfds < 0) {
        if (errno != EINTR) LOGE("epoll_wait error: %s", strerror(errno));
        return;
    }

    for (int i = 0; i < nfds; ++i) {
        int fd = events[i].data.fd;
        if (events[i].events & EPOLLIN) {
            if (fd == ctx->server_fd) {
                struct sockaddr_un client_addr;
                socklen_t client_len = sizeof(client_addr);
                int client_fd = accept(ctx->server_fd,
                                       reinterpret_cast<struct sockaddr *>(&client_addr),
                                       &client_len);
                if (client_fd < 0) {
                    LOGE("accept failed: %s", strerror(errno));
                    continue;
                }
                struct epoll_event ev;
                ev.events = EPOLLIN | EPOLLET;
                ev.data.fd = client_fd;
                if (epoll_ctl(ctx->epoll_fd, EPOLL_CTL_ADD, client_fd, &ev) < 0) {
                    LOGE("epoll_ctl add client failed: %s", strerror(errno));
                    close(client_fd);
                }
                LOGI("Accepted new X client fd=%d", client_fd);
            } else {
                char buf[4096];
                ssize_t n = read(fd, buf, sizeof(buf));
                if (n <= 0) {
                    if (n < 0) LOGE("read error from fd=%d: %s", fd, strerror(errno));
                    epoll_ctl(ctx->epoll_fd, EPOLL_CTL_DEL, fd, nullptr);
                    close(fd);
                    LOGI("Client fd=%d disconnected", fd);
                }
            }
        }
    }
}

JNIEXPORT jboolean JNICALL
Java_com_linlator_renderer_XServerView_00024XServerRenderer_nativeStartXServer(JNIEnv *env,
                                                                                jobject thiz,
                                                                                jlong native_ctx) {
    XServerContext *ctx = reinterpret_cast<XServerContext *>(native_ctx);
    if (!ctx) return JNI_FALSE;

    unlink(XSOCKET_PATH);

    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) {
        LOGE("socket creation failed: %s", strerror(errno));
        return JNI_FALSE;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, XSOCKET_PATH, sizeof(addr.sun_path) - 1);

    if (bind(server_fd, reinterpret_cast<struct sockaddr *>(&addr), sizeof(addr)) < 0) {
        LOGE("bind failed: %s", strerror(errno));
        close(server_fd);
        return JNI_FALSE;
    }

    chmod(XSOCKET_PATH, 0777);

    if (listen(server_fd, 8) < 0) {
        LOGE("listen failed: %s", strerror(errno));
        close(server_fd);
        unlink(XSOCKET_PATH);
        return JNI_FALSE;
    }

    int epoll_fd = epoll_create1(0);
    if (epoll_fd < 0) {
        LOGE("epoll_create1 failed: %s", strerror(errno));
        close(server_fd);
        unlink(XSOCKET_PATH);
        return JNI_FALSE;
    }

    struct epoll_event ev;
    ev.events = EPOLLIN;
    ev.data.fd = server_fd;
    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, server_fd, &ev) < 0) {
        LOGE("epoll_ctl add server failed: %s", strerror(errno));
        close(epoll_fd);
        close(server_fd);
        unlink(XSOCKET_PATH);
        return JNI_FALSE;
    }

    ctx->server_fd = server_fd;
    ctx->epoll_fd = epoll_fd;
    ctx->running = 1;

    LOGI("XServer started on %s (fd=%d, epoll=%d)", XSOCKET_PATH, server_fd, epoll_fd);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_linlator_renderer_XServerView_00024XServerRenderer_nativeStopXServer(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jlong native_ctx) {
    XServerContext *ctx = reinterpret_cast<XServerContext *>(native_ctx);
    if (!ctx) return;

    ctx->running = 0;

    if (ctx->epoll_fd >= 0) {
        close(ctx->epoll_fd);
        ctx->epoll_fd = -1;
    }
    if (ctx->server_fd >= 0) {
        close(ctx->server_fd);
        ctx->server_fd = -1;
    }

    unlink(XSOCKET_PATH);
    LOGI("XServer stopped");
}

}
