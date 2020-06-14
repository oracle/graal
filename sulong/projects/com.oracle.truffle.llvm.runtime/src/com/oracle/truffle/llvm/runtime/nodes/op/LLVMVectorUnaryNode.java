/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.op;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild("operandNode")
public abstract class LLVMVectorUnaryNode extends LLVMExpressionNode {
    private final int vectorLength;

    @Child private LLVMUnaryNode unaryNode;

    public LLVMVectorUnaryNode(int vectorLength, LLVMUnaryNode unaryNode) {
        this.vectorLength = vectorLength;
        this.unaryNode = unaryNode;
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doI1(LLVMI1Vector operand) {
        assert operand.getLength() == vectorLength;
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = LLVMTypesGen.asBoolean(unaryNode.executeWithTarget(operand.getValue(i)));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI8Vector doI8(LLVMI8Vector operand) {
        assert operand.getLength() == vectorLength;
        byte[] result = new byte[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = LLVMTypesGen.asByte(unaryNode.executeWithTarget(operand.getValue(i)));
        }
        return LLVMI8Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI16Vector doI16(LLVMI16Vector operand) {
        assert operand.getLength() == vectorLength;
        short[] result = new short[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = LLVMTypesGen.asShort(unaryNode.executeWithTarget(operand.getValue(i)));
        }
        return LLVMI16Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI32Vector doI32(LLVMI32Vector operand) {
        assert operand.getLength() == vectorLength;
        int[] result = new int[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = LLVMTypesGen.asInteger(unaryNode.executeWithTarget(operand.getValue(i)));
        }
        return LLVMI32Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI64Vector doI64(LLVMI64Vector operand) {
        assert operand.getLength() == vectorLength;
        long[] result = new long[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = LLVMTypesGen.asLong(unaryNode.executeWithTarget(operand.getValue(i)));
        }
        return LLVMI64Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMFloatVector doFloat(LLVMFloatVector operand) {
        assert operand.getLength() == vectorLength;
        float[] result = new float[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = LLVMTypesGen.asFloat(unaryNode.executeWithTarget(operand.getValue(i)));
        }
        return LLVMFloatVector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMDoubleVector doDouble(LLVMDoubleVector operand) {
        assert operand.getLength() == vectorLength;
        double[] result = new double[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = LLVMTypesGen.asDouble(unaryNode.executeWithTarget(operand.getValue(i)));
        }
        return LLVMDoubleVector.create(result);
    }
}
