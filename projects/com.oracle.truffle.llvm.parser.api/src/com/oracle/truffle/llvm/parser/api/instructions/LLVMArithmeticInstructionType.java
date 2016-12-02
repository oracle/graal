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

// see http://llvm.org/docs/LangRef.html#binary-operations
public enum LLVMArithmeticInstructionType {
    /**
     * Implements add and fadd.
     */
    ADDITION("add", "fadd"),
    /**
     * Implements sub and fsub.
     */
    SUBTRACTION("sub", "fsub"),
    /**
     * Implements mul and fmul.
     */
    MULTIPLICATION("mul", "fmul"),
    /**
     * Implements udiv.
     */
    UNSIGNED_DIVISION("udiv"),
    /**
     * Implements div and fdiv.
     */
    DIVISION("sdiv", "fdiv"),
    /**
     * Implements urem.
     */
    UNSIGNED_REMAINDER("urem"),
    /**
     * Implements srem and frem.
     */
    REMAINDER("srem", "frem");

    private final String[] representations;

    LLVMArithmeticInstructionType(String... representations) {
        this.representations = representations;
    }

    public static boolean isArithmeticInstruction(String s) {
        return fromStringNoException(s) != null;
    }

    public static LLVMArithmeticInstructionType fromString(String s) {
        LLVMArithmeticInstructionType type = fromStringNoException(s);
        if (type == null) {
            throw new IllegalArgumentException(s);
        } else {
            return type;
        }
    }

    private static LLVMArithmeticInstructionType fromStringNoException(String s) {
        for (LLVMArithmeticInstructionType type : LLVMArithmeticInstructionType.values()) {
            for (String representation : type.representations) {
                if (representation.equals(s)) {
                    return type;
                }
            }
        }
        return null;
    }
}
