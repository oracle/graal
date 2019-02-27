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
#include "util.h"

#include <string.h>
#include <malloc.h>
#include <assert.h>

static void reflect_trace(JNIEnv *env, jclass clazz, jclass caller_class, const char *function, const char *result, ... /* args */) {
  va_list ap;
  va_start(ap, result);
  trace_append_v(env, "reflect", clazz, caller_class, function, result, ap);
  va_end(ap);
}
struct reflect_breakpoint_entry;

typedef void (*reflect_breakpoint_handler)(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass caller_class, struct reflect_breakpoint_entry *bp);

struct reflect_breakpoint_entry {
  jclass    clazz;
  jmethodID methodID;
  jlocation location;
  const char *class_name;
  const char *name;
  const char *signature;
  reflect_breakpoint_handler handler;
};

/*
 * NOTE: With most of our breakpoints, we recursively call the interrupted method
 * ourself to inspect its return value and determine whether it provides a valid
 * result. This permits us to identify probing.
 *
 * Many of the methods are caller-sensitive, so when we call them from a
 * breakpoint, they observe a different caller class and therefore can behave
 * differently. Short of using bytecode instrumentation to intercept the return
 * value, there seems to be no strictly better approach (and instrumenting
 * java.lang.Class and friends might be tricky, too). It would be possible to
 * set breakpoints at return bytecodes instead, but then there is no way to
 * retrieve the return value from the operand stack.
 */

static void OnBreakpoint_forName(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass caller_class, struct reflect_breakpoint_entry *bp) {
  jstring name = get_object_arg(0);
  jboolean initialize;
  jobject class_loader;
  bool initialize_valid = true;
  bool class_loader_valid = true;
  if (bp->methodID == java_lang_Class_forName3) {
    initialize_valid = ((*jvmti)->GetLocalInt(jvmti, thread, 0, 1, &initialize) == JVMTI_ERROR_NONE);
    class_loader_valid = ((*jvmti)->GetLocalObject(jvmti, thread, 0, 2, &class_loader) == JVMTI_ERROR_NONE);
  } else {
    initialize = JNI_TRUE;
    class_loader = NULL;
    if (caller_class != NULL) {
      // NOTE: our caller class could actually be skipped over in the security
      // stackwalk of @CallerSensitive, leading to different behavior of our call
      class_loader_valid = ((*jvmti)->GetClassLoader(jvmti, caller_class, &class_loader) == JVMTI_ERROR_NONE);
    }
  }
  const char *result = TRACE_VALUE_UNKNOWN;
  if (initialize_valid && class_loader_valid) {
    result = nn_bool(jnifun->CallStaticObjectMethod(jni, bp->clazz, bp->methodID, name, initialize, class_loader));
    if (clear_exception(jni)) {
      result = NULL;
    }
  }

  const char *name_cstr = get_cstr(jni, name);
  reflect_trace(jni, bp->clazz, caller_class, bp->name, result, name_cstr, NULL);
  release_cstr(jni, name, name_cstr);
}

static void OnBreakpoint_bulkGetMembers(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass caller_class, struct reflect_breakpoint_entry *bp) {
  // No need to recursively call these methods because they can only
  // return an empty array or throw a SecurityException.
  jclass self = get_object_arg(0);
  reflect_trace(jni, self, caller_class, bp->name, NULL, NULL);
}

static void OnBreakpoint_getSingleField(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass caller_class, struct reflect_breakpoint_entry *bp) {
  jclass self = get_object_arg(0);
  jstring name = get_object_arg(1);

  jobject result = jnifun->CallObjectMethod(jni, self, bp->methodID, name);
  if (clear_exception(jni)) {
    result = NULL;
  }

  const char *name_cstr = get_cstr(jni, name);
  reflect_trace(jni, self, caller_class, bp->name, nn_bool(result), name_cstr, NULL);
  release_cstr(jni, name, name_cstr);
}

