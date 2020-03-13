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

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.heap.ReferenceQueueInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;

/** Discovers and handles {@link Reference} objects during garbage collection. */
public class ReferenceObjectProcessing {

    private static Reference<?> discoveredReferencesList;

    @AlwaysInline("GC performance")
    public static void discoverIfReference(Object object) {
        assert object != null;
        DynamicHub hub = KnownIntrinsics.readHub(object);
        if (probability(SLOW_PATH_PROBABILITY, hub.isReferenceInstanceClass())) {
            handleDiscoverableReference(object);
        }
    }

    private static void handleDiscoverableReference(Object obj) {
        Reference<?> dr = KnownIntrinsics.convertUnknownValue(obj, Reference.class);
        Log trace = Log.noopLog().string("[ReferenceObjectProcessing.handleDiscoverableReference:");
        trace.string("  dr: ").object(dr);
        /*
         * If the Reference has been allocated but not initialized, do not do anything with it. The
         * referent will be strongly-reachable because it is on the call stack to the constructor so
         * the Reference does not need to be put on the discovered list.
         */
        if (ReferenceInternals.isInitialized(dr)) {
            if (trace.isEnabled()) {
                trace.string("  referent: ").hex(ReferenceInternals.getReferentPointer(dr));
            }
            addToDiscoveredList(dr);
        } else {
            trace.string("  uninitialized");
        }
        trace.string("]").newline();
    }

    public static Reference<?> getDiscoveredReferencesList() {
        return discoveredReferencesList;
    }

    static void clearDiscoveredList() {
        final Log trace = Log.noopLog().string("[ReferenceObjectProcessing.clearList:").newline();
        for (Reference<?> current = popDiscoveredReference(); current != null; current = popDiscoveredReference()) {
            trace.string("  current: ").object(current).string("  referent: ").hex(ReferenceInternals.getReferentPointer(current)).newline();
            ReferenceInternals.clearDiscovered(current);
        }
        discoveredReferencesList = null;
        trace.string("]");
    }

    private static void addToDiscoveredList(Reference<?> dr) {
        final Log trace = Log.noopLog().string("[ReferenceObjectProcessing.addToDiscoveredList:").string("  this: ").object(dr).string("  referent: ")
                        .hex(ReferenceInternals.getReferentPointer(dr));
        if (ReferenceInternals.isDiscovered(dr)) {
            trace.string("  already on list]").newline();
            return;
        }
        trace.newline().string("  [adding to list:").string("  oldList: ").object(discoveredReferencesList);
        ReferenceInternals.setNextDiscovered(dr, discoveredReferencesList);
        discoveredReferencesList = dr;
        trace.string("  new list: ").object(discoveredReferencesList).string("]");
        trace.string("]").newline();
    }

    /**
     * Updates references according to the liveness of the referent, dirtying cards, and only
     * retains those references on the list for which the referent won't survive the collection.
     */
    static void processDiscoveredReferences() {
        final Log trace = Log.noopLog().string("[ReferenceObjectProcessing.processDiscoveredReferences: ").string("  discoveredList: ").object(discoveredReferencesList).newline();
        /* Start a new list. */
        Reference<?> newList = null;
        for (Reference<?> current = popDiscoveredReference(); current != null; current = popDiscoveredReference()) {
            trace.string("  [current: ").object(current).string("  referent before: ").hex(ReferenceInternals.getReferentPointer(current)).string("]").newline();
            /*
             * The referent *has not* been processed as a grey reference, so I have to be careful
             * about looking through the referent field.
             */
            if (!processReferent(current)) {
                trace.string("  unpromoted current: ").object(current).newline();
                ReferenceInternals.setNextDiscovered(current, newList);
                newList = current;
                HeapImpl.getHeapImpl().dirtyCardIfNecessary(current, newList);
            } else {
                trace.string("  promoted current: ").object(current).newline();
            }
        }
        discoveredReferencesList = newList;
        trace.string("]").newline();
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
            ReferenceInternals.clearDiscovered(result);
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

    /** Takes the discovered references from the collector and distributes them to their queues. */
    static final class Scatterer {
        private Scatterer() {
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during a collection.")
        static void distributeReferences() {
            final Log trace = Log.noopLog().string("[ReferenceObjectProcessing.Scatterer.distributeReferences:").newline();
            // Put discovered references on their queues
            for (Reference<?> dr = discoveredReferencesList; dr != null; dr = ReferenceInternals.getNextDiscovered(dr)) {
                enqueueReference(dr, trace);
            }
            if (SubstrateOptions.MultiThreaded.getValue()) { // notify waiters on ReferenceQueue
                trace.string("  broadcasting").newline();
                ReferenceQueueInternals.signalWaiters();
            }
            trace.string("]").newline();
        }

        private static <T> void enqueueReference(Reference<T> ref, Log trace) {
            trace.string("  ref: ").object(ref);
            // Do not call ref.enqueue() because it can be overridden and might allocate or throw
            boolean enqueued = ReferenceInternals.enqueue(ref);
            if (enqueued) {
                trace.string(" (enqueued)");
            }
            trace.newline();
        }
    }

}
