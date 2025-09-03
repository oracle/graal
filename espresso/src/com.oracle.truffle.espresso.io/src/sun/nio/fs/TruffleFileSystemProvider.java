/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package sun.nio.fs;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This file must be compatible with 21+.
 */
class TruffleFileSystemProvider extends FileSystemProvider {

    static {
        // ensure 'nio' is loaded. Also loads 'net' as a side-effect.
        sun.nio.ch.IOUtil.load();
    }

    private final TruffleFileSystem theFileSystem;

    private static final String SCHEME = "file";

    static final String SEPARATOR = getSeparator0();

    TruffleFileSystemProvider() {
        this.theFileSystem = new TruffleFileSystem(this);
    }

    TruffleFileSystem theFileSystem() {
        return theFileSystem;
    }

    @Override
    public String getScheme() {
        return SCHEME;
    }

    private void checkUri(URI uri) {
        if (!uri.getScheme().equalsIgnoreCase(getScheme())) {
            throw new IllegalArgumentException("URI does not match this provider");
        }
        if (uri.getRawAuthority() != null) {
            throw new IllegalArgumentException("Authority component present");
        }
        String path = uri.getPath();
        if (path == null) {
            throw new IllegalArgumentException("Path component is undefined");
        }
        if (!path.equals("/")) {
            throw new IllegalArgumentException("Path component should be '/'");
        }
        if (uri.getRawQuery() != null) {
            throw new IllegalArgumentException("Query component present");
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Fragment component present");
        }
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        checkUri(uri);
        throw new FileSystemAlreadyExistsException();
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        checkUri(uri);
        return theFileSystem;
    }

