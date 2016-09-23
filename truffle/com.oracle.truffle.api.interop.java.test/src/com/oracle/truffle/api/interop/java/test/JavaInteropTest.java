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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.interop.java.MethodMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.Iterator;
import java.util.Map;

public class JavaInteropTest {
    public class Data {
        public int x;
        public double y;
        public String[] arr;
        public Object value;
        public Object map;

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
        final Node keysNode = Message.KEYS.createNode();
        class SendKeys extends RootNode {
            SendKeys() {
                super(TruffleLanguage.class, null, null);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                try {
                    return ForeignAccess.sendKeys(keysNode, frame, obj);
                } catch (InteropException ex) {
                    throw ex.raise();
                }
            }
        }
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new SendKeys());
        final TruffleObject ret = (TruffleObject) callTarget.call();
        List<?> list = JavaInterop.asJavaObject(List.class, ret);
        assertTrue("Contains x " + list, list.contains("x"));
        assertTrue("Contains y " + list, list.contains("y"));
        assertTrue("Contains arr " + list, list.contains("arr"));
        assertTrue("Contains value " + list, list.contains("value"));
        assertTrue("Contains map " + list, list.contains("map"));
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

        assertEquals(2, ForeignAccess.sendRead(Message.READ.createNode(), Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor()), truffleObject, 1));
        assertEquals(2, ForeignAccess.sendRead(Message.READ.createNode(), Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor()), truffleObject, 1.0));
        assertEquals(2, ForeignAccess.sendRead(Message.READ.createNode(), Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor()), truffleObject, 1L));

        ForeignAccess.sendWrite(Message.WRITE.createNode(), Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor()), truffleObject, 1, 42);
        ForeignAccess.sendWrite(Message.WRITE.createNode(), Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor()), truffleObject, 1.0, 42);
        ForeignAccess.sendWrite(Message.WRITE.createNode(), Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor()), truffleObject, 1L, 42);

        assertEquals(42, ForeignAccess.sendRead(Message.READ.createNode(), Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor()), truffleObject, 1));
        assertEquals(42, ForeignAccess.sendRead(Message.READ.createNode(), Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor()), truffleObject, 1.0));
        assertEquals(42, ForeignAccess.sendRead(Message.READ.createNode(), Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor()), truffleObject, 1L));

    }

    public interface XYPlus {
        List<String> arr();

        Map<String, Object> map();

        int x();

        @MethodMessage(message = "WRITE")
        void x(int v);

        double y();

        double plus(double a, double b);

        Integer value();

        XYPlus assertThis(Object obj);
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
                return ForeignAccess.send(foreignAccess, frame, function, args);
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        }
    } // end of TemporaryRoot

}
