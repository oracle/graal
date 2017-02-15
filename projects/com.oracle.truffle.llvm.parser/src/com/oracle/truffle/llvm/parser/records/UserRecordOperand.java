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
package com.oracle.truffle.llvm.parser.records;

import com.oracle.truffle.llvm.parser.BlockParser;
import com.oracle.truffle.llvm.parser.ParserResult;
import com.oracle.truffle.llvm.parser.Primitive;
import com.oracle.truffle.llvm.parser.util.Pair;

public abstract class UserRecordOperand {

    public static Pair<ParserResult, UserRecordOperand> parse(BlockParser parser) {
        ParserResult result = parser.read(1);
        if (result.getValue() == 1) {
            result = result.getParser().read(Primitive.USER_OPERAND_LITERAL);
            return new Pair<>(result, new UserRecordLiteral(result.getValue()));
        } else {
            result = result.getParser().read(Primitive.USER_OPERAND_TYPE);
            UserRecordOperandType type = UserRecordOperandType.decode(result.getValue());
            switch (type) {
                case FIXED:
                    result = result.getParser().read(Primitive.USER_OPERAND_DATA);
                    return new Pair<>(result, new UserRecordFixedOperand(result.getValue()));

                case VBR:
                    result = result.getParser().read(Primitive.USER_OPERAND_DATA);
                    return new Pair<>(result, new UserRecordVariableOperand(result.getValue()));

                case ARRAY:
                    return new Pair<>(result, new UserRecordArrayOperand(null));

                case CHAR6:
                    return new Pair<>(result, new UserRecordCharOperand());

                case BLOB:
                    return new Pair<>(result, new UserRecordBinaryOperand());

                default:
                    throw new IllegalStateException("Illegal encoding");
            }

        }
    }

    protected UserRecordOperand() {
    }

    public abstract ParserResult get(BlockParser parser);
}
