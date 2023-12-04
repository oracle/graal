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
#include <stdarg.h>
#include <stdint.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <poll.h>
#include <netdb.h>
#include <errno.h>
#include <dlfcn.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/time.h>
#include <limits.h>

#include <jni.h>

#define OS_OK 0
#define OS_ERR -1

/* Set by native-image during image build time. Indicates whether the built image is a static binary. */
extern int __svm_vm_is_static_binary;
/*
    The way JDK checks IPv6 support on Linux involves checking if inet_pton exists using JVM_FindLibraryEntry. That
    function in turn calls dlsym, which is a bad idea in a static binary.
    This header provides that symbol, allowing us to return its address through JVM_FindLibraryEntry.
*/
#include <arpa/inet.h>

#ifdef JNI_VERSION_9
    #define JVM_INTERFACE_VERSION 6
#else
    #define JVM_INTERFACE_VERSION 4
#endif

/* macros for restartable system calls */

#define RESTARTABLE(_cmd, _result) do { \
    _result = _cmd; \
  } while(((int)_result == OS_ERR) && (errno == EINTR))

#define RESTARTABLE_RETURN_INT(_cmd) do { \
  int _result; \
  RESTARTABLE(_cmd, _result); \
  return _result; \
} while(0)

JNIEXPORT void JNICALL initialize() {
}

JNIEXPORT int JNICALL JVM_GetInterfaceVersion() {
    return JVM_INTERFACE_VERSION;
}

#ifdef __linux__
/*
  Support for cpusets on Linux (JDK-6515172).

  Ported from `os::active_processor_count` in `src/hotspot/os/linux/os_linux.cpp`,
  omitting HotSpot specific logging statements.
*/

#include <sched.h>

#define assert(p, ...)

// Get the current number of available processors for this process.
// This value can change at any time during a process's lifetime.
// sched_getaffinity gives an accurate answer as it accounts for cpusets.
// If it appears there may be more than 1024 processors then we do a
// dynamic check - see 6515172 for details.
// If anything goes wrong we fallback to returning the number of online
// processors - which can be greater than the number available to the process.
static int linux_active_processor_count() {
  cpu_set_t cpus;  // can represent at most 1024 (CPU_SETSIZE) processors
  cpu_set_t* cpus_p = &cpus;
  int cpus_size = sizeof(cpu_set_t);

  int configured_cpus = sysconf(_SC_NPROCESSORS_CONF);  // upper bound on available cpus
  int cpu_count = 0;

#if 0 /* Disabled due to GR-33678 */
  if (configured_cpus >= CPU_SETSIZE) {
    // kernel may use a mask bigger than cpu_set_t
    cpus_p = CPU_ALLOC(configured_cpus);
    if (cpus_p != NULL) {
      cpus_size = CPU_ALLOC_SIZE(configured_cpus);
      // zero it just to be safe
      CPU_ZERO_S(cpus_size, cpus_p);
    }
    else {
      // failed to allocate so fallback to online cpus
      int online_cpus = sysconf(_SC_NPROCESSORS_ONLN);
      return online_cpus;
    }
  }
#endif /* GR-33678 */

  // pid 0 means the current thread - which we have to assume represents the process
  if (sched_getaffinity(0, cpus_size, cpus_p) == 0) {
    if (cpus_p != &cpus) {
      cpu_count = CPU_COUNT_S(cpus_size, cpus_p);
    }
    else {
      cpu_count = CPU_COUNT(cpus_p);
    }
  }
  else {
    cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
  }

#if 0 /* Disabled due to GR-33678 */
  if (cpus_p != &cpus) {
    CPU_FREE(cpus_p);
  }
#endif /* GR-33678 */

  assert(cpu_count > 0 && cpu_count <= configured_cpus, "sanity check");
  return cpu_count;
}
#endif /* __linux__ */

JNIEXPORT int JNICALL JVM_ActiveProcessorCount() {
#ifdef __linux__
    return linux_active_processor_count();
#else
    return sysconf(_SC_NPROCESSORS_ONLN);
#endif
}

