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
package com.oracle.svm.core.genscavenge.remset;

import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.HostedByteBufferPointer;

/**
 * A remembered set keeps track of references between generations (from the old generation to the
 * young generation, or from the image heap to the runtime heap). During collections, the remembered
 * set is used to avoid scanning the entire image heap and old generation.
 */
public interface RememberedSet extends BarrierSetProvider {
    @Fold
    static RememberedSet get() {
        return ImageSingletons.lookup(RememberedSet.class);
    }

    /** Returns the header size of aligned chunks. */
    UnsignedWord getHeaderSizeOfAlignedChunk();

    /** Returns the header size of unaligned chunks. */
    UnsignedWord getHeaderSizeOfUnalignedChunk();

    /**
     * Enables remembered set tracking for an aligned chunk and its objects. Must be called when
     * adding a new chunk to the image heap or old generation.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    void enableRememberedSetForAlignedChunk(HostedByteBufferPointer chunk, int chunkPosition, List<ImageHeapObject> objects);

    /**
     * Enables remembered set tracking for an unaligned chunk and its objects. Must be called when
     * adding a new chunk to the image heap or old generation.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    void enableRememberedSetForUnalignedChunk(HostedByteBufferPointer chunk);

    /**
     * Enables remembered set tracking for an aligned chunk and its objects. Must be called when
     * adding a new chunk to the image heap or old generation.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void enableRememberedSetForChunk(AlignedHeader chunk);

    /**
     * Enables remembered set tracking for an unaligned chunk and its objects. Must be called when
     * adding a new chunk to the image heap or old generation.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void enableRememberedSetForChunk(UnalignedHeader chunk);

    /**
     * Enables remembered set tracking for a single object in an aligned chunk. Must be called when
     * an object is added to the image heap or old generation.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void enableRememberedSetForObject(AlignedHeader chunk, Object obj, UnsignedWord objSize);

    /** Clears the remembered set of an aligned chunk. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void clearRememberedSet(AlignedHeader chunk);

    /** Clears the remembered set of an unaligned chunk. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void clearRememberedSet(UnalignedHeader chunk);

    /** Checks if remembered set tracking is enabled for an object. */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean hasRememberedSet(UnsignedWord header);

    /**
     * Marks an object as dirty. May only be called for objects for which remembered set tracking is
     * enabled. This tells the GC that the object may contain a reference to another generation
     * (from old generation to young generation, or from image heap to runtime heap).
     */
    @AlwaysInline("GC performance")
    void dirtyCardForAlignedObject(Object object, boolean verifyOnly);

    /**
     * Marks an object as dirty. May only be called for objects for which remembered set tracking is
     * enabled. This tells the GC that the object may contain a reference to another generation
     * (from old generation to young generation, or from image heap to runtime heap).
     */
    @AlwaysInline("GC performance")
    void dirtyCardForUnalignedObject(Object object, boolean verifyOnly);

    /**
     * Marks the {@code holderObject} as dirty if needed according to the location of
     * {@code object}. May only be called for {@code holderObject}s for which remembered set
     * tracking is enabled.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void dirtyCardIfNecessary(Object holderObject, Object object);

    /**
     * Walks all dirty objects in an aligned chunk.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean);

    /**
     * Walks all dirty objects in an unaligned chunk.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean);

    /**
     * Walks all dirty objects in a {@link Space}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor, boolean clean);

    /**
     * Verify the remembered set for an aligned chunk.
     */
    boolean verify(AlignedHeader firstAlignedHeapChunk);

    /**
     * Verify the remembered set for an unaligned chunk.
     */
    boolean verify(UnalignedHeader firstUnalignedHeapChunk);
}
