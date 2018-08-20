/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.source.Source;

public class SourceTextTest {

    private final Source emptySource = Source.newBuilder("", "", "emptySource").build();
    private final Source emptyLineSource = Source.newBuilder("", "\n", "emptyLineSource").build();
    private final Source shortSource = Source.newBuilder("", "01", "shortSource").build();
    private final Source longSource = Source.newBuilder("", "01234\n67\n9\n", "long").build();

    @Test
    public void emptyTextTest0() {
        assertEquals(emptySource.getLineCount(), 0);
    }

    @Test
    public void nameName() {
        assertEquals("emptySource", emptySource.getName());
    }

    @Test
    public void noPath() {
        assertNull(emptySource.getPath());
    }

    @Test
    public void uriEndsWithName() {
        assertTrue(emptySource.getURI().toString().endsWith("emptySource"));
    }

    @Test
    public void emptyTextTest1() {
        assertEquals(1, emptySource.getLineNumber(0));
    }

    @Test
    public void emptyTextTest2() {
        assertEquals(1, emptySource.getColumnNumber(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest3() {
        emptySource.getLineNumber(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest4() {
        emptySource.getLineStartOffset(0);
    }

    @Test
    public void emptyTextTest5() {
        assertEquals(0, emptySource.getLineStartOffset(1));
    }

    @Test
    public void emptyTextTest6() {
        assertEquals(0, emptySource.getLineLength(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest7() {
        emptySource.getLineStartOffset(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest8() {
        emptySource.getLineLength(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTextTest9() {
        emptySource.getLineLength(0);
    }

    @Test
    public void emptyLineTest0() {
        assertEquals(emptyLineSource.getLineCount(), 1);
        assertEquals(emptyLineSource.getLineNumber(0), 1);
        assertEquals(emptyLineSource.getLineStartOffset(1), 0);
        assertEquals(emptyLineSource.getColumnNumber(0), 1);
        assertEquals(emptyLineSource.getLineLength(1), 0);
    }

    @Test
    public void emptyLineTest1() {
        assertEquals(2, emptyLineSource.getLineNumber(1));
    }

    @Test
    public void emptyLineTest2() {
        assertEquals(1, emptyLineSource.getLineStartOffset(2));
    }

    @Test
    public void emptyLineTest3() {
        assertEquals(1, emptyLineSource.getColumnNumber(1));
    }

    @Test
    public void emptyLineTest4() {
        assertEquals(0, emptyLineSource.getLineLength(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyLineTest5() {
        emptyLineSource.getLineNumber(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyLineTest6() {
        emptyLineSource.getColumnNumber(2);
    }

    @Test
    public void shortTextTest0() {

        assertEquals(shortSource.getLineCount(), 1);

        assertEquals(shortSource.getLineNumber(0), 1);
        assertEquals(shortSource.getLineStartOffset(1), 0);
        assertEquals(shortSource.getColumnNumber(0), 1);

        assertEquals(shortSource.getLineNumber(1), 1);
        assertEquals(shortSource.getColumnNumber(1), 2);

        assertEquals(shortSource.getLineLength(1), 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest1() {
        shortSource.getLineNumber(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest2() {
        shortSource.getColumnNumber(-1);
    }

    @Test
    public void shortTextTest3() {
        assertEquals(1, shortSource.getLineNumber(2));
    }

    @Test
    public void shortTextTest4() {
        assertEquals(3, shortSource.getColumnNumber(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest5() {
        shortSource.getLineStartOffset(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest6() {
        shortSource.getLineLength(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest7() {
        shortSource.getLineNumber(3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortTextTest8() {
        shortSource.getColumnNumber(3);
    }

    @Test
    public void longTextTest0() {

        assertEquals(longSource.getLineCount(), 3);

        assertEquals(longSource.getLineNumber(0), 1);
        assertEquals(longSource.getLineStartOffset(1), 0);
        assertEquals(longSource.getColumnNumber(0), 1);

        assertEquals(longSource.getLineNumber(4), 1);
        assertEquals(longSource.getColumnNumber(4), 5);

        assertEquals(longSource.getLineNumber(5), 1); // newline
        assertEquals(longSource.getColumnNumber(5), 6); // newline
        assertEquals(longSource.getLineLength(1), 5);

        assertEquals(longSource.getLineNumber(6), 2);
        assertEquals(longSource.getLineStartOffset(2), 6);
        assertEquals(longSource.getColumnNumber(6), 1);

        assertEquals(longSource.getLineNumber(7), 2);
        assertEquals(longSource.getColumnNumber(7), 2);

        assertEquals(longSource.getLineNumber(8), 2); // newline
        assertEquals(longSource.getLineNumber(8), 2); // newline
        assertEquals(longSource.getLineLength(2), 2);

        assertEquals(longSource.getLineNumber(9), 3);
        assertEquals(longSource.getLineStartOffset(3), 9);
        assertEquals(longSource.getColumnNumber(9), 1);

        assertEquals(longSource.getLineNumber(10), 3); // newline
        assertEquals(longSource.getColumnNumber(10), 2); // newline
        assertEquals(longSource.getLineLength(3), 1);

    }

    @Test
    public void longTextTest1() {
        assertEquals(4, longSource.getLineNumber(11));
    }

    @Test
    public void longTextTest2() {
        assertEquals(1, longSource.getColumnNumber(11));
    }

    @Test
    public void longTextTest3() {
        assertEquals(11, longSource.getLineStartOffset(4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void longTextTest4() {
        longSource.getLineStartOffset(5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void longTextTest5() {
        longSource.getLineNumber(12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void longTextTest6() {
        longSource.getColumnNumber(12);
    }

    @Test
    public void longTextTest7() {
        assertEquals(0, longSource.getLineLength(4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void longTextTest8() {
        longSource.getLineLength(5);
    }

    @Test
    public void nameAndShortNameNoPath() {
        final String name = "/tmp/hi.txt";
        Source source = Source.newBuilder("", "Hi", name).build();
        assertEquals(name, source.getName());
    }
}
