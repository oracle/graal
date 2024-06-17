/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.staticobject;

import java.lang.reflect.Field;
import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import sun.misc.Unsafe;

/**
 * StaticProperty objects represent the mapping between {@linkplain StaticProperty#getId() property
 * identifiers} and storage locations within static objects.
 *
 * <p>
 * Users of the Static Object Model can define custom subtypes of StaticProperty or use
 * {@link DefaultStaticProperty}, a trivial default implementation. In both cases, static properties
 * must be registered to a {@link StaticShape.Builder} using
 * {@link StaticShape.Builder#property(StaticProperty, Class, boolean)}. Then, after allocating a
 * {@link StaticShape} instance with one of the {@link StaticShape.Builder#build()} methods and
 * allocating a static object using the factory class provided by {@link StaticShape#getFactory()},
 * users can call the accessor methods defined in StaticProperty to get and set property values
 * stored in a static object instance.
 *
 * <p>
 * A StaticProperty instance can be added to only one {@link StaticShape.Builder}. StaticProperty
 * instances registered to the same {@link StaticShape.Builder} must have
 * {@linkplain StaticProperty#getId() a unique id} for that builder.
 *
 * @see DefaultStaticProperty
 * @see StaticShape.Builder#property(StaticProperty, Class, boolean).
 * @since 21.3.0
 */
@SuppressWarnings("cast") // make ecj happy
public abstract class StaticProperty {
    private static final Unsafe UNSAFE = getUnsafe();

    @CompilationFinal //
    private boolean storeAsFinal;
    @CompilationFinal //
    private Class<?> type;
    @CompilationFinal //
    private StaticShape<?> shape;
    // The offset is the actual position in the field array of an actual instance.
    @CompilationFinal //
    private int offset;

    /**
     * Constructor for subclasses.
     *
     * @since 21.3.0
     */
    protected StaticProperty() {
    }

    void init(Class<?> clazz, boolean isFinal) {
        type = clazz;
        storeAsFinal = isFinal;
    }

    /**
     * StaticProperty instances must have a {@link String} identifier that is unique and constant
     * for that shape. Subtypes of StaticProperty must make sure that the value returned by this
     * method is constant in time.
     *
     * @return the static property identifier
     * @since 21.3.0
     */
    protected abstract String getId();

    final boolean storeAsFinal() {
        return storeAsFinal;
    }

    final Class<?> getPropertyType() {
        return type;
    }

    final void initOffset(int o) {
        if (this.offset != 0) {
            throw new IllegalStateException("Attempt to reinitialize the offset of static property '" + getId() + "' of type '" + type.getName() + "'.\n" +
                            "Was it added to more than one builder or multiple times to the same builder?");
        }
        this.offset = o;
    }

    final void initShape(StaticShape<?> s) {
        if (this.shape != null) {
            throw new IllegalStateException("Attempt to reinitialize the shape of static property '" + getId() + "' of type '" + type.getName() + "'.\n" +
                            "Was it added to more than one builder or multiple times to the same builder?");
        }
        this.shape = s;
    }

    private void throwIllegalArgumentException(Class<?> accessType) {
        CompilerAsserts.neverPartOfCompilation();
        throw new IllegalArgumentException("Static property '" + getId() + "' of type '" + type.getName() + "' cannot be accessed as '" + (accessType == null ? "null" : accessType.getName()) + "'");
    }

    private void checkObjectGetAccess() {
        if (type.isPrimitive()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throwIllegalArgumentException(Object.class);
        }
    }

    private void checkObjectSetAccess(Object value) {
        if (value == null) {
            if (type.isPrimitive()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throwIllegalArgumentException(null);
            }
        } else if (!type.isInstance(value)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throwIllegalArgumentException(value.getClass());
        }
    }

