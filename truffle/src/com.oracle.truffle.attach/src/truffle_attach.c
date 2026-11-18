/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include <stdarg.h>
#include <stdint.h>
#include <string.h>

#if defined(__linux__) || defined(__APPLE__)
#include <dlfcn.h>
#include <pthread.h>
#elif defined(_WIN32)
#include <windows.h>
#endif

#define EXCEPTION_CHECK(env)                                                                                                                         \
    if ((*env)->ExceptionCheck(env)) {                                                                                                               \
        return;                                                                                                                                      \
    }

#define EXCEPTION_CHECK_OBJECT(env)                                                                                                                  \
    if ((*env)->ExceptionCheck(env)) {                                                                                                               \
        return NULL;                                                                                                                                 \
    }

#define CHECK_TRUE_ELSE_RETURN(cond, result, ...)                                                                                                    \
    do {                                                                                                                                             \
        if (!(cond)) {                                                                                                                               \
            fprintf(stderr, "[engine::attach] ERROR: " __VA_ARGS__);                                                                                 \
            fprintf(stderr, "\n");                                                                                                                   \
            return result;                                                                                                                           \
        }                                                                                                                                            \
    } while (0)

#define CHECK_TRUE_ELSE_POP(cond, result)                                                                                                            \
    do {                                                                                                                                             \
        if (!(cond)) {                                                                                                                               \
            if ((*env)->ExceptionCheck(env)) {                                                                                                       \
                (*env)->ExceptionDescribe(env);                                                                                                      \
                (*env)->ExceptionClear(env);                                                                                                         \
            }                                                                                                                                        \
            (*env)->PopLocalFrame(env, NULL);                                                                                                        \
            return result;                                                                                                                           \
        }                                                                                                                                            \
    } while (0)

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

/* Published before enabling JVMTI events and never replaced, even after a weak reference clears. */
static jobject virtualThreadHooksClass;
static jmethodID virtualThreadMountMethod;
static jmethodID virtualThreadUnmountMethod;
static jboolean libraryPinned;
static jboolean jvmtiInitialized;
static jvmtiEnv *sharedJvmti;
static jmethodID supplierGetMethod;

#if defined(__linux__) || defined(__APPLE__)
static pthread_mutex_t initializationMutex = PTHREAD_MUTEX_INITIALIZER;

static void lock_initialization(void) {
    pthread_mutex_lock(&initializationMutex);
}

static void unlock_initialization(void) {
    pthread_mutex_unlock(&initializationMutex);
}
#elif defined(_WIN32)
static SRWLOCK initializationLock = SRWLOCK_INIT;

static void lock_initialization(void) {
    AcquireSRWLockExclusive(&initializationLock);
}

static void unlock_initialization(void) {
    ReleaseSRWLockExclusive(&initializationLock);
}
#endif

/*
 * SymbolLookup.libraryLookup does not invoke JNI_OnLoad, so initialization shared by regular JNI
 * loading and the Foreign Function and Memory API bootstrap must be performed explicitly.
 */
static jint initialize_shared_state(JNIEnv *env, jboolean pinned) {
    jint result = JNI_OK;
    lock_initialization();
    if (pinned) {
        libraryPinned = JNI_TRUE;
    }
    if (supplierGetMethod == NULL) {
        jclass supplierClass = (*env)->FindClass(env, "java/util/function/Supplier");
        if (supplierClass == NULL) {
            result = JNI_ERR;
        } else {
            jmethodID getMethod = (*env)->GetMethodID(env, supplierClass, "get", "()Ljava/lang/Object;");
            if (getMethod == NULL) {
                result = JNI_ERR;
            } else {
                supplierGetMethod = getMethod;
            }
        }
    }
    unlock_initialization();
    return result;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_21) != JNI_OK) {
        return JNI_ERR;
    }
    if (initialize_shared_state(env, JNI_FALSE) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_21;
}

