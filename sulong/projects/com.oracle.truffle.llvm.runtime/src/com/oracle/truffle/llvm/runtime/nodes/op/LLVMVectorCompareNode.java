/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMVector;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMVectorCompareNode extends LLVMExpressionNode {
    private final int vectorLength;

    @Child private LLVMAbstractCompareNode compare;

    public LLVMVectorCompareNode(int vectorLength, LLVMAbstractCompareNode compare) {
        this.vectorLength = vectorLength;
        this.compare = compare;
    }

    private boolean checkLength(LLVMVector val1, LLVMVector val2) {
        return val1.getLength() == vectorLength && val2.getLength() == vectorLength;
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doI1(LLVMI1Vector val1, LLVMI1Vector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doI8(LLVMI8Vector val1, LLVMI8Vector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doI16(LLVMI16Vector val1, LLVMI16Vector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doI32(LLVMI32Vector val1, LLVMI32Vector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doI64(LLVMI64Vector val1, LLVMI64Vector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doI64AndPointer(LLVMPointerVector val1, LLVMI64Vector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doI64AndPointer(LLVMI64Vector val1, LLVMPointerVector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doPointer(LLVMPointerVector val1, LLVMPointerVector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doDouble(LLVMDoubleVector val1, LLVMDoubleVector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMI1Vector doFloat(LLVMFloatVector val1, LLVMFloatVector val2) {
        assert checkLength(val1, val2);
        boolean[] result = new boolean[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            result[i] = compare.executeWithTarget(val1.getValue(i), val2.getValue(i));
        }
        return LLVMI1Vector.create(result);
    }
}
