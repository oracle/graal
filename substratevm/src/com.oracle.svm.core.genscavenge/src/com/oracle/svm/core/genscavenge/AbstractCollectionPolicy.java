/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.svm.core.heap.PhysicalMemory;

/** Base class for garbage collection policies. All sizes are in bytes. */
public abstract class AbstractCollectionPolicy {
    public abstract String getName();

    /**
     * Ensures that size parameters have been computed and methods like {@link #getMaximumHeapSize}
     * provide reasonable values, but do not force a recomputation of the size parameters like
     * {@link #updateSizeParameters}.
     */
    public abstract void ensureSizeParametersInitialized();

    /**
     * (Re)computes minimum/maximum/initial sizes of space based on the available
     * {@linkplain PhysicalMemory physical memory} and current runtime option values. This method is
     * called after slow-path allocation (of a TLAB or a large object) and so allocation is allowed,
     * but can trigger a collection.
     */
    public abstract void updateSizeParameters();

    /**
     * During a slow-path allocation, determines whether to trigger a collection. Returning
     * {@code true} will initiate a safepoint during which {@link #shouldCollectCompletely} will be
     * called followed by the collection.
     */
    public abstract boolean shouldCollectOnAllocation();

    /**
     * At a safepoint, decides whether to do a complete collection (returning {@code true}) or an
     * incremental collection (returning {@code false}).
     *
     * @param followingIncrementalCollection whether an incremental collection has just finished in
     *            the same safepoint. Implementations would typically decide whether to follow up
     *            with a full collection based on whether enough memory was reclaimed.
     */
    public abstract boolean shouldCollectCompletely(boolean followingIncrementalCollection);

    /**
     * The current limit for the size of the entire heap, which is less than or equal to
     * {@link #getMaximumHeapSize}. Outside of the policy, this limit is used only for free space
     * calculations.
     *
     * NOTE: this can currently be exceeded during a collection while copying objects in the old
     * generation.
     */
    public abstract UnsignedWord getCurrentHeapCapacity();

    /**
     * The hard limit for the size of the entire heap. Exceeding this limit triggers an
     * {@link OutOfMemoryError}.
     *
     * NOTE: this can currently be exceeded during a collection while copying objects in the old
     * generation.
     */
    public abstract UnsignedWord getMaximumHeapSize();

    /** The maximum capacity of the young generation, comprising eden and survivor spaces. */
    public abstract UnsignedWord getMaximumYoungGenerationSize();

    /** The minimum heap size, for inclusion in diagnostic output. */
    public abstract UnsignedWord getMinimumHeapSize();

    /**
     * The total capacity of all survivor-from spaces of all ages, equal to the size of all
     * survivor-to spaces of all ages. In other words, when copying during a collection, up to 2x
     * this amount can be used for surviving objects.
     */
    public abstract UnsignedWord getSurvivorSpacesCapacity();

    /**
     * The maximum number of bytes that should be kept readily available for allocation or copying
     * during collections.
     */
    public abstract UnsignedWord getMaximumFreeReservedSize();

    /**
     * The age at which objects should currently be promoted to the old generation, which is between
     * 1 (straight from eden) and the {@linkplain HeapParameters#getMaxSurvivorSpaces() number of
     * survivor spaces + 1}.
     */
    public abstract int getTenuringAge();

    /** Called at the beginning of a collection, in the safepoint operation. */
    @SuppressWarnings("unused")
    public void onCollectionBegin(boolean completeCollection) {
    }

    /** Called before the end of a collection, in the safepoint operation. */
    @SuppressWarnings("unused")
    public void onCollectionEnd(boolean completeCollection, GCCause cause) {
    }
}
