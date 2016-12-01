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
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMDoubleVectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI16VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI1VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI64VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.types.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.types.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI8Vector;

public abstract class LLVMInsertElementNode {

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI1VectorNode.class), @NodeChild(type = LLVMI1Node.class, value = "element"),
                    @NodeChild(type = LLVMI32Node.class, value = "index")})
    public abstract static class LLVMI1InsertElementNode extends LLVMI1VectorNode {

        @Specialization
        public LLVMI1Vector executeI1(LLVMAddress address, LLVMI1Vector vector, boolean element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI8VectorNode.class), @NodeChild(type = LLVMI8Node.class, value = "element"),
                    @NodeChild(type = LLVMI32Node.class, value = "index")})
    public abstract static class LLVMI8InsertElementNode extends LLVMI8VectorNode {

        @Specialization
        public LLVMI8Vector executeI8(LLVMAddress address, LLVMI8Vector vector, byte element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI16VectorNode.class), @NodeChild(type = LLVMI16Node.class, value = "element"),
                    @NodeChild(type = LLVMI32Node.class, value = "index")})
    public abstract static class LLVMI16InsertElementNode extends LLVMI16VectorNode {

        @Specialization
        public LLVMI16Vector executeI16(LLVMAddress address, LLVMI16Vector vector, short element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI32VectorNode.class), @NodeChild(type = LLVMI32Node.class, value = "element"),
                    @NodeChild(type = LLVMI32Node.class, value = "index")})
    public abstract static class LLVMI32InsertElementNode extends LLVMI32VectorNode {

        @Specialization
        public LLVMI32Vector executeI32(LLVMAddress address, LLVMI32Vector vector, int element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMI64VectorNode.class), @NodeChild(type = LLVMI64Node.class, value = "element"),
                    @NodeChild(type = LLVMI32Node.class, value = "index")})
    public abstract static class LLVMI64InsertElementNode extends LLVMI64VectorNode {

        @Specialization
        public LLVMI64Vector executeI64(LLVMAddress address, LLVMI64Vector vector, long element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMFloatVectorNode.class), @NodeChild(type = LLVMFloatNode.class, value = "element"),
                    @NodeChild(type = LLVMI32Node.class, value = "index")})
    public abstract static class LLVMFloatInsertElementNode extends LLVMFloatVectorNode {

        @Specialization
        public LLVMFloatVector executeFloat(LLVMAddress address, LLVMFloatVector vector, float element, int index) {
            return vector.insert(address, element, index);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMDoubleVectorNode.class), @NodeChild(type = LLVMDoubleNode.class, value = "element"),
                    @NodeChild(type = LLVMI32Node.class, value = "index")})
    public abstract static class LLVMDoubleInsertElementNode extends LLVMDoubleVectorNode {

        @Specialization
        public LLVMDoubleVector executeDouble(LLVMAddress address, LLVMDoubleVector vector, double element, int index) {
            return vector.insert(address, element, index);
        }
    }

}
