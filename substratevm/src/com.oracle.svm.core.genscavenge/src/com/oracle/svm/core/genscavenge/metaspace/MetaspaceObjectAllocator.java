/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.guest.staging.core.jdk.UninterruptibleUtils.AtomicLong;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.replacements.AllocationSnippets;

/** Allocates Java objects in the {@link Metaspace}. */
class MetaspaceObjectAllocator {
    private final ChunkedMetaspaceMemory memory;

    private final AtomicLong dynamicHubSize = new AtomicLong(0);
    private final AtomicLong dynamicHubCount = new AtomicLong(0);
    private final AtomicLong byteArraySize = new AtomicLong(0);
    private final AtomicLong byteArrayCount = new AtomicLong(0);
    private final AtomicLong intArraySize = new AtomicLong(0);
    private final AtomicLong intArrayCount = new AtomicLong(0);
    private final AtomicLong objectSize = new AtomicLong(0);
    private final AtomicLong objectCount = new AtomicLong(0);

    @Platforms(Platform.HOSTED_ONLY.class)
    MetaspaceObjectAllocator(ChunkedMetaspaceMemory memory) {
        this.memory = memory;
    }

    public DynamicHub allocateDynamicHub(int vTableEntries) {
        DynamicHub hub = DynamicHub.fromClass(DynamicHub.class);
        return (DynamicHub) allocateArrayLikeObject(hub, vTableEntries, dynamicHubSize, dynamicHubCount);
    }

    public byte[] allocateByteArray(int length) {
        DynamicHub hub = DynamicHub.fromClass(byte[].class);
        return (byte[]) allocateArrayLikeObject(hub, length, byteArraySize, byteArrayCount);
    }

    public int[] allocateIntArray(int length) {
        DynamicHub hub = DynamicHub.fromClass(int[].class);
        return (int[]) allocateArrayLikeObject(hub, length, intArraySize, intArrayCount);
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    public Object allocateObject(DynamicHub hub) {
        /* Metaspace objects don't move, so they don't need an identity hashcode field. */
        UnsignedWord size = LayoutEncoding.getPureInstanceSize(hub, false);

        Pointer ptr = memory.allocate(size);
        if (ptr.isNull()) {
            return null;
        }

        Object result = FormatObjectNode.formatObject(ptr, DynamicHub.toClass(hub), true, AllocationSnippets.FillContent.WITH_ZEROES, true);
        assert size == LayoutEncoding.getSizeFromObject(result);
        objectSize.getAndAdd(size.rawValue());
        objectCount.incrementAndGet();

        enableRememberedSetTracking(result, size);
        return result;
    }

    @Uninterruptible(reason = "Holds uninitialized memory.")
    private Object allocateArrayLikeObject(DynamicHub hub, int arrayLength, AtomicLong sizeCounter, AtomicLong objectCounter) {
        UnsignedWord size = LayoutEncoding.getArrayAllocationSize(hub.getLayoutEncoding(), arrayLength);

        Pointer ptr = memory.allocate(size);
        if (ptr.isNull()) {
            return null;
        }

        Object result = FormatArrayNode.formatArray(ptr, DynamicHub.toClass(hub), arrayLength, true, false, AllocationSnippets.FillContent.WITH_ZEROES, true);
        assert size == LayoutEncoding.getSizeFromObject(result);
        sizeCounter.getAndAdd(size.rawValue());
        objectCounter.incrementAndGet();

        enableRememberedSetTracking(result, size);
        return result;
    }

    @Uninterruptible(reason = "Prevent GCs until first object table is updated.")
    private static void enableRememberedSetTracking(Object result, UnsignedWord size) {
        AlignedHeapChunk.AlignedHeader chunk = AlignedHeapChunk.getEnclosingChunk(result);
        /* This updates the first object table as well. */
        RememberedSet.get().enableRememberedSetForObject(chunk, result, size);
    }

    public void logStats(Log log) {
        log.string("Metaspace allocation stats:").indent(true);

        long kilo = 1024;
        long mega = kilo * kilo;

        long hubCount = dynamicHubCount.get();
        long hubSize = dynamicHubSize.get();
        logValue(log, "DynamicHub", hubCount, hubSize, mega);

        long byteCount = byteArrayCount.get();
        long byteSize = byteArraySize.get();
        logValue(log, "byte[]", byteCount, byteSize, mega);

        long intCount = intArrayCount.get();
        long intSize = intArraySize.get();
        logValue(log, "int[]", intCount, intSize, mega);

        long pureObjectCount = objectCount.get();
        long pureObjectSize = objectSize.get();
        logValue(log, "Object", pureObjectCount, pureObjectSize, mega);

        long totalCount = hubCount + byteCount + intCount + pureObjectCount;
        long totalSize = hubSize + byteSize + intSize + pureObjectSize;
        logValue(log, "Total", totalCount, totalSize, mega);

        log.indent(false);
    }

    private static void logValue(Log log, String type, long totalCount, long totalSize, long mega) {
        log.string(type).string(": ").unsigned(totalCount).string(" objects for a total of ").rational(totalSize, mega, 2).string("MB");
        if (totalCount > 0) {
            log.string(" (avg: ").rational(totalSize, totalCount, 2).string("B)");
        }
        log.newline();
    }
}
