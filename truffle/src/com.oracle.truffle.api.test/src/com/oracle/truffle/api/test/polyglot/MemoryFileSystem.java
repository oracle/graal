/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.AbstractMap;
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
import java.util.Set;
import java.util.function.BiConsumer;
import org.graalvm.polyglot.io.FileSystem;

public final class MemoryFileSystem implements FileSystem {
    private static final byte[] EMPTY = new byte[0];
    private static final UserPrincipal USER = new UserPrincipal() {
        @Override
        public String getName() {
            return "";
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object other) {
            return other == USER;
        }
    };
    private static final GroupPrincipal GROUP = new GroupPrincipal() {
        @Override
        public String getName() {
            return "";
        }

        @Override
        public int hashCode() {
            return 0;
        }
    };

    private final Map<Long, FileInfo> inodes;
    private final Map<Long, byte[]> blocks;
    private final Path root;
    private final Path tmpDir;
    private volatile Path userDir;
    private long nextInode = 0;

    public MemoryFileSystem() throws IOException {
        this("/tmp");
    }

    public MemoryFileSystem(String tmpDirPath) throws IOException {
        this.inodes = new HashMap<>();
        this.blocks = new HashMap<>();
        root = MemoryPath.getRootDirectory();
        userDir = root;
        createDirectoryImpl();
        tmpDir = root.resolve(tmpDirPath);
        createDirectory(tmpDir);
    }

    @Override
    public Path parsePath(String path) {
        return new MemoryPath(Paths.get(path));
    }

