/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.gc;

import java.lang.ref.Reference;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.ImageHeapWalker;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.graal.compiler.word.Word;

/**
 * Verifies correctness of objects in the heap (see {@link #verifyObject}).
 */
public class WasmHeapVerifier {

    private static final ObjectVerifier OBJECT_VERIFIER = new ObjectVerifier();
    private static final ImageHeapRegionVerifier IMAGE_HEAP_OBJECT_VERIFIER = new ImageHeapRegionVerifier();
    private static final ObjectReferenceVerifier REFERENCE_VERIFIER = new ObjectReferenceVerifier();

    /**
     * @return Whether verification was successful.
     */
    public static boolean verify() {
        boolean success = true;
        success &= verifyImageHeapObjects();
        success &= verifyCollectedHeap();
        return success;
    }

    /**
     * Verifies all objects in the image heap.
     *
     * @return Whether successful
     */
    private static boolean verifyImageHeapObjects() {
        IMAGE_HEAP_OBJECT_VERIFIER.initialize();
        ImageHeapWalker.walkRegions(WasmHeap.getImageHeapInfo(), IMAGE_HEAP_OBJECT_VERIFIER);
        return IMAGE_HEAP_OBJECT_VERIFIER.result;
    }

    /**
     * Verifies all allocated objects.
     *
     * @return Whether successful
     */
    private static boolean verifyCollectedHeap() {
        OBJECT_VERIFIER.initialize();
        WasmAllocation.walkObjects(OBJECT_VERIFIER);
        return OBJECT_VERIFIER.result;
    }

    /**
     * Verifies an object in the heap (image heap or collected heap).
     * <p>
     * Tests the following:
     *
     * <ul>
     * <li>Alignment</li>
     * <li>The header is white</li>
     * <li>Header points to valid hub</li>
     * <li>It has valid references (see {@link #verifyReference})</li>
     * </ul>
     */
    private static boolean verifyObject(Object obj) {
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

        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord header = oh.readHeaderFromPointer(ptr);
        if (!WasmObjectHeader.isWhiteHeader(header)) {
            Log.log().string("Object ").zhex(ptr).string(" has a non-white header: ").zhex(header).newline();
            return false;
        }

        DynamicHub hub = KnownIntrinsics.readHub(obj);
        if (!Heap.getHeap().isInImageHeap(hub)) {
            Log.log().string("Object ").zhex(ptr).string(" references a hub that is not in the image heap: ").zhex(Word.objectToUntrackedPointer(hub)).newline();
            return false;
        }

        return verifyReferences(obj);
    }

    /**
     * Verifies that all references inside the given object are valid.
     *
     * @see #verifyReference
     */
    private static boolean verifyReferences(Object obj) {
        if (!WasmLMGC.Options.WasmVerifyReferences.getValue()) {
            return true;
        }

        REFERENCE_VERIFIER.initialize();
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

    /**
     * Checks that the given reference is valid (in the heap and points to valid object).
     *
     * @param parentObject The parent object where the reference came from or null if the reference
     *            is on the stack.
     * @param objRef Pointer to the given reference.
     * @param referencedObject Pointer to the underlying object.
     * @return true iff verification succeeded
     */
    private static boolean verifyReference(Object parentObject, Pointer objRef, Pointer referencedObject) {
        if (referencedObject.isNull()) {
            return true;
        }

        if (!WasmHeap.getHeapImpl().isInHeap(referencedObject)) {
            Log.log().string("Object reference at ").zhex(objRef).string(" points outside the Java heap: ").zhex(referencedObject).string(". ");
            printParentObject(parentObject);
            return false;
        }

        if (!WasmObjectHeader.getObjectHeaderImpl().pointsToObjectHeader(referencedObject)) {
            Log.log().string("Object reference at ").zhex(objRef).string(" does not point to a Java object or the object header of the Java object is invalid: ").zhex(referencedObject)
                            .string(". ");
            printParentObject(parentObject);
            return false;
        }

        return true;
    }

    private static void printParentObject(Object parentObject) {
        if (parentObject != null) {
            Log.log().string("The object that contains the invalid reference is of type ").string(parentObject.getClass().getName()).newline();
        } else {
            Log.log().string("The invalid reference is on the stack.").newline();
        }
    }

    /**
     * See {@link #verifyObject}.
     */
    private static class ObjectVerifier implements ObjectVisitor {
        protected boolean result;

        @Platforms(Platform.HOSTED_ONLY.class)
        ObjectVerifier() {
        }

        void initialize() {
            this.result = true;
        }

        @Override
        public void visitObject(Object object) {
            result &= verifyObject(object);
        }
    }

    /**
     * Visits objects in image heap regions and verifies that visited objects are in the image heap.
     *
     * @see ObjectVerifier
     */
    private static class ImageHeapRegionVerifier extends ObjectVerifier implements MemoryWalker.ImageHeapRegionVisitor {

        @Platforms(Platform.HOSTED_ONLY.class)
        ImageHeapRegionVerifier() {
        }

        @Override
        public <T> void visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            access.visitObjects(region, this);
        }

        @Override
        public void visitObject(Object object) {
            Word pointer = Word.objectToUntrackedPointer(object);
            if (!Heap.getHeap().isInImageHeap(object)) {
                Log.log().string("Image heap object ").zhex(pointer).string(" is not considered as part of the image heap.").newline();
                result = false;
            }

            super.visitObject(object);
        }
    }

    /**
     * See {@link #verifyReference}.
     */
    static class ObjectReferenceVerifier implements ObjectReferenceVisitor {
        boolean result;

        @Platforms(Platform.HOSTED_ONLY.class)
        ObjectReferenceVerifier() {
        }

        public void initialize() {
            this.result = true;
        }

        @Override
        public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
            Pointer pos = firstObjRef;
            Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
            while (pos.belowThan(end)) {
                visitObjectReference(pos, compressed, holderObject);
                pos = pos.add(referenceSize);
            }
        }

        private void visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            result &= verifyReference(holderObject, objRef, compressed);
        }
    }
}
