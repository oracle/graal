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
package com.oracle.truffle.llvm.runtime.nodes.others;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

@NodeChild(value = "conditionNode", type = LLVMExpressionNode.class)
@NodeChild(value = "trueNode", type = LLVMExpressionNode.class)
@NodeChild(value = "elseNode", type = LLVMExpressionNode.class)
@NodeField(name = "vectorLength", type = int.class)
public abstract class LLVMVectorSelectNode extends LLVMExpressionNode {
    protected final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

    protected abstract int getVectorLength();

    public abstract static class LLVMI1VectorSelectNode extends LLVMVectorSelectNode {
        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doOp(LLVMI1Vector condition, LLVMI1Vector trueValue, LLVMI1Vector elseValue) {
            assert condition.getLength() == getVectorLength();
            boolean[] values = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? trueValue.getValue(i) : elseValue.getValue(i);
            }
            return LLVMI1Vector.create(values);
        }
    }

    public abstract static class LLVMI8VectorSelectNode extends LLVMVectorSelectNode {
        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doOp(LLVMI1Vector condition, LLVMI8Vector trueValue, LLVMI8Vector elseValue) {
            assert condition.getLength() == getVectorLength();
            byte[] values = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? trueValue.getValue(i) : elseValue.getValue(i);
            }
            return LLVMI8Vector.create(values);
        }
    }

    public abstract static class LLVMI16VectorSelectNode extends LLVMVectorSelectNode {
        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doOp(LLVMI1Vector condition, LLVMI16Vector trueValue, LLVMI16Vector elseValue) {
            assert condition.getLength() == getVectorLength();
            short[] values = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? trueValue.getValue(i) : elseValue.getValue(i);
            }
            return LLVMI16Vector.create(values);
        }
    }

    public abstract static class LLVMI32VectorSelectNode extends LLVMVectorSelectNode {
        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doOp(LLVMI1Vector condition, LLVMI32Vector trueValue, LLVMI32Vector elseValue) {
            assert condition.getLength() == getVectorLength();
            int[] values = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? trueValue.getValue(i) : elseValue.getValue(i);
            }
            return LLVMI32Vector.create(values);
        }
    }

    public abstract static class LLVMI64VectorSelectNode extends LLVMVectorSelectNode {
        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doOp(LLVMI1Vector condition, LLVMI64Vector trueValue, LLVMI64Vector elseValue) {
            assert condition.getLength() == getVectorLength();
            long[] values = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? trueValue.getValue(i) : elseValue.getValue(i);
            }
            return LLVMI64Vector.create(values);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMPointerVector doOp(LLVMI1Vector condition, LLVMI64Vector trueValue, LLVMPointerVector elseValue) {
            assert condition.getLength() == getVectorLength();
            LLVMPointer[] values = new LLVMPointer[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? LLVMNativePointer.create(trueValue.getValue(i)) : elseValue.getValue(i);
            }
            return LLVMPointerVector.create(values);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMPointerVector doOp(LLVMI1Vector condition, LLVMPointerVector trueValue, LLVMI64Vector elseValue) {
            assert condition.getLength() == getVectorLength();
            LLVMPointer[] values = new LLVMPointer[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? trueValue.getValue(i) : LLVMNativePointer.create(elseValue.getValue(i));
            }
            return LLVMPointerVector.create(values);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMPointerVector doOp(LLVMI1Vector condition, LLVMPointerVector trueValue, LLVMPointerVector elseValue) {
            assert condition.getLength() == getVectorLength();
            LLVMPointer[] values = new LLVMPointer[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? trueValue.getValue(i) : elseValue.getValue(i);
            }
            return LLVMPointerVector.create(values);
        }
    }

    public abstract static class LLVMFloatVectorSelectNode extends LLVMVectorSelectNode {
        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doOp(LLVMI1Vector condition, LLVMFloatVector trueValue, LLVMFloatVector elseValue) {
            assert condition.getLength() == getVectorLength();
            float[] values = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? trueValue.getValue(i) : elseValue.getValue(i);
            }
            return LLVMFloatVector.create(values);
        }
    }

    public abstract static class LLVMDoubleVectorSelectNode extends LLVMVectorSelectNode {
        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doOp(LLVMI1Vector condition, LLVMDoubleVector trueValue, LLVMDoubleVector elseValue) {
            assert condition.getLength() == getVectorLength();
            double[] values = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                values[i] = conditionProfile.profile(condition.getValue(i)) ? trueValue.getValue(i) : elseValue.getValue(i);
            }
            return LLVMDoubleVector.create(values);
        }
    }
}
