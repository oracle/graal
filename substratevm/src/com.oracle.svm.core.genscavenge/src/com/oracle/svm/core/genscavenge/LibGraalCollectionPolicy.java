/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.word.Word;

/**
 * A libgraal specific garbage collection policy that responds to GC hints and aggressively
 * expands/shrinks the eden space. It also limits the maximum heap size to 16G as the SVM Serial GC
 * can hit a fatal error if it fails to allocate memory or address space (when compressed references
 * are enabled) during a GC (GR-47622). By restricting the heap to 16G, the latter problem can be
 * avoided. Running out of OS memory will almost certainly cause HotSpot to exit anyway so there
 * less concern about defending against that. It's also expected that 16G is a reasonable limit for
 * a libgraal JIT compilation.
 */
class LibGraalCollectionPolicy extends AdaptiveCollectionPolicy {

    public static final class Options {
        @Option(help = "Ratio of used bytes to total allocated bytes for eden space. Setting it to a smaller value " +
                        "will trade more triggered hinted GCs for less resident set size.") //
        public static final RuntimeOptionKey<Double> UsedEdenProportionThreshold = new RuntimeOptionKey<>(0.75D);
        @Option(help = "Soft upper limit for used eden size. The hinted GC will be performed if the used eden size " +
                        "exceeds this value.") //
        public static final RuntimeOptionKey<Long> ExpectedEdenSize = new RuntimeOptionKey<>(32L * 1024L * 1024L);
    }

    protected static final UnsignedWord INITIAL_HEAP_SIZE = Word.unsigned(64L * 1024L * 1024L);
    protected static final UnsignedWord FULL_GC_BONUS = Word.unsigned(2L * 1024L * 1024L);

    /**
     * See class javadoc for rationale behind this 16G limit.
     */
    protected static final UnsignedWord MAXIMUM_HEAP_SIZE = Word.unsigned(16L * 1024L * 1024L * 1024L);
    protected static final UnsignedWord MAXIMUM_YOUNG_SIZE = Word.unsigned(5L * 1024L * 1024L * 1024L);

    private UnsignedWord sizeBefore = Word.zero();
    private GCCause lastGCCause = null;

    @Override
    public String getName() {
        return "libgraal";
    }

    /**
     * The hinted GC will be triggered only if the used bytes in eden space is greater than
     * {@link Options#ExpectedEdenSize}, or if the ratio of used bytes to total allocated bytes of
     * eden space is above {@link Options#UsedEdenProportionThreshold}. The former condition sets a
     * soft limit for max used eden space; the latter condition is a trade-off between more hinted
     * GCs and more used eden space -- for instance, in libgraal it fits multiple typical-size
     * compilations before actually performing a hinted GC, and the lower ratio is, the resident set
     * size is lower but the hinted GC is more often.
     */
    @Override
    public boolean shouldCollectOnHint(boolean fullGC) {
        guaranteeSizeParametersInitialized();
        UnsignedWord edenUsedBytes = HeapImpl.getAccounting().getEdenUsedBytes();
        if (fullGC) {
            /*
             * For full GC request, we slightly lower the threshold to increase their probability to
             * be performed, as they are supposed to be issued at the lowest memory usage point.
             */
            edenUsedBytes = edenUsedBytes.add(FULL_GC_BONUS);
        }
        return edenUsedBytes.aboveOrEqual(Word.unsigned(Options.ExpectedEdenSize.getValue())) ||
                        (UnsignedUtils.toDouble(edenUsedBytes) / UnsignedUtils.toDouble(edenSize) >= Options.UsedEdenProportionThreshold.getValue());
    }

    @Override
    protected UnsignedWord getInitialHeapSize() {
        return INITIAL_HEAP_SIZE;
    }

    @Override
    protected UnsignedWord getHeapSizeLimit() {
        return UnsignedUtils.min(super.getHeapSizeLimit(), MAXIMUM_HEAP_SIZE);
    }

    @Override
    protected UnsignedWord getYoungSizeLimit(UnsignedWord maxHeap) {
        return UnsignedUtils.min(super.getYoungSizeLimit(maxHeap), MAXIMUM_YOUNG_SIZE);
    }

    @Override
    public void onCollectionBegin(boolean completeCollection, long requestingNanoTime) {
        sizeBefore = GCImpl.getChunkBytes();
        super.onCollectionBegin(completeCollection, requestingNanoTime);
    }

    @Override
    public void onCollectionEnd(boolean completeCollection, GCCause cause) {
        super.onCollectionEnd(completeCollection, cause);
        sizeBefore = Word.zero();
        lastGCCause = cause;
    }

    @Override
    protected boolean shouldUpdateStats(GCCause cause) {
        return cause == GCCause.HintedGC || super.shouldUpdateStats(cause);
    }

    /**
     * The adjusting logic are as follows:
     * 
     * 1. if we hit hinted GC twice in a row, there is no allocation failure in between. If the eden
     * space is previously expanded, we will aggressively shrink the eden space to half, such that
     * the memory footprint will be lower in subsequent execution.
     *
     * 2. if a non-hinted GC cannot reclaim half of used bytes, there is likely a session with
     * continuously high memory consumption (e.g, a huge libgraal compilation). In such case, we
     * will double the eden space to avoid frequent GCs.
     */
    @Override
    protected void computeEdenSpaceSize(boolean completeCollection, GCCause cause) {
        if (cause == GCCause.HintedGC) {
            if (completeCollection && lastGCCause == GCCause.HintedGC) {
                UnsignedWord newEdenSize = UnsignedUtils.max(sizes.initialEdenSize, alignUp(edenSize.unsignedDivide(2)));
                if (edenSize.aboveThan(newEdenSize)) {
                    edenSize = newEdenSize;
                }
            }
        } else {
            UnsignedWord sizeAfter = GCImpl.getChunkBytes();
            if (sizeBefore.notEqual(0) && sizeBefore.belowThan(sizeAfter.multiply(2))) {
                UnsignedWord newEdenSize = UnsignedUtils.min(getMaximumEdenSize(), alignUp(edenSize.multiply(2)));
                if (edenSize.belowThan(newEdenSize)) {
                    edenSize = newEdenSize;
                }
            } else {
                super.computeEdenSpaceSize(completeCollection, cause);
            }
        }
    }
}
