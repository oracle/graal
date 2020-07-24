/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.object.LayoutImpl.ACCESS;
import static com.oracle.truffle.object.LocationImpl.expectBoolean;
import static com.oracle.truffle.object.LocationImpl.expectDouble;
import static com.oracle.truffle.object.LocationImpl.expectInteger;
import static com.oracle.truffle.object.LocationImpl.expectLong;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.utilities.AlwaysValidAssumption;
import com.oracle.truffle.api.utilities.NeverValidAssumption;

/**
 * The implementation of {@link DynamicObjectLibrary}.
 */
@ExportLibrary(value = DynamicObjectLibrary.class, receiverType = DynamicObject.class, priority = 10, transitionLimit = "5")
abstract class DynamicObjectLibraryImpl {

    static final int KEY_LIMIT = 3;

    static boolean keyEquals(Object cachedKey, Object key) {
        if (cachedKey instanceof String) {
            return cachedKey == key || (key instanceof String && ((String) cachedKey).equals(key));
        } else if (cachedKey instanceof HiddenKey) {
            return key == cachedKey;
        } else if (cachedKey instanceof Long) {
            return key instanceof Long && ((Long) cachedKey).equals(key);
        } else {
            return cachedKey == key || keyEqualsBoundary(cachedKey, key);
        }
    }

    @TruffleBoundary(allowInlining = true)
    static boolean keyEqualsBoundary(Object cachedKey, Object key) {
        return Objects.equals(cachedKey, key);
    }

    @ExportMessage
    static boolean accepts(DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return object.getShape() == cachedShape;
    }

    @ExportMessage
    static Shape getShape(@SuppressWarnings("unused") DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return cachedShape;
    }

    @ExportMessage
    static Object getOrDefault(DynamicObject object, Object key, Object defaultValue,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        return keyCache.getOrDefault(object, cachedShape, key, defaultValue);
    }

    @ExportMessage
    static int getIntOrDefault(DynamicObject object, Object key, Object defaultValue,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) throws UnexpectedResultException {
        return keyCache.getIntOrDefault(object, cachedShape, key, defaultValue);
    }

    @ExportMessage
    static double getDoubleOrDefault(DynamicObject object, Object key, Object defaultValue,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) throws UnexpectedResultException {
        return keyCache.getDoubleOrDefault(object, cachedShape, key, defaultValue);
    }

    @ExportMessage
    static long getLongOrDefault(DynamicObject object, Object key, Object defaultValue,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) throws UnexpectedResultException {
        return keyCache.getLongOrDefault(object, cachedShape, key, defaultValue);
    }

    @ExportMessage
    static boolean containsKey(DynamicObject object, Object key,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        return keyCache.containsKey(object, cachedShape, key);
    }

    @ExportMessage
    static void put(DynamicObject object, Object key, Object value,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        keyCache.put(object, cachedShape, key, value, Flags.DEFAULT);
    }

    @ExportMessage
    static void putInt(DynamicObject object, Object key, int value,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        keyCache.putInt(object, cachedShape, key, value, Flags.DEFAULT);
    }

    @ExportMessage
    static void putLong(DynamicObject object, Object key, long value,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        keyCache.putLong(object, cachedShape, key, value, Flags.DEFAULT);
    }

    @ExportMessage
    static void putDouble(DynamicObject object, Object key, double value,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        keyCache.putDouble(object, cachedShape, key, value, Flags.DEFAULT);
    }

    @ExportMessage
    static boolean putIfPresent(DynamicObject object, Object key, Object value,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        return keyCache.put(object, cachedShape, key, value, Flags.SET_EXISTING);
    }

    @ExportMessage
    static void putWithFlags(DynamicObject object, Object key, Object value, int flags,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        keyCache.put(object, cachedShape, key, value, Flags.propertyFlagsToPutFlags(flags) | Flags.UPDATE_FLAGS);
    }

    @ExportMessage
    static void putConstant(DynamicObject object, Object key, Object value, int flags,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        keyCache.put(object, cachedShape, key, value, Flags.propertyFlagsToPutFlags(flags) | Flags.UPDATE_FLAGS | Flags.CONST);
    }

    @ExportMessage
    public static Property getProperty(@SuppressWarnings("unused") DynamicObject object, Object key,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        return keyCache.getProperty(object, cachedShape, key);
    }

    @ExportMessage
    public static boolean setPropertyFlags(DynamicObject object, Object key, int propertyFlags,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        return keyCache.setPropertyFlags(object, cachedShape, key, propertyFlags);
    }

    @TruffleBoundary
    @ExportMessage
    public static boolean removeKey(DynamicObject obj, Object key) {
        ShapeImpl oldShape = (ShapeImpl) ACCESS.getShape(obj);
        Property property = oldShape.getProperty(key);
        if (property == null) {
            return false;
        }

        Map<Object, Object> archive = null;
        assert (archive = ACCESS.archive(obj)) != null;

        ShapeImpl newShape = oldShape.removeProperty(property);
        assert oldShape != newShape;
        assert ACCESS.getShape(obj) == oldShape;
        ACCESS.setShape(obj, newShape);

        if (!oldShape.isShared()) {
            shiftPropertyValuesAfterRemove(obj, oldShape, newShape);
            ACCESS.trimToSize(obj, newShape);
        }

        assert ACCESS.verifyValues(obj, archive);
        return true;
    }

