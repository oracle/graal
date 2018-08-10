/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.io.FileSystem;

final class FileSystems {

    static final String FILE_SCHEME = "file";
    private static final AtomicReference<FileSystem> DEFAULT_FILE_SYSTEM = new AtomicReference<>();

    private FileSystems() {
        throw new IllegalStateException("No instance allowed");
    }

    static FileSystem getDefaultFileSystem() {
        FileSystem fs = DEFAULT_FILE_SYSTEM.get();
        if (fs == null) {
            fs = FileSystems.newFileSystem(findDefaultFileSystemProvider());
            if (!DEFAULT_FILE_SYSTEM.compareAndSet(null, fs)) {
                fs = DEFAULT_FILE_SYSTEM.get();
            }
        }
        return fs;
    }

    static FileSystem newNoIOFileSystem() {
        return new DeniedIOFileSystem();
    }

    static FileSystem newNoIOFileSystem(final Path userDir) {
        return new DeniedIOFileSystem(userDir);
    }

    static FileSystem newFullIOFileSystem(Path userDir) {
        return newFileSystem(findDefaultFileSystemProvider(), userDir);
    }

    static FileSystem newFileSystem(final FileSystemProvider fileSystemProvider) {
        return new NIOFileSystem(fileSystemProvider);
    }

    static FileSystem newFileSystem(final FileSystemProvider fileSystemProvider, final Path userDir) {
        return new NIOFileSystem(fileSystemProvider, userDir);
    }

    private static FileSystemProvider findDefaultFileSystemProvider() {
        for (FileSystemProvider fsp : FileSystemProvider.installedProviders()) {
            if (FILE_SCHEME.equals(fsp.getScheme())) {
                return fsp;
            }
        }
        throw new IllegalStateException("No FileSystemProvider for scheme 'file'.");
    }

    private static boolean isFollowLinks(final LinkOption... linkOptions) {
        for (LinkOption lo : linkOptions) {
            if (lo == LinkOption.NOFOLLOW_LINKS) {
                return false;
            }
        }
        return true;
    }

    static final class PreInitializeContextFileSystem implements FileSystem {

        private FileSystem delegate; // effectively final after patch context

        PreInitializeContextFileSystem() {
            this.delegate = newFullIOFileSystem(null);
        }

        void patchDelegate(final FileSystem newDelegate) {
            Objects.requireNonNull(newDelegate, "NewDelegate must be non null.");
            this.delegate = newDelegate;
        }

        @Override
        public Path parsePath(URI path) {
            return delegate.parsePath(path);
        }

