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

import java.lang.ref.Reference;

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.genscavenge.CardTable.ReferenceToYoungObjectReferenceVisitor;
import com.oracle.svm.core.genscavenge.CardTable.ReferenceToYoungObjectVisitor;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;

public final class HeapVerifier {
    public enum Occasion {
        BEFORE_COLLECTION,
        DURING_COLLECTION,
        AFTER_COLLECTION
    }

    private final SpaceVerifier spaceVerifier = new SpaceVerifier();
    private final ReferenceToYoungObjectVisitor referenceToYoungObjectVisitor = new ReferenceToYoungObjectVisitor(new ReferenceToYoungObjectReferenceVisitor());
    private final ImageHeapRegionVerifier imageHeapRegionVerifier = new ImageHeapRegionVerifier();
    private final Log witnessLog = Log.log();

    private String currentCause = "Too soon to tell";

    public HeapVerifier() {
    }

    public String getCurrentCause() {
        return currentCause;
    }

    private void setCurrentCause(String cause) {
        currentCause = cause;
    }

    boolean verifyObjectAt(Pointer ptr) {
        VMOperation.guaranteeInProgress("Can only verify from a VMOperation.");
        Log trace = getTraceLog();
        trace.string("[HeapVerifier.verifyObjectAt:").string("  ptr: ").hex(ptr);

        if (ptr.isNull()) {
            getWitnessLog().string("[verifyObjectAt(objRef: ").hex(ptr).string(")").string("  null ptr").string("]").newline();
            return false;
        }
        if (!slowlyFindPointer(ptr)) {
            getWitnessLog().string("[HeapVerifier.verifyObjectAt:").string("  ptr: ").hex(ptr).string("  is not in heap.").string("]").newline();
            return false;
        }
        UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointerCarefully(ptr);
        trace.string("  header: ").hex(header);
        if (ObjectHeaderImpl.isForwardedHeader(header)) {
            Object obj = ObjectHeaderImpl.getForwardedObject(ptr);
            Pointer op = Word.objectToUntrackedPointer(obj);
            trace.string("  forwards to ").hex(op).newline();
            if (!verifyObjectAt(op)) {
                getWitnessLog().string("[HeapVerifier.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  forwarded object fails to verify").string("]").newline();
                return false;
            }
        } else {
            Object obj = ptr.toObject();
            trace.string("  obj: ").hex(Word.objectToUntrackedPointer(obj)).string("  obj.getClass: ").string(obj.getClass().getName());
            DynamicHub hub = ObjectHeaderImpl.readDynamicHubFromObjectCarefully(obj);
            if (!(hub.getClass().getName().equals("java.lang.Class"))) {
                getWitnessLog().string("[HeapVerifier.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  hub is not a class").string("]").newline();
                return false;
            }
            HeapImpl heap = HeapImpl.getHeapImpl();
            if (heap.isInImageHeap(obj) != heap.isInImageHeapSlow(obj)) {
                try (Log witness = getWitnessLog()) {
                    witness.string("[HeapVerifier.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  obj: ").object(obj);
                    witness.string("  mismatch between isInImageHeap() and isInImageHeapSlow()").string("]").newline();
                }
                return false;
            }
            trace.newline();
            /*
             * Walk the interior pointers of this object looking for breakage. First make sure the
             * references are valid, ...
             */
            if (!noReferencesOutsideHeap(obj)) {
                getWitnessLog().string("[HeapVerifier.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  contains references outside the heap").string("]").newline();
                return false;
            }
            /* ... then ask specific questions about them. */
            if (!noReferencesToForwardedObjectsVerifier(obj)) {
                getWitnessLog().string("[HeapVerifier.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  contains references to forwarded objects").string("]").newline();
                return false;
            }
            if (!verifyReferenceObject(obj)) {
                getWitnessLog().string("[HeapVerifier.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  Reference object fails to verify.").string("]").newline();
                return false;
            }
        }
        trace.string("  returns true]").newline();
        return true;
    }

    static final class VerifyVMOperation extends JavaVMOperation {

        private final String cause;
        private final HeapVerifier verifier;
        private final Occasion occasion;
        private boolean result;

        VerifyVMOperation(String cause, HeapVerifier verifier, Occasion occasion) {
            super("HeapVerification", SystemEffect.SAFEPOINT);
            this.cause = cause;
            this.verifier = verifier;
            this.occasion = occasion;
            result = false;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while verifying the heap.")
        public void operate() {
            HeapVerifier previousVerifier = HeapImpl.getHeapImpl().getHeapVerifier();
            HeapImpl.getHeapImpl().setHeapVerifier(verifier);
            result = verifier.verifyOperation(cause, occasion);
            HeapImpl.getHeapImpl().setHeapVerifier(previousVerifier);
        }

        public boolean getResult() {
            return result;
        }

    }

