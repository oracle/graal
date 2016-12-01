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

// see http://llvm.org/docs/LangRef.html#bitwise-binary-operations
public enum LLVMLogicalInstructionType {
    SHIFT_LEFT("shl"),
    LOGICAL_SHIFT_RIGHT("lshr"),
    ARITHMETIC_SHIFT_RIGHT("ashr"),
    AND("and"),
    OR("or"),
    XOR("xor");

    private final String representation;

    LLVMLogicalInstructionType(String representation) {
        this.representation = representation;
    }

    public static boolean isLogicalInstruction(String opCode) {
        return fromStringInternal(opCode) != null;
    }

    public static LLVMLogicalInstructionType fromString(String opCode) {
        LLVMLogicalInstructionType type = fromStringInternal(opCode);
        if (type == null) {
            throw new IllegalArgumentException(opCode);
        } else {
            return type;
        }
    }

    private static LLVMLogicalInstructionType fromStringInternal(String opCode) {
        for (LLVMLogicalInstructionType type : LLVMLogicalInstructionType.values()) {
            if (type.representation.equals(opCode)) {
                return type;
            }
        }
        return null;
    }

}
