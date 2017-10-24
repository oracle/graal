/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.interop.java.MethodMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;

public class JavaInteropTest {
    public class Data {
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
            assertThisCalled = true;
            return this;
        }
    }

    private TruffleObject obj;
    private Data data;
    private XYPlus xyp;
    private boolean assertThisCalled;

    @Before
    public void initObjects() {
        data = new Data();
        obj = JavaInterop.asTruffleObject(data);
        xyp = JavaInterop.asJavaObject(XYPlus.class, obj);
    }

    @Test
    public void conversionToClassYieldsTheClass() {
        TruffleObject expected = JavaInterop.asTruffleObject(Data.class);
        TruffleObject computed = JavaInterop.toJavaClass(obj);
        assertEquals("Both class objects are the same", expected, computed);
    }

    @Test
    public void conversionToClass2() {
        TruffleObject expected = JavaInterop.asTruffleObject(Class.class);
        TruffleObject computed = JavaInterop.toJavaClass(JavaInterop.asTruffleObject(Data.class));
        assertEquals("Both class objects are the same", expected, computed);
    }

    @Test
    public void conversionToClassNull() {
        TruffleObject expected = JavaInterop.asTruffleObject(null);
        TruffleObject computed = JavaInterop.toJavaClass(expected);
        assertEquals(expected, computed);
    }

    @Test
    public void doubleWrap() {
        data.x = 32;
        data.y = 10.1;
        assertEquals("Assume delegated", 42.1d, xyp.plus(xyp.x(), xyp.y()), 0.05);
    }

    @Test
    public void writeX() {
        xyp.x(10);
        assertEquals("Changed", 10, data.x);
    }

    @Test
    public void assertThisIsSame() {
        assertThisCalled = false;
        XYPlus anotherThis = xyp.assertThis(data);
        assertTrue(assertThisCalled);

        data.x = 44;
        assertEquals(44, anotherThis.x());

        assertEquals("The two proxies are equal", anotherThis, xyp);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void assertKeysAndProperties() {
        TruffleObject keys = sendKeys(obj);
        List<Object> list = JavaInterop.asJavaObject(List.class, keys);
        assertThat(list, CoreMatchers.hasItems("x", "y", "arr", "value", "map", "dataMap", "data", "plus"));

        Method[] objectMethods = Object.class.getMethods();
        for (Method objectMethod : objectMethods) {
            assertThat("No java.lang.Object methods", list, CoreMatchers.not(CoreMatchers.hasItem(objectMethod.getName())));
        }

        keys = sendKeys(obj, true);
        list = JavaInterop.asJavaObject(List.class, keys);
        for (Method objectMethod : objectMethods) {
            assertThat("java.lang.Object methods", list, CoreMatchers.hasItem(objectMethod.getName()));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void assertKeysFromAMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("null", null);
        map.put("three", 3);

        TruffleObject truffleMap = JavaInterop.asTruffleObject(map);
        TruffleObject ret = sendKeys(truffleMap);
        List<Object> list = JavaInterop.asJavaObject(List.class, ret);
        assertThat(list, CoreMatchers.hasItems("one", "null", "three"));
    }

    @Test
    public void readUnknownField() throws Exception {
        assertNoRead("unknown");
    }

    private void assertReadMethod(final String name) throws UnsupportedMessageException, UnknownIdentifierException {
        Object method = ForeignAccess.sendRead(Message.READ.createNode(), obj, name);
        assertTrue("Expected executable", method instanceof TruffleObject && ForeignAccess.sendIsExecutable(Message.IS_EXECUTABLE.createNode(), (TruffleObject) method));
    }

    private void assertNoRead(final String name) throws UnsupportedMessageException {
        try {
            ForeignAccess.sendRead(Message.READ.createNode(), obj, name);
            fail("Expected exception when reading field: " + name);
        } catch (UnknownIdentifierException ex) {
            assertEquals(name, ex.getUnknownIdentifier());
        }
    }

    static void assertThrowsExceptionWithCause(Callable<?> callable, Class<? extends Exception> exception) {
        try {
            callable.call();
            fail("Expected " + exception.getSimpleName() + " but no exception was thrown");
        } catch (Exception e) {
            List<Class<? extends Throwable>> causes = new ArrayList<>();
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause.getClass() == exception) {
                    return;
                }
                causes.add(cause.getClass());
            }
            fail("Expected " + exception.getSimpleName() + ", got " + causes);
        }
    }

    @Test
    public void readJavaLangObjectFields() throws InteropException {
        assertReadMethod("notify");
        assertReadMethod("notifyAll");
        assertReadMethod("wait");
        assertReadMethod("hashCode");
        assertReadMethod("equals");
        assertReadMethod("toString");
        assertReadMethod("getClass");
    }

    @Test
    public void invokeJavaLangObjectFields() throws InteropException {
        Object string = ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), obj, "toString");
        assertTrue(string instanceof String && ((String) string).startsWith(Data.class.getName() + "@"));
        Object clazz = ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), obj, "getClass");
        assertTrue(clazz instanceof TruffleObject && JavaInterop.asJavaObject((TruffleObject) clazz) == Data.class);
        assertEquals(true, ForeignAccess.sendInvoke(Message.createInvoke(1).createNode(), obj, "equals", obj));
        assertTrue(ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), obj, "hashCode") instanceof Integer);

        for (String m : new String[]{"notify", "notifyAll", "wait"}) {
            assertThrowsExceptionWithCause(() -> ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), obj, m), IllegalMonitorStateException.class);
        }
    }

    static TruffleObject sendKeys(TruffleObject receiver) {
        return sendKeys(receiver, false);
    }

    static TruffleObject sendKeys(TruffleObject receiver, boolean includeInternal) {
        final Node keysNode = Message.KEYS.createNode();
        try {
            return ForeignAccess.sendKeys(keysNode, receiver, includeInternal);
        } catch (InteropException ex) {
            throw ex.raise();
        }
    }

    class PrivatePOJO {
        public int x;
    }

    @Test
    public void accessAllProperties() {
        TruffleObject pojo = JavaInterop.asTruffleObject(new PrivatePOJO());
        Map<?, ?> map = JavaInterop.asJavaObject(Map.class, pojo);
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
        TruffleObject pojo = JavaInterop.asTruffleObject(new PrivatePOJO());
        TruffleObject result = sendKeys(pojo);
        List<?> propertyNames = JavaInterop.asJavaObject(List.class, result);
        assertEquals("No props, class isn't public", 0, propertyNames.size());
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
        final TruffleObject pojo = JavaInterop.asTruffleObject(orig);
        TruffleObject result = sendKeys(pojo);
        List<?> propertyNames = JavaInterop.asJavaObject(List.class, result);
        assertEquals("One instance field and one method", 2, propertyNames.size());
        assertEquals("One field x", "x", propertyNames.get(0));
        assertEquals("One method to access x", "readX", propertyNames.get(1));

        TruffleObject readX = (TruffleObject) message(Message.READ, pojo, "readX");
        Boolean isExecutable = (Boolean) message(Message.IS_EXECUTABLE, readX);
        assertTrue("Method can be executed " + readX, isExecutable);

        orig.writeX(10);
        final Object value = message(Message.createExecute(0), readX);
        assertEquals(10, value);
    }

    @Test
    public void noNonStaticPropertiesForAClass() {
        TruffleObject pojo = JavaInterop.asTruffleObject(PublicPOJO.class);
        TruffleObject result = sendKeys(pojo);
        List<?> propertyNames = JavaInterop.asJavaObject(List.class, result);
        assertEquals("One static field and one method", 2, propertyNames.size());
        assertEquals("One field y", "y", propertyNames.get(0));
        assertEquals("One method to read y", "readY", propertyNames.get(1));
    }

    @Test
    public void javaObjectsWrappedForTruffle() {
        Object ret = message(Message.createInvoke(1), obj, "assertThis", obj);
        assertTrue("Expecting truffle wrapper: " + ret, ret instanceof TruffleObject);
        assertEquals("Same as this obj", ret, obj);
    }

    @Test
    public void arrayHasSize() {
        data.arr = new String[]{"Hello", "World", "!"};
        Object arrObj = message(Message.READ, obj, "arr");
        assertTrue("It's obj: " + arrObj, arrObj instanceof TruffleObject);
        TruffleObject truffleArr = (TruffleObject) arrObj;
        assertEquals("It has size", Boolean.TRUE, message(Message.HAS_SIZE, truffleArr));
        assertEquals("Three elements", 3, message(Message.GET_SIZE, truffleArr));
        assertEquals("Hello", message(Message.READ, truffleArr, 0));
        assertEquals("World", message(Message.READ, truffleArr, 1));
        assertEquals("!", message(Message.READ, truffleArr, 2));
    }

    @Test
    public void emptyArrayHasSize() {
        data.arr = new String[]{};
        Object arrObj = message(Message.READ, obj, "arr");
        assertTrue("It's obj: " + arrObj, arrObj instanceof TruffleObject);
        TruffleObject truffleArr = (TruffleObject) arrObj;
        assertEquals("It has size", Boolean.TRUE, message(Message.HAS_SIZE, truffleArr));
        assertEquals("Zero elements", 0, message(Message.GET_SIZE, truffleArr));
    }

    @Test
    public void arrayAsList() {
        data.arr = new String[]{"Hello", "World", "!"};
        List<String> list = xyp.arr();
        assertEquals("Three elements", 3, list.size());
        assertEquals("Hello", list.get(0));
        assertEquals("World", list.get(1));
        assertEquals("!", list.get(2));

        list.set(1, "there");

        assertEquals("there", data.arr[1]);
    }

    @Test
    public void objectsAsMap() {
        data.x = 10;
        data.y = 33.3;
        data.map = data;
        Map<String, Object> map = xyp.map();

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
        assertNull(xyp.value());
    }

    @Test
    public void integerCanBeConvertedFromAnObjectField() {
        data.value = 42;
        assertEquals((Integer) 42, xyp.value());
    }

    @Test
    public void indexJavaArrayWithNumberTypes() throws Exception {
        int[] a = new int[]{1, 2, 3};
        TruffleObject truffleObject = JavaInterop.asTruffleObject(a);

        assertEquals(2, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1));
        assertEquals(2, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1.0));
        assertEquals(2, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1L));

        ForeignAccess.sendWrite(Message.WRITE.createNode(), truffleObject, 1, 42);
        ForeignAccess.sendWrite(Message.WRITE.createNode(), truffleObject, 1.0, 42);
        ForeignAccess.sendWrite(Message.WRITE.createNode(), truffleObject, 1L, 42);

        assertEquals(42, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1));
        assertEquals(42, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1.0));
        assertEquals(42, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1L));

    }

    @Test
    public void isPrimitive() {
        assertFalse(JavaInterop.isPrimitive(null));
        assertFalse(JavaInterop.isPrimitive(new Object()));
        assertFalse(JavaInterop.isPrimitive(this));
        assertTrue(JavaInterop.isPrimitive(42));
        assertTrue(JavaInterop.isPrimitive((byte) 42));
        assertTrue(JavaInterop.isPrimitive((short) 42));
        assertTrue(JavaInterop.isPrimitive(424242424242L));
        assertTrue(JavaInterop.isPrimitive(42.42f));
        assertTrue(JavaInterop.isPrimitive(42e42));
        assertTrue(JavaInterop.isPrimitive("42"));
        assertTrue(JavaInterop.isPrimitive('4'));
        assertTrue(JavaInterop.isPrimitive(true));
        assertTrue(JavaInterop.isPrimitive(false));
    }

    @Test
    public void isJavaObject() {
        // obj == JavaInterop.asJavaObject(new Data())
        assertFalse(JavaInterop.isJavaObject(XYPlus.class, obj));
        assertTrue(JavaInterop.isJavaObject(Data.class, obj));
        assertTrue(JavaInterop.isJavaObject(Object.class, obj));
        // assert that asJavaObject unwraps the object if isJavaObject returns true
        assertTrue(JavaInterop.asJavaObject(Data.class, obj) == data);
        assertTrue(JavaInterop.asJavaObject(Object.class, obj) == data);
    }

    @Test
    public void truffleValue() {
        Object object = new Object();
        // Test that asTruffleValue() returns the same as asTruffleObject() for non-primitive types:
        assertEquals(JavaInterop.asTruffleObject(object), JavaInterop.asTruffleValue(object));
        assertEquals(this.obj, JavaInterop.asTruffleValue(this.data));
        // Test that asTruffleValue() returns non-wraped primitives:
        object = 42;
        assertTrue(JavaInterop.asTruffleValue(object) == object);
        object = (byte) 42;
        assertTrue(JavaInterop.asTruffleValue(object) == object);
        object = (short) 42;
        assertTrue(JavaInterop.asTruffleValue(object) == object);
        object = 424242424242L;
        assertTrue(JavaInterop.asTruffleValue(object) == object);
        object = 42.42;
        assertTrue(JavaInterop.asTruffleValue(object) == object);
        object = true;
        assertTrue(JavaInterop.asTruffleValue(object) == object);
        object = "42";
        assertTrue(JavaInterop.asTruffleValue(object) == object);
        object = '4';
        assertTrue(JavaInterop.asTruffleValue(object) == object);
        object = true;
        assertTrue(JavaInterop.asTruffleValue(object) == object);
    }

    @Test
    public void isNull() {
        assertTrue(isNull(null));
        assertFalse(isNull(JavaInterop.asTruffleObject(new Object())));
        assertTrue(isNull(JavaInterop.asTruffleObject(null)));
    }

    @Test
    public void isArray() {
        assertFalse(isArray(null));
        assertFalse(isNull(JavaInterop.asTruffleObject(new Object())));
        int[] a = new int[]{1, 2, 3};
        TruffleObject truffleArray = JavaInterop.asTruffleObject(a);
        assertTrue(isArray(truffleArray));
    }

    @Test
    public void truffleObjectIsntFunctionalInterface() throws Exception {
        final boolean is = isJavaFunctionalInterface(TruffleObject.class);
        assertFalse("TruffleObject isn't functional interface", is);
    }

    private static boolean isJavaFunctionalInterface(final Class<?> clazz) throws Exception {
        Method isFunctionaInterface = JavaInterop.class.getDeclaredMethod("isJavaFunctionInterface", Class.class);
        ReflectionUtils.setAccessible(isFunctionaInterface, true);
        return (boolean) isFunctionaInterface.invoke(null, clazz);
    }

    @Test
    public void functionalInterfaceWithDefaultMethods() throws Exception {
        assertTrue("yes, it is", isJavaFunctionalInterface(FunctionalWithDefaults.class));
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
        assertTrue("yes, it is", isJavaFunctionalInterface(FunctionalWithObjectMethodOverrides.class));
        TruffleObject object = JavaInterop.asTruffleObject((FunctionalWithObjectMethodOverrides) (args) -> args.length >= 1 ? args[0] : null);
        TruffleObject keysObject = ForeignAccess.sendKeys(Message.KEYS.createNode(), object);
        List<?> keyList = JavaInterop.asJavaObject(List.class, keysObject);
        assertArrayEquals(new Object[]{"call"}, keyList.toArray());
        assertEquals(42, ForeignAccess.sendExecute(Message.createExecute(1).createNode(), object, 42));
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
    public void listUnwrapsTruffleObject() {
        data.data = new Data[]{new Data()};
        Data value = xyp.data().get(0);
        assertSame(data.data[0], value);
    }

    @Test
    public void mapUnwrapsTruffleObject() {
        data.dataMap = data;
        Data value = xyp.dataMap().get("dataMap");
        assertSame(data, value);

        Data newValue = new Data();
        Data previousValue = xyp.dataMap().put("dataMap", newValue);
        assertSame(data, previousValue);

        assertSame(newValue, data.dataMap);
    }

    @Test
    public void mapEntrySetUnwrapsTruffleObject() {
        data.dataMap = data;
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
    public void isBoxed() {
        assertFalse(isBoxed(null));
        assertFalse(isBoxed(JavaInterop.asTruffleObject(new Object())));
        assertTrue(isBoxed(JavaInterop.asTruffleObject(42)));
        assertTrue(isBoxed(JavaInterop.asTruffleObject((byte) 0x42)));
        assertTrue(isBoxed(JavaInterop.asTruffleObject((short) 42)));
        assertTrue(isBoxed(JavaInterop.asTruffleObject(4242424242424242L)));
        assertTrue(isBoxed(JavaInterop.asTruffleObject(42.42f)));
        assertTrue(isBoxed(JavaInterop.asTruffleObject(42.42)));
        assertTrue(isBoxed(JavaInterop.asTruffleObject("42")));
        assertTrue(isBoxed(JavaInterop.asTruffleObject('4')));
        assertTrue(isBoxed(JavaInterop.asTruffleObject(true)));
        assertTrue(isBoxed(JavaInterop.asTruffleObject(false)));
    }

    @Test
    public void unbox() {
        assertNull(unbox(null));
        assertNull(unbox(JavaInterop.asTruffleObject(new Object())));
        assertEquals(42, unbox(JavaInterop.asTruffleObject(42)));
        assertEquals((byte) 42, unbox(JavaInterop.asTruffleObject((byte) 42)));
        assertEquals((short) 42, unbox(JavaInterop.asTruffleObject((short) 42)));
        assertEquals(4242424242424242L, unbox(JavaInterop.asTruffleObject(4242424242424242L)));
        assertEquals(42.42f, unbox(JavaInterop.asTruffleObject(42.42f)));
        assertEquals(42.42, unbox(JavaInterop.asTruffleObject(42.42)));
        assertEquals("42", unbox(JavaInterop.asTruffleObject("42")));
        assertEquals('4', unbox(JavaInterop.asTruffleObject('4')));
        assertEquals(true, unbox(JavaInterop.asTruffleObject(true)));
        assertEquals(false, unbox(JavaInterop.asTruffleObject(false)));
    }

    @Test
    public void notUnboxable() {
        Node unboxNode = Message.UNBOX.createNode();
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendUnbox(unboxNode, JavaInterop.asTruffleObject(null)), UnsupportedMessageException.class);
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendUnbox(unboxNode, JavaInterop.asTruffleObject(new Object())), UnsupportedMessageException.class);
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendUnbox(unboxNode, JavaInterop.asTruffleObject(Object.class)), UnsupportedMessageException.class);
    }

    @Test
    public void keyInfoDefaults() {
        TruffleObject noKeys = new NoKeysObject();
        int keyInfo = getKeyInfo(noKeys, "p1");
        assertEquals(0, keyInfo);

        TruffleObject nkio = new NoKeyInfoObject();
        keyInfo = getKeyInfo(nkio, "p1");
        assertEquals(0b111, keyInfo);
        keyInfo = getKeyInfo(nkio, "p6");
        assertEquals(0b111, keyInfo);
        keyInfo = getKeyInfo(nkio, "p7");
        assertEquals(0, keyInfo);
    }

    @Test
    public void keyInfo() {
        TruffleObject ipobj = new InternalPropertiesObject(-1, -1, 0, 0);
        int keyInfo = getKeyInfo(ipobj, "p1");
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        assertFalse(KeyInfo.isInternal(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p6");
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        assertFalse(KeyInfo.isInternal(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p7");
        assertEquals(0, keyInfo);

        ipobj = new InternalPropertiesObject(0b0100010, 0b0100100, 0b0011000, 0);
        keyInfo = getKeyInfo(ipobj, "p1");
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p2");
        assertFalse(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p3");
        assertFalse(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertTrue(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p4");
        assertFalse(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertTrue(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p5");
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p6");
        assertFalse(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p7");
        assertEquals(0, keyInfo);

        TruffleObject aobj = new ArrayTruffleObject(100);
        testArrayObject(aobj, 100);
        aobj = JavaInterop.asTruffleObject(new String[]{"A", "B", "C", "D"});
        testArrayObject(aobj, 4);
    }

    private static void testArrayObject(TruffleObject array, int length) {
        int keyInfo;
        for (int i = 0; i < length; i++) {
            keyInfo = getKeyInfo(array, i);
            assertTrue(KeyInfo.isReadable(keyInfo));
            assertTrue(KeyInfo.isWritable(keyInfo));
            assertFalse(KeyInfo.isInvocable(keyInfo));
            assertFalse(KeyInfo.isInternal(keyInfo));
            keyInfo = getKeyInfo(array, (long) i);
            assertTrue(KeyInfo.isReadable(keyInfo));
            keyInfo = getKeyInfo(array, (double) i);
            assertTrue(KeyInfo.isReadable(keyInfo));
        }
        assertEquals(0, getKeyInfo(array, length));
        assertEquals(0, getKeyInfo(array, 1.12));
        assertEquals(0, getKeyInfo(array, -1));
        assertEquals(0, getKeyInfo(array, 10L * length));
        assertEquals(0, getKeyInfo(array, Double.NEGATIVE_INFINITY));
        assertEquals(0, getKeyInfo(array, Double.NaN));
        assertEquals(0, getKeyInfo(array, Double.POSITIVE_INFINITY));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void internalKeys() {
        // All non-internal
        InternalPropertiesObject ipobj = new InternalPropertiesObject(0);
        Map map = JavaInterop.asJavaObject(Map.class, ipobj);
        checkInternalKeys(map, "[p1, p2, p3, p4, p5, p6]");
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        // All internal
        ipobj = new InternalPropertiesObject(-1);
        map = JavaInterop.asJavaObject(Map.class, ipobj);
        checkInternalKeys(map, "[]");
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        // Combinations:
        ipobj = new InternalPropertiesObject(0b1101000);
        map = JavaInterop.asJavaObject(Map.class, ipobj);
        checkInternalKeys(map, "[p1, p2, p4]");
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p2")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p3")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p4")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p5")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        ipobj = new InternalPropertiesObject(0b1001110);
        map = JavaInterop.asJavaObject(Map.class, ipobj);
        checkInternalKeys(map, "[p4, p5]");
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p3")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p4")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p5")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        ipobj = new InternalPropertiesObject(0b0101010);
        map = JavaInterop.asJavaObject(Map.class, ipobj);
        checkInternalKeys(map, "[p2, p4, p6]");
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p2")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p3")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p4")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p5")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
    }

    @Test
    public void keyInfoJavaObject() {
        TruffleObject d = JavaInterop.asTruffleObject(new TestJavaObject());
        int keyInfo = getKeyInfo(d, "nnoonnee");
        assertFalse(KeyInfo.isExisting(keyInfo));
        keyInfo = getKeyInfo(d, "aField");
        assertTrue(KeyInfo.isExisting(keyInfo));
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(d, "toString");
        assertTrue(KeyInfo.isExisting(keyInfo));
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertTrue(KeyInfo.isInvocable(keyInfo));
    }

    @Test
    public void testSystemMethod() throws InteropException {
        TruffleObject system = JavaInterop.asTruffleObject(System.class);
        Object value = ForeignAccess.sendInvoke(Message.createInvoke(1).createNode(), system, "getProperty", "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));

        Object getProperty = ForeignAccess.sendRead(Message.READ.createNode(), system, "getProperty");
        assertThat(getProperty, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue("IS_EXECUTABLE", ForeignAccess.sendIsExecutable(Message.IS_EXECUTABLE.createNode(), (TruffleObject) getProperty));
        value = ForeignAccess.sendExecute(Message.createExecute(1).createNode(), (TruffleObject) getProperty, "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));
    }

    @Test
    public void testExecuteClass() {
        TruffleObject hashMapClass = JavaInterop.asTruffleObject(HashMap.class);
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendExecute(Message.createExecute(0).createNode(), hashMapClass), UnsupportedMessageException.class);
        assertFalse("IS_EXECUTABLE", ForeignAccess.sendIsExecutable(Message.IS_EXECUTABLE.createNode(), hashMapClass));
    }

    @Test
    public void testNewClass() throws InteropException {
        TruffleObject hashMapClass = JavaInterop.asTruffleObject(HashMap.class);
        Object hashMap = ForeignAccess.sendNew(Message.createNew(0).createNode(), hashMapClass);
        assertThat(hashMap, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue(JavaInterop.isJavaObject(HashMap.class, (TruffleObject) hashMap));
    }

    @Test
    public void testNewObject() throws InteropException {
        TruffleObject objectClass = JavaInterop.asTruffleObject(Object.class);
        Object object = ForeignAccess.sendNew(Message.createNew(0).createNode(), objectClass);
        assertThat(object, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue(JavaInterop.isJavaObject(Object.class, (TruffleObject) object));
    }

    @Test
    public void testNewArray() throws InteropException {
        TruffleObject longArrayClass = JavaInterop.asTruffleObject(long[].class);
        Object longArray = ForeignAccess.sendNew(Message.createNew(1).createNode(), longArrayClass, 4);
        assertThat(longArray, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue(JavaInterop.isJavaObject(long[].class, (TruffleObject) longArray));
        assertEquals(4, message(Message.GET_SIZE, (TruffleObject) longArray));
    }

    public static final class TestJavaObject {
        public int aField = 10;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void checkInternalKeys(Map map, String nonInternalKeys) {
        Map mapWithInternalKeys = JavaInterop.getMapView(map, true);
        Map mapWithoutInternalKeys = JavaInterop.getMapView(map, false);
        assertEquals(nonInternalKeys, map.keySet().toString());
        assertEquals(nonInternalKeys, mapWithoutInternalKeys.keySet().toString());
        assertEquals("[p1, p2, p3, p4, p5, p6]", mapWithInternalKeys.keySet().toString());
    }

    public interface XYPlus {
        List<String> arr();

        Map<String, Object> map();

        Map<String, Data> dataMap();

        int x();

        @MethodMessage(message = "WRITE")
        void x(int v);

        double y();

        double plus(double a, double b);

        Integer value();

        XYPlus assertThis(Object obj);

        List<Data> data();
    }

    static Object message(final Message m, TruffleObject receiver, Object... arr) {
        Node n = m.createNode();
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(n, receiver));
        return callTarget.call(arr);
    }

    static int getKeyInfo(TruffleObject foreignObject, Object propertyName) {
        return ForeignAccess.sendKeyInfo(Message.KEY_INFO.createNode(), foreignObject, propertyName);
    }

    static boolean isArray(TruffleObject foreignObject) {
        if (foreignObject == null) {
            return false;
        }
        return ForeignAccess.sendHasSize(Message.HAS_SIZE.createNode(), foreignObject);
    }

    static boolean isNull(TruffleObject foreignObject) {
        if (foreignObject == null) {
            return true;
        }
        return ForeignAccess.sendIsNull(Message.IS_NULL.createNode(), foreignObject);
    }

    static boolean isBoxed(TruffleObject foreignObject) {
        if (foreignObject == null) {
            return false;
        }
        return ForeignAccess.sendIsBoxed(Message.IS_BOXED.createNode(), foreignObject);
    }

    static Object unbox(TruffleObject foreignObject) {
        if (foreignObject == null) {
            return null;
        }
        try {
            return ForeignAccess.sendUnbox(Message.UNBOX.createNode(), foreignObject);
        } catch (UnsupportedMessageException e) {
            return null;
        }
    }

    private static class TemporaryRoot extends RootNode {
        @Child private Node foreignAccess;
        private final TruffleObject function;

        TemporaryRoot(Node foreignAccess, TruffleObject function) {
            super(null);
            this.foreignAccess = foreignAccess;
            this.function = function;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return ForeignAccess.send(foreignAccess, function, frame.getArguments());
            } catch (InteropException e) {
                throw e.raise();
            }
        }
    }

    static final class NoKeysObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return NoKeysObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof NoKeysObject;
        }

        @MessageResolution(receiverType = NoKeysObject.class)
        static final class NoKeysObjectMessageResolution {
            // no messages defined, defaults only
        }
    }

    static final class NoKeyInfoObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return NoKeyInfoObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof NoKeyInfoObject;
        }

        @MessageResolution(receiverType = NoKeyInfoObject.class)
        static final class NoKeyInfoObjectMessageResolution {
            // KEYS defined only, using default KEY_INFO
            @Resolve(message = "KEYS")
            public abstract static class PropertiesKeysOnlyNode extends Node {

                public Object access(NoKeyInfoObject receiver) {
                    assert receiver != null;
                    return JavaInterop.asTruffleObject(new String[]{"p1", "p2", "p3", "p4", "p5", "p6"});
                }
            }
        }
    }

    static final class ArrayTruffleObject implements TruffleObject {

        private final int size;

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
        }
    }

    static final class InternalPropertiesObject implements TruffleObject {

        private final int rBits;    // readable
        private final int wBits;    // writable
        private final int iBits;    // invocable
        private final int nBits;    // internal

        /**
         * @param iBits bits at property number indexes, where '1' means internal, '0' means
         *            non-internal.
         */
        InternalPropertiesObject(int iBits) {
            this(-1, -1, -1, iBits);
        }

        InternalPropertiesObject(int rBits, int wBits, int iBits, int nBits) {
            this.rBits = rBits;
            this.wBits = wBits;
            this.iBits = iBits;
            this.nBits = nBits;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return PropertiesVisibilityObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof InternalPropertiesObject;
        }

        @MessageResolution(receiverType = InternalPropertiesObject.class)
        static final class PropertiesVisibilityObjectMessageResolution {
            @Resolve(message = "KEYS")
            public abstract static class KeysNode extends Node {

                public Object access(InternalPropertiesObject receiver, boolean includeInternal) {
                    assert receiver != null;
                    if (includeInternal) {
                        return JavaInterop.asTruffleObject(new String[]{"p1", "p2", "p3", "p4", "p5", "p6"});
                    } else {
                        List<String> propertyNames = new ArrayList<>();
                        for (int i = 1; i <= 6; i++) {
                            if ((receiver.nBits & (1 << i)) == 0) {
                                propertyNames.add("p" + i);
                            }
                        }
                        return JavaInterop.asTruffleObject(propertyNames.toArray());
                    }
                }
            }

            @Resolve(message = "KEY_INFO")
            public abstract static class KeyInfoNode extends Node {

                public int access(InternalPropertiesObject receiver, String propertyName) {
                    if (propertyName.length() != 2 || propertyName.charAt(0) != 'p' || !Character.isDigit(propertyName.charAt(1))) {
                        return 0;
                    }
                    int d = Character.digit(propertyName.charAt(1), 10);
                    if (d > 6) {
                        return 0;
                    }
                    boolean readable = (receiver.rBits & (1 << d)) > 0;
                    boolean writable = (receiver.wBits & (1 << d)) > 0;
                    boolean invocable = (receiver.iBits & (1 << d)) > 0;
                    boolean internal = (receiver.nBits & (1 << d)) > 0;
                    return KeyInfo.newBuilder().setReadable(readable).setWritable(writable).setInvocable(invocable).setInternal(internal).build();
                }
            }
        }
    }
}
