/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.*;

import org.junit.*;

import com.oracle.truffle.api.source.*;

public class BytesSourceSectionTest {

    @Test
    public void testSectionsFromLineNumberASCII() {
        final byte[] bytes = "foo\nbar\nbaz\n".getBytes(StandardCharsets.UTF_8);
        final Source source = Source.fromBytes(bytes, "description", new BytesDecoder.UTF8BytesDecoder());
        assertEquals("foo", source.createSection("identifier", 1).getCode());
        assertEquals("bar", source.createSection("identifier", 2).getCode());
        assertEquals("baz", source.createSection("identifier", 3).getCode());
    }

    @Test
    public void testSectionsFromOffsetsASCII() {
        final byte[] bytes = "foo\nbar\nbaz\n".getBytes(StandardCharsets.UTF_8);
        final Source source = Source.fromBytes(bytes, "description", new BytesDecoder.UTF8BytesDecoder());
        assertEquals("foo", source.createSection("identifier", 0, 3).getCode());
        assertEquals("bar", source.createSection("identifier", 4, 3).getCode());
        assertEquals("baz", source.createSection("identifier", 8, 3).getCode());
    }

    @Test
    public void testSectionsFromLineNumberUTF8() {
        // ☃ is three bytes in UTF8
        final byte[] bytes = "foo\n☃\nbaz\n".getBytes(StandardCharsets.UTF_8);
        final Source source = Source.fromBytes(bytes, "description", new BytesDecoder.UTF8BytesDecoder());
        assertEquals("foo", source.createSection("identifier", 1).getCode());
        assertEquals("☃", source.createSection("identifier", 2).getCode());
        assertEquals("baz", source.createSection("identifier", 3).getCode());
    }

    @Test
    public void testSectionsFromOffsetsUTF8() {
        // ☃ is three bytes in UTF8
        final byte[] bytes = "foo\n☃\nbaz\n".getBytes(StandardCharsets.UTF_8);
        final Source source = Source.fromBytes(bytes, "description", new BytesDecoder.UTF8BytesDecoder());
        assertEquals("foo", source.createSection("identifier", 0, 3).getCode());
        assertEquals("☃", source.createSection("identifier", 4, 3).getCode());
        assertEquals("baz", source.createSection("identifier", 8, 3).getCode());
    }

    @Test
    public void testOffset() {
        final byte[] bytes = "xxxfoo\nbar\nbaz\nxxx".getBytes(StandardCharsets.UTF_8);
        final Source source = Source.fromBytes(bytes, 3, bytes.length - 6, "description", new BytesDecoder.UTF8BytesDecoder());
        assertEquals("foo", source.createSection("identifier", 0, 3).getCode());
        assertEquals("bar", source.createSection("identifier", 4, 3).getCode());
        assertEquals("baz", source.createSection("identifier", 8, 3).getCode());
    }
}
