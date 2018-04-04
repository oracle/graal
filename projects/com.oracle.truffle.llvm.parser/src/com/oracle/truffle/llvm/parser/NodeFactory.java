/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMFrameValueAccess;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.interop.export.InteropNodeFactory;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateStringNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.memory.VarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

/**
 * This interface decouples the parser and the concrete implementation of the nodes by only making
 * {@link LLVMExpressionNode} and {@link LLVMExpressionNode} visible. The parser should not directly
 * instantiate a node, but instead use the factory facade.
 */
public interface NodeFactory extends InteropNodeFactory {

    LLVMExpressionNode createInsertElement(LLVMParserRuntime runtime, Type resultType, LLVMExpressionNode vector, LLVMExpressionNode element, LLVMExpressionNode index);

    LLVMExpressionNode createExtractElement(LLVMParserRuntime runtime, Type resultType, LLVMExpressionNode vector, LLVMExpressionNode index);

    LLVMExpressionNode createShuffleVector(LLVMParserRuntime runtime, Type llvmType, LLVMExpressionNode vector1, LLVMExpressionNode vector2,
                    LLVMExpressionNode mask);

    LLVMExpressionNode createLoad(LLVMParserRuntime runtime, Type resolvedResultType, LLVMExpressionNode loadTarget);

    LLVMExpressionNode createStore(LLVMParserRuntime runtime, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type, LLVMSourceLocation source);

    LLVMExpressionNode createReadModifyWrite(LLVMParserRuntime runtime, ReadModifyWriteOperator operator, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createFence(LLVMParserRuntime runtime);

    LLVMExpressionNode createLogicalOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionKind opCode, Type llvmType, Flag[] flags);

    LLVMExpressionNode createLiteral(LLVMParserRuntime runtime, Object value, Type type);

    LLVMExpressionNode createSimpleConstantNoArray(LLVMParserRuntime runtime, Object constant, Type instructionType);

    LLVMExpressionNode createVectorLiteralNode(LLVMParserRuntime runtime, List<LLVMExpressionNode> listValues, Type type);

    LLVMExpressionNode createFrameNuller(FrameSlot slot);

    LLVMControlFlowNode createRetVoid(LLVMParserRuntime runtime, LLVMSourceLocation source);

    LLVMControlFlowNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type resolvedType, LLVMSourceLocation source);

    LLVMExpressionNode createFunctionArgNode(int argIndex, Type paramType);

    LLVMExpressionNode createFunctionArgNode(int argIndex);

    LLVMExpressionNode createFunctionCall(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type, LLVMSourceLocation sourceSection);

    LLVMControlFlowNode createFunctionInvoke(LLVMParserRuntime runtime, FrameSlot resultLocation, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type, int normalIndex,
                    int unwindIndex, LLVMExpressionNode normalPhiWriteNodes,
                    LLVMExpressionNode unwindPhiWriteNodes, LLVMSourceLocation sourceSection);

    LLVMExpressionNode createFrameRead(LLVMParserRuntime runtime, Type llvmType, FrameSlot frameSlot);

    LLVMExpressionNode createFrameWrite(LLVMParserRuntime runtime, Type llvmType, LLVMExpressionNode result, FrameSlot slot, LLVMSourceLocation sourceSection);

    LLVMExpressionNode createComparison(LLVMParserRuntime runtime, CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs);

    LLVMExpressionNode createCast(LLVMParserRuntime runtime, LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType type);

    LLVMExpressionNode createArithmeticOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, Type llvmType, Flag[] flags);

    LLVMExpressionNode createExtractValue(LLVMParserRuntime runtime, Type type, LLVMExpressionNode targetAddress);

    LLVMExpressionNode createTypedElementPointer(LLVMParserRuntime runtime, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index, long indexedTypeLength,
                    Type targetType);

    LLVMExpressionNode createSelect(LLVMParserRuntime runtime, Type type, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue);

    LLVMExpressionNode createZeroVectorInitializer(LLVMParserRuntime runtime, int nrElements, VectorType llvmType);

    LLVMControlFlowNode createUnreachableNode(LLVMParserRuntime runtime);

    LLVMControlFlowNode createIndirectBranch(LLVMParserRuntime runtime, LLVMExpressionNode value, int[] labelTargets, LLVMExpressionNode[] phiWrites, LLVMSourceLocation source);

    LLVMControlFlowNode createSwitch(LLVMParserRuntime runtime, LLVMExpressionNode cond, int[] labels, LLVMExpressionNode[] cases,
                    Type llvmType, LLVMExpressionNode[] phiWriteNodes, LLVMSourceLocation source);

    LLVMControlFlowNode createConditionalBranch(LLVMParserRuntime runtime, int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMExpressionNode truePhiWriteNodes,
                    LLVMExpressionNode falsePhiWriteNodes, LLVMSourceLocation sourceSection);

    LLVMControlFlowNode createUnconditionalBranch(LLVMParserRuntime runtime, int unconditionalIndex, LLVMExpressionNode phi, LLVMSourceLocation source);

    LLVMExpressionNode createArrayLiteral(LLVMParserRuntime runtime, List<LLVMExpressionNode> arrayValues, ArrayType arrayType);

    /*
     * Stack allocations with type (LLVM's alloca instruction)
     */
    LLVMExpressionNode createAlloca(LLVMParserRuntime runtime, Type type);

    LLVMExpressionNode createAlloca(LLVMParserRuntime runtime, Type type, int alignment);

    LLVMExpressionNode createAllocaArray(LLVMParserRuntime runtime, Type elementType, LLVMExpressionNode numElements, int alignment);

    /*
     * Stack allocation without a type
     */
    VarargsAreaStackAllocationNode createVarargsAreaStackAllocation(LLVMParserRuntime runtime);

    LLVMExpressionNode createInsertValue(LLVMParserRuntime runtime, LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, long offset, LLVMExpressionNode valueToInsert,
                    Type llvmType);

    LLVMExpressionNode createZeroNode(LLVMParserRuntime runtime, LLVMExpressionNode addressNode, int size);

    RootNode createGlobalRootNode(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, LLVMFunctionDescriptor mainFunctionDescriptor, String applicationPath);

    RootNode createGlobalRootNodeWrapping(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, Type returnType);

    LLVMExpressionNode createStructureConstantNode(LLVMParserRuntime runtime, Type structureType, boolean packed, Type[] types, LLVMExpressionNode[] constants);

    LLVMExpressionNode createBasicBlockNode(LLVMParserRuntime runtime, LLVMExpressionNode[] statementNodes, LLVMControlFlowNode terminatorNode, int blockId, String blockName);

    LLVMExpressionNode createFunctionBlockNode(LLVMParserRuntime runtime, FrameSlot exceptionValueSlot, List<? extends LLVMExpressionNode> basicBlockNodes, FrameSlot[][] beforeBlockNuller,
                    FrameSlot[][] afterBlockNuller, LLVMSourceLocation sourceSection, LLVMExpressionNode[] copyArgumentsToFrame);

    RootNode createFunctionStartNode(LLVMParserRuntime runtime, LLVMExpressionNode functionBodyNode, SourceSection sourceSection, FrameDescriptor frameDescriptor, FunctionDefinition functionHeader,
                    Source bcSource, LLVMSourceLocation location);

    LLVMExpressionNode createInlineAssemblerExpression(LLVMParserRuntime runtime, String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type[] argTypes, Type retType,
                    LLVMSourceLocation sourceSection);

    Object allocateGlobalVariable(LLVMParserRuntime runtime, GlobalVariable globalVariable, LLVMSourceSymbol sourceSymbol);

    Object allocateGlobalConstant(LLVMParserRuntime runtime, GlobalConstant globalConstant, LLVMSourceSymbol sourceSymbol);

    LLVMExpressionNode createLandingPad(LLVMParserRuntime runtime, LLVMExpressionNode allocateLandingPadValue, FrameSlot exceptionSlot, boolean cleanup, long[] clauseKinds,
                    LLVMExpressionNode[] entries, LLVMExpressionNode getStack);

    LLVMControlFlowNode createResumeInstruction(LLVMParserRuntime runtime, FrameSlot exceptionSlot, LLVMSourceLocation sourceSection);

    LLVMExpressionNode createCompareExchangeInstruction(LLVMParserRuntime runtime, Type returnType, Type elementType, LLVMExpressionNode ptrNode, LLVMExpressionNode cmpNode,
                    LLVMExpressionNode newNode);

    LLVMExpressionNode createLLVMBuiltin(LLVMParserRuntime runtime, Symbol target, LLVMExpressionNode[] args, int callerArgumentCount, LLVMSourceLocation sourceSection);

    RootNode createStaticInitsRootNode(LLVMParserRuntime visitor, LLVMExpressionNode[] deallocs);

    LLVMExpressionNode createPhi(LLVMParserRuntime runtime, LLVMExpressionNode[] from, FrameSlot[] to, Type[] types);

    LLVMExpressionNode createCopyStructByValue(LLVMParserRuntime runtime, Type type, LLVMExpressionNode parameterNode);

    LLVMExpressionNode createVarArgCompoundValue(LLVMParserRuntime runtime, int length, int alignment, LLVMExpressionNode parameterNode);

    LLVMExpressionNode createDebugWrite(boolean isDeclaration, LLVMExpressionNode valueRead, FrameSlot targetSlot, LLVMExpressionNode aggregateRead, int partIndex, int[] clearParts);

    LLVMExpressionNode createDebugInit(FrameSlot targetSlot, int[] offsets, int[] lengths);

    LLVMDebugValue createDebugStaticValue(LLVMExpressionNode valueNode);

    LLVMFrameValueAccess createDebugFrameValue(FrameSlot slot, boolean isDeclaration);

    LLVMExpressionNode registerSourceType(FrameSlot valueSlot, LLVMSourceType type);

    LLVMMemMoveNode createMemMove();

    LLVMMemSetNode createMemSet();

    LLVMAllocateStringNode createAllocateString();
}
