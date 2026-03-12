/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.genscavenge.AdaptiveCollectionPolicy2Tunables.NUM_OF_GC_SAMPLE;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.Timer;
import com.oracle.svm.core.util.UnsignedUtils;

/** Constants for policy tunables. */
interface AdaptiveCollectionPolicy2Tunables {

    /**
     * Start out with the same sizes for young and old generation. This leads to less frequent minor
     * collections and tenuring at startup especially with {@link #YOUNG_GENERATION_SIZE_SUPPLEMENT}
     * disabled. (The HotSpot NewRatio default is 2, so 1:2 for young:old)
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/gc_globals.hpp#L553-L557") //
    int INITIAL_NEW_RATIO = 1;

    /*
     *
     * The following are -XX options in HotSpot, refer to their descriptions for details. The values
     * are HotSpot defaults unless labeled otherwise.
     *
     * Don't change these values individually without carefully going over their occurrences in
     * HotSpot source code, there are dependencies between them that are not handled in our code.
     *
     */

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/gc_globals.hpp#L325-L401") // actually:jdk-26+25#L308-L353
    int ADAPTIVE_SIZE_POLICY_READY_THRESHOLD = 5;
    int ADAPTIVE_SIZE_POLICY_WEIGHT = 10;
    int PROMOTED_PADDING = 3;
    int THRESHOLD_TOLERANCE = 10;
    /**
     * Maximum size increment step percentage. We reduce it from HotSpot's default to avoid growing
     * the heap too eagerly and overshooting.
     */
    int YOUNG_GENERATION_SIZE_INCREMENT = 10; // HotSpot default: 20
    /*
     * Supplement to accelerate heap expansion at startup. We do not use it to avoid growing the
     * heap too eagerly and overshooting.
     */
    int YOUNG_GENERATION_SIZE_SUPPLEMENT = 0; // HotSpot default: 80
    int YOUNG_GENERATION_SIZE_SUPPLEMENT_DECAY = 8;
    double MAX_GC_PAUSE_MILLIS = UnsignedUtils.toDouble(UnsignedUtils.MAX_VALUE.subtract(1));
    /**
     * Target for throughput, which is the ratio of mutator wall-clock time to GC wall-clock time.
     * Eden grows until reaching this ratio. HotSpot's default is 99, so, spending 1% of time in GC.
     *
     * With our single-threaded collector, we cannot expect to always scale with the allocations of
     * a multi-threaded mutator by growing spaces, and often end up with long pause times instead.
     * Therefore we set this to 1, i.e., 50%. Beyond that, we use {@link #MIN_GC_DISTANCE_SECOND} to
     * drive eden size, avoiding collecting too frequently and starving the mutator.
     */
    int GC_TIME_RATIO = 1;
    int ADAPTIVE_SIZE_DECREMENT_SCALE_FACTOR = 4;
    /**
     * The tenuring threshold at startup (HotSpot default: 7). The policy intentionally never
     * reduces the tenuring threshold, so this is also its minimum value.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/gc_globals.hpp#L576-L581") //
    int INITIAL_TENURING_THRESHOLD = 7;

    /*
     * We don't want to limit our freedom to adjust the heap. (Unless set explicitly, these options
     * are set to these values in ParallelArguments::initialize on HotSpot)
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/parallel/parallelArguments.cpp#L57-L68") //
    int MIN_HEAP_FREE_RATIO = 0; // %
    int MAX_HEAP_FREE_RATIO = 100; // %
    /** On HotSpot, this is the behavior if {@link #MIN_HEAP_FREE_RATIO} is not set explicitly. */
    boolean SHRINK_OLD_CAUTIOUSLY = true;

    /*
     *
     * Constants in class AdaptiveSizePolicy.
     *
     */

    /** [0, 1]; closer to 1 means assigning more weight to most recent samples. */
    double SEQ_DEFAULT_ALPHA_VALUE = 0.75;

    /**
     * Minimal distance between two consecutive GC pauses; shorter distance (more frequent gc) can
     * hinder app throughput. Additionally, too frequent gc means objs haven't got time to die yet,
     * so the number of promoted objs will be high. HotSpot default: 100ms.
     *
     * Beyond the minimum throughput via {@link #GC_TIME_RATIO}, we use distance to set eden size so
     * the mutator can make sufficient progress and to limit overhead from frequent safepoints.
     * Enforcing longer distance generally results in higher memory usage, and when it does not lead
     * to a larger share of dying objects, causes longer pauses with little throughput improvement.
     *
     * 20ms seems to strike a good balance for us.
     */
    double MIN_GC_DISTANCE_SECOND = 0.020;

    int NUM_OF_GC_SAMPLE = 32;
}

