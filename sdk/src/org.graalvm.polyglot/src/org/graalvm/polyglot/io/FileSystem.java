/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.IOAccess.Builder;

/**
 * Service-provider for {@code Truffle} files.
 *
 * @since 19.0
 */
public interface FileSystem {

    /**
     * Parses a path from an {@link URI}.
     *
     * @param uri the {@link URI} to be converted to {@link Path}
     * @return the {@link Path} representing given {@link URI}
     * @throws UnsupportedOperationException when {@link URI} scheme is not supported
     * @throws IllegalArgumentException if preconditions on the {@code uri} do not hold. The format
     *             of the URI is {@link FileSystem} specific.
     * @since 19.0
     */
    Path parsePath(URI uri);

    /**
     * Parses a path from a {@link String}. This method is called only on the {@link FileSystem}
     * with {@code file} scheme.
     *
     * @param path the string path to be converted to {@link Path}
     * @return the {@link Path}
     * @throws UnsupportedOperationException when the {@link FileSystem} supports only {@link URI}
     * @throws IllegalArgumentException if the {@code path} string cannot be converted to a
     *             {@link Path}
     * @since 19.0
     */
    Path parsePath(String path);

    /**
     * Checks existence and accessibility of a file.
     *
     * @param path the path to the file to check
     * @param modes the access modes to check, possibly empty to check existence only.
     * @param linkOptions options determining how the symbolic links should be handled
     * @throws NoSuchFileException if the file denoted by the path does not exist
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException;

    /**
     * Creates a directory.
     *
     * @param dir the directory to create
     * @param attrs the optional attributes to set atomically when creating the directory
     * @throws FileAlreadyExistsException if a file on given path already exists
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the attributes contain an attribute which cannot be
     *             set atomically
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException;

    /**
     * Deletes a file.
     *
     * @param path the path to the file to delete
     * @throws NoSuchFileException if a file on given path does not exist
     * @throws DirectoryNotEmptyException if the path denotes a non empty directory
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    void delete(Path path) throws IOException;

    /**
     * Opens or creates a file returning a {@link SeekableByteChannel} to access the file content.
     *
     * @param path the path to the file to open
     * @param options the options specifying how the file should be opened
     * @param attrs the optional attributes to set atomically when creating the new file
     * @return the created {@link SeekableByteChannel}
     * @throws FileAlreadyExistsException if {@link StandardOpenOption#CREATE_NEW} option is set and
     *             a file already exists on given path
     * @throws IOException in case of IO error
     * @throws UnsupportedOperationException if the attributes contain an attribute which cannot be
     *             set atomically
     * @throws IllegalArgumentException in case of invalid options combination
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException;

    /**
     * Returns directory entries.
     *
     * @param dir the path to the directory to iterate entries for
     * @param filter the filter
     * @return the new {@link DirectoryStream}
     * @throws NotDirectoryException when given path does not denote a directory
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException;

    /**
     * Resolves given path to an absolute path.
     *
     * @param path the path to resolve, may be a non normalized path
     * @return an absolute {@link Path}
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    Path toAbsolutePath(Path path);

    /**
     * Returns the real (canonical) path of an existing file.
     *
     * @param path the path to resolve, may be a non normalized path
     * @param linkOptions options determining how the symbolic links should be handled
     * @return an absolute canonical path
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    Path toRealPath(Path path, LinkOption... linkOptions) throws IOException;

    /**
     * Reads a file's attributes as a bulk operation.
     *
     * @param path the path to file to read attributes for
     * @param attributes the attributes to read. The {@code attributes} parameter has the form:
     *            {@code [view-name:]attribute-list}. The optional {@code view-name} corresponds to
     *            {@link FileAttributeView#name()} and determines the set of attributes, the default
     *            value is {@code "basic"}. The {@code attribute-list} is a comma separated list of
     *            attributes. If the {@code attribute-list} contains {@code '*'} then all the
     *            attributes from given view are read.
     * @param options the options determining how the symbolic links should be handled
     * @return the {@link Map} containing the file attributes. The map's keys are attribute names,
     *         map's values are the attribute values. The map may contain a subset of required
     *         attributes in case when the {@code FileSystem} does not support some of the required
     *         attributes.
     * @throws UnsupportedOperationException if the attribute view is not supported. At least the
     *             {@code "basic"} attribute view has to be supported by the file system.
     * @throws IllegalArgumentException is the {@code attribute-list} is empty or contains an
     *             unknown attribute
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException;

    /**
     * Sets a file's attribute.
     *
     * @param path the path to file to set an attribute to
     * @param attribute the attribute to set. The {@code attribute} parameter has the form:
     *            {@code [view-name:]attribute-name}. The optional {@code view-name} corresponds to
     *            {@link FileAttributeView#name()} and determines the set of attributes, the default
     *            value is {@code "basic"}. The {@code attribute-name} is a name of an attribute.
     * @param value the attribute value
     * @param options the options determining how the symbolic links should be handled
     * @throws ClassCastException if {@code value} is not of the expected type or {@code value} is a
     *             {@link Collection} containing element of a non expected type
     * @throws UnsupportedOperationException if the attribute view is not supported.
     * @throws IllegalArgumentException is the {@code attribute-name} is an unknown attribute or
     *             {@code value} has an inappropriate value
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    default void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Setting attributes is not supported");
    }

    /**
     * Copies source file to target file.
     *
     * @param source the path to file to copy
     * @param target the path to the target file
     * @param options the options specifying how the copy should be performed, see
     *            {@link StandardCopyOption}
     * @throws UnsupportedOperationException if {@code options} contains unsupported option
     * @throws FileAlreadyExistsException if the target path already exists and the {@code options}
     *             don't contain {@link StandardCopyOption#REPLACE_EXISTING} option
     * @throws DirectoryNotEmptyException if the {@code options} contain
     *             {@link StandardCopyOption#REPLACE_EXISTING} but the {@code target} is a non empty
     *             directory
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    default void copy(Path source, Path target, CopyOption... options) throws IOException {
        IOHelper.copy(source, target, this, options);
    }

    /**
     * Moves (renames) source file to target file.
     *
     * @param source the path to file to move
     * @param target the path to the target file
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
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    default void move(Path source, Path target, CopyOption... options) throws IOException {
        IOHelper.move(source, target, this, options);
    }

    /**
     * Creates a new link for an existing file.
     *
     * @param link the path to link to create
     * @param existing the path to existing file
     * @throws UnsupportedOperationException if links are not supported by file system
     * @throws FileAlreadyExistsException if a file on given link path already exists
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    default void createLink(Path link, Path existing) throws IOException {
        throw new UnsupportedOperationException("Links are not supported");
    }

    /**
     * Creates a new symbolic link.
     *
     * @param link the path to symbolic link to create
     * @param target the target path of the symbolic link
     * @param attrs the optional attributes to set atomically when creating the new symbolic link
     * @throws UnsupportedOperationException if symbolic links are not supported by file system
     * @throws FileAlreadyExistsException if a file on given link path already exists
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    default void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException("Links are not supported");
    }

    /**
     * Reads the target of the symbolic link.
     *
     * @param link the path to symbolic link to read
     * @return the {@link Path} representing the symbolic link target
     * @throws UnsupportedOperationException if symbolic links are not supported by file system
     * @throws NotLinkException if the {@code link} does not denote a symbolic link
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 19.0
     */
    default Path readSymbolicLink(Path link) throws IOException {
        throw new UnsupportedOperationException("Links are not supported");
    }

