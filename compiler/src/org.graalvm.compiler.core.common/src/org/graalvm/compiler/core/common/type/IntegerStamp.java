/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.type;

import static org.graalvm.compiler.core.common.calc.FloatConvert.I2D;
import static org.graalvm.compiler.core.common.calc.FloatConvert.I2F;
import static org.graalvm.compiler.core.common.calc.FloatConvert.L2D;
import static org.graalvm.compiler.core.common.calc.FloatConvert.L2F;

import java.nio.ByteBuffer;
import java.util.Formatter;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.ReinterpretUtils;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.FloatConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.ReinterpretOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.ShiftOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SerializableConstant;

/**
 * Describes the possible values of a node that produces an int or long result.
 *
 * The description consists of (inclusive) lower and upper bounds and up (may be set) and down
 * (always set) bit-masks.
 */
public final class IntegerStamp extends PrimitiveStamp {

    private final long lowerBound;
    private final long upperBound;
    private final long downMask;
    private final long upMask;
    /**
     * Determines if this stamp can contain the value {@code 0}. If this is {@code true} the stamp
     * is a typical stamp without holes. If the stamp ranges over zero but {@code canBeZero==false}
     * it means the stamp contains a "hole", i.e., all values in the range except zero. We typically
     * cannot express holes in our Stamp system, thus we have the special logic for {@code 0}.
     */
    private final boolean canBeZero;

    private IntegerStamp(int bits, long lowerBound, long upperBound, long downMask, long upMask) {
        this(bits, lowerBound, upperBound, downMask, upMask, true);
    }

    private IntegerStamp(int bits, long lowerBound, long upperBound, long downMask, long upMask, boolean canBeZero) {
        super(bits, OPS);

        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.downMask = downMask;
        this.upMask = upMask;

        assert lowerBound >= CodeUtil.minValue(bits) : this;
        assert upperBound <= CodeUtil.maxValue(bits) : this;
        assert (downMask & CodeUtil.mask(bits)) == downMask : this;
        assert (upMask & CodeUtil.mask(bits)) == upMask : this;
        // Check for valid masks or the empty encoding
        assert (downMask & ~upMask) == 0 || (upMask == 0 && downMask == CodeUtil.mask(bits)) : String.format("\u21ca: %016x \u21c8: %016x", downMask, upMask);
        // use ctor param because canBeZero is not set yet
        this.canBeZero = contains(0, canBeZero);
        assert !this.canBeZero || contains(0) : " Stamp " + this + " either has canBeZero set to false or needs to contain 0";
    }

    public static IntegerStamp create(int bits, long lowerBoundInput, long upperBoundInput) {
        return create(bits, lowerBoundInput, upperBoundInput, 0, CodeUtil.mask(bits));
    }

    public static IntegerStamp create(int bits, long lowerBoundInput, long upperBoundInput, long downMask, long upMask) {
        return create(bits, lowerBoundInput, upperBoundInput, downMask, upMask, true);
    }

    public static IntegerStamp create(int bits, long lowerBoundInput, long upperBoundInput, long downMask, long upMask, boolean canBeZero) {
        assert (downMask & ~upMask) == 0 : String.format("\u21ca: %016x \u21c8: %016x", downMask, upMask);

        // Set lower bound, use masks to make it more precise
        long minValue = minValueForMasks(bits, downMask, upMask);
        long lowerBoundTmp = Math.max(lowerBoundInput, minValue);

        // Set upper bound, use masks to make it more precise
        long maxValue = maxValueForMasks(bits, downMask, upMask);
        long upperBoundTmp = Math.min(upperBoundInput, maxValue);

        // Assign masks now with the bounds in mind.
        final long boundedDownMask;
        final long boundedUpMask;
        long defaultMask = CodeUtil.mask(bits);
        if (lowerBoundTmp == upperBoundTmp) {
            boundedDownMask = lowerBoundTmp;
            boundedUpMask = lowerBoundTmp;
        } else if (lowerBoundTmp >= 0) {
            int upperBoundLeadingZeros = Long.numberOfLeadingZeros(upperBoundTmp);
            long differentBits = lowerBoundTmp ^ upperBoundTmp;
            int sameBitCount = Long.numberOfLeadingZeros(differentBits << upperBoundLeadingZeros);

            boundedUpMask = upperBoundTmp | -1L >>> (upperBoundLeadingZeros + sameBitCount);
            boundedDownMask = upperBoundTmp & ~(-1L >>> (upperBoundLeadingZeros + sameBitCount));
        } else {
            if (upperBoundTmp >= 0) {
                boundedUpMask = defaultMask;
                boundedDownMask = 0;
            } else {
                int lowerBoundLeadingOnes = Long.numberOfLeadingZeros(~lowerBoundTmp);
                long differentBits = lowerBoundTmp ^ upperBoundTmp;
                int sameBitCount = Long.numberOfLeadingZeros(differentBits << lowerBoundLeadingOnes);

                boundedUpMask = lowerBoundTmp | -1L >>> (lowerBoundLeadingOnes + sameBitCount) | ~(-1L >>> lowerBoundLeadingOnes);
                boundedDownMask = lowerBoundTmp & ~(-1L >>> (lowerBoundLeadingOnes + sameBitCount)) | ~(-1L >>> lowerBoundLeadingOnes);
            }
        }

        return new IntegerStamp(bits, lowerBoundTmp, upperBoundTmp, defaultMask & (downMask | boundedDownMask), defaultMask & upMask & boundedUpMask, canBeZero);
    }

    /**
     * A stamp is empty if the lower bound is greater than the upper bound, the downMask contains
     * bits which are not part of the upMask, or there are no bits set and the bound doesn't contain
     * 0.
     */
    private static boolean isEmpty(long lowerBound, long upperBound, long downMask, long upMask) {
        return lowerBound > upperBound || (downMask & (~upMask)) != 0 || (upMask == 0 && (lowerBound > 0 || upperBound < 0));
    }

    private static long significantBit(long bits, long value) {
        return (value >>> (bits - 1)) & 1;
    }

    private static long minValueForMasks(int bits, long downMask, long upMask) {
        if (significantBit(bits, upMask) == 0) {
            // Value is always positive. Minimum value always positive.
            assert significantBit(bits, downMask) == 0 : String.format("\u21ca: %016x \u21c8: %016x", downMask, upMask);
            return downMask;
        } else {
            // Value can be positive or negative. Minimum value always negative.
            return downMask | (-1L << (bits - 1));
        }
    }

    private static long maxValueForMasks(int bits, long downMask, long upMask) {
        if (significantBit(bits, downMask) == 1) {
            // Value is always negative. Maximum value always negative.
            assert significantBit(bits, upMask) == 1;
            return CodeUtil.signExtend(upMask, bits);
        } else {
            // Value can be positive or negative. Maximum value always positive.
            return upMask & (CodeUtil.mask(bits) >>> 1);
        }
    }

    public static IntegerStamp stampForMask(int bits, long downMask, long upMask) {
        /*
         * Determine if the new stamp created by down & upMask would be contradicting, i.e., empty
         * by definition. This can happen for example if binary logic operations are evaluated
         * repetitively on different branches creating values that are infeasible by definition
         * (logic nodes on phi nodes of false evaluated predecessors).
         */
        if ((downMask & ~upMask) != 0L) {
            return createEmptyStamp(bits);
        }
        return new IntegerStamp(bits, minValueForMasks(bits, downMask, upMask), maxValueForMasks(bits, downMask, upMask), downMask, upMask);
    }

    @Override
    public IntegerStamp unrestricted() {
        return new IntegerStamp(getBits(), CodeUtil.minValue(getBits()), CodeUtil.maxValue(getBits()), 0, CodeUtil.mask(getBits()));
    }

    @Override
    public IntegerStamp empty() {
        return createEmptyStamp(getBits());
    }

