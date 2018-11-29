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
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.genscavenge.CardTable.ReferenceToYoungObjectReferenceVisitor;
import com.oracle.svm.core.genscavenge.CardTable.ReferenceToYoungObjectVisitor;
import com.oracle.svm.core.heap.DiscoverableReference;
import com.oracle.svm.core.heap.NativeImageInfo;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;

/**
 * Verification of the heap.
 *
 * This, maybe, could be just an ObjectVisitor, but I want a little more information about where I
 * am, so I'm writing this as a set of recursive-descent methods.
 */
public class HeapVerifierImpl implements HeapVerifier {

    /* Instance state. */
    private final SpaceVerifierImpl spaceVerifier;
    private String cause;

    /** A verifier that an object reference is not to the young space. */
    private ReferenceToYoungObjectVisitor referenceToYoungObjectVisitor;

    /* Witnessing verification failures. */
    private final Log witnessLog;

    protected HeapVerifierImpl() {
        super();
        /*
         * The constructor is called during native image generation, at which point there isn't a
         * Heap, so the heap field must be initialized lazily.
         */
        this.spaceVerifier = SpaceVerifierImpl.factory();
        this.cause = "Too soon to tell";
        final ReferenceToYoungObjectReferenceVisitor refToYoungObjectReferenceVisitor = new ReferenceToYoungObjectReferenceVisitor();
        this.referenceToYoungObjectVisitor = new ReferenceToYoungObjectVisitor(refToYoungObjectReferenceVisitor);
        this.witnessLog = Log.log();
    }

    /**
     * A factory for a Heap Verifier.
     */
    public static HeapVerifierImpl factory() {
        return new HeapVerifierImpl();
    }

    @Override
    public String getCause() {
        return cause;
    }

    @Override
    public void setCause(String causeArg) {
        cause = causeArg;
    }

    /* TODO: This is probably not the right place for this method. */
    /* TODO: This method could return true if I wanted to find more than just the first failure. */
    /* TODO: add a name field to the heap and use that in logs */
    /** Whatever it takes to verify an Object. */
    @Override
    public boolean verifyObjectAt(Pointer ptr) {
        VMOperation.guaranteeInProgress("Can only verify from a VMOperation.");
        final Log trace = getTraceLog();
        trace.string("[HeapVerifierImpl.verifyObjectAt:").string("  ptr: ").hex(ptr);

        /* I should not be asked to verify null references. */
        if (ptr.isNull()) {
            getWitnessLog().string("[verifyObjectAt(objRef: ").hex(ptr).string(")").string("  null ptr").string("]").newline();
            /* Nothing else to do. */
            return false;
        }
        /* I should be able to find the pointed-to object in the heap. */
        if (!slowlyFindPointer(ptr)) {
            getWitnessLog().string("[HeapVerifierImpl.verifyObjectAt:").string("  ptr: ").hex(ptr).string("  is not in heap.").string("]").newline();
            /* No point in examining the object further. */
            return false;
        }
        final UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointerCarefully(ptr);
        trace.string("  header: ").hex(header);
        final ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
        if (ohi.isForwardedHeader(header)) {
            final Object obj = ohi.getForwardedObject(ptr);
            final Pointer op = Word.objectToUntrackedPointer(obj);
            trace.string("  forwards to ").hex(op).newline();
            if (!verifyObjectAt(op)) {
                getWitnessLog().string("[HeapVerifierImpl.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  forwarded object fails to verify").string("]").newline();
                return false;
            }
        } else {
            final Object obj = ptr.toObject();
            trace.string("  obj: ").hex(Word.objectToUntrackedPointer(obj)).string("  obj.getClass: ").string(obj.getClass().getName()).string("  objectHeader: ").string(ohi.toStringFromObject(obj));
            final DynamicHub hub = ObjectHeaderImpl.readDynamicHubFromObjectCarefully(obj);
            if (!(hub.getClass().getName().equals("java.lang.Class"))) {
                getWitnessLog().string("[HeapVerifierImpl.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  hub is not a class").string("]").newline();
                return false;
            }
            if (slowlyFindObjectInBootImage(obj)) {
                if (!ohi.isBootImageCarefully(obj)) {
                    try (Log witness = getWitnessLog()) {
                        witness.string("[HeapVerifierImpl.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  obj: ").object(obj);
                        witness.string("  header: ").string(ohi.toStringFromHeader(header)).string("  native image object but not native image object header").string("]").newline();
                    }
                    return false;
                }
            } else {
                if (ohi.isNonHeapAllocatedCarefully(obj)) {
                    try (Log witness = getWitnessLog()) {
                        witness.string("[HeapVerifierImpl.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  obj: ").object(obj);
                        witness.string("  header: ").string(ohi.toStringFromHeader(header)).string("  Not native image, but not heap allocated.").string("]").newline();
                    }
                    return false;
                }
            }
            trace.newline();
            /*
             * Walk the interior pointers of this object looking for breakage. First make sure the
             * references are valid, ...
             */
            if (!noReferencesOutsideHeap(obj)) {
                getWitnessLog().string("[HeapVerifierImpl.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  contains references outside the heap").string("]").newline();
                return false;
            }
            /* ... then ask specific questions about them. */
            if (!noReferencesToForwardedObjectsVerifier(obj)) {
                getWitnessLog().string("[HeapVerifierImpl.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  contains references to forwarded objects").string("]").newline();
                return false;
            }
            if (!verifyDiscoverableReference(obj)) {
                getWitnessLog().string("[HeapVerifierImpl.verifyObjectAt(objRef: ").hex(ptr).string(")").string("  DiscoverableReference fails to verify.").string("]").newline();
                return false;
            }
        }

        trace.string("  returns true]").newline();
        return true;
    }

