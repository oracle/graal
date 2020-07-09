/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.FileSystem;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.OSUtils;
import java.nio.file.FileSystemException;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.BiFunction;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assume;

@RunWith(Parameterized.class)
public class FileSystemsTest {

    private static final String LANGUAGE_ID = "virtualised-fs-lang";
    private static final String FOLDER_EXISTING = "folder";
    private static final String FOLDER_EXISTING_INNER1 = "folder1";
    private static final String FOLDER_EXISTING_INNER2 = "folder2";
    private static final String FILE_EXISTING = "existing_file.txt";
    private static final String FILE_EXISTING2 = "existing_file2.txt";
    private static final String FILE_EXISTING_CONTENT = "Existing File Content";
    private static final String FILE_EXISTING_WRITE_MMAP = "write_mmap.txt";
    private static final String FILE_EXISTING_DELETE = "delete.txt";
    private static final String FILE_EXISTING_RENAME = "rename.txt";
    private static final String SYMLINK_EXISTING = "lnk_to_folder";
    private static final String FILE_NEW_WRITE_CHANNEL = "write_channel.txt";
    private static final String FILE_NEW_WRITE_STREAM = "write_stream.txt";
    private static final String FILE_NEW_CREATE_DIR = "new_dir";
    private static final String FILE_NEW_CREATE_FILE = "new_file.txt";
    private static final String FILE_NEW_RENAME = "new_rename.txt";
    private static final String FILE_NEW_LINK = "new_link.txt";
    private static final String FILE_NEW_SYMLINK = "new_symlink.txt";
    private static final String FILE_NEW_COPY = "new_copy.txt";
    private static final String FOLDER_NEW_COPY = "folder_copy";
    private static final String FILE_CHANGE_ATTRS = "existing_attrs.txt";
    private static final String FILE_TMP_DIR = "tmpfolder";

    private static Collection<Configuration> cfgs;
    private static Consumer<Env> languageAction;

