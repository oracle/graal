/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#include <jni.h>
#include <pthread.h>

// The original init function
jint JNICALL Java_sun_nio_fs_UnixNativeDispatcher_init(JNIEnv* env, jclass this);

// cached capabilities
static jint capabilities = 0;

// Global/static initialization flag and mutex
static jboolean initialized = JNI_FALSE;
static pthread_mutex_t init_mutex = PTHREAD_MUTEX_INITIALIZER;

JNIEXPORT jint JNICALL Java_com_oracle_svm_core_jdk_Helper_1sun_1nio_1fs_1UnixNativeDispatcher_init1(JNIEnv* env, jclass this, jclass unixNativeDispatcherClass) {
    pthread_mutex_lock(&init_mutex);
    if (initialized == JNI_FALSE) {
        capabilities = Java_sun_nio_fs_UnixNativeDispatcher_init(env, unixNativeDispatcherClass);
        // initialization done
        initialized = JNI_TRUE;
    }
    pthread_mutex_unlock(&init_mutex);
    return capabilities;
}
