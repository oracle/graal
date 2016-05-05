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
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMAddressLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMDoubleLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMFloatLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI16LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI1LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI64LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI8LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory.LLVMI32AllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory.LLVMI64AllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.factories.LLVMArithmeticFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMBranchFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMCastsFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFrameReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFunctionFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMGetElementPtrFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMIntrinsicFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLiteralFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLogicalFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMMemoryReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMSelectFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMSwitchFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMVectorFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.LLVMIVarBit;

import uk.ac.man.cs.llvm.ir.model.InstructionBlock;
import uk.ac.man.cs.llvm.ir.model.FunctionDeclaration;
import uk.ac.man.cs.llvm.ir.model.FunctionDefinition;
import uk.ac.man.cs.llvm.ir.model.FunctionParameter;
import uk.ac.man.cs.llvm.ir.model.GlobalValueSymbol;
import uk.ac.man.cs.llvm.ir.model.InstructionVisitor;
import uk.ac.man.cs.llvm.ir.model.Symbol;
import uk.ac.man.cs.llvm.ir.model.ValueSymbol;
import uk.ac.man.cs.llvm.ir.model.constants.BinaryOperationConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BlockAddressConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CastConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CompareConstant;
import uk.ac.man.cs.llvm.ir.model.constants.FloatingPointConstant;
import uk.ac.man.cs.llvm.ir.model.constants.GetElementPointerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.IntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.NullConstant;
import uk.ac.man.cs.llvm.ir.model.constants.UndefinedConstant;
import uk.ac.man.cs.llvm.ir.model.constants.VectorConstant;
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
import uk.ac.man.cs.llvm.ir.model.elements.ValueInstruction;
import uk.ac.man.cs.llvm.ir.model.elements.VoidCallInstruction;
import uk.ac.man.cs.llvm.ir.types.ArrayType;
import uk.ac.man.cs.llvm.ir.types.FloatingPointType;
import uk.ac.man.cs.llvm.ir.types.FunctionType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.StructureType;
import uk.ac.man.cs.llvm.ir.types.Type;
import uk.ac.man.cs.llvm.ir.types.VectorType;

public final class LLVMBitcodeInstructionVisitor implements InstructionVisitor {

    private final LLVMBitcodeFunctionVisitor method;

    private final InstructionBlock block;

    public LLVMBitcodeInstructionVisitor(LLVMBitcodeFunctionVisitor method, InstructionBlock block) {
        this.method = method;
        this.block = block;
    }

