/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import java.util.Objects;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

/**
 * Property objects represent the mapping between property identifiers (keys) and storage locations.
 * Optionally, properties may have metadata attached to them.
 *
 * @since 0.17 or earlier
 */
public class PropertyImpl extends Property {
    private final Object key;
    private final Location location;
    private final int flags;
    private final boolean relocatable;

    /**
     * Generic, usual-case constructor for properties storing at least a name.
     *
     * @param key the name of the property
     * @param location the storage location used to access the property
     * @param flags property flags (optional)
     * @since 0.17 or earlier
     */
    PropertyImpl(Object key, Location location, int flags, boolean relocatable) {
        this.key = Objects.requireNonNull(key);
        this.location = Objects.requireNonNull(location);
        this.flags = flags;
        this.relocatable = relocatable;
    }

    /** @since 0.17 or earlier */
    public PropertyImpl(Object name, Location location, int flags) {
        this(name, location, flags, true);
    }

    /** @since 0.17 or earlier */
    @Override
    public final Object getKey() {
        return key;
    }

    /** @since 0.17 or earlier */
    @Override
    public int getFlags() {
        return flags;
    }

    /** @since 0.17 or earlier */
    @Override
    public Property relocate(Location newLocation) {
        if (!getLocation().equals(newLocation) && relocatable) {
            return construct(key, newLocation, flags);
        }
        return this;
    }

    /** @since 0.17 or earlier */
    @Override
    public final Object get(DynamicObject store, Shape shape) {
        return getLocation().get(store, shape);
    }

    /** @since 0.17 or earlier */
    @Override
    public final Object get(DynamicObject store, boolean condition) {
        return getLocation().get(store, condition);
    }

    /** @since 0.17 or earlier */
    @Override
    public final void setInternal(DynamicObject store, Object value) {
        try {
            ((LocationImpl) getLocation()).setInternal(store, value);
        } catch (IncompatibleLocationException e) {
            throw new IllegalStateException();
        }
    }

    private static boolean verifyShapeParameter(DynamicObject store, Shape shape) {
        assert shape == null || store.getShape() == shape : "wrong shape";
        return true;
    }

    /** @since 0.17 or earlier */
    @Override
    public final void set(DynamicObject store, Object value, Shape shape) throws IncompatibleLocationException, FinalLocationException {
        assert verifyShapeParameter(store, shape);
        getLocation().set(store, value, shape);
    }

    /** @since 0.17 or earlier */
    @Override
    public final void setSafe(DynamicObject store, Object value, Shape shape) {
        assert verifyShapeParameter(store, shape);
        try {
            getLocation().set(store, value, shape);
        } catch (IncompatibleLocationException | FinalLocationException ex) {
            throw new IllegalStateException();
        }
    }

    /** @since 0.17 or earlier */
    @Override
    public final void setGeneric(DynamicObject store, Object value, Shape shape) {
        assert verifyShapeParameter(store, shape);
        try {
            set(store, value, shape);
        } catch (IncompatibleLocationException | FinalLocationException ex) {
            setSlowCase(store, value);
        }
    }

    private static boolean verifyShapeParameters(DynamicObject store, Shape oldShape, Shape newShape) {
        assert store.getShape() == oldShape : "wrong shape";
        assert newShape.isValid() : "invalid shape";
        return true;
    }

    /** @since 0.17 or earlier */
    @Override
    public final void set(DynamicObject store, Object value, Shape oldShape, Shape newShape) throws IncompatibleLocationException {
        assert verifyShapeParameters(store, oldShape, newShape);
        getLocation().set(store, value, oldShape, newShape);
    }

    /** @since 0.17 or earlier */
    @Override
    public final void setSafe(DynamicObject store, Object value, Shape oldShape, Shape newShape) {
        assert verifyShapeParameters(store, oldShape, newShape);
        try {
            getLocation().set(store, value, oldShape, newShape);
        } catch (IncompatibleLocationException ex) {
            throw new IllegalStateException();
        }
    }

    /** @since 0.17 or earlier */
    @Override
    public final void setGeneric(DynamicObject store, Object value, Shape oldShape, Shape newShape) {
        assert verifyShapeParameters(store, oldShape, newShape);
        try {
            getLocation().set(store, value, oldShape, newShape);
        } catch (IncompatibleLocationException ex) {
            setWithShapeSlowCase(store, value, oldShape, newShape);
        }
    }

    /** @since 0.17 or earlier */
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
        return key.equals(other.key) && flags == other.flags && relocatable == other.relocatable && location.equals(other.location);
    }

    /** @since 0.17 or earlier */
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

    /** @since 0.17 or earlier */
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

    /** @since 0.17 or earlier */
    @Override
    public String toString() {
        return "\"" + key + "\"" + ":" + location + (flags == 0 ? "" : "%" + flags);
    }

    /** @since 0.17 or earlier */
    @Override
    public final Location getLocation() {
        return location;
    }

    private void setSlowCase(DynamicObject store, Object value) {
        ShapeImpl oldShape = (ShapeImpl) store.getShape();
        oldShape.getLayout().getStrategy().propertySetFallback(this, store, value, oldShape);
    }

    private void setWithShapeSlowCase(DynamicObject store, Object value, Shape currentShape, Shape nextShape) {
        ShapeImpl oldShape = (ShapeImpl) currentShape;
        oldShape.getLayout().getStrategy().propertySetWithShapeFallback(this, store, value, oldShape, (ShapeImpl) nextShape);
    }

    /** @since 0.17 or earlier */
    @Override
    public final boolean isHidden() {
        return key instanceof HiddenKey;
    }

    /** @since 0.17 or earlier */
    @SuppressWarnings("hiding")
    protected Property construct(Object name, Location location, int flags) {
        return new PropertyImpl(name, location, flags, relocatable);
    }

    /** @since 0.17 or earlier */
    @Override
    public Property copyWithFlags(int newFlags) {
        return construct(key, location, newFlags);
    }

    /** @since 0.17 or earlier */
    @Override
    public Property copyWithRelocatable(boolean newRelocatable) {
        if (this.relocatable != newRelocatable) {
            return new PropertyImpl(key, location, flags, newRelocatable);
        }
        return this;
    }
}
