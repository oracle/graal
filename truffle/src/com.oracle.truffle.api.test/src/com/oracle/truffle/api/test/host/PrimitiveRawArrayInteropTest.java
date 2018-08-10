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
