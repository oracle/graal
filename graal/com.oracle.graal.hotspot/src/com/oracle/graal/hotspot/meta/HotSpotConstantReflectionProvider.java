/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;

/**
 * HotSpot implementation of {@link ConstantReflectionProvider}.
 */
public class HotSpotConstantReflectionProvider implements ConstantReflectionProvider {

    protected final HotSpotGraalRuntime runtime;

    public HotSpotConstantReflectionProvider(HotSpotGraalRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        return x.equals(y);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (array.getKind() != Kind.Object || array.isNull()) {
            return null;
        }

        Object arrayObject = HotSpotObjectConstantImpl.asObject(array);
        if (!arrayObject.getClass().isArray()) {
            return null;
        }
        return Array.getLength(arrayObject);
    }

    @Override
    public JavaConstant readUnsafeConstant(Kind kind, JavaConstant baseConstant, long initialDisplacement) {
        Object base;
        long displacement;
        if (baseConstant.getKind() == Kind.Object) {
            base = HotSpotObjectConstantImpl.asObject(baseConstant);
            displacement = initialDisplacement;
            if (base == null) {
                return null;
            }
        } else if (baseConstant.getKind().isNumericInteger()) {
            long baseLong = baseConstant.asLong();
            if (baseLong == 0L) {
                return null;
            }
            displacement = initialDisplacement + baseLong;
            base = null;
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }

        switch (kind) {
            case Boolean:
                return JavaConstant.forBoolean(base == null ? unsafe.getByte(displacement) != 0 : unsafe.getBoolean(base, displacement));
            case Byte:
                return JavaConstant.forByte(base == null ? unsafe.getByte(displacement) : unsafe.getByte(base, displacement));
            case Char:
                return JavaConstant.forChar(base == null ? unsafe.getChar(displacement) : unsafe.getChar(base, displacement));
            case Short:
                return JavaConstant.forShort(base == null ? unsafe.getShort(displacement) : unsafe.getShort(base, displacement));
            case Int:
                return JavaConstant.forInt(base == null ? unsafe.getInt(displacement) : unsafe.getInt(base, displacement));
            case Long:
                if (displacement == config().hubOffset && runtime.getConfig().useCompressedClassPointers) {
                    if (base == null) {
                        throw new GraalInternalError("Base of object must not be null");
                    } else {
                        return JavaConstant.forLong(runtime.getCompilerToVM().readUnsafeKlassPointer(base));
                    }
                } else {
                    return JavaConstant.forLong(base == null ? unsafe.getLong(displacement) : unsafe.getLong(base, displacement));
                }
            case Float:
                return JavaConstant.forFloat(base == null ? unsafe.getFloat(displacement) : unsafe.getFloat(base, displacement));
            case Double:
                return JavaConstant.forDouble(base == null ? unsafe.getDouble(displacement) : unsafe.getDouble(base, displacement));
            case Object: {
                Object o = null;
                if (baseConstant.getKind() == Kind.Object) {
                    o = unsafe.getObject(base, displacement);
                } else if (baseConstant instanceof HotSpotMetaspaceConstant) {
                    Object metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(baseConstant);
                    if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl && initialDisplacement == runtime.getConfig().classMirrorOffset) {
                        o = ((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror();
                    } else {
                        throw GraalInternalError.shouldNotReachHere();
                    }
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
                return HotSpotObjectConstantImpl.forObject(o);
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public JavaConstant readRawConstant(Kind kind, JavaConstant baseConstant, long initialDisplacement, int bits) {
        Object base;
        long displacement;
        if (baseConstant.getKind() == Kind.Object) {
            base = HotSpotObjectConstantImpl.asObject(baseConstant);
            displacement = initialDisplacement;
            if (base == null) {
                return null;
            }
        } else if (baseConstant.getKind().isNumericInteger()) {
            long baseLong = baseConstant.asLong();
            if (baseLong == 0L) {
                return null;
            }
            displacement = initialDisplacement + baseLong;
            base = null;
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }

        long rawValue;
        switch (bits) {
            case 8:
                rawValue = base == null ? unsafe.getByte(displacement) : unsafe.getByte(base, displacement);
                break;
            case 16:
                rawValue = base == null ? unsafe.getShort(displacement) : unsafe.getShort(base, displacement);
                break;
            case 32:
                rawValue = base == null ? unsafe.getInt(displacement) : unsafe.getInt(base, displacement);
                break;
            case 64:
                rawValue = base == null ? unsafe.getLong(displacement) : unsafe.getLong(base, displacement);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }

        if (base != null && displacement == config().hubOffset) {
            if (config().useCompressedClassPointers) {
                assert bits == 32 && kind == Kind.Int;
                long klassPointer = config().getKlassEncoding().uncompress((int) rawValue);
                assert klassPointer == runtime.getCompilerToVM().readUnsafeKlassPointer(base);
                return HotSpotMetaspaceConstantImpl.forMetaspaceObject(kind, rawValue, HotSpotResolvedObjectTypeImpl.fromMetaspaceKlass(klassPointer), true);
            } else {
                assert bits == 64 && kind == Kind.Long;
                return HotSpotMetaspaceConstantImpl.forMetaspaceObject(kind, rawValue, HotSpotResolvedObjectTypeImpl.fromMetaspaceKlass(rawValue), false);
            }
        } else {
            switch (kind) {
                case Int:
                    return JavaConstant.forInt((int) rawValue);
                case Long:
                    return JavaConstant.forLong(rawValue);
                case Float:
                    return JavaConstant.forFloat(Float.intBitsToFloat((int) rawValue));
                case Double:
                    return JavaConstant.forDouble(Double.longBitsToDouble(rawValue));
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (array.getKind() != Kind.Object || array.isNull()) {
            return null;
        }
        Object a = HotSpotObjectConstantImpl.asObject(array);

        if (index < 0 || index >= Array.getLength(a)) {
            return null;
        }

        if (a instanceof Object[]) {
            return HotSpotObjectConstantImpl.forObject(((Object[]) a)[index]);
        } else {
            return JavaConstant.forBoxedPrimitive(Array.get(a, index));
        }
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        if (!source.getKind().isPrimitive()) {
            return null;
        }
        return HotSpotObjectConstantImpl.forObject(source.asBoxedPrimitive());
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        if (!source.getKind().isObject()) {
            return null;
        }
        return JavaConstant.forBoxedPrimitive(HotSpotObjectConstantImpl.asObject(source));
    }

    @Override
    public ResolvedJavaType asJavaType(JavaConstant constant) {
        if (constant instanceof HotSpotObjectConstant) {
            Object obj = HotSpotObjectConstantImpl.asObject(constant);
            if (obj instanceof Class) {
                return runtime.getHostProviders().getMetaAccess().lookupJavaType((Class<?>) obj);
            }
        }
        if (constant instanceof HotSpotMetaspaceConstant) {
            Object obj = HotSpotMetaspaceConstantImpl.getMetaspaceObject(constant);
            if (obj instanceof HotSpotResolvedObjectTypeImpl) {
                return (ResolvedJavaType) obj;
            }
        }
        return null;
    }
}
