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

import com.intel.llvm.ireditor.lLVM_IR.BitwiseBinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_and;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_ashr;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_lshr;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_or;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_shl;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_xor;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI16VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI1VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI64VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI16LogicalNodeFactory.LLVMI16AndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI16LogicalNodeFactory.LLVMI16AshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI16LogicalNodeFactory.LLVMI16LshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI16LogicalNodeFactory.LLVMI16OrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI16LogicalNodeFactory.LLVMI16ShlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI16LogicalNodeFactory.LLVMI16XorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI1LogicalNodeFactory.LLVMI1AndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI1LogicalNodeFactory.LLVMI1OrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI1LogicalNodeFactory.LLVMI1XorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI32LogicalNodeFactory.LLVMI32AndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI32LogicalNodeFactory.LLVMI32AshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI32LogicalNodeFactory.LLVMI32LshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI32LogicalNodeFactory.LLVMI32OrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI32LogicalNodeFactory.LLVMI32ShlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI32LogicalNodeFactory.LLVMI32XorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI64LogicalNodeFactory.LLVMI64AndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI64LogicalNodeFactory.LLVMI64AshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI64LogicalNodeFactory.LLVMI64LshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI64LogicalNodeFactory.LLVMI64OrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI64LogicalNodeFactory.LLVMI64ShlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI64LogicalNodeFactory.LLVMI64XorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI8LogicalNodeFactory.LLVMI8AndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI8LogicalNodeFactory.LLVMI8AshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI8LogicalNodeFactory.LLVMI8LshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI8LogicalNodeFactory.LLVMI8OrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI8LogicalNodeFactory.LLVMI8ShlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMI8LogicalNodeFactory.LLVMI8XorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMIVarLogicalNodeFactory.LLVMIVarAndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMIVarLogicalNodeFactory.LLVMIVarAshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMIVarLogicalNodeFactory.LLVMIVarLshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMIVarLogicalNodeFactory.LLVMIVarOrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMIVarLogicalNodeFactory.LLVMIVarShlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.integer.LLVMIVarLogicalNodeFactory.LLVMIVarXorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI16VectorLogicalNodeFactory.LLVMI16VectorAndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI16VectorLogicalNodeFactory.LLVMI16VectorAshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI16VectorLogicalNodeFactory.LLVMI16VectorLshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI16VectorLogicalNodeFactory.LLVMI16VectorOrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI16VectorLogicalNodeFactory.LLVMI16VectorShlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI16VectorLogicalNodeFactory.LLVMI16VectorXorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI1VectorLogicalNodeFactory.LLVMI1VectorAndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI1VectorLogicalNodeFactory.LLVMI1VectorOrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI1VectorLogicalNodeFactory.LLVMI1VectorXorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI32VectorLogicalNodeFactory.LLVMI32VectorAndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI32VectorLogicalNodeFactory.LLVMI32VectorAshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI32VectorLogicalNodeFactory.LLVMI32VectorLshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI32VectorLogicalNodeFactory.LLVMI32VectorOrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI32VectorLogicalNodeFactory.LLVMI32VectorShlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI32VectorLogicalNodeFactory.LLVMI32VectorXorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI64VectorLogicalNodeFactory.LLVMI64VectorAndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI64VectorLogicalNodeFactory.LLVMI64VectorAshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI64VectorLogicalNodeFactory.LLVMI64VectorLshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI64VectorLogicalNodeFactory.LLVMI64VectorOrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI64VectorLogicalNodeFactory.LLVMI64VectorShlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI64VectorLogicalNodeFactory.LLVMI64VectorXorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI8VectorLogicalNodeFactory.LLVMI8VectorAndNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI8VectorLogicalNodeFactory.LLVMI8VectorAshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI8VectorLogicalNodeFactory.LLVMI8VectorLshrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI8VectorLogicalNodeFactory.LLVMI8VectorOrNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI8VectorLogicalNodeFactory.LLVMI8VectorShlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.op.logical.vector.LLVMI8VectorLogicalNodeFactory.LLVMI8VectorXorNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;

public final class LLVMLogicalFactory {

    private LLVMLogicalFactory() {
    }

