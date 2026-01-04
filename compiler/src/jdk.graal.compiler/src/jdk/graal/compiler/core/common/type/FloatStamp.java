/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.type;

import static jdk.graal.compiler.core.common.calc.FloatConvert.D2F;
import static jdk.graal.compiler.core.common.calc.FloatConvert.D2I;
import static jdk.graal.compiler.core.common.calc.FloatConvert.D2L;
import static jdk.graal.compiler.core.common.calc.FloatConvert.D2UI;
import static jdk.graal.compiler.core.common.calc.FloatConvert.D2UL;
import static jdk.graal.compiler.core.common.calc.FloatConvert.F2D;
import static jdk.graal.compiler.core.common.calc.FloatConvert.F2I;
import static jdk.graal.compiler.core.common.calc.FloatConvert.F2L;
import static jdk.graal.compiler.core.common.calc.FloatConvert.F2UI;
import static jdk.graal.compiler.core.common.calc.FloatConvert.F2UL;

import java.nio.ByteBuffer;
import java.util.function.DoubleBinaryOperator;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.ReinterpretUtils;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SerializableConstant;

/**
 * A {@code FloatStamp} represents the value set of a {@code float} or {@code double} variable.
 *
 * <p>
 * The representation of a {@code FloatStamp} consists of a pair of bounds and a boolean denoting
 * whether NaN is a possible value of the variable:
 * <ul>
 * <li>When the intersection of the stamp and [-Inf, +Inf] is not empty, [lowerBound, upperBound] is
 * the interval representing that intersection.
 * <li>When the intersection of the stamp and [-Inf, +Inf] is empty, there is only 2 possible
 * states, the empty stamp with {@code lowerBound = +Inf}, {@code upperBound = -Inf},
 * {@code nonNaN = true}, and the NaN stamp with {@code lowerBound = upperBound = NaN},
 * {@code nonNaN = false}.
 * </ul>
 *
 * <p>
 * The bound information of an empty stamp is purely marking values and must not be accessed, nonNaN
 * still retains its meaning and can be queried as normal.
 */
public final class FloatStamp extends PrimitiveStamp {

    private final double lowerBound;
    private final double upperBound;
    private final boolean nonNaN;

    private FloatStamp(int bits, double lowerBound, double upperBound, boolean nonNaN) {
        super(bits, OPS);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.nonNaN = nonNaN;
    }

    public static FloatStamp create(int bits, double lowerBound, double upperBound, boolean nonNaN) {
        assert bits == Double.SIZE || bits == Float.SIZE : Assertions.errorMessage(bits);
        if (bits == Float.SIZE) {
            assert Double.compare((float) lowerBound, lowerBound) == 0 : Assertions.errorMessage(lowerBound);
            assert Double.compare((float) upperBound, upperBound) == 0 : Assertions.errorMessage(upperBound);
        }
        assert Double.isNaN(lowerBound) == Double.isNaN(upperBound) : Assertions.errorMessage(lowerBound, upperBound);
        assert !(Double.isNaN(lowerBound) && nonNaN) : Assertions.errorMessage(lowerBound, nonNaN);
        assert !(lowerBound > upperBound) : Assertions.errorMessage(lowerBound, upperBound);
        return new FloatStamp(bits, lowerBound, upperBound, nonNaN);
    }

    public static FloatStamp createUnrestricted(int bits) {
        assert bits == Double.SIZE || bits == Float.SIZE : Assertions.errorMessage(bits);
        return bits == Double.SIZE ? ConstantCache.DOUBLE_BOTTOM : ConstantCache.FLOAT_BOTTOM;
    }

    public static FloatStamp createEmpty(int bits) {
        assert bits == Double.SIZE || bits == Float.SIZE : Assertions.errorMessage(bits);
        return bits == Double.SIZE ? ConstantCache.DOUBLE_TOP : ConstantCache.FLOAT_TOP;
    }

    public static FloatStamp createNaN(int bits) {
        assert bits == Double.SIZE || bits == Float.SIZE : Assertions.errorMessage(bits);
        return bits == Double.SIZE ? ConstantCache.DOUBLE_NAN : ConstantCache.FLOAT_NAN;
    }

    @Override
    public FloatStamp unrestricted() {
        return createUnrestricted(getBits());
    }

    @Override
    public FloatStamp empty() {
        return createEmpty(getBits());
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        JavaConstant jc = (JavaConstant) c;
        assert jc.getJavaKind().isNumericFloat() && jc.getJavaKind().getBitCount() == getBits() : Assertions.errorMessage(jc, getBits());
        return StampFactory.forConstant(jc);
    }

