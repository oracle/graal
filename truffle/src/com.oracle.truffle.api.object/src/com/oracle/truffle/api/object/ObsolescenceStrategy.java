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

import static com.oracle.truffle.api.object.ObjectStorageOptions.TraceReshape;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Location.LocationVisitor;
import com.oracle.truffle.api.object.Transition.ObjectFlagsTransition;
import com.oracle.truffle.api.object.Transition.ObjectTypeTransition;
import com.oracle.truffle.api.object.Transition.RemovePropertyTransition;
import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Transition.AbstractReplacePropertyTransition;
import com.oracle.truffle.api.object.Transition.AddPropertyTransition;
import com.oracle.truffle.api.object.Transition.DirectReplacePropertyTransition;

@SuppressWarnings("deprecation")
final class ObsolescenceStrategy {

    private static final ObsolescenceStrategy SINGLETON = new ObsolescenceStrategy();

    static ObsolescenceStrategy singleton() {
        return SINGLETON;
    }

    private static final DebugCounter makeSuccessorShapeCount = DebugCounter.create("Rebuild shape count");
    private static final DebugCounter reshapeCount = DebugCounter.create("Reshape count");
    private static final int TRACE_RESHAPE_LIMIT = 500;

    @SuppressWarnings("serial") private static final Error STACK_OVERFLOW_ERROR = new NonrecoverableError();

    /**
     * Removes a property without moving property locations, leaving a gap that is lost forever.
     */
    private static Shape directRemoveProperty(Shape shape, Property property, RemovePropertyTransition transition) {
        PropertyMap newPropertyMap = shape.getPropertyMap().removeCopy(property);
        Shape newShape = shape.createShape(shape.objectType, shape.sharedData, newPropertyMap, transition, shape.allocator(), shape.flags);

        return shape.addDirectTransition(transition, newShape);
    }

