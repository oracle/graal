/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import sun.misc.Unsafe;

/**
 * Represents an object members of which can be dynamically added and removed at run time.
 *
 * @see Shape
 * @since 0.8 or earlier
 */
@SuppressWarnings("deprecation")
@ExportLibrary(DynamicDispatchLibrary.class)
public abstract class DynamicObject implements TruffleObject {

    private Shape shape;

    /**
     * @since 0.8 or earlier
     */
    @Deprecated
    protected DynamicObject() {
        CompilerAsserts.neverPartOfCompilation();
        throw new UnsupportedOperationException();
    }

    /**
     * Constructor for subclasses.
     *
     * @since 19.0
     */
    protected DynamicObject(Shape shape) {
        verifyShape(shape, this.getClass());
        this.shape = shape;
    }

    private static void verifyShape(Shape shape, Class<? extends DynamicObject> subclass) {
        Class<? extends DynamicObject> shapeType = shape.getLayout().getType();
        if (!(shapeType == subclass || (shapeType.isAssignableFrom(subclass) && DynamicObject.class.isAssignableFrom(shapeType)))) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Incompatible shape");
        }
    }

    @ExportMessage
    static class Accepts {

        @Specialization(limit = "1", guards = "cachedShape == receiver.getShape()")
        @SuppressWarnings("unused")
        static boolean doCachedShape(DynamicObject receiver,
                        @Shared("cachedShape") @Cached("receiver.getShape()") Shape cachedShape,
                        @Shared("cachedTypeClass") @Cached(value = "receiver.getShape().getObjectType().getClass()", allowUncached = true) Class<? extends ObjectType> typeClass) {
            return true;
        }

        @Specialization(replaces = "doCachedShape")
        static boolean doCachedTypeClass(DynamicObject receiver,
                        @Shared("cachedTypeClass") @Cached(value = "receiver.getShape().getObjectType().getClass()", allowUncached = true) Class<? extends ObjectType> typeClass) {
            return typeClass == receiver.getShape().getObjectType().getClass();
        }
    }

    @ExportMessage
    static class Dispatch {

        @Specialization(limit = "1", guards = "cachedShape == receiver.getShape()")
        @SuppressWarnings("unused")
        static Class<?> doCachedShape(DynamicObject receiver,
                        @Shared("cachedShape") @Cached("receiver.getShape()") Shape cachedShape,
                        @Shared("cachedTypeClass") @Cached(value = "receiver.getShape().getObjectType().getClass()", allowUncached = true) Class<? extends ObjectType> typeClass) {
            return cachedShape.getObjectType().dispatch();
        }

        @Specialization(replaces = "doCachedShape")
        static Class<?> doCachedTypeClass(DynamicObject receiver,
                        @Shared("cachedTypeClass") @Cached(value = "receiver.getShape().getObjectType().getClass()", allowUncached = true) Class<? extends ObjectType> typeClass) {
            ObjectType objectType = CompilerDirectives.castExact(receiver.getShape().getObjectType(), typeClass);
            return objectType.dispatch();
        }
    }

    /**
     * Get the object's current shape.
     *
     * @since 0.8 or earlier
     */
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
        assert shape.getLayout().getType().isInstance(this);
        setShapeHelper(shape, SHAPE_OFFSET);
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
     * Get property value.
     *
     * @param key property identifier
     * @return property value or {@code null} if object has no such property
     * @since 0.8 or earlier
     */
    public final Object get(Object key) {
        return get(key, null);
    }

    /**
     * Get property value.
     *
     * @param key property identifier
     * @param defaultValue return value if property is not found
     * @return property value or defaultValue if object has no such property
     * @since 0.8 or earlier
     */
    public abstract Object get(Object key, Object defaultValue);

    /**
     * Set value of existing property.
     *
     * @param key property identifier
     * @param value value to be set
     * @return {@code true} if successful or {@code false} if property not found
     * @since 0.8 or earlier
     */
    public abstract boolean set(Object key, Object value);

    /**
     * Returns {@code true} if this object contains a property with the given key.
     *
     * @since 0.8 or earlier
     */
    public final boolean containsKey(Object key) {
        return getShape().getProperty(key) != null;
    }

    /**
     * Define new property or redefine existing property.
     *
     * @param key property identifier
     * @param value value to be set
     * @since 0.8 or earlier
     */
    public final void define(Object key, Object value) {
        define(key, value, 0);
    }

    /**
     * Define new property or redefine existing property.
     *
     * @param key property identifier
     * @param value value to be set
     * @param flags flags to be set
     * @since 0.8 or earlier
     */
    public abstract void define(Object key, Object value, int flags);

    /**
     * Define new property with a static location or change existing property.
     *
     * @param key property identifier
     * @param value value to be set
     * @param flags flags to be set
     * @param locationFactory factory function that creates a location for a given shape and value
     * @since 0.8 or earlier
     */
    public abstract void define(Object key, Object value, int flags, LocationFactory locationFactory);

    /**
     * Delete property.
     *
     * @param key property identifier
     * @return {@code true} if successful or {@code false} if property not found
     * @since 0.8 or earlier
     */
    public abstract boolean delete(Object key);

    /**
     * Returns the number of properties in this object.
     *
     * @since 0.8 or earlier
     */
    public abstract int size();

    /**
     * Returns {@code true} if this object contains no properties.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean isEmpty();

    /**
     * Set object shape and grow storage if necessary.
     *
     * @param oldShape the object's current shape (must equal {@link #getShape()})
     * @param newShape the new shape to be set
     * @since 0.8 or earlier
     */
    public abstract void setShapeAndGrow(Shape oldShape, Shape newShape);

    /**
     * Set object shape and resize storage if necessary.
     *
     * @param oldShape the object's current shape (must equal {@link #getShape()})
     * @param newShape the new shape to be set
     * @since 0.8 or earlier
     */
    public abstract void setShapeAndResize(Shape oldShape, Shape newShape);

    /**
     * Ensure object shape is up-to-date.
     *
     * @return {@code true} if shape has changed
     * @since 0.8 or earlier
     */
    public abstract boolean updateShape();

    /**
     * Create a shallow copy of this object.
     *
     * @param currentShape the object's current shape (must equal {@link #getShape()})
     * @since 0.8 or earlier
     */
    public abstract DynamicObject copy(Shape currentShape);

    private static final Unsafe UNSAFE;
    private static final long SHAPE_OFFSET;
    static {
        UNSAFE = getUnsafe();
        try {
            SHAPE_OFFSET = UNSAFE.objectFieldOffset(DynamicObject.class.getDeclaredField("shape"));
        } catch (Exception e) {
            throw new IllegalStateException("Could not get 'shape' field offset", e);
        }
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
