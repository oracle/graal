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

import static com.oracle.svm.core.genscavenge.CollectionPolicy.shouldCollectYoungGenSeparately;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * A garbage collection policy that balances throughput and memory footprint.
 *
 * Much of this is based on HotSpot's ParallelGC adaptive size policy, but without the pause time
 * goals. Many methods in this class have been adapted from classes {@code PSAdaptiveSizePolicy} and
 * its base class {@code AdaptiveSizePolicy}. Method and variable names have been kept mostly the
 * same for comparability.
 */
class AdaptiveCollectionPolicy extends AbstractCollectionPolicy {

    /*
     * Constants that can be made options if desirable. These are -XX options in HotSpot, refer to
     * their descriptions for details. The values are HotSpot defaults unless labeled otherwise.
     *
     * Don't change these values individually without carefully going over their occurrences in
     * HotSpot source code, there are dependencies between them that are not handled in our code.
     */
    private static final int ADAPTIVE_TIME_WEIGHT = DEFAULT_TIME_WEIGHT;
    private static final int ADAPTIVE_SIZE_POLICY_READY_THRESHOLD = 5;
    private static final int ADAPTIVE_SIZE_DECREMENT_SCALE_FACTOR = 4;
    private static final int ADAPTIVE_SIZE_POLICY_WEIGHT = 10;
    private static final boolean USE_ADAPTIVE_SIZE_POLICY_WITH_SYSTEM_GC = false;
    private static final boolean USE_ADAPTIVE_SIZE_DECAY_MAJOR_GC_COST = true;
    private static final double ADAPTIVE_SIZE_MAJOR_GC_DECAY_TIME_SCALE = 10;
    private static final boolean USE_ADAPTIVE_SIZE_POLICY_FOOTPRINT_GOAL = true;
    private static final int THRESHOLD_TOLERANCE = 10;
    private static final int SURVIVOR_PADDING = 3;
    private static final int INITIAL_TENURING_THRESHOLD = 7;
    private static final int PROMOTED_PADDING = 3;
    private static final int TENURED_GENERATION_SIZE_SUPPLEMENT_DECAY = 2;
    private static final int YOUNG_GENERATION_SIZE_SUPPLEMENT_DECAY = 8;
    private static final int PAUSE_PADDING = 1;
    /**
     * Ratio of mutator wall-clock time to GC wall-clock time. HotSpot's default is 99, i.e.
     * spending 1% of time in GC. We set it to 19, i.e. 5%, to prefer a small footprint.
     */
    private static final int GC_TIME_RATIO = 19;
    /**
     * Maximum size increment step percentages. We reduce them from HotSpot's default of 20 to avoid
     * growing the heap too eagerly, and to enable {@linkplain #ADAPTIVE_SIZE_USE_COST_ESTIMATORS
     * cost estimators} to resize the heap in smaller steps which might yield improved throughput
     * when larger steps do not.
     */
    private static final int YOUNG_GENERATION_SIZE_INCREMENT = 10;
    private static final int TENURED_GENERATION_SIZE_INCREMENT = 10;
    /*
     * Supplements to accelerate the expansion of the heap at startup. We do not use them in favor
     * of a small footprint.
     */
    private static final int YOUNG_GENERATION_SIZE_SUPPLEMENT = 0; // HotSpot default: 80
    private static final int TENURED_GENERATION_SIZE_SUPPLEMENT = 0; // HotSpot default: 80
    /**
     * Use least square fitting to estimate if increasing heap sizes will significantly improve
     * throughput. This is intended to limit memory usage once throughput cannot be increased much
     * more, for example when the application is heavily multi-threaded and our single-threaded
     * collector cannot reach the throughput goal. We use a reciprocal function with exponential
     * discounting of old data points, unlike HotSpot's AdaptiveSizeThroughPutPolicy option
     * (disabled by default) which uses linear least-square fitting without discounting.
     */
    private static final boolean ADAPTIVE_SIZE_USE_COST_ESTIMATORS = true;
    /** Number of space size adjustments before cost estimators are used. HotSpot default: 20. */
    private static final int ADAPTIVE_SIZE_POLICY_INITIALIZING_STEPS = ADAPTIVE_SIZE_POLICY_READY_THRESHOLD;
    /**
     * The minimum estimated decrease in {@link #gcCost()} in percent to decide in favor of
     * expanding a space by 1% of the combined size of {@link #edenSize} and {@link #promoSize}.
     */
    private static final double ADAPTIVE_SIZE_ESTIMATOR_MIN_TOTAL_SIZE_COST_TRADEOFF = 0.5;
    /** The effective number of most recent data points used by estimator (exponential decay). */
    private static final double ADAPTIVE_SIZE_COST_ESTIMATORS_HISTORY_LENGTH = 12;
    /** Threshold for triggering a complete collection after repeated minor collections. */
    private static final int CONSECUTIVE_MINOR_TO_MAJOR_COLLECTION_PAUSE_TIME_RATIO = 2;
    /**
     * When the GC cost of a generation is above this value, its estimator is ignored and sizes are
     * increased to avoid starving the mutator.
     */
    private static final double ADAPTIVE_SIZE_COST_ESTIMATOR_GC_COST_LIMIT = 0.5;

