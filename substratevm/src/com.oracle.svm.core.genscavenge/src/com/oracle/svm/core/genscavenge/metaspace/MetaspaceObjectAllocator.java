/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.metaspace;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.SerialAndEpsilonGCOptions;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.metaspace.Metaspace;

import jdk.graal.compiler.replacements.AllocationSnippets;

/** Allocates Java objects in the {@link Metaspace}. */
class MetaspaceObjectAllocator {
    private final ChunkedMetaspaceMemory memory;

    private final UninterruptibleUtils.AtomicLong dynamicHubSize = new UninterruptibleUtils.AtomicLong(0);
    private final UninterruptibleUtils.AtomicLong dynamicHubCount = new UninterruptibleUtils.AtomicLong(0);
    private final UninterruptibleUtils.AtomicLong byteArraySize = new UninterruptibleUtils.AtomicLong(0);
    private final UninterruptibleUtils.AtomicLong byteArrayCount = new UninterruptibleUtils.AtomicLong(0);
    private final UninterruptibleUtils.AtomicLong intArraySize = new UninterruptibleUtils.AtomicLong(0);
    private final UninterruptibleUtils.AtomicLong intArrayCount = new UninterruptibleUtils.AtomicLong(0);

    @Platforms(Platform.HOSTED_ONLY.class)
    MetaspaceObjectAllocator(ChunkedMetaspaceMemory memory) {
        this.memory = memory;
    }

    /**
     * {@link DynamicHub}s can be allocated like normal hybrid (and therefore array-like) objects.
     * The number of vtable entries is used as the array length. Note that inlined fields like
     * {@code closedTypeWorldTypeCheckSlots} are not relevant here, as they are not available in the
     * open type world configuration.
     */
    public DynamicHub allocateDynamicHub(int vTableEntries) {
        assert !SubstrateOptions.useClosedTypeWorldHubLayout();

        DynamicHub hub = DynamicHub.fromClass(DynamicHub.class);
        assert LayoutEncoding.getArrayBaseOffsetAsInt(hub.getLayoutEncoding()) == KnownOffsets.singleton().getVTableBaseOffset();

        DynamicHub result = (DynamicHub) allocateArrayLikeObject(hub, vTableEntries, dynamicHubSize);
        if (SerialAndEpsilonGCOptions.PrintMetaspace.getValue()) {
            dynamicHubCount.getAndIncrement();
        }
        assert Heap.getHeap().getObjectHeader().verifyDynamicHubOffset(result);
        return result;
    }

    public byte[] allocateByteArray(int length) {
        DynamicHub hub = DynamicHub.fromClass(byte[].class);
        if (SerialAndEpsilonGCOptions.PrintMetaspace.getValue()) {
            byteArrayCount.getAndIncrement();
        }
        return (byte[]) allocateArrayLikeObject(hub, length, byteArraySize);
    }

    public int[] allocateIntArray(int length) {
        DynamicHub hub = DynamicHub.fromClass(int[].class);
        if (SerialAndEpsilonGCOptions.PrintMetaspace.getValue()) {
            intArrayCount.getAndIncrement();
        }
        return (int[]) allocateArrayLikeObject(hub, length, intArraySize);
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private Object allocateArrayLikeObject(DynamicHub hub, int arrayLength, UninterruptibleUtils.AtomicLong counter) {
        UnsignedWord size = LayoutEncoding.getArrayAllocationSize(hub.getLayoutEncoding(), arrayLength);

        Pointer ptr = memory.allocate(size);
        Object result = FormatArrayNode.formatArray(ptr, DynamicHub.toClass(hub), arrayLength, true, false, AllocationSnippets.FillContent.WITH_ZEROES, true);
        assert size == LayoutEncoding.getSizeFromObject(result);
        if (SerialAndEpsilonGCOptions.PrintMetaspace.getValue()) {
            counter.getAndAdd(size.rawValue());
        }

        enableRememberedSetTracking(result, size);
        return result;
    }

    @Uninterruptible(reason = "Prevent GCs until first object table is updated.")
    private static void enableRememberedSetTracking(Object result, UnsignedWord size) {
        AlignedHeapChunk.AlignedHeader chunk = AlignedHeapChunk.getEnclosingChunk(result);
        /* This updates the first object table as well. */
        RememberedSet.get().enableRememberedSetForObject(chunk, result, size);
    }

    void printStats(Log log) {
        long kilo = 1024;
        long mega = kilo * kilo;
        long hubCount = dynamicHubCount.get();
        long hubSize = dynamicHubSize.get();
        if (hubCount > 0) {
            log.string(" + DynamicHub: ").unsigned(hubCount)
                            .string(" objects for a total of ").rational(hubSize, mega, 2)
                            .string("MB (avg: ").rational(hubSize, hubCount, 2).string("B)").newline();
        } else {
            log.string(" + DynamicHub: 0 objects").newline();
        }

        long byteCount = byteArrayCount.get();
        long byteSize = byteArraySize.get();
        if (byteCount > 0) {
            log.string(" + byte[]: ").unsigned(byteCount)
                            .string(" objects for a total of ").rational(byteSize, mega, 2)
                            .string("MB (avg: ").rational(byteSize, byteCount, 2).string("B)").newline();
        } else {
            log.string(" + byte[]: 0 objects").newline();
        }

        long intCount = intArrayCount.get();
        long intSize = intArraySize.get();
        if (intCount > 0) {
            log.string(" + int[]: ").unsigned(intCount)
                            .string(" objects for a total of ").rational(intSize, mega, 2)
                            .string("MB (avg: ").rational(intSize, intCount, 2).string("B)").newline();
        } else {
            log.string(" + int[]: 0 objects").newline();
        }

        long totalCount = hubCount + byteCount + intCount;
        long totalSize = hubSize + byteSize + intSize;
        if (totalCount > 0) {
            log.string(" = Total: ").unsigned(totalCount)
                            .string(" objects for a total of ").rational(totalSize, mega, 2)
                            .string("MB (avg: ").rational(totalSize, totalCount, 2).string("B)").newline();
        } else {
            log.string(" = Total: 0 objects").newline();
        }
    }
}
