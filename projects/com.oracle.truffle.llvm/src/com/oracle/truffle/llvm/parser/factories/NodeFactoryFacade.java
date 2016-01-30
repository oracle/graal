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

import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.BitwiseBinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.Type;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStatementNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMVectorNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstruction.LLVMAllocaInstruction;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;

public interface NodeFactoryFacade {

    LLVMVectorNode createInsertElement(LLVMBaseType resultType, LLVMExpressionNode vector, Type vectorType, LLVMExpressionNode element, LLVMI32Node index);

    LLVMExpressionNode createExtractElement(LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index);

    LLVMVectorNode createShuffleVector(LLVMBaseType llvmType, LLVMAddressNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2, LLVMI32VectorNode mask);

    LLVMExpressionNode createLoad(ResolvedType resolvedResultType, LLVMAddressNode loadTarget);

    LLVMNode createStore(LLVMAddressNode pointerNode, LLVMExpressionNode valueNode, ResolvedType type);

    LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType opCode, LLVMBaseType llvmType, LLVMAddressNode target);

    LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, BitwiseBinaryInstruction type, LLVMBaseType llvmType, LLVMAddressNode target);

    LLVMExpressionNode createUndefinedValue(EObject t);

    LLVMExpressionNode createSimpleConstantNoArray(String stringValue, LLVMBaseType instructionType, ResolvedType type);

    LLVMExpressionNode createZeroArrayLiteral(ResolvedType arrayElementLLVMType, int nrElements, LLVMAllocaInstruction address);

    LLVMFunctionNode[] createFunctionLiteralNodes(int nrElements, LLVMFunction value);

    LLVMAddressNode[] createPointerLiteralNodes(int nrElements, LLVMAddress value);

    LLVMDoubleNode[] createDoubleLiteralNodes(int nrElements, double value);

    LLVMFloatNode[] createFloatLiteralNodes(int nrElements, float value);

    LLVMI64Node[] createI64LiteralNodes(int nrElements, long value);

    LLVMI32Node[] createI32LiteralNodes(int nrElements, int value);

    LLVMI16Node[] createI16LiteralNodes(int nrElements, short value);

    LLVMI8Node[] createI8LiteralNodes(int nrElements, byte value);

    LLVMI1Node[] createI1LiteralNodes(int nrElements, boolean value);

    LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, LLVMAddressNode target, ResolvedVectorType type);

    /**
     * Creates an intrinsic for a <code>@llvm.*</code> function.
     *
     * @param functionName the name of the intrinsic function starting with <code>@llvm.</code>
     * @param argNodes the arguments to the intrinsic function
     * @param functionDef the function definition of the function from which the intrinsic is called
     * @return the created intrinsic
     */
    LLVMNode createLLVMIntrinsic(String functionName, Object[] argNodes, FunctionDef functionDef);

    LLVMStatementNode createRetVoid();

    LLVMStatementNode createNonVoidRet(LLVMExpressionNode retValue, ResolvedType resolvedType);

    LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType);

    LLVMNode createFunctionCall(LLVMFunctionNode functionNode, LLVMExpressionNode[] argNodes, LLVMBaseType llvmType);

    LLVMExpressionNode createFrameRead(LLVMBaseType llvmType, FrameSlot frameSlot);

    LLVMNode createFrameWrite(LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot);

    FrameSlotKind getFrameSlotKind(ResolvedType type);

    FrameSlotKind getFrameSlotKind(LLVMBaseType llvmType);

    LLVMI1Node createIntegerComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMIntegerComparisonType type);

    LLVMI1Node createFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMFloatComparisonType type);

    LLVMExpressionNode createCast(LLVMExpressionNode fromNode, ResolvedType targetType, ResolvedType fromType, LLVMConversionType type);

    LLVMExpressionNode createArithmeticOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, LLVMBaseType llvmType, LLVMAddressNode target);

    LLVMExpressionNode createExtractValue(LLVMBaseType type, LLVMAddressNode targetAddress);

    LLVMAddressNode createGetElementPtr(LLVMBaseType llvmBaseType, LLVMAddressNode currentAddress, LLVMExpressionNode valueRef, int indexedTypeLength);

    Class<?> getJavaClass(LLVMExpressionNode llvmExpressionNode);

    LLVMExpressionNode createSelect(LLVMBaseType llvmType, LLVMI1Node condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue);

}
