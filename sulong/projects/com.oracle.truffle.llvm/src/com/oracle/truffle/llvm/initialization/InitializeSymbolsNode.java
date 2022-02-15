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

import com.oracle.truffle.api.CompilerDirectives;
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
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.util.ArrayList;
import java.util.List;

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

    @Child private LLVMAllocateNode allocRoSection;
    @Child private LLVMAllocateNode allocRwSection;

    private final BranchProfile exception;

    /**
     * Contains the offsets of the {@link #globals} to be allocated. -1 represents a pointer type (
     * {@link LLVMGlobalContainer}).
     */
    @CompilationFinal(dimensions = 1) private final int[] globalOffsets;
    @CompilationFinal(dimensions = 1) private final boolean[] globalIsReadOnly;
    @CompilationFinal(dimensions = 1) private final LLVMSymbol[] globals;

    @Children private final AllocSymbolNode[] allocFuncs;
    @CompilationFinal(dimensions = 1) private final LLVMSymbol[] functions;

    private final LLVMScope fileScope;
    private final NodeFactory nodeFactory;

    private final BitcodeID bitcodeID;
    private final int globalLength;

    public InitializeSymbolsNode(LLVMParserResult result, boolean lazyParsing, boolean isInternalSulongLibrary, String moduleName) throws Type.TypeOverflowException {
        DataLayout dataLayout = result.getDataLayout();
        this.nodeFactory = result.getRuntime().getNodeFactory();
        this.fileScope = result.getRuntime().getFileScope();
        this.globalLength = result.getSymbolTableSize();
        this.bitcodeID = result.getRuntime().getBitcodeID();
        this.moduleName = moduleName;

        this.exception = BranchProfile.create();

        // allocate all non-pointer types as two structs
        // one for read-only and one for read-write
        DataSection roSection = new DataSection(dataLayout);
        DataSection rwSection = new DataSection(dataLayout);
        List<GlobalVariable> definedGlobals = result.getDefinedGlobals();
        int globalsCount = definedGlobals.size();
        this.globalOffsets = new int[globalsCount];
        this.globalIsReadOnly = new boolean[globalsCount];
        this.globals = new LLVMSymbol[globalsCount];
        LLVMIntrinsicProvider intrinsicProvider = LLVMLanguage.get(null).getCapability(LLVMIntrinsicProvider.class);

        for (int i = 0; i < globalsCount; i++) {
            GlobalVariable global = definedGlobals.get(i);
            Type type = global.getType().getPointeeType();
            if (isSpecialGlobalSlot(type)) {
                globalOffsets[i] = -1; // pointer type
            } else {
                // allocate at least one byte per global (to make the pointers unique)
                if (type.getSize(dataLayout) == 0) {
                    type = PrimitiveType.getIntegerType(8);
                }
                globalIsReadOnly[i] = global.isReadOnly();
                DataSection dataSection = globalIsReadOnly[i] ? roSection : rwSection;
                long offset = dataSection.add(global, type);
                assert offset >= 0;
                if (offset > Integer.MAX_VALUE) {
                    throw CompilerDirectives.shouldNotReachHere("globals section >2GB not supported");
                }
                globalOffsets[i] = (int) offset;
            }
            LLVMSymbol symbol = fileScope.get(global.getName());
            globals[i] = symbol;
        }

        /*
         * Functions are allocated based on whether they are intrinsic function, regular llvm
         * bitcode function, or eager llvm bitcode function.
         */

        List<FunctionSymbol> definedFunctions = result.getDefinedFunctions();
        int functionCount = definedFunctions.size();
        this.functions = new LLVMSymbol[functionCount];
        this.allocFuncs = new AllocSymbolNode[functionCount];
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
        this.allocRoSection = roSection.getAllocateNode(nodeFactory, "roglobals_struct", true);
        this.allocRwSection = rwSection.getAllocateNode(nodeFactory, "rwglobals_struct", false);
    }

    public void initializeSymbolTable(LLVMContext context) {
        context.initializeSymbolTable(bitcodeID, globalLength);
        context.registerScope(fileScope);
    }

    public LLVMPointer execute(LLVMContext ctx) {
        if (LibraryLocator.loggingEnabled()) {
            LibraryLocator.traceStaticInits(ctx, "symbol initializers", moduleName);
        }
        LLVMPointer roBase = allocOrNull(allocRoSection);
        LLVMPointer rwBase = allocOrNull(allocRwSection);

        allocGlobals(ctx, roBase, rwBase);
        allocFunctions(ctx);

        if (allocRoSection != null) {
            ctx.registerReadOnlyGlobals(bitcodeID.getId(), roBase, nodeFactory);
        }
        if (allocRwSection != null) {
            ctx.registerGlobals(rwBase, nodeFactory);
        }
        return roBase; // needed later to apply memory protection after initialization
    }

    private void allocGlobals(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase) {
        for (int i = 0; i < globals.length; i++) {
            LLVMSymbol allocGlobal = globals[i];
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

    private void allocFunctions(LLVMContext context) {
        for (int i = 0; i < allocFuncs.length; i++) {
            AllocSymbolNode allocSymbol = allocFuncs[i];
            LLVMPointer pointer = allocSymbol.allocate(context);
            context.initializeSymbol(functions[i], pointer);
            List<LLVMSymbol> list = new ArrayList<>(1);
            list.add(allocSymbol.symbol);
            context.registerSymbolReverseMap(list, pointer);
        }
    }

    private static LLVMPointer allocOrNull(LLVMAllocateNode allocNode) {
        if (allocNode != null) {
            return allocNode.executeWithTarget();
        } else {
            return null;
        }
    }

    private static void addPaddingTypes(ArrayList<Type> result, int padding) {
        assert padding >= 0;
        int remaining = padding;
        while (remaining > 0) {
            int size = Math.min(Long.BYTES, Integer.highestOneBit(remaining));
            result.add(PrimitiveType.getIntegerType(size * Byte.SIZE));
            remaining -= size;
        }
    }

    /**
     * Globals of pointer type need to be handles specially because they can potentially contain a
     * foreign object.
     */
    private static boolean isSpecialGlobalSlot(Type type) {
        return type instanceof PointerType;
    }

    private static int getAlignment(DataLayout dataLayout, GlobalVariable global, Type type) {
        return global.getAlign() > 0 ? 1 << (global.getAlign() - 1) : type.getAlignment(dataLayout);
    }

    static final class DataSection {

        final DataLayout dataLayout;
        final ArrayList<Type> types = new ArrayList<>();

        private long offset = 0;

        DataSection(DataLayout dataLayout) {
            this.dataLayout = dataLayout;
        }

        long add(GlobalVariable global, Type type) throws Type.TypeOverflowException {
            int alignment = getAlignment(dataLayout, global, type);
            int padding = Type.getPadding(offset, alignment);
            addPaddingTypes(types, padding);
            offset = Type.addUnsignedExact(offset, padding);
            long ret = offset;
            types.add(type);
            offset = Type.addUnsignedExact(offset, type.getSize(dataLayout));
            return ret;
        }

        LLVMAllocateNode getAllocateNode(NodeFactory factory, String typeName, boolean readOnly) {
            if (offset > 0) {
                StructureType structType = StructureType.createNamedFromList(typeName, true, types);
                return factory.createAllocateGlobalsBlock(structType, readOnly);
            } else {
                return null;
            }
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
