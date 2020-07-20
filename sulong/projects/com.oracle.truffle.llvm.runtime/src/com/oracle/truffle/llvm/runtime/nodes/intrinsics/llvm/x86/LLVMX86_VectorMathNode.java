/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86;

import java.util.function.IntPredicate;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMX86_VectorMathNode {

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorSquareRootNode extends LLVMBuiltin { // mm_sqrt_pd
        @Specialization(guards = "vector.getLength() == 2")
        protected LLVMDoubleVector doM128(LLVMDoubleVector vector) {
            return LLVMDoubleVector.create(new double[]{Math.sqrt(vector.getValue(0)), Math.sqrt(vector.getValue(1))});
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorMaxNode extends LLVMBuiltin { // mm_max_pd
        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doM128(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            Math.max(v1.getValue(0), v2.getValue(0)),
                            Math.max(v1.getValue(1), v2.getValue(1))
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorMaxsdNode extends LLVMBuiltin { // mm_max_sd
        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doM128(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            Math.max(v1.getValue(0), v2.getValue(0)),
                            v1.getValue(1)
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorMinNode extends LLVMBuiltin { // mm_min_pd
        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doM128(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            Math.min(v1.getValue(0), v2.getValue(0)),
                            Math.min(v1.getValue(1), v2.getValue(1))
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorPackNode extends LLVMBuiltin {
        short saturatedPack(int value) {
            if (value > Short.MAX_VALUE) {
                return Short.MAX_VALUE;
            } else if (value < Short.MIN_VALUE) {
                return Short.MIN_VALUE;
            } else {
                return (short) value;
            }
        }

        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMI16Vector doPacksswd(LLVMI32Vector v1, LLVMI32Vector v2) { // _mm_packs_epi32
            return LLVMI16Vector.create(new short[]{
                            saturatedPack(v1.getValue(0)), saturatedPack(v1.getValue(1)), saturatedPack(v1.getValue(2)), saturatedPack(v1.getValue(3)),
                            saturatedPack(v2.getValue(0)), saturatedPack(v2.getValue(1)), saturatedPack(v2.getValue(2)), saturatedPack(v2.getValue(3))
            });
        }

        byte saturatedPack(short value) {
            if (value > Byte.MAX_VALUE) {
                return Byte.MAX_VALUE;
            } else if (value < Byte.MIN_VALUE) {
                return Byte.MIN_VALUE;
            } else {
                return (byte) value;
            }
        }

        @Specialization(guards = {"v1.getLength() == 8", "v2.getLength() == 8"})
        protected LLVMI8Vector doPacksswb(LLVMI16Vector v1, LLVMI16Vector v2) { // _mm_packs_epi16
            return LLVMI8Vector.create(new byte[]{
                            saturatedPack(v1.getValue(0)), saturatedPack(v1.getValue(1)), saturatedPack(v1.getValue(2)), saturatedPack(v1.getValue(3)),
                            saturatedPack(v1.getValue(4)), saturatedPack(v1.getValue(5)), saturatedPack(v1.getValue(6)), saturatedPack(v1.getValue(7)),
                            saturatedPack(v2.getValue(0)), saturatedPack(v2.getValue(1)), saturatedPack(v2.getValue(2)), saturatedPack(v2.getValue(3)),
                            saturatedPack(v2.getValue(4)), saturatedPack(v2.getValue(5)), saturatedPack(v2.getValue(6)), saturatedPack(v2.getValue(7))
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorCmpNode extends LLVMBuiltin { // mm_cmp_sd
        private static final double mask = Double.longBitsToDouble(0xffffffffffffffffL);

        protected enum Comparator {
            _CMP_EQ_OQ(0x00, cmp -> cmp == 0, true, false),
            _CMP_LT_OS(0x01, cmp -> cmp < 0, true, true),
            _CMP_LE_OS(0x02, cmp -> cmp <= 0, true, true),
            _CMP_UNORD_Q(0x03, cmp -> true, false, false),
            _CMP_NEQ_UQ(0x04, cmp -> cmp != 0, false, false),
            _CMP_NLT_US(0x05, cmp -> !(cmp < 0), false, true),
            _CMP_NLE_US(0x06, cmp -> !(cmp <= 0), false, true),
            _CMP_ORD_Q(0x07, cmp -> true, true, false),
            _CMP_EQ_UQ(0x08, cmp -> cmp == 0, false, false),
            _CMP_NGE_US(0x09, cmp -> !(cmp >= 0), false, true),
            _CMP_NGT_US(0x0a, cmp -> !(cmp > 0), false, true),
            _CMP_FALSE_OQ(0x0b, cmp -> false, true, false),
            _CMP_NEQ_OQ(0x0c, cmp -> cmp != 0, true, false),
            _CMP_GE_OS(0x0d, cmp -> cmp >= 0, true, true),
            _CMP_GT_OS(0x0e, cmp -> cmp > 0, true, true),
            _CMP_TRUE_UQ(0x0f, cmp -> true, false, false),
            _CMP_EQ_OS(0x10, cmp -> cmp == 0, true, true),
            _CMP_LT_OQ(0x11, cmp -> cmp < 0, true, false),
            _CMP_LE_OQ(0x12, cmp -> cmp <= 0, true, false),
            _CMP_UNORD_S(0x13, cmp -> true, false, true),
            _CMP_NEQ_US(0x14, cmp -> cmp != 0, false, true),
            _CMP_NLT_UQ(0x15, cmp -> !(cmp < 0), false, false),
            _CMP_NLE_UQ(0x16, cmp -> !(cmp <= 0), false, false),
            _CMP_ORD_S(0x17, cmp -> true, true, true),
            _CMP_EQ_US(0x18, cmp -> cmp == 0, false, true),
            _CMP_NGE_UQ(0x19, cmp -> !(cmp >= 0), false, false),
            _CMP_NGT_UQ(0x1a, cmp -> !(cmp > 0), false, false),
            _CMP_FALSE_OS(0x1b, cmp -> false, true, true),
            _CMP_NEQ_OS(0x1c, cmp -> cmp != 0, true, true),
            _CMP_GE_OQ(0x1d, cmp -> cmp >= 0, true, false),
            _CMP_GT_OQ(0x1e, cmp -> cmp > 0, true, false),
            _CMP_TRUE_US(0x1f, cmp -> true, false, true);

            IntPredicate pred;
            boolean ordered;

            // TODO: how do we map signaling behavior?
            boolean signaling;

            // Parameter i kept to document matching to intrinsic definition
            Comparator(@SuppressWarnings("unused") int i, IntPredicate pred, boolean ordered, boolean signaling) {
                this.pred = pred;
                this.ordered = ordered;
                this.signaling = signaling;
            }
        }

        protected static final int cmpCnt = Comparator.values().length;

        protected static Comparator getComparator(int predicate) {
            return Comparator.values()[predicate];
        }

        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2", "predicate == cachedPredicate"}, limit = "cmpCnt")
        protected LLVMDoubleVector doCmp(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") int predicate,
                        @SuppressWarnings("unused") @Cached("predicate") int cachedPredicate,
                        @Cached("getComparator(predicate)") Comparator comparator) {
            double v11 = v1.getValue(0);
            double v21 = v2.getValue(0);
            boolean compareResult = comparator.pred.test(Double.compare(v11, v21));
            if (comparator.ordered) {
                compareResult = !Double.isNaN(v11) && !Double.isNaN(v21) && compareResult;
            } else {
                compareResult = Double.isNaN(v11) || Double.isNaN(v21) || compareResult;
            }
            return LLVMDoubleVector.create(new double[]{compareResult ? mask : 0f, v1.getValue(1)});
        }

        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2", "predicate == cachedPredicate"}, limit = "cmpCnt")
        protected LLVMDoubleVector doCmp(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") byte predicate,
                        @SuppressWarnings("unused") @Cached("predicate") byte cachedPredicate,
                        @Cached("getComparator(predicate)") Comparator comparator) {
            double v11 = v1.getValue(0);
            double v21 = v2.getValue(0);
            boolean compareResult = comparator.pred.test(Double.compare(v11, v21));
            if (comparator.ordered) {
                compareResult = !Double.isNaN(v11) && !Double.isNaN(v21) && compareResult;
            } else {
                compareResult = Double.isNaN(v11) || Double.isNaN(v21) || compareResult;
            }
            return LLVMDoubleVector.create(new double[]{compareResult ? mask : 0f, v1.getValue(1)});
        }
    }
}
