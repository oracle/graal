/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GeneratePackagePrivate;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.impl.AbstractAssumption;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibraryImpl.RemovePlan;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

import sun.misc.Unsafe;

/**
 * Represents a dynamic object, members of which can be dynamically added and removed at run time.
 *
 * To use it, extend {@link DynamicObject} and use nodes nested under DynamicObject such as
 * {@link DynamicObject.GetNode} for object accesses.
 *
 * When {@linkplain DynamicObject#DynamicObject(Shape) constructing} a {@link DynamicObject}, it has
 * to be initialized with an empty initial shape. Initial shapes are created using
 * {@link Shape#newBuilder()} and should ideally be shared per {@link TruffleLanguage} instance to
 * allow shape caches to be shared across contexts.
 *
 * Subclasses can provide in-object dynamic field slots using the {@link DynamicField} annotation
 * and {@link Shape.Builder#layout(Class, Lookup) Shape.Builder.layout}.
 *
 * <p>
 * Example:
 *
 * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
 * "com.oracle.truffle.api.object.DynamicObjectSnippets.MyObject"}
 *
 * <h2>Using DynamicObject nodes</h2>
 *
 * DynamicObject nodes is the central interface for accessing and mutating properties and other
 * state (flags, dynamic type) of {@link DynamicObject}s. All nodes provide cached and uncached
 * variants.
 *
 * <p>
 * Note: Property keys are always compared using object identity ({@code ==}), never with {@code
 * equals}, for efficiency reasons. If the node is not used with a fixed key, and some keys might be
 * {@code equals} but not have the same identity ({@code ==}), you must either intern the keys
 * first, or cache the key by equality using an inline cache and use the cached key with the
 * DynamicObject node to ensure equal keys with different identity will use the same cache entry and
 * not overflow the cache:
 *
 * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
 * "com.oracle.truffle.api.object.DynamicObjectSnippets.GetWithKeyEquals"}
 *
 * <h3>Usage examples:</h3>
 *
 * <h4>Simple use of {@link GetNode} with a pre-interned symbol key.</h4>
 *
 * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
 * "com.oracle.truffle.api.object.DynamicObjectSnippets.GetSimple"}
 *
 * <h4>Implementing InteropLibrary messages using DynamicObject access nodes:</h4>
 *
 * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
 * "com.oracle.truffle.api.object.DynamicObjectSnippets.ReadMember"}
 *
 * Member name equality check omitted for brevity.
 *
 * <h4>Adding extra dynamic fields to a DynamicObject subclass:</h4>
 *
 * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
 * "com.oracle.truffle.api.object.DynamicObjectSnippets.MyObjectWithFields"}
 *
 * @see DynamicObject#DynamicObject(Shape)
 * @see Shape
 * @see Shape#newBuilder()
 * @see DynamicObject.GetNode
 * @see DynamicObject.ContainsKeyNode
 * @see DynamicObject.PutNode
 * @see DynamicObject.GetPropertyNode
 * @see DynamicObject.GetPropertyFlagsNode
 * @see DynamicObject.SetPropertyFlagsNode
 * @see DynamicObject.CopyPropertiesNode
 * @see DynamicObject.GetKeyArrayNode
 * @see DynamicObject.GetPropertyArrayNode
 * @see DynamicObject.RemoveKeyNode
 * @see DynamicObject.PutConstantNode
 * @see DynamicObject.GetDynamicTypeNode
 * @see DynamicObject.SetDynamicTypeNode
 * @see DynamicObject.GetShapeFlagsNode
 * @see DynamicObject.SetShapeFlagsNode
 * @see DynamicObject.UpdateShapeNode
 * @see DynamicObject.ResetShapeNode
 * @see DynamicObject.IsSharedNode
 * @see DynamicObject.MarkSharedNode
 * @since 0.8 or earlier
 */
@SuppressWarnings("deprecation")
public abstract class DynamicObject implements TruffleObject {

    static final Object[] EMPTY_OBJECT_ARRAY = {};
    static final int[] EMPTY_INT_ARRAY = {};

    private static final MethodHandles.Lookup LOOKUP = internalLookup();

    /**
     * Using this annotation, subclasses can define additional dynamic fields to be used by the
     * object layout. Annotated field must be of type {@code Object} or {@code long}, must not be
     * final, and must not have any direct usages.
     *
     * @see Shape.Builder#layout(Class, Lookup)
     * @since 20.2.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    protected @interface DynamicField {
    }

    /** The current shape of the object. */
    private Shape shape;

    /** Object extension array. */
    private Object[] extRef = EMPTY_OBJECT_ARRAY;
    /** Primitive extension array. */
    private int[] extVal = EMPTY_INT_ARRAY;

    /**
     * Constructor for {@link DynamicObject} subclasses. Initializes the object with the provided
     * shape. The shape must have been constructed with a
     * {@linkplain Shape.Builder#layout(Class, Lookup) layout class} assignable from this class
     * (i.e., the concrete subclass, a superclass thereof, including {@link DynamicObject}) and must
     * not have any instance properties (but may have constant properties).
     *
     * <p>
     * Examples:
     *
     * <pre>
     * Shape shape = {@link Shape#newBuilder()}.{@link Shape.Builder#build() build}();
     * DynamicObject myObject = new MyObject(shape);
     * </pre>
     *
     * <pre>
     * Shape shape = {@link Shape#newBuilder()}.{@link Shape.Builder#layout(Class, Lookup) layout}(MyObject.class, MethodHandles.lookup()).{@link Shape.Builder#build() build}();
     * DynamicObject myObject = new MyObject(shape);
     * </pre>
     *
     * @param shape the initial shape of this object
     * @throws IllegalArgumentException if called with an illegal (incompatible) shape
     * @since 19.0
     */
    protected DynamicObject(Shape shape) {
        verifyShape(shape, this.getClass());
        this.shape = shape;
    }

    private static void verifyShape(Shape shape, Class<? extends DynamicObject> subclass) {
        Class<? extends DynamicObject> shapeType = shape.getLayoutClass();
        assert DynamicObject.class.isAssignableFrom(shapeType) : shapeType;
        if (!(shapeType == subclass || shapeType == DynamicObject.class || shapeType.isAssignableFrom(subclass)) ||
                        shape.hasInstanceProperties()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw illegalShape(shape, subclass);
        }
    }

