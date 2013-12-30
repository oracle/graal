/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.test.utilities;

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.source.*;

public class TextMapTest {

    final TextMap emptyTextMap = new TextMap("");

    final TextMap emptyLineMap = new TextMap("\n");

    private final TextMap shortMap = new TextMap("01");

    private final TextMap longMap = new TextMap("01234\n67\n9\n");

    @Test
    public void emptyTextTest0() {
        assertEquals(emptyTextMap.lineCount(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest1() {
        emptyTextMap.offsetToLine(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest2() {
        emptyTextMap.offsetToCol(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest3() {
        emptyTextMap.lineStartOffset(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest4() {
        emptyTextMap.lineStartOffset(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest5() {
        emptyTextMap.lineStartOffset(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest6() {
        emptyTextMap.lineLength(1);
    }

    @Test
    public void emptyLineTest0() {
        assertEquals(emptyLineMap.lineCount(), 1);
        assertEquals(emptyLineMap.offsetToLine(0), 1);
        assertEquals(emptyLineMap.lineStartOffset(1), 0);
        assertEquals(emptyLineMap.offsetToCol(0), 1);
        assertEquals(emptyLineMap.lineLength(1), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyLineTest1() {
        emptyLineMap.offsetToLine(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyLineTest2() {
        emptyLineMap.lineStartOffset(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyLineTest3() {
        emptyLineMap.offsetToCol(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyLineTest4() {
        emptyLineMap.lineLength(2);
    }

    @Test
    public void shortTextTest0() {

        assertEquals(shortMap.lineCount(), 1);

        assertEquals(shortMap.offsetToLine(0), 1);
        assertEquals(shortMap.lineStartOffset(1), 0);
        assertEquals(shortMap.offsetToCol(0), 1);

        assertEquals(shortMap.offsetToLine(1), 1);
        assertEquals(shortMap.offsetToCol(1), 2);

        assertEquals(shortMap.lineLength(1), 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest1() {
        shortMap.offsetToLine(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest2() {
        shortMap.offsetToCol(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest3() {
        shortMap.offsetToLine(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest4() {
        shortMap.offsetToCol(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest5() {
        shortMap.lineStartOffset(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest6() {
        shortMap.lineLength(2);
    }

    @Test
    public void longTextTest0() {

        assertEquals(longMap.lineCount(), 3);

        assertEquals(longMap.offsetToLine(0), 1);
        assertEquals(longMap.lineStartOffset(1), 0);
        assertEquals(longMap.offsetToCol(0), 1);

        assertEquals(longMap.offsetToLine(4), 1);
        assertEquals(longMap.offsetToCol(4), 5);

        assertEquals(longMap.offsetToLine(5), 1); // newline
        assertEquals(longMap.offsetToCol(5), 6); // newline
        assertEquals(longMap.lineLength(1), 5);

        assertEquals(longMap.offsetToLine(6), 2);
        assertEquals(longMap.lineStartOffset(2), 6);
        assertEquals(longMap.offsetToCol(6), 1);

        assertEquals(longMap.offsetToLine(7), 2);
        assertEquals(longMap.offsetToCol(7), 2);

        assertEquals(longMap.offsetToLine(8), 2); // newline
        assertEquals(longMap.offsetToLine(8), 2); // newline
        assertEquals(longMap.lineLength(2), 2);

        assertEquals(longMap.offsetToLine(9), 3);
        assertEquals(longMap.lineStartOffset(3), 9);
        assertEquals(longMap.offsetToCol(9), 1);

        assertEquals(longMap.offsetToLine(10), 3); // newline
        assertEquals(longMap.offsetToCol(10), 2); // newline
        assertEquals(longMap.lineLength(3), 1);

    }

    @Test(expected = IllegalArgumentException.class)
    public void longTextTest1() {
        longMap.offsetToLine(11);
    }

    @Test(expected = IllegalArgumentException.class)
    public void longTextTest2() {
        longMap.offsetToCol(11);
    }

    @Test(expected = IllegalArgumentException.class)
    public void longTextTest3() {
        longMap.lineStartOffset(4);
    }

}
