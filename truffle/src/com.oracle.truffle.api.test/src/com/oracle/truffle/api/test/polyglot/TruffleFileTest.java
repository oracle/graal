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
import java.lang.ref.Reference;
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.OSUtils;
import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.api.test.polyglot.FileSystemsTest.ForwardingFileSystem;
import com.oracle.truffle.api.test.polyglot.TruffleFileTest.DuplicateMimeTypeLanguage1.Language1Detector;
import com.oracle.truffle.api.test.polyglot.TruffleFileTest.DuplicateMimeTypeLanguage2.Language2Detector;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class TruffleFileTest extends AbstractPolyglotTest {

    private static Path languageHome;
    private static Path languageHomeFile;
    private static Path stdLib;
    private static Path stdLibFile;
    private static Path nonLanguageHomeFile;

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        languageHome = Files.createTempDirectory(TruffleFileTest.class.getSimpleName());
        languageHomeFile = languageHome.resolve("homeFile");
        Files.write(languageHomeFile, Collections.singleton(languageHomeFile.getFileName().toString()));
        stdLib = Files.createDirectory(languageHome.resolve("stdlib"));
        stdLibFile = stdLib.resolve("stdLibFile");
        Files.write(stdLibFile, Collections.singleton(stdLibFile.getFileName().toString()));
        System.setProperty("org.graalvm.language.InternalTruffleFileTestLanguage.home", languageHome.toAbsolutePath().toString());
        nonLanguageHomeFile = Files.createTempFile(TruffleFileTest.class.getSimpleName(), "");
        Files.write(nonLanguageHomeFile, Collections.singleton(nonLanguageHomeFile.getFileName().toString()));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (languageHome != null) {
            System.getProperties().remove("org.graalvm.language.InternalTruffleFileTestLanguage.home");
            resetLanguageHomes();
            delete(languageHome);
            delete(nonLanguageHomeFile);
        }
    }

    private static final Predicate<TruffleFile> FAILING_RECOGNIZER = (tf) -> {
        throw silenceException(RuntimeException.class, new IOException());
    };

    private static final Predicate<TruffleFile> ALL_FILES_RECOGNIZER = (tf) -> true;

    public TruffleFileTest() {
        needsLanguageEnv = true;
    }

    @Before
    public void setUp() throws Exception {
        setupEnv();
        resetLanguageHomes();
    }

    @After
    public void tearDown() throws Exception {
        resetLanguageHomes();
        for (Class<? extends BaseDetector> clz : Arrays.asList(Language1Detector.class, Language2Detector.class)) {
            BaseDetector instance = BaseDetector.getInstance(clz);
            if (instance != null) {
                instance.reset();
            }
        }
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
    public void testDetectMimeType() {
        TruffleFile file = languageEnv.getPublicTruffleFile("/folder/filename.duplicate");
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
        result = languageEnv.getInternalTruffleFile("").detectMimeType();
        assertNull(result);
        assertEquals(0, detector1.resetFindMimeTypeCalled());
        assertEquals(0, detector2.resetFindMimeTypeCalled());
    }

    @Test
    public void testDetectEncoding() {
        TruffleFile file = languageEnv.getPublicTruffleFile("/folder/filename.duplicate");
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
        encoding = TestAPIAccessor.languageAccess().detectEncoding(languageEnv.getInternalTruffleFile(""), mimeType);
        assertNull(encoding);
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
        setupEnv(Context.newBuilder().allowIO(IOAccess.ALL).build());
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
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(new EmptyPathTestFs()).build();
        setupEnv(Context.newBuilder().allowIO(ioAccess).build());
        TruffleFile file = languageEnv.getInternalTruffleFile("");
        TruffleFile otherFile = languageEnv.getInternalTruffleFile("other");

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

    @Test
    public void testIsSameFile() throws IOException {
        String path = Paths.get(".").toAbsolutePath().toString();
        setupEnv(Context.create());
        TruffleFile publicFile = languageEnv.getPublicTruffleFile(path);
        TruffleFile internalFile = languageEnv.getInternalTruffleFile(path);
        assertTrue(publicFile.isSameFile(publicFile));
        assertTrue(internalFile.isSameFile(internalFile));
        assertFalse(publicFile.isSameFile(internalFile));
        assertFalse(internalFile.isSameFile(publicFile));
        setupEnv(Context.newBuilder().allowIO(IOAccess.ALL).build());
        context.initialize("DuplicateMimeTypeLanguage1");
        publicFile = languageEnv.getPublicTruffleFile(path);
        internalFile = languageEnv.getInternalTruffleFile(path);
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
        TruffleFile finalPublicFile = publicFile;
        TruffleFile finalInternalFile = internalFile;
        assertFails(() -> finalPublicFile.isSameFile(null), NullPointerException.class);
        assertFails(() -> finalPublicFile.isSameFile(finalInternalFile, (LinkOption[]) null), NullPointerException.class);
        assertFails(() -> finalPublicFile.isSameFile(finalInternalFile, (LinkOption) null), NullPointerException.class);
    }

    @Test
    public void testRelativeSymLinkWithCustomUserDir() throws IOException {
        Assume.assumeFalse("Link creation requires a special privilege on Windows", OSUtils.isWindows());
        Path workDir = Files.createTempDirectory(TruffleFileTest.class.getSimpleName());
        Path targetPath = workDir.relativize(Files.createFile(Files.createDirectory(workDir.resolve("folder")).resolve("target")));
        try {
            setupEnv(Context.newBuilder().allowIO(IOAccess.ALL).build());
            languageEnv.setCurrentWorkingDirectory(languageEnv.getPublicTruffleFile(workDir.toString()));
            TruffleFile symLink = languageEnv.getPublicTruffleFile("link");
            TruffleFile targetRelativePath = languageEnv.getPublicTruffleFile(targetPath.toString());
            assertFalse(targetRelativePath.isAbsolute());
            symLink.createSymbolicLink(targetRelativePath);
            TruffleFile readLink = symLink.readSymbolicLink();
            assertFalse(readLink.isAbsolute());
            assertEquals(targetRelativePath, readLink);
            assertEquals(targetRelativePath.getCanonicalFile(), symLink.getCanonicalFile());
            symLink = languageEnv.getPublicTruffleFile("folder/link");
            TruffleFile target = languageEnv.getPublicTruffleFile(targetPath.getFileName().toString());
            assertFalse(target.isAbsolute());
            symLink.createSymbolicLink(target);
            readLink = symLink.readSymbolicLink();
            assertFalse(readLink.isAbsolute());
            assertEquals(target, readLink);
            assertEquals(targetRelativePath.getCanonicalFile(), symLink.getCanonicalFile());
        } finally {
            delete(workDir);
        }
    }

    @Test
    public void testGetTruffleFileInternalAllowedIO() throws IOException {
        setupEnv(Context.newBuilder().allowIO(IOAccess.ALL).build(), new InternalTruffleFileTestLanguage());
        StdLibPredicate predicate = new StdLibPredicate(languageEnv.getInternalTruffleFile(stdLib.toString()));
        TruffleFile res = languageEnv.getTruffleFileInternal(nonLanguageHomeFile.toString(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(languageHomeFile.toString(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(stdLibFile.toString(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(nonLanguageHomeFile.toUri(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(languageHomeFile.toUri(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(stdLibFile.toUri(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
    }

    @Test
    public void testGetTruffleFileInternalCustomFileSystem() throws IOException {
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(new ForwardingFileSystem(FileSystem.newDefaultFileSystem())).build();
        setupEnv(Context.newBuilder().allowIO(ioAccess).build(),
                        new InternalTruffleFileTestLanguage());
        StdLibPredicate predicate = new StdLibPredicate(languageEnv.getInternalTruffleFile(stdLib.toString()));
        TruffleFile res = languageEnv.getTruffleFileInternal(nonLanguageHomeFile.toString(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(languageHomeFile.toString(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(stdLibFile.toString(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(nonLanguageHomeFile.toUri(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(languageHomeFile.toUri(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        res = languageEnv.getTruffleFileInternal(stdLibFile.toUri(), predicate);
        assertFalse(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
    }

    @Test
    public void testGetTruffleFileInternalDeniedIO() throws IOException {
        setupEnv(Context.create(), new InternalTruffleFileTestLanguage());
        StdLibPredicate predicate = new StdLibPredicate(languageEnv.getInternalTruffleFile(stdLib.toString()));
        TruffleFile res = languageEnv.getTruffleFileInternal(nonLanguageHomeFile.toString(), predicate);
        assertFalse(predicate.called);
        TruffleFile finRes = res;
        assertFails(() -> finRes.readAllBytes(), SecurityException.class);
        res = languageEnv.getTruffleFileInternal(languageHomeFile.toString(), predicate);
        assertTrue(predicate.called);
        TruffleFile finRes2 = res;
        assertFails(() -> finRes2.readAllBytes(), SecurityException.class);
        predicate.called = false;
        res = languageEnv.getTruffleFileInternal(stdLibFile.toString(), predicate);
        assertTrue(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
        predicate.called = false;
        res = languageEnv.getTruffleFileInternal(nonLanguageHomeFile.toUri(), predicate);
        assertFalse(predicate.called);
        TruffleFile finRes3 = res;
        assertFails(() -> finRes3.readAllBytes(), SecurityException.class);
        res = languageEnv.getTruffleFileInternal(languageHomeFile.toUri(), predicate);
        assertTrue(predicate.called);
        TruffleFile finRes4 = res;
        assertFails(() -> finRes4.readAllBytes(), SecurityException.class);
        predicate.called = false;
        res = languageEnv.getTruffleFileInternal(stdLibFile.toString(), predicate);
        assertTrue(predicate.called);
        assertEquals(res.getName(), new String(res.readAllBytes()).trim());
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
            IOAccess ioAccess = IOAccess.newBuilder().fileSystem(fs).build();
            setupEnv(Context.newBuilder().allowIO(ioAccess).build());
            assertEquals(0, fs.openFileCount);
            SeekableByteChannel readByteChannel1 = languageEnv.getPublicTruffleFile(read1.toString()).newByteChannel(EnumSet.of(StandardOpenOption.READ));
            assertEquals(1, fs.openFileCount);
            SeekableByteChannel readByteChannel2 = languageEnv.getPublicTruffleFile(read2.toString()).newByteChannel(EnumSet.of(StandardOpenOption.READ));
            languageEnv.registerOnDispose(readByteChannel2);
            assertEquals(2, fs.openFileCount);
            InputStream inputStream1 = languageEnv.getPublicTruffleFile(read3.toString()).newInputStream();
            assertEquals(3, fs.openFileCount);
            InputStream inputStream2 = languageEnv.getPublicTruffleFile(read4.toString()).newInputStream();
            languageEnv.registerOnDispose(inputStream2);
            assertEquals(4, fs.openFileCount);
            SeekableByteChannel writeByteChannel1 = languageEnv.getPublicTruffleFile(write1.toString()).newByteChannel(EnumSet.of(StandardOpenOption.WRITE));
            assertEquals(5, fs.openFileCount);
            SeekableByteChannel writeByteChannel2 = languageEnv.getPublicTruffleFile(write2.toString()).newByteChannel(EnumSet.of(StandardOpenOption.WRITE));
            languageEnv.registerOnDispose(writeByteChannel2);
            assertEquals(6, fs.openFileCount);
            OutputStream outputStream1 = languageEnv.getPublicTruffleFile(write3.toString()).newOutputStream();
            assertEquals(7, fs.openFileCount);
            OutputStream outputStream2 = languageEnv.getPublicTruffleFile(write4.toString()).newOutputStream();
            languageEnv.registerOnDispose(outputStream2);
            assertEquals(8, fs.openFileCount);
            readByteChannel1.close();
            assertEquals(7, fs.openFileCount);
            inputStream1.close();
            assertEquals(6, fs.openFileCount);
            writeByteChannel1.close();
            assertEquals(5, fs.openFileCount);
            outputStream1.close();
            assertEquals(4, fs.openFileCount);
            context.close();
            assertEquals(0, fs.openFileCount);
            // The compiler can release channel and stream references before the Context#close is
            // called because these references are no longer in use. In this case, objects
            // referenced by these references can be gc-ed because registered closeables are weakly
            // referenced. We need reachability fences to keep these references alive.
            Reference.reachabilityFence(readByteChannel2);
            Reference.reachabilityFence(inputStream2);
            Reference.reachabilityFence(writeByteChannel2);
            Reference.reachabilityFence(outputStream2);
        } finally {
            delete(p);
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
            IOAccess ioAccess = IOAccess.newBuilder().fileSystem(fs).build();
            setupEnv(Context.newBuilder().allowIO(ioAccess).build());
            assertEquals(0, fs.openDirCount);
            DirectoryStream<TruffleFile> dirStream1 = languageEnv.getPublicTruffleFile(dir1.toString()).newDirectoryStream();
            assertEquals(1, fs.openDirCount);
            DirectoryStream<TruffleFile> dirStream2 = languageEnv.getPublicTruffleFile(dir2.toString()).newDirectoryStream();
            languageEnv.registerOnDispose(dirStream2);
            assertEquals(2, fs.openDirCount);
            dirStream1.close();
            assertEquals(1, fs.openDirCount);
            context.close();
            // The compiler can release the dirStream2 reference before the Context#close is called
            // because the dirStream2 is no longer in use. In this case, object referenced by the
            // dirStream2 can be gc-ed because registered closeables are weakly referenced. We need
            // a reachability fence to keep the dirStream2 reference alive.
            Reference.reachabilityFence(dirStream2);
            assertEquals(0, fs.openDirCount);
        } finally {
            delete(p);
        }
    }

    @Test
    public void testIOExceptionFromClose() {
        TestHandler handler = new TestHandler();
        setupEnv(Context.newBuilder().allowAllAccess(true).logHandler(handler).build());
        Closeable closeable = new Closeable() {
            @Override
            public void close() throws IOException {
                throw new IOException();
            }
        };
        languageEnv.registerOnDispose(closeable);
        context.close();
        // The compiler can release the closeable reference before the Context#close is called
        // because the closeable is no longer in use. In this case, object referenced by the
        // closeable can be gc-ed because registered closeables are weakly referenced. We need a
        // reachability fence to keep the closeable reference alive.
        Reference.reachabilityFence(closeable);
        Optional<LogRecord> record = handler.findRecordByMessage("Failed to close.*");
        assertTrue(record.isPresent());
        assertEquals(Level.WARNING, record.map(LogRecord::getLevel).get());
        assertEquals("engine", record.map(LogRecord::getLoggerName).get());
    }

    @Test
    public void testUncheckedExceptionFromClose() {
        TestHandler handler = new TestHandler();
        setupEnv(Context.newBuilder().allowAllAccess(true).logHandler(handler).build());
        Closeable closeable = new Closeable() {
            @Override
            public void close() throws IOException {
                throw new RuntimeException();
            }
        };
        languageEnv.registerOnDispose(closeable);
        try {
            assertFails(() -> context.close(), PolyglotException.class, (pe) -> {
                assertTrue(pe.isInternalError());
            });
        } finally {
            // The compiler can release the closeable reference before the Context#close is called
            // because the closeable is no longer in use. In this case, object referenced by the
            // closeable can be gc-ed because registered closeables are weakly referenced. We need a
            // reachability fence to keep the closeable reference alive.
            Reference.reachabilityFence(closeable);
            context = null;
        }
        Optional<LogRecord> record = handler.findRecordByMessage("Failed to close.*");
        assertFalse(record.isPresent());
    }

    @Test
    public void testInvalidScheme() throws IOException {
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
        java.nio.file.FileSystem nioJarFS = FileSystems.newFileSystem(jarSchemeExistingFile, Collections.emptyMap());
        try {
            // Context with enabled IO
            testInvalidSchemeImpl(httpScheme);
            testInvalidSchemeImpl(jarSchemeExistingFile);
            testInvalidSchemeImpl(jarSchemeNonExistingFile);
            // Context with disabled IO
            setupEnv(Context.newBuilder().build());
            testInvalidSchemeImpl(httpScheme);
            testInvalidSchemeImpl(jarSchemeExistingFile);
            testInvalidSchemeImpl(jarSchemeNonExistingFile);
        } finally {
            nioJarFS.close();
            delete(tmp);
        }
    }

    private void testInvalidSchemeImpl(URI uri) {
        assertFails(() -> languageEnv.getPublicTruffleFile(uri), UnsupportedOperationException.class);
        assertFails(() -> languageEnv.getInternalTruffleFile(uri), UnsupportedOperationException.class);
        assertFails(() -> languageEnv.getTruffleFileInternal(uri, (f) -> false), UnsupportedOperationException.class);
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

    private static void resetLanguageHomes() throws ReflectiveOperationException {
        Class<?> languageCache = Class.forName("com.oracle.truffle.polyglot.LanguageCache");
        Method reset = languageCache.getDeclaredMethod("resetNativeImageCacheLanguageHomes");
        reset.setAccessible(true);
        reset.invoke(null);
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T silenceException(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }

    public static class BaseDetector implements TruffleFile.FileTypeDetector {

        private static Map<Class<? extends BaseDetector>, BaseDetector> INSTANCES = new HashMap<>();

        private int findMimeTypeCalled;
        private String mimeType;
        private Charset encoding;
        private Predicate<? super TruffleFile> recognizer;

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
        public String findMimeType(TruffleFile file) throws IOException {
            if (getRecognizer().test(file)) {
                findMimeTypeCalled++;
                return mimeType;
            } else {
                return null;
            }
        }

        @Override
        public Charset findEncoding(TruffleFile file) throws IOException {
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

    @TruffleLanguage.Registration(id = "InternalTruffleFileTestLanguage", name = "InternalTruffleFileTestLanguage", characterMimeTypes = "text/x-internal-file-test")
    public static final class InternalTruffleFileTestLanguage extends ProxyLanguage {

        public InternalTruffleFileTestLanguage() {
            /*
             * Whenever the language is not used as a standalone language, e.g. it is used in
             * AbstractPolyglotTest#setupEnv, or the delegation is set directly by
             * ProxyLanguage#setDelegate, we cannot set wrapper to false for all instances, because
             * the instance created by Truffle framework must delegate to the instance set by
             * ProxyLanguage#setDelegate.
             */
            if (ProxyLanguage.getDelegate().getClass() == ProxyLanguage.class) {
                wrapper = false;
            }
        }

        public String getHome() {
            return getLanguageHome();
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

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
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

            private SeekableByteChannel delegate;

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
