/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.oracle.truffle.api.TruffleFile;
import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InternalResourceTest {

    static class LibraryResource implements InternalResource {

        static final String[] RESOURCES = {"library"};

        static int unpackedCalled;

        @Override
        public void unpackFiles(Path targetDirectory) throws IOException {
            unpackedCalled++;
            for (String resource : RESOURCES) {
                Files.createFile(targetDirectory.resolve(resource));
            }
        }

        @Override
        public String versionHash() {
            return "1";
        }

        @Override
        public String name() {
            return "native-library";
        }
    }

    static class SourcesResource implements InternalResource {

        static final String[] RESOURCES = {"source_1", "source_2", "source_3"};

        static int unpackedCalled;

        @Override
        public void unpackFiles(Path targetDirectory) throws IOException {
            unpackedCalled++;
            for (String resource : RESOURCES) {
                Files.createFile(targetDirectory.resolve(resource));
            }
        }

        @Override
        public String versionHash() {
            return "1";
        }

        @Override
        public String name() {
            return "sources";
        }
    }

    private static void verifyResources(TruffleFile root, String[] resources) {
        assertTrue(root.exists());
        assertTrue(root.isDirectory());
        assertTrue(root.isReadable());
        assertFalse(root.isWritable());
        for (String resource : resources) {
            TruffleFile file = root.resolve(resource);
            assertTrue(file.exists());
            assertTrue(file.isReadable());
            assertFalse(file.isWritable());
        }
    }

    @Registration(/* ... */internalResources = {LibraryResource.class, SourcesResource.class})
    public static class TestResourcesUnpackedOnce extends AbstractExecutableTestLanguage {

        public static final String ID = TestUtils.getDefaultLanguageId(TestResourcesUnpackedOnce.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            LibraryResource.unpackedCalled = 0;
            SourcesResource.unpackedCalled = 0;
            TruffleFile libRoot1 = env.getInternalResource(LibraryResource.class);
            verifyResources(libRoot1, LibraryResource.RESOURCES);
            TruffleFile srcRoot1 = env.getInternalResource(SourcesResource.class);
            verifyResources(srcRoot1, SourcesResource.RESOURCES);
            TruffleFile libRoot2 = env.getInternalResource(LibraryResource.class);
            assertEquals(libRoot1.getAbsoluteFile().getPath(), libRoot2.getAbsoluteFile().getPath());
            TruffleFile srcRoot2 = env.getInternalResource(SourcesResource.class);
            assertEquals(srcRoot1.getAbsoluteFile().getPath(), srcRoot2.getAbsoluteFile().getPath());
            assertEquals(1, LibraryResource.unpackedCalled);
            assertEquals(1, SourcesResource.unpackedCalled);
            return "";
        }
    }

    @Test
    public void test() {
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.execute(context, TestResourcesUnpackedOnce.class);
        }
    }

}
