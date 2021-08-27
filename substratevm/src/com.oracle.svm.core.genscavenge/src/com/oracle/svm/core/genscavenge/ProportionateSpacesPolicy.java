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

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.util.UnsignedUtils;

/** A port of HotSpot's SerialGC size policy. */
final class ProportionateSpacesPolicy extends AbstractCollectionPolicy {

    /*
     * Constants that can be made options if desirable. These are -XX options in HotSpot, refer to
     * their descriptions for details. The values are HotSpot defaults unless labeled otherwise.
     *
     * Don't change these values individually without carefully going over their occurrences in
     * HotSpot source code, there are dependencies between them that are not handled in our code.
     */
    static final int MIN_HEAP_FREE_RATIO = 40;
    static final int MAX_HEAP_FREE_RATIO = 70;
    static final boolean SHRINK_HEAP_IN_STEPS = true;
    static final int SURVIVOR_RATIO = 8;
    static final int MAX_TENURING_THRESHOLD = 15;
    static final int TARGET_SURVIVOR_RATIO = 50;

    private int totalCollections;
    private boolean oldSizeExceededInPreviousCollection;
    private int shrinkFactor;

    ProportionateSpacesPolicy() {
        super(MAX_TENURING_THRESHOLD);
    }

    @Override
    public String getName() {
        return "proportionate";
    }

    @Override
    public boolean shouldCollectCompletely(boolean followingIncrementalCollection) {
        guaranteeSizeParametersInitialized();

        if (followingIncrementalCollection && oldSizeExceededInPreviousCollection) {
            /*
             * We promoted objects to the old generation beyond its current capacity to avoid a
             * promotion failure, but due to the chunked nature of our heap, we should still be
             * within the maximum heap size. Follow up with a full collection during which we either
             * reclaim enough space or expand the old generation.
             */
            return true;
        }
        return false;
    }

    @Override
    public void onCollectionBegin(boolean completeCollection) {
    }

    @Override
    public void onCollectionEnd(boolean completeCollection, GCCause cause) {
        UnsignedWord oldLive = GCImpl.getGCImpl().getAccounting().getOldGenerationAfterChunkBytes();
        oldSizeExceededInPreviousCollection = oldLive.aboveThan(oldSize);

        boolean resizeOldOnlyForPromotions = !completeCollection;
        computeNewOldGenSize(resizeOldOnlyForPromotions);
        computeNewYoungGenSize();
        adjustDesiredTenuringThreshold();

        totalCollections++;
    }

    private void adjustDesiredTenuringThreshold() { // DefNewGeneration::adjust_desired_tenuring_threshold
        // Set the desired survivor size to half the real survivor space
        UnsignedWord desiredSurvivorSize = UnsignedUtils.fromDouble(UnsignedUtils.toDouble(survivorSize) * TARGET_SURVIVOR_RATIO / 100);

        // AgeTable::compute_tenuring_threshold
        YoungGeneration youngGen = HeapImpl.getHeapImpl().getYoungGeneration();
        UnsignedWord total = WordFactory.zero();
        int i;
        for (i = 0; i < HeapParameters.getMaxSurvivorSpaces(); i++) {
            Space space = youngGen.getSurvivorFromSpaceAt(0);
            total = total.add(space.getChunkBytes());
            if (total.aboveThan(desiredSurvivorSize)) {
                break;
            }
            i++;
        }

        tenuringThreshold = Math.min(i + 1, MAX_TENURING_THRESHOLD);
    }

