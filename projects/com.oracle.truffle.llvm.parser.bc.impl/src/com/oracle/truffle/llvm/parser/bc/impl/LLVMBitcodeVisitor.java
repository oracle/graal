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
package com.oracle.truffle.llvm.parser.bc.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.types.ResolvedType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nativeint.NativeLookup;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMFreeFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI32CopyFactory;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMAddressLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI1LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMAccessGlobalVariableStorageNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.base.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.LLVMType;
import com.oracle.truffle.llvm.parser.base.model.symbols.ValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.base.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserAsserts;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserResultImpl;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.base.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.parser.base.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.base.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.bc.impl.nodes.LLVMConstantGenerator;
import com.oracle.truffle.llvm.parser.base.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.bc.impl.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.parser.factories.LLVMRootNodeFactory;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;

import com.oracle.truffle.llvm.parser.bc.impl.parser.ir.LLVMParser;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.LLVMToBitcodeAdapter;
import com.oracle.truffle.llvm.parser.base.model.Model;
import com.oracle.truffle.llvm.parser.base.model.ModelModule;
import com.oracle.truffle.llvm.parser.base.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.ModuleVersion;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;

public class LLVMBitcodeVisitor implements ModelVisitor {

    public static final class BitcodeParserResult {
        private final Model model;
        private final LLVMPhiManager phis;
        private final StackAllocation stackAllocation;
        private final LLVMLabelList labels;

        private BitcodeParserResult(Model model, LLVMPhiManager phis, StackAllocation stackAllocation, LLVMLabelList labels) {
            this.model = model;
            this.phis = phis;
            this.stackAllocation = stackAllocation;
            this.labels = labels;
        }

        public Model getModel() {
            return model;
        }

        public LLVMPhiManager getPhis() {
            return phis;
        }

        public StackAllocation getStackAllocation() {
            return stackAllocation;
        }

        public LLVMLabelList getLabels() {
            return labels;
        }

        public static BitcodeParserResult getFromFile(String sourcePath) {
            final Model model = new Model();
            new LLVMParser(model).parse(ModuleVersion.getModuleVersion(LLVMBaseOptionFacade.getLLVMVersion()), sourcePath);

            final LLVMPhiManager phis = LLVMPhiManager.generate(model);
            final StackAllocation stackAllocation = StackAllocation.generate(model);
            final LLVMLabelList labels = LLVMLabelList.generate(model);

            final TargetDataLayout layout = ((ModelModule) model.createModule()).getTargetDataLayout();
            final DataLayoutConverter.DataSpecConverter targetDataLayout = layout != null ? DataLayoutConverter.getConverter(layout.getDataLayout()) : null;
            LLVMMetadata.generate(model, targetDataLayout);

            return new BitcodeParserResult(model, phis, stackAllocation, labels);
        }
    }

