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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.others.LLVMGlobalVariableDescriptorGuards;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.types.IntegerType;

@NodeChildren(value = {@NodeChild(type = LLVMExpressionNode.class, value = "pointerNode")})
public abstract class LLVMStoreNode extends LLVMExpressionNode {

    @Child protected Node foreignWrite = Message.WRITE.createNode();

    protected void doForeignAccess(VirtualFrame frame, LLVMTruffleObject addr, int stride, Object value) {
        try {
            ForeignAccess.sendWrite(foreignWrite, frame, addr.getObject(), (int) (addr.getOffset() / stride), value);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void doForeignAccess(VirtualFrame frame, TruffleObject addr, Object value) {
        try {
            ForeignAccess.sendWrite(foreignWrite, frame, addr, 0, value);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new IllegalStateException(e);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMI1StoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, boolean value) {
            LLVMMemory.putI1(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMI8StoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, byte value) {
            LLVMMemory.putI8(address, value);
            return null;
        }

        @Specialization
        public Object execute(VirtualFrame frame, LLVMTruffleObject address, byte value) {
            doForeignAccess(frame, address, 1, value);
            return null;
        }

        @Specialization
        public Object execute(VirtualFrame frame, TruffleObject address, byte value) {
            execute(frame, new LLVMTruffleObject(address, IntegerType.BYTE), value);
            return null;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMI16StoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, short value) {
            LLVMMemory.putI16(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMI32StoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, int value) {
            LLVMMemory.putI32(address, value);
            return null;
        }

        @Specialization
        public Object execute(VirtualFrame frame, LLVMTruffleObject address, int value) {
            doForeignAccess(frame, address, LLVMExpressionNode.I32_SIZE_IN_BYTES, value);
            return null;
        }

        @Specialization
        public Object execute(VirtualFrame frame, TruffleObject address, int value) {
            execute(frame, new LLVMTruffleObject(address, IntegerType.INTEGER), value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMI64StoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, long value) {
            LLVMMemory.putI64(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMIVarBitStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, LLVMIVarBit value) {
            LLVMMemory.putIVarBit(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMFloatStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, float value) {
            LLVMMemory.putFloat(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMDoubleStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, double value) {
            LLVMMemory.putDouble(address, value);
            return null;
        }

        @Specialization
        public Object execute(VirtualFrame frame, LLVMTruffleObject address, double value) {
            doForeignAccess(frame, address, LLVMExpressionNode.DOUBLE_SIZE_IN_BYTES, value);
            return null;
        }

        @Specialization
        public Object execute(VirtualFrame frame, TruffleObject address, double value) {
            doForeignAccess(frame, address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVM80BitFloatStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, LLVM80BitFloat value) {
            LLVMMemory.put80BitFloat(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMAddressStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, LLVMAddress value) {
            LLVMMemory.putAddress(address, value);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isLLVMAddress(value)")
        public void execute(LLVMAddress address, Object value) {
            CompilerDirectives.bailout("unsupported operation");
            throw new UnsupportedOperationException("Sulong can't store a Truffle object in a native memory address " + address);
        }

        @Specialization
        public Object execute(VirtualFrame frame, LLVMTruffleObject address, Object value) {
            doForeignAccess(frame, address, LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES, value);
            return null;
        }

        @Specialization
        public Object execute(VirtualFrame frame, TruffleObject address, Object value) {
            doForeignAccess(frame, address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    @ImportStatic(LLVMGlobalVariableDescriptorGuards.class)
    public abstract static class LLVMGlobalVariableStoreNode extends LLVMExpressionNode {

        protected final LLVMGlobalVariableDescriptor descriptor;

        public LLVMGlobalVariableStoreNode(LLVMGlobalVariableDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Specialization(guards = "needsTransition(frame, descriptor)")
        public Object executeTransitionNative(VirtualFrame frame, LLVMAddress value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            descriptor.transition(true, false);
            executeNative(frame, value);
            return null;
        }

        @Specialization(guards = {"needsTransition(frame, descriptor)", "!isLLVMAddress(value)"})
        public Object executeTransitionManaged(VirtualFrame frame, Object value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            descriptor.transition(true, true);
            executeManaged(frame, value);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNative(frame, descriptor)")
        public Object executeNative(VirtualFrame frame, LLVMAddress value) {
            LLVMMemory.putAddress(descriptor.getNativeStorage(), value);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isNative(frame, descriptor)", "!isLLVMAddress(value)"})
        public Object executeManagedUnsupported(VirtualFrame frame, Object value) {
            CompilerDirectives.bailout("unsupported operation");
            throw new UnsupportedOperationException("Sulong can't store a Truffle object in a global variable " + descriptor.getName() + " that previously stored a native address");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isManaged(frame, descriptor)", "!isLLVMAddress(value)"})
        public Object executeManaged(VirtualFrame frame, Object value) {
            descriptor.setManagedStorage(value);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isManaged(frame, descriptor)")
        public Object executeManagedUnsupported(VirtualFrame frame, LLVMAddress value) {
            CompilerDirectives.bailout("unsupported operation");
            throw new UnsupportedOperationException("Sulong can't store a native address in a global variable " + descriptor.getName() + " that previously stored a Truffle object");
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMFunctionStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, LLVMFunctionDescriptor function) {
            LLVMHeap.putFunctionIndex(address, function.getFunctionIndex());
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    @NodeField(type = int.class, name = "structSize")
    public abstract static class LLVMStructStoreNode extends LLVMStoreNode {

        public abstract int getStructSize();

        @Specialization
        public Object execute(LLVMAddress address, LLVMAddress value) {
            LLVMMemory.putStruct(address, value, getStructSize());
            return null;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMI1ArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMI1ArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI8(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    boolean currentValue = values[i].executeI1(frame);
                    LLVMMemory.putI1(currentAddress, currentValue);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMI8ArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMI8ArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI8(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    byte currentValue = values[i].executeI8(frame);
                    LLVMMemory.putI8(currentAddress, currentValue);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMI16ArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMI16ArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI8(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    short currentValue = values[i].executeI16(frame);
                    LLVMMemory.putI16(currentAddress, currentValue);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMI32ArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMI32ArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI32(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    int currentValue = values[i].executeI32(frame);
                    LLVMMemory.putI32(currentAddress, currentValue);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMI64ArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMI64ArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI64(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    long currentValue = values[i].executeI64(frame);
                    LLVMMemory.putI64(currentAddress, currentValue);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMFloatArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMFloatArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeI64(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    float currentValue = values[i].executeFloat(frame);
                    LLVMMemory.putFloat(currentAddress, currentValue);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMDoubleArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMDoubleArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    double currentValue = values[i].executeDouble(frame);
                    LLVMMemory.putDouble(currentAddress, currentValue);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVM80BitFloatArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVM80BitFloatArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress write80BitFloat(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    LLVM80BitFloat currentValue = values[i].executeLLVM80BitFloat(frame);
                    LLVMMemory.put80BitFloat(currentAddress, currentValue);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMAddressArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMAddressArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    LLVMAddress currentValue = values[i].executeLLVMAddress(frame);
                    LLVMMemory.putAddress(currentAddress, currentValue);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMFunctionArrayLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMFunctionArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    LLVMFunctionDescriptor currentValue = (LLVMFunctionDescriptor) values[i].executeTruffleObject(frame);
                    LLVMHeap.putFunctionIndex(currentAddress, currentValue.getFunctionIndex());
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

    @NodeChild(value = "address", type = LLVMExpressionNode.class)
    public abstract static class LLVMAddressArrayCopyNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;
        private final int stride;

        public LLVMAddressArrayCopyNode(LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    LLVMAddress currentValue = values[i].executeLLVMAddress(frame);
                    LLVMHeap.memCopy(currentAddress, currentValue, stride);
                    currentAddress = currentAddress.increment(stride);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return addr;
        }

    }

}
