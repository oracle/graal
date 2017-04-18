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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
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
import com.oracle.truffle.llvm.nodes.func.LLVMGlobalRootNode;
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.func.LLVMLandingpadNode;
import com.oracle.truffle.llvm.nodes.func.LLVMResumeNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexDiv;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexDivSC;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexMul;
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
import com.oracle.truffle.llvm.parser.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.NativeAllocator;
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

public class NodeFactoryFacadeImpl implements NodeFactoryFacade {

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
    public LLVMExpressionNode createStore(LLVMParserRuntime runtime, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        return LLVMMemoryReadWriteFactory.createStore(runtime, pointerNode, valueNode, type);
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
    public LLVMControlFlowNode createRetVoid(LLVMParserRuntime runtime) {
        return LLVMFunctionFactory.createRetVoid(runtime);
    }

    @Override
    public LLVMControlFlowNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type resolvedType) {
        return LLVMFunctionFactory.createNonVoidRet(runtime, retValue, resolvedType);
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int argIndex, Type paramType) {
        return LLVMFunctionFactory.createFunctionArgNode(argIndex, paramType);
    }

    @Override
    public LLVMExpressionNode createFunctionCall(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type) {
        return LLVMFunctionFactory.createFunctionCall(functionNode, argNodes, type);
    }

    @Override
    public LLVMExpressionNode createFrameRead(LLVMParserRuntime runtime, Type llvmType, FrameSlot frameSlot) {
        return LLVMFrameReadWriteFactory.createFrameRead(llvmType, frameSlot);
    }

    @Override
    public LLVMExpressionNode createFrameWrite(LLVMParserRuntime runtime, Type llvmType, LLVMExpressionNode result, FrameSlot slot) {
        return LLVMFrameReadWriteFactory.createFrameWrite(llvmType, result, slot);
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
    public LLVMControlFlowNode createIndirectBranch(LLVMParserRuntime runtime, LLVMExpressionNode value, int[] labelTargets, LLVMExpressionNode[] phiWrites) {
        return LLVMBranchFactory.createIndirectBranch(value, labelTargets, phiWrites);
    }

    @Override
    public LLVMControlFlowNode createSwitch(LLVMParserRuntime runtime, LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases,
                    PrimitiveType llvmType, LLVMExpressionNode[] phiWriteNodes) {
        return LLVMSwitchFactory.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, phiWriteNodes);
    }

    @Override
    public LLVMControlFlowNode createConditionalBranch(LLVMParserRuntime runtime, int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMExpressionNode[] truePhiWriteNodes,
                    LLVMExpressionNode[] falsePhiWriteNodes) {
        return LLVMBranchFactory.createConditionalBranch(trueIndex, falseIndex, conditionNode, truePhiWriteNodes, falsePhiWriteNodes);
    }

    @Override
    public LLVMControlFlowNode createUnconditionalBranch(LLVMParserRuntime runtime, int unconditionalIndex, LLVMExpressionNode[] phiWrites) {
        return LLVMBranchFactory.createUnconditionalBranch(unconditionalIndex, phiWrites);
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
        return new LLVMAddressZeroNode(runtime.getNativeFunctions(), addressNode, size);
    }