    public static LLVMParserResult getMain(Source source, LLVMContext context, NodeFactoryFacade factoryFacade) {
        final BitcodeParserResult parserResult = BitcodeParserResult.getFromFile(source.getPath());
        final Model model = parserResult.getModel();
        final StackAllocation stackAllocation = parserResult.getStackAllocation();
        final TargetDataLayout layout = ((ModelModule) model.createModule()).getTargetDataLayout();
        final DataLayoutConverter.DataSpecConverter targetDataLayout = layout != null ? DataLayoutConverter.getConverter(layout.getDataLayout()) : null;

        final LLVMBitcodeVisitor module = new LLVMBitcodeVisitor(source, context, stackAllocation, parserResult.getLabels(), parserResult.getPhis(), targetDataLayout, factoryFacade);
        model.accept(module);

        LLVMFunction mainFunction = module.getFunction("@main");

        FrameDescriptor rootFrame = stackAllocation.getRootFrame();
        FrameSlot stack = stackAllocation.getRootStackSlot();

        LLVMNode[] globals = module.getGobalVariables(stack).toArray(new LLVMNode[0]);
        RootNode globalVarInits = factoryFacade.createStaticInitsRootNode(globals);
        RootCallTarget globalVarInitsTarget = Truffle.getRuntime().createCallTarget(globalVarInits);
        LLVMNode[] deallocs = module.getDeallocations();
        RootNode globalVarDeallocs = factoryFacade.createStaticInitsRootNode(deallocs);
        RootCallTarget globalVarDeallocsTarget = Truffle.getRuntime().createCallTarget(globalVarDeallocs);

        final List<RootCallTarget> constructorFunctions = module.getStructor("@llvm.global_ctors", stack);
        final List<RootCallTarget> destructorFunctions = module.getStructor("@llvm.global_dtors", stack);

        if (mainFunction == null) {
            return new LLVMParserResultImpl(Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(stack)), globalVarInitsTarget, globalVarDeallocsTarget, constructorFunctions,
                            destructorFunctions, module.getFunctions());
        }
        RootCallTarget mainCallTarget = module.getFunctions().get(mainFunction);
        RootNode globalFunction = LLVMRootNodeFactory.createGlobalRootNode(context, stack, rootFrame, mainCallTarget, context.getMainArguments(), source, mainFunction.getParameterTypes());
        RootCallTarget globalFunctionRoot = Truffle.getRuntime().createCallTarget(globalFunction);
        RootNode globalRootNode = factoryFacade.createGlobalRootNodeWrapping(globalFunctionRoot, mainFunction.getReturnType());
        RootCallTarget wrappedCallTarget = Truffle.getRuntime().createCallTarget(globalRootNode);
        return new LLVMParserResultImpl(wrappedCallTarget, globalVarInitsTarget, globalVarDeallocsTarget, constructorFunctions, destructorFunctions, module.getFunctions());
    }

    private final LLVMContext context;

    private final LLVMLabelList labels;

    private final LLVMPhiManager phis;

    private final List<LLVMNode> deallocations = new ArrayList<>();

    private final Map<GlobalAlias, Symbol> aliases = new HashMap<>();

    private final Map<LLVMFunction, RootCallTarget> functions = new HashMap<>();

    private final Map<GlobalValueSymbol, LLVMAddressNode> globals = new HashMap<>();

    private final DataLayoutConverter.DataSpecConverter targetDataLayout;

    private final LLVMBitcodeTypeHelper typeHelper;

    private final NodeFactoryFacade factoryFacade;

    private final LLVMBitcodeVisitorParserRuntime parserRuntime;

    private final Source source;

    private final StackAllocation stack;

    public LLVMBitcodeVisitor(Source source, LLVMContext context, StackAllocation stack, LLVMLabelList labels, LLVMPhiManager phis,
                    DataLayoutConverter.DataSpecConverter layout, NodeFactoryFacade factoryFacade) {
        this.source = source;
        this.context = context;
        this.stack = stack;
        this.labels = labels;
        this.phis = phis;
        this.targetDataLayout = layout;
        this.typeHelper = new LLVMBitcodeTypeHelper(targetDataLayout);
        this.factoryFacade = factoryFacade;
        this.parserRuntime = new LLVMBitcodeVisitorParserRuntime();
        this.factoryFacade.setUpFacade(this.parserRuntime);
    }

    private LLVMExpressionNode createFunction(FunctionDefinition method, LLVMLifetimeAnalysis lifetimes) {
        String functionName = method.getName();

        LLVMBitcodeFunctionVisitor visitor = new LLVMBitcodeFunctionVisitor(
                        this,
                        stack.getFrame(functionName),
                        labels.labels(functionName),
                        phis.getPhiMap(functionName),
                        factoryFacade,
                        method.getParameters().size());

        parserRuntime.setFunctionVisitor(visitor);

        method.accept(visitor);

        parserRuntime.setFunctionVisitor(null);

        final int[] basicBlockIndices = new int[method.getBlockCount()];
        for (int i = 0; i < method.getBlockCount(); i++) {
            basicBlockIndices[i] = i;
        }
        final LLVMStackFrameNuller[][] slotNullerBeginNodes = getSlotNuller(method, lifetimes.getNullableBefore());
        final LLVMStackFrameNuller[][] slotNullerAfterNodes = getSlotNuller(method, lifetimes.getNullableAfter());
        return factoryFacade.createFunctionBlockNode(visitor.getReturnSlot(), visitor.getBlocks(), slotNullerBeginNodes, slotNullerAfterNodes);
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
        return factoryFacade.createFrameNuller(identifier, type, slot);
    }

    private List<LLVMNode> createParameters(FrameDescriptor frame, FunctionDefinition method) {
        final List<FunctionParameter> parameters = method.getParameters();
        final List<LLVMNode> formalParamInits = new ArrayList<>();

        final LLVMExpressionNode stackPointerNode = factoryFacade.createFunctionArgNode(0, LLVMBaseType.ADDRESS);
        formalParamInits.add(factoryFacade.createFrameWrite(LLVMBaseType.ADDRESS, stackPointerNode, frame.findFrameSlot(LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID)));

        int argIndex = LLVMCallNode.ARG_START_INDEX;
        if (method.getReturnType() instanceof StructureType) {
            final LLVMExpressionNode functionReturnParameterNode = factoryFacade.createFunctionArgNode(argIndex++, LLVMBaseType.STRUCT);
            final FrameSlot returnSlot = frame.findOrAddFrameSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
            final LLVMBaseType baseType = method.getReturnType().getLLVMBaseType();
            final LLVMNode returnValue = factoryFacade.createFrameWrite(baseType, functionReturnParameterNode, returnSlot);
            formalParamInits.add(returnValue);
        }
        for (final FunctionParameter parameter : parameters) {
            final LLVMBaseType paramType = parameter.getType().getLLVMBaseType();
            final LLVMExpressionNode parameterNode = factoryFacade.createFunctionArgNode(argIndex++, paramType);
            final FrameSlot slot = frame.findFrameSlot(parameter.getName());
            formalParamInits.add(factoryFacade.createFrameWrite(paramType, parameterNode, slot));
        }
        return formalParamInits;
    }

    private LLVMNode createGlobal(GlobalValueSymbol global, FrameSlot stackSlot) {
        if (global == null || global.getValue() == null) {
            return null;
        }

        LLVMExpressionNode constant = LLVMConstantGenerator.toConstantNode(global.getValue(), global.getAlign(), this::getGlobalVariable, context, stackSlot, labels, typeHelper);
        if (constant != null) {
            final Type type = ((PointerType) global.getType()).getPointeeType();
            final LLVMBaseType baseType = type.getLLVMBaseType();
            final int size = type.getSize(targetDataLayout);

            final LLVMAddressNode globalVarAddress = (LLVMAddressNode) getGlobalVariable(global);

            if (size != 0) {
                final LLVMNode store;
                if (baseType == LLVMBaseType.ARRAY || baseType == LLVMBaseType.STRUCT) {
                    store = LLVMMemI32CopyFactory.create(globalVarAddress, (LLVMAddressNode) constant, new LLVMI32LiteralNode(size), new LLVMI32LiteralNode(0), new LLVMI1LiteralNode(false));
                } else {
                    final Type t = global.getValue().getType();
                    store = factoryFacade.createStore(globalVarAddress, constant, t);
                }
                return store;
            }
        }

        return null;
    }

    public LLVMContext getContext() {
        return context;
    }

    public DataLayoutConverter.DataSpecConverter getTargetDataLayout() {
        return targetDataLayout;
    }

    public LLVMBitcodeTypeHelper getTypeHelper() {
        return typeHelper;
    }

    public LLVMNode[] getDeallocations() {
        return deallocations.toArray(new LLVMNode[deallocations.size()]);
    }

    public LLVMFunction getFunction(String name) {
        for (LLVMFunction function : functions.keySet()) {
            if (function.getName().equals(name)) {
                return function;
            }
        }
        return null;
    }

    public Map<LLVMFunction, RootCallTarget> getFunctions() {
        return functions;
    }

    public LLVMExpressionNode getGlobalVariable(GlobalValueSymbol global) {
        Symbol g = global;
        while (g instanceof GlobalAlias) {
            g = aliases.get(g);
        }

        if (g instanceof GlobalValueSymbol) {
            final GlobalValueSymbol variable = (GlobalValueSymbol) g;
            LLVMAddressNode address = globals.get(variable);

            if (address == null) {
                address = allocateGlobal(variable);
                globals.put(variable, address);
            }
            return address;
        } else {
            return LLVMConstantGenerator.toConstantNode(g, 0, this::getGlobalVariable, context, null, labels, typeHelper);
        }
    }

    public List<RootCallTarget> getStructor(String name, FrameSlot stackSlot) {
        for (GlobalValueSymbol globalValueSymbol : globals.keySet()) {
            if (globalValueSymbol.getName().equals(name)) {
                final LLVMNode[] targets = resolveStructor(globalValueSymbol, stackSlot);
                final RootCallTarget constructorFunctionsRootCallTarget = Truffle.getRuntime().createCallTarget(factoryFacade.createStaticInitsRootNode(targets));
                final List<RootCallTarget> targetList = new ArrayList<>(1);
                targetList.add(constructorFunctionsRootCallTarget);
                return targetList;
            }
        }
        return Collections.emptyList();
    }

    private LLVMNode[] resolveStructor(GlobalValueSymbol globalVar, FrameSlot stackSlot) {
        final LLVMGlobalVariableDescriptor globalVariableDescriptor = globalVariableScope.get(globalVar.getName());
        final ArrayConstant arrayConstant = (ArrayConstant) globalVar.getValue();
        final int elemCount = arrayConstant.getElementCount();

        final StructureType elementType = (StructureType) arrayConstant.getType().getElementType();
        final int structSize = elementType.getSize(targetDataLayout);

        final FunctionType functionType = (FunctionType) ((PointerType) elementType.getElementType(1)).getPointeeType();
        final int indexedTypeLength = functionType.getAlignment(targetDataLayout);

        final LLVMNode[] structors = new LLVMNode[elemCount];
        for (int i = 0; i < elemCount; i++) {
            final LLVMExpressionNode globalVarAddress = factoryFacade.createLiteral(globalVariableDescriptor, LLVMBaseType.ADDRESS);
            final LLVMExpressionNode iNode = factoryFacade.createLiteral(i, LLVMBaseType.I32);
            final LLVMAddressNode structPointer = (LLVMAddressNode) factoryFacade.createGetElementPtr(LLVMBaseType.I32, globalVarAddress, iNode, structSize);
            final LLVMExpressionNode loadedStruct = factoryFacade.createLoad(elementType, structPointer);

            final LLVMExpressionNode oneLiteralNode = factoryFacade.createLiteral(1, LLVMBaseType.I32);
            final LLVMExpressionNode functionLoadTarget = factoryFacade.createGetElementPtr(LLVMBaseType.I32, loadedStruct, oneLiteralNode, indexedTypeLength);
            final LLVMExpressionNode loadedFunction = factoryFacade.createLoad(functionType, functionLoadTarget);
            final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{factoryFacade.createFrameRead(LLVMBaseType.ADDRESS, stackSlot)};
            final LLVMNode functionCall = factoryFacade.createFunctionCall(loadedFunction, argNodes, LLVMBaseType.VOID);
            structors[i] = functionCall;
        }

        return structors;
    }

    private final Map<String, LLVMGlobalVariableDescriptor> globalVariableScope = new HashMap<>();

    // NativeLookup expects a NodeFactoryFacade but does not use it for our purpose
    private final NativeLookup nativeLookup = new NativeLookup(null);

    private LLVMAddressNode allocateGlobal(GlobalValueSymbol global) {
        final String name = global.getName();

        final LLVMGlobalVariableDescriptor.NativeResolver nativeResolver = () -> LLVMAddress.fromLong(nativeLookup.getNativeHandle(name));

        final LLVMGlobalVariableDescriptor descriptor;
        if (global.isStatic()) {
            descriptor = new LLVMGlobalVariableDescriptor(name, nativeResolver);
        } else {
            descriptor = context.getGlobalVariableRegistry().lookupOrAdd(name, nativeResolver);
        }

        // if the global does not have an associated value the compiler did not initialize it, in
        // this case we assume memory has already been allocated elsewhere
        final boolean allocateMemory = !descriptor.isDeclared() && global.getValue() != null;
        if (allocateMemory) {
            final int byteSize = ((PointerType) global.getType()).getPointeeType().getSize(targetDataLayout);
            final LLVMAddress nativeStorage = LLVMHeap.allocateMemory(byteSize);
            final LLVMAddressNode addressLiteralNode = new LLVMAddressLiteralNode(nativeStorage);
            deallocations.add(LLVMFreeFactory.create(addressLiteralNode));
            descriptor.declare(nativeStorage);
        }

        globalVariableScope.put(global.getName(), descriptor);

        return LLVMAccessGlobalVariableStorageNodeGen.create(descriptor);
    }

    public List<LLVMNode> getGobalVariables(FrameSlot stackSlot) {
        final List<LLVMNode> globalNodes = new ArrayList<>();
        for (GlobalValueSymbol global : this.globals.keySet()) {
            final LLVMNode store = createGlobal(global, stackSlot);
            if (store != null) {
                globalNodes.add(store);
            }
        }
        return globalNodes;
    }

    @Override
    public void visit(GlobalAlias alias) {
        aliases.put(alias, alias.getValue());
    }

    @Override
    public void visit(GlobalConstant constant) {
        globals.put(constant, null);
    }

    @Override
    public void visit(GlobalVariable variable) {
        globals.put(variable, null);
    }

    @Override
    public void visit(FunctionDeclaration method) {
    }

    @Override
    public void visit(FunctionDefinition method) {
        FrameDescriptor frame = stack.getFrame(method.getName());

        List<LLVMNode> parameters = createParameters(frame, method);

        final LLVMLifetimeAnalysis lifetimes = LLVMLifetimeAnalysis.getResult(method, frame, phis.getPhiMap(method.getName()));

        LLVMExpressionNode body = createFunction(method, lifetimes);

        LLVMNode[] beforeFunction = parameters.toArray(new LLVMNode[parameters.size()]);
        LLVMNode[] afterFunction = new LLVMNode[0];

        final SourceSection sourceSection = source.createSection(1);
        LLVMFunctionStartNode rootNode = new LLVMFunctionStartNode(body, beforeFunction, afterFunction, sourceSection, frame, method.getName(), getInitNullers(frame, method));
        if (LLVMBaseOptionFacade.printFunctionASTs()) {
            NodeUtil.printTree(System.out, rootNode);
            System.out.flush();
        }

        LLVMRuntimeType llvmReturnType = method.getReturnType().getRuntimeType();
        LLVMRuntimeType[] llvmParamTypes = LLVMBitcodeTypeHelper.toRuntimeTypes(method.getArgumentTypes());
        LLVMFunction function = context.getFunctionRegistry().createFunctionDescriptor(method.getName(), llvmReturnType, llvmParamTypes, method.isVarArg());
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        functions.put(function, callTarget);
    }

    /**
     * Initializes the tags of the frame.
     */
    private LLVMStackFrameNuller[] getInitNullers(FrameDescriptor frameDescriptor, FunctionDefinition method) throws AssertionError {
        final List<LLVMStackFrameNuller> initNullers = new ArrayList<>();
        for (FrameSlot slot : frameDescriptor.getSlots()) {
            initNullers.add(getNullerNode(slot, method));
        }
        return initNullers.toArray(new LLVMStackFrameNuller[initNullers.size()]);
    }

    private static LLVMType findType(FunctionDefinition method, String identifier) {
        for (int i = 0; i < method.getBlockCount(); i++) {
            final InstructionBlock block = method.getBlock(i);
            for (int j = 0; j < block.getInstructionCount(); j++) {
                final Instruction instruction = block.getInstruction(j);
                if (instruction.hasName() && ((ValueSymbol) instruction).getName().equals(identifier)) {
                    return LLVMTypeHelper.getLLVMType(instruction.getType());
                }
            }
        }
        for (final FunctionParameter functionParameter : method.getParameters()) {
            if (functionParameter.getName().equals(identifier)) {
                return LLVMTypeHelper.getLLVMType(functionParameter.getType());
            }
        }
        if (LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID.equals(identifier)) {
            return LLVMTypeHelper.getLLVMType(method.getReturnType());
        } else if (LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID.equals(identifier)) {
            return new LLVMType(LLVMBaseType.ADDRESS);
        }
        throw new IllegalStateException("Cannot find Instruction with name: " + identifier);
    }

    @Override
    public void visit(Type type) {
    }

    private class LLVMBitcodeVisitorParserRuntime implements LLVMParserRuntime {

        private LLVMBitcodeFunctionVisitor functionVisitor = null;

        void setFunctionVisitor(LLVMBitcodeFunctionVisitor functionVisitor) {
            this.functionVisitor = functionVisitor;
        }

        @Override
        public ResolvedType resolve(EObject e) {
            throw new UnsupportedOperationException("Not implemented!");
        }

        @Override
        public LLVMExpressionNode allocateFunctionLifetime(ResolvedType type, int size, int alignment) {
            return factoryFacade.createAlloc(LLVMToBitcodeAdapter.resolveType(type), size, alignment, null, null);
        }

        @Override
        public FrameSlot getReturnSlot() {
            if (functionVisitor != null) {
                return functionVisitor.getReturnSlot();
            }
            throw new IllegalStateException("There is currently no active function visitor set");
        }

        @Override
        public LLVMExpressionNode allocateVectorResult(EObject type) {
            throw new UnsupportedOperationException("Not implemented!");
        }

        @Override
        public Object getGlobalAddress(com.intel.llvm.ireditor.lLVM_IR.GlobalVariable var) {
            throw new UnsupportedOperationException("Not implemented!");
        }

        @Override
        public FrameSlot getStackPointerSlot() {
            return functionVisitor != null ? functionVisitor.getStackSlot() : stack.getRootStackSlot();
        }

        @Override
        public int getBitAlignment(LLVMBaseType type) {
            return targetDataLayout.getBitAlignment(type);
        }

        @Override
        public int getByteSize(Type type) {
            return type.getSize(targetDataLayout);
        }

        @Override
        public FrameDescriptor getGlobalFrameDescriptor() {
            return stack.getRootFrame();
        }

        @Override
        public void addDestructor(LLVMNode destructorNode) {
            throw new UnsupportedOperationException("Not implemented!");
        }

        @Override
        public long getNativeHandle(String name) {
            return nativeLookup.getNativeHandle(name);
        }

        @Override
        public LLVMTypeHelper getTypeHelper() {
            throw new UnsupportedOperationException("Not implemented!");
        }

        @Override
        public Map<String, Type> getVariableNameTypesMapping() {
            throw new UnsupportedOperationException("Not implemented!");
        }

        @Override
        public NodeFactoryFacade getNodeFactoryFacade() {
            return factoryFacade;
        }

    }

}
