/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
#ifndef UTIL_H
#define UTIL_H

#ifdef __cplusplus
extern "C" {
#endif

// A copy of the initial JNI function table before any modifications
extern jniNativeInterface *jnifun;

extern jmethodID java_lang_Class_forName3;
extern jmethodID java_lang_Class_getName;
extern jmethodID java_lang_Class_getConstructor;
extern jmethodID java_lang_Class_getDeclaredConstructor;

// Non-debug assertion
void __guarantee_fail(const char *test, const char *file, unsigned int line, const char *funcname);
#define guarantee(expr) \
  ((expr) \
   ? (void) (0) \
   : __guarantee_fail (#expr, __FILE__, __LINE__, __func__))

void OnVMStart_Util(jvmtiEnv *jvmti, JNIEnv *jni);

jclass get_caller_class(jint depth);
jobject get_object_arg(jint slot);

const char *get_cstr(JNIEnv *jni, jstring str);
void release_cstr(JNIEnv *jni, jstring str, const char *cstr);

jboolean clear_exception(JNIEnv *env);
jboolean test_exception(JNIEnv *env);

static inline jclass nn_class(jclass clazz) {
  return (clazz != NULL) ? clazz : TRACE_OBJECT_NULL;
}

static inline const char * nn_str(const char *cstr) {
  return (cstr != NULL) ? cstr : TRACE_VALUE_NULL;
}

static inline char *nn_bool(void *p) {
  return (p != NULL) ? TRACE_VALUE_TRUE : TRACE_VALUE_FALSE;
}

#ifdef __cplusplus
}
#endif

#endif /* UTIL_H */
