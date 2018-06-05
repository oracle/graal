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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

public class ValueHostInteropTest extends AbstractPolyglotTest {

    public static class Data {
        public int x;
        public double y;
        public String[] arr;
        public Object value;
        public Object map;
        public Object dataMap;
        public Data[] data;

        public double plus(double a, double b) {
            return a + b;
        }

        public Object assertThis(Object param) {
            assertSame("When a Java object is passed into Truffle and back, it is again the same object", this, param);
            return this;
        }
    }

    @Before
    public void initObjects() {
        setupEnv();
    }

    @Test
    public void testRecursiveListMarshalling() {
        List<GregorianCalendar> testList = Arrays.asList(new GregorianCalendar());
        Value testListValue = context.asValue(testList);
        assertTrue(testListValue.isHostObject());

        Value calendarValue = testListValue.getArrayElement(0);
        assertTrue(calendarValue.isHostObject());

        assertValue(context, testListValue);
        assertValue(context, calendarValue);
    }

    @Test
    public void conversionToClassNull() {
        assertSame(Void.class, context.asValue(null).getMetaObject().asHostObject());
    }

    @Test
    public void nullAsJavaObject() {
        assertNull(context.asValue(null).asHostObject());
        assertTrue(context.asValue(null).isHostObject());
    }

    @Test
    public void doubleWrap() {
        Data data = new Data();
        data.x = 32;
        data.y = 10.1;
        XYPlus xyp = context.asValue(data).as(XYPlus.class);
        assertEquals("Assume delegated", 42.1d, xyp.plus(xyp.x(), xyp.y()), 0.05);
    }

    @Test
    public void assertThisIsSame() {
        AtomicReference<Boolean> thisCalled = new AtomicReference<>(false);
        Data data = new Data() {
            @Override
            public Object assertThis(Object param) {
                thisCalled.set(true);
                return super.assertThis(param);
            }
        };
        XYPlus xyp = context.asValue(data).as(XYPlus.class);

        XYPlus anotherThis = xyp.assertThis(data);
        assertTrue(thisCalled.get());

        data.x = 44;
        assertEquals(44, anotherThis.x());
        assertEquals(anotherThis, xyp);
        assertEquals(anotherThis.hashCode(), xyp.hashCode());
    }

    @Test
    public void assertKeysAndProperties() {
        Data data = new Data();
        Value dataValue = context.asValue(data);
        assertThat(dataValue.getMemberKeys(), CoreMatchers.hasItems("x", "y", "arr", "value", "map", "dataMap", "data", "plus"));

        Method[] objectMethods = Object.class.getMethods();
        for (Method objectMethod : objectMethods) {
            assertThat("No java.lang.Object methods", dataValue.getMemberKeys(), CoreMatchers.not(CoreMatchers.hasItem(objectMethod.getName())));
        }
    }

    @Test
    public void assertKeysFromAMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("null", null);
        map.put("three", 3);

