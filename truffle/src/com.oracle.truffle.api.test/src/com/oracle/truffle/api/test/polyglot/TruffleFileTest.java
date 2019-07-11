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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.test.OSUtils;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TruffleFileTest extends AbstractPolyglotTest {

    @Before
    public void setUp() {
        setupEnv();
    }

    @Test
    public void testToAbsolutePath() {
        TruffleFile file = languageEnv.getPublicTruffleFile(OSUtils.isUnix() ? "/a/b" : "C:/a/b");
        Assert.assertTrue(file.isAbsolute());
        Assert.assertEquals(file, file.getAbsoluteFile());
        testToAbsolutePathImpl("");
        testToAbsolutePathImpl("a");
        testToAbsolutePathImpl("a/b");
        testToAbsolutePathImpl(".");
        testToAbsolutePathImpl("./");
        testToAbsolutePathImpl("..");
        testToAbsolutePathImpl("a/../");
        testToAbsolutePathImpl("./a/b");
        testToAbsolutePathImpl("a/../b");
        testToAbsolutePathImpl("a/../../");

        languageEnv.setCurrentWorkingDirectory(languageEnv.getPublicTruffleFile(OSUtils.isUnix() ? "/" : "C:/"));
        testToAbsolutePathImpl("");
        testToAbsolutePathImpl("a");
        testToAbsolutePathImpl("a/b");
        testToAbsolutePathImpl(".");
        testToAbsolutePathImpl("./");
        testToAbsolutePathImpl("..");
        testToAbsolutePathImpl("a/../");
        testToAbsolutePathImpl("./a/b");
        testToAbsolutePathImpl("a/../b");
        testToAbsolutePathImpl("a/../../");
    }

    private void testToAbsolutePathImpl(String path) {
        TruffleFile relativeFile = languageEnv.getPublicTruffleFile(path);
        Assert.assertFalse(relativeFile.isAbsolute());
        TruffleFile absoluteFile = relativeFile.getAbsoluteFile();
        TruffleFile cwd = languageEnv.getCurrentWorkingDirectory();
        TruffleFile expectedFile = cwd.resolve(relativeFile.getPath());
        Assert.assertEquals(expectedFile, absoluteFile);
        Assert.assertEquals(expectedFile.normalize(), absoluteFile.normalize());
    }

    @Test
    public void testGetName() {
        TruffleFile file = languageEnv.getPublicTruffleFile("/folder/filename");
        Assert.assertEquals("filename", file.getName());
        file = languageEnv.getPublicTruffleFile("/filename");
        Assert.assertEquals("filename", file.getName());
        file = languageEnv.getPublicTruffleFile("folder/filename");
        Assert.assertEquals("filename", file.getName());
        file = languageEnv.getPublicTruffleFile("filename");
        Assert.assertEquals("filename", file.getName());
        file = languageEnv.getPublicTruffleFile("");
        Assert.assertEquals("", file.getName());
        file = languageEnv.getPublicTruffleFile("/");
        Assert.assertNull(file.getName());
    }

    @Test
    public void testGetMimeType() throws IOException {
        TruffleFile file = languageEnv.getPublicTruffleFile("/folder/filename.duplicate");
        String result = file.getMimeType();
        assertNull(result);
        assertEquals(1, BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).resetFindMimeTypeCalled());
        assertEquals(1, BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).resetFindMimeTypeCalled());
        BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).setMimeType(null);
        BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).setMimeType("text/x-duplicate-mime");
        result = file.getMimeType();
        assertEquals("text/x-duplicate-mime", result);
        BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).setMimeType("text/x-duplicate-mime");
        BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).setMimeType(null);
        result = file.getMimeType();
        assertEquals("text/x-duplicate-mime", result);
        BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).setMimeType("text/x-duplicate-mime");
        BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).setMimeType("text/x-duplicate-mime");
        result = file.getMimeType();
        assertEquals("text/x-duplicate-mime", result);
        BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).setMimeType("text/x-duplicate-mime-1");
        BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).setMimeType("text/x-duplicate-mime-2");
        result = file.getMimeType();
        // Order is not deterministic can be either 'text/x-duplicate-mime-1' or
        // 'text/x-duplicate-mime-2'
        assertTrue("text/x-duplicate-mime-1".equals(result) || "text/x-duplicate-mime-2".equals(result));
    }

    public static class BaseDetector implements TruffleFile.FileTypeDetector {

        private static Map<Class<? extends BaseDetector>, BaseDetector> INSTANCES = new HashMap<>();

        private int findMimeTypeCalled;
        private String mimeType;

        protected BaseDetector() {
            INSTANCES.put(getClass(), this);
        }

        int resetFindMimeTypeCalled() {
            int res = findMimeTypeCalled;
            findMimeTypeCalled = 0;
            return res;
        }

        void setMimeType(String value) {
            mimeType = value;
        }

        @Override
        public String findMimeType(TruffleFile file) throws IOException {
            String name = file.getName();
            if (name != null && name.endsWith(".duplicate")) {
                findMimeTypeCalled++;
                return mimeType;
            } else {
                return null;
            }
        }

        @Override
        public Charset findEncoding(TruffleFile file) throws IOException {
            return null;
        }

        @SuppressWarnings("unchecked")
        static <T extends BaseDetector> T getInstance(Class<T> clazz) {
            return (T) INSTANCES.get(clazz);
        }
    }

    @TruffleLanguage.Registration(id = "DuplicateMimeTypeLanguage1", name = "DuplicateMimeTypeLanguage1", characterMimeTypes = "text/x-duplicate-mime", fileTypeDetectors = DuplicateMimeTypeLanguage1.Detector.class)
    public static final class DuplicateMimeTypeLanguage1 extends ProxyLanguage {

        public static final class Detector extends BaseDetector {
        }
    }

    @TruffleLanguage.Registration(id = "DuplicateMimeTypeLanguage2", name = "DuplicateMimeTypeLanguage2", characterMimeTypes = "text/x-duplicate-mime", fileTypeDetectors = DuplicateMimeTypeLanguage2.Detector.class)
    public static final class DuplicateMimeTypeLanguage2 extends ProxyLanguage {

        public static final class Detector extends BaseDetector {
        }

    }

}
