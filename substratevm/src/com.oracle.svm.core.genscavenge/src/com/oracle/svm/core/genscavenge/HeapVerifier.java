/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;

public final class HeapVerifier {
    private static final ObjectVerifier OBJECT_VERIFIER = new ObjectVerifier();
    private static final ImageHeapRegionVerifier IMAGE_HEAP_OBJECT_VERIFIER = new ImageHeapRegionVerifier();
    private static final ObjectReferenceVerifier REFERENCE_VERIFIER = new ObjectReferenceVerifier();

    @Platforms(Platform.HOSTED_ONLY.class)
    private HeapVerifier() {
    }

    public static boolean verify(Occasion occasion) {
        boolean success = true;
        success &= verifyImageHeapObjects();
        success &= verifyYoungGeneration(occasion);
        success &= verifyOldGeneration();
        success &= verifyRememberedSets();
        return success;
    }

    private static boolean verifyImageHeapObjects() {
        if (HeapImpl.usesImageHeapChunks()) {
            return verifyChunkedImageHeap();
        } else {
            return verifyNonChunkedImageHeap();
        }
    }

    private static boolean verifyChunkedImageHeap() {
        boolean success = true;
        ImageHeapInfo info = HeapImpl.getImageHeapInfo();
        success &= verifyAlignedChunks(null, info.getFirstWritableAlignedChunk());
        success &= verifyUnalignedChunks(null, info.getFirstWritableUnalignedChunk());
        return success;
    }

    private static boolean verifyNonChunkedImageHeap() {
        IMAGE_HEAP_OBJECT_VERIFIER.initialize();
        ImageHeapWalker.walkRegions(HeapImpl.getImageHeapInfo(), IMAGE_HEAP_OBJECT_VERIFIER);
        return IMAGE_HEAP_OBJECT_VERIFIER.getResult();
    }

    private static boolean verifyYoungGeneration(Occasion occasion) {
        boolean success = true;
        YoungGeneration youngGeneration = HeapImpl.getHeapImpl().getYoungGeneration();
        if (occasion == HeapVerifier.Occasion.AFTER_COLLECTION) {
            Space eden = youngGeneration.getEden();
            if (!eden.isEmpty()) {
                Log.log().string("Eden contains chunks after a collection: firstAlignedChunk: ").zhex(eden.getFirstAlignedHeapChunk()).string(", firstUnalignedChunk: ")
                                .zhex(eden.getFirstUnalignedHeapChunk()).newline();
                success = false;
            }
        }

        success &= verifySpace(youngGeneration.getEden());

        for (int i = 0; i < youngGeneration.getMaxSurvivorSpaces(); i++) {
            Space fromSpace = youngGeneration.getSurvivorFromSpaceAt(i);
            Space toSpace = youngGeneration.getSurvivorToSpaceAt(i);

            if (!toSpace.isEmpty()) {
                Log.log().string("Survivor to-space ").signed(i).string(" contains chunks: firstAlignedChunk: ").zhex(toSpace.getFirstAlignedHeapChunk()).string(", firstUnalignedChunk: ")
                                .zhex(toSpace.getFirstUnalignedHeapChunk()).newline();
                success = false;
            }

            success &= verifySpace(fromSpace);
            success &= verifySpace(toSpace);
        }

        return success;
    }

    private static boolean verifyOldGeneration() {
        boolean success = true;
        OldGeneration oldGeneration = HeapImpl.getHeapImpl().getOldGeneration();
        Space fromSpace = oldGeneration.getFromSpace();
        Space toSpace = oldGeneration.getToSpace();

        if (!toSpace.isEmpty()) {
            Log.log().string("Old generation to-space contains chunks: firstAlignedChunk: ").zhex(toSpace.getFirstAlignedHeapChunk()).string(", firstUnalignedChunk: ")
                            .zhex(toSpace.getFirstUnalignedHeapChunk()).newline();
            success = false;
        }

        success &= verifySpace(fromSpace);
        success &= verifySpace(toSpace);
        return success;
    }

