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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.oracle.svm.core.jdk.JavaNetSubstitutions;

public class NativeImageResourceFileSystemProvider extends FileSystemProvider {

    private final String resourcePath = "/resources";
    private final String resourceUri = JavaNetSubstitutions.FILE_PROTOCOL + ":" + resourcePath;
    private NativeImageResourceFileSystem fileSystem;
    private final Lock writeLock;
    private final Lock readLock;

    private Path uriToPath(URI uri) {
        String scheme = uri.getScheme();
        if ((scheme == null) || !scheme.equalsIgnoreCase(getScheme())) {
            throw new IllegalArgumentException("URI scheme is not '" + getScheme() + "'");
        }

        try {
            // Syntax for URI resource is resource:file:/resources/{entry}
            return Paths.get(new URI(resourceUri));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public NativeImageResourceFileSystemProvider() {
        ReadWriteLock rwlock = new ReentrantReadWriteLock();
        this.writeLock = rwlock.writeLock();
        this.readLock = rwlock.readLock();
    }

    private static NativeImageResourcePath toResourcePath(Path path) {
        if (path == null) {
            throw new NullPointerException();
        }
        if (!(path instanceof NativeImageResourcePath)) {
            throw new ProviderMismatchException();
        }
        return (NativeImageResourcePath) path;
    }

    private void checkIfResourcePath(Path path) {
        if (!path.startsWith(resourcePath)) {
            throw new UnsupportedOperationException("Path " + path + " is not part of resource system!");
        }
    }

    @Override
    public String getScheme() {
        return JavaNetSubstitutions.RESOURCE_PROTOCOL;
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        try {
            writeLock.lock();
            Path path = uriToPath(uri);
            checkIfResourcePath(path);
            if (fileSystem != null) {
                throw new FileSystemAlreadyExistsException();
            }
            fileSystem = new NativeImageResourceFileSystem(this, path, env);
            return fileSystem;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public FileSystem newFileSystem(Path path, Map<String, ?> env) {
        try {
            writeLock.lock();
            checkIfResourcePath(path);
            if (fileSystem != null) {
                throw new FileSystemAlreadyExistsException();
            }
            fileSystem = new NativeImageResourceFileSystem(this, path, env);
            return fileSystem;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        try {
            readLock.lock();
            if (fileSystem == null) {
                throw new FileSystemNotFoundException("The Native Image Resource File System is not present. " +
                                "Please create a new file system using the `newFileSystem` operation before attempting any file system operations on resource URIs.");
            }
            return fileSystem;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Path getPath(URI uri) {
        return getFileSystem(uri).getPath(uri.getSchemeSpecificPart());
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return toResourcePath(path).newByteChannel(options);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return toResourcePath(dir).newDirectoryStream(filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        toResourcePath(dir).createDirectory();
    }

    @Override
    public void delete(Path path) throws IOException {
        toResourcePath(path).delete();
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        return toResourcePath(path).deleteIfExists();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        toResourcePath(source).copy(toResourcePath(target), options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        toResourcePath(source).move(toResourcePath(target), options);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return toResourcePath(path).isSameFile(path2);
    }

    @Override
    public boolean isHidden(Path path) {
        return toResourcePath(path).isHidden();
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return toResourcePath(path).getFileStore();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        toResourcePath(path).checkAccess(modes);
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        return toResourcePath(path).newInputStream(options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        return toResourcePath(path).newOutputStream(options);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return toResourcePath(path).newFileChannel(options, attrs);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return NativeImageResourceFileAttributesView.get(toResourcePath(path), type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class || type == NativeImageResourceFileAttributes.class) {
            return (A) toResourcePath(path).getAttributes();
        }
        return null;
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return toResourcePath(path).readAttributes(attributes);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        toResourcePath(path).setAttribute(attribute, value);
    }

    void removeFileSystem() {
        try {
            writeLock.lock();
            fileSystem = null;
        } finally {
            writeLock.unlock();
        }
    }
}
