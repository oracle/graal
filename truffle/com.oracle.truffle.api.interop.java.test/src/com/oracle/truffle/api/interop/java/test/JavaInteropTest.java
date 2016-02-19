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

import org.junit.After;
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

public class JavaInteropTest {
    public int x;
    public double y;
    public String[] arr;
    public Object value;

    private TruffleObject obj;
    private XYPlus xyp;
    private boolean assertThisCalled;

    public double plus(double a, double b) {
        return a + b;
    }

    public Object assertThis(Object param) {
        assertSame("When a Java object is passed into Truffle and back, it is again the same object", this, param);
        assertThisCalled = true;
        return this;
    }

    @Before
    public void initObjects() {
        obj = JavaInterop.asTruffleObject(this);
        xyp = JavaInterop.asJavaObject(XYPlus.class, obj);
        InstrumentationTestMode.set(true);
    }

    @After
    public void after() {
        InstrumentationTestMode.set(false);
    }

    @Test
    public void doubleWrap() {
        x = 32;
        y = 10.1;
        assertEquals("Assume delegated", 42.1d, xyp.plus(xyp.x(), xyp.y()), 0.05);
    }

    @Test
    public void writeX() {
        xyp.x(10);
        assertEquals("Changed", 10, x);
    }

    @Test
    public void assertThisIsSame() {
        assertThisCalled = false;
        XYPlus anotherThis = xyp.assertThis(this);
        assertTrue(assertThisCalled);

        x = 44;
        assertEquals(44, anotherThis.x());

        assertEquals("The two proxies are equal", anotherThis, xyp);
    }

    @Test
    public void javaObjectsWrappedForTruffle() {
        Object ret = message(Message.createInvoke(1), obj, "assertThis", obj);
        assertTrue("Expecting truffle wrapper: " + ret, ret instanceof TruffleObject);
        assertEquals("Same as this obj", ret, obj);
    }

    @Test
    public void arrayHasSize() {
        arr = new String[]{"Hello", "World", "!"};
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
        arr = new String[]{"Hello", "World", "!"};
        List<String> list = xyp.arr();
        assertEquals("Three elements", 3, list.size());
        assertEquals("Hello", list.get(0));
        assertEquals("World", list.get(1));
        assertEquals("!", list.get(2));

        list.set(1, "there");

        assertEquals("there", arr[1]);
    }

    @Test
    public void nullCanBeReturned() {
        assertNull(xyp.value());
    }

    @Test
    public void integerCanBeConvertedFromAnObjectField() {
        value = 42;
        assertEquals((Integer) 42, xyp.value());
    }

    public interface XYPlus {
        List<String> arr();

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
