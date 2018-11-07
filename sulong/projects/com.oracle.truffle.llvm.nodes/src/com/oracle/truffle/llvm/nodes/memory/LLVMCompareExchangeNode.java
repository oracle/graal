/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.memory.LLVMCompareExchangeNodeGen.LLVMCMPXCHInternalNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI16LoadNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI16LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI16StoreNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI8StoreNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI16;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI32;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI64;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI8;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.AggregateType;

@NodeChild(type = LLVMExpressionNode.class, value = "address")
@NodeChild(type = LLVMExpressionNode.class, value = "comparisonValue")
@NodeChild(type = LLVMExpressionNode.class, value = "newValue")
public abstract class LLVMCompareExchangeNode extends LLVMExpressionNode {

    @Child private LLVMCMPXCHInternalNode cmpxch;

    public LLVMCompareExchangeNode(LLVMContext context, AggregateType returnType) {
        int resultSize = context.getByteSize(returnType);
        long secondValueOffset = context.getIndexOffset(1, returnType);
        this.cmpxch = LLVMCMPXCHInternalNodeGen.create(resultSize, secondValueOffset);
    }

    @Specialization
    protected Object doOp(VirtualFrame frame, LLVMPointer address, Object comparisonValue, Object newValue) {
        return cmpxch.executeWithTarget(frame, address, comparisonValue, newValue);
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

        public abstract Object executeWithTarget(VirtualFrame frame, Object address, Object cmpValue, Object newValue);

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, byte comparisonValue, byte newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            CMPXCHGI8 compareAndSwapI8 = memory.compareAndSwapI8(address, comparisonValue, newValue);
            LLVMNativePointer allocation = allocateResult(frame, memory);
            memory.putI8(allocation, compareAndSwapI8.getValue());
            memory.putI1(allocation.increment(secondValueOffset), compareAndSwapI8.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, short comparisonValue, short newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            CMPXCHGI16 compareAndSwapI16 = memory.compareAndSwapI16(address, comparisonValue, newValue);
            LLVMNativePointer allocation = allocateResult(frame, memory);
            memory.putI16(allocation, compareAndSwapI16.getValue());
            memory.putI1(allocation.increment(secondValueOffset), compareAndSwapI16.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, int comparisonValue, int newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            CMPXCHGI32 compareAndSwapI32 = memory.compareAndSwapI32(address, comparisonValue, newValue);
            LLVMNativePointer allocation = allocateResult(frame, memory);
            memory.putI32(allocation, compareAndSwapI32.getValue());
            memory.putI1(allocation.increment(secondValueOffset), compareAndSwapI32.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, long comparisonValue, long newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            CMPXCHGI64 compareAndSwapI64 = memory.compareAndSwapI64(address, comparisonValue, newValue);
            LLVMNativePointer allocation = allocateResult(frame, memory);
            memory.putI64(allocation, compareAndSwapI64.getValue());
            memory.putI1(allocation.increment(secondValueOffset), compareAndSwapI64.isSwap());
            return allocation;
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, LLVMNativePointer comparisonValue, LLVMNativePointer newValue,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return doOp(frame, address, comparisonValue.asNative(), newValue.asNative(), memory);
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, byte comparisonValue, byte newValue,
                        @Cached("createI8Read()") LLVMI8LoadNode read,
                        @Cached("createI8Write()") LLVMI8StoreNode write,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            synchronized (address.getObject()) {
                LLVMNativePointer allocation = allocateResult(frame, memory);
                byte currentValue = (byte) read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                memory.putI8(allocation, currentValue);
                memory.putI1(allocation.increment(secondValueOffset), success);
                return allocation;
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, short comparisonValue, short newValue,
                        @Cached("createI16Read()") LLVMI16LoadNode read,
                        @Cached("createI16Write()") LLVMI16StoreNode write,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            synchronized (address.getObject()) {
                LLVMNativePointer allocation = allocateResult(frame, memory);
                short currentValue = (short) read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                memory.putI16(allocation, currentValue);
                memory.putI1(allocation.increment(secondValueOffset), success);
                return allocation;
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, int comparisonValue, int newValue,
                        @Cached("createI32Read()") LLVMI32LoadNode read,
                        @Cached("createI32Write()") LLVMI32StoreNode write,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            synchronized (address.getObject()) {
                LLVMNativePointer allocation = allocateResult(frame, memory);
                int currentValue = (int) read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                memory.putI32(allocation, currentValue);
                memory.putI1(allocation.increment(secondValueOffset), success);
                return allocation;
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, long comparisonValue, long newValue,
                        @Cached("createI64Read()") LLVMI64LoadNode read,
                        @Cached("createI64Write()") LLVMI64StoreNode write,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            synchronized (address.getObject()) {
                LLVMNativePointer allocation = allocateResult(frame, memory);
                long currentValue = (long) read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                memory.putI64(allocation, currentValue);
                memory.putI1(allocation.increment(secondValueOffset), success);
                return allocation;
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, LLVMNativePointer comparisonValue, LLVMNativePointer newValue,
                        @Cached("createI64Read()") LLVMI64LoadNode read,
                        @Cached("createI64Write()") LLVMI64StoreNode write,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return doOp(frame, address, comparisonValue.asNative(), newValue.asNative(), read, write, memory);
        }

        private LLVMNativePointer allocateResult(VirtualFrame frame, LLVMMemory memory) {
            LLVMNativePointer allocation = LLVMNativePointer.create(LLVMStack.allocateStackMemory(frame, memory, getStackPointerSlot(), resultSize, 8));
            return allocation;
        }

        protected static LLVMI8LoadNode createI8Read() {
            return LLVMI8LoadNodeGen.create(null);
        }

        protected static LLVMI8StoreNode createI8Write() {
            return LLVMI8StoreNodeGen.create(null, null);
        }

        protected static LLVMI16LoadNode createI16Read() {
            return LLVMI16LoadNodeGen.create(null);
        }

        protected static LLVMI16StoreNode createI16Write() {
            return LLVMI16StoreNodeGen.create(null, null);
        }

        protected static LLVMI32LoadNode createI32Read() {
            return LLVMI32LoadNodeGen.create(null);
        }

        protected static LLVMI32StoreNode createI32Write() {
            return LLVMI32StoreNodeGen.create(null, null);
        }

        protected static LLVMI64LoadNode createI64Read() {
            return LLVMI64LoadNodeGen.create(null);
        }

        protected static LLVMI64StoreNode createI64Write() {
            return LLVMI64StoreNodeGen.create(null, null);
        }
    }
}
