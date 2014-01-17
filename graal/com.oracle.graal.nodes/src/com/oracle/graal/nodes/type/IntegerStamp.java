/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.type;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

/**
 * Describes the possible values of a {@link ValueNode} that produces an int or long result.
 * 
 * The description consists of (inclusive, signed) lower and upper bounds and up (may be set) and
 * down (always set) bit-masks.
 */
public class IntegerStamp extends Stamp {

    private final long lowerBound;
    private final long upperBound;
    private final long downMask;
    private final long upMask;

    public IntegerStamp(Kind kind) {
        this(kind.getStackKind(), kind.getMinValue(), kind.getMaxValue(), 0, defaultMask(isUnsignedKind(kind) ? kind : kind.getStackKind()));
    }

    public IntegerStamp(Kind kind, long lowerBound, long upperBound, long downMask, long upMask) {
        super(kind);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.downMask = downMask;
        this.upMask = upMask;
        assert lowerBound <= upperBound : this;
        assert lowerBound >= kind.getMinValue() : this;
        assert upperBound <= kind.getMaxValue() : this;
        assert (downMask & defaultMask(kind)) == downMask : this;
        assert (upMask & defaultMask(kind)) == upMask : this;
        assert (lowerBound & downMask) == downMask : this;
        assert (upperBound & downMask) == downMask : this;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(kind().toJavaClass());
    }

    /**
     * The (inclusive) lower bound on the value described by this stamp.
     */
    public long lowerBound() {
        return lowerBound;
    }

    /**
     * The (inclusive) upper bound on the value described by this stamp.
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
        return lowerBound == kind().getMinValue() && upperBound == kind().getMaxValue() && downMask == 0 && upMask == defaultMask(kind());
    }

    public boolean contains(long value) {
        return value >= lowerBound && value <= upperBound && (value & downMask) == downMask && (value & upMask) == (value & defaultMask(kind()));
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
        str.append(kind().getTypeChar());
        if (lowerBound == upperBound) {
            str.append(" [").append(lowerBound).append(']');
        } else if (lowerBound != kind().getMinValue() || upperBound != kind().getMaxValue()) {
            str.append(" [").append(lowerBound).append(" - ").append(upperBound).append(']');
        }
        if (downMask != 0) {
            str.append(" \u21ca");
            new Formatter(str).format("%016x", downMask);
        }
        if (upMask != defaultMask(kind())) {
            str.append(" \u21c8");
            new Formatter(str).format("%016x", upMask);
        }
        return str.toString();
    }

    private Stamp createStamp(IntegerStamp other, long newUpperBound, long newLowerBound, long newDownMask, long newUpMask) {
        assert kind() == other.kind();
        if (newLowerBound > newUpperBound || (newDownMask & (~newUpMask)) != 0) {
            return StampFactory.illegal(kind());
        } else if (newLowerBound == lowerBound && newUpperBound == upperBound && newDownMask == downMask && newUpMask == upMask) {
            return this;
        } else if (newLowerBound == other.lowerBound && newUpperBound == other.upperBound && newDownMask == other.downMask && newUpMask == other.upMask) {
            return other;
        } else {
            return new IntegerStamp(kind(), newLowerBound, newUpperBound, newDownMask, newUpMask);
        }
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        if (otherStamp instanceof IllegalStamp) {
            return otherStamp.meet(this);
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
        if (otherStamp instanceof IllegalStamp) {
            return otherStamp.join(this);
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + kind().hashCode();
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
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        IntegerStamp other = (IntegerStamp) obj;
        if (lowerBound != other.lowerBound || upperBound != other.upperBound || downMask != other.downMask || upMask != other.upMask || kind() != other.kind()) {
            return false;
        }
        return true;
    }

    public static long defaultMask(Kind kind) {
        switch (kind) {
            case Boolean:
                return 0x01L;
            case Byte:
                return 0xffL;
            case Char:
                return 0xffffL;
            case Short:
                return 0xffffL;
            case Int:
                return 0xffffffffL;
            case Long:
                return 0xffffffffffffffffL;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static long upMaskFor(Kind kind, long lowerBound, long upperBound) {
        long mask = lowerBound | upperBound;
        if (mask == 0) {
            return 0;
        } else {
            return ((-1L) >>> Long.numberOfLeadingZeros(mask)) & defaultMask(kind);
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
            return Constant.forIntegerKind(kind(), lowerBound, null);
        }
        return null;
    }

    private static boolean isUnsignedKind(Kind kind) {
        return kind == Kind.Char;
    }
}
