/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.jvmci.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

//JaCoCo Exclude

/**
 * A collection of methods used in HotSpot snippets, substitutions and stubs.
 */
public class HotSpotReplacementsUtil {

    @Fold
    public static HotSpotVMConfig config() {
        return runtime().getConfig();
    }

    @Fold
    public static boolean useTLAB() {
        return config().useTLAB;
    }

    @Fold
    public static boolean verifyOops() {
        return config().verifyOops;
    }

    public static final LocationIdentity EXCEPTION_OOP_LOCATION = NamedLocationIdentity.mutable("ExceptionOop");

    /**
     * @see HotSpotVMConfig#threadExceptionOopOffset
     */
    @Fold
    public static int threadExceptionOopOffset() {
        return config().threadExceptionOopOffset;
    }

    public static final LocationIdentity EXCEPTION_PC_LOCATION = NamedLocationIdentity.mutable("ExceptionPc");

    @Fold
    public static int threadExceptionPcOffset() {
        return config().threadExceptionPcOffset;
    }

    public static final LocationIdentity LAST_JAVA_PC_LOCATION = NamedLocationIdentity.mutable("LastJavaPc");

    @Fold
    public static int threadLastJavaPcOffset() {
        return config().threadLastJavaPcOffset();
    }

    public static final LocationIdentity LAST_JAVA_FP_LOCATION = NamedLocationIdentity.mutable("LastJavaFp");

    @Fold
    public static int threadLastJavaFpOffset() {
        return config().threadLastJavaFpOffset();
    }

    public static final LocationIdentity TLAB_TOP_LOCATION = NamedLocationIdentity.mutable("TlabTop");

    @Fold
    public static int threadTlabTopOffset() {
        return config().threadTlabTopOffset();
    }

    public static final LocationIdentity TLAB_END_LOCATION = NamedLocationIdentity.mutable("TlabEnd");

    @Fold
    private static int threadTlabEndOffset() {
        return config().threadTlabEndOffset();
    }

    public static final LocationIdentity TLAB_START_LOCATION = NamedLocationIdentity.mutable("TlabStart");

    @Fold
    private static int threadTlabStartOffset() {
        return config().threadTlabStartOffset();
    }

    public static final LocationIdentity PENDING_EXCEPTION_LOCATION = NamedLocationIdentity.mutable("PendingException");

    /**
     * @see HotSpotVMConfig#pendingExceptionOffset
     */
    @Fold
    private static int threadPendingExceptionOffset() {
        return config().pendingExceptionOffset;
    }

    public static final LocationIdentity PENDING_DEOPTIMIZATION_LOCATION = NamedLocationIdentity.mutable("PendingDeoptimization");

    /**
     * @see HotSpotVMConfig#pendingDeoptimizationOffset
     */
    @Fold
    private static int threadPendingDeoptimizationOffset() {
        return config().pendingDeoptimizationOffset;
    }

    public static final LocationIdentity OBJECT_RESULT_LOCATION = NamedLocationIdentity.mutable("ObjectResult");

    @Fold
    private static int objectResultOffset() {
        return config().threadObjectResultOffset;
    }

    /**
     * @see HotSpotVMConfig#threadExceptionOopOffset
     */
    public static Object readExceptionOop(Word thread) {
        return thread.readObject(threadExceptionOopOffset(), EXCEPTION_OOP_LOCATION);
    }

    public static Word readExceptionPc(Word thread) {
        return thread.readWord(threadExceptionPcOffset(), EXCEPTION_PC_LOCATION);
    }

    /**
     * @see HotSpotVMConfig#threadExceptionOopOffset
     */
    public static void writeExceptionOop(Word thread, Object value) {
        thread.writeObject(threadExceptionOopOffset(), value, EXCEPTION_OOP_LOCATION);
    }

    public static void writeExceptionPc(Word thread, Word value) {
        thread.writeWord(threadExceptionPcOffset(), value, EXCEPTION_PC_LOCATION);
    }

    public static void writeLastJavaPc(Word thread, Word value) {
        thread.writeWord(threadLastJavaPcOffset(), value, LAST_JAVA_PC_LOCATION);
    }

    public static void writeLastJavaFp(Word thread, Word value) {
        thread.writeWord(threadLastJavaFpOffset(), value, LAST_JAVA_FP_LOCATION);
    }

