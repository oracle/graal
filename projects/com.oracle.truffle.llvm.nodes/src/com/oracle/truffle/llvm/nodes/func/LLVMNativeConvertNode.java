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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.context.LLVMContext;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.AddressToNativeNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.FunctionToNativeNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.NativeToAddressNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class LLVMNativeConvertNode extends Node {

    public abstract Object executeConvert(VirtualFrame frame, Object arg);

    public static LLVMNativeConvertNode createToNative(LLVMContext context, Type argType) {
        switch (argType.getLLVMType().getType()) {
            case ADDRESS:
                return AddressToNativeNodeGen.create();
            case FUNCTION_ADDRESS:
                return FunctionToNativeNodeGen.create(context);
            default:
                return new Id();
        }
    }

    public static LLVMNativeConvertNode createFromNative(LLVMRuntimeType retType) {
        switch (retType) {
            case ADDRESS:
                return NativeToAddressNodeGen.create();
            default:
                return new Id();
        }
    }

    protected abstract static class AddressToNative extends LLVMNativeConvertNode {

        @Specialization
        long addressToNative(LLVMAddress address) {
            return address.getVal();
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

        private final LLVMContext context;

        protected FunctionToNative(LLVMContext context) {
            this.context = context;
        }

        @Specialization(limit = "10", guards = {"function.getFunctionIndex() == cachedFunction.getFunctionIndex()", "cachedFunction.getCallTarget() == null"})
        protected static TruffleObject doDirect(LLVMFunctionDescriptor function,
                        @Cached("function") LLVMFunctionDescriptor cachedFunction,
                        @Cached("resolveAsNative(cachedFunction)") TruffleObject cachedNative) {
            return cachedNative;
        }

        @Specialization(replaces = "doDirect", guards = "descriptor.getCallTarget() == null")
        protected TruffleObject doIndirect(LLVMFunctionDescriptor descriptor) {
            return resolveAsNative(descriptor);
        }

        @Specialization(limit = "10", guards = {"handle.getFunctionIndex() == cachedHandle.getFunctionIndex()"})
        protected static TruffleObject doDirectHandle(LLVMFunctionHandle handle,
                        @Cached("handle") LLVMFunctionHandle cachedHandle,
                        @Cached("resolveAsNative(doLookup(cachedHandle))") TruffleObject cachedNative) {
            return cachedNative;
        }

        @Specialization(replaces = "doDirect")
        protected TruffleObject doIndirectHandle(LLVMFunctionHandle handle) {
            return resolveAsNative(doLookup(handle));
        }

        @Specialization(limit = "10", guards = {"descriptor.getFunctionIndex() == cachedDescriptor.getFunctionIndex()", "descriptor.getCallTarget() != null"})
        protected static TruffleObject doCachedNative(LLVMFunctionDescriptor descriptor,
                        @Cached("descriptor") LLVMFunctionDescriptor cachedDescriptor) {
            return cachedDescriptor;
        }

        @Specialization(replaces = "doCachedNative", guards = {"descriptor.getCallTarget() != null"})
        protected TruffleObject doCachedNative(LLVMFunctionDescriptor descriptor) {
            return descriptor;

        }

        protected TruffleObject resolveAsNative(LLVMFunctionDescriptor descriptor) {
            return context.resolveAsNativeFunction(descriptor);
        }

        protected LLVMFunctionDescriptor doLookup(LLVMFunctionHandle handle) {
            return context.getFunctionRegistry().lookup(handle);
        }
    }

    private static final class Id extends LLVMNativeConvertNode {

        @Override
        public Object executeConvert(VirtualFrame frame, Object arg) {
            return arg;
        }
    }
}
