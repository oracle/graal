/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package uk.ac.man.cs.llvm.ir.model.enums;

public enum BinaryOperator {

    INT_ADD(13),
    INT_SUBTRACT(14),
    INT_MULTIPLY(15),
    INT_UNSIGNED_DIVIDE(-1),
    INT_SIGNED_DIVIDE(16),
    INT_UNSIGNED_REMAINDER(-1),
    INT_SIGNED_REMAINDER(17),
    INT_SHIFT_LEFT(-1),
    INT_LOGICAL_SHIFT_RIGHT(-1),
    INT_ARITHMETIC_SHIFT_RIGHT(-1),
    INT_AND(-1),
    INT_OR(-1),
    INT_XOR(-1),

    FP_ADD(-1),
    FP_SUBTRACT(-1),
    FP_MULTIPLY(-1),
    FP_DIVIDE(-1),
    FP_REMAINDER(-1);

    public static BinaryOperator decode(int opcode, boolean isFloatingPoint) {
        BinaryOperator[] ops = values();
        if (opcode >= 0 && opcode <= INT_XOR.ordinal()) {
            BinaryOperator op = ops[opcode];
            return isFloatingPoint ? op.fp() : op;
        }
        return null;
    }

    private final int fpmap;

    BinaryOperator(int fpmap) {
        this.fpmap = fpmap;
    }

    private BinaryOperator fp() {
        return fpmap < 0 ? null : values()[fpmap];
    }
}
