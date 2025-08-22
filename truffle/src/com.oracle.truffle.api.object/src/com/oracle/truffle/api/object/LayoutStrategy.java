/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import com.oracle.truffle.api.object.Transition.AddPropertyTransition;
import com.oracle.truffle.api.object.Transition.DirectReplacePropertyTransition;
import com.oracle.truffle.api.object.Transition.ObjectFlagsTransition;
import com.oracle.truffle.api.object.Transition.ObjectTypeTransition;
import com.oracle.truffle.api.object.Transition.RemovePropertyTransition;

@SuppressWarnings("deprecation")
abstract sealed class LayoutStrategy permits ObsolescenceStrategy {

    protected LayoutStrategy() {
    }

    protected int getLocationOrdinal(Location location) {
        return ((LocationImpl) location).getOrdinal();
    }

    protected abstract boolean updateShape(DynamicObject object);

    protected abstract Shape ensureValid(Shape newShape);

    protected Shape ensureSpace(Shape shape, Location location) {
        Objects.requireNonNull(location);
        assert assertLocationInRange(shape, location);
        return shape;
    }

    protected abstract BaseAllocator createAllocator(Shape shape);

    protected Shape defineProperty(Shape shape, Object key, Object value, int flags) {
        return defineProperty(shape, key, value, flags, Flags.DEFAULT);
    }

    protected Shape defineProperty(Shape shape, Object key, Object value, int flags, int putFlags) {
        Shape oldShape = shape;
        if (!oldShape.isValid()) {
            oldShape = ensureValid(oldShape);
        }
        Property existing = oldShape.getProperty(key);
        return defineProperty(oldShape, key, value, flags, existing, putFlags);
    }

    protected Shape defineProperty(Shape oldShape, Object key, Object value, int propertyFlags, Property existing, int putFlags) {
        if (existing == null) {
            return defineNewProperty(oldShape, key, value, propertyFlags, putFlags);
        } else {
            if (existing.getFlags() == propertyFlags) {
                if (existing.getLocation().canStore(value)) {
                    return oldShape;
                } else {
                    return definePropertyGeneralize(oldShape, existing, value, putFlags);
                }
            } else {
                return definePropertyChangeFlags(oldShape, existing, value, propertyFlags, putFlags);
            }
        }
    }

    private Shape defineNewProperty(Shape oldShape, Object key, Object value, int propertyFlags, int putFlags) {
        if (!Flags.isConstant(putFlags) && !Flags.isDeclaration(putFlags)) {
            Class<?> locationType = detectLocationType(value);
            if (locationType != null) {
                AddPropertyTransition addTransition = new AddPropertyTransition(key, propertyFlags, locationType);
                Shape cachedShape = oldShape.queryTransition(addTransition);
                if (cachedShape != null) {
                    return ensureValid(cachedShape);
                }
            }
        }

        Location location = createLocationForValue(oldShape, value, putFlags);
        Property property = Property.create(key, location, propertyFlags);
        return addProperty(oldShape, property);
    }

    protected Class<?> detectLocationType(Object value) {
        if (value instanceof Integer) {
            return int.class;
        } else if (value instanceof Double) {
            return double.class;
        } else if (value instanceof Long) {
            return long.class;
        } else if (value instanceof Boolean) {
            return boolean.class;
        } else {
            return Object.class;
        }
    }

    protected Location createLocationForValue(Shape shape, Object value, int putFlags) {
        return ((ExtAllocator) shape.allocator()).locationForValue(value, putFlags);
    }

    protected Shape definePropertyChangeFlags(Shape oldShape, Property existing, Object value, int propertyFlags, int putFlags) {
        assert existing.getFlags() != propertyFlags;
        if (existing.getLocation().canStore(value)) {
            Property newProperty = Property.create(existing.getKey(), existing.getLocation(), propertyFlags);
            return replaceProperty(oldShape, existing, newProperty);
        } else {
            return generalizePropertyWithFlags(oldShape, existing, value, propertyFlags, putFlags);
        }
    }

    protected Shape definePropertyGeneralize(Shape oldShape, Property oldProperty, Object value, int putFlags) {
        if (oldProperty.getLocation().isValue()) {
            Location newLocation = createLocationForValue(oldShape, value, putFlags);
            Property newProperty = ((PropertyImpl) oldProperty).relocate(newLocation);
            // Always use direct replace for value locations to avoid shape explosion
            return directReplaceProperty(oldShape, oldProperty, newProperty);
        } else {
            return generalizeProperty(oldProperty, value, oldShape, oldShape, putFlags);
        }
    }

    protected Shape generalizeProperty(Property oldProperty, Object value, Shape currentShape, Shape nextShape, int putFlags) {
        Location oldLocation = oldProperty.getLocation();
        Location newLocation = currentShape.allocator().locationForValueUpcast(value, oldLocation, putFlags);
        Property newProperty = ((PropertyImpl) oldProperty).relocate(newLocation);
        return replaceProperty(nextShape, oldProperty, newProperty);
    }

