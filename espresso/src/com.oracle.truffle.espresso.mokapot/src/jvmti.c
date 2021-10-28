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
#include "structs.h"

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

#define JVMTI_STRUCT_MEMBER_LIST(V) \
    V(_jvmtiThreadInfo, name) \
    V(_jvmtiThreadInfo, priority) \
    V(_jvmtiThreadInfo, is_daemon) \
    V(_jvmtiThreadInfo, thread_group) \
    V(_jvmtiThreadInfo, context_class_loader) \
    V(_jvmtiMonitorStackDepthInfo, monitor) \
    V(_jvmtiMonitorStackDepthInfo, stack_depth) \
    V(_jvmtiThreadGroupInfo, parent) \
    V(_jvmtiThreadGroupInfo, name) \
    V(_jvmtiThreadGroupInfo, max_priority) \
    V(_jvmtiThreadGroupInfo, is_daemon) \
    V(_jvmtiFrameInfo, method) \
    V(_jvmtiFrameInfo, location) \
    V(_jvmtiStackInfo, thread) \
    V(_jvmtiStackInfo, state) \
    V(_jvmtiStackInfo, frame_buffer) \
    V(_jvmtiStackInfo, frame_count) \
    V(_jvmtiHeapReferenceInfoField, index) \
    V(_jvmtiHeapReferenceInfoArray, index) \
    V(_jvmtiHeapReferenceInfoConstantPool, index) \
    V(_jvmtiHeapReferenceInfoStackLocal, thread_tag) \
    V(_jvmtiHeapReferenceInfoStackLocal, thread_id) \
    V(_jvmtiHeapReferenceInfoStackLocal, depth) \
    V(_jvmtiHeapReferenceInfoStackLocal, method) \
    V(_jvmtiHeapReferenceInfoStackLocal, location) \
    V(_jvmtiHeapReferenceInfoStackLocal, slot) \
    V(_jvmtiHeapReferenceInfoJniLocal, thread_tag) \
    V(_jvmtiHeapReferenceInfoJniLocal, thread_id) \
    V(_jvmtiHeapReferenceInfoJniLocal, depth) \
    V(_jvmtiHeapReferenceInfoJniLocal, method) \
    V(_jvmtiHeapReferenceInfoReserved, reserved1) \
    V(_jvmtiHeapReferenceInfoReserved, reserved2) \
    V(_jvmtiHeapReferenceInfoReserved, reserved3) \
    V(_jvmtiHeapReferenceInfoReserved, reserved4) \
    V(_jvmtiHeapReferenceInfoReserved, reserved5) \
    V(_jvmtiHeapReferenceInfoReserved, reserved6) \
    V(_jvmtiHeapReferenceInfoReserved, reserved7) \
    V(_jvmtiHeapReferenceInfoReserved, reserved8) \
    V(_jvmtiHeapCallbacks, heap_iteration_callback) \
    V(_jvmtiHeapCallbacks, heap_reference_callback) \
    V(_jvmtiHeapCallbacks, primitive_field_callback) \
    V(_jvmtiHeapCallbacks, array_primitive_value_callback) \
    V(_jvmtiHeapCallbacks, string_primitive_value_callback) \
    V(_jvmtiHeapCallbacks, reserved5) \
    V(_jvmtiHeapCallbacks, reserved6) \
    V(_jvmtiHeapCallbacks, reserved7) \
    V(_jvmtiHeapCallbacks, reserved8) \
    V(_jvmtiHeapCallbacks, reserved9) \
    V(_jvmtiHeapCallbacks, reserved10) \
    V(_jvmtiHeapCallbacks, reserved11) \
    V(_jvmtiHeapCallbacks, reserved12) \
    V(_jvmtiHeapCallbacks, reserved13) \
    V(_jvmtiHeapCallbacks, reserved14) \
    V(_jvmtiHeapCallbacks, reserved15) \
    V(_jvmtiClassDefinition, klass) \
    V(_jvmtiClassDefinition, class_byte_count) \
    V(_jvmtiClassDefinition, class_bytes) \
    V(_jvmtiMonitorUsage, owner) \
    V(_jvmtiMonitorUsage, entry_count) \
    V(_jvmtiMonitorUsage, waiter_count) \
    V(_jvmtiMonitorUsage, waiters) \
    V(_jvmtiMonitorUsage, notify_waiter_count) \
    V(_jvmtiMonitorUsage, notify_waiters) \
    V(_jvmtiLineNumberEntry, start_location) \
    V(_jvmtiLineNumberEntry, line_number) \
    V(_jvmtiLocalVariableEntry, start_location) \
    V(_jvmtiLocalVariableEntry, length) \
    V(_jvmtiLocalVariableEntry, name) \
    V(_jvmtiLocalVariableEntry, signature) \
    V(_jvmtiLocalVariableEntry, generic_signature) \
    V(_jvmtiLocalVariableEntry, slot) \
    V(_jvmtiParamInfo, name) \
    V(_jvmtiParamInfo, kind) \
    V(_jvmtiParamInfo, base_type) \
    V(_jvmtiParamInfo, null_ok) \
    V(_jvmtiExtensionFunctionInfo, func) \
    V(_jvmtiExtensionFunctionInfo, id) \
    V(_jvmtiExtensionFunctionInfo, short_description) \
    V(_jvmtiExtensionFunctionInfo, param_count) \
    V(_jvmtiExtensionFunctionInfo, params) \
    V(_jvmtiExtensionFunctionInfo, error_count) \
    V(_jvmtiExtensionFunctionInfo, errors) \
    V(_jvmtiExtensionEventInfo, extension_event_index) \
    V(_jvmtiExtensionEventInfo, id) \
    V(_jvmtiExtensionEventInfo, short_description) \
    V(_jvmtiExtensionEventInfo, param_count) \
    V(_jvmtiExtensionEventInfo, params) \
    V(_jvmtiTimerInfo, max_value) \
    V(_jvmtiTimerInfo, may_skip_forward) \
    V(_jvmtiTimerInfo, may_skip_backward) \
    V(_jvmtiTimerInfo, kind) \
    V(_jvmtiTimerInfo, reserved1) \
    V(_jvmtiTimerInfo, reserved2) \
    V(_jvmtiAddrLocationMap, start_address) \
    V(_jvmtiAddrLocationMap, location) \
    V(_jvmtiEventCallbacks, VMInit) \
    V(_jvmtiEventCallbacks, VMDeath) \
    V(_jvmtiEventCallbacks, ThreadStart) \
    V(_jvmtiEventCallbacks, ThreadEnd) \
    V(_jvmtiEventCallbacks, ClassFileLoadHook) \
    V(_jvmtiEventCallbacks, ClassLoad) \
    V(_jvmtiEventCallbacks, ClassPrepare) \
    V(_jvmtiEventCallbacks, VMStart) \
    V(_jvmtiEventCallbacks, Exception) \
    V(_jvmtiEventCallbacks, ExceptionCatch) \
    V(_jvmtiEventCallbacks, SingleStep) \
    V(_jvmtiEventCallbacks, FramePop) \
    V(_jvmtiEventCallbacks, Breakpoint) \
    V(_jvmtiEventCallbacks, FieldAccess) \
    V(_jvmtiEventCallbacks, FieldModification) \
    V(_jvmtiEventCallbacks, MethodEntry) \
    V(_jvmtiEventCallbacks, MethodExit) \
    V(_jvmtiEventCallbacks, NativeMethodBind) \
    V(_jvmtiEventCallbacks, CompiledMethodLoad) \
    V(_jvmtiEventCallbacks, CompiledMethodUnload) \
    V(_jvmtiEventCallbacks, DynamicCodeGenerated) \
    V(_jvmtiEventCallbacks, DataDumpRequest) \
    V(_jvmtiEventCallbacks, reserved72) \
    V(_jvmtiEventCallbacks, MonitorWait) \
    V(_jvmtiEventCallbacks, MonitorWaited) \
    V(_jvmtiEventCallbacks, MonitorContendedEnter) \
    V(_jvmtiEventCallbacks, MonitorContendedEntered) \
    V(_jvmtiEventCallbacks, reserved77) \
    V(_jvmtiEventCallbacks, reserved78) \
    V(_jvmtiEventCallbacks, reserved79) \
    V(_jvmtiEventCallbacks, ResourceExhausted) \
    V(_jvmtiEventCallbacks, GarbageCollectionStart) \
    V(_jvmtiEventCallbacks, GarbageCollectionFinish) \
    V(_jvmtiEventCallbacks, ObjectFree) \
    V(_jvmtiEventCallbacks, VMObjectAlloc) \
    V(_jvmtiEventCallbacks, reserved85) \
    V(_jvmtiEventCallbacks, SampledObjectAlloc) \
    V(member_info, id) \
    V(member_info, offset) \
    V(member_info, next)


