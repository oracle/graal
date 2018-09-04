/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

public class HostClassLoadingTest extends AbstractPolyglotTest {

    private static final String TEST_REPLACE_CLASS_NAME = "HostClassLoadingTestClazz";
    private static final String TEST_REPLACE_QUALIFIED_CLASS_NAME = HostClassLoadingTestClass.class.getPackage().getName() + ".HostClassLoadingTestClazz";

    // static number that has the same lifetime as HostClassLoadingTestClass.class.
    private static int hostStaticFieldValue = 42;

    @Test
    public void testAllowAccess() throws IOException {
        Path tempDir = setupSimpleClassPath();

        // no rights by default
        setupEnv(Context.newBuilder().build());
        try {
            // should fail loading the truffle file
            TruffleFile file = languageEnv.getTruffleFile(tempDir.toString());
            assertFalse(file.isReadable());
            fail();
        } catch (SecurityException e) {
        }

        // test with only io rights
        setupEnv(Context.newBuilder().allowIO(true).build());
        TruffleFile file = languageEnv.getTruffleFile(tempDir.toString());
        try {
            languageEnv.addToHostClassPath(file);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof TruffleException);
            assertFalse(((TruffleException) e).isInternalError());
        }

        // test with only host access rights
        setupEnv(Context.newBuilder().allowIO(true).allowHostAccess(true).build());
        file = languageEnv.getTruffleFile(tempDir.toString());
        try {
            languageEnv.addToHostClassPath(file);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof TruffleException);
            assertFalse(((TruffleException) e).isInternalError());
        }

        // test with only class path add rights
        setupEnv(Context.newBuilder().allowIO(true).allowHostClassLoading(true).build());
        file = languageEnv.getTruffleFile(tempDir.toString());
        try {
            // we should fail early
            languageEnv.addToHostClassPath(file);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof TruffleException);
            assertFalse(((TruffleException) e).isInternalError());
        }
        try {
            // we should fail early
            languageEnv.lookupHostSymbol(TEST_REPLACE_QUALIFIED_CLASS_NAME);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof TruffleException);
            assertFalse(((TruffleException) e).isInternalError());
        }

        setupEnv(Context.newBuilder().allowIO(true).allowHostClassLoading(true).allowHostAccess(true).build());
        file = languageEnv.getTruffleFile(tempDir.toString());
        // we should fail early
        languageEnv.addToHostClassPath(file);

    }

    @Test
    public void testClassFilter() throws IOException {
        Path tempDir;
        tempDir = setupSimpleClassPath();

        // no rights by default
        AtomicInteger invocationCount = new AtomicInteger(0);
        setupEnv(Context.newBuilder().hostClassFilter((s) -> {
            invocationCount.incrementAndGet();
            assertEquals(TEST_REPLACE_QUALIFIED_CLASS_NAME, s);
            return true;
        }).allowAllAccess(true).build());

        languageEnv.addToHostClassPath(languageEnv.getTruffleFile(tempDir.toString()));

        assertNotNull(languageEnv.lookupHostSymbol(TEST_REPLACE_QUALIFIED_CLASS_NAME));
        // called once by the class loader and once by looking up the host symbol
        assertEquals(2, invocationCount.get());
        // now not called again. cached by class loader and internal class lookup cache
        assertNotNull(languageEnv.lookupHostSymbol(TEST_REPLACE_QUALIFIED_CLASS_NAME));
        assertEquals(2, invocationCount.get());

        setupEnv(Context.newBuilder().hostClassFilter((s) -> {
            invocationCount.incrementAndGet();
            assertEquals(TEST_REPLACE_QUALIFIED_CLASS_NAME, s);
            return false;
        }).allowAllAccess(true).build());

        languageEnv.addToHostClassPath(languageEnv.getTruffleFile(tempDir.toString()));
        try {
            languageEnv.lookupHostSymbol(TEST_REPLACE_QUALIFIED_CLASS_NAME);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof TruffleException);
            assertFalse(((TruffleException) e).isInternalError());
        }

        deleteDir(tempDir);
    }

    private static Path setupSimpleClassPath() throws IOException {
        final Class<?> hostClass = HostClassLoadingTestClass.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        return tempDir;
    }

    @Test
    public void testJarHostClassLoading() throws IOException {
        setupEnv();

        final Class<?> hostClass = HostClassLoadingTestClass.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        Path jar = createJar(tempDir);
        assertHostClassPath(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME, jar);
        Files.deleteIfExists(jar);
        deleteDir(tempDir);
    }

    @Test
    public void testDirectoryHostClassLoading() throws IOException {
        setupEnv();
        final Class<?> hostClass = HostClassLoadingTestClass.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        assertHostClassPath(languageEnv, hostClass, TEST_REPLACE_CLASS_NAME, tempDir);
        deleteDir(tempDir);
    }

    private static void assertHostClassPath(Env env, final Class<?> hostClass, String newName, Path classPathEntry) {
        String newClassName = hostClass.getPackage().getName() + "." + newName;

        try {
            env.lookupHostSymbol(newClassName);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof TruffleException);
            assertFalse(((TruffleException) e).isInternalError());
        }

        env.addToHostClassPath(env.getTruffleFile(classPathEntry.toString()));

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

    private static Path createJar(Path directory) throws IOException {
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

    private static void deleteDir(Path p) throws IOException {
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
            return ForeignAccess.sendRead(Message.READ.createNode(), (TruffleObject) o, key);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static Object write(Object o, String key, Object value) {
        try {
            return ForeignAccess.sendWrite(Message.WRITE.createNode(), (TruffleObject) o, key, value);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newInstance(Object o, Object... args) {
        try {
            return ForeignAccess.sendNew(Message.NEW.createNode(), (TruffleObject) o, args);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static Object execute(Object o, Object... args) {
        try {
            return ForeignAccess.sendExecute(Message.EXECUTE.createNode(), (TruffleObject) o, args);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static Path renameHostClass(final Class<?> hostClass, String newName) throws IOException {
        // create a temporary folder with the package directory structure
        String oldName = hostClass.getSimpleName();
        Path packagePath = Paths.get(hostClass.getPackage().getName().replace('.', '/'));
        Path classFilePath = packagePath.resolve(oldName + ".class");
        URL classFileLocation = hostClass.getResource("/" + classFilePath.toString());

        Path tempDir = Files.createTempDirectory("testHostClassLoading");
        Path targetDir = tempDir.resolve(packagePath);
        Files.createDirectories(targetDir);

        // replace class file name in class file bytes
        byte[] bytes = read(classFileLocation);
        byte[] searchBytes = packagePath.resolve(oldName).toString().getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = packagePath.resolve(newName).toString().getBytes(StandardCharsets.UTF_8);

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

}
