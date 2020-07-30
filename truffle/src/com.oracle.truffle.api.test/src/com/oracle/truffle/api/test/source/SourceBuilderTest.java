/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.Assert.assertArrayEquals;
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
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Objects;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.ByteSequence;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.LiteralBuilder;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.OSUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.MemoryFileSystem;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.junit.Assume;

public class SourceBuilderTest extends AbstractPolyglotTest {

    @Test
    public void testBinarySourcesUnavailableSourceSection() {
        ByteSequence sequence = ByteSequence.create(new byte[]{1, 2, 3, 4});
        Source source = Source.newBuilder("", sequence, null).build();
        SourceSection section = source.createUnavailableSection();

        assertEquals("", section.getCharacters().toString());
        assertEquals(0, section.getCharEndIndex());
        assertEquals(0, section.getCharIndex());
        assertEquals(0, section.getCharLength());
        assertEquals(1, section.getEndColumn());
        assertEquals(1, section.getEndLine());
        assertEquals(1, section.getStartColumn());
        assertEquals(1, section.getStartLine());
        assertSame(source, section.getSource());
    }

    @Test
    public void testBinarySources() {
        ByteSequence sequence = ByteSequence.create(new byte[]{1, 2, 3, 4});
        Source source = Source.newBuilder("", sequence, null).build();

        assertTrue(source.hasBytes());
        assertFalse(source.hasCharacters());
        assertEquals(4, source.getLength());
        assertFails(() -> source.createSection(0), UnsupportedOperationException.class);
        assertFails(() -> source.createSection(0, 0), UnsupportedOperationException.class);
        assertFails(() -> source.createSection(0, 0, 0), UnsupportedOperationException.class);

        SourceSection section = source.createUnavailableSection();
        assertFalse(section.isAvailable());
        assertTrue(section.getCharacters().length() == 0);

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
        assertEquals(sequence, source.getBytes());
        assertEquals("Unnamed", source.getName());
        assertNull(source.getURL());
        assertEquals("truffle:9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a/Unnamed", source.getURI().toString());
    }

    @Test
    public void testMimeTypes() {
        setupEnv();
        ByteSequence bytes = ByteSequence.create(new byte[8]);
        assertNotNull(Source.newBuilder("", "", "").mimeType(null).build());

        assertFails(() -> Source.newBuilder("", "", "").mimeType(""), IllegalArgumentException.class);
        assertFails(() -> Source.newBuilder("", "", "").mimeType("/"), IllegalArgumentException.class);
        assertFails(() -> Source.newBuilder("", "", "").mimeType("a/"), IllegalArgumentException.class);
        assertFails(() -> Source.newBuilder("", "", "").mimeType("/a"), IllegalArgumentException.class);

        assertEquals("text/a", Source.newBuilder("", "", "").mimeType("text/a").build().getMimeType());
        assertEquals("application/a", Source.newBuilder("", bytes, "").mimeType("application/a").build().getMimeType());
    }

    @Test
    public void testBuildBinarySources() throws IOException {
        setupEnv();
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
        TruffleFile truffleFile = languageEnv.getPublicTruffleFile(file.getPath());

        // mime-type not specified + invalid langauge -> characters
        source = Source.newBuilder("", truffleFile).build();
        assertFalse(source.hasBytes());
        assertTrue(source.hasCharacters());

        // mime-type not specified + invalid langauge -> characters
        source = Source.newBuilder("", truffleFile).content(bytes).build();
        assertTrue(source.hasBytes());
        assertFalse(source.hasCharacters());

        source = Source.newBuilder("", truffleFile).content("").build();
        assertFalse(source.hasBytes());
        assertTrue(source.hasCharacters());
    }