    @Override
    public Path getPath(URI uri) {
        return new TrufflePath(theFileSystem(), uri.getPath());
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return newFileChannel(path, options, attrs);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        // On Unix ALL_READWRITE is default
        int fileAttributeMask = FileAttributeParser.parseWithDefault(FileAttributeParser.ALL_READWRITE, attrs);
        int openOptionsMask = openOptionsToMask(options);
        boolean readable = options.contains(StandardOpenOption.READ);
        boolean sync = options.contains(StandardOpenOption.SYNC);
        boolean writable = options.contains(StandardOpenOption.WRITE);
        boolean append = options.contains(StandardOpenOption.APPEND);

        // default is reading; append => writing
        if (!readable && !writable) {
            if (append) {
                writable = true;
            } else {
                readable = true;
            }
        }

        // set direct option
        boolean direct = false;
        for (OpenOption option : options) {
            if (ExtendedOptions.DIRECT.matches(option)) {
                direct = true;
                break;
            }
        }
        // check for Exceptions
        if (readable && append) {
            throw new IllegalArgumentException("READ + APPEND not allowed");
        }
        if (append && options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
            throw new IllegalArgumentException("APPEND + TRUNCATE_EXISTING not allowed");
        }

        FileDescriptor fd = new FileDescriptor();
        // populates fd via HostCode which opens the file and checks the permissions
        newFileChannel0(TrufflePath.toTrufflePath(path), fd, openOptionsMask, fileAttributeMask);
        return NewFileChannelHelper.open(fd, path.toString(), readable, writable, sync, direct, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        // ALL_PERMISSIONS is the default permission on Unix.
        int fileAttributeMask = FileAttributeParser.parseWithDefault(FileAttributeParser.ALL_PERMISSIONS, attrs);
        createDirectory0(TrufflePath.toTrufflePath(dir), fileAttributeMask);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        Objects.requireNonNull(filter);
        TrufflePath truffleDir = TrufflePath.toTrufflePath(dir);
        return TruffleFilteredDirectoryStream.create(truffleDir, filter);
    }

    @Override
    public void delete(Path path) throws IOException {
        TrufflePath trufflePath = TrufflePath.toTrufflePath(path);
        delete0(trufflePath);
    }

    // Keep in sync with Target_*_TruffleFileSystemProvider#SUPPORTED_OPEN_OPTIONS.
    private static final List<OpenOption> SUPPORTED_OPEN_OPTIONS = List.of(
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.DELETE_ON_CLOSE,
                    StandardOpenOption.SPARSE,
                    StandardOpenOption.SYNC,
                    StandardOpenOption.DSYNC,
                    LinkOption.NOFOLLOW_LINKS);

    // Keep in sync with Target_*_TruffleFileSystemProvider#SUPPORTED_COPY_OPTIONS.
    private static final List<CopyOption> SUPPORTED_COPY_OPTIONS = List.of(
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.ATOMIC_MOVE,
                    LinkOption.NOFOLLOW_LINKS);

    private static int copyOptionsToMask(CopyOption... options) {
        int mask = 0;
        for (CopyOption option : options) {
            int index = SUPPORTED_COPY_OPTIONS.indexOf(option);
            if (index < 0) {
                throw new UnsupportedOperationException("copy option: " + option);
            }
            assert index < 32;
            mask |= 1 << index;
        }
        return mask;
    }

    private static int openOptionsToMask(Set<? extends OpenOption> options) {
        int mask = 0;
        for (OpenOption option : options) {
            int index = SUPPORTED_OPEN_OPTIONS.indexOf(option);
            if (index < 0) {
                throw new UnsupportedOperationException("open option: " + option);
            }
            mask |= 1 << index;
        }
        return mask;
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        copy0(TrufflePath.toTrufflePath(source), TrufflePath.toTrufflePath(target), copyOptionsToMask(options));
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        move0(TrufflePath.toTrufflePath(source), TrufflePath.toTrufflePath(target), copyOptionsToMask(options));
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(path2);

        TrufflePath trufflePath = TrufflePath.toTrufflePath(path);
        if (!(path2 instanceof TrufflePath)) {
            return false;
        }

        TrufflePath trufflePath2 = TrufflePath.toTrufflePath(path);
        if (trufflePath.equals(trufflePath2)) {
            return true;
        }

        return isSameFile0(trufflePath, trufflePath2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) {
        return new TruffleFileStore(path);
    }

    // Keep in sync with Target_*_TruffleFileSystemProvider#SUPPORTED_ACCESS_MODES.
    private static final List<AccessMode> SUPPORTED_ACCESS_MODES = List.of(
                    AccessMode.READ,
                    AccessMode.WRITE,
                    AccessMode.EXECUTE);

    private static int accessModesToMask(AccessMode... modes) {
        int mask = 0;
        for (AccessMode mode : modes) {
            int index = SUPPORTED_ACCESS_MODES.indexOf(mode);
            if (index < 0) {
                throw new UnsupportedOperationException("access mode: " + mode);
            }
            mask |= 1 << index;
        }
        return mask;
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        checkAccess0(TrufflePath.toTrufflePath(path), accessModesToMask(modes));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        Objects.requireNonNull(type);
        if (type != BasicFileAttributeView.class) {
            throw new UnsupportedOperationException();
        }
        return (V) new TruffleBasicFileAttributeView(TrufflePath.toTrufflePath(path), TrufflePath.followLinks(options));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        Objects.requireNonNull(type);
        if (type != BasicFileAttributes.class) {
            throw new UnsupportedOperationException();
        }
        return (A) getFileAttributeView(path, BasicFileAttributeView.class, options).readAttributes();
    }

    private TruffleBasicFileAttributeView getFileAttributeView(TrufflePath path, String type, LinkOption... options) {
        Objects.requireNonNull(type);
        if ("basic".equals(type)) {
            return (TruffleBasicFileAttributeView) getFileAttributeView(path, BasicFileAttributeView.class, options);
        }
        throw new UnsupportedOperationException("view <" + type + "> is not supported");
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        String view;
        String attrs;
        int colonPos = attributes.indexOf(':');
        if (colonPos == -1) {
            view = "basic";
            attrs = attributes;
        } else {
            view = attributes.substring(0, colonPos++);
            attrs = attributes.substring(colonPos);
        }
        return getFileAttributeView(TrufflePath.toTrufflePath(path), view, options).readAttributes(attrs);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        String view;
        String name;
        int colonPos = attribute.indexOf(':');
        if (colonPos == -1) {
            view = "basic";
            name = attribute;
        } else {
            view = attribute.substring(0, colonPos++);
            name = attribute.substring(colonPos);
        }
        getFileAttributeView(TrufflePath.toTrufflePath(path), view, options).setAttribute(name, value);
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        // 0 is the default permission on Unix.
        int fileAttributeMask = FileAttributeParser.parseWithDefault(0, attrs);
        createSymbolicLink0(TrufflePath.toTrufflePath(link), TrufflePath.toTrufflePath(target), fileAttributeMask);
    }

    @Override
    public void createLink(Path link, Path existing) throws IOException {
        createLink0(TrufflePath.toTrufflePath(link), TrufflePath.toTrufflePath(existing));
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        String linkedPath = readSymbolicLink0(TrufflePath.toTrufflePath(link));
        return theFileSystem.getPath(linkedPath);
    }

    // region native methods

    private static native String getSeparator0();

    private static native void newFileChannel0(TrufflePath path, FileDescriptor fileDescriptor, int openOptionsMask, int fileAttributeMask) throws IOException;

    private static native void createDirectory0(TrufflePath path, int fileAttributeMask) throws IOException;

    private static native void delete0(TrufflePath path) throws IOException;

    private static native void copy0(TrufflePath source, TrufflePath target, int copyOptions) throws IOException;

    private static native void move0(TrufflePath source, TrufflePath target, int copyOptions) throws IOException;

    private static native boolean isSameFile0(TrufflePath path, TrufflePath path2) throws IOException;

    private static native void checkAccess0(TrufflePath path, int accessModesMask) throws IOException;

    private static native void createSymbolicLink0(TrufflePath link, TrufflePath target, int fileAttributeMask) throws IOException;

    private static native void createLink0(TrufflePath link, TrufflePath existing) throws IOException;

    private static native String readSymbolicLink0(TrufflePath link) throws IOException;

    // endregion native methods
}
