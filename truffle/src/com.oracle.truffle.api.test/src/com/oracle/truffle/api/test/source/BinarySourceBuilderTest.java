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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Test;

import com.oracle.truffle.api.source.MissingMIMETypeException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.Builder;

public class BinarySourceBuilderTest {

    @Test
    public void assignMimeTypeAndIdentityForBinarySource() throws RuntimeException {
        byte[] bytes = "Testing Source".getBytes();
        ByteBuffer sourceBytes = ByteBuffer.wrap(bytes);
        Builder<RuntimeException, RuntimeException, RuntimeException> builder = Source.newBuilder(sourceBytes).name("test.bc");
        Source s1 = builder.mimeType("content/unknown").build();
        assertEquals("Base type assigned", "content/unknown", s1.getMimeType());
        assertTrue("Original bytes preserved", Arrays.equals(bytes, s1.getBytes().array()));
        Source s2 = builder.mimeType("application/octet-stream").build();
        assertEquals("They have the same content", s1.getCharacters(), s2.getCharacters());
        assertEquals("Testing Source", s1.getCharacters());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void optionToPreserveOriginalFileBytes() throws Exception {
        File file = File.createTempFile("Hello", ".bc").getCanonicalFile();
        file.deleteOnExit();

        String text = "// Hellobc";
        Files.write(file.toPath(), text.getBytes());
        Source.Builder<IOException, RuntimeException, RuntimeException> builder = Source.newBuilder(file);
        Source s1 = builder.build(false);
        assertEquals("Original bytes not preserved", null, s1.getBytes());
        Source s2 = builder.build(true);
        assertTrue("Original bytes preserved", Arrays.equals(Files.readAllBytes(file.toPath()), s2.getBytes().array()));
        assertEquals("They have the same content", s1.getCharacters(), s2.getCharacters());
        assertEquals("// Hellobc", s1.getCharacters());
        assertNotEquals("But different bytes", s1.getBytes(), s2.getBytes());
        assertEquals("File URI", file.toURI(), s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void otherSourcesHaveNoBytes() throws Exception {
        String text = "// Hello";
        Source.Builder<IOException, MissingMIMETypeException, RuntimeException> builder = Source.newBuilder(new StringReader(text)).name("test.txt");
        Source s1 = builder.name("Hello").mimeType("text/plain").build();
        assertNull("No bytes preserved for reader", s1.getBytes());

        final String code = "test code";
        final String description = "test description";
        final Source literal = Source.newBuilder(code).name(description).mimeType("content/unknown").build();
        assertNull("No bytes preserved for literal source", literal.getBytes());

        File file = File.createTempFile("Hello", ".java");
        file.deleteOnExit();
        Source.Builder<IOException, RuntimeException, RuntimeException> builder2 = Source.newBuilder(file.toURI().toURL()).name("Hello.java");
        Source s2 = builder.build();
        assertNull("No source preserved for URL sources", s2.getBytes());
    }
}
