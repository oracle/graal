/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.api;

import com.oracle.truffle.api.CompilerDirectives;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;
import org.graalvm.wasm.constants.Bytecode;

import java.util.function.Function;

import static org.graalvm.wasm.api.Vector128.BYTES;

/**
 * This is a JDK25-specific implementation of the GraalWasm SIMD proposal. It uses the {@link Vector
 * Vector API} to implement the SIMD operations. The Vector API calls are compiled by the Graal
 * compiler to hardware SIMD instructions. The {@code v128} WebAssembly values are represented as
 * {@code Byte128Vector}s on the GraalWasm stack. If this implementation is not available, GraalWasm
 * falls back to {@link Vector128OpsFallback}.
 */
final class Vector128OpsVectorAPI implements Vector128Ops<ByteVector> {

    private static final Vector128Ops<byte[]> fallbackOps = Vector128OpsFallback.create();

    static Vector128Ops<ByteVector> create() {
        return new Vector128OpsVectorAPI();
    }

    private abstract static class Shape<E> {

        public final VectorShuffle<E> compressEvensShuffle = VectorShuffle.fromOp(species(), i -> (i * 2) % species().length());
        public final VectorMask<E> lowMask = VectorMask.fromLong(species(), (1L << (species().length() / 2)) - 1);
        public final VectorMask<E> highMask = VectorMask.fromLong(species(), ((1L << (species().length() / 2)) - 1) << (species().length() / 2));
        public final VectorMask<E> evensMask;
        public final VectorMask<E> oddsMask;

        protected Shape() {
            boolean[] values = new boolean[species().length() + 1];
            for (int i = 0; i < values.length; i++) {
                values[i] = i % 2 == 0;
            }
            evensMask = species().loadMask(values, 0);
            oddsMask = species().loadMask(values, 1);
        }

        public abstract Vector<E> reinterpret(ByteVector bytes);

        public abstract VectorSpecies<E> species();

        public Vector<E> zero() {
            return species().zero();
        }

        public Vector<E> broadcast(long e) {
            return species().broadcast(e);
        }