    @Override
    public Path parsePath(URI uri) {
        try {
            return new MemoryPath(Paths.get(uri));
        } catch (IllegalArgumentException | FileSystemNotFoundException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        final Path absolutePath = toAbsolutePath(path);
        final Long inode = findInode(absolutePath);
        if (inode == null) {
            throw new NoSuchFileException(path.toString());
        }
        final FileInfo info = inodes.get(inode);
        for (AccessMode mode : modes) {
            if (!info.permissions.contains(mode)) {
                throw new AccessDeniedException(path.toString());
            }
        }
    }

    @Override
    public void delete(Path path) throws IOException {
        final Path absolutePath = toAbsolutePath(path);
        final Path parentPath = absolutePath.getParent();
        if (parentPath == null) {
            throw new IOException("Cannot delete root.");
        }
        Map.Entry<Long, Map<String, Long>> e = readDir(parentPath);
        final long inode = e.getKey();
        final Map<String, Long> dirents = e.getValue();
        if (!inodes.get(inode).permissions.contains(AccessMode.WRITE)) {
            throw new IOException("Read only dir: " + path);
        }
        final String fileName = absolutePath.getFileName().toString();
        final Long fileInode = dirents.get(fileName);
        if (fileInode == null) {
            throw new NoSuchFileException(path.toString());
        }
        if (inodes.get(fileInode).isDirectory()) {
            if (!readDir(fileInode).isEmpty()) {
                throw new DirectoryNotEmptyException(path.toString());
            }
        }
        inodes.remove(fileInode);
        blocks.remove(fileInode);
        dirents.remove(fileName);
        writeDir(inode, dirents);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        final Long inode = findInode(toAbsolutePath(dir));
        FileInfo fileInfo;
        if (inode == null || !(fileInfo = inodes.get(inode)).isDirectory()) {
            throw new NotDirectoryException(dir.toString() + " is not a directory.");
        }
        if (!fileInfo.permissions.contains(AccessMode.READ)) {
            throw new IOException("Cannot read dir: " + dir);
        }
        return new DirectoryStreamImpl(dir, readDir(inode).keySet());
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        if (options.contains(StandardOpenOption.APPEND) && options.contains(StandardOpenOption.READ)) {
            throw new IllegalArgumentException("READ + APPEND not allowed.");
        }
        if (options.contains(StandardOpenOption.SYNC) || options.contains(StandardOpenOption.DSYNC)) {
            throw new IllegalArgumentException("Not supported yet.");
        }
        final Path absolutePath = toAbsolutePath(path);
        final Path parentPath = absolutePath.getParent();
        if (parentPath == null) {
            throw new IOException(path.toString() + " is a directory.");
        }
        boolean read = options.contains(StandardOpenOption.READ);
        boolean write = options.contains(StandardOpenOption.WRITE);
        boolean append = options.contains(StandardOpenOption.APPEND);
        if (!read && !write) {
            if (append) {
                write = true;
            } else {
                read = true;
            }
        }
        final Map.Entry<Long, Map<String, Long>> e = readDir(parentPath);
        final long parentInode = e.getKey();
        final Map<String, Long> parentDirents = e.getValue();
        final String fileName = absolutePath.getFileName().toString();
        Long inode = parentDirents.get(fileName);
        if (inode == null) {
            if (!options.contains(StandardOpenOption.WRITE) || !(options.contains(StandardOpenOption.CREATE) || options.contains(StandardOpenOption.CREATE_NEW))) {
                throw new NoSuchFileException(path.toString());
            }
            if (!inodes.get(parentInode).permissions.contains(AccessMode.WRITE)) {
                throw new IOException("Read only dir: " + path);
            }
            inode = nextInode++;
            inodes.put(inode, FileInfo.newBuilder(FileType.FILE).permissions(attrs).build());
            blocks.put(inode, EMPTY);
            parentDirents.put(fileName, inode);
            writeDir(parentInode, parentDirents);
        } else {
            if (options.contains(StandardOpenOption.CREATE_NEW)) {
                throw new FileAlreadyExistsException(path.toString());
            }
            final FileInfo fileInfo = inodes.get(inode);
            if (!fileInfo.isFile()) {
                throw new IOException(path.toString() + " is a directory.");
            }
            if (read && !fileInfo.permissions.contains(AccessMode.READ)) {
                throw new IOException("Cannot read: " + path);
            }
            if (write && !fileInfo.permissions.contains(AccessMode.WRITE)) {
                throw new IOException("Read only: " + path);
            }
        }
        final boolean deleteOnClose = options.contains(StandardOpenOption.DELETE_ON_CLOSE);
        final byte[] origData = blocks.get(inode);
        final byte[] data = write && options.contains(StandardOpenOption.TRUNCATE_EXISTING) ? EMPTY : Arrays.copyOf(origData, origData.length);
        final long inodeFin = inode;
        final BiConsumer<byte[], Long> syncAction = new BiConsumer<byte[], Long>() {
            @Override
            public void accept(byte[] t, Long u) {
                blocks.put(inodeFin, Arrays.copyOf(t, (int) u.longValue()));
            }
        };
        final boolean readFin = read;
        final boolean writeFin = write;
        final BiConsumer<byte[], Long> metaSyncAction = new BiConsumer<byte[], Long>() {
            @Override
            public void accept(byte[] t, Long u) {
                final long time = System.currentTimeMillis();
                final FileInfo fileInfo = inodes.get(inodeFin);
                if (readFin) {
                    fileInfo.atime = time;
                }
                if (writeFin) {
                    fileInfo.mtime = time;
                }
            }
        };
        final BiConsumer<byte[], Long> closeAction = new BiConsumer<byte[], Long>() {
            @Override
            public void accept(byte[] t, Long u) {
                if (deleteOnClose) {
                    try {
                        delete(absolutePath);
                    } catch (IOException ioe) {
                        sthrow(ioe);
                    }
                } else {
                    syncAction.accept(t, u);
                    metaSyncAction.accept(t, u);
                }
            }

            @SuppressWarnings("unchecked")
            private <E extends Throwable> void sthrow(Throwable t) throws E {
                throw (E) t;
            }
        };
        return new ChannelImpl(
                        data,
                        closeAction,
                        read,
                        write,
                        append);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        final Path absolutePath = toAbsolutePath(path);
        Path parentPath = absolutePath.getParent();
        if (parentPath == null) {
            parentPath = absolutePath;
        }
        checkAccess(parentPath, EnumSet.of(AccessMode.READ));
        Long inode = findInode(absolutePath);
        if (inode == null) {
            throw new NoSuchFileException(path.toString());
        }
        final FileInfo fileInfo = inodes.get(inode);
        final int size = blocks.get(inode).length;
        final Object[] parsedAttributes = parse(attributes);
        switch ((String) parsedAttributes[0]) {
            case "basic":
                return new BasicFileAttributes(inode, fileInfo, size).asMap((String[]) parsedAttributes[1]);
            case "posix":
                return new PermissionsAttributes(inode, fileInfo, size).asMap((String[]) parsedAttributes[1]);
            default:
                throw new UnsupportedOperationException((String) parsedAttributes[0]);
        }
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        final Object[] parsedAttributes = parse(attribute);
        final String[] attributeNames = (String[]) parsedAttributes[1];
        if (attributeNames.length != 1) {
            throw new IllegalArgumentException(attribute);
        }
        final Path absolutePath = toAbsolutePath(path);
        Path parentPath = absolutePath.getParent();
        if (parentPath == null) {
            parentPath = absolutePath;
        }
        checkAccess(parentPath, EnumSet.of(AccessMode.WRITE));
        Long inode = findInode(absolutePath);
        if (inode == null) {
            throw new NoSuchFileException(path.toString());
        }
        final FileInfo fileInfo = inodes.get(inode);
        final int size = blocks.get(inode).length;
        switch ((String) parsedAttributes[0]) {
            case "basic":
                new BasicFileAttributes(inode, fileInfo, size).setValue(attributeNames[0], value);
                break;
            case "posix":
                new PermissionsAttributes(inode, fileInfo, size).setValue(attributeNames[0], value);
                break;
            default:
                throw new UnsupportedOperationException((String) parsedAttributes[0]);
        }
    }

    @Override
    public Path toAbsolutePath(final Path path) {
        if (path.isAbsolute()) {
            return path;
        }
        return userDir.resolve(path);
    }

    @Override
    public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
        Objects.requireNonNull(currentWorkingDirectory, "Current working directory must be non null.");
        this.userDir = currentWorkingDirectory;
    }

