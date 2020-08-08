/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueImpl;
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
 * number value may be accessed as {@link #asByte() byte}, {@link #asShort() short} {@link #asInt()
 * int} {@link #asLong() long}, {@link #asFloat() float} or {@link #asDouble() double} value.
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
 * @see Context
 * @see Engine
 * @see PolyglotException
 * @since 19.0
 */
public final class Value {

    final Object receiver;
    final AbstractValueImpl impl;

    Value(AbstractValueImpl impl, Object value) {
        this.impl = impl;
        this.receiver = value;
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
        return impl.getMetaObject(receiver);
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
        return impl.isMetaObject(receiver);
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
        return impl.getMetaQualifiedName(receiver);
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
        return impl.getMetaSimpleName(receiver);
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
        return impl.isMetaInstance(receiver, instance);
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
        return impl.hasArrayElements(receiver);
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
        return impl.getArrayElement(receiver, index);
    }

    /**
     * Sets the value at a given index. Polyglot arrays start with index <code>0</code>, independent
     * of the guest language. The array element value is subject to polyglot value mapping rules as
     * described in {@link Context#asValue(Object)}.
     *
     * @throws ArrayIndexOutOfBoundsException if the array index does not exist.
     * @throws UnsupportedOperationException if the value does not have any
     *             {@link #hasArrayElements() array elements} or if the index exists but is not
     *             modifiable.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @since 19.0
     */
    public void setArrayElement(long index, Object value) {
        impl.setArrayElement(receiver, index, value);
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
        return impl.removeArrayElement(receiver, index);
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
        return impl.getArraySize(receiver);
    }

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
        return impl.hasMembers(receiver);
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
        return impl.hasMember(receiver, identifier);
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
        return impl.getMember(receiver, identifier);
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
        return impl.getMemberKeys(receiver);
    }

    /**
     * Sets the value of a member using an identifier. The member value is subject to polyglot value
     * mapping rules as described in {@link Context#asValue(Object)}.
     *
     * @throws IllegalStateException if the context is already {@link Context#close() closed}.
     * @throws UnsupportedOperationException if the value does not have any {@link #hasMembers()
     *             members}, the key does not exist and new members cannot be added, or the existing
     *             member is not modifiable.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws NullPointerException if the identifier is null.
     * @since 19.0
     */
    public void putMember(String identifier, Object value) {
        Objects.requireNonNull(identifier, "identifier");
        impl.putMember(receiver, identifier, value);
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
        return impl.removeMember(receiver, identifier);
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
        return impl.canExecute(receiver);
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
            return impl.execute(receiver);
        } else {
            return impl.execute(receiver, arguments);
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
            impl.executeVoid(receiver);
        } else {
            impl.executeVoid(receiver, arguments);
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
        return impl.canInstantiate(receiver);
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
        return impl.newInstance(receiver, arguments);
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
        return impl.canInvoke(identifier, receiver);
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
            return impl.invoke(receiver, identifier);
        } else {
            return impl.invoke(receiver, identifier, arguments);
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
        return impl.isString(receiver);
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
        return impl.asString(receiver);
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
        return impl.fitsInInt(receiver);
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
        return impl.asInt(receiver);
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
        return impl.isBoolean(receiver);
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
        return impl.asBoolean(receiver);
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
        return impl.isNumber(receiver);
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
        return impl.fitsInLong(receiver);
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
        return impl.asLong(receiver);
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
        return impl.fitsInDouble(receiver);
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
        return impl.asDouble(receiver);
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
        return impl.fitsInFloat(receiver);
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
        return impl.asFloat(receiver);
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
        return impl.fitsInByte(receiver);
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
        return impl.asByte(receiver);
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
        return impl.fitsInShort(receiver);
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
        return impl.asShort(receiver);
    }

    /**
     * Returns <code>true</code> if this value is a <code>null</code> like.
     *
     * @throws PolyglotException if a guest language error occurred during execution.
     * @throws IllegalStateException if the underlying context was closed.
     * @since 19.0
     */
    public boolean isNull() {
        return impl.isNull(receiver);
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
        return impl.isNativePointer(receiver);
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
        return impl.asNativePointer(receiver);
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
        return impl.isHostObject(receiver);
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
        return (T) impl.asHostObject(receiver);
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
        return impl.isProxyObject(receiver);
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
        return (T) impl.asProxyObject(receiver);
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
     * target type mappings} specified in the {@link HostAccess} configuration when the context is
     * constructed. Custom target type mappings may override all the type mappings below. This
     * allows for customization if one of the below type mappings is not suitable.
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
     * <li><code>{@link Object}.class</code> is always supported. See section Object mapping rules.
     * <li><code>{@link Map}.class</code> is supported if the value has {@link #hasMembers()
     * members} or {@link #hasArrayElements() array elements}. The returned map can be safely cast
     * to Map<Object, Object>. The key type in such a case is either {@link String} or {@link Long}.
     * It is recommended to use {@link #as(TypeLiteral) type literals} to specify the expected
     * collection component types. With type literals the value type can be restricted, for example
     * to <code>Map<String, String></code>. If the raw <code>{@link Map}.class</code> or an Object
     * component type is used, then the return types of the the list are subject to Object target
     * type mapping rules recursively.
     * <li><code>{@link List}.class</code> is supported if the value has {@link #hasArrayElements()
     * array elements} and it has an {@link Value#getArraySize() array size} that is smaller or
     * equal than {@link Integer#MAX_VALUE}. The returned list can be safely cast to
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
     * <li>Any {@link FunctionalInterface functional} interface if the value can be
     * {@link #canExecute() executed} or {@link #canInstantiate() instantiated} and the interface
     * type is {@link HostAccess implementable}. Note that {@link FunctionalInterface} are
     * implementable by default in with the {@link HostAccess#EXPLICIT explicit} host access policy.
     * In case a value can be executed and instantiated then the returned implementation of the
     * interface will be {@link #execute(Object...) executed}. The coercion to the parameter types
     * of functional interface method is converted using the semantics of {@link #as(Class)}. If a
     * standard functional interface like {@link Function} is used, it is recommended to use
     * {@link #as(TypeLiteral) type literals} to specify the expected generic method parameter and
     * return type.
     * <li>Any interface if the value {@link #hasMembers() has members} and the interface type is
     * {@link HostAccess.Implementable implementable}. Each interface method maps to one
     * {@link #getMember(String) member} of the value. Whenever a method of the interface is
     * executed a member with the method or field name must exist otherwise a
     * {@link UnsupportedOperationException} is thrown when the method is executed. A member If one
     * of the parameters cannot be mapped to the target type a {@link ClassCastException} or a
     * {@link NullPointerException} is thrown.
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
     * assert context.eval("js", "{foo:'bar'}").as(Map.class).get("foo").equals("bar");
     * assert context.eval("js", "[42]").as(List.class).get(0).equals(42);
     * assert ((Map&lt;String, Object>)context.eval("js", "[{foo:'bar'}]").as(List.class).get(0)).get("foo").equals("bar");
     *
     * &#64;FunctionalInterface interface IntFunction { int foo(int value); }
     * assert context.eval("js", "(function(a){a})").as(IntFunction.class).foo(42) == 42;
     *
     * &#64;FunctionalInterface interface StringListFunction { int foo(List&lt;String&gt; value); }
     * assert context.eval("js", "(function(a){a.length})").as(StringListFunction.class)
     *                                                     .foo(new String[]{"42"}) == 1;
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
     * <li>If the value {@link #hasMembers() has members} then the result value will implement
     * {@link Map}. If this value {@link #hasMembers() has members} then all members are accessible
     * using {@link String} keys. The {@link Map#size() size} of the returned {@link Map} is equal
     * to the count of all members. The returned value may also implement {@link Function} if the
     * value can be {@link #canExecute() executed} or {@link #canInstantiate() instantiated}.
     * <li>If the value has {@link #hasArrayElements() array elements} and it has an
     * {@link Value#getArraySize() array size} that is smaller or equal than
     * {@link Integer#MAX_VALUE} then the result value will implement {@link List}. Every array
     * element of the value maps to one list element. The size of the returned list maps to the
     * array size of the value. The returned value may also implement {@link Function} if the value
     * can be {@link #canExecute() executed} or {@link #canInstantiate() instantiated}.
     * <li>If the value can be {@link #canExecute() executed} or {@link #canInstantiate()
     * instantiated} then the result value implements {@link Function Function}. By default the
     * argument of the function will be used as single argument to the function when executed. If a
     * value of type {@link Object Object[]} is provided then the function will be executed with
     * those arguments. The returned function may also implement {@link Map} if the value has
     * {@link #hasArrayElements() array elements} or {@link #hasMembers() members}.
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
        return impl.as(receiver, targetType);
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
        return impl.as(receiver, targetType);
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
        return impl.toString(receiver);
    }

    /**
     * Returns the declared source location of the value.
     *
     * @return the {@link SourceSection} or null if unknown
     * @since 19.0
     */
    public SourceSection getSourceLocation() {
        return impl.getSourceLocation(receiver);
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
        return impl.isDate(receiver);
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
        return impl.asDate(receiver);
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
        return impl.isTime(receiver);
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
        return impl.asTime(receiver);
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
        return impl.asInstant(receiver);
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
        return impl.isTimeZone(receiver);
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
        return impl.asTimeZone(receiver);
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
        return impl.isDuration(receiver);
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
        return impl.asDuration(receiver);
    }

    /**
     * Returns <code>true</code> if this object represents an exception, else <code>false</code>.
     *
     * @throws IllegalStateException if the underlying context is already closed.
     * @see #throwException()
     * @since 19.3
     */
    public boolean isException() {
        return impl.isException(receiver);
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
        return impl.throwException(receiver);
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
        return impl.getContext();
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
        if (!(obj instanceof Value)) {
            return false;
        }
        return impl.equalsImpl(receiver, ((Value) obj).receiver);
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
        return impl.hashCodeImpl(receiver);
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
        return Engine.getImpl().asValue(o);
    }

}