        Set<String> memberKeys = context.asValue(map).getMemberKeys();
        assertFalse(memberKeys.contains("one"));
        assertFalse(memberKeys.contains("null"));
        assertFalse(memberKeys.contains("three"));
        assertValue(context, context.asValue(map));
    }

    @Test
    public void readUnknownField() throws Exception {
        Value dataValue = context.asValue(new Data());
        assertFalse(dataValue.hasMember("unknown"));
        assertFalse(dataValue.getMemberKeys().contains("unknown"));
        assertNull(dataValue.getMember("unknown"));
    }

    static void assertThrowsExceptionWithCause(Callable<?> callable, Class<? extends Exception> exception) throws Exception {
        try {
            callable.call();
            fail("Expected " + exception.getSimpleName() + " but no exception was thrown");
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertEquals(exception, e.asHostException().getClass());
        } catch (Exception e) {
            throw e;
        }
    }

    @Test
    public void readJavaLangObjectFields() {
        assertReadMethod("notify");
        assertReadMethod("notifyAll");
        assertReadMethod("wait");
        assertReadMethod("hashCode");
        assertReadMethod("equals");
        assertReadMethod("toString");
        assertReadMethod("getClass");
    }

    private void assertReadMethod(final String name) {
        Value dataValue = context.asValue(new Data());
        Value member = dataValue.getMember(name);
        assertNotNull(member);
        assertTrue(member.canExecute());
    }

    @Test
    public void invokeJavaLangObjectFields() throws Exception {
        Value obj = context.asValue(new Data());

        String toStringValue = obj.getMember("toString").execute().asString();
        String indirectToStringValue = obj.toString();
        assertEquals(toStringValue, indirectToStringValue);
        assertTrue(toStringValue.startsWith(Data.class.getName() + "@"));

        assertSame(Data.class, obj.getMember("getClass").execute().asHostObject());
        assertTrue(obj.getMember("equals").execute(obj).asBoolean());
        assertEquals(obj.asHostObject().hashCode(), obj.getMember("hashCode").execute().asInt());

        for (String m : new String[]{"notify", "notifyAll", "wait"}) {
            assertThrowsExceptionWithCause(() -> obj.getMember(m).execute(), IllegalMonitorStateException.class);
        }
    }

    class PrivatePOJO {
        public int x;
    }

    @Test
    public void accessAllProperties() {
        Value pojo = context.asValue(new PrivatePOJO());
        Map<?, ?> map = pojo.as(Map.class);
        int cnt = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            assertNotNull(key);
            assertNotNull(value);
            cnt++;
        }
        assertEquals("No properties", 0, cnt);
        assertEquals("Empty: " + map, 0, map.size());
    }

    @Test
    public void accessAllPropertiesDirectly() {
        Value pojo = context.asValue(new PrivatePOJO());
        assertEquals("No props, class isn't public", 0, pojo.getMemberKeys().size());
    }

    public static class PublicPOJO {
        PublicPOJO() {
        }

        public int x;
        public static int y;

        public int readX() {
            return x;
        }

        void writeX(int value) {
            this.x = value;
        }

        public static int readY() {
            return y;
        }

        static void writeY(int value) {
            y = value;
        }
    }

    @Test
    public void accessAllPublicPropertiesDirectly() {
        final PublicPOJO orig = new PublicPOJO();
        final Value pojo = context.asValue(orig);
        Object[] propertyNames = pojo.getMemberKeys().toArray();
        assertEquals("One instance field and one method", 2, propertyNames.length);
        assertEquals("One field x", "x", propertyNames[0]);
        assertEquals("One method to access x", "readX", propertyNames[1]);

        Value readX = pojo.getMember("readX");
        assertTrue(readX.canExecute());

        orig.writeX(10);
        assertEquals(10, readX.execute().asInt());
    }

    @Test
    public void arrayHasSize() {
        Value arrObj = context.asValue(new String[]{"Hello", "World", "!"});
        assertTrue(arrObj.hasArrayElements());
        assertEquals("Three elements", 3L, arrObj.getArraySize());
        assertEquals("Hello", arrObj.getArrayElement(0).asString());
        assertEquals("World", arrObj.getArrayElement(1).asString());
        assertEquals("!", arrObj.getArrayElement(2).asString());
    }

    @Test
    public void emptyArrayHasSize() {
        Value arrObj = context.asValue(new String[0]);
        assertTrue(arrObj.hasArrayElements());
        assertEquals(0L, arrObj.getArraySize());
    }

    private static final TypeLiteral<List<String>> LIST_STRING = new TypeLiteral<List<String>>() {
    };

    @Test
    public void arrayAsList() {
        String[] arr = new String[]{"Hello", "World", "!"};
        Value arrObj = context.asValue(arr);
        List<String> list = arrObj.as(LIST_STRING);
        assertEquals("Three elements", 3, list.size());
        assertEquals("Hello", list.get(0));
        assertEquals("World", list.get(1));
        assertEquals("!", list.get(2));

        list.set(1, "there");

        assertEquals("there", arr[1]);
    }

    @Test
    public void objectsAsMap() {
        Data data = new Data();
        data.x = 10;
        data.y = 33.3;
        data.map = data;
        Map<String, Object> map = context.asValue(data).as(XYPlus.class).map();

        assertEquals("x", map.get("x"), 10);
        assertEquals("y", map.get("y"), 33.3);

        map.put("x", 13);
        assertEquals("x changed", data.x, 13);

        boolean foundX = false;
        boolean foundY = false;
        Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            if ("x".equals(entry.getKey())) {
                assertEquals("x value found", data.x, entry.getValue());
                foundX = true;
            }
            if ("y".equals(entry.getKey())) {
                assertEquals("y value found", data.y, entry.getValue());
                foundY = true;
            }
        }
        assertTrue(foundX);
        assertTrue(foundY);
    }

    @Test
    public void nullCanBeReturned() {
        Data data = new Data();
        XYPlus xyp = context.asValue(data).as(XYPlus.class);
        assertNull(xyp.value());
    }

    @Test
    public void integerCanBeConvertedFromAnObjectField() {
        Data data = new Data();
        XYPlus xyp = context.asValue(data).as(XYPlus.class);
        data.value = 42;
        assertEquals((Integer) 42, xyp.value());
    }

    @Test
    public void isNull() {
        Value value = context.asValue(null);
        assertTrue(value.isNull());
        assertFalse(context.asValue(new Object()).isNull());
    }

    @FunctionalInterface
    public interface FunctionalWithDefaults {
        Object call(Object... args);

        default int call(int a, int b) {
            return (int) call(new Object[]{a, b});
        }
    }

    @Test
    public void functionalInterfaceOverridingObjectMethods() throws Exception {
        Value object = context.asValue((FunctionalWithObjectMethodOverrides) (args) -> args.length >= 1 ? args[0] : null);
        assertArrayEquals(new Object[]{"call"}, object.getMemberKeys().toArray());
        assertEquals(42, object.execute(42).asInt());
    }

    @FunctionalInterface
    public interface FunctionalWithObjectMethodOverrides {
        @Override
        boolean equals(Object obj);

        @Override
        int hashCode();

        @Override
        String toString();

        Object call(Object... args);
    }

    @Test
    public void executableAsFunctionalInterface1() throws Exception {
        TruffleObject executable = new FunctionObject();
        FunctionalWithDefaults f = context.asValue(executable).as(FunctionalWithDefaults.class);
        assertEquals(50, f.call((Object) 13, (Object) 37));
        f.hashCode();
        f.equals(null);
        f.toString();
    }

    @Test
    public void executableAsFunctionalInterface2() throws Exception {
        TruffleObject executable = new FunctionObject();
        FunctionalWithObjectMethodOverrides f = context.asValue(executable).as(FunctionalWithObjectMethodOverrides.class);
        assertEquals(50, f.call(13, 37));
        f.hashCode();
        f.equals(null);
        f.toString();
    }

    @Ignore("Interface not accessible")
    @Test
    public void executableAsFunctionalInterface3() throws Exception {
        assumeTrue("JDK 9 or later", System.getProperty("java.specification.version").compareTo("1.9") >= 0);
        TruffleObject executable = new FunctionObject();
        FunctionalWithDefaults f = context.asValue(executable).as(FunctionalWithDefaults.class);
        assertEquals(42, f.call((Object) 13, (Object) 29));
        assertEquals(50, f.call(13, 37));
        f.hashCode();
        f.equals(null);
        f.toString();
    }

    @Test
    public void listUnwrapsTruffleObject() {
        Data data = new Data();
        data.data = new Data[]{new Data()};
        XYPlus xyp = context.asValue(data).as(XYPlus.class);
        Data value = xyp.data().get(0);
        assertSame(data.data[0], value);
    }

    @Test
    public void mapUnwrapsTruffleObject() {
        Data data = new Data();
        data.dataMap = data;
        XYPlus xyp = context.asValue(data).as(XYPlus.class);
        Data value = xyp.dataMap().get("dataMap");
        assertSame(data, value);

        Data newValue = new Data();
        Data previousValue = xyp.dataMap().put("dataMap", newValue);
        assertSame(data, previousValue);

        assertSame(newValue, data.dataMap);
    }

    @Test
    public void mapEntrySetUnwrapsTruffleObject() {
        Data data = new Data();
        data.dataMap = data;
        XYPlus xyp = context.asValue(data).as(XYPlus.class);
        final Map<String, Data> map = xyp.dataMap();
        Data value = map.get("dataMap");
        assertSame(data, value);

        for (Map.Entry<String, Data> entry : xyp.dataMap().entrySet()) {
            if ("dataMap".equals(entry.getKey())) {
                assertSame(value, entry.getValue());
                Data newValue = new Data();
                Data prev = entry.setValue(newValue);
                assertSame(value, prev);
                assertSame(newValue, map.get("dataMap"));
                return;
            }
        }
        fail("Entry dataMap not found");
    }

    @Test
    public void testNewClass() {
        Value hashMapClass = context.asValue(HashMap.class);
        Value hashMap = hashMapClass.newInstance();
        assertTrue(hashMap.isHostObject());
        assertTrue(hashMap.asHostObject() instanceof HashMap);
    }

    @Test
    public void testNewObject() {
        Value objectClass = context.asValue(Object.class);
        Value object = objectClass.newInstance();
        assertTrue(object.isHostObject());
    }

    @Test
    public void testNewArray() {
        Value objectClass = context.asValue(long[].class);
        Value object = objectClass.newInstance(4);
        assertTrue(object.isHostObject());
        assertTrue(object.hasArrayElements());
        assertEquals(4, object.getArraySize());
    }

    @Test
    public void testException() {
        Value iterator = context.asValue(Collections.emptyList().iterator());
        try {
            iterator.getMember("next").execute();
            fail("expected an exception but none was thrown");
        } catch (PolyglotException ex) {
            assertTrue("expected HostException but was: " + ex.getClass(), ex.isHostException());
            assertThat(ex.asHostException(), CoreMatchers.instanceOf(NoSuchElementException.class));
        }
    }

    @Test
    public void testException2() {
        Value hashMapClass = context.asValue(HashMap.class);
        try {
            hashMapClass.newInstance(-1);
            fail("expected an exception but none was thrown");
        } catch (PolyglotException ex) {
            assertTrue("expected HostException but was: " + ex.getClass(), ex.isHostException());
            assertThat(ex.asHostException(), CoreMatchers.instanceOf(IllegalArgumentException.class));
        }
        try {
            hashMapClass.newInstance("");
            fail("expected an exception but none was thrown");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testRemoveMessage() {
        Data data = new Data();
        data.arr = new String[]{"Hello", "World", "!"};
        Value truffleList = context.asValue(new ArrayList<>(Arrays.asList(data.arr)));
        assertEquals(3L, truffleList.getArraySize());
        assertEquals(true, truffleList.removeArrayElement(1));
        assertEquals(2, truffleList.getArraySize());
        try {
            truffleList.removeArrayElement(10L);
            fail("Out of bounds.");
        } catch (ArrayIndexOutOfBoundsException e) {
        }

        Value arrObj = context.asValue(data.arr);
        try {
            arrObj.removeArrayElement(0);
            fail("Remove of elements of an array is not supported.");
        } catch (UnsupportedOperationException e) {
        }

        Map<String, Object> map = new HashMap<>();
        map.put("a", "aa");
        map.put("b", "bb");
        Value truffleMap = context.asValue(ProxyObject.fromMap(map));
        assertEquals(true, truffleMap.removeMember("a"));
        assertEquals(1, map.size());
        assertFalse(truffleMap.removeMember("a"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRemoveList() {
        List<Integer> list = context.asValue(new ArrayTruffleObject(100)).as(List.class);
        assertEquals(100, list.size());
        Integer value = list.remove(10);
        assertEquals(Integer.valueOf(90), value);
        assertEquals(99, list.size());
        boolean success = list.remove((Object) 20);
        assertTrue(success);
        assertEquals(98, list.size());
        // Iterator
        Iterator<Integer> liter = list.iterator();
        try {
            liter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        assertEquals(Integer.valueOf(98), liter.next());
        assertEquals(Integer.valueOf(97), liter.next());
        liter.remove();
        assertEquals(97, list.size());
        try {
            liter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        assertEquals(Integer.valueOf(96), liter.next());
        liter.remove();
        assertEquals(96, list.size());

        List<String> arr = context.asValue(new String[]{"Hello", "World", "!"}).as(LIST_STRING);
        try {
            assertEquals("World", arr.remove(1));
            fail("Remove of elements of an array is not supported.");
        } catch (UnsupportedOperationException e) {
            // O.K.
        }
    }

    private static final TypeLiteral<Map<String, String>> MAP_STRING_STRING = new TypeLiteral<Map<String, String>>() {
    };

    @SuppressWarnings("unchecked")
    @Test
    public void testRemoveMap() {
        int size = 15;
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            char c = (char) ('a' + i);
            map.put(new String(new char[]{c}), new String(new char[]{c, c}));
        }
        Map<String, String> jmap = context.asValue(new RemoveKeysObject(map)).as(MAP_STRING_STRING);
        assertEquals(size, jmap.size());
        String value = jmap.remove("a");
        assertEquals("aa", value);
        assertEquals(size - 1, jmap.size());
        boolean success = jmap.remove("b", "c");
        assertFalse(success);
        assertEquals(size - 1, jmap.size());
        success = jmap.remove("b", "bb");
        assertTrue(success);
        assertEquals(size - 2, jmap.size());
        // Set
        Set<String> keySet = jmap.keySet();
        success = keySet.remove("c");
        assertTrue(success);
        assertEquals(size - 3, jmap.size());
        success = keySet.remove("xx");
        assertFalse(success);
        assertEquals(size - 3, jmap.size());
        assertEquals(size - 3, keySet.size());
        // Set Iterator
        Iterator<String> siter = keySet.iterator();
        try {
            siter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        assertEquals("d", siter.next());
        siter.remove();
        assertEquals(size - 4, jmap.size());
        try {
            siter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        assertEquals("e", siter.next());
        siter.remove();
        assertEquals(size - 5, jmap.size());
        // Entry Set
        Set<Map.Entry<String, String>> entrySet = jmap.entrySet();
        success = entrySet.remove(new AbstractMap.SimpleEntry<>("f", "ff"));
        assertTrue(success);
        assertEquals(size - 6, jmap.size());
        success = entrySet.remove(new AbstractMap.SimpleEntry<>("g", "xx"));
        assertFalse(success);
        success = entrySet.remove(new AbstractMap.SimpleEntry<>("xx", "gg"));
        assertFalse(success);
        assertEquals(size - 6, jmap.size());
        success = entrySet.remove(new AbstractMap.SimpleEntry<>("g", "gg"));
        assertTrue(success);
        assertEquals(size - 7, jmap.size());
        assertEquals(size - 7, entrySet.size());
        // Entry Set Iterator
        Iterator<Map.Entry<String, String>> esiter = entrySet.iterator();
        try {
            esiter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        Map.Entry<String, String> nextEntry = esiter.next();
        assertEquals("h", nextEntry.getKey());
        assertEquals("hh", nextEntry.getValue());
        esiter.remove();
        assertEquals(size - 8, jmap.size());
        // Values
        Collection<String> values = jmap.values();
        success = values.remove("ii");
        assertTrue(success);
        assertEquals(size - 9, jmap.size());
        success = values.remove("xxx");
        assertFalse(success);
        assertEquals(size - 9, jmap.size());
        // Values Iterator
        Iterator<String> viter = values.iterator();
        try {
            viter.remove();
            fail("IllegalStateException");
        } catch (IllegalStateException e) {
            // O.K.
        }
        assertEquals("jj", viter.next());
        viter.remove();
        assertEquals(size - 10, jmap.size());
        assertEquals(size - 10, values.size());

        Data data = new Data();
        data.map = data;
        Map<String, Object> dmap = context.asValue(data).as(Map.class);
        try {
            dmap.remove("x");
            fail("Remove of object fields is not supported.");
        } catch (UnsupportedOperationException e) {
            // O.K.
        }
    }

    public interface XYPlus {
        List<String> arr();

        Map<String, Object> map();

        Map<String, Data> dataMap();

        int x();

        double y();

        double plus(double a, double b);

        Integer value();

        XYPlus assertThis(Object obj);

        List<Data> data();
    }

    static class ListArray extends ProxyInteropObject {

        private final List<String> array;

        ListArray(List<String> array) {
            this.array = array;
        }

        @Override
        public int keyInfo(Number key) {
            if (key.intValue() < array.size() && key.intValue() >= 0) {
                return KeyInfo.READABLE;
            }
            return super.keyInfo(key);
        }

        @Override
        public Object read(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
            return array.get(key.intValue());
        }

        @Override
        public boolean remove(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
            array.remove(key.intValue());
            return true;
        }

        @Override
        public boolean hasSize() {
            return true;
        }

        @Override
        public int getSize() {
            return array.size();
        }

    }

    static final class RemoveKeysObject implements TruffleObject {

        private final Map<String, ?> keys;

        RemoveKeysObject(Map<String, ?> keys) {
            this.keys = keys;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return RemoveKeysObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof RemoveKeysObject;
        }

        @MessageResolution(receiverType = RemoveKeysObject.class)
        static final class RemoveKeysObjectMessageResolution {

            @Resolve(message = "KEYS")
            public abstract static class PropertiesKeysOnlyNode extends Node {

                public Object access(RemoveKeysObject receiver) {
                    List<String> list = new AbstractList<String>() {
                        final Set<String> keys = receiver.keys.keySet();

                        @Override
                        public String get(int index) {
                            Iterator<String> iterator = keys.iterator();
                            for (int i = 0; i < index; i++) {
                                iterator.next();
                            }
                            return iterator.next();
                        }

                        @Override
                        public int size() {
                            return keys.size();
                        }

                        @Override
                        public String remove(int index) {
                            Iterator<String> iterator = keys.iterator();
                            for (int i = 0; i < index; i++) {
                                iterator.next();
                            }
                            String removed = iterator.next();
                            iterator.remove();
                            return removed;
                        }
                    };
                    return new ListArray(list);
                }
            }

            @Resolve(message = "READ")
            public abstract static class ReadKeyNode extends Node {

                public Object access(RemoveKeysObject receiver, String name) {
                    if (!receiver.keys.containsKey(name)) {
                        CompilerDirectives.transferToInterpreter();
                        throw UnknownIdentifierException.raise(name);
                    }
                    return receiver.keys.get(name);
                }
            }

            @Resolve(message = "REMOVE")
            public abstract static class RemoveKeyNode extends Node {

                public Object access(RemoveKeysObject receiver, String name) {
                    if (!receiver.keys.containsKey(name)) {
                        return false;
                    }
                    receiver.keys.remove(name);
                    return true;
                }
            }
        }
    }

    static final class ArrayTruffleObject implements TruffleObject {

        private int size;

        ArrayTruffleObject(int size) {
            this.size = size;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return ArrayTruffleObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof ArrayTruffleObject;
        }

        @MessageResolution(receiverType = ArrayTruffleObject.class)
        static final class ArrayTruffleObjectMessageResolution {

            @Resolve(message = "HAS_SIZE")
            public abstract static class ArrayHasSizeNode extends Node {

                public Object access(ArrayTruffleObject receiver) {
                    assert receiver != null;
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            public abstract static class ArrayGetSizeNode extends Node {

                public Object access(ArrayTruffleObject receiver) {
                    return receiver.size;
                }
            }

            @Resolve(message = "READ")
            public abstract static class ArrayReadSizeNode extends Node {

                public Object access(ArrayTruffleObject receiver, int index) {
                    if (index < 0 || index >= receiver.size) {
                        throw new ArrayIndexOutOfBoundsException(index);
                    }
                    return receiver.size - index;
                }
            }

            @Resolve(message = "REMOVE")
            public abstract static class ArrayRemoveNode extends Node {

                public Object access(ArrayTruffleObject receiver, int index) {
                    if (index < 0 || index >= receiver.size) {
                        throw new ArrayIndexOutOfBoundsException(index);
                    }
                    receiver.size--;
                    return true;
                }
            }
        }
    }

    @MessageResolution(receiverType = FunctionObject.class)
    static final class FunctionObject implements TruffleObject {
        @Resolve(message = "IS_EXECUTABLE")
        abstract static class IsExecutable extends Node {
            @SuppressWarnings("unused")
            protected Object access(FunctionObject obj) {
                return true;
            }
        }

        @Resolve(message = "EXECUTE")
        @SuppressWarnings("unused")
        abstract static class Execute extends Node {
            protected Object access(FunctionObject obj, Object[] args) {
                return Arrays.stream(args).mapToInt(o -> (int) o).sum();
            }
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof FunctionObject;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return FunctionObjectForeign.ACCESS;
        }
    }
}
