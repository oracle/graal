/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Source.Builder;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceSectionDispatch;
import org.graalvm.polyglot.io.ByteSequence;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.OSUtils;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class SourceAPITest {

    @Test
    public void testCharSequenceNotMaterialized() throws IOException {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        AtomicBoolean materialized = new AtomicBoolean(false);
        String testString = CharSequenceNotMaterializedLanguage.TEST_STRING;
        Source source = Source.newBuilder(CharSequenceNotMaterializedLanguage.ID, new CharSequence() {

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

        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, CharSequenceNotMaterializedLanguage.class, source);

            assertEquals(1, source.getLineCount());
            assertTrue(equalsCharSequence(testString, source.getCharacters()));
            assertTrue(equalsCharSequence(testString, source.getCharacters(1)));
            assertEquals(0, source.getLineStartOffset(1));
            assertNull(source.getURL());
            assertNotNull(source.getName());
            assertNull(source.getPath());
            assertEquals(6, source.getColumnNumber(5));
            assertEquals(CharSequenceNotMaterializedLanguage.ID, source.getLanguage());
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
        }
    }

    @Registration
    public static final class CharSequenceNotMaterializedLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(CharSequenceNotMaterializedLanguage.class);
        static final String TEST_STRING = "testString";

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) throws Exception {
            com.oracle.truffle.api.source.Source source = request.getSource();
            assertEquals(1, source.getLineCount());
            assertTrue(equalsCharSequence(TEST_STRING, source.getCharacters()));
            assertTrue(equalsCharSequence(TEST_STRING, source.getCharacters(1)));
            assertEquals(0, source.getLineStartOffset(1));
            assertNull(source.getURL());
            assertNotNull(source.getName());
            assertNull(source.getPath());
            assertEquals(6, source.getColumnNumber(5));
            assertEquals(ID, source.getLanguage());
            assertEquals(TEST_STRING.length(), source.getLength());
            assertFalse(source.isInteractive());
            assertFalse(source.isInternal());

            // consume reader CharSequence should not be materialized
            CharBuffer charBuffer = CharBuffer.allocate(source.getLength());
            Reader reader = source.getReader();
            reader.read(charBuffer);
            charBuffer.position(0);
            assertEquals(TEST_STRING, charBuffer.toString());
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
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
        ByteSequence sequence = BinarySourcesLanguage.TEST_SEQUENCE;
        Source source = Source.newBuilder(BinarySourcesLanguage.ID, sequence, null).cached(false).mimeType(BinarySourcesLanguage.MIME).buildLiteral();

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

        assertEquals(BinarySourcesLanguage.MIME, source.getMimeType());
        assertEquals(BinarySourcesLanguage.ID, source.getLanguage());
        assertSame(sequence, source.getBytes());
        assertEquals("Unnamed", source.getName());
        assertNull(source.getURL());
        assertEquals("truffle:9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a/Unnamed", source.getURI().toString());

        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, BinarySourcesLanguage.class, source);
        }
    }

    @Registration(byteMimeTypes = BinarySourcesLanguage.MIME)
    public static final class BinarySourcesLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(BinarySourcesLanguage.class);
        static final String MIME = "application/x-TestBinarySourcesLanguage";
        static final ByteSequence TEST_SEQUENCE = ByteSequence.create(new byte[]{1, 2, 3, 4});

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) throws Exception {
            com.oracle.truffle.api.source.Source source = request.getSource();
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

            assertEquals(MIME, source.getMimeType());
            assertEquals(ID, source.getLanguage());
            assertArrayEquals(TEST_SEQUENCE.toByteArray(), source.getBytes().toByteArray());
            assertEquals("Unnamed", source.getName());
            assertNull(source.getURL());
            assertEquals("truffle:9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a/Unnamed", source.getURI().toString());
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
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

        try (Context context = Context.create()) {
            Source source = Source.newBuilder(MimeTypesLanguage.ID, "", "").buildLiteral();
            AbstractExecutableTestLanguage.parseTestLanguage(context, MimeTypesLanguage.class, source);
            source = Source.newBuilder(MimeTypesLanguage.ID, "", "").mimeType(MimeTypesLanguage.MIME_1).buildLiteral();
            AbstractExecutableTestLanguage.parseTestLanguage(context, MimeTypesLanguage.class, source, MimeTypesLanguage.MIME_1);
            source = Source.newBuilder(MimeTypesLanguage.ID, "", "").mimeType(MimeTypesLanguage.MIME_2).buildLiteral();
            AbstractExecutableTestLanguage.parseTestLanguage(context, MimeTypesLanguage.class, source, MimeTypesLanguage.MIME_2);
        }
    }

    @Registration(characterMimeTypes = {MimeTypesLanguage.MIME_1, MimeTypesLanguage.MIME_2}, defaultMimeType = MimeTypesLanguage.MIME_1)
    public static final class MimeTypesLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(MimeTypesLanguage.class);

        static final String MIME_1 = "text/a";
        static final String MIME_2 = "application/a";

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) {
            com.oracle.truffle.api.source.Source source = request.getSource();
            String expectedMimeType = contextArguments.length == 0 ? null : (String) contextArguments[0];
            assertEquals(expectedMimeType, source.getMimeType());
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
    }

    @Test
    public void testBuildBinarySources() throws IOException {
        try (Context context = Context.create()) {
            ByteSequence bytes = ByteSequence.create(new byte[8]);
            Source source = Source.newBuilder(BuildBinarySourcesLanguage.ID, bytes, null).mimeType(BuildBinarySourcesLanguage.MIME_BINARY).build();
            assertTrue(source.hasBytes());
            assertFalse(source.hasCharacters());
            AbstractExecutableTestLanguage.parseTestLanguage(context, BuildBinarySourcesLanguage.class, source, true, false);

            source = Source.newBuilder(BuildBinarySourcesLanguage.ID, "", null).content(bytes).mimeType(BuildBinarySourcesLanguage.MIME_BINARY).build();
            assertTrue(source.hasBytes());
            assertFalse(source.hasCharacters());
            AbstractExecutableTestLanguage.parseTestLanguage(context, BuildBinarySourcesLanguage.class, source, true, false);

            source = Source.newBuilder(BuildBinarySourcesLanguage.ID, bytes, null).content("").build();
            assertFalse(source.hasBytes());
            assertTrue(source.hasCharacters());
            AbstractExecutableTestLanguage.parseTestLanguage(context, BuildBinarySourcesLanguage.class, source, false, true);

            File file = File.createTempFile("Hello", ".bin").getCanonicalFile();
            file.deleteOnExit();

            // mime-type not specified + invalid langauge -> characters
            source = Source.newBuilder(BuildBinarySourcesLanguage.ID, file).build();
            assertFalse(source.hasBytes());
            assertTrue(source.hasCharacters());
            AbstractExecutableTestLanguage.parseTestLanguage(context, BuildBinarySourcesLanguage.class, source, false, true);

            // mime-type not specified + invalid langauge -> characters
            source = Source.newBuilder(BuildBinarySourcesLanguage.ID, file).content(bytes).mimeType(BuildBinarySourcesLanguage.MIME_BINARY).build();
            assertTrue(source.hasBytes());
            assertFalse(source.hasCharacters());
            AbstractExecutableTestLanguage.parseTestLanguage(context, BuildBinarySourcesLanguage.class, source, true, false);

            source = Source.newBuilder(BuildBinarySourcesLanguage.ID, file).content("").build();
            assertFalse(source.hasBytes());
            assertTrue(source.hasCharacters());
            AbstractExecutableTestLanguage.parseTestLanguage(context, BuildBinarySourcesLanguage.class, source, false, true);
        }
    }

    @Registration(characterMimeTypes = BuildBinarySourcesLanguage.MIME_TEXT, byteMimeTypes = BuildBinarySourcesLanguage.MIME_BINARY, defaultMimeType = BuildBinarySourcesLanguage.MIME_TEXT)
    public static final class BuildBinarySourcesLanguage extends AbstractExecutableTestLanguage {

        static final String MIME_TEXT = "text/a";

        static final String MIME_BINARY = "application/a";

        static final String ID = TestUtils.getDefaultLanguageId(BuildBinarySourcesLanguage.class);

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) {
            com.oracle.truffle.api.source.Source source = request.getSource();
            boolean expectHasBytes = (boolean) contextArguments[0];
            boolean expectHasCharacters = (boolean) contextArguments[1];
            assertEquals(expectHasBytes, source.hasBytes());
            assertEquals(expectHasCharacters, source.hasCharacters());
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
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

        String text = FromTextWithFileURILanguage.CONTENT;

        Source source = Source.newBuilder(FromTextWithFileURILanguage.ID, text, "another.tjs").uri(file.toURI()).buildLiteral();
        assertEquals("The content has been changed", text, source.getCharacters());
        assertNull("Mime type not specified", source.getMimeType());
        assertNull("Null MIME type", source.getMimeType());
        assertEquals("another.tjs", source.getName());
        assertEquals("Using the specified URI", file.toURI(), source.getURI());
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, FromTextWithFileURILanguage.class, source, file.toURI().toString());
        }
    }

    @Registration
    public static final class FromTextWithFileURILanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(FromTextWithFileURILanguage.class);

        static final String CONTENT = "// Hello";

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) {
            com.oracle.truffle.api.source.Source source = request.getSource();
            URI expectedURI = URI.create((String) contextArguments[0]);
            assertEquals("The content has been changed", CONTENT, source.getCharacters());
            assertNull("Mime type not specified", source.getMimeType());
            assertNull("Null MIME type", source.getMimeType());
            assertEquals("another.tjs", source.getName());
            assertEquals("Using the specified URI", expectedURI, source.getURI());
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
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

    @SuppressWarnings("deprecation")
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
        final String code = LiteralSourcesLanguage.CODE;
        final String description = LiteralSourcesLanguage.DESCRIPTION;
        final Source literal = Source.newBuilder(LiteralSourcesLanguage.ID, code, description).name(description).build();
        assertEquals(literal.getLanguage(), LiteralSourcesLanguage.ID);
        assertEquals(literal.getName(), description);
        assertEquals(literal.getCharacters(), code);
        assertNull(literal.getMimeType());
        assertNull(literal.getURL());
        assertNotNull("Every source must have URI", literal.getURI());
        final char[] buffer = new char[code.length()];
        assertEquals(literal.getReader().read(buffer), code.length());
        assertEquals(new String(buffer), code);
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, LiteralSourcesLanguage.class, literal);
        }
    }

    @Registration
    public static final class LiteralSourcesLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(LiteralSourcesLanguage.class);

        static final String CODE = "test code";
        static final String DESCRIPTION = "test description";

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) {
            try {
                com.oracle.truffle.api.source.Source source = request.getSource();
                assertEquals(source.getLanguage(), LiteralSourcesLanguage.ID);
                assertEquals(source.getName(), DESCRIPTION);
                assertEquals(source.getCharacters(), CODE);
                assertNull(source.getMimeType());
                assertNull(source.getURL());
                assertNotNull("Every source must have URI", source.getURI());
                final char[] buffer = new char[source.getLength()];
                assertEquals(source.getReader().read(buffer), source.getLength());
                assertEquals(new String(buffer), CODE);
            } catch (IOException ioe) {
                throw new AssertionError("Unexpected IOException", ioe);
            }
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
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

    @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    @Test
    public void testHttpURL() throws IOException, URISyntaxException {
        URL resource = new URL(HttpURLLanguage.RESOURCE);
        Source s = Source.newBuilder(HttpURLLanguage.ID, resource).content("Empty").build();
        // The URL is converted into URI before comparison to skip the expensive hostname
        // normalization.
        assertEquals(resource.toURI(), s.getURL().toURI());
        assertEquals(resource.toURI(), s.getURI());
        assertEquals("File.html", s.getName());
        assertEquals("/test/File.html", s.getPath());
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, HttpURLLanguage.class, s);
        }
    }

    @Registration
    public static final class HttpURLLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(HttpURLLanguage.class);

        static final String RESOURCE = "http://example.org/test/File.html";

        @SuppressWarnings("deprecation")
        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) {
            try {
                com.oracle.truffle.api.source.Source source = request.getSource();
                URL resource = new URL(RESOURCE);
                assertEquals(resource.toURI(), source.getURL().toURI());
                assertEquals(resource.toURI(), source.getURI());
                assertEquals("File.html", source.getName());
                assertEquals("/test/File.html", source.getPath());
            } catch (IOException | URISyntaxException e) {
                throw new AssertionError(e.getMessage(), e);
            }
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
    }

    @Test
    public void whatAreTheDefaultValuesOfNewFromReader() throws Exception {
        String content = WhatAreTheDefaultValuesOfNewFromReaderLanguage.CONTENT;
        String name = WhatAreTheDefaultValuesOfNewFromReaderLanguage.NAME;
        StringReader r = new StringReader(content);
        Source source = Source.newBuilder(WhatAreTheDefaultValuesOfNewFromReaderLanguage.ID, r, name).build();

        assertEquals(content, source.getCharacters());
        assertEquals(name, source.getName());
        assertEquals(WhatAreTheDefaultValuesOfNewFromReaderLanguage.ID, source.getLanguage());
        assertNull(source.getPath());
        assertNotNull(source.getURI());
        assertTrue("URI ends with the name", source.getURI().toString().endsWith(name));
        assertEquals("truffle", source.getURI().getScheme());
        assertNull(source.getURL());
        assertNull(source.getMimeType());
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, WhatAreTheDefaultValuesOfNewFromReaderLanguage.class, source);
        }
    }

    @Registration
    public static final class WhatAreTheDefaultValuesOfNewFromReaderLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(WhatAreTheDefaultValuesOfNewFromReaderLanguage.class);
        static final String NAME = "almostEmpty";
        static final String CONTENT = "Hi!";

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) {
            com.oracle.truffle.api.source.Source source = request.getSource();
            assertEquals(CONTENT, source.getCharacters());
            assertEquals(NAME, source.getName());
            assertEquals(WhatAreTheDefaultValuesOfNewFromReaderLanguage.ID, source.getLanguage());
            assertNull(source.getPath());
            assertNotNull(source.getURI());
            assertTrue("URI ends with the name", source.getURI().toString().endsWith(NAME));
            assertEquals("truffle", source.getURI().getScheme());
            assertNull(source.getURL());
            assertNull(source.getMimeType());
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
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
        Source source = Source.newBuilder(NormalSourceIsNotInterLanguage.ID, "anything", "name").buildLiteral();

        assertFalse("Not internal", source.isInternal());
        assertFalse("Not interactive", source.isInteractive());

        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, NormalSourceIsNotInterLanguage.class, source);
        }
    }

    @Registration
    public static final class NormalSourceIsNotInterLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(NormalSourceIsNotInterLanguage.class);

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) {
            com.oracle.truffle.api.source.Source source = request.getSource();
            assertFalse("Not internal", source.isInternal());
            assertFalse("Not interactive", source.isInteractive());
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
    }

    @Test
    public void markSourceAsInternal() {
        Source source = Source.newBuilder(MarkSourceAsInternalLanguage.ID, "anything", "name").internal(true).buildLiteral();

        assertTrue("This source is internal", source.isInternal());

        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, MarkSourceAsInternalLanguage.class, source);
        }
    }

    @Registration
    public static final class MarkSourceAsInternalLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(MarkSourceAsInternalLanguage.class);

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) {
            com.oracle.truffle.api.source.Source source = request.getSource();
            assertTrue("This source is internal", source.isInternal());
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
    }

    @Test
    public void markSourceAsInteractive() {
        Source source = Source.newBuilder(MarkSourceAsInteractiveLanguage.ID, "anything", "name").interactive(true).buildLiteral();

        assertTrue("This source is interactive", source.isInteractive());

        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, MarkSourceAsInteractiveLanguage.class, source);
        }
    }

    @Registration
    public static final class MarkSourceAsInteractiveLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(MarkSourceAsInteractiveLanguage.class);

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) {
            com.oracle.truffle.api.source.Source source = request.getSource();
            assertTrue("This source is interactive", source.isInteractive());
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }
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

    @SuppressWarnings("deprecation")
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
        AbstractPolyglotImpl polyglot = (AbstractPolyglotImpl) ReflectionUtils.invokeStatic(Engine.class, "getImpl");
        com.oracle.truffle.api.source.Source truffleSource = com.oracle.truffle.api.source.Source.newBuilder(ProxyLanguage.ID, "x", "name").content(
                        com.oracle.truffle.api.source.Source.CONTENT_NONE).build();
        Class<?>[] sourceConstructorTypes = new Class[]{AbstractSourceDispatch.class, Object.class};
        Source source = ReflectionUtils.newInstance(Source.class, sourceConstructorTypes,
                        polyglot.getAPIAccess().getSourceDispatch(Source.create(ProxyLanguage.ID, "")), truffleSource);
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
        Class<?>[] sectionConstructorTypes = new Class[]{Source.class, AbstractSourceSectionDispatch.class, Object.class};
        SourceSection section = ReflectionUtils.newInstance(SourceSection.class, sectionConstructorTypes, source,
                        getSourceSectionDispatch(polyglot), truffleSection);
        assertFalse(section.hasCharIndex());
        assertTrue(section.hasLines());
        assertTrue(section.hasColumns());
        assertEquals("", section.getCharacters());
        assertTrue(truffleSource.getURI().toString().contains("name"));
    }

    @TruffleLanguage.Registration(id = SourceSectionDispatchLanguage.ID, name = SourceSectionDispatchLanguage.ID, version = "1.0", contextPolicy = TruffleLanguage.ContextPolicy.SHARED, //
                    characterMimeTypes = "application/x-source-section-dispatch-language")
    @ProvidedTags({StandardTags.RootTag.class})
    static class SourceSectionDispatchLanguage extends TruffleLanguage<Object> {
        static final String ID = "SourceAPITest_SourceSectionDispatchLanguage";

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return RootNode.createConstantNode(new SourceSectionProvider(request.getSource())).getCallTarget();
        }
    }

    private static AbstractSourceSectionDispatch getSourceSectionDispatch(AbstractPolyglotImpl polyglot) {
        try (Context context = Context.create(SourceSectionDispatchLanguage.ID)) {
            Value res = context.eval(Source.create(SourceSectionDispatchLanguage.ID, ""));
            SourceSection sourceSection = res.getSourceLocation();
            return polyglot.getAPIAccess().getSourceSectionDispatch(sourceSection);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class SourceSectionProvider implements TruffleObject {

        private final com.oracle.truffle.api.source.Source source;

        SourceSectionProvider(com.oracle.truffle.api.source.Source source) {
            this.source = source;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean hasSourceLocation() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        public com.oracle.truffle.api.source.SourceSection getSourceLocation() {
            return getSection();
        }

        @CompilerDirectives.TruffleBoundary
        private com.oracle.truffle.api.source.SourceSection getSection() {
            return source.createSection(0, 0);
        }
    }

    @SuppressWarnings("deprecation")
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

    @Test
    public void testSourceOptions() {
        assertNotNull(Source.newBuilder("", "", "").option("", "").buildLiteral());
        assertNotNull(Source.newBuilder("", "", "").option("1", "").option("2", "").buildLiteral());
        assertNotNull(Source.newBuilder("", "", "").option("1", "").options(Map.of("", "")).buildLiteral());
        var b = Source.newBuilder("", "", "");
        Assert.assertThrows(NullPointerException.class, () -> b.option(null, ""));
        Assert.assertThrows(NullPointerException.class, () -> b.option("", null));
        Assert.assertThrows(NullPointerException.class, () -> b.options(null));

    }

}