        /**
         * This is used by floating-point Shapes to be able to broadcast -0.0, which cannot be
         * faithfully represented as a long.
         */
        public Vector<E> broadcast(@SuppressWarnings("unused") double e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static final class I8X16Shape extends Shape<Byte> {

        private I8X16Shape() {
        }

        @Override
        public ByteVector reinterpret(ByteVector bytes) {
            return castByte128(bytes);
        }

        @Override
        public VectorSpecies<Byte> species() {
            return ByteVector.SPECIES_128;
        }

        @Override
        public ByteVector zero() {
            return castByte128(ByteVector.zero(species()));
        }

        @Override
        public ByteVector broadcast(long e) {
            return castByte128(ByteVector.broadcast(species(), e));
        }

        public ByteVector broadcast(byte e) {
            return castByte128(ByteVector.broadcast(species(), e));
        }
    }

    private static final I8X16Shape I8X16 = new I8X16Shape();

    private static final class I16X8Shape extends Shape<Short> {

        private I16X8Shape() {
        }

        @Override
        public ShortVector reinterpret(ByteVector bytes) {
            return castShort128(castByte128(bytes).reinterpretAsShorts());
        }

        @Override
        public VectorSpecies<Short> species() {
            return ShortVector.SPECIES_128;
        }

        @Override
        public ShortVector zero() {
            return castShort128(ShortVector.zero(species()));
        }

        @Override
        public ShortVector broadcast(long e) {
            return castShort128(ShortVector.broadcast(species(), e));
        }

        public ShortVector broadcast(short e) {
            return castShort128(ShortVector.broadcast(species(), e));
        }
    }

    private static final I16X8Shape I16X8 = new I16X8Shape();

    private static final class I32X4Shape extends Shape<Integer> {

        private I32X4Shape() {
        }

        @Override
        public IntVector reinterpret(ByteVector bytes) {
            return castInt128(castByte128(bytes).reinterpretAsInts());
        }

        @Override
        public VectorSpecies<Integer> species() {
            return IntVector.SPECIES_128;
        }

        @Override
        public IntVector zero() {
            return castInt128(IntVector.zero(species()));
        }

        @Override
        public IntVector broadcast(long e) {
            return castInt128(IntVector.broadcast(species(), e));
        }

        public IntVector broadcast(int e) {
            return castInt128(IntVector.broadcast(species(), e));
        }
    }

    private static final I32X4Shape I32X4 = new I32X4Shape();

    private static final class I64X2Shape extends Shape<Long> {

        private I64X2Shape() {
        }

        @Override
        public LongVector reinterpret(ByteVector bytes) {
            return castLong128(castByte128(bytes).reinterpretAsLongs());
        }

        @Override
        public VectorSpecies<Long> species() {
            return LongVector.SPECIES_128;
        }

        @Override
        public LongVector zero() {
            return castLong128(LongVector.zero(species()));
        }

        @Override
        public LongVector broadcast(long e) {
            return castLong128(LongVector.broadcast(species(), e));
        }
    }

    private static final I64X2Shape I64X2 = new I64X2Shape();

    private static final class F32X4Shape extends Shape<Float> {

        private F32X4Shape() {
        }

        @Override
        public FloatVector reinterpret(ByteVector bytes) {
            return castFloat128(castByte128(bytes).reinterpretAsFloats());
        }

        @Override
        public VectorSpecies<Float> species() {
            return FloatVector.SPECIES_128;
        }

        @Override
        public FloatVector zero() {
            return castFloat128(FloatVector.zero(species()));
        }

        @Override
        public FloatVector broadcast(long e) {
            return castFloat128(FloatVector.broadcast(species(), e));
        }

        @Override
        public FloatVector broadcast(double e) {
            float f = (float) e;
            if (f != e) {
                throw new IllegalArgumentException();
            }
            return broadcast(f);
        }

        public FloatVector broadcast(float e) {
            return castFloat128(FloatVector.broadcast(species(), e));
        }
    }

    private static final F32X4Shape F32X4 = new F32X4Shape();

    private static final class F64X2Shape extends Shape<Double> {

        private F64X2Shape() {
        }

        @Override
        public DoubleVector reinterpret(ByteVector bytes) {
            return castDouble128(castByte128(bytes).reinterpretAsDoubles());
        }

        @Override
        public VectorSpecies<Double> species() {
            return DoubleVector.SPECIES_128;
        }

        @Override
        public DoubleVector zero() {
            return castDouble128(DoubleVector.zero(species()));
        }

        @Override
        public DoubleVector broadcast(long e) {
            return castDouble128(DoubleVector.broadcast(species(), e));
        }

        @Override
        public DoubleVector broadcast(double e) {
            return castDouble128(DoubleVector.broadcast(species(), e));
        }
    }

    private static final F64X2Shape F64X2 = new F64X2Shape();

    @FunctionalInterface
    private interface BinaryVectorOp<F> {
        Vector<F> apply(Vector<F> leftOperand, Vector<F> rightOperand);
    }

    @Override
    public ByteVector unary(ByteVector xVec, int vectorOpcode) {
        ByteVector x = castByte128(xVec);
        return castByte128(switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_NOT -> unop(x, I8X16, VectorOperators.NOT);
            case Bytecode.VECTOR_I8X16_ABS -> unop(x, I8X16, VectorOperators.ABS);
            case Bytecode.VECTOR_I8X16_NEG -> unop(x, I8X16, VectorOperators.NEG);
            case Bytecode.VECTOR_I8X16_POPCNT -> i8x16_popcnt(x); // GR-68892
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S -> extadd_pairwise(x, I8X16, VectorOperators.B2S);
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U -> extadd_pairwise(x, I8X16, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_S -> extend(x, 0, I8X16, VectorOperators.B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_U -> extend(x, 0, I8X16, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_S -> extend(x, 1, I8X16, VectorOperators.B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_U -> extend(x, 1, I8X16, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_ABS -> unop(x, I16X8, VectorOperators.ABS);
            case Bytecode.VECTOR_I16X8_NEG -> unop(x, I16X8, VectorOperators.NEG);
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S -> extadd_pairwise(x, I16X8, VectorOperators.S2I);
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U -> extadd_pairwise(x, I16X8, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S -> extend(x, 0, I16X8, VectorOperators.S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U -> extend(x, 0, I16X8, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S -> extend(x, 1, I16X8, VectorOperators.S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U -> extend(x, 1, I16X8, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I32X4_ABS -> unop(x, I32X4, VectorOperators.ABS);
            case Bytecode.VECTOR_I32X4_NEG -> unop(x, I32X4, VectorOperators.NEG);
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S -> extend(x, 0, I32X4, VectorOperators.I2L);
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U -> extend(x, 0, I32X4, VectorOperators.ZERO_EXTEND_I2L);
            case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S -> extend(x, 1, I32X4, VectorOperators.I2L);
            case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U -> extend(x, 1, I32X4, VectorOperators.ZERO_EXTEND_I2L);
            case Bytecode.VECTOR_I64X2_ABS -> unop(x, I64X2, VectorOperators.ABS);
            case Bytecode.VECTOR_I64X2_NEG -> unop(x, I64X2, VectorOperators.NEG);
            case Bytecode.VECTOR_F32X4_ABS -> unop(x, F32X4, VectorOperators.ABS);
            case Bytecode.VECTOR_F32X4_NEG -> unop(x, F32X4, VectorOperators.NEG);
            case Bytecode.VECTOR_F32X4_SQRT -> unop(x, F32X4, VectorOperators.SQRT);
            case Bytecode.VECTOR_F32X4_CEIL -> ceil(x, F32X4, I32X4, VectorOperators.REINTERPRET_F2I, VectorOperators.REINTERPRET_I2F,
                            Vector128OpsVectorAPI::getExponentFloats, FLOAT_SIGNIFICAND_WIDTH, I32X4.broadcast(FLOAT_SIGNIF_BIT_MASK));
            case Bytecode.VECTOR_F32X4_FLOOR -> floor(x, F32X4, I32X4, VectorOperators.REINTERPRET_F2I, VectorOperators.REINTERPRET_I2F,
                            Vector128OpsVectorAPI::getExponentFloats, FLOAT_SIGNIFICAND_WIDTH, I32X4.broadcast(FLOAT_SIGNIF_BIT_MASK));
            case Bytecode.VECTOR_F32X4_TRUNC -> trunc(x, F32X4, I32X4, VectorOperators.REINTERPRET_F2I, VectorOperators.REINTERPRET_I2F,
                            Vector128OpsVectorAPI::getExponentFloats, FLOAT_SIGNIFICAND_WIDTH, I32X4.broadcast(FLOAT_SIGNIF_BIT_MASK));
            case Bytecode.VECTOR_F32X4_NEAREST -> nearest(x, F32X4, 1 << (FLOAT_SIGNIFICAND_WIDTH - 1));
            case Bytecode.VECTOR_F64X2_ABS -> unop(x, F64X2, VectorOperators.ABS);
            case Bytecode.VECTOR_F64X2_NEG -> unop(x, F64X2, VectorOperators.NEG);
            case Bytecode.VECTOR_F64X2_SQRT -> unop(x, F64X2, VectorOperators.SQRT);
            case Bytecode.VECTOR_F64X2_CEIL -> ceil(x, F64X2, I64X2, VectorOperators.REINTERPRET_D2L, VectorOperators.REINTERPRET_L2D,
                            Vector128OpsVectorAPI::getExponentDoubles, DOUBLE_SIGNIFICAND_WIDTH, I64X2.broadcast(DOUBLE_SIGNIF_BIT_MASK));
            case Bytecode.VECTOR_F64X2_FLOOR -> floor(x, F64X2, I64X2, VectorOperators.REINTERPRET_D2L, VectorOperators.REINTERPRET_L2D,
                            Vector128OpsVectorAPI::getExponentDoubles, DOUBLE_SIGNIFICAND_WIDTH, I64X2.broadcast(DOUBLE_SIGNIF_BIT_MASK));
            case Bytecode.VECTOR_F64X2_TRUNC -> trunc(x, F64X2, I64X2, VectorOperators.REINTERPRET_D2L, VectorOperators.REINTERPRET_L2D,
                            Vector128OpsVectorAPI::getExponentDoubles, DOUBLE_SIGNIFICAND_WIDTH, I64X2.broadcast(DOUBLE_SIGNIF_BIT_MASK));
            case Bytecode.VECTOR_F64X2_NEAREST -> nearest(x, F64X2, 1L << (DOUBLE_SIGNIFICAND_WIDTH - 1));
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_S, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F32X4_S -> fromArray(fallbackOps.unary(x.toArray(), vectorOpcode)); // GR-51421
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_U, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F32X4_U -> fromArray(fallbackOps.unary(x.toArray(), vectorOpcode)); // GR-51421
            case Bytecode.VECTOR_F32X4_CONVERT_I32X4_S -> convert(x, I32X4, VectorOperators.I2F);
            case Bytecode.VECTOR_F32X4_CONVERT_I32X4_U -> fromArray(fallbackOps.unary(x.toArray(), vectorOpcode)); // GR-68843
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F64X2_S_ZERO -> fromArray(fallbackOps.unary(x.toArray(), vectorOpcode)); // GR-51421
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F64X2_U_ZERO -> fromArray(fallbackOps.unary(x.toArray(), vectorOpcode)); // GR-51421
            case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_S -> convert(x, I32X4, VectorOperators.I2D);
            case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_U -> f64x2_convert_low_i32x4_u(x);
            case Bytecode.VECTOR_F32X4_DEMOTE_F64X2_ZERO -> fromArray(fallbackOps.unary(x.toArray(), vectorOpcode)); // GR-68843
            case Bytecode.VECTOR_F64X2_PROMOTE_LOW_F32X4 -> fromArray(fallbackOps.unary(x.toArray(), vectorOpcode)); // GR-68843
            default -> throw CompilerDirectives.shouldNotReachHere();
        });
    }

    @Override
    public ByteVector binary(ByteVector xVec, ByteVector yVec, int vectorOpcode) {
        ByteVector x = castByte128(xVec);
        ByteVector y = castByte128(yVec);
        return castByte128(switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_SWIZZLE, Bytecode.VECTOR_I8X16_RELAXED_SWIZZLE -> i8x16_swizzle(x, y);
            case Bytecode.VECTOR_V128_AND -> binop(x, y, I8X16, VectorOperators.AND);
            case Bytecode.VECTOR_V128_ANDNOT -> binop(x, y, I8X16, VectorOperators.AND_NOT);
            case Bytecode.VECTOR_V128_OR -> binop(x, y, I8X16, VectorOperators.OR);
            case Bytecode.VECTOR_V128_XOR -> binop(x, y, I8X16, VectorOperators.XOR);
            case Bytecode.VECTOR_I8X16_EQ -> relop(x, y, I8X16, VectorOperators.EQ);
            case Bytecode.VECTOR_I8X16_NE -> relop(x, y, I8X16, VectorOperators.NE);
            case Bytecode.VECTOR_I8X16_LT_S -> relop(x, y, I8X16, VectorOperators.LT);
            case Bytecode.VECTOR_I8X16_LT_U -> relop(x, y, I8X16, VectorOperators.ULT);
            case Bytecode.VECTOR_I8X16_GT_S -> relop(x, y, I8X16, VectorOperators.GT);
            case Bytecode.VECTOR_I8X16_GT_U -> relop(x, y, I8X16, VectorOperators.UGT);
            case Bytecode.VECTOR_I8X16_LE_S -> relop(x, y, I8X16, VectorOperators.LE);
            case Bytecode.VECTOR_I8X16_LE_U -> relop(x, y, I8X16, VectorOperators.ULE);
            case Bytecode.VECTOR_I8X16_GE_S -> relop(x, y, I8X16, VectorOperators.GE);
            case Bytecode.VECTOR_I8X16_GE_U -> relop(x, y, I8X16, VectorOperators.UGE);
            case Bytecode.VECTOR_I16X8_EQ -> relop(x, y, I16X8, VectorOperators.EQ);
            case Bytecode.VECTOR_I16X8_NE -> relop(x, y, I16X8, VectorOperators.NE);
            case Bytecode.VECTOR_I16X8_LT_S -> relop(x, y, I16X8, VectorOperators.LT);
            case Bytecode.VECTOR_I16X8_LT_U -> relop(x, y, I16X8, VectorOperators.ULT);
            case Bytecode.VECTOR_I16X8_GT_S -> relop(x, y, I16X8, VectorOperators.GT);
            case Bytecode.VECTOR_I16X8_GT_U -> relop(x, y, I16X8, VectorOperators.UGT);
            case Bytecode.VECTOR_I16X8_LE_S -> relop(x, y, I16X8, VectorOperators.LE);
            case Bytecode.VECTOR_I16X8_LE_U -> relop(x, y, I16X8, VectorOperators.ULE);
            case Bytecode.VECTOR_I16X8_GE_S -> relop(x, y, I16X8, VectorOperators.GE);
            case Bytecode.VECTOR_I16X8_GE_U -> relop(x, y, I16X8, VectorOperators.UGE);
            case Bytecode.VECTOR_I32X4_EQ -> relop(x, y, I32X4, VectorOperators.EQ);
            case Bytecode.VECTOR_I32X4_NE -> relop(x, y, I32X4, VectorOperators.NE);
            case Bytecode.VECTOR_I32X4_LT_S -> relop(x, y, I32X4, VectorOperators.LT);
            case Bytecode.VECTOR_I32X4_LT_U -> relop(x, y, I32X4, VectorOperators.ULT);
            case Bytecode.VECTOR_I32X4_GT_S -> relop(x, y, I32X4, VectorOperators.GT);
            case Bytecode.VECTOR_I32X4_GT_U -> relop(x, y, I32X4, VectorOperators.UGT);
            case Bytecode.VECTOR_I32X4_LE_S -> relop(x, y, I32X4, VectorOperators.LE);
            case Bytecode.VECTOR_I32X4_LE_U -> relop(x, y, I32X4, VectorOperators.ULE);
            case Bytecode.VECTOR_I32X4_GE_S -> relop(x, y, I32X4, VectorOperators.GE);
            case Bytecode.VECTOR_I32X4_GE_U -> relop(x, y, I32X4, VectorOperators.UGE);
            case Bytecode.VECTOR_I64X2_EQ -> relop(x, y, I64X2, VectorOperators.EQ);
            case Bytecode.VECTOR_I64X2_NE -> relop(x, y, I64X2, VectorOperators.NE);
            case Bytecode.VECTOR_I64X2_LT_S -> relop(x, y, I64X2, VectorOperators.LT);
            case Bytecode.VECTOR_I64X2_GT_S -> relop(x, y, I64X2, VectorOperators.GT);
            case Bytecode.VECTOR_I64X2_LE_S -> relop(x, y, I64X2, VectorOperators.LE);
            case Bytecode.VECTOR_I64X2_GE_S -> relop(x, y, I64X2, VectorOperators.GE);
            case Bytecode.VECTOR_F32X4_EQ -> f32x4_relop(x, y, VectorOperators.EQ);
            case Bytecode.VECTOR_F32X4_NE -> f32x4_relop(x, y, VectorOperators.NE);
            case Bytecode.VECTOR_F32X4_LT -> f32x4_relop(x, y, VectorOperators.LT);
            case Bytecode.VECTOR_F32X4_GT -> f32x4_relop(x, y, VectorOperators.GT);
            case Bytecode.VECTOR_F32X4_LE -> f32x4_relop(x, y, VectorOperators.LE);
            case Bytecode.VECTOR_F32X4_GE -> f32x4_relop(x, y, VectorOperators.GE);
            case Bytecode.VECTOR_F64X2_EQ -> f64x2_relop(x, y, VectorOperators.EQ);
            case Bytecode.VECTOR_F64X2_NE -> f64x2_relop(x, y, VectorOperators.NE);
            case Bytecode.VECTOR_F64X2_LT -> f64x2_relop(x, y, VectorOperators.LT);
            case Bytecode.VECTOR_F64X2_GT -> f64x2_relop(x, y, VectorOperators.GT);
            case Bytecode.VECTOR_F64X2_LE -> f64x2_relop(x, y, VectorOperators.LE);
            case Bytecode.VECTOR_F64X2_GE -> f64x2_relop(x, y, VectorOperators.GE);
            case Bytecode.VECTOR_I8X16_NARROW_I16X8_S -> narrow(x, y, I16X8, I8X16, Byte.MIN_VALUE, Byte.MAX_VALUE);
            case Bytecode.VECTOR_I8X16_NARROW_I16X8_U -> narrow(x, y, I16X8, I8X16, (short) 0, (short) 0xff);
            case Bytecode.VECTOR_I8X16_ADD -> binop(x, y, I8X16, VectorOperators.ADD);
            case Bytecode.VECTOR_I8X16_ADD_SAT_S -> binop_sat(x, y, I8X16, I16X8, VectorOperators.B2S, VectorOperators.ADD, Byte.MIN_VALUE, Byte.MAX_VALUE); // GR-68891
            case Bytecode.VECTOR_I8X16_ADD_SAT_U -> binop_sat(x, y, I8X16, I16X8, VectorOperators.ZERO_EXTEND_B2S, VectorOperators.ADD, 0, 0xff); // GR-68891
            case Bytecode.VECTOR_I8X16_SUB -> binop(x, y, I8X16, VectorOperators.SUB);
            case Bytecode.VECTOR_I8X16_SUB_SAT_S -> binop_sat(x, y, I8X16, I16X8, VectorOperators.B2S, VectorOperators.SUB, Byte.MIN_VALUE, Byte.MAX_VALUE); // GR-68891
            case Bytecode.VECTOR_I8X16_SUB_SAT_U -> binop_sat(x, y, I8X16, I16X8, VectorOperators.ZERO_EXTEND_B2S, VectorOperators.SUB, 0, 0xff); // GR-68891
            case Bytecode.VECTOR_I8X16_MIN_S -> binop(x, y, I8X16, VectorOperators.MIN);
            case Bytecode.VECTOR_I8X16_MIN_U -> fromArray(fallbackOps.binary(x.toArray(), y.toArray(), vectorOpcode)); // GR-68891
            case Bytecode.VECTOR_I8X16_MAX_S -> binop(x, y, I8X16, VectorOperators.MAX);
            case Bytecode.VECTOR_I8X16_MAX_U -> fromArray(fallbackOps.binary(x.toArray(), y.toArray(), vectorOpcode)); // GR-68891
            case Bytecode.VECTOR_I8X16_AVGR_U -> avgr_u(x, y, I8X16, I16X8, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_NARROW_I32X4_S -> narrow(x, y, I32X4, I16X8, Short.MIN_VALUE, Short.MAX_VALUE);
            case Bytecode.VECTOR_I16X8_NARROW_I32X4_U -> narrow(x, y, I32X4, I16X8, 0, 0xffff);
            case Bytecode.VECTOR_I16X8_Q15MULR_SAT_S, Bytecode.VECTOR_I16X8_RELAXED_Q15MULR_S -> i16x8_q15mulr_sat_s(x, y);
            case Bytecode.VECTOR_I16X8_ADD -> binop(x, y, I16X8, VectorOperators.ADD);
            case Bytecode.VECTOR_I16X8_ADD_SAT_S -> binop_sat(x, y, I16X8, I32X4, VectorOperators.S2I, VectorOperators.ADD, Short.MIN_VALUE, Short.MAX_VALUE); // GR-68891
            case Bytecode.VECTOR_I16X8_ADD_SAT_U -> binop_sat(x, y, I16X8, I32X4, VectorOperators.ZERO_EXTEND_S2I, VectorOperators.ADD, 0, 0xffff); // GR-68891
            case Bytecode.VECTOR_I16X8_SUB -> binop(x, y, I16X8, VectorOperators.SUB);
            case Bytecode.VECTOR_I16X8_SUB_SAT_S -> binop_sat(x, y, I16X8, I32X4, VectorOperators.S2I, VectorOperators.SUB, Short.MIN_VALUE, Short.MAX_VALUE); // GR-68891
            case Bytecode.VECTOR_I16X8_SUB_SAT_U -> binop_sat(x, y, I16X8, I32X4, VectorOperators.ZERO_EXTEND_S2I, VectorOperators.SUB, 0, 0xffff); // GR-68891
            case Bytecode.VECTOR_I16X8_MUL -> binop(x, y, I16X8, VectorOperators.MUL);
            case Bytecode.VECTOR_I16X8_MIN_S -> binop(x, y, I16X8, VectorOperators.MIN);
            case Bytecode.VECTOR_I16X8_MIN_U -> fromArray(fallbackOps.binary(x.toArray(), y.toArray(), vectorOpcode)); // GR-68891
            case Bytecode.VECTOR_I16X8_MAX_S -> binop(x, y, I16X8, VectorOperators.MAX);
            case Bytecode.VECTOR_I16X8_MAX_U -> fromArray(fallbackOps.binary(x.toArray(), y.toArray(), vectorOpcode)); // GR-68891
            case Bytecode.VECTOR_I16X8_AVGR_U -> avgr_u(x, y, I16X8, I32X4, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_S -> extmul(x, y, I8X16, VectorOperators.B2S, 0);
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_U -> extmul(x, y, I8X16, VectorOperators.ZERO_EXTEND_B2S, 0);
            case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S -> extmul(x, y, I8X16, VectorOperators.B2S, 1);
            case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U -> extmul(x, y, I8X16, VectorOperators.ZERO_EXTEND_B2S, 1);
            case Bytecode.VECTOR_I32X4_ADD -> binop(x, y, I32X4, VectorOperators.ADD);
            case Bytecode.VECTOR_I32X4_SUB -> binop(x, y, I32X4, VectorOperators.SUB);
            case Bytecode.VECTOR_I32X4_MUL -> binop(x, y, I32X4, VectorOperators.MUL);
            case Bytecode.VECTOR_I32X4_MIN_S -> binop(x, y, I32X4, VectorOperators.MIN);
            case Bytecode.VECTOR_I32X4_MIN_U -> fromArray(fallbackOps.binary(x.toArray(), y.toArray(), vectorOpcode)); // GR-68891
            case Bytecode.VECTOR_I32X4_MAX_S -> binop(x, y, I32X4, VectorOperators.MAX);
            case Bytecode.VECTOR_I32X4_MAX_U -> fromArray(fallbackOps.binary(x.toArray(), y.toArray(), vectorOpcode)); // GR-68891
            case Bytecode.VECTOR_I32X4_DOT_I16X8_S -> i32x4_dot_i16x8_s(x, y);
            case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S -> extmul(x, y, I16X8, VectorOperators.S2I, 0);
            case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U -> extmul(x, y, I16X8, VectorOperators.ZERO_EXTEND_S2I, 0);
            case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S -> extmul(x, y, I16X8, VectorOperators.S2I, 1);
            case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U -> extmul(x, y, I16X8, VectorOperators.ZERO_EXTEND_S2I, 1);
            case Bytecode.VECTOR_I64X2_ADD -> binop(x, y, I64X2, VectorOperators.ADD);
            case Bytecode.VECTOR_I64X2_SUB -> binop(x, y, I64X2, VectorOperators.SUB);
            case Bytecode.VECTOR_I64X2_MUL -> binop(x, y, I64X2, VectorOperators.MUL);
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S -> extmul(x, y, I32X4, VectorOperators.I2L, 0);
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U -> extmul(x, y, I32X4, VectorOperators.ZERO_EXTEND_I2L, 0);
            case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S -> extmul(x, y, I32X4, VectorOperators.I2L, 1);
            case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U -> extmul(x, y, I32X4, VectorOperators.ZERO_EXTEND_I2L, 1);
            case Bytecode.VECTOR_F32X4_ADD -> binop(x, y, F32X4, VectorOperators.ADD);
            case Bytecode.VECTOR_F32X4_SUB -> binop(x, y, F32X4, VectorOperators.SUB);
            case Bytecode.VECTOR_F32X4_MUL -> binop(x, y, F32X4, VectorOperators.MUL);
            case Bytecode.VECTOR_F32X4_DIV -> binop(x, y, F32X4, VectorOperators.DIV);
            case Bytecode.VECTOR_F32X4_MIN, Bytecode.VECTOR_F32X4_RELAXED_MIN -> binop(x, y, F32X4, VectorOperators.MIN);
            case Bytecode.VECTOR_F32X4_MAX, Bytecode.VECTOR_F32X4_RELAXED_MAX -> binop(x, y, F32X4, VectorOperators.MAX);
            case Bytecode.VECTOR_F32X4_PMIN -> pmin(x, y, F32X4);
            case Bytecode.VECTOR_F32X4_PMAX -> pmax(x, y, F32X4);
            case Bytecode.VECTOR_F64X2_ADD -> binop(x, y, F64X2, VectorOperators.ADD);
            case Bytecode.VECTOR_F64X2_SUB -> binop(x, y, F64X2, VectorOperators.SUB);
            case Bytecode.VECTOR_F64X2_MUL -> binop(x, y, F64X2, VectorOperators.MUL);
            case Bytecode.VECTOR_F64X2_DIV -> binop(x, y, F64X2, VectorOperators.DIV);
            case Bytecode.VECTOR_F64X2_MIN, Bytecode.VECTOR_F64X2_RELAXED_MIN -> binop(x, y, F64X2, VectorOperators.MIN);
            case Bytecode.VECTOR_F64X2_MAX, Bytecode.VECTOR_F64X2_RELAXED_MAX -> binop(x, y, F64X2, VectorOperators.MAX);
            case Bytecode.VECTOR_F64X2_PMIN -> pmin(x, y, F64X2);
            case Bytecode.VECTOR_F64X2_PMAX -> pmax(x, y, F64X2);
            case Bytecode.VECTOR_I16X8_RELAXED_DOT_I8X16_I7X16_S -> i16x8_relaxed_dot_i8x16_i7x16_s(x, y);
            default -> throw CompilerDirectives.shouldNotReachHere();
        });
    }

    @Override
    public ByteVector ternary(ByteVector xVec, ByteVector yVec, ByteVector zVec, int vectorOpcode) {
        ByteVector x = castByte128(xVec);
        ByteVector y = castByte128(yVec);
        ByteVector z = castByte128(zVec);
        return castByte128(switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_BITSELECT, Bytecode.VECTOR_I8X16_RELAXED_LANESELECT, Bytecode.VECTOR_I16X8_RELAXED_LANESELECT, Bytecode.VECTOR_I32X4_RELAXED_LANESELECT,
                            Bytecode.VECTOR_I64X2_RELAXED_LANESELECT ->
                bitselect(x, y, z);
            case Bytecode.VECTOR_F32X4_RELAXED_MADD, Bytecode.VECTOR_F32X4_RELAXED_NMADD -> f32x4_ternop(x, y, z, vectorOpcode);
            case Bytecode.VECTOR_F64X2_RELAXED_MADD, Bytecode.VECTOR_F64X2_RELAXED_NMADD -> f64x2_ternop(x, y, z, vectorOpcode);
            case Bytecode.VECTOR_I32X4_RELAXED_DOT_I8X16_I7X16_ADD_S -> i32x4_relaxed_dot_i8x16_i7x16_add_s(x, y, z);
            default -> throw CompilerDirectives.shouldNotReachHere();
        });
    }

    @Override
    public int vectorToInt(ByteVector xVec, int vectorOpcode) {
        ByteVector x = castByte128(xVec);
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_ANY_TRUE -> v128_any_true(x);
            case Bytecode.VECTOR_I8X16_ALL_TRUE -> all_true(x, I8X16);
            case Bytecode.VECTOR_I8X16_BITMASK -> bitmask(x, I8X16);
            case Bytecode.VECTOR_I16X8_ALL_TRUE -> all_true(x, I16X8);
            case Bytecode.VECTOR_I16X8_BITMASK -> bitmask(x, I16X8);
            case Bytecode.VECTOR_I32X4_ALL_TRUE -> all_true(x, I32X4);
            case Bytecode.VECTOR_I32X4_BITMASK -> bitmask(x, I32X4);
            case Bytecode.VECTOR_I64X2_ALL_TRUE -> fallbackOps.vectorToInt(x.toArray(), vectorOpcode); // GR-68893
            case Bytecode.VECTOR_I64X2_BITMASK -> bitmask(x, I64X2);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public ByteVector shift(ByteVector xVec, int shift, int vectorOpcode) {
        ByteVector x = castByte128(xVec);
        return castByte128(switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_SHL -> shiftop(x, (byte) shift, I8X16, VectorOperators.LSHL);
            case Bytecode.VECTOR_I8X16_SHR_S -> shiftop(x, (byte) shift, I8X16, VectorOperators.ASHR);
            case Bytecode.VECTOR_I8X16_SHR_U -> shiftop(x, (byte) shift, I8X16, VectorOperators.LSHR);
            case Bytecode.VECTOR_I16X8_SHL -> shiftop(x, (short) shift, I16X8, VectorOperators.LSHL);
            case Bytecode.VECTOR_I16X8_SHR_S -> shiftop(x, (short) shift, I16X8, VectorOperators.ASHR);
            case Bytecode.VECTOR_I16X8_SHR_U -> shiftop(x, (short) shift, I16X8, VectorOperators.LSHR);
            case Bytecode.VECTOR_I32X4_SHL -> shiftop(x, shift, I32X4, VectorOperators.LSHL);
            case Bytecode.VECTOR_I32X4_SHR_S -> shiftop(x, shift, I32X4, VectorOperators.ASHR);
            case Bytecode.VECTOR_I32X4_SHR_U -> shiftop(x, shift, I32X4, VectorOperators.LSHR);
            case Bytecode.VECTOR_I64X2_SHL -> shiftop(x, shift, I64X2, VectorOperators.LSHL);
            case Bytecode.VECTOR_I64X2_SHR_S -> shiftop(x, shift, I64X2, VectorOperators.ASHR);
            case Bytecode.VECTOR_I64X2_SHR_U -> shiftop(x, shift, I64X2, VectorOperators.LSHR);
            default -> throw CompilerDirectives.shouldNotReachHere();
        });
    }

    // Checkstyle: stop method name check

    @Override
    public ByteVector v128_load8x8(long value, int vectorOpcode) {
        ByteVector bytes = LongVector.zero(I64X2.species()).withLane(0, value).reinterpretAsBytes();
        // Could this be faster?
        // ByteVector bytes = Vector128Ops.I64X2.broadcast(value).reinterpretAsBytes();
        VectorOperators.Conversion<Byte, Short> conversion = switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_LOAD8X8_S -> VectorOperators.B2S;
            case Bytecode.VECTOR_V128_LOAD8X8_U -> VectorOperators.ZERO_EXTEND_B2S;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return castByte128(bytes.convert(conversion, 0).reinterpretAsBytes());
    }

    @Override
    public ByteVector v128_load16x4(long value, int vectorOpcode) {
        ShortVector shorts = LongVector.zero(I64X2.species()).withLane(0, value).reinterpretAsShorts();
        // Could this be faster?
        // ShortVector shorts = Vector128Ops.I64X2.broadcast(value).reinterpretAsShorts();
        VectorOperators.Conversion<Short, Integer> conversion = switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_LOAD16X4_S -> VectorOperators.S2I;
            case Bytecode.VECTOR_V128_LOAD16X4_U -> VectorOperators.ZERO_EXTEND_S2I;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return castByte128(shorts.convert(conversion, 0).reinterpretAsBytes());
    }

    @Override
    public ByteVector v128_load32x2(long value, int vectorOpcode) {
        IntVector ints = LongVector.zero(I64X2.species()).withLane(0, value).reinterpretAsInts();
        // Could this be faster?
        // IntVector ints = Vector128Ops.I64X2.broadcast(value).reinterpretAsInts();
        VectorOperators.Conversion<Integer, Long> conversion = switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_LOAD32X2_S -> VectorOperators.I2L;
            case Bytecode.VECTOR_V128_LOAD32X2_U -> VectorOperators.ZERO_EXTEND_I2L;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return castByte128(ints.convert(conversion, 0).reinterpretAsBytes());
    }

    @Override
    public ByteVector v128_load32_zero(int value) {
        return castByte128(I32X4.zero().withLane(0, value).reinterpretAsBytes());
    }

    @Override
    public ByteVector v128_load64_zero(long value) {
        return castByte128(I64X2.zero().withLane(0, value).reinterpretAsBytes());
    }

    @Override
    public ByteVector i8x16_splat(byte value) {
        return I8X16.broadcast(value);
    }

    @Override
    public ByteVector i16x8_splat(short value) {
        return I16X8.broadcast(value).reinterpretAsBytes();
    }

    @Override
    public ByteVector i32x4_splat(int value) {
        return I32X4.broadcast(value).reinterpretAsBytes();
    }

    @Override
    public ByteVector i64x2_splat(long value) {
        return I64X2.broadcast(value).reinterpretAsBytes();
    }

    @Override
    public ByteVector f32x4_splat(float value) {
        return F32X4.broadcast(value).reinterpretAsBytes();
    }

    @Override
    public ByteVector f64x2_splat(double value) {
        return F64X2.broadcast(value).reinterpretAsBytes();
    }

    @Override
    public ByteVector i8x16_shuffle(ByteVector xVec, ByteVector yVec, ByteVector indicesVec) {
        ByteVector x = castByte128(xVec);
        ByteVector y = castByte128(yVec);
        ByteVector indices = castByte128(indicesVec);
        VectorShuffle<Byte> shuffle = indices.add((byte) (-2 * BYTES), indices.lt((byte) BYTES).not()).toShuffle();
        return castByte128(x.rearrange(shuffle, y));
    }

    @Override
    public byte i8x16_extract_lane_s(ByteVector vec, int laneIndex) {
        return castByte128(vec).lane(laneIndex);
    }

    @Override
    public int i8x16_extract_lane(ByteVector vec, int laneIndex, int vectorOpcode) {
        ByteVector v = castByte128(vec);
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_S -> v.lane(laneIndex);
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_U -> Byte.toUnsignedInt(v.lane(laneIndex));
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    @Override
    public ByteVector i8x16_replace_lane(ByteVector vec, int laneIndex, byte value) {
        return castByte128(castByte128(vec).withLane(laneIndex, value));
    }

    @Override
    public short i16x8_extract_lane_s(ByteVector vecBytes, int laneIndex) {
        return castByte128(vecBytes).reinterpretAsShorts().lane(laneIndex);
    }

    @Override
    public int i16x8_extract_lane(ByteVector vecBytes, int laneIndex, int vectorOpcode) {
        ShortVector vec = castByte128(vecBytes).reinterpretAsShorts();
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_S -> vec.lane(laneIndex);
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_U -> Short.toUnsignedInt(vec.lane(laneIndex));
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    @Override
    public ByteVector i16x8_replace_lane(ByteVector vecBytes, int laneIndex, short value) {
        ShortVector vec = castByte128(vecBytes).reinterpretAsShorts();
        return castByte128(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    @Override
    public int i32x4_extract_lane(ByteVector vecBytes, int laneIndex) {
        IntVector vec = castByte128(vecBytes).reinterpretAsInts();
        return vec.lane(laneIndex);
    }

    @Override
    public ByteVector i32x4_replace_lane(ByteVector vecBytes, int laneIndex, int value) {
        IntVector vec = castByte128(vecBytes).reinterpretAsInts();
        return castByte128(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    @Override
    public long i64x2_extract_lane(ByteVector vecBytes, int laneIndex) {
        LongVector vec = castByte128(vecBytes).reinterpretAsLongs();
        return vec.lane(laneIndex);
    }

    @Override
    public ByteVector i64x2_replace_lane(ByteVector vecBytes, int laneIndex, long value) {
        LongVector vec = castByte128(vecBytes).reinterpretAsLongs();
        return castByte128(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    @Override
    public float f32x4_extract_lane(ByteVector vecBytes, int laneIndex) {
        FloatVector vec = castByte128(vecBytes).reinterpretAsFloats();
        return vec.lane(laneIndex);
    }

    @Override
    public ByteVector f32x4_replace_lane(ByteVector vecBytes, int laneIndex, float value) {
        FloatVector vec = castByte128(vecBytes).reinterpretAsFloats();
        return castByte128(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    @Override
    public double f64x2_extract_lane(ByteVector vecBytes, int laneIndex) {
        DoubleVector vec = castByte128(vecBytes).reinterpretAsDoubles();
        return vec.lane(laneIndex);
    }

    @Override
    public ByteVector f64x2_replace_lane(ByteVector vecBytes, int laneIndex, double value) {
        DoubleVector vec = castByte128(vecBytes).reinterpretAsDoubles();
        return castByte128(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    private static <E> ByteVector unop(ByteVector xBytes, Shape<E> shape, VectorOperators.Unary op) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> result = x.lanewise(op);
        return result.reinterpretAsBytes();
    }

    private static ByteVector i8x16_popcnt(ByteVector x) {
        // Based on the same approach as Integer#bitCount
        ByteVector popcnt = x.sub(x.lanewise(VectorOperators.LSHR, 1).and((byte) 0x55));
        popcnt = popcnt.and((byte) 0x33).add(popcnt.lanewise(VectorOperators.LSHR, 2).and((byte) 0x33));
        return popcnt.add(popcnt.lanewise(VectorOperators.LSHR, 4)).and((byte) 0x0F);
    }

    private static <E, F> ByteVector extadd_pairwise(ByteVector xBytes, Shape<E> shape, VectorOperators.Conversion<E, F> conv) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<F> evens = x.compress(shape.evensMask).convert(conv, 0);
        Vector<F> odds = x.compress(shape.oddsMask).convert(conv, 0);
        Vector<F> result = evens.add(odds);
        return result.reinterpretAsBytes();
    }

    private static <E, F> ByteVector extend(ByteVector xBytes, int part, Shape<E> shape, VectorOperators.Conversion<E, F> conv) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<F> result = x.convert(conv, part);
        return result.reinterpretAsBytes();
    }

    private static final int FLOAT_SIGNIFICAND_WIDTH = Float.PRECISION;
    private static final int FLOAT_EXP_BIAS = (1 << (Float.SIZE - FLOAT_SIGNIFICAND_WIDTH - 1)) - 1; // 127
    private static final int FLOAT_EXP_BIT_MASK = ((1 << (Float.SIZE - FLOAT_SIGNIFICAND_WIDTH)) - 1) << (FLOAT_SIGNIFICAND_WIDTH - 1);
    private static final long FLOAT_SIGNIF_BIT_MASK = (1L << (FLOAT_SIGNIFICAND_WIDTH - 1)) - 1;

    // Based on JDK's DoubleConsts
    private static final int DOUBLE_SIGNIFICAND_WIDTH = Double.PRECISION;
    private static final int DOUBLE_EXP_BIAS = (1 << (Double.SIZE - DOUBLE_SIGNIFICAND_WIDTH - 1)) - 1; // 1023
    private static final long DOUBLE_EXP_BIT_MASK = ((1L << (Double.SIZE - DOUBLE_SIGNIFICAND_WIDTH)) - 1) << (DOUBLE_SIGNIFICAND_WIDTH - 1);
    private static final long DOUBLE_SIGNIF_BIT_MASK = (1L << (DOUBLE_SIGNIFICAND_WIDTH - 1)) - 1;

    private static final double CEIL_NEGATIVE_BOUNDARY_ARG = -0.0;
    private static final double CEIL_POSITIVE_BOUNDARY_ARG = 1.0;
    private static final double CEIL_SIGN_ARG = 1.0;

    private static final double FLOOR_NEGATIVE_BOUNDARY_ARG = -1.0;
    private static final double FLOOR_POSITIVE_BOUNDARY_ARG = 0.0;
    private static final double FLOOR_SIGN_ARG = -1.0;

    private static IntVector getExponentFloats(Vector<Float> x) {
        return castInt128(x.convert(VectorOperators.REINTERPRET_F2I, 0).lanewise(VectorOperators.AND, FLOAT_EXP_BIT_MASK).lanewise(VectorOperators.LSHR, FLOAT_SIGNIFICAND_WIDTH - 1).sub(
                        I32X4.broadcast(FLOAT_EXP_BIAS)));
    }

    private static LongVector getExponentDoubles(Vector<Double> x) {
        return castLong128(x.convert(VectorOperators.REINTERPRET_D2L, 0).lanewise(VectorOperators.AND, DOUBLE_EXP_BIT_MASK).lanewise(VectorOperators.LSHR, DOUBLE_SIGNIFICAND_WIDTH - 1).sub(
                        I64X2.broadcast(DOUBLE_EXP_BIAS)));
    }

    private static <F, I> ByteVector ceil(ByteVector xBytes, Shape<F> floatingShape, Shape<I> integralShape,
                    VectorOperators.Conversion<F, I> floatingAsIntegral, VectorOperators.Conversion<I, F> integralAsFloating,
                    Function<Vector<F>, Vector<I>> getExponent, int significantWidth, Vector<I> significandBitMaskVec) {
        // This is based on JDK's StrictMath.ceil
        Vector<F> x = floatingShape.reinterpret(xBytes);
        return floorOrCeil(x, floatingShape, integralShape, floatingAsIntegral, integralAsFloating, getExponent, significantWidth, significandBitMaskVec,
                        floatingShape.broadcast(CEIL_NEGATIVE_BOUNDARY_ARG), floatingShape.broadcast(CEIL_POSITIVE_BOUNDARY_ARG), floatingShape.broadcast(CEIL_SIGN_ARG));
    }

    private static <F, I> ByteVector floor(ByteVector xBytes, Shape<F> floatingShape, Shape<I> integralShape,
                    VectorOperators.Conversion<F, I> floatingAsIntegral, VectorOperators.Conversion<I, F> integralAsFloating,
                    Function<Vector<F>, Vector<I>> getExponent, int significantWidth, Vector<I> significandBitMaskVec) {
        // This is based on JDK's StrictMath.floor
        Vector<F> x = floatingShape.reinterpret(xBytes);
        return floorOrCeil(x, floatingShape, integralShape, floatingAsIntegral, integralAsFloating, getExponent, significantWidth, significandBitMaskVec,
                        floatingShape.broadcast(FLOOR_NEGATIVE_BOUNDARY_ARG), floatingShape.broadcast(FLOOR_POSITIVE_BOUNDARY_ARG), floatingShape.broadcast(FLOOR_SIGN_ARG));
    }

    private static <F, I> ByteVector trunc(ByteVector xBytes, Shape<F> floatingShape, Shape<I> integralShape,
                    VectorOperators.Conversion<F, I> floatingAsIntegral, VectorOperators.Conversion<I, F> integralAsFloating,
                    Function<Vector<F>, Vector<I>> getExponent, int significantWidth, Vector<I> significandBitMaskVec) {
        // This is based on Truffle's ExactMath.truncate
        Vector<F> x = floatingShape.reinterpret(xBytes);
        VectorMask<F> ceil = x.lt(floatingShape.broadcast(0));
        return floorOrCeil(x, floatingShape, integralShape, floatingAsIntegral, integralAsFloating, getExponent, significantWidth, significandBitMaskVec,
                        floatingShape.broadcast(FLOOR_NEGATIVE_BOUNDARY_ARG).blend(floatingShape.broadcast(CEIL_NEGATIVE_BOUNDARY_ARG), ceil),
                        floatingShape.broadcast(FLOOR_POSITIVE_BOUNDARY_ARG).blend(floatingShape.broadcast(CEIL_POSITIVE_BOUNDARY_ARG), ceil),
                        floatingShape.broadcast(FLOOR_SIGN_ARG).blend(floatingShape.broadcast(CEIL_SIGN_ARG), ceil));
    }

    private static <F, I> ByteVector floorOrCeil(Vector<F> x, Shape<F> floatingShape, Shape<I> integralShape,
                    VectorOperators.Conversion<F, I> floatingAsIntegral, VectorOperators.Conversion<I, F> integralAsFloating,
                    Function<Vector<F>, Vector<I>> getExponent, int significandWidth, Vector<I> significandBitMaskVec,
                    Vector<F> negativeBoundary, Vector<F> positiveBoundary, Vector<F> sign) {
        // This is based on JDK's StrictMath.floorOrCeil
        Vector<I> exponent = getExponent.apply(x);
        VectorMask<F> isNegativeExponent = exponent.lt(integralShape.broadcast(0)).cast(floatingShape.species());
        VectorMask<F> isZero = x.eq(floatingShape.broadcast(0));
        VectorMask<F> isNegative = x.lt(floatingShape.broadcast(0));
        Vector<F> negativeExponentResult = positiveBoundary.blend(negativeBoundary, isNegative).blend(x, isZero);
        VectorMask<F> isHighExponent = exponent.compare(VectorOperators.GE, significandWidth - 1).cast(floatingShape.species());
        Vector<F> highExponentResult = x;
        Vector<I> doppel = x.convert(floatingAsIntegral, 0);
        Vector<I> mask = significandBitMaskVec.lanewise(VectorOperators.LSHR, exponent);
        VectorMask<F> isIntegral = doppel.lanewise(VectorOperators.AND, mask).eq(integralShape.broadcast(0)).cast(floatingShape.species());
        Vector<F> integralResult = x;
        Vector<F> fractional = doppel.lanewise(VectorOperators.AND, mask.lanewise(VectorOperators.NOT)).convert(integralAsFloating, 0);
        VectorMask<F> signMatch = x.mul(sign).compare(VectorOperators.GT, 0).cast(floatingShape.species());
        Vector<F> fractionalResult = fractional.blend(fractional.add(sign), signMatch);
        Vector<F> defaultResult = fractionalResult.blend(integralResult, isIntegral);
        Vector<F> result = defaultResult.blend(highExponentResult, isHighExponent).blend(negativeExponentResult, isNegativeExponent);
        return result.reinterpretAsBytes();
    }

    private static <E> Vector<E> sign(Vector<E> x, Shape<E> shape) {
        VectorMask<E> negative = x.test(VectorOperators.IS_NEGATIVE);
        return shape.broadcast(1).blend(shape.broadcast(-1), negative);
    }

    private static <E> ByteVector nearest(ByteVector xBytes, Shape<E> shape, long maxSafePowerOfTwo) {
        // This is based on JDK's StrictMath.rint
        Vector<E> x = shape.reinterpret(xBytes);
        /*
         * If the absolute value of x is not less than 2^52 for double and 2^23 for float, it is
         * either a finite integer (the floating-point format does not have enough significand bits
         * for a number that large to have any fractional portion), an infinity, or a NaN. In any of
         * these cases, nearest(x) == x.
         *
         * Otherwise, the sum (x + maxSafePowerOfTwo) will properly round away any fractional
         * portion of x since ulp(maxSafePowerOfTwo) == 1.0; subtracting out maxSafePowerOfTwo from
         * this sum will then be exact and leave the rounded integer portion of x.
         */
        Vector<E> sign = sign(x, shape); // preserve sign info
        Vector<E> xAbs = x.lanewise(VectorOperators.ABS);
        Vector<E> maxFiniteValueVec = shape.broadcast(maxSafePowerOfTwo);
        VectorMask<E> small = xAbs.lt(maxFiniteValueVec);
        Vector<E> xTrunc = xAbs.blend(xAbs.add(maxFiniteValueVec).sub(maxFiniteValueVec), small);
        return xTrunc.mul(sign).reinterpretAsBytes(); // restore original sign
    }

    private static <E, F> ByteVector convert(ByteVector xBytes, Shape<E> shape, VectorOperators.Conversion<E, F> conv) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<F> result = x.convert(conv, 0);
        return result.reinterpretAsBytes();
    }

    @SuppressWarnings("unused")
    private static ByteVector i32x4_trunc_sat_f32x4_u(ByteVector xBytes) {
        FloatVector x = F32X4.reinterpret(xBytes);
        DoubleVector xLow = castDouble128(x.convert(VectorOperators.F2D, 0));
        DoubleVector xHigh = castDouble128(x.convert(VectorOperators.F2D, 1));
        LongVector xLowTrunc = truncSatU32(xLow);
        LongVector xHighTrunc = truncSatU32(xHigh);
        IntVector resultLow = castInt128(compact(xLowTrunc, 0, I64X2, I32X4));
        IntVector resultHigh = castInt128(compact(xHighTrunc, -1, I64X2, I32X4));
        IntVector result = resultLow.or(resultHigh);
        return result.reinterpretAsBytes();
    }

    @SuppressWarnings("unused")
    private static ByteVector f32x4_convert_i32x4_u(ByteVector xBytes) {
        IntVector x = xBytes.reinterpretAsInts();
        LongVector xUnsignedLow = castLong128(x.convert(VectorOperators.ZERO_EXTEND_I2L, 0));
        LongVector xUnsignedHigh = castLong128(x.convert(VectorOperators.ZERO_EXTEND_I2L, 1));
        FloatVector resultLow = castFloat128(compactGeneral(xUnsignedLow, 0, I64X2, F32X4, VectorOperators.L2F, VectorOperators.REINTERPRET_F2I, VectorOperators.ZERO_EXTEND_I2L));
        FloatVector resultHigh = castFloat128(compactGeneral(xUnsignedHigh, -1, I64X2, F32X4, VectorOperators.L2F, VectorOperators.REINTERPRET_F2I, VectorOperators.ZERO_EXTEND_I2L));
        IntVector result = resultLow.reinterpretAsInts().or(resultHigh.reinterpretAsInts());
        return result.reinterpretAsBytes();
    }

    @SuppressWarnings("unused")
    private static ByteVector i32x4_trunc_sat_f64x2_u_zero(ByteVector xBytes) {
        DoubleVector x = F64X2.reinterpret(xBytes);
        LongVector longResult = truncSatU32(x);
        IntVector result = castInt128(compact(longResult, 0, I64X2, I32X4));
        return result.reinterpretAsBytes();
    }

    private static ByteVector f64x2_convert_low_i32x4_u(ByteVector xBytes) {
        IntVector x = xBytes.reinterpretAsInts();
        Vector<Long> xUnsignedLow = castLong128(x.convert(VectorOperators.ZERO_EXTEND_I2L, 0));
        // Note: L2D might not expand well on certain architectures.
        Vector<Double> result = castDouble128(xUnsignedLow.convert(VectorOperators.L2D, 0));
        return result.reinterpretAsBytes();
    }

    @SuppressWarnings("unused")
    private static ByteVector f32X4_demote_f64X2_zero(ByteVector xBytes) {
        DoubleVector x = F64X2.reinterpret(xBytes);
        Vector<Float> result = compactGeneral(x, 0, I64X2, F32X4, VectorOperators.D2F, VectorOperators.REINTERPRET_F2I, VectorOperators.ZERO_EXTEND_I2L);
        return result.reinterpretAsBytes();
    }

    private static ByteVector i8x16_swizzle(ByteVector valueBytes, ByteVector indexBytes) {
        ByteVector values = valueBytes;
        ByteVector indices = indexBytes;
        VectorMask<Byte> safeIndices = indices.lt((byte) 0).or(indices.lt((byte) BYTES).not()).not();
        return values.rearrange(indices.toShuffle(), safeIndices);
    }

    private static <E> ByteVector binop(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Binary op) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<E> result = x.lanewise(op, y);
        return result.reinterpretAsBytes();
    }

    private static <E> ByteVector relop(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Comparison comp) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<E> result = x.compare(comp, y).toVector();
        return result.reinterpretAsBytes();
    }

    private static ByteVector f32x4_relop(ByteVector xBytes, ByteVector yBytes, VectorOperators.Comparison comp) {
        FloatVector x = xBytes.reinterpretAsFloats();
        FloatVector y = yBytes.reinterpretAsFloats();
        IntVector zero = I32X4.zero();
        IntVector minusOne = I32X4.broadcast(-1);
        IntVector result = zero.blend(minusOne, x.compare(comp, y).cast(I32X4.species()));
        return result.reinterpretAsBytes();
    }

    private static ByteVector f64x2_relop(ByteVector xBytes, ByteVector yBytes, VectorOperators.Comparison comp) {
        DoubleVector x = xBytes.reinterpretAsDoubles();
        DoubleVector y = yBytes.reinterpretAsDoubles();
        LongVector zero = I64X2.zero();
        LongVector minusOne = I64X2.broadcast(-1);
        LongVector result = zero.blend(minusOne, x.compare(comp, y).cast(I64X2.species()));
        return result.reinterpretAsBytes();
    }

    /**
     * Implements {@code vec.convert(downcast, part)} while avoiding the use of
     * {@code VectorSupport#convert} in a way that would map a vector of N elements to a vector of M
     * elements, where M > N. Such a situation is currently not supported by the Graal compiler
     * [GR-68216].
     * <p>
     * Works only for integral shapes. See {@link #compactGeneral} for the general implementation.
     * </p>
     */
    private static <E, F> Vector<F> compact(Vector<E> vec, int part, Shape<E> inShape, Shape<F> outShape) {
        // Works only for integral shapes.
        assert inShape.species().elementType() == short.class && outShape.species().elementType() == byte.class ||
                        inShape.species().elementType() == int.class && outShape.species().elementType() == short.class ||
                        inShape.species().elementType() == long.class && outShape.species().elementType() == int.class;
        VectorMask<F> mask = switch (part) {
            case 0 -> outShape.lowMask;
            case -1 -> outShape.highMask;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return vec.reinterpretShape(outShape.species(), 0).rearrange(outShape.compressEvensShuffle, mask);
    }

    /**
     * Like {@link #compact}, but generalized for non-integral input and output shapes.
     */
    private static <W, WI, N, NI> Vector<N> compactGeneral(Vector<W> vec, int part,
                    Shape<WI> wideIntegralShape, Shape<N> narrowShape,
                    VectorOperators.Conversion<W, N> downcast,
                    VectorOperators.Conversion<N, NI> asIntegral,
                    VectorOperators.Conversion<NI, WI> zeroExtend) {
        // NI and WI must be integral types, with NI being half the size of WI.
        assert zeroExtend.domainType() == byte.class && zeroExtend.rangeType() == short.class ||
                        zeroExtend.domainType() == short.class && zeroExtend.rangeType() == int.class ||
                        zeroExtend.domainType() == int.class && zeroExtend.rangeType() == long.class;
        VectorMask<N> mask = switch (part) {
            case 0 -> narrowShape.lowMask;
            case -1 -> narrowShape.highMask;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        VectorSpecies<N> halfSizeOutShape = narrowShape.species().withShape(VectorShape.S_64_BIT);
        Vector<N> down = vec.convertShape(downcast, halfSizeOutShape, 0);
        Vector<NI> downIntegral = down.convert(asIntegral, 0);
        Vector<WI> upIntegral = downIntegral.convertShape(zeroExtend, wideIntegralShape.species(), 0);
        Vector<N> narrowEvens = upIntegral.reinterpretShape(narrowShape.species(), 0);
        return narrowEvens.rearrange(narrowShape.compressEvensShuffle, mask);
    }

    private static <E, F> ByteVector narrow(ByteVector xBytes, ByteVector yBytes, Shape<E> inShape, Shape<F> outShape, long min, long max) {
        Vector<E> x = inShape.reinterpret(xBytes);
        Vector<E> y = inShape.reinterpret(yBytes);
        Vector<E> xSat = sat(x, inShape, min, max);
        Vector<E> ySat = sat(y, inShape, min, max);
        Vector<F> resultLow = compact(xSat, 0, inShape, outShape);
        Vector<F> resultHigh = compact(ySat, -1, inShape, outShape);
        Vector<F> result = resultLow.lanewise(VectorOperators.OR, resultHigh);
        return result.reinterpretAsBytes();
    }

    private static <E, F> ByteVector binop_sat(ByteVector xBytes, ByteVector yBytes,
                    Shape<E> shape, Shape<F> extendedShape,
                    VectorOperators.Conversion<E, F> upcast,
                    VectorOperators.Binary op, long min, long max) {
        return upcastBinopDowncast(xBytes, yBytes, shape, extendedShape, upcast, (x, y) -> {
            Vector<F> rawResult = x.lanewise(op, y);
            Vector<F> satResult = sat(rawResult, extendedShape, min, max);
            return satResult;
        });
    }

    private static <E, F> ByteVector avgr_u(ByteVector xBytes, ByteVector yBytes,
                    Shape<E> shape, Shape<F> extendedShape,
                    VectorOperators.Conversion<E, F> upcast) {
        Vector<F> one = extendedShape.broadcast(1);
        return upcastBinopDowncast(xBytes, yBytes, shape, extendedShape, upcast, (x, y) -> x.add(y).add(one).lanewise(VectorOperators.LSHR, 1));
    }

    private static ByteVector i16x8_q15mulr_sat_s(ByteVector xBytes, ByteVector yBytes) {
        return upcastBinopDowncast(xBytes, yBytes, I16X8, I32X4, VectorOperators.S2I, (x, y) -> {
            Vector<Integer> rawResult = x.mul(y).add(I32X4.broadcast(1 << 14)).lanewise(VectorOperators.ASHR, I32X4.broadcast(15));
            Vector<Integer> satResult = sat(rawResult, I32X4, Short.MIN_VALUE, Short.MAX_VALUE);
            return satResult;
        });
    }

    private static <E, F> ByteVector extmul(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Conversion<E, F> extend, int part) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<F> xExtended = x.convert(extend, part);
        Vector<F> yExtended = y.convert(extend, part);
        Vector<F> result = xExtended.mul(yExtended);
        return result.reinterpretAsBytes();
    }

    private static ByteVector i32x4_dot_i16x8_s(ByteVector xBytes, ByteVector yBytes) {
        ShortVector x = xBytes.reinterpretAsShorts();
        ShortVector y = yBytes.reinterpretAsShorts();
        Vector<Integer> xEvens = castInt128(x.compress(castShort128Mask(I16X8.evensMask)).convert(VectorOperators.S2I, 0));
        Vector<Integer> xOdds = castInt128(x.compress(castShort128Mask(I16X8.oddsMask)).convert(VectorOperators.S2I, 0));
        Vector<Integer> yEvens = castInt128(y.compress(castShort128Mask(I16X8.evensMask)).convert(VectorOperators.S2I, 0));
        Vector<Integer> yOdds = castInt128(y.compress(castShort128Mask(I16X8.oddsMask)).convert(VectorOperators.S2I, 0));
        Vector<Integer> xMulYEvens = xEvens.mul(yEvens);
        Vector<Integer> xMulYOdds = xOdds.mul(yOdds);
        Vector<Integer> dot = xMulYEvens.lanewise(VectorOperators.ADD, xMulYOdds);
        return dot.reinterpretAsBytes();
    }

    private static <E> ByteVector pmin(ByteVector xBytes, ByteVector yBytes, Shape<E> shape) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<E> result = x.blend(y, y.compare(VectorOperators.LT, x));
        return result.reinterpretAsBytes();
    }

    private static <E> ByteVector pmax(ByteVector xBytes, ByteVector yBytes, Shape<E> shape) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<E> result = x.blend(y, x.compare(VectorOperators.LT, y));
        return result.reinterpretAsBytes();
    }

    private static ByteVector i16x8_relaxed_dot_i8x16_i7x16_s(ByteVector x, ByteVector y) {
        Vector<Short> xEvens = castShort128(x.compress(castByte128Mask(I8X16.evensMask)).convert(VectorOperators.B2S, 0));
        Vector<Short> xOdds = castShort128(x.compress(castByte128Mask(I8X16.oddsMask)).convert(VectorOperators.B2S, 0));
        Vector<Short> yEvens = castShort128(y.compress(castByte128Mask(I8X16.evensMask)).convert(VectorOperators.B2S, 0));
        Vector<Short> yOdds = castShort128(y.compress(castByte128Mask(I8X16.oddsMask)).convert(VectorOperators.B2S, 0));
        Vector<Short> xMulYEvens = xEvens.mul(yEvens);
        Vector<Short> xMulYOdds = xOdds.mul(yOdds);
        Vector<Short> dot = xMulYEvens.lanewise(VectorOperators.SADD, xMulYOdds);
        return dot.reinterpretAsBytes();
    }

    private static ByteVector bitselect(ByteVector x, ByteVector y, ByteVector mask) {
        // y.bitwiseBlend(x, mask) would work too, but it doesn't play nice with native image
        // and ends up expanding to the bottom pattern anyway
        return y.lanewise(VectorOperators.XOR, y.lanewise(VectorOperators.XOR, x).lanewise(VectorOperators.AND, mask));
    }

    private static ByteVector f32x4_ternop(ByteVector xBytes, ByteVector yBytes, ByteVector zBytes, int vectorOpcode) {
        FloatVector x = xBytes.reinterpretAsFloats();
        FloatVector y = yBytes.reinterpretAsFloats();
        FloatVector z = zBytes.reinterpretAsFloats();
        FloatVector result = switch (vectorOpcode) {
            case Bytecode.VECTOR_F32X4_RELAXED_MADD -> x.lanewise(VectorOperators.FMA, y, z);
            case Bytecode.VECTOR_F32X4_RELAXED_NMADD -> x.neg().lanewise(VectorOperators.FMA, y, z);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return result.reinterpretAsBytes();
    }

    private static ByteVector f64x2_ternop(ByteVector xBytes, ByteVector yBytes, ByteVector zBytes, int vectorOpcode) {
        DoubleVector x = F64X2.reinterpret(xBytes);
        DoubleVector y = F64X2.reinterpret(yBytes);
        DoubleVector z = F64X2.reinterpret(zBytes);
        DoubleVector result = switch (vectorOpcode) {
            case Bytecode.VECTOR_F64X2_RELAXED_MADD -> x.lanewise(VectorOperators.FMA, y, z);
            case Bytecode.VECTOR_F64X2_RELAXED_NMADD -> castDouble128(x.neg()).lanewise(VectorOperators.FMA, y, z);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return result.reinterpretAsBytes();
    }

    private static ByteVector i32x4_relaxed_dot_i8x16_i7x16_add_s(ByteVector x, ByteVector y, ByteVector zBytes) {
        IntVector z = zBytes.reinterpretAsInts();
        ShortVector xEvens = castShort128(x.compress(castByte128Mask(I8X16.evensMask)).convert(VectorOperators.B2S, 0));
        ShortVector xOdds = castShort128(x.compress(castByte128Mask(I8X16.oddsMask)).convert(VectorOperators.B2S, 0));
        ShortVector yEvens = castShort128(y.compress(castByte128Mask(I8X16.evensMask)).convert(VectorOperators.B2S, 0));
        ShortVector yOdds = castShort128(y.compress(castByte128Mask(I8X16.oddsMask)).convert(VectorOperators.B2S, 0));
        ShortVector xMulYEvens = xEvens.mul(yEvens);
        ShortVector xMulYOdds = xOdds.mul(yOdds);
        ShortVector dot = xMulYEvens.lanewise(VectorOperators.SADD, xMulYOdds);
        IntVector dotEvens = castInt128(dot.compress(castShort128Mask(I16X8.evensMask)).convert(VectorOperators.S2I, 0));
        IntVector dotOdds = castInt128(dot.compress(castShort128Mask(I16X8.oddsMask)).convert(VectorOperators.S2I, 0));
        IntVector dots = dotEvens.add(dotOdds);
        IntVector result = dots.add(z);
        return result.reinterpretAsBytes();
    }

    private static int v128_any_true(ByteVector vec) {
        return vec.eq((byte) 0).allTrue() ? 0 : 1;
    }

    private static <E> int all_true(ByteVector vecBytes, Shape<E> shape) {
        Vector<E> vec = shape.reinterpret(vecBytes);
        return vec.eq(shape.zero()).anyTrue() ? 0 : 1;
    }

    private static <E> int bitmask(ByteVector vecBytes, Shape<E> shape) {
        Vector<E> vec = shape.reinterpret(vecBytes);
        VectorMask<E> mask = vec.lt(shape.zero());
        return (int) mask.toLong();
    }

    private static <E> ByteVector shiftop(ByteVector xBytes, int shift, Shape<E> shape, VectorOperators.Binary shiftOp) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> result = x.lanewise(shiftOp, shift);
        return result.reinterpretAsBytes();
    }

    // Checkstyle: resume method name check

    private static final String VECTOR_API_PACKAGE = Vector.class.getPackageName();

    private static final Class<? extends ByteVector> BYTE_128_CLASS = lookupClass(VECTOR_API_PACKAGE + ".Byte128Vector");
    private static final Class<? extends ShortVector> SHORT_128_CLASS = lookupClass(VECTOR_API_PACKAGE + ".Short128Vector");
    private static final Class<? extends IntVector> INT_128_CLASS = lookupClass(VECTOR_API_PACKAGE + ".Int128Vector");
    private static final Class<? extends LongVector> LONG_128_CLASS = lookupClass(VECTOR_API_PACKAGE + ".Long128Vector");
    private static final Class<? extends FloatVector> FLOAT_128_CLASS = lookupClass(VECTOR_API_PACKAGE + ".Float128Vector");
    private static final Class<? extends DoubleVector> DOUBLE_128_CLASS = lookupClass(VECTOR_API_PACKAGE + ".Double128Vector");

    private static final Class<? extends VectorMask<Byte>> BYTE_128_MASK_CLASS = lookupClass(VECTOR_API_PACKAGE + ".Byte128Vector$Byte128Mask");
    private static final Class<? extends VectorMask<Short>> SHORT_128_MASK_CLASS = lookupClass(VECTOR_API_PACKAGE + ".Short128Vector$Short128Mask");

    @SuppressWarnings("unchecked")
    private static <E> Class<? extends E> lookupClass(String className) {
        return (Class<? extends E>) Class.forName(Vector.class.getModule(), className);
    }

    private static ByteVector castByte128(Vector<Byte> vec) {
        return BYTE_128_CLASS.cast(vec);
    }

    private static ShortVector castShort128(Vector<Short> vec) {
        return SHORT_128_CLASS.cast(vec);
    }

    private static IntVector castInt128(Vector<Integer> vec) {
        return INT_128_CLASS.cast(vec);
    }

    private static LongVector castLong128(Vector<Long> vec) {
        return LONG_128_CLASS.cast(vec);
    }

    private static FloatVector castFloat128(Vector<Float> vec) {
        return FLOAT_128_CLASS.cast(vec);
    }

    private static DoubleVector castDouble128(Vector<Double> vec) {
        return DOUBLE_128_CLASS.cast(vec);
    }

    private static VectorMask<Byte> castByte128Mask(VectorMask<Byte> mask) {
        return BYTE_128_MASK_CLASS.cast(mask);
    }

    private static VectorMask<Short> castShort128Mask(VectorMask<Short> mask) {
        return SHORT_128_MASK_CLASS.cast(mask);
    }

    private static <E> Vector<E> sat(Vector<E> vec, Shape<E> shape, long min, long max) {
        Vector<E> vMin = shape.broadcast(min);
        Vector<E> vMax = shape.broadcast(max);
        return vec.max(vMin).min(vMax);
    }

    @SuppressWarnings("unused")
    private static LongVector truncSatU32(DoubleVector x) {
        VectorMask<Long> underflow = x.test(VectorOperators.IS_NAN).or(x.test(VectorOperators.IS_NEGATIVE)).cast(I64X2.species());
        VectorMask<Long> overflow = x.compare(VectorOperators.GT, F64X2.broadcast((double) 0xffff_ffffL)).cast(I64X2.species());
        LongVector zero = I64X2.zero();
        LongVector u32max = I64X2.broadcast(0xffff_ffffL);
        // GR-51421: D2L not supported by Graal compiler.
        LongVector trunc = castLong128(x.convert(VectorOperators.D2L, 0));
        return trunc.blend(u32max, overflow).blend(zero, underflow);
    }

    private static <E, F> ByteVector upcastBinopDowncast(ByteVector xBytes, ByteVector yBytes,
                    Shape<E> shape, Shape<F> extendedShape,
                    VectorOperators.Conversion<E, F> upcast,
                    BinaryVectorOp<F> op) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<F> xLow = x.convert(upcast, 0);
        Vector<F> xHigh = x.convert(upcast, 1);
        Vector<F> yLow = y.convert(upcast, 0);
        Vector<F> yHigh = y.convert(upcast, 1);
        Vector<E> resultLow = compact(op.apply(xLow, yLow), 0, extendedShape, shape);
        Vector<E> resultHigh = compact(op.apply(xHigh, yHigh), -1, extendedShape, shape);
        Vector<E> result = resultLow.lanewise(VectorOperators.OR, resultHigh);
        return result.reinterpretAsBytes();
    }

    @Override
    public ByteVector fromArray(byte[] bytes, int offset) {
        return ByteVector.fromArray(I8X16.species(), bytes, offset);
    }

    @Override
    public byte[] toArray(ByteVector vec) {
        return castByte128(vec).toArray();
    }

    @Override
    public void intoArray(ByteVector vec, byte[] array, int offset) {
        castByte128(vec).intoArray(array, offset);
    }

    @Override
    public Vector128 toVector128(ByteVector vec) {
        return new Vector128(castByte128(vec).toArray());
    }

    @Override
    public ByteVector fromVector128(Vector128 vector128) {
        return fromArray(vector128.getBytes());
    }
}
