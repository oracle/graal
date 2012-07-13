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
import com.oracle.graal.nodes.*;

/**
 * Describes the possible values of a {@link ValueNode} that produces an int or long result.
 *
 * The description consists of (inclusive) lower and upper bounds and a bit-mask.
 */
public class IntegerStamp extends Stamp {

    private final long lowerBound;
    private final long upperBound;
    private final long mask;

    public IntegerStamp(Kind kind) {
        this(kind, kind.minValue(), kind.maxValue(), defaultMask(kind));
    }

    public IntegerStamp(Kind kind, long lowerBound, long upperBound, long mask) {
        super(kind);
        assert lowerBound <= upperBound;
        assert lowerBound >= kind.minValue();
        assert upperBound <= kind.maxValue();
        assert (mask & defaultMask(kind)) == mask;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.mask = mask;
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
     * This bit-mask describes the bits that can be set in the value described by this stamp. It is primarily used to
     * represent values that are multiples of a known power of two.
     */
    public long mask() {
        return mask;
    }

    public boolean isUnrestricted() {
        return lowerBound == kind().minValue() && upperBound == kind().maxValue() && mask == defaultMask(kind());
    }

    public boolean contains(long value) {
        return value >= lowerBound && value <= upperBound && (value & mask) == (value & defaultMask(kind()));
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(kind().typeChar);
        if (lowerBound == upperBound) {
            str.append(" [").append(lowerBound).append(']');
        } else if (lowerBound != kind().minValue() || upperBound != kind().maxValue()) {
            str.append(" [").append(lowerBound).append(" - ").append(upperBound).append(']');
        }
        if (mask != defaultMask(kind())) {
            str.append(" #").append(Long.toHexString(mask));
        }
        return str.toString();
    }

    @Override
    public boolean alwaysDistinct(Stamp otherStamp) {
        IntegerStamp other = (IntegerStamp) otherStamp;
        return lowerBound > other.upperBound || upperBound < other.lowerBound;
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (otherStamp == StampFactory.top()) {
            return this;
        }
        IntegerStamp other = (IntegerStamp) otherStamp;
        assert kind() == other.kind();
        long meetUpperBound = Math.max(upperBound, other.upperBound);
        long meetLowerBound = Math.min(lowerBound, other.lowerBound);
        long meetMask = mask | other.mask;
        if (meetLowerBound == lowerBound && meetUpperBound == upperBound && meetMask == mask) {
            return this;
        } else if (meetLowerBound == other.lowerBound && meetUpperBound == other.upperBound && meetMask == other.mask) {
            return other;
        } else {
            return new IntegerStamp(kind(), meetLowerBound, meetUpperBound, meetMask);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (lowerBound ^ (lowerBound >>> 32));
        result = prime * result + (int) (upperBound ^ (upperBound >>> 32));
        result = prime * result + (int) (mask ^ (mask >>> 32));
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
        if (lowerBound != other.lowerBound || upperBound != other.upperBound || mask != other.mask) {
            return false;
        }
        return true;
    }

    public static long defaultMask(Kind kind) {
        if (kind == Kind.Int) {
            return 0xFFFFFFFFL;
        } else {
            return 0xFFFFFFFFFFFFFFFFL;
        }
    }

    public static long maskFor(Kind kind, long lowerBound, long upperBound) {
        long mask = lowerBound | upperBound;
        if (mask == 0) {
            return 0;
        } else {
            return ((-1L) >>> Long.numberOfLeadingZeros(mask)) & defaultMask(kind);
        }
    }

}
