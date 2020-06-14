/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class NIOFileSystemTest {

    private static final FileAttribute<?> ATTR_UNKNOWN = new FileAttribute<Object>() {
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
    private static Path nonExistent;
    private static Path file;
    private static Path fileRelative;
    private static Path folder;
    private static Path folderRelative;
    private static FileSystem fs;

    @BeforeClass
    public static void setUp() throws IOException {
        Path tmp = Files.createTempDirectory(NIOFileSystemTest.class.getSimpleName());
        fs = FileSystem.newDefaultFileSystem();
        workDir = fs.parsePath(tmp.toString());
        fs.setCurrentWorkingDirectory(workDir);
        nonExistent = workDir.resolve("nonexistent");
        file = Files.createFile(workDir.resolve("file"));
        fileRelative = workDir.relativize(file);
        folder = Files.createDirectory(workDir.resolve("folder"));
        folderRelative = workDir.relativize(folder);
    }

    @AfterClass
    public static void tearDown() throws IOException {
        delete(workDir);
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
    }

    @Test
    public void testCheckAccess() throws IOException {
        fs.checkAccess(folder, EnumSet.of(AccessMode.READ));
        fs.checkAccess(folder, EnumSet.of(AccessMode.READ, AccessMode.WRITE));
        fs.checkAccess(folderRelative, EnumSet.of(AccessMode.READ));
        fs.checkAccess(file, EnumSet.of(AccessMode.READ));
        fs.checkAccess(fileRelative, EnumSet.of(AccessMode.READ));
        expectException(() -> fs.checkAccess(nonExistent, EnumSet.of(AccessMode.READ)), NoSuchFileException.class);
        expectException(() -> fs.checkAccess(folder, null), NullPointerException.class);
        expectException(() -> fs.checkAccess(folder, EnumSet.of(AccessMode.READ), (LinkOption[]) null), NullPointerException.class);
        expectException(() -> fs.checkAccess(folder, EnumSet.of(AccessMode.READ), new LinkOption[]{null}), NullPointerException.class);
    }

    @Test
    public void testCopy() throws IOException {
        Path targetRelative = Files.createDirectory(workDir.resolve("testCopy"));
        Path target = fs.toAbsolutePath(targetRelative);
        expectException(() -> fs.copy(file, targetRelative), FileAlreadyExistsException.class);
        expectException(() -> fs.copy(folder, targetRelative), FileAlreadyExistsException.class);
        expectException(() -> fs.copy(file, targetRelative, new CopyOption() {
        }), UnsupportedOperationException.class);
        Path fileInTarget = Files.createFile(targetRelative.resolve("file"));
        expectException(() -> fs.copy(file, targetRelative, StandardCopyOption.REPLACE_EXISTING), DirectoryNotEmptyException.class);
        Files.delete(fileInTarget);
        expectException(() -> fs.copy(null, targetRelative), NullPointerException.class);
        expectException(() -> fs.copy(file, null), NullPointerException.class);
        expectException(() -> fs.copy(file, targetRelative, (CopyOption[]) null), NullPointerException.class);
        expectException(() -> fs.copy(file, targetRelative, new CopyOption[]{null}), NullPointerException.class);
        fs.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        fs.copy(folder, target, StandardCopyOption.REPLACE_EXISTING);
        fs.copy(fileRelative, target, StandardCopyOption.REPLACE_EXISTING);
        fs.copy(file, targetRelative, StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void testCreateDirectory() throws IOException {
        expectException(() -> fs.createDirectory(file), FileAlreadyExistsException.class);
        expectException(() -> fs.createDirectory(folder, ATTR_UNKNOWN), UnsupportedOperationException.class);
        expectException(() -> fs.createDirectory(null), NullPointerException.class);
        Path targetRelative = workDir.resolve("testCreateDirectory");
        Path target = fs.toAbsolutePath(workDir.resolve("testCreateDirectory2"));
        expectException(() -> fs.createDirectory(targetRelative, (FileAttribute[]) null), NullPointerException.class);
        expectException(() -> fs.createDirectory(targetRelative, new FileAttribute<?>[]{null}), NullPointerException.class);
        fs.createDirectory(target);
        fs.createDirectory(targetRelative);
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
        expectException(() -> fs.createLink(null, file), NullPointerException.class);
        fs.createLink(target, file);
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
        expectException(() -> fs.createSymbolicLink(null, file), NullPointerException.class);
        expectException(() -> fs.createSymbolicLink(targetRelative, file, (FileAttribute<?>[]) null), NullPointerException.class);
        expectException(() -> fs.createSymbolicLink(targetRelative, file, new FileAttribute<?>[]{null}), NullPointerException.class);
        expectException(() -> fs.createSymbolicLink(targetRelative, file, ATTR_UNKNOWN), UnsupportedOperationException.class);
        fs.createSymbolicLink(target, file);
    }

    @Test
    public void testDelete() throws IOException {
        Path targetRelative = workDir.resolve("testDelete");
        Path target = fs.toAbsolutePath(workDir.resolve("testDelete2"));
        expectException(() -> fs.delete(targetRelative), NoSuchFileException.class);
        expectException(() -> fs.delete(null), NullPointerException.class);
        Files.createDirectory(targetRelative);
        fs.delete(targetRelative);
        Files.createFile(targetRelative);
        fs.delete(targetRelative);
        Files.createFile(target);
        fs.delete(target);
    }

    @Test
    public void testGetEncoding() {
        expectException(() -> fs.getEncoding(null), NullPointerException.class);
        Assert.assertNull(fs.getEncoding(file));
        Assert.assertNull(fs.getEncoding(fileRelative));
    }

    @Test
    public void testMimeType() {
        expectException(() -> fs.getMimeType(null), NullPointerException.class);
        Assert.assertNull(fs.getMimeType(file));
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
        Path sourceFileRelative = Files.createFile(workDir.resolve("testMoveFile"));
        Path sourceFolderRelative = Files.createDirectory(workDir.resolve("testMoveFolder"));
        Path targetRelative = Files.createDirectory(workDir.resolve("testMoveTarget"));
        Path target = fs.toAbsolutePath(targetRelative);
        Path sourceFile = fs.toAbsolutePath(Files.createFile(workDir.resolve("testMoveFile2")));
        expectException(() -> fs.move(sourceFileRelative, targetRelative), FileAlreadyExistsException.class);
        expectException(() -> fs.move(sourceFolderRelative, targetRelative), FileAlreadyExistsException.class);
        expectException(() -> fs.move(sourceFileRelative, targetRelative, new CopyOption() {
        }), UnsupportedOperationException.class);
        Path fileInTarget = Files.createFile(targetRelative.resolve("file"));
        expectException(() -> fs.move(sourceFileRelative, targetRelative, StandardCopyOption.REPLACE_EXISTING), DirectoryNotEmptyException.class);
        Files.delete(fileInTarget);
        expectException(() -> fs.move(null, targetRelative), NullPointerException.class);
        expectException(() -> fs.move(sourceFileRelative, null), NullPointerException.class);
        expectException(() -> fs.move(sourceFileRelative, targetRelative, (CopyOption[]) null), NullPointerException.class);
        expectException(() -> fs.move(sourceFileRelative, targetRelative, new CopyOption[]{null}), NullPointerException.class);
        fs.move(sourceFileRelative, targetRelative, StandardCopyOption.REPLACE_EXISTING);
        fs.move(sourceFolderRelative, targetRelative, StandardCopyOption.REPLACE_EXISTING);
        fs.move(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void testNewByteChannel() throws IOException {
        expectException(() -> fs.newByteChannel(null, Collections.emptySet()).close(), NullPointerException.class);
        expectException(() -> fs.newByteChannel(file, null).close(), NullPointerException.class);
        expectException(() -> fs.newByteChannel(file, Collections.singleton(null)).close(), NullPointerException.class);
        Path targetRelative = workDir.resolve("testNewByteChannel");
        expectException(() -> fs.newByteChannel(targetRelative, EnumSet.of(StandardOpenOption.CREATE), (FileAttribute<?>[]) null).close(), NullPointerException.class);
        expectException(() -> fs.newByteChannel(targetRelative, EnumSet.of(StandardOpenOption.CREATE), new FileAttribute<?>[]{null}).close(), NullPointerException.class);
        expectException(() -> fs.newByteChannel(targetRelative, EnumSet.of(StandardOpenOption.CREATE), ATTR_UNKNOWN).close(), UnsupportedOperationException.class);
        expectException(() -> fs.newByteChannel(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).close(), FileAlreadyExistsException.class);
        expectException(() -> fs.newByteChannel(targetRelative, Collections.emptySet()).close(), NoSuchFileException.class);
        try (SeekableByteChannel ch = fs.newByteChannel(targetRelative, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE))) {
            Assert.assertTrue(ch instanceof FileChannel);
        }
        try (SeekableByteChannel ch = fs.newByteChannel(file, EnumSet.of(StandardOpenOption.READ))) {
            Assert.assertTrue(ch instanceof FileChannel);
        }
        try (SeekableByteChannel ch = fs.newByteChannel(fileRelative, EnumSet.of(StandardOpenOption.READ))) {
            Assert.assertTrue(ch instanceof FileChannel);
        }
    }

    @Test
    public void testNewDirectoryStream() throws IOException {
        DirectoryStream.Filter<Path> allFilter = new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return true;
            }
        };
        DirectoryStream.Filter<Path> errFilter = new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                throw new RuntimeException();
            }
        };
        Path targetRelative = Files.createDirectory(workDir.resolve("testNewDirectoryStream"));
        Path target = fs.toAbsolutePath(targetRelative);
        Path fileInTarget = Files.createFile(targetRelative.resolve("file"));
        expectException(() -> fs.newDirectoryStream(null, allFilter).close(), NullPointerException.class);
        expectException(() -> fs.newDirectoryStream(targetRelative, null).close(), NullPointerException.class);
        expectException(() -> fs.newDirectoryStream(fileInTarget, allFilter).close(), NotDirectoryException.class);
        try (DirectoryStream<Path> dir = fs.newDirectoryStream(targetRelative, allFilter)) {
            Assert.assertTrue(StreamSupport.stream(dir.spliterator(), false).filter((p) -> p.getFileName().toString().equals("file")).findAny().isPresent());
        }
        try (DirectoryStream<Path> dir = fs.newDirectoryStream(target, allFilter)) {
            Assert.assertTrue(StreamSupport.stream(dir.spliterator(), false).filter((p) -> p.getFileName().toString().equals("file")).findAny().isPresent());
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
        expectException(() -> fs.readAttributes(file, null), NullPointerException.class);
        expectException(() -> fs.readAttributes(file, "basic:*", (LinkOption[]) null), NullPointerException.class);
        expectException(() -> fs.readAttributes(file, "basic:*", new LinkOption[]{null}), NullPointerException.class);
        expectException(() -> fs.readAttributes(file, "extended:*"), UnsupportedOperationException.class);
        expectException(() -> fs.readAttributes(file, ""), IllegalArgumentException.class);
        expectException(() -> fs.readAttributes(file, "basic:size+creationTime"), IllegalArgumentException.class);
        expectException(() -> fs.readAttributes(file, "basic:size,creationTime,unknownAttr"), IllegalArgumentException.class);
        Assert.assertEquals(1, fs.readAttributes(file, "basic:size").size());
        Assert.assertEquals(2, fs.readAttributes(file, "basic:size,creationTime").size());
        Assert.assertEquals(1, fs.readAttributes(file, "size").size());
        Assert.assertFalse(fs.readAttributes(file, "basic:*").isEmpty());
        Assert.assertFalse(fs.readAttributes(file, "*").isEmpty());
        Assert.assertFalse(fs.readAttributes(fileRelative, "*").isEmpty());
    }

    @Test
    public void testReadSymLink() throws IOException {
        Path targetRelative = workDir.resolve("testReadSymLink");
        Path target = fs.toAbsolutePath(workDir.resolve("testReadSymLink"));
        try {
            fs.createSymbolicLink(targetRelative, file);
        } catch (UnsupportedOperationException uoe) {
            // Links not supported by OS.
            return;
        }
        expectException(() -> fs.readSymbolicLink(null), NullPointerException.class);
        expectException(() -> fs.readSymbolicLink(file), NotLinkException.class);
        Assert.assertEquals(file, fs.readSymbolicLink(targetRelative));
        Assert.assertEquals(file, fs.readSymbolicLink(target));
    }

    @Test
    public void testSetAttribute() throws IOException {
        expectException(() -> fs.setAttribute(null, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis())), NullPointerException.class);
        expectException(() -> fs.setAttribute(file, null, FileTime.fromMillis(System.currentTimeMillis())), NullPointerException.class);
        expectException(() -> fs.setAttribute(file, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()), (LinkOption[]) null), NullPointerException.class);
        expectException(() -> fs.setAttribute(file, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()), new LinkOption[]{null}), NullPointerException.class);
        expectException(() -> fs.setAttribute(file, "basic:lastModifiedTime", System.currentTimeMillis()), ClassCastException.class);
        expectException(() -> fs.setAttribute(file, "", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(file, "*", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(file, "basic:*", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(file, "basic:size", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(file, "basic:lastModifiedTime,creationTime", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(file, "basic:unknownAttr", System.currentTimeMillis()), IllegalArgumentException.class);
        expectException(() -> fs.setAttribute(file, "extended:lastModifiedTime", System.currentTimeMillis()), UnsupportedOperationException.class);
        fs.setAttribute(file, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()));
        fs.setAttribute(file, "lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()));
        fs.setAttribute(fileRelative, "lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()));
    }

    @Test
    public void testToAbsolutePath() {
        expectException(() -> fs.toAbsolutePath(null), NullPointerException.class);
        Assert.assertEquals(file, fs.toAbsolutePath(file));
        Assert.assertEquals(file, fs.toAbsolutePath(fileRelative));
    }

    @Test
    public void testToRealPath() throws IOException {
        expectException(() -> fs.toRealPath(null), NullPointerException.class);
        expectException(() -> fs.toRealPath(file, (LinkOption[]) null), NullPointerException.class);
        expectException(() -> fs.toRealPath(file, new LinkOption[]{null}), NullPointerException.class);
        Assert.assertEquals(file.toRealPath(), fs.toRealPath(file));
        Assert.assertEquals(file.toRealPath(), fs.toRealPath(fileRelative));
    }

    @Test
    public void testSetCurrentWorkingDirectory() {
        expectException(() -> fs.setCurrentWorkingDirectory(null), NullPointerException.class);
        expectException(() -> fs.setCurrentWorkingDirectory(file), IllegalArgumentException.class);
        expectException(() -> fs.setCurrentWorkingDirectory(folderRelative), IllegalArgumentException.class);
    }

    @FunctionalInterface
    private interface ExceptionOperation {
        void run() throws Throwable;
    }

    private static void expectException(ExceptionOperation op, Class<? extends Throwable> expectedException) {
        if (AssertionError.class.isAssignableFrom(expectedException)) {
            throw new IllegalArgumentException("AssertionError is not supported as expectedException.");
        }
        try {
            op.run();
            Assert.fail(expectedException.getSimpleName() + " is expected.");
        } catch (ThreadDeath td) {
            throw td;
        } catch (AssertionError ae) {
            throw ae;
        } catch (Throwable t) {
            if (!expectedException.isInstance(t)) {
                throw new AssertionError("Unexpected exception.", t);
            }
        }
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