    @Override
    public Path toRealPath(final Path path, LinkOption... options) throws IOException {
        return toAbsolutePath(path);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        final Path absolutePath = toAbsolutePath(dir);
        final Path parentPath = absolutePath.getParent();
        if (parentPath == null) {
            throw new IOException("Cannot create root.");
        }
        Map.Entry<Long, Map<String, Long>> e = readDir(parentPath);
        final long inode = e.getKey();
        final Map<String, Long> dirents = e.getValue();
        final String fileName = absolutePath.getFileName().toString();
        if (dirents.get(fileName) != null) {
            throw new FileAlreadyExistsException(dir.toString());
        }
        dirents.put(fileName, createDirectoryImpl(attrs));
        writeDir(inode, dirents);
    }

    @Override
    public String getSeparator() {
        return ((MemoryPath) root).delegate.getFileSystem().getSeparator();
    }

    @Override
    public Path getTempDirectory() {
        return tmpDir;
    }

    private static Object[] parse(String attributesSelector) {
        final Object[] result = new Object[2];
        int index = attributesSelector.indexOf(':');
        String names;
        if (index < 0) {
            result[0] = "basic";
            names = attributesSelector;
        } else {
            result[0] = attributesSelector.substring(0, index++);
            names = index == attributesSelector.length() ? "" : attributesSelector.substring(index);
        }
        result[1] = names.split(",");
        return result;
    }

    private long createDirectoryImpl(FileAttribute<?>... attrs) throws IOException {
        final FileInfo fileInfo = FileInfo.newBuilder(FileType.DIRECTORY).permissions(attrs).build();
        long currentInode = nextInode++;
        inodes.put(currentInode, fileInfo);
        writeDir(currentInode, Collections.emptyMap());
        return currentInode;
    }

