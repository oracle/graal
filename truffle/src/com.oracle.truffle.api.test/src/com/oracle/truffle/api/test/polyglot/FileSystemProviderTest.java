/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.graalvm.polyglot.io.FileSystem;

public class FileSystemProviderTest {

    private Path workDir;
    private Path invalidWorkDir;
    private Path existingAbsolute;
    private Path existingRelative;
    private FileSystem fs;

    @Before
    public void setUp() throws IOException {
        invalidWorkDir = Files.createTempDirectory(FileSystemProviderTest.class.getSimpleName());
        workDir = Files.createTempDirectory(FileSystemProviderTest.class.getSimpleName());
        existingAbsolute = Files.write(workDir.resolve("existing.txt"), getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        existingRelative = workDir.relativize(existingAbsolute);
        // Use FileSystem.setCurrentWorkingDirectory to verify that all FileSystem operations are
        // correctly using current working directory
        fs = FileSystem.newDefaultFileSystem();
        fs.setCurrentWorkingDirectory(workDir);
    }

    @After
    public void tearDown() throws IOException {
        try {
            delete(workDir);
        } finally {
            delete(invalidWorkDir);
        }
    }

    @Test
    public void testCheckAccess() throws IOException {
        fs.checkAccess(existingAbsolute, EnumSet.noneOf(AccessMode.class));
        fs.checkAccess(existingRelative, EnumSet.noneOf(AccessMode.class));
    }

    @Test
    public void testCopy() throws IOException {
        Path targetAbsolute = workDir.resolve("copy1.txt");
        fs.copy(existingAbsolute, targetAbsolute);
        Assert.assertEquals(getClass().getSimpleName(), new String(Files.readAllBytes(targetAbsolute), StandardCharsets.UTF_8));
        targetAbsolute = workDir.resolve("copy2.txt");
        fs.copy(existingRelative, targetAbsolute);
        Assert.assertEquals(getClass().getSimpleName(), new String(Files.readAllBytes(targetAbsolute), StandardCharsets.UTF_8));
        Path targetRelative = workDir.resolve("copy3.txt").getFileName();
        fs.copy(existingAbsolute, targetRelative);
        Assert.assertEquals(getClass().getSimpleName(), new String(Files.readAllBytes(workDir.resolve("copy3.txt")), StandardCharsets.UTF_8));
    }

    @Test
    public void testCreateDirectory() throws IOException {
        Path targetAbsolute = workDir.resolve("createDir1.txt");
        fs.createDirectory(targetAbsolute);
        Assert.assertTrue(Files.exists(targetAbsolute));
        Path targetRelative = workDir.resolve("createDir2.txt").getFileName();
        fs.createDirectory(targetRelative);
        Assert.assertTrue(Files.exists(workDir.resolve("createDir2.txt")));
    }

    @Test
    public void testDelete() throws IOException {
        Path targetAbsolute = workDir.resolve("delete.txt");
        Files.createFile(targetAbsolute);
        fs.delete(targetAbsolute);
        Assert.assertFalse(Files.exists(targetAbsolute));
        Files.createFile(targetAbsolute);
        Path targetRelative = targetAbsolute.getFileName();
        fs.delete(targetRelative);
        Assert.assertFalse(Files.exists(targetAbsolute));
    }

    @Test
    public void testMove() throws IOException {
        final Path moveSourceAbsolute = workDir.resolve("move_src.txt");
        Files.copy(existingAbsolute, moveSourceAbsolute);
        Path moveTargetAbsolute = workDir.resolve("move_tgt.txt");
        fs.move(moveSourceAbsolute, moveTargetAbsolute);
        Assert.assertFalse(Files.exists(moveSourceAbsolute));
        Assert.assertTrue(Files.exists(moveTargetAbsolute));

        Files.copy(existingAbsolute, moveSourceAbsolute);
        moveTargetAbsolute = workDir.resolve("move_tgt2.txt");
        Path moveTargetRelative = moveTargetAbsolute.getFileName();
        fs.move(moveSourceAbsolute, moveTargetRelative);
        Assert.assertFalse(Files.exists(moveSourceAbsolute));
        Assert.assertTrue(Files.exists(moveTargetAbsolute));

        Files.copy(existingAbsolute, moveSourceAbsolute);
        moveTargetAbsolute = workDir.resolve("move_tgt3.txt");
        fs.move(moveSourceAbsolute.getFileName(), moveTargetAbsolute);
        Assert.assertFalse(Files.exists(moveSourceAbsolute));
        Assert.assertTrue(Files.exists(moveTargetAbsolute));
    }

    @Test
    public void testNewByteChannel() throws IOException {
        try (SeekableByteChannel ch = fs.newByteChannel(existingAbsolute, EnumSet.of(StandardOpenOption.READ))) {
            final ByteBuffer buffer = ByteBuffer.allocate(1024);
            ch.read(buffer);
            buffer.flip();
            Assert.assertEquals(getClass().getSimpleName(), StandardCharsets.UTF_8.decode(buffer).toString());
        }
        try (SeekableByteChannel ch = fs.newByteChannel(existingRelative, EnumSet.of(StandardOpenOption.READ))) {
            final ByteBuffer buffer = ByteBuffer.allocate(1024);
            ch.read(buffer);
            buffer.flip();
            Assert.assertEquals(getClass().getSimpleName(), StandardCharsets.UTF_8.decode(buffer).toString());
        }
    }

    @Test
    public void testNewDirectoryStream() throws IOException {
        final Path dirAbsolute = workDir.resolve("newdir");
        final Path dirRelative = dirAbsolute.getFileName();
        Files.createDirectory(dirAbsolute);
        Files.createFile(dirAbsolute.resolve("test"));
        try (DirectoryStream<Path> dirents = fs.newDirectoryStream(dirAbsolute, (p) -> true)) {
            final Optional<?> res = StreamSupport.stream(dirents.spliterator(), false).map((p) -> p.getFileName().toString()).filter((n) -> "test".equals(n)).findAny();
            Assert.assertTrue(res.isPresent());
        }
        try (DirectoryStream<Path> dirents = fs.newDirectoryStream(dirRelative, (p) -> true)) {
            final Optional<?> res = StreamSupport.stream(dirents.spliterator(), false).map((p) -> p.getFileName().toString()).filter((n) -> "test".equals(n)).findAny();
            Assert.assertTrue(res.isPresent());
        }
    }

    @Test
    public void testReadAttributes() throws IOException {
        Assert.assertTrue((Boolean) fs.readAttributes(existingAbsolute, "basic:isRegularFile").get("isRegularFile"));
        Assert.assertTrue((Boolean) fs.readAttributes(existingRelative, "basic:isRegularFile").get("isRegularFile"));
    }

    @Test
    public void testSetAttribute() throws IOException {
        final FileTime ft = FileTime.fromMillis(System.currentTimeMillis());
        fs.setAttribute(existingAbsolute, "basic:lastModifiedTime", ft);
        fs.setAttribute(existingRelative, "basic:lastModifiedTime", ft);
    }

    @Test
    public void testToAbsolutePath() {
        Assert.assertEquals(existingAbsolute, fs.toAbsolutePath(existingAbsolute));
        Assert.assertEquals(existingAbsolute, fs.toAbsolutePath(existingRelative));
    }

    @Test
    public void testToRealPath() throws IOException {
        Assert.assertEquals(existingAbsolute.toRealPath(), fs.toRealPath(existingAbsolute));
        Assert.assertEquals(existingAbsolute.toRealPath(), fs.toRealPath(existingRelative));
    }

    @Test
    public void testSetCurrentWorkingDirectory() throws IOException {
        fs.checkAccess(existingRelative, EnumSet.noneOf(AccessMode.class));
        try {
            fs.setCurrentWorkingDirectory(invalidWorkDir);
            try {
                fs.checkAccess(existingRelative, EnumSet.noneOf(AccessMode.class));
                Assert.fail("Should not reach here, NoSuchFileException expected.");
            } catch (NoSuchFileException nsf) {
                // expected
            }
        } finally {
            fs.setCurrentWorkingDirectory(workDir);
        }
        fs.checkAccess(existingRelative, EnumSet.noneOf(AccessMode.class));
    }

    private void delete(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> childen = Files.newDirectoryStream(path)) {
                for (Path child : childen) {
                    delete(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    static FileSystem newFullIOFileSystem(final Path workDir) {
        try {
            final Class<?> clz = Class.forName("com.oracle.truffle.polyglot.FileSystems");
            final Method m = clz.getDeclaredMethod("newDefaultFileSystem", Path.class);
            m.setAccessible(true);
            return (FileSystem) m.invoke(null, workDir);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
