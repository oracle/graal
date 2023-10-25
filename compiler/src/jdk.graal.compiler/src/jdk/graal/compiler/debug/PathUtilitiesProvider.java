/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;

/**
 * Defines a service for accessing storage resembling a file system.
 *
 * This exists for Graal embedders in an environment where a standard file system is not available.
 * An implementation of this service is loaded via {@link ServiceLoader} which means the
 * implementation class must be named in a
 * {@code META-INF/services/jdk.graal.compiler.debug.PathUtilitiesProvider} service description file
 * accessible on the class path. Note that a {@link PathUtilitiesProvider} implementation must work
 * in the context of building a native image as well as in the context of the resulting native
 * image. Delegating to {@link StandardPathUtilitiesProvider} is the recommended way to work in the
 * context of building a native image.
 */
public interface PathUtilitiesProvider {
    /**
     * Gets a value based on {@code name} that can be passed to {@link #getPath(String, String...)}
     * without causing an {@link InvalidPathException}.
     *
     * @return {@code name} with all characters invalid for the current file system replaced by
     *         {@code '_'}
     */
    String sanitizeFileName(String name);

    /**
     * Zips and deletes {@code directory} if it exists.
     *
     * @param zip path of the zip file to create
     * @throws IOException if something goes wrong
     */
    String archiveAndDelete(String directory, String zip) throws IOException;

    /**
     * @see Paths#get(String, String...)
     */
    String getPath(String first, String... more);

    /**
     * @see File#getAbsolutePath()
     */
    String getAbsolutePath(String path);

    /**
     * @see FileOutputStream#FileOutputStream(String, boolean)
     */
    OutputStream openOutputStream(String path, boolean append) throws IOException;

    /**
     * @see File#exists()
     */
    boolean exists(String path);

    /**
     * @see Files#createDirectories
     */
    String createDirectories(String path) throws IOException;

    /**
     * @see Files#createFile
     */
    String createFile(String path) throws IOException;

    /**
     * @see Files#isDirectory
     */
    boolean isDirectory(String path, boolean followLinks);

    /**
     * @see Files#delete(Path)
     */
    void deleteFile(String path) throws IOException;

    /**
     * @see File#getParent
     */
    String getParent(String path);

    /**
     * @see FileChannel#open(Path, OpenOption...)
     */
    WritableByteChannel openFileChannel(String path, OpenOption... options) throws IOException;

    /**
     * @see FileInputStream#FileInputStream(String)
     */
    InputStream openInputStream(String path) throws IOException;
}