#define JVMTI_STRUCT_LIST(V) \
    V(_jvmtiThreadInfo) \
    V(_jvmtiMonitorStackDepthInfo) \
    V(_jvmtiThreadGroupInfo) \
    V(_jvmtiFrameInfo) \
    V(_jvmtiStackInfo) \
    V(_jvmtiHeapReferenceInfoField) \
    V(_jvmtiHeapReferenceInfoArray) \
    V(_jvmtiHeapReferenceInfoConstantPool) \
    V(_jvmtiHeapReferenceInfoStackLocal) \
    V(_jvmtiHeapReferenceInfoJniLocal) \
    V(_jvmtiHeapReferenceInfoReserved) \
    V(_jvmtiHeapCallbacks) \
    V(_jvmtiClassDefinition) \
    V(_jvmtiMonitorUsage) \
    V(_jvmtiLineNumberEntry) \
    V(_jvmtiLocalVariableEntry) \
    V(_jvmtiParamInfo) \
    V(_jvmtiExtensionFunctionInfo) \
    V(_jvmtiExtensionEventInfo) \
    V(_jvmtiTimerInfo) \
    V(_jvmtiAddrLocationMap) \
    V(_jvmtiEventCallbacks) \
    V(member_info)


jvmtiEnv* initializeJvmtiContextImpl(void* (*fetch_by_name)(const char *)) {

  jvmtiEnv* env = (jvmtiEnv*) malloc(sizeof(*env));
  struct jvmtiInterface_1_ *jvmti = (jvmtiInterface_1*) malloc(sizeof(struct jvmtiInterface_1_));

  (*env) = jvmti;

  #define INIT__(name) \
      jvmti->name = fetch_by_name(#name);

  JVMTI_METHOD_LIST(INIT__)
  #undef INIT_

  return env;
}

JNIEXPORT jvmtiEnv* JNICALL initializeJvmtiContext(void* (*fetch_by_name)(const char *), int version) {
	if (version <= JVMTI_VERSION) {
		return initializeJvmtiContextImpl(fetch_by_name);
	} else {
		return NULL;
	}
}

void disposeJvmtiContextImpl(jvmtiEnv *env, void (*release_closure)(void *)) {
  struct jvmtiInterface_1_ *jvmti = (jvmtiInterface_1*) (*env);

  #define DISPOSE__(name) \
    if (release_closure != NULL) { \
      release_closure(jvmti->name); \
    } \
    jvmti->name = NULL;

  JVMTI_METHOD_LIST(DISPOSE__)
  #undef DISPOSE__

  free(jvmti);
  (*env) = NULL;

  free(env);
}

JNIEXPORT void JNICALL disposeJvmtiContext(jvmtiEnv* env, int version, void (*release_closure)(void *)) {
    if (version <= JVMTI_VERSION) {
		disposeJvmtiContextImpl(env, release_closure);
	}
}

