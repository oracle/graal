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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.object.ShapeImpl.BaseAllocator;
import com.oracle.truffle.object.Transition.AddPropertyTransition;
import com.oracle.truffle.object.Transition.DirectReplacePropertyTransition;
import com.oracle.truffle.object.Transition.ObjectFlagsTransition;
import com.oracle.truffle.object.Transition.ObjectTypeTransition;
import com.oracle.truffle.object.Transition.RemovePropertyTransition;
import com.oracle.truffle.object.Transition.ReservePrimitiveArrayTransition;

/** @since 0.17 or earlier */
@SuppressWarnings("deprecation")
public abstract class LayoutStrategy {
    /**
     * @since 0.17 or earlier
     */
    protected LayoutStrategy() {
    }

    /** @since 0.18 */
    protected final LocationFactory getDefaultLocationFactory() {
        return getDefaultLocationFactory(0);
    }

    protected abstract LocationFactory getDefaultLocationFactory(long putFlags);

    protected abstract int getLocationOrdinal(Location location);

    /** @since 0.17 or earlier */
    protected abstract boolean updateShape(DynamicObject object);

    /** @since 0.17 or earlier */
    protected abstract ShapeImpl ensureValid(ShapeImpl newShape);

    /** @since 0.17 or earlier */
    protected abstract ShapeImpl ensureSpace(ShapeImpl shape, Location location);

    /** @since 0.17 or earlier */
    public abstract BaseAllocator createAllocator(LayoutImpl shape);

    /** @since 0.17 or earlier */
    public abstract BaseAllocator createAllocator(ShapeImpl shape);

    /** @since 0.17 or earlier */
    protected ShapeImpl defineProperty(ShapeImpl shape, Object key, Object value, int flags, LocationFactory locationFactory) {
        ShapeImpl oldShape = shape;
        if (!oldShape.isValid()) {
            oldShape = ensureValid(oldShape);
        }
        Property existing = oldShape.getProperty(key);
        return defineProperty(oldShape, key, value, flags, locationFactory, existing, 0);
    }

    protected ShapeImpl defineProperty(ShapeImpl oldShape, Object key, Object value, int propertyFlags, LocationFactory locationFactory, Property existing, long putFlags) {
        if (existing == null) {
            Location location = locationFactory.createLocation(oldShape, value);
            Property property = Property.create(key, location, propertyFlags);
            if (Flags.isSeparateShape(putFlags)) {
                return createSeparateShape(oldShape).addProperty(property);
            } else {
                return oldShape.addProperty(property);
            }
        } else {
            if (existing.getFlags() == propertyFlags) {
                if (existing.getLocation().canSet(value)) {
                    return oldShape;
                } else {
                    return definePropertyGeneralize(oldShape, existing, value, locationFactory, putFlags);
                }
            } else {
                Location oldLocation = existing.getLocation();
                Location newLocation;
                if (oldLocation.canSet(value)) {
                    newLocation = oldLocation;
                } else {
                    newLocation = oldShape.allocator().locationForValueUpcast(value, oldLocation, putFlags);
                }
                Property newProperty = Property.create(existing.getKey(), newLocation, propertyFlags);
                oldShape.onPropertyTransition(existing);
                return replaceProperty(oldShape, existing, newProperty);
            }
        }
    }

    /** @since 1.0 */
    protected ShapeImpl definePropertyGeneralize(ShapeImpl oldShape, Property oldProperty, Object value, LocationFactory locationFactory, long putFlags) {
        if (Flags.isSeparateShape(putFlags)) {
            Property newProperty = oldProperty.relocate(locationFactory.createLocation(oldShape, value));
            oldShape.onPropertyTransition(oldProperty);
            return separateReplaceProperty(oldShape, oldProperty, newProperty);
        } else if (oldProperty.getLocation().isValue()) {
            Property newProperty = oldProperty.relocate(locationFactory.createLocation(oldShape, value));
            // Always use direct replace for value locations to avoid shape explosion
            oldShape.onPropertyTransition(oldProperty);
            return directReplaceProperty(oldShape, oldProperty, newProperty);
        } else {
            return generalizeProperty(oldProperty, value, oldShape, oldShape, putFlags);
        }
    }

