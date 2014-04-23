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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.spi.*;

public class FloatStamp extends PrimitiveStamp {

    private final double lowerBound;
    private final double upperBound;
    private final boolean nonNaN;

    protected FloatStamp(int bits) {
        this(bits, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false);
    }

    public FloatStamp(int bits, double lowerBound, double upperBound, boolean nonNaN) {
        super(bits);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.nonNaN = nonNaN;
    }

    @Override
    public Stamp unrestricted() {
        return new FloatStamp(getBits());
    }

    @Override
    public Stamp illegal() {
        return new FloatStamp(getBits(), Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, true);
    }

    @Override
    public boolean isLegal() {
        return lowerBound <= upperBound || !nonNaN;
    }

    @Override
    public Kind getStackKind() {
        if (getBits() > 32) {
            return Kind.Double;
        } else {
            return Kind.Float;
        }
    }

    @Override
    public PlatformKind getPlatformKind(PlatformKindTool tool) {
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
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    /**
     * The (inclusive) lower bound on the value described by this stamp.
     */
    public double lowerBound() {
        return lowerBound;
    }

    /**
     * The (inclusive) upper bound on the value described by this stamp.
     */
    public double upperBound() {
        return upperBound;
    }

    public boolean isNonNaN() {
        return nonNaN;
    }

    public boolean isUnrestricted() {
        return lowerBound == Double.NEGATIVE_INFINITY && upperBound == Double.POSITIVE_INFINITY && !nonNaN;
    }

    public boolean contains(double value) {
        if (Double.isNaN(value)) {
            return !nonNaN;
        } else {
            return value >= lowerBound && value <= upperBound;
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append('f');
        str.append(getBits());
        str.append(nonNaN ? "!" : "");
        if (lowerBound == upperBound) {
            str.append(" [").append(lowerBound).append(']');
        } else if (lowerBound != Double.NEGATIVE_INFINITY || upperBound != Double.POSITIVE_INFINITY) {
            str.append(" [").append(lowerBound).append(" - ").append(upperBound).append(']');
        }
        return str.toString();
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        if (!(otherStamp instanceof FloatStamp)) {
            return StampFactory.illegal(Kind.Illegal);
        }
        FloatStamp other = (FloatStamp) otherStamp;
        assert getBits() == other.getBits();
        double meetUpperBound = Math.max(upperBound, other.upperBound);
        double meetLowerBound = Math.min(lowerBound, other.lowerBound);
        boolean meetNonNaN = nonNaN && other.nonNaN;
        if (meetLowerBound == lowerBound && meetUpperBound == upperBound && meetNonNaN == nonNaN) {
            return this;
        } else if (meetLowerBound == other.lowerBound && meetUpperBound == other.upperBound && meetNonNaN == other.nonNaN) {
            return other;
        } else {
            return new FloatStamp(getBits(), meetLowerBound, meetUpperBound, meetNonNaN);
        }
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        if (!(otherStamp instanceof FloatStamp)) {
            return StampFactory.illegal(Kind.Illegal);
        }
        FloatStamp other = (FloatStamp) otherStamp;
        assert getBits() == other.getBits();
        double joinUpperBound = Math.min(upperBound, other.upperBound);
        double joinLowerBound = Math.max(lowerBound, other.lowerBound);
        boolean joinNonNaN = nonNaN || other.nonNaN;
        if (joinLowerBound == lowerBound && joinUpperBound == upperBound && joinNonNaN == nonNaN) {
            return this;
        } else if (joinLowerBound == other.lowerBound && joinUpperBound == other.upperBound && joinNonNaN == other.nonNaN) {
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
        return true;
    }

    @Override
    public Constant asConstant() {
        if (nonNaN && lowerBound == upperBound) {
            switch (getBits()) {
                case 32:
                    return Constant.forFloat((float) lowerBound);
                case 64:
                    return Constant.forDouble(lowerBound);
            }
        }
        return null;
    }
}