    private static boolean verifyRememberedSets() {
        /*
         * After we are done with all other verifications, it is guaranteed that the heap is in a
         * reasonable state. Now, we can verify the remembered sets without having to worry about
         * basic heap consistency.
         */
        if (!SubstrateOptions.useRememberedSet() || !HeapOptions.VerifyRememberedSet.getValue()) {
            return true;
        }

        /*
         * It would be nice to assert that all cards in the image heap and old generation are clean
         * after a garbage collection. For the image heap, it is pretty much impossible to do that
         * as the GC itself dirties the card table. For the old generation, it is also not possible
         * at the moment because the reference handling may result in dirty cards.
         */

        boolean success = true;
        RememberedSet rememberedSet = RememberedSet.get();
        if (HeapImpl.usesImageHeapChunks()) {
            /*
             * For the image heap, we can't verify that all cards are clean after a GC because the
             * GC itself may result in dirty cards.
             */
            ImageHeapInfo info = HeapImpl.getImageHeapInfo();
            success &= rememberedSet.verify(info.getFirstWritableAlignedChunk());
            success &= rememberedSet.verify(info.getFirstWritableUnalignedChunk());
        }

        OldGeneration oldGeneration = HeapImpl.getHeapImpl().getOldGeneration();
        Space toSpace = oldGeneration.getToSpace();
        success &= rememberedSet.verify(toSpace.getFirstAlignedHeapChunk());
        success &= rememberedSet.verify(toSpace.getFirstUnalignedHeapChunk());

        Space fromSpace = oldGeneration.getFromSpace();
        success &= rememberedSet.verify(fromSpace.getFirstAlignedHeapChunk());
        success &= rememberedSet.verify(fromSpace.getFirstUnalignedHeapChunk());
        return success;
    }

    private static boolean verifySpace(Space space) {
        boolean success = true;
        success &= verifyChunkList(space, "aligned", space.getFirstAlignedHeapChunk(), space.getLastAlignedHeapChunk());
        success &= verifyChunkList(space, "unaligned", space.getFirstUnalignedHeapChunk(), space.getLastUnalignedHeapChunk());
        success &= verifyAlignedChunks(space, space.getFirstAlignedHeapChunk());
        success &= verifyUnalignedChunks(space, space.getFirstUnalignedHeapChunk());
        return success;
    }

    private static boolean verifyChunkList(Space space, String kind, HeapChunk.Header<?> firstChunk, HeapChunk.Header<?> lastChunk) {
        boolean result = true;
        HeapChunk.Header<?> current = firstChunk;
        HeapChunk.Header<?> previous = WordFactory.nullPointer();
        while (current.isNonNull()) {
            HeapChunk.Header<?> previousOfCurrent = HeapChunk.getPrevious(current);
            if (previousOfCurrent.notEqual(previous)) {
                Log.log().string("Verification failed for the doubly-linked list that holds ").string(kind).string(" chunks: space: ").string(space.getName()).string(", current: ").zhex(current)
                                .string(", current.previous: ").zhex(previousOfCurrent).string(", previous: ").zhex(previous).newline();
                result = false;
            }
            previous = current;
            current = HeapChunk.getNext(current);
        }

        if (previous.notEqual(lastChunk)) {
            Log.log().string("Verification failed for the doubly-linked list that holds ").string(kind).string(" chunks: space: ").string(space.getName()).string(", previous: ").zhex(previous)
                            .string(", lastChunk: ").zhex(lastChunk).newline();
            result = false;
        }
        return result;
    }

    private static boolean verifyAlignedChunks(Space space, AlignedHeader firstAlignedHeapChunk) {
        boolean success = true;
        AlignedHeader aChunk = firstAlignedHeapChunk;
        while (aChunk.isNonNull()) {
            if (space != aChunk.getSpace()) {
                Log.log().string("Space ").string(space.getName()).string(" contains aligned chunk ").zhex(aChunk).string(" but the chunk does not reference the correct space: ")
                                .zhex(Word.objectToUntrackedPointer(aChunk.getSpace())).newline();
                success = false;
            }

            OBJECT_VERIFIER.initialize(aChunk, WordFactory.nullPointer());
            AlignedHeapChunk.walkObjects(aChunk, OBJECT_VERIFIER);
            aChunk = HeapChunk.getNext(aChunk);
            success &= OBJECT_VERIFIER.result;
        }
        return success;
    }

