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
package com.oracle.truffle.llvm.nodes.vector;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.types.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI8Vector;

public class LLVMShuffleVectorNode {

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(value = "left"), @NodeChild(value = "right"), @NodeChild(value = "mask", type = LLVMExpressionNode.class)})
    public abstract static class LLVMShuffleI8VectorNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI8Vector executeI8Vector(LLVMAddress addr, LLVMI8Vector leftVector, LLVMI8Vector rightVector, LLVMI32Vector maskVector) {
            byte[] joinedValues = concat(leftVector.getValues(), rightVector.getValues());
            byte[] newValues = new byte[maskVector.getLength()];
            for (int i = 0; i < maskVector.getLength(); i++) {
                int index = maskVector.getValue(i);
                newValues[i] = joinedValues[index];
            }
            return LLVMI8Vector.fromI8Array(addr, newValues);
        }

        private static byte[] concat(byte[] first, byte[] second) {
            byte[] result = new byte[first.length + second.length];
            for (int i = 0; i < first.length; i++) {
                result[i] = first[i];
            }
            for (int i = first.length; i < first.length + second.length; i++) {
                result[i] = second[i - first.length];
            }
            return result;
        }

    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(value = "left"), @NodeChild(value = "right"), @NodeChild(value = "mask")})
    public abstract static class LLVMShuffleI32VectorNode extends LLVMExpressionNode {

        @Specialization
        public LLVMI32Vector executeI32Vector(LLVMAddress addr, LLVMI32Vector leftVector, LLVMI32Vector rightVector, LLVMI32Vector maskVector) {
            int[] joinedValues = concat(leftVector.getValues(), rightVector.getValues());
            int[] newValues = new int[maskVector.getLength()];
            for (int i = 0; i < maskVector.getLength(); i++) {
                int element = maskVector.getValue(i);
                newValues[i] = joinedValues[element];
            }
            return LLVMI32Vector.fromI32Array(addr, newValues);
        }

        private static int[] concat(int[] first, int[] second) {
            int[] result = new int[first.length + second.length];
            for (int i = 0; i < first.length; i++) {
                result[i] = first[i];
            }
            for (int i = first.length; i < first.length + second.length; i++) {
                result[i] = second[i - first.length];
            }
            return result;
        }

    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(value = "left"), @NodeChild(value = "right"), @NodeChild(value = "mask")})
    public abstract static class LLVMShuffleFloatVectorNode extends LLVMExpressionNode {

        @Specialization
        public LLVMFloatVector execute(LLVMAddress addr, LLVMFloatVector leftVector, LLVMFloatVector rightVector, LLVMI32Vector maskVector) {
            float[] joinedValues = concat(leftVector.getValues(), rightVector.getValues());
            float[] newValues = new float[maskVector.getLength()];
            for (int i = 0; i < maskVector.getLength(); i++) {
                int element = maskVector.getValue(i);
                newValues[i] = joinedValues[element];
            }
            return LLVMFloatVector.fromFloatArray(addr, newValues);
        }

        private static float[] concat(float[] first, float[] second) {
            float[] result = new float[first.length + second.length];
            for (int i = 0; i < first.length; i++) {
                result[i] = first[i];
            }
            for (int i = first.length; i < first.length + second.length; i++) {
                result[i] = second[i - first.length];
            }
            return result;
        }
    }

}
