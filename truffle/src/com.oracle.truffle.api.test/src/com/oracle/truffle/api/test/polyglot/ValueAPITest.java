/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static com.oracle.truffle.api.test.polyglot.ValueAssert.assertValue;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.ARRAY_ELEMENTS;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.BOOLEAN;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.EXECUTABLE;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.HOST_OBJECT;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.INSTANTIABLE;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.MEMBERS;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NULL;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NUMBER;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.PROXY_OBJECT;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;

public class ValueAPITest {

    private Context context;

    @Before
    public void setUp() {
        context = Context.create();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testInstantiate() {
        Value classValue = context.asValue(java.util.Date.class);
        Value dateInstance = classValue.newInstance();
        Date date = dateInstance.asHostObject();
        assertNotNull(date);

        long ms = 1_000_000_000L;
        dateInstance = classValue.newInstance(ms);
        date = dateInstance.asHostObject();
        assertEquals(ms, date.getTime());
    }

    private static final Object[] STRINGS = new Object[]{
                    "", 'a', "a", "foo",
    };

    @SuppressWarnings("deprecation")
    @Test
    public void testString() {
        for (Object string : STRINGS) {
            assertValue(context, context.asValue(string), STRING);
            assertValue(context, context.asValue((org.graalvm.polyglot.proxy.ProxyPrimitive) () -> string), STRING, PROXY_OBJECT);
        }
    }

    private static final Object[] NUMBERS = new Object[]{
                    (byte) 0, (byte) 1, Byte.MAX_VALUE, Byte.MIN_VALUE,
                    (short) 0, (short) 1, Short.MAX_VALUE, Short.MIN_VALUE,
                    0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE,
                    0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE,
                    0f, 0.24f, -0f, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_NORMAL,
                    0d, 0.24d, -0d, Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_NORMAL,
    };

    @SuppressWarnings("deprecation")
    @Test
    public void testNumbers() {
        for (Object number : NUMBERS) {
            assertValue(context, context.asValue(number), NUMBER);
            assertValue(context, context.asValue((org.graalvm.polyglot.proxy.ProxyPrimitive) () -> number), NUMBER, PROXY_OBJECT);
        }
    }

    private static final Object[] BOOLEANS = new Object[]{
                    false, true,
    };

    @SuppressWarnings("deprecation")
    @Test
    public void testBooleans() {
        for (Object bool : BOOLEANS) {
            assertValue(context, context.asValue(bool), BOOLEAN);
            assertValue(context, context.asValue((org.graalvm.polyglot.proxy.ProxyPrimitive) () -> bool), BOOLEAN, PROXY_OBJECT);
        }
    }

    @Test
    public void testNull() {
        assertValue(context, context.asValue(null), HOST_OBJECT, NULL);
    }

    private static final Object[] HOST_OBJECTS = new Object[]{
                    new ArrayList<>(),
                    new HashMap<>(),
                    new Date(),
                    new EmptyObject(),
                    new PrivateObject(),
                    new FieldAccess(),
                    new JavaSuperClass(),
                    new BigInteger("42"),
                    new BigDecimal("42"),
                    new Function<Object, Object>() {
                        public Object apply(Object t) {
                            return t;
                        }
                    },
                    new Supplier<String>() {
                        public String get() {
                            return "foobar";
                        }
                    },
                    BigDecimal.class,
                    Class.class,
                    Proxy.newProxyInstance(ValueAPITest.class.getClassLoader(), new Class<?>[]{ProxyInterface.class}, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            switch (method.getName()) {
                                case "foobar":
                                    return args[0];
                                case "toString":
                                    return "Proxy";
                                default:
                                    throw new UnsupportedOperationException(method.getName());
                            }
                        }
                    })};

    @Test
    public void testHostObject() {
        assertTrue(context.asValue(new EmptyObject()).getMemberKeys().isEmpty());
        assertTrue(context.asValue(new PrivateObject()).getMemberKeys().isEmpty());

        for (Object value : HOST_OBJECTS) {
            boolean functionalInterface = value instanceof Supplier || value instanceof Function;
            boolean instantiable = value instanceof Class && value != Class.class;
            if (functionalInterface) {
                assertValue(context, context.asValue(value), MEMBERS, HOST_OBJECT, EXECUTABLE);
            } else if (value instanceof List) {
                assertValue(context, context.asValue(value), MEMBERS, HOST_OBJECT, ARRAY_ELEMENTS);
            } else if (instantiable) {
                assertValue(context, context.asValue(value), MEMBERS, HOST_OBJECT, INSTANTIABLE);
            } else {
                assertValue(context, context.asValue(value), MEMBERS, HOST_OBJECT);
            }
        }
    }

    private static final Object[] ARRAYS = new Object[]{
                    new boolean[]{true, false, true},
                    new char[]{'a', 'b', 'c'},
                    new byte[]{42, 42, 42},
                    new short[]{42, 42, 42},
                    new int[]{42, 42, 42},
                    new long[]{42, 42, 42},
                    new float[]{42, 42, 42},
                    new double[]{42, 42, 42},
                    new Boolean[]{true, false, true},
                    new Character[]{'a', 'b', 'c'},
                    new Byte[]{42, 42, 42},
                    new Short[]{42, 42, 42},
                    new Integer[]{42, 42, 42},
                    new Long[]{42L, 42L, 42L},
                    new Float[]{42f, 42f, 42f},
                    new Double[]{42d, 42d, 42d},
                    new Object[]{true, 'a', "ab", (byte) 42, (short) 42, 42, 42L, 42f, 42d},
                    new String[]{"a", "b", "c"},
                    new CharSequence[]{"a"},
                    new ArrayList<>(Arrays.asList("a", 42)),
                    new DummyList()

    };

    @Test
    public void testArrays() {
        for (Object array : ARRAYS) {
            assertValue(context, context.asValue(array), ARRAY_ELEMENTS, HOST_OBJECT, MEMBERS);
        }
    }

    @Test
    public void testListRemove() {
        List<Object> list = new ArrayList<>(Arrays.asList("a", "b", 42, 43));
        Value vlist = context.asValue(list);
        assertEquals(4, vlist.getArraySize());
        boolean success = vlist.removeArrayElement(1);
        assertTrue(success);
        assertEquals(3, list.size());
        assertEquals(3, vlist.getArraySize());
    }

    @Test
    public void testComplexGenericCoercion() {
        TypeLiteral<List<Map<Integer, Map<String, Object[]>>>> literal = new TypeLiteral<List<Map<Integer, Map<String, Object[]>>>>() {
        };
        Map<String, Object> map = new HashMap<>();
        map.put("foobar", new Object[]{"baz"});
        Object[] array = new Object[]{ProxyArray.fromArray(ProxyObject.fromMap(map))};

        List<Map<Integer, Map<String, Object[]>>> value = context.asValue(array).as(literal);
        assertEquals("baz", value.get(0).get(0).get("foobar")[0]);

    }

    @Test
    public void testComplexGenericCoercion2() {
        TypeLiteral<List<Map<String, Map<String, Object[]>>>> literal = new TypeLiteral<List<Map<String, Map<String, Object[]>>>>() {
        };
        Object[] array = new Object[]{ProxyObject.fromMap(Collections.singletonMap("foo", ProxyObject.fromMap(Collections.singletonMap("bar", new Object[]{"baz"}))))};

        List<Map<String, Map<String, Object[]>>> value = context.asValue(array).as(literal);
        assertEquals("baz", value.get(0).get("foo").get("bar")[0]);
    }

    public interface JavaInterface {

    }

    @FunctionalInterface
    public interface JavaFunctionalInterface {

        void foo();

    }

    public static class JavaSuperClass {

    }

    public static class JavaClass extends JavaSuperClass implements JavaInterface, JavaFunctionalInterface {

        public void foo() {
        }

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testObjectCoercion() {
        objectCoercionTest(null, Object.class, null);
        objectCoercionTest(null, String.class, null);
        objectCoercionTest(null, Boolean.class, null);
        objectCoercionTest(null, Boolean.class, null);

        objectCoercionTest(true, Boolean.class, null);
        objectCoercionTest("foo", String.class, null);
        objectCoercionTest('c', Character.class, null);
        objectCoercionTest((byte) 42, Byte.class, null);
        objectCoercionTest((short) 42, Short.class, null);
        objectCoercionTest(42, Integer.class, null);
        objectCoercionTest((long) 42, Long.class, null);
        objectCoercionTest(42.5d, Double.class, null);
        objectCoercionTest(42.5f, Float.class, null);

        objectCoercionTest(new JavaClass(), JavaClass.class, null);
        objectCoercionTest(new JavaClass(), JavaSuperClass.class, null);
        objectCoercionTest(new JavaClass(), JavaInterface.class, null);
        objectCoercionTest(new JavaClass(), JavaFunctionalInterface.class, null);
        objectCoercionTest(new JavaClass(), Object.class, null);

        Map<String, Object> map = new HashMap<>();
        map.put("foobar", "baz");
        map.put("foobar2", "baz2");
        objectCoercionTest(ProxyObject.fromMap(map), Map.class,
                        (v) -> assertEquals("baz", v.get("foobar")));
        objectCoercionTest(ProxyObject.fromMap(map), Map.class, (v) -> {
            assertNull(v.remove("notAMember"));
            assertEquals("baz", v.remove("foobar"));
            assertFalse(v.remove("foobar2", "baz"));
            assertFalse(v.remove("foobar", "baz2"));
            assertTrue(v.remove("foobar2", "baz2"));
            assertTrue(map.isEmpty());
            map.put("foobar", "baz");
            map.put("foobar2", "baz2");
        });

        ProxyArray array = ProxyArray.fromArray(42, 42, 42);
        objectCoercionTest(array, List.class,
                        (v) -> assertEquals(42, v.get(2)));
        List<Object> arrayList = new ArrayList<>();
        ProxyArray list = ProxyArray.fromList(arrayList);
        objectCoercionTest(list, List.class, (v) -> {
            arrayList.addAll(Arrays.asList(41, 42));
            assertEquals(42, v.remove(1));
            assertTrue(v.remove((Object) 41));
            assertFalse(v.remove((Object) 14));
        });

        ArrayElements arrayElements = new ArrayElements();
        arrayElements.array.add(42);

        objectCoercionTest(arrayElements, List.class, (v) -> {
            assertEquals(42, v.get(0));
            assertEquals(1, v.size());
            assertFalse(v instanceof Function);

            Value value = context.asValue(v);
            assertFalse(value.canExecute());
            assertFalse(value.canInstantiate());
            assertFalse(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        MembersAndArray mapAndArray = new MembersAndArray();
        mapAndArray.map.put("foo", "bar");
        mapAndArray.array.add(42);

        objectCoercionTest(mapAndArray, Map.class, (v) -> {
            assertNull(v.get(0L));
            assertEquals("bar", v.get("foo"));
            assertEquals(1, v.size());
            assertFalse(v instanceof Function);

            Value value = context.asValue(v);
            assertFalse(value.canExecute());
            assertFalse(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        MembersAndInstantiable membersAndInstantiable = new MembersAndInstantiable();
        membersAndInstantiable.map.put("foo", "bar");
        membersAndInstantiable.instantiableResult = "foobarbaz";

        objectCoercionTest(membersAndInstantiable, Map.class, (v) -> {
            assertEquals("bar", v.get("foo"));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));

            Value value = context.asValue(v);
            assertFalse(value.canExecute());
            assertTrue(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertFalse(value.hasArrayElements());
        });

        MembersAndExecutable membersAndExecutable = new MembersAndExecutable();
        membersAndExecutable.map.put("foo", "bar");
        membersAndExecutable.executableResult = "foobarbaz";

        objectCoercionTest(membersAndExecutable, Map.class, (v) -> {
            assertEquals("bar", v.get("foo"));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));

            Value value = context.asValue(v);
            assertTrue(value.canExecute());
            assertFalse(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertFalse(value.hasArrayElements());
        });

        MembersAndArrayAndExecutable mapAndArrayAndExecutable = new MembersAndArrayAndExecutable();
        mapAndArrayAndExecutable.map.put("foo", "bar");
        mapAndArrayAndExecutable.array.add(42);
        mapAndArrayAndExecutable.executableResult = "foobarbaz";

        objectCoercionTest(mapAndArrayAndExecutable, Map.class, (v) -> {
            assertNull(v.get(0L));
            assertEquals("bar", v.get("foo"));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));

            Value value = context.asValue(v);
            assertTrue(value.canExecute());
            assertFalse(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        MembersAndArrayAndInstantiable mapAndArrayAndInstantiable = new MembersAndArrayAndInstantiable();
        mapAndArrayAndInstantiable.map.put("foo", "bar");
        mapAndArrayAndInstantiable.array.add(42);
        mapAndArrayAndInstantiable.instantiableResult = "foobarbaz";

        objectCoercionTest(mapAndArrayAndInstantiable, Map.class, (v) -> {
            assertNull(v.get(0L));
            assertEquals("bar", v.get("foo"));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));

            Value value = context.asValue(v);
            assertFalse(value.canExecute());
            assertTrue(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        MembersAndArrayAndExecutableAndInstantiable mapAndArrayAndExecutableAndInstantiable = new MembersAndArrayAndExecutableAndInstantiable();
        mapAndArrayAndExecutableAndInstantiable.map.put("foo", "bar");
        mapAndArrayAndExecutableAndInstantiable.array.add(42);
        mapAndArrayAndExecutableAndInstantiable.executableResult = "foobarbaz";

        objectCoercionTest(mapAndArrayAndExecutableAndInstantiable, Map.class, (v) -> {
            assertNull(v.get(0L));
            assertEquals("bar", v.get("foo"));
            assertEquals(1, v.size());
            assertTrue(v instanceof Function);
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));
            Value value = context.asValue(v);
            assertTrue(value.canExecute());
            assertTrue(value.canInstantiate());
            assertTrue(value.hasMembers());
            assertTrue(value.hasArrayElements());
        });

        Executable exectable = new Executable();
        exectable.executableResult = "foobarbaz";

        objectCoercionTest(exectable, Function.class, (v) -> {
            assertEquals("foobarbaz", ((Function<Object, Object>) v).apply(new Object[0]));
            Value value = context.asValue(v);
            assertTrue(value.canExecute());
            assertFalse(value.canInstantiate());
            assertFalse(value.hasMembers());
            assertFalse(value.hasArrayElements());
        });

    }

    @SuppressWarnings({"unchecked"})
    private <T> void objectCoercionTest(Object value, Class<T> expectedType, Consumer<T> validator) {
        Value coerce = context.asValue(new CoerceObject()).getMember("coerce");
        T result = (T) context.asValue(value).as(Object.class);
        if (result != null) {
            assertTrue("expected " + expectedType + " but was " + result.getClass(), expectedType.isInstance(result));
        } else if (value != null) {
            fail("expected " + expectedType + " but was null");
        }

        if (validator == null) {
            assertEquals(value, result);
        } else {
            validator.accept(result);
            coerce.execute(value, validator);
        }

        assertValue(context, context.asValue(value));
        assertValue(context, context.asValue(result));
    }

    private static class DummyList extends DummyCollection implements List<Object> {

        public boolean addAll(int index, Collection<? extends Object> c) {
            return values.addAll(index, c);
        }

        public Object get(int index) {
            return values.get(index);
        }

        public Object set(int index, Object element) {
            return values.set(index, element);
        }

        public void add(int index, Object element) {
            values.add(index, element);
        }

        public Object remove(int index) {
            return values.remove(index);
        }

        public int indexOf(Object o) {
            return values.indexOf(o);
        }

        public int lastIndexOf(Object o) {
            return values.lastIndexOf(o);
        }

        public ListIterator<Object> listIterator() {
            return values.listIterator();
        }

        public ListIterator<Object> listIterator(int index) {
            return values.listIterator(index);
        }

        public List<Object> subList(int fromIndex, int toIndex) {
            return values.subList(fromIndex, toIndex);
        }

    }

    private static class DummyCollection implements Collection<Object> {
        List<Object> values = Arrays.asList("a", 42);

        public java.util.Iterator<Object> iterator() {
            return values.iterator();
        }

        public int size() {
            return values.size();
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public boolean contains(Object o) {
            return values.contains(o);
        }

        public Object[] toArray() {
            return values.toArray();
        }

        public <T> T[] toArray(T[] a) {
            return values.toArray(a);
        }

        public boolean add(Object e) {
            return values.add(e);
        }

        public boolean remove(Object o) {
            return values.remove(o);
        }

        public boolean containsAll(Collection<?> c) {
            return values.containsAll(c);
        }

        public boolean addAll(Collection<? extends Object> c) {
            return values.addAll(c);
        }

        public boolean removeAll(Collection<?> c) {
            return values.removeAll(c);
        }

        public boolean retainAll(Collection<?> c) {
            return values.retainAll(c);
        }

        public void clear() {
            values.clear();
        }
    }

    public static class CoerceObject {

        public <T> Object coerce(T value, Consumer<T> o) {
            o.accept(value);
            return value;
        }

    }

    public static class EmptyObject {
    }

    private static class PrivateObject {
    }

    private static class FieldAccess {
        @SuppressWarnings("unused") public EmptyObject member;
    }

    private interface ProxyInterface {

        Object foobar(Object... args);

    }

    static class Members implements ProxyObject {

        final Map<String, Object> map = new HashMap<>();

        public Object getMember(String key) {
            return map.get(key);
        }

        public ProxyArray getMemberKeys() {
            return ProxyArray.fromArray(map.keySet().toArray());
        }

        public boolean hasMember(String key) {
            return map.containsKey(key);
        }

        public void putMember(String key, Value value) {
            map.put(key, value);
        }

    }

    static class ArrayElements implements ProxyArray {

        final List<Object> array = new ArrayList<>();

        public Object get(long index) {
            return array.get((int) index);
        }

        public void set(long index, Value value) {
            array.set((int) index, value);
        }

        public long getSize() {
            return array.size();
        }
    }

    static class MembersAndArray extends Members implements ProxyObject, ProxyArray {

        final List<Object> array = new ArrayList<>();

        public Object get(long index) {
            return array.get((int) index);
        }

        public void set(long index, Value value) {
            array.set((int) index, value);
        }

        public long getSize() {
            return array.size();
        }

    }

    private static class MembersAndExecutable extends Members implements ProxyObject, ProxyExecutable {

        Object executableResult;

        public Object execute(Value... arguments) {
            return executableResult;
        }
    }

    private static class MembersAndInstantiable extends Members implements ProxyObject, ProxyInstantiable {

        Object instantiableResult;

        public Object newInstance(Value... arguments) {
            return instantiableResult;
        }

    }

    private static class MembersAndArrayAndExecutable extends MembersAndArray implements ProxyArray, ProxyObject, ProxyExecutable {

        Object executableResult;

        public Object execute(Value... arguments) {
            return executableResult;
        }

    }

    private static class MembersAndArrayAndInstantiable extends MembersAndArray implements ProxyArray, ProxyObject, ProxyInstantiable {

        Object instantiableResult;

        public Object newInstance(Value... arguments) {
            return instantiableResult;
        }

    }

    private static class MembersAndArrayAndExecutableAndInstantiable extends MembersAndArray implements ProxyArray, ProxyObject, ProxyInstantiable, ProxyExecutable {

        Object executableResult;
        Object instantiableResult;

        public Object execute(Value... arguments) {
            return executableResult;
        }

        public Object newInstance(Value... arguments) {
            return instantiableResult;
        }

    }

    private static class Executable implements ProxyExecutable {

        Object executableResult;

        public Object execute(Value... arguments) {
            return executableResult;
        }

    }

    @Test
    public void testNullCoercionErrors() {
        Value nullValue = context.asValue(null);
        assertFails(() -> nullValue.asInt(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int' using Value.asInt(). " +
                                        "You can ensure that the operation is supported using Value.fitsInInt().");
        assertFails(() -> nullValue.as(int.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");
        assertFails(() -> nullValue.asByte(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'byte' using Value.asByte(). " +
                                        "You can ensure that the operation is supported using Value.fitsInByte().");
        assertFails(() -> nullValue.as(byte.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'byte'.");
        assertFails(() -> nullValue.asShort(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'short' using Value.asShort(). " +
                                        "You can ensure that the operation is supported using Value.fitsInShort().");
        assertFails(() -> nullValue.as(short.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'short'.");
        assertFails(() -> nullValue.asLong(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'long' using Value.asLong(). " +
                                        "You can ensure that the operation is supported using Value.fitsInLong().");
        assertFails(() -> nullValue.as(long.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'long'.");
        assertFails(() -> nullValue.asFloat(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'float' using Value.asFloat(). " +
                                        "You can ensure that the operation is supported using Value.fitsInFloat().");
        assertFails(() -> nullValue.as(float.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'float'.");
        assertFails(() -> nullValue.asDouble(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'double' using Value.asDouble(). " +
                                        "You can ensure that the operation is supported using Value.fitsInDouble().");
        assertFails(() -> nullValue.as(double.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'double'.");
        assertFails(() -> nullValue.as(char.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'char'.");
        assertFails(() -> nullValue.asBoolean(), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'boolean' using Value.asBoolean(). " +
                                        "You can ensure that the operation is supported using Value.isBoolean().");
        assertFails(() -> nullValue.as(boolean.class), NullPointerException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'boolean'.");
    }

    @Test
    public void testPrimitiveCoercionErrors() {
        Value bigNumber = context.asValue(Long.MAX_VALUE);

        assertFails(() -> bigNumber.asByte(), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'byte' using Value.asByte(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInByte().");
        assertFails(() -> bigNumber.as(byte.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'byte': Invalid or lossy primitive coercion.");
        assertFails(() -> bigNumber.as(Byte.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'java.lang.Byte': Invalid or lossy primitive coercion.");

        assertFails(() -> bigNumber.asShort(), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'short' using Value.asShort(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInShort().");
        assertFails(() -> bigNumber.as(short.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'short': Invalid or lossy primitive coercion.");
        assertFails(() -> bigNumber.as(Short.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'java.lang.Short': Invalid or lossy primitive coercion.");

        assertFails(() -> bigNumber.asInt(), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'int' using Value.asInt(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInInt().");
        assertFails(() -> bigNumber.as(int.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> bigNumber.as(Integer.class), ClassCastException.class,
                        "Cannot convert '9223372036854775807'(language: Java, type: java.lang.Long) to Java type 'java.lang.Integer': Invalid or lossy primitive coercion.");

        Value nan = context.asValue(Double.NaN);

        assertFails(() -> nan.asLong(), ClassCastException.class,
                        "Cannot convert 'NaN'(language: Java, type: java.lang.Double) to Java type 'long' using Value.asLong(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInLong().");
        assertFails(() -> nan.as(long.class), ClassCastException.class,
                        "Cannot convert 'NaN'(language: Java, type: java.lang.Double) to Java type 'long': Invalid or lossy primitive coercion.");
        assertFails(() -> nan.as(Long.class), ClassCastException.class,
                        "Cannot convert 'NaN'(language: Java, type: java.lang.Double) to Java type 'java.lang.Long': Invalid or lossy primitive coercion.");

        Value nofloat = context.asValue(Double.MAX_VALUE);

        assertFails(() -> nofloat.asFloat(), ClassCastException.class,
                        "Cannot convert '1.7976931348623157E308'(language: Java, type: java.lang.Double) to Java type 'float' using Value.asFloat(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInFloat().");
        assertFails(() -> nofloat.as(float.class), ClassCastException.class,
                        "Cannot convert '1.7976931348623157E308'(language: Java, type: java.lang.Double) to Java type 'float': Invalid or lossy primitive coercion.");
        assertFails(() -> nofloat.as(Float.class), ClassCastException.class,
                        "Cannot convert '1.7976931348623157E308'(language: Java, type: java.lang.Double) to Java type 'java.lang.Float': Invalid or lossy primitive coercion.");

        Value nodouble = context.asValue(9007199254740992L);

        assertFails(() -> nodouble.asDouble(), ClassCastException.class,
                        "Cannot convert '9007199254740992'(language: Java, type: java.lang.Long) to Java type 'double' using Value.asDouble(): Invalid or lossy primitive coercion. " +
                                        "You can ensure that the value can be converted using Value.fitsInDouble().");
        assertFails(() -> nodouble.as(double.class), ClassCastException.class,
                        "Cannot convert '9007199254740992'(language: Java, type: java.lang.Long) to Java type 'double': Invalid or lossy primitive coercion.");
        assertFails(() -> nodouble.as(Double.class), ClassCastException.class,
                        "Cannot convert '9007199254740992'(language: Java, type: java.lang.Long) to Java type 'java.lang.Double': Invalid or lossy primitive coercion.");

        Value noString = context.asValue(false);

        assertFails(() -> noString.asString(), ClassCastException.class,
                        "Cannot convert 'false'(language: Java, type: java.lang.Boolean) to Java type 'java.lang.String' using Value.asString(): Invalid coercion. You can ensure that the value can be converted using Value.isString().");
        assertFails(() -> noString.as(char.class), ClassCastException.class,
                        "Cannot convert 'false'(language: Java, type: java.lang.Boolean) to Java type 'char': Invalid or lossy primitive coercion.");
        assertEquals("false", noString.as(String.class));

        Value noBoolean = context.asValue("foobar");

        assertFails(() -> noBoolean.asBoolean(), ClassCastException.class,
                        "Cannot convert 'foobar'(language: Java, type: java.lang.String) to Java type 'boolean' using Value.asBoolean(): Invalid or lossy primitive coercion. You can ensure that the value can be converted using Value.isBoolean().");
        assertFails(() -> noBoolean.as(boolean.class), ClassCastException.class,
                        "Cannot convert 'foobar'(language: Java, type: java.lang.String) to Java type 'boolean': Invalid or lossy primitive coercion.");
        assertFails(() -> noBoolean.as(Boolean.class), ClassCastException.class,
                        "Cannot convert 'foobar'(language: Java, type: java.lang.String) to Java type 'java.lang.Boolean': Invalid or lossy primitive coercion.");

    }

    private static class EmptyProxy implements org.graalvm.polyglot.proxy.Proxy {

        @Override
        public String toString() {
            return "proxy";
        }

    }

    @Test
    public void testTypeCoercionError() {
        Value pipe = context.asValue("not a pipe");

        assertFails(() -> pipe.as(List.class), ClassCastException.class,
                        "Cannot convert 'not a pipe'(language: Java, type: java.lang.String) to Java type 'java.util.List': Unsupported target type.");
        assertFails(() -> pipe.as(Map.class), ClassCastException.class,
                        "Cannot convert 'not a pipe'(language: Java, type: java.lang.String) to Java type 'java.util.Map': Unsupported target type.");
        assertFails(() -> pipe.as(Function.class), ClassCastException.class,
                        "Cannot convert 'not a pipe'(language: Java, type: java.lang.String) to Java type 'java.util.function.Function': Unsupported target type.");
        assertFails(() -> pipe.as(JavaInterface.class), ClassCastException.class,
                        "Cannot convert 'not a pipe'(language: Java, type: java.lang.String) to Java type 'com.oracle.truffle.api.test.polyglot.ValueAPITest$JavaInterface': Unsupported target type.");

        Value other = context.asValue(new EmptyProxy());

        assertFails(() -> other.as(List.class), ClassCastException.class,
                        "Cannot convert 'proxy'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$EmptyProxy) to Java type 'java.util.List': Value must have array elements.");
        assertFails(() -> other.as(Map.class), ClassCastException.class,
                        "Cannot convert 'proxy'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$EmptyProxy) to Java type 'java.util.Map': Value must have members or array elements.");
        assertFails(() -> other.as(Function.class), ClassCastException.class,
                        "Cannot convert 'proxy'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$EmptyProxy) to Java type 'java.util.function.Function': Value must be executable or instantiable.");
        assertFails(() -> other.as(JavaInterface.class), ClassCastException.class,
                        "Cannot convert 'proxy'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$EmptyProxy) to Java type 'com.oracle.truffle.api.test.polyglot.ValueAPITest$JavaInterface': Value must have members.");

    }

    @Test
    public void testUnsupportedError() {
        Value pipe = context.asValue("not a pipe");

        assertFails(() -> pipe.getMember(""), UnsupportedOperationException.class,
                        "Unsupported operation Value.getMember(String) for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasMembers().");
        assertFails(() -> pipe.putMember("", null), UnsupportedOperationException.class,
                        "Unsupported operation Value.putMember(String, Object) for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasMembers().");
        assertFails(() -> pipe.getArrayElement(0), UnsupportedOperationException.class,
                        "Unsupported operation Value.getArrayElement(long) for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");
        assertFails(() -> pipe.setArrayElement(0, null), UnsupportedOperationException.class,
                        "Unsupported operation Value.setArrayElement(long, Object) for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");
        assertFails(() -> pipe.getArraySize(), UnsupportedOperationException.class,
                        "Unsupported operation Value.getArraySize() for 'not a pipe'(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");

    }

    private static final TypeLiteral<List<String>> STRING_LIST = new TypeLiteral<List<String>>() {
    };
    private static final TypeLiteral<List<Integer>> INTEGER_LIST = new TypeLiteral<List<Integer>>() {
    };

    @Test
    @SuppressWarnings("unchecked")
    public void testArrayErrors() {
        String[] array = new String[]{"asdf"};
        Value v = context.asValue(array);

        assertFails(() -> v.getArrayElement(1L), IndexOutOfBoundsException.class,
                        "Invalid array index 1 for array '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> v.setArrayElement(1L, null), IndexOutOfBoundsException.class,
                        "Invalid array index 1 for array '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> v.removeArrayElement(0), UnsupportedOperationException.class,
                        "Unsupported operation Value.removeArrayElement(long, Object) for '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFalse(v.removeMember("a"));

        List<Object> list = v.as(List.class);
        assertFails(() -> list.get(1), IndexOutOfBoundsException.class,
                        "Invalid index 1 for List<Object> '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> list.set(1, null), IndexOutOfBoundsException.class,
                        "Invalid index 1 for List<Object> '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> list.remove(0), UnsupportedOperationException.class,
                        "Unsupported operation remove for List<Object> '[asdf]'(language: Java, type: java.lang.String[]).");

        List<?> stringList = v.as(STRING_LIST);
        assertFails(() -> stringList.get(1), IndexOutOfBoundsException.class,
                        "Invalid index 1 for List<java.lang.String> '[asdf]'(language: Java, type: java.lang.String[]).");
        assertFails(() -> stringList.set(1, null), IndexOutOfBoundsException.class,
                        "Invalid index 1 for List<java.lang.String> '[asdf]'(language: Java, type: java.lang.String[]).");

        ((List<Object>) stringList).set(0, 42);
        assertEquals("42", stringList.get(0));
        ((List<Object>) stringList).set(0, context.asValue(42));
        assertEquals("42", stringList.get(0));

        // just to make sure this works
        ((List<Object>) stringList).set(0, context.asValue("foo"));

        List<Integer> integerList = v.as(INTEGER_LIST);
        assertFails(() -> integerList.get(0), ClassCastException.class,
                        "Cannot convert 'foo'(language: Java, type: java.lang.String) to Java type 'java.lang.Integer': Invalid or lossy primitive coercion.");

        Value notAnArray = context.asValue("");
        assertFails(() -> notAnArray.getArrayElement(0), UnsupportedOperationException.class,
                        "Unsupported operation Value.getArrayElement(long) for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");
        assertFails(() -> notAnArray.setArrayElement(0, null), UnsupportedOperationException.class,
                        "Unsupported operation Value.setArrayElement(long, Object) for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");
        assertFails(() -> notAnArray.getArraySize(), UnsupportedOperationException.class,
                        "Unsupported operation Value.getArraySize() for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasArrayElements().");

        Value rv = context.asValue(new ArrayList<>(Arrays.asList(new String[]{"a", "b", "c"})));
        assertFails(() -> rv.removeArrayElement(3), IndexOutOfBoundsException.class,
                        "Invalid array index 3 for array '[a, b, c]'(language: Java, type: java.util.ArrayList).");
        assertFalse(v.removeMember("a"));
    }

    private static final TypeLiteral<Map<String, String>> STRING_MAP = new TypeLiteral<Map<String, String>>() {
    };

    public static class MemberErrorTest {

        public int value = 43;
        public final int finalValue = 42;

        @Override
        public String toString() {
            return "MemberErrorTest";
        }

    }

    @Test
    public void testMemberErrors() {
        Value noMembers = context.asValue("");
        assertFails(() -> noMembers.getMember(""), UnsupportedOperationException.class,
                        "Unsupported operation Value.getMember(String) for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasMembers().");
        assertFails(() -> noMembers.putMember("", null), UnsupportedOperationException.class,
                        "Unsupported operation Value.putMember(String, Object) for ''(language: Java, type: java.lang.String). You can ensure that the operation is supported using Value.hasMembers().");
        assertFails(() -> noMembers.removeMember(""), UnsupportedOperationException.class,
                        "Unsupported operation Value.removeMember(String, Object) for ''(language: Java, type: java.lang.String).");

        assertEquals(0, noMembers.getMemberKeys().size());
        assertFalse(noMembers.hasMembers());
        assertFalse(noMembers.hasMember(""));
        assertFalse(noMembers.getMemberKeys().contains(""));

        MemberErrorTest test = new MemberErrorTest();
        Value v = context.asValue(test);
        assertEquals(43, v.getMember("value").asInt());
        v.putMember("value", 42);
        assertEquals(42, v.getMember("value").asInt());

        assertFails(() -> v.putMember("value", ""), IllegalArgumentException.class,
                        "Invalid member value ''(language: Java, type: java.lang.String) for object 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest) and member key 'value'.");

        assertFails(() -> v.putMember("finalValue", 42), IllegalArgumentException.class,
                        "Invalid member key 'finalValue' for object 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        assertFails(() -> v.putMember("notAMember", ""), IllegalArgumentException.class,
                        "Invalid member key 'notAMember' for object 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        assertNull(v.getMember("notAMember"));

        assertFalse(v.removeMember("notAMember"));

        @SuppressWarnings("unchecked")
        Map<Object, Object> map = v.as(Map.class);

        // maps behave slightly differently for not existing keys
        assertNull(map.get("notAMember"));

        map.put("value", 43);
        assertEquals(43, map.get("value"));

        assertFails(() -> map.put(new Object(), ""), IllegalArgumentException.class,
                        "Illegal identifier type 'java.lang.Object' for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        Map<String, String> stringMap = v.as(STRING_MAP);
        assertEquals("43", stringMap.get("value"));

        assertFails(() -> map.put("value", ""), ClassCastException.class,
                        "Invalid value ''(language: Java, type: java.lang.String) for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest) and identifier 'value'.");

        assertFails(() -> map.put("finalValue", 42), IllegalArgumentException.class,
                        "Invalid or unmodifiable value for identifier 'finalValue' for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        assertFails(() -> map.put("finalValue", "42"), ClassCastException.class,
                        "Invalid value '42'(language: Java, type: java.lang.String) for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest) and identifier 'finalValue'.");

        assertFails(() -> map.put("notAMember", ""), IllegalArgumentException.class,
                        "Invalid or unmodifiable value for identifier 'notAMember' for Map<Object, Object> 'MemberErrorTest'(language: Java, type: com.oracle.truffle.api.test.polyglot.ValueAPITest$MemberErrorTest).");

        Map<Object, Object> rmap = new HashMap<>();
        rmap.put("value", 43);
        Value rv = context.asValue(rmap);
        assertFalse(rv.removeMember("notAMember"));
    }

    @FunctionalInterface
    public interface ExecutableInterface {

        String execute(String argument);

    }

    @FunctionalInterface
    public interface OtherInterface0 {

        Object execute();

    }

    @FunctionalInterface
    public interface OtherInterface1 {

        Object execute(Object s);

    }

    @FunctionalInterface
    public interface OtherInterface2 {

        Object execute(String s, String s2);

    }

    @SuppressWarnings("unused")
    public static class AmbiguousType {

        public String f(int a, byte b) {
            return "1";
        }

        public String f(byte a, int b) {
            return "2";
        }
    }

    @Test
    public void testExecutableErrors() {
        ExecutableInterface executable = new ExecutableInterface() {

            public String execute(String argument) {
                return argument;
            }

            @Override
            public String toString() {
                return "testExecutable";
            }
        };

        Value v = context.asValue(executable);

        assertTrue(v.canExecute());
        assertEquals("", v.execute("").as(Object.class));
        assertEquals("", v.execute("").asString());

        String className = executable.getClass().getName();
        assertFails(() -> v.execute("", ""), IllegalArgumentException.class,
                        "Invalid argument count when executing 'testExecutable'(language: Java, type: " + className + ") " +
                                        "with arguments [''(language: Java, type: java.lang.String), ''(language: Java, type: java.lang.String)]. Expected 1 argument(s) but got 2.");

        assertFails(() -> v.execute(), IllegalArgumentException.class,
                        "Invalid argument count when executing 'testExecutable'(language: Java, type: " + className + ") with arguments []." +
                                        " Expected 1 argument(s) but got 0.");

        assertTrue(v.execute(42).isString());
        assertEquals("42", v.execute(42).asString());

        assertFails(() -> context.asValue("").execute(), UnsupportedOperationException.class,
                        "Unsupported operation Value.execute(Object...) for ''(language: Java, type: java.lang.String). You can ensure that the operation " +
                                        "is supported using Value.canExecute().");

        assertFails(() -> v.as(OtherInterface0.class).execute(), IllegalArgumentException.class,
                        "Invalid argument count when executing 'testExecutable'(language: Java, type: " + className + ") " +
                                        "with arguments []. Expected 1 argument(s) but got 0.");

        assertEquals("", v.as(OtherInterface1.class).execute(""));

        assertEquals("42", v.as(OtherInterface1.class).execute(42));

        assertFails(() -> v.as(OtherInterface2.class).execute("", ""), IllegalArgumentException.class,
                        "Invalid argument count when executing 'testExecutable'(language: Java, " +
                                        "type: " + className + ") with arguments [''(language: Java, type: java.lang.String), " +
                                        "''(language: Java, type: java.lang.String)]. Expected 1 argument(s) but got 2.");

        assertSame(executable, v.as(ExecutableInterface.class));

        Value value = context.asValue(new AmbiguousType());
        assertFails(() -> value.getMember("f").execute(1, 2), IllegalArgumentException.class,
                        "Invalid argument when executing 'com.oracle.truffle.api.test.polyglot.ValueAPITest$AmbiguousType.f'" +
                                        "(language: Java, type: Bound Method) with arguments ['1'(language: Java, type: java.lang.Integer), " +
                                        "'2'(language: Java, type: java.lang.Integer)].");
    }

    private static void assertFails(Runnable r, Class<?> hostExceptionType, String message) {
        try {
            r.run();
            Assert.fail("No error but expected " + hostExceptionType);
        } catch (Exception e) {
            if (!hostExceptionType.isInstance(e)) {
                throw new AssertionError(e.getClass().getName() + ":" + e.getMessage(), e);
            }
            if (!message.equals(e.getMessage())) {
                ComparisonFailure f = new ComparisonFailure(null, message, e.getMessage());
                f.initCause(e);
                throw f;
            }
        }
    }

    @Test
    public void testList() {
        List<String> list = new ArrayList<>();
        list.add("foo");
        list.add("bar");
        Value v = context.asValue(list);

        assertTrue(v.hasArrayElements());
        assertTrue(v.hasMembers());
        assertEquals("foo", v.getArrayElement(0).asString());
        assertEquals("bar", v.getArrayElement(1).asString());

        ValueAssert.assertFails(() -> v.getArrayElement(2), ArrayIndexOutOfBoundsException.class);
        // append to the list
        v.setArrayElement(2, "baz");
        assertEquals("foo", v.getArrayElement(0).asString());
        assertEquals("bar", v.getArrayElement(1).asString());
        assertEquals("baz", v.getArrayElement(2).asString());

        assertTrue(v.removeArrayElement(1));
        assertEquals("foo", v.getArrayElement(0).asString());
        assertEquals("baz", v.getArrayElement(1).asString());

        assertTrue(v.removeArrayElement(0));
        assertEquals("baz", v.getArrayElement(0).asString());

        assertTrue(v.removeArrayElement(0));
        assertTrue(v.getArraySize() == 0);
    }

    @Test
    public void testRecursiveList() {
        Object[] o1 = new Object[1];
        Object[] o2 = new Object[]{o1};
        o1[0] = o2;

        Value v1 = context.asValue(o1);
        Value v2 = context.asValue(o2);

        assertEquals(v1.as(List.class), v1.as(List.class));
        assertEquals(v2.as(List.class), v2.as(List.class));
        assertNotEquals(v1.as(List.class), (v2.as(List.class)));
        assertNotEquals(v1, v2);
        assertEquals(v1, v1);
        assertEquals(v2, v2);

        ValueAssert.assertValue(context, v1);
        ValueAssert.assertValue(context, v2);
    }

    public static class RecursiveObject {

        public RecursiveObject rec;

    }

    @Test
    public void testRecursiveObject() {
        RecursiveObject o1 = new RecursiveObject();
        RecursiveObject o2 = new RecursiveObject();
        o1.rec = o2;
        o2.rec = o1;

        Value v1 = context.asValue(o1);
        Value v2 = context.asValue(o2);

        assertEquals(v1.as(Map.class), v1.as(Map.class));
        assertEquals(v2.as(Map.class), v2.as(Map.class));
        assertNotEquals(v1.as(Map.class), v2.as(Map.class));
        assertNotEquals(v1, v2);
        assertEquals(v1, v1);
        assertEquals(v2, v2);

        ValueAssert.assertValue(context, v1);
        ValueAssert.assertValue(context, v2);
    }

    public interface EmptyInterface {

        void foo();

        void bar();

    }

    @FunctionalInterface
    public interface EmptyFunctionalInterface {

        void noop();

    }

    @Test
    public void testValueContextPropagation() {
        ProxyInteropObject o = new ProxyInteropObject() {
            @Override
            public boolean hasKeys() {
                return true;
            }

            @Override
            public boolean isExecutable() {
                return true;
            }

            @Override
            public boolean hasSize() {
                return true;
            }
        };
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(o));
            }

            @Override
            protected String toString(@SuppressWarnings("hiding") LanguageContext context, Object value) {
                if (o == value) {
                    return "true";
                } else {
                    return "false";
                }
            }
        });
        Value v = context.eval(ProxyLanguage.ID, "");
        assertEquals("true", v.toString());
        assertEquals("true", context.asValue(v).toString());
        assertEquals("true", v.as(Map.class).toString());
        assertEquals("true", v.as(Function.class).toString());
        assertEquals("true", v.as(List.class).toString());
        assertEquals("true", context.asValue(v.as(Map.class)).toString());
        assertEquals("true", context.asValue(v.as(Function.class)).toString());
        assertEquals("true", context.asValue(v.as(List.class)).toString());

        assertEquals(v, v);
        assertEquals(v, context.asValue(v));

        assertEquals(v.as(Map.class), v.as(Map.class));
        assertEquals(v.as(Function.class), v.as(Function.class));
        assertEquals(v.as(List.class), v.as(List.class));
        assertEquals(v.as(Map.class), context.asValue(v.as(Map.class)).as(Map.class));
        assertEquals(v.as(Function.class), context.asValue(v.as(Function.class)).as(Function.class));
        assertEquals(v.as(List.class), context.asValue(v.as(List.class)).as(List.class));

        assertNotEquals(v.as(Function.class), v.as(Map.class));
        assertNotEquals(v.as(Function.class), v.as(List.class));
        assertNotEquals(v.as(Map.class), v.as(Function.class));
        assertNotEquals(v.as(Map.class), v.as(List.class));
        assertNotEquals(v.as(List.class), v.as(Function.class));
        assertNotEquals(v.as(List.class), v.as(Map.class));

    }

}
