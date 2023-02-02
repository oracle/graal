/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMScopeChain;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMThreadLocalSymbol;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.NativePointerIntoLibrary;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * External symbols are resolved first when the symbol exists in the local scope, and then if the
 * symbol exists in the global scope,
 *
 * A global symbol can be resolved to a native global symbol.
 *
 * Allocating external functions have four cases: 1) If the function is defined in the local scope.
 * 2) If the function is defined in the global scope. 3) if the function is an intrinsic function.
 * 4) And finally, if the function is a native function.
 *
 * For overriding defined functions for symbol resolution in {@link InitializeOverwriteNode}, it is
 * possible to overwrite global symbols as they can be taken from the global and local scope,
 * meanwhile functions can only be overwritten from the local scopes.
 */
public class AllocExternalSymbolNode extends LLVMNode {

    private final NodeFactory nodeFactory;

    public AllocExternalSymbolNode(LLVMParserResult result) {
        this.nodeFactory = result.getRuntime().getNodeFactory();
    }

    public LLVMPointer execute(LLVMScopeChain localScope, LLVMScopeChain globalScope, NativeContextExtension nativeContextExtension,
                    LLVMContext context, LLVMDLOpen.RTLDFlags rtldFlags, LLVMGlobal global) {

        LLVMPointer fromScope = getFromScope(localScope, globalScope, context, rtldFlags, global);
        if (fromScope != null) {
            return fromScope;
        }

        // Allocating a native global symbol to the symbol table as provided by the nfi context.
        if (global.isExternalWeak()) {
            return LLVMNativePointer.createNull();
        } else if (nativeContextExtension != null) {
            NativeContextExtension.NativePointerIntoLibrary pointer = getNativePointer(nativeContextExtension, global);
            if (pointer != null) {
                return LLVMNativePointer.create(pointer.getAddress());
            }
        }
        return null;
    }

    public LLVMPointer execute(LLVMScopeChain localScope, LLVMScopeChain globalScope, LLVMIntrinsicProvider intrinsicProvider, NativeContextExtension nativeContextExtension,
                    LLVMContext context, LLVMDLOpen.RTLDFlags rtldFlags, LLVMFunction function) {

        LLVMPointer fromScope = getFromScope(localScope, globalScope, context, rtldFlags, function);
        if (fromScope != null) {
            return fromScope;
        }

        // Allocates a managed pointer for the newly constructed function descriptors of a
        // native function and intrinsic function.
        if (function.isExternalWeak()) {
            return LLVMNativePointer.createNull();
        } else if (intrinsicProvider != null && intrinsicProvider.isIntrinsified(function.getName())) {
            LLVMFunctionCode functionCode = new LLVMFunctionCode(function);
            LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(function, functionCode);
            functionDescriptor.getFunctionCode().define(intrinsicProvider, nodeFactory);
            return LLVMManagedPointer.create(functionDescriptor);
        } else if (intrinsicProvider != null && !intrinsicProvider.isIntrinsified(function.getName()) && nativeContextExtension != null) {
            /*
             * Currently native functions/globals that are not in the nfi context are not written
             * into the symbol table. For function, another lookup will happen when something tries
             * to call the function. (see {@link LLVMDispatchNode#doCachedNative}) The function will
             * be taken from the filescope directly. Ideally the filescope and symbol table is in
             * sync, and any lazy look up will resolve from the function code in the symbol table.
             */
            NativeLookupResult nativeFunction = getNativeFunction(nativeContextExtension, function);
            if (nativeFunction != null) {
                LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(function, new LLVMFunctionCode(function));
                functionDescriptor.getFunctionCode().define(new LLVMFunctionCode.NativeFunction(nativeFunction.getObject()));
                function.setNFISymbol(nativeFunction.getObject());
                return LLVMManagedPointer.create(functionDescriptor);
            }
        }

        /*
         * Fallback for when the same symbol is being overwritten. There exists code where the
         * symbol is not there.
         */
        return null;
    }

    public LLVMPointer execute(LLVMScopeChain localScope, LLVMScopeChain globalScope, LLVMContext context, LLVMDLOpen.RTLDFlags rtldFlags, LLVMThreadLocalSymbol threadLocalSymbol) {
        return getFromScope(localScope, globalScope, context, rtldFlags, threadLocalSymbol);
    }

    private static LLVMPointer getFromScope(LLVMScopeChain localScope, LLVMScopeChain globalScope,
                    LLVMContext context, LLVMDLOpen.RTLDFlags rtldFlags, LLVMSymbol symbol) {
        LLVMPointer pointerFromLocal = lookupFromScope(localScope, symbol, context);
        // The default case for active default flag is to search the local scope first.
        if (pointerFromLocal != null && isDefaultFlagActive(rtldFlags)) {
            return pointerFromLocal;
        }

        LLVMPointer pointerFromGlobal = lookupFromScope(globalScope, symbol, context);
        // Otherwise, if the symbol exists in the global scope, then it is returned. (Regardless of
        // the default flag, global scope takes priority if the default flag is not active.)
        if (pointerFromGlobal != null) {
            return pointerFromGlobal;
        }

        // Finally, the symbol is searched in the local scope again (for the case where the default
        // flag is not active).
        return pointerFromLocal;
    }

    @TruffleBoundary
    private static NativeLookupResult getNativeFunction(NativeContextExtension nativeContextExtension, LLVMSymbol symbol) {
        return nativeContextExtension.getNativeFunctionOrNull(symbol.getName());

    }

    @TruffleBoundary
    private static NativePointerIntoLibrary getNativePointer(NativeContextExtension nativeContextExtension, LLVMSymbol symbol) {
        return nativeContextExtension.getNativeHandle(symbol.getName());
    }

    @TruffleBoundary
    private static LLVMPointer lookupFromScope(LLVMScopeChain scope, LLVMSymbol symbol, LLVMContext context) {
        LLVMSymbol resultSymbol = scope.get(symbol.getName());
        if (resultSymbol == null) {
            return null;
        }
        LLVMPointer pointer = context.getSymbol(LLVMAlias.resolveAlias(resultSymbol), BranchProfile.getUncached());
        context.registerSymbol(symbol, pointer);
        return pointer;
    }

    private static boolean isDefaultFlagActive(LLVMDLOpen.RTLDFlags rtldFlags) {
        return LLVMDLOpen.RTLDFlags.RTLD_OPEN_DEFAULT.isActive(rtldFlags);
    }
}