    /**
     * Sets the current working directory. The current working directory is used to resolve non
     * absolute paths in {@link FileSystem} operations.
     *
     * @param currentWorkingDirectory the new current working directory
     * @throws UnsupportedOperationException if setting of the current working directory is not
     *             supported
     * @throws IllegalArgumentException if the {@code currentWorkingDirectory} is not a valid
     *             current working directory
     * @throws SecurityException if {@code currentWorkingDirectory} is not readable
     * @since 19.0
     */
    default void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
        throw new UnsupportedOperationException("Setting current working directory is not supported.");
    }

    /**
     * Returns the name separator used to separate names in a path string. The separator is used
     * when creating path strings by invoking the {@link Path#toString() toString()} method.
     *
     * @return the name separator
     * @since 19.0
     */
    default String getSeparator() {
        return parsePath("").getFileSystem().getSeparator();
    }

    /**
     * Returns the path separator used to separate filenames in a path list. On UNIX the path
     * separator is {@code ':'}. On Windows it's {@code ';'}.
     *
     * @return the path separator
     * @since 19.1.0
     */
    default String getPathSeparator() {
        return File.pathSeparator;
    }

    /**
     * Returns a MIME type for given path. An optional operation for {@link FileSystem filesystem}
     * implementations which can provide MIME types in an efficient way.
     *
     * @param path the file to find a MIME type for
     * @return the MIME type or {@code null} if the MIME type is not recognized or the
     *         {@link FileSystem filesystem} does not support MIME type detection
     * @since 19.0
     */
    default String getMimeType(Path path) {
        Objects.requireNonNull(path);
        return null;
    }

    /**
     * Returns an file encoding for given path. An optional operation for {@link FileSystem
     * filesystem} implementations which can provide file encodings in an efficient way.
     *
     * @param path the file to find an file encoding for
     * @return the file encoding or {@code null} if the file encoding is not detected or the
     *         {@link FileSystem filesystem} does not support file encoding detection
     * @since 19.0
     */
    default Charset getEncoding(Path path) {
        Objects.requireNonNull(path);
        return null;
    }

    /**
     * Returns the default temporary directory.
     *
     * @since 19.3.0
     */
    default Path getTempDirectory() {
        throw new UnsupportedOperationException("Temporary directories not supported");
    }

    /**
     * Tests if the given paths refer to the same physical file.
     *
     * The default implementation firstly converts the paths into absolute paths. If the absolute
     * paths are equal it returns {@code true} without checking if the file exists. Otherwise, this
     * method converts the paths into canonical representations and tests the canonical paths for
     * equality. The {@link FileSystem} may re-implement the method with a more efficient test. When
     * re-implemented the method must have the same security privileges as the
     * {@link #toAbsolutePath(Path) toAbsolutePath} and {@link #toRealPath(Path, LinkOption...)
     * toRealPath}.
     *
     * @param path1 the path to the file
     * @param path2 the other path
     * @param options the options determining how the symbolic links should be handled
     * @return {@code true} if the given paths refer to the same physical file
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 20.2.0
     */
    default boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
        if (toAbsolutePath(path1).equals(toAbsolutePath(path2))) {
            return true;
        }
        return toRealPath(path1, options).equals(toRealPath(path2, options));
    }

    /**
     * Returns the size, in bytes, of the file store that contains the given {@code path}. If the
     * file store's size exceeds {@link Long#MAX_VALUE}, {@code Long.MAX_VALUE} is returned.
     *
     * @param path the path whose file store size is to be determined
     * @return the size of the file store in bytes
     * @throws UnsupportedOperationException if the file system does not support retrieving file
     *             store information
     * @throws IOException if an I/O error occurs while accessing the file store
     * @throws SecurityException if the {@link FileSystem} implementation denied the operation
     * @since 25.0.0
     */
    default long getFileStoreTotalSpace(Path path) throws IOException {
        throw new UnsupportedOperationException("GetFileStoreTotalSpace is not supported");
    }

    /**
     * Returns the number of unallocated bytes in the file store that contains the given
     * {@code path}. The returned value represents the raw free space on the storage device,
     * regardless of access permissions or user quotas. If the number of unallocated bytes exceeds
     * {@link Long#MAX_VALUE}, {@code Long.MAX_VALUE} is returned. Note that the value may be
     * imprecise, as it can change at any time due to external I/O operations, including those
     * performed outside this virtual machine.
     *
     * @param path the path whose file store is to be queried
     * @return the number of unallocated bytes
     * @throws UnsupportedOperationException if the file system does not support retrieving file
     *             store information
     * @throws IOException if an I/O error occurs while accessing the file store
     * @throws SecurityException if the {@link FileSystem} implementation denied the operation
     * @since 25.0.0
     */
    default long getFileStoreUnallocatedSpace(Path path) throws IOException {
        throw new UnsupportedOperationException("GetFileStoreUnallocatedSpace is not supported");
    }

    /**
     * Returns the number of bytes available to this Java virtual machine on the file store that
     * contains the given {@code path}. Unlike {@link #getFileStoreUnallocatedSpace(Path)}, this
     * method accounts for operating system level restrictions, user quotas, and file system
     * permissions, and therefore may return a smaller value. If the available space exceeds
     * {@link Long#MAX_VALUE}, {@code Long.MAX_VALUE} is returned. Note that the returned value may
     * be imprecise, as it can change at any time due to external I/O activity, including operations
     * performed outside this virtual machine.
     *
     * @param path the path whose file store is to be queried
     * @return the number of usable bytes available to this Java virtual machine
     * @throws UnsupportedOperationException if the file system does not support retrieving file
     *             store information
     * @throws IOException if an I/O error occurs while accessing the file store
     * @throws SecurityException if the {@link FileSystem} implementation denied the operation
     * @since 25.0.0
     */
    default long getFileStoreUsableSpace(Path path) throws IOException {
        throw new UnsupportedOperationException("GetFileStoreUsableSpace is not supported");
    }

    /**
     * Returns the number of bytes per block in the file store that contains the given {@code path}.
     *
     * @param path the path whose file store is to be queried
     * @return the block size
     * @throws UnsupportedOperationException if the file system does not support retrieving file
     *             store information
     * @throws IOException if an I/O error occurs while accessing the file store
     * @throws SecurityException if the {@link FileSystem} implementation denied the operation
     * @since 25.0.0
     */
    default long getFileStoreBlockSize(Path path) throws IOException {
        throw new UnsupportedOperationException("GetFileStoreBlockSize is not supported");
    }

    /**
     * Determines whether the file store containing the given {@code path} is read-only.
     * <p>
     * Note that even if the file store is not read-only, individual write operations may still be
     * denied due to restrictions imposed by the {@link FileSystem} implementation, operating system
     * level policies, user quotas, or file system permissions.
     *
     * @param path the path whose file store is to be queried
     * @throws UnsupportedOperationException if the file system does not support retrieving file
     *             store information
     * @throws IOException if an I/O error occurs while accessing the file store
     * @throws SecurityException if the {@link FileSystem} implementation denied the operation
     * @since 25.0.0
     */
    default boolean isFileStoreReadOnly(Path path) throws IOException {
        throw new UnsupportedOperationException("IsFileStoreReadOnly is not supported");
    }

    /**
     * Creates a {@link FileSystem} implementation based on the host Java NIO. The returned instance
     * can be used as a delegate by a decorating {@link FileSystem}.
     * <p>
     * For an untrusted code execution, access to the host filesystem should be prevented either by
     * using {@link IOAccess#NONE} or an {@link #newFileSystem(java.nio.file.FileSystem)} in-memory
     * filesystem}. For more details on executing untrusted code, see the
     * <a href="https://www.graalvm.org/dev/security-guide/polyglot-sandbox/">Polyglot Sandboxing
     * Security Guide</a>.
     * <p>
     * The following example shows a {@link FileSystem} logging filesystem operations.
     *
     * <pre>
     * class TracingFileSystem implements FileSystem {
     *
     *     private static final Logger LOGGER = Logger.getLogger(TracingFileSystem.class.getName());
     *
     *     private final FileSystem delegate;
     *
     *     TracingFileSystem() {
     *         this.delegate = FileSystem.newDefaultFileSystem();
     *     }
     *
     *     &#64;Override
     *     public Path parsePath(String path) {
     *         return delegate.parsePath(path);
     *     }
     *
     *     &#64;Override
     *     public Path parsePath(URI uri) {
     *         return delegate.parsePath(uri);
     *     }
     *
     *     &#64;Override
     *     public SeekableByteChannel newByteChannel(Path path,
     *                                               Set&lt;? extends OpenOption&gt; options,
     *                                               FileAttribute&lt;?&gt;... attrs) throws IOException {
     *         boolean success = false;
     *         try {
     *             SeekableByteChannel result =  delegate.newByteChannel(path, options, attrs);
     *             success = true;
     *             return result;
     *         } finally {
     *             trace("newByteChannel", path, success);
     *         }
     *     }
     *
     *     ...
     *
     *     private void trace(String operation, Path path, boolean success) {
     *         LOGGER.log(Level.FINE, "The {0} request for the path {1} {2}.",new Object[] {
     *                         operation, path, success ? "was successful" : "failed"
     *                 });
     *     }
     * }
     * </pre>
     *
     * @see Builder#fileSystem(FileSystem)
     * @see org.graalvm.polyglot.Context.Builder#allowIO(IOAccess)
     *
     * @since 20.2.0
     */
    static FileSystem newDefaultFileSystem() {
        return IOHelper.ImplHolder.IMPL.newDefaultFileSystem(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Decorates the given {@code fileSystem} by an implementation that forwards access to files in
     * the language home to the default file system. The method is intended to be used by custom
     * filesystem implementations with non default storage to allow guest languages to access files
     * in the languages homes. As the returned filesystem uses a default file system to access files
     * in the language home, the {@code fileSystem} has to use the same {@link Path} type,
     * {@link #getSeparator() separator} and {@link #getPathSeparator() path separator} as the
     * {@link #newDefaultFileSystem() default filesystem}.
     *
     * @throws IllegalArgumentException when the {@code fileSystem} does not use the same
     *             {@link Path} type or has a different {@link #getSeparator() separator} or
     *             {@link #getPathSeparator() path separator} as the {@link #newDefaultFileSystem()
     *             default file system}.
     * @since 22.2
     * @deprecated Use {{@link #allowInternalResourceAccess(FileSystem)}}.
     */
    @Deprecated
    static FileSystem allowLanguageHomeAccess(FileSystem fileSystem) {
        return allowInternalResourceAccess(fileSystem);
    }

    /**
     * Decorates the given {@code fileSystem} by an implementation that forwards access to the
     * internal resources to the default file system. The method is intended to be used by custom
     * filesystem implementations with non default storage to allow guest languages to access
     * internal resources. As the returned filesystem uses a default file system to access internal
     * resources, the {@code fileSystem} has to use the same {@link Path} type,
     * {@link #getSeparator() separator} and {@link #getPathSeparator() path separator} as the
     * {@link #newDefaultFileSystem() default filesystem}.
     *
     * @throws IllegalArgumentException when the {@code fileSystem} does not use the same
     *             {@link Path} type or has a different {@link #getSeparator() separator} or
     *             {@link #getPathSeparator() path separator} as the {@link #newDefaultFileSystem()
     *             default file system}.
     * @see Engine#copyResources(Path, String...)
     * @since 24.0
     */
    static FileSystem allowInternalResourceAccess(FileSystem fileSystem) {
        return IOHelper.ImplHolder.IMPL.allowInternalResourceAccess(fileSystem);
    }

    /**
     * Decorates the given {@code fileSystem} by an implementation that makes the passed
     * {@code fileSystem} read-only by forbidding all write operations. This method can be used to
     * make an existing file system, such as the {@link #newDefaultFileSystem() default filesystem},
     * read-only.
     *
     * @since 22.2
     */
    static FileSystem newReadOnlyFileSystem(FileSystem fileSystem) {
        return IOHelper.ImplHolder.IMPL.newReadOnlyFileSystem(fileSystem);
    }

    /**
     * Creates a {@link FileSystem} implementation based on the given Java NIO filesystem. The
     * returned {@link FileSystem} delegates all operations to {@code fileSystem}'s
     * {@link FileSystemProvider provider}.
     *
     * <p>
     * The following example shows how to configure {@link Context} so that languages read files
     * from a prepared zip file.
     *
     * <pre>
     * Path zipFile = Paths.get("filesystem.zip");
     * try (java.nio.file.FileSystem nioFs = FileSystems.newFileSystem(zipFile)) {
     *     IOAccess ioAccess = IOAccess.newBuilder().fileSystem(FileSystem.newFileSystem(nioFs)).build();
     *     try (Context ctx = Context.newBuilder().allowIO(ioAccess).build()) {
     *         Value result = ctx.eval("js", "load('scripts/app.sh'); execute()");
     *     }
     * }
     * </pre>
     *
     * @see IOAccess
     * @since 23.0
     */
    static FileSystem newFileSystem(java.nio.file.FileSystem fileSystem) {
        return IOHelper.ImplHolder.IMPL.newNIOFileSystem(fileSystem);
    }

    /**
     * Creates a {@link FileSystem} that denies all file operations except for path parsing. Any
     * attempt to perform file operations such as reading, writing, or deletion will result in a
     * {@link SecurityException} being thrown.
     * <p>
     * Typically, this file system does not need to be explicitly installed to restrict access to
     * host file systems. Instead, use {@code Context.newBuilder().allowIO(IOAccess.NONE)}. This
     * method is intended primarily for use as a fallback file system in a
     * {@link #newCompositeFileSystem(FileSystem, Selector...) composite file system}.
     *
     * @since 24.2
     */
    static FileSystem newDenyIOFileSystem() {
        return IOHelper.ImplHolder.IMPL.newDenyIOFileSystem();
    }

    /**
     * Creates a composite {@link FileSystem} that delegates operations to the provided
     * {@code delegates}. The {@link FileSystem} of the first {@code delegate} whose
     * {@link Selector#test(Path)} method accepts the path is used for the file system operation. If
     * no {@code delegate} accepts the path, the {@code fallbackFileSystem} is used.
     * <p>
     * The {@code fallbackFileSystem} is responsible for parsing {@link Path} objects. All provided
     * file systems must use the same {@link Path} type, {@link #getSeparator() separator}, and
     * {@link #getPathSeparator() path separator}. If any file system does not meet this
     * requirement, an {@link IllegalArgumentException} is thrown.
     * <p>
     * The composite file system maintains its own notion of the current working directory and
     * ensures that the {@link #setCurrentWorkingDirectory(Path)} method is not invoked on any of
     * the delegates. When a request to set the current working directory is received, the composite
     * file system verifies that the specified path corresponds to an existing directory by
     * consulting either the appropriate delegate or the {@code fallbackFileSystem}. If an explicit
     * current working directory has been set, the composite file system normalizes and resolves all
     * relative paths to absolute paths prior to delegating operations. Conversely, if no explicit
     * current working directory is set, the composite file system directly forwards the incoming
     * path, whether relative or absolute, to the appropriate delegate. Furthermore, when an
     * explicit current working directory is set, the composite file system does not delegate
     * {@code toAbsolutePath} operations, as delegates do not maintain an independent notion of the
     * current working directory. If the current working directory is unset, {@code toAbsolutePath}
     * operations are delegated to the {@code fallbackFileSystem}.
     * <p>
     * Operations that are independent of path context, including {@code getTempDirectory},
     * {@code getSeparator}, and {@code getPathSeparator}, are handled exclusively by the
     * {@code fallbackFileSystem}.
     *
     * @throws IllegalArgumentException if the file systems do not use the same {@link Path} type,
     *             {@link #getSeparator() separator}, or {@link #getPathSeparator() path separator}
     * @since 24.2
     */
    static FileSystem newCompositeFileSystem(FileSystem fallbackFileSystem, Selector... delegates) {
        return IOHelper.ImplHolder.IMPL.newCompositeFileSystem(fallbackFileSystem, delegates);
    }

    /**
     * A selector for determining which {@link FileSystem} should handle operations on a given
     * {@link Path}. This class encapsulates a {@link FileSystem} and defines a condition for
     * selecting it.
     *
     * @since 24.2
     */
    abstract class Selector implements Predicate<Path> {

        private final FileSystem fileSystem;

        /**
         * Creates a {@link Selector} for the specified {@link FileSystem}.
         *
         * @since 24.2
         */
        protected Selector(FileSystem fileSystem) {
            this.fileSystem = Objects.requireNonNull(fileSystem, "FileSystem must be non-null");
        }

        /**
         * Returns the {@link FileSystem} associated with this selector.
         *
         * @since 24.2
         */
        public final FileSystem getFileSystem() {
            return fileSystem;
        }

        /**
         * Tests whether the {@link FileSystem} associated with this selector can handle operations
         * on the specified {@link Path}.
         *
         * @param path the path to test, provided as a normalized absolute path. The given
         *            {@code path} has no path components equal to {@code "."} or {@code ".."}.
         * @return {@code true} if the associated {@link FileSystem} can handle the {@code path};
         *         {@code false} otherwise
         * @since 24.2
         */
        public abstract boolean test(Path path);

        /**
         * Creates a {@link Selector} for the specified {@link FileSystem} using the provided
         * {@link Predicate}.
         *
         * @param fileSystem the {@link FileSystem} to associate with the selector
         * @param predicate the condition to determine if the {@link FileSystem} can handle a given
         *            path
         * @return a new {@link Selector} that delegates path testing to the {@code predicate}
         * @since 24.2
         */
        public static Selector of(FileSystem fileSystem, Predicate<Path> predicate) {
            Objects.requireNonNull(predicate, "Predicate must be non-null");
            return new Selector(fileSystem) {
                @Override
                public boolean test(Path path) {
                    return predicate.test(path);
                }
            };
        }
    }
}
