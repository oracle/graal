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

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.object.ShapeListener;
import com.oracle.truffle.api.utilities.NeverValidAssumption;
import com.oracle.truffle.object.LocationImpl.LocationVisitor;
import com.oracle.truffle.object.Locations.ConstantLocation;
import com.oracle.truffle.object.Locations.DeclaredLocation;
import com.oracle.truffle.object.Locations.ValueLocation;
import com.oracle.truffle.object.Transition.AddPropertyTransition;
import com.oracle.truffle.object.Transition.ObjectTypeTransition;
import com.oracle.truffle.object.Transition.PropertyTransition;
import com.oracle.truffle.object.Transition.ShareShapeTransition;

/**
 * Shape objects create a mapping of Property objects to indexes. The mapping of those indexes to an
 * actual store is not part of Shape's role, but DynamicObject's. Shapes are immutable; adding or
 * deleting a property yields a new Shape which links to the old one. This allows inline caching to
 * simply check the identity of an object's Shape to determine if the cache is valid. There is one
 * exception to this immutability, the transition map, but that is used simply to assure that an
 * identical series of property additions and deletions will yield the same Shape object.
 *
 * @see DynamicObject
 * @see Property
 * @see Locations
 * @since 0.17 or earlier
 */
public abstract class ShapeImpl extends Shape {
    private final int id;

    /** @since 0.17 or earlier */
    protected final LayoutImpl layout;
    /** @since 0.17 or earlier */
    protected final ObjectType objectType;
    /** @since 0.17 or earlier */
    protected final ShapeImpl parent;
    /** @since 0.17 or earlier */
    protected final PropertyMap propertyMap;

    private final Object sharedData;
    private final ShapeImpl root;

    /** @since 0.17 or earlier */
    protected final int objectArraySize;
    /** @since 0.17 or earlier */
    protected final int objectArrayCapacity;
    /** @since 0.17 or earlier */
    protected final int objectFieldSize;
    /** @since 0.17 or earlier */
    protected final int primitiveFieldSize;
    /** @since 0.17 or earlier */
    protected final int primitiveArraySize;
    /** @since 0.17 or earlier */
    protected final int primitiveArrayCapacity;
    /** @since 0.17 or earlier */
    protected final boolean hasPrimitiveArray;

    /** @since 0.18 */
    protected final boolean shared;

    /** @since 0.17 or earlier */
    protected final int depth;
    /** @since 0.17 or earlier */
    protected final int propertyCount;

    /** @since 0.17 or earlier */
    protected final Assumption validAssumption;
    /** @since 0.17 or earlier */
    @CompilationFinal protected volatile Assumption leafAssumption;

    /**
     * Shape transition map; lazily initialized. One of:
     * <ol>
     * <li>{@code null}: empty map
     * <li>{@link Map.Entry}: immutable single entry map
     * <li>{@link Map}: mutable multiple entry map
     * </ol>
     *
     * @see #getTransitionMapForRead()
     * @see #addTransitionInternal(Transition, ShapeImpl)
     */
    private volatile Object transitionMap;

    private final Transition transitionFromParent;

    private static final AtomicReferenceFieldUpdater<ShapeImpl, Object> TRANSITION_MAP_UPDATER = AtomicReferenceFieldUpdater.newUpdater(ShapeImpl.class, Object.class, "transitionMap");
    private static final AtomicReferenceFieldUpdater<ShapeImpl, Assumption> LEAF_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(ShapeImpl.class, Assumption.class, "leafAssumption");

