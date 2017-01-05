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
package com.oracle.truffle.llvm.nodes.cast;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMToI8VectorNode extends LLVMExpressionNode {

    @NodeChildren({@NodeChild(value = "addressNode", type = LLVMExpressionNode.class), @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMI16VectorToI8VectorNode extends LLVMToI8VectorNode {

        @Specialization
        public LLVMI8Vector executeI8Vector(LLVMAddress target, LLVMI16Vector from) {
            return getI8(target, from);
        }

        @ExplodeLoop
        private static LLVMI8Vector getI8(LLVMAddress target, LLVMI16Vector from) {
            int length = from.getLength();

            byte[] values = new byte[length];

            for (int i = 0; i < length; i++) {
                values[i] = (byte) from.getValue(i);
            }

            return LLVMI8Vector.fromI8Array(target, values);
        }
    }

    @NodeChildren({@NodeChild(value = "addressNode", type = LLVMExpressionNode.class), @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMI32VectorToI8VectorNode extends LLVMToI8VectorNode {

        @Specialization
        public LLVMI8Vector executeI8Vector(LLVMAddress target, LLVMI32Vector from) {
            return getI8(target, from);
        }

        @ExplodeLoop
        private static LLVMI8Vector getI8(LLVMAddress target, LLVMI32Vector from) {
            int length = from.getLength();

            byte[] values = new byte[length];

            for (int i = 0; i < length; i++) {
                values[i] = (byte) from.getValue(i);
            }

            return LLVMI8Vector.fromI8Array(target, values);
        }
    }

    @NodeChildren({@NodeChild(value = "addressNode", type = LLVMExpressionNode.class), @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)})
    public abstract static class LLVMI64VectorToI8VectorNode extends LLVMToI8VectorNode {

        @Specialization
        public LLVMI8Vector executeI8Vector(LLVMAddress target, LLVMI64Vector from) {
            return getI8(target, from);
        }

        @ExplodeLoop
        private static LLVMI8Vector getI8(LLVMAddress target, LLVMI64Vector from) {
            int length = from.getLength();

            byte[] values = new byte[length];

            for (int i = 0; i < length; i++) {
                values[i] = (byte) from.getValue(i);
            }

            return LLVMI8Vector.fromI8Array(target, values);
        }
    }
}
