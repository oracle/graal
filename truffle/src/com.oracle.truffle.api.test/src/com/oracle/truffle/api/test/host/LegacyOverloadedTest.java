/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class LegacyOverloadedTest extends ProxyLanguageEnvTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    private Object obj;
    private OverloadedTest.Data data;

    @Before
    public void initObjects() {
        data = new OverloadedTest.Data();
        obj = asTruffleObject(data);
    }

    @Test
    public void threeProperties() throws UnsupportedMessageException, Exception {
        Object ret = INTEROP.getMembers(obj);
        List<?> list = context.asValue(ret).as(List.class);
        int numX = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equals("x")) {
                numX++;
            }
        }
        assertEquals("3 (overloaded) properties: " + list, 3, numX);
        assertEquals("x", list.get(0));
    }

    @Test
    public void readAndWriteField() throws InteropException {
        data.x = 11;
        assertEquals(11, INTEROP.readMember(obj, "x"));

        INTEROP.writeMember(obj, "x", 12);
        assertEquals(12, data.x);

        INTEROP.writeMember(obj, "x", new UnboxableToInt(13));
        assertEquals(13, data.x);
    }

    @Test
    public void callGetterAndSetter() throws InteropException {
        data.x = 11;
        assertEquals(22.0, INTEROP.invokeMember(obj, "x"));

        INTEROP.invokeMember(obj, "x", 10);
        assertEquals(20, data.x);

        INTEROP.invokeMember(obj, "x", new UnboxableToInt(21));
        assertEquals(42, data.x);
    }

    @Test
    public void testOverloadingTruffleObjectArg() throws InteropException {
        INTEROP.invokeMember(obj, "x", new UnboxableToInt(21));
        assertEquals(42, data.x);
        INTEROP.invokeMember(obj, "x", env.asBoxedGuestValue(10));
        assertEquals(20, data.x);
        INTEROP.invokeMember(obj, "x", 10);
        assertEquals(20, data.x);
    }

    @Test
    public void testOverloadingNumber() throws InteropException {
        OverloadedTest.Num num = new OverloadedTest.Num();
        TruffleObject numobj = asTruffleObject(num);
        INTEROP.invokeMember(numobj, "x", new UnboxableToInt(21));
        assertEquals("int", num.parameter);
        INTEROP.invokeMember(numobj, "x", asTruffleObject(new AtomicInteger(22)));
        assertEquals("Number", num.parameter);
        INTEROP.invokeMember(numobj, "x", asTruffleObject(BigInteger.TEN));
        assertEquals("int", num.parameter);
        INTEROP.invokeMember(numobj, "x", asTruffleObject(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        assertEquals("BigInteger", num.parameter);
    }

    @Test
    public void testVarArgs() throws InteropException {
        TruffleObject stringClass = asTruffleHostSymbol(String.class);
        assertEquals("bla", INTEROP.invokeMember(stringClass, "format", "bla"));
        assertEquals("42", INTEROP.invokeMember(stringClass, "format", "%d", 42));
        assertEquals("1337", INTEROP.invokeMember(stringClass, "format", "%d%d", 13, 37));
    }

    @Test
    public void testGenericReturnTypeBridgeMethod() throws InteropException {
        TruffleObject thing = asTruffleObject(new OverloadedTest.ActualRealThingWithIdentity());
        assertEquals(42, INTEROP.invokeMember(thing, "getId"));
    }

    @Test
    public void testWidening() throws InteropException {
        OverloadedTest.Num num = new OverloadedTest.Num();
        TruffleObject numobj = asTruffleObject(num);
        INTEROP.invokeMember(numobj, "d", (byte) 42);
        assertEquals("int", num.parameter);
        INTEROP.invokeMember(numobj, "d", (short) 42);
        assertEquals("int", num.parameter);
        INTEROP.invokeMember(numobj, "d", 42);
        assertEquals("int", num.parameter);

        INTEROP.invokeMember(numobj, "d", 42.1f);
        assertEquals("double", num.parameter);
        INTEROP.invokeMember(numobj, "d", 42.1d);
        assertEquals("double", num.parameter);
        INTEROP.invokeMember(numobj, "d", 0x8000_0000L);
        assertEquals("double", num.parameter);

        INTEROP.invokeMember(numobj, "d", 42L);
        assertEquals("int", num.parameter);

        INTEROP.invokeMember(numobj, "f", 42L);
        assertEquals("int", num.parameter);
    }

    @Test
    public void testNarrowing() throws InteropException {
        OverloadedTest.Num num = new OverloadedTest.Num();
        TruffleObject numobj = asTruffleObject(num);
        INTEROP.invokeMember(numobj, "f", 42.5f);
        assertEquals("float", num.parameter);
        INTEROP.invokeMember(numobj, "f", 42.5d);
        assertEquals("float", num.parameter);
    }

    @Test
    public void testPrimitive() throws InteropException {
        TruffleObject sample = asTruffleObject(new OverloadedTest.Sample());
        for (int i = 0; i < 2; i++) {
            assertEquals("int,boolean", INTEROP.invokeMember(sample, "m1", 42, true));
            assertEquals("double,String", INTEROP.invokeMember(sample, "m1", 42, "asdf"));
        }
        for (int i = 0; i < 2; i++) {
            assertEquals("int,boolean", INTEROP.invokeMember(sample, "m1", 42, true));
            assertEquals("double,Object", INTEROP.invokeMember(sample, "m1", 4.2, true));
        }
    }

    @Test
    public void testClassVsInterface() throws InteropException {
        TruffleObject pool = asTruffleObject(new OverloadedTest.Pool());
        TruffleObject concrete = asTruffleObject(new OverloadedTest.Concrete());
        TruffleObject handler = asTruffleObject(new FunctionalInterfaceTest.TestExecutable());
        assertEquals(OverloadedTest.Concrete.class.getName(), INTEROP.invokeMember(pool, "prepare1", "select", concrete, handler));
        assertEquals(OverloadedTest.Concrete.class.getName(), INTEROP.invokeMember(pool, "prepare2", "select", handler, concrete));
    }

    @Test
    public void testClassVsInterface2() throws InteropException {
        TruffleObject pool = asTruffleObject(new OverloadedTest.Pool());
        TruffleObject thandler = asTruffleObject(new FunctionalInterfaceTest.TestExecutable());
        TruffleObject chandler = asTruffleObject(new OverloadedTest.CHander());
        assertEquals(OverloadedTest.CHander.class.getName(), INTEROP.invokeMember(pool, "prepare3", "select", chandler, thandler));
        TruffleObject proxied = new AsCollectionsTest.MapBasedTO(Collections.singletonMap("handle", new FunctionalInterfaceTest.TestExecutable()));
        assertEquals(OverloadedTest.IHandler.class.getName(), INTEROP.invokeMember(pool, "prepare3", "select", proxied, thandler));
    }

    @Test
    public void testClassVsInterface3() throws InteropException {
        TruffleObject pool = asTruffleObject(new OverloadedTest.Pool());
        TruffleObject thandler = asTruffleObject(new FunctionalInterfaceTest.TestExecutable());
        TruffleObject chandler = asTruffleObject(new OverloadedTest.CHander());
        TruffleObject concrete = asTruffleObject(new OverloadedTest.Concrete());
        assertEquals(OverloadedTest.IHandler.class.getName(), INTEROP.invokeMember(pool, "prepare4", "select", chandler, 42));
        assertEquals(OverloadedTest.IHandler.class.getName(), INTEROP.invokeMember(pool, "prepare4", "select", thandler, 42));
        assertEquals(OverloadedTest.Concrete.class.getName(), INTEROP.invokeMember(pool, "prepare4", "select", concrete, 42));
    }

}
