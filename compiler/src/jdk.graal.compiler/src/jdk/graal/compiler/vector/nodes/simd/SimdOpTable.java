/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.vector.nodes.simd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.calc.FloatConvertCategory;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.ArithmeticOpWrapper;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.FloatConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.ReinterpretOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.ShiftOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.TernaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.SerializableConstant;

final class SimdOpTable implements ArithmeticOpWrapper {

    private static final EconomicMap<ArithmeticOpTable, ArithmeticOpTable> SIMD_TABLES = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

    // These exist so that libgraal construction can read these values from static fields.
    @SuppressWarnings("unused") private static final ArithmeticOpTable EMPTY_SIMD_TABLE = initSimdTable(ArithmeticOpTable.EMPTY);
    @SuppressWarnings("unused") private static final ArithmeticOpTable INTEGER_SIMD_TABLE = initSimdTable(IntegerStamp.OPS);
    @SuppressWarnings("unused") private static final ArithmeticOpTable FLOAT_SIMD_TABLE = initSimdTable(FloatStamp.OPS);
    @SuppressWarnings("unused") private static final ArithmeticOpTable OPMASK_SIMD_TABLE = initSimdTable(SimdStamp.OPMASK_OPS);

    private static ArithmeticOpTable initSimdTable(ArithmeticOpTable table) {
        ArithmeticOpTable simdTable = ArithmeticOpTable.wrap(new SimdOpTable(), table);
        SIMD_TABLES.put(table, simdTable);
        return simdTable;
    }

    private SimdOpTable() {
    }

    /**
     * Lift an {@link ArithmeticOpTable} operating on a scalar type to the domain of SIMD vectors on
     * that type.
     */
    public static ArithmeticOpTable get(ArithmeticOpTable scalarTable) {
        ArithmeticOpTable simdTable = SIMD_TABLES.get(scalarTable);
        if (simdTable == null) {
            throw GraalError.shouldNotReachHere("Unknown scalar table"); // ExcludeFromJacocoGeneratedReport
        }
        return simdTable;
    }

    /**
     * Lift an {@link jdk.graal.compiler.core.common.type.ArithmeticOpTable.Op} operating on a
     * scalar type to the domain of SIMD vectors on that type.
     */
    public static ArithmeticOpTable.Op get(ArithmeticStamp scalarStamp, ArithmeticOpTable.Op scalarOp) {
        ArithmeticOpTable scalarTable = scalarStamp.getOps();
        ArithmeticOpTable simdTable = get(scalarTable);
        if (scalarOp instanceof ShiftOp<?> shift) {
            int idx = 0;
            for (ShiftOp<?> scalar : scalarTable.getShiftOps()) {
                if (scalar != null && scalar.equals(shift)) {
                    return simdTable.getShiftOps()[idx];
                }
                idx++;
            }
        } else if (scalarOp instanceof FloatConvertOp floatConvert) {
            return simdTable.getFloatConvert(floatConvert.getFloatConvert());
        } else if (scalarOp instanceof BinaryOp<?> binary) {
            int idx = 0;
            for (BinaryOp<?> scalar : scalarTable.getBinaryOps()) {
                if (scalar != null && scalar.equals(binary)) {
                    return simdTable.getBinaryOps()[idx];
                }
                idx++;
            }
        } else if (scalarOp instanceof UnaryOp<?> unary) {
            int idx = 0;
            for (UnaryOp<?> scalar : scalarTable.getUnaryOps()) {
                if (scalar != null && scalar.equals(unary)) {
                    return simdTable.getUnaryOps()[idx];
                }
                idx++;
            }
        } else if (scalarOp instanceof IntegerConvertOp<?> integerConvert) {
            int idx = 0;
            for (IntegerConvertOp<?> scalar : scalarTable.getIntegerConvertOps()) {
                if (scalar != null && scalar.equals(integerConvert)) {
                    return simdTable.getIntegerConvertOps()[idx];
                }
                idx++;
            }
        } else if (scalarOp.equals(scalarTable.getFMA())) {
            return simdTable.getFMA();
        } else if (scalarOp.equals(scalarTable.getReinterpret())) {
            return simdTable.getReinterpret();
        }
        throw GraalError.shouldNotReachHere("Could not lift op %s from %s to SIMD version in SIMD table %s".formatted(scalarOp, scalarStamp, simdTable));
    }

