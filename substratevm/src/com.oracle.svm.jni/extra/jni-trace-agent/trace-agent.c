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

#include "jni-agent.h"

void __guarantee_fail(const char *test, const char *file, unsigned int line, const char *funcname) {
    fprintf(stderr, "%s:%u: %s: check failed, aborting: %s\n", file, line, funcname, test);
    exit(1);
}

jniNativeInterface *jnifun;

void JNICALL OnVMStart(jvmtiEnv *jvmti, JNIEnv *jni) {
  guarantee((*jvmti)->GetJNIFunctionTable(jvmti, &jnifun) == JVMTI_ERROR_NONE);

  OnVMStart_JNI(jvmti, jni);
}

void JNICALL OnVMDeath(jvmtiEnv *jvmti, JNIEnv *jni) {
  OnVMDeath_JNI(jvmti, jni);
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  jvmtiEnv* jvmti = NULL;
  guarantee((*vm)->GetEnv(vm, (void**) &jvmti, JVMTI_VERSION) == JNI_OK);

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof (callbacks));
  callbacks.VMStart = &OnVMStart;
  callbacks.VMDeath = &OnVMDeath;

  jint result = OnLoad_JNI(vm, options, jvmti, &callbacks);
  if (result != JNI_OK) {
    return result;
  }

  guarantee((*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof (callbacks)) == JVMTI_ERROR_NONE);

  // enable specific events
  guarantee((*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_START, NULL) == JVMTI_ERROR_NONE);
  guarantee((*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL) == JVMTI_ERROR_NONE);

  return JNI_OK;
}

#ifdef __cplusplus
}
#endif
