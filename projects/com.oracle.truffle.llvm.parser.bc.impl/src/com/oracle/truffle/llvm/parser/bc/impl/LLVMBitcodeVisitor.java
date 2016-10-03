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
import com.oracle.truffle.llvm.nodes.impl.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMFreeFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI32CopyFactory;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMAddressLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI1LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMAccessGlobalVariableStorageNodeGen;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStaticInitsBlockNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.base.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.bc.impl.nodes.LLVMConstantGenerator;
import com.oracle.truffle.llvm.parser.base.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.bc.impl.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.parser.factories.LLVMBlockFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFrameReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFunctionFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMGetElementPtrFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLiteralFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMMemoryReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMRootNodeFactory;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
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
import com.oracle.truffle.llvm.parser.base.model.Model;
import com.oracle.truffle.llvm.parser.base.model.ModelModule;
import com.oracle.truffle.llvm.parser.base.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.ArrayConstant;
import com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.ModuleVersion;
import com.oracle.truffle.llvm.parser.base.model.target.TargetDataLayout;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;

public class LLVMBitcodeVisitor implements ModelVisitor {

    public static LLVMParserResult getMain(Source source, LLVMContext context) {
        Model model = new Model();

        ModuleVersion llvmVersion = ModuleVersion.getModuleVersion(LLVMBaseOptionFacade.getLLVMVersion());
        new LLVMParser(model).parse(llvmVersion, source.getPath());

        LLVMPhiManager phis = LLVMPhiManager.generate(model);

        LLVMFrameDescriptors lifetimes = LLVMFrameDescriptors.generate(model);

        LLVMLabelList labels = LLVMLabelList.generate(model);

        LLVMMetadata.generate(model);

        LLVMBitcodeVisitor module = new LLVMBitcodeVisitor(source, context, lifetimes, labels, phis, ((ModelModule) model.createModule()).getTargetDataLayout());

        model.accept(module);

        LLVMFunctionDescriptor mainFunction = module.getFunction("@main");

        FrameDescriptor frame = new FrameDescriptor();
        FrameSlot stack = frame.addFrameSlot(LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID);

        LLVMNode[] globals = module.getGobalVariables(stack).toArray(new LLVMNode[0]);
        RootNode globalVarInits = new LLVMStaticInitsBlockNode(globals, frame, context, stack);
        RootCallTarget globalVarInitsTarget = Truffle.getRuntime().createCallTarget(globalVarInits);
        LLVMNode[] deallocs = module.getDeallocations();
        RootNode globalVarDeallocs = new LLVMStaticInitsBlockNode(deallocs, frame, context, stack);
        RootCallTarget globalVarDeallocsTarget = Truffle.getRuntime().createCallTarget(globalVarDeallocs);

        final List<RootCallTarget> constructorFunctions = module.getStructor("@llvm.global_ctors", frame, stack);
        final List<RootCallTarget> destructorFunctions = module.getStructor("@llvm.global_dtors", frame, stack);

        if (mainFunction == null) {
            return new LLVMBitcodeParserResult(Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(stack)), globalVarInitsTarget, globalVarDeallocsTarget, module.getFunctions(),
                            constructorFunctions, destructorFunctions);
        }
        RootCallTarget mainCallTarget = module.getFunctions().get(mainFunction);
        RootNode globalFunction = LLVMRootNodeFactory.createGlobalRootNode(context, stack, frame, mainCallTarget, context.getMainArguments(), source, mainFunction.getParameterTypes());
        RootCallTarget globalFunctionRoot = Truffle.getRuntime().createCallTarget(globalFunction);
        RootNode globalRootNode = LLVMFunctionFactory.createGlobalRootNodeWrapping(globalFunctionRoot, mainFunction.getReturnType());
        RootCallTarget wrappedCallTarget = Truffle.getRuntime().createCallTarget(globalRootNode);
        return new LLVMBitcodeParserResult(wrappedCallTarget, globalVarInitsTarget, globalVarDeallocsTarget, module.getFunctions(), constructorFunctions, destructorFunctions);
    }

    private final LLVMContext context;

    private final LLVMFrameDescriptors frames;

    private final LLVMLabelList labels;

    private final LLVMPhiManager phis;

    private final List<LLVMNode> deallocations = new ArrayList<>();

    private final Map<GlobalAlias, Symbol> aliases = new HashMap<>();

    private final Map<LLVMFunctionDescriptor, RootCallTarget> functions = new HashMap<>();

    private final Map<GlobalValueSymbol, LLVMAddressNode> globals = new HashMap<>();

    private final DataLayoutConverter.DataSpecConverter targetDataLayout;

    private final LLVMBitcodeTypeHelper typeHelper;

    private final Source source;

    public LLVMBitcodeVisitor(Source source, LLVMContext context, LLVMFrameDescriptors frames, LLVMLabelList labels, LLVMPhiManager phis,
                    TargetDataLayout layout) {
        this.source = source;
        this.context = context;
        this.frames = frames;
        this.labels = labels;
        this.phis = phis;
        if (layout != null) {
            this.targetDataLayout = DataLayoutConverter.getConverter(layout.getDataLayout());
        } else {
            this.targetDataLayout = null;
        }
        this.typeHelper = new LLVMBitcodeTypeHelper(targetDataLayout);
    }

    private LLVMExpressionNode createFunction(FunctionDefinition method) {
        String name = method.getName();

        LLVMBitcodeFunctionVisitor visitor = new LLVMBitcodeFunctionVisitor(
                        this,
                        frames.getDescriptor(name),
                        frames.getSlots(name),
                        labels.labels(name),
                        phis.getPhiMap(name),
                        method.getParameters().size());

        method.accept(visitor);

        LLVMBasicBlockNode[] basicBlocks = visitor.getBlocks();

        return LLVMBlockFactory.createFunctionBlock(
                        visitor.getReturnSlot(),
                        visitor.getBlocks(),
                        new LLVMStackFrameNuller[basicBlocks.length][0], visitor.getNullers());
    }

    private static List<LLVMNode> createParameters(FrameDescriptor frame, FunctionDefinition method) {
        final List<FunctionParameter> parameters = method.getParameters();
        final List<LLVMNode> formalParamInits = new ArrayList<>();

        final LLVMExpressionNode stackPointerNode = LLVMFunctionFactory.createFunctionArgNode(0, LLVMBaseType.ADDRESS);
        formalParamInits.add(LLVMFrameReadWriteFactory.createFrameWrite(LLVMBaseType.ADDRESS, stackPointerNode, frame.findFrameSlot(LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID)));

        int argIndex = LLVMCallNode.ARG_START_INDEX;
        if (method.getReturnType() instanceof StructureType) {
            final LLVMExpressionNode functionReturnParameterNode = LLVMFunctionFactory.createFunctionArgNode(argIndex++, LLVMBaseType.STRUCT);
            final FrameSlot returnSlot = frame.findOrAddFrameSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
            final LLVMBaseType baseType = LLVMBitcodeTypeHelper.getLLVMBaseType(method.getReturnType());
            final LLVMNode returnValue = LLVMFrameReadWriteFactory.createFrameWrite(baseType, functionReturnParameterNode, returnSlot);
            formalParamInits.add(returnValue);
        }
        for (final FunctionParameter parameter : parameters) {
            final LLVMBaseType paramType = LLVMBitcodeTypeHelper.getLLVMBaseType(parameter.getType());
            final LLVMExpressionNode parameterNode = LLVMFunctionFactory.createFunctionArgNode(argIndex++, paramType);
            final FrameSlot slot = frame.findFrameSlot(parameter.getName());
            formalParamInits.add(LLVMFrameReadWriteFactory.createFrameWrite(paramType, parameterNode, slot));
        }
        return formalParamInits;
    }

    private LLVMNode createGlobal(GlobalValueSymbol global, FrameSlot stack) {
        if (global == null || global.getValue() == null) {
            return null;
        }

        LLVMExpressionNode constant = LLVMConstantGenerator.toConstantNode(global.getValue(), global.getAlign(), this::getGlobalVariable, context, stack, labels, typeHelper);
        if (constant != null) {
            final Type type = ((PointerType) global.getType()).getPointeeType();
            final LLVMBaseType baseType = LLVMBitcodeTypeHelper.getLLVMBaseType(type);
            final int size = typeHelper.getByteSize(type);

            final LLVMAddressNode globalVarAddress = (LLVMAddressNode) getGlobalVariable(global);

            if (size != 0) {
                final LLVMNode store;
                if (baseType == LLVMBaseType.ARRAY || baseType == LLVMBaseType.STRUCT) {
                    store = LLVMMemI32CopyFactory.create(globalVarAddress, (LLVMAddressNode) constant, new LLVMI32LiteralNode(size), new LLVMI32LiteralNode(0), new LLVMI1LiteralNode(false));
                } else {
                    final Type t = global.getValue().getType();
                    store = LLVMMemoryReadWriteFactory.createStore(globalVarAddress, constant, LLVMBitcodeTypeHelper.getLLVMBaseType(t),
                                    typeHelper.getByteSize(t));
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

    public LLVMFunctionDescriptor getFunction(String name) {
        for (LLVMFunctionDescriptor function : functions.keySet()) {
            if (function.getName().equals(name)) {
                return function;
            }
        }
        return null;
    }

    public Map<LLVMFunctionDescriptor, RootCallTarget> getFunctions() {
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

    public List<RootCallTarget> getStructor(String name, FrameDescriptor frame, FrameSlot stack) {
        for (GlobalValueSymbol globalValueSymbol : globals.keySet()) {
            if (globalValueSymbol.getName().equals(name)) {
                final LLVMNode[] targets = resolveStructor(globalValueSymbol, stack);
                final RootCallTarget constructorFunctionsRootCallTarget = Truffle.getRuntime().createCallTarget(new LLVMStaticInitsBlockNode(targets, frame, context, stack));
                final List<RootCallTarget> targetList = new ArrayList<>(1);
                targetList.add(constructorFunctionsRootCallTarget);
                return targetList;
            }
        }
        return Collections.emptyList();
    }

    private LLVMNode[] resolveStructor(GlobalValueSymbol globalVar, FrameSlot stack) {
        final LLVMGlobalVariableDescriptor globalVariableDescriptor = globalVariableScope.get(globalVar.getName());
        final ArrayConstant arrayConstant = (ArrayConstant) globalVar.getValue();
        final int elemCount = arrayConstant.getElementCount();

        final StructureType elementType = (StructureType) arrayConstant.getType().getElementType();
        final int structSize = typeHelper.getByteSize(elementType);

        final FunctionType functionType = (FunctionType) ((PointerType) elementType.getElementType(1)).getPointeeType();
        final int indexedTypeLength = typeHelper.getAlignment(functionType);

        final LLVMNode[] structors = new LLVMNode[elemCount];
        for (int i = 0; i < elemCount; i++) {
            final LLVMExpressionNode globalVarAddress = LLVMLiteralFactory.createLiteral(globalVariableDescriptor, LLVMBaseType.ADDRESS);
            final LLVMExpressionNode iNode = LLVMLiteralFactory.createLiteral(i, LLVMBaseType.I32);
            final LLVMAddressNode structPointer = LLVMGetElementPtrFactory.create(LLVMBaseType.I32, (LLVMAddressNode) globalVarAddress, iNode, structSize);
            final LLVMExpressionNode loadedStruct = LLVMMemoryReadWriteFactory.createLoad(LLVMBitcodeTypeHelper.getLLVMBaseType(elementType), structPointer, 0);

            final LLVMExpressionNode oneLiteralNode = LLVMLiteralFactory.createLiteral(1, LLVMBaseType.I32);
            final LLVMExpressionNode functionLoadTarget = LLVMGetElementPtrFactory.create(LLVMBaseType.I32, (LLVMAddressNode) loadedStruct, oneLiteralNode, indexedTypeLength);
            final LLVMExpressionNode loadedFunction = LLVMMemoryReadWriteFactory.createLoad(LLVMBitcodeTypeHelper.getLLVMBaseType(functionType), (LLVMAddressNode) functionLoadTarget, 0);
            final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{LLVMFrameReadWriteFactory.createFrameRead(LLVMBaseType.ADDRESS, stack)};
            final LLVMNode functionCall = LLVMFunctionFactory.createFunctionCall((LLVMFunctionNode) loadedFunction, argNodes, LLVMBaseType.VOID);
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
            descriptor = context.getGlobalVaraibleRegistry().lookupOrAdd(name, nativeResolver);
        }

        // if the global does not have an associated value the compiler did not initialize it, in
        // this case we assume memory has already been allocated elsewhere
        final boolean allocateMemory = !descriptor.isDeclared() && global.getValue() != null;
        if (allocateMemory) {
            final int byteSize = typeHelper.getByteSize(((PointerType) global.getType()).getPointeeType());
            final LLVMAddress nativeStorage = LLVMHeap.allocateMemory(byteSize);
            final LLVMAddressNode addressLiteralNode = new LLVMAddressLiteralNode(nativeStorage);
            deallocations.add(LLVMFreeFactory.create(addressLiteralNode));
            descriptor.declare(nativeStorage);
        }

        globalVariableScope.put(global.getName(), descriptor);

        return LLVMAccessGlobalVariableStorageNodeGen.create(descriptor);
    }

    public List<LLVMNode> getGobalVariables(FrameSlot stack) {
        final List<LLVMNode> globalNodes = new ArrayList<>();
        for (GlobalValueSymbol global : this.globals.keySet()) {
            final LLVMNode store = createGlobal(global, stack);
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
    @SuppressWarnings("deprecation")
    public void visit(FunctionDefinition method) {
        FrameDescriptor frame = frames.getDescriptor(method.getName());

        List<LLVMNode> parameters = createParameters(frame, method);

        LLVMExpressionNode body = createFunction(method);

        LLVMNode[] beforeFunction = parameters.toArray(new LLVMNode[parameters.size()]);
        LLVMNode[] afterFunction = new LLVMNode[0];

        final SourceSection sourceSection = source.createSection(method.getName(), 1);
        LLVMFunctionStartNode rootNode = new LLVMFunctionStartNode(body, beforeFunction, afterFunction, sourceSection, frame, method.getName());
        if (LLVMBaseOptionFacade.printFunctionASTs()) {
            NodeUtil.printTree(System.out, rootNode);
            System.out.flush();
        }

        LLVMRuntimeType llvmReturnType = LLVMBitcodeTypeHelper.toRuntimeType(method.getReturnType());
        LLVMRuntimeType[] llvmParamTypes = LLVMBitcodeTypeHelper.toRuntimeTypes(method.getArgumentTypes());
        LLVMFunctionDescriptor function = context.getFunctionRegistry().createFunctionDescriptor(method.getName(), llvmReturnType, llvmParamTypes, method.isVarArg());
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        functions.put(function, callTarget);
    }

    @Override
    public void visit(Type type) {
    }

}
