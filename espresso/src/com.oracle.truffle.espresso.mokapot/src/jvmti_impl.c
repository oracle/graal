/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti.h"
#include "jvmti_env.h"

#include <trufflenfi.h>
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#define JVMTI_METHOD_LIST(V) \
    V(SetEventNotificationMode) \
    V(GetAllModules) \
    V(GetAllThreads) \
    V(SuspendThread) \
    V(ResumeThread) \
    V(StopThread) \
    V(InterruptThread) \
    V(GetThreadInfo) \
    V(GetOwnedMonitorInfo) \
    V(GetCurrentContendedMonitor) \
    V(RunAgentThread) \
    V(GetTopThreadGroups) \
    V(GetThreadGroupInfo) \
    V(GetThreadGroupChildren) \
    V(GetFrameCount) \
    V(GetThreadState) \
    V(GetCurrentThread) \
    V(GetFrameLocation) \
    V(NotifyFramePop) \
    V(GetLocalObject) \
    V(GetLocalInt) \
    V(GetLocalLong) \
    V(GetLocalFloat) \
    V(GetLocalDouble) \
    V(SetLocalObject) \
    V(SetLocalInt) \
    V(SetLocalLong) \
    V(SetLocalFloat) \
    V(SetLocalDouble) \
    V(CreateRawMonitor) \
    V(DestroyRawMonitor) \
    V(RawMonitorEnter) \
    V(RawMonitorExit) \
    V(RawMonitorWait) \
    V(RawMonitorNotify) \
    V(RawMonitorNotifyAll) \
    V(SetBreakpoint) \
    V(ClearBreakpoint) \
    V(GetNamedModule) \
    V(SetFieldAccessWatch) \
    V(ClearFieldAccessWatch) \
    V(SetFieldModificationWatch) \
    V(ClearFieldModificationWatch) \
    V(IsModifiableClass) \
    V(Allocate) \
    V(Deallocate) \
    V(GetClassSignature) \
    V(GetClassStatus) \
    V(GetSourceFileName) \
    V(GetClassModifiers) \
    V(GetClassMethods) \
    V(GetClassFields) \
    V(GetImplementedInterfaces) \
    V(IsInterface) \
    V(IsArrayClass) \
    V(GetClassLoader) \
    V(GetObjectHashCode) \
    V(GetObjectMonitorUsage) \
    V(GetFieldName) \
    V(GetFieldDeclaringClass) \
    V(GetFieldModifiers) \
    V(IsFieldSynthetic) \
    V(GetMethodName) \
    V(GetMethodDeclaringClass) \
    V(GetMethodModifiers) \
    V(GetMaxLocals) \
    V(GetArgumentsSize) \
    V(GetLineNumberTable) \
    V(GetMethodLocation) \
    V(GetLocalVariableTable) \
    V(SetNativeMethodPrefix) \
    V(SetNativeMethodPrefixes) \
    V(GetBytecodes) \
    V(IsMethodNative) \
    V(IsMethodSynthetic) \
    V(GetLoadedClasses) \
    V(GetClassLoaderClasses) \
    V(PopFrame) \
    V(ForceEarlyReturnObject) \
    V(ForceEarlyReturnInt) \
    V(ForceEarlyReturnLong) \
    V(ForceEarlyReturnFloat) \
    V(ForceEarlyReturnDouble) \
    V(ForceEarlyReturnVoid) \
    V(RedefineClasses) \
    V(GetVersionNumber) \
    V(GetCapabilities) \
    V(GetSourceDebugExtension) \
    V(IsMethodObsolete) \
    V(SuspendThreadList) \
    V(ResumeThreadList) \
    V(AddModuleReads) \
    V(AddModuleExports) \
    V(AddModuleOpens) \
    V(AddModuleUses) \
    V(AddModuleProvides) \
    V(IsModifiableModule) \
    V(GetAllStackTraces) \
    V(GetThreadListStackTraces) \
    V(GetThreadLocalStorage) \
    V(SetThreadLocalStorage) \
    V(GetStackTrace) \
    V(GetTag) \
    V(SetTag) \
    V(ForceGarbageCollection) \
    V(IterateOverObjectsReachableFromObject) \
    V(IterateOverReachableObjects) \
    V(IterateOverHeap) \
    V(IterateOverInstancesOfClass) \
    V(GetObjectsWithTags) \
    V(FollowReferences) \
    V(IterateThroughHeap) \
    V(SetJNIFunctionTable) \
    V(GetJNIFunctionTable) \
    V(SetEventCallbacks) \
    V(GenerateEvents) \
    V(GetExtensionFunctions) \
    V(GetExtensionEvents) \
    V(SetExtensionEventCallback) \
    V(DisposeEnvironment) \
    V(GetErrorName) \
    V(GetJLocationFormat) \
    V(GetSystemProperties) \
    V(GetSystemProperty) \
    V(SetSystemProperty) \
    V(GetPhase) \
    V(GetCurrentThreadCpuTimerInfo) \
    V(GetCurrentThreadCpuTime) \
    V(GetThreadCpuTimerInfo) \
    V(GetThreadCpuTime) \
    V(GetTimerInfo) \
    V(GetTime) \
    V(GetPotentialCapabilities) \
    V(AddCapabilities) \
    V(RelinquishCapabilities) \
    V(GetAvailableProcessors) \
    V(GetClassVersionNumbers) \
    V(GetConstantPool) \
    V(GetEnvironmentLocalStorage) \
    V(SetEnvironmentLocalStorage) \
    V(AddToBootstrapClassLoaderSearch) \
    V(SetVerboseFlag) \
    V(AddToSystemClassLoaderSearch) \
    V(RetransformClasses) \
    V(GetOwnedMonitorStackDepthInfo) \
    V(GetObjectSize) \
    V(GetLocalInstance) \
    V(SetHeapSamplingInterval)
   


void* initializeJvmtiContextImpl(TruffleEnv *truffle_env, void* (*fetch_by_name)(const char *)) {

  struct jvmtiInterface_1_ *jvmti = (jvmtiInterface_1*) malloc(sizeof(struct jvmtiInterface_1_));

  void *fn_ptr = NULL;
  #define INIT__(name) \
      fn_ptr = fetch_by_name(#name); \
      (*truffle_env)->newClosureRef(truffle_env, fn_ptr); \
      jvmti->name = fn_ptr;

  JVMTI_METHOD_LIST(INIT__)
  #undef INIT_

  return jvmti;
}

void disposeJvmtiContextImpl(TruffleEnv *truffle_env, void *jvmti_ptr) {
  struct jvmtiInterface_1_ *jvmti = (jvmtiInterface_1*) jvmti_ptr;

  #define DISPOSE__(name) \
       (*truffle_env)->releaseClosureRef(truffle_env, jvmti->name); \
       jvmti->name = NULL;

  JVMTI_METHOD_LIST(DISPOSE__)
  #undef DISPOSE__
 
  free(jvmti);
}

