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

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;

/**
 * This visitor is handed *Pointers to Object references* and if necessary it promotes the
 * referenced Object and replaces the Object reference with a forwarding pointer.
 *
 * This turns an individual Object reference from grey to black.
 *
 * Since this visitor is used during collection, one instance of it is constructed during native
 * image generation.
 *
 * The vanilla visitObjectReference method is not inlined, but there is a visitObjectReferenceInline
 * available for performance critical code.
 */
public class GreyToBlackObjRefVisitor implements ObjectReferenceVisitor {
    protected final Counters counters;

    @Platforms(Platform.HOSTED_ONLY.class)
    public GreyToBlackObjRefVisitor() {
        if (Options.GreyToBlackObjRefDemographics.getValue()) {
            counters = RealCounters.factory();
        } else {
            counters = NoopCounters.factory();
        }
    }

    @Override
    public boolean visitObjectReference(final Pointer objRef, boolean compressed) {
        return visitObjectReferenceInline(objRef, 0, compressed, null);
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean visitObjectReferenceInline(final Pointer objRef, boolean compressed, Object holderObject) {
        return visitObjectReferenceInline(objRef, 0, compressed, holderObject);
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean visitObjectReferenceInline(final Pointer objRef, final int innerOffset, boolean compressed) {
        return visitObjectReferenceInline(objRef, innerOffset, compressed, null);
    }

    /**
     * This visitor is deals in *Pointers to Object references*. As such it uses Pointer.readObject
     * and Pointer.writeObject on the Pointer, not Pointer.toObject and Word.fromObject(o).
     */
    @Override
    @AlwaysInline("GC performance")
    public boolean visitObjectReferenceInline(final Pointer objRef, final int innerOffset, boolean compressed, Object holderObject) {
        assert innerOffset >= 0;
        assert !objRef.isNull();
        counters.noteObjRef();

        // Read the referenced Object carefully.
        final Pointer offsetP = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
        assert offsetP.isNonNull() || innerOffset == 0;

        Pointer p = offsetP.subtract(innerOffset);
        if (p.isNull()) {
            counters.noteNullReferent();
            return true;
        }

        if (HeapImpl.getHeapImpl().isInImageHeap(p)) {
            counters.noteNonHeapReferent();
            return true;
        }

        // This is the most expensive check as it accesses the heap fairly randomly, which results
        // in a lot of cache misses.
        final UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(p);
        if (GCImpl.getGCImpl().isCompleteCollection() || !ObjectHeaderImpl.hasRememberedSet(header)) {
            if (ObjectHeaderImpl.isForwardedHeader(header)) {
                counters.noteForwardedReferent();
                // Update the reference to point to the forwarded Object.
                final Object obj = ObjectHeaderImpl.getForwardedObject(p);
                final Object offsetObj = (innerOffset == 0) ? obj : Word.objectToUntrackedPointer(obj).add(innerOffset).toObject();
                ReferenceAccess.singleton().writeObjectAt(objRef, offsetObj, compressed);
                HeapImpl.getHeapImpl().dirtyCardIfNecessary(holderObject, obj);
                return true;
            }

            // Promote the Object if necessary, making it at least grey, and ...
            Object obj = p.toObject();
            assert innerOffset < LayoutEncoding.getSizeFromObject(obj).rawValue();
            Object copy = HeapImpl.getHeapImpl().promoteObject(obj, header);
            if (copy != obj) {
                // ... update the reference to point to the copy, making the reference black.
                counters.noteCopiedReferent();
                final Object offsetCopy = (innerOffset == 0) ? copy : Word.objectToUntrackedPointer(copy).add(innerOffset).toObject();
                ReferenceAccess.singleton().writeObjectAt(objRef, offsetCopy, compressed);
            } else {
                counters.noteUnmodifiedReference();
            }

            // The reference will not be updated if a whole chunk is promoted. However, we still
            // might have to dirty the card.
            HeapImpl.getHeapImpl().dirtyCardIfNecessary(holderObject, copy);
        }
        return true;
    }

    public Counters openCounters() {
        return counters.open();
    }

    public static class Options {
        @Option(help = "Develop demographics of the object references visited.")//
        public static final HostedOptionKey<Boolean> GreyToBlackObjRefDemographics = new HostedOptionKey<>(false);
    }

    /** A set of counters. The default implementation is a noop. */
    public interface Counters extends AutoCloseable {

        Counters open();

        @Override
        void close();

        boolean isOpen();

        void noteObjRef();

        void noteNullReferent();

        void noteForwardedReferent();

        void noteNonHeapReferent();

        void noteCopiedReferent();

        void noteUnmodifiedReference();

        void toLog();

        void reset();
    }

    public static class RealCounters implements Counters {

        public static RealCounters factory() {
            return new RealCounters();
        }

        @Override
        public RealCounters open() {
            reset();
            isOpened = true;
            return this;
        }

        @Override
        public void close() {
            toLog();
            reset();
        }

        @Override
        public boolean isOpen() {
            return isOpened;
        }

        @Override
        public void noteObjRef() {
            objRef += 1L;
        }

        @Override
        public void noteNullReferent() {
            nullReferent += 1L;
        }

        @Override
        public void noteForwardedReferent() {
            forwardedReferent += 1L;
        }

        @Override
        public void noteNonHeapReferent() {
            nonHeapReferent += 1L;
        }

        @Override
        public void noteCopiedReferent() {
            copiedReferent += 1L;
        }

        @Override
        public void noteUnmodifiedReference() {
            unmodifiedReference += 1L;
        }

        @Override
        public void toLog() {
            final Log log = Log.log();
            log.string("[GreyToBlackObjRefVisitor.counters:");
            log.string("  objRef: ").signed(objRef);
            log.string("  nullObjRef: ").signed(nullObjRef);
            log.string("  nullReferent: ").signed(nullReferent);
            log.string("  forwardedReferent: ").signed(forwardedReferent);
            log.string("  nonHeapReferent: ").signed(nonHeapReferent);
            log.string("  copiedReferent: ").signed(copiedReferent);
            log.string("  unmodifiedReference: ").signed(unmodifiedReference);
            log.string("]").newline();
        }

        @Override
        public void reset() {
            objRef = 0L;
            nullObjRef = 0L;
            nullReferent = 0L;
            forwardedReferent = 0L;
            nonHeapReferent = 0L;
            copiedReferent = 0L;
            unmodifiedReference = 0L;
            isOpened = false;
        }

        protected RealCounters() {
            reset();
        }

        protected long objRef;
        protected long nullObjRef;
        protected long nullReferent;
        protected long forwardedReferent;
        protected long nonHeapReferent;
        protected long copiedReferent;
        protected long unmodifiedReference;
        protected boolean isOpened;
    }

    public static class NoopCounters implements Counters {

        public static NoopCounters factory() {
            return new NoopCounters();
        }

        @Override
        public NoopCounters open() {
            return this;
        }

        @Override
        public void close() {
            return;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void noteObjRef() {
            return;
        }

        @Override
        public void noteNullReferent() {
            return;
        }

        @Override
        public void noteForwardedReferent() {
            return;
        }

        @Override
        public void noteNonHeapReferent() {
            return;
        }

        @Override
        public void noteCopiedReferent() {
            return;
        }

        @Override
        public void noteUnmodifiedReference() {
            return;
        }

        @Override
        public void toLog() {
            return;
        }

        @Override
        public void reset() {
            return;
        }

        protected NoopCounters() {
        }
    }
}
