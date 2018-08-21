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
package com.oracle.truffle.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.graalvm.polyglot.io.FileSystem;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * An abstract representation of a file used by Truffle languages.
 *
 * @since 1.0
 */
public final class TruffleFile {
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;
    private static final int BUFFER_SIZE = 8192;

    private final FileSystem fileSystem;
    private final Path path;
    private final Path normalizedPath;

    TruffleFile(final FileSystem fileSystem, final Path path) {
        this(fileSystem, path, path.normalize());
    }

    TruffleFile(final FileSystem fileSystem, final Path path, final Path normalizedPath) {
        Objects.requireNonNull(fileSystem, "FileSystem must not be null.");
        Objects.requireNonNull(path, "Path must not be null.");
        Objects.requireNonNull(normalizedPath, "NormalizedPath must not be null.");
        this.fileSystem = fileSystem;
        this.path = path;
        this.normalizedPath = normalizedPath;
    }

    /**
     * Tests existence of a file.
     *
     * @param options the options determining how the symbolic links should be handled
     * @return {@code true} if the file exists
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public boolean exists(LinkOption... options) {
        try {
            return checkAccess(EnumSet.noneOf(AccessMode.class), options);
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if a file is readable. Checks if the file exists and this Java virtual machine has
     * enough privileges to read the file.
     *
     * @return {@code true} if the file exists and is readable
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public boolean isReadable() {
        try {
            return checkAccess(AccessMode.READ);
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if a file is writable. Checks if the file exists and this Java virtual machine has
     * enough privileges to write to the file.
     *
     * @return {@code true} if the file exists and is writable
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public boolean isWritable() {
        try {
            return checkAccess(AccessMode.WRITE);
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if a file is executable. Checks if the file exists and this Java virtual machine has
     * enough privileges to execute the file.
     *
     * @return {@code true} if the file exists and is executable
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public boolean isExecutable() {
        try {
            return checkAccess(AccessMode.EXECUTE);
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if a file is a directory. Checks if the file exists and is a directory.
     *
     * @param options the options determining how the symbolic links should be handled, by default
     *            the symbolic links are followed.
     * @return {@code true} if the file exists and is a directory
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public boolean isDirectory(LinkOption... options) {
        try {
            return getAttributeImpl("isDirectory", Boolean.class, options);
        } catch (IOException ioe) {
            return false;
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if a file is a regular file. Checks if the file exists and is a regular file.
     *
     * @param options the options determining how the symbolic links should be handled, by default
     *            the symbolic links are followed.
     * @return {@code true} if the file exists and is a regular file
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public boolean isRegularFile(LinkOption... options) {
        try {
            return getAttributeImpl("isRegularFile", Boolean.class, options);
        } catch (IOException ioe) {
            return false;
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if a file is a symbolic link. Checks if the file exists and is a symbolic link.
     *
     * @return {@code true} if the file exists and is a symbolic link
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public boolean isSymbolicLink() {
        try {
            return getAttributeImpl("isSymbolicLink", Boolean.class);
        } catch (IOException ioe) {
            return false;
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if this {@link TruffleFile}'s path is absolute.
     *
     * @return {@code true} if the file path is absolute
     * @since 1.0
     */
    @TruffleBoundary
    public boolean isAbsolute() {
        try {
            return path.isAbsolute();
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the name of this {@link TruffleFile}.
     *
     * @return the name of file or directory denoted by this {@link TruffleFile}
     * @since 1.0
     */
    @TruffleBoundary
    public String getName() {
        try {
            return path.getFileName().toString();
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the string representation of this {@link TruffleFile}.
     *
     * @return the path of this {@link TruffleFile}
     * @since 1.0
     */
    @TruffleBoundary
    public String getPath() {
        try {
            return path.toString();
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the {@link URI} representation of this {@link TruffleFile}.
     *
     * @return the absolute {@link URI} representing the {@link TruffleFile}
     * @throws SecurityException if the {@link FileSystem} denied a resolution of an absolute path
     * @since 1.0
     */
    @TruffleBoundary
    public URI toUri() {
        try {
            final Path absolutePath = path.isAbsolute() ? path : toAbsolutePathImpl()[0];
            return absolutePath.toUri();
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Resolves this {@link TruffleFile} to absolute {@link TruffleFile}. If this
     * {@link TruffleFile} is already absolute this method returns this {@link TruffleFile} without
     * any resolution.
     *
     * @return the absolute {@link TruffleFile}
     * @throws SecurityException if the {@link FileSystem} denied a resolution of an absolute path
     * @since 1.0
     */
    @TruffleBoundary
    public TruffleFile getAbsoluteFile() {
        if (path.isAbsolute()) {
            return this;
        }
        try {
            Path[] absolutePaths = toAbsolutePathImpl();
            return new TruffleFile(fileSystem, absolutePaths[0], absolutePaths[1]);
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns a {@link TruffleFile} representing the real (canonical) path of an existing file.
     *
     * @param options the options determining how the symbolic links should be handled
     * @return a {@link TruffleFile} representing the absolute canonical path
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public TruffleFile getCanonicalFile(LinkOption... options) throws IOException {
        try {
            return new TruffleFile(fileSystem, fileSystem.toRealPath(path, options));
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns a parent {@link TruffleFile} or null when the file does not have a parent.
     *
     * @return the parent {@link TruffleFile}
     * @since 1.0
     */
    @TruffleBoundary
    public TruffleFile getParent() {
        try {
            final Path parent = path.getParent();
            return parent == null ? null : new TruffleFile(fileSystem, parent);
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Resolves given string path representation against this {@link TruffleFile}.
     *
     * @param name the path to resolve
     * @return the resolved {@link TruffleFile}
     * @throws InvalidPathException if the path string contains non valid characters
     * @since 1.0
     */
    @TruffleBoundary
    public TruffleFile resolve(String name) {
        try {
            return new TruffleFile(fileSystem, path.resolve(name));
        } catch (InvalidPathException ip) {
            throw ip;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Resolves given string path representation against the parent of this {@link TruffleFile}.
     *
     * @param name the path to resolve
     * @return the resolved {@link TruffleFile}
     * @throws InvalidPathException if the path string contains non valid characters
     * @since 1.0
     */
    @TruffleBoundary
    public TruffleFile resolveSibling(String name) {
        try {
            return new TruffleFile(fileSystem, path.resolveSibling(name));
        } catch (InvalidPathException ip) {
            throw ip;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the size of a file.
     *
     * @param options the options determining how the symbolic links should be handled
     * @return the file size in bytes
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public long size(LinkOption... options) throws IOException {
        try {
            return getAttributeImpl("size", Long.class, options);
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the last modified time.
     *
     * @param options the options determining how the symbolic links should be handled
     * @return the {@link FileTime} representing the time this {@link TruffleFile} was last modified
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public FileTime getLastModifiedTime(LinkOption... options) throws IOException {
        try {
            return getAttributeImpl("lastModifiedTime", FileTime.class, options);
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Sets the file's last modified time.
     *
     * @param time the new value of the last modified time
     * @param options the options determining how the symbolic links should be handled
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void setLastModifiedTime(FileTime time, LinkOption... options) throws IOException {
        try {
            fileSystem.setAttribute(normalizedPath, "lastModifiedTime", time, options);
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the last access time.
     *
     * @param options the options determining how the symbolic links should be handled
     * @return the {@link FileTime} representing the time this {@link TruffleFile} was last accessed
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public FileTime getLastAccessTime(LinkOption... options) throws IOException {
        try {
            return getAttributeImpl("lastAccessTime", FileTime.class, options);
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Sets the file's last access time.
     *
     * @param time the new value of the last access time
     * @param options the options determining how the symbolic links should be handled
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void setLastAccessTime(FileTime time, LinkOption... options) throws IOException {
        try {
            fileSystem.setAttribute(normalizedPath, "lastAccessTime", time, options);
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the creation time.
     *
     * @param options the options determining how the symbolic links should be handled
     * @return the {@link FileTime} representing the time this {@link TruffleFile} was created
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public FileTime getCreationTime(LinkOption... options) throws IOException {
        try {
            return getAttributeImpl("creationTime", FileTime.class, options);
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Sets the file's creation time.
     *
     * @param time the new value of the creation time
     * @param options the options determining how the symbolic links should be handled
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void setCreationTime(FileTime time, LinkOption... options) throws IOException {
        try {
            fileSystem.setAttribute(normalizedPath, "creationTime", time, options);
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns a collection of {@link TruffleFile}s in the directory denoted by this
     * {@link TruffleFile}.
     *
     * @return a collection of {@link TruffleFile}s located in the directory denoted by this
     *         {@link TruffleFile}
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public Collection<TruffleFile> list() throws IOException {
        try {
            final Collection<TruffleFile> result = new ArrayList<>();
            final boolean normalized = isNormalized();
            try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(normalizedPath, AllFiles.INSTANCE)) {
                for (Path p : stream) {
                    result.add(new TruffleFile(fileSystem, normalized ? p : path.resolve(p.getFileName()), p));
                }
            }
            return result;
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Opens or creates a file returning a {@link SeekableByteChannel} to access the file content.
     *
     * @param options the options specifying how the file should be opened
     * @param attributes the optional attributes to set atomically when creating the new file
     * @return the created {@link SeekableByteChannel}
     * @throws FileAlreadyExistsException if {@link StandardOpenOption#CREATE_NEW} option is set and
     *             a file already exists on given path
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the attributes contain an attribute which cannot be
     *             set atomically
     * @throws IllegalArgumentException in case of invalid options combination
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public SeekableByteChannel newByteChannel(Set<? extends OpenOption> options, FileAttribute<?>... attributes) throws IOException {
        try {
            return ByteChannelDecorator.create(fileSystem.newByteChannel(normalizedPath, options, attributes));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Opens a file for reading returning an {@link InputStream} to access the file content.
     *
     * @param options the options specifying how the file should be opened
     * @return the created {@link InputStream}
     * @throws IOException in case of IO error
     * @throws IllegalArgumentException in case of invalid options combination
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public InputStream newInputStream(OpenOption... options) throws IOException {
        final Set<OpenOption> openOptions = new HashSet<>();
        if (options.length > 0) {
            for (OpenOption option : options) {
                if (option == StandardOpenOption.APPEND || option == StandardOpenOption.WRITE) {
                    throw new IllegalArgumentException(String.format("Option %s is not allowed.", option));
                }
                openOptions.add(option);
            }
        }
        return Channels.newInputStream(newByteChannel(openOptions));
    }

    /**
     * Opens a file for reading returning a {@link BufferedReader} to access the file content.
     *
     * @param charset the file encoding
     * @return the created {@link BufferedReader}
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public BufferedReader newBufferedReader(Charset charset) throws IOException {
        return new BufferedReader(new InputStreamReader(newInputStream(), charset));
    }

    /**
     * Opens a file for reading returning a {@link BufferedReader} to access the file content.
     *
     * @return the created {@link BufferedReader}
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public BufferedReader newBufferedReader() throws IOException {
        return newBufferedReader(StandardCharsets.UTF_8);
    }

    /**
     * Reads a file content as bytes.
     *
     * @return the created {@link BufferedReader}
     * @throws IOException in case of IO error
     * @throws OutOfMemoryError if an array of a file size cannot be allocated
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public byte[] readAllBytes() throws IOException {
        try (SeekableByteChannel channel = newByteChannel(Collections.emptySet())) {
            long sizel = channel.size();
            if (sizel > MAX_BUFFER_SIZE) {
                throw new OutOfMemoryError("File size is too large.");
            }
            try (InputStream in = Channels.newInputStream(channel)) {
                int size = (int) sizel;
                byte[] buf = new byte[size];
                int read = 0;
                while (true) {
                    int n;
                    while ((n = in.read(buf, read, size - read)) > 0) {
                        read += n;
                    }
                    if (n < 0 || (n = in.read()) < 0) {
                        break;
                    }
                    if (size << 1 <= MAX_BUFFER_SIZE) {
                        size = Math.max(size << 1, BUFFER_SIZE);
                    } else if (size == MAX_BUFFER_SIZE) {
                        throw new OutOfMemoryError("Required array size too large");
                    } else {
                        size = MAX_BUFFER_SIZE;
                    }
                    buf = Arrays.copyOf(buf, size);
                    buf[read++] = (byte) n;
                }
                return size == read ? buf : Arrays.copyOf(buf, read);
            }
        } catch (IOException | OutOfMemoryError | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Opens a file for writing returning an {@link OutputStream}.
     *
     * @param options the options specifying how the file should be opened
     * @return the created {@link OutputStream}
     * @throws IOException in case of IO error
     * @throws IllegalArgumentException in case of invalid options combination
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public OutputStream newOutputStream(OpenOption... options) throws IOException {
        final Set<OpenOption> openOptions = new HashSet<>(Math.max(options.length, 2) + 1);
        openOptions.add(StandardOpenOption.WRITE);
        if (options.length == 0) {
            openOptions.add(StandardOpenOption.CREATE);
            openOptions.add(StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            for (OpenOption option : options) {
                if (option == StandardOpenOption.READ) {
                    throw new IllegalArgumentException(String.format("Option %s is not allowed.", option));
                }
                openOptions.add(option);
            }
        }
        return Channels.newOutputStream(newByteChannel(openOptions));
    }

    /**
     * Opens a file for writing returning an {@link BufferedWriter}.
     *
     * @param charset the file encoding
     * @param options the options specifying how the file should be opened
     * @return the created {@link BufferedWriter}
     * @throws IOException in case of IO error
     * @throws IllegalArgumentException in case of invalid options combination
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public BufferedWriter newBufferedWriter(Charset charset, OpenOption... options) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(newOutputStream(options), charset));
    }

    /**
     * Opens a file for writing returning an {@link BufferedWriter}.
     *
     * @param options the options specifying how the file should be opened
     * @return the created {@link BufferedWriter}
     * @throws IOException in case of IO error
     * @throws IllegalArgumentException in case of invalid options combination
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public BufferedWriter newBufferedWriter(OpenOption... options) throws IOException {
        return newBufferedWriter(StandardCharsets.UTF_8, options);
    }

    /**
     * Creates a new empty file.
     *
     * @param attributes the optional attributes to set atomically when creating the new file
     * @throws FileAlreadyExistsException if the file already exists on given path
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the attributes contain an attribute which cannot be
     *             set atomically
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void createFile(FileAttribute<?>... attributes) throws IOException {
        newByteChannel(
                        EnumSet.<StandardOpenOption> of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW),
                        attributes).close();
    }

    /**
     * Creates a new directory.
     *
     * @param attributes the optional attributes to set atomically when creating the new file
     * @throws FileAlreadyExistsException if the file or directory already exists on given path
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the attributes contain an attribute which cannot be
     *             set atomically
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void createDirectory(FileAttribute<?>... attributes) throws IOException {
        try {
            createDirectoryImpl(normalizedPath, attributes);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Creates a directory and all nonexistent parent directories. Unlike the
     * {@link #createDirectory} the {@link FileAlreadyExistsException} is not thrown if the
     * directory already exists.
     *
     * @param attributes the optional attributes to set atomically when creating the new file
     * @throws FileAlreadyExistsException if a file (not a directory) already exists on given path
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the attributes contain an attribute which cannot be
     *             set atomically
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void createDirectories(FileAttribute<?>... attributes) throws IOException {
        try {
            try {
                createDirAndCheck(normalizedPath, attributes);
                return;
            } catch (FileAlreadyExistsException faee) {
                throw faee;
            } catch (IOException ioe) {
                // Try to create parents
            }
            SecurityException notAllowed = null;
            Path absolutePath = normalizedPath;
            try {
                absolutePath = fileSystem.toAbsolutePath(absolutePath);
            } catch (SecurityException se) {
                notAllowed = se;
            }
            Path lastExisting = findExisting(absolutePath);
            if (lastExisting == null) {
                if (notAllowed != null) {
                    throw notAllowed;
                } else {
                    throw new FileSystemException(path.toString(), null, "Cannot determine root");
                }
            }
            for (Path pathElement : lastExisting.relativize(absolutePath)) {
                lastExisting = lastExisting.resolve(pathElement);
                createDirAndCheck(lastExisting, attributes);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Deletes the file. If the {@link TruffleFile} denotes a directory, the directory must be empty
     * before deleting. If the {@link TruffleFile} denotes a symbolic link the symbolic link itself
     * is deleted not its target.
     *
     * @throws NoSuchFileException if the file does not exist
     * @throws DirectoryNotEmptyException if the {@link TruffleFile} denotes a non empty directory
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void delete() throws IOException {
        try {
            fileSystem.delete(normalizedPath);
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Moves or renames the file.
     *
     * @param target the path of a target file
     * @param options the options specifying how the move should be performed, see
     *            {@link StandardCopyOption}
     * @throws UnsupportedOperationException if {@code options} contains unsupported option
     * @throws FileAlreadyExistsException if the target path already exists and the {@code options}
     *             don't contain {@link StandardCopyOption#REPLACE_EXISTING} option
     * @throws DirectoryNotEmptyException if the {@code options} contain
     *             {@link StandardCopyOption#REPLACE_EXISTING} but the {@code target} is a non empty
     *             directory
     * @throws AtomicMoveNotSupportedException if the {@code options} contain
     *             {@link StandardCopyOption#ATOMIC_MOVE} but file cannot be moved atomically
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void move(TruffleFile target, CopyOption... options) throws IOException {
        try {
            fileSystem.move(normalizedPath, target.normalizedPath, options);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the file's Posix permissions.
     *
     * @param linkOptions the options determining how the symbolic links should be handled
     * @return the the file's Posix permissions
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException when the Posix permissions are not supported by
     *             filesystem
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    @SuppressWarnings("unchecked")
    public Set<PosixFilePermission> getPosixPermissions(LinkOption... linkOptions) throws IOException {
        try {
            return (Set<PosixFilePermission>) getAttributeImpl(normalizedPath, "posix:permissions", linkOptions);
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Sets the file's Posix permissions.
     *
     * @param permissions the Posix permissions to set
     * @param linkOptions the options determining how the symbolic links should be handled
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException when the Posix permissions are not supported by
     *             filesystem
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void setPosixPermissions(Set<? extends PosixFilePermission> permissions, LinkOption... linkOptions) throws IOException {
        try {
            fileSystem.setAttribute(normalizedPath, "posix:permissions", permissions, linkOptions);
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    @TruffleBoundary
    public String toString() {
        return path.toString();
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    @TruffleBoundary
    public int hashCode() {
        int res = 17;
        res = res * 31 + fileSystem.hashCode();
        res = res * 31 + path.hashCode();
        return res;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    @TruffleBoundary
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || other.getClass() != TruffleFile.class) {
            return false;
        }
        final TruffleFile otherFile = (TruffleFile) other;
        return path.equals(otherFile.path) && fileSystem.equals(otherFile.fileSystem);
    }

    /**
     * Returns a {@link TruffleFile} with removed redundant name elements in it's path.
     *
     * @return the normalized {@link TruffleFile}
     * @since 1.0
     */
    @TruffleBoundary
    public TruffleFile normalize() {
        if (isNormalized()) {
            return this;
        }
        return new TruffleFile(fileSystem, normalizedPath, normalizedPath);
    }

    private boolean isNormalized() {
        return path == normalizedPath || path.equals(normalizedPath);
    }

    private Path[] toAbsolutePathImpl() {
        Path normalizedAbsolute = fileSystem.toAbsolutePath(normalizedPath);
        if (isNormalized()) {
            return new Path[]{normalizedAbsolute, normalizedAbsolute};
        } else {
            Path root = fileSystem.parsePath("/");
            boolean emptyPath = normalizedPath.getFileName().getNameCount() == 1 && normalizedPath.getFileName().toString().isEmpty();
            Path absolute = root.resolve(normalizedAbsolute.subpath(0, normalizedAbsolute.getNameCount() - (emptyPath ? 0 : normalizedPath.getNameCount()))).resolve(path);
            return new Path[]{absolute, normalizedAbsolute};
        }
    }

    private boolean checkAccess(AccessMode... modes) {
        final Set<AccessMode> modesSet = EnumSet.noneOf(AccessMode.class);
        Collections.addAll(modesSet, modes);
        return checkAccess(modesSet);
    }

    private boolean checkAccess(Set<? extends AccessMode> modes, LinkOption... linkOptions) {
        try {
            fileSystem.checkAccess(normalizedPath, modes, linkOptions);
            return true;
        } catch (IOException ioe) {
            return false;
        }
    }

    private <T> T getAttributeImpl(String attribute, Class<T> type, LinkOption... options) throws IOException {
        return getAttributeImpl(normalizedPath, attribute, type, options);
    }

    private <T> T getAttributeImpl(Path forPath, String attribute, Class<T> type, LinkOption... options) throws IOException {
        final Object value = getAttributeImpl(forPath, attribute, options);
        return value == null ? null : type.cast(value);
    }

    private Object getAttributeImpl(final Path forPath, final String attribute, final LinkOption... options) throws IOException {
        final Map<String, Object> map = fileSystem.readAttributes(forPath, attribute, options);
        final int index = attribute.indexOf(':');
        final String key = index < 0 ? attribute : attribute.substring(index + 1);
        return map.get(key);
    }

    private Path createDirectoryImpl(Path dir, FileAttribute<?>... attrs) throws IOException {
        fileSystem.createDirectory(dir, attrs);
        return dir;
    }

    private Path createDirAndCheck(Path dir, FileAttribute<?>... attrs) throws IOException {
        try {
            return createDirectoryImpl(dir, attrs);
        } catch (FileAlreadyExistsException faee) {
            try {
                if (getAttributeImpl(dir, "isDirectory", Boolean.class, LinkOption.NOFOLLOW_LINKS)) {
                    return dir;
                } else {
                    throw faee;
                }
            } catch (IOException ioe) {
                throw faee;
            }
        }
    }

    private Path findExisting(Path forPath) throws IOException {
        final Set<AccessMode> mode = EnumSet.noneOf(AccessMode.class);
        for (Path p = forPath.getParent(); p != null; p = p.getParent()) {
            try {
                fileSystem.checkAccess(p, mode);
                return p;
            } catch (NoSuchFileException nsfe) {
                // Still does not exist
            }
        }
        return null;
    }

    private <T extends Throwable> RuntimeException wrapHostException(T t) {
        if (TruffleLanguage.AccessAPI.engineAccess().isDefaultFileSystem(fileSystem)) {
            throw sthrow(t);
        }
        throw TruffleLanguage.AccessAPI.engineAccess().wrapHostException(null, t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends RuntimeException> T sthrow(final Throwable t) throws T {
        throw (T) t;
    }

    private static final class AllFiles implements DirectoryStream.Filter<Path> {
        static final DirectoryStream.Filter<Path> INSTANCE = new AllFiles();

        private AllFiles() {
        }

        @Override
        public boolean accept(Path entry) throws IOException {
            return true;
        }
    }

    private static final class ByteChannelDecorator implements SeekableByteChannel {

        private final SeekableByteChannel delegate;

        ByteChannelDecorator(final SeekableByteChannel delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return delegate.read(dst);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return delegate.write(src);
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            delegate.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            delegate.truncate(size);
            return this;
        }

        static SeekableByteChannel create(final SeekableByteChannel delegate) {
            Objects.requireNonNull(delegate, "Delegate must be non null.");
            return new ByteChannelDecorator(delegate);
        }
    }

    @SuppressWarnings("serial")
    static final class FileAdapter extends File {
        private final TruffleFile truffleFile;

        FileAdapter(TruffleFile truffleFile) {
            super(truffleFile.getPath());
            this.truffleFile = truffleFile;
        }

        TruffleFile getTruffleFile() {
            return truffleFile;
        }

        @Override
        public String getName() {
            return truffleFile.getName();
        }

        @Override
        public String getPath() {
            return truffleFile.getPath();
        }

        @Override
        public File getAbsoluteFile() {
            return new FileAdapter(truffleFile.getAbsoluteFile());
        }

        @Override
        public File getCanonicalFile() throws IOException {
            return new FileAdapter(truffleFile.getCanonicalFile());
        }

        @Override
        public URI toURI() {
            return truffleFile.toUri();
        }
    }

}