    @Override
    public LLVMGlobalRootNode createGlobalRootNode(LLVMParserRuntime runtime, RootCallTarget mainCallTarget,
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
                nullers[i] = runtime.getNodeFactoryFacade().createFrameNuller(runtime, identifier, functionHeader.getType().getReturnType(), slot);
            } else if (slot.equals(runtime.getStackPointerSlot())) {
                nullers[i] = runtime.getNodeFactoryFacade().createFrameNuller(runtime, identifier, new PointerType(null), slot);
            } else {
                assert slotType != null : identifier;
                nullers[i] = runtime.getNodeFactoryFacade().createFrameNuller(runtime, identifier, slotType, slot);
            }
            i++;
        }
        return new LLVMFunctionStartNode(sourceSection, runtime.getLanguage(), functionBodyNode, beforeFunction, afterFunction, frameDescriptor, functionHeader.getName(), nullers,
                        functionHeader.getParameters().size());
    }

    @Override
    public Optional<Integer> getArgStartIndex() {
        return Optional.of(LLVMCallNode.ARG_START_INDEX);
    }

    @Override
    public LLVMExpressionNode createInlineAssemblerExpression(LLVMParserRuntime runtime, String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type[] argTypes, Type retType) {
        Parser asmParser = new Parser(asmExpression, asmFlags, args, argTypes, retType);
        LLVMInlineAssemblyRootNode assemblyRoot = asmParser.Parse();
        LLVMFunctionDescriptor asm = LLVMFunctionDescriptor.create(runtime.getContext(), "<asm>", new FunctionType(MetaType.UNKNOWN, new Type[0], false), -1);
        asm.setCallTarget(Truffle.getRuntime().createCallTarget(assemblyRoot));
        LLVMFunctionLiteralNode asmFunction = LLVMFunctionLiteralNodeGen.create(asm);

        LLVMCallNode callNode = new LLVMCallNode(new FunctionType(MetaType.UNKNOWN, argTypes, false), asmFunction, args);
        if (retType instanceof VoidType) {
            return callNode;
        } else if (retType instanceof StructureType) {
            return LLVMStructCallUnboxNodeGen.create(callNode);
        } else if (retType instanceof PointerType) {
            return new LLVMFunctionUnsupportedInlineAssemblerNode();
        } else if (retType instanceof PointerType) {
            return new LLVMAddressUnsupportedInlineAssemblerNode();
        } else if (retType instanceof PrimitiveType) {
            switch (((PrimitiveType) retType).getPrimitiveKind()) {
                case I1:
                    return new LLVMI1UnsupportedInlineAssemblerNode();
                case I8:
                    return LLVMI8CallUnboxNodeGen.create(callNode);
                case I16:
                    return LLVMI16CallUnboxNodeGen.create(callNode);
                case I32:
                    return LLVMI32CallUnboxNodeGen.create(callNode);
                case I64:
                    return LLVMI64CallUnboxNodeGen.create(callNode);
                case FLOAT:
                    return new LLVMFloatUnsupportedInlineAssemblerNode();
                case DOUBLE:
                    return new LLVMDoubleUnsupportedInlineAssemblerNode();
                case X86_FP80:
                    return new LLVM80BitFloatUnsupportedInlineAssemblerNode();
                default:
                    throw new AssertionError(retType);
            }
        } else {
            throw new AssertionError(retType);
        }

    }

    @Override
    public Map<String, NodeFactory<? extends LLVMExpressionNode>> getFunctionSubstitutionFactories() {
        return LLVMRuntimeIntrinsicFactory.getFunctionSubstitutionFactories();
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int i, Class<? extends Node> clazz) {
        return LLVMFunctionFactory.createFunctionArgNode(this, i);
    }

    @Override
    public RootNode createFunctionSubstitutionRootNode(LLVMLanguage language, LLVMExpressionNode intrinsicNode) {
        return LLVMFunctionFactory.createFunctionSubstitutionRootNode(language, intrinsicNode);
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
    public Optional<Boolean> hasStackPointerArgument(LLVMParserRuntime runtime) {
        return Optional.of(true);
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
        return runtime.getLLVMFunctionRegistry().lookupFunctionDescriptor(name, type);
    }

    @Override
    public LLVMControlFlowNode tryCreateFunctionInvokeSubstitution(LLVMParserRuntime runtime, String name, FunctionType type, int argCount, LLVMExpressionNode[] argNodes, Type[] argTypes,
                    FrameSlot returnValueSlot, FrameSlot exceptionValueSlot, int normalIndex,
                    int unwindIndex, LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes) {
        LLVMExpressionNode substitution = getSubstitution(runtime, name, argNodes, argTypes, argCount, exceptionValueSlot);
        if (substitution != null) {
            return LLVMFunctionFactory.createFunctionInvokeSubstitution(substitution, type, returnValueSlot, exceptionValueSlot, normalIndex, unwindIndex, normalPhiWriteNodes, unwindPhiWriteNodes);
        } else {
            return null;
        }
    }

    @Override
    public LLVMExpressionNode tryCreateFunctionCallSubstitution(LLVMParserRuntime runtime, String name, LLVMExpressionNode[] argNodes, Type[] argTypes, int numberOfExplicitArguments,
                    FrameSlot exceptionValueSlot) {
        return getSubstitution(runtime, name, argNodes, argTypes, numberOfExplicitArguments, exceptionValueSlot);
    }

    private static LLVMExpressionNode getSubstitution(LLVMParserRuntime runtime, String name, LLVMExpressionNode[] argNodes, Type[] argTypes, int numberOfExplicitArguments,
                    FrameSlot exceptionValueSlot) {
        if (name.startsWith("@llvm")) {
            return LLVMIntrinsicFactory.create(name, argNodes, numberOfExplicitArguments, runtime);
        } else if (name.startsWith("@__cxa_") || name.startsWith("@__clang")) {
            return LLVMExceptionIntrinsicFactory.create(name, argNodes, numberOfExplicitArguments, runtime, exceptionValueSlot);
        } else if (name.startsWith("@truffle")) {
            return LLVMTruffleIntrinsicFactory.create(name, argNodes, argTypes);
        } else if (name.startsWith("@__divdc3")) {
            // TODO: __divdc3 returns a struct by value, which TNI does not yet support - we
            // substitute for now
            return new LLVMComplexDiv(argNodes[1], argNodes[2], argNodes[3], argNodes[4], argNodes[5]);
        } else if (name.startsWith("@__muldc3")) {
            // TODO: __muldc3 returns a struct by value, which TNI does not yet support - we
            // substitute for now
            return new LLVMComplexMul(argNodes[1], argNodes[2], argNodes[3], argNodes[4], argNodes[5]);
        } else if (name.startsWith("@__divsc3")) {
            // TODO: __divsc3 returns a struct by value, which TNI does not yet support - we
            // substitute for now
            return new LLVMComplexDivSC(argNodes[0], argNodes[1], argNodes[2], argNodes[3], argNodes[4]);
        } else {
            return null;
        }
    }

    @Override
    public LLVMControlFlowNode createFunctionInvoke(LLVMParserRuntime runtime, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type,
                    FrameSlot returnValueSlot, FrameSlot exceptionValueSlot, int normalIndex, int unwindIndex, LLVMExpressionNode[] normalPhiWriteNodes, LLVMExpressionNode[] unwindPhiWriteNodes) {
        return LLVMFunctionFactory.createFunctionInvoke(functionNode, argNodes, type, returnValueSlot, exceptionValueSlot, normalIndex, unwindIndex,
                        normalPhiWriteNodes,
                        unwindPhiWriteNodes);
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
    public LLVMControlFlowNode createResumeInstruction(LLVMParserRuntime runtime, FrameSlot exceptionSlot) {
        return new LLVMResumeNode(exceptionSlot);
    }

    @Override
    public LLVMExpressionNode createCompareExchangeInstruction(LLVMParserRuntime runtime, Type returnType, Type elementType, LLVMExpressionNode ptrNode, LLVMExpressionNode cmpNode,
                    LLVMExpressionNode newNode) {
        return LLVMCompareExchangeNodeGen.create(runtime.getStackPointerSlot(), returnType, runtime.getByteSize(returnType),
                        runtime.getIndexOffset(1, (AggregateType) returnType), ptrNode, cmpNode, newNode);
    }
}
