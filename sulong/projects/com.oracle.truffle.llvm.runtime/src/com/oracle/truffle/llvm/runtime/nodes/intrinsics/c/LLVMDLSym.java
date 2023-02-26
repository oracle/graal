/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen.LLVMDLHandler;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadStringNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMDLSym extends LLVMIntrinsic {

    // Linux Mac
    // RTLD_NEXT ((void *) -1l) ((void *) -1)
    // RTLD_DEFAULT ((void *) 0) ((void *) -2)

    @Specialization(guards = "isLLVMLibrary(libraryHandle)", limit = "2")
    @GenerateAOT.Exclude
    protected Object doOp(LLVMManagedPointer libraryHandle,
                    LLVMPointer symbol,
                    @Cached() LLVMReadStringNode readStr,
                    @CachedLibrary("getLibrary(libraryHandle)") InteropLibrary interop,
                    @Cached WrappedFunctionNode wrapper) {
        try {
            String symbolName = readStr.executeWithTarget(symbol);
            Object function = interop.readMember(getLibrary(libraryHandle), symbolName);
            return wrapper.execute(function);
        } catch (InteropException e) {
            getContext().setDLError(2);
            return LLVMNativePointer.createNull();
        }
    }

    @Specialization(guards = "!(isLLVMLibrary(libraryHandle))")
    protected Object doOp(@SuppressWarnings("unused") LLVMManagedPointer libraryHandle,
                    @SuppressWarnings("unused") LLVMPointer symbol,
                    @SuppressWarnings("unused") @Cached() LLVMReadStringNode readStr) {
        return LLVMNativePointer.createNull();
    }

    @Specialization(guards = "isRtldDefault(libraryHandle)")
    protected Object doDefaultHandle(@SuppressWarnings("unused") LLVMNativePointer libraryHandle,
                    @SuppressWarnings("unused") LLVMPointer symbolName,
                    @SuppressWarnings("unused") @Cached() LLVMReadStringNode readStr,
                    @Cached WrappedFunctionNode wrapper,
                    @Cached BranchProfile exception) {
        LLVMContext ctx = LLVMContext.get(this);
        String name = readStr.executeWithTarget(symbolName);
        LLVMSymbol symbol = ctx.getGlobalScopeChain().get(name);
        if (symbol == null) {
            Object nativeSymbol = getNativeSymbol(name, ctx);
            if (nativeSymbol == null) {
                ctx.setDLError(2);
                return LLVMNativePointer.createNull();
            }
            return wrapper.execute(nativeSymbol);
        }
        return ctx.getSymbol(symbol, exception);
    }

    @TruffleBoundary
    protected Object getNativeSymbol(String name, LLVMContext context) {
        NativeContextExtension nativeContextExtension = context.getContextExtensionOrNull(NativeContextExtension.class);
        if (nativeContextExtension != null) {
            NativeLookupResult result = nativeContextExtension.getNativeFunctionOrNull(name);
            if (result != null) {
                return result.getObject();
            }
        }
        return null;
    }

    protected boolean isRtldDefault(LLVMNativePointer libraryHandle) {
        PlatformCapability<?> sysContextExt = LLVMLanguage.get(null).getCapability(PlatformCapability.class);
        return sysContextExt.isDefaultDLSymFlagSet(libraryHandle.asNative());
    }

    protected Object getLibrary(LLVMManagedPointer pointer) {
        return ((LLVMDLHandler) pointer.getObject()).getLibrary();
    }

    protected boolean isLLVMLibrary(LLVMManagedPointer library) {
        return library.getObject() instanceof LLVMDLHandler;
    }

    abstract static class WrappedFunctionNode extends LLVMNode {

        abstract LLVMPointer execute(Object function);

        @Specialization
        protected LLVMManagedPointer doFunctionDescriptor(LLVMFunctionDescriptor function) {
            return LLVMManagedPointer.create(function);
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"!isFunctionDescriptor(symbol)", "interopLibrary.isPointer(symbol)"}, limit = "1")
        protected LLVMNativePointer doNFISymbol(Object symbol,
                        @CachedLibrary("symbol") InteropLibrary interopLibrary) {
            try {
                return LLVMNativePointer.create(interopLibrary.asPointer(symbol));
            } catch (InteropException e) {
                getContext().setDLError(2);
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        protected static boolean isFunctionDescriptor(Object function) {
            return function instanceof LLVMFunctionDescriptor;
        }

    }

}
