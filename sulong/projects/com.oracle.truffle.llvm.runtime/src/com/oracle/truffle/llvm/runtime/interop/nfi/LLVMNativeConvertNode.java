/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.nfi;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNodeFactory.I1FromNativeToLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNodeFactory.IdNodeGen;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNodeFactory.NativeToAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public abstract class LLVMNativeConvertNode extends LLVMNode {

    public abstract Object executeConvert(Object arg);

    public static LLVMNativeConvertNode createToNative(Type argType) {
        if (argType instanceof PointerType || argType instanceof FunctionType) {
            return new AddressToNative();
        } else if (argType instanceof VoidType) {
            return new VoidToNative();
        } else if (argType instanceof PrimitiveType) {
            if (((PrimitiveType) argType).getPrimitiveKind() == PrimitiveKind.I64) {
                // an I64 might also be a pointer
                return new AddressToNative();
            }
        }
        return IdNodeGen.create();
    }

    public static LLVMNativeConvertNode createFromNative(Type retType) {
        if (retType instanceof PointerType) {
            return NativeToAddressNodeGen.create();
        } else if (retType instanceof PrimitiveType && ((PrimitiveType) retType).getPrimitiveKind() == PrimitiveKind.I1) {
            return I1FromNativeToLLVMNodeGen.create();
        }
        return IdNodeGen.create();
    }

    protected static class VoidToNative extends LLVMNativeConvertNode {

        @Override
        public TruffleObject executeConvert(Object arg) {
            assert LLVMPointer.isInstance(arg) && LLVMPointer.cast(arg).isNull();
            return LLVMNativePointer.createNull();
        }
    }

    protected static class AddressToNative extends LLVMNativeConvertNode {

        @Child LLVMToNativeNode toNative = LLVMToNativeNode.createToNativeWithTarget();

        @Override
        public Object executeConvert(Object arg) {
            return toNative.executeWithTarget(arg).asNative();
        }
    }

    protected abstract static class NativeToAddress extends LLVMNativeConvertNode {

        @Specialization
        protected LLVMNativePointer doLong(long pointer) {
            return LLVMNativePointer.create(pointer);
        }

        @Specialization(guards = "interop.isPointer(address)", limit = "3", rewriteOn = UnsupportedMessageException.class)
        protected LLVMNativePointer doPointer(TruffleObject address,
                        @CachedLibrary("address") InteropLibrary interop) throws UnsupportedMessageException {
            return LLVMNativePointer.create(interop.asPointer(address));
        }

        @Specialization(guards = "!interop.isPointer(address)", limit = "3")
        @SuppressWarnings("unused")
        protected LLVMManagedPointer doFunction(TruffleObject address,
                        @CachedLibrary("address") InteropLibrary interop) {
            /*
             * If the NFI returns an object that's not a pointer, it's probably a callback function.
             * In that case, don't eagerly force TO_NATIVE. If we just call it immediately, we
             * shouldn't throw away the NFI signature just to re-construct it immediately.
             */
            return LLVMManagedPointer.create(address);
        }

        @Specialization(limit = "3", replaces = {"doPointer", "doFunction"})
        protected LLVMPointer doGeneric(TruffleObject address,
                        @CachedLibrary("address") InteropLibrary interop) {
            if (interop.isPointer(address)) {
                try {
                    return doPointer(address, interop);
                } catch (UnsupportedMessageException ex) {
                    // fallthrough
                }
            }
            return doFunction(address, interop);
        }
    }

    protected abstract static class Id extends LLVMNativeConvertNode {

        @Specialization
        protected Object doConvert(Object arg) {
            return arg;
        }
    }

    abstract static class I1FromNativeToLLVMNode extends LLVMNativeConvertNode {
        @Specialization
        protected Object convert(byte value) {
            return value != 0;
        }

        @Specialization
        protected Object convert(boolean value) {
            return value;
        }
    }
}
