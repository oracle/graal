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
package com.oracle.truffle.llvm.parser.bc.impl.nodes;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.asm.amd64.Parser;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMBitcodeFunctionVisitor;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMBitcodeHelper;
import com.oracle.truffle.llvm.parser.factories.LLVMCastsFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFrameReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMGetElementPtrFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLiteralFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import uk.ac.man.cs.llvm.ir.model.FunctionDeclaration;
import uk.ac.man.cs.llvm.ir.model.FunctionDefinition;
import uk.ac.man.cs.llvm.ir.model.FunctionParameter;
import uk.ac.man.cs.llvm.ir.model.GlobalValueSymbol;
import uk.ac.man.cs.llvm.ir.model.Symbol;
import uk.ac.man.cs.llvm.ir.model.ValueSymbol;
import uk.ac.man.cs.llvm.ir.model.constants.BigIntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BinaryOperationConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BlockAddressConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CastConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CompareConstant;
import uk.ac.man.cs.llvm.ir.model.constants.FloatingPointConstant;
import uk.ac.man.cs.llvm.ir.model.constants.GetElementPointerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.InlineAsmConstant;
import uk.ac.man.cs.llvm.ir.model.constants.IntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.NullConstant;
import uk.ac.man.cs.llvm.ir.model.constants.UndefinedConstant;
import uk.ac.man.cs.llvm.ir.model.constants.VectorConstant;
import uk.ac.man.cs.llvm.ir.model.elements.ValueInstruction;
import uk.ac.man.cs.llvm.ir.types.ArrayType;
import uk.ac.man.cs.llvm.ir.types.FloatingPointType;
import uk.ac.man.cs.llvm.ir.types.FunctionType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.StructureType;
import uk.ac.man.cs.llvm.ir.types.Type;

import java.util.ArrayList;
import java.util.List;

public final class LLVMNodeGenerator {

    private final LLVMBitcodeFunctionVisitor method;

    public LLVMNodeGenerator(LLVMBitcodeFunctionVisitor method) {
        this.method = method;
    }

    public LLVMExpressionNode resolve(Symbol symbol) {
        if (symbol instanceof ValueInstruction || symbol instanceof FunctionParameter) {
            final FrameSlot slot = method.getFrame().findFrameSlot(((ValueSymbol) symbol).getName());
            return LLVMFrameReadWriteFactory.createFrameRead(LLVMBitcodeHelper.toBaseType(symbol.getType()).getType(), slot);

        } else if (symbol instanceof GlobalValueSymbol) {
            return method.global((GlobalValueSymbol) symbol);

        } else if (symbol instanceof FunctionDefinition || symbol instanceof FunctionDeclaration) {
            return resolveFunction(((ValueSymbol) symbol).getName(), (FunctionType) symbol);

        } else if (symbol instanceof BinaryOperationConstant) {
            return resolveBinaryOperationConstant((BinaryOperationConstant) symbol);

        } else if (symbol instanceof BlockAddressConstant) {
            return resolveBlockAddressConstant((BlockAddressConstant) symbol);

        } else if (symbol instanceof CastConstant) {
            return resolveCastConstant((CastConstant) symbol);

        } else if (symbol instanceof CompareConstant) {
            return resolveCompareConstant((CompareConstant) symbol);

        } else if (symbol instanceof GetElementPointerConstant) {
            return resolveGetElementPointerConstant((GetElementPointerConstant) symbol);

        } else if (symbol instanceof IntegerConstant) {
            return resolveIntegerConstant((IntegerConstant) symbol);

        } else if (symbol instanceof BigIntegerConstant) {
            return resolveBigIntegerConstant((BigIntegerConstant) symbol);

        } else if (symbol instanceof FloatingPointConstant) {
            return resolveFloatingPointConstant((FloatingPointConstant) symbol);

        } else if (symbol instanceof NullConstant || symbol instanceof UndefinedConstant) {
            return LLVMBitcodeHelper.toConstantZeroNode(symbol.getType(), symbol.getType().getAlignment(), method.getContext(), method.getStackSlot());

        } else if (symbol instanceof VectorConstant) {
            return resolveVectorConstant((VectorConstant) symbol);

        } else {
            throw new AssertionError("Cannot resolve symbol: " + symbol);
        }
    }

    private static LLVMExpressionNode resolveBigIntegerConstant(BigIntegerConstant constant) {
        final int bits = ((IntegerType) constant.getType()).getBitCount();
        return new LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode(LLVMIVarBit.create(bits, constant.getValue().toByteArray()));
    }