    private static final int INITIAL_NEW_RATIO = 1; // same size for young and old generation

    /* Constants derived from other constants. */
    private static final double THROUGHPUT_GOAL = 1.0 - 1.0 / (1.0 + GC_TIME_RATIO);
    private static final double THRESHOLD_TOLERANCE_PERCENT = 1.0 + THRESHOLD_TOLERANCE / 100.0;

    private final Timer minorTimer = new Timer("minor/between minor");
    private final AdaptiveWeightedAverage avgMinorGcCost = new AdaptiveWeightedAverage(ADAPTIVE_TIME_WEIGHT);
    private final AdaptivePaddedAverage avgMinorPause = new AdaptivePaddedAverage(ADAPTIVE_TIME_WEIGHT, PAUSE_PADDING);
    private final AdaptivePaddedAverage avgSurvived = new AdaptivePaddedAverage(ADAPTIVE_SIZE_POLICY_WEIGHT, SURVIVOR_PADDING);
    private final AdaptivePaddedAverage avgPromoted = new AdaptivePaddedAverage(ADAPTIVE_SIZE_POLICY_WEIGHT, PROMOTED_PADDING, true);
    private final ReciprocalLeastSquareFit minorCostEstimator = new ReciprocalLeastSquareFit(ADAPTIVE_SIZE_COST_ESTIMATORS_HISTORY_LENGTH);
    private long minorCount;
    private long latestMinorMutatorIntervalNanos;
    private boolean youngGenPolicyIsReady;
    private UnsignedWord youngGenSizeIncrementSupplement = WordFactory.unsigned(YOUNG_GENERATION_SIZE_SUPPLEMENT);
    private long youngGenChangeForMinorThroughput;
    private int minorCountSinceMajorCollection;

    private final Timer majorTimer = new Timer("major/between major");
    private final AdaptiveWeightedAverage avgMajorGcCost = new AdaptiveWeightedAverage(ADAPTIVE_TIME_WEIGHT);
    private final AdaptivePaddedAverage avgMajorPause = new AdaptivePaddedAverage(ADAPTIVE_TIME_WEIGHT, PAUSE_PADDING);
    private final AdaptiveWeightedAverage avgMajorIntervalSeconds = new AdaptiveWeightedAverage(ADAPTIVE_TIME_WEIGHT);
    private final AdaptiveWeightedAverage avgOldLive = new AdaptiveWeightedAverage(ADAPTIVE_SIZE_POLICY_WEIGHT);
    private final ReciprocalLeastSquareFit majorCostEstimator = new ReciprocalLeastSquareFit(ADAPTIVE_SIZE_COST_ESTIMATORS_HISTORY_LENGTH);
    private long majorCount;
    private UnsignedWord oldGenSizeIncrementSupplement = WordFactory.unsigned(TENURED_GENERATION_SIZE_SUPPLEMENT);
    private long latestMajorMutatorIntervalNanos;
    private boolean oldSizeExceededInPreviousCollection;
    private long oldGenChangeForMajorThroughput;

    AdaptiveCollectionPolicy() {
        super(INITIAL_NEW_RATIO, INITIAL_TENURING_THRESHOLD);
    }

    @Override
    public String getName() {
        return "adaptive";
    }

