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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;
import jdk.internal.misc.Unsafe;

/**
 * Note that a lot of methods in this class are final. Subclasses may only override certain methods
 * to avoid inconsistencies between the different heap size values.
 */
abstract class AbstractCollectionPolicy implements CollectionPolicy {

    protected static final int MIN_SPACE_SIZE_AS_NUMBER_OF_ALIGNED_CHUNKS = 8;

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/hotspot/share/gc/shared/gc_globals.hpp#L572-L575") //
    protected static final int MAX_TENURING_THRESHOLD = 15;

    @Platforms(Platform.HOSTED_ONLY.class)
    static int getMaxSurvivorSpaces(Integer userValue) {
        assert userValue == null || userValue >= 0 : userValue;
        return (userValue != null) ? userValue : AbstractCollectionPolicy.MAX_TENURING_THRESHOLD;
    }

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long SIZES_UPDATE_LOCK_OFFSET = U.objectFieldOffset(AbstractCollectionPolicy.class, "sizesUpdateLock");

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
    // GR-68417: adopt "JDK-8338977: Parallel: Improve heap resizing heuristics"
    // @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/hotspot/share/gc/shared/gc_globals.hpp#L340-L342")
    protected static final int ADAPTIVE_TIME_WEIGHT = 25;

    /* Constants to compute defaults for values which can be set through existing options. */
    protected static final UnsignedWord INITIAL_HEAP_SIZE = Word.unsigned(128 * 1024 * 1024);
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/hotspot/share/gc/shared/gc_globals.hpp#L554-L556") //
    protected static final int NEW_RATIO = 2;

    protected final AdaptiveWeightedAverage avgYoungGenAlignedChunkFraction = new AdaptiveWeightedAverage(ADAPTIVE_TIME_WEIGHT);

    private final int initialNewRatio;
    protected UnsignedWord survivorSize;
    protected UnsignedWord edenSize;
    protected UnsignedWord promoSize;
    protected UnsignedWord oldSize;
    protected int tenuringThreshold;

    protected volatile SizeParameters sizes = null;
    @SuppressWarnings("unused") private volatile int sizesUpdateLock;

    protected AbstractCollectionPolicy(int initialNewRatio, int initialTenuringThreshold) {
        this.initialNewRatio = initialNewRatio;
        this.tenuringThreshold = UninterruptibleUtils.Math.clamp(initialTenuringThreshold, 1, HeapParameters.getMaxSurvivorSpaces() + 1);
    }

    @Override
    public boolean shouldCollectOnAllocation() {
        if (sizes == null) {
            return false; // updateSizeParameters() has never been called
        }
        UnsignedWord edenUsed = HeapImpl.getAccounting().getEdenUsedBytes();
        return edenUsed.aboveOrEqual(edenSize);
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
        /*
         * Read the old object before computing the new values. Otherwise, we risk reusing an
         * outdated SizeParameters object.
         */
        SizeParameters prevParams = sizes;
        SizeParameters newParams = computeSizeParameters(prevParams);
        if (prevParams != null && newParams.equal(prevParams)) {
            return; // nothing to do
        }
        updateSizeParameters0(newParams, prevParams);
        guaranteeSizeParametersInitialized(); // sanity
    }

    @Uninterruptible(reason = "Holding the spin lock at a safepoint can result in deadlocks.")
    private void updateSizeParameters0(SizeParameters newParams, SizeParameters prevParams) {
        /*
         * We use a primitive spin lock because at this point, the current thread might be unable to
         * use a Java lock (e.g. no Thread object yet), and the critical section is short, so we do
         * not want to suspend and wake up threads for it.
         */
        JavaSpinLockUtils.lockNoTransition(this, SIZES_UPDATE_LOCK_OFFSET);
        try {
            if (sizes != prevParams) {
                /*
                 * Some other thread beat us and we cannot tell if our values or their values are
                 * newer, so back off - any newer values will be applied eventually.
                 */
                return;
            }
            updateSizeParametersLocked(newParams, prevParams);
        } finally {
            JavaSpinLockUtils.unlock(this, SIZES_UPDATE_LOCK_OFFSET);
        }
    }

