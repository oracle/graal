/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static com.oracle.truffle.api.test.polyglot.ValueAssert.assertValue;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.ARRAY_ELEMENTS;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.BOOLEAN;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.PROXY_OBJECT;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.HOST_OBJECT;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.MEMBERS;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NULL;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.NUMBER;
import static com.oracle.truffle.api.test.polyglot.ValueAssert.Trait.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyPrimitive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

    @Test
    public void testString() {
        for (Object string : STRINGS) {
            assertValue(context, context.asValue(string), STRING);
            assertValue(context, context.asValue((ProxyPrimitive) () -> string), STRING, PROXY_OBJECT);
        }
    }

    private static final Object[] NUMBERS = new Object[]{
                    (byte) 0, (byte) 1, Byte.MAX_VALUE, Byte.MIN_VALUE,
                    (short) 0, (short) 1, Short.MAX_VALUE, Short.MIN_VALUE,
                    (int) 0, (int) 1, Integer.MAX_VALUE, Integer.MIN_VALUE,
                    (long) 0, (long) 1, Long.MAX_VALUE, Long.MIN_VALUE,
                    (float) 0, (float) 0.24f, -0f, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_NORMAL,
                    (double) 0, (double) 0.24f, -0d, Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_NORMAL,
    };

    @Test
    public void testNumbers() {
        for (Object number : NUMBERS) {
            assertValue(context, context.asValue(number), NUMBER);
            assertValue(context, context.asValue((ProxyPrimitive) () -> number), NUMBER, PROXY_OBJECT);
        }
    }

    private static final Object[] BOOLEANS = new Object[]{
                    false, true,
    };

    @Test
    public void testBooleans() {
        for (Object bool : BOOLEANS) {
            assertValue(context, context.asValue(bool), BOOLEAN);
            assertValue(context, context.asValue((ProxyPrimitive) () -> bool), BOOLEAN, PROXY_OBJECT);
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
                    Proxy.newProxyInstance(ValueAPITest.class.getClassLoader(), new Class[]{ProxyInterface.class}, new InvocationHandler() {
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
                    }),};

    @Test
    public void testHostObject() {
        assertTrue(context.asValue(new EmptyObject()).getMemberKeys().isEmpty());
        assertTrue(context.asValue(new PrivateObject()).getMemberKeys().isEmpty());

        for (Object value : HOST_OBJECTS) {
            if (value instanceof List) {
                assertValue(context, context.asValue(value), MEMBERS, HOST_OBJECT, ARRAY_ELEMENTS);
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
                    new Long[]{42l, 42l, 42l},
                    new Float[]{42f, 42f, 42f},
                    new Double[]{42d, 42d, 42d},
                    new Object[]{true, 'a', "ab", (byte) 42, (short) 42, 42, 42l, 42f, 42d},
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

    public static interface JavaInterface {

    }

    @FunctionalInterface
    public static interface JavaFunctionalInterface {

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
        objectCoercionTest(ProxyObject.fromMap(map), Map.class,
                        (v) -> assertEquals("baz", v.get("foobar")));

        ProxyArray array = ProxyArray.fromArray(42, 42, 42);
        objectCoercionTest(array, List.class,
                        (v) -> assertEquals(42, v.get(2)));

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

    private static interface ProxyInterface {

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

    private static class Instantiable implements ProxyInstantiable {

        Object instantiableResult;

        public Object newInstance(Value... arguments) {
            return instantiableResult;
        }

    }

    private static class ExecutableAndInstantiable implements ProxyExecutable, ProxyInstantiable {

        Object executableResult;
        Object instantiableResult;

        public Object execute(Value... arguments) {
            return executableResult;
        }

        public Object newInstance(Value... arguments) {
            return instantiableResult;
        }

    }

}