    @Override
    public SerializableConstant deserialize(ByteBuffer buffer) {
        switch (getBits()) {
            case 32:
                return JavaConstant.forFloat(buffer.getFloat());
            case 64:
                return JavaConstant.forDouble(buffer.getDouble());
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(getBits()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public boolean hasValues() {
        // The empty stamp is the only one with lowerBound > upperBound since for the NaN stamp, we
        // have NaN > NaN = false
        return !(lowerBound > upperBound);
    }

    @Override
    public JavaKind getStackKind() {
        if (getBits() > 32) {
            return JavaKind.Double;
        } else {
            return JavaKind.Float;
        }
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getFloatingKind(getBits());
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        switch (getBits()) {
            case 32:
                return metaAccess.lookupJavaType(Float.TYPE);
            case 64:
                return metaAccess.lookupJavaType(Double.TYPE);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(getBits()); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * The (inclusive) lower bound on the value described by this stamp.
     */
    public double lowerBound() {
        assert hasValues() : "Empty stamp";
        return lowerBound;
    }

    /**
     * The (inclusive) upper bound on the value described by this stamp.
     */
    public double upperBound() {
        assert hasValues() : "Empty stamp";
        return upperBound;
    }

    private float lowerBound32() {
        assert getBits() == Float.SIZE : "Must be float";
        return (float) lowerBound();
    }

    private float upperBound32() {
        assert getBits() == Float.SIZE : "Must be float";
        return (float) upperBound();
    }

    /**
     * Returns true if NaN is not included in the value described by this stamp.
     */
    public boolean isNonNaN() {
        return nonNaN;
    }

    /**
     * Returns true if NaN is included in the value described by this stamp.
     */
    public boolean canBeNaN() {
        return !nonNaN;
    }

    /**
     * Returns true if this stamp represents the NaN value.
     */
    public boolean isNaN() {
        return Double.isNaN(lowerBound);
    }

    @Override
    public boolean isUnrestricted() {
        return lowerBound == Double.NEGATIVE_INFINITY && upperBound == Double.POSITIVE_INFINITY && !nonNaN;
    }

    private boolean canBeNInf() {
        return lowerBound == Double.NEGATIVE_INFINITY;
    }

    private boolean canBePInf() {
        return upperBound == Double.POSITIVE_INFINITY;
    }

    private boolean canBeInf() {
        return canBeNInf() || canBePInf();
    }

    public boolean contains(double value) {
        if (Double.isNaN(value)) {
            return !nonNaN;
        } else {
            /*
             * Don't use Double.compare for checking the bounds as -0.0 isn't correctly tracked, so
             * the presence of 0.0 means -0.0 might also exist in the range.
             */
            return value >= lowerBound && value <= upperBound;
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append('f');
        str.append(getBits());
        if (hasValues()) {
            str.append(nonNaN ? "!" : "");
            if (lowerBound == upperBound) {
                str.append(" [").append(lowerBound).append(']');
            } else if (lowerBound != Double.NEGATIVE_INFINITY || upperBound != Double.POSITIVE_INFINITY) {
                str.append(" [").append(lowerBound).append(" - ").append(upperBound).append(']');
            }
        } else {
            str.append("<empty>");
        }
        return str.toString();
    }

    private static double meetBounds(double a, double b, DoubleBinaryOperator op) {
        if (Double.isNaN(a)) {
            return b;
        } else if (Double.isNaN(b)) {
            return a;
        } else {
            return op.applyAsDouble(a, b);
        }
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        } else if (isEmpty()) {
            return otherStamp;
        } else if (otherStamp.isEmpty()) {
            return this;
        }
        FloatStamp other = (FloatStamp) otherStamp;
        assert getBits() == other.getBits() : "Bits must match " + Assertions.errorMessageContext("thisBits", getBits(), "otherBits", other.getBits(), "other", other);
        double meetUpperBound = meetBounds(upperBound, other.upperBound, Math::max);
        double meetLowerBound = meetBounds(lowerBound, other.lowerBound, Math::min);
        boolean meetNonNaN = nonNaN && other.nonNaN;
        if (Double.compare(meetLowerBound, lowerBound) == 0 && Double.compare(meetUpperBound, upperBound) == 0 && meetNonNaN == nonNaN) {
            return this;
        } else if (Double.compare(meetLowerBound, other.lowerBound) == 0 && Double.compare(meetUpperBound, other.upperBound) == 0 && meetNonNaN == other.nonNaN) {
            return other;
        } else {
            return new FloatStamp(getBits(), meetLowerBound, meetUpperBound, meetNonNaN);
        }
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        } else if (isEmpty()) {
            return this;
        } else if (otherStamp.isEmpty()) {
            return otherStamp;
        }
        FloatStamp other = (FloatStamp) otherStamp;
        assert getBits() == other.getBits() : "Bits must match " + Assertions.errorMessageContext("thisBits", getBits(), "otherBits", other.getBits(), "other", other);
        double joinUpperBound = Math.min(upperBound, other.upperBound);
        double joinLowerBound = Math.max(lowerBound, other.lowerBound);
        boolean joinNonNaN = nonNaN || other.nonNaN;
        if (joinLowerBound > joinUpperBound) {
            // joinLowerBound > joinUpperBound means an empty set in the non-NaN range, use NaN
            // bounds to mark this as it is consistent with other cases that can result in this
            // situation, e.g one of the stamp is NaN
            joinLowerBound = Double.NaN;
            joinUpperBound = Double.NaN;
        }
        if (Double.isNaN(joinLowerBound) && joinNonNaN) {
            // an empty range in the non-NaN region and an empty range in the NaN region means that
            // the resulting stamp is empty
            return empty();
        }
        if (Double.compare(joinLowerBound, lowerBound) == 0 && Double.compare(joinUpperBound, upperBound) == 0 && joinNonNaN == nonNaN) {
            return this;
        } else if (Double.compare(joinLowerBound, other.lowerBound) == 0 && Double.compare(joinUpperBound, other.upperBound) == 0 && joinNonNaN == other.nonNaN) {
            return other;
        } else {
            return new FloatStamp(getBits(), joinLowerBound, joinUpperBound, joinNonNaN);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        result = prime * result + super.hashCode();
        temp = Double.doubleToLongBits(lowerBound);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (nonNaN ? 1231 : 1237);
        temp = Double.doubleToLongBits(upperBound);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        if (this == stamp) {
            return true;
        }
        if (stamp instanceof FloatStamp) {
            FloatStamp other = (FloatStamp) stamp;
            return getBits() == other.getBits();
        }
        return false;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        if (constant instanceof PrimitiveConstant) {
            PrimitiveConstant prim = (PrimitiveConstant) constant;
            JavaKind kind = prim.getJavaKind();
            return kind.isNumericFloat() && kind.getBitCount() == getBits();
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass() || !super.equals(obj)) {
            return false;
        }
        FloatStamp other = (FloatStamp) obj;
        if (Double.doubleToLongBits(lowerBound) != Double.doubleToLongBits(other.lowerBound)) {
            return false;
        }
        if (Double.doubleToLongBits(upperBound) != Double.doubleToLongBits(other.upperBound)) {
            return false;
        }
        if (nonNaN != other.nonNaN) {
            return false;
        }
        return super.equals(other);
    }

    @Override
    public JavaConstant asConstant() {
        if (isConstant()) {
            switch (getBits()) {
                case 32:
                    return JavaConstant.forFloat((float) lowerBound);
                case 64:
                    return JavaConstant.forDouble(lowerBound);
            }
        }
        return null;
    }

    private boolean isConstant() {
        /*
         * There are many forms of NaNs and any operations on them can silently convert them into
         * the canonical NaN.
         *
         * We need to exclude 0 here since it can contain -0.0 && 0.0 .
         */
        return lowerBound == upperBound && nonNaN && lowerBound != 0;
    }

    private static FloatStamp stampForConstant(Constant constant) {
        FloatStamp result;
        PrimitiveConstant value = (PrimitiveConstant) constant;
        switch (value.getJavaKind()) {
            case Float:
                if (Float.isNaN(value.asFloat())) {
                    result = new FloatStamp(32, Double.NaN, Double.NaN, false);
                } else {
                    result = new FloatStamp(32, value.asFloat(), value.asFloat(), true);
                }
                break;
            case Double:
                if (Double.isNaN(value.asDouble())) {
                    result = new FloatStamp(64, Double.NaN, Double.NaN, false);
                } else {
                    result = new FloatStamp(64, value.asDouble(), value.asDouble(), true);
                }
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
        }
        if (result.isConstant()) {
            return result;
        }
        return null;
    }

    private static Stamp maybeFoldConstant(ArithmeticOpTable.BinaryOp<?> op, FloatStamp stamp1, FloatStamp stamp2) {
        if (stamp1.isConstant() && stamp2.isConstant()) {
            JavaConstant constant1 = stamp1.asConstant();
            JavaConstant constant2 = stamp2.asConstant();
            Constant folded = op.foldConstant(constant1, constant2);
            if (folded != null) {
                FloatStamp stamp = stampForConstant(folded);
                if (stamp != null && stamp.isConstant()) {
                    assert stamp.asConstant().equals(folded);
                    return stamp;
                }
            }
        }
        return null;
    }

    public static boolean floatingToIntegerCanOverflow(FloatStamp floatStamp, int integerBits) {
        return floatingToIntegerCanOverflow(floatStamp, integerBits, false);
    }

    public static boolean floatingToIntegerCanOverflow(FloatStamp floatStamp, int integerBits, boolean unsigned) {
        final boolean canBeInfinity = Double.isInfinite(floatStamp.lowerBound()) || Double.isInfinite(floatStamp.upperBound());
        if (canBeInfinity) {
            return true;
        }
        final boolean conversionCanOverflow = integralPartLargerMaxValue(floatStamp, integerBits, unsigned) || integralPartSmallerMinValue(floatStamp, integerBits, unsigned);
        return conversionCanOverflow;
    }

    private static boolean integralPartLargerMaxValue(FloatStamp floatStamp, int integerBits, boolean unsigned) {
        double upperBound = floatStamp.upperBound();
        if (Double.isInfinite(upperBound) || Double.isNaN(upperBound)) {
            return true;
        }
        assert integerBits == 32 || integerBits == 64 : "Must be int or long " + Assertions.errorMessage(integerBits, upperBound);
        double maxValue = unsigned
                        ? NumUtil.unsignedToDouble(NumUtil.maxValueUnsigned(integerBits))
                        : NumUtil.maxValue(integerBits);
        /*
         * There could be conversion loss here when casting maxValue from long to double - given
         * that max can be Long.MAX_VALUE. In order to avoid checking if upperBound is larger than
         * max with arbitrary precision numbers (BigDecimal for example) we use some trick.
         *
         * (double) Long.MAX_VALUE is 9223372036854775808, which is 1 more than Long.MAX_VALUE. So
         * (long) (double) Long.MAX_VALUE will already overflow. So we need upperBound >= (double)
         * maxValue, with >= instead of >. The next lower double value is Math.nextDown((double)
         * Long.MAX_VALUE) == 9223372036854774784, which fits into long. So if upperBound >=
         * (double) maxValue does not hold, i.e., upperBound < (double) maxValue does hold, then
         * (long) upperBound will not overflow.
         *
         * Similarly, for the unsigned case, unsignedToDouble(-1L) is 18446744073709551616 (2**64),
         * which is 1 more than the unsigned long max value, i.e. 18446744073709551615 (2**64 - 1).
         * So toUnsignedLong(unsignedToDouble(-1L)) will already overflow. The next lower double
         * value is Math.nextDown(0x1p64) == 18446744073709549568, which fits into unsigned long.
         * The same overflow condition reasoning as for the signed long case applies.
         */
        return upperBound >= maxValue;
    }

    private static boolean integralPartSmallerMinValue(FloatStamp floatStamp, int integerBits, boolean unsigned) {
        double lowerBound = floatStamp.lowerBound();
        if (Double.isInfinite(lowerBound) || Double.isNaN(lowerBound)) {
            return true;
        }
        assert integerBits == 32 || integerBits == 64 : "Must be int or long " + Assertions.errorMessage(integerBits, lowerBound);
        double minValue = unsigned ? 0 : NumUtil.minValue(integerBits);
        return lowerBound <= minValue;
    }

    public static final ArithmeticOpTable OPS = new ArithmeticOpTable(

                    new ArithmeticOpTable.UnaryOp.Neg() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return switch (value.getJavaKind()) {
                                case Float -> JavaConstant.forFloat(-value.asFloat());
                                case Double -> JavaConstant.forDouble(-value.asDouble());
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            FloatStamp stamp = (FloatStamp) s;
                            return new FloatStamp(stamp.getBits(), -stamp.upperBound(), -stamp.lowerBound(), stamp.isNonNaN());
                        }

                    },

                    new ArithmeticOpTable.BinaryOp.Add(false, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            return switch (a.getJavaKind()) {
                                case Float -> JavaConstant.forFloat(a.asFloat() + b.asFloat());
                                case Double -> JavaConstant.forDouble(a.asDouble() + b.asDouble());
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            } else if (s2.isEmpty()) {
                                return s2;
                            }

                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;

                            boolean isNonNaN = true;
                            if (stamp1.canBeNaN() || stamp2.canBeNaN()) {
                                isNonNaN = false;
                            } else if ((stamp1.canBeNInf() && stamp2.canBePInf()) || (stamp1.canBePInf() && stamp2.canBeNInf())) {
                                isNonNaN = false;
                            }

                            double lowerBound;
                            double upperBound;
                            /*
                             * x + y is an increasing function with respect to both x and y by the
                             * order imposed by Double.compare, which means that:
                             *
                             * Double.compare(x1, x2) <= 0 necessarily leads to:
                             *
                             * @formatter:off
                             *
                             * Double.compare(x1 + y, x2 + y) <= 0
                             * Double.compare(y + x1, y + x2) <= 0
                             *
                             * @formatter:on
                             *
                             * As a result:
                             *
                             * @formatter:off
                             *
                             * res.lower = s1.lower + s2.lower
                             * res.upper = s1.upper + s2.upper
                             *
                             * @formatter:on
                             */
                            if (stamp1.getBits() == Float.SIZE) {
                                lowerBound = stamp1.lowerBound32() + stamp2.lowerBound32();
                                upperBound = stamp1.upperBound32() + stamp2.upperBound32();
                            } else {
                                lowerBound = stamp1.lowerBound() + stamp2.lowerBound();
                                upperBound = stamp1.upperBound() + stamp2.upperBound();
                            }

                            // If either stamp is a constant Inf, the bound calculations may result
                            // in NaN, strip that value

                            // Ignoring NaN, if s1 is a constant +Inf, s2 can have values > -Inf,
                            // then the result can only be +Inf. Otherwise s2 is a constant -Inf,
                            // then the result does not have any value in the non-NaN region
                            if ((stamp1.lowerBound() == Double.POSITIVE_INFINITY && stamp2.upperBound() > Double.NEGATIVE_INFINITY) ||
                                            (stamp1.upperBound() > Double.NEGATIVE_INFINITY && stamp2.lowerBound() == Double.POSITIVE_INFINITY)) {
                                lowerBound = Double.POSITIVE_INFINITY;
                            }
                            // Similar to above, if s1 is a constant -Inf
                            if ((stamp1.lowerBound() < Double.POSITIVE_INFINITY && stamp2.upperBound() == Double.NEGATIVE_INFINITY) ||
                                            (stamp1.upperBound() == Double.NEGATIVE_INFINITY && stamp2.lowerBound() < Double.POSITIVE_INFINITY)) {
                                upperBound = Double.NEGATIVE_INFINITY;
                            }
                            return new FloatStamp(stamp1.getBits(), lowerBound, upperBound, isNonNaN);
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return switch (n.getJavaKind()) {
                                case Float -> Float.compare(n.asFloat(), -0.0f) == 0;
                                case Double -> Double.compare(n.asDouble(), -0.0) == 0;
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(n.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Sub(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            return switch (a.getJavaKind()) {
                                case Float -> JavaConstant.forFloat(a.asFloat() - b.asFloat());
                                case Double -> JavaConstant.forDouble(a.asDouble() - b.asDouble());
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            } else if (s2.isEmpty()) {
                                return s2;
                            }

                            // x - y is just x + -y so the calculations are similar
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;

                            boolean isNonNaN = true;
                            if (stamp1.canBeNaN() || stamp2.canBeNaN()) {
                                isNonNaN = false;
                            } else if ((stamp1.canBeNInf() && stamp2.canBeNInf()) || (stamp1.canBePInf() && stamp2.canBePInf())) {
                                isNonNaN = false;
                            }

                            double lowerBound;
                            double upperBound;
                            if (stamp1.getBits() == Float.SIZE) {
                                lowerBound = stamp1.lowerBound32() - stamp2.upperBound32();
                                upperBound = stamp1.upperBound32() - stamp2.lowerBound32();
                            } else {
                                lowerBound = stamp1.lowerBound() - stamp2.upperBound();
                                upperBound = stamp1.upperBound() - stamp2.lowerBound();
                            }

                            if ((stamp1.lowerBound() == Double.POSITIVE_INFINITY && stamp2.lowerBound() < Double.POSITIVE_INFINITY) ||
                                            (stamp1.upperBound() > Double.NEGATIVE_INFINITY && stamp2.upperBound() == Double.NEGATIVE_INFINITY)) {
                                lowerBound = Double.POSITIVE_INFINITY;
                            }
                            if ((stamp1.lowerBound() < Double.POSITIVE_INFINITY && stamp2.lowerBound() == Double.POSITIVE_INFINITY) ||
                                            (stamp1.upperBound() == Double.NEGATIVE_INFINITY && stamp2.upperBound() > Double.NEGATIVE_INFINITY)) {
                                upperBound = Double.NEGATIVE_INFINITY;
                            }
                            return new FloatStamp(stamp1.getBits(), lowerBound, upperBound, isNonNaN);
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return switch (n.getJavaKind()) {
                                case Float -> Float.compare(n.asFloat(), 0.0f) == 0;
                                case Double -> Double.compare(n.asDouble(), 0.0) == 0;
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(n.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Mul(false, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            return switch (a.getJavaKind()) {
                                case Float -> JavaConstant.forFloat(a.asFloat() * b.asFloat());
                                case Double -> JavaConstant.forDouble(a.asDouble() * b.asDouble());
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            } else if (s2.isEmpty()) {
                                return s2;
                            }

                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            if (stamp1.isNaN()) {
                                return stamp1;
                            } else if (stamp2.isNaN()) {
                                return stamp2;
                            }

                            boolean isNonNaN = true;
                            if (stamp1.canBeNaN() || stamp2.canBeNaN()) {
                                isNonNaN = false;
                            } else if (stamp1.contains(0) && stamp2.canBeInf()) {
                                isNonNaN = false;
                            } else if (stamp2.contains(0) && stamp1.canBeInf()) {
                                isNonNaN = false;
                            }

                            FloatStamp constant = null;
                            FloatStamp other = null;
                            // If one of the stamp is a constant (in the sense that lowerBound ==
                            // upperBound)
                            if (stamp1.lowerBound() == stamp1.upperBound()) {
                                constant = stamp1;
                                other = stamp2;
                            }
                            if (stamp2.lowerBound() == stamp2.upperBound()) {
                                // if both are constants, push 0 to the right
                                if (constant == null || constant.lowerBound() == 0) {
                                    constant = stamp2;
                                    other = stamp1;
                                }
                            }
                            if (constant != null) {
                                double constantVal = constant.lowerBound();
                                if (Double.isInfinite(constantVal)) {
                                    if (other.lowerBound() == 0 && other.upperBound() == 0) {
                                        return createNaN(constant.getBits());
                                    }

                                    double lowerBound;
                                    double upperBound;
                                    if (other.lowerBound() >= 0) {
                                        // A positive multiplying with an Inf results in the same
                                        // Inf
                                        lowerBound = constantVal;
                                        upperBound = constantVal;
                                    } else if (other.upperBound() <= 0) {
                                        // A negative multiplying with an Inf results in an Inf of
                                        // the opposite sign
                                        lowerBound = -constantVal;
                                        upperBound = -constantVal;
                                    } else {
                                        // The result can either be -Inf or +Inf but we cannot
                                        // represent it
                                        lowerBound = Double.NEGATIVE_INFINITY;
                                        upperBound = Double.POSITIVE_INFINITY;
                                    }
                                    return new FloatStamp(stamp1.getBits(), lowerBound, upperBound, isNonNaN);
                                } else if (constantVal == 0) {
                                    // other cannot be a constant Inf here
                                    return new FloatStamp(stamp1.getBits(), -0.0, 0, isNonNaN);
                                }
                            }

                            /*
                             * x * y is monotonic with respects to both x and y by the order
                             * imposed by Double.compare, which means that:
                             *
                             * @formatter:off
                             *
                             * For x1, x2, x3 such that Double.compare(x1, x2) <= 0 and Double.compare(x2, x3) <= 0, either:
                             *
                             * Double.compare(x1 * y, x2 * y) <= 0 and Double.compare(x2 * y, x3 * y) <= 0
                             * Double.compare(x1 * y, x2 * y) >= 0 and Double.compare(x2 * y, x3 * y) >= 0
                             *
                             * @formatter:on
                             *
                             * As a result, to find the bounds of the result, we only need to look
                             * at the bounds of the 4 values:
                             *
                             * @formatter:off
                             *
                             * s1.lowerBound() * s2.lowerBound()
                             * s1.lowerBound() * s2.upperBound()
                             * s1.upperBound() * s2.lowerBound()
                             * s1.upperBound() * s2.upperBound()
                             *
                             * @formatter:on
                             *
                             * Troubles may arise if one of those calculations results in NaN, in
                             * those cases we need to strip the NaN value and look at its neighbors.
                             * For example, if s1.lowerBound() * s2.upperBound() = NaN, we look at
                             * Math.nextUp(s1.lowerBound()) * s2.upperBound() and s1.lowerBound() *
                             * Math.nextDown(s2.upperBound()) instead.
                             *
                             * To do that, we need that Math.nextUp(s1.lowerBound()) and
                             * Math.nextDown(s2.upperBound()) are actually contained in s1 and s2,
                             * respectively. This means that s1 and s2 are both not constants (in
                             * the sense that s1.lowerBound() < s2.upperBound()). This is why we
                             * filter out the cases where s1 or s2 is a constant 0 or Inf
                             * beforehand.
                             */
                            double lowerBound = Double.POSITIVE_INFINITY;
                            double upperBound = Double.NEGATIVE_INFINITY;
                            // Iteratively look at the 4 boundary values and broaden the bounds of
                            // the resulting stamp
                            if (stamp1.lowerBound() == Double.NEGATIVE_INFINITY && stamp2.lowerBound() == 0) {
                                // nextUp(-Inf) * 0 = 0, -Inf * nextUp(0) = -Inf
                                lowerBound = Double.NEGATIVE_INFINITY;
                                upperBound = Math.max(upperBound, -stamp2.lowerBound());
                            } else if (stamp1.lowerBound() == 0 && stamp2.lowerBound() == Double.NEGATIVE_INFINITY) {
                                // nextUp(0) * -Inf = -Inf, 0 * nextUp(-Inf) = 0
                                lowerBound = Double.NEGATIVE_INFINITY;
                                upperBound = Math.max(upperBound, -stamp1.lowerBound());
                            } else {
                                double bound;
                                if (stamp1.getBits() == Float.SIZE) {
                                    bound = stamp1.lowerBound32() * stamp2.lowerBound32();
                                } else {
                                    bound = stamp1.lowerBound() * stamp2.lowerBound();
                                }
                                lowerBound = Math.min(lowerBound, bound);
                                upperBound = Math.max(upperBound, bound);
                            }

                            if (stamp1.upperBound() == Double.POSITIVE_INFINITY && stamp2.upperBound() == 0) {
                                // nextDown(+Inf) * 0 = 0, +Inf * nextDown(0) = -Inf
                                lowerBound = Double.NEGATIVE_INFINITY;
                                upperBound = Math.max(upperBound, stamp2.upperBound());
                            } else if (stamp1.upperBound() == 0 && stamp2.upperBound() == Double.POSITIVE_INFINITY) {
                                // nextDown(0) * +Inf = -Inf, 0 * nextDown(+Inf) = 0
                                lowerBound = Double.NEGATIVE_INFINITY;
                                upperBound = Math.max(upperBound, stamp1.upperBound());
                            } else {
                                double bound;
                                if (stamp1.getBits() == Float.SIZE) {
                                    bound = stamp1.upperBound32() * stamp2.upperBound32();
                                } else {
                                    bound = stamp1.upperBound() * stamp2.upperBound();
                                }
                                lowerBound = Math.min(lowerBound, bound);
                                upperBound = Math.max(upperBound, bound);
                            }

                            if (stamp1.lowerBound() == Double.NEGATIVE_INFINITY && stamp2.upperBound() == 0) {
                                // nextUp(-Inf) * 0 = 0, -Inf * nextDown(0) = +Inf
                                lowerBound = Math.min(lowerBound, -stamp2.upperBound());
                                upperBound = Double.POSITIVE_INFINITY;
                            } else if (stamp1.lowerBound() == 0 && stamp2.upperBound() == Double.POSITIVE_INFINITY) {
                                // nextUp(0) * +Inf = +Inf, 0 * nextDown(+Inf) = 0
                                lowerBound = Math.min(lowerBound, stamp1.lowerBound());
                                upperBound = Double.POSITIVE_INFINITY;
                            } else {
                                double bound;
                                if (stamp1.getBits() == Float.SIZE) {
                                    bound = stamp1.lowerBound32() * stamp2.upperBound32();
                                } else {
                                    bound = stamp1.lowerBound() * stamp2.upperBound();
                                }
                                lowerBound = Math.min(lowerBound, bound);
                                upperBound = Math.max(upperBound, bound);
                            }

                            if (stamp1.upperBound() == Double.POSITIVE_INFINITY && stamp2.lowerBound() == 0) {
                                // nextDown(+Inf) * 0 = 0, +Inf * nextUp(0) = +Inf
                                lowerBound = Math.min(lowerBound, stamp2.lowerBound());
                                upperBound = Double.POSITIVE_INFINITY;
                            } else if (stamp1.upperBound() == 0 && stamp2.lowerBound() == Double.NEGATIVE_INFINITY) {
                                // nextDown(0) * -Inf = +Inf, 0 * nextUp(-Inf) = 0
                                lowerBound = Math.min(lowerBound, -stamp1.upperBound());
                                upperBound = Double.POSITIVE_INFINITY;
                            } else {
                                double bound;
                                if (stamp1.getBits() == Float.SIZE) {
                                    bound = stamp1.upperBound32() * stamp2.lowerBound32();
                                } else {
                                    bound = stamp1.upperBound() * stamp2.lowerBound();
                                }
                                lowerBound = Math.min(lowerBound, bound);
                                upperBound = Math.max(upperBound, bound);
                            }

                            return new FloatStamp(stamp1.getBits(), lowerBound, upperBound, isNonNaN);
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return switch (n.getJavaKind()) {
                                case Float -> n.asFloat() == 1;
                                case Double -> n.asDouble() == 1;
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(n.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }
                    },

                    null,

                    null,

                    new ArithmeticOpTable.BinaryOp.Div(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            return switch (a.getJavaKind()) {
                                case Float -> JavaConstant.forFloat(a.asFloat() / b.asFloat());
                                case Double -> JavaConstant.forDouble(a.asDouble() / b.asDouble());
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            } else if (s2.isEmpty()) {
                                return s2;
                            }

                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            if (stamp1.isNaN()) {
                                return stamp1;
                            } else if (stamp2.isNaN()) {
                                return stamp2;
                            }

                            boolean isNonNaN = true;
                            if (stamp1.canBeNaN() || stamp2.canBeNaN()) {
                                isNonNaN = false;
                            } else if (stamp1.canBeInf() && stamp2.canBeInf()) {
                                isNonNaN = false;
                            } else if (stamp1.contains(0) && stamp2.contains(0)) {
                                isNonNaN = false;
                            }

                            // Filter out the infinity constants simplifies the calculation below
                            if (stamp2.lowerBound() == Double.POSITIVE_INFINITY || stamp2.upperBound() == Double.NEGATIVE_INFINITY) {
                                // If s1 and s2 are both constant Inf, then the result can only be
                                // NaN, else if s2 is a constant Inf then the result can only be 0
                                // or NaN
                                if (stamp1.lowerBound() == Double.POSITIVE_INFINITY || stamp1.upperBound() == Double.NEGATIVE_INFINITY) {
                                    return createNaN(stamp1.getBits());
                                } else {
                                    return new FloatStamp(stamp1.getBits(), -0.0, 0.0, isNonNaN);
                                }
                            }
                            if (stamp1.lowerBound() == Double.POSITIVE_INFINITY || stamp1.upperBound() == Double.NEGATIVE_INFINITY) {
                                // If s1 is Inf and s2 is not a constant Inf, then the result can
                                // only be Inf
                                if (stamp2.lowerBound() > 0) {
                                    // An Inf dividing by a positive number results in the same Inf
                                    double bound = stamp1.lowerBound() / stamp2.lowerBound();
                                    return new FloatStamp(stamp1.getBits(), bound, bound, isNonNaN);
                                } else if (stamp2.upperBound() < 0) {
                                    // An Inf dividing by a negative number results in an Inf of the
                                    // opposite sign
                                    double bound = stamp1.lowerBound() / stamp2.upperBound();
                                    return new FloatStamp(stamp1.getBits(), bound, bound, isNonNaN);
                                } else {
                                    // The result can be either +Inf or -Inf but we cannot represent
                                    // it
                                    return new FloatStamp(stamp1.getBits(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, isNonNaN);
                                }
                            }

                            if (stamp2.lowerBound() > 0) {
                                // If the divisor is positive, the result is increasing with respect
                                // to the dividend
                                double boundDivisor;
                                /*
                                 * If the dividend is positive, the result is decreasing with
                                 * respect to the divisor, else it is increasing. This means that:
                                 *
                                 * @formatter:off
                                 *
                                 * result.lowerBound = s1.lowerBound / s2.upperBound if s1.lowerBound >= 0
                                 * result.lowerBound = s1.lowerBound / s2.lowerBound otherwise
                                 *
                                 * @formatter:on
                                 */
                                if (stamp1.lowerBound() < 0) {
                                    boundDivisor = stamp2.lowerBound();
                                } else {
                                    boundDivisor = stamp2.upperBound();
                                }
                                double lowerBound;
                                if (stamp1.getBits() == Float.SIZE) {
                                    lowerBound = stamp1.lowerBound32() / (float) boundDivisor;
                                } else {
                                    lowerBound = stamp1.lowerBound() / boundDivisor;
                                }

                                // Similar to above, the dividend of the result upperBound is
                                // s1.upperBound here
                                if (stamp1.upperBound() < 0) {
                                    boundDivisor = stamp2.upperBound();
                                } else {
                                    boundDivisor = stamp2.lowerBound();
                                }
                                double upperBound;
                                if (stamp1.getBits() == Float.SIZE) {
                                    upperBound = stamp1.upperBound32() / (float) boundDivisor;
                                } else {
                                    upperBound = stamp1.upperBound() / boundDivisor;
                                }

                                return new FloatStamp(stamp1.getBits(), lowerBound, upperBound, isNonNaN);
                            }

                            if (stamp2.upperBound() < 0) {
                                // The divisor is negative, this case is similar to the case in
                                // which it is positive
                                double boundDivisor;
                                if (stamp1.upperBound() < 0) {
                                    boundDivisor = stamp2.lowerBound();
                                } else {
                                    boundDivisor = stamp2.upperBound();
                                }
                                double lowerBound;
                                if (stamp1.getBits() == Float.SIZE) {
                                    lowerBound = stamp1.upperBound32() / (float) boundDivisor;
                                } else {
                                    lowerBound = stamp1.upperBound() / boundDivisor;
                                }

                                if (stamp1.lowerBound() < 0) {
                                    boundDivisor = stamp2.upperBound();
                                } else {
                                    boundDivisor = stamp2.lowerBound();
                                }
                                double upperBound;
                                if (stamp1.getBits() == Float.SIZE) {
                                    upperBound = stamp1.lowerBound32() / (float) boundDivisor;
                                } else {
                                    upperBound = stamp1.lowerBound() / boundDivisor;
                                }

                                return new FloatStamp(stamp1.getBits(), lowerBound, upperBound, isNonNaN);
                            }

                            /*
                             * If y approaches 0, the quotient approaches infinity. This, combined
                             * with the fact that signed zero is not recorded strictly in
                             * FloatStamp, results in we not able to restrict the result stamp
                             */
                            return new FloatStamp(stamp1.getBits(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, isNonNaN);
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return switch (n.getJavaKind()) {
                                case Float -> n.asFloat() == 1;
                                case Double -> n.asDouble() == 1;
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(n.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Rem(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            switch (a.getJavaKind()) {
                                case Float:
                                    return JavaConstant.forFloat(a.asFloat() % b.asFloat());
                                case Double:
                                    return JavaConstant.forDouble(a.asDouble() % b.asDouble());
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }
                    },

                    new ArithmeticOpTable.UnaryOp.Not() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            switch (value.getJavaKind()) {
                                case Float:
                                    int f = Float.floatToRawIntBits(value.asFloat());
                                    return JavaConstant.forFloat(Float.intBitsToFloat(~f));
                                case Double:
                                    long d = Double.doubleToRawLongBits(value.asDouble());
                                    return JavaConstant.forDouble(Double.longBitsToDouble(~d));
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            FloatStamp stamp = (FloatStamp) s;
                            JavaConstant constant = stamp.asConstant();
                            if (constant != null) {
                                Constant folded = foldConstant(constant);
                                if (folded != null) {
                                    FloatStamp result = stampForConstant(folded);
                                    if (result != null && result.isConstant()) {
                                        return result;
                                    }
                                }
                            }
                            return s.unrestricted();
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.And(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            switch (a.getJavaKind()) {
                                case Float:
                                    int fa = Float.floatToRawIntBits(a.asFloat());
                                    int fb = Float.floatToRawIntBits(b.asFloat());
                                    return JavaConstant.forFloat(Float.intBitsToFloat(fa & fb));
                                case Double:
                                    long da = Double.doubleToRawLongBits(a.asDouble());
                                    long db = Double.doubleToRawLongBits(b.asDouble());
                                    return JavaConstant.forDouble(Double.longBitsToDouble(da & db));
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            PrimitiveConstant value = (PrimitiveConstant) n;
                            switch (value.getJavaKind()) {
                                case Float:
                                    return Float.floatToRawIntBits(value.asFloat()) == 0xFFFFFFFF;
                                case Double:
                                    return Double.doubleToRawLongBits(value.asDouble()) == 0xFFFFFFFFFFFFFFFFL;
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Or(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            switch (a.getJavaKind()) {
                                case Float:
                                    int fa = Float.floatToRawIntBits(a.asFloat());
                                    int fb = Float.floatToRawIntBits(b.asFloat());
                                    float floatOr = Float.intBitsToFloat(fa | fb);
                                    assert (fa | fb) == Float.floatToRawIntBits((floatOr)) : Assertions.errorMessageContext("a", a, "b", b);
                                    return JavaConstant.forFloat(floatOr);
                                case Double:
                                    long da = Double.doubleToRawLongBits(a.asDouble());
                                    long db = Double.doubleToRawLongBits(b.asDouble());
                                    return JavaConstant.forDouble(Double.longBitsToDouble(da | db));
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            PrimitiveConstant value = (PrimitiveConstant) n;
                            switch (value.getJavaKind()) {
                                case Float:
                                    return Float.floatToRawIntBits(value.asFloat()) == 0;
                                case Double:
                                    return Double.doubleToRawLongBits(value.asDouble()) == 0L;
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Xor(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            switch (a.getJavaKind()) {
                                case Float:
                                    int fa = Float.floatToRawIntBits(a.asFloat());
                                    int fb = Float.floatToRawIntBits(b.asFloat());
                                    return JavaConstant.forFloat(Float.intBitsToFloat(fa ^ fb));
                                case Double:
                                    long da = Double.doubleToRawLongBits(a.asDouble());
                                    long db = Double.doubleToRawLongBits(b.asDouble());
                                    return JavaConstant.forDouble(Double.longBitsToDouble(da ^ db));
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            PrimitiveConstant value = (PrimitiveConstant) n;
                            switch (value.getJavaKind()) {
                                case Float:
                                    return Float.floatToRawIntBits(value.asFloat()) == 0;
                                case Double:
                                    return Double.doubleToRawLongBits(value.asDouble()) == 0L;
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }
                    },

                    null, null, null,

                    new ArithmeticOpTable.UnaryOp.Abs() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return switch (value.getJavaKind()) {
                                case Float -> JavaConstant.forFloat(Math.abs(value.asFloat()));
                                case Double -> JavaConstant.forDouble(Math.abs(value.asDouble()));
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            FloatStamp stamp = (FloatStamp) s;
                            if (stamp.isNaN()) {
                                return s;
                            }
                            double lowerBound;
                            double upperBound;
                            if (stamp.lowerBound() > 0) {
                                lowerBound = stamp.lowerBound();
                                upperBound = stamp.upperBound();
                            } else if (stamp.upperBound() < 0) {
                                lowerBound = -stamp.upperBound();
                                upperBound = -stamp.lowerBound();
                            } else {
                                lowerBound = 0;
                                upperBound = Math.max(Math.abs(stamp.lowerBound()), Math.abs(stamp.upperBound()));
                            }
                            return new FloatStamp(stamp.getBits(), lowerBound, upperBound, stamp.isNonNaN());
                        }
                    },

                    new ArithmeticOpTable.UnaryOp.Sqrt() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return switch (value.getJavaKind()) {
                                case Float -> JavaConstant.forFloat((float) Math.sqrt(value.asFloat()));
                                case Double -> JavaConstant.forDouble(Math.sqrt(value.asDouble()));
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            FloatStamp stamp = (FloatStamp) s;
                            if (stamp.isNaN()) {
                                return s;
                            } else if (stamp.upperBound() < 0) {
                                return createNaN(stamp.getBits());
                            }
                            double lowerBound = Math.sqrt(Math.max(-0.0, stamp.lowerBound()));
                            double upperBound = Math.sqrt(stamp.upperBound());
                            if (stamp.getBits() == Float.SIZE) {
                                lowerBound = (float) lowerBound;
                                upperBound = (float) upperBound;
                            }
                            return new FloatStamp(stamp.getBits(), lowerBound, upperBound, stamp.isNonNaN() && stamp.lowerBound() >= 0);
                        }
                    },

                    null, null, null,

                    new ArithmeticOpTable.BinaryOp.Max(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            return switch (a.getJavaKind()) {
                                case Float -> JavaConstant.forFloat(Math.max(a.asFloat(), b.asFloat()));
                                case Double -> JavaConstant.forDouble(Math.max(a.asDouble(), b.asDouble()));
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            } else if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            return new FloatStamp(stamp1.getBits(), Math.max(stamp1.lowerBound(), stamp2.lowerBound()), Math.max(stamp1.upperBound(), stamp2.upperBound()),
                                            stamp1.isNonNaN() && stamp2.isNonNaN());
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            PrimitiveConstant value = (PrimitiveConstant) n;
                            return switch (value.getJavaKind()) {
                                case Float -> value.asFloat() == Float.NEGATIVE_INFINITY;
                                case Double -> value.asDouble() == Double.NEGATIVE_INFINITY;
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Min(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Must have same kind " + Assertions.errorMessageContext("a", a, "b", b);
                            return switch (a.getJavaKind()) {
                                case Float -> JavaConstant.forFloat(Math.min(a.asFloat(), b.asFloat()));
                                case Double -> JavaConstant.forDouble(Math.min(a.asDouble(), b.asDouble()));
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(a.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            } else if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            return new FloatStamp(stamp1.getBits(), Math.min(stamp1.lowerBound(), stamp2.lowerBound()), Math.min(stamp1.upperBound(), stamp2.upperBound()),
                                            stamp1.isNonNaN() && stamp2.isNonNaN());
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            PrimitiveConstant value = (PrimitiveConstant) n;
                            return switch (value.getJavaKind()) {
                                case Float -> value.asFloat() == Float.POSITIVE_INFINITY;
                                case Double -> value.asDouble() == Double.POSITIVE_INFINITY;
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(value.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            };
                        }
                    },

                    null, // UMax
                    null, // UMin

                    new ArithmeticOpTable.TernaryOp.FMA() {
                        @Override
                        public JavaConstant foldConstant(Constant a, Constant b, Constant c) {
                            PrimitiveConstant pa = (PrimitiveConstant) a;
                            PrimitiveConstant pb = (PrimitiveConstant) b;
                            PrimitiveConstant pc = (PrimitiveConstant) c;
                            GraalError.guarantee(pa.getJavaKind() == pb.getJavaKind() && pa.getJavaKind() == pc.getJavaKind(), "Must have same kind: a %s, b %s, c %s", pa, pb, pc);
                            return switch (pa.getJavaKind()) {
                                case Float -> JavaConstant.forFloat(GraalServices.fma(pa.asFloat(), pb.asFloat(), pc.asFloat()));
                                case Double -> JavaConstant.forDouble(GraalServices.fma(pa.asDouble(), pb.asDouble(), pc.asDouble()));
                                default -> throw GraalError.shouldNotReachHereUnexpectedValue(pa.getJavaKind());
                            };
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b, Stamp c) {
                            if (a.isEmpty()) {
                                return a;
                            }
                            if (b.isEmpty()) {
                                return b;
                            }
                            if (c.isEmpty()) {
                                return c;
                            }
                            JavaConstant constantA = ((FloatStamp) a).asConstant();
                            JavaConstant constantB = ((FloatStamp) b).asConstant();
                            JavaConstant constantC = ((FloatStamp) c).asConstant();
                            if (constantA != null && constantB != null && constantC != null) {
                                return StampFactory.forConstant(foldConstant(constantA, constantB, constantC));
                            }

                            return a.unrestricted();
                        }
                    },

                    new ArithmeticOpTable.ReinterpretOp() {

                        @Override
                        public Constant foldConstant(Stamp resultStamp, Constant constant) {
                            return ReinterpretUtils.foldConstant(resultStamp, constant);
                        }

                        @Override
                        public Stamp foldStamp(Stamp resultStamp, Stamp input) {
                            if (input.isEmpty()) {
                                return resultStamp.empty();
                            } else if (resultStamp instanceof IntegerStamp && input instanceof FloatStamp) {
                                return ReinterpretUtils.floatToInt((FloatStamp) input);
                            } else {
                                return resultStamp;
                            }
                        }
                    },

                    null, // Compress
                    null, // Expand

                    new ArithmeticOpTable.FloatConvertOp(F2I) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forInt((int) value.asFloat());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Int);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 32 : floatStamp;
                            boolean mustHaveZero = !floatStamp.isNonNaN();
                            int lowerBound = (int) floatStamp.lowerBound();
                            int upperBound = (int) floatStamp.upperBound();
                            if (mustHaveZero) {
                                if (lowerBound > 0) {
                                    lowerBound = 0;
                                } else if (upperBound < 0) {
                                    upperBound = 0;
                                }
                            }
                            return StampFactory.forInteger(JavaKind.Int, lowerBound, upperBound);
                        }

                        @Override
                        public boolean canOverflowInteger(Stamp inputStamp) {
                            return inputStamp instanceof FloatStamp floatStamp && floatingToIntegerCanOverflow(floatStamp, Integer.SIZE);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(F2L) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forLong((long) value.asFloat());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Long);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 32 : floatStamp;
                            boolean mustHaveZero = !floatStamp.isNonNaN();
                            long lowerBound = (long) floatStamp.lowerBound();
                            long upperBound = (long) floatStamp.upperBound();
                            if (mustHaveZero) {
                                if (lowerBound > 0) {
                                    lowerBound = 0;
                                } else if (upperBound < 0) {
                                    upperBound = 0;
                                }
                            }
                            return StampFactory.forInteger(JavaKind.Long, lowerBound, upperBound);
                        }

                        @Override
                        public boolean canOverflowInteger(Stamp inputStamp) {
                            return inputStamp instanceof FloatStamp floatStamp && floatingToIntegerCanOverflow(floatStamp, Long.SIZE);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(D2I) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forInt((int) value.asDouble());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Int);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 64 : floatStamp;
                            boolean mustHaveZero = !floatStamp.isNonNaN();
                            int lowerBound = (int) floatStamp.lowerBound();
                            int upperBound = (int) floatStamp.upperBound();
                            if (mustHaveZero) {
                                if (lowerBound > 0) {
                                    lowerBound = 0;
                                } else if (upperBound < 0) {
                                    upperBound = 0;
                                }
                            }
                            return StampFactory.forInteger(JavaKind.Int, lowerBound, upperBound);
                        }

                        @Override
                        public boolean canOverflowInteger(Stamp inputStamp) {
                            return inputStamp instanceof FloatStamp floatStamp && floatingToIntegerCanOverflow(floatStamp, Integer.SIZE);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(D2L) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forLong((long) value.asDouble());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Long);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 64 : floatStamp;
                            boolean mustHaveZero = !floatStamp.isNonNaN();
                            long lowerBound = (long) floatStamp.lowerBound();
                            long upperBound = (long) floatStamp.upperBound();
                            if (mustHaveZero) {
                                if (lowerBound > 0) {
                                    lowerBound = 0;
                                } else if (upperBound < 0) {
                                    upperBound = 0;
                                }
                            }
                            return StampFactory.forInteger(JavaKind.Long, lowerBound, upperBound);
                        }

                        @Override
                        public boolean canOverflowInteger(Stamp inputStamp) {
                            return inputStamp instanceof FloatStamp floatStamp && floatingToIntegerCanOverflow(floatStamp, Long.SIZE);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(F2D) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forDouble(value.asFloat());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Double);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 32 : floatStamp;
                            return StampFactory.forFloat(JavaKind.Double, floatStamp.lowerBound(), floatStamp.upperBound(), floatStamp.isNonNaN());
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(D2F) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forFloat((float) value.asDouble());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Float);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 64 : Assertions.errorMessage(floatStamp);
                            return StampFactory.forFloat(JavaKind.Float, (float) floatStamp.lowerBound(), (float) floatStamp.upperBound(), floatStamp.isNonNaN());
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(F2UI) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forInt((int) NumUtil.minUnsigned(NumUtil.toUnsignedLong(value.asFloat()), 0xffff_ffffL));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Int);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 32 : floatStamp;
                            long lowerBound = floatStamp.canBeNaN() ? 0
                                            : NumUtil.minUnsigned(NumUtil.toUnsignedLong(floatStamp.lowerBound()), 0xffff_ffffL);
                            long upperBound = NumUtil.minUnsigned(NumUtil.toUnsignedLong(floatStamp.upperBound()), 0xffff_ffffL);
                            return StampFactory.forUnsignedInteger(JavaKind.Int.getBitCount(), lowerBound, upperBound);
                        }

                        @Override
                        public boolean canOverflowInteger(Stamp inputStamp) {
                            return inputStamp instanceof FloatStamp floatStamp && floatingToIntegerCanOverflow(floatStamp, Integer.SIZE, true);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(F2UL) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forLong(NumUtil.toUnsignedLong(value.asFloat()));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Long);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 32 : floatStamp;
                            long lowerBound = floatStamp.canBeNaN() ? 0
                                            : NumUtil.toUnsignedLong(floatStamp.lowerBound());
                            long upperBound = NumUtil.toUnsignedLong(floatStamp.upperBound());
                            return StampFactory.forUnsignedInteger(JavaKind.Long.getBitCount(), lowerBound, upperBound);
                        }

                        @Override
                        public boolean canOverflowInteger(Stamp inputStamp) {
                            return inputStamp instanceof FloatStamp floatStamp && floatingToIntegerCanOverflow(floatStamp, Long.SIZE, true);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(D2UI) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forInt((int) NumUtil.minUnsigned(NumUtil.toUnsignedLong(value.asDouble()), 0xffff_ffffL));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Int);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 64 : floatStamp;
                            long lowerBound = floatStamp.canBeNaN() ? 0
                                            : NumUtil.minUnsigned(NumUtil.toUnsignedLong(floatStamp.lowerBound()), 0xffff_ffffL);
                            long upperBound = NumUtil.minUnsigned(NumUtil.toUnsignedLong(floatStamp.upperBound()), 0xffff_ffffL);
                            return StampFactory.forUnsignedInteger(JavaKind.Int.getBitCount(), lowerBound, upperBound);
                        }

                        @Override
                        public boolean canOverflowInteger(Stamp inputStamp) {
                            return inputStamp instanceof FloatStamp floatStamp && floatingToIntegerCanOverflow(floatStamp, Integer.SIZE, true);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(D2UL) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forLong(NumUtil.toUnsignedLong(value.asDouble()));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Long);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 64 : floatStamp;
                            long lowerBound = floatStamp.canBeNaN() ? 0
                                            : NumUtil.toUnsignedLong(floatStamp.lowerBound());
                            long upperBound = NumUtil.toUnsignedLong(floatStamp.upperBound());
                            return StampFactory.forUnsignedInteger(JavaKind.Long.getBitCount(), lowerBound, upperBound);
                        }

                        @Override
                        public boolean canOverflowInteger(Stamp inputStamp) {
                            return inputStamp instanceof FloatStamp floatStamp && floatingToIntegerCanOverflow(floatStamp, Long.SIZE, true);
                        }
                    });

    // Use a separate class to avoid initialization cycles
    private static final class ConstantCache {
        private static final FloatStamp FLOAT_BOTTOM = new FloatStamp(Float.SIZE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false);
        private static final FloatStamp DOUBLE_BOTTOM = new FloatStamp(Double.SIZE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false);
        private static final FloatStamp FLOAT_TOP = new FloatStamp(Float.SIZE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, true);
        private static final FloatStamp DOUBLE_TOP = new FloatStamp(Double.SIZE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, true);
        private static final FloatStamp FLOAT_NAN = new FloatStamp(Float.SIZE, Double.NaN, Double.NaN, false);
        private static final FloatStamp DOUBLE_NAN = new FloatStamp(Double.SIZE, Double.NaN, Double.NaN, false);
    }
}
