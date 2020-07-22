/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import com.oracle.truffle.api.test.OSUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Source.Builder;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.io.ByteSequence;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.test.ReflectionUtils;
import org.junit.Assume;

public class SourceAPITest {

    @Test
    public void testCharSequenceNotMaterialized() throws IOException {
        AtomicBoolean materialized = new AtomicBoolean(false);
        final CharSequence testString = "testString";
        Source source = Source.newBuilder(SourceAPITestLanguage.ID, new CharSequence() {

            public CharSequence subSequence(int start, int end) {
                return testString.subSequence(start, end);
            }

            public int length() {
                return testString.length();
            }

            public char charAt(int index) {
                return testString.charAt(index);
            }

            @Override
            public String toString() {
                materialized.set(true);
                throw new AssertionError("Should not materialize CharSequence.");
            }
        }, "testsource").build();

        Context context = Context.create(SourceAPITestLanguage.ID);
        context.eval(source);

        assertEquals(1, source.getLineCount());
        assertTrue(equalsCharSequence(testString, source.getCharacters()));
        assertTrue(equalsCharSequence(testString, source.getCharacters(1)));
        assertEquals(0, source.getLineStartOffset(1));
        assertNull(source.getURL());
        assertNotNull(source.getName());
        assertNull(source.getPath());
        assertEquals(6, source.getColumnNumber(5));
        assertEquals(SourceAPITestLanguage.ID, source.getLanguage());
        assertEquals(testString.length(), source.getLength());
        assertFalse(source.isInteractive());
        assertFalse(source.isInternal());

        // consume reader CharSequence should not be materialized
        CharBuffer charBuffer = CharBuffer.allocate(source.getLength());
        Reader reader = source.getReader();
        reader.read(charBuffer);
        charBuffer.position(0);
        assertEquals(testString, charBuffer.toString());

        assertFalse(materialized.get());
        context.close();
    }

