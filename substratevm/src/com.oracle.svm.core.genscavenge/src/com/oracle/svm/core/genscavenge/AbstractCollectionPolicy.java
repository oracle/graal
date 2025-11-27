/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

/**
 * Note that a lot of methods in this class are final. Subclasses may only override certain methods
 * to avoid inconsistencies between the different heap size values.
 */
abstract class AbstractCollectionPolicy implements CollectionPolicy {
    private static final VMMutex MUTEX = new VMMutex("AbstractCollectionPolicy.sizeParams");

    protected static final int MIN_SPACE_SIZE_AS_NUMBER_OF_ALIGNED_CHUNKS = 8;

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/hotspot/share/gc/shared/gc_globals.hpp#L572-L575") //
    protected static final int MAX_TENURING_THRESHOLD = 15;

    @Platforms(Platform.HOSTED_ONLY.class)
    static int getMaxSurvivorSpaces(Integer userValue) {
        assert userValue == null || userValue >= 0 : userValue;
        return (userValue != null) ? userValue : AbstractCollectionPolicy.MAX_TENURING_THRESHOLD;
    }

    /*
     * Constants that can be made options if desirable. These are -XX options in HotSpot, refer to
     * their descriptions for details. The values are HotSpot defaults unless labeled otherwise.
     *
     * Don't change these values individually without carefully going over their occurrences in
     * HotSpot source code, there are dependencies between them that are not handled in our code.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/hotspot/share/gc/shared/gc_globals.hpp#L413-L415") //
    protected static final int INITIAL_SURVIVOR_RATIO = 8;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/hotspot/share/gc/shared/gc_globals.hpp#L409-L411") //
    protected static final int MIN_SURVIVOR_RATIO = 3;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/hotspot/share/gc/shared/gc_globals.hpp#L340-L342") //
    protected static final int ADAPTIVE_TIME_WEIGHT = 25;

    /* Constants to compute defaults for values which can be set through existing options. */
    protected static final UnsignedWord INITIAL_HEAP_SIZE = Word.unsigned(128 * 1024 * 1024);
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/hotspot/share/gc/shared/gc_globals.hpp#L554-L556") //
    protected static final int NEW_RATIO = 2;

    protected final AdaptiveWeightedAverage avgYoungGenAlignedChunkFraction = new AdaptiveWeightedAverage(ADAPTIVE_TIME_WEIGHT);
    protected final SizeParameters sizes = new SizeParameters();

    private final int initialNewRatio;
    protected int tenuringThreshold;

    protected AbstractCollectionPolicy(int initialNewRatio, int initialTenuringThreshold) {
        this.initialNewRatio = initialNewRatio;
        this.tenuringThreshold = UninterruptibleUtils.Math.clamp(initialTenuringThreshold, 1, HeapParameters.getMaxSurvivorSpaces() + 1);
    }

    @Override
    @Uninterruptible(reason = "Tear-down in progress.")
    public void tearDown() {
        sizes.tearDown();
    }

    @Override
    public boolean shouldCollectOnAllocation() {
        guaranteeSizeParametersInitialized();
        UnsignedWord edenUsed = HeapImpl.getAccounting().getEdenUsedBytes();
        return edenUsed.aboveOrEqual(sizes.getEdenSize());
    }

    @Override
    public boolean shouldCollectOnHint(boolean fullGC) {
        /* Collection hints are not supported. */
        return false;
    }

