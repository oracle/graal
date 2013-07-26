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

import com.sun.management.HotSpotDiagnosticMXBean;
import sun.management.ManagementFactoryHelper;

/**
 * Used to communicate configuration details, runtime offsets, etc. to Graal upon compileMethod.
 */
public final class HotSpotVMConfig extends CompilerObject {

    private static final long serialVersionUID = -4744897993263044184L;

    private final HotSpotDiagnosticMXBean diagnostic = ManagementFactoryHelper.getDiagnosticMXBean();

    HotSpotVMConfig() {
    }

    /**
     * Gets the value of an VM option.
     * 
     * @param name option's name
     * @return value of option
     * @throws IllegalArgumentException if option doesn't exist
     */
    private int getVMOptionInt(String name) {
        String value = diagnostic.getVMOption(name).getValue();
        return new Integer(value).intValue();
    }

    /**
     * Gets the value of an VM option.
     * 
     * @param name option's name
     * @param defaultValue default value if option is not exists (e.g. development options)
     * @return value of option or defaultValue if option doesn't exist
     */
    private int getVMOption(String name, int defaultValue) {
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
    private boolean getVMOption(String name) {
        String value = diagnostic.getVMOption(name).getValue();
        return new Boolean(value).booleanValue();
    }

    /**
     * Gets the value of an VM option.
     * 
     * @param name option's name
     * @param defaultValue default value if option is not exists (e.g. development options)
     * @return value of option or defaultValue if option doesn't exist
     */
    private boolean getVMOption(String name, boolean defaultValue) {
        try {
            return getVMOption(name);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    // os information, register layout, code generation, ...
    public boolean cAssertions;
    public final boolean windowsOs = System.getProperty("os.name", "").startsWith("Windows");
    public int codeEntryAlignment;
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
    public final boolean useG1GC = getVMOption("UseG1GC");
    public long gcTotalCollectionsAddress;

    // Compressed Oops related values.
    public boolean useCompressedOops = getVMOption("UseCompressedOops");
    public boolean useCompressedKlassPointers = getVMOption("UseCompressedKlassPointers");
    public long narrowOopBase;
    public int narrowOopShift;
    public final int logMinObjAlignment = (int) (Math.log(getVMOptionInt("ObjectAlignmentInBytes")) / Math.log(2));
    public long narrowKlassBase;
    public int narrowKlassShift;
    public int logKlassAlignment;

    // CPU capabilities
    public final int useSSE = getVMOptionInt("UseSSE");
    public final int useAVX = getVMOption("UseAVX", 99);

    // offsets, ...
    public final int stackShadowPages = getVMOptionInt("StackShadowPages");

    /**
     * The offset of the mark word in an object's header.
     */
    public int markOffset;

    /**
     * The offset of the hub (i.e. Klass*) in an object's header.
     */
    public int hubOffset;

    /**
     * The offset of the _prototype_header field in a Klass.
     */
    public int prototypeMarkWordOffset;

    /**
     * The offset of the _subklass field in a Klass.
     */
    public int subklassOffset;

    /**
     * The offset of the _next_sibling field in a Klass.
     */
    public int nextSiblingOffset;

    /**
     * The offset of the array length word in an array object's header.
     */
    public int arrayLengthOffset;

    /**
     * The offset of the _length field in an Array metaspace object (see array.hpp).
     */
    public int metaspaceArrayLengthOffset;

    /**
     * The offset of the _data field in an Array metaspace object (see array.hpp).
     */
    public int metaspaceArrayBaseOffset;

    /**
     * The offset of the _super_check_offset field in a Klass.
     */
    public int superCheckOffsetOffset;

    /**
     * The offset of the _secondary_super_cache field in a Klass.
     */
    public int secondarySuperCacheOffset;

    /**
     * The offset of the _secondary_supers field in a Klass.
     */
    public int secondarySupersOffset;

    /**
     * The offset of the _init_state field in an instanceKlass.
     */
    public int klassStateOffset;

    /**
     * The value of instanceKlass::fully_initialized.
     */
    public int klassStateFullyInitialized;

    /**
     * The value of objArrayKlass::element_klass_offset().
     */
    public int arrayClassElementOffset;

    /**
     * The value of JavaThread::tlab_top_offset().
     */
    public int threadTlabTopOffset;

    /**
     * The value of JavaThread::tlab_end_offset().
     */
    public int threadTlabEndOffset;

    /**
     * The value of JavaThread::threadObj_offset().
     */
    public int threadObjectOffset;

    /**
     * The value of JavaThread::osthread_offset().
     */
    public int osThreadOffset;

    /**
     * The value of OSThread::interrupted_offset().
     */
    public int osThreadInterruptedOffset;

    /**
     * The value of markOopDesc::unlocked_value.
     */
    public int unlockedMask;

    /**
     * The value of markOopDesc::biased_lock_mask_in_place.
     */
    public int biasedLockMaskInPlace;

    /**
     * The value of markOopDesc::age_mask_in_place.
     */
    public int ageMaskInPlace;

    /**
     * The value of markOopDesc::epoch_mask_in_place.
     */
    public int epochMaskInPlace;

    /**
     * The value of markOopDesc::biased_lock_pattern.
     */
    public int biasedLockPattern;

    /**
     * Identity hash code value when uninitialized.
     */
    public int uninitializedIdentityHashCodeValue;

    /**
     * Offset of the _pending_exception field in ThreadShadow (defined in exceptions.hpp). This
     * field is used to propagate exceptions through C/C++ calls.
     * <p>
     * <b>NOTE: This is not the same as {@link #threadExceptionOopOffset}.</b>
     */
    public int pendingExceptionOffset;

    /**
     * Offset of the pending deoptimization field.
     */
    public int pendingDeoptimizationOffset;

    /**
     * Mark word right shift to get identity hash code.
     */
    public int identityHashCodeShift;

    /**
     * Offset of _access_flags in a metaspace Method object.
     */
    public int methodAccessFlagsOffset;

    /**
     * JVM_ACC_QUEUED defined in accessFlags.hpp and used for marking a Method object as queued for
     * compilation.
     */
    public int methodQueuedForCompilationBit;

    /**
     * Offset of _intrinsic_id in a metaspace Method object.
     */
    public int methodIntrinsicIdOffset;

    /**
     * Offset of _max_locals in a metaspace Method object.
     */
    public int methodMaxLocalsOffset;

    /**
     * Offset of _constMethod in a metaspace Method object.
     */
    public int methodConstMethodOffset;

    /**
     * Offset of _max_stack in a metaspace ConstMethod object.
     */
    public int constMethodMaxStackOffset;

    /**
     * Offset of _constants in a metaspace ConstMethod object.
     */
    public int constMethodConstantsOffset;

    /**
     * Offset of _pool_holder in a metaspace ConstantPool object.
     */
    public int constantPoolHolderOffset;

    /**
     * Value of extra_stack_entries() in method.hpp.
     */
    public int extraStackEntries;

    /**
     * Value of JVM_ACC_HAS_FINALIZER in accessFlags.hpp.
     */
    public int klassHasFinalizerFlag;

    /**
     * The value of JavaThread::is_method_handle_return_offset().
     */
    public int threadIsMethodHandleReturnOffset;

    /**
     * Bit pattern that represents a non-oop. Neither the high bits nor the low bits of this value
     * are allowed to look like (respectively) the high or low bits of a real oop.
     */
    public long nonOopBits;

    public long verifyOopCounterAddress;
    public long verifyOopMask;
    public long verifyOopBits;

    /**
     * Offset of the _exception_oop field in Thread (defined in thread.hpp). This field is used to
     * pass exception objects into and out of the runtime system during exception handling for
     * compiled code.
     * <p>
     * <b>NOTE: This is not the same as {@link #pendingExceptionOffset}.</b>
     */
    public int threadExceptionOopOffset;

    public int threadExceptionPcOffset;
    public long cardtableStartAddress;
    public int cardtableShift;
    public long safepointPollingAddress;
    public boolean isPollingPageFar;

    /**
     * G1 Collector Related Values.
     */
    public int g1CardQueueIndexOffset;
    public int g1CardQueueBufferOffset;
    public int logOfHRGrainBytes;
    public int g1SATBQueueMarkingOffset;
    public int g1SATBQueueIndexOffset;
    public int g1SATBQueueBufferOffset;

    /**
     * The offset of the _java_mirror field (of type {@link Class}) in a Klass.
     */
    public int classMirrorOffset;

    public int runtimeCallStackSize;

    /**
     * The offset of the _modifier_flags field in a Klass.
     */
    public int klassModifierFlagsOffset;

    /**
     * The offset of the _access_flags field in a Klass.
     */
    public int klassAccessFlagsOffset;

    /**
     * The offset of the _layout_helper field in a Klass.
     */
    public int klassLayoutHelperOffset;

    /**
     * Bit pattern in the klass layout helper that can be used to identify arrays.
     */
    public int arrayKlassLayoutHelperIdentifier;

    /**
     * The offset of the _componentMirror field in an ArrayKlass.
     */
    public int arrayKlassComponentMirrorOffset;

    /**
     * The offset of the _super field in a Klass.
     */
    public int klassSuperKlassOffset;

    /**
     * The offset of the injected klass field in a {@link Class}.
     */
    public int klassOffset;

    /**
     * The offset of the injected array klass field in a {@link Class}.
     */
    public int arrayKlassOffset;

    /**
     * The offset of the injected graal_mirror field in a {@link Class}.
     */
    public int graalMirrorInClassOffset;

    /**
     * The offset of the _method_data field in a metaspace Method.
     */
    public int methodDataOffset;

    public int nmethodEntryOffset;
    public int methodCompiledEntryOffset;
    public int basicLockSize;
    public int basicLockDisplacedHeaderOffset;
    public long tlabIntArrayMarkWord;
    public long heapEndAddress;
    public long heapTopAddress;
    public int threadTlabStartOffset;
    public int threadTlabSizeOffset;
    public int threadAllocatedBytesOffset;
    public int threadLastJavaSpOffset;
    public int threadLastJavaPcOffset;

    /**
     * This value is only valid on AMD64.
     */
    public int threadLastJavaFpOffset;

    /**
     * This value is only valid on SPARC.
     */
    public int threadJavaFrameAnchorFlagsOffset;

    public int threadObjectResultOffset;
    public int tlabRefillWasteLimitOffset;
    public int tlabRefillWasteIncrement;
    public int tlabAlignmentReserve;
    public int tlabSlowAllocationsOffset;
    public int tlabFastRefillWasteOffset;
    public int tlabNumberOfRefillsOffset;
    public final boolean tlabStats = getVMOption("TLABStats");
    public int klassInstanceSizeOffset;
    public boolean inlineContiguousAllocationSupported;
    public long arrayPrototypeMarkWord;
    public int layoutHelperLog2ElementSizeShift;
    public int layoutHelperLog2ElementSizeMask;
    public int layoutHelperElementTypeShift;
    public int layoutHelperElementTypeMask;
    public int layoutHelperElementTypePrimitiveInPlace;
    public int layoutHelperHeaderSizeShift;
    public int layoutHelperHeaderSizeMask;
    public int layoutHelperOffset;

    // methodData information
    public int methodDataOopDataOffset;
    public int methodDataOopTrapHistoryOffset;
    public int dataLayoutHeaderSize;
    public int dataLayoutTagOffset;
    public int dataLayoutFlagsOffset;
    public int dataLayoutBCIOffset;
    public int dataLayoutCellsOffset;
    public int dataLayoutCellSize;
    public final int bciProfileWidth = getVMOption("BciProfileWidth", 2);  // develop flag; might
// change
    public final int typeProfileWidth = getVMOptionInt("TypeProfileWidth");
    public final int methodProfileWidth = getVMOptionInt("MethodProfileWidth");

    public long inlineCacheMissStub;
    public long handleDeoptStub;
    public long uncommonTrapStub;

    public long aescryptEncryptBlockStub;
    public long aescryptDecryptBlockStub;
    public long cipherBlockChainingEncryptAESCryptStub;
    public long cipherBlockChainingDecryptAESCryptStub;

    public long newInstanceAddress;
    public long newArrayAddress;
    public long newMultiArrayAddress;
    public long dynamicNewArrayAddress;
    public long registerFinalizerAddress;
    public long threadIsInterruptedAddress;
    public long vmMessageAddress;
    public long identityHashCodeAddress;
    public long exceptionHandlerForPcAddress;
    public long exceptionHandlerForReturnAddressAddress;
    public long osrMigrationEndAddress;
    public long monitorenterAddress;
    public long monitorexitAddress;
    public long createNullPointerExceptionAddress;
    public long createOutOfBoundsExceptionAddress;
    public long logPrimitiveAddress;
    public long logObjectAddress;
    public long logPrintfAddress;
    public long vmErrorAddress;
    public long writeBarrierPreAddress;
    public long writeBarrierPostAddress;
    public long javaTimeMillisAddress;
    public long javaTimeNanosAddress;
    public long arithmeticSinAddress;
    public long arithmeticCosAddress;
    public long arithmeticTanAddress;
    public long loadAndClearExceptionAddress;

    public int deoptReasonNone;
    public int deoptReasonNullCheck;
    public int deoptReasonRangeCheck;
    public int deoptReasonClassCheck;
    public int deoptReasonArrayCheck;
    public int deoptReasonUnreached0;
    public int deoptReasonTypeCheckInlining;
    public int deoptReasonOptimizedTypeCheck;
    public int deoptReasonNotCompiledExceptionHandler;
    public int deoptReasonUnresolved;
    public int deoptReasonJsrMismatch;
    public int deoptReasonDiv0Check;
    public int deoptReasonConstraint;
    public int deoptReasonLoopLimitCheck;

    public int deoptActionNone;
    public int deoptActionMaybeRecompile;
    public int deoptActionReinterpret;
    public int deoptActionMakeNotEntrant;
    public int deoptActionMakeNotCompilable;

    public int vmIntrinsicInvokeBasic;
    public int vmIntrinsicLinkToVirtual;
    public int vmIntrinsicLinkToStatic;
    public int vmIntrinsicLinkToSpecial;
    public int vmIntrinsicLinkToInterface;

    public void check() {
        assert codeEntryAlignment > 0;
        assert stackShadowPages > 0;
    }
}
