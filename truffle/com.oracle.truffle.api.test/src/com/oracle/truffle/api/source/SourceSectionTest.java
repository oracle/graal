/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class SourceSectionTest {

    private final Source emptySource = Source.fromText("", null);

    private final Source emptyLineSource = Source.fromText("\n", null);

    private final Source shortSource = Source.fromText("01", null);

    private final Source longSource = Source.fromText("01234\n67\n9\n", "long");

    public void emptySourceTest0() {
        SourceSection section = emptySource.createSection("test", 0, 0);
        assertNotNull(section);
        assertEquals(section.getCode(), "");
    }

    @Test
    public void emptyLineTest0() {
        SourceSection section = emptyLineSource.createSection("test", 0, 0);
        assertNotNull(section);
        assertEquals(section.getCode(), "");
        assertEquals(section.getCharIndex(), 0);
        assertEquals(section.getCharLength(), 0);
        assertEquals(section.getStartLine(), 1);
        assertEquals(section.getStartColumn(), 1);
    }

    @Test
    public void emptyLineTest1() {
        SourceSection section = emptyLineSource.createSection("test", 0, 1);
        assertNotNull(section);
        assertEquals(section.getCode(), "\n");
        assertEquals(section.getCharIndex(), 0);
        assertEquals(section.getCharLength(), 1);
        assertEquals(section.getStartLine(), 1);
        assertEquals(section.getStartColumn(), 1);
        assertEquals(section.getEndLine(), 1);
        assertEquals(section.getEndColumn(), 1);
    }

    @Test
    public void emptySectionTest2() {
        SourceSection section = shortSource.createSection("test", 0, 0);
        assertNotNull(section);
        assertEquals(section.getCode(), "");
    }

    @Test
    public void emptySectionTest3() {
        SourceSection section = longSource.createSection("test", 0, 0);
        assertNotNull(section);
        assertEquals(section.getCode(), "");
    }

    @Test
    public void testGetCode() {
        assertEquals("01234", longSource.createSection("test", 0, 5).getCode());
        assertEquals("67", longSource.createSection("test", 6, 2).getCode());
        assertEquals("9", longSource.createSection("test", 9, 1).getCode());
    }

    @Test
    public void testGetShortDescription() {
        assertEquals("long:1", longSource.createSection("test", 0, 5).getShortDescription());
        assertEquals("long:2", longSource.createSection("test", 6, 2).getShortDescription());
        assertEquals("long:3", longSource.createSection("test", 9, 1).getShortDescription());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange1() {
        longSource.createSection("test", 9, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange2() {
        longSource.createSection("test", -1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange3() {
        longSource.createSection("test", 1, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange4() {
        longSource.createSection("test", 3, 1, 9, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange5() {
        longSource.createSection("test", 1, 1, -1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfRange6() {
        longSource.createSection("test", 1, 1, 1, -1);
    }
}
