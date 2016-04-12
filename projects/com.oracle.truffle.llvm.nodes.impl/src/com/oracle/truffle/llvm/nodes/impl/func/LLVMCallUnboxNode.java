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

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
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
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMVectorNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.types.vector.LLVMVector;

/**
 * This node unboxes the return result of a function call.
 *
 * The {@link LLVMCallNode} returns its result as an Object. Since other nodes expect specific
 * primitives or objects as their input, this node has to unbox the result.
 */
public abstract class LLVMCallUnboxNode {

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI1CallUnboxNode extends LLVMI1Node {

        @Specialization
        public boolean executeI1(Object value) {
            return (boolean) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI8CallUnboxNode extends LLVMI8Node {

        @Specialization
        public byte executeI8(Object value) {
            return (byte) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI16CallUnboxNode extends LLVMI16Node {

        @Specialization
        public short executeI16(Object value) {
            return (short) (int) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI32CallUnboxNode extends LLVMI32Node {

        @Specialization
        public int executeI32(Object value) {
            return (int) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI64CallUnboxNode extends LLVMI64Node {

        @Specialization
        public long executeI64(Object value) {
            return (long) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMVarBitCallUnboxNode extends LLVMIVarBitNode {

        @Specialization
        public LLVMIVarBit executeVarI(Object value) {
            return (LLVMIVarBit) value;
        }
    }

    public static class LLVMVoidCallUnboxNode extends LLVMNode {

        @Child private LLVMExpressionNode functionCallNode;

        public LLVMVoidCallUnboxNode(LLVMExpressionNode functionCallNode) {
            this.functionCallNode = functionCallNode;
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            functionCallNode.executeVoid(frame);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMFloatCallUnboxNode extends LLVMFloatNode {

        @Specialization
        public float executeFloat(Object value) {
            return (float) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMDoubleCallUnboxNode extends LLVMDoubleNode {

        @Specialization
        public double executeDouble(Object value) {
            return (double) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVM80BitFloatCallUnboxNode extends LLVM80BitFloatNode {

        @Specialization
        public LLVM80BitFloat execute80BitFloat(Object value) {
            return (LLVM80BitFloat) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMVectorCallUnboxNode extends LLVMVectorNode {

        @Specialization
        public LLVMVector<?> executeVector(Object value) {
            return (LLVMVector<?>) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMAddressCallUnboxNode extends LLVMAddressNode {

        @Specialization
        public LLVMAddress executePointee(Object value) {
            return (LLVMAddress) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMFunctionCallUnboxNode extends LLVMFunctionNode {

        @Specialization
        public LLVMFunctionDescriptor executeFunction(Object value) {
            return (LLVMFunctionDescriptor) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMStructCallUnboxNode extends LLVMAddressNode {

        @Specialization
        public LLVMAddress executePointee(Object value) {
            return (LLVMAddress) value;
        }
    }
}
