/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.object.DebugCounters.propertyAssumptionsBlocked;
import static com.oracle.truffle.api.object.DebugCounters.propertyAssumptionsCreated;
import static com.oracle.truffle.api.object.DebugCounters.propertyAssumptionsRemoved;
import static com.oracle.truffle.api.object.DebugCounters.shapeCacheHitCount;
import static com.oracle.truffle.api.object.DebugCounters.shapeCacheMissCount;
import static com.oracle.truffle.api.object.DebugCounters.shapeCount;
import static com.oracle.truffle.api.object.DebugCounters.transitionMapsCreated;
import static com.oracle.truffle.api.object.DebugCounters.transitionSingleEntriesCreated;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.Pair;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NonIdempotent;

/**
 * A Shape is an immutable descriptor of the current object "shape" of a DynamicObject, i.e., object
 * layout, metadata ({@linkplain Shape#getDynamicType() type}, {@linkplain Shape#getFlags() flags}),
 * and a mapping of {@linkplain Property properties} to storage locations. This allows cached
 * {@link DynamicObjectLibrary} to do a simple shape check to determine the contents of an object
 * and do fast, constant-time property accesses. Shape changes, like adding or removing a property,
 * yield a new Shape derived from the old one.
 *
 * <p>
 * Shapes are shared between objects that assume the same shape if they follow the same shape
 * transitions, like adding the same properties in the same order, starting from a common root
 * shape. Shape transitions are automatically, weakly cached.
 *
 * <p>
 * Dynamic objects start off with an initial shape that has no instance properties (but may have
 * constant properties that are stored in the shape). Initial shapes are created via
 * {@link Shape#newBuilder()}.
 *
 * @see DynamicObject
 * @see Shape#newBuilder()
 * @see Property
 * @since 0.8 or earlier
 */
public final class Shape {

    static final int OBJECT_FLAGS_MASK = 0x0000_ffff;
    static final int OBJECT_FLAGS_SHIFT = 0;

    /** Shared shape flag. */
    static final int FLAG_SHARED_SHAPE = 1 << 16;
    /** Flag that is set if {@link Shape.Builder#propertyAssumptions(boolean)} is true. */
    static final int FLAG_ALLOW_PROPERTY_ASSUMPTIONS = 1 << 17;
    /** Automatic flag that is set if the shape has instance properties. */
    static final int FLAG_HAS_INSTANCE_PROPERTIES = 1 << 18;

    /** Shape and object flags. */
    final int flags;

    final Object objectType;
    final PropertyMap propertyMap;
    private final LayoutImpl layout;
    final Shape parent;
    final Shape root;
    final Object sharedData;

    private final int objectArraySize;
    private final int objectArrayCapacity;
    private final int primitiveArraySize;
    private final int primitiveArrayCapacity;
    private final short objectFieldSize;
    private final short primitiveFieldSize;

    private final int depth;
    private final int propertyCount;

    private final Assumption validAssumption;
    @CompilationFinal private volatile Assumption leafAssumption;

    /**
     * Shape transition map; lazily initialized. One of:
     * <ol>
     * <li>{@code null}: empty map
     * <li>{@link StrongKeyWeakValueEntry}: immutable single entry map
     * <li>{@link TransitionMap}: mutable multiple entry map
     * </ol>
     *
     * @see #queryTransition(Transition)
     * @see #addTransitionIfAbsentOrNull(Transition, Shape)
     */
    private volatile Object transitionMap;

    private final Transition transitionFromParent;

    private volatile PropertyAssumptions sharedPropertyAssumptions;

    /**
     * The successor shape if this shape is obsolete.
     */
    private volatile Shape successorShape;
    /**
     * This reference keeps shape transitions to obsolete shapes alive as long as the successor
     * shape is reachable in order to ensure that no new shapes are created for those transitions.
     * Otherwise, dead obsolete shapes might be recreated and then obsoleted again.
     *
     * Either null, a single Shape, or a copy-on-write Shape[] array.
     */
    private volatile Object predecessorShape;

    private static final AtomicReferenceFieldUpdater<Shape, Object> TRANSITION_MAP_UPDATER = AtomicReferenceFieldUpdater.newUpdater(Shape.class, Object.class, "transitionMap");
    private static final AtomicReferenceFieldUpdater<Shape, Assumption> LEAF_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(Shape.class, Assumption.class, "leafAssumption");
    private static final AtomicReferenceFieldUpdater<Shape, PropertyAssumptions> PROPERTY_ASSUMPTIONS_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(Shape.class, PropertyAssumptions.class, "sharedPropertyAssumptions");