    @Test
    public void testNoContentSource() throws Exception {
        setupEnv();
        // Relative file
        TruffleFile truffleFile = languageEnv.getPublicTruffleFile("some/path");
        URI uri = new URI("some/path");
        Source source = Source.newBuilder("", truffleFile).content(Source.CONTENT_NONE).build();
        assertFalse(source.hasBytes());
        assertFalse(source.hasCharacters());
        assertEquals(String.join(languageEnv.getFileNameSeparator(), "some", "path"), source.getPath());
        assertEquals("path", source.getName());
        assertEquals(uri, source.getURI());
        assertFalse(source.getURI().isAbsolute());
        assertFails(() -> source.getLength(), UnsupportedOperationException.class);
        assertFails(() -> source.getBytes(), UnsupportedOperationException.class);
        assertFails(() -> source.getCharacters(), UnsupportedOperationException.class);
        // Absolute file
        Path tempFile = Files.createTempFile("Test", ".java").toRealPath();
        tempFile.toFile().deleteOnExit();
        String content = "// Test";
        Files.write(tempFile, content.getBytes("UTF-8"));
        truffleFile = languageEnv.getPublicTruffleFile(tempFile.toString());
        Source source2 = Source.newBuilder("", truffleFile).content(Source.CONTENT_NONE).mimeType("text/x-java").build();
        assertFalse(source2.hasBytes());
        assertFalse(source2.hasCharacters());
        assertTrue(tempFile.toString() + " x " + source2.getPath(), Files.isSameFile(tempFile, new File(source2.getPath()).toPath()));
        assertEquals(tempFile.getFileName().toString(), source2.getName());
        assertTrue(source2.getURI().toString(), source2.getURI().isAbsolute());
        assertFails(() -> source2.getLength(), UnsupportedOperationException.class);
        assertFails(() -> source2.getBytes(), UnsupportedOperationException.class);
        assertFails(() -> source2.getCharacters(), UnsupportedOperationException.class);
    }

    @Test
    public void testRelativeSourceWithContent() throws Exception {
        setupEnv();
        TruffleFile cwd = languageEnv.getCurrentWorkingDirectory();
        String relativeName = "Test.java";
        TruffleFile file = cwd.resolve(relativeName);
        String content = "// Test";
        try {
            try (SeekableByteChannel c = file.newByteChannel(EnumSet.of(WRITE, CREATE))) {
                c.write(ByteBuffer.wrap(content.getBytes("UTF-8")));
            }
            TruffleFile relativeFile = languageEnv.getPublicTruffleFile(relativeName);
            Source source = Source.newBuilder("", relativeFile).build();
            assertFalse(source.hasBytes());
            assertTrue(source.hasCharacters());
            // Constructed from relativeFile TruffleFile, but loads content and should be absolute
            assertTrue(source.getURI().toString(), source.getURI().isAbsolute());
            assertEquals(content, source.getCharacters().toString());
        } finally {
            file.delete();
        }
    }

    @Test
    public void assignMimeTypeAndIdentity() {
        LiteralBuilder builder = Source.newBuilder("lang", "// a comment\n", "Empty comment");
        Source s1 = builder.mimeType("text/unknown").build();
        assertEquals("No mime type assigned", "text/unknown", s1.getMimeType());
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
        SourceBuilder builder = Source.newBuilder("lang", new StringReader(text), "test.txt");
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

        final TruffleFile nonCannonicalFile = languageEnv.getPublicTruffleFile(nonCannonical);
        assertTrue("Exists, as it is the same file", nonCannonicalFile.exists());
        SourceBuilder builder = Source.newBuilder("lang", nonCannonicalFile).mimeType("text/x-java");

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
        final TruffleFile truffleFile = languageEnv.getPublicTruffleFile(file.getAbsolutePath());

        Source source = Source.newBuilder("lang", truffleFile).build();
        assertEither(source.getMimeType(), null, "application/octet-stream", "text/plain", "application/macbinary");
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

        Source source = Source.newBuilder("lang", file.toURI().toURL()).build();
        assertNull(source.getMimeType());
    }

