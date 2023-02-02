/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

/* JVM_ functions imported from the hotspot sources */
#include <stdio.h>
#include <stdint.h>
#include <windows.h>
#include <errno.h>

#include <jni.h>

#define BitsPerByte 8

#ifdef JNI_VERSION_9
    #define JVM_INTERFACE_VERSION 6
#else
    #define JVM_INTERFACE_VERSION 4
#endif

static int _processor_count = 0;
static jlong _performance_frequency = 0L;

jlong jlong_from(DWORD high, DWORD low) {
    return ((((uint64_t)high) << 32) | low);
}

JNIEXPORT int JNICALL JVM_GetInterfaceVersion() {
    return JVM_INTERFACE_VERSION;
}

jlong as_long(LARGE_INTEGER x) {
    return jlong_from(x.HighPart, x.LowPart);
}

JNIEXPORT void JNICALL initialize() {
  LARGE_INTEGER count;
  SYSTEM_INFO si;
  GetSystemInfo(&si);
  _processor_count = si.dwNumberOfProcessors;

  if (QueryPerformanceFrequency(&count)) {
      _performance_frequency = as_long(count);
  }
}

JNIEXPORT int JNICALL JVM_ActiveProcessorCount() {
    DWORD_PTR lpProcessAffinityMask = 0;
    DWORD_PTR lpSystemAffinityMask = 0;
    if (_processor_count <= sizeof(UINT_PTR) * BitsPerByte &&
        GetProcessAffinityMask(GetCurrentProcess(), &lpProcessAffinityMask, &lpSystemAffinityMask)) {
        int bitcount = 0;
        // Nof active processors is number of bits in process affinity mask
        while (lpProcessAffinityMask != 0) {
            lpProcessAffinityMask = lpProcessAffinityMask & (lpProcessAffinityMask-1);
            bitcount++;
        }
        return bitcount;
    } else {
        return _processor_count;
    }
}

HANDLE interrupt_event = NULL;

JNIEXPORT HANDLE JNICALL JVM_GetThreadInterruptEvent() {
    if (interrupt_event != NULL) {
        return interrupt_event;
    }
    interrupt_event = CreateEvent(NULL, TRUE, FALSE, NULL);
    return interrupt_event;
}

/* Called directly from several native functions */
JNIEXPORT int JNICALL JVM_InitializeSocketLibrary() {
    /* A noop, returns 0 in hotspot */
   return 0;
}

static const jlong _offset               = 116444736000000000L;
static const jlong NANOSECS_PER_SEC      = 1000000000L;
static const jint  NANOSECS_PER_MILLISEC = 1000000;

// Returns time ticks in (10th of micro seconds)
static __inline jlong windows_to_time_ticks(FILETIME wt) {
  jlong a = jlong_from(wt.dwHighDateTime, wt.dwLowDateTime);
  return (a - _offset);
}

static __inline jlong getCurrentTimeMillis() {
    jlong a;
    FILETIME wt;
    jlong ticks;
    GetSystemTimeAsFileTime(&wt);
    ticks = windows_to_time_ticks(wt);
    return ticks / 10000;
}

JNIEXPORT jlong JNICALL Java_java_lang_System_nanoTime(void *env, void * ignored) {
    LARGE_INTEGER current_count;
    double current, freq;
    jlong time;

    if (_performance_frequency == 0L) {
        return (getCurrentTimeMillis() * NANOSECS_PER_MILLISEC);
    }

    QueryPerformanceCounter(&current_count);
    current = as_long(current_count);
    freq = _performance_frequency;
    time = (jlong)((current/freq) * NANOSECS_PER_SEC);
    return time;
}

JNIEXPORT jlong JNICALL JVM_NanoTime(void *env, void * ignored) {
    return Java_java_lang_System_nanoTime(env, ignored);
}

JNIEXPORT jlong JNICALL Java_java_lang_System_currentTimeMillis(void *env, void * ignored) {
    return getCurrentTimeMillis();
}

JNIEXPORT jlong JNICALL JVM_CurrentTimeMillis(void *env, void * ignored) {
    return Java_java_lang_System_currentTimeMillis(env, ignored);
}

static void os_javaTimeSystemUTC(jlong *seconds, jlong *nanos) {
  FILETIME wt;
  jlong ticks;
  jlong secs;
  GetSystemTimeAsFileTime(&wt);
  ticks = windows_to_time_ticks(wt); // 10th of micros
  secs = ticks / 10000000; // 10000 * 1000
  *seconds = secs;
  *nanos = ((jlong)(ticks - (secs*10000000))) * 100;
}

/* Taken from src/hotspot/share/prims/jvm.cpp */
#define CONST64(x)  (x ## LL)

static const jlong MAX_DIFF_SECS = CONST64(0x0100000000); //  2^32
static const jlong MIN_DIFF_SECS = -CONST64(0x0100000000); // -2^32

JNIEXPORT jlong JNICALL JVM_GetNanoTimeAdjustment(void *env, void *ignored, jlong offset_secs) {
    jlong seconds;
    jlong nanos;
    jlong diff;

    os_javaTimeSystemUTC(&seconds, &nanos);

    diff = seconds - offset_secs;
    if (diff >= MAX_DIFF_SECS || diff <= MIN_DIFF_SECS) {
       return -1; // sentinel value: the offset is too far off the target
    }

    return (diff * (jlong)1000000000) + nanos;
}

