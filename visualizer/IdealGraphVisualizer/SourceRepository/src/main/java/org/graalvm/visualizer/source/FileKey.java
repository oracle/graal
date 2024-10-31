/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.source;

import org.openide.filesystems.FileObject;

import java.util.Objects;

/**
 * Overridable key which identifies a file. Subclasses may add addditional information,
 * like module (a filename is relative to it). equals() and hashCode() must be properly
 * defined. It is OK to have multiple FileKeys identify a single FileObject
 */
public class FileKey {
    private final String mime;
    private final String fileSpec;
    /**
     * Resolved FileObject, possibly null
     */
    private volatile FileObject resolvedFile;

    public FileKey(String mime, String fileSpec) {
        this.mime = mime;
        this.fileSpec = fileSpec;
    }

    public FileKey(String fileSpec, FileObject file) {
        this(file.getMIMEType(), fileSpec);
        this.resolvedFile = file;
    }

    public final String getMime() {
        return mime;
    }

    public final String getFileSpec() {
        return fileSpec;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.mime);
        hash = 79 * hash + Objects.hashCode(this.fileSpec);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FileKey other = (FileKey) obj;
        if (!Objects.equals(this.mime, other.mime)) {
            return false;
        }
        if (!Objects.equals(this.fileSpec, other.fileSpec)) {
            return false;
        }
        return true;
    }

    public FileObject getResolvedFile() {
        return resolvedFile;
    }


    public final boolean isResolved() {
        return resolvedFile != null;
    }

    final void setResolvedFile(FileObject f) {
        this.resolvedFile = f;
    }

    @Override
    public String toString() {
        return (resolvedFile != null ? resolvedFile.getPath() : fileSpec);
    }

    public static FileKey fromFile(FileObject f) {
        return new FileKey(f.getPath(), f);
    }
}
