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
package com.oracle.truffle.llvm.nodes.vector;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMInsertElementNode {

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class, value = "element"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "index")})
    public abstract static class LLVMI1InsertElementNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI1Vector executeI1(LLVMAddress address, LLVMI1Vector vector, boolean element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class, value = "element"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "index")})
    public abstract static class LLVMI8InsertElementNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI8Vector executeI8(LLVMAddress address, LLVMI8Vector vector, byte element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class, value = "element"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "index")})
    public abstract static class LLVMI16InsertElementNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI16Vector executeI16(LLVMAddress address, LLVMI16Vector vector, short element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class, value = "element"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "index")})
    public abstract static class LLVMI32InsertElementNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI32Vector executeI32(LLVMAddress address, LLVMI32Vector vector, int element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class, value = "element"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "index")})
    public abstract static class LLVMI64InsertElementNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI64Vector executeI64(LLVMAddress address, LLVMI64Vector vector, long element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class, value = "element"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "index")})
    public abstract static class LLVMFloatInsertElementNode extends LLVMExpressionNode {

        @Specialization
        public LLVMFloatVector executeFloat(LLVMAddress address, LLVMFloatVector vector, float element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class, value = "element"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "index")})
    public abstract static class LLVMDoubleInsertElementNode extends LLVMExpressionNode {

        @Specialization
        public LLVMDoubleVector executeDouble(LLVMAddress address, LLVMDoubleVector vector, double element, int index) {
            return vector.insert(address, element, index);
        }
    }

}
