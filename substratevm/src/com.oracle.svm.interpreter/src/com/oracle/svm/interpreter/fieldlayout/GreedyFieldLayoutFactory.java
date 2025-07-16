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

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.JavaKind;
import com.oracle.svm.espresso.classfile.ParserField;

/**
 * Greedy implementation of a field layout factory.
 * <p>
 * Places large-size fields first, near the {@code startOffset}, and small fields last.
 * <p>
 * There is no hole-filling strategy.
 */
final class GreedyFieldLayoutFactory implements FieldLayoutFactory {
    @Override
    public FieldLayout build(ParserField[] fields, long startOffsetInstance, long startOffsetStatic) {
        FieldLayoutImpl forInstance = new FieldLayoutImpl(fields, startOffsetInstance, false);
        FieldLayoutImpl forStatics = new FieldLayoutImpl(fields, startOffsetInstance, true);

        long[] offsets = new long[fields.length];
        for (int i = 0; i < fields.length; i++) {
            ParserField f = fields[i];
            long offset = f.isStatic() ? forStatics.findOffset(f) : forInstance.findOffset(f);
            offsets[i] = offset;
        }

        return new FieldLayout(forInstance.getAfterFieldsOffset(), forStatics.getAfterFieldsOffset(), offsets);
    }

    private static final class FieldLayoutImpl {
        private static final int[] SIZES_IN_BYTES = new int[]{
                        /* OBJECT */ ConfigurationValues.getObjectLayout().getReferenceSize(),
                        /* LONG, DOUBLE */ 8,
                        /* INT, FLOAT */ 4,
                        /* CHAR, SHORT */ 2,
                        /* BYTE, BOOLEAN */ 1
        };

        private static final int COUNT_LEN = SIZES_IN_BYTES.length;

        private static final int OBJECT_INDEX = 0;
        private static final int LD_INDEX = 1;
        private static final int IF_INDEX = 2;
        private static final int CS_INDEX = 3;
        private static final int BB_INDEX = 4;

        private final ParserField[] fields;
        private final long startOffset;
        private final long afterFieldsOffset;

        private final boolean forStatics;

        private final int[] counts = new int[COUNT_LEN];
        private final long[] offsets = new long[COUNT_LEN];

        public long findOffset(ParserField f) {
            assert f.isStatic() == forStatics;
            int idx = indexOf(f.getKind());
            long result = offsets[idx];
            offsets[idx] = result + SIZES_IN_BYTES[idx];
            return result;
        }

        public long getAfterFieldsOffset() {
            return afterFieldsOffset;
        }

        FieldLayoutImpl(ParserField[] fields, long startOffset, boolean forStatics) {
            this.fields = fields;
            this.startOffset = startOffset;
            this.forStatics = forStatics;
            this.afterFieldsOffset = init();
        }

        private long init() {
            for (ParserField f : fields) {
                if (f.isStatic() == forStatics) {
                    int idx = indexOf(f.getKind());
                    counts[idx]++;
                }
            }
            offsets[OBJECT_INDEX] = align(startOffset, SIZES_IN_BYTES[OBJECT_INDEX]);
            int i = 1;
            for (; i < COUNT_LEN; i++) {
                long kindStartOffset = offsetAfterKind(i - 1);
                offsets[i] = align(kindStartOffset, SIZES_IN_BYTES[i]);
            }
            return offsetAfterKind(COUNT_LEN - 1);
        }

        private long offsetAfterKind(int kind) {
            return offsets[kind] + (counts[kind] * (long) SIZES_IN_BYTES[kind]);
        }

        private static int indexOf(JavaKind kind) {
            return switch (kind) {
                case Boolean, Byte -> BB_INDEX;
                case Short, Char -> CS_INDEX;
                case Int, Float -> IF_INDEX;
                case Long, Double -> LD_INDEX;
                case Object -> OBJECT_INDEX;
                default -> throw VMError.shouldNotReachHere("No such field kind: " + kind);
            };
        }

        private static long align(long value, long to) {
            long misalignment = value % to;
            if (misalignment == 0) {
                return value;
            }
            return value + (to - misalignment);
        }
    }

}