    static IntegerStamp createEmptyStamp(int bits) {
        return new IntegerStamp(bits, CodeUtil.maxValue(bits), CodeUtil.minValue(bits), CodeUtil.mask(bits), 0);
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        if (c instanceof PrimitiveConstant) {
            PrimitiveConstant primitiveConstant = (PrimitiveConstant) c;
            long value = primitiveConstant.asLong();
            if (primitiveConstant.getJavaKind() == JavaKind.Boolean && value == 1) {
                // Need to special case booleans as integer stamps are always signed values.
                value = -1;
            }
            Stamp returnedStamp = StampFactory.forInteger(getBits(), value, value);
            assert returnedStamp.hasValues();
            return returnedStamp;
        }
        return this;
    }

    @Override
    public SerializableConstant deserialize(ByteBuffer buffer) {
        switch (getBits()) {
            case 1:
                return JavaConstant.forBoolean(buffer.get() != 0);
            case 8:
                return JavaConstant.forByte(buffer.get());
            case 16:
                return JavaConstant.forShort(buffer.getShort());
            case 32:
                return JavaConstant.forInt(buffer.getInt());
            case 64:
                return JavaConstant.forLong(buffer.getLong());
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public boolean hasValues() {
        return lowerBound <= upperBound;
    }

    @Override
    public JavaKind getStackKind() {
        if (getBits() > 32) {
            return JavaKind.Long;
        } else {
            return JavaKind.Int;
        }
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getIntegerKind(getBits());
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        switch (getBits()) {
            case 1:
                return metaAccess.lookupJavaType(Boolean.TYPE);
            case 8:
                return metaAccess.lookupJavaType(Byte.TYPE);
            case 16:
                return metaAccess.lookupJavaType(Short.TYPE);
            case 32:
                return metaAccess.lookupJavaType(Integer.TYPE);
            case 64:
                return metaAccess.lookupJavaType(Long.TYPE);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement, Stamp accessStamp) {
        PrimitiveStamp primitiveAccess = (PrimitiveStamp) accessStamp;
        int accessBits = primitiveAccess.getBits();
        GraalError.guarantee(getBits() >= ((PrimitiveStamp) accessStamp).getBits(), "access size should be less than or equal the result");

        JavaConstant constant = super.readJavaConstant(provider, base, displacement, accessBits);
        if (constant == null) {
            return null;
        }
        if (constant.getJavaKind().getBitCount() != accessBits) {
            if (canBeNegative()) {
                constant = JavaConstant.forPrimitiveInt(getBits(), CodeUtil.signExtend(constant.asLong(), accessBits));
            } else {
                constant = JavaConstant.forPrimitiveInt(getBits(), CodeUtil.zeroExtend(constant.asLong(), accessBits));
            }
        }
        return constant;
    }

    /**
     * The signed inclusive lower bound on the value described by this stamp.
     */
    public long lowerBound() {
        return lowerBound;
    }

    /**
     * The signed inclusive upper bound on the value described by this stamp.
     */
    public long upperBound() {
        return upperBound;
    }

    /**
     * This bit-mask describes the bits that are always set in the value described by this stamp.
     */
    public long downMask() {
        return downMask;
    }

    /**
     * This bit-mask describes the bits that can be set in the value described by this stamp.
     */
    public long upMask() {
        return upMask;
    }

    @Override
    public boolean isUnrestricted() {
        return lowerBound == CodeUtil.minValue(getBits()) && upperBound == CodeUtil.maxValue(getBits()) && downMask == 0 && upMask == CodeUtil.mask(getBits()) && canBeZero;
    }

    public boolean contains(long value) {
        return contains(value, canBeZero);
    }

    private boolean contains(long value, boolean isCanBeZero) {
        if (value == 0 && !isCanBeZero) {
            /*
             * Special case partially canonicalized graphs and constants: If a guarded pi was
             * created with canBeZero=false but we feed in a constant 0
             */
            if (lowerBound == upperBound && lowerBound == 0) {
                return true;
            }
            return false;
        }
        return value >= lowerBound && value <= upperBound && (value & downMask) == downMask && (value & upMask) == (value & CodeUtil.mask(getBits()));
    }

    public boolean isPositive() {
        return lowerBound() >= 0;
    }

    public boolean isNegative() {
        return upperBound() <= 0;
    }

    public boolean isStrictlyPositive() {
        return lowerBound() > 0;
    }

    public boolean isStrictlyNegative() {
        return upperBound() < 0;
    }

    public boolean canBePositive() {
        return upperBound() > 0;
    }

    public boolean canBeNegative() {
        return lowerBound() < 0;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append('i');
        str.append(getBits());
        if (hasValues()) {
            if (lowerBound == upperBound) {
                str.append(" [").append(lowerBound).append(']');
            } else if (lowerBound != CodeUtil.minValue(getBits()) || upperBound != CodeUtil.maxValue(getBits())) {
                str.append(" [").append(lowerBound);
                str.append(" - ");
                str.append(upperBound).append(']');
            }
            if (downMask != 0) {
                str.append(" \u21ca");
                new Formatter(str).format("%016x", downMask);
            }
            if (upMask != CodeUtil.mask(getBits())) {
                str.append(" \u21c8");
                new Formatter(str).format("%016x", upMask);
            }
        } else {
            str.append("<empty>");
        }
        if (!canBeZero) {
            str.append(" {!=0}");
        }
        return str.toString();
    }

    private IntegerStamp createStamp(IntegerStamp other, long newUpperBound, long newLowerBound, long newDownMask, long newUpMask, boolean newCanBeZero) {
        assert getBits() == other.getBits();
        if (newLowerBound > newUpperBound || (newDownMask & (~newUpMask)) != 0 || (newUpMask == 0 && (newLowerBound > 0 || newUpperBound < 0))) {
            return empty();
        } else if (newLowerBound == lowerBound && newUpperBound == upperBound && newDownMask == downMask && newUpMask == upMask && canBeZero == newCanBeZero) {
            return this;
        } else if (newLowerBound == other.lowerBound && newUpperBound == other.upperBound && newDownMask == other.downMask && newUpMask == other.upMask && newCanBeZero == other.canBeZero) {
            return other;
        } else {
            return IntegerStamp.create(getBits(), newLowerBound, newUpperBound, newDownMask, newUpMask, newCanBeZero);
        }
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        if (isEmpty()) {
            return otherStamp;
        }
        if (otherStamp.isEmpty()) {
            return this;
        }
        IntegerStamp other = (IntegerStamp) otherStamp;
        return createStamp(other, Math.max(upperBound, other.upperBound), Math.min(lowerBound, other.lowerBound), downMask & other.downMask, upMask | other.upMask, canBeZero || other.canBeZero);
    }

    @Override
    public IntegerStamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        IntegerStamp other = (IntegerStamp) otherStamp;
        long newDownMask = downMask | other.downMask;
        long newLowerBound = Math.max(lowerBound, other.lowerBound);
        long newUpperBound = Math.min(upperBound, other.upperBound);
        long newUpMask = upMask & other.upMask;
        boolean newCanBeZero = canBeZero && other.canBeZero;
        return createStamp(other, newUpperBound, newLowerBound, newDownMask, newUpMask, newCanBeZero);
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        if (this == stamp) {
            return true;
        }
        if (stamp instanceof IntegerStamp) {
            IntegerStamp other = (IntegerStamp) stamp;
            return getBits() == other.getBits();
        }
        return false;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        if (constant instanceof PrimitiveConstant) {
            PrimitiveConstant prim = (PrimitiveConstant) constant;
            JavaKind kind = prim.getJavaKind();
            return kind.isNumericInteger() && kind.getBitCount() == getBits();
        }
        return false;
    }

    public long unsignedUpperBound() {
        if (sameSignBounds()) {
            return CodeUtil.zeroExtend(upperBound(), getBits());
        }
        return NumUtil.maxValueUnsigned(getBits());
    }

    public long unsignedLowerBound() {
        if (sameSignBounds()) {
            return CodeUtil.zeroExtend(lowerBound(), getBits());
        }
        return 0;
    }

    private boolean sameSignBounds() {
        return NumUtil.sameSign(lowerBound, upperBound);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + super.hashCode();
        result = prime * result + (int) (lowerBound ^ (lowerBound >>> 32));
        result = prime * result + (int) (upperBound ^ (upperBound >>> 32));
        result = prime * result + (int) (downMask ^ (downMask >>> 32));
        result = prime * result + (int) (upMask ^ (upMask >>> 32));
        result = prime * result + Boolean.hashCode(canBeZero);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass() || !super.equals(obj)) {
            return false;
        }
        IntegerStamp other = (IntegerStamp) obj;
        if (lowerBound != other.lowerBound || upperBound != other.upperBound || downMask != other.downMask || upMask != other.upMask || canBeZero != other.canBeZero) {
            return false;
        }
        return super.equals(other);
    }

    private static long upMaskFor(int bits, long lowerBound, long upperBound) {
        long mask = lowerBound | upperBound;
        if (mask == 0) {
            return 0;
        } else {
            return ((-1L) >>> Long.numberOfLeadingZeros(mask)) & CodeUtil.mask(bits);
        }
    }

    /**
     * Checks if the 2 stamps represent values of the same sign. Returns true if the two stamps are
     * both positive of null or if they are both strictly negative
     *
     * @return true if the two stamps are both positive of null or if they are both strictly
     *         negative
     */
    public static boolean sameSign(IntegerStamp s1, IntegerStamp s2) {
        return s1.isPositive() && s2.isPositive() || s1.isStrictlyNegative() && s2.isStrictlyNegative();
    }

    @Override
    public JavaConstant asConstant() {
        if (lowerBound == upperBound) {
            switch (getBits()) {
                case 1:
                    return JavaConstant.forBoolean(lowerBound != 0);
                case 8:
                    return JavaConstant.forByte((byte) lowerBound);
                case 16:
                    return JavaConstant.forShort((short) lowerBound);
                case 32:
                    return JavaConstant.forInt((int) lowerBound);
                case 64:
                    return JavaConstant.forLong(lowerBound);
            }
        }
        return null;
    }

    public static boolean addCanOverflow(IntegerStamp a, IntegerStamp b) {
        assert a.getBits() == b.getBits();
        return addOverflowsPositively(a.upperBound(), b.upperBound(), a.getBits()) ||
                        addOverflowsNegatively(a.lowerBound(), b.lowerBound(), a.getBits());

    }

    public static boolean addOverflowsPositively(long x, long y, int bits) {
        long result = x + y;
        if (bits == 64) {
            return (~x & ~y & result) < 0;
        } else {
            return result > CodeUtil.maxValue(bits);
        }
    }

    public static boolean addOverflowsNegatively(long x, long y, int bits) {
        long result = x + y;
        if (bits == 64) {
            return (x & y & ~result) < 0;
        } else {
            return result < CodeUtil.minValue(bits);
        }
    }

    public static long carryBits(long x, long y) {
        return (x + y) ^ x ^ y;
    }

    private static long saturate(long v, int bits) {
        if (bits < 64) {
            long max = CodeUtil.maxValue(bits);
            if (v > max) {
                return max;
            }
            long min = CodeUtil.minValue(bits);
            if (v < min) {
                return min;
            }
        }
        return v;
    }

    public static boolean multiplicationOverflows(long a, long b, int bits) {
        assert bits <= 64 && bits >= 0;
        long result = a * b;
        // result is positive if the sign is the same
        boolean positive = (a >= 0 && b >= 0) || (a < 0 && b < 0);
        if (bits == 64) {
            if (a > 0 && b > 0) {
                return a > 0x7FFFFFFF_FFFFFFFFL / b;
            } else if (a > 0 && b <= 0) {
                return b < 0x80000000_00000000L / a;
            } else if (a <= 0 && b > 0) {
                return a < 0x80000000_00000000L / b;
            } else {
                // a<=0 && b <=0
                return a != 0 && b < 0x7FFFFFFF_FFFFFFFFL / a;
            }
        } else {
            if (positive) {
                return result > CodeUtil.maxValue(bits);
            } else {
                return result < CodeUtil.minValue(bits);
            }
        }
    }

    public static boolean multiplicationCanOverflow(IntegerStamp a, IntegerStamp b) {
        // see IntegerStamp#foldStamp for details
        assert a.getBits() == b.getBits();
        if (a.upMask() == 0) {
            return false;
        } else if (b.upMask() == 0) {
            return false;
        }
        if (a.isUnrestricted()) {
            return true;
        }
        if (b.isUnrestricted()) {
            return true;
        }
        int bits = a.getBits();
        long minNegA = a.lowerBound();
        long maxNegA = Math.min(0, a.upperBound());
        long minPosA = Math.max(0, a.lowerBound());
        long maxPosA = a.upperBound();

        long minNegB = b.lowerBound();
        long maxNegB = Math.min(0, b.upperBound());
        long minPosB = Math.max(0, b.lowerBound());
        long maxPosB = b.upperBound();

        boolean mayOverflow = false;
        if (a.canBePositive()) {
            if (b.canBePositive()) {
                mayOverflow |= IntegerStamp.multiplicationOverflows(maxPosA, maxPosB, bits);
                mayOverflow |= IntegerStamp.multiplicationOverflows(minPosA, minPosB, bits);
            }
            if (b.canBeNegative()) {
                mayOverflow |= IntegerStamp.multiplicationOverflows(minPosA, maxNegB, bits);
                mayOverflow |= IntegerStamp.multiplicationOverflows(maxPosA, minNegB, bits);

            }
        }
        if (a.canBeNegative()) {
            if (b.canBePositive()) {
                mayOverflow |= IntegerStamp.multiplicationOverflows(maxNegA, minPosB, bits);
                mayOverflow |= IntegerStamp.multiplicationOverflows(minNegA, maxPosB, bits);
            }
            if (b.canBeNegative()) {
                mayOverflow |= IntegerStamp.multiplicationOverflows(minNegA, minNegB, bits);
                mayOverflow |= IntegerStamp.multiplicationOverflows(maxNegA, maxNegB, bits);
            }
        }
        return mayOverflow;
    }

    public static boolean subtractionCanOverflow(IntegerStamp x, IntegerStamp y) {
        assert x.getBits() == y.getBits();
        return subtractionOverflows(x.lowerBound(), y.upperBound(), x.getBits()) || subtractionOverflows(x.upperBound(), y.lowerBound(), x.getBits());
    }

    public static boolean subtractionOverflows(long x, long y, int bits) {
        long result = x - y;
        if (bits == 64) {
            return (((x ^ y) & (x ^ result)) < 0);
        }
        return result < CodeUtil.minValue(bits) || result > CodeUtil.maxValue(bits);
    }

    /**
     * Returns if {@code stamp} can overflow when applying negation. It effectively tests if
     * {@code stamp}'s value range contains the minimal value of an N-bits integer, where N is the
     * width in bits of the values described by {@code stamp}.
     *
     * @see Math#negateExact
     */
    public static boolean negateCanOverflow(IntegerStamp stamp) {
        return stamp.lowerBound() == CodeUtil.minValue(stamp.getBits());
    }

    public static final ArithmeticOpTable OPS = new ArithmeticOpTable(

                    new UnaryOp.Neg() {

                        @Override
                        public Constant foldConstant(Constant value) {
                            PrimitiveConstant c = (PrimitiveConstant) value;
                            return JavaConstant.forIntegerKind(c.getJavaKind(), -c.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            IntegerStamp stamp = (IntegerStamp) s;
                            int bits = stamp.getBits();
                            if (stamp.lowerBound == stamp.upperBound) {
                                long value = CodeUtil.convert(-stamp.lowerBound(), stamp.getBits(), false);
                                return StampFactory.forInteger(stamp.getBits(), value, value);
                            }
                            if (stamp.lowerBound() != CodeUtil.minValue(bits)) {
                                // TODO(ls) check if the mask calculation is correct...
                                return StampFactory.forInteger(bits, -stamp.upperBound(), -stamp.lowerBound());
                            } else {
                                return stamp.unrestricted();
                            }
                        }
                    },

                    new BinaryOp.Add(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() + b.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;

                            int bits = a.getBits();
                            assert bits == b.getBits() : String.format("stamp1.bits=%d, stamp2.bits=%d", bits, b.getBits());

                            if (a.lowerBound == a.upperBound && b.lowerBound == b.upperBound) {
                                long value = CodeUtil.convert(a.lowerBound() + b.lowerBound(), a.getBits(), false);
                                return StampFactory.forInteger(a.getBits(), value, value);
                            }

                            if (a.isUnrestricted()) {
                                return a;
                            } else if (b.isUnrestricted()) {
                                return b;
                            }
                            long defaultMask = CodeUtil.mask(bits);
                            long variableBits = (a.downMask() ^ a.upMask()) | (b.downMask() ^ b.upMask());
                            long variableBitsWithCarry = variableBits | (carryBits(a.downMask(), b.downMask()) ^ carryBits(a.upMask(), b.upMask()));
                            long newDownMask = (a.downMask() + b.downMask()) & ~variableBitsWithCarry;
                            long newUpMask = (a.downMask() + b.downMask()) | variableBitsWithCarry;

                            newDownMask &= defaultMask;
                            newUpMask &= defaultMask;

                            long newLowerBound;
                            long newUpperBound;
                            boolean lowerOverflowsPositively = addOverflowsPositively(a.lowerBound(), b.lowerBound(), bits);
                            boolean upperOverflowsPositively = addOverflowsPositively(a.upperBound(), b.upperBound(), bits);
                            boolean lowerOverflowsNegatively = addOverflowsNegatively(a.lowerBound(), b.lowerBound(), bits);
                            boolean upperOverflowsNegatively = addOverflowsNegatively(a.upperBound(), b.upperBound(), bits);
                            if ((lowerOverflowsNegatively && !upperOverflowsNegatively) || (!lowerOverflowsPositively && upperOverflowsPositively)) {
                                newLowerBound = CodeUtil.minValue(bits);
                                newUpperBound = CodeUtil.maxValue(bits);
                            } else {
                                newLowerBound = CodeUtil.signExtend((a.lowerBound() + b.lowerBound()) & defaultMask, bits);
                                newUpperBound = CodeUtil.signExtend((a.upperBound() + b.upperBound()) & defaultMask, bits);
                            }
                            IntegerStamp limit = StampFactory.forInteger(bits, newLowerBound, newUpperBound);
                            newUpMask &= limit.upMask();
                            newUpperBound = CodeUtil.signExtend(newUpperBound & newUpMask, bits);
                            newDownMask |= limit.downMask();
                            newLowerBound |= newDownMask;
                            if (isEmpty(newLowerBound, newUpperBound, newDownMask, newUpMask)) {
                                // It's possible the masks are inconsistent with the bounds so
                                // return some less precise but non-empty stamp in that case
                                if (newLowerBound < newUpperBound) {
                                    return StampFactory.forInteger(bits, newLowerBound, newUpperBound);
                                } else {
                                    return limit.unrestricted();
                                }
                            }
                            IntegerStamp result = new IntegerStamp(bits, newLowerBound, newUpperBound, newDownMask, newUpMask);
                            assert !result.isEmpty() : result;
                            return result;
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 0;
                        }
                    },

                    new BinaryOp.Sub(true, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() - b.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            return OPS.getAdd().foldStamp(a, OPS.getNeg().foldStamp(b));
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 0;
                        }

                        @Override
                        public Constant getZero(Stamp s) {
                            IntegerStamp stamp = (IntegerStamp) s;
                            return JavaConstant.forPrimitiveInt(stamp.getBits(), 0);
                        }
                    },

                    new BinaryOp.Mul(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() * b.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;

                            int bits = a.getBits();
                            assert bits == b.getBits();

                            if (a.lowerBound == a.upperBound && b.lowerBound == b.upperBound) {
                                long value = CodeUtil.convert(a.lowerBound() * b.lowerBound(), a.getBits(), false);
                                return StampFactory.forInteger(a.getBits(), value, value);
                            }

                            // if a==0 or b==0 result of a*b is always 0
                            if (a.upMask() == 0) {
                                return a;
                            } else if (b.upMask() == 0) {
                                return b;
                            } else {
                                // if a has the full range or b, the result will also have it
                                if (a.isUnrestricted()) {
                                    return a;
                                } else if (b.isUnrestricted()) {
                                    return b;
                                }
                                // a!=0 && b !=0 holds
                                long newLowerBound = Long.MAX_VALUE;
                                long newUpperBound = Long.MIN_VALUE;
                                /*
                                 * Based on the signs of the incoming stamps lower and upper bound
                                 * of the result of the multiplication may be swapped. LowerBound
                                 * can become upper bound if both signs are negative, and so on. To
                                 * determine the new values for lower and upper bound we need to
                                 * look at the max and min of the cases blow:
                                 *
                                 * @formatter:off
                                 *
                                 * a.lowerBound * b.lowerBound
                                 * a.lowerBound * b.upperBound
                                 * a.upperBound * b.lowerBound
                                 * a.upperBound * b.upperBound
                                 *
                                 * @formatter:on
                                 *
                                 * We are only interested in those cases that are relevant due to
                                 * the sign of the involved stamps (whether a stamp includes
                                 * negative and / or positive values). Based on the signs, the maximum
                                 * or minimum of the above multiplications form the new lower and
                                 * upper bounds.
                                 *
                                 * The table below contains the interesting candidates for lower and
                                 * upper bound after multiplication.
                                 *
                                 * For example if we consider two stamps a & b that both contain
                                 * negative and positive values, the product of minNegA * minNegB
                                 * (both the smallest negative value for each stamp) can only be the
                                 * highest positive number. The other candidates can be computed in
                                 * a similar fashion. Some of them can never be a new minimum or
                                 * maximum and are therefore excluded.
                                 *
                                 *
                                 * @formatter:off
                                 *
                                 *          [x................0................y]
                                 *          -------------------------------------
                                 *          [minNeg     maxNeg minPos     maxPos]
                                 *
                                 *          where maxNeg = min(0,y) && minPos = max(0,x)
                                 *
                                 *
                                 *                 |minNegA  maxNegA    minPosA  maxPosA
                                 *         _______ |____________________________________
                                 *         minNegB | MAX        /     :     /      MIN
                                 *         maxNegB |  /        MIN    :    MAX      /
                                 *                 |------------------+-----------------
                                 *         minPosB |  /        MAX    :    MIN      /
                                 *         maxPosB | MIN        /     :     /      MAX
                                 *
                                 * @formatter:on
                                 */
                                // We materialize all factors here. If they are needed, the signs of
                                // the stamp will ensure the correct value is used.
                                long minNegA = a.lowerBound();
                                long maxNegA = Math.min(0, a.upperBound());
                                long minPosA = Math.max(0, a.lowerBound());
                                long maxPosA = a.upperBound();

                                long minNegB = b.lowerBound();
                                long maxNegB = Math.min(0, b.upperBound());
                                long minPosB = Math.max(0, b.lowerBound());
                                long maxPosB = b.upperBound();

                                // multiplication has shift semantics
                                long newUpMask = ~CodeUtil.mask(Math.min(64, Long.numberOfTrailingZeros(a.upMask) + Long.numberOfTrailingZeros(b.upMask))) & CodeUtil.mask(bits);

                                if (a.canBePositive()) {
                                    if (b.canBePositive()) {
                                        if (multiplicationOverflows(maxPosA, maxPosB, bits)) {
                                            return a.unrestricted();
                                        }
                                        long maxCandidate = maxPosA * maxPosB;
                                        if (multiplicationOverflows(minPosA, minPosB, bits)) {
                                            return a.unrestricted();
                                        }
                                        long minCandidate = minPosA * minPosB;
                                        newLowerBound = Math.min(newLowerBound, minCandidate);
                                        newUpperBound = Math.max(newUpperBound, maxCandidate);
                                    }
                                    if (b.canBeNegative()) {
                                        if (multiplicationOverflows(minPosA, maxNegB, bits)) {
                                            return a.unrestricted();
                                        }
                                        long maxCandidate = minPosA * maxNegB;
                                        if (multiplicationOverflows(maxPosA, minNegB, bits)) {
                                            return a.unrestricted();
                                        }
                                        long minCandidate = maxPosA * minNegB;
                                        newLowerBound = Math.min(newLowerBound, minCandidate);
                                        newUpperBound = Math.max(newUpperBound, maxCandidate);
                                    }
                                }
                                if (a.canBeNegative()) {
                                    if (b.canBePositive()) {
                                        if (multiplicationOverflows(maxNegA, minPosB, bits)) {
                                            return a.unrestricted();
                                        }
                                        long maxCandidate = maxNegA * minPosB;
                                        if (multiplicationOverflows(minNegA, maxPosB, bits)) {
                                            return a.unrestricted();
                                        }
                                        long minCandidate = minNegA * maxPosB;
                                        newLowerBound = Math.min(newLowerBound, minCandidate);
                                        newUpperBound = Math.max(newUpperBound, maxCandidate);
                                    }
                                    if (b.canBeNegative()) {
                                        if (multiplicationOverflows(minNegA, minNegB, bits)) {
                                            return a.unrestricted();
                                        }
                                        long maxCandidate = minNegA * minNegB;
                                        if (multiplicationOverflows(maxNegA, maxNegB, bits)) {
                                            return a.unrestricted();
                                        }
                                        long minCandidate = maxNegA * maxNegB;
                                        newLowerBound = Math.min(newLowerBound, minCandidate);
                                        newUpperBound = Math.max(newUpperBound, maxCandidate);
                                    }
                                }

                                assert newLowerBound <= newUpperBound;
                                return StampFactory.forIntegerWithMask(bits, newLowerBound, newUpperBound, 0, newUpMask);
                            }
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 1;
                        }
                    },

