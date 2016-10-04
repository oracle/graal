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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
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
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.types.vector.LLVMFloatVector;

public abstract class LLVMToI64Node extends LLVMI64Node {

    @NodeChild(value = "fromNode", type = LLVMI1Node.class)
    public abstract static class LLVMI1ToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(boolean from) {
            return from ? -1 : 0;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI1Node.class)
    public abstract static class LLVMI1ToI64ZeroExtNode extends LLVMToI64Node {

        @Specialization
        public long executeI64(boolean from) {
            return from ? 1 : 0;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI8Node.class)
    public abstract static class LLVMI8ToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(byte from) {
            return from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI8Node.class)
    public abstract static class LLVMI8ToI64ZeroExtNode extends LLVMToI64Node {

        @Specialization
        public long executeI64(byte from) {
            return from & LLVMI8Node.MASK;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI16Node.class)
    public abstract static class LLVMI16ToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(short from) {
            return from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI16Node.class)
    public abstract static class LLVMI16ToI64ZeroExtNode extends LLVMToI64Node {

        @Specialization
        public long executeI64(short from) {
            return from & LLVMI16Node.MASK;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI32Node.class)
    public abstract static class LLVMI32ToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(int from) {
            return from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI32Node.class)
    public abstract static class LLVMI32ToI64ZeroExtNode extends LLVMToI64Node {

        @Specialization
        public long executeI64(int from) {
            return from & LLVMI32Node.MASK;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMIVarBitNode.class)
    public abstract static class LLVMIVarToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(LLVMIVarBit from) {
            return from.getLongValue();
        }
    }

    @NodeChild(value = "fromNode", type = LLVMIVarBitNode.class)
    public abstract static class LLVMIVarToI64ZeroExtNode extends LLVMToI64Node {

        @Specialization
        public long executeI64(LLVMIVarBit from) {
            return from.getZeroExtendedLongValue();
        }
    }

    @NodeChild(value = "fromNode", type = LLVMFloatNode.class)
    public abstract static class LLVMFloatToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(float from) {
            return (long) from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMDoubleNode.class)
    public abstract static class LLVMDoubleToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(double from) {
            return (long) from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMDoubleNode.class)
    public abstract static class LLVMDoubleToI64BitCastNode extends LLVMToI64Node {

        @Specialization
        public long executeI64(double from) {
            return Double.doubleToRawLongBits(from);
        }
    }

    @NodeChild(value = "fromNode", type = LLVM80BitFloatNode.class)
    public abstract static class LLVM80BitFloatToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(LLVM80BitFloat from) {
            return from.getLongValue();
        }
    }

    @NodeChild(value = "fromNode", type = LLVMAddressNode.class)
    public abstract static class LLVMAddressToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(boolean from) {
            return from ? 1 : 0;
        }

        @Specialization
        public long executeI64(long from) {
            return from;
        }

        @Specialization
        public long executeI64(LLVMAddress from) {
            return from.getVal();
        }

        @Specialization
        public long executeRubyString(VirtualFrame frame, TruffleObject from,
                        @Cached("createUnboxNode()") Node unboxNode) {
            try {
                return (long) ForeignAccess.sendUnbox(unboxNode, frame, from);
            } catch (UnsupportedMessageException e) {
                throw new UnsupportedOperationException(e);
            }
        }

        protected Node createUnboxNode() {
            return Message.UNBOX.createNode();
        }

    }

    @NodeChild(value = "fromNode", type = LLVMFloatVectorNode.class)
    public abstract static class LLVMFloatVectorToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(LLVMFloatVector from) {
            float f1 = from.getValue(0);
            float f2 = from.getValue(1);
            long composedValue = (long) Float.floatToRawIntBits(f1) << Float.SIZE | Float.floatToRawIntBits(f2);
            return composedValue;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMFunctionNode.class)
    public abstract static class LLVMFunctionToI64Node extends LLVMToI64Node {

        @Specialization
        public long executeI64(LLVMFunctionDescriptor from) {
            return from.getFunctionIndex();
        }
    }
}
