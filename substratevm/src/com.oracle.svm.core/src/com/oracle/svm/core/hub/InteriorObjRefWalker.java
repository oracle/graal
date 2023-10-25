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
package com.oracle.svm.core.hub;

import java.util.function.IntConsumer;

import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.InstanceReferenceMapDecoder;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.heap.PodReferenceMapDecoder;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.util.VMError;

/**
 * The vanilla walkObject and walkOffsetsFromPointer methods are not inlined, but there are
 * walkObjectInline and walkOffsetsFromPointerInline methods available for performance critical
 * code.
 */

public class InteriorObjRefWalker {

    /**
     * Walk a possibly-hybrid Object, consisting of both an array and some fixed fields.
     *
     * @param obj The Object to be walked.
     * @param visitor The visitor to be applied to each Object reference in the Object.
     * @return True if the walk was successful, or false otherwise.
     */
    @NeverInline("Non-performance critical version")
    public static boolean walkObject(final Object obj, final ObjectReferenceVisitor visitor) {
        return walkObjectInline(obj, visitor);
    }

    @AlwaysInline("Performance critical version")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean walkObjectInline(final Object obj, final ObjectReferenceVisitor visitor) {
        final DynamicHub objHub = ObjectHeader.readDynamicHubFromObject(obj);
        final Pointer objPointer = Word.objectToUntrackedPointer(obj);

        switch (objHub.getHubType()) {
            case HubType.INSTANCE:
            case HubType.REFERENCE_INSTANCE:
                return walkInstance(obj, visitor, objHub, objPointer);
            case HubType.POD_INSTANCE:
                return walkPod(obj, visitor, objHub, objPointer);
            case HubType.STORED_CONTINUATION_INSTANCE:
                return walkStoredContinuation(obj, visitor);
            case HubType.OTHER:
                return walkOther();
            case HubType.PRIMITIVE_ARRAY:
                return true;
            case HubType.OBJECT_ARRAY:
                return walkObjectArray(obj, visitor, objHub, objPointer);
        }

        throw VMError.shouldNotReachHere("Object with invalid hub type.");
    }

    public static boolean walkInstanceReferenceOffsets(DynamicHub objHub, IntConsumer offsetConsumer) {
        if (objHub.getHubType() != HubType.INSTANCE && objHub.getHubType() != HubType.REFERENCE_INSTANCE) {
            throw new IllegalArgumentException("Unsupported hub type: " + objHub.getHubType());
        }

        NonmovableArray<Byte> referenceMapEncoding = DynamicHubSupport.getReferenceMapEncoding();
        long referenceMapIndex = objHub.getReferenceMapIndex();

        return InstanceReferenceMapDecoder.walkOffsetsFromPointer(WordFactory.zero(), referenceMapEncoding, referenceMapIndex, new ObjectReferenceVisitor() {
            @Override
            public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
                offsetConsumer.accept((int) objRef.rawValue());
                return true;
            }
        }, null);
    }

    @AlwaysInline("Performance critical version")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean walkInstance(Object obj, ObjectReferenceVisitor visitor, DynamicHub objHub, Pointer objPointer) {
        NonmovableArray<Byte> referenceMapEncoding = DynamicHubSupport.getReferenceMapEncoding();
        long referenceMapIndex = objHub.getReferenceMapIndex();

        // Visit Object reference in the fields of the Object.
        return InstanceReferenceMapDecoder.walkOffsetsFromPointer(objPointer, referenceMapEncoding, referenceMapIndex, visitor, obj);
    }

    @AlwaysInline("Performance critical version")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean walkPod(Object obj, ObjectReferenceVisitor visitor, DynamicHub objHub, Pointer objPointer) {
        if (!Pod.RuntimeSupport.isPresent()) {
            throw VMError.shouldNotReachHere("Pod objects cannot be in the heap if the pod support is disabled.");
        }
        return walkInstance(obj, visitor, objHub, objPointer) && PodReferenceMapDecoder.walkOffsetsFromPointer(objPointer, objHub.getLayoutEncoding(), visitor, obj);
    }

    @AlwaysInline("Performance critical version")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean walkStoredContinuation(Object obj, ObjectReferenceVisitor visitor) {
        if (!ContinuationSupport.isSupported()) {
            throw VMError.shouldNotReachHere("Stored continuation objects cannot be in the heap if the continuation support is disabled.");
        }
        return StoredContinuationAccess.walkReferences(obj, visitor);
    }

    @AlwaysInline("Performance critical version")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean walkOther() {
        throw VMError.shouldNotReachHere("Unexpected object with hub type 'other' in the heap.");
    }

    @AlwaysInline("Performance critical version")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean walkObjectArray(Object obj, ObjectReferenceVisitor visitor, DynamicHub objHub, Pointer objPointer) {
        int length = ArrayLengthNode.arrayLength(obj);
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        boolean isCompressed = ReferenceAccess.singleton().haveCompressedReferences();

        Pointer pos = objPointer.add(LayoutEncoding.getArrayBaseOffset(objHub.getLayoutEncoding()));
        Pointer end = pos.add(WordFactory.unsigned(referenceSize).multiply(length));
        while (pos.belowThan(end)) {
            final boolean visitResult = callVisitor(obj, visitor, isCompressed, pos);
            if (!visitResult) {
                return false;
            }
            pos = pos.add(referenceSize);
        }
        return true;
    }

    @Uninterruptible(reason = "Bridge between uninterruptible and potentially interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static boolean callVisitor(Object obj, ObjectReferenceVisitor visitor, boolean isCompressed, Pointer pos) {
        return visitor.visitObjectReferenceInline(pos, 0, isCompressed, obj);
    }
}
