/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;

public abstract class LLVMX86_VectorMathNode {

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMX86_VectorSquareRootNode extends LLVMBuiltin { // mm_sqrt_pd
        @Specialization(guards = "vector.getLength() == 2")
        protected LLVMDoubleVector doM128(LLVMDoubleVector vector) {
            return LLVMDoubleVector.create(new double[]{Math.sqrt(vector.getValue(0)), Math.sqrt(vector.getValue(1))});
        }

        @Specialization(guards = "vector.getLength() == 4")
        protected LLVMDoubleVector doM256(LLVMDoubleVector vector) {
            return LLVMDoubleVector.create(new double[]{
                            Math.sqrt(vector.getValue(0)), Math.sqrt(vector.getValue(1)),
                            Math.sqrt(vector.getValue(2)), Math.sqrt(vector.getValue(3))
            });
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMX86_VectorMaxNode extends LLVMBuiltin { // mm256_max_pd
        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMDoubleVector doM256(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            Math.max(v1.getValue(0), v2.getValue(0)),
                            Math.max(v1.getValue(1), v2.getValue(1)),
                            Math.max(v1.getValue(2), v2.getValue(2)),
                            Math.max(v1.getValue(3), v2.getValue(3))
            });
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMX86_VectorMinNode extends LLVMBuiltin { // mm256_min_pd
        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMDoubleVector doM256(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            Math.min(v1.getValue(0), v2.getValue(0)),
                            Math.min(v1.getValue(1), v2.getValue(1)),
                            Math.min(v1.getValue(2), v2.getValue(2)),
                            Math.min(v1.getValue(3), v2.getValue(3))
            });
        }
    }
}
