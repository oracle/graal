/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

@SuppressWarnings("deprecation")
public class SourceSectionLegacyTest {

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
        longSource.createSection(1, 6, 1);
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
        File sample = File.createTempFile("hello", ".txt");
        sample.deleteOnExit();
        try (FileWriter w = new FileWriter(sample)) {
            w.write("Hello world!");
        }
        Source complexHello = Source.newBuilder(sample).build();
        SourceSection helloTo = complexHello.createSection(6, 5);
        assertEquals("world", helloTo.getCharacters());

        try (FileWriter w = new FileWriter(sample)) {
            w.write("Hi world!");
        }
        Source simpleHi = Source.newBuilder(sample).build();
        SourceSection hiTo = simpleHi.createSection(3, 5);
        assertEquals("world", hiTo.getCharacters());

        assertEquals("Previously allocated sections remain the same", "world", helloTo.getCharacters());

        sample.delete();
    }
}
