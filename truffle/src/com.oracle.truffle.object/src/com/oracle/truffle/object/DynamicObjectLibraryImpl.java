/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

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
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

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
            return keyEqualsBoundary(cachedKey, key);
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
    static boolean getBooleanOrDefault(DynamicObject object, Object key, Object defaultValue,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) throws UnexpectedResultException {
        return keyCache.getBooleanOrDefault(object, cachedShape, key, defaultValue);
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
    static void putBoolean(DynamicObject object, Object key, boolean value,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        keyCache.putBoolean(object, cachedShape, key, value, Flags.DEFAULT);
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
        shiftPropertyValuesAfterRemove(obj, oldShape, newShape);
        ACCESS.trimToSize(obj, newShape);
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
    public static void makeShared(DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Cached MakeSharedNode setCache) {
        setCache.execute(object, cachedShape);
    }

    @ExportMessage
    public static boolean updateShape(@SuppressWarnings("unused") DynamicObject object) {
        return false;
    }

    @ExportMessage
    public static boolean resetShape(DynamicObject object, Shape otherShape) {
        if (((ShapeImpl) otherShape).hasInstanceProperties()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Shape must not contain any instance properties.");
        }
        Shape currentShape = object.getShape();
        if (currentShape == otherShape) {
            return false;
        }
        ACCESS.invalidateAllPropertyAssumptions(currentShape);
        ACCESS.resizeAndSetShape(object, currentShape, otherShape);
        return true;
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

    private static CoreLocation getLocation(Property existing) {
        return (CoreLocation) existing.getLocation();
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
            } catch (IncompatibleLocationException e) {
                throw shouldNotHappen(e);
            }
            return true;
        } else {
            return putUncachedSlow(object, key, value, putFlags);
        }
    }

    private static boolean putUncachedSlow(DynamicObject object, Object key, Object value, long putFlags) {
        CompilerAsserts.neverPartOfCompilation();
        ShapeImpl oldShape = (ShapeImpl) ACCESS.getShape(object);
        Property existingProperty = oldShape.getProperty(key);
        Shape newShape;
        Property property;
        if (existingProperty == null) {
            if (Flags.isSetExisting(putFlags)) {
                return false;
            } else {
                LocationFactory locationFactory = getLocationFactory(putFlags);
                newShape = oldShape.getLayout().getStrategy().defineProperty(oldShape, key, value, Flags.getPropertyFlags(putFlags), locationFactory, existingProperty, putFlags);
                property = newShape.getProperty(key);
            }
        } else if (Flags.isUpdateFlags(putFlags) && Flags.getPropertyFlags(putFlags) != existingProperty.getFlags()) {
            LocationFactory locationFactory = getLocationFactory(putFlags);
            newShape = oldShape.getLayout().getStrategy().defineProperty(oldShape, key, value, Flags.getPropertyFlags(putFlags), locationFactory, existingProperty, putFlags);
            property = newShape.getProperty(key);
        } else {
            if (existingProperty.getLocation().canSet(value)) {
                newShape = oldShape;
                property = existingProperty;
            } else {
                LocationFactory locationFactory = getLocationFactory(putFlags);
                newShape = oldShape.getLayout().getStrategy().defineProperty(oldShape, key, value, existingProperty.getFlags(), locationFactory, existingProperty, putFlags);
                property = newShape.getProperty(key);
            }
        }

        assert ACCESS.getShape(object) == oldShape;
        if (oldShape != newShape) {
            ACCESS.growAndSetShape(object, oldShape, newShape);
            try {
                ((CoreLocation) property.getLocation()).setInternal(object, value, false);
            } catch (IncompatibleLocationException e) {
                throw shouldNotHappen(e);
            }
        } else {
            try {
                ((CoreLocation) property.getLocation()).set(object, value, false);
            } catch (IncompatibleLocationException e) {
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

    static LocationFactory getLocationFactory(long putFlags) {
        if (Flags.isConstant(putFlags)) {
            return CONSTANT_LOCATION_FACTORY;
        } else if (Flags.isDeclaration(putFlags)) {
            return DECLARED_LOCATION_FACTORY;
        } else {
            return getDefaultLocationFactory(putFlags);
        }
    }

    private static LocationFactory getDefaultLocationFactory(long putFlags) {
        return new LocationFactory() {
            public Location createLocation(Shape shape, Object value) {
                return ((CoreAllocator) ((ShapeImpl) shape).allocator()).locationForValue(value, true, value != null, putFlags);
            }
        };
    }

    private static void shiftPropertyValuesAfterRemove(DynamicObject object, ShapeImpl oldShape, ShapeImpl newShape) {
        List<Move> moves = new ArrayList<>();
        for (ListIterator<Property> iterator = newShape.getPropertyListInternal(false).listIterator(); iterator.hasNext();) {
            Property to = iterator.next();
            Property from = oldShape.getProperty(to.getKey());
            CoreLocation fromLoc = getLocation(from);
            CoreLocation toLoc = getLocation(to);
            if (CoreLocations.isSameLocation(toLoc, fromLoc)) {
                continue;
            }
            assert !toLoc.isValue();
            Move move = new Move(fromLoc, toLoc);
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
        private final CoreLocation fromLoc;
        private final CoreLocation toLoc;

        Move(CoreLocation fromLoc, CoreLocation toLoc) {
            this.fromLoc = fromLoc;
            this.toLoc = toLoc;
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
            int order = Integer.compare(CoreLocations.getLocationOrdinal(fromLoc), CoreLocations.getLocationOrdinal(other.fromLoc));
            assert order == Integer.compare(CoreLocations.getLocationOrdinal(toLoc), CoreLocations.getLocationOrdinal(other.toLoc));
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
                return CoreLocation.expectInteger(defaultValue);
            }
        }

        @TruffleBoundary
        @Override
        public long getLongOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            Property existing = ACCESS.getShape(object).getProperty(key);
            if (existing != null) {
                return getLocation(existing).getLong(object, false);
            } else {
                return CoreLocation.expectLong(defaultValue);
            }
        }

        @TruffleBoundary
        @Override
        public double getDoubleOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            Property existing = ACCESS.getShape(object).getProperty(key);
            if (existing != null) {
                return getLocation(existing).getDouble(object, false);
            } else {
                return CoreLocation.expectDouble(defaultValue);
            }
        }

        @TruffleBoundary
        @Override
        public boolean getBooleanOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
            Property existing = ACCESS.getShape(object).getProperty(key);
            if (existing != null) {
                return getLocation(existing).getBoolean(object, false);
            } else {
                return CoreLocation.expectBoolean(defaultValue);
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

        protected final boolean assertCachedKeyAndShape(DynamicObject object, Shape cachedShape, Object key) {
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
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return ((CoreLocation) cachedProperty.getLocation()).get(object, guard(object, cachedShape));
            }

            @Override
            public int getIntOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return ((CoreLocation) cachedProperty.getLocation()).getInt(object, guard(object, cachedShape));
            }

            @Override
            public long getLongOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return ((CoreLocation) cachedProperty.getLocation()).getLong(object, guard(object, cachedShape));
            }

            @Override
            public double getDoubleOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return ((CoreLocation) cachedProperty.getLocation()).getDouble(object, guard(object, cachedShape));
            }

            @Override
            public boolean getBooleanOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return ((CoreLocation) cachedProperty.getLocation()).getBoolean(object, guard(object, cachedShape));
            }

            @Override
            public boolean put(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean putInt(DynamicObject object, Shape cachedShape, Object key, int value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putIntImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean putLong(DynamicObject object, Shape cachedShape, Object key, long value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putLongImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean putDouble(DynamicObject object, Shape cachedShape, Object key, double value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putDoubleImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean putBoolean(DynamicObject object, Shape cachedShape, Object key, boolean value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putBooleanImpl(object, cachedShape, key, value, putFlags, cachedProperty);
            }

            @Override
            public boolean containsKey(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return true;
            }

            @Override
            public Property getProperty(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return cachedProperty;
            }

            @Override
            public boolean setPropertyFlags(DynamicObject object, Shape cachedShape, Object key, int propertyFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
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
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return defaultValue;
            }

            @Override
            public boolean put(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean putInt(DynamicObject object, Shape cachedShape, Object key, int value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putIntImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean putLong(DynamicObject object, Shape cachedShape, Object key, long value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putLongImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean putDouble(DynamicObject object, Shape cachedShape, Object key, double value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putDoubleImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean putBoolean(DynamicObject object, Shape cachedShape, Object key, boolean value, long putFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return putBooleanImpl(object, cachedShape, key, value, putFlags, null);
            }

            @Override
            public boolean containsKey(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return false;
            }

            @Override
            public Property getProperty(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return null;
            }

            @Override
            public int getIntOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                return CoreLocation.expectInteger(defaultValue);
            }

            @Override
            public long getLongOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                return CoreLocation.expectLong(defaultValue);
            }

            @Override
            public double getDoubleOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                return CoreLocation.expectDouble(defaultValue);
            }

            @Override
            public boolean getBooleanOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException {
                return CoreLocation.expectBoolean(defaultValue);
            }

            @Override
            public boolean setPropertyFlags(DynamicObject object, Shape cachedShape, Object key, int propertyFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShape(object, cachedShape, key);
                return false;
            }
        }

        @ExplodeLoop
        protected boolean putImpl(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags, Property oldProperty) {
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
                    } else if (newProperty.getLocation().canSet(value)) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.growAndSetShape(object, oldShape, newShape);
                        }
                        try {
                            ((CoreLocation) newProperty.getLocation()).set(object, value, object.getShape() == oldShape);
                        } catch (IncompatibleLocationException e) {
                            throw shouldNotHappen(e);
                        }
                        return true;
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
            if (start == PutCacheData.GENERIC) {
                return putUncached(object, key, value, putFlags);
            }
            for (PutCacheData c = start; c != null; c = c.next) {
                if (c.putFlags == putFlags) {
                    Property newProperty = c.property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else if (newProperty.getLocation() instanceof CoreLocations.IntLocation) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.growAndSetShape(object, oldShape, newShape);
                        }
                        ((CoreLocations.IntLocation) newProperty.getLocation()).setInt(object, value, object.getShape() == oldShape);
                        return true;
                    } else if (newProperty.getLocation().canSet(value)) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.growAndSetShape(object, oldShape, newShape);
                        }
                        try {
                            ((CoreLocation) newProperty.getLocation()).set(object, value, object.getShape() == oldShape);
                        } catch (IncompatibleLocationException e) {
                            throw shouldNotHappen(e);
                        }
                        return true;
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
                    } else if (newProperty.getLocation() instanceof CoreLocations.LongLocation) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.growAndSetShape(object, oldShape, newShape);
                        }
                        ((CoreLocations.LongLocation) newProperty.getLocation()).setLong(object, value, object.getShape() == oldShape);
                        return true;
                    } else if (newProperty.getLocation().canSet(value)) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.growAndSetShape(object, oldShape, newShape);
                        }
                        try {
                            ((CoreLocation) newProperty.getLocation()).set(object, value, object.getShape() == oldShape);
                        } catch (IncompatibleLocationException e) {
                            throw shouldNotHappen(e);
                        }
                        return true;
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
                    } else if (newProperty.getLocation() instanceof CoreLocations.DoubleLocation) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.growAndSetShape(object, oldShape, newShape);
                        }
                        ((CoreLocations.DoubleLocation) newProperty.getLocation()).setDouble(object, value, object.getShape() == oldShape);
                        return true;
                    } else if (newProperty.getLocation().canSet(value)) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.growAndSetShape(object, oldShape, newShape);
                        }
                        try {
                            ((CoreLocation) newProperty.getLocation()).set(object, value, object.getShape() == oldShape);
                        } catch (IncompatibleLocationException e) {
                            throw shouldNotHappen(e);
                        }
                        return true;
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
                    } else if (newProperty.getLocation() instanceof CoreLocations.BooleanLocation) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.growAndSetShape(object, oldShape, newShape);
                        }
                        ((CoreLocations.BooleanLocation) newProperty.getLocation()).setBoolean(object, value, object.getShape() == oldShape);
                        return true;
                    } else if (newProperty.getLocation().canSet(value)) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.growAndSetShape(object, oldShape, newShape);
                        }
                        try {
                            ((CoreLocation) newProperty.getLocation()).set(object, value, object.getShape() == oldShape);
                        } catch (IncompatibleLocationException e) {
                            throw shouldNotHappen(e);
                        }
                        return true;
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            KeyCacheNode impl = insertIntoPutCache(object, cachedShape, value, putFlags, oldProperty);
            return impl.putBoolean(object, cachedShape, key, value, putFlags);
        }

        protected KeyCacheNode insertIntoPutCache(DynamicObject object, Shape cachedShape, Object value, long putFlags, Property property) {
            CompilerAsserts.neverPartOfCompilation();
            Lock lock = getLock();
            lock.lock();
            try {
                ShapeImpl oldShape = (ShapeImpl) cachedShape;
                ShapeImpl newShape = getNewShape(object, value, putFlags, property, oldShape);

                Property newProperty;
                if (newShape == oldShape) {
                    newProperty = property;
                } else {
                    newProperty = newShape.getProperty(cachedKey);
                    assert newProperty.getLocation().canSet(value);
                }

                this.cache = new PutCacheData(putFlags, newShape, newProperty, this.cache);
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
                    return oldShape.defineProperty(cachedKey, value, propertyFlags, getLocationFactory(putFlags));
                }
            }

            if (Flags.isUpdateFlags(putFlags)) {
                if (Flags.getPropertyFlags(putFlags) != property.getFlags()) {
                    int propertyFlags = Flags.getPropertyFlags(putFlags);
                    return oldShape.defineProperty(cachedKey, value, propertyFlags, getLocationFactory(putFlags));
                }
            }

            Location location = property.getLocation();
            if (!location.isDeclared() && !location.canSet(value)) {
                // generalize
                assert oldShape == ACCESS.getShape(object);
                ShapeImpl newShape = oldShape.getLayout().getStrategy().definePropertyGeneralize(oldShape, property, value, getLocationFactory(putFlags), putFlags);
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
    }

    static class PutCacheData extends CacheData<PutCacheData> {
        static final PutCacheData GENERIC = new PutCacheData(0, null, null, null);

        final long putFlags;
        final Shape newShape;
        final Property property;

        PutCacheData(long putFlags, Shape newShape, Property property, PutCacheData next) {
            super(next);
            this.putFlags = putFlags;
            this.newShape = newShape;
            this.property = property;
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
            ACCESS.resizeAndSetShape(object, cachedShape, cachedOtherShape);
            return cachedShape != cachedOtherShape;
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
