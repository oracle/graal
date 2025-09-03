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

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

/**
 * Implementation for the default Java NIO {@link FileSystem}.
 * <p>
 * All calls are delegated to jimfs through {@link #delegate}.
 * <p>
 * See {@link WebImageNIOFileSystemProvider} for how file system operations are intercepted in
 * general.
 */
public class WebImageNIOFileSystem extends FileSystem {

    private final FileSystemProvider provider;
    final FileSystem delegate;

    public WebImageNIOFileSystem(FileSystemProvider provider, FileSystem delegate) {
        this.provider = provider;
        this.delegate = delegate;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }

    @Override
    public String getSeparator() {
        return delegate.getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return delegate.getRootDirectories();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return delegate.getFileStores();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return delegate.supportedFileAttributeViews();
    }

    @Override
    public Path getPath(String first, String... more) {
        return delegate.getPath(first, more);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return delegate.getPathMatcher(syntaxAndPattern);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return delegate.getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return delegate.newWatchService();
    }
}
