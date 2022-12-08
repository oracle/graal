/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.stream.StreamSupport;

import org.graalvm.polyglot.io.FileSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class NIOFileSystemTest {

    private static final FileAttribute<?> ATTR_UNKNOWN = new FileAttribute<>() {
        @Override
        public String name() {
            return "unknown";
        }

        @Override
        public Object value() {
            return null;
        }
    };

    private static Path workDir;
    private static Path newWorkDir;
    private static Path nonExistent;
    private static Path fileAbsolute;
    private static Path fileRelative;
    private static Path folderAbsolute;
    private static Path folderRelative;
    private static FileSystem fs;

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Before
    public void setUp() throws IOException {
        workDir = Files.createTempDirectory(NIOFileSystemTest.class.getSimpleName());
        fs = newFullIOFileSystem(workDir);
        newWorkDir = Files.createTempDirectory(NIOFileSystemTest.class.getSimpleName());
        nonExistent = workDir.resolve("nonexistent");
        fileAbsolute = Files.write(workDir.resolve("file"), getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        fileRelative = workDir.relativize(fileAbsolute);
        folderAbsolute = Files.createDirectory(workDir.resolve("folder"));
        folderRelative = workDir.relativize(folderAbsolute);
    }

    @After
    public void tearDown() throws IOException {
        try {
            if (workDir != null) {
                delete(workDir);
            }
        } finally {
            delete(newWorkDir);
        }
    }

    @Test
    public void testParsePath() {
        String fileName = "test";
        Path path = fs.parsePath(fileName);
        Assert.assertEquals(fileName, path.getFileName().toString());
        path = fs.parsePath(workDir.toAbsolutePath().toUri());
        Assert.assertEquals(workDir.toAbsolutePath(), path);
        expectException(() -> fs.parsePath((String) null), NullPointerException.class);
        expectException(() -> fs.parsePath((URI) null), NullPointerException.class);
        expectException(() -> fs.parsePath(new URI("unknownscheme:///tmp/")), UnsupportedOperationException.class);
        expectException(() -> fs.parsePath("\0"), IllegalArgumentException.class);
        expectException(() -> fs.parsePath(new URI("file://host:8000/tmp")), IllegalArgumentException.class);
    }

    @Test
    public void testCheckAccess() throws IOException {
        fs.checkAccess(fileAbsolute, EnumSet.noneOf(AccessMode.class));
        fs.checkAccess(fileRelative, EnumSet.noneOf(AccessMode.class));
        fs.checkAccess(folderAbsolute, EnumSet.of(AccessMode.READ));
        fs.checkAccess(folderAbsolute, EnumSet.of(AccessMode.READ, AccessMode.WRITE));
        fs.checkAccess(folderRelative, EnumSet.of(AccessMode.READ));
        fs.checkAccess(fileAbsolute, EnumSet.of(AccessMode.READ));
        fs.checkAccess(fileRelative, EnumSet.of(AccessMode.READ));
        expectException(() -> fs.checkAccess(nonExistent, EnumSet.of(AccessMode.READ)), NoSuchFileException.class);
        expectException(() -> fs.checkAccess(folderAbsolute, null), NullPointerException.class);
        expectException(() -> fs.checkAccess(folderAbsolute, EnumSet.of(AccessMode.READ), (LinkOption[]) null), NullPointerException.class);
        expectException(() -> fs.checkAccess(folderAbsolute, EnumSet.of(AccessMode.READ), new LinkOption[]{null}), NullPointerException.class);
    }

    @Test
    public void testCopy() throws IOException {
        Path target = Files.createDirectory(workDir.resolve("target"));
        expectException(() -> fs.copy(fileAbsolute, target), FileAlreadyExistsException.class);
        expectException(() -> fs.copy(folderAbsolute, target), FileAlreadyExistsException.class);
        expectException(() -> fs.copy(fileAbsolute, target, new CopyOption() {
        }), UnsupportedOperationException.class);
        Path fileInTarget = Files.createFile(target.resolve("file"));
        expectException(() -> fs.copy(fileAbsolute, target, StandardCopyOption.REPLACE_EXISTING), DirectoryNotEmptyException.class);
        Files.delete(fileInTarget);
        expectException(() -> fs.copy(null, target), NullPointerException.class);
        expectException(() -> fs.copy(fileAbsolute, null), NullPointerException.class);
        expectException(() -> fs.copy(fileAbsolute, target, (CopyOption[]) null), NullPointerException.class);
        expectException(() -> fs.copy(fileAbsolute, target, new CopyOption[]{null}), NullPointerException.class);

        Path targetAbsolute = workDir.resolve("testCopy1");
        fs.copy(fileAbsolute, targetAbsolute);
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));
        targetAbsolute = workDir.resolve("testCopy2");
        fs.copy(fileRelative, targetAbsolute);
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));
        Path targetRelative = fs.parsePath("testCopy3");
        targetAbsolute = workDir.resolve(targetRelative);
        fs.copy(fileAbsolute, targetRelative);
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));
        targetRelative = fs.parsePath("testCopy4");
        targetAbsolute = workDir.resolve(targetRelative);
        fs.copy(fileRelative, targetRelative);
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));

        fs.copy(folderRelative, targetAbsolute, StandardCopyOption.REPLACE_EXISTING);
        Assert.assertTrue(Files.isDirectory(targetAbsolute));
        fs.copy(fileRelative, targetAbsolute, StandardCopyOption.REPLACE_EXISTING);
        Assert.assertTrue(Files.isRegularFile(targetAbsolute));
        fs.copy(folderAbsolute, targetAbsolute, StandardCopyOption.REPLACE_EXISTING);
        Assert.assertTrue(Files.isDirectory(targetAbsolute));
        fs.copy(fileAbsolute, targetRelative, StandardCopyOption.REPLACE_EXISTING);
        Assert.assertTrue(Files.isRegularFile(targetAbsolute));
    }

    @Test
    public void testCreateDirectory() throws IOException {
        expectException(() -> fs.createDirectory(fileAbsolute), FileAlreadyExistsException.class);
        expectException(() -> fs.createDirectory(folderAbsolute, ATTR_UNKNOWN), UnsupportedOperationException.class);
        expectException(() -> fs.createDirectory(null), NullPointerException.class);
        Path targetRelative = fs.parsePath("testCreateDirectory1");
        Path targetAbsolute = workDir.resolve(targetRelative);
        expectException(() -> fs.createDirectory(targetAbsolute, (FileAttribute<?>[]) null), NullPointerException.class);
        expectException(() -> fs.createDirectory(targetAbsolute, new FileAttribute<?>[]{null}), NullPointerException.class);
        fs.createDirectory(targetRelative);
        Assert.assertTrue(Files.isDirectory(targetAbsolute));
        Path targetAbsolute2 = workDir.resolve("testCreateDirectory2");
        fs.createDirectory(targetAbsolute2);
        Assert.assertTrue(Files.isDirectory(targetAbsolute2));
    }

    @Test
    public void testCreateLink() throws IOException {
        Path targetRelative = workDir.resolve("testCreateLink");
        Path target = fs.toAbsolutePath(workDir.resolve("testCreateLink2"));
        try {
            fs.createLink(targetRelative, fileRelative);
        } catch (UnsupportedOperationException uoe) {
            // Links not supported by OS.
            return;
        }
        expectException(() -> fs.createLink(targetRelative, null), NullPointerException.class);
        expectException(() -> fs.createLink(null, fileAbsolute), NullPointerException.class);
        fs.createLink(target, fileAbsolute);
    }

    @Test
    public void testCreateSymLink() throws IOException {
        Path targetRelative = workDir.resolve("testCreateSymLink");
        Path target = fs.toAbsolutePath(workDir.resolve("testCreateSymLink2"));
        try {
            fs.createSymbolicLink(targetRelative, fileRelative);
        } catch (UnsupportedOperationException uoe) {
            // Links not supported by OS.
            return;
        }
        expectException(() -> fs.createSymbolicLink(targetRelative, null), NullPointerException.class);
        expectException(() -> fs.createSymbolicLink(null, fileAbsolute), NullPointerException.class);
        expectException(() -> fs.createSymbolicLink(targetRelative, fileAbsolute, (FileAttribute<?>[]) null), NullPointerException.class);
        expectException(() -> fs.createSymbolicLink(targetRelative, fileAbsolute, new FileAttribute<?>[]{null}), NullPointerException.class);
        expectException(() -> fs.createSymbolicLink(targetRelative, fileAbsolute, ATTR_UNKNOWN), UnsupportedOperationException.class);
        fs.createSymbolicLink(target, fileAbsolute);
    }

    @Test
    public void testDelete() throws IOException {
        Path targetRelative = fs.parsePath("testDelete");
        Path targetAbsolute = workDir.resolve("testDelete");
        expectException(() -> fs.delete(targetAbsolute), NoSuchFileException.class);
        expectException(() -> fs.delete(null), NullPointerException.class);
        Files.createDirectory(targetAbsolute);
        fs.delete(targetRelative);
        Assert.assertFalse(Files.exists(targetAbsolute));
        Files.createDirectory(targetAbsolute);
        fs.delete(targetAbsolute);
        Assert.assertFalse(Files.exists(targetAbsolute));
        Files.createFile(targetAbsolute);
        fs.delete(targetRelative);
        Assert.assertFalse(Files.exists(targetAbsolute));
        Files.createFile(targetAbsolute);
        fs.delete(targetAbsolute);
        Assert.assertFalse(Files.exists(targetAbsolute));
    }

    @Test
    public void testGetEncoding() {
        expectException(() -> fs.getEncoding(null), NullPointerException.class);
        Assert.assertNull(fs.getEncoding(fileAbsolute));
        Assert.assertNull(fs.getEncoding(fileRelative));
    }

    @Test
    public void testMimeType() {
        expectException(() -> fs.getMimeType(null), NullPointerException.class);
        Assert.assertNull(fs.getMimeType(fileAbsolute));
        Assert.assertNull(fs.getMimeType(fileRelative));
    }

    @Test
    public void testGetPathSeparator() {
        Assert.assertEquals(File.pathSeparator, fs.getPathSeparator());
    }

    @Test
    public void testGetSeparator() {
        Assert.assertEquals(File.separator, fs.getSeparator());
    }

    @Test
    public void testGetTempDirectory() {
        Path tmp = fs.getTempDirectory();
        Assert.assertNotNull(tmp);
        Assert.assertTrue(Files.isDirectory(tmp));
    }

    @Test
    public void testMove() throws IOException {
        Path sourceFile = Files.createFile(workDir.resolve("testMoveFile1"));
        Path sourceFolder = Files.createDirectory(workDir.resolve("testMoveFolder1"));
        Path target = Files.createDirectory(workDir.resolve("testMoveTarget1"));
        expectException(() -> fs.move(sourceFile, target), FileAlreadyExistsException.class);
        expectException(() -> fs.move(sourceFolder, target), FileAlreadyExistsException.class);
        expectException(() -> fs.move(sourceFile, target, new CopyOption() {
        }), UnsupportedOperationException.class);
        Path fileInTarget = Files.createFile(target.resolve("file"));
        expectException(() -> fs.move(sourceFile, target, StandardCopyOption.REPLACE_EXISTING), DirectoryNotEmptyException.class);
        Files.delete(fileInTarget);
        expectException(() -> fs.move(null, target), NullPointerException.class);
        expectException(() -> fs.move(sourceFile, null), NullPointerException.class);
        expectException(() -> fs.move(sourceFile, target, (CopyOption[]) null), NullPointerException.class);
        expectException(() -> fs.move(sourceFile, target, new CopyOption[]{null}), NullPointerException.class);

        Path sourceFileAbsolute = Files.createFile(workDir.resolve("testMoveFile2"));
        Path sourceFileRelative = workDir.relativize(sourceFileAbsolute);
        Path targetAbsolute = workDir.resolve("testMoveTarget2");
        Path targetRelative = workDir.relativize(targetAbsolute);
        fs.move(sourceFileRelative, targetRelative);
        Assert.assertFalse(Files.exists(sourceFileAbsolute));
        Assert.assertTrue(Files.exists(targetAbsolute));
        sourceFileAbsolute = Files.createFile(workDir.resolve("testMoveFile3"));
        targetAbsolute = workDir.resolve("testMoveTarget3");
        targetRelative = workDir.relativize(targetAbsolute);
        fs.move(sourceFileAbsolute, targetRelative);
        Assert.assertFalse(Files.exists(sourceFileAbsolute));
        Assert.assertTrue(Files.exists(targetAbsolute));
        sourceFileAbsolute = Files.createFile(workDir.resolve("testMoveFile4"));
        targetAbsolute = workDir.resolve("testMoveTarget4");
        fs.move(sourceFileAbsolute, targetAbsolute, StandardCopyOption.REPLACE_EXISTING);
        Assert.assertFalse(Files.exists(sourceFileAbsolute));
        Assert.assertTrue(Files.exists(targetAbsolute));
        Path sourceFolderAbsolute = Files.createDirectory(workDir.resolve("testMoveFolder5"));
        Path sourceFolderRelative = workDir.relativize(sourceFolderAbsolute);
        targetAbsolute = workDir.resolve("testMoveTarget5");
        targetRelative = workDir.relativize(targetAbsolute);
        fs.move(sourceFolderRelative, targetRelative);
        Assert.assertFalse(Files.exists(sourceFolderAbsolute));
        Assert.assertTrue(Files.exists(targetAbsolute));
        sourceFolderAbsolute = Files.createDirectory(workDir.resolve("testMoveFolder6"));
        targetAbsolute = workDir.resolve("testMoveTarget6");
        targetRelative = workDir.relativize(targetAbsolute);
        fs.move(sourceFolderAbsolute, targetRelative);
        Assert.assertFalse(Files.exists(sourceFolderAbsolute));
        Assert.assertTrue(Files.exists(targetAbsolute));
        sourceFolderAbsolute = Files.createDirectory(workDir.resolve("testMoveFolder7"));
        targetAbsolute = workDir.resolve("testMoveTarget7");
        fs.move(sourceFolderAbsolute, targetAbsolute);
        Assert.assertFalse(Files.exists(sourceFolderAbsolute));
        Assert.assertTrue(Files.exists(targetAbsolute));
    }

    @Test
    public void testNewByteChannel() throws IOException {
        expectException(() -> fs.newByteChannel(null, Collections.emptySet()).close(), NullPointerException.class);
        expectException(() -> fs.newByteChannel(fileAbsolute, null).close(), NullPointerException.class);
        expectException(() -> fs.newByteChannel(fileAbsolute, Collections.singleton(null)).close(), NullPointerException.class);
        Path target = workDir.resolve("testNewByteChannel1");
        expectException(() -> fs.newByteChannel(target, EnumSet.of(StandardOpenOption.CREATE), (FileAttribute<?>[]) null).close(), NullPointerException.class);
        expectException(() -> fs.newByteChannel(target, EnumSet.of(StandardOpenOption.CREATE), new FileAttribute<?>[]{null}).close(), NullPointerException.class);
        expectException(() -> fs.newByteChannel(target, EnumSet.of(StandardOpenOption.CREATE), ATTR_UNKNOWN).close(), UnsupportedOperationException.class);
        expectException(() -> fs.newByteChannel(fileAbsolute, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).close(), FileAlreadyExistsException.class);
        expectException(() -> fs.newByteChannel(target, Collections.emptySet()).close(), NoSuchFileException.class);

        Path targetRelative = fs.parsePath("testNewByteChannel2");
        Path targetAbsolute = workDir.resolve(targetRelative);
        try (SeekableByteChannel ch = fs.newByteChannel(targetRelative, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE))) {
            Assert.assertTrue(ch instanceof FileChannel);
            try (PrintWriter writer = new PrintWriter(Channels.newWriter(ch, StandardCharsets.UTF_8))) {
                writer.print(getClass().getSimpleName());
            }
        }
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));
        try (SeekableByteChannel ch = fs.newByteChannel(targetAbsolute, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE))) {
            Assert.assertTrue(ch instanceof FileChannel);
            try (PrintWriter writer = new PrintWriter(Channels.newWriter(ch, StandardCharsets.UTF_8))) {
                writer.print(getClass().getSimpleName());
            }
        }
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));

        try (SeekableByteChannel ch = fs.newByteChannel(fileAbsolute, EnumSet.of(StandardOpenOption.READ))) {
            Assert.assertTrue(ch instanceof FileChannel);
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            ch.read(buffer);
            buffer.flip();
            Assert.assertEquals(getClass().getSimpleName(), StandardCharsets.UTF_8.decode(buffer).toString());

        }
        try (SeekableByteChannel ch = fs.newByteChannel(fileRelative, EnumSet.of(StandardOpenOption.READ))) {
            Assert.assertTrue(ch instanceof FileChannel);
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            ch.read(buffer);
            buffer.flip();
            Assert.assertEquals(getClass().getSimpleName(), StandardCharsets.UTF_8.decode(buffer).toString());
        }
    }

    @Test
    public void testNewDirectoryStream() throws IOException {
        DirectoryStream.Filter<Path> allFilter = entry -> true;
        DirectoryStream.Filter<Path> errFilter = entry -> {
            throw new RuntimeException();
        };
        Path targetAbsolute = Files.createDirectory(workDir.resolve("testNewDirectoryStream"));
        Path targetRelative = workDir.relativize(targetAbsolute);
        Path fileInTarget = Files.createFile(targetAbsolute.resolve("file"));
        expectException(() -> fs.newDirectoryStream(null, allFilter).close(), NullPointerException.class);
        expectException(() -> fs.newDirectoryStream(targetRelative, null).close(), NullPointerException.class);
        expectException(() -> fs.newDirectoryStream(fileInTarget, allFilter).close(), NotDirectoryException.class);
        try (DirectoryStream<Path> dir = fs.newDirectoryStream(targetRelative, allFilter)) {
            Assert.assertTrue(StreamSupport.stream(dir.spliterator(), false).anyMatch((p) -> p.getFileName().toString().equals("file")));
        }
        try (DirectoryStream<Path> dir = fs.newDirectoryStream(targetAbsolute, allFilter)) {
            Assert.assertTrue(StreamSupport.stream(dir.spliterator(), false).anyMatch((p) -> p.getFileName().toString().equals("file")));
        }
        expectException(() -> {
            try (DirectoryStream<Path> dir = fs.newDirectoryStream(targetRelative, errFilter)) {
                StreamSupport.stream(dir.spliterator(), false).toArray();
            }
        }, RuntimeException.class);
    }

    @Test
    public void testReadAttributes() throws IOException {
        expectException(() -> fs.readAttributes(null, "basic:*"), NullPointerException.class);
        expectException(() -> fs.readAttributes(fileAbsolute, null), NullPointerException.class);
        expectException(() -> fs.readAttributes(fileAbsolute, "basic:*", (LinkOption[]) null), NullPointerException.class);
        expectException(() -> fs.readAttributes(fileAbsolute, "basic:*", new LinkOption[]{null}), NullPointerException.class);
        expectException(() -> fs.readAttributes(fileAbsolute, "extended:*"), UnsupportedOperationException.class);
        expectException(() -> fs.readAttributes(fileAbsolute, ""), IllegalArgumentException.class);
        expectException(() -> fs.readAttributes(fileAbsolute, "basic:size+creationTime"), IllegalArgumentException.class);
        expectException(() -> fs.readAttributes(fileAbsolute, "basic:size,creationTime,unknownAttr"), IllegalArgumentException.class);
        Assert.assertTrue((Boolean) fs.readAttributes(fileAbsolute, "basic:isRegularFile").get("isRegularFile"));
        Assert.assertTrue((Boolean) fs.readAttributes(fileRelative, "basic:isRegularFile").get("isRegularFile"));
        Assert.assertEquals(1, fs.readAttributes(fileAbsolute, "basic:size").size());
        Assert.assertEquals(2, fs.readAttributes(fileAbsolute, "basic:size,creationTime").size());
        Assert.assertEquals(1, fs.readAttributes(fileAbsolute, "size").size());
        Assert.assertFalse(fs.readAttributes(fileAbsolute, "basic:*").isEmpty());
        Assert.assertFalse(fs.readAttributes(fileAbsolute, "*").isEmpty());
        Assert.assertFalse(fs.readAttributes(fileRelative, "*").isEmpty());
    }

    @Test
    public void testReadSymLink() throws IOException {
        Path targetRelative = workDir.resolve("testReadSymLink");
        Path target = fs.toAbsolutePath(workDir.resolve("testReadSymLink"));
        try {
            fs.createSymbolicLink(targetRelative, fileAbsolute);
        } catch (UnsupportedOperationException uoe) {
            // Links not supported by OS.
            return;
        }
        expectException(() -> fs.readSymbolicLink(null), NullPointerException.class);
        expectException(() -> fs.readSymbolicLink(fileAbsolute), NotLinkException.class);
        Assert.assertEquals(fileAbsolute, fs.readSymbolicLink(targetRelative));
        Assert.assertEquals(fileAbsolute, fs.readSymbolicLink(target));
    }

    @Test
    public void testSetAttribute() throws IOException {
        expectException(() -> fs.setAttribute(null, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis())), NullPointerException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, null, FileTime.fromMillis(System.currentTimeMillis())), NullPointerException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()), (LinkOption[]) null), NullPointerException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()), new LinkOption[]{null}), NullPointerException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "basic:lastModifiedTime", System.currentTimeMillis()), ClassCastException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "*", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "basic:*", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "basic:size", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "basic:lastModifiedTime,creationTime", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "basic:unknownAttr", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(fileAbsolute, "extended:lastModifiedTime", System.currentTimeMillis()), UnsupportedOperationException.class);
        fs.setAttribute(fileAbsolute, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()));
        fs.setAttribute(fileAbsolute, "lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()));
        fs.setAttribute(fileRelative, "lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()));
    }

    @Test
    public void testToAbsolutePath() {
        expectException(() -> fs.toAbsolutePath(null), NullPointerException.class);
        Assert.assertEquals(fileAbsolute, fs.toAbsolutePath(fileAbsolute));
        Assert.assertEquals(fileAbsolute, fs.toAbsolutePath(fileRelative));
    }

    @Test
    public void testToRealPath() throws IOException {
        expectException(() -> fs.toRealPath(null), NullPointerException.class);
        expectException(() -> fs.toRealPath(fileAbsolute, (LinkOption[]) null), NullPointerException.class);
        expectException(() -> fs.toRealPath(fileAbsolute, new LinkOption[]{null}), NullPointerException.class);
        Assert.assertEquals(fileAbsolute.toRealPath(), fs.toRealPath(fileAbsolute));
        Assert.assertEquals(fileAbsolute.toRealPath(), fs.toRealPath(fileRelative));
    }

    @Test
    public void testSetCurrentWorkingDirectory() throws IOException {
        expectException(() -> fs.setCurrentWorkingDirectory(null), NullPointerException.class);
        expectException(() -> fs.setCurrentWorkingDirectory(fileAbsolute), IllegalArgumentException.class);
        expectException(() -> fs.setCurrentWorkingDirectory(folderRelative), IllegalArgumentException.class);
        fs.checkAccess(fileRelative, EnumSet.noneOf(AccessMode.class));
        try {
            fs.setCurrentWorkingDirectory(newWorkDir);
            try {
                fs.checkAccess(fileRelative, EnumSet.noneOf(AccessMode.class));
                Assert.fail("Should not reach here, NoSuchFileException expected.");
            } catch (NoSuchFileException nsf) {
                // expected
            }
        } finally {
            fs.setCurrentWorkingDirectory(workDir);
        }
        fs.checkAccess(fileRelative, EnumSet.noneOf(AccessMode.class));
    }

    static FileSystem newFullIOFileSystem(final Path currentWorkingDirectory) {
        FileSystem res = FileSystem.newDefaultFileSystem();
        res.setCurrentWorkingDirectory(currentWorkingDirectory);
        return res;
    }

    @FunctionalInterface
    private interface ExceptionOperation {
        void run() throws Exception;
    }

    private static void expectException(ExceptionOperation op, Class<? extends Throwable> expectedException) {
        AbstractPolyglotTest.assertFails(() -> {
            op.run();
            return null;
        }, expectedException);
    }

    private static void delete(Path toDelete) throws IOException {
        if (Files.isDirectory(toDelete)) {
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(toDelete)) {
                for (Path child : dir) {
                    delete(child);
                }
            }
        }
        Files.delete(toDelete);
    }
}