    public static Word readTlabTop(Word thread) {
        return thread.readWord(threadTlabTopOffset(), TLAB_TOP_LOCATION);
    }

    public static Word readTlabEnd(Word thread) {
        return thread.readWord(threadTlabEndOffset(), TLAB_END_LOCATION);
    }

    public static Word readTlabStart(Word thread) {
        return thread.readWord(threadTlabStartOffset(), TLAB_START_LOCATION);
    }

    public static void writeTlabTop(Word thread, Word top) {
        thread.writeWord(threadTlabTopOffset(), top, TLAB_TOP_LOCATION);
    }

    public static void initializeTlab(Word thread, Word start, Word end) {
        thread.writeWord(threadTlabStartOffset(), start, TLAB_START_LOCATION);
        thread.writeWord(threadTlabTopOffset(), start, TLAB_TOP_LOCATION);
        thread.writeWord(threadTlabEndOffset(), end, TLAB_END_LOCATION);
    }

    /**
     * Clears the pending exception for the given thread.
     *
     * @return {@code true} if there was a pending exception
     */
    public static boolean clearPendingException(Word thread) {
        boolean result = thread.readObject(threadPendingExceptionOffset(), PENDING_EXCEPTION_LOCATION) != null;
        thread.writeObject(threadPendingExceptionOffset(), null, PENDING_EXCEPTION_LOCATION);
        return result;
    }

    /**
     * Reads the pending deoptimization value for the given thread.
     *
     * @return {@code true} if there was a pending deoptimization
     */
    public static int readPendingDeoptimization(Word thread) {
        return thread.readInt(threadPendingDeoptimizationOffset(), PENDING_DEOPTIMIZATION_LOCATION);
    }

    /**
     * Writes the pending deoptimization value for the given thread.
     */
    public static void writePendingDeoptimization(Word thread, int value) {
        thread.writeInt(threadPendingDeoptimizationOffset(), value, PENDING_DEOPTIMIZATION_LOCATION);
    }

    /**
     * Gets and clears the object result from a runtime call stored in a thread local.
     *
     * @return the object that was in the thread local
     */
    public static Object getAndClearObjectResult(Word thread) {
        Object result = thread.readObject(objectResultOffset(), OBJECT_RESULT_LOCATION);
        thread.writeObject(objectResultOffset(), null, OBJECT_RESULT_LOCATION);
        return result;
    }

    public static final LocationIdentity JAVA_THREAD_THREAD_OBJECT_LOCATION = NamedLocationIdentity.mutable("JavaThread::_threadObj");

    @Fold
    public static int threadObjectOffset() {
        return config().threadObjectOffset;
    }

    public static final LocationIdentity JAVA_THREAD_OSTHREAD_LOCATION = NamedLocationIdentity.mutable("JavaThread::_osthread");

    @Fold
    public static int osThreadOffset() {
        return config().osThreadOffset;
    }

    @Fold
    public static int osThreadInterruptedOffset() {
        return config().osThreadInterruptedOffset;
    }

    @Fold
    public static Kind getWordKind() {
        return runtime().getTarget().wordKind;
    }

    @Fold
    public static int wordSize() {
        return runtime().getTarget().wordSize;
    }

    @Fold
    public static int pageSize() {
        return unsafe.pageSize();
    }

    @Fold
    public static int heapWordSize() {
        return config().heapWordSize;
    }

    public static final LocationIdentity PROTOTYPE_MARK_WORD_LOCATION = NamedLocationIdentity.mutable("PrototypeMarkWord");

    @Fold
    public static int prototypeMarkWordOffset() {
        return config().prototypeMarkWordOffset;
    }

    @Fold
    public static long arrayPrototypeMarkWord() {
        return config().arrayPrototypeMarkWord();
    }

    public static final LocationIdentity KLASS_ACCESS_FLAGS_LOCATION = NamedLocationIdentity.immutable("Klass::_access_flags");

    @Fold
    public static int klassAccessFlagsOffset() {
        return config().klassAccessFlagsOffset;
    }

    @Fold
    public static int jvmAccWrittenFlags() {
        return config().jvmAccWrittenFlags;
    }

    public static final LocationIdentity KLASS_LAYOUT_HELPER_LOCATION = NamedLocationIdentity.immutable("Klass::_layout_helper");