    /** @since 0.17 or earlier */
    @Deprecated
    protected ShapeImpl generalizeProperty(Property oldProperty, Object value, ShapeImpl currentShape, ShapeImpl nextShape) {
        return generalizeProperty(oldProperty, value, currentShape, nextShape, 0);
    }

    protected ShapeImpl generalizeProperty(Property oldProperty, Object value, ShapeImpl currentShape, ShapeImpl nextShape, long putFlags) {
        Location oldLocation = oldProperty.getLocation();
        Location newLocation = currentShape.allocator().locationForValueUpcast(value, oldLocation, putFlags);
        Property newProperty = oldProperty.relocate(newLocation);
        nextShape.onPropertyTransition(oldProperty);
        return replaceProperty(nextShape, oldProperty, newProperty);
    }

    /** @since 0.17 or earlier */
    protected void propertySetFallback(Property property, DynamicObject store, Object value, ShapeImpl currentShape) {
        ShapeImpl oldShape = currentShape;
        ShapeImpl newShape = defineProperty(oldShape, property.getKey(), value, property.getFlags(), getDefaultLocationFactory());
        Property newProperty = newShape.getProperty(property.getKey());
        newProperty.setSafe(store, value, oldShape, newShape);
    }

    /** @since 0.17 or earlier */
    protected void propertySetWithShapeFallback(Property property, DynamicObject store, Object value, ShapeImpl currentShape, ShapeImpl nextShape) {
        ShapeImpl oldShape = currentShape;
        ShapeImpl newNextShape = generalizeProperty(property, value, oldShape, nextShape);
        Property newProperty = newNextShape.getProperty(property.getKey());
        newProperty.setSafe(store, value, oldShape, newNextShape);
    }

    /** @since 0.17 or earlier */
    protected void objectDefineProperty(DynamicObjectImpl object, Object key, Object value, int flags, LocationFactory locationFactory, ShapeImpl currentShape) {
        ShapeImpl oldShape = currentShape;
        Property oldProperty = oldShape.getProperty(key);
        ShapeImpl newShape = defineProperty(oldShape, key, value, flags, locationFactory, oldProperty, 0);
        if (oldShape == newShape) {
            assert oldProperty.equals(newShape.getProperty(key));
            oldProperty.setSafe(object, value, oldShape);
        } else {
            Property newProperty = newShape.getProperty(key);
            newProperty.setSafe(object, value, oldShape, newShape);
        }
    }

    /** @since 0.17 or earlier */
    protected void objectRemoveProperty(DynamicObjectImpl object, Property property, ShapeImpl currentShape) {
        ShapeImpl oldShape = currentShape;
        ShapeImpl newShape = oldShape.removeProperty(property);

        reshapeAfterDelete(object, oldShape, newShape, ShapeImpl.findCommonAncestor(oldShape, newShape));
    }

