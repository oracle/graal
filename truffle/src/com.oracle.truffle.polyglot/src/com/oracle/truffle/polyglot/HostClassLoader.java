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
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.polyglot.HostLanguage.HostContext;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.graalvm.polyglot.io.FileSystem;

final class HostClassLoader extends ClassLoader implements Closeable {

    private final HostContext hostContext;
    private final ConcurrentMap<TruffleFile, Boolean> roots;
    private final Queue<Loader> loaders;
    private final Set<Closeable> toClose;
    private volatile boolean closed;

    HostClassLoader(HostContext context, ClassLoader parent) {
        super(parent);
        this.hostContext = context;
        this.roots = new ConcurrentHashMap<>();
        this.loaders = new ConcurrentLinkedQueue<>();
        this.toClose = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    }

    @Override
    public void close() throws IOException {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(new RuntimePermission("closeClassLoader"));
        }
        if (!closed) {
            closed = true;
            List<IOException> exceptions = new ArrayList<>();
            for (Closeable closeable : loaders) {
                try {
                    closeable.close();
                } catch (IOException ioe) {
                    exceptions.add(ioe);
                }
            }
            loaders.clear();
            roots.clear();
            synchronized (toClose) {
                for (Closeable closeable : toClose) {
                    try {
                        closeable.close();
                    } catch (IOException ioe) {
                        exceptions.add(ioe);
                    }
                }
                toClose.clear();
            }
            if (!exceptions.isEmpty()) {
                IOException first = exceptions.get(0);
                for (int i = 1; i < exceptions.size(); i++) {
                    first.addSuppressed(exceptions.get(i));
                }
                throw first;
            }
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream in = getParent().getResourceAsStream(name);
        if (in != null) {
            return in;
        }
        Resource res = findFirstResource(name);
        if (res == null) {
            return null;
        }
        try {
            in = res.getInputStream();
            toClose.add(in);
            return in;
        } catch (IOException ioe) {
            return null;
        }
    }

    @Override
    protected Class<?> findClass(String className) throws ClassNotFoundException {
        hostContext.validateClass(className);
        String resourceName = getResourceName(className);
        Resource res = findFirstResource(resourceName);
        if (res == null) {
            return super.findClass(className);
        }
        try {
            byte[] content = res.getContent();
            definePackage(className);
            return defineClass(className, content, 0, content.length);
        } catch (IOException ioe) {
            throw new ClassNotFoundException("Cannot load class: " + className, ioe);
        }
    }

    @SuppressWarnings("deprecation")
    private void definePackage(String className) {
        String packageName = getPackageName(className);
        if (getPackage(packageName) == null) {
            definePackage(packageName, null, null, null, null, null, null, null);
        }
    }

    @Override
    protected URL findResource(String name) {
        Resource res = findFirstResource(name);
        return res == null ? null : res.getURL();
    }

    private Resource findFirstResource(String name) {
        for (Loader loader : getLoaders()) {
            Resource res = loader.findResource(name);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        List<URL> resources = new ArrayList<>();
        for (Loader loader : getLoaders()) {
            Resource res = loader.findResource(name);
            URL url;
            if (res != null && (url = res.getURL()) != null) {
                resources.add(url);
            }
        }
        return Collections.enumeration(resources);
    }

    public void addClasspathRoot(TruffleFile file) {
        if (!closed) {
            if (roots.putIfAbsent(file, Boolean.TRUE) == null) {
                loaders.add(file.isRegularFile() ? new JarLoader(file) : new FolderLoader(file));
            }
        }
    }

    private Iterable<Loader> getLoaders() {
        return closed ? Collections.emptyList() : loaders;
    }

    private static String getResourceName(String className) {
        return className.replace('.', '/') + ".class";
    }

    private static String getPackageName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot == -1 ? "" : className.substring(0, lastDot);
    }

    private abstract static class Resource {

        abstract URL getURL();

        abstract URL getOwner();

        abstract long getLength() throws IOException;

        abstract InputStream getInputStream() throws IOException;

        byte[] getContent() throws IOException {
            long lenl = getLength();
            if (lenl > Integer.MAX_VALUE) {
                throw new IOException("Invalid class file size.");
            }
            byte[] res;
            int len;
            if (lenl == -1) {   // Unknown length
                len = Integer.MAX_VALUE;
                res = new byte[1 << 12];
            } else {
                len = (int) lenl;
                res = new byte[len];
            }
            try (InputStream in = getInputStream()) {
                int pos = 0;
                while (pos < len) {
                    int toRead;
                    if (pos == res.length) {    // Unknown length
                        toRead = Math.min(len - pos, res.length + 1 << 12);
                        if (toRead > 0) {
                            res = Arrays.copyOf(res, pos + toRead);
                        }
                    } else {
                        toRead = res.length - pos;
                    }
                    int read = in.read(res, pos, toRead);
                    if (read < 0) {
                        if (len == Integer.MAX_VALUE) {
                            if (res.length != pos) {
                                res = Arrays.copyOf(res, pos);
                            }
                            break;
                        } else {
                            throw new EOFException("Unexpected EOF");
                        }
                    }
                    pos += read;
                }
                return res;
            }
        }

