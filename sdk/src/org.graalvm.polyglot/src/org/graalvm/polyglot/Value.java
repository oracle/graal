/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRules;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueDispatch;
import org.graalvm.polyglot.proxy.Proxy;

/**
 * Represents a polyglot value that can be accessed using a set of language agnostic operations.
 * Polyglot values represent values from {@link #isHostObject() host} or guest language. Polyglot
 * values are bound to a {@link Context context}. If the context is closed then all value operations
 * throw an {@link IllegalStateException}.
 * <p>
 * Polyglot values have one of the following type combinations:
 * <ul>
 * <li>{@link #isNull() Null}: This value represents a <code>null</code> like value. Certain
 * languages might use a different name or use multiple values to represent <code>null</code> like
 * values.
 * <li>{@link #isNumber() Number}: This value represents a floating or fixed point number. The
 * number value may be accessed as {@link #asByte() byte}, {@link #asShort() short}, {@link #asInt()
 * int}, {@link #asLong() long}, {@link #asBigInteger()} BigInteger}, {@link #asFloat() float}, or
 * {@link #asDouble() double} value.
 * <li>{@link #isBoolean() Boolean}. This value represents a boolean value. The boolean value can be
 * accessed using {@link #asBoolean()}.
 * <li>{@link #isString() String}: This value represents a string value. The string value can be
 * accessed using {@link #asString()}.
 * <li>{@link #isDate() Date}, {@link #isTime() Time} or {@link #isTimeZone() Timezone}: This value
 * represents a date, time or timezone. Multiple types may return <code>true</code> at the same
 * time.
 * <li>{@link #isDuration() Duration}: This value represents a duration value. The duration value
 * can be accessed using {@link #asDuration()}.
 * <li>{@link #isHostObject() Host Object}: This value represents a value of the host language
 * (Java). The original Java value can be accessed using {@link #asHostObject()}.
 * <li>{@link #isProxyObject() Proxy Object}: This value represents a {@link Proxy proxy} value.
 * <li>{@link #isNativePointer() Native Pointer}: This value represents a native pointer. The native
 * pointer value can be accessed using {@link #asNativePointer()}.
 * <li>{@link #isException() Exception}: This value represents an exception object. The exception
 * can be thrown using {@link #throwException()}.
 * <li>{@link #isMetaObject() Meta-Object}: This value represents a metaobject. Access metaobject
 * operations using {@link #getMetaSimpleName()}, {@link #getMetaQualifiedName()} and
 * {@link #isMetaInstance(Object)}.
 * <li>{@link #isIterator() Iterator}: This value represents an iterator. The iterator can be
 * iterated using {@link #hasIteratorNextElement()} and {@link #getIteratorNextElement()}.
 * </ul>
 * In addition any value may have one or more of the following traits:
 * <ul>
 * <li>{@link #hasArrayElements() Array Elements}: This value may contain array elements. The array
 * indices always start with <code>0</code>, also if the language uses a different style.
 * <li>{@link #hasMembers() Members}: This value may contain members. Members are structural
 * elements of an object. For example, the members of a Java object are all public methods and
 * fields. Members are accessible using {@link #getMember(String)}.
 * <li>{@link #canExecute() Executable}: This value can be {@link #execute(Object...) executed}.
 * This indicates that the value represents an element that can be executed. Guest language examples
 * for executable elements are functions, methods, closures or promises.
 * <li>{@link #canInstantiate() Instantiable}: This value can be {@link #newInstance(Object...)
 * instantiated}. For example, Java classes are instantiable.
 * <li>{@link #hasBufferElements() Buffer Elements}: This value may contain buffer elements. The
 * buffer indices always start with <code>0</code>, also if the language uses a different style.
 * <li>{@link #hasIterator() Iterable}: This value {@link #getIterator() provides} an
 * {@link #isIterator() iterator} which can be used to {@link #getIteratorNextElement() iterate}
 * value elements. For example, Guest language arrays are iterable.
 * <li>{@link #hasHashEntries()} Hash Entries}: This value represents a map.
 * <li>{@link #hasMetaParents()} Meta Parents}: This value represents Array Elements of Meta
 * Objects.
 * </ul>
 * <p>
 * In addition to the language agnostic types, the language specific type can be accessed using
 * {@link #getMetaObject()}. The identity of value objects is unspecified and should not be relied
 * upon. For example, multiple calls to {@link #getArrayElement(long)} with the same index might
 * return the same or different instances of {@link Value}. The {@link #equals(Object) equality} of
 * values is based on the identity of the value instance. All values return a human-readable
 * {@link #toString() string} for debugging, formatted by the original language.
 * <p>
 * Polyglot values may be converted to host objects using {@link #as(Class)}. In addition values may
 * be created from Java values using {@link Context#asValue(Object)}.
 *
 * <h3>Naive and aware dates and times</h3>
 * <p>
 * If a date or time value has a {@link #isTimeZone() timezone} then it is called <i>aware</i>,
 * otherwise <i>naive</i>.
 * <p>
 * An aware time and date has sufficient knowledge of applicable algorithmic and political time
 * adjustments, such as time zone and daylight saving time information, to locate itself relative to
 * other aware objects. An aware object is used to represent a specific moment in time that is not
 * open to interpretation.
 * <p>
 * A naive time and date does not contain enough information to unambiguously locate itself relative
 * to other date/time objects. Whether a naive object represents Coordinated Universal Time (UTC),
 * local time, or time in some other timezone is purely up to the program, just like it is up to the
 * program whether a particular number represents metres, miles, or mass. Naive objects are easy to
 * understand and to work with, at the cost of ignoring some aspects of reality.
 *
 * <h3>Scoped Values</h3>
 *
 * In the case of a guest-to-host callback, a value may be passed as a parameter. These values may
 * represent objects that are only valid during the invocation of the callback function, i.e. they
 * are scoped, with the scope being the callback function. If enabled via the corresponding settings
 * in {@link HostAccess}, such values are released when the function returns, with all future
 * invocations of value operations throwing an exception.
 *
 * If an embedder wishes to extend the scope of the value beyond the callback's return, the value
 * can be {@linkplain Value#pin() pinned}, such that it is not released automatically.
 *
 * @see Context
 * @see Engine
 * @see PolyglotException
 * @since 19.0
 */
public final class Value extends AbstractValue {

    Value(AbstractValueDispatch dispatch, Object context, Object receiver) {
        super(dispatch, context, receiver);
    }

