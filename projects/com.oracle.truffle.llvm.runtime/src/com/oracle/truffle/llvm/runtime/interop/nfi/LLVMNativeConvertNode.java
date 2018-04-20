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
package com.oracle.truffle.llvm.runtime.interop.nfi;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMNativeFunctions.NullPointerNode;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNodeFactory.AddressToNativeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNodeFactory.FunctionToNativeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNodeFactory.I1FromNativeToLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNodeFactory.IdNodeGen;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNodeFactory.NativeToAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public abstract class LLVMNativeConvertNode extends LLVMNode {

    public abstract Object executeConvert(Object arg);

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
        } else if (argType instanceof VoidType) {
            return new VoidToNative();
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
            assert arg == null;
            return LLVMAddress.nullPointer();
        }
    }

    protected abstract static class AddressToNative extends LLVMNativeConvertNode {

        @Specialization
        protected long addressToNative(LLVMAddress address) {
            return address.getVal();
        }

        @Specialization
        protected long addressToNative(LLVMGlobal address,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
            return globalAccess.executeWithTarget(address).getVal();
        }

        @Specialization
        protected long doLLVMTruffleObject(LLVMTruffleObject truffleObject,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
            return toNative.executeWithTarget(truffleObject).getVal();
        }
    }

    protected abstract static class NativeToAddress extends LLVMNativeConvertNode {

        @Specialization
        protected LLVMAddress nativeToAddress(long pointer) {
            return LLVMAddress.fromLong(pointer);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "checkIsPointer(isPointer, address)")
        protected LLVMAddress addressToNative(TruffleObject address,
                        @Cached("createIsPointer()") Node isPointer,
                        @Cached("createAsPointer()") Node asPointer) {
            try {
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, address));
            } catch (UnsupportedMessageException | ClassCastException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(new Object[]{address});
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!checkIsPointer(isPointer, address)"})
        protected LLVMAddress addressToNative(TruffleObject address,
                        @Cached("createIsPointer()") Node isPointer,
                        @Cached("createToNative()") Node toNative,
                        @Cached("createAsPointer()") Node asPointer) {
            try {
                TruffleObject n = (TruffleObject) ForeignAccess.sendToNative(toNative, address);
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, n));
            } catch (UnsupportedMessageException | ClassCastException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(new Object[]{address});
            }
        }
    }

    @SuppressWarnings("unused")
    protected abstract static class FunctionToNative extends LLVMNativeConvertNode {

        // null pointer

        @Specialization(guards = {"descriptor.isNullFunction()"})
        protected TruffleObject doNull(LLVMFunctionDescriptor descriptor,
                        @Cached("nullPointer()") TruffleObject np) {
            return np;
        }

        @Specialization(guards = {"descriptor.getVal() == 0"})
        protected TruffleObject doNull(LLVMAddress descriptor,
                        @Cached("nullPointer()") TruffleObject np) {
            return np;
        }

        // not null pointer

        @Specialization(limit = "10", guards = {"function == cachedFunction", "!cachedFunction.isNullFunction()", "cachedFunction.isNativeFunction()"})
        protected static TruffleObject doDirectNative(LLVMFunctionDescriptor function,
                        @Cached("function") LLVMFunctionDescriptor cachedFunction,
                        @Cached("cachedFunction.getNativeFunction()") TruffleObject cachedNative) {
            return cachedNative;
        }

        @Specialization(replaces = "doDirectNative", guards = {"descriptor.isNativeFunction()", "!descriptor.isNullFunction()"})
        protected TruffleObject doIndirectNative(LLVMFunctionDescriptor descriptor) {
            return descriptor.getNativeFunction();
        }

        @Specialization(guards = {"function == cachedFunction", "!cachedFunction.isNullFunction()", "!cachedFunction.isNativeFunction()"})
        protected static TruffleObject doCachedDescriptor(LLVMFunctionDescriptor function,
                        @Cached("function") LLVMFunctionDescriptor cachedFunction,
                        @Cached("doDescriptor(cachedFunction)") TruffleObject ret) {
            return ret;
        }

        @Specialization(replaces = "doCachedDescriptor", guards = {"!function.isNullFunction()", "!function.isNativeFunction()"})
        protected static TruffleObject doDescriptor(LLVMFunctionDescriptor function) {
            return new LLVMNativeWrapper(function);
        }

        @Specialization(limit = "10", guards = {"descriptor != null", "descriptor.isNativeFunction()", "handle.getVal() == cachedHandle.getVal()", "!descriptor.isNullFunction()"})
        protected static TruffleObject doCachedHandleNative(LLVMAddress handle,
                        @Cached("handle") LLVMAddress cachedHandle,
                        @Cached("doLookup(handle)") LLVMFunctionDescriptor descriptor) {
            return descriptor.getNativeFunction();
        }

        @Specialization(limit = "10", guards = {"descriptor != null", "!descriptor.isNativeFunction()", "handle.getVal() == cachedHandle.getVal()", "!descriptor.isNullFunction()"})
        protected static TruffleObject doCachedHandle(LLVMAddress handle,
                        @Cached("handle") LLVMAddress cachedHandle,
                        @Cached("doLookup(handle)") LLVMFunctionDescriptor descriptor,
                        @Cached("doDescriptor(descriptor)") TruffleObject ret) {
            return ret;
        }

        @Specialization(limit = "10", guards = {"descriptor == null", "handle.getVal() == cachedHandle.getVal()"})
        protected static TruffleObject doCachedNative(LLVMAddress handle,
                        @Cached("handle") LLVMAddress cachedHandle,
                        @Cached("doLookup(cachedHandle)") LLVMFunctionDescriptor descriptor,
                        @Cached("getContextReference()") ContextReference<LLVMContext> c) {
            return handle;
        }

        @Specialization(replaces = {"doCachedHandleNative", "doCachedHandle", "doCachedNative"}, guards = {"handle.getVal() != 0"})
        protected TruffleObject doUncachedHandle(LLVMAddress handle) {
            LLVMFunctionDescriptor descriptor = doLookup(handle);
            if (descriptor == null) {
                return handle;
            } else if (descriptor.isNativeFunction()) {
                return descriptor.getNativeFunction();
            } else {
                return doDescriptor(descriptor);
            }
        }

        protected LLVMFunctionDescriptor doLookup(LLVMAddress handle) {
            return getContextReference().get().getFunctionDescriptor(handle);
        }

        @Child private NullPointerNode nullPointer;

        protected TruffleObject nullPointer() {
            if (nullPointer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                LLVMContext context = getContextReference().get();
                NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
                nullPointer = insert(nfiContextExtension.getNativeSulongFunctions().createNullPointerNode(context));
            }
            return nullPointer.getNullPointer();
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
