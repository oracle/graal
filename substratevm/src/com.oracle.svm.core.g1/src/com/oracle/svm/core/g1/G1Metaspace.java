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
package com.oracle.svm.core.g1;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.g1.nativelib.G1Library;
import com.oracle.svm.core.gc.shared.NativeGCStructs.MetaspaceStatistics;
import com.oracle.svm.core.gc.shared.graal.NativeGCAllocationSupport;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.metaspace.AbstractMetaspace;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.guest.staging.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Provides access to the metaspace managed by the native G1 implementation.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public final class G1Metaspace extends AbstractMetaspace {
    @Platforms(Platform.HOSTED_ONLY.class)
    public G1Metaspace() {
    }

    @Fold
    public static G1Metaspace singleton() {
        return (G1Metaspace) ImageSingletons.lookup(Metaspace.class);
    }

    @Fold
    public static int getRegionCount() {
        return G1Heap.getReservedMetaspaceSize() / G1Options.G1HeapRegionSize.getValue();
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInAllocatedMemory(Pointer ptr) {
        return isInAddressSpace(ptr) && G1Library.isInAllocatedMetaspace(ptr);
    }

    @Override
    protected DynamicHub allocateDynamicHub0(int numVTableEntries) {
        return NativeGCAllocationSupport.singleton().allocateMetaspaceDynamicHub(numVTableEntries);
    }

    @Override
    protected byte[] allocateByteArray0(int length) {
        return NativeGCAllocationSupport.singleton().allocateMetaspaceByteArray(length);
    }

    @Override
    protected int[] allocateIntArray0(int length) {
        return NativeGCAllocationSupport.singleton().allocateMetaspaceIntArray(length);
    }

    @Override
    protected Object allocateObject0(DynamicHub hub) {
        return NativeGCAllocationSupport.singleton().allocateMetaspaceInstance(hub);
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        G1HeapWalker.walkMetaspace(visitor);
    }

    @Override
    protected void printMetaspaceInfo(Log log) {
        printAllocationStatistics(log);
    }

    @Override
    protected void printAllocationStatistics(Log log) {
        MetaspaceStatistics statistics = UnsafeStackValue.get(MetaspaceStatistics.class);
        G1Library.getMetaspaceStatistics(statistics);

        log.string("Metaspace allocation stats:").indent(true);
        printAllocationStatistics(log, "DynamicHub", statistics.dynamicHubCount(), statistics.dynamicHubSize());
        printAllocationStatistics(log, "byte[]", statistics.byteArrayCount(), statistics.byteArraySize());
        printAllocationStatistics(log, "int[]", statistics.intArrayCount(), statistics.intArraySize());
        printAllocationStatistics(log, "Object", statistics.objectCount(), statistics.objectSize());
        UnsignedWord totalCount = statistics.dynamicHubCount().add(statistics.byteArrayCount()).add(statistics.intArrayCount()).add(statistics.objectCount());
        UnsignedWord totalSize = statistics.dynamicHubSize().add(statistics.byteArraySize()).add(statistics.intArraySize()).add(statistics.objectSize());
        printAllocationStatistics(log, "Total", totalCount, totalSize);
        log.indent(false);
    }

    private static void printAllocationStatistics(Log log, String name, UnsignedWord count, UnsignedWord size) {
        log.string(name).string(": ").unsigned(count);
        if (count.equal(0)) {
            log.string(" objects").newline();
        } else {
            log.string(" objects for a total of ").rational(size, 1024 * 1024, 2).string("MB (avg: ").rational(size, count.rawValue(), 2).string("B)").newline();
        }
    }

}
