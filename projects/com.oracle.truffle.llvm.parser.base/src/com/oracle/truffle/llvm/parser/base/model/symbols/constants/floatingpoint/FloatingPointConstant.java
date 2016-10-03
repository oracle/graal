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
package com.oracle.truffle.llvm.parser.base.model.symbols.constants.floatingpoint;

import com.oracle.truffle.llvm.parser.base.model.symbols.constants.AbstractConstant;
import com.oracle.truffle.llvm.parser.base.model.types.FloatingPointType;

import java.nio.ByteBuffer;

public abstract class FloatingPointConstant extends AbstractConstant {

    FloatingPointConstant(FloatingPointType type) {
        super(type);
    }

    public static FloatingPointConstant create(FloatingPointType type, long[] bits) {
        switch (type) {
            case FLOAT:
                return FloatConstant.create(Float.intBitsToFloat((int) bits[0]));

            case DOUBLE:
                return DoubleConstant.create(Double.longBitsToDouble(bits[0]));

            case X86_FP80:
                return X86FP80Constant.create(ByteBuffer.allocate(type.sizeof()).putLong(bits[0]).putShort((short) bits[1]).array());

            default:
                throw new UnsupportedOperationException("Unsupported Floating Point Type: " + type);
        }
    }
}
