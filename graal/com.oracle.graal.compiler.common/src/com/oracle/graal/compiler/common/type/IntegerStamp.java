/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.common.type;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.spi.*;

/**
 * Describes the possible values of a {@link ValueNode} that produces an int or long result.
 *
 * The description consists of (inclusive) lower and upper bounds and up (may be set) and down
 * (always set) bit-masks.
 */
@SuppressWarnings("javadoc")
public class IntegerStamp extends PrimitiveStamp {

    private final long lowerBound;
    private final long upperBound;
    private final long downMask;
    private final long upMask;

    public IntegerStamp(int bits, long lowerBound, long upperBound, long downMask, long upMask) {
        super(bits);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.downMask = downMask;
        this.upMask = upMask;
        assert lowerBound >= defaultMinValue(bits) : this;
        assert upperBound <= defaultMaxValue(bits) : this;
        assert (downMask & defaultMask(bits)) == downMask : this;
        assert (upMask & defaultMask(bits)) == upMask : this;
    }

    @Override
    public Stamp unrestricted() {
        return new IntegerStamp(getBits(), defaultMinValue(getBits()), defaultMaxValue(getBits()), 0, defaultMask(getBits()));
    }

    @Override
    public Stamp illegal() {
        return new IntegerStamp(getBits(), defaultMaxValue(getBits()), defaultMinValue(getBits()), defaultMask(getBits()), 0);
    }

    @Override
    public boolean isLegal() {
        return lowerBound <= upperBound;
    }

    @Override
    public Kind getStackKind() {
        if (getBits() > 32) {
            return Kind.Long;
        } else {
            return Kind.Int;
        }
    }

    @Override
    public PlatformKind getPlatformKind(LIRTypeTool tool) {
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
                throw GraalInternalError.shouldNotReachHere();
        }
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

    public boolean isUnrestricted() {
        return lowerBound == defaultMinValue(getBits()) && upperBound == defaultMaxValue(getBits()) && downMask == 0 && upMask == defaultMask(getBits());
    }

    public boolean contains(long value) {
        return value >= lowerBound && value <= upperBound && (value & downMask) == downMask && (value & upMask) == (value & defaultMask(getBits()));
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
        if (lowerBound == upperBound) {
            str.append(" [").append(lowerBound).append(']');
        } else if (lowerBound != defaultMinValue(getBits()) || upperBound != defaultMaxValue(getBits())) {
            str.append(" [").append(lowerBound).append(" - ").append(upperBound).append(']');
        }
        if (downMask != 0) {
            str.append(" \u21ca");
            new Formatter(str).format("%016x", downMask);
        }
        if (upMask != defaultMask(getBits())) {
            str.append(" \u21c8");
            new Formatter(str).format("%016x", upMask);
        }
        return str.toString();
    }

    private Stamp createStamp(IntegerStamp other, long newUpperBound, long newLowerBound, long newDownMask, long newUpMask) {
        assert getBits() == other.getBits();
        if (newLowerBound > newUpperBound || (newDownMask & (~newUpMask)) != 0) {
            return illegal();
        } else if (newLowerBound == lowerBound && newUpperBound == upperBound && newDownMask == downMask && newUpMask == upMask) {
            return this;
        } else if (newLowerBound == other.lowerBound && newUpperBound == other.upperBound && newDownMask == other.downMask && newUpMask == other.upMask) {
            return other;
        } else {
            return new IntegerStamp(getBits(), newLowerBound, newUpperBound, newDownMask, newUpMask);
        }
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        if (!(otherStamp instanceof IntegerStamp)) {
            return StampFactory.illegal(Kind.Illegal);
        }
        IntegerStamp other = (IntegerStamp) otherStamp;
        return createStamp(other, Math.max(upperBound, other.upperBound), Math.min(lowerBound, other.lowerBound), downMask & other.downMask, upMask | other.upMask);
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        if (!(otherStamp instanceof IntegerStamp)) {
            return StampFactory.illegal(Kind.Illegal);
        }
        IntegerStamp other = (IntegerStamp) otherStamp;
        long newDownMask = downMask | other.downMask;
        long newLowerBound = Math.max(lowerBound, other.lowerBound) | newDownMask;
        return createStamp(other, Math.min(upperBound, other.upperBound), newLowerBound, newDownMask, upMask & other.upMask);
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + super.hashCode();
        result = prime * result + (int) (lowerBound ^ (lowerBound >>> 32));
        result = prime * result + (int) (upperBound ^ (upperBound >>> 32));
        result = prime * result + (int) (downMask ^ (downMask >>> 32));
        result = prime * result + (int) (upMask ^ (upMask >>> 32));
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
        if (lowerBound != other.lowerBound || upperBound != other.upperBound || downMask != other.downMask || upMask != other.upMask) {
            return false;
        }
        return true;
    }

    public static long defaultMask(int bits) {
        assert 0 <= bits && bits <= 64;
        if (bits == 64) {
            return 0xffffffffffffffffL;
        } else {
            return (1L << bits) - 1;
        }
    }

    public static long defaultMinValue(int bits) {
        return -1L << (bits - 1);
    }

    public static long defaultMaxValue(int bits) {
        return defaultMask(bits - 1);
    }

    public static long upMaskFor(int bits, long lowerBound, long upperBound) {
        long mask = lowerBound | upperBound;
        if (mask == 0) {
            return 0;
        } else {
            return ((-1L) >>> Long.numberOfLeadingZeros(mask)) & defaultMask(bits);
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
    public Constant asConstant() {
        if (lowerBound == upperBound) {
            switch (getBits()) {
                case 1:
                    return Constant.forBoolean(lowerBound != 0);
                case 8:
                    return Constant.forByte((byte) lowerBound);
                case 16:
                    return Constant.forShort((short) lowerBound);
                case 32:
                    return Constant.forInt((int) lowerBound);
                case 64:
                    return Constant.forLong(lowerBound);
            }
        }
        return null;
    }
}
