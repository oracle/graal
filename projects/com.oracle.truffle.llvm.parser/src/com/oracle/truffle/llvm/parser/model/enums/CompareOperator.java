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
package com.oracle.truffle.llvm.parser.model.enums;

public enum CompareOperator {

    FP_FALSE(true, "false"),
    FP_ORDERED_EQUAL(true, "oeq"),
    FP_ORDERED_GREATER_THAN(true, "ogt"),
    FP_ORDERED_GREATER_OR_EQUAL(true, "oge"),
    FP_ORDERED_LESS_THAN(true, "olt"),
    FP_ORDERED_LESS_OR_EQUAL(true, "ole"),
    FP_ORDERED_NOT_EQUAL(true, "one"),
    FP_ORDERED(true, "ord"),
    FP_UNORDERED(true, "uno"),
    FP_UNORDERED_EQUAL(true, "ueq"),
    FP_UNORDERED_GREATER_THAN(true, "ugt"),
    FP_UNORDERED_GREATER_OR_EQUAL(true, "uge"),
    FP_UNORDERED_LESS_THAN(true, "ult"),
    FP_UNORDERED_LESS_OR_EQUAL(true, "ule"),
    FP_UNORDERED_NOT_EQUAL(true, "une"),
    FP_TRUE(true, "true"),

    INT_EQUAL(false, "eq"),
    INT_NOT_EQUAL(false, "ne"),
    INT_UNSIGNED_GREATER_THAN(false, "ugt"),
    INT_UNSIGNED_GREATER_OR_EQUAL(false, "uge"),
    INT_UNSIGNED_LESS_THAN(false, "ult"),
    INT_UNSIGNED_LESS_OR_EQUAL(false, "ule"),
    INT_SIGNED_GREATER_THAN(false, "sgt"),
    INT_SIGNED_GREATER_OR_EQUAL(false, "sge"),
    INT_SIGNED_LESS_THAN(false, "slt"),
    INT_SIGNED_LESS_OR_EQUAL(false, "sle");

    private static final long INTEGER_OPERATOR_FLAG = 32L;

    public static CompareOperator decode(long opcode) {
        CompareOperator[] ops = values();

        long fpops = FP_TRUE.ordinal() + 1;

        if (opcode >= 0 && opcode < fpops) {
            return ops[(int) opcode];
        } else {
            long iopcode = (opcode + fpops) - INTEGER_OPERATOR_FLAG;
            if (iopcode >= fpops && iopcode < values().length) {
                return ops[(int) iopcode];
            }
        }
        return null;
    }

    private final boolean isFloatingPoint;
    private final String irString;

    CompareOperator(boolean isFloatingPoint, String irString) {
        this.isFloatingPoint = isFloatingPoint;
        this.irString = irString;
    }

    public boolean isFloatingPoint() {
        return isFloatingPoint;
    }

    /**
     * Useful to get the llvm ir equivalent string of the enum
     */
    public String getIrString() {
        return irString;
    }
}