        static URL urlOrNull(URI uri) {
            try {
                return uri.toURL();
            } catch (MalformedURLException e) {
                return null;
            }
        }
    }

    private interface Loader extends Closeable {
        Resource findResource(String name);
    }

    private static final class FolderLoader implements Loader {
        private final TruffleFile root;

        FolderLoader(TruffleFile root) {
            this.root = root;
        }

        @Override
        public Resource findResource(String name) {
            TruffleFile file = root.resolve(name);
            if (!file.isRegularFile()) {
                return null;
            }
            return new Resource() {
                @Override
                public URL getURL() {
                    return urlOrNull(file.toUri());
                }

                @Override
                public URL getOwner() {
                    return urlOrNull(root.toUri());
                }

                @Override
                public long getLength() throws IOException {
                    return file.size();
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return file.newInputStream(StandardOpenOption.READ);
                }

                @Override
                public byte[] getContent() throws IOException {
                    return file.readAllBytes();
                }
            };
        }

        @Override
        public void close() throws IOException {
        }
    }

    private static final class JarLoader implements Loader {
        private final TruffleFile root;
        private volatile JarFile jarFile;
        /**
         * Cache for fast has resource lookup. Map of folder to list of files in the folder.
         */
        private volatile Map<String, Set<String>> content;

        JarLoader(TruffleFile root) {
            this.root = root;
        }

        @Override
        public Resource findResource(String name) {
            String[] parts = split(name);
            try {
                Set<String> folderContent = getResourceMap().get(parts[0]);
                if (folderContent == null) {
                    return null;
                }
                if (!folderContent.contains(parts[1])) {
                    return null;
                }
                return new Resource() {

                    private volatile JarEntry cachedEntry;

                    @Override
                    URL getURL() {
                        StringBuilder url = new StringBuilder(root.toUri().toString());
                        if (url.charAt(url.length() - 1) != '/') {
                            url.append('/');
                        }
                        url.append(name);
                        try {
                            return new URL(url.toString());
                        } catch (MalformedURLException malformed) {
                            return null;
                        }
                    }

                    @Override
                    URL getOwner() {
                        return urlOrNull(root.toUri());
                    }

                    @Override
                    long getLength() throws IOException {
                        JarEntry entry = getEntry();
                        if (entry == null) {
                            throw new IOException("Not found: " + name);
                        }
                        return entry.getSize();
                    }

                    @Override
                    InputStream getInputStream() throws IOException {
                        JarEntry entry = getEntry();
                        if (entry == null) {
                            throw new IOException("Not found: " + name);
                        }
                        return open().getInputStream(entry);
                    }

                    private JarEntry getEntry() throws IOException {
                        JarEntry entry = cachedEntry;
                        if (entry == null) {
                            entry = open().getJarEntry(name);
                            cachedEntry = entry;
                        }
                        return entry;
                    }
                };
            } catch (IOException ioe) {
                return null;
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (jarFile != null) {
                jarFile.close();
            }
        }

        private Map<String, Set<String>> getResourceMap() throws IOException {
            Map<String, Set<String>> res = content;
            if (res == null) {
                res = new HashMap<>();
                Enumeration<JarEntry> entries = open().entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory()) {
                        String name = entry.getName();
                        String[] parts = split(name);
                        Set<String> names = res.computeIfAbsent(parts[0], new Function<String, Set<String>>() {
                            @Override
                            public Set<String> apply(String t) {
                                return new HashSet<>();
                            }
                        });
                        names.add(parts[1]);
                    }
                }
                content = res;
            }
            return res;
        }

        private JarFile open() throws IOException {
            JarFile res = jarFile;
            if (res == null) {
                synchronized (this) {
                    res = jarFile;
                    if (res == null) {
                        verifyRead(root);
                        res = new JarFile(new File(root.toUri()));
                        jarFile = res;
                    }
                }
            }
            return res;
        }

        /**
         * Splits a {@code resourceName} into folder and base file name.
         * 
         * @param resourceName the name to split
         * @return an array containing parent folder and base file name.
         */
        private static String[] split(String resourceName) {
            int index = resourceName.lastIndexOf('/');
            if (index < 0) {
                return new String[]{"", resourceName};
            } else {
                return new String[]{
                                resourceName.substring(0, index),
                                resourceName.substring(index + 1, resourceName.length())
                };
            }
        }

        /**
         * Verifies that the {@link File} can be read by the local {@link FileSystem}.
         *
         * @param file the {@link File} to check
         * @throws IOException in case of IO error
         * @throws SecurityException if {@link FileSystem} denies read operation.
         */
        private static void verifyRead(TruffleFile file) throws IOException {
            file.newInputStream(StandardOpenOption.READ).close();
        }
    }
}
