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

import static com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystemProvider.RESOURCE_PROTOCOL;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.util.NativeImageResourcePathRepresentation;

/// Native Image resource file-system path. A resource path carries enough identity to address one
/// variant of a resource that appears in multiple classpath or module roots (for example, two jars
/// on the application classpath, two exploded classpath directories, or two module-path entries).
/// For loader-aware resource URIs, the loader key from the URI host is folded into the internal
/// path bytes so that the file system can keep a single tree keyed by loader and resource name.
/// For example, `resource://java.base@boot/2!/java/lang/uniName.dat` is stored internally as the
/// path `/boot/java/lang/uniName.dat`; the resolved lookup path used to query resource storage
/// drops the leading slash and is therefore `boot/java/lang/uniName.dat`.
///
/// The module name is not folded into the path bytes. It is stored separately as `moduleName`
/// because named-module resource lookup needs the module identity in addition to the loader/root
/// identity. For example, `resource://org.graalvm.nativeimage.driver/0!/com/oracle/...` in
/// non-loader-aware mode keeps `org.graalvm.nativeimage.driver` as `moduleName`, while the visible
/// path remains `/com/oracle/...`.
///
/// `rootId` is the dense per-loader ID of the source root encoded in resource URLs and URIs, for
/// example the `2` in class-path resource URLs such as `resource://app/2!/META-INF/services/A`.
/// `resourceIndex` is the local data-array index used by this exact path's resolved
/// [ResourceStorageEntry]. In the common compact case `rootId == resourceIndex`; when entries are
/// registered from roots whose IDs are sparse for a particular resource,
/// [ResourceStorageEntry#getDataIndexForRootId(int)] maps `rootId` to the local `resourceIndex`.
///
/// Paths keep `rootId`, `resourceIndex`, and optional `moduleName` because `rootId` is stable URL
/// identity, while the usable local data index depends on the current resolved resource path and
/// module. Each path resolves its local index when it is created. Structural directory nodes have no
/// data array to index, so they use [NativeImageResourceFileSystem#NO_RESOURCE_INDEX] and only
/// preserve `rootId` while walking toward complete child entries. If a complete path has no data in
/// the selected root, the operation should fail for that root instead of silently falling back to a
/// different resource variant.
public class NativeImageResourcePath extends NativeImageResourcePathRepresentation implements Path {

    private final NativeImageResourceFileSystem fileSystem;
    private final int resourceIndex;
    private final int rootId;
    private final String moduleName;

    NativeImageResourcePath(NativeImageResourceFileSystem fileSystem, byte[] resourcePath, boolean normalized, int rootId) {
        this(fileSystem, resourcePath, normalized, rootId, null);
    }

    NativeImageResourcePath(NativeImageResourceFileSystem fileSystem, byte[] resourcePath, boolean normalized, int rootId, String moduleName) {
        super(resourcePath, normalized);
        this.fileSystem = fileSystem;
        this.rootId = rootId;
        this.moduleName = moduleName;
        this.resourceIndex = NativeImageResourceFileSystem.resolveResourceIndex(getResolvedResourceLookupPath(this.path), rootId, moduleName);
    }

