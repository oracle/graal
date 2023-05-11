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
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.parallel.ParallelGC;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicLong;
import com.oracle.svm.core.log.Log;

/**
 * This visitor is handed <em>Pointers to Object references</em> and if necessary it promotes the
 * referenced Object and replaces the Object reference with a forwarding pointer.
 *
 * This turns an individual Object reference from grey to black.
 *
 * Since this visitor is used during collection, one instance of it is constructed during native
 * image generation.
 */
final class GreyToBlackObjRefVisitor implements ObjectReferenceVisitor {
    private final Counters counters;

    @Platforms(Platform.HOSTED_ONLY.class)
    GreyToBlackObjRefVisitor() {
        if (SerialGCOptions.GreyToBlackObjRefDemographics.getValue()) {
            counters = new RealCounters();
        } else {
            counters = new NoopCounters();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
        return visitObjectReferenceInline(objRef, 0, compressed, holderObject);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean visitObjectReferenceInline(Pointer objRef, int innerOffset, boolean compressed, Object holderObject) {
        assert innerOffset >= 0;
        assert !objRef.isNull();
        counters.noteObjRef();

        Pointer offsetP = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
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
        ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
        Word header = ObjectHeader.readHeaderFromPointer(p);
        if (GCImpl.getGCImpl().isCompleteCollection() || !RememberedSet.get().hasRememberedSet(header)) {

            if (ObjectHeaderImpl.isForwardedHeader(header)) {
                counters.noteForwardedReferent();
                // Update the reference to point to the forwarded Object.
                Object obj = ohi.getForwardedObject(p, header);
                assert ParallelGC.singleton().isInParallelPhase() || innerOffset < LayoutEncoding.getSizeFromObjectInGC(obj).rawValue();
                Object offsetObj = (innerOffset == 0) ? obj : Word.objectToUntrackedPointer(obj).add(innerOffset).toObject();
                ReferenceAccess.singleton().writeObjectAt(objRef, offsetObj, compressed);
                RememberedSet.get().dirtyCardIfNecessary(holderObject, obj);
                return true;
            }

            // Promote the Object if necessary, making it at least grey, and ...
            Object obj = p.toObject();
            Object copy = GCImpl.getGCImpl().promoteObject(obj, header);
            if (copy != obj) {
                // ... update the reference to point to the copy, making the reference black.
                counters.noteCopiedReferent();
                assert ParallelGC.singleton().isInParallelPhase() || innerOffset < LayoutEncoding.getSizeFromObjectInGC(copy).rawValue();
                Object offsetCopy = (innerOffset == 0) ? copy : Word.objectToUntrackedPointer(copy).add(innerOffset).toObject();
                ReferenceAccess.singleton().writeObjectAt(objRef, offsetCopy, compressed);
            } else {
                counters.noteUnmodifiedReference();
            }

            // The reference will not be updated if a whole chunk is promoted. However, we still
            // might have to dirty the card.
            RememberedSet.get().dirtyCardIfNecessary(holderObject, copy);
        }
        return true;
    }

    public Counters openCounters() {
        return counters.open();
    }

    public interface Counters extends AutoCloseable {

        Counters open();

        @Override
        void close();

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void noteObjRef();

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void noteNullReferent();

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void noteForwardedReferent();

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void noteNonHeapReferent();

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void noteCopiedReferent();

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void noteUnmodifiedReference();

        void reset();
    }

    public static class RealCounters implements Counters {
        private final AtomicLong objRef = new AtomicLong(0);
        private final AtomicLong nullObjRef = new AtomicLong(0);
        private final AtomicLong nullReferent = new AtomicLong(0);
        private final AtomicLong forwardedReferent = new AtomicLong(0);
        private final AtomicLong nonHeapReferent = new AtomicLong(0);
        private final AtomicLong copiedReferent = new AtomicLong(0);
        private final AtomicLong unmodifiedReference = new AtomicLong(0);

        RealCounters() {
            reset();
        }

        @Override
        public void reset() {
            objRef.set(0L);
            nullObjRef.set(0L);
            nullReferent.set(0L);
            forwardedReferent.set(0L);
            nonHeapReferent.set(0L);
            copiedReferent.set(0L);
            unmodifiedReference.set(0L);
        }

        @Override
        public RealCounters open() {
            reset();
            return this;
        }

        @Override
        public void close() {
            toLog();
            reset();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteObjRef() {
            objRef.incrementAndGet();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteNullReferent() {
            nullReferent.incrementAndGet();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteForwardedReferent() {
            forwardedReferent.incrementAndGet();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteNonHeapReferent() {
            nonHeapReferent.incrementAndGet();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteCopiedReferent() {
            copiedReferent.incrementAndGet();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteUnmodifiedReference() {
            unmodifiedReference.incrementAndGet();
        }

        private void toLog() {
            Log log = Log.log();
            log.string("[GreyToBlackObjRefVisitor.counters:");
            log.string("  objRef: ").signed(objRef.get());
            log.string("  nullObjRef: ").signed(nullObjRef.get());
            log.string("  nullReferent: ").signed(nullReferent.get());
            log.string("  forwardedReferent: ").signed(forwardedReferent.get());
            log.string("  nonHeapReferent: ").signed(nonHeapReferent.get());
            log.string("  copiedReferent: ").signed(copiedReferent.get());
            log.string("  unmodifiedReference: ").signed(unmodifiedReference.get());
            log.string("]").newline();
        }
    }

    public static class NoopCounters implements Counters {

        NoopCounters() {
        }

        @Override
        public NoopCounters open() {
            return this;
        }

        @Override
        public void close() {
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteObjRef() {
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteNullReferent() {
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteForwardedReferent() {
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteNonHeapReferent() {
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteCopiedReferent() {
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteUnmodifiedReference() {
        }

        @Override
        public void reset() {
        }
    }
}
