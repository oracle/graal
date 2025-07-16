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
 * Represents the result of a {@link FieldLayoutFactory field layout strategy}.
 * <p>
 * The {@code offset} provided at index {@code idx} corresponds to the offset of the field at index
 * {@code idx} in the fields array provided to the
 * {@link FieldLayoutFactory#build(ParserField[], long, long)} call.
 * <p>
 * If the field is a static field, that offset corresponds to the offset in the static storage of
 * the class being constructed. Otherwise, the offset corresponds to an offset in instances of that
 * class.
 */
public final class FieldLayout {
    private final long afterInstanceFieldsOffset;
    private final long afterStaticFieldsOffset;
    private final long[] offsets;

    FieldLayout(long afterInstanceFieldsOffset, long afterStaticFieldsOffset, long[] offsets) {
        this.afterInstanceFieldsOffset = afterInstanceFieldsOffset;
        this.afterStaticFieldsOffset = afterStaticFieldsOffset;
        this.offsets = offsets;
    }

    /**
     * @return The size of an instance of the class being created.
     */
    public long afterInstanceFieldOffset() {
        return afterInstanceFieldsOffset;
    }

    /**
     * @return The size of the static storage of the class being created.
     */
    public long afterStaticFieldsOffset() {
        return afterStaticFieldsOffset;
    }

    /**
     * @param idx index in the fields array of the field to obtain an offset for.
     * @return the offset of the field.
     */
    public long getOffset(int idx) {
        return offsets[idx];
    }
}
