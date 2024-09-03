/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.core.common.type;

import static jdk.vm.ci.code.CodeUtil.signExtend;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class StampFactory {

    // JaCoCo Exclude

    private static final Stamp[] stampCache = new Stamp[JavaKind.values().length];
    private static final Stamp[] emptyStampCache = new Stamp[JavaKind.values().length];
    private static final Stamp objectStamp = new ObjectStamp(null, false, false, false, false);
    private static final Stamp objectNonNullStamp = new ObjectStamp(null, false, true, false, false);
    private static final Stamp objectAlwaysNullStamp = new ObjectStamp(null, false, false, true, false);
    private static final Stamp positiveInt = forInteger(JavaKind.Int, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
    private static final Stamp nonZeroInt = IntegerStamp.create(32, JavaKind.Int.getMinValue(), JavaKind.Int.getMaxValue(), 0, CodeUtil.mask(JavaKind.Int.getStackKind().getBitCount()), false);
    private static final Stamp nonZeroLong = IntegerStamp.create(64, JavaKind.Long.getMinValue(), JavaKind.Long.getMaxValue(), 0, CodeUtil.mask(JavaKind.Long.getStackKind().getBitCount()), false);
    private static final Stamp booleanTrue = forInteger(JavaKind.Boolean, -1, -1, 1, 1);
    private static final Stamp booleanFalse = forInteger(JavaKind.Boolean, 0, 0, 0, 0);
    private static final Stamp rawPointer = new RawPointerStamp();

    private static void setCache(JavaKind kind, Stamp stamp) {
        stampCache[kind.ordinal()] = stamp;
    }

    private static void setIntCache(JavaKind kind) {
        int bits = kind.getStackKind().getBitCount();
        long mask;
        if (kind.isUnsigned()) {
            mask = CodeUtil.mask(kind.getBitCount());
        } else {
            mask = CodeUtil.mask(bits);
        }
        setCache(kind, IntegerStamp.create(bits, kind.getMinValue(), kind.getMaxValue(), 0, mask));
    }

    private static void setFloatCache(JavaKind kind) {
        setCache(kind, FloatStamp.createUnrestricted(kind.getBitCount()));
    }

    static {
        setIntCache(JavaKind.Boolean);
        setIntCache(JavaKind.Byte);
        setIntCache(JavaKind.Short);
        setIntCache(JavaKind.Char);
        setIntCache(JavaKind.Int);
        setIntCache(JavaKind.Long);

        setFloatCache(JavaKind.Float);
        setFloatCache(JavaKind.Double);

        setCache(JavaKind.Object, objectStamp);
        setCache(JavaKind.Void, VoidStamp.getInstance());
        setCache(JavaKind.Illegal, IllegalStamp.getInstance());

        for (JavaKind k : JavaKind.values()) {
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
    public static Stamp forKind(JavaKind kind) {
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

    public static Stamp intValue() {
        return forKind(JavaKind.Int);
    }

    public static Stamp positiveInt() {
        return positiveInt;
    }

    public static Stamp nonZeroInt() {
        return nonZeroInt;
    }

    public static Stamp nonZeroLong() {
        return nonZeroLong;
    }

    public static Stamp empty(JavaKind kind) {
        return emptyStampCache[kind.ordinal()];
    }

    public static IntegerStamp forInteger(JavaKind kind, long lowerBound, long upperBound, long mustBeSet, long mayBeSet) {
        return IntegerStamp.create(kind.getBitCount(), lowerBound, upperBound, mustBeSet, mayBeSet);
    }

    public static IntegerStamp forInteger(int bits) {
        return IntegerStamp.create(bits, CodeUtil.minValue(bits), CodeUtil.maxValue(bits), 0, CodeUtil.mask(bits));
    }

    public static IntegerStamp forInteger(JavaKind kind, long lowerBound, long upperBound) {
        return IntegerStamp.create(kind.getBitCount(), lowerBound, upperBound);
    }

    public static IntegerStamp forIntegerWithMask(int bits, long newLowerBound, long newUpperBound, long newMustBeSet, long newMayBeSet) {
        IntegerStamp limit = IntegerStamp.create(bits, newLowerBound, newUpperBound);
        return IntegerStamp.create(bits, newLowerBound, newUpperBound, limit.mustBeSet() | newMustBeSet, limit.mayBeSet() & newMayBeSet);
    }

    public static IntegerStamp forUnsignedInteger(int bits) {
        return forUnsignedInteger(bits, 0, NumUtil.maxValueUnsigned(bits));
    }

    public static IntegerStamp forUnsignedInteger(int bits, long unsignedLowerBound, long unsignedUpperBound) {
        return forUnsignedInteger(bits, unsignedLowerBound, unsignedUpperBound, 0, CodeUtil.mask(bits));
    }

    /**
     * Creates an IntegerStamp from the given unsigned bounds and bit masks. This method returns an
     * empty stamp if the unsigned lower bound is larger than the unsigned upper bound. If the sign
     * of lower and upper bound differs after sign extension to the specified length ({@code bits}),
     * this method returns an unrestricted stamp with respect to the bounds. {@code mayBeSet} or
     * {@code mustBeSet} can restrict the bounds nevertheless. Take the following example when
     * inverting a zero extended 32bit stamp to the 8bit stamp before extension:
     *
     * </p>
     * 32bit stamp [0, 192] 00...00 xxxxxxx0
     * </p>
     * When converting the bounds to 8bit, they have different signs, i.e.:
     * </p>
     * lowerUnsigned = 0000 0000 and upperUnsigned = 1100 0000
     * </p>
     * In order to respect the (unsigned) boundaries of the extended value, the signed input can be:
     * </p>
     * 0000 0000 - 0111 1111, i.e. 0 to 127 </br>
     * or </br>
     * 1000 0000 - 1100 0000, i.e. -128 to -64
     * </p>
     * The resulting interval [-128, -64]u[0, 127] cannot be represented by a single upper and lower
     * bound. Thus, we have to return an unrestricted stamp, with respect to the bounds.
     */
    public static IntegerStamp forUnsignedInteger(int bits, long unsignedLowerBound, long unsignedUpperBound, long mustBeSet, long mayBeSet) {
        if (Long.compareUnsigned(unsignedLowerBound, unsignedUpperBound) > 0) {
            return IntegerStamp.createEmptyStamp(bits);
        }
        long lowerBound = signExtend(unsignedLowerBound, bits);
        long upperBound = signExtend(unsignedUpperBound, bits);
        if (!NumUtil.sameSign(lowerBound, upperBound)) {
            lowerBound = CodeUtil.minValue(bits);
            upperBound = CodeUtil.maxValue(bits);
        }
        long mask = CodeUtil.mask(bits);
        return IntegerStamp.create(bits, lowerBound, upperBound, mustBeSet & mask, mayBeSet & mask);
    }

    public static FloatStamp forFloat(JavaKind kind, double lowerBound, double upperBound, boolean nonNaN) {
        assert kind.isNumericFloat();
        return FloatStamp.create(kind.getBitCount(), lowerBound, upperBound, nonNaN);
    }

    public static Stamp forConstant(JavaConstant value) {
        JavaKind kind = value.getJavaKind();
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
                return IntegerStamp.createConstant(kind.getStackKind().getBitCount(), value.asLong());
            case Float:
                return forFloat(kind, value.asFloat(), value.asFloat(), !Float.isNaN(value.asFloat()));
            case Double:
                return forFloat(kind, value.asDouble(), value.asDouble(), !Double.isNaN(value.asDouble()));
            case Illegal:
                return forKind(JavaKind.Illegal);
            case Object:
                if (value.isNull()) {
                    return alwaysNull();
                } else {
                    return objectNonNull();
                }
            default:
                throw new GraalError("unexpected kind: %s", kind);
        }
    }

    public static Stamp forConstant(JavaConstant value, MetaAccessProvider metaAccess) {
        if (value.getJavaKind() == JavaKind.Object) {
            ResolvedJavaType type = value.isNull() ? null : metaAccess.lookupJavaType(value);
            return new ObjectStamp(type, value.isNonNull(), value.isNonNull(), value.isNull(), false);
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

    public static ObjectStamp object(TypeReference type) {
        return object(type, false);
    }

    public static ObjectStamp objectNonNull(TypeReference type) {
        return object(type, true);
    }

    public static ObjectStamp object(TypeReference type, boolean nonNull) {
        if (type == null) {
            return new ObjectStamp(null, false, nonNull, false, false);
        } else {
            return new ObjectStamp(type.getType(), type.isExact(), nonNull, false, false);
        }
    }

    public static Stamp[] createParameterStamps(Assumptions assumptions, ResolvedJavaMethod method) {
        return createParameterStamps(assumptions, method, false);
    }

    public static Stamp[] createParameterStamps(Assumptions assumptions, ResolvedJavaMethod method, boolean trustInterfaceTypes) {
        Signature signature = method.getSignature();
        Stamp[] result = new Stamp[signature.getParameterCount(method.hasReceiver())];

        int index = 0;
        ResolvedJavaType accessingClass = method.getDeclaringClass();
        if (method.hasReceiver()) {
            if (trustInterfaceTypes) {
                result[index++] = StampFactory.objectNonNull(TypeReference.createTrusted(assumptions, accessingClass));
            } else {
                result[index++] = StampFactory.objectNonNull(TypeReference.create(assumptions, accessingClass));
            }
        }

        for (int i = 0; i < signature.getParameterCount(false); i++) {
            JavaType type = signature.getParameterType(i, accessingClass);
            JavaKind kind = type.getJavaKind();

            Stamp stamp;
            if (kind == JavaKind.Object && type instanceof ResolvedJavaType) {
                if (trustInterfaceTypes) {
                    stamp = StampFactory.object(TypeReference.createTrusted(assumptions, (ResolvedJavaType) type));
                } else {
                    stamp = StampFactory.object(TypeReference.create(assumptions, (ResolvedJavaType) type));
                }
            } else {
                stamp = StampFactory.forKind(kind);
            }
            result[index++] = stamp;
        }

        return result;
    }

    public static Stamp pointer() {
        return rawPointer;
    }

    public static StampPair forDeclaredType(Assumptions assumptions, JavaType returnType, boolean nonNull) {
        if (returnType.getJavaKind() == JavaKind.Object && returnType instanceof ResolvedJavaType) {
            ResolvedJavaType resolvedJavaType = (ResolvedJavaType) returnType;
            TypeReference reference = TypeReference.create(assumptions, resolvedJavaType);
            ResolvedJavaType elementalType = resolvedJavaType.getElementalType();
            if (elementalType.isInterface()) {
                assert reference == null || !reference.getType().equals(resolvedJavaType);
                TypeReference uncheckedType;
                ResolvedJavaType elementalImplementor = elementalType.getSingleImplementor();
                if (!resolvedJavaType.isArray() && elementalImplementor != null && !elementalType.equals(elementalImplementor)) {
                    ResolvedJavaType implementor = elementalImplementor;
                    ResolvedJavaType t = resolvedJavaType;
                    while (t.isArray()) {
                        implementor = implementor.getArrayClass();
                        t = t.getComponentType();
                    }
                    uncheckedType = TypeReference.createTrusted(assumptions, implementor);
                } else {
                    uncheckedType = TypeReference.createTrusted(assumptions, resolvedJavaType);
                }
                return StampPair.create(StampFactory.object(reference, nonNull), StampFactory.object(uncheckedType, nonNull));
            }
            return StampPair.createSingle(StampFactory.object(reference, nonNull));
        } else {
            return StampPair.createSingle(StampFactory.forKind(returnType.getJavaKind()));
        }
    }

}
