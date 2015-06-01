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
import com.oracle.truffle.object.Locations.DeclaredLocation;

/**
 * Property objects represent the mapping between property identifiers (keys) and storage locations.
 * Optionally, properties may have metadata attached to them.
 */
public class PropertyImpl extends Property {
    private final Object key;
    private final Location location;
    private final int flags;
    private final boolean shadow;
    private final boolean relocatable;

    /**
     * Generic, usual-case constructor for properties storing at least a name.
     *
     * @param key the name of the property
     * @param location the storage location used to access the property
     * @param flags property flags (optional)
     */
    protected PropertyImpl(Object key, Location location, int flags, boolean shadow, boolean relocatable) {
        this.key = Objects.requireNonNull(key);
        this.location = Objects.requireNonNull(location);
        this.flags = flags;
        this.shadow = shadow;
        this.relocatable = relocatable;
    }

    public PropertyImpl(Object name, Location location, int flags) {
        this(name, location, flags, false, true);
    }

    @Override
    public final Object getKey() {
        return key;
    }

    @Override
    public int getFlags() {
        return flags;
    }

    @Override
    public Property relocate(Location newLocation) {
        if (!getLocation().equals(newLocation) && relocatable) {
            return construct(key, newLocation, flags);
        }
        return this;
    }

    @Override
    public final Object get(DynamicObject store, Shape shape) {
        return getLocation().get(store, shape);
    }

    @Override
    public final Object get(DynamicObject store, boolean condition) {
        return getLocation().get(store, condition);
    }

    @Override
    public final void setInternal(DynamicObject store, Object value) {
        try {
            ((LocationImpl) getLocation()).setInternal(store, value);
        } catch (IncompatibleLocationException e) {
            throw new IllegalStateException();
        }
    }

    @Override
    public final void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
        assert shape == null || store.getShape() == shape : "wrong shape";
        getLocation().set(store, value, shape);
    }

    @Override
    public final void setSafe(DynamicObject store, Object value, Shape shape) {
        assert shape == null || store.getShape() == shape : "wrong shape";
        try {
            getLocation().set(store, value, shape);
        } catch (IncompatibleLocationException | FinalLocationException ex) {
            throw new IllegalStateException();
        }
    }

    @Override
    public final void setGeneric(DynamicObject store, Object value, Shape shape) {
        assert shape == null || store.getShape() == shape : "wrong shape";
        try {
            set(store, value, shape);
        } catch (IncompatibleLocationException | FinalLocationException ex) {
            setSlowCase(store, value);
        }
    }

    @Override
    public final void set(DynamicObject store, Object value, Shape oldShape, Shape newShape) throws IncompatibleLocationException {
        assert store.getShape() == oldShape : "wrong shape";
        assert newShape.isValid();
        assert getLocation() != null;
        getLocation().set(store, value, oldShape, newShape);
    }

    @Override
    public final void setSafe(DynamicObject store, Object value, Shape oldShape, Shape newShape) {
        assert store.getShape() == oldShape : "wrong old shape";
        assert newShape.isValid();
        assert getLocation() != null;
        try {
            getLocation().set(store, value, oldShape, newShape);
        } catch (IncompatibleLocationException ex) {
            throw new IllegalStateException();
        }
    }

    @Override
    public final void setGeneric(DynamicObject store, Object value, Shape oldShape, Shape newShape) {
        assert store.getShape() == oldShape : "wrong old shape";
        assert newShape.isValid();
        assert getLocation() != null;
        try {
            getLocation().set(store, value, oldShape, newShape);
        } catch (IncompatibleLocationException ex) {
            setWithShapeSlowCase(store, value, oldShape, newShape);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        PropertyImpl other = (PropertyImpl) obj;
        return key.equals(other.key) && location.equals(other.location) && flags == other.flags && shadow == other.shadow && relocatable == other.relocatable;
    }

    @Override
    public boolean isSame(Property obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        PropertyImpl other = (PropertyImpl) obj;
        return key.equals(other.key) && flags == other.flags;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + getClass().hashCode();
        result = prime * result + key.hashCode();
        result = prime * result + location.hashCode();
        result = prime * result + flags;
        return result;
    }

    @Override
    public String toString() {
        return "\"" + key + "\"" + ":" + location;
    }

    @Override
    public final Location getLocation() {
        return location;
    }

    private void setSlowCase(DynamicObject store, Object value) {
        if (getLocation() instanceof DeclaredLocation) {
            setDeclaredLocation(store, value);
        } else {
            generalize(store, value);
        }
    }

    private void setDeclaredLocation(DynamicObject store, Object value) {
        store.updateShape();
        Shape oldShape = store.getShape();
        Shape newShape = oldShape.addProperty(this.relocateShadow(oldShape.allocator().locationForValue(value, EnumSet.of(LocationModifier.Final, LocationModifier.NonNull))));
        store.updateShape();
        newShape.getLastProperty().setGeneric(store, value, oldShape, newShape);
    }

    private Property generalize(DynamicObject store, Object value) {
        return ((LayoutImpl) store.getShape().getLayout()).getStrategy().generalizeProperty(store, this, value);
    }

    private void setWithShapeSlowCase(DynamicObject store, Object value, Shape oldShape, Shape newShape) {
        ((LayoutImpl) store.getShape().getLayout()).getStrategy().generalizeProperty(store, this, value, oldShape, newShape);
    }

    @Override
    public final boolean isHidden() {
        return key instanceof HiddenKey;
    }

    @Override
    public final boolean isShadow() {
        return shadow;
    }

    private Property relocateShadow(Location newLocation) {
        assert !isShadow() && getLocation() instanceof DeclaredLocation && relocatable;
        return new PropertyImpl(key, newLocation, flags, true, relocatable);
    }

    @SuppressWarnings("hiding")
    protected Property construct(Object name, Location location, int flags) {
        return new PropertyImpl(name, location, flags, shadow, relocatable);
    }

    @Override
    public Property copyWithFlags(int newFlags) {
        return construct(key, location, newFlags);
    }

    @Override
    public Property copyWithRelocatable(boolean newRelocatable) {
        if (this.relocatable != newRelocatable) {
            return new PropertyImpl(key, location, flags, shadow, newRelocatable);
        }
        return this;
    }
}
