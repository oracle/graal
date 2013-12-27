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

import static com.oracle.graal.graph.UnsafeAccess.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
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
    public Integer lookupArrayLength(Constant array) {
        if (array.getKind() != Kind.Object || array.isNull() || !array.asObject().getClass().isArray()) {
            return null;
        }
        return Array.getLength(array.asObject());
    }

    @Override
    public Constant readUnsafeConstant(Kind kind, Object base, long displacement, boolean compressible) {
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
                if (compressible) {
                    o = unsafe.getObject(base, displacement);
                } else {
                    o = runtime.getCompilerToVM().readUnsafeUncompressedPointer(base, displacement);
                }
                return Constant.forObject(o);
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
