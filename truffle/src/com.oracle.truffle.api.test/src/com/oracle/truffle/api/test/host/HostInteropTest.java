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
package com.oracle.truffle.api.test.host;

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
import java.math.BigInteger;
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

import org.hamcrest.CoreMatchers;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
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
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.polyglot.LanguageSPIHostInteropTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.api.test.polyglot.ValueHostInteropTest;

/**
 * Important: This test was migrated to {@link ValueHostInteropTest} and
 * {@link LanguageSPIHostInteropTest}. Please maintain new tests there.
 */
public class HostInteropTest extends ProxyLanguageEnvTest {

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

    @Override
    public void before() {
        super.before();
        data = new Data();
        obj = asTruffleObject(data);
        xyp = asJavaObject(XYPlus.class, obj);
    }

    @Test
    public void testRecursiveListMarshalling() throws UnknownIdentifierException, UnsupportedMessageException {
        List<GregorianCalendar> testList = Arrays.asList(new GregorianCalendar());
        TruffleObject list = asTruffleObject(testList);
        Object firstElement = ForeignAccess.sendRead(Message.READ.createNode(), list, 0);
        assertTrue(env.isHostObject(firstElement));
    }

    @Test
    public void conversionToClassYieldsTheClass() {
        TruffleObject expected = asTruffleObject(Data.class);
        TruffleObject computed = toJavaClass(asTruffleHostSymbol(Data.class));
        assertEquals("Both class objects are the same", expected, computed);
    }

    @Test
    public void conversionToClass2() {
        TruffleObject expected = asTruffleObject(Class.class);
        TruffleObject computed = toJavaClass(asTruffleHostSymbol(Class.class));
        assertEquals("Both class objects are the same", expected, computed);
    }

    @Test
    public void nullAsJavaObject() {
        TruffleObject nullObject = asTruffleObject(null);
        assertTrue(env.isHostObject(nullObject));
        assertNull(env.asHostObject(nullObject));
    }