    @Override
    public NativeImageResourceFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return (this.path.length > 0 && path[0] == '/');
    }

    public boolean isEmpty() {
        return this.path.length == 0;
    }

    @Override
    public NativeImageResourcePath getRoot() {
        if (isAbsolute()) {
            return new NativeImageResourcePath(fileSystem, new byte[]{this.path[0]}, false, rootId, moduleName);
        }
        return null;
    }

    @Override
    public Path getFileName() {
        initOffsets();
        int nbOffsets = offsets.length;
        if (nbOffsets == 0) {
            return null;
        }
        if (nbOffsets == 1 && path[0] != '/') {
            return this;
        }
        int offset = offsets[nbOffsets - 1];
        int length = path.length - offset;
        byte[] newPath = new byte[length];
        System.arraycopy(this.path, offset, newPath, 0, length);
        return new NativeImageResourcePath(fileSystem, newPath, false, rootId, moduleName);
    }

    @Override
    public NativeImageResourcePath getParent() {
        initOffsets();
        int nbOffsets = offsets.length;
        if (nbOffsets == 0) {
            return null;
        }
        int length = offsets[nbOffsets - 1] - 1;
        if (length <= 0) {
            return getRoot();
        }
        byte[] newPath = new byte[length];
        System.arraycopy(this.path, 0, newPath, 0, length);
        return new NativeImageResourcePath(fileSystem, newPath, false, rootId, moduleName);
    }

    @Override
    public Path getName(int index) {
        initOffsets();
        if (index < 0 || index >= offsets.length) {
            throw new IllegalArgumentException();
        }
        int begin = offsets[index];
        int len;
        if (index == (offsets.length - 1)) {
            len = path.length - begin;
        } else {
            len = offsets[index + 1] - begin - 1;
        }

        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return new NativeImageResourcePath(fileSystem, result, false, rootId, moduleName);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        initOffsets();
        if (beginIndex < 0 ||
                        beginIndex >= offsets.length ||
                        endIndex > offsets.length ||
                        beginIndex >= endIndex) {
            throw new IllegalArgumentException();
        }

        int begin = offsets[beginIndex];
        int len;
        if (endIndex == offsets.length) {
            len = path.length - begin;
        } else {
            len = offsets[endIndex] - begin - 1;
        }

        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return new NativeImageResourcePath(fileSystem, result, false, rootId, moduleName);
    }

    @Override
    public boolean startsWith(Path other) {
        NativeImageResourcePath p1 = this;
        NativeImageResourcePath p2 = checkPath(other);
        if (p1.isAbsolute() != p2.isAbsolute() || p1.path.length < p2.path.length) {
            return false;
        }
        int length = p2.path.length;
        for (int idx = 0; idx < length; idx++) {
            if (p1.path[idx] != p2.path[idx]) {
                return false;
            }
        }
        return p1.path.length == p2.path.length || p2.path[length - 1] == '/' || p1.path[length] == '/';
    }

    @Override
    public boolean startsWith(String other) {
        return startsWith(getFileSystem().getPath(other));
    }

    @Override
    public boolean endsWith(Path other) {
        NativeImageResourcePath p1 = this;
        NativeImageResourcePath p2 = checkPath(other);
        int i1 = p1.path.length - 1;
        if (i1 > 0 && p1.path[i1] == '/') {
            i1--;
        }
        int i2 = p2.path.length - 1;
        if (i2 > 0 && p2.path[i2] == '/') {
            i2--;
        }
        if (i2 == -1) {
            return i1 == -1;
        }
        if ((p2.isAbsolute() && (!isAbsolute() || i2 != i1)) || (i1 < i2)) {
            return false;
        }
        for (; i2 >= 0; i1--) {
            if (p2.path[i2] != p1.path[i1]) {
                return false;
            }
            i2--;
        }
        return (p2.path[i2 + 1] == '/');
    }

    @Override
    public boolean endsWith(String other) {
        return endsWith(getFileSystem().getPath(other));
    }

    @Override
    public Path normalize() {
        byte[] p = getResolvedPathBytes();
        if (p == this.path) {
            return this;
        }
        return new NativeImageResourcePath(fileSystem, p, true, rootId, moduleName);
    }

    @Override
    public Path resolve(Path other) {
        NativeImageResourcePath p1 = this;
        NativeImageResourcePath p2 = checkPath(other);
        if (p1.isEmpty() || p2.isAbsolute()) {
            return p2;
        }
        byte[] result;
        if (p1.path[p1.path.length - 1] == '/') {
            result = new byte[p1.path.length + p2.path.length];
            System.arraycopy(p1.path, 0, result, 0, p1.path.length);
            System.arraycopy(p2.path, 0, result, p1.path.length, p2.path.length);
        } else {
            result = new byte[p1.path.length + 1 + p2.path.length];
            System.arraycopy(p1.path, 0, result, 0, p1.path.length);
            result[p1.path.length] = '/';
            System.arraycopy(p2.path, 0, result, p1.path.length + 1, p2.path.length);
        }
        return new NativeImageResourcePath(fileSystem, result, false, rootId, moduleName);
    }

    private static NativeImageResourcePath checkPath(Path paramPath) {
        if (paramPath == null) {
            throw new NullPointerException();
        }
        if (!(paramPath instanceof NativeImageResourcePath)) {
            throw new ProviderMismatchException();
        }
        return (NativeImageResourcePath) paramPath;
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
        NativeImageResourcePath parent = getParent();
        return parent == null ? other : parent.resolve(other);
    }

    @Override
    public Path resolveSibling(String other) {
        return resolveSibling(getFileSystem().getPath(other));
    }

    @Override
    public Path relativize(Path other) {
        NativeImageResourcePath p1 = this;
        NativeImageResourcePath p2 = checkPath(other);
        if (p2.equals(p1)) {
            return new NativeImageResourcePath(fileSystem, new byte[0], true, rootId, moduleName);
        }
        if (p1.isAbsolute() != p2.isAbsolute()) {
            throw new IllegalArgumentException();
        }
        // Check how many segments are common.
        int nbNames1 = p1.getNameCount();
        int nbNames2 = p2.getNameCount();
        int l = Math.min(nbNames1, nbNames2);
        int nbCommon = 0;
        while (nbCommon < l && equalsNameAt(p1, p2, nbCommon)) {
            nbCommon++;
        }
        if (nbCommon == nbNames1 && nbCommon == nbNames2) {
            throw new IllegalArgumentException("Cannot relativize resource paths with the same path but different resource indexes.");
        }
        int nbUp = nbNames1 - nbCommon;
        // Compute the resulting length.
        int length = nbUp * 3 - 1;
        if (nbCommon < nbNames2) {
            length += p2.path.length - p2.offsets[nbCommon] + 1;
        }
        // Compute result.
        byte[] result = new byte[length];
        int idx = 0;
        while (nbUp-- > 0) {
            result[idx++] = '.';
            result[idx++] = '.';
            if (idx < length) {
                result[idx++] = '/';
            }
        }
        // Copy remaining segments.
        if (nbCommon < nbNames2) {
            System.arraycopy(p2.path, p2.offsets[nbCommon], result, idx, p2.path.length - p2.offsets[nbCommon]);
        }
        return new NativeImageResourcePath(fileSystem, result, false, rootId, moduleName);
    }

    @Override
    public URI toUri() {
        try {
            NativeImageResourcePath absolute = toAbsolutePath();
            if (ClassRegistries.respectClassLoader()) {
                byte[] resolvedPath = absolute.getResolvedPath();
                int separator = indexOf(resolvedPath, (byte) '/');
                if (separator <= 0 || separator == resolvedPath.length - 1) {
                    throw new IllegalArgumentException("Loader-aware " + RESOURCE_PROTOCOL + " paths require a loader key and resource name.");
                }
                String host = NativeImageResourceFileSystem.getString(Arrays.copyOf(resolvedPath, separator));
                String resourcePath = NativeImageResourceFileSystem.getString(Arrays.copyOfRange(resolvedPath, separator, resolvedPath.length));
                return new URI(RESOURCE_PROTOCOL, moduleName, host, -1, NativeImageResourceFileSystemUtil.formatRootedResourcePathFromAbsolute(rootId, resourcePath), null, null);
            }
            return new URI(RESOURCE_PROTOCOL, moduleName, NativeImageResourceFileSystemUtil.formatRootedResourcePathFromAbsolute(rootId, NativeImageResourceFileSystem.getString(
                            absolute.path)), null);
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private static int indexOf(byte[] bytes, byte value) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == value) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public NativeImageResourcePath toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        byte[] result = new byte[path.length + 1];
        result[0] = '/';
        System.arraycopy(path, 0, result, 1, path.length);
        return new NativeImageResourcePath(fileSystem, result, true, rootId, moduleName);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        NativeImageResourcePath absolute = new NativeImageResourcePath(fileSystem, getResolvedPath(), true, rootId, moduleName).toAbsolutePath();
        fileSystem.provider().checkAccess(absolute);
        return absolute;
    }

    @Override
    public File toFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Path> iterator() {
        return new Iterator<>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return (i < getNameCount());
            }

            @Override
            public Path next() {
                if (i < getNameCount()) {
                    Path result = getName(i);
                    i++;
                    return result;
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void remove() {
                throw new ReadOnlyFileSystemException();
            }
        };
    }

    @Override
    public int compareTo(Path other) {
        NativeImageResourcePath p1 = this;
        NativeImageResourcePath p2 = checkPath(other);
        byte[] a1 = p1.path;
        byte[] a2 = p2.path;
        int l1 = a1.length;
        int l2 = a2.length;
        for (int i = 0, l = Math.min(l1, l2); i < l; i++) {
            int b1 = a1[i] & 0xFF;
            int b2 = a2[i] & 0xFF;
            if (b1 != b2) {
                return b1 - b2;
            }
        }
        int diff = l1 - l2;
        if (diff != 0) {
            return diff;
        }
        int moduleDiff = Comparator.nullsFirst(String::compareTo).compare(p1.moduleName, p2.moduleName);
        if (moduleDiff != 0) {
            return moduleDiff;
        }
        return Integer.compare(p1.rootId, p2.rootId);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NativeImageResourcePath)) {
            return false;
        }
        NativeImageResourcePath other = (NativeImageResourcePath) obj;
        return fileSystem == other.fileSystem && rootId == other.rootId && Objects.equals(moduleName, other.moduleName) && Arrays.equals(path, other.path);
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(fileSystem);
        result = 31 * result + Arrays.hashCode(path);
        result = 31 * result + rootId;
        result = 31 * result + Objects.hashCode(moduleName);
        return result;
    }

    @Override
    public String toString() {
        return NativeImageResourceFileSystem.getString(path);
    }

    SeekableByteChannel newByteChannel(Set<? extends OpenOption> options) throws IOException {
        return fileSystem.newByteChannel(getResolvedPath(), getResourceIndex(), moduleName, options);
    }

    DirectoryStream<Path> newDirectoryStream(DirectoryStream.Filter<? super Path> filter) throws IOException {
        return new NativeImageResourceDirectoryStream(this, filter);
    }

    NativeImageResourceFileAttributes getAttributes() throws NoSuchFileException {
        NativeImageResourceFileAttributes nativeImageResourceFileAttributes = fileSystem.getFileAttributes(getResolvedPath(), getResourceIndex(), moduleName);
        if (nativeImageResourceFileAttributes == null) {
            throw new NoSuchFileException(toString());
        }
        return nativeImageResourceFileAttributes;
    }

    Map<String, Object> readAttributes(String attributes) throws IOException {
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
        NativeImageResourceFileAttributesView raw = NativeImageResourceFileAttributesView.get(this, view);
        if (raw == null) {
            throw new UnsupportedOperationException("View is not supported!");
        }
        return raw.readAttributes(attrs);
    }

    byte[] getResolvedPath() {
        byte[] r = resolved;
        if (r == null) {
            r = getResolvedResourceLookupPath(path);
            resolved = r;
        }
        return resolved;
    }

    int getRootId() {
        return rootId;
    }

    String getModuleName() {
        return moduleName;
    }

    int getResourceIndex() {
        return resourceIndex;
    }

    private byte[] getResolvedPathBytes() {
        if (path.length == 0) {
            return path;
        }
        for (byte c : path) {
            if (c == '.') {
                return resolveDots(path);
            }
        }
        return path;
    }

    private static byte[] getResolvedResourceLookupPath(byte[] path) {
        byte[] r;
        if (path.length > 0 && path[0] == '/') {
            r = resolveDots(path);
        } else {
            byte[] absolute = new byte[path.length + 1];
            absolute[0] = '/';
            System.arraycopy(path, 0, absolute, 1, path.length);
            r = resolveDots(absolute);
        }
        if (r[0] == '/' && r.length > 1) {
            return Arrays.copyOfRange(r, 1, r.length);
        }
        return r;
    }

    private static byte[] resolveDots(byte[] path) {
        for (byte c : path) {
            if (c == '.') {
                return getResolved(new NativeImageResourcePathRepresentation(path, true));
            }
        }
        return path;
    }

    private static boolean equalsNameAt(NativeImageResourcePath p1, NativeImageResourcePath p2, int index) {
        int beg1 = p1.offsets[index];
        int len1;
        if (index == p1.offsets.length - 1) {
            len1 = p1.path.length - beg1;
        } else {
            len1 = p1.offsets[index + 1] - beg1 - 1;
        }
        int beg2 = p2.offsets[index];
        int len2;
        if (index == p2.offsets.length - 1) {
            len2 = p2.path.length - beg2;
        } else {
            len2 = p2.offsets[index + 1] - beg2 - 1;
        }
        if (len1 != len2) {
            return false;
        }
        for (int n = 0; n < len1; n++) {
            if (p1.path[beg1 + n] != p2.path[beg2 + n]) {
                return false;
            }
        }
        return true;
    }

    boolean exists() {
        return fileSystem.exists(getResolvedPath(), resourceIndex);
    }

    FileStore getFileStore() throws IOException {
        if (exists()) {
            return new NativeImageResourceFileStore(this);
        }
        throw new NoSuchFileException(NativeImageResourceFileSystem.getString(path));
    }

    boolean isSameFile(Path other) throws IOException {
        if (this.equals(other)) {
            return true;
        }
        if (other == null || this.getFileSystem() != other.getFileSystem()) {
            return false;
        }
        this.checkAccess();
        ((NativeImageResourcePath) other).checkAccess();
        NativeImageResourcePath otherResourcePath = (NativeImageResourcePath) other;
        return rootId == otherResourcePath.rootId && getResourceIndex() == otherResourcePath.getResourceIndex() && Arrays.equals(this.getResolvedPath(), otherResourcePath.getResolvedPath());
    }

    void checkAccess(AccessMode... modes) throws IOException {
        boolean x = false;
        for (AccessMode mode : modes) {
            switch (mode) {
                case READ:
                case WRITE:
                    break;
                case EXECUTE:
                    x = true;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
        fileSystem.checkAccess(getResolvedPath(), resourceIndex);
        if (x) {
            throw new AccessDeniedException(toString());
        }
    }

    void setAttribute(String attribute, Object value) throws IOException {
        String type;
        String attr;
        int colonPos = attribute.indexOf(':');
        if (colonPos == -1) {
            type = "basic";
            attr = attribute;
        } else {
            type = attribute.substring(0, colonPos++);
            attr = attribute.substring(colonPos);
        }
        NativeImageResourceFileAttributesView view = NativeImageResourceFileAttributesView.get(this, type);
        if (view == null) {
            throw new UnsupportedOperationException("View is not supported");
        }
        view.setAttribute(attr, value);
    }

    void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws NoSuchFileException {
        fileSystem.setTimes(getResolvedPath(), lastModifiedTime, lastAccessTime, createTime);
    }

    void createDirectory() throws IOException {
        fileSystem.createDirectory(getResolvedPath());
    }

    void delete() throws IOException {
        fileSystem.deleteFile(getResolvedPath(), true);
    }

    boolean deleteIfExists() throws IOException {
        return fileSystem.deleteFile(getResolvedPath(), false);
    }

    void move(NativeImageResourcePath target, CopyOption... options) throws IOException {
        if (Files.isSameFile(this.fileSystem.getResourcePath(), target.fileSystem.getResourcePath())) {
            fileSystem.copyFile(true, getResolvedPath(), target.getResolvedPath(), options);
        } else {
            copyToTarget(target, options);
            delete();
        }
    }

    void copy(NativeImageResourcePath target, CopyOption... options) throws IOException {
        if (Files.isSameFile(this.fileSystem.getResourcePath(), target.fileSystem.getResourcePath())) {
            fileSystem.copyFile(false, getResolvedPath(), target.getResolvedPath(), options);
        } else {
            copyToTarget(target, options);
        }
    }

    private void copyToTarget(NativeImageResourcePath target, CopyOption... options) throws IOException {
        boolean replaceExisting = false;
        boolean copyAttrs = false;
        for (CopyOption opt : options) {
            if (opt == REPLACE_EXISTING) {
                replaceExisting = true;
            } else if (opt == COPY_ATTRIBUTES) {
                copyAttrs = true;
            }
        }
        // Attributes of source file.
        NativeImageResourceFileAttributes nativeImageResourceFileAttributes = getAttributes();

        // Check if target exists.
        boolean exists;
        if (replaceExisting) {
            try {
                target.deleteIfExists();
                exists = false;
            } catch (DirectoryNotEmptyException x) {
                exists = true;
            }
        } else {
            exists = target.exists();
        }
        if (exists) {
            throw new FileAlreadyExistsException(target.toString());
        }

        if (nativeImageResourceFileAttributes.isDirectory()) {
            // Create directory or file.
            target.createDirectory();
        } else {
            try (InputStream is = fileSystem.newInputStream(getResolvedPath(), getResourceIndex(), moduleName)) {
                try (OutputStream os = target.newOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        os.write(buf, 0, n);
                    }
                }
            }
        }
        if (copyAttrs) {
            BasicFileAttributeView attributeView = NativeImageResourceFileAttributesView.get(target, BasicFileAttributeView.class);
            try {
                attributeView.setTimes(nativeImageResourceFileAttributes.lastModifiedTime(), nativeImageResourceFileAttributes.lastAccessTime(),
                                nativeImageResourceFileAttributes.creationTime());
            } catch (IOException e) {
                try {
                    target.delete();
                } catch (IOException ignore) {
                }
                throw e;
            }
        }
    }

    InputStream newInputStream(OpenOption... options) throws IOException {
        if (options.length > 0) {
            for (OpenOption opt : options) {
                if (opt != READ) {
                    throw new UnsupportedOperationException("'" + opt + "' not allowed");
                }
            }
        }
        return fileSystem.newInputStream(getResolvedPath(), getResourceIndex(), moduleName);
    }

    OutputStream newOutputStream(OpenOption... options) throws IOException {
        if (options.length == 0) {
            return fileSystem.newOutputStream(getResolvedPath(), CREATE, TRUNCATE_EXISTING, WRITE);
        }
        return fileSystem.newOutputStream(getResolvedPath(), options);
    }

    FileChannel newFileChannel(Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return fileSystem.newFileChannel(getResolvedPath(), getResourceIndex(), moduleName, options, attrs);
    }

    boolean isHidden() {
        return false;
    }
}
