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

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.heap.InstanceReferenceMapDecoder;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.snippets.KnownIntrinsics;

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
    public static boolean walkObjectInline(final Object obj, final ObjectReferenceVisitor visitor) {
        final DynamicHub objHub = ObjectHeader.readDynamicHubFromObject(obj);
        final int layoutEncoding = objHub.getLayoutEncoding();
        final Pointer objPointer = Word.objectToUntrackedPointer(obj);

        // Visit each Object reference in the array part of the Object.
        if (LayoutEncoding.isObjectArray(layoutEncoding)) {
            int length = KnownIntrinsics.readArrayLength(obj);
            for (int index = 0; index < length; index++) {
                final UnsignedWord elementOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, index);
                final Pointer elementPointer = objPointer.add(elementOffset);
                boolean isCompressed = ReferenceAccess.singleton().haveCompressedReferences();
                final boolean visitResult = visitor.visitObjectReferenceInline(elementPointer, isCompressed, obj);
                if (!visitResult) {
                    return false;
                }
            }
        }

        NonmovableArray<Byte> referenceMapEncoding = DynamicHubSupport.getReferenceMapEncoding();
        long referenceMapIndex = objHub.getReferenceMapIndex();

        // Visit Object reference in the fields of the Object.
        return InstanceReferenceMapDecoder.walkOffsetsFromPointer(objPointer, referenceMapEncoding, referenceMapIndex, visitor, obj);
    }
}
