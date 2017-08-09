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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.model.enums.Flag;
import com.oracle.truffle.llvm.parser.model.enums.ReadModifyWriteOperator;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NativeIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.DataSpecConverter;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

/**
 * This interface decouples the parser and the concrete implementation of the nodes by only making
 * {@link LLVMExpressionNode} and {@link LLVMExpressionNode} visible. The parser should not directly
 * instantiate a node, but instead use the factory facade.
 */
public interface NodeFactory {

    LLVMExpressionNode createInsertElement(LLVMParserRuntime runtime, Type resultType, LLVMExpressionNode vector, LLVMExpressionNode element, LLVMExpressionNode index);

    LLVMExpressionNode createExtractElement(LLVMParserRuntime runtime, Type resultType, LLVMExpressionNode vector, LLVMExpressionNode index);

    LLVMExpressionNode createShuffleVector(LLVMParserRuntime runtime, Type llvmType, LLVMExpressionNode vector1, LLVMExpressionNode vector2,
                    LLVMExpressionNode mask);

    LLVMExpressionNode createLoad(LLVMParserRuntime runtime, Type resolvedResultType, LLVMExpressionNode loadTarget);

    LLVMExpressionNode createStore(LLVMParserRuntime runtime, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type, SourceSection source);

    LLVMExpressionNode createReadModifyWrite(LLVMParserRuntime runtime, ReadModifyWriteOperator operator, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createFence(LLVMParserRuntime runtime);

    LLVMExpressionNode createLogicalOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionKind opCode, Type llvmType, Flag[] flags);

    LLVMExpressionNode createLiteral(LLVMParserRuntime runtime, Object value, Type type);

    LLVMExpressionNode createSimpleConstantNoArray(LLVMParserRuntime runtime, Object constant, Type instructionType);

    LLVMExpressionNode createVectorLiteralNode(LLVMParserRuntime runtime, List<LLVMExpressionNode> listValues, Type type);

    LLVMExpressionNode createFrameNuller(FrameSlot slot);

    LLVMControlFlowNode createRetVoid(LLVMParserRuntime runtime, SourceSection source);

    LLVMControlFlowNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type resolvedType, SourceSection source);

    LLVMExpressionNode createFunctionArgNode(int argIndex, Type paramType);

    LLVMExpressionNode createFunctionArgNode(int argIndex, Class<? extends Node> clazz);

    LLVMExpressionNode createFunctionCall(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type, SourceSection sourceSection);

    LLVMControlFlowNode createFunctionInvoke(LLVMParserRuntime runtime, FrameSlot resultLocation, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type, int normalIndex,
                    int unwindIndex, LLVMExpressionNode normalPhiWriteNodes,
                    LLVMExpressionNode unwindPhiWriteNodes, SourceSection sourceSection);

    LLVMExpressionNode createFrameRead(LLVMParserRuntime runtime, Type llvmType, FrameSlot frameSlot);

    LLVMExpressionNode createFrameWrite(LLVMParserRuntime runtime, Type llvmType, LLVMExpressionNode result, FrameSlot slot, SourceSection sourceSection);

    LLVMExpressionNode createComparison(LLVMParserRuntime runtime, CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs);

    LLVMExpressionNode createCast(LLVMParserRuntime runtime, LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType type);

    LLVMExpressionNode createArithmeticOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, Type llvmType, Flag[] flags);

    LLVMExpressionNode createExtractValue(LLVMParserRuntime runtime, Type type, LLVMExpressionNode targetAddress);

    LLVMExpressionNode createTypedElementPointer(LLVMParserRuntime runtime, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index, int indexedTypeLength,
                    Type targetType);

    LLVMExpressionNode createSelect(LLVMParserRuntime runtime, Type type, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue);

    LLVMExpressionNode createZeroVectorInitializer(LLVMParserRuntime runtime, int nrElements, VectorType llvmType);

    LLVMControlFlowNode createUnreachableNode(LLVMParserRuntime runtime);

    LLVMControlFlowNode createIndirectBranch(LLVMParserRuntime runtime, LLVMExpressionNode value, int[] labelTargets, LLVMExpressionNode[] phiWrites, SourceSection source);

    LLVMControlFlowNode createSwitch(LLVMParserRuntime runtime, LLVMExpressionNode cond, int[] labels, LLVMExpressionNode[] cases,
                    PrimitiveType llvmType, LLVMExpressionNode[] phiWriteNodes, SourceSection source);

    LLVMControlFlowNode createConditionalBranch(LLVMParserRuntime runtime, int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMExpressionNode truePhiWriteNodes,
                    LLVMExpressionNode falsePhiWriteNodes, SourceSection sourceSection);

    LLVMControlFlowNode createUnconditionalBranch(LLVMParserRuntime runtime, int unconditionalIndex, LLVMExpressionNode phi, SourceSection source);

    LLVMExpressionNode createArrayLiteral(LLVMParserRuntime runtime, List<LLVMExpressionNode> arrayValues, Type arrayType);

    LLVMExpressionNode createAlloc(LLVMParserRuntime runtime, Type type, int byteSize, int alignment, Type numElementsType, LLVMExpressionNode numElements);

    LLVMExpressionNode createInsertValue(LLVMParserRuntime runtime, LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset, LLVMExpressionNode valueToInsert,
                    Type llvmType);

    LLVMExpressionNode createZeroNode(LLVMParserRuntime runtime, LLVMExpressionNode addressNode, int size);

    RootNode createGlobalRootNode(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, Object[] args, Source sourceFile, Type[] mainTypes);

    RootNode createGlobalRootNodeWrapping(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, Type returnType);

    LLVMExpressionNode createStructureConstantNode(LLVMParserRuntime runtime, Type structureType, boolean packed, Type[] types, LLVMExpressionNode[] constants);

    LLVMExpressionNode createBasicBlockNode(LLVMParserRuntime runtime, LLVMExpressionNode[] statementNodes, LLVMControlFlowNode terminatorNode, int blockId, String blockName);

    LLVMExpressionNode createFunctionBlockNode(LLVMParserRuntime runtime, FrameSlot exceptionValueSlot, List<? extends LLVMExpressionNode> basicBlockNodes, FrameSlot[][] beforeBlockNuller,
                    FrameSlot[][] afterBlockNuller);

    RootNode createFunctionStartNode(LLVMParserRuntime runtime, LLVMExpressionNode functionBodyNode, LLVMExpressionNode[] copyArgumentsToFrame,
                    SourceSection sourceSection, FrameDescriptor frameDescriptor, FunctionDefinition functionHeader, Source bcSource);

    LLVMExpressionNode createInlineAssemblerExpression(LLVMParserRuntime runtime, String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type[] argTypes, Type retType,
                    SourceSection sourceSection);

    Object allocateGlobalVariable(LLVMParserRuntime runtime, GlobalVariable globalVariable);

    Object allocateGlobalConstant(LLVMParserRuntime runtime, GlobalConstant globalConstant);

    LLVMExpressionNode createLandingPad(LLVMParserRuntime runtime, LLVMExpressionNode allocateLandingPadValue, FrameSlot exceptionSlot, boolean cleanup, long[] clauseKinds,
                    LLVMExpressionNode[] entries);

    LLVMControlFlowNode createResumeInstruction(LLVMParserRuntime runtime, FrameSlot exceptionSlot, SourceSection sourceSection);

    LLVMExpressionNode createCompareExchangeInstruction(LLVMParserRuntime runtime, Type returnType, Type elementType, LLVMExpressionNode ptrNode, LLVMExpressionNode cmpNode,
                    LLVMExpressionNode newNode);

    LLVMExpressionNode createLLVMBuiltin(LLVMParserRuntime runtime, Symbol target, LLVMExpressionNode[] args, int callerArgumentCount, SourceSection sourceSection);

    NativeIntrinsicProvider getNativeIntrinsicsFactory(LLVMLanguage language, LLVMContext context, DataSpecConverter targetDataLayout);

    RootNode createStaticInitsRootNode(LLVMParserRuntime visitor, LLVMExpressionNode[] deallocs);

    LLVMExpressionNode createPhi(LLVMExpressionNode[] from, FrameSlot[] to, Type[] types);

    LLVMExpressionNode createCopyStructByValue(LLVMParserRuntime runtime, int length, int alignment, LLVMExpressionNode parameterNode);

    LLVMExpressionNode createVarArgCompoundValue(LLVMParserRuntime runtime, int length, int alignment, LLVMExpressionNode parameterNode);
}
