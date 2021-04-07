/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <trufflenfi.h>
#include <stdlib.h>

#include "common.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#endif

class Thread {
private:
    TruffleContext *const ctx;
    int64_t (*const fn)(TruffleEnv *, int64_t arg);
    const int64_t arg;

#ifdef _WIN32
    HANDLE thread;
#else
    pthread_t thread;
#endif

public:
    Thread(TruffleEnv *env, int64_t (*fn)(TruffleEnv *, int64_t), int64_t arg);
    ~Thread();

    int64_t run();
    int64_t join(TruffleEnv *env);
};

EXPORT Thread *createThread(TruffleEnv *env, int64_t (*fn)(TruffleEnv *, int64_t), int64_t arg) {
    return new Thread(env, fn, arg);
}

EXPORT int64_t joinThread(TruffleEnv *env, Thread *t) {
    int64_t ret = t->join(env);
    delete t;
    return ret;
}

#ifdef _WIN32
static DWORD WINAPI threadStart(LPVOID x) {
    int64_t ret = static_cast<Thread*>(x)->run();
    return static_cast<DWORD>(ret);
}
#else
static void *threadStart(void *x) {
    int64_t ret = static_cast<Thread*>(x)->run();
    return reinterpret_cast<void *>(ret);
}
#endif

Thread::Thread(TruffleEnv *env, int64_t (*fn)(TruffleEnv *, int64_t), int64_t arg)
        :ctx(env->getTruffleContext()), fn(env->dupClosureRef(fn)), arg(arg) {
#ifdef _WIN32
    thread = CreateThread(NULL, 0, &threadStart, this, 0, NULL);
#else
    pthread_create(&thread, NULL, &threadStart, this);
#endif
}

Thread::~Thread() {
    TruffleEnv *env = ctx->getTruffleEnv();
    env->releaseClosureRef(fn);
#ifdef _WIN32
    CloseHandle(thread);
#endif
}

int64_t Thread::join(TruffleEnv *env) {
#ifdef _WIN32
    DWORD ret;
    WaitForSingleObject(thread, INFINITE);
    GetExitCodeThread(thread, &ret);
    return ret;
#else
    void *ret;
    pthread_join(thread, &ret);
    return reinterpret_cast<int64_t>(ret);
#endif
}

int64_t Thread::run() {
    TruffleEnv *env = ctx->attachCurrentThread();
    if (env == NULL) {
        // unable to attach thread
        return -1;
    }

    int64_t ret = fn(env, arg);
    ctx->detachCurrentThread();
    return ret;
}
