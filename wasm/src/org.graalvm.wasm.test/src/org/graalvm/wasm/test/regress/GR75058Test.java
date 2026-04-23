/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.regress;

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.predefined.wasi.types.Errno;
import org.junit.Test;

public class GR75058Test {
    @Test
    public void testPathOpenCannotEscapePreopenedDirectoryThroughSymlink() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Path outsideDirectory = Files.createDirectory(tempRoot.resolve("outside"));
            Files.writeString(preopenedDirectory.resolve("inside.txt"), "inside", StandardCharsets.UTF_8);
            Files.writeString(outsideDirectory.resolve("secret.txt"), "secret", StandardCharsets.UTF_8);
            Files.createSymbolicLink(preopenedDirectory.resolve("escape"), outsideDirectory.toAbsolutePath());

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_open"
                                (func $path_open (param i32 i32 i32 i32 i32 i64 i64 i32 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) "inside.txt")
                              (data (i32.const 16) "escape/secret.txt")
                              (export "memory" (memory 0))
                              (func (export "direct") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 0)   ;; dirflags
                                  (i32.const 0)   ;; path pointer
                                  (i32.const 10)  ;; path length
                                  (i32.const 0)   ;; oflags
                                  (i64.const 0)   ;; rights base
                                  (i64.const 0)   ;; rights inheriting
                                  (i32.const 0)   ;; fdflags
                                  (i32.const 64)  ;; fd address
                                ))
                              (func (export "escaped") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 0)   ;; dirflags
                                  (i32.const 16)  ;; path pointer
                                  (i32.const 17)  ;; path length
                                  (i32.const 0)   ;; oflags
                                  (i64.const 0)   ;; rights base
                                  (i64.const 0)   ;; rights inheriting
                                  (i32.const 0)   ;; fdflags
                                  (i32.const 64)  ;; fd address
                                )))
                            """));

            Source source = Source.newBuilder(WasmLanguage.ID, binary, "main").build();
            try (Context context = Context.newBuilder(WasmLanguage.ID).allowExperimentalOptions(true).allowIO(IOAccess.ALL).option("wasm.Builtins", "wasi_snapshot_preview1").option("wasm.WasiMapDirs",
                            "test::" + preopenedDirectory.toAbsolutePath()).build()) {
                Value exports = context.eval(source).newInstance().getMember("exports");
                assertEquals(Errno.Success.ordinal(), exports.getMember("direct").execute().asInt());
                assertNotEquals(Errno.Success.ordinal(), exports.getMember("escaped").execute().asInt());
            }
        } finally {
            deleteTree(tempRoot);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