    private static boolean verifyUnalignedChunks(Space space, UnalignedHeader firstUnalignedHeapChunk) {
        boolean success = true;
        UnalignedHeader uChunk = firstUnalignedHeapChunk;
        while (uChunk.isNonNull()) {
            if (space != uChunk.getSpace()) {
                Log.log().string("Space ").string(space.getName()).string(" contains unaligned chunk ").zhex(uChunk).string(" but the chunk does not reference the correct space: ")
                                .zhex(Word.objectToUntrackedPointer(uChunk.getSpace())).newline();
                success = false;
            }

            OBJECT_VERIFIER.initialize(WordFactory.nullPointer(), uChunk);
            UnalignedHeapChunk.walkObjects(uChunk, OBJECT_VERIFIER);
            uChunk = HeapChunk.getNext(uChunk);
            success &= OBJECT_VERIFIER.result;
        }
        return success;
    }

    // This method is executed exactly once per object in the heap.
    private static boolean verifyObject(Object obj, AlignedHeader aChunk, UnalignedHeader uChunk) {
        Pointer ptr = Word.objectToUntrackedPointer(obj);
        if (ptr.isNull()) {
            Log.log().string("Encounter a null pointer while walking the heap objects.").newline();
            return false;
        }

        int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        if (ptr.unsignedRemainder(objectAlignment).notEqual(0)) {
            Log.log().string("Object ").zhex(ptr).string(" is not properly aligned to ").signed(objectAlignment).string(" bytes.").newline();
            return false;
        }

        UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(ptr);
        if (ObjectHeaderImpl.isProducedHeapChunkZapped(header) || ObjectHeaderImpl.isConsumedHeapChunkZapped(header)) {
            Log.log().string("Object ").zhex(ptr).string(" has a zapped header: ").zhex(header).newline();
            return false;
        }

        if (ObjectHeaderImpl.isForwardedHeader(header)) {
            Log.log().string("Object ").zhex(ptr).string(" has a forwarded header: ").zhex(header).newline();
            return false;
        }

        if (HeapImpl.usesImageHeapChunks() || !HeapImpl.getHeapImpl().isInImageHeap(obj)) {
            assert aChunk.isNonNull() ^ uChunk.isNonNull();
            HeapChunk.Header<?> expectedChunk = aChunk.isNonNull() ? aChunk : uChunk;
            HeapChunk.Header<?> chunk = HeapChunk.getEnclosingHeapChunk(obj);
            if (chunk.notEqual(expectedChunk)) {
                Log.log().string("Object ").zhex(ptr).string(" should have ").zhex(expectedChunk).string(" as its enclosing chunk but getEnclosingHeapChunk returned ").zhex(chunk).newline();
                return false;
            }

            Pointer chunkStart = HeapChunk.asPointer(chunk);
            Pointer chunkTop = HeapChunk.getTopPointer(chunk);
            if (chunkStart.aboveOrEqual(ptr) || chunkTop.belowOrEqual(ptr)) {
                Log.log().string("Object ").zhex(ptr).string(" is not within the allocated part of the chunk: ").zhex(chunkStart).string(" - ").zhex(chunkTop).string("").newline();
                return false;
            }

            if (aChunk.isNonNull()) {
                if (!ObjectHeaderImpl.isAlignedHeader(header)) {
                    Log.log().string("Header of object ").zhex(ptr).string(" is not marked as aligned: ").zhex(header).newline();
                    return false;
                }
            } else {
                assert uChunk.isNonNull();
                if (!ObjectHeaderImpl.isUnalignedHeader(header)) {
                    Log.log().string("Header of object ").zhex(ptr).string(" is not marked as unaligned: ").zhex(header).newline();
                    return false;
                }
            }

            Space space = chunk.getSpace();
            if (space == null) {
                if (!HeapImpl.getHeapImpl().isInImageHeap(obj)) {
                    Log.log().string("Object ").zhex(ptr).string(" is not an image heap object even though the space of the parent chunk ").zhex(chunk).string(" is null.").newline();
                    return false;
                }
                // Not all objects in the image heap have the remembered set bit in the header, so
                // we can't verify that this bit is set.

            } else if (space.isOldSpace()) {
                if (SubstrateOptions.useRememberedSet() && !RememberedSet.get().hasRememberedSet(header)) {
                    Log.log().string("Object ").zhex(ptr).string(" is in old generation chunk ").zhex(chunk).string(" but does not have a remembered set.").newline();
                    return false;
                }
            }
        }

        DynamicHub hub = KnownIntrinsics.readHub(obj);
        if (!HeapImpl.getHeapImpl().isInImageHeap(hub)) {
            Log.log().string("Object ").zhex(ptr).string(" references a hub that is not in the image heap: ").zhex(Word.objectToUntrackedPointer(hub)).newline();
            return false;
        }

        return verifyReferences(obj);
    }

