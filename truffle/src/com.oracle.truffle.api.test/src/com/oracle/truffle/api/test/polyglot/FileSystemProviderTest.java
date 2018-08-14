/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
    private Path existingAbsolute;
    private Path existingRelative;
    private FileSystem fs;

    @Before
    public void setUp() throws IOException {
        workDir = Files.createTempDirectory(FileSystemProviderTest.class.getSimpleName());
        existingAbsolute = Files.write(workDir.resolve("existing.txt"), getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        existingRelative = workDir.relativize(existingAbsolute);
        fs = newFullIOFileSystem(workDir);
    }

    @After
    public void tearDown() throws IOException {
        delete(workDir);
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
            final Method m = clz.getDeclaredMethod("newFullIOFileSystem", Path.class);
            m.setAccessible(true);
            return (FileSystem) m.invoke(null, workDir);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static FileSystem newFullIOFileSystem() {
        try {
            final Class<?> clz = Class.forName("com.oracle.truffle.polyglot.FileSystems");
            final Method m = clz.getDeclaredMethod("getDefaultFileSystem");
            m.setAccessible(true);
            return (FileSystem) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
