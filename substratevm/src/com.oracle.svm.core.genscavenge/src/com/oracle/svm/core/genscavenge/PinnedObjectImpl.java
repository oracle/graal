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
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.impl.PinnedObjectSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.log.Log;

/**
 * Holder for a pinned object, such that the object doesn't move until the pin is removed. The
 * garbage collector treats pinned object specially to ensure that they are not moved or discarded.
 * <p>
 * This class implements {@link AutoCloseable} so that the pinning can be managed conveniently with
 * a try-with-resource block that releases the pinning automatically:
 *
 * <pre>
 *   int[] array = ...
 *   try (PinnedObject pin = PinnedObject.open(array)) {
 *     CIntPointer rawData = pin.addressOfArrayElement(0);
 *     // it is safe to pass rawData to a C function.
 *   }
 * </pre>
 *
 * TODO: Is pinning a service of all collectors, or just the one I have now?
 */
public class PinnedObjectImpl implements PinnedObject {

    static class PinnedObjectSupportImpl implements PinnedObjectSupport {
        @Override
        public PinnedObject create(Object object) {
            final Log trace = Log.noopLog().string("[PinnedObject.open:").string(" object: ").object(object).newline();
            final PinnedObjectImpl result = new PinnedObjectImpl(object);
            PinnedObjectImpl.pushPinnedObject(result);
            trace.string("  returns: ]").object(result).newline();
            return result;
        }
    }

    @AutomaticFeature
    static class PinnedObjectFeature implements Feature {
        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            ImageSingletons.add(PinnedObjectSupport.class, new PinnedObjectSupportImpl());
        }
    }

    /**
     * Releases the pin for the object. After this call, the object can be moved or discarded by the
     * collector.
     */
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void close() {
        assert open : "Should not call close() on a closed PinnedObject.";
        open = false;
    }

    /*
     * Access methods.
     */

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
        final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(referent);
        final UnsignedWord offsetOfArrayElement = LayoutEncoding.getArrayElementOffset(hub.getLayoutEncoding(), index);
        return (T) addressOfObject().add(offsetOfArrayElement);
    }

    public boolean isOpen() {
        return open;
    }

    public PinnedObjectImpl getNext() {
        return next;
    }

    protected void setNext(PinnedObjectImpl nextPinnedObject) {
        next = nextPinnedObject;
    }

    /** Constructor. */
    protected PinnedObjectImpl(Object object) {
        this.referent = object;
        this.open = true;
        this.next = null;
    }

    /*
     * Operations on the list of pinned objects.
     */

    /**
     * Push an element onto the list. May be called by many threads simultaneously, so it uses a
     * compareAndSet loop.
     */
    public static PinnedObjectImpl pushPinnedObject(PinnedObjectImpl newHead) {
        final Log trace = Log.noopLog().string("[PinnedObject.pushPinnedObject:").string("  newHead: ").object(newHead);
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final AtomicReference<PinnedObjectImpl> pinHead = heap.getPinHead();
        PinnedObjectImpl sampleHead;
        do {
            sampleHead = pinHead.get();
            newHead.setNext(sampleHead);
        } while (!pinHead.compareAndSet(sampleHead, newHead));
        trace.string("  returns: ").object(newHead).string("]").newline();
        return newHead;
    }

    /**
     * Claim the entire list. Only called once during each collection, but it uses getAndSet(null)
     * anyway so I do not have to worry about it.
     */
    public static PinnedObjectImpl claimPinnedObjectList() {
        final Log trace = Log.noopLog().string("[PinnedObject.claimPinnedObjectList:").newline();
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final AtomicReference<PinnedObjectImpl> pinHead = heap.getPinHead();
        final PinnedObjectImpl result = pinHead.getAndSet(null);
        trace.string("  returns: ").object(result);
        return result;
    }

    /*
     * State.
     */

    /** The object that is pinned. */
    private final Object referent;
    /** Is this pin open? */
    private volatile boolean open;
    /**
     * Pinned object are on a singly-linked list maintained by
     * {@linkplain #pushPinnedObject(PinnedObjectImpl)}.
     */
    private PinnedObjectImpl next;
}
