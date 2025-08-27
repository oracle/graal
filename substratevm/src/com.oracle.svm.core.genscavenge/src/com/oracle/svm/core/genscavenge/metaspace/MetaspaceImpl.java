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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AddressRangeCommittedMemoryProvider;
import com.oracle.svm.core.genscavenge.HeapVerifier;
import com.oracle.svm.core.genscavenge.OldGeneration;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.remset.FirstObjectTable;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.UninterruptibleObjectReferenceVisitor;
import com.oracle.svm.core.heap.UninterruptibleObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.core.thread.VMOperation;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

/**
 * {@link Metaspace} implementation for serial and epsilon GC. The metaspace uses the same address
 * space as the Java heap, but it only consists of aligned heap chunks (see
 * {@link ChunkedMetaspaceMemory}). Each chunk needs a {@link RememberedSet} and an up-to-date
 * {@link FirstObjectTable}, similar to the writable part of the image heap. The chunks are managed
 * in a single "To"-{@link Space}, which ensures that the GC doesn't try to move or promote the
 * objects.
 */
public class MetaspaceImpl implements Metaspace {
    private final Space space = new Space("Metaspace", "M", true, getAge());
    private final ChunkedMetaspaceMemory memory = new ChunkedMetaspaceMemory(space);
    private final MetaspaceObjectAllocator allocator = new MetaspaceObjectAllocator(memory);

    @Platforms(Platform.HOSTED_ONLY.class)
    public MetaspaceImpl() {
    }

    @Fold
    public static MetaspaceImpl singleton() {
        return (MetaspaceImpl) ImageSingletons.lookup(Metaspace.class);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getAge() {
        return OldGeneration.getAge() + 1;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInAllocatedMemory(Object obj) {
        return isInAllocatedMemory(Word.objectToTrackedPointer(obj));
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInAllocatedMemory(Pointer ptr) {
        return space.contains(ptr);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInAddressSpace(Object obj) {
        return isInAddressSpace(Word.objectToTrackedPointer(obj));
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInAddressSpace(Pointer ptr) {
        AddressRangeCommittedMemoryProvider m = AddressRangeCommittedMemoryProvider.singleton();
        return m.isInMetaspace(ptr);
    }

    @Override
    public DynamicHub allocateDynamicHub(int numVTableEntries) {
        return allocator.allocateDynamicHub(numVTableEntries);
    }

    @Override
    public byte[] allocateByteArray(int length) {
        return allocator.allocateByteArray(length);
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        assert VMOperation.isInProgress() : "prevent other threads from manipulating the metaspace";
        space.walkObjects(visitor);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void walkObjects(UninterruptibleObjectVisitor objectVisitor) {
        assert VMOperation.isInProgress() : "prevent other threads from manipulating the metaspace";
        space.walkObjects(objectVisitor);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void walkDirtyObjects(UninterruptibleObjectVisitor objectVisitor, UninterruptibleObjectReferenceVisitor refVisitor, boolean clean) {
        assert VMOperation.isInProgress() : "prevent other threads from manipulating the metaspace";
        RememberedSet.get().walkDirtyObjects(space.getFirstAlignedHeapChunk(), space.getFirstUnalignedHeapChunk(), Word.nullPointer(), objectVisitor, refVisitor, clean);
    }

    public void logChunks(Log log) {
        space.logChunks(log);
    }

    public void logUsage(Log log) {
        space.logUsage(log, true);
    }

    public boolean printLocationInfo(Log log, Pointer ptr) {
        return space.printLocationInfo(log, ptr);
    }

    public boolean verify() {
        return HeapVerifier.verifySpace(space);
    }

    public boolean verifyRememberedSets() {
        return HeapVerifier.verifyRememberedSet(space);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void tearDown() {
        space.tearDown();
    }
}
