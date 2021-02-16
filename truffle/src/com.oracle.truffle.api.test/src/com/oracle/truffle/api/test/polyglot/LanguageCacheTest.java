/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;

public class LanguageCacheTest {

    @Test
    public void testDuplicateLanguageIds() throws Throwable {
        CodeSource codeSource = LanguageCacheTest.class.getProtectionDomain().getCodeSource();
        Assume.assumeNotNull(codeSource);
        Path location = Paths.get(codeSource.getLocation().toURI());
        Function<String, List<URL>> loader = new Function<String, List<URL>>() {
            @Override
            public List<URL> apply(String binaryName) {
                try {
                    if (Files.isRegularFile(location)) {
                        return Collections.singletonList(new URL("jar:" + location.toUri().toString() + "!/" + binaryName));
                    } else {
                        return Collections.singletonList(new URL(location.toUri().toString() + binaryName));
                    }
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        ClassLoader testClassLoader = new TestClassLoader(loader);
        try {
            invokeLanguageCacheCreateLanguages(LanguageCacheTest.class.getClassLoader(), testClassLoader);
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException ise) {
            // Expected exception
        }
    }

    @Test
    public void testNestedArchives() throws Throwable {
        CodeSource codeSource = LanguageCacheTest.class.getProtectionDomain().getCodeSource();
        Assume.assumeNotNull(codeSource);
        URL location = codeSource.getLocation();
        Path source = Paths.get(location.toURI());
        Assume.assumeTrue(Files.isRegularFile(source));
        try (NestedJarLoader loader = new NestedJarLoader(source, location + "!/inner.jar!/")) {
            ClassLoader testClassLoader = new TestClassLoader(loader);
            invokeLanguageCacheCreateLanguages(testClassLoader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeLanguageCacheCreateLanguages(ClassLoader... loaders) throws Throwable {
        try {
            final Class<?> langCacheClz = Class.forName("com.oracle.truffle.polyglot.LanguageCache", true, LanguageCacheTest.class.getClassLoader());
            final Method createLanguages = langCacheClz.getDeclaredMethod("createLanguages", List.class);
            createLanguages.setAccessible(true);
            class LoaderSupplier implements Supplier<ClassLoader> {

                private final ClassLoader classLoader;

                LoaderSupplier(ClassLoader classLoader) {
                    this.classLoader = classLoader;
                }

                @Override
                public ClassLoader get() {
                    return classLoader;
                }
            }
            return (Map<String, Object>) createLanguages.invoke(null,
                            Arrays.stream(loaders).map(LoaderSupplier::new).collect(Collectors.toList()));
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

    }

    /**
     * Inverse {@link ClassLoader} for loading second instance of {@code DuplicateIdLanguage}. This
     * classloader delegates all requests to its parent except of loading of
     * {@code DuplicateIdLanguage} class.
     */
    private static final class TestClassLoader extends ClassLoader {

        private static final Set<String> IMPORTANT_RESOURCES;
        static {
            IMPORTANT_RESOURCES = new HashSet<>();
            IMPORTANT_RESOURCES.add(binaryName(DuplicateIdLanguage.class.getName()) + ".class");
            IMPORTANT_RESOURCES.add(binaryName(LanguageCacheTestDuplicateIdLanguageProvider.class.getName()) + ".class");
        }

        private final Function<String, List<URL>> loader;

        TestClassLoader(Function<String, List<URL>> loader) {
            super(TestClassLoader.class.getClassLoader());
            this.loader = loader;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!IMPORTANT_RESOURCES.contains(binaryName(name) + ".class")) {
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
            String filePath = binaryName(name) + ".class";
            if (!IMPORTANT_RESOURCES.contains(filePath)) {
                throw new IllegalArgumentException("Only " + String.join(", ", IMPORTANT_RESOURCES) + " can be loaded.");
            }
            try {
                URL location = findResource(filePath);
                if (location == null) {
                    throw new ClassNotFoundException("Cannot load class: " + name);
                }
                try (InputStream in = location.openStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    copy(in, out);
                    byte[] content = out.toByteArray();
                    definePackage(name);
                    return defineClass(name, content, 0, content.length);
                }
            } catch (IOException e) {
                throw new ClassNotFoundException("Cannot load class: " + name, e);
            }
        }

        @Override
        public URL getResource(String name) {
            if (!IMPORTANT_RESOURCES.contains(name)) {
                return super.getResource(name);
            } else {
                URL url = findResource(name);
                return url != null ? url : getParent().getResource(name);
            }
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!IMPORTANT_RESOURCES.contains(name)) {
                return super.getResources(name);
            } else {
                Enumeration<URL> e1 = findResources(name);
                Enumeration<URL> e2 = getParent().getResources(name);
                List<URL> result = new ArrayList<>();
                addAll(result, e1);
                addAll(result, e2);
                return Collections.enumeration(result);
            }
        }

        @Override
        protected URL findResource(String name) {
            try {
                Enumeration<URL> e = findResources(name);
                return e.hasMoreElements() ? e.nextElement() : null;
            } catch (IOException ioe) {
                return null;
            }
        }

        @Override
        protected Enumeration<URL> findResources(String name) throws IOException {
            return Collections.enumeration(loader.apply(name));
        }

        private static <T> void addAll(Collection<? super T> dest, Enumeration<? extends T> src) {
            while (src.hasMoreElements()) {
                dest.add(src.nextElement());
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

    /**
     * Simulates a jar file inside a container jar (war, ear) file.
     */
    private static final class NestedJarLoader implements Function<String, List<URL>>, Closeable {

        private final ZipFile zipFile;
        private final String relocation;

        private NestedJarLoader(Path delegate, String relocation) throws IOException {
            if (!relocation.endsWith("!/")) {
                throw new IllegalArgumentException("Relocation must point into an archive file.");
            }
            this.zipFile = new ZipFile(delegate.toFile());
            this.relocation = relocation;
        }

        @Override
        public List<URL> apply(String binaryName) {
            String entryName = binaryName.charAt(0) == '/' ? binaryName.substring(1) : binaryName;
            ZipEntry e = zipFile.getEntry(entryName);
            if (e != null) {
                try {
                    URL url = new URL("jar", null, -1, relocation + binaryName, new NestedJarURLStreamHandler(zipFile, e));
                    return Collections.singletonList(url);
                } catch (MalformedURLException murl) {
                    throw new RuntimeException(murl);
                }
            }
            return Collections.emptyList();
        }

        @Override
        public void close() throws IOException {
            zipFile.close();
        }

        private static final class NestedJarURLStreamHandler extends URLStreamHandler {
            private final ZipFile zipFile;
            private final ZipEntry entry;

            NestedJarURLStreamHandler(ZipFile zipFile, ZipEntry entry) {
                this.zipFile = zipFile;
                this.entry = entry;
            }

            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return new JarURLConnection(u) {

                    @Override
                    public JarFile getJarFile() throws IOException {
                        throw new UnsupportedOperationException("Not supported.");
                    }

                    @Override
                    public URL getJarFileURL() {
                        try {
                            String surl = u.toString();
                            int index = surl.lastIndexOf("!/");
                            return new URL(surl.substring(0, index));
                        } catch (MalformedURLException mue) {
                            throw new IllegalArgumentException(mue);
                        }
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return zipFile.getInputStream(entry);
                    }

                    @Override
                    public void connect() throws IOException {
                    }
                };
            }
        }
    }
}
