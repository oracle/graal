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
package com.oracle.truffle.llvm.nodes.others;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMAddressVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMVectorSelectNode {

    @NodeChildren({
                    @NodeChild(value = "conditionNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "trueNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "elseNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMI1VectorSelectNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI1Vector execute(LLVMI1Vector condition, LLVMI1Vector trueValue, LLVMI1Vector elseValue) {
            int length = condition.getLength();

            boolean[] values = new boolean[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI1Vector.create(values);
        }

    }

    @NodeChildren({
                    @NodeChild(value = "conditionNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "trueNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "elseNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMI8VectorSelectNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI8Vector execute(LLVMI1Vector condition, LLVMI8Vector trueValue, LLVMI8Vector elseValue) {
            int length = condition.getLength();

            byte[] values = new byte[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI8Vector.create(values);
        }

    }

    @NodeChildren({
                    @NodeChild(value = "conditionNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "trueNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "elseNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMI16VectorSelectNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI16Vector execute(LLVMI1Vector condition, LLVMI16Vector trueValue, LLVMI16Vector elseValue) {
            int length = condition.getLength();

            short[] values = new short[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI16Vector.create(values);
        }

    }

    @NodeChildren({
                    @NodeChild(value = "conditionNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "trueNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "elseNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMI32VectorSelectNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI32Vector execute(LLVMI1Vector condition, LLVMI32Vector trueValue, LLVMI32Vector elseValue) {
            int length = condition.getLength();

            int[] values = new int[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI32Vector.create(values);
        }

    }

    @NodeChildren({
                    @NodeChild(value = "conditionNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "trueNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "elseNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMI64VectorSelectNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI64Vector execute(LLVMI1Vector condition, LLVMI64Vector trueValue, LLVMI64Vector elseValue) {
            int length = condition.getLength();

            long[] values = new long[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI64Vector.create(values);
        }

    }

    @NodeChildren({
                    @NodeChild(value = "conditionNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "trueNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "elseNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMFloatVectorSelectNode extends LLVMExpressionNode {

        @Specialization
        public LLVMFloatVector execute(LLVMI1Vector condition, LLVMFloatVector trueValue, LLVMFloatVector elseValue) {
            int length = condition.getLength();

            float[] values = new float[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMFloatVector.create(values);
        }

    }

    @NodeChildren({
                    @NodeChild(value = "conditionNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "trueNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "elseNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMDoubleVectorSelectNode extends LLVMExpressionNode {

        @Specialization
        public LLVMDoubleVector execute(LLVMI1Vector condition, LLVMDoubleVector trueValue, LLVMDoubleVector elseValue) {
            int length = condition.getLength();

            double[] values = new double[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMDoubleVector.create(values);
        }

    }

    @NodeChildren({
                    @NodeChild(value = "conditionNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "trueNode", type = LLVMExpressionNode.class),
                    @NodeChild(value = "elseNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAddressVectorSelectNode extends LLVMExpressionNode {

        @Specialization
        public LLVMAddressVector execute(LLVMI1Vector condition, LLVMAddressVector trueValue, LLVMAddressVector elseValue) {
            int length = condition.getLength();

            long[] values = new long[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMAddressVector.create(values);
        }

    }

}
