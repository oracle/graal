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
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;

/**
 * HotSpot implementation of {@link MemoryAccessProvider}.
 */
public class HotSpotMemoryAccessProviderImpl implements HotSpotMemoryAccessProvider, Remote {

    protected final HotSpotGraalRuntimeProvider runtime;

    public HotSpotMemoryAccessProviderImpl(HotSpotGraalRuntimeProvider runtime) {
        this.runtime = runtime;
    }

    private static long readRawValue(Constant baseConstant, long initialDisplacement, int bits) {
        Object base;
        long displacement;
        if (baseConstant instanceof JavaConstant) {
            JavaConstant javaConstant = (JavaConstant) baseConstant;
            if (javaConstant instanceof HotSpotObjectConstantImpl) {
                base = ((HotSpotObjectConstantImpl) javaConstant).object();
                displacement = initialDisplacement;
            } else if (javaConstant.getKind().isNumericInteger()) {
                long baseLong = javaConstant.asLong();
                assert baseLong != 0;
                displacement = initialDisplacement + baseLong;
                base = null;
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
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
        return rawValue;
    }

    private Object readRawObject(Constant baseConstant, long displacement, boolean compressed) {
        if (baseConstant instanceof HotSpotObjectConstantImpl) {
            assert compressed == runtime.getConfig().useCompressedOops;
            return unsafe.getObject(((HotSpotObjectConstantImpl) baseConstant).object(), displacement);
        } else if (baseConstant instanceof HotSpotMetaspaceConstant) {
            Object metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(baseConstant);
            if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl) {
                assert !compressed : "unexpected compressed read from Klass*";
                if (displacement == runtime.getConfig().classMirrorOffset) {
                    return ((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror();
                } else if (displacement == runtime.getConfig().arrayKlassComponentMirrorOffset) {
                    return ((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror().getComponentType();
                } else if (displacement == runtime.getConfig().instanceKlassNodeClassOffset) {
                    return NodeClass.get(((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror());
                }
            }
            throw GraalInternalError.shouldNotReachHere("read from unknown Klass* offset " + displacement);
        } else {
            throw GraalInternalError.shouldNotReachHere("unexpected base pointer: " + (baseConstant == null ? "null" : baseConstant.toString()));
        }
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
        HotSpotResolvedObjectType metaKlass = HotSpotResolvedObjectTypeImpl.fromMetaspaceKlass(klass);
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(runtime.getTarget().wordKind, klass, metaKlass, false);
    }

    @Override
    public Constant readNarrowKlassPointerConstant(Constant base, long displacement, CompressEncoding encoding) {
        int compressed = (int) readRawValue(base, displacement, 32);
        long klass = encoding.uncompress(compressed);
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
