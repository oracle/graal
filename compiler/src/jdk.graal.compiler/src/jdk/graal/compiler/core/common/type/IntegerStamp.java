/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.calc.FloatConvert.I2D;
import static jdk.graal.compiler.core.common.calc.FloatConvert.I2F;
import static jdk.graal.compiler.core.common.calc.FloatConvert.L2D;
import static jdk.graal.compiler.core.common.calc.FloatConvert.L2F;
import static jdk.vm.ci.code.CodeUtil.isPowerOf2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.ReinterpretUtils;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
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
 * The description consists of inclusive lower and upper bounds and up (may be set) and down (must
 * be set) bit-masks. The masks combine to describe whether a particular bit is 1, 0 or unknown. The
 * must be set mask naturally represents bits which are 1 by having a 1 bit at that location. The
 * may be set mask has a 0 at bits which must be 0 and a 1 where it may or must be 1. So the may be
 * set mask is always a superset of the must be set mask.
 */
public final class IntegerStamp extends PrimitiveStamp {

    /**
     * Inclusive lower bound.
     */
    private final long lowerBound;

    /**
     * Inclusive upper bound.
     */
    private final long upperBound;

    /**
     * This indicates which bits must be set. For every value in range,
     * {@code (value & mustBeSet) == mustBeSet} is true. This stamp only describes values that are
     * in range and for which {@code (value & mustBeSet) == mustBeSet} is true.
     */
    private final long mustBeSet;

    /**
     * This indicates which bits may be set. For every value in range,
     * {@code (value & mayBeSet) == (value & CodeUtil.mask(getBits()))} is true. This mask is always
     * a superset of mustBeSet.
     */
    private final long mayBeSet;

    /**
     * Determines if this stamp can contain the value {@code 0}. If this is {@code true} the stamp
     * is a typical stamp without holes. If the stamp ranges over zero but {@code canBeZero==false}
     * it means the stamp contains a "hole", i.e., all values in the range except zero. We typically
     * cannot express holes in our Stamp system, thus we have the special logic for {@code 0}.
     */
    private final boolean canBeZero;

    /**
     * Build the empty or unrestricted stamp.
     */
    private IntegerStamp(int bits, boolean empty) {
        super(bits, OPS);
        if (empty) {
            this.lowerBound = CodeUtil.maxValue(bits);
            this.upperBound = CodeUtil.minValue(bits);
            this.mustBeSet = CodeUtil.mask(bits);
            this.mayBeSet = 0;
            this.canBeZero = false;
        } else {
            this.lowerBound = CodeUtil.minValue(bits);
            this.upperBound = CodeUtil.maxValue(bits);
            this.mustBeSet = 0;
            this.mayBeSet = CodeUtil.mask(bits);
            this.canBeZero = true;
        }
    }

    /**
     * Create a stamp for a constant.
     */
    private IntegerStamp(int bits, long constant) {
        this(bits, constant, constant, constant & CodeUtil.mask(bits), constant & CodeUtil.mask(bits), constant == 0);
    }

    /**
     * Create a stamp for a range.
     */
    private IntegerStamp(int bits, long lowerBound, long upperBound) {
        super(bits, OPS);
        int sameBitCount = Long.numberOfLeadingZeros(lowerBound ^ upperBound);
        long sameBitMask = -1L >>> sameBitCount;
        long defaultMask = CodeUtil.mask(bits);

        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.mustBeSet = defaultMask & (lowerBound & ~sameBitMask);
        this.mayBeSet = defaultMask & (lowerBound | sameBitMask);

        this.canBeZero = contains(0, true);
        assert checkInvariants();
    }

    private IntegerStamp(int bits, long lowerBound, long upperBound, long mustBeSet, long mayBeSet, boolean canBeZero) {
        super(bits, OPS);

        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.mustBeSet = mustBeSet;
        this.mayBeSet = mayBeSet;

        // use ctor param because canBeZero is not set yet
        this.canBeZero = contains(0, canBeZero);
        assert checkInvariants();
    }

    private boolean checkInvariants() {
        final int allowedBitsMask = 1 | 8 | 16 | 32 | 64;
        assert (getBits() & allowedBitsMask) == getBits() && CodeUtil.isPowerOf2(getBits()) : "unexpected bit size: " + getBits();
        assert lowerBound >= CodeUtil.minValue(getBits()) : this;
        assert upperBound <= CodeUtil.maxValue(getBits()) : this;
        assert (mustBeSet & CodeUtil.mask(getBits())) == mustBeSet : this;
        assert (mayBeSet & CodeUtil.mask(getBits())) == mayBeSet : this;
        // Check for valid masks or the empty encoding
        assert (mustBeSet & ~mayBeSet) == 0 || (mayBeSet == 0 && mustBeSet == CodeUtil.mask(getBits())) : String.format("must: %016x may: %016x", mustBeSet, mayBeSet);
        assert !this.canBeZero || contains(0) : " Stamp " + this + " either has canBeZero set to false or needs to contain 0";
        assert !isEmpty() : String.format("unexpected empty stamp: %s %s %s %s %s %s", lowerBound, upperBound, mustBeSet, mayBeSet, canBeZero, this);
        assert contains(upperBound) : String.format("%s must contain its upper bound", this);
        assert contains(lowerBound) : String.format("%s must contain its lower bound", this);
        return true;
    }

    /**
     * Create a stamp for a constant value.
     */
    public static IntegerStamp createConstant(int bits, long value) {
        return new IntegerStamp(bits, value);
    }

    /**
     * Return the unrestricted stamp.
     */
    public static IntegerStamp create(int bits) {
        return unrestrictedStamps[CodeUtil.log2(bits)];
    }

    /**
     * Return a stamp covering the input range with the default masks.
     */
    public static IntegerStamp create(int bits, long lowerBoundInput, long upperBoundInput) {
        if (lowerBoundInput > upperBoundInput) {
            return createEmptyStamp(bits);
        }

        if (lowerBoundInput == upperBoundInput) {
            return createConstant(bits, lowerBoundInput);
        }

        return new IntegerStamp(bits, lowerBoundInput, upperBoundInput);
    }

    public static IntegerStamp create(int bits, long lowerBoundInput, long upperBoundInput, long mustBeSet, long mayBeSet) {
        return create(bits, lowerBoundInput, upperBoundInput, mustBeSet, mayBeSet, true);
    }

    /**
     * Limit the number of times the stamp refinement loop will run. A final pass is always run to
     * confirm that a stable point has been reached so there are only 2 actual passes that can
     * change the value. Normally this consists of refining the bounds based on the masks and then
     * further refining the masks from the resulting stamps. In real programs the arguments are
     * unchanged after the first pass about 80% of the time. If the arguments do change then only
     * about 1 in 100000 cases change them once more.
     */
    static final int ITERATION_LIMIT = 3;