    @Override
    public boolean shouldCollectCompletely(boolean followingIncrementalCollection) { // should_{attempt_scavenge,full_GC}
        guaranteeSizeParametersInitialized();

        if (!followingIncrementalCollection && shouldCollectYoungGenSeparately(!SerialGCOptions.useCompactingOldGen())) {
            /*
             * With a copying collector, default to always doing an incremental collection first
             * because we expect most of the objects in the young generation to be garbage, and we
             * can reuse their leftover chunks for copying the live objects in the old generation
             * with fewer allocations. With a compacting collector, there is no benefit.
             */
            return false;
        }
        if (followingIncrementalCollection && oldSizeExceededInPreviousCollection) {
            /*
             * In the preceding incremental collection, we promoted objects to the old generation
             * beyond its current capacity to avoid a promotion failure, but due to the chunked
             * nature of our heap, we should still be within the maximum heap size. Follow up with a
             * full collection during which we reclaim enough space or expand the old generation.
             */
            return true;
        }
        if (minorCountSinceMajorCollection * avgMinorPause.getAverage() >= CONSECUTIVE_MINOR_TO_MAJOR_COLLECTION_PAUSE_TIME_RATIO * avgMajorPause.getPaddedAverage()) {
            /*
             * When we do many incremental collections in a row because they reclaim sufficient
             * space, still trigger a complete collection when reaching a cumulative pause time
             * threshold so that garbage in the old generation can also be reclaimed.
             */
            return true;
        }

        UnsignedWord youngUsed = HeapImpl.getHeapImpl().getYoungGeneration().getChunkBytes();
        UnsignedWord oldUsed = HeapImpl.getHeapImpl().getOldGeneration().getChunkBytes();

        /*
         * If the remaining free space in the old generation is less than what is expected to be
         * needed by the next collection, do a full collection now.
         */
        UnsignedWord averagePromoted = UnsignedUtils.fromDouble(avgPromoted.getPaddedAverage());
        UnsignedWord promotionEstimate = UnsignedUtils.min(averagePromoted, youngUsed);
        UnsignedWord oldFree = oldSize.subtract(oldUsed);
        return promotionEstimate.aboveThan(oldFree);
    }

    private void updateAverages(boolean isSurvivorOverflow, UnsignedWord survivedChunkBytes, UnsignedWord promotedChunkBytes) {
        UnsignedWord survived = survivedChunkBytes;
        if (isSurvivorOverflow) {
            survived = survived.add(promotedChunkBytes); // guess
        }
        avgSurvived.sample(survived);

        avgPromoted.sample(promotedChunkBytes);
    }

    private void computeSurvivorSpaceSizeAndThreshold(boolean isSurvivorOverflow, UnsignedWord survivorLimit) {
        if (!youngGenPolicyIsReady) {
            return;
        }

        boolean incrTenuringThreshold = false;
        boolean decrTenuringThreshold = false;
        if (!isSurvivorOverflow) {
            /*
             * We use the tenuring threshold to equalize the cost of major and minor collections.
             *
             * THRESHOLD_TOLERANCE_PERCENT is used to indicate how sensitive the tenuring threshold
             * is to differences in cost between the collection types.
             */
            if (minorGcCost() > majorGcCost() * THRESHOLD_TOLERANCE_PERCENT) {
                decrTenuringThreshold = true;
            } else if (majorGcCost() > minorGcCost() * THRESHOLD_TOLERANCE_PERCENT) {
                incrTenuringThreshold = true;
            }
        } else {
            decrTenuringThreshold = true;
        }

        UnsignedWord targetSize = minSpaceSize(alignUp(UnsignedUtils.fromDouble(avgSurvived.getPaddedAverage())));
        if (targetSize.aboveThan(survivorLimit)) {
            targetSize = survivorLimit;
            decrTenuringThreshold = true;
        }
        survivorSize = targetSize;

        if (decrTenuringThreshold) {
            tenuringThreshold = Math.max(tenuringThreshold - 1, 1);
        } else if (incrTenuringThreshold) {
            tenuringThreshold = Math.min(tenuringThreshold + 1, HeapParameters.getMaxSurvivorSpaces() + 1);
        }
    }

