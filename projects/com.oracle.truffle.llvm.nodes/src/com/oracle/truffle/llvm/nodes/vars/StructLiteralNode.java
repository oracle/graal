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
package com.oracle.truffle.llvm.nodes.vars;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public class StructLiteralNode extends LLVMExpressionNode {

    public static class LLVMI1StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVMI1StructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                boolean value = valueNode.executeI1(frame);
                LLVMMemory.putI1(address, value);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class LLVMI8StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVMI8StructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                byte value = valueNode.executeI8(frame);
                LLVMMemory.putI8(address, value);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class LLVMI16StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVMI16StructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                short value = valueNode.executeI16(frame);
                LLVMMemory.putI16(address, value);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class LLVMI32StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVMI32StructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                int value = valueNode.executeI32(frame);
                LLVMMemory.putI32(address, value);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class LLVMI64StructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVMI64StructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                long value = valueNode.executeI64(frame);
                LLVMMemory.putI64(address, value);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

    }

    public static class LLVMFloatStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVMFloatStructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                float value = valueNode.executeFloat(frame);
                LLVMMemory.putFloat(address, value);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

    }

    public static class LLVMDoubleStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVMDoubleStructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                double value = valueNode.executeDouble(frame);
                LLVMMemory.putDouble(address, value);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

    }

    public static class LLVM80BitFloatStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVM80BitFloatStructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                LLVM80BitFloat value = valueNode.executeLLVM80BitFloat(frame);
                LLVMMemory.put80BitFloat(address, value);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

    }

    public static class LLVMCompoundStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;
        private int size;

        public LLVMCompoundStructWriteNode(LLVMExpressionNode valueNode, int size) {
            this.valueNode = valueNode;
            this.size = size;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                LLVMAddress value = valueNode.executeLLVMAddress(frame);
                LLVMHeap.memCopy(address, value, size);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

    }

    public static class LLVMEmptyStructWriteNode extends LLVMStructWriteNode {

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            return null;
        }

    }

    @Child private LLVMExpressionNode address;
    @CompilationFinal(dimensions = 1) private final int[] offsets;
    @Children private final LLVMStructWriteNode[] elementWriteNodes;

    public StructLiteralNode(int[] offsets, LLVMStructWriteNode[] elementWriteNodes, LLVMExpressionNode address) {
        this.offsets = offsets;
        this.elementWriteNodes = elementWriteNodes;
        this.address = address;
    }

    @Override
    @ExplodeLoop
    public LLVMAddress executeLLVMAddress(VirtualFrame frame) {
        try {
            LLVMAddress addr = address.executeLLVMAddress(frame);
            for (int i = 0; i < offsets.length; i++) {
                LLVMAddress currentAddr = addr.increment(offsets[i]);
                elementWriteNodes[i].executeWrite(frame, currentAddr);
            }
            return addr;
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return executeLLVMAddress(frame);
    }

    public static class LLVMAddressStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVMAddressStructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                LLVMAddress value = valueNode.executeLLVMAddress(frame);
                LLVMMemory.putAddress(address, value);
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

    }

    public static class LLVMFunctionStructWriteNode extends LLVMStructWriteNode {

        @Child private LLVMExpressionNode valueNode;

        public LLVMFunctionStructWriteNode(LLVMExpressionNode valueNode) {
            this.valueNode = valueNode;
        }

        @Override
        public Object executeWrite(VirtualFrame frame, LLVMAddress address) {
            try {
                LLVMFunctionDescriptor value = valueNode.executeLLVMFunctionDescriptor(frame);
                LLVMHeap.putFunctionIndex(address, value.getFunctionIndex());
                return null;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

    }

}