    /** A VMOperation that verifies the heap. */
    protected static final class VerifyVMOperation extends VMOperation {

        private final String message;
        private final HeapVerifierImpl verifier;
        private final HeapVerifier.Occasion occasion;
        private boolean result;

        VerifyVMOperation(String message, HeapVerifierImpl verifier, HeapVerifier.Occasion occasion) {
            super("HeapVerification", CallerEffect.BLOCKS_CALLER, SystemEffect.CAUSES_SAFEPOINT);
            this.message = message;
            this.verifier = verifier;
            this.occasion = occasion;
            result = false;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while verifying the heap.")
        public void operate() {
            /* Install the provided verifier as the verifier for the duration of this operation. */
            final HeapVerifierImpl previousVerifier = HeapImpl.getHeapImpl().getHeapVerifierImpl();
            HeapImpl.getHeapImpl().setHeapVerifierImpl(verifier);
            result = verifier.verifyOperation(message, occasion);
            HeapImpl.getHeapImpl().setHeapVerifierImpl(previousVerifier);
        }

        public boolean getResult() {
            return result;
        }
    }

    @Override
    public boolean verify(String message) {
        final VerifyVMOperation verifyOperation = new VerifyVMOperation(message, this, HeapVerifier.Occasion.BEFORE_COLLECTION);
        verifyOperation.enqueue();
        return verifyOperation.getResult();
    }

    boolean verifyOperation(String message, HeapVerifier.Occasion occasion) {
        VMOperation.guaranteeInProgress("Can only verify from a VMOperation.");
        final Log trace = getTraceLog();
        trace.string("[HeapVerifierImpl.verify ").string(" occasion: ").string(occasion.name()).string(" cause: ").string(message).string(":");
        trace.newline();

        setCause(message);
        ThreadLocalAllocation.disableThreadLocalAllocation();
        boolean result = true;
        /* Verify the native image heap. */
        if (!verifyBootImageObjects()) {
            getWitnessLog().string("[HeapVerifierImpl.verify:").string("  native image fails to verify").string("]").newline();
            result = false;
        }
        /* Verify the young generation. */
        if (!verifyYoungGeneration(occasion)) {
            getWitnessLog().string("[HeapVerifierImpl.verify:").string("  young generation fails to verify").string("]").newline();
            result = false;
        }
        /* Verify the old generation. */
        if (!verifyOldGeneration(occasion)) {
            getWitnessLog().string("[HeapVerifierImpl.verify:").string("  old generation fails to verify").string("]").newline();
            result = false;
        }

        trace.string("  returns: ").bool(result).string("]").newline();
        if ((!result) && HeapOptions.HeapVerificationFailureIsFatal.getValue()) {
            HeapVerifier.HeapVerificationError.throwError();
        }
        return result;
    }

    @Override
    public Log getTraceLog() {
        return (HeapOptions.TraceHeapVerification.getValue() ? Log.log() : Log.noopLog());
    }

    @Override
    public Log getWitnessLog() {
        return witnessLog;
    }

