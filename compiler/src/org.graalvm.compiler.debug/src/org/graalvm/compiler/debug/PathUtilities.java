/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;

/**
 * Miscellaneous methods for modifying and generating file system paths.
 *
 * All path arguments and return values are {@link String}s to avoid use of {@link File} and
 * {@link Path} which may not be supported in all environments in which Graal is embedded. Use in
 * such environments requires defining a {@link PathUtilitiesProvider} service implementation.
 */
public class PathUtilities {

    private static final PathUtilitiesProvider PROVIDER = loadProvider();

    /**
     * Loads the single {@link PathUtilitiesProvider} implementation available via
     * {@linkplain ServiceLoader service loading}. If no implementation is available, a
     * {@link StandardPathUtilitiesProvider} instance is returned.
     */
    private static PathUtilitiesProvider loadProvider() {
        ServiceLoader<PathUtilitiesProvider> providers = ServiceLoader.load(PathUtilitiesProvider.class);
        for (Iterator<PathUtilitiesProvider> it = providers.iterator(); it.hasNext();) {
            PathUtilitiesProvider singleProvider = it.next();
            if (it.hasNext()) {
                PathUtilitiesProvider other = it.next();
                throw new InternalError(
                                String.format("Multiple %s providers found: %s, %s", PathUtilitiesProvider.class.getName(), singleProvider.getClass().getName(), other.getClass().getName()));
            }
            return singleProvider;
        }
        return new StandardPathUtilitiesProvider();
    }

    /**
     * Gets a value based on {@code name} that can be passed to {@link #getPath(String, String...)}
     * without causing an {@link InvalidPathException}.
     *
     * @return {@code name} with all characters invalid for the current file system replaced by
     *         {@code '_'}
     */
    public static String sanitizeFileName(String name) {
        return PROVIDER.sanitizeFileName(name);
    }

    /**
     * Joins a sequence of strings to form a pathname.
     */
    public static String getPath(String first, String... more) {
        return PROVIDER.getPath(first, more);
    }

    /**
     * Gets the absolute pathname of {@code path}.
     */
    public static String getAbsolutePath(String path) {
        return PROVIDER.getAbsolutePath(path);
    }

    /**
     * Tests whether the file or directory denoted by {@code path} exists.
     */
    public static boolean exists(String path) {
        return PROVIDER.exists(path);
    }

    /**
     * Creates a directory represented by {@code path} by creating all nonexistent parent
     * directories first. An exception is not thrown if the directory could not be created because
     * it already exists. If this method fails, then it may do so after creating some, but not all,
     * of the parent directories.
     *
     * @throws IOException if {@code path} exists but is not a directory or if some I/O error occurs
     */
    public static String createDirectories(String path) throws IOException {
        return PROVIDER.createDirectories(path);
    }

    /**
     * Creates an output stream to write to the file {@code path}. If {@code append} is true, then
     * bytes will be written to the end of the file rather than the beginning.
     *
     * throws {@link IOException} if the file exists but is a directory rather than a regular file,
     * does not exist but cannot be created, or cannot be opened for any other reason
     */
    public static OutputStream openOutputStream(String path, boolean append) throws IOException {
        return PROVIDER.openOutputStream(path, append);
    }

    /**
     * Short cut for calling {@link #openOutputStream(String, boolean)} with the arguments
     * {@code path} and {@code false}.
     */
    public static OutputStream openOutputStream(String path) throws IOException {
        return PROVIDER.openOutputStream(path, false);
    }

    /**
     * Gets the pathname of {@code path}'s parent, or null if {@code path} does not have a parent
     * directory.
     */
    public static String getParent(String path) {
        return PROVIDER.getParent(path);
    }

    /**
     * Opens or creates a file, returning a channel to access the file {@code path}.
     */
    public static WritableByteChannel openFileChannel(String path, OpenOption... options) throws IOException {
        return PROVIDER.openFileChannel(path, options);
    }

    /**
     * Creates an input stream by opening the file {@code path}.
     *
     * @throws IOException if {@code path} does not exist, is a directory rather than a regular
     *             file, or for some other reason cannot be opened for reading
     */
    public static InputStream openInputStream(String path) throws IOException {
        return PROVIDER.openInputStream(path);
    }

    /**
     * Zips and deletes {@code directory} if it exists.
     *
     * @param zip path of the zip file to create
     * @throws IOException if something goes wrong
     */
    public static String archiveAndDelete(String directory, String zip) throws IOException {
        return PROVIDER.archiveAndDelete(directory, zip);
    }

    /**
     * A maximum file name length supported by most file systems. There is no platform independent
     * way to get this in Java. Normally it is 255. But for AUFS it is 242. See AUFS_MAX_NAMELEN in
     * http://aufs.sourceforge.net/aufs3/man.html.
     */
    private static final int MAX_FILE_NAME_LENGTH = 242;

    private static final String ELLIPSIS = "...";

    static String createUnique(OptionValues options, OptionKey<String> baseNameOption, String id, String label, String ext, boolean createMissingDirectory) throws IOException {
        String uniqueTag = "";
        int dumpCounter = 1;
        String prefix;
        if (id == null) {
            prefix = baseNameOption.getValue(options);
            int slash = prefix.lastIndexOf(File.separatorChar);
            prefix = prefix.substring(slash + 1);
        } else {
            prefix = id;
        }
        for (;;) {
            int fileNameLengthWithoutLabel = uniqueTag.length() + ext.length() + prefix.length() + "[]".length();
            int labelLengthLimit = MAX_FILE_NAME_LENGTH - fileNameLengthWithoutLabel;
            String fileName;
            if (labelLengthLimit < ELLIPSIS.length()) {
                // This means `id` is very long
                String suffix = uniqueTag + ext;
                int idLengthLimit = Math.min(MAX_FILE_NAME_LENGTH - suffix.length(), prefix.length());
                fileName = sanitizeFileName(prefix.substring(0, idLengthLimit) + suffix);
            } else {
                if (label == null) {
                    fileName = sanitizeFileName(prefix + uniqueTag + ext);
                } else {
                    String adjustedLabel = label;
                    if (label.length() > labelLengthLimit) {
                        adjustedLabel = label.substring(0, labelLengthLimit - ELLIPSIS.length()) + ELLIPSIS;
                    }
                    fileName = sanitizeFileName(prefix + '[' + adjustedLabel + ']' + uniqueTag + ext);
                }
            }
            String dumpDir = DebugOptions.getDumpDirectory(options);
            String result = getPath(dumpDir, fileName);
            try {
                if (createMissingDirectory) {
                    return createDirectories(result);
                } else {
                    try {
                        return PROVIDER.createFile(result);
                    } catch (AccessDeniedException e) {
                        /*
                         * Thrown on Windows if a directory with the same name already exists, so
                         * convert it to FileAlreadyExistsException if that's the case.
                         */
                        throw PROVIDER.isDirectory(result, false) ? new FileAlreadyExistsException(e.getFile()) : e;
                    }
                }
            } catch (FileAlreadyExistsException e) {
                uniqueTag = "_" + dumpCounter++;
            }
        }
    }

    public static boolean isDirectory(String path, boolean followLinks) {
        return PROVIDER.isDirectory(path, followLinks);
    }

    /**
     * Deletes the file or directory denoted by {@code path}. If {@code path} denotes a directory,
     * then it must be empty in order to be deleted.
     *
     * @throws IOException if the deletion fails
     */
    public static void deleteFile(String path) throws IOException {
        PROVIDER.deleteFile(path);
    }
}