    @Fold
    public static int klassLayoutHelperOffset() {
        return config().klassLayoutHelperOffset;
    }

    public static int readLayoutHelper(KlassPointer hub) {
        // return hub.readInt(klassLayoutHelperOffset(), KLASS_LAYOUT_HELPER_LOCATION);
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        return loadKlassLayoutHelperIntrinsic(hub, anchorNode);
    }

    @NodeIntrinsic(value = KlassLayoutHelperNode.class)
    public static native int loadKlassLayoutHelperIntrinsic(KlassPointer object, GuardingNode anchor);

    @NodeIntrinsic(value = KlassLayoutHelperNode.class)
    public static native int loadKlassLayoutHelperIntrinsic(KlassPointer object);

    /**
     * Checks if class {@code klass} is an array.
     *
     * See: Klass::layout_helper_is_array
     *
     * @param klass the class to be checked
     * @return true if klass is an array, false otherwise
     */
    public static boolean klassIsArray(KlassPointer klass) {
        /*
         * The less-than check only works if both values are ints. We use local variables to make
         * sure these are still ints and haven't changed.
         */
        final int layoutHelper = readLayoutHelper(klass);
        final int layoutHelperNeutralValue = config().klassLayoutHelperNeutralValue;
        return (layoutHelper < layoutHelperNeutralValue);
    }

    public static final LocationIdentity ARRAY_KLASS_COMPONENT_MIRROR = NamedLocationIdentity.immutable("ArrayKlass::_component_mirror");

    @Fold
    public static int arrayKlassComponentMirrorOffset() {
        return config().arrayKlassComponentMirrorOffset;
    }

    public static final LocationIdentity KLASS_SUPER_KLASS_LOCATION = NamedLocationIdentity.immutable("Klass::_super");

    @Fold
    public static int klassSuperKlassOffset() {
        return config().klassSuperKlassOffset;
    }

    public static final LocationIdentity MARK_WORD_LOCATION = NamedLocationIdentity.mutable("MarkWord");

    @Fold
    public static int markOffset() {
        return config().markOffset;
    }

    public static final LocationIdentity HUB_WRITE_LOCATION = NamedLocationIdentity.mutable("Hub:write");

    public static final LocationIdentity HUB_LOCATION = NamedLocationIdentity.immutable("Hub");

    @Fold
    private static int hubOffset() {
        return config().hubOffset;
    }

    public static void initializeObjectHeader(Word memory, Word markWord, KlassPointer hub) {
        memory.writeWord(markOffset(), markWord, MARK_WORD_LOCATION);
        StoreHubNode.write(memory, hub);
    }

    @Fold
    public static int unlockedMask() {
        return config().unlockedMask;
    }

    /**
     * Mask for a biasable, locked or unlocked mark word.
     *
     * <pre>
     * +----------------------------------+-+-+
     * |                                 1|1|1|
     * +----------------------------------+-+-+
     * </pre>
     *
     */
    @Fold
    public static int biasedLockMaskInPlace() {
        return config().biasedLockMaskInPlace;
    }

    @Fold
    public static int epochMaskInPlace() {
        return config().epochMaskInPlace;
    }

    /**
     * Pattern for a biasable, unlocked mark word.
     *
     * <pre>
     * +----------------------------------+-+-+
     * |                                 1|0|1|
     * +----------------------------------+-+-+
     * </pre>
     *
     */
    @Fold
    public static int biasedLockPattern() {
        return config().biasedLockPattern;
    }

    @Fold
    public static int ageMaskInPlace() {
        return config().ageMaskInPlace;
    }

    @Fold
    public static int metaspaceArrayLengthOffset() {
        return config().metaspaceArrayLengthOffset;
    }

    @Fold
    public static int metaspaceArrayBaseOffset() {
        return config().metaspaceArrayBaseOffset;
    }

    @Fold
    public static int arrayLengthOffset() {
        return config().arrayLengthOffset;
    }

    @Fold
    public static int arrayBaseOffset(Kind elementKind) {
        return runtime().getJVMCIRuntime().getArrayBaseOffset(elementKind);
    }

    @Fold
    public static int arrayIndexScale(Kind elementKind) {
        return runtime().getJVMCIRuntime().getArrayIndexScale(elementKind);
    }

