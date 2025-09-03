/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import java.io.File;

import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;

/**
 * An entry in a classpath is a file system path that denotes an existing directory, an existing
 * zip/jar file or a file.
 */
public abstract class ClasspathEntry {

    /**
     * Gets the string representing the underlying path of this entry.
     */
    public final String path() {
        return file().getPath();
    }

    /**
     * Gets the File object representing the underlying path of this entry.
     */
    public abstract File file();

    /**
     * Gets the contents of a file denoted by a given path that is relative to this classpath entry.
     * If the denoted file does not exist under this classpath entry then {@code null} is returned.
     * Any IO exception that occurs when reading is silently ignored.
     *
     * @param archiveName name of the file in an archive with {@code '/'} as the separator
     */
    public abstract ClasspathFile readFile(ByteSequence archiveName);

    public boolean isDirectory() {
        return false;
    }

    public boolean isArchive() {
        return false;
    }

    @Override
    public String toString() {
        return path();
    }

}