    private void checkPrimitiveAccess(Class<?> accessType) {
        if (type != accessType) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throwIllegalArgumentException(accessType);
        }
    }

    // Object field access
    /**
     * Returns the {@link Object} value represented by this StaticProperty and stored in the
     * specified static object. This property access has the memory semantics of reading as if the
     * variable was declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is primitive, or obj does not
     *             have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final Object getObject(Object obj) {
        checkObjectGetAccess();
        return UNSAFE.getObject(shape.getStorage(obj, false), (long) offset);
    }

    /**
     * Returns the {@link Object} value represented by this StaticProperty and stored in the
     * specified static object. This property access has the memory semantics of reading as if the
     * variable was declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is primitive, or obj does not
     *             have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final Object getObjectVolatile(Object obj) {
        checkObjectGetAccess();
        return UNSAFE.getObjectVolatile(shape.getStorage(obj, false), offset);
    }

    /**
     * Sets the {@link Object} value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of setting as if the variable
     * was declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not assignable from
     *             {@link Object#getClass() the type of value}, or obj does not have a
     *             {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setObject(Object obj, Object value) {
        checkObjectSetAccess(value);
        UNSAFE.putObject(shape.getStorage(obj, false), (long) offset, value);
    }

    /**
     * Sets the {@link Object} value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of setting as if the variable
     * was declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not assignable from
     *             {@link Object#getClass() the type of value}, or obj does not have a
     *             {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setObjectVolatile(Object obj, Object value) {
        checkObjectSetAccess(value);
        UNSAFE.putObjectVolatile(shape.getStorage(obj, false), offset, value);
    }

    /**
     * Atomically sets the {@link Object} value represented by this StaticProperty and stored in the
     * specified static object to the given updated value if the current value {@code ==} the
     * expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     * @throws IllegalArgumentException if the static property type is not assignable from
     *             {@link Object#getClass() the type of value}, or obj does not have a
     *             {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndSwapObject(Object obj, Object expect, Object update) {
        checkObjectSetAccess(update);
        return UNSAFE.compareAndSwapObject(shape.getStorage(obj, false), offset, expect, update);
    }

    /**
     * Atomically sets the {@link Object} value represented by this StaticProperty and stored in the
     * specified static object to the given value and returns the old value.
     *
     * @param obj the static object that stores the static property value
     * @param value the new value
     * @return the previous value
     * @throws IllegalArgumentException if the static property type is not assignable from
     *             {@link Object#getClass() the type of value}, or obj does not have a
     *             {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final Object getAndSetObject(Object obj, Object value) {
        checkObjectSetAccess(value);
        return UNSAFE.getAndSetObject(shape.getStorage(obj, false), offset, value);
    }

    /**
     * Atomically sets the {@link Object} value represented by this StaticProperty and stored in the
     * specified static object to {@code newValue} if the current value, referred to as the <em>
     * witness value </em>, {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return the witness value, which will be the same as the expected value if successful
     * @throws IllegalArgumentException if the static property type is not assignable from
     *             {@link Object#getClass() the type of value}, or obj does not have a
     *             {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final Object compareAndExchangeObject(Object obj, Object expect, Object update) {
        checkObjectSetAccess(update);
        return CASSupport.compareAndExchangeObject(shape.getStorage(obj, false), offset, expect, update);
    }

    // boolean field access
    /**
     * Returns the boolean value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of reading as if the variable
     * was declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the boolean class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean getBoolean(Object obj) {
        checkPrimitiveAccess(boolean.class);
        return UNSAFE.getBoolean(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the boolean value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of reading as if the variable
     * was declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the boolean class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean getBooleanVolatile(Object obj) {
        checkPrimitiveAccess(boolean.class);
        return UNSAFE.getBooleanVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the boolean value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the boolean class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setBoolean(Object obj, boolean value) {
        checkPrimitiveAccess(boolean.class);
        UNSAFE.putBoolean(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the boolean value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the boolean class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setBooleanVolatile(Object obj, boolean value) {
        checkPrimitiveAccess(boolean.class);
        UNSAFE.putBooleanVolatile(shape.getStorage(obj, true), offset, value);
    }

    /**
     * Atomically sets the boolean value represented by this StaticProperty and stored in the
     * specified static object to the given updated value if the current value {@code ==} the
     * expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     * @throws IllegalArgumentException if the static property type is not the boolean class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndSwapBoolean(Object obj, boolean expect, boolean update) {
        checkPrimitiveAccess(boolean.class);
        return CASSupport.compareAndSetBoolean(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically sets the boolean value represented by this StaticProperty and stored in the
     * specified static object to {@code newValue} if the current value, referred to as the
     * <em>witness value</em>, {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return the witness value, which will be the same as the expected value if successful
     * @throws IllegalArgumentException if the static property type is not the boolean class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndExchangeBoolean(Object obj, boolean expect, boolean update) {
        checkPrimitiveAccess(boolean.class);
        return CASSupport.compareAndExchangeBoolean(shape.getStorage(obj, true), offset, expect, update);
    }

    // byte field access
    /**
     * Returns the byte value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the byte class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final byte getByte(Object obj) {
        checkPrimitiveAccess(byte.class);
        return UNSAFE.getByte(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the byte value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the byte class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final byte getByteVolatile(Object obj) {
        checkPrimitiveAccess(byte.class);
        return UNSAFE.getByteVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the byte value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the byte class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setByte(Object obj, byte value) {
        checkPrimitiveAccess(byte.class);
        UNSAFE.putByte(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the byte value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the byte class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setByteVolatile(Object obj, byte value) {
        checkPrimitiveAccess(byte.class);
        UNSAFE.putByteVolatile(shape.getStorage(obj, true), offset, value);
    }

    /**
     * Atomically sets the byte value represented by this StaticProperty and stored in the specified
     * static object to the given updated value if the current value {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     * @throws IllegalArgumentException if the static property type is not the byte class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndSwapByte(Object obj, byte expect, byte update) {
        checkPrimitiveAccess(byte.class);
        return CASSupport.compareAndSetByte(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically sets the byte value represented by this StaticProperty and stored in the specified
     * static object to {@code newValue} if the current value, referred to as the <em>witness
     * value</em>, {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return the witness value, which will be the same as the expected value if successful
     * @throws IllegalArgumentException if the static property type is not the byte class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final byte compareAndExchangeByte(Object obj, byte expect, byte update) {
        checkPrimitiveAccess(byte.class);
        return CASSupport.compareAndExchangeByte(shape.getStorage(obj, true), offset, expect, update);
    }

    // char field access
    /**
     * Returns the char value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the char class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final char getChar(Object obj) {
        checkPrimitiveAccess(char.class);
        return UNSAFE.getChar(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the char value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the char class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final char getCharVolatile(Object obj) {
        checkPrimitiveAccess(char.class);
        return UNSAFE.getCharVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the char value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the char class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setChar(Object obj, char value) {
        checkPrimitiveAccess(char.class);
        UNSAFE.putChar(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the char value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the char class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setCharVolatile(Object obj, char value) {
        checkPrimitiveAccess(char.class);
        UNSAFE.putCharVolatile(shape.getStorage(obj, true), offset, value);
    }

    /**
     * Atomically sets the char value represented by this StaticProperty and stored in the specified
     * static object to the given updated value if the current value {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     * @throws IllegalArgumentException if the static property type is not the char class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndSwapChar(Object obj, char expect, char update) {
        checkPrimitiveAccess(char.class);
        return CASSupport.compareAndSetChar(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically sets the char value represented by this StaticProperty and stored in the specified
     * static object to {@code newValue} if the current value, referred to as the <em> witness value
     * </em>, {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return the witness value, which will be the same as the expected value if successful
     * @throws IllegalArgumentException if the static property type is not the char class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final char compareAndExchangeChar(Object obj, char expect, char update) {
        checkPrimitiveAccess(char.class);
        return CASSupport.compareAndExchangeChar(shape.getStorage(obj, true), offset, expect, update);
    }

    // double field access
    /**
     * Returns the double value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of reading as if the variable
     * was declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the double class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final double getDouble(Object obj) {
        checkPrimitiveAccess(double.class);
        return UNSAFE.getDouble(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the double value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of reading as if the variable
     * was declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the double class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final double getDoubleVolatile(Object obj) {
        checkPrimitiveAccess(double.class);
        return UNSAFE.getDoubleVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the double value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the double class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setDouble(Object obj, double value) {
        checkPrimitiveAccess(double.class);
        UNSAFE.putDouble(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the double value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the double class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setDoubleVolatile(Object obj, double value) {
        checkPrimitiveAccess(double.class);
        UNSAFE.putDoubleVolatile(shape.getStorage(obj, true), offset, value);
    }

    /**
     * Atomically sets the double value represented by this StaticProperty and stored in the
     * specified static object to the given updated value if the current value {@code ==} the
     * expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     * @throws IllegalArgumentException if the static property type is not the double class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndSwapDouble(Object obj, double expect, double update) {
        checkPrimitiveAccess(double.class);
        return CASSupport.compareAndSetDouble(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically sets the double value represented by this StaticProperty and stored in the
     * specified static object to {@code newValue} if the current value, referred to as the
     * <em>witness value</em>, {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return the witness value, which will be the same as the expected value if successful
     * @throws IllegalArgumentException if the static property type is not the double class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final double compareAndExchangeDouble(Object obj, double expect, double update) {
        checkPrimitiveAccess(double.class);
        return CASSupport.compareAndExchangeDouble(shape.getStorage(obj, true), offset, expect, update);
    }

    // float field access
    /**
     * Returns the float value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the float class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final float getFloat(Object obj) {
        checkPrimitiveAccess(float.class);
        return UNSAFE.getFloat(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the float value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the float class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final float getFloatVolatile(Object obj) {
        checkPrimitiveAccess(float.class);
        return UNSAFE.getFloatVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the float value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the float class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setFloat(Object obj, float value) {
        checkPrimitiveAccess(float.class);
        UNSAFE.putFloat(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the float value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the float class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setFloatVolatile(Object obj, float value) {
        checkPrimitiveAccess(float.class);
        UNSAFE.putFloatVolatile(shape.getStorage(obj, true), offset, value);
    }

    /**
     * Atomically sets the float value represented by this StaticProperty and stored in the
     * specified static object to the given updated value if the current value {@code ==} the
     * expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     * @throws IllegalArgumentException if the static property type is not the float class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndSwapFloat(Object obj, float expect, float update) {
        checkPrimitiveAccess(float.class);
        return CASSupport.compareAndSetFloat(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically sets the float value represented by this StaticProperty and stored in the
     * specified static object to {@code newValue} if the current value, referred to as the
     * <em>witness value</em>, {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return the witness value, which will be the same as the expected value if successful
     * @throws IllegalArgumentException if the static property type is not the float class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final float compareAndExchangeFloat(Object obj, float expect, float update) {
        checkPrimitiveAccess(float.class);
        return CASSupport.compareAndExchangeFloat(shape.getStorage(obj, true), offset, expect, update);
    }

    // int field access
    /**
     * Returns the int value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the int class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final int getInt(Object obj) {
        checkPrimitiveAccess(int.class);
        return UNSAFE.getInt(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the int value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the int class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final int getIntVolatile(Object obj) {
        checkPrimitiveAccess(int.class);
        return UNSAFE.getIntVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the int value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the int class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setInt(Object obj, int value) {
        checkPrimitiveAccess(int.class);
        UNSAFE.putInt(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the int value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the int class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setIntVolatile(Object obj, int value) {
        checkPrimitiveAccess(int.class);
        UNSAFE.putIntVolatile(shape.getStorage(obj, true), offset, value);
    }

    /**
     * Atomically sets the int value represented by this StaticProperty and stored in the specified
     * static object to the given updated value if the current value {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     * @throws IllegalArgumentException if the static property type is not the int class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndSwapInt(Object obj, int expect, int update) {
        checkPrimitiveAccess(int.class);
        return UNSAFE.compareAndSwapInt(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically sets the int value represented by this StaticProperty and stored in the specified
     * static object to {@code newValue} if the current value, referred to as the <em>witness
     * value</em>, {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return the witness value, which will be the same as the expected value if successful
     * @since 21.3.0
     */
    public final int compareAndExchangeInt(Object obj, int expect, int update) {
        checkPrimitiveAccess(int.class);
        return CASSupport.compareAndExchangeInt(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically adds the given value to the current int value represented by this StaticProperty
     * and stored in the specified static object.
     *
     * @param obj the static object that stores the static property value
     * @param delta the value to add
     * @return the previous value
     * @throws IllegalArgumentException if the static property type is not the int class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final int getAndAddInt(Object obj, int delta) {
        checkPrimitiveAccess(int.class);
        return UNSAFE.getAndAddInt(shape.getStorage(obj, true), offset, delta);
    }

    /**
     * Atomically sets the int value represented by this StaticProperty and stored in the specified
     * static object to the given value and returns the old value.
     *
     * @param obj the static object that stores the static property value
     * @param value the new value
     * @return the previous value
     * @throws IllegalArgumentException if the static property type is not the int class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final int getAndSetInt(Object obj, int value) {
        checkPrimitiveAccess(int.class);
        return UNSAFE.getAndSetInt(shape.getStorage(obj, true), offset, value);
    }

    // long field access
    /**
     * Returns the long value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the long class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final long getLong(Object obj) {
        checkPrimitiveAccess(long.class);
        return UNSAFE.getLong(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the long value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the long class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final long getLongVolatile(Object obj) {
        checkPrimitiveAccess(long.class);
        return UNSAFE.getLongVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the long value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the long class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setLong(Object obj, long value) {
        checkPrimitiveAccess(long.class);
        UNSAFE.putLong(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the long value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the long class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setLongVolatile(Object obj, long value) {
        checkPrimitiveAccess(long.class);
        UNSAFE.putLongVolatile(shape.getStorage(obj, true), offset, value);
    }

    /**
     * Atomically sets the long value represented by this StaticProperty and stored in the specified
     * static object to the given updated value if the current value {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     * @throws IllegalArgumentException if the static property type is not the long class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndSwapLong(Object obj, long expect, long update) {
        checkPrimitiveAccess(long.class);
        return UNSAFE.compareAndSwapLong(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically sets the long value represented by this StaticProperty and stored in the specified
     * static object to {@code newValue} if the current value, referred to as the <em>witness
     * value</em>, {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return the witness value, which will be the same as the expected value if successful
     * @throws IllegalArgumentException if the static property type is not the long class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final long compareAndExchangeLong(Object obj, long expect, long update) {
        checkPrimitiveAccess(long.class);
        return CASSupport.compareAndExchangeLong(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically adds the given value to the current long value represented by this StaticProperty
     * and stored in the specified static object.
     *
     * @param obj the static object that stores the static property value
     * @param delta the value to add
     * @return the previous value
     * @throws IllegalArgumentException if the static property type is not the long class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final long getAndAddLong(Object obj, long delta) {
        checkPrimitiveAccess(long.class);
        return UNSAFE.getAndAddLong(shape.getStorage(obj, true), offset, delta);
    }

    /**
     * Atomically sets the long value represented by this StaticProperty and stored in the specified
     * static object to the given value and returns the old value.
     *
     * @param obj the static object that stores the static property value
     * @param value the new value
     * @return the previous value
     * @throws IllegalArgumentException if the static property type is not the long class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final long getAndSetLong(Object obj, long value) {
        checkPrimitiveAccess(long.class);
        return UNSAFE.getAndSetLong(shape.getStorage(obj, true), offset, value);
    }

    // short field access
    /**
     * Returns the short value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the short class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final short getShort(Object obj) {
        checkPrimitiveAccess(short.class);
        return UNSAFE.getShort(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the short value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the short class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final short getShortVolatile(Object obj) {
        checkPrimitiveAccess(short.class);
        return UNSAFE.getShortVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the short value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the short class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setShort(Object obj, short value) {
        checkPrimitiveAccess(short.class);
        UNSAFE.putShort(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the short value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property type is not the short class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final void setShortVolatile(Object obj, short value) {
        checkPrimitiveAccess(short.class);
        UNSAFE.putShortVolatile(shape.getStorage(obj, true), offset, value);
    }

    /**
     * Atomically sets the short value represented by this StaticProperty and stored in the
     * specified static object to the given updated value if the current value {@code ==} the
     * expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     * @throws IllegalArgumentException if the static property type is not the short class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final boolean compareAndSwapShort(Object obj, short expect, short update) {
        checkPrimitiveAccess(short.class);
        return CASSupport.compareAndSetShort(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically sets the short value represented by this StaticProperty and stored in the
     * specified static object to {@code newValue} if the current value, referred to as the
     * <em>witness value</em>, {@code ==} the expected value.
     *
     * @param obj the static object that stores the static property value
     * @param expect the expected value
     * @param update the new value
     * @return the witness value, which will be the same as the expected value if successful
     * @throws IllegalArgumentException if the static property type is not the short class, or obj
     *             does not have a {@link StaticShape} compatible with this static property
     * @since 21.3.0
     */
    public final short compareAndExchangeShort(Object obj, short expect, short update) {
        checkPrimitiveAccess(short.class);
        return CASSupport.compareAndExchangeShort(shape.getStorage(obj, true), offset, expect, update);
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

    /**
     * Temporary class to enable support for compare and swap/exchange for sub-word fields.
     * <p>
     * This class will be moved, in favor of overlay classes: one for version &lt=8 and &gt= 9. This
     * class corresponds to the &lt=8 version.
     * <p>
     * The version for &gt=9 will be able to call directly into host Unsafe methods to get better
     * performance.
     */
    private static final class CASSupport {
        private CASSupport() {
        }

        private static boolean isBigEndian() {
            return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
        }

        private static boolean compareAndSetByte(Object o, long offset,
                        byte expected,
                        byte x) {
            return compareAndExchangeByte(o, offset, expected, x) == expected;
        }

        private static boolean compareAndSetBoolean(Object o, long offset,
                        boolean expected,
                        boolean x) {
            byte byteExpected = expected ? (byte) 1 : (byte) 0;
            byte byteX = x ? (byte) 1 : (byte) 0;
            return compareAndSetByte(o, offset, byteExpected, byteX);
        }

        private static boolean compareAndSetShort(Object o, long offset,
                        short expected,
                        short x) {
            return compareAndExchangeShort(o, offset, expected, x) == expected;
        }

        private static boolean compareAndSetChar(Object o, long offset,
                        char expected,
                        char x) {
            return compareAndSetShort(o, offset, (short) expected, (short) x);
        }

        private static boolean compareAndSetFloat(Object o, long offset,
                        float expected,
                        float x) {
            return UNSAFE.compareAndSwapInt(o, offset,
                            Float.floatToRawIntBits(expected),
                            Float.floatToRawIntBits(x));
        }

        private static boolean compareAndSetDouble(Object o, long offset,
                        double expected,
                        double x) {
            return UNSAFE.compareAndSwapLong(o, offset,
                            Double.doubleToRawLongBits(expected),
                            Double.doubleToRawLongBits(x));
        }

        private static byte compareAndExchangeByte(Object o, long offset,
                        byte expected,
                        byte x) {
            long wordOffset = offset & ~3;
            int shift = (int) (offset & 3) << 3;
            if (isBigEndian()) {
                shift = 24 - shift;
            }
            int mask = 0xFF << shift;
            int maskedExpected = (expected & 0xFF) << shift;
            int maskedX = (x & 0xFF) << shift;
            int fullWord;
            do {
                fullWord = UNSAFE.getIntVolatile(o, wordOffset);
                if ((fullWord & mask) != maskedExpected) {
                    return (byte) ((fullWord & mask) >> shift);
                }
            } while (!UNSAFE.compareAndSwapInt(o, wordOffset,
                            fullWord, (fullWord & ~mask) | maskedX));
            return expected;
        }

        private static boolean compareAndExchangeBoolean(Object o, long offset,
                        boolean expected,
                        boolean x) {
            byte byteExpected = expected ? (byte) 1 : (byte) 0;
            byte byteX = x ? (byte) 1 : (byte) 0;
            return compareAndExchangeByte(o, offset, byteExpected, byteX) != 0;
        }

        private static short compareAndExchangeShort(Object o, long offset,
                        short expected,
                        short x) {
            if ((offset & 3) == 3) {
                throw new IllegalArgumentException("Update spans the word, not supported");
            }
            long wordOffset = offset & ~3;
            int shift = (int) (offset & 3) << 3;
            if (isBigEndian()) {
                shift = 16 - shift;
            }
            int mask = 0xFFFF << shift;
            int maskedExpected = (expected & 0xFFFF) << shift;
            int maskedX = (x & 0xFFFF) << shift;
            int fullWord;
            do {
                fullWord = UNSAFE.getIntVolatile(o, wordOffset);
                if ((fullWord & mask) != maskedExpected) {
                    return (short) ((fullWord & mask) >> shift);
                }
            } while (!UNSAFE.compareAndSwapInt(o, wordOffset,
                            fullWord, (fullWord & ~mask) | maskedX));
            return expected;
        }

        private static char compareAndExchangeChar(Object o, long offset,
                        char expected,
                        char x) {
            return (char) compareAndExchangeShort(o, offset, (short) expected, (short) x);
        }

        private static int compareAndExchangeInt(Object o, long offset,
                        int expected,
                        int x) {
            int result;
            do {
                result = UNSAFE.getIntVolatile(o, offset);
                if (result != expected) {
                    return result;
                }
            } while (!UNSAFE.compareAndSwapInt(o, offset, expected, x));
            return expected;
        }

        private static Object compareAndExchangeObject(Object o, long offset,
                        Object expected,
                        Object x) {
            Object result;
            do {
                result = UNSAFE.getObjectVolatile(o, offset);
                if (result != expected) {
                    return result;
                }
            } while (!UNSAFE.compareAndSwapObject(o, offset, expected, x));
            return expected;
        }

        private static float compareAndExchangeFloat(Object o, long offset,
                        float expected,
                        float x) {
            return Float.intBitsToFloat(compareAndExchangeInt(o, offset,
                            Float.floatToRawIntBits(expected),
                            Float.floatToRawIntBits(x)));
        }

        private static long compareAndExchangeLong(Object o, long offset,
                        long expected,
                        long x) {
            long result;
            do {
                result = UNSAFE.getLongVolatile(o, offset);
                if (result != expected) {
                    return result;
                }
            } while (!UNSAFE.compareAndSwapLong(o, offset, expected, x));
            return expected;
        }

        private static double compareAndExchangeDouble(Object o, long offset,
                        double expected,
                        double x) {
            return Double.longBitsToDouble(compareAndExchangeLong(o, offset,
                            Double.doubleToRawLongBits(expected),
                            Double.doubleToRawLongBits(x)));
        }
    }
}