    @Test
    public void doubleWrap() {
        data.x = 32;
        data.y = 10.1;
        assertEquals("Assume delegated", 42.1d, xyp.plus(xyp.x(), xyp.y()), 0.05);
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
        List<Object> list = asJavaObject(List.class, keys);
        assertThat(list, CoreMatchers.hasItems("x", "y", "arr", "value", "map", "dataMap", "data", "plus"));

        Method[] objectMethods = Object.class.getMethods();
        for (Method objectMethod : objectMethods) {
            assertThat("No java.lang.Object methods", list, CoreMatchers.not(CoreMatchers.hasItem(objectMethod.getName())));
        }

        keys = sendKeys(obj, true);
        list = asJavaObject(List.class, keys);
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

        TruffleObject truffleMap = asTruffleObject(map);
        TruffleObject ret = sendKeys(truffleMap);
        List<Object> list = asJavaObject(List.class, ret);
        assertThat(list, CoreMatchers.not(CoreMatchers.anyOf(CoreMatchers.hasItem("one"), CoreMatchers.hasItem("null"), CoreMatchers.hasItem("three"))));
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
        Object string = ForeignAccess.sendInvoke(Message.INVOKE.createNode(), obj, "toString");
        assertTrue(string instanceof String && ((String) string).startsWith(Data.class.getName() + "@"));
        Object clazz = ForeignAccess.sendInvoke(Message.INVOKE.createNode(), obj, "getClass");
        assertTrue(clazz instanceof TruffleObject && env.asHostObject(clazz) == Data.class);
        assertEquals(true, ForeignAccess.sendInvoke(Message.INVOKE.createNode(), obj, "equals", obj));
        assertTrue(ForeignAccess.sendInvoke(Message.INVOKE.createNode(), obj, "hashCode") instanceof Integer);

        for (String m : new String[]{"notify", "notifyAll", "wait"}) {
            assertThrowsExceptionWithCause(() -> ForeignAccess.sendInvoke(Message.INVOKE.createNode(), obj, m), IllegalMonitorStateException.class);
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
        TruffleObject pojo = asTruffleObject(new PrivatePOJO());
        Map<?, ?> map = asJavaObject(Map.class, pojo);
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
        TruffleObject pojo = asTruffleObject(new PrivatePOJO());
        TruffleObject result = sendKeys(pojo);
        List<?> propertyNames = asJavaObject(List.class, result);
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
        final TruffleObject pojo = asTruffleObject(orig);
        TruffleObject result = sendKeys(pojo);
        List<?> propertyNames = asJavaObject(List.class, result);
        assertEquals("One instance field and one method", 2, propertyNames.size());
        assertEquals("One field x", "x", propertyNames.get(0));
        assertEquals("One method to access x", "readX", propertyNames.get(1));

        TruffleObject readX = (TruffleObject) message(Message.READ, pojo, "readX");
        Boolean isExecutable = (Boolean) message(Message.IS_EXECUTABLE, readX);
        assertTrue("Method can be executed " + readX, isExecutable);

        orig.writeX(10);
        final Object value = message(Message.EXECUTE, readX);
        assertEquals(10, value);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void noNonStaticPropertiesForAClass() {
        TruffleObject pojo = asTruffleHostSymbol(PublicPOJO.class);
        TruffleObject result = sendKeys(pojo);
        List<Object> propertyNames = asJavaObject(List.class, result);
        assertEquals("3 members: static field 'y', static method 'readY', plus 'class'", 3, propertyNames.size());
        assertThat(propertyNames, CoreMatchers.hasItems("y", "readY", "class"));
    }

    @Test
    public void javaObjectsWrappedForTruffle() {
        Object ret = message(Message.INVOKE, obj, "assertThis", obj);
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
        TruffleObject truffleObject = asTruffleObject(a);

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

    private boolean isJavaObject(Class<?> type, TruffleObject object) {
        return env.isHostObject(object) && type.isInstance(env.asHostObject(object));
    }

    @Test
    public void isJavaObject() {
        // obj == asJavaObject(new Data())
        assertFalse(isJavaObject(XYPlus.class, obj));
        assertTrue(isJavaObject(Data.class, obj));
        assertTrue(isJavaObject(Object.class, obj));
        // assert that asJavaObject unwraps the object if isJavaObject returns true
        assertTrue(asJavaObject(Data.class, obj) == data);
        assertTrue(asJavaObject(Object.class, obj) == data);
    }

    @Test
    public void truffleValue() {
        Object object = new Object();
        assertEquals(env.asGuestValue(object), env.asGuestValue(object));
        assertEquals(this.obj, env.asGuestValue(this.data));
        // Test that asTruffleValue() returns non-wraped primitives:
        object = 42;
        assertTrue(env.asGuestValue(object) == object);
        object = (byte) 42;
        assertTrue(env.asGuestValue(object) == object);
        object = (short) 42;
        assertTrue(env.asGuestValue(object) == object);
        object = 424242424242L;
        assertTrue(env.asGuestValue(object) == object);
        object = 42.42;
        assertTrue(env.asGuestValue(object) == object);
        object = true;
        assertTrue(env.asGuestValue(object) == object);
        object = "42";
        assertTrue(env.asGuestValue(object) == object);
        object = '4';
        assertTrue(env.asGuestValue(object) == object);
        object = true;
        assertTrue(env.asGuestValue(object) == object);
    }

    @Test
    public void isNull() {
        assertTrue(isNull(null));
        assertFalse(isNull(asTruffleObject(new Object())));
        assertTrue(isNull(asTruffleObject(null)));
    }

    @Test
    public void isArray() {
        assertFalse(isArray(null));
        assertFalse(isNull(asTruffleObject(new Object())));
        int[] a = new int[]{1, 2, 3};
        TruffleObject truffleArray = asTruffleObject(a);
        assertTrue(isArray(truffleArray));
    }

    @Test
    public void truffleObjectIsntFunctionalInterface() throws Exception {
        final boolean is = isJavaFunctionalInterface(TruffleObject.class);
        assertFalse("TruffleObject isn't functional interface", is);
    }

    private static boolean isJavaFunctionalInterface(final Class<?> clazz) throws Exception {
        Method isFunctionaInterface = Class.forName("com.oracle.truffle.polyglot.HostInteropReflect").getDeclaredMethod("isFunctionalInterface", Class.class);
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
        TruffleObject object = asTruffleObject((FunctionalWithObjectMethodOverrides) (args) -> args.length >= 1 ? args[0] : null);
        TruffleObject keysObject = ForeignAccess.sendKeys(Message.KEYS.createNode(), object);
        List<?> keyList = asJavaObject(List.class, keysObject);
        assertArrayEquals(new Object[]{"call"}, keyList.toArray());
        assertEquals(42, ForeignAccess.sendExecute(Message.EXECUTE.createNode(), object, 42));
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
        assertFalse(isBoxed(asTruffleObject(new Object())));
        assertTrue(isBoxed(asTruffleObject(42)));
        assertTrue(isBoxed(asTruffleObject((byte) 0x42)));
        assertTrue(isBoxed(asTruffleObject((short) 42)));
        assertTrue(isBoxed(asTruffleObject(4242424242424242L)));
        assertTrue(isBoxed(asTruffleObject(42.42f)));
        assertTrue(isBoxed(asTruffleObject(42.42)));
        assertTrue(isBoxed(asTruffleObject("42")));
        assertTrue(isBoxed(asTruffleObject('4')));
        assertTrue(isBoxed(asTruffleObject(true)));
        assertTrue(isBoxed(asTruffleObject(false)));
    }

    @Test
    public void unbox() {
        assertNull(unbox(null));
        assertNull(unbox(asTruffleObject(new Object())));
        assertEquals(42, unbox(asTruffleObject(42)));
        assertEquals((byte) 42, unbox(asTruffleObject((byte) 42)));
        assertEquals((short) 42, unbox(asTruffleObject((short) 42)));
        assertEquals(4242424242424242L, unbox(asTruffleObject(4242424242424242L)));
        assertEquals(42.42f, unbox(asTruffleObject(42.42f)));
        assertEquals(42.42, unbox(asTruffleObject(42.42)));
        assertEquals("42", unbox(asTruffleObject("42")));
        assertEquals('4', unbox(asTruffleObject('4')));
        assertEquals(true, unbox(asTruffleObject(true)));
        assertEquals(false, unbox(asTruffleObject(false)));
    }

    @Test
    public void notUnboxable() {
        Node unboxNode = Message.UNBOX.createNode();
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendUnbox(unboxNode, asTruffleObject(null)), UnsupportedMessageException.class);
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendUnbox(unboxNode, asTruffleObject(new Object())), UnsupportedMessageException.class);
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendUnbox(unboxNode, asTruffleObject(Object.class)), UnsupportedMessageException.class);
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
        aobj = asTruffleObject(new String[]{"A", "B", "C", "D"});
        testArrayObject(aobj, 4);
    }

