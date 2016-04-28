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

import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.BitwiseBinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.GlobalVariable;
import com.intel.llvm.ireditor.lLVM_IR.Type;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMTerminatorNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMVectorNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMGlobalRootNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMFreeFactory;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMAggregateLiteralNode.LLVMEmptyStructLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAddressZeroNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMStaticInitsBlockNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVM80BitFloatUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMAddressUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMDoubleUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMFloatUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMFunctionUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMI16UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMI1UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMI32UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMI64UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMI8UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.parser.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;

public class NodeFactoryFacadeImpl implements NodeFactoryFacade {

    protected LLVMParserRuntime runtime;

    public NodeFactoryFacadeImpl(LLVMParserRuntime runtime) {
        this.runtime = runtime;
    }

    public void setParserRuntime(LLVMParserRuntime runtime) {
        this.runtime = runtime;
    }

    public NodeFactoryFacadeImpl() {
    }

    @Override
    public LLVMVectorNode createInsertElement(LLVMBaseType resultType, LLVMExpressionNode vector, Type vectorType, LLVMExpressionNode element, LLVMExpressionNode index) {
        return LLVMVectorFactory.createInsertElement(runtime, resultType, vector, vectorType, element, (LLVMI32Node) index);
    }

    @Override
    public LLVMExpressionNode createExtractElement(LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index) {
        return LLVMVectorFactory.createExtractElement(resultType, vector, index);
    }

    @Override
    public LLVMVectorNode createShuffleVector(LLVMBaseType llvmType, LLVMExpressionNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2, LLVMExpressionNode mask) {
        return LLVMVectorFactory.createShuffleVector(llvmType, (LLVMAddressNode) target, vector1, vector2, (LLVMI32VectorNode) mask);
    }

    @Override
    public LLVMExpressionNode createLoad(ResolvedType resolvedResultType, LLVMExpressionNode loadTarget) {
        return LLVMMemoryReadWriteFactory.createLoad(resolvedResultType, (LLVMAddressNode) loadTarget, runtime);
    }

