/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.test.server;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.util.Arrays;

import org.graalvm.tools.lsp.server.types.Position;
import org.graalvm.tools.lsp.server.types.Range;
import org.graalvm.tools.lsp.server.types.TextDocumentContentChangeEvent;
import org.graalvm.tools.lsp.server.utils.SourceUtils;

import com.oracle.truffle.api.source.Source;

public class TextDocumentContentChangeTest {

    private static void assertDocumentChanges(String oldText, String replacement, Range range, String expectedText) {
        TextDocumentContentChangeEvent event = TextDocumentContentChangeEvent.create(replacement).setRange(range).setRangeLength(replacement.length());
        String actualText = SourceUtils.applyTextDocumentChanges(Arrays.asList(event), createSource(oldText), null, null);
        assertEquals(expectedText, actualText);
    }

    private static Source createSource(String oldText) {
        return Source.newBuilder("dummy", oldText, TextDocumentContentChangeTest.class.getSimpleName()).build();
    }

    @Test
    public void applyTextDocumentChanges01() {
        assertDocumentChanges("", "a", Range.create(Position.create(0, 0), Position.create(0, 0)), "a");
    }

    @Test
    public void applyTextDocumentChanges02() {
        assertDocumentChanges("a", "b", Range.create(Position.create(0, 0), Position.create(0, 1)), "b");
    }

    @Test
    public void applyTextDocumentChanges03() {
        assertDocumentChanges("abc", "1", Range.create(Position.create(0, 1), Position.create(0, 1)), "a1bc");
    }

    @Test
    public void applyTextDocumentChanges04() {
        assertDocumentChanges("\n", "1", Range.create(Position.create(0, 0), Position.create(1, 0)), "1");
    }

    @Test
    public void applyTextDocumentChanges05() {
        assertDocumentChanges("abc\nefg\n\nhij", "#", Range.create(Position.create(0, 0), Position.create(1, 0)), "#efg\n\nhij");
    }

    @Test
    public void applyTextDocumentChanges06() {
        assertDocumentChanges("abc\nefg\n\nhij", "#", Range.create(Position.create(2, 0), Position.create(3, 0)), "abc\nefg\n#hij");
    }

    @Test
    public void applyTextDocumentChanges07() {
        assertDocumentChanges("abc\nefg\n\n", "#\n", Range.create(Position.create(3, 0), Position.create(3, 0)), "abc\nefg\n\n#\n");
    }

    @Test
    public void applyTextDocumentChanges08() {
        assertDocumentChanges("abc\nefg\n\n", "a", null, "a");
    }

    @Test
    public void applyTextDocumentChangesList() {
        String oldText = "";

        String replacement1 = "a";
        TextDocumentContentChangeEvent event1 = TextDocumentContentChangeEvent.create(replacement1).setRange(Range.create(Position.create(0, 0), Position.create(0, 0))).setRangeLength(
                        replacement1.length());
        String replacement2 = "c";
        TextDocumentContentChangeEvent event2 = TextDocumentContentChangeEvent.create(replacement2).setRange(Range.create(Position.create(0, 1), Position.create(0, 1))).setRangeLength(
                        replacement2.length());
        String replacement3 = "b";
        TextDocumentContentChangeEvent event3 = TextDocumentContentChangeEvent.create(replacement3).setRange(Range.create(Position.create(0, 1), Position.create(0, 1))).setRangeLength(
                        replacement3.length());
        String replacement4 = "\nefg\nhij";
        TextDocumentContentChangeEvent event4 = TextDocumentContentChangeEvent.create(replacement4).setRange(Range.create(Position.create(0, 3), Position.create(0, 3))).setRangeLength(
                        replacement4.length());
        String replacement5 = "####";
        TextDocumentContentChangeEvent event5 = TextDocumentContentChangeEvent.create(replacement5).setRange(Range.create(Position.create(1, 0), Position.create(2, 0))).setRangeLength(
                        replacement5.length());
        String replacement6 = "\n";
        TextDocumentContentChangeEvent event6 = TextDocumentContentChangeEvent.create(replacement6).setRange(Range.create(Position.create(1, 7), Position.create(1, 7))).setRangeLength(
                        replacement6.length());

        String actualText = SourceUtils.applyTextDocumentChanges(Arrays.asList(event1, event2, event3, event4, event5, event6), createSource(oldText), null, null);
        assertEquals("abc\n####hij\n", actualText);
    }
}