JNIEXPORT int JNICALL JVM_Connect(int fd, struct sockaddr* him, socklen_t len) {
    RESTARTABLE_RETURN_INT(connect(fd, him, len));
}

JNIEXPORT void* JNICALL JVM_FindLibraryEntry(void* handle, const char* name) {
    /*
        Calls to this function from a static binary are inherently unsafe. On some libc implementations, it may result
        in a segfault, while on others, the symbol could be wrongly not found. As of JDK11, this function is invoked in
        only one place: IPv6 support checking.
        As a safeguard, we restrict access to only known used symbols from the JDK code. If a future version introduces
        a dependency on another symbol, it could result in hard-to-find bugs. Therefore, calling this function from a
        static binary with an unknown symbol terminates the program.
    */
    if (__svm_vm_is_static_binary) {
        if (strcmp(name, "inet_pton") == 0) {
            return inet_pton;
        }
        fprintf(stderr, "Internal error: JVM_FindLibraryEntry called from a static native image with symbol: %s. Results may be unpredictable. Please report this issue to the SubstrateVM team.", name);
        fflush(stderr);
        exit(1);
    } else {
        return dlsym(handle, name);
    }
}

JNIEXPORT int JNICALL JVM_GetHostName(char* name, int namelen) {
    return gethostname(name, namelen);
}

JNIEXPORT int JNICALL JVM_GetSockOpt(int fd, int level, int optname,
                            char *optval, socklen_t* optlen) {
    return getsockopt(fd, level, optname, optval, optlen);
}

JNIEXPORT int JNICALL JVM_Socket(int domain, int type, int protocol) {
    return socket(domain, type, protocol);
}

JNIEXPORT int JNICALL JVM_GetSockName(int fd, struct sockaddr* him, socklen_t* len) {
    return getsockname(fd, him, len);
}

JNIEXPORT int JNICALL JVM_Listen(int fd, int count) {
    return listen(fd, count);
}

JNIEXPORT int JNICALL JVM_Send(int fd, char* buf, size_t nBytes, unsigned int flags) {
    RESTARTABLE_RETURN_INT(send(fd, buf, nBytes, flags));
}

JNIEXPORT int JNICALL JVM_SetSockOpt(int fd, int level, int optname,
                            const char* optval, socklen_t optlen) {
    return setsockopt(fd, level, optname, optval, optlen);
}

JNIEXPORT int JNICALL JVM_SocketAvailable(int fd, int *pbytes) {
    int ret;

    if (fd < 0)
        return OS_OK;

    RESTARTABLE(ioctl(fd, FIONREAD, pbytes), ret);

    return (ret == OS_ERR) ? 0 : 1;
}

JNIEXPORT int JNICALL JVM_SocketClose(int fd) {
    return close(fd);
}

JNIEXPORT int JNICALL JVM_SocketShutdown(int fd, int howto) {
    return shutdown(fd, howto);
}

/* Called directly from several native functions */
JNIEXPORT int JNICALL JVM_InitializeSocketLibrary() {
    /* A noop, returns 0 in hotspot */
   return 0;
}

JNIEXPORT jlong JNICALL Java_java_lang_System_currentTimeMillis(void *env, void * ignored) {
    struct timeval time;
    int status = gettimeofday(&time, NULL);
    return (jlong)(time.tv_sec * 1000)  +  (jlong)(time.tv_usec / 1000);
}

JNIEXPORT jlong JNICALL Java_java_lang_System_nanoTime(void *env, void * ignored) {
    // get implementation from hotspot/os/bsd/os_bsd.cpp
    // for now, just return 1000 * microseconds
    struct timeval time;
    int status = gettimeofday(&time, NULL);
    return (jlong)(time.tv_sec * 1000000000)  +  (jlong)(time.tv_usec * 1000);
}

JNIEXPORT jlong JNICALL JVM_CurrentTimeMillis(void *env, void * ignored) {
    return Java_java_lang_System_currentTimeMillis(env, ignored);
}