/**
 * A garbage collection policy that balances throughput and memory footprint (and optionally pause
 * times, which we currently don't consider).
 * <p>
 * Most importantly, it tries to ensure a minimum throughput ({@link #GC_TIME_RATIO}) and a minimum
 * time between collections ({@link #MIN_GC_DISTANCE_SECOND}) by expanding or shrinking eden in
 * {@link #computeDesiredEdenSize}.
 * <p>
 * The policy triggers complete collections when an incremental collection promotes objects beyond
 * the old generation's capacity, or the next incremental collection is predicted to exceed the
 * maximum old generation size, see {@link #shouldCollectCompletely}.
 * <p>
 * The policy increases the tenuring threshold (incremental collections before promoting an object
 * to the old generation) to favor incremental collections when spending most time on complete
 * collections, see {@link #computeTenuringThreshold}. It never decreases the tenuring threshold.
 * <p>
 * Most of this code is based on HotSpot's ParallelGC adaptive size policy. Unless otherwise stated,
 * code in this class has been adapted from HotSpot class {@code PSAdaptiveSizePolicy}. Names have
 * been kept mostly the same for comparability. Initial tweaking focused on {@link #GC_TIME_RATIO}
 * and {@link #MIN_GC_DISTANCE_SECOND}, further ideas are tracked in GR-73130.
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/parallel/psAdaptiveSizePolicy.hpp") // actually:jdk-26+25
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/parallel/psAdaptiveSizePolicy.cpp") // actually:jdk-26+25
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/parallel/psScavenge.cpp") // actually:jdk-26+25#L311-L508
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/parallel/psParallelCompact.cpp") // actually:jdk-26+25#L934-L1055
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/parallel/psYoungGen.cpp") // actually:jdk-26+25#L325-L423
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/parallel/parallelScavengeHeap.cpp") // actually:jdk-26+25#L824-L916
class AdaptiveCollectionPolicy2 extends AdaptiveCollectionPolicy2Base {

    private final AdaptivePaddedAverage avgPromoted = new AdaptivePaddedAverage(ADAPTIVE_SIZE_POLICY_WEIGHT, PROMOTED_PADDING, true);

    private boolean oldSizeExceededInPreviousCollection = false;

    /**
     * To facilitate faster growth at start up, supplement the normal growth percentage for the
     * young gen eden and the old gen space for promotion with this value, which decays with
     * increasing collections.
     */
    private int youngGenSizeIncrementSupplement = YOUNG_GENERATION_SIZE_SUPPLEMENT;

    private long majorCount = 0;

    AdaptiveCollectionPolicy2() {
        super(MAX_GC_PAUSE_MILLIS / 1000.0);
    }

    @Override
    public String getName() {
        return "adaptive2";
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/parallel/parallelScavengeHeap.cpp") // actually:jdk-26+25#L372-L421
    public boolean shouldCollectCompletely(boolean followingIncrementalCollection, boolean forcedCompleteCollection) { // ParallelScavengeHeap::should_attempt_young_gc
        guaranteeSizeParametersInitialized();

        boolean collectYoungSeparately = CollectionPolicy.shouldCollectYoungGenSeparately(!SerialGCOptions.useCompactingOldGen());
        if (forcedCompleteCollection && !collectYoungSeparately) {
            return true;
        }
        if (!followingIncrementalCollection && collectYoungSeparately) {
            /*
             * With a copying collector, default to always doing an incremental collection first
             * because we expect most of the objects in the young generation to be garbage, and we
             * can reuse their leftover chunks for copying the live objects in the old generation
             * with fewer allocations.
             *
             * With a compacting collector, complete collections are more expensive, but first doing
             * an incremental collection if the young generation makes up a significant part of the
             * heap wasn't found to be beneficial.
             */
            return false;
        }
        if (followingIncrementalCollection && oldSizeExceededInPreviousCollection) {
            /*
             * In the preceding incremental collection, to avoid a promotion failure, we promoted
             * objects to the old generation beyond its current capacity. We might have temporarily
             * exceeded the maximum heap size, but due to the chunked nature of our heap, should
             * currently be within limits.
             *
             * Follow up with a full collection during which we reclaim enough space or expand the
             * old generation. However, when tenuring all objects, we might exceed the old
             * generation's maximum size and potentially later maximum heap size (GR-72932).
             */
            return true;
        }

        if (!collectYoungSeparately && followingIncrementalCollection) {
            // Don't override the earlier decision to not do a full GC below (and prolong the pause)
            return false;
        }

        UnsignedWord youngUsed = HeapImpl.getHeapImpl().getYoungGeneration().getChunkBytes();
        UnsignedWord oldUsed = HeapImpl.getHeapImpl().getOldGeneration().getChunkBytes();

        /*
         * If the remaining free space in the old generation is less than what is expected to be
         * needed by the next collection, do a full collection now.
         */
        UnsignedWord averagePromoted = UnsignedUtils.fromDouble(avgPromoted.getPaddedAverage());
        UnsignedWord promotionEstimate = UnsignedUtils.min(averagePromoted, youngUsed);
        UnsignedWord freeInOldGenWithExpansion = sizes.getMaxOldSize().subtract(oldUsed);
        /*
         * Note that ParallelScavengeHeap also considers if the OS has enough free memory ready to
         * commit and expand old gen for promotions, and if not, does a full GC. This is to avoid
         * inflating minor GC pause times, and as a result, inaccurately resizing the young gen.
         */
        return promotionEstimate.aboveOrEqual(freeInOldGenWithExpansion);
    }

    /** First part of PSScavenge::invoke and PSParallelCompact::invoke. */
    @Override
    public void onCollectionBegin(boolean completeCollection, long beginNanoTime) {
        incrementTotalCollections(completeCollection);
        if (completeCollection) {
            majorCollectionBegin(beginNanoTime);
        } else {
            minorCollectionBegin(beginNanoTime);
        }

        super.onCollectionBegin(completeCollection, beginNanoTime);
    }

    void incrementTotalCollections(boolean full) { // CollectedHeap::increment_total_collections
        if (full) {
            majorCount++;
        } else {
            minorCount++;
        }
    }

    /** Second part of PSScavenge::invoke and PSParallelCompact::invoke. */
    @Override
    public void onCollectionEnd(boolean completeCollection, GCCause cause) {
        GCAccounting acc = GCImpl.getAccounting();
        UnsignedWord oldLive = acc.getOldGenerationAfterChunkBytes();
        if (completeCollection) {
            majorCollectionEnd();
            UnsignedWord oldUsed = UnsignedUtils.max(acc.getOldGenerationBeforeChunkBytes(), acc.getOldGenerationAfterChunkBytes());
            sampleOldGenUsedBytes(oldUsed);
            resizeAfterFullGC(oldLive);
        } else {
            minorCollectionEnd(sizes.getEdenSize());

            /*
             * We technically don't have promotion failures in which case the HotSpot policy doesn't
             * do some of what is below. We can exceed the current capacity of the old generation
             * during a young collection however, and trigger a full collection straight after.
             */
            oldSizeExceededInPreviousCollection = oldLive.aboveThan(sizes.getOldSize());

            boolean survivorOverflow = acc.hasLastIncrementalCollectionOverflowedSurvivors();
            UnsignedWord survived = HeapImpl.getHeapImpl().getYoungGeneration().getSurvivorChunkBytes();
            UnsignedWord promoted = acc.getLastIncrementalCollectionPromotedChunkBytes();
            updateAverages(survivorOverflow, UnsignedUtils.toDouble(survived), UnsignedUtils.toDouble(promoted));
            sampleOldGenUsedBytes(acc.getOldGenerationAfterChunkBytes());

            tenuringThreshold = computeTenuringThreshold(survivorOverflow, tenuringThreshold);

            resizeAfterYoungGC(survivorOverflow, oldLive, survived);

            decaySupplementalGrowth(minorCount);
        }

        recordGcPauseEndInstant();
    }

    private void resizeAfterFullGC(UnsignedWord oldLive) { // ParallelScavengeHeap::resize_after_full_gc
        resizeOldGenAfterFullGC(oldLive);
        /*
         * We don't resize young-gen after full-gc because eden-size directly affects young-gc
         * frequency (GC_TIME_RATIO), and we don't have enough info to determine its desired size.
         */
    }

    private UnsignedWord calculateDesiredOldGenCapacity(UnsignedWord oldGenLiveSize) { // ParallelScavengeHeap::calculate_desired_old_gen_capacity
        // If min free percent is 100%, the old-gen should always be at its max capacity.
        int minHeapFreeRatio = MIN_HEAP_FREE_RATIO; // (avoids ecj warnings)
        if (minHeapFreeRatio == 100) {
            return sizes.getMaxOldSize();
        }

        // Using recorded data to calculate the new capacity of old-gen to avoid
        // excessive expansion but also keep footprint low

        UnsignedWord promotedEstimate = paddedAveragePromotedInBytes();

        // Should have at least this free room for the next young-gc promotion.
        UnsignedWord freeSize = promotedEstimate;

        UnsignedWord largestLiveSize = UnsignedUtils.max(oldGenLiveSize, UnsignedUtils.fromDouble(peakOldGenUsedEstimate()));
        freeSize = freeSize.add(largestLiveSize.subtract(oldGenLiveSize));

        // Respect free percent
        if (minHeapFreeRatio != 0) {
            UnsignedWord minFree = calculateFreeFromFreeRatioFlag(oldGenLiveSize, minHeapFreeRatio);
            freeSize = UnsignedUtils.max(freeSize, minFree);
        }

        int maxHeapFreeRatio = MAX_HEAP_FREE_RATIO; // (avoids ecj warnings)
        if (maxHeapFreeRatio != 100) {
            UnsignedWord maxFree = calculateFreeFromFreeRatioFlag(oldGenLiveSize, maxHeapFreeRatio);
            freeSize = UnsignedUtils.min(maxFree, freeSize);
        }

        return oldGenLiveSize.add(freeSize);
    }

    private static UnsignedWord calculateFreeFromFreeRatioFlag(UnsignedWord live, int freePercent) { // ParallelScavengeHeap::calculate_free_from_free_ratio_flag
        assert freePercent != 100;
        // We want to calculate how much free memory there can be based on the
        // live size.
        // percent * (free + live) = free
        // =>
        // free = (live * percent) / (1 - percent)
        double percent = freePercent / 100.0;
        return UnsignedUtils.fromDouble(UnsignedUtils.toDouble(live) * percent / (1.0 - percent));
    }

    private void resizeOldGenAfterFullGC(UnsignedWord oldLive) { // ParallelScavengeHeap::resize_old_gen_after_full_gc
        UnsignedWord currentCapacity = sizes.getOldSize();
        UnsignedWord desiredCapacity = calculateDesiredOldGenCapacity(oldLive);

        if (SHRINK_OLD_CAUTIOUSLY) {
            if (desiredCapacity.belowThan(currentCapacity)) {
                // Shrinking
                if (majorCount < ADAPTIVE_SIZE_POLICY_READY_THRESHOLD) {
                    // Not enough data for shrinking
                    return;
                }
            }
        }

        // from PSOldGen::resize
        UnsignedWord newOldSize = UnsignedUtils.clamp(alignUp(desiredCapacity), minSpaceSize(), sizes.getMaxOldSize());
        sizes.setOldSize(newOldSize);
    }

    private void resizeAfterYoungGC(boolean isSurvivorOverflowing, UnsignedWord oldLive, UnsignedWord survived) { // ParallelScavengeHeap::resize_after_young_gc
        doResizeAfterYoungGC(isSurvivorOverflowing, survived);

        // Consider if should shrink old-gen
        if (!isSurvivorOverflowing) {
            // Upper bound for a single step shrink
            UnsignedWord maxShrinkBytes = minSpaceSize();
            UnsignedWord oldCapacity = sizes.getOldSize();
            UnsignedWord oldGenFreeInBytes = oldCapacity.subtract(oldLive);
            UnsignedWord shrinkBytes = computeOldGenShrinkBytes(oldGenFreeInBytes, maxShrinkBytes);
            if (shrinkBytes.notEqual(0)) {
                int minHeapFreeRatio = MIN_HEAP_FREE_RATIO; // (avoids ecj warnings)
                if (minHeapFreeRatio != 0) {
                    UnsignedWord newCapacity = oldCapacity.subtract(shrinkBytes);
                    UnsignedWord newFreeSize = oldGenFreeInBytes.subtract(shrinkBytes);
                    if (UnsignedUtils.toDouble(newFreeSize) / UnsignedUtils.toDouble(newCapacity) * 100 < minHeapFreeRatio) {
                        // Would violate MinHeapFreeRatio
                        return;
                    }
                }

                // from PSOldGen::shrink
                UnsignedWord newOldSize = minSpaceSize(alignDown(oldCapacity.subtract(shrinkBytes)));
                sizes.setOldSize(newOldSize);
            }
        }
    }

    private void doResizeAfterYoungGC(boolean isSurvivorOverflowing, UnsignedWord survived) { // PSYoungGen::resize_after_young_gc
        computeYoungDesiredSizesAndResize(isSurvivorOverflowing, survived);
    }

    private void computeYoungDesiredSizesAndResize(boolean isSurvivorOverflowing, UnsignedWord survived) { // PSYoungGen::compute_desired_sizes
        UnsignedWord currentEdenSize = sizes.getEdenSize();
        UnsignedWord currentSurvivorSize = sizes.getSurvivorSize();

        UnsignedWord edenSize = computeDesiredEdenSize(isSurvivorOverflowing, currentEdenSize);
        edenSize = minSpaceSize(alignUp(edenSize));

        UnsignedWord survivorSize = computeDesiredSurvivorSize(currentSurvivorSize);
        survivorSize = minSpaceSize(alignUp(UnsignedUtils.max(survivorSize, survived)));
        // If we don't have survivor spaces, max survivor size is 0:
        survivorSize = UnsignedUtils.min(survivorSize, sizes.getMaxSurvivorSize());

        UnsignedWord newGenSize = edenSize.add(survivorSize.multiply(2));
        final UnsignedWord minGenSize = getMinYoungSpacesSize();
        final UnsignedWord maxGenSize = sizes.getMaxYoungSize();
        if (newGenSize.belowThan(minGenSize)) {
            // Keep survivor and adjust eden to meet min-gen-size
            edenSize = minGenSize.subtract(survivorSize.multiply(2));
        } else if (maxGenSize.belowThan(newGenSize)) {
            // New capacity would exceed max; need to revise these desired sizes.
            // Favor survivor over eden in order to reduce promotion (overflow).
            if (survivorSize.multiply(2).aboveOrEqual(maxGenSize)) {
                // If requested survivor size is too large
                survivorSize = alignDown(maxGenSize.subtract(minSpaceSize()).unsignedDivide(2));
                edenSize = maxGenSize.subtract(survivorSize.multiply(2));
            } else {
                // Respect survivor size and reduce eden
                edenSize = maxGenSize.subtract(survivorSize.multiply(2));
            }
        }

        // PSYoungGen::resize_inner
        sizes.setEdenSize(edenSize);
        sizes.setSurvivorSize(survivorSize);
    }

    private UnsignedWord paddedAveragePromotedInBytes() {
        return UnsignedUtils.fromDouble(avgPromoted.getPaddedAverage());
    }

    /** Should be called at the start of a major collection. */
    private void majorCollectionBegin(long beginNanoTime) {
        majorTimer.reset();
        majorTimer.startAt(beginNanoTime);
        recordGcPauseStartInstant(beginNanoTime);
    }

    /** Should be called at the end of a major collection. */
    private void majorCollectionEnd() {
        majorTimer.stop();
        double majorPauseInSeconds = TimeUtils.nanosToSecondsDouble(majorTimer.totalNanos());
        recordGcDuration(majorPauseInSeconds);
        trimmedMajorGcTimeSeconds.add(majorPauseInSeconds);
    }

    /**
     * The throughput goal is implemented as _throughput_goal = 1 - (1 / (1 + gc_cost_ratio)).
     *
     * gc_cost_ratio is the ratio application cost / gc cost.
     *
     * For example a gc_cost_ratio of 4 translates into a throughput goal of 0.80.
     */
    private static double calculateThroughputGoal(double gcCostRatio) {
        return 1.0 - (1.0 / (1.0 + gcCostRatio));
    }

    private UnsignedWord computeDesiredEdenSize(boolean isSurvivorOverflowing, UnsignedWord curEden) {
        // Guard against divide-by-zero; 0.001ms
        double gcDistance = Math.max(gcDistanceSecondsSeq.last(), 0.000001);
        double minGcDistance = MIN_GC_DISTANCE_SECOND;

        double throughputGoal = calculateThroughputGoal(GC_TIME_RATIO);

        if (mutatorTimePercent() < throughputGoal) {
            UnsignedWord newEden;
            double expectedGcDistance = trimmedMinorGcTimeSeconds.last() * GC_TIME_RATIO;
            if (gcDistance >= expectedGcDistance) {
                // The latest sample already satisfies throughput goal; keep the current size
                newEden = curEden;
            } else {
                // Using the latest sample to limit the growth in order to avoid overshoot
                newEden = UnsignedUtils.min(
                                UnsignedUtils.fromDouble((expectedGcDistance / gcDistance) * UnsignedUtils.toDouble(curEden)),
                                increaseEden(curEden));
            }
            return newEden;
        }

        if (minorGcTimeEstimate() > gcPauseGoalSec) {
            return decreaseEdenForMinorPauseTime(curEden);
        }

        if (gcDistance < minGcDistance) {
            UnsignedWord newEden = UnsignedUtils.min(
                            UnsignedUtils.fromDouble((minGcDistance / gcDistance) * UnsignedUtils.toDouble(curEden)),
                            increaseEden(curEden));
            return newEden;
        }

        // If no overflowing and promotion is small
        if (!isSurvivorOverflowing && promotedBytesEstimate() < 1024) {
            UnsignedWord delta = UnsignedUtils.min(
                            edenIncrement(curEden).unsignedDivide(ADAPTIVE_SIZE_DECREMENT_SCALE_FACTOR),
                            curEden.unsignedDivide(2));
            double deltaFactor = UnsignedUtils.toDouble(delta) / UnsignedUtils.toDouble(curEden);

            double gcTimeLowerEstimate = trimmedMinorGcTimeSeconds.davg() - trimmedMinorGcTimeSeconds.dsd();
            // Limit GC frequency so that promoted rate is < 1MB/s
            // promotedBytesEstimate() / (gcDistance + gcTimeLowerEstimate) < 1M/s
            // ==> promotedBytesEstimate() / 1M - gcTimeLowerEstimate < gcDistance
            double gcDistanceTarget = Math.max(Math.max(
                            minorGcTimeConservativeEstimate() * GC_TIME_RATIO,
                            promotedBytesEstimate() / (1024 * 1024) - gcTimeLowerEstimate),
                            minGcDistance);
            double predictedGcDistance = gcDistance * (1 - deltaFactor) - gcDistanceSecondsSeq.dsd();

            if (predictedGcDistance > gcDistanceTarget) {
                return curEden.subtract(delta);
            }
        }

        return curEden;
    }

    private UnsignedWord computeDesiredSurvivorSize(UnsignedWord currentSurvivorSize) {
        UnsignedWord desiredSurvivorSize = UnsignedUtils.fromDouble(survivedBytesEstimate());
        if (desiredSurvivorSize.aboveOrEqual(currentSurvivorSize)) {
            // Increasing survivor
            return UnsignedUtils.min(desiredSurvivorSize, sizes.getMaxSurvivorSize());
        }
        UnsignedWord delta = currentSurvivorSize.subtract(desiredSurvivorSize);
        return currentSurvivorSize.subtract(delta.unsignedDivide(ADAPTIVE_SIZE_DECREMENT_SCALE_FACTOR));
    }

    private UnsignedWord computeOldGenShrinkBytes(UnsignedWord oldGenFreeBytes, UnsignedWord maxShrinkBytes) {
        final double lookaheadSec = 10 * 60; // 10min

        double freeBytes = UnsignedUtils.toDouble(oldGenFreeBytes);
        double promotionRate = promotionRateBytesPerSecEstimate();

        double minFreeBytes = Math.max(
                        UnsignedUtils.toDouble(paddedAveragePromotedInBytes()),
                        promotionRate * lookaheadSec);

        UnsignedWord shrinkBytes = Word.zero();
        if (freeBytes > minFreeBytes) {
            shrinkBytes = UnsignedUtils.fromDouble((freeBytes - minFreeBytes) / 2);
            shrinkBytes = UnsignedUtils.min(shrinkBytes, maxShrinkBytes);
        }
        return shrinkBytes;
    }

    /** Decays the supplemental growth additive. Called on collection count increments. */
    private void decaySupplementalGrowth(long numMinorGcs) {
        if (numMinorGcs >= ADAPTIVE_SIZE_POLICY_READY_THRESHOLD && numMinorGcs % YOUNG_GENERATION_SIZE_SUPPLEMENT_DECAY == 0) {
            youngGenSizeIncrementSupplement >>= 1;
        }
    }

    private UnsignedWord decreaseEdenForMinorPauseTime(UnsignedWord currentEdenSize) {
        UnsignedWord desiredEdenSize = minorPauseYoungEstimator.decrementWillDecrease()
                        ? currentEdenSize.subtract(edenDecrementAlignedDown(currentEdenSize))
                        : currentEdenSize;
        assert desiredEdenSize.belowOrEqual(currentEdenSize);
        return desiredEdenSize;
    }

    private UnsignedWord increaseEden(UnsignedWord currentEdenSize) {
        UnsignedWord delta = edenIncrementWithSupplementAlignedUp(currentEdenSize);
        UnsignedWord desiredEdenSize = currentEdenSize.add(delta);
        assert desiredEdenSize.aboveOrEqual(currentEdenSize);
        return desiredEdenSize;
    }

    private UnsignedWord edenIncrementWithSupplementAlignedUp(UnsignedWord curEden) {
        UnsignedWord result = edenIncrement(curEden, YOUNG_GENERATION_SIZE_INCREMENT + youngGenSizeIncrementSupplement);
        return alignUp(result);
    }

    private UnsignedWord edenDecrementAlignedDown(UnsignedWord curEden) {
        UnsignedWord edenHeapDelta = edenIncrement(curEden).unsignedDivide(ADAPTIVE_SIZE_DECREMENT_SCALE_FACTOR);
        return alignDown(edenHeapDelta);
    }

    private int computeTenuringThreshold(boolean isSurvivorOverflowing, int currentThreshold) {
        if (!youngGenPolicyIsReady) {
            return currentThreshold;
        }
        if (isSurvivorOverflowing) {
            return currentThreshold;
        }
        boolean incrTenuringThreshold = false;
        double majorCost = majorGcTimeSum();
        double minorCost = minorGcTimeSum();
        if (minorCost > majorCost * thresholdTolerancePercent) {
            // nothing; we prefer young-gc over full-gc
        } else if (majorCost > minorCost * thresholdTolerancePercent) {
            // Major times are too long, so we want less promotion.
            incrTenuringThreshold = true;
        }
        int newThreshold = currentThreshold;
        if (incrTenuringThreshold && newThreshold < HeapParameters.getMaxSurvivorSpaces()) {
            newThreshold++;
        }
        return newThreshold;
    }

    /** Updates averages that are always used (even if adaptive sizing is turned off). */
    private void updateAverages(boolean isSurvivorOverflow, double survived, double promoted) {
        if (!isSurvivorOverflow) {
            survivedBytes.add(survived);
        } else {
            // survived is an underestimate
            survivedBytes.add(survived + promoted);
        }
        avgPromoted.sample(promoted);
        promotedBytes.add(promoted);

        double promotionRate = promoted / (gcDistanceSecondsSeq.last() + trimmedMinorGcTimeSeconds.last());
        promotionRateBytesPerSec.add(promotionRate);
    }
}

