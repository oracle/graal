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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.parser.util.Pair;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
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

    public static LLVMParserResult parse(Source source, BitcodeParserResult parserResult, LLVMLanguage language, LLVMContext context, NodeFactory nodeFactory) {
        ModelModule model = parserResult.getModel();
        StackAllocation stack = parserResult.getStackAllocation();
        LLVMPhiManager phiManager = parserResult.getPhis();
        LLVMLabelList labels = parserResult.getLabels();
        TargetDataLayout layout = model.getTargetDataLayout();
        assert layout != null;

        LLVMModelVisitor module = new LLVMModelVisitor();
        model.accept(module);

        DataLayoutConverter.DataSpecConverterImpl targetDataLayout = DataLayoutConverter.getConverter(layout.getDataLayout());
        context.setDataLayoutConverter(targetDataLayout);
        LLVMParserRuntime runtime = new LLVMParserRuntime(source, language, context, stack, nodeFactory, module.getAliases());

        runtime.initializeFunctions(phiManager, labels, module.getFunctions());

        LLVMSymbolReadResolver symbolResolver = new LLVMSymbolReadResolver(runtime, labels);
        LLVMExpressionNode[] globals = runtime.createGlobalVariableInitializationNodes(symbolResolver, module.getGlobals());
        RootNode globalVarInits = nodeFactory.createStaticInitsRootNode(runtime, globals);
        RootCallTarget globalVarInitsTarget = Truffle.getRuntime().createCallTarget(globalVarInits);
        LLVMExpressionNode[] deallocs = runtime.getDeallocations();
        RootNode globalVarDeallocs = nodeFactory.createStaticInitsRootNode(runtime, deallocs);
        RootCallTarget globalVarDeallocsTarget = Truffle.getRuntime().createCallTarget(globalVarDeallocs);

        RootCallTarget constructorFunctions = runtime.getConstructors(module.getGlobals());
        RootCallTarget destructorFunctions = runtime.getDestructors(module.getGlobals());

        Map<LLVMSourceSymbol, GlobalValueSymbol> sourceGlobals = parserResult.getSourceModel().getGlobals();
        if (!sourceGlobals.isEmpty() && context.getEnv().getOptions().get(SulongEngineOption.ENABLE_LVI)) {
            LLVMSourceContext sourceContext = context.getSourceContext();
            for (LLVMSourceSymbol symbol : sourceGlobals.keySet()) {
                final LLVMExpressionNode symbolNode = runtime.getGlobalVariable(symbolResolver, sourceGlobals.get(symbol));
                final LLVMDebugValue value = nodeFactory.createGlobalVariableDebug(symbol, symbolNode);
                sourceContext.registerGlobal(symbol, value);
            }
        }

        RootCallTarget mainFunctionCallTarget;
        if (runtime.getScope().functionExists("@main")) {
            LLVMFunctionDescriptor mainDescriptor = runtime.getScope().getFunctionDescriptor("@main");
            LLVMFunctionDescriptor startDescriptor = runtime.getScope().getFunctionDescriptor("@_start");
            RootCallTarget startCallTarget = startDescriptor.getLLVMIRFunction();
            RootNode globalFunction = nodeFactory.createGlobalRootNode(runtime, startCallTarget, source, mainDescriptor.getType().getArgumentTypes());
            RootCallTarget globalFunctionRoot = Truffle.getRuntime().createCallTarget(globalFunction);
            RootNode globalRootNode = nodeFactory.createGlobalRootNodeWrapping(runtime, globalFunctionRoot, startDescriptor.getType().getReturnType());
            mainFunctionCallTarget = Truffle.getRuntime().createCallTarget(globalRootNode);
        } else {
            mainFunctionCallTarget = null;
        }
        return new LLVMParserResult(mainFunctionCallTarget, globalVarInitsTarget, globalVarDeallocsTarget, constructorFunctions, destructorFunctions);
    }

    private final Source source;
    private final LLVMLanguage language;
    private final LLVMContext context;
    private final StackAllocation stack;
    private final NodeFactory nodeFactory;
    private final Map<GlobalAlias, Symbol> aliases;
    private final List<LLVMExpressionNode> deallocations;
    private final LLVMScope scope;

    private LLVMParserRuntime(Source source, LLVMLanguage language, LLVMContext context, StackAllocation stack, NodeFactory nodeFactory,
                    Map<GlobalAlias, Symbol> aliases) {
        this.source = source;
        this.context = context;
        this.stack = stack;
        this.nodeFactory = nodeFactory;
        this.language = language;
        this.aliases = aliases;
        this.deallocations = new ArrayList<>();
        this.scope = LLVMScope.createFileScope(context);
    }

    private void initializeFunctions(LLVMPhiManager phiManager, LLVMLabelList labels, List<FunctionDefinition> functions) {
        for (FunctionDefinition function : functions) {
            String functionName = function.getName();
            LLVMFunctionDescriptor functionDescriptor = scope.lookupOrCreateFunction(context, functionName, !Linkage.isFileLocal(function.getLinkage()),
                            index -> LLVMFunctionDescriptor.createDescriptor(context, functionName, function.getType(), index));
            LazyToTruffleConverterImpl lazyConverter = new LazyToTruffleConverterImpl(this, context, nodeFactory, function, source, stack.getFrame(functionName), phiManager.getPhiMap(functionName),
                            labels.labels(functionName));
            functionDescriptor.declareInSulong(lazyConverter, Linkage.isWeak(function.getLinkage()));
        }
    }

    private LLVMExpressionNode[] createGlobalVariableInitializationNodes(LLVMSymbolReadResolver symbolResolver, List<GlobalValueSymbol> globals) {
        final List<LLVMExpressionNode> globalNodes = new ArrayList<>();
        for (GlobalValueSymbol global : globals) {
            final LLVMExpressionNode store = createGlobalInitialization(symbolResolver, global);
            if (store != null) {
                globalNodes.add(store);
            }
        }
        return globalNodes.toArray(new LLVMExpressionNode[globalNodes.size()]);
    }

    private LLVMExpressionNode createGlobalInitialization(LLVMSymbolReadResolver symbolResolver, GlobalValueSymbol global) {
        if (global == null || global.getValue() == null) {
            return null;
        }

        LLVMExpressionNode constant = symbolResolver.resolve(global.getValue());
        if (constant != null) {
            final Type type = ((PointerType) global.getType()).getPointeeType();
            final int size = getContext().getByteSize(type);

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

    private LLVMExpressionNode getGlobalVariable(LLVMSymbolReadResolver symbolResolver, GlobalValueSymbol global) {
        Symbol g = global;
        while (g instanceof GlobalAlias) {
            g = aliases.get(g);
        }

        if (g instanceof GlobalValueSymbol) {
            final GlobalValueSymbol variable = (GlobalValueSymbol) g;
            Object globalVariableDescriptor = scope.lookupOrCreateGlobal(variable.getName(), !Linkage.isFileLocal(variable.getLinkage()), () -> {
                final Object globalValue;
                if (global instanceof GlobalVariable) {
                    globalValue = nodeFactory.allocateGlobalVariable(this, (GlobalVariable) global);
                } else if (global instanceof GlobalConstant) {
                    globalValue = nodeFactory.allocateGlobalConstant(this, (GlobalConstant) global);
                } else {
                    throw new AssertionError("Cannot allocate global: " + global);
                }
                return globalValue;
            });
            return nodeFactory.createLiteral(this, globalVariableDescriptor, new PointerType(variable.getType()));
        } else {
            return symbolResolver.resolve(g);
        }
    }

    private RootCallTarget getConstructors(List<GlobalValueSymbol> globals) {
        return getStructor(CONSTRUCTORS_VARNAME, globals, ASCENDING_PRIORITY);
    }

    private RootCallTarget getDestructors(List<GlobalValueSymbol> globals) {
        return getStructor(DESTRUCTORS_VARNAME, globals, DESCENDING_PRIORITY);
    }

    private RootCallTarget getStructor(String name, List<GlobalValueSymbol> globals, Comparator<Pair<Integer, ?>> priorityComparator) {
        for (GlobalValueSymbol globalValueSymbol : globals) {
            if (globalValueSymbol.getName().equals(name)) {
                final LLVMExpressionNode[] targets = resolveStructor(globalValueSymbol, priorityComparator);
                final RootCallTarget constructorFunctionsRootCallTarget = Truffle.getRuntime().createCallTarget(nodeFactory.createStaticInitsRootNode(this, targets));
                return constructorFunctionsRootCallTarget;
            }
        }
        return null;
    }

    private LLVMExpressionNode[] resolveStructor(GlobalValueSymbol globalVar, Comparator<Pair<Integer, ?>> priorityComparator) {
        final Object globalVariableDescriptor = scope.getGlobalVariable(globalVar.getName());
        final ArrayConstant arrayConstant = (ArrayConstant) globalVar.getValue();
        final int elemCount = arrayConstant.getElementCount();

        final StructureType elementType = (StructureType) arrayConstant.getType().getElementType();
        final int structSize = getContext().getByteSize(elementType);

        final FunctionType functionType = (FunctionType) ((PointerType) elementType.getElementType(1)).getPointeeType();
        final int indexedTypeLength = getContext().getByteAlignment(functionType);

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
            final Integer priority = LLVMSymbolReadResolver.evaluateIntegerConstant(prioritySymbol);
            structors.add(new Pair<>(priority != null ? priority : LEAST_CONSTRUCTOR_PRIORITY, functionCall));
        }

        return structors.stream().sorted(priorityComparator).map(Pair::getSecond).toArray(LLVMExpressionNode[]::new);
    }

    public LLVMContext getContext() {
        return context;
    }

    private LLVMExpressionNode[] getDeallocations() {
        return deallocations.toArray(new LLVMExpressionNode[deallocations.size()]);
    }

    public LLVMExpressionNode allocateFunctionLifetime(Type type, int size, int alignment) {
        return nodeFactory.createAlloca(this, type, size, alignment);
    }

    public LLVMExpressionNode getGlobalAddress(LLVMSymbolReadResolver symbolResolver, GlobalValueSymbol var) {
        return getGlobalVariable(symbolResolver, var);
    }

    public FrameDescriptor getGlobalFrameDescriptor() {
        return stack.getRootFrame();
    }

    public void addDestructor(LLVMExpressionNode destructorNode) {
        deallocations.add(destructorNode);
    }

    public NodeFactory getNodeFactory() {
        return nodeFactory;
    }

    public LLVMLanguage getLanguage() {
        return language;
    }

    public LLVMScope getScope() {
        return scope;
    }
}
