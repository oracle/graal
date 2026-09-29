/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMX86_VectorMathNode {

    // x86 MIN*/MAX* semantics: the hardware computes MIN(a,b) = (a < b) ? a : b and
    // MAX(a,b) = (a > b) ? a : b, so on a NaN operand or a tie it returns the *second*
    // operand b. This differs from Math.min/Math.max (which propagate NaN and order
    // signed zeros). Eigen's pmin/pmax and their Propagate{Numbers,NaN} wrappers rely
    // on this exact "return b on NaN" behavior, so we must model it faithfully.
    private static double x86Min(double a, double b) {
        return a < b ? a : b;
    }

    private static double x86Max(double a, double b) {
        return a > b ? a : b;
    }

    private static float x86Min(float a, float b) {
        return a < b ? a : b;
    }

    private static float x86Max(float a, float b) {
        return a > b ? a : b;
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_SSE2MultiplyHighWordsNode extends LLVMBuiltin {

        @Specialization(guards = {"left.getLength() == 8", "right.getLength() == 8"})
        protected LLVMI16Vector doI16(LLVMI16Vector left, LLVMI16Vector right) {
            short[] result = new short[8];
            for (int i = 0; i < result.length; i++) {
                result[i] = (short) ((left.getValue(i) * right.getValue(i)) >> Short.SIZE);
            }
            return LLVMI16Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_SSE2MultiplyHighUnsignedWordsNode extends LLVMBuiltin {

        @Specialization(guards = {"left.getLength() == 8", "right.getLength() == 8"})
        protected LLVMI16Vector doI16(LLVMI16Vector left, LLVMI16Vector right) {
            short[] result = new short[8];
            for (int i = 0; i < result.length; i++) {
                int product = Short.toUnsignedInt(left.getValue(i)) * Short.toUnsignedInt(right.getValue(i));
                result[i] = (short) (product >>> Short.SIZE);
            }
            return LLVMI16Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorSquareRootNode extends LLVMBuiltin { // mm_sqrt_pd
        @Specialization(guards = "vector.getLength() == 2")
        protected LLVMDoubleVector doM128(LLVMDoubleVector vector) {
            return LLVMDoubleVector.create(new double[]{Math.sqrt(vector.getValue(0)), Math.sqrt(vector.getValue(1))});
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorMaxNode extends LLVMBuiltin { // mm_max_pd, mm256_max_pd
        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doM128(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            x86Max(v1.getValue(0), v2.getValue(0)),
                            x86Max(v1.getValue(1), v2.getValue(1))
            });
        }

        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMDoubleVector doM256(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            x86Max(v1.getValue(0), v2.getValue(0)),
                            x86Max(v1.getValue(1), v2.getValue(1)),
                            x86Max(v1.getValue(2), v2.getValue(2)),
                            x86Max(v1.getValue(3), v2.getValue(3))
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorMaxsdNode extends LLVMBuiltin { // mm_max_sd
        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doM128(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            x86Max(v1.getValue(0), v2.getValue(0)),
                            v1.getValue(1)
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorMinNode extends LLVMBuiltin { // mm_min_pd, mm256_min_pd
        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doM128(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            x86Min(v1.getValue(0), v2.getValue(0)),
                            x86Min(v1.getValue(1), v2.getValue(1))
            });
        }

        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMDoubleVector doM256(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            x86Min(v1.getValue(0), v2.getValue(0)),
                            x86Min(v1.getValue(1), v2.getValue(1)),
                            x86Min(v1.getValue(2), v2.getValue(2)),
                            x86Min(v1.getValue(3), v2.getValue(3))
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorMinsdNode extends LLVMBuiltin { // mm_min_sd
        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doM128(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            return LLVMDoubleVector.create(new double[]{
                            x86Min(v1.getValue(0), v2.getValue(0)),
                            v1.getValue(1)
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_SSE_VectorMaxNode extends LLVMBuiltin { // mm_max_ps, mm256_max_ps
        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMFloatVector doM128(LLVMFloatVector v1, LLVMFloatVector v2) {
            return LLVMFloatVector.create(new float[]{
                            x86Max(v1.getValue(0), v2.getValue(0)),
                            x86Max(v1.getValue(1), v2.getValue(1)),
                            x86Max(v1.getValue(2), v2.getValue(2)),
                            x86Max(v1.getValue(3), v2.getValue(3))
            });
        }

        @Specialization(guards = {"v1.getLength() == 8", "v2.getLength() == 8"})
        protected LLVMFloatVector doM256(LLVMFloatVector v1, LLVMFloatVector v2) {
            float[] result = new float[8];
            for (int i = 0; i < result.length; i++) {
                result[i] = x86Max(v1.getValue(i), v2.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_SSE_VectorMaxsdNode extends LLVMBuiltin { // mm_max_ss
        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMFloatVector doM128(LLVMFloatVector v1, LLVMFloatVector v2) {
            return LLVMFloatVector.create(new float[]{
                            x86Max(v1.getValue(0), v2.getValue(0)),
                            v1.getValue(1),
                            v1.getValue(2),
                            v1.getValue(3)
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_SSE_VectorMinNode extends LLVMBuiltin { // mm_min_ps, mm256_min_ps
        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMFloatVector doM128(LLVMFloatVector v1, LLVMFloatVector v2) {
            return LLVMFloatVector.create(new float[]{
                            x86Min(v1.getValue(0), v2.getValue(0)),
                            x86Min(v1.getValue(1), v2.getValue(1)),
                            x86Min(v1.getValue(2), v2.getValue(2)),
                            x86Min(v1.getValue(3), v2.getValue(3))
            });
        }

        @Specialization(guards = {"v1.getLength() == 8", "v2.getLength() == 8"})
        protected LLVMFloatVector doM256(LLVMFloatVector v1, LLVMFloatVector v2) {
            float[] result = new float[8];
            for (int i = 0; i < result.length; i++) {
                result[i] = x86Min(v1.getValue(i), v2.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_SSE_VectorMinsdNode extends LLVMBuiltin { // mm_min_ss
        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMFloatVector doM128(LLVMFloatVector v1, LLVMFloatVector v2) {
            return LLVMFloatVector.create(new float[]{
                            x86Min(v1.getValue(0), v2.getValue(0)),
                            v1.getValue(1),
                            v1.getValue(2),
                            v1.getValue(3)
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

            ComparatorPredicate pred;
            boolean ordered;

            // TODO: how do we map signaling behavior?
            boolean signaling;

            // Parameter i kept to document matching to intrinsic definition
            Comparator(@SuppressWarnings("unused") int i, ComparatorPredicate pred, boolean ordered, boolean signaling) {
                this.pred = pred;
                this.ordered = ordered;
                this.signaling = signaling;
            }
        }

        @FunctionalInterface
        interface ComparatorPredicate {
            boolean test(int value);
        }

        protected static final int cmpCnt = Comparator.values().length;

        protected static Comparator getComparator(int predicate) {
            return Comparator.values()[predicate];
        }

        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2", "predicate == cachedPredicate"}, limit = "cmpCnt")
        @GenerateAOT.Exclude
        protected LLVMDoubleVector doCmp(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") int predicate,
                        @SuppressWarnings("unused") @Cached("predicate") int cachedPredicate,
                        @Cached("getComparator(predicate)") Comparator comparator) {
            return compare(v1, v2, comparator);
        }

        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2", "predicate == cachedPredicate"}, limit = "cmpCnt")
        @GenerateAOT.Exclude
        protected LLVMDoubleVector doCmp(LLVMDoubleVector v1, LLVMDoubleVector v2, @SuppressWarnings("unused") byte predicate,
                        @SuppressWarnings("unused") @Cached("predicate") byte cachedPredicate,
                        @Cached("getComparator(predicate)") Comparator comparator) {
            return compare(v1, v2, comparator);
        }

        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doCmpAOT(LLVMDoubleVector v1, LLVMDoubleVector v2, int predicate) {
            return compare(v1, v2, getComparator(predicate));
        }

        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doCmpAOT(LLVMDoubleVector v1, LLVMDoubleVector v2, byte predicate) {
            return compare(v1, v2, getComparator(predicate));
        }

        private static LLVMDoubleVector compare(LLVMDoubleVector v1, LLVMDoubleVector v2, Comparator comparator) {
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

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorRoundNode extends LLVMBuiltin { // mm_round_ps/pd, mm256_round_ps/pd
        /*
         * SSE4.1/AVX rounding-control immediate: bit 2 selects the MXCSR rounding mode (Sulong
         * models MXCSR as round-to-nearest-even, the ABI default), otherwise bits 1:0 select
         * 0=nearest-even, 1=down, 2=up, 3=truncate. Bit 3 (suppress precision exception) is
         * irrelevant here.
         */
        private static double round(double value, int roundingControl) {
            int mode = (roundingControl & 0x4) != 0 ? 0 : (roundingControl & 0x3);
            switch (mode) {
                case 0:
                    return Math.rint(value);
                case 1:
                    return Math.floor(value);
                case 2:
                    return Math.ceil(value);
                default:
                    return value < 0 ? Math.ceil(value) : Math.floor(value);
            }
        }

        @Specialization(guards = "vector.getLength() == 2")
        protected LLVMDoubleVector doM128(LLVMDoubleVector vector, int roundingControl) {
            return roundDoubles(vector, roundingControl, 2);
        }

        @Specialization(guards = "vector.getLength() == 4")
        protected LLVMDoubleVector doM256(LLVMDoubleVector vector, int roundingControl) {
            return roundDoubles(vector, roundingControl, 4);
        }

        @Specialization(guards = "vector.getLength() == 4")
        protected LLVMFloatVector doM128(LLVMFloatVector vector, int roundingControl) {
            return roundFloats(vector, roundingControl, 4);
        }

        @Specialization(guards = "vector.getLength() == 8")
        protected LLVMFloatVector doM256(LLVMFloatVector vector, int roundingControl) {
            return roundFloats(vector, roundingControl, 8);
        }

        private static LLVMDoubleVector roundDoubles(LLVMDoubleVector vector, int roundingControl, int length) {
            double[] result = new double[length];
            for (int i = 0; i < length; i++) {
                result[i] = round(vector.getValue(i), roundingControl);
            }
            return LLVMDoubleVector.create(result);
        }

        private static LLVMFloatVector roundFloats(LLVMFloatVector vector, int roundingControl, int length) {
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                result[i] = (float) round(vector.getValue(i), roundingControl);
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorAddSubNode extends LLVMBuiltin { // addsub_ps/pd (sse3, avx)
        // ADDSUBPS/PD: subtract on the even lanes, add on the odd lanes (per Intel SDM). Length-
        // generic so the SSE3 128-bit forms (<4 x float>, <2 x double>) and the AVX 256-bit forms
        // (<8 x float>, <4 x double>) all funnel through the same two specializations.
        @Specialization(guards = "v1.getLength() == v2.getLength()")
        protected LLVMFloatVector doFloat(LLVMFloatVector v1, LLVMFloatVector v2) {
            int len = v1.getLength();
            float[] result = new float[len];
            for (int i = 0; i < len; i++) {
                result[i] = (i & 1) == 0 ? v1.getValue(i) - v2.getValue(i) : v1.getValue(i) + v2.getValue(i);
            }
            return LLVMFloatVector.create(result);
        }

        @Specialization(guards = "v1.getLength() == v2.getLength()")
        protected LLVMDoubleVector doDouble(LLVMDoubleVector v1, LLVMDoubleVector v2) {
            int len = v1.getLength();
            double[] result = new double[len];
            for (int i = 0; i < len; i++) {
                result[i] = (i & 1) == 0 ? v1.getValue(i) - v2.getValue(i) : v1.getValue(i) + v2.getValue(i);
            }
            return LLVMDoubleVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorBlendvNode extends LLVMBuiltin { // blendvpd/blendvps (sse41, avx)
        // Variable blend: take element i from v2 when the most-significant (sign) bit of the
        // corresponding mask element is set, otherwise from v1. Length-generic to cover both the
        // 128-bit (sse41) and 256-bit (avx) forms.
        @Specialization(guards = {"v1.getLength() == v2.getLength()", "v1.getLength() == mask.getLength()"})
        protected LLVMDoubleVector doDouble(LLVMDoubleVector v1, LLVMDoubleVector v2, LLVMDoubleVector mask) {
            int len = v1.getLength();
            double[] result = new double[len];
            for (int i = 0; i < len; i++) {
                result[i] = Double.doubleToRawLongBits(mask.getValue(i)) < 0 ? v2.getValue(i) : v1.getValue(i);
            }
            return LLVMDoubleVector.create(result);
        }

        @Specialization(guards = {"v1.getLength() == v2.getLength()", "v1.getLength() == mask.getLength()"})
        protected LLVMFloatVector doFloat(LLVMFloatVector v1, LLVMFloatVector v2, LLVMFloatVector mask) {
            int len = v1.getLength();
            float[] result = new float[len];
            for (int i = 0; i < len; i++) {
                result[i] = Float.floatToRawIntBits(mask.getValue(i)) < 0 ? v2.getValue(i) : v1.getValue(i);
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorPblendvbNode extends LLVMBuiltin { // pblendvb (sse41, avx2)
        // Per-byte variable blend: take byte i from v2 when the high bit of mask byte i is set,
        // otherwise from v1. Length-generic (16 bytes for sse41, 32 bytes for avx2); no lane
        // crossing so a single flat loop is correct for both.
        @Specialization(guards = {"v1.getLength() == v2.getLength()", "v1.getLength() == mask.getLength()"})
        protected LLVMI8Vector doI8(LLVMI8Vector v1, LLVMI8Vector v2, LLVMI8Vector mask) {
            int len = v1.getLength();
            byte[] result = new byte[len];
            for (int i = 0; i < len; i++) {
                result[i] = mask.getValue(i) < 0 ? v2.getValue(i) : v1.getValue(i);
            }
            return LLVMI8Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorPackusdwNode extends LLVMBuiltin { // packusdw (sse41, avx2)
        // Pack two i32 vectors down to one u16 vector with unsigned saturation. The 256-bit avx2
        // form operates independently on each 128-bit lane, so its output interleaves the two
        // sources per lane rather than concatenating them.
        static short unsignedSaturate(int value) {
            if (value < 0) {
                return 0;
            } else if (value > 0xFFFF) {
                return (short) 0xFFFF;
            } else {
                return (short) value;
            }
        }

        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMI16Vector doPackusdw128(LLVMI32Vector v1, LLVMI32Vector v2) { // _mm_packus_epi32
            return LLVMI16Vector.create(new short[]{
                            unsignedSaturate(v1.getValue(0)), unsignedSaturate(v1.getValue(1)), unsignedSaturate(v1.getValue(2)), unsignedSaturate(v1.getValue(3)),
                            unsignedSaturate(v2.getValue(0)), unsignedSaturate(v2.getValue(1)), unsignedSaturate(v2.getValue(2)), unsignedSaturate(v2.getValue(3))
            });
        }

        @Specialization(guards = {"v1.getLength() == 8", "v2.getLength() == 8"})
        protected LLVMI16Vector doPackusdw256(LLVMI32Vector v1, LLVMI32Vector v2) { // _mm256_packus_epi32 (per 128-bit lane)
            return LLVMI16Vector.create(new short[]{
                            unsignedSaturate(v1.getValue(0)), unsignedSaturate(v1.getValue(1)), unsignedSaturate(v1.getValue(2)), unsignedSaturate(v1.getValue(3)),
                            unsignedSaturate(v2.getValue(0)), unsignedSaturate(v2.getValue(1)), unsignedSaturate(v2.getValue(2)), unsignedSaturate(v2.getValue(3)),
                            unsignedSaturate(v1.getValue(4)), unsignedSaturate(v1.getValue(5)), unsignedSaturate(v1.getValue(6)), unsignedSaturate(v1.getValue(7)),
                            unsignedSaturate(v2.getValue(4)), unsignedSaturate(v2.getValue(5)), unsignedSaturate(v2.getValue(6)), unsignedSaturate(v2.getValue(7))
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorHorizontalAddNode extends LLVMBuiltin { // phadd.d/.w (ssse3, avx2)
        // Horizontal add of adjacent element pairs. The result draws its first half of each
        // 128-bit lane from v1 and its second half from v2 (per Intel SDM); the 256-bit avx2
        // forms repeat that pattern independently in each 128-bit lane.
        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMI32Vector doPhaddD128(LLVMI32Vector v1, LLVMI32Vector v2) { // _mm_hadd_epi32
            return LLVMI32Vector.create(new int[]{
                            v1.getValue(0) + v1.getValue(1), v1.getValue(2) + v1.getValue(3),
                            v2.getValue(0) + v2.getValue(1), v2.getValue(2) + v2.getValue(3)
            });
        }

        @Specialization(guards = {"v1.getLength() == 8", "v2.getLength() == 8"})
        protected LLVMI32Vector doPhaddD256(LLVMI32Vector v1, LLVMI32Vector v2) { // _mm256_hadd_epi32 (per 128-bit lane)
            return LLVMI32Vector.create(new int[]{
                            v1.getValue(0) + v1.getValue(1), v1.getValue(2) + v1.getValue(3),
                            v2.getValue(0) + v2.getValue(1), v2.getValue(2) + v2.getValue(3),
                            v1.getValue(4) + v1.getValue(5), v1.getValue(6) + v1.getValue(7),
                            v2.getValue(4) + v2.getValue(5), v2.getValue(6) + v2.getValue(7)
            });
        }

        @Specialization(guards = {"v1.getLength() == 8", "v2.getLength() == 8"})
        protected LLVMI16Vector doPhaddW128(LLVMI16Vector v1, LLVMI16Vector v2) { // _mm_hadd_epi16
            return LLVMI16Vector.create(new short[]{
                            (short) (v1.getValue(0) + v1.getValue(1)), (short) (v1.getValue(2) + v1.getValue(3)),
                            (short) (v1.getValue(4) + v1.getValue(5)), (short) (v1.getValue(6) + v1.getValue(7)),
                            (short) (v2.getValue(0) + v2.getValue(1)), (short) (v2.getValue(2) + v2.getValue(3)),
                            (short) (v2.getValue(4) + v2.getValue(5)), (short) (v2.getValue(6) + v2.getValue(7))
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorHorizontalAddFloatNode extends LLVMBuiltin { // hadd.ps/.pd (sse3, avx)
        // Floating-point horizontal add (HADDPS/HADDPD): same adjacent-pair, v1-then-v2-per-lane
        // layout as the integer PHADD, but on float/double lanes. Covers both 128-bit (sse3) and
        // 256-bit (avx) forms; the 256-bit forms operate per 128-bit lane.
        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMFloatVector doHaddPs128(LLVMFloatVector v1, LLVMFloatVector v2) { // _mm_hadd_ps
            return LLVMFloatVector.create(new float[]{
                            v1.getValue(0) + v1.getValue(1), v1.getValue(2) + v1.getValue(3),
                            v2.getValue(0) + v2.getValue(1), v2.getValue(2) + v2.getValue(3)
            });
        }

        @Specialization(guards = {"v1.getLength() == 8", "v2.getLength() == 8"})
        protected LLVMFloatVector doHaddPs256(LLVMFloatVector v1, LLVMFloatVector v2) { // _mm256_hadd_ps (per 128-bit lane)
            return LLVMFloatVector.create(new float[]{
                            v1.getValue(0) + v1.getValue(1), v1.getValue(2) + v1.getValue(3),
                            v2.getValue(0) + v2.getValue(1), v2.getValue(2) + v2.getValue(3),
                            v1.getValue(4) + v1.getValue(5), v1.getValue(6) + v1.getValue(7),
                            v2.getValue(4) + v2.getValue(5), v2.getValue(6) + v2.getValue(7)
            });
        }

        @Specialization(guards = {"v1.getLength() == 2", "v2.getLength() == 2"})
        protected LLVMDoubleVector doHaddPd128(LLVMDoubleVector v1, LLVMDoubleVector v2) { // _mm_hadd_pd
            return LLVMDoubleVector.create(new double[]{
                            v1.getValue(0) + v1.getValue(1),
                            v2.getValue(0) + v2.getValue(1)
            });
        }

        @Specialization(guards = {"v1.getLength() == 4", "v2.getLength() == 4"})
        protected LLVMDoubleVector doHaddPd256(LLVMDoubleVector v1, LLVMDoubleVector v2) { // _mm256_hadd_pd (per 128-bit lane)
            return LLVMDoubleVector.create(new double[]{
                            v1.getValue(0) + v1.getValue(1),
                            v2.getValue(0) + v2.getValue(1),
                            v1.getValue(2) + v1.getValue(3),
                            v2.getValue(2) + v2.getValue(3)
            });
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_VectorRsqrtNode extends LLVMBuiltin { // mm_rsqrt_ps, mm256_rsqrt_ps
        /*
         * Hardware rsqrt is an approximation with |relative error| <= 1.5 * 2^-12; the exact
         * reciprocal square root computed here is within that bound but not bit-identical to any
         * real CPU. Tests must compare with a tolerance.
         */
        @Specialization(guards = "vector.getLength() == 4")
        protected LLVMFloatVector doM128(LLVMFloatVector vector) {
            return rsqrt(vector, 4);
        }

        @Specialization(guards = "vector.getLength() == 8")
        protected LLVMFloatVector doM256(LLVMFloatVector vector) {
            return rsqrt(vector, 8);
        }

        private static LLVMFloatVector rsqrt(LLVMFloatVector vector, int length) {
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                result[i] = (float) (1.0 / Math.sqrt(vector.getValue(i)));
            }
            return LLVMFloatVector.create(result);
        }
    }
}
