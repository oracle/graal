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
package com.oracle.svm.interpreter.fieldlayout;

import com.oracle.svm.espresso.classfile.ParserField;

/**
 * Represents the result of a field layout strategy.
 * <p>
 * The {@code offset} provided at index {@code idx} corresponds to the offset of the field at index
 * {@code idx} in the fields array provided to the {@link FieldLayout#build(ParserField[], long)}
 * call.
 * <p>
 * If the field is a static field, that offset corresponds to the offset in the static storage of
 * the class being constructed. Otherwise, the offset corresponds to an offset in instances of that
 * class.
 */
public final class FieldLayout {
    private final int afterInstanceFieldsOffset;
    private final int[] offsets;
    private final int[] referenceFieldsOffsets;
    private final int staticReferenceFieldCount;
    private final int staticPrimitiveFieldSize;

    FieldLayout(int afterInstanceFieldsOffset, int[] offsets, int[] referenceFieldsOffsets, int staticReferenceFieldCount, int staticPrimitiveFieldSize) {
        this.afterInstanceFieldsOffset = afterInstanceFieldsOffset;
        this.offsets = offsets;
        this.referenceFieldsOffsets = referenceFieldsOffsets;
        this.staticReferenceFieldCount = staticReferenceFieldCount;
        this.staticPrimitiveFieldSize = staticPrimitiveFieldSize;
    }

    /**
     * Creates a field layout from the given fields array and start offset.
     *
     * @param parsedDeclaredFields The parsed declared fields, obtained from
     *            {@link com.oracle.svm.espresso.classfile.ClassfileParser#parse parsing} a
     *            classfile.
     * @param startOffset The offset at which the fields should start. This generally corresponds to
     *            the offset after a class' super's own fields.
     *
     * @implNote The strategy used for layouting fields is implemented at
     *           {@link GreedyFieldLayoutStrategy}.
     */
    public static FieldLayout build(ParserField[] parsedDeclaredFields, long startOffset) {
        return GreedyFieldLayoutStrategy.build(parsedDeclaredFields, startOffset);
    }

    /**
     * @return The offset after all instance fields.
     */
    public int afterInstanceFieldsOffset() {
        return afterInstanceFieldsOffset;
    }

    /**
     * @param idx index in the fields array of the field to obtain an offset for.
     * @return the offset of the field.
     */
    public int getOffset(int idx) {
        return offsets[idx];
    }

    /**
     * @return Offsets of all the declared instance fields that have a reference type.
     */
    public int[] getReferenceFieldsOffsets() {
        return referenceFieldsOffsets;
    }

    /**
     * @return The number of static reference fields.
     */
    public int getStaticReferenceFieldCount() {
        return staticReferenceFieldCount;
    }

    /**
     * @return The size of static primitive fields to allocate.
     */
    public int getStaticPrimitiveFieldSize() {
        return staticPrimitiveFieldSize;
    }
}
