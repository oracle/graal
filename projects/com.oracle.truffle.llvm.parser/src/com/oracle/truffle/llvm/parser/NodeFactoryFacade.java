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

import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.BitwiseBinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.Type;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction.LLVMRuntimeType;

/**
 * This interface decouples the parser and the concrete implementation of the nodes by only making
 * {@link LLVMExpressionNode} and {@link LLVMNode} visible. The parser should not directly
 * instantiate a node, but instead use the factory facade.
 */
public interface NodeFactoryFacade {

    LLVMExpressionNode createInsertElement(LLVMBaseType resultType, LLVMExpressionNode vector, Type vectorType, LLVMExpressionNode element, LLVMExpressionNode index);

    LLVMExpressionNode createExtractElement(LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index);

    LLVMExpressionNode createShuffleVector(LLVMBaseType llvmType, LLVMExpressionNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2, LLVMExpressionNode mask);

    LLVMExpressionNode createLoad(ResolvedType resolvedResultType, LLVMExpressionNode loadTarget);

    LLVMNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, ResolvedType type);

    LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType opCode, LLVMBaseType llvmType, LLVMExpressionNode target);

    LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, BitwiseBinaryInstruction type, LLVMBaseType llvmType, LLVMExpressionNode target);

    LLVMExpressionNode createUndefinedValue(EObject t);

    LLVMExpressionNode createLiteral(Object value, LLVMBaseType type);

    LLVMExpressionNode createSimpleConstantNoArray(String stringValue, LLVMBaseType instructionType, ResolvedType type);

    LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, LLVMExpressionNode target, ResolvedVectorType type);

    /**
     * Creates an intrinsic for a <code>@llvm.*</code> function.
     *
     * @param functionName the name of the intrinsic function starting with <code>@llvm.</code>
     * @param argNodes the arguments to the intrinsic function
     * @param functionDef the function definition of the function from which the intrinsic is called
     * @return the created intrinsic
     */
    LLVMNode createLLVMIntrinsic(String functionName, Object[] argNodes, FunctionDef functionDef);

    LLVMNode createRetVoid();

    LLVMNode createNonVoidRet(LLVMExpressionNode retValue, ResolvedType resolvedType);

    LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType);

    LLVMNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, LLVMBaseType llvmType);

    LLVMExpressionNode createFrameRead(LLVMBaseType llvmType, FrameSlot frameSlot);

    LLVMNode createFrameWrite(LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot);

    FrameSlotKind getFrameSlotKind(ResolvedType type);

    FrameSlotKind getFrameSlotKind(LLVMBaseType llvmType);

    LLVMExpressionNode createIntegerComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMIntegerComparisonType type);

    LLVMExpressionNode createFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMFloatComparisonType type);

    LLVMExpressionNode createCast(LLVMExpressionNode fromNode, ResolvedType targetType, ResolvedType fromType, LLVMConversionType type);

    LLVMExpressionNode createArithmeticOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, LLVMBaseType llvmType, LLVMExpressionNode target);

    LLVMExpressionNode createExtractValue(LLVMBaseType type, LLVMExpressionNode targetAddress);

    LLVMExpressionNode createGetElementPtr(LLVMBaseType llvmBaseType, LLVMExpressionNode currentAddress, LLVMExpressionNode valueRef, int indexedTypeLength);

    Class<?> getJavaClass(LLVMExpressionNode llvmExpressionNode);

    LLVMExpressionNode createSelect(LLVMBaseType llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue);

    /**
     * Creates a zero vector initializer.
     *
     * @param nrElements the number of elements of the vector
     * @param target the allocated result storage
     * @param llvmType the type of the vector
     *
     * @return the zero vector initializer
     */
    LLVMExpressionNode createZeroVectorInitializer(int nrElements, LLVMExpressionNode target, LLVMBaseType llvmType);

    // TODO we do want to have a LLVM node here or merge with createStructLiteralNode
    Node createStructWriteNode(LLVMExpressionNode node, ResolvedType type);

    LLVMExpressionNode createStructLiteralNode(int[] offsets, Node[] nodes, LLVMExpressionNode alloc);

    LLVMNode createUnreachableNode();

    LLVMNode createIndirectBranch(LLVMExpressionNode value, int[] labelTargets);

    LLVMNode createSwitch(LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases, LLVMBaseType llvmType);

    LLVMNode createConditionalBranch(int trueIndex, int falseIndex, LLVMExpressionNode conditionNode);

    LLVMNode createUnconditionalBranch(int unconditionalIndex);

    LLVMExpressionNode createArrayLiteral(List<LLVMExpressionNode> arrayValues, ResolvedType arrayType);

    LLVMNode createConditionalPhiWriteNode(LLVMExpressionNode create, LLVMNode phiWriteNode);

    LLVMExpressionNode createAlloc(LLVMBaseType llvmType, LLVMExpressionNode numElements, int byteSize, int alignment);

    LLVMExpressionNode createAlloc(int size, int alignment);

    LLVMExpressionNode createInsertValue(LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset, LLVMExpressionNode valueToInsert, LLVMBaseType llvmType);

    LLVMExpressionNode createZeroNode(LLVMExpressionNode addressNode, int size);

    LLVMExpressionNode createEmptyStructLiteralNode(LLVMExpressionNode alloca, int byteSize);

    LLVMExpressionNode createGetElementPtr(LLVMExpressionNode currentAddress, LLVMExpressionNode oneValueNode, int currentOffset);

    /**
     * Creates the global root (e.g., the main function in C).
     *
     * @param staticInits
     * @param mainCallTarget
     * @param allocatedGlobalAddresses
     * @param args
     * @return the global root
     */
    RootNode createGlobalRootNode(LLVMNode[] staticInits, RootCallTarget mainCallTarget, LLVMAddress[] allocatedGlobalAddresses, Object... args);

    /**
     * Wraps the global root (e.g., the main function in C) to convert its result.
     *
     * @param mainCallTarget
     * @param returnType
     * @return the wrapped global root
     */
    RootNode createGlobalRootNodeWrapping(RootCallTarget mainCallTarget, LLVMRuntimeType returnType);

}
