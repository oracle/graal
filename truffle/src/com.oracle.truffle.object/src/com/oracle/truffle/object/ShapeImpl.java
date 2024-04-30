/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.LocationImpl.LocationVisitor;
import com.oracle.truffle.object.Transition.AddPropertyTransition;
import com.oracle.truffle.object.Transition.ObjectFlagsTransition;
import com.oracle.truffle.object.Transition.ObjectTypeTransition;
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
    protected final Object objectType;
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
     * <li>{@link StrongKeyWeakValueEntry}: immutable single entry map
     * <li>{@link TransitionMap}: mutable multiple entry map
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

    /** Shared shape flag. */
    protected static final int FLAG_SHARED_SHAPE = 1 << 16;
    /** Flag that is set if {@link Shape.Builder#propertyAssumptions(boolean)} is true. */
    protected static final int FLAG_ALLOW_PROPERTY_ASSUMPTIONS = 1 << 17;
    /** Automatic flag that is set if the shape has instance properties. */
    protected static final int FLAG_HAS_INSTANCE_PROPERTIES = 1 << 18;

    /**
     * Private constructor.
     *
     * @param parent predecessor shape
     * @param transitionFromParent direct transition from parent shape
     */
    private ShapeImpl(com.oracle.truffle.api.object.Layout layout, ShapeImpl parent, Object objectType, Object sharedData, PropertyMap propertyMap, Transition transitionFromParent,
                    int objectArraySize, int objectFieldSize, int primitiveFieldSize, int primitiveArraySize, int flags, Assumption singleContextAssumption) {
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

        if (parent != null) {
            this.propertyCount = makePropertyCount(parent, propertyMap, transitionFromParent);
            this.depth = parent.depth + 1;
        } else {
            this.propertyCount = 0;
            this.depth = 0;
        }

        this.validAssumption = createValidAssumption();

        int allFlags = flags;
        if ((allFlags & FLAG_HAS_INSTANCE_PROPERTIES) == 0) {
            if (objectFieldSize != 0 || objectArraySize != 0 || primitiveFieldSize != 0 || primitiveArraySize != 0) {
                allFlags |= FLAG_HAS_INSTANCE_PROPERTIES;
            }
        }

        this.flags = allFlags;
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
    @SuppressWarnings("this-escape")
    protected ShapeImpl(com.oracle.truffle.api.object.Layout layout, ShapeImpl parent, Object objectType, Object sharedData, PropertyMap propertyMap,
                    Transition transition, BaseAllocator allocator, int flags) {
        this(layout, parent, objectType, sharedData, propertyMap, transition, allocator.objectArraySize, allocator.objectFieldSize,
                        allocator.primitiveFieldSize, allocator.primitiveArraySize, flags, null);
    }

    /** @since 0.17 or earlier */
    @SuppressWarnings("hiding")
    protected abstract ShapeImpl createShape(com.oracle.truffle.api.object.Layout layout, Object sharedData, ShapeImpl parent, Object objectType, PropertyMap propertyMap,
                    Transition transition, BaseAllocator allocator, int id);

    /** @since 0.17 or earlier */
    @SuppressWarnings("this-escape")
    protected ShapeImpl(com.oracle.truffle.api.object.Layout layout, Object dynamicType, Object sharedData, int flags, Assumption constantObjectAssumption) {
        this(layout, null, dynamicType, sharedData, PropertyMap.empty(), null, 0, 0, 0, 0, flags, constantObjectAssumption);
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
            if (!(((RemovePropertyTransition) transitionFromParent).getPropertyKey() instanceof HiddenKey)) {
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
        return getLayout().hasPrimitiveExtensionArray();
    }

    /**
     * @return true if this shape has instance properties.
     */
    @Override
    protected boolean hasInstanceProperties() {
        return (flags & FLAG_HAS_INSTANCE_PROPERTIES) != 0;
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

    public final ShapeImpl addDirectTransition(Transition transition, ShapeImpl next) {
        return addTransitionIfAbsentOrGet(transition, next);
    }

    public final ShapeImpl addIndirectTransition(Transition transition, ShapeImpl next) {
        return addTransitionIfAbsentOrGet(transition, next);
    }

    public final ShapeImpl addTransitionIfAbsentOrGet(Transition transition, ShapeImpl successor) {
        ShapeImpl existing = addTransitionIfAbsentOrNull(transition, successor);
        if (existing != null) {
            return existing;
        } else {
            return successor;
        }
    }

    /**
     * Adds a new shape transition if not the transition is not already in the cache.
     *
     * @return {@code null} or an existing cached shape for this transition.
     */
    public final ShapeImpl addTransitionIfAbsentOrNull(Transition transition, ShapeImpl successor) {
        CompilerAsserts.neverPartOfCompilation();
        assert transition.isDirect() == (successor.getParent() == this);
        assert !isShared() || transition.isDirect();

        // Type is either single entry or transition map.
        Object prev;
        Object next;
        do {
            prev = TRANSITION_MAP_UPDATER.get(this);
            if (prev == null) {
                invalidateLeafAssumption();
                next = newSingleEntry(transition, successor);
            } else if (isSingleEntry(prev)) {
                StrongKeyWeakValueEntry<Object, ShapeImpl> entry = asSingleEntry(prev);
                Transition existingTransition;
                ShapeImpl existingSuccessor = entry.getValue();
                if (existingSuccessor != null && (existingTransition = unwrapKey(entry.getKey())) != null) {
                    if (existingTransition.equals(transition)) {
                        return existingSuccessor;
                    } else {
                        next = newTransitionMap(existingTransition, existingSuccessor, transition, successor);
                    }
                } else {
                    next = newSingleEntry(transition, successor);
                }
            } else {
                ShapeImpl existingSuccessor = addToTransitionMap(transition, successor, asTransitionMap(prev));
                if (existingSuccessor != null) {
                    return existingSuccessor;
                } else {
                    next = prev;
                }
            }
            if (prev == next) {
                return null;
            }
        } while (!TRANSITION_MAP_UPDATER.compareAndSet(this, prev, next));

        return null;
    }

    private static Object newTransitionMap(Transition firstTransition, ShapeImpl firstShape, Transition secondTransition, ShapeImpl secondShape) {
        TransitionMap<Transition, ShapeImpl> map = newTransitionMap();
        addToTransitionMap(firstTransition, firstShape, map);
        addToTransitionMap(secondTransition, secondShape, map);
        return map;
    }

    private static ShapeImpl addToTransitionMap(Transition transition, ShapeImpl successor, TransitionMap<Transition, ShapeImpl> map) {
        if (transition.hasConstantLocation()) {
            return map.putWeakKeyIfAbsent(transition, successor);
        } else {
            return map.putIfAbsent(transition, successor);
        }
    }

    private static TransitionMap<Transition, ShapeImpl> newTransitionMap() {
        transitionMapsCreated.inc();
        return TransitionMap.create();
    }

    @SuppressWarnings("unchecked")
    private static Transition unwrapKey(Object key) {
        if (key instanceof WeakKey<?>) {
            return ((WeakKey<Transition>) key).get();
        }
        return (Transition) key;
    }

    @SuppressWarnings("unchecked")
    private static TransitionMap<Transition, ShapeImpl> asTransitionMap(Object map) {
        return (TransitionMap<Transition, ShapeImpl>) map;
    }

    private static boolean isTransitionMap(Object trans) {
        return trans instanceof TransitionMap<?, ?>;
    }

    private static Object newSingleEntry(Transition transition, ShapeImpl successor) {
        transitionSingleEntriesCreated.inc();
        Object key = transition;
        if (transition.hasConstantLocation()) {
            key = new WeakKey<>(transition);
        }
        return new StrongKeyWeakValueEntry<>(key, successor);
    }

    private static boolean isSingleEntry(Object trans) {
        return trans instanceof StrongKeyWeakValueEntry;
    }

    @SuppressWarnings("unchecked")
    private static StrongKeyWeakValueEntry<Object, ShapeImpl> asSingleEntry(Object trans) {
        return (StrongKeyWeakValueEntry<Object, ShapeImpl>) trans;
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
            StrongKeyWeakValueEntry<Object, ShapeImpl> entry = asSingleEntry(trans);
            ShapeImpl shape = entry.getValue();
            if (shape != null) {
                Transition key = unwrapKey(entry.getKey());
                if (key != null) {
                    consumer.accept(key, shape);
                }
            }
        } else {
            assert isTransitionMap(trans);
            TransitionMap<Transition, ShapeImpl> map = asTransitionMap(trans);
            map.forEach(consumer);
        }
    }

    private ShapeImpl queryTransitionImpl(Transition transition) {
        Object trans = transitionMap;
        if (trans == null) {
            return null;
        } else if (isSingleEntry(trans)) {
            StrongKeyWeakValueEntry<Object, ShapeImpl> entry = asSingleEntry(trans);
            ShapeImpl shape = entry.getValue();
            if (shape != null) {
                Transition key = unwrapKey(entry.getKey());
                if (key != null && transition.equals(key)) {
                    return shape;
                }
            }
            return null;
        } else {
            assert isTransitionMap(trans);
            TransitionMap<Transition, ShapeImpl> map = asTransitionMap(trans);
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
        return getLayoutStrategy().addProperty(this, property);
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    protected void onPropertyTransition(Property property) {
        onPropertyTransitionWithKey(property.getKey());
    }

    final void onPropertyTransitionWithKey(Object propertyKey) {
        if (allowPropertyAssumptions()) {
            PropertyAssumptions propertyAssumptions = getPropertyAssumptions();
            if (propertyAssumptions != null) {
                propertyAssumptions.invalidatePropertyAssumption(propertyKey);
            }
        }
    }

    /** @since 0.17 or earlier */
    @TruffleBoundary
    @Override
    public ShapeImpl defineProperty(Object key, Object value, int propertyFlags) {
        return getLayoutStrategy().defineProperty(this, key, value, propertyFlags);
    }

    @TruffleBoundary
    @Override
    protected ShapeImpl defineProperty(Object key, Object value, int propertyFlags, int putFlags) {
        return getLayoutStrategy().defineProperty(this, key, value, propertyFlags, putFlags);
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

        return newParent.addDirectTransition(from.transitionFromParent, newShape);
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
        assert newShape.hasPrimitiveArray() || ((LocationImpl) addend.getLocation()).primitiveArrayCount() == 0;
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
    public boolean isRelated(Shape other) {
        if (this == other) {
            return true;
        }
        if (this.getRoot() == getRoot()) {
            return true;
        }
        return false;
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
                    next = isLeafShape ? createLeafAssumption() : Assumption.NEVER_VALID;
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
            if (prev == Assumption.NEVER_VALID) {
                break;
            }
            if (prev != null) {
                prev.invalidate();
            }
        } while (!LEAF_ASSUMPTION_UPDATER.compareAndSet(this, prev, Assumption.NEVER_VALID));
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
        boolean first = true;
        for (Iterator<Property> iterator = propertyMap.reverseOrderedValueIterator(); iterator.hasNext();) {
            Property p = iterator.next();
            if (first) {
                first = false;
            } else {
                sb.append("\n");
            }
            sb.append(p);
            if (iterator.hasNext()) {
                sb.append(",");
            }
            if (sb.length() >= limit) {
                sb.append("...");
                break;
            }
        }
        sb.append("}");

        return sb.toString();
    }

    /** @since 0.17 or earlier */
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
        return getLayoutStrategy().removeProperty(this, prop);
    }

    /** @since 0.17 or earlier */
    public final BaseAllocator allocator() {
        return getLayoutStrategy().createAllocator(this);
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
        return getLayoutStrategy().replaceProperty(this, oldProperty, newProperty);
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

    @Override
    public Object getDynamicType() {
        return objectType;
    }

    @TruffleBoundary
    @Override
    protected ShapeImpl setDynamicType(Object newObjectType) {
        Objects.requireNonNull(newObjectType, "dynamicType");
        if (getDynamicType() == newObjectType) {
            return this;
        }
        ObjectTypeTransition transition = new ObjectTypeTransition(newObjectType);
        ShapeImpl cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return cachedShape;
        }

        ShapeImpl newShape = createShape(layout, sharedData, this, newObjectType, propertyMap, transition, allocator(), flags);
        return addDirectTransition(transition, newShape);
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

    public final LayoutStrategy getLayoutStrategy() {
        return getLayout().getStrategy();
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

    /**
     * Clone off a separate shape with new shared data.
     *
     * @since 0.17 or earlier
     */
    @TruffleBoundary
    public final ShapeImpl createSeparateShape(Object newSharedData) {
        if (parent == null) {
            return cloneRoot(this, newSharedData);
        } else {
            return this.cloneOnto(parent.createSeparateShape(newSharedData));
        }
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
            return cachedShape;
        }

        int newFlags = newShapeFlags | (flags & ~OBJECT_FLAGS_MASK);
        ShapeImpl newShape = createShape(layout, sharedData, this, objectType, propertyMap, transition, allocator(), newFlags);
        return addDirectTransition(transition, newShape);
    }

    /** @since 0.17 or earlier */
    @Override
    public final Iterable<Property> getProperties() {
        return getPropertyList();
    }

    /** @since 0.17 or earlier */
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
            return cachedShape;
        }

        ShapeImpl newShape = createShape(layout, sharedData, this, objectType, propertyMap, transition, allocator(), flags | FLAG_SHARED_SHAPE);
        return addDirectTransition(transition, newShape);
    }

    /** Bits available to API users. */
    protected static final int OBJECT_FLAGS_MASK = 0x0000_ffff;
    protected static final int OBJECT_FLAGS_SHIFT = 0;

    protected static int getObjectFlags(int flags) {
        return ((flags & OBJECT_FLAGS_MASK) >>> OBJECT_FLAGS_SHIFT);
    }

    protected static int checkObjectFlags(int flags) {
        if ((flags & ~OBJECT_FLAGS_MASK) != 0) {
            throw new IllegalArgumentException("flags must be in the range [0, 0xffff]");
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
        return Assumption.NEVER_VALID;
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

    /** @since 0.17 or earlier */
    public abstract static class BaseAllocator implements LocationVisitor {
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

        /**
         * Creates a new location from a constant value. The value is stored in the shape rather
         * than in the object.
         *
         * @param value the constant value
         * @since 0.17 or earlier
         */
        @Deprecated
        public Location constantLocation(Object value) {
            throw new UnsupportedOperationException();
        }

        /**
         * Creates a new declared location with a default value. A declared location only assumes a
         * type after the first set (initialization).
         *
         * @param value the default value
         * @since 0.17 or earlier
         */
        @Deprecated
        public Location declaredLocation(Object value) {
            throw new UnsupportedOperationException();
        }

        /**
         * Create a new location compatible with the given initial value.
         *
         * @param value the initial value this location is going to be assigned
         * @param useFinal final location
         * @param nonNull non-null location
         * @since 0.17 or earlier
         */
        @Deprecated
        protected Location locationForValue(Object value, boolean useFinal, boolean nonNull) {
            throw new UnsupportedOperationException();
        }

        /**
         * Used by tests.
         */
        public Location locationForValue(Object value) {
            return locationForValue(value, false, value != null);
        }

        /** @since 0.17 or earlier */
        @Deprecated
        protected Location locationForValueUpcast(Object value, Location oldLocation) {
            return locationForValueUpcast(value, oldLocation, 0);
        }

        @SuppressWarnings("unused")
        protected Location locationForValueUpcast(Object value, Location oldLocation, int putFlags) {
            throw new UnsupportedOperationException();
        }

        /**
         * Create a new location for a fixed type. It can only be assigned to values of this type.
         *
         * @param type the Java type this location must be compatible with (may be primitive)
         * @param useFinal final location
         * @param nonNull non-null location
         * @since 0.17 or earlier
         */
        protected Location locationForType(Class<?> type, boolean useFinal, boolean nonNull) {
            throw new UnsupportedOperationException();
        }

        /**
         * Used by tests.
         */
        public final Location locationForType(Class<?> type) {
            return locationForType(type, false, false);
        }

        /** @since 0.17 or earlier */
        protected <T extends Location> T advance(T location0) {
            if (location0 instanceof LocationImpl) {
                LocationImpl location = (LocationImpl) location0;
                location.accept(this);
                assert layout.hasPrimitiveExtensionArray() || primitiveArraySize == 0;
            }
            depth++;
            return location0;
        }

        /**
         * Reserves space for the given location, so that it will not be available to subsequently
         * allocated locations.
         *
         * @since 0.17 or earlier
         */
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
        @Deprecated
        public Location existingLocationForValue(Object value, Location oldLocation, ShapeImpl oldShape) {
            assert oldShape.getLayout() == this.layout;
            Location newLocation;
            if (oldLocation.canStore(value)) {
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
            if (assumption != null && assumption != Assumption.NEVER_VALID) {
                assumption.invalidate("invalidatePropertyAssumption");
                map.put(propertyName, Assumption.NEVER_VALID);
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
    static final DebugCounter shapeCacheWeakKeys = DebugCounter.create("Shape cache weak keys");
    static final DebugCounter propertyAssumptionsCreated = DebugCounter.create("Property assumptions created");
    static final DebugCounter propertyAssumptionsRemoved = DebugCounter.create("Property assumptions removed");
    static final DebugCounter transitionSingleEntriesCreated = DebugCounter.create("Transition single-entry maps created");
    static final DebugCounter transitionMapsCreated = DebugCounter.create("Transition multi-entry maps created");

}
