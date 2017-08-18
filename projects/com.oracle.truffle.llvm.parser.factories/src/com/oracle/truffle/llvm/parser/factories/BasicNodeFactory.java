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

import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.asm.amd64.Parser;
import com.oracle.truffle.llvm.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.nodes.base.LLVMFrameNuller;
import com.oracle.truffle.llvm.nodes.control.LLVMBrUnconditionalNode;
import com.oracle.truffle.llvm.nodes.control.LLVMConditionalBranchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMDispatchBasicBlockNode;
import com.oracle.truffle.llvm.nodes.control.LLVMIndirectBranchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMVoidReturnNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI16SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI1SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI32SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI64SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI8SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMWritePhisNode;
import com.oracle.truffle.llvm.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.func.LLVMLandingpadNode;
import com.oracle.truffle.llvm.nodes.func.LLVMResumeNode;
import com.oracle.truffle.llvm.nodes.func.LLVMTypeIdForExceptionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMPowNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleGetArgNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMAssumeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI1NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMFrameAddressNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMI64ObjectSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMInvariantEndNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMInvariantStartNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMLifetimeEndNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMLifetimeStartNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI32CopyNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI64CopyNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemMoveFactory.LLVMMemMoveI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemSetNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMNoOpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMPrefetchNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMReturnAddressNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMStackRestoreNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMStackSaveNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMTrapNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexDivSC;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSAddWithOverflowFactory.GCCAddWithOverflowNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSAddWithOverflowFactory.LLVMSAddWithOverflowI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSAddWithOverflowFactory.LLVMSAddWithOverflowI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSAddWithOverflowFactory.LLVMSAddWithOverflowI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSAddWithOverflowFactory.LLVMSAddWithOverflowI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSMulWithOverflowFactory.GCCSMulWithOverflowNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSMulWithOverflowFactory.LLVMSMulWithOverflowI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSMulWithOverflowFactory.LLVMSMulWithOverflowI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSMulWithOverflowFactory.LLVMSMulWithOverflowI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSMulWithOverflowFactory.LLVMSMulWithOverflowI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSSubWithOverflowFactory.GCCSubWithOverflowNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSSubWithOverflowFactory.LLVMSSubWithOverflowI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSSubWithOverflowFactory.LLVMSSubWithOverflowI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSSubWithOverflowFactory.LLVMSSubWithOverflowI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMSSubWithOverflowFactory.LLVMSSubWithOverflowI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowAndCarryI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowAndCarryI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowAndCarryI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowAndCarryI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUMulWithOverflowFactory.GCCUMulWithOverflowNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUMulWithOverflowFactory.LLVMUMulWithOverflowI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUMulWithOverflowFactory.LLVMUMulWithOverflowI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUMulWithOverflowFactory.LLVMUMulWithOverflowI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUMulWithOverflowFactory.LLVMUMulWithOverflowI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUSubWithOverflowFactory.LLVMUSubWithOverflowAndCarryI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUSubWithOverflowFactory.LLVMUSubWithOverflowAndCarryI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUSubWithOverflowFactory.LLVMUSubWithOverflowAndCarryI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUSubWithOverflowFactory.LLVMUSubWithOverflowAndCarryI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUSubWithOverflowFactory.LLVMUSubWithOverflowI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUSubWithOverflowFactory.LLVMUSubWithOverflowI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUSubWithOverflowFactory.LLVMUSubWithOverflowI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUSubWithOverflowFactory.LLVMUSubWithOverflowI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_64BitVACopyNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_64BitVAEnd;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_64BitVAStart;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_ConversionDoubleToIntNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_ConversionFloatToIntNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMFunctionLiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI1LiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI8LiteralNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstruction.LLVMAllocaConstInstruction;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstructionFactory.LLVMAllocaConstInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMCompareExchangeNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMFenceNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNode.LLVMAddressArrayLiteralNode;
import com.oracle.truffle.llvm.nodes.others.LLVMStaticInitsBlockNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.NodeFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.metadata.DebugInfoGenerator;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute.KnownAttribute;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.model.enums.Flag;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.enums.ReadModifyWriteOperator;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.NativeAllocator;
import com.oracle.truffle.llvm.runtime.NativeIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.NativeResolver;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.memory.LLVMStructByValueNodeGen;
import com.oracle.truffle.llvm.runtime.memory.LLVMVarArgCompoundAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.DataSpecConverter;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public class BasicNodeFactory implements NodeFactory {

    @Override
    public LLVMExpressionNode createInsertElement(LLVMParserRuntime runtime, Type resultType, LLVMExpressionNode vector, LLVMExpressionNode element,
                    LLVMExpressionNode index) {
        return LLVMVectorFactory.createInsertElement((VectorType) resultType, vector, element, index);
    }

    @Override
    public LLVMExpressionNode createExtractElement(LLVMParserRuntime runtime, Type resultType, LLVMExpressionNode vector, LLVMExpressionNode index) {
        return LLVMVectorFactory.createExtractElement((PrimitiveType) resultType, vector, index);
    }

    @Override
    public LLVMExpressionNode createShuffleVector(LLVMParserRuntime runtime, Type llvmType, LLVMExpressionNode vector1, LLVMExpressionNode vector2,
                    LLVMExpressionNode mask) {
        return LLVMVectorFactory.createShuffleVector((VectorType) llvmType, vector1, vector2, mask);
    }

    @Override
    public LLVMExpressionNode createLoad(LLVMParserRuntime runtime, Type resolvedResultType, LLVMExpressionNode loadTarget) {
        return LLVMMemoryReadWriteFactory.createLoad(resolvedResultType, loadTarget);
    }

    @Override
    public LLVMExpressionNode createStore(LLVMParserRuntime runtime, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type, SourceSection source) {
        return LLVMMemoryReadWriteFactory.createStore(runtime, pointerNode, valueNode, type, source);
    }

    @Override
    public LLVMExpressionNode createReadModifyWrite(LLVMParserRuntime runtime, ReadModifyWriteOperator operator, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        return LLVMMemoryReadWriteFactory.createReadModifyWrite(operator, pointerNode, valueNode, type);
    }

    @Override
    public LLVMExpressionNode createFence(LLVMParserRuntime runtime) {
        return LLVMFenceNodeGen.create();
    }

    @Override
    public LLVMExpressionNode createLogicalOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionKind type, Type llvmType, Flag[] flags) {
        return LLVMLogicalFactory.createLogicalOperation(left, right, type, llvmType);
    }

    @Override
    public LLVMExpressionNode createSimpleConstantNoArray(LLVMParserRuntime runtime, Object constant, Type type) {
        return LLVMLiteralFactory.createSimpleConstantNoArray(runtime.getContext(), constant, type);
    }

    @Override
    public LLVMExpressionNode createVectorLiteralNode(LLVMParserRuntime runtime, List<LLVMExpressionNode> listValues, Type type) {
        return LLVMLiteralFactory.createVectorLiteralNode(listValues, (VectorType) type);
    }

    @Override
    public LLVMFrameNuller createFrameNuller(FrameSlot slot) {
        return new LLVMFrameNuller(slot);
    }

    @Override
    public LLVMControlFlowNode createRetVoid(LLVMParserRuntime runtime, SourceSection source) {
        return LLVMVoidReturnNodeGen.create(source);
    }

    @Override
    public LLVMControlFlowNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type resolvedType, SourceSection source) {
        return LLVMFunctionFactory.createNonVoidRet(runtime, retValue, resolvedType, source);
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int argIndex, Type paramType) {
        return LLVMFunctionFactory.createFunctionArgNode(argIndex, paramType);
    }

    @Override
    public LLVMExpressionNode createFunctionCall(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type, SourceSection sourceSection) {
        return LLVMFunctionFactory.createFunctionCall(functionNode, argNodes, type, sourceSection);
    }

    @Override
    public LLVMExpressionNode createFrameRead(LLVMParserRuntime runtime, Type llvmType, FrameSlot frameSlot) {
        return LLVMFrameReadWriteFactory.createFrameRead(llvmType, frameSlot);
    }

    @Override
    public LLVMExpressionNode createFrameWrite(LLVMParserRuntime runtime, Type llvmType, LLVMExpressionNode result, FrameSlot slot, SourceSection sourceSection) {
        return LLVMFrameReadWriteFactory.createFrameWrite(llvmType, result, slot, sourceSection);
    }

    @Override
    public LLVMExpressionNode createComparison(LLVMParserRuntime runtime, CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        return LLVMComparisonFactory.toCompareVectorNode(operator, type, lhs, rhs);
    }

    @Override
    public LLVMExpressionNode createCast(LLVMParserRuntime runtime, LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType type) {
        return LLVMCastsFactory.cast(fromNode, targetType, fromType, type);
    }

    @Override
    public LLVMExpressionNode createArithmeticOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType type, Type llvmType, Flag[] flags) {
        return LLVMArithmeticFactory.createArithmeticOperation(left, right, type, llvmType);
    }

    @Override
    public LLVMExpressionNode createExtractValue(LLVMParserRuntime runtime, Type type, LLVMExpressionNode targetAddress) {
        return LLVMAggregateFactory.createExtractValue(type, targetAddress);
    }

    @Override
    public LLVMExpressionNode createTypedElementPointer(LLVMParserRuntime runtime, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index, int indexedTypeLength,
                    Type targetType) {
        return LLVMAddressGetElementPtrNodeGen.create(aggregateAddress, index, indexedTypeLength, targetType);
    }

    @Override
    public LLVMExpressionNode createSelect(LLVMParserRuntime runtime, Type type, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        if (type instanceof VectorType) {
            return LLVMSelectFactory.createSelectVector(type, condition, trueValue, falseValue);
        } else {
            return LLVMSelectFactory.createSelect(type, condition, trueValue, falseValue);
        }
    }

    @Override
    public LLVMExpressionNode createZeroVectorInitializer(LLVMParserRuntime runtime, int nrElements, VectorType llvmType) {
        return LLVMLiteralFactory.createZeroVectorInitializer(nrElements, llvmType);
    }

    @Override
    public LLVMExpressionNode createLiteral(LLVMParserRuntime runtime, Object value, Type type) {
        return LLVMLiteralFactory.createLiteral(value, type);
    }

    @Override
    public LLVMControlFlowNode createUnreachableNode(LLVMParserRuntime runtime) {
        return new LLVMUnreachableNode();
    }

    @Override
    public LLVMControlFlowNode createIndirectBranch(LLVMParserRuntime runtime, LLVMExpressionNode value, int[] labelTargets, LLVMExpressionNode[] phiWrites, SourceSection source) {
        return LLVMIndirectBranchNode.create(new LLVMIndirectBranchNode.LLVMBasicBranchAddressNode(value), labelTargets, phiWrites, source);
    }

    @Override
    public LLVMControlFlowNode createSwitch(LLVMParserRuntime runtime, LLVMExpressionNode cond, int[] successors, LLVMExpressionNode[] cases,
                    PrimitiveType llvmType, LLVMExpressionNode[] phiWriteNodes, SourceSection source) {
        switch (llvmType.getPrimitiveKind()) {
            case I1:
                LLVMExpressionNode[] i1Cases = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
                return new LLVMI1SwitchNode(cond, i1Cases, successors, phiWriteNodes, source);
            case I8:
                LLVMExpressionNode[] i8Cases = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
                return new LLVMI8SwitchNode(cond, i8Cases, successors, phiWriteNodes, source);
            case I16:
                LLVMExpressionNode[] i16Cases = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
                return new LLVMI16SwitchNode(cond, i16Cases, successors, phiWriteNodes, source);
            case I32:
                LLVMExpressionNode[] i32Cases = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
                return new LLVMI32SwitchNode(cond, i32Cases, successors, phiWriteNodes, source);
            case I64:
                LLVMExpressionNode[] i64Cases = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
                return new LLVMI64SwitchNode(cond, i64Cases, successors, phiWriteNodes, source);
            default:
                throw new AssertionError(llvmType);
        }
    }

    @Override
    public LLVMControlFlowNode createConditionalBranch(LLVMParserRuntime runtime, int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMExpressionNode truePhiWriteNodes,
                    LLVMExpressionNode falsePhiWriteNodes, SourceSection sourceSection) {
        return LLVMConditionalBranchNode.create(trueIndex, falseIndex, truePhiWriteNodes, falsePhiWriteNodes, conditionNode, sourceSection);
    }

    @Override
    public LLVMControlFlowNode createUnconditionalBranch(LLVMParserRuntime runtime, int unconditionalIndex, LLVMExpressionNode phiWrites, SourceSection source) {
        return LLVMBrUnconditionalNode.create(unconditionalIndex, phiWrites, source);
    }

    @Override
    public LLVMExpressionNode createArrayLiteral(LLVMParserRuntime runtime, List<LLVMExpressionNode> arrayValues, Type arrayType) {
        return LLVMLiteralFactory.createArrayLiteral(runtime, arrayValues, (ArrayType) arrayType);
    }

    @Override
    public LLVMExpressionNode createAlloc(LLVMParserRuntime runtime, Type type, int byteSize, int alignment, Type llvmType, LLVMExpressionNode numElements) {
        if (numElements == null) {
            assert llvmType == null;
            if (type instanceof StructureType) {
                StructureType struct = (StructureType) type;
                final int[] offsets = new int[struct.getNumberOfElements()];
                final Type[] types = new Type[struct.getNumberOfElements()];
                int currentOffset = 0;
                for (int i = 0; i < struct.getNumberOfElements(); i++) {
                    final Type elemType = struct.getElementType(i);

                    if (!struct.isPacked()) {
                        currentOffset += runtime.getBytePadding(currentOffset, elemType);
                    }

                    offsets[i] = currentOffset;
                    types[i] = elemType;
                    currentOffset += runtime.getByteSize(elemType);
                }
                LLVMAllocaConstInstruction alloc = LLVMAllocaConstInstructionNodeGen.create(byteSize, alignment, type);
                alloc.setTypes(types);
                alloc.setOffsets(offsets);
                return alloc;
            }
            return LLVMAllocaConstInstructionNodeGen.create(byteSize, alignment, type);
        } else {
            return LLVMAllocaInstructionNodeGen.create(numElements, byteSize, alignment, type);
        }
    }

    @Override
    public LLVMExpressionNode createInsertValue(LLVMParserRuntime runtime, LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset,
                    LLVMExpressionNode valueToInsert, Type llvmType) {
        return LLVMAggregateFactory.createInsertValue(resultAggregate, sourceAggregate, size, offset, valueToInsert, llvmType);
    }

    @Override
    public LLVMExpressionNode createZeroNode(LLVMParserRuntime runtime, LLVMExpressionNode addressNode, int size) {
        return LLVMMemSetNodeGen.create(addressNode, new LLVMI8LiteralNode((byte) 0), new LLVMI32LiteralNode(size), new LLVMI32LiteralNode(0), new LLVMI1LiteralNode(false), null);
    }

    @Override
    public LLVMExpressionNode createStructureConstantNode(LLVMParserRuntime runtime, Type structType, boolean packed, Type[] types, LLVMExpressionNode[] constants) {
        return LLVMAggregateFactory.createStructConstantNode(runtime, structType, packed, types, constants);
    }

    @Override
    public LLVMExpressionNode createBasicBlockNode(LLVMParserRuntime runtime, LLVMExpressionNode[] statementNodes, LLVMControlFlowNode terminatorNode, int blockId, String blockName) {
        return new LLVMBasicBlockNode(statementNodes, terminatorNode, blockId, blockName);
    }

    @Override
    public LLVMExpressionNode createFunctionBlockNode(LLVMParserRuntime runtime, FrameSlot exceptionValueSlot, List<? extends LLVMExpressionNode> allFunctionNodes,
                    FrameSlot[][] beforeBlockNuller, FrameSlot[][] afterBlockNuller) {
        return new LLVMDispatchBasicBlockNode(exceptionValueSlot, allFunctionNodes.toArray(new LLVMBasicBlockNode[allFunctionNodes.size()]), beforeBlockNuller, afterBlockNuller);
    }

    @Override
    public RootNode createFunctionStartNode(LLVMParserRuntime runtime, LLVMExpressionNode functionBodyNode, LLVMExpressionNode[] copyArgumentsToFrame,
                    SourceSection sourceSection, FrameDescriptor frame, FunctionDefinition functionHeader, Source bcSource) {
        String originalName = DebugInfoGenerator.getSourceFunctionName(functionHeader);
        return new LLVMFunctionStartNode(sourceSection, runtime.getLanguage(), functionBodyNode, copyArgumentsToFrame, frame, functionHeader.getName(), functionHeader.getParameters().size(),
                        originalName, bcSource);
    }

    @Override
    public LLVMExpressionNode createInlineAssemblerExpression(LLVMParserRuntime runtime, String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type[] argTypes, Type retType,
                    SourceSection sourceSection) {
        Type[] retTypes = null;
        int[] retOffsets = null;
        if (retType instanceof StructureType) { // multiple out values
            assert args[1] instanceof LLVMAllocaConstInstruction;
            LLVMAllocaConstInstruction alloca = (LLVMAllocaConstInstruction) args[1];
            retTypes = alloca.getTypes();
            retOffsets = alloca.getOffsets();
        }

        Parser asmParser = new Parser(asmExpression, asmFlags, argTypes, retType, retTypes, retOffsets);
        LLVMInlineAssemblyRootNode assemblyRoot = asmParser.Parse();
        LLVMFunctionDescriptor asm = LLVMFunctionDescriptor.createDescriptor(runtime.getContext(), "<asm>", new FunctionType(MetaType.UNKNOWN, new Type[0], false), -1);
        asm.declareInSulong(Truffle.getRuntime().createCallTarget(assemblyRoot), false);
        LLVMFunctionLiteralNode asmFunction = LLVMFunctionLiteralNodeGen.create(asm);

        return new LLVMCallNode(new FunctionType(MetaType.UNKNOWN, argTypes, false), asmFunction, args, sourceSection);
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int i, Class<? extends Node> clazz) {
        return LLVMArgNodeGen.create(i);
    }

    @Override
    public Object allocateGlobalVariable(LLVMParserRuntime runtime, GlobalVariable globalVariable) {
        return allocateGlobalIntern(runtime, globalVariable);

    }

    private static Object allocateGlobalIntern(LLVMParserRuntime runtime, final GlobalValueSymbol globalVariable) {
        final Type resolvedType = ((PointerType) globalVariable.getType()).getPointeeType();
        final String name = globalVariable.getName();

        final NativeResolver nativeResolver = () -> LLVMAddress.fromLong(runtime.getNativeHandle(name));

        final LLVMGlobalVariable descriptor = LLVMGlobalVariable.create(name, nativeResolver, resolvedType);

        if ((globalVariable.getInitialiser() > 0 || !Linkage.isExtern(globalVariable.getLinkage())) && descriptor.isUninitialized()) {
            runtime.addDestructor(new LLVMExpressionNode() {

                private final LLVMGlobalVariable global = descriptor;

                @Child private LLVMGlobalVariableAccess access = createGlobalAccess();

                @Override
                public Object executeGeneric(VirtualFrame frame) {
                    access.destroy(global);
                    return null;
                }
            });
            descriptor.declareInSulong(new NativeAllocator() {
                private final int byteSize = runtime.getByteSize(resolvedType);

                @Override
                public LLVMAddress allocate() {
                    return LLVMHeap.allocateMemory(byteSize);
                }
            });
        }

        return descriptor;
    }

    @Override
    public Object allocateGlobalConstant(LLVMParserRuntime runtime, GlobalConstant globalConstant) {
        return allocateGlobalIntern(runtime, globalConstant);
    }

    @Override
    public RootNode createStaticInitsRootNode(LLVMParserRuntime runtime, LLVMExpressionNode[] staticInits) {
        return new LLVMStaticInitsBlockNode(runtime.getLanguage(), staticInits, runtime.getGlobalFrameDescriptor());
    }

    @Override
    public LLVMControlFlowNode createFunctionInvoke(LLVMParserRuntime runtime, FrameSlot resultLocation, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type,
                    int normalIndex, int unwindIndex, LLVMExpressionNode normalPhiWriteNodes, LLVMExpressionNode unwindPhiWriteNodes,
                    SourceSection sourceSection) {
        return LLVMFunctionFactory.createFunctionInvoke(resultLocation, functionNode, argNodes, type, normalIndex, unwindIndex, normalPhiWriteNodes, unwindPhiWriteNodes,
                        sourceSection);
    }

    @Override
    public LLVMExpressionNode createLandingPad(LLVMParserRuntime runtime, LLVMExpressionNode allocateLandingPadValue, FrameSlot exceptionValueSlot, boolean cleanup, long[] clauseKinds,
                    LLVMExpressionNode[] entries) {

        LLVMLandingpadNode.LandingpadEntryNode[] landingpadEntries = new LLVMLandingpadNode.LandingpadEntryNode[entries.length];
        for (int i = 0; i < entries.length; i++) {
            if (clauseKinds[i] == 0) {
                // catch
                landingpadEntries[i] = getLandingpadCatchEntry(entries[i]);
            } else if (clauseKinds[i] == 1) {
                // filter
                landingpadEntries[i] = getLandingpadFilterEntry(entries[i]);
            } else {
                throw new IllegalStateException();
            }
        }
        return new LLVMLandingpadNode(allocateLandingPadValue, exceptionValueSlot, cleanup, landingpadEntries);
    }

    private static LLVMLandingpadNode.LandingpadEntryNode getLandingpadCatchEntry(LLVMExpressionNode exp) {
        return new LLVMLandingpadNode.LandingpadCatchEntryNode(exp);
    }

    private static LLVMLandingpadNode.LandingpadEntryNode getLandingpadFilterEntry(LLVMExpressionNode exp) {
        LLVMAddressArrayLiteralNode array = (LLVMAddressArrayLiteralNode) exp;
        LLVMExpressionNode[] types = array == null ? new LLVMExpressionNode[]{} : array.getValues();
        return new LLVMLandingpadNode.LandingpadFilterEntryNode(types);
    }

    @Override
    public LLVMControlFlowNode createResumeInstruction(LLVMParserRuntime runtime, FrameSlot exceptionValueSlot, SourceSection source) {
        return new LLVMResumeNode(exceptionValueSlot, source);
    }

    @Override
    public LLVMExpressionNode createCompareExchangeInstruction(LLVMParserRuntime runtime, Type returnType, Type elementType, LLVMExpressionNode ptrNode, LLVMExpressionNode cmpNode,
                    LLVMExpressionNode newNode) {
        return LLVMCompareExchangeNodeGen.create(runtime.getByteSize(returnType),
                        runtime.getIndexOffset(1, (AggregateType) returnType), ptrNode, cmpNode, newNode);
    }

    @Override
    public LLVMExpressionNode createLLVMBuiltin(LLVMParserRuntime runtime, Symbol target, LLVMExpressionNode[] args, int callerArgumentCount, SourceSection sourceSection) {
        /*
         * This LLVM Builtins are *not* function intrinsics. Builtins replace statements that look
         * like function calls but are actually LLVM intrinsics. An example is llvm.stackpointer.
         * Also, it is not possible to retrieve the functionpointer of such pseudo-call-targets.
         *
         * This builtins shall not be used for regular function intrinsification!
         */
        if (target instanceof FunctionDeclaration) {
            FunctionDeclaration declaration = (FunctionDeclaration) target;
            if (declaration.getName().startsWith("@llvm.")) {
                return getLLVMBuiltin(runtime, declaration, args, callerArgumentCount, sourceSection);
            } else if (declaration.getName().startsWith("@__builtin_")) {
                return getGccBuiltin(declaration, args, sourceSection);
            } else if (declaration.getName().equals("@truffle_get_arg")) {
                // this function accesses the frame directly
                // it must therefore not be hidden behind a call target
                return LLVMTruffleGetArgNodeGen.create(args[1], sourceSection);
            } else if (declaration.getName().equals("@__divsc3")) {
                // this function allocates the result on the stack
                return new LLVMComplexDivSC(args[1], args[2], args[3], args[4]);
            }
        }
        return null;
    }

    protected LLVMExpressionNode getLLVMBuiltin(LLVMParserRuntime runtime, FunctionDeclaration declaration, LLVMExpressionNode[] args, int callerArgumentCount, SourceSection sourceSection) {

        switch (declaration.getName()) {
            case "@llvm.memset.p0i8.i32":
            case "@llvm.memset.p0i8.i64":
                return LLVMMemSetNodeGen.create(args[1], args[2], args[3], args[4], args[5], sourceSection);
            case "@llvm.assume":
                return LLVMAssumeNodeGen.create(args[1], sourceSection);
            case "@llvm.donothing":
                return LLVMNoOpNodeGen.create(sourceSection);
            case "@llvm.prefetch":
                return LLVMPrefetchNodeGen.create(args[1], args[2], args[3], args[4], sourceSection);
            case "@llvm.ctlz.i8":
                return CountLeadingZeroesI8NodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.ctlz.i16":
                return CountLeadingZeroesI16NodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.ctlz.i32":
                return CountLeadingZeroesI32NodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.ctlz.i64":
                return CountLeadingZeroesI64NodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.memcpy.p0i8.p0i8.i64":
                return LLVMMemI64CopyNodeGen.create(args[1], args[2], args[3], args[4], args[5], sourceSection);
            case "@llvm.memcpy.p0i8.p0i8.i32":
                return LLVMMemI32CopyNodeGen.create(args[1], args[2], args[3], args[4], args[5], sourceSection);
            case "@llvm.ctpop.i32":
                return CountSetBitsI32NodeGen.create(args[1], sourceSection);
            case "@llvm.ctpop.i64":
                return CountSetBitsI64NodeGen.create(args[1], sourceSection);
            case "@llvm.cttz.i8":
                return CountTrailingZeroesI8NodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.cttz.i16":
                return CountTrailingZeroesI16NodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.cttz.i32":
                return CountTrailingZeroesI32NodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.cttz.i64":
                return CountTrailingZeroesI64NodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.trap":
                return LLVMTrapNodeGen.create(sourceSection);
            case "@llvm.bswap.i16":
                return LLVMByteSwapI16NodeGen.create(args[1], sourceSection);
            case "@llvm.bswap.i32":
                return LLVMByteSwapI32NodeGen.create(args[1], sourceSection);
            case "@llvm.bswap.i64":
                return LLVMByteSwapI64NodeGen.create(args[1], sourceSection);
            case "@llvm.memmove.p0i8.p0i8.i64":
                return LLVMMemMoveI64NodeGen.create(args[1], args[2], args[3], args[4], args[5], sourceSection);
            case "@llvm.pow.f32":
                return LLVMPowNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.pow.f64":
                return LLVMPowNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.pow.f80":
                return LLVMPowNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.powi.f32":
                return LLVMPowNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.powi.f64":
                return LLVMPowNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.powi.f80":
                return LLVMPowNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.fabs.f32":
            case "@llvm.fabs.f64":
                return LLVMFAbsNodeGen.create(args[1], sourceSection);
            case "@llvm.returnaddress":
                return LLVMReturnAddressNodeGen.create(args[1], sourceSection);
            case "@llvm.lifetime.start":
                return LLVMLifetimeStartNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.lifetime.end":
                return LLVMLifetimeEndNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.invariant.start":
                return LLVMInvariantStartNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.invariant.end":
                return LLVMInvariantEndNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.stacksave":
                return LLVMStackSaveNodeGen.create(sourceSection);
            case "@llvm.stackrestore":
                return LLVMStackRestoreNodeGen.create(args[1], sourceSection);
            case "@llvm.frameaddress":
                return LLVMFrameAddressNodeGen.create(args[1], sourceSection);
            case "@llvm.va_start":
                return new LLVMX86_64BitVAStart(callerArgumentCount, args[1], sourceSection);
            case "@llvm.va_end":
                return new LLVMX86_64BitVAEnd(args[1], sourceSection);
            case "@llvm.va_copy":
                return LLVMX86_64BitVACopyNodeGen.create(args[1], args[2], sourceSection, callerArgumentCount);
            case "@llvm.eh.sjlj.longjmp":
            case "@llvm.eh.sjlj.setjmp":
                throw new LLVMUnsupportedException(UnsupportedReason.SET_JMP_LONG_JMP);
            case "@llvm.dbg.declare":
                throw new IllegalStateException("@llvm.dbg.declare should be handled in the parser!");
            case "@llvm.dbg.value":
                throw new IllegalStateException("@llvm.dbg.value should be handled in the parser!");
            case "@llvm.eh.typeid.for":
                return new LLVMTypeIdForExceptionNode(args[1], sourceSection);
            case "@llvm.expect.i1": {
                boolean expectedValue = args[2].executeI1(null);
                LLVMExpressionNode actualValueNode = args[1];
                return LLVMExpectI1NodeGen.create(expectedValue, actualValueNode, sourceSection);
            }
            case "@llvm.expect.i32": {
                int expectedValue = args[2].executeI32(null);
                LLVMExpressionNode actualValueNode = args[1];
                return LLVMExpectI32NodeGen.create(expectedValue, actualValueNode, sourceSection);
            }
            case "@llvm.expect.i64": {
                long expectedValue = args[2].executeI64(null);
                LLVMExpressionNode actualValueNode = args[1];
                return LLVMExpectI64NodeGen.create(expectedValue, actualValueNode, sourceSection);
            }
            case "@llvm.objectsize.i64.p0i8":
            case "@llvm.objectsize.i64":
                return LLVMI64ObjectSizeNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.copysign.f32":
            case "@llvm.copysign.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMCopySignNodeGen.create(args[1], args[2], sourceSection);

            case "@llvm.uadd.with.overflow.i8":
                return LLVMUAddWithOverflowI8NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.uadd.with.overflow.i16":
                return LLVMUAddWithOverflowI16NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.uadd.with.overflow.i32":
                return LLVMUAddWithOverflowI32NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.uadd.with.overflow.i64":
                return LLVMUAddWithOverflowI64NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.usub.with.overflow.i8":
                return LLVMUSubWithOverflowI8NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.usub.with.overflow.i16":
                return LLVMUSubWithOverflowI16NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.usub.with.overflow.i32":
                return LLVMUSubWithOverflowI32NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.usub.with.overflow.i64":
                return LLVMUSubWithOverflowI64NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.umul.with.overflow.i8":
                return LLVMUMulWithOverflowI8NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.umul.with.overflow.i16":
                return LLVMUMulWithOverflowI16NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.umul.with.overflow.i32":
                return LLVMUMulWithOverflowI32NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.umul.with.overflow.i64":
                return LLVMUMulWithOverflowI64NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.sadd.with.overflow.i8":
                return LLVMSAddWithOverflowI8NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.sadd.with.overflow.i16":
                return LLVMSAddWithOverflowI16NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.sadd.with.overflow.i32":
                return LLVMSAddWithOverflowI32NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.sadd.with.overflow.i64":
                return LLVMSAddWithOverflowI64NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.ssub.with.overflow.i8":
                return LLVMSSubWithOverflowI8NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.ssub.with.overflow.i16":
                return LLVMSSubWithOverflowI16NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.ssub.with.overflow.i32":
                return LLVMSSubWithOverflowI32NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.ssub.with.overflow.i64":
                return LLVMSSubWithOverflowI64NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.smul.with.overflow.i8":
                return LLVMSMulWithOverflowI8NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.smul.with.overflow.i16":
                return LLVMSMulWithOverflowI16NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.smul.with.overflow.i32":
                return LLVMSMulWithOverflowI32NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.smul.with.overflow.i64":
                return LLVMSMulWithOverflowI64NodeGen.create(getOverflowFieldOffset(runtime, declaration), args[2], args[3], args[1], sourceSection);
            case "@llvm.exp2.f32":
            case "@llvm.exp2.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMExp2NodeGen.create(args[1], sourceSection);
            case "@llvm.sqrt.f32":
            case "@llvm.sqrt.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMSqrtNodeGen.create(args[1], sourceSection);
            case "@llvm.sin.f32":
            case "@llvm.sin.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMSinNodeGen.create(args[1], sourceSection);
            case "@llvm.cos.f32":
            case "@llvm.cos.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMCosNodeGen.create(args[1], sourceSection);
            case "@llvm.exp.f32":
            case "@llvm.exp.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMExpNodeGen.create(args[1], sourceSection);
            case "@llvm.log.f32":
            case "@llvm.log.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMLogNodeGen.create(args[1], sourceSection);
            case "@llvm.log2.f32":
            case "@llvm.log2.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMLog2NodeGen.create(args[1], sourceSection);
            case "@llvm.log10.f32":
            case "@llvm.log10.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMLog10NodeGen.create(args[1], sourceSection);
            case "@llvm.floor.f32":
            case "@llvm.floor.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMFloorNodeGen.create(args[1], sourceSection);
            case "@llvm.ceil.f32":
            case "@llvm.ceil.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMCeilNodeGen.create(args[1], sourceSection);
            case "@llvm.rint.f32":
            case "@llvm.rint.f64":
                return LLVMCMathsIntrinsicsFactory.LLVMRintNodeGen.create(args[1], sourceSection);
            case "@llvm.x86.sse.cvtss2si":
                return LLVMX86_ConversionFloatToIntNodeGen.create(args[1], sourceSection);
            case "@llvm.x86.sse2.cvtsd2si":
                return LLVMX86_ConversionDoubleToIntNodeGen.create(args[1], sourceSection);
            default:
                throw new IllegalStateException("Missing LLVM builtin: " + declaration.getName());
        }

    }

    private static int getOverflowFieldOffset(LLVMParserRuntime runtime, FunctionDeclaration declaration) {
        return runtime.getIndexOffset(1, (AggregateType) declaration.getType().getReturnType());
    }

    protected LLVMExpressionNode getGccBuiltin(FunctionDeclaration declaration, LLVMExpressionNode[] args, SourceSection sourceSection) {
        switch (declaration.getName()) {
            case "@__builtin_addcb":
                return LLVMUAddWithOverflowAndCarryI8NodeGen.create(args[1], args[2], args[3], args[4], sourceSection);
            case "@__builtin_addcs":
                return LLVMUAddWithOverflowAndCarryI16NodeGen.create(args[1], args[2], args[3], args[4], sourceSection);
            case "@__builtin_addc":
                return LLVMUAddWithOverflowAndCarryI32NodeGen.create(args[1], args[2], args[3], args[4], sourceSection);
            case "@__builtin_addcl":
                return LLVMUAddWithOverflowAndCarryI64NodeGen.create(args[1], args[2], args[3], args[4], sourceSection);
            case "@__builtin_subcb":
                return LLVMUSubWithOverflowAndCarryI8NodeGen.create(args[1], args[2], args[3], args[4], sourceSection);
            case "@__builtin_subcs":
                return LLVMUSubWithOverflowAndCarryI16NodeGen.create(args[1], args[2], args[3], args[4], sourceSection);
            case "@__builtin_subc":
                return LLVMUSubWithOverflowAndCarryI32NodeGen.create(args[1], args[2], args[3], args[4], sourceSection);
            case "@__builtin_subcl":
                return LLVMUSubWithOverflowAndCarryI64NodeGen.create(args[1], args[2], args[3], args[4], sourceSection);

            case "@__builtin_add_overflow":
                if (isZeroExtendArithmeticBuiltin(declaration)) {
                    throw new IllegalStateException("Missing GCC builtin: " + declaration.getName());
                } else {
                    return GCCAddWithOverflowNodeGen.create(args[1], args[2], args[3], sourceSection);
                }
            case "@__builtin_sub_overflow":
                if (isZeroExtendArithmeticBuiltin(declaration)) {
                    throw new IllegalStateException("Missing GCC builtin: " + declaration.getName());
                } else {
                    return GCCSubWithOverflowNodeGen.create(args[1], args[2], args[3], sourceSection);
                }
            case "@__builtin_mul_overflow":
                if (isZeroExtendArithmeticBuiltin(declaration)) {
                    return GCCUMulWithOverflowNodeGen.create(args[1], args[2], args[3], sourceSection);
                } else {
                    return GCCSMulWithOverflowNodeGen.create(args[1], args[2], args[3], sourceSection);
                }

            default:
                throw new IllegalStateException("Missing GCC builtin: " + declaration.getName());
        }
    }

    private static boolean isZeroExtendArithmeticBuiltin(FunctionDeclaration declaration) {
        final AttributesGroup group = declaration.getParameterAttributesGroup(0);
        if (group == null) {
            return false;
        }
        for (Attribute a : group.getAttributes()) {
            if (a instanceof KnownAttribute && ((KnownAttribute) a).getAttr() == Attribute.Kind.ZEROEXT) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NativeIntrinsicProvider getNativeIntrinsicsFactory(LLVMLanguage language, LLVMContext context, DataSpecConverter dataLayout) {
        return new LLVMNativeIntrinsicsProvider(context, language, dataLayout).collectIntrinsics();
    }

    @Override
    public LLVMExpressionNode createPhi(LLVMExpressionNode[] from, FrameSlot[] to, Type[] types) {
        return new LLVMWritePhisNode(from, to, types);
    }

    @Override
    public RootNode createGlobalRootNode(LLVMParserRuntime runtime, RootCallTarget mainCallTarget,
                    Object[] args, Source sourceFile, Type[] mainTypes) {
        return LLVMRootNodeFactory.createGlobalRootNode(runtime, mainCallTarget, args, sourceFile, mainTypes);
    }

    @Override
    public RootNode createGlobalRootNodeWrapping(LLVMParserRuntime runtime, RootCallTarget mainCallTarget, Type returnType) {
        return LLVMFunctionFactory.createGlobalRootNodeWrapping(runtime.getLanguage(), mainCallTarget, returnType);
    }

    @Override
    public LLVMExpressionNode createCopyStructByValue(LLVMParserRuntime runtime, int length, int alignment, LLVMExpressionNode parameterNode) {
        return LLVMStructByValueNodeGen.create(parameterNode, length, alignment);
    }

    @Override
    public LLVMExpressionNode createVarArgCompoundValue(LLVMParserRuntime runtime, int length, int alignment, LLVMExpressionNode parameterNode) {
        return LLVMVarArgCompoundAddressNodeGen.create(parameterNode, length, alignment);
    }
}