static void OnBreakpoint_getSingleMethod(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass caller_class, struct reflect_breakpoint_entry *bp) {
  bool is_ctor = (bp->methodID == java_lang_Class_getConstructor || bp->methodID == java_lang_Class_getDeclaredConstructor);
  jclass self = get_object_arg(0);
  jstring method_name = NULL;
  const char *method_name_cstr;
  jobjectArray param_types;
  jobject result;
  if (is_ctor) {
    method_name_cstr = TRACE_ARG_IGNORE;
    param_types = get_object_arg(1);
    result = jnifun->CallObjectMethod(jni, self, bp->methodID, param_types);
  } else {
    method_name = get_object_arg(1);
    method_name_cstr = get_cstr(jni, method_name);
    param_types = get_object_arg(2);
    result = jnifun->CallObjectMethod(jni, self, bp->methodID, method_name, param_types);
  }
  if (clear_exception(jni)) {
    result = NULL;
  }

  jint param_types_len = (param_types == NULL) ? -1 : jnifun->GetArrayLength(jni, param_types);
  struct sbuf b;
  if (param_types_len > 0) {
    sbuf_new(&b);
    char prefix = '[';
    for (jint i = 0; i < param_types_len; i++) {
      sbuf_printf(&b, "%c", prefix);
      jclass arg = jnifun->GetObjectArrayElement(jni, param_types, i);
      jstring class_name = (arg != NULL) ? jnifun->CallObjectMethod(jni, arg, java_lang_Class_getName) : NULL;
      const char *class_name_cstr = get_cstr(jni, class_name);
      sbuf_quote(&b, class_name_cstr);
      prefix = ',';
      release_cstr(jni, class_name, class_name_cstr);
    }
    sbuf_printf(&b, "]");
    reflect_trace(jni, self, caller_class, bp->name, nn_bool(result), method_name_cstr, TRACE_NEXT_ARG_UNQUOTED_TAG, sbuf_as_cstr(&b), NULL);
    sbuf_destroy(&b);
  } else {
    const char *param_types_cstr = (param_types != NULL) ? "[]" : TRACE_VALUE_NULL;
    reflect_trace(jni, self, caller_class, bp->name, nn_bool(result), method_name_cstr, TRACE_NEXT_ARG_UNQUOTED_TAG, param_types_cstr, NULL);
  }
  if (!is_ctor) {
    release_cstr(jni, method_name, method_name_cstr);
  }
}

static void OnBreakpoint_requestProxy(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass caller_class, struct reflect_breakpoint_entry *bp) {
  jobject class_loader = get_object_arg(0);
  jobjectArray ifaces = get_object_arg(1);
  jobject invoke_handler = NULL;
  jobject result;
  // Proxies should be cached, so repeated calls shouldn't generate extra types
  if ((*jvmti)->GetLocalObject(jvmti, thread, 0, 2, &invoke_handler) == JVMTI_ERROR_NONE) {
    result = jnifun->CallStaticObjectMethod(jni, bp->clazz, bp->methodID, class_loader, ifaces, invoke_handler);
  } else {
    result = jnifun->CallStaticObjectMethod(jni, bp->clazz, bp->methodID, class_loader, ifaces);
  }
  if (clear_exception(jni)) {
    result = NULL;
  }

  jint ifaces_len = (ifaces == NULL) ? -1 : jnifun->GetArrayLength(jni, ifaces);
  struct sbuf b;
  if (ifaces_len > 0) {
    sbuf_new(&b);
    char prefix = '[';
    for (jint i = 0; i < ifaces_len; i++) {
      sbuf_printf(&b, "%c", prefix);
      jclass arg = jnifun->GetObjectArrayElement(jni, ifaces, i);
      jstring class_name = (arg != NULL) ? jnifun->CallObjectMethod(jni, arg, java_lang_Class_getName) : NULL;
      const char *class_name_cstr = get_cstr(jni, class_name);
      sbuf_quote(&b, class_name_cstr);
      prefix = ',';
      release_cstr(jni, class_name, class_name_cstr);
    }
    sbuf_printf(&b, "]");
    reflect_trace(jni, NULL, caller_class, bp->name, nn_bool(result), TRACE_VALUE_UNKNOWN, TRACE_NEXT_ARG_UNQUOTED_TAG, sbuf_as_cstr(&b), TRACE_VALUE_UNKNOWN, NULL);
    sbuf_destroy(&b);
  } else {
    const char *ifaces_cstr = (ifaces != NULL) ? "[]" : TRACE_VALUE_NULL;
    reflect_trace(jni, NULL, caller_class, bp->name, nn_bool(result), class_loader, ifaces_cstr, invoke_handler, NULL);
  }
}

static void OnBreakpoint_getResource(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass caller_class, struct reflect_breakpoint_entry *bp) {
  jobject self = get_object_arg(0);
  jstring name = get_object_arg(1);

  jobject result = jnifun->CallObjectMethod(jni, self, bp->methodID, name);
  if (clear_exception(jni)) {
    result = NULL;
  }

  const char *name_cstr = get_cstr(jni, name);
  jclass clazz = (self != NULL) ? jnifun->GetObjectClass(jni, self) : NULL;
  reflect_trace(jni, clazz, caller_class, bp->name, nn_bool(result), name_cstr, NULL);
  release_cstr(jni, name, name_cstr);
}

static void OnBreakpoint_getSystemResource(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass caller_class, struct reflect_breakpoint_entry *bp) {
  jstring name = get_object_arg(0);

  jobject result = jnifun->CallStaticObjectMethod(jni, bp->clazz, bp->methodID, name);
  if (clear_exception(jni)) {
    result = NULL;
  }

  const char *name_cstr = get_cstr(jni, name);
  reflect_trace(jni, NULL, caller_class, bp->name, NULL, name_cstr, NULL);
  release_cstr(jni, name, name_cstr);
}

