/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.scanner;

enum Primitive {
    CHAR6(true, 6),

    ABBREVIATED_RECORD_OPERANDS(false, 5),
    SUBBLOCK_ID(false, 8),
    SUBBLOCK_ID_SIZE(false, 4),
    UNABBREVIATED_RECORD_ID(false, 6),
    UNABBREVIATED_RECORD_OPERAND(false, 6),
    UNABBREVIATED_RECORD_OPS(false, 6),

    USER_OPERAND_ARRAY_LENGTH(false, 6),
    USER_OPERAND_BLOB_LENGTH(false, 6),
    USER_OPERAND_DATA(false, 5),
    USER_OPERAND_LITERAL(false, 8),
    USER_OPERAND_TYPE(true, 3),
    USER_OPERAND_LITERALBIT(true, 1);

    private final boolean isFixed;

    private final int bits;

    Primitive(boolean isFixed, int bits) {
        this.isFixed = isFixed;
        this.bits = bits;
    }

    public int getBits() {
        return bits;
    }

    public boolean isFixed() {
        return isFixed;
    }
}
