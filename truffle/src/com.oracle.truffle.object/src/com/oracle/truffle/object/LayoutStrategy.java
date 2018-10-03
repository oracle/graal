/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.ListIterator;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.Locations.DeclaredLocation;
import com.oracle.truffle.object.ShapeImpl.BaseAllocator;
import com.oracle.truffle.object.Transition.AddPropertyTransition;
import com.oracle.truffle.object.Transition.DirectReplacePropertyTransition;
import com.oracle.truffle.object.Transition.ObjectTypeTransition;
import com.oracle.truffle.object.Transition.RemovePropertyTransition;
import com.oracle.truffle.object.Transition.ReservePrimitiveArrayTransition;

/** @since 0.17 or earlier */
public abstract class LayoutStrategy {
    /**
     * @since 0.17 or earlier
     */
    protected LayoutStrategy() {
    }

    private static final LocationFactory DEFAULT_LOCATION_FACTORY = new LocationFactory() {
        public Location createLocation(Shape shape, Object value) {
            return ((ShapeImpl) shape).allocator().locationForValue(value, true, value != null);
        }
    };

    /** @since 0.18 */
    protected LocationFactory getDefaultLocationFactory() {
        return DEFAULT_LOCATION_FACTORY;
    }

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
        return defineProperty(oldShape, key, value, flags, locationFactory, existing);
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl defineProperty(ShapeImpl oldShape, Object key, Object value, int flags, LocationFactory locationFactory, Property existing) {
        if (existing == null) {
            Property property = Property.create(key, locationFactory.createLocation(oldShape, value), flags);
            ShapeImpl newShape = oldShape.addProperty(property);
            return newShape;
        } else {
            if (existing.getFlags() == flags) {
                if (existing.getLocation().canSet(value)) {
                    return oldShape;
                } else {
                    return definePropertyGeneralize(oldShape, existing, value, locationFactory);
                }
            } else {
                return definePropertyChangeFlags(oldShape, existing, value, flags);
            }
        }
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl definePropertyGeneralize(ShapeImpl oldShape, Property oldProperty, Object value, LocationFactory locationFactory) {
        if (oldProperty.getLocation() instanceof DeclaredLocation) {
            Property property = oldProperty.relocate(locationFactory.createLocation(oldShape, value));
            return oldShape.replaceProperty(oldProperty, property);
        } else {
            return generalizeProperty(oldProperty, value, oldShape, oldShape);
        }
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl definePropertyChangeFlags(ShapeImpl oldShape, Property oldProperty, Object value, int flags) {
        Location oldLocation = oldProperty.getLocation();
        Location newLocation;
        if (oldLocation.canSet(value)) {
            newLocation = oldLocation;
        } else {
            newLocation = oldShape.allocator().locationForValueUpcast(value, oldLocation);
        }
        Property newProperty = Property.create(oldProperty.getKey(), newLocation, flags);
        ShapeImpl newShape = oldShape.replaceProperty(oldProperty, newProperty);
        return newShape;
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl generalizeProperty(Property oldProperty, Object value, ShapeImpl currentShape, ShapeImpl nextShape) {
        Location oldLocation = oldProperty.getLocation();
        Location newLocation = currentShape.allocator().locationForValueUpcast(value, oldLocation);
        Property newProperty = oldProperty.relocate(newLocation);
        ShapeImpl newShape = nextShape.replaceProperty(oldProperty, newProperty);
        return newShape;
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
        ShapeImpl newShape = defineProperty(oldShape, key, value, flags, locationFactory, oldProperty);
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
        assert !shape.isShared();
        RemovePropertyTransition transition = new RemovePropertyTransition(property);
        ShapeImpl cachedShape = shape.queryTransition(transition);
        if (cachedShape != null) {
            return ensureValid(cachedShape);
        }

        ShapeImpl owningShape = getShapeFromProperty(shape, property.getKey());
        if (owningShape != null) {
            List<Transition> transitionList = new ArrayList<>();
            ShapeImpl current = shape;
            while (current != owningShape) {
                if (!(current.getTransitionFromParent() instanceof Transition.DirectReplacePropertyTransition) ||
                                !((Transition.DirectReplacePropertyTransition) current.getTransitionFromParent()).getPropertyBefore().getKey().equals(property.getKey())) {
                    transitionList.add(current.getTransitionFromParent());
                }
                current = current.parent;
            }
            ShapeImpl newShape = owningShape.parent;
            for (ListIterator<Transition> iterator = transitionList.listIterator(transitionList.size()); iterator.hasPrevious();) {
                Transition previous = iterator.previous();
                newShape = applyTransition(newShape, previous, true);
            }

            shape.addIndirectTransition(transition, newShape);
            return newShape;
        } else {
            return null;
        }
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl directReplaceProperty(ShapeImpl shape, Property oldProperty, Property newProperty) {
        Transition replacePropertyTransition = new Transition.DirectReplacePropertyTransition(oldProperty, newProperty);
        ShapeImpl cachedShape = shape.queryTransition(replacePropertyTransition);
        if (cachedShape != null) {
            return ensureValid(cachedShape);
        }
        PropertyMap newPropertyMap = shape.getPropertyMap().replaceCopy(oldProperty, newProperty);
        BaseAllocator allocator = shape.allocator().addLocation(newProperty.getLocation());
        ShapeImpl newShape = shape.createShape(shape.getLayout(), shape.getSharedData(), shape, shape.getObjectType(), newPropertyMap, replacePropertyTransition, allocator, shape.getId());

        shape.addDirectTransition(replacePropertyTransition, newShape);
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
        return newShape;
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl applyTransition(ShapeImpl shape, Transition transition, boolean append) {
        if (transition instanceof AddPropertyTransition) {
            Property property = ((AddPropertyTransition) transition).getProperty();
            if (append) {
                return shape.append(property);
            } else {
                shape.onPropertyTransition(property);
                return addProperty(shape, property, false);
            }
        } else if (transition instanceof ObjectTypeTransition) {
            return shape.changeType(((ObjectTypeTransition) transition).getObjectType());
        } else if (transition instanceof ReservePrimitiveArrayTransition) {
            return shape.reservePrimitiveExtensionArray();
        } else if (transition instanceof DirectReplacePropertyTransition) {
            Property oldProperty = ((DirectReplacePropertyTransition) transition).getPropertyBefore();
            Property newProperty = ((DirectReplacePropertyTransition) transition).getPropertyAfter();
            if (append) {
                oldProperty = shape.getProperty(oldProperty.getKey());
                newProperty = newProperty.relocate(shape.allocator().moveLocation(newProperty.getLocation()));
            }
            return directReplaceProperty(shape, oldProperty, newProperty);
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