    public static IntegerStamp create(int bits, long lowerBoundInput, long upperBoundInput, long mustBeSetInput, long mayBeSetInput, boolean canBeZero) {
        assert lowerBoundInput >= CodeUtil.minValue(bits) && lowerBoundInput <= CodeUtil.maxValue(bits) : Assertions.errorMessageContext("bits", bits, "lowerBound", lowerBoundInput, "upperBound",
                        upperBoundInput, "mustBeSetInput", mustBeSetInput, "mayBeSetInput", mayBeSetInput, "canBeZero", canBeZero);
        assert upperBoundInput >= CodeUtil.minValue(bits) && upperBoundInput <= CodeUtil.maxValue(bits) : Assertions.errorMessageContext("bits", bits, "lowerBound", lowerBoundInput, "upperBound",
                        upperBoundInput, "mustBeSetInput", mustBeSetInput, "mayBeSetInput", mayBeSetInput, "canBeZero", canBeZero);

        if (isEmpty(lowerBoundInput, upperBoundInput, mustBeSetInput, mayBeSetInput)) {
            return createEmptyStamp(bits);
        }

        long defaultMask = CodeUtil.mask(bits);

        if (mustBeSetInput == 0 && mayBeSetInput == defaultMask && canBeZero) {
            return create(bits, lowerBoundInput, upperBoundInput);
        }

        long lowerBoundCurrent = lowerBoundInput;
        long upperBoundCurrent = upperBoundInput;
        long mustBeSetCurrent = mustBeSetInput;
        long mayBeSetCurrent = mayBeSetInput;

        int iterations = 0;
        while (iterations++ < ITERATION_LIMIT) {

            // Set lower bound, use masks to make it more precise
            long minValue = minValueForMasks(bits, mustBeSetCurrent, mayBeSetCurrent);
            long lowerBoundTmp = Math.max(lowerBoundCurrent, minValue);

            // Set upper bound, use masks to make it more precise
            long maxValue = maxValueForMasks(bits, mustBeSetCurrent, mayBeSetCurrent);
            long upperBoundTmp = Math.min(upperBoundCurrent, maxValue);

            // Compute masks with the new bounds in mind.
            final long boundedMustBeSet;
            final long boundedMayBeSet;
            if (lowerBoundTmp == upperBoundTmp) {
                // For constants the masks are just the value
                boundedMustBeSet = lowerBoundTmp;
                boundedMayBeSet = lowerBoundTmp;
            } else {
                /*
                 * Any high bits that are the same between the upper and lower bound can be used to
                 * refine the mayBeSet and mustBeSet. xor'ing the bounds produces leading zeros for
                 * these bits.
                 */
                int sameBitCount = Long.numberOfLeadingZeros(lowerBoundTmp ^ upperBoundTmp);
                long sameBitMask = -1L >>> sameBitCount;
                boundedMayBeSet = lowerBoundTmp | sameBitMask;
                boundedMustBeSet = lowerBoundTmp & ~sameBitMask;
            }

            long mustBeSetTmp = defaultMask & (mustBeSetCurrent | boundedMustBeSet);
            long mayBeSetTmp = defaultMask & mayBeSetCurrent & boundedMayBeSet;

            // Now recompute the bounds from any adjustments to the may and must masks
            upperBoundTmp = Math.min(upperBoundTmp, maxValueForMasks(bits, mustBeSetTmp, mayBeSetTmp));
            lowerBoundTmp = Math.max(lowerBoundTmp, minValueForMasks(bits, mustBeSetTmp, mayBeSetTmp));

            upperBoundTmp = computeUpperBound(bits, upperBoundTmp, mustBeSetTmp, mayBeSetTmp, canBeZero);
            lowerBoundTmp = computeLowerBound(bits, lowerBoundTmp, mustBeSetTmp, mayBeSetTmp, canBeZero);

            if (lowerBoundTmp > upperBoundTmp || (mustBeSetTmp & (~mayBeSetTmp)) != 0 || (mayBeSetTmp == 0 && (lowerBoundTmp > 0 || upperBoundTmp < 0))) {
                return createEmptyStamp(bits);
            }

            if (lowerBoundCurrent == lowerBoundTmp && upperBoundCurrent == upperBoundTmp && mustBeSetCurrent == mustBeSetTmp && mayBeSetCurrent == mayBeSetTmp) {
                // The values have reached a stable state. If the incoming values are unchanged then
                // this completes in a single pass but if they change then another iteration is
                // performed to ensure the values have stabilized.
                return new IntegerStamp(bits, lowerBoundTmp, upperBoundTmp, mustBeSetTmp, mayBeSetTmp, canBeZero);
            }

            GraalError.guarantee(lowerBoundTmp >= lowerBoundCurrent, "lower bound can't get smaller: %s < %s", lowerBoundTmp, lowerBoundCurrent);
            GraalError.guarantee(upperBoundTmp <= upperBoundCurrent, "upper bound can't get larger: %s > %s", upperBoundTmp, upperBoundCurrent);

            lowerBoundCurrent = lowerBoundTmp;
            upperBoundCurrent = upperBoundTmp;
            mustBeSetCurrent = mustBeSetTmp;
            mayBeSetCurrent = mayBeSetTmp;
        }
        throw GraalError.shouldNotReachHere("More than " + ITERATION_LIMIT + "iterations required to reach a stable stamp");
    }

    /**
     * A stamp is empty if the lower bound is greater than the upper bound, the mustBeSet contains
     * bits which are not part of the mayBeSet, or there are no bits set and the bound doesn't
     * contain 0.
     */
    private static boolean isEmpty(long lowerBound, long upperBound, long mustBeSet, long mayBeSet) {
        return lowerBound > upperBound || (mustBeSet & (~mayBeSet)) != 0 || (mayBeSet == 0 && (lowerBound > 0 || upperBound < 0));
    }

    private static long significantBit(long bits, long value) {
        return (value >>> (bits - 1)) & 1;
    }

    private static long minValueForMasks(int bits, long mustBeSet, long mayBeSet) {
        if (significantBit(bits, mayBeSet) == 0) {
            // Value is always positive. Minimum value always positive.
            assert significantBit(bits, mustBeSet) == 0 : String.format("must: %016x may: %016x", mustBeSet, mayBeSet);
            return mustBeSet;
        } else {
            // Value can be positive or negative. Minimum value always negative.
            return mustBeSet | (-1L << (bits - 1));
        }
    }

    private static long maxValueForMasks(int bits, long mustBeSet, long mayBeSet) {
        if (significantBit(bits, mustBeSet) == 1) {
            // Value is always negative. Maximum value always negative.
            assert significantBit(bits, mayBeSet) == 1 : Assertions.errorMessageContext("bits", bits, "mayBeSet", mayBeSet);
            return CodeUtil.signExtend(mayBeSet, bits);
        } else {
            // Value can be positive or negative. Maximum value always positive.
            return mayBeSet & (CodeUtil.mask(bits) >>> 1);
        }
    }

    /**
     * Compute the upper bounds by starting from an initial value derived from mustBeSet and then
     * setting optional bits until a value which is less than or equal to the current upper bound is
     * found. Returns {@code CodeUtil.minValue(bits)} if no such value can be found.
     */
    private static long computeUpperBound(int bits, long upperBound, long mustBeSet, long mayBeSet, boolean canBeZero) {
        // Start with the sign extended mustBeSet. That will be the smallest positive or negative
        // value.
        long newUpperBound = CodeUtil.signExtend(mustBeSet, bits);
        if (upperBound < 0 || newUpperBound > upperBound) {
            // If the upper bound is negative or it's positive but greater than the least
            // positive value, then start from the minimum negative value
            newUpperBound = minValueForMasks(bits, mustBeSet, mayBeSet);
        }
        // Compute the bits which are set in the mayBeSet but not the mustBeSet, ignoring the sign
        // bit which was handled above.
        newUpperBound = setOptionalBits(bits, upperBound, mustBeSet, mayBeSet, newUpperBound);

        if (newUpperBound == 0 && !canBeZero) {
            // The actual upper bound must be negative
            if (significantBit(bits, mayBeSet) == 0) {
                // All values are positive so return the minimum value.
                return CodeUtil.minValue(bits);
            } else {
                // Choose the max negative value
                newUpperBound = maxValueForMasks(bits, mustBeSet | (1L << bits - 1), mayBeSet);
            }
        }

        if (newUpperBound > upperBound) {
            // The smallest upper bound that's compatible with the masks is larger than the expected
            // upper bound, so return the minimum value.
            return CodeUtil.minValue(bits);
        }
        return newUpperBound;
    }

