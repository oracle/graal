/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
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
import java.nio.file.FileSystems;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.jimfs.Jimfs;
import com.oracle.truffle.api.test.OSUtils;
import org.graalvm.collections.Pair;
import org.graalvm.polyglot.io.FileSystem;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NIOFileSystemTest {

    private static final String FILE = "file";
    private static final String FOLDER = "folder";

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

    private static Collection<? extends Config> configurations;

    private final Config config;
    private final Path nonExistent;
    private final Path fileAbsolute;
    private final Path fileRelative;
    private final Path folderAbsolute;
    private final Path folderRelative;

    /**
     * A factory to create and dispose tested filesystem. SVM unit test runs the
     * {@link #createParameters()} at image build time, and the {@code ZipFileSystem} cannot be
     * stored in the image heap. So the {@link Config} can't hold the {@link FileSystem} instance as
     * a final field, but it has to create it lazily at image execution time.
     */
    private interface FileSystemSupplier extends Supplier<Pair<FileSystem, Path>>, Closeable {
    }

    private static final class DefaultFileSystemSupplier implements FileSystemSupplier {

        private volatile Path workDir;

        @Override
        public void close() throws IOException {
            if (workDir != null) {
                delete(workDir);
            }
        }

        @Override
        public Pair<FileSystem, Path> get() {
            try {
                workDir = Files.createTempDirectory(NIOFileSystemTest.class.getSimpleName());
                Files.write(workDir.resolve(FILE), NIOFileSystemTest.class.getSimpleName().getBytes(StandardCharsets.UTF_8));
                Files.createDirectory(workDir.resolve(FOLDER));
                FileSystem hostFs = FileSystem.newDefaultFileSystem();
                return Pair.create(hostFs, workDir);
            } catch (IOException ioe) {
                throw new AssertionError("Failed to prepare test file system", ioe);
            }
        }
    }

    private static final class ZipFileSystemSupplier implements FileSystemSupplier {

        private volatile Path zipFile;
        private volatile java.nio.file.FileSystem zipFs;

        @Override
        public void close() throws IOException {
            if (zipFs != null) {
                zipFs.close();
            }
            if (zipFile != null) {
                delete(zipFile);
            }
        }

        @Override
        public Pair<FileSystem, Path> get() {
            try {
                zipFile = Files.createTempFile("archive", ".zip");
                String workdir = "/workdir";
                try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zipFile, StandardOpenOption.CREATE))) {
                    ZipEntry entry = new ZipEntry(workdir + "/" + FILE);
                    outputStream.putNextEntry(entry);
                    outputStream.write(NIOFileSystemTest.class.getSimpleName().getBytes(StandardCharsets.UTF_8));
                    entry = new ZipEntry(workdir + "/" + FOLDER + "/");
                    outputStream.putNextEntry(entry);
                }
                zipFs = FileSystems.newFileSystem(zipFile, getClass().getClassLoader());
                return Pair.create(FileSystem.newFileSystem(zipFs), zipFs.getPath(workdir));
            } catch (IOException ioe) {
                throw new AssertionError("Failed to prepare test file system", ioe);
            }
        }
    }

    private static final class MemFileSystemSupplier implements FileSystemSupplier {

        private volatile java.nio.file.FileSystem memFs;

        @Override
        public void close() throws IOException {
            if (memFs != null) {
                memFs.close();
            }
        }

        @Override
        public Pair<FileSystem, Path> get() {
            try {
                memFs = Jimfs.newFileSystem();
                Path workdir = OSUtils.isWindows() ? memFs.getPath("C:\\workdir") : memFs.getPath("/workdir");
                Files.createDirectory(workdir);
                Files.write(workdir.resolve(FILE), NIOFileSystemTest.class.getSimpleName().getBytes(StandardCharsets.UTF_8));
                Files.createDirectory(workdir.resolve(FOLDER));
                return Pair.create(FileSystem.newFileSystem(memFs), workdir);
            } catch (IOException ioe) {
                throw new AssertionError("Failed to prepare test file system", ioe);
            }
        }
    }

    private static final class Config {
        final String name;
        final FileSystemSupplier fileSystemSupplier;
        /**
         * Separator used by the filesystem to separate path components. The default filesystem uses
         * {@link File#separator}. The zip filesystem always uses {@code "/"}.
         */
        final String separator;

        /**
         * If {@code true} the filesystem supports parsing a Path from an URI.
         */
        final boolean supportsURI;
        /**
         * If {@code true} the filesystem supports links. If {@code false} the filesystem does not
         * support links and may not even verify {@link LinkOption} parameters.
         */
        final boolean supportsLinks;
        /**
         * If {@code true} the filesystem supports {@link FileSystem#getTempDirectory()}.
         */
        final boolean supportsTempDirectory;
        /**
         * If {@code true} the readAttributes verifies attribute names and throws an
         * {@link IllegalArgumentException} for unknown attributes. If {@code false} the
         * readAttributes ignores unknown attributes without throwing an
         * {@link IllegalArgumentException}.
         */
        final boolean strictReadAttributes;
        /**
         * If {@code true} the copy verifies options and throws
         * {@link UnsupportedOperationException} if the array contains a copy option that is not
         * supported. If {@code false} the copy ignores unknown options without throwing an
         * {@link UnsupportedOperationException}.
         */
        final boolean strictCopyOptions;
        /**
         * If {@code true} the move verifies options and throws
         * {@link UnsupportedOperationException} if the array contains a copy option that is not
         * supported. If {@code false} the move ignores unknown options without throwing an
         * {@link UnsupportedOperationException}.
         */
        final boolean strictMoveOptions;
        /**
         * If {@code true} the newByteChannel verifies attributes and throws
         * {@link UnsupportedOperationException} if the array contains an attribute that is not
         * supported. If {@code false} the newByteChannel ignores unknown attributes without
         * throwing an {@link UnsupportedOperationException}.
         */
        final boolean strictNewByteChannelAttrs;
        /**
         * If {@code true} the createDirectory verifies attributes and throws
         * {@link UnsupportedOperationException} if the array contains an attribute that is not
         * supported. If {@code false} the createDirectory ignores unknown attributes without
         * throwing an {@link UnsupportedOperationException}.
         */
        final boolean strictCreateDirectoryAttrs;

        /**
         * Exception thrown by readAttributes. According to Javadoc it should be
         * {@link UnsupportedOperationException}. But jimfs throws a {@link NullPointerException},
         * see https://github.com/google/jimfs/issues/212.
         */
        final Class<? extends Exception> readUnsupportedViewException;

        private volatile FileSystem fsCache;
        private volatile Path workDirCache;

        Config(String name, FileSystemSupplier fileSystemSupplier, String separator,
                        boolean supportsURI, boolean supportsLinks, boolean supportsTempDirectory, boolean strictReadAttributes,
                        boolean strictCopyOptions, boolean strictMoveOptions, boolean strictNewByteChannelAttrs, boolean strictCreateDirectoryAttrs,
                        Class<? extends Exception> readUnsupportedViewException) {
            this.name = name;
            this.fileSystemSupplier = fileSystemSupplier;
            this.separator = separator;
            this.supportsURI = supportsURI;
            this.supportsLinks = supportsLinks;
            this.supportsTempDirectory = supportsTempDirectory;
            this.strictReadAttributes = strictReadAttributes;
            this.strictCopyOptions = strictCopyOptions;
            this.strictMoveOptions = strictMoveOptions;
            this.strictNewByteChannelAttrs = strictNewByteChannelAttrs;
            this.strictCreateDirectoryAttrs = strictCreateDirectoryAttrs;
            this.readUnsupportedViewException = readUnsupportedViewException != null ? readUnsupportedViewException : UnsupportedOperationException.class;
        }

        FileSystem fs() {
            init();
            return fsCache;
        }

        Path workDir() {
            init();
            return workDirCache;
        }

        private void init() {
            if (fsCache == null) {
                synchronized (this) {
                    if (fsCache == null) {
                        assert workDirCache == null;
                        Pair<FileSystem, Path> pair = fileSystemSupplier.get();
                        fsCache = pair.getLeft();
                        workDirCache = pair.getRight();
                        fsCache.setCurrentWorkingDirectory(workDirCache);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Config> createParameters() {
        Collection<Config> res = List.of(
                        new Config("Default FS", new DefaultFileSystemSupplier(), File.separator,
                                        true, true, true, true, true, true, true, true, null),
                        new Config("Zip FS", new ZipFileSystemSupplier(), "/",
                                        false, false, false, false, false, false, false, false, null),
                        new Config("Memory FS", new MemFileSystemSupplier(), File.separator,
                                        false, true, false, true, false, false, true, true, NullPointerException.class));
        configurations = res;
        return res;
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        if (configurations != null) {
            for (Config configuration : configurations) {
                configuration.fileSystemSupplier.close();
            }
        }
    }

    public NIOFileSystemTest(Config config) {
        this.config = config;
        this.nonExistent = config.workDir().resolve("nonexistent");
        Assert.assertFalse(Files.exists(nonExistent));
        this.fileAbsolute = config.workDir().resolve(FILE);
        Assert.assertTrue(Files.isRegularFile(fileAbsolute));
        this.fileRelative = config.workDir().relativize(fileAbsolute);
        this.folderAbsolute = config.workDir().resolve(FOLDER);
        Assert.assertTrue(Files.isDirectory(folderAbsolute));
        this.folderRelative = config.workDir().relativize(folderAbsolute);
    }

    @Test
    public void testParsePath() {
        String fileName = "test";
        Path path = config.fs().parsePath(fileName);
        Assert.assertEquals(fileName, path.getFileName().toString());
        expectException(() -> config.fs().parsePath((String) null), NullPointerException.class);
        expectException(() -> config.fs().parsePath("\0"), IllegalArgumentException.class);
        if (config.supportsURI) {
            path = config.fs().parsePath(config.workDir().toAbsolutePath().toUri());
            Assert.assertEquals(config.workDir().toAbsolutePath(), path);
            expectException(() -> config.fs().parsePath((URI) null), NullPointerException.class);
            expectException(() -> config.fs().parsePath(new URI("unknownscheme:///tmp/")), UnsupportedOperationException.class);
            expectException(() -> config.fs().parsePath(new URI("file://host:8000/tmp")), IllegalArgumentException.class);
        }
    }

    @Test
    public void testCheckAccess() throws IOException {
        config.fs().checkAccess(fileAbsolute, EnumSet.noneOf(AccessMode.class));
        config.fs().checkAccess(fileRelative, EnumSet.noneOf(AccessMode.class));
        config.fs().checkAccess(folderAbsolute, EnumSet.of(AccessMode.READ));
        config.fs().checkAccess(folderAbsolute, EnumSet.of(AccessMode.READ, AccessMode.WRITE));
        config.fs().checkAccess(folderRelative, EnumSet.of(AccessMode.READ));
        config.fs().checkAccess(fileAbsolute, EnumSet.of(AccessMode.READ));
        config.fs().checkAccess(fileRelative, EnumSet.of(AccessMode.READ));
        expectException(() -> config.fs().checkAccess(nonExistent, EnumSet.of(AccessMode.READ)), NoSuchFileException.class);
        expectException(() -> config.fs().checkAccess(folderAbsolute, null), NullPointerException.class);
        expectException(() -> config.fs().checkAccess(folderAbsolute, EnumSet.of(AccessMode.READ), (LinkOption[]) null), NullPointerException.class);
        expectException(() -> config.fs().checkAccess(folderAbsolute, EnumSet.of(AccessMode.READ), new LinkOption[]{null}), NullPointerException.class);
    }

    @Test
    public void testCopy() throws IOException {
        Path target = config.workDir().resolve("target");
        if (config.strictCopyOptions) {
            expectException(() -> config.fs().copy(fileAbsolute, target, new CopyOption() {
            }), UnsupportedOperationException.class);
        }
        Files.createDirectory(target);
        expectException(() -> config.fs().copy(fileAbsolute, target), FileAlreadyExistsException.class);
        expectException(() -> config.fs().copy(folderAbsolute, target), FileAlreadyExistsException.class);
        if (config.strictCopyOptions) {
            Path fileInTarget = Files.createFile(target.resolve("file"));
            expectException(() -> config.fs().copy(fileAbsolute, target, StandardCopyOption.REPLACE_EXISTING), DirectoryNotEmptyException.class);
            Files.delete(fileInTarget);
        }
        expectException(() -> config.fs().copy(null, target), NullPointerException.class);
        expectException(() -> config.fs().copy(fileAbsolute, null), NullPointerException.class);
        expectException(() -> config.fs().copy(fileAbsolute, target, (CopyOption[]) null), NullPointerException.class);
        if (config.strictCopyOptions) {
            expectException(() -> config.fs().copy(fileAbsolute, target, new CopyOption[]{null}), NullPointerException.class);
        }

        Path targetAbsolute = config.workDir().resolve("testCopy1");
        config.fs().copy(fileAbsolute, targetAbsolute);
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));
        targetAbsolute = config.workDir().resolve("testCopy2");
        config.fs().copy(fileRelative, targetAbsolute);
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));
        Path targetRelative = config.fs().parsePath("testCopy3");
        targetAbsolute = config.workDir().resolve(targetRelative);
        config.fs().copy(fileAbsolute, targetRelative);
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));
        targetRelative = config.fs().parsePath("testCopy4");
        targetAbsolute = config.workDir().resolve(targetRelative);
        config.fs().copy(fileRelative, targetRelative);
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));

        config.fs().copy(fileRelative, targetAbsolute, StandardCopyOption.REPLACE_EXISTING);
        Assert.assertTrue(Files.isRegularFile(targetAbsolute));
        config.fs().copy(fileAbsolute, targetRelative, StandardCopyOption.REPLACE_EXISTING);
        Assert.assertTrue(Files.isRegularFile(targetAbsolute));
    }

    @Test
    public void testCreateDirectory() throws IOException {
        expectException(() -> config.fs().createDirectory(fileAbsolute), FileAlreadyExistsException.class);
        Path target = config.fs().parsePath("testCreateDirectory");
        if (config.strictCreateDirectoryAttrs) {
            expectException(() -> config.fs().createDirectory(target, ATTR_UNKNOWN), UnsupportedOperationException.class);
        }
        expectException(() -> config.fs().createDirectory(null), NullPointerException.class);
        Path targetRelative = config.fs().parsePath("testCreateDirectory1");
        Path targetAbsolute = config.workDir().resolve(targetRelative);
        expectException(() -> config.fs().createDirectory(targetAbsolute, (FileAttribute<?>[]) null), NullPointerException.class);
        if (config.strictCreateDirectoryAttrs) {
            expectException(() -> config.fs().createDirectory(targetAbsolute, new FileAttribute<?>[]{null}), NullPointerException.class);
        }
        config.fs().createDirectory(targetRelative);
        Assert.assertTrue(Files.isDirectory(targetAbsolute));
        Path targetAbsolute2 = config.workDir().resolve("testCreateDirectory2");
        config.fs().createDirectory(targetAbsolute2);
        Assert.assertTrue(Files.isDirectory(targetAbsolute2));
    }

    @Test
    public void testCreateLink() throws IOException {
        Assume.assumeTrue(config.supportsLinks);
        try {
            config.fs().createLink(config.workDir().resolve("testCreateLink"), fileRelative);
        } catch (UnsupportedOperationException uoe) {
            // Links not supported by OS.
            return;
        }
        Path targetRelative = config.fs().parsePath("testCreateLink1");
        Path target = config.workDir().resolve("testCreateLink2");
        expectException(() -> config.fs().createLink(targetRelative, null), NullPointerException.class);
        expectException(() -> config.fs().createLink(null, fileAbsolute), NullPointerException.class);
        config.fs().createLink(target, fileAbsolute);
    }

    @Test
    public void testCreateSymLink() throws IOException {
        Assume.assumeTrue(config.supportsLinks);
        try {
            config.fs().createSymbolicLink(config.workDir().resolve("testCreateSymLink"), fileRelative);
        } catch (UnsupportedOperationException uoe) {
            // Links not supported by OS.
            return;
        }
        Path targetRelative = config.fs().parsePath("testCreateSymLink1");
        Path target = config.workDir().resolve("testCreateSymLink2");
        expectException(() -> config.fs().createSymbolicLink(targetRelative, null), NullPointerException.class);
        expectException(() -> config.fs().createSymbolicLink(null, fileAbsolute), NullPointerException.class);
        expectException(() -> config.fs().createSymbolicLink(targetRelative, fileAbsolute, (FileAttribute<?>[]) null), NullPointerException.class);
        expectException(() -> config.fs().createSymbolicLink(targetRelative, fileAbsolute, new FileAttribute<?>[]{null}), NullPointerException.class);
        expectException(() -> config.fs().createSymbolicLink(targetRelative, fileAbsolute, ATTR_UNKNOWN), UnsupportedOperationException.class);
        config.fs().createSymbolicLink(target, fileAbsolute);
    }

    @Test
    public void testDelete() throws IOException {
        Path targetRelative = config.fs().parsePath("testDelete");
        Path targetAbsolute = config.workDir().resolve("testDelete");
        expectException(() -> config.fs().delete(targetAbsolute), NoSuchFileException.class);
        expectException(() -> config.fs().delete(null), NullPointerException.class);
        Files.createDirectory(targetAbsolute);
        config.fs().delete(targetRelative);
        Assert.assertFalse(Files.exists(targetAbsolute));
        Files.createDirectory(targetAbsolute);
        config.fs().delete(targetAbsolute);
        Assert.assertFalse(Files.exists(targetAbsolute));
        Files.createFile(targetAbsolute);
        config.fs().delete(targetRelative);
        Assert.assertFalse(Files.exists(targetAbsolute));
        Files.createFile(targetAbsolute);
        config.fs().delete(targetAbsolute);
        Assert.assertFalse(Files.exists(targetAbsolute));
    }

    @Test
    public void testGetEncoding() {
        expectException(() -> config.fs().getEncoding(null), NullPointerException.class);
        Assert.assertNull(config.fs().getEncoding(fileAbsolute));
        Assert.assertNull(config.fs().getEncoding(fileRelative));
    }

    @Test
    public void testMimeType() {
        expectException(() -> config.fs().getMimeType(null), NullPointerException.class);
        Assert.assertNull(config.fs().getMimeType(fileAbsolute));
        Assert.assertNull(config.fs().getMimeType(fileRelative));
    }

    @Test
    public void testGetPathSeparator() {
        Assert.assertEquals(File.pathSeparator, config.fs().getPathSeparator());
    }

    @Test
    public void testGetSeparator() {
        Assert.assertEquals(config.separator, config.fs().getSeparator());
    }

    @Test
    public void testGetTempDirectory() {
        if (config.supportsTempDirectory) {
            Path tmp = config.fs().getTempDirectory();
            Assert.assertNotNull(tmp);
            Assert.assertTrue(Files.isDirectory(tmp));
        } else {
            expectException(config.fs()::getTempDirectory, UnsupportedOperationException.class);
        }
    }

    @Test
    public void testMove() throws IOException {
        Path sourceFile = Files.createFile(config.workDir().resolve("testMoveFile1"));
        Path sourceFolder = Files.createDirectory(config.workDir().resolve("testMoveFolder1"));
        Path target = config.workDir().resolve("testMoveTarget1");
        if (config.strictMoveOptions) {
            expectException(() -> config.fs().move(sourceFile, target, new CopyOption() {
            }), UnsupportedOperationException.class);
        }
        Files.createDirectory(target);
        expectException(() -> config.fs().move(sourceFile, target), FileAlreadyExistsException.class);
        expectException(() -> config.fs().move(sourceFolder, target), FileAlreadyExistsException.class);
        if (config.strictMoveOptions) {
            Path fileInTarget = Files.createFile(target.resolve("file"));
            expectException(() -> config.fs().move(sourceFile, target, StandardCopyOption.REPLACE_EXISTING), DirectoryNotEmptyException.class);
            Files.delete(fileInTarget);
        }
        expectException(() -> config.fs().move(null, target), NullPointerException.class);
        expectException(() -> config.fs().move(sourceFile, null), NullPointerException.class);
        expectException(() -> config.fs().move(sourceFile, target, (CopyOption[]) null), NullPointerException.class);
        if (config.strictMoveOptions) {
            expectException(() -> config.fs().move(sourceFile, target, new CopyOption[]{null}), NullPointerException.class);
        }

        Path sourceFileAbsolute = Files.createFile(config.workDir().resolve("testMoveFile2"));
        Path sourceFileRelative = config.workDir().relativize(sourceFileAbsolute);
        Path targetAbsolute = config.workDir().resolve("testMoveTarget2");
        Path targetRelative = config.workDir().relativize(targetAbsolute);
        config.fs().move(sourceFileRelative, targetRelative);
        Assert.assertFalse(Files.exists(sourceFileAbsolute));
        Assert.assertTrue(Files.exists(targetAbsolute));
        sourceFileAbsolute = Files.createFile(config.workDir().resolve("testMoveFile3"));
        targetAbsolute = config.workDir().resolve("testMoveTarget3");
        targetRelative = config.workDir().relativize(targetAbsolute);
        config.fs().move(sourceFileAbsolute, targetRelative);
        Assert.assertFalse(Files.exists(sourceFileAbsolute));
        Assert.assertTrue(Files.exists(targetAbsolute));
        sourceFileAbsolute = Files.createFile(config.workDir().resolve("testMoveFile4"));
        targetAbsolute = config.workDir().resolve("testMoveTarget4");
        config.fs().move(sourceFileAbsolute, targetAbsolute, StandardCopyOption.REPLACE_EXISTING);
        Assert.assertFalse(Files.exists(sourceFileAbsolute));
        Assert.assertTrue(Files.exists(targetAbsolute));
    }

    @Test
    public void testNewByteChannel() throws IOException {
        expectException(() -> config.fs().newByteChannel(null, Collections.emptySet()).close(), NullPointerException.class);
        expectException(() -> config.fs().newByteChannel(fileAbsolute, null).close(), NullPointerException.class);
        Path target = config.workDir().resolve("testNewByteChannel1");
        expectException(() -> config.fs().newByteChannel(target, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE), (FileAttribute<?>[]) null).close(), NullPointerException.class);
        expectException(() -> config.fs().newByteChannel(target, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE), new FileAttribute<?>[]{null}).close(), NullPointerException.class);
        if (config.strictNewByteChannelAttrs) {
            expectException(() -> config.fs().newByteChannel(target, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE), ATTR_UNKNOWN).close(), UnsupportedOperationException.class);
        }
        expectException(() -> config.fs().newByteChannel(fileAbsolute, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)).close(), FileAlreadyExistsException.class);
        expectException(() -> config.fs().newByteChannel(target, Collections.emptySet()).close(), NoSuchFileException.class);

        Path targetRelative = config.fs().parsePath("testNewByteChannel2");
        Path targetAbsolute = config.workDir().resolve(targetRelative);
        try (SeekableByteChannel ch = config.fs().newByteChannel(targetRelative, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
            Assert.assertTrue(ch instanceof FileChannel);
            try (PrintWriter writer = new PrintWriter(Channels.newWriter(ch, StandardCharsets.UTF_8))) {
                writer.print(getClass().getSimpleName());
            }
        }
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));
        try (SeekableByteChannel ch = config.fs().newByteChannel(targetAbsolute, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
            Assert.assertTrue(ch instanceof FileChannel);
            try (PrintWriter writer = new PrintWriter(Channels.newWriter(ch, StandardCharsets.UTF_8))) {
                writer.print(getClass().getSimpleName());
            }
        }
        Assert.assertEquals(getClass().getSimpleName(), Files.readString(targetAbsolute));

        try (SeekableByteChannel ch = config.fs().newByteChannel(fileAbsolute, EnumSet.of(StandardOpenOption.READ))) {
            Assert.assertTrue(ch instanceof FileChannel);
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            ch.read(buffer);
            buffer.flip();
            Assert.assertEquals(getClass().getSimpleName(), StandardCharsets.UTF_8.decode(buffer).toString());

        }
        try (SeekableByteChannel ch = config.fs().newByteChannel(fileRelative, EnumSet.of(StandardOpenOption.READ))) {
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
        Path targetAbsolute = Files.createDirectory(config.workDir().resolve("testNewDirectoryStream"));
        Path targetRelative = config.workDir().relativize(targetAbsolute);
        Path fileInTarget = Files.createFile(targetAbsolute.resolve("file"));
        expectException(() -> config.fs().newDirectoryStream(null, allFilter).close(), NullPointerException.class);
        expectException(() -> config.fs().newDirectoryStream(fileInTarget, allFilter).close(), NotDirectoryException.class);
        try (DirectoryStream<Path> dir = config.fs().newDirectoryStream(targetRelative, allFilter)) {
            Assert.assertTrue(StreamSupport.stream(dir.spliterator(), false).anyMatch((p) -> p.getFileName().toString().equals("file")));
        }
        try (DirectoryStream<Path> dir = config.fs().newDirectoryStream(targetAbsolute, allFilter)) {
            Assert.assertTrue(StreamSupport.stream(dir.spliterator(), false).anyMatch((p) -> p.getFileName().toString().equals("file")));
        }
        expectException(() -> {
            try (DirectoryStream<Path> dir = config.fs().newDirectoryStream(targetRelative, errFilter)) {
                StreamSupport.stream(dir.spliterator(), false).toArray();
            }
        }, RuntimeException.class);
    }

    @Test
    public void testReadAttributes() throws IOException {
        expectException(() -> config.fs().readAttributes(null, "basic:*"), NullPointerException.class);
        expectException(() -> config.fs().readAttributes(fileAbsolute, null), NullPointerException.class);
        expectException(() -> config.fs().readAttributes(fileAbsolute, "basic:*", (LinkOption[]) null), NullPointerException.class);
        expectException(() -> config.fs().readAttributes(fileAbsolute, "basic:*", new LinkOption[]{null}), NullPointerException.class);
        expectException(() -> config.fs().readAttributes(fileAbsolute, "extended:*"), config.readUnsupportedViewException);
        if (config.strictReadAttributes) {
            expectException(() -> config.fs().readAttributes(fileAbsolute, ""), IllegalArgumentException.class);
            expectException(() -> config.fs().readAttributes(fileAbsolute, "basic:size+creationTime"), IllegalArgumentException.class);
            expectException(() -> config.fs().readAttributes(fileAbsolute, "basic:size,creationTime,unknownAttr"), IllegalArgumentException.class);
        }
        Assert.assertTrue((Boolean) config.fs().readAttributes(fileAbsolute, "basic:isRegularFile").get("isRegularFile"));
        Assert.assertTrue((Boolean) config.fs().readAttributes(fileRelative, "basic:isRegularFile").get("isRegularFile"));
        Assert.assertEquals(1, config.fs().readAttributes(fileAbsolute, "basic:size").size());
        Assert.assertEquals(2, config.fs().readAttributes(fileAbsolute, "basic:size,creationTime").size());
        Assert.assertEquals(1, config.fs().readAttributes(fileAbsolute, "size").size());
        Assert.assertFalse(config.fs().readAttributes(fileAbsolute, "basic:*").isEmpty());
        Assert.assertFalse(config.fs().readAttributes(fileAbsolute, "*").isEmpty());
        Assert.assertFalse(config.fs().readAttributes(fileRelative, "*").isEmpty());
    }

    @Test
    public void testReadSymLink() throws IOException {
        Assume.assumeTrue(config.supportsLinks);
        Path targetRelative = config.workDir().resolve("testReadSymLink");
        Path target = config.fs().toAbsolutePath(config.workDir().resolve("testReadSymLink"));
        try {
            config.fs().createSymbolicLink(targetRelative, fileAbsolute);
        } catch (UnsupportedOperationException uoe) {
            // Links not supported by OS.
            return;
        }
        expectException(() -> config.fs().readSymbolicLink(null), NullPointerException.class);
        expectException(() -> config.fs().readSymbolicLink(fileAbsolute), NotLinkException.class);
        Assert.assertEquals(fileAbsolute, config.fs().readSymbolicLink(targetRelative));
        Assert.assertEquals(fileAbsolute, config.fs().readSymbolicLink(target));
    }

    @Test
    public void testSetAttribute() throws IOException {
        Assume.assumeFalse("JDK-8308386", FileSystemsTest.isMacOSOlderThanHighSierra());
        expectException(() -> config.fs().setAttribute(null, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis())), NullPointerException.class);
        expectException(() -> config.fs().setAttribute(fileAbsolute, null, FileTime.fromMillis(System.currentTimeMillis())), NullPointerException.class);
        expectException(() -> config.fs().setAttribute(fileAbsolute, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()), (LinkOption[]) null), NullPointerException.class);
        expectException(() -> config.fs().setAttribute(fileAbsolute, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()), new LinkOption[]{null}),
                        NullPointerException.class);
        expectException(() -> config.fs().setAttribute(fileAbsolute, "basic:lastModifiedTime", System.currentTimeMillis()), ClassCastException.class, IllegalArgumentException.class);
        // According to Javadoc the FileSystemProvider#setAttribute should throw
        // IllegalArgumentException if the attribute name is not specified, or is not recognized and
        // UnsupportedOperationException if the attribute view is not available. But the
        // ZipFileSystem throws UnsupportedOperationException in all cases.
        expectException(() -> config.fs().setAttribute(fileAbsolute, "", System.currentTimeMillis()), IllegalArgumentException.class, UnsupportedOperationException.class);
        expectException(() -> config.fs().setAttribute(fileAbsolute, "*", System.currentTimeMillis()), IllegalArgumentException.class, UnsupportedOperationException.class);
        expectException(() -> config.fs().setAttribute(fileAbsolute, "basic:*", System.currentTimeMillis()), IllegalArgumentException.class, UnsupportedOperationException.class);
        expectException(() -> config.fs().setAttribute(fileAbsolute, "basic:lastModifiedTime,creationTime", System.currentTimeMillis()), IllegalArgumentException.class,
                        UnsupportedOperationException.class);
        expectException(() -> config.fs().setAttribute(fileAbsolute, "basic:unknownAttr", System.currentTimeMillis()), IllegalArgumentException.class, UnsupportedOperationException.class);
        expectException(() -> config.fs().setAttribute(fileAbsolute, "extended:lastModifiedTime", System.currentTimeMillis()), UnsupportedOperationException.class);
        config.fs().setAttribute(fileAbsolute, "basic:lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()));
        config.fs().setAttribute(fileAbsolute, "lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()));
        config.fs().setAttribute(fileRelative, "lastModifiedTime", FileTime.fromMillis(System.currentTimeMillis()));
    }

    @Test
    public void testToAbsolutePath() {
        expectException(() -> config.fs().toAbsolutePath(null), NullPointerException.class);
        Assert.assertEquals(fileAbsolute, config.fs().toAbsolutePath(fileAbsolute));
        Assert.assertEquals(fileAbsolute, config.fs().toAbsolutePath(fileRelative));
    }

    @Test
    public void testToRealPath() throws IOException {
        expectException(() -> config.fs().toRealPath(null), NullPointerException.class);
        expectException(() -> config.fs().toRealPath(fileAbsolute, (LinkOption[]) null), NullPointerException.class);
        expectException(() -> config.fs().toRealPath(fileAbsolute, new LinkOption[]{null}), NullPointerException.class);
        Assert.assertEquals(fileAbsolute.toRealPath(), config.fs().toRealPath(fileAbsolute));
        Assert.assertEquals(fileAbsolute.toRealPath(), config.fs().toRealPath(fileRelative));
    }

    @Test
    public void testSetCurrentWorkingDirectory() throws IOException {
        expectException(() -> config.fs().setCurrentWorkingDirectory(null), NullPointerException.class);
        expectException(() -> config.fs().setCurrentWorkingDirectory(fileAbsolute), IllegalArgumentException.class);
        expectException(() -> config.fs().setCurrentWorkingDirectory(folderRelative), IllegalArgumentException.class);
        config.fs().checkAccess(fileRelative, EnumSet.noneOf(AccessMode.class));
        Path newWorkDir = config.workDir().resolve("newWorkDir");
        config.fs().createDirectory(newWorkDir);
        try {
            config.fs().setCurrentWorkingDirectory(newWorkDir);
            try {
                config.fs().checkAccess(fileRelative, EnumSet.noneOf(AccessMode.class));
                Assert.fail("Should not reach here, NoSuchFileException expected.");
            } catch (NoSuchFileException nsf) {
                // expected
            }
        } finally {
            config.fs().setCurrentWorkingDirectory(config.workDir());
        }
        config.fs().checkAccess(fileRelative, EnumSet.noneOf(AccessMode.class));
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

    private static void expectException(ExceptionOperation op, Class<? extends Throwable> expectedException1, Class<? extends Throwable> expectedException2) {
        AbstractPolyglotTest.assertFails(() -> {
            op.run();
            return null;
        }, Throwable.class, (t) -> {
            if (!expectedException1.isInstance(t) && !expectedException2.isInstance(t)) {
                throw new AssertionError("expected instanceof " + expectedException1.getName() + " or " + expectedException1.getName() + " was " + t.toString(), t);
            }
        });
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
