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

import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.reflect.Array;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.heap.AbstractPinnedObjectSupport;
import com.oracle.svm.guest.staging.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
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

    /**
     * Allocates and returns an instance of the given class without calling any constructor. The
     * class is initialized if it is uninitialized. The allocated instance remains at its initial
     * memory address throughout its lifetime. The instance will be collected after it is no longer
     * referenced, and it is the caller's responsibility to keep it strongly reachable until it is
     * not needed any more.
     * <p>
     * This is intended for applications which frequently allocate objects that need to be pinned
     * for nearly all of their lifetime. Allocation can be less efficient than regular allocation
     * and involve thread synchronization. However, unlike {@link PinnedObject#create} or off-heap
     * memory, no additional objects or code are needed to track the memory. GC can reclaim the
     * memory of unreachable objects directly, without requiring a second, deferred step like with
     * {@link Cleaner}. In order to limit heap fragmentation, objects that are allocated around the
     * same time should have similar lifetimes.
     *
     * @see #addressOfPinnedObject
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T allocateInstance(Class<T> clazz) throws InstantiationException {
        if (SubstrateOptions.useEpsilonGC()) {
            return (T) Unsafe.getUnsafe().allocateInstance(clazz);
        }
        DynamicHub hub = DynamicHub.fromClass(clazz);
        return (T) YoungGeneration.getPinnedAllocation().allocateInstance(hub);
    }

    /**
     * Allocates and returns an array of the given component type and size. The allocated array
     * remains at its initial memory address throughout its lifetime. The array will be collected
     * after it is no longer referenced, and it is the caller's responsibility to keep it strongly
     * reachable until it is not needed any more.
     * <p>
     * For the tradeoffs of pinned allocation, see {@link #allocateInstance}.
     *
     * @see java.lang.reflect.Array#newInstance(Class, int)
     * @see #addressOfPinnedArrayElement
     */
    @Override
    public Object newArray(Class<?> componentType, int length) {
        if (SubstrateOptions.useEpsilonGC()) {
            return Array.newInstance(componentType, length);
        }
        return YoungGeneration.getPinnedAllocation().newArray(componentType, length);
    }

    /**
     * Returns the memory address of an element of an array allocated with {@link #newArray}. The
     * caller must ensure that the array remains strongly reachable so that it cannot be collected
     * while it might still be accessed by address, possibly by using
     * {@link Reference#reachabilityFence}.
     *
     * @see #addressOfPinnedObject
     */
    @Override
    public Pointer addressOfPinnedArrayElement(Object array, int index) {
        if (!SubstrateOptions.PinnedObjectAddressing.getValue()) {
            throw new UnsupportedOperationException("Pinned object addressing has been disabled.");
        }
        DynamicHub hub = (DynamicHub) ObjectHeader.readDynamicHubFromObject(array);
        assert hub.isArray();
        UnsignedWord offsetOfArrayElement = LayoutEncoding.getArrayElementOffset(hub.getLayoutEncoding(), index);
        return addressOfPinnedObject(array).add(offsetOfArrayElement);
    }

    /**
     * Returns the memory address of an object allocated with {@link #allocateInstance} or
     * {@link #newArray}. The caller must ensure that the object remains strongly reachable so that
     * it cannot be collected while it might still be accessed by address, possibly by using
     * {@link Reference#reachabilityFence}.
     *
     * @see #addressOfPinnedArrayElement
     */
    @Override
    public Pointer addressOfPinnedObject(Object object) {
        if (!SubstrateOptions.PinnedObjectAddressing.getValue()) {
            throw new UnsupportedOperationException("Pinned object addressing has been disabled.");
        }
        assert SubstrateOptions.useEpsilonGC() || ObjectHeaderImpl.isUnalignedObject(object) ||
                        AlignedHeapChunk.getEnclosingChunk(object).getAllPinned() : "must use only on objects from pinned allocation";
        return Word.objectToUntrackedPointer(object);
    }
}
