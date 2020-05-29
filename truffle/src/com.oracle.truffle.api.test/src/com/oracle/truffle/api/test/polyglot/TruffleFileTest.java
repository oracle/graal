/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.test.OSUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.FileSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class TruffleFileTest extends AbstractPolyglotTest {

    @Before
    public void setUp() throws Exception {
        setupEnv();
        resetLanguageHomes();
    }

    @After
    public void tearDown() throws Exception {
        resetLanguageHomes();
    }

    @Test
    public void testToAbsolutePath() {
        TruffleFile file = languageEnv.getPublicTruffleFile(OSUtils.isUnix() ? "/a/b" : "C:/a/b");
        Assert.assertTrue(file.isAbsolute());
        Assert.assertEquals(file, file.getAbsoluteFile());
        testToAbsolutePathImpl("");
        testToAbsolutePathImpl("a");
        testToAbsolutePathImpl("a/b");
        testToAbsolutePathImpl(".");
        testToAbsolutePathImpl("./");
        testToAbsolutePathImpl("..");
        testToAbsolutePathImpl("a/../");
        testToAbsolutePathImpl("./a/b");
        testToAbsolutePathImpl("a/../b");
        testToAbsolutePathImpl("a/../../");

        languageEnv.setCurrentWorkingDirectory(languageEnv.getPublicTruffleFile(OSUtils.isUnix() ? "/" : "C:/"));
        testToAbsolutePathImpl("");
        testToAbsolutePathImpl("a");
        testToAbsolutePathImpl("a/b");
        testToAbsolutePathImpl(".");
        testToAbsolutePathImpl("./");
        testToAbsolutePathImpl("..");
        testToAbsolutePathImpl("a/../");
        testToAbsolutePathImpl("./a/b");
        testToAbsolutePathImpl("a/../b");
        testToAbsolutePathImpl("a/../../");
    }

    private void testToAbsolutePathImpl(String path) {
        TruffleFile relativeFile = languageEnv.getPublicTruffleFile(path);
        Assert.assertFalse(relativeFile.isAbsolute());
        TruffleFile absoluteFile = relativeFile.getAbsoluteFile();
        TruffleFile cwd = languageEnv.getCurrentWorkingDirectory();
        TruffleFile expectedFile = cwd.resolve(relativeFile.getPath());
        Assert.assertEquals(expectedFile, absoluteFile);
        Assert.assertEquals(expectedFile.normalize(), absoluteFile.normalize());
    }

    @Test
    public void testGetName() {
        TruffleFile file = languageEnv.getPublicTruffleFile("/folder/filename");
        Assert.assertEquals("filename", file.getName());
        file = languageEnv.getPublicTruffleFile("/filename");
        Assert.assertEquals("filename", file.getName());
        file = languageEnv.getPublicTruffleFile("folder/filename");
        Assert.assertEquals("filename", file.getName());
        file = languageEnv.getPublicTruffleFile("filename");
        Assert.assertEquals("filename", file.getName());
        file = languageEnv.getPublicTruffleFile("");
        Assert.assertEquals("", file.getName());
        file = languageEnv.getPublicTruffleFile("/");
        Assert.assertNull(file.getName());
    }

    @Test
    public void testGetMimeType() throws IOException {
        TruffleFile file = languageEnv.getPublicTruffleFile("/folder/filename.duplicate");
        String result = file.getMimeType();
        assertNull(result);
        assertEquals(1, BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).resetFindMimeTypeCalled());
        assertEquals(1, BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).resetFindMimeTypeCalled());
        BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).setMimeType(null);
        BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).setMimeType("text/x-duplicate-mime");
        result = file.getMimeType();
        assertEquals("text/x-duplicate-mime", result);
        BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).setMimeType("text/x-duplicate-mime");
        BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).setMimeType(null);
        result = file.getMimeType();
        assertEquals("text/x-duplicate-mime", result);
        BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).setMimeType("text/x-duplicate-mime");
        BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).setMimeType("text/x-duplicate-mime");
        result = file.getMimeType();
        assertEquals("text/x-duplicate-mime", result);
        BaseDetector.getInstance(DuplicateMimeTypeLanguage1.Detector.class).setMimeType("text/x-duplicate-mime-1");
        BaseDetector.getInstance(DuplicateMimeTypeLanguage2.Detector.class).setMimeType("text/x-duplicate-mime-2");
        result = file.getMimeType();
        // Order is not deterministic can be either 'text/x-duplicate-mime-1' or
        // 'text/x-duplicate-mime-2'
        assertTrue("text/x-duplicate-mime-1".equals(result) || "text/x-duplicate-mime-2".equals(result));
    }

    @Test
    public void testCreateTempFileInvalidNames() throws IOException {
        String separator = languageEnv.getFileNameSeparator();
        try {
            languageEnv.createTempFile(null, "a" + separator + "b", ".tmp");
            Assert.fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            languageEnv.createTempFile(null, "ab", ".tmp" + separator + "2");
            Assert.fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            languageEnv.createTempDirectory(null, "a" + separator + "b");
            Assert.fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testExistsToCanonicalFileInconsistence() throws IOException {
        TruffleFile tmp = languageEnv.createTempDirectory(null, "testExistsToCanonicalFileInconsistence");
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
    }

    @Test
    public void testSetCurrentWorkingDirectory() throws IOException {
        Path newCwd = Files.createTempDirectory("testSetCWD");
        Path absoluteFolder = Files.createTempDirectory("testSetCWDAbs");
        try (Context ctx = Context.newBuilder().allowAllAccess(true).currentWorkingDirectory(newCwd).build()) {
            setupEnv(ctx);
            TruffleFile relative = languageEnv.getPublicTruffleFile("test");
            TruffleFile absolute = relative.getAbsoluteFile();
            Assert.assertEquals(newCwd.resolve("test"), Paths.get(absolute.getPath()));
            absolute = languageEnv.getPublicTruffleFile(absoluteFolder.toString());
            Assert.assertEquals(absoluteFolder, Paths.get(absolute.getPath()));
            relative = languageEnv.getInternalTruffleFile("test");
            absolute = relative.getAbsoluteFile();
            Assert.assertEquals(newCwd.resolve("test"), Paths.get(absolute.getPath()));
            absolute = languageEnv.getInternalTruffleFile(absoluteFolder.toString());
            Assert.assertEquals(absoluteFolder, Paths.get(absolute.getPath()));
        } finally {
            Files.delete(newCwd);
        }
    }

    @Test
    public void testRelativePathToLanguageHome() throws IOException {
        setupEnv(Context.create());
        Path cwdPath = new File("").toPath().toRealPath();
        Assume.assumeTrue(cwdPath.getNameCount() > 1);
        Path langHomePath = cwdPath.getParent().resolve("home");
        System.setProperty(String.format("org.graalvm.language.%s.home", ProxyLanguage.ID), langHomePath.toString());
        TruffleFile file = languageEnv.getInternalTruffleFile("../home");
        file.exists();  // Language home should be accessible
        file.resolve("file").exists();  // File in language home should be accessible
    }

    @Test
    public void testNormalizeEmptyPath() {
        setupEnv(Context.newBuilder().allowIO(true).build());
        TruffleFile file = languageEnv.getInternalTruffleFile("");
        assertEquals("", file.normalize().getPath());
        file = languageEnv.getInternalTruffleFile(".");
        assertEquals(".", file.normalize().getPath());
        file = languageEnv.getInternalTruffleFile("a/..");
        assertEquals(".", file.normalize().getPath());
    }

    /**
     * Test checking that a {@link FileSystem} method for a non path operation is never called for
     * an empty path.
     */
    @Test
    public void testEmptyPath() throws Exception {
        setupEnv(Context.newBuilder().allowIO(true).fileSystem(new EmptyPathTestFs()).build());
        TruffleFile file = languageEnv.getInternalTruffleFile("");
        TruffleFile otherFile = languageEnv.getInternalTruffleFile("other");

        Map<Type, Object> defaultParameterValues = new HashMap<>();
        defaultParameterValues.put(int.class, 0);
        defaultParameterValues.put(String.class, "");
        defaultParameterValues.put(TruffleFile.class, otherFile);
        defaultParameterValues.put(Object.class, otherFile);
        defaultParameterValues.put(Set.class, Collections.emptySet());
        defaultParameterValues.put(Charset.class, StandardCharsets.UTF_8);
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
            public FileVisitResult preVisitDirectory(TruffleFile t, BasicFileAttributes bfa) throws IOException {
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult visitFile(TruffleFile t, BasicFileAttributes bfa) throws IOException {
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult visitFileFailed(TruffleFile t, IOException ioe) throws IOException {
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(TruffleFile t, IOException ioe) throws IOException {
                return FileVisitResult.TERMINATE;
            }
        });

        Set<Method> untestedMethods = new HashSet<>();
        for (Method m : EmptyPathTestFs.class.getMethods()) {
            if (m.isDefault()) {
                System.out.println(m.getName());
            }
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

    private static void resetLanguageHomes() throws ReflectiveOperationException {
        Class<?> languageCache = Class.forName("com.oracle.truffle.polyglot.LanguageCache");
        Method reset = languageCache.getDeclaredMethod("resetNativeImageCacheLanguageHomes");
        reset.setAccessible(true);
        reset.invoke(null);
    }

    public static class BaseDetector implements TruffleFile.FileTypeDetector {

        private static Map<Class<? extends BaseDetector>, BaseDetector> INSTANCES = new HashMap<>();

        private int findMimeTypeCalled;
        private String mimeType;

        protected BaseDetector() {
            INSTANCES.put(getClass(), this);
        }

        int resetFindMimeTypeCalled() {
            int res = findMimeTypeCalled;
            findMimeTypeCalled = 0;
            return res;
        }

        void setMimeType(String value) {
            mimeType = value;
        }

        @Override
        public String findMimeType(TruffleFile file) throws IOException {
            String name = file.getName();
            if (name != null && name.endsWith(".duplicate")) {
                findMimeTypeCalled++;
                return mimeType;
            } else {
                return null;
            }
        }

        @Override
        public Charset findEncoding(TruffleFile file) throws IOException {
            return null;
        }

        @SuppressWarnings("unchecked")
        static <T extends BaseDetector> T getInstance(Class<T> clazz) {
            return (T) INSTANCES.get(clazz);
        }
    }

    @TruffleLanguage.Registration(id = "DuplicateMimeTypeLanguage1", name = "DuplicateMimeTypeLanguage1", characterMimeTypes = "text/x-duplicate-mime", fileTypeDetectors = DuplicateMimeTypeLanguage1.Detector.class)
    public static final class DuplicateMimeTypeLanguage1 extends ProxyLanguage {

        public static final class Detector extends BaseDetector {
        }
    }

    @TruffleLanguage.Registration(id = "DuplicateMimeTypeLanguage2", name = "DuplicateMimeTypeLanguage2", characterMimeTypes = "text/x-duplicate-mime", fileTypeDetectors = DuplicateMimeTypeLanguage2.Detector.class)
    public static final class DuplicateMimeTypeLanguage2 extends ProxyLanguage {

        public static final class Detector extends BaseDetector {
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
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            throw fail();
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            throw fail();
        }

        @Override
        public void delete(Path path) throws IOException {
            throw fail();
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            throw fail();
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            throw fail();
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            throw fail();
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            throw fail();
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            throw fail();
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
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
        public void createLink(Path link, Path existing) throws IOException {
            fail();
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            fail();
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            fail();
        }

        private static RuntimeException fail() {
            throw new RuntimeException("Should not reach here.");
        }
    }
}
