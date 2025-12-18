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
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.core.thread.VMOperation;
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
    private static final VMMutex SIZES_MUTEX = new VMMutex("AbstractCollectionPolicy.sizes");

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

    /**
     * (Re)computes and updates the {@link SizeParameters} used by this garbage collection policy to
     * reflect the latest values derived from relevant heap size options and physical memory
     * constraints.
     * <p>
     * This method is thread-safe and may be invoked concurrently by multiple threads. It is called
     * at least once during VM startup, and is called again if the value of a relevant runtime
     * option changes. A mutex is used to guarantee consistency of the computed parameters and
     * prevent lost updates (note that a CAS would not be sufficient to prevent lost updates because
     * threads could overwrite more recent values with outdated values).
     */
    @Override
    public void updateSizeParameters() {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            SIZES_MUTEX.lock();
            try {
                assert RecurringCallbackSupport.isCallbackUnsupportedOrTimerSuspended() : "recurring callbacks could trigger recursive locking, which isn't supported";

                RawSizeParameters newValuesOnStack = StackValue.get(RawSizeParameters.class);
                computeSizeParameters(newValuesOnStack);

                sizes.update(newValuesOnStack);
            } finally {
                SIZES_MUTEX.unlock();
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
        return sizes.getHeapSize();
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
    private void computeSizeParameters(RawSizeParameters newParamsOnStack) {
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

        RawSizeParametersOnStackAccess.initialize(newParamsOnStack,
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
}
