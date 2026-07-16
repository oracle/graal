/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

public abstract class LLVMAdditionalIntrinsics {

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSetRoundingNode extends LLVMBuiltin {

        @Specialization
        protected Object doSet(int roundingMode) {
            getLanguage().setRoundingMode(roundingMode);
            return null;
        }
    }

    public abstract static class LLVMGetRoundingNode extends LLVMBuiltin {

        @Specialization
        protected int doGet() {
            return getLanguage().getRoundingMode();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "signed", type = boolean.class)
    public abstract static class LLVMIntegerCompareNode extends LLVMBuiltin {

        protected abstract boolean isSigned();

        @Specialization
        protected int doI32(int left, int right) {
            return isSigned() ? Integer.compare(left, right) : Integer.compareUnsigned(left, right);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMCountTrailingElementsNode extends LLVMBuiltin {

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected long doI1(LLVMI1Vector value, @SuppressWarnings("unused") boolean zeroIsPoison) {
            assert value.getLength() == getVectorLength();
            for (int i = 0; i < getVectorLength(); i++) {
                if (value.getValue(i)) {
                    return i;
                }
            }
            return getVectorLength();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class LLVMSaturatingFptoSINode extends LLVMBuiltin {

        protected abstract int getVectorLength();

        protected abstract int getBitWidth();

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doDouble(LLVMDoubleVector value) {
            assert value.getLength() == getVectorLength();
            assert getBitWidth() > 1 && getBitWidth() <= Short.SIZE;
            int min = -(1 << (getBitWidth() - 1));
            int max = (1 << (getBitWidth() - 1)) - 1;
            short[] result = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                double element = value.getValue(i);
                if (Double.isNaN(element)) {
                    result[i] = 0;
                } else if (element <= min) {
                    result[i] = (short) min;
                } else if (element >= max) {
                    result[i] = (short) max;
                } else {
                    result[i] = (short) element;
                }
            }
            return LLVMI16Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedGatherI32Node extends LLVMBuiltin {

        @Child private LLVMI32LoadNode load = LLVMI32LoadNode.create();

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doGather(LLVMPointerVector pointers, LLVMI1Vector mask, LLVMI32Vector passthrough) {
            assert pointers.getLength() == getVectorLength();
            assert mask.getLength() == getVectorLength();
            assert passthrough.getLength() == getVectorLength();
            int[] result = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = mask.getValue(i) ? load.executeWithTarget(pointers.getValue(i)) : passthrough.getValue(i);
            }
            return LLVMI32Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedScatterI32Node extends LLVMBuiltin {

        @Child private LLVMI32StoreNode store = LLVMI32StoreNodeGen.create(null, null);

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected Object doScatter(LLVMI32Vector values, LLVMPointerVector pointers, LLVMI1Vector mask) {
            assert values.getLength() == getVectorLength();
            assert pointers.getLength() == getVectorLength();
            assert mask.getLength() == getVectorLength();
            for (int i = 0; i < getVectorLength(); i++) {
                if (mask.getValue(i)) {
                    store.executeWithTarget(pointers.getValue(i), values.getValue(i));
                }
            }
            return null;
        }
    }
}
