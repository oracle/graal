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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
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

/**
 * This node unboxes the return result of a function call.
 *
 * The {@link LLVMCallNode} returns its result as an Object. Since other nodes expect specific
 * primitives or objects as their input, this node has to unbox the result.
 */
public abstract class LLVMCallUnboxNode {

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI1CallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public boolean executeI1(boolean value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI8CallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public byte executeI8(byte value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI16CallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public short executeI16(int value) {
            return (short) value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI32CallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public int executeI32(int value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMI64CallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public long executeI64(long value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMVarBitCallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public LLVMIVarBit executeVarI(LLVMIVarBit value) {
            return value;
        }
    }

    public static class LLVMVoidCallUnboxNode extends LLVMExpressionNode {

        @Child private LLVMExpressionNode functionCallNode;

        public LLVMVoidCallUnboxNode(LLVMExpressionNode functionCallNode) {
            this.functionCallNode = functionCallNode;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            functionCallNode.executeGeneric(frame);
            return null;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMFloatCallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public float executeFloat(float value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMDoubleCallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public double executeDouble(double value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVM80BitFloatCallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public LLVM80BitFloat execute80BitFloat(LLVM80BitFloat value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMVectorCallUnboxNode extends LLVMExpressionNode {

        @Specialization
        protected Object doVector(LLVMDoubleVector value) {
            return value;
        }

        @Specialization
        protected Object doVector(LLVMFloatVector value) {
            return value;
        }

        @Specialization
        protected Object doVector(LLVMI16Vector value) {
            return value;
        }

        @Specialization
        protected Object doVector(LLVMI1Vector value) {
            return value;
        }

        @Specialization
        protected Object doVector(LLVMI32Vector value) {
            return value;
        }

        @Specialization
        protected Object doVector(LLVMI64Vector value) {
            return value;
        }

        @Specialization
        protected Object doVector(LLVMI8Vector value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMAddressCallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public Object executeObject(Object value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMFunctionCallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public LLVMFunctionDescriptor executeFunction(LLVMFunctionDescriptor value) {
            return value;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "functionCallNode")
    public abstract static class LLVMStructCallUnboxNode extends LLVMExpressionNode {

        @Specialization
        public LLVMAddress executePointee(LLVMAddress value) {
            return value;
        }
    }
}