    @ExportMessage
    public static Object getDynamicType(@SuppressWarnings("unused") DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return cachedShape.getDynamicType();
    }

    @ExportMessage
    public static boolean setDynamicType(DynamicObject object, @SuppressWarnings("unused") Object objectType,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Cached SetDynamicTypeNode setCache) {
        return setCache.execute(object, cachedShape, objectType);
    }

    @ExportMessage
    public static int getShapeFlags(@SuppressWarnings("unused") DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return cachedShape.getFlags();
    }

    @ExportMessage
    public static boolean setShapeFlags(DynamicObject object, @SuppressWarnings("unused") int flags,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Cached SetFlagsNode setCache) {
        return setCache.execute(object, cachedShape, flags);
    }

    @ExportMessage
    public static boolean isShared(@SuppressWarnings("unused") DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return cachedShape.isShared();
    }

    @ExportMessage
    public static void markShared(DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Cached MakeSharedNode setCache) {
        setCache.execute(object, cachedShape);
    }

    @ExportMessage
    public static boolean updateShape(DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        if (cachedShape.isValid()) {
            return false;
        } else {
            return updateShapeImpl(object);
        }
    }

    @TruffleBoundary
    static boolean updateShapeImpl(DynamicObject object) {
        return ((ShapeImpl) object.getShape()).getLayout().getStrategy().updateShape(object);
    }

    @ExportMessage
    public static boolean resetShape(DynamicObject object, Shape otherShape,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Cached ResetShapeNode setCache) {
        return setCache.execute(object, cachedShape, otherShape);
    }

    @ExportMessage
    public static Object[] getKeyArray(@SuppressWarnings("unused") DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return ((ShapeImpl) cachedShape).getKeyArray();
    }

    @ExportMessage
    public static Property[] getPropertyArray(@SuppressWarnings("unused") DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return ((ShapeImpl) cachedShape).getPropertyArray();
    }

    private static LocationImpl getLocation(Property existing) {
        return (LocationImpl) existing.getLocation();
    }

    @TruffleBoundary
    protected static boolean putUncached(DynamicObject object, Object key, Object value, long putFlags) {
        Shape s = ACCESS.getShape(object);
        Property existingProperty = s.getProperty(key);
        if (existingProperty == null && Flags.isSetExisting(putFlags)) {
            return false;
        }
        if (existingProperty != null && !Flags.isUpdateFlags(putFlags) && existingProperty.getLocation().canSet(value)) {
            try {
                getLocation(existingProperty).set(object, value, false);
            } catch (IncompatibleLocationException | FinalLocationException e) {
                throw shouldNotHappen(e);
            }
            return true;
        } else {
            return putUncachedSlow(object, key, value, putFlags);
        }
    }

    private static boolean putUncachedSlow(DynamicObject object, Object key, Object value, long putFlags) {
        CompilerAsserts.neverPartOfCompilation();
        updateShapeImpl(object);
        ShapeImpl oldShape;
        Property existingProperty;
        Shape newShape;
        Property property;
        do {
            oldShape = (ShapeImpl) ACCESS.getShape(object);
            existingProperty = oldShape.getProperty(key);
            if (existingProperty == null) {
                if (Flags.isSetExisting(putFlags)) {
                    return false;
                } else {
                    LayoutStrategy strategy = oldShape.getLayout().getStrategy();
                    LocationFactory locationFactory = getLocationFactory(strategy, putFlags);
                    newShape = strategy.defineProperty(oldShape, key, value, Flags.getPropertyFlags(putFlags), locationFactory, existingProperty, putFlags);
                    property = newShape.getProperty(key);
                }
            } else if (Flags.isUpdateFlags(putFlags) && Flags.getPropertyFlags(putFlags) != existingProperty.getFlags()) {
                LayoutStrategy strategy = oldShape.getLayout().getStrategy();
                LocationFactory locationFactory = getLocationFactory(strategy, putFlags);
                newShape = strategy.defineProperty(oldShape, key, value, Flags.getPropertyFlags(putFlags), locationFactory, existingProperty, putFlags);
                property = newShape.getProperty(key);
            } else {
                if (existingProperty.getLocation().canSet(value)) {
                    newShape = oldShape;
                    property = existingProperty;
                } else {
                    LayoutStrategy strategy = oldShape.getLayout().getStrategy();
                    LocationFactory locationFactory = getLocationFactory(strategy, putFlags);
                    newShape = strategy.defineProperty(oldShape, key, value, existingProperty.getFlags(), locationFactory, existingProperty, putFlags);
                    property = newShape.getProperty(key);
                }
            }
        } while (updateShapeImpl(object));

        assert ACCESS.getShape(object) == oldShape;
        if (oldShape != newShape) {
            ACCESS.growAndSetShape(object, oldShape, newShape);
            try {
                getLocation(property).setInternal(object, value, false);
            } catch (IncompatibleLocationException e) {
                throw shouldNotHappen(e);
            }
            updateShapeImpl(object);
        } else {
            try {
                getLocation(property).set(object, value, false);
            } catch (IncompatibleLocationException | FinalLocationException e) {
                throw shouldNotHappen(e);
            }
        }
        return true;
    }

