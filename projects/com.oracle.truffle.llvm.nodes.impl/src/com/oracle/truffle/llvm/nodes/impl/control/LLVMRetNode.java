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
package com.oracle.truffle.llvm.nodes.impl.control;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMTerminatorNode;
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
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.vector.LLVMVector;

@NodeField(name = "retSlot", type = FrameSlot.class)
public abstract class LLVMRetNode extends LLVMTerminatorNode {

    public static final int RETURN_FROM_FUNCTION = -1;

    public LLVMRetNode() {
        super(RETURN_FROM_FUNCTION);
    }

    public abstract FrameSlot getRetSlot();

    @NodeChild(value = "retResult", type = LLVMI1Node.class)
    public abstract static class LLVMI1RetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, boolean retResult) {
            frame.setBoolean(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMI8Node.class)
    public abstract static class LLVMI8RetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, byte retResult) {
            frame.setByte(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMI16Node.class)
    public abstract static class LLVMI16RetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, short retResult) {
            frame.setInt(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMI32Node.class)
    public abstract static class LLVMI32RetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, int retResult) {
            frame.setInt(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMI64Node.class)
    public abstract static class LLVMI64RetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, long retResult) {
            frame.setLong(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMIVarBitNode.class)
    public abstract static class LLVMIVarBitRetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, LLVMIVarBit retResult) {
            frame.setObject(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMFloatNode.class)
    public abstract static class LLVMFloatRetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, float retResult) {
            frame.setFloat(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMDoubleNode.class)
    public abstract static class LLVMDoubleRetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, double retResult) {
            frame.setDouble(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVM80BitFloatNode.class)
    public abstract static class LLVM80BitFloatRetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, LLVM80BitFloat retResult) {
            frame.setObject(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMAddressNode.class)
    public abstract static class LLVMAddressRetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, LLVMAddress retResult) {
            frame.setObject(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMFunctionNode.class)
    public abstract static class LLVMFunctionRetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, LLVMFunctionDescriptor retResult) {
            frame.setObject(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMVectorNode.class)
    public abstract static class LLVMVectorRetNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, LLVMVector<?> retResult) {
            frame.setObject(getRetSlot(), retResult);
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    @NodeChild(value = "retResult", type = LLVMAddressNode.class)
    @NodeField(name = "structSize", type = int.class)
    public abstract static class LLVMStructRetNode extends LLVMRetNode {

        public abstract int getStructSize();

        @Specialization
        public int executeGetSuccessorIndex(VirtualFrame frame, LLVMAddress retResult) {
            LLVMAddress retStructAddress = (LLVMAddress) FrameUtil.getObjectSafe(frame, getRetSlot());
            LLVMHeap.memCopy(retStructAddress, retResult, getStructSize());
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

    public abstract static class LLVMVoidReturnNode extends LLVMRetNode {

        @Specialization
        public int executeGetSuccessorIndex() {
            return LLVMBasicBlockNode.DEFAULT_SUCCESSOR;
        }

    }

}
