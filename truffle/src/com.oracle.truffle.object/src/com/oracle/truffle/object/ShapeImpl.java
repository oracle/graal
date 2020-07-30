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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.utilities.NeverValidAssumption;
import com.oracle.truffle.object.LocationImpl.LocationVisitor;
import com.oracle.truffle.object.Transition.AddPropertyTransition;
import com.oracle.truffle.object.Transition.ObjectFlagsTransition;
import com.oracle.truffle.object.Transition.ObjectTypeTransition;
import com.oracle.truffle.object.Transition.PropertyTransition;
import com.oracle.truffle.object.Transition.RemovePropertyTransition;
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
 * @since 0.17 or earlier
 */
@SuppressWarnings("deprecation")
public abstract class ShapeImpl extends Shape {
    /** Shape and object flags. */
    protected final int flags;

    /** @since 0.17 or earlier */
    protected final LayoutImpl layout;
    /** @since 0.17 or earlier */
    protected final com.oracle.truffle.api.object.ObjectType objectType;
    /** @since 0.17 or earlier */
    protected final ShapeImpl parent;
    /** @since 0.17 or earlier */
    protected final PropertyMap propertyMap;

    protected final Object sharedData;
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
     * @see #queryTransition(Transition)
     * @see #addTransitionInternal(Transition, ShapeImpl)
     */
    private volatile Object transitionMap;

    private final Transition transitionFromParent;

    private volatile PropertyAssumptions sharedPropertyAssumptions;

    private static final AtomicReferenceFieldUpdater<ShapeImpl, Object> TRANSITION_MAP_UPDATER = AtomicReferenceFieldUpdater.newUpdater(ShapeImpl.class, Object.class, "transitionMap");
    private static final AtomicReferenceFieldUpdater<ShapeImpl, Assumption> LEAF_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(ShapeImpl.class, Assumption.class, "leafAssumption");
    private static final AtomicReferenceFieldUpdater<ShapeImpl, PropertyAssumptions> PROPERTY_ASSUMPTIONS_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(ShapeImpl.class, PropertyAssumptions.class, "sharedPropertyAssumptions");