    protected Shape generalizePropertyWithFlags(Shape currentShape, Property oldProperty, Object value, int propertyFlags, int putFlags) {
        assert !oldProperty.getLocation().canStore(value);
        Location newLocation = currentShape.allocator().locationForValueUpcast(value, oldProperty.getLocation(), putFlags);
        Property newProperty = Property.create(oldProperty.getKey(), newLocation, propertyFlags);
        return replaceProperty(currentShape, oldProperty, newProperty);
    }

    protected Shape replaceProperty(Shape shape, Property oldProperty, Property newProperty) {
        return directReplaceProperty(shape, oldProperty, newProperty);
    }

    protected Shape removeProperty(Shape shape, Property property) {
        if (property.getLocation() instanceof ExtLocations.InstanceLocation instanceLocation) {
            instanceLocation.maybeInvalidateFinalAssumption();
        }

        boolean direct = shape.isShared();
        RemovePropertyTransition transition = newRemovePropertyTransition(property, direct);
        shape.onPropertyTransition(transition);
        Shape cachedShape = shape.queryTransition(transition);
        if (cachedShape != null) {
            return ensureValid(cachedShape);
        }

        if (direct) {
            return directRemoveProperty(shape, property, transition);
        }

        return indirectRemoveProperty(shape, property, transition);
    }

    protected RemovePropertyTransition newRemovePropertyTransition(Property property, boolean direct) {
        return new RemovePropertyTransition(property, toLocationOrType(property.getLocation()), direct);
    }

    /**
     * Removes a property by rewinding and replaying property transitions; moves any subsequent
     * property locations to fill in the gap.
     */
    private Shape indirectRemoveProperty(Shape shape, Property property, RemovePropertyTransition transition) {
        Shape owningShape = getShapeFromProperty(shape, property.getKey());
        if (owningShape == null) {
            return null;
        }

        List<Transition> transitionList = new ArrayList<>();
        for (Shape current = shape; current != owningShape; current = current.parent) {
            Transition transitionFromParent = current.getTransitionFromParent();
            if (transitionFromParent instanceof Transition.DirectReplacePropertyTransition &&
                            ((Transition.DirectReplacePropertyTransition) transitionFromParent).getPropertyBefore().getKey().equals(property.getKey())) {
                continue;
            } else {
                transitionList.add(transitionFromParent);
            }
        }

        Shape newShape = owningShape.parent;
        for (ListIterator<Transition> iterator = transitionList.listIterator(transitionList.size()); iterator.hasPrevious();) {
            Transition previous = iterator.previous();
            newShape = applyTransition(newShape, previous, true);
        }

        return shape.addIndirectTransition(transition, newShape);
    }

    /**
     * Removes a property without moving property locations, leaving a gap that is lost forever.
     */
    private static Shape directRemoveProperty(Shape shape, Property property, RemovePropertyTransition transition) {
        PropertyMap newPropertyMap = shape.getPropertyMap().removeCopy(property);
        Shape newShape = shape.createShape(shape.objectType, shape.sharedData, newPropertyMap, transition, shape.allocator(), shape.flags);

        return shape.addDirectTransition(transition, newShape);
    }

    protected Shape directReplaceProperty(Shape shape, Property oldProperty, Property newProperty) {
        return directReplaceProperty(shape, oldProperty, newProperty, true);
    }

    protected Shape directReplaceProperty(Shape shape, Property oldProperty, Property newProperty, boolean ensureValid) {
        Shape newShape = directReplacePropertyInner(shape, oldProperty, newProperty);

        Property actualProperty = newShape.getProperty(newProperty.getKey());
        // Ensure the actual property location is of the same type or more general.
        ensureSameTypeOrMoreGeneral(actualProperty, newProperty);

        return ensureValid ? ensureValid(newShape) : newShape;
    }

    private static Shape directReplacePropertyInner(Shape shape, Property oldProperty, Property newProperty) {
        assert oldProperty.getKey().equals(newProperty.getKey());
        if (oldProperty.equals(newProperty)) {
            return shape;
        }

        var replacePropertyTransition = new Transition.DirectReplacePropertyTransition(oldProperty, newProperty);
        shape.onPropertyTransition(replacePropertyTransition);
        Shape cachedShape = shape.queryTransition(replacePropertyTransition);
        if (cachedShape != null) {
            return cachedShape;
        }
        PropertyMap newPropertyMap = shape.getPropertyMap().replaceCopy(oldProperty, newProperty);
        BaseAllocator allocator = shape.allocator().addLocation(newProperty.getLocation());
        Shape newShape = shape.createShape(shape.objectType, shape.sharedData, newPropertyMap, replacePropertyTransition, allocator, shape.flags);

        newShape = shape.addDirectTransition(replacePropertyTransition, newShape);

        if (!shape.isValid()) {
            newShape.invalidateValidAssumption();
        }
        return newShape;
    }

    protected Shape addProperty(Shape shape, Property property) {
        return addProperty(shape, property, true);
    }

