/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic;

import java.util.*;

import com.oracle.truffle.api.object.*;
import com.oracle.truffle.object.*;
import com.oracle.truffle.object.ShapeImpl.BaseAllocator;

class DefaultStrategy implements LayoutStrategy {
    public boolean updateShape(DynamicObject object) {
        assert object.getShape().isValid();
        return false;
    }

    public Shape returnCached(Shape newShape) {
        assert newShape.isValid();
        return newShape;
    }

    private static boolean assertLocationInRange(Shape shape, Location location) {
        BasicLayout layout = (BasicLayout) shape.getLayout();
        assert (shape.getPrimitiveFieldSize() + ((LocationImpl) location).primitiveFieldCount() <= layout.getPrimitiveFieldCount());
        assert (shape.getObjectFieldSize() + ((LocationImpl) location).objectFieldCount() <= layout.getObjectFieldCount());
        return true;
    }

    public Shape ensureSpace(Shape shape, Location location) {
        Objects.requireNonNull(location);
        assert assertLocationInRange(shape, location);
        return shape;
    }

    public boolean isAutoExtArray() {
        return false;
    }

    public Property generalizeProperty(DynamicObject object, Property oldProperty, Object value) {
        Shape oldShape = object.getShape();
        Location oldLocation = oldProperty.getLocation();
        Location newLocation = ((BasicAllocator) oldShape.allocator()).locationForValueUpcast(value, oldLocation);
        Property newProperty = oldProperty.relocate(newLocation);
        Shape newShape = oldShape.replaceProperty(oldProperty, newProperty);
        newProperty.setSafe(object, value, oldShape, newShape);
        return newProperty;
    }

    public Property generalizeProperty(DynamicObject object, Property oldProperty, Object value, Shape currentShape, Shape oldNewShape) {
        Location oldLocation = oldProperty.getLocation();
        Location newLocation = ((BasicAllocator) currentShape.allocator()).locationForValueUpcast(value, oldLocation);
        Property newProperty = oldProperty.relocate(newLocation);
        Shape newShape = oldNewShape.replaceProperty(oldProperty, newProperty);
        newProperty.setSafe(object, value, currentShape, newShape);
        return newProperty;
    }

    public BaseAllocator createAllocator(Shape shape) {
        return new DefaultAllocatorImpl((ShapeImpl) shape);
    }

    public BaseAllocator createAllocator(Layout layout) {
        return new DefaultAllocatorImpl((LayoutImpl) layout);
    }

    public static class DefaultAllocatorImpl extends BasicAllocator {
        protected DefaultAllocatorImpl(LayoutImpl layout) {
            super(layout);
        }

        protected DefaultAllocatorImpl(ShapeImpl shape) {
            super(shape);
        }

        @Override
        public Location locationForValue(Object value, boolean useFinal, boolean nonNull) {
            return super.newDualLocationForValue(value);
        }

        @Override
        public Location declaredLocation(Object value) {
            return super.newDeclaredDualLocation(value);
        }
    }
}
