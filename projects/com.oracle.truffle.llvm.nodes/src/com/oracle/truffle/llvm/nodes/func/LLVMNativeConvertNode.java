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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.AddressToNativeNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.FunctionToNativeNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.IdNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.NativeToAddressNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMTruffleNull;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions.NullPointerNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

abstract class LLVMNativeConvertNode extends LLVMNode {

    public abstract Object executeConvert(VirtualFrame frame, Object arg);

    static LLVMNativeConvertNode createToNative(Type argType) {
        if (Type.isFunctionOrFunctionPointer(argType)) {
            return FunctionToNativeNodeGen.create();
        } else if (argType instanceof PointerType) {
            return AddressToNativeNodeGen.create();
        }
        return IdNodeGen.create();
    }

    static LLVMNativeConvertNode createFromNative(Type retType) {
        if (retType instanceof PointerType) {
            return NativeToAddressNodeGen.create();
        }
        return IdNodeGen.create();
    }

    protected abstract static class AddressToNative extends LLVMNativeConvertNode {

        @Specialization
        long addressToNative(LLVMAddress address) {
            return address.getVal();
        }

        @Specialization
        long addressToNative(@SuppressWarnings("unused") LLVMTruffleNull address) {
            return 0;
        }

        @Specialization
        long addressToNative(LLVMGlobalVariable address) {
            return address.getNativeLocation().getVal();
        }

        @Specialization
        long llvmTruffleObjectToNative(LLVMTruffleObject truffleObject) {
            return truffleObject.getOffset() + addressToNative(truffleObject.getObject());
        }

        @Child private Node unbox = Message.UNBOX.createNode();

        @Specialization
        long addressToNative(TruffleObject address) {
            try {
                return (long) ForeignAccess.sendUnbox(unbox, address);
            } catch (UnsupportedMessageException | ClassCastException e) {
                CompilerDirectives.transferToInterpreter();
                UnsupportedTypeException.raise(new Object[]{address});
                return 0;
            }
        }
    }

    protected abstract static class NativeToAddress extends LLVMNativeConvertNode {

        @Specialization
        LLVMAddress nativeToAddress(long pointer) {
            return LLVMAddress.fromLong(pointer);
        }

        @Child private Node unbox = Message.UNBOX.createNode();

        @Specialization
        LLVMAddress nativeToAddress(TruffleObject pointer) {
            try {
                return LLVMAddress.fromLong((long) ForeignAccess.sendUnbox(unbox, pointer));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    @SuppressWarnings("unused")
    protected abstract static class FunctionToNative extends LLVMNativeConvertNode {

        // not null pointer

        @Specialization(limit = "10", guards = {"function.getFunctionIndex() == cachedFunction.getFunctionIndex()", "cachedFunction.getFunctionIndex() != 0", "cachedFunction.getCallTarget() == null"})
        protected static TruffleObject doDirect(LLVMFunctionDescriptor function,
                        @Cached("function") LLVMFunctionDescriptor cachedFunction,
                        @Cached("resolveAsNative(cachedFunction)") TruffleObject cachedNative) {
            return cachedNative;
        }

        @Specialization(replaces = "doDirect", guards = {"descriptor.getCallTarget() == null", "descriptor.getFunctionIndex() != 0"})
        protected TruffleObject doIndirect(LLVMFunctionDescriptor descriptor) {
            return resolveAsNative(descriptor);
        }

        // null pointer

        @Specialization(guards = {"descriptor.getFunctionIndex() == 0"})
        protected TruffleObject doNull(LLVMFunctionDescriptor descriptor, @Cached("nullPointer()") TruffleObject np) {
            return np;
        }

        // not null pointer

        @Specialization(limit = "10", guards = {"handle.getFunctionIndex() == cachedHandle.getFunctionIndex()", "cachedHandle.getFunctionIndex() != 0"})
        protected static TruffleObject doDirectHandle(LLVMFunctionHandle handle,
                        @Cached("handle") LLVMFunctionHandle cachedHandle,
                        @Cached("resolveAsNative(doLookup(cachedHandle))") TruffleObject cachedNative) {
            return cachedNative;
        }

        @Specialization(replaces = "doDirect", guards = "handle.getFunctionIndex() != 0")
        protected TruffleObject doIndirectHandle(LLVMFunctionHandle handle) {
            return resolveAsNative(doLookup(handle));
        }

        // null pointer

        @Specialization(guards = {"descriptor.getFunctionIndex() == 0"})
        protected TruffleObject doNull(LLVMFunctionHandle descriptor, @Cached("nullPointer()") TruffleObject np) {
            return np;
        }

        @Specialization(limit = "10", guards = {"descriptor.getFunctionIndex() == cachedDescriptor.getFunctionIndex()", "descriptor.getCallTarget() != null",
                        "cachedDescriptor.getFunctionIndex() != 0"})
        protected static TruffleObject doCachedNative(LLVMFunctionDescriptor descriptor,
                        @Cached("descriptor") LLVMFunctionDescriptor cachedDescriptor) {
            return cachedDescriptor;
        }

        @Specialization(replaces = "doCachedNative", guards = {"descriptor.getCallTarget() != null", "descriptor.getFunctionIndex() != 0"})
        protected TruffleObject doCachedNative(LLVMFunctionDescriptor descriptor) {
            return descriptor;

        }

        protected TruffleObject resolveAsNative(LLVMFunctionDescriptor descriptor) {
            return getContext().resolveAsNativeFunction(descriptor);
        }

        protected LLVMFunctionDescriptor doLookup(LLVMFunctionHandle handle) {
            return getContext().lookup(handle);
        }

        @Child private NullPointerNode nullPointer;

        protected TruffleObject nullPointer() {
            if (nullPointer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nullPointer = insert(getContext().getNativeFunctions().createNullPointerNode());
            }
            return nullPointer.getNullPointer();
        }
    }

    protected abstract static class Id extends LLVMNativeConvertNode {

        @Specialization
        public Object executeConvert(Object arg) {
            return arg;
        }

    }
}
