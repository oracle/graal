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

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public class OverloadedTest extends ProxyLanguageEnvTest {
    public static final class Data {
        public int x;

        public void x(int value) {
            this.x = value * 2;
        }

        public double x() {
            return this.x * 2;
        }
    }

    public static final class Num {
        public Object x;
        public String parameter;

        public void x(int value) {
            this.x = value;
            this.parameter = "int";
        }

        public void x(Number value) {
            this.x = value;
            this.parameter = "Number";
        }

        public void x(BigInteger value) {
            this.x = value;
            this.parameter = "BigInteger";
        }

        public void d(int value) {
            this.x = value;
            this.parameter = "int";
        }

        public void d(double value) {
            this.x = value;
            this.parameter = "double";
        }

        public void f(int value) {
            this.x = value;
            this.parameter = "int";
        }

        public void f(float value) {
            this.x = value;
            this.parameter = "float";
        }
    }

    private TruffleObject obj;
    private Data data;

    @Before
    public void initObjects() {
        data = new Data();
        obj = asTruffleObject(data);
    }

    @Test
    public void threeProperties() {
        TruffleObject ret = JavaInteropTest.sendKeys(obj);
        List<?> list = context.asValue(ret).as(List.class);
        assertEquals("Just one (overloaded) property: " + list, 1, list.size());
        assertEquals("x", list.get(0));
    }

    @Test
    public void readAndWriteField() {
        data.x = 11;
        assertEquals(11, JavaInteropTest.message(Message.READ, obj, "x"));

        JavaInteropTest.message(Message.WRITE, obj, "x", 12);
        assertEquals(12, data.x);

        JavaInteropTest.message(Message.WRITE, obj, "x", new UnboxableToInt(13));
        assertEquals(13, data.x);
    }

    @Test
    public void callGetterAndSetter() {
        data.x = 11;
        assertEquals(22.0, JavaInteropTest.message(Message.createInvoke(0), obj, "x"));

        JavaInteropTest.message(Message.createInvoke(1), obj, "x", 10);
        assertEquals(20, data.x);

        JavaInteropTest.message(Message.createInvoke(1), obj, "x", new UnboxableToInt(21));
        assertEquals(42, data.x);
    }

    @Test
    public void testOverloadingTruffleObjectArg() throws InteropException {
        Node n = Message.createInvoke(1).createNode();
        ForeignAccess.sendInvoke(n, obj, "x", new UnboxableToInt(21));
        assertEquals(42, data.x);
        ForeignAccess.sendInvoke(n, obj, "x", env.asBoxedGuestValue(10));
        assertEquals(20, data.x);
        ForeignAccess.sendInvoke(n, obj, "x", 10);
        assertEquals(20, data.x);
    }

    @Test
    public void testOverloadingNumber() throws InteropException {
        Node n = Message.createInvoke(1).createNode();
        Num num = new Num();
        TruffleObject numobj = asTruffleObject(num);
        ForeignAccess.sendInvoke(n, numobj, "x", new UnboxableToInt(21));
        assertEquals("int", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "x", asTruffleObject(new AtomicInteger(22)));
        assertEquals("Number", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "x", asTruffleObject(BigInteger.TEN));
        assertEquals("BigInteger", num.parameter);
    }

    @Test
    public void testVarArgs() throws InteropException {
        TruffleObject stringClass = asTruffleHostSymbol(String.class);
        assertEquals("bla", ForeignAccess.sendInvoke(Message.createInvoke(1).createNode(), stringClass, "format", "bla"));
        assertEquals("42", ForeignAccess.sendInvoke(Message.createInvoke(2).createNode(), stringClass, "format", "%d", 42));
        assertEquals("1337", ForeignAccess.sendInvoke(Message.createInvoke(3).createNode(), stringClass, "format", "%d%d", 13, 37));
    }

    public interface Identity<T> {
        T getId();
    }

    public interface SomeThingWithIdentity extends Identity<Integer> {
        @Override
        Integer getId();
    }

    public static class ActualRealThingWithIdentity implements SomeThingWithIdentity {
        Integer id = 42;

        @Override
        public Integer getId() {
            return id;
        }
    }

    @Test
    public void testGenericReturnTypeBridgeMethod() throws InteropException {
        TruffleObject thing = asTruffleObject(new ActualRealThingWithIdentity());
        assertEquals(42, ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), thing, "getId"));
    }

    @Test
    public void testWidening() throws InteropException {
        Node n = Message.createInvoke(1).createNode();
        Num num = new Num();
        TruffleObject numobj = asTruffleObject(num);
        ForeignAccess.sendInvoke(n, numobj, "d", (byte) 42);
        assertEquals("int", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "d", (short) 42);
        assertEquals("int", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "d", 42);
        assertEquals("int", num.parameter);

        ForeignAccess.sendInvoke(n, numobj, "d", 42.1f);
        assertEquals("double", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "d", 42.1d);
        assertEquals("double", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "d", 0x8000_0000L);
        assertEquals("double", num.parameter);

        ForeignAccess.sendInvoke(n, numobj, "d", 42L);
        assertEquals("int", num.parameter);

        ForeignAccess.sendInvoke(n, numobj, "f", 42L);
        assertEquals("int", num.parameter);
    }

    @Test
    public void testNarrowing() throws InteropException {
        Node n = Message.createInvoke(1).createNode();
        Num num = new Num();
        TruffleObject numobj = asTruffleObject(num);
        ForeignAccess.sendInvoke(n, numobj, "f", 42.5f);
        assertEquals("float", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "f", 42.5d);
        assertEquals("float", num.parameter);
    }
}
