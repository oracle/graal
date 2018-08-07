/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMX86_VectorMathNode {

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMX86_VectorSquareRootNode extends LLVMBuiltin { // mm_sqrt_(s|p)d
        @Specialization(guards = "vector.getLength() == 2")
        protected LLVMDoubleVector doM128(LLVMDoubleVector vector) {
            return LLVMDoubleVector.create(new double[]{Math.sqrt(vector.getValue(0)), Math.sqrt(vector.getValue(1))});
        }

        @Specialization(guards = "vector.getLength() == 4")
        protected LLVMDoubleVector doM256(LLVMDoubleVector vector) {
            return LLVMDoubleVector.create(new double[]{
                            Math.sqrt(vector.getValue(0)), Math.sqrt(vector.getValue(1)),
                            Math.sqrt(vector.getValue(2)), Math.sqrt(vector.getValue(3))
            });
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMX86_VectorMaxNode extends LLVMBuiltin { // mm256_max_pd
        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doM128(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            Math.max(v1.getValue(0), v2.getValue(0)),
                            Math.max(v1.getValue(1), v2.getValue(1))
            });
        }

        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMDoubleVector doM256(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            Math.max(v1.getValue(0), v2.getValue(0)),
                            Math.max(v1.getValue(1), v2.getValue(1)),
                            Math.max(v1.getValue(2), v2.getValue(2)),
                            Math.max(v1.getValue(3), v2.getValue(3))
            });
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMX86_VectorMinNode extends LLVMBuiltin { // mm256_min_pd
        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doM128(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            Math.min(v1.getValue(0), v2.getValue(0)),
                            Math.min(v1.getValue(1), v2.getValue(1))
            });
        }

        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMDoubleVector doM256(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            Math.min(v1.getValue(0), v2.getValue(0)),
                            Math.min(v1.getValue(1), v2.getValue(1)),
                            Math.min(v1.getValue(2), v2.getValue(2)),
                            Math.min(v1.getValue(3), v2.getValue(3))
            });
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMX86_VectorPackNode extends LLVMBuiltin {
        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMI16Vector doPacksswd(LLVMI32Vector v1, LLVMI32Vector v2) {
            // _mm_packs_epi32
            return LLVMI16Vector.create(new short[]{
                            (short) v1.getValue(0), (short) v1.getValue(1), (short) v1.getValue(2), (short) v1.getValue(3),
                            (short) v2.getValue(0), (short) v2.getValue(1), (short) v2.getValue(2), (short) v2.getValue(3)
            });
        }

        @Specialization(guards = {"v1.getLength() == 8", "v2.getLength() == 8"})
        protected LLVMI8Vector doPacksswb(LLVMI16Vector v1, LLVMI16Vector v2) {
            // _mm_packs_epi16
            return LLVMI8Vector.create(new byte[]{
                            (byte) v1.getValue(0), (byte) v1.getValue(1), (byte) v1.getValue(2), (byte) v1.getValue(3),
                            (byte) v1.getValue(4), (byte) v1.getValue(5), (byte) v1.getValue(6), (byte) v1.getValue(7),
                            (byte) v2.getValue(0), (byte) v2.getValue(1), (byte) v2.getValue(2), (byte) v2.getValue(3),
                            (byte) v2.getValue(4), (byte) v2.getValue(5), (byte) v2.getValue(6), (byte) v2.getValue(7),
            });
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMX86_VectorCmpNode extends LLVMBuiltin { // mm_cmp_sd
        private static double mask = Double.longBitsToDouble(0xffffffffffffffffL);

        /**
         * Compare <tt>
         * #define _CMP_EQ_OQ    0x00 (* Equal (ordered, non-signaling)  *)
         * #define _CMP_LT_OS    0x01 (* Less-than (ordered, signaling)  *)
         * #define _CMP_LE_OS    0x02 (* Less-than-or-equal (ordered, signaling)  *)
         * #define _CMP_UNORD_Q  0x03 (* Unordered (non-signaling)  *)
         * #define _CMP_NEQ_UQ   0x04 (* Not-equal (unordered, non-signaling)  *)
         * #define _CMP_NLT_US   0x05 (* Not-less-than (unordered, signaling)  *)
         * #define _CMP_NLE_US   0x06 (* Not-less-than-or-equal (unordered, signaling)  *)
         * #define _CMP_ORD_Q    0x07 (* Ordered (nonsignaling)   *)
         * #define _CMP_EQ_UQ    0x08 (* Equal (unordered, non-signaling)  *)
         * #define _CMP_NGE_US   0x09 (* Not-greater-than-or-equal (unord, signaling)  *)
         * #define _CMP_NGT_US   0x0a (* Not-greater-than (unordered, signaling)  *)
         * #define _CMP_FALSE_OQ 0x0b (* False (ordered, non-signaling)  *)
         * #define _CMP_NEQ_OQ   0x0c (* Not-equal (ordered, non-signaling)  *)
         * #define _CMP_GE_OS    0x0d (* Greater-than-or-equal (ordered, signaling)  *)
         * #define _CMP_GT_OS    0x0e (* Greater-than (ordered, signaling)  *)
         * #define _CMP_TRUE_UQ  0x0f (* True (unordered, non-signaling)  *)
         * #define _CMP_EQ_OS    0x10 (* Equal (ordered, signaling)  *)
         * #define _CMP_LT_OQ    0x11 (* Less-than (ordered, non-signaling)  *)
         * #define _CMP_LE_OQ    0x12 (* Less-than-or-equal (ordered, non-signaling)  *)
         * #define _CMP_UNORD_S  0x13 (* Unordered (signaling)  *)
         * #define _CMP_NEQ_US   0x14 (* Not-equal (unordered, signaling)  *)
         * #define _CMP_NLT_UQ   0x15 (* Not-less-than (unordered, non-signaling)  *)
         * #define _CMP_NLE_UQ   0x16 (* Not-less-than-or-equal (unord, non-signaling)  *)
         * #define _CMP_ORD_S    0x17 (* Ordered (signaling)  *)
         * #define _CMP_EQ_US    0x18 (* Equal (unordered, signaling)  *)
         * #define _CMP_NGE_UQ   0x19 (* Not-greater-than-or-equal (unord, non-sign)  *)
         * #define _CMP_NGT_UQ   0x1a (* Not-greater-than (unordered, non-signaling)  *)
         * #define _CMP_FALSE_OS 0x1b (* False (ordered, signaling)  *)
         * #define _CMP_NEQ_OS   0x1c (* Not-equal (ordered, signaling)  *)
         * #define _CMP_GE_OQ    0x1d (* Greater-than-or-equal (ordered, non-signaling)  *)
         * #define _CMP_GT_OQ    0x1e (* Greater-than (ordered, non-signaling)  *)
         * #define _CMP_TRUE_US  0x1f (* True (unordered, signaling)  *)
         * </tt>
         */
        @Specialization(guards = {"predicate == 0x00"})
        protected LLVMDoubleVector doEq(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") int predicate) {
            return LLVMDoubleVector.create(new double[]{
                            v1.getValue(0) == v2.getValue(0) ? mask : 0f,
                            v1.getValue(1)
            });
        }

        @Specialization(guards = {"predicate == 0x01"})
        protected LLVMDoubleVector doLt(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") int predicate) {
            return LLVMDoubleVector.create(new double[]{
                            v1.getValue(0) < v2.getValue(0) ? mask : 0f,
                            v1.getValue(1)
            });
        }

        @Specialization(guards = {"predicate == 0x02"})
        protected LLVMDoubleVector doLe(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") int predicate) {
            return LLVMDoubleVector.create(new double[]{
                            v1.getValue(0) <= v2.getValue(0) ? mask : 0f,
                            v1.getValue(1)
            });
        }

        @Specialization(guards = {"predicate == 0x0c"})
        protected LLVMDoubleVector doNe(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") int predicate) {
            return LLVMDoubleVector.create(new double[]{
                            v1.getValue(0) != v2.getValue(0) ? mask : 0f,
                            v1.getValue(1)
            });
        }

        @Specialization(guards = {"predicate == 0x0d"})
        protected LLVMDoubleVector doGe(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") int predicate) {
            return LLVMDoubleVector.create(new double[]{
                            v1.getValue(0) >= v2.getValue(0) ? mask : 0f,
                            v1.getValue(1)
            });
        }

        @Specialization(guards = {"predicate == 0x0e"})
        protected LLVMDoubleVector doGt(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") int predicate) {
            return LLVMDoubleVector.create(new double[]{
                            v1.getValue(0) > v2.getValue(0) ? mask : 0f,
                            v1.getValue(1)
            });
        }
    }
}
