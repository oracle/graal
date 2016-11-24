#include <jni.h>
#include "native.h"

#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <string.h>
#include <errno.h>

static bool check_error(JNIEnv *env, int ret) {
    if (ret < 0) {
        char *message = strerror(errno);
        jclass ioex = env->FindClass("java/io/IOException");
        env->ThrowNew(ioex, message);
        return true;
    } else {
        return false;
    }
}

JNIEXPORT jint JNICALL Java_com_oracle_truffle_llvm_pipe_CaptureOutput_startCapturing(JNIEnv *env, jclass self, jstring filename) {
    const char *path = env->GetStringUTFChars(filename, NULL);

    int fd = open(path, O_WRONLY);
    bool error = check_error(env, fd);
    env->ReleaseStringUTFChars(filename, path);
    if (error) {
        return -1;
    }

    int oldStdOut = dup(1);
    if (check_error(env, oldStdOut)) {
        close(fd);
        return -1;
    }

    int result = dup2(fd, 1);
    if (check_error(env, result)) {
        close(fd);
        close(oldStdOut);
        return -1;
    }

    close(fd);
    return oldStdOut;
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_llvm_pipe_CaptureOutput_stopCapturing(JNIEnv *env, jclass self, jint oldStdOut) {
    if (check_error(env, fflush(stdout))) {
        return;
    }
    if (check_error(env, dup2(oldStdOut, 1))) {
        return;
    }
    check_error(env, close(oldStdOut));
}
