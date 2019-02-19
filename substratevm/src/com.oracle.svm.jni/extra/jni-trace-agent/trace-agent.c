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
#ifdef __cplusplus
extern "C" {
#endif

#include "trace-agent.h"

#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#include "jni-agent.h"
#include "reflect-agent.h"
#include "sbuf.h"

void __guarantee_fail(const char *test, const char *file, unsigned int line, const char *funcname) {
    fprintf(stderr, "%s:%u: %s: check failed, aborting: %s\n", file, line, funcname, test);
    exit(1);
}

jniNativeInterface *jnifun;

static jmethodID java_lang_Class_getName;

static FILE *trace_file;
static pthread_mutex_t trace_mtx;
const jclass TRACE_OBJECT_NULL = (jclass) -1; // unlikely to be a valid jclass
const char * const TRACE_VALUE_NULL = "null";
const char * const TRACE_VALUE_UNKNOWN = "?";
const char * const TRACE_NEXT_ARG_UNQUOTED_TAG = "@next_unquoted@";

void JNICALL OnVMStart(jvmtiEnv *jvmti, JNIEnv *jni) {
  guarantee((*jvmti)->GetJNIFunctionTable(jvmti, &jnifun) == JVMTI_ERROR_NONE);

  jclass javaLangClass;
  guarantee((javaLangClass = jnifun->FindClass(jni, "java/lang/Class")) != NULL);
  guarantee((java_lang_Class_getName = jnifun->GetMethodID(jni, javaLangClass, "getName", "()Ljava/lang/String;")) != NULL);

  OnVMStart_JNI(jvmti, jni);
  OnVMStart_Reflection(jvmti, jni);
}

void JNICALL OnVMInit(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  OnVMInit_Reflection(jvmti, jni, thread);
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  jvmtiEnv* jvmti = NULL;
  guarantee((*vm)->GetEnv(vm, (void**) &jvmti, JVMTI_VERSION) == JNI_OK);

  const char output_opt[] = "output=";
  guarantee(strstr(options, output_opt) == options);
  const char *output = options + sizeof(output_opt) - 1;
  guarantee((trace_file = fopen(output, "w")) != NULL);
  guarantee(fputs("[\n", trace_file) > 0);

  guarantee(pthread_mutex_init(&trace_mtx, NULL) == 0);

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof (callbacks));
  callbacks.VMStart = &OnVMStart;
  callbacks.VMInit = &OnVMInit;

  jint result = OnLoad_Reflection(vm, options, jvmti, &callbacks);
  if (result != JNI_OK) {
    return result;
  }

  guarantee((*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof (callbacks)) == JVMTI_ERROR_NONE);

  guarantee((*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_START, NULL) == JVMTI_ERROR_NONE);
  guarantee((*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL) == JVMTI_ERROR_NONE);

  return JNI_OK;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
  fputs("]", trace_file);
  fclose(trace_file);
  pthread_mutex_destroy(&trace_mtx);
}

static void mtx_trace_print(const char *s) {
  pthread_mutex_lock(&trace_mtx);
  fputs(s, trace_file);
  pthread_mutex_unlock(&trace_mtx);
}

static bool should_quote(const char *s) {
  return (s != TRACE_VALUE_NULL && s != TRACE_VALUE_UNKNOWN);
}

#define SBUF_PRINT_OPTIONAL_QUOTE(condition, sbuf, fmt, ...) \
  sbuf_printf((sbuf), ((condition) ? (fmt "\"%s\"") : (fmt "%s")), __VA_ARGS__)

void trace_append_v(JNIEnv *env, const char *tracer, jclass clazz, const char *function, const char *result, va_list args) {
  struct sbuf e;
  sbuf_new(&e);
  sbuf_printf(&e, "{\"tracer\":\"%s\"", tracer);
  if (function != NULL) {
    SBUF_PRINT_OPTIONAL_QUOTE(should_quote(function), &e, ", \"function\":", function);
  }
  if (clazz != NULL) {
    jstring clazz_name = NULL;
    const char *clazz_name_cstr = NULL;
    if (clazz != TRACE_OBJECT_NULL) {
      jclass clazz_class = jnifun->GetObjectClass(env, clazz);
      clazz_name = (clazz_class != NULL) ? jnifun->CallObjectMethod(env, clazz, java_lang_Class_getName) : NULL;
      clazz_name_cstr = (clazz_name != NULL) ? jnifun->GetStringUTFChars(env, clazz_name, NULL) : NULL;
    }
    clazz_name_cstr = (clazz_name_cstr != NULL) ? clazz_name_cstr : TRACE_VALUE_NULL;
    SBUF_PRINT_OPTIONAL_QUOTE(should_quote(clazz_name_cstr), &e, ", \"class\":", clazz_name_cstr);
    if (clazz_name_cstr != TRACE_VALUE_NULL) {
      jnifun->ReleaseStringUTFChars(env, clazz_name, clazz_name_cstr);
    }
  }
  if (result != NULL) {
    SBUF_PRINT_OPTIONAL_QUOTE(should_quote(result), &e, ", \"function\":", result);
  }
  char *arg = va_arg(args, char*);
  if (arg != NULL) {
    sbuf_printf(&e, ", \"args\":");
    char prefix = '[';
    bool quote_next = true;
    do {
      if (arg == TRACE_NEXT_ARG_UNQUOTED_TAG) {
        quote_next = false;
      } else {
        SBUF_PRINT_OPTIONAL_QUOTE(quote_next && should_quote(arg), &e, "%c", prefix, arg);
        quote_next = true;
        prefix = ',';
      }
      arg = va_arg(args, char*);
    } while (arg != NULL);
    sbuf_printf(&e, "]");
  }
  sbuf_printf(&e, "},\n");
  mtx_trace_print(sbuf_as_cstr(&e));
  sbuf_destroy(&e);
}

#ifdef __cplusplus
}
#endif
