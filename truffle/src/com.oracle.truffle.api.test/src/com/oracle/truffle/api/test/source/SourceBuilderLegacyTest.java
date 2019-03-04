/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

/*
 * Legacy tests are necessary to make sure deprecated APIs don't change behavior.
 * To be removed with Source builder deprecations.
 */
@SuppressWarnings("deprecation")
public class SourceBuilderLegacyTest extends AbstractPolyglotTest {

    @Test
    public void assignMimeTypeAndIdentity() {
        Source.Builder<RuntimeException, com.oracle.truffle.api.source.MissingMIMETypeException, RuntimeException> builder = Source.newBuilder("// a comment\n").name("Empty comment");
        Source s1 = builder.mimeType("content/unknown").build();
        assertEquals("No mime type assigned", "content/unknown", s1.getMimeType());
        Source s2 = builder.mimeType("text/x-c").build();
        assertEquals("They have the same content", s1.getCharacters(), s2.getCharacters());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void assignMimeTypeAndIdentityForReader() throws IOException {
        String text = "// Hello";
        Source.Builder<IOException, com.oracle.truffle.api.source.MissingMIMETypeException, RuntimeException> builder = Source.newBuilder(new StringReader(text)).name("test.txt");
        Source s1 = builder.name("Hello").mimeType("text/plain").build();
        assertEquals("Base type assigned", "text/plain", s1.getMimeType());
        Source s2 = builder.mimeType("text/x-c").build();
        assertEquals("They have the same content", s1.getCharacters(), s2.getCharacters());
        assertEquals("// Hello", s1.getCharacters());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void assignMimeTypeAndIdentityForFile() throws IOException {
        setupEnv();
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
        assertEquals("They have the same content", s1.getCharacters(), s2.getCharacters());
        assertEquals("// Hello", s1.getCharacters());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertEquals("File URI is used from cannonicalized form", file.toURI(), s1.getURI());
        assertEquals("Sources with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void mimeTypeIsDetectedRandomBytes() throws IOException {
        setupEnv();
        File file = File.createTempFile("Hello", ".bin").getCanonicalFile();
        file.deleteOnExit();

        try (FileOutputStream w = new FileOutputStream(file)) {
            w.write(0x04);
            w.write(0x05);
        }

        Source source = Source.newBuilder(file).build();
        assertEither(source.getMimeType(), "content/unknown", "application/octet-stream", "text/plain", "application/macbinary");
    }

    @Test
    public void mimeTypeIsDetectedRandomBytesForURI() throws IOException {
        setupEnv();
        File file = File.createTempFile("Hello", ".bin").getCanonicalFile();
        file.deleteOnExit();

        try (FileOutputStream w = new FileOutputStream(file)) {
            w.write(0x04);
            w.write(0x05);
        }

        Source source = Source.newBuilder(file.toURI().toURL()).build();
        assertEither(source.getMimeType(), "content/unknown", "application/octet-stream", "text/plain", "application/macbinary");
    }

    @Test
    public void ioExceptionWhenFileDoesntExist() throws Exception {
        setupEnv();
        File file = File.createTempFile("Hello", ".java").getCanonicalFile();
        file.delete();
        assertFalse("Doesn't exist", file.exists());

        Source.Builder<IOException, RuntimeException, RuntimeException> builder = Source.newBuilder(file);

        Source s1 = null;
        try {
            s1 = builder.build();
        } catch (IOException e) {
            // OK, file doesn't exist
            return;
        }
        fail("No source should be created: " + s1);
    }

    @Test
    public void ioExceptionWhenReaderThrowsIt() throws Exception {
        final IOException ioEx = new IOException();
        Reader reader = new Reader() {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                throw ioEx;
            }

            @Override
            public void close() throws IOException {
            }
        };

        Source.Builder<IOException, RuntimeException, RuntimeException> builder = Source.newBuilder(reader).name("unloadable.txt").mimeType("text/plain");

        Source s1 = null;
        try {
            s1 = builder.build();
        } catch (IOException e) {
            Assert.assertSame(ioEx, e);
            return;
        }
        fail("No source should be created: " + s1);
    }

    @Test
    public void assignMimeTypeAndIdentityForVirtualFile() throws Exception {
        setupEnv();
        File file = File.createTempFile("Hello", ".java").getCanonicalFile();
        file.deleteOnExit();

        String text = "// Hello";
        Source.Builder<RuntimeException, RuntimeException, RuntimeException> builder = Source.newBuilder(file).content(text).mimeType("text/x-java");
        // JDK8 default fails on OS X: https://bugs.openjdk.java.net/browse/JDK-8129632
        Source s1 = builder.build();
        assertEquals("Recognized as Java", "text/x-java", s1.getMimeType());
        Source s2 = builder.mimeType("text/x-c").build();
        assertEquals("They have the same content", s1.getCharacters(), s2.getCharacters());
        assertEquals("// Hello", s1.getCharacters());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertEquals("File URI", file.toURI(), s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void noIOWhenContentSpecified() {
        setupEnv();
        File file = new File("some.tjs");

        String text = "// Hello";

        Source source = Source.newBuilder(file).content(text).build();
        assertEquals("The content has been changed", text, source.getCharacters());
        assertNotNull("Mime type specified", source.getMimeType());
        assertEquals("Recognized as JavaScript", "application/test-js", source.getMimeType());
        assertEquals("some.tjs", source.getName());
    }

    @Test
    public void fromTextWithFileURI() {
        setupEnv();
        File file = new File("some.tjs");

        String text = "// Hello";

        Source source = Source.newBuilder(text).uri(file.toURI()).mimeType("plain/text").name("another.tjs").build();
        assertEquals("The content has been changed", text, source.getCharacters());
        assertNotNull("Mime type specified", source.getMimeType());
        assertEquals("Assigned MIME type", "plain/text", source.getMimeType());
        assertEquals("another.tjs", source.getName());
        assertEquals("Using the specified URI", file.toURI(), source.getURI());
    }

    @Test
    public void assignMimeTypeAndIdentityForURL() throws IOException {
        setupEnv();
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
        assertEquals("They have the same content", s1.getCharacters(), s2.getCharacters());
        assertEquals("// Hello", s1.getCharacters());
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
        assertEquals(literal.getCharacters(), code);
        assertNull(literal.getURL());
        assertNotNull("Every source must have URI", literal.getURI());
        final char[] buffer = new char[code.length()];
        assertEquals(literal.getReader().read(buffer), code.length());
        assertEquals(new String(buffer), code);
    }

    @Test
    public void clientManagedSourceChange() {
        setupEnv();
        final String path = "test.input";
        final String code1 = "test\ntest";
        final String code2 = "test\ntest\nlonger\ntest";
        final Source source1 = Source.newBuilder(new File(path)).content(code1).mimeType("content/unknown").build();
        assertEquals(source1.getCharacters(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.newBuilder(new File(path)).content(code2).mimeType("content/unknown").build();
        assertEquals(source2.getCharacters(), code2);
        assertEquals(source2.getLineNumber(code2.length() - 1), 4);
        assertEquals("File URI", new File(path).toURI(), source1.getURI());
        assertEquals("File sources with different content have the same URI", source1.getURI(), source2.getURI());
    }

    @Test
    public void clientManagedSourceChangeAbsolute() {
        setupEnv();
        final String path = new File("test.input").getAbsolutePath();
        final String code1 = "test\ntest";
        final String code2 = "test\ntest\nlonger\ntest";
        final Source source1 = Source.newBuilder(new File(path)).content(code1).mimeType("x-application/input").build();
        assertEquals(source1.getCharacters(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.newBuilder(new File(path)).content(code2).mimeType("x-application/input").build();
        assertEquals(source2.getCharacters(), code2);
        assertEquals(source2.getLineNumber(code2.length() - 1), 4);
        assertEquals("File URI", new File("test.input").getAbsoluteFile().toURI(), source1.getURI());
        assertEquals("File sources with different content have the same URI", source1.getURI(), source2.getURI());
    }

    @Test
    public void jarURLGetsAName() throws IOException {
        setupEnv();
        File sample = File.createTempFile("sample", ".jar");
        sample.deleteOnExit();
        JarOutputStream os = new JarOutputStream(new FileOutputStream(sample));
        os.putNextEntry(new ZipEntry("x.tjs"));
        os.write("Hi!".getBytes("UTF-8"));
        os.closeEntry();
        os.close();

        URL resource = new URL("jar:" + sample.toURI() + "!/x.tjs");
        assertNotNull("Resource found", resource);
        assertEquals("JAR protocol", "jar", resource.getProtocol());
        Source s = Source.newBuilder(resource).build();
        assertEquals("Hi!", s.getCharacters());
        assertEquals("x.tjs", s.getName());

        sample.delete();
    }

    @Test
    public void whatAreTheDefaultValuesOfNewFromReader() throws Exception {
        StringReader r = new StringReader("Hi!");
        Source source = Source.newBuilder(r).name("almostEmpty").mimeType("text/plain").build();

        assertEquals("Hi!", source.getCharacters());
        assertEquals("almostEmpty", source.getName());
        assertNull(source.getPath());
        assertNotNull(source.getURI());
        assertTrue("URI ends with the name", source.getURI().toString().endsWith("almostEmpty"));
        assertEquals("truffle", source.getURI().getScheme());
        assertNull(source.getURL());
        assertEquals("text/plain", source.getMimeType());
    }

    @Test
    public void fileWithReload() throws Exception {
        setupEnv();
        File file = File.createTempFile("ChangeMe", ".java");
        file.deleteOnExit();

        String text;
        try (FileWriter w = new FileWriter(file)) {
            text = "// Hello";
            w.write(text);
        }

        Source original = Source.newBuilder(file).build();
        assertEquals(text, original.getCharacters());

        String newText;
        try (FileWriter w = new FileWriter(file)) {
            newText = "// Hello World!";
            w.write(newText);
        }

        Source reloaded = Source.newBuilder(file).build();
        assertNotEquals(original, reloaded);
        assertEquals("New source has the new text", newText, reloaded.getCharacters());

        assertEquals("Old source1 remains unchanged", text, original.getCharacters());
    }

    @Test
    public void normalSourceIsNotInter() {
        Source source = Source.newBuilder("anything").mimeType("text/plain").name("anyname").build();

        assertFalse("Not internal", source.isInternal());
        assertFalse("Not interactive", source.isInteractive());
    }

    @Test
    public void markSourceAsInternal() {
        Source source = Source.newBuilder("anything internal").mimeType("text/plain").name("internalsrc").internal().build();

        assertTrue("This source is internal", source.isInternal());
    }

    @Test
    public void markSourceAsInteractive() {
        Source source = Source.newBuilder("anything interactive").mimeType("text/plain").name("interactivesrc").interactive().build();

        assertTrue("This source is interactive", source.isInteractive());
    }

    public void subSourceHashAndEquals() {
        Source src = Source.newBuilder("One Two Three").name("counting.en").mimeType("content/unknown").build();
        Source one = src.subSource(0, 3);
        Source two = src.subSource(4, 3);
        Source three = src.subSource(8, src.getLength() - 8);

        Source oneSnd = src.subSource(0, 3);
        Source twoSnd = src.subSource(4, 3);
        Source threeSnd = src.subSource(8, src.getLength() - 8);

        assertNotEquals("One: " + one.getCharacters() + " two: " + two.getCharacters(), one, two);
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
        setupEnv();
        File f1 = File.createTempFile("subSource", ".tjs").getCanonicalFile();
        File f2 = File.createTempFile("subSource", ".tjs").getCanonicalFile();

        try (FileWriter w = new FileWriter(f1)) {
            w.write("function test() {\n" + "  return 1;\n" + "}\n");
        }

        try (FileWriter w = new FileWriter(f2)) {
            w.write("function test() {\n" + "  return 1;\n" + "}\n");
        }

        Source s1 = Source.newBuilder(f1).build();
        Source s2 = Source.newBuilder(f2).build();

        assertNotEquals("Different sources", s1, s2);
        assertEquals("But same content", s1.getCharacters(), s2.getCharacters());

        Source sub1 = s1.subSource(0, 8);
        Source sub2 = s2.subSource(0, 8);

        assertNotEquals("Different sub sources", sub1, sub2);
        assertEquals("with the same content", sub1.getCharacters(), sub2.getCharacters());
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
        } catch (com.oracle.truffle.api.source.MissingNameException ex) {
            // OK
            return;
        }
        fail("Expecting MissingNameException");
    }

    @Test
    public void throwsErrorIfMIMETypeIsNull() {
        try {
            Source.newBuilder("Hi").name("unknown.txt").build();
        } catch (com.oracle.truffle.api.source.MissingMIMETypeException ex) {
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

    private static void assertEither(String mimeType, String... expected) {
        for (String e : expected) {
            if (mimeType.equals(e)) {
                return;
            }
        }
        fail("Unexpected MIME type: " + mimeType);
    }
}
