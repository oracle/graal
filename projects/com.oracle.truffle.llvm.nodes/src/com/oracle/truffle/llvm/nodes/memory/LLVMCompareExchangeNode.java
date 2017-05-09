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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.base.LLVMFrameUtil;
import com.oracle.truffle.llvm.nodes.memory.LLVMCompareExchangeNodeGen.LLVMCMPXCHInternalNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class, value = "address"), @NodeChild(type = LLVMExpressionNode.class, value = "comparisonValue"),
                @NodeChild(type = LLVMExpressionNode.class, value = "newValue")})
public abstract class LLVMCompareExchangeNode extends LLVMExpressionNode {

    @Child private LLVMCMPXCHInternalNode cmpxch;

    public LLVMCompareExchangeNode(FrameSlot stackPointerSlot, Type resultType, int resultSize, int secondValueOffset) {
        this.cmpxch = LLVMCMPXCHInternalNodeGen.create(stackPointerSlot, resultType, resultSize, secondValueOffset);
    }

    abstract static class LLVMCMPXCHInternalNode extends LLVMNode {

        private final FrameSlot stackPointerSlot;
        private final Type resultType;
        private final int resultSize;
        private final int secondValueOffset;

        LLVMCMPXCHInternalNode(FrameSlot stackPointerSlot, Type resultType, int resultSize, int secondValueOffset) {
            this.stackPointerSlot = stackPointerSlot;
            this.resultType = resultType;
            this.resultSize = resultSize;
            this.secondValueOffset = secondValueOffset;
        }

        public abstract Object executeWithTarget(VirtualFrame frame, LLVMAddress address, Object cmpValue, Object newValue, LLVMStack stack);

        private LLVMAddress getResultAllocation(VirtualFrame frame, LLVMStack stack) {
            return LLVMFrameUtil.allocateMemory(stack, frame, stackPointerSlot, resultSize, 8, resultType);
        }

        @Specialization
        public Object execute(VirtualFrame frame, LLVMAddress address, byte comparisonValue, byte newValue, LLVMStack stack) {
            byte value = LLVMMemory.getI8(address);
            LLVMAddress allocation = getResultAllocation(frame, stack);
            LLVMMemory.putI8(allocation, value);
            if (value == comparisonValue) {
                LLVMMemory.putI8(address, newValue);
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, true);
            } else {
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, false);
            }
            return allocation;
        }

        @Specialization
        public Object execute(VirtualFrame frame, LLVMAddress address, short comparisonValue, short newValue, LLVMStack stack) {
            short value = LLVMMemory.getI16(address);
            LLVMAddress allocation = getResultAllocation(frame, stack);
            LLVMMemory.putI16(allocation, value);
            if (value == comparisonValue) {
                LLVMMemory.putI16(address, newValue);
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, true);
            } else {
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, false);
            }
            return allocation;
        }

        @Specialization
        public Object execute(VirtualFrame frame, LLVMAddress address, int comparisonValue, int newValue, LLVMStack stack) {
            int value = LLVMMemory.getI32(address);
            LLVMAddress allocation = getResultAllocation(frame, stack);
            LLVMMemory.putI32(allocation, value);
            if (value == comparisonValue) {
                LLVMMemory.putI32(address, newValue);
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, true);
            } else {
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, false);
            }
            return allocation;
        }

        @Specialization
        public Object execute(VirtualFrame frame, LLVMAddress address, long comparisonValue, long newValue, LLVMStack stack) {
            long value = LLVMMemory.getI64(address);
            LLVMAddress allocation = getResultAllocation(frame, stack);
            LLVMMemory.putI64(allocation, value);
            if (value == comparisonValue) {
                LLVMMemory.putI64(address, newValue);
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, true);
            } else {
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, false);
            }
            return allocation;
        }

        @Specialization
        public Object execute(VirtualFrame frame, LLVMAddress address, LLVMAddress comparisonValue, LLVMAddress newValue, LLVMStack stack) {
            LLVMAddress value = LLVMMemory.getAddress(address);
            LLVMAddress allocation = getResultAllocation(frame, stack);
            LLVMMemory.putAddress(allocation, value);
            if (value.getVal() == comparisonValue.getVal()) {
                LLVMMemory.putAddress(address, newValue);
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, true);
            } else {
                LLVMMemory.putI1(allocation.getVal() + secondValueOffset, false);
            }
            return allocation;
        }

    }

    @Specialization
    public Object execute(VirtualFrame frame, LLVMAddress address, Object comparisonValue, Object newValue, @Cached("getContext().getStack()") LLVMStack stack) {
        return cmpxch.executeWithTarget(frame, address, comparisonValue, newValue, stack);
    }

    @Specialization
    public Object execute(VirtualFrame frame, LLVMGlobalVariable address, Object comparisonValue, Object newValue, @Cached("getContext().getStack()") LLVMStack stack) {
        return cmpxch.executeWithTarget(frame, address.getNativeLocation(), comparisonValue, newValue, stack);
    }

}
