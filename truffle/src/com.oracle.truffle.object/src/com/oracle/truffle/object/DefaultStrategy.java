/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.object;

import java.util.Objects;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.ShapeImpl.BaseAllocator;

final class DefaultStrategy extends LayoutStrategy {
    static final LayoutStrategy SINGLETON = new DefaultStrategy();

    private DefaultStrategy() {
    }

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
        DefaultLayout layout = (DefaultLayout) shape.getLayout();
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
        return new CoreAllocator(shape);
    }

    @Override
    public BaseAllocator createAllocator(LayoutImpl layout) {
        return new CoreAllocator(layout);
    }

    private static final LocationFactory DEFAULT_LOCATION_FACTORY = createDefaultLocationFactory(0);

    @Override
    protected LocationFactory getDefaultLocationFactory(long putFlags) {
        if (putFlags == 0) {
            return DEFAULT_LOCATION_FACTORY;
        }
        return createDefaultLocationFactory(putFlags);
    }

    private static LocationFactory createDefaultLocationFactory(long putFlags) {
        return new LocationFactory() {
            @Override
            public Location createLocation(Shape shape, Object value) {
                return ((CoreAllocator) ((ShapeImpl) shape).allocator()).locationForValue(value, true, value != null, putFlags);
            }
        };
    }

    @Override
    protected int getLocationOrdinal(Location location) {
        return CoreLocations.getLocationOrdinal(((CoreLocation) location));
    }
}