    /**
     * Starting from an initial value derived from the must be set mask and ignoring the sign bits,
     * start setting optional bits until a value which is less than or equal to the bound is
     * reached.
     */
    private static long setOptionalBits(int bits, long bound, long mustBeSet, long mayBeSet, long initialValue) {
        final long optionalBits = mayBeSet & ~mustBeSet & CodeUtil.mask(bits - 1);
        assert (initialValue & optionalBits) == 0 : Assertions.errorMessageContext("bits", bits, "bound", bound, "mustBeSet", mustBeSet, "mayBeSet", mayBeSet, "initialValue", initialValue);
        long value = initialValue;
        for (int position = bits - 1; position >= 0; position--) {
            long bit = 1L << position;
            if ((bit & optionalBits) != 0 && (value | bit) <= bound) {
                value |= bit;
            }
        }
        return value;
    }

    /**
     * Compute a lower bound which is compatible with the masks. Returns
     * {@code CodeUtil.maxValue(bits)} if no such value can be found.
     */
    private static long computeLowerBound(int bits, long lowerBound, long mustBeSet, long mayBeSet, boolean canBeZero) {
        long newLowerBound = minValueForMasks(bits, mustBeSet, mayBeSet);
        final long optionalBits = mayBeSet & ~mustBeSet & CodeUtil.mask(bits - 1);
        if (newLowerBound < lowerBound) {
            if (optionalBits == 0) {
                newLowerBound = 0;
            } else {
                // First find the largest value which is less than or equal to the current bound
                for (int position = bits - 1; position >= 0; position--) {
                    long bit = 1L << position;
                    if ((bit & optionalBits) != 0) {
                        if (newLowerBound + bit <= lowerBound) {
                            newLowerBound += bit;
                        }
                    }
                }
                GraalError.guarantee(newLowerBound <= lowerBound, "should have been sufficient");
                if (newLowerBound < lowerBound) {
                    // Increment the first optional bit and then adjust the bits upward until it's
                    // compatible with the masks
                    boolean incremented = false;
                    for (int position = 0; position < bits - 1; position++) {
                        long bit = 1L << position;
                        if (incremented) {
                            // We have to propagate any carried bit that changed any bits which must
                            // be set or cleared.
                            if ((bit & mustBeSet) != 0 && (newLowerBound & bit) == 0) {
                                // A mustBeSet bit has been cleared so set it
                                newLowerBound |= bit;
                            }
                            if ((bit & mayBeSet) == 0 && (newLowerBound & bit) != 0) {
                                // A bit has carried into the clear section so it needs to propagate
                                // into an mayBeSet bit.
                                newLowerBound += bit;
                            }
                        } else if ((bit & optionalBits) != 0) {
                            newLowerBound += bit;
                            incremented = true;
                        }
                    }
                }
            }
        }
        if (newLowerBound == 0 && !canBeZero) {
            // The actual upper bound must positive
            if (mustBeSet > 0) {
                newLowerBound = mustBeSet;
            } else if (mustBeSet == 0) {
                int lowBit = Long.numberOfTrailingZeros(mayBeSet);
                newLowerBound = 1L << lowBit;
            } else {
                // There is no positive value which is compatible with the masks to return the max
                // value.
                newLowerBound = CodeUtil.maxValue(bits);
            }
        }
        if (newLowerBound < lowerBound) {
            // There is no lower bound which is greater than or equal to the current bound
            // so return the max value.
            return CodeUtil.maxValue(bits);
        }
        return newLowerBound;
    }

    public static IntegerStamp stampForMask(int bits, long mustBeSet, long mayBeSet) {
        /*
         * Determine if the new stamp created by down & mayBeSet would be contradicting, i.e., empty
         * by definition. This can happen for example if binary logic operations are evaluated
         * repetitively on different branches creating values that are infeasible by definition
         * (logic nodes on phi nodes of false evaluated predecessors).
         */
        if ((mustBeSet & ~mayBeSet) != 0L) {
            return createEmptyStamp(bits);
        }
        return new IntegerStamp(bits, minValueForMasks(bits, mustBeSet, mayBeSet), maxValueForMasks(bits, mustBeSet, mayBeSet), mustBeSet, mayBeSet, true);
    }

    @Override
    public IntegerStamp unrestricted() {
        return create(getBits());
    }

    @Override
    public IntegerStamp empty() {
        return createEmptyStamp(getBits());
    }

    static IntegerStamp createEmptyStamp(int bits) {
        assert isPowerOf2(bits);
        return emptyStamps[CodeUtil.log2(bits)];
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
            Stamp returnedStamp = createConstant(getBits(), value);
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
                throw GraalError.shouldNotReachHereUnexpectedValue(getBits()); // ExcludeFromJacocoGeneratedReport
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
                throw GraalError.shouldNotReachHereUnexpectedValue(getBits()); // ExcludeFromJacocoGeneratedReport
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
    public long mustBeSet() {
        return mustBeSet;
    }

    /**
     * This bit-mask describes the bits that can be set in the value described by this stamp.
     */
    public long mayBeSet() {
        return mayBeSet;
    }

    @Override
    public boolean isUnrestricted() {
        return lowerBound == CodeUtil.minValue(getBits()) && upperBound == CodeUtil.maxValue(getBits()) && mustBeSet == 0 && mayBeSet == CodeUtil.mask(getBits()) && canBeZero;
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
        return value >= lowerBound && value <= upperBound && (value & mustBeSet) == mustBeSet && (value & mayBeSet) == (value & CodeUtil.mask(getBits()));
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
            if (lowerBound != upperBound && (mustBeSet != 0 || mayBeSet != CodeUtil.mask(getBits()))) {
                // Emit a string describing the state of each bit, summarizing any leading repeated
                // bits since this representation is very verbose otherwise.
                str.append(" bits:");
                char firstChar = 0;
                boolean summarized = false;
                for (int i = getBits() - 1; i >= 0; i--) {
                    long bit = 1L << i;
                    char c = 'x';
                    if ((mayBeSet & bit) == 0) {
                        // A zero in mayBeSet means the value must be 0
                        c = '0';
                    } else if ((mustBeSet & bit) == bit) {
                        // A one in mustBeSet means the value must be 1
                        c = '1';
                    }
                    if (summarized) {
                        str.append(c);
                        continue;
                    }
                    // Summarize leading repeated characters
                    if (firstChar == 0) {
                        firstChar = c;
                    } else if (firstChar != c) {
                        // The bits have changed, so summarize the leading bits
                        int leading = getBits() - 1 - i;
                        if (leading > 8) {
                            str.append(firstChar).append("...").append(firstChar);
                        } else {
                            for (int j = 0; j < leading; j++) {
                                str.append(firstChar);
                            }
                        }
                        str.append(c);
                        summarized = true;
                    }
                }
            }
            if (!canBeZero && contains(0, true)) {
                // Only print this for ranges which could contain 0
                str.append(" {!=0}");
            }
        } else {
            str.append("<empty>");
        }
        return str.toString();
    }