    private Map<String, Long> readDir(long forInode) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blocks.get(forInode)))) {
            final Map<String, Long> result = new HashMap<>();
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String name = in.readUTF();
                long inode = in.readLong();
                result.put(name, inode);
            }
            return result;
        }
    }

    private void writeDir(long forInode, Map<String, Long> dir) throws IOException {
        blocks.put(forInode, serializeDir(dir));
    }

    private static byte[] serializeDir(Map<String, Long> dir) throws IOException {
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bout)) {
            out.writeInt(dir.size());
            for (Map.Entry<String, Long> e : dir.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeLong(e.getValue());
            }
            return bout.toByteArray();
        }
    }

    private Map.Entry<Long, Map<String, Long>> readDir(Path forDir) throws IOException {
        Long inode = 0L;
        Map<String, Long> dirents = readDir(inode);
        for (Path p : forDir) {
            inode = dirents.get(p.toString());
            if (inode == null) {
                throw new IOException("Parent does not exist");
            }
            final FileInfo fileInfo = inodes.get(inode);
            if (!fileInfo.isDirectory()) {
                throw new IOException("Parent is not a directory");
            }
            dirents = readDir(inode);
        }
        return new AbstractMap.SimpleImmutableEntry<>(inode, dirents);
    }

    private Long findInode(Path path) throws IOException {
        Long inode = 0L;
        final Path parentPath = path.getParent();
        if (parentPath == null) {
            return inode;
        }
        Map<String, Long> dirents = readDir(inode);
        for (Path p : parentPath) {
            inode = dirents.get(p.toString());
            if (inode == null) {
                throw new IOException("Parent does not exist");
            }
            final FileInfo fileInfo = inodes.get(inode);
            if (!fileInfo.isDirectory()) {
                throw new IOException("Parent is not a directory");
            }
            dirents = readDir(inode);
        }
        return dirents.get(path.getFileName().toString());
    }

    private enum FileType {
        FILE,
        DIRECTORY
    }

    private static final class DirectoryStreamImpl implements DirectoryStream<Path> {
        private final Path parent;
        private final Collection<? extends String> names;

        DirectoryStreamImpl(final Path parent, final Collection<? extends String> names) {
            this.parent = parent;
            this.names = names;
        }

        @Override
        public Iterator<Path> iterator() {
            return new Iterator<Path>() {
                private final Iterator<? extends String> delegate = names.iterator();

                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public Path next() {
                    return parent.resolve(delegate.next());
                }
            };
        }

        @Override
        public void close() throws IOException {
        }
    }

    private static class BasicFileAttributes {
        private static final String ATTR_MTIME = "lastModifiedTime";
        private static final String ATTR_ATIME = "lastAccessTime";
        private static final String ATTR_CTIME = "creationTime";
        private static final String ATTR_SIZE = "size";
        private static final String ATTR_FILE = "isRegularFile";
        private static final String ATTR_DIR = "isDirectory";
        private static final String ATTR_SYM_LINK = "isSymbolicLink";
        private static final String ATTR_OTHER = "isOther";
        private static final String ATTR_KEY = "fileKey";

        private static final String[] SUPPORTED_KEYS = {
                        ATTR_MTIME, ATTR_ATIME, ATTR_CTIME, ATTR_SIZE, ATTR_FILE, ATTR_DIR, ATTR_SYM_LINK, ATTR_OTHER, ATTR_KEY
        };

        private final long inode;
        final FileInfo fileInfo;
        private final int size;

        BasicFileAttributes(final long inode, final FileInfo fileInfo, final int size) {
            this.inode = inode;
            this.fileInfo = fileInfo;
            this.size = size;
        }

        final Map<String, Object> asMap(String[] requiredProperties) {
            final Set<String> requiredKeys = new HashSet<>();
            for (String requiredProperty : requiredProperties) {
                if ("*".equals(requiredProperty)) {
                    requiredKeys.addAll(getSupportedKeys());
                    break;
                }
                requiredKeys.add(requiredProperty);
            }
            final Map<String, Object> result = new HashMap<>();
            for (String requiredKey : requiredKeys) {
                final Object value = getValue(requiredKey);
                if (value != null) {
                    result.put(requiredKey, value);
                }
            }
            return result;
        }

        Set<String> getSupportedKeys() {
            final Set<String> res = new HashSet<>();
            Collections.addAll(res, SUPPORTED_KEYS);
            return res;
        }

        Object getValue(String key) {
            Object value;
            switch (key) {
                case ATTR_ATIME:
                    value = FileTime.fromMillis(fileInfo.atime);
                    break;
                case ATTR_CTIME:
                    value = FileTime.fromMillis(fileInfo.ctime);
                    break;
                case ATTR_MTIME:
                    value = FileTime.fromMillis(fileInfo.mtime);
                    break;
                case ATTR_SIZE:
                    value = (long) size;
                    break;
                case ATTR_FILE:
                    value = fileInfo.isFile();
                    break;
                case ATTR_DIR:
                    value = fileInfo.isDirectory();
                    break;
                case ATTR_SYM_LINK:
                    value = false;
                    break;
                case ATTR_OTHER:
                    value = false;
                    break;
                case ATTR_KEY:
                    value = inode;
                    break;
                default:
                    value = null;
            }
            return value;
        }

        boolean setValue(String key, Object value) {
            switch (key) {
                case ATTR_ATIME:
                    fileInfo.atime = cast(value, FileTime.class).toMillis();
                    return true;
                case ATTR_CTIME:
                    fileInfo.ctime = cast(value, FileTime.class).toMillis();
                    return true;
                case ATTR_MTIME:
                    fileInfo.mtime = cast(value, FileTime.class).toMillis();
                    return true;
                default:
                    return false;
            }
        }

        static <T> T cast(final Object object, final Class<T> clz) {
            if (clz.isInstance(object)) {
                return clz.cast(object);
            } else {
                throw new IllegalArgumentException(String.valueOf(object));
            }
        }

        @SuppressWarnings("unchecked")
        static <T> Collection<T> castCollection(final Object object, final Class<T> clz) {
            final Collection<?> c = cast(object, Collection.class);
            for (Object o : c) {
                if (!clz.isInstance(o)) {
                    throw new IllegalArgumentException(String.valueOf(object));
                }
            }
            return (Collection<T>) c;
        }
    }

    private static final class PermissionsAttributes extends BasicFileAttributes {
        private static final String ATTR_PERMISSIONS = "permissions";
        private static final String ATTR_OWNER = "owner";
        private static final String ATTR_GROUP = "group";

        PermissionsAttributes(long inode, FileInfo fileInfo, int size) {
            super(inode, fileInfo, size);
        }

        @Override
        Set<String> getSupportedKeys() {
            final Set<String> base = super.getSupportedKeys();
            base.add(ATTR_PERMISSIONS);
            base.add(ATTR_OWNER);
            base.add(ATTR_GROUP);
            return base;
        }

        @Override
        Object getValue(String key) {
            if (ATTR_PERMISSIONS.equals(key)) {
                final Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);
                if (fileInfo.permissions.contains(AccessMode.READ)) {
                    result.add(PosixFilePermission.OWNER_READ);
                }
                if (fileInfo.permissions.contains(AccessMode.WRITE)) {
                    result.add(PosixFilePermission.OWNER_WRITE);
                }
                if (fileInfo.permissions.contains(AccessMode.EXECUTE)) {
                    result.add(PosixFilePermission.OWNER_EXECUTE);
                }
                return result;
            } else if (ATTR_OWNER.equals(key)) {
                return USER;
            } else if (ATTR_GROUP.equals(key)) {
                return GROUP;
            }
            return super.getValue(key);
        }

        @Override
        boolean setValue(String key, Object value) {
            if (ATTR_PERMISSIONS.equals(key)) {
                final Collection<PosixFilePermission> c = castCollection(value, PosixFilePermission.class);
                fileInfo.permissions.clear();
                for (PosixFilePermission p : c) {
                    switch (p) {
                        case OWNER_READ:
                            fileInfo.permissions.add(AccessMode.READ);
                            break;
                        case OWNER_WRITE:
                            fileInfo.permissions.add(AccessMode.WRITE);
                            break;
                        case OWNER_EXECUTE:
                            fileInfo.permissions.add(AccessMode.EXECUTE);
                            break;
                    }
                }
                return true;
            }
            return super.setValue(key, value);
        }
    }

    private static final class ChannelImpl implements SeekableByteChannel {
        private final BiConsumer<byte[], Long> closeAction;
        private final boolean read;
        private final boolean write;
        private final boolean append;
        private byte[] data;
        private boolean closed;
        private long limit;
        private long pos;

        ChannelImpl(
                        final byte[] data,
                        final BiConsumer<byte[], Long> closeAction,
                        final boolean read,
                        final boolean write,
                        final boolean append) {
            this.data = data;
            this.closeAction = closeAction;
            this.read = read;
            this.write = write;
            this.append = append;
            this.limit = data.length;
            this.pos = append ? limit : 0;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            checkClosed();
            checkRead();
            final long available = limit - pos;
            if (available == 0) {
                return -1;
            }
            final int toRead = Math.min((int) available, dst.limit());
            dst.put(data, (int) pos, toRead);
            pos += toRead;
            return toRead;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            checkClosed();
            checkWrite();
            final int len = src.limit() - src.position();
            ensureCapacity((int) (pos + len));
            src.get(data, (int) pos, len);
            pos += len;
            if (pos > limit) {
                limit = pos;
            }
            return len;
        }

        @Override
        public long position() throws IOException {
            checkClosed();
            return pos;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            checkClosed();
            if (newPosition < 0) {
                throw new IllegalArgumentException(String.valueOf(newPosition));
            }
            pos = newPosition;
            return this;
        }

        @Override
        public long size() throws IOException {
            checkClosed();
            return limit;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            checkClosed();
            checkWrite();
            if (size < 0) {
                throw new IllegalArgumentException(String.valueOf(size));
            }
            if (append) {
                throw new IOException("Truncate not allowed in append mode.");
            }
            if (size < limit) {
                limit = size;
            }
            if (pos > limit) {
                pos = limit;
            }
            return this;
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            closeAction.accept(data, limit);
        }

        private void checkRead() {
            if (!read) {
                throw new NonReadableChannelException();
            }
        }

        private void checkWrite() {
            if (!write) {
                throw new NonWritableChannelException();
            }
        }

        private void checkClosed() throws ClosedChannelException {
            if (closed) {
                throw new ClosedChannelException();
            }
        }

        private byte[] ensureCapacity(final int requiredLength) {
            if (requiredLength > data.length) {
                data = Arrays.copyOf(data, Math.max(requiredLength, data.length << 1));
            }
            return data;
        }
    }

    private static final class FileInfo {
        private final FileType type;
        private final Set<AccessMode> permissions;
        private long ctime;
        private long mtime;
        private long atime;

        private FileInfo(FileType type, Set<AccessMode> permissions, long ctime, long mtime, long atime) {
            this.type = type;
            this.permissions = permissions;
            this.ctime = ctime;
            this.mtime = mtime;
            this.atime = atime;
        }

        boolean isFile() {
            return type == FileType.FILE;
        }

        boolean isDirectory() {
            return type == FileType.DIRECTORY;
        }

        static Builder newBuilder(final FileType type) {
            return new Builder(type);
        }

        private static final class Builder {
            private final FileType type;
            private final Set<AccessMode> permissions;
            private long ctime;
            private long mtime;
            private long atime;

            private Builder(final FileType type) {
                Objects.requireNonNull(type, "Type must be non null");
                this.type = type;
                this.ctime = this.mtime = this.atime = System.currentTimeMillis();
                this.permissions = EnumSet.of(AccessMode.READ, AccessMode.WRITE);
            }

            Builder permissions(FileAttribute<?>... attrs) {
                for (FileAttribute<?> attr : attrs) {
                    if ("posix:permissions".equals(attr.name())) {
                        this.permissions.clear();
                        @SuppressWarnings("unchecked")
                        final Set<? extends PosixFilePermission> posixFilePermissions = (Set<PosixFilePermission>) attr.value();
                        for (PosixFilePermission permission : posixFilePermissions) {
                            switch (permission) {
                                case OWNER_READ:
                                    this.permissions.add(AccessMode.READ);
                                    break;
                                case OWNER_WRITE:
                                    this.permissions.add(AccessMode.WRITE);
                                    break;
                                case OWNER_EXECUTE:
                                    this.permissions.add(AccessMode.EXECUTE);
                                    break;
                            }
                        }
                    }
                }
                return this;
            }

            FileInfo build() {
                return new FileInfo(type, permissions, ctime, mtime, atime);
            }
        }
    }

    private static final class MemoryPath implements Path {

        private final Path delegate;

        MemoryPath(Path delegate) {
            assert delegate != null;
            this.delegate = delegate;
        }

        @Override
        public java.nio.file.FileSystem getFileSystem() {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public File toFile() {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public boolean isAbsolute() {
            return delegate.isAbsolute();
        }

        @Override
        public Path getRoot() {
            Path delegateRoot = delegate.getRoot();
            return delegateRoot == null ? null : new MemoryPath(delegateRoot);
        }

        @Override
        public Path getFileName() {
            Path delegateFileName = delegate.getFileName();
            return delegateFileName == null ? null : new MemoryPath(delegateFileName);
        }

        @Override
        public Path getParent() {
            Path delegateParent = delegate.getParent();
            return delegateParent == null ? null : new MemoryPath(delegateParent);
        }

        @Override
        public int getNameCount() {
            return delegate.getNameCount();
        }

        @Override
        public Path getName(int index) {
            Path delegateName = delegate.getName(index);
            return new MemoryPath(delegateName);
        }

        @Override
        public Path subpath(int beginIndex, int endIndex) {
            Path delegateSubpath = delegate.subpath(beginIndex, endIndex);
            return new MemoryPath(delegateSubpath);
        }

        @Override
        public boolean startsWith(Path other) {
            if (other.getClass() != MemoryPath.class) {
                throw new IllegalArgumentException("Unsupported path: " + other.getClass().getName());
            }
            return delegate.startsWith(((MemoryPath) other).delegate);
        }

        @Override
        public boolean startsWith(String other) {
            return delegate.startsWith(other);
        }

        @Override
        public boolean endsWith(Path other) {
            if (other.getClass() != MemoryPath.class) {
                throw new IllegalArgumentException("Unsupported path: " + other.getClass().getName());
            }
            return delegate.endsWith(((MemoryPath) other).delegate);
        }

        @Override
        public boolean endsWith(String other) {
            return delegate.endsWith(other);
        }

        @Override
        public Path normalize() {
            return new MemoryPath(delegate.normalize());
        }

        @Override
        public Path resolve(Path other) {
            if (other.getClass() != MemoryPath.class) {
                throw new IllegalArgumentException("Unsupported path: " + other.getClass().getName());
            }
            return new MemoryPath(delegate.resolve(((MemoryPath) other).delegate));
        }

        @Override
        public Path resolve(String other) {
            return new MemoryPath(delegate.resolve(other));
        }

        @Override
        public Path resolveSibling(Path other) {
            if (other.getClass() != MemoryPath.class) {
                throw new IllegalArgumentException("Unsupported path: " + other.getClass().getName());
            }
            return new MemoryPath(delegate.resolveSibling(((MemoryPath) other).delegate));
        }

        @Override
        public Path resolveSibling(String other) {
            return new MemoryPath(delegate.resolveSibling(other));
        }

        @Override
        public Path relativize(Path other) {
            if (other.getClass() != MemoryPath.class) {
                throw new IllegalArgumentException("Unsupported path: " + other.getClass().getName());
            }
            return new MemoryPath(delegate.relativize(((MemoryPath) other).delegate));
        }

        @Override
        public URI toUri() {
            return delegate.toUri();
        }

        @Override
        public Path toAbsolutePath() {
            return new MemoryPath(delegate.toAbsolutePath());
        }

        @Override
        public Path toRealPath(LinkOption... options) throws IOException {
            return this;
        }

        @Override
        public Iterator<Path> iterator() {
            return new Iterator<Path>() {

                private final Iterator<Path> delegateIt = delegate.iterator();

                @Override
                public boolean hasNext() {
                    return delegateIt.hasNext();
                }

                @Override
                public Path next() {
                    return new MemoryPath(delegateIt.next());
                }
            };
        }

        @Override
        public int compareTo(Path other) {
            if (other.getClass() != MemoryPath.class) {
                throw new IllegalArgumentException("Unsupported path: " + other.getClass().getName());
            }
            return delegate.compareTo(((MemoryPath) other).delegate);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (other == null || other.getClass() != MemoryPath.class) {
                return false;
            }
            return delegate.equals(((MemoryPath) other).delegate);
        }

        static Path getRootDirectory() {
            List<? extends Path> rootDirectories = FileSystemsTest.getRootDirectories();
            if (rootDirectories.isEmpty()) {
                throw new IllegalStateException("No root directory.");
            }
            return new MemoryPath(rootDirectories.get(0));
        }
    }
}
