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
package com.oracle.truffle.llvm.parser.factories;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.asm.amd64.Parser;
import com.oracle.truffle.llvm.context.LLVMContext;
import com.oracle.truffle.llvm.context.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller.LLVMAddressNuller;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller.LLVMBooleanNuller;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller.LLVMByteNuller;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller.LLVMDoubleNuller;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller.LLVMFloatNuller;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller.LLVMIntNuller;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller.LLVMLongNuller;
import com.oracle.truffle.llvm.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.nodes.base.LLVMTerminatorNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode.LLVMResolvedDirectCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI32CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMStructCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.func.LLVMGlobalRootNode;
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMFreeFactory;
import com.oracle.truffle.llvm.nodes.literals.LLVMAggregateLiteralNode.LLVMEmptyStructLiteralNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressZeroNode;
import com.oracle.truffle.llvm.nodes.others.LLVMStaticInitsBlockNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVM80BitFloatUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMAddressUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMDoubleUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMFloatUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMFunctionUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMI16UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMI1UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMI64UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMI8UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.parser.api.LLVMBaseType;
import com.oracle.truffle.llvm.parser.api.LLVMType;
import com.oracle.truffle.llvm.parser.api.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.api.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.api.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.api.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.parser.api.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.api.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.api.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.api.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.api.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.api.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.api.model.types.PointerType;
import com.oracle.truffle.llvm.parser.api.model.types.Type;
import com.oracle.truffle.llvm.parser.api.model.types.VectorType;
import com.oracle.truffle.llvm.parser.api.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.api.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.types.NativeResolver;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;

public class NodeFactoryFacadeImpl implements NodeFactoryFacade {

    @Override
    public LLVMExpressionNode createInsertElement(LLVMParserRuntime runtime, LLVMBaseType resultType, LLVMExpressionNode vector, Object type, LLVMExpressionNode element,
                    LLVMExpressionNode index) {
        return LLVMVectorFactory.createInsertElement(runtime, resultType, type, vector, element, index);
    }

    @Override
    public LLVMExpressionNode createExtractElement(LLVMParserRuntime runtime, LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index) {
        return LLVMVectorFactory.createExtractElement(resultType, vector, index);
    }

    @Override
    public LLVMExpressionNode createShuffleVector(LLVMParserRuntime runtime, LLVMBaseType llvmType, LLVMExpressionNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2,
                    LLVMExpressionNode mask) {
        return LLVMVectorFactory.createShuffleVector(llvmType, target, vector1, vector2, mask);
    }

    @Override
    public LLVMExpressionNode createLoad(LLVMParserRuntime runtime, Type resolvedResultType, LLVMExpressionNode loadTarget) {
        return LLVMMemoryReadWriteFactory.createLoad(resolvedResultType, loadTarget);
    }

    @Override
    public LLVMExpressionNode createStore(LLVMParserRuntime runtime, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        return LLVMMemoryReadWriteFactory.createStore(runtime, pointerNode, valueNode, type);
    }