    @Fold
    public static int instanceHeaderSize() {
        return config().useCompressedClassPointers ? (2 * wordSize()) - 4 : 2 * wordSize();
    }

    @Fold
    public static byte dirtyCardValue() {
        return config().dirtyCardValue;
    }

    @Fold
    public static byte g1YoungCardValue() {
        return config().g1YoungCardValue;
    }

    @Fold
    public static int cardTableShift() {
        return config().cardtableShift();
    }

    @Fold
    public static long cardTableStart() {
        return config().cardtableStartAddress();
    }

    @Fold
    public static int g1CardQueueIndexOffset() {
        return config().g1CardQueueIndexOffset();
    }

    @Fold
    public static int g1CardQueueBufferOffset() {
        return config().g1CardQueueBufferOffset();
    }

    @Fold
    public static int logOfHeapRegionGrainBytes() {
        return config().logOfHRGrainBytes;
    }

    @Fold
    public static int g1SATBQueueMarkingOffset() {
        return config().g1SATBQueueMarkingOffset();
    }

    @Fold
    public static int g1SATBQueueIndexOffset() {
        return config().g1SATBQueueIndexOffset();
    }

    @Fold
    public static int g1SATBQueueBufferOffset() {
        return config().g1SATBQueueBufferOffset();
    }

    public static final LocationIdentity KLASS_SUPER_CHECK_OFFSET_LOCATION = NamedLocationIdentity.immutable("Klass::_super_check_offset");

    @Fold
    public static int superCheckOffsetOffset() {
        return config().superCheckOffsetOffset;
    }

    public static final LocationIdentity SECONDARY_SUPER_CACHE_LOCATION = NamedLocationIdentity.mutable("SecondarySuperCache");

    @Fold
    public static int secondarySuperCacheOffset() {
        return config().secondarySuperCacheOffset;
    }

    public static final LocationIdentity SECONDARY_SUPERS_LOCATION = NamedLocationIdentity.immutable("SecondarySupers");

    @Fold
    public static int secondarySupersOffset() {
        return config().secondarySupersOffset;
    }

    public static final LocationIdentity DISPLACED_MARK_WORD_LOCATION = NamedLocationIdentity.mutable("DisplacedMarkWord");

    @Fold
    public static int lockDisplacedMarkOffset() {
        return config().basicLockDisplacedHeaderOffset;
    }

    @Fold
    public static boolean useBiasedLocking() {
        return config().useBiasedLocking;
    }

    @Fold
    public static boolean useDeferredInitBarriers() {
        return config().useDeferredInitBarriers;
    }

    @Fold
    public static boolean useG1GC() {
        return config().useG1GC;
    }

    @Fold
    public static boolean useCompressedOops() {
        return config().useCompressedOops;
    }

    @Fold
    static int uninitializedIdentityHashCodeValue() {
        return config().uninitializedIdentityHashCodeValue;
    }

    @Fold
    static int identityHashCodeShift() {
        return config().identityHashCodeShift;
    }

    /**
     * Loads the hub of an object (without null checking it first).
     */
    public static KlassPointer loadHub(Object object) {
        return loadHubIntrinsic(object);
    }

