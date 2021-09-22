/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNode.AllocExistingLocalSymbolsNode.AllocExistingGlobalSymbolsNode;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNode.AllocExistingLocalSymbolsNode.AllocExistingGlobalSymbolsNode.AllocExternalFunctionNode;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNode.AllocExistingLocalSymbolsNode.AllocExistingGlobalSymbolsNode.AllocExternalGlobalNode;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMScopeChain;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen.RTLDFlags;
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

    public abstract LLVMPointer execute(LLVMScopeChain localScope, LLVMScopeChain globalScope, LLVMIntrinsicProvider intrinsicProvider, NativeContextExtension nativeContextExtension,
                    LLVMContext context,
                    RTLDFlags rtldFlags);

    /**
     * Allocating symbols to the symbol table as provided by the local scope.
     */
    @ImportStatic({LLVMAlias.class, LLVMDLOpen.class})
    abstract static class AllocExistingLocalSymbolsNode extends AllocExternalSymbolNode {

        AllocExistingLocalSymbolsNode(LLVMSymbol symbol) {
            super(symbol);
        }

        @Specialization(guards = {"pointer != null", "isDefaultFlagActive(rtldFlags)"}, limit = "1")
        @GenerateAOT.Exclude
        LLVMPointer doDefault(@SuppressWarnings("unused") LLVMScopeChain localScope,
                        @SuppressWarnings("unused") LLVMScopeChain globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                        @SuppressWarnings("unused") LLVMContext context,
                        @SuppressWarnings("unused") RTLDFlags rtldFlags,
                        @SuppressWarnings("unused") @Cached() LookupScopeNode lookupNode,
                        @Bind("lookupNode.execute(localScope, symbol, context)") LLVMPointer pointer) {
            return pointer;
        }

        @Specialization(guards = {"pointer != null", "!(isDefaultFlagActive(rtldFlags))"}, limit = "1")
        @GenerateAOT.Exclude
        LLVMPointer doDLopen(@SuppressWarnings("unused") LLVMScopeChain localScope,
                        @SuppressWarnings("unused") LLVMScopeChain globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                        @SuppressWarnings("unused") LLVMContext context,
                        @SuppressWarnings("unused") RTLDFlags rtldFlags,
                        @SuppressWarnings("unused") @Cached() LookupScopeNode lookupNode,
                        @Bind("lookupNode.execute(globalScope, symbol, context)") LLVMPointer pointer) {
            return pointer;
        }

        @TruffleBoundary
        protected boolean containsSymbol(LLVMSymbol localSymbol) {
            return symbol.equals(localSymbol);
        }

        protected boolean isDefaultFlagActive(RTLDFlags rtldFlags) {
            return RTLDFlags.RTLD_OPEN_DEFAULT.isActive(rtldFlags);
        }

        /**
         * Fallback for when the same symbol is being overwritten.
         * <p>
         * There exists code where the symbol is not there.
         */
        @Fallback
        LLVMPointer allocateFromLocalScopeFallback(@SuppressWarnings("unused") LLVMScopeChain localScope,
                        @SuppressWarnings("unused") LLVMScopeChain globalScope,
                        @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                        @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                        @SuppressWarnings("unused") LLVMContext context,
                        @SuppressWarnings("unused") RTLDFlags rtldFlags) {
            return null;
        }

        abstract static class LookupScopeNode extends LLVMNode {

            public abstract LLVMPointer execute(LLVMScopeChain scope, LLVMSymbol symbol, LLVMContext context);

            @Specialization(guards = {"resultSymbol != null"})
            @GenerateAOT.Exclude
            LLVMPointer allocateFromLocalScope(@SuppressWarnings("unused") LLVMScopeChain scope,
                            LLVMSymbol symbol,
                            LLVMContext context,
                            @Bind("scope.get(symbol.getName())") LLVMSymbol resultSymbol,
                            @Cached BranchProfile exception) {
                LLVMSymbol function = LLVMAlias.resolveAlias(resultSymbol);
                LLVMPointer pointer = context.getSymbol(function, exception);
                context.registerSymbol(symbol, pointer);
                return pointer;
            }

            @Specialization(guards = {"resultSymbol == null"})
            @GenerateAOT.Exclude
            LLVMPointer allocateFromLocalScopeNull(@SuppressWarnings("unused") LLVMScopeChain scope,
                            @SuppressWarnings("unused") LLVMSymbol symbol,
                            @SuppressWarnings("unused") LLVMContext context,
                            @SuppressWarnings("unused") @Bind("scope.get(symbol.getName())") LLVMSymbol resultSymbol) {
                return null;
            }
        }

        /**
         * Allocating symbols to the symbol table as provided by the global scope.
         */
        @ImportStatic({LLVMAlias.class, LLVMDLOpen.class})
        abstract static class AllocExistingGlobalSymbolsNode extends AllocExistingLocalSymbolsNode {

            AllocExistingGlobalSymbolsNode(LLVMSymbol symbol) {
                super(symbol);
            }

            // global for default

            // local for dlopen

            @Specialization(guards = {"localScope.get(symbol.getName()) == null", "pointer != null", "isDefaultFlagActive(rtldFlags)"}, limit = "1")
            @GenerateAOT.Exclude
            LLVMPointer doDefaultGlobal(@SuppressWarnings("unused") LLVMScopeChain localScope,
                            @SuppressWarnings("unused") LLVMScopeChain globalScope,
                            @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                            @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                            @SuppressWarnings("unused") LLVMContext context,
                            @SuppressWarnings("unused") RTLDFlags rtldFlags,
                            @SuppressWarnings("unused") @Cached() LookupScopeNode lookupNode,
                            @Bind("lookupNode.execute(globalScope, symbol, context)") LLVMPointer pointer) {
                return pointer;
            }

            @Specialization(guards = {"globalScope.get(symbol.getName()) == null", "pointer != null", "!(isDefaultFlagActive(rtldFlags))"}, limit = "1")
            @GenerateAOT.Exclude
            LLVMPointer doDLopenLocal(@SuppressWarnings("unused") LLVMScopeChain localScope,
                            @SuppressWarnings("unused") LLVMScopeChain globalScope,
                            @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                            @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                            @SuppressWarnings("unused") LLVMContext context,
                            @SuppressWarnings("unused") RTLDFlags rtldFlags,
                            @SuppressWarnings("unused") @Cached() LookupScopeNode lookupNode,
                            @Bind("lookupNode.execute(localScope, symbol, context)") LLVMPointer pointer) {
                return pointer;
            }

            @Override
            @TruffleBoundary
            protected boolean containsSymbol(LLVMSymbol globalSymbol) {
                return symbol.equals(globalSymbol);
            }

            @Override
            public abstract LLVMPointer execute(LLVMScopeChain localScope, LLVMScopeChain globalScope, LLVMIntrinsicProvider intrinsicProvider, NativeContextExtension nativeContextExtension,
                            LLVMContext context, RTLDFlags rtldFlags);

            /**
             * Allocating a native global symbol to the symbol table as provided by the nfi context.
             */
            abstract static class AllocExternalGlobalNode extends AllocExistingGlobalSymbolsNode {

                AllocExternalGlobalNode(LLVMSymbol symbol) {
                    super(symbol);
                }

                @Specialization(guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                                "symbol.isGlobalVariable()", "symbol.isExternalWeak()"})
                LLVMPointer allocateExternalWeakGlobal(@SuppressWarnings("unused") LLVMScopeChain localScope,
                                @SuppressWarnings("unused") LLVMScopeChain globalScope,
                                @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                                @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                                @SuppressWarnings("unused") LLVMContext context,
                                @SuppressWarnings("unused") RTLDFlags rtldFlags) {
                    return LLVMNativePointer.createNull();
                }

                @TruffleBoundary
                @Specialization(guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                                "!intrinsicProvider.isIntrinsified(symbol.getName())", "nativeContextExtension != null",
                                "symbol.isGlobalVariable()"})
                LLVMPointer allocateNativeGlobal(@SuppressWarnings("unused") LLVMScopeChain localScope,
                                @SuppressWarnings("unused") LLVMScopeChain globalScope,
                                @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                                NativeContextExtension nativeContextExtension,
                                @SuppressWarnings("unused") LLVMContext context,
                                @SuppressWarnings("unused") RTLDFlags rtldFlags) {
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

                @Specialization(guards = {"localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                                "symbol.isFunction()", "symbol.isExternalWeak()"})
                LLVMPointer allocateExternalWeakFunction(@SuppressWarnings("unused") LLVMScopeChain localScope,
                                @SuppressWarnings("unused") LLVMScopeChain globalScope,
                                @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                                @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                                @SuppressWarnings("unused") LLVMContext context,
                                @SuppressWarnings("unused") RTLDFlags rtldFlags) {
                    return LLVMNativePointer.createNull();
                }

                @TruffleBoundary
                @Specialization(guards = {"intrinsicProvider != null", "localScope.get(symbol.getName()) == null", "globalScope.get(symbol.getName()) == null",
                                "intrinsicProvider.isIntrinsified(symbol.getName())", "symbol.isFunction()"})
                LLVMPointer allocateIntrinsicFunction(@SuppressWarnings("unused") LLVMScopeChain localScope,
                                @SuppressWarnings("unused") LLVMScopeChain globalScope,
                                LLVMIntrinsicProvider intrinsicProvider,
                                @SuppressWarnings("unused") NativeContextExtension nativeContextExtension,
                                LLVMContext context,
                                @SuppressWarnings("unused") RTLDFlags rtldFlags) {
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
                LLVMPointer allocateNativeFunction(@SuppressWarnings("unused") LLVMScopeChain localScope,
                                @SuppressWarnings("unused") LLVMScopeChain globalScope,
                                @SuppressWarnings("unused") LLVMIntrinsicProvider intrinsicProvider,
                                NativeContextExtension nativeContextExtension,
                                LLVMContext context,
                                @SuppressWarnings("unused") RTLDFlags rtldFlags) {
                    NativeContextExtension.NativeLookupResult nativeFunction = nativeContextExtension.getNativeFunctionOrNull(symbol.getName());
                    if (nativeFunction != null) {
                        LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), new LLVMFunctionCode(symbol.asFunction()));
                        functionDescriptor.getFunctionCode().define(new LLVMFunctionCode.NativeFunction(nativeFunction.getObject()));
                        this.symbol.asFunction().setNFISymbol(nativeFunction.getObject());
                        return LLVMManagedPointer.create(functionDescriptor);
                    }
                    return null;
                }
            }
        }
    }
}
