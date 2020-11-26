/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNode.AllocExistingLocalSymbolsNode.AllocExistingGlobalSymbolsNode;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNode.AllocExistingLocalSymbolsNode.AllocExistingGlobalSymbolsNode.AllocExternalFunctionNode;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNode.AllocExistingLocalSymbolsNode.AllocExistingGlobalSymbolsNode.AllocExternalGlobalNode;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLocalScope;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * The pattern of resolving external symbols are as follow: {@link AllocExternalSymbolNode} is the
 * top level node, with the execute method. {@link AllocExistingLocalSymbolsNode} implements the
 * case when the symbol exists in the local scope, and it extends {@link AllocExternalSymbolNode}.
 * {@link AllocExternalSymbolNode} implements the case when the symbol exists in the global scope,
 * and it extends {@link AllocExistingLocalSymbolsNode}. {@link AllocExternalGlobalNode} is for
 * allocating a native global symbol to the symbol take and {@link AllocExternalFunctionNode} is for
 * allocating an instrinsic or a native function into the symbol table, and they both extend
 * {@link AllocExistingGlobalSymbolsNode}.
 * <p>
 * {@link AllocExternalFunctionNode} is created for allocating external functions
 * {@link InitializeExternalNode}, which has four cases (the first two is covered by the
 * superclasses {@link AllocExistingGlobalSymbolsNode} and {@link AllocExistingLocalSymbolsNode} ):
 * 1) If the function is defined in the local scope. 2) If the function is defined in the global
 * scope. 3) if the function is an intrinsic function. 4) And finally, if the function is a native
 * function.
 * <p>
 * Similarly, {@link AllocExternalGlobalNode} is created for allocating external globals
 * {@link InitializeExternalNode}.
 * <p>
 * For overriding defined functions for symbol resolution in {@link InitializeOverwriteNode},
 * {@link AllocExistingGlobalSymbolsNode} is created for overwriting global symbols as they can be
 * taken from the global and local scope, meanwhile {@link AllocExistingLocalSymbolsNode} is created
 * for overwriting functions, as they can only be taken from the local scopes.
 */
public abstract class AllocExternalSymbolNode extends LLVMNode {

    @SuppressWarnings("unused") public static final AllocExternalSymbolNode[] EMPTY = {};
    final LLVMSymbol symbol;

    public AllocExternalSymbolNode(LLVMSymbol symbol) {
        this.symbol = symbol;
    }

    public abstract LLVMPointer execute(LLVMLocalScope localScope, LLVMScope globalScope, LLVMIntrinsicProvider intrinsicProvider, NativeContextExtension nativeContextExtension, LLVMContext context);

    /**
     * Allocating symbols to the symbol table as provided by the local scope.
     */
    @ImportStatic(LLVMAlias.class)
    abstract static class AllocExistingLocalSymbolsNode extends AllocExternalSymbolNode {

        AllocExistingLocalSymbolsNode(LLVMSymbol symbol) {
            super(symbol);
        }

        @Specialization(guards = {"cachedLocalSymbol != null", "localScope.get(symbol.getName()) == cachedLocalSymbol", "!(containsSymbol(cachedLocalSymbol))"})
        LLVMPointer allocateFromLocalScopeCached(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                        LLVMContext context,
                        @Cached("resolveAlias(localScope.get(symbol.getName()))") LLVMSymbol cachedLocalSymbol) {
            LLVMPointer pointer = context.getSymbol(cachedLocalSymbol);
            context.registerSymbol(symbol, pointer);
            return pointer;
        }

        @Specialization(replaces = "allocateFromLocalScopeCached", guards = {"localScope.get(symbol.getName()) != null", "!(containsSymbol(localScope.get(symbol.getName())))"})
        LLVMPointer allocateFromLocalScope(LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                        LLVMContext context) {
            LLVMSymbol function = LLVMAlias.resolveAlias(localScope.get(symbol.getName()));
            LLVMPointer pointer = context.getSymbol(function);
            context.registerSymbol(symbol, pointer);
            return pointer;
        }

        @TruffleBoundary
        protected boolean containsSymbol(LLVMSymbol localSymbol) {
            return symbol.equals(localSymbol);
        }

        /**
         * Fallback for when the same symbol is being overwritten.
         * <p>
         * There exists code where the symbol is not there.
         */
        @Fallback
        LLVMPointer allocateFromLocalScopeFallback(@SuppressWarnings("unused") LLVMLocalScope localScope,
                        @SuppressWarnings("unused") LLVMScope globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                        @SuppressWarnings("unused") LLVMContext context) {
            return null;
        }

        /**
         * Allocating symbols to the symbol table as provided by the global scope.
         */
        @ImportStatic(LLVMAlias.class)
        abstract static class AllocExistingGlobalSymbolsNode extends AllocExistingLocalSymbolsNode {

            AllocExistingGlobalSymbolsNode(LLVMSymbol symbol) {
                super(symbol);
            }

