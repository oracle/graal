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
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.heap.Target_java_lang_ref_Reference;
import com.oracle.svm.core.heap.Target_java_lang_ref_ReferenceQueue;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;

public class DiscoverableReferenceProcessing {

    /*
     * Public methods for collectors.
     */
    @AlwaysInline("GC performance")
    public static void discoverDiscoverableReference(Object object) {
        final Object obj = KnownIntrinsics.convertUnknownValue(object, Object.class);
        /* TODO: What's the cost of this type test, since it will be a concrete subtype? */
        if (probability(SLOW_PATH_PROBABILITY, obj instanceof Target_java_lang_ref_Reference)) {
            handleDiscoverableReference(obj);
        }
    }

    private static void handleDiscoverableReference(final Object obj) {
        final Log trace = Log.noopLog().string("[DiscoverableReference.discoverDiscoverableReference:");
        final Target_java_lang_ref_Reference<?> dr = (Target_java_lang_ref_Reference<?>) obj;
        trace.string("  dr: ").object(dr);
        /*
         * If the DiscoverableReference has been allocated but not initialized, do not do anything
         * with it. The referent will be strongly-reachable because it is on the call stack to the
         * constructor so the DiscoveredReference does not need to be put on the discovered list.
         */
        if (dr.isInitialized()) {
            /* Add this DiscoverableReference to the discovered list. */
            if (trace.isEnabled()) {
                trace.string("  referent: ").hex(Target_java_lang_ref_Reference.TestingBackDoor.getReferentPointer(SubstrateUtil.cast(dr, Reference.class)));
            }
            addToDiscoveredReferences(dr);
        } else {
            trace.string("  uninitialized");
        }
        trace.string("]").newline();
    }

    /** The first element of the discovered list, or null. */
    public static Target_java_lang_ref_Reference<?> getDiscoveredList() {
        return HeapImpl.getHeapImpl().getGCImpl().getDiscoveredReferenceList();
    }

    /** Clear the discovered DiscoverableReference list. */
    static void clearDiscoveredReferences() {
        final Log trace = Log.noopLog().string("[DiscoverableReference.clearDiscoveredList:").newline();
        /*
         * It's not enough to just set the discovered list to null. I also have to clean all the
         * entries from the discovered references from last time.
         */
        for (Target_java_lang_ref_Reference<?> current = popDiscoveredReference(); current != null; current = popDiscoveredReference()) {
            trace.string("  current: ").object(current).string("  referent: ").hex(current.getReferentPointer()).newline();
            current.cleanDiscovered();
        }
        setDiscoveredList(null);
        trace.string("]");
    }

    private static void addToDiscoveredReferences(Target_java_lang_ref_Reference<?> dr) {
        final Log trace = Log.noopLog().string("[DiscoverableReference.addToDiscoveredReferences:").string("  this: ").object(dr).string("  referent: ").hex(dr.getReferentPointer());
        if (dr.getIsDiscovered()) {
            /*
             * This DiscoverableReference is already on the discovered list. Don't add it again or
             * the list gets broken.
             */
            trace.string("  already on list]").newline();
            return;
        }
        trace.newline().string("  [adding to list:").string("  oldList: ").object(getDiscoveredList());
        setDiscoveredList(dr.prependToDiscoveredReference(getDiscoveredList()));
        trace.string("  new list: ").object(getDiscoveredList()).string("]");
        trace.string("]").newline();
    }

    /** Scrub the list of entries whose referent is in the old space. */
    static void processDiscoveredReferences() {
        final Log trace = Log.noopLog().string("[DiscoverableReference.processDiscoveredReferences: ").string("  discoveredList: ").object(getDiscoveredList()).newline();
        /* Start a new list. */
        Target_java_lang_ref_Reference<?> newList = null;
        for (Target_java_lang_ref_Reference<?> current = popDiscoveredReference(); current != null; current = popDiscoveredReference()) {
            trace.string("  [current: ").object(current).string("  referent before: ").hex(current.getReferentPointer()).string("]").newline();
            /*
             * The referent *has not* been processed as a grey reference, so I have to be careful
             * about looking through the referent field.
             */
            if (!processReferent(current)) {
                /* The referent will not survive the collection: put it on the new list. */
                trace.string("  unpromoted current: ").object(current).newline();
                newList = current.prependToDiscoveredReference(newList);
                HeapImpl.getHeapImpl().dirtyCardIfNecessary(current, newList);
            } else {
                /* Referent will survive the collection: don't add it to the new list. */
                trace.string("  promoted current: ").object(current).newline();
            }
        }
        setDiscoveredList(newList);
        trace.string("]").newline();
    }

