/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.jvmci.common.*;

public class StampFactory {

    // JaCoCo Exclude

    private static final Stamp[] stampCache = new Stamp[Kind.values().length];
    private static final Stamp[] emptyStampCache = new Stamp[Kind.values().length];
    private static final Stamp objectStamp = new ObjectStamp(null, false, false, false);
    private static final Stamp objectNonNullStamp = new ObjectStamp(null, false, true, false);
    private static final Stamp objectAlwaysNullStamp = new ObjectStamp(null, false, false, true);
    private static final Stamp nodeIntrinsicStamp = new ObjectStamp(null, false, false, false);
    private static final Stamp positiveInt = forInteger(Kind.Int, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
    private static final Stamp booleanTrue = forInteger(Kind.Boolean, -1, -1, 1, 1);
    private static final Stamp booleanFalse = forInteger(Kind.Boolean, 0, 0, 0, 0);

    private static void setCache(Kind kind, Stamp stamp) {
        stampCache[kind.ordinal()] = stamp;
    }

    private static void setIntCache(Kind kind) {
        int bits = kind.getStackKind().getBitCount();
        long mask;
        if (kind.isUnsigned()) {
            mask = CodeUtil.mask(kind.getBitCount());
        } else {
            mask = CodeUtil.mask(bits);
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
        setCache(Kind.Illegal, IllegalStamp.getInstance());

        for (Kind k : Kind.values()) {
            if (stampCache[k.ordinal()] != null) {
                emptyStampCache[k.ordinal()] = stampCache[k.ordinal()].empty();
            }
        }
    }

    public static Stamp tautology() {
        return booleanTrue;
    }

    public static Stamp contradiction() {
        return booleanFalse;
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

    public static Stamp empty(Kind kind) {
        return emptyStampCache[kind.ordinal()];
    }

    public static IntegerStamp forInteger(Kind kind, long lowerBound, long upperBound, long downMask, long upMask) {
        return new IntegerStamp(kind.getBitCount(), lowerBound, upperBound, downMask, upMask);
    }

    public static IntegerStamp forInteger(Kind kind, long lowerBound, long upperBound) {
        return forInteger(kind.getBitCount(), lowerBound, upperBound);
    }

    public static IntegerStamp forInteger(int bits) {
        return new IntegerStamp(bits, CodeUtil.minValue(bits), CodeUtil.maxValue(bits), 0, CodeUtil.mask(bits));
    }

    public static IntegerStamp forInteger(int bits, long lowerBound, long upperBound) {
        long defaultMask = CodeUtil.mask(bits);
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

    public static Stamp forConstant(JavaConstant value) {
        Kind kind = value.getKind();
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
                long mask = value.asLong() & CodeUtil.mask(kind.getBitCount());
                return forInteger(kind.getStackKind(), value.asLong(), value.asLong(), mask, mask);
            case Float:
                return forFloat(kind, value.asFloat(), value.asFloat(), !Float.isNaN(value.asFloat()));
            case Double:
                return forFloat(kind, value.asDouble(), value.asDouble(), !Double.isNaN(value.asDouble()));
            case Illegal:
                return forKind(Kind.Illegal);
            case Object:
                if (value.isNull()) {
                    return alwaysNull();
                } else {
                    return objectNonNull();
                }
            default:
                throw new JVMCIError("unexpected kind: %s", kind);
        }
    }

    public static Stamp forConstant(JavaConstant value, MetaAccessProvider metaAccess) {
        if (value.getKind() == Kind.Object) {
            ResolvedJavaType type = value.isNull() ? null : metaAccess.lookupJavaType(value);
            return new ObjectStamp(type, value.isNonNull(), value.isNonNull(), value.isNull());
        } else {
            return forConstant(value);
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

    /**
     * Returns a {@link Stamp} for objects of type {@code type}, or one of its subtypes, or null.
     */
    public static Stamp declared(ResolvedJavaType type) {
        return object(type, false, false, false);
    }

    /**
     * Returns a {@link Stamp} for objects of type {@code type}, or one of its subtypes, but not
     * null.
     */
    public static Stamp declaredNonNull(ResolvedJavaType type) {
        return object(type, false, true, false);
    }

    /**
     * Returns a {@link Stamp} for objects of type {@code type}, or one of its subtypes, or null.
     * Contrary to {@link #declared(ResolvedJavaType)}, interface types will be preserved in the
     * stamp.
     *
     * In general interface types are not verified at class loading or run-time so this should be
     * used with care.
     */
    public static Stamp declaredTrusted(ResolvedJavaType type) {
        return object(type, false, false, true);
    }

    /**
     * Returns a {@link Stamp} for objects of type {@code type}, or one of its subtypes, but not
     * null. Contrary to {@link #declaredNonNull(ResolvedJavaType)}, interface types will be
     * preserved in the stamp.
     *
     * In general interface types are not verified at class loading or run-time so this should be
     * used with care.
     */
    public static Stamp declaredTrustedNonNull(ResolvedJavaType type) {
        return declaredTrusted(type, true);
    }

    public static Stamp declaredTrusted(ResolvedJavaType type, boolean nonNull) {
        return object(type, false, nonNull, true);
    }

    /**
     * Returns a {@link Stamp} for objects of exactly type {@code type}, or null.
     */
    public static Stamp exact(ResolvedJavaType type) {
        if (ObjectStamp.isConcreteType(type)) {
            return new ObjectStamp(type, true, false, false);
        } else {
            return empty(Kind.Object);
        }
    }

    /**
     * Returns a {@link Stamp} for non-null objects of exactly type {@code type}.
     */
    public static Stamp exactNonNull(ResolvedJavaType type) {
        if (ObjectStamp.isConcreteType(type)) {
            return new ObjectStamp(type, true, true, false);
        } else {
            return empty(Kind.Object);
        }
    }

    private static ResolvedJavaType filterInterfaceTypesOut(ResolvedJavaType type) {
        if (type.isArray()) {
            ResolvedJavaType componentType = filterInterfaceTypesOut(type.getComponentType());
            if (componentType != null) {
                return componentType.getArrayClass();
            }
            return type.getSuperclass().getArrayClass(); // arrayType.getSuperClass() == Object type
        }
        if (type.isInterface() && !type.isTrustedInterfaceType()) {
            return null;
        }
        return type;
    }

    public static Stamp object(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean trustInterfaces) {
        assert type != null;
        assert type.getKind() == Kind.Object;
        ResolvedJavaType trustedtype;
        if (!trustInterfaces) {
            trustedtype = filterInterfaceTypesOut(type);
            assert !exactType || trustedtype.equals(type);
        } else {
            trustedtype = type;
        }
        ResolvedJavaType exact = trustedtype != null ? trustedtype.asExactType() : null;
        if (exact != null) {
            assert !exactType || trustedtype.equals(exact);
            return new ObjectStamp(exact, true, nonNull, false);
        }
        assert !exactType || AbstractObjectStamp.isConcreteType(trustedtype);
        return new ObjectStamp(trustedtype, exactType, nonNull, false);
    }

    public static Stamp[] createParameterStamps(ResolvedJavaMethod method) {
        Signature sig = method.getSignature();
        Stamp[] result = new Stamp[sig.getParameterCount(!method.isStatic())];
        int index = 0;

        if (!method.isStatic()) {
            result[index++] = StampFactory.declaredNonNull(method.getDeclaringClass());
        }

        int max = sig.getParameterCount(false);
        ResolvedJavaType accessingClass = method.getDeclaringClass();
        for (int i = 0; i < max; i++) {
            JavaType type = sig.getParameterType(i, accessingClass);
            Kind kind = type.getKind();
            Stamp stamp;
            if (kind == Kind.Object && type instanceof ResolvedJavaType) {
                stamp = StampFactory.declared((ResolvedJavaType) type);
            } else {
                stamp = StampFactory.forKind(kind);
            }
            result[index++] = stamp;
        }

        return result;
    }
}
