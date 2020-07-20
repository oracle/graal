/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FileTypeDetectorTest extends AbstractPolyglotTest {

    private File testFile1;
    private File testFile2;
    private File testFile3;

    @Before
    public void setUp() throws IOException {
        testFile1 = createTmpFile("test", ".tjs");
        testFile2 = createTmpFile("test", ".txt");
        testFile3 = createTmpFile("test", ".xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<!DOCTYPE foo PUBLIC \"foo\"><root></root>");
    }

    @After
    public void tearDown() {
        if (testFile1 != null) {
            testFile1.delete();
        }
        if (testFile2 != null) {
            testFile2.delete();
        }
        if (testFile3 != null) {
            testFile3.delete();
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testFindMimeTypeNoIO() throws IOException {
        setupEnv(Context.create());
        TruffleFile truffleFile1 = languageEnv.getPublicTruffleFile(testFile1.getAbsolutePath());
        TruffleFile truffleFile2 = languageEnv.getPublicTruffleFile(testFile2.getAbsolutePath());
        TruffleFile truffleFile3 = languageEnv.getPublicTruffleFile(testFile3.getAbsolutePath());

        String mimeType = org.graalvm.polyglot.Source.findMimeType(testFile1);
        Assert.assertEquals("application/test-js", mimeType);
        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile2);
        Assert.assertEquals("text/plain", mimeType);
        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile3);
        Assert.assertEquals("text/foo+xml", mimeType);

        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile1.toURI().toURL());
        Assert.assertEquals("application/test-js", mimeType);
        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile2.toURI().toURL());
        Assert.assertEquals("text/plain", mimeType);
        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile3.toURI().toURL());
        Assert.assertEquals("text/foo+xml", mimeType);

        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile1);
        Assert.assertEquals("application/test-js", mimeType);
        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile2);
        Assert.assertEquals("text/plain", mimeType);
        try {
            mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile3);
            Assert.fail("SecurityException is expected");
        } catch (SecurityException se) {
            // Expected
        }

        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile1.toUri().toURL());
        Assert.assertEquals("application/test-js", mimeType);
        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile2.toUri().toURL());
        Assert.assertEquals("text/plain", mimeType);
        try {
            mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile3.toUri().toURL());
            Assert.fail("SecurityException is expected");
        } catch (SecurityException se) {
            // Expected
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testFindMimeTypeFullIO() throws IOException {
        setupEnv(Context.newBuilder().allowIO(true).build());
        TruffleFile truffleFile1 = languageEnv.getPublicTruffleFile(testFile1.getAbsolutePath());
        TruffleFile truffleFile2 = languageEnv.getPublicTruffleFile(testFile2.getAbsolutePath());
        TruffleFile truffleFile3 = languageEnv.getPublicTruffleFile(testFile3.getAbsolutePath());

        String mimeType = org.graalvm.polyglot.Source.findMimeType(testFile1);
        Assert.assertEquals("application/test-js", mimeType);
        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile2);
        Assert.assertEquals("text/plain", mimeType);
        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile3);
        Assert.assertEquals("text/foo+xml", mimeType);

        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile1.toURI().toURL());
        Assert.assertEquals("application/test-js", mimeType);
        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile2.toURI().toURL());
        Assert.assertEquals("text/plain", mimeType);
        mimeType = org.graalvm.polyglot.Source.findMimeType(testFile3.toURI().toURL());
        Assert.assertEquals("text/foo+xml", mimeType);

        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile1);
        Assert.assertEquals("application/test-js", mimeType);
        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile2);
        Assert.assertEquals("text/plain", mimeType);
        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile3);
        Assert.assertEquals("text/foo+xml", mimeType);

        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile1.toUri().toURL());
        Assert.assertEquals("application/test-js", mimeType);
        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile2.toUri().toURL());
        Assert.assertEquals("text/plain", mimeType);
        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile3);
        Assert.assertEquals("text/foo+xml", mimeType);
    }

    @Test
    public void testSourceBulderNoIO() throws IOException {
        setupEnv(Context.create());
        TruffleFile truffleFile1 = languageEnv.getPublicTruffleFile(testFile1.getAbsolutePath());
        TruffleFile truffleFile3 = languageEnv.getPublicTruffleFile(testFile3.getAbsolutePath());

        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.newBuilder("TestJS", testFile1).build();
        Assert.assertEquals("application/test-js", source.getMimeType());
        source = org.graalvm.polyglot.Source.newBuilder("TestFooXML", testFile3).build();
        Assert.assertEquals("text/foo+xml", source.getMimeType());

        try {
            com.oracle.truffle.api.source.Source.newBuilder("TestJS", truffleFile1).build();
            Assert.fail("Expected SecurityException");
        } catch (SecurityException se) {
        }
        try {
            com.oracle.truffle.api.source.Source.newBuilder("TestFooXML", truffleFile3).build();
            Assert.fail("Expected SecurityException");
        } catch (SecurityException se) {
        }

        try {
            com.oracle.truffle.api.source.Source.newBuilder("TestJS", truffleFile1.toUri().toURL()).build();
            Assert.fail("Expected SecurityException");
        } catch (SecurityException se) {
        }
        try {
            com.oracle.truffle.api.source.Source.newBuilder("TestFooXML", truffleFile3.toUri().toURL()).build();
            Assert.fail("Expected SecurityException");
        } catch (SecurityException se) {
        }
    }

    @Test
    public void testSourceBulderFullIO() throws IOException {
        setupEnv(Context.newBuilder().allowIO(true).build());
        TruffleFile truffleFile1 = languageEnv.getPublicTruffleFile(testFile1.getAbsolutePath());
        TruffleFile truffleFile3 = languageEnv.getPublicTruffleFile(testFile3.getAbsolutePath());

        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.newBuilder("TestJS", testFile1).build();
        Assert.assertEquals("application/test-js", source.getMimeType());
        source = org.graalvm.polyglot.Source.newBuilder("TestFooXML", testFile3).build();
        Assert.assertEquals("text/foo+xml", source.getMimeType());

        com.oracle.truffle.api.source.Source truffleSource = com.oracle.truffle.api.source.Source.newBuilder("TestJS", truffleFile1).build();
        Assert.assertEquals("application/test-js", truffleSource.getMimeType());
        truffleSource = com.oracle.truffle.api.source.Source.newBuilder("TestFooXML", truffleFile3).build();
        Assert.assertEquals("text/foo+xml", truffleSource.getMimeType());

    }

    @Test
    public void testFileTypeDetectorFiltering() throws IOException {
        setupEnv(Context.newBuilder().allowIO(true).build());
        File firsta = createTmpFile("test", "." + FirstLanguage.EXT_A, "");
        File firstb = createTmpFile("test", "." + FirstLanguage.EXT_B, "");
        File seconda = createTmpFile("test", "." + SecondLanguage.EXT_A, "");
        File secondb = createTmpFile("test", "." + SecondLanguage.EXT_B, "");

        AbstractFileTypeDetector.active = true;
        FirstFileTypeDetector.events.clear();
        SecondFileTypeDetector.events.clear();
        com.oracle.truffle.api.source.Source.newBuilder(FirstLanguage.LANG_ID, languageEnv.getPublicTruffleFile(firsta.getAbsolutePath())).build();
        Assert.assertEquals(1, FirstFileTypeDetector.events.stream().filter((e) -> e.getType() == AbstractFileTypeDetector.Event.Type.MIME).count());
        Assert.assertEquals(0, FirstFileTypeDetector.events.stream().filter((e) -> e.getType() == AbstractFileTypeDetector.Event.Type.ENCODING).count());
        Assert.assertTrue(SecondFileTypeDetector.events.isEmpty());
        FirstFileTypeDetector.events.clear();
        com.oracle.truffle.api.source.Source.newBuilder(FirstLanguage.LANG_ID, languageEnv.getPublicTruffleFile(firstb.getAbsolutePath())).build();
        Assert.assertEquals(1, FirstFileTypeDetector.events.stream().filter((e) -> e.getType() == AbstractFileTypeDetector.Event.Type.MIME).count());
        Assert.assertEquals(1, FirstFileTypeDetector.events.stream().filter((e) -> e.getType() == AbstractFileTypeDetector.Event.Type.ENCODING).count());
        Assert.assertTrue(SecondFileTypeDetector.events.isEmpty());
        FirstFileTypeDetector.events.clear();

        com.oracle.truffle.api.source.Source.newBuilder(SecondLanguage.LANG_ID, languageEnv.getPublicTruffleFile(seconda.getAbsolutePath())).build();
        Assert.assertTrue(FirstFileTypeDetector.events.isEmpty());
        Assert.assertEquals(1, SecondFileTypeDetector.events.stream().filter((e) -> e.getType() == AbstractFileTypeDetector.Event.Type.MIME).count());
        Assert.assertEquals(1, SecondFileTypeDetector.events.stream().filter((e) -> e.getType() == AbstractFileTypeDetector.Event.Type.ENCODING).count());
        SecondFileTypeDetector.events.clear();
        com.oracle.truffle.api.source.Source.newBuilder(SecondLanguage.LANG_ID, languageEnv.getPublicTruffleFile(secondb.getAbsolutePath())).build();
        Assert.assertTrue(FirstFileTypeDetector.events.isEmpty());
        Assert.assertEquals(1, SecondFileTypeDetector.events.stream().filter((e) -> e.getType() == AbstractFileTypeDetector.Event.Type.MIME).count());
        Assert.assertEquals(1, SecondFileTypeDetector.events.stream().filter((e) -> e.getType() == AbstractFileTypeDetector.Event.Type.ENCODING).count());
        SecondFileTypeDetector.events.clear();
        AbstractFileTypeDetector.active = false;
    }

    private static File createTmpFile(String name, String ext, String... content) throws IOException {
        File tmpFile = File.createTempFile(name, ext);
        if (content.length > 0) {
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"))) {
                for (String line : content) {
                    out.println(line);
                }
            }
        }
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    public static class AbstractFileTypeDetector implements TruffleFile.FileTypeDetector {

        static volatile boolean active = false;
        private final List<? super Event> sink;
        private final Map<String, String> mimeTypes;

        protected AbstractFileTypeDetector(List<? super Event> sink, Map<String, String> mimeTypes) {
            this.sink = sink;
            this.mimeTypes = mimeTypes;
        }

        @Override
        public String findMimeType(TruffleFile file) throws IOException {
            if (active) {
                sink.add(new Event(Event.Type.MIME, file));
            }
            String name = file.getName();
            if (name != null) {
                for (Map.Entry<String, String> e : mimeTypes.entrySet()) {
                    if (name.endsWith(e.getKey())) {
                        return e.getValue();
                    }
                }
            }
            return null;
        }

        @Override
        public Charset findEncoding(TruffleFile file) throws IOException {
            if (active) {
                sink.add(new Event(Event.Type.ENCODING, file));
            }
            return null;
        }

        static Map<String, String> createMimeMap(String... extMimePairs) {
            assert (extMimePairs.length & 1) == 0;
            Map<String, String> res = new HashMap<>();
            for (int i = 0; i < extMimePairs.length; i += 2) {
                res.put("." + extMimePairs[i], extMimePairs[i + 1]);
            }
            return res;
        }

        static final class Event {

            enum Type {
                MIME,
                ENCODING
            }

            private final Type type;
            private final TruffleFile file;

            Event(Type type, TruffleFile file) {
                this.type = type;
                this.file = file;
            }

            Type getType() {
                return type;
            }

            TruffleFile getSource() {
                return file;
            }

            @Override
            public String toString() {
                return String.format("Find {0} for {1}", type, file);
            }
        }
    }

    public static final class FirstFileTypeDetector extends AbstractFileTypeDetector {

        static final List<Event> events = new ArrayList<>();

        public FirstFileTypeDetector() {
            super(events, createMimeMap(FirstLanguage.EXT_A, FirstLanguage.MIME_A, FirstLanguage.EXT_B, FirstLanguage.MIME_B));
        }
    }

    public static final class SecondFileTypeDetector extends AbstractFileTypeDetector {

        static final List<Event> events = new ArrayList<>();

        public SecondFileTypeDetector() {
            super(events, createMimeMap(SecondLanguage.EXT_A, SecondLanguage.MIME_A, SecondLanguage.EXT_B, SecondLanguage.MIME_B));
        }
    }

    @TruffleLanguage.Registration(id = FirstLanguage.LANG_ID, name = "", byteMimeTypes = FirstLanguage.MIME_A, characterMimeTypes = FirstLanguage.MIME_B, defaultMimeType = FirstLanguage.MIME_A, fileTypeDetectors = FirstFileTypeDetector.class)
    public static final class FirstLanguage extends ProxyLanguage {
        public static final String LANG_ID = "FirstFileTypeDetectorLanguage";
        public static final String EXT_A = "firsta";
        public static final String MIME_A = "application/firsta";
        public static final String EXT_B = "firstb";
        public static final String MIME_B = "application/firstb";
    }

    @TruffleLanguage.Registration(id = SecondLanguage.LANG_ID, name = "", characterMimeTypes = {SecondLanguage.MIME_A,
                    SecondLanguage.MIME_B}, defaultMimeType = SecondLanguage.MIME_A, fileTypeDetectors = SecondFileTypeDetector.class)
    public static final class SecondLanguage extends ProxyLanguage {
        public static final String LANG_ID = "SecondFileTypeDetectorLanguage";
        public static final String EXT_A = "seconda";
        public static final String MIME_A = "application/seconda";
        public static final String EXT_B = "secondb";
        public static final String MIME_B = "application/secondb";
    }
}
