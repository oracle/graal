/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;

import java.io.File;

/**
 * Encapsulates the contents of a file loaded from an {@linkplain ClasspathEntry entry} on a
 * classpath.
 */
public final class ClasspathFile {

    /**
     * The bytes of the file represented by this object.
     */
    public final byte[] contents;

    /**
     * The classpath entry from which the file represented by this object was read.
     */
    public final ClasspathEntry classpathEntry;

    /**
     * Name of the file relative to {@link #classpathEntry}.
     */
    public final ByteSequence name;

    /**
     * Creates an object encapsulating the bytes of a file read via a classpath entry.
     *
     * @param contents the bytes of the file that was read
     * @param classpathEntry the entry from which the file was read
     */
    public ClasspathFile(byte[] contents, ClasspathEntry classpathEntry, ByteSequence name) {
        this.classpathEntry = classpathEntry;
        this.contents = contents;
        this.name = name;
    }

    @Override
    public String toString() {
        if (classpathEntry.isArchive()) {
            return classpathEntry.file().getAbsolutePath() + '!' + name;
        }
        return classpathEntry.file().getAbsolutePath() + File.separatorChar + name;
    }
}
