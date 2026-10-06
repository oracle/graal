/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1.graal;

import org.graalvm.word.impl.Word;

import com.oracle.svm.core.g1.G1Constants;
import com.oracle.svm.core.g1.G1Heap;
import com.oracle.svm.core.g1.nativelib.G1Library;
import com.oracle.svm.core.gc.shared.graal.NativeGCAllocationSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.DuplicatedInNativeCode;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public class G1AllocationSupport extends NativeGCAllocationSupport {
    @Override
    public Word getThreadLocalData() {
        return G1Heap.barrierAndAllocationDataTL.getAddress();
    }

    @Override
    public int tlabTopOffset() {
        return G1Constants.tlabTopOffset();
    }

    @Override
    public int tlabEndOffset() {
        return G1Constants.tlabEndOffset();
    }

    @Override
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected Object allocateInstance0(DynamicHub hub) {
        Word result = G1Library.allocateInstance(Word.objectToUntrackedWord(hub));
        return result.toObject();
    }

    @Override
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected Object allocateArray0(int length, DynamicHub hub) {
        Word result = G1Library.allocateArray(Word.objectToUntrackedWord(hub), length);
        return result.toObject();
    }

    @Override
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected Object allocateStoredContinuation0(int length, DynamicHub hub) {
        Word result = G1Library.allocateStoredContinuation(Word.objectToUntrackedWord(hub), length);
        return result.toObject();
    }

    @Override
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected Object allocatePod0(int length, DynamicHub hub) {
        Word result = G1Library.allocatePod(Word.objectToUntrackedWord(hub), length);
        return result.toObject();
    }

    @Override
    public DynamicHub allocateMetaspaceDynamicHub(int numVTableEntries) {
        DynamicHub hub = DynamicHub.fromClass(DynamicHub.class);
        return (DynamicHub) allocateMetaspaceArrayLikeObject(hub, numVTableEntries, MetaspaceAllocationKind.DYNAMIC_HUB);
    }

    @Override
    public byte[] allocateMetaspaceByteArray(int length) {
        DynamicHub hub = DynamicHub.fromClass(byte[].class);
        return (byte[]) allocateMetaspaceArrayLikeObject(hub, length, MetaspaceAllocationKind.BYTE_ARRAY);
    }

    @Override
    public int[] allocateMetaspaceIntArray(int length) {
        DynamicHub hub = DynamicHub.fromClass(int[].class);
        return (int[]) allocateMetaspaceArrayLikeObject(hub, length, MetaspaceAllocationKind.INT_ARRAY);
    }

    @Override
    public Object allocateMetaspaceInstance(DynamicHub hub) {
        return allocateMetaspaceInstance0(hub);
    }

    @Uninterruptible(reason = "Uses raw pointers", calleeMustBe = false)
    private static Object allocateMetaspaceArrayLikeObject(DynamicHub hub, int length, int allocationKind) {
        Word result = G1Library.allocateMetaspaceArray(Word.objectToUntrackedWord(hub), length, allocationKind);
        return result.toObject();
    }

    @Uninterruptible(reason = "Uses raw pointers", calleeMustBe = false)
    private static Object allocateMetaspaceInstance0(DynamicHub hub) {
        Word result = G1Library.allocateMetaspaceInstance(Word.objectToUntrackedWord(hub));
        return result.toObject();
    }

    @DuplicatedInNativeCode
    private interface MetaspaceAllocationKind {
        int DYNAMIC_HUB = 0;
        int BYTE_ARRAY = 1;
        int INT_ARRAY = 2;
    }
}
