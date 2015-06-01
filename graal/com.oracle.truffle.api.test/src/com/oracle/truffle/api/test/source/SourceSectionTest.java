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
package com.oracle.truffle.api.test.source;

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.source.*;

public class SourceSectionTest {

    private final Source emptySource = Source.fromText("", null);

    private final Source emptyLineSource = Source.fromText("\n", null);

    private final Source shortSource = Source.fromText("01", null);

    private final Source longSource = Source.fromText("01234\n67\n9\n", null);

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

    @Ignore
    @Test
    public void emptyLineTest0a() {
        SourceSection section = emptyLineSource.createSection("test", 0, 0);
        assertEquals(section.getEndLine(), 1);
        assertEquals(section.getEndColumn(), 1);
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

    @Ignore
    @Test
    public void emptyLineTest2() {
        SourceSection section = emptyLineSource.createSection("test", 1, 0);
        assertNotNull(section);
        assertEquals(section.getCode(), "");
        assertEquals(section.getCharIndex(), 1);
        assertEquals(section.getCharLength(), 0);
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

}
