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

import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI1LiteralNode;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatOeqNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatOgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatOgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatOleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatOltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatOneNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatOrdNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatUeqNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatUgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatUgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatUleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatUltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatUneNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVM80BitFloatCompareNodeFactory.LLVM80BitFloatUnoNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMAddressCompareNodeFactory.LLVMAddressSgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMAddressCompareNodeFactory.LLVMAddressSgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMAddressCompareNodeFactory.LLVMAddressSleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMAddressCompareNodeFactory.LLVMAddressSltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMAddressCompareNodeFactory.LLVMAddressUgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMAddressCompareNodeFactory.LLVMAddressUgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMAddressCompareNodeFactory.LLVMAddressUleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMAddressCompareNodeFactory.LLVMAddressUltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleOeqNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleOgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleOgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleOleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleOltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleOneNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleOrdNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleUeqNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleUgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleUgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleUleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleUltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleUneNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMDoubleCompareNodeFactory.LLVMDoubleUnoNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMEqNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatOeqNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatOgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatOgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatOleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatOltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatOneNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatOrdNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatUeqNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatUgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatUgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatUleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatUltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatUneNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMFloatCompareNodeFactory.LLVMFloatUnoNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI16CompareNodeFactory.LLVMI16SgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI16CompareNodeFactory.LLVMI16SgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI16CompareNodeFactory.LLVMI16SleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI16CompareNodeFactory.LLVMI16SltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI16CompareNodeFactory.LLVMI16UgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI16CompareNodeFactory.LLVMI16UgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI16CompareNodeFactory.LLVMI16UleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI16CompareNodeFactory.LLVMI16UltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32CompareNodeFactory.LLVMI32SgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32CompareNodeFactory.LLVMI32SgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32CompareNodeFactory.LLVMI32SleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32CompareNodeFactory.LLVMI32SltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32CompareNodeFactory.LLVMI32UgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32CompareNodeFactory.LLVMI32UgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32CompareNodeFactory.LLVMI32UleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32CompareNodeFactory.LLVMI32UltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorEqNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorNeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorSgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorSgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorSleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorSltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorUgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorUgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorUleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI32VectorCompareNodeFactory.LLVMI32VectorUltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI64CompareNodeFactory.LLVMI64SgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI64CompareNodeFactory.LLVMI64SgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI64CompareNodeFactory.LLVMI64SleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI64CompareNodeFactory.LLVMI64SltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI64CompareNodeFactory.LLVMI64UgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI64CompareNodeFactory.LLVMI64UgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI64CompareNodeFactory.LLVMI64UleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI64CompareNodeFactory.LLVMI64UltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI8CompareNodeFactory.LLVMI8SgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI8CompareNodeFactory.LLVMI8SgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI8CompareNodeFactory.LLVMI8SleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI8CompareNodeFactory.LLVMI8SltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI8CompareNodeFactory.LLVMI8UgeNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI8CompareNodeFactory.LLVMI8UgtNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI8CompareNodeFactory.LLVMI8UleNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMI8CompareNodeFactory.LLVMI8UltNodeGen;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMNeqNodeGen;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.model.enums.CompareOperator;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;

final class LLVMComparisonFactory {

    private LLVMComparisonFactory() {
    }

