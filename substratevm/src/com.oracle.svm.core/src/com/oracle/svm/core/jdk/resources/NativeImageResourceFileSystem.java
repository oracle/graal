/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.core.jdk.resources;

import static com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystemUtil.toRegexPattern;
import static java.lang.Boolean.TRUE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;

import com.oracle.svm.core.jdk.Resources;

/**
 * <p>
 * Some parts of code from this class are copied from jdk.nio.zipfs.ZipFileSystem with small tweaks.
 * The main reason why we cannot reuse this class (without any code copying) is that we cannot
 * extend jdk.nio.zipfs.ZipPath which is the central class in the Zip file system.
 * </p>
 */
public class NativeImageResourceFileSystem extends FileSystem {

    private static final String GLOB_SYNTAX = "glob";
    private static final String REGEX_SYNTAX = "regex";

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private static final Set<String> supportedFileAttributeViews = Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList("basic", "resource")));

    private final Set<InputStream> inputStreams = Collections.synchronizedSet(new HashSet<>());
    private final Set<OutputStream> outputStreams = Collections.synchronizedSet(new HashSet<>());
    private final Set<Path> tmpPaths = Collections.synchronizedSet(new HashSet<>());

    private final long defaultTimestamp = System.currentTimeMillis();

    private final NativeImageResourceFileSystemProvider provider;
    private final Path resourcePath;
    private final NativeImageResourcePath root;
    private boolean isOpen = true;
    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();

    private static final byte[] ROOT_PATH = new byte[]{'/'};
    private final IndexNode lookupKey = new IndexNode(null, true);
    private final LinkedHashMap<IndexNode, IndexNode> inodes = new LinkedHashMap<>(10);

    public NativeImageResourceFileSystem(NativeImageResourceFileSystemProvider provider, Path resourcePath, Map<String, ?> env) {
        this.provider = provider;
        this.resourcePath = resourcePath;
        this.root = new NativeImageResourcePath(this, new byte[]{'/'});
        if (!isTrue(env)) {
            throw new FileSystemNotFoundException(resourcePath.toString());
        }
        readAllEntries();
    }

    // Returns true if there is a name=true/"true" setting in env.
    private static boolean isTrue(Map<String, ?> env) {
        return "true".equals(env.get("create")) || TRUE.equals(env.get("create"));
    }

    private void ensureOpen() {
        if (!isOpen) {
            throw new ClosedFileSystemException();
        }
    }

    private void beginWrite() {
        rwlock.writeLock().lock();
    }

    private void endWrite() {
        rwlock.writeLock().unlock();
    }

    private void beginRead() {
        rwlock.readLock().lock();
    }

    private void endRead() {
        rwlock.readLock().unlock();
    }

    byte[] getBytes(String path) {
        return path.getBytes(StandardCharsets.UTF_8);
    }

    String getString(byte[] path) {
        return new String(path, StandardCharsets.UTF_8);
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        beginWrite();
        try {
            if (!isOpen) {
                return;
            }
            isOpen = false;
        } finally {
            endWrite();
        }

        if (!inputStreams.isEmpty()) {    // Unlock and close all remaining input streams.
            Set<InputStream> copy = new HashSet<>(inputStreams);
            for (InputStream is : copy) {
                is.close();
            }
        }

        if (!outputStreams.isEmpty()) {    // Unlock and close all remaining output streams.
            Set<OutputStream> copy = new HashSet<>(outputStreams);
            for (OutputStream os : copy) {
                os.close();
            }
        }

        provider.removeFileSystem();
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singleton(root);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return Collections.singleton(new NativeImageResourceFileStore(root));
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return supportedFileAttributeViews;
    }

    Path getResourcePath() {
        return resourcePath;
    }

    @Override
    public String toString() {
        return resourcePath.toString();
    }

    @Override
    public Path getPath(String first, String... more) {
        String path;
        if (more.length == 0) {
            path = first;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(first);
            for (String segment : more) {
                if (segment.length() > 0) {
                    if (sb.length() > 0) {
                        sb.append('/');
                    }
                    sb.append(segment);
                }
            }
            path = sb.toString();
        }
        return new NativeImageResourcePath(this, getBytes(path));
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        int pos = syntaxAndPattern.indexOf(':');
        if (pos <= 0 || pos == syntaxAndPattern.length()) {
            throw new IllegalArgumentException();
        }
        String syntax = syntaxAndPattern.substring(0, pos);
        String input = syntaxAndPattern.substring(pos + 1);
        String expr;
        if (syntax.equals(GLOB_SYNTAX)) {
            expr = toRegexPattern(input);
        } else {
            if (syntax.equals(REGEX_SYNTAX)) {
                expr = input;
            } else {
                throw new UnsupportedOperationException("Syntax '" + syntax + "' not recognized");
            }
        }

        Pattern pattern = Pattern.compile(expr);
        return path -> pattern.matcher(path.toString()).matches();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    NativeImageResourceFileAttributes getFileAttributes(byte[] path) {
        Entry entry;
        beginRead();
        try {
            ensureOpen();
            entry = getEntry(path);
            if (entry == null) {
                IndexNode inode = getInode(path);
                if (inode == null) {
                    return null;
                }
                entry = new Entry(inode.name, inode.isDir);
                entry.lastModifiedTime = entry.lastAccessTime = entry.createTime = defaultTimestamp;
            }
        } finally {
            endRead();
        }
        return new NativeImageResourceFileAttributes(this, entry);
    }

    static void checkOptions(Set<? extends OpenOption> options) {
        for (OpenOption option : options) {
            if (option == null) {
                throw new NullPointerException();
            }
            if (!(option instanceof StandardOpenOption)) {
                throw new IllegalArgumentException();
            }
        }
        if (options.contains(APPEND) && options.contains(TRUNCATE_EXISTING)) {
            throw new IllegalArgumentException("APPEND + TRUNCATE_EXISTING are not allowed!");
        }
    }

    SeekableByteChannel newByteChannel(byte[] path, Set<? extends OpenOption> options) throws IOException {
        checkOptions(options);
        if (options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND)) {
            beginRead();    // Only need a read lock, the "update()" will obtain the write lock when
            // the channel is closed.
            try {
                Entry e = getEntry(path);
                if (e != null) {
                    if (e.isDir() || options.contains(CREATE_NEW)) {
                        throw new FileAlreadyExistsException(getString(path));
                    }
                    SeekableByteChannel sbc = new EntryOutputChannel(new Entry(e, Entry.NEW));
                    if (options.contains(APPEND)) {
                        try (InputStream is = getInputStream(e)) {
                            sbc.write(ByteBuffer.wrap(NativeImageResourceFileSystemUtil.inputStreamToByteArray(is)));
                        }
                    }
                    return sbc;
                }
                if (!options.contains(CREATE) && !options.contains(CREATE_NEW)) {
                    throw new NoSuchFileException(getString(path));
                }
                checkParents(path);
                return new EntryOutputChannel(new Entry(path, false));
            } finally {
                endRead();
            }
        } else {
            beginRead();
            try {
                ensureOpen();
                Entry e = getEntry(path);
                if (e == null || e.isDir()) {
                    throw new NoSuchFileException(getString(path));
                }
                try (InputStream is = getInputStream(e)) {
                    return new ByteArrayChannel(NativeImageResourceFileSystemUtil.inputStreamToByteArray(is), true);
                }
            } finally {
                endRead();
            }
        }
    }

    boolean exists(byte[] path) {
        beginRead();
        try {
            ensureOpen();
            return getInode(path) != null;
        } finally {
            endRead();
        }
    }

    static FileStore getFileStore(NativeImageResourcePath path) {
        return new NativeImageResourceFileStore(path);
    }

    void checkAccess(byte[] path) throws NoSuchFileException {
        beginRead();
        try {
            ensureOpen();
            if (getInode(path) == null) {
                throw new NoSuchFileException(toString());
            }
        } finally {
            endRead();
        }
    }

    void setTimes(byte[] path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws NoSuchFileException {
        beginWrite();
        try {
            ensureOpen();
            Entry e = getEntry(path);
            if (e == null) {
                throw new NoSuchFileException(getString(path));
            }
            if (lastModifiedTime != null) {
                e.lastModifiedTime = lastModifiedTime.toMillis();
            }
            if (lastAccessTime != null) {
                e.lastAccessTime = lastAccessTime.toMillis();
            }
            if (createTime != null) {
                e.createTime = createTime.toMillis();
            }
            update(e);
        } finally {
            endWrite();
        }
    }

    boolean isDirectory(byte[] path) {
        beginRead();
        try {
            IndexNode n = getInode(path);
            return n != null && n.isDir();
        } finally {
            endRead();
        }
    }

    void createDirectory(byte[] dir) throws IOException {
        beginWrite();
        try {
            ensureOpen();
            if (dir.length == 0 || exists(dir)) {
                throw new FileAlreadyExistsException(getString(dir));
            }
            checkParents(dir);
            Entry e = new Entry(dir, true);
            update(e);
        } finally {
            endWrite();
        }
    }

    boolean deleteFile(byte[] path, boolean failIfNotExists) throws IOException {
        IndexNode inode = getInode(path);
        if (inode == null) {
            if (path.length == 0) {
                throw new NativeImageResourceFileSystemException("Root directory </> can't be deleted!");
            }
            if (failIfNotExists) {
                throw new NoSuchFileException(getString(path));
            } else {
                return false;
            }
        } else {
            if (inode.isDir() && inode.child != null) {
                throw new DirectoryNotEmptyException(getString(path));
            }
            updateDelete(inode);
        }
        return true;
    }

    private Path createTempFileInSameDirectoryAs() throws IOException {
        Path tmpPath = Files.createTempFile("rfs_tmp", null);
        tmpPaths.add(tmpPath);
        return tmpPath;
    }

    private Path getTempPathForEntry(byte[] path) throws IOException {
        Path tmpPath = createTempFileInSameDirectoryAs();
        if (path != null) {
            Entry e = getEntry(path);
            if (e != null) {
                try (InputStream is = newInputStream(path)) {
                    Files.copy(is, tmpPath, REPLACE_EXISTING);
                }
            }
        }

        return tmpPath;
    }

    private void removeTempPathForEntry(Path path) throws IOException {
        Files.delete(path);
        tmpPaths.remove(path);
    }

    void copyFile(boolean deleteSource, byte[] src, byte[] dst, CopyOption[] options) throws IOException {
        if (Arrays.equals(src, dst)) {
            return;    // Do nothing, src and dst are the same.
        }

        beginWrite();
        try {
            ensureOpen();
            Entry eSrc = getEntry(src);  // ensureOpen checked

            if (eSrc == null) {
                throw new NoSuchFileException(getString(src));
            }
            if (eSrc.isDir()) {    // spec says to create dst dir
                createDirectory(dst);
                return;
            }
            boolean hasReplace = false;
            boolean hasCopyAttrs = false;
            for (CopyOption opt : options) {
                if (opt == REPLACE_EXISTING) {
                    hasReplace = true;
                } else if (opt == COPY_ATTRIBUTES) {
                    hasCopyAttrs = true;
                }
            }
            Entry eDst = getEntry(dst);
            if (eDst != null) {
                if (!hasReplace) {
                    throw new FileAlreadyExistsException(getString(dst));
                }
            } else {
                checkParents(dst);
            }
            Entry target = new Entry(eSrc, Entry.COPY);  // Copy eSrc entry.
            target.name(dst);                            // Change name.
            if (eSrc.type == Entry.NEW || eSrc.type == Entry.FILE_CH) {
                target.type = eSrc.type;    // Make it the same type.
                if (deleteSource) {       // If it's a "rename", take the data
                    target.bytes = eSrc.bytes;
                    target.file = eSrc.file;
                } else {               // If it's not "rename", copy the data.
                    if (eSrc.bytes != null) {
                        target.bytes = Arrays.copyOf(eSrc.bytes, eSrc.bytes.length);
                    } else if (eSrc.file != null) {
                        target.file = getTempPathForEntry(null);
                        Files.copy(eSrc.file, target.file, REPLACE_EXISTING);
                    }
                }
            }
            if (!hasCopyAttrs) {
                target.lastModifiedTime = target.lastAccessTime = target.createTime = System.currentTimeMillis();
            }
            update(target);
            if (deleteSource) {
                updateDelete(eSrc);
            }
        } finally {
            endWrite();
        }
    }

    private void checkParents(byte[] pathBytes) throws IOException {
        beginRead();
        try {
            byte[] path = pathBytes;
            while ((path = getParent(path)) != null && path != ROOT_PATH) {
                if (!inodes.containsKey(IndexNode.keyOf(path))) {
                    throw new NoSuchFileException(getString(path));
                }
            }
        } finally {
            endRead();
        }
    }

    IndexNode getInode(byte[] path) {
        if (path == null) {
            throw new NullPointerException("Path is null!");
        }
        return inodes.get(IndexNode.keyOf(path));
    }

    Entry getEntry(byte[] path) {
        IndexNode inode = getInode(path);
        if (inode instanceof Entry) {
            return (Entry) inode;
        }
        if (inode == null) {
            return null;
        }
        return new Entry(inode.name, inode.isDir);
    }

    static byte[] getParent(byte[] path) {
        int off = getParentOff(path);
        if (off <= 1) {
            return ROOT_PATH;
        }
        return Arrays.copyOf(path, off);
    }

    private static int getParentOff(byte[] path) {
        int off = path.length - 1;
        if (off > 0 && path[off] == '/') {
            off--;
        }
        while (off > 0 && path[off] != '/') {
            off--;
        }
        return off;
    }

    private void removeFromTree(IndexNode inode) {
        IndexNode parent = inodes.get(lookupKey.as(getParent(inode.name)));
        IndexNode child = parent.child;
        if (child.equals(inode)) {
            parent.child = child.sibling;
        } else {
            IndexNode last = child;
            while ((child = child.sibling) != null) {
                if (child.equals(inode)) {
                    last.sibling = child.sibling;
                    break;
                } else {
                    last = child;
                }
            }
        }
    }

    private void updateDelete(IndexNode inode) {
        beginWrite();
        try {
            removeFromTree(inode);
            inodes.remove(inode);
        } finally {
            endWrite();
        }
    }

    private void update(Entry e) {
        beginWrite();
        try {
            IndexNode old = inodes.put(e, e);
            if (old != null) {
                removeFromTree(old);
            }
            if (e.type == Entry.NEW || e.type == Entry.FILE_CH || e.type == Entry.COPY) {
                IndexNode parent = inodes.get(lookupKey.as(getParent(e.name)));
                e.sibling = parent.child;
                parent.child = e;
            }
        } finally {
            endWrite();
        }
    }

    private void readAllEntries() {
        MapCursor<Pair<String, String>, ResourceStorageEntry> entries = Resources.singleton().resources().getEntries();
        while (entries.advance()) {
            byte[] name = getBytes(entries.getKey().getRight());
            if (!entries.getValue().isDirectory()) {
                IndexNode newIndexNode = new IndexNode(name, false);
                inodes.put(newIndexNode, newIndexNode);
            }
        }
        buildNodeTree();
    }

    private void buildNodeTree() {
        beginWrite();
        try {
            IndexNode rootIndex = inodes.get(lookupKey.as(ROOT_PATH));
            if (rootIndex == null) {
                rootIndex = new IndexNode(ROOT_PATH, true);
            } else {
                inodes.remove(rootIndex);
            }
            IndexNode[] nodes = inodes.keySet().toArray(new IndexNode[0]);
            inodes.put(rootIndex, rootIndex);
            ParentLookup lookup = new ParentLookup();
            for (IndexNode controlNode : nodes) {
                IndexNode parent;
                IndexNode node = controlNode;
                while (true) {
                    int off = getParentOff(node.name);
                    if (off <= 1) {    // Parent is root.
                        node.sibling = rootIndex.child;
                        rootIndex.child = node;
                        break;
                    }
                    lookup = lookup.as(node.name, off);
                    if (inodes.containsKey(lookup)) {
                        parent = inodes.get(lookup);
                        node.sibling = parent.child;
                        parent.child = node;
                        break;
                    }
                    // Add new pseudo directory entry.
                    parent = new IndexNode(Arrays.copyOf(node.name, off), true);
                    inodes.put(parent, parent);
                    node.sibling = parent.child;
                    parent.child = node;
                    node = parent;
                }
            }
        } finally {
            endWrite();
        }
    }

    private InputStream getInputStream(Entry e) throws IOException {
        InputStream eis = null;
        if (e.type == Entry.NEW || e.type == Entry.COPY) {
            byte[] bytes = e.getBytes(true);
            if (bytes != null) {
                eis = new ByteArrayInputStream(bytes);
            } else {
                if (e.file != null) {
                    eis = Files.newInputStream(e.file);
                } else {
                    throw new NativeImageResourceFileSystemException("Entry data is missing!");
                }
            }
        } else {
            if (e.type == Entry.FILE_CH) {
                eis = Files.newInputStream(e.file);
                return eis;
            }
        }
        inputStreams.add(eis);
        return eis;
    }

    private OutputStream getOutputStream(Entry e) {
        e.getBytes(false);
        if (e.lastModifiedTime == -1) {
            e.lastModifiedTime = System.currentTimeMillis();
        }
        OutputStream os = new EntryOutputStream(e, new ByteArrayOutputStream((e.size > 0) ? e.size : DEFAULT_BUFFER_SIZE));
        outputStreams.add(os);
        return os;
    }

    InputStream newInputStream(byte[] path) throws IOException {
        beginRead();
        try {
            ensureOpen();
            Entry entry = getEntry(path);
            if (entry == null) {
                throw new NoSuchFileException(getString(path));
            }
            if (entry.isDir()) {
                throw new FileSystemException(getString(path), "is a directory", null);
            }
            return getInputStream(entry);
        } finally {
            endRead();
        }
    }

    OutputStream newOutputStream(byte[] path, OpenOption... options) throws IOException {
        boolean hasCreateNew = false;
        boolean hasCreate = false;
        boolean hasAppend = false;
        boolean hasTruncate = false;
        for (OpenOption opt : options) {
            if (opt == READ) {
                throw new IllegalArgumentException("READ not allowed!");
            }
            if (opt == CREATE_NEW) {
                hasCreateNew = true;
            }
            if (opt == CREATE) {
                hasCreate = true;
            }
            if (opt == APPEND) {
                hasAppend = true;
            }
            if (opt == TRUNCATE_EXISTING) {
                hasTruncate = true;
            }
        }

        if (hasAppend && hasTruncate) {
            throw new IllegalArgumentException("APPEND + TRUNCATE_EXISTING are not allowed!");
        }

        beginRead();                 // Only need a read lock, the update will
        try {                        // try to obtain a write lock when the os is
            ensureOpen();            // being closed.
            Entry e = getEntry(path);
            if (e != null) {
                if (e.isDir() || hasCreateNew) {
                    throw new FileAlreadyExistsException(getString(path));
                }
                if (hasAppend) {
                    try (InputStream is = getInputStream(e)) {
                        OutputStream os = getOutputStream(new Entry(e, Entry.NEW));
                        byte[] bytes = NativeImageResourceFileSystemUtil.inputStreamToByteArray(is);
                        os.write(bytes, 0, bytes.length);
                        return os;
                    }
                }
                return getOutputStream(new Entry(e, Entry.NEW));
            } else {
                if (!hasCreate && !hasCreateNew) {
                    throw new NoSuchFileException(getString(path));
                }
                checkParents(path);
                return getOutputStream(new Entry(path, false));
            }
        } finally {
            endRead();
        }
    }

    FileChannel newFileChannel(byte[] path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        checkOptions(options);
        boolean forWrite = (options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND));
        beginRead();
        try {
            ensureOpen();
            Entry e = getEntry(path);
            if (forWrite) {
                if (e == null) {
                    if (!options.contains(StandardOpenOption.CREATE) && !options.contains(StandardOpenOption.CREATE_NEW)) {
                        throw new NoSuchFileException(getString(path));
                    }
                } else {
                    if (options.contains(StandardOpenOption.CREATE_NEW)) {
                        throw new FileAlreadyExistsException(getString(path));
                    }
                    if (e.isDir()) {
                        throw new FileAlreadyExistsException("Directory <" + getString(path) + "> exists!");
                    }
                }
            } else if (e == null || e.isDir()) {
                throw new NoSuchFileException(getString(path));
            }

            final boolean isFCH = (e != null && e.type == Entry.FILE_CH);
            final Path tmpFile = isFCH ? e.file : getTempPathForEntry(path);
            final FileChannel fch = tmpFile.getFileSystem().provider().newFileChannel(tmpFile, options, attrs);
            final Entry target = isFCH ? e : new Entry(path, tmpFile, Entry.FILE_CH);
            return new FileChannel() {

                @Override
                public int write(ByteBuffer src) throws IOException {
                    return fch.write(src);
                }

                @Override
                public long write(ByteBuffer[] src, int offset, int length) throws IOException {
                    return fch.write(src, offset, length);
                }

                @Override
                public long position() throws IOException {
                    return fch.position();
                }

                @Override
                public FileChannel position(long newPosition) throws IOException {
                    fch.position(newPosition);
                    return this;
                }

                @Override
                public long size() throws IOException {
                    return fch.size();
                }

                @Override
                public FileChannel truncate(long size) throws IOException {
                    fch.truncate(size);
                    return this;
                }

                @Override
                public void force(boolean metaData) throws IOException {
                    fch.force(metaData);
                }

                @Override
                public long transferTo(long position, long count, WritableByteChannel byteChannel)
                                throws IOException {

                    return fch.transferTo(position, count, byteChannel);
                }

                @Override
                public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
                    return fch.transferFrom(src, position, count);
                }

                @Override
                public int read(ByteBuffer dst) throws IOException {
                    return fch.read(dst);
                }

                @Override
                public int read(ByteBuffer dst, long position) throws IOException {
                    return fch.read(dst, position);
                }

                @Override
                public long read(ByteBuffer[] dst, int offset, int length) throws IOException {
                    return fch.read(dst, offset, length);
                }

                @Override
                public int write(ByteBuffer src, long position) throws IOException {
                    return fch.write(src, position);
                }

                @Override
                public MappedByteBuffer map(MapMode mode, long position, long size) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public FileLock lock(long position, long size, boolean shared) throws IOException {
                    return fch.lock(position, size, shared);
                }

                @Override
                public FileLock tryLock(long position, long size, boolean shared) throws IOException {
                    return fch.tryLock(position, size, shared);
                }

                @Override
                protected void implCloseChannel() throws IOException {
                    fch.close();
                    if (forWrite) {
                        target.lastModifiedTime = System.currentTimeMillis();
                        target.size = (int) Files.size(target.file);

                        update(target);
                    } else {
                        if (!isFCH) {
                            removeTempPathForEntry(tmpFile);
                        }
                    }
                }
            };
        } finally {
            endRead();
        }
    }

    Iterator<Path> iteratorOf(NativeImageResourcePath dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        beginWrite();
        try {
            ensureOpen();
            byte[] path = dir.getResolvedPath();
            IndexNode inode = getInode(path);
            if (inode == null) {
                throw new NotDirectoryException(getString(path));
            }
            List<Path> list = new ArrayList<>();
            IndexNode child = inode.child;
            while (child != null) {
                byte[] childName = child.name;
                NativeImageResourcePath childPath = new NativeImageResourcePath(this, childName, true);
                Path childFileName = childPath.getFileName();
                Path dirPath = null;
                if (childFileName != null) {
                    dirPath = dir.resolve(childFileName);
                }
                if (filter == null || (dirPath != null && filter.accept(dirPath))) {
                    list.add(dirPath);
                }
                child = child.sibling;
            }
            return list.iterator();
        } finally {
            endWrite();
        }
    }

    private static class IndexNode {

        private static final ThreadLocal<IndexNode> cachedKey = new ThreadLocal<>();

        byte[] name;
        int hashcode;
        boolean isDir;

        IndexNode child;
        IndexNode sibling;

        IndexNode() {
        }

        IndexNode(byte[] n) {
            name(n);
        }

        IndexNode(byte[] n, boolean isDir) {
            name(n);
            this.isDir = isDir;
        }

        static IndexNode keyOf(byte[] n) {
            IndexNode key = cachedKey.get();
            if (key == null) {
                key = new IndexNode(n);
                cachedKey.set(key);
            }
            return key.as(n);
        }

        final void name(byte[] n) {
            this.name = n;
            this.hashcode = Arrays.hashCode(n);
        }

        final IndexNode as(byte[] n) {
            name(n);
            return this;
        }

        boolean isDir() {
            return isDir;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof IndexNode)) {
                return false;
            }
            if (other instanceof ParentLookup) {
                return other.equals(this);
            }
            return Arrays.equals(name, ((IndexNode) other).name);
        }

        @Override
        public int hashCode() {
            return hashcode;
        }
    }

    // For parent lookup, so we don't have to copy the parent name every time.
    private static class ParentLookup extends IndexNode {

        private int length;

        ParentLookup() {
        }

        ParentLookup as(byte[] n, int len) {
            name(n, len);
            return this;
        }

        void name(byte[] n, int len) {
            this.name = n;
            this.length = len;
            int result = 1;
            for (int i = 0; i < len; i++) {
                result = 31 * result + n[i];
            }
            this.hashcode = result;
        }

        boolean isEquals(byte[] name1, int endIndex1, byte[] name2, int endIndex2) {
            if (name1.length < endIndex1 || name2.length < endIndex2) {
                return false;
            }
            int lenToCmp = Math.min(endIndex1, endIndex2);
            for (int index = 0; index < lenToCmp; index++) {
                if (name1[index] != name2[index]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof IndexNode)) {
                return false;
            }
            byte[] otherName = ((IndexNode) other).name;
            return isEquals(name, length, otherName, otherName.length);
        }
    }

    class Entry extends IndexNode {

        private static final int NEW = 1;       // Updated contents in bytes or file.
        private static final int FILE_CH = 2;   // File channel update in file.
        private static final int COPY = 3;      // Copy entry.

        public int size;
        public int type;
        public long lastModifiedTime;
        public long lastAccessTime;
        public long createTime;

        private boolean copyOnWrite;
        private byte[] bytes;
        public Path file;

        Entry(byte[] name, Path file, int type) {
            this(name, type, false);
            this.file = file;
        }

        void initTimes() {
            this.lastModifiedTime = this.lastAccessTime = this.createTime = System.currentTimeMillis();
        }

        void initData() {
            this.bytes = NativeImageResourceFileSystemUtil.getBytes(getString(name), true);
            this.size = !isDir ? this.bytes.length : 0;
        }

        byte[] getBytes(boolean readOnly) {
            if (!readOnly) {
                // Copy On Write technique.
                if (!copyOnWrite) {
                    copyOnWrite = true;
                    this.bytes = NativeImageResourceFileSystemUtil.getBytes(getString(name), false);
                }
            }
            return this.bytes;
        }

        Entry(byte[] name, boolean isDir) {
            name(name);
            this.type = Entry.NEW;
            this.isDir = isDir;
            initData();
            initTimes();
        }

        Entry(byte[] name, int type, boolean isDir) {
            name(name);
            this.type = type;
            this.isDir = isDir;
            initData();
            initTimes();
        }

        Entry(Entry other, int type) {
            name(other.name);
            this.lastModifiedTime = other.lastModifiedTime;
            this.lastAccessTime = other.lastAccessTime;
            this.createTime = other.createTime;
            this.isDir = other.isDir;
            this.size = other.size;
            this.bytes = other.bytes;
            this.type = type;
            this.copyOnWrite = true;
        }

        boolean isDirectory() {
            return isDir;
        }

        long size() {
            return size;
        }
    }

    class EntryOutputChannel extends ByteArrayChannel {

        Entry e;

        EntryOutputChannel(Entry e) {
            super(e.size > 0 ? e.size : DEFAULT_BUFFER_SIZE, false);
            this.e = e;
            if (e.lastModifiedTime == -1) {
                e.lastModifiedTime = System.currentTimeMillis();
            }
        }

        @Override
        public void close() throws IOException {
            // This will update entry.
            try (OutputStream os = getOutputStream(e)) {
                os.write(toByteArray());
            }
            super.close();
        }
    }

    private class EntryOutputStream extends FilterOutputStream {
        private final Entry e;
        private int written;
        private boolean isClosed;

        EntryOutputStream(Entry e, OutputStream os) {
            super(os);
            this.e = Objects.requireNonNull(e, "Entry is null!");
        }

        // Checkstyle: stop
        @Override
        public synchronized void write(int b) throws IOException {
            out.write(b);
            written += 1;
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            written += len;
        }

        @Override
        public synchronized void close() throws IOException {
            if (isClosed) {
                return;
            }
            isClosed = true;
            e.size = written;
            if (out instanceof ByteArrayOutputStream) {
                e.bytes = ((ByteArrayOutputStream) out).toByteArray();
            }
            super.close();
            update(e);
        }
        // Checkstyle: resume
    }
}
