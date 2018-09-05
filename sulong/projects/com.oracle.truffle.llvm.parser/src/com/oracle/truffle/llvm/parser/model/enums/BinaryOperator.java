/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

public enum BinaryOperator {

    INT_ADD(13, "add"),
    INT_SUBTRACT(14, "sub"),
    INT_MULTIPLY(15, "mul"),
    INT_UNSIGNED_DIVIDE(-1, "udiv"),
    INT_SIGNED_DIVIDE(16, "sdiv"),
    INT_UNSIGNED_REMAINDER(-1, "urem"),
    INT_SIGNED_REMAINDER(17, "srem"),
    INT_SHIFT_LEFT(-1, "shl"),
    INT_LOGICAL_SHIFT_RIGHT(-1, "lshr"),
    INT_ARITHMETIC_SHIFT_RIGHT(-1, "ashr"),
    INT_AND(-1, "and"),
    INT_OR(-1, "or"),
    INT_XOR(-1, "xor"),

    FP_ADD(-1, "fadd"),
    FP_SUBTRACT(-1, "fsub"),
    FP_MULTIPLY(-1, "fmul"),
    FP_DIVIDE(-1, "fdiv"),
    FP_REMAINDER(-1, "frem");

    private static final BinaryOperator[] VALUES = values();

    public static BinaryOperator decode(int opcode, boolean isFloatingPoint) {
        if (opcode >= 0 && opcode <= INT_XOR.ordinal()) {
            BinaryOperator op = VALUES[opcode];
            return isFloatingPoint ? op.fp() : op;
        }
        return null;
    }

    private final int fpmap;
    private final String irString;

    BinaryOperator(int fpmap, String irString) {
        this.fpmap = fpmap;
        this.irString = irString;
    }

    private BinaryOperator fp() {
        return fpmap < 0 ? null : VALUES[fpmap];
    }

    public boolean isFloatingPoint() {
        return this.ordinal() > INT_XOR.ordinal();
    }

    /**
     * Useful to get the llvm ir equivalent string of the enum.
     */
    public String getIrString() {
        return irString;
    }
}