    private static final LocationFactory CONSTANT_LOCATION_FACTORY = new LocationFactory() {
        @Override
        public Location createLocation(Shape s, Object v) {
            return s.allocator().constantLocation(v);
        }
    };

    private static final LocationFactory DECLARED_LOCATION_FACTORY = new LocationFactory() {
        @Override
        public Location createLocation(Shape s, Object v) {
            return s.allocator().declaredLocation(v);
        }
    };

    static LocationFactory getLocationFactory(LayoutStrategy strategy, long putFlags) {
        if (Flags.isConstant(putFlags)) {
            return CONSTANT_LOCATION_FACTORY;
        } else if (Flags.isDeclaration(putFlags)) {
            return DECLARED_LOCATION_FACTORY;
        } else {
            return strategy.getDefaultLocationFactory(putFlags);
        }
    }

    private static void shiftPropertyValuesAfterRemove(DynamicObject object, ShapeImpl oldShape, ShapeImpl newShape) {
        List<Move> moves = new ArrayList<>();
        for (ListIterator<Property> iterator = newShape.getPropertyListInternal(false).listIterator(); iterator.hasNext();) {
            Property to = iterator.next();
            Property from = oldShape.getProperty(to.getKey());
            LocationImpl fromLoc = getLocation(from);
            LocationImpl toLoc = getLocation(to);
            if (LocationImpl.isSameLocation(toLoc, fromLoc)) {
                continue;
            }
            assert !toLoc.isValue();
            Move move = new Move(fromLoc, toLoc, oldShape.getLayout().getStrategy());
            moves.add(move);
        }
        if (!isSorted(moves)) {
            Collections.sort(moves);
        }
        // perform the moves in inverse order
        for (ListIterator<Move> iterator = moves.listIterator(moves.size()); iterator.hasPrevious();) {
            Move current = iterator.previous();
            boolean last = !iterator.hasPrevious();
            current.accept(object, last);
        }
    }

    private static boolean isSorted(List<Move> moves) {
        for (int i = 1; i < moves.size(); i++) {
            Move m1 = moves.get(i - 1);
            Move m2 = moves.get(i);
            if (m1.compareTo(m2) > 0) {
                return false;
            }
        }
        return true;
    }

    private static class Move implements Comparable<Move> {
        private final LocationImpl fromLoc;
        private final LocationImpl toLoc;
        private final LayoutStrategy strategy;

        Move(LocationImpl fromLoc, LocationImpl toLoc, LayoutStrategy strategy) {
            this.fromLoc = fromLoc;
            this.toLoc = toLoc;
            this.strategy = strategy;
        }

        public void accept(DynamicObject obj, boolean last) {
            try {
                Object fromValue = fromLoc.get(obj, false);
                toLoc.setInternal(obj, fromValue, false);
                if (last && fromLoc instanceof CoreLocations.ObjectLocation && fromValue != null) {
                    // clear location to avoid memory leaks
                    fromLoc.setInternal(obj, null, false);
                }
            } catch (IncompatibleLocationException e) {
                throw shouldNotHappen(e);
            }
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return fromLoc + " => " + toLoc;
        }

        @Override
        public int compareTo(Move other) {
            int order = Integer.compare(strategy.getLocationOrdinal(fromLoc), strategy.getLocationOrdinal(other.fromLoc));
            assert order == Integer.compare(strategy.getLocationOrdinal(toLoc), strategy.getLocationOrdinal(other.toLoc));
            return -order;
        }
    }

    abstract static class KeyCacheNode extends Node {
        public abstract Object getOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue);

        public abstract int getIntOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract long getLongOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract double getDoubleOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract boolean getBooleanOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract boolean put(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags);

        public abstract boolean containsKey(DynamicObject object, Shape cachedShape, Object key);

        public abstract Property getProperty(DynamicObject object, Shape cachedShape, Object key);

        public abstract boolean setPropertyFlags(DynamicObject object, Shape cachedShape, Object key, int propertyFlags);

        public boolean putInt(DynamicObject object, Shape cachedShape, Object key, int value, long putFlags) {
            return put(object, cachedShape, key, value, putFlags);
        }

        public boolean putLong(DynamicObject object, Shape cachedShape, Object key, long value, long putFlags) {
            return put(object, cachedShape, key, value, putFlags);
        }

        public boolean putDouble(DynamicObject object, Shape cachedShape, Object key, double value, long putFlags) {
            return put(object, cachedShape, key, value, putFlags);
        }

        public boolean putBoolean(DynamicObject object, Shape cachedShape, Object key, boolean value, long putFlags) {
            return put(object, cachedShape, key, value, putFlags);
        }

        static KeyCacheNode create(Shape cachedShape, Object key) {
            if (key == null) {
                return getUncached();
            }
            return AnyKey.create(key, cachedShape);
        }

