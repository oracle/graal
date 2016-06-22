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
import java.net.URL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SeparateClassloaderTestRunner.class)
public class SourceBuilderTest {
    @Test
    public void assignMimeTypeAndIdentity() {
        Source.Builder<RuntimeException, MissingMIMETypeException, RuntimeException> builder = Source.newBuilder("// a comment\n").name("Empty comment");
        Source s1 = builder.mimeType("content/unknown").build();
        assertEquals("No mime type assigned", "content/unknown", s1.getMimeType());
        Source s2 = builder.mimeType("text/x-c").build();
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void assignMimeTypeAndIdentityForApppendable() {
        Source s1 = Source.fromAppendableText("<stdio>");
        assertEquals("<stdio>", s1.getName());
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
    public void assignMimeTypeAndIdentityForReader() throws IOException {
        String text = "// Hello";
        Source.Builder<IOException, MissingMIMETypeException, RuntimeException> builder = Source.newBuilder(new StringReader(text)).name("test.txt");
        Source s1 = builder.name("Hello").mimeType("text/plain").build();
        assertEquals("Base type assigned", "text/plain", s1.getMimeType());
        Source s2 = builder.mimeType("text/x-c").build();
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void assignMimeTypeAndIdentityForFile() throws IOException {
        File file = File.createTempFile("Hello", ".java").getCanonicalFile();
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
        Source.Builder<IOException, RuntimeException, RuntimeException> builder = Source.newBuilder(nonCannonicalFile);

        Source s1 = builder.build();
        assertEquals("Path is cannonicalized", file.getPath(), s1.getPath());
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
    public void assignMimeTypeAndIdentityForVirtualFile() throws Exception {
        File file = File.createTempFile("Hello", ".java").getCanonicalFile();
        file.deleteOnExit();

        String text = "// Hello";
        Source.Builder<RuntimeException, RuntimeException, RuntimeException> builder = Source.newBuilder(file).content(text).mimeType("text/x-java");
        // JDK8 default fails on OS X: https://bugs.openjdk.java.net/browse/JDK-8129632
        Source s1 = builder.build();
        assertEquals("Recognized as Java", "text/x-java", s1.getMimeType());
        Source s2 = builder.mimeType("text/x-c").build();
        assertEquals("They have the same content", s1.getCode(), s2.getCode());
        assertEquals("// Hello", s1.getCode());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertEquals("File URI", file.toURI(), s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void noIOWhenContentSpecified() {
        File file = new File("some.js");

        String text = "// Hello";

        Source source = Source.newBuilder(file).content(text).build();
        assertEquals("The content has been changed", text, source.getCode());
        assertNotNull("Mime type specified", source.getMimeType());
        assertTrue("Recognized as JavaScript", source.getMimeType().endsWith("/javascript"));
        assertEquals("some.js", source.getName());
    }

    @Test
    public void fromTextWithFileURI() {
        File file = new File("some.js");

        String text = "// Hello";

        Source source = Source.newBuilder(text).uri(file.toURI()).mimeType("plain/text").name("another.js").build();
        assertEquals("The content has been changed", text, source.getCode());
        assertNotNull("Mime type specified", source.getMimeType());
        assertEquals("Assigned MIME type", "plain/text", source.getMimeType());
        assertEquals("another.js", source.getName());
        assertEquals("Using the specified URI", file.toURI(), source.getURI());
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
        Source.Builder<IOException, RuntimeException, RuntimeException> builder = Source.newBuilder(file.toURI().toURL()).name("Hello.java");

        Source s1 = builder.build();
        assertEquals("Recognized as Java", "text/x-java", s1.getMimeType());
        Source s2 = builder.mimeType("text/x-c").build();
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
        final Source literal = Source.newBuilder(code).name(description).mimeType("content/unknown").build();
        assertEquals(literal.getName(), description);
        assertEquals(literal.getCode(), code);
        assertNull(literal.getURL());
        assertNotNull("Every source must have URI", literal.getURI());
        final char[] buffer = new char[code.length()];
        assertEquals(literal.getReader().read(buffer), code.length());
        assertEquals(new String(buffer), code);
    }

    @Test
    public void clientManagedSourceChange() {
        final String path = "test.input";
        final String code1 = "test\ntest";
        final String code2 = "test\ntest\nlonger\ntest";
        final Source source1 = Source.newBuilder(new File(path)).content(code1).mimeType("content/unknown").build();
        assertEquals(source1.getCode(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.newBuilder(new File(path)).content(code2).mimeType("content/unknown").build();
        assertEquals(source2.getCode(), code2);
        assertEquals(source2.getLineNumber(code2.length() - 1), 4);
        assertEquals("File URI", new File(path).toURI(), source1.getURI());
        assertEquals("File sources with different content have the same URI", source1.getURI(), source2.getURI());
    }

    @Test
    public void clientManagedSourceChangeAbsolute() {
        final String path = new File("test.input").getAbsolutePath();
        final String code1 = "test\ntest";
        final String code2 = "test\ntest\nlonger\ntest";
        final Source source1 = Source.newBuilder(new File(path)).content(code1).mimeType("x-application/input").build();
        assertEquals(source1.getCode(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.newBuilder(new File(path)).content(code2).mimeType("x-application/input").build();
        assertEquals(source2.getCode(), code2);
        assertEquals(source2.getLineNumber(code2.length() - 1), 4);
        assertEquals("File URI", new File("test.input").getAbsoluteFile().toURI(), source1.getURI());
        assertEquals("File sources with different content have the same URI", source1.getURI(), source2.getURI());
    }

    @Test
    public void relativeURL() throws Exception {
        URL resource = SourceSnippets.class.getResource("sample.js");
        assertNotNull("Sample js file found", resource);
        SourceSnippets.fromURL();
    }

    @Test
    public void relativeURLWithOwnContent() throws Exception {
        URL resource = SourceSnippets.class.getResource("sample.js");
        assertNotNull("Sample js file found", resource);
        SourceSnippets.fromURLWithOwnContent();
    }

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
    public void whatAreTheDefaultValuesOfNewFromReader() throws Exception {
        StringReader r = new StringReader("Hi!");
        Source source = Source.newBuilder(r).name("almostEmpty").mimeType("text/plain").build();

        assertEquals("Hi!", source.getCode());
        assertEquals("almostEmpty", source.getName());
        assertNull(source.getPath());
        assertNotNull(source.getURI());
        assertEquals("truffle", source.getURI().getScheme());
        assertNull(source.getURL());
        assertEquals("text/plain", source.getMimeType());
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

        Source original = Source.newBuilder(file).build();
        assertEquals(text, original.getCode());

        String newText;
        try (FileWriter w = new FileWriter(file)) {
            newText = "// Hello World!";
            w.write(newText);
        }

        Source reloaded = Source.newBuilder(file).build();
        assertNotEquals(original, reloaded);
        assertEquals("New source has the new text", newText, reloaded.getCode());

        assertEquals("Old source1 remains unchanged", text, original.getCode());
    }

    @Test
    public void normalSourceIsNotInternal() {
        Source source = Source.newBuilder("anything").mimeType("text/plain").name("anyname").build();

        assertFalse("Not internal", source.isInternal());
    }

    @Test
    public void markSourceAsInternal() {
        Source source = Source.newBuilder("anything internal").mimeType("text/plain").name("internalsrc").internal().build();

        assertTrue("This source is internal", source.isInternal());
    }

    public void subSourceHashAndEquals() {
        Source src = Source.newBuilder("One Two Three").name("counting.en").mimeType("content/unknown").build();
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
        File f1 = File.createTempFile("subSource", ".js").getCanonicalFile();
        File f2 = File.createTempFile("subSource", ".js").getCanonicalFile();

        try (FileWriter w = new FileWriter(f1)) {
            w.write("function test() {\n" + "  return 1;\n" + "}\n");
        }

        try (FileWriter w = new FileWriter(f2)) {
            w.write("function test() {\n" + "  return 1;\n" + "}\n");
        }

        Source s1 = Source.newBuilder(f1).build();
        Source s2 = Source.newBuilder(f2).build();

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

    @Test
    public void throwsErrorNameCannotBeNull() {
        try {
            Source.newBuilder("Hi").name(null);
        } catch (NullPointerException ex) {
            return;
        }
        fail("Expecting NullPointerException");
    }

    @Test
    public void throwsErrorIfNameIsNull() {
        try {
            Source.newBuilder("Hi").mimeType("content/unknown").build();
        } catch (MissingNameException ex) {
            // OK
            return;
        }
        fail("Expecting MissingNameException");
    }

    @Test
    public void throwsErrorIfMIMETypeIsNull() {
        try {
            Source.newBuilder("Hi").name("unknown.txt").build();
        } catch (MissingMIMETypeException ex) {
            // OK
            return;
        }
        fail("Expecting MissingNameException");
    }

    @Test
    public void succeedsWithBothNameAndMIME() {
        Source src = Source.newBuilder("Hi").mimeType("content/unknown").name("unknown.txt").build();
        assertNotNull(src);
    }
}