    @Override
    public <OP> UnaryOp<OP> wrapUnaryOp(final UnaryOp<OP> scalarOp) {
        return new UnaryOp<>(scalarOp.toString()) {

            @Override
            public UnaryOp<OP> unwrap() {
                return scalarOp;
            }

            @Override
            public Constant foldConstant(Constant value) {
                SimdConstant c = (SimdConstant) value;
                Constant[] ret = new Constant[c.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = scalarOp.foldConstant(c.getValue(i));
                }
                return new SimdConstant(ret);
            }

            @Override
            protected Stamp foldStampImpl(Stamp stamp) {
                SimdStamp s = (SimdStamp) stamp;
                ArithmeticStamp[] ret = new ArithmeticStamp[s.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = (ArithmeticStamp) scalarOp.foldStamp(s.getComponent(i));
                }
                return new SimdStamp(ret);
            }
        };
    }

    @Override
    public <OP> BinaryOp<OP> wrapBinaryOp(final BinaryOp<OP> scalarOp) {
        return new BinaryOp<>(scalarOp.toString(), scalarOp.isAssociative(), scalarOp.isCommutative()) {

            @Override
            public BinaryOp<OP> unwrap() {
                return scalarOp;
            }

            @Override
            public Constant foldConstant(Constant const1, Constant const2) {
                SimdConstant a = (SimdConstant) const1;
                SimdConstant b = (SimdConstant) const2;

                assert a.getVectorLength() == b.getVectorLength() : a + " " + a.getVectorLength() + " vs " + b + " " + b.getVectorLength();
                Constant[] ret = new Constant[a.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    Constant folded = scalarOp.foldConstant(a.getValue(i), b.getValue(i));
                    if (folded == null) {
                        return null;
                    }
                    ret[i] = folded;
                }
                return new SimdConstant(ret);
            }

            @Override
            protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
                SimdStamp a = (SimdStamp) stamp1;
                SimdStamp b = (SimdStamp) stamp2;

                assert a.getVectorLength() == b.getVectorLength() : a + " " + a.getVectorLength() + " vs " + b + " " + b.getVectorLength();
                ArithmeticStamp[] ret = new ArithmeticStamp[a.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = (ArithmeticStamp) scalarOp.foldStamp(a.getComponent(i), b.getComponent(i));
                }
                return new SimdStamp(ret);
            }

            @Override
            public boolean isNeutral(Constant n) {
                SimdConstant c = (SimdConstant) n;
                for (int i = 0; i < c.getVectorLength(); i++) {
                    if (!scalarOp.isNeutral(c.getValue(i))) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Constant getZero(Stamp stamp) {
                SimdStamp s = (SimdStamp) stamp;
                Constant[] zero = new Constant[s.getVectorLength()];
                for (int i = 0; i < zero.length; i++) {
                    Constant z = scalarOp.getZero(s.getComponent(i));
                    if (z == null) {
                        return null;
                    }
                    zero[i] = z;
                }
                return new SimdConstant(zero);
            }
        };
    }

    @Override
    public <OP> TernaryOp<OP> wrapTernaryOp(final TernaryOp<OP> scalarOp) {
        return new TernaryOp<>(scalarOp.toString()) {
            @Override
            public Constant foldConstant(Constant a, Constant b, Constant c) {
                SimdConstant sa = (SimdConstant) a;
                SimdConstant sb = (SimdConstant) b;
                SimdConstant sc = (SimdConstant) c;

                assert sa.getVectorLength() == sb.getVectorLength() && sa.getVectorLength() == sc.getVectorLength() : sa + " " + sa.getVectorLength() + " vs " + sb + " " + sb.getVectorLength() +
                                " vs " + sc + " " + sc.getVectorLength();
                Constant[] ret = new Constant[sa.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    Constant folded = scalarOp.foldConstant(sa.getValue(i), sb.getValue(i), sc.getValue(i));
                    if (folded == null) {
                        return null;
                    }
                    ret[i] = folded;
                }
                return new SimdConstant(ret);
            }

            @Override
            public Stamp foldStamp(Stamp a, Stamp b, Stamp c) {
                SimdStamp sa = (SimdStamp) a;
                SimdStamp sb = (SimdStamp) b;
                SimdStamp sc = (SimdStamp) c;

                assert sa.getVectorLength() == sb.getVectorLength() && sa.getVectorLength() == sc.getVectorLength() : sa + " " + sa.getVectorLength() + " vs " + sb + " " + sb.getVectorLength() +
                                " vs " + sc + " " + sc.getVectorLength();
                ArithmeticStamp[] ret = new ArithmeticStamp[sa.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = (ArithmeticStamp) scalarOp.foldStamp(sa.getComponent(i), sb.getComponent(i), sc.getComponent(i));
                }
                return new SimdStamp(ret);
            }
        };
    }

    @Override
    public <OP> ShiftOp<OP> wrapShiftOp(final ShiftOp<OP> scalarOp) {
        return new ShiftOp<>(scalarOp.toString()) {

            @Override
            public Constant foldConstant(Constant value, Constant amount) {
                SimdConstant c = (SimdConstant) value;
                Constant[] ret = new Constant[c.getVectorLength()];
                if (amount instanceof SimdConstant s) {
                    for (int i = 0; i < ret.length; i++) {
                        ret[i] = scalarOp.foldConstant(c.getValue(i), s.getValue(i));
                    }
                } else {
                    for (int i = 0; i < ret.length; i++) {
                        ret[i] = scalarOp.foldConstant(c.getValue(i), amount);
                    }
                }

                return new SimdConstant(ret);
            }

            @Override
            public Stamp foldStampImpl(Stamp stamp, Stamp amount) {
                SimdStamp a = (SimdStamp) stamp;

                ArithmeticStamp[] ret = new ArithmeticStamp[a.getVectorLength()];
                if (amount instanceof SimdStamp s) {
                    for (int i = 0; i < ret.length; i++) {
                        ret[i] = (ArithmeticStamp) scalarOp.foldStamp(a.getComponent(i), s.getComponent(i));
                    }
                } else {
                    IntegerStamp s = (IntegerStamp) amount;
                    for (int i = 0; i < ret.length; i++) {
                        ret[i] = (ArithmeticStamp) scalarOp.foldStamp(a.getComponent(i), s);
                    }
                }
                return new SimdStamp(ret);
            }

            private boolean assertShiftAmountMask(SimdStamp s) {
                int amount = scalarOp.getShiftAmountMask(s.getComponent(0));
                for (int i = 1; i < s.getVectorLength(); i++) {
                    assert amount == scalarOp.getShiftAmountMask(s.getComponent(i)) : amount + "!=" + scalarOp.getShiftAmountMask(s.getComponent(i)) + " for " + s.getComponent(i);
                }
                return true;
            }

            @Override
            public boolean isNeutral(Constant c) {
                if (!(c instanceof SimdConstant sc)) {
                    return false;
                }

                for (int i = 0; i < sc.getVectorLength(); i++) {
                    if (!scalarOp.isNeutral(sc.getValue(i))) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public int getShiftAmountMask(Stamp stamp) {
                SimdStamp s = (SimdStamp) stamp;
                assert assertShiftAmountMask(s);
                return scalarOp.getShiftAmountMask(s.getComponent(0));
            }
        };
    }

    @Override
    public <OP> IntegerConvertOp<OP> wrapIntegerConvertOp(final IntegerConvertOp<OP> scalarOp) {
        return new IntegerConvertOp<>(scalarOp.toString()) {

            @Override
            public IntegerConvertOp<OP> unwrap() {
                return scalarOp;
            }

            @Override
            public Constant foldConstant(int inputBits, int resultBits, Constant value) {
                SimdConstant c = (SimdConstant) value;
                Constant[] ret = new Constant[c.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = scalarOp.foldConstant(inputBits, resultBits, c.getValue(i));
                }
                return new SimdConstant(ret);
            }

            @Override
            public Stamp foldStamp(int inputBits, int resultBits, Stamp stamp) {
                SimdStamp s = (SimdStamp) stamp;
                ArithmeticStamp[] ret = new ArithmeticStamp[s.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = (ArithmeticStamp) scalarOp.foldStamp(inputBits, resultBits, s.getComponent(i));
                }
                return new SimdStamp(ret);
            }

            @Override
            public Stamp invertStamp(int inputBits, int resultBits, Stamp outStamp) {
                SimdStamp s = (SimdStamp) outStamp;
                ArithmeticStamp[] ret = new ArithmeticStamp[s.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = (ArithmeticStamp) scalarOp.invertStamp(inputBits, resultBits, s.getComponent(i));
                    if (ret[i] == null) {
                        // stamp inversion is not supported
                        return null;
                    }
                }
                return new SimdStamp(ret);
            }
        };
    }

    @Override
    public ReinterpretOp wrapReinterpretOp(ReinterpretOp scalarOp) {
        return new ReinterpretOp() {

            @Override
            public Constant foldConstant(Stamp resultStamp, Constant value) {
                // Constant folding uses the op table of the result stamp so this must be SimdStamp
                SimdStamp toStamp = (SimdStamp) resultStamp;
                int eSize = PrimitiveStamp.getBits(toStamp.getComponent(0)) / Byte.SIZE;
                int size = eSize * toStamp.getVectorLength();
                GraalError.guarantee(value instanceof SerializableConstant s && s.getSerializedSize() == size, "%s - %s", resultStamp, value);

                ByteBuffer buffer = ByteBuffer.wrap(new byte[size]).order(ByteOrder.nativeOrder());
                buffer.mark();
                ((SerializableConstant) value).serialize(buffer);
                buffer.reset();
                Constant[] elements = new Constant[toStamp.getVectorLength()];
                for (int i = 0; i < toStamp.getVectorLength(); i++) {
                    elements[i] = Objects.requireNonNull(((PrimitiveStamp) toStamp.getComponent(i)).deserialize(buffer));
                }
                return new SimdConstant(elements);
            }

            @Override
            public Stamp foldStamp(Stamp resultStamp, Stamp input) {
                // Stamp folding uses the op table of the input stamp so this must be SimdStamp
                SimdStamp s = (SimdStamp) input;
                int fromBits = PrimitiveStamp.getBits(s.getComponent(0));
                SimdStamp r;
                if (resultStamp instanceof SimdStamp rs) {
                    int toBits = PrimitiveStamp.getBits(rs.getComponent(0));
                    GraalError.guarantee(fromBits * s.getVectorLength() == toBits * rs.getVectorLength(), "%s - %s", rs, s);
                    r = rs;
                    if (fromBits != toBits) {
                        // We would need to try folding multiple elements into one or vice versa
                        // such as i64 -> <i32, i32>. Don't try.
                        return resultStamp;
                    }
                } else {
                    int toBits = PrimitiveStamp.getBits(resultStamp);
                    if (fromBits != toBits) {
                        GraalError.guarantee(fromBits * s.getVectorLength() == toBits, "%s - %s", resultStamp, s);
                        // We would need to try folding multiple elements into one or vice versa
                        // such as i64 -> <i32, i32>. Don't try.
                        return resultStamp;
                    }
                    r = null;
                }

                ArithmeticStamp[] ret = new ArithmeticStamp[s.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    Stamp componentStamp = r == null ? resultStamp : r.getComponent(i);
                    ret[i] = (ArithmeticStamp) scalarOp.foldStamp(componentStamp, s.getComponent(i));
                }
                return new SimdStamp(ret);
            }
        };
    }

    @Override
    public FloatConvertOp wrapFloatConvertOp(final FloatConvertOp scalarOp) {
        return new FloatConvertOp(scalarOp.getFloatConvert()) {

            @Override
            public FloatConvertOp unwrap() {
                return scalarOp;
            }

            @Override
            public Constant foldConstant(Constant value) {
                SimdConstant c = (SimdConstant) value;
                Constant[] ret = new Constant[c.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = scalarOp.foldConstant(c.getValue(i));
                }
                return new SimdConstant(ret);
            }

            @Override
            protected Stamp foldStampImpl(Stamp stamp) {
                SimdStamp s = (SimdStamp) stamp;
                ArithmeticStamp[] ret = new ArithmeticStamp[s.getVectorLength()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = (ArithmeticStamp) scalarOp.foldStamp(s.getComponent(i));
                }
                return new SimdStamp(ret);
            }

            @Override
            public boolean inputCanBeNaN(Stamp inputStamp) {
                SimdStamp s = (SimdStamp) inputStamp;
                for (int i = 0; i < s.getVectorLength(); i++) {
                    if (scalarOp.inputCanBeNaN(s.getComponent(i))) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean canOverflowInteger(Stamp inputStamp) {
                if (!scalarOp.getFloatConvert().getCategory().equals(FloatConvertCategory.FloatingPointToInteger)) {
                    return false;
                }
                SimdStamp s = (SimdStamp) inputStamp;
                for (int i = 0; i < s.getVectorLength(); i++) {
                    if (scalarOp.canOverflowInteger(s.getComponent(i))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
