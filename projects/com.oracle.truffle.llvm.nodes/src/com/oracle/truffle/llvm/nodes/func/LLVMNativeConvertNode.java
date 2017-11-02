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
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.AddressToNativeNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.FunctionToNativeNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.I1FromNativeToLLVMNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.IdNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeConvertNodeFactory.NativeToAddressNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMForceLLVMAddressNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMForceLLVMAddressNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMNativeFunctions.NullPointerNode;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class LLVMNativeConvertNode extends LLVMNode {

    public abstract Object executeConvert(VirtualFrame frame, Object arg);

    protected static boolean checkIsPointer(Node isPointer, TruffleObject object) {
        return ForeignAccess.sendIsPointer(isPointer, object);
    }

    protected static Node createIsPointer() {
        return Message.IS_POINTER.createNode();
    }

    protected static Node createAsPointer() {
        return Message.AS_POINTER.createNode();
    }

    protected static Node createToNative() {
        return Message.TO_NATIVE.createNode();
    }

    public static LLVMNativeConvertNode createToNative(Type argType) {
        if (Type.isFunctionOrFunctionPointer(argType)) {
            return FunctionToNativeNodeGen.create();
        } else if (argType instanceof PointerType) {
            return AddressToNativeNodeGen.create();
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

    protected abstract static class AddressToNative extends LLVMNativeConvertNode {

        @Specialization
        long addressToNative(LLVMAddress address) {
            return address.getVal();
        }

        @Specialization
        long addressToNative(LLVMGlobalVariable address, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return globalAccess.getNativeLocation(address).getVal();
        }

        protected LLVMForceLLVMAddressNode createLLVMForceLLVMAddressNode() {
            return LLVMForceLLVMAddressNodeGen.create();
        }

        @Specialization
        long doLLVMTruffleObject(VirtualFrame frame, LLVMTruffleObject truffleObject, @Cached("createLLVMForceLLVMAddressNode()") LLVMForceLLVMAddressNode toNative) {
            return truffleObject.getOffset() + toNative.executeWithTarget(frame, truffleObject).getVal();
        }

    }

    protected abstract static class NativeToAddress extends LLVMNativeConvertNode {

        @Specialization
        LLVMAddress nativeToAddress(long pointer) {
            return LLVMAddress.fromLong(pointer);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "checkIsPointer(isPointer, address)")
        LLVMAddress addressToNative(TruffleObject address, @Cached("createIsPointer()") Node isPointer, @Cached("createAsPointer()") Node asPointer) {
            try {
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, address));
            } catch (UnsupportedMessageException | ClassCastException e) {
                CompilerDirectives.transferToInterpreter();
                UnsupportedTypeException.raise(new Object[]{address});
                return LLVMAddress.nullPointer();
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!checkIsPointer(isPointer, address)"})
        LLVMAddress addressToNative(TruffleObject address, @Cached("createIsPointer()") Node isPointer, @Cached("createToNative()") Node toNative,
                        @Cached("createAsPointer()") Node asPointer) {
            try {
                TruffleObject n = (TruffleObject) ForeignAccess.sendToNative(toNative, address);
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, n));
            } catch (UnsupportedMessageException | ClassCastException e) {
                CompilerDirectives.transferToInterpreter();
                UnsupportedTypeException.raise(new Object[]{address});
                return LLVMAddress.nullPointer();
            }
        }
    }

    @SuppressWarnings("unused")
    protected abstract static class FunctionToNative extends LLVMNativeConvertNode {

        // null pointer

        @Specialization(guards = {"descriptor.isNullFunction()"})
        protected TruffleObject doNull(LLVMFunctionDescriptor descriptor, @Cached("nullPointer()") TruffleObject np) {
            return np;
        }

        @Specialization(guards = {"descriptor.isNullFunction()"})
        protected TruffleObject doNull(LLVMFunctionHandle descriptor, @Cached("nullPointer()") TruffleObject np) {
            return np;
        }

        // not null pointer

        @Specialization(limit = "10", guards = {"function.getFunctionPointer() == cachedFunction.getFunctionPointer()", "!cachedFunction.isNullFunction()", "cachedFunction.isNativeFunction()"})
        protected static TruffleObject doDirectNative(LLVMFunctionDescriptor function,
                        @Cached("function") LLVMFunctionDescriptor cachedFunction,
                        @Cached("cachedFunction.getNativeFunction()") TruffleObject cachedNative) {
            return cachedNative;
        }

        @Specialization(replaces = "doDirectNative", guards = {"descriptor.isNativeFunction()", "!descriptor.isNullFunction()"})
        protected TruffleObject doIndirectNative(LLVMFunctionDescriptor descriptor) {
            return descriptor.getNativeFunction();
        }

        @Specialization(guards = {"!function.isNullFunction()", "!function.isNativeFunction()"})
        protected static TruffleObject doDescriptor(LLVMFunctionDescriptor function) {
            return function;
        }

        @Specialization(limit = "10", guards = {"descriptor != null", "handle.getFunctionPointer() == descriptor.getFunctionPointer()", "!descriptor.isNullFunction()"})
        protected static TruffleObject doCachedHandle(LLVMFunctionHandle handle,
                        @Cached("doLookup(handle)") LLVMFunctionDescriptor descriptor) {
            if (descriptor.isNativeFunction()) {
                return descriptor.getNativeFunction();
            } else {
                return descriptor;
            }
        }

        @Specialization(limit = "10", guards = {"descriptor == null", "handle.getFunctionPointer() == cachedHandle.getFunctionPointer()"})
        protected static TruffleObject doCachedNative(LLVMFunctionHandle handle,
                        @Cached("handle") LLVMFunctionHandle cachedHandle,
                        @Cached("doLookup(cachedHandle)") LLVMFunctionDescriptor descriptor,
                        @Cached("getContext()") LLVMContext c) {
            return new LLVMTruffleAddress(LLVMAddress.fromLong(handle.getFunctionPointer()), new PointerType(null), c);
        }

        @Specialization(replaces = {"doCachedHandle", "doCachedNative"}, guards = {"!handle.isNullFunction()"})
        protected TruffleObject doUncachedHandle(LLVMFunctionHandle handle, @Cached("getContext()") LLVMContext c) {
            LLVMFunctionDescriptor descriptor = doLookup(handle);
            if (descriptor == null) {
                return new LLVMTruffleAddress(LLVMAddress.fromLong(handle.getFunctionPointer()), new PointerType(null), c);
            } else if (descriptor.isNativeFunction()) {
                return descriptor.getNativeFunction();
            } else {
                return descriptor;
            }
        }

        protected LLVMFunctionDescriptor doLookup(LLVMFunctionHandle handle) {
            return getContext().getFunctionDescriptor(handle);
        }

        @Child private NullPointerNode nullPointer;

        protected TruffleObject nullPointer() {
            if (nullPointer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                LLVMContext context = getContext();
                NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
                nullPointer = insert(nfiContextExtension.getNativeSulongFunctions().createNullPointerNode(context));
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

    abstract static class I1FromNativeToLLVMNode extends LLVMNativeConvertNode {
        @Specialization
        public Object convert(byte value) {
            return value != 0;
        }

        @Specialization
        public Object convert(boolean value) {
            return value;
        }
    }
}
