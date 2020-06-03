/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model.enums;

public enum Flag {

    INT_EXACT("exact", 1),

    INT_NO_UNSIGNED_WRAP("nuw", 1),
    INT_NO_SIGNED_WRAP("nsw", 2),

    FP_NO_NANS("nnan", 2),
    FP_NO_INFINITIES("ninf", 4),
    FP_NO_SIGNED_ZEROES("nsz", 8),
    FP_ALLOW_RECIPROCAL("arcp", 16),
    FP_FAST("fast", 31);

    private final String irString;

    private final int mask;

    public static final Flag[] EMPTY_ARRAY = {};

    Flag(String irString, int mask) {
        this.irString = irString;
        this.mask = mask;
    }

    public boolean test(long flags) {
        return (flags & mask) == mask;
    }

    @Override
    public String toString() {
        return irString;
    }

    public String getIrString() {
        return irString;
    }

    /*
     * This method exists because there is ambiguity in the binary operation instruction bitcode
     * encoding. The flags can be one of three sets depending on the type and operation used. This
     * helper method converts the flagbits into an array of Flag enums.
     */
    public static Flag[] decode(BinaryOperator opcode, int flagbits) {
        switch (opcode) {
            case INT_ADD:
            case INT_SUBTRACT:
            case INT_MULTIPLY:
            case INT_SHIFT_LEFT:
                return create(flagbits, INT_NO_UNSIGNED_WRAP, INT_NO_SIGNED_WRAP);

            case FP_ADD:
            case FP_SUBTRACT:
            case FP_MULTIPLY:
            case FP_DIVIDE:
            case FP_REMAINDER:
                if (FP_FAST.test(flagbits)) {
                    return new Flag[]{FP_FAST};
                }
                return create(flagbits, FP_NO_NANS, FP_NO_INFINITIES, FP_NO_SIGNED_ZEROES, FP_ALLOW_RECIPROCAL);

            default:
                return create(flagbits, INT_EXACT);
        }
    }

    public static Flag[] decode(@SuppressWarnings("unused") UnaryOperator opcode, int flagbits) {
        return create(flagbits);
    }

    private static Flag[] create(long flagbits, Flag... options) {
        final int count = Long.bitCount(flagbits);
        if (count != 0) {
            int i = 0;
            final Flag[] flags = new Flag[count];
            for (Flag option : options) {
                if (option.test(flagbits)) {
                    flags[i++] = option;
                }
            }
            return flags;

        } else {
            return EMPTY_ARRAY;
        }
    }
}
