/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.Locations.ValueLocation;

public abstract class DynamicObjectImpl extends DynamicObject implements Cloneable {
    private ShapeImpl shape;

    public static final DebugCounter reshapeCount = DebugCounter.create("Reshape count");

    protected DynamicObjectImpl(Shape shape) {
        assert shape instanceof ShapeImpl;
        initialize(shape);
        setShape(shape);

        if (ObjectStorageOptions.Profile) {
            Debug.trackObject(this);
        }
    }

    public Object getTypeIdentifier() {
        return getShape();
    }

    @Override
    public ShapeImpl getShape() {
        return shape;
    }

    protected void setShape(Shape shape) {
        assert shape.getLayout().getType().isInstance(this);
        this.shape = (ShapeImpl) shape;
    }

    protected abstract void initialize(Shape initialShape);

    public final void setShapeAndResize(Shape newShape) {
        setShapeAndResize(getShape(), newShape);
    }

    @Override
    public final void setShapeAndResize(Shape oldShape, Shape newShape) {
        assert getShape() == oldShape : "wrong old shape";
        if (oldShape != newShape) {
            setShape(newShape);
            resizeStore(oldShape, newShape);

            assert checkExtensionArrayInvariants(newShape);
        }
    }

    /**
     * Set shape to an immediate child of the current shape, optionally growing the extension array.
     * Typically this would add a single property. Cannot shrink or grow more than one property at a
     * time.
     *
     * @see #setShapeAndResize(Shape, Shape)
     */
    @Override
    public final void setShapeAndGrow(Shape oldShape, Shape newShape) {
        assert getShape() == oldShape : "wrong old shape";
        if (oldShape != newShape) {
            assert checkSetShape(oldShape, newShape);

            setShape(newShape);
            growStore(oldShape, newShape);

            assert checkExtensionArrayInvariants(newShape);
        }
    }

    /**
     * Simpler version of {@link #resizeStore} when the object is only increasing in size.
     */
    private void growStore(Shape oldShape, Shape newShape) {
        growObjectStore(oldShape, newShape);
        if (((ShapeImpl) newShape).hasPrimitiveArray) {
            growPrimitiveStore(oldShape, newShape);
        }
    }

    protected abstract void growObjectStore(Shape oldShape, Shape newShape);

    protected abstract void growPrimitiveStore(Shape oldShape, Shape newShape);

    private void resizeStore(Shape oldShape, Shape newShape) {
        resizeObjectStore(oldShape, newShape);
        if (((ShapeImpl) newShape).hasPrimitiveArray) {
            resizePrimitiveStore(oldShape, newShape);
        }
    }

    protected abstract void resizePrimitiveStore(Shape oldShape, Shape newShape);

    protected abstract void resizeObjectStore(Shape oldShape, Shape newShape);

    /**
     * Check whether fast transition is valid.
     *
     * @see #setShapeAndGrow
     */
    private boolean checkSetShape(Shape oldShape, Shape newShape) {
        Shape currentShape = getShape();
        assert oldShape != newShape : "Wrong old shape assumption?";
        assert newShape != currentShape : "Redundant shape change? shape=" + currentShape;
        // assert oldShape == currentShape || (oldShape.getLastProperty() == ((EnterpriseLayout)
        // oldShape.getLayout()).getPrimitiveArrayProperty() && oldShape.getParent() ==
        // currentShape) : "Out-of-order shape change?" + "\nparentShape=" + currentShape +
        // "\noldShape=" + oldShape + "\nnewShape=" + newShape;
        return true;
    }

    /**
     * Check whether the extension arrays are in accordance with the description in the shape.
     */
    protected abstract boolean checkExtensionArrayInvariants(Shape newShape);

    @Override
    protected final DynamicObject clone() {
        try {
            return (DynamicObject) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException();
        }
    }

    protected abstract DynamicObject cloneWithShape(Shape currentShape);

    void reshapeAfterDelete(final Shape newShape, final Shape deletedParentShape) {
        DynamicObject original = this.cloneWithShape(getShape());
        setShapeAndResize(newShape);
        copyProperties(original, deletedParentShape);
    }