                    /*
                     * MulHigh is not associative, for example:
                     *
                     * mulHigh(mulHigh(-1, 1), 1) = mulHigh(-1, 1) = -1
                     *
                     * but
                     *
                     * mulHigh(-1, mulHigh(1, 1)) = mulHigh(-1, 0) = 0
                     */
                    new BinaryOp.MulHigh(false, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            return JavaConstant.forIntegerKind(a.getJavaKind(), multiplyHigh(a.asLong(), b.asLong(), a.getJavaKind()));
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            JavaKind javaKind = a.getStackKind();

                            assert a.getBits() == b.getBits();
                            assert javaKind == b.getStackKind();
                            assert (javaKind == JavaKind.Int || javaKind == JavaKind.Long);

                            if (a.isEmpty() || b.isEmpty()) {
                                return a.empty();
                            } else if (a.isUnrestricted() || b.isUnrestricted()) {
                                return a.unrestricted();
                            }

                            long[] xExtremes = {a.lowerBound(), a.upperBound()};
                            long[] yExtremes = {b.lowerBound(), b.upperBound()};
                            long min = Long.MAX_VALUE;
                            long max = Long.MIN_VALUE;
                            for (long x : xExtremes) {
                                for (long y : yExtremes) {
                                    long result = multiplyHigh(x, y, javaKind);
                                    min = Math.min(min, result);
                                    max = Math.max(max, result);
                                }
                            }
                            return StampFactory.forInteger(javaKind, min, max);
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            return false;
                        }