JNIEXPORT jlong JNICALL JVM_NanoTime(void *env, void * ignored) {
    return Java_java_lang_System_nanoTime(env, ignored);
}

JNIEXPORT jlong JNICALL JVM_GetNanoTimeAdjustment(void *env, void * ignored, jlong offset_secs) {
    int64_t maxDiffSecs = 0x0100000000LL;
    int64_t minDiffSecs = -maxDiffSecs;
    struct timeval time;
    int status = gettimeofday(&time, NULL);

    int64_t seconds = time.tv_sec;
    int64_t nanos = time.tv_usec * 1000;

    int64_t diff = seconds - offset_secs;
    if (diff >= maxDiffSecs || diff <= minDiffSecs) {
        return -1;
    }
    return diff * 1000000000LL + nanos;
}

JNIEXPORT jlong JNICALL Java_jdk_internal_misc_VM_getNanoTimeAdjustment(void *env, void * ignored, jlong offset_secs) {
    return JVM_GetNanoTimeAdjustment(env, ignored, offset_secs);
}

JNIEXPORT void JNICALL JVM_Halt(int retcode) {
    exit(retcode);
}

JNIEXPORT void JNICALL JVM_BeforeHalt() {
}

JNIEXPORT int JNICALL JVM_GetLastErrorString(char *buf, int len) {
    const char *s;
    size_t n;

    if (errno == 0) {
        return 0;
    }

    s = strerror(errno);
    n = strlen(s);
    if (n >= len) {
        n = len - 1;
    }

    strncpy(buf, s, n);
    buf[n] = '\0';
    return n;
}

JNIEXPORT jobject JNICALL JVM_DoPrivileged(JNIEnv *env, jclass cls, jobject action, jobject context, jboolean wrapException) {
    jclass actionClass = (*env)->FindClass(env, "java/security/PrivilegedAction");
    if (actionClass != NULL && !(*env)->ExceptionCheck(env)) {
        jmethodID run = (*env)->GetMethodID(env, actionClass, "run", "()Ljava/lang/Object;");
        if (run != NULL && !(*env)->ExceptionCheck(env)) {
            return (*env)->CallObjectMethod(env, action, run);
        }
    }

    /* Some error occurred - clear pending exception and try to report the error. */
    (*env)->ExceptionClear(env);

    jclass errorClass = (*env)->FindClass(env, "java/lang/InternalError");
    if (errorClass != NULL && !(*env)->ExceptionCheck(env)) {
        (*env)->ThrowNew(env, errorClass, "Could not invoke PrivilegedAction");
    } else {
        (*env)->ExceptionClear(env);
        (*env)->FatalError(env, "PrivilegedAction could not be invoked and the error could not be reported");
    }
    return NULL;
}

#ifdef __APPLE__
char temp_path_storage[PATH_MAX];
JNIEXPORT jstring JNICALL JVM_GetTemporaryDirectory(JNIEnv *env) {
    // see os_bsd.cpp line 910
    static char *temp_path = NULL;
    if (temp_path == NULL) {
        int pathSize = confstr(_CS_DARWIN_USER_TEMP_DIR, temp_path_storage, PATH_MAX);
        if (pathSize == 0 || pathSize > PATH_MAX) {
            strlcpy(temp_path_storage, "/tmp/", sizeof(temp_path_storage));
        }
        temp_path = temp_path_storage;
    }
    return (*env)->NewStringUTF(env, temp_path);
}
#else
JNIEXPORT jstring JNICALL JVM_GetTemporaryDirectory(JNIEnv *env) {
    // see os_linux.cpp line 1380
    return (*env)->NewStringUTF(env, "/tmp");
}
#endif /* __APPLE__ */

JNIEXPORT jobject JNICALL Java_sun_nio_ch_sctp_SctpChannelImpl_initIDs(JNIEnv *env) {
    (*env)->FatalError(env, "Currently SCTP not supported for native-images");
    return NULL;
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