static void call_virtual_thread_hooks(JNIEnv *env, jthread vthread, jboolean mount) {
    jclass hooksClass = (jclass) (*env)->NewLocalRef(env, virtualThreadHooksClass);
    if (hooksClass == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        return;
    }
    jmethodID method = mount ? virtualThreadMountMethod : virtualThreadUnmountMethod;
    (*env)->CallStaticVoidMethod(env, hooksClass, method, vthread);
    (*env)->DeleteLocalRef(env, hooksClass);
    if ((*env)->ExceptionCheck(env)) {
        fprintf(stderr, "[engine::attach] Exception in %s_callback\n", mount ? "mount" : "unmount");
        /*
         * ExceptionDescribe is not ideal here because it calls Throwable#printStackTrace,
         * which uses locks. Not reporting the exception is worse because it leaves a pending
         * exception on the carrier thread.
         */
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

// Parameters: (jvmtiEnv *jvmti, JNIEnv* env, jthread vthread)
static void JNICALL mount_callback(jvmtiEnv *jvmti, ...) {
    va_list ap;
    JNIEnv *env = NULL;
    jthread vthread = NULL;

    va_start(ap, jvmti);
    env = va_arg(ap, JNIEnv *);
    vthread = va_arg(ap, jthread);
    va_end(ap);

    call_virtual_thread_hooks(env, vthread, JNI_TRUE);
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

    call_virtual_thread_hooks(env, vthread, JNI_FALSE);
}

static jboolean throw_illegal_state(JNIEnv *env, const char *format, ...) {
    if (!(*env)->ExceptionCheck(env)) {
        char message[512];
        va_list args;
        va_start(args, format);
        vsnprintf(message, sizeof(message), format, args);
        va_end(args);
        jclass exceptionClass = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (exceptionClass != NULL) {
            (*env)->ThrowNew(env, exceptionClass, message);
            (*env)->DeleteLocalRef(env, exceptionClass);
        }
    }
    return JNI_FALSE;
}

static jvmtiError uninstall_jvmti_hook(jint eventIndex) {
    jvmtiError disableError = (*sharedJvmti)->SetEventNotificationMode(sharedJvmti, JVMTI_DISABLE, eventIndex, NULL);
    jvmtiError clearError = (*sharedJvmti)->SetExtensionEventCallback(sharedJvmti, eventIndex, NULL);
    return disableError != JVMTI_ERROR_NONE ? disableError : clearError;
}

/* Must be called with the initialization lock held. */
static jboolean initialize_jvmti(JNIEnv *env) {
    if (jvmtiInitialized) {
        return JNI_TRUE;
    }

    jint err;
    if (sharedJvmti == NULL) {
        JavaVM *jvm = NULL;
        err = (*env)->GetJavaVM(env, &jvm);
        if (err != JNI_OK) {
            return throw_illegal_state(env, "GetJavaVM failed while initializing JVMTI virtual thread hooks: %d", err);
        }
        err = (*jvm)->GetEnv(jvm, (void **) &sharedJvmti, JVMTI_VERSION_21);
        if (err != JNI_OK) {
            sharedJvmti = NULL;
            return throw_illegal_state(env, "Failed to get the JVMTI environment: %d", err);
        }
    }

    jint mountEventIndex = 0;
    jint unmountEventIndex = 0;
    jint eventsCount = 0;
    jvmtiExtensionEventInfo *events = NULL;
    err = (*sharedJvmti)->GetExtensionEvents(sharedJvmti, &eventsCount, &events);
    if (err != JVMTI_ERROR_NONE) {
        return throw_illegal_state(env, "JVMTI GetExtensionEvents failed: %d", err);
    }
    for (int i = 0; i < eventsCount; i++) {
        if (strcmp(events[i].id, "com.sun.hotspot.events.VirtualThreadMount") == 0) {
            mountEventIndex = events[i].extension_event_index;
        } else if (strcmp(events[i].id, "com.sun.hotspot.events.VirtualThreadUnmount") == 0) {
            unmountEventIndex = events[i].extension_event_index;
        }
    }
    if (mountEventIndex == 0 || unmountEventIndex == 0) {
        return throw_illegal_state(env, "JVMTI virtual thread extension events were not found");
    }

    err = (*sharedJvmti)->SetExtensionEventCallback(sharedJvmti, mountEventIndex, mount_callback);
    if (err != JVMTI_ERROR_NONE) {
        return throw_illegal_state(env, "Failed to set the JVMTI virtual thread mount callback: %d", err);
    }
    err = (*sharedJvmti)->SetEventNotificationMode(sharedJvmti, JVMTI_ENABLE, mountEventIndex, NULL);
    if (err != JVMTI_ERROR_NONE) {
        jvmtiError cleanupError = uninstall_jvmti_hook(mountEventIndex);
        if (cleanupError != JVMTI_ERROR_NONE) {
            return throw_illegal_state(env, "Failed to enable the JVMTI virtual thread mount callback: %d; failed to uninstall the mount hook: %d",
                                       err, cleanupError);
        }
        return throw_illegal_state(env, "Failed to enable the JVMTI virtual thread mount callback: %d", err);
    }
    err = (*sharedJvmti)->SetExtensionEventCallback(sharedJvmti, unmountEventIndex, unmount_callback);
    if (err != JVMTI_ERROR_NONE) {
        jvmtiError cleanupError = uninstall_jvmti_hook(mountEventIndex);
        if (cleanupError != JVMTI_ERROR_NONE) {
            return throw_illegal_state(env, "Failed to set the JVMTI virtual thread unmount callback: %d; failed to uninstall the mount hook: %d",
                                       err, cleanupError);
        }
        return throw_illegal_state(env, "Failed to set the JVMTI virtual thread unmount callback: %d", err);
    }
    err = (*sharedJvmti)->SetEventNotificationMode(sharedJvmti, JVMTI_ENABLE, unmountEventIndex, NULL);
    if (err != JVMTI_ERROR_NONE) {
        jvmtiError unmountCleanupError = uninstall_jvmti_hook(unmountEventIndex);
        jvmtiError mountCleanupError = uninstall_jvmti_hook(mountEventIndex);
        if (unmountCleanupError != JVMTI_ERROR_NONE || mountCleanupError != JVMTI_ERROR_NONE) {
            return throw_illegal_state(env,
                                       "Failed to enable the JVMTI virtual thread unmount callback: %d; hook removal errors: unmount=%d, mount=%d",
                                       err, unmountCleanupError, mountCleanupError);
        }
        return throw_illegal_state(env, "Failed to enable the JVMTI virtual thread unmount callback: %d", err);
    }

    jvmtiInitialized = JNI_TRUE;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_polyglot_JDKSupport_registerJVMTIHook0(JNIEnv *env, jclass clz, jclass target) {
    jmethodID mountMethod = (*env)->GetStaticMethodID(env, target, "mountHook", "(Ljava/lang/Thread;)V");
    EXCEPTION_CHECK(env);
    jmethodID unmountMethod = (*env)->GetStaticMethodID(env, target, "unmountHook", "(Ljava/lang/Thread;)V");
    EXCEPTION_CHECK(env);

    lock_initialization();
    if (virtualThreadHooksClass != NULL) {
        unlock_initialization();
        throw_illegal_state(env, "Truffle virtual thread hooks have already been registered. Multiple optimized Truffle runtimes are not supported.");
        return;
    }

    /*
     * System.load ties the library's lifetime to its loading class loader. Keep the hook class
     * (defined by the same loader) strongly reachable so JVMTI never calls into an unloaded library.
     * The FFM path pins the library with Arena.global(), so its hook class can be held weakly and
     * the Truffle class loader can be collected independently of the native callbacks.
     */
    jobject hooksClass = libraryPinned ? (*env)->NewWeakGlobalRef(env, target) : (*env)->NewGlobalRef(env, target);
    if (hooksClass == NULL) {
        unlock_initialization();
        return;
    }
    virtualThreadMountMethod = mountMethod;
    virtualThreadUnmountMethod = unmountMethod;
    virtualThreadHooksClass = hooksClass;
    if (!initialize_jvmti(env)) {
        unlock_initialization();
        return;
    }
    unlock_initialization();
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_polyglot_JDKSupport_runPinned0(JNIEnv *env, jclass clz, jobject action) {
    return (*env)->CallObjectMethod(env, action, supplierGetMethod);
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_polyglot_JDKSupport_getPlatformStackEnd0(JNIEnv *env, jclass clz) {
#if defined(__linux__)
    pthread_attr_t attr;
    if (pthread_getattr_np(pthread_self(), &attr) != 0) {
        return 0L;
    }
    void *bottom = NULL;
    size_t size = 0;
    if (pthread_attr_getstack(&attr, &bottom, &size) != 0) {
        pthread_attr_destroy(&attr);
        return 0L;
    }
    size_t guard_size = 0;
    if (pthread_attr_getguardsize(&attr, &guard_size) != 0) {
        pthread_attr_destroy(&attr);
        return 0L;
    }
    pthread_attr_destroy(&attr);
    return (jlong) ((uintptr_t) bottom + guard_size);
#elif defined(__APPLE__)
    pthread_t self = pthread_self();
    uintptr_t stack_top = (uintptr_t) pthread_get_stackaddr_np(self);
    size_t stack_size = pthread_get_stacksize_np(self);
    return (jlong) stack_top - stack_size;
#elif defined(_WIN32)
    MEMORY_BASIC_INFORMATION minfo;
    uintptr_t stack_bottom;
    size_t stack_size;
    if (VirtualQuery(&minfo, &minfo, sizeof(minfo)) == 0) {
        return 0L;
    }
    return (jlong) minfo.AllocationBase;
#endif
}

typedef jint(JNICALL *GetCreatedJavaVMs_t)(JavaVM **, jsize, jsize *);

static GetCreatedJavaVMs_t find_get_created_java_vms(void) {
    GetCreatedJavaVMs_t getCreatedJavaVMs = NULL;
#if defined(__linux__) || defined(__APPLE__)
    getCreatedJavaVMs = (GetCreatedJavaVMs_t) dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs");
#elif defined(_WIN32)
    HMODULE jvm = GetModuleHandleA("jvm.dll");
    if (jvm != NULL) {
        getCreatedJavaVMs = (GetCreatedJavaVMs_t) GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
    }
#endif
    return getCreatedJavaVMs;
}

static JNIEnv *current_env(void) {
    GetCreatedJavaVMs_t getCreatedJavaVMs = find_get_created_java_vms();
#if defined(__linux__) || defined(__APPLE__)
    CHECK_TRUE_ELSE_RETURN(getCreatedJavaVMs != NULL, NULL,
                           "Cannot find the JNI_GetCreatedJavaVMs entry point. The Java VM library must be loaded with RTLD_GLOBAL");
#else
    CHECK_TRUE_ELSE_RETURN(getCreatedJavaVMs != NULL, NULL, "Cannot find the JNI_GetCreatedJavaVMs entry point of the loaded Java VM library");
#endif

    JavaVM *vm = NULL;
    jsize numberOfVMs = 0;
    jint err = getCreatedJavaVMs(&vm, 1, &numberOfVMs);
    CHECK_TRUE_ELSE_RETURN(err == JNI_OK, NULL, "JNI_GetCreatedJavaVMs failed: %d", err);
    CHECK_TRUE_ELSE_RETURN(numberOfVMs > 0, NULL, "No Java VM has been created in this process");

    JNIEnv *env = NULL;
    err = (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_21);
    CHECK_TRUE_ELSE_RETURN(err == JNI_OK, NULL, "GetEnv failed: %d", err);
    return env;
}

static jclass load_class(JNIEnv *env, jclass classClass, jmethodID forName, jobject loader, const char *className) {
    jstring name = (*env)->NewStringUTF(env, className);
    if (name == NULL) {
        return NULL;
    }
    return (jclass) (*env)->CallStaticObjectMethod(env, classClass, forName, name, JNI_FALSE, loader);
}

/*
 * Loads the classes that declare truffleattach native methods through the calling thread's context
 * class loader and binds the methods to this library. This entry point is used when the library is
 * loaded using java foreign access (panama), which does not associate the library with a class loader
 * and does not invoke JNI_OnLoad.
 */
JNIEXPORT jint JNICALL truffle_attach_init(void) {
    JNIEnv *env = current_env();
    if (env == NULL) {
        return 1;
    }
    if ((*env)->PushLocalFrame(env, 16) != JNI_OK) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        return 2;
    }

    CHECK_TRUE_ELSE_POP(initialize_shared_state(env, JNI_TRUE) == JNI_OK, 3);

    jclass threadClass = (*env)->FindClass(env, "java/lang/Thread");
    CHECK_TRUE_ELSE_POP(threadClass != NULL, 4);
    jmethodID currentThreadMethod = (*env)->GetStaticMethodID(env, threadClass, "currentThread", "()Ljava/lang/Thread;");
    CHECK_TRUE_ELSE_POP(currentThreadMethod != NULL, 5);
    jmethodID getContextClassLoader = (*env)->GetMethodID(env, threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
    CHECK_TRUE_ELSE_POP(getContextClassLoader != NULL, 6);
    jobject thread = (*env)->CallStaticObjectMethod(env, threadClass, currentThreadMethod);
    CHECK_TRUE_ELSE_POP(thread != NULL && !(*env)->ExceptionCheck(env), 7);
    jobject loader = (*env)->CallObjectMethod(env, thread, getContextClassLoader);
    CHECK_TRUE_ELSE_POP(!(*env)->ExceptionCheck(env), 8);
    jclass classClass = (*env)->FindClass(env, "java/lang/Class");
    CHECK_TRUE_ELSE_POP(classClass != NULL, 9);
    jmethodID forName = (*env)->GetStaticMethodID(env, classClass, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
    CHECK_TRUE_ELSE_POP(forName != NULL, 10);

    jclass jdkSupport = load_class(env, classClass, forName, loader, "com.oracle.truffle.polyglot.JDKSupport");
    CHECK_TRUE_ELSE_POP(jdkSupport != NULL && !(*env)->ExceptionCheck(env), 11);
    JNINativeMethod jdkSupportMethods[] = {
        { "addExports0", "(Ljava/lang/Module;Ljava/lang/String;Ljava/lang/Module;)V",
          (void *) Java_com_oracle_truffle_polyglot_JDKSupport_addExports0 },
        { "runPinned0", "(Ljava/util/function/Supplier;)Ljava/lang/Object;", (void *) Java_com_oracle_truffle_polyglot_JDKSupport_runPinned0 },
        { "registerJVMTIHook0", "(Ljava/lang/Class;)V", (void *) Java_com_oracle_truffle_polyglot_JDKSupport_registerJVMTIHook0 },
        { "getPlatformStackEnd0", "()J", (void *) Java_com_oracle_truffle_polyglot_JDKSupport_getPlatformStackEnd0 },
    };
    CHECK_TRUE_ELSE_POP(
        (*env)->RegisterNatives(env, jdkSupport, jdkSupportMethods, sizeof(jdkSupportMethods) / sizeof(jdkSupportMethods[0])) == JNI_OK, 12);

    (*env)->PopLocalFrame(env, NULL);
    return 0;
}
