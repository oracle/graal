/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import org.graalvm.home.Version;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.OSUtils;

@RunWith(Parameterized.class)
public class FileSystemsTest {
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
    private static final String FULL_IO_DEPRECATED = "Full IO - Deprecated";
    private static final String NO_IO_DEPRECATED = "No IO - Deprecated";
    private static final String CUSTOM_FS_DEPRECATED = "Custom File System - Deprecated";
    private static final String FULL_IO = "Full IO";
    private static final String NO_IO = "No IO";
    private static final String NO_IO_UNDER_LANGUAGE_HOME_PUBLIC_FILE = "No IO under language home - public file";
    private static final String NO_IO_UNDER_LANGUAGE_HOME_INTERNAL_FILE = "No IO under language home - internal file";
    private static final String READ_ONLY = "Read Only";
    private static final String CONDITIONAL_IO_READ_WRITE_PART = "Conditional IO - read/write part";
    private static final String CONDITIONAL_IO_READ_ONLY_PART = "Conditional IO - read only part";
    private static final String CONDITIONAL_IO_PRIVATE_PART = "Conditional IO - private part";
    private static final String MEMORY_FILE_SYSTEM = "Memory FileSystem";
    private static final String MEMORY_FILE_SYSTEM_WITH_LANGUAGE_HOMES = "Memory FileSystem With Language Homes";
    private static final String MEMORY_FILE_SYSTEM_WITH_LANGUAGE_HOMES_INTERNAL_FILE = "Memory FileSystem With Language Homes - internal file";
    private static final String CONTEXT_PRE_INITIALIZATION_FILESYSTEM_BUILD_TIME = "Context pre-initialization filesystem build time";
    private static final String CONTEXT_PRE_INITIALIZATION_FILESYSTEM_EXECUTION_TIME = "Context pre-initialization filesystem execution time";

    private static final Map<String, Configuration> cfgs = new HashMap<>();

    private static final Version OSX_10_13 = Version.create(10, 13);

    private final Configuration cfg;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<String> createParameters() {
        return List.of(FULL_IO_DEPRECATED,
                        NO_IO_DEPRECATED,
                        CUSTOM_FS_DEPRECATED,
                        FULL_IO,
                        NO_IO,
                        NO_IO_UNDER_LANGUAGE_HOME_PUBLIC_FILE,
                        NO_IO_UNDER_LANGUAGE_HOME_INTERNAL_FILE,
                        READ_ONLY,
                        CONDITIONAL_IO_READ_WRITE_PART,
                        CONDITIONAL_IO_READ_ONLY_PART,
                        CONDITIONAL_IO_PRIVATE_PART,
                        MEMORY_FILE_SYSTEM,
                        MEMORY_FILE_SYSTEM_WITH_LANGUAGE_HOMES,
                        MEMORY_FILE_SYSTEM_WITH_LANGUAGE_HOMES_INTERNAL_FILE,
                        CONTEXT_PRE_INITIALIZATION_FILESYSTEM_BUILD_TIME,
                        CONTEXT_PRE_INITIALIZATION_FILESYSTEM_EXECUTION_TIME);
    }

    @BeforeClass
    public static void createConfigurations() throws IOException, ReflectiveOperationException {
        assert cfgs.isEmpty();
        final FileSystem fullIO = FileSystem.newDefaultFileSystem();
        createDeprecatedConfigurations(fullIO);

        // Full IO
        Path accessibleDir;
        Context ctx;
        if (TruffleTestAssumptions.isNoClassLoaderEncapsulation()) { // setCwd not supported
            accessibleDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                            fullIO);
            ctx = Context.newBuilder().allowIO(IOAccess.ALL).build();
            setCwd(ctx, accessibleDir);
            cfgs.put(FULL_IO, new Configuration(FULL_IO, ctx, accessibleDir, fullIO, true, true, true, true));
        }

        // No IO
        ctx = Context.newBuilder().allowIO(IOAccess.NONE).build();
        Path privateDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        cfgs.put(NO_IO, new Configuration(NO_IO, ctx, privateDir, Paths.get("").toAbsolutePath(), fullIO, true, false, false, false));