    private static Shape directReplacePropertyInner(Shape shape, Property oldProperty, Property newProperty) {
        assert oldProperty.getKey().equals(newProperty.getKey());
        if (oldProperty.equals(newProperty)) {
            return shape;
        }

        var replacePropertyTransition = new DirectReplacePropertyTransition(oldProperty, newProperty);
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

    /**
     * Get the (parent) shape that holds the given property.
     */
    private static Shape getShapeFromProperty(Shape shape, Object propertyName) {
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

    private static boolean assertLocationInRange(final Shape shape, final Location location) {
        final LayoutImpl layout = shape.getLayout();
        location.accept(new LocationVisitor() {
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
        return true;
    }

    private Shape ensureSpace(Shape shape, Location location) {
        Objects.requireNonNull(location);
        assert assertLocationInRange(shape, location);
        return shape;
    }

    Shape defineProperty(Shape shape, Object key, Object value, int flags) {
        return defineProperty(shape, key, value, flags, Flags.DEFAULT);
    }

    Shape defineProperty(Shape shape, Object key, Object value, int flags, int putFlags) {
        Shape oldShape = shape;
        if (!oldShape.isValid()) {
            oldShape = ensureValid(oldShape);
        }
        Property existing = oldShape.getProperty(key);
        return defineProperty(oldShape, key, value, flags, existing, putFlags);
    }

    Shape defineProperty(Shape oldShape, Object key, Object value, int propertyFlags, Property existing, int putFlags) {
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
        if (!Flags.isConstant(putFlags)) {
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

    private Class<?> detectLocationType(Object value) {
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

    private Location createLocationForValue(Shape shape, Object value, int putFlags) {
        return ((ExtAllocator) shape.allocator()).locationForValue(value, putFlags);
    }

    private Shape definePropertyChangeFlags(Shape oldShape, Property existing, Object value, int propertyFlags, int putFlags) {
        assert existing.getFlags() != propertyFlags;
        if (existing.getLocation().canStore(value)) {
            Property newProperty = Property.create(existing.getKey(), existing.getLocation(), propertyFlags);
            return replaceProperty(oldShape, existing, newProperty);
        } else {
            return generalizePropertyWithFlags(oldShape, existing, value, propertyFlags, putFlags);
        }
    }

    Shape definePropertyGeneralize(Shape oldShape, Property oldProperty, Object value, int putFlags) {
        if (oldProperty.getLocation().isValue()) {
            Location newLocation = createLocationForValue(oldShape, value, putFlags);
            Property newProperty = oldProperty.relocate(newLocation);
            // Always use direct replace for value locations to avoid shape explosion
            return directReplaceProperty(oldShape, oldProperty, newProperty);
        } else {
            return generalizeProperty(oldProperty, value, oldShape, oldShape, putFlags);
        }
    }

    Shape removeProperty(Shape shape, Property property) {
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

    private RemovePropertyTransition newRemovePropertyTransition(Property property, boolean direct) {
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
            if (transitionFromParent instanceof DirectReplacePropertyTransition &&
                            ((DirectReplacePropertyTransition) transitionFromParent).getPropertyBefore().getKey().equals(property.getKey())) {
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

    private Shape directReplaceProperty(Shape shape, Property oldProperty, Property newProperty) {
        return directReplaceProperty(shape, oldProperty, newProperty, true);
    }

    private Shape directReplaceProperty(Shape shape, Property oldProperty, Property newProperty, boolean ensureValid) {
        Shape newShape = directReplacePropertyInner(shape, oldProperty, newProperty);

        Property actualProperty = newShape.getProperty(newProperty.getKey());
        // Ensure the actual property location is of the same type or more general.
        ensureSameTypeOrMoreGeneral(actualProperty, newProperty);

        return ensureValid ? ensureValid(newShape) : newShape;
    }

    Shape addProperty(Shape shape, Property property) {
        return addProperty(shape, property, true);
    }

    private Shape addProperty(Shape shape, Property property, boolean ensureValid) {
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

    private AddPropertyTransition newAddPropertyTransition(Property property) {
        return new AddPropertyTransition(property, toLocationOrType(property.getLocation()));
    }

    private Object toLocationOrType(Location location) {
        Class<?> type = location.getType();
        if (type != null) {
            return type;
        }
        return location;
    }

    private Shape applyTransition(Shape shape, Transition transition, boolean append) {
        if (transition instanceof AddPropertyTransition) {
            Property property = ((AddPropertyTransition) transition).getProperty();
            Shape newShape;
            if (append) {
                Property newProperty = property.relocate(shape.allocator().moveLocation(property.getLocation()));
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
                newProperty = newProperty.relocate(newLocation);
            }
            return directReplaceProperty(shape, oldProperty, newProperty, append);
        } else {
            throw new UnsupportedOperationException(transition.getClass().getName());
        }
    }

    private void ensureSameTypeOrMoreGeneral(Property generalProperty, Property specificProperty) {
        assert generalProperty.isSame(specificProperty) : generalProperty;
        assert generalProperty.getLocation() == specificProperty.getLocation() ||
                        generalProperty.getLocation().getType() == specificProperty.getLocation().getType() : generalProperty;
        ensureSameTypeOrMoreGeneral(generalProperty.getLocation(), specificProperty.getLocation());
    }

    @SuppressWarnings("serial")
    static final class NonrecoverableError extends ThreadDeath {

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        @Override
        public String getMessage() {
            return "Execution cancelled due to non-recoverable StackOverflowError";
        }
    }

    private ObsolescenceStrategy() {
    }

    @TruffleBoundary
    boolean updateShape(DynamicObject object) {
        boolean changed = checkForObsoleteShapeAndMigrate(object);
        // shape should be valid now, but we cannot assert this due to a possible race
        return changed;
    }

    private Shape ensureValid(Shape newShape) {
        Shape nextShape = newShape;
        // if it's been obsoleted (cached shape), skip over
        if (!nextShape.isValid()) {
            nextShape = getObsoletedBy(nextShape);
        }
        // shape should be valid now, but we cannot assert this due to a possible race
        return nextShape;
    }

    private Shape generalizeProperty(Property oldProperty, Object value, Shape oldShape, Shape newShape, int putFlags) {
        if (oldShape.isShared() || oldProperty.getLocation().isValue()) {
            Location oldLocation = oldProperty.getLocation();
            Location newLocation = oldShape.allocator().locationForValueUpcast(value, oldLocation, putFlags);
            Property newProperty = oldProperty.relocate(newLocation);
            return replaceProperty(newShape, oldProperty, newProperty);
        } else {
            if (oldShape == newShape) {
                return generalizeHelper(oldProperty, value, oldShape, putFlags);
            } else {
                return generalizeHelperWithShape(oldProperty, value, oldShape, newShape, putFlags);
            }
        }
    }

    private Shape generalizePropertyWithFlags(Shape oldShape, Property oldProperty, Object value, int propertyFlags, int putFlags) {
        if (oldShape.isShared() || oldProperty.getLocation().isValue()) {
            assert !oldProperty.getLocation().canStore(value);
            Location newLocation = oldShape.allocator().locationForValueUpcast(value, oldProperty.getLocation(), putFlags);
            Property newProperty = Property.create(oldProperty.getKey(), newLocation, propertyFlags);
            return replaceProperty(oldShape, oldProperty, newProperty);
        } else {
            Shape generalizedShape = generalizeHelper(oldProperty, value, oldShape, putFlags);
            Property generalizedProperty = generalizedShape.getProperty(oldProperty.getKey());
            Property propertyWithFlags = Property.create(oldProperty.getKey(), generalizedProperty.getLocation(), propertyFlags);
            return replaceProperty(generalizedShape, generalizedProperty, propertyWithFlags);
        }
    }

    Shape replaceProperty(Shape shape, Property oldProperty, Property newProperty) {
        if (shape.isShared() || oldProperty.getLocation().isValue()) {
            return directReplaceProperty(shape, oldProperty, newProperty);
        } else {
            return indirectReplaceProperty(shape, oldProperty, newProperty);
        }
    }

    private Shape indirectReplaceProperty(Shape shape, Property oldProperty, Property newProperty) {
        assert !shape.isShared();
        assert oldProperty.getKey().equals(newProperty.getKey());
        Object key = newProperty.getKey();

        var replacePropertyTransition = new Transition.IndirectReplacePropertyTransition(oldProperty, newProperty);
        shape.onPropertyTransition(replacePropertyTransition);
        Shape cachedShape = shape.queryTransition(replacePropertyTransition);
        if (cachedShape != null) {
            return cachedShape;
        }

        Shape oldParent = shape;
        Shape root = shape.getRoot();
        List<Transition> transitionList = new ArrayList<>();
        Shape newParent = null;

        while (oldParent != root) {
            Transition transition = oldParent.getTransitionFromParent();
            if (transition instanceof AddPropertyTransition) {
                if (((AddPropertyTransition) transition).getPropertyKey().equals(key)) {
                    newParent = applyTransition(oldParent.getParent(), newAddPropertyTransition(newProperty), false);
                    break;
                }
            } else if (transition instanceof DirectReplacePropertyTransition replaceTransition) {
                if (replaceTransition.getPropertyKey().equals(key)) {
                    newParent = applyTransition(oldParent.getParent(), new DirectReplacePropertyTransition(replaceTransition.getProperty(), newProperty), false);
                    break;
                }
            }
            transitionList.add(transition);
            oldParent = oldParent.getParent();
        }

        if (newParent == null) {
            throw new IllegalArgumentException("property not found");
        }

        Shape newShape = newParent;
        boolean obsolete = false;
        for (ListIterator<Transition> iterator = transitionList.listIterator(transitionList.size()); iterator.hasPrevious();) {
            if (!newShape.isValid()) {
                obsolete = true;
            }
            Transition transition = iterator.previous();
            newShape = applyTransition(newShape, transition, false);
            assert !(transition instanceof AddPropertyTransition) || ((AddPropertyTransition) transition).getProperty().equals(newShape.getLastProperty());
            if (obsolete && newShape.isValid()) {
                // invariant: if a shape has an obsolete ancestor, it has to be obsolete, too.
                newShape.invalidateValidAssumption();
            }
        }

        newShape = shape.addIndirectTransition(replacePropertyTransition, newShape);

        Property actualProperty = newShape.getProperty(key);
        ensureSameTypeOrMoreGeneral(actualProperty, newProperty);

        return newShape;
    }

    BaseAllocator createAllocator(Shape shape) {
        return new ExtAllocator(shape);
    }

    private Shape getObsoletedBy(Shape shape) {
        if (shape.isValid()) {
            return null;
        }

        assert !shape.isShared();
        Shape ret = shape.getSuccessorShape();
        while (ret == null || !ret.isValid()) {
            if (ret != null) {
                Shape next = ret.getSuccessorShape();
                assert ret != next; // cycle
                ret = next;
            } else {
                ret = makeSuccessorShape(shape);
                Obsolescence.setObsoletedBy(shape, ret);
                break;
            }
        }
        // the returned shape should be valid, but we cannot assert this due to a possible race
        return ret;
    }

    /**
     * Reshape this object from obsolete shape to successor shape. New shape has to be an upcast of
     * the old one.
     *
     * Migration is a two-step process: first we need to build the new shape, then copy the
     * properties over; this is due to append/put not necessarily progressing linear (may reorder
     * properties to make space for extension array).
     */
    private void reshape(DynamicObject store) {
        CompilerAsserts.neverPartOfCompilation();
        reshapeCount.inc();

        final Shape oldShape = store.getShape();
        assert !oldShape.isValid() && !oldShape.isShared();

        final Deque<Shape> affectedShapes = new ArrayDeque<>();
        Shape goodAncestor = oldShape;
        while (!goodAncestor.isValid()) {
            affectedShapes.addFirst(goodAncestor);
            goodAncestor = goodAncestor.getParent();
        }

        final Shape offendingShape = affectedShapes.removeFirst();
        final Shape obsoletedBy = getObsoletedBy(offendingShape);

        if (TraceReshape) {
            out().printf("RESHAPE\nGOOD ANCESTOR: %s\nOFFENDING SHAPE: %s\nOBSOLETED BY: %s\n",
                            goodAncestor.toStringLimit(TRACE_RESHAPE_LIMIT),
                            offendingShape.toStringLimit(TRACE_RESHAPE_LIMIT),
                            obsoletedBy.toStringLimit(TRACE_RESHAPE_LIMIT));
        }

        Shape newShape = obsoletedBy;

        for (Shape affectedShape : affectedShapes) {
            Transition transition = affectedShape.getTransitionFromParent();
            newShape = applyTransition(newShape, transition, true);
            // shape should be valid, but we cannot assert this due to a possible race
        }

        List<Object> toCopy = prepareCopy(store, oldShape, newShape);

        try {
            resizeStore(store, oldShape, newShape);

            performCopy(store, toCopy);

            DynamicObjectSupport.setShapeWithStoreFence(store, newShape);
            assert store.getShape() == newShape;
        } catch (StackOverflowError e) {
            throw STACK_OVERFLOW_ERROR;
        }

        if (TraceReshape) {
            while (!goodAncestor.isValid()) {
                goodAncestor = goodAncestor.getParent();
            }

            out().printf("OLD %s\nNEW %s\nLCA %s\nDIFF %s\n---\n",
                            oldShape.toStringLimit(TRACE_RESHAPE_LIMIT),
                            newShape.toStringLimit(TRACE_RESHAPE_LIMIT),
                            goodAncestor.toStringLimit(TRACE_RESHAPE_LIMIT),
                            diffToString(oldShape, newShape));
        }

        assert checkExtensionArrayInvariants(store, newShape);
    }

    private static void resizeStore(DynamicObject store, final Shape oldShape, Shape newShape) {
        DynamicObjectSupport.resize(store, oldShape, newShape);
    }

    static boolean checkExtensionArrayInvariants(DynamicObject store, Shape newShape) {
        assert store.getShape() == newShape;
        Object[] objectArray = store.getObjectStore();
        assert (objectArray == null && newShape.getObjectArrayCapacity() == 0) || (objectArray != null && objectArray.length == newShape.getObjectArrayCapacity());
        if (newShape.hasPrimitiveArray()) {
            int[] primitiveArray = store.getPrimitiveStore();
            assert (primitiveArray == null && newShape.getPrimitiveArrayCapacity() == 0) || (primitiveArray != null && primitiveArray.length == newShape.getPrimitiveArrayCapacity());
        }
        return true;
    }

    private Shape makeSuccessorShape(Shape oldShape) {
        makeSuccessorShapeCount.inc();

        assert !oldShape.isValid();

        final Deque<Shape> affectedShapes = new ArrayDeque<>();
        Shape goodAncestor = oldShape;
        while (!goodAncestor.isValid()) {
            affectedShapes.addFirst(goodAncestor);
            goodAncestor = goodAncestor.getParent();
        }

        final Shape offendingShape = affectedShapes.removeFirst();
        final Shape obsoletedBy = getObsoletedBy(offendingShape);

        if (TraceReshape) {
            out().printf("REBUILDING SHAPE: %s\nGOOD ANCESTOR: %s\nOFFENDING SHAPE: %s\nOBSOLETED BY: %s\n",
                            oldShape.toStringLimit(TRACE_RESHAPE_LIMIT),
                            goodAncestor.toStringLimit(TRACE_RESHAPE_LIMIT),
                            offendingShape.toStringLimit(TRACE_RESHAPE_LIMIT),
                            obsoletedBy.toStringLimit(TRACE_RESHAPE_LIMIT));
        }

        Shape newShape = obsoletedBy;

        for (Shape affectedShape : affectedShapes) {
            Transition transition = affectedShape.getTransitionFromParent();
            newShape = applyTransition(newShape, transition, true);
            // shape should be valid, but we cannot assert this due to a possible race
        }

        if (TraceReshape) {
            while (!goodAncestor.isValid()) {
                goodAncestor = goodAncestor.getParent();
            }

            out().printf("OLD %s\nNEW %s\nLCA %s\nDIFF %s\n---\n",
                            oldShape.toStringLimit(TRACE_RESHAPE_LIMIT),
                            newShape.toStringLimit(TRACE_RESHAPE_LIMIT),
                            goodAncestor.toStringLimit(TRACE_RESHAPE_LIMIT),
                            diffToString(oldShape, newShape));
        }

        return newShape;
    }

    private static List<Object> prepareCopy(DynamicObject fromObject, Shape fromShape, Shape toShape) {
        List<Object> toCopy = new ArrayList<>();
        PropertyMap fromMap = fromShape.getPropertyMap();
        for (Iterator<Property> toMapIt = toShape.getPropertyMap().orderedValueIterator(); toMapIt.hasNext();) {
            Property toProperty = toMapIt.next();
            Property fromProperty = fromMap.get(toProperty.getKey());

            // copy only if property has a location and it's not the same as the source location
            if (!toProperty.getLocation().isValue() && !toProperty.getLocation().equals(fromProperty.getLocation())) {
                Object value = fromProperty.getLocation().get(fromObject, false);
                toCopy.add(toProperty);
                toCopy.add(value);
            }
        }
        return toCopy;
    }

    private static void performCopy(DynamicObject toObject, List<Object> toCopy) {
        for (int i = 0; i < toCopy.size(); i += 2) {
            Property toProperty = (Property) toCopy.get(i);
            Object value = toCopy.get(i + 1);
            setPropertyInternal(toProperty, toObject, value);
        }
    }

    private static void setPropertyInternal(Property toProperty, DynamicObject toObject, Object value) {
        toProperty.getLocation().set(toObject, value, false, true);
    }

    private boolean checkForObsoleteShapeAndMigrate(DynamicObject store) {
        Shape currentShape = store.getShape();
        if (currentShape.isValid()) {
            return false;
        }
        CompilerDirectives.transferToInterpreter();
        return migrateObsoleteShape(currentShape, store);
    }

    private boolean migrateObsoleteShape(Shape currentShape, DynamicObject store) {
        CompilerAsserts.neverPartOfCompilation();
        synchronized (currentShape.getMutex()) {
            if (!currentShape.isValid()) {
                assert !currentShape.isShared();
                reshape(store);
                return true;
            }
            return false;
        }
    }

    private Shape rebuildObsoleteShape(Shape oldShape, Shape owningShape) {
        assert !owningShape.isValid();
        if (oldShape.isValid()) {
            // The shape is not marked obsolete despite the parent shape owning the property being
            // obsolete. Usually, when a shape is obsoleted, all child shapes are obsoleted, too.
            // But there can be race between the shape and its descendants being marked obsolete
            // and a new transition being added.
            // Correct the situation by marking all shapes descending from this parent obsolete.
            Shape current = oldShape;
            while (current != owningShape && current != null) {
                Obsolescence.invalidateShape(current);
                current = current.getParent();
            }
        }
        assert !oldShape.isValid();
        return makeSuccessorShape(oldShape);
    }

    private Shape generalizeHelper(Property currentProperty, Object value, Shape currentShape, int putFlags) {
        Shape oldShape = currentShape;
        Property oldProperty = currentProperty;
        assert !oldProperty.getLocation().canStore(value);
        while (true) { // TERMINATION ARGUMENT: loop will terminate once value can be stored
            final Shape owningShape = getOwningShape(oldShape, oldProperty);
            synchronized (oldShape.getMutex()) {
                if (owningShape.isValid()) {
                    Shape oldParentShape = owningShape.getParent();
                    Location newLocation = oldParentShape.allocator().locationForValueUpcast(value, oldProperty.getLocation(), putFlags);
                    Property newProperty = Property.create(oldProperty.getKey(), newLocation, oldProperty.getFlags());
                    return obsoleteAndMakeShapeWithProperty(oldProperty, oldShape, owningShape, newProperty);
                } else {
                    Shape newShape = rebuildObsoleteShape(oldShape, owningShape);
                    Property newPropertyAfterReshape = newShape.getProperty(oldProperty.getKey());
                    if (newPropertyAfterReshape.getLocation().canStore(value)) {
                        return newShape;
                    } else {
                        oldShape = newShape;
                        oldProperty = newPropertyAfterReshape;
                    }
                    /*
                     * If the new shape cannot store the new value, it means there was a race that
                     * either caused the property to be moved to a not general enough location or,
                     * more likely, another shape higher up the parent chain was obsoleted
                     * concurrently, generalizing a different property than the one we are about to
                     * assign. So we work our way down from the first invalid parent to the shape
                     * owning the property until we have generalized that property to a location
                     * that can store the new value. As a result, multiple properties may have been
                     * generalized by this operation (and will be migrated before the value is set).
                     */
                    // Possible scenario:
                    // Thread 1: var x = {}; x.a = 0, x.b = 0; x.a = 'string';
                    // Thread 2: var x = {}; x.a = 0, x.b = 0; x.b = 0.5;
                    /*
                     * Thread 1 begins to generalize 'a', when thread 2, on the same (still valid)
                     * shape, begins to generalize 'b'. Thread 2 then sees the parent shape owning
                     * 'b' as obsolete (because thread 1 obsoleted its parent owning 'a') and tries
                     * to rebuild a valid shape, but that actually produces a shape that generalized
                     * only 'a' and still cannot fit 'b'. So now thread 2 needs to restart the
                     * process with the new shape and obsolete the now-valid shape owning 'b' and
                     * generalize it to fit the new value of 'b'. In the end, all threads involved
                     * should agree on a shape that can fit the values of either.
                     */
                }
            }
        }
    }

    private Shape obsoleteAndMakeShapeWithProperty(Property oldProperty, Shape oldShape, Shape owningShape, Property newProperty) {
        Shape newOwningShape = makeNewOwningShape(owningShape, newProperty);
        assert owningShape != newOwningShape;
        // both owning shapes should be valid, but we cannot assert this due to a possible race
        Obsolescence.markObsolete(owningShape, newOwningShape, oldProperty, newProperty);
        return rebuildObsoleteShape(oldShape, owningShape);
    }

    private Shape makeNewOwningShape(Shape owningShape, Property newProperty) {
        Shape oldParentShape = owningShape.getParent();
        Transition transitionFromParent = owningShape.getTransitionFromParent();
        if (transitionFromParent instanceof DirectReplacePropertyTransition) {
            return directReplaceProperty(oldParentShape, ((AbstractReplacePropertyTransition) transitionFromParent).getPropertyBefore(), newProperty);
        } else {
            assert transitionFromParent instanceof AddPropertyTransition;
            return oldParentShape.addProperty(newProperty);
        }
    }

    private static Shape generalizeHelperWithShape(Property oldProperty, Object value, Shape oldShapeBefore, Shape oldShapeAfter, int putFlags) {
        assert !(oldProperty.getLocation().isDeclared());
        Location newLocation = oldShapeBefore.allocator().locationForValueUpcast(value, oldProperty.getLocation(), putFlags);
        Property newProperty = Property.create(oldProperty.getKey(), newLocation, oldProperty.getFlags());
        synchronized (oldShapeBefore.getMutex()) {
            final Shape newShapeAfter = oldShapeBefore.addProperty(newProperty);
            assert oldShapeAfter != newShapeAfter;
            Obsolescence.markObsolete(oldShapeAfter, newShapeAfter, oldProperty, newProperty);
            assert oldProperty.getKey().equals(newShapeAfter.getLastProperty().getKey());
            assert newShapeAfter.getLastProperty().equals(newShapeAfter.getProperty(oldProperty.getKey()));
            return newShapeAfter;
        }
    }

    /**
     * Get the (parent) shape that holds the given property.
     */
    private static Shape getOwningShape(Shape shape, Property prop) {
        Shape current = shape;
        Shape root = shape.getRoot();
        while (current != root) {
            Transition transitionFromParent = current.getTransitionFromParent();
            if (transitionFromParent instanceof AddPropertyTransition) {
                if (((AddPropertyTransition) transitionFromParent).getProperty().equals(prop)) {
                    return current;
                }
            } else if (transitionFromParent instanceof DirectReplacePropertyTransition) {
                if (((DirectReplacePropertyTransition) transitionFromParent).getPropertyAfter().equals(prop)) {
                    return current;
                }
            }
            current = current.getParent();
        }

        return null;
    }

    private static PrintStream out() {
        return System.out;
    }

    public static Map<Object, Pair<Property, Property>> findPropertyDifferences(Shape oldShape, Shape newShape) {
        List<Property> oldList = oldShape.getPropertyListInternal(true);
        List<Property> newList = newShape.getPropertyListInternal(true);

        Map<Object, Pair<Property, Property>> diff = new LinkedHashMap<>(Math.max(oldList.size(), newList.size()));
        for (Property p : oldList) {
            diff.put(p.getKey(), Pair.createLeft(p));
        }
        for (Property p : newList) {
            diff.merge(p.getKey(), Pair.createRight(p), (lp, rp) -> Pair.create(lp.getLeft(), rp.getRight()));
        }
        for (Iterator<Pair<Property, Property>> iterator = diff.values().iterator(); iterator.hasNext();) {
            Pair<Property, Property> pair = iterator.next();
            if (Objects.equals(pair.getLeft(), pair.getRight())) {
                iterator.remove();
            }
        }
        return diff;
    }

    public static String diffToString(Shape oldShape, Shape newShape) {
        return findPropertyDifferences(oldShape, newShape).values().stream().//
                        map(p -> p.getLeft() + " => " + p.getRight()).//
                        collect(Collectors.joining(",\n", "[", "]"));
    }
}
