/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.suites.wasi;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.utils.WasmBinaryTools;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class WasiOptionsSuite {
    @Parameterized.Parameter() public String dir;

    @Parameterized.Parameter(1) public String absolutePath;

    @Parameterized.Parameters(name = "{0}")
    public static String[][] dirs() {
        return new String[][]{
                        {".", "/test/data/foo"},
                        {"/", "/foo"},
                        {"/tmp", "/tmp/foo"},
                        {"./tmp", "/test/data/tmp/foo"},
                        {".::.", "/test/data/foo"},
                        {".::/", "/test/data/foo"},
                        {".::/tmp", "/test/data/foo"},
                        {".::./tmp", "/test/data/foo"},
                        {"/::.", "/foo"},
                        {"/::/", "/foo"},
                        {"/::/tmp", "/foo"},
                        {"/::./tmp", "/foo"},
                        {"/tmp::.", "/tmp/foo"},
                        {"/tmp::/", "/tmp/foo"},
                        {"/tmp::/tmp", "/tmp/foo"},
                        {"/tmp::./tmp", "/tmp/foo"},
                        {"./tmp::.", "/test/data/tmp/foo"},
                        {"./tmp::/", "/test/data/tmp/foo"},
                        {"./tmp::/tmp", "/test/data/tmp/foo"},
                        {"./tmp::./tmp", "/test/data/tmp/foo"}
        };
    }

    private static class TestFileSystem implements FileSystem {

        @Override
        public Path parsePath(URI uri) {
            return Path.of(uri);
        }

        @Override
        public Path parsePath(String path) {
            return Path.of(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) {

        }

        @Override
        public void delete(Path path) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path toAbsolutePath(Path path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) {
            return Path.of("/test", "data").resolve(path);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void test() throws IOException, InterruptedException {
        final byte[] source = WasmBinaryTools.compileWat("main", "" +
                        "(module" +
                        "   (import \"wasi_snapshot_preview1\" \"path_create_directory\" (func $path_create_dir (param i32 i32 i32) (result i32)))" +
                        "   (memory 1)" +
                        "   (data (i32.const 0) \"dir\")" +
                        "   (data (i32.const 3) \"./tmp\")" +
                        "   (data (i32.const 8) \"" + absolutePath + "\")" +
                        "   (export \"memory\" (memory 0))" +
                        "   (func (export \"relative\") (result i32)" +
                        "       (call $path_create_dir" +
                        "           (i32.const 3)" +
                        "           (i32.const 0)" +
                        "           (i32.const 3)" +
                        "       )" +
                        "   )" +
                        "   (func (export \"direct\") (result i32)" +
                        "       (call $path_create_dir" +
                        "           (i32.const 3)" +
                        "           (i32.const 3)" +
                        "           (i32.const 5)" +
                        "       )" +
                        "   )" +
                        "   (func (export \"absolute\") (result i32)" +
                        "       (call $path_create_dir" +
                        "           (i32.const 3)" +
                        "           (i32.const 8)" +
                        "           (i32.const " + absolutePath.length() + ")" +
                        "       )" +
                        "   )" +
                        ")");

        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.allowIO(IOAccess.newBuilder().fileSystem(new TestFileSystem()).build());
        contextBuilder.option("wasm.Builtins", "wasi_snapshot_preview1");
        contextBuilder.option("wasm.WasiMapDirs", dir);
        try (Context context = contextBuilder.build()) {
            final Source s = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(source), "main").build();
            context.eval(s);
            final Value main = context.getBindings(WasmLanguage.ID).getMember("main");
            final Value relative = main.getMember("relative");
            final Value direct = main.getMember("direct");
            final Value absolute = main.getMember("absolute");
            Assert.assertEquals("Expected success result", 0, relative.execute().asInt());
            Assert.assertEquals("Expected success result", 0, direct.execute().asInt());
            Assert.assertEquals("Expected success result", 0, absolute.execute().asInt());
        }
    }
}
