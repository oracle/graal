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
package org.graalvm.polyglot.io;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
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
import java.util.Set;

/**
 * Service-provider for {@code Truffle} files.
 *
 * @since 1.0
 */
public interface FileSystem {

    /**
     * Parses a path from an {@link URI}.
     *
     * @param uri the {@link URI} to be converted to {@link Path}
     * @return the {@link Path} representing given {@link URI}
     * @throws UnsupportedOperationException when {@link URI} scheme is not supported
     * @since 1.0
     */
    Path parsePath(URI uri);

    /**
     * Parses a path from a {@link String}. This method is called only on the {@link FileSystem}
     * with {@code file} scheme.
     *
     * @param path the string path to be converted to {@link Path}
     * @return the {@link Path}
     * @throws UnsupportedOperationException when the {@link FileSystem} supports only {@link URI}
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
     */
    DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException;

    /**
     * Resolves given path to an absolute path.
     *
     * @param path the path to resolve
     * @return an absolute {@link Path}
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 1.0
     */
    Path toAbsolutePath(Path path);

    /**
     * Returns the real (canonical) path of an existing file.
     *
     * @param path the path to resolve
     * @param linkOptions options determining how the symbolic links should be handled
     * @return an absolute canonical path
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 1.0
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
     *         map's values are the attribute values
     * @throws UnsupportedOperationException if the attribute view is not supported. At least the
     *             {@code "basic"} attribute view has to be supported by the file system.
     * @throws IllegalArgumentException is the {@code attribute-list} is empty or contains an
     *             unknown attribute
     * @throws IOException in case of IO error
     * @throws SecurityException if this {@link FileSystem} denied the operation
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
     */
    default Path readSymbolicLink(Path link) throws IOException {
        throw new UnsupportedOperationException("Links are not supported");
    }
}
