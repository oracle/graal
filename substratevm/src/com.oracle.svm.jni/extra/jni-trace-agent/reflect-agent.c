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
#include "sbuf.h"

#include <string.h>
#include <malloc.h>
#include <assert.h>

static jclass java_lang_Class;
static jmethodID java_lang_Class_getName;
static jmethodID java_lang_Class_getConstructor;
static jmethodID java_lang_Class_getDeclaredConstructor;

static void reflect_trace(JNIEnv *env, jclass clazz, const char *function, ...) {
  va_list ap;
  va_start(ap, function);
  trace_append_v(env, "reflect", clazz, function, NULL, ap);
  va_end(ap);
}
struct reflect_breakpoint_entry;

typedef void (*reflect_breakpoint_handler)(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp);

struct reflect_breakpoint_entry {
  jmethodID methodID;
  jlocation location;
  const char *class_name;
  const char *name;
  const char *signature;
  reflect_breakpoint_handler handler;
};

static jobject get_arg(jvmtiEnv *jvmti, jthread thread, jint slot) {
  jobject arg;
  jvmtiError code = (*jvmti)->GetLocalObject(jvmti, thread, 0, slot, &arg);
  if (code != JVMTI_ERROR_NONE) {
    fprintf(stderr, "WARNING: GetLocalObject of local %d failed with error %d.\n", (int)slot, (int)code);
    arg = NULL;
  }
  return arg;
}

static const char *get_cstr(JNIEnv *jni, jstring str) {
  const char *cstr = (str != NULL) ? jnifun->GetStringUTFChars(jni, str, NULL) : NULL;
  return (cstr != NULL) ? cstr : TRACE_VALUE_NULL;
}

static void release_cstr(JNIEnv *jni, jstring str, const char *cstr) {
  assert(cstr != NULL);
  if (cstr != TRACE_VALUE_NULL) {
    jnifun->ReleaseStringUTFChars(jni, str, cstr);
  }
}

static void OnBreakpoint_forName(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  jstring name = get_arg(jvmti, thread, 0);
  const char *name_cstr = get_cstr(jni, name);
  reflect_trace(jni, java_lang_Class, bp->name, name_cstr, NULL);
  release_cstr(jni, name, name_cstr);
}

static void OnBreakpoint_bulkGetMembers(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  jclass self = get_arg(jvmti, thread, 0);;
  reflect_trace(jni, self, bp->name, NULL);
}

static void OnBreakpoint_getSingleField(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  jclass self = get_arg(jvmti, thread, 0);
  jstring name = get_arg(jvmti, thread, 1);
  const char *name_cstr = get_cstr(jni, name);
  reflect_trace(jni, self, bp->name, name_cstr, NULL);
  release_cstr(jni, name, name_cstr);
}

static void OnBreakpoint_getSingleMethod(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  bool is_ctor = (bp->methodID == java_lang_Class_getConstructor || bp->methodID == java_lang_Class_getDeclaredConstructor);
  jclass self = get_arg(jvmti, thread, 0);
  jstring method_name = NULL;
  const char *method_name_cstr;
  jobjectArray param_types;
  if (is_ctor) {
    method_name_cstr = TRACE_ARG_IGNORE;
    param_types = get_arg(jvmti, thread, 1);
  } else {
    method_name = get_arg(jvmti, thread, 1);
    method_name_cstr = get_cstr(jni, method_name);
    param_types = get_arg(jvmti, thread, 2);
  }
  jint param_types_len = (param_types == NULL) ? -1 : jnifun->GetArrayLength(jni, param_types);
  struct sbuf b;
  if (param_types_len > 0) {
    sbuf_new(&b);
    char prefix = '[';
    for (jint i = 0; i < param_types_len; i++) {
      jclass arg = jnifun->GetObjectArrayElement(jni, param_types, i);
      jstring class_name = (arg != NULL) ? jnifun->CallObjectMethod(jni, arg, java_lang_Class_getName) : NULL;
      const char *class_name_cstr = get_cstr(jni, class_name);
      sbuf_printf(&b, "%c\"%s\"", prefix, class_name_cstr);
      prefix = ',';
      release_cstr(jni, class_name, class_name_cstr);
    }
    sbuf_printf(&b, "]");
    reflect_trace(jni, self, bp->name, method_name_cstr, TRACE_NEXT_ARG_UNQUOTED_TAG, sbuf_as_cstr(&b), NULL);
    sbuf_destroy(&b);
  } else {
    const char *param_types_cstr = (param_types != NULL) ? "[]" : TRACE_VALUE_NULL;
    reflect_trace(jni, self, bp->name, method_name_cstr, TRACE_NEXT_ARG_UNQUOTED_TAG, param_types_cstr, NULL);
  }
  if (!is_ctor) {
    release_cstr(jni, method_name, method_name_cstr);
  }
}

