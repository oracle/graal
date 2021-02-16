/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#if !defined(_WIN32)

#ifndef _JAVASOFT_JVM_MD_H_
#define _JAVASOFT_JVM_MD_H_

#include <jni.h>

/*
 * This file is currently collecting system-specific dregs for the
 * JNI conversion, which should be sorted out later.
 */
#include <sys/param.h>          /* For MAXPATHLEN */
#include <unistd.h>             /* For F_OK, R_OK, W_OK */
#include <stddef.h>             /* For ptrdiff_t */
#include <stdint.h>             /* For uintptr_t */
#include <sys/socket.h>

#define JNI_ONLOAD_SYMBOLS   {"JNI_OnLoad"}
#define JNI_ONUNLOAD_SYMBOLS {"JNI_OnUnload"}

#define JNI_LIB_PREFIX "lib"
#ifdef __APPLE__
#define JNI_LIB_SUFFIX ".dylib"
#define VERSIONED_JNI_LIB_NAME(NAME, VERSION) JNI_LIB_PREFIX NAME "." VERSION JNI_LIB_SUFFIX
#else
#define JNI_LIB_SUFFIX ".so"
#define VERSIONED_JNI_LIB_NAME(NAME, VERSION) JNI_LIB_PREFIX NAME JNI_LIB_SUFFIX "." VERSION
#endif
#define JNI_LIB_NAME(NAME) JNI_LIB_PREFIX NAME JNI_LIB_SUFFIX

#define JVM_MAXPATHLEN MAXPATHLEN

#define JVM_R_OK    R_OK
#define JVM_W_OK    W_OK
#define JVM_X_OK    X_OK
#define JVM_F_OK    F_OK

/*
 * File I/O
 */

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/signal.h>

/*
 * Macros to use the right data type for file descriptors
 */
#define FD jint

/*
 * Retry the operation if it is interrupted
 */
#define RESTARTABLE(_cmd, _result) do { \
    do { \
        _result = _cmd; \
    } while((_result == -1) && (errno == EINTR)); \
} while(0)

/* O Flags */

#define JVM_O_RDONLY     O_RDONLY
#define JVM_O_WRONLY     O_WRONLY
#define JVM_O_RDWR       O_RDWR
#define JVM_O_O_APPEND   O_APPEND
#define JVM_O_EXCL       O_EXCL
#define JVM_O_CREAT      O_CREAT
#define JVM_O_DELETE     0x10000

/* Signals */

#define JVM_SIGINT     SIGINT
#define JVM_SIGTERM    SIGTERM

/* Misc */

// This code originates from JDK's sysOpen and open64_w
// from src/solaris/hpi/src/system_md.c

#ifndef O_DELETE
#define O_DELETE 0x10000
#endif

#define K           (1024)

#define MAX_PATH    (2 * K)

JNIEXPORT int JNICALL JVM_handle_linux_signal(int sig,
                          siginfo_t* info,
                          void* ucVoid,
                          int abort_if_unrecognized);

#endif /* !_JAVASOFT_JVM_MD_H_ */
 
#endif /* !defined(_WIN32) */
