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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMDoubleVectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI16VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI1VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI64VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;
import com.oracle.truffle.llvm.types.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.types.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.types.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI8Vector;

public class LLVMLoadVectorNode {

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI1VectorNode extends LLVMI1VectorNode {

        public abstract int getSize();

        @Specialization
        public LLVMI1Vector executeI1Vector(LLVMAddress addr) {
            return LLVMMemory.getI1Vector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI8VectorNode extends LLVMI8VectorNode {

        public abstract int getSize();

        @Specialization
        public LLVMI8Vector executeI8Vector(LLVMAddress addr) {
            return LLVMMemory.getI8Vector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI16VectorNode extends LLVMI16VectorNode {

        public abstract int getSize();

        @Specialization
        public LLVMI16Vector executeI16Vector(LLVMAddress addr) {
            return LLVMMemory.getI16Vector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI32VectorNode extends LLVMI32VectorNode {

        public abstract int getSize();

        @Specialization
        public LLVMI32Vector executeI32Vector(LLVMAddress addr) {
            return LLVMMemory.getI32Vector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI64VectorNode extends LLVMI64VectorNode {

        public abstract int getSize();

        @Specialization
        public LLVMI64Vector executeI64Vector(LLVMAddress addr) {
            return LLVMMemory.getI64Vector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadFloatVectorNode extends LLVMFloatVectorNode {

        public abstract int getSize();

        @Specialization
        public LLVMFloatVector executeFloatVector(LLVMAddress addr) {
            return LLVMMemory.getFloatVector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadDoubleVectorNode extends LLVMDoubleVectorNode {

        public abstract int getSize();

        @Specialization
        public LLVMDoubleVector executeDoubleVector(LLVMAddress addr) {
            return LLVMMemory.getDoubleVector(addr, getSize());
        }
    }

}
