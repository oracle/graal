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

#include <string.h>
#include <malloc.h>

static FILE *output;

static jmethodID classGetName;

static void reflect_trace(JNIEnv *env, jclass clazz, const char *function, char *fmt, ...) {
  fputs("reflect:", output);
  if (clazz != NULL) {
    fputc('"', output);
    jobject name;
    guarantee((name = jnifun->CallObjectMethod(env, clazz, classGetName)) != NULL);
    const char *cname;
    guarantee((cname = jnifun->GetStringUTFChars(env, name, NULL)) != NULL);
    fputs(cname, output);
    jnifun->ReleaseStringUTFChars(env, name, cname);
    fputc('"', output);
  }
  fputc(':', output);
  fputs(function, output);
  va_list args;
  va_start (args, fmt);
  vfprintf(output, fmt, args);
  va_end(args);
  fputc('\n', output);
}

static jboolean test_exception(JNIEnv *env) {
  if (jnifun->ExceptionCheck(env)) {
    jnifun->ExceptionDescribe(env);
    return JNI_TRUE;
  }
  return JNI_FALSE;
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

static void OnBreakpoint_forName(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  jstring name;
  guarantee((*jvmti)->GetLocalObject(jvmti, thread, 0, 0, &name) == JVMTI_ERROR_NONE);
  const char *cname;
  guarantee((cname = jnifun->GetStringUTFChars(jni, name, NULL)) != NULL);
  reflect_trace(jni, NULL, bp->name, "(\"%s\")", cname);
  jnifun->ReleaseStringUTFChars(jni, name, cname);
}

static void OnBreakpoint_bulkGetMembers(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  jstring self;
  guarantee((*jvmti)->GetLocalObject(jvmti, thread, 0, 0, &self) == JVMTI_ERROR_NONE);
  reflect_trace(jni, self, bp->name, "()");
}

static void OnBreakpoint_getSingleField(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  jclass self;
  guarantee((*jvmti)->GetLocalObject(jvmti, thread, 0, 0, &self) == JVMTI_ERROR_NONE);
  jstring name;
  guarantee((*jvmti)->GetLocalObject(jvmti, thread, 0, 1, &name) == JVMTI_ERROR_NONE);
  const char *cname;
  guarantee((cname = jnifun->GetStringUTFChars(jni, name, NULL)) != NULL);
  reflect_trace(jni, self, bp->name, "(\"%s\")", cname);
  jnifun->ReleaseStringUTFChars(jni, name, cname);
}

static void OnBreakpoint_getSingleMethod(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, struct reflect_breakpoint_entry *bp) {
  jclass self;
  guarantee((*jvmti)->GetLocalObject(jvmti, thread, 0, 0, &self) == JVMTI_ERROR_NONE);
  jstring name;
  guarantee((*jvmti)->GetLocalObject(jvmti, thread, 0, 1, &name) == JVMTI_ERROR_NONE);
  jobjectArray types;
  guarantee((*jvmti)->GetLocalObject(jvmti, thread, 0, 2, &types) == JVMTI_ERROR_NONE);
  jint types_len = jnifun->GetArrayLength(jni, types);
  char *types_str = "";
  if (types_len > 0) {
    size_t types_str_capacity = 64 * types_len;
    guarantee ((types_str = malloc(types_str_capacity)) != NULL);
    types_str[0] = '\0';
    size_t types_str_len = 0;
    for (jint i = 0; i < types_len; i++) {
      jclass arg;
      guarantee((arg = jnifun->GetObjectArrayElement(jni, types, i)) != NULL);
      jstring class_name;
      guarantee((class_name = jnifun->CallObjectMethod(jni, arg, classGetName)) != NULL);
      const char *class_cname;
      guarantee((class_cname = jnifun->GetStringUTFChars(jni, class_name, NULL)) != NULL);
      size_t class_cname_len = strlen(class_cname);
      size_t required_capacity = types_str_len + 3 + class_cname_len + 2;
      if(required_capacity > types_str_capacity) {
        guarantee((types_str = realloc(types_str, required_capacity)) != NULL);
        types_str_capacity = required_capacity;
      }
      strcpy(&types_str[types_str_len], ", \"");
      types_str_len += 3;
      strcpy(&types_str[types_str_len], class_cname);
      types_str_len += class_cname_len;
      strcpy(&types_str[types_str_len], "\"");
      types_str_len++;
      jnifun->ReleaseStringUTFChars(jni, class_name, class_cname);
    }
  }
  const char *cname;
  guarantee((cname = jnifun->GetStringUTFChars(jni, name, NULL)) != NULL);
  reflect_trace(jni, self, bp->name, "(\"%s\"%s)", cname, types_str);
  jnifun->ReleaseStringUTFChars(jni, name, cname);
}

#define REFLECTION_BREAKPOINT(class_name, name, signature, handler) \
  { (jmethodID)0, (jlocation)0, (class_name), (name), (signature), (handler) }

static struct reflect_breakpoint_entry reflect_breakpoints[] = {
  REFLECTION_BREAKPOINT("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", &OnBreakpoint_forName),
  REFLECTION_BREAKPOINT("java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", &OnBreakpoint_forName),

  REFLECTION_BREAKPOINT("java/lang/Class", "getFields", "()[Ljava/lang/reflect/Field;", &OnBreakpoint_bulkGetMembers),
  REFLECTION_BREAKPOINT("java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", &OnBreakpoint_bulkGetMembers),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredFields", "()[Ljava/lang/reflect/Field;", &OnBreakpoint_bulkGetMembers),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredMethods", "()[Ljava/lang/reflect/Method;", &OnBreakpoint_bulkGetMembers),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredConstructors", "()[Ljava/lang/reflect/Constructor;", &OnBreakpoint_bulkGetMembers),

  REFLECTION_BREAKPOINT("java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", &OnBreakpoint_getSingleField),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", &OnBreakpoint_getSingleField),
  REFLECTION_BREAKPOINT("java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", &OnBreakpoint_getSingleMethod),
  REFLECTION_BREAKPOINT("java/lang/Class", "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", &OnBreakpoint_getSingleMethod),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", &OnBreakpoint_getSingleMethod),
  REFLECTION_BREAKPOINT("java/lang/Class", "getDeclaredConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", &OnBreakpoint_getSingleMethod),

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
  char path[MAX_PATH_LEN];
  guarantee(snprintf(path, MAX_PATH_LEN, "%s%s", options, ".reflect.log") < MAX_PATH_LEN);
  guarantee((output = fopen(path, "w")) != NULL);

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
  jclass javaLangClass;
  guarantee((javaLangClass = jnifun->FindClass(jni, "java/lang/Class")) != NULL);
  guarantee((classGetName = jnifun->GetMethodID(jni, javaLangClass, "getName", "()Ljava/lang/String;")) != NULL);
}

#ifdef __cplusplus
}
#endif