    public final void copyProperties(DynamicObject fromObject, Shape ancestor) {
        ShapeImpl fromShape = (ShapeImpl) fromObject.getShape();
        ShapeImpl toShape = getShape();
        assert toShape.isRelated(ancestor);
        assert toShape.isValid();
        assert ancestor.isValid();
        PropertyMap ancestorMap = ((ShapeImpl) ancestor).getPropertyMap();
        PropertyMap fromMap = fromShape.getPropertyMap();
        for (PropertyMap toMap = toShape.getPropertyMap(); !toMap.isEmpty() && toMap != ancestorMap; toMap = toMap.getParentMap()) {
            Property toProperty = toMap.getLastProperty();
            Property fromProperty = fromMap.get(toProperty.getKey());

            // copy only if property has a location and it's not the same as the source location
            if (toProperty.getLocation() != null && !(toProperty.getLocation() instanceof ValueLocation) && !toProperty.getLocation().equals(fromProperty.getLocation())) {
                toProperty.setInternal(this, fromProperty.get(fromObject, false));
                assert toShape.isValid();
            }

            if (fromProperty == fromMap.getLastProperty()) {
                // no property is looked up twice, so we can skip over to parent
                fromMap = fromMap.getParentMap();
            }
        }
    }

    @TruffleBoundary
    public boolean changeFlags(Object id, int newFlags) {
        Shape oldShape = getShape();
        Property existing = oldShape.getProperty(id);
        if (existing != null) {
            if (existing.getFlags() != newFlags) {
                Property newProperty = existing.copyWithFlags(newFlags);
                Shape newShape = oldShape.replaceProperty(existing, newProperty);
                this.setShape(newShape);
            }
            return true;
        } else {
            return false;
        }
    }

    public String debugDump(int level) {
        return debugDump(0, level);
    }

    public String debugDump(int level, int levelStop) {
        return Debug.dumpObject(this, level, levelStop);
    }

    @Override
    public String toString() {
        return getShape().getObjectType().toString(this);
    }

    @Override
    public boolean equals(Object obj) {
        return getShape().getObjectType().equals(this, obj);
    }

    @Override
    public int hashCode() {
        return getShape().getObjectType().hashCode(this);
    }

    @Override
    @TruffleBoundary
    public Object get(Object id, Object defaultValue) {
        Property existing = getShape().getProperty(id);
        if (existing != null) {
            return existing.get(this, false);
        } else {
            return defaultValue;
        }
    }

    @Override
    @TruffleBoundary
    public boolean set(Object id, Object value) {
        Property existing = getShape().getProperty(id);
        if (existing != null) {
            existing.setGeneric(this, value, null);
            return true;
        } else {
            return false;
        }
    }

    @Override
    @TruffleBoundary
    public void define(Object id, Object value, int flags) {
        define(id, value, flags, ShapeImpl.DEFAULT_LAYOUT_FACTORY);
    }

    @Override
    @TruffleBoundary
    public void define(Object id, Object value, int flags, LocationFactory locationFactory) {
        ShapeImpl oldShape = getShape();
        ShapeImpl newShape = oldShape.defineProperty(id, value, flags, locationFactory);
        if (updateShape()) {
            oldShape = getShape();
        }
        Property property = newShape.getProperty(id);

        if (oldShape == newShape) {
            property.setSafe(this, value, oldShape);
        } else {
            property.setSafe(this, value, oldShape, newShape);
        }
    }

    @Override
    @TruffleBoundary
    public boolean delete(Object id) {
        ShapeImpl oldShape = getShape();
        Property existing = oldShape.getProperty(id);
        if (existing != null) {
            ShapeImpl newShape = oldShape.removeProperty(existing);
            this.reshapeAfterDelete(newShape, ShapeImpl.findCommonAncestor(oldShape, newShape));
            // TODO ancestor should be the parent of found property's shape
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int size() {
        return getShape().getPropertyCount();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public final boolean updateShape() {
        return getShape().getLayout().getStrategy().updateShape(this);
    }

    @Override
    public final DynamicObject copy(Shape currentShape) {
        return cloneWithShape(currentShape);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return getShape().getForeignAccessFactory(this);
    }
}
