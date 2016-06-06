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
package com.oracle.truffle.api.source;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class BytesSourceSectionTest {

    @Test
    public void testSectionsFromLineNumberASCII() {
        final byte[] bytes = "foo\nbar\nbaz\n".getBytes(StandardCharsets.US_ASCII);
        final Source source = Source.fromBytes(bytes, "description", StandardCharsets.US_ASCII);
        assertEquals("description", source.getName());
        assertEquals("description", source.getShortName());
        assertEquals("description", source.getPath());
        assertEquals("foo", source.createSection("identifier", 1).getCode());
        assertEquals("bar", source.createSection("identifier", 2).getCode());
        assertEquals("baz", source.createSection("identifier", 3).getCode());
    }

    @Test
    public void testSectionsFromOffsetsASCII() {
        final byte[] bytes = "foo\nbar\nbaz\n".getBytes(StandardCharsets.US_ASCII);
        final Source source = Source.fromBytes(bytes, "description", StandardCharsets.US_ASCII);
        assertEquals("foo", source.createSection("identifier", 0, 3).getCode());
        assertEquals("bar", source.createSection("identifier", 4, 3).getCode());
        assertEquals("baz", source.createSection("identifier", 8, 3).getCode());
    }

    @Test
    public void testOffset() {
        final byte[] bytes = "xxxfoo\nbar\nbaz\nxxx".getBytes(StandardCharsets.US_ASCII);
        final Source source = Source.fromBytes(bytes, 3, bytes.length - 6, "description", StandardCharsets.US_ASCII);
        assertEquals("foo", source.createSection("identifier", 0, 3).getCode());
        assertEquals("bar", source.createSection("identifier", 4, 3).getCode());
        assertEquals("baz", source.createSection("identifier", 8, 3).getCode());
    }

    @Test
    public void testEqualsImpliesSameHasCode() {
        final byte[] bytes = "Ahoj".getBytes(StandardCharsets.US_ASCII);
        final byte[] clone = bytes.clone();
        final Source sourceOrig = Source.fromBytes(bytes, "description", StandardCharsets.US_ASCII);
        final Source sourceClone = Source.fromBytes(clone, "description", StandardCharsets.US_ASCII);

        assertEquals("The sources are equal", sourceClone, sourceOrig);
        assertEquals("Equal sources have to have the same hash", sourceClone.hashCode(), sourceOrig.hashCode());
    }

    @Test
    public void testOffsetWithInternationalChars() {
        final String horse = "xxxP\u0159\u00EDli\u0161 \u017Elu\u0165ou\u010Dk\u00FD k\u016F\u0148 \u00FAp\u011Bl \u010F\u00E1belsk\u00E9 \u00F3dy.xxx";
        final byte[] bytes = horse.getBytes(StandardCharsets.UTF_8);
        final Source source = Source.fromBytes(bytes, 3, bytes.length - 6, "description", StandardCharsets.UTF_8);

        assertEquals(source.getLength(), horse.length() - 6);
        String[] words = source.getCode().split(" ");

        String sndWord = source.getCode(words[0].length() + 1, words[1].length());
        assertEquals(words[1], sndWord);
    }
}
