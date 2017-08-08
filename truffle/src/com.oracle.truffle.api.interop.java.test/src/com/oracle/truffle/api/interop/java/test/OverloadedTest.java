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
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;

public class OverloadedTest {
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
    }

    private TruffleObject obj;
    private Data data;

    @Before
    public void initObjects() {
        data = new Data();
        obj = JavaInterop.asTruffleObject(data);
    }

    @Test
    public void threeProperties() {
        TruffleObject ret = JavaInteropTest.sendKeys(obj);
        List<?> list = JavaInterop.asJavaObject(List.class, ret);
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
        ForeignAccess.sendInvoke(n, obj, "x", JavaInterop.asTruffleObject(BigInteger.TEN));
        assertEquals(20, data.x);
    }

    @Test
    public void testOverloadingNumber() throws InteropException {
        Node n = Message.createInvoke(1).createNode();
        Num num = new Num();
        TruffleObject numobj = JavaInterop.asTruffleObject(num);
        ForeignAccess.sendInvoke(n, numobj, "x", new UnboxableToInt(21));
        assertEquals("int", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "x", JavaInterop.asTruffleObject(new AtomicInteger(22)));
        assertEquals("Number", num.parameter);
        ForeignAccess.sendInvoke(n, numobj, "x", JavaInterop.asTruffleObject(BigInteger.TEN));
        assertEquals("BigInteger", num.parameter);
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
        TruffleObject thing = JavaInterop.asTruffleObject(new ActualRealThingWithIdentity());
        assertEquals(42, ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), thing, "getId"));
    }

    @MessageResolution(receiverType = UnboxableToInt.class)
    public static final class UnboxableToInt implements TruffleObject {

        private final int value;

        public UnboxableToInt(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return UnboxableToIntForeign.ACCESS;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof UnboxableToInt;
        }

        @Resolve(message = "UNBOX")
        abstract static class UnboxINode extends Node {
            Object access(UnboxableToInt obj) {
                return obj.getValue();
            }
        }

        @Resolve(message = "IS_BOXED")
        abstract static class IsBoxedINode extends Node {
            @SuppressWarnings("unused")
            Object access(UnboxableToInt obj) {
                return true;
            }
        }
    }
}