    private IntegerStamp createStamp(IntegerStamp other, long newUpperBound, long newLowerBound, long newMustBeSet, long newMayBeSet, boolean newCanBeZero) {
        assert getBits() == other.getBits() : "Bits must match " + Assertions.errorMessageContext("this", this, "other", other);
        if (isEmpty(newLowerBound, newUpperBound, newMustBeSet, newMayBeSet)) {
            return empty();
        } else if (newLowerBound == lowerBound && newUpperBound == upperBound && newMustBeSet == mustBeSet && newMayBeSet == mayBeSet && canBeZero == newCanBeZero) {
            return this;
        } else if (newLowerBound == other.lowerBound && newUpperBound == other.upperBound && newMustBeSet == other.mustBeSet && newMayBeSet == other.mayBeSet && newCanBeZero == other.canBeZero) {
            return other;
        } else {
            return IntegerStamp.create(getBits(), newLowerBound, newUpperBound, newMustBeSet, newMayBeSet, newCanBeZero);
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
        return createStamp(other, Math.max(upperBound, other.upperBound), Math.min(lowerBound, other.lowerBound), mustBeSet & other.mustBeSet, mayBeSet | other.mayBeSet, canBeZero || other.canBeZero);
    }

    @Override
    public IntegerStamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        IntegerStamp other = (IntegerStamp) otherStamp;
        long newMustBeSet = mustBeSet | other.mustBeSet;
        long newLowerBound = Math.max(lowerBound, other.lowerBound);
        long newUpperBound = Math.min(upperBound, other.upperBound);
        long newMayBeSet = mayBeSet & other.mayBeSet;
        boolean newCanBeZero = canBeZero && other.canBeZero;
        return createStamp(other, newUpperBound, newLowerBound, newMustBeSet, newMayBeSet, newCanBeZero);
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
        result = prime * result + (int) (mustBeSet ^ (mustBeSet >>> 32));
        result = prime * result + (int) (mayBeSet ^ (mayBeSet >>> 32));
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
        if (lowerBound != other.lowerBound || upperBound != other.upperBound || mustBeSet != other.mustBeSet || mayBeSet != other.mayBeSet || canBeZero != other.canBeZero) {
            return false;
        }
        return super.equals(other);
    }