    private LLVMExpressionNode resolveBinaryOperationConstant(BinaryOperationConstant constant) {
        final LLVMExpressionNode lhs = resolve(constant.getLHS());
        final LLVMExpressionNode rhs = resolve(constant.getRHS());
        final LLVMBaseType type = LLVMBitcodeHelper.toBaseType(constant.getType()).getType();
        return LLVMBitcodeHelper.toBinaryOperatorNode(constant.getOperator(), type, lhs, rhs);
    }

    private LLVMExpressionNode resolveBlockAddressConstant(BlockAddressConstant constant) {
        final int val = method.labels().get(constant.getInstructionBlock().getName());
        return new LLVMSimpleLiteralNode.LLVMAddressLiteralNode(LLVMAddress.fromLong(val));
    }

    private LLVMExpressionNode resolveCastConstant(CastConstant constant) {
        final LLVMConversionType type = LLVMBitcodeHelper.toConversionType(constant.getOperator());
        final LLVMExpressionNode fromNode = resolve(constant.getValue());
        final LLVMBaseType from = LLVMBitcodeHelper.toBaseType(constant.getValue().getType()).getType();
        final LLVMBaseType to = LLVMBitcodeHelper.toBaseType(constant.getType()).getType();
        return LLVMCastsFactory.cast(fromNode, to, from, type);
    }

    private LLVMExpressionNode resolveCompareConstant(CompareConstant constant) {
        final LLVMExpressionNode lhs = resolve(constant.getLHS());
        final LLVMExpressionNode rhs = resolve(constant.getRHS());
        return LLVMBitcodeHelper.toCompareNode(constant.getOperator(), constant.getLHS().getType(), lhs, rhs);
    }

    private static LLVMExpressionNode resolveFloatingPointConstant(FloatingPointConstant constant) {
        switch ((FloatingPointType) constant.getType()) {
            case FLOAT:
                return new LLVMSimpleLiteralNode.LLVMFloatLiteralNode(constant.toFloat());
            case DOUBLE:
                return new LLVMSimpleLiteralNode.LLVMDoubleLiteralNode(constant.toDouble());
            default:
                throw new AssertionError("Unsupported Type for FloatingPointConstant: " + constant.getType());
        }
    }

    private LLVMExpressionNode resolveFunction(String name, FunctionType type) {
        final LLVMFunctionDescriptor.LLVMRuntimeType returnType = LLVMBitcodeHelper.toRuntimeType(type.getReturnType());
        final LLVMFunctionDescriptor.LLVMRuntimeType[] argTypes = LLVMBitcodeHelper.toRuntimeTypes(type.getArgumentTypes());
        return LLVMFunctionLiteralNodeGen.create(method.getContext().getFunctionRegistry().createFunctionDescriptor(name, returnType, argTypes, type.isVarArg()));
    }

    private LLVMExpressionNode resolveGetElementPointerConstant(GetElementPointerConstant constant) {
        LLVMAddressNode currentAddress = (LLVMAddressNode) resolve(constant.getBasePointer());

        Type type = constant.getBasePointer().getType();
        int align = 0;
        if (constant.getBasePointer() instanceof ValueSymbol) {
            align = ((ValueSymbol) constant.getBasePointer()).getAlign();
        } else if (constant.getBasePointer() instanceof CastConstant) {
            align = ((ValueSymbol) ((CastConstant) constant.getBasePointer()).getValue()).getAlign();
        }

        for (int i = 0; i < constant.getIndexCount(); i++) {
            final Symbol index = constant.getIndex(i);
            int idx = index instanceof NullConstant ? 0 : (int) ((IntegerConstant) index).getValue();

            if (type instanceof ArrayType) {
                type = ((ArrayType) type).getElementType();
            } else if (type instanceof PointerType) {
                type = ((PointerType) type).getPointeeType();
            } else {
                int offset = 0;
                for (int j = 0; j < idx; j++) {
                    final Type t = ((StructureType) type).getElementType(j);
                    offset = offset + LLVMBitcodeHelper.getPaddingSize(t, align, offset) + LLVMBitcodeHelper.getSize(t, align);
                }
                type = ((StructureType) type).getElementType(idx);
                offset += LLVMBitcodeHelper.getPaddingSize(type, align, offset);
                if (offset != 0) {
                    currentAddress = LLVMGetElementPtrFactory.create(
                                    LLVMBaseType.I32,
                                    currentAddress,
                                    new LLVMSimpleLiteralNode.LLVMI32LiteralNode(1),
                                    offset);
                }
                continue;
            }

            if (idx != 0) {
                currentAddress = LLVMGetElementPtrFactory.create(
                                LLVMBaseType.I32,
                                currentAddress,
                                new LLVMSimpleLiteralNode.LLVMI32LiteralNode(idx),
                                LLVMBitcodeHelper.getSize(type, align));
            }
        }

        return currentAddress;
    }

