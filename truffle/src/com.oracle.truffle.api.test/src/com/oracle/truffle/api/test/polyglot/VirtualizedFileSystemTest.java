/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
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

@RunWith(Parameterized.class)
public class VirtualizedFileSystemTest {

    private static final String LANGAUGE_ID = "virtualised-fs-lang";
    private static final String FOLDER_EXISTING = "folder";
    private static final String FILE_EXISTING = "existing_file.txt";
    private static final String FILE_EXISTING_CONTENT = "Existing File Content";
    private static final String FILE_EXISTING_WRITE_MMAP = "write_mmap.txt";
    private static final String FILE_EXISTING_DELETE = "delete.txt";
    private static final String FILE_EXISTING_RENAME = "rename.txt";
    private static final String FILE_NEW_WRITE_CHANNEL = "write_channel.txt";
    private static final String FILE_NEW_WRITE_STREAM = "write_stream.txt";
    private static final String FILE_NEW_CREATE_DIR = "new_dir";
    private static final String FILE_NEW_CREATE_FILE = "new_file.txt";
    private static final String FILE_NEW_RENAME = "new_rename.txt";
    private static Collection<Configuration> cfgs;
    private static Consumer<Env> languageAction;

    private final Configuration cfg;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Configuration> createParameters() throws IOException {
        final List<Configuration> result = new ArrayList<>();
        final FileSystem fullIO = FileSystemProviderTest.newFullIOFileSystem();
        final Path cwd = Paths.get("").toAbsolutePath();
        // Full IO
        Path accessibleDir = createContent(
                        Files.createTempDirectory(VirtualizedFileSystemTest.class.getSimpleName()),
                        fullIO);
        Context ctx = Context.newBuilder(LANGAUGE_ID).allowIO(true).build();
        result.add(new Configuration("Full IO", ctx, accessibleDir, cwd, fullIO, false, true, true, true));
        // No IO
        ctx = Context.newBuilder(LANGAUGE_ID).allowIO(false).build();
        Path privateDir = createContent(
                        Files.createTempDirectory(VirtualizedFileSystemTest.class.getSimpleName()),
                        fullIO);
        result.add(new Configuration("No IO", ctx, privateDir, cwd, fullIO, false, false, false, true));
        // No IO under language home
        ctx = Context.newBuilder(LANGAUGE_ID).allowIO(false).build();
        privateDir = createContent(
                        Files.createTempDirectory(VirtualizedFileSystemTest.class.getSimpleName()),
                        fullIO);
        final String langHome = privateDir.toString();
        result.add(new Configuration("No IO under language home", ctx, privateDir, cwd, fullIO, false, true, false, true, () -> {
            System.setProperty(LANGAUGE_ID + ".home", langHome);
        }));
        // Checked IO
        accessibleDir = createContent(
                        Files.createTempDirectory(VirtualizedFileSystemTest.class.getSimpleName()),
                        fullIO);
        Path readOnlyDir = createContent(
                        Files.createTempDirectory(VirtualizedFileSystemTest.class.getSimpleName()),
                        fullIO);
        privateDir = createContent(
                        Files.createTempDirectory(VirtualizedFileSystemTest.class.getSimpleName()),
                        fullIO);
        FileSystem fileSystem = new RestrictedFileSystem(
                        FileSystemProviderTest.newFullIOFileSystem(accessibleDir),
                        new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir)),
                        new AccessPredicate(Collections.singleton(accessibleDir)));
        ctx = Context.newBuilder(LANGAUGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Conditional IO - read/write part", ctx, accessibleDir, fullIO, false, true, true, true));
        fileSystem = new RestrictedFileSystem(
                        FileSystemProviderTest.newFullIOFileSystem(readOnlyDir),
                        new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir)),
                        new AccessPredicate(Collections.singleton(accessibleDir)));
        ctx = Context.newBuilder(LANGAUGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Conditional IO - read only part", ctx, readOnlyDir, fullIO, false, true, false, true));
        fileSystem = new RestrictedFileSystem(
                        FileSystemProviderTest.newFullIOFileSystem(privateDir),
                        new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir)),
                        new AccessPredicate(Collections.singleton(accessibleDir)));
        ctx = Context.newBuilder(LANGAUGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Conditional IO - private part", ctx, privateDir, fullIO, false, false, false, true));

        // Memory
        fileSystem = new MemoryFileSystem();
        Path memDir = mkdirs(fileSystem.parsePath(URI.create("file:///work")), fileSystem);
        ((MemoryFileSystem) fileSystem).setUserDir(memDir);
        createContent(memDir, fileSystem);
        ctx = Context.newBuilder(LANGAUGE_ID).allowIO(true).fileSystem(fileSystem).build();
        result.add(new Configuration("Memory FileSystem", ctx, memDir, fileSystem, false, true, true, true));
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

    public VirtualizedFileSystemTest(final Configuration cfg) {
        this.cfg = cfg;
    }

    @Before
    public void setUp() {
        Optional.ofNullable(this.cfg.getBeforeAction()).ifPresent(Runnable::run);
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
            final TruffleFile file = cfg.needsURI() ? env.getTruffleFile(folderExisting.toUri()) : env.getTruffleFile(folderExisting.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testListNonNormalized() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final Path folderExisting = path.resolve(FOLDER_EXISTING);
            TruffleFile file = cfg.needsURI() ? env.getTruffleFile(folderExisting.toUri()) : env.getTruffleFile(folderExisting.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testReadUsingChannel() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testReadUsingStream() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testWriteUsingChannel() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testWriteUsingStream() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testCreateDirectoryTest() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testCreateFileTest() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testDelete() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testExists() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
            try {
                final TruffleFile toCreate = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean exists = toCreate.exists();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertTrue(cfg.formatErrorMessage("File should exist"), exists);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testGetAbsoluteFile() {
        final Context ctx = cfg.getContext();
        final boolean allowsUserDir = cfg.allowsUserDir();
        if (cfg.needsURI()) {
            // Nothing to test for URI path
            return;
        }
        languageAction = (Env env) -> {
            final TruffleFile file = env.getTruffleFile(FOLDER_EXISTING).resolve(FILE_EXISTING);
            try {
                final TruffleFile absolute = file.getAbsoluteFile();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), allowsUserDir);
                Assert.assertEquals(absolute.getPath(), cfg.getUserDir().resolve(FOLDER_EXISTING).resolve(FILE_EXISTING).toString());
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), allowsUserDir);
            }
        };
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testGetCanonicalFile() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testGetLastModified() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testIsDirectory() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean isDir = file.isDirectory();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertFalse(cfg.formatErrorMessage("Not directory"), isDir);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testRegularFile() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean isFile = file.isRegularFile();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertTrue(cfg.formatErrorMessage("Is file"), isFile);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void isReadable() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean readable = file.isReadable();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertTrue(cfg.formatErrorMessage("Is readable"), readable);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void isWritable() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean writable = file.isWritable();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertTrue(cfg.formatErrorMessage("Is writable"), writable);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void isExecutable() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
            try {
                final TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                final boolean executable = file.isExecutable();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), canRead);
                Assert.assertFalse(cfg.formatErrorMessage("Is executable"), executable);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), canRead);
            }
        };
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testRename() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        final boolean canWrite = cfg.canWrite();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testSize() {
        final Context ctx = cfg.getContext();
        final Path path = cfg.getPath();
        final boolean canRead = cfg.canRead();
        languageAction = (Env env) -> {
            final TruffleFile root = cfg.needsURI() ? env.getTruffleFile(path.toUri()) : env.getTruffleFile(path.toString());
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
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testToUri() {
        final Context ctx = cfg.getContext();
        final Path userDir = cfg.getUserDir();
        final boolean allowsUserDir = cfg.allowsUserDir();
        if (cfg.needsURI()) {
            // Nothing to test for URI path
            return;
        }
        languageAction = (Env env) -> {
            final TruffleFile file = env.getTruffleFile(FOLDER_EXISTING).resolve(FILE_EXISTING);
            try {
                final URI uri = file.toUri();
                Assert.assertTrue(cfg.formatErrorMessage("Expected SecurityException"), allowsUserDir);
                Assert.assertEquals(cfg.formatErrorMessage("URI"), userDir.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING).toUri(), uri);
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), allowsUserDir);
            }
        };
        ctx.eval(LANGAUGE_ID, "");
    }

    @Test
    public void testNormalize() {
        final Context ctx = cfg.getContext();
        final boolean allowsUserDir = cfg.allowsUserDir();
        languageAction = (Env env) -> {
            TruffleFile fileNormalized = env.getTruffleFile(FOLDER_EXISTING);
            Assert.assertEquals(fileNormalized, fileNormalized.normalize());
            Assert.assertSame(fileNormalized, fileNormalized.normalize());
            TruffleFile fileNonNormalized = env.getTruffleFile(FOLDER_EXISTING + "/lib/../.");
            Assert.assertEquals(fileNormalized.getPath() + "/lib/../.", fileNonNormalized.getPath());
            Assert.assertEquals(fileNormalized.getPath(), fileNonNormalized.normalize().getPath());
            Assert.assertEquals(fileNormalized, fileNonNormalized.normalize());
            try {
                Assert.assertEquals(fileNormalized.getAbsoluteFile().getPath() + "/lib/../.", fileNonNormalized.getAbsoluteFile().getPath());
                Assert.assertEquals(fileNormalized.getAbsoluteFile().getPath(), fileNonNormalized.normalize().getAbsoluteFile().getPath());
            } catch (SecurityException se) {
                Assert.assertFalse(cfg.formatErrorMessage("Unexpected SecurityException"), allowsUserDir);
            }
            Assert.assertEquals(".", fileNonNormalized.getName());
            Assert.assertEquals("..", fileNonNormalized.getParent().getName());
            Assert.assertEquals("lib", fileNonNormalized.getParent().getParent().getName());
        };
        ctx.eval(LANGAUGE_ID, "");
    }

    public static final class Configuration implements Closeable {
        private final String name;
        private final Context ctx;
        private final Path path;
        private final Path userDir;
        private final FileSystem fileSystem;
        private final boolean needsURI;
        private final boolean readable;
        private final boolean writable;
        private final boolean allowsUserDir;
        private final Runnable beforeAction;

        Configuration(
                        final String name,
                        final Context context,
                        final Path path,
                        final FileSystem fileSystem,
                        final boolean needsURI,
                        final boolean readable,
                        final boolean writable,
                        final boolean allowsUserDir) {
            this(name, context, path, path, fileSystem, needsURI, readable, writable, allowsUserDir, null);
        }

        Configuration(
                        final String name,
                        final Context context,
                        final Path path,
                        final Path userDir,
                        final FileSystem fileSystem,
                        final boolean needsURI,
                        final boolean readable,
                        final boolean writable,
                        final boolean allowsUserDir) {
            this(name, context, path, userDir, fileSystem, needsURI, readable, writable, allowsUserDir, null);
        }

        Configuration(
                        final String name,
                        final Context context,
                        final Path path,
                        final Path userDir,
                        final FileSystem fileSystem,
                        final boolean needsURI,
                        final boolean readable,
                        final boolean writable,
                        final boolean allowsUserDir,
                        final Runnable beforeAction) {
            Objects.requireNonNull(name, "Name must be non null.");
            Objects.requireNonNull(context, "Context must be non null.");
            Objects.requireNonNull(path, "Path must be non null.");
            Objects.requireNonNull(userDir, "UserDir must be non null.");
            Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            this.name = name;
            this.ctx = context;
            this.path = path;
            this.userDir = userDir;
            this.fileSystem = fileSystem;
            this.needsURI = needsURI;
            this.readable = readable;
            this.writable = writable;
            this.allowsUserDir = allowsUserDir;
            this.beforeAction = beforeAction;
        }

        Runnable getBeforeAction() {
            return beforeAction;
        }

        String getName() {
            return name;
        }

        Context getContext() {
            return ctx;
        }

        Path getPath() {
            return path;
        }

        Path getUserDir() {
            return userDir;
        }

        boolean needsURI() {
            return needsURI;
        }

        boolean canRead() {
            return readable;
        }

        boolean canWrite() {
            return writable;
        }

        boolean allowsUserDir() {
            return allowsUserDir;
        }

        String formatErrorMessage(final String message) {
            return String.format("%s, configuration: %s, root: %s",
                            message,
                            name,
                            path);
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

    @TruffleLanguage.Registration(id = LANGAUGE_ID, name = LANGAUGE_ID, version = "1.0")
    public static class VirtualizedFileSystemTestLanguage extends TruffleLanguage<LanguageContext> {

        @Override
        protected LanguageContext createContext(Env env) {
            return new LanguageContext(env);
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final CharSequence result = request.getSource().getCharacters();
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    languageAction.accept(getContextReference().get().env());
                    return result;
                }
            });
        }
    }

    private static Path createContent(
                    final Path folder,
                    final FileSystem fs) throws IOException {
        mkdirs(folder.resolve(FOLDER_EXISTING), fs);
        write(folder.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING), FILE_EXISTING_CONTENT.getBytes(StandardCharsets.UTF_8), fs);
        touch(folder.resolve(FILE_EXISTING_WRITE_MMAP), fs);
        touch(folder.resolve(FILE_EXISTING_DELETE), fs);
        touch(folder.resolve(FILE_EXISTING_RENAME), fs);
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
            final Class<?> langCacheClz = Class.forName("com.oracle.truffle.polyglot.LanguageCache", true, VirtualizedFileSystemTest.class.getClassLoader());
            final Method reset = langCacheClz.getDeclaredMethod("resetNativeImageCacheLanguageHomes");
            reset.setAccessible(true);
            reset.invoke(null);
        } catch (ReflectiveOperationException re) {
            throw new RuntimeException(re);
        }
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

        AccessPredicate(
                        final Collection<? extends Path> allowedRoots) {
            this.allowedRoots = allowedRoots;
        }

        @Override
        @SuppressWarnings("fallthrough")
        public boolean test(Path path) {
            return getOwnerRoot(path, allowedRoots) != null;
        }

        private static Path getOwnerRoot(final Path path, final Collection<? extends Path> roots) {
            final Path absolutePath = path.toAbsolutePath();
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
}
