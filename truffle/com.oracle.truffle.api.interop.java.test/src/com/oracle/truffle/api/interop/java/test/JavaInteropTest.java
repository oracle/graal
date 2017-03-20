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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
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

    @Test
    public void assertKeysAndProperties() {
        CallTarget callTarget = sendKeys();
        final TruffleObject ret = (TruffleObject) callTarget.call(obj);
        List<?> list = JavaInterop.asJavaObject(List.class, ret);
        assertTrue("Contains x " + list, list.contains("x"));
        assertTrue("Contains y " + list, list.contains("y"));
        assertTrue("Contains arr " + list, list.contains("arr"));
        assertTrue("Contains value " + list, list.contains("value"));
        assertTrue("Contains map " + list, list.contains("map"));
    }

    @Test
    public void assertKeysFromAMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("null", null);
        map.put("three", 3);

        TruffleObject truffleMap = JavaInterop.asTruffleObject(map);
        TruffleObject ret = (TruffleObject) sendKeys().call(truffleMap);
        List<?> list = JavaInterop.asJavaObject(List.class, ret);
        assertTrue("Contains one " + list, list.contains("one"));
        assertTrue("Contains null " + list, list.contains("null"));
        assertTrue("Contains three " + list, list.contains("three"));

    }

    static CallTarget sendKeys() {
        final Node keysNode = Message.KEYS.createNode();

        class SendKeys extends RootNode {
            SendKeys() {
                super(TruffleLanguage.class, null, null);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                try {
                    final TruffleObject receiver = (TruffleObject) frame.getArguments()[0];
                    return ForeignAccess.sendKeys(keysNode, receiver);
                } catch (InteropException ex) {
                    throw ex.raise();
                }
            }
        }
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new SendKeys());
        return callTarget;
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
        CallTarget callKeys = sendKeys();
        TruffleObject result = (TruffleObject) callKeys.call(pojo);
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
        CallTarget callKeys = sendKeys();
        TruffleObject result = (TruffleObject) callKeys.call(pojo);
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
        CallTarget callKeys = sendKeys();
        TruffleObject result = (TruffleObject) callKeys.call(pojo);
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
        assertTrue(JavaInterop.isNull(null));
        assertFalse(JavaInterop.isNull(JavaInterop.asTruffleObject(new Object())));
        assertTrue(JavaInterop.isNull(JavaInterop.asTruffleObject(null)));
    }

    @Test
    public void isArray() {
        assertFalse(JavaInterop.isArray(null));
        assertFalse(JavaInterop.isNull(JavaInterop.asTruffleObject(new Object())));
        int[] a = new int[]{1, 2, 3};
        TruffleObject truffleArray = JavaInterop.asTruffleObject(a);
        assertTrue(JavaInterop.isArray(truffleArray));
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
        final boolean is = isJavaFunctionalInterface(FunctionalWithDefaults.class);
        assertTrue("yes, it is", is);
    }

    @FunctionalInterface
    interface FunctionalWithDefaults {
        Object call(Object... args);

        default int call(int a, int b) {
            return (int) call(new Object[]{a, b});
        }
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
        assertFalse(JavaInterop.isBoxed(null));
        assertFalse(JavaInterop.isBoxed(JavaInterop.asTruffleObject(new Object())));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject(42)));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject((byte) 0x42)));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject((short) 42)));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject(4242424242424242L)));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject(42.42f)));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject(42.42)));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject("42")));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject('4')));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject(true)));
        assertTrue(JavaInterop.isBoxed(JavaInterop.asTruffleObject(false)));
    }

    @Test
    public void unbox() {
        assertNull(JavaInterop.unbox(null));
        assertNull(JavaInterop.unbox(JavaInterop.asTruffleObject(new Object())));
        assertEquals(42, JavaInterop.unbox(JavaInterop.asTruffleObject(42)));
        assertEquals((byte) 42, JavaInterop.unbox(JavaInterop.asTruffleObject((byte) 42)));
        assertEquals((short) 42, JavaInterop.unbox(JavaInterop.asTruffleObject((short) 42)));
        assertEquals(4242424242424242L, JavaInterop.unbox(JavaInterop.asTruffleObject(4242424242424242L)));
        assertEquals(42.42f, JavaInterop.unbox(JavaInterop.asTruffleObject(42.42f)));
        assertEquals(42.42, JavaInterop.unbox(JavaInterop.asTruffleObject(42.42)));
        assertEquals("42", JavaInterop.unbox(JavaInterop.asTruffleObject("42")));
        assertEquals('4', JavaInterop.unbox(JavaInterop.asTruffleObject('4')));
        assertEquals(true, JavaInterop.unbox(JavaInterop.asTruffleObject(true)));
        assertEquals(false, JavaInterop.unbox(JavaInterop.asTruffleObject(false)));
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
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(TruffleLanguage.class, n, receiver, arr));
        return callTarget.call();
    }

    private static class TemporaryRoot extends RootNode {
        @Node.Child private Node foreignAccess;
        private final TruffleObject function;
        private final Object[] args;

        @SuppressWarnings("rawtypes")
        TemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function, Object... args) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.function = function;
            this.args = args;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return ForeignAccess.send(foreignAccess, function, args);
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        }
    } // end of TemporaryRoot

}
