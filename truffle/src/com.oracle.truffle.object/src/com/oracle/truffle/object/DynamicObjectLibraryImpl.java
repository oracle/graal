/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

/**
 * The implementation of {@link DynamicObjectLibrary}.
 */
@SuppressWarnings("deprecation")
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
    static ShapeImpl changePropertyFlags(ShapeImpl shape, PropertyImpl cachedProperty, int propertyFlags) {
        return shape.replaceProperty(cachedProperty, cachedProperty.copyWithFlags(propertyFlags));
    }

    @ExportMessage
    public static boolean removeKey(DynamicObject object, Object key,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Shared("keyCache") @Cached("create(object.getShape(), key)") KeyCacheNode keyCache) {
        return keyCache.removeKey(object, cachedShape, key);
    }

    @ExportMessage
    public static Object getDynamicType(@SuppressWarnings("unused") DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return cachedShape.getDynamicType();
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static boolean setDynamicType(DynamicObject object, Object objectType,
                    @Bind("$node") Node node,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Cached SetDynamicTypeNode setCache) {
        return setCache.execute(node, object, cachedShape, objectType);
    }

    @ExportMessage
    public static int getShapeFlags(@SuppressWarnings("unused") DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return cachedShape.getFlags();
    }

    @ExportMessage
    public static boolean setShapeFlags(DynamicObject object, @SuppressWarnings("unused") int flags,
                    @Bind("$node") Node node,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Cached SetFlagsNode setCache) {
        return setCache.execute(node, object, cachedShape, flags);
    }

    @ExportMessage
    public static boolean isShared(@SuppressWarnings("unused") DynamicObject object,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape) {
        return cachedShape.isShared();
    }

    @ExportMessage
    public static void markShared(DynamicObject object,
                    @Bind("$node") Node node,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Cached MakeSharedNode setCache) {
        setCache.execute(node, object, cachedShape);
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
        return ((ShapeImpl) object.getShape()).getLayoutStrategy().updateShape(object);
    }

    @ExportMessage
    public static boolean resetShape(DynamicObject object, Shape otherShape,
                    @Bind("$node") Node node,
                    @Shared("cachedShape") @Cached(value = "object.getShape()", allowUncached = true) Shape cachedShape,
                    @Cached ResetShapeNode setCache) {
        return setCache.execute(node, object, cachedShape, otherShape);
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

    static LocationImpl getLocation(Property existing) {
        return (LocationImpl) existing.getLocation();
    }

    @TruffleBoundary
    protected static boolean putUncached(DynamicObject object, Object key, Object value, long putFlags) {
        Shape s = ACCESS.getShape(object);
        Property existingProperty = s.getProperty(key);
        if (existingProperty == null && Flags.isSetExisting(putFlags)) {
            return false;
        }
        if (existingProperty != null && !Flags.isUpdateFlags(putFlags) && existingProperty.getLocation().canStore(value)) {
            getLocation(existingProperty).setSafe(object, value, false, false);
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
                    LayoutStrategy strategy = oldShape.getLayoutStrategy();
                    newShape = strategy.defineProperty(oldShape, key, value, Flags.getPropertyFlags(putFlags), null, existingProperty, putFlags);
                    property = newShape.getProperty(key);
                }
            } else if (Flags.isUpdateFlags(putFlags) && Flags.getPropertyFlags(putFlags) != existingProperty.getFlags()) {
                LayoutStrategy strategy = oldShape.getLayoutStrategy();
                newShape = strategy.defineProperty(oldShape, key, value, Flags.getPropertyFlags(putFlags), null, existingProperty, putFlags);
                property = newShape.getProperty(key);
            } else {
                if (existingProperty.getLocation().canStore(value)) {
                    newShape = oldShape;
                    property = existingProperty;
                } else {
                    LayoutStrategy strategy = oldShape.getLayoutStrategy();
                    newShape = strategy.defineProperty(oldShape, key, value, existingProperty.getFlags(), null, existingProperty, putFlags);
                    property = newShape.getProperty(key);
                }
            }
        } while (updateShapeImpl(object));

        assert ACCESS.getShape(object) == oldShape;
        LocationImpl location = getLocation(property);
        if (oldShape != newShape) {
            ACCESS.grow(object, oldShape, newShape);
            location.setSafe(object, value, false, true);
            ACCESS.setShapeWithStoreFence(object, newShape);
            updateShapeImpl(object);
        } else {
            location.setSafe(object, value, false, false);
        }
        return true;
    }

    static RemovePlan prepareRemove(ShapeImpl shapeBefore, ShapeImpl shapeAfter) {
        assert !shapeBefore.isShared();
        LayoutStrategy strategy = shapeBefore.getLayoutStrategy();
        List<Move> moves = new ArrayList<>();
        boolean canMoveInPlace = shapeAfter.getObjectArrayCapacity() <= shapeBefore.getObjectArrayCapacity() &&
                        shapeAfter.getPrimitiveArrayCapacity() <= shapeBefore.getPrimitiveArrayCapacity();
        for (ListIterator<Property> iterator = shapeAfter.getPropertyListInternal(false).listIterator(); iterator.hasNext();) {
            Property to = iterator.next();
            Property from = shapeBefore.getProperty(to.getKey());
            LocationImpl fromLoc = getLocation(from);
            LocationImpl toLoc = getLocation(to);
            if (LocationImpl.isSameLocation(toLoc, fromLoc)) {
                continue;
            }
            assert !toLoc.isValue();
            int fromOrd = strategy.getLocationOrdinal(fromLoc);
            int toOrd = strategy.getLocationOrdinal(toLoc);
            Move move = new Move(fromLoc, toLoc, fromOrd, toOrd);
            canMoveInPlace = canMoveInPlace && fromOrd > toOrd;
            moves.add(move);
        }
        if (canMoveInPlace) {
            if (!isSorted(moves)) {
                Collections.sort(moves);
            }
        }
        return new RemovePlan(moves.toArray(new Move[0]), canMoveInPlace, shapeBefore, shapeAfter);
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

    private static final class Move implements Comparable<Move> {
        private final LocationImpl fromLoc;
        private final LocationImpl toLoc;
        private final int fromOrd;
        private final int toOrd;

        Move(LocationImpl fromLoc, LocationImpl toLoc, int fromOrd, int toOrd) {
            this.fromLoc = fromLoc;
            this.toLoc = toLoc;
            this.fromOrd = fromOrd;
            this.toOrd = toOrd;
        }

        void perform(DynamicObject obj) {
            performSet(obj, performGet(obj));
        }

        Object performGet(DynamicObject obj) {
            return fromLoc.get(obj, false);
        }

        void performSet(DynamicObject obj, Object value) {
            toLoc.setSafe(obj, value, false, true);
        }

        void clear(DynamicObject obj) {
            // clear location to avoid memory leak
            fromLoc.clear(obj);
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return fromLoc + " => " + toLoc;
        }

        @Override
        public int compareTo(Move other) {
            int order = Integer.compare(fromOrd, other.fromOrd);
            assert order == Integer.compare(toOrd, other.toOrd);
            return -order;
        }
    }

    private static final class RemovePlan {
        private static final int MAX_UNROLL = 32;

        @CompilationFinal(dimensions = 1) private final Move[] moves;
        private final boolean canMoveInPlace;
        private final Shape shapeBefore;
        private final Shape shapeAfter;

        RemovePlan(Move[] moves, boolean canMoveInPlace, Shape shapeBefore, Shape shapeAfter) {
            this.moves = moves;
            this.canMoveInPlace = canMoveInPlace;
            this.shapeBefore = shapeBefore;
            this.shapeAfter = shapeAfter;
        }

        void execute(DynamicObject object) {
            CompilerAsserts.partialEvaluationConstant(moves.length);
            if (CompilerDirectives.inCompiledCode() && moves.length <= MAX_UNROLL) {
                perform(object);
            } else {
                performBoundary(object);
            }
        }

        @ExplodeLoop
        void perform(DynamicObject object) {
            CompilerAsserts.partialEvaluationConstant(moves.length);
            if (canMoveInPlace) {
                // perform the moves in inverse order
                for (int i = moves.length - 1; i >= 0; i--) {
                    moves[i].perform(object);
                    if (i == 0) {
                        moves[i].clear(object);
                    }
                }
                ACCESS.trimToSize(object, shapeBefore, shapeAfter);
                ACCESS.setShape(object, shapeAfter);
            } else {
                // we cannot perform the moves in place, so stash away the values
                Object[] tempValues = new Object[moves.length];
                for (int i = moves.length - 1; i >= 0; i--) {
                    tempValues[i] = moves[i].performGet(object);
                    moves[i].clear(object);
                }
                ACCESS.resize(object, shapeBefore, shapeAfter);
                for (int i = moves.length - 1; i >= 0; i--) {
                    moves[i].performSet(object, tempValues[i]);
                }
                ACCESS.setShape(object, shapeAfter);
            }
        }

        @TruffleBoundary
        void performBoundary(DynamicObject object) {
            perform(object);
        }
    }

    abstract static class KeyCacheNode extends Node {
        public abstract Object getOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue);

        public abstract int getIntOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract long getLongOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract double getDoubleOrDefault(DynamicObject object, Shape cachedShape, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract boolean put(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags);

        public abstract boolean containsKey(DynamicObject object, Shape cachedShape, Object key);

        public abstract Property getProperty(DynamicObject object, Shape cachedShape, Object key);

        public abstract boolean setPropertyFlags(DynamicObject object, Shape cachedShape, Object key, int propertyFlags);

        public abstract boolean removeKey(DynamicObject object, Shape cachedShape, Object key);

        public boolean putInt(DynamicObject object, Shape cachedShape, Object key, int value, long putFlags) {
            return put(object, cachedShape, key, value, putFlags);
        }

        public boolean putLong(DynamicObject object, Shape cachedShape, Object key, long value, long putFlags) {
            return put(object, cachedShape, key, value, putFlags);
        }

        public boolean putDouble(DynamicObject object, Shape cachedShape, Object key, double value, long putFlags) {
            return put(object, cachedShape, key, value, putFlags);
        }

        boolean isIdentity() {
            return false;
        }

        @NeverDefault
        static KeyCacheNode create(Shape cachedShape, Object key) {
            if (key == null) {
                return getUncached();
            }
            return AnyKey.create(key, cachedShape);
        }

        @NeverDefault
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

    static final class Generic extends KeyCacheEntry {
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
            updateShapeImpl(object);
            ShapeImpl oldShape = (ShapeImpl) ACCESS.getShape(object);
            Property existingProperty = oldShape.getProperty(key);
            if (existingProperty == null) {
                return false;
            }
            if (existingProperty.getFlags() != propertyFlags) {
                Shape newShape = changePropertyFlags(oldShape, (PropertyImpl) existingProperty, propertyFlags);
                if (newShape != oldShape) {
                    ACCESS.setShape(object, newShape);
                    updateShapeImpl(object);
                }
            }
            return true;
        }

        @TruffleBoundary
        @Override
        public boolean removeKey(DynamicObject obj, Shape cachedShape, Object key) {
            ShapeImpl oldShape = (ShapeImpl) cachedShape;
            Property property = oldShape.getProperty(key);
            if (property == null) {
                return false;
            }

            Map<Object, Object> archive = null;
            assert (archive = ACCESS.archive(obj)) != null;

            ShapeImpl newShape = oldShape.removeProperty(property);
            assert oldShape != newShape;
            assert ACCESS.getShape(obj) == oldShape;

            if (!oldShape.isShared()) {
                RemovePlan plan = prepareRemove(oldShape, newShape);
                plan.execute(obj);
            } else {
                ACCESS.setShape(obj, newShape);
            }

            assert ACCESS.verifyValues(obj, archive);
            return true;
        }

    }

    /**
     * Polymorphic inline cache for a limited number of distinct property keys.
     *
     * The generic case is used if the number of property keys accessed overflows the limit of the
     * polymorphic inline cache.
     */
    static final class AnyKey extends KeyCacheNode {

        @Child private KeyCacheEntry keyCache;

        AnyKey(KeyCacheEntry keyCache) {
            this.keyCache = keyCache;
        }

        public static KeyCacheNode create() {
            return new AnyKey(null);
        }

        public static KeyCacheNode create(Object key, Shape cachedShape) {
            return new AnyKey(SpecificKey.create(key, cachedShape, null, true));
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

        @ExplodeLoop
        @Override
        public boolean removeKey(DynamicObject object, Shape cachedShape, Object key) {
            KeyCacheEntry start = keyCache;
            if (start != KeyCacheNode.getUncached()) {
                for (KeyCacheEntry c = start; c != null; c = c.next) {
                    if (c.acceptsKey(key)) {
                        return c.removeKey(object, cachedShape, key);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                KeyCacheNode impl = insertIntoKeyCache(key, cachedShape);
                if (impl != null) {
                    return impl.removeKey(object, cachedShape, key);
                }
            }
            return Generic.instance().removeKey(object, cachedShape, key);
        }

        private KeyCacheNode insertIntoKeyCache(Object key, Shape cachedShape) {
            CompilerAsserts.neverPartOfCompilation();
            Lock lock = getLock();
            lock.lock();
            try {
                KeyCacheEntry tail = this.keyCache;
                int cachedCount = 0;
                boolean generic = false;
                boolean useIdentity = true;

                for (KeyCacheEntry c = tail; c != null; c = c.next) {
                    if (c == KeyCacheNode.getUncached()) {
                        generic = true;
                        break;
                    } else {
                        cachedCount++;
                        if (c.acceptsKey(key)) {
                            return c;
                        }
                        if (!c.isIdentity()) {
                            useIdentity = false;
                        }
                    }
                }

                if (cachedCount > 1 && useIdentity) {
                    // if we have duplicate keys in the cache due to identity comparison,
                    // clear the cache and compare keys with equals() from now on.
                    if (hasDuplicateCacheKeys(tail, key)) {
                        tail = null;
                        cachedCount = 0;
                        useIdentity = false;
                    }
                }

                if (cachedCount >= KEY_LIMIT) {
                    generic = true;
                    this.keyCache = KeyCacheNode.getUncached();
                }
                if (generic) {
                    return null;
                }

                SpecificKey newEntry = SpecificKey.create(key, cachedShape, tail, useIdentity);
                insert(newEntry);
                this.keyCache = newEntry;
                return this;
            } finally {
                lock.unlock();
            }
        }

        private static boolean hasDuplicateCacheKeys(KeyCacheEntry tail, Object key) {
            EconomicSet<Object> keySet = EconomicSet.create();
            for (KeyCacheEntry c = tail; c != null; c = c.next) {
                if (c instanceof SpecificKey) {
                    SpecificKey cacheEntry = (SpecificKey) c;
                    if (!keySet.add(cacheEntry.cachedKey)) {
                        return true;
                    }
                }
            }
            return !keySet.add(key);
        }
    }

    abstract static class SpecificKey extends KeyCacheEntry {
        final Object cachedKey;

        @CompilationFinal MutateCacheData cache;

        SpecificKey(Object key, KeyCacheEntry next) {
            super(next);
            this.cachedKey = key;
        }

        static SpecificKey create(Object key, Shape shape, KeyCacheEntry next, boolean useIdentity) {
            if (key != null) {
                Property property = shape.getProperty(key);
                if (property != null) {
                    return useIdentity ? new SpecificKey.ExistingKeyIdentity(key, property, next) : new SpecificKey.ExistingKey(key, property, next);
                }
            }
            return useIdentity ? new SpecificKey.MissingKeyIdentity(key, next) : new SpecificKey.MissingKey(key, next);
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
                return setPropertyFlagsImpl(object, cachedShape, key, propertyFlags, cachedProperty);
            }

            @Override
            public boolean removeKey(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return removeKeyImpl(object, cachedShape, key, cachedProperty);
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
            public boolean setPropertyFlags(DynamicObject object, Shape cachedShape, Object key, int propertyFlags) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return false;
            }

            @Override
            public boolean removeKey(DynamicObject object, Shape cachedShape, Object key) {
                CompilerAsserts.partialEvaluationConstant(cachedShape);
                assert assertCachedKeyAndShapeForWrite(object, cachedShape, key);
                return false;
            }
        }

        static final class ExistingKeyIdentity extends ExistingKey {
            ExistingKeyIdentity(Object key, Property property, KeyCacheEntry next) {
                super(key, property, next);
            }

            @Override
            public boolean acceptsKey(Object key) {
                return cachedKey == key;
            }

            @Override
            boolean isIdentity() {
                return true;
            }
        }

        static final class MissingKeyIdentity extends MissingKey {
            MissingKeyIdentity(Object key, KeyCacheEntry next) {
                super(key, next);
            }

            @Override
            public boolean acceptsKey(Object key) {
                return cachedKey == key;
            }

            @Override
            boolean isIdentity() {
                return true;
            }
        }

        @ExplodeLoop
        protected boolean putImpl(DynamicObject object, Shape cachedShape, Object key, Object value, long putFlags, Property oldProperty) {
            Shape oldShape = cachedShape;
            MutateCacheData start = cache;
            if (start == MutateCacheData.GENERIC || !cachedShape.isValid()) {
                return putUncached(object, key, value, putFlags);
            }
            for (MutateCacheData c = start; c != null; c = c.next) {
                if (!c.isValid()) {
                    break;
                } else if (c instanceof PutCacheData && ((PutCacheData) c).putFlags == putFlags) {
                    Property newProperty = ((PutCacheData) c).property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else {
                        LocationImpl location = getLocation(newProperty);
                        boolean guardCondition = object.getShape() == oldShape;
                        if (location.canStore(value)) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.grow(object, oldShape, newShape);
                                location.setSafe(object, value, guardCondition, true);
                                ACCESS.setShapeWithStoreFence(object, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            } else {
                                location.setSafe(object, value, guardCondition, false);
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
            MutateCacheData start = cache;
            if (start == MutateCacheData.GENERIC || !cachedShape.isValid()) {
                return putUncached(object, key, value, putFlags);
            }
            for (MutateCacheData c = start; c != null; c = c.next) {
                if (!c.isValid()) {
                    break;
                } else if (c instanceof PutCacheData && ((PutCacheData) c).putFlags == putFlags) {
                    Property newProperty = ((PutCacheData) c).property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else {
                        LocationImpl location = getLocation(newProperty);
                        Shape newShape = c.newShape;
                        boolean guardCondition = object.getShape() == oldShape;
                        if (location.isIntLocation()) {
                            if (newShape != oldShape) {
                                ACCESS.grow(object, oldShape, newShape);
                                location.setIntSafe(object, value, guardCondition, true);
                                ACCESS.setShapeWithStoreFence(object, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            } else {
                                location.setIntSafe(object, value, guardCondition, false);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (location.isImplicitCastIntToLong()) {
                            if (newShape != oldShape) {
                                ACCESS.grow(object, oldShape, newShape);
                                location.setLongSafe(object, value, guardCondition, true);
                                ACCESS.setShapeWithStoreFence(object, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            } else {
                                location.setLongSafe(object, value, guardCondition, false);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (location.isImplicitCastIntToDouble()) {
                            if (newShape != oldShape) {
                                ACCESS.grow(object, oldShape, newShape);
                                location.setDoubleSafe(object, value, guardCondition, true);
                                ACCESS.setShapeWithStoreFence(object, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            } else {
                                location.setDoubleSafe(object, value, guardCondition, false);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (location.canStore(value)) {
                            if (newShape != oldShape) {
                                ACCESS.grow(object, oldShape, newShape);
                                location.setSafe(object, value, guardCondition, true);
                                ACCESS.setShapeWithStoreFence(object, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            } else {
                                location.setSafe(object, value, guardCondition, false);
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
            MutateCacheData start = cache;
            if (start == MutateCacheData.GENERIC) {
                return putUncached(object, key, value, putFlags);
            }
            for (MutateCacheData c = start; c != null; c = c.next) {
                if (c instanceof PutCacheData && ((PutCacheData) c).putFlags == putFlags) {
                    Property newProperty = ((PutCacheData) c).property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else {
                        LocationImpl location = getLocation(newProperty);
                        boolean guardCondition = object.getShape() == oldShape;
                        if (location.isLongLocation()) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.grow(object, oldShape, newShape);
                                location.setLongSafe(object, value, guardCondition, true);
                                ACCESS.setShapeWithStoreFence(object, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            } else {
                                location.setLongSafe(object, value, guardCondition, false);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (location.canStore(value)) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.grow(object, oldShape, newShape);
                                location.setSafe(object, value, guardCondition, true);
                                ACCESS.setShapeWithStoreFence(object, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            } else {
                                location.setSafe(object, value, guardCondition, false);
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
            MutateCacheData start = cache;
            if (start == MutateCacheData.GENERIC) {
                return putUncached(object, key, value, putFlags);
            }
            for (MutateCacheData c = start; c != null; c = c.next) {
                if (c instanceof PutCacheData && ((PutCacheData) c).putFlags == putFlags) {
                    Property newProperty = ((PutCacheData) c).property;
                    if (newProperty == null) {
                        assert Flags.isSetExisting(putFlags);
                        return false;
                    } else {
                        LocationImpl location = getLocation(newProperty);
                        boolean guardCondition = object.getShape() == oldShape;
                        if (location.isDoubleLocation()) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.grow(object, oldShape, newShape);
                                location.setDoubleSafe(object, value, guardCondition, true);
                                ACCESS.setShapeWithStoreFence(object, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            } else {
                                location.setDoubleSafe(object, value, guardCondition, false);
                            }
                            c.maybeUpdateShape(object);
                            return true;
                        } else if (newProperty.getLocation().canStore(value)) {
                            Shape newShape = c.newShape;
                            if (newShape != oldShape) {
                                ACCESS.grow(object, oldShape, newShape);
                                location.setSafe(object, value, guardCondition, true);
                                ACCESS.setShapeWithStoreFence(object, newShape);
                            } else if (location.isFinal()) {
                                continue;
                            } else {
                                location.setSafe(object, value, guardCondition, false);
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

        protected KeyCacheNode insertIntoPutCache(DynamicObject object, Shape cachedShape, Object value, long putFlags, Property property) {
            CompilerAsserts.neverPartOfCompilation();
            if (!cachedShape.isValid()) {
                return Generic.instance();
            }
            Lock lock = getLock();
            lock.lock();
            try {
                MutateCacheData tail = filterValid(this.cache);

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
                    assert newProperty.getLocation().canStore(value);
                }

                Assumption newShapeValid = getShapeValidAssumption(oldShape, newShape);
                this.cache = new PutCacheData(putFlags, newShape, newShapeValid, newProperty, tail);
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
                    LayoutStrategy strategy = oldShape.getLayoutStrategy();
                    return strategy.defineProperty(oldShape, cachedKey, value, propertyFlags, null, putFlags);
                }
            }

            if (Flags.isUpdateFlags(putFlags)) {
                if (Flags.getPropertyFlags(putFlags) != property.getFlags()) {
                    int propertyFlags = Flags.getPropertyFlags(putFlags);
                    LayoutStrategy strategy = oldShape.getLayoutStrategy();
                    return strategy.defineProperty(oldShape, cachedKey, value, propertyFlags, null, putFlags);
                }
            }

            Location location = property.getLocation();
            if (!location.isDeclared() && !location.canStore(value)) {
                // generalize
                assert oldShape == ACCESS.getShape(object);
                LayoutStrategy strategy = oldShape.getLayoutStrategy();
                ShapeImpl newShape = strategy.definePropertyGeneralize(oldShape, property, value, null, putFlags);
                assert newShape != oldShape;
                return newShape;
            } else if (location.isDeclared()) {
                // redefine declared
                LayoutStrategy strategy = oldShape.getLayoutStrategy();
                return strategy.defineProperty(oldShape, cachedKey, value, property.getFlags(), null, putFlags);
            } else {
                // set existing
                assert location.canStore(value);
                return oldShape;
            }
        }

        @ExplodeLoop
        protected boolean setPropertyFlagsImpl(DynamicObject object, Shape cachedShape, Object key, int propertyFlags, Property cachedProperty) {
            Shape oldShape = cachedShape;
            MutateCacheData start = cache;
            if (start == MutateCacheData.GENERIC || !cachedShape.isValid()) {
                return Generic.instance().setPropertyFlags(object, cachedShape, key, propertyFlags);
            }
            for (MutateCacheData c = start; c != null; c = c.next) {
                if (!c.isValid()) {
                    break;
                } else if (c instanceof SetPropertyFlagsCacheData && ((SetPropertyFlagsCacheData) c).property.getFlags() == propertyFlags) {
                    if (cachedProperty == null) {
                        return false;
                    }
                    if (cachedProperty.getFlags() != propertyFlags) {
                        Shape newShape = c.newShape;
                        if (newShape != oldShape) {
                            ACCESS.setShape(object, newShape);
                            c.maybeUpdateShape(object);
                        }
                    }
                    return true;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            KeyCacheNode impl = insertIntoSetPropertyFlagsCache(cachedShape, propertyFlags, cachedProperty);
            return impl.setPropertyFlags(object, cachedShape, key, propertyFlags);
        }

        protected KeyCacheNode insertIntoSetPropertyFlagsCache(Shape cachedShape, int propertyFlags, Property cachedProperty) {
            CompilerAsserts.neverPartOfCompilation();
            if (!cachedShape.isValid()) {
                return Generic.instance();
            }
            Lock lock = getLock();
            lock.lock();
            try {
                MutateCacheData tail = filterValid(this.cache);

                ShapeImpl oldShape = (ShapeImpl) cachedShape;
                ShapeImpl newShape = changePropertyFlags(oldShape, (PropertyImpl) cachedProperty, propertyFlags);

                if (!oldShape.isValid()) {
                    // If shape was invalidated, other locations may have changed, too,
                    // so we need to update the object's shape first.
                    // Cache entries with an invalid cache entry directly go to the slow path.
                    return Generic.instance();
                }

                Property newProperty;
                if (newShape == oldShape) {
                    newProperty = cachedProperty;
                } else {
                    newProperty = newShape.getProperty(cachedKey);
                }

                Assumption newShapeValid = getShapeValidAssumption(oldShape, newShape);
                this.cache = new SetPropertyFlagsCacheData(newShape, newShapeValid, newProperty, tail);
                return this;
            } finally {
                lock.unlock();
            }
        }

        @ExplodeLoop
        protected boolean removeKeyImpl(DynamicObject object, Shape cachedShape, Object key, Property cachedProperty) {
            Shape oldShape = cachedShape;
            MutateCacheData start = cache;
            if (start == MutateCacheData.GENERIC || !cachedShape.isValid()) {
                return Generic.instance().removeKey(object, cachedShape, key);
            }
            for (MutateCacheData c = start; c != null; c = c.next) {
                if (!c.isValid()) {
                    break;
                } else if (c instanceof RemovePropertyCacheData) {
                    if (cachedProperty == null) {
                        return false;
                    }
                    Shape newShape = c.newShape;
                    assert newShape != oldShape;
                    Map<Object, Object> archive = null;
                    assert (archive = ACCESS.archive(object)) != null;

                    if (!oldShape.isShared()) {
                        ((RemovePropertyCacheData) c).removePlan.execute(object);
                    } else {
                        ACCESS.setShape(object, newShape);
                    }

                    assert ACCESS.verifyValues(object, archive);

                    c.maybeUpdateShape(object);
                    return true;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            KeyCacheNode impl = insertIntoRemoveKeyCache(cachedShape, cachedProperty);
            return impl.removeKey(object, cachedShape, key);
        }

        protected KeyCacheNode insertIntoRemoveKeyCache(Shape cachedShape, Property cachedProperty) {
            CompilerAsserts.neverPartOfCompilation();
            if (!cachedShape.isValid()) {
                return Generic.instance();
            }
            Lock lock = getLock();
            lock.lock();
            try {
                MutateCacheData tail = filterValid(this.cache);

                ShapeImpl oldShape = (ShapeImpl) cachedShape;
                ShapeImpl newShape = oldShape.removeProperty(cachedProperty);

                if (!oldShape.isValid()) {
                    // If shape was invalidated, other locations may have changed, too,
                    // so we need to update the object's shape first.
                    // Cache entries with an invalid cache entry directly go to the slow path.
                    return Generic.instance();
                }

                RemovePlan removePlan = null;
                if (!oldShape.isShared()) {
                    removePlan = prepareRemove(oldShape, newShape);
                }

                Assumption newShapeValid = getShapeValidAssumption(oldShape, newShape);
                this.cache = new RemovePropertyCacheData(newShape, newShapeValid, removePlan, tail);
                return this;
            } finally {
                lock.unlock();
            }
        }

        private static Assumption getShapeValidAssumption(Shape oldShape, Shape newShape) {
            if (oldShape == newShape) {
                return Assumption.ALWAYS_VALID;
            }
            return newShape.isValid() ? newShape.getValidAssumption() : Assumption.NEVER_VALID;
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

    static class MutateCacheData extends CacheData<MutateCacheData> {
        static final MutateCacheData GENERIC = new MutateCacheData(null, null, null);

        final Shape newShape;
        final Assumption newShapeValidAssumption;

        MutateCacheData(MutateCacheData next, Shape newShape, Assumption newShapeValidAssumption) {
            super(next);
            this.newShape = newShape;
            this.newShapeValidAssumption = newShapeValidAssumption;
        }

        @Override
        protected boolean isValid() {
            Assumption newShapeValid = newShapeValidAssumption;
            return newShapeValid == Assumption.NEVER_VALID || newShapeValid == Assumption.ALWAYS_VALID || newShapeValid.isValid();
        }

        protected void maybeUpdateShape(DynamicObject store) {
            if (newShapeValidAssumption == Assumption.NEVER_VALID) {
                updateShapeImpl(store);
            }
        }

        @Override
        protected MutateCacheData withNext(MutateCacheData newNext) {
            return new MutateCacheData(next, newShape, newShapeValidAssumption);
        }
    }

    static class PutCacheData extends MutateCacheData {

        final long putFlags;
        final Property property;

        PutCacheData(long putFlags, Shape newShape, Assumption newShapeValidAssumption, Property property, MutateCacheData next) {
            super(next, newShape, newShapeValidAssumption);
            this.putFlags = putFlags;
            this.property = property;
        }

        @Override
        protected MutateCacheData withNext(MutateCacheData newNext) {
            return new PutCacheData(putFlags, newShape, newShapeValidAssumption, property, newNext);
        }
    }

    static class SetPropertyFlagsCacheData extends MutateCacheData {

        final Property property;

        SetPropertyFlagsCacheData(Shape newShape, Assumption newShapeValidAssumption, Property property, MutateCacheData next) {
            super(next, newShape, newShapeValidAssumption);
            this.property = property;
        }

        @Override
        protected MutateCacheData withNext(MutateCacheData newNext) {
            return new SetPropertyFlagsCacheData(newShape, newShapeValidAssumption, property, newNext);
        }
    }

    static class RemovePropertyCacheData extends MutateCacheData {

        final RemovePlan removePlan;

        RemovePropertyCacheData(Shape newShape, Assumption newShapeValidAssumption, RemovePlan removePlan, MutateCacheData next) {
            super(next, newShape, newShapeValidAssumption);
            this.removePlan = removePlan;
        }

        @Override
        protected MutateCacheData withNext(MutateCacheData newNext) {
            return new RemovePropertyCacheData(newShape, newShapeValidAssumption, removePlan, newNext);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class SetFlagsNode extends Node {
        abstract boolean execute(Node node, DynamicObject object, Shape cachedShape, int flags);

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
    @GenerateInline
    @GenerateCached(false)
    abstract static class SetDynamicTypeNode extends Node {
        abstract boolean execute(Node node, DynamicObject object, Shape cachedShape, Object objectType);

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
            return ((ShapeImpl) shape).setDynamicType(newType);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class MakeSharedNode extends Node {
        abstract void execute(Node node, DynamicObject object, Shape cachedShape);

        @Specialization
        static void doCached(DynamicObject object, Shape cachedShape,
                        @Cached(value = "makeSharedShape(cachedShape)", allowUncached = true, neverDefault = true) Shape newShape) {
            assert newShape != cachedShape &&
                            ((ShapeImpl) cachedShape).getObjectArrayCapacity() == ((ShapeImpl) newShape).getObjectArrayCapacity() &&
                            ((ShapeImpl) cachedShape).getPrimitiveArrayCapacity() == ((ShapeImpl) newShape).getPrimitiveArrayCapacity();
            ACCESS.setShape(object, newShape);
        }

        static Shape makeSharedShape(Shape inputShape) {
            return ((ShapeImpl) inputShape).makeSharedShape();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class ResetShapeNode extends Node {
        abstract boolean execute(Node node, DynamicObject object, Shape cachedShape, Shape newShape);

        @Specialization(guards = "otherShape == cachedOtherShape", limit = "3")
        static boolean doCached(DynamicObject object, Shape cachedShape, @SuppressWarnings("unused") Shape otherShape,
                        @Cached(value = "verifyResetShape(cachedShape, otherShape)", allowUncached = true) Shape cachedOtherShape) {
            if (cachedShape == cachedOtherShape) {
                return false;
            }
            ACCESS.resize(object, cachedShape, cachedOtherShape);
            ACCESS.setShape(object, cachedOtherShape);
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
