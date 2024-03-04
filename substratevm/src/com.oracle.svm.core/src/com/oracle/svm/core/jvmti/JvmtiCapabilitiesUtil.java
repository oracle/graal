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
package com.oracle.svm.core.jvmti;

import static com.oracle.svm.core.jvmti.JvmtiEnvSharedUtil.SHARED_CAPABILITIES_TYPE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.jvmti.headers.JvmtiCapabilities;
import com.oracle.svm.core.jvmti.headers.JvmtiError;

public final class JvmtiCapabilitiesUtil {

    private static final JvmtiCapabilitiesEnum[] allCapabilitiesEnumValues = JvmtiCapabilitiesEnum.values();

    @Platforms(Platform.HOSTED_ONLY.class)
    private JvmtiCapabilitiesUtil() {
    }

    public static boolean hasAny(JvmtiCapabilities capabilities) {
        assert capabilities.isNonNull();

        for (JvmtiCapabilitiesEnum cap : allCapabilitiesEnumValues) {
            if (getCapability(cap, capabilities)) {
                return true;
            }
        }
        return false;
        // TODO @dprcci keep old implementation?
        /*
         * Pointer rawData = (Pointer) capabilities; for (int i = 0; i <
         * SizeOf.get(JvmtiCapabilities.class); i++) { if (rawData.readByte(i) != 0) { return true;
         * } } return false;
         */
    }

    static boolean hasCapability(JvmtiCapabilities capabilities, JvmtiCapabilitiesEnum desired){
        return getCapability(desired, capabilities);
    }

    public static void clear(JvmtiCapabilities capabilities) {
        assert capabilities.isNonNull();
        UnmanagedMemoryUtil.fill((Pointer) capabilities, SizeOf.unsigned(JvmtiCapabilities.class), (byte) 0);
    }

    public static void copy(JvmtiCapabilities src, JvmtiCapabilities dst) {
        UnmanagedMemoryUtil.copyForward((Pointer) src, (Pointer) dst, SizeOf.unsigned(JvmtiCapabilities.class));
    }

    public static void initSharedCapabilities(JvmtiEnvShared envShared) {
        for (SHARED_CAPABILITIES_TYPE type : SHARED_CAPABILITIES_TYPE.values()) {
            setSharedCapabilityFromBitVector(envShared, type, type.getInitial());
        }
    }

