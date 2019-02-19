/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TruffleFileTypeDetectorTest extends AbstractPolyglotTest {

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
        TruffleFile truffleFile1 = languageEnv.getTruffleFile(testFile1.getAbsolutePath());
        TruffleFile truffleFile2 = languageEnv.getTruffleFile(testFile2.getAbsolutePath());
        TruffleFile truffleFile3 = languageEnv.getTruffleFile(testFile3.getAbsolutePath());

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
        Assert.assertNull(mimeType);

        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile1.toUri().toURL());
        Assert.assertEquals("application/test-js", mimeType);
        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile2.toUri().toURL());
        Assert.assertEquals("text/plain", mimeType);
        mimeType = com.oracle.truffle.api.source.Source.findMimeType(truffleFile3.toUri().toURL());
        Assert.assertNull(mimeType);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testFindMimeTypeFullIO() throws IOException {
        setupEnv(Context.newBuilder().allowIO(true).build());
        TruffleFile truffleFile1 = languageEnv.getTruffleFile(testFile1.getAbsolutePath());
        TruffleFile truffleFile2 = languageEnv.getTruffleFile(testFile2.getAbsolutePath());
        TruffleFile truffleFile3 = languageEnv.getTruffleFile(testFile3.getAbsolutePath());

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
    @SuppressWarnings("deprecation")
    public void testSourceBulderNoIO() throws IOException {
        setupEnv(Context.create());
        TruffleFile truffleFile1 = languageEnv.getTruffleFile(testFile1.getAbsolutePath());
        TruffleFile truffleFile3 = languageEnv.getTruffleFile(testFile3.getAbsolutePath());

        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.newBuilder("TestJS", testFile1).build();
        Assert.assertEquals("application/test-js", source.getMimeType());
        source = org.graalvm.polyglot.Source.newBuilder("TestFooXML", testFile3).build();
        Assert.assertEquals("text/foo+xml", source.getMimeType());

        com.oracle.truffle.api.source.Source truffleSource;
        try {
            truffleSource = com.oracle.truffle.api.source.Source.newBuilder("TestJS", truffleFile1).build();
            Assert.fail("Expected SecurityException");
        } catch (SecurityException ioe) {
        }
        try {
            truffleSource = com.oracle.truffle.api.source.Source.newBuilder("TestFooXML", truffleFile3).build();
            Assert.fail("Expected SecurityException");
        } catch (SecurityException ioe) {
        }

        truffleSource = com.oracle.truffle.api.source.Source.newBuilder("TestJS", truffleFile1.toUri().toURL()).build();
        Assert.assertNull(truffleSource.getMimeType());
        truffleSource = com.oracle.truffle.api.source.Source.newBuilder("TestFooXML", truffleFile3.toUri().toURL()).build();
        Assert.assertNull(truffleSource.getMimeType());

        truffleSource = com.oracle.truffle.api.source.Source.newBuilder(testFile1).build();
        Assert.assertEquals("application/test-js", truffleSource.getMimeType());
        truffleSource = com.oracle.truffle.api.source.Source.newBuilder(testFile3).build();
        Assert.assertEquals("content/unknown", truffleSource.getMimeType());

        truffleSource = com.oracle.truffle.api.source.Source.newBuilder(testFile1.toURI().toURL()).build();
        Assert.assertEquals("application/test-js", truffleSource.getMimeType());
        truffleSource = com.oracle.truffle.api.source.Source.newBuilder(testFile3.toURI().toURL()).build();
        Assert.assertEquals("content/unknown", truffleSource.getMimeType());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testSourceBulderFullIO() throws IOException {
        setupEnv(Context.newBuilder().allowIO(true).build());
        TruffleFile truffleFile1 = languageEnv.getTruffleFile(testFile1.getAbsolutePath());
        TruffleFile truffleFile3 = languageEnv.getTruffleFile(testFile3.getAbsolutePath());

        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.newBuilder("TestJS", testFile1).build();
        Assert.assertEquals("application/test-js", source.getMimeType());
        source = org.graalvm.polyglot.Source.newBuilder("TestFooXML", testFile3).build();
        Assert.assertEquals("text/foo+xml", source.getMimeType());

        com.oracle.truffle.api.source.Source truffleSource = com.oracle.truffle.api.source.Source.newBuilder("TestJS", truffleFile1).build();
        Assert.assertEquals("application/test-js", truffleSource.getMimeType());
        truffleSource = com.oracle.truffle.api.source.Source.newBuilder("TestFooXML", truffleFile3).build();
        Assert.assertEquals("text/foo+xml", truffleSource.getMimeType());

        truffleSource = com.oracle.truffle.api.source.Source.newBuilder(testFile1).build();
        Assert.assertEquals("application/test-js", truffleSource.getMimeType());
        truffleSource = com.oracle.truffle.api.source.Source.newBuilder(testFile3).build();
        Assert.assertEquals("text/foo+xml", truffleSource.getMimeType());

        truffleSource = com.oracle.truffle.api.source.Source.newBuilder(testFile1.toURI().toURL()).build();
        Assert.assertEquals("application/test-js", truffleSource.getMimeType());
        truffleSource = com.oracle.truffle.api.source.Source.newBuilder(testFile3.toURI().toURL()).build();
        Assert.assertEquals("text/foo+xml", truffleSource.getMimeType());
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

    @TruffleLanguage.Registration(id = "TestFooXML", name = "", byteMimeTypes = "text/foo+xml")
    public static class TestJSLanguage extends ProxyLanguage {
    }
}
