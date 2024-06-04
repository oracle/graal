/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.OSUtils;
import com.oracle.truffle.api.test.polyglot.FileSystemsTest.ForwardingFileSystem;
import com.oracle.truffle.api.test.polyglot.TruffleFileTest.DuplicateMimeTypeLanguage1.Language1Detector;
import com.oracle.truffle.api.test.polyglot.TruffleFileTest.DuplicateMimeTypeLanguage2.Language2Detector;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class TruffleFileTest {

    private static Path languageHome;
    private static Path languageHomeFile;
    private static Path stdLibFile;
    private static Path nonLanguageHomeFile;

    @BeforeClass
    public static void setUpClass() throws Exception {
        languageHome = Files.createTempDirectory(TruffleFileTest.class.getSimpleName()).toRealPath();
        languageHomeFile = languageHome.resolve("homeFile");
        Files.write(languageHomeFile, Collections.singleton(languageHomeFile.getFileName().toString()));
        Path stdLib = Files.createDirectory(languageHome.resolve("stdlib"));
        stdLibFile = stdLib.resolve("stdLibFile");
        Files.write(stdLibFile, Collections.singleton(stdLibFile.getFileName().toString()));
        nonLanguageHomeFile = Files.createTempFile(TruffleFileTest.class.getSimpleName(), "").toRealPath();
        Files.write(nonLanguageHomeFile, Collections.singleton(nonLanguageHomeFile.getFileName().toString()));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (languageHome != null) {
            delete(languageHome);
            delete(nonLanguageHomeFile);
        }
    }

    private static final Predicate<TruffleFile> FAILING_RECOGNIZER = (tf) -> {
        throw silenceException(RuntimeException.class, new IOException());
    };

    private static final Predicate<TruffleFile> ALL_FILES_RECOGNIZER = (tf) -> true;

    @After
    public void tearDown() throws Exception {
        for (Class<? extends BaseDetector> clz : Arrays.asList(Language1Detector.class, Language2Detector.class)) {
            BaseDetector instance = BaseDetector.getInstance(clz);
            if (instance != null) {
                instance.reset();
            }
        }
    }

    @Test
    public void testToAbsolutePath() {
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestToAbsolutePathLanguage.class, "");
        }
    }

    @Registration
    static final class TestToAbsolutePathLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile file = env.getPublicTruffleFile(OSUtils.isUnix() ? "/a/b" : "C:/a/b");
            Assert.assertTrue(file.isAbsolute());
            Assert.assertEquals(file, file.getAbsoluteFile());
            testToAbsolutePathImpl(env, "");
            testToAbsolutePathImpl(env, "a");
            testToAbsolutePathImpl(env, "a/b");
            testToAbsolutePathImpl(env, ".");
            testToAbsolutePathImpl(env, "./");
            testToAbsolutePathImpl(env, "..");
            testToAbsolutePathImpl(env, "a/../");
            testToAbsolutePathImpl(env, "./a/b");
            testToAbsolutePathImpl(env, "a/../b");
            testToAbsolutePathImpl(env, "a/../../");

            env.setCurrentWorkingDirectory(env.getPublicTruffleFile(OSUtils.isUnix() ? "/" : "C:/"));
            testToAbsolutePathImpl(env, "");
            testToAbsolutePathImpl(env, "a");
            testToAbsolutePathImpl(env, "a/b");
            testToAbsolutePathImpl(env, ".");
            testToAbsolutePathImpl(env, "./");
            testToAbsolutePathImpl(env, "..");
            testToAbsolutePathImpl(env, "a/../");
            testToAbsolutePathImpl(env, "./a/b");
            testToAbsolutePathImpl(env, "a/../b");
            testToAbsolutePathImpl(env, "a/../../");
            return null;
        }

        private static void testToAbsolutePathImpl(Env env, String path) {
            TruffleFile relativeFile = env.getPublicTruffleFile(path);
            Assert.assertFalse(relativeFile.isAbsolute());
            TruffleFile absoluteFile = relativeFile.getAbsoluteFile();
            TruffleFile cwd = env.getCurrentWorkingDirectory();
            TruffleFile expectedFile = cwd.resolve(relativeFile.getPath());
            Assert.assertEquals(expectedFile, absoluteFile);
            Assert.assertEquals(expectedFile.normalize(), absoluteFile.normalize());
        }
    }

    @Test
    public void testGetName() {
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetNameLanguage.class, "");
        }
    }

    @Registration
    static final class TestGetNameLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile file = env.getPublicTruffleFile("/folder/filename");
            Assert.assertEquals("filename", file.getName());
            file = env.getPublicTruffleFile("/filename");
            Assert.assertEquals("filename", file.getName());
            file = env.getPublicTruffleFile("folder/filename");
            Assert.assertEquals("filename", file.getName());
            file = env.getPublicTruffleFile("filename");
            Assert.assertEquals("filename", file.getName());
            file = env.getPublicTruffleFile("");
            Assert.assertEquals("", file.getName());
            file = env.getPublicTruffleFile("/");
            Assert.assertNull(file.getName());
            return null;
        }
    }

    @Test
    public void testDetectMimeType() {
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestDetectMimeTypeLanguage.class, "");
        }
    }

    @Registration
    static final class TestDetectMimeTypeLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile file = env.getPublicTruffleFile("/folder/filename.duplicate");
            String result = file.detectMimeType();
            assertNull(result);
            Language1Detector detector1 = Language1Detector.getInstance();
            Language2Detector detector2 = Language2Detector.getInstance();
            assertEquals(1, detector1.resetFindMimeTypeCalled());
            assertEquals(1, detector2.resetFindMimeTypeCalled());
            detector1.mimeType(null);
            detector2.mimeType("text/x-duplicate-mime");
            result = file.detectMimeType();
            assertEquals("text/x-duplicate-mime", result);
            detector1.mimeType("text/x-duplicate-mime");
            detector2.mimeType(null);
            result = file.detectMimeType();
            assertEquals("text/x-duplicate-mime", result);
            detector1.mimeType("text/x-duplicate-mime");
            detector2.mimeType("text/x-duplicate-mime");
            result = file.detectMimeType();
            assertEquals("text/x-duplicate-mime", result);
            detector1.mimeType("text/x-duplicate-mime-1");
            detector2.mimeType("text/x-duplicate-mime-2");
            result = file.detectMimeType();
            // Order is not deterministic can be either 'text/x-duplicate-mime-1' or
            // 'text/x-duplicate-mime-2'
            assertTrue("text/x-duplicate-mime-1".equals(result) || "text/x-duplicate-mime-2".equals(result));
            detector1.reset().mimeType("text/x-duplicate-mime-1").recognizer(FAILING_RECOGNIZER);
            detector2.reset().mimeType("text/x-duplicate-mime-2");
            result = file.detectMimeType();
            assertEquals("text/x-duplicate-mime-2", result);
            detector1.reset().mimeType("text/x-duplicate-mime-1");
            detector2.reset().mimeType("text/x-duplicate-mime-2").recognizer(FAILING_RECOGNIZER);
            result = file.detectMimeType();
            assertEquals("text/x-duplicate-mime-1", result);
            detector1.reset().mimeType("text/x-duplicate-mime-1").recognizer(FAILING_RECOGNIZER);
            detector2.reset().mimeType("text/x-duplicate-mime-2").recognizer(FAILING_RECOGNIZER);
            result = file.detectMimeType();
            assertNull(result);
            detector1.reset().mimeType("text/x-duplicate-mime-1").recognizer(ALL_FILES_RECOGNIZER);
            detector2.reset().mimeType("text/x-duplicate-mime-2").recognizer(ALL_FILES_RECOGNIZER);
            result = env.getInternalTruffleFile("").detectMimeType();
            assertNull(result);
            assertEquals(0, detector1.resetFindMimeTypeCalled());
            assertEquals(0, detector2.resetFindMimeTypeCalled());
            return null;
        }
    }

    @Test
    public void testDetectEncoding() {
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestDetectEncodingLanguage.class, "");
        }
    }

    @Registration
    static final class TestDetectEncodingLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile file = env.getPublicTruffleFile("/folder/filename.duplicate");
            assertFails(() -> TestAPIAccessor.languageAccess().detectEncoding(file, null), IllegalArgumentException.class);
            String mimeType = "text/x-duplicate-mime";
            Charset encoding = TestAPIAccessor.languageAccess().detectEncoding(file, mimeType);
            assertNull(encoding);
            Language1Detector detector1 = Language1Detector.getInstance();
            Language2Detector detector2 = Language2Detector.getInstance();
            detector1.reset();
            detector2.reset().mimeType(mimeType);
            encoding = TestAPIAccessor.languageAccess().detectEncoding(file, mimeType);
            assertNull(encoding);
            detector1.reset().mimeType(mimeType);
            detector2.reset().mimeType(mimeType).encoding(UTF_16);
            encoding = TestAPIAccessor.languageAccess().detectEncoding(file, mimeType);
            assertEquals(UTF_16, encoding);
            detector1.reset().mimeType(mimeType).encoding(UTF_8);
            detector2.reset().mimeType(mimeType);
            encoding = TestAPIAccessor.languageAccess().detectEncoding(file, mimeType);
            assertEquals(UTF_8, encoding);
            detector1.reset().mimeType(mimeType).encoding(UTF_8);
            detector2.reset().mimeType(mimeType).encoding(UTF_16);
            encoding = TestAPIAccessor.languageAccess().detectEncoding(file, mimeType);
            // Order is not deterministic can be either 'UTF-8' or 'UTF-16'
            assertTrue(UTF_8.equals(encoding) || UTF_16.equals(encoding));
            detector1.reset().mimeType(mimeType).encoding(UTF_8).recognizer(FAILING_RECOGNIZER);
            detector2.reset().mimeType(mimeType).encoding(UTF_16);
            encoding = TestAPIAccessor.languageAccess().detectEncoding(file, mimeType);
            assertEquals(UTF_16, encoding);
            detector1.reset().mimeType(mimeType).encoding(UTF_8);
            detector2.reset().mimeType(mimeType).encoding(UTF_16).recognizer(FAILING_RECOGNIZER);
            encoding = TestAPIAccessor.languageAccess().detectEncoding(file, mimeType);
            assertEquals(UTF_8, encoding);
            detector1.reset().mimeType(mimeType).encoding(UTF_8).recognizer(FAILING_RECOGNIZER);
            detector2.reset().mimeType(mimeType).encoding(UTF_16).recognizer(FAILING_RECOGNIZER);
            encoding = TestAPIAccessor.languageAccess().detectEncoding(file, mimeType);
            assertNull(encoding);
            detector1.reset().mimeType(mimeType).encoding(UTF_8).recognizer(ALL_FILES_RECOGNIZER);
            detector2.reset().mimeType(mimeType).encoding(UTF_8).recognizer(ALL_FILES_RECOGNIZER);
            encoding = TestAPIAccessor.languageAccess().detectEncoding(env.getInternalTruffleFile(""), mimeType);
            assertNull(encoding);
            return null;
        }
    }

    @Test
    public void testCreateTempFileInvalidNames() {
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestCreateTempFileInvalidNamesLanguage.class, "");
        }
    }

    @Registration
    static final class TestCreateTempFileInvalidNamesLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String separator = env.getFileNameSeparator();
            try {
                env.createTempFile(null, "a" + separator + "b", ".tmp");
                Assert.fail("IllegalArgumentException expected.");
            } catch (IllegalArgumentException e) {
                // expected
            }
            try {
                env.createTempFile(null, "ab", ".tmp" + separator + "2");
                Assert.fail("IllegalArgumentException expected.");
            } catch (IllegalArgumentException e) {
                // expected
            }
            try {
                env.createTempDirectory(null, "a" + separator + "b");
                Assert.fail("IllegalArgumentException expected.");
            } catch (IllegalArgumentException e) {
                // expected
            }
            return null;
        }
    }

    @Test
    public void testExistsToCanonicalFileInconsistence() {
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestExistsToCanonicalFileInconsistenceLanguage.class, "");
        }
    }

    @Registration
    static final class TestExistsToCanonicalFileInconsistenceLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile tmp = env.createTempDirectory(null, "testExistsToCanonicalFileInconsistence");
            TruffleFile existing = tmp.resolve("existing");
            existing.createDirectory();
            Assert.assertTrue(existing.exists());
            Assert.assertTrue(existing.isDirectory());
            TruffleFile existingCanonical = existing.getCanonicalFile();

            TruffleFile nonNormalizedExisting = tmp.resolve("non-existing/../existing");
            Assert.assertTrue(nonNormalizedExisting.exists());
            Assert.assertTrue(nonNormalizedExisting.isDirectory());
            TruffleFile existingCanonical2 = nonNormalizedExisting.getCanonicalFile();
            Assert.assertEquals(existingCanonical, existingCanonical2);

            TruffleFile nonExisting = tmp.resolve("non-existing");
            Assert.assertFalse(nonExisting.exists());
            try {
                nonExisting.getCanonicalFile();
                Assert.fail("Expected IOException");
            } catch (IOException ioe) {
                // expected
            }
            return null;
        }
    }

    @Test
    public void testSetCurrentWorkingDirectory() throws IOException {
        Path newCwd = Files.createTempDirectory("testSetCWD");
        Path absoluteFolder = Files.createTempDirectory("testSetCWDAbs");
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).currentWorkingDirectory(newCwd).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestSetCurrentWorkingDirectoryLanguage.class, "", newCwd.toString(), absoluteFolder.toString());
        } finally {
            Files.delete(newCwd);
            Files.delete(absoluteFolder);
        }
    }

    @Registration
    static final class TestSetCurrentWorkingDirectoryLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Path newCwd = Paths.get((String) contextArguments[0]);
            Path absoluteFolder = Paths.get((String) contextArguments[1]);
            TruffleFile relative = env.getPublicTruffleFile("test");
            TruffleFile absolute = relative.getAbsoluteFile();
            Assert.assertEquals(newCwd.resolve("test"), Paths.get(absolute.getPath()));
            absolute = env.getPublicTruffleFile(absoluteFolder.toString());
            Assert.assertEquals(absoluteFolder, Paths.get(absolute.getPath()));
            relative = env.getInternalTruffleFile("test");
            absolute = relative.getAbsoluteFile();
            Assert.assertEquals(newCwd.resolve("test"), Paths.get(absolute.getPath()));
            absolute = env.getInternalTruffleFile(absoluteFolder.toString());
            Assert.assertEquals(absoluteFolder, Paths.get(absolute.getPath()));
            return null;
        }
    }

    @Test
    public void testRelativePathToLanguageHome() throws IOException {
        // reflection access
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation();
        Path cwdPath = new File("").toPath().toRealPath();
        Assume.assumeTrue(cwdPath.getNameCount() > 1);
        Path langHome = cwdPath.getParent().resolve("home");
        try (Engine engine = Engine.create()) {
            FileSystemsTest.markAsLanguageHome(engine, TestRelativePathToLanguageHomeLanguage.class, langHome);
            try (Context ctx = Context.newBuilder().engine(engine).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestRelativePathToLanguageHomeLanguage.class, "");
            }
        }
    }

    @Registration
    static final class TestRelativePathToLanguageHomeLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile file = env.getInternalTruffleFile("../home");
            file.exists();  // Language home should be accessible
            file.resolve("file").exists();  // File in language home should be accessible
            return null;
        }
    }

    @Test
    public void testNormalizeEmptyPath() {
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestNormalizeEmptyPathLanguage.class, "");
        }

    }

    @Registration
    static final class TestNormalizeEmptyPathLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile file = env.getInternalTruffleFile("");
            assertEquals("", file.normalize().getPath());
            file = env.getInternalTruffleFile(".");
            assertEquals(".", file.normalize().getPath());
            file = env.getInternalTruffleFile("a/..");
            assertEquals(".", file.normalize().getPath());
            return null;
        }
    }

    /**
     * Test checking that a {@link FileSystem} method for a non path operation is never called for
     * an empty path.
     */
    @Test
    public void testEmptyPath() {
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(new EmptyPathTestFs()).build();
        try (Context ctx = Context.newBuilder().allowIO(ioAccess).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestEmptyPathLanguage.class, "");
        }
    }

    @Registration
    static final class TestEmptyPathLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleFile file = env.getInternalTruffleFile("");
            TruffleFile otherFile = env.getInternalTruffleFile("other");

            Map<Type, Object> defaultParameterValues = new HashMap<>();
            defaultParameterValues.put(int.class, 0);
            defaultParameterValues.put(String.class, "");
            defaultParameterValues.put(TruffleFile.class, otherFile);
            defaultParameterValues.put(Object.class, otherFile);
            defaultParameterValues.put(Set.class, Collections.emptySet());
            defaultParameterValues.put(Charset.class, UTF_8);
            defaultParameterValues.put(OpenOption[].class, new OpenOption[0]);
            defaultParameterValues.put(LinkOption[].class, new LinkOption[0]);
            defaultParameterValues.put(CopyOption[].class, new CopyOption[0]);
            defaultParameterValues.put(FileVisitOption[].class, new FileVisitOption[0]);
            defaultParameterValues.put(FileAttribute[].class, new FileAttribute<?>[0]);
            defaultParameterValues.put(FileTime.class, FileTime.fromMillis(System.currentTimeMillis()));
            defaultParameterValues.put(TruffleFile.AttributeDescriptor.class, TruffleFile.SIZE);
            defaultParameterValues.put(TruffleFile.class.getMethod("getAttributes", Collection.class, LinkOption[].class).getGenericParameterTypes()[0], Collections.singleton(TruffleFile.SIZE));
            defaultParameterValues.put(FileVisitor.class, new FileVisitor<TruffleFile>() {
                @Override
                public FileVisitResult preVisitDirectory(TruffleFile t, BasicFileAttributes bfa) {
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult visitFile(TruffleFile t, BasicFileAttributes bfa) {
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult visitFileFailed(TruffleFile t, IOException ioe) {
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(TruffleFile t, IOException ioe) {
                    return FileVisitResult.TERMINATE;
                }
            });

            Set<Method> untestedMethods = new HashSet<>();
            for (Method m : EmptyPathTestFs.class.getMethods()) {
                assertFalse("Method " + m + " is not implemented by the EmptyPathTestFs.", m.isDefault());
            }
            nextMethod: for (Method m : TruffleFile.class.getDeclaredMethods()) {
                if ((m.getModifiers() & Modifier.PUBLIC) == Modifier.PUBLIC) {
                    Type[] paramTypes = m.getGenericParameterTypes();
                    Object[] params = new Object[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        Type paramType = paramTypes[i];
                        Object param = defaultParameterValues.get(paramType);
                        if (param == null) {
                            param = defaultParameterValues.get(erase(paramType));
                        }
                        if (param == null) {
                            untestedMethods.add(m);
                            continue nextMethod;
                        }
                        params[i] = param;
                    }
                    try {
                        m.invoke(file, params);
                    } catch (InvocationTargetException e) {
                        if (!(e.getCause() instanceof NoSuchFileException)) {
                            throw new AssertionError("Unexpected exception.", e);
                        }
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError("Unexpected exception.", e);
                    }
                }
            }
            assertTrue("Failed to check methods: " + untestedMethods.stream().map(Method::getName).collect(Collectors.joining(", ")), untestedMethods.isEmpty());
            return null;
        }
    }

    @Test
    public void testIsSameFile() {
        String path = Paths.get(".").toAbsolutePath().toString();
        try (Context ctx = Context.create()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsSameFileBasicLanguage.class, "", path);
        }
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            ctx.initialize("DuplicateMimeTypeLanguage1");
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIsSameFileLanguage.class, "", path);
        }
    }

    @Registration
    static final class TestIsSameFileBasicLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String path = (String) contextArguments[0];
            TruffleFile publicFile = env.getPublicTruffleFile(path);
            TruffleFile internalFile = env.getInternalTruffleFile(path);
            assertTrue(publicFile.isSameFile(publicFile));
            assertTrue(internalFile.isSameFile(internalFile));
            assertFalse(publicFile.isSameFile(internalFile));
            assertFalse(internalFile.isSameFile(publicFile));
            return null;
        }
    }

    @Registration
    static final class TestIsSameFileLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String path = (String) contextArguments[0];
            TruffleFile publicFile = env.getPublicTruffleFile(path);
            TruffleFile internalFile = env.getInternalTruffleFile(path);
            assertTrue(publicFile.isSameFile(publicFile));
            assertTrue(internalFile.isSameFile(internalFile));
            assertTrue(publicFile.isSameFile(internalFile));
            assertTrue(internalFile.isSameFile(publicFile));
            TruffleLanguage.Env otherLanguageEnv = DuplicateMimeTypeLanguage1.getContext().getEnv();
            TruffleFile otherLanguagePublicFile = otherLanguageEnv.getPublicTruffleFile(path);
            TruffleFile otherLanguageInternalFile = otherLanguageEnv.getInternalTruffleFile(path);
            assertTrue(publicFile.isSameFile(otherLanguagePublicFile));
            assertTrue(publicFile.isSameFile(otherLanguageInternalFile));
            assertTrue(internalFile.isSameFile(otherLanguagePublicFile));
            assertTrue(internalFile.isSameFile(otherLanguageInternalFile));
            assertTrue(otherLanguagePublicFile.isSameFile(publicFile));
            assertTrue(otherLanguagePublicFile.isSameFile(internalFile));
            assertTrue(otherLanguageInternalFile.isSameFile(publicFile));
            assertTrue(otherLanguageInternalFile.isSameFile(internalFile));
            assertFails(() -> publicFile.isSameFile(null), NullPointerException.class);
            assertFails(() -> publicFile.isSameFile(internalFile, (LinkOption[]) null), NullPointerException.class);
            assertFails(() -> publicFile.isSameFile(internalFile, (LinkOption) null), NullPointerException.class);
            return null;
        }
    }

    @Test
    public void testRelativeSymLinkWithCustomUserDir() throws IOException {
        Assume.assumeFalse("Link creation requires a special privilege on Windows", OSUtils.isWindows());
        Path workDir = Files.createTempDirectory(TruffleFileTest.class.getSimpleName());
        Path targetPath = workDir.relativize(Files.createFile(Files.createDirectory(workDir.resolve("folder")).resolve("target")));
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestRelativeSymLinkWithCustomUserDirLanguage.class, "",
                            workDir.toString(), targetPath.toString(), targetPath.getFileName().toString());
        } finally {
            delete(workDir);
        }
    }

    @Registration
    static final class TestRelativeSymLinkWithCustomUserDirLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String workDir = (String) contextArguments[0];
            String targetPath = (String) contextArguments[1];
            String targetFileName = (String) contextArguments[2];
            env.setCurrentWorkingDirectory(env.getPublicTruffleFile(workDir));
            TruffleFile symLink = env.getPublicTruffleFile("link");
            TruffleFile targetRelativePath = env.getPublicTruffleFile(targetPath);
            assertFalse(targetRelativePath.isAbsolute());
            symLink.createSymbolicLink(targetRelativePath);
            TruffleFile readLink = symLink.readSymbolicLink();
            assertFalse(readLink.isAbsolute());
            assertEquals(targetRelativePath, readLink);
            assertEquals(targetRelativePath.getCanonicalFile(), symLink.getCanonicalFile());
            symLink = env.getPublicTruffleFile("folder/link");
            TruffleFile target = env.getPublicTruffleFile(targetFileName);
            assertFalse(target.isAbsolute());
            symLink.createSymbolicLink(target);
            readLink = symLink.readSymbolicLink();
            assertFalse(readLink.isAbsolute());
            assertEquals(target, readLink);
            assertEquals(targetRelativePath.getCanonicalFile(), symLink.getCanonicalFile());
            return null;
        }
    }

    @Test
    public void testGetTruffleFileInternalAllowedIO() {
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation();
        try (Engine engine = Engine.create()) {
            FileSystemsTest.markAsLanguageHome(engine, TestGetTruffleFileInternalAllowedIOLanguage.class, languageHome);
            try (Context ctx = Context.newBuilder().engine(engine).allowIO(IOAccess.ALL).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetTruffleFileInternalAllowedIOLanguage.class, "",
                                languageHomeFile.toAbsolutePath().toString(), languageHomeFile.toUri().toString(),
                                stdLibFile.toAbsolutePath().toString(), stdLibFile.toAbsolutePath().toString(), stdLibFile.toUri().toString(),
                                nonLanguageHomeFile.toAbsolutePath().toString(), nonLanguageHomeFile.toUri().toString());
            }
        }
    }

    @Test
    public void testGetTruffleFileInternalCustomFileSystem() {
        // reflection access
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation();
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(new ForwardingFileSystem(FileSystem.newDefaultFileSystem())).build();
        try (Engine engine = Engine.create()) {
            FileSystemsTest.markAsLanguageHome(engine, TestGetTruffleFileInternalAllowedIOLanguage.class, languageHome);
            try (Context ctx = Context.newBuilder().engine(engine).allowIO(ioAccess).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetTruffleFileInternalAllowedIOLanguage.class, "",
                                languageHomeFile.toAbsolutePath().toString(), languageHomeFile.toUri().toString(),
                                stdLibFile.toAbsolutePath().toString(), stdLibFile.toAbsolutePath().toString(), stdLibFile.toUri().toString(),
                                nonLanguageHomeFile.toAbsolutePath().toString(), nonLanguageHomeFile.toUri().toString());
            }
        }
    }

    @Registration
    static final class TestGetTruffleFileInternalAllowedIOLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String languageHomeFilePath = (String) contextArguments[0];
            URI languageHomeFileURI = URI.create((String) contextArguments[1]);
            String stdLibPath = (String) contextArguments[2];
            String stdLibFilePath = (String) contextArguments[3];
            URI stdLibFileURI = URI.create((String) contextArguments[4]);
            String nonLanguageHomeFilePath = (String) contextArguments[5];
            URI nonLanguageHomeFileURI = URI.create((String) contextArguments[6]);
            StdLibPredicate predicate = new StdLibPredicate(env.getInternalTruffleFile(stdLibPath));
            TruffleFile res = env.getTruffleFileInternal(nonLanguageHomeFilePath, predicate);
            assertFalse(predicate.called);
            assertEquals(res.getName(), new String(res.readAllBytes()).trim());
            res = env.getTruffleFileInternal(languageHomeFilePath, predicate);
            assertFalse(predicate.called);
            assertEquals(res.getName(), new String(res.readAllBytes()).trim());
            res = env.getTruffleFileInternal(stdLibFilePath, predicate);
            assertFalse(predicate.called);
            assertEquals(res.getName(), new String(res.readAllBytes()).trim());
            res = env.getTruffleFileInternal(nonLanguageHomeFileURI, predicate);
            assertFalse(predicate.called);
            assertEquals(res.getName(), new String(res.readAllBytes()).trim());
            res = env.getTruffleFileInternal(languageHomeFileURI, predicate);
            assertFalse(predicate.called);
            assertEquals(res.getName(), new String(res.readAllBytes()).trim());
            res = env.getTruffleFileInternal(stdLibFileURI, predicate);
            assertFalse(predicate.called);
            assertEquals(res.getName(), new String(res.readAllBytes()).trim());
            return null;
        }
    }

    @Test
    public void testGetTruffleFileInternalDeniedIO() {
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation();
        try (Engine engine = Engine.create()) {
            FileSystemsTest.markAsLanguageHome(engine, TestGetTruffleFileInternalDeniedIOLanguage.class, languageHome);
            try (Context ctx = Context.newBuilder().engine(engine).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestGetTruffleFileInternalDeniedIOLanguage.class, "",
                                languageHomeFile.toAbsolutePath().toString(), languageHomeFile.toUri().toString(),
                                stdLibFile.toAbsolutePath().toString(), stdLibFile.toAbsolutePath().toString(), stdLibFile.toUri().toString(),
                                nonLanguageHomeFile.toAbsolutePath().toString(), nonLanguageHomeFile.toUri().toString());
            }
        }
    }

    @Registration
    static final class TestGetTruffleFileInternalDeniedIOLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String languageHomeFilePath = (String) contextArguments[0];
            URI languageHomeFileURI = URI.create((String) contextArguments[1]);
            String stdLibPath = (String) contextArguments[2];
            String stdLibFilePath = (String) contextArguments[3];
            URI stdLibFileURI = URI.create((String) contextArguments[4]);
            String nonLanguageHomeFilePath = (String) contextArguments[5];
            URI nonLanguageHomeFileURI = URI.create((String) contextArguments[6]);
            StdLibPredicate predicate = new StdLibPredicate(env.getInternalTruffleFile(stdLibPath));
            TruffleFile res = env.getTruffleFileInternal(nonLanguageHomeFilePath, predicate);
            assertFalse(predicate.called);
            TruffleFile finRes = res;
            assertFails(finRes::readAllBytes, SecurityException.class);
            res = env.getTruffleFileInternal(languageHomeFilePath, predicate);
            assertTrue(predicate.called);
            TruffleFile finRes2 = res;
            assertFails(finRes2::readAllBytes, SecurityException.class);
            predicate.called = false;
            res = env.getTruffleFileInternal(stdLibFilePath, predicate);
            assertTrue(predicate.called);
            assertEquals(res.getName(), new String(res.readAllBytes()).trim());
            predicate.called = false;
            res = env.getTruffleFileInternal(nonLanguageHomeFileURI, predicate);
            assertFalse(predicate.called);
            TruffleFile finRes3 = res;
            assertFails(finRes3::readAllBytes, SecurityException.class);
            res = env.getTruffleFileInternal(languageHomeFileURI, predicate);
            assertTrue(predicate.called);
            TruffleFile finRes4 = res;
            assertFails(finRes4::readAllBytes, SecurityException.class);
            predicate.called = false;
            res = env.getTruffleFileInternal(stdLibFileURI, predicate);
            assertTrue(predicate.called);
            assertEquals(res.getName(), new String(res.readAllBytes()).trim());
            return null;
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void testChannelClose() throws IOException {
        Path p = Files.createTempDirectory("channelClose");
        Path read1 = Files.createFile(p.resolve("read1"));
        Path read2 = Files.createFile(p.resolve("read2"));
        Path read3 = Files.createFile(p.resolve("read3"));
        Path read4 = Files.createFile(p.resolve("read4"));
        Path write1 = Files.createFile(p.resolve("write1"));
        Path write2 = Files.createFile(p.resolve("write2"));
        Path write3 = Files.createFile(p.resolve("write3"));
        Path write4 = Files.createFile(p.resolve("write4"));
        try {
            CheckCloseFileSystem fs = new CheckCloseFileSystem();
            TestRegisterOnDisposeHostSupport support = new TestRegisterOnDisposeHostSupport(fs);
            IOAccess ioAccess = IOAccess.newBuilder().fileSystem(fs).build();
            try (Context ctx = Context.newBuilder().allowIO(ioAccess).allowHostAccess(HostAccess.ALL).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestChannelCloseLanguage.class, "", support,
                                read1.toString(), read2.toString(), read3.toString(), read4.toString(),
                                write1.toString(), write2.toString(), write3.toString(), write4.toString());
            }
            assertEquals(0, support.getOpenFileCount());
        } finally {
            delete(p);
        }
    }

    @Registration
    static final class TestChannelCloseLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object support = contextArguments[0];
            String read1 = (String) contextArguments[1];
            String read2 = (String) contextArguments[2];
            String read3 = (String) contextArguments[3];
            String read4 = (String) contextArguments[4];
            String write1 = (String) contextArguments[5];
            String write2 = (String) contextArguments[6];
            String write3 = (String) contextArguments[7];
            String write4 = (String) contextArguments[8];
            assertEquals(0, getOpenFileCount(support));
            SeekableByteChannel readByteChannel1 = env.getPublicTruffleFile(read1).newByteChannel(EnumSet.of(StandardOpenOption.READ));
            assertEquals(1, getOpenFileCount(support));
            SeekableByteChannel readByteChannel2 = env.getPublicTruffleFile(read2).newByteChannel(EnumSet.of(StandardOpenOption.READ));
            env.registerOnDispose(readByteChannel2);
            assertEquals(2, getOpenFileCount(support));
            InputStream inputStream1 = env.getPublicTruffleFile(read3).newInputStream();
            assertEquals(3, getOpenFileCount(support));
            InputStream inputStream2 = env.getPublicTruffleFile(read4).newInputStream();
            env.registerOnDispose(inputStream2);
            assertEquals(4, getOpenFileCount(support));
            SeekableByteChannel writeByteChannel1 = env.getPublicTruffleFile(write1).newByteChannel(EnumSet.of(StandardOpenOption.WRITE));
            assertEquals(5, getOpenFileCount(support));
            SeekableByteChannel writeByteChannel2 = env.getPublicTruffleFile(write2).newByteChannel(EnumSet.of(StandardOpenOption.WRITE));
            env.registerOnDispose(writeByteChannel2);
            assertEquals(6, getOpenFileCount(support));
            OutputStream outputStream1 = env.getPublicTruffleFile(write3).newOutputStream();
            assertEquals(7, getOpenFileCount(support));
            OutputStream outputStream2 = env.getPublicTruffleFile(write4).newOutputStream();
            env.registerOnDispose(outputStream2);
            assertEquals(8, getOpenFileCount(support));
            readByteChannel1.close();
            assertEquals(7, getOpenFileCount(support));
            inputStream1.close();
            assertEquals(6, getOpenFileCount(support));
            writeByteChannel1.close();
            assertEquals(5, getOpenFileCount(support));
            outputStream1.close();
            assertEquals(4, getOpenFileCount(support));
            // We need to prevent the GC from releasing closeables before the context is closed.
            reachabilityFence(support, readByteChannel2);
            reachabilityFence(support, inputStream2);
            reachabilityFence(support, writeByteChannel2);
            reachabilityFence(support, outputStream2);
            return null;
        }

        private int getOpenFileCount(Object support) throws InteropException {
            return interop.asInt(interop.invokeMember(support, "getOpenFileCount"));
        }

        /**
         * We need to prevent the GC from releasing closeables before the context is closed. The
         * only way how to do it in the strong isolation is to hold these closeables on the host
         * side.
         */
        private void reachabilityFence(Object support, Object ref) throws InteropException {
            interop.invokeMember(support, "reachabilityFence", new ObjectHolder(ref));
        }
    }

    private static final class ObjectHolder implements TruffleObject {

        @SuppressWarnings("unused") private final Object ref;

        ObjectHolder(Object ref) {
            this.ref = Objects.requireNonNull(ref);
        }
    }

    public static final class TestRegisterOnDisposeHostSupport {

        private final CheckCloseFileSystem fs;
        private final Set<Object> objects = Collections.newSetFromMap(new IdentityHashMap<>());

        TestRegisterOnDisposeHostSupport(CheckCloseFileSystem fs) {
            this.fs = Objects.requireNonNull(fs);
        }

        TestRegisterOnDisposeHostSupport() {
            this.fs = null;
        }

        public int getOpenFileCount() {
            if (fs == null) {
                throw new UnsupportedOperationException();
            }
            return fs.openFileCount;
        }

        public int getOpenDirCount() {
            if (fs == null) {
                throw new UnsupportedOperationException();
            }
            return fs.openDirCount;
        }

        public void reachabilityFence(Object ref) {
            objects.add(ref);
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void testDirClose() throws IOException {
        Path p = Files.createTempDirectory("channelClose");
        Path dir1 = Files.createDirectory(p.resolve("read1"));
        Path dir2 = Files.createDirectory(p.resolve("read2"));
        try {
            CheckCloseFileSystem fs = new CheckCloseFileSystem();
            TestRegisterOnDisposeHostSupport support = new TestRegisterOnDisposeHostSupport(fs);
            IOAccess ioAccess = IOAccess.newBuilder().fileSystem(fs).build();
            try (Context ctx = Context.newBuilder().allowIO(ioAccess).allowHostAccess(HostAccess.ALL).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestDirCloseLanguage.class, "", support,
                                dir1.toString(), dir2.toString());
            }
            assertEquals(0, support.getOpenDirCount());
        } finally {
            delete(p);
        }
    }

    @Registration
    static final class TestDirCloseLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object support = contextArguments[0];
            String dir1 = (String) contextArguments[1];
            String dir2 = (String) contextArguments[2];
            assertEquals(0, getOpenDirCount(support));
            DirectoryStream<TruffleFile> dirStream1 = env.getPublicTruffleFile(dir1).newDirectoryStream();
            assertEquals(1, getOpenDirCount(support));
            DirectoryStream<TruffleFile> dirStream2 = env.getPublicTruffleFile(dir2).newDirectoryStream();
            env.registerOnDispose(dirStream2);
            assertEquals(2, getOpenDirCount(support));
            dirStream1.close();
            assertEquals(1, getOpenDirCount(support));
            // We need to prevent the GC from releasing closeables before the context is closed.
            reachabilityFence(support, dirStream2);
            return null;
        }

        private int getOpenDirCount(Object support) throws InteropException {
            return interop.asInt(interop.invokeMember(support, "getOpenDirCount"));
        }

        /**
         * We need to prevent the GC from releasing closeables before the context is closed. The
         * only way how to do it in the strong isolation is to hold these closeables on the host
         * side.
         */
        private void reachabilityFence(Object support, Object ref) throws InteropException {
            interop.invokeMember(support, "reachabilityFence", new ObjectHolder(ref));
        }
    }

    @Test
    public void testIOExceptionFromClose() {
        TestHandler handler = new TestHandler();
        TestRegisterOnDisposeHostSupport support = new TestRegisterOnDisposeHostSupport();
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).allowHostAccess(HostAccess.ALL).logHandler(handler).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestIOExceptionFromCloseLanguage.class, "", support);
        }
        Optional<LogRecord> record = handler.findRecordByMessage("Failed to close.*");
        assertTrue(record.isPresent());
        assertEquals(Level.WARNING, record.map(LogRecord::getLevel).get());
        assertEquals("engine", record.map(LogRecord::getLoggerName).get());
    }

    @Registration
    static final class TestIOExceptionFromCloseLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object support = contextArguments[0];
            Closeable closeable = () -> {
                throw new IOException();
            };
            env.registerOnDispose(closeable);
            // We need to prevent the GC from releasing closeables before the context is closed.
            reachabilityFence(support, closeable);
            return null;
        }

        /**
         * We need to prevent the GC from releasing closeables before the context is closed. The
         * only way how to do it in the strong isolation is to hold these closeables on the host
         * side.
         */
        private void reachabilityFence(Object support, Object ref) throws InteropException {
            interop.invokeMember(support, "reachabilityFence", new ObjectHolder(ref));
        }
    }

    @Test
    public void testUncheckedExceptionFromClose() {
        TestHandler handler = new TestHandler();
        TestRegisterOnDisposeHostSupport support = new TestRegisterOnDisposeHostSupport();
        Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).allowHostAccess(HostAccess.ALL).logHandler(handler).build();
        AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestUncheckedExceptionFromCloseLanguage.class, "", support);
        assertFails(() -> ctx.close(), PolyglotException.class, (pe) -> assertTrue(pe.isInternalError()));
        Optional<LogRecord> record = handler.findRecordByMessage("Failed to close.*");
        assertFalse(record.isPresent());
    }

    @Registration
    static final class TestUncheckedExceptionFromCloseLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object support = contextArguments[0];
            Closeable closeable = () -> {
                throw new RuntimeException();
            };
            env.registerOnDispose(closeable);
            // We need to prevent the GC from releasing closeables before the context is closed.
            reachabilityFence(support, closeable);
            return null;
        }

        /**
         * We need to prevent the GC from releasing closeables before the context is closed. The
         * only way how to do it in the strong isolation is to hold these closeables on the host
         * side.
         */
        private void reachabilityFence(Object support, Object ref) throws InteropException {
            interop.invokeMember(support, "reachabilityFence", new ObjectHolder(ref));
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testInvalidURI() throws IOException {
        URI httpScheme = URI.create("http://127.0.0.1/foo.js");
        Path tmp = Files.createTempDirectory("invalidscheme");
        Path existingJar = tmp.resolve("exiting.jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(existingJar, StandardOpenOption.CREATE))) {
            out.putNextEntry(new ZipEntry("file.txt"));
            out.write("content\n".getBytes(UTF_8));
            out.closeEntry();
        }
        Path nonExistingJar = tmp.resolve("non_existing.jar");
        URI jarSchemeExistingFile = URI.create("jar:" + existingJar.toUri() + "!/");
        URI jarSchemeNonExistingFile = URI.create("jar:" + nonExistingJar.toUri() + "!/");
        URI invalidUri = URI.create("file://localhost:8000/tmp");
        try (java.nio.file.FileSystem nioJarFS = FileSystems.newFileSystem(jarSchemeExistingFile, Collections.emptyMap())) {
            // Context with enabled IO
            try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestInvalidURILanguage.class, "",
                                httpScheme.toString(), jarSchemeExistingFile.toString(), jarSchemeNonExistingFile.toString(), invalidUri.toString());
            }
            // Context with disabled IO
            try (Context ctx = Context.create()) {
                AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestInvalidURILanguage.class, "",
                                httpScheme.toString(), jarSchemeExistingFile.toString(), jarSchemeNonExistingFile.toString(), invalidUri.toString());
            }
        } finally {
            delete(tmp);
        }
    }

    @Registration
    static final class TestInvalidURILanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            URI httpScheme = URI.create((String) contextArguments[0]);
            URI jarSchemeExistingFile = URI.create((String) contextArguments[1]);
            URI jarSchemeNonExistingFile = URI.create((String) contextArguments[2]);
            URI invalidUri = URI.create((String) contextArguments[3]);
            testInvalidSchemeImpl(env, httpScheme);
            testInvalidSchemeImpl(env, jarSchemeExistingFile);
            testInvalidSchemeImpl(env, jarSchemeNonExistingFile);
            testWrongURIPreconditionsImpl(env, invalidUri);
            return null;
        }

        private static void testInvalidSchemeImpl(Env env, URI uri) {
            assertFails(() -> env.getPublicTruffleFile(uri), UnsupportedOperationException.class);
            assertFails(() -> env.getInternalTruffleFile(uri), UnsupportedOperationException.class);
            assertFails(() -> env.getTruffleFileInternal(uri, (f) -> false), UnsupportedOperationException.class);
        }

        private static void testWrongURIPreconditionsImpl(Env env, URI uri) {
            assertFails(() -> env.getPublicTruffleFile(uri), IllegalArgumentException.class);
            assertFails(() -> env.getInternalTruffleFile(uri), IllegalArgumentException.class);
            assertFails(() -> env.getTruffleFileInternal(uri, (f) -> false), IllegalArgumentException.class);
        }
    }

    @Test
    public void testInvalidPath() {
        try (Context ctx = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(ctx, TestInvalidPathLanguage.class, "");
        }
    }

    @Registration
    static final class TestInvalidPathLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String invalidPath = "\0";
            assertFails(() -> env.getPublicTruffleFile(invalidPath), IllegalArgumentException.class);
            assertFails(() -> env.getInternalTruffleFile(invalidPath), IllegalArgumentException.class);
            assertFails(() -> env.getTruffleFileInternal(invalidPath, (f) -> false), IllegalArgumentException.class);
            return null;
        }
    }

    private static void delete(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(path)) {
                for (Path child : dir) {
                    delete(child);
                }
            }
        }
        Files.delete(path);
    }

    private static Type erase(Type type) {
        if (type instanceof ParameterizedType) {
            return ((ParameterizedType) type).getRawType();
        } else if (type instanceof GenericArrayType) {
            GenericArrayType gat = (GenericArrayType) type;
            Class<?> erasedComponent = (Class<?>) erase(gat.getGenericComponentType());
            return Array.newInstance(erasedComponent, 0).getClass();
        } else if (type instanceof TypeVariable) {
            return erase(((TypeVariable<?>) type).getBounds()[0]);
        } else {
            return type;
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T silenceException(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }

    public static class BaseDetector implements TruffleFile.FileTypeDetector {

        private static final Map<Class<? extends BaseDetector>, BaseDetector> INSTANCES = new HashMap<>();

        private int findMimeTypeCalled;
        private String mimeType;
        private Charset encoding;
        private Predicate<? super TruffleFile> recognizer;

        @SuppressWarnings("this-escape")
        protected BaseDetector() {
            INSTANCES.put(getClass(), this);
        }

        int resetFindMimeTypeCalled() {
            int res = findMimeTypeCalled;
            findMimeTypeCalled = 0;
            return res;
        }

        BaseDetector mimeType(String value) {
            mimeType = value;
            return this;
        }

        BaseDetector encoding(Charset value) {
            encoding = value;
            return this;
        }

        BaseDetector recognizer(Predicate<? super TruffleFile> predicate) {
            this.recognizer = predicate;
            return this;
        }

        BaseDetector reset() {
            findMimeTypeCalled = 0;
            mimeType = null;
            encoding = null;
            recognizer = null;
            return this;
        }

        @Override
        public String findMimeType(TruffleFile file) {
            if (getRecognizer().test(file)) {
                findMimeTypeCalled++;
                return mimeType;
            } else {
                return null;
            }
        }

        @Override
        public Charset findEncoding(TruffleFile file) {
            if (getRecognizer().test(file)) {
                return encoding;
            } else {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        static <T extends BaseDetector> T getInstance(Class<T> clazz) {
            return (T) INSTANCES.get(clazz);
        }

        private Predicate<? super TruffleFile> getRecognizer() {
            if (recognizer == null) {
                recognizer = (file) -> file.getName() != null && file.getName().endsWith(".duplicate");
            }
            return recognizer;
        }
    }

    @TruffleLanguage.Registration(id = "DuplicateMimeTypeLanguage1", name = "DuplicateMimeTypeLanguage1", characterMimeTypes = "text/x-duplicate-mime", fileTypeDetectors = DuplicateMimeTypeLanguage1.Language1Detector.class)
    public static final class DuplicateMimeTypeLanguage1 extends ProxyLanguage {

        public static final class Language1Detector extends BaseDetector {

            static Language1Detector getInstance() {
                return BaseDetector.getInstance(Language1Detector.class);
            }
        }

        private static final LanguageReference<DuplicateMimeTypeLanguage1> REFERENCE = LanguageReference.create(DuplicateMimeTypeLanguage1.class);
        private static final ContextReference<LanguageContext> CONTEXT_REF = ContextReference.create(DuplicateMimeTypeLanguage1.class);

        public static DuplicateMimeTypeLanguage1 get(Node node) {
            return REFERENCE.get(node);
        }

        public static LanguageContext getContext() {
            return CONTEXT_REF.get(null);
        }

        public DuplicateMimeTypeLanguage1() {
            wrapper = false;
        }
    }

    @TruffleLanguage.Registration(id = "DuplicateMimeTypeLanguage2", name = "DuplicateMimeTypeLanguage2", characterMimeTypes = "text/x-duplicate-mime", fileTypeDetectors = DuplicateMimeTypeLanguage2.Language2Detector.class)
    public static final class DuplicateMimeTypeLanguage2 extends ProxyLanguage {

        public static final class Language2Detector extends BaseDetector {
            static Language2Detector getInstance() {
                return BaseDetector.getInstance(Language2Detector.class);
            }
        }

        public DuplicateMimeTypeLanguage2() {
            wrapper = false;
        }
    }

    static final class EmptyPathTestFs implements FileSystem {

        @Override
        public Path parsePath(URI uri) {
            return Paths.get(uri);
        }

        @Override
        public Path parsePath(String path) {
            return Paths.get(path);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            // Path operations are allowed
            return path.toAbsolutePath();
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            // Path operations are allowed
            return path.toRealPath(linkOptions);
        }

        @Override
        public String getSeparator() {
            // Path operations are allowed
            return FileSystem.super.getSeparator();
        }

        @Override
        public String getPathSeparator() {
            // Path operations are allowed
            return FileSystem.super.getPathSeparator();
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) {
            throw fail();
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) {
            throw fail();
        }

        @Override
        public void delete(Path path) throws IOException {
            throw fail();
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
            throw fail();
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) {
            throw fail();
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
            throw fail();
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
            throw fail();
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) {
            throw fail();
        }

        @Override
        public Path readSymbolicLink(Path link) {
            throw fail();
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            throw fail();
        }

        @Override
        public String getMimeType(Path path) {
            throw fail();
        }

        @Override
        public Charset getEncoding(Path path) {
            throw fail();
        }

        @Override
        public Path getTempDirectory() {
            throw fail();
        }

        @Override
        public void createLink(Path link, Path existing) {
            fail();
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) {
            fail();
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            fail();
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) {
            throw fail();
        }

        private static RuntimeException fail() {
            throw new RuntimeException("Should not reach here.");
        }
    }

    private static final class StdLibPredicate implements Predicate<TruffleFile> {

        private final TruffleFile stdLibFolder;
        boolean called;

        StdLibPredicate(TruffleFile stdLibFolder) {
            this.stdLibFolder = stdLibFolder;
        }

        @Override
        public boolean test(TruffleFile truffleFile) {
            called = true;
            assertTrue(truffleFile.isAbsolute());
            return truffleFile.startsWith(stdLibFolder);
        }
    }

    private static final class CheckCloseFileSystem extends ForwardingFileSystem {

        private int openFileCount;
        private int openDirCount;

        CheckCloseFileSystem() {
            super(FileSystem.newDefaultFileSystem());
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            return new CheckCloseChannel(super.newByteChannel(path, options, attrs));
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            return new CheckCloseDirectoryStream(super.newDirectoryStream(dir, filter));
        }

        private final class CheckCloseChannel implements SeekableByteChannel {

            private final SeekableByteChannel delegate;

            CheckCloseChannel(SeekableByteChannel delegate) {
                this.delegate = Objects.requireNonNull(delegate);
                openFileCount++;
            }

            @Override
            public int read(ByteBuffer byteBuffer) throws IOException {
                return delegate.read(byteBuffer);
            }

            @Override
            public int write(ByteBuffer byteBuffer) throws IOException {
                return delegate.write(byteBuffer);
            }

            @Override
            public long position() throws IOException {
                return delegate.position();
            }

            @Override
            public SeekableByteChannel position(long l) throws IOException {
                delegate.position(l);
                return this;
            }

            @Override
            public long size() throws IOException {
                return delegate.size();
            }

            @Override
            public SeekableByteChannel truncate(long l) throws IOException {
                delegate.truncate(l);
                return this;
            }

            @Override
            public boolean isOpen() {
                return delegate.isOpen();
            }

            @Override
            public void close() throws IOException {
                try {
                    delegate.close();
                } finally {
                    openFileCount--;
                }
            }
        }

        private final class CheckCloseDirectoryStream implements DirectoryStream<Path> {

            private final DirectoryStream<Path> delegate;

            CheckCloseDirectoryStream(DirectoryStream<Path> delegate) {
                this.delegate = Objects.requireNonNull(delegate);
                openDirCount++;
            }

            @Override
            public Iterator<Path> iterator() {
                return delegate.iterator();
            }

            @Override
            public void close() throws IOException {
                try {
                    delegate.close();
                } finally {
                    openDirCount--;
                }
            }
        }
    }

    private static final class TestHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        Optional<LogRecord> findRecordByMessage(String regex) {
            Pattern pattern = Pattern.compile(regex);
            return records.stream().filter((r) -> r.getMessage() != null && pattern.matcher(r.getMessage()).matches()).findAny();
        }
    }
}
