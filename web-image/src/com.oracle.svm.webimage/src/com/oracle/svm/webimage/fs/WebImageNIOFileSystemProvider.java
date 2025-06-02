/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.fs;

import org.graalvm.shadowed.com.google.common.jimfs.Jimfs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

/**
 * Implementation for the default Java NIO {@link FileSystemProvider} for the {@code file} scheme.
 * <p>
 * This implementation is injected into the JDK by substituting
 * {@code java.nio.file.FileSystems$DefaultFileSystemHolder#getDefaultProvider} to return an
 * instance of this class.
 * <p>
 * All operations are delegated to Jimfs, which provides the actual file system implementation.
 * However, any {@link URI} instances are first converted to use the {@code jimfs} scheme so that
 * Jimfs will accept them. This allows us to fully replace file system for the {@code file} scheme
 * with Jimfs (which only supports the {@code jimfs} scheme).
 *
 * @see WebImageNIOFileSystem
 */
public class WebImageNIOFileSystemProvider extends FileSystemProvider {

    public static final WebImageNIOFileSystemProvider INSTANCE = new WebImageNIOFileSystemProvider();

    private WebImageNIOFileSystem theFileSystem;

    private FileSystemProvider getDelegate() {
        return getTheFileSystem().delegate.provider();
    }

    private WebImageNIOFileSystem getTheFileSystem() {
        if (theFileSystem == null) {
            theFileSystem = new WebImageNIOFileSystem(this, FileSystemInitializer.createFileSystem());
            FileSystemInitializer.populate(theFileSystem);
        }
        return theFileSystem;
    }

    /**
     * Convert an URI with the {@code file} scheme to an URI that encodes the equivalent path in the
     * jimfs file system.
     */
    private static URI convertURI(URI uri) {
        if (uri.getScheme().equals("file")) {
            try {
                return new URI(Jimfs.URI_SCHEME, uri.getUserInfo(), FileSystemInitializer.FS_NAME, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }

        return uri;
    }

    /**
     * Looks up the {@code SystemJimfsFileSystemProvider} instance. This is different from
     * {@code JimfsFileSystemProvider}, which is the provider attached to the Jimfs
     * {@link FileSystem}.
     * <p>
     * The instance is the installed provider for the {@code jimfs} URI scheme.
     * <p>
     * This instance is needed for calling {@link FileSystemProvider#getPath(URI)}.
     */
    private static FileSystemProvider getJimfsSystemProvider() {
        for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
            if (provider.getScheme().equalsIgnoreCase(Jimfs.URI_SCHEME)) {
                return provider;
            }
        }

        throw new FileSystemNotFoundException("Provider \"" + Jimfs.URI_SCHEME + "\" not installed");
    }

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        throw new FileSystemAlreadyExistsException();
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        return getTheFileSystem();
    }

    @Override
    public Path getPath(URI uri) {
        // Make sure file system is initialized
        getTheFileSystem();
        return getJimfsSystemProvider().getPath(convertURI(uri));
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return getDelegate().newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return getDelegate().newDirectoryStream(dir, filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        getDelegate().createDirectory(dir, attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        getDelegate().delete(path);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        getDelegate().copy(source, target, options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        getDelegate().move(source, target, options);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return getDelegate().isSameFile(path, path2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return getDelegate().isHidden(path);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return getDelegate().getFileStore(path);
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        getDelegate().checkAccess(path, modes);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return getDelegate().getFileAttributeView(path, type, options);
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        return getDelegate().readAttributes(path, type, options);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return getDelegate().readAttributes(path, attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        getDelegate().setAttribute(path, attribute, value, options);
    }
}
