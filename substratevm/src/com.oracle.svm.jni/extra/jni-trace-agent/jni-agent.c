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

static FILE *output = NULL;

static jmethodID classGetName;

static void trace(JNIEnv *env, char *function, char *fmt, ...) {
  fputs(function, output);
  fputc('(', output);
  va_list args;
  va_start (args, fmt);
  vfprintf(output, fmt, args);
  va_end(args);
  fputs(")\n", output);
}

static void trace_with_class(JNIEnv *env, char *function, jclass clazz, char *fmt, ...) {
  fputs(function, output);
  fputs("(\"", output);
  jclass clazzclass;
  guarantee((clazzclass = jnifun->GetObjectClass(env, clazz)) != NULL);
  jobject name;
  guarantee((name = jnifun->CallObjectMethod(env, clazz, classGetName)) != NULL);
  const char *cname;
  guarantee((cname = jnifun->GetStringUTFChars(env, name, NULL)) != NULL);
  const char *cnamep = cname;
  while (*cnamep != '\0') {
    char c = (*cnamep != '.') ? *cnamep : '/';
    fputc(c, output);
    cnamep++;
  }
  jnifun->ReleaseStringUTFChars(env, name, cname);
  fputs("\", ", output);
  va_list args;
  va_start(args, fmt);
  vfprintf(output, fmt, args);
  va_end(args);
  fputs(")\n", output);
}

static jclass JNICALL DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize bufLen) {
  trace(env, "DefineClass", "\"%s\"", name);
  return jnifun->DefineClass(env, name, loader, buf, bufLen);
}

static jclass JNICALL FindClass(JNIEnv *env, const char *name) {
  trace(env, "FindClass", "\"%s\"", name);
  return jnifun->FindClass(env, name);
}

static jmethodID JNICALL GetMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
  trace_with_class(env, "GetMethodID", clazz, "\"%s\", \"%s\"", name, sig);
  return jnifun->GetMethodID(env, clazz, name, sig);
}

static jmethodID JNICALL GetStaticMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
  trace_with_class(env, "GetStaticMethodID", clazz, "\"%s\", \"%s\"", name, sig);
  return jnifun->GetStaticMethodID(env, clazz, name, sig);
}

static jfieldID JNICALL GetFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
  trace_with_class(env, "GetFieldID", clazz, "\"%s\", \"%s\"", name, sig);
  return jnifun->GetFieldID(env, clazz, name, sig);
}

static jfieldID JNICALL GetStaticFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
  trace_with_class(env, "GetStaticFieldID", clazz, "\"%s\", \"%s\"", name, sig);
  return jnifun->GetStaticFieldID(env, clazz, name, sig);
}

jint OnLoad_JNI(JavaVM *vm, char *options, void *reserved, jvmtiEventCallbacks *callbacks) {
  char path[MAX_PATH_LEN];
  guarantee(snprintf(path, MAX_PATH_LEN, "%s%s", options, ".jni.log") < MAX_PATH_LEN);
  guarantee((output = fopen(path, "w")) != NULL);
  return JVMTI_ERROR_NONE;
}

void OnVMStart_JNI(jvmtiEnv *jvmti, JNIEnv *jni) {
  jclass classClass;
  guarantee((classClass = jnifun->FindClass(jni, "java/lang/Class")) != NULL);
  guarantee((classGetName = jnifun->GetMethodID(jni, classClass, "getName", "()Ljava/lang/String;")) != NULL);

  jniNativeInterface *functions;
  guarantee((*jvmti)->GetJNIFunctionTable(jvmti, &functions) == JVMTI_ERROR_NONE);
  functions->DefineClass = &DefineClass;
  functions->FindClass = &FindClass;
  functions->GetMethodID = &GetMethodID;
  functions->GetStaticMethodID = &GetStaticMethodID;
  functions->GetFieldID = &GetFieldID;
  functions->GetStaticFieldID = &GetStaticFieldID;
  guarantee((*jvmti)->SetJNIFunctionTable(jvmti, functions) == JVMTI_ERROR_NONE);
  guarantee((*jvmti)->Deallocate(jvmti, (unsigned char *) functions) == JVMTI_ERROR_NONE);
}

void OnVMDeath_JNI(jvmtiEnv *jvmti, JNIEnv *jni) {
  guarantee((*jvmti)->Deallocate(jvmti, (unsigned char *) jnifun) == JVMTI_ERROR_NONE);
  guarantee(fclose(output) == 0);
}

#ifdef __cplusplus
}
#endif
