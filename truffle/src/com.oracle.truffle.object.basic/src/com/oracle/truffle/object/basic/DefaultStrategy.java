/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic;

import java.util.Objects;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.object.LayoutImpl;
import com.oracle.truffle.object.LayoutStrategy;
import com.oracle.truffle.object.LocationImpl;
import com.oracle.truffle.object.ShapeImpl;
import com.oracle.truffle.object.ShapeImpl.BaseAllocator;

class DefaultStrategy extends LayoutStrategy {
    @Override
    public boolean updateShape(DynamicObject object) {
        assert object.getShape().isValid();
        return false;
    }

    @Override
    public ShapeImpl ensureValid(ShapeImpl newShape) {
        assert newShape.isValid();
        return newShape;
    }

    private static boolean assertLocationInRange(ShapeImpl shape, Location location) {
        BasicLayout layout = (BasicLayout) shape.getLayout();
        assert (shape.getPrimitiveFieldSize() + ((LocationImpl) location).primitiveFieldCount() <= layout.getPrimitiveFieldCount());
        assert (shape.getObjectFieldSize() + ((LocationImpl) location).objectFieldCount() <= layout.getObjectFieldCount());
        return true;
    }

    @Override
    public ShapeImpl ensureSpace(ShapeImpl shape, Location location) {
        Objects.requireNonNull(location);
        assert assertLocationInRange(shape, location);
        return shape;
    }

    @Override
    public BaseAllocator createAllocator(ShapeImpl shape) {
        return new BasicAllocator(shape);
    }

    @Override
    public BaseAllocator createAllocator(LayoutImpl layout) {
        return new BasicAllocator(layout);
    }
}
