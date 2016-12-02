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
package com.oracle.truffle.llvm.parser.api.instructions;

/**
 * See see http://llvm.org/docs/LangRef.html#fcmp-instruction.
 */
public enum LLVMFloatComparisonType {

    FALSE("false"),
    ORDERED_AND_EQUALS("oeq"),
    ORDERED_AND_GREATER_THAN("ogt"),
    ORDERED_AND_GREATER_EQUALS("oge"),
    ORDERED_AND_LESS_THAN("olt"),
    ORDERED_AND_LESS_EQUALS("ole"),
    ORDERED_AND_NOT_EQUALS("one"),
    ORDERED("ord"),
    UNORDERED_OR_EQUALS("ueq"),
    UNORDERED_OR_GREATER_THAN("ugt"),
    UNORDERED_OR_GREATER_EQUALS("uge"),
    UNORDERED_OR_LESS_THAN("ult"),
    UNORDERED_OR_LESS_EQUALS("ule"),
    UNORDERED_OR_NOT_EQUALS("une"),
    UNORDERED("uno"),
    TRUE("true");

    private final String representation;

    LLVMFloatComparisonType(String representation) {
        this.representation = representation;
    }

    public static boolean isFloatCondition(String s) {
        return fromStringNoException(s) != null;
    }

    public static LLVMFloatComparisonType fromString(String s) {
        LLVMFloatComparisonType type = fromStringNoException(s);
        if (type == null) {
            throw new IllegalArgumentException(s);
        } else {
            return type;
        }
    }

    private static LLVMFloatComparisonType fromStringNoException(String s) {
        for (LLVMFloatComparisonType cond : LLVMFloatComparisonType.values()) {
            if (cond.representation.equals(s)) {
                return cond;
            }
        }
        return null;
    }
}
