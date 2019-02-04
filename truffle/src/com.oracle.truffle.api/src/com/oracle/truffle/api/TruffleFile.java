/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
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
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
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

    Path getSPIPath() {
        return normalizedPath;
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
            return getAttributeImpl("isSymbolicLink", Boolean.class, LinkOption.NOFOLLOW_LINKS);
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
     * @return the name of file or directory denoted by this {@link TruffleFile}, or {@code null} if
     *         the file is a root directory
     * @since 1.0
     */
    @TruffleBoundary
    public String getName() {
        try {
            final Path fileName = path.getFileName();
            return fileName == null ? null : fileName.toString();
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
     * Returns the absolute {@link URI} representation of this {@link TruffleFile}.
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
     * Returns a relative {@link URI} representation of non absolute {@link TruffleFile}. If this
     * {@link TruffleFile} is relative it returns a relative {@link URI}. For an
     * {@link #isAbsolute() absolute} {@link TruffleFile} it returns an absolute {@link URI}.
     *
     * @return the {@link URI} representing the {@link TruffleFile}
     * @since 1.0
     */
    @TruffleBoundary
    public URI toRelativeUri() {
        if (isAbsolute()) {
            return toUri();
        }
        try {
            String strPath = "/".equals(fileSystem.getSeparator()) ? path.toString() : path.toString().replace(path.getFileSystem().getSeparator(), "/");
            return new URI(null, null, strPath, null);
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
                    result.add(new TruffleFile(
                                    fileSystem,
                                    normalized ? p : path.resolve(p.getFileName()),
                                    normalized ? p : normalizedPath.resolve(p.getFileName())));
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

    /**
     * Creates a {@link TruffleFile} with a relative path between this {@link TruffleFile} and a
     * given {@link TruffleFile}.
     * <p>
     * Relativization is the inverse of {@link #resolve(java.lang.String) resolution}.
     * Relativization constructs a {@link TruffleFile} with relative path that when
     * {@link #resolve(java.lang.String) resolved} against this {@link TruffleFile} yields a
     * {@link TruffleFile} locating the same file as given {@link TruffleFile}. A relative path
     * cannot be constructed if only one of the {@link TruffleFile}s is {@link #isAbsolute()
     * absolute}.
     *
     * @param other the {@link TruffleFile} to relativize against this {@link TruffleFile}
     * @return the {@link TruffleFile} with relative path between this and {@code other}
     *         {@link TruffleFile}s
     * @throws IllegalArgumentException when {@code other} cannot be relativized against this
     *             {@link TruffleFile}
     * @since 1.0
     */
    @TruffleBoundary
    public TruffleFile relativize(TruffleFile other) {
        try {
            return new TruffleFile(fileSystem, path.relativize(other.path));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if this {@link TruffleFile} path starts with the given path. The path {@code foo/bar}
     * starts with {@code foo} and {@code foo/bar} but does not start with {@code f}.
     *
     * @param other the path
     * @return {@code true} if this {@link TruffleFile} path starts with given path
     * @throws IllegalArgumentException if the path cannot be parsed.
     * @since 1.0
     */
    @TruffleBoundary
    public boolean startsWith(String other) {
        try {
            return path.startsWith(other);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if this {@link TruffleFile} path starts with the given {@link TruffleFile} path. The
     * path {@code foo/bar} starts with {@code foo} and {@code foo/bar} but does not start with
     * {@code f}.
     *
     * @param other the {@link TruffleFile}
     * @return {@code true} if this {@link TruffleFile} path starts with given {@link TruffleFile}
     *         path
     * @since 1.0
     */
    @TruffleBoundary
    public boolean startsWith(TruffleFile other) {
        try {
            return path.startsWith(other.path);
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if this {@link TruffleFile} path ends with the given path. The path {@code foo/bar}
     * ends with {@code bar} and {@code foo/bar} but does not end with {@code r}.
     *
     * @param other the path
     * @return {@code true} if this {@link TruffleFile} path ends with given path
     * @throws IllegalArgumentException if the path cannot be parsed.
     * @since 1.0
     */
    @TruffleBoundary
    public boolean endsWith(String other) {
        try {
            return path.endsWith(other);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Tests if this {@link TruffleFile} path ends with the given {@link TruffleFile} path. The path
     * {@code foo/bar} ends with {@code bar} and {@code foo/bar} but does not end with {@code r}.
     *
     * @param other the {@link TruffleFile}
     * @return {@code true} if this {@link TruffleFile} path ends with given {@link TruffleFile}
     *         path
     * @since 1.0
     */
    @TruffleBoundary
    public boolean endsWith(TruffleFile other) {
        try {
            return path.endsWith(other.path);
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Creates a new link to an existing target <i>(optional operation)</i>.
     *
     * @param target the existing file to link
     * @throws FileAlreadyExistsException if the file or directory already exists on given path
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the {@link FileSystem} implementation does not
     *             support links
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void createLink(TruffleFile target) throws IOException {
        try {
            fileSystem.createLink(normalizedPath, target.normalizedPath);
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Creates a symbolic link to a target <i>(optional operation)</i>.
     *
     * @param target the target of the symbolic link
     * @param attrs the optional attributes to set atomically when creating the symbolic link
     * @throws FileAlreadyExistsException if the file or directory already exists on given path
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the {@link FileSystem} implementation does not
     *             support symbolic links or the attributes contain an attribute which cannot be set
     *             atomically
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void createSymbolicLink(TruffleFile target, FileAttribute<?>... attrs) throws IOException {
        try {
            fileSystem.createSymbolicLink(normalizedPath, target.path, attrs);
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the owner of the file.
     *
     * @param options the options determining how the symbolic links should be handled
     * @return the file owner
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the {@link FileSystem} implementation does not
     *             support owner attribute
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public UserPrincipal getOwner(LinkOption... options) throws IOException {
        try {
            return getAttributeImpl("posix:owner", UserPrincipal.class, options);
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Returns the group owner of the file.
     *
     * @param options the options determining how the symbolic links should be handled
     * @return the file owner
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the {@link FileSystem} implementation does not
     *             support group owner attribute
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public GroupPrincipal getGroup(LinkOption... options) throws IOException {
        try {
            return getAttributeImpl("posix:group", GroupPrincipal.class, options);
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Opens a directory, returning a {@link DirectoryStream} to iterate over all entries in the
     * directory.
     * <p>
     * The {@link TruffleFile}s returned by the directory stream's {@link DirectoryStream#iterator
     * iterator} are created as if by {@link #resolve(java.lang.String) resolving} the name of the
     * directory entry against this {@link TruffleFile}.
     * <p>
     * When not using the try-with-resources construct, then the directory stream's
     * {@link DirectoryStream#close() close} method should be called after iteration is completed.
     * <p>
     * The code which iterates over all files can use simpler {@link #list()} method.
     *
     * @return a new opened {@link DirectoryStream} object
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public DirectoryStream<TruffleFile> newDirectoryStream() throws IOException {
        try {
            return new TruffleFileDirectoryStream(this, fileSystem.newDirectoryStream(normalizedPath, AllFiles.INSTANCE));
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Visits this {@link TruffleFile} file tree.
     *
     * <p>
     * This method walks a file tree rooted at this {@link TruffleFile}. The file tree traversal is
     * <em>depth-first</em>. The appropriate method on give {@link FileVisitor} is invoked for each
     * met file. File tree traversal completes when all accessible files in the tree have been
     * visited, a {@link FileVisitor} visit method returns a {@link FileVisitResult#TERMINATE} or a
     * {@link FileVisitor} method terminates due to an uncaught exception.
     *
     * <p>
     * For each file encountered this method attempts to read its
     * {@link java.nio.file.attribute.BasicFileAttributes}. If the file is not a directory then the
     * {@link FileVisitor#visitFile visitFile} method is invoked with the file attributes. If the
     * file attributes cannot be read, due to an I/O exception, then the
     * {@link FileVisitor#visitFileFailed visitFileFailed} method is invoked with the I/O exception.
     *
     * <p>
     * Where the file is a directory, and the directory could not be opened, then the
     * {@code visitFileFailed} method is invoked with the I/O exception, after which, the file tree
     * walk continues, by default, at the next <em>sibling</em> of the directory.
     *
     * <p>
     * Where the directory is opened successfully, then the entries in the directory, and their
     * <em>descendants</em> are visited. When all entries have been visited, or an I/O error occurs
     * during iteration of the directory, then the directory is closed and the visitor's
     * {@link FileVisitor#postVisitDirectory postVisitDirectory} method is invoked. The file tree
     * walk then continues, by default, at the next <em>sibling</em> of the directory.
     *
     * <p>
     * By default, symbolic links are not automatically followed by this method. If the
     * {@code options} parameter contains the {@link FileVisitOption#FOLLOW_LINKS FOLLOW_LINKS}
     * option then symbolic links are followed.
     *
     * <p>
     * The {@code maxDepth} parameter is the maximum number of levels of directories to visit. A
     * value of {@code 0} means that only the starting file is visited. The {@code visitFile} method
     * is invoked for all files, including directories, encountered at {@code maxDepth}, unless the
     * basic file attributes cannot be read, in which case the {@code
     * visitFileFailed} method is invoked.
     *
     * @param visitor the {@link FileVisitor} to invoke for each file
     * @param maxDepth the maximum number of directory levels to visit, {@link Integer#MAX_VALUE
     *            MAX_VALUE} may be used to indicate that all levels should be visited.
     * @param options the options configuring the file tree traversal
     * @throws IllegalArgumentException if the {@code maxDepth} parameter is negative
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void visit(FileVisitor<TruffleFile> visitor, int maxDepth, FileVisitOption... options) throws IOException {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("The maxDepth must be >= 0");
        }
        try {
            Walker walker = new Walker(this, maxDepth, options);
            for (Walker.Event event : walker) {
                FileVisitResult result;
                switch (event.type) {
                    case PRE_VISIT_DIRECTORY:
                        result = visitor.preVisitDirectory(event.file, event.attrs);
                        if (result == FileVisitResult.SKIP_SUBTREE || result == FileVisitResult.SKIP_SIBLINGS) {
                            walker.pop();
                        }
                        break;
                    case VISIT:
                        IOException ioe = event.ioe;
                        if (ioe == null) {
                            result = visitor.visitFile(event.file, event.attrs);
                        } else {
                            result = visitor.visitFileFailed(event.file, ioe);
                        }
                        break;
                    case POST_VISIT_DIRECTORY:
                        result = visitor.postVisitDirectory(event.file, event.ioe);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected event type: " + event.type);
                }
                if (Objects.requireNonNull(result) != FileVisitResult.CONTINUE) {
                    switch (result) {
                        case SKIP_SIBLINGS:
                            walker.skipRemainingSiblings();
                            break;
                        case TERMINATE:
                            return;
                    }
                }
            }
        } catch (IOException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    /**
     * Copies the file. When the file is a directory the copy creates an empty directory in the
     * target location, the directory entries are not copied. This method can be used with the
     * {@link #visit visit} method to copy the whole sub-tree.
     *
     * @param target the path of a target file
     * @param options the options specifying how the copy should be performed, see
     *            {@link StandardCopyOption}
     * @throws UnsupportedOperationException if {@code options} contains unsupported option
     * @throws FileAlreadyExistsException if the target path already exists and the {@code options}
     *             don't contain {@link StandardCopyOption#REPLACE_EXISTING} option
     * @throws DirectoryNotEmptyException if the {@code options} contain
     *             {@link StandardCopyOption#REPLACE_EXISTING} but the {@code target} is a non empty
     *             directory
     * @throws IOException in case of IO error
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 1.0
     */
    @TruffleBoundary
    public void copy(TruffleFile target, CopyOption... options) throws IOException {
        try {
            fileSystem.copy(normalizedPath, target.normalizedPath, options);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            throw e;
        } catch (Throwable t) {
            throw wrapHostException(t);
        }
    }

    private boolean isNormalized() {
        return path == normalizedPath || path.equals(normalizedPath);
    }

    private Path[] toAbsolutePathImpl() {
        Path absolute = fileSystem.toAbsolutePath(path);
        Path normalizedAbsolute = fileSystem.toAbsolutePath(normalizedPath).normalize();
        return new Path[]{absolute, normalizedAbsolute};
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
        throw TruffleLanguage.AccessAPI.engineAccess().wrapHostException(TruffleLanguage.AccessAPI.engineAccess().getCurrentHostContext(), t);
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

    private static final class TruffleFileDirectoryStream implements DirectoryStream<TruffleFile> {

        private final TruffleFile directory;
        private final DirectoryStream<Path> delegate;

        TruffleFileDirectoryStream(TruffleFile directory, DirectoryStream<Path> delegate) {
            this.directory = directory;
            this.delegate = delegate;
        }

        @Override
        public Iterator<TruffleFile> iterator() {
            try {
                final Iterator<Path> delegateIterator = delegate.iterator();
                final boolean normalized = directory.isNormalized();
                return new IteratorImpl(directory, delegateIterator, normalized);
            } catch (Throwable t) {
                throw directory.wrapHostException(t);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                this.delegate.close();
            } catch (IOException e) {
                throw e;
            } catch (Throwable t) {
                throw directory.wrapHostException(t);
            }
        }

        private static final class IteratorImpl implements Iterator<TruffleFile> {

            private final TruffleFile directory;
            private final Iterator<? extends Path> delegateIterator;
            private final boolean normalized;

            IteratorImpl(TruffleFile directory, Iterator<? extends Path> delegateIterator, boolean normalized) {
                this.directory = directory;
                this.delegateIterator = delegateIterator;
                this.normalized = normalized;
            }

            @Override
            public boolean hasNext() {
                try {
                    return delegateIterator.hasNext();
                } catch (Throwable t) {
                    throw directory.wrapHostException(t);
                }
            }

            @Override
            public TruffleFile next() {
                try {
                    Path path = delegateIterator.next();
                    return new TruffleFile(
                                    directory.fileSystem,
                                    normalized ? path : directory.path.resolve(path.getFileName()),
                                    normalized ? path : directory.normalizedPath.resolve(path.getFileName()));
                } catch (DirectoryIteratorException e) {
                    throw e;
                } catch (Throwable t) {
                    throw directory.wrapHostException(t);
                }
            }
        }
    }

    private static final class Walker implements Iterable<Walker.Event> {

        private final TruffleFile start;
        private final int maxDepth;
        private final boolean followSymLinks;
        private IteratorImpl currentIterator;

        Walker(TruffleFile start, int maxDepth, FileVisitOption... options) {
            this.start = start;
            this.maxDepth = maxDepth;
            boolean followSymLinksTmp = false;
            for (FileVisitOption option : options) {
                if (option == FileVisitOption.FOLLOW_LINKS) {
                    followSymLinksTmp = true;
                    break;
                }
            }
            this.followSymLinks = followSymLinksTmp;
        }

        @Override
        public Iterator<Event> iterator() {
            if (currentIterator != null) {
                throw new IllegalStateException("Multiple iterators are not allowed.");
            }
            currentIterator = new IteratorImpl(start, maxDepth, followSymLinks);
            return currentIterator;
        }

        void pop() {
            if (!currentIterator.stack.isEmpty()) {
                try {
                    currentIterator.stack.removeLast().close();
                } catch (IOException ignored) {
                }
            }
        }

        void skipRemainingSiblings() {
            if (!currentIterator.stack.isEmpty()) {
                currentIterator.stack.peekLast().setSkipped(true);
            }
        }

        static class Event {

            final Type type;
            final TruffleFile file;
            final IOException ioe;
            final BasicFileAttributes attrs;

            Event(Type type, TruffleFile file, BasicFileAttributes attrs) {
                this.type = type;
                this.file = file;
                this.attrs = attrs;
                this.ioe = null;
            }

            Event(Type type, TruffleFile file, IOException ioe) {
                this.type = type;
                this.file = file;
                this.attrs = null;
                this.ioe = ioe;
            }

            enum Type {
                PRE_VISIT_DIRECTORY,
                VISIT,
                POST_VISIT_DIRECTORY
            }
        }

        private static class IteratorImpl implements Iterator<Event> {

            private final int maxDepth;
            private final LinkOption[] linkOptions;
            private final Deque<Dir> stack;
            private Event current;

            IteratorImpl(TruffleFile start, int maxDepth, boolean followSymLinks) {
                this.maxDepth = maxDepth;
                this.linkOptions = followSymLinks ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
                this.stack = new ArrayDeque<>();
                this.current = enter(start);
            }

            @Override
            public boolean hasNext() {
                if (current == null) {
                    Dir top = stack.peekLast();
                    if (top != null) {
                        IOException ioe = null;
                        TruffleFile file = null;
                        if (!top.isSkipped()) {
                            try {
                                file = top.next();
                            } catch (DirectoryIteratorException x) {
                                ioe = x.getCause();
                            }
                        }
                        if (file == null) {
                            try {
                                top.close();
                            } catch (IOException e) {
                                if (ioe == null) {
                                    ioe = e;
                                } else {
                                    ioe.addSuppressed(e);
                                }
                            }
                            stack.removeLast();
                            current = new Event(Event.Type.POST_VISIT_DIRECTORY, top.directory, ioe);
                        } else {
                            current = enter(file);
                        }
                    }
                }
                return current != null;
            }

            @Override
            public Event next() {
                if (current == null) {
                    throw new NoSuchElementException();
                }
                Event res = current;
                current = null;
                return res;
            }

            private Event enter(TruffleFile file) {
                BasicFileAttributes attrs;
                try {
                    attrs = new BasicFileAttributesImpl(file.fileSystem.readAttributes(file.normalizedPath, "*", linkOptions));
                } catch (IOException ioe) {
                    return new Event(Event.Type.VISIT, file, ioe);
                }
                int currentDepth = stack.size();
                if (currentDepth >= maxDepth || !attrs.isDirectory()) {
                    return new Event(Event.Type.VISIT, file, attrs);
                }
                DirectoryStream<TruffleFile> stream = null;
                try {
                    stream = file.newDirectoryStream();
                } catch (IOException ioe) {
                    return new Event(Event.Type.VISIT, file, ioe);
                }
                stack.addLast(new Dir(file, stream));
                return new Event(Event.Type.PRE_VISIT_DIRECTORY, file, attrs);
            }

            private static final class Dir implements Closeable {

                final TruffleFile directory;
                final DirectoryStream<TruffleFile> stream;
                private final Iterator<TruffleFile> iterator;
                private boolean skipped;

                Dir(TruffleFile directory, DirectoryStream<TruffleFile> stream) {
                    this.directory = directory;
                    this.stream = stream;
                    this.iterator = stream.iterator();
                }

                void setSkipped(boolean value) {
                    skipped = value;
                }

                boolean isSkipped() {
                    return skipped;
                }

                TruffleFile next() {
                    return iterator.hasNext() ? iterator.next() : null;
                }

                @Override
                public void close() throws IOException {
                    stream.close();
                }
            }

            private static final class BasicFileAttributesImpl implements BasicFileAttributes {

                private Map<String, Object> attrsMap;

                BasicFileAttributesImpl(Map<String, Object> attrsMap) {
                    this.attrsMap = Objects.requireNonNull(attrsMap);
                }

                @Override
                public FileTime lastModifiedTime() {
                    return (FileTime) attrsMap.get("lastModifiedTime");
                }

                @Override
                public FileTime lastAccessTime() {
                    return (FileTime) attrsMap.get("lastAccessTime");
                }

                @Override
                public FileTime creationTime() {
                    return (FileTime) attrsMap.get("creationTime");
                }

                @Override
                public boolean isRegularFile() {
                    return (boolean) attrsMap.get("isRegularFile");
                }

                @Override
                public boolean isDirectory() {
                    return (boolean) attrsMap.get("isDirectory");
                }

                @Override
                public boolean isSymbolicLink() {
                    return (boolean) attrsMap.get("isSymbolicLink");
                }

                @Override
                public boolean isOther() {
                    return (boolean) attrsMap.get("isOther");
                }

                @Override
                public long size() {
                    return (long) attrsMap.get("size");
                }

                @Override
                public Object fileKey() {
                    return attrsMap.get("fileKey");
                }
            }
        }
    }
}
