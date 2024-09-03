/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemorySizedOpNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.LLVMStackAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion;
import com.oracle.truffle.llvm.runtime.memory.VarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNode;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.symbols.LocalVariableDebugInfo;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

/**
 * This interface decouples the parser and the concrete implementation of the nodes by only making
 * {@link LLVMExpressionNode} and {@link LLVMExpressionNode} visible. The parser should not directly
 * instantiate a node, but instead use the factory facade.
 */
public interface NodeFactory {

    DataLayout getDataLayout();

    LLVMLanguage getLanguage();

    LLVMExpressionNode createInsertElement(Type resultType, LLVMExpressionNode vector, LLVMExpressionNode element, LLVMExpressionNode index);

    LLVMExpressionNode createExtractElement(Type resultType, LLVMExpressionNode vector, LLVMExpressionNode index);

    LLVMExpressionNode createShuffleVector(Type llvmType, LLVMExpressionNode vector1, LLVMExpressionNode vector2, LLVMExpressionNode mask);

    LLVMExpressionNode createLoad(Type resolvedResultType, LLVMExpressionNode loadTarget);

    LLVMStatementNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMOffsetStoreNode createOffsetMemoryStore(Type resolvedType, LLVMExpressionNode value) throws TypeOverflowException;

    LLVMExpressionNode createRMWXchg(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createRMWAdd(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createRMWSub(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createRMWAnd(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createRMWNand(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createRMWOr(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMExpressionNode createRMWXor(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMStatementNode createFence();

    LLVMExpressionNode createFenceExpression();

    LLVMExpressionNode createVectorLiteralNode(LLVMExpressionNode[] values, Type type);

    LLVMControlFlowNode createRetVoid();

    LLVMControlFlowNode createRetTail(LLVMExpressionNode retValue);

    LLVMControlFlowNode createNonVoidRet(LLVMExpressionNode retValue, Type resolvedType);

    LLVMExpressionNode createFunctionArgNode(int argIndex, Type paramType);

    LLVMControlFlowNode createFunctionInvoke(LLVMWriteNode writeResult, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type, int normalIndex,
                    int unwindIndex, LLVMStatementNode normalPhiWriteNodes,
                    LLVMStatementNode unwindPhiWriteNodes);

    LLVMExpressionNode createExtractValue(Type type, LLVMExpressionNode targetAddress);

    LLVMExpressionNode createTypedElementPointer(long indexedTypeLength, Type targetType, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index);

    LLVMExpressionNode createVectorizedTypedElementPointer(long indexedTypeLength, Type targetType, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index);

    LLVMExpressionNode createSelect(Type type, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue);

    LLVMExpressionNode createZeroVectorInitializer(int nrElements, VectorType llvmType);

    LLVMControlFlowNode createUnreachableNode();

    LLVMControlFlowNode createIndirectBranch(LLVMExpressionNode value, int[] labelTargets, LLVMStatementNode[] phiWrites);

    LLVMControlFlowNode createSwitch(LLVMExpressionNode cond, int[] labels, LLVMExpressionNode[] cases, Type llvmType, LLVMStatementNode[] phiWriteNodes);

    LLVMControlFlowNode createConditionalBranch(int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMStatementNode truePhiWriteNodes, LLVMStatementNode falsePhiWriteNodes);

    LLVMControlFlowNode createUnconditionalBranch(int unconditionalIndex, LLVMStatementNode phi);

    LLVMExpressionNode createArrayLiteral(LLVMExpressionNode[] arrayValues, ArrayType arrayType, GetStackSpaceFactory arrayGetStackSpaceFactory);

    LLVMExpressionNode createPrimitiveArrayLiteral(Object arrayValues, ArrayType arrayType, GetStackSpaceFactory arrayGetStackSpaceFactory);

    LLVMExpressionNode createBitcast(LLVMExpressionNode fromNode, Type targetType, Type fromType);

    LLVMExpressionNode createArithmeticOp(ArithmeticOperation op, Type type, LLVMExpressionNode left, LLVMExpressionNode right);

    LLVMExpressionNode createUnaryOp(UnaryOperation op, Type type, LLVMExpressionNode operand);

    /*
     * Stack allocations with type
     */
    LLVMExpressionNode createAlloca(Type type);

    LLVMExpressionNode createAlloca(Type type, int alignment);

    LLVMExpressionNode createGetUniqueStackSpace(Type type, UniquesRegion uniquesRegion);

    LLVMExpressionNode createAllocaArray(Type elementType, LLVMExpressionNode numElements, int alignment);

    /*
     * Stack allocation without a type
     */
    VarargsAreaStackAllocationNode createVarargsAreaStackAllocation();

    LLVMExpressionNode createInsertValue(LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, long size, long offset, LLVMExpressionNode valueToInsert, Type llvmType);

    LLVMExpressionNode createZeroNode(LLVMExpressionNode addressNode, long size);

    LLVMExpressionNode createStructureConstantNode(Type structureType, GetStackSpaceFactory getStackSpaceFactory, boolean packed, Type[] types, LLVMExpressionNode[] constants);

    RootNode createFunction(int exceptionValueSlot, LLVMBasicBlockNode[] basicBlockNodes, UniquesRegion uniquesRegion, LLVMStatementNode[] copyArgumentsToFrame, FrameDescriptor frameDescriptor,
                    LocalVariableDebugInfo debugInfo, String name, String originalName, int argumentCount, Source bcSource, LLVMSourceLocation location,
                    LLVMFunction rootFunction);

    LLVMExpressionNode createInlineAssemblerExpression(String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type.TypeArrayBuilder argTypes, Type retType);

    LLVMExpressionNode createLandingPad(LLVMExpressionNode allocateLandingPadValue, int exceptionSlot, boolean cleanup, long[] clauseKinds, LLVMExpressionNode[] entries,
                    LLVMExpressionNode getStack);

    LLVMControlFlowNode createCatchSwitch(int exceptionSlot, int[] targetBlocks, LLVMExpressionNode getStack, LLVMStatementNode[] phiWrites);

    LLVMControlFlowNode createCatchSwitch(int exceptionSlot, int[] targetBlocks, int unwindBlock, LLVMExpressionNode getStack, LLVMStatementNode[] phiWrites);

    LLVMControlFlowNode createCatchReturn(int unconditionalIndex, LLVMExpressionNode getStack, LLVMStatementNode phiWrites);

    LLVMControlFlowNode createCleanupReturn(int unconditionalIndex, LLVMExpressionNode getStack, LLVMStatementNode phiWrites);

    LLVMControlFlowNode createResumeInstruction(int exceptionSlot);

    LLVMExpressionNode createCompareExchangeInstruction(AggregateType returnType, Type elementType, LLVMExpressionNode ptrNode, LLVMExpressionNode cmpNode, LLVMExpressionNode newNode);

    LLVMExpressionNode createLLVMBuiltin(Symbol target, LLVMExpressionNode[] args, Type.TypeArrayBuilder argsTypes, int callerArgumentCount);

    LLVMStatementNode createPhi(LLVMExpressionNode[] cycleFrom, LLVMWriteNode[] cycleWrites, LLVMWriteNode[] ordinaryWrites);

    LLVMExpressionNode createCopyStructByValue(Type type, GetStackSpaceFactory getStackSpaceFactory, LLVMExpressionNode parameterNode);

    LLVMExpressionNode createVarArgCompoundValue(long length, int alignment, Type type, LLVMExpressionNode parameterNode);

    LLVMMemMoveNode createMemMove();

    LLVMMemSetNode createMemSet();

    LLVMAllocateNode createAllocateGlobalsBlock(long totalSize);

    LLVMMemorySizedOpNode createProtectGlobalsBlock();

    LLVMMemorySizedOpNode createFreeGlobalsBlock();

    LLVMMemorySizedOpNode getFreeGlobalsBlockUncached();

    LLVMExpressionNode createGetStackFromFrame();

    boolean boxGlobals();

    LLVMStackAccess createStackAccess();

    void addStackSlots(FrameDescriptor.Builder builder);
}
