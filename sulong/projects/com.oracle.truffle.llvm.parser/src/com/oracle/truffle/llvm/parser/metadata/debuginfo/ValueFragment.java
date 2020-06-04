/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import static com.oracle.truffle.llvm.parser.metadata.DwarfOpcode.LLVM_FRAGMENT;
import static com.oracle.truffle.llvm.parser.metadata.DwarfOpcode.hasOp;
import static com.oracle.truffle.llvm.parser.metadata.DwarfOpcode.numElements;

import java.util.List;

import com.oracle.truffle.llvm.parser.metadata.MDExpression;

public final class ValueFragment implements Comparable<ValueFragment> {

    private static final ValueFragment COMPLETE_VALUE = new ValueFragment(-1, -1);

    private static final int EXPRESSION_SIZE = 3;
    private static final int EXPRESSION_INDEX_OFFSET = 1;
    private static final int EXPRESSION_INDEX_LENGTH = 2;

    public static boolean describesFragment(MDExpression expression) {
        // a DEREF can precede the LLVM_FRAGMENT
        return hasOp(expression, LLVM_FRAGMENT);
    }

    public static ValueFragment create(int offset, int length) {
        return new ValueFragment(offset, length);
    }

    public static ValueFragment parse(MDExpression expression) {
        final int elementCount = expression.getElementCount();
        int i = 0;
        while (i < elementCount) {
            final long op = expression.getOperand(i);
            if (op == LLVM_FRAGMENT) {
                if (i + EXPRESSION_SIZE <= elementCount) {
                    final int offset = (int) expression.getOperand(i + EXPRESSION_INDEX_OFFSET);
                    final int length = (int) expression.getOperand(i + EXPRESSION_INDEX_LENGTH);
                    return new ValueFragment(offset, length);
                }
            }
            i += numElements(op);
        }
        return COMPLETE_VALUE;
    }

    public static int getPartIndex(ValueFragment fragment, List<ValueFragment> siblings, List<Integer> clearParts) {
        int partIndex = -1;
        for (int i = 0; i < siblings.size(); i++) {
            final ValueFragment sibling = siblings.get(i);

            if (sibling.equals(fragment)) {
                partIndex = i;

            } else if (!ValueFragment.COMPLETE_VALUE.equals(sibling) && fragment.hides(sibling)) {
                clearParts.add(i);
            }
        }
        return partIndex;
    }

    private final int offset;
    private final int length;

    private ValueFragment(int offset, int length) {
        this.offset = offset;
        this.length = length;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public int getStart() {
        return offset;
    }

    public int getEnd() {
        return offset + length;
    }

    public boolean isComplete() {
        return COMPLETE_VALUE.equals(this);
    }

    private boolean hides(ValueFragment other) {
        return getStart() <= other.getStart() && other.getEnd() <= getEnd();
    }

    @Override
    public int compareTo(ValueFragment o) {
        if (offset == o.offset) {
            return Integer.compare(length, o.length);
        } else {
            return Integer.compare(offset, o.offset);
        }
    }

    @Override
    public int hashCode() {
        return (offset << Short.SIZE) | length;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final ValueFragment other = (ValueFragment) obj;
        return offset == other.offset && length == other.length;
    }
}
