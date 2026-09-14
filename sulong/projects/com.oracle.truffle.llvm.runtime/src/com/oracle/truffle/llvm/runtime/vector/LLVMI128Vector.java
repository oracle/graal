/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.vector;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;

/**
 * A vector of 128-bit integers ({@code <N x i128>}). clang emits this type as an intermediate for
 * Eigen's AVX {@code predux} reductions, which reinterpret a 256-bit float/double register as
 * {@code <2 x i128>} and {@code extractelement} the high 128-bit lane. Each lane is held as an
 * {@link LLVMIVarBit} of width 128 (big-endian storage, matching {@code LLVMIVarBit.getBytes()}).
 */
@ValueType
public final class LLVMI128Vector extends LLVMVector {

    public static final int ELEMENT_BITS = 128;

    private final LLVMIVarBit[] vector;

    public static LLVMI128Vector create(LLVMIVarBit[] vector) {
        return new LLVMI128Vector(vector);
    }

    private LLVMI128Vector(LLVMIVarBit[] vector) {
        this.vector = vector;
    }

    public LLVMIVarBit getValue(int index) {
        return vector[index];
    }

    @Override
    public int getLength() {
        return vector.length;
    }

    @Override
    public Type getElementType() {
        return new VariableBitWidthType(ELEMENT_BITS);
    }

    @Override
    public Object getElement(int index) {
        return index >= 0 && index < vector.length ? vector[index] : null;
    }
}
