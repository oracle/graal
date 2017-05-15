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
package com.oracle.truffle.llvm.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.datalayout.DataLayoutConverter.DataSpecConverterImpl;
import com.oracle.truffle.llvm.parser.metadata.SourceSectionGenerator;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolResolver;
import com.oracle.truffle.llvm.parser.util.Pair;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class LLVMParserRuntime {
    private static final String CONSTRUCTORS_VARNAME = "@llvm.global_ctors";
    private static final String DESTRUCTORS_VARNAME = "@llvm.global_dtors";
    private static final int LEAST_CONSTRUCTOR_PRIORITY = 65535;

    private static final Comparator<Pair<Integer, ?>> ASCENDING_PRIORITY = (p1, p2) -> p1.getFirst() >= p2.getFirst() ? 1 : -1;
    private static final Comparator<Pair<Integer, ?>> DESCENDING_PRIORITY = (p1, p2) -> p1.getFirst() < p2.getFirst() ? 1 : -1;

    public static LLVMParserResult parse(Source source, LLVMLanguage language, LLVMContext context, SulongNodeFactory nodeFactory) {
        BitcodeParserResult parserResult = BitcodeParserResult.getFromSource(source);
        ModelModule model = parserResult.getModel();
        StackAllocation stack = parserResult.getStackAllocation();
        LLVMPhiManager phiManager = parserResult.getPhis();
        LLVMLabelList labels = parserResult.getLabels();
        TargetDataLayout layout = model.getTargetDataLayout();
        assert layout != null;

        if (!LLVMLogger.TARGET_NONE.equals(LLVMOptions.DEBUG.printMetadata())) {
            model.getMetadata().print(LLVMLogger.print(LLVMOptions.DEBUG.printMetadata()));
        }

        LLVMModelVisitor module = new LLVMModelVisitor();
        model.accept(module);

        DataLayoutConverter.DataSpecConverterImpl targetDataLayout = DataLayoutConverter.getConverter(layout.getDataLayout());
        LLVMParserRuntime runtime = new LLVMParserRuntime(source, language, context, stack, targetDataLayout, nodeFactory, module.getGlobals(), module.getAliases());

        Map<String, LLVMFunctionDescriptor> functions = initializeFunctions(runtime, context, nodeFactory, stack, phiManager, labels, module.getFunctions(), source);
        LLVMFunctionDescriptor mainFunction = functions.get("@main");

        LLVMSymbolResolver symbolResolver = new LLVMSymbolResolver(runtime, labels);
        LLVMExpressionNode[] globals = runtime.createGlobalVariableNodes(symbolResolver).toArray(new LLVMExpressionNode[0]);
        RootNode globalVarInits = nodeFactory.createStaticInitsRootNode(runtime, globals);
        RootCallTarget globalVarInitsTarget = Truffle.getRuntime().createCallTarget(globalVarInits);
        LLVMExpressionNode[] deallocs = runtime.getDeallocations();
        RootNode globalVarDeallocs = nodeFactory.createStaticInitsRootNode(runtime, deallocs);
        RootCallTarget globalVarDeallocsTarget = Truffle.getRuntime().createCallTarget(globalVarDeallocs);

        List<RootCallTarget> constructorFunctions = runtime.getConstructors();
        List<RootCallTarget> destructorFunctions = runtime.getDestructors();

        RootCallTarget mainFunctionCallTarget;
        if (mainFunction != null) {
            RootCallTarget mainCallTarget = mainFunction.getLLVMIRFunction();
            RootNode globalFunction = nodeFactory.createGlobalRootNode(runtime, mainCallTarget, context.getMainArguments(), source, mainFunction.getType().getArgumentTypes());
            RootCallTarget globalFunctionRoot = Truffle.getRuntime().createCallTarget(globalFunction);
            RootNode globalRootNode = nodeFactory.createGlobalRootNodeWrapping(runtime, globalFunctionRoot, mainFunction.getType().getReturnType());
            mainFunctionCallTarget = Truffle.getRuntime().createCallTarget(globalRootNode);
        } else {
            mainFunctionCallTarget = null;
        }
        return new LLVMParserResult(mainFunctionCallTarget, globalVarInitsTarget, globalVarDeallocsTarget, constructorFunctions, destructorFunctions);
    }

    private static Map<String, LLVMFunctionDescriptor> initializeFunctions(LLVMParserRuntime runtime, LLVMContext context, SulongNodeFactory nodeFactory, StackAllocation stack,
                    LLVMPhiManager phiManager, LLVMLabelList labels, List<FunctionDefinition> functions, Source source) {
        Map<String, LLVMFunctionDescriptor> result = new HashMap<>();
        for (FunctionDefinition function : functions) {
            String functionName = function.getName();
            LLVMFunctionDescriptor functionDescriptor = context.lookupFunctionDescriptor(functionName, i -> nodeFactory.createFunctionDescriptor(context, functionName, function.getType(), i));

            LazyToTruffleConverterImpl lazyConverter = new LazyToTruffleConverterImpl(runtime, nodeFactory, function, source, stack.getFrame(functionName), phiManager.getPhiMap(functionName),
                            labels.labels(functionName));
            if (!LLVMOptions.ENGINE.lazyParsing()) {
                lazyConverter.convert();
            }
            functionDescriptor.declareInSulong(lazyConverter);
            result.put(functionName, functionDescriptor);
        }
        return result;
    }

    private final Source source;
    private final LLVMLanguage language;
    private final LLVMContext context;
    private final StackAllocation stack;
    private final DataLayoutConverter.DataSpecConverterImpl targetDataLayout;
    private final SulongNodeFactory nodeFactory;
    private final Map<GlobalValueSymbol, LLVMExpressionNode> globals;
    private final Map<GlobalAlias, Symbol> aliases;

    private final List<LLVMExpressionNode> deallocations;
    private final SourceSectionGenerator sourceSectionGenerator;
    private final Map<String, Object> globalVariableScope;

    public LLVMParserRuntime(Source source, LLVMLanguage language, LLVMContext context, StackAllocation stack, DataSpecConverterImpl targetDataLayout, SulongNodeFactory nodeFactory,
                    Map<GlobalValueSymbol, LLVMExpressionNode> globals, Map<GlobalAlias, Symbol> aliases) {
        this.source = source;
        this.context = context;
        this.stack = stack;
        this.targetDataLayout = targetDataLayout;
        this.nodeFactory = nodeFactory;
        this.language = language;
        this.globals = globals;
        this.aliases = aliases;

        this.deallocations = new ArrayList<>();
        this.sourceSectionGenerator = new SourceSectionGenerator();
        this.globalVariableScope = new HashMap<>();
    }

    private LLVMExpressionNode getGlobalVariable(LLVMSymbolResolver symbolResolver, GlobalValueSymbol global) {
        Symbol g = global;
        while (g instanceof GlobalAlias) {
            g = aliases.get(g);
        }

        if (g instanceof GlobalValueSymbol) {
            final GlobalValueSymbol variable = (GlobalValueSymbol) g;
            return globals.computeIfAbsent(variable, k -> allocateGlobal(variable));
        } else {
            return symbolResolver.resolve(g);
        }
    }

    private List<RootCallTarget> getConstructors() {
        return getStructor(CONSTRUCTORS_VARNAME, ASCENDING_PRIORITY);
    }

    private List<RootCallTarget> getDestructors() {
        return getStructor(DESTRUCTORS_VARNAME, DESCENDING_PRIORITY);
    }

    private List<RootCallTarget> getStructor(String name, Comparator<Pair<Integer, ?>> priorityComparator) {
        for (GlobalValueSymbol globalValueSymbol : globals.keySet()) {
            if (globalValueSymbol.getName().equals(name)) {
                final LLVMExpressionNode[] targets = resolveStructor(globalValueSymbol, priorityComparator);
                final RootCallTarget constructorFunctionsRootCallTarget = Truffle.getRuntime().createCallTarget(nodeFactory.createStaticInitsRootNode(this, targets));
                return Collections.singletonList(constructorFunctionsRootCallTarget);
            }
        }
        return Collections.emptyList();
    }

    private LLVMExpressionNode[] resolveStructor(GlobalValueSymbol globalVar, Comparator<Pair<Integer, ?>> priorityComparator) {
        final Object globalVariableDescriptor = globalVariableScope.get(globalVar.getName());
        final ArrayConstant arrayConstant = (ArrayConstant) globalVar.getValue();
        final int elemCount = arrayConstant.getElementCount();

        final StructureType elementType = (StructureType) arrayConstant.getType().getElementType();
        final int structSize = getByteSize(elementType);

        final FunctionType functionType = (FunctionType) ((PointerType) elementType.getElementType(1)).getPointeeType();
        final int indexedTypeLength = getByteAlignment(functionType);

        final ArrayList<Pair<Integer, LLVMExpressionNode>> structors = new ArrayList<>(elemCount);
        for (int i = 0; i < elemCount; i++) {
            final LLVMExpressionNode globalVarAddress = nodeFactory.createLiteral(this, globalVariableDescriptor, new PointerType(globalVar.getType()));
            final LLVMExpressionNode iNode = nodeFactory.createLiteral(this, i, PrimitiveType.I32);
            final LLVMExpressionNode structPointer = nodeFactory.createTypedElementPointer(this, globalVarAddress, iNode, structSize, elementType);
            final LLVMExpressionNode loadedStruct = nodeFactory.createLoad(this, elementType, structPointer);

            final LLVMExpressionNode oneLiteralNode = nodeFactory.createLiteral(this, 1, PrimitiveType.I32);
            final LLVMExpressionNode functionLoadTarget = nodeFactory.createTypedElementPointer(this, loadedStruct, oneLiteralNode, indexedTypeLength, functionType);
            final LLVMExpressionNode loadedFunction = nodeFactory.createLoad(this, functionType, functionLoadTarget);
            final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{nodeFactory.createFrameRead(this, PrimitiveType.I64, stack.getRootFrame().findFrameSlot(LLVMStack.FRAME_ID))};
            final LLVMExpressionNode functionCall = nodeFactory.createFunctionCall(this, loadedFunction, argNodes, functionType, null);

            final StructureConstant structorDefinition = (StructureConstant) arrayConstant.getElement(i);
            final Symbol prioritySymbol = structorDefinition.getElement(0);
            final Integer priority = LLVMSymbolResolver.evaluateIntegerConstant(prioritySymbol);
            structors.add(new Pair<>(priority != null ? priority : LEAST_CONSTRUCTOR_PRIORITY, functionCall));
        }

        return structors.stream().sorted(priorityComparator).map(Pair::getSecond).toArray(LLVMExpressionNode[]::new);
    }

    private LLVMExpressionNode allocateGlobal(GlobalValueSymbol global) {
        final Object globalValue;
        if (global instanceof GlobalVariable) {
            globalValue = nodeFactory.allocateGlobalVariable(this, (GlobalVariable) global);
        } else if (global instanceof GlobalConstant) {
            globalValue = nodeFactory.allocateGlobalConstant(this, (GlobalConstant) global);
        } else {
            throw new AssertionError("Cannot allocate global: " + global);
        }

        globalVariableScope.put(global.getName(), globalValue);
        return nodeFactory.createLiteral(this, globalValue, new PointerType(global.getType()));
    }

    private List<LLVMExpressionNode> createGlobalVariableNodes(LLVMSymbolResolver symbolResolver) {
        final List<LLVMExpressionNode> globalNodes = new ArrayList<>();
        for (GlobalValueSymbol global : globals.keySet()) {
            final LLVMExpressionNode store = createGlobal(symbolResolver, global);
            if (store != null) {
                globalNodes.add(store);
            }
        }
        return globalNodes;
    }

    private LLVMExpressionNode createGlobal(LLVMSymbolResolver symbolResolver, GlobalValueSymbol global) {
        if (global == null || global.getValue() == null) {
            return null;
        }

        LLVMExpressionNode constant = symbolResolver.resolve(global.getValue());
        if (constant != null) {
            final Type type = ((PointerType) global.getType()).getPointeeType();
            final int size = getByteSize(type);

            final LLVMExpressionNode globalVarAddress = getGlobalVariable(symbolResolver, global);

            if (size != 0) {
                final LLVMExpressionNode store;
                if (type instanceof ArrayType || type instanceof StructureType) {
                    store = nodeFactory.createStore(this, globalVarAddress, constant, type, null);
                } else {
                    final Type t = global.getValue().getType();
                    store = nodeFactory.createStore(this, globalVarAddress, constant, t, null);
                }
                return store;
            }
        }

        return null;
    }

    public LLVMContext getContext() {
        return context;
    }

    private LLVMExpressionNode[] getDeallocations() {
        return deallocations.toArray(new LLVMExpressionNode[deallocations.size()]);
    }

    public LLVMExpressionNode allocateFunctionLifetime(Type type, int size, int alignment) {
        return nodeFactory.createAlloc(this, type, size, alignment, null, null);
    }

    public Object getGlobalAddress(LLVMSymbolResolver symbolResolver, GlobalValueSymbol var) {
        return getGlobalVariable(symbolResolver, var);
    }

    public int getByteAlignment(Type type) {
        return type.getAlignment(targetDataLayout);
    }

    public int getByteSize(Type type) {
        return type.getSize(targetDataLayout);
    }

    public int getBytePadding(int offset, Type type) {
        return Type.getPadding(offset, type, targetDataLayout);
    }

    public int getIndexOffset(int index, AggregateType type) {
        return type.getOffsetOf(index, targetDataLayout);
    }

    public FrameDescriptor getGlobalFrameDescriptor() {
        return stack.getRootFrame();
    }

    public void addDestructor(LLVMExpressionNode destructorNode) {
        deallocations.add(destructorNode);
    }

    public long getNativeHandle(String name) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            return ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), context.getNativeLookup().getNativeDataObject(name));
        } catch (UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    public LLVMNativeFunctions getNativeFunctions() {
        return context.getNativeFunctions();
    }

    public SulongNodeFactory getNodeFactory() {
        return nodeFactory;
    }

    public LLVMLanguage getLanguage() {
        return language;
    }

    SourceSection getSourceSection(FunctionDefinition function) {
        return sourceSectionGenerator.getOrDefault(function, source);
    }

    SourceSection getSourceSection(Instruction instruction) {
        return sourceSectionGenerator.getOrDefault(instruction);
    }
}
