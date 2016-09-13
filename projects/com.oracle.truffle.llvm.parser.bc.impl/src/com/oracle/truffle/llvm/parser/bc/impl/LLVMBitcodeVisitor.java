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
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMFreeFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI32CopyFactory;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMAddressLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI1LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStaticInitsBlockNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.bc.impl.util.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.bc.impl.util.DataLayoutParser;
import com.oracle.truffle.llvm.parser.bc.impl.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.factories.LLVMBlockFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFrameReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFunctionFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMMemoryReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMRootNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;

import uk.ac.man.cs.llvm.ir.LLVMParser;
import uk.ac.man.cs.llvm.ir.model.FunctionDeclaration;
import uk.ac.man.cs.llvm.ir.model.FunctionDefinition;
import uk.ac.man.cs.llvm.ir.model.FunctionParameter;
import uk.ac.man.cs.llvm.ir.model.GlobalAlias;
import uk.ac.man.cs.llvm.ir.model.GlobalConstant;
import uk.ac.man.cs.llvm.ir.model.GlobalValueSymbol;
import uk.ac.man.cs.llvm.ir.model.GlobalVariable;
import uk.ac.man.cs.llvm.ir.model.Model;
import uk.ac.man.cs.llvm.ir.model.ModelModule;
import uk.ac.man.cs.llvm.ir.model.ModelVisitor;
import uk.ac.man.cs.llvm.ir.model.Symbol;
import uk.ac.man.cs.llvm.ir.model.constants.ArrayConstant;
import uk.ac.man.cs.llvm.ir.model.constants.StructureConstant;
import uk.ac.man.cs.llvm.ir.module.ModuleVersion;
import uk.ac.man.cs.llvm.ir.module.TargetDataLayout;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.Type;

public class LLVMBitcodeVisitor implements ModelVisitor {

