/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.test.OSUtils;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;

public class InternalResourceTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        // GR-47044: Bundling of internal resources on the polyglot isolate is not yet implemented.
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @InternalResource.Id(LibraryResource.ID)
    static class LibraryResource implements InternalResource {

        static final String ID = "library";

        static final String[] RESOURCES = {"library"};

        static int unpackedCalled;

        @Override
        public void unpackFiles(Path targetDirectory, Env env) throws IOException {
            unpackedCalled++;
            for (String resource : RESOURCES) {
                Files.createFile(targetDirectory.resolve(resource));
            }
        }

        @Override
        public String versionHash(Env env) {
            return "1";
        }
    }

    @InternalResource.Id(SourcesResource.ID)
    static class SourcesResource implements InternalResource {

        static final String ID = "sources";

        static final String[] RESOURCES = {"source_1", "source_2", "source_3"};

        static int unpackedCalled;

        @Override
        public void unpackFiles(Path targetDirectory, Env env) throws IOException {
            unpackedCalled++;
            for (String resource : RESOURCES) {
                Files.createFile(targetDirectory.resolve(resource));
            }
        }

        @Override
        public String versionHash(Env env) {
            return "1";
        }
    }

    @InternalResource.Id("resources")
    static class FileAccessCheckResource implements InternalResource {

        static String fileName;
        static String folderName;
        static String linkTargetName;
        static String linkName;

        @Override
        public void unpackFiles(Path targetDirectory, Env env) throws IOException {
            Path sources = Files.createDirectory(targetDirectory.resolve("sources"));
            folderName = targetDirectory.relativize(sources).toString();
            Path source = Files.writeString(sources.resolve("source"), "source");
            fileName = targetDirectory.relativize(source).toString();
            Path library = Files.createFile(targetDirectory.resolve("library"));
            linkTargetName = targetDirectory.relativize(library).toString();
            try {
                Path libraryLink = Files.createSymbolicLink(targetDirectory.resolve("library-amd64"), library.getFileName());
                linkName = targetDirectory.relativize(libraryLink).toString();
            } catch (UnsupportedOperationException | IOException e) {
                // pass OS does not support symbolic links
            }
        }

        @Override
        public String versionHash(Env env) {
            return "1";
        }
    }

    private static void verifyResources(TruffleFile root, String[] resources) {
        assertTrue(root.exists());
        assertTrue(root.isDirectory());
        assertTrue(root.isReadable());
        assertFalse(root.isWritable());
        for (String resource : resources) {
            TruffleFile file = root.resolve(resource);
            assertTrue(file.exists());
            assertTrue(file.isReadable());
            assertFalse(file.isWritable());
        }
    }

    @Registration(/* ... */internalResources = {LibraryResource.class, SourcesResource.class})
    public static class TestLanguageResourcesUnpackedOnce extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        @SuppressWarnings("try")
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try (TemporaryResourceCacheRoot cache = new TemporaryResourceCacheRoot()) {
                LibraryResource.unpackedCalled = 0;
                SourcesResource.unpackedCalled = 0;
                TruffleFile libRoot1 = env.getInternalResource(LibraryResource.class);
                verifyResources(libRoot1, LibraryResource.RESOURCES);
                TruffleFile srcRoot1 = env.getInternalResource(SourcesResource.class);
                verifyResources(srcRoot1, SourcesResource.RESOURCES);
                TruffleFile libRoot2 = env.getInternalResource(LibraryResource.class);
                assertEquals(libRoot1.getAbsoluteFile(), libRoot2.getAbsoluteFile());
                TruffleFile srcRoot2 = env.getInternalResource(SourcesResource.class);
                assertEquals(srcRoot1.getAbsoluteFile(), srcRoot2.getAbsoluteFile());
                assertEquals(1, LibraryResource.unpackedCalled);
                assertEquals(1, SourcesResource.unpackedCalled);
                return "";
            }
        }
    }

    @Test
    public void testLanguageResourcesUnpackedOnce() {
        Assume.assumeFalse("Cannot run as native unittest", ImageInfo.inImageRuntimeCode());
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.execute(context, TestLanguageResourcesUnpackedOnce.class);
        }
    }

    @TruffleInstrument.Registration(id = InstrumentWithResources.ID, name = InstrumentWithResources.ID, //
                    services = InstrumentWithResources.Create.class, internalResources = {LibraryResource.class, SourcesResource.class})
    public static final class InstrumentWithResources extends TruffleInstrument {
        static final String ID = "InstrumentWithResources";

        public interface Create {
        }

        @Override
        @SuppressWarnings("try")
        protected void onCreate(Env env) {
            env.registerService(new Create() {
            });
            try (TemporaryResourceCacheRoot cache = new TemporaryResourceCacheRoot()) {
                LibraryResource.unpackedCalled = 0;
                SourcesResource.unpackedCalled = 0;
                TruffleFile libRoot1 = env.getInternalResource(LibraryResource.class);
                verifyResources(libRoot1, LibraryResource.RESOURCES);
                TruffleFile srcRoot1 = env.getInternalResource(SourcesResource.class);
                verifyResources(srcRoot1, SourcesResource.RESOURCES);
                TruffleFile libRoot2 = env.getInternalResource(LibraryResource.class);
                assertEquals(libRoot1.getAbsoluteFile(), libRoot2.getAbsoluteFile());
                TruffleFile srcRoot2 = env.getInternalResource(SourcesResource.class);
                assertEquals(srcRoot1.getAbsoluteFile(), srcRoot2.getAbsoluteFile());
                assertEquals(1, LibraryResource.unpackedCalled);
                assertEquals(1, SourcesResource.unpackedCalled);
            } catch (IOException ioe) {
                throw CompilerDirectives.shouldNotReachHere(ioe);
            }
        }
    }

    @Registration(/* ... */)
    public static class TestInstrumentResourcesUnpackedOnce extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            InstrumentInfo info = env.getInstruments().get(InstrumentWithResources.ID);
            assertNotNull(info);
            env.lookup(info, InstrumentWithResources.Create.class);
            return null;
        }
    }

    @Test
    public void testInstrumentResourcesUnpackedOnce() {
        Assume.assumeFalse("Cannot run as native unittest", ImageInfo.inImageRuntimeCode());
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.execute(context, TestInstrumentResourcesUnpackedOnce.class);
        }
    }

    @Registration(/* ... */internalResources = {LibraryResource.class, SourcesResource.class})
    public static class TestAccessFileOutsideOfResourceRoot extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        @SuppressWarnings("try")
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile hostFolder = env.createTempDirectory(null, getClass().getSimpleName());
            try (TemporaryResourceCacheRoot cache = new TemporaryResourceCacheRoot()) {
                // Relative paths
                TruffleFile lib = env.getInternalResource(LibraryResource.class);
                assertNull(lib.getParent());
                assertNoFileAccess(lib.resolve(".."), hostFolder);
                // Absolute paths
                TruffleFile absoluteLibParent = lib.getAbsoluteFile().getParent();
                assertNotNull(absoluteLibParent);
                assertNoFileAccess(absoluteLibParent, hostFolder);
                // Combine absolute paths with relative paths to escape from internal resource root
                absoluteLibParent = lib.getAbsoluteFile().resolve("..");
                assertNoFileAccess(absoluteLibParent, hostFolder);
                absoluteLibParent = lib.getAbsoluteFile().resolve("prefix").resolve("..").resolve("..");
                assertNoFileAccess(absoluteLibParent, hostFolder);
                // Try to access other resource files
                TruffleFile src = env.getInternalResource(SourcesResource.class);
                TruffleFile srcResolvedUsingLib = lib.resolve(src.getAbsoluteFile().toString());
                assertNoFileAccess(srcResolvedUsingLib, hostFolder);
                return null;
            } finally {
                delete(hostFolder);
            }
        }

        private static void assertNoFileAccess(TruffleFile file, TruffleFile hostFolder) {
            assertSecurityException(() -> file.resolve("fooDir").createDirectory());
            assertSecurityException(() -> file.resolve("fooDir").createDirectories());
            assertSecurityException(() -> file.resolve("fooFile").createFile());
            assertSecurityException(file::exists);
            assertSecurityException(file::isDirectory);
            assertSecurityException(file::isRegularFile);
            assertSecurityException(file::isSymbolicLink);
            assertSecurityException(file::isReadable);
            assertSecurityException(file::isExecutable);
            assertSecurityException(file::size);
            assertFalse(file.isWritable());
            assertSecurityException(() -> file.isSameFile(file.resolveSibling("other")));
            assertSecurityException(() -> file.getAttribute(TruffleFile.CREATION_TIME));
            assertSecurityException(() -> file.getAttributes(List.of(TruffleFile.CREATION_TIME)));
            assertSecurityException(file::getCreationTime);
            assertSecurityException(file::getLastAccessTime);
            assertSecurityException(file::getLastModifiedTime);
            assertSecurityException(() -> file.setAttribute(TruffleFile.CREATION_TIME, FileTime.from(Instant.now())));
            assertSecurityException(() -> file.setCreationTime(FileTime.from(Instant.now())));
            assertSecurityException(() -> file.setLastAccessTime(FileTime.from(Instant.now())));
            assertSecurityException(() -> file.setLastModifiedTime(FileTime.from(Instant.now())));
            assertSecurityException(file::list);
            assertSecurityException(() -> file.visit(new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(TruffleFile dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(TruffleFile f, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(TruffleFile f, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(TruffleFile dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            }, 1));
            assertSecurityException(file::newBufferedReader);
            assertSecurityException(file::newBufferedWriter);
            assertSecurityException(file::newInputStream);
            assertSecurityException(file::newOutputStream);
            assertSecurityException(() -> file.newByteChannel(Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE)));
            assertSecurityException(() -> file.newByteChannel(Set.of(StandardOpenOption.READ)));
            assertSecurityException(file::readAllBytes);
            assertSecurityException(file::newDirectoryStream);
            assertSecurityException(file::delete);
            assertSecurityException(() -> file.copy(hostFolder.resolve("cp")));
            assertSecurityException(() -> file.move(hostFolder.resolve("mv")));
            if (OSUtils.isUnix()) {
                assertSecurityException(file::getOwner);
                assertSecurityException(file::getGroup);
                assertSecurityException(file::getPosixPermissions);
                assertSecurityException(() -> file.setPosixPermissions(Set.of()));
                assertSecurityException(() -> file.createLink(file.resolveSibling("ln")));
                assertSecurityException(() -> file.createSymbolicLink(file.resolveSibling("lns")));
                assertSecurityException(file::readSymbolicLink);
            }
        }
    }

    @Test
    public void testAccessFileOutsideOfResourceRoot() {
        Assume.assumeFalse("Cannot run as native unittest", ImageInfo.inImageRuntimeCode());
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.execute(context, TestAccessFileOutsideOfResourceRoot.class);
        }
    }

    @Registration(/* ... */internalResources = {FileAccessCheckResource.class})
    public static class TestAccessFileInResourceRoot extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        @SuppressWarnings("try")
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile hostFolder = env.createTempDirectory(null, getClass().getSimpleName());
            try (TemporaryResourceCacheRoot cache = new TemporaryResourceCacheRoot()) {
                TruffleFile root = env.getInternalResource(FileAccessCheckResource.class);
                TruffleFile file = root.resolve(FileAccessCheckResource.fileName);
                TruffleFile folder = root.resolve(FileAccessCheckResource.folderName);
                TruffleFile linkTarget = root.resolve(FileAccessCheckResource.linkTargetName);
                TruffleFile link = FileAccessCheckResource.linkName != null ? root.resolve(FileAccessCheckResource.linkName) : null;
                assertSecurityException(() -> root.resolve("fooDir").createDirectory());
                assertSecurityException(() -> root.resolve("fooDir").createDirectories());
                assertSecurityException(() -> root.resolve("fooFile").createFile());
                assertTrue(file.exists());
                assertTrue(folder.isDirectory());
                assertTrue(file.isRegularFile());
                assertTrue(file.isReadable());
                assertFalse(file.isWritable());
                assertFalse(file.isExecutable() && !OSUtils.isWindows());
                assertEquals(Objects.requireNonNull(file.getName()).getBytes(StandardCharsets.UTF_8).length, file.size());
                assertFalse(file.isSameFile(folder));
                assertNotNull(file.getAttribute(TruffleFile.CREATION_TIME));
                assertNotNull(file.getAttributes(List.of(TruffleFile.CREATION_TIME)).get(TruffleFile.CREATION_TIME));
                assertNotNull(file.getCreationTime());
                assertNotNull(file.getLastAccessTime());
                assertNotNull(file.getLastModifiedTime());
                assertSecurityException(() -> file.setAttribute(TruffleFile.CREATION_TIME, FileTime.from(Instant.now())));
                assertEquals(1, folder.list().size());
                List<TruffleFile> foundFiles = new ArrayList<>();
                try (DirectoryStream<TruffleFile> stream = folder.newDirectoryStream()) {
                    stream.forEach(foundFiles::add);
                }
                assertEquals(1, foundFiles.size());
                foundFiles.clear();
                folder.visit(new FileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(TruffleFile dir, BasicFileAttributes attrs) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(TruffleFile f, BasicFileAttributes attrs) {
                        foundFiles.add(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(TruffleFile f, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(TruffleFile dir, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                }, 100);
                assertEquals(1, foundFiles.size());
                try (BufferedReader r = file.newBufferedReader()) {
                    assertEquals(file.getName(), readContent(r));
                }
                try (BufferedReader r = new BufferedReader(new InputStreamReader(file.newInputStream()))) {
                    assertEquals(file.getName(), readContent(r));
                }
                try (BufferedReader r = new BufferedReader(Channels.newReader(file.newByteChannel(Set.of(StandardOpenOption.READ)), StandardCharsets.UTF_8))) {
                    assertEquals(file.getName(), readContent(r));
                }
                assertEquals(file.getName(), new String(file.readAllBytes(), StandardCharsets.UTF_8));
                assertSecurityException(file::newBufferedWriter);
                assertSecurityException(file::newOutputStream);
                assertSecurityException(() -> file.newByteChannel(Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE)));
                if (link != null) {
                    assertTrue(link.isSymbolicLink());
                    assertSecurityException(() -> file.createLink(file.resolveSibling("ln")));
                    assertSecurityException(() -> file.createSymbolicLink(file.resolveSibling("lns")));
                    assertEquals(linkTarget, link.readSymbolicLink());
                }
                assertSecurityException(file::delete);
                if (OSUtils.isUnix()) {
                    assertNotNull(file.getOwner());
                    assertNotNull(file.getGroup());
                    assertNotNull(file.getPosixPermissions());
                    assertSecurityException(() -> file.setPosixPermissions(Set.of()));
                }
                assertSecurityException(() -> file.copy(file.resolveSibling("cp")));
                assertSecurityException(() -> file.move(file.resolveSibling("mv")));
                assertSecurityException(() -> file.move(hostFolder.resolve("mv")));
                return null;
            } finally {
                delete(hostFolder);
            }
        }

        private static String readContent(BufferedReader r) throws IOException {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (first) {
                    first = false;
                } else {
                    builder.append("\n");
                }
                builder.append(line);
            }
            return builder.toString();
        }
    }

    @Test
    public void testAccessFileInResourceRoot() {
        Assume.assumeFalse("Cannot run as native unittest", ImageInfo.inImageRuntimeCode());
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.execute(context, TestAccessFileInResourceRoot.class);
        }
    }

    @Registration(/* ... */internalResources = {LibraryResource.class, SourcesResource.class})
    public static class TestExplicitResourceRoot extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        @SuppressWarnings("try")
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String explicitCacheRoot = (String) contextArguments[0];
            LibraryResource.unpackedCalled = 0;
            SourcesResource.unpackedCalled = 0;
            TruffleFile libRoot = env.getInternalResource(LibraryResource.class);
            assertTrue(libRoot.getCanonicalFile().getPath().startsWith(explicitCacheRoot));
            verifyResources(libRoot, LibraryResource.RESOURCES);
            TruffleFile srcRoot = env.getInternalResource(SourcesResource.class);
            assertTrue(srcRoot.getCanonicalFile().getPath().startsWith(explicitCacheRoot));
            verifyResources(srcRoot, SourcesResource.RESOURCES);
            assertEquals(0, LibraryResource.unpackedCalled);
            assertEquals(0, SourcesResource.unpackedCalled);
            return "";
        }
    }

    @Test
    public void testExplicitResourceRoot() throws Exception {
        Assume.assumeFalse("Cannot run as native unittest", ImageInfo.inImageRuntimeCode());
        // Prepare standalone resources
        Path cacheRoot = Files.createTempDirectory(null);
        Engine.copyResources(cacheRoot, TestUtils.getDefaultLanguageId(TestExplicitResourceRoot.class));
        // Reset cached resource root
        TemporaryResourceCacheRoot.setTestCacheRoot(null, true);
        // Set explicit resource root
        System.setProperty("polyglot.engine.ResourcesFolder", cacheRoot.toString());
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.execute(context, TestExplicitResourceRoot.class, cacheRoot.toRealPath().toString());
        } finally {
            // Clean explicit resource root
            System.getProperties().remove("polyglot.engine.ResourcesFolder");
            // Reset cached resource root
            TemporaryResourceCacheRoot.setTestCacheRoot(null, true);
            TemporaryResourceCacheRoot.delete(cacheRoot);
        }
    }

    @Registration(/* ... */internalResources = {LibraryResource.class, SourcesResource.class})
    public static class TestLanguageResourcesLookedUpById extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        @SuppressWarnings("try")
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try (TemporaryResourceCacheRoot cache = new TemporaryResourceCacheRoot()) {
                TruffleFile libRoot = env.getInternalResource(LibraryResource.ID);
                verifyResources(libRoot, LibraryResource.RESOURCES);
                TruffleFile srcRoot = env.getInternalResource(SourcesResource.ID);
                verifyResources(srcRoot, SourcesResource.RESOURCES);
                return "";
            }
        }
    }

    @Test
    public void testLanguageResourcesLookedUpById() {
        Assume.assumeFalse("Cannot run as native unittest", ImageInfo.inImageRuntimeCode());
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.execute(context, TestLanguageResourcesLookedUpById.class);
        }
    }

    @TruffleInstrument.Registration(id = InstrumentUsingResourcesByName.ID, name = InstrumentUsingResourcesByName.ID, //
                    services = InstrumentUsingResourcesByName.Create.class, internalResources = {LibraryResource.class, SourcesResource.class})
    public static final class InstrumentUsingResourcesByName extends TruffleInstrument {
        static final String ID = "InstrumentUsingResourcesByName";

        public interface Create {
        }

        @Override
        @SuppressWarnings("try")
        protected void onCreate(Env env) {
            env.registerService(new Create() {
            });
            try (TemporaryResourceCacheRoot cache = new TemporaryResourceCacheRoot()) {
                TruffleFile libRoot = env.getInternalResource(LibraryResource.ID);
                verifyResources(libRoot, LibraryResource.RESOURCES);
                TruffleFile srcRoot = env.getInternalResource(SourcesResource.ID);
                verifyResources(srcRoot, SourcesResource.RESOURCES);
            } catch (IOException ioe) {
                throw CompilerDirectives.shouldNotReachHere(ioe);
            }
        }
    }

    @Registration(/* ... */)
    public static class TestInstrumentResourcesLookedUpById extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            InstrumentInfo info = env.getInstruments().get(InstrumentUsingResourcesByName.ID);
            assertNotNull(info);
            env.lookup(info, InstrumentUsingResourcesByName.Create.class);
            return null;
        }
    }

    @Test
    public void testInstrumentResourcesLookedUpById() {
        Assume.assumeFalse("Cannot run as native unittest", ImageInfo.inImageRuntimeCode());
        try (Context context = Context.create()) {
            AbstractExecutableTestLanguage.execute(context, TestInstrumentResourcesLookedUpById.class);
        }
    }

    private static void assertSecurityException(TruffleFileAction action) {
        assertFails(() -> {
            action.run();
            return null;
        }, SecurityException.class);
    }

    private static void delete(TruffleFile file) throws IOException {
        if (file.isDirectory()) {
            for (TruffleFile child : file.list()) {
                delete(child);
            }
        }
        file.delete();
    }

    @FunctionalInterface
    interface TruffleFileAction {
        void run() throws IOException;
    }

    static final class TemporaryResourceCacheRoot implements AutoCloseable {

        private final Path root;
        private final boolean disposeResourceFileSystem;

        TemporaryResourceCacheRoot() throws IOException {
            this(true);
        }

        TemporaryResourceCacheRoot(boolean disposeResourceFileSystem) throws IOException {
            this(Files.createTempDirectory(null), disposeResourceFileSystem);
        }

        TemporaryResourceCacheRoot(Path cacheRoot, boolean disposeResourceFileSystem) throws IOException {
            try {
                root = cacheRoot.toRealPath();
                this.disposeResourceFileSystem = disposeResourceFileSystem;
                setTestCacheRoot(root, false);
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Failed to set cache root.", e);
            }
        }

        Path getRoot() {
            return root;
        }

        @Override
        public void close() {
            try {
                setTestCacheRoot(null, disposeResourceFileSystem);
                delete(root);
            } catch (IOException | ClassNotFoundException e) {
                throw new AssertionError("Failed to reset cache root.", e);
            }
        }

        private static void delete(Path path) throws IOException {
            if (Files.isDirectory(path)) {
                try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                    for (Path child : children) {
                        delete(child);
                    }
                }
            }
            Files.delete(path);
        }

        private static void setTestCacheRoot(Path root, boolean disposeResourceFileSystem) throws ClassNotFoundException {
            Class<?> internalResourceCacheClass = Class.forName("com.oracle.truffle.polyglot.InternalResourceCache");
            ReflectionUtils.invokeStatic(internalResourceCacheClass, "setTestCacheRoot", new Class<?>[]{Path.class, boolean.class}, root, disposeResourceFileSystem);
        }
    }
}
