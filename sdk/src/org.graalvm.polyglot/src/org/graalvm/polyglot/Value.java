/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.polyglot;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueImpl;
import org.graalvm.polyglot.proxy.Proxy;

/**
 * Represents a polyglot value that can be accessed using a set of language agnostic operations.
 * Polyglot values can either result from a {@link #isHostObject() host} or guest language. Polyglot
 * values are bound to a {@link Context context}. If the context is closed then the value operation
 * throw an {@link IllegalStateException}.
 *
 * @since 1.0
 */
public final class Value {

    final Object receiver;
    final AbstractValueImpl impl;

    Value(AbstractValueImpl impl, Object value) {
        this.impl = impl;
        this.receiver = value;
    }

    /**
     * Returns the meta representation of this polyglot value. The interpretation of this function
     * depends on the guest language. For example, in JavaScript the expression
     * <code>context.eval("js", "42")</code> will return the "number" string as meta object.
     *
     * @since 1.0
     */
    public Value getMetaObject() {
        return impl.getMetaObject(receiver);
    }

    /**
     * Returns <code>true</code> if this polyglot value has array elements. In this case array elements
     * can be accessed using {@link #getArrayElement(long)}, {@link #setArrayElement(long, Object)} and
     * the array size can be queried using {@link #getArraySize()}.
     *
     * @since 1.0
     */
    public boolean hasArrayElements() {
        return impl.hasArrayElements(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public Value getArrayElement(long index) {
        return impl.getArrayElement(receiver, index);
    }

    /**
     *
     *
     * @since 1.0
     */
    public void setArrayElement(long index, Object value) {
        impl.setArrayElement(receiver, index, value);
    }

    /**
     *
     *
     * @since 1.0
     */
    public long getArraySize() {
        return impl.getArraySize(receiver);
    }

    // dynamic object

    /**
     *
     *
     * @since 1.0
     */
    public boolean hasMembers() {
        return impl.hasMembers(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public Value getMember(String key) {
        return impl.getMember(receiver, key);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean hasMember(String key) {
        return impl.hasMember(receiver, key);
    }

    /**
     *
     *
     * @since 1.0
     */
    public Set<String> getMemberKeys() {
        return impl.getMemberKeys(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public void putMember(String key, Object member) {
        impl.putMember(receiver, key, member);
    }

    // executable

    /**
     * Returns <code>true</code> if the value can be {@link #execute(Object...) executed}.
     *
     * @throws IllegalStateException if the underlying context was closed
     * @see #execute(Object...)
     * @since 1.0
     */
    public boolean canExecute() {
        return impl.canExecute(receiver);
    }

    /**
     * Executes this value if it {@link #canExecute() can} be executed and returns its result. If no
     * result value is expected or needed use {@link #executeVoid(Object...)} for better performance.
     *
     * @throws IllegalStateException if the underlying context was closed
     * @throws UnsupportedOperationException if this object cannot be executed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @see #executeVoid(Object...)
     *
     *      All arguments are subject to polylgot value mapping rules as described in
     *      {@link Context#asValue(Object)}.
     *
     * @since 1.0
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
     * Returns <code>true</code> if the value can be instantiated. This indicates that the
     * {@link #newInstance(Object...)} can be used with this value.
     *
     * @since 1.0
     */
    public boolean canInstantiate() {
        return impl.canInstantiate(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public Value newInstance(Object... arguments) {
        return impl.newInstance(receiver, arguments);
    }

    /**
     * Executes this value if it {@link #canExecute() can} be executed. If the result value is needed
     * use {@link #execute(Object...)} instead.
     *
     * @throws IllegalStateException if the underlying context was closed
     * @throws UnsupportedOperationException if this object cannot be executed.
     * @throws PolyglotException if a guest language error occurred during execution.
     * @see #execute(Object...)
     *
     * @since 1.0
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
     *
     *
     * @since 1.0
     */
    public boolean isString() {
        return impl.isString(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public String asString() {
        return impl.asString(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean fitsInInt() {
        return impl.fitsInInt(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public int asInt() {
        return impl.asInt(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean isBoolean() {
        return impl.isBoolean(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean asBoolean() {
        return impl.asBoolean(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean isNumber() {
        return impl.isNumber(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean fitsInLong() {
        return impl.fitsInLong(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public long asLong() {
        return impl.asLong(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean fitsInDouble() {
        return impl.fitsInDouble(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public double asDouble() {
        return impl.asDouble(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean fitsInFloat() {
        return impl.fitsInFloat(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public float asFloat() {
        return impl.asFloat(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean fitsInByte() {
        return impl.fitsInByte(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public byte asByte() {
        return impl.asByte(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean fitsInShort() {
        return impl.fitsInShort(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public short asShort() {
        return impl.asShort(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean isNull() {
        return impl.isNull(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public boolean isNativePointer() {
        return impl.isNativePointer(receiver);
    }

    /**
     *
     *
     * @since 1.0
     */
    public long asNativePointer() {
        return impl.asNativePointer(receiver);
    }

    /**
     * Returns <code>true</code> if the value originated form the host language Java. In such a case the
     * value can be accessed using {@link #asHostObject()}.
     *
     * @since 1.0
     */
    public boolean isHostObject() {
        return impl.isHostObject(receiver);
    }

    /**
     * Returns the original Java host language object.
     *
     * @throws UnsupportedOperationException if {@link #isHostObject()} is <code>false</code>.
     * @since 1.0
     */
    @SuppressWarnings("unchecked")
    public <T> T asHostObject() {
        return (T) impl.asHostObject(receiver);
    }

    public boolean isProxyObject() {
        return impl.isProxyObject(receiver);
    }

    @SuppressWarnings("unchecked")
    public <T extends Proxy> T asProxyObject() {
        return (T) impl.asProxyObject(receiver);
    }

    /**
     * Maps a polyglot value to a value with a given Java target type.
     *
     * <h1>Target type mapping</h1>
     *
     * The following target types are supported:
     * <ul>
     * <li><code>{@link Value}.class</code> is always supported and returns this instance.
     * <li><code>{@link Object}.class</code> is always supported. See section Object mapping rules.
     * <li>If the value represents a {@link #isHostObject() host object} then all classes implemented or
     * extended by the host object can be used as target type.
     * <li><code>{@link String}.class</code> is supported if the value is a {@link #isString() string}.
     * <li><code>{@link Character}.class</code> is supported if the value is a {@link #isString()
     * string} of length one.
     * <li><code>{@link Number}.class</code> is supported if the value is a {@link #isNumber() number}.
     * {@link Byte}, {@link Short}, {@link Integer}, {@link Long}, {@link Float} and {@link Double} are
     * allowed if they fit without conversion. If a conversion is necessary then a
     * {@link ClassCastException} is thrown.
     * <li><code>{@link Boolean}.class</code> is supported if the value is a {@link #isBoolean()
     * boolean}.
     * <li><code>{@link Proxy}.class</code> or one of its subclasses is supported if the value is a
     * {@link #isProxyObject() proxy}.
     * <li><code>{@link Map}.class</code> is supported if the value has {@link #hasMembers() members} or
     * {@link #hasArrayElements() array elements}. The returned map can be safely cast to Map<Object,
     * Object>. The key type in such a case is either {@link String} or {@link Long}. It is recommended
     * to use {@link #as(TypeLiteral) type literals} to specify the expected collection component types.
     * With type literals the value type can be restricted, for example to
     * <code>Map<String, String></code>. If the raw <code>{@link Map}.class</code> or an Object
     * component type is used, then the return types of the the list are subject to Object mapping rules
     * recursively.
     * <li><code>{@link List}.class</code> is supported if the value has {@link #hasArrayElements()
     * array elements} and it has an {@link Value#getArraySize() array size} that is smaller or equal
     * than {@link Integer#MAX_VALUE}. The returned list can be safely cast to
     * <code>List<Object></code>. It is recommended to use {@link #as(TypeLiteral) type literals} to
     * specify the expected component type. With type literals the value type can be restricted to any
     * supported target type, for example to <code>List<Integer></code>. If the raw
     * <code>{@link List}.class</code> or an Object component type is used, then the return types of the
     * the list are recursively subject to Object mapping rules.
     * <li>Any Java array type of a supported target type. The values of the value will be eagerly
     * coerced and copied into a new instance of the provided array type. This means that changes in
     * returned array will not be reflected in the original value. Since conversion to a Java array
     * might be an expensive operation it is recommended to use the `List` or `Collection` target type
     * if possible.
     * <li>Any {@link FunctionalInterface functional} interface if the value can be {@link #canExecute()
     * executed} or {@link #canInstantiate() instantiated}. In case a value can be executed and
     * instantiated then the returned implementation of the interface will be {@link #execute(Object...)
     * executed}. The coercion to parameter types of functional interface method is converted using the
     * semantics as {@link #asHostValue(Class)}. If standard functional interface like {@link Function}
     * are used, is recommended to use {@link #as(TypeLiteral) type literals} to specify the expected
     * function arguments and return value.
     * <li>Any interface where each method name maps to one {@link #getMember(String) member} of the
     * value. Whenever a method of the interface is executed a member with the method name must exist
     * otherwise a {@link ClassCastException} is thrown. TODO more info needed.
     *
     * <li>Any array type with a supported component type if the value {@link #hasArrayElements() has
     * array elements} and every element can be converted to the array component type using
     * {@link #asHostValue(Class)}. TODO duplicate
     * </ul>
     * An {@link ClassCastException} is thrown for unsupported expected types.
     * <p>
     * <b>JavaScript Usage Examples:</b>
     *
     * <pre>
     * Context context = Context.create();
     * assert context.eval("js", "undefined").as(Object.class) == null;
     * assert context.eval("js", "'foobar'").as(String.class).equals("foobar");
     * assert context.eval("js", "42").as(Integer.class) == 42;
     * assert context.eval("js", "{foo:'bar'}").as(Map.class).get("foo").equals("bar");
     * assert context.eval("js", "[42]").as(List.class).get(0).equals(42);
     * assert ((Map<String, Object>)context.eval("js", "[{foo:'bar'}]").as(List.class).get(0)).get("foo").equals("bar");
     *
     * &#64;FunctionalInterface interface IntFunction { int foo(int value); }
     * assert context.eval("js", "(function(a){a})").as(IntFunction.class).foo(42) == 42;
     *
     * &#64;FunctionalInterface interface StringListFunction { int foo(List<String> value); }
     * assert context.eval("js", "(function(a){a.length})").as(StringListFunction.class)
     *                                                     .foo(new String[]{"42"}) == 1;
     * </pre>
     *
     * <h1>Object target type mapping</h1>
     *
     * Object target mapping is useful to map polyglot values to its closest corresponding standard JDK
     * type.
     *
     * The following rules apply when <code>Object</code> is used as a target type:
     * <ol>
     * <li>If the value represents {@link #isNull() null} then <code>null</code> is returned.
     * <li>If the value is a {@link #isHostObject() host object} then the value is coerced to
     * {@link #asHostObject() host object value}.
     * <li>If the value is a {@link #isString() string} then the value is coerced to {@link String} or
     * {@link Character}.
     * <li>If the value is a {@link #isBoolean() boolean} then the value is coerced to {@link Boolean}.
     * <li>If the value is a {@link #isNumber() number} then the value is coerced to {@link Number}. The
     * specific sub type of the {@link Number} is not specified. Users need to be prepared for any
     * Number subclass including {@link BigInteger} or {@link BigDecimal}. It is recommended to cast to
     * {@link Number} and then convert to a Java primitive like with {@link Number#longValue()}.
     * <li>If the value {@link #hasMembers() has members} then the result value will implement
     * {@link Map}. If this value {@link #hasMembers() has members} then all members are accessible
     * using {@link String} keys. The {@link Map#size() size} of the returned {@link Map} is equal to
     * the count of all members. The returned value may also implement {@link Function} if the value can
     * be {@link #canExecute() executed} or {@link #canInstantiate() instantiated}.
     * <li>If the value has {@link #hasArrayElements() array elements} and it has an
     * {@link Value#getArraySize() array size} that is smaller or equal than {@link Integer#MAX_VALUE}
     * then the result value will implement {@link List}. Every array element of the value maps to one
     * list element. The size of the returned list maps to the array size of the value. The returned
     * value may also implement {@link Function} if the value can be {@link #canExecute() executed} or
     * {@link #canInstantiate() instantiated}.
     * <li>If the value can be {@link #canExecute() executed} or {@link #canInstantiate() instantiated}
     * then the result value implements {@link Function Function}. By default the argument of the
     * function will be used as single argument to the function when executed. If a value of type
     * {@link Object Object[]} is provided then the function will executed with those arguments. The
     * returned function may also implement {@link Map} if the value has {@link #hasArrayElements()
     * array elements} or {@link #hasMembers() members}.
     * <li>If none of the above rules apply then this {@link Value} instance is returned.
     * </ol>
     * Returned {@link #isHostObject() host objects}, {@link String}, {@link Number}, {@link Boolean}
     * and <code>null</code> values have unlimited lifetime. Other values will throw an
     * {@link IllegalStateException} for any operation if their originating {@link Context context} was
     * closed.
     * <p>
     * If a {@link Map} element is modified, a {@link List} element is modified or a {@link Function}
     * argument is provided then these values are interpreted according to the
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
     * assert ((Map<Object, Object>) context.eval("js", "[{}]").as(Object.class)).get(0) instanceof Map;
     * assert context.eval("js", "(function(){})").asHostValue() instanceof Function;
     * </pre>
     *
     * <h1>Identity preservation</h1>
     *
     * If polyglot values are mapped as Java primitives such as {@link Boolean}, <code>null</code>,
     * {@link String}, {@link Character} or {@link Number}, then the identity of the polyglot value is
     * not preserved. All other results can be converted back to a {@link Value polyglot value} using
     * {@link Context#asValue(Object)}.
     *
     * <b>Mapping Example using JavaScript:</b> This example first creates a new JavaScript object and
     * maps it to a {@link Map}. Using the {@link #asValue(Object)} it is possible to recreate the
     * {@link Value polyglot value} from the Java map. The JavaScript object identity is preserved in
     * the process.
     *
     * <pre>
     * Context context = Context.create();
     * Map<Object, Object> javaMap = context.eval("js", "{}").as(Map.class);
     * Value polyglotValue = context.asValue(javaMap);
     * </pre>
     *
     * @see #as(TypeLiteral) to map to generic type signatures.
     * @param targetType the target Java type to map
     * @throws ClassCastException if polyglot value could not be mapped to the target type.
     * @throws PolyglotException if the conversion triggered a guest language error.
     * @throws IllegalStateException if the underlying context is already closed.
     * @since 1.0
     */
    public <T> T as(Class<T> targetType) throws ClassCastException, IllegalStateException, PolyglotException {
        if (targetType == Value.class) {
            return targetType.cast(this);
        }
        return impl.as(receiver, targetType);
    }

    /**
     * Maps a polyglot value to a given Java target type literal.
     *
     * @param targetType
     * @return
     * @see #as(Class) for documetnation on
     */
    public <T> T as(TypeLiteral<T> targetType) {
        return impl.as(receiver, targetType);
    }

    /**
     * Language specific string representation of the value, when printed.
     *
     * @since 1.0
     */
    @Override
    public String toString() {
        return impl.toString(receiver);
    }

}
