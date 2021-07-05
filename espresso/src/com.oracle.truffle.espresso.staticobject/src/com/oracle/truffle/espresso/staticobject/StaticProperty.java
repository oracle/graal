/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.staticobject;

import java.lang.reflect.Field;
import java.nio.ByteOrder;

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
 * {@link StaticShape.Builder#property(StaticProperty)}. Then, after allocating a
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
 * @see StaticShape.Builder#property(StaticProperty)
 */
public abstract class StaticProperty {
    private static final Unsafe UNSAFE = getUnsafe();
    private static final byte STORE_AS_FINAL = (byte) (1 << 7);
    private final byte flags;
    @CompilationFinal //
    private StaticShape<?> shape;
    // The offset is the actual position in the field array of an actual instance.
    @CompilationFinal //
    private int offset;

    /**
     * Constructor for subclasses. Only property accesses that match the
     * {@linkplain StaticPropertyKind property kind} are allowed. Property values can be optionally
     * stored in a final field. Accesses to such values might be specially optimized by the
     * compiler. For example, reads might be constant-folded. It is up to the user to enforce that
     * property values stored as final are not assigned more than once.
     *
     * @param kind the kind of static property
     * @param storeAsFinal if this property value can be stored in a final field
     */
    protected StaticProperty(StaticPropertyKind kind, boolean storeAsFinal) {
        byte internalKind = getInternalKind(kind);
        assert (internalKind & STORE_AS_FINAL) == 0;
        this.flags = (byte) (storeAsFinal ? STORE_AS_FINAL | internalKind : internalKind);
    }

    /**
     * StaticProperty instances must have a {@link String} identifier that is unique and constant
     * for that shape. Subtypes of StaticProperty must make sure that the value returned by this
     * method is constant in time.
     *
     * @return the static property identifier
     */
    protected abstract String getId();

    final boolean storeAsFinal() {
        return (flags & STORE_AS_FINAL) == STORE_AS_FINAL;
    }

    private static byte getInternalKind(StaticPropertyKind kind) {
        return kind.toByte();
    }

    final byte getInternalKind() {
        return (byte) (flags & ~STORE_AS_FINAL);
    }

    final void initOffset(int o) {
        if (this.offset != 0) {
            throw new RuntimeException("Attempt to reinitialize the offset of static property '" + getId() + "' of kind '" + StaticPropertyKind.valueOf(getInternalKind()).name() + "'.\n" +
                            "Was it added to more than one builder or multiple times to the same builder?");
        }
        this.offset = o;
    }

    final void initShape(StaticShape<?> s) {
        if (this.shape != null) {
            throw new RuntimeException("Attempt to reinitialize the shape of static property '" + getId() + "' of kind '" + StaticPropertyKind.valueOf(getInternalKind()).name() + "'.\n" +
                            "Was it added to more than one builder or multiple times to the same builder?");
        }
        this.shape = s;
    }

    private void checkKind(StaticPropertyKind kind) {
        byte internalKind = getInternalKind();
        if (internalKind != getInternalKind(kind)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            String kindName = StaticPropertyKind.valueOf(internalKind).name();
            throw new IllegalArgumentException("Static property '" + getId() + "' of kind '" + kindName + "' cannot be accessed as '" + kind.name() + "'");
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Object} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final Object getObject(Object obj) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getObject(shape.getStorage(obj, false), (long) offset);
    }

    /**
     * Returns the {@link Object} value represented by this StaticProperty and stored in the
     * specified static object. This property access has the memory semantics of reading as if the
     * variable was declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Object} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final Object getObjectVolatile(Object obj) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.getObjectVolatile(shape.getStorage(obj, false), offset);
    }

    /**
     * Sets the {@link Object} value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of setting as if the variable
     * was declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Object} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setObject(Object obj, Object value) {
        checkKind(StaticPropertyKind.Object);
        UNSAFE.putObject(shape.getStorage(obj, false), (long) offset, value);
    }

    /**
     * Sets the {@link Object} value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of setting as if the variable
     * was declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Object} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setObjectVolatile(Object obj, Object value) {
        checkKind(StaticPropertyKind.Object);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Object} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndSwapObject(Object obj, Object expect, Object update) {
        checkKind(StaticPropertyKind.Object);
        return UNSAFE.compareAndSwapObject(shape.getStorage(obj, false), offset, expect, update);
    }

    /**
     * Atomically sets the {@link Object} value represented by this StaticProperty and stored in the
     * specified static object to the given value and returns the old value.
     *
     * @param obj the static object that stores the static property value
     * @param value the new value
     * @return the previous value
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Object} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final Object getAndSetObject(Object obj, Object value) {
        checkKind(StaticPropertyKind.Object);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Object} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final Object compareAndExchangeObject(Object obj, Object expect, Object update) {
        checkKind(StaticPropertyKind.Object);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Boolean} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean getBoolean(Object obj) {
        checkKind(StaticPropertyKind.Boolean);
        return UNSAFE.getBoolean(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the boolean value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of reading as if the variable
     * was declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Boolean} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean getBooleanVolatile(Object obj) {
        checkKind(StaticPropertyKind.Boolean);
        return UNSAFE.getBooleanVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the boolean value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Boolean} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setBoolean(Object obj, boolean value) {
        checkKind(StaticPropertyKind.Boolean);
        UNSAFE.putBoolean(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the boolean value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Boolean} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setBooleanVolatile(Object obj, boolean value) {
        checkKind(StaticPropertyKind.Boolean);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Boolean} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndSwapBoolean(Object obj, boolean expect, boolean update) {
        checkKind(StaticPropertyKind.Boolean);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Boolean} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndExchangeBoolean(Object obj, boolean expect, boolean update) {
        checkKind(StaticPropertyKind.Boolean);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Byte} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final byte getByte(Object obj) {
        checkKind(StaticPropertyKind.Byte);
        return UNSAFE.getByte(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the byte value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Byte} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final byte getByteVolatile(Object obj) {
        checkKind(StaticPropertyKind.Byte);
        return UNSAFE.getByteVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the byte value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Byte} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setByte(Object obj, byte value) {
        checkKind(StaticPropertyKind.Byte);
        UNSAFE.putByte(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the byte value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Byte} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setByteVolatile(Object obj, byte value) {
        checkKind(StaticPropertyKind.Byte);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Byte} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndSwapByte(Object obj, byte expect, byte update) {
        checkKind(StaticPropertyKind.Byte);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Byte} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final byte compareAndExchangeByte(Object obj, byte expect, byte update) {
        checkKind(StaticPropertyKind.Byte);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Char} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final char getChar(Object obj) {
        checkKind(StaticPropertyKind.Char);
        return UNSAFE.getChar(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the char value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Char} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final char getCharVolatile(Object obj) {
        checkKind(StaticPropertyKind.Char);
        return UNSAFE.getCharVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the char value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Char} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setChar(Object obj, char value) {
        checkKind(StaticPropertyKind.Char);
        UNSAFE.putChar(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the char value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Char} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setCharVolatile(Object obj, char value) {
        checkKind(StaticPropertyKind.Char);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Char} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndSwapChar(Object obj, char expect, char update) {
        checkKind(StaticPropertyKind.Char);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Char} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final char compareAndExchangeChar(Object obj, char expect, char update) {
        checkKind(StaticPropertyKind.Char);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Double} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final double getDouble(Object obj) {
        checkKind(StaticPropertyKind.Double);
        return UNSAFE.getDouble(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the double value represented by this StaticProperty and stored in the specified
     * static object. This property access has the memory semantics of reading as if the variable
     * was declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Double} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final double getDoubleVolatile(Object obj) {
        checkKind(StaticPropertyKind.Double);
        return UNSAFE.getDoubleVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the double value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Double} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setDouble(Object obj, double value) {
        checkKind(StaticPropertyKind.Double);
        UNSAFE.putDouble(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the double value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Double} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setDoubleVolatile(Object obj, double value) {
        checkKind(StaticPropertyKind.Double);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Double} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndSwapDouble(Object obj, double expect, double update) {
        checkKind(StaticPropertyKind.Double);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Double} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final double compareAndExchangeDouble(Object obj, double expect, double update) {
        checkKind(StaticPropertyKind.Double);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Float} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final float getFloat(Object obj) {
        checkKind(StaticPropertyKind.Float);
        return UNSAFE.getFloat(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the float value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Float} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final float getFloatVolatile(Object obj) {
        checkKind(StaticPropertyKind.Float);
        return UNSAFE.getFloatVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the float value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Float} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setFloat(Object obj, float value) {
        checkKind(StaticPropertyKind.Float);
        UNSAFE.putFloat(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the float value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Float} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setFloatVolatile(Object obj, float value) {
        checkKind(StaticPropertyKind.Float);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Float} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndSwapFloat(Object obj, float expect, float update) {
        checkKind(StaticPropertyKind.Float);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Float} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final float compareAndExchangeFloat(Object obj, float expect, float update) {
        checkKind(StaticPropertyKind.Float);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Int} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final int getInt(Object obj) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getInt(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the int value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Int} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final int getIntVolatile(Object obj) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getIntVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the int value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Int} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setInt(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
        UNSAFE.putInt(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the int value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Int} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setIntVolatile(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Int} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndSwapInt(Object obj, int expect, int update) {
        checkKind(StaticPropertyKind.Int);
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
     */
    public final int compareAndExchangeInt(Object obj, int expect, int update) {
        checkKind(StaticPropertyKind.Int);
        return CASSupport.compareAndExchangeInt(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically adds the given value to the current int value represented by this StaticProperty
     * and stored in the specified static object.
     *
     * @param obj the static object that stores the static property value
     * @param delta the value to add
     * @return the previous value
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Int} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final int getAndAddInt(Object obj, int delta) {
        checkKind(StaticPropertyKind.Int);
        return UNSAFE.getAndAddInt(shape.getStorage(obj, true), offset, delta);
    }

    /**
     * Atomically sets the int value represented by this StaticProperty and stored in the specified
     * static object to the given value and returns the old value.
     *
     * @param obj the static object that stores the static property value
     * @param value the new value
     * @return the previous value
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Int} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final int getAndSetInt(Object obj, int value) {
        checkKind(StaticPropertyKind.Int);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Long} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final long getLong(Object obj) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getLong(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the long value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Long} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final long getLongVolatile(Object obj) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getLongVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the long value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Long} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setLong(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
        UNSAFE.putLong(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the long value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Long} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setLongVolatile(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Long} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndSwapLong(Object obj, long expect, long update) {
        checkKind(StaticPropertyKind.Long);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Long} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final long compareAndExchangeLong(Object obj, long expect, long update) {
        checkKind(StaticPropertyKind.Long);
        return CASSupport.compareAndExchangeLong(shape.getStorage(obj, true), offset, expect, update);
    }

    /**
     * Atomically adds the given value to the current long value represented by this StaticProperty
     * and stored in the specified static object.
     *
     * @param obj the static object that stores the static property value
     * @param delta the value to add
     * @return the previous value
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Long} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final long getAndAddLong(Object obj, long delta) {
        checkKind(StaticPropertyKind.Long);
        return UNSAFE.getAndAddLong(shape.getStorage(obj, true), offset, delta);
    }

    /**
     * Atomically sets the long value represented by this StaticProperty and stored in the specified
     * static object to the given value and returns the old value.
     *
     * @param obj the static object that stores the static property value
     * @param value the new value
     * @return the previous value
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Long} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final long getAndSetLong(Object obj, long value) {
        checkKind(StaticPropertyKind.Long);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Short} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final short getShort(Object obj) {
        checkKind(StaticPropertyKind.Short);
        return UNSAFE.getShort(shape.getStorage(obj, true), (long) offset);
    }

    /**
     * Returns the short value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of reading as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @return the value of the static property stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Short} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final short getShortVolatile(Object obj) {
        checkKind(StaticPropertyKind.Short);
        return UNSAFE.getShortVolatile(shape.getStorage(obj, true), offset);
    }

    /**
     * Sets the short value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared non-volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Short} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setShort(Object obj, short value) {
        checkKind(StaticPropertyKind.Short);
        UNSAFE.putShort(shape.getStorage(obj, true), (long) offset, value);
    }

    /**
     * Sets the short value represented by this StaticProperty and stored in the specified static
     * object. This property access has the memory semantics of setting as if the variable was
     * declared volatile.
     *
     * @param obj the static object that stores the static property value
     * @param value the new static property value, to be stored in static object obj
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Short} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final void setShortVolatile(Object obj, short value) {
        checkKind(StaticPropertyKind.Short);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Short} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final boolean compareAndSwapShort(Object obj, short expect, short update) {
        checkKind(StaticPropertyKind.Short);
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
     * @throws IllegalArgumentException if the static property kind is not
     *             {@link StaticPropertyKind#Short} or obj does not have a {@link StaticShape}
     *             compatible with this static property
     */
    public final short compareAndExchangeShort(Object obj, short expect, short update) {
        checkKind(StaticPropertyKind.Short);
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
