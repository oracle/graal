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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;

public class ClassInteropTest extends ProxyLanguageEnvTest {
    private TruffleObject obj;
    private XYPlus xyp;

    public static int x;
    public static double y;
    public static final int CONST = 42;
    public int value = CONST;

    public static double plus(double a, double b) {
        return a + b;
    }

    @Override
    public void before() {
        super.before();
        obj = asTruffleHostSymbol(ClassInteropTest.class);
        xyp = asJavaObject(XYPlus.class, obj);
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

    @Test(expected = UnsupportedOperationException.class)
    public void cannotReadValueAsItIsNotStatic() throws Exception {
        assertEquals("Field read", 42, xyp.value());
        fail("value isn't static field");
    }

    @Test
    public void canReadValueAfterCreatingNewInstance() throws Exception {
        Object objInst = HostInteropTest.message(Message.NEW, obj);
        assertTrue("It is truffle object", objInst instanceof TruffleObject);
        XYPlus inst = asJavaObject(XYPlus.class, (TruffleObject) objInst);
        assertEquals("Field read", 42, inst.value());
    }

    @Test(expected = UnknownIdentifierException.class)
    public void noNonStaticMethods() {
        Object res = HostInteropTest.message(Message.READ, obj, "readCONST");
        assertNull("not found", res);
    }

    @Test
    public void canAccessStaticMemberTypes() {
        Object res = HostInteropTest.message(Message.READ, obj, "XYPlus");
        assertTrue("It is truffle object", res instanceof TruffleObject);
        Class<?> c = asJavaObject(Class.class, (TruffleObject) res);
        assertSame(XYPlus.class, c);
    }

    @Test
    public void canCreateMemberTypeInstances() {
        Object type = HostInteropTest.message(Message.READ, obj, "Zed");
        assertTrue("Type is a truffle object", type instanceof TruffleObject);
        TruffleObject truffleType = (TruffleObject) type;
        Object objInst = HostInteropTest.message(Message.NEW, truffleType, 22);
        assertTrue("Created instance is a truffle object", objInst instanceof TruffleObject);
        Object res = asJavaObject(Object.class, (TruffleObject) objInst);
        assertTrue("Instance is of correct type", res instanceof Zed);
        assertEquals("Constructor was invoked", 22, ((Zed) res).val);
    }

    @Test
    public void canListStaticTypes() {
        Object type = HostInteropTest.message(Message.KEYS, obj);
        assertTrue("Type is a truffle object", type instanceof TruffleObject);
        String[] names = asJavaObject(String[].class, (TruffleObject) type);
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
        Object type = HostInteropTest.message(Message.KEY_INFO, obj, "Nonstatic");
        assertEquals(0, type);
    }

    @Test
    public void staticInnerTypeIsNotWritable() {
        Object type = HostInteropTest.message(Message.KEY_INFO, obj, "Zed");
        int keyInfo = (int) type;
        assertTrue("Key exists", KeyInfo.isExisting(keyInfo));
        assertTrue("Key readable", KeyInfo.isReadable(keyInfo));
        assertFalse("Key NOT writable", KeyInfo.isModifiable(keyInfo));
    }

    @Test(expected = com.oracle.truffle.api.interop.UnknownIdentifierException.class)
    public void nonpublicTypeNotvisible() {
        Object type = HostInteropTest.message(Message.KEY_INFO, obj, "NonStaticInterface");
        assertEquals("Non-public member type not visible", 0, type);

        type = HostInteropTest.message(Message.KEYS, obj);
        assertTrue("Type is a truffle object", type instanceof TruffleObject);
        String[] names = asJavaObject(String[].class, (TruffleObject) type);
        assertEquals("Non-public member type not enumerated", -1, Arrays.asList(names).indexOf("NonStaticInterface"));

        HostInteropTest.message(Message.READ, obj, "NonStaticInterface");
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

        int value();
    }
}
