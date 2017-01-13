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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;

abstract class LLVMAddressMessageResolutionNode extends Node {
    private static final int I1_SIZE = 1;
    private static final int I8_SIZE = 1;
    private static final int I16_SIZE = 2;
    private static final int I32_SIZE = 4;
    private static final int I64_SIZE = 8;
    private static final int FLOAT_SIZE = 4;
    private static final int DOUBLE_SIZE = 8;

    public LLVMRuntimeType getType(LLVMTruffleAddress receiver) {
        return receiver.getType();
    }

    public boolean typeGuard(LLVMTruffleAddress receiver, LLVMRuntimeType type) {
        return receiver.getType() == type;
    }

    public ToLLVMNode getToLLVMNode(LLVMRuntimeType type) {
        return ToLLVMNode.createNode(ToLLVMNode.convert(type));
    }

    abstract static class LLVMAddressReadMessageResolutionNode extends LLVMAddressMessageResolutionNode {

        public abstract Object executeWithTarget(VirtualFrame frame, LLVMTruffleAddress receiver, int index);

        @SuppressWarnings("unused")
        @Specialization(guards = {"index == cachedIndex", "typeGuard(receiver, cachedType)"})
        public static Object doCachedTypeCachedOffset(LLVMTruffleAddress receiver, int index,
                        @Cached("getType(receiver)") LLVMRuntimeType cachedType,
                        @Cached("index") int cachedIndex) {
            return doRead(receiver, cachedType, cachedIndex);
        }

        @Specialization(guards = {"typeGuard(receiver, cachedType)"}, contains = "doCachedTypeCachedOffset")
        public static Object doCachedType(LLVMTruffleAddress receiver, int index,
                        @Cached("getType(receiver)") LLVMRuntimeType cachedType) {
            return doRead(receiver, cachedType, index);
        }

        @Specialization(contains = {"doCachedTypeCachedOffset", "doCachedType"})
        public static Object doRegular(LLVMTruffleAddress receiver, int index) {
            return doRead(receiver, receiver.getType(), index);
        }

        private static Object doRead(LLVMTruffleAddress receiver, LLVMRuntimeType cachedType, int cachedIndex) {
            LLVMAddress address = receiver.getAddress();
            switch (cachedType) {
                case I1_POINTER:
                    return LLVMMemory.getI1(address.increment(cachedIndex * I1_SIZE));
                case I8_POINTER:
                    return LLVMMemory.getI8(address.increment(cachedIndex * I8_SIZE));
                case I16_POINTER:
                    return LLVMMemory.getI16(address.increment(cachedIndex * I16_SIZE));
                case I32_POINTER:
                    return LLVMMemory.getI32(address.increment(cachedIndex * I32_SIZE));
                case I64_POINTER:
                    return LLVMMemory.getI64(address.increment(cachedIndex * I64_SIZE));
                case FLOAT_POINTER:
                    return LLVMMemory.getFloat(address.increment(cachedIndex * FLOAT_SIZE));
                case DOUBLE_POINTER:
                    return LLVMMemory.getDouble(address.increment(cachedIndex * DOUBLE_SIZE));
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException();
            }
        }

    }

    abstract static class LLVMAddressWriteMessageResolutionNode extends LLVMAddressMessageResolutionNode {

        public abstract Object executeWithTarget(VirtualFrame frame, LLVMTruffleAddress receiver, int index, Object value);

        @SuppressWarnings("unused")
        @Specialization(guards = {"index == cachedIndex", "typeGuard(receiver, cachedType)"})
        public static Object doCachedTypeCachedOffset(VirtualFrame frame, LLVMTruffleAddress receiver, int index, Object value,
                        @Cached("getType(receiver)") LLVMRuntimeType cachedType,
                        @Cached("index") int cachedIndex,
                        @Cached("getToLLVMNode(cachedType)") ToLLVMNode toLLVM) {
            return doFastWrite(frame, receiver, cachedType, cachedIndex, value, toLLVM);
        }

        @Specialization(guards = {"typeGuard(receiver, cachedType)"}, contains = "doCachedTypeCachedOffset")
        public static Object doCachedType(VirtualFrame frame, LLVMTruffleAddress receiver, int index, Object value,
                        @Cached("getType(receiver)") LLVMRuntimeType cachedType,
                        @Cached("getToLLVMNode(cachedType)") ToLLVMNode toLLVM) {
            return doFastWrite(frame, receiver, cachedType, index, value, toLLVM);
        }

        @Child private ToLLVMNode slowConvert;

        @Specialization(contains = {"doCachedTypeCachedOffset", "doCachedType"})
        public Object doRegular(VirtualFrame frame, LLVMTruffleAddress receiver, int index, Object value) {
            if (slowConvert == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.slowConvert = insert(ToLLVMNode.createNode(null));
            }
            return doSlowWrite(frame, receiver, receiver.getType(), index, value, slowConvert);
        }

        private static Object doFastWrite(VirtualFrame frame, LLVMTruffleAddress receiver, LLVMRuntimeType cachedType, int index, Object value, ToLLVMNode toLLVM) {
            Object v = toLLVM.executeWithTarget(frame, value);
            return doWrite(receiver, cachedType, index, v);
        }

        private static Object doSlowWrite(VirtualFrame frame, LLVMTruffleAddress receiver, LLVMRuntimeType cachedType, int index, Object value, ToLLVMNode toLLVM) {
            Object v = toLLVM.slowConvert(frame, value, ToLLVMNode.convert(cachedType));
            return doWrite(receiver, cachedType, index, v);
        }

        private static Object doWrite(LLVMTruffleAddress receiver, LLVMRuntimeType cachedType, int index, Object v) {
            LLVMAddress address = receiver.getAddress();
            switch (cachedType) {
                case I1_POINTER:
                    LLVMMemory.putI1(address.increment(index * I1_SIZE), (boolean) v);
                    return v;
                case I8_POINTER:
                    LLVMMemory.putI8(address.increment(index * I8_SIZE), (byte) v);
                    return v;
                case I16_POINTER:
                    LLVMMemory.putI16(address.increment(index * I16_SIZE), (short) v);
                    return v;
                case I32_POINTER:
                    LLVMMemory.putI32(address.increment(index * I32_SIZE), (int) v);
                    return v;
                case I64_POINTER:
                    LLVMMemory.putI64(address.increment(index * I64_SIZE), (long) v);
                    return v;
                case FLOAT_POINTER:
                    LLVMMemory.putFloat(address.increment(index * FLOAT_SIZE), (float) v);
                    return v;
                case DOUBLE_POINTER:
                    LLVMMemory.putDouble(address.increment(index * DOUBLE_SIZE), (double) v);
                    return v;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException();
            }
        }

    }
}
