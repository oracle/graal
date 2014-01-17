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

import com.oracle.graal.api.meta.*;

public class FloatStamp extends Stamp {

    private final double lowerBound;
    private final double upperBound;
    private final boolean nonNaN;

    protected FloatStamp(Kind kind) {
        this(kind, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false);
        assert kind == Kind.Float || kind == Kind.Double;
    }

    protected FloatStamp(Kind kind, double lowerBound, double upperBound, boolean nonNaN) {
        super(kind);
        assert (!nonNaN && Double.isNaN(lowerBound) && Double.isNaN(upperBound)) || lowerBound <= upperBound;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.nonNaN = nonNaN;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(kind().toJavaClass());
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
        str.append(kind().getTypeChar());
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
        if (otherStamp instanceof IllegalStamp) {
            return otherStamp.meet(this);
        }
        if (!(otherStamp instanceof FloatStamp)) {
            return StampFactory.illegal(Kind.Illegal);
        }
        FloatStamp other = (FloatStamp) otherStamp;
        assert kind() == other.kind();
        double meetUpperBound = Math.max(upperBound, other.upperBound);
        double meetLowerBound = Math.min(lowerBound, other.lowerBound);
        boolean meetNonNaN = nonNaN && other.nonNaN;
        if (meetLowerBound == lowerBound && meetUpperBound == upperBound && meetNonNaN == nonNaN) {
            return this;
        } else if (meetLowerBound == other.lowerBound && meetUpperBound == other.upperBound && meetNonNaN == other.nonNaN) {
            return other;
        } else {
            return new FloatStamp(kind(), meetLowerBound, meetUpperBound, meetNonNaN);
        }
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        if (otherStamp instanceof IllegalStamp) {
            return otherStamp.join(this);
        }
        if (!(otherStamp instanceof FloatStamp)) {
            return StampFactory.illegal(Kind.Illegal);
        }
        FloatStamp other = (FloatStamp) otherStamp;
        assert kind() == other.kind();
        double joinUpperBound = Math.min(upperBound, other.upperBound);
        double joinLowerBound = Math.max(lowerBound, other.lowerBound);
        boolean joinNonNaN = nonNaN || other.nonNaN;
        if (joinLowerBound == lowerBound && joinUpperBound == upperBound && joinNonNaN == nonNaN) {
            return this;
        } else if (joinLowerBound == other.lowerBound && joinUpperBound == other.upperBound && joinNonNaN == other.nonNaN) {
            return other;
        } else if (joinLowerBound > joinUpperBound) {
            return StampFactory.illegal(kind());
        } else {
            return new FloatStamp(kind(), joinLowerBound, joinUpperBound, joinNonNaN);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        result = prime * result + kind().hashCode();
        temp = Double.doubleToLongBits(lowerBound);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (nonNaN ? 1231 : 1237);
        temp = Double.doubleToLongBits(upperBound);
        result = prime * result + (int) (temp ^ (temp >>> 32));
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
        FloatStamp other = (FloatStamp) obj;
        if (kind() != other.kind()) {
            return false;
        }
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
            switch (kind()) {
                case Float:
                    return Constant.forFloat((float) lowerBound);
                case Double:
                    return Constant.forDouble(lowerBound);
            }
        }
        return null;
    }
}
