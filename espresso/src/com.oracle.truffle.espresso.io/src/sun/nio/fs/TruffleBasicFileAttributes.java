/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package sun.nio.fs;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;

/**
 * Basic attributes for the Truffle FS, all supported except {@link #fileKey()}. {@link FileTime}
 * attributes are serialized as a lon (milliseconds from the epoch).
 *
 * <p>
 * This file must be compatible with 21+.
 */
final class TruffleBasicFileAttributes implements BasicFileAttributes {

    static final List<String> BASIC_ATTRIBUTES = List.of(
                    "lastModifiedTime",
                    "lastAccessTime",
                    "creationTime",
                    "isRegularFile",
                    "isDirectory",
                    "isSymbolicLink",
                    "isOther",
                    "size");

    private final long lastModifiedTimeMillis;
    private final long lastAccessTimeMillis;
    private final long creationTimeMillis;
    private final boolean isRegularFile;
    private final boolean isDirectory;
    private final boolean isSymbolicLink;
    private final boolean isOther;
    private final long size;

    private TruffleBasicFileAttributes(
                    long lastModifiedTimeMillis, long lastAccessTimeMillis, long creationTimeMillis,
                    boolean isRegularFile, boolean isDirectory, boolean isSymbolicLink, boolean isOther,
                    long size) {
        this.lastModifiedTimeMillis = lastModifiedTimeMillis;
        this.lastAccessTimeMillis = lastAccessTimeMillis;
        this.creationTimeMillis = creationTimeMillis;
        this.isRegularFile = isRegularFile;
        this.isDirectory = isDirectory;
        this.isSymbolicLink = isSymbolicLink;
        this.isOther = isOther;
        this.size = size;
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.fromMillis(lastModifiedTimeMillis);
    }

    @Override
    public FileTime lastAccessTime() {
        return FileTime.fromMillis(lastAccessTimeMillis);
    }

    @Override
    public FileTime creationTime() {
        return FileTime.fromMillis(creationTimeMillis);
    }

    @Override
    public boolean isRegularFile() {
        return isRegularFile;
    }

    @Override
    public boolean isDirectory() {
        return isDirectory;
    }

    @Override
    public boolean isSymbolicLink() {
        return isSymbolicLink;
    }

    @Override
    public boolean isOther() {
        return isOther;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public Object fileKey() {
        // unsupported
        return null;
    }

    Object getAttribute(String basicAttributeName) {
        switch (basicAttributeName) {
            case "lastModifiedTime":
                return lastModifiedTime();
            case "lastAccessTime":
                return lastAccessTime();
            case "creationTime":
                return creationTime();
            case "isRegularFile":
                return isRegularFile();
            case "isDirectory":
                return isDirectory();
            case "isSymbolicLink":
                return isSymbolicLink();
            case "isOther":
                return isOther();
            case "size":
                return size();
        }
        return null;
    }
}
