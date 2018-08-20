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

import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;

public class PrimitiveArrayInteropTest extends ProxyLanguageEnvTest {
    public Object[] stringArr;
    public byte[] byteArr;
    public short[] shortArr;
    public int[] intArr;
    public long[] longArr;
    public float[] floatArr;
    public double[] doubleArr;
    public char[] charArr;
    public boolean[] boolArr;

    public interface ExactMatchInterop {
        List<String> stringArr();

        List<Byte> byteArr();

        List<Short> shortArr();

        List<Integer> intArr();

        List<Long> longArr();

        List<Float> floatArr();

        List<Double> doubleArr();

        List<Character> charArr();

        List<Boolean> boolArr();
    }

    private TruffleObject obj;
    private ExactMatchInterop interop;

    @Override
    public void before() {
        super.before();
        obj = asTruffleObject(this);
        interop = asJavaObject(ExactMatchInterop.class, obj);
    }

    @Test
    public void everyThingIsNull() {
        assertNull(interop.stringArr());
        assertNull(interop.byteArr());
        assertNull(interop.shortArr());
        assertNull(interop.intArr());
        assertNull(interop.longArr());
        assertNull(interop.floatArr());
        assertNull(interop.doubleArr());
        assertNull(interop.charArr());
        assertNull(interop.boolArr());
    }

    @Test
    public void stringAsList() {
        stringArr = new Object[]{"Hello", "World", "!"};
        List<String> list = interop.stringArr();
        assertEquals("Three elements", 3, list.size());
        assertEquals("Hello", list.get(0));
        assertEquals("World", list.get(1));
        assertEquals("!", list.get(2));

        list.set(1, "there");
        assertEquals("there", stringArr[1]);

        list.set(0, null);
        assertNull("set to null", stringArr[0]);
    }

    @Test
    public void charOp() {
        charArr = new char[]{'A', 'h', 'o', 'j'};
        assertEquals('j', (char) interop.charArr().get(3));
        interop.charArr().set(3, 'y');

        String s = new String(charArr);
        assertEquals("Ahoy", s);
    }

    @Test
    public void boolOp() {
        boolArr = new boolean[]{true, false};

        interop.boolArr().set(1, !interop.boolArr().get(1));

        assertEquals(boolArr[0], boolArr[1]);
    }

    @Test
    public void byteSum() {
        byteArr = new byte[]{(byte) 1, (byte) 2, (byte) 3};
        assertSum("Sum is OK", 6, interop.byteArr());
    }

    @Test
    public void shortSum() {
        shortArr = new short[]{(short) 1, (short) 2, (short) 3};
        assertSum("Sum is OK", 6, interop.shortArr());
    }

    @Test
    public void intSum() {
        intArr = new int[]{1, 2, 3};
        assertSum("Sum is OK", 6, interop.intArr());
    }

    @Test
    public void longSum() {
        longArr = new long[]{1, 2, 3};
        assertSum("Sum is OK", 6, interop.longArr());
    }

    @Test
    public void floatSum() {
        floatArr = new float[]{1, 2, 3};
        assertSum("Sum is OK", 6, interop.floatArr());
    }

    @Test
    public void doubleSum() {
        doubleArr = new double[]{1, 2, 3};
        assertSum("Sum is OK", 6, interop.doubleArr());
    }

    @Test
    public void writeSomebyteSum() {
        byteArr = new byte[]{(byte) 10, (byte) 2, (byte) 3};
        interop.byteArr().set(0, (byte) 1);
        assertSum("Sum is OK", 6, interop.byteArr());
    }

    @Test
    public void writeSomeshortSum() {
        shortArr = new short[]{(short) 10, (short) 2, (short) 3};
        interop.shortArr().set(0, (short) 1);
        assertSum("Sum is OK", 6, interop.shortArr());
    }

    @Test
    public void writeSomeintSum() {
        intArr = new int[]{10, 2, 3};
        interop.intArr().set(0, 1);
        assertSum("Sum is OK", 6, interop.intArr());
    }

    @Test
    public void writeSomelongSum() {
        longArr = new long[]{10, 2, 3};
        interop.longArr().set(0, (long) 1);
        assertSum("Sum is OK", 6, interop.longArr());
    }

    @Test
    public void writeSomefloatSum() {
        floatArr = new float[]{10, 2, 3};
        interop.floatArr().set(0, (float) 1);
        assertSum("Sum is OK", 6, interop.floatArr());
    }

    @Test
    public void writeSomedoubleSum() {
        doubleArr = new double[]{10, 2, 3};
        interop.doubleArr().set(0, (double) 1);
        assertSum("Sum is OK", 6, interop.doubleArr());
    }

    private static void assertSum(String msg, double expected, List<? extends Number> numbers) {
        double v = 0.0;
        for (Number n : numbers) {
            v += n.doubleValue();
        }
        assertEquals(msg, expected, v, 0.05);
    }
}
