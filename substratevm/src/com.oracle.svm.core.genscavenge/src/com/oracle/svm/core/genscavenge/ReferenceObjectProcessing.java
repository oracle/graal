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

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
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
public class ReferenceObjectProcessing {

    /** Linked list of discovered references. */
    private static Reference<?> discoveredReferencesList;

    /**
     * For a {@link SoftReference}, the longest duration after its last access to keep its referent
     * alive. Determined at the end of a collection to be applied during the next collection.
     */
    private static UnsignedWord maxSoftRefAccessIntervalMs = UnsignedUtils.MAX_VALUE;

    @AlwaysInline("GC performance")
    public static void discoverIfReference(Object object, ObjectReferenceVisitor refVisitor) {
        assert object != null;
        DynamicHub hub = KnownIntrinsics.readHub(object);
        if (probability(SLOW_PATH_PROBABILITY, hub.isReferenceInstanceClass())) {
            /*
             * References are immutable and in the image heap, they can only point to another image
             * heap object that cannot be moved or reclaimed, so no need to look closer.
             */
            if (!Heap.getHeap().isInImageHeap(object)) {
                handleDiscoverableReference(object, refVisitor);
            }
        }
    }

    private static void handleDiscoverableReference(Object obj, ObjectReferenceVisitor refVisitor) {
        Reference<?> dr = KnownIntrinsics.convertUnknownValue(obj, Reference.class);
        Log trace = Log.noopLog().string("[ReferenceObjectProcessing.handleDiscoverableReference:");
        trace.string("  dr: ").object(dr);
        if (probability(FREQUENT_PROBABILITY, ReferenceInternals.needsDiscovery(dr))) {
            if (trace.isEnabled()) {
                trace.string("  referent: ").hex(ReferenceInternals.getReferentPointer(dr));
            }
            discover(dr, refVisitor);
        } else {
            trace.string("  does not need to be discovered (uninitialized, already on a list, or null referent)]").newline();
        }
        trace.string("]").newline();
    }

    private static void discover(Reference<?> dr, ObjectReferenceVisitor refVisitor) {
        if (probability(NOT_FREQUENT_PROBABILITY, dr instanceof SoftReference)) {
            // Soft refs are presumed rare compared to weak refs (maps) and phantom refs (cleaners)
            long clock = ReferenceInternals.getSoftReferenceClock();
            long timestamp = ReferenceInternals.getSoftReferenceTimestamp((SoftReference<?>) dr);
            UnsignedWord elapsed = WordFactory.unsigned(clock - timestamp);
            if (elapsed.belowThan(maxSoftRefAccessIntervalMs)) {
                refVisitor.visitObjectReference(ReferenceInternals.getReferentFieldAddress(dr), true);
                return; // done: referent object will survive and referent field has been updated
            }
        }

        addToDiscoveredList(dr);
    }

    private static void addToDiscoveredList(Reference<?> dr) {
        final Log trace = Log.noopLog().string("[ReferenceObjectProcessing.addToDiscoveredList:").string("  this: ").object(dr)
                        .string("  referent: ").hex(ReferenceInternals.getReferentPointer(dr));
        trace.newline().string("  [adding to list:").string("  oldList: ").object(discoveredReferencesList);
        ReferenceInternals.setNextDiscovered(dr, discoveredReferencesList);
        discoveredReferencesList = dr;
        trace.string("  new list: ").object(discoveredReferencesList).string("]");
        trace.string("]").newline();
    }

    /**
     * Updates discovered references according to the liveness of the referent, dirtying cards, and
     * clears the discovered status of non-pending references.
     *
     * @return a list of those references which are pending to be added to a {@link ReferenceQueue}.
     */
    static Reference<?> processDiscoveredReferences() {
        final Log trace = Log.noopLog().string("[ReferenceObjectProcessing.processDiscoveredReferences: ").string("  discoveredList: ").object(discoveredReferencesList).newline();
        Reference<?> pendingHead = null;
        for (Reference<?> current = popDiscoveredReference(); current != null; current = popDiscoveredReference()) {
            trace.string("  [current: ").object(current).string("  referent before: ").hex(ReferenceInternals.getReferentPointer(current)).string("]").newline();
            /*
             * The referent *has not* been processed as a grey reference, so I have to be careful
             * about looking through the referent field.
             */
            if (!processReferent(current)) {
                trace.string("  unpromoted current: ").object(current).newline();
                if (ReferenceInternals.hasQueue(current)) {
                    ReferenceInternals.setNextDiscovered(current, pendingHead);
                    pendingHead = current;
                }
                HeapImpl.getHeapImpl().dirtyCardIfNecessary(current, pendingHead);
            } else {
                trace.string("  promoted current: ").object(current).newline();
            }
        }
        trace.string("]").newline();
        return pendingHead;
    }

