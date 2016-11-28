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
package com.oracle.truffle.llvm.parser.base.facade;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMType;
import com.oracle.truffle.llvm.parser.base.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

/**
 * This class implements an abstract adapter that returns <code>null</code> for each implemented
 * method.
 */
public class NodeFactoryFacadeAdapter implements NodeFactoryFacade {

    @Override
    public LLVMExpressionNode createInsertElement(LLVMParserRuntime runtime, LLVMBaseType resultType, LLVMExpressionNode vector, Object type, LLVMExpressionNode element,
                    LLVMExpressionNode index) {
        return null;
    }

    @Override
    public LLVMExpressionNode createExtractElement(LLVMParserRuntime runtime, LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index) {
        return null;
    }

    @Override
    public LLVMExpressionNode createShuffleVector(LLVMParserRuntime runtime, LLVMBaseType llvmType, LLVMExpressionNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2,
                    LLVMExpressionNode mask) {
        return null;
    }

    @Override
    public LLVMExpressionNode createLoad(LLVMParserRuntime runtime, Type resolvedResultType, LLVMExpressionNode loadTarget) {
        return null;
    }

    @Override
    public LLVMNode createStore(LLVMParserRuntime runtime, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createLogicalOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType opCode, LLVMBaseType llvmType,
                    LLVMExpressionNode target) {
        return null;
    }

    @Override
    public LLVMExpressionNode createUndefinedValue(LLVMParserRuntime runtime, Type t) {
        return null;
    }