    private static final VarHandle PREDECESSOR_SHAPE_UPDATER;
    static {
        var lookup = MethodHandles.lookup();
        try {
            PREDECESSOR_SHAPE_UPDATER = lookup.findVarHandle(Shape.class, "predecessorShape", Object.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Creates a new initial shape builder.
     *
     *
     * The builder instance is not thread-safe and must not be used from multiple threads at the
     * same time.
     *
     * @since 20.2.0
     */
    public static Builder newBuilder() {
        CompilerAsserts.neverPartOfCompilation();
        return new Builder();
    }

    /**
     * Internal superclass shared by {@link Builder} and {@link DerivedBuilder}.
     *
     * @since 20.2.0
     */
    abstract static class AbstractBuilder<T extends AbstractBuilder<T>> {
        /**
         * Sets initial dynamic object type identifier.
         *
         * See {@link DynamicObjectLibrary#setDynamicType(DynamicObject, Object)} for more
         * information.
         *
         * @param dynamicType a non-null object type identifier
         * @throws NullPointerException if the type is {@code null}
         * @since 20.2.0
         */
        public abstract T dynamicType(Object dynamicType);

        /**
         * Sets initial shape flags (default: 0). Currently limited to 16 bits.
         *
         * See {@link DynamicObjectLibrary#setShapeFlags(DynamicObject, int)} for more information.
         *
         * @param flags an int value in the range from 0 to 65535 (inclusive)
         * @throws IllegalArgumentException if the flags value is not in the supported range
         * @since 20.2.0
         */
        public abstract T shapeFlags(int flags);

        /**
         * Adds a property with a constant value to the shape. The key must not be {@code null} and
         * must not be equal to any previously added property's key.
         *
         * @param key the property's key
         * @param value the property's value
         * @param flags the property's flags
         * @throws NullPointerException if the key is {@code null}
         * @throws IllegalArgumentException if a property with the key already exists
         * @see DynamicObjectLibrary#putConstant(DynamicObject, Object, Object, int)
         * @since 20.2.0
         */
        public abstract T addConstantProperty(Object key, Object value, int flags);

        static Object checkDynamicType(Object dynamicType) {
            Objects.requireNonNull(dynamicType, "dynamicType");
            return dynamicType;
        }

        static int checkShapeFlags(int flags) {
            if ((flags & ~OBJECT_FLAGS_MASK) != 0) {
                throw new IllegalArgumentException("flags must be in the range [0, 0xffff]");
            }
            return flags;
        }
    }

    /**
     * Builder class to construct initial {@link Shape} instances.
     *
     * The builder instance is not thread-safe and must not be used from multiple threads at the
     * same time.
     *
     * @see Shape#newBuilder()
     * @since 20.2.0
     */
    @SuppressWarnings({"hiding", "deprecation"})
    public static final class Builder extends AbstractBuilder<Builder> {

        private Class<? extends DynamicObject> layoutClass = DynamicObject.class;
        private MethodHandles.Lookup layoutLookup = DynamicObject.internalLookup();
        private Object dynamicType = ObjectType.DEFAULT;
        private int shapeFlags;
        private boolean allowImplicitCastIntToDouble;
        private boolean allowImplicitCastIntToLong;
        private boolean shared;
        private boolean propertyAssumptions;
        private Object sharedData;
        private Assumption singleContextAssumption;
        private EconomicMap<Object, Pair<Object, Integer>> properties;

        Builder() {
        }

        /**
         * Sets custom object layout class (default: {@link DynamicObject} base class).
         *
         * Enables the use of dynamic object fields declared in subclasses using the
         * {@code DynamicField} annotation.
         *
         * <p>
         * Examples:
         *
         * <pre>
         * <code>
         * public class MyObject extends DynamicObject implements TruffleObject {
         *
         *     &#64;DynamicField private Object _obj1;
         *     &#64;DynamicField private Object _obj2;
         *     &#64;DynamicField private long _long1;
         *     &#64;DynamicField private long _long2;
         *
         *     public MyObject(Shape shape) {
         *         super(shape);
         *     }
         * }
         *
         *
         * Shape myObjShape = Shape.newBuilder().layout(MyObject.class).build();
         * MyObject obj = new MyObject(myObjShape);
         * </code>
         * </pre>
         *
         * @param layoutClass custom object layout class
         * @since 20.2.0
         * @deprecated Use {@link #layout(Class, MethodHandles.Lookup)} instead.
         */
        @Deprecated(since = "24.2")
        public Builder layout(Class<? extends DynamicObject> layoutClass) {
            CompilerAsserts.neverPartOfCompilation();
            if (!DynamicObject.class.isAssignableFrom(layoutClass)) {
                throw new IllegalArgumentException(String.format("Expected a subclass of %s but got: %s",
                                DynamicObject.class.getName(), layoutClass.getTypeName()));
            }
            this.layoutClass = layoutClass;
            this.layoutLookup = null;
            return this;
        }

        /**
         * Sets a custom object layout class (default: <code>{@link DynamicObject}.class</code>) and
         * a corresponding {@link MethodHandles.Lookup} created by the layout class, or a class in
         * the same module, that has full privilege access in order to provide access to its fields.
         * <p>
         * Enables the allocation of any additional {@code DynamicField}-annotated fields declared
         * in the {@link DynamicObject} layout subclass as storage locations. By default, only
         * extension array based storage locations in the {@link DynamicObject} base class are used.
         * <p>
         * Also restricts the shape to a specific {@link DynamicObject} subclass and subclasses
         * thereof, i.e. shapes created for a {@link DynamicObject} layout subclass, and any derived
         * shapes, can only be used to instantiate, and be assigned to, instances of that class
         * (regardless of whether the class actually contains any dynamic fields).
         *
         * <p>
         * Example:
         *
         * <pre>
         * <code>
         * public class MyObject extends DynamicObject implements TruffleObject {
         *
         *     &#64;DynamicField private Object _obj1;
         *     &#64;DynamicField private Object _obj2;
         *     &#64;DynamicField private long _long1;
         *     &#64;DynamicField private long _long2;
         *
         *     public MyObject(Shape shape) {
         *         super(shape);
         *     }
         *
         *     static MethodHandles.Lookup lookup() {
         *         return MethodHandles.lookup();
         *     }
         * }
         *
         *
         * Shape myObjShape = Shape.newBuilder().layout(MyObject.class, MyObject.lookup()).build();
         * MyObject obj = new MyObject(myObjShape);
         * </code>
         * </pre>
         *
         * @param layoutClass a {@link DynamicObject} layout subclass or the default
         *            <code>{@link DynamicObject}.class</code>.
         * @param layoutClassLookup a {@link Lookup} that has full privilege access, created using
         *            {@link MethodHandles#lookup()}, either by the layout class itself, or a class
         *            in the same module. If <code>layoutClass == DynamicObject.class</code>, and
         *            only then, this parameter is ignored and may be <code>null</code>.
         * @throws IllegalArgumentException if <code>layoutClass != DynamicObject.class</code> and
         *             the lookup does not have {@link MethodHandles.Lookup#hasFullPrivilegeAccess()
         *             full privilege access} or is not from the layout class or a class within the
         *             same module as the layout class.
         * @throws NullPointerException if <code>layoutClass == null</code>, or
         *             <code>lookup == null</code> and
         *             <code>layoutClass != {@link DynamicObject}.class</code>.
         * @since 24.2.0
         */
        public Builder layout(Class<? extends DynamicObject> layoutClass, Lookup layoutClassLookup) {
            CompilerAsserts.neverPartOfCompilation();
            if (!DynamicObject.class.isAssignableFrom(layoutClass)) {
                throw new IllegalArgumentException(String.format("Expected a subclass of %s but got: %s",
                                DynamicObject.class.getName(), layoutClass.getTypeName()));
            }
            if (layoutClass == DynamicObject.class) {
                this.layoutClass = DynamicObject.class;
                this.layoutLookup = DynamicObject.internalLookup();
                return this;
            }
            if (!layoutClassLookup.hasFullPrivilegeAccess()) {
                throw new IllegalArgumentException("Lookup must have full privilege access");
            }
            if (layoutClassLookup.lookupClass().getModule() != layoutClass.getModule()) {
                throw new IllegalArgumentException("Lookup must be from the same module as the layout class");
            }
            this.layoutClass = layoutClass;
            this.layoutLookup = layoutClassLookup;
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException {@inheritDoc}
         * @see DynamicObjectLibrary#getDynamicType(DynamicObject)
         * @see DynamicObjectLibrary#setDynamicType(DynamicObject, Object)
         * @since 20.2.0
         */
        @Override
        public Builder dynamicType(Object dynamicType) {
            CompilerAsserts.neverPartOfCompilation();
            this.dynamicType = checkDynamicType(dynamicType);
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @throws IllegalArgumentException {@inheritDoc}
         * @see DynamicObjectLibrary#getShapeFlags(DynamicObject)
         * @see DynamicObjectLibrary#setShapeFlags(DynamicObject, int)
         * @since 20.2.0
         */
        @Override
        public Builder shapeFlags(int flags) {
            CompilerAsserts.neverPartOfCompilation();
            this.shapeFlags = checkShapeFlags(flags);
            return this;
        }

        /**
         * If {@code true}, makes the object shared (default: {@code false}).
         *
         * @see Shape#isShared()
         * @see DynamicObjectLibrary#isShared(DynamicObject)
         * @see DynamicObjectLibrary#markShared(DynamicObject)
         * @since 20.2.0
         */
        public Builder shared(boolean isShared) {
            CompilerAsserts.neverPartOfCompilation();
            this.shared = isShared;
            return this;
        }

        /**
         * If {@code true}, enables the use of {@linkplain Shape#getPropertyAssumption(Object)
         * property assumptions} for this object shape and any derived shapes (default:
         * {@code false}). Property assumptions allow speculating on select properties being absent
         * or stable across shape changes.
         *
         * <p>
         * Use of property assumptions can be beneficial in single-context mode for long-lived
         * objects with stable properties but recurrent shape changes due to properties being added
         * (e.g. global objects of a context), in which case a shape cache would be unstable while
         * the property assumption allows for a stable cache.
         *
         * @see Shape#getPropertyAssumption(Object)
         * @since 20.2.0
         */
        public Builder propertyAssumptions(boolean enable) {
            CompilerAsserts.neverPartOfCompilation();
            this.propertyAssumptions = enable;
            return this;
        }

        /**
         * Sets shared data to be associated with the root shape and any derived shapes (e.g. a
         * {@code TruffleLanguage} instance). May be null (the default).
         *
         * @see Shape#getSharedData()
         * @since 20.2.0
         */
        public Builder sharedData(Object sharedData) {
            CompilerAsserts.neverPartOfCompilation();
            this.sharedData = sharedData;
            return this;
        }

        /**
         * Sets an assumption that allows specializations on constant object instances with this
         * shape, as long as the assumption is valid. The assumption should be valid only if code is
         * not shared across contexts and invalidated when this is not longer true. The assumption
         * may be {@code null} in which case this feature is disabled (the default).
         *
         * @see #propertyAssumptions(boolean)
         * @since 20.2.0
         */
        public Builder singleContextAssumption(Assumption assumption) {
            CompilerAsserts.neverPartOfCompilation();
            this.singleContextAssumption = assumption;
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException {@inheritDoc}
         * @throws IllegalArgumentException {@inheritDoc}
         * @see DynamicObjectLibrary#putConstant(DynamicObject, Object, Object, int)
         * @since 20.2.0
         */
        @Override
        public Builder addConstantProperty(Object key, Object value, int flags) {
            CompilerAsserts.neverPartOfCompilation();
            Objects.requireNonNull(key, "key");
            if (properties == null) {
                properties = EconomicMap.create(Equivalence.DEFAULT);
            }
            if (properties.containsKey(key)) {
                throw new IllegalArgumentException(String.format("Property already exists: %s.", key));
            }

            properties.put(key, Pair.create(value, flags));
            return this;
        }

        /**
         * Allows values to be implicitly cast from int to long in this shape and any derived
         * shapes.
         *
         * @since 20.2.0
         */
        public Builder allowImplicitCastIntToLong(boolean allow) {
            this.allowImplicitCastIntToLong = allow;
            return this;
        }

        /**
         * Allows values to be implicitly cast from int to double in this shape and any derived
         * shapes.
         *
         * @since 20.2.0
         */
        public Builder allowImplicitCastIntToDouble(boolean allow) {
            this.allowImplicitCastIntToDouble = allow;
            return this;
        }

        /**
         * Builds a new shape using the configuration of this builder.
         *
         * @since 20.2.0
         */
        public Shape build() {
            CompilerAsserts.neverPartOfCompilation();
            int flags = shapeFlags;
            if (shared) {
                flags = shapeFlags | FLAG_SHARED_SHAPE;
            }
            if (propertyAssumptions) {
                flags = shapeFlags | FLAG_ALLOW_PROPERTY_ASSUMPTIONS;
            }

            int implicitCastFlags = (allowImplicitCastIntToDouble ? Layout.INT_TO_DOUBLE_FLAG : 0) | (allowImplicitCastIntToLong ? Layout.INT_TO_LONG_FLAG : 0);
            Shape shape = Layout.getFactory().createShape(
                            layoutClass,
                            implicitCastFlags,
                            dynamicType,
                            sharedData,
                            flags,
                            properties,
                            singleContextAssumption,
                            layoutLookup);

            assert shape.isShared() == shared && shape.getFlags() == shapeFlags && shape.getDynamicType() == dynamicType;
            return shape;
        }
    }

    /**
     * Creates a new derived shape builder that allows changing a root shape's flags and dynamic
     * type and adding constant properties.
     *
     * The builder instance is not thread-safe and must not be used from multiple threads at the
     * same time.
     *
     * @param baseShape the shape to be modified
     * @see Shape#newBuilder()
     * @since 20.2.0
     */
    public static DerivedBuilder newBuilder(Shape baseShape) {
        CompilerAsserts.neverPartOfCompilation();
        return new DerivedBuilder(baseShape);
    }

    /**
     * Builder class to construct derived {@link Shape} instances.
     *
     * The builder instance is not thread-safe and must not be used from multiple threads at the
     * same time.
     *
     * @see Shape#newBuilder(Shape)
     * @since 20.2.0
     */
    @SuppressWarnings("hiding")
    public static final class DerivedBuilder extends AbstractBuilder<DerivedBuilder> {

        private final Shape baseShape;
        private Object dynamicType;
        private int shapeFlags;
        private EconomicMap<Object, Pair<Object, Integer>> properties;

        DerivedBuilder(Shape baseShape) {
            this.baseShape = baseShape;
            this.dynamicType = baseShape.getDynamicType();
            this.shapeFlags = baseShape.getFlags();
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException {@inheritDoc}
         * @see DynamicObjectLibrary#getDynamicType(DynamicObject)
         * @see DynamicObjectLibrary#setDynamicType(DynamicObject, Object)
         * @since 20.2.0
         */
        @Override
        public DerivedBuilder dynamicType(Object dynamicType) {
            CompilerAsserts.neverPartOfCompilation();
            this.dynamicType = checkDynamicType(dynamicType);
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @throws IllegalArgumentException {@inheritDoc}
         * @see DynamicObjectLibrary#getShapeFlags(DynamicObject)
         * @see DynamicObjectLibrary#setShapeFlags(DynamicObject, int)
         * @since 20.2.0
         */
        @Override
        public DerivedBuilder shapeFlags(int flags) {
            CompilerAsserts.neverPartOfCompilation();
            this.shapeFlags = checkShapeFlags(flags);
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException {@inheritDoc}
         * @throws IllegalArgumentException {@inheritDoc}
         * @see DynamicObjectLibrary#putConstant(DynamicObject, Object, Object, int)
         * @since 20.2.0
         */
        @Override
        public DerivedBuilder addConstantProperty(Object key, Object value, int flags) {
            CompilerAsserts.neverPartOfCompilation();
            Objects.requireNonNull(key, "key");
            if (properties == null) {
                properties = EconomicMap.create(Equivalence.DEFAULT);
            }
            if (baseShape.getProperty(key) != null || properties.containsKey(key)) {
                throw new IllegalArgumentException(String.format("Property already exists: %s.", key));
            }
            properties.put(key, Pair.create(value, flags));
            return this;
        }

        /**
         * Builds a derived shape from the base shape supplied to the constructor using the
         * configuration of this builder.
         *
         * @return a new or cached shape
         * @since 20.2.0
         */
        public Shape build() {
            CompilerAsserts.neverPartOfCompilation();
            Shape derivedShape = baseShape;
            if (dynamicType != derivedShape.getDynamicType()) {
                derivedShape = derivedShape.setDynamicType(dynamicType);
            }
            if (shapeFlags != derivedShape.getFlags()) {
                derivedShape = derivedShape.setFlags(shapeFlags);
            }
            if (properties != null) {
                var cursor = properties.getEntries();
                while (cursor.advance()) {
                    derivedShape = derivedShape.defineConstantProperty(cursor.getKey(), cursor.getValue().getLeft(), cursor.getValue().getRight());
                }
            }
            return derivedShape;
        }
    }

    /**
     * Get a property entry by key.
     *
     * @param key the identifier to look up
     * @return a Property object, or {@code null} if not found
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    public Property getProperty(Object key) {
        return propertyMap.get(key);
    }

    /**
     * Add a new property in the map, yielding a new or cached Shape object.
     *
     * @param property the property to add
     * @return the new Shape
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    protected Shape addProperty(Property property) {
        return getLayoutStrategy().addProperty(this, property);
    }

    /**
     * Add or change property in the map, yielding a new or cached Shape object.
     *
     * @return the shape after defining the property
     * @since 0.8 or earlier
     * @deprecated Use {@link DynamicObjectLibrary#put(DynamicObject, Object, Object)} or
     *             {@link DynamicObjectLibrary#putWithFlags(DynamicObject, Object, Object, int)}.
     */
    @Deprecated(since = "22.2")
    @TruffleBoundary
    public Shape defineProperty(Object key, Object value, int propertyFlags) {
        return getLayoutStrategy().defineProperty(this, key, value, propertyFlags);
    }

    /**
     * Add or change property in the map, yielding a new or cached Shape object.
     *
     * @return the shape after defining the property
     * @since 24.1
     */
    @TruffleBoundary
    protected Shape defineProperty(Object key, Object value, int propertyFlags, int putFlags) {
        return getLayoutStrategy().defineProperty(this, key, value, propertyFlags, putFlags);
    }

    /**
     * Add or replace shape-constant property.
     *
     * @return the shape after defining the property
     */
    Shape defineConstantProperty(Object key, Object value, int propertyFlags) {
        return defineProperty(key, value, propertyFlags, Flags.CONST);
    }

    /**
     * An {@link Iterable} over the shape's properties in insertion order.
     *
     * @since 0.8 or earlier
     */
    public Iterable<Property> getProperties() {
        return getPropertyList();
    }

    /**
     * Get a list of all properties that this Shape stores.
     *
     * Properties with a {@link HiddenKey} are not included.
     *
     * @return list of properties
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    public List<Property> getPropertyList() {
        return Arrays.asList(getPropertyArray());
    }

    @TruffleBoundary
    Property[] getPropertyArray() {
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
     * Returns all (also hidden) property objects in this shape.
     *
     * @param ascending desired order ({@code true} for insertion order, {@code false} for reverse
     *            insertion order)
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    public List<Property> getPropertyListInternal(boolean ascending) {
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
     * Get a list of all property keys in insertion order.
     *
     * Properties with a {@link HiddenKey} are not included.
     *
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    public List<Object> getKeyList() {
        return Arrays.asList(getKeyArray());
    }

    @TruffleBoundary
    Object[] getKeyArray() {
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

    /**
     * Get all property keys in insertion order.
     *
     * @since 0.8 or earlier
     */
    public Iterable<Object> getKeys() {
        return getKeyList();
    }

    /**
     * Get an assumption that the shape is valid.
     *
     * @since 0.8 or earlier
     */
    @Idempotent
    public Assumption getValidAssumption() {
        return validAssumption;
    }

    /**
     * Check whether this shape is valid.
     *
     * @since 0.8 or earlier
     */
    @NonIdempotent
    public boolean isValid() {
        return getValidAssumption().isValid();
    }

    /**
     * Get an assumption that the shape is a leaf.
     *
     * @since 0.8 or earlier
     */
    @NonIdempotent
    public Assumption getLeafAssumption() {
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

    /**
     * Check whether this shape is a leaf in the transition graph, i.e. transitionless.
     *
     * @since 0.8 or earlier
     */
    @NonIdempotent
    @TruffleBoundary
    public boolean isLeaf() {
        Assumption assumption = leafAssumption;
        return assumption == null || assumption.isValid();
    }

    /**
     * Check whether the shape has a property with the given key.
     *
     * @since 0.8 or earlier
     */
    public boolean hasProperty(Object name) {
        return getProperty(name) != null;
    }

    /**
     * Remove the given property from the shape.
     *
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    protected Shape removeProperty(Property property) {
        return getLayoutStrategy().removeProperty(this, property);
    }

    /**
     * Replace a property in the shape.
     *
     * @since 0.8 or earlier
     */
    protected Shape replaceProperty(Property oldProperty, Property newProperty) {
        assert oldProperty.getKey().equals(newProperty.getKey());
        return getLayoutStrategy().replaceProperty(this, oldProperty, newProperty);
    }

    /**
     * Get the last property.
     *
     * @since 0.8 or earlier
     */
    public Property getLastProperty() {
        return propertyMap.getLastProperty();
    }

    /**
     * Returns the language-specific shape flags previously set using
     * {@link DynamicObjectLibrary#setShapeFlags(DynamicObject, int)} or
     * {@link Shape.Builder#shapeFlags(int)}. If no shape flags were explicitly set, the default of
     * 0 is returned.
     *
     * These flags may be used to tag objects that possess characteristics that need to be queried
     * efficiently on fast and slow paths. For example, they can be used to mark objects as frozen.
     *
     * @see DynamicObjectLibrary#getShapeFlags(DynamicObject)
     * @see DynamicObjectLibrary#setShapeFlags(DynamicObject, int)
     * @see Shape.Builder#shapeFlags(int)
     * @since 20.2.0
     */
    @Idempotent
    public int getFlags() {
        return getObjectFlags(flags);
    }

    int getFlagsInternal() {
        return flags;
    }

    /**
     * Returns a copy of the shape, with the shape flags set to {@code newFlags}.
     *
     * @param objectFlags the new shape flags; an int value in the range from 0 to 65535 (inclusive)
     * @throws IllegalArgumentException if the flags value is not in the supported range
     * @see Shape.Builder#shapeFlags(int)
     * @since 20.2.0
     */
    @TruffleBoundary
    protected Shape setFlags(int objectFlags) {
        checkObjectFlags(objectFlags);
        if (getFlags() == objectFlags) {
            return this;
        }

        Transition.ObjectFlagsTransition transition = new Transition.ObjectFlagsTransition(objectFlags);
        Shape cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return cachedShape;
        }

        int newFlags = objectFlags | (this.flags & ~OBJECT_FLAGS_MASK);
        Shape newShape = this.createShapeWithSameSize(objectType, sharedData, propertyMap, transition, newFlags);
        return addDirectTransition(transition, newShape);
    }

    /**
     * Returns the number of properties in this shape.
     *
     * @since 0.8 or earlier
     */
    public int getPropertyCount() {
        return propertyCount;
    }

    /**
     * Get the shape's dynamic object type identifier.
     *
     * @since 20.2.0
     */
    public Object getDynamicType() {
        return objectType;
    }

    /**
     * Returns a copy of the shape, with the dynamic object type identifier set to
     * {@code dynamicType}.
     *
     * @param dynamicType the new dynamic object type identifier
     * @throws NullPointerException if the argument is null.
     * @see Shape.Builder#dynamicType(Object)
     * @since 20.2.0
     */
    @TruffleBoundary
    protected Shape setDynamicType(Object dynamicType) {
        Objects.requireNonNull(dynamicType, "dynamicType");
        if (getDynamicType() == dynamicType) {
            return this;
        }
        Transition.ObjectTypeTransition transition = new Transition.ObjectTypeTransition(dynamicType);
        Shape cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return cachedShape;
        }

        Shape newShape = this.createShapeWithSameSize(dynamicType, sharedData, propertyMap, transition, flags);
        return addDirectTransition(transition, newShape);
    }

    /**
     * Get the root shape.
     *
     * Planned to be deprecated.
     *
     * @since 0.8 or earlier
     */
    public Shape getRoot() {
        return UnsafeAccess.unsafeCast(root, Shape.class, true, true);
    }

    /**
     * Checks whether the given object's shape is identical to this shape.
     *
     * @since 0.8 or earlier
     */
    public boolean check(DynamicObject subject) {
        return subject.getShape() == this;
    }

    /**
     * Get the shape's layout class.
     *
     * @see Shape.Builder#layout(Class, Lookup)
     * @since 21.1
     */
    public Class<? extends DynamicObject> getLayoutClass() {
        return layout.clazz;
    }

    /**
     * Get the shape's shared data.
     *
     * @see Shape.Builder#sharedData(Object)
     * @since 0.8 or earlier
     */
    public Object getSharedData() {
        return sharedData;
    }

    /**
     * Try to merge two related shapes to a more general shape that has the same properties and can
     * store at least the values of both shapes.
     *
     * @return this, other, or a new shape that is compatible with both shapes
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    public Shape tryMerge(Shape other) {
        // double-checked locking is safe since isValid() boils down to a volatile field load.
        if (this != other && this.isValid() && other.isValid()) {
            return Obsolescence.tryObsoleteDowncast(this, other);
        }
        return null;
    }

    /**
     * Returns {@code true} if this shape is marked as shared.
     *
     * @see DynamicObjectLibrary#isShared(DynamicObject)
     * @see DynamicObjectLibrary#markShared(DynamicObject)
     * @see Shape.Builder#shared(boolean)
     * @since 0.18
     */
    @Idempotent
    public boolean isShared() {
        return (flags & FLAG_SHARED_SHAPE) != 0;
    }

    /**
     * Make a shared variant of this shape, to allow safe usage of this object between threads.
     * Shared shapes will not reuse storage locations for other fields. In combination with careful
     * synchronization on writes, this can prevent reading out-of-thin-air values.
     * <p>
     * Note: Where possible, avoid using this method and use
     * {@link DynamicObjectLibrary#markShared(DynamicObject)} instead.
     *
     * @return a cached and shared variant of this shape
     * @throws UnsupportedOperationException if this shape is already shared
     * @see #isShared()
     * @see DynamicObjectLibrary#markShared(DynamicObject)
     * @since 0.18
     */
    @TruffleBoundary
    public Shape makeSharedShape() {
        if (isShared()) {
            throw new UnsupportedOperationException("makeSharedShape() can only be called on non-shared shapes.");
        }

        Transition transition = new Transition.ShareShapeTransition();
        Shape cachedShape = queryTransition(transition);
        if (cachedShape != null) {
            return cachedShape;
        }

        Shape newShape = this.createShapeWithSameSize(objectType, sharedData, propertyMap, transition, flags | FLAG_SHARED_SHAPE);
        return addDirectTransition(transition, newShape);
    }

    /**
     * Returns {@code true} if this shape has instance properties (i.e., stored in the object).
     *
     * @since 20.2.0
     */
    @Idempotent
    protected boolean hasInstanceProperties() {
        return (flags & FLAG_HAS_INSTANCE_PROPERTIES) != 0;
    }

    /**
     * Gets a stable property assumption for the given property key. May be invalid. If a valid
     * assumption is returned, it may be used to assume this particular property is still absent or
     * present at the current storage location in objects of this shape. The assumption is
     * invalidated if a shape change is triggered because of a property with the given key was
     * added, removed, or changed, or a {@link DynamicObjectLibrary#resetShape resetShape}.
     *
     * <p>
     * Only applicable if {@linkplain Builder#propertyAssumptions(boolean) property assumptions} are
     * enabled for this shape, otherwise always returns an invalid assumption.
     *
     * @param key the property key of interest
     * @return an assumption that the property is stable or an invalid assumption
     * @see Shape.Builder#propertyAssumptions(boolean)
     * @since 20.2.0
     */
    @TruffleBoundary
    public Assumption getPropertyAssumption(Object key) {
        if (allowPropertyAssumptions()) {
            // Deny new property assumptions from being made if shape is already obsolete.
            if (!this.isValid()) {
                return Assumption.NEVER_VALID;
            }
            Assumption propertyAssumption = getOrCreatePropertyAssumptions().getPropertyAssumption(key);
            if (propertyAssumption != null && propertyAssumption.isValid()) {
                return propertyAssumption;
            }
        }
        return Assumption.NEVER_VALID;
    }

    /**
     * Tests if all properties in the shape match the provided predicate. May not evaluate the
     * predicate on all properties if a predicate did not match. If the shape does not contain any
     * properties, returns {@code true} and does not evaluate the predicate.
     *
     * @return {@code true} if the all properties match the predicate, else {@code false}
     * @since 20.2.0
     */
    @TruffleBoundary
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

    boolean testPropertyFlags(IntPredicate predicate) {
        for (Property p : getProperties()) {
            if (predicate.test(p.getFlags())) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Makes a property getter for this shape and the given property key, if it exists. Otherwise,
     * returns {@code null}.
     *
     * Note that the returned {@link PropertyGetter} only accepts objects of this particular
     * {@link Shape}.
     *
     * @param key the identifier to look up
     * @return a {@link PropertyGetter}, or {@code null} if the property was not found in this shape
     * @since 22.2
     */
    @TruffleBoundary
    public PropertyGetter makePropertyGetter(Object key) {
        Property property = getProperty(key);
        if (property == null) {
            return null;
        }
        return new PropertyGetter(this, property);
    }

    /**
     * Private constructor.
     *
     * @param parent predecessor shape
     * @param transitionFromParent direct transition from parent shape
     */
    @SuppressWarnings("this-escape")
    private Shape(LayoutImpl layout, Shape parent, Object objectType, Object sharedData, PropertyMap propertyMap, int flags, Transition transitionFromParent,
                    int objectArraySize, int objectFieldSize, int primitiveArraySize, int primitiveFieldSize, Assumption singleContextAssumption) {
        this.layout = layout;
        this.objectType = Objects.requireNonNull(objectType);
        this.propertyMap = Objects.requireNonNull(propertyMap);
        this.root = parent != null ? parent.getRoot() : this;
        this.parent = parent;

        this.objectArraySize = objectArraySize;
        this.objectArrayCapacity = capacityFromSize(objectArraySize);
        this.primitiveArraySize = primitiveArraySize;
        this.primitiveArrayCapacity = capacityFromSize(primitiveArraySize);
        assert objectFieldSize <= ExtLocations.MAX_DYNAMIC_FIELDS && primitiveFieldSize <= ExtLocations.MAX_DYNAMIC_FIELDS;
        this.objectFieldSize = (short) objectFieldSize;
        this.primitiveFieldSize = (short) primitiveFieldSize;

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

    Shape(LayoutImpl layout, Object dynamicType, Object sharedData, int flags, Assumption constantObjectAssumption) {
        this(layout, null, dynamicType, sharedData, PropertyMap.empty(), flags, null, 0, 0, 0, 0, constantObjectAssumption);
    }

    @SuppressWarnings("hiding")
    Shape createShape(Object dynamicType, Object sharedData, PropertyMap propertyMap,
                    Transition transition, BaseAllocator allocator, int flags) {
        return new Shape(this.getLayout(), this, dynamicType, sharedData, propertyMap, flags, transition,
                        allocator.objectArraySize, allocator.objectFieldSize, allocator.primitiveArraySize, allocator.primitiveFieldSize, null);
    }

    @SuppressWarnings("hiding")
    Shape createShapeWithSameSize(Object dynamicType, Object sharedData, PropertyMap propertyMap, Transition transition, int flags) {
        assert !(transition instanceof Transition.PropertyTransition) : transition;
        return new Shape(this.getLayout(), this, dynamicType, sharedData, propertyMap, flags, transition,
                        this.objectArraySize, this.objectFieldSize, this.primitiveArraySize, this.primitiveFieldSize, null);
    }

    private static int makePropertyCount(Shape parent, PropertyMap propertyMap, Transition transitionFromParent) {
        int thisSize = propertyMap.size();
        int parentSize = parent.propertyMap.size();
        if (thisSize > parentSize) {
            Property lastProperty = propertyMap.getLastProperty();
            if (!lastProperty.isHidden()) {
                return parent.propertyCount + 1;
            }
        } else if (thisSize < parentSize && transitionFromParent instanceof Transition.RemovePropertyTransition) {
            if (!(((Transition.RemovePropertyTransition) transitionFromParent).getPropertyKey() instanceof HiddenKey)) {
                return parent.propertyCount - 1;
            }
        }
        return parent.propertyCount;
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

    int getObjectArraySize() {
        return objectArraySize;
    }

    int getObjectFieldSize() {
        return objectFieldSize;
    }

    int getPrimitiveFieldSize() {
        return primitiveFieldSize;
    }

    int getObjectArrayCapacity() {
        return objectArrayCapacity;
    }

    int getPrimitiveArrayCapacity() {
        return primitiveArrayCapacity;
    }

    int getPrimitiveArraySize() {
        return primitiveArraySize;
    }

    boolean hasPrimitiveArray() {
        return getLayout().hasPrimitiveExtensionArray();
    }

    PropertyMap getPropertyMap() {
        return propertyMap;
    }

    Shape addDirectTransition(Transition transition, Shape next) {
        return addTransitionIfAbsentOrGet(transition, next);
    }

    Shape addIndirectTransition(Transition transition, Shape next) {
        return addTransitionIfAbsentOrGet(transition, next);
    }

    Shape addTransitionIfAbsentOrGet(Transition transition, Shape successor) {
        Shape existing = addTransitionIfAbsentOrNull(transition, successor);
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
    Shape addTransitionIfAbsentOrNull(Transition transition, Shape successor) {
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
                StrongKeyWeakValueEntry<Object, Shape> entry = asSingleEntry(prev);
                Transition existingTransition;
                Shape existingSuccessor = entry.getValue();
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
                Shape existingSuccessor = addToTransitionMap(transition, successor, asTransitionMap(prev));
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

    private static Object newTransitionMap(Transition firstTransition, Shape firstShape, Transition secondTransition, Shape secondShape) {
        TransitionMap<Transition, Shape> map = newTransitionMap();
        addToTransitionMap(firstTransition, firstShape, map);
        addToTransitionMap(secondTransition, secondShape, map);
        return map;
    }

    private static Shape addToTransitionMap(Transition transition, Shape successor, TransitionMap<Transition, Shape> map) {
        if (transition.isWeak()) {
            return map.putWeakKeyIfAbsent(transition, successor);
        } else {
            return map.putIfAbsent(transition, successor);
        }
    }

    private static TransitionMap<Transition, Shape> newTransitionMap() {
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
    private static TransitionMap<Transition, Shape> asTransitionMap(Object map) {
        return (TransitionMap<Transition, Shape>) map;
    }

    private static boolean isTransitionMap(Object trans) {
        return trans instanceof TransitionMap<?, ?>;
    }

    private static Object newSingleEntry(Transition transition, Shape successor) {
        transitionSingleEntriesCreated.inc();
        Object key = transition;
        if (transition.isWeak()) {
            key = new WeakKey<>(transition);
        }
        return new StrongKeyWeakValueEntry<>(key, successor);
    }

    private static boolean isSingleEntry(Object trans) {
        return trans instanceof StrongKeyWeakValueEntry;
    }

    @SuppressWarnings("unchecked")
    private static StrongKeyWeakValueEntry<Object, Shape> asSingleEntry(Object trans) {
        return (StrongKeyWeakValueEntry<Object, Shape>) trans;
    }

    void forEachTransition(BiConsumer<Transition, Shape> consumer) {
        Object trans = transitionMap;
        if (trans == null) {
            return;
        } else if (isSingleEntry(trans)) {
            StrongKeyWeakValueEntry<Object, Shape> entry = asSingleEntry(trans);
            Shape shape = entry.getValue();
            if (shape != null) {
                Transition key = unwrapKey(entry.getKey());
                if (key != null) {
                    consumer.accept(key, shape);
                }
            }
        } else {
            assert isTransitionMap(trans);
            TransitionMap<Transition, Shape> map = asTransitionMap(trans);
            map.forEach(consumer);
        }
    }

    private Shape queryTransitionImpl(Transition transition) {
        Object trans = transitionMap;
        if (trans == null) {
            return null;
        } else if (isSingleEntry(trans)) {
            StrongKeyWeakValueEntry<Object, Shape> entry = asSingleEntry(trans);
            Shape shape = entry.getValue();
            if (shape != null) {
                Transition key = unwrapKey(entry.getKey());
                if (key != null && transition.equals(key)) {
                    return shape;
                }
            }
            return null;
        } else {
            assert isTransitionMap(trans);
            TransitionMap<Transition, Shape> map = asTransitionMap(trans);
            return map.get(transition);
        }
    }

    Shape queryTransition(Transition transition) {
        Shape cachedShape = queryTransitionImpl(transition);
        if (cachedShape != null) {
            shapeCacheHitCount.inc();
            return cachedShape;
        }
        shapeCacheMissCount.inc();

        return null;
    }

    void onPropertyTransition(Transition.PropertyTransition propertyTransition) {
        if (allowPropertyAssumptions()) {
            invalidatePropertyAssumption(propertyTransition.getPropertyKey(), propertyTransition.isDirect());
        }
    }

    private void invalidatePropertyAssumption(Object propertyKey, boolean onlyExisting) {
        PropertyAssumptions propertyAssumptions = onlyExisting
                        ? getPropertyAssumptions()
                        : getOrCreatePropertyAssumptions();
        if (propertyAssumptions != null) {
            propertyAssumptions.invalidatePropertyAssumption(propertyKey, onlyExisting);
        }
    }

    Transition getTransitionFromParent() {
        return transitionFromParent;
    }

    /**
     * Create a new shape that adds a property to the parent shape.
     *
     */
    static Shape makeShapeWithAddedProperty(Shape parent, Transition.AddPropertyTransition addTransition) {
        Property addend = addTransition.getProperty();
        var allocator = parent.allocator().addLocation(addend.getLocation());

        PropertyMap newPropertyMap = parent.propertyMap.putCopy(addend);

        Shape newShape = parent.createShape(parent.objectType, parent.sharedData, newPropertyMap, addTransition, allocator, parent.flags);
        assert newShape.hasPrimitiveArray() || ((LocationImpl) addend.getLocation()).primitiveArrayCount() == 0;
        assert newShape.depth == allocator.depth;
        return newShape;
    }

    /**
     * Are these two shapes related, i.e. do they have the same root?
     *
     * @param other Shape to compare to
     * @return true if one shape is an upcast of the other, or the Shapes are equal
     */
    boolean isRelated(Shape other) {
        if (this == other) {
            return true;
        }
        return this.getRoot() == other.getRoot();
    }

    private static Assumption createValidAssumption() {
        return Truffle.getRuntime().createAssumption("valid shape");
    }

    void invalidateValidAssumption() {
        getValidAssumption().invalidate();
    }

    private static Assumption createLeafAssumption() {
        return Truffle.getRuntime().createAssumption("leaf shape");
    }

    @TruffleBoundary
    void invalidateLeafAssumption() {
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

    /**
     * {@return a string representation of the object}
     * 
     * @since 26.0
     */
    @Override
    public String toString() {
        return toStringLimit(Integer.MAX_VALUE);
    }

    @TruffleBoundary
    String toStringLimit(int limit) {
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

    Shape getParent() {
        return parent;
    }

    int getDepth() {
        return depth;
    }

    BaseAllocator allocator() {
        return getLayoutStrategy().createAllocator(this);
    }

    /**
     * Find lowest common ancestor of two related shapes.
     *
     */
    static Shape findCommonAncestor(Shape left, Shape right) {
        if (!left.isRelated(right)) {
            throw new IllegalArgumentException("shapes must have the same root");
        } else if (left == right) {
            return left;
        }
        int leftLength = left.depth;
        int rightLength = right.depth;
        Shape leftPtr = left;
        Shape rightPtr = right;
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
     * Find difference between two shapes.
     */
    static List<Property> diff(Shape oldShape, Shape newShape) {
        List<Property> oldList = oldShape.getPropertyListInternal(false);
        List<Property> newList = newShape.getPropertyListInternal(false);

        List<Property> diff = new ArrayList<>(oldList);
        diff.addAll(newList);
        List<Property> intersection = new ArrayList<>(oldList);
        intersection.retainAll(newList);
        diff.removeAll(intersection);
        return diff;
    }

    LayoutImpl getLayout() {
        return layout;
    }

    LayoutStrategy getLayoutStrategy() {
        return getLayout().getStrategy();
    }

    Object getSharedDataInternal() {
        return sharedData;
    }

    boolean allowPropertyAssumptions() {
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
    void invalidateAllPropertyAssumptions() {
        assert allowPropertyAssumptions();
        PropertyAssumptions propertyAssumptions = getPropertyAssumptions();
        if (propertyAssumptions != null) {
            propertyAssumptions.invalidateAllPropertyAssumptions();
        }
    }

    Assumption getSingleContextAssumption() {
        PropertyAssumptions propertyAssumptions = getPropertyAssumptions();
        if (propertyAssumptions != null) {
            return propertyAssumptions.getSingleContextAssumption();
        }
        return null;
    }

    Object getMutex() {
        return getRoot();
    }

    void setSuccessorShape(Shape successorShape) {
        this.successorShape = successorShape;
    }

    Shape getSuccessorShape() {
        return successorShape;
    }

    void addPredecessorShape(Shape nextShape) {
        Object prev;
        Object next;
        do {
            prev = predecessorShape;
            if (prev == null) {
                next = nextShape;
            } else if (prev instanceof Shape prevShape) {
                if (prevShape == nextShape) {
                    break;
                }
                next = new Shape[]{prevShape, nextShape};
            } else {
                Shape[] prevArray = (Shape[]) prev;
                for (Shape prevShape : prevArray) {
                    if (prevShape == nextShape) {
                        break;
                    }
                }
                Shape[] nextArray = Arrays.copyOf(prevArray, prevArray.length + 1);
                nextArray[prevArray.length] = nextShape;
                next = nextArray;
            }
        } while (!PREDECESSOR_SHAPE_UPDATER.compareAndSet(this, prev, next));
    }

    private static int getObjectFlags(int flags) {
        return ((flags & OBJECT_FLAGS_MASK) >>> OBJECT_FLAGS_SHIFT);
    }

    private static int checkObjectFlags(int flags) {
        if ((flags & ~OBJECT_FLAGS_MASK) != 0) {
            throw new IllegalArgumentException("flags must be in the range [0, 0xffff]");
        }
        return flags;
    }

}

final class PropertyAssumptions {
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

    synchronized void invalidatePropertyAssumption(Object propertyName, boolean onlyExisting) {
        CompilerAsserts.neverPartOfCompilation();
        EconomicMap<Object, Assumption> map = stablePropertyAssumptions;
        Assumption assumption = map.get(propertyName);
        if (assumption == Assumption.NEVER_VALID) {
            return;
        }
        if (assumption != null) {
            assumption.invalidate("invalidatePropertyAssumption");
        }
        /*
         * Direct property transitions can happen only once per object as they always lead to new
         * shapes, so we only need to invalidate already registered assumptions.
         *
         * Indirect property transitions, OTOH, can form transition cycles in the shape tree that
         * may cause toggling between existing shapes for the same object, and since already cached
         * shape transitions fly under the radar of future property assumptions, we have to block
         * any future assumptions from being registered for this property.
         */
        if (assumption != null || !onlyExisting) {
            map.put(propertyName, Assumption.NEVER_VALID);
            if (assumption != null) {
                propertyAssumptionsRemoved.inc();
            } else {
                propertyAssumptionsBlocked.inc();
            }
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
