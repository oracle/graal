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
import java.util.Optional;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.parser.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInstruction;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.ReducedInstructionVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolResolver;
import com.oracle.truffle.llvm.parser.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.parser.util.LLVMParserAsserts;
import com.oracle.truffle.llvm.parser.util.Pair;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeapFunctions;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.LLVMBaseType;
import com.oracle.truffle.llvm.runtime.types.LLVMType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class LLVMParserRuntime {

    public static LLVMParserResult parse(Source source, LLVMContext context, NodeFactoryFacade factoryFacade) {
        final BitcodeParserResult parserResult = BitcodeParserResult.getFromSource(source);
        final ModelModule model = parserResult.getModel();
        final StackAllocation stackAllocation = parserResult.getStackAllocation();
        final TargetDataLayout layout = model.getTargetDataLayout();
        assert layout != null;
        final DataLayoutConverter.DataSpecConverterImpl targetDataLayout = DataLayoutConverter.getConverter(layout.getDataLayout());

        final LLVMFunctionRegistry functionRegistry = new LLVMFunctionRegistry(context, factoryFacade);

        final LLVMParserRuntime visitor = new LLVMParserRuntime(source, context, stackAllocation, parserResult.getLabels(), parserResult.getPhis(), targetDataLayout, factoryFacade,
                        functionRegistry);
        final LLVMModelVisitor module = new LLVMModelVisitor(visitor, functionRegistry);
        model.accept(module);

        LLVMFunctionDescriptor mainFunction = visitor.getFunction("@main");

        LLVMExpressionNode[] globals = visitor.getGobalVariables().toArray(new LLVMExpressionNode[0]);
        RootNode globalVarInits = factoryFacade.createStaticInitsRootNode(visitor, globals);
        RootCallTarget globalVarInitsTarget = Truffle.getRuntime().createCallTarget(globalVarInits);
        LLVMExpressionNode[] deallocs = visitor.getDeallocations();
        RootNode globalVarDeallocs = factoryFacade.createStaticInitsRootNode(visitor, deallocs);
        RootCallTarget globalVarDeallocsTarget = Truffle.getRuntime().createCallTarget(globalVarDeallocs);

        final List<RootCallTarget> constructorFunctions = visitor.getConstructors();
        final List<RootCallTarget> destructorFunctions = visitor.getDestructors();

        final RootCallTarget mainFunctionCallTarget;
        if (mainFunction != null) {
            final RootCallTarget mainCallTarget = mainFunction.getCallTarget();
            final RootNode globalFunction = factoryFacade.createGlobalRootNode(visitor, mainCallTarget, context.getMainArguments(), source, mainFunction.getParameterTypes());
            final RootCallTarget globalFunctionRoot = Truffle.getRuntime().createCallTarget(globalFunction);
            final RootNode globalRootNode = factoryFacade.createGlobalRootNodeWrapping(visitor, globalFunctionRoot, mainFunction.getReturnType());
            mainFunctionCallTarget = Truffle.getRuntime().createCallTarget(globalRootNode);
        } else {
            mainFunctionCallTarget = null;
        }
        return new LLVMParserResult(mainFunctionCallTarget, globalVarInitsTarget, globalVarDeallocsTarget, constructorFunctions, destructorFunctions);
    }

    private final LLVMContext context;

    private final LLVMLabelList labels;

    private final LLVMPhiManager phis;

    private final List<LLVMExpressionNode> deallocations = new ArrayList<>();

    private final Map<GlobalAlias, Symbol> aliases = new HashMap<>();

    private final Map<String, LLVMFunctionDescriptor> functions = new HashMap<>();

    private final Map<GlobalValueSymbol, LLVMExpressionNode> globals = new HashMap<>();

    private final DataLayoutConverter.DataSpecConverterImpl targetDataLayout;

    private final NodeFactoryFacade factoryFacade;

    private final Source source;

    private final StackAllocation stack;

    private final LLVMSymbolResolver symbolResolver;

    private final LLVMFunctionRegistry functionRegistry;

    private LLVMParserRuntime(Source source, LLVMContext context, StackAllocation stack, LLVMLabelList labels, LLVMPhiManager phis,
                    DataLayoutConverter.DataSpecConverterImpl layout, NodeFactoryFacade factoryFacade, LLVMFunctionRegistry functionRegistry) {
        this.source = source;
        this.context = context;
        this.stack = stack;
        this.labels = labels;
        this.phis = phis;
        this.targetDataLayout = layout;
        this.factoryFacade = factoryFacade;
        this.functionRegistry = functionRegistry;
        this.symbolResolver = new LLVMSymbolResolver(labels, this);
    }

    LLVMExpressionNode createFunction(FunctionDefinition method, LLVMLifetimeAnalysis lifetimes) {
        String functionName = method.getName();

        LLVMBitcodeFunctionVisitor visitor = new LLVMBitcodeFunctionVisitor(
                        this,
                        stack.getFrame(functionName),
                        labels.labels(functionName),
                        phis.getPhiMap(functionName),
                        factoryFacade,
                        method.getParameters().size(),
                        symbolResolver,
                        method);

        initFunction(visitor);

        method.accept(visitor);

        final LLVMStackFrameNuller[][] slotNullerBeginNodes = getSlotNuller(method, lifetimes.getNullableBefore());
        final LLVMStackFrameNuller[][] slotNullerAfterNodes = getSlotNuller(method, lifetimes.getNullableAfter());
        return factoryFacade.createFunctionBlockNode(this, visitor.getReturnSlot(), visitor.getBlocks(), slotNullerBeginNodes, slotNullerAfterNodes);
    }

    private LLVMStackFrameNuller[][] getSlotNuller(FunctionDefinition method, Map<InstructionBlock, FrameSlot[]> slots) {
        final LLVMStackFrameNuller[][] indexToSlotNuller = new LLVMStackFrameNuller[method.getBlockCount()][];
        for (int j = 0; j < method.getBlockCount(); j++) {
            final InstructionBlock block = method.getBlock(j);
            final FrameSlot[] deadSlots = slots.get(block);
            if (deadSlots != null) {
                LLVMParserAsserts.assertNoNullElement(deadSlots);
                indexToSlotNuller[j] = getSlotNullerNode(deadSlots, method);
            }
        }
        return indexToSlotNuller;
    }

    private LLVMStackFrameNuller[] getSlotNullerNode(FrameSlot[] deadSlots, FunctionDefinition method) {
        if (deadSlots == null) {
            return new LLVMStackFrameNuller[0];
        }
        final LLVMStackFrameNuller[] nullers = new LLVMStackFrameNuller[deadSlots.length];
        int i = 0;
        for (FrameSlot slot : deadSlots) {
            nullers[i++] = getNullerNode(slot, method);
        }
        LLVMParserAsserts.assertNoNullElement(nullers);
        return nullers;
    }

    private LLVMStackFrameNuller getNullerNode(FrameSlot slot, FunctionDefinition method) {
        final String identifier = (String) slot.getIdentifier();
        final LLVMType type = findType(method, identifier);
        return factoryFacade.createFrameNuller(this, identifier, type, slot);
    }

    boolean needsStackPointerArgument() {
        Optional<Boolean> hasStackPointerArgument = factoryFacade.hasStackPointerArgument(this);
        return hasStackPointerArgument.isPresent() && hasStackPointerArgument.get();
    }

    List<LLVMExpressionNode> createParameters(FrameDescriptor frame, FunctionDefinition method) {
        final List<FunctionParameter> parameters = method.getParameters();
        final List<LLVMExpressionNode> formalParamInits = new ArrayList<>();
        if (needsStackPointerArgument()) {
            final LLVMExpressionNode stackPointerNode = factoryFacade.createFunctionArgNode(0, LLVMBaseType.ADDRESS);
            formalParamInits.add(factoryFacade.createFrameWrite(this, LLVMBaseType.ADDRESS, stackPointerNode, frame.findFrameSlot(LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID)));
        }

        final Optional<Integer> argStartIndex = factoryFacade.getArgStartIndex();
        if (!argStartIndex.isPresent()) {
            throw new IllegalStateException("Cannot find Argument Start Index!");
        }
        int argIndex = argStartIndex.get();
        if (method.getReturnType() instanceof StructureType) {
            final LLVMExpressionNode functionReturnParameterNode = factoryFacade.createFunctionArgNode(argIndex++, LLVMBaseType.STRUCT);
            final FrameSlot returnSlot = frame.findOrAddFrameSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
            final LLVMBaseType baseType = method.getReturnType().getLLVMBaseType();
            final LLVMExpressionNode returnValue = factoryFacade.createFrameWrite(this, baseType, functionReturnParameterNode, returnSlot);
            formalParamInits.add(returnValue);
        }
        for (final FunctionParameter parameter : parameters) {
            final LLVMBaseType paramType = parameter.getType().getLLVMBaseType();
            final LLVMExpressionNode parameterNode = factoryFacade.createFunctionArgNode(argIndex++, paramType);
            final FrameSlot slot = frame.findFrameSlot(parameter.getName());
            formalParamInits.add(factoryFacade.createFrameWrite(this, paramType, parameterNode, slot));
        }
        return formalParamInits;
    }

    private LLVMExpressionNode createGlobal(GlobalValueSymbol global) {
        if (global == null || global.getValue() == null) {
            return null;
        }

        LLVMExpressionNode constant = symbolResolver.resolve(global.getValue());
        if (constant != null) {
            final Type type = ((PointerType) global.getType()).getPointeeType();
            final LLVMBaseType baseType = type.getLLVMBaseType();
            final int size = getByteSize(type);

            final LLVMExpressionNode globalVarAddress = getGlobalVariable(global);

            if (size != 0) {
                final LLVMExpressionNode store;
                if (baseType == LLVMBaseType.ARRAY || baseType == LLVMBaseType.STRUCT) {
                    store = factoryFacade.createStore(this, globalVarAddress, constant, type);
                } else {
                    final Type t = global.getValue().getType();
                    store = factoryFacade.createStore(this, globalVarAddress, constant, t);
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

    private LLVMFunctionDescriptor getFunction(String name) {
        return functions.get(name);
    }

    void addFunction(LLVMFunctionDescriptor function) {
        functions.put(function.getName(), function);
    }

    private LLVMExpressionNode getGlobalVariable(GlobalValueSymbol global) {
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

    private static final String CONSTRUCTORS_VARNAME = "@llvm.global_ctors";

    private static final Comparator<Pair<Integer, ?>> ASCENDING_PRIORITY = (p1, p2) -> p1.getFirst() >= p2.getFirst() ? 1 : -1;

    private List<RootCallTarget> getConstructors() {
        return getStructor(CONSTRUCTORS_VARNAME, ASCENDING_PRIORITY);
    }

    private static final String DESTRUCTORS_VARNAME = "@llvm.global_dtors";

    private static final Comparator<Pair<Integer, ?>> DESCENDING_PRIORITY = (p1, p2) -> p1.getFirst() < p2.getFirst() ? 1 : -1;

    private List<RootCallTarget> getDestructors() {
        return getStructor(DESTRUCTORS_VARNAME, DESCENDING_PRIORITY);
    }

    private List<RootCallTarget> getStructor(String name, Comparator<Pair<Integer, ?>> priorityComparator) {
        for (GlobalValueSymbol globalValueSymbol : globals.keySet()) {
            if (globalValueSymbol.getName().equals(name)) {
                final LLVMExpressionNode[] targets = resolveStructor(globalValueSymbol, priorityComparator);
                final RootCallTarget constructorFunctionsRootCallTarget = Truffle.getRuntime().createCallTarget(factoryFacade.createStaticInitsRootNode(this, targets));
                return Collections.singletonList(constructorFunctionsRootCallTarget);
            }
        }
        return Collections.emptyList();
    }

    private static final int LEAST_CONSTRUCTOR_PRIORITY = 65535;

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
            final LLVMExpressionNode globalVarAddress = factoryFacade.createLiteral(this, globalVariableDescriptor, LLVMBaseType.ADDRESS);
            final LLVMExpressionNode iNode = factoryFacade.createLiteral(this, i, LLVMBaseType.I32);
            final LLVMExpressionNode structPointer = factoryFacade.createTypedElementPointer(this, LLVMBaseType.I32, globalVarAddress, iNode, structSize, elementType);
            final LLVMExpressionNode loadedStruct = factoryFacade.createLoad(this, elementType, structPointer);

            final LLVMExpressionNode oneLiteralNode = factoryFacade.createLiteral(this, 1, LLVMBaseType.I32);
            final LLVMExpressionNode functionLoadTarget = factoryFacade.createTypedElementPointer(this, LLVMBaseType.I32, loadedStruct, oneLiteralNode, indexedTypeLength, functionType);
            final LLVMExpressionNode loadedFunction = factoryFacade.createLoad(this, functionType, functionLoadTarget);
            final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{factoryFacade.createFrameRead(this, LLVMBaseType.ADDRESS, getStackPointerSlot())};
            final Type[] argTypes = new Type[]{new PointerType(null)};
            final LLVMExpressionNode functionCall = factoryFacade.createFunctionCall(this, loadedFunction, argNodes, argTypes, LLVMBaseType.VOID);

            final StructureConstant structorDefinition = (StructureConstant) arrayConstant.getElement(i);
            final Symbol prioritySymbol = structorDefinition.getElement(0);
            final Integer priority = LLVMSymbolResolver.evaluateIntegerConstant(prioritySymbol);
            structors.add(new Pair<>(priority != null ? priority : LEAST_CONSTRUCTOR_PRIORITY, functionCall));
        }

        return structors.stream().sorted(priorityComparator).map(Pair::getSecond).toArray(LLVMExpressionNode[]::new);
    }

    private final Map<String, Object> globalVariableScope = new HashMap<>();

    private LLVMExpressionNode allocateGlobal(GlobalValueSymbol global) {

        final Object globalValue;
        if (global instanceof GlobalVariable) {
            globalValue = factoryFacade.allocateGlobalVariable(this, (GlobalVariable) global);
        } else if (global instanceof GlobalConstant) {
            globalValue = factoryFacade.allocateGlobalConstant(this, (GlobalConstant) global);
        } else {
            throw new AssertionError("Cannot allocate global: " + global);
        }

        globalVariableScope.put(global.getName(), globalValue);
        return factoryFacade.createLiteral(this, globalValue, LLVMBaseType.ADDRESS);
    }

    private List<LLVMExpressionNode> getGobalVariables() {
        final List<LLVMExpressionNode> globalNodes = new ArrayList<>();
        for (GlobalValueSymbol global : this.globals.keySet()) {
            final LLVMExpressionNode store = createGlobal(global);
            if (store != null) {
                globalNodes.add(store);
            }
        }
        return globalNodes;
    }

    private static LLVMType findType(FunctionDefinition method, String identifier) {
        final Type methodType = method.getType(identifier);
        if (methodType != null) {
            return methodType.getLLVMType();
        } else if (LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID.equals(identifier)) {
            return method.getReturnType().getLLVMType();
        } else if (LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID.equals(identifier)) {
            return new LLVMType(LLVMBaseType.ADDRESS);
        } else {
            throw new IllegalStateException("Cannot find Instruction with name: " + identifier);
        }
    }

    private LLVMBitcodeFunctionVisitor functionVisitor = null;

    private final Map<String, Type> nameToTypeMapping = new HashMap<>();

    private final InstructionVisitor nameToTypeMappingVisitor = new ReducedInstructionVisitor() {
        @Override
        public void visitValueInstruction(ValueInstruction valueInstruction) {
            nameToTypeMapping.put(valueInstruction.getName(), valueInstruction.getType());
        }

        @Override
        public void visitVoidInstruction(VoidInstruction voidInstruction) {

        }
    };

    private void initFunction(LLVMBitcodeFunctionVisitor visitor) {
        this.functionVisitor = visitor;
        nameToTypeMapping.clear();
        if (visitor != null) {
            for (int i = 0; i < visitor.getFunction().getBlockCount(); i++) {
                visitor.getFunction().getBlock(i).accept(nameToTypeMappingVisitor);
            }
            visitor.getFunction().getParameters().forEach(p -> nameToTypeMapping.put(p.getName(), p.getType()));
        }
    }

    void exitFunction() {
        this.functionVisitor = null;
        nameToTypeMapping.clear();
    }

    public LLVMExpressionNode allocateFunctionLifetime(Type type, int size, int alignment) {
        return factoryFacade.createAlloc(this, type, size, alignment, null, null);
    }

    public FrameSlot getReturnSlot() {
        if (functionVisitor != null) {
            return functionVisitor.getReturnSlot();
        }
        throw new IllegalStateException("There is currently no active function visitor set");
    }

    public Object getGlobalAddress(GlobalValueSymbol var) {
        return getGlobalVariable(var);
    }

    public FrameSlot getStackPointerSlot() {
        return functionVisitor != null ? functionVisitor.getStackSlot() : stack.getRootStackSlot();
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

    public int getIndexOffset(int index, Type type) {
        return type.getIndexOffset(index, targetDataLayout);
    }

    public FrameDescriptor getGlobalFrameDescriptor() {
        return stack.getRootFrame();
    }

    public FrameDescriptor getMethodFrameDescriptor() {
        return functionVisitor.getFrame();
    }

    public void addDestructor(LLVMExpressionNode destructorNode) {
        deallocations.add(destructorNode);
    }

    public long getNativeHandle(String name) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            return (long) ForeignAccess.sendUnbox(Message.UNBOX.createNode(), context.getNativeData(name));
        } catch (UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Type> getVariableNameTypesMapping() {
        return Collections.unmodifiableMap(nameToTypeMapping);
    }

    public LLVMHeapFunctions getHeapFunctions() {
        return context.getHeapFunctions();
    }

    public NodeFactoryFacade getNodeFactoryFacade() {
        return factoryFacade;
    }

    Map<GlobalAlias, Symbol> getAliases() {
        return aliases;
    }

    Map<GlobalValueSymbol, LLVMExpressionNode> getGlobals() {
        return globals;
    }

    StackAllocation getStack() {
        return stack;
    }

    Source getSource() {
        return source;
    }

    LLVMPhiManager getPhis() {
        return phis;
    }

    public LLVMFunctionRegistry getLLVMFunctionRegistry() {
        return functionRegistry;
    }
}