    @Test
    public void testHasKeysDefaults() {
        TruffleObject noKeys = new NoKeysObject();
        assertFalse(hasKeys(noKeys));
        TruffleObject keysObj = new DefaultHasKeysObject();
        assertTrue(hasKeys(keysObj));
    }

    @Test
    public void testHasKeys() {
        TruffleObject hasKeysObj = new HasKeysObject(true);
        assertTrue(hasKeys(hasKeysObj));
        hasKeysObj = new HasKeysObject(false);
        assertFalse(hasKeys(hasKeysObj));
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
    public void keyInfoJavaObject() {
        TruffleObject d = asTruffleObject(new TestJavaObject());
        int keyInfo = getKeyInfo(d, "nnoonnee");
        assertFalse(KeyInfo.isExisting(keyInfo));
        keyInfo = getKeyInfo(d, "aField");
        assertTrue(KeyInfo.isExisting(keyInfo));
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        assertFalse(KeyInfo.isRemovable(keyInfo));
        keyInfo = getKeyInfo(d, "toString");
        assertTrue(KeyInfo.isExisting(keyInfo));
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertTrue(KeyInfo.isInvocable(keyInfo));
        assertFalse(KeyInfo.isRemovable(keyInfo));
    }

    @Test
    public void testSystemMethod() throws InteropException {
        TruffleObject system = asTruffleHostSymbol(System.class);
        Object value = ForeignAccess.sendInvoke(Message.INVOKE.createNode(), system, "getProperty", "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));

        Object getProperty = ForeignAccess.sendRead(Message.READ.createNode(), system, "getProperty");
        assertThat(getProperty, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue("IS_EXECUTABLE", ForeignAccess.sendIsExecutable(Message.IS_EXECUTABLE.createNode(), (TruffleObject) getProperty));
        value = ForeignAccess.sendExecute(Message.EXECUTE.createNode(), (TruffleObject) getProperty, "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));
    }

    @Test
    public void testExecuteClass() {
        TruffleObject hashMapClass = asTruffleHostSymbol(HashMap.class);
        assertThrowsExceptionWithCause(() -> ForeignAccess.sendExecute(Message.EXECUTE.createNode(), hashMapClass), UnsupportedMessageException.class);
        assertFalse("IS_EXECUTABLE", ForeignAccess.sendIsExecutable(Message.IS_EXECUTABLE.createNode(), hashMapClass));
    }

    @Test
    public void testNewClass() throws InteropException {
        TruffleObject hashMapClass = asTruffleHostSymbol(HashMap.class);
        Object hashMap = ForeignAccess.sendNew(Message.NEW.createNode(), hashMapClass);
        assertThat(hashMap, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue(isJavaObject(HashMap.class, (TruffleObject) hashMap));
    }

    @Test
    public void testNewObject() throws InteropException {
        TruffleObject objectClass = asTruffleHostSymbol(Object.class);
        Object object = ForeignAccess.sendNew(Message.NEW.createNode(), objectClass);
        assertThat(object, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue(isJavaObject(Object.class, (TruffleObject) object));
    }

    @Test
    public void testNewArray() throws InteropException {
        TruffleObject longArrayClass = asTruffleHostSymbol(long[].class);
        Object longArray = ForeignAccess.sendNew(Message.NEW.createNode(), longArrayClass, 4);
        assertThat(longArray, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue(isJavaObject(long[].class, (TruffleObject) longArray));
        assertEquals(4, message(Message.GET_SIZE, (TruffleObject) longArray));
    }

    @Test
    public void testException() throws InteropException {
        TruffleObject iterator = asTruffleObject(Collections.emptyList().iterator());
        try {
            ForeignAccess.sendInvoke(Message.INVOKE.createNode(), iterator, "next");
            fail("expected an exception but none was thrown");
        } catch (InteropException ex) {
            throw ex;
        } catch (Exception ex) {
            assertTrue("expected HostException but was: " + ex.getClass(), env.isHostException(ex));
            assertThat(env.asHostException(ex), CoreMatchers.instanceOf(NoSuchElementException.class));
        }
    }

    @Test
    public void testException2() throws InteropException {
        TruffleObject hashMapClass = asTruffleHostSymbol(HashMap.class);
        try {
            ForeignAccess.sendNew(Message.NEW.createNode(), hashMapClass, -1);
            fail("expected an exception but none was thrown");
        } catch (InteropException ex) {
            throw ex;
        } catch (Exception ex) {
            assertTrue("expected HostException but was: " + ex.getClass(), env.isHostException(ex));
            assertThat(env.asHostException(ex), CoreMatchers.instanceOf(IllegalArgumentException.class));
        }

        try {
            ForeignAccess.sendNew(Message.NEW.createNode(), hashMapClass, "");
            fail("expected an exception but none was thrown");
        } catch (UnsupportedTypeException ex) {
        }
    }

    @Test
    public void testRemoveMessage() {
        data.arr = new String[]{"Hello", "World", "!"};
        TruffleObject truffleList = asTruffleObject(new ArrayList<>(Arrays.asList(data.arr)));
        assertEquals(3, message(Message.GET_SIZE, truffleList));
        assertEquals(true, message(Message.REMOVE, truffleList, 1));
        assertEquals(2, message(Message.GET_SIZE, truffleList));
        try {
            message(Message.REMOVE, truffleList, 10);
            fail("Out of bounds.");
        } catch (Exception e) {
            assertTrue(e.toString(), e instanceof UnknownIdentifierException);
            assertEquals("10", ((UnknownIdentifierException) e).getUnknownIdentifier());
        }

        Object arrObj = message(Message.READ, obj, "arr");
        TruffleObject truffleArr = (TruffleObject) arrObj;
        try {
            message(Message.REMOVE, truffleArr, 0);
            fail("Remove of elements of an array is not supported.");
        } catch (Exception e) {
            assertTrue(e.toString(), e instanceof UnsupportedMessageException);
            assertEquals(Message.REMOVE, ((UnsupportedMessageException) e).getUnsupportedMessage());
        }

        Map<String, String> map = new HashMap<>();
        map.put("a", "aa");
        map.put("b", "bb");
        TruffleObject truffleMap = asTruffleObject(map);
        assertEquals(true, message(Message.REMOVE, truffleMap, "a"));
        assertEquals(1, map.size());
        try {
            message(Message.REMOVE, truffleMap, "a");
            fail("UnknownIdentifierException");
        } catch (Exception e) {
            assertTrue(e.toString(), e instanceof UnknownIdentifierException);
            assertEquals("a", ((UnknownIdentifierException) e).getUnknownIdentifier());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRemoveList() {
        List<Integer> list = asJavaObject(List.class, new ArrayTruffleObject(100));
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

        data.arr = new String[]{"Hello", "World", "!"};
        List<String> arr = xyp.arr();
        try {
            assertEquals("World", arr.remove(1));
            fail("Remove of elements of an array is not supported.");
        } catch (UnsupportedOperationException e) {
            // O.K.
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRemoveMap() {
        int size = 15;
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            char c = (char) ('a' + i);
            map.put(new String(new char[]{c}), new String(new char[]{c, c}));
        }
        Map<String, String> jmap = asJavaObject(Map.class, new RemoveKeysObject(map));
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

        data.map = data;
        Map<String, Object> dmap = xyp.map();
        try {
            dmap.remove("x");
            fail("Remove of object fields is not supported.");
        } catch (UnsupportedOperationException e) {
            // O.K.
        }
    }

    @Test
    public void testHostSymbol() {
        TruffleObject bigIntegerClass = (TruffleObject) env.asGuestValue(BigInteger.class);
        assertFalse(env.isHostSymbol(bigIntegerClass));
        TruffleObject bigIntegerStatic1 = (TruffleObject) env.asHostSymbol(BigInteger.class);
        assertTrue(env.isHostSymbol(bigIntegerStatic1));
        TruffleObject bigIntegerStatic2 = (TruffleObject) env.lookupHostSymbol(BigInteger.class.getName());
        assertTrue(env.isHostSymbol(bigIntegerStatic2));

        assertFalse(KeyInfo.isExisting(getKeyInfo(bigIntegerClass, "ZERO")));
        assertTrue(KeyInfo.isExisting(getKeyInfo(bigIntegerClass, "getName")));
        for (TruffleObject bigIntegerStatic : Arrays.asList(bigIntegerStatic1, bigIntegerStatic2)) {
            assertTrue(KeyInfo.isExisting(getKeyInfo(bigIntegerStatic, "ZERO")));
            assertFalse(KeyInfo.isExisting(getKeyInfo(bigIntegerStatic, "getName")));
        }
    }

    public static final class TestJavaObject {
        public int aField = 10;
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

    static Object message(final Message m, TruffleObject receiver, Object... arr) {
        Node n = m.createNode();
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(n, receiver));
        return callTarget.call(arr);
    }

    static boolean hasKeys(TruffleObject foreignObject) {
        return ForeignAccess.sendHasKeys(Message.HAS_KEYS.createNode(), foreignObject);
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
                    return ProxyLanguage.getCurrentContext().getEnv().asGuestValue(new String[]{"p1", "p2", "p3", "p4", "p5", "p6"});
                }
            }
        }
    }

    static final class DefaultHasKeysObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return ForeignAccess.create(new ForeignAccess.Factory() {
                @Override
                public boolean canHandle(TruffleObject obj) {
                    return obj instanceof DefaultHasKeysObject;
                }

                @Override
                public CallTarget accessMessage(Message message) {
                    if (Message.HAS_KEYS.equals(message)) {
                        return null;
                    }
                    if (Message.KEYS.equals(message)) {
                        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new ArrayTruffleObject(1)));
                    }
                    return null;
                }
            });
        }

    }

    static final class HasKeysObject implements TruffleObject {

        private final boolean hasKeys;

        HasKeysObject(boolean hasKeys) {
            this.hasKeys = hasKeys;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return HasKeysObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof HasKeysObject;
        }

        @MessageResolution(receiverType = HasKeysObject.class)
        static final class HasKeysObjectMessageResolution {

            @Resolve(message = "HAS_KEYS")
            public abstract static class HasKeysNode extends Node {

                public Object access(HasKeysObject receiver) {
                    return receiver.hasKeys;
                }
            }
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
                    return ProxyLanguage.getCurrentContext().getEnv().asGuestValue(list);
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

            @Resolve(message = "HAS_KEYS")
            public abstract static class HasKeysNode extends Node {

                public Object access(InternalPropertiesObject receiver) {
                    assert receiver != null;
                    return true;
                }
            }

            @Resolve(message = "KEYS")
            public abstract static class KeysNode extends Node {

                public Object access(InternalPropertiesObject receiver, boolean includeInternal) {
                    assert receiver != null;
                    if (includeInternal) {
                        return ProxyLanguage.getCurrentContext().getEnv().asGuestValue(new String[]{"p1", "p2", "p3", "p4", "p5", "p6"});
                    } else {
                        List<String> propertyNames = new ArrayList<>();
                        for (int i = 1; i <= 6; i++) {
                            if ((receiver.nBits & (1 << i)) == 0) {
                                propertyNames.add("p" + i);
                            }
                        }
                        return ProxyLanguage.getCurrentContext().getEnv().asGuestValue(propertyNames.toArray());
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
                    int info = KeyInfo.NONE;
                    if (readable) {
                        info |= KeyInfo.READABLE;
                    }
                    if (writable) {
                        info |= KeyInfo.MODIFIABLE;
                    }
                    if (invocable) {
                        info |= KeyInfo.INVOCABLE;
                    }
                    if (internal) {
                        info |= KeyInfo.INTERNAL;
                    }
                    return info;
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
