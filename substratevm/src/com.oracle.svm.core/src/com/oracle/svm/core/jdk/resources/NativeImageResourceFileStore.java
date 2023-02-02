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

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

public class NativeImageResourceFileStore extends FileStore {

    private final FileSystem resourceFileSystem;

    NativeImageResourceFileStore(Path path) {
        this.resourceFileSystem = path.getFileSystem();
    }

    @Override
    public String name() {
        return resourceFileSystem.toString() + resourceFileSystem.getSeparator();
    }

    @Override
    public String type() {
        return "rfs";
    }

    @Override
    public boolean isReadOnly() {
        return resourceFileSystem.isReadOnly();
    }

    @Override
    public long getTotalSpace() {
        throw new UnsupportedOperationException("This operation is not support for native image resource file system!");
    }

    @Override
    public long getUsableSpace() {
        throw new UnsupportedOperationException("This operation is not support for native image resource file system!");
    }

    @Override
    public long getUnallocatedSpace() {
        throw new UnsupportedOperationException("This operation is not support for native image resource file system!");
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return type == BasicFileAttributeView.class || type == NativeImageResourceFileAttributesView.class;
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        return name.equals("basic") || name.equals("resource");
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        if (type == null) {
            throw new NullPointerException();
        }
        return null;
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        if (attribute.equals("totalSpace")) {
            return getTotalSpace();
        }
        if (attribute.equals("usableSpace")) {
            return getUsableSpace();
        }
        if (attribute.equals("unallocatedSpace")) {
            return getUnallocatedSpace();
        }
        throw new UnsupportedOperationException("Attribute isn't supported!");
    }
}
