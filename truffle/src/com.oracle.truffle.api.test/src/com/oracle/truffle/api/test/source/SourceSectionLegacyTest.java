/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileWriter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings("deprecation")
public class SourceSectionLegacyTest extends AbstractPolyglotTest {

    private final Source emptySource = Source.newBuilder("").name("emptySource").mimeType("content/unknown").build();

    private final Source emptyLineSource = Source.newBuilder("\n").name("emptyLineSource").mimeType("content/unknown").build();

    private final Source shortSource = Source.newBuilder("01").name("shortSource").mimeType("content/unknown").build();

    private final Source longSource = Source.newBuilder("01234\n67\n9\n").name("long").mimeType("content/unknown").build();

    @Test
    public void emptySourceTest0() {
        SourceSection section = emptySource.createSection(0, 0);
        assertNotNull(section);
        assertEquals(section.getCharacters(), "");
    }

    @Test
    public void emptyLineTest0() {
        SourceSection section = emptyLineSource.createSection(0, 0);
        assertNotNull(section);
        assertEquals(section.getCharacters(), "");
        assertEquals(section.getCharIndex(), 0);
        assertEquals(section.getCharLength(), 0);
        assertEquals(section.getStartLine(), 1);
        assertEquals(section.getStartColumn(), 1);

        SourceSection other = emptyLineSource.createSection(0, 0);
        assertTrue(section.equals(other));
        assertEquals(other.hashCode(), section.hashCode());
    }

    @Test
    public void emptyLineTest1() {
        SourceSection section = emptyLineSource.createSection(0, 1);
        assertNotNull(section);
        assertEquals(section.getCharacters(), "\n");
        assertEquals(section.getCharIndex(), 0);
        assertEquals(section.getCharLength(), 1);
        assertEquals(section.getStartLine(), 1);
        assertEquals(section.getStartColumn(), 1);
        assertEquals(section.getEndLine(), 1);
        assertEquals(section.getEndColumn(), 1);

        SourceSection other = emptyLineSource.createSection(0, 1);
        assertTrue(section.equals(other));
        assertEquals(other.hashCode(), section.hashCode());
    }

    @Test
    public void emptySourceTest1() {
        SourceSection section = emptySource.createSection(0, 0);
        assertNotNull(section);
        assertEquals(section.getCharIndex(), 0);
        assertEquals(section.getCharLength(), 0);
        assertEquals(section.getStartLine(), 1);
        assertEquals(section.getEndLine(), 1);
        assertEquals(section.getStartColumn(), 1);
        assertEquals(section.getEndColumn(), 1);
        assertEquals("", section.getCharacters());

        SourceSection other = emptySource.createSection(0, 0);
        assertTrue(section.equals(other));
        assertEquals(other.hashCode(), section.hashCode());
    }

    @Test
    public void emptySourceSectionOnLongSource() {
        SourceSection section = longSource.createSection(longSource.getCharacters().length() - 1, 0);
        assertNotNull(section);
        assertEquals(longSource.getCharacters().length() - 1, section.getCharIndex());
        assertEquals(0, section.getCharLength(), 0);
        assertEquals(3, section.getStartLine());
        assertEquals(3, section.getEndLine());
        assertEquals(2, section.getStartColumn());
        assertEquals(2, section.getEndColumn());

        SourceSection other = longSource.createSection(longSource.getCharacters().length() - 1, 0);
        assertTrue(section.equals(other));
        assertEquals(other.hashCode(), section.hashCode());
    }

    @Test
    public void emptySectionTest2() {
        SourceSection section = shortSource.createSection(0, 0);
        assertNotNull(section);
        assertEquals(section.getCharacters(), "");
    }

    @Test
    public void emptySectionTest3() {
        SourceSection section = longSource.createSection(0, 0);
        assertNotNull(section);
        assertEquals(section.getCharacters(), "");
    }