    protected void computeEdenSpaceSize(@SuppressWarnings("unused") boolean completeCollection, @SuppressWarnings("unused") GCCause cause) {
        boolean expansionReducesCost = true; // general assumption
        if (shouldUseEstimator(youngGenChangeForMinorThroughput, minorGcCost())) {
            expansionReducesCost = expansionSignificantlyReducesTotalCost(minorCostEstimator, edenSize, majorGcCost(), promoSize);
            /*
             * Note that if the estimator thinks expanding does not lead to significant improvement,
             * shrink so to not get stuck in a supposed optimum and to keep collecting data points.
             */
        }

        UnsignedWord desiredEdenSize = edenSize;
        if (expansionReducesCost && adjustedMutatorCost() < THROUGHPUT_GOAL && gcCost() > 0) {
            // from adjust_eden_for_throughput():
            UnsignedWord edenHeapDelta = edenIncrementWithSupplementAlignedUp(edenSize);
            double scaleByRatio = minorGcCost() / gcCost();
            assert scaleByRatio >= 0 && scaleByRatio <= 1;
            UnsignedWord scaledEdenHeapDelta = UnsignedUtils.fromDouble(scaleByRatio * UnsignedUtils.toDouble(edenHeapDelta));

            desiredEdenSize = alignUp(desiredEdenSize.add(scaledEdenHeapDelta));
            desiredEdenSize = UnsignedUtils.max(desiredEdenSize, edenSize);
            youngGenChangeForMinorThroughput++;
        }
        if (!expansionReducesCost || (USE_ADAPTIVE_SIZE_POLICY_FOOTPRINT_GOAL && youngGenPolicyIsReady && adjustedMutatorCost() >= THROUGHPUT_GOAL)) {
            UnsignedWord desiredSum = edenSize.add(promoSize);
            desiredEdenSize = adjustEdenForFootprint(edenSize, desiredSum);
        }
        assert isAligned(desiredEdenSize);
        desiredEdenSize = minSpaceSize(desiredEdenSize);

        UnsignedWord edenLimit = getMaximumEdenSize();
        if (desiredEdenSize.aboveThan(edenLimit)) {
            /*
             * If the policy says to get a larger eden but is hitting the limit, don't decrease
             * eden. This can lead to a general drifting down of the eden size. Let the tenuring
             * calculation push more into the old gen.
             */
            desiredEdenSize = UnsignedUtils.max(edenLimit, edenSize);
        }
        edenSize = desiredEdenSize;
    }

    private static boolean shouldUseEstimator(long genChangeForThroughput, double cost) {
        return ADAPTIVE_SIZE_USE_COST_ESTIMATORS && genChangeForThroughput > ADAPTIVE_SIZE_POLICY_INITIALIZING_STEPS && cost <= ADAPTIVE_SIZE_COST_ESTIMATOR_GC_COST_LIMIT;
    }

    private static boolean expansionSignificantlyReducesTotalCost(ReciprocalLeastSquareFit estimator, UnsignedWord size, double otherCost, UnsignedWord otherSize) {
        double totalSize = UnsignedUtils.toDouble(size.add(otherSize));
        double x0 = UnsignedUtils.toDouble(size);
        double deltax = (1.01 - 1) * x0;
        if (deltax == 0 || totalSize == 0) { // division by zero below
            return true; // general assumption for space expansion
        }
        double y0 = estimator.estimate(x0) + otherCost;
        double y1 = y0 * (1 - deltax / totalSize * ADAPTIVE_SIZE_ESTIMATOR_MIN_TOTAL_SIZE_COST_TRADEOFF);
        double minSlope = (y1 - y0) / deltax;

        double estimatedSlope = estimator.getSlope(x0);
        return estimatedSlope <= minSlope;
    }

    private static UnsignedWord adjustEdenForFootprint(UnsignedWord curEden, UnsignedWord desiredSum) {
        assert curEden.belowOrEqual(desiredSum);

        UnsignedWord change = edenDecrement(curEden);
        change = scaleDown(change, curEden, desiredSum);

        UnsignedWord reducedSize = curEden.subtract(change);
        assert reducedSize.belowOrEqual(curEden);
        return alignDown(reducedSize);
    }

    private static UnsignedWord scaleDown(UnsignedWord change, UnsignedWord part, UnsignedWord total) {
        assert part.belowOrEqual(total);
        UnsignedWord reducedChange = change;
        if (total.aboveThan(0)) {
            double fraction = UnsignedUtils.toDouble(part) / UnsignedUtils.toDouble(total);
            reducedChange = UnsignedUtils.fromDouble(fraction * UnsignedUtils.toDouble(change));
        }
        assert reducedChange.belowOrEqual(change);
        return reducedChange;
    }