static void OnBreakpoint_requestProxy(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  const char *class_loader = TRACE_VALUE_UNKNOWN;   // not relevant
  const char *invoke_handler = TRACE_VALUE_UNKNOWN; // not relevant
  jobjectArray ifaces = get_arg(jvmti, thread, 1);
  jint ifaces_len = (ifaces == NULL) ? -1 : jnifun->GetArrayLength(jni, ifaces);
  struct sbuf b;
  if (ifaces_len > 0) {
    sbuf_new(&b);
    char prefix = '[';
    for (jint i = 0; i < ifaces_len; i++) {
      jclass arg = jnifun->GetObjectArrayElement(jni, ifaces, i);
      jstring class_name = (arg != NULL) ? jnifun->CallObjectMethod(jni, arg, java_lang_Class_getName) : NULL;
      const char *class_name_cstr = get_cstr(jni, class_name);
      sbuf_printf(&b, "%c\"%s\"", prefix, class_name_cstr);
      prefix = ',';
      release_cstr(jni, class_name, class_name_cstr);
    }
    sbuf_printf(&b, "]");
    reflect_trace(jni, NULL, bp->name, class_loader, TRACE_NEXT_ARG_UNQUOTED_TAG, sbuf_as_cstr(&b), invoke_handler, NULL);
    sbuf_destroy(&b);
  } else {
    const char *ifaces_cstr = (ifaces != NULL) ? "[]" : TRACE_VALUE_NULL;
    reflect_trace(jni, NULL, bp->name, class_loader, ifaces_cstr, invoke_handler, NULL);
  }
}

static void OnBreakpoint_getResource(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  jobject self = get_arg(jvmti, thread, 0);
  jclass clazz = (self != NULL) ? jnifun->GetObjectClass(jni, self) : NULL;
  jstring name = get_arg(jvmti, thread, 1);
  const char *name_cstr = get_cstr(jni, name);
  reflect_trace(jni, clazz, bp->name, name_cstr, NULL);
  release_cstr(jni, name, name_cstr);
}

static void OnBreakpoint_getSystemResource(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  jstring name = get_arg(jvmti, thread, 0);
  const char *name_cstr = get_cstr(jni, name);
  reflect_trace(jni, NULL, bp->name, name_cstr, NULL);
  release_cstr(jni, name, name_cstr);
}

#define REFLECTION_BREAKPOINT(class_name, name, signature, handler) \
  { (jmethodID)0, (jlocation)0, (class_name), (name), (signature), (handler) }

static struct reflect_breakpoint_entry reflect_breakpoints[] = {
  REFLECTION_BREAKPOINT("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", &OnBreakpoint_forName),
  REFLECTION_BREAKPOINT("java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", &OnBreakpoint_forName),

  REFLECTION_BREAKPOINT("java/lang/Class", "getFields", "()[Ljava/lang/reflect/Field;", &OnBreakpoint_bulkGetMembers),
  REFLECTION_BREAKPOINT("java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", &OnBreakpoint_bulkGetMembers),
  REFLECTION_BREAKPOINT("java/lang/Class", "getConstructors", "()[Ljava/lang/reflect/Constructor;", &OnBreakpoint_bulkGetMembers),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredFields", "()[Ljava/lang/reflect/Field;", &OnBreakpoint_bulkGetMembers),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredMethods", "()[Ljava/lang/reflect/Method;", &OnBreakpoint_bulkGetMembers),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredConstructors", "()[Ljava/lang/reflect/Constructor;", &OnBreakpoint_bulkGetMembers),

  REFLECTION_BREAKPOINT("java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", &OnBreakpoint_getSingleField),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", &OnBreakpoint_getSingleField),
  REFLECTION_BREAKPOINT("java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", &OnBreakpoint_getSingleMethod),
  REFLECTION_BREAKPOINT("java/lang/Class", "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", &OnBreakpoint_getSingleMethod),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", &OnBreakpoint_getSingleMethod),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", &OnBreakpoint_getSingleMethod),

  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getResource", "(Ljava/lang/String;)Ljava/net/URL;", &OnBreakpoint_getResource),
  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", &OnBreakpoint_getResource),
  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getResources", "(Ljava/lang/String;)Ljava/util/Enumeration;", &OnBreakpoint_getResource),
  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getSystemResource", "(Ljava/lang/String;)Ljava/net/URL;", &OnBreakpoint_getSystemResource),
  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getSystemResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", &OnBreakpoint_getSystemResource),
  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getSystemResources", "(Ljava/lang/String;)Ljava/util/Enumeration;", &OnBreakpoint_getSystemResource),

