/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.UnsignedUtils;

final class EdenAllocation extends HeapAllocation {

    @Platforms(Platform.HOSTED_ONLY.class)
    EdenAllocation() {
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25.0.3+2/src/hotspot/share/gc/g1/g1CollectedHeap.cpp#L385-L397")
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    public Pointer allocateNewTlab(UnsignedWord minSize, UnsignedWord requestedSize, WordPointer actualSize) {
        assert fitsInAlignedChunk(requestedSize) : "We do not allow TLABs larger than an aligned chunk.";
        return attemptAllocation(minSize, requestedSize, actualSize);
    }

    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    public Pointer allocateOutsideTlab(UnsignedWord size) {
        assert fitsInAlignedChunk(size) : "Must not be called for allocation requests that require an unaligned chunk.";
        return attemptAllocationExact(size);
    }

    @Override
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-23-ga/src/hotspot/share/gc/g1/g1AllocRegion.cpp#L275-L287")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected boolean shouldRetain(UnsignedWord freeBytes) {
        UnsignedWord minTlabSize = Word.unsigned(TlabOptionCache.getMinTlabSize());
        return freeBytes.aboveOrEqual(minTlabSize);
    }

    /**
     * Returns the remaining space in the current allocation chunk if it can accommodate a
     * minimum-sized TLAB; otherwise, returns the usable capacity of a new chunk.
     *
     * Multiple threads may execute the TLAB logic concurrently, so there is no guarantee that the
     * returned size is really available in the current allocation chunk once a thread requests the
     * new TLAB.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-23-ga/src/hotspot/share/gc/g1/g1Allocator.cpp#L184-L203")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord availableSizeForNewTlab() {
        UnsignedWord maxTlabSize = AlignedHeapChunk.getUsableSizeForObjects();
        UnsignedWord minTlabSize = Word.unsigned(TlabOptionCache.getMinTlabSize());
        UnsignedWord currentChunkFreeBytes = getCurrentChunkFreeBytes();
        if (currentChunkFreeBytes.belowThan(minTlabSize)) {
            /* The next TLAB allocation will most likely happen in a new chunk. */
            return maxTlabSize;
        }

        return UnsignedUtils.min(currentChunkFreeBytes, maxTlabSize);
    }

    @Override
    protected void printChunkInfo(Log log, AlignedHeader chunk, boolean unusablePart) {
        String unusable = unusablePart ? "unusable part of " : "";
        log.string("points into ").string(unusable).string("eden allocation aligned chunk").spaces(1).zhex(chunk).spaces(1);
    }
}
