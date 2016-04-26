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
package com.oracle.truffle.llvm.nodes.impl.others;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMDoubleVectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI16VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI1VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI64VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.types.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.types.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI8Vector;

public abstract class LLVMVectorSelectNode {

    @NodeChildren({
        @NodeChild(value = "addressNode", type = LLVMAddressNode.class),
        @NodeChild(value = "conditionNode", type = LLVMI1VectorNode.class),
        @NodeChild(value = "trueNode", type = LLVMI1VectorNode.class),
        @NodeChild(value = "elseNode", type = LLVMI1VectorNode.class)})
    public abstract static class LLVMI1VectorSelectNode extends LLVMI1VectorNode {

        @Specialization
        public LLVMI1Vector execute(LLVMAddress target, LLVMI1Vector condition, LLVMI1Vector trueValue, LLVMI1Vector elseValue) {
            int length = condition.getLength();

            boolean[] values = new boolean[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI1Vector.fromI1Array(target, values);
        }

    }

    @NodeChildren({
        @NodeChild(value = "addressNode", type = LLVMAddressNode.class),
        @NodeChild(value = "conditionNode", type = LLVMI1VectorNode.class),
        @NodeChild(value = "trueNode", type = LLVMI8VectorNode.class),
        @NodeChild(value = "elseNode", type = LLVMI8VectorNode.class)})
    public abstract static class LLVMI8VectorSelectNode extends LLVMI8VectorNode {

        @Specialization
        public LLVMI8Vector execute(LLVMAddress target, LLVMI1Vector condition, LLVMI8Vector trueValue, LLVMI8Vector elseValue) {
            int length = condition.getLength();

            byte[] values = new byte[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI8Vector.fromI8Array(target, values);
        }

    }

    @NodeChildren({
        @NodeChild(value = "addressNode", type = LLVMAddressNode.class),
        @NodeChild(value = "conditionNode", type = LLVMI1VectorNode.class),
        @NodeChild(value = "trueNode", type = LLVMI16VectorNode.class),
        @NodeChild(value = "elseNode", type = LLVMI16VectorNode.class)})
    public abstract static class LLVMI16VectorSelectNode extends LLVMI16VectorNode {

        @Specialization
        public LLVMI16Vector execute(LLVMAddress target, LLVMI1Vector condition, LLVMI16Vector trueValue, LLVMI16Vector elseValue) {
            int length = condition.getLength();

            short[] values = new short[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI16Vector.fromI16Array(target, values);
        }

    }

    @NodeChildren({
        @NodeChild(value = "addressNode", type = LLVMAddressNode.class),
        @NodeChild(value = "conditionNode", type = LLVMI1VectorNode.class),
        @NodeChild(value = "trueNode", type = LLVMI32VectorNode.class),
        @NodeChild(value = "elseNode", type = LLVMI32VectorNode.class)})
    public abstract static class LLVMI32VectorSelectNode extends LLVMI32VectorNode {

        @Specialization
        public LLVMI32Vector execute(LLVMAddress target, LLVMI1Vector condition, LLVMI32Vector trueValue, LLVMI32Vector elseValue) {
            int length = condition.getLength();

            int[] values = new int[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI32Vector.fromI32Array(target, values);
        }

    }

    @NodeChildren({
        @NodeChild(value = "addressNode", type = LLVMAddressNode.class),
        @NodeChild(value = "conditionNode", type = LLVMI1VectorNode.class),
        @NodeChild(value = "trueNode", type = LLVMI64VectorNode.class),
        @NodeChild(value = "elseNode", type = LLVMI64VectorNode.class)})
    public abstract static class LLVMI64VectorSelectNode extends LLVMI64VectorNode {

        @Specialization
        public LLVMI64Vector execute(LLVMAddress target, LLVMI1Vector condition, LLVMI64Vector trueValue, LLVMI64Vector elseValue) {
            int length = condition.getLength();

            long[] values = new long[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMI64Vector.fromI64Array(target, values);
        }

    }

    @NodeChildren({
        @NodeChild(value = "addressNode", type = LLVMAddressNode.class),
        @NodeChild(value = "conditionNode", type = LLVMI1VectorNode.class),
        @NodeChild(value = "trueNode", type = LLVMFloatVectorNode.class),
        @NodeChild(value = "elseNode", type = LLVMFloatVectorNode.class)})
    public abstract static class LLVMFloatVectorSelectNode extends LLVMFloatVectorNode {

        @Specialization
        public LLVMFloatVector execute(LLVMAddress target, LLVMI1Vector condition, LLVMFloatVector trueValue, LLVMFloatVector elseValue) {
            int length = condition.getLength();

            float[] values = new float[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMFloatVector.fromFloatArray(target, values);
        }

    }

    @NodeChildren({
        @NodeChild(value = "addressNode", type = LLVMAddressNode.class),
        @NodeChild(value = "conditionNode", type = LLVMI1VectorNode.class),
        @NodeChild(value = "trueNode", type = LLVMDoubleVectorNode.class),
        @NodeChild(value = "elseNode", type = LLVMDoubleVectorNode.class)})
    public abstract static class LLVMDoubleVectorSelectNode extends LLVMDoubleVectorNode {

        @Specialization
        public LLVMDoubleVector execute(LLVMAddress target, LLVMI1Vector condition, LLVMDoubleVector trueValue, LLVMDoubleVector elseValue) {
            int length = condition.getLength();

            double[] values = new double[length];

            for (int i = 0; i < length; i++) {
                values[i] = condition.getValue(i) ? trueValue.getValue(i) : elseValue.getValue(i);
            }

            return LLVMDoubleVector.fromDoubleArray(target, values);
        }

    }

}
