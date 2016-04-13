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
package com.oracle.truffle.llvm.nodes.impl.func;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMDoubleVectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI16VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI1VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI64VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.types.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.types.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.types.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI8Vector;

public class LLVMArgNode {

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI1ArgNode extends LLVMI1Node {

        public abstract int getIndex();

        @Specialization
        @Override
        public boolean executeI1(VirtualFrame frame) {
            return (boolean) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI8ArgNode extends LLVMI8Node {

        public abstract int getIndex();

        @Specialization
        @Override
        public byte executeI8(VirtualFrame frame) {
            return (byte) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI16ArgNode extends LLVMI16Node {

        public abstract int getIndex();

        @Specialization
        @Override
        public short executeI16(VirtualFrame frame) {
            return (short) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI32ArgNode extends LLVMI32Node {

        public abstract int getIndex();

        @Specialization
        @Override
        public int executeI32(VirtualFrame frame) {
            return (int) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI64ArgNode extends LLVMI64Node {

        public abstract int getIndex();

        @Specialization
        @Override
        public long executeI64(VirtualFrame frame) {
            return (long) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMIVarBitArgNode extends LLVMIVarBitNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMIVarBit executeVarI(VirtualFrame frame) {
            return (LLVMIVarBit) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMFloatArgNode extends LLVMFloatNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public float executeFloat(VirtualFrame frame) {
            return (float) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMDoubleArgNode extends LLVMDoubleNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public double executeDouble(VirtualFrame frame) {
            return (double) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVM80BitFloatArgNode extends LLVM80BitFloatNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVM80BitFloat execute80BitFloat(VirtualFrame frame) {
            return (LLVM80BitFloat) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMAddressArgNode extends LLVMAddressNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMAddress executePointee(VirtualFrame frame) {
            return (LLVMAddress) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMFunctionArgNode extends LLVMFunctionNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMFunctionDescriptor executeFunction(VirtualFrame frame) {
            return (LLVMFunctionDescriptor) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI1VectorArgNode extends LLVMI1VectorNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMI1Vector executeI1Vector(VirtualFrame frame) {
            return (LLVMI1Vector) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI8VectorArgNode extends LLVMI8VectorNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMI8Vector executeI8Vector(VirtualFrame frame) {
            return (LLVMI8Vector) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI16VectorArgNode extends LLVMI16VectorNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMI16Vector executeI16Vector(VirtualFrame frame) {
            return (LLVMI16Vector) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI32VectorArgNode extends LLVMI32VectorNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMI32Vector executeI32Vector(VirtualFrame frame) {
            return (LLVMI32Vector) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMI64VectorArgNode extends LLVMI64VectorNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMI64Vector executeI64Vector(VirtualFrame frame) {
            return (LLVMI64Vector) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMFloatVectorArgNode extends LLVMFloatVectorNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMFloatVector executeFloatVector(VirtualFrame frame) {
            return (LLVMFloatVector) frame.getArguments()[getIndex()];
        }

    }

    @NodeField(name = "index", type = int.class)
    public abstract static class LLVMDoubleVectorArgNode extends LLVMDoubleVectorNode {

        public abstract int getIndex();

        @Specialization
        @Override
        public LLVMDoubleVector executeDoubleVector(VirtualFrame frame) {
            return (LLVMDoubleVector) frame.getArguments()[getIndex()];
        }

    }

}
