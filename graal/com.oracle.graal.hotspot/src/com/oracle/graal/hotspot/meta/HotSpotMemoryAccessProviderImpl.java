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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;

/**
 * HotSpot implementation of {@link MemoryAccessProvider}.
 */
public class HotSpotMemoryAccessProviderImpl implements HotSpotMemoryAccessProvider, HotSpotProxified {

    protected final HotSpotGraalRuntimeProvider runtime;

    public HotSpotMemoryAccessProviderImpl(HotSpotGraalRuntimeProvider runtime) {
        this.runtime = runtime;
    }

    private static Object asObject(Constant base) {
        if (base instanceof HotSpotObjectConstantImpl) {
            return ((HotSpotObjectConstantImpl) base).object();
        } else {
            return null;
        }
    }

    private static long asRawPointer(Constant base) {
        if (base instanceof HotSpotMetaspaceConstant) {
            return ((HotSpotMetaspaceConstant) base).rawValue();
        } else if (base instanceof PrimitiveConstant) {
            PrimitiveConstant prim = (PrimitiveConstant) base;
            if (prim.getKind().isNumericInteger()) {
                return prim.asLong();
            }
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    private static long readRawValue(Constant baseConstant, long displacement, int bits) {
        Object base = asObject(baseConstant);
        if (base != null) {
            switch (bits) {
                case 8:
                    return unsafe.getByte(base, displacement);
                case 16:
                    return unsafe.getShort(base, displacement);
                case 32:
                    return unsafe.getInt(base, displacement);
                case 64:
                    return unsafe.getLong(base, displacement);
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            long pointer = asRawPointer(baseConstant);
            switch (bits) {
                case 8:
                    return unsafe.getByte(pointer + displacement);
                case 16:
                    return unsafe.getShort(pointer + displacement);
                case 32:
                    return unsafe.getInt(pointer + displacement);
                case 64:
                    return unsafe.getLong(pointer + displacement);
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    private boolean verifyReadRawObject(Object expected, Constant base, long displacement, boolean compressed) {
        if (compressed == runtime.getConfig().useCompressedOops) {
            Object obj = asObject(base);
            if (obj != null) {
                assert expected == unsafe.getObject(obj, displacement) : "readUnsafeOop doesn't agree with unsafe.getObject";
            }
        }
        if (base instanceof HotSpotMetaspaceConstant) {
            Object metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl) {
                if (displacement == runtime.getConfig().classMirrorOffset) {
                    assert expected == ((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror();
                } else if (displacement == runtime.getConfig().arrayKlassComponentMirrorOffset) {
                    assert expected == ((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror().getComponentType();
                }
            }
        }
        return true;
    }

    private Object readRawObject(Constant baseConstant, long initialDisplacement, boolean compressed) {
        long displacement = initialDisplacement;

        Object base = asObject(baseConstant);
        if (base == null) {
            displacement += asRawPointer(baseConstant);
        }

        Object ret = runtime.getCompilerToVM().readUnsafeOop(base, displacement, compressed);
        assert verifyReadRawObject(ret, baseConstant, initialDisplacement, compressed);

        return ret;
    }

    @Override
    public JavaConstant readUnsafeConstant(Kind kind, JavaConstant baseConstant, long displacement) {
        if (kind == Kind.Object) {
            Object o = readRawObject(baseConstant, displacement, runtime.getConfig().useCompressedOops);
            return HotSpotObjectConstantImpl.forObject(o);
        } else {
            return readPrimitiveConstant(kind, baseConstant, displacement, kind.getByteCount() * 8);
        }
    }

    @Override
    public JavaConstant readPrimitiveConstant(Kind kind, Constant baseConstant, long initialDisplacement, int bits) {
        try {
            long rawValue = readRawValue(baseConstant, initialDisplacement, bits);
            switch (kind) {
                case Boolean:
                    return JavaConstant.forBoolean(rawValue != 0);
                case Byte:
                    return JavaConstant.forByte((byte) rawValue);
                case Char:
                    return JavaConstant.forChar((char) rawValue);
                case Short:
                    return JavaConstant.forShort((short) rawValue);
                case Int:
                    return JavaConstant.forInt((int) rawValue);
                case Long:
                    return JavaConstant.forLong(rawValue);
                case Float:
                    return JavaConstant.forFloat(Float.intBitsToFloat((int) rawValue));
                case Double:
                    return JavaConstant.forDouble(Double.longBitsToDouble(rawValue));
                default:
                    throw GraalInternalError.shouldNotReachHere("unsupported kind: " + kind);
            }
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public JavaConstant readObjectConstant(Constant base, long displacement) {
        return HotSpotObjectConstantImpl.forObject(readRawObject(base, displacement, false));
    }

    @Override
    public JavaConstant readNarrowOopConstant(Constant base, long displacement, CompressEncoding encoding) {
        assert encoding.equals(runtime.getConfig().getOopEncoding()) : "unexpected oop encoding: " + encoding + " != " + runtime.getConfig().getOopEncoding();
        return HotSpotObjectConstantImpl.forObject(readRawObject(base, displacement, true), true);
    }

    @Override
    public Constant readKlassPointerConstant(Constant base, long displacement) {
        long klass = readRawValue(base, displacement, runtime.getTarget().wordSize * 8);
        if (klass == 0) {
            return JavaConstant.NULL_POINTER;
        }
        HotSpotResolvedObjectType metaKlass = HotSpotResolvedObjectTypeImpl.fromMetaspaceKlass(klass);
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(runtime.getTarget().wordKind, klass, metaKlass, false);
    }

    @Override
    public Constant readNarrowKlassPointerConstant(Constant base, long displacement, CompressEncoding encoding) {
        int compressed = (int) readRawValue(base, displacement, 32);
        long klass = encoding.uncompress(compressed);
        if (klass == 0) {
            return HotSpotCompressedNullConstant.COMPRESSED_NULL;
        }
        HotSpotResolvedObjectType metaKlass = HotSpotResolvedObjectTypeImpl.fromMetaspaceKlass(klass);
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(Kind.Int, compressed, metaKlass, true);
    }

    @Override
    public Constant readMethodPointerConstant(Constant base, long displacement) {
        long method = readRawValue(base, displacement, runtime.getTarget().wordSize * 8);
        HotSpotResolvedJavaMethod metaMethod = HotSpotResolvedJavaMethodImpl.fromMetaspace(method);
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(runtime.getTarget().wordKind, method, metaMethod, false);
    }
}