    @Test
    public void testGetCode() {
        assertEquals("01234", longSource.createSection(0, 5).getCharacters());
        assertEquals("67", longSource.createSection(6, 2).getCharacters());
        assertEquals("9", longSource.createSection(9, 1).getCharacters());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange1() {
        longSource.createSection(9, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange2() {
        longSource.createSection(-1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange3() {
        longSource.createSection(1, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange7() {
        // out of range with length
        longSource.createSection(longSource.getCharacters().length() - 4, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange8() {
        // out of range with charIndex
        longSource.createSection(longSource.getCharacters().length(), 1);

    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange9() {
        // out of range with charIndex
        longSource.createSection(longSource.getCharacters().length() + 1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange10() {
        longSource.createSection(4, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange11() {
        longSource.createSection(-1, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange12() {
        longSource.createSection(1, 7, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange13() {
        shortSource.createSection(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange14() {
        longSource.createSection(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange15() {
        longSource.createSection(5);
    }

    @Test
    public void testUnavailable() {
        SourceSection section = longSource.createUnavailableSection();
        assertEquals(0, section.getCharEndIndex());
        assertEquals(0, section.getCharIndex());
        assertEquals(0, section.getCharLength());

        assertEquals(1, section.getStartColumn());
        assertEquals(1, section.getEndColumn());
        assertEquals(1, section.getStartLine());
        assertEquals(1, section.getEndLine());
        assertSame(longSource, section.getSource());
        assertFalse(section.isAvailable());

        assertEquals("", section.getCharacters());
        assertNotNull(section.toString());

        // Unavailable sections must not be equals otherwise builtins
        // will be considered all identical if they share the same source.
        SourceSection other = longSource.createUnavailableSection();
        assertFalse(section.equals(other));
        assertNotEquals(other.hashCode(), section.hashCode());

        SourceSection other2 = shortSource.createUnavailableSection();
        assertFalse(section.equals(other2));
        assertNotEquals(other2.hashCode(), section.hashCode());
    }

    @Test
    public void testEOF1() {
        SourceSection section = shortSource.createSection(shortSource.getLength(), 0);
        assertNotNull(section);
        assertEquals(section.getCharIndex(), shortSource.getLength());
        assertEquals(section.getCharLength(), 0);
        assertEquals(section.getStartLine(), 1);
        assertEquals(section.getEndLine(), 1);
        assertEquals(section.getStartColumn(), 3);
        assertEquals(section.getEndColumn(), 3);
        assertEquals("", section.getCharacters());

        SourceSection other = shortSource.createSection(shortSource.getLength(), 0);
        assertTrue(section.equals(other));
        assertEquals(other.hashCode(), section.hashCode());
    }

    @Test
    public void testEOF2() {
        SourceSection section = longSource.createSection(longSource.getLength(), 0);
        assertNotNull(section);
        assertEquals(section.getCharIndex(), longSource.getLength());
        assertEquals(section.getCharLength(), 0);
        assertEquals(section.getStartLine(), 4);
        assertEquals(section.getEndLine(), 4);
        assertEquals(section.getStartColumn(), 1);
        assertEquals(section.getEndColumn(), 1);
        assertEquals("", section.getCharacters());

        SourceSection other = longSource.createSection(longSource.getLength(), 0);
        assertTrue(section.equals(other));
        assertEquals(other.hashCode(), section.hashCode());
    }

    @Test
    public void testFinalNL() {
        int sourceLength = longSource.getCharacters().length();
        SourceSection section = longSource.createSection(4);
        assertNotNull(section);
        assertEquals(sourceLength, section.getCharIndex());
        assertEquals(0, section.getCharLength());
        assertEquals(4, section.getStartLine());
        assertEquals(4, section.getEndLine());
        assertEquals(1, section.getStartColumn());
        assertEquals(1, section.getEndColumn());
        assertEquals("", section.getCharacters());
        assertTrue(section.isAvailable());
    }

    @Test
    public void onceObtainedAlwaysTheSame() throws Exception {
        setupEnv();
        File sample = File.createTempFile("hello", ".txt");
        sample.deleteOnExit();
        try (FileWriter w = new FileWriter(sample)) {
            w.write("Hello world!");
        }
        Source complexHello = Source.newBuilder(sample).mimeType("").build();
        SourceSection helloTo = complexHello.createSection(6, 5);
        assertEquals("world", helloTo.getCharacters());

        try (FileWriter w = new FileWriter(sample)) {
            w.write("Hi world!");
        }
        Source simpleHi = Source.newBuilder(sample).mimeType("").build();
        SourceSection hiTo = simpleHi.createSection(3, 5);
        assertEquals("world", hiTo.getCharacters());

        assertEquals("Previously allocated sections remain the same", "world", helloTo.getCharacters());

        sample.delete();
    }
}
