/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CBitfield;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

@CContext(JvmtiDirectives.class)
@CStruct("jvmtiCapabilities")
public interface JvmtiCapabilities extends PointerBase {
    @CBitfield("can_tag_objects")
    void setCanTagObjects(boolean value);

    @CBitfield("can_tag_objects")
    boolean getCanTagObjects();

    @CBitfield("can_generate_field_modification_events")
    void setCanGenerateFieldModificationEvents(boolean value);

    @CBitfield("can_generate_field_modification_events")
    boolean getCanGenerateFieldModificationEvents();

    @CBitfield("can_generate_field_access_events")
    void setCanGenerateFieldAccessEvents(boolean value);

    @CBitfield("can_generate_field_access_events")
    boolean getCanGenerateFieldAccessEvents();

    @CBitfield("can_get_bytecodes")
    void setCanGetBytecodes(boolean value);

    @CBitfield("can_get_bytecodes")
    boolean getCanGetBytecodes();

    @CBitfield("can_get_synthetic_attribute")
    void setCanGetSyntheticAttribute(boolean value);

    @CBitfield("can_get_synthetic_attribute")
    boolean getCanGetSyntheticAttribute();

    @CBitfield("can_get_owned_monitor_info")
    void setCanGetOwnedMonitorInfo(boolean value);

    @CBitfield("can_get_owned_monitor_info")
    boolean getCanGetOwnedMonitorInfo();

    @CBitfield("can_get_current_contended_monitor")
    void setCanGetCurrentContendedMonitor(boolean value);

    @CBitfield("can_get_current_contended_monitor")
    boolean getCanGetCurrentContendedMonitor();

    @CBitfield("can_get_monitor_info")
    void setCanGetMonitorInfo(boolean value);

    @CBitfield("can_get_monitor_info")
    boolean getCanGetMonitorInfo();

    @CBitfield("can_pop_frame")
    void setCanPopFrame(boolean value);

    @CBitfield("can_pop_frame")
    boolean getCanPopFrame();

    @CBitfield("can_redefine_classes")
    void setCanRedefineClasses(boolean value);

    @CBitfield("can_redefine_classes")
    boolean getCanRedefineClasses();

    @CBitfield("can_signal_thread")
    void setCanSignalThread(boolean value);

    @CBitfield("can_signal_thread")
    boolean getCanSignalThread();

    @CBitfield("can_get_source_file_name")
    void setCanGetSourceFileName(boolean value);

    @CBitfield("can_get_source_file_name")
    boolean getCanGetSourceFileName();

    @CBitfield("can_get_line_numbers")
    void setCanGetLineNumbers(boolean value);

    @CBitfield("can_get_line_numbers")
    boolean getCanGetLineNumbers();

    @CBitfield("can_get_source_debug_extension")
    void setCanGetSourceDebugExtension(boolean value);

    @CBitfield("can_get_source_debug_extension")
    boolean getCanGetSourceDebugExtension();

    @CBitfield("can_access_local_variables")
    void setCanAccessLocalVariables(boolean value);

    @CBitfield("can_access_local_variables")
    boolean getCanAccessLocalVariables();

    @CBitfield("can_maintain_original_method_order")
    void setCanMaintainOriginalMethodOrder(boolean value);

    @CBitfield("can_maintain_original_method_order")
    boolean getCanMaintainOriginalMethodOrder();

    @CBitfield("can_generate_single_step_events")
    void setCanGenerateSingleStepEvents(boolean value);

    @CBitfield("can_generate_single_step_events")
    boolean getCanGenerateSingleStepEvents();

    @CBitfield("can_generate_exception_events")
    void setCanGenerateExceptionEvents(boolean value);

    @CBitfield("can_generate_exception_events")
    boolean getCanGenerateExceptionEvents();

    @CBitfield("can_generate_frame_pop_events")
    void setCanGenerateFramePopEvents(boolean value);

    @CBitfield("can_generate_frame_pop_events")
    boolean getCanGenerateFramePopEvents();

    @CBitfield("can_generate_breakpoint_events")
    void setCanGenerateBreakpointEvents(boolean value);

    @CBitfield("can_generate_breakpoint_events")
    boolean getCanGenerateBreakpointEvents();

    @CBitfield("can_suspend")
    void setCanSuspend(boolean value);

    @CBitfield("can_suspend")
    boolean getCanSuspend();

    @CBitfield("can_redefine_any_class")
    void setCanRedefineAnyClass(boolean value);

    @CBitfield("can_redefine_any_class")
    boolean getCanRedefineAnyClass();

    @CBitfield("can_get_current_thread_cpu_time")
    void setCanGetCurrentThreadCpuTime(boolean value);

    @CBitfield("can_get_current_thread_cpu_time")
    boolean getCanGetCurrentThreadCpuTime();

    @CBitfield("can_get_thread_cpu_time")
    void setCanGetThreadCpuTime(boolean value);

