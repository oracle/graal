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
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNode;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMVoidReturnNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI16LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI64LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI8LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAddressGetElementPtrNodeFactory;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory.LLVMI32AllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory.LLVMI64AllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.bc.impl.nodes.LLVMNodeGenerator;
import com.oracle.truffle.llvm.parser.bc.impl.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.factories.LLVMAggregateFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMArithmeticFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMBranchFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMCastsFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFrameReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFunctionFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMGetElementPtrFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMIntrinsicFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLogicalFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMMemoryReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMSelectFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMSwitchFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMTruffleIntrinsicFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMVectorFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;

import com.oracle.truffle.llvm.types.memory.LLVMStack;
import uk.ac.man.cs.llvm.ir.model.InstructionBlock;
import uk.ac.man.cs.llvm.ir.model.FunctionDeclaration;
import uk.ac.man.cs.llvm.ir.model.InstructionVisitor;
import uk.ac.man.cs.llvm.ir.model.Symbol;
import uk.ac.man.cs.llvm.ir.model.ValueSymbol;
import uk.ac.man.cs.llvm.ir.model.constants.InlineAsmConstant;
import uk.ac.man.cs.llvm.ir.model.constants.IntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.NullConstant;
import uk.ac.man.cs.llvm.ir.model.elements.AllocateInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.BinaryOperationInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.BranchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.CallInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.CastInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.CompareInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ConditionalBranchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ExtractElementInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ExtractValueInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.GetElementPointerInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.IndirectBranchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.InsertElementInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.InsertValueInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.LoadInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.PhiInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ReturnInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.SelectInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.ShuffleVectorInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.StoreInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.SwitchInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.SwitchOldInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.UnreachableInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.VoidCallInstruction;
import uk.ac.man.cs.llvm.ir.types.AggregateType;
import uk.ac.man.cs.llvm.ir.types.ArrayType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.StructureType;
import uk.ac.man.cs.llvm.ir.types.Type;
import uk.ac.man.cs.llvm.ir.types.VectorType;

public final class LLVMBitcodeInstructionVisitor implements InstructionVisitor {

    private final LLVMBitcodeFunctionVisitor method;

    private final InstructionBlock block;

    private final LLVMNodeGenerator symbols;

    private final LLVMBitcodeTypeHelper typeHelper;

    public LLVMBitcodeInstructionVisitor(LLVMBitcodeFunctionVisitor method, InstructionBlock block) {
        this.method = method;
        this.block = block;
        this.symbols = method.getSymbolResolver();
        this.typeHelper = method.getModule().getTypeHelper();
    }

