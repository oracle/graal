/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include <err.h>
#include "jvmti.h"

// Non-debug assertion
void __checked_fail(const char *__test, const char *__file, unsigned int __line, const char *__function) {
    errx(1, "%s:%u: %s: check failed, aborting: %s", __file, __line, __function, __test);
}
#define checked(expr)							\
  ((expr)								\
   ? (void) (0)						                \
   : __checked_fail (__STRING(expr), __FILE__, __LINE__, __PRETTY_FUNCTION__))

FILE *output = NULL;
jniNativeInterface *originalJNI;

void trace(JNIEnv *threadenv, char *function, char *fmt, ...) {
  va_list args;
  va_start (args, fmt);
  fputs(function, output);
  fputc('(', output);
  vfprintf(output, fmt, args);
  fputs(")\n", output);
  va_end(args);
}

void trace_class(JNIEnv *threadenv, char *function, jclass clazz, char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  fputs(function, output);
  fputs("(\"", output);
  JNIEnv *env = (JNIEnv *) &originalJNI;
  jclass clazzclass;
  checked((clazzclass = (*env)->GetObjectClass(threadenv, clazz)) != NULL);
  jmethodID getname;
  checked((getname = (*env)->GetMethodID(threadenv, clazzclass, "getName", "()Ljava/lang/String;")) != NULL);
  jobject name;
  checked((name = (*env)->CallObjectMethod(threadenv, clazz, getname)) != NULL);
  const char *cname;
  checked((cname = (*env)->GetStringUTFChars(threadenv, name, NULL)) != NULL);
  const char *cnamep = cname;
  while (*cnamep != '\0') {
    char c = (*cnamep != '.') ? *cnamep : '/';
    fputc(c, output);
    cnamep++;
  }
  fputs("\", ", output);
  (*env)->ReleaseStringUTFChars(threadenv, name, cname);
  vfprintf(output, fmt, args);
  fputs(")\n", output);
  va_end(args);
}

jclass JNICALL DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize bufLen) {
  trace(env, "DefineClass", "\"%s\"", name);
  return originalJNI->DefineClass(env, name, loader, buf, bufLen);
}

jclass JNICALL FindClass(JNIEnv *env, const char *name) {
  trace(env, "FindClass", "\"%s\"", name);
  return originalJNI->FindClass(env, name);
}

jmethodID JNICALL GetMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
  trace_class(env, "GetMethodID", clazz, "\"%s\", \"%s\"", name, sig);
  return originalJNI->GetMethodID(env, clazz, name, sig);
}

jmethodID JNICALL GetStaticMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
  trace_class(env, "GetStaticMethodID", clazz, "\"%s\", \"%s\"", name, sig);
  return originalJNI->GetStaticMethodID(env, clazz, name, sig);
}

jfieldID JNICALL GetFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
  trace_class(env, "GetFieldID", clazz, "\"%s\", \"%s\"", name, sig);
  return originalJNI->GetFieldID(env, clazz, name, sig);
}

jfieldID JNICALL GetStaticFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
  trace_class(env, "GetStaticFieldID", clazz, "\"%s\", \"%s\"", name, sig);
  return originalJNI->GetStaticFieldID(env, clazz, name, sig);
}

void JNICALL OnVMStart(jvmtiEnv *jvmti, JNIEnv *jni) {
  jniNativeInterface *functions;
  checked((*jvmti)->GetJNIFunctionTable(jvmti, &originalJNI) == JVMTI_ERROR_NONE);
  checked((*jvmti)->GetJNIFunctionTable(jvmti, &functions) == JVMTI_ERROR_NONE);
  functions->DefineClass = DefineClass;
  functions->FindClass = FindClass;
  functions->GetMethodID = GetMethodID;
  functions->GetStaticMethodID = GetStaticMethodID;
  functions->GetFieldID = GetFieldID;
  functions->GetStaticFieldID = GetStaticFieldID;
  checked((*jvmti)->SetJNIFunctionTable(jvmti, functions) == JVMTI_ERROR_NONE);
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  checked((output = fopen(options, "w")) != NULL);

  jvmtiEnv* jvmti = NULL;
  checked((*vm)->GetEnv(vm, (void**) &jvmti, JVMTI_VERSION) == JNI_OK);

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof (callbacks));
  callbacks.VMStart = OnVMStart;
  checked((*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof (callbacks)) == JVMTI_ERROR_NONE);

  // enable specific events
  checked((*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_START, NULL) == JVMTI_ERROR_NONE);

  return JNI_OK;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
  checked(fclose(output) == 0);
}
