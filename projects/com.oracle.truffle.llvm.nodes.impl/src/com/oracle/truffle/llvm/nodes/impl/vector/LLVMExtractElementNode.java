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
package com.oracle.truffle.llvm.nodes.impl.vector;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMDoubleVectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI16VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI64VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.types.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.types.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.types.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI8Vector;

public abstract class LLVMExtractElementNode {

    @NodeChildren({@NodeChild(type = LLVMI8VectorNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMI8ExtractElementNode extends LLVMI8Node {

        @Specialization
        public byte executeI8(LLVMI8Vector vector, int index) {
            return vector.getValue(index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMI16VectorNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMI16ExtractElementNode extends LLVMI16Node {

        @Specialization
        public short executeI16(LLVMI16Vector vector, int index) {
            return vector.getValue(index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMI32VectorNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMI32ExtractElementNode extends LLVMI32Node {

        @Specialization
        public int executeI32(LLVMI32Vector vector, int index) {
            return vector.getValue(index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMI64VectorNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMI64ExtractElementNode extends LLVMI64Node {

        @Specialization
        public long executeI64(LLVMI64Vector vector, int index) {
            return vector.getValue(index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMFloatVectorNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMFloatExtractElementNode extends LLVMFloatNode {

        @Specialization
        public float executeFloat(LLVMFloatVector vector, int index) {
            return vector.getValue(index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMDoubleVectorNode.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMDoubleExtractElementNode extends LLVMDoubleNode {

        @Specialization
        public Double executeDouble(LLVMDoubleVector vector, int index) {
            return vector.getValue(index);
        }
    }

}
