/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.types.*;


public class StampFactory {

    private static class BasicValueStamp implements Stamp {

        private final CiKind kind;
        private final boolean nonNull;
        private RiResolvedType declaredType;
        private RiResolvedType exactType;
        private final ScalarTypeQuery scalarType;
        private final ObjectTypeQuery objectType;

        public BasicValueStamp(CiKind kind) {
            this(kind, false, null, null);
        }

        public BasicValueStamp(CiKind kind, boolean nonNull, RiResolvedType declaredType, RiResolvedType exactType) {
            this(kind, nonNull, declaredType, exactType, null, null);
        }

        public BasicValueStamp(CiKind kind, boolean nonNull, RiResolvedType declaredType, RiResolvedType exactType, ScalarTypeQuery scalarType, ObjectTypeQuery objectType) {
            this.kind = kind;
            this.nonNull = nonNull;
            this.declaredType = declaredType;
            this.exactType = exactType;
            this.scalarType = scalarType;
            this.objectType = objectType;
        }

        @Override
        public CiKind kind() {
            return kind;
        }

        @Override
        public boolean nonNull() {
            return nonNull;
        }

        @Override
        public RiResolvedType declaredType() {
            return declaredType;
        }

        @Override
        public RiResolvedType exactType() {
            return exactType;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Stamp) {
                Stamp other = (Stamp) obj;
                return kind == other.kind() && nonNull() == other.nonNull() && declaredType() == other.declaredType() && exactType() == other.exactType();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return kind.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append(kind().typeChar);
            if (nonNull || declaredType != null || exactType != null) {
                str.append(nonNull ? "!" : "").append(' ').append(declaredType == null ? "-" : declaredType.name()).append(' ').append(exactType == null ? "-" : exactType.name());
            }
            if (scalarType != null) {
                str.append(' ').append(scalarType);
            }
            if (objectType != null) {
                str.append(' ').append(objectType);
            }
            return str.toString();
        }

        @Override
        public boolean alwaysDistinct(Stamp other) {
            if (other.kind() != kind()) {
                return true;
            } else if (kind() != CiKind.Object) {
                return false;
            } else if (other.declaredType() == null || declaredType() == null) {
                // We have no type information for one of the values.
                return false;
            } else if (other.nonNull() || nonNull()) {
                // One of the two values cannot be null.
                return !other.declaredType().isInterface() && !declaredType().isInterface() && !other.declaredType().isSubtypeOf(declaredType()) && !declaredType().isSubtypeOf(other.declaredType());
            } else {
                // Both values may be null.
                return false;
            }
        }

        @Override
        public ScalarTypeQuery scalarType() {
            return scalarType;
        }

        @Override
        public ObjectTypeQuery objectType() {
            return objectType;
        }
    }

    private static final Stamp[] stampCache = new Stamp[CiKind.values().length];
    static {
        for (CiKind k : CiKind.values()) {
            stampCache[k.ordinal()] = new BasicValueStamp(k);
        }
    }

    public static Stamp illegal() {
        return forKind(CiKind.Illegal);
    }

    public static Stamp intValue() {
        return forKind(CiKind.Int);
    }

    public static Stamp forKind(CiKind kind) {
        return stampCache[kind.stackKind().ordinal()];
    }

    public static Stamp forKind(CiKind kind, ScalarTypeQuery scalarTypeFeedback, ObjectTypeQuery objectTypeFeedback) {
        if (scalarTypeFeedback == null && objectTypeFeedback == null) {
            return forKind(kind);
        } else {
            return new BasicValueStamp(kind, false, null, null, scalarTypeFeedback, objectTypeFeedback);
        }
    }

    public static final Stamp positiveInt = forInt(0, Integer.MAX_VALUE);

    public static Stamp positiveInt() {
        return positiveInt;
    }

    public static Stamp forInt(int lowerBound, int upperBound) {
        ScalarTypeFeedbackStore scalarType = new ScalarTypeFeedbackStore(CiKind.Int, new TypeFeedbackChanged());
        scalarType.constantBound(Condition.GE, CiConstant.forInt(lowerBound));
        scalarType.constantBound(Condition.LE, CiConstant.forInt(upperBound));

        return new BasicValueStamp(CiKind.Int, false, null, null, scalarType.query(), null);
    }

