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

import org.graalvm.word.Pointer;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.InstanceReferenceMapDecoder;
import com.oracle.svm.core.heap.InstanceReferenceMapDecoder.InstanceReferenceMap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.heap.PodReferenceMapDecoder;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.word.Word;

public class InteriorObjRefWalker {
    /**
     * Walk a possibly-hybrid Object, consisting of both an array and some fixed fields.
     *
     * @param obj The Object to be walked.
     * @param visitor The visitor to be applied to each Object reference in the Object.
     */
    @NeverInline("Non-performance critical version")
    @Uninterruptible(reason = "Forced inlining (StoredContinuation objects must not move).")
    public static void walkObject(Object obj, ObjectReferenceVisitor visitor) {
        walkObjectInline(obj, visitor);
    }

    /**
     * Same as {@link #walkObject} but force-inlined. Performance-critical code should call this
     * method instead of {@link #walkObject}. However, be aware that this increases code size.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Forced inlining (StoredContinuation objects must not move).", callerMustBe = true)
    public static void walkObjectInline(Object obj, ObjectReferenceVisitor visitor) {
        DynamicHub objHub = KnownIntrinsics.readHub(obj);

        int hubType = objHub.getHubType();
        if (HubType.isInstance(hubType)) {
            /* Combine all instance cases to reduce the code size. */
            walkInstanceInline(obj, visitor, objHub);
        }

        switch (hubType) {
            case HubType.INSTANCE:
            case HubType.PRIMITIVE_ARRAY:
                /* Nothing (more) to do. */
                return;
            case HubType.REFERENCE_INSTANCE:
                walkReferenceSpecificFieldsInline(obj, visitor);
                return;
            case HubType.POD_INSTANCE:
                walkPodArrayPartInline(obj, visitor, objHub);
                return;
            case HubType.STORED_CONTINUATION_INSTANCE:
                walkStoredContinuationInline(obj, visitor);
                return;
            case HubType.OBJECT_ARRAY:
                walkObjectArrayInline(obj, visitor, objHub);
                return;
            case HubType.OTHER:
            default:
                throw VMError.shouldNotReachHere("Object with invalid hub type.");
        }
    }

    public static void walkInstanceReferenceOffsets(DynamicHub objHub, IntConsumer offsetConsumer) {
        if (objHub.getHubType() != HubType.INSTANCE && objHub.getHubType() != HubType.REFERENCE_INSTANCE) {
            throw new IllegalArgumentException("Unsupported hub type: " + objHub.getHubType());
        }

        InstanceReferenceMap referenceMap = DynamicHubSupport.getInstanceReferenceMap(objHub);
        InstanceReferenceMapDecoder.walkReferences(Word.zero(), referenceMap, new ObjectReferenceVisitor() {
            @Override
            public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
                Pointer pos = firstObjRef;
                Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
                while (pos.belowThan(end)) {
                    visitObjectReference(pos);
                    pos = pos.add(referenceSize);
                }
            }

            private void visitObjectReference(Pointer objRef) {
                offsetConsumer.accept((int) objRef.rawValue());
            }
        }, null);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void walkInstanceInline(Object obj, ObjectReferenceVisitor visitor, DynamicHub objHub) {
        // Visit Object reference in the fields of the Object.
        Pointer objPointer = Word.objectToUntrackedPointer(obj);
        InstanceReferenceMap referenceMap = DynamicHubSupport.getInstanceReferenceMap(objHub);
        InstanceReferenceMapDecoder.walkReferencesInline(objPointer, referenceMap, visitor, obj);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void walkReferenceSpecificFieldsInline(Object obj, ObjectReferenceVisitor visitor) {
        Pointer objPointer = Word.objectToUntrackedPointer(obj);
        long discoveredOffset = ReferenceInternals.getNextDiscoveredFieldOffset();
        Pointer objRef = objPointer.add(Word.unsigned(discoveredOffset));

        /*
         * The Object reference at the discovered offset needs to be visited separately as it is not
         * part of the reference map.
         */
        callVisitorInline(obj, visitor, objRef, 1);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void walkPodArrayPartInline(Object obj, ObjectReferenceVisitor visitor, DynamicHub objHub) {
        if (!Pod.RuntimeSupport.isPresent()) {
            throw VMError.shouldNotReachHere("Pod objects cannot be in the heap if the pod support is disabled.");
        }

        Pointer objPointer = Word.objectToUntrackedPointer(obj);
        PodReferenceMapDecoder.walkOffsetsFromPointer(objPointer, objHub.getLayoutEncoding(), visitor, obj);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    private static void walkStoredContinuationInline(Object obj, ObjectReferenceVisitor visitor) {
        if (!ContinuationSupport.isSupported()) {
            throw VMError.shouldNotReachHere("Stored continuation objects cannot be in the heap if the continuation support is disabled.");
        }
        StoredContinuationAccess.walkReferences((StoredContinuation) obj, visitor);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void walkObjectArrayInline(Object obj, ObjectReferenceVisitor visitor, DynamicHub objHub) {
        Pointer objPointer = Word.objectToUntrackedPointer(obj);
        int length = ArrayLengthNode.arrayLength(obj);
        Pointer firstObjRef = objPointer.add(LayoutEncoding.getArrayBaseOffset(objHub.getLayoutEncoding()));
        callVisitorInline(obj, visitor, firstObjRef, length);
    }

    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    @Uninterruptible(reason = "Bridge between uninterruptible and potentially interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static void callVisitorInline(Object obj, ObjectReferenceVisitor visitor, Pointer firstObjRef, int count) {
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        visitor.visitObjectReferences(firstObjRef, true, referenceSize, obj, count);
    }
}