    private boolean verifyBootImageObjects() {
        final boolean ropResult = verifyBootImageObjects(NativeImageInfo.firstReadOnlyPrimitiveObject, NativeImageInfo.lastReadOnlyPrimitiveObject);
        final boolean rorResult = verifyBootImageObjects(NativeImageInfo.firstReadOnlyReferenceObject, NativeImageInfo.lastReadOnlyReferenceObject);
        final boolean rwpResult = verifyBootImageObjects(NativeImageInfo.firstWritablePrimitiveObject, NativeImageInfo.lastWritablePrimitiveObject);
        final boolean rwrResult = verifyBootImageObjects(NativeImageInfo.firstWritableReferenceObject, NativeImageInfo.lastWritableReferenceObject);
        return ropResult && rorResult && rwpResult && rwrResult;
    }

    private boolean verifyBootImageObjects(Object firstObject, Object lastObject) {
        final Log trace = getTraceLog();
        trace.string("[HeapVerifierImpl.verifyBootImageObjects:").newline();

        final Pointer firstPointer = Word.objectToUntrackedPointer(firstObject);
        final Pointer lastPointer = Word.objectToUntrackedPointer(lastObject);
        trace.string("  [ firstPointer: ").hex(firstPointer).string("  .. lastPointer: ").hex(lastPointer).string(" ]").newline();

        if ((firstObject == null) || (lastObject == null)) {
            trace.string("  returns: true because boundary object is null").string("]").newline();
            return true;
        }

        boolean result = true;
        Pointer currentPointer = firstPointer;
        while (currentPointer.belowOrEqual(lastPointer)) {
            final Object currentObject = currentPointer.toObject();
            /* Make sure obj is marked as a SystemType object. */
            if (!ObjectHeaderImpl.getObjectHeaderImpl().isNonHeapAllocatedCarefully(currentObject)) {
                result = false;
                try (Log witness = getWitnessLog()) {
                    witness.string("[HeapVerifierImpl.verifyBootImageObjects:").string("  [ firstPointer: ").hex(firstPointer).string("  .. lastPointer: ").hex(lastPointer).string(" ]");
                    witness.string("  current: ").hex(currentPointer).string("  object is not NonHeapAllocated").string("]").newline();
                }
            }
            /* Make sure obj is an object. */
            if (!verifyObjectAt(currentPointer)) {
                result = false;
                try (Log witness = getWitnessLog()) {
                    witness.string("[HeapVerifierImpl.verifyBootImageObjects:").string("  [ firstPointer: ").hex(firstPointer).string("  .. lastPointer: ").hex(lastPointer).string(" ]");
                    witness.string("  current: ").hex(currentPointer).string("  object does not verify").string("]").newline();
                }
            }
            currentPointer = LayoutEncoding.getObjectEnd(currentObject);
        }

        trace.string("  returns: ").bool(result).string("]").newline();

        return true;
    }

    private static boolean verifyYoungGeneration(HeapVerifier.Occasion occasion) {
        final Generation youngGeneration = HeapImpl.getHeapImpl().getYoungGeneration();
        return youngGeneration.verify(occasion);
    }

    private static boolean verifyOldGeneration(HeapVerifier.Occasion occasion) {
        final OldGeneration oldGeneration = HeapImpl.getHeapImpl().getOldGeneration();
        return oldGeneration.verify(occasion);
    }

