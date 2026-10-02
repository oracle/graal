/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.heap.AbstractPinnedObjectSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.internal.misc.Unsafe;

/** Support for pinning objects to a memory address with {@link PinnedObject}. */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public final class PinnedObjectSupportImpl extends AbstractPinnedObjectSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    public PinnedObjectSupportImpl() {
    }

    @Override
    @Uninterruptible(reason = "Ensure that object pin counts and PinnedObjects are consistent.", callerMustBe = true)
    protected void pinObject(Object object) {
        modifyObjectPinCount(object, 1);
    }

    @Override
    @Uninterruptible(reason = "Ensure that object pin counts and PinnedObjects are consistent.", callerMustBe = true)
    protected void unpinObject(Object object) {
        modifyObjectPinCount(object, -1);
    }

    @Override
    public boolean isPinnedSlow(Object object) {
        if (ObjectHeaderImpl.isAlignedObject(object) && AlignedHeapChunk.getEnclosingChunk(object).getAllPinned()) {
            return true;
        }
        return super.isPinnedSlow(object);
    }

    @Uninterruptible(reason = "Ensure that object pin counts and PinnedObjects are consistent.", callerMustBe = true)
    private static void modifyObjectPinCount(Object object, int delta) {
        assert !VMOperation.isGCInProgress() : "pinning must not change during collections";

        // If an object cannot actually move, only put it on the list to keep it alive.
        if (!ObjectHeaderImpl.isAlignedObject(object)) {
            return;
        }
        AlignedHeapChunk.AlignedHeader chunk = AlignedHeapChunk.getEnclosingChunk(object);
        if (chunk.getAllPinned()) {
            return;
        }

        Pointer pinCount = chunk.addressOfObjectPinCount();
        int oldValue;
        do {
            oldValue = pinCount.readInt(0);
        } while (!pinCount.logicCompareAndSwapInt(0, oldValue, oldValue + delta, NamedLocationIdentity.OFF_HEAP_LOCATION));

        assert oldValue >= 0 && oldValue < Integer.MAX_VALUE && oldValue + delta >= 0;
    }

    @Override
    public boolean supportsPinnedAllocation() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T allocateInstance(Class<T> clazz) throws InstantiationException {
        if (SubstrateOptions.useEpsilonGC()) {
            return (T) Unsafe.getUnsafe().allocateInstance(clazz);
        }
        DynamicHub hub = DynamicHub.fromClass(clazz);
        return (T) YoungGeneration.getPinnedAllocation().allocateInstance(hub);
    }

    @Override
    public Pointer addressOfPinnedObject(Object object) {
        if (!SubstrateOptions.PinnedObjectAddressing.getValue()) {
            throw new UnsupportedOperationException("Pinned object addressing has been disabled.");
        }
        assert SubstrateOptions.useEpsilonGC() || AlignedHeapChunk.getEnclosingChunk(object).getAllPinned() : "must use only on objects from pinned allocation";
        return Word.objectToUntrackedPointer(object);
    }
}
