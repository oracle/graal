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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMSulongFunctionToNativePointer extends LLVMIntrinsic {

    @TruffleBoundary
    protected LLVMFunctionDescriptor lookupFunction(LLVMFunctionHandle function) {
        return getContext().getFunctionDescriptor(function);
    }

    @TruffleBoundary
    protected LLVMFunctionDescriptor lookupFunction(LLVMAddress function) {
        return getContext().getFunctionDescriptor(LLVMFunctionHandle.createHandle(function.getVal()));
    }

    @TruffleBoundary
    protected TruffleObject identityFunction(LLVMAddress signature) {
        return getContext().getNativeLookup().getNativeFunction("@identity", String.format("(%s):POINTER", readString(signature)));
    }

    protected boolean isSulong(LLVMAddress address) {
        return LLVMFunction.isSulongFunctionPointer(address.getVal());
    }

    @Child private Node execute = Message.createExecute(1).createNode();
    @Child private Node asPointer = Message.AS_POINTER.createNode();

    @SuppressWarnings("unused")
    @Specialization(guards = {"isSulong(pointer)", "pointer.getVal() == cachedPointer.getVal()",
                    "signature.getVal() == cachedSignature.getVal()"})
    LLVMAddress bothCached(LLVMAddress pointer, LLVMAddress signature,
                    @Cached("pointer") LLVMAddress cachedPointer,
                    @Cached("signature") LLVMAddress cachedSignature,
                    @Cached("lookupFunction(pointer)") LLVMFunctionDescriptor descriptor,
                    @Cached("identityFunction(signature)") TruffleObject identity) {
        try {
            TruffleObject nativePointer = (TruffleObject) ForeignAccess.sendExecute(execute, identity,
                            descriptor);
            return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, nativePointer));
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isSulong(pointer)", "signature.getVal() == cachedSignature.getVal()"})
    LLVMAddress signatureCached(LLVMAddress pointer, LLVMAddress signature,
                    @Cached("signature") LLVMAddress cachedSignature,
                    @Cached("identityFunction(signature)") TruffleObject identity) {
        try {
            TruffleObject nativePointer = (TruffleObject) ForeignAccess.sendExecute(execute, identity,
                            lookupFunction(pointer));
            return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, nativePointer));
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"handle.isSulong()", "handle.getFunctionPointer() == cachedHandle.getFunctionPointer()",
                    "signature.getVal() == cachedSignature.getVal()"})
    LLVMAddress bothCached(LLVMFunctionHandle handle, LLVMAddress signature,
                    @Cached("handle") LLVMFunctionHandle cachedHandle,
                    @Cached("signature") LLVMAddress cachedSignature,
                    @Cached("lookupFunction(handle)") LLVMFunctionDescriptor descriptor,
                    @Cached("identityFunction(signature)") TruffleObject identity) {
        try {
            TruffleObject nativePointer = (TruffleObject) ForeignAccess.sendExecute(execute, identity,
                            descriptor);
            return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, nativePointer));
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"handle.isSulong()", "signature.getVal() == cachedSignature.getVal()"})
    LLVMAddress signatureCached(LLVMFunctionHandle handle, LLVMAddress signature,
                    @Cached("signature") LLVMAddress cachedSignature,
                    @Cached("identityFunction(signature)") TruffleObject identity) {
        try {
            TruffleObject nativePointer = (TruffleObject) ForeignAccess.sendExecute(execute, identity,
                            lookupFunction(handle));
            return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, nativePointer));
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @Specialization(guards = "handle.isSulong()")
    LLVMAddress generic(LLVMFunctionHandle handle, LLVMAddress signature) {
        try {
            TruffleObject nativePointer = (TruffleObject) ForeignAccess.sendExecute(execute,
                            identityFunction(signature), lookupFunction(handle));
            return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, nativePointer));
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "handle.isExternNative()")
    LLVMAddress extern(LLVMFunctionHandle handle, LLVMAddress signature) {
        return LLVMAddress.fromLong(handle.getFunctionPointer());
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "!isSulong(address)")
    LLVMAddress extern(LLVMAddress address, LLVMAddress signature) {
        return address;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"signature.getVal() == cachedSignature.getVal()"})
    LLVMAddress signatureCached(LLVMFunctionDescriptor handle, LLVMAddress signature,
                    @Cached("signature") LLVMAddress cachedSignature,
                    @Cached("identityFunction(signature)") TruffleObject identity) {
        try {
            TruffleObject nativePointer = (TruffleObject) ForeignAccess.sendExecute(execute, identity,
                            handle);
            return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, nativePointer));
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @Specialization
    LLVMAddress generic(LLVMFunctionDescriptor handle, LLVMAddress signature) {
        try {
            TruffleObject nativePointer = (TruffleObject) ForeignAccess.sendExecute(execute, identityFunction(signature), handle);
            return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, nativePointer));
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

}