/*
 * Code in this class has been adapted from HotSpot class {@code AdaptiveSizePolicy}. Names have
 * been kept mostly the same for comparability.
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/adaptiveSizePolicy.hpp") // actually:jdk-26+25
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/adaptiveSizePolicy.cpp") // actually:jdk-26+25
abstract class AdaptiveCollectionPolicy2Base extends AbstractCollectionPolicy implements AdaptiveCollectionPolicy2Tunables {

    // pause and interval times for collections
    final Timer minorTimer = new Timer("minor/between minor");
    final Timer majorTimer = new Timer("major/between major");

    // To measure wall-clock time between two GCs, i.e. mutator running time, and record them.
    final Timer gcDistanceTimer = new Timer("mutator running time");
    final NumberSeq gcDistanceSecondsSeq = new NumberSeq(SEQ_DEFAULT_ALPHA_VALUE);

    // Recording the last NUM_OF_GC_SAMPLE number of minor/major gc durations
    final TruncatedSeq trimmedMinorGcTimeSeconds = new TruncatedSeq(NUM_OF_GC_SAMPLE, SEQ_DEFAULT_ALPHA_VALUE);
    final TruncatedSeq trimmedMajorGcTimeSeconds = new TruncatedSeq(NUM_OF_GC_SAMPLE, SEQ_DEFAULT_ALPHA_VALUE);

    final GCSampleRingBuffer gcSamples = new GCSampleRingBuffer();

    /** The number of bytes promoted to old-gen after a young-gc. */
    final NumberSeq promotedBytes = new NumberSeq(SEQ_DEFAULT_ALPHA_VALUE);

    /** The number of bytes in to-space after a young-gc. */
    final NumberSeq survivedBytes = new NumberSeq(SEQ_DEFAULT_ALPHA_VALUE);

    /** The rate of promotion to old-gen. */
    final NumberSeq promotionRateBytesPerSec = new NumberSeq(SEQ_DEFAULT_ALPHA_VALUE);

    /** The peak of used bytes in old-gen before/after young/full-gc. */
    final NumberSeq peakOldUsedBytesSeq = new NumberSeq(SEQ_DEFAULT_ALPHA_VALUE);

    /**
     * Variable for estimating the major and minor pause times. These variables represent linear
     * least-squares fits of the data: minor pause time vs. young gen size
     */
    final LinearLeastSquareFit minorPauseYoungEstimator = new LinearLeastSquareFit(ADAPTIVE_SIZE_POLICY_WEIGHT);

    /** Allowed difference between major and minor GC times, used to compute tenuring threshold. */
    final double thresholdTolerancePercent = 1.0 + THRESHOLD_TOLERANCE / 100.0;

    /** Goal for maximum GC pause. */
    final double gcPauseGoalSec;

    /** Flag indicating that the adaptive policy is ready to use. */
    boolean youngGenPolicyIsReady = false;

    long minorCount = 0;

    AdaptiveCollectionPolicy2Base(double gcPauseGoalSec) {
        super(INITIAL_NEW_RATIO, INITIAL_TENURING_THRESHOLD);
        this.gcPauseGoalSec = gcPauseGoalSec;
    }

    double minorGcTimeSum() {
        return trimmedMinorGcTimeSeconds.getSum();
    }

    double majorGcTimeSum() {
        return trimmedMajorGcTimeSeconds.getSum();
    }

    void recordGcDuration(double gcDuration) {
        gcSamples.recordSample(gcDuration);
    }

    /** Percent of GC wall-clock time. */
    double gcTimePercent() {
        double totalTime = gcSamples.trimmedWindowDuration();
        double gcTime = gcSamples.durationSum();
        double gcPercent = gcTime / totalTime;
        assert gcPercent <= 1.0;
        assert gcPercent >= 0;
        return gcPercent;
    }

    UnsignedWord edenIncrement(UnsignedWord curEden, int percentChange) {
        return curEden.multiply(percentChange).unsignedDivide(100);
    }

    UnsignedWord edenIncrement(UnsignedWord curEden) {
        return edenIncrement(curEden, YOUNG_GENERATION_SIZE_INCREMENT);
    }

    void recordGcPauseEndInstant() {
        gcDistanceTimer.reset();
        gcDistanceTimer.start();
    }

    void recordGcPauseStartInstant(long beginNanoTime) {
        if (!gcDistanceTimer.wasStartedAtLeastOnce()) {
            long origin = Isolates.isStartTimeAssigned() ? Isolates.getStartTimeNanos() : beginNanoTime;
            gcDistanceTimer.startAt(origin);
        }
        gcDistanceTimer.stopAt(beginNanoTime);
        gcDistanceSecondsSeq.add(TimeUtils.nanosToSecondsDouble(gcDistanceTimer.totalNanos()));
    }

    double minorGcTimeEstimate() {
        return trimmedMinorGcTimeSeconds.davg() + trimmedMinorGcTimeSeconds.dsd();
    }

    double minorGcTimeConservativeEstimate() {
        double davgPlusDsd = trimmedMinorGcTimeSeconds.davg() + trimmedMinorGcTimeSeconds.dsd();
        double avgPlusSd = trimmedMinorGcTimeSeconds.avg() + trimmedMinorGcTimeSeconds.sd();
        return Math.max(davgPlusDsd, avgPlusSd);
    }

    void sampleOldGenUsedBytes(UnsignedWord usedBytes) {
        peakOldUsedBytesSeq.add(UnsignedUtils.toDouble(usedBytes));
    }

    double peakOldGenUsedEstimate() {
        return peakOldUsedBytesSeq.davg() + peakOldUsedBytesSeq.dsd();
    }

    double promotedBytesEstimate() {
        return promotedBytes.davg() + promotedBytes.dsd();
    }

    double promotionRateBytesPerSecEstimate() {
        return promotionRateBytesPerSec.davg() + promotionRateBytesPerSec.dsd();
    }

    /** Conservative estimate to minimize promotion to old-gen. */
    double survivedBytesEstimate() {
        double avgPlusSd = survivedBytes.avg() + survivedBytes.sd();
        double davgPlusDsd = survivedBytes.davg() + survivedBytes.dsd();
        return Math.max(avgPlusSd, davgPlusDsd);
    }

    /** Percent of mutator wall-clock time. */
    double mutatorTimePercent() {
        return 1.0 - gcTimePercent();
    }

    void minorCollectionBegin(long beginNanoTime) {
        minorTimer.reset();
        minorTimer.startAt(beginNanoTime);
        recordGcPauseStartInstant(beginNanoTime);
    }

    void minorCollectionEnd(UnsignedWord edenCapacityBytes) {
        minorTimer.stop();

        double minorPauseInSeconds = TimeUtils.nanosToSecondsDouble(minorTimer.totalNanos());
        double minorPauseInMs = minorPauseInSeconds * 1000;

        recordGcDuration(minorPauseInSeconds);
        trimmedMinorGcTimeSeconds.add(minorPauseInSeconds);

        if (!youngGenPolicyIsReady) {
            /*
             * NOTE that the original code uses GCId::current(), which is incremented for both major
             * and minor collections, contrary to what the following comment says.
             */
            // The policy does not have enough data until at least some
            // young collections have been done.
            youngGenPolicyIsReady = (minorCount >= ADAPTIVE_SIZE_POLICY_READY_THRESHOLD);
        }

        double edenSizeInMbytes = UnsignedUtils.toDouble(edenCapacityBytes) / (1024 * 1024);
        minorPauseYoungEstimator.update(edenSizeInMbytes, minorPauseInMs);
    }
}

