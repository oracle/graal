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

import com.oracle.truffle.api.TruffleLanguage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class LanguageCacheTest {

    @Test
    public void testDuplicateLanguageIds() throws Throwable {
        ClassLoader testClassLoader = new TestClassLoader();
        try {
            invokeLanguageCacheCreateLanguages(testClassLoader);
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException ise) {
            // Expected exception
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeLanguageCacheCreateLanguages(ClassLoader loader) throws Throwable {
        try {
            final Class<?> langCacheClz = Class.forName("com.oracle.truffle.polyglot.LanguageCache", true, LanguageCacheTest.class.getClassLoader());
            final Method createLanguages = langCacheClz.getDeclaredMethod("createLanguages", ClassLoader.class);
            createLanguages.setAccessible(true);
            return (Map<String, Object>) createLanguages.invoke(null, loader);
        } catch (InvocationTargetException ite) {
            throw ite.getCause();
        } catch (ReflectiveOperationException re) {
            throw new RuntimeException(re);
        }
    }

    @TruffleLanguage.Registration(id = DuplicateIdLanguage.ID, name = DuplicateIdLanguage.ID, version = "1.0")
    public static final class DuplicateIdLanguage extends TruffleLanguage<Void> {
        static final String ID = "DuplicateIdLanguage";

        @Override
        protected Void createContext(Env env) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }
    }

    /**
     * Inverse {@link ClassLoader} for loading second instance of {@code DuplicateIdLanguage}. This
     * classloader delegates all requests to its parent except of loading of
     * {@code DuplicateIdLanguage} class.
     */
    private static final class TestClassLoader extends ClassLoader {

        TestClassLoader() {
            super(TestClassLoader.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!DuplicateIdLanguage.class.getName().equals(name)) {
                return super.loadClass(name, resolve);
            } else {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        c = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!DuplicateIdLanguage.class.getName().equals(name)) {
                throw new IllegalArgumentException("Only " + DuplicateIdLanguage.class + " can be loaded.");
            }
            try {
                URL location = DuplicateIdLanguage.class.getProtectionDomain().getCodeSource().getLocation();
                Path path = Paths.get(location.toURI());
                if (Files.isRegularFile(path)) {
                    location = new URL("jar:" + location.toExternalForm() + "!/" + binaryName(DuplicateIdLanguage.class.getName()) + ".class");
                } else {
                    location = new URL(location.toExternalForm() + binaryName(DuplicateIdLanguage.class.getName()) + ".class");
                }
                try (InputStream in = location.openStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    copy(in, out);
                    byte[] content = out.toByteArray();
                    definePackage(name);
                    return defineClass(name, content, 0, content.length);
                }
            } catch (URISyntaxException | IOException e) {
                throw new ClassNotFoundException("Cannot load class: " + name, e);
            }
        }

        @SuppressWarnings("deprecation")
        private void definePackage(String className) {
            String packageName = getPackageName(className);
            if (getPackage(packageName) == null) {
                definePackage(packageName, null, null, null, null, null, null, null);
            }
        }

        private static void copy(InputStream in, OutputStream out) throws IOException {
            byte[] buffer = new byte[4096];
            while (true) {
                int read = in.read(buffer, 0, buffer.length);
                if (read == -1) {
                    return;
                }
                out.write(buffer, 0, read);
            }
        }

        private static String getPackageName(String className) {
            int lastDot = className.lastIndexOf('.');
            return lastDot == -1 ? "" : className.substring(0, lastDot);
        }

        private static String binaryName(String name) {
            return name.replace(".", "/");
        }
    }
}