    private static IllegalArgumentException illegalShape(Shape shape, Class<? extends DynamicObject> subclass) {
        Class<? extends DynamicObject> shapeType = shape.getLayoutClass();
        if (!(shapeType == subclass || shapeType == DynamicObject.class || shapeType.isAssignableFrom(subclass))) {
            throw illegalShapeType(shapeType, subclass);
        }
        assert shape.hasInstanceProperties() : shape;
        throw illegalShapeProperties();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private static IllegalArgumentException illegalShapeType(Class<? extends DynamicObject> shapeClass, Class<? extends DynamicObject> thisClass) {
        throw new IllegalArgumentException(String.format("Incompatible shape: layout class (%s) not assignable from this class (%s)", shapeClass.getName(), thisClass.getName()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private static IllegalArgumentException illegalShapeProperties() {
        throw new IllegalArgumentException("Shape must not have instance properties");
    }

    /**
     * Get the object's current shape.
     *
     * @since 0.8 or earlier
     * @see Shape
     */
    @NeverDefault
    public final Shape getShape() {
        return getShapeHelper(shape);
    }

    /**
     * @implNote This method may be intrinsified by the Truffle compiler.
     */
    private static Shape getShapeHelper(Shape shape) {
        return shape;
    }

    /**
     * Set the object's shape.
     */
    final void setShape(Shape shape) {
        assert assertSetShape(shape);
        setShapeHelper(shape, SHAPE_OFFSET);
    }

    private boolean assertSetShape(Shape s) {
        Class<? extends DynamicObject> layoutType = s.getLayoutClass();
        assert layoutType.isInstance(this) : illegalShapeType(layoutType, this.getClass());
        return true;
    }

    /**
     * @implNote This method may be intrinsified by the Truffle compiler.
     *
     * @param shapeOffset Shape field offset
     */
    private void setShapeHelper(Shape shape, long shapeOffset) {
        this.shape = shape;
    }

    /**
     * The {@link #clone()} method is not supported by {@link DynamicObject} at this point in time,
     * so it always throws {@link CloneNotSupportedException}, even if the {@link Cloneable}
     * interface is implemented in a subclass.
     *
     * Subclasses may however override this method and create a copy of this object by constructing
     * a new object and copying any properties over manually.
     *
     * @since 20.2.0
     * @throws CloneNotSupportedException
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw cloneNotSupported();
    }

    @TruffleBoundary
    private static CloneNotSupportedException cloneNotSupported() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    final Object[] getObjectStore() {
        return extRef;
    }

    final void setObjectStore(Object[] newArray) {
        extRef = Objects.requireNonNull(newArray);
    }

    final int[] getPrimitiveStore() {
        return extVal;
    }

    final void setPrimitiveStore(int[] newArray) {
        extVal = Objects.requireNonNull(newArray);
    }

    static Class<? extends Annotation> getDynamicFieldAnnotation() {
        return DynamicField.class;
    }

    static Lookup internalLookup() {
        return LOOKUP;
    }

    // NODES

    static final int SHAPE_CACHE_LIMIT = ObjectStorageOptions.CacheLimit;

    /**
     * Gets the value of a property or returns a default value if no such property exists.
     * <p>
     * Specialized return type variants are available for when a primitive result is expected.
     *
     * @see #execute(DynamicObject, Object, Object)
     * @see #executeInt(DynamicObject, Object, Object)
     * @see #executeLong(DynamicObject, Object, Object)
     * @see #executeDouble(DynamicObject, Object, Object)
     * @since 25.1
     */
    @GeneratePackagePrivate
    @GenerateCached(true)
    @GenerateInline(false)
    @GenerateUncached
    @ImportStatic(DynamicObject.class)
    public abstract static class GetNode extends Node {

        GetNode() {
        }

        /**
         * Gets the value of an existing property or returns the provided default value if no such
         * property exists.
         *
         * <h3>Usage examples:</h3>
         *
         * <h4>Simple use of {@link GetNode} with a pre-interned symbol key.</h4>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.GetSimple"}
         *
         * <h4>Simple use of {@link GetNode} with a string key cached by equality.</h4>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.GetWithKeyEquals"}
         *
         * @param key the property key, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @param defaultValue value to be returned if the property does not exist
         * @return the property's value if it exists, else {@code defaultValue}.
         * @since 25.1
         */
        public abstract Object execute(DynamicObject receiver, Object key, Object defaultValue);

        /**
         * Gets the value of an existing property or returns the provided default value if no such
         * property exists.
         *
         * @param key the property key, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @param defaultValue the value to be returned if the property does not exist
         * @return the property's value if it exists, else {@code defaultValue}.
         * @throws UnexpectedResultException if the value (or default value if the property is
         *             missing) is not an {@code int}
         * @see #execute(DynamicObject, Object, Object)
         * @since 25.1
         */
        public abstract int executeInt(DynamicObject receiver, Object key, Object defaultValue) throws UnexpectedResultException;

        /**
         * Gets the value of an existing property or returns the provided default value if no such
         * property exists.
         *
         * @param key the property key, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @param defaultValue the value to be returned if the property does not exist
         * @return the property's value if it exists, else {@code defaultValue}.
         * @throws UnexpectedResultException if the value (or default value if the property is
         *             missing) is not an {@code long}
         * @see #execute(DynamicObject, Object, Object)
         * @since 25.1
         */
        public abstract long executeLong(DynamicObject receiver, Object key, Object defaultValue) throws UnexpectedResultException;

        /**
         * Gets the value of an existing property or returns the provided default value if no such
         * property exists.
         *
         * @param key the property key, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @param defaultValue the value to be returned if the property does not exist
         * @return the property's value if it exists, else {@code defaultValue}.
         * @throws UnexpectedResultException if the value (or default value if the property is
         *             missing) is not an {@code double}
         * @see #execute(DynamicObject, Object, Object)
         * @since 25.1
         */
        public abstract double executeDouble(DynamicObject receiver, Object key, Object defaultValue) throws UnexpectedResultException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"guard", "key == cachedKey"}, limit = "SHAPE_CACHE_LIMIT", rewriteOn = UnexpectedResultException.class)
        static long doCachedLong(DynamicObject receiver, Object key, Object defaultValue,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Bind("shape == cachedShape") boolean guard,
                        @Cached("key") Object cachedKey,
                        @Cached("cachedShape.getLocation(key)") Location cachedLocation) throws UnexpectedResultException {
            if (cachedLocation != null) {
                return cachedLocation.getLongInternal(receiver, cachedShape, guard);
            } else {
                return Location.expectLong(defaultValue);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"guard", "key == cachedKey"}, limit = "SHAPE_CACHE_LIMIT", rewriteOn = UnexpectedResultException.class)
        static int doCachedInt(DynamicObject receiver, Object key, Object defaultValue,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Bind("shape == cachedShape") boolean guard,
                        @Cached("key") Object cachedKey,
                        @Cached("cachedShape.getLocation(key)") Location cachedLocation) throws UnexpectedResultException {
            if (cachedLocation != null) {
                return cachedLocation.getIntInternal(receiver, cachedShape, guard);
            } else {
                return Location.expectInteger(defaultValue);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"guard", "key == cachedKey"}, limit = "SHAPE_CACHE_LIMIT", rewriteOn = UnexpectedResultException.class)
        static double doCachedDouble(DynamicObject receiver, Object key, Object defaultValue,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Bind("shape == cachedShape") boolean guard,
                        @Cached("key") Object cachedKey,
                        @Cached("cachedShape.getLocation(key)") Location cachedLocation) throws UnexpectedResultException {
            if (cachedLocation != null) {
                return cachedLocation.getDoubleInternal(receiver, cachedShape, guard);
            } else {
                return Location.expectDouble(defaultValue);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"guard", "key == cachedKey"}, limit = "SHAPE_CACHE_LIMIT", replaces = {"doCachedLong", "doCachedInt", "doCachedDouble"})
        static Object doCached(DynamicObject receiver, Object key, Object defaultValue,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Bind("shape == cachedShape") boolean guard,
                        @Cached("key") Object cachedKey,
                        @Cached("cachedShape.getLocation(key)") Location cachedLocation) {
            if (cachedLocation != null) {
                return cachedLocation.getInternal(receiver, cachedShape, guard);
            } else {
                return defaultValue;
            }
        }

        @Specialization(replaces = {"doCachedLong", "doCachedInt", "doCachedDouble", "doCached"}, rewriteOn = UnexpectedResultException.class)
        static long doGenericLong(DynamicObject receiver, Object key, Object defaultValue) throws UnexpectedResultException {
            Shape shape = receiver.getShape();
            Location location = shape.getLocation(key);
            if (location != null) {
                return location.getLongInternal(receiver, shape, false);
            } else {
                return Location.expectLong(defaultValue);
            }
        }

        @Specialization(replaces = {"doCachedLong", "doCachedInt", "doCachedDouble", "doCached"}, rewriteOn = UnexpectedResultException.class)
        static int doGenericInt(DynamicObject receiver, Object key, Object defaultValue) throws UnexpectedResultException {
            Shape shape = receiver.getShape();
            Location location = shape.getLocation(key);
            if (location != null) {
                return location.getIntInternal(receiver, shape, false);
            } else {
                return Location.expectInteger(defaultValue);
            }
        }

        @Specialization(replaces = {"doCachedLong", "doCachedInt", "doCachedDouble", "doCached"}, rewriteOn = UnexpectedResultException.class)
        static double doGenericDouble(DynamicObject receiver, Object key, Object defaultValue) throws UnexpectedResultException {
            Shape shape = receiver.getShape();
            Location location = shape.getLocation(key);
            if (location != null) {
                return location.getDoubleInternal(receiver, shape, false);
            } else {
                return Location.expectDouble(defaultValue);
            }
        }

        @TruffleBoundary
        @Specialization(replaces = {"doCachedLong", "doCachedInt", "doCachedDouble", "doCached", "doGenericLong", "doGenericInt", "doGenericDouble"})
        static Object doGeneric(DynamicObject receiver, Object key, Object defaultValue) {
            Shape shape = receiver.getShape();
            Location location = shape.getLocation(key);
            if (location != null) {
                return location.getInternal(receiver, shape, false);
            } else {
                return defaultValue;
            }
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetNode create() {
            return DynamicObjectFactory.GetNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetNode getUncached() {
            return DynamicObjectFactory.GetNodeGen.getUncached();
        }
    }

    /**
     * Sets the value of an existing property or adds a new property if no such property exists.
     * Additional variants allow setting property flags, only setting the property if it's either
     * absent or present, and setting constant properties stored in the shape.
     *
     * @see #execute(DynamicObject, Object, Object)
     * @see #executeIfAbsent(DynamicObject, Object, Object)
     * @see #executeIfPresent(DynamicObject, Object, Object)
     * @see #executeWithFlags(DynamicObject, Object, Object, int)
     * @see #executeWithFlagsIfAbsent(DynamicObject, Object, Object, int)
     * @see #executeWithFlagsIfPresent(DynamicObject, Object, Object, int)
     * @see PutConstantNode
     * @since 25.1
     */
    @GeneratePackagePrivate
    @ImportStatic(DynamicObject.class)
    @GenerateUncached
    @GenerateCached(true)
    @GenerateInline(false)
    public abstract static class PutNode extends Node {

        PutNode() {
        }

        /**
         * Sets the value of an existing property or adds a new property if no such property exists.
         *
         * A newly added property will have flags 0; flags of existing properties will not be
         * changed. Use {@link #executeWithFlags} to set property flags as well.
         *
         * <h3>Usage examples:</h3>
         *
         * <h4>Simple use of {@link PutNode} with a pre-interned symbol key.</h4>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.PutSimple"}
         *
         * <h4>Simple use of {@link PutNode} with a string key cached by equality.</h4>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.SetWithKeyEquals"}
         *
         * @param key the property key, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @param value the value to be set
         * @see #executeIfPresent (DynamicObject, Object, Object)
         * @see #executeWithFlags (DynamicObject, Object, Object, int)
         */
        @HostCompilerDirectives.InliningRoot
        public final void execute(DynamicObject receiver, Object key, Object value) {
            executeImpl(receiver, key, value, 0, Flags.DEFAULT);
        }

        /**
         * Sets the value of the property if present, otherwise returns {@code false}.
         *
         * @param key property identifier
         * @param value value to be set
         * @return {@code true} if the property was present and the value set, otherwise
         *         {@code false}
         * @see #execute(DynamicObject, Object, Object)
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeIfPresent(DynamicObject receiver, Object key, Object value) {
            return executeImpl(receiver, key, value, 0, Flags.IF_PRESENT);
        }

        /**
         * Sets the value of the property if absent, otherwise returns {@code false}.
         *
         * @param key property identifier
         * @param value value to be set
         * @return {@code true} if the property was absent and the value set, otherwise
         *         {@code false}
         * @see #execute(DynamicObject, Object, Object)
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeIfAbsent(DynamicObject receiver, Object key, Object value) {
            return executeImpl(receiver, key, value, 0, Flags.IF_ABSENT);
        }

        /**
         * Like {@link #execute(DynamicObject, Object, Object)}, but additionally assigns flags to
         * the property. If the property already exists, its flags will be updated before the value
         * is set.
         *
         * @param key property identifier
         * @param value value to be set
         * @param propertyFlags flags to be set
         * @see #execute(DynamicObject, Object, Object)
         */
        @HostCompilerDirectives.InliningRoot
        public final void executeWithFlags(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            executeImpl(receiver, key, value, propertyFlags, Flags.DEFAULT | Flags.UPDATE_FLAGS);
        }

        /**
         * Like {@link #executeIfPresent(DynamicObject, Object, Object)} but also sets property
         * flags.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeWithFlagsIfPresent(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            return executeImpl(receiver, key, value, propertyFlags, Flags.IF_PRESENT | Flags.UPDATE_FLAGS);
        }

        /**
         * Like {@link #executeIfAbsent(DynamicObject, Object, Object)} but also sets property
         * flags.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeWithFlagsIfAbsent(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            return executeImpl(receiver, key, value, propertyFlags, Flags.IF_ABSENT | Flags.UPDATE_FLAGS);
        }

        abstract boolean executeImpl(DynamicObject receiver, Object key, Object value, int propertyFlags, int mode);

        @SuppressWarnings("unused")
        @Specialization(guards = {
                        "guard",
                        "key == cachedKey",
                        "propertyFlagsEqual(propertyFlags, mode, oldShape, newShape, oldProperty, newProperty)",
                        "newLocation == null || newLocation.canStoreValue(value)",
        }, assumptions = "oldShapeValidAssumption", limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Object key, Object value, int propertyFlags, int mode,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape oldShape,
                        @Bind("shape == oldShape") boolean guard,
                        @Cached("key") Object cachedKey,
                        @Cached("oldShape.getProperty(key)") Property oldProperty,
                        @Cached("getNewShape(key, value, propertyFlags, mode, oldProperty, oldShape)") Shape newShape,
                        @Cached("getNewProperty(oldShape, newShape, key, oldProperty)") Property newProperty,
                        @Cached("getLocation(newProperty)") Location newLocation,
                        @Cached("oldShape.getValidAbstractAssumption()") AbstractAssumption oldShapeValidAssumption) {
            CompilerAsserts.partialEvaluationConstant(mode);
            if ((mode & Flags.IF_ABSENT) != 0 && oldProperty != null) {
                return false;
            }
            if ((mode & Flags.IF_PRESENT) != 0 && oldProperty == null) {
                return false;
            }

            newLocation.setInternal(receiver, value, guard, oldShape, newShape);

            if (newShape != oldShape) {
                DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
                maybeUpdateShape(receiver, newShape);
            }
            return true;
        }

        /*
         * This specialization is necessary because we don't want to remove doCached specialization
         * instances with valid shapes. Yet we have to handle obsolete shapes, and we prefer to do
         * that here than inside the doCached method. This also means new shapes being seen can
         * still create new doCached instances, which is important once we see objects with the new
         * non-obsolete shape.
         */
        @Specialization(guards = "!receiver.getShape().isValid()", excludeForUncached = true)
        static boolean doInvalid(DynamicObject receiver, Object key, Object value, int propertyFlags, int mode) {
            return ObsolescenceStrategy.putGeneric(receiver, key, value, propertyFlags, mode);
        }

        @Specialization(replaces = {"doCached", "doInvalid"})
        static boolean doGeneric(DynamicObject receiver, Object key, Object value, int propertyFlags, int mode) {
            return ObsolescenceStrategy.putGeneric(receiver, key, value, propertyFlags, mode);
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static PutNode create() {
            return DynamicObjectFactory.PutNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static PutNode getUncached() {
            return DynamicObjectFactory.PutNodeGen.getUncached();
        }
    }

    /**
     * Sets the value of an existing property or adds a new property if no such property exists.
     * Additional variants allow setting property flags, only setting the property if it's either
     * absent or present, and setting constant properties stored in the shape.
     *
     * @see #execute(DynamicObject, Object, Object)
     * @see #executeIfAbsent(DynamicObject, Object, Object)
     * @see #executeIfPresent(DynamicObject, Object, Object)
     * @see #executeWithFlags(DynamicObject, Object, Object, int)
     * @see #executeWithFlagsIfAbsent(DynamicObject, Object, Object, int)
     * @see #executeWithFlagsIfPresent(DynamicObject, Object, Object, int)
     * @see PutNode
     * @since 25.1
     */
    @GeneratePackagePrivate
    @ImportStatic(DynamicObject.class)
    @GenerateUncached
    @GenerateCached(true)
    @GenerateInline(false)
    public abstract static class PutConstantNode extends Node {

        PutConstantNode() {
        }

        /**
         * Same as {@link #executeWithFlags}, except the property is added with 0 flags, and if the
         * property already exists, its flags will <em>not</em> be updated.
         */
        @HostCompilerDirectives.InliningRoot
        public final void execute(DynamicObject receiver, Object key, Object value) {
            executeImpl(receiver, key, value, 0, Flags.DEFAULT | Flags.CONST);
        }

        /**
         * Like {@link #execute(DynamicObject, Object, Object)} but only if the property is present.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeIfPresent(DynamicObject receiver, Object key, Object value) {
            return executeImpl(receiver, key, value, 0, Flags.IF_PRESENT | Flags.CONST);
        }

        /**
         * Like {@link #execute(DynamicObject, Object, Object)} but only if the property is absent.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeIfAbsent(DynamicObject receiver, Object key, Object value) {
            return executeImpl(receiver, key, value, 0, Flags.IF_ABSENT | Flags.CONST);
        }

        /**
         * Adds a property with a constant value or replaces an existing one. If the property
         * already exists, its flags will be updated.
         *
         * The constant value is stored in the shape rather than the object instance and a new shape
         * will be allocated if it does not already exist.
         *
         * A typical use case for this method is setting the initial default value of a declared,
         * but yet uninitialized, property. This defers storage allocation and type speculation
         * until the first actual value is set.
         *
         * <p>
         * Warning: this method will lead to a shape transition every time a new value is set and
         * should be used sparingly (with at most one constant value per property) since it could
         * cause an excessive amount of shapes to be created.
         * <p>
         * Note: the value is strongly referenced from the shape property map. It should ideally be
         * a value type or light-weight object without any references to guest language objects in
         * order to prevent potential memory leaks from holding onto the Shape in inline caches. The
         * Shape transition itself is weak, so the previous shapes will not hold strongly on the
         * value.
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.PutConstant1"}
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.PutConstant2"}
         *
         * @param key property identifier
         * @param value the constant value to be set
         * @param propertyFlags property flags or 0
         * @see #execute (DynamicObject, Object, Object)
         */
        @HostCompilerDirectives.InliningRoot
        public final void executeWithFlags(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            executeImpl(receiver, key, value, propertyFlags, Flags.DEFAULT | Flags.CONST | Flags.UPDATE_FLAGS);
        }

        /**
         * Like {@link #executeWithFlags(DynamicObject, Object, Object, int)} but only if the
         * property is present.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeWithFlagsIfPresent(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            return executeImpl(receiver, key, value, propertyFlags, Flags.IF_PRESENT | Flags.CONST | Flags.UPDATE_FLAGS);
        }

        /**
         * Like {@link #executeWithFlags(DynamicObject, Object, Object, int)} but only if the
         * property is absent.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeWithFlagsIfAbsent(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            return executeImpl(receiver, key, value, propertyFlags, Flags.IF_ABSENT | Flags.CONST | Flags.UPDATE_FLAGS);
        }

        abstract boolean executeImpl(DynamicObject receiver, Object key, Object value, int propertyFlags, int mode);

        @SuppressWarnings("unused")
        @Specialization(guards = {
                        "guard",
                        "key == cachedKey",
                        "propertyFlagsEqual(propertyFlags, mode, oldShape, newShape, oldProperty, newProperty)",
                        "newProperty == null || newProperty.getLocation().canStoreConstant(value)",
        }, assumptions = "oldShapeValidAssumption", limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Object key, Object value, int propertyFlags, int mode,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape oldShape,
                        @Bind("shape == oldShape") boolean guard,
                        @Cached("key") Object cachedKey,
                        @Cached("oldShape.getProperty(key)") Property oldProperty,
                        @Cached("getNewShape(key, value, propertyFlags, mode, oldProperty, oldShape)") Shape newShape,
                        @Cached("getNewProperty(oldShape, newShape, key, oldProperty)") Property newProperty,
                        @Cached("oldShape.getValidAbstractAssumption()") AbstractAssumption oldShapeValidAssumption) {
            CompilerAsserts.partialEvaluationConstant(mode);
            if ((mode & Flags.IF_ABSENT) != 0 && oldProperty != null) {
                return false;
            }
            if ((mode & Flags.IF_PRESENT) != 0 && oldProperty == null) {
                return false;
            }

            if (newShape != oldShape) {
                DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
                maybeUpdateShape(receiver, newShape);
            }
            return true;
        }

        /*
         * This specialization is necessary because we don't want to remove doCached specialization
         * instances with valid shapes. Yet we have to handle obsolete shapes, and we prefer to do
         * that here than inside the doCached method. This also means new shapes being seen can
         * still create new doCached instances, which is important once we see objects with the new
         * non-obsolete shape.
         */
        @Specialization(guards = "!receiver.getShape().isValid()", excludeForUncached = true)
        static boolean doInvalid(DynamicObject receiver, Object key, Object value, int propertyFlags, int mode) {
            return ObsolescenceStrategy.putGeneric(receiver, key, value, propertyFlags, mode);
        }

        @Specialization(replaces = {"doCached", "doInvalid"})
        static boolean doGeneric(DynamicObject receiver, Object key, Object value, int propertyFlags, int mode) {
            return ObsolescenceStrategy.putGeneric(receiver, key, value, propertyFlags, mode);
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static PutConstantNode create() {
            return DynamicObjectFactory.PutConstantNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static PutConstantNode getUncached() {
            return DynamicObjectFactory.PutConstantNodeGen.getUncached();
        }
    }

    /**
     * {@return true if the cache entry can be used with the passed property flags and mode}
     *
     * <ol>
     * <li>ignore flags => new flags must equal old flags, if any, else passed flags</li>
     * <li>update flags => new flags must equal passed flags</li>
     * </ol>
     */
    static boolean propertyFlagsEqual(int propertyFlags, int mode, Shape oldShape, Shape newShape, Property oldProperty, Property newProperty) {
        if (newProperty == null) {
            assert oldProperty == null;
            return (mode & Flags.IF_PRESENT) != 0;
        }
        return (mode & Flags.UPDATE_FLAGS) == 0
                        ? oldShape == newShape || (oldProperty == null ? propertyFlags : oldProperty.getFlags()) == newProperty.getFlags()
                        : propertyFlags == newProperty.getFlags();
    }

    static Shape getNewShape(Object cachedKey, Object value, int newPropertyFlags, int mode, Property existingProperty, Shape oldShape) {
        if (existingProperty == null) {
            if ((mode & Flags.IF_PRESENT) != 0) {
                return oldShape;
            } else {
                return oldShape.defineProperty(cachedKey, value, newPropertyFlags, mode);
            }
        } else if ((mode & Flags.IF_ABSENT) != 0) {
            return oldShape;
        } else if ((mode & Flags.UPDATE_FLAGS) != 0 && newPropertyFlags != existingProperty.getFlags()) {
            return oldShape.defineProperty(cachedKey, value, newPropertyFlags, mode);
        }
        if (existingProperty.getLocation().canStore(value)) {
            // set existing
            return oldShape;
        } else {
            // generalize
            Shape newShape = oldShape.defineProperty(oldShape, value, existingProperty.getFlags(), mode, existingProperty);
            assert newShape != oldShape;
            return newShape;
        }
    }

    static Property getNewProperty(Shape oldShape, Shape newShape, Object cachedKey, Property oldProperty) {
        if (newShape == oldShape) {
            return oldProperty;
        } else {
            return newShape.getProperty(cachedKey);
        }
    }

    static Location getLocation(Property property) {
        return property == null ? null : property.getLocation();
    }

    /**
     * Copies all properties of a DynamicObject to another, preserving property flags. Does not copy
     * hidden properties.
     *
     * @see #execute(DynamicObject, DynamicObject)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class CopyPropertiesNode extends Node {

        CopyPropertiesNode() {
        }

        /**
         * Copies all properties of a DynamicObject to another, preserving property flags. Does not
         * copy hidden properties.
         *
         * @since 25.1
         */
        public abstract void execute(DynamicObject from, DynamicObject to);

        @ExplodeLoop
        @Specialization(guards = {"from != to", "shape == cachedShape"}, limit = "SHAPE_CACHE_LIMIT")
        static void doCached(DynamicObject from, DynamicObject to,
                        @Bind("from.getShape()") @SuppressWarnings("unused") Shape shape,
                        @Cached("shape") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached(value = "createPropertyGetters(cachedShape)", dimensions = 1) PropertyGetter[] getters,
                        @Cached(value = "createPutNodes(getters)") PutNode[] putNodes) {
            for (int i = 0; i < getters.length; i++) {
                PropertyGetter getter = getters[i];
                putNodes[i].executeWithFlags(to, getter.getKey(), getter.get(from), getter.getFlags());
            }
        }

        @TruffleBoundary
        @Specialization(guards = {"from != to"}, replaces = "doCached")
        static void doGeneric(DynamicObject from, DynamicObject to) {
            Property[] properties = from.getShape().getPropertyArray();
            for (int i = 0; i < properties.length; i++) {
                Property property = properties[i];
                Object value = property.get(from, false);
                PutNode.getUncached().executeWithFlags(to, property.getKey(), value, property.getFlags());
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"from == to"})
        static void doSameObject(DynamicObject from, DynamicObject to) {
            // nothing to do
        }

        static PropertyGetter[] createPropertyGetters(Shape shape) {
            Property[] properties = shape.getPropertyArray();
            PropertyGetter[] getters = new PropertyGetter[properties.length];
            int i = 0;
            for (Property property : properties) {
                getters[i] = shape.makePropertyGetter(property.getKey());
                i++;
            }
            return getters;
        }

        static PutNode[] createPutNodes(PropertyGetter[] getters) {
            PutNode[] putNodes = new PutNode[getters.length];
            for (int i = 0; i < getters.length; i++) {
                putNodes[i] = PutNode.create();
            }
            return putNodes;
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static CopyPropertiesNode create() {
            return DynamicObjectFactory.CopyPropertiesNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static CopyPropertiesNode getUncached() {
            return DynamicObjectFactory.CopyPropertiesNodeGen.getUncached();
        }
    }

    @TruffleBoundary
    static boolean updateShape(DynamicObject object) {
        return ObsolescenceStrategy.updateShape(object);
    }

    static boolean updateShape(DynamicObject object, Shape currentShape) {
        return ObsolescenceStrategy.updateShape(object, currentShape);
    }

    /**
     * Checks if this object contains a property with the given key.
     *
     * @see #execute(DynamicObject, Object)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class ContainsKeyNode extends Node {

        ContainsKeyNode() {
        }

        /**
         * Returns {@code true} if this object contains a property with the given key.
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.ContainsKey"}
         *
         * Member name equality check omitted for brevity.
         *
         * @param key the property key, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @return {@code true} if the object contains a property with this key, else {@code false}
         */
        public abstract boolean execute(DynamicObject receiver, Object key);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == cachedShape", "key == cachedKey"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Object key,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Cached("key") Object cachedKey,
                        @Cached("cachedShape.hasProperty(cachedKey)") boolean cachedResult) {
            return cachedResult;
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, Object key) {
            Shape shape = receiver.getShape();
            return shape.hasProperty(key);
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static ContainsKeyNode create() {
            return DynamicObjectFactory.ContainsKeyNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static ContainsKeyNode getUncached() {
            return DynamicObjectFactory.ContainsKeyNodeGen.getUncached();
        }
    }

    /**
     * Removes the property with the given key from the object.
     *
     * @see #execute(DynamicObject, Object)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class RemoveKeyNode extends Node {

        RemoveKeyNode() {
        }

        /**
         * Removes the property with the given key from the object.
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.RemoveKey"}
         *
         * Member name equality check omitted for brevity.
         *
         * @param key the property key, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @return {@code true} if the property was removed or {@code false} if property was not
         *         found
         */
        public abstract boolean execute(DynamicObject receiver, Object key);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == oldShape", "key == cachedKey"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Object key,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape oldShape,
                        @Cached("key") Object cachedKey,
                        @Cached("oldShape.getProperty(cachedKey)") Property cachedProperty,
                        @Cached("removeProperty(oldShape, cachedProperty)") Shape newShape,
                        @Cached("makeRemovePlanOrNull(oldShape, newShape, cachedProperty)") RemovePlan removePlan) {
            if (cachedProperty == null) {
                // nothing to do
                return false;
            }
            if (oldShape.isValid()) {
                if (newShape != oldShape) {
                    if (!oldShape.isShared()) {
                        removePlan.execute(receiver);
                        maybeUpdateShape(receiver, newShape);
                    } else {
                        DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
                    }
                }
            } else {
                removePropertyGeneric(receiver, oldShape, cachedProperty);
            }
            return true;
        }

        @TruffleBoundary
        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, Object key) {
            Shape oldShape = receiver.getShape();
            Property existingProperty = oldShape.getProperty(key);
            if (existingProperty == null) {
                return false;
            }
            removePropertyGeneric(receiver, oldShape, existingProperty);
            return true;
        }

        @TruffleBoundary
        static boolean removePropertyGeneric(DynamicObject receiver, Shape cachedShape, Property cachedProperty) {
            updateShape(receiver, cachedShape);
            Shape oldShape = receiver.getShape();
            Property existingProperty = reusePropertyLookup(cachedShape, cachedProperty, oldShape);

            Map<Object, Object> archive = null;
            assert (archive = DynamicObjectSupport.archive(receiver)) != null;

            Shape newShape = oldShape.removeProperty(existingProperty);
            assert oldShape != newShape;
            assert receiver.getShape() == oldShape;

            if (!oldShape.isShared()) {
                RemovePlan plan = DynamicObjectLibraryImpl.prepareRemove(oldShape, newShape, existingProperty);
                plan.execute(receiver);
            } else {
                DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
            }

            assert DynamicObjectSupport.verifyValues(receiver, archive);
            maybeUpdateShape(receiver, newShape);
            return true;
        }

        static Shape removeProperty(Shape shape, Property cachedProperty) {
            CompilerAsserts.neverPartOfCompilation();
            if (cachedProperty == null) {
                return shape;
            }
            return shape.removeProperty(cachedProperty);
        }

        static RemovePlan makeRemovePlanOrNull(Shape oldShape, Shape newShape, Property removedProperty) {
            if (oldShape.isShared() || oldShape == newShape) {
                return null;
            } else {
                return DynamicObjectLibraryImpl.prepareRemove(oldShape, newShape, removedProperty);
            }
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static RemoveKeyNode create() {
            return DynamicObjectFactory.RemoveKeyNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static RemoveKeyNode getUncached() {
            return DynamicObjectFactory.RemoveKeyNodeGen.getUncached();
        }
    }

    /**
     * Gets the language-specific object shape flags.
     *
     * @see #execute(DynamicObject)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class GetShapeFlagsNode extends Node {

        GetShapeFlagsNode() {
        }

        /**
         * Gets the language-specific object shape flags previously set using
         * {@link SetShapeFlagsNode} or {@link Shape.Builder#shapeFlags(int)}. If no shape flags
         * were explicitly set, the default of 0 is returned.
         *
         * These flags may be used to tag objects that possess characteristics that need to be
         * queried efficiently on fast and slow paths. For example, they can be used to mark objects
         * as frozen.
         *
         * <h3>Usage example:</h3>
         *
         * <h4>Implementing frozen object check in writeMember:</h4>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.WriteMember"}
         *
         * Member name equality check omitted for brevity.
         *
         * @return shape flags
         * @see SetShapeFlagsNode
         * @see Shape.Builder#shapeFlags(int)
         * @see Shape#getFlags()
         */
        public abstract int execute(DynamicObject receiver);

        @SuppressWarnings("unused")
        @Specialization(guards = "shape == cachedShape", limit = "1")
        static int doCached(DynamicObject receiver,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape) {
            return cachedShape.getFlags();
        }

        @Specialization(replaces = "doCached")
        static int doGeneric(DynamicObject receiver) {
            return receiver.getShape().getFlags();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetShapeFlagsNode create() {
            return DynamicObjectFactory.GetShapeFlagsNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetShapeFlagsNode getUncached() {
            return DynamicObjectFactory.GetShapeFlagsNodeGen.getUncached();
        }
    }

    /**
     * Sets or updates language-specific object shape flags.
     *
     * @see #execute(DynamicObject, int)
     * @see #executeAdd(DynamicObject, int)
     * @see #executeRemove(DynamicObject, int)
     * @see #executeRemoveAndAdd(DynamicObject, int, int)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class SetShapeFlagsNode extends Node {

        SetShapeFlagsNode() {
        }

        /**
         * Sets language-specific object shape flags, changing the object's shape if need be.
         *
         * These flags may be used to tag objects that possess characteristics that need to be
         * queried efficiently on fast and slow paths. For example, they can be used to mark objects
         * as frozen.
         *
         * Only the lowest 16 bits (i.e. values in the range 0 to 65535) are allowed, the remaining
         * bits are currently reserved.
         *
         * <h3>Usage example:</h3>
         *
         * <h4>Implementing a freeze object operation:</h4>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.SetShapeFlags"}
         *
         * Note that {@link #executeAdd(DynamicObject, int)} is more efficient and convenient for
         * that particular pattern.
         *
         * @param newFlags the flags to set; must be in the range from 0 to 65535 (inclusive).
         * @return {@code true} if the object's shape changed, {@code false} if no change was made.
         * @throws IllegalArgumentException if the flags are not in the allowed range.
         * @see GetShapeFlagsNode
         * @see #executeAdd(DynamicObject, int)
         * @see Shape.Builder#shapeFlags(int)
         * @since 25.1
         */
        public final boolean execute(DynamicObject receiver, int newFlags) {
            return execute(receiver, 0, newFlags);
        }

        /**
         * Adds language-specific object shape flags, changing the object's shape if need be.
         *
         * <h3>Usage example:</h3>
         *
         * <h4>Implementing a freeze object operation:</h4>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.AddShapeFlags"}
         *
         * @see #execute(DynamicObject, int)
         * @since 25.1
         */
        public final boolean executeAdd(DynamicObject receiver, int addedFlags) {
            return execute(receiver, ~0, addedFlags);
        }

        /**
         * Removes language-specific object shape flags, changing the object's shape if need be.
         *
         * @see #execute(DynamicObject, int)
         * @since 25.1
         */
        public final boolean executeRemove(DynamicObject receiver, int removedFlags) {
            return execute(receiver, ~removedFlags, 0);
        }

        /**
         * Removes, then adds language-specific object shape flags, changing the object's shape if
         * need be.
         *
         * @see #execute(DynamicObject, int)
         * @since 25.1
         */
        public final boolean executeRemoveAndAdd(DynamicObject receiver, int removedFlags, int addedFlags) {
            return execute(receiver, ~removedFlags, addedFlags);
        }

        abstract boolean execute(DynamicObject receiver, int andFlags, int orFlags);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == cachedShape", "newFlags == newShape.getFlags()"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, int andFlags, int orFlags,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Bind("computeFlags(cachedShape, andFlags, orFlags)") int newFlags,
                        @Cached("shapeSetFlags(cachedShape, newFlags)") Shape newShape) {
            if (newShape != cachedShape) {
                receiver.setShape(newShape);
                return true;
            } else {
                return false;
            }
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, int andFlags, int orFlags,
                        @Bind("receiver.getShape()") Shape shape) {
            Shape newShape = shapeSetFlags(shape, computeFlags(shape, andFlags, orFlags));
            if (newShape != shape) {
                receiver.setShape(newShape);
                return true;
            } else {
                return false;
            }
        }

        static Shape shapeSetFlags(Shape shape, int newFlags) {
            return shape.setFlags(newFlags);
        }

        static int computeFlags(Shape shape, int and, int or) {
            return (shape.getFlags() & and) | or;
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static SetShapeFlagsNode create() {
            return DynamicObjectFactory.SetShapeFlagsNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static SetShapeFlagsNode getUncached() {
            return DynamicObjectFactory.SetShapeFlagsNodeGen.getUncached();
        }
    }

    /**
     * Checks whether this object is marked as shared.
     *
     * @see #execute(DynamicObject)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class IsSharedNode extends Node {

        IsSharedNode() {
        }

        /**
         * Checks whether this object is marked as shared.
         *
         * @return {@code true} if the object is shared
         * @see MarkSharedNode
         * @see Shape#isShared()
         */
        public abstract boolean execute(DynamicObject receiver);

        @SuppressWarnings("unused")
        @Specialization(guards = "shape == cachedShape", limit = "1")
        static boolean doCached(DynamicObject receiver,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape) {
            return cachedShape.isShared();
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver) {
            return receiver.getShape().isShared();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static IsSharedNode create() {
            return DynamicObjectFactory.IsSharedNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static IsSharedNode getUncached() {
            return DynamicObjectFactory.IsSharedNodeGen.getUncached();
        }
    }

    /**
     * Marks this object as shared.
     *
     * @see #execute(DynamicObject)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class MarkSharedNode extends Node {

        MarkSharedNode() {
        }

        /**
         * Marks this object as shared.
         * <p>
         * Makes the object use a shared variant of the {@link Shape}, to allow safe usage of this
         * object between threads. Objects with a shared {@link Shape} will not reuse storage
         * locations for other fields. In combination with careful synchronization on writes, this
         * can prevent reading out-of-thin-air values.
         *
         * @throws UnsupportedOperationException if the object is already {@link IsSharedNode
         *             shared}.
         * @see IsSharedNode
         */
        public abstract boolean execute(DynamicObject receiver);

        @SuppressWarnings("unused")
        @Specialization(guards = "shape == cachedShape", limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Cached("cachedShape.makeSharedShape()") Shape newShape) {
            if (newShape != cachedShape) {
                receiver.setShape(newShape);
                return true;
            } else {
                return false;
            }
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver,
                        @Bind("receiver.getShape()") Shape shape) {
            Shape newShape = shape.makeSharedShape();
            if (newShape != shape) {
                receiver.setShape(newShape);
                return true;
            } else {
                return false;
            }
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static MarkSharedNode create() {
            return DynamicObjectFactory.MarkSharedNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static MarkSharedNode getUncached() {
            return DynamicObjectFactory.MarkSharedNodeGen.getUncached();
        }
    }

    /**
     * Gets the language-specific dynamic type identifier currently associated with this object.
     *
     * @see #execute(DynamicObject)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class GetDynamicTypeNode extends Node {

        GetDynamicTypeNode() {
        }

        /**
         * Gets the dynamic type identifier currently associated with this object. What this type
         * represents is completely up to the language. For example, it could be a guest-language
         * class.
         *
         * @return the object type
         * @see DynamicObject.SetDynamicTypeNode
         */
        public abstract Object execute(DynamicObject receiver);

        @SuppressWarnings("unused")
        @Specialization(guards = "shape == cachedShape", limit = "1")
        static Object doCached(DynamicObject receiver,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape) {
            return cachedShape.getDynamicType();
        }

        @Specialization(replaces = "doCached")
        static Object doGeneric(DynamicObject receiver) {
            return receiver.getShape().getDynamicType();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetDynamicTypeNode create() {
            return DynamicObjectFactory.GetDynamicTypeNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetDynamicTypeNode getUncached() {
            return DynamicObjectFactory.GetDynamicTypeNodeGen.getUncached();
        }
    }

    /**
     * Sets the language-specific dynamic type identifier.
     *
     * @see #execute(DynamicObject, Object)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class SetDynamicTypeNode extends Node {

        SetDynamicTypeNode() {
        }

        /**
         * Sets the object's dynamic type identifier. What this type represents is completely up to
         * the language. For example, it could be a guest-language class.
         *
         * The type object is strongly referenced from the shape. It should ideally be a singleton
         * or light-weight object without any references to guest language objects in order to keep
         * the memory footprint low and prevent potential memory leaks from holding onto the Shape
         * in inline caches. The Shape transition itself is weak, so the previous shapes will not
         * hold strongly on the type object.
         *
         * Type objects are always compared by object identity, never {@code equals}.
         *
         * @param type a non-null type identifier defined by the guest language.
         * @return {@code true} if the type (and the object's shape) changed
         * @see DynamicObject.GetDynamicTypeNode
         */
        public abstract boolean execute(DynamicObject receiver, Object type);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == cachedShape", "objectType == newObjectType"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Object objectType,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Cached("objectType") Object newObjectType,
                        @Cached("cachedShape.setDynamicType(newObjectType)") Shape newShape) {
            if (newShape != cachedShape) {
                receiver.setShape(newShape);
                return true;
            } else {
                return false;
            }
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, Object objectType,
                        @Bind("receiver.getShape()") Shape shape) {
            Shape newShape = shape.setDynamicType(objectType);
            if (newShape != shape) {
                receiver.setShape(newShape);
                return true;
            } else {
                return false;
            }
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static SetDynamicTypeNode create() {
            return DynamicObjectFactory.SetDynamicTypeNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static SetDynamicTypeNode getUncached() {
            return DynamicObjectFactory.SetDynamicTypeNodeGen.getUncached();
        }
    }

    /**
     * Gets the property flags associated with the requested property key.
     *
     * @see #execute(DynamicObject, Object, int)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class GetPropertyFlagsNode extends Node {

        GetPropertyFlagsNode() {
        }

        /**
         * Gets the property flags associated with the requested property key. Returns the
         * {@code defaultValue} if the object contains no such property. If the property exists but
         * no flags were explicitly set, returns the default of 0.
         *
         * <p>
         * Convenience method equivalent to:
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.GetPropertyEquivalent"}
         *
         * <h3>Usage example:</h3>
         *
         * <h4>Implementing read-only property check in writeMember:</h4>
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.WriteMember"}
         *
         * Member name equality check omitted for brevity.
         *
         * @param key the property key, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @param defaultValue value to return if no such property exists
         * @return the property flags if the property exists, else {@code defaultValue}
         * @see GetPropertyNode
         */
        public abstract int execute(DynamicObject receiver, Object key, int defaultValue);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == cachedShape", "key == cachedKey"}, limit = "SHAPE_CACHE_LIMIT")
        static int doCached(DynamicObject receiver, Object key, int defaultValue,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Cached("key") Object cachedKey,
                        @Cached("cachedShape.getProperty(cachedKey)") Property cachedProperty) {
            return cachedProperty != null ? cachedProperty.getFlags() : defaultValue;
        }

        @Specialization(replaces = "doCached")
        final int doGeneric(DynamicObject receiver, Object key, int defaultValue,
                        @Cached InlinedConditionProfile isPropertyNonNull) {
            Property property = receiver.getShape().getProperty(key);
            return isPropertyNonNull.profile(this, property != null) ? property.getFlags() : defaultValue;
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetPropertyFlagsNode create() {
            return DynamicObjectFactory.GetPropertyFlagsNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetPropertyFlagsNode getUncached() {
            return DynamicObjectFactory.GetPropertyFlagsNodeGen.getUncached();
        }
    }

    /**
     * Sets or updates property flags associated with the requested property key.
     *
     * @see #execute(DynamicObject, Object, int)
     * @see #executeAdd(DynamicObject, Object, int)
     * @see #executeRemove(DynamicObject, Object, int)
     * @see #executeRemoveAndAdd(DynamicObject, Object, int, int)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class SetPropertyFlagsNode extends Node {

        SetPropertyFlagsNode() {
        }

        /**
         * Sets the property flags associated with the requested property.
         *
         * @param key the property key, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @return {@code true} if the property was found and its flags were changed, else
         *         {@code false}
         * @since 25.1
         */
        public final boolean execute(DynamicObject receiver, Object key, int propertyFlags) {
            return execute(receiver, key, 0, propertyFlags);
        }

        /**
         * Adds property flags associated with the requested property.
         *
         * @see #execute(DynamicObject, Object, int)
         * @since 25.1
         */
        public final boolean executeAdd(DynamicObject receiver, Object key, int addedFlags) {
            return execute(receiver, key, ~0, addedFlags);
        }

        /**
         * Removes (clears) property flags associated with the requested property.
         *
         * @see #execute(DynamicObject, Object, int)
         * @since 25.1
         */
        public final boolean executeRemove(DynamicObject receiver, Object key, int removedFlags) {
            return execute(receiver, key, ~removedFlags, 0);
        }

        /**
         * Removes, then adds property flags associated with the requested property.
         *
         * @see #execute(DynamicObject, Object, int)
         * @since 25.1
         */
        public final boolean executeRemoveAndAdd(DynamicObject receiver, Object key, int removedFlags, int addedFlags) {
            return execute(receiver, key, ~removedFlags, addedFlags);
        }

        abstract boolean execute(DynamicObject receiver, Object key, int andFlags, int orFlags);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == oldShape", "key == cachedKey", "propertyFlags == cachedPropertyFlags"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Object key, int andFlags, int orFlags,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape oldShape,
                        @Cached("key") Object cachedKey,
                        @Cached("oldShape.getProperty(cachedKey)") Property cachedProperty,
                        @Bind("computeFlags(cachedProperty, andFlags, orFlags)") int propertyFlags,
                        @Cached("propertyFlags") int cachedPropertyFlags,
                        @Cached("setPropertyFlags(oldShape, cachedProperty, cachedPropertyFlags)") Shape newShape) {
            if (cachedProperty == null) {
                return false;
            }
            if (cachedProperty.getFlags() != cachedPropertyFlags) {
                if (oldShape.isValid()) {
                    if (newShape != oldShape) {
                        DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
                        maybeUpdateShape(receiver, newShape);
                    }
                } else {
                    changePropertyFlagsGeneric(receiver, oldShape, cachedProperty, cachedPropertyFlags);
                }
                return true;
            }
            return false;
        }

        @TruffleBoundary
        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, Object key, int andFlags, int orFlags) {
            Shape oldShape = receiver.getShape();
            Property existingProperty = oldShape.getProperty(key);
            if (existingProperty == null) {
                return false;
            }
            int propertyFlags = computeFlags(existingProperty, andFlags, orFlags);
            if (existingProperty.getFlags() != propertyFlags) {
                changePropertyFlagsGeneric(receiver, oldShape, existingProperty, propertyFlags);
                return true;
            }
            return false;
        }

        @TruffleBoundary
        private static void changePropertyFlagsGeneric(DynamicObject receiver, Shape cachedShape, Property cachedProperty, int propertyFlags) {
            assert cachedProperty != null;
            assert cachedProperty.getFlags() != propertyFlags;

            updateShape(receiver, cachedShape);

            Shape oldShape = receiver.getShape();
            final Property existingProperty = reusePropertyLookup(cachedShape, cachedProperty, oldShape);
            Shape newShape = oldShape.setPropertyFlags(existingProperty, propertyFlags);
            if (newShape != oldShape) {
                DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
                updateShape(receiver, newShape);
            }
        }

        static Shape setPropertyFlags(Shape shape, Property cachedProperty, int propertyFlags) {
            CompilerAsserts.neverPartOfCompilation();
            if (cachedProperty == null) {
                return shape;
            }
            return shape.setPropertyFlags(cachedProperty, propertyFlags);
        }

        static int computeFlags(Property property, int and, int or) {
            if (property == null) {
                return 0;
            }
            return (property.getFlags() & and) | or;
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static SetPropertyFlagsNode create() {
            return DynamicObjectFactory.SetPropertyFlagsNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static SetPropertyFlagsNode getUncached() {
            return DynamicObjectFactory.SetPropertyFlagsNodeGen.getUncached();
        }
    }

    static Property reusePropertyLookup(Shape cachedShape, Property cachedProperty, Shape updatedShape) {
        if (updatedShape == cachedShape) {
            return cachedProperty;
        } else {
            return updatedShape.getProperty(cachedProperty.getKey());
        }
    }

    static void maybeUpdateShape(DynamicObject store, Shape newShape) {
        CompilerAsserts.partialEvaluationConstant(newShape);
        if (!newShape.isValid()) {
            updateShape(store, newShape);
        }
    }

    /**
     * Ensures the object's shape is up-to-date.
     *
     * @see #execute(DynamicObject)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class UpdateShapeNode extends Node {

        UpdateShapeNode() {
        }

        /**
         * Ensures the object's shape is up-to-date. If the object's shape has been marked as
         * {@link Shape#isValid() invalid}, this method will attempt to bring the object into a
         * valid shape again. If the object's shape is already {@link Shape#isValid() valid}, this
         * method will have no effect.
         * <p>
         * This method does not need to be called normally; all the messages in this library will
         * work on invalid shapes as well, but it can be useful in some cases to avoid such shapes
         * being cached which can cause unnecessary cache polymorphism and invalidations.
         *
         * @return {@code true} if the object's shape was changed, otherwise {@code false}.
         */
        public abstract boolean execute(DynamicObject receiver);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == cachedShape", "cachedShape.isValid()"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCachedValid(DynamicObject receiver,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape) {
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "shape.isValid()", replaces = "doCachedValid")
        static boolean doGenericValid(DynamicObject receiver,
                        @Bind("receiver.getShape()") Shape shape) {
            return false;
        }

        @Fallback
        static boolean doInvalid(DynamicObject receiver) {
            return updateShape(receiver);
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static UpdateShapeNode create() {
            return DynamicObjectFactory.UpdateShapeNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static UpdateShapeNode getUncached() {
            return DynamicObjectFactory.UpdateShapeNodeGen.getUncached();
        }
    }

    /**
     * Empties and resets the object to the given root shape.
     *
     * @see #execute(DynamicObject, Shape)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class ResetShapeNode extends Node {

        ResetShapeNode() {
        }

        /**
         * Empties and resets the object to the given root shape, which must not contain any
         * instance properties (but may contain properties with a constant value).
         *
         * @param newShape the desired shape
         * @return {@code true} if the object's shape was changed
         * @throws IllegalArgumentException if the shape contains instance properties
         */
        public abstract boolean execute(DynamicObject receiver, Shape newShape);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == cachedShape", "otherShape == cachedOtherShape"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Shape otherShape,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Cached("verifyResetShape(cachedShape, otherShape)") Shape cachedOtherShape) {
            return doGeneric(receiver, cachedOtherShape, cachedShape);
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, Shape otherShape,
                        @Bind("receiver.getShape()") Shape shape) {
            verifyResetShape(shape, otherShape);
            if (shape == otherShape) {
                return false;
            }
            DynamicObjectSupport.resize(receiver, shape, otherShape);
            DynamicObjectSupport.setShapeWithStoreFence(receiver, otherShape);
            return true;
        }

        static Shape verifyResetShape(Shape currentShape, Shape otherShape) {
            if (otherShape.hasInstanceProperties()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("Shape must not contain any instance properties.");
            }
            if (currentShape != otherShape) {
                DynamicObjectSupport.invalidateAllPropertyAssumptions(currentShape);
            }
            return otherShape;
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static ResetShapeNode create() {
            return DynamicObjectFactory.ResetShapeNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static ResetShapeNode getUncached() {
            return DynamicObjectFactory.ResetShapeNodeGen.getUncached();
        }
    }

    /**
     * Gets a {@linkplain Property property descriptor} for the requested property key or
     * {@code null} if no such property exists.
     *
     * @see #execute(DynamicObject, Object)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class GetPropertyNode extends Node {

        GetPropertyNode() {
        }

        /**
         * Gets a {@linkplain Property property descriptor} for the requested property key. Returns
         * {@code null} if the object contains no such property.
         *
         * @return {@link Property} if the property exists, else {@code null}
         */
        public abstract Property execute(DynamicObject receiver, Object key);

        @Specialization(guards = {"shape == cachedShape", "key == cachedKey"}, limit = "SHAPE_CACHE_LIMIT")
        static Property doCached(@SuppressWarnings("unused") DynamicObject receiver, @SuppressWarnings("unused") Object key,
                        @Bind("receiver.getShape()") @SuppressWarnings("unused") Shape shape,
                        @Cached("shape") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached("key") @SuppressWarnings("unused") Object cachedKey,
                        @Cached("cachedShape.getProperty(cachedKey)") Property cachedProperty) {
            return cachedProperty;
        }

        @Specialization(replaces = "doCached")
        static Property doGeneric(DynamicObject receiver, Object key) {
            return receiver.getShape().getProperty(key);
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetPropertyNode create() {
            return DynamicObjectFactory.GetPropertyNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetPropertyNode getUncached() {
            return DynamicObjectFactory.GetPropertyNodeGen.getUncached();
        }
    }

    /**
     * Gets a snapshot of the object's property keys, in insertion order.
     *
     * @see #execute(DynamicObject)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class GetKeyArrayNode extends Node {

        GetKeyArrayNode() {
        }

        /**
         * Gets a snapshot of the object's property keys, in insertion order. The returned array may
         * have been cached and must not be mutated.
         *
         * Properties with a {@link HiddenKey} are not included.
         *
         * <h3>Usage example:</h3>
         *
         * The example below shows how the returned keys array could be translated to an interop
         * array for use with InteropLibrary.
         *
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.GetMembers"}
         *
         * @return a read-only array of the object's property keys. Do not modify.
         */
        public abstract Object[] execute(DynamicObject receiver);

        @SuppressWarnings("unused")
        @Specialization(guards = "receiver.getShape() == cachedShape", limit = "SHAPE_CACHE_LIMIT")
        static Object[] doCached(DynamicObject receiver,
                        @Cached("receiver.getShape()") Shape cachedShape,
                        @Cached(value = "getKeyArray(cachedShape)", dimensions = 1) Object[] cachedKeyArray) {
            return cachedKeyArray;
        }

        @Specialization(replaces = "doCached")
        static Object[] getKeyArray(DynamicObject receiver) {
            return getKeyArray(receiver.getShape());
        }

        static Object[] getKeyArray(Shape shape) {
            return shape.getKeyArray();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetKeyArrayNode create() {
            return DynamicObjectFactory.GetKeyArrayNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetKeyArrayNode getUncached() {
            return DynamicObjectFactory.GetKeyArrayNodeGen.getUncached();
        }
    }

    /**
     * Gets a snapshot of the object's {@linkplain Property properties}, in insertion order.
     *
     * @see #execute(DynamicObject)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class GetPropertyArrayNode extends Node {

        GetPropertyArrayNode() {
        }

        /**
         * Gets an array snapshot of the object's properties, in insertion order. The returned array
         * may have been cached and must not be mutated.
         *
         * Properties with a {@link HiddenKey} are not included.
         *
         * Similar to {@link GetKeyArrayNode} but allows the properties' flags to be queried
         * simultaneously which may be relevant for quick filtering.
         *
         * @return a read-only array of the object's properties. Do not modify.
         * @see GetKeyArrayNode
         */
        public abstract Property[] execute(DynamicObject receiver);

        @SuppressWarnings("unused")
        @Specialization(guards = "receiver.getShape() == cachedShape", limit = "SHAPE_CACHE_LIMIT")
        static Property[] doCached(DynamicObject receiver,
                        @Cached("receiver.getShape()") Shape cachedShape,
                        @Cached(value = "cachedShape.getPropertyArray()", dimensions = 1) Property[] cachedPropertyArray) {
            return cachedPropertyArray;
        }

        @Specialization(replaces = "doCached")
        static Property[] getPropertyArray(DynamicObject receiver) {
            return receiver.getShape().getPropertyArray();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetPropertyArrayNode create() {
            return DynamicObjectFactory.GetPropertyArrayNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static GetPropertyArrayNode getUncached() {
            return DynamicObjectFactory.GetPropertyArrayNodeGen.getUncached();
        }
    }

    /**
     * Adds or sets multiple properties in bulk. Behaves like {@link PutNode}, but is usually more
     * efficient for cases like object initialization where more than a few properties are added at
     * once.
     *
     * @see #execute(DynamicObject, Object[], Object[])
     * @see #executeIfAbsent(DynamicObject, Object[], Object[])
     * @see #executeIfPresent(DynamicObject, Object[], Object[])
     * @see #executeWithFlags(DynamicObject, Object[], Object[], int[])
     * @see #executeWithFlagsIfAbsent(DynamicObject, Object[], Object[], int[])
     * @see #executeWithFlagsIfPresent(DynamicObject, Object[], Object[], int[])
     * @see PutNode
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class PutAllNode extends Node {

        /**
         * Adds multiple properties or sets the values of multiple existing properties at once.
         * <p>
         * Newly added properties will have flags 0; flags of existing properties will not be
         * changed. Use {@link #executeWithFlags} to set property flags as well.
         * <p>
         * Cached property keys are compared by identity ({@code ==}), not equality
         * ({@code equals}).
         *
         * <h3>Usage example:</h3>
         *
         * <h4>Simple use of {@link PutAllNode} to allocate and fill an object with properties.</h4>
         * <p>
         * {@snippet file = "com/oracle/truffle/api/object/DynamicObjectSnippets.java" region =
         * "com.oracle.truffle.api.object.DynamicObjectSnippets.PutAll"}
         *
         * @param keys the property keys, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @param values the values to be set; needs to be the same length as {@code keys}
         * @see #executeIfPresent(DynamicObject, Object[], Object[])
         * @see #executeWithFlags(DynamicObject, Object[], Object[], int[])
         * @since 25.1
         */
        public final void execute(DynamicObject receiver, Object[] keys, Object[] values) {
            executeImpl(receiver, keys, values, null, Flags.DEFAULT);
        }

        /**
         * Like {@link #execute(DynamicObject, Object[], Object[])} but only sets properties that
         * are already present.
         *
         * @see #execute(DynamicObject, Object[], Object[])
         * @since 25.1
         */
        public final void executeIfPresent(DynamicObject receiver, Object[] keys, Object[] values) {
            executeImpl(receiver, keys, values, null, Flags.IF_PRESENT);
        }

        /**
         * Like {@link #execute(DynamicObject, Object[], Object[])} but only adds properties that
         * are absent.
         *
         * @see #execute(DynamicObject, Object[], Object[])
         * @since 25.1
         */
        public final void executeIfAbsent(DynamicObject receiver, Object[] keys, Object[] values) {
            executeImpl(receiver, keys, values, null, Flags.IF_ABSENT);
        }

        /**
         * Like {@link #execute(DynamicObject, Object[], Object[])} but additionally sets property
         * flags. If a property already exists, its flags will be updated before the value is set.
         *
         * @param keys the property keys, compared by identity ({@code ==}), not equality
         *            ({@code equals}). See {@link DynamicObject} for more information.
         * @param values the values to be set; needs to be the same length as {@code keys}
         * @param propertyFlags the property flags to be set; needs to be the same length as keys
         * @since 25.1
         */
        public final void executeWithFlags(DynamicObject receiver, Object[] keys, Object[] values, int[] propertyFlags) {
            executeImpl(receiver, keys, values, propertyFlags, Flags.DEFAULT);
        }

        /**
         * Like {@link #executeIfPresent(DynamicObject, Object[], Object[])} but also sets property
         * flags when a property is present.
         *
         * @see #executeWithFlags(DynamicObject, Object[], Object[], int[])
         * @see #executeIfPresent(DynamicObject, Object[], Object[])
         * @since 25.1
         */
        public final void executeWithFlagsIfPresent(DynamicObject receiver, Object[] keys, Object[] values, int[] propertyFlags) {
            executeImpl(receiver, keys, values, propertyFlags, Flags.IF_PRESENT);
        }

        /**
         * Like {@link #executeIfAbsent(DynamicObject, Object[], Object[])} but also sets property
         * flags when adding a property.
         *
         * @see #executeWithFlags(DynamicObject, Object[], Object[], int[])
         * @see #executeIfAbsent(DynamicObject, Object[], Object[])
         * @since 25.1
         */
        public final void executeWithFlagsIfAbsent(DynamicObject receiver, Object[] keys, Object[] values, int[] propertyFlags) {
            executeImpl(receiver, keys, values, propertyFlags, Flags.IF_ABSENT);
        }

        abstract void executeImpl(DynamicObject receiver, Object[] keys, Object[] values, int[] propertyFlags, int mode);

        @SuppressWarnings({"unused"})
        @Specialization(guards = {
                        "mode == cachedMode",
                        "keysEqual(cachedKeys, keys)",
                        "guard",
                        "plan != null",
                        "canStoreAll(newProperties, values, propertyFlags)"
        }, assumptions = {"oldShapeValidAssumption"}, limit = "SHAPE_CACHE_LIMIT")
        static void doCached(DynamicObject receiver, Object[] keys, Object[] values, int[] propertyFlags, int mode,
                        @Bind Node node,
                        @Cached(value = "keys", dimensions = 1) Object[] cachedKeys,
                        @Cached("mode") int cachedMode,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape oldShape,
                        @Bind("shape == oldShape") boolean guard,
                        @Cached(value = "getPropertiesOrNull(oldShape, cachedKeys)", dimensions = 1) Property[] oldProperties,
                        @Cached("preparePutAll(keys, values, mode, propertyFlags, oldShape, oldProperties)") PutAllPlan plan,
                        @Bind("plan.newShape()") Shape newShape,
                        @Bind("plan.newProperties()") Property[] newProperties,
                        @Cached("oldShape.getValidAbstractAssumption()") AbstractAssumption oldShapeValidAssumption) {
            assert keys.length == values.length && (propertyFlags == null || propertyFlags.length == keys.length) : "arrays must have the same length";
            performPutAll(receiver, cachedKeys, values, mode, propertyFlags, oldShape, newShape, oldProperties, newProperties);
        }

        /*
         * This specialization is necessary because we don't want to remove doCached specialization
         * instances with valid shapes. Yet we have to handle obsolete shapes, and we prefer to do
         * that here than inside the doCached method. This also means new shapes being seen can
         * still create new doCached instances, which is important once we see objects with the new
         * non-obsolete shape.
         */
        @Specialization(guards = "!receiver.getShape().isValid()", excludeForUncached = true)
        static void doInvalid(DynamicObject receiver, Object[] keys, Object[] values, int[] propertyFlags, int mode) {
            doGeneric(receiver, keys, values, propertyFlags, mode);
        }

        @TruffleBoundary
        @Specialization(replaces = "doCached")
        static void doGeneric(DynamicObject receiver, Object[] keys, Object[] values, int[] propertyFlags, int mode) {
            CompilerAsserts.neverPartOfCompilation();
            assert keys.length == values.length && (propertyFlags == null || propertyFlags.length == keys.length) : "arrays must have the same length";
            updateShape(receiver);
            Shape oldShape = receiver.getShape();
            preparePutAllAndApply(keys, values, mode, propertyFlags, oldShape, null, receiver);
        }

        @CompilerDirectives.ValueType
        record PutAllPlan(Shape newShape,
                        @CompilationFinal(dimensions = 1) Property[] oldProperties,
                        @CompilationFinal(dimensions = 1) Property[] newProperties) {
        }

        static PutAllPlan preparePutAll(Object[] keys, Object[] values, int mode, int[] flags, Shape oldShape, Property[] existingPropertiesOpt) {
            return preparePutAllAndApply(keys, values, mode, flags, oldShape, existingPropertiesOpt, null);
        }

        static PutAllPlan preparePutAllAndApply(Object[] keys, Object[] values, int mode, int[] flags, Shape startShape, Property[] existingPropertiesOpt, DynamicObject object) {
            Shape oldShape = startShape;
            Shape newShape = startShape;
            Property[] oldProperties = existingPropertiesOpt;
            Property[] newProperties = new Property[keys.length];
            boolean updatedShape = false;
            boolean preparing = object == null;
            int i = 0;
            while (i < keys.length) {
                Object key = keys[i];
                Object value = values[i];
                int propertyFlags = flags == null ? 0 : flags[i];
                Property newProperty;
                Property existingProperty;
                if (existingPropertiesOpt != null && !updatedShape) {
                    existingProperty = existingPropertiesOpt[i];
                    assert Objects.equals(existingProperty, newShape.getProperty(key)) : key;
                } else {
                    existingProperty = newShape.getProperty(key);
                }
                if (existingProperty == null) {
                    if (Flags.isPutIfPresent(mode)) {
                        newProperty = null;
                    } else {
                        newShape = newShape.defineProperty(key, value, propertyFlags, mode, null);
                        newProperty = newShape.getProperty(key);
                    }
                } else if (Flags.isPutIfAbsent(mode)) {
                    newProperty = null;
                } else {
                    if (Flags.isUpdateFlags(mode) && propertyFlags != existingProperty.getFlags()) {
                        newShape = newShape.defineProperty(key, value, propertyFlags, mode, existingProperty);
                        newProperty = newShape.getProperty(key);
                    } else {
                        if (existingProperty.getLocation().canStoreValue(value)) {
                            newProperty = existingProperty;
                        } else {
                            assert !Flags.isUpdateFlags(mode) || propertyFlags == existingProperty.getFlags();
                            newShape = newShape.defineProperty(key, value, existingProperty.getFlags(), mode, existingProperty);
                            newProperty = newShape.getProperty(key);
                        }
                    }
                }
                if (!oldShape.isValid()) {
                    if (preparing) {
                        /*
                         * Preparing is not supported for obsolete shapes, since that might require
                         * moving around existing properties. Like PutNode, we don't cache obsolete
                         * shapes and handle them via the generic case.
                         */
                        return null;
                    } else {
                        /*
                         * Must perform shape migration. Since properties may have changed
                         * locations, restart from the beginning with the updated shape.
                         */
                        updateShape(object);
                        updatedShape = true;
                        oldShape = newShape = object.getShape();
                        // restart after shape migration
                        i = 0;
                        continue;
                    }
                }
                newProperties[i] = newProperty;
                if (existingProperty != null && newProperty != null) {
                    // Only allocate the array if not all elements are null.
                    if (oldProperties == null) {
                        oldProperties = new Property[keys.length];
                    }
                    if (oldProperties[i] == null) {
                        oldProperties[i] = existingProperty;
                    } else {
                        assert oldProperties[i].equals(existingProperty);
                    }
                }
                i++;
            }

            if (preparing) {
                return new PutAllPlan(newShape, oldProperties, newProperties);
            } else {
                performPutAll(object, keys, values, mode, flags, oldShape, newShape, oldProperties, newProperties);
                return null; // not used in this case, so skip the allocation
            }
        }

        @ExplodeLoop
        static void performPutAll(DynamicObject receiver, Object[] keys, Object[] values, int mode, int[] pflags,
                        Shape oldShape, Shape newShape, Property[] oldProperties, Property[] newProperties) {
            if (oldShape != newShape) {
                DynamicObjectSupport.grow(receiver, oldShape, newShape);
            }
            CompilerAsserts.partialEvaluationConstant(keys.length);
            assert oldProperties == null || oldProperties.length == newProperties.length;
            for (int i = 0; i < keys.length; i++) {
                Object value = values[i];
                Property property = newProperties[i];
                if (property == null) {
                    /*
                     * A null property implies we're in IfPresent or IfAbsent mode and means there
                     * is nothing to do for this property since the property is absent or present,
                     * respectively.
                     */
                    assert Flags.isPutIfPresent(mode) || Flags.isPutIfAbsent(mode);
                    continue;
                }
                /*
                 * These assertions hold because of cached specialization guards or the
                 * defineProperty contract in the generic case.
                 */
                assert property.getKey().equals(keys[i]) && (property.getFlags() == (pflags == null ? 0 : pflags[i]) || !Flags.isUpdateFlags(mode)) : property;
                assert property.getLocation().canStoreValue(value) : property;
                boolean init = oldProperties == null || oldProperties[i] == null || property.getLocation() != oldProperties[i].getLocation();
                Location location = property.getLocation();
                location.setInternal(receiver, value, false, init);
            }
            if (oldShape != newShape) {
                DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
                maybeUpdateShape(receiver, newShape);
            }
            assert verifyPropertyValues(receiver, keys, values, mode, oldShape, newShape);
        }

        private static boolean verifyPropertyValues(DynamicObject receiver, Object[] keys, Object[] values, int mode, Shape oldShape, Shape newShape) {
            return IntStream.range(0, keys.length).allMatch(i -> {
                Object key = keys[i];
                if (Flags.isPutIfAbsent(mode) && oldShape.getProperty(key) != null) {
                    assert newShape.getProperty(key).equals(oldShape.getProperty(key)) : key;
                    return true;
                }
                if (Flags.isPutIfPresent(mode) && oldShape.getProperty(key) == null) {
                    assert newShape.getProperty(key) == null : key;
                    return true;
                }
                Object newValue = GetNode.getUncached().execute(receiver, key, null);
                assert Objects.equals(values[i], newValue) : "key=" + key + " expectedValue=" + values[i] + " actualValue=" + newValue;
                return true;
            });
        }

        @ExplodeLoop
        static boolean keysEqual(Object[] cachedKeys, Object[] keys) {
            CompilerAsserts.partialEvaluationConstant(cachedKeys.length);
            for (int i = 0; i < cachedKeys.length; i++) {
                if (cachedKeys[i] != keys[i]) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Looks up multiple properties at once. Returns null if none of properties are present.
         */
        static Property[] getPropertiesOrNull(Shape cachedShape, Object[] keys) {
            if (cachedShape.getPropertyMap().isEmpty()) {
                return null;
            }
            Property[] properties = null;
            for (int i = 0; i < keys.length; i++) {
                Property property = cachedShape.getProperty(keys[i]);
                if (property == null) {
                    continue;
                }
                if (properties == null) {
                    properties = new Property[keys.length];
                }
                properties[i] = property;
            }
            return properties;
        }

        /**
         * Checks if the cached properties can store all values and property flags (if any).
         */
        @ExplodeLoop
        static boolean canStoreAll(Property[] properties, Object[] values, int[] propertyFlags) {
            CompilerAsserts.partialEvaluationConstant(properties.length);
            if (values.length != properties.length) {
                return false;
            }
            for (int i = 0; i < properties.length; i++) {
                Property property = properties[i];
                if (property == null) {
                    continue;
                }
                if (!property.getLocation().canStoreValue(values[i]) ||
                                !(propertyFlags == null || property.getFlags() == propertyFlags[i])) {
                    return false;
                }
            }
            return true;
        }

        @NeverDefault
        public static PutAllNode getUncached() {
            return DynamicObjectFactory.PutAllNodeGen.getUncached();
        }

        @NeverDefault
        public static PutAllNode create() {
            return DynamicObjectFactory.PutAllNodeGen.create();
        }
    }

    private static final Unsafe UNSAFE;
    private static final long SHAPE_OFFSET;
    static {
        UNSAFE = getUnsafe();
        try {
            SHAPE_OFFSET = getObjectFieldOffset(DynamicObject.class.getDeclaredField("shape"));
        } catch (Exception e) {
            throw new IllegalStateException("Could not get 'shape' field offset", e);
        }
    }

    @SuppressWarnings("deprecation" /* JDK-8277863 */)
    private static long getObjectFieldOffset(Field field) {
        return UNSAFE.objectFieldOffset(field);
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }
}
