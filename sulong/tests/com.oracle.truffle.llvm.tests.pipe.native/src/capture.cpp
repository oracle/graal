/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include <jni.h>
#include "com_oracle_truffle_llvm_tests_pipe_CaptureNativeOutput.h"

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

JNIEXPORT jint JNICALL Java_com_oracle_truffle_llvm_tests_pipe_CaptureNativeOutput_startCapturing(JNIEnv *env, jclass self, jint stdFd,
                                                                                                  jstring filename) {
    const char *path = env->GetStringUTFChars(filename, NULL);

    int fd = open(path, O_WRONLY);
    bool error = check_error(env, fd);
    env->ReleaseStringUTFChars(filename, path);
    if (error) {
        return -1;
    }

    int oldFd = dup(stdFd);
    if (check_error(env, oldFd)) {
        close(fd);
        return -1;
    }

    int result = dup2(fd, stdFd);
    if (check_error(env, result)) {
        close(fd);
        close(oldFd);
        return -1;
    }

    close(fd);
    return oldFd;
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_llvm_tests_pipe_CaptureNativeOutput_stopCapturing(JNIEnv *env, jclass self, jint oldStdOut,
                                                                                                 jint oldStdErr) {
    if (check_error(env, fflush(stdout))) {
        return;
    }
    if (check_error(env, fflush(stderr))) {
        return;
    }
    if (check_error(env, dup2(oldStdOut, com_oracle_truffle_llvm_tests_pipe_CaptureNativeOutput_STDOUT))) {
        return;
    }
    if (check_error(env, dup2(oldStdErr, com_oracle_truffle_llvm_tests_pipe_CaptureNativeOutput_STDERR))) {
        return;
    }
    if (check_error(env, close(oldStdOut))) {
        return;
    }
    check_error(env, close(oldStdErr));
}
