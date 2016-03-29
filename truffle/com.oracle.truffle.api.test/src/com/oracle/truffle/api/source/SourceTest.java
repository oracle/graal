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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class SourceTest {
    @Test
    public void assignMimeTypeAndIdentity() {
        Source s1 = Source.fromText("// a comment\n", "Empty comment");
        assertNull("No mime type assigned", s1.getMimeType());
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
    }

    @Test
    public void assignMimeTypeAndIdentityForApppendable() {
        Source s1 = Source.fromAppendableText("<stdio>");
        assertNull("No mime type assigned", s1.getMimeType());
        s1.appendCode("// Hello");
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
    }

    @Test
    public void assignMimeTypeAndIdentityForBytes() {
        String text = "// Hello";
        Source s1 = Source.fromBytes(text.getBytes(StandardCharsets.UTF_8), "Hello", StandardCharsets.UTF_8);
        assertNull("No mime type assigned", s1.getMimeType());
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
    }

    @Test
    public void assignMimeTypeAndIdentityForReader() throws IOException {
        String text = "// Hello";
        Source s1 = Source.fromReader(new StringReader(text), "Hello");
        assertNull("No mime type assigned", s1.getMimeType());
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
    }

    @Test
    public void assignMimeTypeAndIdentityForFile() throws IOException {
        File file = File.createTempFile("Hello", ".java");
        file.deleteOnExit();

        String text;
        try (FileWriter w = new FileWriter(file)) {
            text = "// Hello";
            w.write(text);
        }

        // JDK8 default fails on OS X: https://bugs.openjdk.java.net/browse/JDK-8129632
        Source s1 = Source.fromFileName(file.getPath()).withMimeType("text/x-java");
        assertEquals("Recognized as Java", "text/x-java", s1.getMimeType());
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
    }

    @Test
    public void assignMimeTypeAndIdentityForVirtualFile() throws IOException {
        File file = File.createTempFile("Hello", ".java");
        file.deleteOnExit();

        String text = "// Hello";

        // JDK8 default fails on OS X: https://bugs.openjdk.java.net/browse/JDK-8129632
        Source s1 = Source.fromFileName(text, file.getPath()).withMimeType("text/x-java");
        assertEquals("Recognized as Java", "text/x-java", s1.getMimeType());
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
    }

    @Test
    public void assignMimeTypeAndIdentityForURL() throws IOException {
        File file = File.createTempFile("Hello", ".java");
        file.deleteOnExit();

        String text;
        try (FileWriter w = new FileWriter(file)) {
            text = "// Hello";
            w.write(text);
        }

        Source s1 = Source.fromURL(file.toURI().toURL(), "Hello.java");
        assertEquals("Threated as plain", "text/plain", s1.getMimeType());
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
    }

    @Test
    public void literalSources() throws IOException {
        final String code = "test code";
        final String description = "test description";
        final Source literal = Source.fromText(code, description);
        assertEquals(literal.getName(), description);
        assertEquals(literal.getShortName(), description);
        assertEquals(literal.getCode(), code);
        assertNull(literal.getPath());
        assertNull(literal.getURL());
        final char[] buffer = new char[code.length()];
        assertEquals(literal.getReader().read(buffer), code.length());
        assertEquals(new String(buffer), code);
    }

    @Test
    public void clientManagedSourceChange() throws IOException {
        final String path = "test.input";
        final String code1 = "test\ntest";
        final String code2 = "test\ntest\nlonger\ntest";
        final Source source1 = Source.fromFileName(code1, path);
        assertEquals(source1.getCode(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.fromFileName(code2, path);
        assertEquals(source2.getCode(), code2);
        assertEquals(source2.getLineNumber(code2.length() - 1), 4);
    }

    @Test
    public void clientManagedSourceChangeAbsolute() throws IOException {
        final String path = new File("test.input").getAbsolutePath();
        final String code1 = "test\ntest";
        final String code2 = "test\ntest\nlonger\ntest";
        final Source source1 = Source.fromFileName(code1, path);
        assertEquals(source1.getCode(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.fromFileName(code2, path);
        assertEquals(source2.getCode(), code2);
        assertEquals(source2.getLineNumber(code2.length() - 1), 4);
    }
}