  REFLECTION_BREAKPOINT("java/lang/reflect/Proxy", "getProxyClass", "(Ljava/lang/ClassLoader;[Ljava/lang/Class;)Ljava/lang/Class;", &OnBreakpoint_requestProxy),
  REFLECTION_BREAKPOINT("java/lang/reflect/Proxy", "newProxyInstance", "(Ljava/lang/ClassLoader;[Ljava/lang/Class;Ljava/lang/reflect/InvocationHandler;)Ljava/lang/Object;", &OnBreakpoint_requestProxy),

  /* These two methods call getDeclaredMethods() and getDeclaredConstructors() and find
   * the enclosing method in the result. Therefore, we already record the enclosing method
   * using our other breakpoints, but also unnecessary extra methods. Narrowing this down
   * to only the enclosing method would mean detecting and not recording the nested calls
   * and inspecting the return value. We currently don't do this because these methods are
   * fairly uncommon.
   */
  //REFLECTION_BREAKPOINT("java/lang/Class", "getEnclosingMethod", "()Ljava/lang/reflect/Method;", &OnBreakpoint_getEnclosingMethod),
  //REFLECTION_BREAKPOINT("java/lang/Class", "getEnclosingConstructor", "()Ljava/lang/reflect/Constructor;", &OnBreakpoint_getEnclosingMethod),
};

static jboolean test_exception(JNIEnv *env) {
  if (jnifun->ExceptionCheck(env)) {
    jnifun->ExceptionDescribe(env);
    return JNI_TRUE;
  }
  return JNI_FALSE;
}

static void JNICALL OnBreakpoint(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method, jlocation location) {
  // Linear search should be fine for now, but we could sort and do binary searches
  const int count = sizeof (reflect_breakpoints) / sizeof (struct reflect_breakpoint_entry);
  struct reflect_breakpoint_entry *match = NULL;
  for (int i = 0; i < count; i++) {
    struct reflect_breakpoint_entry *bp = &reflect_breakpoints[i];
    if (method == bp->methodID && location == bp->location) {
      match = bp;
      break;
    }
  }
  guarantee(match != NULL);
  match->handler(jvmti, jni, thread, match);
  guarantee(!test_exception(jni));
}

jint OnLoad_Reflection(JavaVM *vm, char *options, jvmtiEnv *jvmti, jvmtiEventCallbacks *callbacks) {
  jvmtiCapabilities caps;
  guarantee((*jvmti)->GetCapabilities(jvmti, &caps) == JVMTI_ERROR_NONE);
  caps.can_generate_breakpoint_events = 1;
  caps.can_access_local_variables = 1;
  guarantee((*jvmti)->AddCapabilities(jvmti, &caps) == JVMTI_ERROR_NONE);

  callbacks->Breakpoint = &OnBreakpoint;
  guarantee((*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, NULL) == JVMTI_ERROR_NONE);

  return JNI_OK;
}

void OnVMInit_Reflection(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  const int count = sizeof (reflect_breakpoints) / sizeof (struct reflect_breakpoint_entry);
  const char *last_class_name = NULL;
  jclass last_class = NULL;
  for (int i = 0; i < count; i++) {
    struct reflect_breakpoint_entry *bp = &reflect_breakpoints[i];
    if (last_class_name == NULL || (last_class_name != bp->class_name && strcmp(last_class_name, bp->class_name) != 0)) {
      guarantee((last_class = jnifun->FindClass(jni, bp->class_name)) != NULL);
      guarantee(!test_exception(jni));
    }
    last_class_name = bp->class_name;
    bp->methodID = jnifun->GetMethodID(jni, last_class, bp->name, bp->signature);
    if (bp->methodID == NULL) {
      jnifun->ExceptionClear(jni);
      bp->methodID = jnifun->GetStaticMethodID(jni, last_class, bp->name, bp->signature);
    }
    guarantee(bp->methodID != NULL);
    guarantee((*jvmti)->SetBreakpoint(jvmti, bp->methodID, bp->location) == JVMTI_ERROR_NONE);
    guarantee(!test_exception(jni));
  }
}

void OnVMStart_Reflection(jvmtiEnv *jvmti, JNIEnv *jni) {
  guarantee((java_lang_Class = jnifun->FindClass(jni, "java/lang/Class")) != NULL);
  guarantee((java_lang_Class = jnifun->NewGlobalRef(jni, java_lang_Class)) != NULL);
  guarantee((java_lang_Class_getName = jnifun->GetMethodID(jni, java_lang_Class, "getName", "()Ljava/lang/String;")) != NULL);
  guarantee((java_lang_Class_getConstructor = jnifun->GetMethodID(jni, java_lang_Class, "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;")) != NULL);
  guarantee((java_lang_Class_getDeclaredConstructor = jnifun->GetMethodID(jni, java_lang_Class, "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;")) != NULL);
}

#ifdef __cplusplus
}
#endif
