/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.impl.PinnedObjectSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.thread.VMOperation;

/** Support for pinning objects to a memory address with {@link PinnedObject}. */
final class PinnedObjectImpl implements PinnedObject {

    @AutomaticallyRegisteredImageSingleton(value = PinnedObjectSupport.class, onlyWith = UseSerialOrEpsilonGC.class)
    static class PinnedObjectSupportImpl implements PinnedObjectSupport {
        @Override
        public PinnedObject create(Object object) {
            PinnedObjectImpl result = new PinnedObjectImpl(object);
            PinnedObjectImpl.pushPinnedObject(result);
            return result;
        }

        @Override
        public boolean isPinned(Object object) {
            PinnedObjectImpl pin = HeapImpl.getHeapImpl().getPinHead().get();
            while (pin != null) {
                if (pin.open && pin.referent == object) {
                    return true;
                }
                pin = pin.next;
            }
            return false;
        }
    }

    static void pushPinnedObject(PinnedObjectImpl newHead) {
        // To avoid ABA problems, the application may only push data. All other operations may only
        // be executed by the GC.
        HeapImpl heap = HeapImpl.getHeapImpl();
        UninterruptibleUtils.AtomicReference<PinnedObjectImpl> pinHead = heap.getPinHead();
        PinnedObjectImpl sampleHead;
        do {
            sampleHead = pinHead.get();
            newHead.next = sampleHead;
        } while (!pinHead.compareAndSet(sampleHead, newHead));
    }

    static PinnedObjectImpl getPinnedObjects() {
        assert VMOperation.isGCInProgress();
        UninterruptibleUtils.AtomicReference<PinnedObjectImpl> pinHead = HeapImpl.getHeapImpl().getPinHead();
        return pinHead.get();
    }

    static void setPinnedObjects(PinnedObjectImpl list) {
        assert VMOperation.isGCInProgress();
        UninterruptibleUtils.AtomicReference<PinnedObjectImpl> pinHead = HeapImpl.getHeapImpl().getPinHead();
        pinHead.set(list);
    }

    private final Object referent;

    private volatile boolean open = true;

    /** Successor on the singly-linked list maintained by {@linkplain #pushPinnedObject}. */
    private PinnedObjectImpl next;

    PinnedObjectImpl(Object object) {
        this.referent = object;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void close() {
        assert open : "Should not call close() on a closed PinnedObject.";
        open = false;
    }

    @Override
    public Object getObject() {
        assert open : "Should not call getObject() on a closed PinnedObject.";
        return referent;
    }

    @Override
    public Pointer addressOfObject() {
        assert open : "Should not call addressOfObject() on a closed PinnedObject.";
        return Word.objectToUntrackedPointer(referent);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends PointerBase> T addressOfArrayElement(int index) {
        if (referent == null) {
            throw new NullPointerException("null PinnedObject");
        }
        DynamicHub hub = ObjectHeader.readDynamicHubFromObject(referent);
        UnsignedWord offsetOfArrayElement = LayoutEncoding.getArrayElementOffset(hub.getLayoutEncoding(), index);
        return (T) addressOfObject().add(offsetOfArrayElement);
    }

    public boolean isOpen() {
        return open;
    }

    public PinnedObjectImpl getNext() {
        return next;
    }

    void setNext(PinnedObjectImpl value) {
        // Avoid useless writes as those would dirty the card table unnecessarily.
        if (value != next) {
            this.next = value;
        }
    }
}
