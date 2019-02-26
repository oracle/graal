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
#ifdef __cplusplus
extern "C" {
#endif

#include "trace-agent.h"
#include "util.h"

#include <assert.h>

// JVMTI environments, unlike JNI, can be safely shared across threads
static jvmtiEnv *global_jvmti = NULL;

jniNativeInterface *jnifun;

jclass java_lang_Class;
jmethodID java_lang_Class_getName;
jmethodID java_lang_Class_getConstructor;
jmethodID java_lang_Class_getDeclaredConstructor;

void __guarantee_fail(const char *test, const char *file, unsigned int line, const char *funcname) {
    fprintf(stderr, "%s:%u: %s: check failed, aborting: %s\n", file, line, funcname, test);
    exit(1);
}

void OnVMStart_Util(jvmtiEnv *jvmti, JNIEnv *jni) {
  global_jvmti = jvmti;

  guarantee((*jvmti)->GetJNIFunctionTable(jvmti, &jnifun) == JVMTI_ERROR_NONE);

  guarantee((java_lang_Class = jnifun->FindClass(jni, "java/lang/Class")) != NULL);
  guarantee((java_lang_Class = jnifun->NewGlobalRef(jni, java_lang_Class)) != NULL);
  guarantee((java_lang_Class_getName = jnifun->GetMethodID(jni, java_lang_Class, "getName", "()Ljava/lang/String;")) != NULL);
  guarantee((java_lang_Class_getConstructor = jnifun->GetMethodID(jni, java_lang_Class, "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;")) != NULL);
  guarantee((java_lang_Class_getDeclaredConstructor = jnifun->GetMethodID(jni, java_lang_Class, "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;")) != NULL);
}

jclass get_caller_class(jint depth) {
  jvmtiFrameInfo info;
  jint count;
  if ((*global_jvmti)->GetStackTrace(global_jvmti, NULL, depth, 1, &info, &count) == JVMTI_ERROR_NONE && count == 1) {
    jclass declaring;
    if ((*global_jvmti)->GetMethodDeclaringClass(global_jvmti, info.method, &declaring) == JVMTI_ERROR_NONE) {
      return declaring;
    }
  }
  return NULL;
}

jobject get_object_arg(jint slot) {
  jobject arg;
  jvmtiError code = (*global_jvmti)->GetLocalObject(global_jvmti, NULL, 0, slot, &arg);
  if (code != JVMTI_ERROR_NONE) {
    fprintf(stderr, "WARNING: GetLocalObject of local %d failed with error %d.\n", (int)slot, (int)code);
    arg = NULL;
  }
  return arg;
}

const char *get_cstr(JNIEnv *jni, jstring str) {
  const char *cstr = (str != NULL) ? jnifun->GetStringUTFChars(jni, str, NULL) : NULL;
  return (cstr != NULL) ? cstr : TRACE_VALUE_NULL;
}

void release_cstr(JNIEnv *jni, jstring str, const char *cstr) {
  assert(cstr != NULL);
  if (cstr != TRACE_VALUE_NULL) {
    jnifun->ReleaseStringUTFChars(jni, str, cstr);
  }
}

jboolean test_exception(JNIEnv *env) {
  if (jnifun->ExceptionCheck(env)) {
    jnifun->ExceptionDescribe(env);
    return JNI_TRUE;
  }
  return JNI_FALSE;
}

#ifdef __cplusplus
}
#endif
