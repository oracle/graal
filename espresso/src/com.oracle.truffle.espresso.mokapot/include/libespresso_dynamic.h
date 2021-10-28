/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#ifndef __LIBESPRESSO_H
#define __LIBESPRESSO_H

#include <graal_isolate_dynamic.h>


#if defined(__cplusplus)
extern "C" {
#endif

typedef int (*Espresso_CreateJavaVM_fn_t)(graal_isolatethread_t* thread, struct JavaVM_** javaVMPointer, struct JNIEnv_** penv, JavaVMInitArgs* args);

typedef int (*Espresso_EnterContext_fn_t)(graal_isolatethread_t* thread, struct JavaVM_* javaVM);

typedef int (*Espresso_LeaveContext_fn_t)(graal_isolatethread_t* thread, struct JavaVM_* javaVM);

typedef int (*Espresso_ReleaseContext_fn_t)(graal_isolatethread_t* thread, struct JavaVM_* javaVM);

typedef int (*Espresso_CloseContext_fn_t)(graal_isolatethread_t* thread, struct JavaVM_* javaVM);

typedef void (*Espresso_Exit_fn_t)(graal_isolatethread_t* thread, struct JavaVM_* javaVM);

#if defined(__cplusplus)
}
#endif
#endif
