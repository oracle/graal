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
package com.oracle.truffle.api.object;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Layout.ImplicitCast;
import com.oracle.truffle.api.utilities.NeverValidAssumption;

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
    static final int OBJECT_FLAGS_MASK = 0x0000_00ff;
    static final int OBJECT_FLAGS_SHIFT = 0;
    static final int OBJECT_SHARED = 1 << 16;
    static final int OBJECT_PROPERTY_ASSUMPTIONS = 1 << 17;

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
         * @param dynamicType type identifier object; an instance of {@link ObjectType}
         * @throws NullPointerException if the type is {@code null}
         * @throws IllegalArgumentException if the type is not an instance of {@link ObjectType}
         * @since 20.2.0
         */
        public abstract T dynamicType(Object dynamicType);

        /**
         * Sets initial shape flags (default: 0). Currently limited to 8 bits.
         *
         * See {@link DynamicObjectLibrary#setShapeFlags(DynamicObject, int)} for more information.
         *
         * @param flags an int value in the range from 0 to 255 (inclusive)
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
            if (!(dynamicType instanceof ObjectType)) {
                throw new IllegalArgumentException("dynamicType must be an instance of ObjectType");
            }
            return dynamicType;
        }

        static int checkShapeFlags(int flags) {
            if ((flags & ~OBJECT_FLAGS_MASK) != 0) {
                throw new IllegalArgumentException("flags must be in the range (0, 255)");
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
    @SuppressWarnings("hiding")
    public static final class Builder extends AbstractBuilder<Builder> {

        private Class<? extends DynamicObject> layoutClass = DynamicObject.class;
        private Object dynamicType = ObjectType.DEFAULT;
        private int shapeFlags;
        private boolean shared;
        private boolean propertyAssumptions;
        private Object sharedData;
        private Assumption singleContextAssumption;
        private EconomicMap<Object, Property> properties;
        private EnumSet<ImplicitCast> allowedImplicitCasts = EnumSet.noneOf(ImplicitCast.class);

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

            Layout layout = Layout.newLayout().type(DynamicObject.class).build();

            Location location = layout.createAllocator().constantLocation(value);
            properties.put(key, Property.create(key, location, flags));
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
            if (allow) {
                this.allowedImplicitCasts.add(Layout.ImplicitCast.IntToLong);
            } else {
                this.allowedImplicitCasts.remove(Layout.ImplicitCast.IntToLong);
            }
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
            if (allow) {
                this.allowedImplicitCasts.add(Layout.ImplicitCast.IntToDouble);
            } else {
                this.allowedImplicitCasts.remove(Layout.ImplicitCast.IntToDouble);
            }
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

            Layout layout = Layout.newLayout().type(layoutClass).setAllowedImplicitCasts(allowedImplicitCasts).build();

            Shape shape = layout.buildShape(dynamicType, sharedData, flags, singleContextAssumption);

            if (properties != null) {
                for (Property property : properties.getValues()) {
                    shape = shape.addProperty(property);
                }
            }

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
        private EconomicMap<Object, Property> properties;

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
            Location location = baseShape.allocator().constantLocation(value);
            properties.put(key, Property.create(key, location, flags));
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
                for (Property property : properties.getValues()) {
                    derivedShape = derivedShape.addProperty(property);
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
     * Planned to be deprecated. Use {@link DynamicObjectLibrary#put} or
     * {@link DynamicObjectLibrary#putWithFlags} to add properties to an object.
     *
     * @param property the property to add
     * @return the new Shape
     * @since 0.8 or earlier
     */
    public abstract Shape addProperty(Property property);

    /**
     * Add or change property in the map, yielding a new or cached Shape object.
     *
     * Planned to be deprecated. Use {@link DynamicObjectLibrary#put} or
     * {@link DynamicObjectLibrary#putWithFlags} to add properties to an object.
     *
     * @return the shape after defining the property
     * @since 0.8 or earlier
     */
    public abstract Shape defineProperty(Object key, Object value, int flags);

    /**
     * Add or change property in the map, yielding a new or cached Shape object.
     *
     * @return the shape after defining the property
     * @since 0.8 or earlier
     * @deprecated Use {@link #defineProperty(Object, Object, int)} or
     *             {@link DynamicObjectLibrary#put(DynamicObject, Object, Object)} or
     *             {@link DynamicObjectLibrary#putWithFlags(DynamicObject, Object, Object, int)} or
     *             {@link DynamicObjectLibrary#putConstant(DynamicObject, Object, Object, int)}
     */
    @Deprecated
    public abstract Shape defineProperty(Object key, Object value, int flags, LocationFactory locationFactory);

    /**
     * An {@link Iterable} over the shape's properties in insertion order.
     *
     * @since 0.8 or earlier
     */
    public abstract Iterable<Property> getProperties();

    /**
     * Get a list of properties that this Shape stores.
     *
     * @return list of properties
     * @since 0.8 or earlier
     * @deprecated use {@link #getPropertyList()} instead
     */
    @Deprecated
    public abstract List<Property> getPropertyList(Pred<Property> filter);

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
     * Get a filtered list of property keys in insertion order.
     *
     * @since 0.8 or earlier
     * @deprecated use {@link #getKeyList()} instead
     */
    @Deprecated
    public abstract List<Object> getKeyList(Pred<Property> filter);

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
    public abstract Assumption getValidAssumption();

    /**
     * Check whether this shape is valid.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean isValid();

    /**
     * Get an assumption that the shape is a leaf.
     *
     * @since 0.8 or earlier
     */
    public abstract Assumption getLeafAssumption();

    /**
     * Check whether this shape is a leaf in the transition graph, i.e. transitionless.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean isLeaf();

    /**
     * @return the parent shape or {@code null} if none.
     * @since 0.8 or earlier
     * @deprecated no replacement, do not rely on a specific parent shape
     */
    @Deprecated
    public abstract Shape getParent();

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
     * @deprecated Use {@link DynamicObjectLibrary#removeKey} to remove properties from an object.
     */
    @Deprecated
    public abstract Shape removeProperty(Property property);

    /**
     * Replace a property in the shape.
     *
     * @since 0.8 or earlier
     * @deprecated Use {@link DynamicObjectLibrary#put} to replace properties in an object.
     */
    @Deprecated
    public abstract Shape replaceProperty(Property oldProperty, Property newProperty);

    /**
     * Get the last property.
     *
     * @since 0.8 or earlier
     */
    public abstract Property getLastProperty();

    /**
     * @see #getFlags()
     * @since 0.8 or earlier
     * @deprecated no replacement, returns 0
     */
    @Deprecated
    public abstract int getId();

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
    public int getFlags() {
        CompilerAsserts.neverPartOfCompilation();
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Returns a copy of the shape, with the shape flags set to {@code newFlags}.
     *
     * @param newFlags the new shape flags; an int value in the range from 0 to 255 (inclusive)
     * @throws IllegalArgumentException if the flags value is not in the supported range
     * @see Shape.Builder#shapeFlags(int)
     * @since 20.2.0
     */
    protected Shape setFlags(int newFlags) {
        CompilerAsserts.neverPartOfCompilation();
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Append the property, relocating it to the next allocated location.
     *
     * @since 0.8 or earlier
     * @deprecated no replacement
     */
    @Deprecated
    public abstract Shape append(Property oldProperty);

    /**
     * Obtain an {@link Allocator} instance for the purpose of allocating locations.
     *
     * @since 0.8 or earlier
     */
    public abstract Allocator allocator();

    /**
     * Returns the number of properties in this shape.
     *
     * @since 0.8 or earlier
     */
    public abstract int getPropertyCount();

    /**
     * Get the shape's object type info.
     *
     * Planned to be deprecated. To be replaced by {@link #getDynamicType()}.
     *
     * @since 0.8 or earlier
     * @see #getDynamicType()
     */
    public abstract ObjectType getObjectType();

    /**
     * Get the shape's dynamic object type identifier (formerly {@link #getObjectType()}).
     *
     * @since 20.2.0
     */
    public Object getDynamicType() {
        CompilerAsserts.neverPartOfCompilation();
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Returns a copy of the shape, with the dynamic object type identifier set to
     * {@code objectType}. Currently, the object type must be an instance of {@link ObjectType}.
     *
     * @param objectType the new dynamic object type identifier
     * @throws IllegalArgumentException if the type is not an instance of {@link ObjectType}
     * @see Shape.Builder#dynamicType(Object)
     * @since 20.2.0
     */
    protected Shape setDynamicType(Object objectType) {
        CompilerAsserts.neverPartOfCompilation();
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
    public abstract Layout getLayout();

    /**
     * Get the shape's shared data.
     *
     * @see Shape.Builder#sharedData(Object)
     * @since 0.8 or earlier
     */
    public abstract Object getSharedData();

    /**
     * Query whether the shape has a transition with the given key.
     *
     * @since 0.8 or earlier
     * @deprecated the result of this method may change at any time
     */
    @Deprecated
    public abstract boolean hasTransitionWithKey(Object key);

    /**
     * Clone off a separate shape with new shared data.
     *
     * @since 0.8 or earlier
     * @deprecated no replacement
     */
    @Deprecated
    public abstract Shape createSeparateShape(Object sharedData);

    /**
     * Change the shape's type, yielding a new shape.
     *
     * Planned to be deprecated. To be replaced by {@link #setDynamicType(Object)}.
     *
     * @since 0.8 or earlier
     */
    public abstract Shape changeType(ObjectType newOps);

    /**
     * Reserve the primitive extension array field.
     *
     * @since 0.8 or earlier
     * @deprecated It is unnecessary to call this method, it has no effect and always returns this.
     */
    @Deprecated
    public abstract Shape reservePrimitiveExtensionArray();

    /**
     * Create a new {@link DynamicObject} instance with this shape.
     *
     * @throws UnsupportedOperationException if this layout does not support construction
     * @since 0.8 or earlier
     */
    public abstract DynamicObject newInstance();

    /**
     * Create a {@link DynamicObjectFactory} for creating instances of this shape.
     *
     * @throws UnsupportedOperationException if this layout does not support construction
     * @since 0.8 or earlier
     */
    public abstract DynamicObjectFactory createFactory();

    /**
     * Get mutex object shared by related shapes, i.e. shapes with a common root.
     *
     * Planned to be deprecated.
     *
     * @since 0.8 or earlier
     */
    public abstract Object getMutex();

    /**
     * Are these two shapes related, i.e. do they have the same root?
     *
     * @param other Shape to compare to
     * @return true if one shape is an upcast of the other, or the Shapes are equal
     * @since 0.8 or earlier
     * @deprecated no replacement
     */
    @Deprecated
    public abstract boolean isRelated(Shape other);

    /**
     * Try to merge two related shapes to a more general shape that has the same properties and can
     * store at least the values of both shapes.
     *
     * @return this, other, or a new shape that is compatible with both shapes
     * @since 0.8 or earlier
     */
    public abstract Shape tryMerge(Shape other);

    /**
     * Returns {@code true} if this shape is {@link Shape#makeSharedShape() shared}.
     *
     * @see DynamicObjectLibrary#isShared(DynamicObject)
     * @see DynamicObjectLibrary#markShared(DynamicObject)
     * @see Shape.Builder#shared(boolean)
     * @since 0.18
     */
    public boolean isShared() {
        return false;
    }

    /**
     * Make a shared variant of this shape, to allow safe usage of this object between threads.
     * Shared shapes will not reuse storage locations for other fields. In combination with careful
     * synchronization on writes, this can prevent reading out-of-thin-air values.
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
        return NeverValidAssumption.INSTANCE;
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
     * Utility class to allocate locations in an object layout.
     *
     * Planned to be deprecated.
     *
     * @since 0.8 or earlier
     */
    public abstract static class Allocator {
        /**
         * @since 0.8 or earlier
         */
        protected Allocator() {
        }

        /** @since 0.8 or earlier */
        @Deprecated
        protected abstract Location locationForValue(Object value, boolean useFinal, boolean nonNull);

        /**
         * Create a new location compatible with the given initial value.
         *
         * Use {@link #locationForType(Class)} or {@link Shape#defineProperty(Object, Object, int)}
         * instead.
         *
         * @param value the initial value this location is going to be assigned
         * @since 0.8 or earlier
         */
        @Deprecated
        public final Location locationForValue(Object value) {
            return locationForValue(value, false, value != null);
        }

        /**
         * Create a new location compatible with the given initial value.
         *
         * @param value the initial value this location is going to be assigned
         * @param modifiers additional restrictions and semantics
         * @since 0.8 or earlier
         * @deprecated use {@link #locationForType(Class, EnumSet)} or
         *             {@link Shape#defineProperty(Object, Object, int)} instead
         */
        @Deprecated
        public final Location locationForValue(Object value, EnumSet<LocationModifier> modifiers) {
            assert value != null || !modifiers.contains(LocationModifier.NonNull);
            return locationForValue(value, modifiers.contains(LocationModifier.Final), modifiers.contains(LocationModifier.NonNull));
        }

        /** @since 0.8 or earlier */
        protected abstract Location locationForType(Class<?> type, boolean useFinal, boolean nonNull);

        /**
         * Create a new location for a fixed type. It can only be assigned to values of this type.
         *
         * @param type the Java type this location must be compatible with (may be primitive)
         * @since 0.8 or earlier
         */
        public final Location locationForType(Class<?> type) {
            return locationForType(type, false, false);
        }

        /**
         * Create a new location for a fixed type.
         *
         * @param type the Java type this location must be compatible with (may be primitive)
         * @param modifiers additional restrictions and semantics
         * @since 0.8 or earlier
         */
        public final Location locationForType(Class<?> type, EnumSet<LocationModifier> modifiers) {
            return locationForType(type, modifiers.contains(LocationModifier.Final), modifiers.contains(LocationModifier.NonNull));
        }

        /**
         * Creates a new location from a constant value. The value is stored in the shape rather
         * than in the object.
         *
         * @since 0.8 or earlier
         */
        public abstract Location constantLocation(Object value);

        /**
         * Creates a new declared location with a default value. A declared location only assumes a
         * type after the first set (initialization).
         *
         * @since 0.8 or earlier
         */
        public abstract Location declaredLocation(Object value);

        /**
         * Reserves space for the given location, so that it will not be available to subsequently
         * allocated locations.
         *
         * @since 0.8 or earlier
         */
        public abstract Allocator addLocation(Location location);

        /**
         * Creates an copy of this allocator state.
         *
         * @since 0.8 or earlier
         */
        public abstract Allocator copy();
    }

    /**
     * Represents a predicate (boolean-valued function) of one argument.
     *
     * For Java 7 compatibility (equivalent to Predicate).
     *
     * @param <T> the type of the input to the predicate
     * @since 0.8 or earlier
     * @deprecated all methods that use this interface are deprecated; use
     *             {@link java.util.function.Predicate} instead.
     */
    @Deprecated
    public interface Pred<T> {
        /**
         * Evaluates this predicate on the given argument.
         *
         * @param t the input argument
         * @return {@code true} if the input argument matches the predicate, otherwise {@code false}
         * @since 0.8 or earlier
         */
        boolean test(T t);
    }
}