    private final Configuration cfg;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Configuration> createParameters() throws IOException, ReflectiveOperationException {
        assert cfgs == null;
        final List<Configuration> result = new ArrayList<>();
        final FileSystem fullIO = FileSystem.newDefaultFileSystem();
        // Full IO
        Path accessibleDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        Context ctx = Context.newBuilder(LANGUAGE_ID).allowIO(true).build();
        setCwd(ctx, accessibleDir, null);
        result.add(new Configuration("Full IO", ctx, accessibleDir, fullIO, true, true, true, true));
        // No IO
        ctx = Context.newBuilder(LANGUAGE_ID).allowIO(false).build();
        Path privateDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        result.add(new Configuration("No IO", ctx, privateDir, Paths.get("").toAbsolutePath(), fullIO, true, false, false, false));
        // No IO under language home - public file
        ctx = Context.newBuilder(LANGUAGE_ID).allowIO(false).build();
        privateDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        setCwd(ctx, privateDir, privateDir);
        result.add(new Configuration("No IO under language home - public file", ctx, privateDir, fullIO, true, false, false, false));
        // No IO under language home - internal file
        ctx = Context.newBuilder(LANGUAGE_ID).allowIO(false).build();
        privateDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        setCwd(ctx, privateDir, privateDir);
        result.add(new Configuration("No IO under language home - internal file", ctx, privateDir, privateDir, fullIO, true, true, false, false, true, (env, p) -> env.getInternalTruffleFile(p)));
        // Checked IO
        accessibleDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        Path readOnlyDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        privateDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        AccessPredicate read = new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir));
        AccessPredicate write = new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir));
        FileSystem fileSystem = new RestrictedFileSystem(FileSystemProviderTest.newFullIOFileSystem(accessibleDir), read, write);
        read.setFileSystem(fileSystem);
        write.setFileSystem(fileSystem);
        ctx = Context.newBuilder(LANGUAGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Conditional IO - read/write part", ctx, accessibleDir, fullIO, false, true, true, true));
        read = new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir));
        write = new AccessPredicate(Collections.singleton(accessibleDir));
        fileSystem = new RestrictedFileSystem(
                        FileSystemProviderTest.newFullIOFileSystem(readOnlyDir), read, write);
        read.setFileSystem(fileSystem);
        write.setFileSystem(fileSystem);
        ctx = Context.newBuilder(LANGUAGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Conditional IO - read only part", ctx, readOnlyDir, fullIO, false, true, false, true));
        read = new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir));
        write = new AccessPredicate(Collections.singleton(accessibleDir));
        fileSystem = new RestrictedFileSystem(FileSystemProviderTest.newFullIOFileSystem(privateDir), read, write);
        read.setFileSystem(fileSystem);
        write.setFileSystem(fileSystem);
        ctx = Context.newBuilder(LANGUAGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Conditional IO - private part", ctx, privateDir, fullIO, false, false, false, true));

        // Memory
        fileSystem = new MemoryFileSystem();
        Path memDir = mkdirs(fileSystem.toAbsolutePath(fileSystem.parsePath("work")), fileSystem);
        ((MemoryFileSystem) fileSystem).setCurrentWorkingDirectory(memDir);
        createContent(memDir, fileSystem);
        ctx = Context.newBuilder(LANGUAGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Memory FileSystem", ctx, memDir, fileSystem, false, true, true, true));

        // PreInitializeContextFileSystem in image build time
        fileSystem = createPreInitializeContextFileSystem();
        Path workDir = mkdirs(fileSystem.parsePath(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()).toString()), fileSystem);
        fileSystem.setCurrentWorkingDirectory(workDir);
        createContent(workDir, fileSystem);
        ctx = Context.newBuilder(LANGUAGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Context pre-initialization filesystem build time", ctx, workDir, fileSystem, true, true, true, true));

        // PreInitializeContextFileSystem in image execution time
        fileSystem = createPreInitializeContextFileSystem();
        workDir = mkdirs(fileSystem.parsePath(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()).toString()), fileSystem);
        fileSystem.setCurrentWorkingDirectory(workDir);
        switchToImageExecutionTime(fileSystem, workDir);
        createContent(workDir, fileSystem);
        ctx = Context.newBuilder(LANGUAGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Context pre-initialization filesystem execution time", ctx, workDir, fileSystem, true, true, true, true));

        cfgs = result;
        return result;
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        if (cfgs != null) {
            for (Configuration cfg : cfgs) {
                cfg.close();
            }
            cfgs = null;
        }
    }

    public FileSystemsTest(final Configuration cfg) {
        this.cfg = cfg;
    }

    @Before
    public void setUp() {
        resetLanguageHomes();
    }

    @After
    public void tearDown() {
        languageAction = null;
    }

    @Test
    public void testList() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final Path folderExisting = path.resolve(FOLDER_EXISTING);
            final TruffleFile file = cfg.resolve(env, folderExisting);
            try {
                final String expected = path.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING).toString();
                final Collection<? extends TruffleFile> children = file.list();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                final Optional<String> expectedFile = children.stream().map(TruffleFile::getAbsoluteFile).map(TruffleFile::getPath).filter(expected::equals).findAny();
                Assert.assertTrue(cfg.formatErrorMessage("Expected child"), expectedFile.isPresent());
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testListNonNormalized() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final Path folderExisting = path.resolve(FOLDER_EXISTING);
            TruffleFile file = cfg.resolve(env, folderExisting);
            file = file.resolve("lib/../.");
            try {
                final String expected = path.resolve(FOLDER_EXISTING).resolve("lib/../.").resolve(FILE_EXISTING).toString();
                final Collection<? extends TruffleFile> children = file.list();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                final Optional<String> expectedFile = children.stream().map(TruffleFile::getAbsoluteFile).map(TruffleFile::getPath).filter(expected::equals).findAny();
                Assert.assertTrue(cfg.formatErrorMessage("Expected child"), expectedFile.isPresent());
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testReadUsingChannel() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final String content = new String(file.readAllBytes(), StandardCharsets.UTF_8);
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertEquals(cfg.formatErrorMessage("Expected file content"), FILE_EXISTING_CONTENT, content);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testReadUsingStream() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final StringBuilder content = new StringBuilder();
                try (BufferedReader in = file.newBufferedReader()) {
                    final char[] buffer = new char[512];
                    while (true) {
                        int len = in.read(buffer);
                        if (len < 0) {
                            break;
                        } else {
                            content.append(buffer, 0, len);
                        }
                    }
                }
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertEquals(cfg.formatErrorMessage("Expected file content"), FILE_EXISTING_CONTENT, content.toString());
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testWriteUsingChannel() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final String expectedContent = "0123456789";
                final TruffleFile file = root.resolve(FILE_NEW_WRITE_CHANNEL);
                try (ByteChannel bc = file.newByteChannel(EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW));
                                OutputStream out = Channels.newOutputStream(bc)) {
                    out.write(expectedContent.getBytes(StandardCharsets.UTF_8));
                }
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
                if (canRead) {
                    Assert.assertEquals(cfg.formatErrorMessage("Expected file size"), 10, file.size());
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testWriteUsingStream() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final String expectedContent = "0123456789";
                final TruffleFile file = root.resolve(FILE_NEW_WRITE_STREAM);
                try (BufferedWriter out = file.newBufferedWriter(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    out.write(expectedContent, 0, expectedContent.length());
                }
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
                if (canRead) {
                    Assert.assertEquals(cfg.formatErrorMessage("Expected file size"), 10, file.size());
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testCreateDirectory() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile toCreate = root.resolve(FILE_NEW_CREATE_DIR);
                toCreate.createDirectories();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
                if (canRead) {
                    Assert.assertTrue(cfg.formatErrorMessage("Expected dir exists"), toCreate.exists());
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testCreateFile() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile toCreate = root.resolve(FILE_NEW_CREATE_FILE);
                toCreate.createFile();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
                if (canRead) {
                    Assert.assertTrue(cfg.formatErrorMessage("Expected file exists"), toCreate.exists());
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testDelete() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile toCreate = root.resolve(FILE_EXISTING_DELETE);
                toCreate.delete();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testExists() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile toCreate = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean exists = toCreate.exists();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertTrue(cfg.formatErrorMessage("File should exist"), exists);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testGetAbsoluteFile() {
        final Context ctx = cfg.getContext();
        final boolean allowsUserDir = cfg.allowsAbsolutePath();
        languageAction = (Env env) -> {
            final TruffleFile file = cfg.resolve(env, FOLDER_EXISTING).resolve(FILE_EXISTING);
            try {
                final TruffleFile absolute = file.getAbsoluteFile();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), allowsUserDir);
                Assert.assertEquals(absolute.getPath(), cfg.getUserDir().resolve(FOLDER_EXISTING).resolve(FILE_EXISTING).toString());
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), allowsUserDir);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testGetCanonicalFile() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile canonical = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING).getCanonicalFile();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertNotNull(cfg.formatErrorMessage("Canonical file"), canonical);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testGetLastModified() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final FileTime lastModifiedTime = file.getLastModifiedTime();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertNotNull(cfg.formatErrorMessage("Has last modified"), lastModifiedTime);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testIsDirectory() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean isDir = file.isDirectory();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertFalse(cfg.formatErrorMessage("Not directory"), isDir);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testRegularFile() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean isFile = file.isRegularFile();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertTrue(cfg.formatErrorMessage("Is file"), isFile);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testIsReadable() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean readable = file.isReadable();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertTrue(cfg.formatErrorMessage("Is readable"), readable);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testIsWritable() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean writable = file.isWritable();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertEquals(cfg.formatErrorMessage("Is writable"), canWrite, writable);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testIsExecutable() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean executable = file.isExecutable();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                // On Windows all files have executable mode.
                if (!OSUtils.isWindows()) {
                    Assert.assertFalse(cfg.formatErrorMessage("Is executable"), executable);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testIsSymbolicLink() throws Throwable {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(SYMLINK_EXISTING);
                Assume.assumeTrue("File System does not support optional symbolic links", file.exists(LinkOption.NOFOLLOW_LINKS));
                final boolean symlink = file.isSymbolicLink();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertTrue(cfg.formatErrorMessage("Is symbolic link"), symlink);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        try {
            ctx.eval(LANGUAGE_ID, "");
        } catch (PolyglotException pe) {
            if (pe.isHostException()) {
                throw pe.asHostException();
            }
        }
    }

    @Test
    public void testRename() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FILE_EXISTING_RENAME);
                final TruffleFile target = root.resolve(FILE_NEW_RENAME);
                file.move(target);
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
                if (canRead) {
                    final Collection<? extends TruffleFile> children = root.list();
                    final boolean hasRenameSource = children.stream().filter((TruffleFile truffleFile) -> FILE_EXISTING_RENAME.equals(truffleFile.getName())).findAny().isPresent();
                    final boolean hasRenameTarget = children.stream().filter((TruffleFile truffleFile) -> FILE_NEW_RENAME.equals(truffleFile.getName())).findAny().isPresent();
                    Assert.assertFalse(cfg.formatErrorMessage("Renamed file should not exist"), hasRenameSource);
                    Assert.assertTrue(cfg.formatErrorMessage("Rename target file should exist"), hasRenameTarget);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testSize() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final long size = file.size();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertEquals(cfg.formatErrorMessage("File size"), FILE_EXISTING_CONTENT.getBytes(StandardCharsets.UTF_8).length, size);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testToUri() {
        final Context ctx = cfg.getContext();
        final Path userDir = cfg.getUserDir();
        final boolean allowsUserDir = cfg.allowsAbsolutePath();
        languageAction = (Env env) -> {
            final TruffleFile file = cfg.resolve(env, FOLDER_EXISTING).resolve(FILE_EXISTING);
            try {
                final URI uri = file.toUri();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), allowsUserDir);
                Assert.assertTrue(uri.isAbsolute());
                Assert.assertEquals(cfg.formatErrorMessage("URI"), userDir.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING).toUri(), uri);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), allowsUserDir);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testToRelativeUri() {
        final Context ctx = cfg.getContext();
        final Path userDir = cfg.getUserDir();
        List<? extends Path> rootDirectories = getRootDirectories();
        if (rootDirectories.isEmpty()) {
            throw new IllegalStateException("No root directory.");
        }
        languageAction = (Env env) -> {
            TruffleFile relativeFile = cfg.resolve(env, FILE_EXISTING);
            URI uri = relativeFile.toRelativeUri();
            Assert.assertFalse(uri.isAbsolute());
            URI expectedUri = userDir.toUri().relativize(userDir.resolve(FILE_EXISTING).toUri());
            Assert.assertEquals(cfg.formatErrorMessage("Relative URI"), expectedUri, uri);
            final TruffleFile absoluteFile = cfg.resolve(env, rootDirectories.get(0)).resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
            uri = absoluteFile.toUri();
            Assert.assertTrue(uri.isAbsolute());
            Assert.assertEquals(cfg.formatErrorMessage("Absolute URI"), Paths.get("/").resolve(FOLDER_EXISTING).resolve(FILE_EXISTING).toUri(), uri);
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testNormalize() {
        final Context ctx = cfg.getContext();
        final boolean allowsUserDir = cfg.allowsAbsolutePath();
        languageAction = (Env env) -> {
            TruffleFile fileNormalized = cfg.resolve(env, FOLDER_EXISTING);
            Assert.assertEquals(fileNormalized, fileNormalized.normalize());
            Assert.assertSame(fileNormalized, fileNormalized.normalize());
            TruffleFile fileNonNormalized = cfg.resolve(env, FOLDER_EXISTING + "/lib/../.");
            Assert.assertEquals(fileNormalized.resolve("lib/../.").getPath(), fileNonNormalized.getPath());
            Assert.assertEquals(fileNormalized.getPath(), fileNonNormalized.normalize().getPath());
            Assert.assertEquals(fileNormalized, fileNonNormalized.normalize());
            try {
                Assert.assertEquals(fileNormalized.getAbsoluteFile().resolve("lib/../.").getPath(), fileNonNormalized.getAbsoluteFile().getPath());
                Assert.assertEquals(fileNormalized.getAbsoluteFile().getPath(), fileNonNormalized.normalize().getAbsoluteFile().getPath());
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), allowsUserDir);
            }
            Assert.assertEquals(".", fileNonNormalized.getName());
            Assert.assertEquals("..", fileNonNormalized.getParent().getName());
            Assert.assertEquals("lib", fileNonNormalized.getParent().getParent().getName());
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testRelativize() {
        Context ctx = cfg.getContext();
        languageAction = (Env env) -> {
            TruffleFile parent = cfg.resolve(env, "/test/parent");
            TruffleFile child = cfg.resolve(env, "/test/parent/child");
            TruffleFile relative = parent.relativize(child);
            Assert.assertEquals("child", relative.getPath());
            Assert.assertEquals(child, parent.resolve(relative.getPath()));
            child = cfg.resolve(env, "/test/parent/child/inner");
            relative = parent.relativize(child);
            Assert.assertEquals(String.join(env.getFileNameSeparator(), "child", "inner"), relative.getPath());
            Assert.assertEquals(child, parent.resolve(relative.getPath()));
            TruffleFile sibling = cfg.resolve(env, "/test/sibling");
            relative = parent.relativize(sibling);
            Assert.assertEquals(String.join(env.getFileNameSeparator(), "..", "sibling"), relative.getPath());
            Assert.assertEquals(sibling.normalize(), parent.resolve(relative.getPath()).normalize());
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testResolve() {
        Context ctx = cfg.getContext();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            TruffleFile parent = cfg.resolve(env, FOLDER_EXISTING);
            TruffleFile child = parent.resolve(FILE_EXISTING);
            try {
                Assert.assertTrue(child.exists());
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
            TruffleFile childRelativeToParent = parent.relativize(child);
            Assert.assertEquals(FILE_EXISTING, childRelativeToParent.getPath());
            try {
                Assert.assertFalse(childRelativeToParent.exists());
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testStartsWith() {
        Context ctx = cfg.getContext();
        languageAction = (Env env) -> {
            TruffleFile testAbsolute = cfg.resolve(env, "/test");
            TruffleFile testParentAbsolute = cfg.resolve(env, "/test/parent");
            TruffleFile testSiblingAbolute = cfg.resolve(env, "/test/sibling");
            TruffleFile testParentSiblingAbsolute = cfg.resolve(env, "/test/parent/sibling");
            TruffleFile teAbsolute = cfg.resolve(env, "/te");
            TruffleFile testParentChildAbsolute = cfg.resolve(env, "/test/parent/child");
            TruffleFile testRelative = cfg.resolve(env, "test");
            TruffleFile testParentRelative = cfg.resolve(env, "test/parent");
            TruffleFile testSiblingRelative = cfg.resolve(env, "test/sibling");
            TruffleFile testParentSiblingRelative = cfg.resolve(env, "test/parent/sibling");
            TruffleFile teRelative = cfg.resolve(env, "te");
            TruffleFile testParentChildRelative = cfg.resolve(env, "test/parent/child");
            Assert.assertTrue(testParentChildAbsolute.startsWith(testAbsolute));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testAbsolute.getPath()));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testParentAbsolute));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testParentAbsolute.getPath()));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testParentChildAbsolute));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testParentChildAbsolute.getPath()));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testSiblingAbolute));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testSiblingAbolute.getPath()));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testParentSiblingAbsolute));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testParentSiblingAbsolute.getPath()));
            Assert.assertFalse(testParentChildAbsolute.startsWith(teAbsolute));
            Assert.assertFalse(testParentChildAbsolute.startsWith(teAbsolute.getPath()));
            Assert.assertTrue(testParentChildRelative.startsWith(testRelative));
            Assert.assertTrue(testParentChildRelative.startsWith(testRelative.getPath()));
            Assert.assertTrue(testParentChildRelative.startsWith(testParentRelative));
            Assert.assertTrue(testParentChildRelative.startsWith(testParentRelative.getPath()));
            Assert.assertTrue(testParentChildRelative.startsWith(testParentChildRelative));
            Assert.assertTrue(testParentChildRelative.startsWith(testParentChildRelative.getPath()));
            Assert.assertFalse(testParentChildRelative.startsWith(testSiblingRelative));
            Assert.assertFalse(testParentChildRelative.startsWith(testSiblingRelative.getPath()));
            Assert.assertFalse(testParentChildRelative.startsWith(testParentSiblingRelative));
            Assert.assertFalse(testParentChildRelative.startsWith(testParentSiblingRelative.getPath()));
            Assert.assertFalse(testParentChildRelative.startsWith(teRelative));
            Assert.assertFalse(testParentChildRelative.startsWith(teRelative.getPath()));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testRelative));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testRelative.getPath()));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testParentRelative));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testParentRelative.getPath()));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testParentChildRelative));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testParentChildRelative.getPath()));
            Assert.assertFalse(testParentChildRelative.startsWith(testAbsolute));
            Assert.assertFalse(testParentChildRelative.startsWith(testAbsolute.getPath()));
            Assert.assertFalse(testParentChildRelative.startsWith(testParentAbsolute));
            Assert.assertFalse(testParentChildRelative.startsWith(testParentAbsolute.getPath()));
            Assert.assertFalse(testParentChildRelative.startsWith(testParentChildAbsolute));
            Assert.assertFalse(testParentChildRelative.startsWith(testParentChildAbsolute.getPath()));
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testEndsWith() {
        Context ctx = cfg.getContext();
        languageAction = (Env env) -> {
            TruffleFile testParentInnerAbsolute = cfg.resolve(env, "/test/parent/inner");
            TruffleFile testParentInnerRelative = cfg.resolve(env, "test/parent/inner");
            TruffleFile innerAbsolute = cfg.resolve(env, "/inner");
            TruffleFile innerRelative = cfg.resolve(env, "inner");
            TruffleFile parentInnerAbsolute = cfg.resolve(env, "/parent/inner");
            TruffleFile parentInnerRelative = cfg.resolve(env, "parent/inner");
            TruffleFile nnerRelative = cfg.resolve(env, "nner");
            TruffleFile testParentSiblingAbsolute = cfg.resolve(env, "/test/parent/sibling");
            TruffleFile testParentSiblingRelative = cfg.resolve(env, "test/parent/sibling");
            TruffleFile testParentInnerChildAbsolute = cfg.resolve(env, "/test/parent/inner/child");
            TruffleFile testParentInnerChildRelative = cfg.resolve(env, "test/parent/inner/child");

            Assert.assertTrue(testParentInnerAbsolute.endsWith(testParentInnerAbsolute));
            Assert.assertTrue(testParentInnerAbsolute.endsWith(testParentInnerAbsolute.getPath()));
            Assert.assertTrue(testParentInnerAbsolute.endsWith(testParentInnerRelative));
            Assert.assertTrue(testParentInnerAbsolute.endsWith(testParentInnerRelative.getPath()));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(innerAbsolute));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(innerAbsolute.getPath()));
            Assert.assertTrue(testParentInnerAbsolute.endsWith(innerRelative));
            Assert.assertTrue(testParentInnerAbsolute.endsWith(innerRelative.getPath()));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(parentInnerAbsolute));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(parentInnerAbsolute.getPath()));
            Assert.assertTrue(testParentInnerAbsolute.endsWith(parentInnerRelative));
            Assert.assertTrue(testParentInnerAbsolute.endsWith(parentInnerRelative.getPath()));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(nnerRelative));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(nnerRelative.getPath()));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(testParentSiblingAbsolute));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(testParentSiblingAbsolute.getPath()));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(testParentSiblingRelative));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(testParentSiblingRelative.getPath()));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(testParentInnerChildAbsolute));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(testParentInnerChildAbsolute.getPath()));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(testParentInnerChildRelative));
            Assert.assertFalse(testParentInnerAbsolute.endsWith(testParentInnerChildRelative.getPath()));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentInnerAbsolute));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentInnerAbsolute.getPath()));
            Assert.assertTrue(testParentInnerRelative.endsWith(testParentInnerRelative));
            Assert.assertTrue(testParentInnerRelative.endsWith(testParentInnerRelative.getPath()));
            Assert.assertFalse(testParentInnerRelative.endsWith(innerAbsolute));
            Assert.assertFalse(testParentInnerRelative.endsWith(innerAbsolute.getPath()));
            Assert.assertTrue(testParentInnerRelative.endsWith(innerRelative));
            Assert.assertTrue(testParentInnerRelative.endsWith(innerRelative.getPath()));
            Assert.assertFalse(testParentInnerRelative.endsWith(parentInnerAbsolute));
            Assert.assertFalse(testParentInnerRelative.endsWith(parentInnerAbsolute.getPath()));
            Assert.assertTrue(testParentInnerRelative.endsWith(parentInnerRelative));
            Assert.assertTrue(testParentInnerRelative.endsWith(parentInnerRelative.getPath()));
            Assert.assertFalse(testParentInnerRelative.endsWith(nnerRelative));
            Assert.assertFalse(testParentInnerRelative.endsWith(nnerRelative.getPath()));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentSiblingAbsolute));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentSiblingAbsolute.getPath()));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentSiblingRelative));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentSiblingRelative.getPath()));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentInnerChildAbsolute));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentInnerChildAbsolute.getPath()));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentInnerChildRelative));
            Assert.assertFalse(testParentInnerRelative.endsWith(testParentInnerChildRelative.getPath()));
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testNewDirectoryStream() {
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            Path folderExisting = path.resolve(FOLDER_EXISTING);
            TruffleFile file = cfg.resolve(env, folderExisting);
            Set<String> expected = new HashSet<>();
            Collections.addAll(expected, FILE_EXISTING, FOLDER_EXISTING_INNER1, FOLDER_EXISTING_INNER2);
            try (DirectoryStream<TruffleFile> stream = file.newDirectoryStream()) {
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Set<String> result = new HashSet<>();
                for (TruffleFile child : stream) {
                    result.add(child.getName());
                }
                Assert.assertEquals(expected, result);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testVisit() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            TruffleFile root = cfg.resolve(env, path);
            TruffleFile existingFolder = root.resolve(FOLDER_EXISTING);
            try {
                // @formatter:off
                TestVisitor visitor = TestVisitor.newBuilder(existingFolder).
                        folder(FOLDER_EXISTING_INNER1).
                            file(FILE_EXISTING).
                            file(FILE_EXISTING2).
                        end().
                        folder(FOLDER_EXISTING_INNER2).
                            folder(FOLDER_EXISTING_INNER1).
                            end().
                            folder(FOLDER_EXISTING_INNER2).
                                file(FILE_EXISTING).
                            end().
                            file(FILE_EXISTING).
                            file(FILE_EXISTING2).
                        end().
                        file(FILE_EXISTING).
                        build();
                // @formatter:on
                existingFolder.visit(visitor, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS);
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                visitor.assertConsumed();
                TruffleFile existingFile = existingFolder.resolve(FILE_EXISTING);
                visitor = TestVisitor.newBuilder(existingFile).build();
                existingFile.visit(visitor, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS);
                visitor.assertConsumed();
                // @formatter:off
                visitor = TestVisitor.newBuilder(existingFolder).
                        file(FOLDER_EXISTING_INNER1).
                        file(FOLDER_EXISTING_INNER2).
                        file(FILE_EXISTING).
                        build();
                // @formatter:on
                existingFolder.visit(visitor, 1, FileVisitOption.FOLLOW_LINKS);
                visitor.assertConsumed();
                // @formatter:off
                visitor = TestVisitor.newBuilder(existingFolder).
                        folder(FOLDER_EXISTING_INNER1).
                            file(FILE_EXISTING).
                            file(FILE_EXISTING2).
                        end().
                        folder(FOLDER_EXISTING_INNER2).
                            file(FOLDER_EXISTING_INNER1).
                            file(FOLDER_EXISTING_INNER2).
                            file(FILE_EXISTING).
                            file(FILE_EXISTING2).
                        end().
                        file(FILE_EXISTING).
                        build();
                // @formatter:on
                existingFolder.visit(visitor, 2, FileVisitOption.FOLLOW_LINKS);
                visitor.assertConsumed();
                // @formatter:off
                visitor = TestVisitor.newBuilder(existingFolder).
                        folder(FOLDER_EXISTING_INNER1).
                            file(FILE_EXISTING).
                            file(FILE_EXISTING2).
                        end().
                        folder(FOLDER_EXISTING_INNER2).
                            skipSubTree().
                        end().
                        file(FILE_EXISTING).
                        build();
                // @formatter:on
                existingFolder.visit(visitor, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS);
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                visitor.assertConsumed();
                // TestVisitor cannot be used for SKIP_SIBLINGS due to random order of files on file
                // system
                FileVisitor<TruffleFile> fileVisitor = new FileVisitor<TruffleFile>() {

                    private boolean skipReturned;
                    private Set<TruffleFile> importantFiles;

                    {
                        importantFiles = new HashSet<>();
                        importantFiles.add(existingFolder.resolve(FOLDER_EXISTING_INNER1));
                        importantFiles.add(existingFolder.resolve(FOLDER_EXISTING_INNER2));
                        importantFiles.add(existingFile);
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(TruffleFile dir, BasicFileAttributes attrs) throws IOException {
                        return check(dir);
                    }

                    @Override
                    public FileVisitResult visitFile(TruffleFile file, BasicFileAttributes attrs) throws IOException {
                        return check(file);
                    }

                    @Override
                    public FileVisitResult visitFileFailed(TruffleFile file, IOException exc) throws IOException {
                        return FileVisitResult.TERMINATE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(TruffleFile dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    private FileVisitResult check(TruffleFile file) {
                        if (importantFiles.contains(file)) {
                            if (skipReturned) {
                                throw new AssertionError("Visited skipped sibling: " + file);
                            } else {
                                skipReturned = true;
                                return FileVisitResult.SKIP_SIBLINGS;
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                };
                existingFolder.visit(fileVisitor, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testCreateLink() {
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                TruffleFile target = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                TruffleFile link = root.resolve(FILE_NEW_LINK);
                link.createLink(target);
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            } catch (UnsupportedOperationException uoe) {
                // Links may not be supported on file system
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testCreateSymbolicLink() {
        Assume.assumeFalse("Link creation requires a special privilege on Windows", OSUtils.isWindows());
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            TruffleFile root = cfg.resolve(env, path);
            try {
                TruffleFile target = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                TruffleFile link = root.resolve(FILE_NEW_SYMLINK);
                link.createSymbolicLink(target);
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
                Assert.assertTrue(link.isSymbolicLink());
                Assert.assertEquals(target.getCanonicalFile(), link.getCanonicalFile());
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            } catch (UnsupportedOperationException uoe) {
                // Symbolik links may not be supported on file system
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testGetOwner() {
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                TruffleFile file = root.resolve(FOLDER_EXISTING);
                UserPrincipal owner = file.getOwner();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertNotNull(owner);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            } catch (UnsupportedOperationException uoe) {
                // Onwer may not be supported on file system
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testGetGroup() {
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                TruffleFile file = root.resolve(FOLDER_EXISTING);
                GroupPrincipal group = file.getGroup();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertNotNull(group);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            } catch (UnsupportedOperationException uoe) {
                // Group may not be supported on file system
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testCopy() {
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            TruffleFile root = cfg.resolve(env, path);
            try {
                TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                TruffleFile target = root.resolve(FILE_NEW_COPY);
                file.copy(target);
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
                if (canRead) {
                    Collection<? extends TruffleFile> children = root.list();
                    boolean hasTarget = children.stream().filter((TruffleFile truffleFile) -> FILE_NEW_COPY.equals(truffleFile.getName())).findAny().isPresent();
                    Assert.assertTrue(cfg.formatErrorMessage("Copied target file should exist"), hasTarget);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
            try {
                TruffleFile folder = root.resolve(FOLDER_EXISTING);
                TruffleFile target = root.resolve(FOLDER_NEW_COPY);
                folder.copy(target);
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
                if (canRead) {
                    Collection<? extends TruffleFile> children = root.list();
                    boolean hasTarget = children.stream().filter((TruffleFile truffleFile) -> FOLDER_NEW_COPY.equals(truffleFile.getName())).findAny().isPresent();
                    boolean hasChildren = !target.list().isEmpty();
                    Assert.assertTrue(cfg.formatErrorMessage("Copied target file should exist"), hasTarget);
                    Assert.assertTrue(cfg.formatErrorMessage("Copied target should not have children"), !hasChildren);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testExceptions() {
        final Context ctx = cfg.getContext();
        languageAction = (Env env) -> {
            TruffleFile existing = cfg.resolve(env, FOLDER_EXISTING);
            try {
                existing.resolve(null);
                Assert.fail("Should not reach here.");
            } catch (Exception e) {
                if (cfg.isInternal()) {
                    Assert.assertTrue(e instanceof NullPointerException);
                } else {
                    Assert.assertTrue(TestAPIAccessor.engineAccess().isHostException(e));
                    Assert.assertTrue(TestAPIAccessor.engineAccess().asHostException(e) instanceof NullPointerException);
                }
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testSetCurrentWorkingDirectory() {
        final Context ctx = cfg.getContext();
        final boolean allowsUserDir = cfg.allowsUserDir();
        final boolean canRead = cfg.canRead();
        final Path path = cfg.getPath();
        languageAction = (Env env) -> {
            try {
                TruffleFile oldCwd = env.getCurrentWorkingDirectory();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), allowsUserDir);
                Assert.assertNotNull(oldCwd);
                Assert.assertTrue(oldCwd.isAbsolute());
                TruffleFile relative = cfg.resolve(env, FILE_EXISTING);
                Assert.assertNotNull(relative);
                Assert.assertFalse(relative.isAbsolute());
                Assert.assertEquals(oldCwd.resolve(FILE_EXISTING), relative.getAbsoluteFile());
                try {
                    Assert.assertFalse(relative.exists());
                    Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                } catch (SecurityException se) {
                    Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
                }
                TruffleFile newCwd = cfg.resolve(env, path).resolve(FOLDER_EXISTING).getAbsoluteFile();
                try {
                    env.setCurrentWorkingDirectory(newCwd);
                    try {
                        Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                        Assert.assertEquals(newCwd, env.getCurrentWorkingDirectory());
                        Assert.assertEquals(newCwd.resolve(FILE_EXISTING), relative.getAbsoluteFile());
                        try {
                            Assert.assertTrue(relative.exists());
                            Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                        } catch (SecurityException se) {
                            Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
                        }
                    } finally {
                        env.setCurrentWorkingDirectory(oldCwd);
                    }
                } catch (SecurityException se) {
                    Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), allowsUserDir);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testGetFileNameSeparator() {
        final Context ctx = cfg.getContext();
        languageAction = (Env env) -> {
            Assert.assertEquals(cfg.fileSystem.getSeparator(), env.getFileNameSeparator());
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testGetPathSeparator() {
        final Context ctx = cfg.getContext();
        languageAction = (Env env) -> {
            Assert.assertEquals(cfg.fileSystem.getSeparator(), env.getFileNameSeparator());
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testGetAttribute() {
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            TruffleFile root = cfg.resolve(env, path);
            try {
                TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                Assert.assertEquals(file.getLastModifiedTime(), file.getAttribute(TruffleFile.LAST_MODIFIED_TIME));
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertEquals(file.getLastAccessTime(), file.getAttribute(TruffleFile.LAST_ACCESS_TIME));
                Assert.assertEquals(file.getCreationTime(), file.getAttribute(TruffleFile.CREATION_TIME));
                Assert.assertEquals(file.isRegularFile(), file.getAttribute(TruffleFile.IS_REGULAR_FILE));
                Assert.assertEquals(file.isDirectory(), file.getAttribute(TruffleFile.IS_DIRECTORY));
                Assert.assertEquals(file.isSymbolicLink(), file.getAttribute(TruffleFile.IS_SYMBOLIC_LINK));
                Assert.assertEquals(!(file.isRegularFile() | file.isDirectory() | file.isSymbolicLink()), file.getAttribute(TruffleFile.IS_OTHER));
                Assert.assertEquals(file.size(), file.getAttribute(TruffleFile.SIZE).longValue());
                Assert.assertEquals(file.getOwner(), file.getAttribute(TruffleFile.UNIX_OWNER));
                Assert.assertEquals(file.getGroup(), file.getAttribute(TruffleFile.UNIX_GROUP));
                Assert.assertEquals(file.getPosixPermissions(), file.getAttribute(TruffleFile.UNIX_PERMISSIONS));
                Assert.assertTrue(file.getAttribute(TruffleFile.UNIX_NLINK) >= 1);
                Assert.assertEquals(file.getOwner().hashCode(), file.getAttribute(TruffleFile.UNIX_UID).intValue());
                Assert.assertEquals(file.getGroup().hashCode(), file.getAttribute(TruffleFile.UNIX_GID).intValue());
                Assert.assertTrue(verifyPermissions(file.getPosixPermissions(), file.getAttribute(TruffleFile.UNIX_MODE)));
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            } catch (UnsupportedOperationException e) {
                // Verify that file system does not support unix attributes
                try {
                    cfg.fileSystem.readAttributes(path, "unix:*");
                    throw e;
                } catch (UnsupportedOperationException unsupported) {
                    // Expected
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testSetAttribute() {
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            TruffleFile root = cfg.resolve(env, path);
            try {
                TruffleFile file = root.resolve(FILE_CHANGE_ATTRS);
                FileTime time = FileTime.from(Instant.now().minusSeconds(1_000).truncatedTo(ChronoUnit.MINUTES));
                file.setAttribute(TruffleFile.LAST_MODIFIED_TIME, time);
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canWrite);
                Assert.assertEquals(time, file.getAttribute(TruffleFile.LAST_MODIFIED_TIME));
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                file.setAttribute(TruffleFile.LAST_ACCESS_TIME, time);
                Assert.assertEquals(time, file.getAttribute(TruffleFile.LAST_ACCESS_TIME));
                file.setAttribute(TruffleFile.CREATION_TIME, time);
                Assert.assertEquals(time, file.getAttribute(TruffleFile.CREATION_TIME));
                file.setAttribute(TruffleFile.UNIX_PERMISSIONS, EnumSet.of(PosixFilePermission.OWNER_READ));
                Assert.assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ), file.getAttribute(TruffleFile.UNIX_PERMISSIONS));
                file.setAttribute(TruffleFile.UNIX_PERMISSIONS, EnumSet.of(PosixFilePermission.OWNER_READ));
                Assert.assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ), file.getAttribute(TruffleFile.UNIX_PERMISSIONS));
                file.setAttribute(TruffleFile.UNIX_MODE, (file.getAttribute(TruffleFile.UNIX_MODE) & ~0700) | 0200);
                Assert.assertEquals(0200, file.getAttribute(TruffleFile.UNIX_MODE) & 0777);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            } catch (UnsupportedOperationException e) {
                // Verify that file system does not support unix attributes
                try {
                    cfg.fileSystem.readAttributes(path, "unix:*");
                    throw e;
                } catch (UnsupportedOperationException unsupported) {
                    // Expected
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testGetAttributes() {
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            TruffleFile root = cfg.resolve(env, path);
            TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
            try {
                TruffleFile.Attributes attrs = file.getAttributes(Arrays.asList(
                                TruffleFile.LAST_MODIFIED_TIME,
                                TruffleFile.LAST_ACCESS_TIME,
                                TruffleFile.CREATION_TIME,
                                TruffleFile.IS_REGULAR_FILE,
                                TruffleFile.IS_DIRECTORY,
                                TruffleFile.IS_SYMBOLIC_LINK,
                                TruffleFile.IS_OTHER,
                                TruffleFile.SIZE));
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertNotNull(attrs);
                Assert.assertEquals(file.getLastModifiedTime(), attrs.get(TruffleFile.LAST_MODIFIED_TIME));
                Assert.assertEquals(file.getLastAccessTime(), attrs.get(TruffleFile.LAST_ACCESS_TIME));
                Assert.assertEquals(file.getCreationTime(), attrs.get(TruffleFile.CREATION_TIME));
                Assert.assertEquals(file.isRegularFile(), attrs.get(TruffleFile.IS_REGULAR_FILE));
                Assert.assertEquals(file.isDirectory(), attrs.get(TruffleFile.IS_DIRECTORY));
                Assert.assertEquals(file.isSymbolicLink(), attrs.get(TruffleFile.IS_SYMBOLIC_LINK));
                Assert.assertEquals(!(file.isRegularFile() | file.isDirectory() | file.isSymbolicLink()), attrs.get(TruffleFile.IS_OTHER));
                Assert.assertEquals(file.size(), attrs.get(TruffleFile.SIZE).longValue());
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
            try {
                TruffleFile.Attributes attrs = file.getAttributes(Arrays.asList(TruffleFile.LAST_MODIFIED_TIME,
                                TruffleFile.LAST_ACCESS_TIME,
                                TruffleFile.CREATION_TIME,
                                TruffleFile.IS_REGULAR_FILE,
                                TruffleFile.IS_DIRECTORY,
                                TruffleFile.IS_SYMBOLIC_LINK,
                                TruffleFile.IS_OTHER,
                                TruffleFile.SIZE,
                                TruffleFile.UNIX_OWNER,
                                TruffleFile.UNIX_GROUP,
                                TruffleFile.UNIX_PERMISSIONS,
                                TruffleFile.UNIX_MODE,
                                TruffleFile.UNIX_INODE,
                                TruffleFile.UNIX_DEV,
                                TruffleFile.UNIX_RDEV,
                                TruffleFile.UNIX_NLINK,
                                TruffleFile.UNIX_UID,
                                TruffleFile.UNIX_GID,
                                TruffleFile.UNIX_CTIME));
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertNotNull(attrs);
                Assert.assertEquals(file.getLastModifiedTime(), attrs.get(TruffleFile.LAST_MODIFIED_TIME));
                Assert.assertEquals(file.getLastAccessTime(), attrs.get(TruffleFile.LAST_ACCESS_TIME));
                Assert.assertEquals(file.getCreationTime(), attrs.get(TruffleFile.CREATION_TIME));
                Assert.assertEquals(file.isRegularFile(), attrs.get(TruffleFile.IS_REGULAR_FILE));
                Assert.assertEquals(file.isDirectory(), attrs.get(TruffleFile.IS_DIRECTORY));
                Assert.assertEquals(file.isSymbolicLink(), attrs.get(TruffleFile.IS_SYMBOLIC_LINK));
                Assert.assertEquals(!(file.isRegularFile() | file.isDirectory() | file.isSymbolicLink()), attrs.get(TruffleFile.IS_OTHER));
                Assert.assertEquals(file.size(), attrs.get(TruffleFile.SIZE).longValue());
                Assert.assertEquals(file.getOwner(), attrs.get(TruffleFile.UNIX_OWNER));
                Assert.assertEquals(file.getGroup(), attrs.get(TruffleFile.UNIX_GROUP));
                Assert.assertEquals(file.getPosixPermissions(), attrs.get(TruffleFile.UNIX_PERMISSIONS));
                Assert.assertTrue(attrs.get(TruffleFile.UNIX_NLINK) >= 1);
                Assert.assertEquals(file.getOwner().hashCode(), attrs.get(TruffleFile.UNIX_UID).intValue());
                Assert.assertEquals(file.getGroup().hashCode(), attrs.get(TruffleFile.UNIX_GID).intValue());
                Assert.assertTrue(verifyPermissions(file.getPosixPermissions(), attrs.get(TruffleFile.UNIX_MODE)));
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            } catch (UnsupportedOperationException e) {
                // Verify that file system does not support unix attributes
                try {
                    cfg.fileSystem.readAttributes(path, "unix:*");
                    throw e;
                } catch (UnsupportedOperationException unsupported) {
                    // Expected
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
            try {
                TruffleFile.Attributes attrs = file.getAttributes(Arrays.asList(TruffleFile.LAST_MODIFIED_TIME,
                                TruffleFile.LAST_ACCESS_TIME,
                                TruffleFile.SIZE,
                                TruffleFile.UNIX_MODE,
                                TruffleFile.UNIX_INODE,
                                TruffleFile.UNIX_DEV,
                                TruffleFile.UNIX_NLINK,
                                TruffleFile.UNIX_UID,
                                TruffleFile.UNIX_GID,
                                TruffleFile.UNIX_CTIME));
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertNotNull(attrs);
                Assert.assertEquals(file.getLastModifiedTime(), attrs.get(TruffleFile.LAST_MODIFIED_TIME));
                Assert.assertEquals(file.getLastAccessTime(), attrs.get(TruffleFile.LAST_ACCESS_TIME));
                Assert.assertEquals(file.size(), attrs.get(TruffleFile.SIZE).longValue());
                Assert.assertTrue(verifyPermissions(file.getPosixPermissions(), attrs.get(TruffleFile.UNIX_MODE)));
                Assert.assertNotNull(attrs.get(TruffleFile.UNIX_INODE));
                Assert.assertNotNull(attrs.get(TruffleFile.UNIX_DEV));
                Assert.assertTrue(attrs.get(TruffleFile.UNIX_NLINK) >= 1);
                Assert.assertEquals(file.getOwner().hashCode(), attrs.get(TruffleFile.UNIX_UID).intValue());
                Assert.assertEquals(file.getGroup().hashCode(), attrs.get(TruffleFile.UNIX_GID).intValue());
                Assert.assertNotNull(attrs.get(TruffleFile.UNIX_CTIME));
                try {
                    attrs.get(TruffleFile.CREATION_TIME);
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    // Expected
                }
                try {
                    attrs.get(TruffleFile.IS_REGULAR_FILE);
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    // Expected
                }
                try {
                    attrs.get(TruffleFile.IS_DIRECTORY);
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    // Expected
                }
                try {
                    attrs.get(TruffleFile.IS_SYMBOLIC_LINK);
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    // Expected
                }
                try {
                    attrs.get(TruffleFile.IS_OTHER);
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    // Expected
                }
                try {
                    attrs.get(TruffleFile.UNIX_PERMISSIONS);
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    // Expected
                }
                try {
                    attrs.get(TruffleFile.UNIX_OWNER);
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    // Expected
                }
                try {
                    attrs.get(TruffleFile.UNIX_GROUP);
                    Assert.fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    // Expected
                }
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            } catch (UnsupportedOperationException e) {
                // Verify that file system does not support unix attributes
                try {
                    cfg.fileSystem.readAttributes(path, "unix:*");
                    throw e;
                } catch (UnsupportedOperationException unsupported) {
                    // Expected
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testCreateTempFile() {
        final Context ctx = cfg.getContext();
        languageAction = (Env env) -> {
            try {
                TruffleFile tmpf1 = env.createTempFile(null, "prefix", ".ext");
                Assert.assertTrue(tmpf1.exists());
                Assert.assertTrue(tmpf1.isRegularFile());
                Assert.assertTrue(tmpf1.getName().startsWith("prefix"));
                Assert.assertTrue(tmpf1.getName().endsWith(".ext"));
                TruffleFile tmpf2 = env.createTempFile(null, "prefix", ".ext");
                Assert.assertTrue(tmpf2.exists());
                Assert.assertTrue(tmpf2.isRegularFile());
                Assert.assertTrue(tmpf2.getName().startsWith("prefix"));
                Assert.assertTrue(tmpf2.getName().endsWith(".ext"));
                TruffleFile tmpf3 = env.createTempFile(null, "prefix", null);
                Assert.assertTrue(tmpf3.exists());
                Assert.assertTrue(tmpf3.isRegularFile());
                Assert.assertTrue(tmpf3.getName().startsWith("prefix"));
                Assert.assertTrue(tmpf3.getName().endsWith(".tmp"));
                Assert.assertNotEquals(tmpf1, tmpf2);
                Assert.assertNotEquals(tmpf1, tmpf3);
                Assert.assertNotEquals(tmpf2, tmpf3);
            } catch (SecurityException se) {
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testCreateTempFileInFolder() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile tmpDir = root.resolve(FILE_TMP_DIR);
                TruffleFile tmpf1 = env.createTempFile(tmpDir, "prefix", ".ext");
                Assert.assertTrue(tmpf1.exists());
                Assert.assertTrue(tmpf1.isRegularFile());
                Assert.assertEquals(tmpDir, tmpf1.getParent());
                Assert.assertTrue(tmpf1.getName().startsWith("prefix"));
                Assert.assertTrue(tmpf1.getName().endsWith(".ext"));
                TruffleFile tmpf2 = env.createTempFile(tmpDir, "prefix", ".ext");
                Assert.assertTrue(tmpf2.exists());
                Assert.assertTrue(tmpf2.isRegularFile());
                Assert.assertEquals(tmpDir, tmpf2.getParent());
                Assert.assertTrue(tmpf2.getName().startsWith("prefix"));
                Assert.assertTrue(tmpf2.getName().endsWith(".ext"));
                TruffleFile tmpf3 = env.createTempFile(tmpDir, "prefix", null);
                Assert.assertEquals(tmpDir, tmpf3.getParent());
                Assert.assertTrue(tmpf3.exists());
                Assert.assertTrue(tmpf3.isRegularFile());
                Assert.assertTrue(tmpf3.getName().startsWith("prefix"));
                Assert.assertTrue(tmpf3.getName().endsWith(".tmp"));
                Assert.assertNotEquals(tmpf1, tmpf2);
                Assert.assertNotEquals(tmpf1, tmpf3);
                Assert.assertNotEquals(tmpf2, tmpf3);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testCreateTempDirectory() {
        final Context ctx = cfg.getContext();
        languageAction = (Env env) -> {
            try {
                TruffleFile tmpf1 = env.createTempDirectory(null, "prefix");
                Assert.assertTrue(tmpf1.exists());
                Assert.assertTrue(tmpf1.isDirectory());
                Assert.assertTrue(tmpf1.getName().startsWith("prefix"));
                TruffleFile tmpf2 = env.createTempDirectory(null, "prefix");
                Assert.assertTrue(tmpf2.exists());
                Assert.assertTrue(tmpf2.isDirectory());
                Assert.assertTrue(tmpf2.getName().startsWith("prefix"));
                Assert.assertNotEquals(tmpf1, tmpf2);
            } catch (SecurityException se) {
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testCreateTempDirectoryInFolder() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.resolve(env, path);
            try {
                final TruffleFile tmpDir = root.resolve(FILE_TMP_DIR);
                TruffleFile tmpf1 = env.createTempDirectory(tmpDir, "prefix");
                Assert.assertTrue(tmpf1.exists());
                Assert.assertTrue(tmpf1.isDirectory());
                Assert.assertEquals(tmpDir, tmpf1.getParent());
                Assert.assertTrue(tmpf1.getName().startsWith("prefix"));
                TruffleFile tmpf2 = env.createTempDirectory(tmpDir, "prefix");
                Assert.assertTrue(tmpf2.exists());
                Assert.assertTrue(tmpf2.isDirectory());
                Assert.assertEquals(tmpDir, tmpf2.getParent());
                Assert.assertTrue(tmpf2.getName().startsWith("prefix"));
                Assert.assertNotEquals(tmpf1, tmpf2);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testVisitRelativeFolderAfterSetSurrentWorkingDirectory() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        Assume.assumeTrue(cfg.canRead() && cfg.allowsUserDir());
        languageAction = (Env env) -> {
            final TruffleFile folder = cfg.resolve(env, path.resolve(FOLDER_EXISTING));
            TruffleFile cwd = env.getCurrentWorkingDirectory();
            try {
                env.setCurrentWorkingDirectory(folder);

                TruffleFile relativeFolder = env.getInternalTruffleFile(FOLDER_EXISTING_INNER1);
                Assert.assertFalse(relativeFolder.isAbsolute());
                Assert.assertTrue(relativeFolder.isDirectory());
                relativeFolder.visit(new FileVisitor<TruffleFile>() {
                    @Override
                    public FileVisitResult preVisitDirectory(TruffleFile t, BasicFileAttributes bfa) throws IOException {
                        Assert.assertFalse(t.isAbsolute());
                        relativeFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(TruffleFile t, BasicFileAttributes bfa) throws IOException {
                        Assert.assertFalse(t.isAbsolute());
                        relativeFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(TruffleFile t, IOException ioe) throws IOException {
                        return FileVisitResult.TERMINATE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(TruffleFile t, IOException ioe) throws IOException {
                        Assert.assertFalse(t.isAbsolute());
                        relativeFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }
                }, Integer.MAX_VALUE);

                TruffleFile absoluteFolder = folder.resolve(FOLDER_EXISTING_INNER1);
                Assert.assertTrue(absoluteFolder.isAbsolute());
                Assert.assertTrue(absoluteFolder.isDirectory());
                absoluteFolder.visit(new FileVisitor<TruffleFile>() {
                    @Override
                    public FileVisitResult preVisitDirectory(TruffleFile t, BasicFileAttributes bfa) throws IOException {
                        Assert.assertTrue(t.isAbsolute());
                        absoluteFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(TruffleFile t, BasicFileAttributes bfa) throws IOException {
                        Assert.assertTrue(t.isAbsolute());
                        absoluteFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(TruffleFile t, IOException ioe) throws IOException {
                        return FileVisitResult.TERMINATE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(TruffleFile t, IOException ioe) throws IOException {
                        Assert.assertTrue(t.isAbsolute());
                        absoluteFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }
                }, Integer.MAX_VALUE);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            } finally {
                env.setCurrentWorkingDirectory(cwd);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
    }

    @Test
    public void testIsSameFile() throws Throwable {
        Context ctx = cfg.getContext();
        Path path = cfg.getPath();
        boolean canResolveAbsolutePath = cfg.allowsAbsolutePath();
        languageAction = (Env env) -> {
            TruffleFile root = cfg.resolve(env, path);
            TruffleFile wd = root.resolve(FOLDER_EXISTING).resolve(FOLDER_EXISTING_INNER1);
            TruffleFile file1 = wd.resolve(FILE_EXISTING);
            TruffleFile file2 = wd.resolve(FILE_EXISTING2);
            try {
                Assert.assertTrue(file1.isSameFile(file1));
                Assert.assertTrue(file1.isSameFile(file1, LinkOption.NOFOLLOW_LINKS));
                Assert.assertFalse(file1.isSameFile(file2));
                Assert.assertFalse(file1.isSameFile(file2, LinkOption.NOFOLLOW_LINKS));
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canResolveAbsolutePath);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canResolveAbsolutePath);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
        languageAction = (Env env) -> {
            TruffleFile root = cfg.resolve(env, path);
            TruffleFile wd = root.resolve(FOLDER_EXISTING).resolve(FOLDER_EXISTING_INNER1);
            TruffleFile file1 = wd.resolve(FILE_EXISTING);
            TruffleFile file1Relative = cfg.resolve(env, cfg.getUserDir()).relativize(file1);
            try {
                Assert.assertTrue(file1.isSameFile(file1Relative));
                Assert.assertTrue(file1.isSameFile(file1Relative, LinkOption.NOFOLLOW_LINKS));
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canResolveAbsolutePath);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canResolveAbsolutePath);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        ctx.eval(LANGUAGE_ID, "");
        languageAction = (Env env) -> {
            TruffleFile root = cfg.resolve(env, path);
            TruffleFile link = root.resolve(SYMLINK_EXISTING);
            Assume.assumeTrue("File System does not support optional symbolic links", link.exists(LinkOption.NOFOLLOW_LINKS));
            TruffleFile target = root.resolve(FOLDER_EXISTING);
            try {
                Assert.assertTrue(link.isSameFile(link));
                Assert.assertTrue(link.isSameFile(link, LinkOption.NOFOLLOW_LINKS));
                Assert.assertTrue(link.isSameFile(target));
                Assert.assertFalse(link.isSameFile(target, LinkOption.NOFOLLOW_LINKS));
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canResolveAbsolutePath);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canResolveAbsolutePath);
            } catch (IOException ioe) {
                throw new AssertionError(cfg.formatErrorMessage(ioe.getMessage()), ioe);
            }
        };
        try {
            ctx.eval(LANGUAGE_ID, "");
        } catch (PolyglotException pe) {
            if (pe.isHostException()) {
                throw pe.asHostException();
            }
        }
    }

    static boolean verifyPermissions(Set<PosixFilePermission> permissions, int mode) {
        int perms = 0;
        for (PosixFilePermission perm : permissions) {
            switch (perm) {
                case OWNER_READ:
                    perms |= 4 << 6;
                    break;
                case OWNER_WRITE:
                    perms |= 2 << 6;
                    break;
                case OTHERS_EXECUTE:
                    perms |= 1 << 6;
                    break;
                case GROUP_READ:
                    perms |= 4 << 3;
                    break;
                case GROUP_WRITE:
                    perms |= 2 << 3;
                    break;
                case GROUP_EXECUTE:
                    perms |= 1 << 3;
                    break;
                case OTHERS_READ:
                    perms |= 4;
                    break;
                case OTHERS_WRITE:
                    perms |= 2;
                    break;
                case OWNER_EXECUTE:
                    perms |= 1;
                    break;
            }
        }
        return (perms & mode) == perms;
    }

    static List<? extends Path> getRootDirectories() {
        List<Path> result = new ArrayList<>();
        if (OSUtils.isUnix()) {
            result.add(Paths.get("/"));
        } else {
            for (Path root : Paths.get("").getFileSystem().getRootDirectories()) {
                result.add(root);
            }
        }
        return result;
    }

    public static final class Configuration implements Closeable {
        private final String name;
        private final Context ctx;
        private final Path path;
        private final Path userDir;
        private final FileSystem fileSystem;
        private final boolean internal;
        private final boolean readable;
        private final boolean writable;
        private final boolean allowsUserDir;
        private final boolean allowsAbsolutePath;
        private final BiFunction<Env, String, TruffleFile> fileFactory;

        Configuration(
                        final String name,
                        final Context context,
                        final Path path,
                        final FileSystem fileSystem,
                        final boolean internal,
                        final boolean readable,
                        final boolean writable,
                        final boolean allowsUserDir) {
            this(name, context, path, path, fileSystem, internal, readable, writable, allowsUserDir);
        }

        Configuration(
                        final String name,
                        final Context context,
                        final Path path,
                        final Path userDir,
                        final FileSystem fileSystem,
                        final boolean internal,
                        final boolean readable,
                        final boolean writable,
                        final boolean allowsUserDir) {
            this(name, context, path, userDir, fileSystem, internal, readable, writable, allowsUserDir, allowsUserDir, (env, p) -> env.getPublicTruffleFile(p));
        }

        Configuration(
                        final String name,
                        final Context context,
                        final Path path,
                        final Path userDir,
                        final FileSystem fileSystem,
                        final boolean internal,
                        final boolean readable,
                        final boolean writable,
                        final boolean allowsUserDir,
                        final boolean allowsAbsolutePath,
                        final BiFunction<Env, String, TruffleFile> fileFactory) {
            Objects.requireNonNull(name, "Name must be non null.");
            Objects.requireNonNull(context, "Context must be non null.");
            Objects.requireNonNull(path, "Path must be non null.");
            Objects.requireNonNull(userDir, "UserDir must be non null.");
            Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            Objects.requireNonNull(fileFactory, "FileFactory must be non null.");
            this.name = name;
            this.ctx = context;
            this.path = path;
            this.userDir = userDir;
            this.fileSystem = fileSystem;
            this.internal = internal;
            this.readable = readable;
            this.writable = writable;
            this.allowsUserDir = allowsUserDir;
            this.allowsAbsolutePath = allowsAbsolutePath;
            this.fileFactory = fileFactory;
        }

        String getName() {
            return name;
        }

        Context getContext() {
            return ctx;
        }

        /**
         * Returns the work directory containing the test data.
         *
         * @return the work directory
         */
        Path getPath() {
            return path;
        }

        /**
         * The current working directory the test configuration was created with.
         *
         * @return the current working directory
         */
        Path getUserDir() {
            return userDir;
        }

        /**
         * Returns true if the test configuration allows read operations.
         *
         * @return {@code true} if reading is enabled
         */
        boolean canRead() {
            return readable;
        }

        /**
         * Returns true if the test configuration allows write operations.
         *
         * @return {@code true} if writing is enabled
         */
        boolean canWrite() {
            return writable;
        }

        /**
         * Returns true if the test configuration allows reading or setting current working
         * directory.
         *
         * @return {@code true} if get and set of current working directory is enabled
         */
        boolean allowsUserDir() {
            return allowsUserDir;
        }

        /**
         * Returns true if the test configuration allows conversion of relative path to absolute
         * path.
         *
         */
        boolean allowsAbsolutePath() {
            return allowsAbsolutePath;
        }

        boolean isInternal() {
            return internal;
        }

        String formatErrorMessage(final String message) {
            return String.format("%s, configuration: %s, root: %s",
                            message,
                            name,
                            path);
        }

        TruffleFile resolve(Env env, Path pathToResolve) {
            return fileFactory.apply(env, pathToResolve.toString());
        }

        TruffleFile resolve(Env env, String filePath) {
            return fileFactory.apply(env, filePath);
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public void close() throws IOException {
            try {
                ctx.close();
            } finally {
                deleteRecursively(path, fileSystem);
            }
        }
    }

    private static final class LanguageContext {
        private final Env env;

        LanguageContext(final Env env) {
            this.env = env;
        }

        Env env() {
            return env;
        }
    }

    @TruffleLanguage.Registration(id = LANGUAGE_ID, name = LANGUAGE_ID, version = "1.0")
    public static class VirtualizedFileSystemTestLanguage extends TruffleLanguage<LanguageContext> {

        @Override
        protected LanguageContext createContext(Env env) {
            return new LanguageContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final CharSequence result = request.getSource().getCharacters();
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    languageAction.accept(lookupContextReference(VirtualizedFileSystemTestLanguage.class).get().env());
                    return result;
                }
            });
        }
    }

    private static Path createContent(
                    final Path folder,
                    final FileSystem fs) throws IOException {
        Path existing = mkdirs(folder.resolve(FOLDER_EXISTING), fs);
        Path l2i1 = mkdirs(existing.resolve(FOLDER_EXISTING_INNER1), fs);
        write(l2i1.resolve(FILE_EXISTING), new byte[0], fs);
        write(l2i1.resolve(FILE_EXISTING2), new byte[0], fs);
        Path l2i2 = mkdirs(existing.resolve(FOLDER_EXISTING_INNER2), fs);
        write(l2i2.resolve(FILE_EXISTING), new byte[0], fs);
        write(l2i2.resolve(FILE_EXISTING2), new byte[0], fs);
        mkdirs(l2i2.resolve(FOLDER_EXISTING_INNER1), fs);
        Path l3i2 = mkdirs(l2i2.resolve(FOLDER_EXISTING_INNER2), fs);
        write(l3i2.resolve(FILE_EXISTING), new byte[0], fs);
        write(existing.resolve(FILE_EXISTING), FILE_EXISTING_CONTENT.getBytes(StandardCharsets.UTF_8), fs);
        touch(folder.resolve(FILE_EXISTING_WRITE_MMAP), fs);
        touch(folder.resolve(FILE_EXISTING_DELETE), fs);
        touch(folder.resolve(FILE_EXISTING_RENAME), fs);
        touch(folder.resolve(FILE_CHANGE_ATTRS), fs);
        try {
            ln(folder.resolve(FOLDER_EXISTING), folder.resolve(SYMLINK_EXISTING), fs);
        } catch (UnsupportedOperationException | FileSystemException unsupported) {
            // File system does not support optional symbolic links or required privilege is not
            // held by the client on Windows, the test will be ignored
        }
        mkdirs(folder.resolve(FILE_TMP_DIR), fs);
        return folder;
    }

    private static Path findRoot(final Path path) {
        Path current = path;
        Path prev = null;
        while (current != null) {
            prev = current;
            current = current.getParent();
        }
        return prev;
    }

    private static Path mkdirs(Path path, FileSystem fs) throws IOException {
        Path current = findRoot(path);
        for (Path element : path) {
            current = current.resolve(element);
            try {
                fs.createDirectory(current);
            } catch (FileAlreadyExistsException exists) {
            }
        }
        return path;
    }

    private static void write(Path path, byte[] content, FileSystem fs) throws IOException {
        final Set<StandardOpenOption> options = EnumSet.of(
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
        try (SeekableByteChannel ch = fs.newByteChannel(path, options)) {
            ByteBuffer bb = ByteBuffer.wrap(content);
            ch.write(bb);
        }
    }

    private static void touch(Path path, FileSystem fs) throws IOException {
        fs.newByteChannel(
                        path,
                        EnumSet.<StandardOpenOption> of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)).close();
    }

    private static void ln(Path file, Path link, FileSystem fs) throws IOException {
        fs.createSymbolicLink(link, file);
    }

    private static boolean isDirectory(final Path path, final FileSystem fs) throws IOException {
        final Map<String, Object> attrs = fs.readAttributes(path, "isDirectory", LinkOption.NOFOLLOW_LINKS);
        return (Boolean) attrs.get("isDirectory");
    }

    private static void deleteRecursively(Path path, final FileSystem fs) throws IOException {
        if (isDirectory(path, fs)) {
            DirectoryStream.Filter<Path> filter = (Path entry) -> true;
            try (DirectoryStream<Path> children = fs.newDirectoryStream(path, filter)) {
                for (Path child : children) {
                    deleteRecursively(child, fs);
                }
            }
        }
        try {
            fs.delete(path);
        } catch (NoSuchFileException notFound) {
        }
    }

    private static void resetLanguageHomes() {
        try {
            final Class<?> langCacheClz = Class.forName("com.oracle.truffle.polyglot.LanguageCache", true, FileSystemsTest.class.getClassLoader());
            final Method reset = langCacheClz.getDeclaredMethod("resetNativeImageCacheLanguageHomes");
            reset.setAccessible(true);
            reset.invoke(null);
        } catch (ReflectiveOperationException re) {
            throw new RuntimeException(re);
        }
    }

    /**
     * Sets the current working directory to a work folder. Used by configurations running on local
     * file system which have work folder in temp directory.
     *
     * @param ctx the context to set the cwd for
     * @param cwd the new current working directory
     * @param langHome language home to set
     */
    private static void setCwd(Context ctx, Path cwd, Path langHome) {
        languageAction = (env) -> {
            env.setCurrentWorkingDirectory(env.getInternalTruffleFile(cwd.toString()));
        };
        if (langHome != null) {
            System.setProperty("org.graalvm.language." + LANGUAGE_ID + ".home", langHome.toString());
            resetLanguageHomes();
        }
        ctx.eval(LANGUAGE_ID, "");
    }

    private static FileSystem createPreInitializeContextFileSystem() throws ReflectiveOperationException {
        Class<? extends FileSystem> clazz = Class.forName("com.oracle.truffle.polyglot.FileSystems$PreInitializeContextFileSystem").asSubclass(FileSystem.class);
        Constructor<? extends FileSystem> init = clazz.getDeclaredConstructor();
        init.setAccessible(true);
        return init.newInstance();
    }

    private static void switchToImageExecutionTime(FileSystem fileSystem, Path cwd) throws ReflectiveOperationException {
        String workDir = cwd.toString();
        Class<? extends FileSystem> clazz = Class.forName("com.oracle.truffle.polyglot.FileSystems$PreInitializeContextFileSystem").asSubclass(FileSystem.class);
        Method preInitClose = clazz.getDeclaredMethod("onPreInitializeContextEnd");
        preInitClose.setAccessible(true);
        preInitClose.invoke(fileSystem);
        Method patchStart = clazz.getDeclaredMethod("onLoadPreinitializedContext", FileSystem.class);
        patchStart.setAccessible(true);
        patchStart.invoke(fileSystem, FileSystemProviderTest.newFullIOFileSystem(Paths.get(workDir)));
    }

    static class ForwardingFileSystem implements FileSystem {

        private final FileSystem delegate;

        protected ForwardingFileSystem(final FileSystem fileSystem) {
            Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            this.delegate = fileSystem;
        }

        protected final FileSystem getDelegate() {
            return delegate;
        }

        @Override
        public Path parsePath(URI path) {
            return delegate.parsePath(path);
        }

        @Override
        public Path parsePath(String path) {
            return delegate.parsePath(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            delegate.checkAccess(path, modes, linkOptions);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            delegate.createDirectory(dir, attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            delegate.delete(path);
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            delegate.createLink(link, existing);
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            delegate.createSymbolicLink(link, target, attrs);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            return delegate.readSymbolicLink(link);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            delegate.copy(source, target, options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            delegate.move(source, target, options);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            return delegate.newByteChannel(path, options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            return delegate.newDirectoryStream(dir, filter);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return delegate.readAttributes(path, attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            delegate.setAttribute(path, attribute, value, options);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return delegate.toAbsolutePath(path);
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return delegate.toRealPath(path, linkOptions);
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            delegate.setCurrentWorkingDirectory(currentWorkingDirectory);
        }

        @Override
        public String getSeparator() {
            return delegate.getSeparator();
        }

        @Override
        public String getMimeType(Path path) {
            return delegate.getMimeType(path);
        }

        @Override
        public Charset getEncoding(Path path) {
            return delegate.getEncoding(path);
        }

        @Override
        public Path getTempDirectory() {
            return delegate.getTempDirectory();
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            return delegate.isSameFile(path1, path2, options);
        }
    }

    private static final class RestrictedFileSystem extends ForwardingFileSystem {

        private final Predicate<Path> readPredicate;
        private final Predicate<Path> writePredicate;

        RestrictedFileSystem(
                        final FileSystem delegate,
                        final Predicate<Path> readPredicate,
                        final Predicate<Path> writePredicate) {
            super(delegate);
            Objects.requireNonNull(readPredicate, "ReadPredicate must be non null");
            Objects.requireNonNull(writePredicate, "WritePredicate must be non null");
            this.readPredicate = readPredicate;
            this.writePredicate = writePredicate;
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            checkRead(path);
            return super.toRealPath(path, linkOptions);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            checkRead(dir);
            return super.newDirectoryStream(dir, filter);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            checkChannelOpenOptions(path, options);
            return super.newByteChannel(path, options, attrs);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            checkWrite(source);
            checkWrite(target);
            super.move(source, target, options);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            checkRead(source);
            checkWrite(target);
            super.copy(source, target, options);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            checkReadLink(link);
            return super.readSymbolicLink(link);
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            checkWrite(link);
            super.createSymbolicLink(link, target, attrs);
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            checkWrite(link);
            checkWrite(existing);
            super.createLink(link, existing);
        }

        @Override
        public void delete(Path path) throws IOException {
            checkDelete(path);
            super.delete(path);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            checkWrite(dir);
            super.createDirectory(dir, attrs);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            checkRead(path);
            if (modes.contains(AccessMode.WRITE) && !writePredicate.test(path)) {
                throw new IOException();
            }
            super.checkAccess(path, modes, linkOptions);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            checkRead(path);
            return super.readAttributes(path, attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            checkWrite(path);
            super.setAttribute(path, attribute, value, options);
        }

        private Path checkRead(Path path) {
            if (!readPredicate.test(path)) {
                throw new SecurityException("Read operation is not allowed for: " + path);
            }
            return path;
        }

        private Path checkWrite(Path path) {
            if (!writePredicate.test(path)) {
                throw new SecurityException("Write operation is not allowed for: " + path);
            }
            return path;
        }

        private Path checkDelete(Path path) {
            if (!writePredicate.test(path)) {
                throw new SecurityException("Delete operation is not allowed for: " + path);
            }
            return path;
        }

        private Path checkReadLink(Path path) {
            if (!readPredicate.test(path)) {
                throw new SecurityException("Read link operation is not allowed for: " + path);
            }
            return path;
        }

        private Path checkChannelOpenOptions(
                        final Path path,
                        final Set<? extends OpenOption> options) {
            boolean checkRead = options.contains(StandardOpenOption.READ);
            boolean checkWrite = options.contains(StandardOpenOption.WRITE);
            if (!checkRead && !checkWrite) {
                if (options.contains(StandardOpenOption.APPEND)) {
                    checkWrite = true;
                } else {
                    checkRead = true;
                }
            }
            if (checkRead) {
                checkRead(path);
            }
            if (checkWrite) {
                checkWrite(path);
            }
            if (options.contains(StandardOpenOption.DELETE_ON_CLOSE)) {
                checkDelete(path);
            }
            return path;
        }
    }

    private static final class AccessPredicate implements Predicate<Path> {

        private final Collection<? extends Path> allowedRoots;
        private volatile FileSystem fs;

        AccessPredicate(final Collection<? extends Path> allowedRoots) {
            this.allowedRoots = allowedRoots;
        }

        void setFileSystem(FileSystem fileSystem) {
            this.fs = fileSystem;
        }

        @Override
        public boolean test(Path path) {
            if (fs == null) {
                throw new IllegalStateException("FileSystem is not set.");
            }
            return getOwnerRoot(fs.toAbsolutePath(path), allowedRoots) != null;
        }

        private static Path getOwnerRoot(final Path absolutePath, final Collection<? extends Path> roots) {
            for (Path root : roots) {
                for (Path currentPath = absolutePath; currentPath != null; currentPath = currentPath.getParent()) {
                    if (currentPath.equals(root)) {
                        return root;
                    }
                }
            }
            return null;
        }
    }

    private static final class TestVisitor implements FileVisitor<TruffleFile> {

        private Node current;

        private TestVisitor(Node root) {
            this.current = root;
        }

        void assertConsumed() {
            Assert.assertTrue(current.children.isEmpty());
        }

        @Override
        public FileVisitResult preVisitDirectory(TruffleFile dir, BasicFileAttributes attrs) throws IOException {
            Node node = null;
            for (Node child : current.children) {
                if (child.file.equals(dir)) {
                    node = child;
                    break;
                }
            }
            Assert.assertNotNull(node);
            Assert.assertTrue(node.folder);
            if (node.action == FileVisitResult.SKIP_SIBLINGS || node.action == FileVisitResult.SKIP_SUBTREE) {
                current.children.remove(node);
            } else {
                current = node;
            }
            return node.action;
        }

        @Override
        public FileVisitResult visitFile(TruffleFile file, BasicFileAttributes attrs) throws IOException {
            Node node = null;
            for (Node child : current.children) {
                if (child.file.equals(file)) {
                    node = child;
                    break;
                }
            }
            Assert.assertNotNull(node);
            Assert.assertFalse(node.folder);
            current.children.remove(node);
            return node.action;
        }

        @Override
        public FileVisitResult visitFileFailed(TruffleFile file, IOException exc) throws IOException {
            return FileVisitResult.TERMINATE;
        }

        @Override
        public FileVisitResult postVisitDirectory(TruffleFile dir, IOException exc) throws IOException {
            Assert.assertTrue(current.children.isEmpty());
            Node prev = current;
            current = current.parent;
            current.children.remove(prev);
            return FileVisitResult.CONTINUE;
        }

        static Builder newBuilder(TruffleFile root) {
            return new Builder(root);
        }

        static final class Builder {
            private final Node rootNode;
            private Node currentScope;

            Builder(TruffleFile start) {
                rootNode = new Node(null, (TruffleFile) null, true);
                this.currentScope = new Node(rootNode, start, start.isDirectory());
                rootNode.children.add(this.currentScope);
            }

            Builder folder(String name) {
                Node newScope = new Node(currentScope, name, true);
                currentScope.children.add(newScope);
                currentScope = newScope;
                return this;
            }

            Builder end() {
                currentScope = currentScope.parent;
                if (currentScope == null) {
                    throw new IllegalStateException("Closing non opened folder.");
                }
                return this;
            }

            Builder file(String name) {
                Node node = new Node(currentScope, name, false);
                currentScope.children.add(node);
                return this;
            }

            Builder skipSubTree() {
                currentScope.action = FileVisitResult.SKIP_SUBTREE;
                return this;
            }

            TestVisitor build() {
                return new TestVisitor(rootNode);
            }
        }

        private static final class Node {

            private final Node parent;
            private final TruffleFile file;
            private final boolean folder;
            private final Collection<Node> children;
            private FileVisitResult action;

            Node(Node parent, TruffleFile file, boolean folder) {
                this.parent = parent;
                this.file = file;
                this.folder = folder;
                this.children = folder ? new ArrayList<>() : Collections.emptyList();
                this.action = FileVisitResult.CONTINUE;
            }

            Node(Node parent, String name, boolean folder) {
                this(parent, parent.file.resolve(name), folder);
            }

            @Override
            public String toString() {
                return file.toString();
            }
        }
    }
}
