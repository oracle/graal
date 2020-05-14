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
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.PinnedObjectSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.log.Log;

/** Support for pinning objects to a memory address with {@link PinnedObject}. */
final class PinnedObjectImpl implements PinnedObject {
    static class PinnedObjectSupportImpl implements PinnedObjectSupport {
        @Override
        public PinnedObject create(Object object) {
            Log trace = Log.noopLog().string("[PinnedObject.open:").string(" object: ").object(object).newline();
            PinnedObjectImpl result = new PinnedObjectImpl(object);
            PinnedObjectImpl.pushPinnedObject(result);
            trace.string("  returns: ]").object(result).newline();
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

    @AutomaticFeature
    static class PinnedObjectFeature implements Feature {
        @Override
        public boolean isInConfiguration(IsInConfigurationAccess access) {
            return SubstrateOptions.UseCardRememberedSetHeap.getValue();
        }

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            ImageSingletons.add(PinnedObjectSupport.class, new PinnedObjectSupportImpl());
        }
    }

    static void pushPinnedObject(PinnedObjectImpl newHead) {
        Log trace = Log.noopLog().string("[PinnedObject.pushPinnedObject:").string("  newHead: ").object(newHead);
        HeapImpl heap = HeapImpl.getHeapImpl();
        UninterruptibleUtils.AtomicReference<PinnedObjectImpl> pinHead = heap.getPinHead();
        PinnedObjectImpl sampleHead;
        do {
            sampleHead = pinHead.get();
            newHead.next = sampleHead;
        } while (!pinHead.compareAndSet(sampleHead, newHead));
        trace.string("  returns: ").object(newHead).string("]").newline();
    }

    /** Clears the list head reference and returns the former head object. */
    static PinnedObjectImpl claimPinnedObjectList() {
        Log trace = Log.noopLog().string("[PinnedObject.claimPinnedObjectList:").newline();
        HeapImpl heap = HeapImpl.getHeapImpl();
        UninterruptibleUtils.AtomicReference<PinnedObjectImpl> pinHead = heap.getPinHead();
        PinnedObjectImpl result = pinHead.getAndSet(null);
        trace.string("  returns: ").object(result);
        return result;
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
}
