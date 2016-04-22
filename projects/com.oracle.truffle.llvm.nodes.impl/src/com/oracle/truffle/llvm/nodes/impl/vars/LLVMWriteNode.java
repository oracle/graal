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
package com.oracle.truffle.llvm.nodes.impl.vars;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
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
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.LLVMTruffleObject;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;

@NodeField(name = "slot", type = FrameSlot.class)
public abstract class LLVMWriteNode extends LLVMNode {

    protected abstract FrameSlot getSlot();

    @NodeChild(value = "valueNode", type = LLVMI1Node.class)
    public abstract static class LLVMWriteI1Node extends LLVMWriteNode {

        @Specialization
        protected void writeI1(VirtualFrame frame, boolean value) {
            frame.setBoolean(getSlot(), value);
        }

    }

    @NodeChild(value = "valueNode", type = LLVMI8Node.class)
    public abstract static class LLVMWriteI8Node extends LLVMWriteNode {

        @Specialization
        protected void writeI8(VirtualFrame frame, byte value) {
            frame.setByte(getSlot(), value);
        }

    }

    @NodeChild(value = "valueNode", type = LLVMI16Node.class)
    public abstract static class LLVMWriteI16Node extends LLVMWriteNode {

        @Specialization
        protected void writeI16(VirtualFrame frame, short value) {
            frame.setInt(getSlot(), value);
        }

    }

    @NodeChild(value = "valueNode", type = LLVMI32Node.class)
    public abstract static class LLVMWriteI32Node extends LLVMWriteNode {

        @Specialization
        protected void writeI32(VirtualFrame frame, int value) {
            frame.setInt(getSlot(), value);
        }

    }

    @NodeChild(value = "valueNode", type = LLVMI64Node.class)
    public abstract static class LLVMWriteI64Node extends LLVMWriteNode {

        @Specialization
        protected void writeI64(VirtualFrame frame, long value) {
            frame.setLong(getSlot(), value);
        }

    }

    @NodeChild(value = "valueNode", type = LLVMIVarBitNode.class)
    public abstract static class LLVMWriteIVarBitNode extends LLVMWriteNode {

        @Specialization
        protected void writeIVarBit(VirtualFrame frame, LLVMIVarBit value) {
            frame.setObject(getSlot(), value);
        }

    }

    @NodeChild(value = "valueNode", type = LLVMFloatNode.class)
    public abstract static class LLVMWriteFloatNode extends LLVMWriteNode {

        @Specialization
        protected void writeDouble(VirtualFrame frame, float value) {
            frame.setFloat(getSlot(), value);
        }

    }

    @NodeChild(value = "valueNode", type = LLVMDoubleNode.class)
    public abstract static class LLVMWriteDoubleNode extends LLVMWriteNode {

        @Specialization
        protected void writeDouble(VirtualFrame frame, double value) {
            frame.setDouble(getSlot(), value);
        }

    }

    @NodeChild(value = "valueNode", type = LLVM80BitFloatNode.class)
    public abstract static class LLVMWrite80BitFloatingNode extends LLVMWriteNode {

        @Specialization
        protected void write80BitFloat(VirtualFrame frame, LLVM80BitFloat value) {
            frame.setObject(getSlot(), value);
        }
    }

    @NodeChild(value = "valueNode", type = LLVMAddressNode.class)
    public abstract static class LLVMWriteAddressNode extends LLVMWriteNode {

        @Specialization
        protected void writeAddress(VirtualFrame frame, LLVMAddress value) {
            frame.setObject(getSlot(), value);
        }

        @Specialization
        protected void writeObject(VirtualFrame frame, LLVMTruffleObject value) {
            frame.setObject(getSlot(), value);
        }

    }

    @NodeChild(value = "valueNode", type = LLVMFunctionNode.class)
    public abstract static class LLVMWriteFunctionNode extends LLVMWriteNode {

        @Specialization
        protected void writeAddress(VirtualFrame frame, LLVMFunctionDescriptor value) {
            frame.setObject(getSlot(), value);
        }

    }

}
