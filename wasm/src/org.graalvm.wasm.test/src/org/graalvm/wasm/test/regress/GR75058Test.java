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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.predefined.wasi.types.Errno;
import org.graalvm.wasm.predefined.wasi.types.Filetype;
import org.junit.Assume;
import org.junit.Test;

public class GR75058Test {
    // Verifies that path_open rejects intermediate symlink traversal outside the preopen root.
    @Test
    public void testPathOpenCannotEscapePreopenedDirectoryThroughSymlink() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            assumeSymbolicLinksSupported(tempRoot);
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
                                  (i32.const 0)   ;; dirflags: none
                                  (i32.const 0)   ;; path pointer: "inside.txt"
                                  (i32.const 10)  ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                ))
                              (func (export "escaped") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 0)   ;; dirflags: none
                                  (i32.const 16)  ;; path pointer: "escape/secret.txt"
                                  (i32.const 17)  ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                )))
                            """));

            Source source = Source.newBuilder(WasmLanguage.ID, binary, "main").build();
            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(source).newInstance().getMember("exports");
                assertEquals(Errno.Success.ordinal(), exports.getMember("direct").execute().asInt());
                assertNotEquals(Errno.Success.ordinal(), exports.getMember("escaped").execute().asInt());
            }
        } finally {
            deleteTree(tempRoot);
        }
    }

    // Verifies that path_open allows intermediate symlinks when their resolved target stays inside
    // the preopen root.
    @Test
    public void testPathOpenAllowsIntermediateSymlinkInsidePreopenedDirectory() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            assumeSymbolicLinksSupported(tempRoot);
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Path releasesDirectory = Files.createDirectories(preopenedDirectory.resolve("releases/v1"));
            Files.writeString(releasesDirectory.resolve("log.txt"), "inside", StandardCharsets.UTF_8);
            Files.createSymbolicLink(preopenedDirectory.resolve("current"), releasesDirectory.toAbsolutePath());

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_open"
                                (func $path_open (param i32 i32 i32 i32 i32 i64 i64 i32 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) "current/log.txt")
                              (export "memory" (memory 0))
                              (func (export "open") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 0)   ;; dirflags: none
                                  (i32.const 0)   ;; path pointer: "current/log.txt"
                                  (i32.const 15)  ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                )))
                            """));

            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(Source.newBuilder(WasmLanguage.ID, binary, "main").build()).newInstance().getMember("exports");
                assertEquals(Errno.Success.ordinal(), exports.getMember("open").execute().asInt());
            }
        } finally {
            deleteTree(tempRoot);
        }
    }

    // Verifies that LOOKUP_SYMLINK_FOLLOW still rejects paths that escape through an intermediate
    // symlink before reaching a final symlink.
    @Test
    public void testPathOpenFollowRejectsIntermediateEscapeBeforeFinalSymlink() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            assumeSymbolicLinksSupported(tempRoot);
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Path outsideDirectory = Files.createDirectory(tempRoot.resolve("outside"));
            Path insideTarget = preopenedDirectory.resolve("inside.txt");
            Files.writeString(insideTarget, "inside", StandardCharsets.UTF_8);
            Files.createSymbolicLink(outsideDirectory.resolve("final-link"), insideTarget.toAbsolutePath());
            Files.createSymbolicLink(preopenedDirectory.resolve("escape"), outsideDirectory.toAbsolutePath());

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_open"
                                (func $path_open (param i32 i32 i32 i32 i32 i64 i64 i32 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) "escape/final-link")
                              (export "memory" (memory 0))
                              (func (export "followEscapedFinalLink") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 1)   ;; dirflags: LOOKUP_SYMLINK_FOLLOW
                                  (i32.const 0)   ;; path pointer: "escape/final-link"
                                  (i32.const 17)  ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                )))
                            """));

            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(Source.newBuilder(WasmLanguage.ID, binary, "main").build()).newInstance().getMember("exports");
                assertEquals(Errno.Noent.ordinal(), exports.getMember("followEscapedFinalLink").execute().asInt());
            }
        } finally {
            deleteTree(tempRoot);
        }
    }

    // Verifies that paths which normalize to the preopened directory itself remain accessible.
    @Test
    public void testPathOpsCanAddressThePreopenedDirectoryItself() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Files.createDirectory(preopenedDirectory.resolve("dir"));

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_open"
                                (func $path_open (param i32 i32 i32 i32 i32 i64 i64 i32 i32) (result i32)))
                              (import "wasi_snapshot_preview1" "path_filestat_get"
                                (func $path_filestat_get (param i32 i32 i32 i32 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) ".")
                              (data (i32.const 16) "dir/..")
                              (export "memory" (memory 0))
                              (func (export "openRoot") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 0)   ;; dirflags: none
                                  (i32.const 0)   ;; path pointer: "."
                                  (i32.const 1)   ;; path length
                                  (i32.const 2)   ;; oflags: DIRECTORY
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                ))
                              (func (export "statRootViaDotDot") (result i32) (local $ret i32)
                                (local.set $ret
                                  (call $path_filestat_get
                                    (i32.const 3)   ;; fd
                                    (i32.const 0)   ;; flags: none
                                    (i32.const 16)  ;; path pointer: "dir/.."
                                    (i32.const 6)   ;; path length
                                    (i32.const 32)  ;; result address
                                  ))
                                (if (i32.ne (local.get $ret) (i32.const 0))
                                  (then (return (local.get $ret))))
                                (i32.load8_u (i32.const 48))))
                            """));

            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(Source.newBuilder(WasmLanguage.ID, binary, "main").build()).newInstance().getMember("exports");
                assertEquals(Errno.Success.ordinal(), exports.getMember("openRoot").execute().asInt());
                assertEquals(Filetype.Directory.ordinal(), exports.getMember("statRootViaDotDot").execute().asInt());
            }
        } finally {
            deleteTree(tempRoot);
        }
    }

    // Verifies that path_open rejects final symlinks by default and only follows them when asked,
    // including dangling and looping final links.
    @Test
    public void testPathOpenHandlesFinalSymlinksAccordingToLookupFlags() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            assumeSymbolicLinksSupported(tempRoot);
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Path outsideDirectory = Files.createDirectory(tempRoot.resolve("outside"));
            Path insideTarget = preopenedDirectory.resolve("inside.txt");
            Path outsideTarget = outsideDirectory.resolve("secret.txt");
            Path missingTarget = preopenedDirectory.resolve("missing.txt");
            Files.writeString(insideTarget, "inside", StandardCharsets.UTF_8);
            Files.writeString(outsideTarget, "secret", StandardCharsets.UTF_8);
            Files.createSymbolicLink(preopenedDirectory.resolve("inside-link"), insideTarget.toAbsolutePath());
            Files.createSymbolicLink(preopenedDirectory.resolve("outside-link"), outsideTarget.toAbsolutePath());
            Files.createSymbolicLink(preopenedDirectory.resolve("dangling-link"), missingTarget.toAbsolutePath());
            Files.createSymbolicLink(preopenedDirectory.resolve("loop-link"), Path.of("loop-link"));

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_open"
                                (func $path_open (param i32 i32 i32 i32 i32 i64 i64 i32 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) "inside-link")
                              (data (i32.const 16) "outside-link")
                              (data (i32.const 32) "dangling-link")
                              (data (i32.const 48) "loop-link")
                              (export "memory" (memory 0))
                              (func (export "nofollowInside") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 0)   ;; dirflags: none
                                  (i32.const 0)   ;; path pointer: "inside-link"
                                  (i32.const 11)  ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                ))
                              (func (export "followInside") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 1)   ;; dirflags: LOOKUP_SYMLINK_FOLLOW
                                  (i32.const 0)   ;; path pointer: "inside-link"
                                  (i32.const 11)  ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                ))
                              (func (export "followOutside") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 1)   ;; dirflags: LOOKUP_SYMLINK_FOLLOW
                                  (i32.const 16)  ;; path pointer: "outside-link"
                                  (i32.const 12)  ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                ))
                              (func (export "nofollowDangling") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 0)   ;; dirflags: none
                                  (i32.const 32)  ;; path pointer: "dangling-link"
                                  (i32.const 13)  ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                ))
                              (func (export "followDangling") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 1)   ;; dirflags: LOOKUP_SYMLINK_FOLLOW
                                  (i32.const 32)  ;; path pointer: "dangling-link"
                                  (i32.const 13)  ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                ))
                              (func (export "followLoop") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 1)   ;; dirflags: LOOKUP_SYMLINK_FOLLOW
                                  (i32.const 48)  ;; path pointer: "loop-link"
                                  (i32.const 9)   ;; path length
                                  (i32.const 0)   ;; oflags: none
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                )))
                            """));

            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(Source.newBuilder(WasmLanguage.ID, binary, "main").build()).newInstance().getMember("exports");
                assertEquals(Errno.Loop.ordinal(), exports.getMember("nofollowInside").execute().asInt());
                assertEquals(Errno.Success.ordinal(), exports.getMember("followInside").execute().asInt());
                assertEquals(Errno.Noent.ordinal(), exports.getMember("followOutside").execute().asInt());
                assertEquals(Errno.Loop.ordinal(), exports.getMember("nofollowDangling").execute().asInt());
                assertEquals(Errno.Noent.ordinal(), exports.getMember("followDangling").execute().asInt());
                assertEquals(Errno.Loop.ordinal(), exports.getMember("followLoop").execute().asInt());
            }
        } finally {
            deleteTree(tempRoot);
        }
    }

    // Verifies that path_open with LOOKUP_SYMLINK_FOLLOW preserves O_CREAT and O_EXCL semantics on
    // dangling final symlinks.
    @Test
    public void testPathOpenCreateFollowsDanglingFinalSymlinks() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            assumeSymbolicLinksSupported(tempRoot);
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Path targetsDirectory = Files.createDirectory(preopenedDirectory.resolve("targets"));
            Path createTarget = targetsDirectory.resolve("created.txt");
            Path exclTarget = targetsDirectory.resolve("excl.txt");
            Files.createSymbolicLink(preopenedDirectory.resolve("create-link"), Path.of("targets", "created.txt"));
            Files.createSymbolicLink(preopenedDirectory.resolve("excl-link"), Path.of("targets", "excl.txt"));

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_open"
                                (func $path_open (param i32 i32 i32 i32 i32 i64 i64 i32 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) "create-link")
                              (data (i32.const 16) "excl-link")
                              (export "memory" (memory 0))
                              (func (export "createThroughDanglingLink") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 1)   ;; dirflags: LOOKUP_SYMLINK_FOLLOW
                                  (i32.const 0)   ;; path pointer: "create-link"
                                  (i32.const 11)  ;; path length
                                  (i32.const 1)   ;; oflags: CREAT
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                ))
                              (func (export "createExclusiveThroughDanglingLink") (result i32)
                                (call $path_open
                                  (i32.const 3)   ;; fd
                                  (i32.const 1)   ;; dirflags: LOOKUP_SYMLINK_FOLLOW
                                  (i32.const 16)  ;; path pointer: "excl-link"
                                  (i32.const 9)   ;; path length
                                  (i32.const 5)   ;; oflags: CREAT | EXCL
                                  (i64.const 0)   ;; rights base: none
                                  (i64.const 0)   ;; rights inheriting: none
                                  (i32.const 0)   ;; fdflags: none
                                  (i32.const 64)  ;; fd address
                                )))
                            """));

            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(Source.newBuilder(WasmLanguage.ID, binary, "main").build()).newInstance().getMember("exports");
                assertEquals(Errno.Success.ordinal(), exports.getMember("createThroughDanglingLink").execute().asInt());
                assertEquals(Errno.Exist.ordinal(), exports.getMember("createExclusiveThroughDanglingLink").execute().asInt());
            }

            assertTrue(Files.exists(createTarget));
            assertEquals(false, Files.exists(exclTarget));
        } finally {
            deleteTree(tempRoot);
        }
    }

    // Verifies that path_filestat_get inspects the final symlink itself unless follow is requested,
    // including for dangling final links.
    @Test
    public void testPathFilestatGetDoesNotFollowFinalSymlinkByDefault() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            assumeSymbolicLinksSupported(tempRoot);
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Path outsideDirectory = Files.createDirectory(tempRoot.resolve("outside"));
            Path outsideTarget = outsideDirectory.resolve("secret.txt");
            Path missingTarget = preopenedDirectory.resolve("missing.txt");
            Files.writeString(outsideTarget, "secret", StandardCharsets.UTF_8);
            Files.createSymbolicLink(preopenedDirectory.resolve("final-link"), outsideTarget.toAbsolutePath());
            Files.createSymbolicLink(preopenedDirectory.resolve("dangling-link"), missingTarget.toAbsolutePath());

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_filestat_get"
                                (func $path_filestat_get (param i32 i32 i32 i32 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) "final-link")
                              (data (i32.const 16) "dangling-link")
                              (export "memory" (memory 0))
                              (func (export "nofollowOutside") (result i32) (local $ret i32)
                                (local.set $ret
                                  (call $path_filestat_get
                                    (i32.const 3)   ;; fd
                                    (i32.const 0)   ;; flags: none
                                    (i32.const 0)   ;; path pointer: "final-link"
                                    (i32.const 10)  ;; path length
                                    (i32.const 32)  ;; result address
                                  ))
                                (if (i32.ne (local.get $ret) (i32.const 0))
                                  (then (return (local.get $ret))))
                                (i32.load8_u (i32.const 48)))
                              (func (export "follow") (result i32)
                                (call $path_filestat_get
                                  (i32.const 3)   ;; fd
                                  (i32.const 1)   ;; flags: LOOKUP_SYMLINK_FOLLOW
                                  (i32.const 0)   ;; path pointer: "final-link"
                                  (i32.const 10)  ;; path length
                                  (i32.const 32)  ;; result address
                                )
                                )
                              (func (export "nofollowDangling") (result i32) (local $ret i32)
                                (local.set $ret
                                  (call $path_filestat_get
                                    (i32.const 3)   ;; fd
                                    (i32.const 0)   ;; flags: none
                                    (i32.const 16)  ;; path pointer: "dangling-link"
                                    (i32.const 13)  ;; path length
                                    (i32.const 32)  ;; result address
                                  ))
                                (if (i32.ne (local.get $ret) (i32.const 0))
                                  (then (return (local.get $ret))))
                                (i32.load8_u (i32.const 48)))
                              (func (export "followDangling") (result i32)
                                (call $path_filestat_get
                                  (i32.const 3)   ;; fd
                                  (i32.const 1)   ;; flags: LOOKUP_SYMLINK_FOLLOW
                                  (i32.const 16)  ;; path pointer: "dangling-link"
                                  (i32.const 13)  ;; path length
                                  (i32.const 32)  ;; result address
                                )
                                ))
                            """));

            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(Source.newBuilder(WasmLanguage.ID, binary, "main").build()).newInstance().getMember("exports");
                assertEquals(Filetype.SymbolicLink.ordinal(), exports.getMember("nofollowOutside").execute().asInt());
                assertEquals(Errno.Noent.ordinal(), exports.getMember("follow").execute().asInt());
                assertEquals(Filetype.SymbolicLink.ordinal(), exports.getMember("nofollowDangling").execute().asInt());
                assertEquals(Errno.Noent.ordinal(), exports.getMember("followDangling").execute().asInt());
            }
        } finally {
            deleteTree(tempRoot);
        }
    }

    // Verifies that path_filestat_set_times does not mutate the final symlink target by default.
    @Test
    public void testPathFilestatSetTimesDoesNotMutateSymlinkTargetByDefault() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            assumeSymbolicLinksSupported(tempRoot);
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Path outsideDirectory = Files.createDirectory(tempRoot.resolve("outside"));
            Path outsideTarget = outsideDirectory.resolve("secret.txt");
            Files.writeString(outsideTarget, "secret", StandardCharsets.UTF_8);
            FileTime initialTime = FileTime.from(1_000_000L, TimeUnit.MILLISECONDS);
            Files.setLastModifiedTime(outsideTarget, initialTime);
            FileTime observedInitialTime = Files.getLastModifiedTime(outsideTarget);
            Files.createSymbolicLink(preopenedDirectory.resolve("final-link"), outsideTarget.toAbsolutePath());

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_filestat_set_times"
                                (func $path_filestat_set_times (param i32 i32 i32 i32 i64 i64 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) "final-link")
                              (export "memory" (memory 0))
                              (func (export "nofollow") (result i32)
                                (call $path_filestat_set_times
                                  (i32.const 3)               ;; fd
                                  (i32.const 0)               ;; flags: none
                                  (i32.const 0)               ;; path pointer: "final-link"
                                  (i32.const 10)              ;; path length
                                  (i64.const 0)               ;; atim
                                  (i64.const 9000000000000)   ;; mtim
                                  (i32.const 4)               ;; fstflags: MTIM
                                )))
                            """));

            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(Source.newBuilder(WasmLanguage.ID, binary, "main").build()).newInstance().getMember("exports");
                exports.getMember("nofollow").execute();
            }

            assertEquals(observedInitialTime.to(TimeUnit.MILLISECONDS), Files.getLastModifiedTime(outsideTarget).to(TimeUnit.MILLISECONDS));
        } finally {
            deleteTree(tempRoot);
        }
    }

    // Verifies that path_unlink_file removes the symlink itself instead of its target.
    @Test
    public void testPathUnlinkFileUnlinksFinalSymlinkWithoutTouchingTarget() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            assumeSymbolicLinksSupported(tempRoot);
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Path outsideDirectory = Files.createDirectory(tempRoot.resolve("outside"));
            Path outsideTarget = outsideDirectory.resolve("secret.txt");
            Path symlink = preopenedDirectory.resolve("final-link");
            Files.writeString(outsideTarget, "secret", StandardCharsets.UTF_8);
            Files.createSymbolicLink(symlink, outsideTarget.toAbsolutePath());

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_unlink_file"
                                (func $path_unlink_file (param i32 i32 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) "final-link")
                              (export "memory" (memory 0))
                              (func (export "unlink") (result i32)
                                (call $path_unlink_file
                                  (i32.const 3)   ;; fd
                                  (i32.const 0)   ;; path pointer: "final-link"
                                  (i32.const 10)  ;; path length
                                )))
                            """));

            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(Source.newBuilder(WasmLanguage.ID, binary, "main").build()).newInstance().getMember("exports");
                assertEquals(Errno.Success.ordinal(), exports.getMember("unlink").execute().asInt());
            }

            assertEquals(false, Files.exists(symlink, LinkOption.NOFOLLOW_LINKS));
            assertTrue(Files.exists(outsideTarget));
        } finally {
            deleteTree(tempRoot);
        }
    }

    // Verifies that path_remove_directory rejects a final symlink instead of acting on its target.
    @Test
    public void testPathRemoveDirectoryRejectsFinalSymlinkToDirectory() throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("gr-75058-");
        try {
            assumeSymbolicLinksSupported(tempRoot);
            Path preopenedDirectory = Files.createDirectory(tempRoot.resolve("preopen"));
            Path outsideDirectory = Files.createDirectory(tempRoot.resolve("outside"));
            Path outsideTarget = Files.createDirectory(outsideDirectory.resolve("target-dir"));
            Path symlink = preopenedDirectory.resolve("final-link");
            Files.createSymbolicLink(symlink, outsideTarget.toAbsolutePath());

            ByteSequence binary = ByteSequence.create(compileWat("main", """
                            (module
                              (import "wasi_snapshot_preview1" "path_remove_directory"
                                (func $path_remove_directory (param i32 i32 i32) (result i32)))
                              (memory 1)
                              (data (i32.const 0) "final-link")
                              (export "memory" (memory 0))
                              (func (export "remove") (result i32)
                                (call $path_remove_directory
                                  (i32.const 3)   ;; fd
                                  (i32.const 0)   ;; path pointer: "final-link"
                                  (i32.const 10)  ;; path length
                                )))
                            """));

            try (Context context = contextForPreopenedDirectory(preopenedDirectory)) {
                Value exports = context.eval(Source.newBuilder(WasmLanguage.ID, binary, "main").build()).newInstance().getMember("exports");
                assertEquals(Errno.Notdir.ordinal(), exports.getMember("remove").execute().asInt());
            }

            assertTrue(Files.exists(symlink, LinkOption.NOFOLLOW_LINKS));
            assertTrue(Files.exists(outsideTarget));
        } finally {
            deleteTree(tempRoot);
        }
    }

    private static Context contextForPreopenedDirectory(Path preopenedDirectory) {
        Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.allowExperimentalOptions(true);
        contextBuilder.allowIO(IOAccess.ALL);
        contextBuilder.option("wasm.Builtins", "wasi_snapshot_preview1");
        contextBuilder.option("wasm.WasiMapDirs", "test::" + preopenedDirectory.toAbsolutePath());
        return contextBuilder.build();
    }

    private static void assumeSymbolicLinksSupported(Path tempRoot) throws IOException {
        Path target = Files.createFile(tempRoot.resolve("symlink-target"));
        Path link = tempRoot.resolve("symlink-link");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assume.assumeTrue("symbolic links are not supported in this environment", false);
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
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