    public static LLVMParserResult getMain(Source source, LLVMContext context, LLVMOptimizationConfiguration configuration) {
        Model model = new Model();

        new LLVMParser(model).parse(ModuleVersion.LLVM_3_2, source.getPath());

        LLVMPhiManager phis = LLVMPhiManager.generate(model);

        LLVMFrameDescriptors lifetimes = LLVMFrameDescriptors.generate(model);

        LLVMLabelList labels = LLVMLabelList.generate(model);

        LLVMBitcodeVisitor module = new LLVMBitcodeVisitor(context, configuration, lifetimes, labels, phis, ((ModelModule) model.createModule()).getTargetDataLayout());

        model.accept(module);

        LLVMFunctionDescriptor mainFunction = module.getFunction("@main");

        FrameDescriptor frame = new FrameDescriptor();
        FrameSlot stack = frame.addFrameSlot(LLVMBitcodeHelper.STACK_ADDRESS_FRAME_SLOT_ID);

        List<RootCallTarget> constructorFunctions = module.getGlobalConstructorFunctions();
        List<RootCallTarget> destructorFunctions = module.getGlobalDestructorFunctions();

        LLVMNode[] globals = module.getGobalVariables(stack).toArray(new LLVMNode[0]);
        RootNode globalVarInits = new LLVMStaticInitsBlockNode(globals, frame, context, stack);
        RootCallTarget globalVarInitsTarget = Truffle.getRuntime().createCallTarget(globalVarInits);
        LLVMNode[] deallocs = module.getDeallocations();
        RootNode globalVarDeallocs = new LLVMStaticInitsBlockNode(deallocs, frame, context, stack);
        RootCallTarget globalVarDeallocsTarget = Truffle.getRuntime().createCallTarget(globalVarDeallocs);
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

    private final LLVMOptimizationConfiguration optimizationConfiguration;

    private final LLVMFrameDescriptors frames;

    private final LLVMLabelList labels;

    private final LLVMPhiManager phis;

    private final List<LLVMNode> deallocations = new ArrayList<>();

    private final Map<GlobalAlias, Symbol> aliases = new HashMap<>();

    private final Map<LLVMFunctionDescriptor, RootCallTarget> functions = new HashMap<>();

    private final Map<GlobalValueSymbol, LLVMAddressNode> variables = new HashMap<>();

    private final DataLayoutConverter.DataSpecConverter targetDataLayout;

    private final LLVMBitcodeTypeHelper typeHelper;

    public LLVMBitcodeVisitor(LLVMContext context, LLVMOptimizationConfiguration optimizationConfiguration, LLVMFrameDescriptors frames, LLVMLabelList labels, LLVMPhiManager phis,
                    TargetDataLayout layout) {
        this.context = context;
        this.optimizationConfiguration = optimizationConfiguration;
        this.frames = frames;
        this.labels = labels;
        this.phis = phis;
        if (layout != null) {
            final List<DataLayoutParser.DataTypeSpecification> dataLayout = DataLayoutParser.parseDataLayout(layout.getDataLayout());
            this.targetDataLayout = DataLayoutConverter.getConverter(dataLayout);
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
                        phis.getPhiMap(name));

        method.accept(visitor);

        LLVMBasicBlockNode[] basicBlocks = visitor.getBlocks();

        return LLVMBlockFactory.createFunctionBlock(
                        visitor.getReturnSlot(),
                        visitor.getBlocks(),
                        new LLVMStackFrameNuller[basicBlocks.length][0], visitor.getNullers());
    }

    private static List<LLVMNode> createParameters(FrameDescriptor frame, List<FunctionParameter> parameters) {
        List<LLVMNode> parameterNodes = new ArrayList<>();

        LLVMExpressionNode stack = LLVMFunctionFactory.createFunctionArgNode(0, LLVMBaseType.ADDRESS);
        parameterNodes.add(LLVMFrameReadWriteFactory.createFrameWrite(LLVMBaseType.ADDRESS, stack, frame.findFrameSlot(LLVMBitcodeHelper.STACK_ADDRESS_FRAME_SLOT_ID)));

        int argIndex = LLVMCallNode.ARG_START_INDEX;
        // if (resolve(functionHeader.getRettype()).isStruct()) {
        // LLVMExpressionNode functionRetParNode =
        // LLVMFunctionFactory.createFunctionArgNode(argIndex, paramType)e(argIndex++,
        // LLVMBaseType.STRUCT);
        // LLVMNode retValue = createAssignment((String) retSlot.getIdentifier(),
        // functionRetParNode, functionHeader.getRettype());
        // formalParamInits.add(retValue);
        // }
        for (FunctionParameter parameter : parameters) {
            LLVMBaseType llvmtype = LLVMBitcodeHelper.toBaseType(parameter.getType()).getType();
            LLVMExpressionNode parameterNode = LLVMFunctionFactory.createFunctionArgNode(argIndex++, llvmtype);
            FrameSlot slot = frame.findFrameSlot(parameter.getName());
            parameterNodes.add(LLVMFrameReadWriteFactory.createFrameWrite(llvmtype, parameterNode, slot));
        }
        return parameterNodes;
    }

    private LLVMNode createVariable(GlobalValueSymbol global, FrameSlot stack) {
        if (global == null || global.getValue() == null) {
            return null;
        } else {
            LLVMExpressionNode constant = LLVMBitcodeHelper.toConstantNode(global.getValue(), global.getAlign(), this::getGlobalVariable, context, stack, labels);
            if (constant != null) {
                Type type = ((PointerType) global.getType()).getPointeeType();
                LLVMBaseType baseType = LLVMBitcodeHelper.toBaseType(type).getType();
                int size = LLVMBitcodeHelper.getSize(type, global.getAlign());

                LLVMAddressLiteralNode globalVarAddress = (LLVMAddressLiteralNode) getGlobalVariable(global);

                if (size == 0) {
                    return null;
                } else {
                    LLVMNode store;
                    if (baseType == LLVMBaseType.ARRAY || baseType == LLVMBaseType.STRUCT) {
                        store = LLVMMemI32CopyFactory.create(globalVarAddress, (LLVMAddressNode) constant, new LLVMI32LiteralNode(size), new LLVMI32LiteralNode(0), new LLVMI1LiteralNode(false));
                    } else {
                        Type t = global.getValue().getType();
                        store = LLVMMemoryReadWriteFactory.createStore(globalVarAddress, constant, LLVMBitcodeHelper.toBaseType(t).getType(),
                                        LLVMBitcodeHelper.getSize(t, 0));
                    }
                    return store;
                }
            } else {
                return null;
            }
        }
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
            GlobalValueSymbol variable = (GlobalValueSymbol) g;
            LLVMAddressNode address = variables.get(variable);

            if (address == null) {
                Type type = ((PointerType) variable.getType()).getPointeeType();

                address = new LLVMAddressLiteralNode(LLVMHeap.allocateMemory(LLVMBitcodeHelper.getSize(type, variable.getAlign())));
                deallocations.add(LLVMFreeFactory.create(address));
                variables.put(variable, address);
            }
            return address;
        } else {
            return LLVMBitcodeHelper.toConstantNode(g, 0, this::getGlobalVariable, context, null, labels);
        }
    }

