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


public class IntegerStamp extends Stamp {

    private final long lowerBound;
    private final long upperBound;

    public IntegerStamp(Kind kind) {
        this(kind, kind.minValue(), kind.maxValue());
    }

    public IntegerStamp(Kind kind, long lowerBound, long upperBound) {
        super(kind);
        assert lowerBound <= upperBound;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public long lowerBound() {
        return lowerBound;
    }

    public long upperBound() {
        return upperBound;
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
        return str.toString();
    }

    @Override
    public boolean alwaysDistinct(Stamp otherStamp) {
        IntegerStamp other = (IntegerStamp) otherStamp;
        return lowerBound > other.upperBound || upperBound < other.lowerBound;
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        IntegerStamp other = (IntegerStamp) otherStamp;
        assert kind() == other.kind();
        long meetUpperBound = Math.max(upperBound, other.upperBound);
        long meetLowerBound = Math.min(lowerBound, other.lowerBound);
        if (meetLowerBound == lowerBound && meetUpperBound == upperBound) {
            return this;
        } else {
            return new IntegerStamp(kind(), meetLowerBound, meetUpperBound);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (lowerBound ^ (lowerBound >>> 32));
        result = prime * result + (int) (upperBound ^ (upperBound >>> 32));
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
        if (lowerBound != other.lowerBound || upperBound != other.upperBound) {
            return false;
        }
        return true;
    }


}

