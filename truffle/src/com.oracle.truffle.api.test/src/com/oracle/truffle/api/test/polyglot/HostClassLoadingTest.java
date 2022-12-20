/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class HostClassLoadingTest extends AbstractPolyglotTest {

    static final String TEST_REPLACE_CLASS_NAME = "HostClassLoadingTestClazz1";
    private static final String TEST_REPLACE_CLASS_NAME_2 = "HostClassLoadingTestClazz2";
    private static final String TEST_REPLACE_QUALIFIED_CLASS_NAME = HostClassLoadingTestClass1.class.getPackage().getName() + ".HostClassLoadingTestClazz1";

    // static number that has the same lifetime as HostClassLoadingTestClass1.class.
    private static int hostStaticFieldValue = 42;

    @BeforeClass
    public static void skipForStrongEncapsulationAndAOT() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        TruffleTestAssumptions.assumeNotAOT();
    }

    public HostClassLoadingTest() {
        needsLanguageEnv = true;
    }

    @Test
    public void testAllowAccess() throws IOException {
        Path tempDir = setupSimpleClassPath();

        // no rights by default
        setupEnv(Context.newBuilder().build());
        try {
            // should fail loading the truffle file
            TruffleFile file = languageEnv.getPublicTruffleFile(tempDir.toString());
            assertFalse(file.isReadable());
            fail();
        } catch (SecurityException e) {
        }

        // test with only io rights
        setupEnv(Context.newBuilder().allowIO(IOAccess.ALL).build());
        TruffleFile file = languageEnv.getPublicTruffleFile(tempDir.toString());
        try {
            languageEnv.addToHostClassPath(file);
            fail();
        } catch (Exception e) {
            assertNonInternalException(e);
        }

        // test with only host access rights
        setupEnv(Context.newBuilder().allowIO(IOAccess.ALL).allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build());
        file = languageEnv.getPublicTruffleFile(tempDir.toString());
        try {
            languageEnv.addToHostClassPath(file);
            fail();
        } catch (Exception e) {
            assertNonInternalException(e);
        }

        // test with only class path add rights
        setupEnv(Context.newBuilder().allowIO(IOAccess.ALL).allowHostClassLoading(true).build());
        file = languageEnv.getPublicTruffleFile(tempDir.toString());
        try {
            // we should fail early
            languageEnv.addToHostClassPath(file);
            fail();
        } catch (Exception e) {
            assertNonInternalException(e);
        }
        try {
            // we should fail early
            languageEnv.lookupHostSymbol(TEST_REPLACE_QUALIFIED_CLASS_NAME);
            fail();
        } catch (Exception e) {
            assertNonInternalException(e);
        }

        setupEnv(Context.newBuilder().allowIO(IOAccess.ALL).allowHostClassLoading(true).allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build());
        file = languageEnv.getPublicTruffleFile(tempDir.toString());
        // we should fail early
        languageEnv.addToHostClassPath(file);

    }

    @Test
    public void testClassFilter() throws IOException {
        Path tempDir;
        tempDir = setupSimpleClassPath();

        // no rights by default
        AtomicInteger invocationCount = new AtomicInteger(0);
        setupEnv(Context.newBuilder().allowHostClassLookup((s) -> {
            invocationCount.incrementAndGet();
            assertEquals(TEST_REPLACE_QUALIFIED_CLASS_NAME, s);
            return true;
        }).allowAllAccess(true).build());

        languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(tempDir.toString()));

        assertNotNull(languageEnv.lookupHostSymbol(TEST_REPLACE_QUALIFIED_CLASS_NAME));
        // called once by the class loader and once by looking up the host symbol
        assertEquals(2, invocationCount.get());
        // now not called again. cached by class loader and internal class lookup cache
        assertNotNull(languageEnv.lookupHostSymbol(TEST_REPLACE_QUALIFIED_CLASS_NAME));
        assertEquals(2, invocationCount.get());

        setupEnv(Context.newBuilder().allowHostClassLookup((s) -> {
            invocationCount.incrementAndGet();
            assertEquals(TEST_REPLACE_QUALIFIED_CLASS_NAME, s);
            return false;
        }).allowAllAccess(true).build());

        languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(tempDir.toString()));
        try {
            languageEnv.lookupHostSymbol(TEST_REPLACE_QUALIFIED_CLASS_NAME);
            fail();
        } catch (Exception e) {
            assertNonInternalException(e);
        }

        deleteDir(tempDir);
    }

    private static void assertNonInternalException(Throwable t) {
        InteropLibrary interop = InteropLibrary.getUncached();
        assertTrue(interop.isException(t));
    }

    private static Path setupSimpleClassPath() throws IOException {
        final Class<?> hostClass = HostClassLoadingTestClass1.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        return tempDir;
    }

    @Test
    public void testJarHostClassLoading() throws IOException {
        setupEnv();

        final Class<?> hostClass = HostClassLoadingTestClass1.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        Path jar = createJar(tempDir);
        assertHostClassPath(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME, languageEnv.getPublicTruffleFile(jar.toString()));
        Files.deleteIfExists(jar);
        deleteDir(tempDir);
    }

    @Test
    public void testDirectoryHostClassLoading() throws IOException {
        setupEnv();
        final Class<?> hostClass = HostClassLoadingTestClass1.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        assertHostClassPath(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME, languageEnv.getPublicTruffleFile(tempDir.toString()));
        deleteDir(tempDir);
    }

    @Test
    public void testMemoryFileSystemJarHostClassLoading() throws IOException {
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID).allowAllAccess(true).allowIO(ioAccess).build());
        Class<?> hostClass = HostClassLoadingTestClass1.class;
        TruffleFile root = createClassFolderOnPolyglotFileSystem(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME);
        assertHostClassPath(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME, root);
    }

    @Test
    public void testMemoryFileSystemDirectoryHostClassLoading() throws IOException {
        IOAccess ioAccess = IOAccess.newBuilder().fileSystem(new MemoryFileSystem()).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID).allowAllAccess(true).allowIO(ioAccess).build());
        Class<?> hostClass = HostClassLoadingTestClass1.class;
        TruffleFile root = createJarOnPolyglotFileSystem(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME);
        assertHostClassPath(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME, root);
    }

    @Test
    public void testMultipleClassPathRoots() throws IOException {
        setupEnv();
        final Class<?> hostClass = HostClassLoadingTestClass1.class;
        Path tempDir1 = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        Path tempDir2 = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME_2);
        Path jar1 = createJar(tempDir1);
        Path jar2 = createJar(tempDir2);
        assertHostClassPath(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME, languageEnv.getPublicTruffleFile(jar1.toString()));
        assertHostClassPath(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME_2, languageEnv.getPublicTruffleFile(jar2.toString()));
        Files.deleteIfExists(jar1);
        Files.deleteIfExists(jar2);
        deleteDir(tempDir1);
        deleteDir(tempDir2);
    }

    @Test
    public void testResourceOrder() throws IOException {
        setupEnv();
        Class<?> hostClass = HostClassLoadingTestClass2.class;
        Path tempDir1 = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        hostClass = HostClassLoadingTestClass1.class;
        Path tempDir2 = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        Path jar1 = createJar(tempDir1);
        Path jar2 = createJar(tempDir2);
        languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(jar1.toString()));
        languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(jar2.toString()));
        Object newSymbol = languageEnv.lookupHostSymbol(hostClass.getPackage().getName() + "." + TEST_REPLACE_CLASS_NAME);
        assertEquals(1, read(newSymbol, "staticField"));
        Files.deleteIfExists(jar1);
        Files.deleteIfExists(jar2);
        deleteDir(tempDir1);
        deleteDir(tempDir2);
    }

    @Test
    public void testLoadingFromContextClassLoader() throws Exception {
        Class<?> hostClass = HostClassLoadingTestClass1.class;
        Path tempDir1 = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        Path tempDir2 = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME_2);
        Path jar1 = createJar(tempDir1);
        Path jar2 = createJar(tempDir2);
        try {
            URLClassLoader hostClassLoader = new URLClassLoader(new URL[]{jar1.toUri().toURL()});
            try {
                setupEnv(Context.newBuilder().allowAllAccess(true).hostClassLoader(hostClassLoader).build());
                languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(jar2.toString()));

                Object newSymbol = languageEnv.lookupHostSymbol(hostClass.getPackage().getName() + "." + TEST_REPLACE_CLASS_NAME);
                assertEquals(42, read(newSymbol, "staticField"));

                newSymbol = languageEnv.lookupHostSymbol(hostClass.getPackage().getName() + "." + TEST_REPLACE_CLASS_NAME_2);
                assertEquals(42, read(newSymbol, "staticField"));
            } finally {
                hostClassLoader.close();
            }
        } finally {
            Files.deleteIfExists(jar1);
            Files.deleteIfExists(jar2);
            deleteDir(tempDir1);
            deleteDir(tempDir2);
        }
    }

    @Test
    public void testMultipleJarStreams() throws IOException, InteropException {
        setupEnv();

        final Class<?> hostClass = HostClassLoadingTestClass3.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        Path jar = createJar(tempDir);
        try {
            languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(jar.toString()));

            Object statics = languageEnv.lookupHostSymbol(hostClass.getPackage().getName() + "." + TEST_REPLACE_CLASS_NAME);
            Object result = InteropLibrary.getUncached(statics).invokeMember(statics, "testMethod");

            try (InputStream stream = hostClass.getResourceAsStream("/" + HostClassLoadingTestClass3.class.getName().replace('.', '/') + ".class")) {
                assertNotNull(stream);
                assertEquals(HostClassLoadingTestClass3.countBytes(stream), result);
            }
        } finally {
            Files.deleteIfExists(jar);
            deleteDir(tempDir);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testNoClassDefFoundError() throws IOException {
        Class<?> hostClass = HostClassLoadingTestClass1.class;
        String simpleName = hostClass.getSimpleName();
        URL hostClassURL = hostClass.getResource(simpleName.concat(".class"));
        assertNotNull(hostClassURL);
        String urlStr = hostClassURL.toString();
        URL baseURL = new URL(hostClassURL, urlStr.substring(0, urlStr.lastIndexOf('/') + 1));
        assertEquals(hostClassURL.toString(), baseURL.toString().concat(simpleName.concat(".class")));

        ClassLoader mockClassLoader = new URLClassLoader(new URL[]{baseURL}, null);

        setupEnv(Context.newBuilder().hostClassLoader(mockClassLoader).allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build());
        assertFails(() -> languageEnv.lookupHostSymbol(simpleName), AbstractTruffleException.class, (e) -> {
            assertNonInternalException(e);
            assertTrue(e.getMessage().contains(String.format("Access to host class %s is not allowed or does not exist.", simpleName)));
        });
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testClassFormatError() throws IOException {
        Class<?> hostClass = HostClassLoadingTestClass1.class;
        String qualifiedName = hostClass.getName();
        URL hostClassURL = hostClass.getResource(hostClass.getSimpleName().concat(".class"));
        assertNotNull(hostClassURL);
        String hostClassFilePath = qualifiedName.replace('.', '/').concat(".class");
        String urlStr = hostClassURL.toString();
        assertTrue(urlStr.endsWith(hostClassFilePath));
        URL baseURL = new URL(hostClassURL, urlStr.substring(0, urlStr.length() - hostClassFilePath.length()));
        assertEquals(hostClassURL.toString(), baseURL.toString().concat(hostClassFilePath));

        ClassLoader mockClassLoader = new URLClassLoader(new URL[]{hostClassURL}, null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(qualifiedName)) {
                    throw new ClassFormatError("bad class");
                } else {
                    return super.findClass(name);
                }
            }
        };

        setupEnv(Context.newBuilder().hostClassLoader(mockClassLoader).allowHostAccess(HostAccess.ALL).allowHostClassLookup((String s) -> true).build());
        assertFails(() -> languageEnv.lookupHostSymbol(qualifiedName), AbstractTruffleException.class, (e) -> {
            assertNonInternalException(e);
            assertThat(e.getMessage(), containsString(String.format("Access to host class %s is not allowed or does not exist.", qualifiedName)));
        });
    }

    @Test
    public void testInterruptedThread() throws IOException {
        setupEnv();
        final Class<?> hostClass = HostClassLoadingTestClass1.class;
        Path tempDir1 = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        try {
            Path jar = createJar(tempDir1);
            try {
                String newClassName = hostClass.getPackage().getName() + "." + TEST_REPLACE_CLASS_NAME;
                assertClassNotFound(languageEnv, newClassName);
                languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(jar.toString()));
                Thread.currentThread().interrupt();
                try {
                    assertFails(() -> languageEnv.lookupHostSymbol(newClassName), AbstractTruffleException.class, (t) -> {
                        assertTrue(t.getMessage().contains(String.format("Access to host class %s is not allowed or does not exist.", newClassName)));
                    });
                } finally {
                    Thread.interrupted();   // Clean interrupted
                }
                assertNotNull(languageEnv.lookupHostSymbol(newClassName));
            } finally {
                Files.deleteIfExists(jar);
            }
        } finally {
            deleteDir(tempDir1);
        }
    }

    @Test
    public void testResourceBundleJar() throws IOException {
        setupEnv();
        final Class<?> hostClass = HostClassLoadingTestClass4.class;
        String newClassName = hostClass.getPackage().getName() + "." + TEST_REPLACE_CLASS_NAME;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        try {
            Files.write(tempDir.resolve("bundle.properties"), Collections.singleton("key=value"));
            Path jar = createJar(tempDir);
            try {
                languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(jar.toString()));
                Object testClass = languageEnv.lookupHostSymbol(newClassName);
                assertEquals("value", execute(read(testClass, "testMethod"), "bundle", "key"));
            } finally {
                Files.deleteIfExists(jar);
            }
        } finally {
            deleteDir(tempDir);
        }
    }

    @Test
    public void testResourceBundleFolder() throws IOException {
        setupEnv();
        final Class<?> hostClass = HostClassLoadingTestClass4.class;
        String newClassName = hostClass.getPackage().getName() + "." + TEST_REPLACE_CLASS_NAME;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        try {
            Files.write(tempDir.resolve("bundle.properties"), Collections.singleton("key=value"));
            languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(tempDir.toString()));
            Object testClass = languageEnv.lookupHostSymbol(newClassName);
            assertEquals("value", execute(read(testClass, "testMethod"), "bundle", "key"));
        } finally {
            deleteDir(tempDir);
        }
    }

    @Test
    public void testProtectionDomainJar() throws IOException {
        setupEnv();
        Class<?> hostClass = HostClassLoadingTestClass1.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        Path jar = createJar(tempDir);
        languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(jar.toString()));
        Object newSymbol = languageEnv.lookupHostSymbol(hostClass.getPackage().getName() + "." + TEST_REPLACE_CLASS_NAME);
        Class<?> clz = (Class<?>) languageEnv.asHostObject(newSymbol);
        ProtectionDomain protectionDomain = clz.getProtectionDomain();
        assertNotNull(protectionDomain);
        CodeSource codeSource = protectionDomain.getCodeSource();
        assertNotNull(codeSource);
        assertEquals(jar.toUri().toURL().toString(), codeSource.getLocation().toString());
        Files.deleteIfExists(jar);
        deleteDir(tempDir);
    }

    @Test
    public void testProtectionDomainFolder() throws IOException {
        setupEnv();
        Class<?> hostClass = HostClassLoadingTestClass1.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        languageEnv.addToHostClassPath(languageEnv.getPublicTruffleFile(tempDir.toString()));
        Object newSymbol = languageEnv.lookupHostSymbol(hostClass.getPackage().getName() + "." + TEST_REPLACE_CLASS_NAME);
        Class<?> clz = (Class<?>) languageEnv.asHostObject(newSymbol);
        ProtectionDomain protectionDomain = clz.getProtectionDomain();
        assertNotNull(protectionDomain);
        CodeSource codeSource = protectionDomain.getCodeSource();
        assertNotNull(codeSource);
        assertEquals(tempDir.toUri().toURL().toString(), codeSource.getLocation().toString());
        deleteDir(tempDir);
    }

    private static void assertHostClassPath(Env env, final Class<?> hostClass, String newName, TruffleFile classPathEntry) {
        String newClassName = hostClass.getPackage().getName() + "." + newName;

        assertClassNotFound(env, newClassName);

        env.addToHostClassPath(classPathEntry);

        Object newSymbol = env.lookupHostSymbol(newClassName);
        assertHostSymbol(newSymbol, 42);

        // ensure same static field values
        newSymbol = env.lookupHostSymbol(newClassName);
        assertHostSymbol(newSymbol, 43);

        // test that we can access the parent class loader
        Object oldSymbol = env.lookupHostSymbol(hostClass.getName());
        assertHostSymbol(oldSymbol, hostStaticFieldValue++);

        oldSymbol = env.lookupHostSymbol(hostClass.getName());
        assertHostSymbol(oldSymbol, hostStaticFieldValue++);
    }

    private static void assertClassNotFound(Env env, String className) {
        assertFails(() -> env.lookupHostSymbol(className), Exception.class, (e) -> {
            assertNonInternalException(e);
        });
    }

    static Path createJar(Path directory) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        Path tempJar = Files.createTempFile("tempjar", ".jar");
        JarOutputStream target = new JarOutputStream(Files.newOutputStream(tempJar), manifest);
        addJarEntries(directory, directory, target);
        target.close();
        return tempJar;
    }

    private static void addJarEntries(Path basePath, Path source, JarOutputStream target) {
        try {
            String name = basePath.relativize(source).toString().replace("\\", "/");
            if (!name.isEmpty()) {
                if (Files.isDirectory(source) && !name.endsWith("/")) {
                    name += "/";
                }
                JarEntry entry = new JarEntry(name);
                entry.setTime(Files.getLastModifiedTime(source).toMillis());
                target.putNextEntry(entry);
                if (Files.isRegularFile(source)) {
                    target.write(Files.readAllBytes(source));
                }
                target.closeEntry();
            }
            if (Files.isDirectory(source)) {
                Files.list(source).forEach((p) -> addJarEntries(basePath, p, target));
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static void deleteDir(Path p) throws IOException {
        Files.walk(p).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }

    private static void assertHostSymbol(Object hostSymbol, int incrementValue) {
        assertEquals(incrementValue, read(hostSymbol, "staticField"));
        write(hostSymbol, "staticField", incrementValue + 1);
        assertEquals(incrementValue + 1, read(hostSymbol, "staticField"));

        Object hostInstance = newInstance(hostSymbol);
        assertEquals(42, read(hostInstance, "testField"));
        assertEquals(42, execute(read(hostInstance, "testMethod")));
    }

    private static Object read(Object o, String key) {
        try {
            return InteropLibrary.getFactory().getUncached().readMember(o, key);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static void write(Object o, String key, Object value) {
        try {
            InteropLibrary.getFactory().getUncached().writeMember(o, key, value);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newInstance(Object o, Object... args) {
        try {
            return InteropLibrary.getFactory().getUncached().instantiate(o, args);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static Object execute(Object o, Object... args) {
        try {
            return InteropLibrary.getFactory().getUncached().execute(o, args);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    static Path renameHostClass(final Class<?> hostClass, String newName) throws IOException {
        // create a temporary folder with the package directory structure
        String oldName = hostClass.getSimpleName();
        Path packagePath = Paths.get(hostClass.getPackage().getName().replace('.', '/'));
        Path classFilePath = packagePath.resolve(oldName + ".class");
        URL classFileLocation = hostClass.getResource("/" + pathToInternalName(classFilePath));

        Path tempDir = Files.createTempDirectory("testHostClassLoading");
        Path targetDir = tempDir.resolve(packagePath);
        Files.createDirectories(targetDir);

        // replace class file name in class file bytes
        byte[] bytes = read(classFileLocation);
        byte[] searchBytes = pathToInternalName(packagePath.resolve(oldName)).getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = pathToInternalName(packagePath.resolve(newName)).getBytes(StandardCharsets.UTF_8);

        // need to encode to the same number of bytes. otherwise more difficult to rename
        assert newBytes.length == searchBytes.length;

        for (int index = 0; (index = indexOfByteArray(bytes, searchBytes)) != -1;) {
            for (int i = 0; i < newBytes.length; i++) {
                bytes[i + index] = newBytes[i];
            }
        }

        // create the new class name with the new name
        Files.copy(new ByteArrayInputStream(bytes), targetDir.resolve(newName + ".class"));
        return tempDir;
    }

    private static String pathToInternalName(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static byte[] read(URL file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        try (InputStream stream = file.openStream()) {
            while ((bytesRead = stream.read(chunk)) > 0) {
                outputStream.write(chunk, 0, bytesRead);
            }
        }
        return outputStream.toByteArray();
    }

    private static int indexOfByteArray(byte[] outerArray, byte[] smallerArray) {
        for (int i = 0; i < outerArray.length - smallerArray.length + 1; ++i) {
            boolean found = true;
            for (int j = 0; j < smallerArray.length; ++j) {
                if (outerArray[i + j] != smallerArray[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    private static TruffleFile createClassFolderOnPolyglotFileSystem(Env env, Class<?> testClass, String newClassName) throws IOException {
        Path tempDir = renameHostClass(testClass, newClassName);
        try {
            TruffleFile root = env.getPublicTruffleFile("/");
            assertNotNull(root);
            TruffleFile bin = root.resolve("bin");
            bin.createDirectory();
            copyToPolyglotFileSystem(tempDir, bin);
            return bin;
        } finally {
            deleteDir(tempDir);
        }
    }

    private static TruffleFile createJarOnPolyglotFileSystem(Env env, Class<?> testClass, String newClassName) throws IOException {
        Path tempDir = renameHostClass(testClass, newClassName);
        try {
            Path jarFile = createJar(tempDir);
            try {
                TruffleFile root = env.getPublicTruffleFile("/");
                assertNotNull(root);
                copyToPolyglotFileSystem(jarFile, root);
                return root.resolve(jarFile.getFileName().toString());
            } finally {
                Files.deleteIfExists(jarFile);
            }
        } finally {
            deleteDir(tempDir);
        }
    }

    private static void copyToPolyglotFileSystem(Path source, TruffleFile targetRoot) throws IOException {
        boolean folder = Files.isDirectory(source);
        Files.walk(source).forEach((sourceFile) -> {
            try {
                TruffleFile targetFile;
                if (folder) {
                    Path relative = source.relativize(sourceFile);
                    targetFile = targetRoot.resolve(relative.toString());
                } else {
                    targetFile = targetRoot.resolve(source.getFileName().toString());
                }
                if (Files.isDirectory(sourceFile)) {
                    targetFile.createDirectories();
                } else {
                    byte[] data = Files.readAllBytes(sourceFile);
                    try (OutputStream out = targetFile.newOutputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                        out.write(data);
                    }
                }
            } catch (IOException ioe) {
                throw new AssertionError("Cannot copy test data.", ioe);
            }
        });
    }

}
