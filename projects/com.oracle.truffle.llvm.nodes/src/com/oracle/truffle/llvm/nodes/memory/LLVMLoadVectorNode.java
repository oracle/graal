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
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public class LLVMLoadVectorNode {

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI1VectorNode extends LLVMExpressionNode {

        public abstract int getSize();

        @Specialization
        public LLVMI1Vector executeI1Vector(LLVMAddress addr) {
            return LLVMMemory.getI1Vector(addr, getSize());
        }

        @Specialization
        public LLVMI1Vector executeI1Vector(LLVMGlobalVariable addr) {
            return LLVMMemory.getI1Vector(addr.getNativeLocation(), getSize());
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI8VectorNode extends LLVMExpressionNode {

        public abstract int getSize();

        @Specialization
        public LLVMI8Vector executeI8Vector(LLVMGlobalVariable addr) {
            return LLVMMemory.getI8Vector(addr.getNativeLocation(), getSize());
        }

        @Specialization
        public LLVMI8Vector executeI8Vector(LLVMAddress addr) {
            return LLVMMemory.getI8Vector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI16VectorNode extends LLVMExpressionNode {

        public abstract int getSize();

        @Specialization
        public LLVMI16Vector executeI16Vector(LLVMGlobalVariable addr) {
            return LLVMMemory.getI16Vector(addr.getNativeLocation(), getSize());
        }

        @Specialization
        public LLVMI16Vector executeI16Vector(LLVMAddress addr) {
            return LLVMMemory.getI16Vector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI32VectorNode extends LLVMExpressionNode {

        public abstract int getSize();

        @Specialization
        public LLVMI32Vector executeI32Vector(LLVMGlobalVariable addr) {
            return LLVMMemory.getI32Vector(addr.getNativeLocation(), getSize());
        }

        @Specialization
        public LLVMI32Vector executeI32Vector(LLVMAddress addr) {
            return LLVMMemory.getI32Vector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadI64VectorNode extends LLVMExpressionNode {

        public abstract int getSize();

        @Specialization
        public LLVMI64Vector executeI64Vector(LLVMGlobalVariable addr) {
            return LLVMMemory.getI64Vector(addr.getNativeLocation(), getSize());
        }

        @Specialization
        public LLVMI64Vector executeI64Vector(LLVMAddress addr) {
            return LLVMMemory.getI64Vector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadFloatVectorNode extends LLVMExpressionNode {

        public abstract int getSize();

        @Specialization
        public LLVMFloatVector executeFloatVector(LLVMGlobalVariable addr) {
            return LLVMMemory.getFloatVector(addr.getNativeLocation(), getSize());
        }

        @Specialization
        public LLVMFloatVector executeFloatVector(LLVMAddress addr) {
            return LLVMMemory.getFloatVector(addr, getSize());
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "size", type = int.class)
    public abstract static class LLVMLoadDoubleVectorNode extends LLVMExpressionNode {

        public abstract int getSize();

        @Specialization
        public LLVMDoubleVector executeDoubleVector(LLVMGlobalVariable addr) {
            return LLVMMemory.getDoubleVector(addr.getNativeLocation(), getSize());
        }

        @Specialization
        public LLVMDoubleVector executeDoubleVector(LLVMAddress addr) {
            return LLVMMemory.getDoubleVector(addr, getSize());
        }
    }

}
