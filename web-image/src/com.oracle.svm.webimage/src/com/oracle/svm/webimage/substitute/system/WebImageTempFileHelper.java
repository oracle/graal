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
package com.oracle.svm.webimage.substitute.system;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

// Checkstyle: stop
/**
 * Copy of {@code java.nio.file.TempFileHelper} but with {@link Random} instead of
 * {@link java.security.SecureRandom}. This avoids pulling in a lot of security related classes from
 * the JDK.
 */
// @formatter:off
@SuppressWarnings("all")
public class WebImageTempFileHelper {
    private WebImageTempFileHelper() { }

    // temporary directory location
    private static final Path tmpdir =
            Paths.get(System.getProperty("java.io.tmpdir"));

    private static final boolean isPosix =
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    // file name generation, same as java.io.File for now
    private static final Random random = new Random();
    private static Path generatePath(String prefix, String suffix, Path dir) {
        long n = random.nextLong();
        String s = prefix + Long.toUnsignedString(n) + suffix;
        Path name = dir.getFileSystem().getPath(s);
        // the generated name should be a simple file name
        if (name.getParent() != null)
            throw new IllegalArgumentException("Invalid prefix or suffix");
        return dir.resolve(name);
    }

    // default file and directory permissions (lazily initialized)
    private static class PosixPermissions {
        static final FileAttribute<Set<PosixFilePermission>> filePermissions =
                PosixFilePermissions.asFileAttribute(EnumSet.of(OWNER_READ, OWNER_WRITE));
        static final FileAttribute<Set<PosixFilePermission>> dirPermissions =
                PosixFilePermissions.asFileAttribute(EnumSet
                        .of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
    }

    /**
     * Creates a file or directory in the given directory (or in the
     * temporary directory if dir is {@code null}).
     */
    private static Path create(Path dir,
                               String prefix,
                               String suffix,
                               boolean createDirectory,
                               FileAttribute<?>[] attrs)
            throws IOException
    {
        if (prefix == null)
            prefix = "";
        if (suffix == null)
            suffix = (createDirectory) ? "" : ".tmp";
        if (dir == null)
            dir = tmpdir;

        // in POSIX environments use default file and directory permissions
        // if initial permissions not given by caller.
        if (isPosix && (dir.getFileSystem() == FileSystems.getDefault())) {
            if (attrs.length == 0) {
                // no attributes so use default permissions
                attrs = new FileAttribute<?>[1];
                attrs[0] = (createDirectory) ? PosixPermissions.dirPermissions :
                        PosixPermissions.filePermissions;
            } else {
                // check if posix permissions given; if not use default
                boolean hasPermissions = false;
                for (int i=0; i<attrs.length; i++) {
                    if (attrs[i].name().equals("posix:permissions")) {
                        hasPermissions = true;
                        break;
                    }
                }
                if (!hasPermissions) {
                    FileAttribute<?>[] copy = new FileAttribute<?>[attrs.length+1];
                    System.arraycopy(attrs, 0, copy, 0, attrs.length);
                    attrs = copy;
                    attrs[attrs.length-1] = (createDirectory) ?
                            PosixPermissions.dirPermissions :
                            PosixPermissions.filePermissions;
                }
            }
        }

        // loop generating random names until file or directory can be created
        SecurityManager sm = System.getSecurityManager();
        for (;;) {
            Path f;
            try {
                f = generatePath(prefix, suffix, dir);
            } catch (InvalidPathException e) {
                // don't reveal temporary directory location
                if (sm != null)
                    throw new IllegalArgumentException("Invalid prefix or suffix");
                throw e;
            }
            try {
                if (createDirectory) {
                    return Files.createDirectory(f, attrs);
                } else {
                    return Files.createFile(f, attrs);
                }
            } catch (SecurityException e) {
                // don't reveal temporary directory location
                if (dir == tmpdir && sm != null)
                    throw new SecurityException("Unable to create temporary file or directory");
                throw e;
            } catch (FileAlreadyExistsException e) {
                // ignore
            }
        }
    }

    /**
     * Creates a temporary file in the given directory, or in the
     * temporary directory if dir is {@code null}.
     */
    static Path createTempFile(Path dir,
                               String prefix,
                               String suffix,
                               FileAttribute<?>[] attrs)
            throws IOException
    {
        return create(dir, prefix, suffix, false, attrs);
    }

    /**
     * Creates a temporary directory in the given directory, or in the
     * temporary directory if dir is {@code null}.
     */
    static Path createTempDirectory(Path dir,
                                    String prefix,
                                    FileAttribute<?>[] attrs)
            throws IOException
    {
        return create(dir, prefix, null, true, attrs);
    }
    // @formatter:on
    // Checkstyle: resume
}
