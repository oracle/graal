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
package com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint;

import java.nio.ByteBuffer;

import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.symbols.constants.AbstractConstant;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class FloatingPointConstant extends AbstractConstant {

    private static final int X86_FP80_BYTES = Math.toIntExact(PrimitiveType.X86_FP80.getBitSize() / Byte.SIZE);

    FloatingPointConstant(Type type) {
        super(type);
    }

    public abstract String getStringValue();

    public static FloatingPointConstant create(Type type, RecordBuffer buffer) {
        switch (((PrimitiveType) type).getPrimitiveKind()) {
            case FLOAT:
                return new FloatConstant(Float.intBitsToFloat(buffer.readInt()));

            case DOUBLE:
                return new DoubleConstant(Double.longBitsToDouble(buffer.read()));

            case X86_FP80:
                return new X86FP80Constant(ByteBuffer.allocate(X86_FP80_BYTES).putLong(buffer.read()).putShort((short) buffer.read()).array());

            default:
                throw new LLVMParserException("Unsupported Floating Point Type: " + type);
        }
    }

    @Override
    public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
    }
}
