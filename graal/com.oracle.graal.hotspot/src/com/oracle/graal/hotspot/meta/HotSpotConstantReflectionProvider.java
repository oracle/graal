/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
    public Integer readArrayLength(Constant array) {
        if (array.getKind() != Kind.Object || array.isNull() || !HotSpotObjectConstant.asObject(array).getClass().isArray()) {
            return null;
        }
        return Array.getLength(HotSpotObjectConstant.asObject(array));
    }

    @Override
    public Constant readUnsafeConstant(Kind kind, Constant baseConstant, long initialDisplacement) {
        Object base;
        long displacement;
        if (baseConstant.getKind() == Kind.Object) {
            base = HotSpotObjectConstant.asObject(baseConstant);
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
                return Constant.forBoolean(base == null ? unsafe.getByte(displacement) != 0 : unsafe.getBoolean(base, displacement));
            case Byte:
                return Constant.forByte(base == null ? unsafe.getByte(displacement) : unsafe.getByte(base, displacement));
            case Char:
                return Constant.forChar(base == null ? unsafe.getChar(displacement) : unsafe.getChar(base, displacement));
            case Short:
                return Constant.forShort(base == null ? unsafe.getShort(displacement) : unsafe.getShort(base, displacement));
            case Int:
                return Constant.forInt(base == null ? unsafe.getInt(displacement) : unsafe.getInt(base, displacement));
            case Long:
                if (displacement == config().hubOffset && runtime.getConfig().useCompressedClassPointers) {
                    if (base == null) {
                        throw new GraalInternalError("Base of object must not be null");
                    } else {
                        return Constant.forLong(runtime.getCompilerToVM().readUnsafeKlassPointer(base));
                    }
                } else {
                    return Constant.forLong(base == null ? unsafe.getLong(displacement) : unsafe.getLong(base, displacement));
                }
            case Float:
                return Constant.forFloat(base == null ? unsafe.getFloat(displacement) : unsafe.getFloat(base, displacement));
            case Double:
                return Constant.forDouble(base == null ? unsafe.getDouble(displacement) : unsafe.getDouble(base, displacement));
            case Object: {
                Object o = null;
                if (baseConstant.getKind() == Kind.Object) {
                    o = unsafe.getObject(base, displacement);
                } else if (baseConstant instanceof HotSpotMetaspaceConstant) {
                    Object metaspaceObject = HotSpotMetaspaceConstant.getMetaspaceObject(baseConstant);
                    if (metaspaceObject instanceof HotSpotResolvedObjectType && initialDisplacement == runtime.getConfig().classMirrorOffset) {
                        o = ((HotSpotResolvedObjectType) metaspaceObject).mirror();
                    } else {
                        throw GraalInternalError.shouldNotReachHere();
                    }
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
                return HotSpotObjectConstant.forObject(o);
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Constant readRawConstant(Kind kind, Constant baseConstant, long initialDisplacement, int bits) {
        Object base;
        long displacement;
        if (baseConstant.getKind() == Kind.Object) {
            base = HotSpotObjectConstant.asObject(baseConstant);
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
                return HotSpotMetaspaceConstant.forMetaspaceObject(kind, rawValue, HotSpotResolvedObjectType.fromMetaspaceKlass(klassPointer));
            } else {
                assert bits == 64 && kind == Kind.Long;
                return HotSpotMetaspaceConstant.forMetaspaceObject(kind, rawValue, HotSpotResolvedObjectType.fromMetaspaceKlass(rawValue));
            }
        } else {
            switch (kind) {
                case Int:
                    return Constant.forInt((int) rawValue);
                case Long:
                    return Constant.forLong(rawValue);
                case Float:
                    return Constant.forFloat(Float.intBitsToFloat((int) rawValue));
                case Double:
                    return Constant.forDouble(Double.longBitsToDouble(rawValue));
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    @Override
    public Constant readArrayElement(Constant array, int index) {
        if (array.getKind() != Kind.Object || array.isNull()) {
            return null;
        }
        Object a = HotSpotObjectConstant.asObject(array);

        if (index < 0 || index >= Array.getLength(a)) {
            return null;
        }

        if (a instanceof Object[]) {
            return HotSpotObjectConstant.forObject(((Object[]) a)[index]);
        } else {
            return Constant.forBoxedPrimitive(Array.get(a, index));
        }
    }

    @Override
    public Constant boxPrimitive(Constant source) {
        if (!source.getKind().isPrimitive()) {
            return null;
        }
        return HotSpotObjectConstant.forObject(source.asBoxedPrimitive());
    }

    @Override
    public Constant unboxPrimitive(Constant source) {
        if (!source.getKind().isObject()) {
            return null;
        }
        return Constant.forBoxedPrimitive(HotSpotObjectConstant.asObject(source));
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        if (constant.getKind() == Kind.Object) {
            Object obj = HotSpotObjectConstant.asObject(constant);
            if (obj instanceof Class) {
                return runtime.getHostProviders().getMetaAccess().lookupJavaType((Class<?>) obj);
            }
        }
        return null;
    }
}
