/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata;

import java.util.Arrays;

public final class MDExpression implements MDBaseNode {

    public static final MDExpression EMPTY = new MDExpression(new long[0]);

    private final long[] operands;

    private MDExpression(long[] operands) {
        this.operands = operands;
    }

    public long getOperand(int i) {
        return operands[i];
    }

    public int getElementCount() {
        return operands.length;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int INDEX_OPERANDSTART = 1;
    private static final int INDEX_VERSION = 0;
    private static final int VERSION_VALUE_SHIFT = 1;

    public static MDExpression create(long[] record) {
        final int version = (int) (record[INDEX_VERSION] >> VERSION_VALUE_SHIFT);

        long[] ops = Arrays.copyOfRange(record, INDEX_OPERANDSTART, record.length);
        ops = upgrade(version, ops);

        return new MDExpression(ops);
    }

    private static final int CURRENT_VERSION = 3;
    private static final int V0_FRAGMENT_OFFSET = 3;
    private static final long DEFAULT_OPERAND = 0;

    private static final class LongList {
        private long[] array;
        private int size;

        LongList(int initialCapacity) {
            this.array = new long[initialCapacity];
        }

        void add(long value) {
            if (size >= array.length) {
                array = Arrays.copyOf(array, Math.max(8, array.length * 2));
            }
            array[size++] = value;
        }

        long[] toArray() {
            return array.length == size ? array : Arrays.copyOf(array, size);
        }
    }

    private static long[] upgrade(int version, long[] originalOps) {
        final int numOps = originalOps.length;
        if (numOps == 0 || version >= CURRENT_VERSION) {
            return originalOps;
        }

        long[] ops = originalOps;

        if (version >= 0 && numOps >= V0_FRAGMENT_OFFSET && ops[numOps - V0_FRAGMENT_OFFSET] == DwarfOpcode.BIT_PIECE) {
            ops[numOps - V0_FRAGMENT_OFFSET] = DwarfOpcode.LLVM_FRAGMENT;
        }

        if (version >= 1 && ops[0] == DwarfOpcode.DEREF) {
            System.arraycopy(ops, 1, ops, 0, numOps - 1);
            ops[numOps - 1] = DwarfOpcode.DEREF;
            // TODO NeedDeclareExpressionUpgrade = true;
        }

        if (version >= 2) {
            final LongList buffer = new LongList(numOps);

            int i = 0;
            while (i < numOps) {

                final long op = ops[i++];
                switch ((int) op) {

                    case (int) DwarfOpcode.PLUS:
                        buffer.add(DwarfOpcode.PLUS_UCONST);
                        buffer.add(i < numOps ? ops[i++] : DEFAULT_OPERAND);
                        break;

                    case (int) DwarfOpcode.MINUS:
                        buffer.add(DwarfOpcode.CONSTU);
                        buffer.add(i < numOps ? ops[i++] : DEFAULT_OPERAND);
                        buffer.add(DwarfOpcode.MINUS);
                        break;

                    case (int) DwarfOpcode.CONSTU:
                        buffer.add(op);
                        buffer.add(i < numOps ? ops[i++] : DEFAULT_OPERAND);
                        break;

                    case (int) DwarfOpcode.LLVM_FRAGMENT:
                        buffer.add(op);
                        buffer.add(i < numOps ? ops[i++] : DEFAULT_OPERAND);
                        buffer.add(i < numOps ? ops[i++] : DEFAULT_OPERAND);
                        break;

                    default:
                        buffer.add(op);
                        break;
                }
            }
            return buffer.toArray();
        }

        return ops;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final MDExpression that = (MDExpression) o;
        return Arrays.equals(operands, that.operands);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(operands);
    }
}