    private static long mayBeSetFor(int bits, long lowerBound, long upperBound) {
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
        assert a.getBits() == b.getBits() : "Bits must match " + Assertions.errorMessageContext("a", a, "b", b);

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
        assert bits <= 64 && bits >= 0 : bits;
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
        assert a.getBits() == b.getBits() : "Bits must match " + Assertions.errorMessageContext("a", a, "b", b);

        if (a.mayBeSet() == 0) {
            return false;
        } else if (b.mayBeSet() == 0) {
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
        assert x.getBits() == y.getBits() : "Bits must match " + Assertions.errorMessageContext("x", x, "y", y);
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

                    new ArithmeticOpTable.UnaryOp.Neg() {

                        @Override
                        public Constant foldConstant(Constant value) {
                            PrimitiveConstant c = (PrimitiveConstant) value;
                            return JavaConstant.forIntegerKind(c.getJavaKind(), -c.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            IntegerStamp stamp = (IntegerStamp) s;
                            int bits = stamp.getBits();
                            if (stamp.lowerBound == stamp.upperBound) {
                                long value = CodeUtil.convert(-stamp.lowerBound(), stamp.getBits(), false);
                                return createConstant(stamp.getBits(), value);
                            }
                            if (stamp.lowerBound() != CodeUtil.minValue(bits)) {
                                // TODO(ls) check if the mask calculation is correct...
                                return create(bits, -stamp.upperBound(), -stamp.lowerBound());
                            } else {
                                return stamp.unrestricted();
                            }
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Add(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() + b.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
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
                                return createConstant(a.getBits(), value);
                            }

                            if (a.isUnrestricted()) {
                                return a;
                            } else if (b.isUnrestricted()) {
                                return b;
                            }
                            long defaultMask = CodeUtil.mask(bits);
                            long variableBits = (a.mustBeSet() ^ a.mayBeSet()) | (b.mustBeSet() ^ b.mayBeSet());
                            long variableBitsWithCarry = variableBits | (carryBits(a.mustBeSet(), b.mustBeSet()) ^ carryBits(a.mayBeSet(), b.mayBeSet()));
                            long newMustBeSet = (a.mustBeSet() + b.mustBeSet()) & ~variableBitsWithCarry;
                            long newMayBeSet = (a.mustBeSet() + b.mustBeSet()) | variableBitsWithCarry;

                            newMustBeSet &= defaultMask;
                            newMayBeSet &= defaultMask;

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
                            IntegerStamp limit = create(bits, newLowerBound, newUpperBound);
                            newMayBeSet &= limit.mayBeSet();
                            newUpperBound = CodeUtil.signExtend(newUpperBound & newMayBeSet, bits);
                            newMustBeSet |= limit.mustBeSet();
                            newLowerBound |= newMustBeSet;
                            return IntegerStamp.create(bits, newLowerBound, newUpperBound, newMustBeSet, newMayBeSet);
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 0;
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Sub(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() - b.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp a, Stamp b) {
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

                    new ArithmeticOpTable.BinaryOp.Mul(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() * b.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;

                            int bits = a.getBits();
                            assert bits == b.getBits() : Assertions.errorMessage(a, b);

                            if (a.lowerBound == a.upperBound && b.lowerBound == b.upperBound) {
                                long value = CodeUtil.convert(a.lowerBound() * b.lowerBound(), a.getBits(), false);
                                return createConstant(a.getBits(), value);
                            }

                            // if a==0 or b==0 result of a*b is always 0
                            if (a.mayBeSet() == 0) {
                                return a;
                            } else if (b.mayBeSet() == 0) {
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
                                long newMayBeSet = ~CodeUtil.mask(Math.min(64, Long.numberOfTrailingZeros(a.mayBeSet) + Long.numberOfTrailingZeros(b.mayBeSet))) & CodeUtil.mask(bits);

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

                                assert newLowerBound <= newUpperBound : Assertions.errorMessageContext("newLowerBound", newLowerBound, "newUpperBonud", newUpperBound, "stamp1", stamp1, "stamp2",
                                                stamp2);
                                return StampFactory.forIntegerWithMask(bits, newLowerBound, newUpperBound, 0, newMayBeSet);
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
                    new ArithmeticOpTable.BinaryOp.MulHigh(false, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            return JavaConstant.forIntegerKind(a.getJavaKind(), multiplyHigh(a.asLong(), b.asLong(), a.getJavaKind()));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            JavaKind javaKind = a.getStackKind();

                            assert a.getBits() == b.getBits() : "Bits must match " + Assertions.errorMessageContext("a", a, "b", b);

                            assert javaKind == b.getStackKind() : Assertions.errorMessageContext("stamp1", stamp1, "stamp2", stamp2);
                            assert (javaKind == JavaKind.Int || javaKind == JavaKind.Long) : javaKind;

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
                                assert javaKind == JavaKind.Long : javaKind;
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
                    new ArithmeticOpTable.BinaryOp.UMulHigh(false, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            return JavaConstant.forIntegerKind(a.getJavaKind(), multiplyHighUnsigned(a.asLong(), b.asLong(), a.getJavaKind()));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            JavaKind javaKind = a.getStackKind();

                            assert a.getBits() == b.getBits() : "Bits must match " + Assertions.errorMessageContext("a", a, "b", b);

                            assert javaKind == b.getStackKind() : Assertions.errorMessageContext("stamp1", stamp1, "stamp2", stamp2);
                            assert (javaKind == JavaKind.Int || javaKind == JavaKind.Long) : javaKind;

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
                                assert javaKind == JavaKind.Long : javaKind;
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

                    new ArithmeticOpTable.BinaryOp.Div(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            if (b.asLong() == 0) {
                                return null;
                            }
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() / b.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits() : "Bits must match " + Assertions.errorMessageContext("a", a, "b", b);

                            if (a.lowerBound == a.upperBound && b.lowerBound == b.upperBound && b.lowerBound != 0) {
                                long value = CodeUtil.convert(a.lowerBound() / b.lowerBound(), a.getBits(), false);
                                return createConstant(a.getBits(), value);
                            } else if (b.isStrictlyPositive()) {
                                long newLowerBound = a.lowerBound() < 0 ? a.lowerBound() / b.lowerBound() : a.lowerBound() / b.upperBound();
                                long newUpperBound = a.upperBound() < 0 ? a.upperBound() / b.upperBound() : a.upperBound() / b.lowerBound();
                                return create(a.getBits(), newLowerBound, newUpperBound);
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

                    new ArithmeticOpTable.BinaryOp.Rem(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            if (b.asLong() == 0) {
                                return null;
                            }
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() % b.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits() : "Bits must match " + Assertions.errorMessageContext("a", a, "b", b);

                            if (a.lowerBound == a.upperBound && b.lowerBound == b.upperBound && b.lowerBound != 0) {
                                long value = CodeUtil.convert(a.lowerBound() % b.lowerBound(), a.getBits(), false);
                                return createConstant(a.getBits(), value);
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

                            if (newLowerBound > newUpperBound) {
                                // Don't return the empty stamp
                                return stamp1.unrestricted();
                            }

                            return create(a.getBits(), newLowerBound, newUpperBound);
                        }
                    },

                    new UnaryOp.Not() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forIntegerKind(value.getJavaKind(), ~value.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return stamp;
                            }
                            IntegerStamp integerStamp = (IntegerStamp) stamp;
                            int bits = integerStamp.getBits();
                            long defaultMask = CodeUtil.mask(bits);
                            long lowerBoundInput = ~integerStamp.upperBound();
                            long upperBoundInput = ~integerStamp.lowerBound();
                            long mustBeSet1 = (~integerStamp.mayBeSet()) & defaultMask;
                            long mayBeSet1 = (~integerStamp.mustBeSet()) & defaultMask;
                            return IntegerStamp.create(bits, lowerBoundInput, upperBoundInput, mustBeSet1, mayBeSet1);
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.And(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() & b.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits() : "Bits must match " + Assertions.errorMessageContext("a", a, "b", b);

                            return stampForMask(a.getBits(), a.mustBeSet() & b.mustBeSet(), a.mayBeSet() & b.mayBeSet());
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            int bits = n.getJavaKind().getBitCount();
                            long mask = CodeUtil.mask(bits);
                            return (n.asLong() & mask) == mask;
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Or(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() | b.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits() : "Bits must match " + Assertions.errorMessageContext("a", a, "b", b);

                            return stampForMask(a.getBits(), a.mustBeSet() | b.mustBeSet(), a.mayBeSet() | b.mayBeSet());
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 0;
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Xor(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind() : "Kind must match " + Assertions.errorMessageContext("a", a, "b", b);
                            return JavaConstant.forIntegerKind(a.getJavaKind(), a.asLong() ^ b.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp stamp1, Stamp stamp2) {
                            if (stamp1.isEmpty()) {
                                return stamp1;
                            }
                            if (stamp2.isEmpty()) {
                                return stamp2;
                            }
                            IntegerStamp a = (IntegerStamp) stamp1;
                            IntegerStamp b = (IntegerStamp) stamp2;
                            assert a.getBits() == b.getBits() : "Bits must match " + Assertions.errorMessageContext("a", a, "b", b);

                            long variableBits = (a.mustBeSet() ^ a.mayBeSet()) | (b.mustBeSet() ^ b.mayBeSet());
                            long newMustBeSet = (a.mustBeSet() ^ b.mustBeSet()) & ~variableBits;
                            long newMayBeSet = (a.mustBeSet() ^ b.mustBeSet()) | variableBits;
                            return stampForMask(a.getBits(), newMustBeSet, newMayBeSet);
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

                    new ArithmeticOpTable.ShiftOp.Shl() {

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
                                    throw GraalError.shouldNotReachHereUnexpectedValue(c.getJavaKind()); // ExcludeFromJacocoGeneratedReport
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
                                return createEmptyStamp(bits);
                            } else if (value.mayBeSet() == 0) {
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
                                    IntegerStamp result = IntegerStamp.create(bits, value.lowerBound() << shiftAmount, value.upperBound() << shiftAmount,
                                                    (value.mustBeSet() << shiftAmount) & CodeUtil.mask(bits), (value.mayBeSet() << shiftAmount) & CodeUtil.mask(bits));
                                    return result;
                                }
                            }
                            if ((shift.lowerBound() >>> shiftBits) == (shift.upperBound() >>> shiftBits)) {
                                long defaultMask = CodeUtil.mask(bits);
                                long mustBeSet = defaultMask;
                                long mayBeSet = 0;
                                for (long i = shift.lowerBound(); i <= shift.upperBound(); i++) {
                                    if (shift.contains(i)) {
                                        mustBeSet &= value.mustBeSet() << (i & shiftMask);
                                        mayBeSet |= value.mayBeSet() << (i & shiftMask);
                                    }
                                }
                                return IntegerStamp.stampForMask(bits, mustBeSet, mayBeSet & defaultMask);
                            }
                            return value.unrestricted();
                        }

                        @Override
                        public int getShiftAmountMask(Stamp s) {
                            return s.getStackKind().getBitCount() - 1;
                        }
                    },

                    new ArithmeticOpTable.ShiftOp.Shr() {

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
                                    throw GraalError.shouldNotReachHereUnexpectedValue(c.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp, IntegerStamp shift) {
                            IntegerStamp value = (IntegerStamp) stamp;
                            int bits = value.getBits();
                            if (value.isEmpty()) {
                                return value;
                            } else if (shift.isEmpty()) {
                                return createEmptyStamp(bits);
                            } else if (shift.lowerBound() == shift.upperBound()) {
                                long shiftCount = shift.lowerBound() & getShiftAmountMask(stamp);
                                if (shiftCount == 0) {
                                    return stamp;
                                }

                                int extraBits = 64 - bits;
                                long defaultMask = CodeUtil.mask(bits);
                                // shifting back and forth performs sign extension
                                long mustBeSet = (value.mustBeSet() << extraBits) >> (shiftCount + extraBits) & defaultMask;
                                long mayBeSet = (value.mayBeSet() << extraBits) >> (shiftCount + extraBits) & defaultMask;
                                return IntegerStamp.create(bits, value.lowerBound() >> shiftCount, value.upperBound() >> shiftCount, mustBeSet, mayBeSet);
                            }
                            long mask = IntegerStamp.mayBeSetFor(bits, value.lowerBound(), value.upperBound());
                            return IntegerStamp.stampForMask(bits, 0, mask);
                        }

                        @Override
                        public int getShiftAmountMask(Stamp s) {
                            return s.getStackKind().getBitCount() - 1;
                        }
                    },

                    new ArithmeticOpTable.ShiftOp.UShr() {

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
                                    throw GraalError.shouldNotReachHereUnexpectedValue(c.getJavaKind()); // ExcludeFromJacocoGeneratedReport
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp, IntegerStamp shift) {
                            IntegerStamp value = (IntegerStamp) stamp;
                            int bits = value.getBits();
                            if (value.isEmpty()) {
                                return value;
                            } else if (shift.isEmpty()) {
                                return createEmptyStamp(bits);
                            }

                            if (shift.lowerBound() == shift.upperBound()) {
                                long shiftCount = shift.lowerBound() & getShiftAmountMask(stamp);
                                if (shiftCount == 0) {
                                    return stamp;
                                }

                                long mustBeSet = value.mustBeSet() >>> shiftCount;
                                long mayBeSet = value.mayBeSet() >>> shiftCount;
                                if (value.lowerBound() < 0) {
                                    return IntegerStamp.create(bits, mustBeSet, mayBeSet, mustBeSet, mayBeSet);
                                } else {
                                    return IntegerStamp.create(bits, value.lowerBound() >>> shiftCount, value.upperBound() >>> shiftCount, mustBeSet, mayBeSet);
                                }
                            }
                            long mask = IntegerStamp.mayBeSetFor(bits, value.lowerBound(), value.upperBound());
                            return IntegerStamp.stampForMask(bits, 0, mask);
                        }

                        @Override
                        public int getShiftAmountMask(Stamp s) {
                            return s.getStackKind().getBitCount() - 1;
                        }
                    },

                    new ArithmeticOpTable.UnaryOp.Abs() {

                        @Override
                        public Constant foldConstant(Constant value) {
                            PrimitiveConstant c = (PrimitiveConstant) value;
                            return JavaConstant.forIntegerKind(c.getJavaKind(), Math.abs(c.asLong()));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp input) {
                            if (input.isEmpty()) {
                                return input;
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            int bits = stamp.getBits();
                            if (stamp.lowerBound == stamp.upperBound) {
                                long value = CodeUtil.convert(Math.abs(stamp.lowerBound()), stamp.getBits(), false);
                                return createConstant(stamp.getBits(), value);
                            }
                            if (stamp.lowerBound() == CodeUtil.minValue(bits)) {
                                return input.unrestricted();
                            } else {
                                long limit = Math.max(-stamp.lowerBound(), stamp.upperBound());
                                return create(bits, 0, limit);
                            }
                        }
                    },

                    null,

                    new ArithmeticOpTable.IntegerConvertOp.ZeroExtend() {

                        @Override
                        public Constant foldConstant(int inputBits, int resultBits, Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forPrimitiveInt(resultBits, CodeUtil.zeroExtend(value.asLong(), inputBits));
                        }

                        @Override
                        public Stamp foldStamp(int inputBits, int resultBits, Stamp input) {
                            if (input.isEmpty()) {
                                return createEmptyStamp(resultBits);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert inputBits == stamp.getBits() : "Input bits" + inputBits + " stamp bits " +
                                            stamp.getBits() + " result bits " + resultBits;
                            assert inputBits <= resultBits : inputBits + ">=" + resultBits;

                            if (inputBits == resultBits) {
                                return input;
                            }

                            if (input.isEmpty()) {
                                return createEmptyStamp(resultBits);
                            }

                            long mustBeSet = CodeUtil.zeroExtend(stamp.mustBeSet(), inputBits);
                            long mayBeSet = CodeUtil.zeroExtend(stamp.mayBeSet(), inputBits);
                            long lowerBound = stamp.unsignedLowerBound();
                            long upperBound = stamp.unsignedUpperBound();
                            return IntegerStamp.create(resultBits, lowerBound, upperBound, mustBeSet, mayBeSet);
                        }

                        @Override
                        public Stamp invertStamp(int inputBits, int resultBits, Stamp outStamp) {
                            IntegerStamp stamp = (IntegerStamp) outStamp;
                            if (stamp.isEmpty()) {
                                return createEmptyStamp(inputBits);
                            }

                            /*
                             * There is no guarantee that a given result is in the range of the
                             * input because of holes in ranges resulting from signed / unsigned
                             * extension, so we must ensure that the extension bits are all zeros
                             * otherwise we cannot represent the result, and we have to return an
                             * empty stamp.
                             *
                             * This case is much less likely to happen than the case for SignExtend
                             * but the following is defensive to ensure that we only perform valid
                             * inversions.
                             */
                            long mustBeSetOutputBits = stamp.mustBeSet();
                            long mustBeSetExtensionBits = mustBeSetOutputBits >>> inputBits;
                            if (mustBeSetExtensionBits != 0) {
                                return createEmptyStamp(inputBits);
                            }

                            /*
                             * The output of a zero extend cannot be negative. Setting the lower
                             * bound > 0 enables inverting stamps like [-8, 16] without having to
                             * return an unrestricted stamp.
                             */
                            long lowerBound = Math.max(stamp.lowerBound(), 0);
                            assert stamp.upperBound() >= 0 : "Cannot invert ZeroExtend for stamp with msb=1, which implies a negative value after ZeroExtend!";

                            return StampFactory.forUnsignedInteger(inputBits, lowerBound, stamp.upperBound(), stamp.mustBeSet(), stamp.mayBeSet());
                        }
                    },

                    new ArithmeticOpTable.IntegerConvertOp.SignExtend() {

                        @Override
                        public Constant foldConstant(int inputBits, int resultBits, Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forPrimitiveInt(resultBits, CodeUtil.signExtend(value.asLong(), inputBits));
                        }

                        @Override
                        public Stamp foldStamp(int inputBits, int resultBits, Stamp input) {
                            if (input.isEmpty()) {
                                return createEmptyStamp(resultBits);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert inputBits == stamp.getBits() : Assertions.errorMessageContext("inputBits", inputBits, "stamp", stamp);
                            assert inputBits <= resultBits : inputBits + ">=" + resultBits;

                            long defaultMask = CodeUtil.mask(resultBits);
                            long mustBeSet = CodeUtil.signExtend(stamp.mustBeSet(), inputBits) & defaultMask;
                            long mayBeSet = CodeUtil.signExtend(stamp.mayBeSet(), inputBits) & defaultMask;

                            return IntegerStamp.create(resultBits, stamp.lowerBound(), stamp.upperBound(), mustBeSet, mayBeSet);
                        }

                        @Override
                        public Stamp invertStamp(int inputBits, int resultBits, Stamp outStamp) {
                            IntegerStamp stamp = (IntegerStamp) outStamp;
                            if (stamp.isEmpty()) {
                                return createEmptyStamp(inputBits);
                            }

                            /*
                             * There is no guarantee that a given result bit is in the range of the
                             * input because of holes in ranges resulting from signed / unsigned
                             * extension, so we must ensure that the extension bits are either all
                             * zeros or all ones otherwise we cannot represent the result, and we
                             * have to return an empty stamp.
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
                             * the only sensible inversion is the empty stamp.
                             */
                            long mustBeSetExtensionBits = stamp.mustBeSet() >>> inputBits;
                            long mayBeSetExtensionBits = stamp.mayBeSet() >>> inputBits;
                            long extensionMask = CodeUtil.mask(stamp.getBits()) >>> inputBits;

                            boolean zeroInExtension = mayBeSetExtensionBits != extensionMask;
                            boolean oneInExtension = mustBeSetExtensionBits != 0;
                            boolean inputMSBOne = significantBit(inputBits, stamp.mustBeSet()) == 1;
                            boolean inputMSBZero = significantBit(inputBits, stamp.mayBeSet()) == 0;

                            /*
                             * Checks for contradictions in the extension and returns an empty stamp in such cases.
                             * Examples for contradictions for a stamp after an artificial 4->8 bit sign extension:
                             *
                             * @formatter:off
                             *
                             * 1) 01xx xxxx --> extension cannot contain zeroes and ones
                             * 2) x0xx 1xxx --> extension cannot contain a zero if the MSB of the extended value is 1
                             * 3) xx1x 0xxx --> extension cannot contain a one if the MSB of the extended value is 0
                             *
                             * @formatter:on
                             */
                            if ((zeroInExtension && oneInExtension) || (inputMSBOne && zeroInExtension) || (inputMSBZero && oneInExtension)) {
                                return createEmptyStamp(inputBits);
                            }

                            long inputMask = CodeUtil.mask(inputBits);
                            long inputMustBeSet = stamp.mustBeSet() & inputMask;
                            long inputMayBeSet = stamp.mayBeSet() & inputMask;

                            if (!inputMSBOne && !inputMSBZero) {
                                /*
                                 * Input MSB yet unknown, try to infer it from the extension:
                                 *
                                 * @formatter:off
                                 *
                                 * xx0x xxxx implies that the extension is 0000 which implies that the MSB of the input is 0
                                 * x1xx xxxx implies that the extension is 1111 which implies that the MSB of the input is 1
                                 *
                                 * @formatter:on
                                 */
                                if (zeroInExtension) {
                                    long msbZeroMask = ~(1 << (inputBits - 1));
                                    inputMustBeSet &= msbZeroMask;
                                    inputMayBeSet &= msbZeroMask;
                                } else if (oneInExtension) {
                                    long msbOneMask = 1 << (inputBits - 1);
                                    inputMustBeSet |= msbOneMask;
                                    inputMayBeSet |= msbOneMask;
                                }
                            }

                            // Calculate conservative bounds for the input.
                            long inputUpperBound = maxValueForMasks(inputBits, inputMustBeSet, inputMayBeSet);
                            long inputLowerBound = minValueForMasks(inputBits, inputMustBeSet, inputMayBeSet);

                            /*
                             * If the bounds calculated for the input stamp do not overlap with the
                             * bounds for the stamp to invert, return an empty stamp. Otherwise,
                             * refine the conservative stamp for the input.
                             */
                            if ((stamp.upperBound() < inputLowerBound) || (stamp.lowerBound() > inputUpperBound)) {
                                return createEmptyStamp(inputBits);
                            }
                            inputUpperBound = Math.min(inputUpperBound, stamp.upperBound());
                            inputLowerBound = Math.max(inputLowerBound, stamp.lowerBound());

                            return StampFactory.forIntegerWithMask(inputBits, inputLowerBound, inputUpperBound, inputMustBeSet, inputMayBeSet);
                        }
                    },

                    new ArithmeticOpTable.IntegerConvertOp.Narrow() {

                        @Override
                        public Constant foldConstant(int inputBits, int resultBits, Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forPrimitiveInt(resultBits, CodeUtil.narrow(value.asLong(), resultBits));
                        }

                        @Override
                        public Stamp foldStamp(int inputBits, int resultBits, Stamp input) {
                            if (input.isEmpty()) {
                                return createEmptyStamp(resultBits);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert inputBits == stamp.getBits() : Assertions.errorMessageContext("inputBits", inputBits, "stamp", stamp);
                            assert resultBits <= inputBits : resultBits + ">=" + inputBits;
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
                            long newMustBeSet = stamp.mustBeSet() & defaultMask;
                            long newMayBeSet = stamp.mayBeSet() & defaultMask;
                            long newLowerBound = CodeUtil.signExtend((lowerBound | newMustBeSet) & newMayBeSet, resultBits);
                            long newUpperBound = CodeUtil.signExtend((upperBound | newMustBeSet) & newMayBeSet, resultBits);

                            IntegerStamp result = IntegerStamp.create(resultBits, newLowerBound, newUpperBound, newMustBeSet, newMayBeSet);
                            assert result.hasValues();
                            return result;
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Max(true, true) {

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant x = (PrimitiveConstant) a;
                            PrimitiveConstant y = (PrimitiveConstant) b;
                            return JavaConstant.forIntegerKind(x.getJavaKind(), Math.max(x.asLong(), y.asLong()));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }
                            if (b.isEmpty()) {
                                return b;
                            }
                            IntegerStamp x = (IntegerStamp) a;
                            IntegerStamp y = (IntegerStamp) b;
                            long lowerBound = Math.max(x.lowerBound(), y.lowerBound());
                            long upperBound = Math.max(x.upperBound(), y.upperBound());
                            long mustBeSet = x.mustBeSet() & y.mustBeSet();
                            long mayBeSet = x.mayBeSet() | y.mayBeSet();
                            return create(x.getBits(), lowerBound, upperBound, mustBeSet, mayBeSet);
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            int bits = n.getJavaKind().getBitCount();
                            return n.asLong() == NumUtil.minValue(bits);
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.Min(true, true) {

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant x = (PrimitiveConstant) a;
                            PrimitiveConstant y = (PrimitiveConstant) b;
                            return JavaConstant.forIntegerKind(x.getJavaKind(), Math.min(x.asLong(), y.asLong()));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }
                            if (b.isEmpty()) {
                                return b;
                            }
                            IntegerStamp x = (IntegerStamp) a;
                            IntegerStamp y = (IntegerStamp) b;
                            long lowerBound = Math.min(x.lowerBound(), y.lowerBound());
                            long upperBound = Math.min(x.upperBound(), y.upperBound());
                            long mustBeSet = x.mustBeSet() & y.mustBeSet();
                            long mayBeSet = x.mayBeSet() | y.mayBeSet();
                            return create(x.getBits(), lowerBound, upperBound, mustBeSet, mayBeSet);
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            int bits = n.getJavaKind().getBitCount();
                            return n.asLong() == NumUtil.maxValue(bits);
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.UMax(true, true) {

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant x = (PrimitiveConstant) a;
                            PrimitiveConstant y = (PrimitiveConstant) b;
                            return JavaConstant.forIntegerKind(x.getJavaKind(), NumUtil.maxUnsigned(x.asLong(), y.asLong()));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }
                            if (b.isEmpty()) {
                                return b;
                            }
                            IntegerStamp x = (IntegerStamp) a;
                            IntegerStamp y = (IntegerStamp) b;
                            if (x.sameSignBounds() && y.sameSignBounds()) {
                                long lowerBound = NumUtil.maxUnsigned(x.unsignedLowerBound(), y.unsignedLowerBound());
                                long upperBound = NumUtil.maxUnsigned(x.unsignedUpperBound(), y.unsignedUpperBound());
                                long mustBeSet = x.mustBeSet() & y.mustBeSet();
                                long mayBeSet = x.mayBeSet() | y.mayBeSet();
                                return StampFactory.forUnsignedInteger(x.getBits(), lowerBound, upperBound, mustBeSet, mayBeSet);
                            } else {
                                return x.meet(y);
                            }
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            return n.asLong() == 0;
                        }
                    },

                    new ArithmeticOpTable.BinaryOp.UMin(true, true) {

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant x = (PrimitiveConstant) a;
                            PrimitiveConstant y = (PrimitiveConstant) b;
                            return JavaConstant.forIntegerKind(x.getJavaKind(), NumUtil.minUnsigned(x.asLong(), y.asLong()));
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }
                            if (b.isEmpty()) {
                                return b;
                            }
                            IntegerStamp x = (IntegerStamp) a;
                            IntegerStamp y = (IntegerStamp) b;
                            if (x.sameSignBounds() && y.sameSignBounds()) {
                                long lowerBound = NumUtil.minUnsigned(x.unsignedLowerBound(), y.unsignedLowerBound());
                                long upperBound = NumUtil.minUnsigned(x.unsignedUpperBound(), y.unsignedUpperBound());
                                long mustBeSet = x.mustBeSet() & y.mustBeSet();
                                long mayBeSet = x.mayBeSet() | y.mayBeSet();
                                return StampFactory.forUnsignedInteger(x.getBits(), lowerBound, upperBound, mustBeSet, mayBeSet);
                            } else {
                                return x.meet(y);
                            }
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            int bits = n.getJavaKind().getBitCount();
                            return CodeUtil.zeroExtend(n.asLong(), bits) == NumUtil.maxValueUnsigned(bits);
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
                            } else if (resultStamp instanceof FloatStamp && input instanceof IntegerStamp) {
                                return ReinterpretUtils.intToFloat((IntegerStamp) input);
                            } else {
                                return resultStamp;
                            }
                        }
                    },

                    new BinaryOp.Compress(false, false) {

                        private static final long INT_MASK = CodeUtil.mask(32);
                        private static final long LONG_MASK = CodeUtil.mask(64);

                        private static int integerCompress(int i, int mask) {
                            try {
                                Method compress = Integer.class.getDeclaredMethod("compress", int.class, int.class);
                                return (Integer) compress.invoke(null, i, mask);
                            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                                throw GraalError.shouldNotReachHere(e, "Integer.compress is introduced in Java 19");
                            }
                        }

                        private static long longCompress(long i, long mask) {
                            try {
                                Method compress = Long.class.getDeclaredMethod("compress", long.class, long.class);
                                return (Long) compress.invoke(null, i, mask);
                            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                                throw GraalError.shouldNotReachHere(e, "Long.compress is introduced in Java 19");
                            }
                        }

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant i = (PrimitiveConstant) a;
                            PrimitiveConstant mask = (PrimitiveConstant) b;

                            if (i.getJavaKind() == JavaKind.Int) {
                                return JavaConstant.forInt(integerCompress(i.asInt(), mask.asInt()));
                            } else {
                                GraalError.guarantee(i.getJavaKind() == JavaKind.Long, "unexpected Java kind %s", i.getJavaKind());
                                return JavaConstant.forLong(longCompress(i.asLong(), mask.asLong()));
                            }
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp a, Stamp b) {
                            IntegerStamp valueStamp = (IntegerStamp) a;
                            IntegerStamp maskStamp = (IntegerStamp) b;

                            if (valueStamp.getStackKind() == JavaKind.Int) {
                                if (maskStamp.mayBeSet() == INT_MASK && valueStamp.canBeNegative()) {
                                    // compress result can be negative
                                    return IntegerStamp.create(32, valueStamp.lowerBound(), CodeUtil.maxValue(32));
                                }
                                // compress result will always be positive
                                return IntegerStamp.create(32,
                                                integerCompress((int) valueStamp.mustBeSet(), (int) maskStamp.mustBeSet()) & INT_MASK,
                                                integerCompress((int) valueStamp.mayBeSet(), (int) maskStamp.mayBeSet()) & INT_MASK,
                                                0,
                                                integerCompress((int) INT_MASK, (int) maskStamp.mayBeSet()) & INT_MASK);
                            } else {
                                GraalError.guarantee(valueStamp.getStackKind() == JavaKind.Long, "unexpected Java kind %s", valueStamp.getStackKind());
                                if (maskStamp.mayBeSet() == LONG_MASK && valueStamp.canBeNegative()) {
                                    // compress result can be negative
                                    return IntegerStamp.create(64, valueStamp.lowerBound(), CodeUtil.maxValue(64));
                                }
                                // compress result will always be positive
                                return IntegerStamp.create(64,
                                                longCompress(valueStamp.mustBeSet(), maskStamp.mustBeSet()),
                                                longCompress(valueStamp.mayBeSet(), maskStamp.mayBeSet()),
                                                0,
                                                longCompress(LONG_MASK, maskStamp.mayBeSet()));
                            }
                        }
                    },

                    new BinaryOp.Expand(false, false) {

                        private static int integerExpand(int i, int mask) {
                            try {
                                Method expand = Integer.class.getDeclaredMethod("expand", int.class, int.class);
                                return (Integer) expand.invoke(null, i, mask);
                            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                                throw GraalError.shouldNotReachHere(e, "Integer.expand is introduced in Java 19");
                            }
                        }

                        private static long longExpand(long i, long mask) {
                            try {
                                Method expand = Long.class.getDeclaredMethod("expand", long.class, long.class);
                                return (Long) expand.invoke(null, i, mask);
                            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                                throw GraalError.shouldNotReachHere(e, "Long.expand is introduced in Java 19");
                            }
                        }

                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            PrimitiveConstant i = (PrimitiveConstant) a;
                            PrimitiveConstant mask = (PrimitiveConstant) b;

                            if (i.getJavaKind() == JavaKind.Int) {
                                return JavaConstant.forInt(integerExpand(i.asInt(), mask.asInt()));
                            } else {
                                GraalError.guarantee(i.getJavaKind() == JavaKind.Long, "unexpected Java kind %s", i.getJavaKind());
                                return JavaConstant.forLong(longExpand(i.asLong(), mask.asLong()));
                            }
                        }

                        @Override
                        public Stamp foldStampImpl(Stamp a, Stamp b) {
                            IntegerStamp maskStamp = (IntegerStamp) b;
                            return IntegerStamp.stampForMask(maskStamp.getBits(), 0, maskStamp.mayBeSet());
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(I2F) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forFloat(value.asInt());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.empty(JavaKind.Float);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert stamp.getBits() == 32 : stamp;
                            float lowerBound = stamp.lowerBound();
                            float upperBound = stamp.upperBound();
                            return StampFactory.forFloat(JavaKind.Float, lowerBound, upperBound, true);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(L2F) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forFloat(value.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.empty(JavaKind.Float);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert stamp.getBits() == 64 : stamp;
                            float lowerBound = stamp.lowerBound();
                            float upperBound = stamp.upperBound();
                            return StampFactory.forFloat(JavaKind.Float, lowerBound, upperBound, true);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(I2D) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forDouble(value.asInt());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.empty(JavaKind.Double);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert stamp.getBits() == 32 : stamp;
                            double lowerBound = stamp.lowerBound();
                            double upperBound = stamp.upperBound();
                            return StampFactory.forFloat(JavaKind.Double, lowerBound, upperBound, true);
                        }
                    },

                    new ArithmeticOpTable.FloatConvertOp(L2D) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forDouble(value.asLong());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp input) {
                            if (input.isEmpty()) {
                                return StampFactory.empty(JavaKind.Double);
                            }
                            IntegerStamp stamp = (IntegerStamp) input;
                            assert stamp.getBits() == 64 : stamp;
                            double lowerBound = stamp.lowerBound();
                            double upperBound = stamp.upperBound();
                            return StampFactory.forFloat(JavaKind.Double, lowerBound, upperBound, true);
                        }
                    });

    static final IntegerStamp[] emptyStamps = new IntegerStamp[CodeUtil.log2(64) + 1];
    static final IntegerStamp[] unrestrictedStamps = new IntegerStamp[CodeUtil.log2(64) + 1];

    static {
        for (int logBits = 0; logBits < emptyStamps.length; logBits++) {
            emptyStamps[logBits] = new IntegerStamp(1 << logBits, true);
            unrestrictedStamps[logBits] = new IntegerStamp(1 << logBits, false);
        }
    }
}
