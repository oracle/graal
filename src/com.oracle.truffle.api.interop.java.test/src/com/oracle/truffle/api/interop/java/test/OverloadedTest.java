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

import com.oracle.truffle.api.interop.Message;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import static com.oracle.truffle.api.interop.java.test.JavaInteropTest.sendKeys;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OverloadedTest {
    public final class Data {
        public int x;

        public void x(int value) {
            this.x = value * 2;
        }

        public double x() {
            return this.x * 2;
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
        TruffleObject ret = (TruffleObject) sendKeys().call(obj);
        List<?> list = JavaInterop.asJavaObject(List.class, ret);
        assertEquals("Just one (overloaded) property: " + list, 1, list.size());
        assertEquals("x", list.get(0));
    }

    @Test
    public void readAndWriteField() {
        data.x = 11;
        final Object raw = JavaInteropTest.message(Message.READ, obj, "x");
        assertTrue("It is truffle object: " + raw, raw instanceof TruffleObject);
        final TruffleObject wrap = (TruffleObject) raw;
        assertTrue("Can be unboxed", (boolean) JavaInteropTest.message(Message.IS_BOXED, wrap));
        final Object value = JavaInteropTest.message(Message.UNBOX, wrap);
        assertEquals(11, value);

        JavaInteropTest.message(Message.WRITE, obj, "x", 10);
        assertEquals(10, data.x);
    }

    @Test
    public void callGetterAndSetter() {
        data.x = 11;
        assertEquals(22.0, JavaInteropTest.message(Message.createInvoke(0), obj, "x"));

        JavaInteropTest.message(Message.createInvoke(1), obj, "x", 10);
        assertEquals(20, data.x);
    }

}
