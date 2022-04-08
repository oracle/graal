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

import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.LayoutEncoding;

public final class PodReferenceMapDecoder {
    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    public static boolean walkOffsetsFromPointer(Pointer baseAddress, int layoutEncoding, ObjectReferenceVisitor visitor, Object obj) {
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        boolean isCompressed = ReferenceAccess.singleton().haveCompressedReferences();

        UnsignedWord refOffset = LayoutEncoding.getArrayBaseOffset(layoutEncoding);
        UnsignedWord mapOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, ArrayLengthNode.arrayLength(obj) - 1);

        int nrefs;
        int gap;
        do {
            nrefs = Byte.toUnsignedInt(baseAddress.readByte(mapOffset));
            gap = Byte.toUnsignedInt(baseAddress.readByte(mapOffset.subtract(1)));
            mapOffset = mapOffset.subtract(2);

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

    private PodReferenceMapDecoder() {
    }
}
