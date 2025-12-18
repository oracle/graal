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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.genscavenge.AbstractCollectionPolicy.isAligned;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

/**
 * Once a {@link RawSizeParameters} struct is visible to other threads (see {@link #update}), it may
 * only be accessed by {@link Uninterruptible} code or when the VM is at a safepoint. This is
 * necessary to prevent use-after-free errors because no longer needed {@link RawSizeParameters}
 * structs may be freed during a GC.
 * <p>
 * When the VM is at a safepoint, the GC may directly update some of the values in the latest
 * {@link RawSizeParameters} (see setters below).
 */
final class SizeParameters {
    private static final String ACCESS_RAW_SIZE_PARAMETERS = "Prevent that RawSizeParameters are freed.";

    private volatile RawSizeParameters sizes;

    @Platforms(Platform.HOSTED_ONLY.class)
    SizeParameters() {
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInitialized() {
        /*
         * This only checks for non-null and doesn't access any values in the struct, so inlining
         * into interruptible code is allowed.
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
    public UnsignedWord getHeapSize() {
        assert isInitialized();
        assert VMOperation.isGCInProgress() : "use only during GC";

        return sizes.getHeapSize();
    }

    /** The caller needs to ensure that . */
    @Uninterruptible(reason = ACCESS_RAW_SIZE_PARAMETERS)
    void update(RawSizeParameters newValuesOnStack) {
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
         * Publish the new struct via a volatile store. Once the data is published, other threads
         * need to see a fully initialized struct right away. This is guaranteed by the implicit
         * STORE_STORE barrier before the volatile write.
         */
        sizes = newValuesOnHeap;

        assert isAligned(getMaxSurvivorSize()) && isAligned(getInitialYoungSize()) && isAligned(getInitialOldSize()) && isAligned(getMaxOldSize());
        assert getMaxSurvivorSize().belowThan(getMaxYoungSize());
        assert getMaxYoungSize().add(getMaxOldSize()).equal(getMaxHeapSize());
        assert getInitialEdenSize().add(getInitialSurvivorSize().multiply(2)).equal(getInitialYoungSize());
        assert getInitialYoungSize().add(getInitialOldSize()).equal(sizes.getInitialHeapSize());
    }

    /**
     * Frees no longer needed {@link RawSizeParameters}, so that only the most recent one remains.
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
