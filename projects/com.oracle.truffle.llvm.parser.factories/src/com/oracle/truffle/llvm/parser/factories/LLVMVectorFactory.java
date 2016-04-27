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

import com.intel.llvm.ireditor.lLVM_IR.Type;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMDoubleVectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI16VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI1VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI64VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMVectorNode;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractElementNodeFactory.LLVMDoubleExtractElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractElementNodeFactory.LLVMFloatExtractElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractElementNodeFactory.LLVMI16ExtractElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractElementNodeFactory.LLVMI32ExtractElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractElementNodeFactory.LLVMI64ExtractElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMInsertElementNodeFactory.LLVMDoubleInsertElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMInsertElementNodeFactory.LLVMFloatInsertElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMInsertElementNodeFactory.LLVMI16InsertElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMInsertElementNodeFactory.LLVMI1InsertElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMInsertElementNodeFactory.LLVMI32InsertElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMInsertElementNodeFactory.LLVMI64InsertElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMInsertElementNodeFactory.LLVMI8InsertElementNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMShuffleVectorNodeFactory.LLVMShuffleI32VectorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMShuffleVectorNodeFactory.LLVMShuffleI8VectorNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;

public final class LLVMVectorFactory {

    private LLVMVectorFactory() {
    }

    public static LLVMVectorNode createInsertElement(LLVMParserRuntime runtime, LLVMBaseType resultType, LLVMExpressionNode vector, Type vectorType, LLVMExpressionNode element, LLVMI32Node index) {
        return createInsertElement(resultType, (LLVMAddressNode) runtime.allocateVectorResult(vectorType), vector, element, index);
    }

    public static LLVMVectorNode createInsertElement(LLVMBaseType resultType, LLVMAddressNode target, LLVMExpressionNode vector, LLVMExpressionNode element, LLVMI32Node index) {
        switch (resultType) {
            case I1_VECTOR:
                return LLVMI1InsertElementNodeGen.create(target, (LLVMI1VectorNode) vector, (LLVMI1Node) element, index);
            case I8_VECTOR:
                return LLVMI8InsertElementNodeGen.create(target, (LLVMI8VectorNode) vector, (LLVMI8Node) element, index);
            case I16_VECTOR:
                return LLVMI16InsertElementNodeGen.create(target, (LLVMI16VectorNode) vector, (LLVMI16Node) element, index);
            case I32_VECTOR:
                return LLVMI32InsertElementNodeGen.create(target, (LLVMI32VectorNode) vector, (LLVMI32Node) element, index);
            case I64_VECTOR:
                return LLVMI64InsertElementNodeGen.create(target, (LLVMI64VectorNode) vector, (LLVMI64Node) element, index);
            case FLOAT_VECTOR:
                return LLVMFloatInsertElementNodeGen.create(target, (LLVMFloatVectorNode) vector, (LLVMFloatNode) element, index);
            case DOUBLE_VECTOR:
                return LLVMDoubleInsertElementNodeGen.create(target, (LLVMDoubleVectorNode) vector, (LLVMDoubleNode) element, index);
            default:
                throw new AssertionError("vector type " + resultType + "  not supported!");
        }
    }

    public static LLVMExpressionNode createExtractElement(LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index) {
        switch (resultType) {
            case I16:
                return LLVMI16ExtractElementNodeGen.create((LLVMI16VectorNode) vector, (LLVMI32Node) index);
            case I32:
                return LLVMI32ExtractElementNodeGen.create((LLVMI32VectorNode) vector, (LLVMI32Node) index);
            case I64:
                return LLVMI64ExtractElementNodeGen.create((LLVMI64VectorNode) vector, (LLVMI32Node) index);
            case FLOAT:
                return LLVMFloatExtractElementNodeGen.create((LLVMFloatVectorNode) vector, (LLVMI32Node) index);
            case DOUBLE:
                return LLVMDoubleExtractElementNodeGen.create((LLVMDoubleVectorNode) vector, (LLVMI32Node) index);
            default:
                throw new AssertionError(resultType + " not supported!");
        }
    }

    public static LLVMVectorNode createShuffleVector(LLVMBaseType resultType, LLVMAddressNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2, LLVMI32VectorNode mask) {
        switch (resultType) {
            case I8_VECTOR:
                return LLVMShuffleI8VectorNodeGen.create(target, (LLVMI8VectorNode) vector1, (LLVMI8VectorNode) vector2, mask);
            case I32_VECTOR:
                return LLVMShuffleI32VectorNodeGen.create(target, (LLVMI32VectorNode) vector1, (LLVMI32VectorNode) vector2, mask);
            default:
                throw new AssertionError(resultType);
        }
    }

}
