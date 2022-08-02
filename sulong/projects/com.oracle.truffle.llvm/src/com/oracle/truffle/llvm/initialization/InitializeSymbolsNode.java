/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMThreadLocalPointer;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * {@link InitializeSymbolsNode} creates the symbol of all defined functions and globals, and put
 * them into the symbol table. Alias will be unwrapped before they are inserted into the symbol
 * table.
 *
 * @see InitializeGlobalNode
 * @see InitializeModuleNode
 * @see InitializeExternalNode
 * @see InitializeOverwriteNode
 */
public final class InitializeSymbolsNode extends LLVMNode {

    private final String moduleName;
    private final LLVMGlobal imageBase;

    @Child private InitializeGlobalsBlockNode initializeGlobalsBlockNode;

    private final BranchProfile exception;

    /**
     * Contains the offsets of the {@link #globals} to be allocated. -1 represents a pointer type (
     * {@link LLVMGlobalContainer}).
     */
    @CompilationFinal(dimensions = 1) private final int[] globalOffsets;
    @CompilationFinal(dimensions = 1) private final int[] threadLocalGlobalOffsets;
    @CompilationFinal(dimensions = 1) private final boolean[] globalIsReadOnly;
    @CompilationFinal(dimensions = 1) private final LLVMSymbol[] globals;

    @Children private final AllocSymbolNode[] allocFuncs;
    @CompilationFinal(dimensions = 1) private final LLVMSymbol[] functions;
    @CompilationFinal(dimensions = 1) private LLVMSymbol[] threadLocalGlobalsArray;

    private final LLVMScope fileScope;
    private final NodeFactory nodeFactory;

    private final BitcodeID bitcodeID;
    private final int globalLength;

    public InitializeSymbolsNode(LLVMParserResult result, boolean lazyParsing, boolean isInternalSulongLibrary, String moduleName, DataSectionFactory dataSectionFactory, LLVMLanguage language) {
        this.nodeFactory = result.getRuntime().getNodeFactory();
        this.fileScope = result.getRuntime().getFileScope();
        this.globalLength = result.getSymbolTableSize();
        this.bitcodeID = result.getRuntime().getBitcodeID();
        this.moduleName = moduleName;
        this.exception = BranchProfile.create();
        List<GlobalVariable> definedGlobals = result.getDefinedGlobals();
        List<GlobalVariable> threadLocalGlobals = result.getThreadLocalGlobals();
        int globalsCount = definedGlobals.size();
        int threadLocalGlobalsCount = threadLocalGlobals.size();
        this.threadLocalGlobalsArray = new LLVMSymbol[threadLocalGlobalsCount];
        this.globals = new LLVMSymbol[globalsCount];
        LLVMIntrinsicProvider intrinsicProvider = LLVMLanguage.get(null).getCapability(LLVMIntrinsicProvider.class);
        this.globalOffsets = dataSectionFactory.getGlobalOffsets();
        this.threadLocalGlobalOffsets = dataSectionFactory.getThreadLocalGlobalOffsets();
        this.globalIsReadOnly = dataSectionFactory.getGlobalIsReadOnly();
        assert threadLocalGlobalOffsets.length == threadLocalGlobalsArray.length;

        for (int i = 0; i < globalsCount; i++) {
            GlobalVariable global = definedGlobals.get(i);
            LLVMSymbol symbol = fileScope.get(global.getName());
            assert symbol != null;
            globals[i] = symbol;
        }

        for (int i = 0; i < threadLocalGlobalsCount; i++) {
            GlobalVariable tlGlobals = threadLocalGlobals.get(i);
            LLVMSymbol symbol = fileScope.get(tlGlobals.getName());
            assert symbol != null;
            threadLocalGlobalsArray[i] = symbol;
        }

        List<FunctionSymbol> definedFunctions = result.getDefinedFunctions();
        int functionCount = definedFunctions.size();
        this.functions = new LLVMSymbol[functionCount];
        this.allocFuncs = new AllocSymbolNode[functionCount];

        /*
         * Functions are allocated based on whether they are intrinsic function, regular llvm
         * bitcode function, or eager llvm bitcode function.
         */
        for (int i = 0; i < functionCount; i++) {
            FunctionSymbol functionSymbol = definedFunctions.get(i);
            LLVMFunction function = fileScope.getFunction(functionSymbol.getName());
            LLVMFunctionCode functionCode = new LLVMFunctionCode(function);
            // Internal libraries in the llvm library path are allowed to have intriniscs.
            if (isInternalSulongLibrary && intrinsicProvider.isIntrinsified(function.getName())) {
                allocFuncs[i] = new AllocIntrinsicFunctionNode(function, functionCode, nodeFactory, intrinsicProvider);
            } else if (lazyParsing) {
                allocFuncs[i] = new AllocLLVMFunctionNode(function, functionCode);
            } else {
                allocFuncs[i] = new AllocLLVMEagerFunctionNode(function, functionCode);
            }
            functions[i] = function;
        }

        initializeGlobalsBlockNode = new InitializeGlobalsBlockNode(result, dataSectionFactory, language);

        this.imageBase = result.getRuntime().getFileScope().getGlobalVariable("__ImageBase");
    }

