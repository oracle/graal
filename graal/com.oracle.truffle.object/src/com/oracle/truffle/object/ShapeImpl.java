/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.object.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.object.LocationImpl.*;
import com.oracle.truffle.object.Locations.ConstantLocation;
import com.oracle.truffle.object.Locations.DeclaredDualLocation;
import com.oracle.truffle.object.Locations.DeclaredLocation;
import com.oracle.truffle.object.Locations.DualLocation;
import com.oracle.truffle.object.Locations.ValueLocation;
import com.oracle.truffle.object.Transition.AddPropertyTransition;
import com.oracle.truffle.object.Transition.ObjectTypeTransition;
import com.oracle.truffle.object.Transition.PropertyTransition;
import com.oracle.truffle.object.Transition.RemovePropertyTransition;
import com.oracle.truffle.object.Transition.ReservePrimitiveArrayTransition;

/**
 * Shape objects create a mapping of Property objects to indexes. The mapping of those indexes to an
 * actual store is not part of Shape's role, but JSObject's. Shapes are immutable; adding or
 * deleting a property yields a new Shape which links to the old one. This allows inline caching to
 * simply check the identity of an object's Shape to determine if the cache is valid. There is one
 * exception to this immutability, the transition map, but that is used simply to assure that an
 * identical series of property additions and deletions will yield the same Shape object.
 *
 * @see DynamicObject
 * @see Property
 * @see Locations
 */
public abstract class ShapeImpl extends Shape {
    private final int id;

    protected final LayoutImpl layout;
    protected final ObjectType objectType;
    protected final ShapeImpl parent;
    protected final PropertyMap propertyMap;

    private final Object extraData;
    private final Object sharedData;
    private final ShapeImpl root;

    protected final int objectArraySize;
    protected final int objectArrayCapacity;
    protected final int objectFieldSize;
    protected final int primitiveFieldSize;
    protected final int primitiveArraySize;
    protected final int primitiveArrayCapacity;
    protected final boolean hasPrimitiveArray;

    protected final int depth;
    protected final int propertyCount;
    protected Property[] propertyArray;

    protected final Assumption validAssumption;
    @CompilationFinal protected volatile Assumption leafAssumption;

    /**
     * Shape transition map; lazily initialized.
     *
     * @see #getTransitionMapForRead()
     * @see #getTransitionMapForWrite()
     */
    private HashMap<Transition, ShapeImpl> transitionMap;

    private final Transition transitionFromParent;

