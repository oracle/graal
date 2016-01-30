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
package com.oracle.truffle.llvm.nodes.literals;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMDoubleVectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI16VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI1VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI64VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.types.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.types.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI8Vector;

public class LLVMVectorLiteralNode {

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMVectorI1LiteralNode extends LLVMI1VectorNode {

        @Children private final LLVMI1Node[] values;

        public LLVMVectorI1LiteralNode(LLVMI1Node[] values) {
            this.values = values;
        }

        @ExplodeLoop
        @Specialization
        public LLVMI1Vector executeI1Vector(VirtualFrame frame, LLVMAddress target) {
            boolean[] vals = new boolean[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = values[i].executeI1(frame);
            }
            return LLVMI1Vector.fromI1Array(target, vals);
        }

    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMVectorI8LiteralNode extends LLVMI8VectorNode {

        @Children private final LLVMI8Node[] values;

        public LLVMVectorI8LiteralNode(LLVMI8Node[] values) {
            this.values = values;
        }

        @ExplodeLoop
        @Specialization
        public LLVMI8Vector executeI8Vector(VirtualFrame frame, LLVMAddress target) {
            byte[] vals = new byte[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = values[i].executeI8(frame);
            }
            return LLVMI8Vector.fromI8Array(target, vals);
        }

    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMVectorI16LiteralNode extends LLVMI16VectorNode {

        @Children private final LLVMI16Node[] values;

        public LLVMVectorI16LiteralNode(LLVMI16Node[] values) {
            this.values = values;
        }

        @ExplodeLoop
        @Specialization
        public LLVMI16Vector executeI16Vector(VirtualFrame frame, LLVMAddress target) {
            short[] vals = new short[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = values[i].executeI16(frame);
            }
            return LLVMI16Vector.fromI16Array(target, vals);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMVectorI32LiteralNode extends LLVMI32VectorNode {

        @Children private final LLVMI32Node[] values;

        public LLVMVectorI32LiteralNode(LLVMI32Node[] values) {
            this.values = values;
        }

        @ExplodeLoop
        @Specialization
        public LLVMI32Vector executeI32Vector(VirtualFrame frame, LLVMAddress target) {
            int[] vals = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = values[i].executeI32(frame);
            }
            return LLVMI32Vector.fromI32Array(target, vals);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMVectorI64LiteralNode extends LLVMI64VectorNode {

        @Children private final LLVMI64Node[] values;

        public LLVMVectorI64LiteralNode(LLVMI64Node[] values) {
            this.values = values;
        }

        @ExplodeLoop
        @Specialization
        public LLVMI64Vector executeI64Vector(VirtualFrame frame, LLVMAddress target) {
            long[] vals = new long[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = values[i].executeI64(frame);
            }
            return LLVMI64Vector.fromI64Array(target, vals);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMVectorFloatLiteralNode extends LLVMFloatVectorNode {

        @Children private final LLVMFloatNode[] values;

        public LLVMVectorFloatLiteralNode(LLVMFloatNode[] values) {
            this.values = values;
        }

        @ExplodeLoop
        @Specialization
        public LLVMFloatVector executeFloatVector(VirtualFrame frame, LLVMAddress target) {
            float[] vals = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = values[i].executeFloat(frame);
            }
            return LLVMFloatVector.fromFloatArray(target, vals);
        }

    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMVectorDoubleLiteralNode extends LLVMDoubleVectorNode {

        @Children private final LLVMDoubleNode[] values;

        public LLVMVectorDoubleLiteralNode(LLVMDoubleNode[] values) {
            this.values = values;
        }

        @ExplodeLoop
        @Specialization
        public LLVMDoubleVector executeDoubleVector(VirtualFrame frame, LLVMAddress target) {
            double[] vals = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = values[i].executeDouble(frame);
            }
            return LLVMDoubleVector.fromDoubleArray(target, vals);
        }

    }

}