        // No IO under language home - public file
        if (TruffleTestAssumptions.isNoClassLoaderEncapsulation()) { // setCwd not supported
            Engine engine = Engine.create();
            privateDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()).toRealPath(),
                            fullIO);
            markAsLanguageHome(engine, privateDir);
            ctx = Context.newBuilder().engine(engine).allowIO(IOAccess.NONE).build();
            setCwd(ctx, privateDir);
            cfgs.put(NO_IO_UNDER_LANGUAGE_HOME_PUBLIC_FILE,
                            new Configuration(NO_IO_UNDER_LANGUAGE_HOME_PUBLIC_FILE, ctx, privateDir, privateDir, fullIO, true, false, false, false, false, true, engine));

            // No IO under language home - internal file
            engine = Engine.create();
            privateDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()).toRealPath(),
                            fullIO);
            markAsLanguageHome(engine, privateDir);
            ctx = Context.newBuilder().engine(engine).allowIO(IOAccess.NONE).build();
            setCwd(ctx, privateDir);
            cfgs.put(NO_IO_UNDER_LANGUAGE_HOME_INTERNAL_FILE,
                            new Configuration(NO_IO_UNDER_LANGUAGE_HOME_INTERNAL_FILE, ctx, privateDir, privateDir, fullIO, true, true, false, false, true, false, engine));
        }

        // Read Only
        IOAccess ioAccess;
        if (TruffleTestAssumptions.isNoClassLoaderEncapsulation()) { // setCwd not supported
            accessibleDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                            fullIO);
            ioAccess = IOAccess.newBuilder().fileSystem(FileSystem.newReadOnlyFileSystem(fullIO)).build();
            ctx = Context.newBuilder().allowIO(ioAccess).build();
            setCwd(ctx, accessibleDir);
            cfgs.put(READ_ONLY, new Configuration(READ_ONLY, ctx, accessibleDir, fullIO, true, true, false, true));
        }

        // Checked IO
        accessibleDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        Path readOnlyDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        privateDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        AccessPredicate read = new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir));
        AccessPredicate write = new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir));
        FileSystem fileSystem = new RestrictedFileSystem(newFullIOFileSystem(accessibleDir), read, write);
        read.setFileSystem(fileSystem);
        write.setFileSystem(fileSystem);
        ioAccess = IOAccess.newBuilder().fileSystem(fileSystem).build();
        ctx = Context.newBuilder().allowIO(ioAccess).build();
        cfgs.put(CONDITIONAL_IO_READ_WRITE_PART, new Configuration(CONDITIONAL_IO_READ_WRITE_PART, ctx, accessibleDir, fullIO, false, true, true, true));
        read = new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir));
        write = new AccessPredicate(Collections.singleton(accessibleDir));
        fileSystem = new RestrictedFileSystem(newFullIOFileSystem(readOnlyDir), read, write);
        read.setFileSystem(fileSystem);
        write.setFileSystem(fileSystem);
        ioAccess = IOAccess.newBuilder().fileSystem(fileSystem).build();
        ctx = Context.newBuilder().allowIO(ioAccess).build();
        cfgs.put(CONDITIONAL_IO_READ_ONLY_PART, new Configuration(CONDITIONAL_IO_READ_ONLY_PART, ctx, readOnlyDir, fullIO, false, true, false, true));
        read = new AccessPredicate(Arrays.asList(accessibleDir, readOnlyDir));
        write = new AccessPredicate(Collections.singleton(accessibleDir));
        fileSystem = new RestrictedFileSystem(newFullIOFileSystem(privateDir), read, write);
        read.setFileSystem(fileSystem);
        write.setFileSystem(fileSystem);
        ioAccess = IOAccess.newBuilder().fileSystem(fileSystem).build();
        ctx = Context.newBuilder().allowIO(ioAccess).build();
        cfgs.put(CONDITIONAL_IO_PRIVATE_PART, new Configuration(CONDITIONAL_IO_PRIVATE_PART, ctx, privateDir, fullIO, false, false, false, true));

        // Memory
        fileSystem = new MemoryFileSystem();
        Path memDir = mkdirs(fileSystem.toAbsolutePath(fileSystem.parsePath("work")), fileSystem);
        fileSystem.setCurrentWorkingDirectory(memDir);
        createContent(memDir, fileSystem);
        ioAccess = IOAccess.newBuilder().fileSystem(fileSystem).build();
        ctx = Context.newBuilder().allowIO(ioAccess).build();
        cfgs.put(MEMORY_FILE_SYSTEM, new Configuration(MEMORY_FILE_SYSTEM, ctx, memDir, fileSystem, false, true, true, true));

        // Memory with language home
        fileSystem = FileSystem.allowLanguageHomeAccess(new MemoryFileSystem());
        memDir = mkdirs(fileSystem.toAbsolutePath(fileSystem.parsePath("work")), fileSystem);
        fileSystem.setCurrentWorkingDirectory(memDir);
        createContent(memDir, fileSystem);
        ioAccess = IOAccess.newBuilder().fileSystem(fileSystem).build();
        ctx = Context.newBuilder().allowIO(ioAccess).build();
        cfgs.put(MEMORY_FILE_SYSTEM_WITH_LANGUAGE_HOMES, new Configuration(MEMORY_FILE_SYSTEM_WITH_LANGUAGE_HOMES, ctx, memDir, fileSystem, false, true, true, true));

        if (TruffleTestAssumptions.isNoClassLoaderEncapsulation()) { // setCwd not supported
            // Memory with language home - in language home
            fileSystem = FileSystem.allowLanguageHomeAccess(new MemoryFileSystem());
            memDir = mkdirs(fileSystem.toAbsolutePath(fileSystem.parsePath("work")), fileSystem);
            fileSystem.setCurrentWorkingDirectory(memDir);
            privateDir = createContent(memDir, fileSystem);
            Engine engine = Engine.create();
            markAsLanguageHome(engine, privateDir);
            ioAccess = IOAccess.newBuilder().fileSystem(fileSystem).build();
            ctx = Context.newBuilder().engine(engine).allowIO(ioAccess).build();
            setCwd(ctx, privateDir);
            cfgs.put(MEMORY_FILE_SYSTEM_WITH_LANGUAGE_HOMES_INTERNAL_FILE,
                            new Configuration(MEMORY_FILE_SYSTEM_WITH_LANGUAGE_HOMES_INTERNAL_FILE, ctx, privateDir, privateDir, fileSystem, false, true, true, true, true, true, engine));

            // PreInitializeContextFileSystem in image build time
            fileSystem = createPreInitializeContextFileSystem();
            Path workDir = mkdirs(fileSystem.parsePath(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()).toString()), fileSystem);
            fileSystem.setCurrentWorkingDirectory(workDir);
            createContent(workDir, fileSystem);
            ioAccess = IOAccess.newBuilder().fileSystem(fileSystem).build();
            ctx = Context.newBuilder().allowIO(ioAccess).build();
            cfgs.put(CONTEXT_PRE_INITIALIZATION_FILESYSTEM_BUILD_TIME, new Configuration(CONTEXT_PRE_INITIALIZATION_FILESYSTEM_BUILD_TIME, ctx, workDir, fileSystem, true, true, true, true));

            // PreInitializeContextFileSystem in image execution time
            fileSystem = createPreInitializeContextFileSystem();
            workDir = mkdirs(fileSystem.parsePath(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()).toString()), fileSystem);
            fileSystem.setCurrentWorkingDirectory(workDir);
            switchToImageExecutionTime(fileSystem, workDir);
            createContent(workDir, fileSystem);
            ioAccess = IOAccess.newBuilder().fileSystem(fileSystem).build();
            ctx = Context.newBuilder().allowIO(ioAccess).build();
            cfgs.put(CONTEXT_PRE_INITIALIZATION_FILESYSTEM_EXECUTION_TIME, new Configuration(CONTEXT_PRE_INITIALIZATION_FILESYSTEM_EXECUTION_TIME, ctx, workDir, fileSystem, true, true, true, true));
        }

    }

    @SuppressWarnings("deprecation")
    private static void createDeprecatedConfigurations(FileSystem fullIO) throws IOException {
        // Full IO using deprecated Context.Builder methods
        Path accessibleDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        Context ctx = Context.newBuilder().allowIO(true).build();
        setCwd(ctx, accessibleDir);
        cfgs.put(FULL_IO_DEPRECATED, new Configuration(FULL_IO, ctx, accessibleDir, fullIO, true, true, true, true));

        // No IO using deprecated Context.Builder methods
        ctx = Context.newBuilder().allowIO(false).build();
        Path privateDir = createContent(Files.createTempDirectory(FileSystemsTest.class.getSimpleName()),
                        fullIO);
        cfgs.put(NO_IO_DEPRECATED, new Configuration(NO_IO, ctx, privateDir, Paths.get("").toAbsolutePath(), fullIO, true, false, false, false));

        // Memory file system using deprecated Context.Builder methods
        FileSystem fileSystem = new MemoryFileSystem();
        Path memDir = mkdirs(fileSystem.toAbsolutePath(fileSystem.parsePath("work")), fileSystem);
        fileSystem.setCurrentWorkingDirectory(memDir);
        createContent(memDir, fileSystem);
        ctx = Context.newBuilder().allowIO(true).fileSystem(fileSystem).build();
        cfgs.put(CUSTOM_FS_DEPRECATED, new Configuration(MEMORY_FILE_SYSTEM, ctx, memDir, fileSystem, false, true, true, true));
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        for (Map.Entry<String, Configuration> cfgEntry : cfgs.entrySet()) {
            cfgEntry.getValue().close();
        }
        cfgs.clear();
    }

    public FileSystemsTest(final String cfgName) {
        this.cfg = cfgs.get(cfgName);
        Assume.assumeNotNull(this.cfg);
    }

    @Before
    public void setUp() {
        resetLanguageHomes();
    }

    @Test
    public void testList() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestListLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestListLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING);
            try {
                String expected = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING).toString();
                final Collection<? extends TruffleFile> children = file.list();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                final Optional<String> expectedFile = children.stream().map(TruffleFile::getAbsoluteFile).map(TruffleFile::getPath).filter(expected::equals).findAny();
                Assert.assertTrue(formatErrorMessage("Expected child", configurationName, path), expectedFile.isPresent());
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testListNonNormalized() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestListNonNormalizedLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestListNonNormalizedLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, "lib/../.");
            try {
                String expected = resolve(env, usePublicFile, path, FOLDER_EXISTING, "lib/../.", FILE_EXISTING).toString();
                final Collection<? extends TruffleFile> children = file.list();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                final Optional<String> expectedFile = children.stream().map(TruffleFile::getAbsoluteFile).map(TruffleFile::getPath).filter(expected::equals).findAny();
                Assert.assertTrue(formatErrorMessage("Expected child", configurationName, path), expectedFile.isPresent());
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testReadUsingChannel() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestReadUsingChannelLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestReadUsingChannelLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                final String content = new String(file.readAllBytes(), StandardCharsets.UTF_8);
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertEquals(formatErrorMessage("Expected file content", configurationName, path), FILE_EXISTING_CONTENT, content);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testReadUsingStream() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestReadUsingStreamLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestReadUsingStreamLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
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
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertEquals(formatErrorMessage("Expected file content", configurationName, path), FILE_EXISTING_CONTENT, content.toString());
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testWriteUsingChannel() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestWriteUsingChannelLanguage.class, "", configuration, path, usePublicFile, canRead, canWrite);
    }

    @Registration
    public static final class TestWriteUsingChannelLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean canWrite = (boolean) contextArguments[4];
            TruffleFile file = resolve(env, usePublicFile, path, FILE_NEW_WRITE_CHANNEL);
            final String expectedContent = "0123456789";
            try {
                try (ByteChannel bc = file.newByteChannel(EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW));
                                OutputStream out = Channels.newOutputStream(bc)) {
                    out.write(expectedContent.getBytes(StandardCharsets.UTF_8));
                }
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                if (canRead) {
                    Assert.assertEquals(formatErrorMessage("Expected file content", configurationName, path), expectedContent, new String(file.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testWriteUsingStream() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestWriteUsingStreamLanguage.class, "", configuration, path, usePublicFile, canRead, canWrite);
    }

    @Registration
    public static final class TestWriteUsingStreamLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean canWrite = (boolean) contextArguments[4];
            TruffleFile file = resolve(env, usePublicFile, path, FILE_NEW_WRITE_STREAM);
            final String expectedContent = "0123456789";
            try {
                try (BufferedWriter out = file.newBufferedWriter(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    out.write(expectedContent, 0, expectedContent.length());
                }
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                if (canRead) {
                    Assert.assertEquals(formatErrorMessage("Expected file content", configurationName, path), expectedContent, new String(file.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testCreateDirectory() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCreateDirectoryLanguage.class, "", configuration, path, usePublicFile, canRead, canWrite);
    }

    @Registration
    public static final class TestCreateDirectoryLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean canWrite = (boolean) contextArguments[4];
            TruffleFile toCreate = resolve(env, usePublicFile, path, FILE_NEW_CREATE_DIR);
            try {
                toCreate.createDirectories();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                if (canRead) {
                    Assert.assertTrue(formatErrorMessage("Created dir exists", configurationName, path), toCreate.exists());
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testCreateFile() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCreateFileLanguage.class, "", configuration, path, usePublicFile, canRead, canWrite);
    }

    @Registration
    public static final class TestCreateFileLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean canWrite = (boolean) contextArguments[4];
            TruffleFile toCreate = resolve(env, usePublicFile, path, FILE_NEW_CREATE_FILE);
            try {
                toCreate.createFile();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                if (canRead) {
                    Assert.assertTrue(formatErrorMessage("Created file exists", configurationName, path), toCreate.exists());
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testDelete() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestDeleteLanguage.class, "", configuration, path, usePublicFile, canRead, canWrite);
    }

    @Registration
    public static final class TestDeleteLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean canWrite = (boolean) contextArguments[4];
            TruffleFile toDelete = resolve(env, usePublicFile, path, FILE_EXISTING_DELETE);
            try {
                toDelete.delete();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                if (canRead) {
                    Assert.assertFalse(formatErrorMessage("Deleted file does not exist", configurationName, path), toDelete.exists());
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testExists() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestExistsLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestExistsLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                boolean exists = file.exists();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertTrue(formatErrorMessage("File should exist", configurationName, path), exists);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            }
            return null;
        }
    }

    @Test
    public void testGetAbsoluteFile() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        String userDir = cfg.getUserDir().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean allowsUserDir = cfg.allowsAbsolutePath();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetAbsoluteFileLanguage.class, "", configuration, path, userDir, usePublicFile, allowsUserDir);
    }

    @Registration
    public static final class TestGetAbsoluteFileLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            String userDir = (String) contextArguments[2];
            boolean usePublicFile = (boolean) contextArguments[3];
            boolean allowsUserDir = (boolean) contextArguments[4];
            TruffleFile file = resolve(env, usePublicFile, FOLDER_EXISTING, FILE_EXISTING);
            try {
                TruffleFile absolute = file.getAbsoluteFile();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), allowsUserDir);
                Assert.assertEquals(absolute.getPath(), resolve(env, usePublicFile, userDir, FOLDER_EXISTING, FILE_EXISTING).getPath());
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), allowsUserDir);
            }
            return null;
        }
    }

    @Test
    public void testGetCanonicalFile() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetCanonicalFileLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestGetCanonicalFileLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                TruffleFile canonical = file.getCanonicalFile();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertNotNull(formatErrorMessage("Canonical file", configurationName, path), canonical);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testGetLastModified() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetLastModifiedLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestGetLastModifiedLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                FileTime lastModifiedTime = file.getLastModifiedTime();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertNotNull(formatErrorMessage("Has last modified", configurationName, path), lastModifiedTime);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testIsDirectory() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsDirectoryLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestIsDirectoryLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                boolean isDir = file.isDirectory();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertFalse(formatErrorMessage("Not directory", configurationName, path), isDir);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            }
            return null;
        }
    }

    @Test
    public void testIsRegularFile() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsRegularFileLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestIsRegularFileLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                boolean isFile = file.isRegularFile();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertTrue(formatErrorMessage("Is file", configurationName, path), isFile);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            }
            return null;
        }
    }

    @Test
    public void testIsReadable() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsReadableLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestIsReadableLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                boolean readable = file.isReadable();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertTrue(formatErrorMessage("Is readable", configurationName, path), readable);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            }
            return null;
        }
    }

    @Test
    public void testIsWritable() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsWritableLanguage.class, "", configuration, path, usePublicFile, canRead, canWrite);
    }

    @Registration
    public static final class TestIsWritableLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean canWrite = (boolean) contextArguments[4];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                boolean writable = file.isWritable();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertEquals(formatErrorMessage("Is writable", configurationName, path), canWrite, writable);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            }
            return null;
        }
    }

    @Test
    public void testIsExecutable() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsExecutableLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestIsExecutableLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                boolean executable = file.isExecutable();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                // On Windows all files have executable mode.
                if (!OSUtils.isWindows()) {
                    Assert.assertFalse(formatErrorMessage("Is executable", configurationName, path), executable);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            }
            return null;
        }
    }

    @Test
    public void testIsSymbolicLink() throws Throwable {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        Assume.assumeTrue("File System does not support optional symbolic links", supportsSymLinks());
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsSymbolicLinkLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestIsSymbolicLinkLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, SYMLINK_EXISTING);
            try {
                boolean symlink = file.isSymbolicLink();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertTrue(formatErrorMessage("Is symbolic link", configurationName, path), symlink);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            }
            return null;
        }
    }

    @Test
    public void testRename() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestRenameLanguage.class, "", configuration, path, usePublicFile, canRead, canWrite);
    }

    @Registration
    public static final class TestRenameLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean canWrite = (boolean) contextArguments[4];
            TruffleFile root = resolve(env, usePublicFile, path);
            TruffleFile file = resolve(env, usePublicFile, path, FILE_EXISTING_RENAME);
            TruffleFile target = resolve(env, usePublicFile, path, FILE_NEW_RENAME);
            try {
                file.move(target);
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                if (canRead) {
                    final Collection<? extends TruffleFile> children = root.list();
                    final boolean hasRenameSource = children.stream().map(TruffleFile::getName).anyMatch(FILE_EXISTING_RENAME::equals);
                    final boolean hasRenameTarget = children.stream().map(TruffleFile::getName).anyMatch(FILE_NEW_RENAME::equals);
                    Assert.assertFalse(formatErrorMessage("Renamed file should not exist", configurationName, path), hasRenameSource);
                    Assert.assertTrue(formatErrorMessage("Rename target file should exist", configurationName, path), hasRenameTarget);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testSize() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestSizeLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestSizeLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            try {
                long size = file.size();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertEquals(formatErrorMessage("File size", configurationName, path), FILE_EXISTING_CONTENT.getBytes(StandardCharsets.UTF_8).length, size);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testToUri() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean allowsUserDir = cfg.allowsAbsolutePath();
        String expectedURI = cfg.getUserDir().resolve(FOLDER_EXISTING).resolve(FILE_EXISTING).toUri().toString();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestToUriLanguage.class, "", configuration, path, usePublicFile, allowsUserDir, expectedURI);
    }

    @Registration
    public static final class TestToUriLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean allowsUserDir = (boolean) contextArguments[3];
            String expectedURI = (String) contextArguments[4];
            TruffleFile file = resolve(env, usePublicFile, FOLDER_EXISTING, FILE_EXISTING);
            try {
                URI uri = file.toUri();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), allowsUserDir);
                Assert.assertTrue(uri.isAbsolute());
                Assert.assertEquals(formatErrorMessage("URI", configurationName, path), URI.create(expectedURI), uri);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), allowsUserDir);
            }
            return null;
        }
    }

    @Test
    public void testToRelativeUri() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        String userDirURI = cfg.getUserDir().toUri().toString();
        boolean usePublicFile = cfg.usePublicFile;
        List<? extends Path> rootDirectories = getRootDirectories();
        if (rootDirectories.isEmpty()) {
            throw new IllegalStateException("No root directory.");
        }
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestToRelativeUriLanguage.class, "", configuration, path, usePublicFile, userDirURI, rootDirectories.get(0).toString());
    }

    @Registration
    public static final class TestToRelativeUriLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            URI userDirURI = createFolderURI((String) contextArguments[3]);
            String rootDir = (String) contextArguments[4];
            TruffleFile relativeFile = resolve(env, usePublicFile, FILE_EXISTING);
            URI uri = relativeFile.toRelativeUri();
            Assert.assertFalse(uri.isAbsolute());
            URI expectedUri = userDirURI.relativize(userDirURI.resolve(FILE_EXISTING));
            Assert.assertEquals(formatErrorMessage("Relative URI", configurationName, path), expectedUri, uri);
            final TruffleFile absoluteFile = resolve(env, usePublicFile, rootDir, FOLDER_EXISTING, FILE_EXISTING);
            uri = absoluteFile.toUri();
            Assert.assertTrue(uri.isAbsolute());
            Assert.assertEquals(formatErrorMessage("Absolute URI", configurationName, path), Paths.get("/").resolve(FOLDER_EXISTING).resolve(FILE_EXISTING).toUri(), uri);
            return null;
        }

        private static URI createFolderURI(String uri) {
            String useUri = uri.endsWith("/") ? uri : uri + '/';
            return URI.create(useUri);
        }
    }

    @Test
    public void testNormalize() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean allowsUserDir = cfg.allowsAbsolutePath();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestNormalizeLanguage.class, "", configuration, path, usePublicFile, allowsUserDir);
    }

    @Registration
    public static final class TestNormalizeLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean allowsUserDir = (boolean) contextArguments[3];

            TruffleFile fileNormalized = resolve(env, usePublicFile, FOLDER_EXISTING);
            Assert.assertEquals(fileNormalized, fileNormalized.normalize());
            Assert.assertSame(fileNormalized, fileNormalized.normalize());
            TruffleFile fileNonNormalized = resolve(env, usePublicFile, FOLDER_EXISTING + "/lib/../.");
            Assert.assertEquals(fileNormalized.resolve("lib/../.").getPath(), fileNonNormalized.getPath());
            Assert.assertEquals(fileNormalized.getPath(), fileNonNormalized.normalize().getPath());
            Assert.assertEquals(fileNormalized, fileNonNormalized.normalize());
            try {
                Assert.assertEquals(fileNormalized.getAbsoluteFile().resolve("lib/../.").getPath(), fileNonNormalized.getAbsoluteFile().getPath());
                Assert.assertEquals(fileNormalized.getAbsoluteFile().getPath(), fileNonNormalized.normalize().getAbsoluteFile().getPath());
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), allowsUserDir);
            }
            Assert.assertEquals(".", fileNonNormalized.getName());
            Assert.assertEquals("..", fileNonNormalized.getParent().getName());
            Assert.assertEquals("lib", fileNonNormalized.getParent().getParent().getName());
            return null;
        }
    }

    @Test
    public void testRelativize() {
        Context ctx = cfg.getContext();
        boolean usePublicFile = cfg.usePublicFile;
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestRelativizeLanguage.class, "", usePublicFile);
    }

    @Registration
    public static final class TestRelativizeLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            boolean usePublicFile = (boolean) contextArguments[0];
            TruffleFile parent = resolve(env, usePublicFile, "/test/parent");
            TruffleFile child = resolve(env, usePublicFile, "/test/parent/child");
            TruffleFile relative = parent.relativize(child);
            Assert.assertEquals("child", relative.getPath());
            Assert.assertEquals(child, parent.resolve(relative.getPath()));
            child = resolve(env, usePublicFile, "/test/parent/child/inner");
            relative = parent.relativize(child);
            Assert.assertEquals(String.join(env.getFileNameSeparator(), "child", "inner"), relative.getPath());
            Assert.assertEquals(child, parent.resolve(relative.getPath()));
            TruffleFile sibling = resolve(env, usePublicFile, "/test/sibling");
            relative = parent.relativize(sibling);
            Assert.assertEquals(String.join(env.getFileNameSeparator(), "..", "sibling"), relative.getPath());
            Assert.assertEquals(sibling.normalize(), parent.resolve(relative.getPath()).normalize());
            return null;
        }
    }

    @Test
    public void testResolve() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestResolveLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestResolveLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile parent = resolve(env, usePublicFile, FOLDER_EXISTING);
            TruffleFile child = parent.resolve(FILE_EXISTING);
            try {
                Assert.assertTrue(child.exists());
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            }
            TruffleFile childRelativeToParent = parent.relativize(child);
            Assert.assertEquals(FILE_EXISTING, childRelativeToParent.getPath());
            try {
                Assert.assertFalse(childRelativeToParent.exists());
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            }
            return null;
        }
    }

    @Test
    public void testStartsWith() {
        Context ctx = cfg.getContext();
        boolean usePublicFile = cfg.usePublicFile;
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestStartsWithLanguage.class, "", usePublicFile);
    }

    @Registration
    public static final class TestStartsWithLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            boolean usePublicFile = (boolean) contextArguments[0];
            TruffleFile testAbsolute = resolve(env, usePublicFile, "/test");
            TruffleFile testParentAbsolute = resolve(env, usePublicFile, "/test/parent");
            TruffleFile testSiblingAbsolute = resolve(env, usePublicFile, "/test/sibling");
            TruffleFile testParentSiblingAbsolute = resolve(env, usePublicFile, "/test/parent/sibling");
            TruffleFile teAbsolute = resolve(env, usePublicFile, "/te");
            TruffleFile testParentChildAbsolute = resolve(env, usePublicFile, "/test/parent/child");
            TruffleFile testRelative = resolve(env, usePublicFile, "test");
            TruffleFile testParentRelative = resolve(env, usePublicFile, "test/parent");
            TruffleFile testSiblingRelative = resolve(env, usePublicFile, "test/sibling");
            TruffleFile testParentSiblingRelative = resolve(env, usePublicFile, "test/parent/sibling");
            TruffleFile teRelative = resolve(env, usePublicFile, "te");
            TruffleFile testParentChildRelative = resolve(env, usePublicFile, "test/parent/child");
            Assert.assertTrue(testParentChildAbsolute.startsWith(testAbsolute));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testAbsolute.getPath()));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testParentAbsolute));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testParentAbsolute.getPath()));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testParentChildAbsolute));
            Assert.assertTrue(testParentChildAbsolute.startsWith(testParentChildAbsolute.getPath()));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testSiblingAbsolute));
            Assert.assertFalse(testParentChildAbsolute.startsWith(testSiblingAbsolute.getPath()));
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
            return null;
        }
    }

    @Test
    public void testEndsWith() {
        Context ctx = cfg.getContext();
        boolean usePublicFile = cfg.usePublicFile;
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestEndsWithLanguage.class, "", usePublicFile);
    }

    @Registration
    public static final class TestEndsWithLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            boolean usePublicFile = (boolean) contextArguments[0];
            TruffleFile testParentInnerAbsolute = resolve(env, usePublicFile, "/test/parent/inner");
            TruffleFile testParentInnerRelative = resolve(env, usePublicFile, "test/parent/inner");
            TruffleFile innerAbsolute = resolve(env, usePublicFile, "/inner");
            TruffleFile innerRelative = resolve(env, usePublicFile, "inner");
            TruffleFile parentInnerAbsolute = resolve(env, usePublicFile, "/parent/inner");
            TruffleFile parentInnerRelative = resolve(env, usePublicFile, "parent/inner");
            TruffleFile nnerRelative = resolve(env, usePublicFile, "nner");
            TruffleFile testParentSiblingAbsolute = resolve(env, usePublicFile, "/test/parent/sibling");
            TruffleFile testParentSiblingRelative = resolve(env, usePublicFile, "test/parent/sibling");
            TruffleFile testParentInnerChildAbsolute = resolve(env, usePublicFile, "/test/parent/inner/child");
            TruffleFile testParentInnerChildRelative = resolve(env, usePublicFile, "test/parent/inner/child");
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
            return null;
        }
    }

    @Test
    public void testNewDirectoryStream() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestNewDirectoryStreamLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestNewDirectoryStreamLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile folder = resolve(env, usePublicFile, FOLDER_EXISTING);
            Set<String> expected = Set.of(FILE_EXISTING, FOLDER_EXISTING_INNER1, FOLDER_EXISTING_INNER2);
            try (DirectoryStream<TruffleFile> stream = folder.newDirectoryStream()) {
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Set<String> result = new HashSet<>();
                for (TruffleFile child : stream) {
                    result.add(child.getName());
                }
                Assert.assertEquals(expected, result);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testVisit() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestVisitLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestVisitLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile root = resolve(env, usePublicFile, path);
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
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
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
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                visitor.assertConsumed();
                // TestVisitor cannot be used for SKIP_SIBLINGS due to random order of files on file
                // system
                FileVisitor<TruffleFile> fileVisitor = new FileVisitor<>() {

                    private boolean skipReturned;
                    private final Set<TruffleFile> importantFiles = Set.of(existingFolder.resolve(FOLDER_EXISTING_INNER1),
                                    existingFolder.resolve(FOLDER_EXISTING_INNER2), existingFile);

                    @Override
                    public FileVisitResult preVisitDirectory(TruffleFile dir, BasicFileAttributes attrs) {
                        return check(dir);
                    }

                    @Override
                    public FileVisitResult visitFile(TruffleFile file, BasicFileAttributes attrs) {
                        return check(file);
                    }

                    @Override
                    public FileVisitResult visitFileFailed(TruffleFile file, IOException exc) {
                        return FileVisitResult.TERMINATE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(TruffleFile dir, IOException exc) {
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
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testCreateLink() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCreateLinkLanguage.class, "", configuration, path, usePublicFile, canWrite);
    }

    @Registration
    public static final class TestCreateLinkLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canWrite = (boolean) contextArguments[3];
            TruffleFile target = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            TruffleFile link = resolve(env, usePublicFile, path, FILE_NEW_LINK);
            try {
                link.createLink(target);
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } catch (UnsupportedOperationException uoe) {
                // Links may not be supported on file system
            }
            return null;
        }
    }

    @Test
    public void testCreateSymbolicLink() {
        Assume.assumeFalse("Link creation requires a special privilege on Windows", OSUtils.isWindows());
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCreateSymbolicLinkLanguage.class, "", configuration, path, usePublicFile, canWrite);
    }

    @Registration
    public static final class TestCreateSymbolicLinkLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canWrite = (boolean) contextArguments[3];
            TruffleFile target = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            TruffleFile link = resolve(env, usePublicFile, path, FILE_NEW_SYMLINK);
            try {
                Assert.assertTrue(target.isAbsolute());
                link.createSymbolicLink(target);
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                Assert.assertTrue(link.isSymbolicLink());
                Assert.assertEquals(target.getCanonicalFile(), link.getCanonicalFile());
                Assert.assertTrue(link.readSymbolicLink().isAbsolute());
                Assert.assertEquals(target, link.readSymbolicLink());
                link.delete();
                target = resolve(env, usePublicFile, FOLDER_EXISTING, FILE_EXISTING);
                Assert.assertFalse(target.isAbsolute());
                link.createSymbolicLink(target);
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                Assert.assertTrue(link.isSymbolicLink());
                Assert.assertEquals(target.getCanonicalFile(), link.getCanonicalFile());
                Assert.assertFalse(link.readSymbolicLink().isAbsolute());
                Assert.assertEquals(target, link.readSymbolicLink());
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } catch (UnsupportedOperationException uoe) {
                // Symbolic links may not be supported on file system
            }
            return null;
        }
    }

    @Test
    public void testReadSymbolicLink() throws Throwable {
        Assume.assumeFalse("Link creation requires a special privilege on Windows", OSUtils.isWindows());
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        Assume.assumeTrue("File System does not support optional symbolic links", supportsSymLinks());
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestReadSymbolicLinkLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestReadSymbolicLinkLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile root = resolve(env, usePublicFile, path);
            try {
                TruffleFile link = root.resolve(SYMLINK_EXISTING);
                link.readSymbolicLink();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                AbstractPolyglotTest.assertFails(root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING)::readSymbolicLink, NotLinkException.class);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } catch (UnsupportedOperationException uoe) {
                // Symbolic links may not be supported on file system
            }
            return null;
        }
    }

    @Test
    public void testGetOwner() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetOwnerLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestGetOwnerLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING);
            try {
                UserPrincipal owner = file.getOwner();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertNotNull(owner);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } catch (UnsupportedOperationException uoe) {
                // Owner may not be supported on file system
            }
            return null;
        }
    }

    @Test
    public void testGetGroup() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetGroupLanguage.class, "", configuration, path, usePublicFile, canRead);
    }

    @Registration
    public static final class TestGetGroupLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING);
            try {
                GroupPrincipal group = file.getGroup();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                Assert.assertNotNull(group);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } catch (UnsupportedOperationException uoe) {
                // Owner may not be supported on file system
            }
            return null;
        }
    }

    @Test
    public void testCopy() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCopyLanguage.class, "", configuration, path, usePublicFile, canRead, canWrite);
    }

    @Registration
    public static final class TestCopyLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean canWrite = (boolean) contextArguments[4];
            TruffleFile root = resolve(env, usePublicFile, path);
            TruffleFile file = resolve(env, usePublicFile, path, FOLDER_EXISTING, FILE_EXISTING);
            TruffleFile target = resolve(env, usePublicFile, path, FILE_NEW_COPY);
            try {
                file.copy(target);
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                if (canRead) {
                    final Collection<? extends TruffleFile> children = root.list();
                    final boolean hasTarget = children.stream().map(TruffleFile::getName).anyMatch(FILE_NEW_COPY::equals);
                    Assert.assertTrue(formatErrorMessage("Copied target file should exist", configurationName, path), hasTarget);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }

            TruffleFile folder = resolve(env, usePublicFile, path, FOLDER_EXISTING);
            target = resolve(env, usePublicFile, path, FOLDER_NEW_COPY);
            try {
                folder.copy(target);
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                if (canRead) {
                    Collection<? extends TruffleFile> children = root.list();
                    final boolean hasTarget = children.stream().map(TruffleFile::getName).anyMatch(FOLDER_NEW_COPY::equals);
                    boolean hasChildren = !target.list().isEmpty();
                    Assert.assertTrue(formatErrorMessage("Copied target file should exist", configurationName, path), hasTarget);
                    Assert.assertFalse(formatErrorMessage("Copied target should not have children", configurationName, path), hasChildren);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testExceptions() {
        Context ctx = cfg.getContext();
        boolean usePublicFile = cfg.usePublicFile;
        boolean isInternal = cfg.isInternalFileSystem();
        boolean weakEncapsulation = TruffleTestAssumptions.isNoIsolateEncapsulation();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestExceptionsLanguage.class, "", usePublicFile, isInternal, weakEncapsulation);
    }

    @Registration
    public static final class TestExceptionsLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            boolean usePublicFile = (boolean) contextArguments[0];
            boolean isInternal = (boolean) contextArguments[1];
            boolean weakEncapsulation = (boolean) contextArguments[2];
            TruffleFile existing = resolve(env, usePublicFile, FOLDER_EXISTING);
            try {
                existing.resolve(null);
                Assert.fail("Should not reach here.");
            } catch (Exception e) {
                if (isInternal) {
                    Assert.assertFalse(env.isHostException(e));
                    Assert.assertTrue(e instanceof RuntimeException);
                } else {
                    Assert.assertTrue(env.isHostException(e));
                    if (weakEncapsulation) {
                        Assert.assertTrue(env.asHostException(e) instanceof NullPointerException);
                    }
                }
            }
            return null;
        }
    }

    @Test
    public void testSetCurrentWorkingDirectory() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean allowsUserDir = cfg.allowsUserDir();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestSetCurrentWorkingDirectoryLanguage.class, "", configuration, path, usePublicFile, canRead, allowsUserDir);
    }

    @Registration
    public static final class TestSetCurrentWorkingDirectoryLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean allowsUserDir = (boolean) contextArguments[4];
            try {
                TruffleFile oldCwd = env.getCurrentWorkingDirectory();
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), allowsUserDir);
                Assert.assertNotNull(oldCwd);
                Assert.assertTrue(oldCwd.isAbsolute());
                TruffleFile relative = resolve(env, usePublicFile, FILE_EXISTING);
                Assert.assertNotNull(relative);
                Assert.assertFalse(relative.isAbsolute());
                Assert.assertEquals(oldCwd.resolve(FILE_EXISTING), relative.getAbsoluteFile());
                try {
                    Assert.assertFalse(relative.exists());
                    Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                } catch (SecurityException se) {
                    Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
                }
                TruffleFile newCwd = resolve(env, usePublicFile, path, FOLDER_EXISTING).getAbsoluteFile();
                try {
                    env.setCurrentWorkingDirectory(newCwd);
                    try {
                        Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                        Assert.assertEquals(newCwd, env.getCurrentWorkingDirectory());
                        Assert.assertEquals(newCwd.resolve(FILE_EXISTING), relative.getAbsoluteFile());
                        try {
                            Assert.assertTrue(relative.exists());
                            Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                        } catch (SecurityException se) {
                            Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
                        }
                    } finally {
                        env.setCurrentWorkingDirectory(oldCwd);
                    }
                } catch (SecurityException se) {
                    Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
                }
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), allowsUserDir);
            }
            return null;
        }
    }

    @Test
    public void testGetFileNameSeparator() {
        Context ctx = cfg.getContext();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetFileNameSeparatorLanguage.class, "", cfg.fileSystem.getSeparator());
    }

    @Registration
    public static final class TestGetFileNameSeparatorLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String expectedSeparator = (String) contextArguments[0];
            Assert.assertEquals(expectedSeparator, env.getFileNameSeparator());
            return null;
        }
    }

    @Test
    public void testGetPathSeparator() {
        Context ctx = cfg.getContext();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetPathSeparatorLanguage.class, "", cfg.fileSystem.getPathSeparator());
    }

    @Registration
    public static final class TestGetPathSeparatorLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String expectedSeparator = (String) contextArguments[0];
            Assert.assertEquals(expectedSeparator, env.getPathSeparator());
            return null;
        }
    }

    @Test
    public void testGetAttribute() throws IOException {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetAttributeLanguage.class, "", configuration, path, usePublicFile, canRead, supportsUnixAttributes());
    }

    @Registration
    public static final class TestGetAttributeLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean supportsUnixAttributes = (boolean) contextArguments[4];
            TruffleFile root = resolve(env, usePublicFile, path);
            try {
                TruffleFile file = root.resolve(FOLDER_EXISTING).resolve(FILE_EXISTING);
                Assert.assertEquals(file.getLastModifiedTime(), file.getAttribute(TruffleFile.LAST_MODIFIED_TIME));
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
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
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } catch (UnsupportedOperationException e) {
                if (supportsUnixAttributes) {
                    throw e;
                }
            }
            return null;
        }
    }

    static boolean isMacOSOlderThanHighSierra() {
        return OSUtils.getCurrent() == OSUtils.OS.Darwin && OSX_10_13.compareTo(Version.parse(System.getProperty("os.version"))) > 0;
    }

    @Test
    public void testSetAttribute() throws IOException {
        Assume.assumeFalse("JDK-8308386", Runtime.version().feature() == 21 && isMacOSOlderThanHighSierra());
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestSetAttributeLanguage.class, "", configuration, path, usePublicFile, canRead, canWrite, supportsUnixAttributes(),
                        supportsSetLastAccessTime(), supportsSetCreationTime());
    }

    @Registration
    public static final class TestSetAttributeLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean canWrite = (boolean) contextArguments[4];
            boolean supportsUnixAttributes = (boolean) contextArguments[5];
            boolean supportsSetLastAccessTime = (boolean) contextArguments[6];
            boolean supportsSetCreationTime = (boolean) contextArguments[7];
            TruffleFile root = resolve(env, usePublicFile, path);
            try {
                TruffleFile file = root.resolve(FILE_CHANGE_ATTRS);
                FileTime time = FileTime.from(Instant.now().minusSeconds(1_000).truncatedTo(ChronoUnit.MINUTES));
                file.setAttribute(TruffleFile.LAST_MODIFIED_TIME, time);
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canWrite);
                Assert.assertEquals(time, file.getAttribute(TruffleFile.LAST_MODIFIED_TIME));
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
                file.setAttribute(TruffleFile.LAST_ACCESS_TIME, time);
                FileTime lastAccessTime = file.getAttribute(TruffleFile.LAST_ACCESS_TIME);
                // Workaround for issue JDK-8298187: The file last access time does not work on
                // JDK-20 on macOS with the hfs file system.
                if (supportsSetLastAccessTime) {
                    Assert.assertEquals(time, lastAccessTime);
                }
                file.setAttribute(TruffleFile.CREATION_TIME, time);
                FileTime creationTime = file.getAttribute(TruffleFile.CREATION_TIME);
                if (supportsSetCreationTime) {
                    Assert.assertEquals(time, creationTime);
                }
                file.setAttribute(TruffleFile.UNIX_PERMISSIONS, EnumSet.of(PosixFilePermission.OWNER_READ));
                Assert.assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ), file.getAttribute(TruffleFile.UNIX_PERMISSIONS));
                file.setAttribute(TruffleFile.UNIX_PERMISSIONS, EnumSet.of(PosixFilePermission.OWNER_READ));
                Assert.assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ), file.getAttribute(TruffleFile.UNIX_PERMISSIONS));
                file.setAttribute(TruffleFile.UNIX_MODE, (file.getAttribute(TruffleFile.UNIX_MODE) & ~0700) | 0200);
                Assert.assertEquals(0200, file.getAttribute(TruffleFile.UNIX_MODE) & 0777);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } catch (UnsupportedOperationException e) {
                if (supportsUnixAttributes) {
                    throw e;
                }
            }
            return null;
        }
    }

    /**
     * Setting file last access time does not work on JDK-20 on macOS with the hfs file system,
     * issue JDK-8298187.
     */
    private boolean supportsSetLastAccessTime() throws IOException {
        if (Runtime.version().feature() == 20 && OSUtils.getCurrent() == OSUtils.OS.Darwin) {
            Path unwrappedPath = Paths.get(cfg.path.toString());
            return !Files.exists(unwrappedPath) || !"hfs".equals(Files.getFileStore(unwrappedPath).type());
        }
        return true;
    }

    /**
     * Returns {@code true} if the operating system supports file creation time modification. Note:
     * Posix does not support setting a file's creation time directly. MacOS and BSD Unix, however,
     * provide an additional system call {@code fsetattrlist} utilized by Java NIO to set the
     * creation time. On Linux, the Posix functions {@code utimes}, {@code futimens}, or
     * {@code utimensat} are employed, allowing modification only of access and modification times.
     */
    private static boolean supportsSetCreationTime() {
        return OSUtils.isWindows() || OSUtils.getCurrent() == OSUtils.OS.Darwin;
    }

    @Test
    public void testGetAttributes() throws IOException {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canRead = cfg.canRead();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetAttributesLanguage.class, "", configuration, path, usePublicFile, canRead, supportsUnixAttributes());
    }

    @Registration
    public static final class TestGetAttributesLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canRead = (boolean) contextArguments[3];
            boolean supportsUnixAttributes = (boolean) contextArguments[4];
            TruffleFile root = resolve(env, usePublicFile, path);
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
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
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
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
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
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
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
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } catch (UnsupportedOperationException e) {
                if (supportsUnixAttributes) {
                    throw e;
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
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canRead);
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
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canRead);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } catch (UnsupportedOperationException e) {
                if (supportsUnixAttributes) {
                    throw e;
                }
            }
            return null;
        }
    }

    @Test
    public void testCreateTempFile() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCreateTempFileLanguage.class, "", configuration, path);
    }

    @Registration
    public static final class TestCreateTempFileLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
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
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testCreateTempFileInFolder() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCreateTempFileInFolderLanguage.class, "", configuration, path, usePublicFile, canWrite);
    }

    @Registration
    public static final class TestCreateTempFileInFolderLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canWrite = (boolean) contextArguments[3];
            TruffleFile root = resolve(env, usePublicFile, path);
            try {
                TruffleFile tmpDir = root.resolve(FILE_TMP_DIR);
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
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testCreateTempDirectory() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCreateTempDirectoryLanguage.class, "", configuration, path);
    }

    @Registration
    public static final class TestCreateTempDirectoryLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
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
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testCreateTempDirectoryInFolder() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canWrite = cfg.canWrite();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCreateTempDirectoryInFolderLanguage.class, "", configuration, path, usePublicFile, canWrite);
    }

    @Registration
    public static final class TestCreateTempDirectoryInFolderLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canWrite = (boolean) contextArguments[3];
            TruffleFile root = resolve(env, usePublicFile, path);
            try {
                TruffleFile tmpDir = root.resolve(FILE_TMP_DIR);
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
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canWrite);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Test
    public void testVisitRelativeFolderAfterSetCurrentWorkingDirectory() {
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        boolean usePublicFile = cfg.usePublicFile;
        Assume.assumeTrue(cfg.canRead() && cfg.allowsUserDir());
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, VisitRelativeFolderAfterSetCurrentWorkingDirectory.class, "", configuration, path, usePublicFile);
    }

    @Registration
    public static final class VisitRelativeFolderAfterSetCurrentWorkingDirectory extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            final TruffleFile folder = resolve(env, usePublicFile, path, FOLDER_EXISTING);
            TruffleFile cwd = env.getCurrentWorkingDirectory();
            try {
                env.setCurrentWorkingDirectory(folder);
                TruffleFile relativeFolder = env.getInternalTruffleFile(FOLDER_EXISTING_INNER1);
                Assert.assertFalse(relativeFolder.isAbsolute());
                Assert.assertTrue(relativeFolder.isDirectory());
                relativeFolder.visit(new FileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(TruffleFile t, BasicFileAttributes bfa) {
                        Assert.assertFalse(t.isAbsolute());
                        relativeFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(TruffleFile t, BasicFileAttributes bfa) {
                        Assert.assertFalse(t.isAbsolute());
                        relativeFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(TruffleFile t, IOException ioe) {
                        return FileVisitResult.TERMINATE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(TruffleFile t, IOException ioe) {
                        Assert.assertFalse(t.isAbsolute());
                        relativeFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }
                }, Integer.MAX_VALUE);

                TruffleFile absoluteFolder = folder.resolve(FOLDER_EXISTING_INNER1);
                Assert.assertTrue(absoluteFolder.isAbsolute());
                Assert.assertTrue(absoluteFolder.isDirectory());
                absoluteFolder.visit(new FileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(TruffleFile t, BasicFileAttributes bfa) {
                        Assert.assertTrue(t.isAbsolute());
                        absoluteFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(TruffleFile t, BasicFileAttributes bfa) {
                        Assert.assertTrue(t.isAbsolute());
                        absoluteFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(TruffleFile t, IOException ioe) {
                        return FileVisitResult.TERMINATE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(TruffleFile t, IOException ioe) {
                        Assert.assertTrue(t.isAbsolute());
                        absoluteFolder.relativize(t);
                        return FileVisitResult.CONTINUE;
                    }
                }, Integer.MAX_VALUE);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            } finally {
                env.setCurrentWorkingDirectory(cwd);
            }
            return null;
        }
    }

    @Test
    public void testIsSameFile() throws Throwable {
        // not sure what is wrong with this test with class loader encapsulation
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation();
        Context ctx = cfg.getContext();
        String configuration = cfg.getName();
        String path = cfg.getPath().toString();
        String userDir = cfg.getUserDir().toString();
        boolean usePublicFile = cfg.usePublicFile;
        boolean canResolveAbsolutePath = cfg.allowsAbsolutePath();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsSameFileLanguage.class, "", configuration, path, userDir, usePublicFile, canResolveAbsolutePath);
        Assume.assumeTrue("File System does not support optional symbolic links", supportsSymLinks());
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsSameFileSymLinkLanguage.class, "", configuration, path, usePublicFile, canResolveAbsolutePath);
    }

    @Registration
    public static final class TestIsSameFileLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            String userDir = (String) contextArguments[2];
            boolean usePublicFile = (boolean) contextArguments[3];
            boolean canResolveAbsolutePath = (boolean) contextArguments[4];
            TruffleFile root = resolve(env, usePublicFile, path);
            TruffleFile wd = root.resolve(FOLDER_EXISTING).resolve(FOLDER_EXISTING_INNER1);
            TruffleFile file1 = wd.resolve(FILE_EXISTING);
            TruffleFile file1Relative = resolve(env, usePublicFile, userDir).relativize(file1);
            TruffleFile file2 = wd.resolve(FILE_EXISTING2);
            try {
                Assert.assertTrue(file1.isSameFile(file1));
                Assert.assertTrue(file1.isSameFile(file1, LinkOption.NOFOLLOW_LINKS));
                Assert.assertFalse(file1.isSameFile(file2));
                Assert.assertFalse(file1.isSameFile(file2, LinkOption.NOFOLLOW_LINKS));
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canResolveAbsolutePath);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canResolveAbsolutePath);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            try {
                Assert.assertTrue(file1.isSameFile(file1Relative));
                Assert.assertTrue(file1.isSameFile(file1Relative, LinkOption.NOFOLLOW_LINKS));
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canResolveAbsolutePath);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canResolveAbsolutePath);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    @Registration
    public static final class TestIsSameFileSymLinkLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String configurationName = (String) contextArguments[0];
            String path = (String) contextArguments[1];
            boolean usePublicFile = (boolean) contextArguments[2];
            boolean canResolveAbsolutePath = (boolean) contextArguments[3];
            TruffleFile root = resolve(env, usePublicFile, path);
            TruffleFile link = root.resolve(SYMLINK_EXISTING);
            TruffleFile linkTarget = root.resolve(FOLDER_EXISTING);
            try {
                Assert.assertTrue(link.isSameFile(link));
                Assert.assertTrue(link.isSameFile(link, LinkOption.NOFOLLOW_LINKS));
                Assert.assertTrue(link.isSameFile(linkTarget));
                Assert.assertFalse(link.isSameFile(linkTarget, LinkOption.NOFOLLOW_LINKS));
                Assert.assertTrue(formatErrorMessage("Expected SecurityException", configurationName, path), canResolveAbsolutePath);
            } catch (SecurityException se) {
                Assert.assertFalse(formatErrorMessage("Unexpected SecurityException", configurationName, path), canResolveAbsolutePath);
            } catch (IOException ioe) {
                throw new AssertionError(formatErrorMessage(ioe.getMessage(), configurationName, path), ioe);
            }
            return null;
        }
    }

    static TruffleFile resolve(Env env, boolean usePublicFile, String path, String... paths) {
        TruffleFile file;
        if (usePublicFile) {
            file = env.getPublicTruffleFile(path);
        } else {
            file = env.getInternalTruffleFile(path);
        }
        for (String relative : paths) {
            file = file.resolve(relative);
        }
        return file;
    }

    static String formatErrorMessage(String message, String configurationName, String root) {
        return String.format("%s, configuration: %s, root: %s", message, configurationName, root);
    }

    private boolean supportsSymLinks() throws IOException {
        if (cfg.canRead()) {
            try {
                cfg.fileSystem.checkAccess(cfg.getPath().resolve(SYMLINK_EXISTING), Collections.emptySet());
                return true;
            } catch (NoSuchFileException doesNotExist) {
                // continue and return false
            }
        }
        return false;
    }

    private boolean supportsUnixAttributes() throws IOException {
        if (cfg.canRead()) {
            try {
                cfg.fileSystem.readAttributes(cfg.getPath().resolve(FOLDER_EXISTING), "unix:*");
                return true;
            } catch (UnsupportedOperationException unsupported) {
                // continue and return false
            }
        }
        return false;
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
        private final boolean internalFileSystem;
        private final boolean readableFileSystem;
        private final boolean writableFileSystem;
        private final boolean allowsUserDir;
        private final boolean allowsAbsolutePath;
        private final boolean usePublicFile;
        private final Engine engine;

        Configuration(
                        final String name,
                        final Context context,
                        final Path path,
                        final FileSystem fileSystem,
                        final boolean internalFileSystem,
                        final boolean readableFileSystem,
                        final boolean writableFileSystem,
                        final boolean allowsUserDir) {
            this(name, context, path, path, fileSystem, internalFileSystem, readableFileSystem, writableFileSystem, allowsUserDir);
        }

        Configuration(
                        final String name,
                        final Context context,
                        final Path path,
                        final Path userDir,
                        final FileSystem fileSystem,
                        final boolean internalFileSystem,
                        final boolean readableFileSystem,
                        final boolean writableFileSystem,
                        final boolean allowsUserDir) {
            this(name, context, path, userDir, fileSystem, internalFileSystem, readableFileSystem, writableFileSystem, allowsUserDir, allowsUserDir, true, null);
        }

        Configuration(String name, Context context, Path path, Path userDir, FileSystem fileSystem, boolean internalFileSystem, boolean readableFileSystem,
                        boolean writableFileSystem, boolean allowsUserDir, boolean allowsAbsolutePath, boolean usePublicFile, Engine engine) {
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
            this.internalFileSystem = internalFileSystem;
            this.readableFileSystem = readableFileSystem;
            this.writableFileSystem = writableFileSystem;
            this.allowsUserDir = allowsUserDir;
            this.allowsAbsolutePath = allowsAbsolutePath;
            this.usePublicFile = usePublicFile;
            this.engine = engine;
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
            return readableFileSystem;
        }

        /**
         * Returns true if the test configuration allows write operations.
         *
         * @return {@code true} if writing is enabled
         */
        boolean canWrite() {
            return writableFileSystem;
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

        boolean isInternalFileSystem() {
            return internalFileSystem;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public void close() throws IOException {
            try {
                ctx.close();
                if (engine != null) {
                    engine.close();
                }
            } finally {
                deleteRecursively(path, fileSystem);
            }
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
                        EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)).close();
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
            ReflectionUtils.setAccessible(reset, true);
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
     */
    private static void setCwd(Context ctx, Path cwd) {
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, SetCurrentWorkingDirectoryLanguage.class, "", cwd.toString());
    }

    @Registration
    public static final class SetCurrentWorkingDirectoryLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String currentWorkingDirectory = (String) contextArguments[0];
            env.setCurrentWorkingDirectory(env.getInternalTruffleFile(currentWorkingDirectory));
            return null;
        }
    }

    private static void markAsLanguageHome(Engine engine, Path languageHome) {
        try (Context ctx = Context.newBuilder().engine(engine).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, MarkAsLanguageHomeLanguage.class, "", languageHome.toString());
        }
    }

    @Registration
    public static final class MarkAsLanguageHomeLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String langHome = (String) contextArguments[0];
            String languageId = TestUtils.getDefaultLanguageId(getClass());
            System.setProperty("org.graalvm.language." + languageId + ".home", langHome);
            resetLanguageHomes();
            return null;
        }
    }

    private static FileSystem createPreInitializeContextFileSystem() throws ReflectiveOperationException {
        Class<? extends FileSystem> clazz = Class.forName("com.oracle.truffle.polyglot.FileSystems$PreInitializeContextFileSystem").asSubclass(FileSystem.class);
        Constructor<? extends FileSystem> init = clazz.getDeclaredConstructor(String.class);
        ReflectionUtils.setAccessible(init, true);
        return init.newInstance(System.getProperty("java.io.tmpdir"));
    }

    private static void switchToImageExecutionTime(FileSystem fileSystem, Path cwd) throws ReflectiveOperationException {
        String workDir = cwd.toString();
        Class<?> internalResourceRootsClass = Class.forName("com.oracle.truffle.polyglot.InternalResourceRoots");
        Object roots = ReflectionUtils.invokeStatic(internalResourceRootsClass, "getInstance");
        ReflectionUtils.invoke(fileSystem, "onPreInitializeContextEnd", new Class<?>[]{internalResourceRootsClass, Map.class}, roots, Map.of());
        ReflectionUtils.invoke(fileSystem, "onLoadPreinitializedContext", new Class<?>[]{FileSystem.class}, newFullIOFileSystem(Paths.get(workDir)));
    }

    static FileSystem newFullIOFileSystem(final Path currentWorkingDirectory) {
        FileSystem res = FileSystem.newDefaultFileSystem();
        res.setCurrentWorkingDirectory(currentWorkingDirectory);
        return res;
    }

    static class ForwardingFileSystem implements FileSystem {

        private final FileSystem delegate;

        protected ForwardingFileSystem(final FileSystem fileSystem) {
            Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            this.delegate = fileSystem;
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

        private void checkRead(Path path) {
            if (!readPredicate.test(path)) {
                throw new SecurityException("Read operation is not allowed for: " + path);
            }
        }

        private void checkWrite(Path path) {
            if (!writePredicate.test(path)) {
                throw new SecurityException("Write operation is not allowed for: " + path);
            }
        }

        private void checkDelete(Path path) {
            if (!writePredicate.test(path)) {
                throw new SecurityException("Delete operation is not allowed for: " + path);
            }
        }

        private void checkReadLink(Path path) {
            if (!readPredicate.test(path)) {
                throw new SecurityException("Read link operation is not allowed for: " + path);
            }
        }

        private void checkChannelOpenOptions(
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
        public FileVisitResult preVisitDirectory(TruffleFile dir, BasicFileAttributes attrs) {
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
        public FileVisitResult visitFile(TruffleFile file, BasicFileAttributes attrs) {
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
        public FileVisitResult visitFileFailed(TruffleFile file, IOException exc) {
            return FileVisitResult.TERMINATE;
        }

        @Override
        public FileVisitResult postVisitDirectory(TruffleFile dir, IOException exc) {
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