    private LLVMNode[] getPhiWriteNodes() {
        List<LLVMNode> nodes = new ArrayList<>();
        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            for (Phi phi : phis) {
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = symbols.resolve(phi.getValue());
                LLVMBaseType baseType = LLVMBitcodeHelper.toBaseType(phi.getValue().getType()).getType();
                LLVMNode phiWriteNode = LLVMFrameReadWriteFactory.createFrameWrite(baseType, value, slot);
                nodes.add(phiWriteNode);
            }
        }
        return nodes.toArray(new LLVMNode[nodes.size()]);
    }

    @Override
    public void visit(AllocateInstruction allocate) {
        Type type = allocate.getPointeeType();
        int align = allocate.getAlign();

        if (align == 0) {
            align = LLVMStack.NO_ALIGNMENT_REQUIREMENTS;
        }

        Symbol count = allocate.getCount();

        int size = LLVMBitcodeHelper.getSize(type, align);
        int alignment = LLVMBitcodeHelper.getAlignment(type, align);

        LLVMExpressionNode result;
        if (count instanceof NullConstant) {
            result = LLVMAllocaInstructionNodeGen.create(
                            size,
                            alignment,
                            method.getContext(),
                            method.getStackSlot());
        } else if (count instanceof IntegerConstant) {
            result = LLVMAllocaInstructionNodeGen.create(
                            size * (int) ((IntegerConstant) count).getValue(),
                            alignment,
                            method.getContext(),
                            method.getStackSlot());
        } else {
            LLVMExpressionNode num = symbols.resolve(count);
            switch (LLVMBitcodeHelper.toBaseType(count.getType()).getType()) {
                case I32:
                    result = LLVMI32AllocaInstructionNodeGen.create((LLVMI32Node) num, size, alignment, method.getContext(), method.getStackSlot());
                    break;
                case I64:
                    result = LLVMI64AllocaInstructionNodeGen.create((LLVMI64Node) num, size, alignment, method.getContext(), method.getStackSlot());
                    break;
                default:
                    throw new AssertionError("Unsupported type for \'count\' in alloca!");
            }
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(allocate.getType()).getType(), result, method.getFrame().findFrameSlot(allocate.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(BinaryOperationInstruction operation) {
        LLVMExpressionNode lhs = symbols.resolve(operation.getLHS());
        LLVMExpressionNode rhs = symbols.resolve(operation.getRHS());

        LLVMAddressNode target = null;
        if (operation.getType() instanceof VectorType) {
            target = LLVMAllocaInstructionNodeGen.create(LLVMBitcodeHelper.getSize(operation, 0), operation.getType().getAlignment(), method.getContext(), method.getStackSlot());
        }

        LLVMBaseType type = LLVMBitcodeHelper.toBaseType(operation.getType()).getType();
        LLVMArithmeticInstructionType opA = LLVMBitcodeHelper.toArithmeticInstructionType(operation.getOperator());
        if (opA != null) {
            LLVMExpressionNode result = LLVMArithmeticFactory.createArithmeticOperation(lhs, rhs, opA, type, target);
            LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(type, result, method.getFrame().findFrameSlot(operation.getName()));
            method.addInstruction(node);
            return;
        }

        LLVMLogicalInstructionType opL = LLVMBitcodeHelper.toLogicalInstructionType(operation.getOperator());
        if (opL != null) {
            LLVMExpressionNode result = LLVMLogicalFactory.createLogicalOperation(lhs, rhs, opL, type, target);
            LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(type, result, method.getFrame().findFrameSlot(operation.getName()));
            method.addInstruction(node);
            return;
        }

        throw new RuntimeException("Missed a binary operator");
    }

    @Override
    public void visit(BranchInstruction branch) {
        method.addTerminatingInstruction(LLVMBranchFactory.createUnconditionalBranch(method.labels().get(branch.getSuccessor().getName()), getPhiWriteNodes()), block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(CallInstruction call) {
        final Type targetType = call.getType();
        final LLVMBaseType targetLLVMType = LLVMBitcodeHelper.toBaseType(targetType).getType();
        final int argumentCount = call.getArgumentCount() + (targetType instanceof StructureType ? 2 : 1);
        final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[argumentCount];
        int argIndex = 0;
        if (targetType instanceof StructureType) {
            // TODO use LLVMAllocFactory instead to free the memory after return
            argNodes[argIndex++] = LLVMAllocaInstructionNodeGen.create(targetType.sizeof(), targetType.getAlignment(), method.getContext(), method.getStackSlot());
        }
        argNodes[argIndex++] = LLVMFrameReadWriteFactory.createFrameRead(LLVMBaseType.ADDRESS, method.getStackSlot());
        for (int i = 0; argIndex < argumentCount; i++, argIndex++) {
            argNodes[argIndex] = symbols.resolve(call.getArgument(i));
        }

        final Symbol target = call.getCallTarget();
        LLVMExpressionNode result;
        if (target instanceof FunctionDeclaration && (((ValueSymbol) target).getName()).startsWith("@llvm.")) {
            result = (LLVMExpressionNode) LLVMIntrinsicFactory.create(((ValueSymbol) target).getName(), argNodes, call.getCallType().getArgumentTypes().length, method.getStackSlot(),
                            method.getOptimizationConfiguration());

        } else if (target instanceof FunctionDeclaration && (((ValueSymbol) target).getName()).startsWith("@truffle_")) {
            method.addInstruction(LLVMTruffleIntrinsicFactory.create(((ValueSymbol) target).getName(), argNodes));
            return;

        } else if (target instanceof InlineAsmConstant) {
            final InlineAsmConstant inlineAsmConstant = (InlineAsmConstant) target;
            result = LLVMNodeGenerator.resolveInlineAsmConstant(inlineAsmConstant, argNodes, targetLLVMType);

        } else {
            LLVMFunctionNode function = (LLVMFunctionNode) symbols.resolve(target);
            result = (LLVMExpressionNode) LLVMFunctionFactory.createFunctionCall(function, argNodes, targetLLVMType);
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(targetLLVMType, result, method.getFrame().findFrameSlot(call.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(CastInstruction cast) {
        LLVMConversionType type = LLVMBitcodeHelper.toConversionType(cast.getOperator());
        LLVMExpressionNode fromNode = symbols.resolve(cast.getValue());
        LLVMBaseType from = LLVMBitcodeHelper.toBaseType(cast.getValue().getType()).getType();
        LLVMBaseType to = LLVMBitcodeHelper.toBaseType(cast.getType()).getType();

        int bits = 0;
        if (cast.getType() instanceof IntegerType) {
            bits = ((IntegerType) cast.getType()).getBitCount();
        }

        LLVMExpressionNode result = LLVMCastsFactory.cast(fromNode, to, from, type, bits);

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(cast.getType()).getType(), result, method.getFrame().findFrameSlot(cast.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(CompareInstruction compare) {
        LLVMExpressionNode result;

        if (compare.getType() instanceof VectorType) {
            Type type = compare.getType();
            LLVMAddressNode target = LLVMAllocaInstructionNodeGen.create(type.sizeof(), type.getAlignment(), method.getContext(), method.getStackSlot());

            result = LLVMBitcodeHelper.toCompareVectorNode(
                            compare.getOperator(),
                            compare.getLHS().getType(),
                            target,
                            symbols.resolve(compare.getLHS()),
                            symbols.resolve(compare.getRHS()));
        } else {
            result = LLVMBitcodeHelper.toCompareNode(
                            compare.getOperator(),
                            compare.getLHS().getType(),
                            symbols.resolve(compare.getLHS()),
                            symbols.resolve(compare.getRHS()));
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(compare.getType()).getType(), result, method.getFrame().findFrameSlot(compare.getName()));
        method.addInstruction(node);
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
                LLVMBaseType baseType = LLVMBitcodeHelper.toBaseType(phi.getValue().getType()).getType();
                LLVMNode phiWriteNode = LLVMFrameReadWriteFactory.createFrameWrite(baseType, value, slot);

                if (branch.getTrueSuccessor() == phi.getBlock()) {
                    trueConditionPhiWriteNodes.add(phiWriteNode);
                } else {
                    falseConditionPhiWriteNodes.add(phiWriteNode);
                }
            }
        }
        LLVMNode[] truePhiWriteNodes = trueConditionPhiWriteNodes.toArray(new LLVMNode[trueConditionPhiWriteNodes.size()]);
        LLVMNode[] falsePhiWriteNodes = falseConditionPhiWriteNodes.toArray(new LLVMNode[falseConditionPhiWriteNodes.size()]);
        LLVMTerminatorNode node = LLVMBranchFactory.createConditionalBranch(trueIndex, falseIndex, conditionNode, truePhiWriteNodes, falsePhiWriteNodes);

        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(ExtractElementInstruction extract) {
        LLVMExpressionNode vector = symbols.resolve(extract.getVector());
        LLVMExpressionNode index = symbols.resolve(extract.getIndex());
        LLVMBaseType resultType = LLVMBitcodeHelper.toBaseType(extract.getType()).getType();

        LLVMExpressionNode result = LLVMVectorFactory.createExtractElement(resultType, vector, index);

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(resultType, result, method.getFrame().findFrameSlot(extract.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(ExtractValueInstruction extract) {
        if (!(extract.getAggregate().getType() instanceof ArrayType || extract.getAggregate().getType() instanceof StructureType)) {
            throw new IllegalStateException("\'extractvalue\' can only extract elements of arrays and structs!");
        }
        final LLVMExpressionNode baseAddress = symbols.resolve(extract.getAggregate());
        final Type baseType = extract.getAggregate().getType();
        final int targetIndex = extract.getIndex();
        final LLVMBaseType resultType = LLVMBitcodeHelper.toBaseType(extract.getType()).getType();

        LLVMAddressNode targetAddress = (LLVMAddressNode) baseAddress;

        final AggregateType aggregateType = (AggregateType) baseType;
        int offset = 0;
        for (int i = 0; i < targetIndex; i++) {
            final Type elemType = aggregateType.getElementType(i);
            offset += LLVMBitcodeHelper.getSize(elemType, elemType.getAlignment());
        }
        if (offset != 0) {
            targetAddress = LLVMAddressGetElementPtrNodeFactory.LLVMAddressI32GetElementPtrNodeGen.create(targetAddress, new LLVMI32LiteralNode(1), offset);
        }

        final LLVMExpressionNode result = LLVMAggregateFactory.createExtractValue(resultType, targetAddress);
        final LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(resultType, result, method.getFrame().findFrameSlot(extract.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(GetElementPointerInstruction gep) {
        LLVMAddressNode baseNode = (LLVMAddressNode) symbols.resolve(gep.getBasePointer());
        LLVMAddressNode currentAddress = baseNode;

        Type type = gep.getBasePointer().getType();

        int align = 0;
        if (gep.getBasePointer() instanceof ValueSymbol) {
            align = ((ValueSymbol) gep.getBasePointer()).getAlign();
        }

        for (int i = 0; i < gep.getIndexCount(); i++) {
            int sizeof = 0;
            LLVMExpressionNode elements;

            if (type instanceof StructureType) {
                Symbol index = gep.getIndex(i);

                if (index instanceof NullConstant) {
                    type = ((StructureType) type).getElementType(0);
                    continue;
                }

                int idx = (int) ((IntegerConstant) index).getValue();
                for (int j = 0; j < idx; j++) {
                    Type t = ((StructureType) type).getElementType(j);
                    sizeof = sizeof + LLVMBitcodeHelper.getPaddingSize(t, align, sizeof) + LLVMBitcodeHelper.getSize(t, align);
                }
                type = ((StructureType) type).getElementType(idx);
                sizeof += LLVMBitcodeHelper.getPaddingSize(type, align, sizeof);

                elements = new LLVMI32LiteralNode(1);
            } else if (type instanceof ArrayType || type instanceof PointerType) {
                type = type instanceof PointerType
                                ? ((PointerType) type).getPointeeType()
                                : ((ArrayType) type).getElementType();

                sizeof = LLVMBitcodeHelper.getSize(type, align);

                Symbol index = gep.getIndex(i);
                if (index instanceof NullConstant) {
                    continue;
                }

                elements = symbols.resolve(gep.getIndex(i));
            } else {
                throw new RuntimeException("Cannot index " + type + "in GEP");
            }

            currentAddress = LLVMGetElementPtrFactory.create(
                            elements instanceof LLVMI32Node ? LLVMBaseType.I32 : LLVMBaseType.I64,
                            currentAddress,
                            elements,
                            sizeof);
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(gep.getType()).getType(), currentAddress, method.getFrame().findFrameSlot(gep.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(IndirectBranchInstruction branch) {
        int[] labelTargets = new int[branch.getSuccessorCount()];
        for (int i = 0; i < labelTargets.length; i++) {
            labelTargets[i] = method.labels().get(branch.getSuccessor(i).getName());
        }
        LLVMAddressNode value = (LLVMAddressNode) symbols.resolve(branch.getAddress());

        LLVMTerminatorNode node = LLVMBranchFactory.createIndirectBranch(value, labelTargets, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(InsertElementInstruction insert) {
        LLVMExpressionNode vector = symbols.resolve(insert.getVector());
        LLVMI32Node index = (LLVMI32Node) symbols.resolve(insert.getIndex());
        LLVMExpressionNode element = symbols.resolve(insert.getValue());
        LLVMBaseType resultType = LLVMBitcodeHelper.toBaseType(insert.getType()).getType();

        LLVMAddressNode target = LLVMAllocaInstructionNodeGen.create(LLVMBitcodeHelper.getSize(insert, 0), insert.getType().getAlignment(), method.getContext(),
                        method.getStackSlot());

        LLVMExpressionNode result = LLVMVectorFactory.createInsertElement(resultType, target, vector, element, index);

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(resultType, result, method.getFrame().findFrameSlot(insert.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(InsertValueInstruction insert) {
        if (!(insert.getAggregate().getType() instanceof StructureType || insert.getAggregate().getType() instanceof ArrayType)) {
            throw new IllegalStateException("\'insertvalue\' can only insert values into arrays and structs!");
        }
        final AggregateType sourceType = (AggregateType) insert.getAggregate().getType();
        final LLVMExpressionNode sourceAggregate = symbols.resolve(insert.getAggregate());
        final LLVMExpressionNode valueToInsert = symbols.resolve(insert.getValue());
        final LLVMBaseType valueType = LLVMBitcodeHelper.toBaseType(insert.getValue().getType()).getType();
        final int targetIndex = insert.getIndex();

        // TODO use LLVMAllocFactory instead to free the memory after return
        final LLVMExpressionNode resultAggregate = LLVMAllocaInstructionNodeGen.create(sourceType.sizeof(), sourceType.getAlignment(), method.getContext(), method.getStackSlot());
        int offset = 0;
        for (int i = 0; i < targetIndex; i++) {
            final Type elemType = sourceType.getElementType(i);
            offset += LLVMBitcodeHelper.getSize(elemType, elemType.getAlignment());
        }

        final LLVMExpressionNode result = LLVMAggregateFactory.createInsertValue((LLVMAddressNode) resultAggregate, (LLVMAddressNode) sourceAggregate,
                        LLVMBitcodeHelper.getSize(sourceType, sourceType.getAlignment()), offset, valueToInsert, valueType);

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(insert.getType()).getType(), result, method.getFrame().findFrameSlot(insert.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(LoadInstruction load) {
        LLVMAddressNode source = (LLVMAddressNode) symbols.resolve(load.getSource());
        LLVMBaseType resultType = LLVMBitcodeHelper.toBaseType(load.getType()).getType();
        LLVMExpressionNode result;

        if (load.getType() instanceof VectorType) {
            VectorType type = (VectorType) load.getType();
            result = LLVMMemoryReadWriteFactory.createLoadVector(resultType, source, type.getElementCount());
        } else {
            int bits = load.getType() instanceof IntegerType
                            ? ((IntegerType) load.getType()).getBitCount()
                            : 0;

            result = LLVMMemoryReadWriteFactory.createLoad(resultType, source, method.getOptimizationConfiguration(), bits);
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(load.getType()).getType(), result, method.getSlot(load.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(PhiInstruction pi) {
    }

    @Override
    public void visit(ReturnInstruction ret) {
        FrameSlot slot = method.getFrame().findFrameSlot(LLVMBitcodeHelper.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);

        LLVMRetNode node;
        if (ret.getValue() == null) {
            node = LLVMVoidReturnNodeGen.create(slot);
        } else {
            Type type = ret.getValue().getType();

            LLVMExpressionNode value = symbols.resolve(ret.getValue());

            slot.setKind(LLVMBitcodeHelper.toFrameSlotKind(type));

            switch (LLVMBitcodeHelper.toBaseType(type).getType()) {
                case I1:
                    node = LLVMRetNodeFactory.LLVMI1RetNodeGen.create((LLVMI1Node) value, slot);
                    break;
                case I8:
                    node = LLVMRetNodeFactory.LLVMI8RetNodeGen.create((LLVMI8Node) value, slot);
                    break;
                case I16:
                    node = LLVMRetNodeFactory.LLVMI16RetNodeGen.create((LLVMI16Node) value, slot);
                    break;
                case I32:
                    node = LLVMRetNodeFactory.LLVMI32RetNodeGen.create((LLVMI32Node) value, slot);
                    break;
                case I64:
                    node = LLVMRetNodeFactory.LLVMI64RetNodeGen.create((LLVMI64Node) value, slot);
                    break;
                case I_VAR_BITWIDTH:
                    node = LLVMRetNodeFactory.LLVMIVarBitRetNodeGen.create((LLVMIVarBitNode) value, slot);
                    break;
                case FLOAT:
                    node = LLVMRetNodeFactory.LLVMFloatRetNodeGen.create((LLVMFloatNode) value, slot);
                    break;
                case DOUBLE:
                    node = LLVMRetNodeFactory.LLVMDoubleRetNodeGen.create((LLVMDoubleNode) value, slot);
                    break;
                case X86_FP80:
                    node = LLVMRetNodeFactory.LLVM80BitFloatRetNodeGen.create((LLVM80BitFloatNode) value, slot);
                    break;
                case ADDRESS:
                    node = LLVMRetNodeFactory.LLVMAddressRetNodeGen.create((LLVMAddressNode) value, slot);
                    break;
                case FUNCTION_ADDRESS:
                    node = LLVMRetNodeFactory.LLVMFunctionRetNodeGen.create((LLVMFunctionNode) value, slot);
                    break;
                case STRUCT:
                    // final int size = LLVMBitcodeHelper.getSize(type, type.getAlignment());
                    // node = LLVMRetNodeFactory.LLVMStructRetNodeGen.create((LLVMAddressNode)
                    // value, slot, size);
                    // break;
                default:
                    throw new UnsupportedOperationException("Unsupported Return Type: " + type);
            }
        }

        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(SelectInstruction select) {
        LLVMExpressionNode condition = symbols.resolve(select.getCondition());
        LLVMExpressionNode trueValue = symbols.resolve(select.getTrueValue());
        LLVMExpressionNode falseValue = symbols.resolve(select.getFalseValue());
        LLVMBaseType llvmType = LLVMBitcodeHelper.toBaseType(select.getType()).getType();

        LLVMExpressionNode result;
        if (select.getType() instanceof VectorType) {
            VectorType type = (VectorType) select.getType();
            LLVMAddressNode target = LLVMAllocaInstructionNodeGen.create(LLVMBitcodeHelper.getSize(type, 0), type.getAlignment(), method.getContext(), method.getStackSlot());

            result = LLVMSelectFactory.createSelectVector(llvmType, target, condition, trueValue, falseValue);
        } else {
            result = LLVMSelectFactory.createSelect(llvmType, (LLVMI1Node) condition, trueValue, falseValue);
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(llvmType, result, method.getSlot(select.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(ShuffleVectorInstruction shuffle) {
        LLVMExpressionNode vector1 = symbols.resolve(shuffle.getVector1());
        LLVMExpressionNode vector2 = symbols.resolve(shuffle.getVector2());
        LLVMI32VectorNode mask = (LLVMI32VectorNode) symbols.resolve(shuffle.getMask());

        LLVMBaseType type = LLVMBitcodeHelper.toBaseType(shuffle.getType()).getType();

        LLVMAddressNode destination = LLVMAllocaInstructionNodeGen.create(LLVMBitcodeHelper.getSize(shuffle, 0), shuffle.getType().getAlignment(), method.getContext(),
                        method.getStackSlot());

        LLVMExpressionNode result = LLVMVectorFactory.createShuffleVector(type, destination, vector1, vector2, mask);

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(type, result, method.getSlot(shuffle.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(StoreInstruction store) {
        LLVMAddressNode pointerNode = (LLVMAddressNode) symbols.resolve(store.getDestination());
        LLVMExpressionNode valueNode = symbols.resolve(store.getSource());

        Type type = store.getSource().getType();

        LLVMNode node = LLVMMemoryReadWriteFactory.createStore(pointerNode, valueNode, LLVMBitcodeHelper.toBaseType(type).getType(),
                        LLVMBitcodeHelper.getSize(type, store.getAlign()));

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
        LLVMBaseType llvmType = LLVMBitcodeHelper.toBaseType(zwitch.getCondition().getType()).getType();

        LLVMTerminatorNode node = LLVMSwitchFactory.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, getPhiWriteNodes());
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
        LLVMBaseType llvmType = LLVMBitcodeHelper.toBaseType(zwitch.getCondition().getType()).getType();
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

        LLVMTerminatorNode node = LLVMSwitchFactory.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(UnreachableInstruction ui) {
        method.addTerminatingInstruction(new LLVMUnreachableNode(), block.getBlockIndex(), block.getName());
    }

    @Override
    public void visit(VoidCallInstruction call) {
        Symbol target = call.getCallTarget();

        int argumentCount = call.getArgumentCount();

        LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount + 1];

        args[0] = LLVMFrameReadWriteFactory.createFrameRead(LLVMBaseType.ADDRESS, method.getStackSlot());

        for (int i = 0; i < argumentCount; i++) {
            args[i + 1] = symbols.resolve(call.getArgument(i));
        }

        LLVMNode node;

        if (target instanceof FunctionDeclaration && (((ValueSymbol) target).getName()).startsWith("@llvm.")) {
            node = LLVMIntrinsicFactory.create(((ValueSymbol) target).getName(), args, call.getCallType().getArgumentTypes().length, method.getStackSlot(), method.getOptimizationConfiguration());
        } else {
            LLVMFunctionNode function = (LLVMFunctionNode) symbols.resolve(target);
            node = LLVMFunctionFactory.createFunctionCall(function, args, LLVMBitcodeHelper.toBaseType(call.getType()).getType());
        }

        method.addInstruction(node);
    }
}