    @Test
    public void ioExceptionWhenFileDoesntExist() throws Exception {
        setupEnv();
        File file = File.createTempFile("Hello", ".java").getCanonicalFile();
        file.delete();
        assertFalse("Doesn't exist", file.exists());
        final TruffleFile truffleFile = languageEnv.getPublicTruffleFile(file.getAbsolutePath());

        SourceBuilder builder = Source.newBuilder("lang", truffleFile);

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

        SourceBuilder builder = Source.newBuilder("lang", reader, "unloadable.txt");

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
        setupEnv();
        File file = File.createTempFile("Hello", ".java").getCanonicalFile();
        file.deleteOnExit();
        final TruffleFile truffleFile = languageEnv.getPublicTruffleFile(file.getAbsolutePath());

        String text = "// Hello";
        SourceBuilder builder = Source.newBuilder("java", truffleFile).content(text).mimeType("text/x-java");
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
        final TruffleFile truffleFile = languageEnv.getPublicTruffleFile(file.getAbsolutePath());

        Source source = Source.newBuilder("lang", truffleFile).content(text).mimeType("text/javascript").build();
        assertEquals("The content has been changed", text, source.getCharacters());
        assertNotNull("Mime type specified", source.getMimeType());
        assertTrue("Recognized as JavaScript", source.getMimeType().equals("text/javascript"));
        assertEquals("some.tjs", source.getName());
    }

