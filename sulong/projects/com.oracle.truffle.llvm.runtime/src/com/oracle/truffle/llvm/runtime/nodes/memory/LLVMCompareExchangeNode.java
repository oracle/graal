/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI16;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI32;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI64;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI8;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMCompareExchangeNodeGen.LLVMCMPXCHInternalNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

@NodeChild(type = LLVMExpressionNode.class, value = "address")
@NodeChild(type = LLVMExpressionNode.class, value = "comparisonValue")
@NodeChild(type = LLVMExpressionNode.class, value = "newValue")
public abstract class LLVMCompareExchangeNode extends LLVMExpressionNode {

    @Child private LLVMCMPXCHInternalNode cmpxch;

    public static LLVMCompareExchangeNode create(LLVMExpressionNode resultAllocation, AggregateType returnType, DataLayout dataLayout, LLVMExpressionNode address, LLVMExpressionNode comparisonValue,
                    LLVMExpressionNode newValue)
                    throws TypeOverflowException {
        long secondValueOffset = returnType.getOffsetOf(1, dataLayout);
        return LLVMCompareExchangeNodeGen.create(resultAllocation, secondValueOffset, address, comparisonValue, newValue);
    }

    public LLVMCompareExchangeNode(LLVMExpressionNode resultAllocation, long secondValueOffset) {
        this.cmpxch = LLVMCMPXCHInternalNodeGen.create(resultAllocation, secondValueOffset);
    }

    @Specialization
    protected Object doOp(VirtualFrame frame, LLVMPointer address, Object comparisonValue, Object newValue) {
        return cmpxch.executeWithTarget(frame, address, comparisonValue, newValue);
    }

    abstract static class LLVMCMPXCHInternalNode extends LLVMNode {

        private final long secondValueOffset;
        @Child private LLVMExpressionNode resultAllocation;

        LLVMCMPXCHInternalNode(LLVMExpressionNode resultAllocation, long secondValueOffset) {
            this.resultAllocation = resultAllocation;
            this.secondValueOffset = secondValueOffset;
        }

        public abstract Object executeWithTarget(VirtualFrame frame, Object address, Object cmpValue, Object newValue);

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, byte comparisonValue, byte newValue) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            CMPXCHGI8 compareAndSwapI8 = memory.compareAndSwapI8(this, address, comparisonValue, newValue);
            LLVMNativePointer allocation = allocateResult(frame);
            memory.putI8(this, allocation, compareAndSwapI8.getValue());
            memory.putI1(this, allocation.increment(secondValueOffset), compareAndSwapI8.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, short comparisonValue, short newValue) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            CMPXCHGI16 compareAndSwapI16 = memory.compareAndSwapI16(this, address, comparisonValue, newValue);
            LLVMNativePointer allocation = allocateResult(frame);
            memory.putI16(this, allocation, compareAndSwapI16.getValue());
            memory.putI1(this, allocation.increment(secondValueOffset), compareAndSwapI16.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, int comparisonValue, int newValue) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            CMPXCHGI32 compareAndSwapI32 = memory.compareAndSwapI32(this, address, comparisonValue, newValue);
            LLVMNativePointer allocation = allocateResult(frame);
            memory.putI32(this, allocation, compareAndSwapI32.getValue());
            memory.putI1(this, allocation.increment(secondValueOffset), compareAndSwapI32.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, long comparisonValue, long newValue) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            CMPXCHGI64 compareAndSwapI64 = memory.compareAndSwapI64(this, address, comparisonValue, newValue);
            LLVMNativePointer allocation = allocateResult(frame);
            memory.putI64(this, allocation, compareAndSwapI64.getValue());
            memory.putI1(this, allocation.increment(secondValueOffset), compareAndSwapI64.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, LLVMNativePointer comparisonValue, LLVMNativePointer newValue) {
            return doOp(frame, address, comparisonValue.asNative(), newValue.asNative());
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, byte comparisonValue, byte newValue,
                        @Cached LLVMI8LoadNode read,
                        @Cached LLVMI8StoreNode write) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            synchronized (address.getObject()) {
                LLVMNativePointer allocation = allocateResult(frame);
                byte currentValue = read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                memory.putI8(this, allocation, currentValue);
                memory.putI1(this, allocation.increment(secondValueOffset), success);
                return allocation;
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, short comparisonValue, short newValue,
                        @Cached LLVMI16LoadNode read,
                        @Cached LLVMI16StoreNode write) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            synchronized (address.getObject()) {
                LLVMNativePointer allocation = allocateResult(frame);
                short currentValue = read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                memory.putI16(this, allocation, currentValue);
                memory.putI1(this, allocation.increment(secondValueOffset), success);
                return allocation;
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, int comparisonValue, int newValue,
                        @Cached LLVMI32LoadNode read,
                        @Cached LLVMI32StoreNode write) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            synchronized (address.getObject()) {
                LLVMNativePointer allocation = allocateResult(frame);
                int currentValue = read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                memory.putI32(this, allocation, currentValue);
                memory.putI1(this, allocation.increment(secondValueOffset), success);
                return allocation;
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, long comparisonValue, long newValue,
                        @Cached LLVMI64LoadNode read,
                        @Cached LLVMI64StoreNode write) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            synchronized (address.getObject()) {
                LLVMNativePointer allocation = allocateResult(frame);
                long currentValue = (long) read.executeWithTargetGeneric(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                memory.putI64(this, allocation, currentValue);
                memory.putI1(this, allocation.increment(secondValueOffset), success);
                return allocation;
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, LLVMNativePointer comparisonValue, LLVMNativePointer newValue,
                        @Cached LLVMI64LoadNode read,
                        @Cached LLVMI64StoreNode write) {
            return doOp(frame, address, comparisonValue.asNative(), newValue.asNative(), read, write);
        }

        private LLVMNativePointer allocateResult(VirtualFrame frame) {
            return LLVMNativePointer.cast(resultAllocation.executeGeneric(frame));
        }
    }
}