    protected Shape addProperty(Shape shape, Property property, boolean ensureValid) {
        Shape newShape = addPropertyInner(shape, property);

        Property actualProperty = newShape.getLastProperty();
        // Ensure the actual property location is of the same type or more general.
        ensureSameTypeOrMoreGeneral(actualProperty, property);

        return ensureValid ? ensureValid(newShape) : newShape;
    }

    private Shape addPropertyInner(Shape shape, Property property) {
        assert !(shape.hasProperty(property.getKey())) : "duplicate property " + property.getKey();

        AddPropertyTransition addTransition = newAddPropertyTransition(property);
        shape.onPropertyTransition(addTransition);
        Shape cachedShape = shape.queryTransition(addTransition);
        if (cachedShape != null) {
            return cachedShape;
        }

        Shape oldShape = ensureSpace(shape, property.getLocation());

        Shape newShape = Shape.makeShapeWithAddedProperty(oldShape, addTransition);
        newShape = oldShape.addDirectTransition(addTransition, newShape);

        if (!oldShape.isValid()) {
            newShape.invalidateValidAssumption();
        }
        return newShape;
    }

    protected AddPropertyTransition newAddPropertyTransition(Property property) {
        return new AddPropertyTransition(property, toLocationOrType(property.getLocation()));
    }

    protected Object toLocationOrType(Location location) {
        if (location instanceof LocationImpl) {
            Class<?> type = ((LocationImpl) location).getType();
            if (type != null) {
                return type;
            }
        }
        return location;
    }

    protected Shape applyTransition(Shape shape, Transition transition, boolean append) {
        if (transition instanceof AddPropertyTransition) {
            Property property = ((AddPropertyTransition) transition).getProperty();
            Shape newShape;
            if (append) {
                Property newProperty = ((PropertyImpl) property).relocate(shape.allocator().moveLocation(property.getLocation()));
                newShape = addProperty(shape, newProperty, true);
            } else {
                newShape = addProperty(shape, property, false);
            }
            return newShape;
        } else if (transition instanceof ObjectTypeTransition) {
            return shape.setDynamicType(((ObjectTypeTransition) transition).getObjectType());
        } else if (transition instanceof ObjectFlagsTransition) {
            return shape.setFlags(((ObjectFlagsTransition) transition).getObjectFlags());
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
                newProperty = ((PropertyImpl) newProperty).relocate(newLocation);
            }
            return directReplaceProperty(shape, oldProperty, newProperty, append);
        } else {
            throw new UnsupportedOperationException(transition.getClass().getName());
        }
    }

    /**
     * Get the (parent) shape that holds the given property.
     */
    protected static Shape getShapeFromProperty(Shape shape, Object propertyName) {
        Shape current = shape;
        Shape root = shape.getRoot();
        while (current != root) {
            if (current.getTransitionFromParent() instanceof AddPropertyTransition && ((AddPropertyTransition) current.getTransitionFromParent()).getPropertyKey().equals(propertyName)) {
                return current;
            }
            current = current.getParent();
        }

        return null;
    }

    protected void ensureSameTypeOrMoreGeneral(Property generalProperty, Property specificProperty) {
        assert ((PropertyImpl) generalProperty).isSame(specificProperty) : generalProperty;
        assert generalProperty.getLocation() == specificProperty.getLocation() ||
                        ((LocationImpl) generalProperty.getLocation()).getType() == ((LocationImpl) specificProperty.getLocation()).getType() : generalProperty;
        ensureSameTypeOrMoreGeneral(generalProperty.getLocation(), specificProperty.getLocation());
    }

    private static void ensureSameTypeOrMoreGeneral(Location generalLocation, Location specificLocation) {
        if (generalLocation == specificLocation) {
            return;
        }
        if (generalLocation instanceof ExtLocations.AbstractObjectLocation objLocGeneral && specificLocation instanceof ExtLocations.AbstractObjectLocation objLocSpecific) {
            ExtLocations.TypeAssumption assGeneral = objLocGeneral.getTypeAssumption();
            ExtLocations.TypeAssumption assSpecific = objLocSpecific.getTypeAssumption();
            if (assGeneral != assSpecific) {
                if (!assGeneral.type.isAssignableFrom(assSpecific.type) || assGeneral.nonNull && !assSpecific.nonNull) {
                    // If assignable check failed, merge type assumptions to ensure safety.
                    // Otherwise, we might unsafe cast based on a wrong type assumption.
                    objLocGeneral.mergeTypeAssumption(assSpecific);
                }
            }
        }
    }

    protected static boolean assertLocationInRange(final Shape shape, final Location location) {
        final LayoutImpl layout = shape.getLayout();
        if (location instanceof LocationImpl) {
            ((LocationImpl) location).accept(new LocationImpl.LocationVisitor() {
                @Override
                public void visitPrimitiveField(int index, int count) {
                    assert index + count <= layout.getPrimitiveFieldCount();
                }

                @Override
                public void visitObjectField(int index, int count) {
                    assert index + count <= layout.getObjectFieldCount();
                }

                @Override
                public void visitPrimitiveArray(int index, int count) {
                }

                @Override
                public void visitObjectArray(int index, int count) {
                }
            });
        }
        return true;
    }
}
