/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.UnsignedUtils;

/** Discovers and handles {@link Reference} objects during garbage collection. */
final class ReferenceObjectProcessing {
    /** Head of the linked list of discovered references that need to be revisited. */
    private static Reference<?> rememberedRefsList;

    /**
     * For a {@link SoftReference}, the longest duration after its last access to keep its referent
     * alive. Determined at the end of a collection to be applied during the next collection.
     */
    private static UnsignedWord maxSoftRefAccessIntervalMs = UnsignedUtils.MAX_VALUE;

    /** Treat all soft references as weak, typically to reclaim space when low on memory. */
    private static boolean softReferencesAreWeak = false;

    /**
     * The first timestamp that was set as {@link SoftReference} clock, for examining references
     * that were created earlier than that.
     */
    private static long initialSoftRefClock = 0;

    private ReferenceObjectProcessing() { // all static
    }

    /*
     * Enables (or disables) reclaiming all objects that are softly reachable only, typically as a
     * last resort to avoid running out of memory.
     */
    public static void setSoftReferencesAreWeak(boolean enabled) {
        softReferencesAreWeak = enabled;
    }

    @AlwaysInline("GC performance")
    public static void discoverIfReference(Object object, ObjectReferenceVisitor refVisitor) {
        assert object != null;
        DynamicHub hub = KnownIntrinsics.readHub(object);
        if (probability(SLOW_PATH_PROBABILITY, hub.isReferenceInstanceClass())) {
            discover(object, refVisitor);
        }
    }

    private static void discover(Object obj, ObjectReferenceVisitor refVisitor) {
        Reference<?> dr = KnownIntrinsics.convertUnknownValue(obj, Reference.class);
        Log trace = Log.noopLog().string("[ReferenceObjectProcessing.discover: ").object(dr);
        if (ReferenceInternals.getNextDiscovered(dr) != null) {
            trace.string(" already discovered]").newline();
            return;
        }
        Pointer referentAddr = ReferenceInternals.getReferentPointer(dr);
        trace.string(" referent: ").hex(referentAddr);
        if (referentAddr.isNull()) {
            /*
             * If the Reference has been allocated but not yet initialized (null referent), its
             * soon-to-be referent will still be strongly reachable from the call stack. If the
             * Reference is initialized but has a null referent, it has already been enqueued
             * (either manually or by the GC) and does not need to be discovered.
             */
            trace.string(" is inactive]").newline();
            return;
        }
        if (Heap.getHeap().isInImageHeap(referentAddr)) {
            // Referents in the image heap cannot be moved or reclaimed, no need to look closer
            trace.string(" is in image heap]").newline();
            return;
        }
        if (maybeUpdateForwardedReference(dr, referentAddr)) {
            trace.string(" has already been promoted and field has been updated]").newline();
            return;
        }
        if (willSurviveThisCollection(referentAddr.toObject())) {
            // Referent is in a to-space, so it won't be reclaimed at this time (incremental GC?)
            HeapImpl.getHeapImpl().dirtyCardIfNecessary(dr, referentAddr.toObject());
            trace.string(" referent is in a to-space]").newline();
            return;
        }
        if (!softReferencesAreWeak && dr instanceof SoftReference) {
            long clock = ReferenceInternals.getSoftReferenceClock();
            long timestamp = ReferenceInternals.getSoftReferenceTimestamp((SoftReference<?>) dr);
            if (timestamp == 0) { // created or last accessed before the clock was initialized
                timestamp = initialSoftRefClock;
            }
            UnsignedWord elapsed = WordFactory.unsigned(clock - timestamp);
            if (elapsed.belowThan(maxSoftRefAccessIntervalMs)) {
                refVisitor.visitObjectReference(ReferenceInternals.getReferentFieldAddress(dr), true);
                return; // referent will survive and referent field has been updated
            }
        }
        trace.string(" remembered to revisit later]").newline();
        // null link means undiscovered, avoid for the last node with a cyclic reference
        Reference<?> next = (rememberedRefsList != null) ? rememberedRefsList : dr;
        ReferenceInternals.setNextDiscovered(dr, next);
        rememberedRefsList = dr;
    }

    /**
     * Updates remembered references according to the liveness of the referent, dirtying cards, and
     * clears the discovered status of non-pending references.
     *
     * @return a list of those references which are pending to be added to a {@link ReferenceQueue}.
     */
    static Reference<?> processRememberedReferences() {
        Reference<?> pendingHead = null;
        for (Reference<?> current = popRememberedRef(); current != null; current = popRememberedRef()) {
            if (!processRememberedRef(current)) {
                if (ReferenceInternals.hasQueue(current)) {
                    ReferenceInternals.setNextDiscovered(current, pendingHead);
                    pendingHead = current;
                }
                HeapImpl.getHeapImpl().dirtyCardIfNecessary(current, pendingHead);
            }
        }
        assert rememberedRefsList == null;
        return pendingHead;
    }

