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

import com.oracle.truffle.api.interop.InteropException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import java.util.Arrays;

public class ClassInteropTest {
    private TruffleObject obj;
    private XYPlus xyp;

    public static int x;
    public static double y;
    public static final int CONST = 42;
    public int value = CONST;

    public static double plus(double a, double b) {
        return a + b;
    }

    @Before
    public void initObjects() {
        obj = JavaInterop.asTruffleObject(ClassInteropTest.class);
        xyp = JavaInterop.asJavaObject(XYPlus.class, obj);
    }

    @Test
    public void doubleWrap() {
        x = 32;
        y = 10.1;
        assertEquals("Assume delegated", 42.1d, xyp.plus(xyp.x(), xyp.y()), 0.05);
    }

    @Test
    public void readCONST() {
        assertEquals("Field read", 42, xyp.CONST(), 0.01);
    }

    @Test(expected = UnknownIdentifierException.class)
    public void cannotReadValueAsItIsNotStatic() throws Exception {
        assertEquals("Field read", 42, xyp.value());
        fail("value isn't static field");
    }

    @Test
    public void canReadValueAfterCreatingNewInstance() throws Exception {
        Object objInst = JavaInteropTest.message(Message.createNew(0), obj);
        assertTrue("It is truffle object", objInst instanceof TruffleObject);
        XYPlus inst = JavaInterop.asJavaObject(XYPlus.class, (TruffleObject) objInst);
        assertEquals("Field read", 42, inst.value());
    }

    @Test(expected = UnknownIdentifierException.class)
    public void noNonStaticMethods() {
        Object res = JavaInteropTest.message(Message.READ, obj, "readCONST");
        assertNull("not found", res);
    }

    @Test
    public void canAccessStaticMemberTypes() {
        Object res = JavaInteropTest.message(Message.READ, obj, "XYPlus");
        assertTrue("It is truffle object", res instanceof TruffleObject);
        Class<?> c = JavaInterop.asJavaObject(Class.class, (TruffleObject) res);
        assertSame(XYPlus.class, c);
    }

    @Test
    public void canCreateMemberTypeInstances() {
        Object type = JavaInteropTest.message(Message.READ, obj, "Zed");
        assertTrue("Type is a truffle object", type instanceof TruffleObject);
        TruffleObject truffleType = (TruffleObject) type;
        Object objInst = JavaInteropTest.message(Message.createNew(1), truffleType, 22);
        assertTrue("Created instance is a truffle object", objInst instanceof TruffleObject);
        Object res = JavaInterop.asJavaObject(Object.class, (TruffleObject) objInst);
        assertTrue("Instance is of correct type", res instanceof Zed);
        assertEquals("Constructor was invoked", 22, ((Zed) res).val);
    }

    @Test
    public void canListStaticTypes() {
        Object type = JavaInteropTest.message(Message.KEYS, obj);
        assertTrue("Type is a truffle object", type instanceof TruffleObject);
        String[] names = JavaInterop.asJavaObject(String[].class, (TruffleObject) type);
        int zed = 0;
        int xy = 0;
        int eman = 0;
        int nonstatic = 0;
        for (String s : names) {
            switch (s) {
                case "Zed":
                    zed++;
                    break;
                case "XYPlus":
                    xy++;
                    break;
                case "Eman":
                    eman++;
                    break;
                case "Nonstatic":
                    nonstatic++;
                    break;
            }
        }
        assertEquals("Static class enumerated", 1, zed);
        assertEquals("Interface enumerated", 1, xy);
        assertEquals("Enum enumerated", 1, eman);
        assertEquals("Nonstatic type suppressed", 0, nonstatic);
    }

    @Test
    public void nonstaticTypeDoesNotExist() {
        Object type = JavaInteropTest.message(Message.KEY_INFO, obj, "Nonstatic");
        assertEquals(0, type);
    }

    @Test
    public void staticInnerTypeIsNotWritable() {
        Object type = JavaInteropTest.message(Message.KEY_INFO, obj, "Zed");
        assertTrue("Key exists", ((Integer) type & 0b1) > 0);
        assertTrue("Key readable", ((Integer) type & 0b10) > 0);
        assertTrue("Key NOT writable", ((Integer) type & 0b100) == 0);
    }

    @Test(expected = com.oracle.truffle.api.interop.UnknownIdentifierException.class)
    public void nonpublicTypeNotvisible() {
        Object type = JavaInteropTest.message(Message.KEY_INFO, obj, "NonStaticInterface");
        assertEquals("Non-public member type not visible", 0, type);

        type = JavaInteropTest.message(Message.KEYS, obj);
        assertTrue("Type is a truffle object", type instanceof TruffleObject);
        String[] names = JavaInterop.asJavaObject(String[].class, (TruffleObject) type);
        assertEquals("Non-public member type not enumerated", -1, Arrays.asList(names).indexOf("NonStaticInterface"));

        JavaInteropTest.message(Message.READ, obj, "NonStaticInterface");
        fail("Cannot read non-static member type");
    }

    interface NonStaticInterface {
    }

    public enum Eman {

    }

    class Nonstatic {

    }

    public static class Zed {
        int val;

        public Zed() {
        }

        public Zed(int val) {
            this.val = val;
        }
    }

    public interface XYPlus {
        int x();

        double y();

        double plus(double a, double b);

        // Checkstyle: stop method name check
        int CONST();

        // Checkstyle: resume method name check

        int value() throws InteropException;
    }
}
