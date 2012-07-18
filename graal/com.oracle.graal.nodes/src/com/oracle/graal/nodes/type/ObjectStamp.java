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


public class ObjectStamp extends Stamp {

    private final ResolvedJavaType type;
    private final boolean exactType;
    private final boolean nonNull;
    private final boolean alwaysNull;

    public ObjectStamp(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull) {
        super(Kind.Object);
        assert !exactType || type != null;
        assert !(nonNull && alwaysNull);
        this.type = type;
        this.exactType = exactType;
        this.nonNull = nonNull;
        this.alwaysNull = alwaysNull;
    }

    @Override
    public boolean nonNull() {
        return nonNull;
    }

    public boolean alwaysNull() {
        return alwaysNull;
    }

    public ResolvedJavaType type() {
        return type;
    }

    public boolean isExactType() {
        return exactType;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(kind().typeChar);
        str.append(nonNull ? "!" : "").append(exactType ? "#" : "").append(' ').append(type == null ? "-" : type.name()).append(alwaysNull ? " NULL" : "");
        return str.toString();
    }

    @Override
    public boolean alwaysDistinct(Stamp otherStamp) {
        ObjectStamp other = (ObjectStamp) otherStamp;
        if ((alwaysNull && other.nonNull) || (nonNull && other.alwaysNull)) {
            return true;
        }
        if (other.type == null || type == null) {
            // We have no type information for one of the values.
            return false;
        } else if (other.nonNull || nonNull) {
            // One of the two values cannot be null.
            return !other.type.isInterface() && !type.isInterface() && !other.type.isSubtypeOf(type) && !type.isSubtypeOf(other.type);
        }
        return false;
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        ObjectStamp other = (ObjectStamp) otherStamp;
        ResolvedJavaType meetType;
        boolean meetExactType;
        boolean meetNonNull;
        boolean meetAlwaysNull;
        if (other.alwaysNull) {
            meetType = type();
            meetExactType = exactType;
            meetNonNull = false;
            meetAlwaysNull = alwaysNull;
        } else if (alwaysNull) {
            meetType = other.type();
            meetExactType = other.exactType;
            meetNonNull = false;
            meetAlwaysNull = other.alwaysNull;
        } else {
            meetType = meetTypes(type(), other.type());
            meetExactType = meetType == type && meetType == other.type && exactType && other.exactType;
            meetNonNull = nonNull && other.nonNull;
            meetAlwaysNull = false;
        }

        if (meetType == type && meetExactType == exactType && meetNonNull == nonNull && meetAlwaysNull == alwaysNull) {
            return this;
        } else {
            return new ObjectStamp(meetType, meetExactType, meetNonNull, meetAlwaysNull);
        }
    }

    private static ResolvedJavaType meetTypes(ResolvedJavaType a, ResolvedJavaType b) {
        if (a == b) {
            return a;
        } else if (a == null || b == null) {
            return null;
        } else {
            return a.leastCommonAncestor(b);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (exactType ? 1231 : 1237);
        result = prime * result + (nonNull ? 1231 : 1237);
        result = prime * result + ((type == null) ? 0 : type.hashCode());
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
        ObjectStamp other = (ObjectStamp) obj;
        if (exactType != other.exactType || nonNull != other.nonNull) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        return true;
    }
}
