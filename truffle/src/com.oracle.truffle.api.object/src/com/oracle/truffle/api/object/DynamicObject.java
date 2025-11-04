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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
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

// @formatter:off
/**
 * Represents a dynamic object, members of which can be dynamically added and removed at run time.
 *
 * To use it, extend {@link DynamicObject} and use nodes nested under DynamicObject such as {@link DynamicObject.GetNode} for object accesses.
 *
 * When {@linkplain DynamicObject#DynamicObject(Shape) constructing} a {@link DynamicObject}, it has
 * to be initialized with an empty initial shape. Initial shapes are created using
 * {@link Shape#newBuilder()} and should ideally be shared per {@link TruffleLanguage} instance to allow
 * shape caches to be shared across contexts.
 *
 * Subclasses can provide in-object dynamic field slots using the {@link DynamicField} annotation
 * and {@link Shape.Builder#layout(Class, Lookup) Shape.Builder.layout}.
 *
 * <p>
 * Example:
 * {@snippet :
 * public class MyObject extends DynamicObject implements TruffleObject {
 *     public MyObject(Shape shape) {
 *         super(shape);
 *     }
 * }
 *
 * Shape initialShape = Shape.newBuilder().layout(MyObject.class).build();
 *
 * MyObject obj = new MyObject(initialShape);
 * }
 *
 * <h2>General documentation about DynamicObject nodes</h2>
 *
 * DynamicObject nodes is the central interface for accessing and mutating properties and other state (flags,
 * dynamic type) of {@link DynamicObject}s.
 * All nodes provide cached and uncached variants.
 *
 * <p>
 * Property keys are always compared using object identity ({@code ==}), never with {@code equals}.
 * This is because it is far more efficient for host inlining that way, and caching by {@code equals} is only needed in some cases.
 * If some keys might be {@code equals} but not have the same identity ({@code ==}),
 * it can be worthwhile to "intern" the key using an inline cache before using the DynamicObject node:
 * {@snippet :
 * import com.oracle.truffle.api.dsl.Cached.Exclusive;
 * import com.oracle.truffle.api.strings.TruffleString;
 *
 * @Specialization(guards = "equalNode.execute(key, cachedKey, ENCODING)", limit = "3")
 * static Object read(MyDynamicObjectSubclass receiver, TruffleString key,
 *                 @Cached TruffleString.EqualNode equalNode,
 *                 @Cached TruffleString cachedKey,
 *                 @Cached @Exclusive DynamicObject.GetNode getNode) {
 *     return getNode.execute(receiver, cachedKey, NULL_VALUE);
 * }
 * }
 *
 * <h3>Usage examples:</h3>
 *
 * {@snippet :
 * @Specialization(limit = "3")
 * static Object read(MyDynamicObjectSubclass receiver, Object key,
 *                 @Cached DynamicObject.GetNode getNode) {
 *     return getNode.execute(receiver, key, NULL_VALUE);
 * }
 * }
 *
 * {@snippet :
 * @ExportMessage
 * Object readMember(String name,
 *                 @Cached DynamicObject.GetNode getNode) throws UnknownIdentifierException {
 *     Object result = getNode.execute(this, name, null);
 *     if (result == null) {
 *         throw UnknownIdentifierException.create(name);
 *     }
 *     return result;
 * }
 * }
 *
 * @see DynamicObject#DynamicObject(Shape)
 * @see Shape
 * @see Shape#newBuilder()
 * @see DynamicObject.GetNode
 * @see DynamicObject.ContainsKeyNode
 * @see DynamicObject.GetPropertyNode
 * @see DynamicObject.GetPropertyFlagsNode
 * @see DynamicObject.PutNode
 * @see DynamicObject.PutConstantNode
 * @see DynamicObject.GetDynamicTypeNode
 * @see DynamicObject.SetDynamicTypeNode
 * @see DynamicObject.GetShapeFlagsNode
 * @see DynamicObject.SetShapeFlagsNode
 * @see DynamicObject.GetKeyArrayNode
 * @see DynamicObject.GetPropertyArrayNode
 * @see DynamicObject.RemoveKeyNode
 * @see DynamicObject.UpdateShapeNode
 * @see DynamicObject.IsSharedNode
 * @see DynamicObject.MarkSharedNode
 * @since 0.8 or earlier
 */