    private static boolean equalsCharSequence(CharSequence seq1, CharSequence seq2) {
        if (seq1.length() != seq2.length()) {
            return false;
        }
        for (int i = 0; i < seq1.length(); i++) {
            if (seq1.charAt(i) != seq2.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testBinarySources() {
        ByteSequence sequence = ByteSequence.create(new byte[]{1, 2, 3, 4});
        Source source = Source.newBuilder("", sequence, null).cached(false).buildLiteral();

        assertTrue(source.hasBytes());
        assertFalse(source.hasCharacters());

        assertFails(() -> source.getCharacters(), UnsupportedOperationException.class);
        assertFails(() -> source.getCharacters(0), UnsupportedOperationException.class);
        assertFails(() -> source.getColumnNumber(0), UnsupportedOperationException.class);
        assertFails(() -> source.getLineCount(), UnsupportedOperationException.class);
        assertFails(() -> source.getLineLength(0), UnsupportedOperationException.class);
        assertFails(() -> source.getLineNumber(0), UnsupportedOperationException.class);
        assertFails(() -> source.getLineStartOffset(0), UnsupportedOperationException.class);
        assertFails(() -> source.getReader(), UnsupportedOperationException.class);

        assertNull(source.getMimeType());
        assertEquals("", source.getLanguage());
        assertSame(sequence, source.getBytes());
        assertEquals("Unnamed", source.getName());
        assertNull(source.getURL());
        assertEquals("truffle:9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a/Unnamed", source.getURI().toString());
    }

    @Test
    public void testMimeTypes() {
        ByteSequence bytes = ByteSequence.create(new byte[8]);
        assertNotNull(Source.newBuilder("", "", "").mimeType(null).buildLiteral());

        assertFails(() -> Source.newBuilder("", "", "").mimeType(""), IllegalArgumentException.class);
        assertFails(() -> Source.newBuilder("", "", "").mimeType("/"), IllegalArgumentException.class);
        assertFails(() -> Source.newBuilder("", "", "").mimeType("a/"), IllegalArgumentException.class);
        assertFails(() -> Source.newBuilder("", "", "").mimeType("/a"), IllegalArgumentException.class);

        assertEquals("text/a", Source.newBuilder("", "", "").mimeType("text/a").buildLiteral().getMimeType());
        assertEquals("application/a", Source.newBuilder("", bytes, "").mimeType("application/a").buildLiteral().getMimeType());
    }

    @Test
    public void testBuildBinarySources() throws IOException {
        ByteSequence bytes = ByteSequence.create(new byte[8]);
        Source source = Source.newBuilder("", bytes, null).build();

        assertTrue(source.hasBytes());
        assertFalse(source.hasCharacters());

        source = Source.newBuilder("", "", null).content(bytes).build();
        assertTrue(source.hasBytes());
        assertFalse(source.hasCharacters());

        source = Source.newBuilder("", bytes, null).content("").build();
        assertFalse(source.hasBytes());
        assertTrue(source.hasCharacters());

        File file = File.createTempFile("Hello", ".bin").getCanonicalFile();
        file.deleteOnExit();

        // mime-type not specified + invalid langauge -> characters
        source = Source.newBuilder("", file).build();
        assertFalse(source.hasBytes());
        assertTrue(source.hasCharacters());

        // mime-type not specified + invalid langauge -> characters
        source = Source.newBuilder("", file).content(bytes).build();
        assertTrue(source.hasBytes());
        assertFalse(source.hasCharacters());

        source = Source.newBuilder("", file).content("").build();
        assertFalse(source.hasBytes());
        assertTrue(source.hasCharacters());
    }

    private static void assertFails(Callable<?> callable, Class<? extends Exception> exception) {
        try {
            callable.call();
            fail("Expected " + exception.getSimpleName() + " but no exception was thrown");
        } catch (Exception e) {
            assertTrue(exception.toString(), exception.isInstance(e));
        }
    }

    @Test
    public void assignMimeTypeAndIdentity() {
        Builder builder = Source.newBuilder("lang", "// a comment\n", "Empty comment");
        Source s1 = builder.mimeType("text/unknown").buildLiteral();
        assertEquals("No mime type assigned", "text/unknown", s1.getMimeType());
        Source s2 = builder.mimeType("text/x-c").buildLiteral();
        assertEquals("They have the same content", s1.getCharacters(), s2.getCharacters());
        assertNotEquals("But different type", s1.getMimeType(), s2.getMimeType());
        assertNotEquals("So they are different", s1, s2);
        assertNotNull("Every source must have URI", s1.getURI());
        assertEquals("Source with different MIME type has the same URI", s1.getURI(), s2.getURI());
    }

    @Test
    public void assignMimeTypeAndIdentityForReader() throws IOException {
        String text = "// Hello";
        Source.Builder builder = Source.newBuilder("lang", new StringReader(text), "test.txt");
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
        Source.Builder builder = Source.newBuilder("lang", nonCannonicalFile).mimeType("text/x-java");

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
        File file = File.createTempFile("Hello", ".bin").getCanonicalFile();
        file.deleteOnExit();

        try (FileOutputStream w = new FileOutputStream(file)) {
            w.write(0x04);
            w.write(0x05);
        }

        Source source = Source.newBuilder("lang", file).build();
        assertNull(source.getMimeType());
    }

    @Test
    public void mimeTypeIsDetectedRandomBytesForURI() throws IOException {
        File file = File.createTempFile("Hello", ".bin").getCanonicalFile();
        file.deleteOnExit();

        try (FileOutputStream w = new FileOutputStream(file)) {
            w.write(0x04);
            w.write(0x05);
        }

        Source source = Source.newBuilder("lang", file.toURI().toURL()).build();
        assertNull(source.getMimeType());
    }

    @Test
    public void ioExceptionWhenFileDoesntExist() throws Exception {
        File file = File.createTempFile("Hello", ".java").getCanonicalFile();
        file.delete();
        assertFalse("Doesn't exist", file.exists());

        Source.Builder builder = Source.newBuilder("lang", file);

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

        Source.Builder builder = Source.newBuilder("lang", reader, "unloadable.txt");

        Source s1 = null;
        try {
            s1 = builder.build();
            fail("No source should be created: " + s1);
        } catch (IOException e) {
            Assert.assertSame(ioEx, e);
        }
    }

    @Test
    public void assignMimeTypeAndIdentityForVirtualFile() throws Exception {
        File file = File.createTempFile("Hello", ".java").getCanonicalFile();
        file.deleteOnExit();

        String text = "// Hello";
        Source.Builder builder = Source.newBuilder("java", file).content(text).mimeType("text/x-java");
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
        File file = new File("some.tjs");

        String text = "// Hello";

        Source source = Source.newBuilder("lang", file).content(text).mimeType("text/javascript").buildLiteral();
        assertEquals("The content has been changed", text, source.getCharacters());
        assertNotNull("Mime type specified", source.getMimeType());
        assertTrue("Recognized as JavaScript", source.getMimeType().equals("text/javascript"));
        assertEquals("some.tjs", source.getName());
    }

    @Test
    public void fromTextWithFileURI() {
        File file = new File("some.tjs");

        String text = "// Hello";

        Source source = Source.newBuilder("lang", text, "another.tjs").uri(file.toURI()).buildLiteral();
        assertEquals("The content has been changed", text, source.getCharacters());
        assertNull("Mime type not specified", source.getMimeType());
        assertNull("Null MIME type", source.getMimeType());
        assertEquals("another.tjs", source.getName());
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
        Source.Builder builder = Source.newBuilder("TestJava", file.toURI().toURL()).name("Hello.java").mimeType(Source.findMimeType(file.toURI().toURL()));

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
    public void unassignedMimeTypeForURL() throws IOException {
        File file = File.createTempFile("Hello", ".java");
        file.deleteOnExit();
        Path path = file.toPath();
        byte[] content = "// Test".getBytes("UTF-8");
        Files.write(path, content);
        URL url = path.toUri().toURL();
        assertEquals("text/x-java", Source.findMimeType(url));
        Source.Builder builder = Source.newBuilder("TestJava", url);
        Source source = builder.build();
        assertNull("MIME type should be null if not specified", source.getMimeType());

        File archive = File.createTempFile("hello", ".jar");
        archive.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
            out.putNextEntry(new ZipEntry("Hello.java"));
            out.write(content);
        }
        url = new URL("jar:" + archive.toURI().toURL().toExternalForm() + "!/Hello.java");
        builder = Source.newBuilder("TestJava", url);
        source = builder.build();
        assertNull("MIME type should be null if not specified", source.getMimeType());
    }

    @Test
    public void literalSources() throws IOException {
        final String code = "test code";
        final String description = "test description";
        final Source literal = Source.newBuilder("lang", code, description).name(description).build();
        assertEquals(literal.getLanguage(), "lang");
        assertEquals(literal.getName(), description);
        assertEquals(literal.getCharacters(), code);
        assertNull(literal.getMimeType());
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
        final File truffleFile = new File(path);

        final Source source1 = Source.newBuilder("lang", truffleFile).content(code1).buildLiteral();
        assertEquals(source1.getCharacters(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.newBuilder("lang", truffleFile).content(code2).buildLiteral();
        assertEquals(source2.getCharacters(), code2);
        assertEquals(source2.getLineNumber(code2.length() - 1), 4);
        assertEquals("File URI", new File(path).toURI(), source1.getURI());
        assertEquals("File sources with different content have the same URI", source1.getURI(), source2.getURI());
    }

    @Test
    public void clientManagedSourceChangeAbsolute() {
        final String path = new File("test.input").getAbsolutePath();
        final String code1 = "test\ntest";
        final String code2 = "test\ntest\nlonger\ntest";
        final File file = new File(path);

        final Source source1 = Source.newBuilder("lang", file).content(code1).buildLiteral();
        assertEquals(source1.getCharacters(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.newBuilder("lang", file).content(code2).buildLiteral();
        assertEquals(source2.getCharacters(), code2);
        assertEquals(source2.getLineNumber(code2.length() - 1), 4);
        assertEquals("File URI", new File("test.input").getAbsoluteFile().toURI(), source1.getURI());
        assertEquals("File sources with different content have the same URI", source1.getURI(), source2.getURI());
    }

    @Test
    public void jarURLGetsAName() throws IOException {
        File sample = File.createTempFile("sample", ".jar");
        sample.deleteOnExit();
        JarOutputStream os = new JarOutputStream(new FileOutputStream(sample));
        os.putNextEntry(new ZipEntry("x.tjs"));
        byte[] bytes = "Hi!".getBytes("UTF-8");
        os.write(bytes);
        os.closeEntry();
        os.close();

        URL resource = new URL("jar:" + sample.toURI() + "!/x.tjs");
        assertNotNull("Resource found", resource);
        assertEquals("JAR protocol", "jar", resource.getProtocol());
        Source s = Source.newBuilder("TestJS", resource).build();
        assertEquals(resource, s.getURL());
        Assert.assertArrayEquals(bytes, s.getBytes().toByteArray());
        assertEquals("x.tjs", s.getName());

        sample.delete();
    }

    @Test
    public void testHttpURL() throws IOException, URISyntaxException {
        URL resource = new URL("http://example.org/test/File.html");
        Source s = Source.newBuilder("TestJS", resource).content("Empty").build();
        assertEquals(resource, s.getURL());
        assertEquals(resource.toURI(), s.getURI());
        assertEquals("File.html", s.getName());
        assertEquals("/test/File.html", s.getPath());
    }

    @Test
    public void whatAreTheDefaultValuesOfNewFromReader() throws Exception {
        StringReader r = new StringReader("Hi!");
        Source source = Source.newBuilder("lang", r, "almostEmpty").build();

        assertEquals("Hi!", source.getCharacters());
        assertEquals("almostEmpty", source.getName());
        assertEquals("lang", source.getLanguage());
        assertNull(source.getPath());
        assertNotNull(source.getURI());
        assertTrue("URI ends with the name", source.getURI().toString().endsWith("almostEmpty"));
        assertEquals("truffle", source.getURI().getScheme());
        assertNull(source.getURL());
        assertNull(source.getMimeType());
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

        Source original = Source.newBuilder("lang", file).build();
        assertEquals(text, original.getCharacters());

        String newText;
        try (FileWriter w = new FileWriter(file)) {
            newText = "// Hello World!";
            w.write(newText);
        }

        Source reloaded = Source.newBuilder("lang", file).build();
        assertNotEquals(original, reloaded);
        assertEquals("New source has the new text", newText, reloaded.getCharacters());

        assertEquals("Old source1 remains unchanged", text, original.getCharacters());
    }

    @Test
    public void normalSourceIsNotInter() {
        Source source = Source.newBuilder("lang", "anything", "name").buildLiteral();

        assertFalse("Not internal", source.isInternal());
        assertFalse("Not interactive", source.isInteractive());
    }

    @Test
    public void markSourceAsInternal() {
        Source source = Source.newBuilder("lang", "anything", "name").internal(true).buildLiteral();

        assertTrue("This source is internal", source.isInternal());
    }

    @Test
    public void markSourceAsInteractive() {
        Source source = Source.newBuilder("lang", "anything", "name").interactive(true).buildLiteral();

        assertTrue("This source is interactive", source.isInteractive());
    }

    @Test
    public void throwsErrorNameCannotBeNull() {
        assertEquals("Unnamed", Source.newBuilder("lang", "Hi", null).buildLiteral().getName());
    }

    @Test
    public void throwsErrorIfCharContentIsNull() {
        try {
            Source.newBuilder("lang", (CharSequence) null, "name");
            fail("Expecting NullPointerException");
        } catch (NullPointerException ex) {
            // OK
        }
    }

    @Test
    public void throwsErrorIfByteContentIsNull() {
        try {
            Source.newBuilder("lang", (ByteSequence) null, "name");
            fail("Expecting NullPointerException");
        } catch (NullPointerException ex) {
            // OK
        }
    }

    @Test
    public void throwsErrorIfLangIsNull1() {
        try {
            Source.newBuilder(null, new File("foo.bar"));
            fail();
        } catch (NullPointerException ex) {
            // OK
        }
    }

    @Test
    public void throwsErrorIfLangIsNull2() {
        try {
            Source.newBuilder(null, "", "name");
            fail();
        } catch (NullPointerException ex) {
            // OK
        }
    }

    @Test
    public void throwsErrorIfLangIsNull3() throws MalformedURLException {
        try {
            URL url = new URL("file://test.bar");
            Source.newBuilder(null, url);
            fail();
        } catch (NullPointerException ex) {
            // OK
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testNoContentSource() {
        com.oracle.truffle.api.source.Source truffleSource = com.oracle.truffle.api.source.Source.newBuilder(ProxyLanguage.ID, "x", "name").content(
                        com.oracle.truffle.api.source.Source.CONTENT_NONE).build();
        Class<?>[] sourceConstructorTypes = new Class[]{String.class, Object.class};
        Source source = ReflectionUtils.newInstance(Source.class, sourceConstructorTypes, ProxyLanguage.ID, truffleSource);
        assertFalse(source.hasCharacters());
        assertFalse(source.hasBytes());
        try {
            source.getCharacters();
            fail();
        } catch (UnsupportedOperationException ex) {
            // O.K.
        }
        try {
            Context.create().eval(source);
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
        }
        com.oracle.truffle.api.source.SourceSection truffleSection = truffleSource.createSection(1, 2, 3, 4);
        Class<?>[] sectionConstructorTypes = new Class[]{Source.class, Object.class};
        SourceSection section = ReflectionUtils.newInstance(SourceSection.class, sectionConstructorTypes, source, truffleSection);
        assertFalse(section.hasCharIndex());
        assertTrue(section.hasLines());
        assertTrue(section.hasColumns());
        assertEquals("", section.getCharacters());
    }

    @Test
    public void testNonResolvableURL() throws IOException {
        Assume.assumeFalse("Query parameters are not supported by file URLConnection on Windows", OSUtils.isWindows());
        File file = File.createTempFile("Test", ".java");
        file.deleteOnExit();
        String text;
        try (FileWriter w = new FileWriter(file)) {
            text = "// Test";
            w.write(text);
        }
        URL url = new URL(file.toURI() + "?query");
        Source src = Source.newBuilder("TestJava", url).build();
        assertNotNull(src);
        assertTrue(text.contentEquals(src.getCharacters()));
        assertEquals("text/plain", Source.findMimeType(url));
    }
}
