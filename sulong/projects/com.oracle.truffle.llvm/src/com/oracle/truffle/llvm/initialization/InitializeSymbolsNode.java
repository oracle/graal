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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
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
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMCheckSymbolNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMCheckSymbolNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMWriteSymbolNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMWriteSymbolNodeGen;
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
 * @see InitializeScopeNode
 * @see InitializeGlobalNode
 * @see InitializeModuleNode
 * @see InitializeExternalNode
 * @see InitializeOverwriteNode
 */
public final class InitializeSymbolsNode extends LLVMNode {

    @Child LLVMAllocateNode allocRoSection;
    @Child LLVMAllocateNode allocRwSection;

    @Children final AllocGlobalNode[] allocGlobals;
    final String moduleName;

    @Children final AllocSymbolNode[] allocFuncs;

    @Children final LLVMWriteSymbolNode[] writeGlobals;
    @Children final LLVMWriteSymbolNode[] writeFunctions;
    @Children final LLVMCheckSymbolNode[] checkGlobals;

    private final LLVMScope fileScope;
    private final NodeFactory nodeFactory;

    private final int bitcodeID;
    private final int globalLength;

    public InitializeSymbolsNode(LLVMParserResult result, NodeFactory nodeFactory, boolean lazyParsing, boolean isInternalSulongLibrary, String moduleName) throws Type.TypeOverflowException {
        DataLayout dataLayout = result.getDataLayout();
        this.nodeFactory = nodeFactory;
        this.fileScope = result.getRuntime().getFileScope();
        this.globalLength = result.getSymbolTableSize();
        this.bitcodeID = result.getRuntime().getBitcodeID();
        this.moduleName = moduleName;

        // allocate all non-pointer types as two structs
        // one for read-only and one for read-write
        DataSection roSection = new DataSection(dataLayout);
        DataSection rwSection = new DataSection(dataLayout);
        ArrayList<AllocGlobalNode> allocGlobalsList = new ArrayList<>();
        ArrayList<LLVMWriteSymbolNode> writeGlobalsList = new ArrayList<>();
        ArrayList<LLVMCheckSymbolNode> checkGlobalsList = new ArrayList<>();
        LLVMIntrinsicProvider intrinsicProvider = LLVMLanguage.getLanguage().getCapability(LLVMIntrinsicProvider.class);

        for (GlobalVariable global : result.getDefinedGlobals()) {
            Type type = global.getType().getPointeeType();
            LLVMSymbol symbol = fileScope.get(global.getName());
            if (isSpecialGlobalSlot(type)) {
                allocGlobalsList.add(new AllocPointerGlobalNode(global));
                writeGlobalsList.add(LLVMWriteSymbolNodeGen.create(symbol));
                checkGlobalsList.add(LLVMCheckSymbolNodeGen.create(symbol));
            } else {
                // allocate at least one byte per global (to make the pointers unique)
                if (type.getSize(dataLayout) == 0) {
                    type = PrimitiveType.getIntegerType(8);
                }
                allocGlobalsList.add(new AllocOtherGlobalNode(global, type, roSection, rwSection));
                writeGlobalsList.add(LLVMWriteSymbolNodeGen.create(symbol));
                checkGlobalsList.add(LLVMCheckSymbolNodeGen.create(symbol));
            }
        }

        /*
         * Functions are allocated based on whether they are intrinsic function, regular llvm
         * bitcode function, or eager llvm bitcode function.
         */

        ArrayList<AllocSymbolNode> allocFuncsAndAliasesList = new ArrayList<>();
        ArrayList<LLVMWriteSymbolNode> writeFunctionsList = new ArrayList<>();
        for (FunctionSymbol functionSymbol : result.getDefinedFunctions()) {
            LLVMFunction function = fileScope.getFunction(functionSymbol.getName());
            LLVMFunctionCode functionCode = new LLVMFunctionCode(function);
            // Internal libraries in the llvm library path are allowed to have intriniscs.
            if (isInternalSulongLibrary && intrinsicProvider.isIntrinsified(function.getName())) {
                allocFuncsAndAliasesList.add(new AllocIntrinsicFunctionNode(function, functionCode, nodeFactory, intrinsicProvider));
                writeFunctionsList.add(LLVMWriteSymbolNodeGen.create(function));
            } else if (lazyParsing) {
                allocFuncsAndAliasesList.add(new AllocLLVMFunctionNode(function, functionCode));
                writeFunctionsList.add(LLVMWriteSymbolNodeGen.create(function));
            } else {
                allocFuncsAndAliasesList.add(new AllocLLVMEagerFunctionNode(function, functionCode));
                writeFunctionsList.add(LLVMWriteSymbolNodeGen.create(function));
            }
        }
        this.allocRoSection = roSection.getAllocateNode(nodeFactory, "roglobals_struct", true);
        this.allocRwSection = rwSection.getAllocateNode(nodeFactory, "rwglobals_struct", false);
        this.allocGlobals = allocGlobalsList.toArray(AllocGlobalNode.EMPTY);
        this.allocFuncs = allocFuncsAndAliasesList.toArray(AllocSymbolNode.EMPTY);
        this.writeGlobals = writeGlobalsList.toArray(LLVMWriteSymbolNode.EMPTY);
        this.checkGlobals = checkGlobalsList.toArray(LLVMCheckSymbolNode.EMPTY);
        this.writeFunctions = writeFunctionsList.toArray(LLVMWriteSymbolNode.EMPTY);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void initializeSymbolTable(LLVMContext context) {
        context.registerSymbolTable(bitcodeID, new AssumedValue[globalLength]);
        context.registerScope(fileScope);
    }

    public LLVMPointer execute(LLVMContext ctx) {
        if (ctx.loaderTraceStream() != null) {
            LibraryLocator.traceStaticInits(ctx, "symbol initializers", moduleName);
        }
        LLVMPointer roBase = allocOrNull(allocRoSection);
        LLVMPointer rwBase = allocOrNull(allocRwSection);

        allocGlobals(ctx, roBase, rwBase);
        allocFunctions(ctx);

        if (allocRoSection != null) {
            ctx.registerReadOnlyGlobals(bitcodeID, roBase, nodeFactory);
        }
        if (allocRwSection != null) {
            ctx.registerGlobals(rwBase, nodeFactory);
        }
        return roBase; // needed later to apply memory protection after initialization
    }

    @ExplodeLoop
    private void allocGlobals(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase) {
        for (int i = 0; i < allocGlobals.length; i++) {
            AllocGlobalNode allocGlobal = allocGlobals[i];
            LLVMWriteSymbolNode writeSymbols = writeGlobals[i];
            LLVMCheckSymbolNode checkSymbols = checkGlobals[i];
            LLVMGlobal descriptor = fileScope.getGlobalVariable(allocGlobal.name);
            if (descriptor == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(String.format("Global variable %s not found", allocGlobal.name));
            }
            if (!checkSymbols.execute()) {
                // because of our symbol overriding support, it can happen that the global was
                // already bound before to a different target location
                LLVMPointer ref = allocGlobal.allocate(context, roBase, rwBase);
                writeSymbols.execute(ref);
                List<LLVMSymbol> list = new ArrayList<>();
                list.add(descriptor);
                context.registerSymbolReverseMap(list, ref);
            }
        }
    }

    @ExplodeLoop
    private void allocFunctions(LLVMContext ctx) {
        for (int i = 0; i < allocFuncs.length; i++) {
            AllocSymbolNode allocSymbol = allocFuncs[i];
            LLVMWriteSymbolNode writeSymbols = writeFunctions[i];
            LLVMPointer pointer = allocSymbol.allocate(ctx);
            writeSymbols.execute(pointer);
            List<LLVMSymbol> list = new ArrayList<>();
            list.add(allocSymbol.symbol);
            ctx.registerSymbolReverseMap(list, pointer);
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

        @CompilerDirectives.TruffleBoundary
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

        @CompilerDirectives.TruffleBoundary
        private LLVMFunctionDescriptor createAndResolve(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), functionCode);
            functionDescriptor.getFunctionCode().resolveIfLazyLLVMIRFunction();
            return functionDescriptor;
        }

        @Override
        LLVMPointer allocate(LLVMContext context) {
            LLVMFunctionDescriptor functionDescriptor = createAndResolve(context);
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

        @CompilerDirectives.TruffleBoundary
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

    abstract static class AllocGlobalNode extends LLVMNode {

        static final AllocGlobalNode[] EMPTY = {};

        final String name;

        AllocGlobalNode(GlobalVariable global) {
            this.name = global.getName();
        }

        abstract LLVMPointer allocate(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase);

        @Override
        public String toString() {
            return "AllocGlobal: " + name;
        }
    }

    static final class AllocPointerGlobalNode extends AllocGlobalNode {

        AllocPointerGlobalNode(GlobalVariable global) {
            super(global);
        }

        @Override
        LLVMPointer allocate(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase) {
            return LLVMManagedPointer.create(new LLVMGlobalContainer());
        }
    }

    static final class AllocOtherGlobalNode extends AllocGlobalNode {

        final boolean readOnly;
        final long offset;

        AllocOtherGlobalNode(GlobalVariable global, Type type, DataSection roSection, DataSection rwSection) throws Type.TypeOverflowException {
            super(global);
            this.readOnly = global.isReadOnly();

            DataSection dataSection = readOnly ? roSection : rwSection;
            this.offset = dataSection.add(global, type);
        }

        @Override
        LLVMPointer allocate(LLVMContext context, LLVMPointer roBase, LLVMPointer rwBase) {
            LLVMPointer base = readOnly ? roBase : rwBase;
            return base.increment(offset);
        }
    }
}