    /**
     * Private constructor.
     *
     * @param parent predecessor shape
     * @param transitionFromParent direct transition from parent shape
     *
     * @see #ShapeImpl(Layout, ShapeImpl, ObjectType, Object, PropertyMap, Transition,
     *      BaseAllocator, int)
     */
    private ShapeImpl(Layout layout, ShapeImpl parent, ObjectType objectType, Object sharedData, PropertyMap propertyMap, Transition transitionFromParent, int objectArraySize, int objectFieldSize,
                    int primitiveFieldSize, int primitiveArraySize, boolean hasPrimitiveArray, int id) {
        this.layout = (LayoutImpl) layout;
        this.objectType = Objects.requireNonNull(objectType);
        this.propertyMap = Objects.requireNonNull(propertyMap);
        this.root = parent != null ? parent.getRoot() : this;
        this.parent = parent;

        this.objectArraySize = objectArraySize;
        this.objectArrayCapacity = capacityFromSize(objectArraySize);
        this.objectFieldSize = objectFieldSize;
        this.primitiveFieldSize = primitiveFieldSize;
        this.primitiveArraySize = primitiveArraySize;
        this.primitiveArrayCapacity = capacityFromSize(primitiveArraySize);
        this.hasPrimitiveArray = hasPrimitiveArray;
        this.shared = transitionFromParent instanceof ShareShapeTransition || (parent != null && parent.shared);

        if (parent != null) {
            this.propertyCount = makePropertyCount(parent, propertyMap);
            this.depth = parent.depth + 1;
        } else {
            this.propertyCount = 0;
            this.depth = 0;
        }

        this.validAssumption = createValidAssumption();

        this.id = id;
        this.transitionFromParent = transitionFromParent;
        this.sharedData = sharedData;

        shapeCount.inc();
        if (ObjectStorageOptions.DumpShapes) {
            Debug.trackShape(this);
        }
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl(Layout layout, ShapeImpl parent, ObjectType operations, Object sharedData, PropertyMap propertyMap, Transition transition, Allocator allocator, int id) {
        this(layout, parent, operations, sharedData, propertyMap, transition, ((BaseAllocator) allocator).objectArraySize, ((BaseAllocator) allocator).objectFieldSize,
                        ((BaseAllocator) allocator).primitiveFieldSize, ((BaseAllocator) allocator).primitiveArraySize, ((BaseAllocator) allocator).hasPrimitiveArray, id);
    }

    /** @since 0.17 or earlier */
    @SuppressWarnings("hiding")
    protected abstract ShapeImpl createShape(Layout layout, Object sharedData, ShapeImpl parent, ObjectType operations, PropertyMap propertyMap, Transition transition, Allocator allocator, int id);

    /** @since 0.17 or earlier */
    protected ShapeImpl(Layout layout, ObjectType operations, Object sharedData, int id) {
        this(layout, null, operations, sharedData, PropertyMap.empty(), null, layout.createAllocator(), id);
    }

    private static int makePropertyCount(ShapeImpl parent, PropertyMap propertyMap) {
        if (propertyMap.size() > parent.propertyMap.size()) {
            Property lastProperty = propertyMap.getLastProperty();
            if (!lastProperty.isHidden()) {
                return parent.propertyCount + 1;
            }
        }
        return parent.propertyCount;
    }

    /** @since 0.17 or earlier */
    @Override
    public final Property getLastProperty() {
        return propertyMap.getLastProperty();
    }

    /** @since 0.17 or earlier */
    @Override
    public final int getId() {
        return this.id;
    }

    /**
     * Calculate array size for the given number of elements.
     */
    private static int capacityFromSize(int size) {
        if (size == 0) {
            return 0;
        } else if (size < 4) {
            return 4;
        } else if (size < 32) {
            return ((size + 7) / 8) * 8;
        } else {
            return ((size + 15) / 16) * 16;
        }
    }

    /** @since 0.17 or earlier */
    public final int getObjectArraySize() {
        return objectArraySize;
    }

    /** @since 0.17 or earlier */
    public final int getObjectFieldSize() {
        return objectFieldSize;
    }

    /** @since 0.17 or earlier */
    public final int getPrimitiveFieldSize() {
        return primitiveFieldSize;
    }

    /** @since 0.17 or earlier */
    public final int getObjectArrayCapacity() {
        return objectArrayCapacity;
    }

    /** @since 0.17 or earlier */
    public final int getPrimitiveArrayCapacity() {
        return primitiveArrayCapacity;
    }

    /** @since 0.17 or earlier */
    public final int getPrimitiveArraySize() {
        return primitiveArraySize;
    }

    /** @since 0.17 or earlier */
    public final boolean hasPrimitiveArray() {
        return hasPrimitiveArray;
    }

    /**
     * Get a property entry by string name.
     *
     * @param key the name to look up
     * @return a Property object, or null if not found
     * @since 0.17 or earlier
     */
    @Override
    @TruffleBoundary
    public Property getProperty(Object key) {
        return propertyMap.get(key);
    }

    /** @since 0.17 or earlier */
    public final PropertyMap getPropertyMap() {
        return propertyMap;
    }

    /** @since 0.17 or earlier */
    public final void addDirectTransition(Transition transition, ShapeImpl next) {
        assert next.getParent() == this && transition.isDirect();
        addTransitionInternal(transition, next);
    }

    /** @since 0.17 or earlier */
    public final void addIndirectTransition(Transition transition, ShapeImpl next) {
        assert !isShared();
        assert next.getParent() != this && !transition.isDirect();
        addTransitionInternal(transition, next);
    }

    @SuppressWarnings("unchecked")
    private void addTransitionInternal(Transition transition, ShapeImpl successor) {
        Object prev;
        Object next;
        do {
            prev = TRANSITION_MAP_UPDATER.get(this);
            if (prev == null) {
                invalidateLeafAssumption();
                next = new AbstractMap.SimpleImmutableEntry<>(transition, successor);
            } else if (prev instanceof Map.Entry<?, ?>) {
                Map.Entry<Transition, ShapeImpl> entry = (Map.Entry<Transition, ShapeImpl>) prev;
                ConcurrentHashMap<Transition, ShapeImpl> map = new ConcurrentHashMap<>();
                map.put(entry.getKey(), entry.getValue());
                map.put(transition, successor);
                next = map;
            } else {
                assert prev instanceof Map<?, ?>;
                Map<Transition, ShapeImpl> map = (Map<Transition, ShapeImpl>) prev;
                map.put(transition, successor);
                break;
            }
        } while (!TRANSITION_MAP_UPDATER.compareAndSet(this, prev, next));
    }

    /** @since 0.17 or earlier */
    @SuppressWarnings("unchecked")
    public final Map<Transition, ShapeImpl> getTransitionMapForRead() {
        Object trans = transitionMap;
        if (trans == null) {
            return Collections.<Transition, ShapeImpl> emptyMap();
        } else if (trans instanceof Map.Entry<?, ?>) {
            Map.Entry<Transition, ShapeImpl> entry = (Map.Entry<Transition, ShapeImpl>) trans;
            return Collections.singletonMap(entry.getKey(), entry.getValue());
        } else {
            assert trans instanceof Map<?, ?>;
            Map<Transition, ShapeImpl> map = (Map<Transition, ShapeImpl>) trans;
            return map;
        }
    }

    @SuppressWarnings("unchecked")
    private ShapeImpl queryTransitionImpl(Transition transition) {
        Object trans = transitionMap;
        if (trans == null) {
            return null;
        } else if (trans instanceof Map.Entry<?, ?>) {
            Map.Entry<Transition, ShapeImpl> entry = (Map.Entry<Transition, ShapeImpl>) trans;
            if (entry.getKey().equals(transition)) {
                return entry.getValue();
            } else {
                return null;
            }
        } else {
            assert trans instanceof Map<?, ?>;
            Map<Transition, ShapeImpl> map = (Map<Transition, ShapeImpl>) trans;
            return map.get(transition);
        }
    }

    /** @since 0.17 or earlier */
    public final ShapeImpl queryTransition(Transition transition) {
        ShapeImpl cachedShape = queryTransitionImpl(transition);
        if (cachedShape != null) {
            shapeCacheHitCount.inc();
            return cachedShape;
        }
        shapeCacheMissCount.inc();

        return null;
    }

    /**
     * Add a new property in the map, yielding a new or cached Shape object.
     *
     * @param property the property to add
     * @return the new Shape
     * @since 0.17 or earlier
     */
    @TruffleBoundary
    @Override
    public ShapeImpl addProperty(Property property) {
        assert isValid();
        onPropertyTransition(property);

        return layout.getStrategy().addProperty(this, property);
    }

    /** @since 0.17 or earlier */
    protected void onPropertyTransition(Property property) {
        if (sharedData instanceof ShapeListener) {
            ((ShapeListener) sharedData).onPropertyTransition(property.getKey());
        }
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public ShapeImpl defineProperty(Object key, Object value, int flags) {
        return defineProperty(key, value, flags, layout.getStrategy().getDefaultLocationFactory());
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public ShapeImpl defineProperty(Object key, Object value, int flags, LocationFactory locationFactory) {
        return layout.getStrategy().defineProperty(this, key, value, flags, locationFactory);
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl cloneRoot(ShapeImpl from, Object newSharedData) {
        return createShape(from.layout, newSharedData, null, from.objectType, from.propertyMap, null, from.allocator(), from.id);
    }

    /**
     * Create a separate clone of a shape.
     *
     * @param newParent the cloned parent shape
     * @since 0.17 or earlier
     */
    protected final ShapeImpl cloneOnto(ShapeImpl newParent) {
        ShapeImpl from = this;
        ShapeImpl newShape = createShape(newParent.layout, newParent.sharedData, newParent, from.objectType, from.propertyMap, from.transitionFromParent, from.allocator(), newParent.id);

        shapeCloneCount.inc();

        newParent.addDirectTransition(from.transitionFromParent, newShape);
        return newShape;
    }

    /** @since 0.17 or earlier */
    public final Transition getTransitionFromParent() {
        return transitionFromParent;
    }

    /**
     * Create a new shape that adds a property to the parent shape.
     *
     * @since 0.17 or earlier
     */
    protected static ShapeImpl makeShapeWithAddedProperty(ShapeImpl parent, AddPropertyTransition addTransition) {
        Property addend = addTransition.getProperty();
        BaseAllocator allocator = parent.allocator().addLocation(addend.getLocation());

        PropertyMap newPropertyMap = parent.propertyMap.putCopy(addend);

        ShapeImpl newShape = parent.createShape(parent.layout, parent.sharedData, parent, parent.objectType, newPropertyMap, addTransition, allocator, parent.id);
        assert ((LocationImpl) addend.getLocation()).primitiveArrayCount() == 0 || newShape.hasPrimitiveArray;
        assert newShape.depth == allocator.depth;
        return newShape;
    }

    /**
     * Create a new shape that reserves the primitive extension array field.
     *
     * @since 0.17 or earlier
     */
    protected static ShapeImpl makeShapeWithPrimitiveExtensionArray(ShapeImpl parent, Transition transition) {
        assert parent.getLayout().hasPrimitiveExtensionArray();
        assert !parent.hasPrimitiveArray();
        BaseAllocator allocator = parent.allocator().addLocation(parent.getLayout().getPrimitiveArrayLocation());
        ShapeImpl newShape = parent.createShape(parent.layout, parent.sharedData, parent, parent.objectType, parent.propertyMap, transition, allocator, parent.id);
        assert newShape.hasPrimitiveArray();
        assert newShape.depth == allocator.depth;
        return newShape;
    }

    /**
     * Are these two shapes related, i.e. do they have the same root?
     *
     * @param other Shape to compare to
     * @return true if one shape is an upcast of the other, or the Shapes are equal
     * @since 0.17 or earlier
     */
    @Override
    public boolean isRelated(Shape other) {
        if (this == other) {
            return true;
        }
        if (this.getRoot() == getRoot()) {
            return true;
        }
        return false;
    }

    /**
     * Get a list of all properties that this Shape stores.
     *
     * @return list of properties
     * @since 0.17 or earlier
     */
    @TruffleBoundary
    @Override
    public final List<Property> getPropertyList(Pred<Property> filter) {
        ArrayDeque<Property> props = new ArrayDeque<>();
        for (Iterator<Property> it = this.propertyMap.reverseOrderedValueIterator(); it.hasNext();) {
            Property currentProperty = it.next();
            if (!currentProperty.isHidden() && filter.test(currentProperty)) {
                props.addFirst(currentProperty);
            }
        }
        return Arrays.asList(props.toArray(new Property[0]));
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public final List<Property> getPropertyList() {
        Property[] props = new Property[getPropertyCount()];
        int i = props.length;
        for (Iterator<Property> it = this.propertyMap.reverseOrderedValueIterator(); it.hasNext();) {
            Property currentProperty = it.next();
            if (!currentProperty.isHidden()) {
                props[--i] = currentProperty;
            }
        }
        return Arrays.asList(props);
    }

    /**
     * Returns all (also hidden) Property objects in this shape.
     *
     * @param ascending desired order
     * @since 0.17 or earlier
     */
    @TruffleBoundary
    @Override
    public final List<Property> getPropertyListInternal(boolean ascending) {
        Property[] props = new Property[this.propertyMap.size()];
        int i = ascending ? props.length : 0;
        for (Iterator<Property> it = this.propertyMap.reverseOrderedValueIterator(); it.hasNext();) {
            Property current = it.next();
            if (ascending) {
                props[--i] = current;
            } else {
                props[i++] = current;
            }
        }
        return Arrays.asList(props);
    }

    /**
     * Get a list of all (visible) property names in insertion order.
     *
     * @return list of property names
     * @since 0.17 or earlier
     */
    @TruffleBoundary
    @Override
    public final List<Object> getKeyList(Pred<Property> filter) {
        ArrayDeque<Object> keys = new ArrayDeque<>();
        for (Iterator<Property> it = this.propertyMap.reverseOrderedValueIterator(); it.hasNext();) {
            Property currentProperty = it.next();
            if (!currentProperty.isHidden() && filter.test(currentProperty)) {
                keys.addFirst(currentProperty.getKey());
            }
        }
        return Arrays.asList(keys.toArray(new Object[0]));
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public final List<Object> getKeyList() {
        Object[] props = new Object[getPropertyCount()];
        int i = props.length;
        for (Iterator<Property> it = this.propertyMap.reverseOrderedValueIterator(); it.hasNext();) {
            Property currentProperty = it.next();
            if (!currentProperty.isHidden()) {
                props[--i] = currentProperty.getKey();
            }
        }
        return Arrays.asList(props);
    }

    /** @since 0.17 or earlier */
    @Override
    public Iterable<Object> getKeys() {
        return getKeyList();
    }

    /** @since 0.17 or earlier */
    @Override
    public final boolean isValid() {
        return getValidAssumption().isValid();
    }

    /** @since 0.17 or earlier */
    @Override
    public final Assumption getValidAssumption() {
        return validAssumption;
    }

    private static Assumption createValidAssumption() {
        return Truffle.getRuntime().createAssumption("valid shape");
    }

    /** @since 0.17 or earlier */
    public final void invalidateValidAssumption() {
        getValidAssumption().invalidate();
    }

    /** @since 0.17 or earlier */
    @Override
    public final boolean isLeaf() {
        Assumption assumption = leafAssumption;
        return assumption == null || assumption.isValid();
    }

    /** @since 0.17 or earlier */
    @Override
    public final Assumption getLeafAssumption() {
        Assumption assumption = leafAssumption;
        if (assumption != null) {
            return assumption;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Assumption prev;
            Assumption next;
            do {
                prev = LEAF_ASSUMPTION_UPDATER.get(this);
                if (prev != null) {
                    return prev;
                } else {
                    boolean isLeafShape = transitionMap == null;
                    next = isLeafShape ? createLeafAssumption() : NeverValidAssumption.INSTANCE;
                }
            } while (!LEAF_ASSUMPTION_UPDATER.compareAndSet(this, prev, next));
            return next;
        }
    }

    private static Assumption createLeafAssumption() {
        return Truffle.getRuntime().createAssumption("leaf shape");
    }

    private void invalidateLeafAssumption() {
        Assumption prev;
        do {
            prev = LEAF_ASSUMPTION_UPDATER.get(this);
            if (prev == NeverValidAssumption.INSTANCE) {
                break;
            }
            if (prev != null) {
                prev.invalidate();
            }
        } while (!LEAF_ASSUMPTION_UPDATER.compareAndSet(this, prev, NeverValidAssumption.INSTANCE));
    }

    /** @since 0.17 or earlier */
    @Override
    public String toString() {
        return toStringLimit(Integer.MAX_VALUE);
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    public String toStringLimit(int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append('@');
        sb.append(Integer.toHexString(hashCode()));
        if (!isValid()) {
            sb.append('!');
        }

        sb.append("{");
        for (Iterator<Property> iterator = propertyMap.reverseOrderedValueIterator(); iterator.hasNext();) {
            Property p = iterator.next();
            sb.append(p);
            if (iterator.hasNext()) {
                sb.append(",");
            }
            if (sb.length() >= limit) {
                sb.append("...");
                break;
            }
            sb.append("\n");
        }
        sb.append("}");

        return sb.toString();
    }

    /** @since 0.17 or earlier */
    @Override
    public final ShapeImpl getParent() {
        return parent;
    }

    /** @since 0.17 or earlier */
    public final int getDepth() {
        return depth;
    }

    /** @since 0.17 or earlier */
    @Override
    public final boolean hasProperty(Object name) {
        return getProperty(name) != null;
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public final ShapeImpl removeProperty(Property prop) {
        assert isValid();
        if (shared) {
            throw new UnsupportedOperationException("Do not use delete() with a shared shape as it moves locations");
        }
        onPropertyTransition(prop);

        return layout.getStrategy().removeProperty(this, prop);
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public final ShapeImpl append(Property oldProperty) {
        return addProperty(oldProperty.relocate(allocator().moveLocation(oldProperty.getLocation())));
    }

    /** @since 0.17 or earlier */
    @Override
    public final BaseAllocator allocator() {
        return layout.getStrategy().createAllocator(this);
    }

    /**
     * Duplicate shape exchanging existing property with new property.
     *
     * @since 0.17 or earlier
     */
    @Override
    public ShapeImpl replaceProperty(Property oldProperty, Property newProperty) {
        assert oldProperty.getKey().equals(newProperty.getKey());
        onPropertyTransition(oldProperty);

        return layout.getStrategy().replaceProperty(this, oldProperty, newProperty);
    }

    /**
     * Find lowest common ancestor of two related shapes.
     *
     * @since 0.17 or earlier
     */

    public static ShapeImpl findCommonAncestor(ShapeImpl left, ShapeImpl right) {
        if (!left.isRelated(right)) {
            throw new IllegalArgumentException("shapes must have the same root");
        } else if (left == right) {
            return left;
        }
        int leftLength = left.depth;
        int rightLength = right.depth;
        ShapeImpl leftPtr = left;
        ShapeImpl rightPtr = right;
        while (leftLength > rightLength) {
            leftPtr = leftPtr.parent;
            leftLength--;
        }
        while (rightLength > leftLength) {
            rightPtr = rightPtr.parent;
            rightLength--;
        }
        while (leftPtr != rightPtr) {
            leftPtr = leftPtr.parent;
            rightPtr = rightPtr.parent;
        }
        return leftPtr;
    }

    /** @since 0.17 or earlier */
    @Override
    public final int getPropertyCount() {
        return propertyCount;
    }

    /**
     * Find difference between two shapes.
     *
     * @see ObjectStorageOptions#TraceReshape
     * @since 0.17 or earlier
     */
    public static List<Property> diff(Shape oldShape, Shape newShape) {
        List<Property> oldList = oldShape.getPropertyListInternal(false);
        List<Property> newList = newShape.getPropertyListInternal(false);

        List<Property> diff = new ArrayList<>(oldList);
        diff.addAll(newList);
        List<Property> intersection = new ArrayList<>(oldList);
        intersection.retainAll(newList);
        diff.removeAll(intersection);
        return diff;
    }

    /** @since 0.17 or earlier */
    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    /** @since 0.17 or earlier */
    @Override
    public ShapeImpl getRoot() {
        return root;
    }

    /** @since 0.17 or earlier */
    @Override
    public final boolean check(DynamicObject subject) {
        return subject.getShape() == this;
    }

    /** @since 0.17 or earlier */
    @Override
    public final LayoutImpl getLayout() {
        return layout;
    }

    /** @since 0.17 or earlier */
    @Override
    public final Object getSharedData() {
        return sharedData;
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public final boolean hasTransitionWithKey(Object key) {
        for (Transition transition : getTransitionMapForRead().keySet()) {
            if (transition instanceof PropertyTransition) {
                if (((PropertyTransition) transition).getProperty().getKey().equals(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Clone off a separate shape with new shared data.
     *
     * @since 0.17 or earlier
     */
    @TruffleBoundary
    @Override
    public final ShapeImpl createSeparateShape(Object newSharedData) {
        if (parent == null) {
            return cloneRoot(this, newSharedData);
        } else {
            return this.cloneOnto(parent.createSeparateShape(newSharedData));
        }
    }

    /** @since 0.17 or earlier */
    @Override
    @TruffleBoundary
    public final ShapeImpl changeType(ObjectType newOps) {
        ObjectTypeTransition transition = new ObjectTypeTransition(newOps);
        ShapeImpl cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return layout.getStrategy().ensureValid(cachedShape);
        }

        ShapeImpl newShape = createShape(layout, sharedData, this, newOps, propertyMap, transition, allocator(), id);
        addDirectTransition(transition, newShape);
        return newShape;
    }

    /** @since 0.17 or earlier */
    @Override
    public final ShapeImpl reservePrimitiveExtensionArray() {
        if (layout.hasPrimitiveExtensionArray() && !hasPrimitiveArray()) {
            return layout.getStrategy().addPrimitiveExtensionArray(this);
        }
        return this;
    }

    /** @since 0.17 or earlier */
    @Override
    public final Iterable<Property> getProperties() {
        return getPropertyList();
    }

    /** @since 0.17 or earlier */
    @Override
    public final DynamicObject newInstance() {
        return layout.newInstance(this);
    }

    /** @since 0.17 or earlier */
    @Override
    public final DynamicObjectFactory createFactory() {
        List<Property> properties = getPropertyListInternal(true);
        List<Property> filtered = null;
        for (ListIterator<Property> iterator = properties.listIterator(); iterator.hasNext();) {
            Property property = iterator.next();
            // skip non-instance fields
            assert property.getLocation() != layout.getPrimitiveArrayLocation();
            if (property.getLocation() instanceof ValueLocation) {
                if (filtered == null) {
                    filtered = new ArrayList<>();
                    filtered.addAll(properties.subList(0, iterator.previousIndex()));
                }
            } else if (filtered != null) {
                filtered.add(property);
            }
        }

        if (filtered != null) {
            properties = filtered;
        }
        return new DynamicObjectFactoryImpl(this, properties);
    }

    /** @since 0.17 or earlier */
    @Override
    public Object getMutex() {
        return getRoot();
    }

    /** @since 0.17 or earlier */
    @Override
    public Shape tryMerge(Shape other) {
        return null;
    }

    /** @since 0.18 */
    @Override
    public boolean isShared() {
        return shared;
    }

    /** @since 0.18 */
    @Override
    public Shape makeSharedShape() {
        if (shared) {
            throw new UnsupportedOperationException("makeSharedShape() can only be called on non-shared shapes.");
        }

        Transition transition = new ShareShapeTransition();
        ShapeImpl cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return layout.getStrategy().ensureValid(cachedShape);
        }

        ShapeImpl newShape = createShape(layout, sharedData, this, objectType, propertyMap, transition, allocator(), id);
        addDirectTransition(transition, newShape);
        return newShape;
    }

    private static final class DynamicObjectFactoryImpl implements DynamicObjectFactory {
        private final ShapeImpl shape;
        @CompilationFinal(dimensions = 1) private final PropertyImpl[] instanceFields;
        private static final PropertyImpl[] EMPTY = new PropertyImpl[0];

        private DynamicObjectFactoryImpl(ShapeImpl shape, List<Property> properties) {
            this.shape = shape;
            this.instanceFields = properties.toArray(EMPTY);
        }

        @ExplodeLoop
        public DynamicObject newInstance(Object... initialValues) {
            assert initialValues.length == instanceFields.length : wrongArguments(initialValues.length);
            DynamicObject store = shape.newInstance();
            CompilerAsserts.partialEvaluationConstant(instanceFields.length);
            for (int i = 0; i < instanceFields.length; i++) {
                instanceFields[i].setInternal(store, initialValues[i]);
            }
            return store;
        }

        private String wrongArguments(int givenLength) {
            String message = givenLength + " arguments given but the factory takes " + instanceFields.length + ": ";
            for (int i = 0; i < instanceFields.length; i++) {
                message += instanceFields[i].getKey();
                if (i != instanceFields.length - 1) {
                    message += ", ";
                }
            }
            return message;
        }

        public Shape getShape() {
            return shape;
        }
    }

    /** @since 0.17 or earlier */
    public abstract static class BaseAllocator extends Allocator implements LocationVisitor, Cloneable {
        /** @since 0.17 or earlier */
        protected final LayoutImpl layout;
        /** @since 0.17 or earlier */
        protected int objectArraySize;
        /** @since 0.17 or earlier */
        protected int objectFieldSize;
        /** @since 0.17 or earlier */
        protected int primitiveFieldSize;
        /** @since 0.17 or earlier */
        protected int primitiveArraySize;
        /** @since 0.17 or earlier */
        protected boolean hasPrimitiveArray;
        /** @since 0.17 or earlier */
        protected int depth;
        /** @since 0.18 */
        protected boolean shared;

        /** @since 0.17 or earlier */
        protected BaseAllocator(LayoutImpl layout) {
            this.layout = layout;
        }

        /** @since 0.17 or earlier */
        protected BaseAllocator(ShapeImpl shape) {
            this(shape.getLayout());
            this.objectArraySize = shape.objectArraySize;
            this.objectFieldSize = shape.objectFieldSize;
            this.primitiveFieldSize = shape.primitiveFieldSize;
            this.primitiveArraySize = shape.primitiveArraySize;
            this.hasPrimitiveArray = shape.hasPrimitiveArray;
            this.depth = shape.depth;
            this.shared = shape.shared;
        }

        /** @since 0.17 or earlier */
        protected abstract Location moveLocation(Location oldLocation);

        /** @since 0.17 or earlier */
        protected abstract Location newObjectLocation(boolean useFinal, boolean nonNull);

        /** @since 0.17 or earlier */
        protected abstract Location newTypedObjectLocation(boolean useFinal, Class<?> type, boolean nonNull);

        /** @since 0.17 or earlier */
        protected abstract Location newIntLocation(boolean useFinal);

        /** @since 0.17 or earlier */
        protected abstract Location newDoubleLocation(boolean useFinal);

        /** @since 0.17 or earlier */
        protected abstract Location newLongLocation(boolean useFinal);

        /** @since 0.17 or earlier */
        protected abstract Location newBooleanLocation(boolean useFinal);

        /** @since 0.17 or earlier */
        @Override
        public final Location constantLocation(Object value) {
            return new ConstantLocation(value);
        }

        /** @since 0.17 or earlier */
        @Override
        public Location declaredLocation(Object value) {
            return new DeclaredLocation(value);
        }

        /** @since 0.17 or earlier */
        @Override
        protected Location locationForValue(Object value, boolean useFinal, boolean nonNull) {
            if (value instanceof Integer) {
                return newIntLocation(useFinal);
            } else if (value instanceof Double) {
                return newDoubleLocation(useFinal);
            } else if (value instanceof Long) {
                return newLongLocation(useFinal);
            } else if (value instanceof Boolean) {
                return newBooleanLocation(useFinal);
            } else if (ObjectStorageOptions.TypedObjectLocations && value != null) {
                return newTypedObjectLocation(useFinal, value.getClass(), nonNull);
            }
            return newObjectLocation(useFinal, nonNull && value != null);
        }

        /** @since 0.17 or earlier */
        protected abstract Location locationForValueUpcast(Object value, Location oldLocation);

        /** @since 0.17 or earlier */
        @Override
        protected Location locationForType(Class<?> type, boolean useFinal, boolean nonNull) {
            if (type == int.class) {
                return newIntLocation(useFinal);
            } else if (type == double.class) {
                return newDoubleLocation(useFinal);
            } else if (type == long.class) {
                return newLongLocation(useFinal);
            } else if (type == boolean.class) {
                return newBooleanLocation(useFinal);
            } else if (ObjectStorageOptions.TypedObjectLocations && type != null && type != Object.class) {
                assert !type.isPrimitive() : "unsupported primitive type";
                return newTypedObjectLocation(useFinal, type, nonNull);
            }
            return newObjectLocation(useFinal, nonNull);
        }

        /** @since 0.17 or earlier */
        protected <T extends Location> T advance(T location0) {
            if (location0 instanceof LocationImpl) {
                LocationImpl location = (LocationImpl) location0;
                if (location != layout.getPrimitiveArrayLocation()) {
                    location.accept(this);
                }
                if (layout.hasPrimitiveExtensionArray()) {
                    hasPrimitiveArray |= location == layout.getPrimitiveArrayLocation() || primitiveArraySize > 0;
                } else {
                    assert !hasPrimitiveArray && primitiveArraySize == 0;
                }
            }
            depth++;
            return location0;
        }

        /** @since 0.17 or earlier */
        @Override
        public BaseAllocator addLocation(Location location) {
            advance(location);
            return this;
        }

        /** @since 0.17 or earlier */
        public void visitObjectField(int index, int count) {
            objectFieldSize = Math.max(objectFieldSize, index + count);
        }

        /** @since 0.17 or earlier */
        public void visitObjectArray(int index, int count) {
            objectArraySize = Math.max(objectArraySize, index + count);
        }

        /** @since 0.17 or earlier */
        public void visitPrimitiveArray(int index, int count) {
            primitiveArraySize = Math.max(primitiveArraySize, index + count);
        }

        /** @since 0.17 or earlier */
        public void visitPrimitiveField(int index, int count) {
            primitiveFieldSize = Math.max(primitiveFieldSize, index + count);
        }

        /** @since 0.17 or earlier */
        @Override
        public final BaseAllocator copy() {
            return clone();
        }

        /** @since 0.17 or earlier */
        @Override
        protected final BaseAllocator clone() {
            try {
                return (BaseAllocator) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        /** @since 0.17 or earlier */
        public Location existingLocationForValue(Object value, Location oldLocation, ShapeImpl oldShape) {
            assert oldShape.getLayout() == this.layout;
            Location newLocation;
            if (oldLocation.canSet(value)) {
                newLocation = oldLocation;
            } else {
                newLocation = oldShape.allocator().locationForValueUpcast(value, oldLocation);
            }
            return newLocation;
        }
    }

    private static final DebugCounter shapeCount = DebugCounter.create("Shapes allocated total");
    private static final DebugCounter shapeCloneCount = DebugCounter.create("Shapes allocated cloned");
    private static final DebugCounter shapeCacheHitCount = DebugCounter.create("Shape cache hits");
    private static final DebugCounter shapeCacheMissCount = DebugCounter.create("Shape cache misses");

    /** @since 0.17 or earlier */
    public ForeignAccess getForeignAccessFactory(DynamicObject object) {
        return getObjectType().getForeignAccessFactory(object);
    }
}
