/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.memory.LLVMCompareExchangeNodeGen.LLVMCMPXCHInternalNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI16;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI32;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI64;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI8;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class, value = "address"), @NodeChild(type = LLVMExpressionNode.class, value = "comparisonValue"),
                @NodeChild(type = LLVMExpressionNode.class, value = "newValue")})
public abstract class LLVMCompareExchangeNode extends LLVMExpressionNode {

    @Child private LLVMCMPXCHInternalNode cmpxch;

    public LLVMCompareExchangeNode(int resultSize, long secondValueOffset) {
        this.cmpxch = LLVMCMPXCHInternalNodeGen.create(resultSize, secondValueOffset);
    }

    abstract static class LLVMCMPXCHInternalNode extends LLVMNode {

        private final int resultSize;
        private final long secondValueOffset;

        LLVMCMPXCHInternalNode(int resultSize, long secondValueOffset) {
            this.resultSize = resultSize;
            this.secondValueOffset = secondValueOffset;
        }

        @CompilationFinal private FrameSlot stackPointerSlot;

        private FrameSlot getStackPointerSlot() {
            if (stackPointerSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                stackPointerSlot = getRootNode().getFrameDescriptor().findFrameSlot(LLVMStack.FRAME_ID);
            }
            return stackPointerSlot;
        }

        public abstract Object executeWithTarget(VirtualFrame frame, LLVMAddress address, Object cmpValue, Object newValue);

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMAddress address, byte comparisonValue, byte newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            CMPXCHGI8 compareAndSwapI8 = memory.compareAndSwapI8(address, comparisonValue, newValue);
            LLVMAddress allocation = LLVMAddress.fromLong(LLVMStack.allocateStackMemory(frame, memory, getStackPointerSlot(), resultSize, 8));
            memory.putI8(allocation, compareAndSwapI8.getValue());
            memory.putI1(allocation.getVal() + secondValueOffset, compareAndSwapI8.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMAddress address, short comparisonValue, short newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            CMPXCHGI16 compareAndSwapI16 = memory.compareAndSwapI16(address, comparisonValue, newValue);
            LLVMAddress allocation = LLVMAddress.fromLong(LLVMStack.allocateStackMemory(frame, memory, getStackPointerSlot(), resultSize, 8));
            memory.putI16(allocation, compareAndSwapI16.getValue());
            memory.putI1(allocation.getVal() + secondValueOffset, compareAndSwapI16.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMAddress address, int comparisonValue, int newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            CMPXCHGI32 compareAndSwapI32 = memory.compareAndSwapI32(address, comparisonValue, newValue);
            LLVMAddress allocation = LLVMAddress.fromLong(LLVMStack.allocateStackMemory(frame, memory, getStackPointerSlot(), resultSize, 8));
            memory.putI32(allocation, compareAndSwapI32.getValue());
            memory.putI1(allocation.getVal() + secondValueOffset, compareAndSwapI32.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMAddress address, long comparisonValue, long newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            CMPXCHGI64 compareAndSwapI64 = memory.compareAndSwapI64(address, comparisonValue, newValue);
            LLVMAddress allocation = LLVMAddress.fromLong(LLVMStack.allocateStackMemory(frame, memory, getStackPointerSlot(), resultSize, 8));
            memory.putI64(allocation, compareAndSwapI64.getValue());
            memory.putI1(allocation.getVal() + secondValueOffset, compareAndSwapI64.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMAddress address, LLVMAddress comparisonValue, LLVMAddress newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            CMPXCHGI64 compareAndSwapI64 = memory.compareAndSwapI64(address, comparisonValue.getVal(), newValue.getVal());
            LLVMAddress allocation = LLVMAddress.fromLong(LLVMStack.allocateStackMemory(frame, memory, getStackPointerSlot(), resultSize, 8));
            memory.putI64(allocation, compareAndSwapI64.getValue());
            memory.putI1(allocation.getVal() + secondValueOffset, compareAndSwapI64.isSwap());
            return allocation;
        }
    }

    @Specialization
    protected Object doOp(VirtualFrame frame, LLVMAddress address, Object comparisonValue, Object newValue) {
        return cmpxch.executeWithTarget(frame, address, comparisonValue, newValue);
    }

    @Specialization
    protected Object doOp(VirtualFrame frame, LLVMGlobal address, Object comparisonValue, Object newValue,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        return cmpxch.executeWithTarget(frame, globalAccess.executeWithTarget(frame, address), comparisonValue, newValue);
    }
}