    /**
     * For debugging: look for objects with interior references to outside the heap.
     *
     * That includes: references that are to zapped objects, and references that aren't to the heap.
     */
    private boolean noReferencesOutsideHeap(Object obj) {
        final Log trace = getTraceLog();
        trace.string("[HeapVerifierImpl.noReferencesToZappedObjectsVerifier:");
        trace.string("  obj: ").object(obj).string("  obj.getClass: ").string(obj.getClass().getName());

        final ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
        final UnsignedWord header = ObjectHeaderImpl.readHeaderFromObjectCarefully(obj);
        trace.string("  header: ").hex(header).string("  objectHeader: ").string(ohi.toStringFromObject(obj));

        final Pointer objPointer = Word.objectToUntrackedPointer(obj);
        trace.string("  objPointer: ").hex(objPointer);

        boolean result = InteriorObjRefWalker.walkObject(obj, noReferencesOutsideHeapVisitor);
        if (!result) {
            try (Log witness = getWitnessLog()) {
                witness.string("[HeapVerifierImpl.noReferencesOutsideHeap:").string("  cause: ").string(getCause());
                witness.string("  obj: ").string(obj.getClass().getName()).string("@").hex(objPointer);
                witness.string("  header: ").hex(header).string("  objectHeader: ").string(ohi.toStringFromObject(obj)).string("]").newline();
            }
        }

        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    /** An ObjectReferenceVisitor to check for references outside of the heap. */
    private static class NoReferencesOutsideHeapVisitor implements ObjectReferenceVisitor {
        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            final HeapVerifierImpl verifier = HeapImpl.getHeapImpl().getHeapVerifierImpl();
            final Pointer objPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (objPointer.isNull()) {
                return true;
            }

            /* Consider the field pointer. */
            if (!compressed && (objPointer.equal(HeapPolicy.getProducedHeapChunkZapWord()) || objPointer.equal(HeapPolicy.getConsumedHeapChunkZapWord()))) {
                try (Log witness = verifier.getWitnessLog()) {
                    witness.string("[HeapVerifierImpl.noReferencesOutsideHeap:").string("  cause: ").string(verifier.getCause());
                    witness.string("  contains zapped field Pointer: ").hex(objPointer).string("  at: ").hex(objRef).string("]").newline();
                }
                return false;
            }
            if (!HeapVerifierImpl.slowlyFindPointer(objPointer)) {
                try (Log witness = verifier.getWitnessLog()) {
                    witness.string("[HeapVerifierImpl.noReferencesOutsideHeap:").string("  cause: ").string(verifier.getCause());
                    witness.string("  at: ").hex(objRef).string("  contains fieldPointer: ").hex(objPointer).string("  that is not a reference to the heap").newline();
                    witness.string("    Foolishly trying to look at the object pointed to by the fieldPointer:");
                    final UnsignedWord fieldHeader = ObjectHeaderImpl.readHeaderFromPointerCarefully(objPointer);
                    final ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
                    witness.string("  fieldHeader: ").string(ohi.toStringFromHeader(fieldHeader));
                    final Object fieldObject = objPointer.toObject();
                    witness.string("  fieldObject: ").object(fieldObject).string("]").newline();
                }
                return false;
            }
            /* It is probably safe to look at the referenced object. */
            final Word readWord = objPointer.readWord(0);
            if (readWord.equal(HeapPolicy.getProducedHeapChunkZapWord()) || readWord.equal(HeapPolicy.getConsumedHeapChunkZapWord())) {
                try (Log witness = verifier.getWitnessLog()) {
                    witness.string("[HeapVerifierImpl.noReferencesOutsideHeap:").string("  cause: ").string(verifier.getCause());
                    witness.string("  contains fieldPointer: ").hex(objPointer).string("  to zapped memory: ").hex(readWord).string("  at: ").hex(objRef).string("]").newline();
                }
                return false;
            }
            return true;
        }
    }

    /** A singleton instance of the visitor. */
    private static final HeapVerifierImpl.NoReferencesOutsideHeapVisitor noReferencesOutsideHeapVisitor = new NoReferencesOutsideHeapVisitor();

    /* Look for objects with forwarded headers. */
    private boolean noReferencesToForwardedObjectsVerifier(Object obj) {
        final Log trace = getTraceLog();
        trace.string("[HeapVerifierImpl.noReferencesToForwardedObjectsVerifier:");
        trace.string("  obj: ").object(obj);
        final UnsignedWord header = ObjectHeaderImpl.readHeaderFromObjectCarefully(obj);
        trace.string("  header: ").hex(header);

        final Pointer objPointer = Word.objectToUntrackedPointer(obj);
        trace.string("  objPointer: ").hex(objPointer);

        boolean result = InteriorObjRefWalker.walkObject(obj, noReferencesToForwardedObjectsVisitor);
        if (!result) {
            try (Log witness = getWitnessLog()) {
                witness.string("[HeapVerifierImpl.noReferencesToForwardedObjectsVerifier:").string("  cause: ").string(getCause()).string("  obj: ").object(obj).string("]").newline();
            }
        }

        trace.string("]").newline();
        return result;
    }

    private static class NoReferencesToForwardedObjectsVisitor implements ObjectReferenceVisitor {
        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            final HeapImpl heap = HeapImpl.getHeapImpl();
            final HeapVerifierImpl verifier = heap.getHeapVerifierImpl();
            final Pointer objPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (objPointer.isNull()) {
                return true;
            }
            final ObjectHeaderImpl ohi = heap.getObjectHeaderImpl();
            if (ohi.isPointerToForwardedObjectCarefully(objPointer)) {
                try (Log witness = verifier.getWitnessLog()) {
                    witness.string("[HeapVerifierImpl.noReferencesToForwardedObjectsVerifier:").string("  cause: ").string(verifier.getCause());
                    witness.string("  contains fieldPointer: ").hex(objPointer).string("  to forwarded object at: ").hex(objRef).string("]").newline();
                }
                return false;
            }
            return true;
        }
    }

