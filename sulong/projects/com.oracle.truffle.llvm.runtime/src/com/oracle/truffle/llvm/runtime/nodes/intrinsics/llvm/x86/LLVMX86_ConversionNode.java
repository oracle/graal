/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMX86_ConversionNode {

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_ConversionFloatToIntNode extends LLVMBuiltin { // implements
                                                                                        // cvtss2si

        @Specialization
        protected int doIntrinsic(LLVMFloatVector vector) {
            if (vector.getLength() != 4) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("cvtss2si requires a float[4] as parameter");
            }

            return Math.round(vector.getValue(0));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_ConversionDoubleToIntNode extends LLVMBuiltin { // implements
                                                                                         // cvtsd2si

        @Specialization
        protected int doIntrinsic(LLVMDoubleVector vector) {
            if (vector.getLength() != 2) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("cvtsd2si requires a double[2] as parameter");
            }

            // returns an int instead of a long,
            // causes an exception in one OpenCV test application when returning a long
            return Math.toIntExact(Math.round(vector.getValue(0)));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_Pmovmskb128 extends LLVMBuiltin {

        private static final int VECTOR_LENGTH = 16;

        @Specialization
        @ExplodeLoop
        protected int doIntrinsic(LLVMI8Vector vector) {
            if (vector.getLength() != VECTOR_LENGTH) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("expected a <16 x i8> vector");
            }
            int result = 0;
            for (int i = 0; i < VECTOR_LENGTH; i++) {
                int currentByte = vector.getValue(i);
                int mostSignificantBit = (currentByte & 0xff) >> (Byte.SIZE - 1);
                result |= mostSignificantBit << i;
            }

            return result;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_Movmskpd extends LLVMBuiltin {

        @Specialization
        protected int doIntrinsic(LLVMDoubleVector vector) {
            if (vector.getLength() != 2) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("expected a <2 x double> vector");
            }
            return ((vector.getValue(1) < 0 ? 1 : 0) << 1) | (vector.getValue(0) < 0 ? 1 : 0);
        }
    }
}