    static void afterCollection(UnsignedWord usedBytes, UnsignedWord maxBytes) {
        assert rememberedRefsList == null;
        UnsignedWord unusedMbytes = maxBytes.subtract(usedBytes).unsignedDivide(1024 * 1024 /* MB */);
        maxSoftRefAccessIntervalMs = unusedMbytes.multiply(HeapOptions.SoftRefLRUPolicyMSPerMB.getValue());
        ReferenceInternals.updateSoftReferenceClock();
        if (initialSoftRefClock == 0) {
            initialSoftRefClock = ReferenceInternals.getSoftReferenceClock();
        }
    }

    /**
     * Determine if the referent is live, updating the reference field, and dirtying cards.
     *
     * @return true if the referent will survive the collection, false otherwise.
     */
    private static boolean processRememberedRef(Reference<?> dr) {
        /*
         * The referent *has not* been processed as a grey reference, so I have to be careful about
         * looking through the referent field.
         */
        Pointer refPointer = ReferenceInternals.getReferentPointer(dr);
        if (refPointer.isNull()) {
            return true;
        }
        assert !HeapImpl.getHeapImpl().isInImageHeap(refPointer) : "Image heap referent: should not have been discovered";
        if (maybeUpdateForwardedReference(dr, refPointer)) {
            return true;
        }
        Object refObject = refPointer.toObject();
        if (willSurviveThisCollection(refObject)) {
            HeapImpl.getHeapImpl().dirtyCardIfNecessary(dr, refObject);
            return true;
        }
        /*
         * Referent has not survived.
         *
         * Note that we must use the Object-level store here, not the Pointer-level store: the
         * static analysis must see that the field can be null. This means that we get a write
         * barrier for this store.
         */
        ReferenceInternals.clear(dr);
        return false;
    }

    private static boolean maybeUpdateForwardedReference(Reference<?> dr, Pointer referentAddr) {
        UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(referentAddr);
        if (ObjectHeaderImpl.isForwardedHeader(header)) {
            Pointer forwardedPointer = Word.objectToUntrackedPointer(ObjectHeaderImpl.getForwardedObject(referentAddr));
            ReferenceInternals.setReferentPointer(dr, forwardedPointer);
            HeapImpl.getHeapImpl().dirtyCardIfNecessary(dr, forwardedPointer.toObject());
            return true;
        }
        return false;
    }

    private static boolean willSurviveThisCollection(Object obj) {
        HeapChunk.Header<?> chunk = HeapChunk.getEnclosingHeapChunk(obj);
        Space space = HeapChunk.getSpace(chunk);
        return !space.isFromSpace();
    }

    private static Reference<?> popRememberedRef() {
        Reference<?> result = rememberedRefsList;
        if (result != null) {
            Reference<?> next = ReferenceInternals.getNextDiscovered(result);
            rememberedRefsList = (next != result) ? next : null; // cyclic link for last node
            ReferenceInternals.setNextDiscovered(result, null);
        }
        return result;
    }

    public static boolean verify(Reference<?> dr) {
        Pointer refPointer = ReferenceInternals.getReferentPointer(dr);
        int refClassification = HeapVerifier.classifyPointer(refPointer);
        if (refClassification < 0) {
            Log witness = Log.log();
            witness.string("[ReferenceObjectProcessing.verify:");
            witness.string("  epoch: ").unsigned(HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch());
            witness.string("  refClassification: ").signed(refClassification);
            witness.string("]").newline();
            assert (!(refClassification < 0)) : "Bad referent.";
            return false;
        }
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();
        boolean refNull = refPointer.isNull();
        boolean refBootImage = (!refNull) && heap.isInImageHeapSlow(refPointer);
        boolean refYoung = (!refNull) && youngGen.slowlyFindPointer(refPointer);
        boolean refOldFrom = (!refNull) && oldGen.slowlyFindPointerInFromSpace(refPointer);
        boolean refOldTo = (!refNull) && oldGen.slowlyFindPointerInToSpace(refPointer);
        /* The referent might already have survived, or might not have. */
        if (!(refNull || refYoung || refBootImage || refOldFrom)) {
            Log witness = Log.log();
            witness.string("[ReferenceObjectProcessing.verify:");
            witness.string("  epoch: ").unsigned(HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch());
            witness.string("  refBootImage: ").bool(refBootImage);
            witness.string("  refYoung: ").bool(refYoung);
            witness.string("  refOldFrom: ").bool(refOldFrom);
            witness.string("  referent should be in heap.");
            witness.string("]").newline();
            return false;
        }
        assert !refOldTo : "referent should be in the heap.";
        return true;
    }
}