    private static UnsignedWord edenDecrement(UnsignedWord curEden) {
        return spaceIncrement(curEden, WordFactory.unsigned(YOUNG_GENERATION_SIZE_INCREMENT))
                        .unsignedDivide(ADAPTIVE_SIZE_DECREMENT_SCALE_FACTOR);
    }

    private double adjustedMutatorCost() {
        double cost = 1 - decayingGcCost();
        assert cost >= 0;
        return cost;
    }

    private double decayingGcCost() { // decaying_gc_cost and decaying_major_gc_cost
        double decayedMajorGcCost = majorGcCost();
        double avgMajorInterval = avgMajorIntervalSeconds.getAverage();
        if (USE_ADAPTIVE_SIZE_DECAY_MAJOR_GC_COST && ADAPTIVE_SIZE_MAJOR_GC_DECAY_TIME_SCALE > 0 && avgMajorInterval > 0) {
            double secondsSinceMajor = secondsSinceMajorGc();
            if (secondsSinceMajor > 0 && secondsSinceMajor > ADAPTIVE_SIZE_MAJOR_GC_DECAY_TIME_SCALE * avgMajorInterval) {
                double decayed = decayedMajorGcCost * (ADAPTIVE_SIZE_MAJOR_GC_DECAY_TIME_SCALE * avgMajorInterval) / secondsSinceMajor;
                decayedMajorGcCost = Math.min(decayedMajorGcCost, decayed);
            }
        }
        return Math.min(1, decayedMajorGcCost + minorGcCost());
    }

    private double minorGcCost() {
        return Math.max(0, avgMinorGcCost.getAverage());
    }

    private double majorGcCost() {
        return Math.max(0, avgMajorGcCost.getAverage());
    }

    private double gcCost() {
        /*
         * Because we're dealing with averages, gcCost() can be larger than 1.0 if just the sum of
         * the minor cost and the major cost is used. Worse than that is the fact that the minor
         * cost and the major cost each tend toward 1.0 in the extreme of high GC costs. Limit the
         * value of gcCost() to 1.0 so that the mutator cost stays non-negative.
         */
        double cost = Math.min(1, minorGcCost() + majorGcCost());
        assert cost >= 0 : "Both minor and major costs are non-negative";
        return cost;
    }

    private UnsignedWord edenIncrementWithSupplementAlignedUp(UnsignedWord curEden) {
        return alignUp(spaceIncrement(curEden, youngGenSizeIncrementSupplement.add(YOUNG_GENERATION_SIZE_INCREMENT)));
    }

    private static UnsignedWord spaceIncrement(UnsignedWord curSize, UnsignedWord percentChange) { // {eden,promo}_increment
        return curSize.unsignedDivide(100).multiply(percentChange);
    }

    private double secondsSinceMajorGc() { // time_since_major_gc
        return TimeUtils.nanosToSecondsDouble(System.nanoTime() - majorTimer.getOpenedTime());
    }

    @Override
    public void onCollectionBegin(boolean completeCollection, long requestingNanoTime) { // {major,minor}_collection_begin
        Timer timer = completeCollection ? majorTimer : minorTimer;
        timer.closeAt(requestingNanoTime);
        if (completeCollection) {
            latestMajorMutatorIntervalNanos = timer.getMeasuredNanos();
        } else {
            latestMinorMutatorIntervalNanos = timer.getMeasuredNanos();
        }

        timer.reset();
        timer.open(); // measure collection pause

        super.onCollectionBegin(completeCollection, requestingNanoTime);
    }

