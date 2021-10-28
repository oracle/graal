/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.resources;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Formatter;

public class NativeImageResourceFileAttributes implements BasicFileAttributes {

    private final NativeImageResourceFileSystem fileSystem;
    private final NativeImageResourceFileSystem.Entry entry;

    public NativeImageResourceFileAttributes(NativeImageResourceFileSystem fileSystem, NativeImageResourceFileSystem.Entry entry) {
        this.fileSystem = fileSystem;
        this.entry = entry;
    }

    public String getName() {
        return fileSystem.getString(entry.name);
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.fromMillis(entry.lastModifiedTime);
    }

    @Override
    public FileTime lastAccessTime() {
        return FileTime.fromMillis(entry.lastAccessTime);
    }

    @Override
    public FileTime creationTime() {
        return FileTime.fromMillis(entry.createTime);
    }

    @Override
    public boolean isRegularFile() {
        return !entry.isDirectory();
    }

    @Override
    public boolean isDirectory() {
        return entry.isDirectory();
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public long size() {
        return entry.size();
    }

    @Override
    public Object fileKey() {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        Formatter fm = new Formatter(sb);
        fm.format("    name            : %s%n", getName());
        fm.format("    creationTime    : %tc%n", creationTime().toMillis());
        fm.format("    lastAccessTime  : %tc%n", lastAccessTime().toMillis());
        fm.format("    lastModifiedTime: %tc%n", lastModifiedTime().toMillis());
        fm.format("    isRegularFile   : %b%n", isRegularFile());
        fm.format("    isDirectory     : %b%n", isDirectory());
        fm.format("    isSymbolicLink  : %b%n", isSymbolicLink());
        fm.format("    isOther         : %b%n", isOther());
        fm.format("    fileKey         : %s%n", fileKey());
        fm.format("    size            : %d%n", size());
        fm.close();
        return sb.toString();
    }
}