    static void afterCollection(UnsignedWord usedBytes, UnsignedWord maxBytes) {
        assert discoveredReferencesList == null;

        UnsignedWord unusedMbytes = maxBytes.subtract(usedBytes).unsignedDivide(1024 * 1024 /* MB */);
        maxSoftRefAccessIntervalMs = unusedMbytes.multiply(HeapOptions.SoftRefLRUPolicyMSPerMB.getValue());
        ReferenceInternals.updateSoftReferenceClock();
    }

    /**
     * Determine if the referent is live, updating the reference field, and dirtying cards.
     *
     * Returns true if the referent will survive the collection, false otherwise.
     */
    private static boolean processReferent(Reference<?> dr) {
        final Pointer refPointer = ReferenceInternals.getReferentPointer(dr);
        if (refPointer.isNull()) {
            return false;
        }
        if (HeapImpl.getHeapImpl().isInImageHeap(refPointer)) {
            return true;
        }
        final UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(refPointer);
        if (ObjectHeaderImpl.isForwardedHeader(header)) {
            Pointer forwardedPointer = Word.objectToUntrackedPointer(ObjectHeaderImpl.getForwardedObject(refPointer));
            ReferenceInternals.setReferentPointer(dr, forwardedPointer);
            HeapImpl.getHeapImpl().dirtyCardIfNecessary(dr, forwardedPointer.toObject());
            return true;
        }
        final Object refObject = refPointer.toObject();
        if (hasSurvivedThisCollection(refObject)) {
            /* The referent has survived, it does not need to be updated. */
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

    private static boolean hasSurvivedThisCollection(Object obj) {
        assert !HeapImpl.getHeapImpl().isInImageHeap(obj);
        HeapChunk.Header<?> chunk = HeapChunk.getEnclosingHeapChunk(obj);
        Space space = chunk.getSpace();
        return !space.isFrom();
    }

    private static Reference<?> popDiscoveredReference() {
        final Reference<?> result = discoveredReferencesList;
        if (result != null) {
            discoveredReferencesList = ReferenceInternals.getNextDiscovered(result);
            ReferenceInternals.setNextDiscovered(result, null);
        }
        return result;
    }

    public static boolean verify(Reference<?> dr) {
        final Pointer refPointer = ReferenceInternals.getReferentPointer(dr);
        final int refClassification = HeapVerifierImpl.classifyPointer(refPointer);
        if (refClassification < 0) {
            final Log witness = Log.log();
            witness.string("[ReferenceObjectProcessing.verify:");
            witness.string("  epoch: ").unsigned(HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch());
            witness.string("  refClassification: ").signed(refClassification);
            witness.string("]").newline();
            assert (!(refClassification < 0)) : "Bad referent.";
            return false;
        }
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final YoungGeneration youngGen = heap.getYoungGeneration();
        final OldGeneration oldGen = heap.getOldGeneration();
        final boolean refNull = refPointer.isNull();
        final boolean refBootImage = (!refNull) && heap.isInImageHeapSlow(refPointer);
        final boolean refYoung = (!refNull) && youngGen.slowlyFindPointer(refPointer);
        final boolean refOldFrom = (!refNull) && oldGen.slowlyFindPointerInFromSpace(refPointer);
        final boolean refOldTo = (!refNull) && oldGen.slowlyFindPointerInToSpace(refPointer);
        /* The referent might already have survived, or might not have. */
        if (!(refNull || refYoung || refBootImage || refOldFrom)) {
            final Log witness = Log.log();
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
