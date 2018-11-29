/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;

public class PrimitiveRawArrayInteropTest extends ProxyLanguageEnvTest {

    private Object[] objArr;
    private byte[] byteArr;
    private short[] shortArr;
    private int[] intArr;
    private long[] longArr;
    private float[] floatArr;
    private double[] doubleArr;
    private char[] charArr;
    private boolean[] boolArr;

    public Object arr(int type) {
        switch (type) {
            case 0:
                return objArr;
            case 1:
                return byteArr;
            case 2:
                return shortArr;
            case 3:
                return intArr;
            case 4:
                return longArr;
            case 5:
                return floatArr;
            case 6:
                return doubleArr;
            case 7:
                return charArr;
            case 8:
                return boolArr;
            case 666:
                throw new SimulatedDeath();
            default:
                throw new WrongArgument(type);
        }
    }

    public static final class WrongArgument extends RuntimeException {
        private static final long serialVersionUID = 1L;

        final int type;

        public WrongArgument(int type) {
            this.type = type;
        }
    }

    public interface RawInterop {
        List<Object> arr(int type);
    }

    private TruffleObject obj;
    private RawInterop interop;

    @Override
    public void before() {
        super.before();
        obj = asTruffleObject(this);
        interop = asJavaObject(RawInterop.class, obj);
    }

    @Test
    public void everyThingIsNull() {
        assertNull(interop.arr(0));
        assertNull(interop.arr(1));
        assertNull(interop.arr(2));
        assertNull(interop.arr(3));
        assertNull(interop.arr(4));
        assertNull(interop.arr(5));
        assertNull(interop.arr(6));
        assertNull(interop.arr(7));
        assertNull(interop.arr(8));
    }

    @Test
    public void exceptionIsPropagated() {
        try {
            assertNull(interop.arr(30));
        } catch (PolyglotException hostException) {
            assertTrue("Expected HostException but got: " + hostException.getClass(), hostException.isHostException());
            WrongArgument wrongArgument = (WrongArgument) hostException.asHostException();
            assertEquals(30, wrongArgument.type);
            return;
        }
        fail("WrongArgument should have been thrown");
    }

    @Test
    public void errorIsPropagated() {
        try {
            assertNull(interop.arr(666));
        } catch (PolyglotException ex) {
            assertTrue(ex.isInternalError());
            return;
        }
        fail("SimulatedDeath should have been thrown");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void stringAsList() {
        objArr = new Object[]{"Hello", "World", "!"};
        List<Object> list = interop.arr(0);
        assertEquals("Three elements", 3, list.size());
        assertEquals("Hello", list.get(0));
        assertEquals("World", list.get(1));
        assertEquals("!", list.get(2));

        list.set(1, "there");
        assertEquals("there", objArr[1]);

        list.set(0, null);
        assertNull("set to null", objArr[0]);

        List rawList = list;
        rawList.set(0, 42);
        assertEquals("safelly changed", 42, objArr[0]);
    }

    @Test
    public void charOp() {
        charArr = new char[]{'A', 'h', 'o', 'j'};
        assertEquals('j', (char) interop.arr(7).get(3));
        interop.arr(7).set(3, 'y');

        String s = new String(charArr);
        assertEquals("Ahoy", s);
    }

    @Test
    public void boolOp() {
        boolArr = new boolean[]{true, false};

        interop.arr(8).set(1, !(Boolean) interop.arr(8).get(1));

        assertEquals(boolArr[0], boolArr[1]);
    }

    @Test
    public void byteSum() {
        byteArr = new byte[]{(byte) 1, (byte) 2, (byte) 3};
        assertSum("Sum is OK", 6, interop.arr(1));
    }

    @Test
    public void shortSum() {
        shortArr = new short[]{(short) 1, (short) 2, (short) 3};
        assertSum("Sum is OK", 6, interop.arr(2));
    }

    @Test
    public void intSum() {
        intArr = new int[]{1, 2, 3};
        assertSum("Sum is OK", 6, interop.arr(3));
    }

    @Test
    public void longSum() {
        longArr = new long[]{1, 2, 3};
        assertSum("Sum is OK", 6, interop.arr(4));
    }

    @Test
    public void floatSum() {
        floatArr = new float[]{1, 2, 3};
        assertSum("Sum is OK", 6, interop.arr(5));
    }

    @Test
    public void doubleSum() {
        doubleArr = new double[]{1, 2, 3};
        assertSum("Sum is OK", 6, interop.arr(6));
    }

    @Test
    public void writeSomebyteSum() {
        byteArr = new byte[]{(byte) 10, (byte) 2, (byte) 3};
        interop.arr(1).set(0, (byte) 1);
        assertSum("Sum is OK", 6, interop.arr(1));
    }

    @Test
    public void writeSomeshortSum() {
        shortArr = new short[]{(short) 10, (short) 2, (short) 3};
        interop.arr(2).set(0, (short) 1);
        assertSum("Sum is OK", 6, interop.arr(2));
    }

    @Test
    public void writeSomeintSum() {
        intArr = new int[]{10, 2, 3};
        interop.arr(3).set(0, 1);
        assertSum("Sum is OK", 6, interop.arr(3));
    }

    @Test
    public void writeSomelongSum() {
        longArr = new long[]{10, 2, 3};
        interop.arr(4).set(0, (long) 1);
        assertSum("Sum is OK", 6, interop.arr(4));
    }

    @Test
    public void writeSomefloatSum() {
        floatArr = new float[]{10, 2, 3};
        interop.arr(5).set(0, (float) 1);
        assertSum("Sum is OK", 6, interop.arr(5));
    }

    @Test
    public void writeSomedoubleSum() {
        doubleArr = new double[]{10, 2, 3};
        interop.arr(6).set(0, (double) 1);
        assertSum("Sum is OK", 6, interop.arr(6));
    }

    private static void assertSum(String msg, double expected, List<? extends Object> numbers) {
        double v = 0.0;
        for (Object o : numbers) {
            if (o instanceof Number) {
                Number n = (Number) o;
                v += n.doubleValue();
            }
        }
        assertEquals(msg, expected, v, 0.05);
    }

    private static class SimulatedDeath extends ThreadDeath {
        private static final long serialVersionUID = 1L;

        @Override
        public String getMessage() {
            return "simulation";
        }
    }
}