    public boolean verify(String cause) {
        VerifyVMOperation op = new VerifyVMOperation(cause, this, Occasion.BEFORE_COLLECTION);
        op.enqueue();
        return op.getResult();
    }

    boolean verifyOperation(String cause, Occasion occasion) {
        VMOperation.guaranteeInProgress("Can only verify from a VMOperation.");
        Log trace = getTraceLog();
        trace.string("[HeapVerifier.verify ").string(" occasion: ").string(occasion.name()).string(" cause: ").string(cause).string(":");
        trace.newline();

        setCurrentCause(cause);
        ThreadLocalAllocation.disableAndFlushForAllThreads();
        boolean result = true;
        if (!verifyImageHeapObjects()) {
            getWitnessLog().string("[HeapVerifier.verify:").string("  native image fails to verify").string("]").newline();
            result = false;
        }
        if (!verifyYoungGeneration(occasion)) {
            getWitnessLog().string("[HeapVerifier.verify:").string("  young generation fails to verify").string("]").newline();
            result = false;
        }
        if (!verifyOldGeneration(occasion)) {
            getWitnessLog().string("[HeapVerifier.verify:").string("  old generation fails to verify").string("]").newline();
            result = false;
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        if ((!result) && HeapOptions.HeapVerificationFailureIsFatal.getValue()) {
            HeapVerificationError.throwError();
        }
        return result;
    }

    static void verifyDirtyCard(boolean inToSpace) {
        OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
        oldGen.verifyDirtyCards(inToSpace);
    }

    static Log getTraceLog() {
        return (HeapOptions.TraceHeapVerification.getValue() ? Log.log() : Log.noopLog());
    }

    Log getWitnessLog() {
        return witnessLog;
    }

    private boolean verifyImageHeapObjects() {
        imageHeapRegionVerifier.reset();
        ImageHeapWalker.walkRegions(HeapImpl.getImageHeapInfo(), imageHeapRegionVerifier);
        return imageHeapRegionVerifier.verifyResult;
    }

    class ImageHeapRegionVerifier implements MemoryWalker.ImageHeapRegionVisitor {
        private final ImageHeapObjectVerifier objectVerifier = new ImageHeapObjectVerifier();
        boolean verifyResult;

        void reset() {
            verifyResult = true;
        }

        @Override
        public <T> boolean visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            Log trace = getTraceLog();
            trace.string("[ImageHeapRegionVerifier:").newline();
            Pointer regionStart = (Pointer) access.getStart(region);
            Pointer regionEnd = regionStart.add(access.getSize(region));
            trace.string("  [ regionStart: ").hex(regionStart).string("  .. regionEnd: ").hex(regionEnd).string(" ]").newline();
            objectVerifier.reset(regionStart, regionEnd);
            boolean visitResult = access.visitObjects(region, objectVerifier);
            verifyResult = verifyResult && objectVerifier.verifyResult;
            trace.string("  returns: ").bool(verifyResult).string("]").newline();
            return visitResult;
        }
    }

    class ImageHeapObjectVerifier implements ObjectVisitor {
        boolean verifyResult;
        Pointer regionStart;
        Pointer regionEnd;

        void reset(Pointer start, Pointer end) {
            this.verifyResult = true;
            this.regionStart = start;
            this.regionEnd = end;
        }

        @Override
        public boolean visitObject(Object currentObject) {
            Word currentPointer = Word.objectToUntrackedPointer(currentObject);
            if (!HeapImpl.getHeapImpl().isInImageHeap(currentObject)) {
                verifyResult = false;
                try (Log witness = getWitnessLog()) {
                    witness.string("[ImageHeapObjectVerifier:").string("  [ regionStart: ").hex(regionStart).string("  .. regionEnd: ").hex(regionEnd).string(" ]");
                    witness.string("  current: ").hex(currentPointer).string("  object is not considered to be in image heap").string("]").newline();
                }
            }
            if (!verifyObjectAt(currentPointer)) {
                verifyResult = false;
                try (Log witness = getWitnessLog()) {
                    witness.string("[ImageHeapObjectVerifier:").string("  [ regionStart: ").hex(regionStart).string("  .. regionEnd: ").hex(regionEnd).string(" ]");
                    witness.string("  current: ").hex(currentPointer).string("  object does not verify").string("]").newline();
                }
            }
            return true;
        }
    }