    public static LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType type, LLVMBaseType llvmType, LLVMAddressNode target) {
        return createNode(left, right, llvmType, type, target);
    }

    private static LLVMExpressionNode createNode(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMLogicalInstructionType type, LLVMAddressNode target)
                    throws AssertionError {
        switch (llvmType) {
            case I1:
                return visitLogicalI1Instruction(type, (LLVMI1Node) left, (LLVMI1Node) right);
            case I8:
                return visitLogicalI8Instruction(type, (LLVMI8Node) left, (LLVMI8Node) right);
            case I16:
                return visitLogicalI16Instruction(type, (LLVMI16Node) left, (LLVMI16Node) right);
            case I32:
                return visitLogicalI32Instruction(type, (LLVMI32Node) left, (LLVMI32Node) right);
            case I64:
                return visitLogicalI64Instruction(type, (LLVMI64Node) left, (LLVMI64Node) right);
            case I_VAR_BITWIDTH:
                return visitLogicalIVarInstruction(type, (LLVMIVarBitNode) left, (LLVMIVarBitNode) right);
            case I1_VECTOR:
                return visitLogicalI1VectorInstruction(type, (LLVMI1VectorNode) left, (LLVMI1VectorNode) right, target);
            case I8_VECTOR:
                return visitLogicalI8VectorInstruction(type, (LLVMI8VectorNode) left, (LLVMI8VectorNode) right, target);
            case I16_VECTOR:
                return visitLogicalI16VectorInstruction(type, (LLVMI16VectorNode) left, (LLVMI16VectorNode) right, target);
            case I32_VECTOR:
                return visitLogicalI32VectorInstruction(type, (LLVMI32VectorNode) left, (LLVMI32VectorNode) right, target);
            case I64_VECTOR:
                return visitLogicalI64VectorInstruction(type, (LLVMI64VectorNode) left, (LLVMI64VectorNode) right, target);
            default:
                throw new AssertionError(llvmType);
        }
    }

    private static LLVMExpressionNode visitLogicalIVarInstruction(LLVMLogicalInstructionType type, LLVMIVarBitNode left, LLVMIVarBitNode right) {
        switch (type) {
            case SHIFT_LEFT:
                return LLVMIVarShlNodeGen.create(left, right);
            case LOGICAL_SHIFT_RIGHT:
                return LLVMIVarLshrNodeGen.create(left, right);
            case ARITHMETIC_SHIFT_RIGHT:
                return LLVMIVarAshrNodeGen.create(left, right);
            case AND:
                return LLVMIVarAndNodeGen.create(left, right);
            case OR:
                return LLVMIVarOrNodeGen.create(left, right);
            case XOR:
                return LLVMIVarXorNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitLogicalI1VectorInstruction(LLVMLogicalInstructionType type, LLVMI1VectorNode left, LLVMI1VectorNode right, LLVMAddressNode target) {
        switch (type) {
            case AND:
                return LLVMI1VectorAndNodeGen.create(target, left, right);
            case OR:
                return LLVMI1VectorOrNodeGen.create(target, left, right);
            case XOR:
                return LLVMI1VectorXorNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitLogicalI16VectorInstruction(LLVMLogicalInstructionType type, LLVMI16VectorNode left, LLVMI16VectorNode right, LLVMAddressNode target) {
        switch (type) {
            case SHIFT_LEFT:
                return LLVMI16VectorShlNodeGen.create(target, left, right);
            case LOGICAL_SHIFT_RIGHT:
                return LLVMI16VectorLshrNodeGen.create(target, left, right);
            case ARITHMETIC_SHIFT_RIGHT:
                return LLVMI16VectorAshrNodeGen.create(target, left, right);
            case AND:
                return LLVMI16VectorAndNodeGen.create(target, left, right);
            case OR:
                return LLVMI16VectorOrNodeGen.create(target, left, right);
            case XOR:
                return LLVMI16VectorXorNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitLogicalI8VectorInstruction(LLVMLogicalInstructionType type, LLVMI8VectorNode left, LLVMI8VectorNode right, LLVMAddressNode target) {
        switch (type) {
            case SHIFT_LEFT:
                return LLVMI8VectorShlNodeGen.create(target, left, right);
            case LOGICAL_SHIFT_RIGHT:
                return LLVMI8VectorLshrNodeGen.create(target, left, right);
            case ARITHMETIC_SHIFT_RIGHT:
                return LLVMI8VectorAshrNodeGen.create(target, left, right);
            case AND:
                return LLVMI8VectorAndNodeGen.create(target, left, right);
            case OR:
                return LLVMI8VectorOrNodeGen.create(target, left, right);
            case XOR:
                return LLVMI8VectorXorNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitLogicalI32VectorInstruction(LLVMLogicalInstructionType type, LLVMI32VectorNode left, LLVMI32VectorNode right, LLVMAddressNode target) {
        switch (type) {
            case SHIFT_LEFT:
                return LLVMI32VectorShlNodeGen.create(target, left, right);
            case LOGICAL_SHIFT_RIGHT:
                return LLVMI32VectorLshrNodeGen.create(target, left, right);
            case ARITHMETIC_SHIFT_RIGHT:
                return LLVMI32VectorAshrNodeGen.create(target, left, right);
            case AND:
                return LLVMI32VectorAndNodeGen.create(target, left, right);
            case OR:
                return LLVMI32VectorOrNodeGen.create(target, left, right);
            case XOR:
                return LLVMI32VectorXorNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitLogicalI64VectorInstruction(LLVMLogicalInstructionType type, LLVMI64VectorNode left, LLVMI64VectorNode right, LLVMAddressNode target) {
        switch (type) {
            case SHIFT_LEFT:
                return LLVMI64VectorShlNodeGen.create(target, left, right);
            case LOGICAL_SHIFT_RIGHT:
                return LLVMI64VectorLshrNodeGen.create(target, left, right);
            case ARITHMETIC_SHIFT_RIGHT:
                return LLVMI64VectorAshrNodeGen.create(target, left, right);
            case AND:
                return LLVMI64VectorAndNodeGen.create(target, left, right);
            case OR:
                return LLVMI64VectorOrNodeGen.create(target, left, right);
            case XOR:
                return LLVMI64VectorXorNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    public static LLVMLogicalInstructionType getLogicalInstructionType(BitwiseBinaryInstruction instr) {
        if (instr instanceof Instruction_and) {
            return LLVMLogicalInstructionType.AND;
        } else if (instr instanceof Instruction_or) {
            return LLVMLogicalInstructionType.OR;
        } else if (instr instanceof Instruction_shl) {
            return LLVMLogicalInstructionType.SHIFT_LEFT;
        } else if (instr instanceof Instruction_lshr) {
            return LLVMLogicalInstructionType.LOGICAL_SHIFT_RIGHT;
        } else if (instr instanceof Instruction_ashr) {
            return LLVMLogicalInstructionType.ARITHMETIC_SHIFT_RIGHT;
        } else if (instr instanceof Instruction_xor) {
            return LLVMLogicalInstructionType.XOR;
        } else {
            throw new AssertionError(instr);
        }
    }

    private static LLVMI1Node visitLogicalI1Instruction(LLVMLogicalInstructionType type, LLVMI1Node left, LLVMI1Node right) {
        switch (type) {
            case AND:
                return LLVMI1AndNodeGen.create(left, right);
            case OR:
                return LLVMI1OrNodeGen.create(left, right);
            case XOR:
                return LLVMI1XorNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMI8Node visitLogicalI8Instruction(LLVMLogicalInstructionType type, LLVMI8Node left, LLVMI8Node right) {
        switch (type) {
            case SHIFT_LEFT:
                return LLVMI8ShlNodeGen.create(left, right);
            case LOGICAL_SHIFT_RIGHT:
                return LLVMI8LshrNodeGen.create(left, right);
            case ARITHMETIC_SHIFT_RIGHT:
                return LLVMI8AshrNodeGen.create(left, right);
            case AND:
                return LLVMI8AndNodeGen.create(left, right);
            case OR:
                return LLVMI8OrNodeGen.create(left, right);
            case XOR:
                return LLVMI8XorNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMI16Node visitLogicalI16Instruction(LLVMLogicalInstructionType type, LLVMI16Node left, LLVMI16Node right) {
        switch (type) {
            case SHIFT_LEFT:
                return LLVMI16ShlNodeGen.create(left, right);
            case LOGICAL_SHIFT_RIGHT:
                return LLVMI16LshrNodeGen.create(left, right);
            case ARITHMETIC_SHIFT_RIGHT:
                return LLVMI16AshrNodeGen.create(left, right);
            case AND:
                return LLVMI16AndNodeGen.create(left, right);
            case OR:
                return LLVMI16OrNodeGen.create(left, right);
            case XOR:
                return LLVMI16XorNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMI32Node visitLogicalI32Instruction(LLVMLogicalInstructionType type, LLVMI32Node left, LLVMI32Node right) {
        switch (type) {
            case SHIFT_LEFT:
                return LLVMI32ShlNodeGen.create(left, right);
            case LOGICAL_SHIFT_RIGHT:
                return LLVMI32LshrNodeGen.create(left, right);
            case ARITHMETIC_SHIFT_RIGHT:
                return LLVMI32AshrNodeGen.create(left, right);
            case AND:
                return LLVMI32AndNodeGen.create(left, right);
            case OR:
                return LLVMI32OrNodeGen.create(left, right);
            case XOR:
                return LLVMI32XorNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMI64Node visitLogicalI64Instruction(LLVMLogicalInstructionType type, LLVMI64Node left, LLVMI64Node right) {
        switch (type) {
            case SHIFT_LEFT:
                return LLVMI64ShlNodeGen.create(left, right);
            case LOGICAL_SHIFT_RIGHT:
                return LLVMI64LshrNodeGen.create(left, right);
            case ARITHMETIC_SHIFT_RIGHT:
                return LLVMI64AshrNodeGen.create(left, right);
            case AND:
                return LLVMI64AndNodeGen.create(left, right);
            case OR:
                return LLVMI64OrNodeGen.create(left, right);
            case XOR:
                return LLVMI64XorNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

}
