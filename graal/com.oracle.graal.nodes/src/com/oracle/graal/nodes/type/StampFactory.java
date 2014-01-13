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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.type.GenericStamp.GenericStampType;

public class StampFactory {

    // JaCoCo Exclude

    private static final Stamp[] stampCache = new Stamp[Kind.values().length];
    private static final Stamp[] illegalStampCache = new Stamp[Kind.values().length];
    private static final Stamp objectStamp = new ObjectStamp(null, false, false, false);
    private static final Stamp objectNonNullStamp = new ObjectStamp(null, false, true, false);
    private static final Stamp objectAlwaysNullStamp = new ObjectStamp(null, false, false, true);
    private static final Stamp dependencyStamp = new GenericStamp(GenericStampType.Dependency);
    private static final Stamp extensionStamp = new GenericStamp(GenericStampType.Extension);
    private static final Stamp conditionStamp = new GenericStamp(GenericStampType.Condition);
    private static final Stamp voidStamp = new GenericStamp(GenericStampType.Void);
    private static final Stamp nodeIntrinsicStamp = new ObjectStamp(null, false, false, false);
    private static final Stamp positiveInt = forInteger(Kind.Int, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);

    private static void setCache(Kind kind, Stamp stamp) {
        stampCache[kind.ordinal()] = stamp;
    }

    static {
        setCache(Kind.Boolean, new IntegerStamp(Kind.Boolean));
        setCache(Kind.Byte, new IntegerStamp(Kind.Byte));
        setCache(Kind.Short, new IntegerStamp(Kind.Short));
        setCache(Kind.Char, new IntegerStamp(Kind.Char));
        setCache(Kind.Int, new IntegerStamp(Kind.Int));
        setCache(Kind.Long, new IntegerStamp(Kind.Long));

        setCache(Kind.Float, new FloatStamp(Kind.Float));
        setCache(Kind.Double, new FloatStamp(Kind.Double));

        setCache(Kind.Object, objectStamp);
        setCache(Kind.Void, voidStamp);
        for (Kind k : Kind.values()) {
            illegalStampCache[k.ordinal()] = new IllegalStamp(k);
        }
    }

    public static Stamp forKind(Kind kind) {
        assert stampCache[kind.ordinal()] != null : "unexpected forKind(" + kind + ")";
        return stampCache[kind.ordinal()];
    }

    public static Stamp forVoid() {
        return voidStamp;
    }

    /**
     * A stamp used only in the graph of intrinsics, e.g., snippets. It is then replaced by an
     * actual stamp when the intrinsic is used, i.e., when the snippet template is instantiated.
     */
    public static Stamp forNodeIntrinsic() {
        return nodeIntrinsicStamp;
    }

    public static Stamp intValue() {
        return forKind(Kind.Int);
    }

    public static Stamp dependency() {
        return dependencyStamp;
    }

    public static Stamp extension() {
        return extensionStamp;
    }

    public static Stamp condition() {
        return conditionStamp;
    }

    public static Stamp positiveInt() {
        return positiveInt;
    }

    public static Stamp illegal(Kind kind) {
        return illegalStampCache[kind.ordinal()];
    }

    public static IntegerStamp forInteger(Kind kind, long lowerBound, long upperBound, long downMask, long upMask) {
        return new IntegerStamp(kind, lowerBound, upperBound, downMask, upMask);
    }