    @Override
    public LLVMExpressionNode createLiteral(LLVMParserRuntime runtime, Object value, LLVMBaseType type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createSimpleConstantNoArray(LLVMParserRuntime runtime, String stringValue, LLVMBaseType instructionType, Type type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createVectorLiteralNode(LLVMParserRuntime runtime, List<LLVMExpressionNode> listValues, LLVMExpressionNode target, LLVMBaseType type) {
        return null;
    }

    @Override
    public LLVMNode createRetVoid(LLVMParserRuntime runtime) {
        return null;
    }

    @Override
    public LLVMNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type resolvedType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType) {
        return null;
    }

    @Override
    public LLVMNode createFunctionCall(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, LLVMBaseType llvmType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createFrameRead(LLVMParserRuntime runtime, LLVMBaseType llvmType, FrameSlot frameSlot) {
        return null;
    }

    @Override
    public LLVMNode createFrameWrite(LLVMParserRuntime runtime, LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot) {
        return null;
    }

    @Override
    public FrameSlotKind getFrameSlotKind(LLVMParserRuntime runtime, Type type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createComparison(LLVMParserRuntime runtime, CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        return null;
    }

    @Override
    public LLVMExpressionNode createCast(LLVMParserRuntime runtime, LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType type) {
        return null;
    }

    @Override
    public LLVMExpressionNode createArithmeticOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, LLVMBaseType llvmType,
                    LLVMExpressionNode target) {
        return null;
    }

    @Override
    public LLVMExpressionNode createExtractValue(LLVMParserRuntime runtime, LLVMBaseType type, LLVMExpressionNode targetAddress) {
        return null;
    }

    @Override
    public LLVMExpressionNode createGetElementPtr(LLVMParserRuntime runtime, LLVMBaseType llvmBaseType, LLVMExpressionNode currentAddress, LLVMExpressionNode valueRef, int indexedTypeLength) {
        return null;
    }

    @Override
    public Class<?> getJavaClass(LLVMExpressionNode llvmExpressionNode) {
        return null;
    }

    @Override
    public LLVMExpressionNode createSelect(LLVMParserRuntime runtime, Type type, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        return null;
    }

    @Override
    public LLVMExpressionNode createZeroVectorInitializer(LLVMParserRuntime runtime, int nrElements, LLVMExpressionNode target, LLVMBaseType llvmType) {
        return null;
    }

    @Override
    public LLVMNode createUnreachableNode(LLVMParserRuntime runtime) {
        return null;
    }

    @Override
    public LLVMNode createIndirectBranch(LLVMParserRuntime runtime, LLVMExpressionNode value, int[] labelTargets, LLVMNode[] phiWrites) {
        return null;
    }

    @Override
    public LLVMNode createSwitch(LLVMParserRuntime runtime, LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases, LLVMBaseType llvmType, LLVMNode[] phiWriteNodes) {
        return null;
    }

    @Override
    public LLVMNode createConditionalBranch(LLVMParserRuntime runtime, int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMNode[] truePhiWriteNodes, LLVMNode[] falsePhiWriteNodes) {
        return null;
    }

    @Override
    public LLVMNode createUnconditionalBranch(LLVMParserRuntime runtime, int unconditionalIndex, LLVMNode[] phiWrites) {
        return null;
    }

    @Override
    public LLVMExpressionNode createArrayLiteral(LLVMParserRuntime runtime, List<LLVMExpressionNode> arrayValues, Type arrayType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createAlloc(LLVMParserRuntime runtime, Type type, int byteSize, int alignment, LLVMBaseType llvmType, LLVMExpressionNode numElements) {
        return null;
    }

    @Override
    public LLVMExpressionNode createInsertValue(LLVMParserRuntime runtime, LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset,
                    LLVMExpressionNode valueToInsert, LLVMBaseType llvmType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createZeroNode(LLVMParserRuntime runtime, LLVMExpressionNode addressNode, int size) {
        return null;
    }

    @Override
    public LLVMExpressionNode createEmptyStructLiteralNode(LLVMParserRuntime runtime, LLVMExpressionNode alloca, int byteSize) {
        return null;
    }

    @Override
    public RootNode createGlobalRootNode(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, Object[] args, Source sourceFile, LLVMRuntimeType[] mainTypes) {
        return null;
    }

    @Override
    public RootNode createGlobalRootNodeWrapping(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, LLVMRuntimeType returnType) {
        return null;
    }

    @Override
    public LLVMExpressionNode createStructureConstantNode(LLVMParserRuntime runtime, Type structType, boolean packed, Type[] types, LLVMExpressionNode[] constants) {
        return null;
    }

    @Override
    public LLVMNode createBasicBlockNode(LLVMParserRuntime runtime, LLVMNode[] statementNodes, LLVMNode terminatorNode, int blockId, String blockName) {
        return null;
    }

    @Override
    public LLVMExpressionNode createFunctionBlockNode(LLVMParserRuntime runtime, FrameSlot returnSlot, List<? extends LLVMNode> basicBlockNodes, LLVMStackFrameNuller[][] beforeSlotNullerNodes,
                    LLVMStackFrameNuller[][] afterSlotNullerNodes) {
        return null;
    }

    @Override
    public RootNode createFunctionStartNode(LLVMParserRuntime runtime, LLVMExpressionNode functionBodyNode, LLVMNode[] beforeFunction, LLVMNode[] afterFunction, SourceSection sourceSection,
                    FrameDescriptor frameDescriptor,
                    FunctionDefinition functionHeader) {
        return null;
    }

    @Override
    public Optional<Integer> getArgStartIndex() {
        return Optional.empty();
    }

    @Override
    public LLVMNode createInlineAssemblerExpression(LLVMParserRuntime runtime, String asmExpression, String asmFlags, LLVMExpressionNode[] finalArgs, LLVMBaseType retType) {
        return null;
    }

    @Override
    public Map<String, NodeFactory<? extends LLVMNode>> getFunctionSubstitutionFactories() {
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
    public Object allocateGlobalVariable(LLVMParserRuntime runtime, GlobalVariable globalVariable) {
        return null;
    }

    @Override
    public Object allocateGlobalConstant(LLVMParserRuntime runtime, GlobalConstant globalConstant) {
        return null;
    }

    @Override
    public RootNode createStaticInitsRootNode(LLVMParserRuntime runtime, LLVMNode[] staticInits) {
        return null;
    }

    @Override
    public Optional<Boolean> hasStackPointerArgument(LLVMParserRuntime runtime) {
        return Optional.empty();
    }

    @Override
    public LLVMStackFrameNuller createFrameNuller(LLVMParserRuntime runtime, String identifier, LLVMType type, FrameSlot slot) {
        return null;
    }

    @Override
    public LLVMFunction createFunctionDescriptor(String name, LLVMRuntimeType returnType, boolean varArgs, LLVMRuntimeType[] paramTypes, int functionIndex) {
        return null;
    }

    @Override
    public LLVMFunction createAndRegisterFunctionDescriptor(LLVMParserRuntime runtime, String name, LLVMRuntimeType convertType, boolean varArgs, LLVMRuntimeType[] convertTypes) {
        return null;
    }

    @Override
    public LLVMNode tryCreateFunctionSubstitution(LLVMParserRuntime runtime, FunctionType declaration, LLVMExpressionNode[] argNodes, int numberOfExplicitArguments) {
        return null;
    }

}
