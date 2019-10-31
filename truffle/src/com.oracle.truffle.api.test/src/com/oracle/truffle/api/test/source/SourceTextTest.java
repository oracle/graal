/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
    private final Source lineDelimitersSource = Source.newBuilder("", "\rA\r\rB\nC\n\nD\r\nE\r\n\r\nF", "lineDelimiters").build();

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
    public void lineDelimiters0() {
        assertEquals(10, lineDelimitersSource.getLineCount());
    }

    @Test
    public void lineDelimiters1() {
        int[] lineLengths = new int[]{0, 1, 0, 1, 1, 0, 1, 1, 0, 1};
        for (int i = 0; i < lineLengths.length; i++) {
            assertEquals("Wrong length of line " + (i + 1), lineLengths[i], lineDelimitersSource.getLineLength(i + 1));
        }
    }

    @Test
    public void lineDelimiters2() {
        int[] lineNumbers = new int[]{1, 2, 2, 3, 4, 4, 5, 5, 6, 7, 7, 7, 8, 8, 8, 9, 9, 10, 10};
        for (int i = 0; i < lineNumbers.length; i++) {
            assertEquals("Wrong line number at " + i, lineNumbers[i], lineDelimitersSource.getLineNumber(i));
        }
    }

    @Test
    public void lineDelimiters3() {
        int[] lineStartOffsets = new int[]{0, 1, 3, 4, 6, 8, 9, 12, 15, 17};
        for (int i = 0; i < lineStartOffsets.length; i++) {
            assertEquals("Wrong start offset of line " + (i + 1), lineStartOffsets[i], lineDelimitersSource.getLineStartOffset(i + 1));
        }
    }

    @Test
    public void nameAndShortNameNoPath() {
        final String name = "/tmp/hi.txt";
        Source source = Source.newBuilder("", "Hi", name).build();
        assertEquals(name, source.getName());
    }
}