    @Override
    public LLVMExpressionNode createLogicalOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType type, LLVMBaseType llvmType,
                    LLVMExpressionNode target) {
        return LLVMLogicalFactory.createLogicalOperation(left, right, type, llvmType, target);
    }

    @Override
    public LLVMExpressionNode createUndefinedValue(LLVMParserRuntime runtime, Type t) {
        return LLVMLiteralFactory.createUndefinedValue(runtime, t);
    }

    @Override
    public LLVMExpressionNode createSimpleConstantNoArray(LLVMParserRuntime runtime, String stringValue, LLVMBaseType instructionType, Type type) {
        return LLVMLiteralFactory.createSimpleConstantNoArray(stringValue, instructionType, type);
    }

    @Override
    public LLVMExpressionNode createVectorLiteralNode(LLVMParserRuntime runtime, List<LLVMExpressionNode> listValues, LLVMExpressionNode target, LLVMBaseType type) {
        return LLVMLiteralFactory.createVectorLiteralNode(listValues, target, type);
    }

    @Override
    public LLVMExpressionNode tryCreateFunctionSubstitution(LLVMParserRuntime runtime, FunctionType declaration, LLVMExpressionNode[] argNodes, int numberOfExplicitArguments) {
        String functionName = declaration.getName();
        if (functionName.startsWith("@llvm")) {
            return LLVMIntrinsicFactory.create(declaration, argNodes, numberOfExplicitArguments, runtime);
        } else if (functionName.startsWith("@truffle")) {
            return LLVMTruffleIntrinsicFactory.create(functionName, argNodes);
        } else {
            return null;
        }
    }

    @Override
    public LLVMControlFlowNode createRetVoid(LLVMParserRuntime runtime) {
        return LLVMFunctionFactory.createRetVoid(runtime);
    }

    @Override
    public LLVMControlFlowNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type resolvedType) {
        return LLVMFunctionFactory.createNonVoidRet(runtime, retValue, resolvedType);
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType) {
        return LLVMFunctionFactory.createFunctionArgNode(argIndex, paramType);
    }

    @Override
    public LLVMExpressionNode createFunctionCall(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, LLVMType[] argTypes, LLVMBaseType llvmType) {
        return LLVMFunctionFactory.createFunctionCall(functionNode, argNodes, argTypes, llvmType);
    }

    @Override
    public LLVMExpressionNode createFrameRead(LLVMParserRuntime runtime, LLVMBaseType llvmType, FrameSlot frameSlot) {
        return LLVMFrameReadWriteFactory.createFrameRead(llvmType, frameSlot);
    }

    @Override
    public LLVMExpressionNode createFrameWrite(LLVMParserRuntime runtime, LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot) {
        return LLVMFrameReadWriteFactory.createFrameWrite(llvmType, result, slot);
    }

    @Override
    public FrameSlotKind getFrameSlotKind(LLVMParserRuntime runtime, Type type) {
        return LLVMFrameReadWriteFactory.getFrameSlotKind(type);
    }

    @Override
    public LLVMExpressionNode createComparison(LLVMParserRuntime runtime, CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        return LLVMComparisonFactory.toCompareVectorNode(runtime, operator, type, lhs, rhs);
    }

    @Override
    public LLVMExpressionNode createCast(LLVMParserRuntime runtime, LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType type) {
        return LLVMCastsFactory.cast(fromNode, targetType, fromType, type);
    }

    @Override
    public LLVMExpressionNode createArithmeticOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType type, LLVMBaseType llvmType,
                    LLVMExpressionNode target) {
        return LLVMArithmeticFactory.createArithmeticOperation(left, right, type, llvmType, target);
    }

    @Override
    public LLVMExpressionNode createExtractValue(LLVMParserRuntime runtime, LLVMBaseType type, LLVMExpressionNode targetAddress) {
        return LLVMAggregateFactory.createExtractValue(type, targetAddress);
    }

    @Override
    public LLVMExpressionNode createGetElementPtr(LLVMParserRuntime runtime, LLVMBaseType llvmBaseType, LLVMExpressionNode currentAddress, LLVMExpressionNode valueRef, int indexedTypeLength) {
        return LLVMGetElementPtrFactory.create(llvmBaseType, currentAddress, valueRef, indexedTypeLength);
    }

    @Override
    public LLVMExpressionNode createSelect(LLVMParserRuntime runtime, Type type, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        LLVMBaseType llvmType = type.getLLVMBaseType();
        if (type instanceof VectorType) {
            final LLVMExpressionNode target = runtime.allocateVectorResult(type);
            return LLVMSelectFactory.createSelectVector(llvmType, target, condition, trueValue, falseValue);
        } else {
            return LLVMSelectFactory.createSelect(llvmType, condition, trueValue, falseValue);
        }
    }

    @Override
    public LLVMExpressionNode createZeroVectorInitializer(LLVMParserRuntime runtime, int nrElements, LLVMExpressionNode target, LLVMBaseType llvmType) {
        return LLVMLiteralFactory.createZeroVectorInitializer(nrElements, target, llvmType);
    }

    @Override
    public LLVMExpressionNode createLiteral(LLVMParserRuntime runtime, Object value, LLVMBaseType type) {
        return LLVMLiteralFactory.createLiteral(value, type);
    }

    @Override
    public LLVMControlFlowNode createUnreachableNode(LLVMParserRuntime runtime) {
        return new LLVMUnreachableNode();
    }

    @Override
    public LLVMControlFlowNode createIndirectBranch(LLVMParserRuntime runtime, LLVMExpressionNode value, int[] labelTargets, LLVMExpressionNode[] phiWrites) {
        return LLVMBranchFactory.createIndirectBranch(value, labelTargets, phiWrites);
    }

    @Override
    public LLVMControlFlowNode createSwitch(LLVMParserRuntime runtime, LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases,
                    LLVMBaseType llvmType, LLVMExpressionNode[] phiWriteNodes) {
        return LLVMSwitchFactory.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, phiWriteNodes);
    }

    @Override
    public LLVMControlFlowNode createConditionalBranch(LLVMParserRuntime runtime, int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMExpressionNode[] truePhiWriteNodes,
                    LLVMExpressionNode[] falsePhiWriteNodes) {
        return LLVMBranchFactory.createConditionalBranch(trueIndex, falseIndex, conditionNode, truePhiWriteNodes, falsePhiWriteNodes);
    }

    @Override
    public LLVMControlFlowNode createUnconditionalBranch(LLVMParserRuntime runtime, int unconditionalIndex, LLVMExpressionNode[] phiWrites) {
        return LLVMBranchFactory.createUnconditionalBranch(unconditionalIndex, phiWrites);
    }

    @Override
    public LLVMExpressionNode createArrayLiteral(LLVMParserRuntime runtime, List<LLVMExpressionNode> arrayValues, Type arrayType) {
        return LLVMLiteralFactory.createArrayLiteral(runtime, arrayValues, (ArrayType) arrayType);
    }

    @Override
    public LLVMExpressionNode createAlloc(LLVMParserRuntime runtime, Type type, int byteSize, int alignment, LLVMBaseType llvmType, LLVMExpressionNode numElements) {
        if (numElements == null) {
            assert llvmType == null;
            return LLVMAllocFactory.createAlloc(runtime, byteSize, alignment);
        } else {
            return LLVMAllocFactory.createAlloc(runtime, llvmType, numElements, byteSize, alignment);
        }
    }

    @Override
    public LLVMExpressionNode createInsertValue(LLVMParserRuntime runtime, LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset,
                    LLVMExpressionNode valueToInsert, LLVMBaseType llvmType) {
        return LLVMAggregateFactory.createInsertValue(resultAggregate, sourceAggregate, size, offset, valueToInsert, llvmType);
    }

    @Override
    public LLVMExpressionNode createZeroNode(LLVMParserRuntime runtime, LLVMExpressionNode addressNode, int size) {
        return new LLVMAddressZeroNode(addressNode, size);
    }

    @Override
    public LLVMExpressionNode createEmptyStructLiteralNode(LLVMParserRuntime runtime, LLVMExpressionNode alloca, int byteSize) {
        return new LLVMEmptyStructLiteralNode(alloca, byteSize);
    }

    @Override
    public LLVMGlobalRootNode createGlobalRootNode(LLVMParserRuntime runtime, RootCallTarget mainCallTarget,
                    Object[] args, Source sourceFile, LLVMRuntimeType[] mainTypes) {
        return LLVMRootNodeFactory.createGlobalRootNode(runtime, mainCallTarget, args, sourceFile, mainTypes);
    }

    @Override
    public RootNode createGlobalRootNodeWrapping(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, LLVMRuntimeType returnType) {
        return LLVMFunctionFactory.createGlobalRootNodeWrapping(mainCallTarget, returnType);
    }

    @Override
    public LLVMExpressionNode createStructureConstantNode(LLVMParserRuntime runtime, Type structType, boolean packed, Type[] types, LLVMExpressionNode[] constants) {
        return LLVMAggregateFactory.createStructConstantNode(runtime, structType, packed, types, constants);
    }

    @Override
    public LLVMExpressionNode createBasicBlockNode(LLVMParserRuntime runtime, LLVMExpressionNode[] statementNodes, LLVMControlFlowNode terminatorNode, int blockId, String blockName) {
        return LLVMBlockFactory.createBasicBlock(statementNodes, (LLVMTerminatorNode) terminatorNode, blockId, blockName);
    }

    @Override
    public LLVMExpressionNode createFunctionBlockNode(LLVMParserRuntime runtime, FrameSlot retSlot, List<? extends LLVMExpressionNode> allFunctionNodes, LLVMStackFrameNuller[][] beforeSlotNullerNodes,
                    LLVMStackFrameNuller[][] afterSlotNullerNodes) {
        return LLVMBlockFactory.createFunctionBlock(retSlot, allFunctionNodes.toArray(new LLVMBasicBlockNode[allFunctionNodes.size()]), beforeSlotNullerNodes, afterSlotNullerNodes);
    }

    @Override
    public RootNode createFunctionStartNode(LLVMParserRuntime runtime, LLVMExpressionNode functionBodyNode, LLVMExpressionNode[] beforeFunction, LLVMExpressionNode[] afterFunction,
                    SourceSection sourceSection,
                    FrameDescriptor frameDescriptor,
                    FunctionDefinition functionHeader) {
        LLVMStackFrameNuller[] nullers = new LLVMStackFrameNuller[frameDescriptor.getSlots().size()];
        int i = 0;
        for (FrameSlot slot : frameDescriptor.getSlots()) {
            String identifier = (String) slot.getIdentifier();
            Type slotType = runtime.getVariableNameTypesMapping().get(identifier);
            if (slot.equals(runtime.getReturnSlot())) {
                nullers[i] = runtime.getNodeFactoryFacade().createFrameNuller(runtime, identifier, LLVMTypeHelper.getLLVMType(functionHeader.getReturnType()), slot);
            } else if (slot.equals(runtime.getStackPointerSlot())) {
                nullers[i] = runtime.getNodeFactoryFacade().createFrameNuller(runtime, identifier, new LLVMType(LLVMBaseType.ADDRESS), slot);
            } else {
                assert slotType != null : identifier;
                nullers[i] = runtime.getNodeFactoryFacade().createFrameNuller(runtime, identifier, LLVMTypeHelper.getLLVMType(slotType), slot);
            }
            i++;
        }
        return new LLVMFunctionStartNode(functionBodyNode, beforeFunction, afterFunction, sourceSection, frameDescriptor, functionHeader, nullers);
    }

    @Override
    public Optional<Integer> getArgStartIndex() {
        return Optional.of(LLVMCallNode.ARG_START_INDEX);
    }

    @Override
    public LLVMExpressionNode createInlineAssemblerExpression(LLVMParserRuntime runtime, String asmExpression, String asmFlags, LLVMExpressionNode[] args, LLVMType[] argTypes, LLVMBaseType retType) {
        Parser asmParser = new Parser(asmExpression, asmFlags, args, retType);
        LLVMInlineAssemblyRootNode assemblyRoot = asmParser.Parse();
        CallTarget target = Truffle.getRuntime().createCallTarget(assemblyRoot);
        switch (retType) {
            case VOID:
                return new LLVMUnsupportedInlineAssemblerNode();
            case I1:
                return new LLVMI1UnsupportedInlineAssemblerNode();
            case I8:
                return new LLVMI8UnsupportedInlineAssemblerNode();
            case I16:
                return new LLVMI16UnsupportedInlineAssemblerNode();
            case I32:
                return LLVMI32CallUnboxNodeGen.create(new LLVMResolvedDirectCallNode(target, args, argTypes));
            case I64:
                return new LLVMI64UnsupportedInlineAssemblerNode();
            case FLOAT:
                return new LLVMFloatUnsupportedInlineAssemblerNode();
            case DOUBLE:
                return new LLVMDoubleUnsupportedInlineAssemblerNode();
            case X86_FP80:
                return new LLVM80BitFloatUnsupportedInlineAssemblerNode();
            case ADDRESS:
                return new LLVMAddressUnsupportedInlineAssemblerNode();
            case FUNCTION_ADDRESS:
                return new LLVMFunctionUnsupportedInlineAssemblerNode();
            case STRUCT:
                return LLVMStructCallUnboxNodeGen.create(new LLVMResolvedDirectCallNode(target, args, argTypes));
            default:
                throw new AssertionError(retType);
        }
    }

    @Override
    public Map<String, NodeFactory<? extends LLVMExpressionNode>> getFunctionSubstitutionFactories() {
        return LLVMRuntimeIntrinsicFactory.getFunctionSubstitutionFactories();
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int i, Class<? extends Node> clazz) {
        return LLVMFunctionFactory.createFunctionArgNode(this, i);
    }

    @Override
    public RootNode createFunctionSubstitutionRootNode(LLVMExpressionNode intrinsicNode) {
        return LLVMFunctionFactory.createFunctionSubstitutionRootNode(intrinsicNode);
    }

    @Override
    public Object allocateGlobalVariable(LLVMParserRuntime runtime, GlobalVariable globalVariable) {
        final String name = globalVariable.getName();

        final NativeResolver nativeResolver = () -> LLVMAddress.fromLong(runtime.getNativeHandle(name));

        final LLVMGlobalVariableDescriptor descriptor;
        if (globalVariable.isStatic()) {
            descriptor = new LLVMGlobalVariableDescriptor(name, nativeResolver);
        } else {
            final LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());
            descriptor = context.getGlobalVariableRegistry().lookupOrAdd(name, nativeResolver);
        }

        if ((globalVariable.getInitialiser() > 0 || !globalVariable.isExtern()) && !descriptor.isDeclared()) {
            final Type resolvedType = ((PointerType) globalVariable.getType()).getPointeeType();
            final int byteSize = runtime.getByteSize(resolvedType);
            final LLVMAddress nativeStorage = LLVMHeap.allocateMemory(byteSize);
            final LLVMExpressionNode addressLiteralNode = createLiteral(runtime, nativeStorage, LLVMBaseType.ADDRESS);
            runtime.addDestructor(LLVMFreeFactory.create(addressLiteralNode));
            descriptor.declare(nativeStorage);
        }

        return descriptor;
    }

    @Override
    public Object allocateGlobalConstant(LLVMParserRuntime runtime, GlobalConstant globalConstant) {
        final String name = globalConstant.getName();

        final NativeResolver nativeResolver = () -> LLVMAddress.fromLong(runtime.getNativeHandle(name));

        final LLVMGlobalVariableDescriptor descriptor;
        if (globalConstant.isStatic()) {
            descriptor = new LLVMGlobalVariableDescriptor(name, nativeResolver);
        } else {
            final LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());
            descriptor = context.getGlobalVariableRegistry().lookupOrAdd(name, nativeResolver);
        }

        if ((globalConstant.getInitialiser() > 0 || !globalConstant.isExtern()) && !descriptor.isDeclared()) {
            final Type resolvedType = ((PointerType) globalConstant.getType()).getPointeeType();
            final int byteSize = runtime.getByteSize(resolvedType);
            final LLVMAddress nativeStorage = LLVMHeap.allocateMemory(byteSize);
            final LLVMExpressionNode addressLiteralNode = createLiteral(runtime, nativeStorage, LLVMBaseType.ADDRESS);
            runtime.addDestructor(LLVMFreeFactory.create(addressLiteralNode));
            descriptor.declare(nativeStorage);
        }

        return descriptor;
    }

    @Override
    public RootNode createStaticInitsRootNode(LLVMParserRuntime runtime, LLVMExpressionNode[] staticInits) {
        return new LLVMStaticInitsBlockNode(staticInits, runtime.getGlobalFrameDescriptor(), LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0()),
                        runtime.getStackPointerSlot());
    }

    @Override
    public Optional<Boolean> hasStackPointerArgument(LLVMParserRuntime runtime) {
        return Optional.of(true);
    }

    @Override
    public LLVMStackFrameNuller createFrameNuller(LLVMParserRuntime runtime, String identifier, LLVMType llvmType, FrameSlot slot) {
        switch (slot.getKind()) {
            case Boolean:
                return new LLVMBooleanNuller(slot);
            case Byte:
                return new LLVMByteNuller(slot);
            case Int:
                return new LLVMIntNuller(slot);
            case Long:
                return new LLVMLongNuller(slot);
            case Float:
                return new LLVMFloatNuller(slot);
            case Double:
                return new LLVMDoubleNuller(slot);
            case Object:
                /*
                 * It would be cleaner to not distinguish between the frame slot kinds, and use the
                 * variable type instead. We cannot simply set the object to null, because phis that
                 * have null and other Objects inside escape and are allocated. We set a null
                 * address here, since other Sulong data types that use Object are implemented
                 * inefficiently anyway. In the long term, they should have their own stack nuller.
                 */
                return new LLVMAddressNuller(slot);
            case Illegal:
                if (LLVMOptions.DEBUG.debug()) {
                    LLVMLogger.info("illegal frame slot at stack nuller: " + identifier);
                }
                return new LLVMAddressNuller(slot);
            default:
                throw new AssertionError();
        }

    }

    @Override
    public LLVMFunction createFunctionDescriptor(String name, LLVMRuntimeType returnType, boolean varArgs, LLVMRuntimeType[] paramTypes, int functionIndex) {
        return LLVMFunctionDescriptor.create(name, returnType, paramTypes, varArgs, functionIndex);
    }

    @Override
    public LLVMFunction createAndRegisterFunctionDescriptor(LLVMParserRuntime runtime, String name, LLVMRuntimeType convertType, boolean varArgs, LLVMRuntimeType[] convertTypes) {
        return LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0()).getFunctionRegistry().createFunctionDescriptor(name, convertType, convertTypes, varArgs);
    }

}