    @Fold
    static UnsignedWord getAlignment() {
        return HeapParameters.getAlignedHeapChunkSize();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static UnsignedWord alignUp(UnsignedWord size) {
        return UnsignedUtils.roundUp(size, getAlignment());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static UnsignedWord alignDown(UnsignedWord size) {
        return UnsignedUtils.roundDown(size, getAlignment());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isAligned(UnsignedWord size) {
        return UnsignedUtils.isAMultiple(size, getAlignment());
    }

    @Fold
    static UnsignedWord minSpaceSize() {
        return HeapParameters.getAlignedHeapChunkSize().multiply(MIN_SPACE_SIZE_AS_NUMBER_OF_ALIGNED_CHUNKS);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static UnsignedWord minSpaceSize(UnsignedWord size) {
        return UnsignedUtils.max(size, minSpaceSize());
    }

    @Override
    public void ensureSizeParametersInitialized() {
        if (!sizes.isInitialized()) {
            updateSizeParameters();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void guaranteeSizeParametersInitialized() {
        VMError.guarantee(sizes.isInitialized());
    }

    @Override
    public void updateSizeParameters() {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            MUTEX.lock();
            try {
                assert RecurringCallbackSupport.isCallbackUnsupportedOrTimerSuspended() : "recurring callbacks could trigger recursive locking, which isn't supported";

                RawSizeParameters newValuesOnStack = StackValue.get(RawSizeParameters.class);
                computeSizeParameters(newValuesOnStack);

                sizes.update(newValuesOnStack);
            } finally {
                MUTEX.unlock();
            }
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    public final UnsignedWord getInitialEdenSize() {
        return sizes.getInitialEdenSize();
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final UnsignedWord getMaximumEdenSize() {
        return sizes.getMaxEdenSize();
    }

    @Override
    public final UnsignedWord getMaximumHeapSize() {
        return sizes.getMaxHeapSize();
    }

    @Override
    public final UnsignedWord getMaximumYoungGenerationSize() {
        return sizes.getMaxYoungSize();
    }

    @Override
    public final UnsignedWord getInitialSurvivorSize() {
        return sizes.getInitialSurvivorSize();
    }

    @Override
    public final UnsignedWord getMaximumSurvivorSize() {
        return sizes.getMaxSurvivorSize();
    }

    @Override
    public final UnsignedWord getCurrentHeapCapacity() {
        return sizes.getCurrentHeapCapacity();
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final UnsignedWord getSurvivorSpacesCapacity() {
        return sizes.getSurvivorSize();
    }

    @Override
    public final UnsignedWord getYoungGenerationCapacity() {
        return sizes.getYoungSize();
    }

    @Override
    public final UnsignedWord getInitialOldSize() {
        return sizes.getInitialOldSize();
    }

    @Override
    public final UnsignedWord getMaximumOldSize() {
        return sizes.getMaxOldSize();
    }

    @Override
    public final UnsignedWord getOldGenerationCapacity() {
        return sizes.getOldSize();
    }

    @Override
    public UnsignedWord getMaximumFreeAlignedChunksSize() {
        assert VMOperation.isGCInProgress() : "use only during GC";
        guaranteeSizeParametersInitialized();
        /*
         * Keep chunks ready for allocations in eden as well as for copying the objects currently in
         * survivor spaces in a future collection. We could alternatively return
         * getCurrentHeapCapacity() to have chunks ready during full GCs as well.
         */
        UnsignedWord total = sizes.getEdenSize().add(HeapImpl.getAccounting().getSurvivorUsedBytes());
        double alignedFraction = Math.min(1, Math.max(0, avgYoungGenAlignedChunkFraction.getAverage()));
        return UnsignedUtils.fromDouble(UnsignedUtils.toDouble(total) * alignedFraction);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getTenuringAge() {
        assert VMOperation.isGCInProgress() : "use only during GC";
        return tenuringThreshold;
    }

    @Override
    public void onCollectionBegin(boolean completeCollection, long beginNanoTime) {
        sizes.freeUnusedSizeParameters();

        // Capture the fraction of bytes in aligned chunks at the start to include all allocated
        // (also dead) objects, because we use it to reserve aligned chunks for future allocations
        UnsignedWord youngChunkBytes = GCImpl.getAccounting().getYoungChunkBytesBefore();
        if (youngChunkBytes.notEqual(0)) {
            UnsignedWord youngAlignedChunkBytes = HeapImpl.getHeapImpl().getYoungGeneration().getAlignedChunkBytes();
            avgYoungGenAlignedChunkFraction.sample(UnsignedUtils.toDouble(youngAlignedChunkBytes) / UnsignedUtils.toDouble(youngChunkBytes));
        }
    }

    @Override
    public final UnsignedWord getMinimumHeapSize() {
        return sizes.getMinHeapSize();
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/hotspot/share/gc/shared/genArguments.cpp#L195-L310")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/hotspot/share/gc/parallel/psYoungGen.cpp#L104-L116")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/hotspot/share/gc/parallel/psYoungGen.cpp#L146-L168")
    private void computeSizeParameters(RawSizeParameters newParams) {
        UnsignedWord minYoungSpaces = minSpaceSize(); // eden
        if (HeapParameters.getMaxSurvivorSpaces() > 0) {
            minYoungSpaces = minYoungSpaces.add(minSpaceSize().multiply(2)); // survivor from and to
        }
        UnsignedWord minAllSpaces = minYoungSpaces.add(minSpaceSize()); // old
        UnsignedWord heapSizeLimit = UnsignedUtils.max(alignDown(getHeapSizeLimit()), minAllSpaces);

        UnsignedWord maxHeap;
        long optionMax = getMaximumHeapSizeOptionValue();
        if (optionMax > 0L) {
            maxHeap = Word.unsigned(optionMax);
        } else {
            maxHeap = PhysicalMemory.size().unsignedDivide(100).multiply(HeapParameters.getMaximumHeapSizePercent());
        }
        UnsignedWord unadjustedMaxHeap = maxHeap;
        maxHeap = UnsignedUtils.clamp(alignDown(maxHeap), minAllSpaces, heapSizeLimit);

        UnsignedWord maxYoung;
        long optionMaxYoung = getMaxNewSizeOptionValue();
        if (optionMaxYoung > 0L) {
            maxYoung = Word.unsigned(optionMaxYoung);
        } else if (SerialAndEpsilonGCOptions.MaximumYoungGenerationSizePercent.hasBeenSet()) {
            maxYoung = maxHeap.unsignedDivide(100).multiply(HeapParameters.getMaximumYoungGenerationSizePercent());
        } else {
            maxYoung = maxHeap.unsignedDivide(AbstractCollectionPolicy.NEW_RATIO + 1);
        }
        maxYoung = UnsignedUtils.clamp(alignDown(maxYoung), minYoungSpaces, getYoungSizeLimit(maxHeap));

        UnsignedWord maxOld = alignDown(maxHeap.subtract(maxYoung));
        maxHeap = maxYoung.add(maxOld);
        VMError.guarantee(maxOld.aboveOrEqual(minSpaceSize()) && maxHeap.belowOrEqual(heapSizeLimit) &&
                        (maxHeap.belowOrEqual(unadjustedMaxHeap) || unadjustedMaxHeap.belowThan(minAllSpaces)));

        UnsignedWord minHeap = Word.zero();
        long optionMin = getMinHeapSizeOptionValue();
        if (optionMin > 0L) {
            minHeap = Word.unsigned(optionMin);
        }
        minHeap = UnsignedUtils.clamp(alignUp(minHeap), minAllSpaces, maxHeap);

        UnsignedWord initialHeap = getInitialHeapSize();
        initialHeap = UnsignedUtils.clamp(alignUp(initialHeap), minHeap, maxHeap);

        UnsignedWord initialYoung;
        if (initialHeap.equal(maxHeap)) {
            initialYoung = maxYoung;
        } else {
            initialYoung = initialHeap.unsignedDivide(initialNewRatio + 1);
            initialYoung = UnsignedUtils.clamp(alignUp(initialYoung), minYoungSpaces, maxYoung);
        }

        UnsignedWord initialSurvivor = Word.zero();
        if (HeapParameters.getMaxSurvivorSpaces() > 0) {
            /*
             * In HotSpot, this is the reserved capacity of each of the survivor From and To spaces,
             * i.e., together they occupy 2x this size. Our chunked heap doesn't reserve memory, so
             * we never occupy more than 1x this size for survivors except during collections. We
             * reserve 2x regardless (see below). However, this is inconsistent with how we
             * interpret the maximum size of the old generation, which we can exceed the same way
             * while copying during collections, but reserve only 1x its size.
             */
            initialSurvivor = initialYoung.unsignedDivide(AbstractCollectionPolicy.INITIAL_SURVIVOR_RATIO);
            initialSurvivor = minSpaceSize(alignDown(initialSurvivor));
        }

        UnsignedWord initialEden = initialYoung.subtract(initialSurvivor.multiply(2));
        initialEden = minSpaceSize(alignDown(initialEden));

        UnsignedWord maxSurvivorSize = Word.zero();
        UnsignedWord maxEdenSize = maxYoung;
        if (HeapParameters.getMaxSurvivorSpaces() > 0) {
            maxSurvivorSize = maxYoung.unsignedDivide(MIN_SURVIVOR_RATIO);
            maxSurvivorSize = minSpaceSize(alignDown(maxSurvivorSize));
            maxEdenSize = maxYoung.subtract(minSpaceSize().multiply(2));
        }

        UnsignedWord maxOldSize = maxHeap.subtract(maxYoung);

        UnsignedWord survivorSize;
        UnsignedWord edenSize;
        UnsignedWord oldSize;
        UnsignedWord promoSize;
        if (sizes.isInitialized()) {
            /* Copy and limit existing values. */
            survivorSize = UnsignedUtils.min(sizes.getSurvivorSize(), maxSurvivorSize);
            edenSize = UnsignedUtils.min(sizes.getEdenSize(), maxEdenSize);
            oldSize = UnsignedUtils.min(sizes.getOldSize(), maxOldSize);
            promoSize = UnsignedUtils.min(sizes.getPromoSize(), maxOldSize);
        } else {
            /* Set initial values. */
            survivorSize = initialSurvivor;
            edenSize = initialEden;
            oldSize = initialHeap.subtract(initialYoung);
            promoSize = UnsignedUtils.min(edenSize, oldSize);
        }

        UnsignedWord initialOldSize = initialHeap.subtract(initialYoung);
        UnsignedWord youngSize = edenSize.add(survivorSize);
        UnsignedWord heapSize = edenSize.add(survivorSize).add(oldSize);

        RawSizeParametersAccess.initialize(newParams,
                        initialEden, edenSize, maxEdenSize,
                        initialSurvivor, survivorSize, maxSurvivorSize,
                        initialOldSize, oldSize, maxOldSize,
                        promoSize,
                        initialYoung, youngSize, maxYoung,
                        minHeap, initialHeap, heapSize, maxHeap);
    }

    protected UnsignedWord getHeapSizeLimit() {
        return CommittedMemoryProvider.get().getCollectedHeapAddressSpaceSize();
    }

    protected UnsignedWord getYoungSizeLimit(UnsignedWord maxHeap) {
        return maxHeap.subtract(minSpaceSize());
    }

    protected long getMaximumHeapSizeOptionValue() {
        int optionIndex = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.MaxHeapSize);
        return IsolateArgumentParser.singleton().getLongOptionValue(optionIndex);
    }

    protected static long getMaxNewSizeOptionValue() {
        int optionIndex = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.MaxNewSize);
        return IsolateArgumentParser.singleton().getLongOptionValue(optionIndex);
    }

    protected static long getMinHeapSizeOptionValue() {
        int optionIndex = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.MinHeapSize);
        return IsolateArgumentParser.singleton().getLongOptionValue(optionIndex);
    }

    protected UnsignedWord getInitialHeapSize() {
        return AbstractCollectionPolicy.INITIAL_HEAP_SIZE;
    }

    /**
     * Once a {@link RawSizeParameters} struct is visible to other threads (see {@link #update}), it
     * may only be accessed by {@link Uninterruptible} code or when the VM is at a safepoint. This
     * is necessary to prevent use-after-free errors because no longer needed
     * {@link RawSizeParameters} structs may be freed during a GC.
     * <p>
     * When the VM is at a safepoint, the GC may directly update some of the values in the latest
     * {@link RawSizeParameters} (see setters below).
     */
    protected static final class SizeParameters {
        private static final String ACCESS_RAW_SIZE_PARAMETERS = "Prevent that RawSizeParameters are freed.";

        private volatile RawSizeParameters sizes;

        @Platforms(Platform.HOSTED_ONLY.class)
        private SizeParameters() {
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public boolean isInitialized() {
            /*
             * This only checks for non-null and doesn't access any values in the struct, so
             * inlining into interruptible code is allowed.
             */
            return sizes.isNonNull();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getInitialEdenSize() {
            assert isInitialized();
            return sizes.getInitialEdenSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getEdenSize() {
            assert isInitialized();
            return sizes.getEdenSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public void setEdenSize(UnsignedWord value) {
            assert isInitialized();
            assert VMOperation.isGCInProgress();

            sizes.setEdenSize(value);
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getMaxEdenSize() {
            assert isInitialized();
            return sizes.getMaxEdenSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getInitialSurvivorSize() {
            assert isInitialized();
            return sizes.getInitialSurvivorSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getSurvivorSize() {
            assert isInitialized();
            return sizes.getSurvivorSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public void setSurvivorSize(UnsignedWord value) {
            assert isInitialized();
            assert VMOperation.isGCInProgress();

            sizes.setSurvivorSize(value);
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/hotspot/share/gc/parallel/psYoungGen.cpp#L104-L116")
        UnsignedWord getMaxSurvivorSize() {
            assert isInitialized();
            return sizes.getMaxSurvivorSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        UnsignedWord getInitialYoungSize() {
            assert isInitialized();
            return sizes.getInitialYoungSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getYoungSize() {
            assert isInitialized();
            return sizes.getYoungSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getMaxYoungSize() {
            assert isInitialized();
            return sizes.getMaxYoungSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        UnsignedWord getInitialOldSize() {
            assert isInitialized();
            return sizes.getInitialOldSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getOldSize() {
            assert isInitialized();
            return sizes.getOldSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public void setOldSize(UnsignedWord value) {
            assert isInitialized();
            assert VMOperation.isGCInProgress();

            sizes.setOldSize(value);
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        UnsignedWord getMaxOldSize() {
            assert isInitialized();
            return sizes.getMaxOldSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getPromoSize() {
            assert isInitialized();
            return sizes.getPromoSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public void setPromoSize(UnsignedWord value) {
            assert isInitialized();
            assert VMOperation.isGCInProgress();

            sizes.setPromoSize(value);
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getMinHeapSize() {
            assert isInitialized();
            return sizes.getMinHeapSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getMaxHeapSize() {
            assert isInitialized();
            return sizes.getMaxHeapSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        public UnsignedWord getCurrentHeapCapacity() {
            assert isInitialized();
            assert VMOperation.isGCInProgress() : "use only during GC";

            return sizes.getHeapSize();
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        private void update(RawSizeParameters newValuesOnStack) {
            RawSizeParameters prevValues = sizes;
            if (prevValues.isNonNull() && matches(prevValues, newValuesOnStack)) {
                /* Nothing to do - cached params are still accurate. */
                return;
            }

            /* Try allocating a struct on the C heap. */
            UnsignedWord structSize = SizeOf.unsigned(RawSizeParameters.class);
            RawSizeParameters newValuesOnHeap = NullableNativeMemory.malloc(structSize, NmtCategory.GC);
            VMError.guarantee(newValuesOnHeap.isNonNull(), "Out-of-memory while updating GC policy sizes.");

            /* Copy the values from the stack to the C heap. */
            UnmanagedMemoryUtil.copyForward((Pointer) newValuesOnStack, (Pointer) newValuesOnHeap, structSize);
            newValuesOnHeap.setNext(prevValues);

            /*
             * Publish the new struct via a volatile store. Once the data is published, other
             * threads need to see a fully initialized struct right away. This is guaranteed by the
             * implicit STORE_STORE barrier before the volatile write.
             */
            sizes = newValuesOnHeap;

            assert isAligned(getMaxSurvivorSize()) && isAligned(getInitialYoungSize()) && isAligned(getInitialOldSize()) && isAligned(getMaxOldSize());
            assert getMaxSurvivorSize().belowThan(getMaxYoungSize());
            assert getMaxYoungSize().add(getMaxOldSize()).equal(getMaxHeapSize());
            assert getInitialEdenSize().add(getInitialSurvivorSize().multiply(2)).equal(getInitialYoungSize());
            assert getInitialYoungSize().add(getInitialOldSize()).equal(sizes.getInitialHeapSize());
        }

        /**
         * Frees no longer needed {@link RawSizeParameters}, so that only the most recent one
         * remains.
         */
        public void freeUnusedSizeParameters() {
            assert isInitialized();
            assert VMOperation.isGCInProgress() : "would need to be uninterruptible otherwise";

            RawSizeParameters cur = sizes;
            freeSizeParameters(cur.getNext());
            cur.setNext(Word.nullPointer());
        }

        @Uninterruptible(reason = "Tear-down in progress.")
        public void tearDown() {
            assert VMThreads.isTearingDown();

            freeSizeParameters(sizes);
            sizes = Word.nullPointer();
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static void freeSizeParameters(RawSizeParameters first) {
            assert VMOperation.isGCInProgress() || VMThreads.isTearingDown();

            RawSizeParameters cur = first;
            while (cur.isNonNull()) {
                RawSizeParameters next = cur.getNext();
                NullableNativeMemory.free(cur);
                cur = next;
            }
        }

        @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
        private static boolean matches(RawSizeParameters a, RawSizeParameters b) {
            return a.getInitialEdenSize() == b.getInitialEdenSize() &&
                            a.getEdenSize() == b.getEdenSize() &&
                            a.getMaxEdenSize() == b.getMaxEdenSize() &&

                            a.getInitialSurvivorSize() == b.getInitialSurvivorSize() &&
                            a.getSurvivorSize() == b.getSurvivorSize() &&
                            a.getMaxSurvivorSize() == b.getMaxSurvivorSize() &&

                            a.getInitialYoungSize() == b.getInitialYoungSize() &&
                            a.getYoungSize() == b.getYoungSize() &&
                            a.getMaxYoungSize() == b.getMaxYoungSize() &&

                            a.getInitialOldSize() == b.getInitialOldSize() &&
                            a.getOldSize() == b.getOldSize() &&
                            a.getMaxOldSize() == b.getMaxOldSize() &&

                            a.getPromoSize() == b.getPromoSize() &&

                            a.getMinHeapSize() == b.getMinHeapSize() &&
                            a.getInitialHeapSize() == b.getInitialHeapSize() &&
                            a.getHeapSize() == b.getHeapSize() &&
                            a.getMaxHeapSize() == b.getMaxHeapSize();
        }
    }

    /**
     * Struct that stores all GC-related sizes. See {@link SizeParameters} for more details on its
     * lifecycle.
     */
    @RawStructure
    private interface RawSizeParameters extends PointerBase {
        @RawField
        UnsignedWord getInitialEdenSize();

        @RawField
        void setInitialEdenSize(UnsignedWord value);

        @RawField
        UnsignedWord getEdenSize();

        @RawField
        void setEdenSize(UnsignedWord value);

        @RawField
        UnsignedWord getMaxEdenSize();

        @RawField
        void setMaxEdenSize(UnsignedWord value);

        @RawField
        UnsignedWord getInitialSurvivorSize();

        @RawField
        void setInitialSurvivorSize(UnsignedWord value);

        @RawField
        UnsignedWord getSurvivorSize();

        @RawField
        void setSurvivorSize(UnsignedWord value);

        @RawField
        UnsignedWord getMaxSurvivorSize();

        @RawField
        void setMaxSurvivorSize(UnsignedWord value);

        @RawField
        UnsignedWord getInitialYoungSize();

        @RawField
        void setInitialYoungSize(UnsignedWord value);

        @RawField
        UnsignedWord getYoungSize();

        @RawField
        void setYoungSize(UnsignedWord value);

        @RawField
        UnsignedWord getMaxYoungSize();

        @RawField
        void setMaxYoungSize(UnsignedWord value);

        @RawField
        UnsignedWord getInitialOldSize();

        @RawField
        void setInitialOldSize(UnsignedWord value);

        @RawField
        UnsignedWord getOldSize();

        @RawField
        void setOldSize(UnsignedWord value);

        @RawField
        UnsignedWord getMaxOldSize();

        @RawField
        void setMaxOldSize(UnsignedWord value);

        @RawField
        UnsignedWord getPromoSize();

        @RawField
        void setPromoSize(UnsignedWord value);

        @RawField
        UnsignedWord getMinHeapSize();

        @RawField
        void setMinHeapSize(UnsignedWord value);

        @RawField
        UnsignedWord getInitialHeapSize();

        @RawField
        void setInitialHeapSize(UnsignedWord value);

        @RawField
        UnsignedWord getHeapSize();

        @RawField
        void setHeapSize(UnsignedWord value);

        @RawField
        UnsignedWord getMaxHeapSize();

        @RawField
        void setMaxHeapSize(UnsignedWord value);

        @RawField
        RawSizeParameters getNext();

        @RawField
        void setNext(RawSizeParameters value);
    }

    /**
     * The methods in this class may only be used on {@link RawSizeParameters} that are allocated on
     * the stack and therefore not yet visible to other threads. Please keep the methods in this
     * class to a minimum.
     */
    private static final class RawSizeParametersAccess {
        static void initialize(RawSizeParameters valuesOnStack,
                        UnsignedWord initialEdenSize, UnsignedWord edenSize, UnsignedWord maxEdenSize,
                        UnsignedWord initialSurvivorSize, UnsignedWord survivorSize, UnsignedWord maxSurvivorSize,
                        UnsignedWord initialOldSize, UnsignedWord oldSize, UnsignedWord maxOldSize,
                        UnsignedWord promoSize,
                        UnsignedWord initialYoungSize, UnsignedWord youngSize, UnsignedWord maxYoungSize,
                        UnsignedWord minHeapSize, UnsignedWord initialHeapSize, UnsignedWord heapSize, UnsignedWord maxHeapSize) {
            assert isAligned(maxHeapSize) && isAligned(maxYoungSize) && isAligned(initialHeapSize) && isAligned(initialEdenSize) && isAligned(initialSurvivorSize);

            assert initialEdenSize.belowOrEqual(initialYoungSize);
            assert edenSize.belowOrEqual(youngSize);
            assert maxEdenSize.belowOrEqual(maxYoungSize);

            assert initialSurvivorSize.belowOrEqual(initialYoungSize);
            assert survivorSize.belowOrEqual(youngSize);
            assert maxSurvivorSize.belowOrEqual(maxYoungSize);

            assert initialOldSize.belowOrEqual(initialHeapSize);
            assert oldSize.belowOrEqual(heapSize);
            assert maxOldSize.belowOrEqual(maxHeapSize);

            assert initialYoungSize.belowOrEqual(initialHeapSize);
            assert youngSize.belowOrEqual(heapSize);
            assert maxYoungSize.belowOrEqual(maxHeapSize);

            assert minHeapSize.belowOrEqual(initialHeapSize);
            assert initialHeapSize.belowOrEqual(maxHeapSize);
            assert heapSize.belowOrEqual(maxHeapSize);
            assert maxHeapSize.belowOrEqual(ReferenceAccess.singleton().getMaxAddressSpaceSize());

            valuesOnStack.setInitialEdenSize(initialEdenSize);
            valuesOnStack.setEdenSize(edenSize);
            valuesOnStack.setMaxEdenSize(maxEdenSize);

            valuesOnStack.setInitialSurvivorSize(initialSurvivorSize);
            valuesOnStack.setSurvivorSize(survivorSize);
            valuesOnStack.setMaxSurvivorSize(maxSurvivorSize);

            valuesOnStack.setInitialYoungSize(initialYoungSize);
            valuesOnStack.setYoungSize(youngSize);
            valuesOnStack.setMaxYoungSize(maxYoungSize);

            valuesOnStack.setInitialOldSize(initialOldSize);
            valuesOnStack.setOldSize(oldSize);
            valuesOnStack.setMaxOldSize(maxOldSize);

            valuesOnStack.setPromoSize(promoSize);

            valuesOnStack.setMinHeapSize(minHeapSize);
            valuesOnStack.setInitialHeapSize(initialHeapSize);
            valuesOnStack.setHeapSize(heapSize);
            valuesOnStack.setMaxHeapSize(maxHeapSize);

            valuesOnStack.setNext(Word.nullPointer());
        }
    }
}
