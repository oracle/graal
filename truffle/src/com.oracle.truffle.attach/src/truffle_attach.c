/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include <jvmti.h>
#include <string.h>

#define EXCEPTION_CHECK(env)                                                                                                                         \
    if ((*env)->ExceptionCheck(env)) {                                                                                                               \
        return;                                                                                                                                      \
    }

#define CHECK_NONZERO(cond)                                                                                                                          \
    if (!(cond)) {                                                                                                                                   \
        fprintf(stderr, "[engine::attach] ERROR in " #cond ", was unexpectedly 0\n");                                                                \
        return;                                                                                                                                      \
    }

#define CHECK_ERROR(cond)                                                                                                                            \
    err = cond;                                                                                                                                      \
    if (err != 0) {                                                                                                                                  \
        fprintf(stderr, "[engine::attach] ERROR in " #cond ": %d\n", err);                                                                           \
        return;                                                                                                                                      \
    }

// Library entry points

JNIEXPORT void JNICALL Java_com_oracle_truffle_polyglot_JDKSupport_addExports0(JNIEnv *env, jclass clz, jobject m1, jobject pn, jobject m2) {
    jclass modulesClass = (*env)->FindClass(env, "jdk/internal/module/Modules");
    EXCEPTION_CHECK(env);
    jmethodID addExports = (*env)->GetStaticMethodID(env, modulesClass, "addExports", "(Ljava/lang/Module;Ljava/lang/String;Ljava/lang/Module;)V");
    EXCEPTION_CHECK(env);
    jvalue args[3];
    args[0].l = m1;
    args[1].l = pn;
    args[2].l = m2;
    (*env)->CallStaticVoidMethodA(env, modulesClass, addExports, args);
}

static jobject virtualThreadHooksClass;
static jmethodID mountMethod;
static jmethodID unmountMethod;

// Parameters: (jvmtiEnv *jvmti, JNIEnv* env, jthread vthread)
static void JNICALL mount_callback(jvmtiEnv *jvmti, ...) {
    va_list ap;
    JNIEnv *env = NULL;
    jthread vthread = NULL;

    va_start(ap, jvmti);
    env = va_arg(ap, JNIEnv *);
    vthread = va_arg(ap, jthread);
    va_end(ap);

    (*env)->CallStaticVoidMethod(env, virtualThreadHooksClass, mountMethod, vthread);
    if ((*env)->ExceptionCheck(env)) {
        fprintf(stderr, "[engine::attach] Exception in mount_callback\n");
        // Note: ExceptionDescribe is not ideal here because that calls Throwable#printStackTrace which uses locks.
        // But not doing anything doesn't show any details about the exception and just hangs, which is worse.
        (*env)->ExceptionDescribe(env);
    }
}

// Parameters: (jvmtiEnv *jvmti, JNIEnv* env, jthread vthread)
static void JNICALL unmount_callback(jvmtiEnv *jvmti, ...) {
    va_list ap;
    JNIEnv *env = NULL;
    jthread vthread = NULL;

    va_start(ap, jvmti);
    env = va_arg(ap, JNIEnv *);
    vthread = va_arg(ap, jthread);
    va_end(ap);

    (*env)->CallStaticVoidMethod(env, virtualThreadHooksClass, unmountMethod, vthread);
    if ((*env)->ExceptionCheck(env)) {
        fprintf(stderr, "[engine::attach] Exception in unmount_callback\n");
        // See comment in mount_callback()
        (*env)->ExceptionDescribe(env);
    }
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_api_impl_Accessor_00024JavaLangSupport_registerJVMTIHook(JNIEnv *env, jclass clz) {
    virtualThreadHooksClass = (*env)->NewGlobalRef(env, clz);
    EXCEPTION_CHECK(env);
    CHECK_NONZERO(virtualThreadHooksClass);

    mountMethod = (*env)->GetStaticMethodID(env, virtualThreadHooksClass, "mountHook", "(Ljava/lang/Thread;)V");
    EXCEPTION_CHECK(env);

    unmountMethod = (*env)->GetStaticMethodID(env, virtualThreadHooksClass, "unmountHook", "(Ljava/lang/Thread;)V");
    EXCEPTION_CHECK(env);

    jint err;
    JavaVM *jvm;
    CHECK_ERROR((*env)->GetJavaVM(env, &jvm));

    jvmtiEnv *jvmti;
    CHECK_ERROR((*jvm)->GetEnv(jvm, (void **) &jvmti, JVMTI_VERSION_21));

    jint eventsCount;
    jvmtiExtensionEventInfo *events;
    CHECK_ERROR((*jvmti)->GetExtensionEvents(jvmti, &eventsCount, &events));

    jint mount_event_index = 0;
    jint unmount_event_index = 0;
    for (int i = 0; i < eventsCount; i++) {
        if (strcmp(events[i].id, "com.sun.hotspot.events.VirtualThreadMount") == 0) {
            mount_event_index = events[i].extension_event_index;
        } else if (strcmp(events[i].id, "com.sun.hotspot.events.VirtualThreadUnmount") == 0) {
            unmount_event_index = events[i].extension_event_index;
        }
    }
    CHECK_NONZERO(mount_event_index);
    CHECK_NONZERO(unmount_event_index);

    CHECK_ERROR((*jvmti)->SetExtensionEventCallback(jvmti, mount_event_index, mount_callback));
    CHECK_ERROR((*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, mount_event_index, NULL));

    CHECK_ERROR((*jvmti)->SetExtensionEventCallback(jvmti, unmount_event_index, unmount_callback));
    CHECK_ERROR((*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, unmount_event_index, NULL));
}
