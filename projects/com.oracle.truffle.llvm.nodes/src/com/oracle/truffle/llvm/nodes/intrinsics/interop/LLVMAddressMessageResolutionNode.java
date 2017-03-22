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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMPerformance;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

abstract class LLVMAddressMessageResolutionNode extends Node {
    private static final int I1_SIZE = 1;
    private static final int I8_SIZE = 1;
    private static final int I16_SIZE = 2;
    private static final int I32_SIZE = 4;
    private static final int I64_SIZE = 8;
    private static final int FLOAT_SIZE = 4;
    private static final int DOUBLE_SIZE = 8;

    @Child protected LLVMDataEscapeNode prepareValueForEscape = LLVMDataEscapeNodeGen.create();

    public Type getType(LLVMTruffleAddress receiver) {
        return receiver.getType();
    }

    public boolean typeGuard(LLVMTruffleAddress receiver, Type type) {
        return receiver.getType() == (type);
    }

    public ToLLVMNode getToLLVMNode(Type type) {
        PrimitiveType primitiveType = (PrimitiveType) ((PointerType) type).getPointeeType();
        return ToLLVMNode.createNode(ToLLVMNode.convert(primitiveType));
    }

    abstract static class LLVMAddressReadMessageResolutionNode extends LLVMAddressMessageResolutionNode {

        public abstract Object executeWithTarget(VirtualFrame frame, Object receiver, int index);

        @SuppressWarnings("unused")
        @Specialization(guards = {"index == cachedIndex", "typeGuard(receiver, cachedType)"})
        public Object doCachedTypeCachedOffset(LLVMTruffleAddress receiver, int index,
                        @Cached("getType(receiver)") Type cachedType,
                        @Cached("index") int cachedIndex) {
            return prepareValueForEscape.executeWithTarget(doRead(receiver, cachedType, cachedIndex));
        }

        @Specialization(guards = {"typeGuard(receiver, cachedType)"}, replaces = "doCachedTypeCachedOffset")
        public Object doCachedType(LLVMTruffleAddress receiver, int index,
                        @Cached("getType(receiver)") Type cachedType) {
            return prepareValueForEscape.executeWithTarget(doRead(receiver, cachedType, index));
        }

        @Specialization(replaces = {"doCachedTypeCachedOffset", "doCachedType"})
        public Object doRegular(LLVMTruffleAddress receiver, int index) {
            LLVMPerformance.warn(this);
            return prepareValueForEscape.executeWithTarget(doRead(receiver, receiver.getType(), index));
        }

        private static Object doRead(LLVMTruffleAddress receiver, Type cachedType, int cachedIndex) {
            LLVMAddress address = receiver.getAddress();
            if (cachedType instanceof PointerType && ((PointerType) cachedType).getPointeeType() instanceof PrimitiveType) {
                PrimitiveType primitiveType = (PrimitiveType) ((PointerType) cachedType).getPointeeType();
                return doPrimitiveRead(cachedIndex, address, primitiveType);
            }
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException();
        }

        private static Object doPrimitiveRead(int cachedIndex, LLVMAddress address, PrimitiveType primitiveType) {
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                    return LLVMMemory.getI1(address.increment(cachedIndex * I1_SIZE));
                case I8:
                    return LLVMMemory.getI8(address.increment(cachedIndex * I8_SIZE));
                case I16:
                    return LLVMMemory.getI16(address.increment(cachedIndex * I16_SIZE));
                case I32:
                    return LLVMMemory.getI32(address.increment(cachedIndex * I32_SIZE));
                case I64:
                    return LLVMMemory.getI64(address.increment(cachedIndex * I64_SIZE));
                case FLOAT:
                    return LLVMMemory.getFloat(address.increment(cachedIndex * FLOAT_SIZE));
                case DOUBLE:
                    return LLVMMemory.getDouble(address.increment(cachedIndex * DOUBLE_SIZE));
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException();
            }
        }

