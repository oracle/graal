/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shenandoah.nativelib;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.c.function.CFunctionOptions;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperationData;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahHeapOptions;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahInitState;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahInternalState;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahRegionBoundaries;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahRegionInfo;

import jdk.graal.compiler.word.Word;

/**
 * This class contains all methods that native-image uses for calling Shenandoah C++ code. It is
 * essential to use the correct kind of transition as we will either end up with deadlocks or
 * incorrect behavior otherwise. In the C++ code, the expected transition for each method is
 * documented in more detail. So, when changing the transition of a method, make sure to update that
 * documentation as well.
 *
 * The method {@link #parseOptions} passes a version to Shenandoah. This is used to verify if the
 * native-image really uses a compatible version. We increment this version whenever we change any
 * method below.
 */
@CContext(value = ShenandoahLibraryDependencies.class)
public class ShenandoahLibrary {
    // GraalVM major, GraalVM minor, interface revision
    public static final int VERSION = 250101;

    @CFunction(value = "svm_gc_parse_options", transition = Transition.NO_TRANSITION)
    public static native void parseOptions(int nativeImageVersion, int argc, CCharPointerPointer argv, CCharPointer imageBuildHostedArguments, CCharPointer imageBuildRuntimeArguments,
                    UnsignedWord maxHeapAddressSpaceSize, UnsignedWord heapBaseAlignment, UnsignedWord nullRegionSize, UnsignedWord imageHeapSize,
                    int compressedReferenceShift, boolean isContainerized, long containerMemoryLimitInBytes, int containerActiveProcessorCount, ShenandoahHeapOptions result);

    @CFunction(value = "svm_gc_create", transition = Transition.NO_TRANSITION)
    public static native ShenandoahInitState create(IsolateThread isolateThread, Pointer heapBase,
                    int closedImageHeapRegions, int openImageHeapRegions, Word imageHeapRegionTypes, Word imageHeapRegionFreeSpaces,
                    Word dynamicHubClass, Word fillerObjectClass, Word fillerArrayClass, Word stringClass, Word systemClass,
                    Word staticObjectFields, Word staticPrimitiveFields, Word vmOperationThread, Word safepoint, Word runtimeCodeInfoMemory,
                    int referenceMapCompressedOffsetShift, Word threadLocalsReferenceMap,
                    Word classesAssumedReachableForCodeUnloading, boolean perfDataSupport, boolean useStringInlining, boolean closedTypeWorld,
                    boolean useInterfaceHashing, int interfaceHashingMaxId, int dynamicHubHashingInterfaceMask, int dynamicHubHashingShiftOffset,
                    Word offsets, int offsetsLength,
                    CFunctionPointer collectForAllocationOp, CFunctionPointer collectFullOp,
                    CFunctionPointer waitForVMOperationExecutionStatus, CFunctionPointer updateVMOperationExecutionStatus, CFunctionPointer isVMOperationFinished,
                    CFunctionPointer fetchThreadStackFrames, CFunctionPointer freeThreadStackFrames,
                    CFunctionPointer fetchContinuationStackFrames, CFunctionPointer freeContinuationStackFrames,
                    CFunctionPointer fetchCodeInfos, CFunctionPointer freeCodeInfos, CFunctionPointer cleanRuntimeCodeCache,
                    CFunctionPointer transitionVMToNative, CFunctionPointer fastTransitionNativeToVM, CFunctionPointer slowTransitionNativeToVM);

    @CFunction(value = "svm_gc_update_option_value", transition = Transition.NO_TRANSITION)
    public static native void updateOptionValue(Word optionName, long value);

    @CFunction(value = "svm_gc_teardown", transition = Transition.NO_TRANSITION)
    public static native boolean tearDown();

    @CFunction(value = "svm_gc_attach_thread", transition = Transition.NO_TRANSITION)
    public static native void attachThread(IsolateThread thread);

    @CFunction(value = "svm_gc_detach_thread", transition = Transition.NO_TRANSITION)
    public static native void detachThread(IsolateThread thread);

    @CFunction(value = "svm_gc_retire_tlab", transition = Transition.NO_TRANSITION)
    public static native void retireTlab();

    @CFunction(value = "svm_gc_prepare_for_safepoint", transition = Transition.NO_TRANSITION)
    public static native void prepareForSafepoint();

    @CFunction(value = "svm_gc_end_safepoint", transition = Transition.NO_TRANSITION)
    public static native void endSafepoint();

    @CFunctionOptions(transition = CFunctionOptions.Transition.TO_VM)
    @CFunction(value = "svm_gc_collect")
    public static native void collect(int gcCause);

    @CFunction(value = "svm_gc_execute_vm_operation_prologue", transition = Transition.TO_NATIVE)
    public static native boolean executeVMOperationPrologue(NativeGCVMOperationData data);

    @CFunction(value = "svm_gc_execute_vm_operation_main", transition = Transition.TO_NATIVE)
    public static native void executeVMOperationMain(NativeGCVMOperationData data);

    @CFunction(value = "svm_gc_execute_vm_operation_epilogue", transition = Transition.TO_NATIVE)
    public static native void executeVMOperationEpilogue(NativeGCVMOperationData data);