    private static LLVMExpressionNode resolveIntegerConstant(IntegerConstant constant) {
        final int bits = ((IntegerType) constant.getType()).getBitCount();
        switch (bits) {
            case 1:
                return new LLVMSimpleLiteralNode.LLVMI1LiteralNode(constant.getValue() != 0);
            case Byte.SIZE:
                return new LLVMSimpleLiteralNode.LLVMI8LiteralNode((byte) constant.getValue());
            case Short.SIZE:
                return new LLVMSimpleLiteralNode.LLVMI16LiteralNode((short) constant.getValue());
            case Integer.SIZE:
                return new LLVMSimpleLiteralNode.LLVMI32LiteralNode((int) constant.getValue());
            case Long.SIZE:
                return new LLVMSimpleLiteralNode.LLVMI64LiteralNode(constant.getValue());
            default:
                return new LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode(LLVMIVarBit.fromLong(bits, constant.getValue()));
        }
    }

    private LLVMExpressionNode resolveVectorConstant(VectorConstant constant) {
        final List<LLVMExpressionNode> values = new ArrayList<>();
        for (int i = 0; i < constant.getLength(); i++) {
            values.add(resolve(constant.getElement(i)));
        }

        final LLVMAddressNode target = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(LLVMBitcodeHelper.getSize(constant, 0), LLVMBitcodeHelper.getAlignment(constant, 0),
                        method.getContext(),
                        method.getStackSlot());

        return LLVMLiteralFactory.createVectorLiteralNode(values, target, LLVMBitcodeHelper.toBaseType(constant.getType()).getType());
    }

    public static LLVMExpressionNode resolveInlineAsmConstant(InlineAsmConstant asmConstant, LLVMExpressionNode[] argNodes, LLVMBaseType targetType) {
        final Parser asmParser = new Parser(asmConstant.getAsmExpression(), asmConstant.getAsmFlags(), argNodes, targetType);
        final LLVMInlineAssemblyRootNode assemblyRootNode = asmParser.Parse();
        final CallTarget callTarget = Truffle.getRuntime().createCallTarget(assemblyRootNode);
        switch (targetType) {
            case VOID:
                return new LLVMUnsupportedInlineAssemblerNode();
            case I1:
                return new LLVMUnsupportedInlineAssemblerNode.LLVMI1UnsupportedInlineAssemblerNode();
            case I8:
                return new LLVMUnsupportedInlineAssemblerNode.LLVMI8UnsupportedInlineAssemblerNode();
            case I16:
                return new LLVMUnsupportedInlineAssemblerNode.LLVMI16UnsupportedInlineAssemblerNode();
            case I32:
                return LLVMCallUnboxNodeFactory.LLVMI32CallUnboxNodeGen.create(new LLVMCallNode.LLVMResolvedDirectCallNode(callTarget, argNodes));
            case I64:
                return new LLVMUnsupportedInlineAssemblerNode.LLVMI64UnsupportedInlineAssemblerNode();
            case FLOAT:
                return new LLVMUnsupportedInlineAssemblerNode.LLVMFloatUnsupportedInlineAssemblerNode();
            case DOUBLE:
                return new LLVMUnsupportedInlineAssemblerNode.LLVMDoubleUnsupportedInlineAssemblerNode();
            case X86_FP80:
                return new LLVMUnsupportedInlineAssemblerNode.LLVM80BitFloatUnsupportedInlineAssemblerNode();
            case ADDRESS:
                return new LLVMUnsupportedInlineAssemblerNode.LLVMAddressUnsupportedInlineAssemblerNode();
            case FUNCTION_ADDRESS:
                return new LLVMUnsupportedInlineAssemblerNode.LLVMFunctionUnsupportedInlineAssemblerNode();
            default:
                throw new AssertionError("Unknown Inline Assembly Return Type!");
        }
    }
}
