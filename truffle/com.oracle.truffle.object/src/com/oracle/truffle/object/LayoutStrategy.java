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
import com.oracle.truffle.object.Locations.DualLocation;
import com.oracle.truffle.object.ShapeImpl.BaseAllocator;
import com.oracle.truffle.object.Transition.AddPropertyTransition;
import com.oracle.truffle.object.Transition.DirectReplacePropertyTransition;
import com.oracle.truffle.object.Transition.ObjectTypeTransition;
import com.oracle.truffle.object.Transition.RemovePropertyTransition;
import com.oracle.truffle.object.Transition.ReservePrimitiveArrayTransition;

public abstract class LayoutStrategy {
    protected static final LocationFactory DEFAULT_LAYOUT_FACTORY = new LocationFactory() {
        public Location createLocation(Shape shape, Object value) {
            return ((ShapeImpl) shape).allocator().locationForValue(value, true, value != null);
        }
    };

    protected abstract boolean updateShape(DynamicObject object);

    protected abstract ShapeImpl ensureValid(ShapeImpl newShape);

    protected abstract ShapeImpl ensureSpace(ShapeImpl shape, Location location);

    public abstract BaseAllocator createAllocator(LayoutImpl shape);

    public abstract BaseAllocator createAllocator(ShapeImpl shape);

    protected ShapeImpl defineProperty(ShapeImpl shape, Object key, Object value, int flags, LocationFactory locationFactory) {
        ShapeImpl oldShape = shape;
        if (!oldShape.isValid()) {
            oldShape = ensureValid(oldShape);
        }
        Property existing = oldShape.getProperty(key);
        return defineProperty(oldShape, key, value, flags, locationFactory, existing);
    }

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

    protected ShapeImpl definePropertyGeneralize(ShapeImpl oldShape, Property oldProperty, Object value, LocationFactory locationFactory) {
        if (oldProperty.getLocation() instanceof DeclaredLocation) {
            Property property = relocateShadow(oldProperty, locationFactory.createLocation(oldShape, value));
            ShapeImpl newShape = oldShape.addProperty(property);
            return newShape;
        } else {
            return generalizeProperty(oldProperty, value, oldShape, oldShape);
        }
    }

    protected ShapeImpl definePropertyChangeFlags(ShapeImpl oldShape, Property oldProperty, Object value, int flags) {
        Location oldLocation = oldProperty.getLocation();
        Location newLocation = oldShape.allocator().existingLocationForValue(value, oldLocation, oldShape);
        Property newProperty = Property.create(oldProperty.getKey(), newLocation, flags);
        ShapeImpl newShape = oldShape.replaceProperty(oldProperty, newProperty);
        return newShape;
    }

    protected ShapeImpl generalizeProperty(Property oldProperty, Object value, ShapeImpl currentShape, ShapeImpl nextShape) {
        Location oldLocation = oldProperty.getLocation();
        Location newLocation = currentShape.allocator().locationForValueUpcast(value, oldLocation);
        Property newProperty = oldProperty.relocate(newLocation);
        ShapeImpl newShape = nextShape.replaceProperty(oldProperty, newProperty);
        return newShape;
    }

    protected void propertySetFallback(Property property, DynamicObject store, Object value, ShapeImpl currentShape) {
        ShapeImpl oldShape = currentShape;
        ShapeImpl newShape = defineProperty(oldShape, property.getKey(), value, property.getFlags(), DEFAULT_LAYOUT_FACTORY);
        Property newProperty = newShape.getProperty(property.getKey());
        newProperty.setSafe(store, value, oldShape, newShape);
    }

    protected void propertySetWithShapeFallback(Property property, DynamicObject store, Object value, ShapeImpl currentShape, ShapeImpl nextShape) {
        ShapeImpl oldShape = currentShape;
        ShapeImpl newNextShape = generalizeProperty(property, value, oldShape, nextShape);
        Property newProperty = newNextShape.getProperty(property.getKey());
        newProperty.setSafe(store, value, oldShape, newNextShape);
    }

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

    protected void objectRemoveProperty(DynamicObjectImpl object, Property property, ShapeImpl currentShape) {
        ShapeImpl oldShape = currentShape;
        ShapeImpl newShape = oldShape.removeProperty(property);
        reshapeAfterDelete(object, oldShape, newShape, ShapeImpl.findCommonAncestor(oldShape, newShape));
    }

    protected void reshapeAfterDelete(DynamicObjectImpl object, ShapeImpl oldShape, ShapeImpl newShape, ShapeImpl deletedParentShape) {
        DynamicObject original = object.cloneWithShape(oldShape);
        object.setShapeAndResize(newShape);
        object.copyProperties(original, deletedParentShape);
    }

    protected static Property relocateShadow(Property property, Location newLocation) {
        return ((PropertyImpl) property).relocateShadow(newLocation);
    }

    protected ShapeImpl replaceProperty(ShapeImpl shape, Property oldProperty, Property newProperty) {
        return directReplaceProperty(shape, oldProperty, newProperty);
    }

    protected ShapeImpl removeProperty(ShapeImpl shape, Property property) {
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

    protected ShapeImpl addProperty(ShapeImpl shape, Property property) {
        return addProperty(shape, property, true);
    }

    protected ShapeImpl addProperty(ShapeImpl shape, Property property, boolean ensureValid) {
        assert property.isShadow() || !(shape.hasProperty(property.getKey())) : "duplicate property " + property.getKey();

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
                if (oldProperty.getLocation() instanceof DualLocation && newProperty.getLocation() instanceof DualLocation) {
                    newProperty = newProperty.relocate(((DualLocation) oldProperty.getLocation()).changeType(((DualLocation) newProperty.getLocation()).getType()));
                } else {
                    newProperty = newProperty.relocate(shape.allocator().moveLocation(newProperty.getLocation()));
                }
            }
            return directReplaceProperty(shape, oldProperty, newProperty);
        } else {
            throw new UnsupportedOperationException();
        }
    }

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
