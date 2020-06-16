/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a value accessed using the debugger API. Please note that values can become invalid
 * depending on the context in which they are used. For example stack values will only remain valid
 * as long as the current stack element is active. Heap values on the other hand remain valid. If a
 * value becomes invalid then setting or getting a value will throw an {@link IllegalStateException}
 * . {@link DebugValue} instances neither support equality or preserve identity.
 * <p>
 * Clients may access the debug value only on the execution thread where the suspended event of the
 * stack frame was created and notification received; access from other threads will throw
 * {@link IllegalStateException}.
 *
 * @since 0.17
 */
public abstract class DebugValue {

    static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    final LanguageInfo preferredLanguage;

    abstract Object get() throws DebugException;

    DebugValue(LanguageInfo preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    /**
     * Sets the value using another {@link DebugValue}. Throws an {@link IllegalStateException} if
     * the value is not writable, the passed value is not readable, this value or the passed value
     * is invalid, or the guest language of the values do not match. Use
     * {@link DebugStackFrame#eval(String)} to evaluate values to be set.
     *
     * @param value the value to set
     * @throws DebugException when guest language code throws an exception
     * @since 0.17
     */
    public abstract void set(DebugValue value) throws DebugException;

    /**
     * Sets a primitive value. Strings and boxed Java primitive types are considered primitive.
     * Throws an {@link IllegalStateException} if the value is not writable and
     * {@link IllegalArgumentException} if the value is not primitive.
     *
     * @param primitiveValue a primitive value to set
     * @throws DebugException when guest language code throws an exception
     * @since 19.0
     */
    public abstract void set(Object primitiveValue) throws DebugException;

    /**
     * Converts the debug value into a Java type. Class conversions which are always supported:
     * <ul>
     * <li>{@link String}.class converts the value to its language specific string representation.
     * </li>
     * <li>{@link Number}.class converts the value to a Number representation, if any.</li>
     * <li>{@link Boolean}.class converts the value to a Boolean representation, if any.</li>
     * </ul>
     * No optional conversions are currently available. If a conversion is not supported then an
     * {@link UnsupportedOperationException} is thrown. If the value is not {@link #isReadable()
     * readable} then an {@link IllegalStateException} is thrown.
     *
     * @param clazz the type to convert to
     * @return the converted Java type, or <code>null</code> when the conversion was not possible.
     * @throws DebugException when guest language code throws an exception
     * @since 0.17
     * @deprecated Use {@link #toDisplayString()} instead.
     */
    @Deprecated
    public abstract <T> T as(Class<T> clazz) throws DebugException;

    /**
     * Returns the name of this value as it is referred to from its origin. If this value is
     * originated from the stack it returns the name of the local variable. If the value was
     * returned from another objects then it returns the name of the property or field it is
     * contained in. If no name is available <code>null</code> is returned.
     *
     * @since 0.17
     */
    public abstract String getName();

    /**
     * Returns <code>true</code> if this value can be read else <code>false</code>.
     *
     * @since 0.17
     */
    public abstract boolean isReadable();

    /**
     * Returns <code>true</code> if reading of this value can have side-effects, else
     * <code>false</code>. Read has side-effects if it changes runtime state.
     *
     * @since 19.0
     */
    public abstract boolean hasReadSideEffects();

    /**
     * Returns <code>true</code> if setting a new value can have side-effects, else
     * <code>false</code>. Write has side-effects if it changes runtime state besides this value.
     *
     * @since 19.0
     */
    public abstract boolean hasWriteSideEffects();

    /**
     * Returns <code>true</code> if this value can be written to, else <code>false</code>.
     *
     * @since 0.26
     */
    public abstract boolean isWritable();

    /**
     * Returns <code>true</code> if this value represents an internal variable or property,
     * <code>false</code> otherwise.
     * <p>
     * Languages might have extra object properties or extra scope variables that are a part of the
     * runtime, but do not correspond to anything what is an explicit part of the guest language
     * representation. They may represent additional language artifacts, providing more in-depth
     * information that can be valuable during debugging. Language implementors mark these variables
     * as <em>internal</em>. An example of such internal values are internal slots in ECMAScript.
     * </p>
     *
     * @since 0.26
     */
    public abstract boolean isInternal();

    /**
     * Get the scope where this value is declared in. It returns a non-null value for local
     * variables declared on a stack. It's <code>null</code> for object properties and other heap
     * values.
     *
     * @return the scope, or <code>null</code> when this value does not belong into any scope.
     *
     * @since 0.26
     */
    public DebugScope getScope() {
        return null;
    }

    /**
     * Test if the value represents 'null'.
     *
     * @throws DebugException when guest language code throws an exception
     * @since 19.0
     */
    public final boolean isNull() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.isNull(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if and only if this value represents a string.
     *
     * @throws DebugException when guest language code throws an exception
     * @since 20.1.0
     */
    public boolean isString() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.isString(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns the {@link String} value if this value represents a string. This method returns
     * <code>null</code> otherwise.
     *
     * @throws DebugException when guest language code throws an exception
     * @since 19.0
     */
    public final String asString() throws DebugException {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object val = get();
            if (INTEROP.isString(val)) {
                return INTEROP.asString(val);
            } else {
                return null;
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>int</code>, else <code>false</code>.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #asInt()
     * @since 20.1.0
     */
    public boolean fitsInInt() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.fitsInInt(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns an <code>int</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInInt() fits}.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #fitsInInt()
     * @since 20.1.0
     */
    public int asInt() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asInt(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not an int", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if and only if this value represents a boolean value.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #asBoolean()
     * @since 20.1.0
     */
    public boolean isBoolean() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.isBoolean(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns a <code>boolean</code> representation of this value if it is {@link #isBoolean()
     * boolean}.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #isBoolean()
     * @since 20.1.0
     */
    public boolean asBoolean() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asBoolean(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a boolean", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if and only if this value represents a number. The number value may
     * be accessed as {@link #asByte() byte}, {@link #asShort() short}, {@link #asInt() int},
     * {@link #asLong() long}, {@link #asFloat() float} or {@link #asDouble() double} value.
     *
     * @throws DebugException when guest language code throws an exception
     * @since 20.1.0
     */
    public boolean isNumber() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.isNumber(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>long</code>, else <code>false</code>.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #asLong()
     * @since 20.1.0
     */
    public boolean fitsInLong() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.fitsInLong(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns a <code>long</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInLong() fits}.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #fitsInLong()
     * @since 20.1.0
     */
    public long asLong() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asLong(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a long", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>double</code>, else <code>false</code>.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #asDouble()
     * @since 20.1.0
     */
    public boolean fitsInDouble() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.fitsInDouble(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns a <code>double</code> representation of this value if it is {@link #isNumber()
     * number} and the value {@link #fitsInDouble() fits}.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #fitsInDouble()
     * @since 20.1.0
     */
    public double asDouble() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asDouble(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a double", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>float</code>, else <code>false</code>.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #asFloat()
     * @since 20.1.0
     */
    public boolean fitsInFloat() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.fitsInFloat(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns a <code>float</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInFloat() fits}.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #fitsInFloat()
     * @since 20.1.0
     */
    public float asFloat() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asFloat(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a float", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>byte</code>, else <code>false</code>.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #asByte()
     * @since 20.1.0
     */
    public boolean fitsInByte() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.fitsInByte(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns a <code>byte</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInByte() fits}.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #fitsInByte()
     * @since 20.1.0
     */
    public byte asByte() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asByte(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a byte", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>short</code>, else <code>false</code>.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #asShort()
     * @since 20.1.0
     */
    public boolean fitsInShort() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.fitsInShort(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns a <code>short</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInShort() fits}.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #fitsInShort()
     * @since 20.1.0
     */
    public short asShort() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asShort(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a short", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value represents a date, else <code>false</code>. If this
     * value is also a {@link #isTimeZone() timezone} then the date is aware, otherwise it is naive.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #asDate()
     * @since 20.1.0
     */
    public boolean isDate() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.isDate(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns this value as date if it is a {@link #isDate() date}. The returned date is either
     * aware if the value has a {@link #isTimeZone() timezone} otherwise it is naive.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #isDate()
     * @since 20.1.0
     */
    public LocalDate asDate() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asDate(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a date", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value represents a time, else <code>false</code>. If the
     * value is also a {@link #isTimeZone() timezone} then the time is aware, otherwise it is naive.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #asTime()
     * @since 20.1.0
     */
    public boolean isTime() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.isTime(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns this value as time if it is a {@link #isTime() time}. The returned time is either
     * aware if the value has a {@link #isTimeZone() timezone} otherwise it is naive.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #isTime()
     * @since 20.1.0
     */
    public LocalTime asTime() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asTime(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a time", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value represents an instant. See
     * {@link InteropLibrary#isInstant(Object)} for detailed description.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #isDate()
     * @see #isTime()
     * @see #isInstant()
     * @see #asInstant()
     * @since 20.1.0
     */
    public boolean isInstant() {
        return isDate() && isTime() && isTimeZone();
    }

    /**
     * Returns this value as instant if it is an {@link #isInstant() instant}. See
     * {@link InteropLibrary#asInstant(Object)} for detailed description.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #isDate()
     * @see #isTime()
     * @see #isTimeZone()
     * @since 20.1.0
     */
    public Instant asInstant() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asInstant(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not an instant", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this object represents a timezone, else <code>false</code>. See
     * {@link InteropLibrary#isTimeZone(Object)} for detailed description.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #asTimeZone()
     * @see #asInstant()
     * @since 20.1.0
     */
    public boolean isTimeZone() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.isTimeZone(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns this value as timestamp if it represents a {@link #isTimeZone() timezone}.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #isTimeZone()
     * @since 20.1.0
     */
    public ZoneId asTimeZone() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asTimeZone(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a time", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this object represents a duration, else <code>false</code>.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see Duration
     * @see #asDuration()
     * @since 20.1.0
     */
    public boolean isDuration() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.isDuration(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns this value as duration if this object represents a {@link #isDuration() duration}.
     *
     * @throws UnsupportedOperationException if this value could not be converted.
     * @throws DebugException when guest language code throws an exception
     * @see #isDuration()
     * @since 20.1.0
     */
    public Duration asDuration() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asDuration(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a time", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if the value represents a metaobject. See
     * {@link InteropLibrary#isMetaObject(Object)} for detailed description.
     *
     * @throws DebugException when guest language code throws an exception
     * @see #getMetaQualifiedName()
     * @see #getMetaSimpleName()
     * @see #isMetaInstance(DebugValue)
     * @see #getMetaObject()
     * @since 20.1
     */
    public boolean isMetaObject() {
        if (!isReadable()) {
            return false;
        }
        try {
            Object value = get();
            return INTEROP.isMetaObject(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns the qualified name of a metaobject as {@link #isString() String}. See
     * {@link InteropLibrary#getMetaQualifiedName(Object)} for detailed description.
     *
     * @throws UnsupportedOperationException if and only if {@link #isMetaObject()} returns
     *             <code>false</code> for the same value.
     * @throws DebugException when guest language code throws an exception
     * @see #isMetaObject()
     * @since 20.1.0
     */
    public String getMetaQualifiedName() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asString(INTEROP.getMetaQualifiedName(value));
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a metaobject", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns the simple name of a metaobject as {@link #isString() string}. See
     * {@link InteropLibrary#getMetaSimpleName(Object)} for detailed description.
     *
     * @throws UnsupportedOperationException if and only if {@link #isMetaObject()} returns
     *             <code>false</code> for the same value.
     * @throws DebugException when guest language code throws an exception
     * @see #isMetaObject()
     * @since 20.1.0
     */
    public String getMetaSimpleName() {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.asString(INTEROP.getMetaSimpleName(value));
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a metaobject", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if the given instance is an instance of this value, else
     * <code>false</code>. See {@link InteropLibrary#isMetaInstance(Object, Object)} for detailed
     * description.
     *
     * @param instance the instance value to check.
     * @throws UnsupportedOperationException if and only if {@link #isMetaObject()} returns
     *             <code>false</code> for the same value.
     * @throws DebugException when guest language code throws an exception
     * @see #isMetaObject()
     * @since 20.1.0
     */
    public boolean isMetaInstance(DebugValue instance) {
        if (!isReadable()) {
            throw new UnsupportedOperationException("Value is not readable");
        }
        try {
            Object value = get();
            return INTEROP.isMetaInstance(value, instance.get());
        } catch (ThreadDeath td) {
            throw td;
        } catch (UnsupportedMessageException uex) {
            throw new UnsupportedOperationException("Not a metaobject", uex);
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Get a list of breakpoints installed to the value's session and whose
     * {@link Breakpoint.Builder#rootInstance(DebugValue) root instance} is this value.
     *
     * @return a list of breakpoints with this value as root instance
     * @since 19.3.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public final List<Breakpoint> getRootInstanceBreakpoints() {
        Object value = get();
        List<Breakpoint>[] breakpoints = new List[]{null};
        getSession().visitBreakpoints(new Consumer<Breakpoint>() {
            @Override
            public void accept(Breakpoint b) {
                if (b.getRootInstance() == value) {
                    if (breakpoints[0] == null) {
                        breakpoints[0] = new LinkedList<>();
                    }
                    breakpoints[0].add(b);
                }
            }
        });
        return breakpoints[0] != null ? breakpoints[0] : Collections.emptyList();
    }

    /**
     * Provides properties representing an internal structure of this value. The returned collection
     * is not thread-safe. If the value is not {@link #isReadable() readable} then <code>null</code>
     * is returned.
     *
     * @return a collection of property values, or </code>null</code> when the value does not have
     *         any concept of properties.
     * @throws DebugException when guest language code throws an exception
     * @since 0.19
     */
    public final Collection<DebugValue> getProperties() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object value = get();
        try {
            return getProperties(value, getSession(), resolveLanguage(), null);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    static ValuePropertiesCollection getProperties(Object value, DebuggerSession session, LanguageInfo language, DebugScope scope) {
        if (INTEROP.hasMembers(value)) {
            Object keys;
            try {
                keys = INTEROP.getMembers(value, true);
            } catch (UnsupportedMessageException e) {
                return null;
            }
            return new ValuePropertiesCollection(session, language, value, keys, scope);
        }
        return null;
    }

    /**
     * Get a property value by its name.
     *
     * @param name name of a property
     * @return the property value, or <code>null</code> if the property does not exist.
     * @throws DebugException when guest language code throws an exception
     * @since 19.0
     */
    public final DebugValue getProperty(String name) throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object value = get();
        if (value != null) {
            try {
                if (!INTEROP.isMemberExisting(value, name)) {
                    return null;
                } else {
                    return new DebugValue.ObjectMemberValue(getSession(), resolveLanguage(), null, value, name);
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        } else {
            return null;
        }
    }

    /**
     * Returns <code>true</code> if this value represents an array, <code>false</code> otherwise.
     *
     * @throws DebugException when guest language code throws an exception
     * @since 0.19
     */
    public final boolean isArray() throws DebugException {
        if (!isReadable()) {
            return false;
        }
        return INTEROP.hasArrayElements(get());
    }

    /**
     * Provides array elements when this value represents an array. To test if this value represents
     * an array, check {@link #isArray()}.
     *
     * @return a list of array elements, or <code>null</code> when the value does not represent an
     *         array.
     * @throws DebugException when guest language code throws an exception
     * @since 0.19
     */
    public final List<DebugValue> getArray() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object value = get();
        if (INTEROP.hasArrayElements(value)) {
            return new ValueInteropList(getSession(), resolveLanguage(), value);
        }
        return null;
    }

    /**
     * Returns the underlying guest value object held by this {@link DebugValue}.
     *
     * This method is permitted only if the guest language class is available. This is the case if
     * you want to utilize the Debugger API directly from within a guest language, or if you are an
     * instrument bound/dependent on a specific language.
     *
     * @param languageClass the Truffle language class for a given guest language
     * @return the guest language object or null if the language differs from the language that
     *         created the underlying {@link DebugValue}
     *
     * @since 20.1
     */
    public Object getRawValue(Class<? extends TruffleLanguage<?>> languageClass) {
        Objects.requireNonNull(languageClass);
        RootNode rootNode = getScope().getRoot();
        if (rootNode == null) {
            return null;
        }
        // check if language class of the root node corresponds to the input language
        TruffleLanguage<?> language = Debugger.ACCESSOR.nodeSupport().getLanguage(rootNode);
        return language != null && language.getClass() == languageClass ? get() : null;
    }

    /**
     * Converts the value to a language-specific string representation. Is the same as
     * {@link #toDisplayString(boolean) toDisplayString(true)}.
     *
     * @see #toDisplayString(boolean)
     * @since 20.1
     */
    public final String toDisplayString() {
        return toDisplayString(true);
    }

    /**
     * Converts the value to a language-specific string representation.
     *
     * @param allowSideEffects whether side-effects are allowed in the production of the string.
     * @since 20.1
     */
    public final String toDisplayString(boolean allowSideEffects) throws DebugException {
        if (!isReadable()) {
            return "<not readable>";
        }
        try {
            Object stringValue = INTEROP.toDisplayString(getLanguageView(), allowSideEffects);
            return INTEROP.asString(stringValue);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    final Object getLanguageView() {
        LanguageInfo language = resolveLanguage();
        Object value = get();
        if (language == null) {
            return value;
        } else {
            return getDebugger().getEnv().getLanguageView(language, value);
        }
    }

    final LanguageInfo resolveLanguage() {
        LanguageInfo languageInfo;
        if (preferredLanguage != null) {
            languageInfo = preferredLanguage;
        } else if (getScope() != null && getScope().getLanguage() != null) {
            languageInfo = getScope().getLanguage();
        } else {
            languageInfo = getOriginalLanguage();
        }
        return languageInfo;
    }

    /**
     * Get a meta-object of this value, if any. The meta-object represents a description of the
     * value, reveals it's kind and it's features. See {@link InteropLibrary#getMetaObject(Object)}
     * for detailed description.
     *
     * @return a value representing the meta-object, or <code>null</code>
     * @throws DebugException when guest language code throws an exception
     * @since 0.22
     */
    public final DebugValue getMetaObject() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object view = getLanguageView();
        try {
            if (INTEROP.hasMetaObject(view)) {
                return new HeapValue(getSession(), resolveLanguage(), null, INTEROP.getMetaObject(view));
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
        return null;
    }

    /**
     * Get a source location where this value is declared, if any.
     *
     * @return a source location of the object, or <code>null</code>
     * @throws DebugException when guest language code throws an exception
     * @since 0.22
     */
    public final SourceSection getSourceLocation() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        try {
            Object obj = getLanguageView();
            if (INTEROP.hasSourceLocation(obj)) {
                return getSession().resolveSection(INTEROP.getSourceLocation(obj));
            } else {
                return null;
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Returns <code>true</code> if this value can be executed (represents a guest language
     * function), else <code>false</code>.
     *
     * @since 19.0
     */
    public final boolean canExecute() throws DebugException {
        if (!isReadable()) {
            return false;
        }
        Object value = get();
        try {
            return INTEROP.isExecutable(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Executes the executable represented by this value.
     *
     * @param arguments Arguments passed to the executable
     * @return the result of the execution
     * @throws DebugException when guest language code throws an exception
     * @see #canExecute()
     * @since 19.0
     */
    public final DebugValue execute(DebugValue... arguments) throws DebugException {
        Object value = get();
        Object[] args = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            args[i] = arguments[i].get();
        }
        try {
            Object retValue = INTEROP.execute(value, args);
            return new HeapValue(getSession(), resolveLanguage(), null, retValue);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Get the original language that created the value, if any. This method will return
     * <code>null</code> for values representing a primitive value, or objects that are not
     * associated with any language.
     *
     * @return the language, or <code>null</code> when no language can be identified as the creator
     *         of the value.
     * @throws DebugException when guest language code throws an exception
     * @since 0.27
     */
    public final LanguageInfo getOriginalLanguage() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object obj = get();
        if (obj == null) {
            return null;
        }
        InteropLibrary lib = InteropLibrary.getFactory().getUncached(obj);
        if (lib.hasLanguage(obj)) {
            try {
                return getSession().getDebugger().getEnv().getLanguageInfo(lib.getLanguage(obj));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
        return null;
    }

    /**
     * Returns a debug value that presents itself as seen by the provided language. The language
     * affects the output of {@link #toDisplayString()}, {@link #getMetaObject()},
     * {@link #getSourceLocation()} and other methods that provide various representations of the
     * value. The {@link #getOriginalLanguage() original language} of the returned value remains the
     * same as of this value.
     *
     * @param language a language to get the value representation of
     * @return the value as represented in the language
     * @since 0.27
     */
    public final DebugValue asInLanguage(LanguageInfo language) {
        if (preferredLanguage == language) {
            return this;
        }
        return createAsInLanguage(language);
    }

    abstract DebugValue createAsInLanguage(LanguageInfo language);

    abstract DebuggerSession getSession();

    final Debugger getDebugger() {
        return getSession().getDebugger();
    }

    /**
     * Returns a string representation of the debug value.
     *
     * @since 0.17
     */
    @Override
    public String toString() {
        return "DebugValue(name=" + getName() + ", value = " + (isReadable() ? toDisplayString() : "<not readable>") + ")";
    }

    abstract static class AbstractDebugValue extends DebugValue {

        final DebuggerSession session;

        AbstractDebugValue(DebuggerSession session, LanguageInfo preferredLanguage) {
            super(preferredLanguage);
            this.session = session;
        }

        @Override
        @SuppressWarnings("deprecation")
        public final <T> T as(Class<T> clazz) throws DebugException {
            if (!isReadable()) {
                throw new IllegalStateException("Value is not readable");
            }

            try {
                if (clazz == String.class) {
                    Object val = get();
                    Object stringValue;
                    if (INTEROP.isMetaObject(val)) {
                        stringValue = INTEROP.getMetaQualifiedName(val);
                    } else {
                        stringValue = INTEROP.toDisplayString(getLanguageView());
                    }
                    return clazz.cast(INTEROP.asString(stringValue));
                } else if (clazz == Number.class || clazz == Boolean.class) {
                    return convertToPrimitive(clazz);
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
            throw new UnsupportedOperationException();
        }

        private <T> T convertToPrimitive(Class<T> clazz) {
            Object val = get();
            if (clazz.isInstance(val)) {
                return clazz.cast(val);
            }
            return clazz.cast(Debugger.ACCESSOR.engineSupport().convertPrimitive(val, clazz));
        }

        @Override
        final DebuggerSession getSession() {
            return session;
        }
    }

    static class HeapValue extends AbstractDebugValue {

        private final String name;
        private Object value;

        HeapValue(DebuggerSession session, String name, Object value) {
            this(session, null, name, value);
        }

        HeapValue(DebuggerSession session, LanguageInfo preferredLanguage, String name, Object value) {
            super(session, preferredLanguage);
            this.name = name;
            this.value = value;
            assert value != null;
        }

        @Override
        Object get() {
            return value;
        }

        @Override
        public void set(DebugValue expression) {
            value = expression.get();
        }

        @Override
        public void set(Object primitiveValue) {
            checkPrimitive(primitiveValue);
            value = primitiveValue;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isReadable() {
            return true;
        }

        @Override
        public boolean isWritable() {
            return true;
        }

        @Override
        public boolean hasReadSideEffects() {
            return false;
        }

        @Override
        public boolean hasWriteSideEffects() {
            return false;
        }

        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new HeapValue(session, language, name, value);
        }

    }

    abstract static class AbstractDebugCachedValue extends AbstractDebugValue {

        private volatile Object cachedValue;

        AbstractDebugCachedValue(DebuggerSession session, LanguageInfo preferredLanguage) {
            super(session, preferredLanguage);
        }

        @Override
        final Object get() {
            Object value = cachedValue;
            if (value == null) {
                synchronized (this) {
                    value = cachedValue;
                    if (value == null) {
                        value = readValue();
                        cachedValue = value;
                    }
                }
            }
            return value;
        }

        abstract Object readValue();

        final void resetCachedValue() {
            this.cachedValue = null;
        }
    }

    static final class ObjectMemberValue extends AbstractDebugCachedValue {

        private final Object object;
        private final String member;
        private final DebugScope scope;

        ObjectMemberValue(DebuggerSession session, LanguageInfo preferredLanguage, DebugScope scope, Object object, String member) {
            super(session, preferredLanguage);
            this.object = object;
            this.member = member;
            this.scope = scope;
        }

        @Override
        Object readValue() {
            checkValid();
            try {
                return INTEROP.readMember(object, member);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public String getName() {
            return String.valueOf(member);
        }

        @Override
        public boolean isReadable() {
            checkValid();
            return INTEROP.isMemberReadable(object, member);
        }

        @Override
        public boolean isWritable() {
            checkValid();
            return INTEROP.isMemberWritable(object, member);
        }

        @Override
        public boolean hasReadSideEffects() {
            checkValid();
            return INTEROP.hasMemberReadSideEffects(object, member);
        }

        @Override
        public boolean hasWriteSideEffects() {
            checkValid();
            return INTEROP.hasMemberWriteSideEffects(object, member);
        }

        @Override
        public boolean isInternal() {
            checkValid();
            return INTEROP.isMemberInternal(object, member);
        }

        @Override
        public DebugScope getScope() {
            checkValid();
            return scope;
        }

        @Override
        public void set(DebugValue value) {
            checkValid();
            try {
                Object newValue = value.get();
                INTEROP.writeMember(object, member, newValue);
                resetCachedValue();
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public void set(Object primitiveValue) {
            checkValid();
            checkPrimitive(primitiveValue);
            try {
                INTEROP.writeMember(object, member, primitiveValue);
                resetCachedValue();
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new ObjectMemberValue(session, language, scope, object, member);
        }

        private void checkValid() {
            if (scope != null) {
                scope.verifyValidState();
            }
        }
    }

    static final class ArrayElementValue extends AbstractDebugCachedValue {

        private final Object array;
        private final long index;
        private final DebugScope scope;

        ArrayElementValue(DebuggerSession session, LanguageInfo preferredLanguage, DebugScope scope, Object array, long index) {
            super(session, preferredLanguage);
            this.array = array;
            this.index = index;
            this.scope = scope;
        }

        @Override
        Object readValue() {
            checkValid();
            try {
                return INTEROP.readArrayElement(array, index);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public String getName() {
            return String.valueOf(index);
        }

        @Override
        public boolean isReadable() {
            checkValid();
            return INTEROP.isArrayElementReadable(array, index);
        }

        @Override
        public boolean isWritable() {
            checkValid();
            return INTEROP.isArrayElementWritable(array, index);
        }

        @Override
        public boolean hasReadSideEffects() {
            checkValid();
            return false;
        }

        @Override
        public boolean hasWriteSideEffects() {
            checkValid();
            return false;
        }

        @Override
        public boolean isInternal() {
            checkValid();
            return false;
        }

        @Override
        public DebugScope getScope() {
            checkValid();
            return scope;
        }

        @Override
        public void set(DebugValue value) {
            checkValid();
            try {
                Object newValue = value.get();
                INTEROP.writeArrayElement(array, index, newValue);
                resetCachedValue();
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public void set(Object primitiveValue) {
            checkValid();
            checkPrimitive(primitiveValue);
            try {
                INTEROP.writeArrayElement(array, index, primitiveValue);
                resetCachedValue();
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new ArrayElementValue(session, language, scope, array, index);
        }

        private void checkValid() {
            if (scope != null) {
                scope.verifyValidState();
            }
        }
    }

    private static void checkPrimitive(Object value) {
        Class<?> clazz;
        if (value == null || !((clazz = value.getClass()) == Byte.class ||
                        clazz == Short.class ||
                        clazz == Integer.class ||
                        clazz == Long.class ||
                        clazz == Float.class ||
                        clazz == Double.class ||
                        clazz == Character.class ||
                        clazz == Boolean.class ||
                        clazz == String.class)) {
            throw new IllegalArgumentException(value + " is not primitive.");
        }
    }

}
