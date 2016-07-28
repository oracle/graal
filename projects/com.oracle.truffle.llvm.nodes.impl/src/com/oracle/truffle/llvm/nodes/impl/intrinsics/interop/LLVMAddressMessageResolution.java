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
package com.oracle.truffle.llvm.nodes.impl.intrinsics.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMTruffleAddress;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

@MessageResolution(receiverType = LLVMTruffleAddress.class, language = LLVMLanguage.class)
public class LLVMAddressMessageResolution {
    private static final int I1_SIZE = 1;
    private static final int I8_SIZE = 1;
    private static final int I16_SIZE = 2;
    private static final int I32_SIZE = 4;
    private static final int I64_SIZE = 8;
    private static final int FLOAT_SIZE = 4;
    private static final int DOUBLE_SIZE = 8;

    @Resolve(message = "READ")
    public abstract static class ForeignRead extends Node {
        protected Object access(@SuppressWarnings("unused") VirtualFrame frame, LLVMTruffleAddress receiver, int index) {
            LLVMAddress address = receiver.getAddress();
            switch (receiver.getType()) {
                case I1_POINTER:
                    return LLVMMemory.getI1(address.increment(index * I1_SIZE));
                case I8_POINTER:
                    return LLVMMemory.getI8(address.increment(index * I8_SIZE));
                case I16_POINTER:
                    return LLVMMemory.getI16(address.increment(index * I16_SIZE));
                case I32_POINTER:
                    return LLVMMemory.getI32(address.increment(index * I32_SIZE));
                case I64_POINTER:
                    return LLVMMemory.getI64(address.increment(index * I64_SIZE));
                case FLOAT_POINTER:
                    return LLVMMemory.getFloat(address.increment(index * FLOAT_SIZE));
                case DOUBLE_POINTER:
                    return LLVMMemory.getDouble(address.increment(index * DOUBLE_SIZE));
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    @Resolve(message = "WRITE")
    public abstract static class ForeignWrite extends Node {
        protected Object access(@SuppressWarnings("unused") VirtualFrame frame, LLVMTruffleAddress receiver, int index, boolean value) {
            LLVMAddress address = receiver.getAddress();
            switch (receiver.getType()) {
                case I1_POINTER:
                    LLVMMemory.putI1(address.increment(index * I1_SIZE), value);
                    break;
                case I8_POINTER:
                    LLVMMemory.putI8(address.increment(index * I8_SIZE), (byte) (value ? 1 : 0));
                    break;
                case I16_POINTER:
                    LLVMMemory.putI16(address.increment(index * I16_SIZE), (short) (value ? 1 : 0));
                    break;
                case I32_POINTER:
                    LLVMMemory.putI32(address.increment(index * I32_SIZE), value ? 1 : 0);
                    break;
                case I64_POINTER:
                    LLVMMemory.putI64(address.increment(index * I64_SIZE), value ? 1 : 0);
                    break;
                case FLOAT_POINTER:
                    LLVMMemory.putFloat(address.increment(index * FLOAT_SIZE), value ? 1 : 0);
                    break;
                case DOUBLE_POINTER:
                    LLVMMemory.putDouble(address.increment(index * DOUBLE_SIZE), value ? 1 : 0);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            return value;
        }

        protected Object access(@SuppressWarnings("unused") VirtualFrame frame, LLVMTruffleAddress receiver, int index, Number value) {
            LLVMAddress address = receiver.getAddress();
            switch (receiver.getType()) {
                case I1_POINTER:
                    LLVMMemory.putI1(address.increment(index * I1_SIZE), value.intValue() != 0);
                    return value.intValue() != 0;
                case I8_POINTER:
                    LLVMMemory.putI8(address.increment(index * I8_SIZE), value.byteValue());
                    return value.byteValue();
                case I16_POINTER:
                    LLVMMemory.putI16(address.increment(index * I16_SIZE), value.shortValue());
                    return value.shortValue();
                case I32_POINTER:
                    LLVMMemory.putI32(address.increment(index * I32_SIZE), value.intValue());
                    return value.intValue();
                case I64_POINTER:
                    LLVMMemory.putI64(address.increment(index * I64_SIZE), value.longValue());
                    return value.longValue();
                case FLOAT_POINTER:
                    LLVMMemory.putFloat(address.increment(index * FLOAT_SIZE), value.floatValue());
                    return value.floatValue();
                case DOUBLE_POINTER:
                    LLVMMemory.putDouble(address.increment(index * DOUBLE_SIZE), value.doubleValue());
                    return value.doubleValue();
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }
}
