/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;

public class ClassInteropTest extends ProxyLanguageEnvTest {
    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

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
        Object objInst = INTEROP.instantiate(obj);
        assertTrue("It is truffle object", objInst instanceof TruffleObject);
        XYPlus inst = asJavaObject(XYPlus.class, (TruffleObject) objInst);
        assertEquals("Field read", 42, inst.value());
    }

    @Test(expected = UnknownIdentifierException.class)
    public void noNonStaticMethods() throws InteropException {
        Object res = INTEROP.readMember(obj, "readCONST");
        assertNull("not found", res);
    }

    @Test
    public void canAccessStaticMemberTypes() throws InteropException {
        Object res = INTEROP.readMember(obj, "XYPlus");
        assertTrue("It is truffle object", res instanceof TruffleObject);
        Class<?> c = asJavaObject(Class.class, (TruffleObject) res);
        assertSame(XYPlus.class, c);
    }

    @Test
    public void canCreateMemberTypeInstances() throws InteropException {
        Object type = INTEROP.readMember(obj, "Zed");
        assertTrue("Type is a truffle object", type instanceof TruffleObject);
        TruffleObject truffleType = (TruffleObject) type;
        Object objInst = INTEROP.instantiate(truffleType, 22);
        assertTrue("Created instance is a truffle object", objInst instanceof TruffleObject);
        Object res = asJavaObject(Object.class, (TruffleObject) objInst);
        assertTrue("Instance is of correct type", res instanceof Zed);
        assertEquals("Constructor was invoked", 22, ((Zed) res).val);
    }

    @Test
    public void canListStaticTypes() throws InteropException {
        Object type = INTEROP.getMembers(obj);
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
        assertFalse(INTEROP.isMemberExisting(obj, "Nonstatic"));
    }

    @Test
    public void staticInnerTypeIsNotWritable() {
        assertTrue("Key exists", INTEROP.isMemberExisting(obj, "Zed"));
        assertTrue("Key readable", INTEROP.isMemberReadable(obj, "Zed"));
        assertFalse("Key NOT writable", INTEROP.isMemberModifiable(obj, "Zed"));
    }

    @Test(expected = com.oracle.truffle.api.interop.UnknownIdentifierException.class)
    public void nonpublicTypeNotvisible() throws InteropException {
        assertFalse("Non-public member type not visible", INTEROP.isMemberReadable(obj, "NonStaticInterface"));

        Object type = INTEROP.getMembers(obj);
        assertTrue("Type is a truffle object", type instanceof TruffleObject);
        String[] names = asJavaObject(String[].class, (TruffleObject) type);
        assertEquals("Non-public member type not enumerated", -1, Arrays.asList(names).indexOf("NonStaticInterface"));

        INTEROP.readMember(obj, "NonStaticInterface");
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