    @Override
    public LLVMNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, ResolvedType type) {
        return LLVMMemoryReadWriteFactory.createStore((LLVMAddressNode) pointerNode, valueNode, type);
    }

    @Override
    public LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType type, LLVMBaseType llvmType, LLVMExpressionNode target) {
        return LLVMLogicalFactory.createLogicalOperation(left, right, type, llvmType, (LLVMAddressNode) target);
    }

    @Override
    public LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, BitwiseBinaryInstruction type, LLVMBaseType llvmType, LLVMExpressionNode target) {
        return LLVMLogicalFactory.createLogicalOperation(left, right, type, llvmType, (LLVMAddressNode) target);
    }

    @Override
    public LLVMExpressionNode createUndefinedValue(EObject t) {
        return LLVMLiteralFactory.createUndefinedValue(runtime, t);
    }

    @Override
    public LLVMExpressionNode createSimpleConstantNoArray(String stringValue, LLVMBaseType instructionType, ResolvedType type) {
        return LLVMLiteralFactory.createSimpleConstantNoArray(stringValue, instructionType, type);
    }

    @Override
    public LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, LLVMExpressionNode target, LLVMBaseType type) {
        return LLVMLiteralFactory.createVectorLiteralNode(listValues, (LLVMAddressNode) target, type);
    }

    @Override
    public LLVMNode createLLVMIntrinsic(String functionName, Object[] argNodes, FunctionDef def) {
        return LLVMIntrinsicFactory.create(functionName, argNodes, def, runtime);
    }

    @Override
    public LLVMTerminatorNode createRetVoid() {
        return LLVMFunctionFactory.createRetVoid(runtime);
    }

    @Override
    public LLVMTerminatorNode createNonVoidRet(LLVMExpressionNode retValue, ResolvedType resolvedType) {
        return LLVMFunctionFactory.createNonVoidRet(runtime, retValue, resolvedType);
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType) {
        return LLVMFunctionFactory.createFunctionArgNode(argIndex, paramType);
    }

    @Override
    public LLVMNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, LLVMBaseType llvmType) {
        return LLVMFunctionFactory.createFunctionCall((LLVMFunctionNode) functionNode, argNodes, llvmType);
    }

    @Override
    public LLVMExpressionNode createFrameRead(LLVMBaseType llvmType, FrameSlot frameSlot) {
        return LLVMFrameReadWriteFactory.createFrameRead(llvmType, frameSlot);
    }

    @Override
    public LLVMNode createFrameWrite(LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot) {
        return LLVMFrameReadWriteFactory.createFrameWrite(llvmType, result, slot);
    }

    @Override
    public FrameSlotKind getFrameSlotKind(ResolvedType type) {
        return LLVMFrameReadWriteFactory.getFrameSlotKind(type);
    }

    @Override
    public LLVMI1Node createIntegerComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMIntegerComparisonType type) {
        return LLVMComparisonFactory.createIntegerComparison(left, right, llvmType, type);
    }

    @Override
    public LLVMI1Node createFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMFloatComparisonType conditionString) {
        return LLVMComparisonFactory.createFloatComparison(left, right, llvmType, conditionString);
    }

    @Override
    public LLVMExpressionNode createCast(LLVMExpressionNode fromNode, ResolvedType targetType, ResolvedType fromType, LLVMConversionType type) {
        return LLVMCastsFactory.cast(fromNode, targetType, fromType, type);
    }

    @Override
    public LLVMExpressionNode createArithmeticOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType type, LLVMBaseType llvmType, LLVMExpressionNode target) {
        return LLVMArithmeticFactory.createArithmeticOperation(left, right, type, llvmType, (LLVMAddressNode) target);
    }

    @Override
    public LLVMExpressionNode createExtractValue(LLVMBaseType type, LLVMExpressionNode targetAddress) {
        return LLVMAggregateFactory.createExtractValue(type, (LLVMAddressNode) targetAddress);
    }

    @Override
    public LLVMAddressNode createGetElementPtr(LLVMBaseType llvmBaseType, LLVMExpressionNode currentAddress, LLVMExpressionNode valueRef, int indexedTypeLength) {
        return LLVMGetElementPtrFactory.create(llvmBaseType, (LLVMAddressNode) currentAddress, valueRef, indexedTypeLength);
    }

    @Override
    public Class<?> getJavaClass(LLVMExpressionNode node) {
        return LLVMNativeFactory.getJavaClass(node);
    }

    @Override
    public LLVMExpressionNode createSelect(LLVMBaseType llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        return LLVMSelectFactory.createSelect(llvmType, condition, trueValue, falseValue, runtime);
    }

    @Override
    public LLVMExpressionNode createZeroVectorInitializer(int nrElements, LLVMExpressionNode target, LLVMBaseType llvmType) {
        return LLVMLiteralFactory.createZeroVectorInitializer(nrElements, (LLVMAddressNode) target, llvmType);
    }

    @Override
    public LLVMExpressionNode createLiteral(Object value, LLVMBaseType type) {
        return LLVMLiteralFactory.createLiteral(value, type);
    }

    @Override
    public LLVMNode createUnreachableNode() {
        return new LLVMUnreachableNode();
    }

    @Override
    public LLVMNode createIndirectBranch(LLVMExpressionNode value, int[] labelTargets, LLVMNode[] phiWrites) {
        return LLVMBranchFactory.createIndirectBranch(value, labelTargets, phiWrites);
    }

    @Override
    public LLVMTerminatorNode createSwitch(LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases,
                    LLVMBaseType llvmType, LLVMNode[] phiWriteNodes) {
        return LLVMSwitchFactory.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, phiWriteNodes);
    }

    @Override
    public LLVMTerminatorNode createConditionalBranch(int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMNode[] truePhiWriteNodes, LLVMNode[] falsePhiWriteNodes) {
        return LLVMBranchFactory.createConditionalBranch(runtime, trueIndex, falseIndex, conditionNode, truePhiWriteNodes, falsePhiWriteNodes);
    }

    @Override
    public LLVMTerminatorNode createUnconditionalBranch(int unconditionalIndex, LLVMNode[] phiWrites) {
        return LLVMBranchFactory.createUnconditionalBranch(unconditionalIndex, phiWrites);
    }

    @Override
    public LLVMAddressNode createArrayLiteral(List<LLVMExpressionNode> arrayValues, ResolvedType arrayType) {
        return LLVMLiteralFactory.createArrayLiteral(runtime, arrayValues, arrayType);
    }

    @Override
    public LLVMExpressionNode createAlloc(ResolvedType type, int byteSize, int alignment, LLVMBaseType llvmType, LLVMExpressionNode numElements) {
        if (numElements == null) {
            assert llvmType == null;
            return LLVMAllocFactory.createAlloc(runtime, byteSize, alignment);
        } else {
            return LLVMAllocFactory.createAlloc(runtime, llvmType, numElements, byteSize, alignment);
        }
    }

    @Override
    public LLVMExpressionNode createInsertValue(LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset, LLVMExpressionNode valueToInsert, LLVMBaseType llvmType) {
        return LLVMAggregateFactory.createInsertValue((LLVMAddressNode) resultAggregate, (LLVMAddressNode) sourceAggregate, size, offset, valueToInsert, llvmType);
    }

    @Override
    public LLVMExpressionNode createZeroNode(LLVMExpressionNode addressNode, int size) {
        return new LLVMAddressZeroNode((LLVMAddressNode) addressNode, size);
    }

    @Override
    public LLVMExpressionNode createEmptyStructLiteralNode(LLVMExpressionNode alloca, int byteSize) {
        return new LLVMEmptyStructLiteralNode((LLVMAddressNode) alloca, byteSize);
    }

    @Override
    public LLVMGlobalRootNode createGlobalRootNode(RootCallTarget mainCallTarget,
                    Object[] args, Source sourceFile, LLVMRuntimeType[] mainTypes) {
        return LLVMRootNodeFactory.createGlobalRootNode(runtime, mainCallTarget, args, sourceFile, mainTypes);
    }

    @Override
    public RootNode createGlobalRootNodeWrapping(RootCallTarget mainCallTarget, LLVMRuntimeType returnType) {
        return LLVMFunctionFactory.createGlobalRootNodeWrapping(mainCallTarget, returnType);
    }

    @Override
    public LLVMExpressionNode createStructureConstantNode(ResolvedType structType, boolean packed, ResolvedType[] types, LLVMExpressionNode[] constants) {
        return LLVMAggregateFactory.createStructConstantNode(runtime, structType, packed, types, constants);
    }

    @Override
    public LLVMNode createBasicBlockNode(LLVMNode[] statementNodes, LLVMNode terminatorNode) {
        return LLVMBlockFactory.createBasicBlock(statementNodes, (LLVMTerminatorNode) terminatorNode);
    }

    @Override
    public LLVMExpressionNode createFunctionBlockNode(FrameSlot retSlot, List<LLVMNode> allFunctionNodes, LLVMStackFrameNuller[][] indexToSlotNuller) {
        return LLVMBlockFactory.createFunctionBlock(retSlot, allFunctionNodes.toArray(new LLVMBasicBlockNode[allFunctionNodes.size()]), indexToSlotNuller);
    }

    @Override
    public RootNode createFunctionStartNode(LLVMExpressionNode functionBodyNode, LLVMNode[] beforeFunction, LLVMNode[] afterFunction, FrameDescriptor frameDescriptor, String functionName) {
        return new LLVMFunctionStartNode(functionBodyNode, beforeFunction, afterFunction, frameDescriptor, functionName);
    }

    @Override
    public Optional<Integer> getArgStartIndex() {
        return Optional.of(LLVMCallNode.ARG_START_INDEX);
    }

    @Override
    public LLVMNode createInlineAssemblerExpression(String asmExpression, String asmFlags, LLVMExpressionNode[] args, LLVMBaseType retType) {
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
                return new LLVMI32UnsupportedInlineAssemblerNode();
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
            default:
                throw new AssertionError(retType);
        }
    }

    @Override
    public Map<String, NodeFactory<? extends LLVMNode>> getFunctionSubstitutionFactories(LLVMOptimizationConfiguration optConfig) {
        return LLVMRuntimeIntrinsicFactory.getFunctionSubstitutionFactories(optConfig);
    }

    @Override
    public LLVMNode createFunctionArgNode(int i, Class<? extends Node> clazz) {
        return LLVMFunctionFactory.createFunctionArgNode(i, clazz);
    }

    @Override
    public RootNode createFunctionSubstitutionRootNode(LLVMNode intrinsicNode) {
        return LLVMFunctionFactory.createFunctionSubstitutionRootNode(intrinsicNode);
    }

    @Override
    public LLVMFunctionDescriptor createFunctionDescriptor(String name, LLVMRuntimeType convertType, LLVMRuntimeType[] convertTypes, boolean varArgs) {
        return LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0()).getFunctionRegistry().createFunctionDescriptor(name, convertType, convertTypes, varArgs);
    }

    @Override
    public Object allocateGlobalVariable(GlobalVariable globalVariable) {
        ResolvedType resolvedType = runtime.resolve(globalVariable.getType());
        int byteSize = LLVMTypeHelper.getByteSize(resolvedType);
        LLVMAddress allocation = LLVMHeap.allocateMemory(byteSize);
        LLVMAddressNode addressLiteralNode = (LLVMAddressNode) createLiteral(allocation, LLVMBaseType.ADDRESS);
        runtime.addDestructor(LLVMFreeFactory.create(addressLiteralNode));
        return allocation;
    }

    @Override
    public RootNode createStaticInitsRootNode(LLVMNode[] staticInits) {
        return new LLVMStaticInitsBlockNode(staticInits, runtime.getGlobalFrameDescriptor(), LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0()),
                        runtime.getStackPointerSlot());
    }

}
