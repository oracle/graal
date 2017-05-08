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
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI16CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI32CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI64CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI8CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMStructCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.func.LLVMLandingpadNode;
import com.oracle.truffle.llvm.nodes.func.LLVMResumeNode;
import com.oracle.truffle.llvm.nodes.func.LLVMTypeIdForExceptionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMPowNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleGetArgNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI1NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMFrameAddressNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMI64ObjectSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMLifetimeEndNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMLifetimeStartNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI32CopyNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI64CopyNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemMoveFactory.LLVMMemMoveI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemSetFactory.LLVMMemSetI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemSetFactory.LLVMMemSetI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMNoOpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMPrefetchNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMReturnAddressNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMStackRestoreNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMStackSaveNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMTrapNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMCopySignFactory.LLVMCopySignDoubleFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMCopySignFactory.LLVMCopySignFloatFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_64BitVACopyNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_64BitVAEnd;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_64BitVAStart;
import com.oracle.truffle.llvm.nodes.literals.LLVMFunctionLiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressZeroNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstruction.LLVMAllocaInstruction;
import com.oracle.truffle.llvm.nodes.memory.LLVMCompareExchangeNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNode.LLVMAddressArrayLiteralNode;
import com.oracle.truffle.llvm.nodes.others.LLVMStaticInitsBlockNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVM80BitFloatUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMAddressUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMDoubleUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMFloatUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMFunctionUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMI1UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.SulongNodeFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.NativeAllocator;
import com.oracle.truffle.llvm.runtime.NativeIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.NativeResolver;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller.LLVMAddressNuller;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller.LLVMBooleanNuller;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller.LLVMByteNuller;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller.LLVMDoubleNuller;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller.LLVMFloatNuller;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller.LLVMIntNuller;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller.LLVMLongNuller;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public class BasicSulongNodeFactory implements SulongNodeFactory {

    @Override
    public String getConfigurationName() {
        return "default";
    }

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
    public LLVMExpressionNode createLogicalOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionKind type, Type llvmType) {
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
    public LLVMControlFlowNode createRetVoid(LLVMParserRuntime runtime, SourceSection source) {
        return LLVMFunctionFactory.createRetVoid(runtime, source);
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
    public LLVMExpressionNode createArithmeticOperation(LLVMParserRuntime runtime, LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType type, Type llvmType) {
        return LLVMArithmeticFactory.createArithmeticOperation(left, right, type, llvmType);
    }

    @Override
    public LLVMExpressionNode createExtractValue(LLVMParserRuntime runtime, Type type, LLVMExpressionNode targetAddress) {
        return LLVMAggregateFactory.createExtractValue(type, targetAddress);
    }

    @Override
    public LLVMExpressionNode createTypedElementPointer(LLVMParserRuntime runtime, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index, int indexedTypeLength,
                    Type targetType) {
        return LLVMGetElementPtrFactory.create(aggregateAddress, index, indexedTypeLength, targetType);
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
    public LLVMControlFlowNode createIndirectBranch(LLVMParserRuntime runtime, LLVMExpressionNode value, int[] labelTargets, LLVMExpressionNode[][] phiWrites, SourceSection source) {
        return LLVMBranchFactory.createIndirectBranch(value, labelTargets, phiWrites, source);
    }

    @Override
    public LLVMControlFlowNode createSwitch(LLVMParserRuntime runtime, LLVMExpressionNode cond, int[] successors, LLVMExpressionNode[] cases,
                    PrimitiveType llvmType, LLVMExpressionNode[][] phiWriteNodes, SourceSection source) {
        return LLVMSwitchFactory.createSwitch(cond, successors, cases, llvmType, phiWriteNodes, source);
    }

    @Override
    public LLVMControlFlowNode createConditionalBranch(LLVMParserRuntime runtime, int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMExpressionNode[] truePhiWriteNodes,
                    LLVMExpressionNode[] falsePhiWriteNodes, SourceSection sourceSection) {
        return LLVMBranchFactory.createConditionalBranch(trueIndex, falseIndex, conditionNode, truePhiWriteNodes, falsePhiWriteNodes, sourceSection);
    }

    @Override
    public LLVMControlFlowNode createUnconditionalBranch(LLVMParserRuntime runtime, int unconditionalIndex, LLVMExpressionNode[] phiWrites, SourceSection source) {
        return LLVMBranchFactory.createUnconditionalBranch(unconditionalIndex, phiWrites, source);
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
                LLVMAllocaInstruction alloc = LLVMAllocFactory.createAlloc(runtime, byteSize, alignment, type);
                alloc.setTypes(types);
                alloc.setOffsets(offsets);
                return alloc;
            }
            return LLVMAllocFactory.createAlloc(runtime, byteSize, alignment, type);
        } else {
            return LLVMAllocFactory.createAlloc(runtime, (PrimitiveType) llvmType, numElements, byteSize, alignment, type);
        }
    }

    @Override
    public LLVMExpressionNode createInsertValue(LLVMParserRuntime runtime, LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset,
                    LLVMExpressionNode valueToInsert, Type llvmType) {
        return LLVMAggregateFactory.createInsertValue(runtime, resultAggregate, sourceAggregate, size, offset, valueToInsert, llvmType);
    }

    @Override
    public LLVMExpressionNode createZeroNode(LLVMParserRuntime runtime, LLVMExpressionNode addressNode, int size) {
        return new LLVMAddressZeroNode(addressNode, size);
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
    public LLVMExpressionNode createStructureConstantNode(LLVMParserRuntime runtime, Type structType, boolean packed, Type[] types, LLVMExpressionNode[] constants) {
        return LLVMAggregateFactory.createStructConstantNode(runtime, structType, packed, types, constants);
    }

    @Override
    public LLVMExpressionNode createBasicBlockNode(LLVMParserRuntime runtime, LLVMExpressionNode[] statementNodes, LLVMControlFlowNode terminatorNode, int blockId, String blockName) {
        return LLVMBlockFactory.createBasicBlock(statementNodes, terminatorNode, blockId, blockName);
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
                nullers[i] = runtime.getNodeFactory().createFrameNuller(runtime, identifier, functionHeader.getType().getReturnType(), slot);
            } else if (slot.equals(runtime.getStackPointerSlot())) {
                nullers[i] = runtime.getNodeFactory().createFrameNuller(runtime, identifier, new PointerType(null), slot);
            } else {
                assert slotType != null : identifier;
                nullers[i] = runtime.getNodeFactory().createFrameNuller(runtime, identifier, slotType, slot);
            }
            i++;
        }
        return new LLVMFunctionStartNode(sourceSection, runtime.getLanguage(), functionBodyNode, beforeFunction, afterFunction, frameDescriptor, functionHeader.getName(), nullers,
                        functionHeader.getParameters().size());
    }

    @Override
    public LLVMExpressionNode createInlineAssemblerExpression(LLVMParserRuntime runtime, String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type[] argTypes, Type retType,
                    SourceSection sourceSection) {
        Parser asmParser = new Parser(asmExpression, asmFlags, args, argTypes, retType);
        LLVMInlineAssemblyRootNode assemblyRoot = asmParser.Parse();
        LLVMFunctionDescriptor asm = LLVMFunctionDescriptor.create(runtime.getContext(), "<asm>", new FunctionType(MetaType.UNKNOWN, new Type[0], false), -1);
        asm.declareInSulong(Truffle.getRuntime().createCallTarget(assemblyRoot));
        LLVMFunctionLiteralNode asmFunction = LLVMFunctionLiteralNodeGen.create(asm);

        LLVMCallNode callNode = new LLVMCallNode(new FunctionType(MetaType.UNKNOWN, argTypes, false), asmFunction, args, sourceSection);
        if (retType instanceof VoidType) {
            return callNode;
        } else if (retType instanceof StructureType) {
            return LLVMStructCallUnboxNodeGen.create(callNode);
        } else if (retType instanceof PointerType) {
            return new LLVMFunctionUnsupportedInlineAssemblerNode(sourceSection);
        } else if (retType instanceof PointerType) {
            return new LLVMAddressUnsupportedInlineAssemblerNode(sourceSection);
        } else if (retType instanceof PrimitiveType) {
            switch (((PrimitiveType) retType).getPrimitiveKind()) {
                case I1:
                    return new LLVMI1UnsupportedInlineAssemblerNode(sourceSection);
                case I8:
                    return LLVMI8CallUnboxNodeGen.create(callNode);
                case I16:
                    return LLVMI16CallUnboxNodeGen.create(callNode);
                case I32:
                    return LLVMI32CallUnboxNodeGen.create(callNode);
                case I64:
                    return LLVMI64CallUnboxNodeGen.create(callNode);
                case FLOAT:
                    return new LLVMFloatUnsupportedInlineAssemblerNode(sourceSection);
                case DOUBLE:
                    return new LLVMDoubleUnsupportedInlineAssemblerNode(sourceSection);
                case X86_FP80:
                    return new LLVM80BitFloatUnsupportedInlineAssemblerNode(sourceSection);
                default:
                    throw new AssertionError(retType);
            }
        } else {
            throw new AssertionError(retType);
        }

    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int i, Class<? extends Node> clazz) {
        return LLVMFunctionFactory.createFunctionArgNode(i);
    }

    @Override
    public Object allocateGlobalVariable(LLVMParserRuntime runtime, GlobalVariable globalVariable) {
        return allocateGlobalIntern(runtime, globalVariable);

    }

    private static Object allocateGlobalIntern(LLVMParserRuntime runtime, final GlobalValueSymbol globalVariable) {
        final Type resolvedType = ((PointerType) globalVariable.getType()).getPointeeType();
        final String name = globalVariable.getName();

        final NativeResolver nativeResolver = () -> LLVMAddress.fromLong(runtime.getNativeHandle(name));

        final LLVMGlobalVariable descriptor;
        if (globalVariable.isStatic()) {
            descriptor = LLVMGlobalVariable.create(name, nativeResolver, resolvedType);
        } else {
            final LLVMContext context = runtime.getContext();
            descriptor = context.getGlobalVariableRegistry().lookupOrAdd(name, nativeResolver, resolvedType);
        }

        if ((globalVariable.getInitialiser() > 0 || !globalVariable.isExtern()) && descriptor.isUninitialized()) {
            runtime.addDestructor(new LLVMExpressionNode() {

                private final LLVMGlobalVariable global = descriptor;

                @Override
                public Object executeGeneric(VirtualFrame frame) {
                    global.destroy();
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
        return new LLVMStaticInitsBlockNode(runtime.getLanguage(), staticInits, runtime.getGlobalFrameDescriptor(), runtime.getContext(),
                        runtime.getStackPointerSlot());
    }

    @Override
    public LLVMStackFrameNuller createFrameNuller(LLVMParserRuntime runtime, String identifier, Type llvmType, FrameSlot slot) {
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
                LLVMLogger.info("illegal frame slot at stack nuller: " + identifier);
                return new LLVMAddressNuller(slot);
            default:
                throw new AssertionError();
        }

    }

    @Override
    public LLVMFunctionDescriptor createFunctionDescriptor(LLVMContext context, String name, FunctionType type, int functionIndex) {
        return LLVMFunctionDescriptor.create(context, name, type, functionIndex);
    }

    @Override
    public LLVMFunctionDescriptor createAndRegisterFunctionDescriptor(LLVMParserRuntime runtime, String name, FunctionType type) {
        return runtime.getContext().lookupFunctionDescriptor(name, i -> runtime.getNodeFactory().createFunctionDescriptor(runtime.getContext(), name, type, i));
    }

    @Override
    public LLVMControlFlowNode createFunctionInvoke(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type,
                    FrameSlot returnValueSlot, FrameSlot exceptionValueSlot, int normalIndex, int unwindIndex, LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes,
                    SourceSection sourceSection) {
        return LLVMFunctionFactory.createFunctionInvoke(functionNode, argNodes, type, returnValueSlot, exceptionValueSlot, normalIndex, unwindIndex, normalPhiWriteNodes, unwindPhiWriteNodes,
                        sourceSection);
    }

    @Override
    public LLVMExpressionNode createLandingPad(LLVMParserRuntime runtime, LLVMExpressionNode allocateLandingPadValue, FrameSlot exceptionSlot, boolean cleanup, long[] clauseKinds,
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
        return LLVMFunctionFactory.createLandingpad(allocateLandingPadValue, exceptionSlot, cleanup, landingpadEntries);
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
    public LLVMControlFlowNode createResumeInstruction(LLVMParserRuntime runtime, FrameSlot exceptionSlot, SourceSection source) {
        return new LLVMResumeNode(exceptionSlot, source);
    }

    @Override
    public LLVMExpressionNode createCompareExchangeInstruction(LLVMParserRuntime runtime, Type returnType, Type elementType, LLVMExpressionNode ptrNode, LLVMExpressionNode cmpNode,
                    LLVMExpressionNode newNode) {
        return LLVMCompareExchangeNodeGen.create(runtime.getStackPointerSlot(), returnType, runtime.getByteSize(returnType),
                        runtime.getIndexOffset(1, (AggregateType) returnType), ptrNode, cmpNode, newNode);
    }

    @Override
    public LLVMExpressionNode createLLVMBuiltin(Symbol target, LLVMExpressionNode[] args, FrameSlot stack, int callerArgumentCount, SourceSection sourceSection) {
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
                return getLLVMBuiltin(declaration.getName(), args, stack, callerArgumentCount, sourceSection);
            } else if (declaration.getName().equals("@truffle_get_arg")) {
                // this function accesses the frame directly
                // it must therefore not be hidden behind a call target
                return LLVMTruffleGetArgNodeGen.create(args[1], sourceSection);
            }
        }
        return null;
    }

    protected LLVMExpressionNode getLLVMBuiltin(String name, LLVMExpressionNode[] args, FrameSlot stack, int callerArgumentCount, SourceSection sourceSection) {
        switch (name) {
            case "@llvm.memset.p0i8.i32":
                return LLVMMemSetI32NodeGen.create(args[1], args[2], args[3], args[4], args[5], sourceSection);
            case "@llvm.memset.p0i8.i64":
                return LLVMMemSetI64NodeGen.create(args[1], args[2], args[3], args[4], args[5], sourceSection);
            case "@llvm.donothing":
                return LLVMNoOpNodeGen.create(sourceSection);
            case "@llvm.prefetch":
                return LLVMPrefetchNodeGen.create(args[1], args[2], args[3], args[4], sourceSection);
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
            case "@llvm.powi.f64":
                return LLVMPowNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.powi.f32":
                return LLVMPowNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.fabs.f64":
                return LLVMFAbsNodeGen.create(args[1], sourceSection);
            case "@llvm.returnaddress":
                return LLVMReturnAddressNodeGen.create(args[1], sourceSection);
            case "@llvm.lifetime.start":
                return LLVMLifetimeStartNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.lifetime.end":
                return LLVMLifetimeEndNodeGen.create(args[1], args[2], sourceSection);
            case "@llvm.uadd.with.overflow.i32":
                return LLVMUAddWithOverflowI32NodeGen.create(args[2], args[3], args[1], sourceSection);
            case "@llvm.stacksave":
                return LLVMStackSaveNodeGen.create(args[0], sourceSection);
            case "@llvm.stackrestore":
                return LLVMStackRestoreNodeGen.create(args[1], sourceSection, stack);
            case "@llvm.frameaddress":
                return LLVMFrameAddressNodeGen.create(args[1], sourceSection, stack);
            case "@llvm.va_start":
                return new LLVMX86_64BitVAStart(callerArgumentCount, args[1], stack, sourceSection);
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
                return LLVMCopySignFloatFactory.create(args[0], args[1]);
            case "@llvm.copysign.f64":
                return LLVMCopySignDoubleFactory.create(args[0], args[1]);

            default:
                throw new IllegalStateException("Missing LLVM builtin: " + name);
        }

    }

    @Override
    public NativeIntrinsicProvider getNativeIntrinsicsFactory(LLVMLanguage language, LLVMContext context) {
        return new LLVMNativeIntrinsicsProvider(context, language).collectIntrinsics();
    }
}