    private static final HeapVerifierImpl.NoReferencesToForwardedObjectsVisitor noReferencesToForwardedObjectsVisitor = new NoReferencesToForwardedObjectsVisitor();

    private static boolean verifyDiscoverableReference(Object object) {
        boolean result = true;
        Object obj = KnownIntrinsics.convertUnknownValue(object, Object.class);
        if (obj instanceof DiscoverableReference) {
            final DiscoverableReference dr = (DiscoverableReference) obj;
            result = DiscoverableReferenceProcessing.verify(dr);
        }
        return result;
    }

    public enum ChunkLimit {
        top,
        end
    }

    static boolean slowlyFindPointer(Pointer p) {
        if (slowlyFindPointerInBootImage(p)) {
            return true;
        }
        final HeapImpl heap = HeapImpl.getHeapImpl();
        if (slowlyFindPointerInYoungGeneration(p)) {
            return true;
        }
        if (slowlyFindPointerInOldGeneration(p)) {
            return true;
        }
        heap.getHeapVerifierImpl().getWitnessLog().string("[HeapVerifierImpl.slowlyFindPointer:").string("  did not find pointer in heap: ").hex(p).string("]").newline();
        return false;
    }

    /* Used by runtime verification code. */
    private static boolean slowlyFindObjectInBootImage(Object obj) {
        final Pointer objectPointer = Word.objectToUntrackedPointer(obj);
        return slowlyFindPointerInBootImage(objectPointer);
    }

    static boolean slowlyFindPointerInBootImage(Pointer objectPointer) {
        boolean result = false;
        result |= NativeImageInfo.isInReadOnlyPrimitivePartition(objectPointer);
        result |= NativeImageInfo.isInReadOnlyReferencePartition(objectPointer);
        result |= NativeImageInfo.isInWritablePrimitivePartition(objectPointer);
        result |= NativeImageInfo.isInWritableReferencePartition(objectPointer);
        return result;
    }

    private static boolean slowlyFindPointerInYoungGeneration(Pointer p) {
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final YoungGeneration youngGen = heap.getYoungGeneration();
        return youngGen.slowlyFindPointer(p);
    }

    private static boolean slowlyFindPointerInOldGeneration(Pointer p) {
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        return oldGen.slowlyFindPointer(p);
    }

    private static boolean slowlyFindPointerInUnusedSpace(Pointer p) {
        return HeapChunkProvider.get().slowlyFindPointer(p);
    }

    static boolean slowlyFindPointerInSpace(Space space, Pointer p, HeapVerifierImpl.ChunkLimit chunkLimit) {
        /* Look through all the chunks in the space. */
        /* - The aligned chunks. */
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            final Pointer start = AlignedHeapChunk.getAlignedHeapChunkStart(aChunk);
            final Pointer limit = (chunkLimit == ChunkLimit.top ? aChunk.getTop() : aChunk.getEnd());
            final boolean atLeast = start.belowOrEqual(p);
            final boolean atMost = p.belowThan(limit);
            if (atLeast && atMost) {
                return true;
            }
            aChunk = aChunk.getNext();
        }
        /* - The unaligned chunks. */
        UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            final Pointer start = UnalignedHeapChunk.getUnalignedHeapChunkStart(uChunk);
            final Pointer limit = (chunkLimit == ChunkLimit.top ? uChunk.getTop() : uChunk.getEnd());
            final boolean atLeast = start.belowOrEqual(p);
            final boolean atMost = p.belowThan(limit);
            if (atLeast && atMost) {
                return true;
            }
            uChunk = uChunk.getNext();
        }
        return false;
    }

    public static int classifyObject(Object o) {
        return classifyPointer(Word.objectToUntrackedPointer(o));
    }

    /* This could return an enum, but I want to be able to examine it easily from a debugger. */
    static int classifyPointer(Pointer p) {
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final YoungGeneration youngGen = heap.getYoungGeneration();
        final OldGeneration oldGen = heap.getOldGeneration();
        if (p.isNull()) {
            return 0;
        }
        if (slowlyFindPointerInBootImage(p)) {
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

    SpaceVerifierImpl getSpaceVerifierImpl() {
        return spaceVerifier;
    }
}