    private static boolean verifyYoungGeneration(Occasion occasion) {
        Generation youngGeneration = HeapImpl.getHeapImpl().getYoungGeneration();
        return youngGeneration.verify(occasion);
    }

    private static boolean verifyOldGeneration(Occasion occasion) {
        OldGeneration oldGeneration = HeapImpl.getHeapImpl().getOldGeneration();
        return oldGeneration.verify(occasion);
    }

    /**
     * For debugging: look for objects with interior references to outside the heap.
     *
     * That includes: references that are to zapped objects, and references that aren't to the heap.
     */
    private boolean noReferencesOutsideHeap(Object obj) {
        Log trace = getTraceLog();
        trace.string("[HeapVerifier.noReferencesOutsideHeap:");
        trace.string("  obj: ").object(obj).string("  obj.getClass: ").string(obj.getClass().getName());

        UnsignedWord header = ObjectHeaderImpl.readHeaderFromObjectCarefully(obj);
        trace.string("  header: ").hex(header);

        Pointer objPointer = Word.objectToUntrackedPointer(obj);
        trace.string("  objPointer: ").hex(objPointer);

        boolean result = InteriorObjRefWalker.walkObject(obj, noReferencesOutsideHeapVisitor);
        if (!result) {
            try (Log witness = getWitnessLog()) {
                witness.string("[HeapVerifier.noReferencesOutsideHeap:").string("  cause: ").string(getCurrentCause());
                witness.string("  obj: ").string(obj.getClass().getName()).string("@").hex(objPointer);
                witness.string("  header: ").hex(header).string("]").newline();
            }
        }

        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    /** An ObjectReferenceVisitor to check for references outside of the heap. */
    private static class NoReferencesOutsideHeapVisitor implements ObjectReferenceVisitor {
        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            HeapVerifier verifier = HeapImpl.getHeapImpl().getHeapVerifier();
            Pointer objPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (objPointer.isNull()) {
                return true;
            }
            if (!compressed && (objPointer.equal(HeapPolicy.getProducedHeapChunkZapWord()) || objPointer.equal(HeapPolicy.getConsumedHeapChunkZapWord()))) {
                try (Log witness = verifier.getWitnessLog()) {
                    witness.string("[HeapVerifier.NoReferencesOutsideHeapVisitor:").string("  cause: ").string(verifier.getCurrentCause());
                    witness.string("  contains zapped field Pointer: ").hex(objPointer).string("  at: ").hex(objRef).string("]").newline();
                }
                return false;
            }
            if (!HeapVerifier.slowlyFindPointer(objPointer)) {
                try (Log witness = verifier.getWitnessLog()) {
                    witness.string("[HeapVerifier.NoReferencesOutsideHeapVisitor:").string("  cause: ").string(verifier.getCurrentCause());
                    witness.string("  at: ").hex(objRef).string("  contains fieldPointer: ").hex(objPointer).string("  that is not a reference to the heap").newline();
                    witness.string("    Foolishly trying to look at the object pointed to by the fieldPointer:");
                    UnsignedWord fieldHeader = ObjectHeaderImpl.readHeaderFromPointerCarefully(objPointer);
                    witness.string("  fieldHeader: ").hex(fieldHeader);
                    Object fieldObject = objPointer.toObject();
                    witness.string("  fieldObject: ").object(fieldObject).string("]").newline();
                }
                return false;
            }
            /* It is probably safe to look at the referenced object. */
            Word readWord = objPointer.readWord(0);
            if (readWord.equal(HeapPolicy.getProducedHeapChunkZapWord()) || readWord.equal(HeapPolicy.getConsumedHeapChunkZapWord())) {
                try (Log witness = verifier.getWitnessLog()) {
                    witness.string("[HeapVerifier.NoReferencesOutsideHeapVisitor:").string("  cause: ").string(verifier.getCurrentCause());
                    witness.string("  contains fieldPointer: ").hex(objPointer).string("  to zapped memory: ").hex(readWord).string("  at: ").hex(objRef).string("]").newline();
                }
                return false;
            }
            return true;
        }
    }

    private static final HeapVerifier.NoReferencesOutsideHeapVisitor noReferencesOutsideHeapVisitor = new NoReferencesOutsideHeapVisitor();

