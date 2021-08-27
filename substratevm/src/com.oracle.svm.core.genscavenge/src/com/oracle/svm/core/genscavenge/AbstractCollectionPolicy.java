/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

abstract class AbstractCollectionPolicy implements CollectionPolicy {

    /*
     * Constants that can be made options if desirable. These are -XX options in HotSpot, refer to
     * their descriptions for details. The values are HotSpot defaults unless labeled otherwise.
     *
     * Don't change these values individually without carefully going over their occurrences in
     * HotSpot source code, there are dependencies between them that are not handled in our code.
     */
    static final int INITIAL_SURVIVOR_RATIO = 8;
    static final int MIN_SURVIVOR_RATIO = 3;

    /* Constants to compute defaults for values which can be set through existing options. */
    /** HotSpot: -XX:MaxHeapSize default without ergonomics. */
    static final UnsignedWord SMALL_HEAP_SIZE = WordFactory.unsigned(96 * 1024 * 1024);
    static final int NEW_RATIO = 2; // HotSpot: -XX:NewRatio
    static final int LARGE_MEMORY_MAX_HEAP_PERCENT = 25; // -XX:MaxRAMPercentage
    static final int SMALL_MEMORY_MAX_HEAP_PERCENT = 50; // -XX:MinRAMPercentage
    static final double INITIAL_HEAP_MEMORY_PERCENT = 1.5625; // -XX:InitialRAMPercentage

    protected final AdaptiveWeightedAverage avgYoungGenAlignedChunkFraction = new AdaptiveWeightedAverage(AdaptiveCollectionPolicy.ADAPTIVE_TIME_WEIGHT);

    protected UnsignedWord survivorSize;
    protected UnsignedWord edenSize;
    protected UnsignedWord promoSize;
    protected UnsignedWord oldSize;
    protected int tenuringThreshold;

    protected volatile SizeParameters sizes;
    private final ReentrantLock sizesUpdateLock = new ReentrantLock();

    protected AbstractCollectionPolicy(int initialTenuringThreshold) {
        tenuringThreshold = UninterruptibleUtils.Math.clamp(initialTenuringThreshold, 1, HeapParameters.getMaxSurvivorSpaces() + 1);
    }

    @Override
    public boolean shouldCollectOnAllocation() {
        if (sizes == null) {
            return false; // updateSizeParameters() has never been called
        }
        UnsignedWord edenUsed = HeapImpl.getHeapImpl().getAccounting().getEdenUsedBytes();
        return edenUsed.aboveOrEqual(edenSize);
    }

    @Fold
    static UnsignedWord minSpaceSize() {
        return HeapParameters.getAlignedHeapChunkSize();
    }

    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    static UnsignedWord alignUp(UnsignedWord size) {
        return UnsignedUtils.roundUp(size, minSpaceSize());
    }

    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    static UnsignedWord alignDown(UnsignedWord size) {
        return UnsignedUtils.roundDown(size, minSpaceSize());
    }

    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    static boolean isAligned(UnsignedWord size) {
        return UnsignedUtils.isAMultiple(size, minSpaceSize());
    }

    @Uninterruptible(reason = "Used in uninterruptible code.", mayBeInlined = true)
    static UnsignedWord minSpaceSize(UnsignedWord size) {
        return UnsignedUtils.max(size, minSpaceSize());
    }

