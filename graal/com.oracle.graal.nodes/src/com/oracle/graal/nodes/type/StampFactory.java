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
import com.oracle.graal.compiler.common.*;

public class StampFactory {

    // JaCoCo Exclude

    private static final Stamp[] stampCache = new Stamp[Kind.values().length];
    private static final Stamp[] illegalStampCache = new Stamp[Kind.values().length];
    private static final Stamp objectStamp = new ObjectStamp(null, false, false, false);
    private static final Stamp objectNonNullStamp = new ObjectStamp(null, false, true, false);
    private static final Stamp objectAlwaysNullStamp = new ObjectStamp(null, false, false, true);
    private static final Stamp nodeIntrinsicStamp = new ObjectStamp(null, false, false, false);
    private static final Stamp positiveInt = forInteger(Kind.Int, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);

    private static void setCache(Kind kind, Stamp stamp) {
        stampCache[kind.ordinal()] = stamp;
    }

    private static void setIntCache(Kind kind) {
        int bits = kind.getStackKind().getBitCount();
        long mask;
        if (kind.isUnsigned()) {
            mask = IntegerStamp.defaultMask(kind.getBitCount());
        } else {
            mask = IntegerStamp.defaultMask(bits);
        }
        setCache(kind, new IntegerStamp(bits, kind.getMinValue(), kind.getMaxValue(), 0, mask));
    }

    private static void setFloatCache(Kind kind) {
        setCache(kind, new FloatStamp(kind.getBitCount()));
    }

    static {
        setIntCache(Kind.Boolean);
        setIntCache(Kind.Byte);
        setIntCache(Kind.Short);
        setIntCache(Kind.Char);
        setIntCache(Kind.Int);
        setIntCache(Kind.Long);

        setFloatCache(Kind.Float);
        setFloatCache(Kind.Double);

        setCache(Kind.Object, objectStamp);
        setCache(Kind.Void, VoidStamp.getInstance());

        for (Kind k : Kind.values()) {
            if (stampCache[k.ordinal()] != null) {
                illegalStampCache[k.ordinal()] = stampCache[k.ordinal()].illegal();
            } else {
                illegalStampCache[k.ordinal()] = IllegalStamp.getInstance();
            }
        }
    }

    /**
     * Return a stamp for a Java kind, as it would be represented on the bytecode stack.
     */
    public static Stamp forKind(Kind kind) {
        assert stampCache[kind.ordinal()] != null : "unexpected forKind(" + kind + ")";
        return stampCache[kind.ordinal()];
    }

    /**
     * Return the stamp for the {@code void} type. This will return a singleton instance than can be
     * compared using {@code ==}.
     */
    public static Stamp forVoid() {
        return VoidStamp.getInstance();
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

    public static Stamp positiveInt() {
        return positiveInt;
    }

    public static Stamp illegal() {
        return illegal(Kind.Illegal);
    }

    public static Stamp illegal(Kind kind) {
        return illegalStampCache[kind.ordinal()];
    }

    public static IntegerStamp forInteger(Kind kind, long lowerBound, long upperBound, long downMask, long upMask) {
        return new IntegerStamp(kind.getBitCount(), lowerBound, upperBound, downMask, upMask);
    }

    public static IntegerStamp forInteger(Kind kind, long lowerBound, long upperBound) {
        return forInteger(kind.getBitCount(), lowerBound, upperBound);
    }

    public static IntegerStamp forInteger(int bits) {
        return new IntegerStamp(bits, IntegerStamp.defaultMinValue(bits), IntegerStamp.defaultMaxValue(bits), 0, IntegerStamp.defaultMask(bits));
    }

    public static IntegerStamp forInteger(int bits, long lowerBound, long upperBound) {
        long defaultMask = IntegerStamp.defaultMask(bits);
        if (lowerBound == upperBound) {
            return new IntegerStamp(bits, lowerBound, lowerBound, lowerBound & defaultMask, lowerBound & defaultMask);
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
        return new IntegerStamp(bits, lowerBound, upperBound, downMask & defaultMask, upMask & defaultMask);
    }

    public static FloatStamp forFloat(Kind kind, double lowerBound, double upperBound, boolean nonNaN) {
        assert kind.isNumericFloat();
        return new FloatStamp(kind.getBitCount(), lowerBound, upperBound, nonNaN);
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
                long mask = value.asLong() & IntegerStamp.defaultMask(kind.getBitCount());
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
            assert !exactType || type.equals(exact);
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