    private boolean noReferencesToForwardedObjectsVerifier(Object obj) {
        Log trace = getTraceLog();
        trace.string("[HeapVerifier.noReferencesToForwardedObjectsVerifier:");
        trace.string("  obj: ").object(obj);
        UnsignedWord header = ObjectHeaderImpl.readHeaderFromObjectCarefully(obj);
        trace.string("  header: ").hex(header);

        Pointer objPointer = Word.objectToUntrackedPointer(obj);
        trace.string("  objPointer: ").hex(objPointer);

        boolean result = InteriorObjRefWalker.walkObject(obj, noReferencesToForwardedObjectsVisitor);
        if (!result) {
            try (Log witness = getWitnessLog()) {
                witness.string("[HeapVerifier.noReferencesToForwardedObjectsVerifier:").string("  cause: ").string(getCurrentCause()).string("  obj: ").object(obj).string("]").newline();
            }
        }

        trace.string("]").newline();
        return result;
    }

    private static class NoReferencesToForwardedObjectsVisitor implements ObjectReferenceVisitor {
        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            HeapImpl heap = HeapImpl.getHeapImpl();
            HeapVerifier verifier = heap.getHeapVerifier();
            Pointer objPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (objPointer.isNull()) {
                return true;
            }
            if (ObjectHeaderImpl.isPointerToForwardedObjectCarefully(objPointer)) {
                try (Log witness = verifier.getWitnessLog()) {
                    witness.string("[HeapVerifier.noReferencesToForwardedObjectsVerifier:").string("  cause: ").string(verifier.getCurrentCause());
                    witness.string("  contains fieldPointer: ").hex(objPointer).string("  to forwarded object at: ").hex(objRef).string("]").newline();
                }
                return false;
            }
            return true;
        }
    }

    private static final HeapVerifier.NoReferencesToForwardedObjectsVisitor noReferencesToForwardedObjectsVisitor = new NoReferencesToForwardedObjectsVisitor();

    private static boolean verifyReferenceObject(Object object) {
        Object obj = KnownIntrinsics.convertUnknownValue(object, Object.class);
        if (obj instanceof Reference) {
            return ReferenceObjectProcessing.verify((Reference<?>) obj);
        }
        return true;
    }

    static boolean slowlyFindPointer(Pointer p) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        boolean found = heap.isInImageHeapSlow(p) || slowlyFindPointerInYoungGeneration(p) || slowlyFindPointerInOldGeneration(p);
        if (!found) {
            heap.getHeapVerifier().getWitnessLog().string("[HeapVerifier.slowlyFindPointer:").string("  did not find pointer in heap: ").hex(p).string("]").newline();
        }
        return found;
    }

    private static boolean slowlyFindPointerInYoungGeneration(Pointer p) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        return youngGen.slowlyFindPointer(p);
    }

    private static boolean slowlyFindPointerInOldGeneration(Pointer p) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        OldGeneration oldGen = heap.getOldGeneration();
        return oldGen.slowlyFindPointer(p);
    }

    private static boolean slowlyFindPointerInUnusedSpace(Pointer p) {
        return HeapImpl.getChunkProvider().slowlyFindPointer(p);
    }

    static boolean slowlyFindPointerInSpace(Space space, Pointer p) {
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            Pointer start = AlignedHeapChunk.getObjectsStart(aChunk);
            if (start.belowOrEqual(p) && p.belowThan(HeapChunk.getTopPointer(aChunk))) {
                return true;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }
        UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            Pointer start = UnalignedHeapChunk.getObjectStart(uChunk);
            if (start.belowOrEqual(p) && p.belowThan(HeapChunk.getTopPointer(uChunk))) {
                return true;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        return false;
    }

    public static int classifyObject(Object o) {
        return classifyPointer(Word.objectToUntrackedPointer(o));
    }

    /* This could return an enum, but I want to be able to examine it easily from a debugger. */
    static int classifyPointer(Pointer p) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        OldGeneration oldGen = heap.getOldGeneration();
        if (p.isNull()) {
            return 0;
        }
        if (HeapImpl.getHeapImpl().isInImageHeapSlow(p)) {
            return 1;
        }
        if (youngGen.slowlyFindPointer(p)) {
            return 2;
        }
        int oldGenClassification = oldGen.classifyPointer(p);
        if (oldGenClassification > 0) {
            return 2 + oldGenClassification;
        }
        if (slowlyFindPointerInUnusedSpace(p)) {
            return -1;
        }
        return -2;
    }

    ReferenceToYoungObjectVisitor getReferenceToYoungObjectVisitor() {
        return referenceToYoungObjectVisitor;
    }

    SpaceVerifier getSpaceVerifier() {
        return spaceVerifier;
    }
}

@SuppressWarnings("serial")
final class HeapVerificationError extends Error {
    private static final HeapVerificationError SINGLETON = new HeapVerificationError();

    private HeapVerificationError() {
    }

    static void throwError() {
        Log.log().string("[HeapVerificationError.throwError:  message: ").string("Heap verification failed").string("]").newline();
        throw SINGLETON;
    }
}