    @Uninterruptible(reason = "Holding the spin lock at a safepoint can result in deadlocks. Updating the size parameters must be atomic with regard to garbage collection.")
    private void updateSizeParametersLocked(SizeParameters newParams, SizeParameters prevParams) {
        sizes = newParams;

        if (prevParams == null || gcCount() == 0) {
            survivorSize = newParams.initialSurvivorSize;
            edenSize = newParams.initialEdenSize;
            oldSize = newParams.initialOldSize();
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
        survivorSize = UnsignedUtils.min(survivorSize, newParams.maxSurvivorSize());
        edenSize = UnsignedUtils.min(edenSize, getMaximumEdenSize());
        oldSize = UnsignedUtils.min(oldSize, newParams.maxOldSize());
        promoSize = UnsignedUtils.min(promoSize, newParams.maxOldSize());
    }

    @Override
    public final UnsignedWord getInitialEdenSize() {
        guaranteeSizeParametersInitialized();
        return sizes.initialEdenSize;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/hotspot/share/gc/parallel/psYoungGen.cpp#L104-L116")
    public final UnsignedWord getMaximumEdenSize() {
        guaranteeSizeParametersInitialized();
        return alignDown(sizes.maxYoungSize.subtract(survivorSize.multiply(2)));
    }

    @Override
    public final UnsignedWord getMaximumHeapSize() {
        guaranteeSizeParametersInitialized();
        return sizes.maxHeapSize;
    }

    @Override
    public final UnsignedWord getMaximumYoungGenerationSize() {
        guaranteeSizeParametersInitialized();
        return sizes.maxYoungSize;
    }

    @Override
    public final UnsignedWord getInitialSurvivorSize() {
        guaranteeSizeParametersInitialized();
        return sizes.initialSurvivorSize;
    }

    @Override
    public final UnsignedWord getMaximumSurvivorSize() {
        guaranteeSizeParametersInitialized();
        return sizes.maxSurvivorSize();
    }

    @Override
    public final UnsignedWord getCurrentHeapCapacity() {
        assert VMOperation.isGCInProgress() : "use only during GC";
        guaranteeSizeParametersInitialized();
        return edenSize.add(survivorSize).add(oldSize);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final UnsignedWord getSurvivorSpacesCapacity() {
        assert VMOperation.isGCInProgress() : "use only during GC";
        guaranteeSizeParametersInitialized();
        return survivorSize;
    }

    @Override
    @Uninterruptible(reason = "Ensure reading a consistent value.")
    public final UnsignedWord getYoungGenerationCapacity() {
        guaranteeSizeParametersInitialized();
        return edenSize.add(survivorSize);
    }

    @Override
    public final UnsignedWord getInitialOldSize() {
        guaranteeSizeParametersInitialized();
        return sizes.initialOldSize();
    }

    @Override
    public final UnsignedWord getMaximumOldSize() {
        guaranteeSizeParametersInitialized();
        return sizes.maxOldSize();
    }

    @Override
    public final UnsignedWord getOldGenerationCapacity() {
        guaranteeSizeParametersInitialized();
        return oldSize;
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
        UnsignedWord total = edenSize.add(HeapImpl.getAccounting().getSurvivorUsedBytes());
        double alignedFraction = Math.min(1, Math.max(0, avgYoungGenAlignedChunkFraction.getAverage()));
        return UnsignedUtils.fromDouble(UnsignedUtils.toDouble(total) * alignedFraction);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getTenuringAge() {
        assert VMOperation.isGCInProgress() : "use only during GC";
        return tenuringThreshold;
    }

    @Override
    public void onCollectionBegin(boolean completeCollection, long requestingNanoTime) {
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
        return sizes.minHeapSize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract long gcCount();

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/gc/shared/genArguments.cpp#L190-L305")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/hotspot/share/gc/parallel/psYoungGen.cpp#L146-L168")
    protected SizeParameters computeSizeParameters(SizeParameters existing) {
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
        long optionMaxYoung = SubstrateGCOptions.MaxNewSize.getValue();
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
        long optionMin = SubstrateGCOptions.MinHeapSize.getValue();
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

        return SizeParameters.get(existing, maxHeap, maxYoung, initialHeap, initialEden, initialSurvivor, minHeap);
    }

    protected UnsignedWord getHeapSizeLimit() {
        return CommittedMemoryProvider.get().getCollectedHeapAddressSpaceSize();
    }

    protected UnsignedWord getYoungSizeLimit(UnsignedWord maxHeap) {
        return maxHeap.subtract(minSpaceSize());
    }

    protected long getMaximumHeapSizeOptionValue() {
        return SubstrateGCOptions.MaxHeapSize.getValue();
    }

    protected UnsignedWord getInitialHeapSize() {
        return AbstractCollectionPolicy.INITIAL_HEAP_SIZE;
    }

    protected static final class SizeParameters {
        final UnsignedWord maxHeapSize;
        final UnsignedWord maxYoungSize;
        final UnsignedWord initialHeapSize;
        final UnsignedWord initialEdenSize;
        final UnsignedWord initialSurvivorSize;
        final UnsignedWord minHeapSize;

        static SizeParameters get(SizeParameters existing, UnsignedWord maxHeap, UnsignedWord maxYoung, UnsignedWord initialHeap,
                        UnsignedWord initialEden, UnsignedWord initialSurvivor, UnsignedWord minHeap) {
            if (existing != null && existing.matches(maxHeap, maxYoung, initialHeap, initialEden, initialSurvivor, minHeap)) {
                return existing;
            }
            return new SizeParameters(maxHeap, maxYoung, initialHeap, initialEden, initialSurvivor, minHeap);
        }

        private SizeParameters(UnsignedWord maxHeapSize, UnsignedWord maxYoungSize, UnsignedWord initialHeapSize,
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
            assert maxHeapSize.belowOrEqual(ReferenceAccess.singleton().getMaxAddressSpaceSize());
            assert initialEdenSize.add(initialSurvivorSize.multiply(2)).equal(initialYoungSize());
            assert initialYoungSize().add(initialOldSize()).equal(initialHeapSize);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/hotspot/share/gc/parallel/psYoungGen.cpp#L104-L116")
        UnsignedWord maxSurvivorSize() {
            if (HeapParameters.getMaxSurvivorSpaces() == 0) {
                return Word.zero();
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
            return other == this || other.matches(maxHeapSize, maxYoungSize, initialHeapSize, initialEdenSize, initialSurvivorSize, minHeapSize);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        boolean matches(UnsignedWord maxHeap, UnsignedWord maxYoung, UnsignedWord initialHeap, UnsignedWord initialEden, UnsignedWord initialSurvivor, UnsignedWord minHeap) {
            return maxHeapSize.equal(maxHeap) && maxYoungSize.equal(maxYoung) && initialHeapSize.equal(initialHeap) &&
                            initialEdenSize.equal(initialEden) && initialSurvivorSize.equal(initialSurvivor) && minHeapSize.equal(minHeap);
        }
    }
}
