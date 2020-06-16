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
package com.oracle.truffle.llvm.runtime.nodes.literals;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.LLVMRewriteException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToPointerNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMPointerVectorLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

public class LLVMVectorLiteralNode {

    public abstract static class LLVMI1VectorLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;

        public LLVMI1VectorLiteralNode(LLVMExpressionNode[] values) {
            this.values = values;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI1Vector(VirtualFrame frame) {
            boolean[] vals = new boolean[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = LLVMTypesGen.asBoolean(values[i].executeGeneric(frame));
            }
            return LLVMI1Vector.create(vals);
        }
    }

    public abstract static class LLVMI8VectorLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;

        public LLVMI8VectorLiteralNode(LLVMExpressionNode[] values) {
            this.values = values;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI8Vector(VirtualFrame frame) {
            byte[] vals = new byte[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = LLVMTypesGen.asByte(values[i].executeGeneric(frame));
            }
            return LLVMI8Vector.create(vals);
        }
    }

    public abstract static class LLVMI16VectorLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;

        public LLVMI16VectorLiteralNode(LLVMExpressionNode[] values) {
            this.values = values;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI16Vector(VirtualFrame frame) {
            short[] vals = new short[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = LLVMTypesGen.asShort(values[i].executeGeneric(frame));
            }
            return LLVMI16Vector.create(vals);
        }
    }

    public abstract static class LLVMI32VectorLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;

        public LLVMI32VectorLiteralNode(LLVMExpressionNode[] values) {
            this.values = values;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI32Vector(VirtualFrame frame) {
            int[] vals = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = LLVMTypesGen.asInteger(values[i].executeGeneric(frame));
            }
            return LLVMI32Vector.create(vals);
        }
    }

    public abstract static class LLVMI64VectorLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;

        public LLVMI64VectorLiteralNode(LLVMExpressionNode[] values) {
            this.values = values;
        }

        @Specialization(rewriteOn = LLVMRewriteException.class)
        @ExplodeLoop
        protected LLVMI64Vector doI64Vector(VirtualFrame frame) throws LLVMRewriteException {
            try {
                long[] vals = new long[values.length];
                for (int i = 0; i < values.length; i++) {
                    vals[i] = values[i].executeI64(frame);
                }
                return LLVMI64Vector.create(vals);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMRewriteException(e);
            }
        }

        @Specialization
        @ExplodeLoop
        protected LLVMPointerVector doPointerVector(VirtualFrame frame,
                        @Cached("createPointerLiteral()") LLVMPointerVectorLiteralNode pointerLiteral) {
            return LLVMTypesGen.asLLVMPointerVector(pointerLiteral.executeGeneric(frame));
        }

        protected LLVMPointerVectorLiteralNode createPointerLiteral() {
            CompilerAsserts.neverPartOfCompilation();
            return LLVMPointerVectorLiteralNodeGen.create(values);
        }
    }

    public abstract static class LLVMPointerVectorLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;

        public LLVMPointerVectorLiteralNode(LLVMExpressionNode[] values) {
            this.values = values;
        }

        @Specialization(rewriteOn = LLVMRewriteException.class)
        @ExplodeLoop
        protected LLVMPointerVector doPointerVector(VirtualFrame frame) throws LLVMRewriteException {
            try {
                LLVMPointer[] vals = new LLVMPointer[values.length];
                for (int i = 0; i < values.length; i++) {
                    vals[i] = values[i].executeLLVMPointer(frame);
                }
                return LLVMPointerVector.create(vals);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMRewriteException(e);
            }
        }

        @Specialization
        @ExplodeLoop
        protected LLVMPointerVector doMixedVector(VirtualFrame frame,
                        @Cached("createToPointerNodes()") LLVMToPointerNode[] toPointer) {
            LLVMPointer[] vals = new LLVMPointer[values.length];
            for (int i = 0; i < values.length; i++) {
                Object value = values[i].executeGeneric(frame);
                vals[i] = toPointer[i].executeWithTarget(value);
            }
            return LLVMPointerVector.create(vals);
        }

        @TruffleBoundary
        protected LLVMToPointerNode[] createToPointerNodes() {
            LLVMToPointerNode[] result = new LLVMToPointerNode[values.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = LLVMToPointerNodeGen.create();
            }
            return result;
        }
    }

    public abstract static class LLVMFloatVectorLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;

        public LLVMFloatVectorLiteralNode(LLVMExpressionNode[] values) {
            this.values = values;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doFloatVector(VirtualFrame frame) {
            float[] vals = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = LLVMTypesGen.asFloat(values[i].executeGeneric(frame));
            }
            return LLVMFloatVector.create(vals);
        }
    }

    public abstract static class LLVMDoubleVectorLiteralNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] values;

        public LLVMDoubleVectorLiteralNode(LLVMExpressionNode[] values) {
            this.values = values;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVector(VirtualFrame frame) {
            double[] vals = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                vals[i] = LLVMTypesGen.asDouble(values[i].executeGeneric(frame));
            }
            return LLVMDoubleVector.create(vals);
        }
    }
}