JNIEXPORT jlong JNICALL Java_jdk_internal_misc_VM_getNanoTimeAdjustment(void *env, void * ignored, jlong offset_secs) {
    return JVM_GetNanoTimeAdjustment(env, ignored, offset_secs);
}

JNIEXPORT void JNICALL JVM_BeforeHalt() {
}

JNIEXPORT void JNICALL JVM_Halt(int retcode) {
    _exit(retcode);
}

JNIEXPORT int JNICALL JVM_GetLastErrorString(char *buf, int len) {
    DWORD errval;

    if ((errval = GetLastError()) != 0) {
      /* DOS error */
      size_t n = (size_t)FormatMessage(
            FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL,
            errval,
            0,
            buf,
            (DWORD)len,
            NULL);
      if (n > 3) {
        /* Drop final '.', CR, LF */
        if (buf[n - 1] == '\n') n--;
        if (buf[n - 1] == '\r') n--;
        if (buf[n - 1] == '.') n--;
        buf[n] = '\0';
      }
      return n;
    }

    if (errno != 0) {
      /* C runtime error that has no corresponding DOS error code */
      const char* s = strerror(errno);
      size_t n = strlen(s);
      if (n >= len) n = len - 1;
      strncpy(buf, s, n);
      buf[n] = '\0';
      return n;
    }

  return 0;
}

JNIEXPORT jobject JNICALL JVM_DoPrivileged(JNIEnv *env, jclass cls, jobject action, jobject context, jboolean wrapException) {
    jclass errorClass;
    jclass actionClass = (*env)->FindClass(env, "java/security/PrivilegedAction");
    if (actionClass != NULL && !(*env)->ExceptionCheck(env)) {
        jmethodID run = (*env)->GetMethodID(env, actionClass, "run", "()Ljava/lang/Object;");
        if (run != NULL && !(*env)->ExceptionCheck(env)) {
            return (*env)->CallObjectMethod(env, action, run);
        }
    }
    errorClass = (*env)->FindClass(env, "java/lang/InternalError");
    if (errorClass != NULL && !(*env)->ExceptionCheck(env)) {
        (*env)->ThrowNew(env, errorClass, "Could not invoke PrivilegedAction");
    } else {
        (*env)->FatalError(env, "PrivilegedAction could not be invoked and the error could not be reported");
    }
    return NULL;
}

JNIEXPORT jstring JNICALL JVM_GetTemporaryDirectory(JNIEnv *env) {
    // see os_windows.cpp line 1367
    static char path_buf[MAX_PATH];
    if (GetTempPath(MAX_PATH, path_buf) <= 0) {
        path_buf[0] = '\0';
    }
    return (*env)->NewStringUTF(env, path_buf);
}

jboolean VerifyFixClassname(char *utf_name) {
    fprintf(stderr, "VerifyFixClassname(%s) called:  Unimplemented\n", utf_name);
    abort();
}

jboolean VerifyClassname(char *utf_name, jboolean arrayAllowed) {
    fprintf(stderr, "VerifyClassname(%s, %d) called:  Unimplemented\n", utf_name, arrayAllowed);
    abort();
}

int jio_vfprintf(FILE* f, const char *fmt, va_list args) {
  return vfprintf(f, fmt, args);
}

int jio_vsnprintf(char *str, size_t count, const char *fmt, va_list args) {
  int result;

  if ((intptr_t)count <= 0) return -1;

  result = vsnprintf(str, count, fmt, args);
  if ((result > 0 && (size_t)result >= count) || result == -1) {
    str[count - 1] = '\0';
    result = -1;
  }

  return result;
}

#ifdef JNI_VERSION_9
/*
 * Both `jio_snprintf` and `jio_fprintf` as defined in `src/java.base/share/native/libjava/jio.c`
 * are no longer part of `STATIC_BUILD`, which is used to build static JDK libraries, so we redefine
 * them here.
 */
JNIEXPORT int jio_snprintf(char *str, size_t count, const char *fmt, ...) {
    int len;

    va_list args;
    va_start(args, fmt);
    len = jio_vsnprintf(str, count, fmt, args);
    va_end(args);

    return len;
}

JNIEXPORT int jio_fprintf(FILE *fp, const char *fmt, ...) {
    int len;

    va_list args;
    va_start(args, fmt);
    len = jio_vfprintf(fp, fmt, args);
    va_end(args);

    return len;
}
#endif

/*
 * Signal support, used to implement the shutdown sequence.  Every VM must
 * support JVM_SIGINT and JVM_SIGTERM, raising the former for user interrupts
 * (^C) and the latter for external termination (kill, system shutdown, etc.).
 * Other platform-dependent signal values may also be supported.
 */
#include "JvmFuncs_SignalImpl.h"

JNIEXPORT jint JNICALL JVM_FindSignal(const char *name) {
  return os__get_signal_number(name);
}

JNIEXPORT jboolean JNICALL JVM_RaiseSignal(jint sig) {
  os__signal_raise(sig);
  return JNI_TRUE;
}

JNIEXPORT void * JNICALL JVM_RegisterSignal(jint sig, void *handler) {
  // Copied from classic vm
  // signals_md.c       1.4 98/08/23
  void* newHandler = handler == (void *)2
                   ? os__user_handler()
                   : handler;

  void* oldHandler = os__signal(sig, newHandler);
  if (oldHandler == os__user_handler()) {
      return (void *)2;
  } else {
      return oldHandler;
  }
}
