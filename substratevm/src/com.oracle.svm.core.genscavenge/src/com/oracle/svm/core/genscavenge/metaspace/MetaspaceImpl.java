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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.shared.Uninterruptible.CORE_GC_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.genscavenge.HeapVerifier;
import com.oracle.svm.core.genscavenge.OldGeneration;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.remset.FirstObjectTable;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.UninterruptibleObjectReferenceVisitor;
import com.oracle.svm.core.heap.UninterruptibleObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.metaspace.AbstractMetaspace;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * {@link Metaspace} implementation for serial and epsilon GC. The metaspace uses the same address
 * space as the Java heap, but it only consists of aligned heap chunks (see
 * {@link ChunkedMetaspaceMemory}). Each chunk needs a {@link RememberedSet} and an up-to-date
 * {@link FirstObjectTable}, similar to the writable part of the image heap. The chunks are managed
 * in a single "To"-{@link Space}, which ensures that the GC doesn't try to move or promote the
 * objects.
 * <p>
 * This singleton is not fully layer aware because the {@link MetaspaceImpl#space} should be either
 * always relinked or properly duplicated for each layer.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public class MetaspaceImpl extends AbstractMetaspace {
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
    public boolean isInAllocatedMemory(Pointer ptr) {
        /* This is not necessarily thread-safe enough, see GR-79696. */
        return isInAddressSpace(ptr) && space.contains(ptr);
    }

    @Override
    protected DynamicHub allocateDynamicHub0(int numVTableEntries) {
        return allocator.allocateDynamicHub(numVTableEntries);
    }

    @Override
    protected byte[] allocateByteArray0(int length) {
        return allocator.allocateByteArray(length);
    }

    @Override
    protected int[] allocateIntArray0(int length) {
        return allocator.allocateIntArray(length);
    }

    @Override
    protected Object allocateObject0(DynamicHub hub) {
        return allocator.allocateObject(hub);
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        assert VMOperation.isInProgressAtSafepoint() : "prevent other threads from manipulating the metaspace";
        space.walkObjects(visitor);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void walkObjects(UninterruptibleObjectVisitor objectVisitor) {
        assert VMOperation.isInProgressAtSafepoint() : "prevent other threads from manipulating the metaspace";
        space.walkObjects(objectVisitor);
    }

    @Uninterruptible(reason = CORE_GC_CODE)
    public void walkDirtyObjects(UninterruptibleObjectVisitor objectVisitor, UninterruptibleObjectReferenceVisitor refVisitor, boolean clean) {
        assert VMOperation.isInProgressAtSafepoint() : "prevent other threads from manipulating the metaspace";
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
        assert VMOperation.isInProgressAtSafepoint() : "prevent other threads from manipulating the metaspace";
        return HeapVerifier.verifySpace(space);
    }

    public boolean verifyRememberedSets() {
        assert VMOperation.isInProgressAtSafepoint() : "prevent other threads from manipulating the metaspace";
        return HeapVerifier.verifyRememberedSet(space);
    }

    @Uninterruptible(reason = "Tear-down in progress.")
    public void tearDown() {
        space.tearDown();
    }

    @Override
    protected void printMetaspaceInfo(Log log) {
        logUsage(log);
        printAllocationStatistics(log);
    }

    @Override
    protected void printAllocationStatistics(Log log) {
        allocator.logStats(log);
    }
}
