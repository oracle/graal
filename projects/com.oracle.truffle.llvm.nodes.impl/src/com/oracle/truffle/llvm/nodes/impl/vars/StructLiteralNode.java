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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public class StructLiteralNode extends LLVMAddressNode {

    public static class LLVMI1StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMI1Node valueNode;

        public LLVMI1StructWriteNode(LLVMI1Node valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            boolean value = valueNode.executeI1(frame);
            LLVMMemory.putI1(address, value);
        }
    }

    public static class LLVMI8StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMI8Node valueNode;

        public LLVMI8StructWriteNode(LLVMI8Node valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            byte value = valueNode.executeI8(frame);
            LLVMMemory.putI8(address, value);
        }
    }

    public static class LLVMI16StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMI16Node valueNode;

        public LLVMI16StructWriteNode(LLVMI16Node valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            short value = valueNode.executeI16(frame);
            LLVMMemory.putI16(address, value);
        }
    }

    public static class LLVMI32StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMI32Node valueNode;

        public LLVMI32StructWriteNode(LLVMI32Node valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            int value = valueNode.executeI32(frame);
            LLVMMemory.putI32(address, value);
        }
    }

    public static class LLVMI64StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMI64Node valueNode;

        public LLVMI64StructWriteNode(LLVMI64Node valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            long value = valueNode.executeI64(frame);
            LLVMMemory.putI64(address, value);
        }

    }

    public static class LLVMFloatStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMFloatNode valueNode;

        public LLVMFloatStructWriteNode(LLVMFloatNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            float value = valueNode.executeFloat(frame);
            LLVMMemory.putFloat(address, value);
        }

    }

    public static class LLVMDoubleStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMDoubleNode valueNode;

        public LLVMDoubleStructWriteNode(LLVMDoubleNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            double value = valueNode.executeDouble(frame);
            LLVMMemory.putDouble(address, value);
        }

    }

    public static class LLVM80BitFloatStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVM80BitFloatNode valueNode;

        public LLVM80BitFloatStructWriteNode(LLVM80BitFloatNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            LLVM80BitFloat value = valueNode.execute80BitFloat(frame);
            LLVMMemory.put80BitFloat(address, value);
        }

    }

    public static class LLVMCompoundStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMAddressNode valueNode;
        private int size;

        public LLVMCompoundStructWriteNode(LLVMAddressNode valueNode, int size) {
            this.valueNode = valueNode;
            this.size = size;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            LLVMAddress value = valueNode.executePointee(frame);
            LLVMHeap.memCopy(address, value, size);
        }

    }

    public static class LLVMEmptyStructWriteNode extends LLVMStructWriteNode {

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {

        }

    }

    @Child private LLVMAddressNode address;
    @CompilationFinal private final int[] offsets;
    @Children private final LLVMStructWriteNode[] elementWriteNodes;

    public StructLiteralNode(int[] offsets, LLVMStructWriteNode[] elementWriteNodes, LLVMAddressNode address) {
        this.offsets = offsets;
        this.elementWriteNodes = elementWriteNodes;
        this.address = address;
    }

    @Override
    @ExplodeLoop
    public LLVMAddress executePointee(VirtualFrame frame) {
        LLVMAddress addr = address.executePointee(frame);
        for (int i = 0; i < offsets.length; i++) {
            LLVMAddress currentAddr = addr.increment(offsets[i]);
            elementWriteNodes[i].executeWrite(frame, currentAddr);
        }
        return addr;
    }

    public static class LLVMAddressStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMAddressNode valueNode;

        public LLVMAddressStructWriteNode(LLVMAddressNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            LLVMAddress value = valueNode.executePointee(frame);
            LLVMMemory.putAddress(address, value);
        }

    }

    public static class LLVMFunctionStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMFunctionNode valueNode;

        public LLVMFunctionStructWriteNode(LLVMFunctionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public void executeWrite(VirtualFrame frame, LLVMAddress address) {
            LLVMFunctionDescriptor value = valueNode.executeFunction(frame);
            LLVMHeap.putFunctionIndex(address, value.getFunctionIndex());
        }

    }

}
