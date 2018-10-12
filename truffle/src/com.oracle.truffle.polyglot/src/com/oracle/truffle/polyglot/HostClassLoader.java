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
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

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
        private volatile SeekableByteChannel channel;
        /**
         * Cache for fast resource lookup. Map of folder to files in the folder.
         */
        private volatile Map<String, Map<String, ZipUtils.Info>> content;

        JarLoader(TruffleFile root) {
            this.root = root;
        }

        @Override
        public Resource findResource(String name) {
            String[] parts = split(name);
            try {
                Map<String, ZipUtils.Info> folderContent = getResourceMap().get(parts[0]);
                if (folderContent == null) {
                    return null;
                }
                ZipUtils.Info info = folderContent.get(parts[1]);
                if (info == null) {
                    return null;
                }

                return new Resource() {

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
                        return info.size;
                    }

                    @Override
                    InputStream getInputStream() throws IOException {
                        return ZipUtils.getInputStream(getChannel(), info.offset);
                    }
                };
            } catch (IOException ioe) {
                return null;
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (channel != null) {
                channel.close();
            }
        }

        private SeekableByteChannel getChannel() throws IOException {
            SeekableByteChannel res = channel;
            if (res == null) {
                synchronized (this) {
                    res = channel;
                    if (res == null) {
                        res = root.newByteChannel(EnumSet.of(StandardOpenOption.READ));
                        channel = res;
                    }
                }
            }
            return res;
        }

        private Map<String, Map<String, ZipUtils.Info>> getResourceMap() throws IOException {
            Map<String, Map<String, ZipUtils.Info>> res = content;
            if (res == null) {
                synchronized (this) {
                    res = content;
                    if (res == null) {
                        res = ZipUtils.readEntries(getChannel());
                        content = res;
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
         * Utility methods to read zip file.
         */
        static final class ZipUtils {

            private static final int LIMIT = 1 << 16;
            private static final long UINT32_MAX_VALUE = 0xffffffffL;
            private static final int UINT16_MAX_VALUE = 0xffff;

            private ZipUtils() {
                throw new IllegalStateException("No instance allowed.");
            }

            /**
             * Adapts {@link SeekableByteChannel} to {@link InputStream}.
             */
            private static class ChannelInputStream extends InputStream {

                private final SeekableByteChannel channel;
                private final long len;

                ChannelInputStream(SeekableByteChannel channel) throws IOException {
                    this.channel = channel;
                    this.len = channel.size();
                }

                ChannelInputStream(SeekableByteChannel channel, long len) throws IOException {
                    assert channel != null;
                    assert len >= 0;
                    this.channel = channel;
                    this.len = channel.position() + len;
                }

                @Override
                public int read(byte[] data, int offset, int size) throws IOException {
                    int rem = available();
                    if (rem == 0) {
                        return -1;
                    }
                    int rlen = size < rem ? size : rem;
                    ByteBuffer buffer = ByteBuffer.wrap(data, offset, rlen);
                    return this.channel.read(buffer);
                }

                @Override
                public int read() throws java.io.IOException {
                    if (available() == 0) {
                        return -1;
                    } else {
                        ByteBuffer buffer = ByteBuffer.allocate(1);
                        channel.read(buffer);
                        buffer.flip();
                        return buffer.get();
                    }
                }

                @Override
                public int available() throws IOException {
                    return (int) (len - this.channel.position());
                }
            }

            static final class Info {
                /**
                 * Offset of a zip entry in the zip file.
                 */
                final long offset;
                /**
                 * Uncompressed size of the zipped file.
                 */
                final long size;

                Info(long offset, long size) {
                    this.offset = offset;
                    this.size = size;
                }
            }

            /**
             * Returns an {@link InputStream} for zip entry in given channel starting at given
             * offset.
             *
             * @param channel the channel to read the entry from
             * @param offset the starting offset
             * @return the {@link InputStream} to read the uncompressed zipped file content
             * @throws IOException in case of IO error
             */
            static InputStream getInputStream(SeekableByteChannel channel, long offset) throws IOException {
                channel.position(offset);
                ZipInputStream in = new ZipInputStream(new ChannelInputStream(channel));
                ZipEntry e = in.getNextEntry();
                if (e != null && e.getCrc() == 0L && e.getMethod() == ZipEntry.STORED) {
                    in.close();
                    return new ChannelInputStream(channel, e.getSize());
                }
                return in;
            }

            /**
             * Reads the zip file central table into map of folders.
             *
             * @param channel to read from
             * @return the map mapping folders to simple names and {@link Info infos}
             * @throws IOException in case of IO error
             */
            static Map<String, Map<String, Info>> readEntries(SeekableByteChannel channel) throws IOException {
                long size = (int) channel.size();
                channel.position(size - ZipFile.ENDHDR);

                ByteBuffer data = ByteBuffer.allocate(ZipFile.ENDHDR);
                data.order(ByteOrder.LITTLE_ENDIAN);
                int giveup = 0;

                do {
                    data.clear();
                    if (readFully(channel, data) != ZipFile.ENDHDR) {
                        throw new IOException();
                    }
                    channel.position(channel.position() - (ZipFile.ENDHDR + 1));
                    giveup++;
                    if (giveup > LIMIT) {
                        throw new IOException();
                    }
                } while (getsig(data) != ZipFile.ENDSIG);

                long censize = endsiz(data);
                long cenoff = endoff(data);
                channel.position(cenoff);

                Map<String, Map<String, Info>> result = new HashMap<>();
                int cenread = 0;
                data = ByteBuffer.allocate(ZipFile.CENHDR);
                data.order(ByteOrder.LITTLE_ENDIAN);
                while (cenread < censize) {
                    data.clear();
                    if (readFully(channel, data) != ZipFile.CENHDR) {
                        throw new IOException("No central table");         // NOI18N
                    }
                    if (getsig(data) != ZipFile.CENSIG) {
                        throw new IOException("No central table");          // NOI18N
                    }
                    int cennam = cennam(data);
                    int cenext = cenext(data);
                    int cencom = cencom(data);
                    long lhoff = cenoff(data);
                    long cenlen = cenlen(data);
                    String name = name(channel, cennam);
                    int seekby = cenext + cencom;
                    int cendatalen = ZipFile.CENHDR + cennam + seekby;
                    cenread += cendatalen;
                    if (!isDirectory(name)) {
                        String[] parts = split(name);
                        Map<String, ZipUtils.Info> names = result.computeIfAbsent(parts[0], new Function<String, Map<String, ZipUtils.Info>>() {
                            @Override
                            public Map<String, ZipUtils.Info> apply(String t) {
                                return new HashMap<>();
                            }
                        });
                        names.put(parts[1], new Info(lhoff, cenlen));
                    }
                    seekBy(channel, seekby);
                }
                return result;
            }

            private static String name(SeekableByteChannel channel, int cennam) throws IOException {
                ByteBuffer name = ByteBuffer.allocate(cennam);
                if (readFully(channel, name) != cennam) {
                    throw new IOException("Unexpected EOF.");
                }
                return new String(name.array(), "UTF-8");
            }

            private static boolean isDirectory(String name) {
                return name.endsWith("/");
            }

            private static int readFully(SeekableByteChannel channel, ByteBuffer buffer) throws IOException {
                int res = 0;
                while (buffer.remaining() > 0) {
                    int read = channel.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    res += read;
                }
                return res;
            }

            private static long getsig(ByteBuffer b) {
                return b.getInt(0) & UINT32_MAX_VALUE;
            }

            private static long endsiz(ByteBuffer b) {
                return b.getInt(ZipFile.ENDSIZ) & UINT32_MAX_VALUE;
            }

            private static long endoff(ByteBuffer b) {
                return b.getInt(ZipFile.ENDOFF) & UINT32_MAX_VALUE;
            }

            private static long cenlen(ByteBuffer b) {
                return b.getInt(ZipFile.CENLEN) & UINT32_MAX_VALUE;
            }

            private static int cennam(ByteBuffer b) {
                return b.getShort(ZipFile.CENNAM) & UINT16_MAX_VALUE;
            }

            private static int cenext(ByteBuffer b) {
                return b.getShort(ZipFile.CENEXT) & UINT16_MAX_VALUE;
            }

            private static int cencom(ByteBuffer b) {
                return b.getShort(ZipFile.CENCOM) & UINT16_MAX_VALUE;
            }

            private static long cenoff(ByteBuffer b) {
                return b.getInt(ZipFile.CENOFF) & UINT32_MAX_VALUE;
            }

            private static void seekBy(final SeekableByteChannel ch, int offset) throws IOException {
                ch.position(ch.position() + offset);
            }
        }
    }
}
