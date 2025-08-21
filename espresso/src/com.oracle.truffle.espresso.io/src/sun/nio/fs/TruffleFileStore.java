/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

class TruffleFileStore extends FileStore {
    private static final String name = "truffle";
    private static final String type = "virtual";
    private final TrufflePath path;

    TruffleFileStore(Path path) {
        this.path = TrufflePath.toTrufflePath(path);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public boolean isReadOnly() {
        return isReadOnly0(path);
    }

    @Override
    public long getTotalSpace() throws IOException {
        return totalSpace0(path);
    }

    @Override
    public long getUsableSpace() throws IOException {
        return getUsableSpace0(path);
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
        return getUnallocatedSpace0(path);
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return type == BasicFileAttributeView.class;
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        return "basic".equals(name);
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        return null;
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        throw new UnsupportedOperationException("getAttribute is not supported.");
    }

    private static native boolean isReadOnly0(TrufflePath path);

    private static native long totalSpace0(TrufflePath path);

    private static native long getUsableSpace0(TrufflePath path);

    private static native long getUnallocatedSpace0(TrufflePath path);
}
