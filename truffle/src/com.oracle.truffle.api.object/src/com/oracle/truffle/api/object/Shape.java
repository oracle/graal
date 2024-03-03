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
package com.oracle.truffle.api.object;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.Pair;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NonIdempotent;

/**
 * A Shape is an immutable descriptor of the current object "shape" of a DynamicObject, i.e., object
 * layout, metadata ({@linkplain Shape#getDynamicType() type}, {@linkplain Shape#getFlags() flags}),
 * and a mapping of {@linkplain Property properties} to storage locations. This allows cached
 * {@link DynamicObjectLibrary} to do a simple shape check to determine the contents of an object
 * and do fast, constant-time property accesses.
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
public abstract class Shape {
    static final int OBJECT_FLAGS_MASK = 0x0000_ffff;
    static final int OBJECT_FLAGS_SHIFT = 0;
    static final int OBJECT_SHARED = 1 << 16;
    static final int OBJECT_PROPERTY_ASSUMPTIONS = 1 << 17;

    // keep in sync with Flags.java
    static final int PUT_CONSTANT = 1 << 5;

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
         */
        public Builder layout(Class<? extends DynamicObject> layoutClass) {
            CompilerAsserts.neverPartOfCompilation();
            if (!DynamicObject.class.isAssignableFrom(layoutClass)) {
                throw new IllegalArgumentException(String.format("Expected a subclass of %s but got: %s",
                                DynamicObject.class.getName(), layoutClass.getTypeName()));
            }
            this.layoutClass = layoutClass;
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException {@inheritDoc}
         * @throws IllegalArgumentException {@inheritDoc}
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
         * @see #layout(Class)
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
         * @see #layout(Class)
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
                flags = shapeFlags | OBJECT_SHARED;
            }
            if (propertyAssumptions) {
                flags = shapeFlags | OBJECT_PROPERTY_ASSUMPTIONS;
            }

            int implicitCastFlags = (allowImplicitCastIntToDouble ? Layout.INT_TO_DOUBLE_FLAG : 0) | (allowImplicitCastIntToLong ? Layout.INT_TO_LONG_FLAG : 0);
            Shape shape = Layout.getFactory().createShape(new Object[]{layoutClass, implicitCastFlags, dynamicType, sharedData, flags, properties, singleContextAssumption});

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
         * @throws IllegalArgumentException {@inheritDoc}
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
        @SuppressWarnings("deprecation")
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
     * Constructor for subclasses.
     *
     * @since 0.8 or earlier
     */
    protected Shape() {
    }

    /**
     * Get a property entry by key.
     *
     * @param key the identifier to look up
     * @return a Property object, or {@code null} if not found
     * @since 0.8 or earlier
     */
    public abstract Property getProperty(Object key);

    /**
     * Add a new property in the map, yielding a new or cached Shape object.
     *
     * @param property the property to add
     * @return the new Shape
     * @since 0.8 or earlier
     */
    protected abstract Shape addProperty(Property property);

    /**
     * Add or change property in the map, yielding a new or cached Shape object.
     *
     * @return the shape after defining the property
     * @since 0.8 or earlier
     * @deprecated Use {@link DynamicObjectLibrary#put(DynamicObject, Object, Object)} or
     *             {@link DynamicObjectLibrary#putWithFlags(DynamicObject, Object, Object, int)}.
     */
    @Deprecated(since = "22.2")
    public abstract Shape defineProperty(Object key, Object value, int propertyFlags);

    /**
     * Add or change property in the map, yielding a new or cached Shape object.
     *
     * @return the shape after defining the property
     * @since 24.1
     */
    protected abstract Shape defineProperty(Object key, Object value, int propertyFlags, int putFlags);

    /**
     * Add or replace shape-constant property.
     *
     * @return the shape after defining the property
     */
    final Shape defineConstantProperty(Object key, Object value, int propertyFlags) {
        return defineProperty(key, value, propertyFlags, PUT_CONSTANT);
    }

    /**
     * An {@link Iterable} over the shape's properties in insertion order.
     *
     * @since 0.8 or earlier
     */
    public abstract Iterable<Property> getProperties();

    /**
     * Get a list of all properties that this Shape stores.
     *
     * Properties with a {@link HiddenKey} are not included.
     *
     * @return list of properties
     * @since 0.8 or earlier
     */
    public abstract List<Property> getPropertyList();

    /**
     * Returns all (also hidden) property objects in this shape.
     *
     * @param ascending desired order ({@code true} for insertion order, {@code false} for reverse
     *            insertion order)
     * @since 0.8 or earlier
     */
    public abstract List<Property> getPropertyListInternal(boolean ascending);

    /**
     * Get a list of all property keys in insertion order.
     *
     *
     * Properties with a {@link HiddenKey} are not included.
     *
     * @since 0.8 or earlier
     */
    public abstract List<Object> getKeyList();

    /**
     * Get all property keys in insertion order.
     *
     * @since 0.8 or earlier
     */
    public abstract Iterable<Object> getKeys();

    /**
     * Get an assumption that the shape is valid.
     *
     * @since 0.8 or earlier
     */
    @Idempotent
    public abstract Assumption getValidAssumption();

    /**
     * Check whether this shape is valid.
     *
     * @since 0.8 or earlier
     */
    @NonIdempotent
    public abstract boolean isValid();

    /**
     * Get an assumption that the shape is a leaf.
     *
     * @since 0.8 or earlier
     */
    @NonIdempotent
    public abstract Assumption getLeafAssumption();

    /**
     * Check whether this shape is a leaf in the transition graph, i.e. transitionless.
     *
     * @since 0.8 or earlier
     */
    @NonIdempotent
    public abstract boolean isLeaf();

    /**
     * Check whether the shape has a property with the given key.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean hasProperty(Object key);

    /**
     * Remove the given property from the shape.
     *
     * @since 0.8 or earlier
     */
    protected abstract Shape removeProperty(Property property);

    /**
     * Replace a property in the shape.
     *
     * @since 0.8 or earlier
     */
    protected abstract Shape replaceProperty(Property oldProperty, Property newProperty);

    /**
     * Get the last property.
     *
     * @since 0.8 or earlier
     */
    public abstract Property getLastProperty();

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
        CompilerAsserts.neverPartOfCompilation();
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Returns a copy of the shape, with the shape flags set to {@code newFlags}.
     *
     * @param newFlags the new shape flags; an int value in the range from 0 to 65535 (inclusive)
     * @throws IllegalArgumentException if the flags value is not in the supported range
     * @see Shape.Builder#shapeFlags(int)
     * @since 20.2.0
     */
    protected Shape setFlags(int newFlags) {
        CompilerAsserts.neverPartOfCompilation();
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Returns the number of properties in this shape.
     *
     * @since 0.8 or earlier
     */
    public abstract int getPropertyCount();

    /**
     * Get the shape's dynamic object type identifier.
     *
     * @since 20.2.0
     */
    public Object getDynamicType() {
        CompilerAsserts.neverPartOfCompilation();
        throw CompilerDirectives.shouldNotReachHere();
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
    protected Shape setDynamicType(Object dynamicType) {
        CompilerAsserts.neverPartOfCompilation();
        Objects.requireNonNull(dynamicType);
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Get the root shape.
     *
     * Planned to be deprecated.
     *
     * @since 0.8 or earlier
     */
    public abstract Shape getRoot();

    /**
     * Checks whether the given object's shape is identical to this shape.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean check(DynamicObject subject);

    /**
     * Get the shape's layout.
     *
     * @see Shape.Builder#layout(Class)
     * @since 0.8 or earlier
     */
    @SuppressWarnings("deprecation")
    protected abstract Layout getLayout();

    /**
     * Get the shape's layout class.
     *
     * @see Shape.Builder#layout(Class)
     * @since 21.1
     */
    @SuppressWarnings("deprecation")
    public Class<? extends DynamicObject> getLayoutClass() {
        return getLayout().getType();
    }

    /**
     * Get the shape's shared data.
     *
     * @see Shape.Builder#sharedData(Object)
     * @since 0.8 or earlier
     */
    public abstract Object getSharedData();

    /**
     * Try to merge two related shapes to a more general shape that has the same properties and can
     * store at least the values of both shapes.
     *
     * @return this, other, or a new shape that is compatible with both shapes
     * @since 0.8 or earlier
     */
    public abstract Shape tryMerge(Shape other);

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
        return false;
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
     * @see #isShared()
     * @see DynamicObjectLibrary#markShared(DynamicObject)
     * @since 0.18
     */
    public Shape makeSharedShape() {
        CompilerAsserts.neverPartOfCompilation();
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Returns {@code true} if this shape has instance properties (i.e., stored in the object).
     *
     * @since 20.2.0
     */
    @Idempotent
    protected boolean hasInstanceProperties() {
        return true;
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
    public Assumption getPropertyAssumption(Object key) {
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
    public boolean allPropertiesMatch(@SuppressWarnings("unused") Predicate<Property> predicate) {
        CompilerAsserts.neverPartOfCompilation();
        throw CompilerDirectives.shouldNotReachHere();
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
    @CompilerDirectives.TruffleBoundary
    public PropertyGetter makePropertyGetter(Object key) {
        Property property = getProperty(key);
        if (property == null) {
            return null;
        }
        return new PropertyGetter(this, property);
    }
}