        @Specialization
        public Object doGlobal(LLVMGlobalVariableDescriptor receiver, int index) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            return prepareValueForEscape.executeWithTarget(receiver.load());
        }

    }

    abstract static class LLVMAddressWriteMessageResolutionNode extends LLVMAddressMessageResolutionNode {

        public abstract Object executeWithTarget(VirtualFrame frame, Object receiver, int index, Object value);

        @SuppressWarnings("unused")
        @Specialization(guards = {"index == cachedIndex", "typeGuard(receiver, cachedType)"})
        public Object doCachedTypeCachedOffset(LLVMTruffleAddress receiver, int index, Object value,
                        @Cached("getType(receiver)") Type cachedType,
                        @Cached("index") int cachedIndex,
                        @Cached("getToLLVMNode(cachedType)") ToLLVMNode toLLVM) {
            return prepareValueForEscape.executeWithTarget(doFastWrite(receiver, cachedType, cachedIndex, value, toLLVM));
        }

        @Specialization(guards = {"typeGuard(receiver, cachedType)"}, replaces = "doCachedTypeCachedOffset")
        public Object doCachedType(LLVMTruffleAddress receiver, int index, Object value,
                        @Cached("getType(receiver)") Type cachedType,
                        @Cached("getToLLVMNode(cachedType)") ToLLVMNode toLLVM) {
            return prepareValueForEscape.executeWithTarget(doFastWrite(receiver, cachedType, index, value, toLLVM));
        }

        @Child private ToLLVMNode slowConvert;

        @Specialization(replaces = {"doCachedTypeCachedOffset", "doCachedType"})
        public Object doRegular(LLVMTruffleAddress receiver, int index, Object value) {
            LLVMPerformance.warn(this);
            if (slowConvert == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.slowConvert = insert(ToLLVMNode.createNode(null));
            }
            return prepareValueForEscape.executeWithTarget(doSlowWrite(receiver, receiver.getType(), index, value, slowConvert));
        }

        private static Object doFastWrite(LLVMTruffleAddress receiver, Type cachedType, int index, Object value, ToLLVMNode toLLVM) {
            Object v = toLLVM.executeWithTarget(value);
            return doWrite(receiver, cachedType, index, v);
        }

        private static Object doSlowWrite(LLVMTruffleAddress receiver, Type cachedType, int index, Object value, ToLLVMNode toLLVM) {
            Object v = toLLVM.slowConvert(value, ToLLVMNode.convert(cachedType));
            return doWrite(receiver, cachedType, index, v);
        }

        private static Object doWrite(LLVMTruffleAddress receiver, Type cachedType, int index, Object v) {
            LLVMAddress address = receiver.getAddress();
            if (cachedType instanceof PointerType && ((PointerType) cachedType).getPointeeType() instanceof PrimitiveType) {
                PrimitiveType primitiveType = (PrimitiveType) ((PointerType) cachedType).getPointeeType();
                return doPrimitiveWrite(index, v, address, primitiveType);
            }
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException();
        }

        private static Object doPrimitiveWrite(int index, Object v, LLVMAddress address, PrimitiveType primitiveType) {
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                    LLVMMemory.putI1(address.increment(index * I1_SIZE), (boolean) v);
                    return v;
                case I8:
                    LLVMMemory.putI8(address.increment(index * I8_SIZE), (byte) v);
                    return v;
                case I16:
                    LLVMMemory.putI16(address.increment(index * I16_SIZE), (short) v);
                    return v;
                case I32:
                    LLVMMemory.putI32(address.increment(index * I32_SIZE), (int) v);
                    return v;
                case I64:
                    LLVMMemory.putI64(address.increment(index * I64_SIZE), (long) v);
                    return v;
                case FLOAT:
                    LLVMMemory.putFloat(address.increment(index * FLOAT_SIZE), (float) v);
                    return v;
                case DOUBLE:
                    LLVMMemory.putDouble(address.increment(index * DOUBLE_SIZE), (double) v);
                    return v;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException();
            }
        }

        @Specialization
        public Object doGlobal(LLVMGlobalVariableDescriptor receiver, int index, LLVMAddress value) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            receiver.storeLLVMAddress(value);
            return prepareValueForEscape.executeWithTarget(value);
        }

        @Specialization
        public Object doGlobal(LLVMGlobalVariableDescriptor receiver, int index, LLVMTruffleAddress value) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            receiver.storeLLVMAddress(value.getAddress());
            return value;
        }

        protected boolean notAnLLVMValue(TruffleObject value) {
            return LLVMExpressionNode.notLLVM(value);
        }

        @Specialization(guards = {"notAnLLVMValue(value)"})
        public Object doGlobal(LLVMGlobalVariableDescriptor receiver, int index, TruffleObject value) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            receiver.storeTruffleObject(value);
            return value;
        }
    }
}
