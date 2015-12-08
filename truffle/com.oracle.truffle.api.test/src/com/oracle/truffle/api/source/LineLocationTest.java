/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.Test;

public class LineLocationTest {

    @Test
    public void lineLocationLiteralTest() {
        final Source source = Source.fromText("line 1\nline2\n", "lineLocationTest");
        final LineLocation line1 = source.createLineLocation(1);
        final LineLocation line2 = source.createLineLocation(2);
        assertEquals(line1.getLineNumber(), 1);
        assertNotEquals(line1, line2);
        assertEquals(line1, source.createLineLocation(1));
        assertEquals(line1.compareTo(line2), -1);
        assertEquals(line1.compareTo(source.createLineLocation(1)), 0);
    }

    @Test
    public void lineLocationPathTest() throws IOException {
        final Source s1 = createSourceFile("Hello1 line 1\nline2\n", "LineLocation1");
        final Source s2 = createSourceFile("Hello2 line 1\nline2\n", "LineLocation2");
        assertNotNull(s1);
        assertNotNull(s2);
        final LineLocation s1l1 = s1.createLineLocation(1);
        final LineLocation s1l2 = s1.createLineLocation(2);
        final LineLocation s2l1 = s2.createLineLocation(1);
        assertEquals(s1l1.compareTo(s1l2), -1);
        assertEquals(s1l1.compareTo(s2l1), -1);
        assertEquals(s1l2.compareTo(s2l1), -1);
    }

    @Test
    public void lineLocationMixedTest() throws IOException {
        final Source s1 = createSourceFile("same contents", "Testfile");
        final Source s2 = Source.fromText("same contents", "literal test source");
        final LineLocation s1l1 = s1.createLineLocation(1);
        final LineLocation s2l1 = s2.createLineLocation(1);
        assertEquals(s1l1.compareTo(s2l1), 0);
        assertEquals(s2l1.compareTo(s1l1), 0);
    }

    private static Source createSourceFile(String contents, String name) throws IOException {
        final File file = File.createTempFile(name, ".txt");
        try (FileWriter w = new FileWriter(file)) {
            w.write(contents);
        }
        file.deleteOnExit();
        return Source.fromFileName(file.getAbsolutePath());
    }
}