static void OnBreakpoint_getEnclosingMethod(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass caller_class, struct reflect_breakpoint_entry *bp) {
  jclass self = get_object_arg(0);
  jobject method = jnifun->CallObjectMethod(jni, self, bp->methodID);
  jmethodID methodID = jnifun->FromReflectedMethod(jni, method);
  jclass clazz = NULL;
  (*jvmti)->GetMethodDeclaringClass(jvmti, methodID, &clazz);
  jstring clazz_name = (clazz != NULL) ? jnifun->CallObjectMethod(jni, clazz, java_lang_Class_getName) : NULL;
  const char *class_name_cstr = get_cstr(jni, clazz_name);
  char *name = NULL;
  char *signature = NULL;
  (*jvmti)->GetMethodName(jvmti, methodID, &name, &signature, NULL);
  struct sbuf b;
  sbuf_new(&b);
  sbuf_printf(&b, "%s.%s%s", class_name_cstr, name, signature);
  release_cstr(jni, clazz, class_name_cstr);
  (*jvmti)->Deallocate(jvmti, name);
  (*jvmti)->Deallocate(jvmti, signature);
  reflect_trace(jni, NULL, caller_class, bp->name, sbuf_as_cstr(&b), NULL);
  sbuf_destroy(&b);
}

#define REFLECTION_BREAKPOINT(class_name, name, signature, handler) \
  { (jclass)0, (jmethodID)0, (jlocation)0, (class_name), (name), (signature), (handler) }

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

  REFLECTION_BREAKPOINT("java/lang/Class", "getEnclosingMethod", "()Ljava/lang/reflect/Method;", &OnBreakpoint_getEnclosingMethod),
  REFLECTION_BREAKPOINT("java/lang/Class", "getEnclosingConstructor", "()Ljava/lang/reflect/Constructor;", &OnBreakpoint_getEnclosingMethod),

  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getResource", "(Ljava/lang/String;)Ljava/net/URL;", &OnBreakpoint_getResource),
  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getResources", "(Ljava/lang/String;)Ljava/util/Enumeration;", &OnBreakpoint_getResource),
  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getSystemResource", "(Ljava/lang/String;)Ljava/net/URL;", &OnBreakpoint_getSystemResource),
  REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getSystemResources", "(Ljava/lang/String;)Ljava/util/Enumeration;", &OnBreakpoint_getSystemResource),
// Not needed: getResourceAsStream() generally uses getResource()
//REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", &OnBreakpoint_getResource),
//REFLECTION_BREAKPOINT("java/lang/ClassLoader", "getSystemResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", &OnBreakpoint_getSystemResource),

  REFLECTION_BREAKPOINT("java/lang/reflect/Proxy", "getProxyClass", "(Ljava/lang/ClassLoader;[Ljava/lang/Class;)Ljava/lang/Class;", &OnBreakpoint_requestProxy),
  REFLECTION_BREAKPOINT("java/lang/reflect/Proxy", "newProxyInstance", "(Ljava/lang/ClassLoader;[Ljava/lang/Class;Ljava/lang/reflect/InvocationHandler;)Ljava/lang/Object;", &OnBreakpoint_requestProxy),
};

static void JNICALL OnBreakpoint(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method, jlocation location) {
  void *data = NULL;
  if ((*jvmti)->GetThreadLocalStorage(jvmti, thread, &data) == JVMTI_ERROR_NONE && data != NULL) {
    return; // recursion
  }
  (*jvmti)->SetThreadLocalStorage(jvmti, thread, "prevent recursion");

  assert(!test_exception(jni)); // should never happen with our breakpoints

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
  jclass caller_class = get_caller_class(1);
  match->handler(jvmti, jni, thread, caller_class, match);

  guarantee(!test_exception(jni));
  (*jvmti)->SetThreadLocalStorage(jvmti, thread, NULL);
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
      guarantee((last_class = jnifun->NewGlobalRef(jni, last_class)) != NULL);
      guarantee(!test_exception(jni));
    }
    last_class_name = bp->class_name;
    bp->clazz = last_class;
    bp->methodID = jnifun->GetMethodID(jni, bp->clazz, bp->name, bp->signature);
    if (bp->methodID == NULL) {
      jnifun->ExceptionClear(jni);
      bp->methodID = jnifun->GetStaticMethodID(jni, bp->clazz, bp->name, bp->signature);
    }
    guarantee(bp->methodID != NULL);
    guarantee((*jvmti)->SetBreakpoint(jvmti, bp->methodID, bp->location) == JVMTI_ERROR_NONE);
    guarantee(!test_exception(jni));
  }
}

#ifdef __cplusplus
}
#endif
