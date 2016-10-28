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
package com.oracle.truffle.llvm.parser.bc.impl;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMTerminatorNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI16LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI64LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI8LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAddressGetElementPtrNodeFactory;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.bc.impl.nodes.LLVMNodeGenerator;
import com.oracle.truffle.llvm.parser.base.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.bc.impl.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

import com.oracle.truffle.llvm.parser.base.model.enums.AsmDialect;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.base.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.base.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.base.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.ValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.base.model.types.AggregateType;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;
import com.oracle.truffle.llvm.runtime.LLVMLogger;

public final class LLVMBitcodeInstructionVisitor implements InstructionVisitor {

    private final LLVMBitcodeFunctionVisitor method;

    private final InstructionBlock block;

    private final LLVMNodeGenerator symbols;

    private final LLVMBitcodeTypeHelper typeHelper;

    private final NodeFactoryFacade factoryFacade;

    public LLVMBitcodeInstructionVisitor(LLVMBitcodeFunctionVisitor method, InstructionBlock block, NodeFactoryFacade factoryFacade) {
        this.method = method;
        this.block = block;
        this.symbols = method.getSymbolResolver();
        this.typeHelper = method.getModule().getTypeHelper();
        this.factoryFacade = factoryFacade;
    }

    private LLVMNode[] getPhiWriteNodes() {
        List<LLVMNode> nodes = new ArrayList<>();
        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            for (Phi phi : phis) {
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                LLVMBaseType baseType = phi.getValue().getType().getLLVMBaseType();
                LLVMNode phiWriteNode = factoryFacade.createFrameWrite(baseType, value, slot);
                nodes.add(phiWriteNode);
            }
        }
        return nodes.toArray(new LLVMNode[nodes.size()]);
    }

    @Override
    public void visit(AllocateInstruction allocate) {
        final Type type = allocate.getPointeeType();
        int alignment = 0;
        if (allocate.getAlign() == 0) {
            alignment = type.getAlignment(method.getTargetDataLayout());
        } else {
            alignment = 1 << (allocate.getAlign() - 1);
        }
        if (alignment == 0) {
            alignment = LLVMStack.NO_ALIGNMENT_REQUIREMENTS;
        }

        final int size = type.getSize(typeHelper.getTargetDataLayout());
        final Symbol count = allocate.getCount();
        final LLVMExpressionNode result;
        if (count instanceof NullConstant) {
            result = factoryFacade.createAlloc(type, size, alignment, null, null);
        } else if (count instanceof IntegerConstant) {
            result = factoryFacade.createAlloc(type, size * (int) ((IntegerConstant) count).getValue(), alignment, null, null);
        } else {
            LLVMExpressionNode num = symbols.resolve(count);
            result = factoryFacade.createAlloc(type, size, alignment, count.getType().getLLVMBaseType(), num);
        }

        createFrameWrite(result, allocate);
    }

    @Override
    public void visit(BinaryOperationInstruction operation) {
        LLVMExpressionNode lhs = symbols.resolve(operation.getLHS());
        LLVMExpressionNode rhs = symbols.resolve(operation.getRHS());

        LLVMAddressNode target = null;
        if (operation.getType() instanceof VectorType) {
            Type operationType = operation.getType();
            final int size = operationType.getSize(typeHelper.getTargetDataLayout());
            final int alignment = operationType.getAlignment(method.getTargetDataLayout());
            target = (LLVMAddressNode) factoryFacade.createAlloc(operationType, size, alignment, null, null);
        }

        final LLVMBaseType type = operation.getType().getLLVMBaseType();
        final LLVMArithmeticInstructionType opA = LLVMBitcodeTypeHelper.toArithmeticInstructionType(operation.getOperator());
        if (opA != null) {
            final LLVMExpressionNode result = factoryFacade.createArithmeticOperation(lhs, rhs, opA, type, target);
            createFrameWrite(result, operation);
            return;
        }

        final LLVMLogicalInstructionType opL = LLVMBitcodeTypeHelper.toLogicalInstructionType(operation.getOperator());
        if (opL != null) {
            final LLVMExpressionNode result = factoryFacade.createLogicalOperation(lhs, rhs, opL, type, target);
            createFrameWrite(result, operation);
            return;
        }

        throw new RuntimeException("Missed a binary operator");
    }

    @Override
    public void visit(BranchInstruction branch) {
        method.addTerminatingInstruction((LLVMTerminatorNode) factoryFacade.createUnconditionalBranch(method.labels().get(branch.getSuccessor().getName()),
                        getPhiWriteNodes()), block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(CallInstruction call) {
        final Type targetType = call.getType();
        final LLVMBaseType targetLLVMType = targetType.getLLVMBaseType();
        final int argumentCount = call.getArgumentCount() + (targetType instanceof StructureType ? 2 : 1);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        int argIndex = 0;
        argNodes[argIndex++] = factoryFacade.createFrameRead(LLVMBaseType.ADDRESS, method.getStackSlot());
        if (targetType instanceof StructureType) {
            final int size = targetType.getSize(typeHelper.getTargetDataLayout());
            final int align = targetType.getAlignment(method.getTargetDataLayout());
            argNodes[argIndex++] = factoryFacade.createAlloc(targetType, size, align, null, null);
        }
        for (int i = 0; argIndex < argumentCount; i++, argIndex++) {
            argNodes[argIndex] = symbols.resolve(call.getArgument(i));
        }

        final Symbol target = call.getCallTarget();
        LLVMExpressionNode result;
        if (target instanceof FunctionDeclaration && (((ValueSymbol) target).getName()).startsWith("@llvm.")) {
            FunctionDeclaration targetDecl = (FunctionDeclaration) target;
            result = (LLVMExpressionNode) factoryFacade.createLLVMIntrinsic(targetDecl, argNodes, targetDecl.getArgumentTypes().length);

        } else if (target instanceof FunctionDeclaration && (((ValueSymbol) target).getName()).startsWith("@truffle_")) {
            method.addInstruction(factoryFacade.createTruffleIntrinsic(((ValueSymbol) target).getName(), argNodes));
            return;

        } else if (target instanceof InlineAsmConstant) {
            final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
            result = (LLVMExpressionNode) createInlineAssemblerNode(inlineAsmConstant, argNodes, targetLLVMType);

        } else {
            LLVMFunctionNode function = (LLVMFunctionNode) symbols.resolve(target);
            result = (LLVMExpressionNode) factoryFacade.createFunctionCall(function, argNodes, targetLLVMType);
        }

        createFrameWrite(result, call);
    }

    @Override
    public void visit(CastInstruction cast) {
        LLVMConversionType type = LLVMBitcodeTypeHelper.toConversionType(cast.getOperator());
        LLVMExpressionNode fromNode = symbols.resolve(cast.getValue());
        Type from = cast.getValue().getType();
        Type to = cast.getType();

        LLVMExpressionNode result = factoryFacade.createCast(fromNode, to, from, type);
        createFrameWrite(result, cast);
    }

    @Override
    public void visit(CompareInstruction compare) {
        LLVMExpressionNode result;

        if (compare.getType() instanceof VectorType) {
            Type type = compare.getType();
            final int size = type.getSize(typeHelper.getTargetDataLayout());
            final int alignment = type.getAlignment(method.getTargetDataLayout());
            LLVMAddressNode target = (LLVMAddressNode) factoryFacade.createAlloc(type, size, alignment, null, null);

            result = LLVMNodeGenerator.toCompareVectorNode(
                            compare.getOperator(),
                            compare.getLHS().getType(),
                            target,
                            symbols.resolve(compare.getLHS()),
                            symbols.resolve(compare.getRHS()));
        } else {
            result = LLVMNodeGenerator.toCompareNode(
                            compare.getOperator(),
                            compare.getLHS().getType(),
                            symbols.resolve(compare.getLHS()),
                            symbols.resolve(compare.getRHS()));
        }

        createFrameWrite(result, compare);
    }

    @Override
    public void visit(ConditionalBranchInstruction branch) {
        LLVMExpressionNode conditionNode = symbols.resolve(branch.getCondition());
        int trueIndex = method.labels().get(branch.getTrueSuccessor().getName());
        int falseIndex = method.labels().get(branch.getFalseSuccessor().getName());

        List<LLVMNode> trueConditionPhiWriteNodes = new ArrayList<>();
        List<LLVMNode> falseConditionPhiWriteNodes = new ArrayList<>();

        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            for (Phi phi : phis) {
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                LLVMBaseType baseType = phi.getValue().getType().getLLVMBaseType();
                LLVMNode phiWriteNode = factoryFacade.createFrameWrite(baseType, value, slot);

                if (branch.getTrueSuccessor() == phi.getBlock()) {
                    trueConditionPhiWriteNodes.add(phiWriteNode);
                } else {
                    falseConditionPhiWriteNodes.add(phiWriteNode);
                }
            }
        }
        LLVMNode[] truePhiWriteNodes = trueConditionPhiWriteNodes.toArray(new LLVMNode[trueConditionPhiWriteNodes.size()]);
        LLVMNode[] falsePhiWriteNodes = falseConditionPhiWriteNodes.toArray(new LLVMNode[falseConditionPhiWriteNodes.size()]);
        LLVMTerminatorNode node = (LLVMTerminatorNode) factoryFacade.createConditionalBranch(trueIndex, falseIndex, conditionNode, truePhiWriteNodes, falsePhiWriteNodes);

        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(ExtractElementInstruction extract) {
        LLVMExpressionNode vector = symbols.resolve(extract.getVector());
        LLVMExpressionNode index = symbols.resolve(extract.getIndex());
        LLVMBaseType resultType = extract.getType().getLLVMBaseType();

        LLVMExpressionNode result = factoryFacade.createExtractElement(resultType, vector, index);

        createFrameWrite(result, extract);
    }

    @Override
    public void visit(ExtractValueInstruction extract) {
        if (!(extract.getAggregate().getType() instanceof ArrayType || extract.getAggregate().getType() instanceof StructureType)) {
            throw new IllegalStateException("\'extractvalue\' can only extract elements of arrays and structs!");
        }
        final LLVMExpressionNode baseAddress = symbols.resolve(extract.getAggregate());
        final Type baseType = extract.getAggregate().getType();
        final int targetIndex = extract.getIndex();
        final LLVMBaseType resultType = extract.getType().getLLVMBaseType();

        LLVMAddressNode targetAddress = (LLVMAddressNode) baseAddress;

        final AggregateType aggregateType = (AggregateType) baseType;

        int offset = aggregateType.getIndexOffset(targetIndex, typeHelper.getTargetDataLayout());

        final Type targetType = aggregateType.getElementType(targetIndex);
        if (targetType != null && !((targetType instanceof StructureType) && (((StructureType) targetType).isPacked()))) {
            offset += Type.getPadding(offset, targetType, typeHelper.getTargetDataLayout());
        }

        if (offset != 0) {
            targetAddress = LLVMAddressGetElementPtrNodeFactory.LLVMAddressI32GetElementPtrNodeGen.create(targetAddress, new LLVMI32LiteralNode(1), offset);
        }

        final LLVMExpressionNode result = factoryFacade.createExtractValue(resultType, targetAddress);
        createFrameWrite(result, extract);
    }

    @Override
    public void visit(GetElementPointerInstruction gep) {
        final LLVMExpressionNode targetAddress = symbols.resolveElementPointer(gep.getBasePointer(), gep.getIndices());
        createFrameWrite(targetAddress, gep);
    }

    @Override
    public void visit(IndirectBranchInstruction branch) {
        int[] labelTargets = new int[branch.getSuccessorCount()];
        for (int i = 0; i < labelTargets.length; i++) {
            labelTargets[i] = method.labels().get(branch.getSuccessor(i).getName());
        }
        LLVMAddressNode value = (LLVMAddressNode) symbols.resolve(branch.getAddress());

        LLVMTerminatorNode node = (LLVMTerminatorNode) factoryFacade.createIndirectBranch(value, labelTargets, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(InsertElementInstruction insert) {
        final LLVMExpressionNode vector = symbols.resolve(insert.getVector());
        final LLVMI32Node index = (LLVMI32Node) symbols.resolve(insert.getIndex());
        final LLVMExpressionNode element = symbols.resolve(insert.getValue());
        final Type type = insert.getType();
        final LLVMBaseType resultType = type.getLLVMBaseType();

        final int size = type.getSize(method.getTargetDataLayout());
        final int alignment = type.getAlignment(method.getTargetDataLayout());
        final LLVMAddressNode target = (LLVMAddressNode) factoryFacade.createAlloc(type, size, alignment, null, null);

        final LLVMExpressionNode result = factoryFacade.createInsertElement(resultType, vector, target, element, index);

        createFrameWrite(result, insert);
    }

    @Override
    public void visit(InsertValueInstruction insert) {
        if (!(insert.getAggregate().getType() instanceof StructureType || insert.getAggregate().getType() instanceof ArrayType)) {
            throw new IllegalStateException("\'insertvalue\' can only insert values into arrays and structs!");
        }
        final AggregateType sourceType = (AggregateType) insert.getAggregate().getType();
        final LLVMExpressionNode sourceAggregate = symbols.resolve(insert.getAggregate());
        final LLVMExpressionNode valueToInsert = symbols.resolve(insert.getValue());
        final LLVMBaseType valueType = insert.getValue().getType().getLLVMBaseType();
        final int targetIndex = insert.getIndex();
        final int size = sourceType.getSize(method.getTargetDataLayout());
        final int alignment = sourceType.getAlignment(method.getTargetDataLayout());

        final LLVMExpressionNode resultAggregate = factoryFacade.createAlloc(sourceType, size, alignment, null, null);

        final int offset = sourceType.getIndexOffset(targetIndex, typeHelper.getTargetDataLayout());
        final LLVMExpressionNode result = factoryFacade.createInsertValue(resultAggregate, sourceAggregate,
                        sourceType.getSize(method.getTargetDataLayout()), offset, valueToInsert, valueType);

        createFrameWrite(result, insert);
    }

    @Override
    public void visit(LoadInstruction load) {
        LLVMAddressNode source = (LLVMAddressNode) symbols.resolve(load.getSource());
        LLVMExpressionNode result = factoryFacade.createLoad(load.getType(), source);
        createFrameWrite(result, load);
    }

    @Override
    public void visit(PhiInstruction pi) {
    }

    @Override
    public void visit(ReturnInstruction ret) {
        LLVMNode node;
        if (ret.getValue() == null) {
            node = factoryFacade.createRetVoid();
        } else {
            final Type type = ret.getValue().getType();
            method.getFrame().findFrameSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID).setKind(type.getFrameSlotKind());
            final LLVMExpressionNode value = symbols.resolve(ret.getValue());
            node = factoryFacade.createNonVoidRet(value, type);
        }
        method.addTerminatingInstruction((LLVMTerminatorNode) node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(SelectInstruction select) {
        final LLVMExpressionNode condition = symbols.resolve(select.getCondition());
        final LLVMExpressionNode trueValue = symbols.resolve(select.getTrueValue());
        final LLVMExpressionNode falseValue = symbols.resolve(select.getFalseValue());
        final Type type = select.getType();

        final LLVMExpressionNode result = factoryFacade.createSelect(type, condition, trueValue, falseValue);

        createFrameWrite(result, select);
    }

    @Override
    public void visit(ShuffleVectorInstruction shuffle) {
        final LLVMExpressionNode vector1 = symbols.resolve(shuffle.getVector1());
        final LLVMExpressionNode vector2 = symbols.resolve(shuffle.getVector2());
        final LLVMI32VectorNode mask = (LLVMI32VectorNode) symbols.resolve(shuffle.getMask());

        final Type type = shuffle.getType();
        final int size = type.getSize(method.getTargetDataLayout());
        final int alignment = type.getAlignment(method.getTargetDataLayout());
        final LLVMAddressNode destination = (LLVMAddressNode) factoryFacade.createAlloc(type, size, alignment, null, null);

        final LLVMExpressionNode result = factoryFacade.createShuffleVector(type.getLLVMBaseType(), destination, vector1, vector2, mask);

        createFrameWrite(result, shuffle);
    }

    @Override
    public void visit(StoreInstruction store) {
        final LLVMAddressNode pointerNode = (LLVMAddressNode) symbols.resolve(store.getDestination());
        final LLVMExpressionNode valueNode = symbols.resolve(store.getSource());

        final Type type = store.getSource().getType();

        final LLVMNode node = factoryFacade.createStore(pointerNode, valueNode, type);

        method.addInstruction(node);
    }

    @Override
    public void visit(SwitchInstruction zwitch) {
        LLVMExpressionNode cond = symbols.resolve(zwitch.getCondition());
        int defaultLabel = method.labels().get(zwitch.getDefaultBlock().getName());
        int[] otherLabels = new int[zwitch.getCaseCount()];
        for (int i = 0; i < otherLabels.length; i++) {
            otherLabels[i] = method.labels().get(zwitch.getCaseBlock(i).getName());
        }
        LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            cases[i] = symbols.resolve(zwitch.getCaseValue(i));
        }
        LLVMBaseType llvmType = zwitch.getCondition().getType().getLLVMBaseType();

        LLVMTerminatorNode node = (LLVMTerminatorNode) factoryFacade.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(SwitchOldInstruction zwitch) {
        LLVMExpressionNode cond = symbols.resolve(zwitch.getCondition());
        int defaultLabel = method.labels().get(zwitch.getDefaultBlock().getName());
        int[] otherLabels = new int[zwitch.getCaseCount()];
        for (int i = 0; i < otherLabels.length; i++) {
            otherLabels[i] = method.labels().get(zwitch.getCaseBlock(i).getName());
        }
        LLVMBaseType llvmType = zwitch.getCondition().getType().getLLVMBaseType();
        LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            if (llvmType == LLVMBaseType.I8) {
                cases[i] = new LLVMI8LiteralNode((byte) zwitch.getCaseValue(i));
            } else if (llvmType == LLVMBaseType.I16) {
                cases[i] = new LLVMI16LiteralNode((short) zwitch.getCaseValue(i));
            } else if (llvmType == LLVMBaseType.I32) {
                cases[i] = new LLVMI32LiteralNode((int) zwitch.getCaseValue(i));
            } else {
                cases[i] = new LLVMI64LiteralNode(zwitch.getCaseValue(i));
            }
        }

        LLVMTerminatorNode node = (LLVMTerminatorNode) factoryFacade.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(UnreachableInstruction ui) {
        method.addTerminatingInstruction((LLVMTerminatorNode) factoryFacade.createUnreachableNode(), block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(VoidCallInstruction call) {
        final Symbol target = call.getCallTarget();
        final int argumentCount = call.getArgumentCount();
        final LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount + 1];

        int argIndex = 0;
        args[argIndex++] = factoryFacade.createFrameRead(LLVMBaseType.ADDRESS, method.getStackSlot());
        for (int i = 0; i < argumentCount; i++) {
            args[argIndex++] = symbols.resolve(call.getArgument(i));
        }

        final LLVMNode node;
        if (target instanceof FunctionDeclaration && (((ValueSymbol) target).getName()).startsWith("@llvm.")) {
            // number of arguments of the caller so llvm intrinsics can distinguish varargs
            final int parentArgCount = method.getArgCount();
            node = factoryFacade.createLLVMIntrinsic((FunctionDeclaration) target, args, parentArgCount);

        } else if (target instanceof FunctionDeclaration && (((ValueSymbol) target).getName()).startsWith("@truffle_")) {
            node = factoryFacade.createTruffleIntrinsic(((ValueSymbol) target).getName(), args);
            if (node == null) {
                throw new UnsupportedOperationException("Unknown Truffle Intrinsic: " + ((ValueSymbol) target).getName());
            }

        } else if (target instanceof InlineAsmConstant) {
            final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
            node = createInlineAssemblerNode(inlineAsmConstant, args, call.getType().getLLVMBaseType());

        } else {
            LLVMFunctionNode function = (LLVMFunctionNode) symbols.resolve(target);
            node = factoryFacade.createFunctionCall(function, args, call.getType().getLLVMBaseType());
        }

        method.addInstruction(node);
    }

    private void createFrameWrite(LLVMExpressionNode result, ValueInstruction source) {
        final LLVMNode node = factoryFacade.createFrameWrite(source.getType().getLLVMBaseType(), result, method.getSlot(source.getName()));
        method.addInstruction(node);
    }

    private LLVMNode createInlineAssemblerNode(InlineAsmConstant inlineAsmConstant, LLVMExpressionNode[] argNodes, LLVMBaseType retType) {
        if (inlineAsmConstant.hasSideEffects()) {
            LLVMLogger.info("Parsing Inline Assembly Constant with Sideeffects!");
        }
        if (inlineAsmConstant.needsAlignedStack()) {
            throw new UnsupportedOperationException("Assembly Expressions that require an aligned Stack are not supported yet!");
        }
        if (inlineAsmConstant.getDialect() != AsmDialect.AT_T) {
            throw new UnsupportedOperationException("Unsupported Assembly Dialect: " + inlineAsmConstant.getDialect());
        }
        return factoryFacade.createInlineAssemblerExpression(inlineAsmConstant.getAsmExpression(), inlineAsmConstant.getAsmFlags(), argNodes, retType);
    }
}