    @CBitfield("can_get_thread_cpu_time")
    boolean getCanGetThreadCpuTime();

    @CBitfield("can_generate_method_entry_events")
    void setCanGenerateMethodEntryEvents(boolean value);

    @CBitfield("can_generate_method_entry_events")
    boolean getCanGenerateMethodEntryEvents();

    @CBitfield("can_generate_method_exit_events")
    void setCanGenerateMethodExitEvents(boolean value);

    @CBitfield("can_generate_method_exit_events")
    boolean getCanGenerateMethodExitEvents();

    @CBitfield("can_generate_all_class_hook_events")
    void setCanGenerateAllClassHookEvents(boolean value);

    @CBitfield("can_generate_all_class_hook_events")
    boolean getCanGenerateAllClassHookEvents();

    @CBitfield("can_generate_compiled_method_load_events")
    void setCanGenerateCompiledMethodLoadEvents(boolean value);

    @CBitfield("can_generate_compiled_method_load_events")
    boolean getCanGenerateCompiledMethodLoadEvents();

    @CBitfield("can_generate_monitor_events")
    void setCanGenerateMonitorEvents(boolean value);

    @CBitfield("can_generate_monitor_events")
    boolean getCanGenerateMonitorEvents();

    @CBitfield("can_generate_vm_object_alloc_events")
    void setCanGenerateVmObjectAllocEvents(boolean value);

    @CBitfield("can_generate_vm_object_alloc_events")
    boolean getCanGenerateVmObjectAllocEvents();

    @CBitfield("can_generate_native_method_bind_events")
    void setCanGenerateNativeMethodBindEvents(boolean value);

    @CBitfield("can_generate_native_method_bind_events")
    boolean getCanGenerateNativeMethodBindEvents();

    @CBitfield("can_generate_garbage_collection_events")
    void setCanGenerateGarbageCollectionEvents(boolean value);

    @CBitfield("can_generate_garbage_collection_events")
    boolean getCanGenerateGarbageCollectionEvents();

    @CBitfield("can_generate_object_free_events")
    void setCanGenerateObjectFreeEvents(boolean value);

    @CBitfield("can_generate_object_free_events")
    boolean getCanGenerateObjectFreeEvents();

    @CBitfield("can_force_early_return")
    void setCanForceEarlyReturn(boolean value);

    @CBitfield("can_force_early_return")
    boolean getCanForceEarlyReturn();

    @CBitfield("can_get_owned_monitor_stack_depth_info")
    void setCanGetOwnedMonitorStackDepthInfo(boolean value);

    @CBitfield("can_get_owned_monitor_stack_depth_info")
    boolean getCanGetOwnedMonitorStackDepthInfo();

    @CBitfield("can_get_constant_pool")
    void setCanGetConstantPool(boolean value);

    @CBitfield("can_get_constant_pool")
    boolean getCanGetConstantPool();

    @CBitfield("can_set_native_method_prefix")
    void setCanSetNativeMethodPrefix(boolean value);

    @CBitfield("can_set_native_method_prefix")
    boolean getCanSetNativeMethodPrefix();

    @CBitfield("can_retransform_classes")
    void setCanRetransformClasses(boolean value);

    @CBitfield("can_retransform_classes")
    boolean getCanRetransformClasses();

    @CBitfield("can_retransform_any_class")
    void setCanRetransformAnyClass(boolean value);

    @CBitfield("can_retransform_any_class")
    boolean getCanRetransformAnyClass();

    @CBitfield("can_generate_resource_exhaustion_heap_events")
    void setCanGenerateResourceExhaustionHeapEvents(boolean value);

    @CBitfield("can_generate_resource_exhaustion_heap_events")
    boolean getCanGenerateResourceExhaustionHeapEvents();

    @CBitfield("can_generate_resource_exhaustion_threads_events")
    void setCanGenerateResourceExhaustionThreadsEvents(boolean value);

    @CBitfield("can_generate_resource_exhaustion_threads_events")
    boolean getCanGenerateResourceExhaustionThreadsEvents();

    @CBitfield("can_generate_early_vmstart")
    void setCanGenerateEarlyVmstart(boolean value);

    @CBitfield("can_generate_early_vmstart")
    boolean getCanGenerateEarlyVmstart();

    @CBitfield("can_generate_early_class_hook_events")
    void setCanGenerateEarlyClassHookEvents(boolean value);

    @CBitfield("can_generate_early_class_hook_events")
    boolean getCanGenerateEarlyClassHookEvents();

    @CBitfield("can_generate_sampled_object_alloc_events")
    void setCanGenerateSampledObjectAllocEvents(boolean value);

    @CBitfield("can_generate_sampled_object_alloc_events")
    boolean getCanGenerateSampledObjectAllocEvents();

    @CBitfield("can_support_virtual_threads")
    void setCanSupportVirtualThreads(boolean value);

    @CBitfield("can_support_virtual_threads")
    boolean getCanSupportVirtualThreads();
}
