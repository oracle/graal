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
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

/**
 * This class implements an abstract adapter that returns <code>null</code> for each implemented
 * method.
 */
public class NodeFactoryFacadeAdapter implements NodeFactoryFacade {

    @Override
    public LLVMExpressionNode createInsertElement(LLVMBaseType resultType, LLVMExpressionNode vector, Type vectorType, LLVMExpressionNode element, LLVMExpressionNode index) {
        return null;
    }

    @Override
    public LLVMExpressionNode createExtractElement(LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index) {
        return null;
    }

    @Override
    public LLVMExpressionNode createShuffleVector(LLVMBaseType llvmType, LLVMExpressionNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2, LLVMExpressionNode mask) {
        return null;
    }

    @Override
    public LLVMExpressionNode createLoad(ResolvedType resolvedResultType, LLVMExpressionNode loadTarget) {
        return null;
    }

    @Override
    public LLVMNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, ResolvedType type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType opCode, LLVMBaseType llvmType, LLVMExpressionNode target) {
        return null;
    }

    @Override
    public LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, BitwiseBinaryInstruction type, LLVMBaseType llvmType, LLVMExpressionNode target) {
        return null;
    }

    @Override
    public LLVMExpressionNode createUndefinedValue(EObject t) {
        return null;
    }

    @Override
    public LLVMExpressionNode createLiteral(Object value, LLVMBaseType type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createSimpleConstantNoArray(String stringValue, LLVMBaseType instructionType, ResolvedType type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, LLVMExpressionNode target, LLVMBaseType type) {
        return null;
    }

    @Override
    public LLVMNode createLLVMIntrinsic(String functionName, Object[] argNodes, FunctionDef functionDef) {
        return null;
    }

    @Override
    public LLVMNode createTruffleIntrinsic(String functionName, LLVMExpressionNode[] argNodes) {
        return null;
    }

    @Override
    public LLVMNode createRetVoid() {
        return null;
    }

    @Override
    public LLVMNode createNonVoidRet(LLVMExpressionNode retValue, ResolvedType resolvedType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType) {
        return null;
    }

    @Override
    public LLVMNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, LLVMBaseType llvmType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createFrameRead(LLVMBaseType llvmType, FrameSlot frameSlot) {
        return null;
    }

    @Override
    public LLVMNode createFrameWrite(LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot) {
        return null;
    }

    @Override
    public FrameSlotKind getFrameSlotKind(ResolvedType type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createIntegerComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMIntegerComparisonType type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMFloatComparisonType type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createCast(LLVMExpressionNode fromNode, ResolvedType targetType, ResolvedType fromType, LLVMConversionType type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createArithmeticOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, LLVMBaseType llvmType, LLVMExpressionNode target) {
        return null;
    }

    @Override
    public LLVMExpressionNode createExtractValue(LLVMBaseType type, LLVMExpressionNode targetAddress) {
        return null;
    }

    @Override
    public LLVMExpressionNode createGetElementPtr(LLVMBaseType llvmBaseType, LLVMExpressionNode currentAddress, LLVMExpressionNode valueRef, int indexedTypeLength) {
        return null;
    }

    @Override
    public Class<?> getJavaClass(LLVMExpressionNode llvmExpressionNode) {
        return null;
    }

    @Override
    public LLVMExpressionNode createSelect(LLVMBaseType llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        return null;
    }

    @Override
    public LLVMExpressionNode createZeroVectorInitializer(int nrElements, LLVMExpressionNode target, LLVMBaseType llvmType) {
        return null;
    }

    @Override
    public LLVMNode createUnreachableNode() {
        return null;
    }

    @Override
    public LLVMNode createIndirectBranch(LLVMExpressionNode value, int[] labelTargets, LLVMNode[] phiWrites) {
        return null;
    }

    @Override
    public LLVMNode createSwitch(LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases, LLVMBaseType llvmType, LLVMNode[] phiWriteNodes) {
        return null;
    }

    @Override
    public LLVMNode createConditionalBranch(int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMNode[] truePhiWriteNodes, LLVMNode[] falsePhiWriteNodes) {
        return null;
    }

    @Override
    public LLVMNode createUnconditionalBranch(int unconditionalIndex, LLVMNode[] phiWrites) {
        return null;
    }

    @Override
    public LLVMExpressionNode createArrayLiteral(List<LLVMExpressionNode> arrayValues, ResolvedType arrayType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createAlloc(ResolvedType type, int byteSize, int alignment, LLVMBaseType llvmType, LLVMExpressionNode numElements) {
        return null;
    }

    @Override
    public LLVMExpressionNode createInsertValue(LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset, LLVMExpressionNode valueToInsert, LLVMBaseType llvmType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createZeroNode(LLVMExpressionNode addressNode, int size) {
        return null;
    }

    @Override
    public LLVMExpressionNode createEmptyStructLiteralNode(LLVMExpressionNode alloca, int byteSize) {
        return null;
    }

    @Override
    public RootNode createGlobalRootNode(RootCallTarget mainCallTarget, Object[] args, Source sourceFile, LLVMRuntimeType[] mainTypes) {
        return null;
    }

    @Override
    public RootNode createGlobalRootNodeWrapping(RootCallTarget mainCallTarget, LLVMRuntimeType returnType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createStructureConstantNode(ResolvedType structType, boolean packed, ResolvedType[] types, LLVMExpressionNode[] constants) {
        return null;
    }

    @Override
    public LLVMNode createBasicBlockNode(LLVMNode[] statementNodes, LLVMNode terminatorNode, int blockId, String blockName) {
        return null;
    }

    @Override
    public LLVMExpressionNode createFunctionBlockNode(FrameSlot returnSlot, List<LLVMNode> basicBlockNodes, LLVMStackFrameNuller[][] beforeSlotNullerNodes,
                    LLVMStackFrameNuller[][] afterSlotNullerNodes) {
        return null;
    }

    @Override
    public RootNode createFunctionStartNode(LLVMExpressionNode functionBodyNode, LLVMNode[] beforeFunction, LLVMNode[] afterFunction, SourceSection sourceSection, FrameDescriptor frameDescriptor,
                    String functionName) {
        return null;
    }

    @Override
    public Optional<Integer> getArgStartIndex() {
        return Optional.empty();
    }

    @Override
    public LLVMNode createInlineAssemblerExpression(String asmExpression, String asmFlags, LLVMExpressionNode[] finalArgs, LLVMBaseType retType) {
        return null;
    }

    @Override
    public Map<String, NodeFactory<? extends LLVMNode>> getFunctionSubstitutionFactories(LLVMOptimizationConfiguration optimizationConfig) {
        return null;
    }

    @Override
    public LLVMNode createFunctionArgNode(int argIndex, Class<? extends Node> clazz) {
        return null;
    }

    @Override
    public RootNode createFunctionSubstitutionRootNode(LLVMNode intrinsicNode) {
        return null;
    }

    @Override
    public LLVMFunctionDescriptor createFunctionDescriptor(String name, LLVMRuntimeType convertType, LLVMRuntimeType[] convertTypes, boolean varArgs) {
        return null;
    }

    @Override
    public Object allocateGlobalVariable(GlobalVariable globalVariable) {
        return null;
    }

    @Override
    public RootNode createStaticInitsRootNode(LLVMNode[] staticInits) {
        return null;
    }

}