/**
 * A ring buffer with fixed size to record the most recent samples of GC duration (minor and major)
 * so that we can calculate mutator-wall-clock-time percentage for the given window.
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/shared/adaptiveSizePolicy.hpp") // actually:jdk-26+25
final class GCSampleRingBuffer {
    private final double[] startInstants = new double[NUM_OF_GC_SAMPLE];
    private final double[] durations = new double[NUM_OF_GC_SAMPLE];
    private double durationSum = 0.0;
    private int sampleIndex = 0;
    private int numOfSamples = 0;

    double durationSum() {
        return durationSum;
    }

    /** Records a GC duration into the ring buffer. */
    void recordSample(double gcDuration) {
        if (numOfSamples < NUM_OF_GC_SAMPLE) {
            numOfSamples++;
        } else {
            assert numOfSamples == NUM_OF_GC_SAMPLE;
            durationSum -= durations[sampleIndex];
        }
        double gcStartInstant = elapsedTime() - gcDuration;
        startInstants[sampleIndex] = gcStartInstant;
        durations[sampleIndex] = gcDuration;
        durationSum += gcDuration;

        sampleIndex = (sampleIndex + 1) % NUM_OF_GC_SAMPLE;
    }

    /** Returns window length, i.e. time from oldest to now. */
    double trimmedWindowDuration() {
        double currentTime = elapsedTime();
        double oldestGcStartInstant;
        if (numOfSamples < NUM_OF_GC_SAMPLE) {
            oldestGcStartInstant = startInstants[0];
        } else {
            oldestGcStartInstant = startInstants[sampleIndex];
        }
        return currentTime - oldestGcStartInstant;
    }

    private static double elapsedTime() {
        return TimeUtils.nanosToSecondsDouble(System.nanoTime());
    }
}