    @Test
    public void fromTextWithFileURI() {
        File file = new File("some.tjs");

        String text = "// Hello";

        Source source = Source.newBuilder("lang", text, "another.tjs").uri(file.toURI()).build();
        assertEquals("The content has been changed", text, source.getCharacters());
        assertNull("Mime type not specified", source.getMimeType());
        assertNull("Null MIME type", source.getMimeType());
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
        SourceBuilder builder = Source.newBuilder("TestJava", file.toURI().toURL()).name("Hello.java").mimeType(Source.findMimeType(file.toURI().toURL()));
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
        setupEnv();

        final String path = "test.input";
        final String code1 = "test\ntest";
        final String code2 = "test\ntest\nlonger\ntest";
        final TruffleFile truffleFile = languageEnv.getPublicTruffleFile(path);

        final Source source1 = Source.newBuilder("lang", truffleFile).content(code1).build();
        assertEquals(source1.getCharacters(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.newBuilder("lang", truffleFile).content(code2).build();
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
        final TruffleFile truffleFile = languageEnv.getPublicTruffleFile(path);

        final Source source1 = Source.newBuilder("lang", truffleFile).content(code1).build();
        assertEquals(source1.getCharacters(), code1);
        assertEquals(source1.getLineNumber(code1.length() - 1), 2);
        final Source source2 = Source.newBuilder("lang", truffleFile).content(code2).build();
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
        assertEquals(sample.toURI() + "!/x.tjs", s.getPath());

        sample.delete();
    }

    @Test
    public void testHttpURL() throws IOException, URISyntaxException {
        setupEnv();
        URL resource = new URL("http://example.org/test/File.html");
        Source s = Source.newBuilder("TestJS", resource).content("Empty").build();
        assertEquals(resource, s.getURL());
        assertEquals(resource.toURI(), s.getURI());
        assertEquals("File.html", s.getName());
        assertEquals("/test/File.html", s.getPath());
        assertEquals("Empty", s.getCharacters());
    }

    @Test
    public void testBuiltFromSourceLiteral() {
        final String code = "test code";
        final String description = "test description";
        for (int cached = 0; cached <= 1; cached++) {
            for (int interactive = 0; interactive <= 1; interactive++) {
                for (int internal = 0; internal <= 1; internal++) {
                    final Source literal1 = Source.newBuilder("lang", code, description).cached(cached != 0).interactive(interactive != 0).internal(internal != 0).mimeType("text/test").build();
                    final Source literal2 = Source.newBuilder(literal1).build();
                    assertSameProperties(literal1, literal2);
                }
            }
        }
        assertNewSourceChanged(Source.newBuilder("lang", code, description).build());
    }

    @Test
    public void testBuiltFromBinarySource() {
        setupEnv();
        ByteSequence bytes = ByteSequence.create(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        Source source1 = Source.newBuilder("Lang", bytes, "testName").build();
        Source source2 = Source.newBuilder(source1).build();
        assertSameProperties(source1, source2);
        assertNewSourceChanged(source1);
    }

    @Test
    public void testBuiltFromSourceReader() throws IOException {
        setupEnv();
        StringReader reader = new StringReader("test\ncode");
        Source source1 = Source.newBuilder("Lang", reader, "testName").build();
        Source source2 = Source.newBuilder(source1).build();
        assertSameProperties(source1, source2);
        assertNewSourceChanged(source1);
    }

    @Test
    public void testBuiltFromSourceFile() {
        setupEnv();
        File file = new File("some.tjs");
        final TruffleFile truffleFile = languageEnv.getPublicTruffleFile(file.getAbsolutePath());
        Source source1 = Source.newBuilder("Lang", truffleFile).content("Empty").build();
        Source source2 = Source.newBuilder(source1).build();
        assertSameProperties(source1, source2);
        assertNewSourceChanged(source1);
    }

    @Test
    public void testBuiltFromSourceURL() throws IOException {
        setupEnv();
        URL resource = new URL("http://example.org/test/File.html");
        Source source1 = Source.newBuilder("Lang", resource).content("Empty").build();
        Source source2 = Source.newBuilder(source1).build();
        assertSameProperties(source1, source2);
        assertNewSourceChanged(source1);
    }

    @Test
    public void testBuiltFromNoContentSource() {
        setupEnv();
        // Relative file
        TruffleFile truffleFile = languageEnv.getPublicTruffleFile("some/path");
        Source source1 = Source.newBuilder("Lang", truffleFile).content(Source.CONTENT_NONE).build();
        Source source2 = Source.newBuilder(source1).build();
        assertSameProperties(source1, source2);
        assertNewSourceChanged(source1);
    }

    private static void assertSameProperties(Source s1, Source s2) {
        assertEquals(s1.hasBytes(), s2.hasBytes());
        assertEquals(s1.hasCharacters(), s2.hasCharacters());
        if (s1.hasCharacters()) {
            assertEquals(s1.getCharacters(), s2.getCharacters());
            assertEquals(s1.getLength(), s2.getLength());
        }
        if (s1.hasBytes()) {
            assertArrayEquals(s1.getBytes().toByteArray(), s2.getBytes().toByteArray());
            assertEquals(s1.getLength(), s2.getLength());
        }
        assertEquals(s1.getLanguage(), s2.getLanguage());
        assertEquals(s1.getMimeType(), s2.getMimeType());
        assertEquals(s1.getName(), s2.getName());
        assertEquals(s1.getPath(), s2.getPath());
        assertEquals(s1.getURI(), s2.getURI());
        assertEquals(s1.getURL(), s2.getURL());
        assertEquals(s1.isInteractive(), s2.isInteractive());
        assertEquals(s1.isInternal(), s2.isInternal());
    }

    private static void assertNewSourceChanged(Source s1) {
        // Change name
        Source s2 = Source.newBuilder(s1).name("New Test Name").build();
        assertNotEquals(s1.getName(), s2.getName());
        if ("truffle".equals(s1.getURI().getScheme())) {
            assertNotEquals(s1.getURI(), s2.getURI());
        }
        // Change content
        s2 = Source.newBuilder(s1).content("New Content").build();
        assertTrue(s2.hasCharacters());
        assertEquals("New Content", s2.getCharacters());
        if ("truffle".equals(s1.getURI().getScheme())) {
            assertNotEquals(s1.getURI(), s2.getURI());
        }
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
        setupEnv();
        File file = File.createTempFile("ChangeMe", ".java");
        file.deleteOnExit();

        String text;
        try (FileWriter w = new FileWriter(file)) {
            text = "// Hello";
            w.write(text);
        }

        final TruffleFile truffleFile = languageEnv.getPublicTruffleFile(file.getAbsolutePath());

        Source original = Source.newBuilder("lang", truffleFile).build();
        assertEquals(text, original.getCharacters());

        String newText;
        try (FileWriter w = new FileWriter(file)) {
            newText = "// Hello World!";
            w.write(newText);
        }

        Source reloaded = Source.newBuilder("lang", truffleFile).build();
        assertNotEquals(original, reloaded);
        assertEquals("New source has the new text", newText, reloaded.getCharacters());

        assertEquals("Old source1 remains unchanged", text, original.getCharacters());
    }

    @Test
    public void normalSourceIsNotInter() {
        Source source = Source.newBuilder("lang", "anything", "name").build();

        assertFalse("Not internal", source.isInternal());
        assertFalse("Not interactive", source.isInteractive());
    }

    @Test
    public void markSourceAsInternal() {
        Source source = Source.newBuilder("lang", "anything", "name").internal(true).build();

        assertTrue("This source is internal", source.isInternal());
    }

    @Test
    public void markSourceAsInteractive() {
        Source source = Source.newBuilder("lang", "anything", "name").interactive(true).build();

        assertTrue("This source is interactive", source.isInteractive());
    }

    @Test
    public void testEncodingTruffleFile() throws IOException {
        // Checkstyle: stop
        String content = "žščřďťňáéíóúůý";
        String xmlContent = "<?xml version=\"1.0\" encoding=\"windows-1250\"?>\n<!DOCTYPE foo PUBLIC \"foo\">\n<content>žščřďťňáéíóúůý</content>";
        // Checkstyle: resume
        setupEnv();
        File testFile = File.createTempFile("test", ".txt").getAbsoluteFile();
        try (FileOutputStream out = new FileOutputStream(testFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_16LE));
        }
        TruffleFile file = languageEnv.getPublicTruffleFile(testFile.getPath());
        Source src = Source.newBuilder("lang", file).encoding(StandardCharsets.UTF_16LE).build();
        assertEquals(content, src.getCharacters().toString());

        testFile = File.createTempFile("test", ".xml").getAbsoluteFile();
        try (FileOutputStream out = new FileOutputStream(testFile)) {
            out.write(xmlContent.getBytes(Charset.forName("windows-1250")));
        }
        file = languageEnv.getPublicTruffleFile(testFile.getPath());
        src = Source.newBuilder("lang", file).build();
        assertEquals(xmlContent, src.getCharacters().toString());

        testFile = File.createTempFile("test", ".txt").getAbsoluteFile();
        try (FileOutputStream out = new FileOutputStream(testFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        file = languageEnv.getPublicTruffleFile(testFile.getPath());
        src = Source.newBuilder("lang", file).build();
        assertEquals(content, src.getCharacters().toString());
    }

    @Test
    public void testEncodingURL() throws IOException {
        // Checkstyle: stop
        String content = "žščřďťňáéíóúůý";
        String xmlContent = "<?xml version=\"1.0\" encoding=\"windows-1250\"?>\n<!DOCTYPE foo PUBLIC \"foo\">\n<content>žščřďťňáéíóúůý</content>";
        // Checkstyle: resume
        setupEnv();
        File testFile = File.createTempFile("test", ".txt").getAbsoluteFile();
        try (FileOutputStream out = new FileOutputStream(testFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_16LE));
        }
        Source src = Source.newBuilder("lang", testFile.toURI().toURL()).encoding(StandardCharsets.UTF_16LE).build();
        assertEquals(content, src.getCharacters().toString());

        testFile = File.createTempFile("test", ".xml").getAbsoluteFile();
        try (FileOutputStream out = new FileOutputStream(testFile)) {
            out.write(xmlContent.getBytes(Charset.forName("windows-1250")));
        }
        src = Source.newBuilder("lang", testFile.toURI().toURL()).build();
        assertEquals(xmlContent, src.getCharacters().toString());

        testFile = File.createTempFile("test", ".txt").getAbsoluteFile();
        try (FileOutputStream out = new FileOutputStream(testFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        src = Source.newBuilder("lang", testFile.toURI().toURL()).build();
        assertEquals(content, src.getCharacters().toString());
    }

    public void subSourceHashAndEquals() {
        Source src = Source.newBuilder("lang", "One Two Three", "counting.en").build();
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

        final TruffleFile truffleFile1 = languageEnv.getPublicTruffleFile(f1.getAbsolutePath());
        final TruffleFile truffleFile2 = languageEnv.getPublicTruffleFile(f2.getAbsolutePath());

        Source s1 = Source.newBuilder("lang", truffleFile1).build();
        Source s2 = Source.newBuilder("lang", truffleFile2).build();

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
        assertEquals("Unnamed", Source.newBuilder("lang", "Hi", null).build().getName());
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
            setupEnv();
            Source.newBuilder("lang", (ByteSequence) null, "name");
            fail("Expecting NullPointerException");
        } catch (NullPointerException ex) {
            // OK
        }
    }

    @Test
    public void throwsErrorIfLangIsNull1() {
        try {
            TruffleFile file = languageEnv.getPublicTruffleFile("foo.bar");
            Source.newBuilder(null, file);
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

    private static void assertEither(String mimeType, String... expected) {
        for (String e : expected) {
            if (Objects.equals(mimeType, e)) {
                return;
            }
        }
        fail("Unexpected MIME type: " + mimeType);
    }

    @Registration(id = "TestJava", name = "", characterMimeTypes = "text/x-java", fileTypeDetectors = CommonMIMETypeTestDetector.class)
    public static class TestJavaLanguage extends ProxyLanguage {

    }

    @Registration(id = "TestJS", name = "", byteMimeTypes = "application/test-js", fileTypeDetectors = CommonMIMETypeTestDetector.class)
    public static class TestJSLanguage extends ProxyLanguage {
    }

    @Registration(id = "TestTxt", name = "", byteMimeTypes = "text/plain", fileTypeDetectors = CommonMIMETypeTestDetector.class)
    public static class TestTxtLanguage extends ProxyLanguage {
    }

    @Test
    public void testFindLaguage() throws IOException {
        setupEnv();
        TruffleFile knownMimeTypeFile = languageEnv.getPublicTruffleFile("test.tjs");
        TruffleFile unknownMimeTypeFile = languageEnv.getPublicTruffleFile("test.unknown");
        assertEquals("application/test-js", Source.findMimeType(knownMimeTypeFile));
        assertNull(Source.findMimeType(unknownMimeTypeFile));
    }

    @Test
    public void testNonResolvableURLAllowedIO() throws IOException {
        Assume.assumeFalse("Query parameters are not supported by file URLConnection on Windows", OSUtils.isWindows());
        setupEnv();
        File file = File.createTempFile("Test", ".java");
        file.deleteOnExit();
        String text;
        try (FileWriter w = new FileWriter(file)) {
            text = "// Test";
            w.write(text);
        }
        Source src = Source.newBuilder("TestJava", queryURL(file.toURI())).build();
        assertNotNull(src);
        assertTrue(text.contentEquals(src.getCharacters()));

        assertEquals("text/plain", Source.findMimeType(queryURL(file.toURI())));
    }

    @Test
    public void testNonResolvableURLDeniedIO() throws IOException {
        setupEnv(Context.create());
        File file = File.createTempFile("Test", ".java");
        file.deleteOnExit();
        String text;
        try (FileWriter w = new FileWriter(file)) {
            text = "// Test";
            w.write(text);
        }
        try {
            Source.newBuilder("TestJava", queryURL(file.toURI())).build();
            fail("Expected SecurityException");
        } catch (SecurityException se) {
            // expected SecurityException
        }
        try {
            Source.findMimeType(queryURL(file.toURI()));
            fail("Expected SecurityException");
        } catch (SecurityException se) {
            // expected SecurityException
        }
    }

    @Test
    public void testNonResolvableURLCustomFileSystem() throws IOException {
        MemoryFileSystem fs = new MemoryFileSystem();
        Path path = fs.parsePath("/Test.java");
        String text;
        try (OutputStream out = Channels.newOutputStream(fs.newByteChannel(path, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE)))) {
            text = "// Test";
            out.write(text.getBytes());
        }
        setupEnv(Context.newBuilder().allowIO(true).fileSystem(fs).build());
        try {
            Source.newBuilder("TestJava", queryURL(path.toUri())).build();
            fail("Expected SecurityException");
        } catch (SecurityException se) {
            // expected SecurityException
        }
        try {
            Source.findMimeType(queryURL(path.toUri()));
            fail("Expected SecurityException");
        } catch (SecurityException se) {
            // expected SecurityException
        }
    }

    @Test
    public void testCanonicalizedSourcePathByDefault() throws IOException {
        Assume.assumeFalse("Link creation requires a special privilege on Windows", OSUtils.isWindows());
        setupEnv();
        TruffleFile tempDir = languageEnv.createTempDirectory(null, "sourcePathCanonicalizationDefault").getCanonicalFile();
        try {
            TruffleFile sourceFile = tempDir.resolve("sourceFile");
            sourceFile.createFile();

            TruffleFile symlink = tempDir.resolve("symlink");
            symlink.createSymbolicLink(sourceFile);

            Source source = Source.newBuilder("TestJava", symlink).content("hello").build();
            assertEquals(sourceFile.getPath(), source.getPath());
        } finally {
            for (TruffleFile f : tempDir.list()) {
                f.delete();
            }
            tempDir.delete();
        }
    }

    @Test
    public void testNotCanonicalizedSymlinkSourcePath() throws IOException {
        Assume.assumeFalse("Link creation requires a special privilege on Windows", OSUtils.isWindows());
        setupEnv();
        TruffleFile tempDir = languageEnv.createTempDirectory(null, "sourcePathCanonicalizationSymlink").getCanonicalFile();
        try {
            TruffleFile sourceFile = tempDir.resolve("sourceFile");
            sourceFile.createFile();

            TruffleFile symlink = tempDir.resolve("symlink");
            symlink.createSymbolicLink(sourceFile);

            Source source = Source.newBuilder("TestJava", symlink).canonicalizePath(false).content("hello").build();
            assertEquals(symlink.getPath(), source.getPath());
        } finally {
            for (TruffleFile f : tempDir.list()) {
                f.delete();
            }
            tempDir.delete();
        }
    }

    @Test
    public void testNotCanonicalizedSymlinkRelativeSourcePath() throws IOException {
        Assume.assumeFalse("Link creation requires a special privilege on Windows", OSUtils.isWindows());
        setupEnv();
        TruffleFile oldCWD = languageEnv.getCurrentWorkingDirectory();
        TruffleFile tempDir = languageEnv.createTempDirectory(null, "sourcePathCanonicalizationRelative").getCanonicalFile();
        try {
            languageEnv.setCurrentWorkingDirectory(tempDir);

            TruffleFile sourceFile = languageEnv.getInternalTruffleFile("sourceFile");
            sourceFile.createFile();

            TruffleFile symlink = languageEnv.getInternalTruffleFile("symlink");
            symlink.createSymbolicLink(sourceFile);

            Source source = Source.newBuilder("TestJava", symlink).canonicalizePath(false).content("hello").build();
            assertEquals("symlink", source.getPath());
        } finally {
            languageEnv.setCurrentWorkingDirectory(oldCWD);
            for (TruffleFile f : tempDir.list()) {
                f.delete();
            }
            tempDir.delete();
        }
    }

    @Test
    public void testNotCanonicalizedNotExistingSourcePath() throws IOException {
        Assume.assumeFalse("Link creation requires a special privilege on Windows", OSUtils.isWindows());
        setupEnv();
        TruffleFile tempDir = languageEnv.createTempDirectory(null, "sourcePathCanonicalizationNotExist").getCanonicalFile();
        try {
            TruffleFile sourceFile = tempDir.resolve("sourceFile");

            TruffleFile symlink = tempDir.resolve("symlink");
            symlink.createSymbolicLink(sourceFile);

            Assert.assertFalse(sourceFile.exists());
            Assert.assertFalse(symlink.exists());

            Source source = Source.newBuilder("TestJava", symlink).canonicalizePath(false).content("hello").build();
            assertEquals(symlink.getPath(), source.getPath());
        } finally {
            for (TruffleFile f : tempDir.list()) {
                f.delete();
            }
            tempDir.delete();
        }
    }

    private static URL queryURL(URI uri) throws MalformedURLException {
        return new URL(uri.toString() + "?query");
    }
}
