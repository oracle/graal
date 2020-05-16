/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;

public abstract class LLVMX86_ComparisonNode {
    @NodeChild(value = "xmm1", type = LLVMExpressionNode.class)
    @NodeChild(value = "xmm2", type = LLVMExpressionNode.class)
    @NodeChild(value = "imm", type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_Cmpss extends LLVMBuiltin {
        private static final float TRUEMASK = Float.intBitsToFloat(-1);

        @Specialization
        protected LLVMFloatVector doIntrinsic(LLVMFloatVector xmm1, LLVMFloatVector xmm2, byte imm) {
            // https://www.felixcloutier.com/x86/cmpss
            if (xmm1.getLength() != 4) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("xmm1 expected a <4 x float> vector");
            }
            if (xmm2.getLength() != 4) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("xmm2 expected a <4 x float> vector");
            }
            float[] rv = new float[]{0, xmm1.getValue(1), xmm1.getValue(2), xmm1.getValue(3)};
            float left = xmm1.getValue(0);
            float right = xmm2.getValue(0);
            switch (imm) {
                case 0: // CMPEQSS
                    rv[0] = (left == right) ? TRUEMASK : 0;
                    break;
                case 1: // CMPLTSS
                    rv[0] = (left < right) ? TRUEMASK : 0;
                    break;
                case 2: // CMPLESS
                    rv[0] = (left <= right) ? TRUEMASK : 0;
                    break;
                case 3: // CMPUNORDSS
                    rv[0] = (Float.isNaN(left) || Float.isNaN(right)) ? TRUEMASK : 0;
                    break;
                case 4: // CMPNEQSS
                    rv[0] = (left == right) ? 0 : TRUEMASK;
                    break;
                case 5: // CMPNLTSS
                    rv[0] = (left < right) ? 0 : TRUEMASK;
                    break;
                case 6: // CMPNLESS
                    rv[0] = (left <= right) ? 0 : TRUEMASK;
                    break;
                case 7: // CMPORDSS
                    rv[0] = (Float.isNaN(left) || Float.isNaN(right)) ? 0 : TRUEMASK;
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError("unsupported predicate (not in range 0-7)");
            }
            return LLVMFloatVector.create(rv);
        }
    }
}