        static KeyCacheEntry getUncached() {
            return Generic.instance();
        }
    }

    abstract static class KeyCacheEntry extends KeyCacheNode {
        @Child KeyCacheEntry next;

        KeyCacheEntry(KeyCacheEntry next) {
            this.next = next;
        }

        public boolean acceptsKey(@SuppressWarnings("unused") Object key) {
            return true;
        }
    }

    static class Generic extends KeyCacheEntry {
        private static final Generic INSTANCE = new Generic();

        Generic() {
            super(null);
        }

        static Generic instance() {
            return INSTANCE;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @TruffleBoundary
        @Override
        public Object getOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) {
            Property existing = ACCESS.getShape(object).getProperty(key);
            if (existing != null) {
                return getLocation(existing).get(object, false);
            } else {
                return defaultValue;
            }
        }

        @TruffleBoundary
        @Override
        public int getIntOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            Property existing = ACCESS.getShape(object).getProperty(key);
            if (existing != null) {
                return getLocation(existing).getInt(object, false);
            } else {
                return expectInteger(defaultValue);
            }
        }

        @TruffleBoundary
        @Override
        public long getLongOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            Property existing = ACCESS.getShape(object).getProperty(key);
            if (existing != null) {
                return getLocation(existing).getLong(object, false);
            } else {
                return expectLong(defaultValue);
            }
        }

        @TruffleBoundary
        @Override
        public double getDoubleOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            Property existing = ACCESS.getShape(object).getProperty(key);
            if (existing != null) {
                return getLocation(existing).getDouble(object, false);
            } else {
                return expectDouble(defaultValue);
            }
        }

        @TruffleBoundary
        @Override
        public boolean getBooleanOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            Property existing = ACCESS.getShape(object).getProperty(key);
            if (existing != null) {
                return getLocation(existing).getBoolean(object, false);
            } else {
                return expectBoolean(defaultValue);
            }
        }

        @Override
        public boolean put(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags) {
            return putUncached(object, key, value, putFlags);
        }

        @Override
        public boolean containsKey(DynamicObject object, Shape cachedShape, Object key) {
            Property existing = getProperty(object, cachedShape, key);
            return existing != null;
        }

        @Override
        public Property getProperty(DynamicObject object, Shape cachedShape, Object key) {
            return ACCESS.getShape(object).getProperty(key);
        }

        @TruffleBoundary
        @Override
        public boolean setPropertyFlags(DynamicObject object, Shape cachedShape, Object key, int propertyFlags) {
            ShapeImpl oldShape = (ShapeImpl) ACCESS.getShape(object);
            Property existingProperty = oldShape.getProperty(key);
            if (existingProperty == null) {
                return false;
            }
            if (existingProperty.getFlags() != propertyFlags) {
                Shape newShape = oldShape.replaceProperty(existingProperty, ((PropertyImpl) existingProperty).copyWithFlags(propertyFlags));
                if (newShape != oldShape) {
                    ACCESS.setShape(object, newShape);
                }
            }
            return true;
        }
    }

    /**
     * Polymorphic inline cache for a limited number of distinct property keys.
     *
     * The generic case is used if the number of property keys accessed overflows the limit of the
     * polymorphic inline cache.
     */
    static class AnyKey extends KeyCacheNode {

        @Child private KeyCacheEntry keyCache;

        AnyKey(KeyCacheEntry keyCache) {
            this.keyCache = keyCache;
        }

        public static KeyCacheNode create() {
            return new AnyKey(null);
        }

        public static KeyCacheNode create(Object key, Shape cachedShape) {
            return new AnyKey(SpecificKey.create(key, cachedShape, null));
        }

        @ExplodeLoop
        @Override
        public Object getOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.getOrDefault(object, cachedShape, key, defaultValue);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.getOrDefault(object, cachedShape, key, defaultValue);
                }
            }
            return Generic.instance().getOrDefault(object, cachedShape, key, defaultValue);
        }

        @ExplodeLoop
        @Override
        public int getIntOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.getIntOrDefault(object, cachedShape, key, defaultValue);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.getIntOrDefault(object, cachedShape, key, defaultValue);
                }
            }
            return Generic.instance().getIntOrDefault(object, cachedShape, key, defaultValue);
        }

        @ExplodeLoop
        @Override
        public long getLongOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.getLongOrDefault(object, cachedShape, key, defaultValue);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.getLongOrDefault(object, cachedShape, key, defaultValue);
                }
            }
            return Generic.instance().getLongOrDefault(object, cachedShape, key, defaultValue);
        }

        @ExplodeLoop
        @Override
        public double getDoubleOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.getDoubleOrDefault(object, cachedShape, key, defaultValue);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.getDoubleOrDefault(object, cachedShape, key, defaultValue);
                }
            }
            return Generic.instance().getDoubleOrDefault(object, cachedShape, key, defaultValue);
        }

        @ExplodeLoop
        @Override
        public boolean getBooleanOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.getBooleanOrDefault(object, cachedShape, key, defaultValue);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.getBooleanOrDefault(object, cachedShape, key, defaultValue);
                }
            }
            return Generic.instance().getBooleanOrDefault(object, cachedShape, key, defaultValue);
        }

        @ExplodeLoop
        @Override
        public boolean put(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags) {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.put(object, cachedShape, key, value, putFlags);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.put(object, cachedShape, key, value, putFlags);
                }
            }
            return Generic.instance().put(object, cachedShape, key, value, putFlags);
        }

        @ExplodeLoop
        @Override
        public boolean containsKey(DynamicObject object, Shape cachedShape, Object key) {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.containsKey(object, cachedShape, key);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.containsKey(object, cachedShape, key);
                }
            }
            return Generic.instance().containsKey(object, cachedShape, key);
        }

        @ExplodeLoop
        @Override
        public Property getProperty(DynamicObject object, Shape cachedShape, Object key) {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.getProperty(object, cachedShape, key);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.getProperty(object, cachedShape, key);
                }
            }
            return Generic.instance().getProperty(object, cachedShape, key);
        }

        @ExplodeLoop
        @Override
        public boolean setPropertyFlags(DynamicObject object, Shape cachedShape, Object key, int propertyFlags) {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.setPropertyFlags(object, cachedShape, key, propertyFlags);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.setPropertyFlags(object, cachedShape, key, propertyFlags);
                }
            }
            return Generic.instance().setPropertyFlags(object, cachedShape, key, propertyFlags);
        }

        private KeyCacheNode insertIntoKeyCache(Object key, Shape cachedShape) {
            CompilerAsserts.neverPartOfCompilation();
            Lock lock = getLock();
            lock.lock();
            try {
                KeyCacheEntry tail = this.keyCache;
                int cachedCount = 0;
                boolean generic = false;

                for (KeyCacheEntry c = tail; c != null; c = c.next) {
                    if (c == KeyCacheNode.getUncached()) {
                        generic = true;
                        break;
                    } else {
                        cachedCount++;
                        if (c.acceptsKey(key)) {
                            return c;
                        }
                    }
                }

                if (cachedCount >= KEY_LIMIT) {
                    generic = true;
                    this.keyCache = KeyCacheNode.getUncached();
                }
                if (generic) {
                    return null;
                }

                if (cachedCount == 1) {
                    reportPolymorphicSpecialize();
                }

                SpecificKey newEntry = SpecificKey.create(key, cachedShape, tail);
                insert(newEntry);
                this.keyCache = newEntry;
                return this;
            } finally {
                lock.unlock();
            }
        }
    }

    abstract static class SpecificKey extends KeyCacheEntry {
        final Object cachedKey;

        @CompilationFinal PutCacheData cache;

        SpecificKey(Object key, KeyCacheEntry next) {
            super(next);
            this.cachedKey = key;
        }

        static SpecificKey create(Object key, Shape shape, KeyCacheEntry next) {
            if (key != null) {
                Property property = shape.getProperty(key);
                if (property != null) {
                    return new SpecificKey.ExistingKey(key, property, next);
                }
            }
            return new SpecificKey.MissingKey(key, next);
        }

        protected final boolean assertCachedKeyAndShapeForRead(DynamicObject object, Shape cachedShape, Object key) {
            // The object's Shape might differ from cachedShape if cachedShape is shared,
            // this is fine for reads.
            assert object.getShape() == cachedShape || cachedShape.isShared();
            assert keyEquals(this.cachedKey, key);
            return true;
        }

        protected final boolean assertCachedKeyAndShapeForWrite(DynamicObject object, Shape cachedShape, Object key) {
            assert object.getShape() == cachedShape;
            assert keyEquals(this.cachedKey, key);
            return true;
        }

        @Override
        public boolean acceptsKey(Object key) {
            return keyEquals(cachedKey, key);
        }

        static class ExistingKey extends SpecificKey {
            final Property cachedProperty;

            ExistingKey(Object key, Property property, KeyCacheEntry next) {
                super(key, next);
                this.cachedProperty = property;
            }

            private static boolean guard(DynamicObject object, Shape cachedShape) {
                return object.getShape() == cachedShape;
            }

            @Override
            public Object getOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return getLocation(cachedProperty).get(object, guard(object, cachedShape));
            }

            @Override
            public int getIntOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return getLocation(cachedProperty).getInt(object, guard(object, cachedShape));
            }

            @Override
            public long getLongOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return getLocation(cachedProperty).getLong(object, guard(object, cachedShape));
            }

            @Override
            public double getDoubleOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return getLocation(cachedProperty).getDouble(object, guard(object, cachedShape));
            }

            @Override
            public boolean getBooleanOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return getLocation(cachedProperty).getBoolean(object, guard(object, cachedShape));
            }

            @Override
            public boolean put(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean putInt(DynamicObject object, Shape cachedShape, Object key, int value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putIntImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean putLong(DynamicObject object, Shape cachedShape, Object key, long value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putLongImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean putDouble(DynamicObject object, Shape cachedShape, Object key, double value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putDoubleImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean putBoolean(DynamicObject object, Shape cachedShape, Object key, boolean value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putBooleanImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean containsKey(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return true;
            }

            @Override
            public Property getProperty(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return cachedProperty;
            }

            @Override
            public boolean setPropertyFlags(DynamicObject object, Shape cachedShape, Object key, int propertyFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                if (cachedProperty.getFlags() != propertyFlags) {
                    ShapeImpl oldShape = (ShapeImpl) cachedShape;
                    ShapeImpl newShape = changePropertyFlags(oldShape, (PropertyImpl) cachedProperty, propertyFlags);
                    if (newShape != oldShape) {
                        ACCESS.setShape(object, newShape);
                    }
                }
                return true;
            }

            @TruffleBoundary
            private static ShapeImpl changePropertyFlags(ShapeImpl shape, PropertyImpl cachedProperty, int propertyFlags) {
                return shape.replaceProperty(cachedProperty, cachedProperty.copyWithFlags(propertyFlags));
            }
        }

        static class MissingKey extends SpecificKey {
            MissingKey(Object key, KeyCacheEntry next) {
                super(key, next);
            }

            @Override
            public Object getOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return defaultValue;
            }

            @Override
            public boolean put(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean putInt(DynamicObject object, Shape cachedShape, Object key, int value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putIntImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean putLong(DynamicObject object, Shape cachedShape, Object key, long value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putLongImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean putDouble(DynamicObject object, Shape cachedShape, Object key, double value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putDoubleImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean putBoolean(DynamicObject object, Shape cachedShape, Object key, boolean value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return putBooleanImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean containsKey(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return false;
            }

            @Override
            public Property getProperty(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForRead(object, cachedShape, key);
                return null;
            }

            @Override
            public int getIntOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                return expectInteger(defaultValue);
            }

            @Override
            public long getLongOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                return expectLong(defaultValue);
            }

            @Override
            public double getDoubleOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                return expectDouble(defaultValue);
            }

            @Override
            public boolean getBooleanOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                return expectBoolean(defaultValue);
            }

            @Override
            public boolean setPropertyFlags(DynamicObject object, Shape cachedShape, Object key, int propertyFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return false;
            }
        }

        @ExplodeLoop
        protected boolean putImpl(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags, Property oldProperty) {
            Shape oldShape = cachedShape;
            PutCacheData start = cache;
            if (start == PutCacheData.GENERIC || !cachedShape.isValid()) {
                return putUncached(object, key, value, putFlags);
            }
            for (PutCacheData c = start; c != null; c = c.next) {
                if (!c.isValid()) {
                    break;
                } else if (c.putFlags == putFlags) {
                    Property newProperty = c.property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else {
                        LocationImpl location = getLocation(newProperty);
                        if (location.canStore(value)) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.set(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            KeyCacheNode impl = insertIntoPutCache(object, cachedShape, value, putFlags, oldProperty);
            return impl.put(object, cachedShape, key, value, putFlags);
        }

        @ExplodeLoop
        protected boolean putIntImpl(DynamicObject object, Shape cachedShape, Object key, int value, long putFlags, Property oldProperty) {
            Shape oldShape = cachedShape;
            PutCacheData start = cache;
            if (start == PutCacheData.GENERIC || !cachedShape.isValid()) {
                return putUncached(object, key, value, putFlags);
            }
            for (PutCacheData c = start; c != null; c = c.next) {
                if (!c.isValid()) {
                    break;
                } else if (c.putFlags == putFlags) {
                    Property newProperty = c.property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else {
                        LocationImpl location = getLocation(newProperty);
                        Shape newShape = c.newShape;
                        if (location.isIntLocation()) {
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.setInt(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (location.isImplicitCastIntToLong()) {
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.setLong(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (location.isImplicitCastIntToDouble()) {
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.setDouble(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (location.canStore(value)) {
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.set(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            KeyCacheNode impl = insertIntoPutCache(object, cachedShape, value, putFlags, oldProperty);
            return impl.putInt(object, cachedShape, key, value, putFlags);
        }

        @ExplodeLoop
        protected boolean putLongImpl(DynamicObject object, Shape cachedShape, Object key, long value, long putFlags, Property oldProperty) {
            Shape oldShape = cachedShape;
            PutCacheData start = cache;
            if (start == PutCacheData.GENERIC) {
                return putUncached(object, key, value, putFlags);
            }
            for (PutCacheData c = start; c != null; c = c.next) {
                if (c.putFlags == putFlags) {
                    Property newProperty = c.property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else {
                        LocationImpl location = getLocation(newProperty);
                        if (location.isLongLocation()) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.setLong(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (location.canStore(value)) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.set(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            KeyCacheNode impl = insertIntoPutCache(object, cachedShape, value, putFlags, oldProperty);
            return impl.putLong(object, cachedShape, key, value, putFlags);
        }

        @ExplodeLoop
        protected boolean putDoubleImpl(DynamicObject object, Shape cachedShape, Object key, double value, long putFlags, Property oldProperty) {
            Shape oldShape = cachedShape;
            PutCacheData start = cache;
            if (start == PutCacheData.GENERIC) {
                return putUncached(object, key, value, putFlags);
            }
            for (PutCacheData c = start; c != null; c = c.next) {
                if (c.putFlags == putFlags) {
                    Property newProperty = c.property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else {
                        LocationImpl location = getLocation(newProperty);
                        if (location.isDoubleLocation()) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.setDouble(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (newProperty.getLocation().canStore(value)) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.set(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            KeyCacheNode impl = insertIntoPutCache(object, cachedShape, value, putFlags, oldProperty);
            return impl.putDouble(object, cachedShape, key, value, putFlags);
        }

        @ExplodeLoop
        protected boolean putBooleanImpl(DynamicObject object, Shape cachedShape, Object key, boolean value, long putFlags, Property oldProperty) {
            Shape oldShape = cachedShape;
            PutCacheData start = cache;
            if (start == PutCacheData.GENERIC) {
                return putUncached(object, key, value, putFlags);
            }
            for (PutCacheData c = start; c != null; c = c.next) {
                if (c.putFlags == putFlags) {
                    Property newProperty = c.property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else {
                        LocationImpl location = getLocation(newProperty);
                        if (location.canStore(value)) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.growAndSetShape(object, oldShape, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            }
                            try {
                                location.set(object, value, object.getShape() == oldShape);
                            } catch (IncompatibleLocationException | FinalLocationException e) {
                                throw shouldNotHappen(e);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            KeyCacheNode impl = insertIntoPutCache(object, cachedShape, value, putFlags, oldProperty);
            return impl.putBoolean(object, cachedShape, key, value, putFlags);
        }

        protected KeyCacheNode insertIntoPutCache(DynamicObject object, Shape cachedShape, Object value, long putFlags, Property property) {
            CompilerAsserts.neverPartOfCompilation();
            if (!cachedShape.isValid()) {
                return Generic.instance();
            }
            Lock lock = getLock();
            lock.lock();
            try {
                PutCacheData tail = filterValid(this.cache);

                ShapeImpl oldShape = (ShapeImpl) cachedShape;
                ShapeImpl newShape = getNewShape(object, value, putFlags, property, oldShape);

                if (!oldShape.isValid()) {
                    // If shape was invalidated, other locations may have changed, too,
                    // so we need to update the object's shape first.
                    // Cache entries with an invalid cache entry directly go to the slow path.
                    return Generic.instance();
                }

                Property newProperty;
                if (newShape == oldShape) {
                    newProperty = property;
                } else {
                    newProperty = newShape.getProperty(cachedKey);
                    assert newProperty.getLocation().canSet(value);
                }

                Assumption newShapeValid = getShapeValidAssumption(oldShape, newShape);
                this.cache = new PutCacheData(putFlags, newShape, newProperty, newShapeValid, tail);
                return this;
            } finally {
                lock.unlock();
            }
        }

        private ShapeImpl getNewShape(DynamicObject object, Object value, long putFlags, Property property, ShapeImpl oldShape) {
            if (property == null) {
                if (Flags.isSetExisting(putFlags)) {
                    return oldShape;
                } else {
                    int propertyFlags = Flags.getPropertyFlags(putFlags);
                    LayoutStrategy strategy = oldShape.getLayout().getStrategy();
                    return oldShape.defineProperty(cachedKey, value, propertyFlags, getLocationFactory(strategy, putFlags));
                }
            }

            if (Flags.isUpdateFlags(putFlags)) {
                if (Flags.getPropertyFlags(putFlags) != property.getFlags()) {
                    int propertyFlags = Flags.getPropertyFlags(putFlags);
                    LayoutStrategy strategy = oldShape.getLayout().getStrategy();
                    return oldShape.defineProperty(cachedKey, value, propertyFlags, getLocationFactory(strategy, putFlags));
                }
            }

            Location location = property.getLocation();
            if (!location.isDeclared() && !location.canSet(value)) {
                // generalize
                assert oldShape == ACCESS.getShape(object);
                LayoutStrategy strategy = oldShape.getLayout().getStrategy();
                ShapeImpl newShape = strategy.definePropertyGeneralize(oldShape, property, value, getLocationFactory(strategy, putFlags), putFlags);
                assert newShape != oldShape;
                return newShape;
            } else if (location.isDeclared()) {
                // redefine declared
                return oldShape.defineProperty(cachedKey, value, property.getFlags());
            } else {
                // set existing
                assert location.canSet(value);
                return oldShape;
            }
        }

        private static Assumption getShapeValidAssumption(Shape oldShape, Shape newShape) {
            if (oldShape == newShape) {
                return AlwaysValidAssumption.INSTANCE;
            }
            return newShape.isValid() ? newShape.getValidAssumption() : NeverValidAssumption.INSTANCE;
        }
    }

    static <T extends CacheData<T>> T filterValid(T cache) {
        if (cache == null) {
            return null;
        }
        T filteredNext = filterValid(cache.next);
        if (cache.isValid()) {
            if (filteredNext == cache.next) {
                return cache;
            } else {
                return cache.withNext(filteredNext);
            }
        } else {
            return filteredNext;
        }
    }

    static RuntimeException shouldNotHappen(Exception e) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException(e);
    }

    abstract static class CacheData<T extends CacheData<T>> {
        final T next;

        CacheData(T next) {
            this.next = next;
        }

        protected boolean isValid() {
            return true;
        }

        protected abstract T withNext(T newNext);
    }

    static class PutCacheData extends CacheData<PutCacheData> {
        static final PutCacheData GENERIC = new PutCacheData(0, null, null, null, null);

        final long putFlags;
        final Shape newShape;
        final Property property;
        final Assumption newShapeValidAssumption;

        PutCacheData(long putFlags, Shape newShape, Property property, Assumption newShapeValidAssumption, PutCacheData next) {
            super(next);
            this.putFlags = putFlags;
            this.newShape = newShape;
            this.property = property;
            this.newShapeValidAssumption = newShapeValidAssumption;
        }

        @Override
        protected boolean isValid() {
            return newShapeValidAssumption == NeverValidAssumption.INSTANCE || newShapeValidAssumption.isValid();
        }

        protected void maybeUpdateShape(DynamicObject store) {
            if (newShapeValidAssumption == NeverValidAssumption.INSTANCE) {
                updateShapeImpl(store);
            }
        }

        @Override
        protected PutCacheData withNext(PutCacheData newNext) {
            return new PutCacheData(putFlags, newShape, property, newShapeValidAssumption, newNext);
        }
    }

    @GenerateUncached
    abstract static class SetFlagsNode extends Node {
        abstract boolean execute(DynamicObject object, Shape cachedShape, int flags);

        @Specialization(guards = {"flags == newFlags"}, limit = "3")
        static boolean doCached(DynamicObject object, Shape cachedShape, @SuppressWarnings("unused") int flags,
                        @Cached(value = "flags", allowUncached = true) @SuppressWarnings("unused") int newFlags,
                        @Cached(value = "shapeSetFlags(cachedShape, newFlags)", allowUncached = true) Shape newShape) {
            if (newShape != cachedShape) {
                ACCESS.setShape(object, newShape);
                return true;
            } else {
                return false;
            }
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(DynamicObject object, Shape cachedShape, int flags) {
            Shape newShape = shapeSetFlags(cachedShape, flags);
            if (newShape != cachedShape) {
                ACCESS.setShape(object, newShape);
                return true;
            } else {
                return false;
            }
        }

        static Shape shapeSetFlags(Shape shape, int newFlags) {
            return ((ShapeImpl) shape).setFlags(newFlags);
        }
    }

    @GenerateUncached
    abstract static class SetDynamicTypeNode extends Node {
        abstract boolean execute(DynamicObject object, Shape cachedShape, Object objectType);

        @Specialization(guards = {"objectType == newObjectType"}, limit = "3")
        static boolean doCached(DynamicObject object, Shape cachedShape, @SuppressWarnings("unused") Object objectType,
                        @Cached(value = "objectType", allowUncached = true) @SuppressWarnings("unused") Object newObjectType,
                        @Cached(value = "shapeSetDynamicType(cachedShape, newObjectType)", allowUncached = true) Shape newShape) {
            if (newShape != cachedShape) {
                ACCESS.setShape(object, newShape);
                return true;
            } else {
                return false;
            }
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(DynamicObject object, Shape cachedShape, Object objectType) {
            Shape newShape = shapeSetDynamicType(cachedShape, objectType);
            if (newShape != cachedShape) {
                ACCESS.setShape(object, newShape);
                return true;
            } else {
                return false;
            }
        }

        static Shape shapeSetDynamicType(Shape shape, Object newType) {
            return shape.changeType((ObjectType) newType);
        }
    }

    @GenerateUncached
    abstract static class MakeSharedNode extends Node {
        abstract void execute(DynamicObject object, Shape cachedShape);

        @Specialization
        static void doCached(DynamicObject object, Shape cachedShape,
                        @Cached(value = "cachedShape.makeSharedShape()", allowUncached = true) Shape newShape) {
            assert newShape != cachedShape;
            ACCESS.growAndSetShape(object, cachedShape, newShape);
        }
    }

    @GenerateUncached
    abstract static class ResetShapeNode extends Node {
        abstract boolean execute(DynamicObject object, Shape cachedShape, Shape newShape);

        @Specialization(guards = "otherShape == cachedOtherShape")
        static boolean doCached(DynamicObject object, Shape cachedShape, @SuppressWarnings("unused") Shape otherShape,
                        @Cached(value = "verifyResetShape(cachedShape, otherShape)", allowUncached = true) Shape cachedOtherShape) {
            if (cachedShape == cachedOtherShape) {
                return false;
            }
            ACCESS.resizeAndSetShape(object, cachedShape, cachedOtherShape);
            return true;
        }

        static Shape verifyResetShape(Shape currentShape, Shape otherShape) {
            if (((ShapeImpl) otherShape).hasInstanceProperties()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("Shape must not contain any instance properties.");
            }
            if (currentShape != otherShape) {
                ACCESS.invalidateAllPropertyAssumptions(currentShape);
            }
            return otherShape;
        }
    }
}
