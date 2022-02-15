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
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMScopeChain;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.NativePointerIntoLibrary;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public class AllocAnExternalSymbolNode extends LLVMNode {

    private final NodeFactory nodeFactory;

    public AllocAnExternalSymbolNode(LLVMParserResult result) {
        this.nodeFactory = result.getRuntime().getNodeFactory();
    }

    public LLVMPointer execute(LLVMScopeChain localScope, LLVMScopeChain globalScope, LLVMIntrinsicProvider intrinsicProvider, NativeContextExtension nativeContextExtension,
                    LLVMContext context, LLVMDLOpen.RTLDFlags rtldFlags, LLVMSymbol symbol) {

        LLVMPointer pointerFromLocal = lookupFromScope(localScope, symbol, context, BranchProfile.create());
        if (pointerFromLocal != null && isDefaultFlagActive(rtldFlags)) {
            return pointerFromLocal;
        }

        LLVMPointer pointerFromGlobal = lookupFromScope(globalScope, symbol, context, BranchProfile.create());
        if (pointerFromGlobal != null && !(isDefaultFlagActive(rtldFlags))) {
            return pointerFromGlobal;
        }

        if (symbol.isGlobalVariable()) {
            if (symbol.isExternalWeak()) {
                return LLVMNativePointer.createNull();
            } else if (!intrinsicProvider.isIntrinsified(symbol.getName()) && nativeContextExtension != null) {
                NativeContextExtension.NativePointerIntoLibrary pointer = getNativePointer(nativeContextExtension, symbol);
                if (pointer != null) {
                    return LLVMNativePointer.create(pointer.getAddress());
                }
                return null;
            }
        }

        if (symbol.isFunction()) {
            if (symbol.isExternalWeak()) {
                return LLVMNativePointer.createNull();
            } else if (intrinsicProvider != null && intrinsicProvider.isIntrinsified(symbol.getName())) {
                LLVMFunctionCode functionCode = new LLVMFunctionCode(symbol.asFunction());
                LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), functionCode);
                functionDescriptor.getFunctionCode().define(intrinsicProvider, nodeFactory);
                return LLVMManagedPointer.create(functionDescriptor);
            } else if (intrinsicProvider != null && !intrinsicProvider.isIntrinsified(symbol.getName()) && nativeContextExtension != null) {
                NativeLookupResult nativeFunction = getNativeFunction(nativeContextExtension, symbol);
                if (nativeFunction != null) {
                    LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), new LLVMFunctionCode(symbol.asFunction()));
                    functionDescriptor.getFunctionCode().define(new LLVMFunctionCode.NativeFunction(nativeFunction.getObject()));
                    symbol.asFunction().setNFISymbol(nativeFunction.getObject());
                    return LLVMManagedPointer.create(functionDescriptor);
                }
                return null;
            }
        }
        return null;
    }

    public LLVMPointer allocExternalFunction(LLVMIntrinsicProvider intrinsicProvider, NativeContextExtension nativeContextExtension,
                                                 LLVMSymbol symbol) {
        if (symbol.isGlobalVariable()) {
            if (symbol.isExternalWeak()) {
                return LLVMNativePointer.createNull();
            } else if (!intrinsicProvider.isIntrinsified(symbol.getName()) && nativeContextExtension != null) {
                NativeContextExtension.NativePointerIntoLibrary pointer = getNativePointer(nativeContextExtension, symbol);
                if (pointer != null) {
                    return LLVMNativePointer.create(pointer.getAddress());
                }
                return null;
            }
        }
        return null;
    }

    public LLVMPointer allocExternalGlobal(LLVMIntrinsicProvider intrinsicProvider, NativeContextExtension nativeContextExtension,
                                               LLVMContext context, LLVMSymbol symbol) {
        if (symbol.isFunction()) {
            if (symbol.isExternalWeak()) {
                return LLVMNativePointer.createNull();
            } else if (intrinsicProvider != null && intrinsicProvider.isIntrinsified(symbol.getName())) {
                LLVMFunctionCode functionCode = new LLVMFunctionCode(symbol.asFunction());
                LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), functionCode);
                functionDescriptor.getFunctionCode().define(intrinsicProvider, nodeFactory);
                return LLVMManagedPointer.create(functionDescriptor);
            } else if (intrinsicProvider != null && !intrinsicProvider.isIntrinsified(symbol.getName()) && nativeContextExtension != null) {
                NativeLookupResult nativeFunction = getNativeFunction(nativeContextExtension, symbol);
                if (nativeFunction != null) {
                    LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), new LLVMFunctionCode(symbol.asFunction()));
                    functionDescriptor.getFunctionCode().define(new LLVMFunctionCode.NativeFunction(nativeFunction.getObject()));
                    symbol.asFunction().setNFISymbol(nativeFunction.getObject());
                    return LLVMManagedPointer.create(functionDescriptor);
                }
                return null;
            }
        }
        return null;
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
    private static LLVMPointer lookupFromScope(LLVMScopeChain scope, LLVMSymbol symbol, LLVMContext context, BranchProfile exception) {
        LLVMSymbol resultSymbol = scope.get(symbol.getName());
        if (resultSymbol == null) {
            return null;
        }
        LLVMSymbol function = LLVMAlias.resolveAlias(resultSymbol);
        LLVMPointer pointer = context.getSymbol(function, exception);
        context.registerSymbol(symbol, pointer);
        return pointer;
    }

    private static boolean isDefaultFlagActive(LLVMDLOpen.RTLDFlags rtldFlags) {
        return LLVMDLOpen.RTLDFlags.RTLD_OPEN_DEFAULT.isActive(rtldFlags);
    }
}