    public static Stamp forLong(long lowerBound, long upperBound) {
        ScalarTypeFeedbackStore scalarType = new ScalarTypeFeedbackStore(CiKind.Long, new TypeFeedbackChanged());
        scalarType.constantBound(Condition.GE, CiConstant.forLong(lowerBound));
        scalarType.constantBound(Condition.LE, CiConstant.forLong(upperBound));

        return new BasicValueStamp(CiKind.Long, false, null, null, scalarType.query(), null);
    }

    public static Stamp exactNonNull(final RiResolvedType type) {
        // (cwimmer) type can be null for certain Maxine-internal objects such as the static hub. Is this a problem here?
        assert type == null || type.kind(false) == CiKind.Object;
        ObjectTypeFeedbackStore objectType = new ObjectTypeFeedbackStore(new TypeFeedbackChanged());
        objectType.constantBound(Condition.NE, CiConstant.NULL_OBJECT);
        objectType.exactType(type);
        return new BasicValueStamp(CiKind.Object, true, type, type, null, objectType.query());
    }

    public static Stamp forConstant(CiConstant value) {
        assert value.kind != CiKind.Object;
        if (value.kind == CiKind.Object) {
            throw new GraalInternalError("unexpected kind: %s", value.kind);
        } else {
            if (value.kind == CiKind.Int) {
                return forInt(value.asInt(), value.asInt());
            } else if (value.kind == CiKind.Long) {
                return forLong(value.asLong(), value.asLong());
            }
            return forKind(value.kind.stackKind());
        }
    }

    public static Stamp forConstant(CiConstant value, RiRuntime runtime) {
        assert value.kind == CiKind.Object;
        if (value.kind == CiKind.Object) {
            ObjectTypeFeedbackStore objectType = new ObjectTypeFeedbackStore(new TypeFeedbackChanged());
            objectType.constantBound(Condition.EQ, value);
            RiResolvedType type = value.isNull() ? null : runtime.getTypeOf(value);
            return new BasicValueStamp(CiKind.Object, value.isNonNull(), type, type, null, objectType.query());
        } else {
            throw new GraalInternalError("CiKind.Object expected, actual kind: %s", value.kind);
        }
    }

    public static Stamp objectNonNull() {
        return new BasicValueStamp(CiKind.Object, true, null, null);
    }

    public static Stamp declared(final RiResolvedType type) {
        assert type != null;
        assert type.kind(false) == CiKind.Object;
        return new BasicValueStamp(CiKind.Object, false, type, type.exactType());
    }

    public static Stamp declaredNonNull(final RiResolvedType type) {
        assert type != null;
        assert type.kind(false) == CiKind.Object;
        return new BasicValueStamp(CiKind.Object, true, type, type.exactType());
    }

    public static Stamp or(Collection<? extends StampProvider> values) {
        if (values.size() == 0) {
            return illegal();
        } else {
            Iterator< ? extends StampProvider> iterator = values.iterator();
            Stamp first = iterator.next().stamp();
            if (values.size() == 1) {
                return first;
            }

            boolean nonNull = first.nonNull();
            RiResolvedType declaredType = first.declaredType();
            RiResolvedType exactType = first.exactType();
            while (iterator.hasNext()) {
                Stamp current = iterator.next().stamp();
                assert current.kind() == first.kind() : values + " first=" + first + " current=" + current + " first kind=" + first.kind() + " current kind=" + current.kind();
                nonNull &= current.nonNull();
                declaredType = orTypes(declaredType, current.declaredType());
                if (exactType != current.exactType()) {
                    exactType = null;
                }
            }

            if (nonNull != first.nonNull() || declaredType != first.declaredType() || exactType != first.exactType()) {
                return new BasicValueStamp(first.kind(), nonNull, declaredType, exactType);
            } else {
                return first;
            }
        }
    }

    private static RiResolvedType orTypes(RiResolvedType a, RiResolvedType b) {
        if (a == b) {
            return a;
        } else if (a == null || b == null) {
            return null;
        } else {
            return a.leastCommonAncestor(b);
        }
    }
}