                        private long multiplyHigh(long x, long y, JavaKind javaKind) {
                            if (javaKind == JavaKind.Int) {
                                return (x * y) >> 32;
                            } else {
                                assert javaKind == JavaKind.Long;
                                long x0 = x & 0xFFFFFFFFL;
                                long x1 = x >> 32;

                                long y0 = y & 0xFFFFFFFFL;
                                long y1 = y >> 32;

                                long z0 = x0 * y0;
                                long t = x1 * y0 + (z0 >>> 32);
                                long z1 = t & 0xFFFFFFFFL;
                                long z2 = t >> 32;
                                z1 += x0 * y1;

                                return x1 * y1 + z2 + (z1 >> 32);
                            }
                        }
                    },

                    /*
                     * UMulHigh is not associative, for example:
                     *
                     * uMulHigh(uMulHigh(-1L, Long.MAX_VALUE), 4L) = 1
                     *
                     * but
                     *
                     * uMulHigh(-1L, uMulHigh(Long.MAX_VALUE, 4L)) = 0
                     */
                    new BinaryOp.UMulHigh(false, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            return JavaConstant.forIntegerKind(a.getJavaKind(), multiplyHighUnsigned(a.asLong(), b.asLong(), a.getJavaKind()));
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            JavaKind javaKind = a.getStackKind();

                            assert a.getBits() == b.getBits();
                            assert javaKind == b.getStackKind();
                            assert (javaKind == JavaKind.Int || javaKind == JavaKind.Long);

                            if (a.isEmpty() || b.isEmpty()) {
                                return a.empty();
                            } else if (a.isUnrestricted() || b.isUnrestricted()) {
                                return a.unrestricted();
                            }

                            // Note that the minima and maxima are calculated using signed min/max
                            // functions, while the values themselves are unsigned.
                            long[] xExtremes = getUnsignedExtremes(a);
                            long[] yExtremes = getUnsignedExtremes(b);
                            long min = Long.MAX_VALUE;
                            long max = Long.MIN_VALUE;
                            for (long x : xExtremes) {
                                for (long y : yExtremes) {
                                    long result = multiplyHighUnsigned(x, y, javaKind);
                                    min = Math.min(min, result);
                                    max = Math.max(max, result);
                                }
                            }

                            // if min is negative, then the value can reach into the unsigned range
                            if (min == max || min >= 0) {
                                return StampFactory.forInteger(javaKind, min, max);
                            } else {
                                return StampFactory.forKind(javaKind);
                            }
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            return false;
                        }

                        private long[] getUnsignedExtremes(IntegerStamp stamp) {
                            if (stamp.lowerBound() < 0 && stamp.upperBound() >= 0) {
                                /*
                                 * If -1 and 0 are both in the signed range, then we can't say
                                 * anything about the unsigned range, so we have to return [0,
                                 * MAX_UNSIGNED].
                                 */
                                return new long[]{0, -1L};
                            } else {
                                return new long[]{stamp.lowerBound(), stamp.upperBound()};
                            }
                        }

                        private long multiplyHighUnsigned(long x, long y, JavaKind javaKind) {
                            if (javaKind == JavaKind.Int) {
                                long xl = x & 0xFFFFFFFFL;
                                long yl = y & 0xFFFFFFFFL;
                                long r = xl * yl;
                                return (int) (r >>> 32);
                            } else {
                                assert javaKind == JavaKind.Long;
                                long x0 = x & 0xFFFFFFFFL;
                                long x1 = x >>> 32;

                                long y0 = y & 0xFFFFFFFFL;
                                long y1 = y >>> 32;

                                long z0 = x0 * y0;
                                long t = x1 * y0 + (z0 >>> 32);
                                long z1 = t & 0xFFFFFFFFL;
                                long z2 = t >>> 32;
                                z1 += x0 * y1;

                                return x1 * y1 + z2 + (z1 >>> 32);
                            }
                        }
                    },

                    new BinaryOp.Div(true, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            if (b.asLong() == 0) {
                                return null;
                            }
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() / b.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits();
                            if (a.lowerBound == a.upperBound && b.lowerBound == b.upperBound && b.lowerBound != 0) {
                                long value = CodeUtil.convert(a.lowerBound() / b.lowerBound(), a.getBits(), false);
                                return StampFactory.forInteger(a.getBits(), value, value);
                            } else if (b.isStrictlyPositive()) {
                                long newLowerBound = a.lowerBound() < 0 ? a.lowerBound() / b.lowerBound() : a.lowerBound() / b.upperBound();
                                long newUpperBound = a.upperBound() < 0 ? a.upperBound() / b.upperBound() : a.upperBound() / b.lowerBound();
                                return StampFactory.forInteger(a.getBits(), newLowerBound, newUpperBound);
                            } else {
                                return a.unrestricted();
                            }
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 1;
                        }
                    },

                    new BinaryOp.Rem(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            if (b.asLong() == 0) {
                                return null;
                            }
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() % b.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits();

                            if (a.lowerBound == a.upperBound && b.lowerBound == b.upperBound && b.lowerBound != 0) {
                                long value = CodeUtil.convert(a.lowerBound() % b.lowerBound(), a.getBits(), false);
                                return StampFactory.forInteger(a.getBits(), value, value);
                            }

                            // zero is always possible
                            long newLowerBound = Math.min(a.lowerBound(), 0);
                            long newUpperBound = Math.max(a.upperBound(), 0);

                            /* the maximum absolute value of the result, derived from b */
                            long magnitude;
                            if (b.lowerBound() == CodeUtil.minValue(b.getBits())) {
                                // Math.abs(...) - 1 does not work in a case
                                magnitude = CodeUtil.maxValue(b.getBits());
                            } else {
                                magnitude = Math.max(Math.abs(b.lowerBound()), Math.abs(b.upperBound())) - 1;
                            }
                            newLowerBound = Math.max(newLowerBound, -magnitude);
                            newUpperBound = Math.min(newUpperBound, magnitude);

                            return StampFactory.forInteger(a.getBits(), newLowerBound, newUpperBound);
                        }
                    },

                    new UnaryOp.Not() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forIntegerKind(value.getJavaKind(), ~value.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return stamp;
                            }
                            IntegerStamp integerStamp = (IntegerStamp) stamp;
                            int bits = integerStamp.getBits();
                            long defaultMask = CodeUtil.mask(bits);
                            return new IntegerStamp(bits, ~integerStamp.upperBound(), ~integerStamp.lowerBound(), (~integerStamp.upMask()) & defaultMask, (~integerStamp.downMask()) & defaultMask);
                        }
                    },

                    new BinaryOp.And(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() & b.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits();
                            return stampForMask(a.getBits(), a.downMask() & b.downMask(), a.upMask() & b.upMask());
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            int bits = n.getJavaKind().getBitCount();
                            long mask = CodeUtil.mask(bits);
                            return (n.asLong() & mask) == mask;
                        }
                    },

                    new BinaryOp.Or(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() | b.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits();
                            return stampForMask(a.getBits(), a.downMask() | b.downMask(), a.upMask() | b.upMask());
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 0;
                        }
                    },

                    new BinaryOp.Xor(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() ^ b.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits();

                            long variableBits = (a.downMask() ^ a.upMask()) | (b.downMask() ^ b.upMask());
                            long newDownMask = (a.downMask() ^ b.downMask()) & ~variableBits;
                            long newUpMask = (a.downMask() ^ b.downMask()) | variableBits;
                            return stampForMask(a.getBits(), newDownMask, newUpMask);
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 0;
                        }

                        @Override
                        public Constant getZero(Stamp s) {
                            IntegerStamp stamp = (IntegerStamp) s;
                            return JavaConstant.forPrimitiveInt(stamp.getBits(), 0);
                        }
                    },

                    new ShiftOp.Shl() {

                        @Override
                        public Constant foldConstant(Constant value, int amount) {
                            PrimitiveConstant c = (PrimitiveConstant) value;
                            switch (c.getJavaKind()) {
                                case Byte:
                                    return JavaConstant.forByte((byte) (c.asInt() << amount));
                                case Char:
                                    return JavaConstant.forChar((char) (c.asInt() << amount));
                                case Short:
                                    return JavaConstant.forShort((short) (c.asInt() << amount));
                                case Int:
                                    return JavaConstant.forInt(c.asInt() << amount);
                                case Long:
                                    return JavaConstant.forLong(c.asLong() << amount);
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        private boolean testNoSignChangeAfterShifting(int bits, long value, int shiftAmount) {
                            long removedBits = -1L << (bits - shiftAmount - 1);
                            if (value < 0) {
                                return (value & removedBits) == removedBits;
                            } else {
                                return (value & removedBits) == 0;
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp, IntegerStamp shift) {
                            IntegerStamp value = (IntegerStamp) stamp;
                            int bits = value.getBits();
                            if (value.isEmpty()) {
                                return value;
                            } else if (shift.isEmpty()) {
                                return StampFactory.forInteger(bits).empty();
                            } else if (value.upMask() == 0) {
                                return value;
                            }

                            int shiftMask = getShiftAmountMask(stamp);
                            int shiftBits = Integer.bitCount(shiftMask);
                            if (shift.lowerBound() == shift.upperBound()) {
                                int shiftAmount = (int) (shift.lowerBound() & shiftMask);
                                if (shiftAmount == 0) {
                                    return value;
                                }
                                if (shiftAmount >= bits) {
                                    IntegerStamp result = IntegerStamp.create(bits, 0, 0, 0, 0);
                                    return result;
                                }
                                // the mask of bits that will be lost or shifted into the sign bit
                                if (testNoSignChangeAfterShifting(bits, value.lowerBound(), shiftAmount) && testNoSignChangeAfterShifting(bits, value.upperBound(), shiftAmount)) {
                                    /*
                                     * use a better stamp if neither lower nor upper bound can lose
                                     * bits
                                     */
                                    IntegerStamp result = new IntegerStamp(bits, value.lowerBound() << shiftAmount, value.upperBound() << shiftAmount,
                                                    (value.downMask() << shiftAmount) & CodeUtil.mask(bits),
                                                    (value.upMask() << shiftAmount) & CodeUtil.mask(bits));
                                    return result;
                                }
                            }
                            if ((shift.lowerBound() >>> shiftBits) == (shift.upperBound() >>> shiftBits)) {
                                long defaultMask = CodeUtil.mask(bits);
                                long downMask = defaultMask;
                                long upMask = 0;
                                for (long i = shift.lowerBound(); i <= shift.upperBound(); i++) {
                                    if (shift.contains(i)) {
                                        downMask &= value.downMask() << (i & shiftMask);
                                        upMask |= value.upMask() << (i & shiftMask);
                                    }
                                }
                                return IntegerStamp.stampForMask(bits, downMask, upMask & defaultMask);
                            }
                            return value.unrestricted();
                        }

                        @Override
                        public int getShiftAmountMask(Stamp s) {
                            return s.getStackKind().getBitCount() - 1;
                        }
                    },

                    new ShiftOp.Shr() {

                        @Override
                        public Constant foldConstant(Constant value, int amount) {
                            PrimitiveConstant c = (PrimitiveConstant) value;
                            switch (c.getJavaKind()) {
                                case Byte:
                                    return JavaConstant.forByte((byte) (c.asInt() >> amount));
                                case Char:
                                    return JavaConstant.forChar((char) (c.asInt() >> amount));
                                case Short:
                                    return JavaConstant.forShort((short) (c.asInt() >> amount));
                                case Int:
                                    return JavaConstant.forInt(c.asInt() >> amount);
                                case Long:
                                    return JavaConstant.forLong(c.asLong() >> amount);
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp, IntegerStamp shift) {
                            IntegerStamp value = (IntegerStamp) stamp;
                            int bits = value.getBits();
                            if (value.isEmpty()) {
                                return value;
                            } else if (shift.isEmpty()) {
                                return StampFactory.forInteger(bits).empty();
                            } else if (shift.lowerBound() == shift.upperBound()) {
                                long shiftCount = shift.lowerBound() & getShiftAmountMask(stamp);
                                if (shiftCount == 0) {
                                    return stamp;
                                }

                                int extraBits = 64 - bits;
                                long defaultMask = CodeUtil.mask(bits);
                                // shifting back and forth performs sign extension
                                long downMask = (value.downMask() << extraBits) >> (shiftCount + extraBits) & defaultMask;
                                long upMask = (value.upMask() << extraBits) >> (shiftCount + extraBits) & defaultMask;
                                return new IntegerStamp(bits, value.lowerBound() >> shiftCount, value.upperBound() >> shiftCount, downMask, upMask);
                            }
                            long mask = IntegerStamp.upMaskFor(bits, value.lowerBound(), value.upperBound());
                            return IntegerStamp.stampForMask(bits, 0, mask);
                        }

                        @Override
                        public int getShiftAmountMask(Stamp s) {
                            return s.getStackKind().getBitCount() - 1;
                        }
                    },

                    new ShiftOp.UShr() {

                        @Override
                        public Constant foldConstant(Constant value, int amount) {
                            PrimitiveConstant c = (PrimitiveConstant) value;
                            switch (c.getJavaKind()) {
                                case Byte:
                                    return JavaConstant.forByte((byte) (c.asInt() >>> amount));
                                case Char:
                                    return JavaConstant.forChar((char) (c.asInt() >>> amount));
                                case Short:
                                    return JavaConstant.forShort((short) (c.asInt() >>> amount));
                                case Int:
                                    return JavaConstant.forInt(c.asInt() >>> amount);
                                case Long:
                                    return JavaConstant.forLong(c.asLong() >>> amount);
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp, IntegerStamp shift) {
                            IntegerStamp value = (IntegerStamp) stamp;
                            int bits = value.getBits();
                            if (value.isEmpty()) {
                                return value;
                            } else if (shift.isEmpty()) {
                                return StampFactory.forInteger(bits).empty();
                            }

                            if (shift.lowerBound() == shift.upperBound()) {
                                long shiftCount = shift.lowerBound() & getShiftAmountMask(stamp);
                                if (shiftCount == 0) {
                                    return stamp;
                                }

                                long downMask = value.downMask() >>> shiftCount;
                                long upMask = value.upMask() >>> shiftCount;
                                if (value.lowerBound() < 0) {
                                    return new IntegerStamp(bits, downMask, upMask, downMask, upMask);
                                } else {
                                    return new IntegerStamp(bits, value.lowerBound() >>> shiftCount, value.upperBound() >>> shiftCount, downMask, upMask);
                                }
                            }
                            long mask = IntegerStamp.upMaskFor(bits, value.lowerBound(), value.upperBound());
                            return IntegerStamp.stampForMask(bits, 0, mask);
                        }

                        @Override
                        public int getShiftAmountMask(Stamp s) {
                            return s.getStackKind().getBitCount() - 1;
                        }
                    },

                    new UnaryOp.Abs() {

                        @Override
                        public Constant foldConstant(Constant value) {
                            PrimitiveConstant c = (PrimitiveConstant) value;
                            return JavaConstant.forIntegerKind(c.getJavaKind(), Math.abs(c.asLong()));
                        }

                        @Override
                        public Stamp foldStamp(Stamp input) {
                            if (input.isEmpty()) {
                                return input;
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            int bits = stamp.getBits();
                            if (stamp.lowerBound == stamp.upperBound) {
                                long value = CodeUtil.convert(Math.abs(stamp.lowerBound()), stamp.getBits(), false);
                                return StampFactory.forInteger(stamp.getBits(), value, value);
                            }
                            if (stamp.lowerBound() == CodeUtil.minValue(bits)) {
                                return input.unrestricted();
                            } else {
                                long limit = Math.max(-stamp.lowerBound(), stamp.upperBound());
                                return StampFactory.forInteger(bits, 0, limit);
                            }
                        }
                    },

                    null,

                    new IntegerConvertOp.ZeroExtend() {

                        @Override
                        public Constant foldConstant(int inputBits, int resultBits, Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forPrimitiveInt(resultBits, CodeUtil.zeroExtend(value.asLong(), inputBits));
                        }

                        @Override
                        public Stamp foldStamp(int inputBits, int resultBits, Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.forInteger(resultBits).empty();
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert inputBits == stamp.getBits() : "Input bits" + inputBits + " stamp bits " +
                                            stamp.getBits() + " result bits " + resultBits;
                            assert inputBits <= resultBits;

                            if (inputBits == resultBits) {
                                return input;
                            }

                            if (input.isEmpty()) {
                                return StampFactory.forInteger(resultBits).empty();
                            }

                            long downMask = CodeUtil.zeroExtend(stamp.downMask(), inputBits);
                            long upMask = CodeUtil.zeroExtend(stamp.upMask(), inputBits);
                            long lowerBound = stamp.unsignedLowerBound();
                            long upperBound = stamp.unsignedUpperBound();
                            return IntegerStamp.create(resultBits, lowerBound, upperBound, downMask, upMask);
                        }

                        @Override
                        public Stamp invertStamp(int inputBits, int resultBits, Stamp outStamp) {
                            IntegerStamp stamp = (IntegerStamp) outStamp;
                            if (stamp.isEmpty()) {
                                return StampFactory.forInteger(inputBits).empty();
                            }

                            /*
                             * there is no guarantee that a given result is in the range of the
                             * input because of holes in ranges resulting from signed / unsigned
                             * extension, so we must ensure that the extension bits are all zeros
                             * otherwise we cannot represent the result, and we have to return an
                             * unrestricted stamp
                             *
                             * This case is much less likely to happen than the case for SignExtend
                             * but the following is defensive to ensure that we only perform valid
                             * inversions.
                             */
                            long alwaysSetOutputBits = stamp.downMask();
                            long alwaysSetExtensionBits = alwaysSetOutputBits >>> inputBits;
                            if (alwaysSetExtensionBits != 0) {
                                StampFactory.forInteger(inputBits).empty();
                            }

                            long inputMask = CodeUtil.mask(inputBits);
                            return StampFactory.forUnsignedInteger(inputBits, stamp.lowerBound(), stamp.upperBound(), stamp.downMask() & inputMask, stamp.upMask() & inputMask);
                        }
                    },

                    new IntegerConvertOp.SignExtend() {

                        @Override
                        public Constant foldConstant(int inputBits, int resultBits, Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forPrimitiveInt(resultBits, CodeUtil.signExtend(value.asLong(), inputBits));
                        }

                        @Override
                        public Stamp foldStamp(int inputBits, int resultBits, Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.forInteger(resultBits).empty();
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert inputBits == stamp.getBits();
                            assert inputBits <= resultBits;

                            long defaultMask = CodeUtil.mask(resultBits);
                            long downMask = CodeUtil.signExtend(stamp.downMask(), inputBits) & defaultMask;
                            long upMask = CodeUtil.signExtend(stamp.upMask(), inputBits) & defaultMask;

                            return new IntegerStamp(resultBits, stamp.lowerBound(), stamp.upperBound(), downMask, upMask);
                        }

                        @Override
                        public Stamp invertStamp(int inputBits, int resultBits, Stamp outStamp) {
                            IntegerStamp stamp = (IntegerStamp) outStamp;
                            if (stamp.isEmpty()) {
                                return StampFactory.forInteger(inputBits).empty();
                            }

                            /*
                             * there is no guarantee that a given result bit is in the range of the
                             * input because of holes in ranges resulting from signed / unsigned
                             * extension, so we must ensure that the extension bits are either all
                             * zeros or all ones otherwise we cannot represent the result, and we
                             * have to return an unrestricted stamp
                             *
                             * As an example:
                             * @formatter:off
                             * byte a = ...
                             * char b = (char) a;
                             * if ((short) b != 45832)
                             * @formatter:on
                             *
                             * the flow from a to the use of b in terms of nodes would be:
                             * @formatter:off
                             * read#Array byte (stamp i8 [ -128 - 127 ])
                             *   V
                             * SignExtend (stamp i16 [ -128 - 127 ])
                             *   V
                             * ZeroExtend (stamp i32 [ 0 - 65535 ])
                             * @formatter:on
                             *
                             * The stamp on the compare of b suggests that b could equal 45832
                             * 0x0000b308. If we assume the value is 0x0000b308. We invert the
                             * ZeroExtend to get 0xb308, but then we try to invert the SignExtend.
                             * The sign extend could only have produced 0xff__ or 0x00__ from a byte
                             * but 0xb308 has 0xb3, and so we cannot invert the stamp. In this case
                             * the only sensible inversion is the unrestricted stamp.
                             */
                            long alwaysSetOutputBits = stamp.downMask();
                            long alwaysSetExtensionBits = alwaysSetOutputBits >>> inputBits;
                            long outputMask = CodeUtil.mask(resultBits);
                            if (alwaysSetExtensionBits != 0 && alwaysSetExtensionBits != (outputMask >>> inputBits)) {
                                return StampFactory.forInteger(inputBits).empty();
                            }

                            long inputMask = CodeUtil.mask(inputBits);
                            return StampFactory.forIntegerWithMask(inputBits, stamp.lowerBound(), stamp.upperBound(), stamp.downMask() & inputMask, stamp.upMask() & inputMask);
                        }
                    },

                    new IntegerConvertOp.Narrow() {

                        @Override
                        public Constant foldConstant(int inputBits, int resultBits, Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forPrimitiveInt(resultBits, CodeUtil.narrow(value.asLong(), resultBits));
                        }

                        @Override
                        public Stamp foldStamp(int inputBits, int resultBits, Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.forInteger(resultBits).empty();
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert inputBits == stamp.getBits();
                            assert resultBits <= inputBits;
                            if (resultBits == inputBits) {
                                return stamp;
                            }

                            final long upperBound;
                            if (stamp.lowerBound() < CodeUtil.minValue(resultBits)) {
                                upperBound = CodeUtil.maxValue(resultBits);
                            } else {
                                upperBound = saturate(stamp.upperBound(), resultBits);
                            }
                            final long lowerBound;
                            if (stamp.upperBound() > CodeUtil.maxValue(resultBits)) {
                                lowerBound = CodeUtil.minValue(resultBits);
                            } else {
                                lowerBound = saturate(stamp.lowerBound(), resultBits);
                            }

                            long defaultMask = CodeUtil.mask(resultBits);
                            long newDownMask = stamp.downMask() & defaultMask;
                            long newUpMask = stamp.upMask() & defaultMask;
                            long newLowerBound = CodeUtil.signExtend((lowerBound | newDownMask) & newUpMask, resultBits);
                            long newUpperBound = CodeUtil.signExtend((upperBound | newDownMask) & newUpMask, resultBits);

                            IntegerStamp result = new IntegerStamp(resultBits, newLowerBound, newUpperBound, newDownMask, newUpMask);
                            assert result.hasValues();
                            return result;
                        }
                    },

                    new BinaryOp.Max(true, true) {

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant x = (PrimitiveConstant) a;
                            PrimitiveConstant y = (PrimitiveConstant) b;
                            return JavaConstant.forIntegerKind(x.getJavaKind(), Math.max(x.asLong(), y.asLong()));
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }
                            if (b.isEmpty()) {
                                return b;
                            }
                            IntegerStamp x = (IntegerStamp) a;
                            IntegerStamp y = (IntegerStamp) b;
                            return StampFactory.forInteger(x.getBits(), Math.max(x.lowerBound(), y.lowerBound()), Math.max(x.upperBound(), y.upperBound()));
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            int bits = n.getJavaKind().getBitCount();
                            return n.asLong() == NumUtil.minValue(bits);
                        }
                    },

                    new BinaryOp.Min(true, true) {

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant x = (PrimitiveConstant) a;
                            PrimitiveConstant y = (PrimitiveConstant) b;
                            return JavaConstant.forIntegerKind(x.getJavaKind(), Math.min(x.asLong(), y.asLong()));
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }
                            if (b.isEmpty()) {
                                return b;
                            }
                            IntegerStamp x = (IntegerStamp) a;
                            IntegerStamp y = (IntegerStamp) b;
                            return StampFactory.forInteger(x.getBits(), Math.min(x.lowerBound(), y.lowerBound()), Math.min(x.upperBound(), y.upperBound()));
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            int bits = n.getJavaKind().getBitCount();
                            return n.asLong() == NumUtil.maxValue(bits);
                        }
                    },

                    new BinaryOp.UMax(true, true) {

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant x = (PrimitiveConstant) a;
                            PrimitiveConstant y = (PrimitiveConstant) b;
                            return JavaConstant.forIntegerKind(x.getJavaKind(), NumUtil.maxUnsigned(x.asLong(), y.asLong()));
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }
                            if (b.isEmpty()) {
                                return b;
                            }
                            IntegerStamp x = (IntegerStamp) a;
                            IntegerStamp y = (IntegerStamp) b;
                            return StampFactory.forUnsignedInteger(x.getBits(), NumUtil.maxUnsigned(x.unsignedLowerBound(), y.unsignedLowerBound()),
                                            NumUtil.maxUnsigned(x.unsignedUpperBound(), y.unsignedUpperBound()));
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 0;
                        }
                    },

                    new BinaryOp.UMin(true, true) {

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant x = (PrimitiveConstant) a;
                            PrimitiveConstant y = (PrimitiveConstant) b;
                            return JavaConstant.forIntegerKind(x.getJavaKind(), NumUtil.minUnsigned(x.asLong(), y.asLong()));
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }
                            if (b.isEmpty()) {
                                return b;
                            }
                            IntegerStamp x = (IntegerStamp) a;
                            IntegerStamp y = (IntegerStamp) b;
                            return StampFactory.forUnsignedInteger(x.getBits(), NumUtil.minUnsigned(x.unsignedLowerBound(), y.unsignedLowerBound()),
                                            NumUtil.minUnsigned(x.unsignedUpperBound(), y.unsignedUpperBound()));
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            int bits = n.getJavaKind().getBitCount();
                            return CodeUtil.zeroExtend(n.asLong(), bits) == NumUtil.maxValueUnsigned(bits);
                        }
                    },

                    new ReinterpretOp() {

                        @Override
                        public Constant foldConstant(Stamp resultStamp, Constant constant) {
                            return ReinterpretUtils.foldConstant(resultStamp, constant);
                        }

                        @Override
                        public Stamp foldStamp(Stamp resultStamp, Stamp input) {
                            if (input.isEmpty()) {
                                return resultStamp.empty();
                            } else if (resultStamp instanceof FloatStamp && input instanceof IntegerStamp) {
                                return ReinterpretUtils.intToFloat((IntegerStamp) input);
                            } else {
                                return resultStamp;
                            }
                        }
                    },

                    new FloatConvertOp(I2F) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forFloat(value.asInt());
                        }

                        @Override
                        public Stamp foldStamp(Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.empty(JavaKind.Float);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert stamp.getBits() == 32;
                            float lowerBound = stamp.lowerBound();
                            float upperBound = stamp.upperBound();
                            return StampFactory.forFloat(JavaKind.Float, lowerBound, upperBound, true);
                        }
                    },

                    new FloatConvertOp(L2F) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forFloat(value.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.empty(JavaKind.Float);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert stamp.getBits() == 64;
                            float lowerBound = stamp.lowerBound();
                            float upperBound = stamp.upperBound();
                            return StampFactory.forFloat(JavaKind.Float, lowerBound, upperBound, true);
                        }
                    },

                    new FloatConvertOp(I2D) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forDouble(value.asInt());
                        }

                        @Override
                        public Stamp foldStamp(Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.empty(JavaKind.Double);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert stamp.getBits() == 32;
                            double lowerBound = stamp.lowerBound();
                            double upperBound = stamp.upperBound();
                            return StampFactory.forFloat(JavaKind.Double, lowerBound, upperBound, true);
                        }
                    },

                    new FloatConvertOp(L2D) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forDouble(value.asLong());
                        }

                        @Override
                        public Stamp foldStamp(Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.empty(JavaKind.Double);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert stamp.getBits() == 64;
                            double lowerBound = stamp.lowerBound();
                            double upperBound = stamp.upperBound();
                            return StampFactory.forFloat(JavaKind.Double, lowerBound, upperBound, true);
                        }
                    });
}