    /**
     * Private constructor.
     *
     * @param parent predecessor shape
     * @param transitionFromParent direct transition from parent shape
     *
     * @see #ShapeImpl(Layout, ShapeImpl, Object, Object, PropertyMap, Transition, BaseAllocator,
     *      int)
     */
    private ShapeImpl(Layout layout, ShapeImpl parent, Object objectType, Object sharedData, PropertyMap propertyMap, Transition transitionFromParent, int objectArraySize, int objectFieldSize,
                    int primitiveFieldSize, int primitiveArraySize, boolean hasPrimitiveArray, int flags, Assumption singleContextAssumption) {
        this.layout = (LayoutImpl) layout;
        this.objectType = (com.oracle.truffle.api.object.ObjectType) Objects.requireNonNull(objectType);
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

        if (parent != null) {
            this.propertyCount = makePropertyCount(parent, propertyMap, transitionFromParent);
            this.depth = parent.depth + 1;
        } else {
            this.propertyCount = 0;
            this.depth = 0;
        }

        this.validAssumption = createValidAssumption();

        this.flags = flags;
        this.transitionFromParent = transitionFromParent;
        this.sharedData = sharedData;
        assert parent == null || this.sharedData == parent.sharedData;

        this.sharedPropertyAssumptions = parent == null && (flags & FLAG_ALLOW_PROPERTY_ASSUMPTIONS) != 0 && singleContextAssumption != null
                        ? new PropertyAssumptions(singleContextAssumption)
                        : null;

        shapeCount.inc();
        if (ObjectStorageOptions.DumpShapes) {
            Debug.trackShape(this);
        }
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl(Layout layout, ShapeImpl parent, Object objectType, Object sharedData, PropertyMap propertyMap, Transition transition, Allocator allocator, int flags) {
        this(layout, parent, objectType, sharedData, propertyMap, transition, ((BaseAllocator) allocator).objectArraySize, ((BaseAllocator) allocator).objectFieldSize,
                        ((BaseAllocator) allocator).primitiveFieldSize, ((BaseAllocator) allocator).primitiveArraySize, ((BaseAllocator) allocator).hasPrimitiveArray, flags, null);
    }

    /** @since 0.17 or earlier */
    @SuppressWarnings("hiding")
    protected abstract ShapeImpl createShape(Layout layout, Object sharedData, ShapeImpl parent, Object objectType, PropertyMap propertyMap, Transition transition, Allocator allocator, int id);

    /** @since 0.17 or earlier */
    protected ShapeImpl(Layout layout, Object dynamicType, Object sharedData, int flags, Assumption constantObjectAssumption) {
        this(layout, null, dynamicType, sharedData, PropertyMap.empty(), null, 0, 0, 0, 0, true, flags, constantObjectAssumption);
    }

    private static int makePropertyCount(ShapeImpl parent, PropertyMap propertyMap, Transition transitionFromParent) {
        int thisSize = propertyMap.size();
        int parentSize = parent.propertyMap.size();
        if (thisSize > parentSize) {
            Property lastProperty = propertyMap.getLastProperty();
            if (!lastProperty.isHidden()) {
                return parent.propertyCount + 1;
            }
        } else if (thisSize < parentSize && transitionFromParent instanceof RemovePropertyTransition) {
            if (!((RemovePropertyTransition) transitionFromParent).getProperty().isHidden()) {
                return parent.propertyCount - 1;
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
        return getObjectFlags(flags);
    }

    @Override
    public final int getFlags() {
        return getObjectFlags(flags);
    }

    public final int getFlagsInternal() {
        return flags;
    }

    /**
     * Calculate array size for the given number of elements.
     */
    private static int capacityFromSize(int size) {
        if (size == 0) {
            return 0;
        } else if (size <= 4) {
            return 4;
        } else if (size <= 8) {
            return 8;
        } else {
            // round up to (3/2) * highestOneBit or the next power of 2, alternately;
            // i.e., the next in the sequence: 8, 12, 16, 24, 32, 48, 64, 96, 128, ...
            int hi = Integer.highestOneBit(size);
            int cap = hi;
            if (cap < size) {
                cap = hi + (hi >>> 1);
                if (cap < size) {
                    cap = hi << 1;
                    if (cap < size) {
                        // handle potential overflow
                        cap = size;
                    }
                }
            }
            return cap;
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
     * @return true if this shape has instance properties.
     */
    @Override
    protected boolean hasInstanceProperties() {
        return objectFieldSize != 0 || objectArraySize != 0 || primitiveFieldSize != 0 || primitiveArraySize != 0;
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

    private void addTransitionInternal(Transition transition, ShapeImpl successor) {
        CompilerAsserts.neverPartOfCompilation();
        Object prev;
        Object next;
        do {
            prev = TRANSITION_MAP_UPDATER.get(this);
            if (prev == null) {
                invalidateLeafAssumption();
                next = newSingleEntry(transition, successor);
            } else if (isSingleEntry(prev)) {
                StrongKeyWeakValueEntry<Transition, ShapeImpl> entry = asSingleEntry(prev);
                Transition exTra = entry.getKey();
                ShapeImpl exSucc = entry.getValue();
                if (exSucc != null) {
                    next = newTransitionMap(exTra, exSucc, transition, successor);
                } else {
                    next = newSingleEntry(transition, successor);
                }
            } else {
                next = addToTransitionMap(transition, successor, prev);
            }
            if (prev == next) {
                break;
            }
        } while (!TRANSITION_MAP_UPDATER.compareAndSet(this, prev, next));
    }

    private static Object newTransitionMap(Transition firstTransition, ShapeImpl firstShape, Transition secondTransition, ShapeImpl secondShape) {
        Map<Transition, ShapeImpl> map = newTransitionMap();
        map.put(firstTransition, firstShape);
        map.put(secondTransition, secondShape);
        return map;
    }

    private static Object addToTransitionMap(Transition transition, ShapeImpl successor, Object prevMap) {
        assert isTransitionMap(prevMap);
        Map<Transition, ShapeImpl> map = asTransitionMap(prevMap);
        map.put(transition, successor);
        return map;
    }

    private static Map<Transition, ShapeImpl> newTransitionMap() {
        return new TransitionMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<Transition, ShapeImpl> asTransitionMap(Object map) {
        return (Map<Transition, ShapeImpl>) map;
    }

    private static boolean isTransitionMap(Object trans) {
        return trans instanceof Map<?, ?>;
    }

    private static Object newSingleEntry(Transition transition, ShapeImpl successor) {
        return new StrongKeyWeakValueEntry<>(transition, successor);
    }

    private static boolean isSingleEntry(Object trans) {
        return trans instanceof StrongKeyWeakValueEntry;
    }

    @SuppressWarnings("unchecked")
    private static StrongKeyWeakValueEntry<Transition, ShapeImpl> asSingleEntry(Object trans) {
        return (StrongKeyWeakValueEntry<Transition, ShapeImpl>) trans;
    }

    /**
     * @since 0.17 or earlier
     * @deprecated use {@link #forEachTransition(BiConsumer)} instead.
     */
    @Deprecated
    public final Map<Transition, ShapeImpl> getTransitionMapForRead() {
        Map<Transition, ShapeImpl> snapshot = new HashMap<>();
        forEachTransition(new BiConsumer<Transition, ShapeImpl>() {
            @Override
            public void accept(Transition t, ShapeImpl s) {
                snapshot.put(t, s);
            }
        });
        return snapshot;
    }

    public final void forEachTransition(BiConsumer<Transition, ShapeImpl> consumer) {
        Object trans = transitionMap;
        if (trans == null) {
            return;
        } else if (isSingleEntry(trans)) {
            StrongKeyWeakValueEntry<Transition, ShapeImpl> entry = asSingleEntry(trans);
            ShapeImpl shape = entry.getValue();
            if (shape != null) {
                Transition key = entry.getKey();
                consumer.accept(key, shape);
            }
        } else {
            assert isTransitionMap(trans);
            Map<Transition, ShapeImpl> map = asTransitionMap(trans);
            map.forEach(consumer);
        }
    }

    private ShapeImpl queryTransitionImpl(Transition transition) {
        Object trans = transitionMap;
        if (trans == null) {
            return null;
        } else if (isSingleEntry(trans)) {
            StrongKeyWeakValueEntry<Transition, ShapeImpl> entry = asSingleEntry(trans);
            Transition key = entry.getKey();
            if (key.equals(transition)) {
                return entry.getValue();
            } else {
                return null;
            }
        } else {
            assert isTransitionMap(trans);
            Map<Transition, ShapeImpl> map = asTransitionMap(trans);
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
    @TruffleBoundary
    protected void onPropertyTransition(Property property) {
        if (allowPropertyAssumptions()) {
            PropertyAssumptions propertyAssumptions = getPropertyAssumptions();
            if (propertyAssumptions != null) {
                propertyAssumptions.invalidatePropertyAssumption(property.getKey());
            }
        }
        if (sharedData instanceof com.oracle.truffle.api.object.ShapeListener) {
            ((com.oracle.truffle.api.object.ShapeListener) sharedData).onPropertyTransition(property.getKey());
        }
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public ShapeImpl defineProperty(Object key, Object value, int propertyFlags) {
        return defineProperty(key, value, propertyFlags, layout.getStrategy().getDefaultLocationFactory());
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public ShapeImpl defineProperty(Object key, Object value, int propertyFlags, LocationFactory locationFactory) {
        return layout.getStrategy().defineProperty(this, key, value, propertyFlags, locationFactory);
    }

    /** @since 0.17 or earlier */
    protected ShapeImpl cloneRoot(ShapeImpl from, Object newSharedData) {
        return createShape(from.layout, newSharedData, null, from.objectType, from.propertyMap, null, from.allocator(), from.flags);
    }

    /**
     * Create a separate clone of a shape.
     *
     * @param newParent the cloned parent shape
     * @since 0.17 or earlier
     */
    protected final ShapeImpl cloneOnto(ShapeImpl newParent) {
        ShapeImpl from = this;
        ShapeImpl newShape = createShape(newParent.layout, newParent.sharedData, newParent, from.objectType, from.propertyMap, from.transitionFromParent, from.allocator(), newParent.flags);

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

        ShapeImpl newShape = parent.createShape(parent.layout, parent.sharedData, parent, parent.objectType, newPropertyMap, addTransition, allocator, parent.flags);
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
        ShapeImpl newShape = parent.createShape(parent.layout, parent.sharedData, parent, parent.objectType, parent.propertyMap, transition, allocator, parent.flags);
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
        return Arrays.asList(getPropertyArray());
    }

    @TruffleBoundary
    public final Property[] getPropertyArray() {
        Property[] props = new Property[getPropertyCount()];
        int i = props.length;
        for (Iterator<Property> it = this.propertyMap.reverseOrderedValueIterator(); it.hasNext();) {
            Property currentProperty = it.next();
            if (!currentProperty.isHidden()) {
                props[--i] = currentProperty;
            }
        }
        return props;
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
        return Arrays.asList(getKeyArray());
    }

    @TruffleBoundary
    public final Object[] getKeyArray() {
        Object[] props = new Object[getPropertyCount()];
        int i = props.length;
        for (Iterator<Property> it = this.propertyMap.reverseOrderedValueIterator(); it.hasNext();) {
            Property currentProperty = it.next();
            if (!currentProperty.isHidden()) {
                props[--i] = currentProperty.getKey();
            }
        }
        return props;
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
    @TruffleBoundary
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

    @TruffleBoundary
    protected void invalidateLeafAssumption() {
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
    @TruffleBoundary
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
    public com.oracle.truffle.api.object.ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public Object getDynamicType() {
        return getObjectType();
    }

    @TruffleBoundary
    @Override
    protected Shape setDynamicType(Object newObjectType) {
        Objects.requireNonNull(newObjectType, "dynamicType");
        if (!(newObjectType instanceof ObjectType)) {
            throw new IllegalArgumentException("dynamicType must be an instance of ObjectType");
        }
        if (getDynamicType() == newObjectType) {
            return this;
        }
        return changeType((ObjectType) newObjectType);
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

    final Object getSharedDataInternal() {
        return sharedData;
    }

    final boolean allowPropertyAssumptions() {
        return (flags & FLAG_ALLOW_PROPERTY_ASSUMPTIONS) != 0;
    }

    private PropertyAssumptions getOrCreatePropertyAssumptions() {
        CompilerAsserts.neverPartOfCompilation();
        assert allowPropertyAssumptions();
        PropertyAssumptions ass = root.sharedPropertyAssumptions;
        if (ass == null) {
            ass = new PropertyAssumptions(null);
            if (!PROPERTY_ASSUMPTIONS_UPDATER.compareAndSet(root, null, ass)) {
                ass = getPropertyAssumptions();
            }
        }
        assert ass != null;
        return ass;
    }

    private PropertyAssumptions getPropertyAssumptions() {
        CompilerAsserts.neverPartOfCompilation();
        assert allowPropertyAssumptions();
        return root.sharedPropertyAssumptions;
    }

    @TruffleBoundary
    protected void invalidateAllPropertyAssumptions() {
        assert allowPropertyAssumptions();
        PropertyAssumptions propertyAssumptions = getPropertyAssumptions();
        if (propertyAssumptions != null) {
            propertyAssumptions.invalidateAllPropertyAssumptions();
        }
    }

    protected Assumption getSingleContextAssumption() {
        PropertyAssumptions propertyAssumptions = getPropertyAssumptions();
        if (propertyAssumptions != null) {
            return propertyAssumptions.getSingleContextAssumption();
        }
        return null;
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
    public final ShapeImpl changeType(ObjectType newObjectType) {
        if (getObjectType() == newObjectType) {
            return this;
        }
        ObjectTypeTransition transition = new ObjectTypeTransition(newObjectType);
        ShapeImpl cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return layout.getStrategy().ensureValid(cachedShape);
        }

        ShapeImpl newShape = createShape(layout, sharedData, this, newObjectType, propertyMap, transition, allocator(), flags);
        addDirectTransition(transition, newShape);
        return newShape;
    }

    @TruffleBoundary
    @Override
    protected ShapeImpl setFlags(int newShapeFlags) {
        checkObjectFlags(newShapeFlags);
        if (getFlags() == newShapeFlags) {
            return this;
        }

        ObjectFlagsTransition transition = new ObjectFlagsTransition(newShapeFlags);
        ShapeImpl cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return layout.getStrategy().ensureValid(cachedShape);
        }

        int newFlags = newShapeFlags | (flags & ~OBJECT_FLAGS_MASK);
        ShapeImpl newShape = createShape(layout, sharedData, this, objectType, propertyMap, transition, allocator(), newFlags);
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
        if (!layout.isLegacyLayout()) {
            throw DefaultLayout.unsupported();
        }

        List<Property> properties = getPropertyListInternal(true);
        List<Property> filtered = null;
        for (ListIterator<Property> iterator = properties.listIterator(); iterator.hasNext();) {
            Property property = iterator.next();
            // skip non-instance fields
            assert property.getLocation() != layout.getPrimitiveArrayLocation();
            if (property.getLocation().isValue()) {
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
        return (flags & FLAG_SHARED_SHAPE) != 0;
    }

    /** @since 0.18 */
    @TruffleBoundary
    @Override
    public Shape makeSharedShape() {
        if (isShared()) {
            throw new UnsupportedOperationException("makeSharedShape() can only be called on non-shared shapes.");
        }

        Transition transition = new ShareShapeTransition();
        ShapeImpl cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return layout.getStrategy().ensureValid(cachedShape);
        }

        ShapeImpl newShape = createShape(layout, sharedData, this, objectType, propertyMap, transition, allocator(), flags | FLAG_SHARED_SHAPE);
        addDirectTransition(transition, newShape);
        return newShape;
    }

    /** Bits available to API users. */
    protected static final int OBJECT_FLAGS_MASK = 0x0000_00ff;
    protected static final int OBJECT_FLAGS_SHIFT = 0;

    /** Shared shape flag. */
    protected static final int FLAG_SHARED_SHAPE = 1 << 16;
    protected static final int FLAG_ALLOW_PROPERTY_ASSUMPTIONS = 1 << 17;

    protected static int getObjectFlags(int flags) {
        return ((flags & OBJECT_FLAGS_MASK) >>> OBJECT_FLAGS_SHIFT);
    }

    protected static int checkObjectFlags(int flags) {
        if ((flags & ~OBJECT_FLAGS_MASK) != 0) {
            throw new IllegalArgumentException("flags must be in the range [0, 255]");
        }
        return flags;
    }

    /** @since 20.2.0 */
    @TruffleBoundary
    @Override
    public Assumption getPropertyAssumption(Object key) {
        if (allowPropertyAssumptions()) {
            Assumption propertyAssumption = getOrCreatePropertyAssumptions().getPropertyAssumption(key);
            if (propertyAssumption != null && propertyAssumption.isValid()) {
                return propertyAssumption;
            }
        }
        return NeverValidAssumption.INSTANCE;
    }

    protected boolean testPropertyFlags(IntPredicate predicate) {
        for (Property p : getProperties()) {
            if (predicate.test(p.getFlags())) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    /** @since 20.2.0 */
    @TruffleBoundary
    @Override
    public boolean allPropertiesMatch(Predicate<Property> predicate) {
        for (Property p : getProperties()) {
            if (predicate.test(p)) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    public static final class DynamicObjectFactoryImpl implements DynamicObjectFactory {
        private final ShapeImpl shape;
        @CompilationFinal(dimensions = 1) private final PropertyImpl[] instanceFields;
        private static final PropertyImpl[] EMPTY = new PropertyImpl[0];

        private DynamicObjectFactoryImpl(ShapeImpl shape, List<Property> properties) {
            this.shape = shape;
            this.instanceFields = properties.toArray(EMPTY);
        }

        public DynamicObject newInstance(Object... initialValues) {
            assert initialValues.length == instanceFields.length : wrongArguments(initialValues.length);
            CompilerAsserts.partialEvaluationConstant(shape);
            DynamicObject store = shape.layout.construct(shape);
            return fillValues(store, initialValues);
        }

        @ExplodeLoop
        private DynamicObject fillValues(DynamicObject store, Object... initialValues) {
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
            this.shared = shape.isShared();
        }

        /** @since 0.17 or earlier */
        protected abstract Location moveLocation(Location oldLocation);

        /** @since 0.17 or earlier */
        @Deprecated
        protected abstract Location newObjectLocation(boolean useFinal, boolean nonNull);

        /** @since 0.17 or earlier */
        @Deprecated
        protected abstract Location newTypedObjectLocation(boolean useFinal, Class<?> type, boolean nonNull);

        /** @since 0.17 or earlier */
        @Deprecated
        protected abstract Location newIntLocation(boolean useFinal);

        /** @since 0.17 or earlier */
        @Deprecated
        protected abstract Location newDoubleLocation(boolean useFinal);

        /** @since 0.17 or earlier */
        @Deprecated
        protected abstract Location newLongLocation(boolean useFinal);

        /** @since 0.17 or earlier */
        @Deprecated
        protected abstract Location newBooleanLocation(boolean useFinal);

        /** @since 0.17 or earlier */
        @Deprecated
        @Override
        public Location constantLocation(Object value) {
            throw new UnsupportedOperationException();
        }

        /** @since 0.17 or earlier */
        @Deprecated
        @Override
        public Location declaredLocation(Object value) {
            throw new UnsupportedOperationException();
        }

        /** @since 0.17 or earlier */
        @Deprecated
        @Override
        protected Location locationForValue(Object value, boolean useFinal, boolean nonNull) {
            throw new UnsupportedOperationException();
        }

        /** @since 0.17 or earlier */
        @Deprecated
        protected Location locationForValueUpcast(Object value, Location oldLocation) {
            return locationForValueUpcast(value, oldLocation, 0);
        }

        @SuppressWarnings("unused")
        protected Location locationForValueUpcast(Object value, Location oldLocation, long putFlags) {
            throw new UnsupportedOperationException();
        }

        /** @since 0.17 or earlier */
        @Override
        protected Location locationForType(Class<?> type, boolean useFinal, boolean nonNull) {
            throw new UnsupportedOperationException();
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
                throw shouldNotReachHere(e);
            }
        }

        /** @since 0.17 or earlier */
        @Deprecated
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

    static final class PropertyAssumptions {
        private final EconomicMap<Object, Assumption> stablePropertyAssumptions;
        private final Assumption singleContextAssumption;

        PropertyAssumptions(Assumption singleContextAssumption) {
            this.singleContextAssumption = singleContextAssumption;
            this.stablePropertyAssumptions = EconomicMap.create();
        }

        synchronized Assumption getPropertyAssumption(Object propertyName) {
            CompilerAsserts.neverPartOfCompilation();
            EconomicMap<Object, Assumption> map = stablePropertyAssumptions;
            Assumption assumption = map.get(propertyName);
            if (assumption != null) {
                return assumption;
            }
            assumption = Truffle.getRuntime().createAssumption(propertyName.toString());
            map.put(propertyName, assumption);
            propertyAssumptionsCreated.inc();
            return assumption;
        }

        synchronized void invalidatePropertyAssumption(Object propertyName) {
            CompilerAsserts.neverPartOfCompilation();
            EconomicMap<Object, Assumption> map = stablePropertyAssumptions;
            Assumption assumption = map.get(propertyName);
            if (assumption != null && assumption != NeverValidAssumption.INSTANCE) {
                assumption.invalidate("invalidatePropertyAssumption");
                map.put(propertyName, NeverValidAssumption.INSTANCE);
                propertyAssumptionsRemoved.inc();
            }
        }

        synchronized void invalidateAllPropertyAssumptions() {
            CompilerAsserts.neverPartOfCompilation();
            for (Assumption assumption : stablePropertyAssumptions.getValues()) {
                assumption.invalidate("invalidateAllPropertyAssumptions");
            }
            stablePropertyAssumptions.clear();
        }

        Assumption getSingleContextAssumption() {
            return singleContextAssumption;
        }
    }

    private static final DebugCounter shapeCount = DebugCounter.create("Shapes allocated total");
    private static final DebugCounter shapeCloneCount = DebugCounter.create("Shapes allocated cloned");
    private static final DebugCounter shapeCacheHitCount = DebugCounter.create("Shape cache hits");
    private static final DebugCounter shapeCacheMissCount = DebugCounter.create("Shape cache misses");
    static final DebugCounter shapeCacheExpunged = DebugCounter.create("Shape cache expunged");
    static final DebugCounter propertyAssumptionsCreated = DebugCounter.create("Property assumptions created");
    static final DebugCounter propertyAssumptionsRemoved = DebugCounter.create("Property assumptions removed");

}
