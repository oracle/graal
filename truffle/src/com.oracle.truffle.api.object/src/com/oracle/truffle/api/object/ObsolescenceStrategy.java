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

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Transition.AbstractReplacePropertyTransition;
import com.oracle.truffle.api.object.Transition.AddPropertyTransition;
import com.oracle.truffle.api.object.Transition.DirectReplacePropertyTransition;

@SuppressWarnings("deprecation")
final class ObsolescenceStrategy extends LayoutStrategy {
    private static final DebugCounter makeSuccessorShapeCount = DebugCounter.create("Rebuild shape count");
    private static final DebugCounter reshapeCount = DebugCounter.create("Reshape count");
    private static final int TRACE_RESHAPE_LIMIT = 500;

    @SuppressWarnings("serial") private static final Error STACK_OVERFLOW_ERROR = new NonrecoverableError();

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

    private static final ObsolescenceStrategy SINGLETON = new ObsolescenceStrategy();

    private ObsolescenceStrategy() {
    }

    static ObsolescenceStrategy singleton() {
        return SINGLETON;
    }

    @Override
    protected boolean updateShape(DynamicObject object) {
        boolean changed = checkForObsoleteShapeAndMigrate(object);
        // shape should be valid now, but we cannot assert this due to a possible race
        return changed;
    }

    @Override
    protected Shape ensureValid(Shape newShape) {
        Shape nextShape = newShape;
        // if it's been obsoleted (cached shape), skip over
        if (!nextShape.isValid()) {
            nextShape = getObsoletedBy(nextShape);
        }
        // shape should be valid now, but we cannot assert this due to a possible race
        return nextShape;
    }

    @Override
    protected Shape definePropertyGeneralize(Shape oldShape, Property oldProperty, Object value, int putFlags) {
        return super.definePropertyGeneralize(oldShape, oldProperty, value, putFlags);
    }

    @Override
    protected Shape generalizeProperty(Property oldProperty, Object value, Shape oldShape, Shape newShape, int putFlags) {
        if (oldShape.isShared() || oldProperty.getLocation().isValue()) {
            return super.generalizeProperty(oldProperty, value, oldShape, newShape, putFlags);
        } else {
            if (oldShape == newShape) {
                return generalizeHelper(oldProperty, value, oldShape, putFlags);
            } else {
                return generalizeHelperWithShape(oldProperty, value, oldShape, newShape, putFlags);
            }
        }
    }

    @Override
    protected Shape generalizePropertyWithFlags(Shape oldShape, Property oldProperty, Object value, int propertyFlags, int putFlags) {
        if (oldShape.isShared() || oldProperty.getLocation().isValue()) {
            return super.generalizePropertyWithFlags(oldShape, oldProperty, value, propertyFlags, putFlags);
        } else {
            Shape generalizedShape = generalizeHelper(oldProperty, value, oldShape, putFlags);
            Property generalizedProperty = generalizedShape.getProperty(oldProperty.getKey());
            Property propertyWithFlags = Property.create(oldProperty.getKey(), generalizedProperty.getLocation(), propertyFlags);
            return replaceProperty(generalizedShape, generalizedProperty, propertyWithFlags);
        }
    }

    @Override
    protected Shape replaceProperty(Shape shape, Property oldProperty, Property newProperty) {
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

    @Override
    protected BaseAllocator createAllocator(Shape shape) {
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
     * Migration is a two step process: first we need to build the new shape, then copy the
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
        ((LocationImpl) toProperty.getLocation()).set(toObject, value, false, true);
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
