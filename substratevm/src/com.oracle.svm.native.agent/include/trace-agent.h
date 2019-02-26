/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#ifndef TRACE_AGENT_H
#define TRACE_AGENT_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdbool.h>
#include <stdarg.h>

#include <jvmti.h>

#define MAX_PATH_LEN 4096

// A copy of the initial JNI function table before any modifications
extern jniNativeInterface *jnifun;

// Non-debug assertion
void __guarantee_fail(const char *test, const char *file, unsigned int line, const char *funcname);
#define guarantee(expr) \
  ((expr) \
   ? (void) (0) \
   : __guarantee_fail (#expr, __FILE__, __LINE__, __func__))

void trace_append_v(JNIEnv *env, const char *tracer, jclass clazz, jclass caller_class,
        const char *function, const char *result, va_list args);

extern const jobject TRACE_OBJECT_NULL;
extern const char * const TRACE_VALUE_NULL;
extern const char * const TRACE_VALUE_UNKNOWN;
extern const char * const TRACE_ARG_IGNORE;
extern const char * const TRACE_NEXT_ARG_UNQUOTED_TAG;

#ifdef __cplusplus
}
#endif

#endif /* TRACE_AGENT_H */