    private LLVMNode[] getPhiWriteNodes() {
        List<LLVMNode> nodes = new ArrayList<>();
        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            for (Phi phi : phis) {
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = resolve(phi.getValue());
                LLVMBaseType baseType = LLVMBitcodeHelper.toBaseType(phi.getValue().getType());
                LLVMNode phiWriteNode = LLVMFrameReadWriteFactory.createFrameWrite(baseType, value, slot);
                nodes.add(phiWriteNode);
            }
        }
        return nodes.toArray(new LLVMNode[nodes.size()]);
    }

    private LLVMExpressionNode resolve(Symbol symbol) {
        if (symbol instanceof ValueInstruction || symbol instanceof FunctionParameter) {
            FrameSlot slot = method.getFrame().findFrameSlot(((ValueSymbol) symbol).getName());
            return LLVMFrameReadWriteFactory.createFrameRead(LLVMBitcodeHelper.toBaseType(symbol.getType()), slot);
        } else if (symbol instanceof GlobalValueSymbol) {
            return method.global((GlobalValueSymbol) symbol);
        } else if (symbol instanceof FunctionDefinition || symbol instanceof FunctionDeclaration) {
            String name = ((ValueSymbol) symbol).getName();
            FunctionType type = (FunctionType) symbol;

            LLVMRuntimeType returnType = LLVMBitcodeHelper.toRuntimeType(type.getReturnType());
            LLVMRuntimeType[] argTypes = LLVMBitcodeHelper.toRuntimeTypes(type.getArgumentTypes());

            return LLVMFunctionLiteralNodeGen.create(method.getContext().getFunctionRegistry().createFunctionDescriptor(name, returnType, argTypes, type.isVarArg()));
        } else {
            if (symbol instanceof BinaryOperationConstant) {
                BinaryOperationConstant operation = (BinaryOperationConstant) symbol;
                LLVMExpressionNode lhs = resolve(operation.getLHS());
                LLVMExpressionNode rhs = resolve(operation.getRHS());
                LLVMBaseType type = LLVMBitcodeHelper.toBaseType(operation.getType());

                return LLVMBitcodeHelper.toBinaryOperatorNode(operation.getOperator(), type, lhs, rhs);
            }
            if (symbol instanceof BlockAddressConstant) {
                BlockAddressConstant blockaddr = (BlockAddressConstant) symbol;
                // resolve(blockaddr.getMethod());
                //
                // if (isGlobalScope) {
                // Map<String, Integer> functionBlocks = functionToLabelMapping.get(function);
                // val = functionBlocks.get(basicBlock.getName());
                // } else {

                int val = method.labels().get(((ValueSymbol) blockaddr.getBlock()).getName());
                return new LLVMAddressLiteralNode(LLVMAddress.fromLong(val));
            }
            if (symbol instanceof CastConstant) {
                CastConstant cast = (CastConstant) symbol;
                LLVMConversionType type = LLVMBitcodeHelper.toConversionType(cast.getOperator());
                LLVMExpressionNode fromNode = resolve(cast.getValue());
                LLVMBaseType from = LLVMBitcodeHelper.toBaseType(cast.getValue().getType());
                LLVMBaseType to = LLVMBitcodeHelper.toBaseType(cast.getType());

                return LLVMCastsFactory.cast(fromNode, to, from, type);
            }
            if (symbol instanceof CompareConstant) {
                CompareConstant compare = (CompareConstant) symbol;
                LLVMExpressionNode lhs = resolve(compare.getLHS());
                LLVMExpressionNode rhs = resolve(compare.getRHS());

                return LLVMBitcodeHelper.toCompareNode(compare.getOperator(), compare.getLHS().getType(), lhs, rhs);
            }
            if (symbol instanceof GetElementPointerConstant) {
                GetElementPointerConstant ptr = (GetElementPointerConstant) symbol;

                LLVMAddressNode baseNode = (LLVMAddressNode) resolve(ptr.getBasePointer());
                LLVMAddressNode currentAddress = baseNode;

                Type type = ptr.getBasePointer().getType();
                int align = 0;
                if (ptr.getBasePointer() instanceof ValueSymbol) {
                    align = ((ValueSymbol) ptr.getBasePointer()).getAlign();
                } else if (ptr.getBasePointer() instanceof CastConstant) {
                    align = ((ValueSymbol) ((CastConstant) ptr.getBasePointer()).getValue()).getAlign();
                }

                for (int i = 0; i < ptr.getIndexCount(); i++) {
                    Symbol index = ptr.getIndex(i);
                    int idx = index instanceof NullConstant ? 0 : (int) ((IntegerConstant) index).getValue();

                    if (type instanceof ArrayType) {
                        type = ((ArrayType) type).getElementType();
                    } else if (type instanceof PointerType) {
                        type = ((PointerType) type).getPointeeType();
                    } else {
                        int offset = 0;
                        for (int j = 0; j < idx; j++) {
                            Type t = ((StructureType) type).getElementType(j);
                            offset = offset + LLVMBitcodeHelper.getPaddingSize(t, align, offset) + LLVMBitcodeHelper.getSize(t, align);
                        }
                        type = ((StructureType) type).getElementType(idx);
                        offset += LLVMBitcodeHelper.getPaddingSize(type, align, offset);
                        if (offset != 0) {
                            currentAddress = LLVMGetElementPtrFactory.create(
                                            LLVMBaseType.I32,
                                            currentAddress,
                                            new LLVMI32LiteralNode(1),
                                            offset);
                        }
                        continue;
                    }

                    if (idx != 0) {
                        currentAddress = LLVMGetElementPtrFactory.create(
                                        LLVMBaseType.I32,
                                        currentAddress,
                                        new LLVMI32LiteralNode(idx),
                                        LLVMBitcodeHelper.getSize(type, align));
                    }
                }

                return currentAddress;
            }
            if (symbol instanceof IntegerConstant) {
                IntegerConstant constant = (IntegerConstant) symbol;
                int bits = ((IntegerType) constant.getType()).getBitCount();
                switch (bits) {
                    case 1:
                        return new LLVMI1LiteralNode(constant.getValue() != 0);
                    case Byte.SIZE:
                        return new LLVMI8LiteralNode((byte) constant.getValue());
                    case Short.SIZE:
                        return new LLVMI16LiteralNode((short) constant.getValue());
                    case Integer.SIZE:
                        return new LLVMI32LiteralNode((int) constant.getValue());
                    case Long.SIZE:
                        return new LLVMI64LiteralNode(constant.getValue());
                    default:
                        return new LLVMIVarBitLiteralNode(LLVMIVarBit.fromLong(bits, constant.getValue()));
                }
            }
            if (symbol instanceof FloatingPointConstant) {
                FloatingPointConstant constant = (FloatingPointConstant) symbol;
                switch ((FloatingPointType) constant.getType()) {
                    case FLOAT:
                        return new LLVMFloatLiteralNode(constant.toFloat());
                    case DOUBLE:
                        return new LLVMDoubleLiteralNode(constant.toDouble());
                    default:
                        break;
                }
            }
            if (symbol instanceof NullConstant || symbol instanceof UndefinedConstant) {
                return LLVMBitcodeHelper.toConstantZeroNode(symbol.getType(), symbol.getType().getAlignment(), method.getContext(), method.getStackSlot());
            }
            if (symbol instanceof VectorConstant) {
                VectorConstant vector = (VectorConstant) symbol;
                List<LLVMExpressionNode> values = new ArrayList<>();
                for (int i = 0; i < vector.getLength(); i++) {
                    values.add(resolve(vector.getElement(i)));
                }
                LLVMAddressNode target = LLVMAllocaInstructionNodeGen.create(LLVMBitcodeHelper.getSize(vector, 0), LLVMBitcodeHelper.getAlignment(vector, 0), method.getContext(),
                                method.getStackSlot());
                return LLVMLiteralFactory.createVectorLiteralNode(values, target, LLVMBitcodeHelper.toBaseType(vector.getType()));
            }
        }
        return null;
    }

    @Override
    public void visit(AllocateInstruction allocate) {
        Type type = allocate.getPointeeType();
        int align = allocate.getAlign();

        Symbol count = allocate.getCount();

        int size = LLVMBitcodeHelper.getSize(type, align);
        int alignment = LLVMBitcodeHelper.getAlignment(type, align);

        LLVMExpressionNode result;
        if (count instanceof IntegerConstant) {
            result = LLVMAllocaInstructionNodeGen.create(
                            size * (int) ((IntegerConstant) count).getValue(),
                            alignment,
                            method.getContext(),
                            method.getStackSlot());
        } else {
            LLVMExpressionNode num = resolve(count);
            switch (LLVMBitcodeHelper.toBaseType(count.getType())) {
                case I32:
                    result = LLVMI32AllocaInstructionNodeGen.create((LLVMI32Node) num, size, alignment, method.getContext(), method.getStackSlot());
                    break;
                case I64:
                    result = LLVMI64AllocaInstructionNodeGen.create((LLVMI64Node) num, size, alignment, method.getContext(), method.getStackSlot());
                    break;
                default:
                    throw new AssertionError("Unsupported element type in alloca");
            }
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(allocate.getType()), result, method.getFrame().findFrameSlot(allocate.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(BinaryOperationInstruction operation) {
        LLVMExpressionNode lhs = resolve(operation.getLHS());
        LLVMExpressionNode rhs = resolve(operation.getRHS());

        LLVMAddressNode target = null;
        if (operation.getType() instanceof VectorType) {
            target = LLVMAllocaInstructionNodeGen.create(LLVMBitcodeHelper.getSize(operation, 0), operation.getType().getAlignment(), method.getContext(), method.getStackSlot());
        }

        LLVMBaseType type = LLVMBitcodeHelper.toBaseType(operation.getType());
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
        method.addTerminatingInstruction(LLVMBranchFactory.createUnconditionalBranch(method.labels().get(branch.getSuccessor().getName()), getPhiWriteNodes()), block.getIndex());
    }

    @Override
    public void visit(CallInstruction call) {
        Symbol target = call.getCallTarget();

        int argumentCount = call.getArgumentCount();

        LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount + 1];

        args[0] = LLVMFrameReadWriteFactory.createFrameRead(LLVMBaseType.ADDRESS, method.getStackSlot());

        for (int i = 0; i < argumentCount; i++) {
            args[i + 1] = resolve(call.getArgument(i));
        }

        LLVMExpressionNode result;

        if (target instanceof FunctionDeclaration && (((ValueSymbol) target).getName()).startsWith("@llvm.")) {
            result = (LLVMExpressionNode) LLVMIntrinsicFactory.create(((ValueSymbol) target).getName(), args, call.getCallType().getArgumentTypes().length, method.getStackSlot(),
                            method.getOptimizationConfiguration());
        } else {
            LLVMFunctionNode function = (LLVMFunctionNode) resolve(target);
            result = (LLVMExpressionNode) LLVMFunctionFactory.createFunctionCall(function, args, LLVMBitcodeHelper.toBaseType(call.getType()));
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(call.getType()), result, method.getFrame().findFrameSlot(call.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(CastInstruction cast) {
        LLVMConversionType type = LLVMBitcodeHelper.toConversionType(cast.getOperator());
        LLVMExpressionNode fromNode = resolve(cast.getValue());
        LLVMBaseType from = LLVMBitcodeHelper.toBaseType(cast.getValue().getType());
        LLVMBaseType to = LLVMBitcodeHelper.toBaseType(cast.getType());

        int bits = 0;
        if (cast.getType() instanceof IntegerType) {
            bits = ((IntegerType) cast.getType()).getBitCount();
        }

        LLVMExpressionNode result = LLVMCastsFactory.cast(fromNode, to, from, type, bits);

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(cast.getType()), result, method.getFrame().findFrameSlot(cast.getName()));
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
                            resolve(compare.getLHS()),
                            resolve(compare.getRHS()));
        } else {
            result = LLVMBitcodeHelper.toCompareNode(
                            compare.getOperator(),
                            compare.getLHS().getType(),
                            resolve(compare.getLHS()),
                            resolve(compare.getRHS()));
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(compare.getType()), result, method.getFrame().findFrameSlot(compare.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(ConditionalBranchInstruction branch) {
        LLVMExpressionNode conditionNode = resolve(branch.getCondition());
        int trueIndex = method.labels().get(branch.getTrueSuccessor().getName());
        int falseIndex = method.labels().get(branch.getFalseSuccessor().getName());

        List<LLVMNode> trueConditionPhiWriteNodes = new ArrayList<>();
        List<LLVMNode> falseConditionPhiWriteNodes = new ArrayList<>();

        List<Phi> phis = method.getPhiManager().get(block);
        if (phis != null) {
            for (Phi phi : phis) {
                FrameSlot slot = method.getSlot(phi.getPhiValue().getName());
                LLVMExpressionNode value = resolve(phi.getValue());
                LLVMBaseType baseType = LLVMBitcodeHelper.toBaseType(phi.getValue().getType());
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

        method.addTerminatingInstruction(node, block.getIndex());
    }

    @Override
    public void visit(ExtractElementInstruction extract) {
        LLVMExpressionNode vector = resolve(extract.getVector());
        LLVMExpressionNode index = resolve(extract.getIndex());
        LLVMBaseType resultType = LLVMBitcodeHelper.toBaseType(extract.getType());

        LLVMExpressionNode result = LLVMVectorFactory.createExtractElement(resultType, vector, index);

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(resultType, result, method.getFrame().findFrameSlot(extract.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(ExtractValueInstruction extract) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void visit(GetElementPointerInstruction gep) {
        LLVMAddressNode baseNode = (LLVMAddressNode) resolve(gep.getBasePointer());
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
                int idx = index instanceof NullConstant ? 0 : (int) ((IntegerConstant) index).getValue();
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
                elements = resolve(gep.getIndex(i));
            } else {
                throw new RuntimeException("Cannot index " + type + "in GEP");
            }

            currentAddress = LLVMGetElementPtrFactory.create(
                            elements instanceof LLVMI32Node ? LLVMBaseType.I32 : LLVMBaseType.I64,
                            currentAddress,
                            elements,
                            sizeof);
        }

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(gep.getType()), currentAddress, method.getFrame().findFrameSlot(gep.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(IndirectBranchInstruction branch) {
        int[] labelTargets = new int[branch.getSuccessorCount()];
        for (int i = 0; i < labelTargets.length; i++) {
            labelTargets[i] = method.labels().get(branch.getSuccessor(i).getName());
        }
        LLVMAddressNode value = (LLVMAddressNode) resolve(branch.getAddress());

        LLVMTerminatorNode node = LLVMBranchFactory.createIndirectBranch(value, labelTargets, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getIndex());
    }

    @Override
    public void visit(InsertElementInstruction insert) {
        LLVMExpressionNode vector = resolve(insert.getVector());
        LLVMI32Node index = (LLVMI32Node) resolve(insert.getIndex());
        LLVMExpressionNode element = resolve(insert.getValue());
        LLVMBaseType resultType = LLVMBitcodeHelper.toBaseType(insert.getType());

        LLVMAddressNode target = LLVMAllocaInstructionNodeGen.create(LLVMBitcodeHelper.getSize(insert, 0), insert.getType().getAlignment(), method.getContext(),
                        method.getStackSlot());

        LLVMExpressionNode result = LLVMVectorFactory.createInsertElement(resultType, target, vector, element, index);

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(resultType, result, method.getFrame().findFrameSlot(insert.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(InsertValueInstruction insert) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void visit(LoadInstruction load) {
        LLVMAddressNode source = (LLVMAddressNode) resolve(load.getSource());
        LLVMBaseType resultType = LLVMBitcodeHelper.toBaseType(load.getType());
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

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(LLVMBitcodeHelper.toBaseType(load.getType()), result, method.getSlot(load.getName()));
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

            LLVMExpressionNode value = resolve(ret.getValue());

            slot.setKind(LLVMBitcodeHelper.toFrameSlotKind(type));

            switch (LLVMBitcodeHelper.toBaseType(type)) {
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
                    // ResolvedStructType structType = (ResolvedStructType) resolvedType;
                    // int size = LLVMTypeHelper.getByteSize(structType);
                    // return LLVMRetNodeFactory.LLVMStructRetNodeGen.create((LLVMAddressNode)
                    // value, retSlot, size);
                default:
                    // if (LLVMTypeHelper.isVectorType(type)) {
                    // return LLVMRetNodeFactory.LLVMVectorRetNodeGen.create((LLVMVectorNode)
                    // retValue, retSlot);
                    // } else
                    throw new AssertionError(type);
            }
        }

        method.addTerminatingInstruction(node, block.getIndex());
    }

    @Override
    public void visit(SelectInstruction select) {
        LLVMExpressionNode condition = resolve(select.getCondition());
        LLVMExpressionNode trueValue = resolve(select.getTrueValue());
        LLVMExpressionNode falseValue = resolve(select.getFalseValue());
        LLVMBaseType llvmType = LLVMBitcodeHelper.toBaseType(select.getType());

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
        LLVMExpressionNode vector1 = resolve(shuffle.getVector1());
        LLVMExpressionNode vector2 = resolve(shuffle.getVector2());
        LLVMI32VectorNode mask = (LLVMI32VectorNode) resolve(shuffle.getMask());

        LLVMBaseType type = LLVMBitcodeHelper.toBaseType(shuffle.getType());

        LLVMAddressNode destination = LLVMAllocaInstructionNodeGen.create(LLVMBitcodeHelper.getSize(shuffle, 0), shuffle.getType().getAlignment(), method.getContext(),
                        method.getStackSlot());

        LLVMExpressionNode result = LLVMVectorFactory.createShuffleVector(type, destination, vector1, vector2, mask);

        LLVMNode node = LLVMFrameReadWriteFactory.createFrameWrite(type, result, method.getSlot(shuffle.getName()));
        method.addInstruction(node);
    }

    @Override
    public void visit(StoreInstruction store) {
        LLVMAddressNode pointerNode = (LLVMAddressNode) resolve(store.getDestination());
        LLVMExpressionNode valueNode = resolve(store.getSource());

        Type type = store.getSource().getType();

        LLVMNode node = LLVMMemoryReadWriteFactory.createStore(pointerNode, valueNode, LLVMBitcodeHelper.toBaseType(type), LLVMBitcodeHelper.getSize(type, store.getAlign()));

        method.addInstruction(node);
    }

    @Override
    public void visit(SwitchInstruction zwitch) {
        LLVMExpressionNode cond = resolve(zwitch.getCondition());
        int defaultLabel = method.labels().get(zwitch.getDefaultBlock().getName());
        int[] otherLabels = new int[zwitch.getCaseCount()];
        for (int i = 0; i < otherLabels.length; i++) {
            otherLabels[i] = method.labels().get(zwitch.getCaseBlock(i).getName());
        }
        LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            cases[i] = resolve(zwitch.getCaseValue(i));
        }
        LLVMBaseType llvmType = LLVMBitcodeHelper.toBaseType(zwitch.getCondition().getType());

        LLVMTerminatorNode node = LLVMSwitchFactory.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getIndex());
    }

    @Override
    public void visit(SwitchOldInstruction zwitch) {
        LLVMExpressionNode cond = resolve(zwitch.getCondition());
        int defaultLabel = method.labels().get(zwitch.getDefaultBlock().getName());
        int[] otherLabels = new int[zwitch.getCaseCount()];
        for (int i = 0; i < otherLabels.length; i++) {
            otherLabels[i] = method.labels().get(zwitch.getCaseBlock(i).getName());
        }
        LLVMBaseType llvmType = LLVMBitcodeHelper.toBaseType(zwitch.getCondition().getType());
        LLVMExpressionNode[] cases = new LLVMExpressionNode[zwitch.getCaseCount()];
        for (int i = 0; i < cases.length; i++) {
            if (llvmType == LLVMBaseType.I32) {
                cases[i] = new LLVMI32LiteralNode((int) zwitch.getCaseValue(i));
            } else {
                cases[i] = new LLVMI64LiteralNode(zwitch.getCaseValue(i));
            }
        }

        LLVMTerminatorNode node = LLVMSwitchFactory.createSwitch(cond, defaultLabel, otherLabels, cases, llvmType, getPhiWriteNodes());
        method.addTerminatingInstruction(node, block.getIndex());
    }

    @Override
    public void visit(UnreachableInstruction ui) {
        method.addTerminatingInstruction(new LLVMUnreachableNode(), block.getIndex());
    }

    @Override
    public void visit(VoidCallInstruction call) {
        Symbol target = call.getCallTarget();

        int argumentCount = call.getArgumentCount();

        LLVMExpressionNode[] args = new LLVMExpressionNode[argumentCount + 1];

        args[0] = LLVMFrameReadWriteFactory.createFrameRead(LLVMBaseType.ADDRESS, method.getStackSlot());

        for (int i = 0; i < argumentCount; i++) {
            args[i + 1] = resolve(call.getArgument(i));
        }

        LLVMNode node;

        if (target instanceof FunctionDeclaration && (((ValueSymbol) target).getName()).startsWith("@llvm.")) {
            node = LLVMIntrinsicFactory.create(((ValueSymbol) target).getName(), args, call.getCallType().getArgumentTypes().length, method.getStackSlot(), method.getOptimizationConfiguration());
        } else {
            LLVMFunctionNode function = (LLVMFunctionNode) resolve(target);
            node = LLVMFunctionFactory.createFunctionCall(function, args, LLVMBitcodeHelper.toBaseType(call.getType()));
        }

        method.addInstruction(node);
    }
}