        @Override
        public Path parsePath(String path) {
            return delegate.parsePath(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            delegate.checkAccess(path, modes, linkOptions);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            delegate.createDirectory(dir, attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            delegate.delete(path);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            return delegate.newByteChannel(path, options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            return delegate.newDirectoryStream(dir, filter);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return delegate.toAbsolutePath(path);
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return delegate.toRealPath(path, linkOptions);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return delegate.readAttributes(path, attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            delegate.setAttribute(path, attribute, value, options);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            delegate.copy(source, target, options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            delegate.move(source, target, options);
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            delegate.createLink(link, existing);
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            delegate.createSymbolicLink(link, target, attrs);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            return delegate.readSymbolicLink(link);
        }
    }

    private static final class NIOFileSystem implements FileSystem {

        private final FileSystemProvider delegate;
        private final boolean explicitUserDir;
        private final Path userDir;

        NIOFileSystem(final FileSystemProvider fileSystemProvider) {
            this(fileSystemProvider, false, null);
        }

        NIOFileSystem(final FileSystemProvider fileSystemProvider, final Path userDir) {
            this(fileSystemProvider, true, userDir);
        }

        private NIOFileSystem(final FileSystemProvider fileSystemProvider, final boolean explicitUserDir, final Path userDir) {
            Objects.requireNonNull(fileSystemProvider, "FileSystemProvider must be non null.");
            this.delegate = fileSystemProvider;
            this.explicitUserDir = explicitUserDir;
            this.userDir = userDir;
        }

        @Override
        public Path parsePath(URI uri) {
            return delegate.getPath(uri);
        }

        @Override
        public Path parsePath(String path) {
            if (!"file".equals(delegate.getScheme())) {
                throw new IllegalStateException("The ParsePath(String path) should be called only for file scheme.");
            }
            return Paths.get(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            if (isFollowLinks(linkOptions)) {
                delegate.checkAccess(resolveRelative(path), modes.toArray(new AccessMode[modes.size()]));
            } else if (modes.isEmpty()) {
                delegate.readAttributes(path, "isRegularFile", LinkOption.NOFOLLOW_LINKS);
            } else {
                throw new UnsupportedOperationException("CheckAccess for NIO Provider is unsupported with non empty AccessMode and NOFOLLOW_LINKS.");
            }
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            delegate.createDirectory(resolveRelative(dir), attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            delegate.delete(resolveRelative(path));
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            delegate.copy(resolveRelative(source), resolveRelative(target), options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            delegate.move(resolveRelative(source), resolveRelative(target), options);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            final Path resolved = resolveRelative(path);
            try {
                return delegate.newFileChannel(resolved, options, attrs);
            } catch (UnsupportedOperationException uoe) {
                return delegate.newByteChannel(resolved, options, attrs);
            }
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            return delegate.newDirectoryStream(resolveRelative(dir), filter);
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            delegate.createLink(resolveRelative(link), resolveRelative(existing));
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            delegate.createSymbolicLink(resolveRelative(link), resolveRelative(target), attrs);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            return delegate.readSymbolicLink(resolveRelative(link));
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return delegate.readAttributes(resolveRelative(path), attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            delegate.setAttribute(resolveRelative(path), attribute, value, options);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            if (path.isAbsolute()) {
                return path;
            }
            if (explicitUserDir) {
                if (userDir == null) {
                    throw new SecurityException("Access to user.dir is not allowed.");
                }
                return userDir.resolve(path);
            } else {
                return path.toAbsolutePath();
            }
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            final Path resolvedPath = resolveRelative(path);
            return resolvedPath.toRealPath(linkOptions);
        }

        private Path resolveRelative(Path path) {
            return explicitUserDir ? toAbsolutePath(path) : path;
        }
    }

    private static final class DeniedIOFileSystem implements FileSystem {
        private final Path userDir;
        private final boolean explicitUserDir;
        private final FileSystem fullIO;
        private volatile Set<Path> languageHomes;

        DeniedIOFileSystem() {
            this(null, false);
        }

        DeniedIOFileSystem(final Path userDir) {
            this(userDir, true);
        }

        private DeniedIOFileSystem(final Path userDir, final boolean explicitUserDir) {
            this.userDir = userDir;
            this.explicitUserDir = explicitUserDir;
            this.fullIO = FileSystems.getDefaultFileSystem();
        }

        @Override
        public Path parsePath(final URI uri) {
            return Paths.get(uri);
        }

        @Override
        public Path parsePath(final String path) {
            return Paths.get(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            Path absolutePath = toAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                fullIO.checkAccess(absolutePath, modes, linkOptions);
                return;
            }
            throw forbidden(absolutePath);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            throw forbidden(dir);
        }

        @Override
        public void delete(Path path) throws IOException {
            throw forbidden(path);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            throw forbidden(source);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            throw forbidden(source);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path inPath, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            boolean read = options.contains(StandardOpenOption.READ);
            boolean write = options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.DELETE_ON_CLOSE);
            if (!read && !write) {
                if (options.contains(StandardOpenOption.APPEND)) {
                    write = true;
                } else {
                    read = true;
                }
            }
            if (write) {
                throw forbidden(inPath);
            }
            assert read;
            Path absolutePath = toAbsolutePath(inPath);
            if (inLanguageHome(absolutePath)) {
                return fullIO.newByteChannel(absolutePath, options, attrs);
            }
            throw forbidden(absolutePath);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            Path absoluteDir = toAbsolutePath(dir);
            if (inLanguageHome(absoluteDir)) {
                return fullIO.newDirectoryStream(absoluteDir, filter);
            }
            throw forbidden(absoluteDir);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            Path absolutePath = toAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                return fullIO.readAttributes(absolutePath, attributes, options);
            }
            throw forbidden(absolutePath);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            throw forbidden(path);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            if (path.isAbsolute()) {
                return path;
            }
            if (explicitUserDir) {
                if (userDir == null) {
                    throw new SecurityException("Access to 'user.dir' is not allowed.");
                }
                return userDir.resolve(path);
            } else {
                return path.toAbsolutePath();
            }
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            Path absoluetPath = toAbsolutePath(path);
            if (inLanguageHome(absoluetPath)) {
                return fullIO.toRealPath(absoluetPath, linkOptions);
            }
            throw forbidden(absoluetPath);
        }

        private boolean inLanguageHome(final Path path) {
            for (Path home : getLanguageHomes()) {
                if (path.startsWith(home)) {
                    return true;
                }
            }
            return false;
        }

        private Set<Path> getLanguageHomes() {
            Set<Path> res = languageHomes;
            if (res == null) {
                synchronized (this) {
                    res = languageHomes;
                    if (res == null) {
                        res = new HashSet<>();
                        for (LanguageCache cache : LanguageCache.languages().values()) {
                            final String languageHome = cache.getLanguageHome();
                            if (languageHome != null) {
                                res.add(Paths.get(languageHome));
                            }
                        }
                        languageHomes = res;
                    }
                }
            }
            return res;
        }

        private static SecurityException forbidden(final Path path) {
            throw new SecurityException("Operation is not allowed for: " + path);
        }
    }
}
