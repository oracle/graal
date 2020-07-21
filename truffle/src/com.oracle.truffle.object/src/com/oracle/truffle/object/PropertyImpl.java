/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
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
@SuppressWarnings("deprecation")
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
        CompilerAsserts.neverPartOfCompilation();
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