    @CFunctionOptions(transition = CFunctionOptions.Transition.TO_VM)
    @CFunction(value = "svm_gc_allocate_instance")
    public static native Word allocateInstance(Word hub);

    @CFunctionOptions(transition = CFunctionOptions.Transition.TO_VM)
    @CFunction(value = "svm_gc_allocate_array")
    public static native Word allocateArray(Word hub, int length);

    @CFunctionOptions(transition = CFunctionOptions.Transition.TO_VM)
    @CFunction(value = "svm_gc_allocate_stack_chunk")
    public static native Word allocateStoredContinuation(Word hub, int length);

    @CFunctionOptions(transition = CFunctionOptions.Transition.TO_VM)
    @CFunction(value = "svm_gc_allocate_pod")
    public static native Word allocatePod(Word hub, int length);

    @CFunction(value = "svm_gc_pin_object", transition = Transition.NO_TRANSITION)
    public static native void pinObject(Word object);

    @CFunction(value = "svm_gc_unpin_object", transition = Transition.NO_TRANSITION)
    public static native void unpinObject(Word object);

    @CFunction(value = "svm_gc_pre_write_barrier", transition = Transition.NO_TRANSITION)
    public static native void preWriteBarrierStub(Word object);

    @CFunction(value = "svm_gc_post_write_barrier", transition = Transition.NO_TRANSITION)
    public static native void postWriteBarrierStub(Word cardAddress);

    @CFunction(value = "svm_gc_dirty_all_references_of", transition = Transition.NO_TRANSITION)
    public static native void dirtyAllReferencesOf(Word obj);

    @CFunction(value = "svm_gc_millis_since_last_whole_heap_examined", transition = Transition.NO_TRANSITION)
    public static native long getMillisSinceLastWholeHeapExamined();

    @CFunction(value = "svm_gc_has_reference_pending_list", transition = Transition.TO_NATIVE)
    public static native boolean hasReferencePendingList();

    @CFunctionOptions(transition = CFunctionOptions.Transition.TO_VM)
    @CFunction(value = "svm_gc_get_and_clear_reference_pending_list")
    public static native Word getAndClearReferencePendingList();

    @CFunction(value = "svm_gc_get_reference_pending_list_wakeup_count", transition = Transition.NO_TRANSITION)
    public static native long getReferencePendingListWakeupCount();

    @CFunction(value = "svm_gc_wait_for_reference_pending_list", transition = Transition.TO_NATIVE)
    public static native boolean waitForReferencePendingList(long initialWakeupCount);

    @CFunction(value = "svm_gc_wake_up_reference_pending_list_waiters", transition = Transition.TO_NATIVE)
    public static native void wakeUpReferencePendingListWaiters();

    @CFunction(value = "svm_gc_get_region_boundaries", transition = Transition.TO_NATIVE)
    public static native void getRegionBoundaries(ShenandoahRegionBoundaries regionBoundaries);

    @CFunction(value = "svm_gc_register_object_fields", transition = Transition.NO_TRANSITION)
    public static native void registerObjectFields(CodeInfo codeInfo);

    @CFunction(value = "svm_gc_register_code_constants", transition = Transition.NO_TRANSITION)
    public static native void registerCodeConstants(CodeInfo codeInfo);

    @CFunction(value = "svm_gc_register_frame_metadata", transition = Transition.NO_TRANSITION)
    public static native void registerFrameMetadata(CodeInfo codeInfo);

    @CFunction(value = "svm_gc_register_deopt_metadata", transition = Transition.NO_TRANSITION)
    public static native void registerDeoptMetadata(CodeInfo codeInfo);

    @CFunction(value = "svm_gc_get_internal_state", transition = Transition.NO_TRANSITION)
    public static native void getGCInternalState(ShenandoahInternalState result);

    @CFunction(value = "svm_gc_get_current_thread_name", transition = Transition.NO_TRANSITION)
    public static native CCharPointer getCurrentThreadName();

    @CFunction(value = "svm_gc_get_region_info", transition = Transition.NO_TRANSITION)
    public static native boolean getRegionInfo(int regionIndex, ShenandoahRegionInfo result);

    @CFunction(value = "svm_gc_get_thread_allocated_memory", transition = Transition.NO_TRANSITION)
    public static native long getThreadAllocatedMemory(IsolateThread isolateThread);

    @CFunctionOptions(transition = CFunctionOptions.Transition.TO_VM)
    @CFunction(value = "svm_gc_get_used_memory")
    public static native long getUsedMemory();

    @CFunctionOptions(transition = CFunctionOptions.Transition.TO_VM)
    @CFunction(value = "svm_gc_get_free_memory")
    public static native long getFreeMemory();

    @CFunction(value = "svm_gc_get_total_memory", transition = Transition.NO_TRANSITION)
    public static native long getTotalMemory();

    @CFunction(value = "svm_gc_get_max_memory", transition = Transition.NO_TRANSITION)
    public static native long getMaxMemory();

    @CFunction(value = "svm_gc_get_used_memory_after_last_gc", transition = Transition.NO_TRANSITION)
    public static native UnsignedWord getUsedMemoryAfterLastGC();
}