    // This method is executed exactly once per object in the heap.
    private static boolean verifyReferences(Object obj) {
        if (!HeapOptions.VerifyReferences.getValue()) {
            return true;
        }

        REFERENCE_VERIFIER.initialize(obj);
        InteriorObjRefWalker.walkObject(obj, REFERENCE_VERIFIER);

        boolean success = REFERENCE_VERIFIER.result;
        DynamicHub hub = KnownIntrinsics.readHub(obj);
        if (hub.isReferenceInstanceClass()) {
            // The referent field of java.lang.Reference is excluded from the reference map, so we
            // need to verify it separately.
            Reference<?> ref = (Reference<?>) obj;
            success &= verifyReferent(ref);
        }
        return success;
    }

    private static boolean verifyReferent(Reference<?> ref) {
        return verifyReference(ref, ReferenceInternals.getReferentFieldAddress(ref), ReferenceInternals.getReferentPointer(ref));
    }

    public static boolean verifyReference(Object parentObject, Pointer objRef, boolean compressed) {
        Pointer ptr = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
        return verifyReference(parentObject, objRef, ptr);
    }

    // This method is executed exactly once for each object reference in the heap and on the stack.
    private static boolean verifyReference(Object parentObject, Pointer reference, Pointer referencedObject) {
        if (referencedObject.isNull()) {
            return true;
        }

        if (!HeapImpl.getHeapImpl().isInHeap(referencedObject)) {
            Log.log().string("Object reference at ").zhex(reference).string(" points outside the Java heap: ").zhex(referencedObject).string(". ");
            if (parentObject != null) {
                Log.log().string("The object that contains the invalid reference is of type ").string(parentObject.getClass().getName()).newline();
            } else {
                Log.log().string("The invalid reference is on the stack.").newline();
            }
            return false;
        }

        return true;
    }

    private static class ImageHeapRegionVerifier implements MemoryWalker.ImageHeapRegionVisitor {
        private final ImageHeapObjectVerifier objectVerifier;

        @Platforms(Platform.HOSTED_ONLY.class)
        ImageHeapRegionVerifier() {
            objectVerifier = new ImageHeapObjectVerifier();
        }

        public void initialize() {
            objectVerifier.initialize(WordFactory.nullPointer(), WordFactory.nullPointer());
        }

        public boolean getResult() {
            return objectVerifier.result;
        }

        @Override
        public <T> boolean visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            access.visitObjects(region, objectVerifier);
            return true;
        }
    }

    private static class ObjectVerifier implements ObjectVisitor {
        protected boolean result;
        private AlignedHeader aChunk;
        private UnalignedHeader uChunk;

        @Platforms(Platform.HOSTED_ONLY.class)
        ObjectVerifier() {
        }

        @SuppressWarnings("hiding")
        void initialize(AlignedHeader aChunk, UnalignedHeader uChunk) {
            this.result = true;
            this.aChunk = aChunk;
            this.uChunk = uChunk;
        }

        @Override
        public boolean visitObject(Object object) {
            result &= verifyObject(object, aChunk, uChunk);
            return true;
        }
    }

    private static class ImageHeapObjectVerifier extends ObjectVerifier {
        @Platforms(Platform.HOSTED_ONLY.class)
        ImageHeapObjectVerifier() {
        }

        @Override
        public boolean visitObject(Object object) {
            Word pointer = Word.objectToUntrackedPointer(object);
            if (!HeapImpl.getHeapImpl().isInImageHeap(object)) {
                Log.log().string("Image heap object ").zhex(pointer).string(" is not considered as part of the image heap.").newline();
                result = false;
            }

            return super.visitObject(object);
        }
    }

    private static class ObjectReferenceVerifier implements ObjectReferenceVisitor {
        private Object parentObject;
        private boolean result;

        @Platforms(Platform.HOSTED_ONLY.class)
        ObjectReferenceVerifier() {
        }

        @SuppressWarnings("hiding")
        public void initialize(Object parentObject) {
            this.parentObject = parentObject;
            this.result = true;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            result &= verifyReference(parentObject, objRef, compressed);
            return true;
        }
    }

    public enum Occasion {
        BEFORE_COLLECTION,
        AFTER_COLLECTION
    }
}