    public LLVMOptimizationConfiguration getOptimizationConfiguration() {
        return optimizationConfiguration;
    }

    public List<LLVMNode> getGobalVariables(FrameSlot stack) {
        List<LLVMNode> globals = new ArrayList<>();
        for (GlobalValueSymbol global : variables.keySet()) {
            LLVMNode store = createVariable(global, stack);
            if (store != null) {
                globals.add(store);
            }
        }
        return globals;
    }

    private List<RootCallTarget> getStructors(String name) {
        final List<RootCallTarget> structors = new ArrayList<>();
        for (GlobalValueSymbol global : variables.keySet()) {
            if (name.equals(global.getName())) {
                ArrayConstant arrayConstant = (ArrayConstant) global.getValue();
                for (int i = 0; i < arrayConstant.getElementCount(); i++) {
                    StructureConstant constant = (StructureConstant) arrayConstant.getElement(i);
                    FunctionDefinition functionDefinition = (FunctionDefinition) constant.getElement(1);
                    String functionName = functionDefinition.getName();
                    LLVMFunctionDescriptor functionDescriptor = getFunction(functionName);
                    structors.add(functions.get(functionDescriptor));
                }
                break;
            }
        }
        return structors;
    }

    public List<RootCallTarget> getGlobalConstructorFunctions() {
        return getStructors("@llvm.global_ctors");
    }

    public List<RootCallTarget> getGlobalDestructorFunctions() {
        return getStructors("@llvm.global_dtors");
    }

    @Override
    public void visit(GlobalAlias alias) {
        aliases.put(alias, alias.getValue());
    }

    @Override
    public void visit(GlobalConstant constant) {
        variables.put(constant, null);
    }

    @Override
    public void visit(GlobalVariable variable) {
        variables.put(variable, null);
    }

    @Override
    public void visit(FunctionDeclaration method) {
    }

    @Override
    public void visit(FunctionDefinition method) {
        FrameDescriptor frame = frames.getDescriptor(method.getName());

        List<LLVMNode> parameters = createParameters(frame, method.getParameters());

        LLVMExpressionNode body = createFunction(method);

        LLVMNode[] beforeFunction = parameters.toArray(new LLVMNode[parameters.size()]);
        LLVMNode[] afterFunction = new LLVMNode[0];

        LLVMFunctionStartNode rootNode = new LLVMFunctionStartNode(body, beforeFunction, afterFunction, null, frame, method.getName());
        if (LLVMBaseOptionFacade.printFunctionASTs()) {
            NodeUtil.printTree(System.out, rootNode);
        }

        LLVMRuntimeType llvmReturnType = LLVMBitcodeHelper.toRuntimeType(method.getReturnType());
        LLVMRuntimeType[] llvmParamTypes = LLVMBitcodeHelper.toRuntimeTypes(method.getArgumentTypes());
        LLVMFunctionDescriptor function = context.getFunctionRegistry().createFunctionDescriptor(method.getName(), llvmReturnType, llvmParamTypes, method.isVarArg());
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        functions.put(function, callTarget);
    }

    @Override
    public void visit(Type type) {
    }

}
