/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI16;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI32;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI64;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.CMPXCHGI8;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMCompareExchangeNodeGen.AllocResultNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMCompareExchangeNodeGen.LLVMCMPXCHInternalNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNode.LLVMI1OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNode.LLVMI16OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode.LLVMI32OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode.LLVMI8OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.LLVMPointerOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMAddressEqualsNode;
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
        AllocResultNode allocResult = AllocResultNodeGen.create(secondValueOffset, resultAllocation);
        this.cmpxch = LLVMCMPXCHInternalNodeGen.create(allocResult);
    }

    @Specialization
    protected Object doOp(VirtualFrame frame, LLVMPointer address, Object comparisonValue, Object newValue) {
        return cmpxch.executeWithTarget(frame, address, comparisonValue, newValue);
    }

    abstract static class AllocResultNode extends LLVMNode {

        private final long secondValueOffset;

        @Child LLVMExpressionNode resultAllocation;
        @Child LLVMI1OffsetStoreNode successStore;

        protected abstract Object execute(VirtualFrame frame, Object value, boolean success);

        protected abstract Object execute(VirtualFrame frame, byte value, boolean success);

        protected abstract Object execute(VirtualFrame frame, short value, boolean success);

        protected abstract Object execute(VirtualFrame frame, int value, boolean success);

        protected abstract Object execute(VirtualFrame frame, long value, boolean success);

        AllocResultNode(long secondValueOffset, LLVMExpressionNode resultAllocation) {
            this.secondValueOffset = secondValueOffset;
            this.resultAllocation = resultAllocation;
            this.successStore = LLVMI1OffsetStoreNode.create();
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        LLVMPointer doByte(VirtualFrame frame, byte value, boolean success,
                        @Cached LLVMI8OffsetStoreNode valueStore) throws UnexpectedResultException {
            LLVMPointer ret = resultAllocation.executeLLVMPointer(frame);
            valueStore.executeWithTarget(ret, 0, value);
            successStore.executeWithTarget(ret, secondValueOffset, success);
            return ret;
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        LLVMPointer doShort(VirtualFrame frame, short value, boolean success,
                        @Cached LLVMI16OffsetStoreNode valueStore) throws UnexpectedResultException {
            LLVMPointer ret = resultAllocation.executeLLVMPointer(frame);
            valueStore.executeWithTarget(ret, 0, value);
            successStore.executeWithTarget(ret, secondValueOffset, success);
            return ret;
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        LLVMPointer doInt(VirtualFrame frame, int value, boolean success,
                        @Cached LLVMI32OffsetStoreNode valueStore) throws UnexpectedResultException {
            LLVMPointer ret = resultAllocation.executeLLVMPointer(frame);
            valueStore.executeWithTarget(ret, 0, value);
            successStore.executeWithTarget(ret, secondValueOffset, success);
            return ret;
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        LLVMPointer doLong(VirtualFrame frame, long value, boolean success,
                        @Cached LLVMI64OffsetStoreNode valueStore) throws UnexpectedResultException {
            LLVMPointer ret = resultAllocation.executeLLVMPointer(frame);
            valueStore.executeWithTarget(ret, 0, value);
            successStore.executeWithTarget(ret, secondValueOffset, success);
            return ret;
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        LLVMPointer doPointer(VirtualFrame frame, LLVMPointer value, boolean success,
                        @Cached LLVMPointerOffsetStoreNode valueStore) throws UnexpectedResultException {
            LLVMPointer ret = resultAllocation.executeLLVMPointer(frame);
            valueStore.executeWithTarget(ret, 0, value);
            successStore.executeWithTarget(ret, secondValueOffset, success);
            return ret;
        }
    }

    abstract static class LLVMCMPXCHInternalNode extends LLVMNode {

        @Child AllocResultNode allocResult;

        LLVMCMPXCHInternalNode(AllocResultNode allocResult) {
            this.allocResult = allocResult;
        }

        public abstract Object executeWithTarget(VirtualFrame frame, Object address, Object cmpValue, Object newValue);

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, byte comparisonValue, byte newValue) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            CMPXCHGI8 compareAndSwapI8 = memory.compareAndSwapI8(this, address, comparisonValue, newValue);
            return allocResult.execute(frame, compareAndSwapI8.getValue(), compareAndSwapI8.isSwap());
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, short comparisonValue, short newValue) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            CMPXCHGI16 compareAndSwapI16 = memory.compareAndSwapI16(this, address, comparisonValue, newValue);
            return allocResult.execute(frame, compareAndSwapI16.getValue(), compareAndSwapI16.isSwap());
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, int comparisonValue, int newValue) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            CMPXCHGI32 compareAndSwapI32 = memory.compareAndSwapI32(this, address, comparisonValue, newValue);
            return allocResult.execute(frame, compareAndSwapI32.getValue(), compareAndSwapI32.isSwap());
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, long comparisonValue, long newValue) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            CMPXCHGI64 compareAndSwapI64 = memory.compareAndSwapI64(this, address, comparisonValue, newValue);
            return allocResult.execute(frame, compareAndSwapI64.getValue(), compareAndSwapI64.isSwap());
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMNativePointer address, LLVMNativePointer comparisonValue, LLVMNativePointer newValue) {
            return doOp(frame, address, comparisonValue.asNative(), newValue.asNative());
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, byte comparisonValue, byte newValue,
                        @Cached LLVMI8LoadNode read,
                        @Cached LLVMI8StoreNode write) {
            synchronized (address.getObject()) {
                byte currentValue = read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                return allocResult.execute(frame, currentValue, success);
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, short comparisonValue, short newValue,
                        @Cached LLVMI16LoadNode read,
                        @Cached LLVMI16StoreNode write) {
            synchronized (address.getObject()) {
                short currentValue = read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                return allocResult.execute(frame, currentValue, success);
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, int comparisonValue, int newValue,
                        @Cached LLVMI32LoadNode read,
                        @Cached LLVMI32StoreNode write) {
            synchronized (address.getObject()) {
                int currentValue = read.executeWithTarget(address);
                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                return allocResult.execute(frame, currentValue, success);
            }
        }

        static class RewriteException extends SlowPathException {

            private static final long serialVersionUID = 1L;
        }

        @Specialization(rewriteOn = RewriteException.class)
        protected Object doLong(VirtualFrame frame, LLVMManagedPointer address, long comparisonValue, long newValue,
                        @Cached LLVMI64LoadNode read,
                        @Cached LLVMI64StoreNode write) throws RewriteException {
            synchronized (address.getObject()) {
                long currentValue;
                try {
                    currentValue = read.executeWithTarget(address);
                } catch (UnexpectedResultException ex) {
                    throw new RewriteException();
                }

                boolean success = currentValue == comparisonValue;
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                return allocResult.execute(frame, currentValue, success);
            }
        }

        @Specialization(replaces = "doLong")
        protected Object doLongGeneric(VirtualFrame frame, LLVMManagedPointer address, long comparisonValue, long newValue,
                        @Cached LLVMI64LoadNode read,
                        @Cached LLVMI64StoreNode write,
                        @Cached LLVMAddressEqualsNode cmp) {
            synchronized (address.getObject()) {
                Object currentValue = read.executeWithTargetGeneric(address);
                boolean success = cmp.executeWithTarget(currentValue, comparisonValue);
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                return allocResult.execute(frame, currentValue, success);
            }
        }

        @Specialization
        protected Object doOp(VirtualFrame frame, LLVMManagedPointer address, LLVMPointer comparisonValue, LLVMPointer newValue,
                        @Cached LLVMPointerLoadNode read,
                        @Cached LLVMPointerStoreNode write,
                        @Cached LLVMAddressEqualsNode cmp) {
            synchronized (address.getObject()) {
                LLVMPointer currentValue = read.executeWithTarget(address);
                boolean success = cmp.executeWithTarget(currentValue, comparisonValue);
                if (success) {
                    write.executeWithTarget(address, newValue);
                }
                return allocResult.execute(frame, currentValue, success);
            }
        }
    }
}