    private void computeNewOldGenSize(boolean resizeOnlyForPromotions) { // CardGeneration::compute_new_size
        UnsignedWord capacityAtPrologue = oldSize;
        UnsignedWord usedAfterGc = GCImpl.getGCImpl().getAccounting().getOldGenerationAfterChunkBytes();
        if (oldSize.belowThan(usedAfterGc)) {
            oldSize = usedAfterGc;
        }
        if (resizeOnlyForPromotions) {
            return;
        }

        int currentShrinkFactor = shrinkFactor;
        shrinkFactor = 0;

        double minimumFreePercentage = MIN_HEAP_FREE_RATIO / 100.0;
        double maximumUsedPercentage = 1 - minimumFreePercentage;

        UnsignedWord minimumDesiredCapacity = UnsignedUtils.fromDouble(UnsignedUtils.toDouble(usedAfterGc) / maximumUsedPercentage);
        minimumDesiredCapacity = UnsignedUtils.max(minimumDesiredCapacity, sizes.initialOldSize());

        if (oldSize.belowThan(minimumDesiredCapacity)) {
            oldSize = alignUp(minimumDesiredCapacity);
            return;
        }

        UnsignedWord maxShrinkBytes = oldSize.subtract(minimumDesiredCapacity);
        UnsignedWord shrinkBytes = WordFactory.zero();
        if (MAX_HEAP_FREE_RATIO < 100) {
            double maximumFreePercentage = MAX_HEAP_FREE_RATIO / 100.0;
            double minimumUsedPercentage = 1 - maximumFreePercentage;
            UnsignedWord maximumDesiredCapacity = UnsignedUtils.fromDouble(UnsignedUtils.toDouble(usedAfterGc) / minimumUsedPercentage);
            maximumDesiredCapacity = UnsignedUtils.max(maximumDesiredCapacity, sizes.initialOldSize());
            assert minimumDesiredCapacity.belowOrEqual(maximumDesiredCapacity);

            if (oldSize.aboveThan(maximumDesiredCapacity)) {
                shrinkBytes = oldSize.subtract(maximumDesiredCapacity);
                if (SHRINK_HEAP_IN_STEPS) {
                    /*
                     * We don't want to shrink all the way back to initSize if people call
                     * System.gc(), because some programs do that between "phases" and then we'd
                     * just have to grow the heap up again for the next phase. So we damp the
                     * shrinking: 0% on the first call, 10% on the second call, 40% on the third
                     * call, and 100% by the fourth call. But if we recompute size without
                     * shrinking, it goes back to 0%.
                     */
                    shrinkBytes = shrinkBytes.unsignedDivide(100).multiply(currentShrinkFactor);
                    if (currentShrinkFactor == 0) {
                        shrinkFactor = 10;
                    } else {
                        shrinkFactor = Math.min(currentShrinkFactor * 4, 100);
                    }
                }
                assert shrinkBytes.belowOrEqual(maxShrinkBytes);
            }
        }

        if (oldSize.aboveThan(capacityAtPrologue)) {
            /*
             * We might have expanded for promotions, in which case we might want to take back that
             * expansion if there's room after GC. That keeps us from stretching the heap with
             * promotions when there's plenty of room.
             */
            UnsignedWord expansionForPromotion = oldSize.subtract(capacityAtPrologue);
            expansionForPromotion = UnsignedUtils.min(expansionForPromotion, maxShrinkBytes);
            shrinkBytes = UnsignedUtils.max(shrinkBytes, expansionForPromotion);
        }

        if (shrinkBytes.aboveThan(MIN_HEAP_FREE_RATIO)) {
            oldSize = oldSize.subtract(shrinkBytes);
        }
    }

    private void computeNewYoungGenSize() { // DefNewGeneration::compute_new_size
        UnsignedWord desiredNewSize = oldSize.unsignedDivide(NEW_RATIO);
        desiredNewSize = UnsignedUtils.clamp(desiredNewSize, sizes.initialYoungSize(), sizes.maxYoungSize);

        // DefNewGeneration::compute_space_boundaries, DefNewGeneration::compute_survivor_size
        survivorSize = minSpaceSize(alignDown(desiredNewSize.unsignedDivide(SURVIVOR_RATIO)));
        UnsignedWord desiredEdenSize = WordFactory.zero();
        if (desiredNewSize.aboveThan(survivorSize.multiply(2))) {
            desiredEdenSize = desiredNewSize.subtract(survivorSize.multiply(2));
        }
        edenSize = minSpaceSize(alignDown(desiredEdenSize));
        assert edenSize.aboveThan(0) && survivorSize.belowOrEqual(edenSize);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected long gcCount() {
        return totalCollections;
    }
}
