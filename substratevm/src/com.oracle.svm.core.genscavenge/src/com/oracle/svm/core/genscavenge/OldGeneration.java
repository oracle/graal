/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

public abstract class OldGeneration extends Generation {
    OldGeneration(String name) {
        super(name);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract void beginPromotion(boolean incrementalGc);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract void blackenDirtyCardRoots(GreyToBlackObjectVisitor visitor);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract boolean scanGreyObjects(boolean incrementalGc);

    abstract void sweepAndCompact(Timers timers, ChunkReleaser chunkReleaser);

    abstract void releaseSpaces(ChunkReleaser chunkReleaser);

    abstract void swapSpaces();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract UnsignedWord getChunkBytes();

    abstract UnsignedWord computeObjectBytes();

    abstract boolean isInSpace(Pointer ptr);

    abstract boolean verifyRememberedSets();

    abstract boolean verifySpaces();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract void tearDown();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    AlignedHeapChunk.AlignedHeader requestAlignedChunk() {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        AlignedHeapChunk.AlignedHeader chunk = HeapImpl.getChunkProvider().produceAlignedChunk();
        if (probability(EXTREMELY_SLOW_PATH_PROBABILITY, chunk.isNull())) {
            throw VMError.shouldNotReachHere("OldGeneration.requestAlignedChunk: failure to allocate aligned chunk");
        }
        RememberedSet.get().enableRememberedSetForChunk(chunk);
        return chunk;
    }
}