// @formatter:on
@SuppressWarnings("deprecation")
public abstract class DynamicObject implements TruffleObject {

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
    @DynamicField private Object[] extRef;
    /** Primitive extension array. */
    @DynamicField private int[] extVal;

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
        extRef = newArray;
    }

    final int[] getPrimitiveStore() {
        return extVal;
    }

    final void setPrimitiveStore(int[] newArray) {
        extVal = newArray;
    }

    static Class<? extends Annotation> getDynamicFieldAnnotation() {
        return DynamicField.class;
    }

    static Lookup internalLookup() {
        return LOOKUP;
    }

    // NODES

    static final int SHAPE_CACHE_LIMIT = 5;

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

        // @formatter:off
        /**
         * Gets the value of an existing property or returns the provided default value if no such property exists.
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet :
         * @Specialization(limit = "3")
         * static Object read(DynamicObject receiver, Object key,
         *                 @Cached DynamicObject.GetNode getNode) {
         *     return getNode.execute(receiver, key, NULL_VALUE);
         * }
         * }
         *
         * @param key the property key
         * @param defaultValue value to be returned if the property does not exist
         * @return the property's value if it exists, else {@code defaultValue}.
         * @since 25.1
         */
        // @formatter:on
        public abstract Object execute(DynamicObject receiver, Object key, Object defaultValue);

        /**
         * Gets the value of an existing property or returns the provided default value if no such
         * property exists.
         *
         * @param key the property key
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
         * @param key the property key
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
         * @param key the property key
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
                return cachedLocation.getLong(receiver, guard);
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
                if (cachedLocation instanceof ExtLocations.IntArrayLocation intArrayLocation) {
                    return intArrayLocation.getInt(receiver, guard);
                } else {
                    return cachedLocation.getInt(receiver, guard);
                }
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
                return cachedLocation.getDouble(receiver, guard);
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
                if (cachedLocation instanceof ExtLocations.ObjectArrayLocation objectArrayLocation) {
                    return objectArrayLocation.get(receiver, guard);
                } else if (cachedLocation instanceof ExtLocations.IntArrayLocation intArrayLocation) {
                    return intArrayLocation.get(receiver, guard);
                } else {
                    return cachedLocation.get(receiver, guard);
                }
            } else {
                return defaultValue;
            }
        }

        @Specialization(replaces = {"doCachedLong", "doCachedInt", "doCachedDouble", "doCached"}, rewriteOn = UnexpectedResultException.class)
        static long doGenericLong(DynamicObject receiver, Object key, Object defaultValue) throws UnexpectedResultException {
            Location location = receiver.getShape().getLocation(key);
            if (location != null) {
                return location.getLong(receiver, false);
            } else {
                return Location.expectLong(defaultValue);
            }
        }

        @Specialization(replaces = {"doCachedLong", "doCachedInt", "doCachedDouble", "doCached"}, rewriteOn = UnexpectedResultException.class)
        static int doGenericInt(DynamicObject receiver, Object key, Object defaultValue) throws UnexpectedResultException {
            Location location = receiver.getShape().getLocation(key);
            if (location != null) {
                return location.getInt(receiver, false);
            } else {
                return Location.expectInteger(defaultValue);
            }
        }

        @Specialization(replaces = {"doCachedLong", "doCachedInt", "doCachedDouble", "doCached"}, rewriteOn = UnexpectedResultException.class)
        static double doGenericDouble(DynamicObject receiver, Object key, Object defaultValue) throws UnexpectedResultException {
            Location location = receiver.getShape().getLocation(key);
            if (location != null) {
                return location.getDouble(receiver, false);
            } else {
                return Location.expectDouble(defaultValue);
            }
        }

        @TruffleBoundary
        @Specialization(replaces = {"doCachedLong", "doCachedInt", "doCachedDouble", "doCached", "doGenericLong", "doGenericInt", "doGenericDouble"})
        static Object doGeneric(DynamicObject receiver, Object key, Object defaultValue) {
            Location location = receiver.getShape().getLocation(key);
            if (location != null) {
                return location.get(receiver, false);
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

        // @formatter:off
        /**
         * Sets the value of an existing property or adds a new property if no such property exists.
         *
         * A newly added property will have flags 0; flags of existing properties will not be changed.
         * Use {@link #executeWithFlags} to set property flags as well.
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet :
         * @ExportMessage
         * Object writeMember(String member, Object value,
         *                 @Cached DynamicObject.PutNode putNode) {
         *     putNode.execute(this, member, value);
         * }
         * }
         *
         * @param key the property key
         * @param value the value to be set
         * @see #executeIfPresent (DynamicObject, Object, Object)
         * @see #executeWithFlags (DynamicObject, Object, Object, int)
         */
        // @formatter:on
        @HostCompilerDirectives.InliningRoot
        public final void execute(DynamicObject receiver, Object key, Object value) {
            executeImpl(receiver, key, value, Flags.DEFAULT, 0);
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
            return executeImpl(receiver, key, value, Flags.IF_PRESENT, 0);
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
            return executeImpl(receiver, key, value, Flags.IF_ABSENT, 0);
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
            executeImpl(receiver, key, value, Flags.DEFAULT | Flags.UPDATE_FLAGS, propertyFlags);
        }

        /**
         * Like {@link #executeIfPresent(DynamicObject, Object, Object)} but also sets property
         * flags.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeWithFlagsIfPresent(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            return executeImpl(receiver, key, value, Flags.IF_PRESENT | Flags.UPDATE_FLAGS, propertyFlags);
        }

        /**
         * Like {@link #executeIfAbsent(DynamicObject, Object, Object)} but also sets property
         * flags.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeWithFlagsIfAbsent(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            return executeImpl(receiver, key, value, Flags.IF_ABSENT | Flags.UPDATE_FLAGS, propertyFlags);
        }

        // private

        abstract boolean executeImpl(DynamicObject receiver, Object key, Object value, int mode, int propertyFlags);

        @SuppressWarnings("unused")
        @Specialization(guards = {
                        "guard",
                        "key == cachedKey",
                        "mode == cachedMode",
                        "propertyFlags == cachedPropertyFlags",
                        "newLocation == null || canStore(newLocation, value)",
        }, assumptions = "newShapeValidAssumption", limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Object key, Object value, int mode, int propertyFlags,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape oldShape,
                        @Bind("shape == oldShape") boolean guard,
                        @Cached("key") Object cachedKey,
                        @Cached("mode") int cachedMode,
                        @Cached("propertyFlags") int cachedPropertyFlags,
                        @Cached("oldShape.getProperty(key)") Property oldProperty,
                        @Cached("getNewShapeAndCheckOldShapeStillValid(key, value, cachedPropertyFlags, cachedMode, oldProperty, oldShape)") Shape newShape,
                        @Cached("getNewLocation(oldShape, newShape, key, oldProperty)") Location newLocation,
                        @Cached("newShape.getValidAbstractAssumption()") AbstractAssumption newShapeValidAssumption) {
            // We use mode instead of cachedMode to fold it during host inlining
            CompilerAsserts.partialEvaluationConstant(mode);
            if ((mode & Flags.IF_ABSENT) != 0 && oldProperty != null) {
                return false;
            }
            if ((mode & Flags.IF_PRESENT) != 0 && oldProperty == null) {
                return false;
            } else {
                boolean addingNewProperty = newShape != oldShape;
                if (addingNewProperty) {
                    DynamicObjectSupport.grow(receiver, oldShape, newShape);
                }

                if (newLocation instanceof ExtLocations.ObjectArrayLocation objectArrayLocation) {
                    objectArrayLocation.set(receiver, value, guard, addingNewProperty);
                } else {
                    newLocation.set(receiver, value, guard, addingNewProperty);
                }

                if (addingNewProperty) {
                    DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
                }
                return true;
            }
        }

        /*
         * This specialization is necessary because we don't want to remove doCached specialization
         * instances with valid shapes. Yet we have to handle obsolete shapes, and we prefer to do
         * that here than inside the doCached method. This also means new shapes being seen can
         * still create new doCached instances, which is important once we see objects with the new
         * non-obsolete shape.
         */
        @Specialization(guards = "!receiver.getShape().isValid()")
        static boolean doInvalid(DynamicObject receiver, Object key, Object value, int mode, int propertyFlags) {
            return ObsolescenceStrategy.putGeneric(receiver, key, value, propertyFlags, mode);
        }

        @Specialization(replaces = {"doCached", "doInvalid"})
        static boolean doGeneric(DynamicObject receiver, Object key, Object value, int mode, int propertyFlags) {
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
            executeImpl(receiver, key, value, Flags.DEFAULT | Flags.CONST, 0);
        }

        /**
         * Like {@link #execute(DynamicObject, Object, Object)} but only if the property is present.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeIfPresent(DynamicObject receiver, Object key, Object value) {
            return executeImpl(receiver, key, value, Flags.IF_PRESENT | Flags.CONST, 0);
        }

        /**
         * Like {@link #execute(DynamicObject, Object, Object)} but only if the property is absent.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeIfAbsent(DynamicObject receiver, Object key, Object value) {
            return executeImpl(receiver, key, value, Flags.IF_ABSENT | Flags.CONST, 0);
        }

        // @formatter:off
        /**
         * Adds a property with a constant value or replaces an existing one. If the property already
         * exists, its flags will be updated.
         *
         * The constant value is stored in the shape rather than the object instance and a new shape
         * will be allocated if it does not already exist.
         *
         * A typical use case for this method is setting the initial default value of a declared, but
         * yet uninitialized, property. This defers storage allocation and type speculation until the
         * first actual value is set.
         *
         * <p>
         * Warning: this method will lead to a shape transition every time a new value is set and should
         * be used sparingly (with at most one constant value per property) since it could cause an
         * excessive amount of shapes to be created.
         * <p>
         * Note: the value is strongly referenced from the shape property map. It should ideally be a
         * value type or light-weight object without any references to guest language objects in order
         * to prevent potential memory leaks from holding onto the Shape in inline caches. The Shape
         * transition itself is weak, so the previous shapes will not hold strongly on the value.
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet :
         * // declare property
         * putConstantNode.putConstant(receiver, key, NULL_VALUE);
         *
         * // initialize property
         * putNode.put(receiver, key, value);
         * }
         *
         * @param key property identifier
         * @param value the constant value to be set
         * @param propertyFlags property flags or 0
         * @see #execute (DynamicObject, Object, Object)
         */
        // @formatter:on
        @HostCompilerDirectives.InliningRoot
        public final void executeWithFlags(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            executeImpl(receiver, key, value, Flags.DEFAULT | Flags.CONST | Flags.UPDATE_FLAGS, propertyFlags);
        }

        /**
         * Like {@link #executeWithFlags(DynamicObject, Object, Object, int)} but only if the
         * property is present.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeWithFlagsIfPresent(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            return executeImpl(receiver, key, value, Flags.IF_PRESENT | Flags.CONST | Flags.UPDATE_FLAGS, propertyFlags);
        }

        /**
         * Like {@link #executeWithFlags(DynamicObject, Object, Object, int)} but only if the
         * property is absent.
         */
        @HostCompilerDirectives.InliningRoot
        public final boolean executeWithFlagsIfAbsent(DynamicObject receiver, Object key, Object value, int propertyFlags) {
            return executeImpl(receiver, key, value, Flags.IF_ABSENT | Flags.CONST | Flags.UPDATE_FLAGS, propertyFlags);
        }

        // private

        abstract boolean executeImpl(DynamicObject receiver, Object key, Object value, int mode, int propertyFlags);

        @SuppressWarnings("unused")
        @Specialization(guards = {
                        "guard",
                        "key == cachedKey",
                        "mode == cachedMode",
                        "propertyFlags == cachedPropertyFlags",
                        "newLocation == null || canStore(newLocation, value)",
        }, assumptions = "newShapeValidAssumption", limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Object key, Object value, int mode, int propertyFlags,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape oldShape,
                        @Bind("shape == oldShape") boolean guard,
                        @Cached("key") Object cachedKey,
                        @Cached("mode") int cachedMode,
                        @Cached("propertyFlags") int cachedPropertyFlags,
                        @Cached("oldShape.getProperty(key)") Property oldProperty,
                        @Cached("getNewShapeAndCheckOldShapeStillValid(key, value, cachedPropertyFlags, cachedMode, oldProperty, oldShape)") Shape newShape,
                        @Cached("getNewLocation(oldShape, newShape, key, oldProperty)") Location newLocation,
                        @Cached("newShape.getValidAbstractAssumption()") AbstractAssumption newShapeValidAssumption) {
            // We use mode instead of cachedMode to fold it during host inlining
            CompilerAsserts.partialEvaluationConstant(mode);
            if ((mode & Flags.IF_ABSENT) != 0 && oldProperty != null) {
                return false;
            }
            if ((mode & Flags.IF_PRESENT) != 0 && oldProperty == null) {
                return false;
            } else {
                boolean addingNewProperty = newShape != oldShape;
                if (addingNewProperty) {
                    DynamicObjectSupport.grow(receiver, oldShape, newShape);
                }

                if (newLocation instanceof ExtLocations.ObjectArrayLocation objectArrayLocation) {
                    objectArrayLocation.set(receiver, value, guard, addingNewProperty);
                } else {
                    newLocation.set(receiver, value, guard, addingNewProperty);
                }

                if (addingNewProperty) {
                    DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
                }
                return true;
            }
        }

        /*
         * This specialization is necessary because we don't want to remove doCached specialization
         * instances with valid shapes. Yet we have to handle obsolete shapes, and we prefer to do
         * that here than inside the doCached method. This also means new shapes being seen can
         * still create new doCached instances, which is important once we see objects with the new
         * non-obsolete shape.
         */
        @Specialization(guards = "!receiver.getShape().isValid()")
        static boolean doInvalid(DynamicObject receiver, Object key, Object value, int mode, int propertyFlags) {
            return ObsolescenceStrategy.putGeneric(receiver, key, value, propertyFlags, mode);
        }

        @Specialization(replaces = {"doCached", "doInvalid"})
        static boolean doGeneric(DynamicObject receiver, Object key, Object value, int mode, int propertyFlags) {
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

    static boolean canStore(Location newLocation, Object value) {
        return newLocation instanceof ExtLocations.AbstractObjectLocation || newLocation.canStore(value);
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

    // defineProperty() might obsolete the oldShape and we don't handle invalid shape -> valid
    // shape transitions on the fast path (in doCached)
    static Shape getNewShapeAndCheckOldShapeStillValid(Object cachedKey, Object value, int newPropertyFlags, int putFlags, Property existingProperty, Shape oldShape) {
        Shape newShape = getNewShape(cachedKey, value, newPropertyFlags, putFlags, existingProperty, oldShape);
        if (!oldShape.isValid()) {
            return oldShape; // return an invalid shape to not use this specialization
        }
        return newShape;
    }

    static Location getNewLocation(Shape oldShape, Shape newShape, Object cachedKey, Property oldProperty) {
        if (newShape == oldShape) {
            return oldProperty == null ? null : oldProperty.getLocation();
        } else {
            return newShape.getLocation(cachedKey);
        }
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
        @Specialization(guards = "shape == cachedShape", limit = "SHAPE_CACHE_LIMIT")
        static void doCached(DynamicObject from, DynamicObject to,
                        @Bind("from.getShape()") @SuppressWarnings("unused") Shape shape,
                        @Cached("shape") @SuppressWarnings("unused") Shape cachedShape,
                        @Cached(value = "createPropertyGetters(cachedShape)", dimensions = 1) PropertyGetter[] getters,
                        @Cached DynamicObject.PutNode putNode) {
            for (int i = 0; i < getters.length; i++) {
                PropertyGetter getter = getters[i];
                putNode.executeWithFlags(to, getter.getKey(), getter.get(from), getter.getFlags());
            }
        }

        @TruffleBoundary
        @Specialization(replaces = "doCached")
        static void doGeneric(DynamicObject from, DynamicObject to) {
            Property[] properties = from.getShape().getPropertyArray();
            for (int i = 0; i < properties.length; i++) {
                Property property = properties[i];
                PutNode.getUncached().executeWithFlags(to, property.getKey(), property.get(from, false), property.getFlags());
            }
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

        // @formatter:off
        /**
         * Returns {@code true} if this object contains a property with the given key.
         *
         * {@snippet :
         * @ExportMessage
         * boolean isMemberReadable(String name,
         *                 @Cached DynamicObject.ContainsKeyNode containsKeyNode) {
         *     return containsKeyNode.execute(this, name);
         * }
         * }
         *
         * @param key the property key
         * @return {@code true} if the object contains a property with this key, else {@code false}
         */
        // @formatter:on
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
         * @param key the property key
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
            updateShape(receiver);
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

        // @formatter:off
        /**
         * Gets the language-specific object shape flags previously set using
         * {@link SetShapeFlagsNode} or
         * {@link Shape.Builder#shapeFlags(int)}. If no shape flags were explicitly set, the default of
         * 0 is returned.
         *
         * These flags may be used to tag objects that possess characteristics that need to be queried
         * efficiently on fast and slow paths. For example, they can be used to mark objects as frozen.
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet :
         * @ExportMessage
         * Object writeMember(String member, Object value,
         *                 @Cached DynamicObject.GetShapeFlagsNode getShapeFlagsNode,
         *                 @Cached DynamicObject.PutNode putNode)
         *                 throws UnsupportedMessageException {
         *     if ((getShapeFlagsNode.execute(receiver) & FROZEN) != 0) {
         *         throw UnsupportedMessageException.create();
         *     }
         *     putNode.execute(this, member, value);
         * }
         * }
         *
         * Note that {@link HasShapeFlagsNode} is more convenient for that particular pattern.
         *
         * @return shape flags
         * @see HasShapeFlagsNode
         * @see SetShapeFlagsNode
         * @see Shape.Builder#shapeFlags(int)
         * @see Shape#getFlags()
         */
        // @formatter:on
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
     * Checks if the language-specific object shape flags include the given flags.
     *
     * @see #execute(DynamicObject, int)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class HasShapeFlagsNode extends Node {

        HasShapeFlagsNode() {
        }

        // @formatter:off
        /**
         * Checks if the language-specific object shape flags contains the given flags, previously set using
         * {@link SetShapeFlagsNode} or
         * {@link Shape.Builder#shapeFlags(int)}. If no shape flags were explicitly set, the default of
         * false is returned.
         *
         * These flags may be used to tag objects that possess characteristics that need to be queried
         * efficiently on fast and slow paths. For example, they can be used to mark objects as frozen.
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet :
         * @ExportMessage
         * Object writeMember(String member, Object value,
         *                 @Cached DynamicObject.HasShapeFlagsNode hasShapeFlagsNode,
         *                 @Cached DynamicObject.PutNode putNode)
         *                 throws UnsupportedMessageException {
         *     if (hasShapeFlagsNode.execute(receiver, FROZEN)) {
         *         throw UnsupportedMessageException.create();
         *     }
         *     putNode.execute(this, member, value);
         * }
         * }
         *
         * @return whether the shape flags contain (all of) the given flags
         * @see GetShapeFlagsNode
         * @see SetShapeFlagsNode
         * @see Shape.Builder#shapeFlags(int)
         * @see Shape#getFlags()
         */
        // @formatter:on
        public abstract boolean execute(DynamicObject receiver, int flags);

        @SuppressWarnings("unused")
        @Specialization(guards = "shape == cachedShape", limit = "1")
        static boolean doCached(DynamicObject receiver, int flags,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape) {
            return (cachedShape.getFlags() & flags) == flags;
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, int flags) {
            return (receiver.getShape().getFlags() & flags) == flags;
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static HasShapeFlagsNode create() {
            return DynamicObjectFactory.HasShapeFlagsNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static HasShapeFlagsNode getUncached() {
            return DynamicObjectFactory.HasShapeFlagsNodeGen.getUncached();
        }
    }

    /**
     * Sets language-specific object shape flags.
     *
     * @see #execute(DynamicObject, int)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class SetShapeFlagsNode extends Node {

        SetShapeFlagsNode() {
        }

        // @formatter:off
        /**
         * Sets language-specific object shape flags, changing the object's shape if need be.
         *
         * These flags may be used to tag objects that possess characteristics that need to be queried
         * efficiently on fast and slow paths. For example, they can be used to mark objects as frozen.
         *
         * Only the lowest 16 bits (i.e. values in the range 0 to 65535) are allowed, the remaining bits
         * are currently reserved.
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet :
         * @Specialization
         * static void freeze(DynamicObject receiver,
         *                 @Cached DynamicObject.GetShapeFlagsNode getShapeFlagsNode,
         *                 @Cached DynamicObject.SetShapeFlagsNode setShapeFlagsNode) {
         *     setShapeFlagsNode.execute(receiver, getShapeFlagsNode.execute(receiver) | FROZEN);
         * }
         * }
         *
         * Note that {@link AddShapeFlagsNode} is more efficient and convenient for that particular pattern.
         *
         * @param newFlags the flags to set; must be in the range from 0 to 65535 (inclusive).
         * @return {@code true} if the object's shape changed, {@code false} if no change was made.
         * @throws IllegalArgumentException if the flags are not in the allowed range.
         * @see GetShapeFlagsNode
         * @see AddShapeFlagsNode
         * @see Shape.Builder#shapeFlags(int)
         */
        // @formatter:on
        public abstract boolean execute(DynamicObject receiver, int newFlags);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == cachedShape", "flags == newShape.getFlags()"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, int flags,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Cached("shapeSetFlags(cachedShape, flags)") Shape newShape) {
            if (newShape != cachedShape) {
                receiver.setShape(newShape);
                return true;
            } else {
                return false;
            }
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, int flags,
                        @Bind("receiver.getShape()") Shape shape) {
            Shape newShape = shapeSetFlags(shape, flags);
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
     * Adds language-specific object shape flags.
     *
     * @see #execute(DynamicObject, int)
     * @since 25.1
     */
    @ImportStatic(DynamicObject.class)
    @GeneratePackagePrivate
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class AddShapeFlagsNode extends Node {

        AddShapeFlagsNode() {
        }

        // @formatter:off
        /**
         * Adds language-specific object shape flags, changing the object's shape if need be.
         *
         * These flags may be used to tag objects that possess characteristics that need to be queried
         * efficiently on fast and slow paths. For example, they can be used to mark objects as frozen.
         * <p>
         * Only the lowest 16 bits (i.e. values in the range 0 to 65535) are allowed, the remaining bits
         * are currently reserved.
         * <p>
         * Equivalent to:
         * {@snippet :
         * @Specialization
         * static void addFlags(DynamicObject receiver, int newFlags,
         *                 @Cached DynamicObject.GetShapeFlagsNode getShapeFlagsNode,
         *                 @Cached DynamicObject.SetShapeFlagsNode setShapeFlagsNode) {
         *     setShapeFlagsNode.execute(receiver, getShapeFlagsNode.execute(receiver) | newFlags);
         * }
         * }
         *
         * <h3>Usage example:</h3>
         *
         * {@snippet :
         * @Specialization
         * static void freeze(DynamicObject receiver,
         *                 @Cached DynamicObject.AddShapeFlagsNode addShapeFlagsNode) {
         *     addShapeFlagsNode.execute(receiver, FROZEN);
         * }
         * }
         *
         * @param newFlags the flags to set; must be in the range from 0 to 65535 (inclusive).
         * @return {@code true} if the object's shape changed, {@code false} if no change was made.
         * @throws IllegalArgumentException if the flags are not in the allowed range.
         * @see GetShapeFlagsNode
         * @see Shape.Builder#shapeFlags(int)
         */
        // @formatter:on
        public abstract boolean execute(DynamicObject receiver, int newFlags);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == cachedShape", "flags == newFlags"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, int flags,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape cachedShape,
                        @Cached("flags") int newFlags,
                        @Cached("shapeAddFlags(cachedShape, newFlags)") Shape newShape) {
            if (newShape != cachedShape) {
                receiver.setShape(newShape);
                return true;
            } else {
                return false;
            }
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, int flags,
                        @Bind("receiver.getShape()") Shape shape) {
            Shape newShape = shapeAddFlags(shape, flags);
            if (newShape != shape) {
                receiver.setShape(newShape);
                return true;
            } else {
                return false;
            }
        }

        static Shape shapeAddFlags(Shape shape, int newFlags) {
            return shape.setFlags(shape.getFlags() | newFlags);
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static AddShapeFlagsNode create() {
            return DynamicObjectFactory.AddShapeFlagsNodeGen.create();
        }

        /**
         * @since 25.1
         */
        @NeverDefault
        public static AddShapeFlagsNode getUncached() {
            return DynamicObjectFactory.AddShapeFlagsNodeGen.getUncached();
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

        // @formatter:off
        /**
         * Gets the property flags associated with the requested property key. Returns the
         * {@code defaultValue} if the object contains no such property. If the property exists but no
         * flags were explicitly set, returns the default of 0.
         *
         * <p>
         * Convenience method equivalent to:
         *
         * {@snippet :
         * @Specialization
         * int getPropertyFlags(@Cached GetPropertyNode getPropertyNode) {
         *     Property property = getPropertyNode.execute(object, key);
         *     return property != null ? property.getFlags() : defaultValue;
         * }
         * }
         *
         * @param key the property key
         * @param defaultValue value to return if no such property exists
         * @return the property flags if the property exists, else {@code defaultValue}
         * @see GetPropertyNode
         */
        // @formatter:on
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
     * Sets the property flags associated with the requested property key.
     *
     * @see #execute(DynamicObject, Object, int)
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
         * @param key the property key
         * @return {@code true} if the property was found and its flags were changed, else
         *         {@code false}
         */
        public abstract boolean execute(DynamicObject receiver, Object key, int propertyFlags);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shape == oldShape", "key == cachedKey", "propertyFlags == cachedPropertyFlags"}, limit = "SHAPE_CACHE_LIMIT")
        static boolean doCached(DynamicObject receiver, Object key, int propertyFlags,
                        @Bind("receiver.getShape()") Shape shape,
                        @Cached("shape") Shape oldShape,
                        @Cached("key") Object cachedKey,
                        @Cached("propertyFlags") int cachedPropertyFlags,
                        @Cached("oldShape.getProperty(cachedKey)") Property cachedProperty,
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
            }
            return true;
        }

        @TruffleBoundary
        @Specialization(replaces = "doCached")
        static boolean doGeneric(DynamicObject receiver, Object key, int propertyFlags) {
            Shape oldShape = receiver.getShape();
            Property existingProperty = oldShape.getProperty(key);
            if (existingProperty == null) {
                return false;
            }
            if (existingProperty.getFlags() != propertyFlags) {
                changePropertyFlagsGeneric(receiver, oldShape, existingProperty, propertyFlags);
            }
            return true;
        }

        @TruffleBoundary
        private static void changePropertyFlagsGeneric(DynamicObject receiver, Shape cachedShape, Property cachedProperty, int propertyFlags) {
            assert cachedProperty != null;
            assert cachedProperty.getFlags() != propertyFlags;

            updateShape(receiver);

            Shape oldShape = receiver.getShape();
            final Property existingProperty = reusePropertyLookup(cachedShape, cachedProperty, oldShape);
            Shape newShape = oldShape.setPropertyFlags(existingProperty, propertyFlags);
            if (newShape != oldShape) {
                DynamicObjectSupport.setShapeWithStoreFence(receiver, newShape);
                updateShape(receiver);
            }
        }

        static Shape setPropertyFlags(Shape shape, Property cachedProperty, int propertyFlags) {
            CompilerAsserts.neverPartOfCompilation();
            if (cachedProperty == null) {
                return shape;
            }
            return shape.setPropertyFlags(cachedProperty, propertyFlags);
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
            updateShape(store);
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

        // @formatter:off
        /**
         * Gets a snapshot of the object's property keys, in insertion order. The returned array may
         * have been cached and must not be mutated.
         *
         * Properties with a {@link HiddenKey} are not included.
         *
         * <h3>Usage example:</h3>
         *
         * The example below shows how the returned keys array could be translated to an interop array
         * for use with InteropLibrary.
         *
         * {@snippet :
         * @ExportMessage
         * Object getMembers(
         *                 @Cached DynamicObject.GetKeyArrayNode getKeyArrayNode) {
         *     return new Keys(getKeyArrayNode.execute(this));
         * }
         *
         * @ExportLibrary(InteropLibrary.class)
         * static final class Keys implements TruffleObject {
         *
         *     @CompilationFinal(dimensions = 1) final Object[] keys;
         *
         *     Keys(Object[] keys) {
         *         this.keys = keys;
         *     }
         *
         *     @ExportMessage
         *     boolean hasArrayElements() {
         *         return true;
         *     }
         *
         *     @ExportMessage
         *     Object readArrayElement(long index) throws InvalidArrayIndexException {
         *         if (!isArrayElementReadable(index)) {
         *             throw InvalidArrayIndexException.create(index);
         *         }
         *         return keys[(int) index];
         *     }
         *
         *     @ExportMessage
         *     long getArraySize() {
         *         return keys.length;
         *     }
         *
         *     @ExportMessage
         *     boolean isArrayElementReadable(long index) {
         *         return index >= 0 && index < keys.length;
         *     }
         * }
         * }
         *
         * @return a read-only array of the object's property keys.
         */
        // @formatter:on
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
         * @return a read-only array of the object's properties.
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
