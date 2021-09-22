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

#include "jvm.h"
#include "jvmti.h"
#include "structs.h"

#include <trufflenfi.h>
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#define JNI_STRUCT_MEMBER_LIST(V) \
	V(JavaVMAttachArgs, version) \
	V(JavaVMAttachArgs, name) \
	V(JavaVMAttachArgs, group) \

#define JVM_STRUCT_MEMBER_LIST(V) \
    V(jdk_version_info, jdk_version)

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
    V(_jvmtiEventCallbacks, SampledObjectAlloc)
    
#define MEMBER_INFO_STRUCT_MEMBER_LIST(V) \
    V(member_info, id) \
    V(member_info, offset) \
    V(member_info, next)
    
#define JNI_STRUCT_LIST(V) \
	V(JavaVMAttachArgs) \
	V(JavaVMAttachArgs) \
	V(JavaVMAttachArgs) \

#define JVM_STRUCT_LIST(V) \
    V(jdk_version_info)
    
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
    V(_jvmtiEventCallbacks)
    
#define MEMBER_INFO_STRUCT_LIST(V) \
    V(member_info)

#define STRUCT_LIST_LIST(V) \
	JNI_STRUCT_LIST(V) \
	JVM_STRUCT_LIST(V) \
    JVMTI_STRUCT_LIST(V) \
    MEMBER_INFO_STRUCT_LIST(V)
    
#define STRUCT_MEMBER_LIST_LIST(V) \
	JNI_STRUCT_MEMBER_LIST(V) \
	JVM_STRUCT_MEMBER_LIST(V) \
    JVMTI_STRUCT_MEMBER_LIST(V) \
    MEMBER_INFO_STRUCT_MEMBER_LIST(V)

void add_member_info(member_info** info, char* id, size_t offset) {
	member_info* current = malloc(sizeof(struct member_info));
	current->id = id;
	current->offset = offset;
	current->next = (*info);
	*info = current;
}

size_t lookup_member_info(member_info** info, char* id) {
	for (member_info* current = *info; current != NULL; current = current->next) {
		if (strcmp(id, current->id) == 0) {
			return current->offset;
		}
	}
	return -1;
}

void free_member_info(member_info** info) {
  if (info != NULL) {
    member_info* current = (*info);
    while (current != NULL) {
      member_info* next = current->next;
      free(current);
      current = next;
    }
    free(info);
  }
}

JNIEXPORT void JNICALL initializeStructs(void (*notify_member_offset_init)(void *)) {
  member_info** info = malloc(sizeof(struct member_info*));
  (*info) = NULL;
	
  #define MEMBER_INFO__(STRUCT_NAME, MEMBER_NAME) \
    add_member_info(info, #STRUCT_NAME "." #MEMBER_NAME, offsetof(struct STRUCT_NAME, MEMBER_NAME));
    
  STRUCT_MEMBER_LIST_LIST(MEMBER_INFO__)
  #undef MEMBER_INFO__

  #define STRUCT_INFO__(STRUCT_NAME) \
    add_member_info(info, #STRUCT_NAME, sizeof(struct STRUCT_NAME));

  STRUCT_LIST_LIST(STRUCT_INFO__)
  #undef STRUCT_INFO__
  
  notify_member_offset_init(info);
  
  free_member_info(info);
}

JNIEXPORT size_t JNICALL lookupMemberOffset(void* info, char* id) {
    return lookup_member_info((member_info**) info, id);
}

