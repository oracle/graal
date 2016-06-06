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

import com.oracle.truffle.api.profiles.SeparateClassloaderTestRunner;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SeparateClassloaderTestRunner.class)
public class SourceBuilderTest {
    @Test
    public void assignMimeTypeAndIdentity() {
        Source s1 = Source.fromText("// a comment\n", "Empty comment");
        assertNull("No mime type assigned", s1.getMimeType());
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void assignMimeTypeAndIdentityForApppendable() {
        Source s1 = Source.fromAppendableText("<stdio>");
        assertEquals("<stdio>", s1.getName());
        assertEquals("<stdio>", s1.getShortName());
        assertEquals("Appendable path is based on name", "<stdio>", s1.getPath());
        assertNull("No mime type assigned", s1.getMimeType());
        s1.appendCode("// Hello");
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
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
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
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
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
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

        String nonCannonical = file.getParent() + File.separatorChar + ".." + File.separatorChar + file.getParentFile().getName() + File.separatorChar + file.getName();
        final File nonCannonicalFile = new File(nonCannonical);
        assertTrue("Exists, as it is the same file", nonCannonicalFile.exists());
        final Source.Builder<Source> builder = Source.newFromFile(new File(nonCannonical));

        Source s1 = builder.build();
        assertEquals("Path is cannonicalized", file.getPath(), s1.getPath());
        assertEquals("Just the name of the file", file.getName(), s1.getShortName());
        assertEquals("Name is short", file.getName(), s1.getName());
        assertEquals("Recognized as Java", "text/x-java", s1.getMimeType());
        Source s2 = builder.mimeType("text/x-c").build();
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertEquals("File URI is used from cannonicalized form", file.toURI(), s1.getURI());
        assertEquals("Sources with different MIME type has the same URI", s1.getURI(), s2.getURI());
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
        assertEquals("File URI", file.toURI(), s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    /* Test currently fails on Sparc. */
    @Ignore
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
        assertEquals("Recognized as Java", "text/x-java", s1.getMimeType());
        Source s2 = s1.withMimeType("text/x-c");
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertEquals("File URI", file.toURI(), s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void literalSources() throws IOException {
        final String code = "test code";
        final String description = "test description";
        final Source literal = Source.fromText(code, description);
        assertEquals(literal.getName(), description);
        assertEquals(literal.getShortName(), description);
        assertEquals(literal.getCode(), code);
        assertEquals("Non-appendable path is based on name", description, literal.getPath());
        assertNull(literal.getURL());
        assertNotNull("Every source must have URI", literal.getURI());
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
        assertEquals("File URI", new File(path).toURI(), source1.getURI());
        assertEquals("File sources with different content have the same URI", source1.getURI(), source2.getURI());
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
        assertEquals("File URI", new File("test.input").getAbsoluteFile().toURI(), source1.getURI());
        assertEquals("File sources with different content have the same URI", source1.getURI(), source2.getURI());
    }

    @Test
    public void withName() throws Exception {
        final String tmpName = "/tmp/hi.tmp";
        final String realName = "/path/hi.txt";

        Source orig = Source.fromText("Hi", tmpName);
        assertEquals(tmpName, orig.getName());
        Source foundOrig = Source.find(tmpName);
        assertEquals(orig, foundOrig);

        Source source = orig.withName(realName);
        assertEquals(realName, source.getName());

        Source foundSource = Source.find(realName);
        assertSame(source, foundSource);

        WeakReference<Source> refOrig = new WeakReference<>(orig);
        orig = null;
        foundOrig = null;

        assertGC("The source can disappear", refOrig);

        Source notFoundSource = Source.find(tmpName);
        assertNull("Original source isn't there anymore", notFoundSource);
    }

    @Test
    public void withShortName() throws Exception {
        Source orig = Source.fromText("Hi", "/tmp/hi.tmp");
        assertEquals("/tmp/hi.tmp", orig.getShortName());
        Source source = orig.withShortName("hi.txt");
        assertEquals("hi.txt", source.getShortName());
    }

    @Test
    public void withPath() throws Exception {
        Source orig = Source.fromText("Hi", "/tmp/hi.tmp");
        assertEquals("Path is derived from name", "/tmp/hi.tmp", orig.getPath());
        Source source = orig.withPath("c:\\temp\\hi.txt");
        assertEquals("c:\\temp\\hi.txt", source.getPath());
    }

    /* Test currently fails on Sparc. */
    @Ignore
    @Test
    public void relativeURL() throws Exception {
        URL resource = SourceSnippets.class.getResource("sample.js");
        assertNotNull("Sample js file found", resource);
        SourceSnippets.fromURL();
    }

    /* Test currently fails on Sparc. */
    @Test
    public void fileSample() throws Exception {
        File sample = File.createTempFile("sample", ".java");
        sample.deleteOnExit();
        SourceSnippets.fromFile(sample.getParentFile(), sample.getName());
        sample.delete();
    }

    @Test
    public void stringSample() throws Exception {
        Source source = SourceSnippets.fromAString();
        assertNotNull("Every source must have URI", source.getURI());
    }

    @Test
    public void readerSample() throws Exception {
        Source source = SourceSnippets.fromReader();
        assertNotNull("Every source must have URI", source.getURI());
    }

    @Test
    public void fileWithReload() throws Exception {
        File file = File.createTempFile("ChangeMe", ".java");
        file.deleteOnExit();

        String text;
        try (FileWriter w = new FileWriter(file)) {
            text = "// Hello";
            w.write(text);
        }

        Source original = Source.fromFileName(file.getPath());
        assertEquals(text, original.getCode());

        String newText;
        try (FileWriter w = new FileWriter(file)) {
            newText = "// Hello World!";
            w.write(newText);
        }

        Source still = Source.fromFileName(file.getPath(), false);
        assertEquals(original, still);
        assertEquals(text, still.getCode());
        assertEquals(file.toURI(), still.getURI());

        Source reloaded = Source.fromFileName(file.getPath(), true);
        assertNotEquals(original, reloaded);
        assertEquals("New source has the new text", newText, reloaded.getCode());
        assertEquals("New source has the same URI", reloaded.getURI(), still.getURI());

        assertEquals("Old source1 remains unchanged", text, original.getCode());
        assertEquals("Old source2 remains unchanged", text, still.getCode());
    }

    @Test
    public void normalSourceIsntInternal() throws IOException {
        Source source = Source.newWithText("anything").mimeType("text/plain").build();

        assertFalse("Not internal", source.isInternal());
    }

    @Test
    public void markSourceAsInternal() throws IOException {
        Source source = Source.newWithText("anything internal").mimeType("text/plain").internal(true).build();

        assertTrue("This source is internal", source.isInternal());
    }

    public void subSourceHashAndEquals() {
        Source src = Source.fromText("One Two Three", "counting.en");
        Source one = Source.subSource(src, 0, 3);
        Source two = Source.subSource(src, 4, 3);
        Source three = Source.subSource(src, 8);

        Source oneSnd = Source.subSource(src, 0, 3);
        Source twoSnd = Source.subSource(src, 4, 3);
        Source threeSnd = Source.subSource(src, 8);

        assertNotEquals("One: " + one.getCode() + " two: " + two.getCode(), one, two);
        assertNotEquals(three, two);
        assertNotEquals(one, three);

        assertNotEquals(oneSnd, twoSnd);

        assertEquals(one, oneSnd);
        assertEquals(two, twoSnd);
        assertEquals(three, threeSnd);

        assertEquals(one.hashCode(), oneSnd.hashCode());
        assertEquals(two.hashCode(), twoSnd.hashCode());
        assertEquals(three.hashCode(), threeSnd.hashCode());

        assertEquals(src.getURI(), one.getURI());
        assertEquals(src.getURI(), two.getURI());
        assertEquals(src.getURI(), three.getURI());
    }

    @Test
    public void subSourceFromTwoFiles() throws Exception {
        File f1 = File.createTempFile("subSource", ".js");
        File f2 = File.createTempFile("subSource", ".js");

        try (FileWriter w = new FileWriter(f1)) {
            w.write("function test() {\n" + "  return 1;\n" + "}\n");
        }

        try (FileWriter w = new FileWriter(f2)) {
            w.write("function test() {\n" + "  return 1;\n" + "}\n");
        }

        Source s1 = Source.fromFileName(f1.getPath());
        Source s2 = Source.fromFileName(f2.getPath());

        assertNotEquals("Different sources", s1, s2);
        assertEquals("But same content", s1.getCode(), s2.getCode());

        Source sub1 = Source.subSource(s1, 0, 8);
        Source sub2 = Source.subSource(s2, 0, 8);

        assertNotEquals("Different sub sources", sub1, sub2);
        assertEquals("with the same content", sub1.getCode(), sub2.getCode());
        assertNotEquals("and different hash", sub1.hashCode(), sub2.hashCode());

        assertEquals(f1.toURI(), s1.getURI());
        assertEquals(s1.getURI(), sub1.getURI());
        assertEquals(f2.toURI(), s2.getURI());
        assertEquals(s2.getURI(), sub2.getURI());
    }

    private static void assertGC(String msg, WeakReference<?> ref) {
        for (int i = 0; i < 100; i++) {
            if (ref.get() == null) {
                return;
            }
            System.gc();
            System.runFinalization();
        }
        fail(msg + " ref: " + ref.get());
    }
}
