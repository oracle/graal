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

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;

public abstract class OldGeneration extends Generation {
    OldGeneration(String name) {
        super(name);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract void beginPromotion(YoungGeneration youngGen, boolean incrementalGc);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract void blackenDirtyCardRoots(GreyToBlackObjectVisitor visitor);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract boolean scanGreyObjects(boolean incrementalGc);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract AlignedHeapChunk.AlignedHeader requestAlignedChunk();

    abstract void sweepAndCompact(Timers timers, ChunkReleaser chunkReleaser);

    abstract void releaseSpaces(ChunkReleaser chunkReleaser);

    abstract void swapSpaces();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract UnsignedWord getChunkBytes();

    abstract UnsignedWord computeObjectBytes();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract boolean isInSpace(Pointer ptr);

    abstract boolean verifyRememberedSets();

    abstract boolean verifySpaces();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    abstract void tearDown();
}
