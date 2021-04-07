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
#error "TODO"
#else
#include <pthread.h>
#endif

class Thread {
private:
    TruffleContext *const ctx;
    int64_t (*const fn)(TruffleEnv *, int64_t arg);
    const int64_t arg;

    pthread_t thread;

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

static void *threadStart(void *x) {
    int64_t ret = static_cast<Thread*>(x)->run();
    return reinterpret_cast<void *>(ret);
}

Thread::Thread(TruffleEnv *env, int64_t (*fn)(TruffleEnv *, int64_t), int64_t arg)
        :ctx(env->getTruffleContext()), fn(env->dupClosureRef(fn)), arg(arg) {
    pthread_create(&thread, NULL, &threadStart, this);
}

Thread::~Thread() {
    TruffleEnv *env = ctx->getTruffleEnv();
    env->releaseClosureRef(fn);
}

int64_t Thread::join(TruffleEnv *env) {
    void *ret;
    pthread_join(thread, &ret);
    return reinterpret_cast<int64_t>(ret);
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
