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
package com.oracle.truffle.llvm.nodes.impl.memory;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
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
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

@NodeChildren(value = {@NodeChild(type = LLVMAddressNode.class, value = "pointerNode")})
public abstract class LLVMStoreNode extends LLVMNode {

    @Child protected Node foreignWrite = Message.WRITE.createNode();

    protected void doForeignAccess(VirtualFrame frame, LLVMTruffleObject addr, int stride, Object value) {
        try {
            ForeignAccess.sendWrite(foreignWrite, frame, addr.getObject(), (int) (addr.getOffset() / stride), value);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new IllegalStateException(e);
        }
    }

    @NodeChild(type = LLVMI1Node.class, value = "valueNode")
    public abstract static class LLVMI1StoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, boolean value) {
            LLVMMemory.putI1(address, value);
        }

    }

    @NodeChild(type = LLVMI8Node.class, value = "valueNode")
    public abstract static class LLVMI8StoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, byte value) {
            LLVMMemory.putI8(address, value);
        }

    }

    @NodeChild(type = LLVMI16Node.class, value = "valueNode")
    public abstract static class LLVMI16StoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, short value) {
            LLVMMemory.putI16(address, value);
        }

    }

    @NodeChild(type = LLVMI32Node.class, value = "valueNode")
    public abstract static class LLVMI32StoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, int value) {
            LLVMMemory.putI32(address, value);
        }

        @Specialization
        public void execute(VirtualFrame frame, LLVMTruffleObject address, int value) {
            doForeignAccess(frame, address, LLVMI32Node.BYTE_SIZE, value);
        }

    }

    @NodeChild(type = LLVMI64Node.class, value = "valueNode")
    public abstract static class LLVMI64StoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, long value) {
            LLVMMemory.putI64(address, value);
        }

    }

    @NodeChild(type = LLVMIVarBitNode.class, value = "valueNode")
    public abstract static class LLVMIVarBitStoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, LLVMIVarBit value) {
            LLVMMemory.putIVarBit(address, value);
        }

    }

    @NodeChild(type = LLVMFloatNode.class, value = "valueNode")
    public abstract static class LLVMFloatStoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, float value) {
            LLVMMemory.putFloat(address, value);
        }

    }

    @NodeChild(type = LLVMDoubleNode.class, value = "valueNode")
    public abstract static class LLVMDoubleStoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, double value) {
            LLVMMemory.putDouble(address, value);
        }

        @Specialization
        public void execute(VirtualFrame frame, LLVMTruffleObject address, double value) {
            doForeignAccess(frame, address, LLVMDoubleNode.BYTE_SIZE, value);
        }

    }

    @NodeChild(type = LLVM80BitFloatNode.class, value = "valueNode")
    public abstract static class LLVM80BitFloatStoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, LLVM80BitFloat value) {
            LLVMMemory.put80BitFloat(address, value);
        }

    }

    @NodeChild(type = LLVMAddressNode.class, value = "valueNode")
    public abstract static class LLVMAddressStoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, LLVMAddress value) {
            LLVMMemory.putAddress(address, value);
        }

    }

    @NodeChild(type = LLVMFunctionNode.class, value = "valueNode")
    public abstract static class LLVMFunctionStoreNode extends LLVMStoreNode {

        @Specialization
        public void execute(LLVMAddress address, LLVMFunctionDescriptor function) {
            LLVMHeap.putFunctionIndex(address, function.getFunctionIndex());
        }

    }

    @NodeChild(type = LLVMAddressNode.class, value = "valueNode")
    @NodeField(type = int.class, name = "structSize")
    public abstract static class LLVMStructStoreNode extends LLVMStoreNode {

        public abstract int getStructSize();

        @Specialization
        public void execute(LLVMAddress address, LLVMAddress value) {
            LLVMMemory.putStruct(address, value, getStructSize());
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMI1ArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVMI1Node[] values;
        private final int stride;

        public LLVMI1ArrayLiteralNode(LLVMI1Node[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI8(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                boolean currentValue = values[i].executeI1(frame);
                LLVMMemory.putI1(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMI8ArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVMI8Node[] values;
        private final int stride;

        public LLVMI8ArrayLiteralNode(LLVMI8Node[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI8(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                byte currentValue = values[i].executeI8(frame);
                LLVMMemory.putI8(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMI16ArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVMI16Node[] values;
        private final int stride;

        public LLVMI16ArrayLiteralNode(LLVMI16Node[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI8(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                short currentValue = values[i].executeI16(frame);
                LLVMMemory.putI16(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMI32ArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVMI32Node[] values;
        private final int stride;

        public LLVMI32ArrayLiteralNode(LLVMI32Node[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI32(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                int currentValue = values[i].executeI32(frame);
                LLVMMemory.putI32(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMI64ArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVMI64Node[] values;
        private final int stride;

        public LLVMI64ArrayLiteralNode(LLVMI64Node[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI64(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                long currentValue = values[i].executeI64(frame);
                LLVMMemory.putI64(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMFloatArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVMFloatNode[] values;
        private final int stride;

        public LLVMFloatArrayLiteralNode(LLVMFloatNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI64(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                float currentValue = values[i].executeFloat(frame);
                LLVMMemory.putFloat(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMDoubleArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVMDoubleNode[] values;
        private final int stride;

        public LLVMDoubleArrayLiteralNode(LLVMDoubleNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                double currentValue = values[i].executeDouble(frame);
                LLVMMemory.putDouble(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVM80BitFloatArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVM80BitFloatNode[] values;
        private final int stride;

        public LLVM80BitFloatArrayLiteralNode(LLVM80BitFloatNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress write80BitFloat(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                LLVM80BitFloat currentValue = values[i].execute80BitFloat(frame);
                LLVMMemory.put80BitFloat(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMAddressArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVMAddressNode[] values;
        private final int stride;

        public LLVMAddressArrayLiteralNode(LLVMAddressNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                LLVMAddress currentValue = values[i].executePointee(frame);
                LLVMMemory.putAddress(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMFunctionArrayLiteralNode extends LLVMAddressNode {

        @Children private final LLVMFunctionNode[] values;
        private final int stride;

        public LLVMFunctionArrayLiteralNode(LLVMFunctionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                LLVMFunctionDescriptor currentValue = values[i].executeFunction(frame);
                LLVMHeap.putFunctionIndex(currentAddress, currentValue.getFunctionIndex());
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMAddressNode.class)
    public abstract static class LLVMAddressArrayCopyNode extends LLVMAddressNode {

        @Children private final LLVMAddressNode[] values;
        private final int stride;

        public LLVMAddressArrayCopyNode(LLVMAddressNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                LLVMAddress currentValue = values[i].executePointee(frame);
                LLVMHeap.memCopy(currentAddress, currentValue, stride);
                currentAddress = currentAddress.increment(stride);
            }
            return addr;
        }

    }

}