    /**
     * Private constructor.
     *
     * @param parent predecessor shape
     * @param transitionFromParent direct transition from parent shape
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
        this.transitionFromParent = transitionFromParent;
        this.objectArraySize = objectArraySize;
        this.objectArrayCapacity = capacityFromSize(objectArraySize);
        this.objectFieldSize = objectFieldSize;
        this.primitiveFieldSize = primitiveFieldSize;
        this.primitiveArraySize = primitiveArraySize;
        this.primitiveArrayCapacity = capacityFromSize(primitiveArraySize);
        this.hasPrimitiveArray = hasPrimitiveArray;

        if (parent != null) {
            this.propertyCount = makePropertyCount(parent, propertyMap);
            this.propertyArray = makePropertiesList(parent, propertyMap);
            this.depth = parent.depth + 1;
        } else {
            this.propertyCount = 0;
            this.propertyArray = null;
            this.depth = 0;
        }

        this.validAssumption = createValidAssumption();

        this.id = id;
        shapeCount.inc();

        this.sharedData = sharedData;
        this.extraData = objectType.createShapeData(this);

        debugRegisterShape(this);
    }

    protected ShapeImpl(Layout layout, ShapeImpl parent, ObjectType operations, Object sharedData, PropertyMap propertyMap, Transition transition, Allocator allocator, int id) {
        this(layout, parent, operations, sharedData, propertyMap, transition, ((BaseAllocator) allocator).objectArraySize, ((BaseAllocator) allocator).objectFieldSize,
                        ((BaseAllocator) allocator).primitiveFieldSize, ((BaseAllocator) allocator).primitiveArraySize, ((BaseAllocator) allocator).hasPrimitiveArray, id);
    }

    @SuppressWarnings("hiding")
    protected abstract ShapeImpl createShape(Layout layout, Object sharedData, ShapeImpl parent, ObjectType operations, PropertyMap propertyMap, Transition transition, Allocator allocator, int id);

    protected ShapeImpl(Layout layout, ObjectType operations, Object sharedData, int id) {
        this(layout, null, operations, sharedData, PropertyMap.empty(), null, layout.createAllocator(), id);
    }

    private static int makePropertyCount(ShapeImpl parent, PropertyMap propertyMap) {
        return parent.propertyCount + ((propertyMap.size() > parent.propertyMap.size() && !propertyMap.getLastProperty().isHidden() && !propertyMap.getLastProperty().isShadow()) ? 1 : 0);
    }

    private static Property[] makePropertiesList(ShapeImpl parent, PropertyMap propertyMap) {
        Property[] properties = parent.propertyArray;
        if (properties != null && propertyMap.size() != parent.propertyMap.size()) {
            Property lastProperty = propertyMap.getLastProperty();
            if (lastProperty != null && !lastProperty.isHidden()) {
                propertyListAllocCount.inc();
                if (!lastProperty.isShadow()) {
                    properties = Arrays.copyOf(properties, properties.length + 1);
                    properties[properties.length - 1] = lastProperty;
                } else {
                    properties = Arrays.copyOf(properties, properties.length);
                    for (int i = 0; i < properties.length; i++) {
                        if (properties[i].isSame(lastProperty)) {
                            properties[i] = lastProperty;
                        }
                    }
                }
            } else {
                propertyListShareCount.inc();
            }
        }
        return properties;
    }

    @Override
    public final Property getLastProperty() {
        return propertyMap.getLastProperty();
    }

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

    @Override
    public final int getObjectArraySize() {
        return objectArraySize;
    }

    @Override
    public final int getObjectFieldSize() {
        return objectFieldSize;
    }

    @Override
    public final int getPrimitiveFieldSize() {
        return primitiveFieldSize;
    }

    @Override
    public final int getObjectArrayCapacity() {
        return objectArrayCapacity;
    }

    @Override
    public final int getPrimitiveArrayCapacity() {
        return primitiveArrayCapacity;
    }

    @Override
    public final int getPrimitiveArraySize() {
        return primitiveArraySize;
    }

    @Override
    public final boolean hasPrimitiveArray() {
        return hasPrimitiveArray;
    }

    /**
     * Get the (parent) shape that holds the given property.
     */
    public final ShapeImpl getShapeFromProperty(Object propertyName) {
        ShapeImpl current = this;
        while (current != getRoot()) {
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
    public final ShapeImpl getShapeFromProperty(Property prop) {
        ShapeImpl current = this;
        while (current != getRoot()) {
            if (current.getTransitionFromParent() instanceof AddPropertyTransition && ((AddPropertyTransition) current.getTransitionFromParent()).getProperty().equals(prop)) {
                return current;
            }
            current = current.parent;
        }

        return null;
    }

    /**
     * Get a property entry by string name.
     *
     * @param key the name to look up
     * @return a Property object, or null if not found
     */
    @Override
    @TruffleBoundary
    public Property getProperty(Object key) {
        PropertyMap current = this.propertyMap;
        while (current.getLastProperty() != null) {
            if (current.getLastProperty().getKey().equals(key)) {
                return current.getLastProperty();
            }
            current = current.getParentMap();
        }
        return null;
    }

    protected final void addDirectTransition(Transition transition, ShapeImpl next) {
        assert next.getParent() == this && transition.isDirect();
        addTransitionInternal(transition, next);
    }

    public final void addIndirectTransition(Transition transition, ShapeImpl next) {
        assert next.getParent() != this && !transition.isDirect();
        addTransitionInternal(transition, next);
    }

    private void addTransitionInternal(Transition transition, ShapeImpl next) {
        getTransitionMapForWrite().put(transition, next);
    }

    public final Map<Transition, ShapeImpl> getTransitionMapForRead() {
        return transitionMap != null ? transitionMap : Collections.<Transition, ShapeImpl> emptyMap();
    }

    private Map<Transition, ShapeImpl> getTransitionMapForWrite() {
        if (transitionMap != null) {
            return transitionMap;
        } else {
            invalidateLeafAssumption();
            return transitionMap = new HashMap<>();
        }
    }

    public final PropertyMap getPropertyMap() {
        return propertyMap;
    }

    private ShapeImpl queryTransition(Transition transition) {
        ShapeImpl cachedShape = this.getTransitionMapForRead().get(transition);
        if (cachedShape != null) { // Shape already exists?
            shapeCacheHitCount.inc();
            return (ShapeImpl) layout.getStrategy().returnCached(cachedShape);
        }
        shapeCacheMissCount.inc();

        return null;
    }

    /**
     * Add a new property in the map, yielding a new or cached Shape object.
     *
     * @param property the property to add
     * @return the new Shape
     */
    @TruffleBoundary
    @Override
    public ShapeImpl addProperty(Property property) {
        assert isValid();
        ShapeImpl nextShape = addPropertyInternal(property);
        objectType.onPropertyAdded(property, this, nextShape);
        return nextShape;
    }

    /**
     * Add a new property in the map, yielding a new or cached Shape object.
     *
     * In contrast to {@link ShapeImpl#addProperty(Property)}, this method does not care about
     * obsolete shapes.
     *
     * @see #addProperty(Property)
     */
    private ShapeImpl addPropertyInternal(Property prop) {
        CompilerAsserts.neverPartOfCompilation();
        assert prop.isShadow() || !(this.hasProperty(prop.getKey())) : "duplicate property";
        assert !getPropertyListInternal(false).contains(prop);
        // invalidatePropertyAssumption(prop.getName());

        AddPropertyTransition addTransition = new AddPropertyTransition(prop);
        ShapeImpl cachedShape = queryTransition(addTransition);
        if (cachedShape != null) {
            return cachedShape;
        }

        ShapeImpl oldShape = (ShapeImpl) layout.getStrategy().ensureSpace(this, prop.getLocation());

        ShapeImpl newShape = makeShapeWithAddedProperty(oldShape, addTransition);
        oldShape.addDirectTransition(addTransition, newShape);
        return newShape;
    }

    protected ShapeImpl cloneRoot(ShapeImpl from, Object newSharedData) {
        return createShape(from.layout, newSharedData, null, from.objectType, from.propertyMap, null, from.allocator(), from.id);
    }

    /**
     * Create a separate clone of a shape.
     *
     * @param newParent the cloned parent shape
     */
    protected final ShapeImpl cloneOnto(ShapeImpl newParent) {
        ShapeImpl from = this;
        ShapeImpl newShape = createShape(newParent.layout, newParent.sharedData, newParent, from.objectType, from.propertyMap, from.transitionFromParent, from.allocator(), newParent.id);

        shapeCloneCount.inc();

        // (aw) need to have this transition for obsolescence
        newParent.addDirectTransition(from.transitionFromParent, newShape);
        return newShape;
    }

    public final Transition getTransitionFromParent() {
        return transitionFromParent;
    }

    /**
     * Create a new shape that adds a property to the parent shape.
     */
    private static ShapeImpl makeShapeWithAddedProperty(ShapeImpl parent, AddPropertyTransition addTransition) {
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
     */
    private static ShapeImpl makeShapeWithPrimitiveExtensionArray(ShapeImpl parent, Transition transition) {
        assert parent.getLayout().hasPrimitiveExtensionArray();
        assert !parent.hasPrimitiveArray();
        BaseAllocator allocator = parent.allocator().addLocation(parent.getLayout().getPrimitiveArrayLocation());
        ShapeImpl newShape = parent.createShape(parent.layout, parent.sharedData, parent, parent.objectType, parent.propertyMap, transition, allocator, parent.id);
        assert newShape.hasPrimitiveArray();
        assert newShape.depth == allocator.depth;
        return newShape;
    }

    private ShapeImpl addPrimitiveExtensionArray() {
        assert layout.hasPrimitiveExtensionArray() && !hasPrimitiveArray();
        Transition transition = new ReservePrimitiveArrayTransition();
        ShapeImpl cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return cachedShape;
        }

        ShapeImpl oldShape = (ShapeImpl) layout.getStrategy().ensureSpace(this, layout.getPrimitiveArrayLocation());
        ShapeImpl newShape = makeShapeWithPrimitiveExtensionArray(oldShape, transition);
        oldShape.addDirectTransition(transition, newShape);
        return newShape;
    }

    /**
     * Are these two shapes related, i.e. do they have the same root?
     *
     * @param other Shape to compare to
     * @return true if one shape is an upcast of the other, or the Shapes are equal
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
     */
    @TruffleBoundary
    @Override
    public final List<Property> getPropertyList(Pred<Property> filter) {
        LinkedList<Property> props = new LinkedList<>();
        next: for (Property currentProperty : this.propertyMap.reverseOrderValues()) {
            if (!currentProperty.isHidden() && filter.test(currentProperty)) {
                if (currentProperty.getLocation() instanceof DeclaredLocation) {
                    for (Iterator<Property> iter = props.iterator(); iter.hasNext();) {
                        Property other = iter.next();
                        if (other.isShadow() && other.getKey().equals(currentProperty.getKey())) {
                            iter.remove();
                            props.addFirst(other);
                            continue next;
                        }
                    }
                }
                props.addFirst(currentProperty);
            }
        }
        return props;
    }

    @Override
    public final List<Property> getPropertyList() {
        return getPropertyList(ALL);
    }

    /**
     * Returns all (also hidden) Property objects in this shape.
     *
     * @param ascending desired order
     */
    @TruffleBoundary
    @Override
    public final List<Property> getPropertyListInternal(boolean ascending) {
        LinkedList<Property> props = new LinkedList<>();
        for (Property current : this.propertyMap.reverseOrderValues()) {
            if (ascending) {
                props.addFirst(current);
            } else {
                props.add(current);
            }
        }
        return props;
    }

    /**
     * Get a list of all (visible) property names in insertion order.
     *
     * @return list of property names
     */
    @TruffleBoundary
    @Override
    public final List<Object> getKeyList(Pred<Property> filter) {
        LinkedList<Object> keys = new LinkedList<>();
        for (Property currentProperty : this.propertyMap.reverseOrderValues()) {
            if (!currentProperty.isHidden() && filter.test(currentProperty) && !currentProperty.isShadow()) {
                keys.addFirst(currentProperty.getKey());
            }
        }
        return keys;
    }

    @Override
    public final List<Object> getKeyList() {
        return getKeyList(ALL);
    }

    @Override
    public Iterable<Object> getKeys() {
        return getKeyList();
    }

    @Override
    public final boolean isValid() {
        return getValidAssumption().isValid();
    }

    @Override
    public final Assumption getValidAssumption() {
        return validAssumption;
    }

    private static Assumption createValidAssumption() {
        return Truffle.getRuntime().createAssumption("valid shape");
    }

    public final void invalidateValidAssumption() {
        getValidAssumption().invalidate();
    }

    @Override
    public final boolean isLeaf() {
        return leafAssumption == null || leafAssumption.isValid();
    }

    @Override
    public final Assumption getLeafAssumption() {
        if (leafAssumption == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (getMutex()) {
                if (leafAssumption == null) {
                    leafAssumption = isLeafHelper() ? createLeafAssumption() : NeverValidAssumption.INSTANCE;
                }
            }
        }
        return leafAssumption;
    }

    private boolean isLeafHelper() {
        return getTransitionMapForRead().isEmpty();
    }

    private static Assumption createLeafAssumption() {
        return Truffle.getRuntime().createAssumption("leaf shape");
    }

    private void invalidateLeafAssumption() {
        Assumption assumption = leafAssumption;
        if (assumption != null) {
            assumption.invalidate();
        } else {
            leafAssumption = NeverValidAssumption.INSTANCE;
        }
    }

    @Override
    public String toString() {
        return toStringLimit(Integer.MAX_VALUE);
    }

    @TruffleBoundary
    public String toStringLimit(int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append('@');
        sb.append(Integer.toHexString(hashCode()));
        if (!isValid()) {
            sb.append('!');
        }

        sb.append("{");
        for (Iterator<Property> iterator = propertyMap.reverseOrderValues().iterator(); iterator.hasNext();) {
            Property p = iterator.next();
            sb.append(p);
            if (iterator.hasNext()) {
                sb.append(", ");
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

    @Override
    public final ShapeImpl getParent() {
        return parent;
    }

    public final int getDepth() {
        return depth;
    }

    @Override
    public final boolean hasProperty(Object name) {
        return getProperty(name) != null;
    }

    @TruffleBoundary
    @Override
    public final ShapeImpl removeProperty(Property prop) {
        RemovePropertyTransition transition = new RemovePropertyTransition(prop);
        ShapeImpl cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return cachedShape;
        }

        ShapeImpl shape = getShapeFromProperty(prop);
        if (shape != null) {
            List<Transition> transitionList = new ArrayList<>();
            ShapeImpl current = this;
            while (current != shape) {
                transitionList.add(current.getTransitionFromParent());
                current = current.parent;
            }
            ShapeImpl newShape = shape.parent;
            for (ListIterator<Transition> iterator = transitionList.listIterator(transitionList.size()); iterator.hasPrevious();) {
                Transition previous = iterator.previous();
                newShape = newShape.applyTransition(previous, true);
            }

            getTransitionMapForWrite().put(transition, newShape);
            return newShape;
        } else {
            return null;
        }
    }

    @TruffleBoundary
    @Override
    public final ShapeImpl append(Property oldProperty) {
        return addProperty(oldProperty.relocate(allocator().moveLocation(oldProperty.getLocation())));
    }

    public final ShapeImpl applyTransition(Transition transition, boolean append) {
        if (transition instanceof AddPropertyTransition) {
            return append ? append(((AddPropertyTransition) transition).getProperty()) : addProperty(((AddPropertyTransition) transition).getProperty());
        } else if (transition instanceof ObjectTypeTransition) {
            return changeType(((ObjectTypeTransition) transition).getObjectType());
        } else if (transition instanceof ReservePrimitiveArrayTransition) {
            return reservePrimitiveExtensionArray();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public final BaseAllocator allocator() {
        return layout.getStrategy().createAllocator(this);
    }

    /**
     * Duplicate shape exchanging existing property with new property.
     */
    @Override
    public final ShapeImpl replaceProperty(Property oldProperty, Property newProperty) {
        Transition replacePropertyTransition = new Transition.ReplacePropertyTransition(oldProperty, newProperty);
        ShapeImpl cachedShape = queryTransition(replacePropertyTransition);
        if (cachedShape != null) {
            return cachedShape;
        }

        ShapeImpl top = this;
        List<Transition> transitionList = new ArrayList<>();
        boolean found = false;
        while (top != getRoot() && !found) {
            Transition transition = top.getTransitionFromParent();
            transitionList.add(transition);
            if (transition instanceof AddPropertyTransition && ((AddPropertyTransition) transition).getProperty().getKey().equals(newProperty.getKey())) {
                found = true;
            }
            top = top.parent;
        }
        ShapeImpl newShape = top;
        for (ListIterator<Transition> iterator = transitionList.listIterator(transitionList.size()); iterator.hasPrevious();) {
            Transition transition = iterator.previous();
            if (transition instanceof AddPropertyTransition && ((AddPropertyTransition) transition).getProperty().getKey().equals(newProperty.getKey())) {
                newShape = newShape.addProperty(newProperty);
            } else {
                newShape = newShape.applyTransition(transition, false);
            }
        }
        addIndirectTransition(replacePropertyTransition, newShape);
        return newShape;
    }

    /**
     * Find lowest common ancestor of two related shapes.
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

    /**
     * For copying over properties after exchanging the prototype of an object.
     */
    @TruffleBoundary
    @Override
    public final ShapeImpl copyOverPropertiesInternal(Shape destination) {
        assert ((ShapeImpl) destination).getDepth() == 0;
        List<Property> properties = this.getPropertyListInternal(true);
        ShapeImpl newShape = ((ShapeImpl) destination).addPropertiesInternal(properties);
        return newShape;
    }

    private ShapeImpl addPropertiesInternal(List<Property> properties) {
        ShapeImpl newShape = this;
        for (Property p : properties) {
            newShape = newShape.addPropertyInternal(p);
        }
        return newShape;
    }

    @Override
    public final int getPropertyCount() {
        return propertyCount;
    }

    /**
     * Find difference between two shapes.
     *
     * @see ObjectStorageOptions#TraceReshape
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
    public ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public ShapeImpl getRoot() {
        return root;
    }

    @Override
    public final boolean check(DynamicObject subject) {
        return subject.getShape() == this;
    }

    @Override
    public final LayoutImpl getLayout() {
        return layout;
    }

    @Override
    public final Object getData() {
        return extraData;
    }

    @Override
    public final Object getSharedData() {
        return sharedData;
    }

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

    @Override
    @TruffleBoundary
    public final ShapeImpl changeType(ObjectType newOps) {
        ObjectTypeTransition transition = new ObjectTypeTransition(newOps);
        ShapeImpl cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return cachedShape;
        }

        ShapeImpl newShape = createShape(layout, sharedData, this, newOps, propertyMap, transition, allocator(), id);
        addDirectTransition(transition, newShape);
        return newShape;
    }

    @Override
    public final ShapeImpl reservePrimitiveExtensionArray() {
        if (layout.hasPrimitiveExtensionArray() && !hasPrimitiveArray()) {
            return addPrimitiveExtensionArray();
        }
        return this;
    }

    @Override
    public final Iterable<Property> getProperties() {
        if (getPropertyCount() != 0 && propertyArray == null) {
            CompilerDirectives.transferToInterpreter();
            propertyArray = createPropertiesArray();
        }
        return new Iterable<Property>() {
            public Iterator<Property> iterator() {
                return new Iterator<Property>() {
                    private int cursor;

                    public boolean hasNext() {
                        return cursor < getPropertyCount();
                    }

                    public Property next() {
                        if (hasNext()) {
                            return propertyArray[cursor++];
                        }
                        throw new NoSuchElementException();
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    private Property[] createPropertiesArray() {
        propertyListAllocCount.inc();
        Property[] propertiesArray = new Property[getPropertyCount()];
        List<Property> ownProperties = getPropertyList(ALL);
        assert ownProperties.size() == getPropertyCount();
        for (int i = 0; i < getPropertyCount(); i++) {
            propertiesArray[i] = ownProperties.get(i);
        }
        return propertiesArray;
    }

    @Override
    public final DynamicObject newInstance() {
        return layout.newInstance(this);
    }

    @Override
    public final DynamicObjectFactory createFactory() {
        final List<Property> properties = getPropertyListInternal(true);
        for (Iterator<Property> iterator = properties.iterator(); iterator.hasNext();) {
            Property property = iterator.next();
            // skip non-instance fields
            assert property.getLocation() != layout.getPrimitiveArrayLocation();
            if (property.getLocation() instanceof ValueLocation) {
                iterator.remove();
            }
        }

        return new DynamicObjectFactory() {
            @CompilationFinal private final PropertyImpl[] instanceFields = properties.toArray(new PropertyImpl[properties.size()]);

            @ExplodeLoop
            public DynamicObject newInstance(Object... initialValues) {
                DynamicObject store = ShapeImpl.this.newInstance();
                for (int i = 0; i < instanceFields.length; i++) {
                    instanceFields[i].setInternal(store, initialValues[i]);
                }
                return store;
            }

            public Shape getShape() {
                return ShapeImpl.this;
            }
        };
    }

    @Override
    public Object getMutex() {
        return getRoot();
    }

    @Override
    public Shape tryMerge(Shape other) {
        return null;
    }

    public abstract static class BaseAllocator extends Allocator implements LocationVisitor {
        protected final LayoutImpl layout;
        protected int objectArraySize;
        protected int objectFieldSize;
        protected int primitiveFieldSize;
        protected int primitiveArraySize;
        protected boolean hasPrimitiveArray;
        protected int depth;

        protected BaseAllocator(LayoutImpl layout) {
            this.layout = layout;
        }

        protected BaseAllocator(ShapeImpl shape) {
            this(shape.getLayout());
            this.objectArraySize = shape.objectArraySize;
            this.objectFieldSize = shape.objectFieldSize;
            this.primitiveFieldSize = shape.primitiveFieldSize;
            this.primitiveArraySize = shape.primitiveArraySize;
            this.hasPrimitiveArray = shape.hasPrimitiveArray;
            this.depth = shape.depth;
        }

        protected abstract Location moveLocation(Location oldLocation);

        protected abstract Location newObjectLocation(boolean useFinal, boolean nonNull);

        protected abstract Location newTypedObjectLocation(boolean useFinal, Class<?> type, boolean nonNull);

        protected abstract Location newIntLocation(boolean useFinal);

        protected abstract Location newDoubleLocation(boolean useFinal);

        protected abstract Location newLongLocation(boolean useFinal);

        protected abstract Location newBooleanLocation(boolean useFinal);

        @Override
        public final Location constantLocation(Object value) {
            return new ConstantLocation(value);
        }

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

        protected abstract Location locationForValueUpcast(Object value, Location oldLocation);

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

        protected Location newDualLocation(Class<?> type) {
            return new DualLocation((InternalLongLocation) newLongLocation(false), (ObjectLocation) newObjectLocation(false, false), layout, type);
        }

        protected DualLocation newDualLocationForValue(Object value) {
            Class<?> initialType = null;
            if (value instanceof Integer) {
                initialType = int.class;
            } else if (value instanceof Double) {
                initialType = double.class;
            } else if (value instanceof Boolean) {
                initialType = boolean.class;
            } else {
                initialType = Object.class;
            }
            return new DualLocation((InternalLongLocation) newLongLocation(false), (ObjectLocation) newObjectLocation(false, false), layout, initialType);
        }

        protected Location newDeclaredDualLocation(Object value) {
            return new DeclaredDualLocation((InternalLongLocation) newLongLocation(false), (ObjectLocation) newObjectLocation(false, false), value, layout);
        }

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

        @Override
        public BaseAllocator addLocation(Location location) {
            advance(location);
            return this;
        }

        public void visitObjectField(int index, int count) {
            objectFieldSize = Math.max(objectFieldSize, index + count);
        }

        public void visitObjectArray(int index, int count) {
            objectArraySize = Math.max(objectArraySize, index + count);
        }

        public void visitPrimitiveArray(int index, int count) {
            primitiveArraySize = Math.max(primitiveArraySize, index + count);
        }

        public void visitPrimitiveField(int index, int count) {
            primitiveFieldSize = Math.max(primitiveFieldSize, index + count);
        }
    }

    private static void debugRegisterShape(ShapeImpl newShape) {
        if (ObjectStorageOptions.DumpShapes) {
            Debug.registerShape(newShape);
        }
    }

    /**
     * Match all filter.
     */
    public static final Pred<Property> ALL = new Pred<Property>() {
        public boolean test(Property t) {
            return true;
        }
    };

    private static final DebugCounter shapeCount = DebugCounter.create("Shapes allocated total");
    private static final DebugCounter shapeCloneCount = DebugCounter.create("Shapes allocated cloned");
    private static final DebugCounter shapeCacheHitCount = DebugCounter.create("Shape cache hits");
    private static final DebugCounter shapeCacheMissCount = DebugCounter.create("Shape cache misses");

    protected static final DebugCounter propertyListAllocCount = DebugCounter.create("Property lists allocated");
    protected static final DebugCounter propertyListShareCount = DebugCounter.create("Property lists shared");

    public ForeignAccessFactory getForeignAccessFactory() {
        return getObjectType().getForeignAccessFactory();
    }
}