    public void initializeSymbolTable(LLVMContext context) {
        context.initializeSymbolTable(bitcodeID, globalLength);
        context.registerScope(fileScope);
    }

    public void execute(LLVMContext ctx) {
        if (LibraryLocator.loggingEnabled()) {
            LibraryLocator.traceStaticInits(ctx, "symbol initializers", moduleName);
        }

        LLVMPointer basePointer = initializeGlobalsBlockNode.allocateGlobalsSectionBlock();
        LLVMPointer rwBase = initializeGlobalsBlockNode.getRwSectionPointer(basePointer);
        LLVMPointer roBase = initializeGlobalsBlockNode.getRoSectionPointer(basePointer);

        if (initializeGlobalsBlockNode.hasGlobalsBlock()) {
            assert basePointer != null;
            ctx.registerGlobals(bitcodeID.getId(), basePointer, initializeGlobalsBlockNode.getGlobalsBlockSize(), nodeFactory);

            if (roBase != null) {
                ctx.registerReadOnlyGlobals(bitcodeID.getId(), roBase, initializeGlobalsBlockNode.getRoBlockSize(), nodeFactory);
            }

            if (imageBase != null) {
                // On Windows the application may make reference to the undefined external global
                // called
                // __ImageBase. This needs to be defined here.
                ctx.initializeSymbol(imageBase, basePointer);
            }
        }

        initializeGlobalSymbols(ctx, roBase, rwBase);
        initializeFunctionSymbols(ctx);
        initializeTLGlobalSymbols(ctx);
    }

    public void initializeTLGlobalSymbols(LLVMContext context) {
        for (int i = 0; i < threadLocalGlobalOffsets.length; i++) {
            int offset = threadLocalGlobalOffsets[i];
            LLVMThreadLocalPointer pointer = new LLVMThreadLocalPointer(threadLocalGlobalsArray[i], offset);
            LLVMSymbol symbol = pointer.getSymbol();
            LLVMPointer llvmPointer = LLVMManagedPointer.create(pointer);
            if (symbol == null) {
                exception.enter();
                throw new LLVMLinkerException(this, "Thread local global variable %s not found", pointer.toString());
            }
            context.initializeSymbol(symbol, llvmPointer);

            if (symbol.isExported()) {
                LLVMGlobal descriptor = fileScope.getGlobalVariable(symbol.getName());
                List<LLVMSymbol> list = new ArrayList<>(1);
                list.add(descriptor);
                context.registerSymbolReverseMap(list, llvmPointer);
            }
        }
    }

