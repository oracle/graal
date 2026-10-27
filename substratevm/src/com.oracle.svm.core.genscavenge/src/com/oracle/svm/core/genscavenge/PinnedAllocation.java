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

import static com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.reflect.Array;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.genscavenge.graal.GenScavengeAllocationSupport;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.events.JfrAllocationEvents;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.replacements.AllocationSnippets;

/**
 * Allocates pinned objects in aligned chunks {@linkplain AlignedHeader#setAllPinned where all
 * objects remain pinned}. Unlike {@link PinnedObject}, allocated objects are not kept alive, and
 * once unreachable, are reclaimed by sweeping. Due to the higher risk and cost of fragmentation
 * with pinned objects, this allocator tries to fill chunks as much as it can and prefers
 * synchronization over thread-local buffers that can leave unfilled gaps. Moreover, chunks which
 * contain large unused regions are subject to {@linkplain AlignedChunkPartialUncommitter being
 * partially uncommitted} to limit worst-case memory waste.
 * <p>
 * This class shares much of the allocation protocol of {@link ThreadLocalAllocation} and changes
 * to it may require updates here.
 */
final class PinnedAllocation extends HeapAllocation {
    /**
     * Minimum remaining capacity in bytes to keep a chunk for future allocations when we acquire a
     * new chunk to satisfy a larger allocation. All-pinned chunks incur higher overhead than
     * regular chunks, so we should fill them up, but we also don't want to keep trying to allocate
     * in a chunk that is too full.
     */
    private static final int MIN_RETAIN_SIZE = 32; // should fit another reasonably-sized object

    @Platforms(Platform.HOSTED_ONLY.class)
    PinnedAllocation() {
    }

    Object allocateInstance(DynamicHub hub) throws InstantiationException {
        VMError.guarantee(!SubstrateOptions.useEpsilonGC(), "should use regular allocation paths");

        SubstrateAllocationSnippets.validateUnsafeInstanceAllocationHub(hub);
        hub.ensureInitialized();

        UnsignedWord size = LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding());
        assert fitsInAlignedChunk(size) : "Must not be called for allocation requests that require an unaligned chunk.";
        GCImpl.getPolicy().ensureSizeParametersInitialized();
        return allocateInstance0(hub, size);
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private Object allocateInstance0(DynamicHub hub, UnsignedWord size) {
        HeapImpl.exitIfAllocationDisallowed("PinnedAllocation.allocateInstance0", hub);
        GCImpl.getGCImpl().maybeCollectOnAllocation(size);
        try {
            return allocateInstance1(hub, size);
        } catch (HeapChunkAllocationException e) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(e.getOutOfMemoryError());
        }
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private Object allocateInstance1(DynamicHub hub, UnsignedWord size) {
        long startTicks = JfrTicks.elapsedTicks();

        assert size.equal(LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding()));
        Pointer memory = attemptAllocationExact(size);

        ThreadLocalAllocation.trackPinnedAlignedAllocationOutsideTlab(size);

        Object result = FormatObjectNode.formatObject(memory, DynamicHub.toClass(hub), false, AllocationSnippets.FillContent.WITH_ZEROES, true);
        assert size == LayoutEncoding.getSizeFromObject(result);

        JfrAllocationEvents.emit(startTicks, hub, size, Word.zero(), true);
        /*
         * Intentionally omit JFR old-object profiling. The current profiler attempts to sample each
         * slow-path allocation. Pinned allocation has no fast path, so every allocation here would
         * become a sampling candidate.
         */

        return result;
    }

    Object newArray(Class<?> componentType, int length) {
        if (componentType == null) {
            throw new NullPointerException();
        } else if (length < 0) {
            throw new NegativeArraySizeException(String.valueOf(length));
        } else if (componentType == void.class || SubstrateUtil.arrayTypeDimension(componentType) >= 255) {
            throw new IllegalArgumentException();
        }
        DynamicHub componentHub = DynamicHub.fromClass(componentType);
        DynamicHub arrayHub = componentHub.getOrCreateArrayHub();
        if (arrayHub == null || !arrayHub.isInstantiated()) {
            throw MissingReflectionRegistrationUtils.reportArrayInstantiation(componentType, 1);
        }
        UnsignedWord size = LayoutEncoding.getArrayAllocationSize(arrayHub.getLayoutEncoding(), length);
        if (!GenScavengeAllocationSupport.shouldAllocateInAlignedChunk(size)) {
            /*
             * Allocate in an unaligned chunk. Unfortunately, this means we can no longer tell that
             * the array was intentionally pinned and that code relies on it not to move (e.g., for
             * the auxiliary image builder when it calls isPinnedSlow).
             */
            return Array.newInstance(componentType, length);
        }
        ThreadLocalAllocation.checkArrayLikeObjectSizeBeforeAllocation(size);
        return newArray0(arrayHub, length, size);
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private Object newArray0(DynamicHub arrayHub, int length, UnsignedWord size) {
        HeapImpl.exitIfAllocationDisallowed("PinnedAllocation.newArray0", arrayHub);
        GCImpl.getGCImpl().maybeCollectOnAllocation(size);
        try {
            return newArray1(arrayHub, length, size);
        } catch (HeapChunkAllocationException e) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(e.getOutOfMemoryError());
        }
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private Object newArray1(DynamicHub arrayHub, int length, UnsignedWord size) {
        VMError.guarantee(!SubstrateOptions.useEpsilonGC(), "should use regular allocation paths");

        long startTicks = JfrTicks.elapsedTicks();

        assert size.equal(LayoutEncoding.getArrayAllocationSize(arrayHub.getLayoutEncoding(), length));
        Pointer memory = attemptAllocationExact(size);

        ThreadLocalAllocation.trackPinnedAlignedAllocationOutsideTlab(size);

        Object result = FormatArrayNode.formatArray(memory, DynamicHub.toClass(arrayHub), length, false, false, AllocationSnippets.FillContent.WITH_ZEROES, true);
        assert size == LayoutEncoding.getSizeFromObject(result);

        JfrAllocationEvents.emit(startTicks, arrayHub, size, Word.zero(), true);
        /*
         * Intentionally omit JFR old-object profiling. The current profiler attempts to sample each
         * slow-path allocation. Pinned allocation has no fast path, so every allocation here would
         * become a sampling candidate.
         */

        return result;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected boolean shouldRetain(UnsignedWord freeBytes) {
        return freeBytes.aboveOrEqual(MIN_RETAIN_SIZE);
    }

    @Override
    @Uninterruptible(reason = "Allocation internals must never end up in interruptible code.")
    protected AlignedHeader requestNewAlignedChunk() {
        AlignedHeader chunk = super.requestNewAlignedChunk();
        chunk.setAllPinned(true);
        return chunk;
    }

    @Override
    protected void printChunkInfo(Log log, AlignedHeader chunk, boolean unusablePart) {
        String unusable = unusablePart ? "unusable part of " : "";
        log.string("points into ").string(unusable).string("pinned allocation aligned chunk").spaces(1).zhex(chunk).spaces(1);
    }
}
