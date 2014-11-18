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

import java.util.*;

import com.oracle.truffle.api.object.*;
import com.oracle.truffle.api.object.Shape.Allocator;
import com.oracle.truffle.object.LocationImpl.EffectivelyFinalLocation;
import com.oracle.truffle.object.LocationImpl.TypedObjectLocation;
import com.oracle.truffle.object.Locations.ConstantLocation;
import com.oracle.truffle.object.Locations.DeclaredLocation;
import com.oracle.truffle.object.Locations.DualLocation;
import com.oracle.truffle.object.Locations.ValueLocation;
import com.oracle.truffle.object.ShapeImpl.BaseAllocator;

public abstract class LayoutImpl extends Layout {
    private static final int INT_TO_DOUBLE_FLAG = 1;
    private static final int INT_TO_LONG_FLAG = 2;

    private final LayoutStrategy strategy;
    private final Class<? extends DynamicObject> clazz;
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
    public final Shape createShape(ObjectType operations, Object sharedData) {
        return createShape(operations, sharedData, 0);
    }

    @Override
    public final Shape createShape(ObjectType operations) {
        return createShape(operations, null);
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

    protected boolean isLocationAssignableFrom(Location destination, Location source) {
        LayoutImpl layout = this;
        if (destination.isFinal()) {
            // allowed Final<X>Location => Final<X>Location
            // allowed FinalIntLocation => Final{Int,Double}Location
            // allowed: Final{Int,Double,TypedObject}Location => FinalObjectLocation
            if (!source.isFinal()) {
                return false;
            }
        }

        if (destination instanceof IntLocation) {
            return (source instanceof IntLocation);
        } else if (destination instanceof DoubleLocation) {
            return (source instanceof DoubleLocation || (layout.isAllowedIntToDouble() && source instanceof IntLocation));
        } else if (destination instanceof LongLocation) {
            return (source instanceof LongLocation || (layout.isAllowedIntToLong() && source instanceof IntLocation));
        } else if (destination instanceof BooleanLocation) {
            return (source instanceof BooleanLocation);
        } else if (destination instanceof TypedObjectLocation) {
            return source instanceof TypedObjectLocation && ((TypedObjectLocation<?>) destination).getType().isAssignableFrom(((TypedObjectLocation<?>) source).getType());
        } else if (destination instanceof ValueLocation) {
            return false;
        } else {
            assert destination instanceof ObjectLocation || destination instanceof DualLocation;
            return true;
        }
    }

    protected Location existingLocationForValue(Object value, Location oldLocation, Shape oldShape) {
        assert oldShape.getLayout() == this;
        Location newLocation;
        if (oldLocation instanceof IntLocation && value instanceof Integer) {
            newLocation = oldLocation;
        } else if (oldLocation instanceof DoubleLocation && (value instanceof Double || this.isAllowedIntToDouble() && value instanceof Integer)) {
            newLocation = oldLocation;
        } else if (oldLocation instanceof LongLocation && (value instanceof Long || this.isAllowedIntToLong() && value instanceof Long)) {
            newLocation = oldLocation;
        } else if (oldLocation instanceof DeclaredLocation) {
            return oldShape.allocator().locationForValue(value, EnumSet.of(LocationModifier.Final, LocationModifier.NonNull));
        } else if (oldLocation instanceof ConstantLocation) {
            return LocationImpl.valueEquals(oldLocation.get(null, false), value) ? oldLocation : new Locations.ConstantLocation(value);
        } else if (oldLocation instanceof TypedObjectLocation && !((TypedObjectLocation<?>) oldLocation).getType().isAssignableFrom(value.getClass())) {
            newLocation = (((TypedObjectLocation<?>) oldLocation).toUntypedLocation());
        } else if (oldLocation instanceof DualLocation) {
            if (oldLocation.canStore(value)) {
                newLocation = oldLocation;
            } else {
                newLocation = ((BaseAllocator) oldShape.allocator()).locationForValueUpcast(value, oldLocation);
            }
        } else if (oldLocation instanceof ObjectLocation) {
            newLocation = oldLocation;
        } else {
            return oldShape.allocator().locationForValue(value, EnumSet.of(LocationModifier.NonNull));
        }
        if (newLocation instanceof EffectivelyFinalLocation) {
            newLocation = ((EffectivelyFinalLocation<?>) newLocation).toNonFinalLocation();
        }
        return newLocation;
    }

    /**
     * Is this property an upcast of the other property?
     *
     * @param other the property being compared to
     * @return true if this is a upcast of the other property, false otherwise
     */
    public boolean isPropertyUpcastOf(Property thiz, Property other) {
        if (thiz.getLocation() != null && other.getLocation() != null && other.getKey().equals(thiz.getKey()) && other.getFlags() == thiz.getFlags()) {
            if (isLocationAssignableFrom(thiz.getLocation(), other.getLocation())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public abstract Allocator createAllocator();

    public LayoutStrategy getStrategy() {
        return strategy;
    }
}
