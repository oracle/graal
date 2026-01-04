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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Truffle VFS implementation of {@link java.nio.file.Path}.
 *
 * <p>
 * This file must be compatible with 21+.
 */
final class TrufflePath implements Path {

    private final TruffleFileSystem fs;
    private final String path;

    TrufflePath(TruffleFileSystem fs, String path) {
        this.fs = Objects.requireNonNull(fs);
        this.path = Objects.requireNonNull(path);
        init0(path);
    }

    @Override
    public FileSystem getFileSystem() {
        return fs;
    }

    TruffleFileSystem getTruffleFileSystem() {
        return fs;
    }

    @Override
    public boolean isAbsolute() {
        return isAbsolute0();
    }

    @Override
    public Path getRoot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path getFileName() {
        return new TrufflePath(getTruffleFileSystem(), getFileName0());
    }

    @Override
    public Path getParent() {
        String parent = getParent0();
        if (parent == null) {
            return null;
        }
        return new TrufflePath(fs, getParent0());
    }

    private String[] getPathComponents() {
        String separator = Pattern.quote(getFileSystem().getSeparator());
        // Split includes all empty components.
        return path.split(separator, -1);
    }

    @Override
    public int getNameCount() {
        return getPathComponents().length;
    }

    @Override
    public Path getName(int index) {
        String[] parts = getPathComponents();
        if (index < 0 || parts.length < index) {
            throw new IllegalArgumentException();
        }
        return new TrufflePath(getTruffleFileSystem(), parts[index]);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        String[] parts = getPathComponents();
        if (beginIndex >= endIndex || beginIndex < 0 || endIndex > parts.length) {
            throw new IllegalArgumentException();
        }
        String[] subParts = Arrays.copyOfRange(parts, beginIndex, endIndex);
        return new TrufflePath(getTruffleFileSystem(), String.join(getFileSystem().getSeparator(), subParts));
    }

    // Checks that the given file is a TrufflePath
    static TrufflePath toTrufflePath(Path path) {
        Objects.requireNonNull(path);
        if (path instanceof TrufflePath) {
            return (TrufflePath) path;
        }
        throw new ProviderMismatchException();
    }

    @Override
    public boolean startsWith(Path other) {
        Objects.requireNonNull(other);
        if (!(other instanceof TrufflePath)) {
            return false;
        }
        return startsWith0((TrufflePath) other);
    }

    @Override
    public boolean endsWith(Path other) {
        Objects.requireNonNull(other);
        if (!(other instanceof TrufflePath)) {
            return false;
        }
        return endsWith0((TrufflePath) other);
    }

    @Override
    public Path normalize() {
        return new TrufflePath(getTruffleFileSystem(), normalize0());
    }

    @Override
    public Path resolve(Path other) {
        TrufflePath otherPath = toTrufflePath(other);
        return new TrufflePath(getTruffleFileSystem(), resolve0(otherPath.path));
    }

    @Override
    public Path relativize(Path other) {
        TrufflePath otherPath = toTrufflePath(other);
        return new TrufflePath(getTruffleFileSystem(), relativize0(otherPath));
    }

    @Override
    public URI toUri() {
        return URI.create(toURI0());
    }

    @Override
    public Path toAbsolutePath() {
        return new TrufflePath(getTruffleFileSystem(), toAbsolutePath0());
    }

    static boolean followLinks(LinkOption... options) {
        if (options != null && options.length > 0) {
            for (LinkOption option : options) {
                Objects.requireNonNull(option);
                if (option == LinkOption.NOFOLLOW_LINKS) {
                    return false;
                } else {
                    throw new UnsupportedOperationException("link option: " + option);
                }
            }
        }
        return true;
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return new TrufflePath(getTruffleFileSystem(), toRealPath0(followLinks(options)));
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Path other) {
        TrufflePath otherTruffle = toTrufflePath(other);
        return path.compareTo(otherTruffle.path);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof TrufflePath) &&
                        path.equals(((TrufflePath) obj).path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path;
    }

    @Override
    public boolean startsWith(String other) {
        return startsWith(getFileSystem().getPath(other));
    }

    @Override
    public boolean endsWith(String other) {
        return endsWith(getFileSystem().getPath(other));
    }

    @Override
    public Path resolve(String other) {
        return resolve(getFileSystem().getPath(other));
    }

    @Override
    public Path resolveSibling(Path other) {
        if (other == null) {
            throw new NullPointerException();
        }
        Path parent = getParent();
        return (parent == null) ? other : parent.resolve(other);
    }

    @Override
    public Path resolveSibling(String other) {
        return resolveSibling(getFileSystem().getPath(other));
    }

    @Override
    public File toFile() {
        if (getFileSystem() == FileSystems.getDefault()) {
            return new File(toString());
        } else {
            throw new UnsupportedOperationException("Path not associated with " + "default file system.");
        }
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        return register(watcher, events, new WatchEvent.Modifier[0]);
    }

    @Override
    public Iterator<Path> iterator() {
        List<String> components = Arrays.asList(getPathComponents());
        return MapFilterIterator.map(components.iterator(), s -> new TrufflePath(getTruffleFileSystem(), s));
    }

    // region native methods

    private native void init0(String path);

    private native boolean isAbsolute0();

    private native String getFileName0();

    private native String getParent0();

    private native boolean startsWith0(TrufflePath other);

    private native boolean endsWith0(TrufflePath other);

    private native String normalize0();

    private native String resolve0(String other);

    private native String relativize0(TrufflePath other);

    private native String toURI0();

    private native String toAbsolutePath0();

    private native String toRealPath0(boolean followLinks) throws IOException;

    // endregion native methods
}