    private void initializeGlobalSymbols(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase) {
        for (int i = 0; i < globals.length; i++) {
            LLVMSymbol allocGlobal = globals[i];
            assert allocGlobal != null;
            assert fileScope != null;
            LLVMGlobal descriptor = fileScope.getGlobalVariable(allocGlobal.getName());
            if (descriptor == null) {
                exception.enter();
                throw new LLVMLinkerException(this, "Global variable %s not found", allocGlobal.getName());
            }
            if (!context.checkSymbol(allocGlobal)) {
                // because of our symbol overriding support, it can happen that the global was
                // already bound before to a different target location
                LLVMPointer ref;
                if (globalOffsets[i] == -1) {
                    ref = LLVMManagedPointer.create(new LLVMGlobalContainer());
                } else {
                    LLVMPointer base = globalIsReadOnly[i] ? roBase : rwBase;
                    ref = base.increment(globalOffsets[i]);
                }
                context.initializeSymbol(globals[i], ref);
                List<LLVMSymbol> list = new ArrayList<>(1);
                list.add(descriptor);
                context.registerSymbolReverseMap(list, ref);
            }
        }
    }

    private void initializeFunctionSymbols(LLVMContext context) {
        for (int i = 0; i < allocFuncs.length; i++) {
            AllocSymbolNode allocSymbol = allocFuncs[i];
            LLVMPointer pointer = allocSymbol.allocate(context);
            context.initializeSymbol(functions[i], pointer);
            List<LLVMSymbol> list = new ArrayList<>(1);
            list.add(allocSymbol.symbol);
            context.registerSymbolReverseMap(list, pointer);
        }
    }

    abstract static class AllocSymbolNode extends LLVMNode {

        static final AllocSymbolNode[] EMPTY = {};
        final LLVMSymbol symbol;

        AllocSymbolNode(LLVMSymbol symbol) {
            this.symbol = symbol;
        }

        abstract LLVMPointer allocate(LLVMContext context);
    }

    /*
     * Allocation for internal functions, they can either be regular LLVM bitcode function, eager
     * LLVM bitcode function, and intrinsic function.
     *
     */
    static final class AllocLLVMFunctionNode extends AllocSymbolNode {

        private final LLVMFunctionCode functionCode;

        AllocLLVMFunctionNode(LLVMFunction function, LLVMFunctionCode functionCode) {
            super(function);
            this.functionCode = functionCode;
        }

        @TruffleBoundary
        private LLVMFunctionDescriptor createAndResolve(LLVMContext context) {
            return context.createFunctionDescriptor(symbol.asFunction(), functionCode);
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = createAndResolve(context);
            return LLVMManagedPointer.create(functionDescriptor);
        }
    }

    static final class AllocLLVMEagerFunctionNode extends AllocSymbolNode {

        private final LLVMFunctionCode functionCode;

        AllocLLVMEagerFunctionNode(LLVMFunction function, LLVMFunctionCode functionCode) {
            super(function);
            this.functionCode = functionCode;
        }

        @TruffleBoundary
        private LLVMFunctionDescriptor createAndResolve(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), functionCode);
            functionDescriptor.getFunctionCode().resolveIfLazyLLVMIRFunction();
            return functionDescriptor;
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = createAndResolve(context);
            if (context.isAOTCacheLoad() || context.isAOTCacheStore()) {
                // Initialize the native state in the descriptor to prevent deopts/unstable ifs. The
                // function in the descriptor should already be resolved after the auxiliary engine
                // cache was loaded.
                functionDescriptor.toNative();
            }
            return LLVMManagedPointer.create(functionDescriptor);
        }
    }

    static final class AllocIntrinsicFunctionNode extends AllocSymbolNode {

        private final NodeFactory nodeFactory;
        LLVMIntrinsicProvider intrinsicProvider;
        private final LLVMFunctionCode functionCode;

        AllocIntrinsicFunctionNode(LLVMFunction function, LLVMFunctionCode functionCode, NodeFactory nodeFactory, LLVMIntrinsicProvider intrinsicProvider) {
            super(function);
            this.functionCode = functionCode;
            this.nodeFactory = nodeFactory;
            this.intrinsicProvider = intrinsicProvider;
        }

        @TruffleBoundary
        private LLVMFunctionDescriptor createAndDefine(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), functionCode);
            if (intrinsicProvider.isIntrinsified(symbol.getName())) {
                functionDescriptor.getFunctionCode().define(intrinsicProvider, nodeFactory);
                return functionDescriptor;
            }
            throw new IllegalStateException("Failed to allocate intrinsic function " + symbol.getName());
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = createAndDefine(context);
            return LLVMManagedPointer.create(functionDescriptor);
        }
    }
}
