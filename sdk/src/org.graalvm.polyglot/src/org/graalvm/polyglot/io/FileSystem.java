/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
     * Creates a {@link FileSystem} implementation based on the host Java NIO. The returned instance
     * can be used as a delegate by a decorating {@link FileSystem}.
     * <p>
     * The following example shows a {@link FileSystem} restricting an IO access only to a given
     * folder.
     *
     * <pre>
     * class RestrictedFileSystem implements FileSystem {
     *
     *     private final FileSystem delegate;
     *     private final Path allowedFolder;
     *
     *     RestrictedFileSystem(String allowedFolder) throws IOException {
     *         this.delegate = FileSystem.newDefaultFileSystem();
     *         this.allowedFolder = delegate.toRealPath(
     *                         delegate.parsePath(allowedFolder));
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
     *                     Set&lt;? extends OpenOption&gt; options,
     *                     FileAttribute&lt;?&gt;... attrs) throws IOException {
     *         verifyAccess(path);
     *         return delegate.newByteChannel(path, options, attrs);
     *     }
     *
     *     private void verifyAccess(Path path) {
     *         Path realPath = null;
     *         for (Path c = path; c != null; c = c.getParent()) {
     *             try {
     *                 realPath = delegate.toRealPath(c);
     *                 break;
     *             } catch (IOException ioe) {
     *             }
     *         }
     *         if (realPath == null || !realPath.startsWith(allowedFolder)) {
     *             throw new SecurityException("Access to " + path + " is denied.");
     *         }
     *     }
     * }
     * </pre>
     *
     * @see org.graalvm.polyglot.Context.Builder#fileSystem(org.graalvm.polyglot.io.FileSystem)
     *
     * @since 20.2.0
     */
    static FileSystem newDefaultFileSystem() {
        return IOHelper.IMPL.newDefaultFileSystem();
    }
}