    public static Object verifyOop(Object object) {
        if (verifyOops()) {
            verifyOopStub(VERIFY_OOP, object);
        }
        return object;
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native Object verifyOopStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    public static Word loadWordFromObject(Object object, int offset) {
        ReplacementsUtil.staticAssert(offset != hubOffset(), "Use loadHubIntrinsic instead of loadWordFromObject");
        return loadWordFromObjectIntrinsic(object, offset, getWordKind(), LocationIdentity.any());
    }

    public static Word loadWordFromObject(Object object, int offset, LocationIdentity identity) {
        ReplacementsUtil.staticAssert(offset != hubOffset(), "Use loadHubIntrinsic instead of loadWordFromObject");
        return loadWordFromObjectIntrinsic(object, offset, getWordKind(), identity);
    }

    /**
     * Reads the value of a given register.
     *
     * @param register a register which must not be available to the register allocator
     * @return the value of {@code register} as a word
     */
    public static Word registerAsWord(@ConstantNodeParameter Register register) {
        return registerAsWord(register, true, false);
    }

    @NodeIntrinsic(value = ReadRegisterNode.class, setStampFromReturnType = true)
    public static native Word registerAsWord(@ConstantNodeParameter Register register, @ConstantNodeParameter boolean directUse, @ConstantNodeParameter boolean incoming);

    @NodeIntrinsic(value = WriteRegisterNode.class, setStampFromReturnType = true)
    public static native void writeRegisterAsWord(@ConstantNodeParameter Register register, Word value);

    @NodeIntrinsic(value = UnsafeLoadNode.class, setStampFromReturnType = true)
    private static native Word loadWordFromObjectIntrinsic(Object object, long offset, @ConstantNodeParameter Kind wordKind, @ConstantNodeParameter LocationIdentity locationIdentity);

    @NodeIntrinsic(value = LoadHubNode.class)
    public static native KlassPointer loadHubIntrinsic(Object object, GuardingNode anchor);

    @NodeIntrinsic(value = LoadHubNode.class)
    public static native KlassPointer loadHubIntrinsic(Object object);

    @Fold
    public static int log2WordSize() {
        return CodeUtil.log2(wordSize());
    }

    public static final LocationIdentity CLASS_STATE_LOCATION = NamedLocationIdentity.mutable("ClassState");

    @Fold
    public static int instanceKlassInitStateOffset() {
        return config().instanceKlassInitStateOffset;
    }

    @Fold
    public static int instanceKlassStateFullyInitialized() {
        return config().instanceKlassStateFullyInitialized;
    }

    /**
     *
     * @param hub the hub of an InstanceKlass
     * @return true is the InstanceKlass represented by hub is fully initialized
     */
    public static boolean isInstanceKlassFullyInitialized(KlassPointer hub) {
        return readInstanceKlassState(hub) == instanceKlassStateFullyInitialized();
    }

    private static byte readInstanceKlassState(KlassPointer hub) {
        return hub.readByte(instanceKlassInitStateOffset(), CLASS_STATE_LOCATION);
    }

    public static final LocationIdentity KLASS_MODIFIER_FLAGS_LOCATION = NamedLocationIdentity.immutable("Klass::_modifier_flags");

    @Fold
    public static int klassModifierFlagsOffset() {
        return config().klassModifierFlagsOffset;
    }

    public static final LocationIdentity CLASS_KLASS_LOCATION = NamedLocationIdentity.immutable("Class._klass");

    @Fold
    public static int klassOffset() {
        return config().klassOffset;
    }

    public static final LocationIdentity CLASS_ARRAY_KLASS_LOCATION = NamedLocationIdentity.mutable("Class._array_klass");

    @Fold
    public static int arrayKlassOffset() {
        return config().arrayKlassOffset;
    }

    public static final LocationIdentity CLASS_MIRROR_LOCATION = NamedLocationIdentity.immutable("Klass::_java_mirror");

    @Fold
    public static int classMirrorOffset() {
        return config().classMirrorOffset;
    }

    public static final LocationIdentity HEAP_TOP_LOCATION = NamedLocationIdentity.mutable("HeapTop");

    @Fold
    public static long heapTopAddress() {
        return config().heapTopAddress;
    }

    public static final LocationIdentity HEAP_END_LOCATION = NamedLocationIdentity.mutable("HeapEnd");

    @Fold
    public static long heapEndAddress() {
        return config().heapEndAddress;
    }

    @Fold
    public static long tlabIntArrayMarkWord() {
        return config().tlabIntArrayMarkWord();
    }

    @Fold
    public static boolean inlineContiguousAllocationSupported() {
        return config().inlineContiguousAllocationSupported;
    }

    @Fold
    public static int tlabAlignmentReserveInHeapWords() {
        return config().tlabAlignmentReserve;
    }

    public static final LocationIdentity TLAB_SIZE_LOCATION = NamedLocationIdentity.mutable("TlabSize");

    @Fold
    public static int threadTlabSizeOffset() {
        return config().threadTlabSizeOffset();
    }

    public static final LocationIdentity TLAB_THREAD_ALLOCATED_BYTES_LOCATION = NamedLocationIdentity.mutable("TlabThreadAllocatedBytes");

    @Fold
    public static int threadAllocatedBytesOffset() {
        return config().threadAllocatedBytesOffset;
    }

    public static final LocationIdentity TLAB_REFILL_WASTE_LIMIT_LOCATION = NamedLocationIdentity.mutable("RefillWasteLimit");

    @Fold
    public static int tlabRefillWasteLimitOffset() {
        return config().tlabRefillWasteLimitOffset();
    }

    public static final LocationIdentity TLAB_NOF_REFILLS_LOCATION = NamedLocationIdentity.mutable("TlabNOfRefills");

    @Fold
    public static int tlabNumberOfRefillsOffset() {
        return config().tlabNumberOfRefillsOffset();
    }

    public static final LocationIdentity TLAB_FAST_REFILL_WASTE_LOCATION = NamedLocationIdentity.mutable("TlabFastRefillWaste");

    @Fold
    public static int tlabFastRefillWasteOffset() {
        return config().tlabFastRefillWasteOffset();
    }

    public static final LocationIdentity TLAB_SLOW_ALLOCATIONS_LOCATION = NamedLocationIdentity.mutable("TlabSlowAllocations");

    @Fold
    public static int tlabSlowAllocationsOffset() {
        return config().tlabSlowAllocationsOffset();
    }

    @Fold
    public static int tlabRefillWasteIncrement() {
        return config().tlabRefillWasteIncrement;
    }

    @Fold
    public static boolean tlabStats() {
        return config().tlabStats;
    }

    @Fold
    public static int layoutHelperHeaderSizeShift() {
        return config().layoutHelperHeaderSizeShift;
    }

    @Fold
    public static int layoutHelperHeaderSizeMask() {
        return config().layoutHelperHeaderSizeMask;
    }

    @Fold
    public static int layoutHelperLog2ElementSizeShift() {
        return config().layoutHelperLog2ElementSizeShift;
    }

    @Fold
    public static int layoutHelperLog2ElementSizeMask() {
        return config().layoutHelperLog2ElementSizeMask;
    }

    @Fold
    public static int layoutHelperElementTypeShift() {
        return config().layoutHelperElementTypeShift;
    }

    @Fold
    public static int layoutHelperElementTypeMask() {
        return config().layoutHelperElementTypeMask;
    }

    @Fold
    public static int layoutHelperElementTypePrimitiveInPlace() {
        return config().layoutHelperElementTypePrimitiveInPlace();
    }

    static int computeHashCode(Object x) {
        Word mark = loadWordFromObject(x, markOffset());

        // this code is independent from biased locking (although it does not look that way)
        final Word biasedLock = mark.and(biasedLockMaskInPlace());
        if (probability(FAST_PATH_PROBABILITY, biasedLock.equal(Word.unsigned(unlockedMask())))) {
            int hash = (int) mark.unsignedShiftRight(identityHashCodeShift()).rawValue();
            if (probability(FAST_PATH_PROBABILITY, hash != uninitializedIdentityHashCodeValue())) {
                return hash;
            }
        }

        return identityHashCode(IDENTITY_HASHCODE, x);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native int identityHashCode(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    @Fold
    public static boolean isImmutableCode() {
        return GraalOptions.ImmutableCode.getValue();
    }

    @Fold
    public static boolean generatePIC() {
        return GraalOptions.GeneratePIC.getValue();
    }

    @Fold
    public static int verifiedEntryPointOffset() {
        return config().nmethodEntryOffset;
    }

    @Fold
    public static long gcTotalCollectionsAddress() {
        return config().gcTotalCollectionsAddress();
    }

    @Fold
    public static long referentOffset() {
        try {
            return unsafe.objectFieldOffset(java.lang.ref.Reference.class.getDeclaredField("referent"));
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }

    public static final LocationIdentity OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION = NamedLocationIdentity.immutable("ObjArrayKlass::_element_klass");

    @Fold
    public static int arrayClassElementOffset() {
        return config().arrayClassElementOffset;
    }

    public static final LocationIdentity PRIMARY_SUPERS_LOCATION = NamedLocationIdentity.immutable("PrimarySupers");

    public static final LocationIdentity METASPACE_ARRAY_LENGTH_LOCATION = NamedLocationIdentity.immutable("MetaspaceArrayLength");

    public static final LocationIdentity SECONDARY_SUPERS_ELEMENT_LOCATION = NamedLocationIdentity.immutable("SecondarySupersElement");
}