            @Specialization(guards = {"localScope.get(symbol.getName()) == null", "cachedGlobalSymbol != null", "globalScope.get(symbol.getName()) == cachedGlobalSymbol",
                            "!(containsSymbol(cachedGlobalSymbol))"})
            LLVMPointer allocateFromGlobalScopeCached(@SuppressWarnings("unused") LLVMLocalScope localScope,
                            @SuppressWarnings("unused") LLVMScope globalScope,
                            @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                            @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                            LLVMContext context,
                            @Cached("resolveAlias(globalScope.get(symbol.getName()))") LLVMSymbol cachedGlobalSymbol) {
                LLVMPointer pointer = context.getSymbol(cachedGlobalSymbol);
                context.registerSymbol(symbol, pointer);
                return pointer;
            }

            @Specialization(replaces = "allocateFromGlobalScopeCached", guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) != null",
                            "!(containsSymbol(globalScope.get(symbol.getName())))"})
            LLVMPointer allocateFromGlobalScope(@SuppressWarnings("unused") LLVMLocalScope localScope,
                            LLVMScope globalScope,
                            @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                            @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                            LLVMContext context) {
                LLVMSymbol function = LLVMAlias.resolveAlias(globalScope.get(symbol.getName()));
                assert function.isFunction();
                LLVMPointer pointer = context.getSymbol(function);
                context.registerSymbol(symbol, pointer);
                return pointer;
            }

            @Override
            @TruffleBoundary
            protected boolean containsSymbol(LLVMSymbol globalSymbol) {
                return symbol.equals(globalSymbol);
            }

            @Override
            public abstract LLVMPointer execute(LLVMLocalScope localScope, LLVMScope globalScope, LLVMIntrinsicProvider intrinsicProvider, NativeContextExtension nativeContextExtension,
                            LLVMContext context);

            /**
             * Allocating a native global symbol to the symbol table as provided by the nfi context.
             */
            abstract static class AllocExternalGlobalNode extends AllocExistingGlobalSymbolsNode {

                AllocExternalGlobalNode(LLVMSymbol symbol) {
                    super(symbol);
                }

                @TruffleBoundary
                @Specialization(guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                                "!intrinsicProvider.isIntrinsified(symbol.getName())", "nativeContextExtension != null",
                                "symbol.isGlobalVariable()"})
                LLVMPointer allocateNativeGlobal(@SuppressWarnings("unused") LLVMLocalScope localScope,
                                @SuppressWarnings("unused") LLVMScope globalScope,
                                @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                                NativeContextExtension nativeContextExtension,
                                @SuppressWarnings("unused") LLVMContext context) {
                    NativeContextExtension.NativePointerIntoLibrary pointer = nativeContextExtension.getNativeHandle(symbol.getName());
                    if (pointer != null) {
                        return LLVMNativePointer.create(pointer.getAddress());
                    }
                    return null;
                }
            }

            /*
             * Allocates a managed pointer for the newly constructed function descriptors of a
             * native function and intrinsic function.
             */
            abstract static class AllocExternalFunctionNode extends AllocExistingGlobalSymbolsNode {

                private final NodeFactory nodeFactory;
                private final LLVMFunctionCode functionCode;

                AllocExternalFunctionNode(LLVMSymbol symbol, LLVMFunctionCode functionCode, NodeFactory nodeFactory) {
                    super(symbol);
                    this.functionCode = functionCode;
                    this.nodeFactory = nodeFactory;
                }

                @TruffleBoundary
                @Specialization(guards = {"intrinsicProvider != null", "localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                                "intrinsicProvider.isIntrinsified(symbol.getName())", "symbol.isFunction()"})
                LLVMPointer allocateIntrinsicFunction(@SuppressWarnings("unused") LLVMLocalScope localScope,
                                @SuppressWarnings("unused") LLVMScope globalScope,
                                LLVMIntrinsicProvider intrinsicProvider,
                                @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                                LLVMContext context) {
                    LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), functionCode);
                    functionDescriptor.getFunctionCode().define(intrinsicProvider, nodeFactory);
                    return LLVMManagedPointer.create(functionDescriptor);
                }

                /*
                 * Currently native functions/globals that are not in the nfi context are not
                 * written into the symbol table. For function, another lookup will happen when
                 * something tries to call the function. (see {@link
                 * LLVMDispatchNode#doCachedNative}) The function will be taken from the filescope
                 * directly. Ideally the filescope and symbol table is in sync, and any lazy look up
                 * will resolve from the function code in the symbol table.
                 */
                @TruffleBoundary
                @Specialization(guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                                "!intrinsicProvider.isIntrinsified(symbol.getName())", "nativeContextExtension != null",
                                "symbol.isFunction()"})
                LLVMPointer allocateNativeFunction(@SuppressWarnings("unused") LLVMLocalScope localScope,
                                @SuppressWarnings("unused") LLVMScope globalScope,
                                @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                                NativeContextExtension nativeContextExtension,
                                LLVMContext context) {
                    NativeContextExtension.NativeLookupResult nativeFunction = nativeContextExtension.getNativeFunctionOrNull(symbol.getName());
                    if (nativeFunction != null) {
                        LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), new LLVMFunctionCode(symbol.asFunction()));
                        functionDescriptor.getFunctionCode().define(new LLVMFunctionCode.NativeFunction(nativeFunction.getObject()));
                        return LLVMManagedPointer.create(functionDescriptor);
                    }
                    return null;
                }
            }
        }
    }
}