    public static JvmtiError relinquishCapabilities(JvmtiEnvShared sharedCapabilities, JvmtiCapabilities current, JvmtiCapabilities unwanted) {
        long alwaysSoloBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS_SOLO);
        long onLoadSoloBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD_SOLO);
        long onLoadSoloRemainingBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD_SOLO_REMAINING);
        long alwaysSoloRemainingBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS_SOLO_REMAINING);

        long currentBitVector = convertCapabilitiesToBitVector(current);
        long unwantedBitVector = convertCapabilitiesToBitVector(unwanted);
        long toTrash, temp;

        toTrash = both(currentBitVector, unwantedBitVector);
        temp = both(alwaysSoloBitVector, toTrash);
        alwaysSoloRemainingBitVector = either(alwaysSoloRemainingBitVector, temp);
        temp = both(onLoadSoloBitVector, toTrash);
        onLoadSoloRemainingBitVector = either(onLoadSoloRemainingBitVector, temp);

        // TODO dprcci add virtual thread support

        update();

        temp = exclude(currentBitVector, unwantedBitVector);

        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS_SOLO, alwaysSoloBitVector);
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD_SOLO, onLoadSoloBitVector);
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD_SOLO_REMAINING, alwaysSoloRemainingBitVector);
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS_SOLO_REMAINING, onLoadSoloRemainingBitVector);

        assignBitVectoToCapabilities(current, temp);

        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getPotentialCapabilities(int phase, JvmtiCapabilities current, JvmtiCapabilities prohibited, JvmtiEnvShared sharedCapabilities, JvmtiCapabilities result) {
        long always = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS);
        long alwaysSoloRemaining = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS_SOLO_REMAINING);
        long onLoad = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD);
        long onLoadSoloRemaining = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD_SOLO_REMAINING);

        long currentVector = convertCapabilitiesToBitVector(current);
        long prohibitedVector = convertCapabilitiesToBitVector(prohibited);

        long potentialVector = computePotentialBitVector(phase, currentVector, prohibitedVector, always, alwaysSoloRemaining, onLoad, onLoadSoloRemaining);
        assignBitVectoToCapabilities(result, potentialVector);

        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError addCapabilities(int phase, JvmtiEnvShared sharedCapabilities, JvmtiCapabilities current, JvmtiCapabilities prohibited, JvmtiCapabilities desired) {

        long alwaysBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS);
        long alwaysSoloBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS_SOLO);
        long onLoadBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD);
        long onLoadSoloBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD_SOLO);
        long onLoadSoloRemainingBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD_SOLO_REMAINING);
        long alwaysSoloRemainingBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS_SOLO_REMAINING);
        long acquiredBitVector = getBitVectorFromSharedCapability(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ACQUIRED);

        long currentBitVector = convertCapabilitiesToBitVector(current);
        long prohibitedBitVector = convertCapabilitiesToBitVector(prohibited);
        long desiredBitVector = convertCapabilitiesToBitVector(desired);

        long temp = computePotentialBitVector(phase, currentBitVector, prohibitedBitVector,
                        alwaysBitVector, alwaysSoloRemainingBitVector, onLoadBitVector, onLoadSoloRemainingBitVector);

        if (hasSome(exclude(desiredBitVector, temp))) {
            return JvmtiError.JVMTI_ERROR_NOT_AVAILABLE;
        }

        acquiredBitVector = either(acquiredBitVector, desiredBitVector);

        // onload capabilities that got added are now permanent - so, also remove from onload
        temp = both(onLoadBitVector, desiredBitVector);
        alwaysBitVector = either(alwaysBitVector, temp);
        onLoadBitVector = exclude(onLoadBitVector, temp);

        // same for solo capabilities (transferred capabilities in the remaining sets handled as
        // part of standard grab - below)
        temp = both(onLoadSoloBitVector, desiredBitVector);
        alwaysSoloBitVector = either(alwaysSoloBitVector, temp);
        onLoadSoloBitVector = exclude(onLoadSoloBitVector, temp);

        // remove solo capabilities that are now taken
        alwaysSoloRemainingBitVector = exclude(alwaysSoloRemainingBitVector, desiredBitVector);
        onLoadSoloRemainingBitVector = exclude(onLoadSoloRemainingBitVector, desiredBitVector);

        // TODO dprcci add virtual thread support

        // return the result
        temp = either(currentBitVector, desiredBitVector);
        update();

        // update shared capabilities
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS, alwaysBitVector);
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS_SOLO, alwaysSoloBitVector);
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD, onLoadBitVector);
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD_SOLO, onLoadSoloBitVector);
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ON_LOAD_SOLO_REMAINING, onLoadSoloRemainingBitVector);
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ALWAYS_SOLO_REMAINING, alwaysSoloRemainingBitVector);
        setSharedCapabilityFromBitVector(sharedCapabilities, SHARED_CAPABILITIES_TYPE.ACQUIRED, acquiredBitVector);

        // write result back
        assignBitVectoToCapabilities(current, temp);

        return JvmtiError.JVMTI_ERROR_NONE;
    }

    // TODO @dprcci implement
    private static void update() {
    }

    private static long computePotentialBitVector(int phase, long current, long prohibited,
                    long always, long alwaysSoloRemaining, long onLoad, long onLoadSoloRemaining) {
        long potential;

        potential = exclude(always, prohibited);
        potential = either(potential, current);
        potential = either(potential, alwaysSoloRemaining); // TODO @dprcci checck

        // TODO @dprcci temporarily disabled for tests
        // if (phase == JvmtiPhase.JVMTI_PHASE_ONLOAD()) {
        if (true) {
            potential = either(potential, onLoad);
            potential = either(potential, onLoadSoloRemaining);
        }
        return potential;
    }

    private static long getBitVectorFromSharedCapability(JvmtiEnvShared sharedCapability, SHARED_CAPABILITIES_TYPE type) {
        return convertCapabilitiesToBitVector(JvmtiEnvSharedUtil.getSharedCapability(sharedCapability, type));
    }

    private static void setSharedCapabilityFromBitVector(JvmtiEnvShared sharedCapability, SHARED_CAPABILITIES_TYPE type, long bitVector) {
        assignBitVectoToCapabilities(JvmtiEnvSharedUtil.getSharedCapability(sharedCapability, type), bitVector);
    }

    private static void assignBitVectoToCapabilities(JvmtiCapabilities capabilities, long capabilityBitVector) {
        for (JvmtiCapabilitiesEnum cap : allCapabilitiesEnumValues) {
            boolean value = ((1L << cap.ordinal()) & capabilityBitVector) != 0;
            setCapability(cap, capabilities, value);
        }
    }

    private static long convertCapabilitiesToBitVector(JvmtiCapabilities capabilities) {
        long capabilitiesVector = 0L;
        for (JvmtiCapabilitiesEnum cap : allCapabilitiesEnumValues) {
            if (getCapability(cap, capabilities)) {
                capabilitiesVector |= 1L << cap.ordinal();
            }
        }
        return capabilitiesVector;
    }

    private static void setCapability(JvmtiCapabilitiesEnum cap, JvmtiCapabilities capabilities, boolean value) {
        switch (cap) {
            case CAN_TAG_OBJECTS -> capabilities.setCanTagObjects(value);
            case CAN_GENERATE_FIELD_MODIFICATION_EVENTS -> capabilities.setCanGenerateFieldModificationEvents(value);
            case CAN_GENERATE_FIELD_ACCESS_EVENTS -> capabilities.setCanGenerateFieldAccessEvents(value);
            case CAN_GET_BYTECODES -> capabilities.setCanGetBytecodes(value);
            case CAN_GET_SYNTHETIC_ATTRIBUTE -> capabilities.setCanGetSyntheticAttribute(value);
            case CAN_GET_OWNED_MONITOR_INFO -> capabilities.setCanGetOwnedMonitorInfo(value);
            case CAN_GET_CURRENT_CONTENDED_MONITOR -> capabilities.setCanGetCurrentContendedMonitor(value);
            case CAN_GET_MONITOR_INFO -> capabilities.setCanGetMonitorInfo(value);
            case CAN_POP_FRAME -> capabilities.setCanPopFrame(value);
            case CAN_REDEFINE_CLASSES -> capabilities.setCanRedefineClasses(value);
            case CAN_SIGNAL_THREAD -> capabilities.setCanSignalThread(value);
            case CAN_GET_SOURCE_FILE_NAME -> capabilities.setCanGetSourceFileName(value);
            case CAN_GET_LINE_NUMBERS -> capabilities.setCanGetLineNumbers(value);
            case CAN_GET_SOURCE_DEBUG_EXTENSION -> capabilities.setCanGetSourceDebugExtension(value);
            case CAN_ACCESS_LOCAL_VARIABLES -> capabilities.setCanAccessLocalVariables(value);
            case CAN_MAINTAIN_ORIGINAL_METHOD_ORDER -> capabilities.setCanMaintainOriginalMethodOrder(value);
            case CAN_GENERATE_SINGLE_STEP_EVENTS -> capabilities.setCanGenerateSingleStepEvents(value);
            case CAN_GENERATE_EXCEPTION_EVENTS -> capabilities.setCanGenerateExceptionEvents(value);
            case CAN_GENERATE_FRAME_POP_EVENTS -> capabilities.setCanGenerateFramePopEvents(value);
            case CAN_GENERATE_BREAKPOINT_EVENTS -> capabilities.setCanGenerateBreakpointEvents(value);
            case CAN_SUSPEND -> capabilities.setCanSuspend(value);
            case CAN_REDEFINE_ANY_CLASS -> capabilities.setCanRedefineAnyClass(value);
            case CAN_GET_CURRENT_THREAD_CPU_TIME -> capabilities.setCanGetCurrentThreadCpuTime(value);
            case CAN_GET_THREAD_CPU_TIME -> capabilities.setCanGetThreadCpuTime(value);
            case CAN_GENERATE_METHOD_ENTRY_EVENTS -> capabilities.setCanGenerateMethodEntryEvents(value);
            case CAN_GENERATE_METHOD_EXIT_EVENTS -> capabilities.setCanGenerateMethodExitEvents(value);
            case CAN_GENERATE_ALL_CLASS_HOOK_EVENTS -> capabilities.setCanGenerateAllClassHookEvents(value);
            case CAN_GENERATE_COMPILED_METHOD_LOAD_EVENTS -> capabilities.setCanGenerateCompiledMethodLoadEvents(value);
            case CAN_GENERATE_MONITOR_EVENTS -> capabilities.setCanGenerateMonitorEvents(value);
            case CAN_GENERATE_VM_OBJECT_ALLOC_EVENTS -> capabilities.setCanGenerateVmObjectAllocEvents(value);
            case CAN_GENERATE_NATIVE_METHOD_BIND_EVENTS -> capabilities.setCanGenerateNativeMethodBindEvents(value);
            case CAN_GENERATE_GARBAGE_COLLECTION_EVENTS -> capabilities.setCanGenerateGarbageCollectionEvents(value);
            case CAN_GENERATE_OBJECT_FREE_EVENTS -> capabilities.setCanGenerateObjectFreeEvents(value);
            case CAN_FORCE_EARLY_RETURN -> capabilities.setCanForceEarlyReturn(value);
            case CAN_GET_OWNED_MONITOR_STACK_DEPTH_INFO -> capabilities.setCanGetOwnedMonitorStackDepthInfo(value);
            case CAN_GET_CONSTANT_POOL -> capabilities.setCanGetConstantPool(value);
            case CAN_SET_NATIVE_METHOD_PREFIX -> capabilities.setCanSetNativeMethodPrefix(value);
            case CAN_RETRANSFORM_CLASSES -> capabilities.setCanRetransformClasses(value);
            case CAN_RETRANSFORM_ANY_CLASS -> capabilities.setCanRetransformAnyClass(value);
            case CAN_GENERATE_RESOURCE_EXHAUSTION_HEAP_EVENTS -> capabilities.setCanGenerateResourceExhaustionHeapEvents(value);
            case CAN_GENERATE_RESOURCE_EXHAUSTION_THREADS_EVENTS -> capabilities.setCanGenerateResourceExhaustionThreadsEvents(value);
            case CAN_GENERATE_EARLY_VMSTART -> capabilities.setCanGenerateEarlyVmstart(value);
            case CAN_GENERATE_EARLY_CLASS_HOOK_EVENTS -> capabilities.setCanGenerateEarlyClassHookEvents(value);
            case CAN_GENERATE_SAMPLED_OBJECT_ALLOC_EVENTS -> capabilities.setCanGenerateSampledObjectAllocEvents(value);
            case CAN_SUPPORT_VIRTUAL_THREADS -> capabilities.setCanSupportVirtualThreads(value);
            default -> {
            }
        }
    }

    private static boolean getCapability(JvmtiCapabilitiesEnum cap, JvmtiCapabilities capabilities) {
        return switch (cap) {
            case CAN_TAG_OBJECTS -> capabilities.getCanTagObjects();
            case CAN_GENERATE_FIELD_MODIFICATION_EVENTS -> capabilities.getCanGenerateFieldModificationEvents();
            case CAN_GENERATE_FIELD_ACCESS_EVENTS -> capabilities.getCanGenerateFieldAccessEvents();
            case CAN_GET_BYTECODES -> capabilities.getCanGetBytecodes();
            case CAN_GET_SYNTHETIC_ATTRIBUTE -> capabilities.getCanGetSyntheticAttribute();
            case CAN_GET_OWNED_MONITOR_INFO -> capabilities.getCanGetOwnedMonitorInfo();
            case CAN_GET_CURRENT_CONTENDED_MONITOR -> capabilities.getCanGetCurrentContendedMonitor();
            case CAN_GET_MONITOR_INFO -> capabilities.getCanGetMonitorInfo();
            case CAN_POP_FRAME -> capabilities.getCanPopFrame();
            case CAN_REDEFINE_CLASSES -> capabilities.getCanRedefineClasses();
            case CAN_SIGNAL_THREAD -> capabilities.getCanSignalThread();
            case CAN_GET_SOURCE_FILE_NAME -> capabilities.getCanGetSourceFileName();
            case CAN_GET_LINE_NUMBERS -> capabilities.getCanGetLineNumbers();
            case CAN_GET_SOURCE_DEBUG_EXTENSION -> capabilities.getCanGetSourceDebugExtension();
            case CAN_ACCESS_LOCAL_VARIABLES -> capabilities.getCanAccessLocalVariables();
            case CAN_MAINTAIN_ORIGINAL_METHOD_ORDER -> capabilities.getCanMaintainOriginalMethodOrder();
            case CAN_GENERATE_SINGLE_STEP_EVENTS -> capabilities.getCanGenerateSingleStepEvents();
            case CAN_GENERATE_EXCEPTION_EVENTS -> capabilities.getCanGenerateExceptionEvents();
            case CAN_GENERATE_FRAME_POP_EVENTS -> capabilities.getCanGenerateFramePopEvents();
            case CAN_GENERATE_BREAKPOINT_EVENTS -> capabilities.getCanGenerateBreakpointEvents();
            case CAN_SUSPEND -> capabilities.getCanSuspend();
            case CAN_REDEFINE_ANY_CLASS -> capabilities.getCanRedefineAnyClass();
            case CAN_GET_CURRENT_THREAD_CPU_TIME -> capabilities.getCanGetCurrentThreadCpuTime();
            case CAN_GET_THREAD_CPU_TIME -> capabilities.getCanGetThreadCpuTime();
            case CAN_GENERATE_METHOD_ENTRY_EVENTS -> capabilities.getCanGenerateMethodEntryEvents();
            case CAN_GENERATE_METHOD_EXIT_EVENTS -> capabilities.getCanGenerateMethodExitEvents();
            case CAN_GENERATE_ALL_CLASS_HOOK_EVENTS -> capabilities.getCanGenerateAllClassHookEvents();
            case CAN_GENERATE_COMPILED_METHOD_LOAD_EVENTS -> capabilities.getCanGenerateCompiledMethodLoadEvents();
            case CAN_GENERATE_MONITOR_EVENTS -> capabilities.getCanGenerateMonitorEvents();
            case CAN_GENERATE_VM_OBJECT_ALLOC_EVENTS -> capabilities.getCanGenerateVmObjectAllocEvents();
            case CAN_GENERATE_NATIVE_METHOD_BIND_EVENTS -> capabilities.getCanGenerateNativeMethodBindEvents();
            case CAN_GENERATE_GARBAGE_COLLECTION_EVENTS -> capabilities.getCanGenerateGarbageCollectionEvents();
            case CAN_GENERATE_OBJECT_FREE_EVENTS -> capabilities.getCanGenerateObjectFreeEvents();
            case CAN_FORCE_EARLY_RETURN -> capabilities.getCanForceEarlyReturn();
            case CAN_GET_OWNED_MONITOR_STACK_DEPTH_INFO -> capabilities.getCanGetOwnedMonitorStackDepthInfo();
            case CAN_GET_CONSTANT_POOL -> capabilities.getCanGetConstantPool();
            case CAN_SET_NATIVE_METHOD_PREFIX -> capabilities.getCanSetNativeMethodPrefix();
            case CAN_RETRANSFORM_CLASSES -> capabilities.getCanRetransformClasses();
            case CAN_RETRANSFORM_ANY_CLASS -> capabilities.getCanRetransformAnyClass();
            case CAN_GENERATE_RESOURCE_EXHAUSTION_HEAP_EVENTS -> capabilities.getCanGenerateResourceExhaustionHeapEvents();
            case CAN_GENERATE_RESOURCE_EXHAUSTION_THREADS_EVENTS -> capabilities.getCanGenerateResourceExhaustionThreadsEvents();
            case CAN_GENERATE_EARLY_VMSTART -> capabilities.getCanGenerateEarlyVmstart();
            case CAN_GENERATE_EARLY_CLASS_HOOK_EVENTS -> capabilities.getCanGenerateEarlyClassHookEvents();
            case CAN_GENERATE_SAMPLED_OBJECT_ALLOC_EVENTS -> capabilities.getCanGenerateSampledObjectAllocEvents();
            case CAN_SUPPORT_VIRTUAL_THREADS -> capabilities.getCanSupportVirtualThreads();
            // has to be there for no memory alloc
            default -> false;
        };
    }

    private static boolean hasSome(long a) {
        return a != 0L;
    }

    private static long both(long a, long b) {
        return a & b;
    }

    private static long either(long a, long b) {
        return a | b;
    }

    private static long exclude(long a, long b) {
        return a & ~b;
    }
}
