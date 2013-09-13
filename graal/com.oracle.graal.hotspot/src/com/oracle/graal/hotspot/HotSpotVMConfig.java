/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import java.lang.reflect.*;

import com.oracle.graal.hotspot.bridge.*;
import com.sun.management.HotSpotDiagnosticMXBean;

import sun.management.ManagementFactoryHelper;

/**
 * Used to access native configuration details.
 * 
 * All non-static, public fields in this class are final so that they can be compiled as constants.
 */
public final class HotSpotVMConfig extends CompilerObject {

    private static final long serialVersionUID = -4744897993263044184L;

    private static final HotSpotDiagnosticMXBean diagnostic = ManagementFactoryHelper.getDiagnosticMXBean();

    HotSpotVMConfig(CompilerToVM c2vm) {
        c2vm.initializeConfiguration(this);
        assert check();
    }

    /**
     * Gets the value of an VM option.
     * 
     * @param name option's name
     * @return value of option
     * @throws IllegalArgumentException if option doesn't exist
     */
    public static int getVMOptionInt(String name) {
        String value = diagnostic.getVMOption(name).getValue();
        return Integer.valueOf(value).intValue();
    }

    /**
     * Gets the value of an VM option.
     * 
     * @param name option's name
     * @param defaultValue default value if option is not exists (e.g. development options)
     * @return value of option or defaultValue if option doesn't exist
     */
    public static int getVMOption(String name, int defaultValue) {
        try {
            return getVMOptionInt(name);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /**
     * Gets the value of an VM option.
     * 
     * @param name option's name
     * @return value of option
     * @throws IllegalArgumentException if option doesn't exist
     */
    public static boolean getVMOption(String name) {
        String value = diagnostic.getVMOption(name).getValue();
        return Boolean.valueOf(value).booleanValue();
    }

    /**
     * Gets the value of an VM option.
     * 
     * @param name option's name
     * @param defaultValue default value if option is not exists (e.g. development options)
     * @return value of option or defaultValue if option doesn't exist
     */
    public static boolean getVMOption(String name, boolean defaultValue) {
        try {
            return getVMOption(name);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    // Using systenm properties ensures the Java source compilers can never
    // optimize away an access to a config field
    private static final boolean UNINITIALIZED_BOOLEAN = Boolean.getBoolean("graal.config.uninitializedBoolean");
    private static final long UNINITIALIZED_LONG = Long.getLong("graal.config.uninitializedLong", 0L);
    private static final int UNINITIALIZED_INT = Integer.getInteger("graal.config.uninitializedInt", 0);

    private static int getUninitializedInt() {
        return UNINITIALIZED_INT;
    }

    private static long getUninitializedLong() {
        return UNINITIALIZED_LONG;
    }

    private static boolean getUninitializedBoolean() {
        return UNINITIALIZED_BOOLEAN;
    }

    // os information, register layout, code generation, ...
    public final boolean cAssertions = getUninitializedBoolean();
    public final boolean windowsOs = System.getProperty("os.name", "").startsWith("Windows");
    public final int codeEntryAlignment = getUninitializedInt();
    public final boolean verifyOops = getVMOption("VerifyOops", false);
    public final boolean ciTime = getVMOption("CITime");
    public final int compileThreshold = getVMOptionInt("CompileThreshold");
    public final boolean compileTheWorld = getVMOption("CompileTheWorld", false);
    public final int compileTheWorldStartAt = getVMOption("CompileTheWorldStartAt", 1);
    public final int compileTheWorldStopAt = getVMOption("CompileTheWorldStopAt", Integer.MAX_VALUE);
    public final boolean printCompilation = getVMOption("PrintCompilation");
    public final boolean printInlining = getVMOption("PrintInlining", false);
    public final boolean useFastLocking = getVMOption("GraalUseFastLocking", true);
    public final boolean useTLAB = getVMOption("UseTLAB");
    public final boolean useBiasedLocking = getVMOption("UseBiasedLocking");
    public final boolean usePopCountInstruction = getVMOption("UsePopCountInstruction");
    public final boolean useAESIntrinsics = getVMOption("UseAESIntrinsics");
    public final boolean useCRC32Intrinsics = getVMOption("UseCRC32Intrinsics");
    public final boolean useG1GC = getVMOption("UseG1GC");
    public final long gcTotalCollectionsAddress = getUninitializedLong();

    // Compressed Oops related values.
    public final boolean useCompressedOops = getVMOption("UseCompressedOops");
    public final boolean useCompressedKlassPointers = getVMOption("UseCompressedKlassPointers");
    public final long narrowOopBase = getUninitializedLong();
    public final int narrowOopShift = getUninitializedInt();
    public final int logMinObjAlignment = (int) (Math.log(getVMOptionInt("ObjectAlignmentInBytes")) / Math.log(2));
    public final long narrowKlassBase = getUninitializedLong();
    public final int narrowKlassShift = getUninitializedInt();
    public final int logKlassAlignment = getUninitializedInt();

    // CPU capabilities
    public final int useSSE = getVMOptionInt("UseSSE");
    public final int useAVX = getVMOption("UseAVX", 99);

    // offsets, ...
    public final int stackShadowPages = getVMOptionInt("StackShadowPages");

    /**
     * The offset of the mark word in an object's header.
     */
    public final int markOffset = getUninitializedInt();

    /**
     * The offset of the hub (i.e. Klass*) in an object's header.
     */
    public final int hubOffset = getUninitializedInt();

    /**
     * The offset of the _prototype_header field in a Klass.
     */
    public final int prototypeMarkWordOffset = getUninitializedInt();

    /**
     * The offset of the _subklass field in a Klass.
     */
    public final int subklassOffset = getUninitializedInt();

    /**
     * The offset of the _next_sibling field in a Klass.
     */
    public final int nextSiblingOffset = getUninitializedInt();

    /**
     * The offset of the array length word in an array object's header.
     */
    public final int arrayLengthOffset = getUninitializedInt();

    /**
     * The offset of the _length field in an Array metaspace object (see array.hpp).
     */
    public final int metaspaceArrayLengthOffset = getUninitializedInt();

    /**
     * The offset of the _data field in an Array metaspace object (see array.hpp).
     */
    public final int metaspaceArrayBaseOffset = getUninitializedInt();

    /**
     * The offset of the _super_check_offset field in a Klass.
     */
    public final int superCheckOffsetOffset = getUninitializedInt();

    /**
     * The offset of the _secondary_super_cache field in a Klass.
     */
    public final int secondarySuperCacheOffset = getUninitializedInt();

    /**
     * The offset of the _secondary_supers field in a Klass.
     */
    public final int secondarySupersOffset = getUninitializedInt();

    /**
     * The offset of the _init_state field in an instanceKlass.
     */
    public final int klassStateOffset = getUninitializedInt();

    /**
     * The value of instanceKlass::fully_initialized.
     */
    public final int klassStateFullyInitialized = getUninitializedInt();

    /**
     * The value of objArrayKlass::element_klass_offset().
     */
    public final int arrayClassElementOffset = getUninitializedInt();

    /**
     * The value of JavaThread::tlab_top_offset().
     */
    public final int threadTlabTopOffset = getUninitializedInt();

    /**
     * The value of JavaThread::tlab_end_offset().
     */
    public final int threadTlabEndOffset = getUninitializedInt();

    /**
     * The value of JavaThread::threadObj_offset().
     */
    public final int threadObjectOffset = getUninitializedInt();

    /**
     * The value of JavaThread::osthread_offset().
     */
    public final int osThreadOffset = getUninitializedInt();

    /**
     * The value of OSThread::interrupted_offset().
     */
    public final int osThreadInterruptedOffset = getUninitializedInt();

    /**
     * The value of markOopDesc::unlocked_value.
     */
    public final int unlockedMask = getUninitializedInt();

    /**
     * The value of markOopDesc::biased_lock_mask_in_place.
     */
    public final int biasedLockMaskInPlace = getUninitializedInt();

    /**
     * The value of markOopDesc::age_mask_in_place.
     */
    public final int ageMaskInPlace = getUninitializedInt();

    /**
     * The value of markOopDesc::epoch_mask_in_place.
     */
    public final int epochMaskInPlace = getUninitializedInt();

    /**
     * The value of markOopDesc::biased_lock_pattern.
     */
    public final int biasedLockPattern = getUninitializedInt();

    /**
     * Identity hash code value when uninitialized.
     */
    public final int uninitializedIdentityHashCodeValue = getUninitializedInt();

    /**
     * Offset of the _pending_exception field in ThreadShadow (defined in exceptions.hpp). This
     * field is used to propagate exceptions through C/C++ calls.
     * <p>
     * <b>NOTE: This is not the same as {@link #threadExceptionOopOffset}.</b>
     */
    public final int pendingExceptionOffset = getUninitializedInt();

    /**
     * Offset of the pending deoptimization field.
     */
    public final int pendingDeoptimizationOffset = getUninitializedInt();

    /**
     * Mark word right shift to get identity hash code.
     */
    public final int identityHashCodeShift = getUninitializedInt();

    /**
     * Offset of _access_flags in a metaspace Method object.
     */
    public final int methodAccessFlagsOffset = getUninitializedInt();

    /**
     * JVM_ACC_QUEUED defined in accessFlags.hpp and used for marking a Method object as queued for
     * compilation.
     */
    public final int methodQueuedForCompilationBit = getUninitializedInt();

    /**
     * Offset of _intrinsic_id in a metaspace Method object.
     */
    public final int methodIntrinsicIdOffset = getUninitializedInt();

    /**
     * Offset of _max_locals in a metaspace Method object.
     */
    public final int methodMaxLocalsOffset = getUninitializedInt();

    /**
     * Offset of _constMethod in a metaspace Method object.
     */
    public final int methodConstMethodOffset = getUninitializedInt();

    /**
     * Offset of _max_stack in a metaspace ConstMethod object.
     */
    public final int constMethodMaxStackOffset = getUninitializedInt();

    /**
     * Offset of _constants in a metaspace ConstMethod object.
     */
    public final int constMethodConstantsOffset = getUninitializedInt();

    /**
     * Offset of _pool_holder in a metaspace ConstantPool object.
     */
    public final int constantPoolHolderOffset = getUninitializedInt();

    /**
     * Value of extra_stack_entries() in method.hpp.
     */
    public final int extraStackEntries = getUninitializedInt();

    /**
     * Value of JVM_ACC_HAS_FINALIZER in accessFlags.hpp.
     */
    public final int klassHasFinalizerFlag = getUninitializedInt();

    /**
     * The value of JavaThread::is_method_handle_return_offset().
     */
    public final int threadIsMethodHandleReturnOffset = getUninitializedInt();

    /**
     * Bit pattern that represents a non-oop. Neither the high bits nor the low bits of this value
     * are allowed to look like (respectively) the high or low bits of a real oop.
     */
    public final long nonOopBits = getUninitializedLong();

    public final long verifyOopCounterAddress = getUninitializedLong();
    public final long verifyOopMask = getUninitializedLong();
    public final long verifyOopBits = getUninitializedLong();

    /**
     * Offset of the _exception_oop field in Thread (defined in thread.hpp). This field is used to
     * pass exception objects into and out of the runtime system during exception handling for
     * compiled code.
     * <p>
     * <b>NOTE: This is not the same as {@link #pendingExceptionOffset}.</b>
     */
    public final int threadExceptionOopOffset = getUninitializedInt();

    public final int threadExceptionPcOffset = getUninitializedInt();
    public final long cardtableStartAddress = getUninitializedLong();
    public final int cardtableShift = getUninitializedInt();
    public final long safepointPollingAddress = getUninitializedLong();
    public final boolean isPollingPageFar = getUninitializedBoolean();

    /**
     * G1 Collector Related Values.
     */
    public final int g1CardQueueIndexOffset = getUninitializedInt();
    public final int g1CardQueueBufferOffset = getUninitializedInt();
    public final int logOfHRGrainBytes = getUninitializedInt();
    public final int g1SATBQueueMarkingOffset = getUninitializedInt();
    public final int g1SATBQueueIndexOffset = getUninitializedInt();
    public final int g1SATBQueueBufferOffset = getUninitializedInt();

    /**
     * The offset of the _java_mirror field (of type {@link Class}) in a Klass.
     */
    public final int classMirrorOffset = getUninitializedInt();

    public final int runtimeCallStackSize = getUninitializedInt();

    /**
     * The offset of the _modifier_flags field in a Klass.
     */
    public final int klassModifierFlagsOffset = getUninitializedInt();

    /**
     * The offset of the _access_flags field in a Klass.
     */
    public final int klassAccessFlagsOffset = getUninitializedInt();

    /**
     * The offset of the _layout_helper field in a Klass.
     */
    public final int klassLayoutHelperOffset = getUninitializedInt();

    /**
     * Bit pattern in the klass layout helper that can be used to identify arrays.
     */
    public final int arrayKlassLayoutHelperIdentifier = getUninitializedInt();

    /**
     * The offset of the _componentMirror field in an ArrayKlass.
     */
    public final int arrayKlassComponentMirrorOffset = getUninitializedInt();

    /**
     * The offset of the _super field in a Klass.
     */
    public final int klassSuperKlassOffset = getUninitializedInt();

    /**
     * The offset of the injected klass field in a {@link Class}.
     */
    public final int klassOffset = getUninitializedInt();

    /**
     * The offset of the injected array klass field in a {@link Class}.
     */
    public final int arrayKlassOffset = getUninitializedInt();

    /**
     * The offset of the injected graal_mirror field in a {@link Class}.
     */
    public final int graalMirrorInClassOffset = getUninitializedInt();

    /**
     * The offset of the _method_data field in a metaspace Method.
     */
    public final int methodDataOffset = getUninitializedInt();

    public final int nmethodEntryOffset = getUninitializedInt();
    public final int methodCompiledEntryOffset = getUninitializedInt();
    public final int basicLockSize = getUninitializedInt();
    public final int basicLockDisplacedHeaderOffset = getUninitializedInt();
    public final long tlabIntArrayMarkWord = getUninitializedLong();
    public final long heapEndAddress = getUninitializedLong();
    public final long heapTopAddress = getUninitializedLong();
    public final int threadTlabStartOffset = getUninitializedInt();
    public final int threadTlabSizeOffset = getUninitializedInt();
    public final int threadAllocatedBytesOffset = getUninitializedInt();
    public final int threadLastJavaSpOffset = getUninitializedInt();
    public final int threadLastJavaPcOffset = getUninitializedInt();

    /**
     * This value is only valid on AMD64.
     */
    public final int threadLastJavaFpOffset = getUninitializedInt();

    /**
     * This value is only valid on SPARC.
     */
    public final int threadJavaFrameAnchorFlagsOffset = getUninitializedInt();

    public final int threadObjectResultOffset = getUninitializedInt();
    public final int tlabRefillWasteLimitOffset = getUninitializedInt();
    public final int tlabRefillWasteIncrement = getUninitializedInt();
    public final int tlabAlignmentReserve = getUninitializedInt();
    public final int tlabSlowAllocationsOffset = getUninitializedInt();
    public final int tlabFastRefillWasteOffset = getUninitializedInt();
    public final int tlabNumberOfRefillsOffset = getUninitializedInt();
    public final boolean tlabStats = getVMOption("TLABStats");
    public final int klassInstanceSizeOffset = getUninitializedInt();
    public final boolean inlineContiguousAllocationSupported = getUninitializedBoolean();
    public final long arrayPrototypeMarkWord = getUninitializedLong();
    public final int layoutHelperLog2ElementSizeShift = getUninitializedInt();
    public final int layoutHelperLog2ElementSizeMask = getUninitializedInt();
    public final int layoutHelperElementTypeShift = getUninitializedInt();
    public final int layoutHelperElementTypeMask = getUninitializedInt();
    public final int layoutHelperElementTypePrimitiveInPlace = getUninitializedInt();
    public final int layoutHelperHeaderSizeShift = getUninitializedInt();
    public final int layoutHelperHeaderSizeMask = getUninitializedInt();
    public final int layoutHelperOffset = getUninitializedInt();

    // methodData information
    public final int methodDataOopDataOffset = getUninitializedInt();
    public final int methodDataOopTrapHistoryOffset = getUninitializedInt();
    public final int dataLayoutHeaderSize = getUninitializedInt();
    public final int dataLayoutTagOffset = getUninitializedInt();
    public final int dataLayoutFlagsOffset = getUninitializedInt();
    public final int dataLayoutBCIOffset = getUninitializedInt();
    public final int dataLayoutCellsOffset = getUninitializedInt();
    public final int dataLayoutCellSize = getUninitializedInt();

    // develop flag; might change
    public final int bciProfileWidth = getVMOption("BciProfileWidth", 2);

    public final int typeProfileWidth = getVMOptionInt("TypeProfileWidth");
    public final int methodProfileWidth = getVMOptionInt("MethodProfileWidth");

    public final long inlineCacheMissStub = getUninitializedLong();
    public final long handleDeoptStub = getUninitializedLong();
    public final long uncommonTrapStub = getUninitializedLong();

    public final long aescryptEncryptBlockStub = getUninitializedLong();
    public final long aescryptDecryptBlockStub = getUninitializedLong();
    public final long cipherBlockChainingEncryptAESCryptStub = getUninitializedLong();
    public final long cipherBlockChainingDecryptAESCryptStub = getUninitializedLong();
    public final long updateBytesCRC32Stub = getUninitializedLong();

    public final long newInstanceAddress = getUninitializedLong();
    public final long newArrayAddress = getUninitializedLong();
    public final long newMultiArrayAddress = getUninitializedLong();
    public final long dynamicNewArrayAddress = getUninitializedLong();
    public final long registerFinalizerAddress = getUninitializedLong();
    public final long threadIsInterruptedAddress = getUninitializedLong();
    public final long vmMessageAddress = getUninitializedLong();
    public final long identityHashCodeAddress = getUninitializedLong();
    public final long exceptionHandlerForPcAddress = getUninitializedLong();
    public final long exceptionHandlerForReturnAddressAddress = getUninitializedLong();
    public final long osrMigrationEndAddress = getUninitializedLong();
    public final long monitorenterAddress = getUninitializedLong();
    public final long monitorexitAddress = getUninitializedLong();
    public final long createNullPointerExceptionAddress = getUninitializedLong();
    public final long createOutOfBoundsExceptionAddress = getUninitializedLong();
    public final long logPrimitiveAddress = getUninitializedLong();
    public final long logObjectAddress = getUninitializedLong();
    public final long logPrintfAddress = getUninitializedLong();
    public final long vmErrorAddress = getUninitializedLong();
    public final long writeBarrierPreAddress = getUninitializedLong();
    public final long writeBarrierPostAddress = getUninitializedLong();
    public final long validateObject = getUninitializedLong();
    public final long javaTimeMillisAddress = getUninitializedLong();
    public final long javaTimeNanosAddress = getUninitializedLong();
    public final long arithmeticSinAddress = getUninitializedLong();
    public final long arithmeticCosAddress = getUninitializedLong();
    public final long arithmeticTanAddress = getUninitializedLong();
    public final long loadAndClearExceptionAddress = getUninitializedLong();
    public final long crcTableAddress = getUninitializedLong();

    public final int deoptReasonNone = getUninitializedInt();
    public final int deoptReasonNullCheck = getUninitializedInt();
    public final int deoptReasonRangeCheck = getUninitializedInt();
    public final int deoptReasonClassCheck = getUninitializedInt();
    public final int deoptReasonArrayCheck = getUninitializedInt();
    public final int deoptReasonUnreached0 = getUninitializedInt();
    public final int deoptReasonTypeCheckInlining = getUninitializedInt();
    public final int deoptReasonOptimizedTypeCheck = getUninitializedInt();
    public final int deoptReasonNotCompiledExceptionHandler = getUninitializedInt();
    public final int deoptReasonUnresolved = getUninitializedInt();
    public final int deoptReasonJsrMismatch = getUninitializedInt();
    public final int deoptReasonDiv0Check = getUninitializedInt();
    public final int deoptReasonConstraint = getUninitializedInt();
    public final int deoptReasonLoopLimitCheck = getUninitializedInt();

    public final int deoptActionNone = getUninitializedInt();
    public final int deoptActionMaybeRecompile = getUninitializedInt();
    public final int deoptActionReinterpret = getUninitializedInt();
    public final int deoptActionMakeNotEntrant = getUninitializedInt();
    public final int deoptActionMakeNotCompilable = getUninitializedInt();

    public final int vmIntrinsicInvokeBasic = getUninitializedInt();
    public final int vmIntrinsicLinkToVirtual = getUninitializedInt();
    public final int vmIntrinsicLinkToStatic = getUninitializedInt();
    public final int vmIntrinsicLinkToSpecial = getUninitializedInt();
    public final int vmIntrinsicLinkToInterface = getUninitializedInt();

    public boolean check() {
        assert codeEntryAlignment > 0 : codeEntryAlignment;
        assert stackShadowPages > 0;
        for (Field f : getClass().getDeclaredFields()) {
            int modifiers = f.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                assert Modifier.isFinal(modifiers) : "field should be final: " + f;
            }
        }
        return true;
    }
}