    /** @since 0.17 or earlier */
    protected void reshapeAfterDelete(DynamicObjectImpl object, ShapeImpl oldShape, ShapeImpl newShape, ShapeImpl deletedParentShape) {
        if (oldShape.isShared()) {
            object.setShapeAndGrow(oldShape, newShape);
            return;
        }

        DynamicObject original = object.cloneWithShape(oldShape);
        object.setShapeAndResize(newShape);
        object.copyProperties(original, deletedParentShape);
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl replaceProperty(ShapeImpl shape, Property oldProperty, Property newProperty) {
        return directReplaceProperty(shape, oldProperty, newProperty);
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl removeProperty(ShapeImpl shape, Property property) {
        boolean direct = shape.isShared();
        RemovePropertyTransition transition = new RemovePropertyTransition(property, direct);
        ShapeImpl cachedShape = shape.queryTransition(transition);
        if (cachedShape != null) {
            return ensureValid(cachedShape);
        }

        if (direct) {
            return directRemoveProperty(shape, property, transition);
        }

        return indirectRemoveProperty(shape, property, transition);
    }

    /**
     * Removes a property by rewinding and replaying property transitions; moves any subsequent
     * property locations to fill in the gap.
     */
    private ShapeImpl indirectRemoveProperty(ShapeImpl shape, Property property, RemovePropertyTransition transition) {
        ShapeImpl owningShape = getShapeFromProperty(shape, property.getKey());
        if (owningShape == null) {
            return null;
        }

        List<Transition> transitionList = new ArrayList<>();
        for (ShapeImpl current = shape; current != owningShape; current = current.parent) {
            Transition transitionFromParent = current.getTransitionFromParent();
            if (transitionFromParent instanceof Transition.DirectReplacePropertyTransition &&
                            ((Transition.DirectReplacePropertyTransition) transitionFromParent).getPropertyBefore().getKey().equals(property.getKey())) {
                continue;
            } else {
                transitionList.add(transitionFromParent);
            }
        }

        ShapeImpl newShape = owningShape.parent;
        for (ListIterator<Transition> iterator = transitionList.listIterator(transitionList.size()); iterator.hasPrevious();) {
            Transition previous = iterator.previous();
            newShape = applyTransition(newShape, previous, true);
        }

        shape.addIndirectTransition(transition, newShape);
        return newShape;
    }

    /**
     * Removes a property without moving property locations, leaving a gap that is lost forever.
     */
    private static ShapeImpl directRemoveProperty(ShapeImpl shape, Property property, RemovePropertyTransition transition) {
        PropertyMap newPropertyMap = shape.getPropertyMap().removeCopy(property);
        ShapeImpl newShape = shape.createShape(shape.getLayout(), shape.sharedData, shape, shape.objectType, newPropertyMap, transition, shape.allocator(), shape.flags);

        shape.addDirectTransition(transition, newShape);
        return newShape;
    }

    protected ShapeImpl directReplaceProperty(ShapeImpl shape, Property oldProperty, Property newProperty) {
        return directReplaceProperty(shape, oldProperty, newProperty, true);
    }

    protected ShapeImpl directReplaceProperty(ShapeImpl shape, Property oldProperty, Property newProperty, boolean ensureValid) {
        assert oldProperty.getKey().equals(newProperty.getKey());
        if (oldProperty.equals(newProperty)) {
            return shape;
        }

        shape.onPropertyTransition(oldProperty);

        Transition replacePropertyTransition = new Transition.DirectReplacePropertyTransition(oldProperty, newProperty);
        ShapeImpl cachedShape = shape.queryTransition(replacePropertyTransition);
        if (cachedShape != null) {
            return ensureValid ? ensureValid(cachedShape) : cachedShape;
        }
        PropertyMap newPropertyMap = shape.getPropertyMap().replaceCopy(oldProperty, newProperty);
        BaseAllocator allocator = shape.allocator().addLocation(newProperty.getLocation());
        ShapeImpl newShape = shape.createShape(shape.getLayout(), shape.sharedData, shape, shape.objectType, newPropertyMap, replacePropertyTransition, allocator, shape.flags);

        assert newProperty.isSame(newShape.getProperty(newProperty.getKey())) : newShape.getProperty(newProperty.getKey());

        shape.addDirectTransition(replacePropertyTransition, newShape);
        if (!shape.isValid()) {
            newShape.invalidateValidAssumption();
            return ensureValid ? ensureValid(newShape) : newShape;
        }
        return newShape;
    }

    protected ShapeImpl separateReplaceProperty(ShapeImpl shape, Property oldProperty, Property newProperty) {
        ShapeImpl newRoot = shape.createShape(shape.getLayout(), shape.sharedData, null, shape.objectType, PropertyMap.empty(), null, shape.getLayout().createAllocator(), shape.flags);
        ShapeImpl newShape = newRoot;
        boolean found = false;
        for (Iterator<Property> iterator = shape.getPropertyMap().orderedValueIterator(); iterator.hasNext();) {
            Property p = iterator.next();
            if (!found && p.equals(oldProperty)) {
                p = newProperty;
                found = true;
            }
            newShape = newShape.addProperty(newProperty);
        }
        assert found;
        assert newShape.isValid();
        return newShape;
    }

    protected ShapeImpl createSeparateShape(ShapeImpl shape) {
        ShapeImpl newRoot = shape.createShape(shape.getLayout(), shape.sharedData, null, shape.objectType, PropertyMap.empty(), null, shape.getLayout().createAllocator(), shape.flags);
        ShapeImpl newShape = newRoot;
        for (Iterator<Property> iterator = shape.getPropertyMap().orderedValueIterator(); iterator.hasNext();) {
            Property p = iterator.next();
            newShape = newShape.addProperty(p);
        }
        assert newShape.isValid();
        return newShape;
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl addProperty(ShapeImpl shape, Property property) {
        return addProperty(shape, property, true);
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl addProperty(ShapeImpl shape, Property property, boolean ensureValid) {
        assert !(shape.hasProperty(property.getKey())) : "duplicate property " + property.getKey();

        AddPropertyTransition addTransition = new AddPropertyTransition(property);
        ShapeImpl cachedShape = shape.queryTransition(addTransition);
        if (cachedShape != null) {
            return ensureValid ? ensureValid(cachedShape) : cachedShape;
        }

        ShapeImpl oldShape = ensureSpace(shape, property.getLocation());

        ShapeImpl newShape = ShapeImpl.makeShapeWithAddedProperty(oldShape, addTransition);
        oldShape.addDirectTransition(addTransition, newShape);
        if (!oldShape.isValid()) {
            newShape.invalidateValidAssumption();
            return ensureValid ? ensureValid(newShape) : newShape;
        }
        return newShape;
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl applyTransition(ShapeImpl shape, Transition transition, boolean append) {
        if (transition instanceof AddPropertyTransition) {
            Property property = ((AddPropertyTransition) transition).getProperty();
            ShapeImpl newShape;
            if (append) {
                newShape = shape.append(property);
            } else {
                shape.onPropertyTransition(property);
                newShape = addProperty(shape, property, false);
            }
            return newShape;
        } else if (transition instanceof ObjectTypeTransition) {
            return shape.changeType(((ObjectTypeTransition) transition).getObjectType());
        } else if (transition instanceof ObjectFlagsTransition) {
            return shape.setFlags(((ObjectFlagsTransition) transition).getObjectFlags());
        } else if (transition instanceof ReservePrimitiveArrayTransition) {
            return shape.reservePrimitiveExtensionArray();
        } else if (transition instanceof DirectReplacePropertyTransition) {
            Property oldProperty = ((DirectReplacePropertyTransition) transition).getPropertyBefore();
            Property newProperty = ((DirectReplacePropertyTransition) transition).getPropertyAfter();
            if (append) {
                boolean sameLocation = oldProperty.getLocation().equals(newProperty.getLocation());
                oldProperty = shape.getProperty(oldProperty.getKey());
                Location newLocation;
                if (sameLocation) {
                    newLocation = oldProperty.getLocation();
                } else {
                    newLocation = shape.allocator().moveLocation(newProperty.getLocation());
                }
                newProperty = newProperty.relocate(newLocation);
            }
            return directReplaceProperty(shape, oldProperty, newProperty, append);
        } else {
            throw new UnsupportedOperationException(transition.getClass().getName());
        }
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl addPrimitiveExtensionArray(ShapeImpl shape) {
        LayoutImpl layout = shape.getLayout();
        assert layout.hasPrimitiveExtensionArray() && !shape.hasPrimitiveArray();
        Transition transition = new ReservePrimitiveArrayTransition();
        ShapeImpl cachedShape = shape.queryTransition(transition);
        if (cachedShape != null) {
            return layout.getStrategy().ensureValid(cachedShape);
        }

        ShapeImpl oldShape = ensureSpace(shape, layout.getPrimitiveArrayLocation());
        ShapeImpl newShape = ShapeImpl.makeShapeWithPrimitiveExtensionArray(oldShape, transition);
        oldShape.addDirectTransition(transition, newShape);
        return newShape;
    }

    /**
     * Get the (parent) shape that holds the given property.
     *
     * @since 0.17 or earlier
     */
    protected static ShapeImpl getShapeFromProperty(ShapeImpl shape, Object propertyName) {
        ShapeImpl current = shape;
        ShapeImpl root = shape.getRoot();
        while (current != root) {
            if (current.getTransitionFromParent() instanceof AddPropertyTransition && ((AddPropertyTransition) current.getTransitionFromParent()).getProperty().getKey().equals(propertyName)) {
                return current;
            }
            current = current.getParent();
        }

        return null;
    }

    /**
     * Get the (parent) shape that holds the given property.
     *
     * @since 0.17 or earlier
     */
    @Deprecated
    protected static ShapeImpl getShapeFromProperty(ShapeImpl shape, Property prop) {
        ShapeImpl current = shape;
        ShapeImpl root = shape.getRoot();
        while (current != root) {
            if (current.getTransitionFromParent() instanceof AddPropertyTransition && ((AddPropertyTransition) current.getTransitionFromParent()).getProperty().equals(prop)) {
                return current;
            }
            current = current.parent;
        }

        return null;
    }
}
