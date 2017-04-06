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
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMDataEscapeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleNull;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions.MemCopyNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

@NodeChildren(value = {@NodeChild(type = LLVMExpressionNode.class, value = "pointerNode")})
public abstract class LLVMStoreNode extends LLVMExpressionNode {

    @Child protected Node foreignWrite = Message.WRITE.createNode();
    @Child protected LLVMDataEscapeNode dataEscape = LLVMDataEscapeNodeGen.create();

    protected void doForeignAccess(LLVMTruffleObject addr, int stride, Object value) {
        try {
            ForeignAccess.sendWrite(foreignWrite, addr.getObject(), (int) (addr.getOffset() / stride), dataEscape.executeWithTarget(value));
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void doForeignAccess(TruffleObject addr, Object value) {
        try {
            ForeignAccess.sendWrite(foreignWrite, addr, 0, dataEscape.executeWithTarget(value));
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new IllegalStateException(e);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMI1StoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, boolean value) {
            LLVMMemory.putI1(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, boolean value) {
            LLVMMemory.putI1(address, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMTruffleObject address, boolean value) {
            doForeignAccess(address, 1, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMBoxedPrimitive address, boolean value) {
            if (address.getValue() instanceof Long) {
                LLVMMemory.putI1(LLVMAddress.fromLong((long) address.getValue()), value);
                return null;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access address: " + address.getValue());
            }
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, boolean value) {
            execute(new LLVMTruffleObject(address, PrimitiveType.I1), value);
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
        public Object execute(LLVMGlobalVariableDescriptor address, byte value) {
            LLVMMemory.putI8(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object execute(LLVMTruffleObject address, byte value) {
            doForeignAccess(address, 1, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMBoxedPrimitive address, byte value) {
            if (address.getValue() instanceof Long) {
                LLVMMemory.putI8(LLVMAddress.fromLong((long) address.getValue()), value);
                return null;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access address: " + address.getValue());
            }
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, byte value) {
            execute(new LLVMTruffleObject(address, PrimitiveType.I8), value);
            return null;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMI16StoreNode extends LLVMStoreNode {
        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, short value) {
            LLVMMemory.putI16(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, short value) {
            LLVMMemory.putI16(address, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMTruffleObject address, short value) {
            doForeignAccess(address, LLVMExpressionNode.I16_SIZE_IN_BYTES, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMBoxedPrimitive address, short value) {
            if (address.getValue() instanceof Long) {
                LLVMMemory.putI16(LLVMAddress.fromLong((long) address.getValue()), value);
                return null;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access address: " + address.getValue());
            }
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, short value) {
            execute(new LLVMTruffleObject(address, PrimitiveType.I16), value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMI32StoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, int value) {
            LLVMMemory.putI32(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, int value) {
            LLVMMemory.putI32(address, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMTruffleObject address, int value) {
            doForeignAccess(address, LLVMExpressionNode.I32_SIZE_IN_BYTES, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMBoxedPrimitive address, int value) {
            if (address.getValue() instanceof Long) {
                LLVMMemory.putI32(LLVMAddress.fromLong((long) address.getValue()), value);
                return null;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access address: " + address.getValue());
            }
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, int value) {
            execute(new LLVMTruffleObject(address, PrimitiveType.I32), value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMI64StoreNode extends LLVMStoreNode {
        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, long value) {
            LLVMMemory.putI64(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, long value) {
            LLVMMemory.putI64(address, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMTruffleObject address, long value) {
            doForeignAccess(address, LLVMExpressionNode.I64_SIZE_IN_BYTES, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMBoxedPrimitive address, long value) {
            if (address.getValue() instanceof Long) {
                LLVMMemory.putI64(LLVMAddress.fromLong((long) address.getValue()), value);
                return null;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access address: " + address.getValue());
            }
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, long value) {
            execute(new LLVMTruffleObject(address, PrimitiveType.I64), value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMIVarBitStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, LLVMIVarBit value) {
            LLVMMemory.putIVarBit(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, LLVMIVarBit value) {
            LLVMMemory.putIVarBit(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMFloatStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, float value) {
            LLVMMemory.putFloat(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, float value) {
            LLVMMemory.putFloat(address, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMTruffleObject address, float value) {
            doForeignAccess(address, LLVMExpressionNode.FLOAT_SIZE_IN_BYTES, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMBoxedPrimitive address, float value) {
            if (address.getValue() instanceof Long) {
                LLVMMemory.putFloat(LLVMAddress.fromLong((long) address.getValue()), value);
                return null;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access address: " + address.getValue());
            }
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, float value) {
            execute(new LLVMTruffleObject(address, PrimitiveType.FLOAT), value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMDoubleStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, double value) {
            LLVMMemory.putDouble(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, double value) {
            LLVMMemory.putDouble(address, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMTruffleObject address, double value) {
            doForeignAccess(address, LLVMExpressionNode.DOUBLE_SIZE_IN_BYTES, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMBoxedPrimitive address, double value) {
            if (address.getValue() instanceof Long) {
                LLVMMemory.putDouble(LLVMAddress.fromLong((long) address.getValue()), value);
                return null;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access address: " + address.getValue());
            }
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, double value) {
            doForeignAccess(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVM80BitFloatStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, LLVM80BitFloat value) {
            LLVMMemory.put80BitFloat(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, LLVM80BitFloat value) {
            LLVMMemory.put80BitFloat(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMAddressStoreNode extends LLVMStoreNode {

        @Specialization
        public Object doAddress(LLVMAddress address, LLVMAddress value) {
            LLVMMemory.putAddress(address, value);
            return null;
        }

        @Specialization
        public Object execute(LLVMBoxedPrimitive address, LLVMAddress value) {
            if (address.getValue() instanceof Long) {
                LLVMMemory.putAddress(LLVMAddress.fromLong((long) address.getValue()), value);
                return null;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access address: " + address.getValue());
            }
        }

        @Specialization
        public Object execute(LLVMBoxedPrimitive address, LLVMGlobalVariableDescriptor value) {
            if (address.getValue() instanceof Long) {
                LLVMMemory.putAddress(LLVMAddress.fromLong((long) address.getValue()), value.getNativeAddress());
                return null;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access address: " + address.getValue());
            }
        }

        @Specialization
        public Object doAddress(LLVMGlobalVariableDescriptor address, LLVMAddress value) {
            LLVMMemory.putAddress(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object doAddress(LLVMAddress address, LLVMGlobalVariableDescriptor value) {
            LLVMMemory.putAddress(address, value.getNativeAddress());
            return null;
        }

        @Specialization
        public Object doAddress(LLVMGlobalVariableDescriptor address, LLVMGlobalVariableDescriptor value) {
            LLVMMemory.putAddress(address.getNativeAddress(), value.getNativeAddress());
            return null;
        }

        @Specialization(guards = "notLLVM(value)")
        public Object doAddress(LLVMGlobalVariableDescriptor address, TruffleObject value) {
            address.storeTruffleObject(value);
            return null;
        }

        @Specialization
        public Object doAddress(LLVMGlobalVariableDescriptor address, LLVMBoxedPrimitive value) {
            address.storeTruffleObject(value);
            return null;
        }

        @Specialization
        public Object doAddress(LLVMGlobalVariableDescriptor address, LLVMTruffleObject value) {
            address.storeLLVMTruffleObject(value);
            return null;
        }

        @Specialization(guards = "notLLVM(value)")
        public void execute(LLVMAddress address, TruffleObject value) {
            CompilerDirectives.bailout("unsupported operation");
            throw new UnsupportedOperationException("Sulong can't store a Truffle object in a native memory address " + address + " value: " + value);
        }

        @Specialization(guards = "notLLVM(value)")
        public Object execute(LLVMBoxedPrimitive address, TruffleObject value) {
            CompilerDirectives.bailout("unsupported operation");
            throw new UnsupportedOperationException("Sulong can't store a Truffle object in a native memory address " + address + " value: " + value);
        }

        @Specialization
        public Object execute(LLVMTruffleObject address, LLVMAddress value) {
            doForeignAccess(address, LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES, new LLVMTruffleAddress(value));
            return null;
        }

        @Specialization
        public Object execute(LLVMTruffleObject address, Object value) {
            doForeignAccess(address, LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES, value);
            return null;
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, LLVMAddress value) {
            doForeignAccess(address, new LLVMTruffleAddress(value));
            return null;
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, LLVMGlobalVariableDescriptor value) {
            doForeignAccess(address, new LLVMSharedGlobalVariableDescriptor(value));
            return null;
        }

        @Specialization(guards = "notLLVM(address)")
        public Object execute(TruffleObject address, Object value) {
            doForeignAccess(address, value);
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMGlobalVariableStoreNode extends LLVMExpressionNode {

        protected final LLVMGlobalVariableDescriptor descriptor;

        public LLVMGlobalVariableStoreNode(LLVMGlobalVariableDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Specialization
        public Object executeNative(LLVMAddress value) {
            descriptor.storeLLVMAddress(value);
            return null;
        }

        @Specialization
        public Object executeNative(LLVMTruffleAddress value) {
            descriptor.storeLLVMAddress(value.getAddress());
            return null;
        }

        @Specialization
        public Object executeNative(LLVMFunctionHandle value) {
            descriptor.storeFunction(value);
            return null;
        }

        @Specialization
        public Object executeNative(LLVMFunctionDescriptor value) {
            descriptor.storeFunction(value);
            return null;
        }

        @Specialization
        public Object executeNative(LLVMTruffleNull value) {
            descriptor.storeNull(value);
            return null;
        }

        @Specialization
        public Object executeNative(LLVMGlobalVariableDescriptor value) {
            descriptor.storeGlobalVariableDescriptor(value);
            return null;
        }

        @Specialization
        public Object executeNative(LLVMTruffleObject value) {
            descriptor.storeLLVMTruffleObject(value);
            return null;
        }

        @Child private ToLLVMNode toLLVM = ToLLVMNode.createNode(long.class);

        @Specialization
        public Object executeLLVMBoxedPrimitive(LLVMBoxedPrimitive value) {
            descriptor.storeTruffleObject(value);
            return null;
        }

        @Specialization(guards = "notLLVM(value)")
        public Object executeManaged(TruffleObject value) {
            descriptor.storeTruffleObject(value);
            return null;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    public abstract static class LLVMFunctionStoreNode extends LLVMStoreNode {

        @Specialization
        public Object execute(LLVMAddress address, LLVMFunction function) {
            LLVMHeap.putFunctionIndex(address, function.getFunctionIndex());
            return null;
        }

        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, LLVMFunction function) {
            LLVMHeap.putFunctionIndex(address.getNativeAddress(), function.getFunctionIndex());
            return null;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
    @NodeField(type = int.class, name = "structSize")
    public abstract static class LLVMStructStoreNode extends LLVMStoreNode {

        @Child private MemCopyNode memCopy;

        public abstract int getStructSize();

        protected LLVMStructStoreNode(LLVMNativeFunctions heapFunctions) {
            memCopy = heapFunctions.createMemCopyNode();
        }

        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, LLVMGlobalVariableDescriptor value) {
            memCopy.execute(address.getNativeAddress(), value.getNativeAddress(), getStructSize());
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, LLVMGlobalVariableDescriptor value) {
            memCopy.execute(address, value.getNativeAddress(), getStructSize());
            return null;
        }

        @Specialization
        public Object execute(LLVMGlobalVariableDescriptor address, LLVMAddress value) {
            memCopy.execute(address.getNativeAddress(), value, getStructSize());
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, LLVMAddress value) {
            memCopy.execute(address, value, getStructSize());
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
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeI8(frame, global.getNativeAddress());
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
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeI8(frame, global.getNativeAddress());
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
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeI8(frame, global.getNativeAddress());
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
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeI32(frame, global.getNativeAddress());
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
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeI64(frame, global.getNativeAddress());
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
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeI64(frame, global.getNativeAddress());
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
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeDouble(frame, global.getNativeAddress());
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
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return write80BitFloat(frame, global.getNativeAddress());
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

        public LLVMExpressionNode[] getValues() {
            return values;
        }

        @Specialization
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeDouble(frame, global.getNativeAddress());
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                LLVMAddress currentValue = values[i].enforceLLVMAddress(frame);
                LLVMMemory.putAddress(currentAddress, currentValue);
                currentAddress = currentAddress.increment(stride);
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
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeDouble(frame, global.getNativeAddress());
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

        @Child private MemCopyNode memCopy;

        public LLVMAddressArrayCopyNode(LLVMNativeFunctions heapFunctions, LLVMExpressionNode[] values, int stride) {
            this.values = values;
            this.stride = stride;
            this.memCopy = heapFunctions.createMemCopyNode();
        }

        @Specialization
        protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariableDescriptor global) {
            return writeDouble(frame, global.getNativeAddress());
        }

        @Specialization
        @ExplodeLoop
        protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
            LLVMAddress currentAddress = addr;
            for (int i = 0; i < values.length; i++) {
                try {
                    LLVMAddress currentValue = values[i].executeLLVMAddress(frame);
                    memCopy.execute(currentAddress, currentValue, stride);
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