    /**
     * Returns the metaobject that is associated with this value or <code>null</code> if no
     * metaobject is available. The metaobject represents a description of the object, reveals it's
     * kind and it's features. Some information that a metaobject might define includes the base
     * object's type, interface, class, methods, attributes, etc.
     * <p>
     * The returned value returns <code>true</code> for {@link #isMetaObject()} and provides
     * implementations for {@link #getMetaSimpleName()}, {@link #getMetaQualifiedName()}, and
     * {@link #isMetaInstance(Object)}.
     * <p>
     * This method does not cause any observable side-effects.
     *
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @see #isMetaObject()
     * @since 19.0 revised in 20.1
     */
    public Value getMetaObject() {
        return (Value) dispatch.getMetaObject(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if the value represents a metaobject. Metaobjects may be values
     * that naturally occur in a language or they may be returned by {@link #getMetaObject()}. A
     * metaobject represents a description of the object, reveals its kind and its features. Returns
     * <code>false</code> by default. Metaobjects are often also {@link #canInstantiate()
     * instantiable}, but not necessarily.
     * <p>
     * <b>Sample interpretations:</b> In Java an instance of the type {@link Class} is a metaobject.
     * In JavaScript any function instance is a metaobject. For example, the metaobject of a
     * JavaScript class is the associated constructor function.
     * <p>
     * This method does not cause any observable side-effects. If this method is implemented then
     * also {@link #getMetaQualifiedName()}, {@link #getMetaSimpleName()} and
     * {@link #isMetaInstance(Object)} must be implemented as well.
     *
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @see #getMetaQualifiedName()
     * @see #getMetaSimpleName()
     * @see #isMetaInstance(Object)
     * @see #getMetaObject()
     * @since 20.1
     */
    public boolean isMetaObject() {
        return dispatch.isMetaObject(this.context, receiver);
    }

    /**
     * Returns the qualified name of a metaobject as {@link #isString() String}.
     * <p>
     * <b>Sample interpretations:</b> The qualified name of a Java class includes the package name
     * and its class name. JavaScript does not have the notion of qualified name and therefore
     * returns the {@link #getMetaSimpleName() simple name} instead.
     *
     * @throws UnsupportedOperationException if and only if {@link #isMetaObject()} returns
     *             <code>false</code> for the same value.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 20.1
     */
    public String getMetaQualifiedName() {
        return dispatch.getMetaQualifiedName(this.context, receiver);
    }

    /**
     * Returns the simple name of a metaobject as {@link #isString() string}.
     * <p>
     * <b>Sample interpretations:</b> The simple name of a Java class is the class name.
     *
     * @throws UnsupportedOperationException if and only if {@link #isMetaObject()} returns
     *             <code>false</code> for the same value.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 20.1
     */
    public String getMetaSimpleName() {
        return dispatch.getMetaSimpleName(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if the given instance is an instance of this value, else
     * <code>false</code>. The instance value is subject to polyglot value mapping rules as
     * described in {@link Context#asValue(Object)}.
     * <p>
     * <b>Sample interpretations:</b> A Java object is an instance of its returned
     * {@link Object#getClass() class}.
     * <p>
     *
     * @param instance the instance object to check.
     * @throws UnsupportedOperationException if and only if {@link #isMetaObject()} returns
     *             <code>false</code> for the same value.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 20.1
     */
    public boolean isMetaInstance(Object instance) {
        return dispatch.isMetaInstance(this.context, receiver, instance);
    }

    /**
     * Returns <code>true</code> if the value represents a metaobject and the metaobject has meta
     * parents. Returns <code>false</code> by default.
     * <p>
     * <b>Sample interpretations:</b> In Java an instance of the type {@link Class} is a metaobject.
     * Further, the superclass and the implemented interfaces types of that type constitute the meta
     * parents. In JavaScript any function instance is a metaobject. For example, the metaobject of
     * a JavaScript class is the associated constructor function.
     * <p>
     * This method does not cause any observable side-effects. If this method is implemented then
     * also {@link #getMetaParents()} must be implemented as well.
     *
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @see #getMetaParents()
     * @since 22.2
     */
    public boolean hasMetaParents() {
        return dispatch.hasMetaParents(this.context, receiver);
    }

    /**
     * Returns the meta parents of a meta object as an array object {@link #hasArrayElements()}.
     * This method does not cause any observable side-effects. If this method is implemented then
     * also {@link #hasMetaParents()} must be implemented as well.
     *
     * @throws IllegalStateException if the context is already closed.
     * @throws UnsupportedOperationException if the value does not have any
     *             {@link #hasMetaParents()} meta parents.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @see #hasMetaParents()
     * @since 22.2
     */
    public Value getMetaParents() {
        return (Value) dispatch.getMetaParents(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this polyglot value has array elements. In this case array
     * elements can be accessed using {@link #getArrayElement(long)},
     * {@link #setArrayElement(long, Object)}, {@link #removeArrayElement(long)} and the array size
     * can be queried using {@link #getArraySize()}.
     *
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 19.0
     */
    public boolean hasArrayElements() {
        return dispatch.hasArrayElements(this.context, receiver);
    }

    /**
     * Returns the array element of a given index. Polyglot arrays start with index <code>0</code>,
     * independent of the guest language. The given array index must be greater or equal to 0.
     *
     * @throws ArrayIndexOutOfBoundsException if the array index does not exist.
     * @throws UnsupportedOperationException if the value does not have any
     *             {@link #hasArrayElements() array elements} or if the index exists but is not
     *             readable.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 19.0
     */
    public Value getArrayElement(long index) {
        return (Value) dispatch.getArrayElement(this.context, receiver, index);
    }

    /**
     * Sets the value at a given index. Polyglot arrays start with index <code>0</code>, independent
     * of the guest language. The array element value is subject to polyglot value mapping rules as
     * described in {@link Context#asValue(Object)}.
     *
     * @throws ArrayIndexOutOfBoundsException if the array index does not exist.
     * @throws ClassCastException if the provided value type is not allowed to be written.
     * @throws UnsupportedOperationException if the value does not have any
     *             {@link #hasArrayElements() array elements} or if the index exists but is not
     *             modifiable.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 19.0
     */
    public void setArrayElement(long index, Object value) {
        dispatch.setArrayElement(this.context, receiver, index, value);
    }

    /**
     * Removes an array element at a given index. Returns <code>true</code> if the underlying array
     * element could be removed, otherwise <code>false</code>.
     *
     * @throws ArrayIndexOutOfBoundsException if the array index does not exist.
     * @throws UnsupportedOperationException if the value does not have any
     *             {@link #hasArrayElements() array elements} or if the index exists but is not
     *             removable.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 19.0
     */
    public boolean removeArrayElement(long index) {
        return dispatch.removeArrayElement(this.context, receiver, index);
    }

    /**
     * Returns the array size for values with array elements.
     *
     * @throws UnsupportedOperationException if the value does not have any
     *             {@link #hasArrayElements() array elements}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 19.0
     */
    public long getArraySize() {
        return dispatch.getArraySize(this.context, receiver);
    }

    // region Buffer Methods

    /**
     * Returns {@code true} if the receiver may have buffer elements. In this case, the buffer size
     * can be queried using {@link #getBufferSize()} and elements can be read using
     * {@link #readBufferByte(long)}, {@link #readBufferShort(ByteOrder, long)},
     * {@link #readBufferInt(ByteOrder, long)}, {@link #readBufferLong(ByteOrder, long)},
     * {@link #readBufferFloat(ByteOrder, long)} and {@link #readBufferDouble(ByteOrder, long)}. If
     * {@link #isBufferWritable()} returns {@code true}, then buffer elements can also be written
     * using {@link #writeBufferByte(long, byte)},
     * {@link #writeBufferShort(ByteOrder, long, short)},
     * {@link #writeBufferInt(ByteOrder, long, int)},
     * {@link #writeBufferLong(ByteOrder, long, long)},
     * {@link #writeBufferFloat(ByteOrder, long, float)} and
     * {@link #writeBufferDouble(ByteOrder, long, double)}.
     * <p>
     * Invoking this method does not cause any observable side-effects.
     *
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @see #hasBufferElements()
     * @since 21.1
     */
    public boolean hasBufferElements() {
        return dispatch.hasBufferElements(this.context, receiver);
    }

    /**
     * Returns true if the receiver object is a modifiable buffer. In this case, elements can be
     * written using {@link #writeBufferByte(long, byte)},
     * {@link #writeBufferShort(ByteOrder, long, short)},
     * {@link #writeBufferInt(ByteOrder, long, int)},
     * {@link #writeBufferLong(ByteOrder, long, long)},
     * {@link #writeBufferFloat(ByteOrder, long, float)} and
     * {@link #writeBufferDouble(ByteOrder, long, double)}.
     * <p>
     * Invoking this method does not cause any observable side-effects.
     *
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements}.
     * @since 21.1
     */
    public boolean isBufferWritable() throws UnsupportedOperationException {
        return dispatch.isBufferWritable(this.context, receiver);
    }

    /**
     * Returns the buffer size in bytes for values with buffer elements.
     * <p>
     * Invoking this method does not cause any observable side-effects.
     *
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements}.
     * @since 21.1
     */
    public long getBufferSize() throws UnsupportedOperationException {
        return dispatch.getBufferSize(this.context, receiver);
    }

    /**
     * Reads the byte at the given byte offset from the start of the buffer.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this method does not cause any observable side-effects.
     *
     * @param byteOffset the offset, in bytes, from the start of the buffer at which the byte will
     *            be read.
     * @return the byte at the given byte offset from the start of the buffer.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= </code>{@link #getBufferSize()}.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public byte readBufferByte(long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        return dispatch.readBufferByte(this.context, receiver, byteOffset);
    }

    /**
     * Reads bytes from the receiver object into the specified byte array.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     * <p>
     * <b>Example</b> reading into an output stream using a 4k auxiliary byte array:
     * 
     * <pre>
     * Value val = ...
     * assert val.hasBufferElements();
     * try (OutputStream out = ...) {
     *     byte[] aux = new byte[4096];
     *     long bufferSize = val.getBufferSize();
     *     for (long offset = 0; offset < bufferSize; offset += aux.length) {
     *         int bytesToRead = (int) Math.min(bufferSize - offset, aux.length);
     *         val.readBuffer(offset, aux, 0, bytesToRead);
     *         out.write(aux, 0, bytesToRead);
     *     }
     * }
     * </pre>
     *
     * In case the goal is to read the whole contents into a single byte array, the easiest way is
     * to do that through {@link org.graalvm.polyglot.io.ByteSequence}:
     * 
     * <pre>
     * byte[] byteArray = val.as(ByteSequence.class).toByteArray();
     * </pre>
     * 
     * @param byteOffset offset in the buffer to start reading from.
     * @param destination byte array to write the read bytes into.
     * @param destinationOffset offset in the destination array to start writing from.
     * @param length number of bytes to read.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || length < 0 || byteOffset + length > </code>{@link #getBufferSize()}<code> || destinationOffset < 0 || destinationOffset + length > destination.length</code>
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 24.0
     */
    public void readBuffer(long byteOffset, byte[] destination, int destinationOffset, int length) throws UnsupportedOperationException, IndexOutOfBoundsException {
        Objects.requireNonNull(destination, "destination");
        Objects.checkFromIndexSize(destinationOffset, length, destination.length);
        dispatch.readBuffer(this.context, receiver, byteOffset, destination, destinationOffset, length);
    }

    /**
     * Writes the given byte at the given byte offset from the start of the buffer.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     *
     * @param byteOffset the offset, in bytes, from the start of the buffer at which the byte will
     *            be written.
     * @param value the byte value to be written.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= </code>{@link #getBufferSize()}.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements} or is not {@link #isBufferWritable() modifiable}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public void writeBufferByte(long byteOffset, byte value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        dispatch.writeBufferByte(this.context, receiver, byteOffset, value);
    }

    /**
     * Reads the short at the given byte offset from the start of the buffer in the given byte
     * order.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this method does not cause any observable side-effects.
     *
     * @param order the order in which to read the individual bytes of the short.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the short
     *            will be read.
     * @return the short at the given byte offset from the start of the buffer.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 1</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public short readBufferShort(ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        return dispatch.readBufferShort(this.context, receiver, order, byteOffset);
    }

    /**
     * Writes the given short in the given byte order at the given byte offset from the start of the
     * buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     *
     * @param order the order in which to write the individual bytes of the short.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the short
     *            will be written.
     * @param value the short value to be written.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 1</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements} or is not {@link #isBufferWritable() modifiable}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public void writeBufferShort(ByteOrder order, long byteOffset, short value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        dispatch.writeBufferShort(this.context, receiver, order, byteOffset, value);
    }

    /**
     * Reads the int at the given byte offset from the start of the buffer in the given byte order.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this method does not cause any observable side-effects.
     *
     * @param order the order in which to read the individual bytes of the int.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the int will
     *            be read.
     * @return the int at the given byte offset from the start of the buffer.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 3</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public int readBufferInt(ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        return dispatch.readBufferInt(this.context, receiver, order, byteOffset);
    }

    /**
     * Writes the given int in the given byte order at the given byte offset from the start of the
     * buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     *
     * @param order the order in which to write the individual bytes of the int.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the int will
     *            be written.
     * @param value the int value to be written.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 3</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements} or is not {@link #isBufferWritable() modifiable}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public void writeBufferInt(ByteOrder order, long byteOffset, int value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        dispatch.writeBufferInt(this.context, receiver, order, byteOffset, value);
    }

    /**
     * Reads the long at the given byte offset from the start of the buffer in the given byte order.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this method does not cause any observable side-effects.
     *
     * @param order the order in which to read the individual bytes of the long.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the int will
     *            be read.
     * @return the int at the given byte offset from the start of the buffer.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 7</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public long readBufferLong(ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        return dispatch.readBufferLong(this.context, receiver, order, byteOffset);
    }

    /**
     * Writes the given long in the given byte order at the given byte offset from the start of the
     * buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     *
     * @param order the order in which to write the individual bytes of the long.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the int will
     *            be written.
     * @param value the int value to be written.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 7</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements} or is not {@link #isBufferWritable() modifiable}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public void writeBufferLong(ByteOrder order, long byteOffset, long value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        dispatch.writeBufferLong(this.context, receiver, order, byteOffset, value);
    }

    /**
     * Reads the float at the given byte offset from the start of the buffer in the given byte
     * order.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this method does not cause any observable side-effects.
     *
     * @param order the order in which to read the individual bytes of the float.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the float
     *            will be read.
     * @return the float at the given byte offset from the start of the buffer.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 3</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public float readBufferFloat(ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        return dispatch.readBufferFloat(this.context, receiver, order, byteOffset);
    }

    /**
     * Writes the given float in the given byte order at the given byte offset from the start of the
     * buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     *
     * @param order the order in which to read the individual bytes of the float.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the float
     *            will be written.
     * @param value the float value to be written.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 3</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements} or is not {@link #isBufferWritable() modifiable}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public void writeBufferFloat(ByteOrder order, long byteOffset, float value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        dispatch.writeBufferFloat(this.context, receiver, order, byteOffset, value);
    }

    /**
     * Reads the double at the given byte offset from the start of the buffer in the given byte
     * order.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this method does not cause any observable side-effects.
     *
     * @param order the order in which to write the individual bytes of the double.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the double
     *            will be read.
     * @return the double at the given byte offset from the start of the buffer.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 7</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public double readBufferDouble(ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        return dispatch.readBufferDouble(this.context, receiver, order, byteOffset);
    }

    /**
     * Writes the given double in the given byte order at the given byte offset from the start of
     * the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this method is <em>not</em>
     * thread-safe.
     *
     * @param order the order in which to write the individual bytes of the double.
     * @param byteOffset the offset, in bytes, from the start of the buffer from which the double
     *            will be written.
     * @param value the double value to be written.
     * @throws IndexOutOfBoundsException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize()} - 7</code>.
     * @throws UnsupportedOperationException if the value does not have {@link #hasBufferElements
     *             buffer elements} or is not {@link #isBufferWritable() modifiable}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public void writeBufferDouble(ByteOrder order, long byteOffset, double value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        dispatch.writeBufferDouble(this.context, receiver, order, byteOffset, value);
    }

    // endregion

    /**
     * Returns <code>true</code> if this value generally supports containing members. To check
     * whether a value has <i>no</i> members use
     * <code>{@link #getMemberKeys() getMemberKeys()}.{@link Set#isEmpty() isEmpty()}</code>
     * instead. If polyglot value has members, it may also support {@link #getMember(String)},
     * {@link #putMember(String, Object)} and {@link #removeMember(String)}.
     *
     * @see #hasMember(String) To check the existence of members.
     * @see #getMember(String) To read members.
     * @see #putMember(String, Object) To write members.
     * @see #removeMember(String) To remove a member.
     * @see #getMemberKeys() For a list of members.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 19.0
     */
    public boolean hasMembers() {
        return dispatch.hasMembers(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if such a member exists for a given <code>identifier</code>. If the
     * value has no {@link #hasMembers() members} then {@link #hasMember(String)} returns
     * <code>false</code>.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws NullPointerException if the identifier is null.
     * @since 19.0
     */
    public boolean hasMember(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return dispatch.hasMember(this.context, receiver, identifier);
    }

    /**
     * Returns the member with a given <code>identifier</code> or <code>null</code> if the member
     * does not exist.
     *
     * @throws UnsupportedOperationException if the value {@link #hasMembers() has no members} or
     *             the given identifier exists but is not readable.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws NullPointerException if the identifier is null.
     * @since 19.0
     */
    public Value getMember(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return (Value) dispatch.getMember(this.context, receiver, identifier);
    }

    /**
     * Returns a set of all member keys. Calling {@link Set#contains(Object)} with a string key is
     * equivalent to calling {@link #hasMember(String)}. Removing an element from the returned set
     * is equivalent to calling {@link #removeMember(String)}. Adding an element to the set is
     * equivalent to calling {@linkplain #putMember(String, Object) putMember(key, null)}. If the
     * value does not support {@link #hasMembers() members} then an empty unmodifiable set is
     * returned. If the context gets closed while the returned set is still alive, then the set will
     * throw an {@link IllegalStateException} if any methods except Object methods are invoked.
     *
     * @throws IllegalStateException if the context is already {@link Context#close() closed}.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 19.0
     */
    public Set<String> getMemberKeys() {
        return dispatch.getMemberKeys(this.context, receiver);
    }

    /**
     * Sets the value of a member using an identifier. The member value is subject to polyglot value
     * mapping rules as described in {@link Context#asValue(Object)}.
     *
     * @throws IllegalStateException if the context is already {@link Context#close() closed}.
     * @throws UnsupportedOperationException if the value does not have any {@link #hasMembers()
     *             members}, the key does not exist and new members cannot be added, or the existing
     *             member is not modifiable.
     * @throws IllegalArgumentException if the provided value type is not allowed to be written.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws NullPointerException if the identifier is null.
     * @since 19.0
     */
    public void putMember(String identifier, Object value) {
        Objects.requireNonNull(identifier, "identifier");
        dispatch.putMember(this.context, receiver, identifier, value);
    }

    /**
     * Removes a single member from the object. Returns <code>true</code> if the member was
     * successfully removed, <code>false</code> if such a member does not exist.
     *
     * @throws UnsupportedOperationException if the value does not have any {@link #hasMembers()
     *             members} or if the key {@link #hasMember(String) exists} but cannot be removed.
     * @throws IllegalStateException if the context is already {@link Context#close() closed}.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws NullPointerException if the identifier is null.
     * @since 19.0
     */
    public boolean removeMember(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return dispatch.removeMember(this.context, receiver, identifier);
    }

    // executable

    /**
     * Returns <code>true</code> if the value can be {@link #execute(Object...) executed}.
     *
     * @throws IllegalStateException if the underlying context was closed.
     * @see #execute(Object...)
     * @since 19.0
     */
    public boolean canExecute() {
        return dispatch.canExecute(this.context, receiver);
    }

    /**
     * Executes this value if it {@link #canExecute() can} be executed and returns its result. If no
     * result value is expected or needed use {@link #executeVoid(Object...)} for better
     * performance. All arguments are subject to polyglot value mapping rules as described in
     * {@link Context#asValue(Object)}.
     *
     * @throws IllegalStateException if the underlying context was closed.
     * @throws IllegalArgumentException if a wrong number of arguments was provided or one of the
     *             arguments was not applicable.
     * @throws UnsupportedOperationException if this value cannot be executed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws NullPointerException if the arguments array is null.
     * @see #executeVoid(Object...)
     * @since 19.0
     */
    public Value execute(Object... arguments) {
        if (arguments.length == 0) {
            // specialized entry point for zero argument execute calls
            return (Value) dispatch.execute(this.context, receiver);
        } else {
            return (Value) dispatch.execute(this.context, receiver, arguments);
        }
    }

    /**
     * Executes this value if it {@link #canExecute() can} be executed. All arguments are subject to
     * polyglot value mapping rules as described in {@link Context#asValue(Object)}.
     *
     * @throws IllegalStateException if the underlying context was closed.
     * @throws IllegalArgumentException if a wrong number of arguments was provided or one of the
     *             arguments was not applicable.
     * @throws UnsupportedOperationException if this value cannot be executed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws NullPointerException if the arguments array is null.
     * @see #execute(Object...)
     * @since 19.0
     */
    public void executeVoid(Object... arguments) {
        if (arguments.length == 0) {
            // specialized entry point for zero argument execute calls
            dispatch.executeVoid(this.context, receiver);
        } else {
            dispatch.executeVoid(this.context, receiver, arguments);
        }
    }

    /**
     * Returns <code>true</code> if the value can be instantiated. This indicates that the
     * {@link #newInstance(Object...)} can be used with this value. If a value is instantiable it is
     * often also a {@link #isMetaObject()}, but this is not a requirement.
     *
     * @see #isMetaObject()
     * @since 19.0
     */
    public boolean canInstantiate() {
        return dispatch.canInstantiate(this.context, receiver);
    }

    /**
     * Instantiates this value if it {@link #canInstantiate() can} be instantiated. All arguments
     * are subject to polyglot value mapping rules as described in {@link Context#asValue(Object)}.
     *
     * @throws IllegalStateException if the underlying context was closed.
     * @throws IllegalArgumentException if a wrong number of arguments was provided or one of the
     *             arguments was not applicable.
     * @throws UnsupportedOperationException if this value cannot be instantiated.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws NullPointerException if the arguments array is null.
     * @since 19.0
     */
    public Value newInstance(Object... arguments) {
        Objects.requireNonNull(arguments, "arguments");
        return (Value) dispatch.newInstance(this.context, receiver, arguments);
    }

    /**
     * Returns <code>true</code> if the given member exists and can be invoked. Returns
     * <code>false</code> if the member does not exist ({@link #hasMember(String)} returns
     * <code>false</code>), or is not invocable.
     *
     * @param identifier the member identifier
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred.
     * @see #getMemberKeys() For a list of members.
     * @see #invokeMember(String, Object...)
     * @since 19.0
     */
    public boolean canInvokeMember(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return dispatch.canInvoke(this.context, identifier, receiver);
    }

    /**
     * Invokes the given member of this value. Unlike {@link #execute(Object...)}, this is an object
     * oriented execution of a member of an object. To test whether invocation is supported, call
     * {@link #canInvokeMember(String)}. When object oriented semantics are not supported, use
     * <code>{@link #getMember(String)}.{@link #execute(Object...) execute(Object...)}</code>
     * instead.
     *
     * @param identifier the member identifier to invoke
     * @param arguments the invocation arguments
     * @throws UnsupportedOperationException if this member cannot be invoked.
     * @throws PolyglotException if a guest language error occurred during invocation.
     * @throws NullPointerException if the arguments array is null.
     * @see #canInvokeMember(String)
     * @since 19.0
     */
    public Value invokeMember(String identifier, Object... arguments) {
        Objects.requireNonNull(identifier, "identifier");
        if (arguments.length == 0) {
            // specialized entry point for zero argument invoke calls
            return (Value) dispatch.invoke(this.context, receiver, identifier);
        } else {
            return (Value) dispatch.invoke(this.context, receiver, identifier, arguments);
        }
    }

    /**
     * Returns <code>true</code> if this value represents a string.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public boolean isString() {
        return dispatch.isString(this.context, receiver);
    }

    /**
     * Returns the {@link String} value if this value {@link #isString() is} a string. This method
     * returns <code>null</code> if this value represents a {@link #isNull() null} value.
     *
     * @throws ClassCastException if this value could not be converted to string.
     * @throws UnsupportedOperationException if this value does not represent a string.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 19.0
     */
    public String asString() {
        return dispatch.asString(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>int</code>, else <code>false</code>.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @see #asInt()
     * @since 19.0
     */
    public boolean fitsInInt() {
        return dispatch.fitsInInt(this.context, receiver);
    }

    /**
     * Returns an <code>int</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInInt() fits}.
     *
     * @throws NullPointerException if this value represents {@link #isNull() null}.
     * @throws ClassCastException if this value could not be converted.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public int asInt() {
        return dispatch.asInt(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a boolean value.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @see #asBoolean()
     * @since 19.0
     */
    public boolean isBoolean() {
        return dispatch.isBoolean(this.context, receiver);
    }

    /**
     * Returns a <code>boolean</code> representation of this value if it is {@link #isBoolean()
     * boolean}.
     *
     * @throws NullPointerException if this value represents {@link #isNull() null}
     * @throws ClassCastException if this value could not be converted.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public boolean asBoolean() {
        return dispatch.asBoolean(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number}, else
     * <code>false</code>. The number value may be accessed as {@link #asByte() byte},
     * {@link #asShort() short} {@link #asInt() int} {@link #asLong() long}, {@link #asFloat()
     * float} or {@link #asDouble() double} value.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public boolean isNumber() {
        return dispatch.isNumber(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>long</code>, else <code>false</code>.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @see #asLong()
     * @since 19.0
     */
    public boolean fitsInLong() {
        return dispatch.fitsInLong(this.context, receiver);
    }

    /**
     * Returns a <code>long</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInLong() fits}.
     *
     * @throws NullPointerException if this value represents {@link #isNull() null}.
     * @throws ClassCastException if this value could not be converted to long.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public long asLong() {
        return dispatch.asLong(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>BigInteger</code>, else <code>false</code>.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @see #asBigInteger()
     * @since 23.0
     */
    public boolean fitsInBigInteger() {
        return dispatch.fitsInBigInteger(this.context, receiver);
    }

    /**
     * Returns a <code>BigInteger</code> representation of this value if it is {@link #isNumber()
     * number} and the value {@link #fitsInBigInteger() fits}.
     *
     * @throws NullPointerException if this value represents {@link #isNull() null}.
     * @throws ClassCastException if this value could not be converted to BigInteger.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 23.0
     */
    public BigInteger asBigInteger() {
        return dispatch.asBigInteger(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>double</code>, else <code>false</code>.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @see #asDouble()
     * @since 19.0
     */
    public boolean fitsInDouble() {
        return dispatch.fitsInDouble(this.context, receiver);
    }

    /**
     * Returns a <code>double</code> representation of this value if it is {@link #isNumber()
     * number} and the value {@link #fitsInDouble() fits}.
     *
     * @throws NullPointerException if this value represents {@link #isNull() null}.
     * @throws ClassCastException if this value could not be converted.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public double asDouble() {
        return dispatch.asDouble(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>float</code>, else <code>false</code>.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @see #asFloat()
     * @since 19.0
     */
    public boolean fitsInFloat() {
        return dispatch.fitsInFloat(this.context, receiver);
    }

    /**
     * Returns a <code>float</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInFloat() fits}.
     *
     * @throws NullPointerException if this value represents {@link #isNull() null}.
     * @throws ClassCastException if this value could not be converted.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public float asFloat() {
        return dispatch.asFloat(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>byte</code>, else <code>false</code>.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @see #asByte()
     * @since 19.0
     */
    public boolean fitsInByte() {
        return dispatch.fitsInByte(this.context, receiver);
    }

    /**
     * Returns a <code>byte</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInByte() fits}.
     *
     * @throws NullPointerException if this value represents {@link #isNull() null}.
     * @throws ClassCastException if this value could not be converted.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public byte asByte() {
        return dispatch.asByte(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a {@link #isNumber() number} and the value
     * fits in <code>short</code>, else <code>false</code>.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @see #asShort()
     * @since 19.0
     */
    public boolean fitsInShort() {
        return dispatch.fitsInShort(this.context, receiver);
    }

    /**
     * Returns a <code>short</code> representation of this value if it is {@link #isNumber() number}
     * and the value {@link #fitsInShort() fits}.
     *
     * @throws NullPointerException if this value represents {@link #isNull() null}.
     * @throws ClassCastException if this value could not be converted.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public short asShort() {
        return dispatch.asShort(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value is a <code>null</code> like.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public boolean isNull() {
        return dispatch.isNull(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value is a native pointer. The value of the pointer can be
     * accessed using {@link #asNativePointer()}.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public boolean isNativePointer() {
        return dispatch.isNativePointer(this.context, receiver);
    }

    /**
     * Returns the value of the pointer as <code>long</code> value.
     *
     * @throws UnsupportedOperationException if the value is not a pointer.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public long asNativePointer() {
        return dispatch.asNativePointer(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if the value originated form the host language Java. In such a case
     * the value can be accessed using {@link #asHostObject()}.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public boolean isHostObject() {
        return dispatch.isHostObject(this.context, receiver);
    }

    /**
     * Returns the original Java host language object.
     *
     * @throws UnsupportedOperationException if {@link #isHostObject()} is <code>false</code>.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    public <T> T asHostObject() {
        return (T) dispatch.asHostObject(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents a {@link Proxy}. The proxy instance can be
     * unboxed using {@link #asProxyObject()}.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public boolean isProxyObject() {
        return dispatch.isProxyObject(this.context, receiver);
    }

    /**
     * Returns the unboxed instance of the {@link Proxy}. Proxies are not automatically boxed to
     * {@link #isHostObject() host objects} on host language call boundaries (Java methods).
     *
     * @throws UnsupportedOperationException if a value is not a proxy object.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    public <T extends Proxy> T asProxyObject() {
        return (T) dispatch.asProxyObject(this.context, receiver);
    }

    /**
     * Maps a polyglot value to a value with a given Java target type.
     *
     * <h3>Target type mapping</h3>
     * <p>
     * The following target types are supported and interpreted in the following order:
     * <ul>
     * <li>Custom
     * {@link HostAccess.Builder#targetTypeMapping(Class, Class, java.util.function.Predicate, Function)
     * target type mappings} specified in the {@link HostAccess} configuration with precedence
     * {@link TargetMappingPrecedence#HIGHEST} or {@link TargetMappingPrecedence#HIGH}. These custom
     * target type mappings may override all the type mappings below. This allows for customization
     * if one of the below type mappings is not suitable.
     * <li><code>{@link Value}.class</code> is always supported and returns this instance.
     * <li>If the value represents a {@link #isHostObject() host object} then all classes
     * implemented or extended by the host object can be used as target type.
     * <li><code>{@link String}.class</code> is supported if the value is a {@link #isString()
     * string}.
     * <li><code>{@link Character}.class</code> is supported if the value is a {@link #isString()
     * string} of length one or if a number can be safely be converted to a character.
     * <li><code>{@link Number}.class</code> is supported if the value is a {@link #isNumber()
     * number}. {@link Byte}, {@link Short}, {@link Integer}, {@link Long}, {@link Float} and
     * {@link Double} are allowed if they fit without conversion. If a conversion is necessary then
     * a {@link ClassCastException} is thrown. Primitive class literals throw a
     * {@link NullPointerException} if the value represents {@link #isNull() null}.
     * <li><code>{@link Boolean}.class</code> is supported if the value is a {@link #isBoolean()
     * boolean}. Primitive {@link Boolean boolean.class} literal is also supported. The primitive
     * class literal throws a {@link NullPointerException} if the value represents {@link #isNull()
     * null}.
     * <li><code>{@link LocalDate}.class</code> is supported if the value is a {@link #isDate()
     * date}</li>
     * <li><code>{@link LocalTime}.class</code> is supported if the value is a {@link #isTime()
     * time}</li>
     * <li><code>{@link LocalDateTime}.class</code> is supported if the value is a {@link #isDate()
     * date} and {@link #isTime() time}.</li>
     * <li><code>{@link Instant}.class</code> is supported if the value is an {@link #isInstant()
     * instant}.</li>
     * <li><code>{@link ZonedDateTime}.class</code> is supported if the value is a {@link #isDate()
     * date}, {@link #isTime() time} and {@link #isTimeZone() timezone}.</li>
     * <li><code>{@link ZoneId}.class</code> is supported if the value is a {@link #isTimeZone()
     * timezone}.</li>
     * <li><code>{@link Duration}.class</code> is supported if the value is a {@link #isDuration()
     * duration}.</li>
     * <li><code>{@link PolyglotException}.class</code> is supported if the value is an
     * {@link #isException() exception object}.</li>
     * <li>Any Java type in the type hierarchy of a {@link #isHostObject() host object}.
     * <li>Custom
     * {@link HostAccess.Builder#targetTypeMapping(Class, Class, java.util.function.Predicate, Function)
     * target type mappings} specified in the {@link HostAccess} configuration with precedence
     * {@link TargetMappingPrecedence#LOW}.
     * <li><code>{@link Object}.class</code> is always supported. See section Object mapping rules.
     * <li><code>{@link Map}.class</code> is supported if
     * {@link HostAccess.MutableTargetMapping#MEMBERS_TO_JAVA_MAP} respectively
     * {@link HostAccess.MutableTargetMapping#HASH_TO_JAVA_MAP} are
     * {@link HostAccess.Builder#allowMutableTargetMappings(HostAccess.MutableTargetMapping...)
     * allowed} and the value has {@link #hasHashEntries()} hash entries}, {@link #hasMembers()
     * members} or {@link #hasArrayElements() array elements}. The returned map can be safely cast
     * to Map<Object, Object>. For value with {@link #hasMembers() members} the key type is
     * {@link String}. For value with {@link #hasArrayElements() array elements} the key type is
     * {@link Long}. It is recommended to use {@link #as(TypeLiteral) type literals} to specify the
     * expected collection component types. With type literals the value type can be restricted, for
     * example to <code>Map<String, String></code>. If the raw <code>{@link Map}.class</code> or an
     * Object component type is used, then the return types of the the list are subject to Object
     * target type mapping rules recursively.
     * <li><code>{@link List}.class</code> is supported if
     * {@link HostAccess.MutableTargetMapping#ARRAY_TO_JAVA_LIST} is
     * {@link HostAccess.Builder#allowMutableTargetMappings(HostAccess.MutableTargetMapping...)
     * allowed} and the value has {@link #hasArrayElements() array elements} and it has an
     * {@link Value#getArraySize() array size} that is smaller or equal than
     * {@link Integer#MAX_VALUE}. The returned list can be safely cast to
     * <code>List&lt;Object&gt;</code>. It is recommended to use {@link #as(TypeLiteral) type
     * literals} to specify the expected component type. With type literals the value type can be
     * restricted to any supported target type, for example to <code>List&lt;Integer&gt;</code>. If
     * the raw <code>{@link List}.class</code> or an Object component type is used, then the return
     * types of the the list are recursively subject to Object target type mapping rules.
     * <li>Any Java array type of a supported target type. The values of the value will be eagerly
     * coerced and copied into a new instance of the provided array type. This means that changes in
     * returned array will not be reflected in the original value. Since conversion to a Java array
     * might be an expensive operation it is recommended to use the `List` or `Collection` target
     * type if possible.
     * <li><code>{@link Iterable}.class</code> is supported if
     * {@link HostAccess.MutableTargetMapping#ITERATOR_TO_JAVA_ITERATOR} is
     * {@link HostAccess.Builder#allowMutableTargetMappings(HostAccess.MutableTargetMapping...)
     * allowed} and the value has an {@link #hasIterator() iterator}. The returned iterable can be
     * safely cast to <code>Iterable&lt;Object&gt;</code>. It is recommended to use
     * {@link #as(TypeLiteral) type literals} to specify the expected component type. With type
     * literals the value type can be restricted to any supported target type, for example to
     * <code>Iterable&lt;Integer&gt;</code>.
     * <li><code>{@link Iterator}.class</code> is supported if
     * {@link HostAccess.MutableTargetMapping#ITERATOR_TO_JAVA_ITERATOR} is
     * {@link HostAccess.Builder#allowMutableTargetMappings(HostAccess.MutableTargetMapping...)
     * allowed} and the value is an {@link #isIterator() iterator} The returned iterator can be
     * safely cast to <code>Iterator&lt;Object&gt;</code>. It is recommended to use
     * {@link #as(TypeLiteral) type literals} to specify the expected component type. With type
     * literals the value type can be restricted to any supported target type, for example to
     * <code>Iterator&lt;Integer&gt;</code>. If the raw <code>{@link Iterator}.class</code> or an
     * Object component type is used, then the return types of the the iterator are recursively
     * subject to Object target type mapping rules. The returned iterator's {@link Iterator#next()
     * next} method may throw a {@link ConcurrentModificationException} when an underlying iterable
     * has changed or {@link UnsupportedOperationException} when the iterator's current element is
     * not readable.
     * <li>Any {@link FunctionalInterface functional} interface if
     * {@link HostAccess.MutableTargetMapping#EXECUTABLE_TO_JAVA_INTERFACE} is
     * {@link HostAccess.Builder#allowMutableTargetMappings(HostAccess.MutableTargetMapping...)
     * allowed} and the value can be {@link #canExecute() executed} or {@link #canInstantiate()
     * instantiated} and the interface type is {@link HostAccess implementable}. Note that
     * {@link FunctionalInterface} are implementable by default in with the
     * {@link HostAccess#EXPLICIT explicit} host access policy. In case a value can be executed and
     * instantiated then the returned implementation of the interface will be
     * {@link #execute(Object...) executed}. The coercion to the parameter types of functional
     * interface method is converted using the semantics of {@link #as(Class)}. If a standard
     * functional interface like {@link Function} is used, it is recommended to use
     * {@link #as(TypeLiteral) type literals} to specify the expected generic method parameter and
     * return type.
     * <li>Any interface if the value {@link #hasMembers() has members} and the interface type is
     * {@link HostAccess.Implementable implementable} and
     * {@link HostAccess.MutableTargetMapping#MEMBERS_TO_JAVA_INTERFACE} is
     * {@link HostAccess.Builder#allowMutableTargetMappings(HostAccess.MutableTargetMapping...)
     * allowed}. Each interface method maps to one {@link #getMember(String) member} of the value.
     * Whenever a method of the interface is executed a member with the method or field name must
     * exist otherwise an {@link UnsupportedOperationException} is thrown when the method is
     * executed. If one of the parameters or the return value cannot be mapped to the target type a
     * {@link ClassCastException} or a {@link NullPointerException} is thrown.
     * <li>JVM only: Any abstract class with an accessible default constructor if the value
     * {@link #hasMembers() has members} and the class is {@link HostAccess.Implementable
     * implementable}. Each interface method maps to one {@link #getMember(String) member} of the
     * value. Whenever an abstract method of the class is executed a member with the method or field
     * name must exist otherwise an {@link UnsupportedOperationException} is thrown when the method
     * is executed. If one of the parameters or the return value cannot be mapped to the target type
     * a {@link ClassCastException} or a {@link NullPointerException} is thrown.
     * <li>Custom
     * {@link HostAccess.Builder#targetTypeMapping(Class, Class, java.util.function.Predicate, Function)
     * target type mappings} specified in the {@link HostAccess} configuration with precedence
     * {@link TargetMappingPrecedence#LOWEST}.
     * </ul>
     * A {@link ClassCastException} is thrown for other unsupported target types.
     * <p>
     * <b>JavaScript Usage Examples:</b>
     *
     * <pre>
     * Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
     * assert context.eval("js", "undefined").as(Object.class) == null;
     * assert context.eval("js", "'foobar'").as(String.class).equals("foobar");
     * assert context.eval("js", "42").as(Integer.class) == 42;
     * assert context.eval("js", "({foo:'bar'})").as(Map.class).get("foo").equals("bar");
     * assert context.eval("js", "[42]").as(List.class).get(0).equals(42);
     * assert ((Map&lt;String, Object>) context.eval("js", "[{foo:'bar'}]").as(List.class).get(0)).get("foo").equals("bar");
     *
     * &#64;FunctionalInterface
     * interface IntFunction {
     *     int foo(int value);
     * }
     * assert context.eval("js", "(function(a){return a})").as(IntFunction.class).foo(42).asInt() == 42;
     *
     * &#64;FunctionalInterface
     * interface StringListFunction {
     *     int foo(List&lt;String&gt; value);
     * }
     * assert context.eval("js", "(function(a){return a.length})").as(StringListFunction.class).foo(new String[]{"42"}).asInt() == 1;
     *
     * public abstract class AbstractClass {
     *     public AbstractClass() {
     *     }
     *
     *     int foo(int value);
     * }
     * assert context.eval("js", "({foo: function(a){return a}})").as(AbstractClass.class).foo(42).asInt() == 42;
     * </pre>
     *
     * <h3>Object target type mapping</h3>
     * <p>
     * Object target mapping is useful to map polyglot values to its closest corresponding standard
     * JDK type.
     *
     * The following rules apply when <code>Object</code> is used as a target type:
     * <ol>
     * <li>If the value represents {@link #isNull() null} then <code>null</code> is returned.
     * <li>If the value is a {@link #isHostObject() host object} then the value is coerced to
     * {@link #asHostObject() host object value}.
     * <li>If the value is a {@link #isString() string} then the value is coerced to {@link String}
     * or {@link Character}.
     * <li>If the value is a {@link #isBoolean() boolean} then the value is coerced to
     * {@link Boolean}.
     * <li>If the value is a {@link #isNumber() number} then the value is coerced to {@link Number}.
     * The specific sub type of the {@link Number} is not specified. Users need to be prepared for
     * any Number subclass including {@link BigInteger} or {@link BigDecimal}. It is recommended to
     * cast to {@link Number} and then convert to a Java primitive like with
     * {@link Number#longValue()}.
     * <li>If the value has {@link #hasArrayElements() array elements} and it has an
     * {@link Value#getArraySize() array size} that is smaller or equal than
     * {@link Integer#MAX_VALUE} then the result value will implement {@link List}. Every array
     * element of the value maps to one list element. The size of the returned list maps to the
     * array size of the value. The returned value may also implement {@link Function} if the value
     * can be {@link #canExecute() executed} or {@link #canInstantiate() instantiated}.
     * <li>If the value has {@link #hasHashEntries() hash entries} then the result value will
     * implement {@link Map}. The {@link Map#size() size} of the returned {@link Map} is equal to
     * the {@link #getHashSize() hash entries count}. The returned value may also implement
     * {@link Function} if the value can be {@link #canExecute() executed} or
     * {@link #canInstantiate() instantiated}.
     * <li>If the value {@link #hasMembers() has members} then the result value will implement
     * {@link Map}. If this value {@link #hasMembers() has members} then all members are accessible
     * using {@link String} keys. The {@link Map#size() size} of the returned {@link Map} is equal
     * to the count of all members. The returned value may also implement {@link Function} if the
     * value can be {@link #canExecute() executed} or {@link #canInstantiate() instantiated}.
     * <li>If the value has an {@link #hasIterator()} iterator} then the result value will implement
     * {@link Iterable}. The returned value may also implement {@link Function} if the value can be
     * {@link #canExecute() executed} or {@link #canInstantiate() instantiated}.
     * <li>If the value is an {@link #isIterator()} iterator} then the result value will implement
     * {@link Iterator}. The returned value may also implement {@link Function} if the value can be
     * {@link #canExecute() executed} or {@link #canInstantiate() instantiated}.
     * <li>If the value can be {@link #canExecute() executed} or {@link #canInstantiate()
     * instantiated} then the result value implements {@link Function Function}. By default the
     * argument of the function will be used as single argument to the function when executed. If a
     * value of type {@link Object Object[]} is provided then the function will be executed with
     * those arguments. The returned function may also implement {@link List} or {@link Map} if the
     * value has {@link #hasArrayElements() array elements} or {@link #hasMembers() members},
     * respectively.
     * <li>Mappings to mutable target types such as {@link List}, {@link Map}, {@link Iterator} and
     * {@link Iterable} are only available if the corresponding mappings are enabled (see
     * {@link org.graalvm.polyglot.HostAccess.Builder#allowMutableTargetMappings(org.graalvm.polyglot.HostAccess.MutableTargetMapping...)}).
     * <li>If none of the above rules apply then this {@link Value} instance is returned.
     * </ol>
     * Returned {@link #isHostObject() host objects}, {@link String}, {@link Number},
     * {@link Boolean} and <code>null</code> values have unlimited lifetime. Other values will throw
     * an {@link IllegalStateException} for any operation if their originating {@link Context
     * context} was closed.
     * <p>
     * If a {@link Map} element is modified, a {@link List} element is modified or a
     * {@link Function} argument is provided then these values are interpreted according to the
     * {@link Context#asValue(Object) host to polyglot value mapping rules}.
     * <p>
     * <b>JavaScript Usage Examples:</b>
     *
     * <pre>
     * Context context = Context.create();
     * assert context.eval("js", "undefined").as(Object.class) == null;
     * assert context.eval("js", "'foobar'").as(Object.class) instanceof String;
     * assert context.eval("js", "42").as(Object.class) instanceof Number;
     * assert context.eval("js", "[]").as(Object.class) instanceof Map;
     * assert context.eval("js", "{}").as(Object.class) instanceof Map;
     * assert ((Map&lt;Object, Object>) context.eval("js", "[{}]").as(Object.class)).get(0) instanceof Map;
     * assert context.eval("js", "(function(){})").as(Object.class) instanceof Function;
     * </pre>
     *
     * <h3>Object Identity</h3>
     * <p>
     * If polyglot values are mapped as Java primitives such as {@link Boolean}, <code>null</code>,
     * {@link String}, {@link Character} or {@link Number}, then the identity of the polyglot value
     * is not preserved. All other results can be converted back to a {@link Value polyglot value}
     * using {@link Context#asValue(Object)}.
     *
     * <b>Mapping Example using JavaScript:</b> This example first creates a new JavaScript object
     * and maps it to a {@link Map}. Using the {@link Context#asValue(Object)} it is possible to
     * recreate the {@link Value polyglot value} from the Java map. The JavaScript object identity
     * is preserved in the process.
     *
     * <pre>
     * Context context = Context.create();
     * Map&lt;Object, Object> javaMap = context.eval("js", "{}").as(Map.class);
     * Value polyglotValue = context.asValue(javaMap);
     * </pre>
     *
     * @see #as(TypeLiteral) to map to generic type signatures.
     * @param targetType the target Java type to map
     * @throws ClassCastException if polyglot value could not be mapped to the target type.
     * @throws PolyglotException if the conversion triggered a guest language error.
     * @throws IllegalStateException if the underlying context is already closed.
     * @throws NullPointerException if the target type is null.
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    public <T> T as(Class<T> targetType) throws ClassCastException, IllegalStateException, PolyglotException {
        Objects.requireNonNull(targetType, "targetType");
        if (targetType == Value.class) {
            return (T) this;
        }
        return dispatch.asClass(this.context, receiver, targetType);
    }

    /**
     * Maps a polyglot value to a given Java target type literal. For usage instructions see
     * {@link TypeLiteral}.
     * <p>
     * Usage example:
     *
     * <pre>
     * static final TypeLiteral&lt;List&lt;String>> STRING_LIST = new TypeLiteral&lt;List&lt;String>>() {
     * };
     *
     * public static void main(String[] args) {
     *     Context context = Context.create();
     *     List&lt;String> javaList = context.eval("js", "['foo', 'bar', 'bazz']").as(STRING_LIST);
     *     assert javaList.get(0).equals("foo");
     * }
     * </pre>
     *
     * @throws NullPointerException if the target type is null.
     * @see #as(Class)
     * @since 19.0
     */
    public <T> T as(TypeLiteral<T> targetType) {
        Objects.requireNonNull(targetType, "targetType");
        return dispatch.asTypeLiteral(this.context, receiver, targetType.getRawType(), targetType.getType());
    }

    /**
     * Converts this value to a human readable string. Each language may have special formating
     * conventions - even primitive values may not follow the traditional Java formating rules. The
     * format of the returned string is intended to be interpreted by humans not machines and should
     * therefore not be relied upon by machines. By default this value class name and its
     * {@link System#identityHashCode(Object) identity hash code} is used as string representation.
     *
     * @since 19.0
     */
    @Override
    public String toString() {
        return super.toString();
    }

    /**
     * Returns the declared source location of the value.
     *
     * @return the {@link SourceSection} or null if unknown
     * @since 19.0
     */
    public SourceSection getSourceLocation() {
        return (SourceSection) dispatch.getSourceLocation(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this object represents a date, else <code>false</code>. If this
     * value is also a {@link #isTimeZone() timezone} then the date is aware, otherwise it is naive.
     *
     * @throws ClassCastException if polyglot value could not be mapped to the target type.
     * @throws NullPointerException if the target type is null.
     * @throws PolyglotException if the conversion triggered a guest language error.
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #asDate()
     * @since 19.2.0
     */
    public boolean isDate() {
        return dispatch.isDate(this.context, receiver);
    }

    /**
     * Returns this value as date if this object represents a {@link #isDate() date}. The returned
     * date is either aware if the value has a {@link #isTimeZone() timezone} otherwise it is naive.
     *
     * @throws ClassCastException if polyglot value could not be mapped to the target type.
     * @throws NullPointerException if the target type is null.
     * @throws PolyglotException if the conversion triggered a guest language error.
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #isDate()
     * @since 19.2.0
     */
    public LocalDate asDate() {
        return dispatch.asDate(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this object represents a time, else <code>false</code>. If the
     * value is also a {@link #isTimeZone() timezone} then the time is aware, otherwise it is naive.
     *
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #asTime()
     * @since 19.2.0
     */
    public boolean isTime() {
        return dispatch.isTime(this.context, receiver);
    }

    /**
     * Returns this value as time if this object represents a {@link #isTime() time}. The returned
     * time is either aware if the value has a {@link #isTimeZone() timezone} otherwise it is naive.
     *
     * @throws ClassCastException if polyglot value could not be mapped to the target type.
     * @throws NullPointerException if the target type is null.
     * @throws PolyglotException if the conversion triggered a guest language error.
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #isTime()
     * @since 19.2.0
     */
    public LocalTime asTime() {
        return dispatch.asTime(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this value represents an instant. If a value is an instant then
     * it is also a {@link #isDate() date}, {@link #isTime() time} and {@link #isTimeZone()
     * timezone}.
     *
     * This method is short-hand for:
     *
     * <pre>
     * v.{@linkplain #isDate() isDate}() && v.{@link #isTime() isTime}() && v.{@link #isTimeZone() isTimeZone}()
     * </pre>
     *
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #isDate()
     * @see #isTime()
     * @see #isInstant()
     * @see #asInstant()
     * @since 19.2.0
     */
    public boolean isInstant() {
        return isDate() && isTime() && isTimeZone();
    }

    /**
     * Returns this value as instant if this object represents an {@link #isInstant() instant}. If a
     * value is an instant then it is also a {@link #isDate() date}, {@link #isTime() time} and
     * {@link #isTimeZone() timezone}. Using this method may be more efficient than reconstructing
     * the timestamp from the date, time and timezone data.
     * <p>
     * The following assertion always holds if {@link #isInstant()} returns <code>true</code>:
     *
     * <pre>
     * ZoneId zone = getTimeZone(receiver);
     * LocalDate date = getDate(receiver);
     * LocalTime time = getTime(receiver);
     * assert ZonedDateTime.of(date, time, zone).toInstant().equals(getInstant(receiver));
     * </pre>
     *
     * @throws ClassCastException if polyglot value could not be mapped to the target type.
     * @throws NullPointerException if the target type is null.
     * @throws PolyglotException if the conversion triggered a guest language error.
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #isDate()
     * @see #isTime()
     * @see #isTimeZone()
     * @since 19.2.0
     */
    public Instant asInstant() {
        return dispatch.asInstant(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this object represents a timezone, else <code>false</code>. The
     * interpretation of timezone objects may vary:
     * <ul>
     * <li>If {@link #isDate()} and {@link #isTime()} return <code>true</code>, then the returned
     * date or time information is aware of this timezone.
     * <li>If {@link #isDate()} and {@link #isTime()} returns <code>false</code>, then it represents
     * just timezone information.
     * </ul>
     * Objects with only date information must not have timezone information attached and objects
     * with only time information must have either none, or {@link ZoneRules#isFixedOffset() fixed
     * zone} only. If this rule is violated then an {@link AssertionError} is thrown if assertions
     * are enabled.
     * <p>
     * If this method is implemented then also {@link #asTimeZone()} must be implemented.
     *
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #asTimeZone()
     * @see #asInstant()
     * @since 19.2.0
     */
    public boolean isTimeZone() {
        return dispatch.isTimeZone(this.context, receiver);
    }

    /**
     * Returns this value as timestamp if this object represents a {@link #isTimeZone() timezone}.
     *
     * @throws ClassCastException if polyglot value could not be mapped to the target type.
     * @throws PolyglotException if the conversion triggered a guest language error.
     * @throws IllegalStateException if the underlying context is already closed.
     * @throws NullPointerException if the target type is null.
     * @see #isTimeZone()
     * @since 19.2.0
     */
    public ZoneId asTimeZone() {
        return dispatch.asTimeZone(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this object represents a duration, else <code>false</code>.
     *
     * @throws IllegalStateException if the underlying context is already closed.
     * @see Duration
     * @see #asDuration()
     * @since 19.2.0
     */
    public boolean isDuration() {
        return dispatch.isDuration(this.context, receiver);
    }

    /**
     * Returns this value as duration if this object represents a {@link #isDuration() duration}.
     *
     * @throws ClassCastException if polyglot value could not be mapped to the target type.
     * @throws PolyglotException if the conversion triggered a guest language error.
     * @throws IllegalStateException if the underlying context is already closed.
     * @throws NullPointerException if the target type is null.
     * @see #isDuration()
     * @since 19.2.0
     */
    public Duration asDuration() {
        return dispatch.asDuration(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this object represents an exception, else <code>false</code>.
     *
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #throwException()
     * @since 19.3
     */
    public boolean isException() {
        return dispatch.isException(this.context, receiver);
    }

    /**
     * Throws this value if this object represents an {@link #isException() exception}.
     *
     * @throws UnsupportedOperationException if the value is not an exception.
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #isException()
     * @since 19.3
     */
    public RuntimeException throwException() {
        return dispatch.throwException(this.context, receiver);
    }

    /**
     * Returns the context this value was created with. The returned context may be
     * <code>null</code> if the value was created using {@link Value#asValue(Object)} and no current
     * context was {@link Context#enter() entered} at the time.
     * <p>
     * The returned context can <b>not</b> be used to {@link Context#enter() enter} ,
     * {@link Context#leave() leave} or {@link Context#close() close} the context or
     * {@link Context#getEngine() engine}. Invoking such methods will cause an
     * {@link IllegalStateException} to be thrown. This ensures that only the
     * {@link Context#create(String...) creator} of a context is allowed to enter, leave or close a
     * context and that a context is not closed while it is still active.
     *
     * @since 19.3.0
     */
    public Context getContext() {
        Context c = (Context) dispatch.getContext(this.context);
        if (c != null && c.currentAPI != null) {
            return c.currentAPI;
        } else {
            return c;
        }
    }

    /**
     * Compares the identity of the underlying polyglot objects. This method does not do any
     * structural comparisons.
     *
     * {@inheritDoc}
     *
     * @since 20.1
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Returns the identity hash code of the underlying object. This method does not compute the
     * hash code depending on the contents of the value.
     *
     * {@inheritDoc}
     *
     * @since 20.1
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Returns <code>true</code> if this polyglot value provides an iterator. In this case the
     * iterator can be obtained using {@link #getIterator()}.
     *
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     *
     * @see #getIterator()
     * @since 21.1
     */
    public boolean hasIterator() {
        return dispatch.hasIterator(this.context, receiver);
    }

    /**
     * Creates a new iterator that allows read each element of a sequence.
     *
     * @throws UnsupportedOperationException if the value does not provide {@link #hasIterator()
     *             iterator}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     *
     * @see #hasIterator()
     * @since 21.1
     */
    public Value getIterator() {
        return (Value) dispatch.getIterator(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if the value represents an iterator object. In this case the
     * iterator elements can be accessed using {@link #getIteratorNextElement()}.
     *
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     *
     * @see #hasIteratorNextElement()
     * @see #getIteratorNextElement()
     * @since 21.1
     */
    public boolean isIterator() {
        return dispatch.isIterator(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if the value represents an iterator which has more elements, else
     * {@code false}. Multiple calls to the {@link #hasIteratorNextElement()} might lead to
     * different results if the underlying data structure is modified.
     *
     * @throws UnsupportedOperationException if the value is not an {@link #isIterator() iterator}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     *
     * @see #isIterator()
     * @see #getIteratorNextElement()
     * @since 21.1
     */
    public boolean hasIteratorNextElement() {
        return dispatch.hasIteratorNextElement(this.context, receiver);
    }

    /**
     * Returns the next element in the iteration. When the underlying data structure is modified the
     * {@link #getIteratorNextElement()} may throw the {@link NoSuchElementException} despite the
     * {@link #hasIteratorNextElement()} returned {@code true}, or it may throw a language error.
     *
     * @throws UnsupportedOperationException if the value is not an {@link #isIterator() iterator}
     *             or when the underlying iterable element exists but is not readable.
     * @throws NoSuchElementException if the iteration has no more elements. Even if the
     *             {@link NoSuchElementException} was thrown it might not be thrown again by a next
     *             call of the {@link #getIteratorNextElement()} due to a modification of an
     *             underlying iterable.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     *
     * @see #isIterator()
     * @see #hasIteratorNextElement()
     * @since 21.1
     */
    public Value getIteratorNextElement() {
        return (Value) dispatch.getIteratorNextElement(this.context, receiver);
    }

    /**
     * Returns <code>true</code> if this polyglot value represents a map. In this case map entries
     * can be accessed using {@link #getHashValue(Object)},
     * {@link #getHashValueOrDefault(Object, Object)}, {@link #putHashEntry(Object, Object)},
     * {@link #removeHashEntry(Object)}, {@link #getHashEntriesIterator()} and the map size can be
     * queried using {@link #getHashSize()}.
     *
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public boolean hasHashEntries() {
        return dispatch.hasHashEntries(this.context, receiver);
    }

    /**
     * Returns the number of map entries for values with hash entries.
     *
     * @throws UnsupportedOperationException if the value does not have any
     *             {@link #hasHashEntries()} hash entries}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public long getHashSize() throws UnsupportedOperationException {
        return dispatch.getHashSize(this.context, receiver);
    }

    /**
     * Returns {@code true} if mapping for the specified key exists. If the value has no
     * {@link #hasHashEntries() hash entries} then {@link #hasHashEntry(Object)} returns
     * {@code false}. The key is subject to polyglot value mapping rules as described in
     * {@link Context#asValue(Object)}.
     *
     * @throws IllegalStateException if the context is already {@link Context#close() closed}.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public boolean hasHashEntry(Object key) {
        return dispatch.hasHashEntry(this.context, receiver, key);
    }

    /**
     * Returns the value for the specified key or {@code null} if the mapping for the specified key
     * does not exist. The key is subject to polyglot value mapping rules as described in
     * {@link Context#asValue(Object)}.
     *
     * @throws UnsupportedOperationException if the value has no {@link #hasHashEntries() hash
     *             entries} or the mapping for given key exists but is not readable.
     * @throws IllegalStateException if the context is already {@link Context#close() closed}.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public Value getHashValue(Object key) throws UnsupportedOperationException {
        return (Value) dispatch.getHashValue(this.context, receiver, key);
    }

    /**
     * Returns the value for the specified key or the default value if the mapping for the specified
     * key does not exist or is not readable. The key and the default value are subject to polyglot
     * value mapping rules as described in {@link Context#asValue(Object)}.
     *
     * @throws UnsupportedOperationException if the value has no {@link #hasHashEntries() hash
     *             entries} at all.
     * @throws IllegalStateException if the context is already {@link Context#close() closed}.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public Value getHashValueOrDefault(Object key, Object defaultValue) throws UnsupportedOperationException {
        return (Value) dispatch.getHashValueOrDefault(this.context, receiver, key, defaultValue);
    }

    /**
     * Associates the specified value with the specified key. Both key and value are subject to
     * polyglot value mapping rules as described in {@link Context#asValue(Object)}.
     *
     * @throws UnsupportedOperationException if the value does not have any {@link #hasHashEntries()
     *             hash entries}, the mapping for specified key does not exist and new members
     *             cannot be added, or the existing mapping for specified key is not modifiable.
     * @throws IllegalArgumentException if the provided key type or value type is not allowed to be
     *             written.
     * @throws IllegalStateException if the context is already {@link Context#close() closed}.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public void putHashEntry(Object key, Object value) throws IllegalArgumentException, UnsupportedOperationException {
        dispatch.putHashEntry(this.context, receiver, key, value);
    }

    /**
     * Removes the mapping for a given key. Returns {@code true} if the mapping was successfully
     * removed, {@code false} if mapping for a given key does not exist. The key is subject to
     * polyglot value mapping rules as described in {@link Context#asValue(Object)}.
     *
     * @throws UnsupportedOperationException if the value does not have any {@link #hasHashEntries()
     *             hash entries} or if mapping for specified key {@link #hasHashEntry(Object)
     *             exists} but cannot be removed.
     * @throws IllegalStateException if the context is already {@link Context#close() closed}.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 21.1
     */
    public boolean removeHashEntry(Object key) throws UnsupportedOperationException {
        return dispatch.removeHashEntry(this.context, receiver, key);
    }

    /**
     * Creates a new hash entries iterator that allows read each map entry. The return value is
     * always an {@link #isIterator() iterator} of {@link #hasArrayElements() array elements}. The
     * first array element is a key, the second array element is an associated value. Even if the
     * value array element is {@link #setArrayElement(long, Object) modifiable} writing to array may
     * not update the mapping, always use {@link #putHashEntry(Object, Object)} to update the
     * mapping.
     *
     * @throws UnsupportedOperationException if the value does not have any {@link #hasHashEntries()
     *             hash entries}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     *
     * @since 21.1
     */
    public Value getHashEntriesIterator() throws UnsupportedOperationException {
        return (Value) dispatch.getHashEntriesIterator(this.context, receiver);
    }

    /**
     * Creates a new hash keys iterator that allows read each map key. The return value is always an
     * {@link #isIterator() iterator}.
     *
     * @throws UnsupportedOperationException if the value does not have any {@link #hasHashEntries()
     *             hash entries}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     *
     * @since 21.1
     */
    public Value getHashKeysIterator() throws UnsupportedOperationException {
        return (Value) dispatch.getHashKeysIterator(this.context, receiver);
    }

    /**
     * Creates a new hash values iterator that allows read each map value. The return value is
     * always an {@link #isIterator() iterator}.
     *
     * @throws UnsupportedOperationException if the value does not have any {@link #hasHashEntries()
     *             hash entries}.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     *
     * @since 21.1
     */
    public Value getHashValuesIterator() throws UnsupportedOperationException {
        return (Value) dispatch.getHashValuesIterator(this.context, receiver);
    }

    /**
     * Converts a Java host value to a polyglot value. Returns a value for any host or guest value.
     * If there is a context available use {@link Context#asValue(Object)} for efficiency instead.
     * The value is bound the {@link Context#getCurrent() current} context when created. If there is
     * no context available when the value was constructed then Values constructed with this method
     * may return <code>null</code> for {@link #getContext()}.
     *
     * @param o the object to convert
     * @throws IllegalStateException if no context is currently entered.
     * @see Context#asValue(Object) Conversion rules.
     * @since 19.0
     */
    public static Value asValue(Object o) {
        if (o instanceof Value) {
            return (Value) o;
        }
        return (Value) Engine.getImpl().asValue(o);
    }

    /**
     * Pins a scoped value such that it can be used beyond the scope of a scoped host method call.
     * Pinning is an idempotent operation, i.e. pinning an already pinned value just results in a
     * pinned value again.
     *
     * Trying to pin a value that is not scoped will not cause an effect. Trying to pin a scoped
     * value that has already been released will raise a {@link IllegalStateException}.
     *
     * @throws IllegalStateException if the method scope of the value was finished
     * @see HostAccess#SCOPED
     * @since 21.3
     */
    public void pin() {
        dispatch.pin(this.context, receiver);
    }
}

abstract class AbstractValue {

    final Object receiver;
    final Object context;
    final AbstractValueDispatch dispatch;

    AbstractValue(AbstractValueDispatch dispatch, Object context, Object receiver) {
        this.context = context;
        this.dispatch = dispatch;
        this.receiver = receiver;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AbstractValue)) {
            return false;
        }
        return dispatch.equalsImpl(this.context, receiver, ((AbstractValue) obj).receiver);
    }

    @Override
    public int hashCode() {
        return dispatch.hashCodeImpl(this.context, receiver);
    }

    @Override
    public String toString() {
        return dispatch.toString(this.context, receiver);
    }
}