    @Override
    public void onCollectionEnd(boolean completeCollection, GCCause cause) { // {major,minor}_collection_end
        Timer timer = completeCollection ? majorTimer : minorTimer;
        timer.close();

        if (completeCollection) {
            updateCollectionEndAverages(avgMajorGcCost, avgMajorPause, majorCostEstimator, avgMajorIntervalSeconds,
                            cause, latestMajorMutatorIntervalNanos, timer.getMeasuredNanos(), promoSize);
            majorCount++;
            minorCountSinceMajorCollection = 0;

        } else {
            updateCollectionEndAverages(avgMinorGcCost, avgMinorPause, minorCostEstimator, null,
                            cause, latestMinorMutatorIntervalNanos, timer.getMeasuredNanos(), edenSize);
            minorCount++;
            minorCountSinceMajorCollection++;

            if (minorCount >= ADAPTIVE_SIZE_POLICY_READY_THRESHOLD) {
                youngGenPolicyIsReady = true;
            }
        }

        timer.reset();
        timer.open();

        GCAccounting accounting = GCImpl.getAccounting();
        UnsignedWord oldLive = accounting.getOldGenerationAfterChunkBytes();
        oldSizeExceededInPreviousCollection = oldLive.aboveThan(oldSize);

        if (!completeCollection) {
            /*
             * Update the averages that survivor space and tenured space sizes are derived from.
             * Note that we use chunk bytes (not object bytes) for the survivors. This is because
             * they are kept in many spaces (one for each age), which potentially results in
             * significant overhead from chunks that may only be partially filled, especially when
             * the heap is small. Using chunk bytes here ensures that the needed survivor capacity
             * is not underestimated.
             */
            boolean survivorOverflow = accounting.hasLastIncrementalCollectionOverflowedSurvivors();
            UnsignedWord survivedChunkBytes = HeapImpl.getHeapImpl().getYoungGeneration().getSurvivorChunkBytes();
            UnsignedWord tenuredChunkBytes = accounting.getLastIncrementalCollectionPromotedChunkBytes();
            updateAverages(survivorOverflow, survivedChunkBytes, tenuredChunkBytes);

            computeSurvivorSpaceSizeAndThreshold(survivorOverflow, sizes.maxSurvivorSize());
        }
        if (shouldUpdateStats(cause)) {
            computeEdenSpaceSize(completeCollection, cause);
            if (completeCollection) {
                computeOldGenSpaceSize(oldLive);
            }
            decaySupplementalGrowth(completeCollection);
        }
    }

    private void computeOldGenSpaceSize(UnsignedWord oldLive) { // compute_old_gen_free_space
        avgOldLive.sample(oldLive);

        // NOTE: if maxOldSize shrunk and difference is negative, unsigned conversion results in 0
        UnsignedWord promoLimit = UnsignedUtils.fromDouble(UnsignedUtils.toDouble(sizes.maxOldSize()) - avgOldLive.getAverage());
        promoLimit = alignDown(UnsignedUtils.max(promoSize, promoLimit));

        boolean expansionReducesCost = true; // general assumption
        if (shouldUseEstimator(oldGenChangeForMajorThroughput, majorGcCost())) {
            expansionReducesCost = expansionSignificantlyReducesTotalCost(majorCostEstimator, promoSize, minorGcCost(), edenSize);
            /*
             * Note that if the estimator thinks expanding does not lead to significant improvement,
             * shrink so to not get stuck in a supposed optimum and to keep collecting data points.
             */
        }

        UnsignedWord desiredPromoSize = promoSize;
        if (expansionReducesCost && adjustedMutatorCost() < THROUGHPUT_GOAL && gcCost() > 0) {
            // from adjust_promo_for_throughput():
            UnsignedWord promoHeapDelta = promoIncrementWithSupplementAlignedUp(promoSize);
            double scaleByRatio = majorGcCost() / gcCost();
            assert scaleByRatio >= 0 && scaleByRatio <= 1;
            UnsignedWord scaledPromoHeapDelta = UnsignedUtils.fromDouble(scaleByRatio * UnsignedUtils.toDouble(promoHeapDelta));

            desiredPromoSize = alignUp(promoSize.add(scaledPromoHeapDelta));
            desiredPromoSize = UnsignedUtils.max(desiredPromoSize, promoSize);
            oldGenChangeForMajorThroughput++;
        }
        if (!expansionReducesCost || (USE_ADAPTIVE_SIZE_POLICY_FOOTPRINT_GOAL && youngGenPolicyIsReady && adjustedMutatorCost() >= THROUGHPUT_GOAL)) {
            UnsignedWord desiredSum = edenSize.add(promoSize);
            desiredPromoSize = adjustPromoForFootprint(promoSize, desiredSum);
        }
        assert isAligned(desiredPromoSize);
        desiredPromoSize = minSpaceSize(desiredPromoSize);

        desiredPromoSize = UnsignedUtils.min(desiredPromoSize, promoLimit);
        promoSize = desiredPromoSize;

        // from PSOldGen::resize
        UnsignedWord desiredFreeSpace = calculatedOldFreeSizeInBytes();
        UnsignedWord desiredOldSize = alignUp(oldLive.add(desiredFreeSpace));
        oldSize = UnsignedUtils.clamp(desiredOldSize, minSpaceSize(), sizes.maxOldSize());
    }

