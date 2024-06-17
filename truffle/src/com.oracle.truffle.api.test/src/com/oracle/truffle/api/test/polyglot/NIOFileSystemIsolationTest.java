/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class NIOFileSystemIsolationTest {

    private static final String RESOURCE_NAME = "/resources/file";
    private static final String CONTENT_1 = "context_1";
    private static final String CONTENT_2 = "context_2";

    @Test
    public void testFileSystemIsolation() throws IOException {
        Map<String, String> files1 = Map.of(RESOURCE_NAME, CONTENT_1);
        Map<String, String> files2 = Map.of(RESOURCE_NAME, CONTENT_2);
        try (java.nio.file.FileSystem fileSystemSandbox1 = createSandbox(files1);
                        java.nio.file.FileSystem fileSystemSandbox2 = createSandbox(files2)) {
            IOAccess ioAccess1 = IOAccess.newBuilder().fileSystem(FileSystem.newFileSystem(fileSystemSandbox1)).build();
            IOAccess ioAccess2 = IOAccess.newBuilder().fileSystem(FileSystem.newFileSystem(fileSystemSandbox2)).build();
            try (Context context1 = Context.newBuilder().allowIO(ioAccess1).build();
                            Context context2 = Context.newBuilder().allowIO(ioAccess2).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(context1, TestFileSystemIsolationLanguage.class, "", CONTENT_1);
                AbstractExecutableTestLanguage.evalTestLanguage(context2, TestFileSystemIsolationLanguage.class, "", CONTENT_2);
            }
        }
    }

    @Registration
    public static final class TestFileSystemIsolationLanguage extends AbstractExecutableTestLanguage {
        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile file = env.getPublicTruffleFile(RESOURCE_NAME);
            String content = new String(file.readAllBytes(), StandardCharsets.UTF_8);
            String expected = interop.asString(contextArguments[0]);
            Assert.assertEquals(expected, content);
            return null;
        }
    }

    private static java.nio.file.FileSystem createSandbox(Map<String, String> resources) throws IOException {
        Path zipFile = Files.createTempFile("sandbox", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (Map.Entry<String, String> e : resources.entrySet()) {
                ZipEntry file = new ZipEntry(e.getKey());
                out.putNextEntry(file);
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
        return FileSystems.newFileSystem(zipFile, NIOFileSystemIsolationTest.class.getClassLoader());
    }
}
