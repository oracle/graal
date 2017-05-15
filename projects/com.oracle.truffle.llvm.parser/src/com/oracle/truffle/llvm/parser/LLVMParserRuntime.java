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
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.metadata.SourceSectionGenerator;
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
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.model.visitors.ValueInstructionVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolResolver;
import com.oracle.truffle.llvm.parser.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.parser.util.LLVMParserAsserts;
import com.oracle.truffle.llvm.parser.util.Pair;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller;
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

    public static LLVMParserResult parse(Source source, LLVMLanguage language, LLVMContext context, SulongNodeFactory nodeFactory) {
        final BitcodeParserResult parserResult = BitcodeParserResult.getFromSource(source);
        final ModelModule model = parserResult.getModel();
        final StackAllocation stackAllocation = parserResult.getStackAllocation();
        final TargetDataLayout layout = model.getTargetDataLayout();
        assert layout != null;
        final DataLayoutConverter.DataSpecConverterImpl targetDataLayout = DataLayoutConverter.getConverter(layout.getDataLayout());

        final LLVMParserRuntime visitor = new LLVMParserRuntime(source, language, context, stackAllocation, parserResult.getLabels(), parserResult.getPhis(), targetDataLayout, nodeFactory);
        final LLVMModelVisitor module = new LLVMModelVisitor(visitor);

        if (!LLVMLogger.TARGET_NONE.equals(LLVMOptions.DEBUG.printMetadata())) {
            model.getMetadata().print(LLVMLogger.print(LLVMOptions.DEBUG.printMetadata()));
        }

        model.accept(module);

        LLVMFunctionDescriptor mainFunction = visitor.getFunction("@main");

        LLVMExpressionNode[] globals = visitor.getGobalVariables().toArray(new LLVMExpressionNode[0]);
        RootNode globalVarInits = nodeFactory.createStaticInitsRootNode(visitor, globals);
        RootCallTarget globalVarInitsTarget = Truffle.getRuntime().createCallTarget(globalVarInits);
        LLVMExpressionNode[] deallocs = visitor.getDeallocations();
        RootNode globalVarDeallocs = nodeFactory.createStaticInitsRootNode(visitor, deallocs);
        RootCallTarget globalVarDeallocsTarget = Truffle.getRuntime().createCallTarget(globalVarDeallocs);

        final List<RootCallTarget> constructorFunctions = visitor.getConstructors();
        final List<RootCallTarget> destructorFunctions = visitor.getDestructors();

        final RootCallTarget mainFunctionCallTarget;
        if (mainFunction != null) {
            final RootCallTarget mainCallTarget = mainFunction.getLLVMIRFunction();
            final RootNode globalFunction = nodeFactory.createGlobalRootNode(visitor, mainCallTarget, context.getMainArguments(), source, mainFunction.getType().getArgumentTypes());
            final RootCallTarget globalFunctionRoot = Truffle.getRuntime().createCallTarget(globalFunction);
            final RootNode globalRootNode = nodeFactory.createGlobalRootNodeWrapping(visitor, globalFunctionRoot, mainFunction.getType().getReturnType());
            mainFunctionCallTarget = Truffle.getRuntime().createCallTarget(globalRootNode);
        } else {
            mainFunctionCallTarget = null;
        }
        return new LLVMParserResult(mainFunctionCallTarget, globalVarInitsTarget, globalVarDeallocsTarget, constructorFunctions, destructorFunctions);
    }

    private final LLVMContext context;

    private final LLVMLanguage language;

    private final LLVMLabelList labels;

    private final LLVMPhiManager phis;

    private final List<LLVMExpressionNode> deallocations = new ArrayList<>();

    private final Map<GlobalAlias, Symbol> aliases = new HashMap<>();

    private final Map<String, LLVMFunctionDescriptor> functions = new HashMap<>();

    private final Map<GlobalValueSymbol, LLVMExpressionNode> globals = new HashMap<>();

    private final DataLayoutConverter.DataSpecConverterImpl targetDataLayout;

    private final SulongNodeFactory nodeFactory;

    private final Source source;

    private final StackAllocation stack;

    private final LLVMSymbolResolver symbolResolver;

    private final SourceSectionGenerator sourceSectionGenerator;

    private LLVMParserRuntime(Source source, LLVMLanguage language, LLVMContext context, StackAllocation stack, LLVMLabelList labels, LLVMPhiManager phis,
                    DataLayoutConverter.DataSpecConverterImpl layout, SulongNodeFactory nodeFactory) {
        this.source = source;
        this.context = context;
        this.stack = stack;
        this.labels = labels;
        this.phis = phis;
        this.targetDataLayout = layout;
        this.nodeFactory = nodeFactory;
        this.language = language;
        this.symbolResolver = new LLVMSymbolResolver(labels, this);
        this.sourceSectionGenerator = new SourceSectionGenerator();
    }

    LLVMExpressionNode createFunction(FunctionDefinition method, LLVMLifetimeAnalysis lifetimes) {
        String functionName = method.getName();

        LLVMBitcodeFunctionVisitor visitor = new LLVMBitcodeFunctionVisitor(
                        this,
                        stack.getFrame(functionName),
                        labels.labels(functionName),
                        phis.getPhiMap(functionName),
                        nodeFactory,
                        method.getParameters().size(),
                        symbolResolver,
                        method);

        initFunction(visitor);

        method.accept(visitor);

        final LLVMStackFrameNuller[][] slotNullerBeginNodes = getSlotNuller(method, lifetimes.getNullableBefore());
        final LLVMStackFrameNuller[][] slotNullerAfterNodes = getSlotNuller(method, lifetimes.getNullableAfter());
        return nodeFactory.createFunctionBlockNode(this, visitor.getReturnSlot(), visitor.getBlocks(), slotNullerBeginNodes, slotNullerAfterNodes);
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
        final Type type = findType(method, identifier);
        return nodeFactory.createFrameNuller(this, identifier, type, slot);
    }

    List<LLVMExpressionNode> createParameters(FrameDescriptor frame, FunctionDefinition method) {
        final List<FunctionParameter> parameters = method.getParameters();
        final List<LLVMExpressionNode> formalParamInits = new ArrayList<>();

        int argIndex = 0;
        if (method.getType().getReturnType() instanceof StructureType) {
            final LLVMExpressionNode functionReturnParameterNode = nodeFactory.createFunctionArgNode(argIndex++, method.getType().getReturnType());
            final FrameSlot returnSlot = frame.findOrAddFrameSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
            final LLVMExpressionNode returnValue = nodeFactory.createFrameWrite(this, method.getType().getReturnType(), functionReturnParameterNode, returnSlot, null);
            formalParamInits.add(returnValue);
        }
        for (final FunctionParameter parameter : parameters) {
            final LLVMExpressionNode parameterNode = nodeFactory.createFunctionArgNode(argIndex++, parameter.getType());
            final FrameSlot slot = frame.findFrameSlot(parameter.getName());
            formalParamInits.add(nodeFactory.createFrameWrite(this, parameter.getType(), parameterNode, slot, null));
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
            final int size = getByteSize(type);

            final LLVMExpressionNode globalVarAddress = getGlobalVariable(global);

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
                final RootCallTarget constructorFunctionsRootCallTarget = Truffle.getRuntime().createCallTarget(nodeFactory.createStaticInitsRootNode(this, targets));
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
            final LLVMExpressionNode globalVarAddress = nodeFactory.createLiteral(this, globalVariableDescriptor, new PointerType(globalVar.getType()));
            final LLVMExpressionNode iNode = nodeFactory.createLiteral(this, i, PrimitiveType.I32);
            final LLVMExpressionNode structPointer = nodeFactory.createTypedElementPointer(this, globalVarAddress, iNode, structSize, elementType);
            final LLVMExpressionNode loadedStruct = nodeFactory.createLoad(this, elementType, structPointer);

            final LLVMExpressionNode oneLiteralNode = nodeFactory.createLiteral(this, 1, PrimitiveType.I32);
            final LLVMExpressionNode functionLoadTarget = nodeFactory.createTypedElementPointer(this, loadedStruct, oneLiteralNode, indexedTypeLength, functionType);
            final LLVMExpressionNode loadedFunction = nodeFactory.createLoad(this, functionType, functionLoadTarget);
            final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{};
            final LLVMExpressionNode functionCall = nodeFactory.createFunctionCall(this, loadedFunction, argNodes, functionType, null);

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
            globalValue = nodeFactory.allocateGlobalVariable(this, (GlobalVariable) global);
        } else if (global instanceof GlobalConstant) {
            globalValue = nodeFactory.allocateGlobalConstant(this, (GlobalConstant) global);
        } else {
            throw new AssertionError("Cannot allocate global: " + global);
        }

        globalVariableScope.put(global.getName(), globalValue);
        return nodeFactory.createLiteral(this, globalValue, new PointerType(global.getType()));
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

    private static Type findType(FunctionDefinition method, String identifier) {
        final Type methodType = method.getType(identifier);
        if (methodType != null) {
            return methodType;
        } else if (LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID.equals(identifier)) {
            return method.getType().getReturnType();
        } else if (LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID.equals(identifier)) {
            return new PointerType(null);
        } else {
            throw new IllegalStateException("Cannot find Instruction with name: " + identifier);
        }
    }

    private LLVMBitcodeFunctionVisitor functionVisitor = null;

    private final Map<String, Type> nameToTypeMapping = new HashMap<>();

    private final ValueInstructionVisitor nameToTypeMappingVisitor = new ValueInstructionVisitor() {
        @Override
        public void visitValueInstruction(ValueInstruction valueInstruction) {
            nameToTypeMapping.put(valueInstruction.getName(), valueInstruction.getType());
        }
    };

    private void initFunction(LLVMBitcodeFunctionVisitor visitor) {
        this.functionVisitor = visitor;
        nameToTypeMapping.clear();
        nameToTypeMapping.put(LLVMFrameIDs.FUNCTION_EXCEPTION_VALUE_FRAME_SLOT_ID, new PointerType(null));
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
        return nodeFactory.createAlloc(this, type, size, alignment, null, null);
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

    public int getIndexOffset(int index, AggregateType type) {
        return type.getOffsetOf(index, targetDataLayout);
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
            return ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), context.getNativeLookup().getNativeDataObject(name));
        } catch (UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Type> getVariableNameTypesMapping() {
        return Collections.unmodifiableMap(nameToTypeMapping);
    }

    public LLVMNativeFunctions getNativeFunctions() {
        return context.getNativeFunctions();
    }

    public SulongNodeFactory getNodeFactory() {
        return nodeFactory;
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