    public static IntegerStamp forInteger(Kind kind, long lowerBound, long upperBound) {
        long defaultMask = IntegerStamp.defaultMask(kind);
        if (lowerBound == upperBound) {
            return new IntegerStamp(kind, lowerBound, lowerBound, lowerBound & defaultMask, lowerBound & defaultMask);
        }
        final long downMask;
        final long upMask;
        if (lowerBound >= 0) {
            int upperBoundLeadingZeros = Long.numberOfLeadingZeros(upperBound);
            long differentBits = lowerBound ^ upperBound;
            int sameBitCount = Long.numberOfLeadingZeros(differentBits << upperBoundLeadingZeros);

            upMask = upperBound | -1L >>> (upperBoundLeadingZeros + sameBitCount);
            downMask = upperBound & ~(-1L >>> (upperBoundLeadingZeros + sameBitCount));
        } else {
            if (upperBound >= 0) {
                upMask = defaultMask;
                downMask = 0;
            } else {
                int lowerBoundLeadingOnes = Long.numberOfLeadingZeros(~lowerBound);
                long differentBits = lowerBound ^ upperBound;
                int sameBitCount = Long.numberOfLeadingZeros(differentBits << lowerBoundLeadingOnes);

                upMask = lowerBound | -1L >>> (lowerBoundLeadingOnes + sameBitCount) | ~(-1L >>> lowerBoundLeadingOnes);
                downMask = lowerBound & ~(-1L >>> (lowerBoundLeadingOnes + sameBitCount)) | ~(-1L >>> lowerBoundLeadingOnes);
            }
        }
        return forInteger(kind, lowerBound, upperBound, downMask & defaultMask, upMask & defaultMask);
    }

    public static FloatStamp forFloat(Kind kind, double lowerBound, double upperBound, boolean nonNaN) {
        return new FloatStamp(kind, lowerBound, upperBound, nonNaN);
    }

    public static Stamp forConstant(Constant value) {
        Kind kind = value.getKind();
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
                long mask = value.asLong() & IntegerStamp.defaultMask(kind);
                return forInteger(kind.getStackKind(), value.asLong(), value.asLong(), mask, mask);
            case Float:
                return forFloat(kind, value.asFloat(), value.asFloat(), !Float.isNaN(value.asFloat()));
            case Double:
                return forFloat(kind, value.asDouble(), value.asDouble(), !Double.isNaN(value.asDouble()));
            case Illegal:
                return illegal(Kind.Illegal);
            case Object:
                if (value.isNull()) {
                    return alwaysNull();
                } else {
                    return objectNonNull();
                }
            default:
                throw new GraalInternalError("unexpected kind: %s", kind);
        }
    }

    public static Stamp forConstant(Constant value, MetaAccessProvider metaAccess) {
        assert value.getKind() == Kind.Object;
        if (value.getKind() == Kind.Object) {
            ResolvedJavaType type = value.isNull() ? null : metaAccess.lookupJavaType(value);
            return new ObjectStamp(type, value.isNonNull(), value.isNonNull(), value.isNull());
        } else {
            throw new GraalInternalError(Kind.Object + " expected, actual kind: %s", value.getKind());
        }
    }

    public static Stamp object() {
        return objectStamp;
    }

    public static Stamp objectNonNull() {
        return objectNonNullStamp;
    }

    public static Stamp alwaysNull() {
        return objectAlwaysNullStamp;
    }

    public static Stamp declared(ResolvedJavaType type) {
        return declared(type, false);
    }

    public static Stamp declaredNonNull(ResolvedJavaType type) {
        return declared(type, true);
    }

    public static Stamp declared(ResolvedJavaType type, boolean nonNull) {
        return object(type, false, nonNull);
    }

    public static Stamp object(ResolvedJavaType type, boolean exactType, boolean nonNull) {
        assert type != null;
        assert type.getKind() == Kind.Object;
        ResolvedJavaType exact = type.asExactType();
        if (exact != null) {
            assert !exactType || type == exact;
            return new ObjectStamp(exact, true, nonNull, false);
        } else {
            return new ObjectStamp(type, exactType, nonNull, false);
        }
    }

    public static Stamp exactNonNull(ResolvedJavaType type) {
        if (ObjectStamp.isConcreteType(type)) {
            return new ObjectStamp(type, true, true, false);
        } else {
            return illegal(Kind.Object);
        }
    }

    public static Stamp exact(ResolvedJavaType type) {
        return new ObjectStamp(type, true, false, false);
    }
}