    /**
     * Determine if a referent is live, and adjust it as necessary.
     *
     * Returns true if the referent will survive the collection, false otherwise.
     */
    private static boolean processReferent(Target_java_lang_ref_Reference<?> dr) {
        final Log trace = Log.noopLog().string("[DiscoverableReference.processReferent:").string("  this: ").object(dr);
        final Pointer refPointer = dr.getReferentPointer();
        if (refPointer.isNull()) {
            return false;
        }

        if (HeapImpl.getHeapImpl().isInImageHeap(refPointer)) {
            return true;
        }

        final UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(refPointer);
        if (ObjectHeaderImpl.isForwardedHeader(header)) {
            /* If the referent got forwarded, then update the referent. */
            Pointer forwardedPointer = Word.objectToUntrackedPointer(ObjectHeaderImpl.getForwardedObject(refPointer));
            dr.setReferentPointer(forwardedPointer);
            HeapImpl.getHeapImpl().dirtyCardIfNecessary(dr, forwardedPointer.toObject());
            return true;
        }

        // It's a real object. See if the referent has survived.
        final Object refObject = refPointer.toObject();
        if (hasSurvivedThisCollection(refObject)) {
            /* The referent has survived, it does not need to be updated. */
            HeapImpl.getHeapImpl().dirtyCardIfNecessary(dr, refObject);
            return true;
        }
        /*
         * Otherwise, referent has not survived.
         *
         * Note that we must use the Object-level store here, not the Pointer-level store: the
         * static analysis must see that the field can be null. This means that we get a write
         * barrier for this store.
         */
        dr.doClear();
        return false;
    }

    private static boolean hasSurvivedThisCollection(Object obj) {
        assert !HeapImpl.getHeapImpl().isInImageHeap(obj);
        HeapChunk.Header<?> chunk = HeapChunk.getEnclosingHeapChunk(obj);
        Space space = chunk.getSpace();
        return !space.isFrom();
    }

    /** Pop the first element off the discovered references list. */
    private static Target_java_lang_ref_Reference<?> popDiscoveredReference() {
        final Target_java_lang_ref_Reference<?> result = getDiscoveredList();
        if (result != null) {
            setDiscoveredList(result.getNextDiscoveredReference());
            result.cleanDiscovered();
        }
        return result;
    }

    /** Write access to the discovered list: The whole list. */
    private static void setDiscoveredList(Target_java_lang_ref_Reference<?> value) {
        HeapImpl.getHeapImpl().getGCImpl().setDiscoveredReferenceList(value);
    }

    public static boolean verify(Target_java_lang_ref_Reference<?> dr) {
        final Pointer refPointer = dr.getReferentPointer();
        final int refClassification = HeapVerifierImpl.classifyPointer(refPointer);
        if (refClassification < 0) {
            final Log witness = Log.log();
            witness.string("[DiscoverableReference.verify:");
            witness.string("  epoch: ").unsigned(HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch());
            witness.string("  refClassification: ").signed(refClassification);
            witness.string("]").newline();
            assert (!(refClassification < 0)) : "Bad referent.";
            return false;
        }
        /* Figure out where the referent is. */
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
            witness.string("[DiscoverableReference.verify:");
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

    /**
     * Take the discovered references from the collector and distribute the references to their
     * queues.
     */
    static final class Scatterer {
        private Scatterer() {
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during a collection.")
        static void distributeReferences() {
            final Log trace = Log.noopLog().string("[DiscoverableReferenceProcessing.Scatterer.distributeReferences:").newline();
            /*
             * Walk down the discovered references looking for FeebleReferences, and put them on
             * their lists, if any.
             */
            for (Target_java_lang_ref_Reference<?> dr = DiscoverableReferenceProcessing.getDiscoveredList(); dr != null; dr = dr.getNextDiscoveredReference()) {
                processReference(dr, trace);
            }
            if (SubstrateOptions.MultiThreaded.getValue()) {
                trace.string("  broadcasting").newline();
                /* Notify anyone blocked waiting for FeebleReferences to be available. */
                Target_java_lang_ref_ReferenceQueue.signalWaiters();
            }
            trace.string("]").newline();
        }

        private static <T> void processReference(Target_java_lang_ref_Reference<T> fr, Log trace) {
            trace.string("  fr: ").object(fr).newline();
            Target_java_lang_ref_ReferenceQueue<? super T> frList = fr.getList();
            if (frList != null) {
                trace.string("  frList: ").object(frList).newline();
                frList.push(fr);
            } else {
                trace.string("  frList is null").newline();
            }
        }
    }

}
