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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;

public abstract class LLVMByteSwap {

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMByteSwapI16 extends LLVMBuiltin {

        @Specialization
        public short executeI16(short value) {
            return Short.reverseBytes(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMByteSwapI32 extends LLVMBuiltin {

        @Specialization
        public int executeI32(int value) {
            return Integer.reverseBytes(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMByteSwapI64 extends LLVMBuiltin {

        @Specialization
        public long executeI64(long value) {
            return Long.reverseBytes(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMByteSwapV8I16 extends LLVMBuiltin {
        @Specialization
        public LLVMI16Vector executeI16Vector(LLVMI16Vector vector) {
            short[] result = new short[8];
            for (int i = 0; i < 8; i++) {
                result[i] = Short.reverseBytes(vector.getValue(i));
            }
            return LLVMI16Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMByteSwapV4I32 extends LLVMBuiltin {
        @Specialization
        public LLVMI32Vector executeI32Vector(LLVMI32Vector vector) {
            int[] result = new int[4];
            for (int i = 0; i < 4; i++) {
                result[i] = Integer.reverseBytes(vector.getValue(i));
            }
            return LLVMI32Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMByteSwapV2I64 extends LLVMBuiltin {
        @Specialization
        public LLVMI64Vector executeI32Vector(LLVMI64Vector vector) {
            long[] result = new long[2];
            for (int i = 0; i < 2; i++) {
                result[i] = Long.reverseBytes(vector.getValue(i));
            }
            return LLVMI64Vector.create(result);
        }
    }
}