    UnsignedWord calculatedOldFreeSizeInBytes() {
        return UnsignedUtils.fromDouble(UnsignedUtils.toDouble(promoSize) + avgPromoted.getPaddedAverage());
    }

    private static UnsignedWord adjustPromoForFootprint(UnsignedWord curPromo, UnsignedWord desiredSum) {
        assert curPromo.belowOrEqual(desiredSum);

        UnsignedWord change = promoDecrement(curPromo);
        change = scaleDown(change, curPromo, desiredSum);

        UnsignedWord reducedSize = curPromo.subtract(change);
        assert reducedSize.belowOrEqual(curPromo);
        return alignDown(reducedSize);
    }

    private static UnsignedWord promoDecrement(UnsignedWord curPromo) {
        return promoIncrement(curPromo).unsignedDivide(ADAPTIVE_SIZE_DECREMENT_SCALE_FACTOR);
    }

    private static UnsignedWord promoIncrement(UnsignedWord curPromo) {
        return spaceIncrement(curPromo, WordFactory.unsigned(TENURED_GENERATION_SIZE_INCREMENT));
    }

    private UnsignedWord promoIncrementWithSupplementAlignedUp(UnsignedWord curPromo) {
        return alignUp(spaceIncrement(curPromo, oldGenSizeIncrementSupplement.add(TENURED_GENERATION_SIZE_INCREMENT)));
    }

    private void decaySupplementalGrowth(boolean completeCollection) {
        // Decay the supplement growth factor even if it is not used. It is only meant to give a
        // boost to the initial growth and if it is not used, then it was not needed.
        if (completeCollection) {
            // Don't wait for the threshold value for the major collections. If here, the
            // supplemental growth term was used and should decay.
            if (majorCount % TENURED_GENERATION_SIZE_SUPPLEMENT_DECAY == 0) {
                oldGenSizeIncrementSupplement = oldGenSizeIncrementSupplement.unsignedShiftRight(1);
            }
        } else {
            if (minorCount >= ADAPTIVE_SIZE_POLICY_READY_THRESHOLD && minorCount % YOUNG_GENERATION_SIZE_SUPPLEMENT_DECAY == 0) {
                youngGenSizeIncrementSupplement = youngGenSizeIncrementSupplement.unsignedShiftRight(1);
            }
        }
    }

    protected boolean shouldUpdateStats(GCCause cause) { // should_update_{eden,promo}_stats
        return cause == GenScavengeGCCause.OnAllocation || USE_ADAPTIVE_SIZE_POLICY_WITH_SYSTEM_GC;
    }

    private void updateCollectionEndAverages(AdaptiveWeightedAverage costAverage, AdaptivePaddedAverage pauseAverage, ReciprocalLeastSquareFit costEstimator,
                    AdaptiveWeightedAverage intervalSeconds, GCCause cause, long mutatorNanos, long pauseNanos, UnsignedWord sizeBytes) {
        if (shouldUpdateStats(cause)) {
            double cost = 0;
            double mutatorInSeconds = TimeUtils.nanosToSecondsDouble(mutatorNanos);
            double pauseInSeconds = TimeUtils.nanosToSecondsDouble(pauseNanos);
            pauseAverage.sample(pauseInSeconds);
            if (mutatorInSeconds > 0 && pauseInSeconds > 0) {
                double intervalInSeconds = mutatorInSeconds + pauseInSeconds;
                cost = pauseInSeconds / intervalInSeconds;
                costAverage.sample(cost);
                if (intervalSeconds != null) {
                    intervalSeconds.sample(intervalInSeconds);
                }
            }
            costEstimator.sample(UnsignedUtils.toDouble(sizeBytes), cost);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected long gcCount() {
        return minorCount + majorCount;
    }
}
