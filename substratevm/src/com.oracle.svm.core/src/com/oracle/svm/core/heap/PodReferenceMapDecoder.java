/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.nodes.NewPodInstanceNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.util.UnsignedUtils;

public final class PodReferenceMapDecoder {
    @DuplicatedInNativeCode
    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    public static boolean walkOffsetsFromPointer(Pointer baseAddress, int layoutEncoding, ObjectReferenceVisitor visitor, Object obj) {
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        boolean isCompressed = ReferenceAccess.singleton().haveCompressedReferences();

        UnsignedWord refOffset = LayoutEncoding.getArrayBaseOffset(layoutEncoding);
        UnsignedWord mapOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, ArrayLengthNode.arrayLength(obj));

        int nrefs;
        int gap;
        do {
            mapOffset = mapOffset.subtract(2);
            gap = Byte.toUnsignedInt(baseAddress.readByte(mapOffset));
            nrefs = Byte.toUnsignedInt(baseAddress.readByte(mapOffset.add(1)));

            for (int i = 0; i < nrefs; i++) {
                if (!visitor.visitObjectReferenceInline(baseAddress.add(refOffset), 0, isCompressed, obj)) {
                    return false;
                }
                refOffset = refOffset.add(referenceSize);
            }
            refOffset = refOffset.add(referenceSize * gap);
        } while (gap != 0 || nrefs == 0xff);

        return true;
    }

    /**
     * Implements the allocation and the copying of the reference map and data stored in the hybrid
     * array for {@link Object#clone()}.
     */
    public static Object clone(Object original, DynamicHub hub, int layoutEncoding) {
        Class<?> nonNullHub = GraalDirectives.guardingNonNull(DynamicHub.toClass(hub));
        int length = ArrayLengthNode.arrayLength(original);
        byte[] referenceMap = extractReferenceMap(original, layoutEncoding, length);
        Object result = NewPodInstanceNode.newPodInstance(null, nonNullHub, length, referenceMap);
        copyArray(original, result, layoutEncoding, length);
        return result;
    }

    /**
     * We could optimize cloning if it turns out to be a bottleneck by avoiding this and passing the
     * existing pod to {@link NewPodInstanceNode} (and its slow path), but this needs some
     * duplication of nodes and snippets to avoid touching the {@link LocationIdentity} of extra
     * objects in situations in which we don't need to.
     */
    private static byte[] extractReferenceMap(Object obj, int layoutEncoding, int length) {
        UnsignedWord mapEndOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, length);
        UnsignedWord mapOffset = mapEndOffset;

        int gap;
        int nrefs;
        do {
            mapOffset = mapOffset.subtract(2);
            gap = Byte.toUnsignedInt(BarrieredAccess.readByte(obj, mapOffset));
            nrefs = Byte.toUnsignedInt(BarrieredAccess.readByte(obj, mapOffset.add(1)));
        } while (gap != 0 || nrefs == 0xff);

        int refMapLength = UnsignedUtils.safeToInt(mapEndOffset.subtract(mapOffset));
        byte[] refMap = new byte[refMapLength];
        for (int i = 0; i < refMapLength; i++) {
            refMap[i] = BarrieredAccess.readByte(obj, mapOffset.add(i));
        }
        return refMap;
    }

    private static void copyArray(Object original, Object copy, int layoutEncoding, int length) {
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();

        UnsignedWord refOffset = LayoutEncoding.getArrayBaseOffset(layoutEncoding);
        UnsignedWord mapOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, length);

        UnsignedWord gap;
        UnsignedWord nrefs;
        do {
            mapOffset = mapOffset.subtract(2);
            gap = WordFactory.unsigned(Byte.toUnsignedInt(BarrieredAccess.readByte(copy, mapOffset)));
            nrefs = WordFactory.unsigned(Byte.toUnsignedInt(BarrieredAccess.readByte(copy, mapOffset.add(1))));

            // Copy references separately with the required barriers
            JavaMemoryUtil.copyReferencesForward(original, refOffset, copy, refOffset, nrefs);

            // Copy primitives in between
            UnsignedWord primOffset = refOffset.add(nrefs.multiply(referenceSize));
            UnsignedWord primBytes = gap.multiply(referenceSize);
            JavaMemoryUtil.copyForward(original, primOffset, copy, primOffset, primBytes);

            refOffset = primOffset.add(primBytes);
        } while (gap.notEqual(0) || nrefs.equal(0xff));

        // The loop above could be optimized for very long sequences of references or primitive
        // values encoded in multiple reference map entries, but those should be rare in practice.

        // Copy primitives between last reference and reference map
        UnsignedWord primBytes = mapOffset.subtract(refOffset);
        JavaMemoryUtil.copyForward(original, refOffset, copy, refOffset, primBytes);
    }

    private PodReferenceMapDecoder() {
    }
}
