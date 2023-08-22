/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#ifndef _JAVA_JMM21_H_
#define _JAVA_JMM21_H_

/*
 * This is a private interface used by JDK for JVM monitoring
 * and management.
 *
 * Bump the version number when either of the following happens:
 *
 * 1. There is a change in functions in JmmInterface.
 *
 * 2. There is a change in the contract between VM and Java classes.
 */


#include "jni.h"
#include "jmm_common.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct jmmInterface_4 {
  void*        reserved1;
  void*        reserved2;

  jint         (JNICALL *GetVersion)             (JNIEnv *env);

  jint         (JNICALL *GetOptionalSupport)     (JNIEnv *env,
                                                  jmmOptionalSupport* support_ptr);

  jint         (JNICALL *GetThreadInfo)          (JNIEnv *env,
                                                  jlongArray ids,
                                                  jint maxDepth,
                                                  jobjectArray infoArray);

  jobjectArray (JNICALL *GetMemoryPools)         (JNIEnv* env, jobject mgr);

  jobjectArray (JNICALL *GetMemoryManagers)      (JNIEnv* env, jobject pool);

  jobject      (JNICALL *GetMemoryPoolUsage)     (JNIEnv* env, jobject pool);
  jobject      (JNICALL *GetPeakMemoryPoolUsage) (JNIEnv* env, jobject pool);

  jlong        (JNICALL *GetTotalThreadAllocatedMemory)
                                                 (JNIEnv *env);
  jlong        (JNICALL *GetOneThreadAllocatedMemory)
                                                 (JNIEnv *env,
                                                  jlong thread_id);
  void         (JNICALL *GetThreadAllocatedMemory)
                                                 (JNIEnv *env,
                                                  jlongArray ids,
                                                  jlongArray sizeArray);

  jobject      (JNICALL *GetMemoryUsage)         (JNIEnv* env, jboolean heap);

  jlong        (JNICALL *GetLongAttribute)       (JNIEnv *env, jobject obj, jmmLongAttribute att);
  jboolean     (JNICALL *GetBoolAttribute)       (JNIEnv *env, jmmBoolAttribute att);
  jboolean     (JNICALL *SetBoolAttribute)       (JNIEnv *env, jmmBoolAttribute att, jboolean flag);

  jint         (JNICALL *GetLongAttributes)      (JNIEnv *env,
                                                  jobject obj,
                                                  jmmLongAttribute* atts,
                                                  jint count,
                                                  jlong* result);

  jobjectArray (JNICALL *FindCircularBlockedThreads) (JNIEnv *env);

  // Not used in JDK 6 or JDK 7
  jlong        (JNICALL *GetThreadCpuTime)       (JNIEnv *env, jlong thread_id);

  jobjectArray (JNICALL *GetVMGlobalNames)       (JNIEnv *env);
  jint         (JNICALL *GetVMGlobals)           (JNIEnv *env,
                                                  jobjectArray names,
                                                  jmmVMGlobal *globals,
                                                  jint count);

  jint         (JNICALL *GetInternalThreadTimes) (JNIEnv *env,
                                                  jobjectArray names,
                                                  jlongArray times);

  jboolean     (JNICALL *ResetStatistic)         (JNIEnv *env,
                                                  jvalue obj,
                                                  jmmStatisticType type);

  void         (JNICALL *SetPoolSensor)          (JNIEnv *env,
                                                  jobject pool,
                                                  jmmThresholdType type,
                                                  jobject sensor);

  jlong        (JNICALL *SetPoolThreshold)       (JNIEnv *env,
                                                  jobject pool,
                                                  jmmThresholdType type,
                                                  jlong threshold);
  jobject      (JNICALL *GetPoolCollectionUsage) (JNIEnv* env, jobject pool);

  jint         (JNICALL *GetGCExtAttributeInfo)  (JNIEnv *env,
                                                  jobject mgr,
                                                  jmmExtAttributeInfo *ext_info,
                                                  jint count);
  void         (JNICALL *GetLastGCStat)          (JNIEnv *env,
                                                  jobject mgr,
                                                  jmmGCStat *gc_stat);

  jlong        (JNICALL *GetThreadCpuTimeWithKind)
                                                 (JNIEnv *env,
                                                  jlong thread_id,
                                                  jboolean user_sys_cpu_time);
  void         (JNICALL *GetThreadCpuTimesWithKind)
                                                 (JNIEnv *env,
                                                  jlongArray ids,
                                                  jlongArray timeArray,
                                                  jboolean user_sys_cpu_time);

  jint         (JNICALL *DumpHeap0)              (JNIEnv *env,
                                                  jstring outputfile,
                                                  jboolean live);
  jobjectArray (JNICALL *FindDeadlocks)          (JNIEnv *env,
                                                  jboolean object_monitors_only);
  void         (JNICALL *SetVMGlobal)            (JNIEnv *env,
                                                  jstring flag_name,
                                                  jvalue  new_value);
  void*        reserved6;
  jobjectArray (JNICALL *DumpThreads)            (JNIEnv *env,
                                                  jlongArray ids,
                                                  jboolean lockedMonitors,
                                                  jboolean lockedSynchronizers,
                                                  jint maxDepth);
  void         (JNICALL *SetGCNotificationEnabled) (JNIEnv *env,
                                                    jobject mgr,
                                                    jboolean enabled);
  jobjectArray (JNICALL *GetDiagnosticCommands)  (JNIEnv *env);
  void         (JNICALL *GetDiagnosticCommandInfo)
                                                 (JNIEnv *env,
                                                  jobjectArray cmds,
                                                  dcmdInfo *infoArray);
  void         (JNICALL *GetDiagnosticCommandArgumentsInfo)
                                                 (JNIEnv *env,
                                                  jstring commandName,
                                                  dcmdArgInfo *infoArray,
                                                  jint count);
  jstring      (JNICALL *ExecuteDiagnosticCommand)
                                                 (JNIEnv *env,
                                                  jstring command);
  void         (JNICALL *SetDiagnosticFrameworkNotificationEnabled)
                                                 (JNIEnv *env,
                                                  jboolean enabled);
} JmmInterface;

#ifdef __cplusplus
} /* extern "C" */
#endif /* __cplusplus */

#endif /* !_JAVA_JMM21_H_ */