    @Override
    public void ensureSizeParametersInitialized() {
        if (sizes == null) {
            updateSizeParameters();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void guaranteeSizeParametersInitialized() {
        VMError.guarantee(sizes != null);
    }

    @Override
    public void updateSizeParameters() {
        PhysicalMemory.tryInitialize();

        SizeParameters params = computeSizeParameters();
        SizeParameters previous = sizes;
        if (previous != null && params.equal(previous)) {
            return; // nothing to do
        }
        sizesUpdateLock.lock();
        try {
            updateSizeParametersLocked(params, previous);
        } finally {
            sizesUpdateLock.unlock();
        }
        guaranteeSizeParametersInitialized(); // sanity
    }

    @Uninterruptible(reason = "Must be atomic with regard to garbage collection.")
    private void updateSizeParametersLocked(SizeParameters params, SizeParameters previous) {
        if (sizes != previous) {
            // Some other thread beat us and we cannot tell if our values or their values are newer,
            // so back off -- any newer values will be applied eventually.
            return;
        }
        sizes = params;

        if (previous == null || gcCount() == 0) {
            survivorSize = params.initialSurvivorSize;
            edenSize = params.initialEdenSize;
            oldSize = params.initialOldSize();
            promoSize = UnsignedUtils.min(edenSize, oldSize);
        }

        /*
         * NOTE: heap limits can change when options are updated at runtime or once the physical
         * memory size becomes known. This means that we start off with sizes which can cause higher
         * GC costs initially, and when shrinking the heap, that previously computed values such as
         * GC costs and intervals and survived/promoted objects are likely no longer representative.
         *
         * We assume that such changes happen very early on and values then adapt reasonably quick,
         * but we must still ensure that computations can handle it (for example, no overflows).
         */
        survivorSize = UnsignedUtils.min(survivorSize, params.maxSurvivorSize());
        edenSize = UnsignedUtils.min(edenSize, maxEdenSize());
        oldSize = UnsignedUtils.min(oldSize, sizes.maxOldSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected UnsignedWord maxEdenSize() {
        return alignDown(sizes.maxYoungSize.subtract(survivorSize.multiply(2)));
    }

    @Override
    public UnsignedWord getMaximumHeapSize() {
        guaranteeSizeParametersInitialized();
        return sizes.maxHeapSize;
    }

    @Override
    public UnsignedWord getMaximumYoungGenerationSize() {
        guaranteeSizeParametersInitialized();
        return sizes.maxYoungSize;
    }

    @Override
    public UnsignedWord getCurrentHeapCapacity() {
        guaranteeSizeParametersInitialized();
        return edenSize.add(survivorSize.multiply(2)).add(oldSize);
    }

    @Override
    public UnsignedWord getSurvivorSpacesCapacity() {
        guaranteeSizeParametersInitialized();
        return survivorSize;
    }

    @Override
    public UnsignedWord getMaximumFreeAlignedChunksSize() {
        guaranteeSizeParametersInitialized();
        /*
         * Keep chunks ready for allocations in eden and for the survivor to-spaces during young
         * collections (although we might keep too many aligned chunks when large objects in
         * unallocated chunks are also allocated). We could alternatively return
         * getCurrentHeapCapacity() to have chunks ready during full GCs as well.
         */
        UnsignedWord total = edenSize.add(survivorSize);
        float alignedFraction = Math.min(1, Math.max(0, avgYoungGenAlignedChunkFraction.getAverage()));
        return UnsignedUtils.fromDouble(UnsignedUtils.toDouble(total) * alignedFraction);
    }

    @Override
    public int getTenuringAge() {
        return tenuringThreshold;
    }

    @Override
    public UnsignedWord getMinimumHeapSize() {
        return sizes.minHeapSize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract long gcCount();

    protected SizeParameters computeSizeParameters() {
        UnsignedWord addressSpaceSize = ReferenceAccess.singleton().getAddressSpaceSize();
        UnsignedWord minAllSpaces = minSpaceSize().multiply(2); // eden, old
        if (HeapParameters.getMaxSurvivorSpaces() > 0) {
            minAllSpaces = minAllSpaces.add(minSpaceSize().multiply(2)); // survivor from and to
        }

        UnsignedWord maxHeap;
        long optionMax = SubstrateGCOptions.MaxHeapSize.getValue();
        if (optionMax > 0L) {
            maxHeap = WordFactory.unsigned(optionMax);
        } else if (!PhysicalMemory.isInitialized()) {
            maxHeap = addressSpaceSize;
        } else {
            UnsignedWord physicalMemorySize = PhysicalMemory.getCachedSize();
            if (HeapParameters.Options.MaximumHeapSizePercent.hasBeenSet(RuntimeOptionValues.singleton())) {
                maxHeap = physicalMemorySize.unsignedDivide(100).multiply(HeapParameters.getMaximumHeapSizePercent());
            } else {
                UnsignedWord reasonableMax = physicalMemorySize.unsignedDivide(100).multiply(AbstractCollectionPolicy.LARGE_MEMORY_MAX_HEAP_PERCENT);
                UnsignedWord reasonableMin = physicalMemorySize.unsignedDivide(100).multiply(AbstractCollectionPolicy.SMALL_MEMORY_MAX_HEAP_PERCENT);
                if (reasonableMin.belowThan(AbstractCollectionPolicy.SMALL_HEAP_SIZE)) {
                    // small physical memory, use a small fraction for the heap
                    reasonableMax = reasonableMin;
                } else {
                    reasonableMax = UnsignedUtils.max(reasonableMax, AbstractCollectionPolicy.SMALL_HEAP_SIZE);
                }
                maxHeap = reasonableMax;
            }
        }
        maxHeap = UnsignedUtils.clamp(alignDown(maxHeap), minAllSpaces, alignDown(addressSpaceSize));

        UnsignedWord maxYoung;
        long optionMaxYoung = SubstrateGCOptions.MaxNewSize.getValue();
        if (optionMaxYoung > 0L) {
            maxYoung = WordFactory.unsigned(optionMaxYoung);
        } else if (HeapParameters.Options.MaximumYoungGenerationSizePercent.hasBeenSet(RuntimeOptionValues.singleton())) {
            maxYoung = maxHeap.unsignedDivide(100).multiply(HeapParameters.getMaximumYoungGenerationSizePercent());
        } else {
            maxYoung = maxHeap.unsignedDivide(AbstractCollectionPolicy.NEW_RATIO + 1);
        }
        maxYoung = UnsignedUtils.clamp(alignUp(maxYoung), minSpaceSize(), maxHeap);

        UnsignedWord maxOld = maxHeap.subtract(maxYoung);
        maxOld = minSpaceSize(alignUp(maxOld));
        maxHeap = maxYoung.add(maxOld);
        if (maxHeap.aboveThan(addressSpaceSize)) {
            maxYoung = alignDown(maxYoung.subtract(minSpaceSize()));
            maxHeap = maxYoung.add(maxOld);
            VMError.guarantee(maxHeap.belowOrEqual(addressSpaceSize) && maxYoung.aboveOrEqual(minSpaceSize()));
        }

        UnsignedWord minHeap = WordFactory.zero();
        long optionMin = SubstrateGCOptions.MinHeapSize.getValue();
        if (optionMin > 0L) {
            minHeap = WordFactory.unsigned(optionMin);
        }
        minHeap = UnsignedUtils.clamp(alignUp(minHeap), minAllSpaces, maxHeap);

        UnsignedWord initialHeap;
        if (PhysicalMemory.isInitialized()) {
            initialHeap = UnsignedUtils.fromDouble(UnsignedUtils.toDouble(PhysicalMemory.getCachedSize()) / 100 * AbstractCollectionPolicy.INITIAL_HEAP_MEMORY_PERCENT);
        } else {
            initialHeap = AbstractCollectionPolicy.SMALL_HEAP_SIZE;
        }
        initialHeap = UnsignedUtils.clamp(alignUp(initialHeap), minHeap, maxHeap);

        UnsignedWord initialYoung;
        if (initialHeap.equal(maxHeap)) {
            initialYoung = maxYoung;
        } else {
            initialYoung = UnsignedUtils.clamp(alignUp(initialHeap.unsignedDivide(AbstractCollectionPolicy.NEW_RATIO + 1)), minSpaceSize(), maxYoung);
        }
        UnsignedWord initialSurvivor = WordFactory.zero();
        if (HeapParameters.getMaxSurvivorSpaces() > 0) {
            /*
             * In HotSpot, this is the reserved capacity of each of the survivor From and To spaces,
             * i.e., together they occupy 2x this size. Our chunked heap doesn't reserve memory, so
             * we use never occupy more than 1x this size for survivors except during collections.
             * However, this is inconsistent with how we interpret the maximum size of the old
             * generation, which we can exceed the (current) old gen size while copying during
             * collections.
             */
            initialSurvivor = minSpaceSize(alignUp(initialYoung.unsignedDivide(AbstractCollectionPolicy.INITIAL_SURVIVOR_RATIO)));
        }
        UnsignedWord initialEden = minSpaceSize(alignUp(initialYoung.subtract(initialSurvivor.multiply(2))));

        return new SizeParameters(maxHeap, maxYoung, initialHeap, initialEden, initialSurvivor, minHeap);
    }

    protected static final class SizeParameters {
        final UnsignedWord maxHeapSize;
        final UnsignedWord maxYoungSize;
        final UnsignedWord initialHeapSize;
        final UnsignedWord initialEdenSize;
        final UnsignedWord initialSurvivorSize;
        final UnsignedWord minHeapSize;

        SizeParameters(UnsignedWord maxHeapSize, UnsignedWord maxYoungSize, UnsignedWord initialHeapSize,
                        UnsignedWord initialEdenSize, UnsignedWord initialSurvivorSize, UnsignedWord minHeapSize) {
            this.maxHeapSize = maxHeapSize;
            this.maxYoungSize = maxYoungSize;
            this.initialHeapSize = initialHeapSize;
            this.initialEdenSize = initialEdenSize;
            this.initialSurvivorSize = initialSurvivorSize;
            this.minHeapSize = minHeapSize;

            assert isAligned(maxHeapSize) && isAligned(maxYoungSize) && isAligned(initialHeapSize) && isAligned(initialEdenSize) && isAligned(initialSurvivorSize);
            assert isAligned(maxSurvivorSize()) && isAligned(initialYoungSize()) && isAligned(initialOldSize()) && isAligned(maxOldSize());

            assert initialHeapSize.belowOrEqual(maxHeapSize);
            assert maxSurvivorSize().belowThan(maxYoungSize);
            assert maxYoungSize.add(maxOldSize()).equal(maxHeapSize);
            assert maxHeapSize.belowOrEqual(ReferenceAccess.singleton().getAddressSpaceSize());
            assert initialEdenSize.add(initialSurvivorSize.multiply(2)).equal(initialYoungSize());
            assert initialYoungSize().add(initialOldSize()).equal(initialHeapSize);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        UnsignedWord maxSurvivorSize() {
            if (HeapParameters.getMaxSurvivorSpaces() == 0) {
                return WordFactory.zero();
            }
            UnsignedWord size = maxYoungSize.unsignedDivide(MIN_SURVIVOR_RATIO);
            return minSpaceSize(alignDown(size));
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        UnsignedWord initialYoungSize() {
            return initialEdenSize.add(initialSurvivorSize.multiply(2));
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        UnsignedWord initialOldSize() {
            return initialHeapSize.subtract(initialYoungSize());
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        UnsignedWord maxOldSize() {
            return maxHeapSize.subtract(maxYoungSize);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        boolean equal(SizeParameters other) {
            return maxHeapSize.equal(other.maxHeapSize) && maxYoungSize.equal(other.maxYoungSize) && initialHeapSize.equal(other.initialHeapSize) &&
                            initialEdenSize.equal(other.initialEdenSize) && initialSurvivorSize.equal(other.initialSurvivorSize) && minHeapSize.equal(other.minHeapSize);
        }
    }
}
