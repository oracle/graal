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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.UninterruptibleObjectReferenceVisitor;
import com.oracle.svm.core.log.Log;

import jdk.graal.compiler.word.Word;

/**
 * This visitor is handed <em>Pointers to Object references</em> and if necessary it promotes the
 * referenced Object and replaces the Object reference with a forwarding pointer.
 *
 * This turns an individual Object reference from grey to black.
 *
 * Since this visitor is used during collection, one instance of it is constructed during native
 * image generation.
 */
public final class GreyToBlackObjRefVisitor implements UninterruptibleObjectReferenceVisitor {
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
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
        Pointer pos = firstObjRef;
        Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
        while (pos.belowThan(end)) {
            visitObjectReference(pos, compressed, holderObject);
            pos = pos.add(referenceSize);
        }
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
        assert !objRef.isNull();
        counters.noteObjRef();

        Pointer p = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
        if (p.isNull()) {
            counters.noteNullReferent();
            return;
        }

        if (HeapImpl.getHeapImpl().isInImageHeap(p)) {
            counters.noteNonHeapReferent();
            return;
        }

        // This is the most expensive check as it accesses the heap fairly randomly, which results
        // in a lot of cache misses.
        ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
        Word header = ohi.readHeaderFromPointer(p);
        if (GCImpl.getGCImpl().isCompleteCollection() || !RememberedSet.get().hasRememberedSet(header)) {

            if (ObjectHeaderImpl.isForwardedHeader(header)) {
                counters.noteForwardedReferent();
                // Update the reference to point to the forwarded Object.
                Object obj = ohi.getForwardedObject(p, header);
                ReferenceAccess.singleton().writeObjectAt(objRef, obj, compressed);
                RememberedSet.get().dirtyCardIfNecessary(holderObject, obj, objRef);
                return;
            }

            Object obj = p.toObjectNonNull();
            if (SerialGCOptions.useCompactingOldGen() && ObjectHeaderImpl.isMarkedHeader(header)) {
                RememberedSet.get().dirtyCardIfNecessary(holderObject, obj, objRef);
                return;
            }

            // Promote the Object if necessary, making it at least grey, and ...
            Object copy = GCImpl.getGCImpl().promoteObject(obj, header);
            if (copy != obj) {
                // ... update the reference to point to the copy, making the reference black.
                counters.noteCopiedReferent();
                ReferenceAccess.singleton().writeObjectAt(objRef, copy, compressed);
            } else {
                counters.noteUnmodifiedReference();
            }

            // The reference will not be updated if a whole chunk is promoted. However, we still
            // might have to dirty the card.
            RememberedSet.get().dirtyCardIfNecessary(holderObject, copy, objRef);
        }
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

        void toLog();

        void reset();
    }

    public static class RealCounters implements Counters {
        private long objRef;
        private long nullObjRef;
        private long nullReferent;
        private long forwardedReferent;
        private long nonHeapReferent;
        private long copiedReferent;
        private long unmodifiedReference;

        RealCounters() {
            reset();
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
            objRef += 1L;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteNullReferent() {
            nullReferent += 1L;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteForwardedReferent() {
            forwardedReferent += 1L;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteNonHeapReferent() {
            nonHeapReferent += 1L;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteCopiedReferent() {
            copiedReferent += 1L;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void noteUnmodifiedReference() {
            unmodifiedReference += 1L;
        }

        @Override
        public void toLog() {
            Log log = Log.log();
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
        public void toLog() {
        }

        @Override
        public void reset() {
        }
    }
}
