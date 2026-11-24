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
package com.oracle.svm.core.heap;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.PinnedObjectSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.guest.staging.core.heap.ObjectHeader;
import com.oracle.svm.guest.staging.core.heap.Heap;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.imagelayer.LayeredFoldResolver;
import com.oracle.svm.guest.staging.core.metaspace.Metaspace;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.guest.staging.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.api.replacements.Fold;

public abstract class AbstractPinnedObjectSupport implements PinnedObjectSupport {
    private final AtomicReference<PinnedObjectImpl> pinnedObjects = new AtomicReference<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    public AbstractPinnedObjectSupport() {
    }

    @Fold(resolver = LayeredFoldResolver.InitialLayer.class)
    public static AbstractPinnedObjectSupport singleton() {
        return (AbstractPinnedObjectSupport) ImageSingletons.lookup(PinnedObjectSupport.class);
    }

    @Override
    public PinnedObject create(Object object) {
        PinnedObjectImpl result = new PinnedObjectImpl(object);
        if (needsPinning(object)) {
            pushPinnedObject(result);
        }
        return result;
    }

    @Uninterruptible(reason = "Ensure that object pin counts and PinnedObjects are consistent.")
    private void pushPinnedObject(PinnedObjectImpl pinned) {
        /*
         * To avoid ABA problems, the application may only push data. Other operations may only be
         * performed during a GC.
         */
        PinnedObjectImpl head;
        do {
            head = pinnedObjects.get();
            pinned.next = head;
        } while (!pinnedObjects.compareAndSet(head, pinned));

        pinObject(pinned.referent);
    }

    @Uninterruptible(reason = "Ensure that object pin counts and PinnedObjects are consistent.", callerMustBe = true)
    protected abstract void pinObject(Object object);

    @Uninterruptible(reason = "Ensure that object pin counts and PinnedObjects are consistent.", callerMustBe = true)
    protected abstract void unpinObject(Object object);

    /** Removes all closed pinned objects. This method may only be called by GC. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void removeClosedObjects() {
        VMOperation.guaranteeGCInProgress("would cause various race conditions otherwise");
        PinnedObjectImpl cur = pinnedObjects.get();
        PinnedObjectImpl lastOpen = null;
        PinnedObjectImpl newHead = null;

        while (cur != null) {
            PinnedObjectImpl next = cur.next;
            if (cur.open) {
                if (newHead == null) {
                    newHead = cur;
                } else if (lastOpen.next != cur) {
                    /* Avoid useless writes as those would dirty the card table unnecessarily. */
                    lastOpen.next = cur;
                }
                lastOpen = cur;
            } else if (next != null) {
                /* Clear the link so a PinnedObject retained elsewhere does not keep other PinnedObjects alive. */
                cur.next = null;
            }
            cur = next;
        }

        if (lastOpen != null) {
            lastOpen.next = null;
        }

        pinnedObjects.set(newHead);
    }

    /**
     * Returns whether the given object is currently tracked as pinned, indicating an intent that it
     * must not move. Objects that are implicitly non-moving are not tracked and therefore return
     * {@code false}. These include image heap objects (including auxiliary images), metaspace
     * objects, and, with Epsilon GC, all objects.
     *
     * Consequently, {@code false} does not imply that the object can move or that no code relies on
     * its address remaining stable.
     */
    public boolean isPinnedSlow(Object object) {
        PinnedObjectImpl pin = pinnedObjects.get();
        while (pin != null) {
            if (pin.open && pin.referent == object) {
                return true;
            }
            pin = pin.next;
        }
        return false;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean needsPinning(Object object) {
        return !isImplicitlyPinned(object);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isImplicitlyPinned(Object object) {
        return SubstrateOptions.useEpsilonGC() || object == null || Heap.getHeap().isInImageHeap(object) || Metaspace.singleton().isInAddressSpace(object);
    }

    public static class PinnedObjectImpl implements PinnedObject {
        private Object referent;
        private boolean open;
        private PinnedObjectImpl next;

        PinnedObjectImpl(Object object) {
            this.referent = object;
            this.open = true;
            this.next = null;
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public Object getObject() {
            assert open : "Should not call getObject() on a closed PinnedObject.";
            return referent;
        }

        @Override
        @Uninterruptible(reason = "Ensure that object pin counts and PinnedObjects are consistent.")
        public void close() {
            assert open : "Should not call close() on a closed PinnedObject.";
            open = false;
            if (needsPinning(referent)) {
                AbstractPinnedObjectSupport.singleton().unpinObject(referent);
            }
            referent = null;
        }

        @Override
        public Pointer addressOfObject() {
            if (!SubstrateOptions.PinnedObjectAddressing.getValue()) {
                throw new UnsupportedOperationException("Pinned object addressing has been disabled.");
            }
            assert open : "Should not call addressOfObject() on a closed PinnedObject.";
            return Word.objectToUntrackedPointer(referent);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends PointerBase> T addressOfArrayElement(int index) {
            if (referent == null) {
                throw new NullPointerException("PinnedObject is missing a referent");
            }
            DynamicHub hub = (DynamicHub) ObjectHeader.readDynamicHubFromObject(referent);
            UnsignedWord offsetOfArrayElement = LayoutEncoding.getArrayElementOffset(hub.getLayoutEncoding(), index);
            return (T) addressOfObject().add(offsetOfArrayElement);
        }
    }
}
