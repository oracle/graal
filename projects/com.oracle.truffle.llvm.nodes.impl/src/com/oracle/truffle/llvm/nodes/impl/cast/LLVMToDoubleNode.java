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
package com.oracle.truffle.llvm.nodes.impl.cast;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;

public abstract class LLVMToDoubleNode extends LLVMDoubleNode {

    @NodeChild(value = "fromNode", type = LLVMI1Node.class)
    public abstract static class LLVMI1ToDoubleNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(boolean from) {
            return from ? 1 : 0;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI8Node.class)
    public abstract static class LLVMI8ToDoubleNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(byte from) {
            return from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI8Node.class)
    public abstract static class LLVMI8ToDoubleZeroExtNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(byte from) {
            return from & LLVMI8Node.MASK;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI16Node.class)
    public abstract static class LLVMI16ToDoubleNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(short from) {
            return from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI16Node.class)
    public abstract static class LLVMI16ToDoubleZeroExtNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(short from) {
            return from & LLVMI16Node.MASK;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI32Node.class)
    public abstract static class LLVMI32ToDoubleNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(int from) {
            return from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI32Node.class)
    public abstract static class LLVMI32ToDoubleUnsignedNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(int from) {
            return from & LLVMI32Node.MASK;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI64Node.class)
    public abstract static class LLVMI64ToDoubleNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(long from) {
            return from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI64Node.class)
    public abstract static class LLVMI64ToDoubleUnsignedNode extends LLVMToDoubleNode {

        private static final double LEADING_BIT = 0x1.0p63;

        @Specialization
        public double executeDouble(long from) {
            double val = from & Long.MAX_VALUE;
            if (from < 0) {
                val += LEADING_BIT;
            }
            return val;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI64Node.class)
    public abstract static class LLVMI64ToDoubleBitNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(long from) {
            return Double.longBitsToDouble(from);
        }
    }

    @NodeChild(value = "fromNode", type = LLVMFloatNode.class)
    public abstract static class LLVMFloatToDoubleNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(float from) {
            return from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVM80BitFloatNode.class)
    public abstract static class LLVM80BitFloatToDoubleNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(LLVM80BitFloat from) {
            return from.getDoubleValue();
        }
    }

}
