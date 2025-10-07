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

import jdk.graal.compiler.core.common.NumUtil;

/**
 * Greedy implementation of a field layout strategy.
 * <p>
 * Places large-size fields first, near the {@code startOffset}, and small fields last.
 * <p>
 * There is no hole-filling strategy.
 * <p>
 * For field layouting in AOT code, see {@code UniverseBuilder.layoutInstanceFields}.
 */
/* GR-69569: This should move closer to UniverseBuilder.layoutInstanceFields */
final class GreedyFieldLayoutStrategy {

    static FieldLayout build(ParserField[] declaredParsedFields, long startOffset) {
        FieldLayoutImpl.ForInstanceFields forInstance = new FieldLayoutImpl.ForInstanceFields(declaredParsedFields, startOffset);
        FieldLayoutImpl.ForStaticReferenceFields forStaticReferences = new FieldLayoutImpl.ForStaticReferenceFields(declaredParsedFields,
                        ConfigurationValues.getObjectLayout().getArrayBaseOffset(jdk.vm.ci.meta.JavaKind.Object));
        FieldLayoutImpl.ForStaticPrimitiveFields forStaticPrimitives = new FieldLayoutImpl.ForStaticPrimitiveFields(declaredParsedFields,
                        ConfigurationValues.getObjectLayout().getArrayBaseOffset(jdk.vm.ci.meta.JavaKind.Byte));

        int[] offsets = new int[declaredParsedFields.length];
        int[] referenceOffsets = new int[forInstance.getCountFor(JavaKind.Object)];
        int referencePos = 0;
        for (int i = 0; i < declaredParsedFields.length; i++) {
            ParserField f = declaredParsedFields[i];
            int offset = -1;
            if (forInstance.accepts(f)) {
                assert !forStaticReferences.accepts(f) && !forStaticPrimitives.accepts(f);
                offset = NumUtil.safeToInt(forInstance.findOffset(f));

                if (f.getKind() == JavaKind.Object && !f.isStatic()) {
                    referenceOffsets[referencePos] = offset;
                    referencePos++;
                }
            } else if (forStaticReferences.accepts(f)) {
                assert !forStaticPrimitives.accepts(f);
                offset = NumUtil.safeToInt(forStaticReferences.findOffset(f));
            } else if (forStaticPrimitives.accepts(f)) {
                offset = NumUtil.safeToInt(forStaticPrimitives.findOffset(f));
            }
            assert offset >= 0;
            offsets[i] = offset;
        }
        assert referencePos == referenceOffsets.length;
        return new FieldLayout(NumUtil.safeToInt(forInstance.getAfterFieldsOffset()), offsets, referenceOffsets,
                        forStaticReferences.getTotalCount(), forStaticPrimitives.getTotalSize());
    }

    private abstract static class FieldLayoutImpl {
        private static final int[] SIZES_IN_BYTES = new int[]{
                        /* LONG, DOUBLE */ 8,
                        /* OBJECT */ ConfigurationValues.getObjectLayout().getReferenceSize(),
                        /* INT, FLOAT */ 4,
                        /* CHAR, SHORT */ 2,
                        /* BYTE, BOOLEAN */ 1
        };

        private static final int COUNT_LEN = SIZES_IN_BYTES.length;

        private static final int NO_FIELDS = -1;

        private static final int LD_INDEX = 0;
        private static final int OBJECT_INDEX = 1;
        private static final int IF_INDEX = 2;
        private static final int CS_INDEX = 3;
        private static final int BB_INDEX = 4;

        private final long startOffset;
        private final long afterFieldsOffset;

        private final int[] counts = new int[COUNT_LEN];
        private final long[] offsets = new long[COUNT_LEN];

        public abstract boolean accepts(ParserField f);

        public final long findOffset(ParserField f) {
            assert accepts(f);
            int idx = indexOf(f.getKind());
            long result = offsets[idx];
            offsets[idx] = result + SIZES_IN_BYTES[idx];
            return result;
        }

        public final long getAfterFieldsOffset() {
            return afterFieldsOffset;
        }

        public final int getCountFor(JavaKind kind) {
            return counts[indexOf(kind)];
        }

        public final int getTotalCount() {
            long total = 0;
            for (int i = 0; i < COUNT_LEN; i++) {
                total = total + counts[i];
            }
            return NumUtil.safeToInt(total);
        }

        public final int getTotalSize() {
            long total = 0;
            for (int i = 0; i < COUNT_LEN; i++) {
                total += counts[i] * (long) SIZES_IN_BYTES[i];
            }
            return NumUtil.safeToInt(total);
        }

        FieldLayoutImpl(ParserField[] declaredParsedFields, long startOffset) {
            this.startOffset = startOffset;
            this.afterFieldsOffset = init(declaredParsedFields);
        }

        private long init(ParserField[] declaredParsedFields) {
            for (ParserField f : declaredParsedFields) {
                if (accepts(f)) {
                    int idx = indexOf(f.getKind());
                    counts[idx]++;
                }
            }

            // Find the largest existing field, and align the starting offset to it.
            int firstCount = findLargestFieldIndex();
            if (firstCount == NO_FIELDS) {
                return startOffset;
            }
            offsets[firstCount] = align(startOffset, SIZES_IN_BYTES[firstCount]);

            // Compute first offset for remaining sizes
            for (int i = firstCount + 1; i < COUNT_LEN; i++) {
                long kindStartOffset = offsetAfterKind(i - 1);
                assert kindStartOffset == align(kindStartOffset, SIZES_IN_BYTES[i]) : "By construction, the ith kind is aligned to the (i-1)th.";
                offsets[i] = kindStartOffset;
            }
            return offsetAfterKind(COUNT_LEN - 1);
        }

        private long offsetAfterKind(int kind) {
            return offsets[kind] + (counts[kind] * (long) SIZES_IN_BYTES[kind]);
        }

        private int findLargestFieldIndex() {
            for (int i = 0; i < COUNT_LEN; i++) {
                if (counts[i] > 0) {
                    return i;
                }
            }
            return NO_FIELDS;
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

        private static final class ForInstanceFields extends FieldLayoutImpl {
            ForInstanceFields(ParserField[] declaredParsedFields, long startOffset) {
                super(declaredParsedFields, startOffset);
            }

            @Override
            public boolean accepts(ParserField f) {
                return !f.isStatic();
            }
        }

        private static final class ForStaticReferenceFields extends FieldLayoutImpl {
            ForStaticReferenceFields(ParserField[] declaredParsedFields, long startOffset) {
                super(declaredParsedFields, startOffset);
            }

            @Override
            public boolean accepts(ParserField f) {
                return f.isStatic() && f.getKind().isObject();
            }
        }

        private static final class ForStaticPrimitiveFields extends FieldLayoutImpl {
            ForStaticPrimitiveFields(ParserField[] declaredParsedFields, long startOffset) {
                super(declaredParsedFields, startOffset);
            }

            @Override
            public boolean accepts(ParserField f) {
                return f.isStatic() && f.getKind().isPrimitive();
            }
        }
    }

}