    static LLVMExpressionNode toCompareVectorNode(CompareOperator operator, Type llvmtype, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {

        switch (operator) {
            case FP_FALSE:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.FALSE);
            case FP_ORDERED_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_EQUALS);
            case FP_ORDERED_GREATER_THAN:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_GREATER_THAN);
            case FP_ORDERED_GREATER_OR_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_GREATER_EQUALS);
            case FP_ORDERED_LESS_THAN:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_LESS_THAN);
            case FP_ORDERED_LESS_OR_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_LESS_EQUALS);
            case FP_ORDERED_NOT_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_NOT_EQUALS);

            case FP_ORDERED:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED);
            case FP_UNORDERED:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED);
            case FP_UNORDERED_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_EQUALS);
            case FP_UNORDERED_GREATER_THAN:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_GREATER_THAN);
            case FP_UNORDERED_GREATER_OR_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_GREATER_EQUALS);
            case FP_UNORDERED_LESS_THAN:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_LESS_THAN);
            case FP_UNORDERED_LESS_OR_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_LESS_EQUALS);
            case FP_UNORDERED_NOT_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_NOT_EQUALS);
            case FP_TRUE:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.TRUE);
            default:
                break;
        }

        final LLVMIntegerComparisonType comparison;
        switch (operator) {
            case INT_EQUAL:
                comparison = LLVMIntegerComparisonType.EQUALS;
                break;
            case INT_NOT_EQUAL:
                comparison = LLVMIntegerComparisonType.NOT_EQUALS;
                break;
            case INT_UNSIGNED_GREATER_THAN:
                comparison = LLVMIntegerComparisonType.UNSIGNED_GREATER_THAN;
                break;
            case INT_UNSIGNED_GREATER_OR_EQUAL:
                comparison = LLVMIntegerComparisonType.UNSIGNED_GREATER_EQUALS;
                break;
            case INT_UNSIGNED_LESS_THAN:
                comparison = LLVMIntegerComparisonType.UNSIGNED_LESS_THAN;
                break;
            case INT_UNSIGNED_LESS_OR_EQUAL:
                comparison = LLVMIntegerComparisonType.UNSIGNED_LESS_EQUALS;
                break;
            case INT_SIGNED_GREATER_THAN:
                comparison = LLVMIntegerComparisonType.SIGNED_GREATER_THAN;
                break;
            case INT_SIGNED_GREATER_OR_EQUAL:
                comparison = LLVMIntegerComparisonType.SIGNED_GREATER_EQUALS;
                break;
            case INT_SIGNED_LESS_THAN:
                comparison = LLVMIntegerComparisonType.SIGNED_LESS_THAN;
                break;
            case INT_SIGNED_LESS_OR_EQUAL:
                comparison = LLVMIntegerComparisonType.SIGNED_LESS_EQUALS;
                break;

            default:
                throw new RuntimeException("Missed a compare operator");
        }

        if (llvmtype instanceof VectorType) {
            return LLVMComparisonFactory.createVectorComparison(lhs, rhs, llvmtype, comparison);
        } else {
            return LLVMComparisonFactory.createIntegerComparison(lhs, rhs, llvmtype, comparison);
        }
    }

    private static LLVMExpressionNode createIntegerComparison(LLVMExpressionNode left, LLVMExpressionNode right, Type llvmType, LLVMIntegerComparisonType condition) {
        if (llvmType instanceof PrimitiveType) {
            return handlePrimitive(left, right, llvmType, condition);
        } else if (llvmType instanceof VariableBitWidthType) {
            return visitIVarComparison(left, right, condition);
        } else if (Type.isFunctionOrFunctionPointer(llvmType)) {
            return visitFunctionComparison(left, right, condition);
        } else if (llvmType instanceof PointerType) {
            return visitAddressComparison(left, right, condition);
        }
        throw new AssertionError(llvmType);
    }

    private static LLVMExpressionNode handlePrimitive(LLVMExpressionNode left, LLVMExpressionNode right, Type llvmType, LLVMIntegerComparisonType condition) throws AssertionError {
        switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
            case I1:
                return visitI1Comparison(left, right, condition);
            case I8:
                return visitI8Comparison(left, right, condition);
            case I16:
                return visitI16Comparison(left, right, condition);
            case I32:
                return visitI32Comparison(left, right, condition);
            case I64:
                return visitI64Comparison(left, right, condition);
            default:
                throw new AssertionError(llvmType);
        }
    }

    private static LLVMExpressionNode createVectorComparison(LLVMExpressionNode left, LLVMExpressionNode right, Type llvmType, LLVMIntegerComparisonType condition) {
        if (llvmType instanceof VectorType && ((VectorType) llvmType).getElementType() == PrimitiveType.I32) {
            return visitI32VectorComparison(left, right, condition);
        } else {
            throw new AssertionError(llvmType);
        }
    }

    private static LLVMExpressionNode visitIVarComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMIntegerComparisonType condition) {
        switch (condition) {
            case NOT_EQUALS:
                return LLVMNeqNodeGen.create(left, right);
            case EQUALS:
                return LLVMEqNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visitI1Comparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMIntegerComparisonType condition) {
        switch (condition) {
            case EQUALS:
                return LLVMEqNodeGen.create(left, right);
            case NOT_EQUALS:
                return LLVMNeqNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visitI8Comparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMIntegerComparisonType condition) {
        switch (condition) {
            case EQUALS:
                return LLVMEqNodeGen.create(left, right);
            case NOT_EQUALS:
                return LLVMNeqNodeGen.create(left, right);
            case UNSIGNED_GREATER_THAN:
                return LLVMI8UgtNodeGen.create(left, right);
            case UNSIGNED_GREATER_EQUALS:
                return LLVMI8UgeNodeGen.create(left, right);
            case UNSIGNED_LESS_THAN:
                return LLVMI8UltNodeGen.create(left, right);
            case UNSIGNED_LESS_EQUALS:
                return LLVMI8UleNodeGen.create(left, right);
            case SIGNED_GREATER_THAN:
                return LLVMI8SgtNodeGen.create(left, right);
            case SIGNED_GREATER_EQUALS:
                return LLVMI8SgeNodeGen.create(left, right);
            case SIGNED_LESS_THAN:
                return LLVMI8SltNodeGen.create(left, right);
            case SIGNED_LESS_EQUALS:
                return LLVMI8SleNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visitI16Comparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMIntegerComparisonType condition) {
        switch (condition) {
            case EQUALS:
                return LLVMEqNodeGen.create(left, right);
            case NOT_EQUALS:
                return LLVMNeqNodeGen.create(left, right);
            case UNSIGNED_GREATER_THAN:
                return LLVMI16UgtNodeGen.create(left, right);
            case UNSIGNED_GREATER_EQUALS:
                return LLVMI16UgeNodeGen.create(left, right);
            case UNSIGNED_LESS_THAN:
                return LLVMI16UltNodeGen.create(left, right);
            case UNSIGNED_LESS_EQUALS:
                return LLVMI16UleNodeGen.create(left, right);
            case SIGNED_GREATER_THAN:
                return LLVMI16SgtNodeGen.create(left, right);
            case SIGNED_GREATER_EQUALS:
                return LLVMI16SgeNodeGen.create(left, right);
            case SIGNED_LESS_THAN:
                return LLVMI16SltNodeGen.create(left, right);
            case SIGNED_LESS_EQUALS:
                return LLVMI16SleNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visitI32Comparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMIntegerComparisonType condition) {
        switch (condition) {
            case EQUALS:
                return LLVMEqNodeGen.create(left, right);
            case NOT_EQUALS:
                return LLVMNeqNodeGen.create(left, right);
            case UNSIGNED_GREATER_THAN:
                return LLVMI32UgtNodeGen.create(left, right);
            case UNSIGNED_GREATER_EQUALS:
                return LLVMI32UgeNodeGen.create(left, right);
            case UNSIGNED_LESS_THAN:
                return LLVMI32UltNodeGen.create(left, right);
            case UNSIGNED_LESS_EQUALS:
                return LLVMI32UleNodeGen.create(left, right);
            case SIGNED_GREATER_THAN:
                return LLVMI32SgtNodeGen.create(left, right);
            case SIGNED_GREATER_EQUALS:
                return LLVMI32SgeNodeGen.create(left, right);
            case SIGNED_LESS_THAN:
                return LLVMI32SltNodeGen.create(left, right);
            case SIGNED_LESS_EQUALS:
                return LLVMI32SleNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visitI64Comparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMIntegerComparisonType condition) {
        switch (condition) {
            case EQUALS:
                return LLVMEqNodeGen.create(left, right);
            case NOT_EQUALS:
                return LLVMNeqNodeGen.create(left, right);
            case UNSIGNED_GREATER_THAN:
                return LLVMI64UgtNodeGen.create(left, right);
            case UNSIGNED_GREATER_EQUALS:
                return LLVMI64UgeNodeGen.create(left, right);
            case UNSIGNED_LESS_THAN:
                return LLVMI64UltNodeGen.create(left, right);
            case UNSIGNED_LESS_EQUALS:
                return LLVMI64UleNodeGen.create(left, right);
            case SIGNED_GREATER_THAN:
                return LLVMI64SgtNodeGen.create(left, right);
            case SIGNED_GREATER_EQUALS:
                return LLVMI64SgeNodeGen.create(left, right);
            case SIGNED_LESS_THAN:
                return LLVMI64SltNodeGen.create(left, right);
            case SIGNED_LESS_EQUALS:
                return LLVMI64SleNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visitAddressComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMIntegerComparisonType condition) {
        switch (condition) {
            case EQUALS:
                return LLVMEqNodeGen.create(left, right);
            case NOT_EQUALS:
                return LLVMNeqNodeGen.create(left, right);
            case UNSIGNED_GREATER_THAN:
                return LLVMAddressUgtNodeGen.create(left, right);
            case UNSIGNED_GREATER_EQUALS:
                return LLVMAddressUgeNodeGen.create(left, right);
            case UNSIGNED_LESS_THAN:
                return LLVMAddressUltNodeGen.create(left, right);
            case UNSIGNED_LESS_EQUALS:
                return LLVMAddressUleNodeGen.create(left, right);
            case SIGNED_GREATER_THAN:
                return LLVMAddressSgtNodeGen.create(left, right);
            case SIGNED_GREATER_EQUALS:
                return LLVMAddressSgeNodeGen.create(left, right);
            case SIGNED_LESS_THAN:
                return LLVMAddressSltNodeGen.create(left, right);
            case SIGNED_LESS_EQUALS:
                return LLVMAddressSleNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visitFunctionComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMIntegerComparisonType condition) {
        switch (condition) {
            case EQUALS:
                return LLVMEqNodeGen.create(left, right);
            case NOT_EQUALS:
                return LLVMNeqNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode createFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, Type llvmType, LLVMFloatComparisonType condition) {
        if (condition == LLVMFloatComparisonType.FALSE) {
            return new LLVMI1LiteralNode(false);
        } else if (condition == LLVMFloatComparisonType.TRUE) {
            return new LLVMI1LiteralNode(true);
        }
        switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
            case FLOAT:
                return visitFloatComparison(left, right, condition);
            case DOUBLE:
                return visitDoubleComparison(left, right, condition);
            case X86_FP80:
                return visit80BitFloatComparison(left, right, condition);
            default:
                throw new AssertionError(llvmType);
        }
    }

    private static LLVMExpressionNode visitFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMFloatComparisonType condition) {
        switch (condition) {
            case ORDERED_AND_EQUALS:
                return LLVMFloatOeqNodeGen.create(left, right);
            case ORDERED_AND_GREATER_THAN:
                return LLVMFloatOgtNodeGen.create(left, right);
            case ORDERED_AND_GREATER_EQUALS:
                return LLVMFloatOgeNodeGen.create(left, right);
            case ORDERED_AND_LESS_THAN:
                return LLVMFloatOltNodeGen.create(left, right);
            case ORDERED_AND_LESS_EQUALS:
                return LLVMFloatOleNodeGen.create(left, right);
            case ORDERED_AND_NOT_EQUALS:
                return LLVMFloatOneNodeGen.create(left, right);
            case ORDERED:
                return LLVMFloatOrdNodeGen.create(left, right);
            case UNORDERED_OR_EQUALS:
                return LLVMFloatUeqNodeGen.create(left, right);
            case UNORDERED_OR_GREATER_THAN:
                return LLVMFloatUgtNodeGen.create(left, right);
            case UNORDERED_OR_GREATER_EQUALS:
                return LLVMFloatUgeNodeGen.create(left, right);
            case UNORDERED_OR_LESS_THAN:
                return LLVMFloatUltNodeGen.create(left, right);
            case UNORDERED_OR_LESS_EQUALS:
                return LLVMFloatUleNodeGen.create(left, right);
            case UNORDERED_OR_NOT_EQUALS:
                return LLVMFloatUneNodeGen.create(left, right);
            case UNORDERED:
                return LLVMFloatUnoNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visitDoubleComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMFloatComparisonType condition) {
        switch (condition) {
            case ORDERED_AND_EQUALS:
                return LLVMDoubleOeqNodeGen.create(left, right);
            case ORDERED_AND_GREATER_THAN:
                return LLVMDoubleOgtNodeGen.create(left, right);
            case ORDERED_AND_GREATER_EQUALS:
                return LLVMDoubleOgeNodeGen.create(left, right);
            case ORDERED_AND_LESS_THAN:
                return LLVMDoubleOltNodeGen.create(left, right);
            case ORDERED_AND_LESS_EQUALS:
                return LLVMDoubleOleNodeGen.create(left, right);
            case ORDERED_AND_NOT_EQUALS:
                return LLVMDoubleOneNodeGen.create(left, right);
            case ORDERED:
                return LLVMDoubleOrdNodeGen.create(left, right);
            case UNORDERED_OR_EQUALS:
                return LLVMDoubleUeqNodeGen.create(left, right);
            case UNORDERED_OR_GREATER_THAN:
                return LLVMDoubleUgtNodeGen.create(left, right);
            case UNORDERED_OR_GREATER_EQUALS:
                return LLVMDoubleUgeNodeGen.create(left, right);
            case UNORDERED_OR_LESS_THAN:
                return LLVMDoubleUltNodeGen.create(left, right);
            case UNORDERED_OR_LESS_EQUALS:
                return LLVMDoubleUleNodeGen.create(left, right);
            case UNORDERED_OR_NOT_EQUALS:
                return LLVMDoubleUneNodeGen.create(left, right);
            case UNORDERED:
                return LLVMDoubleUnoNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visit80BitFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMFloatComparisonType condition) {
        switch (condition) {
            case ORDERED_AND_EQUALS:
                return LLVM80BitFloatOeqNodeGen.create(left, right);
            case ORDERED_AND_GREATER_THAN:
                return LLVM80BitFloatOgtNodeGen.create(left, right);
            case ORDERED_AND_GREATER_EQUALS:
                return LLVM80BitFloatOgeNodeGen.create(left, right);
            case ORDERED_AND_LESS_THAN:
                return LLVM80BitFloatOltNodeGen.create(left, right);
            case ORDERED_AND_LESS_EQUALS:
                return LLVM80BitFloatOleNodeGen.create(left, right);
            case ORDERED_AND_NOT_EQUALS:
                return LLVM80BitFloatOneNodeGen.create(left, right);
            case ORDERED:
                return LLVM80BitFloatOrdNodeGen.create(left, right);
            case UNORDERED_OR_EQUALS:
                return LLVM80BitFloatUeqNodeGen.create(left, right);
            case UNORDERED_OR_GREATER_THAN:
                return LLVM80BitFloatUgtNodeGen.create(left, right);
            case UNORDERED_OR_GREATER_EQUALS:
                return LLVM80BitFloatUgeNodeGen.create(left, right);
            case UNORDERED_OR_LESS_THAN:
                return LLVM80BitFloatUltNodeGen.create(left, right);
            case UNORDERED_OR_LESS_EQUALS:
                return LLVM80BitFloatUleNodeGen.create(left, right);
            case UNORDERED_OR_NOT_EQUALS:
                return LLVM80BitFloatUneNodeGen.create(left, right);
            case UNORDERED:
                return LLVM80BitFloatUnoNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }

    private static LLVMExpressionNode visitI32VectorComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMIntegerComparisonType condition) {
        switch (condition) {
            case EQUALS:
                return LLVMI32VectorEqNodeGen.create(left, right);
            case NOT_EQUALS:
                return LLVMI32VectorNeNodeGen.create(left, right);
            case UNSIGNED_GREATER_THAN:
                return LLVMI32VectorUgtNodeGen.create(left, right);
            case UNSIGNED_GREATER_EQUALS:
                return LLVMI32VectorUgeNodeGen.create(left, right);
            case UNSIGNED_LESS_THAN:
                return LLVMI32VectorUltNodeGen.create(left, right);
            case UNSIGNED_LESS_EQUALS:
                return LLVMI32VectorUleNodeGen.create(left, right);
            case SIGNED_GREATER_THAN:
                return LLVMI32VectorSgtNodeGen.create(left, right);
            case SIGNED_GREATER_EQUALS:
                return LLVMI32VectorSgeNodeGen.create(left, right);
            case SIGNED_LESS_THAN:
                return LLVMI32VectorSltNodeGen.create(left, right);
            case SIGNED_LESS_EQUALS:
                return LLVMI32VectorSleNodeGen.create(left, right);
            default:
                throw new AssertionError(condition);
        }
    }
}
