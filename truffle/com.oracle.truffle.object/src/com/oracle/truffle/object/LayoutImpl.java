/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import java.util.EnumSet;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.object.Shape.Allocator;

public abstract class LayoutImpl extends Layout {
    private static final int INT_TO_DOUBLE_FLAG = 1;
    private static final int INT_TO_LONG_FLAG = 2;

    protected final LayoutStrategy strategy;
    protected final Class<? extends DynamicObject> clazz;
    private final int allowedImplicitCasts;

    protected LayoutImpl(EnumSet<ImplicitCast> allowedImplicitCasts, Class<? extends DynamicObjectImpl> clazz, LayoutStrategy strategy) {
        this.strategy = strategy;
        this.clazz = clazz;

        this.allowedImplicitCasts = (allowedImplicitCasts.contains(ImplicitCast.IntToDouble) ? INT_TO_DOUBLE_FLAG : 0) | (allowedImplicitCasts.contains(ImplicitCast.IntToLong) ? INT_TO_LONG_FLAG : 0);
    }

    @Override
    public abstract DynamicObject newInstance(Shape shape);

    @Override
    public Class<? extends DynamicObject> getType() {
        return clazz;
    }

    @Override
    public final Shape createShape(ObjectType objectType, Object sharedData) {
        return createShape(objectType, sharedData, 0);
    }

    @Override
    public final Shape createShape(ObjectType objectType) {
        return createShape(objectType, null);
    }

    public boolean isAllowedIntToDouble() {
        return (allowedImplicitCasts & INT_TO_DOUBLE_FLAG) != 0;
    }

    public boolean isAllowedIntToLong() {
        return (allowedImplicitCasts & INT_TO_LONG_FLAG) != 0;
    }

    protected abstract boolean hasObjectExtensionArray();

    protected abstract boolean hasPrimitiveExtensionArray();

    protected abstract int getObjectFieldCount();

    protected abstract int getPrimitiveFieldCount();

    protected abstract Location getObjectArrayLocation();

    protected abstract Location getPrimitiveArrayLocation();

    protected abstract int objectFieldIndex(Location location);

    @Override
    public abstract Allocator createAllocator();

    public LayoutStrategy getStrategy() {
        return strategy;
    }
}
