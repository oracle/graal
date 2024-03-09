/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.interop.TruffleObject;

import sun.misc.Unsafe;

/**
 * Represents a dynamic object, members of which can be dynamically added and removed at run time.
 *
 * To use it, extend {@link DynamicObject} and use {@link DynamicObjectLibrary} for object accesses.
 *
 * When {@linkplain DynamicObject#DynamicObject(Shape) constructing} a {@link DynamicObject}, it has
 * to be initialized with an empty initial shape. Initial shapes are created using
 * {@link Shape#newBuilder()} and should ideally be shared per TruffleLanguage instance to allow
 * shape caches to be shared across contexts.
 *
 * Subclasses can provide in-object dynamic field slots using the {@link DynamicField} annotation
 * and {@link Shape.Builder#layout(Class) Shape.Builder.layout}.
 *
 * <p>
 * Example:
 *
 * <pre>
 * <code>
 * public class MyObject extends DynamicObject implements TruffleObject {
 *     public MyObject(Shape shape) {
 *         super(shape);
 *     }
 * }
 *
 * Shape initialShape = Shape.newBuilder().layout(MyObject.class).build();
 *
 * MyObject obj = new MyObject(initialShape);
 * </code>
 * </pre>
 *
 * @see DynamicObject#DynamicObject(Shape)
 * @see DynamicObjectLibrary
 * @see Shape
 * @see Shape#newBuilder()
 * @since 0.8 or earlier
 */
public abstract class DynamicObject implements TruffleObject {

    /**
     * Using this annotation, subclasses can define additional dynamic fields to be used by the
     * object layout. Annotated field must be of type {@code Object} or {@code long}, must not be
     * final, and must not have any direct usages.
     *
     * @see Shape.Builder#layout(Class)
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
     * shape. The shape must have been constructed with a {@linkplain Shape.Builder#layout(Class)
     * layout class} assignable from this class (i.e., the concrete subclass, a superclass thereof,
     * including {@link DynamicObject}) and must not have any instance properties (but may have
     * constant properties).
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
     * Shape shape = {@link Shape#newBuilder()}.{@link Shape.Builder#layout(Class) layout}(MyObject.class).{@link Shape.Builder#build() build}();
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
        if (!(shapeType == subclass || (shapeType.isAssignableFrom(subclass) && DynamicObject.class.isAssignableFrom(shapeType)))) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw illegalShapeType(shapeType, subclass);
        }
        if (shape.hasInstanceProperties()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw illegalShapeProperties();
        }
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
